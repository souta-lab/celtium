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
    /**
     * Which draw mode the glyphs actually arrive as, counted per submission.
     * <p>
     * The coalesced run draws its accumulated vertices in one call with the run's mode. A run whose mode is a
     * triangle strip or fan joins consecutive glyphs (a strip of 4N vertices draws 4N-2 triangles, connecting one
     * glyph's last vertices to the next glyph's first), which is the shape of the 1-px bars the user reports in the
     * gaps between characters. Quads do not join. The census is what says which of the two this path is on - the
     * mode is read from the game's own glBegin, and this file must not assume it.
     */
    /**
     * The draw-mode census as a flat array: a ConcurrentHashMap.merge ran per glyph and cost ~1us in boxes and
     * hashing - at F3's ~15K glyphs a frame that alone was worth ~15ms of the 43ms F3 frame. Draw modes are small GL
     * enums (0..10), so an index beats a hash; modes outside the table still land in {@link #otherModes}.
     */
    /**
     * The char->glyph-column lookups vanilla performs per character as {@code String.indexOf} against the font-page
     * table string and the format-code strings - a linear scan of a ~256-char constant per glyph, ~7% of the F3
     * leg's warm samples between {@code String.indexOf}/{@code StringUTF16.indexOf}. The tables are compile-time
     * literals (interned, so identity-keyed is exact), and each one gets a direct-index map built once. Chars outside
     * the table's character range fall back to the string's own indexOf so a caller can never get a wrong answer.
     */
    private static final java.util.IdentityHashMap<String, int[]> CHAR_INDEX_CACHES =
            new java.util.IdentityHashMap<>();

    /** {@return the index {@code table.indexOf(ch)} would return}, from a per-table direct map. */
    public static int tableCharIndex(String table, int ch) {
        int[] cache = CHAR_INDEX_CACHES.get(table);
        if (cache == null) {
            char[] chars = table.toCharArray();
            int hi = 0;
            for (char c : chars) {
                if (c >= hi) {
                    hi = c + 1;
                }
            }
            cache = new int[Math.max(hi, 1)];
            java.util.Arrays.fill(cache, -1);
            for (int i = 0; i < chars.length; i++) {
                if (cache[chars[i]] == -1) {
                    // indexOf returns the first occurrence - later duplicates must not overwrite it.
                    cache[chars[i]] = i;
                }
            }
            CHAR_INDEX_CACHES.put(table, cache);
        }
        return ch >= 0 && ch < cache.length ? cache[ch] : table.indexOf(ch);
    }

    private static final int[] MODE_COUNTS = new int[16];
    private static int otherModes;
    private static final java.util.Map<Integer, Integer> OTHER_MODE_COUNTS = new java.util.HashMap<>();

    /**
     * The per-string quad buffer: glyphs accumulate here between {@link #endRenderString} flushes and go to the
     * backend in one {@code celtium$fontAppend} call instead of one call per glyph. Measured on the F3 leg, the
     * per-glyph call chain - submit, VERTEX pack, append - was ~2us/glyph, ~30ms of the 43ms F3 frame; the buffer
     * keeps the same vertex bytes and the same strip separators, so downstream nothing about the run changes.
     * <p>
     * Ordering is preserved three ways: a colour change flushes before the differently-coloured quad is written; a
     * unicode or debug-path glyph flushes then takes the per-glyph path itself; and every intervening or boundary
     * draw reaches it through {@link #flushBufferedQuads}, which the backend invokes from the same place it flushes
     * its own pending font run ({@code celtium$flushPendingFontBatch}) - a strikethrough quad inside a string is
     * submitted after the glyphs that came before it and before the ones that come after, exactly as per-glyph
     * submission ordered it.
     */
    private static ByteBuffer bufferedQuads;
    private static long bufferedQuadsBase;
    private static int bufferedVertexCount;
    private static int bufferedQuadCount;
    private static int bufferedColour = -1;
    private static boolean bufferedOpen;
    private static long bufferedFlushes;
    private static long bufferedFlushQuads;
    /** The window's family: unicode glyphs always take the per-glyph path, since their texture differs from the
     * default page's and the buffer must not fold them into the ASCII segment. */
    private static boolean windowUnicode;
    /** The backend the pre-flush hook is registered on, so a backend swap re-registers it. */
    private static Object bufferedHookBackend;

    /** The census in the same `{0:0 1:18 ...}` shape the map printed. */
    private static String modeCensusSummary() {
        StringBuilder line = new StringBuilder(96).append('{');
        for (int i = 0; i < MODE_COUNTS.length; i++) {
            line.append(i).append(':').append(MODE_COUNTS[i]).append(' ');
        }
        if (otherModes > 0) {
            line.append("other=").append(otherModes).append('=').append(OTHER_MODE_COUNTS).append(' ');
        }
        return line.append('}').toString();
    }


    /** One glyph's worth of vertices, written per glyph and handed to the backend by address. */
    private static final ByteBuffer VERTEX = ByteBuffer.allocateDirect(STRIDE * MAX_VERTICES)
            .order(ByteOrder.nativeOrder());
    /**
     * {@link #VERTEX}'s native base, cached: the buffer is allocated once, so writes through
     * {@code MemoryUtil.memPutFloat} skip the per-call bounds check {@code ByteBuffer.putFloat} pays - 24 checks per
     * glyph, ~15K glyphs a frame under F3.
     */
    private static final long VERTEX_BASE = MemoryUtil.memAddress(VERTEX);
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
     * batch and every GUI batch that is known to reach the frame: the primitive mode. It is a diagnostic; the shipped
     * path submits the strip vanilla emitted, unchanged.
     */
    private static final boolean FORCE_QUADS = "quads".equals(System.getProperty("celtium.font.mode", ""));

    /**
     * {@code -Dceltium.font.winding=swapped} offers the four vertices in the strip order with its middle pair exchanged
     * (top-left, top-right, bottom-left, bottom-right), off by default.
     * <p>
     * The rectangle and every vertex's texture coordinate are unchanged; only the winding is. It is the second half of
     * the "accepted and never seen" experiment: {@link #STATE_PROBE} reads whether culling is on at the glyph, and this
     * asks the other question - if the same four corners appear when their winding is exchanged, the batch was culled
     * for its winding rather than never drawn. Both are diagnostics and nothing but an explicit property turns either
     * on; neither is a mode the font is meant to run in.
     */
    private static final boolean SWAP_WINDING = "swapped".equals(System.getProperty("celtium.font.winding", ""));

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

    /**
     * {@code -Dceltium.font.stateProbe=true} prints the live GL state and the matrices at a bounded number of glyphs,
     * in either configuration, off by default.
     * <p>
     * The question it answers is whether a batch that is accepted and never seen was culled: the raster state that
     * decides that (culling, the front face, the depth test) is the game's, not the capture's, and it can only be read
     * at the moment of the glyph. Both the GL answer and the renderer's own mirrored matrices are printed, because the
     * draw uses the mirror while the stub answers the GL query from its own record, and a geometry that is accepted
     * and invisible is either drawn with the wrong matrix or not drawn at all. In the bridge the same line is the
     * reference: that is the state real OpenGL draws these glyphs correctly under, with the same vanilla vertex order.
     */
    private static final boolean STATE_PROBE = Boolean.getBoolean("celtium.font.stateProbe");
    private static int stateProbeLines;
    private static int stateProbeGuiLines;

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
    /**
     * The capture's own per-glyph cost, split from the backend's: {@code windowNanos} is the time in
     * {@code openWindow}/{@code closeWindow} - which decide {@code armed} by asking the stub and the backend registry on
     * every glyph - and {@code hookNanos} the time in the three immediate-call hooks that fill the vertex arrays.
     * <p>
     * They exist because the F3 overlay's frame cost was in no timer the renderer kept: with F3 on the cold-window p50
     * is 162 ms against 83 ms with it off (same jar, one flag), while {@code submitMillis} accounts for ~2 ms/frame and
     * the immediate path's own timers for ~1 ms/frame. The per-glyph Java-side work is the part no counter covered, and
     * a glyph is one window plus four texcoord calls, four vertex calls and one end - so it is the shape that can hide
     * tens of milliseconds per frame at ~5,900 glyphs per frame.
     */
    private static long windowNanos;
    private static long hookNanos;
    /**
     * The two brackets above the capture's own: the whole vanilla glyph method ({@code renderDefaultChar} /
     * {@code renderUnicodeChar}, opened and closed by {@link FontRendererMixin}) and the whole string
     * ({@code drawString(String, float, float, int, boolean)}, SRG {@code func_175065_a}).
     * <p>
     * They exist to split the F3 overlay's frame cost instead of adding another aggregate to it. Per frame the numbers
     * read: {@code stringMillis} is everything the overlay's text costs; subtracting {@code glyphMillis} leaves the
     * caller's own per-character work (the ASCII-table lookup, the style/width bookkeeping); subtracting this capture's
     * {@code windowMillis}, {@code hookMillis} and {@code submitMillis} from {@code glyphMillis} leaves the vanilla
     * glyph body (the {@code bindTexture} per glyph and the {@code GlStateManager} wrapper calls) - which is the
     * suspect the F3 measurement named and which no counter covered.
     */
    private static long glyphNanos;
    private static long glyphCalls;
    private static long stringNanos;
    private static long stringCalls;
    /**
     * The innermost string bracket: {@code FontRenderer.renderString(String, float, float, int, boolean)}, SRG
     * {@code func_180455_b} - the method that actually holds the per-character loop.
     * <p>
     * {@code stringMillis} above brackets {@code drawString(String, float, float, int, boolean)}, and that turned out
     * to be the wrong scope for the split: {@code renderStringAligned} and other callers reach {@code renderString}
     * directly, so {@code stringMillis - glyphMillis} measured ~0 (4,533 against 4,546 ms on one leg) and the caller's
     * own work was not separable. This bracket wraps the loop itself, for every entry point, so
     * {@code renderStringMillis - glyphMillis} is the caller's work and
     * {@code glyphMillis - (windowMillis + hookMillis + submitMillis)} is the vanilla glyph body.
     */
    private static long renderStringNanos;
    private static long renderStringCalls;
    private static long renderStringStartNanos;
    /**
     * {@code -Dceltium.font.fastPath=false} turns the Celtium-side default-glyph path off inside the same jar, so the
     * before/after is one variable: with it off every glyph goes through vanilla's body (the {@code bindTexture} chain
     * and the nine {@code GlStateManager} wrappers) and the capture window, exactly as before this path existed.
     */
    private static final boolean FAST_PATH = !"false".equalsIgnoreCase(
            System.getProperty("celtium.font.fastPath", "true"));
    /** Glyphs drawn by {@link #fastDefaultGlyph} rather than through vanilla's body, and the texture re-binds it needed. */
    private static long fastGlyphs;
    private static long fontTextureRebinds;
    private static long glyphStartNanos;
    private static long stringStartNanos;
    private static long accepted;
    private static long refused;
    /** Individual buffered quads the per-quad fallback could not place anywhere (B16). */
    private static long refusedQuads;
    private static boolean firstQuadReported;
    private static final Map<String, Integer> REFUSALS = new LinkedHashMap<String, Integer>();
    /**
     * One progress line per this many outcomes, and at most {@link #PROGRESS_LINES} of them.
     * <p>
     * The cap is 64 lines, not 24: a GUI scene offers more than 48000 glyph quads (measured 52406 in the video
     * settings scene and 75790 in its unicode variant), so a cap of 24 stopped the counter lines before the run did
     * and left the totals readable only by adding them up. The stride is 20000 rather than 2000 for the same reason
     * one order of magnitude up: an F3 overlay leg offers 1.22M windows, so a stride of 2000 stopped the lines at
     * 128000 and the run's real totals were readable only from the shutdown-hook summary - which a leg whose fixture
     * exits early never prints (measured: two of the strip-separator legs have no CELTIUM_FONT_GLYPH_SUMMARY at all).
     * 64 lines of 20000 covers 1.28M outcomes, which is this leg's size, and the log stays bounded.
     */
    private static final int PROGRESS_STRIDE = 20000;
    private static final int PROGRESS_LINES = 64;
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
        long started = System.nanoTime();
        try {
            openWindowBody(unicode);
        } finally {
            windowNanos += System.nanoTime() - started;
        }
    }

    private static void openWindowBody(boolean unicode) {
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
        windowUnicode = unicode;
        // The startup windows run before a backend exists and before the overlay matrices are installed (identity), so
        // the lines worth having are the armed ones - the glyphs that are actually offered to the renderer. Three of
        // those and three more under the overlay's orthographic projection, which is the state the GUI's text is drawn
        // with; the bridge prints the same pair from real OpenGL, so the two configurations can be read side by side.
        if (STATE_PROBE && stateProbeLines < 3) {
            stateProbeLines++;
            System.out.println("[Celtium] CELTIUM_FONT_STATE first glless=" + glless + " armed=" + armed
                    + " unicode=" + unicode + " " + glState() + " " + matrices() + " " + mirroredMatrices());
        } else if (STATE_PROBE && stateProbeGuiLines < 3 && overlayProjection()) {
            stateProbeGuiLines++;
            System.out.println("[Celtium] CELTIUM_FONT_STATE overlay glless=" + glless + " armed=" + armed
                    + " unicode=" + unicode + " " + glState() + " " + matrices() + " " + mirroredMatrices());
        }
        reset();
    }

    /** {@return whether the Celtium-side default-glyph path may draw a glyph itself right now}. */
    public static boolean fastPathAvailable() {
        return FAST_PATH && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                && TerrainGpuBackends.current() != null;
    }

    /** Records that the fast path had to re-bind the font sheet because another texture had taken the unit. */
    public static void noteFontTextureRebind() {
        fontTextureRebinds++;
    }

    /**
     * Draws one default-page glyph without vanilla's body, and hands it to the same submit the capture window uses.
     * <p>
     * The geometry, the texture coordinates, the colour and the advance are copied from {@code renderDefaultChar}
     * ({@code FontRenderer.java:230-249}) exactly: {@code i = ch % 16 * 8}, {@code j = ch / 16 * 8},
     * {@code k = italic ? 1 : 0}, {@code l = charWidth[ch]}, {@code f = l - 0.01F}, the four vertices
     * {@code (posX+k, posY)}, {@code (posX-k, posY+7.99)}, {@code (posX+f-1+k, posY)}, {@code (posX+f-1-k, posY+7.99)},
     * and the four texture coordinates {@code (i/128, j/128)}, {@code (i/128, (j+7.99)/128)},
     * {@code ((i+f-1)/128, j/128)}, {@code ((i+f-1)/128, (j+7.99)/128)}. What is not copied is the cost: the
     * {@code TextureManager.bindTexture} chain (the caller binds the sheet when the unit no longer holds it) and the
     * nine {@code GlStateManager} wrapper calls per glyph, each a stub no-op plus a hook. The submit is the capture's
     * own - format 2, stride 24, mode 5, the coalesced run and the strip separators - so the format, glyph and unicode
     * contracts and the batching are the ones that already existed.
     *
     * @return the advance vanilla would have returned, or -1 when this glyph must take the window path
     */
    public static float fastDefaultGlyph(int[] charWidth, float posX, float posY, int ch, boolean italic) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || charWidth == null || ch < 0 || ch >= charWidth.length) {
            return -1.0F;
        }
        int i = ch % 16 * 8;
        int j = ch / 16 * 8;
        int k = italic ? 1 : 0;
        int l = charWidth[ch];
        float f = (float) l - 0.01F;
        windows++;
        defaultWindows++;
        fastGlyphs++;
        armed = true;
        windowUnicode = false;
        mode = GL_TRIANGLE_STRIP;
        vertexCount = MAX_VERTICES;
        texCoordCount = MAX_VERTICES;
        // The buffered path writes the quad straight into the segment with no float-array round trip through the
        // capture arrays - at F3's ~15K glyphs a frame the arrays were a second copy of every vertex.
        if (DEBUG_RGB < 0 && !SCREEN_FILL && !ISOLATE_POSITION_COLOR && !FORCE_QUADS) {
            int packed = byteValue(colorRed) | (byteValue(colorGreen) << 8) | (byteValue(colorBlue) << 16)
                    | (byteValue(colorAlpha) << 24);
            if (bufferedOpen && packed != bufferedColour) {
                flushBufferedQuads(backend);
            }
            float x0 = posX + k, x1 = posX - k;
            float x2 = posX + f - 1.0F + k, x3 = posX + f - 1.0F - k;
            float u0 = i / 128.0F, u1 = (i + f - 1.0F) / 128.0F;
            float v0 = j / 128.0F, v1 = (j + 7.99F) / 128.0F;
            if (bufferQuad(backend, packed,
                    x0, posY, u0, v0, x1, posY + 7.99F, u0, v1,
                    x2, posY, u1, v0, x3, posY + 7.99F, u1, v1)) {
                submitted++;
                MODE_COUNTS[GL_TRIANGLE_STRIP]++;
                armed = false;
                reset();
                return (float) l;
            }
        }
        {
            float[] xs = {posX + (float) k, posX - (float) k, posX + f - 1.0F + (float) k,
                    posX + f - 1.0F - (float) k};
            float[] ys = {posY, posY + 7.99F, posY, posY + 7.99F};
            float[] us = {(float) i / 128.0F, (float) i / 128.0F, ((float) i + f - 1.0F) / 128.0F,
                    ((float) i + f - 1.0F) / 128.0F};
            float[] vs = {(float) j / 128.0F, ((float) j + 7.99F) / 128.0F, (float) j / 128.0F,
                    ((float) j + 7.99F) / 128.0F};
            for (int n = 0; n < MAX_VERTICES; n++) {
                positionX[n] = xs[n];
                positionY[n] = ys[n];
                positionZ[n] = 0.0F;
                textureU[n] = us[n];
                textureV[n] = vs[n];
            }
        }
        submit();
        armed = false;
        reset();
        return (float) l;
    }

    /**
     * One POSITION_TEX_COLOR glyph quad into the per-string segment, with its strip separator when the segment is
     * not empty.
     *
     * @return {@code true} when the quad was buffered; {@code false} only when the segment cannot grow to hold it
     */
    private static boolean bufferQuad(TerrainGpuBackend backend, int packed,
            float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1,
            float x2, float y2, float u2, float v2, float x3, float y3, float u3, float v3) {
        int needed = (bufferedQuadCount == 0 ? MAX_VERTICES : MAX_VERTICES + 2) * STRIDE;
        if (!ensureBufferedCapacity(bufferedVertexCount * STRIDE + needed)) {
            return false;
        }
        long base = bufferedQuadsBase + (long) bufferedVertexCount * STRIDE;
        if (bufferedQuadCount > 0) {
            // The strip separator: the previous glyph's last vertex, then this glyph's first.
            MemoryUtil.memCopy(base - STRIDE, base, STRIDE);
            base += STRIDE;
            emitVertex(base, x0, y0, u0, v0, packed);
            base += STRIDE;
        }
        emitVertex(base, x0, y0, u0, v0, packed);
        base += STRIDE;
        emitVertex(base, x1, y1, u1, v1, packed);
        base += STRIDE;
        emitVertex(base, x2, y2, u2, v2, packed);
        base += STRIDE;
        emitVertex(base, x3, y3, u3, v3, packed);
        bufferedVertexCount += bufferedQuadCount == 0 ? MAX_VERTICES : MAX_VERTICES + 2;
        bufferedQuadCount++;
        bufferedOpen = true;
        bufferedColour = packed;
        registerBufferedHook(backend);
        return true;
    }

    private static void emitVertex(long base, float x, float y, float u, float v, int packed) {
        MemoryUtil.memPutFloat(base, x);
        MemoryUtil.memPutFloat(base + 4, y);
        MemoryUtil.memPutFloat(base + 8, 0.0F);
        MemoryUtil.memPutFloat(base + 12, u);
        MemoryUtil.memPutFloat(base + 16, v);
        MemoryUtil.memPutInt(base + 20, packed);
    }

    /**
     * The unicode-page-0 glyph, drawn by Celtium when {@code unicodeFlag} routes every character through
     * {@code renderUnicodeChar} ({@code FontRenderer.java:270-299}): ja_jp and {@code forceUnicodeFont} clients take
     * that path for the whole string, so without this the fast path was a dead letter for them - measured on leg
     * p-fu at 71.5ms frame p50 with 880K unicode windows and zero fast glyphs.
     * <p>
     * The geometry is vanilla's exactly: {@code i = glyphWidth[ch] & 255} (high nibble the left edge, low the
     * width), {@code f = i>>>4}, {@code f1 = (i&15)+1}, {@code f2 = ch%16*16 + f},
     * {@code f3 = (ch&255)/16*16}, {@code f4 = f1-f-0.02}, {@code f5 = italic ? 1 : 0}, and the advance
     * {@code (f1-f)/2+1}. Only page 0 ({@code ch < 256}) is drawn here: pages bind a different texture per 256-char
     * block, so a wider char falls back to the window path where the page texture binds per glyph.
     *
     * @return the advance vanilla would have returned, 0 for a glyph vanilla skips, or -1 to take the window path
     */
    public static float fastUnicodeGlyph(byte[] glyphWidth, float posX, float posY, char ch, boolean italic) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || glyphWidth == null || ch >= 256) {
            return -1.0F;
        }
        int i = glyphWidth[ch] & 255;
        if (i == 0) {
            // Vanilla returns before binding a texture or emitting anything: the advance is zero and so is the draw.
            return 0.0F;
        }
        float f = i >>> 4;
        float f1 = (i & 15) + 1;
        float f2 = (float) (ch % 16 * 16) + f;
        float f3 = (float) ((ch & 255) / 16 * 16);
        float f4 = f1 - f - 0.02F;
        float f5 = italic ? 1.0F : 0.0F;
        windows++;
        unicodeWindows++;
        fastGlyphs++;
        armed = true;
        windowUnicode = true;
        mode = GL_TRIANGLE_STRIP;
        vertexCount = MAX_VERTICES;
        texCoordCount = MAX_VERTICES;
        if (DEBUG_RGB < 0 && !SCREEN_FILL && !ISOLATE_POSITION_COLOR && !FORCE_QUADS) {
            int packed = byteValue(colorRed) | (byteValue(colorGreen) << 8) | (byteValue(colorBlue) << 16)
                    | (byteValue(colorAlpha) << 24);
            if (bufferedOpen && packed != bufferedColour) {
                flushBufferedQuads(backend);
            }
            float x0 = posX + f5, y0 = posY, u0 = f2 / 256.0F, v0 = f3 / 256.0F;
            float x1 = posX - f5, y1 = posY + 7.99F, v1 = (f3 + 15.98F) / 256.0F;
            float x2 = posX + f4 / 2.0F + f5;
            float x3 = posX + f4 / 2.0F - f5;
            float u1 = (f2 + f4) / 256.0F;
            if (bufferQuad(backend, packed,
                    x0, y0, u0, v0, x1, y1, u0, v1, x2, y0, u1, v0, x3, y1, u1, v1)) {
                submitted++;
                MODE_COUNTS[GL_TRIANGLE_STRIP]++;
                armed = false;
                reset();
                return (f1 - f) / 2.0F + 1.0F;
            }
        }
        {
            float[] xs = {posX + f5, posX - f5, posX + f4 / 2.0F + f5, posX + f4 / 2.0F - f5};
            float[] ys = {posY, posY + 7.99F, posY, posY + 7.99F};
            float[] us = {f2 / 256.0F, f2 / 256.0F, (f2 + f4) / 256.0F, (f2 + f4) / 256.0F};
            float[] vs = {f3 / 256.0F, (f3 + 15.98F) / 256.0F, f3 / 256.0F, (f3 + 15.98F) / 256.0F};
            for (int n = 0; n < MAX_VERTICES; n++) {
                positionX[n] = xs[n];
                positionY[n] = ys[n];
                positionZ[n] = 0.0F;
                textureU[n] = us[n];
                textureV[n] = vs[n];
            }
        }
        submit();
        armed = false;
        reset();
        return (f1 - f) / 2.0F + 1.0F;
    }

    private static final float[] ATLAS_UV = new float[4];

    /**
     * The whole-atlas glyph path: one texture for every character, so the quad's UVs address the baked sheet instead
     * of a per-page texture. {@code unicodePath} is {@code renderUnicodeChar}'s caller (unicode flag on - every char,
     * ASCII included, measures its width on {@code glyphWidth}); otherwise the caller is {@code renderDefaultChar}
     * and {@code ch} is the 0-255 sheet index whose width comes from {@code charWidth}.
     * <p>
     * The emitted vertices are vanilla's geometry exactly, in the capture's own buffered segment, so the run, its
     * strip separators and the one draw per string are unchanged - only the texture (the atlas) and the UVs differ.
     *
     * @return the advance vanilla would return, 0 for a glyph vanilla skips, or -1 to take the window path
     */
    public static float fastAtlasGlyph(byte[] glyphWidth, int[] charWidth, float posX, float posY, char ch,
            boolean italic, boolean unicodePath) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || !CeltiumFontAtlas.active()) {
            return -1.0F;
        }
        float x0, y0, x1, x2, x3, u0, v0, u1, v1, advance;
        if (unicodePath) {
            if (glyphWidth == null) {
                return -1.0F;
            }
            int i = glyphWidth[ch] & 255;
            if (i == 0) {
                return 0.0F;
            }
            CeltiumFontAtlas.unicodeUV(ch, glyphWidth, ATLAS_UV);
            float f4 = ((i & 15) + 1 - (i >>> 4)) - 0.02F;
            float f5 = italic ? 1.0F : 0.0F;
            x0 = posX + f5;
            y0 = posY;
            x1 = posX - f5;
            x2 = posX + f4 / 2.0F + f5;
            x3 = posX + f4 / 2.0F - f5;
            u0 = ATLAS_UV[0];
            v0 = ATLAS_UV[1];
            u1 = ATLAS_UV[2];
            v1 = ATLAS_UV[3];
            advance = CeltiumFontAtlas.unicodeAdvance(glyphWidth, ch);
        } else {
            int index = ch & 0xFFFF;
            if (charWidth == null || index >= charWidth.length) {
                return -1.0F;
            }
            int width = charWidth[index];
            float k = italic ? 1.0F : 0.0F;
            float f = (float) width - 0.01F;
            CeltiumFontAtlas.asciiUV(index, width, ATLAS_UV);
            x0 = posX + k;
            y0 = posY;
            x1 = posX - k;
            x2 = posX + f - 1.0F + k;
            x3 = posX + f - 1.0F - k;
            u0 = ATLAS_UV[0];
            v0 = ATLAS_UV[1];
            u1 = ATLAS_UV[2];
            v1 = ATLAS_UV[3];
            advance = (float) width;
        }
        windows++;
        fastGlyphs++;
        armed = true;
        windowUnicode = unicodePath;
        mode = GL_TRIANGLE_STRIP;
        vertexCount = MAX_VERTICES;
        texCoordCount = MAX_VERTICES;
        int packed = byteValue(colorRed) | (byteValue(colorGreen) << 8) | (byteValue(colorBlue) << 16)
                | (byteValue(colorAlpha) << 24);
        if (bufferedOpen && packed != bufferedColour) {
            flushBufferedQuads(backend);
        }
        if (bufferQuad(backend, packed,
                x0, y0, u0, v0, x1, y0 + 7.99F, u0, v1, x2, y0, u1, v0, x3, y0 + 7.99F, u1, v1)) {
            submitted++;
            MODE_COUNTS[GL_TRIANGLE_STRIP]++;
            armed = false;
            reset();
            return advance;
        }
        // The segment would not grow: fall back to the capture's own four-float path.
        positionX[0] = x0;
        positionY[0] = y0;
        positionZ[0] = 0.0F;
        textureU[0] = u0;
        textureV[0] = v0;
        positionX[1] = x1;
        positionY[1] = y0 + 7.99F;
        positionZ[1] = 0.0F;
        textureU[1] = u0;
        textureV[1] = v1;
        positionX[2] = x2;
        positionY[2] = y0;
        positionZ[2] = 0.0F;
        textureU[2] = u1;
        textureV[2] = v0;
        positionX[3] = x3;
        positionY[3] = y0 + 7.99F;
        positionZ[3] = 0.0F;
        textureU[3] = u1;
        textureV[3] = v1;
        submit();
        armed = false;
        reset();
        return advance;
    }

    /** Opens the vanilla glyph method's own bracket. Called from the mixin's HEAD injection. */
    public static void beginGlyph() {
        glyphStartNanos = System.nanoTime();
        glyphCalls++;
    }

    /** Closes it. Called from the mixin's RETURN injection, after {@link #closeWindow()}. */
    public static void endGlyph() {
        glyphNanos += System.nanoTime() - glyphStartNanos;
    }

    /** Opens the per-character loop's bracket. Called from {@code func_180455_b}'s HEAD injection. */
    public static void beginRenderString() {
        renderStringStartNanos = System.nanoTime();
        renderStringCalls++;
    }

    /** Closes it. Called from {@code func_180455_b}'s RETURN injection. */
    public static void endRenderString() {
        renderStringNanos += System.nanoTime() - renderStringStartNanos;
        flushBufferedQuads(null);
    }

    /**
     * Sends the buffered segment to the backend and resets it. Called at every point a per-glyph submission would
     * have gone out: the end of a string, a colour change, before a non-buffered glyph, and - through the hook the
     * backend invokes - before any intervening or boundary draw.
     *
     * @param backend the caller's backend, looked up when {@code null}
     */
    public static void flushBufferedQuads(TerrainGpuBackend backend) {
        if (!bufferedOpen) {
            return;
        }
        // Closed before the append, not after: the append can reach this method again through the backend's own
        // pending-run flush (a state-change inside celtium$fontAppend), and a re-entrant call must find nothing.
        // Measured as the leg that hung: the open flag let the hook re-enter and recurse until the timeout.
        bufferedOpen = false;
        if (backend == null) {
            backend = TerrainGpuBackends.current();
        }
        try {
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                boolean taken = vulkan.celtium$fontAppend(DefaultVertexFormats.POSITION_TEX_COLOR, STRIDE,
                        FORMAT_ID, bufferedQuadsBase, bufferedVertexCount, GL_TRIANGLE_STRIP);
                int takenThisFlush;
                if (taken) {
                    takenThisFlush = bufferedQuadCount;
                } else {
                    // The run refused (matrices dirty, a stall, off-main target): submit each stored quad through
                    // the ordinary path, in order. Quads sit at every sixth vertex past the separators between
                    // them - [Q][sep][Q][sep][Q] - so the walk is a fixed stride. B16: each quad's own result is
                    // counted - a refusal under the OR would previously promote the whole flush to accepted.
                    takenThisFlush = 0;
                    for (int k = 0; k < bufferedQuadCount; k++) {
                        if (backend.submitImmediateBatch(DefaultVertexFormats.POSITION_TEX_COLOR, STRIDE,
                                FORMAT_ID, bufferedQuadsBase + (long) k * 6 * STRIDE, MAX_VERTICES,
                                GL_TRIANGLE_STRIP)) {
                            takenThisFlush++;
                        }
                    }
                    if (takenThisFlush != bufferedQuadCount) {
                        refusedQuads += bufferedQuadCount - takenThisFlush;
                        refuse("flush-quad-refused", "quadCount=" + bufferedQuadCount
                                + " accepted=" + takenThisFlush);
                    }
                }
                accepted += takenThisFlush;
            } else if (backend != null) {
                for (int k = 0; k < bufferedQuadCount; k++) {
                    backend.submitImmediateBatch(DefaultVertexFormats.POSITION_TEX_COLOR, STRIDE,
                            FORMAT_ID, bufferedQuadsBase + (long) k * 6 * STRIDE, MAX_VERTICES,
                            GL_TRIANGLE_STRIP);
                }
            }
        } catch (Throwable error) {
            refuse("flush-threw", String.valueOf(error));
        } finally {
            bufferedFlushes++;
            bufferedFlushQuads += bufferedQuadCount;
            bufferedVertexCount = 0;
            bufferedQuadCount = 0;
            bufferedColour = -1;
        }
    }

    /** Grows {@link #bufferedQuads} until it can hold {@code bytes}; {@code false} only when allocation fails. */
    private static boolean ensureBufferedCapacity(int bytes) {
        ByteBuffer buffer = bufferedQuads;
        if (buffer == null) {
            int capacity = Math.max(4096, Integer.highestOneBit(Math.max(1, bytes - 1)) << 1);
            try {
                bufferedQuads = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            } catch (OutOfMemoryError e) {
                return false;
            }
            bufferedQuadsBase = MemoryUtil.memAddress(bufferedQuads);
            return true;
        }
        if (buffer.capacity() >= bytes) {
            return true;
        }
        try {
            int capacity = Integer.highestOneBit(Math.max(buffer.capacity(), bytes - 1)) << 1;
            ByteBuffer grown = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            MemoryUtil.memCopy(bufferedQuadsBase, MemoryUtil.memAddress(grown), buffer.capacity());
            bufferedQuads = grown;
            bufferedQuadsBase = MemoryUtil.memAddress(grown);
        } catch (OutOfMemoryError e) {
            return false;
        }
        return true;
    }

    /**
     * Registers the pre-flush hook on this backend, once per backend instance: at every place the backend flushes
     * its own pending font run - an intervening submission, a boundary, a state change - it invokes this flush
     * first, so the buffered glyphs land in that same run and keep their submission order against the draw that is
     * about to happen.
     */
    private static void registerBufferedHook(TerrainGpuBackend backend) {
        if (backend == bufferedHookBackend) {
            return;
        }
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$setFontPreFlush(CeltiumFontGlyphCapture::bufferedPreFlush);
            bufferedHookBackend = backend;
        }
    }

    /** The hook's shape: a {@code Runnable} that takes no backend and flushes on the current one. */
    private static void bufferedPreFlush() {
        flushBufferedQuads(null);
    }

    /** Opens the string bracket. Called from {@code func_175065_a}'s HEAD injection. */
    public static void beginString() {
        stringStartNanos = System.nanoTime();
        stringCalls++;
    }

    /** Closes it. Called from {@code func_175065_a}'s RETURN injection. */
    public static void endString() {
        stringNanos += System.nanoTime() - stringStartNanos;
    }

    /** Closes the window. Called on the glyph method's normal return, and after the glyph's own {@code glEnd}. */
    public static void closeWindow() {
        long started = System.nanoTime();
        try {
            window = false;
            armed = false;
            reset();
        } finally {
            windowNanos += System.nanoTime() - started;
        }
    }

    private static void reset() {
        mode = -1;
        vertexCount = 0;
        texCoordCount = 0;
        localRefusal = null;
    }

    /** {@code GlStateManager.glBegin}: the mode the sequence will be drawn with. */
    public static void immediateBegin(int glMode) {
        long started = System.nanoTime();
        try {
            immediateBeginBody(glMode);
        } finally {
            hookNanos += System.nanoTime() - started;
        }
    }

    private static void immediateBeginBody(int glMode) {
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
        long started = System.nanoTime();
        try {
            immediateTexCoordBody(s, t);
        } finally {
            hookNanos += System.nanoTime() - started;
        }
    }

    private static void immediateTexCoordBody(float s, float t) {
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
        long started = System.nanoTime();
        try {
            immediateVertexBody(x, y, z);
        } finally {
            hookNanos += System.nanoTime() - started;
        }
    }

    private static void immediateVertexBody(float x, float y, float z) {
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
        if (SCREEN_FILL && !screenFillUsed) {
            // Applied to the vertices before they are written, which is the whole of the probe: it was written after the
            // buffer had already been filled, so the frame-filling quad the log printed was never the quad the backend
            // received - the run reported a screen fill and submitted an ordinary glyph. The mutated arrays and the
            // submitted bytes have to be the same vertices for the probe to answer anything.
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
        int packed = red | (green << 8) | (blue << 16) | (alpha << 24);
        if (!windowUnicode && DEBUG_RGB < 0 && !SCREEN_FILL && !ISOLATE_POSITION_COLOR && !FORCE_QUADS
                && mode == GL_TRIANGLE_STRIP) {
            // The buffered path: write the quad - and the strip separator the run inserts between successive glyphs -
            // into the per-string buffer and return. The submission happens once per segment in flushBufferedQuads.
            if (bufferedOpen && packed != bufferedColour) {
                flushBufferedQuads(backend);
            }
            int quads = bufferedQuadCount;
            int needed = (quads == 0 ? MAX_VERTICES : MAX_VERTICES + 2) * STRIDE;
            if (ensureBufferedCapacity(bufferedVertexCount * STRIDE + needed)) {
                long base = bufferedQuadsBase + (long) bufferedVertexCount * STRIDE;
                if (quads > 0) {
                    // The separator: a copy of the previous glyph's last vertex and a copy of the new glyph's first -
                    // two zero-area triangles between the glyph strips, exactly what the backend inserts between
                    // successive appends.
                    MemoryUtil.memCopy(base - STRIDE, base, STRIDE);
                    base += STRIDE;
                    MemoryUtil.memPutFloat(base, positionX[0]);
                    MemoryUtil.memPutFloat(base + 4, positionY[0]);
                    MemoryUtil.memPutFloat(base + 8, positionZ[0]);
                    MemoryUtil.memPutFloat(base + 12, textureU[0]);
                    MemoryUtil.memPutFloat(base + 16, textureV[0]);
                    MemoryUtil.memPutInt(base + 20, packed);
                    base += STRIDE;
                }
                for (int i = 0; i < MAX_VERTICES; i++) {
                    int vertex = SWAP_WINDING ? (i == 1 ? 2 : i == 2 ? 1 : i) : i;
                    MemoryUtil.memPutFloat(base, positionX[vertex]);
                    MemoryUtil.memPutFloat(base + 4, positionY[vertex]);
                    MemoryUtil.memPutFloat(base + 8, positionZ[vertex]);
                    MemoryUtil.memPutFloat(base + 12, textureU[vertex]);
                    MemoryUtil.memPutFloat(base + 16, textureV[vertex]);
                    MemoryUtil.memPutInt(base + 20, packed);
                    base += STRIDE;
                }
                bufferedVertexCount += quads == 0 ? MAX_VERTICES : MAX_VERTICES + 2;
                bufferedQuadCount++;
                bufferedOpen = true;
                bufferedColour = packed;
                registerBufferedHook(backend);
                submitted++;
                MODE_COUNTS[GL_TRIANGLE_STRIP]++;
                return;
            }
            // Capacity could not grow: fall through to the per-glyph path for this quad.
        }
        if (bufferedOpen) {
            // Anything outside the buffered family - a unicode glyph, a debug probe, a QUADS-mode quad - orders
            // after every buffered glyph: the segment goes out first, then this one takes the per-glyph path.
            flushBufferedQuads(backend);
        }
        {
            long base = VERTEX_BASE;
            for (int i = 0; i < MAX_VERTICES; i++) {
                // The middle pair exchanged when the winding probe is on: the same four corners with the same texture
                // coordinates, in an order whose triangles face the other way.
                int vertex = SWAP_WINDING ? (i == 1 ? 2 : i == 2 ? 1 : i) : i;
                MemoryUtil.memPutFloat(base, positionX[vertex]);
                MemoryUtil.memPutFloat(base + 4, positionY[vertex]);
                MemoryUtil.memPutFloat(base + 8, positionZ[vertex]);
                MemoryUtil.memPutFloat(base + 12, textureU[vertex]);
                MemoryUtil.memPutFloat(base + 16, textureV[vertex]);
                MemoryUtil.memPutInt(base + 20, packed);
                base += STRIDE;
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
                int vertex = SWAP_WINDING ? (i == 1 ? 2 : i == 2 ? 1 : i) : i;
                isolated.putFloat(positionX[vertex]);
                isolated.putFloat(positionY[vertex]);
                isolated.putFloat(positionZ[vertex]);
                isolated.put((byte) red);
                isolated.put((byte) green);
                isolated.put((byte) blue);
                isolated.put((byte) alpha);
            }
            formatId = POSITION_COLOR_ID;
            stride = POSITION_COLOR_STRIDE;
            format = DefaultVertexFormats.POSITION_COLOR;
            // flip() before the address, not after. MemoryUtil.memAddress(ByteBuffer) returns the base PLUS THE
            // BUFFER'S POSITION, and the loop above left the position at the capacity - so the address handed to the
            // backend was one past the last written byte, and the native batch read 4*16 bytes of whatever followed.
            // Verified without a GPU (scripts/cpu-contract/GlyphAddressCheck.java): position=64, capacity=64,
            // memAddress(buf) - memAddress(buf,0) = 64, and 0 after flip. That is why glyphs were accepted and the
            // text was missing or garbage, with no validation error to show for it - nothing was out of bounds from
            // Vulkan's point of view, it was simply the wrong bytes.
            isolated.flip();
            if (isolated.position() != 0 || isolated.limit() != MAX_VERTICES * POSITION_COLOR_STRIDE) {
                refuse("address-extent", "isolated wrote " + isolated.limit() + " bytes, expected "
                        + (MAX_VERTICES * POSITION_COLOR_STRIDE));
                return;
            }
            vertexAddress = MemoryUtil.memAddress(isolated);
        } else {
            // clear(), not flip(): the memPut writes do not move the buffer's position, so flip would write
            // limit=0 and the extent check below would refuse every glyph. clear() restores position=0 with
            // limit=capacity - the same contract flip produced when putFloat advanced the position.
            VERTEX.clear();
            if (VERTEX.position() != 0 || VERTEX.limit() != MAX_VERTICES * STRIDE) {
                refuse("address-extent", "VERTEX wrote " + VERTEX.limit() + " bytes, expected "
                        + (MAX_VERTICES * STRIDE));
                return;
            }
            vertexAddress = MemoryUtil.memAddress(VERTEX);
        }
        submitted++;
        if (drawMode >= 0 && drawMode < MODE_COUNTS.length) {
            MODE_COUNTS[drawMode]++;
        } else {
            OTHER_MODE_COUNTS.merge(Integer.valueOf(drawMode), Integer.valueOf(1), Integer::sum);
            otherModes++;
        }
        if (MATRIX_PROBE && matrixProbeLines < 12 && (submitted < 4 || submitted % 2000 == 0)) {
            matrixProbeLines++;
            System.out.println("[Celtium] CELTIUM_FONT_GLYPH_MATRIX submitted=" + submitted + " quad=(" + positionX[0]
                    + "," + positionY[0] + ")-(" + positionX[2] + "," + positionY[2] + ") " + matrices());
        }
        boolean taken;
        long submitStarted = System.nanoTime();
        try {
            // task-59: the backend owns a pending run and appends this quad to it, so a string's glyphs go out in a
            // handful of calls instead of one per character (the user's session measured 1,012,751 calls and 47,246 ms
            // inside them, 46.6 us per glyph). The run is flushed at the entry of every other submission and at every
            // group/pass/frame boundary, which is what makes the coalescing order-safe. A backend that cannot take the
            // append (no run, a matrix change, a stall) falls back to the per-glyph submission below, unchanged.
            boolean appended = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && vulkan.celtium$fontAppend(format, stride, formatId, vertexAddress, MAX_VERTICES, drawMode);
            taken = appended
                    || backend.submitImmediateBatch(format, stride, formatId, vertexAddress, MAX_VERTICES, drawMode);
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

    /**
     * {@return whether the current projection is the overlay's orthographic one}, read the same way the matrix probe
     * reads it. Minecraft's {@code setupOverlayRendering} installs {@code ortho(0, width, height, 0, 1000, 3000)},
     * whose y scale is negative because the GUI's y axis points down; the world's perspective has a positive one.
     */
    private static boolean overlayProjection() {
        try {
            java.nio.FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
            net.minecraft.client.renderer.GlStateManager.getFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, projection);
            return projection.get(5) < 0.0f && projection.get(15) == 1.0f;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /**
     * {@return the live raster state the glyph is drawn under, read from the GL the game itself set}.
     * <p>
     * Culling is the one that decides whether a triangle of the glyph survives: real OpenGL draws the vanilla strip
     * with these very vertices and the same state, so a Vulkan pipeline that drops it is disagreeing with GL about
     * winding or about the front face, and only the state at the glyph can say which.
     */
    private static String glState() {
        try {
            return "cull=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE)
                    + " depthTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                    + " blend=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND)
                    + " alphaTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST)
                    + " texture2d=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D)
                    + " boundTexture=" + celtium$boundTexture()
                    + " frontFace=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE)
                    + " cullMode=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_CULL_FACE_MODE);
        } catch (RuntimeException | LinkageError error) {
            return "state-unreadable: " + error;
        }
    }

    /**
     * {@return the matrices the renderer itself will draw this glyph with}, read from the backend's own mirror.
     * <p>
     * {@link #matrices()} asks GL, which under the stub answers from the stub's own record; the draw uses the
     * backend's mirror. A geometry that is accepted and never seen is either drawn with the wrong matrix or not drawn
     * at all, and printing both at the same instant is what separates those two.
     */
    private static String mirroredMatrices() {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (!(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan)) {
                return "mirror=none";
            }
            float[] projection = vulkan.celtium$mirroredProjection();
            float[] modelView = vulkan.celtium$mirroredModelView();
            return "mirrorProj=[x " + projection[0] + " y " + projection[5] + " z " + projection[10] + " tx "
                    + projection[12] + " ty " + projection[13] + " tz " + projection[14] + "] mirrorMvT=("
                    + modelView[12] + "," + modelView[13] + "," + modelView[14] + ")";
        } catch (RuntimeException | LinkageError error) {
            return "mirror-unreadable: " + error;
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
                + " windowMillis=" + (windowNanos / 1000000L)
                + " hookMillis=" + (hookNanos / 1000000L)
                + " glyphMillis=" + (glyphNanos / 1000000L)
                + " stringMillis=" + (stringNanos / 1000000L)
                + " stringCalls=" + stringCalls
                + " renderStringMillis=" + (renderStringNanos / 1000000L)
                + " renderStringCalls=" + renderStringCalls
                + " fastGlyphs=" + fastGlyphs
                + " fontTextureRebinds=" + fontTextureRebinds
                + " meanSubmitMicros=" + (submitted == 0 ? 0 : submitNanos / submitted / 1000L)
                + " reasons=" + REFUSALS);
    }

    /** {@return the one-line counter summary, for the report}. */
    public static synchronized String summary() {
        return "windows=" + windows + " defaultWindows=" + defaultWindows + " unicodeWindows=" + unicodeWindows
                + " bridgeWindows=" + bridgeWindows + " quads=" + quads + " submitted=" + submitted
                + " accepted=" + accepted + " refused=" + refused + " refusedQuads=" + refusedQuads
                + " submitMillis=" + (submitNanos / 1000000L)
                + " windowMillis=" + (windowNanos / 1000000L) + " hookMillis=" + (hookNanos / 1000000L)
                + " glyphMillis=" + (glyphNanos / 1000000L) + " glyphCalls=" + glyphCalls
                + " stringMillis=" + (stringNanos / 1000000L) + " stringCalls=" + stringCalls
                + " renderStringMillis=" + (renderStringNanos / 1000000L)
                + " renderStringCalls=" + renderStringCalls
                + " fastGlyphs=" + fastGlyphs
                + " fontTextureRebinds=" + fontTextureRebinds
                + " reasons=" + REFUSALS + " modes=" + modeCensusSummary() + " " + batchSummary();
    }

    /**
     * {@return the backend's account of the coalesced runs}, so "batched" is observable in the same line as the
     * per-glyph cost it was meant to remove (task-59): runs started, quads appended, and how each run went out - by an
     * intervening submission, at a boundary, or on a state change.
     */
    private static String batchSummary() {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            return vulkan.celtium$fontBatchSummary();
        }
        return "fontRunsStarted=0 fontQuadsAppended=0 flushedIntervening=0 flushedBoundary=0 flushedStateChange=0"
                + " appendMillis=0 (no coalescing backend)";
    }

    /**
     * Prints {@link #summary()} once, when the JVM exits.
     * <p>
     * The progress lines stop after {@link #PROGRESS_LINES} of them, so the totals a run ends with were only ever
     * readable by adding them up; {@code summary()} existed for the report and nothing called it. A shutdown hook is
     * the one place that runs after the last glyph of every configuration - a run that is killed by the harness's
     * timeout included - and it only reads these counters, never the game.
     */
    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> System.out.println("[Celtium] CELTIUM_FONT_GLYPH_SUMMARY " + summary()),
                    "celtium-font-summary"));
        } catch (IllegalStateException | SecurityException ignored) {
            // A shutdown already in progress, or a security manager: the counters are a diagnostic, not a contract.
        }
    }
}
