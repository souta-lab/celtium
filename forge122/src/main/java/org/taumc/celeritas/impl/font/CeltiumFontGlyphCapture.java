package org.taumc.celeritas.impl.font;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the vanilla font's immediate-mode glyph into native geometry, and nothing else.
 * <p>
 * Minecraft 1.12.2 draws every glyph of its own font with immediate mode: {@code FontRenderer.renderDefaultChar} and
 * {@code renderUnicodeChar} each emit one {@code glBegin(GL_TRIANGLE_STRIP)} / four {@code glTexCoord2f} / four
 * {@code glVertex3f} / {@code glEnd} sequence. Under the GL-less stub all five entry points are state-set no-ops, so
 * the glyphs are lost, while panels and item icons - which travel through the Tessellator and
 * {@code WorldVertexBufferUploader} - are drawn natively.
 * <p>
 * This class is the capture for exactly that geometry. {@link FontRendererMixin} opens a window around the two
 * vanilla glyph methods and {@link GlStateManagerImmediateMixin} records the immediate calls that arrive inside it;
 * the {@code glEnd} that closes a glyph submits the four collected vertices to the renderer's existing immediate path
 * as one {@code POSITION_TEX_COLOR} quad - the layout, pipeline and acceptance rules the GUI's own format-2 batches
 * already use. Nothing outside the window is looked at, so no general GL-to-Vulkan conversion of immediate mode is
 * introduced: a mod's own {@code glBegin}/{@code glVertex3f} stays a no-op and is counted here as out of scope, not
 * silently captured.
 * <p>
 * The state a glyph needs is read from the mirrors the renderer already keeps, never invented:
 * <ul>
 *   <li><b>colour</b> - the current {@code GlStateManager.color(r,g,b,a)} value, published by the existing colour hook
 *       in {@link GlStateManagerMixin} (the same call the backend's own colour mirror is fed from). GL's fixed
 *       pipeline multiplies a textured fragment by the current colour, so it is baked into the quad's four
 *       {@code COLOR_4UB} bytes - the format-2 vertex layout carries colour per vertex, and the pipeline's fragment
 *       stage computes {@code texture * v_Color}.</li>
 *   <li><b>texture</b> - not passed at all: the backend binds the texture its own mirror has (the same
 *       {@code GlStateManager.bindTexture} record the GUI's format-2 batches use), and {@code renderDefaultChar}
 *       binds the font's own texture with {@code bindTexture(locationFontTexture)} before emitting the quad.</li>
 *   <li><b>blend</b> - the mirrored {@code enableBlend}/{@code blendFunc} state, per group.</li>
 *   <li><b>projection and model view</b> - the mirrored matrix state the GUI already draws under.</li>
 * </ul>
 * The capture is limited to the GL-less configuration on purpose: with a real GL context the vanilla calls draw the
 * glyph themselves, so capturing it there would draw every glyph twice. In the bridge this class counts the windows
 * it declined and submits nothing.
 */
public final class CeltiumFontGlyphCapture {
    /**
     * The primitive modes a glyph arrives in. Vanilla opens both glyph methods with {@code glBegin(5)}, which is
     * {@code GL_TRIANGLE_STRIP} - four vertices in the order top-left, bottom-left, top-right, bottom-right - and the
     * strip's two triangles cover the same rectangle a quad would. The strip is passed through as the strip it is
     * rather than rewritten into a quad, so the native draw sees exactly the topology GL was given.
     * {@code GL_QUADS} is accepted as well because it is the same four-vertex rectangle and costs nothing to admit.
     */
    private static final int GL_TRIANGLE_STRIP = 5;
    private static final int GL_QUADS = 7;
    /** {@code VulkanImmediateFormats.POSITION_TEX_COLOR}, the layout the GUI's own quads use. */
    private static final int FORMAT_ID = 2;
    /** {@code VulkanImmediateFormats.POSITION_COLOR}: the untextured layout, used only by the isolation probe. */
    private static final int POSITION_COLOR_ID = 1;
    private static final int POSITION_COLOR_STRIDE = 16;
    /** 3 floats position, 2 floats texture coordinates, 4 unsigned bytes colour. */
    private static final int STRIDE = 24;
    private static final int MAX_VERTICES = 4;

    /** One glyph's worth of vertices, written per glyph and handed to the backend by address. */
    private static final ByteBuffer VERTEX = ByteBuffer.allocateDirect(STRIDE * MAX_VERTICES)
            .order(ByteOrder.nativeOrder());
    /** The isolation probe's buffer: the same four vertices without texture coordinates. */
    private static final ByteBuffer ISOLATED = ByteBuffer.allocateDirect(POSITION_COLOR_STRIDE * MAX_VERTICES)
            .order(ByteOrder.nativeOrder());

    /** True while one of the two vanilla glyph methods is running. */
    private static boolean window;
    /** True while the calls inside that window are worth recording: GL-less, and a backend is installed at submit. */
    private static boolean armed;

    /**
     * A diagnostic override for the glyph colour, {@code -Dceltium.font.debugRgb=0xRRGGBB}, off by default.
     * <p>
     * "The quads are accepted" and "the quads are visible" are different claims, and only a pixel can settle the
     * second. Painting every native glyph in one unmistakable colour separates "the geometry never reaches the frame"
     * from "it reaches the frame and is invisible", which no counter can.
     */
    private static final int DEBUG_RGB = Integer.getInteger("celtium.font.debugRgb", -1).intValue();

    /**
     * {@code -Dceltium.font.isolation=positionColor} submits each glyph as the untextured {@code POSITION_COLOR}
     * layout instead of {@code POSITION_TEX_COLOR}, off by default.
     * <p>
     * "The batch is accepted" and "a pixel changed" are separate claims. This probe keeps the geometry, the colour and
     * the submit path identical and drops only the texture: if the frame then shows coloured blocks where the glyphs
     * are, the geometry reaches the image and the sampling is what failed; if it shows nothing, the geometry never
     * arrives. It is a diagnostic, not a mode: nothing but an explicit property turns it on.
     */
    private static final boolean ISOLATE_POSITION_COLOR =
            "positionColor".equals(System.getProperty("celtium.font.isolation", ""));

    /**
     * {@code -Dceltium.font.mode=quads} offers the same four vertices as {@code GL_QUADS} instead of the strip vanilla
     * used, off by default.
     * <p>
     * The strip and the quad cover the same rectangle, so this isolates the one thing that differs between the glyph
     * batch and every GUI batch that is known to reach the frame: the primitive mode.
     */
    private static final boolean FORCE_QUADS = "quads".equals(System.getProperty("celtium.font.mode", ""));

    /**
     * {@code -Dceltium.font.screenFill=true} replaces the first submitted glyph with one huge quad, off by default.
     * <p>
     * The routing question - does anything offered from this path reach the image the window shows - is answered by a
     * quad that covers the whole frame: if the frame does not turn magenta, the batch was accepted into an image the
     * present never shows, and no amount of glyph arithmetic can fix that.
     */
    private static final boolean SCREEN_FILL = Boolean.getBoolean("celtium.font.screenFill");
    private static boolean screenFillUsed;

    /**
     * {@code -Dceltium.font.matrixProbe=true} prints the live projection and model view at a bounded number of glyphs.
     * <p>
     * A quad that is accepted and still lands nowhere is either drawn outside the frame or drawn with matrices other
     * than the ones the rest of the GUI uses. The two look identical in every counter; the matrices at the moment of
     * the glyph are what tells them apart, and they are read from the same mirror the backend pushes.
     */
    private static final boolean MATRIX_PROBE = Boolean.getBoolean("celtium.font.matrixProbe");
    private static int matrixProbeLines;

    /** The current colour, as the last {@code GlStateManager.color} call left it. */
    private static float colorRed = 1.0f;
    private static float colorGreen = 1.0f;
    private static float colorBlue = 1.0f;
    private static float colorAlpha = 1.0f;

    private static int mode = -1;
    private static int vertexCount;
    private static int texCoordCount;
    private static final float[] positionX = new float[MAX_VERTICES];
    private static final float[] positionY = new float[MAX_VERTICES];
    private static final float[] positionZ = new float[MAX_VERTICES];
    private static final float[] textureU = new float[MAX_VERTICES];
    private static final float[] textureV = new float[MAX_VERTICES];
    /** Why the sequence in flight cannot become a quad, or null when it still can. */
    private static String localRefusal;

    private static long windows;
    private static long defaultWindows;
    private static long unicodeWindows;
    private static long bridgeWindows;
    private static long quads;
    private static long submitted;
    /** Wall time spent inside the backend's batch call, so the per-glyph cost is measured rather than assumed. */
    private static long submitNanos;
    private static long accepted;
    private static long refused;
    private static boolean firstQuadReported;
    private static final Map<String, Integer> REFUSALS = new LinkedHashMap<String, Integer>();
    /** One progress line per this many outcomes, and at most {@link #PROGRESS_LINES} of them. */
    private static final int PROGRESS_STRIDE = 2000;
    private static final int PROGRESS_LINES = 24;
    private static int progressLines;

    private CeltiumFontGlyphCapture() {
    }

    /**
     * Publishes the current colour.
     * <p>
     * Called from the existing {@code GlStateManager.color} hook, which sees every colour vanilla sets (the three
     * argument overload delegates to the four argument one). A call whose value the GlStateManager cache already holds
     * still reports the colour GL is using, so recording every call is recording the truth.
     */
    public static void currentColor(float red, float green, float blue, float alpha) {
        colorRed = red;
        colorGreen = green;
        colorBlue = blue;
        colorAlpha = alpha;
    }

    /**
     * Opens the capture window for one glyph method call.
     *
     * @param unicode whether the call came from {@code renderUnicodeChar} rather than {@code renderDefaultChar}, so the
     *                two entry points are counted apart: they are the font's whole drawing surface and a run has to say
     *                which of them it exercised.
     */
    public static void openWindow(boolean unicode) {
        windows++;
        if (unicode) {
            unicodeWindows++;
        } else {
            defaultWindows++;
        }
        // The bridge keeps its GL drawing: capturing there would draw the glyph twice. A frame with no backend yet -
        // the splash and loading screens - has nothing to draw into either, so it is skipped at the window rather than
        // counted as a refusal: measured, that is 291500 glyph quads in 45 s of startup and none of them geometry.
        boolean glless = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled();
        if (!glless) {
            bridgeWindows++;
        }
        armed = glless && TerrainGpuBackends.current() != null;
        reset();
    }

    /** Closes the window. Called on the glyph method's normal return, and after the glyph's own {@code glEnd}. */
    public static void closeWindow() {
        window = false;
        armed = false;
        reset();
    }

    private static void reset() {
        mode = -1;
        vertexCount = 0;
        texCoordCount = 0;
        localRefusal = null;
    }

    /** {@code GlStateManager.glBegin}: the mode the sequence will be drawn with. */
    public static void immediateBegin(int glMode) {
        if (!armed) {
            return;
        }
        if (mode != -1 || vertexCount != 0) {
            // A second begin without an end: the first sequence is dropped rather than mixed with the second.
            localRefusal = "overlapping-begin";
        }
        mode = glMode;
        vertexCount = 0;
        texCoordCount = 0;
    }

    /** {@code GlStateManager.glTexCoord2f}. */
    public static void immediateTexCoord(float s, float t) {
        if (!armed) {
            return;
        }
        if (texCoordCount >= MAX_VERTICES) {
            localRefusal = "texcoords-over-four";
            return;
        }
        textureU[texCoordCount] = s;
        textureV[texCoordCount] = t;
        texCoordCount++;
    }

    /** {@code GlStateManager.glVertex3f}. */
    public static void immediateVertex(float x, float y, float z) {
        if (!armed) {
            return;
        }
        if (vertexCount >= MAX_VERTICES) {
            localRefusal = "vertices-over-four";
            return;
        }
        positionX[vertexCount] = x;
        positionY[vertexCount] = y;
        positionZ[vertexCount] = z;
        vertexCount++;
    }

    /**
     * {@code GlStateManager.glEnd}: the sequence is complete, so it is offered to the renderer's immediate path.
     * <p>
     * The window closes here as well, which keeps it as short as the glyph itself: the calls that follow the glyph's
     * own {@code glEnd} are outside the font and are not the font's geometry.
     */
    public static void immediateEnd() {
        if (!armed) {
            return;
        }
        quads++;
        if (localRefusal != null) {
            refuse(localRefusal, shape());
        } else if (mode != GL_TRIANGLE_STRIP && mode != GL_QUADS) {
            refuse("mode-" + mode, shape());
        } else if (vertexCount != MAX_VERTICES) {
            refuse("vertex-count-" + vertexCount, shape());
        } else if (texCoordCount != MAX_VERTICES) {
            refuse("texcoord-count-" + texCoordCount, shape());
        } else if (!finite()) {
            refuse("non-finite-vertex", shape());
        } else {
            submit();
        }
        closeWindow();
    }

    private static boolean finite() {
        for (int i = 0; i < MAX_VERTICES; i++) {
            if (!Float.isFinite(positionX[i]) || !Float.isFinite(positionY[i]) || !Float.isFinite(positionZ[i])
                    || !Float.isFinite(textureU[i]) || !Float.isFinite(textureV[i])) {
                return false;
            }
        }
        return true;
    }

    /** Writes the glyph into the native vertex buffer and offers it as one POSITION_TEX_COLOR quad. */
    private static void submit() {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        long vertexAddress;
        if (backend == null) {
            refuse("no-backend", shape());
            return;
        }
        int red = byteValue(colorRed);
        int green = byteValue(colorGreen);
        int blue = byteValue(colorBlue);
        int alpha = byteValue(colorAlpha);
        if (DEBUG_RGB >= 0) {
            red = (DEBUG_RGB >> 16) & 0xFF;
            green = (DEBUG_RGB >> 8) & 0xFF;
            blue = DEBUG_RGB & 0xFF;
            alpha = 0xFF;
        }
        VERTEX.clear();
        for (int i = 0; i < MAX_VERTICES; i++) {
            VERTEX.putFloat(positionX[i]);
            VERTEX.putFloat(positionY[i]);
            VERTEX.putFloat(positionZ[i]);
            VERTEX.putFloat(textureU[i]);
            VERTEX.putFloat(textureV[i]);
            VERTEX.put((byte) red);
            VERTEX.put((byte) green);
            VERTEX.put((byte) blue);
            VERTEX.put((byte) alpha);
        }
        if (SCREEN_FILL && !screenFillUsed) {
            screenFillUsed = true;
            float[] huge = { -10000.0f, -10000.0f, 10000.0f, 10000.0f };
            positionX[0] = huge[0]; positionY[0] = huge[0];
            positionX[1] = huge[0]; positionY[1] = huge[3];
            positionX[2] = huge[2]; positionY[2] = huge[0];
            positionX[3] = huge[2]; positionY[3] = huge[3];
            for (int i = 0; i < MAX_VERTICES; i++) {
                textureU[i] = 0.0f;
                textureV[i] = 0.0f;
            }
        }
        int drawMode = FORCE_QUADS ? GL_QUADS : mode;
        int formatId = FORMAT_ID;
        int stride = STRIDE;
        Object format = DefaultVertexFormats.POSITION_TEX_COLOR;
        if (ISOLATE_POSITION_COLOR) {
            // Rewrite in place: the texture coordinates are dropped and the colour moves from offset 20 to offset 12.
            java.nio.ByteBuffer isolated = ISOLATED;
            isolated.clear();
            for (int i = 0; i < MAX_VERTICES; i++) {
                isolated.putFloat(positionX[i]);
                isolated.putFloat(positionY[i]);
                isolated.putFloat(positionZ[i]);
                isolated.put((byte) red);
                isolated.put((byte) green);
                isolated.put((byte) blue);
                isolated.put((byte) alpha);
            }
            formatId = POSITION_COLOR_ID;
            stride = POSITION_COLOR_STRIDE;
            format = DefaultVertexFormats.POSITION_COLOR;
            vertexAddress = MemoryUtil.memAddress(isolated);
        } else {
            vertexAddress = MemoryUtil.memAddress(VERTEX);
        }
        submitted++;
        if (MATRIX_PROBE && matrixProbeLines < 12 && (submitted < 4 || submitted % 2000 == 0)) {
            matrixProbeLines++;
            System.out.println("[Celtium] CELTIUM_FONT_GLYPH_MATRIX submitted=" + submitted + " quad=(" + positionX[0]
                    + "," + positionY[0] + ")-(" + positionX[2] + "," + positionY[2] + ") " + matrices());
        }
        boolean taken;
        long submitStarted = System.nanoTime();
        try {
            taken = backend.submitImmediateBatch(format, stride, formatId, vertexAddress, MAX_VERTICES, drawMode);
        } catch (Throwable error) {
            refuse("submit-threw", String.valueOf(error));
            return;
        } finally {
            submitNanos += System.nanoTime() - submitStarted;
        }
        if (taken) {
            accepted++;
            if (!firstQuadReported) {
                firstQuadReported = true;
                System.out.println("[Celtium] CELTIUM_FONT_GLYPH first: quad=(" + positionX[0] + "," + positionY[0]
                        + ")-(" + positionX[2] + "," + positionY[2] + ") uv=(" + textureU[0] + "," + textureV[0]
                        + ")-(" + textureU[2] + "," + textureV[2] + ") rgba=" + red + "," + green + "," + blue + ","
                        + alpha + " texture=" + celtium$boundTexture() + " windows=" + windows
                        + " defaultWindows=" + defaultWindows + " unicodeWindows=" + unicodeWindows
                        + " (offered as format " + formatId + " stride " + stride + " mode " + drawMode + ")");
            }
            reportProgress();
        } else {
            String reason = backend.lastRefusal();
            refuse("backend", (reason == null || reason.isEmpty() ? "no reason recorded" : reason));
        }
    }

    /** {@return the live projection and model view, read from the same GL mirror the backend pushes}. */
    private static String matrices() {
        try {
            java.nio.FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
            java.nio.FloatBuffer modelView = org.lwjgl.BufferUtils.createFloatBuffer(16);
            net.minecraft.client.renderer.GlStateManager.getFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, projection);
            net.minecraft.client.renderer.GlStateManager.getFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
            return "proj=[x " + projection.get(0) + " y " + projection.get(5) + " z " + projection.get(10) + " w "
                    + projection.get(15) + " tx " + projection.get(12) + " ty " + projection.get(13) + " tz "
                    + projection.get(14) + "] mvT=(" + modelView.get(12) + "," + modelView.get(13) + ","
                    + modelView.get(14) + ")";
        } catch (RuntimeException | LinkageError error) {
            return "matrices-unreadable: " + error;
        }
    }

    /** {@return the GL name the font's own texture is bound under, or 0 when the read fails}. */
    private static int celtium$boundTexture() {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        } catch (RuntimeException | LinkageError error) {
            return 0;
        }
    }

    /** {@return a float colour component as the unsigned byte the vertex layout carries}. */
    private static int byteValue(float value) {
        int scaled = Math.round(value * 255.0f);
        return scaled < 0 ? 0 : scaled > 255 ? 255 : scaled;
    }

    private static String shape() {
        return "mode=" + mode + " vertices=" + vertexCount + " texcoords=" + texCoordCount;
    }

    /** Records one refused glyph, printing the first of each reason so the run says why text is still missing. */
    private static void refuse(String reason, String detail) {
        refused++;
        String key = reason;
        Integer seen = REFUSALS.get(key);
        if (seen == null) {
            REFUSALS.put(key, Integer.valueOf(1));
            System.out.println("[Celtium] CELTIUM_FONT_GLYPH_REFUSED reason=" + reason + " detail=" + detail
                    + " (first of this reason; refused=" + refused + " of " + quads + " glyph quads)");
        } else {
            REFUSALS.put(key, Integer.valueOf(seen.intValue() + 1));
        }
        reportProgress();
    }

    /** One progress line every {@link #PROGRESS_STRIDE} outcomes, capped so a bad run cannot flood the log. */
    private static void reportProgress() {
        if (progressLines >= PROGRESS_LINES) {
            return;
        }
        long outcomes = accepted + refused;
        if (outcomes < (progressLines + 1) * PROGRESS_STRIDE) {
            return;
        }
        progressLines++;
        System.out.println("[Celtium] CELTIUM_FONT_GLYPH submitted=" + submitted + " accepted=" + accepted
                + " refused=" + refused + " windows=" + windows + " defaultWindows=" + defaultWindows
                + " unicodeWindows=" + unicodeWindows + " bridgeWindows=" + bridgeWindows
                + " submitMillis=" + (submitNanos / 1000000L)
                + " meanSubmitMicros=" + (submitted == 0 ? 0 : submitNanos / submitted / 1000L)
                + " reasons=" + REFUSALS);
    }

    /** {@return the one-line counter summary, for the report}. */
    public static synchronized String summary() {
        return "windows=" + windows + " defaultWindows=" + defaultWindows + " unicodeWindows=" + unicodeWindows
                + " bridgeWindows=" + bridgeWindows + " quads=" + quads + " submitted=" + submitted
                + " accepted=" + accepted + " refused=" + refused + " submitMillis=" + (submitNanos / 1000000L)
                + " reasons=" + REFUSALS;
    }
}
