package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Mirrors the {@code GlStateManager} calls that select drawing state into the Vulkan backend.
 * <p>
 * This is the state half of the Forge API swap: a mod (or vanilla) that sets blend, depth, colour or the matrix
 * stack through Minecraft's own API is captured here, without changing its code and without touching any public
 * Minecraft signature. The calls are mirrored only for now - the original body still runs - so the game keeps
 * rendering while the Vulkan pipelines that consume this state are added; once those land, the same injectors become
 * cancellable and the GL call stops happening.
 * <p>
 * Every injector uses {@code require = 0}: a method name that this particular build does not have must degrade to
 * "not mirrored", never to a failed mixin that stops the client from starting. (An earlier revision used a wrong SRG
 * name and the client exited during startup, which is exactly what this guards against.) Every mirror is also
 * null-checked and exception-guarded, because it runs inside vanilla rendering.
 * <p>
 * The names are taken from the decompiled sources: {@code func_179147_l}/{@code func_179084_k} enable and disable
 * blend, {@code func_179132_a} sets the depth mask, {@code func_179097_i}/{@code func_179126_j} disable and enable
 * the depth test, {@code func_179131_c} sets the colour, {@code func_179094_E}/{@code func_179121_F} push and pop the
 * matrix, and {@code func_187428_a} sets the separate blend factors.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    /**
     * Counts one display-list replay. The geometry inside a display list never passes through the uploader the immediate
     * path hooks, so this is the only place the size of that missing path can be measured from - and it is where a native
     * replacement will have to draw from, once the vertices are captured at compile time.
     */
    /**
     * Counts one display-list replay and, when the native path draws the list, cancels the GL body.
     * <p>
     * {@code cancellable = true} is what makes {@link CallbackInfo#cancel()} mean anything: with it false - the
     * default - {@code cancel()} throws {@code CancellationException}, the catch below swallows it as if a measurement
     * had failed, the census still counts a cancellation and the original GL body runs anyway. That is exactly the
     * silent double draw this annotation now prevents, and {@link #celtium$observeCallListExecuted(int, CallbackInfo)}
     * exists to make it measurable: a HEAD cancellation returns before the original body, so the RETURN hook fires
     * only for lists that really executed on GL.
     */
    @Inject(method = "callList", at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$countCallList(int list, CallbackInfo info) {
        try {
            org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replay(list);
            // Lab switch: cancel the replay of lists the native path already draws. The sky and the stars are drawn by
            // the immediate path as POSITION_TEX, so their display lists are the first candidates - cancelling them is
            // only correct if the image does not change, which is what the flag is for measuring.
            // The experiment draws the list itself; the GL replay is cancelled only when that draw actually happened.
            // No name and no counter takes part in the decision: "another path drew this" is the whole justification, and
            // when the experiment is off nothing is drawn and nothing is cancelled.
            if (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.modelDrawEnabled()) {
                boolean drawn = false;
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                    // Asked of GL here: the model's own texture is bound at replay time, and it is not the texture the
                    // immediate path happened to leave behind.
                    int boundTexture = 0;
                    int activeUnit = -1;
                    try {
                        activeUnit = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
                        boundTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                        if (!org.soutalab.celtium.vulkan.VulkanDisplayListCensus.textureLogged(list)) {
                            System.out.println("[Celtium] CELTIUM_MODEL_TEXTURE list=" + list + " unit=" + activeUnit
                                    + " texture=" + boundTexture);
                        }
                    } catch (RuntimeException | LinkageError ignored) {
                        boundTexture = 0;
                    }
                    drawn = vulkan.celtium$drawModelList(list, boundTexture);
                }
                if (drawn) {
                    // Cancelled first, counted second: the census exists to say that the GL body did not run, so it
                    // must not be written before the cancellation that makes that true has actually taken effect.
                    info.cancel();
                    org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled(list);
                    celtium$callListCancelled++;
                    if (celtium$callListCancelled == 1 || celtium$callListCancelled % 500 == 0) {
                        // Bounded, and printed from the cancelled path so it appears only in a run that really took a
                        // GL replay over. `executed` counts GL bodies that ran; equal counts with a non-zero
                        // cancellation count would mean the cancellation is not real.
                        System.out.println("[Celtium] CELTIUM_CANCEL_STATE cancelled=" + celtium$callListCancelled
                                + " executed=" + celtium$callListExecuted + " failures="
                                + celtium$callListCancelFailures);
                    }
                }
            }
        } catch (java.util.concurrent.CancellationException notCancellable) {
            // Reaching here means the annotation lost its cancellable flag again: the exception is not a measurement
            // failing, it is the cancellation not happening. Loud and bounded, because the alternative is a silent
            // double draw that every counter still reports as a native takeover.
            celtium$callListCancelFailures++;
            if (celtium$callListCancelFailures <= 4) {
                System.out.println("[Celtium] CELTIUM_CANCEL_NOT_CANCELLABLE list=" + list + " - the callList"
                        + " injection is not cancellable, so the GL body ran as well: " + notCancellable);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Never let a measurement stop the game's rendering.
        }
    }

    /**
     * Fires only when the original {@code callList} body actually ran, because a cancelled HEAD callback returns
     * before it. Compared against {@link #celtium$callListCancelled} it says whether a native model draw really
     * replaced the GL replay: equal counters with a non-zero cancellation count mean the cancellation is not real.
     */
    @Inject(method = "callList", at = @At("RETURN"), require = 0)
    private static void celtium$observeCallListExecuted(int list, CallbackInfo info) {
        celtium$callListExecuted++;
    }

    /** Cancellations that took effect, GL bodies that ran anyway, and the times the annotation was not cancellable. */
    private static int celtium$callListCancelled;
    private static int celtium$callListExecuted;
    private static int celtium$callListCancelFailures;

    /**
     * The display list's lifetime, mirrored into the model capture (audit N02).
     * <p>
     * A display list is not "geometry that arrived once": {@code glNewList} replaces the list with that name, and
     * {@code glDeleteLists} ends it, with the name later handed out again. The capture has to see both, or a recompile
     * appends its quads to the previous compile's geometry and a deleted list's mesh keeps drawing. These two hooks
     * are the lifetime signal, and they are taken from the game's own API rather than from GL, so they hold in
     * both the GL-less window and the bridge.
     * <p>
     * The names are SRG because these are vanilla {@code GlStateManager} methods, not Forge additions: measured with
     * javap against the vanilla client jar, where only {@code bus.class} (GlStateManager) names {@code glNewList} and
     * {@code glDeleteLists}, so both are renamed at runtime ({@code callList}, used above, is Forge-added and keeps its
     * name). {@code require = 0} as everywhere else in this class: a build whose names differ must degrade to "not
     * hooked", never to a client that will not start.
     */
    @Inject(method = "func_187423_f", at = @At("HEAD"), require = 0)
    private static void celtium$newList(int list, int mode, CallbackInfo info) {
        try {
            org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listCompileStarted(list);
        } catch (RuntimeException | LinkageError ignored) {
            // Never let the capture stop the game's own compile.
        }
    }

    @Inject(method = "func_187449_e", at = @At("HEAD"), require = 0)
    private static void celtium$deleteLists(int list, int range, CallbackInfo info) {
        try {
            org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listsDeleted(list, range);
        } catch (RuntimeException | LinkageError ignored) {
            // Same rule: a lifetime signal that fails is a measurement lost, not a frame lost.
        }
    }

    // The counters are read from the bounded log line inside the cancelled path rather than through an accessor:
    // Mixin rejects a non-private method in a mixin class ("contains non-private static method"), and that rejection
    // is a hard crash at class-load time, not a warning.

    @Inject(method = "func_179147_l", at = @At("HEAD"), require = 0)
    private static void celtium$enableBlend(CallbackInfo info) {
        celtium$mirror(backend -> backend.setBlendEnabled(true));
    }

    @Inject(method = "func_179084_k", at = @At("HEAD"), require = 0)
    private static void celtium$disableBlend(CallbackInfo info) {
        celtium$mirror(backend -> backend.setBlendEnabled(false));
    }

    @Inject(method = "func_179132_a", at = @At("HEAD"), require = 0)
    private static void celtium$depthMask(boolean mask, CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthMask(mask));
    }

    @Inject(method = "func_179097_i", at = @At("HEAD"), require = 0)
    private static void celtium$disableDepth(CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthTest(false));
    }

    @Inject(method = "func_179126_j", at = @At("HEAD"), require = 0)
    private static void celtium$enableDepth(CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthTest(true));
    }

    @Inject(method = "func_179092_a", at = @At("HEAD"), require = 0)
    private static void celtium$alphaFunc(int glFunc, float reference, CallbackInfo info) {
        celtium$mirror(backend -> backend.setAlphaFunc(glFunc, reference));
    }

    @Inject(method = "func_179098_w", at = @At("HEAD"), require = 0)
    private static void celtium$enableTexture2d(CallbackInfo info) {
        celtium$mirror(backend -> backend.setTexture2dEnabled(true));
    }

    @Inject(method = "func_179090_x", at = @At("HEAD"), require = 0)
    private static void celtium$disableTexture2d(CallbackInfo info) {
        celtium$mirror(backend -> backend.setTexture2dEnabled(false));
    }

    @Inject(method = "func_179144_i", at = @At("HEAD"), require = 0)
    private static void celtium$bindTexture(int glTextureId, CallbackInfo info) {
        celtium$mirror(backend -> backend.bindTexture(glTextureId));
    }

    @Inject(method = "func_179131_c", at = @At("HEAD"), require = 0)
    private static void celtium$color(float red, float green, float blue, float alpha, CallbackInfo info) {
        // Published for the one path that needs the current colour as vertex data rather than as pipeline state: the
        // font's glyph quads are POSITION_TEX_COLOR, whose fragment stage computes texture * v_Color, so the colour
        // has to travel in the vertices. This hook is the same call the backend's own colour mirror is fed from
        // (formats 6, 7 and 9 read it back for their group tint), so both see one value and cannot disagree. It is
        // recorded before the mirror so a backend-less frame still leaves the font with the colour vanilla set.
        CeltiumFontGlyphCapture.currentColor(red, green, blue, alpha);
        celtium$mirror(backend -> backend.setColor(red, green, blue, alpha));
    }

    /** The depth comparison, which the pipelines baked as LESS_OR_EQUAL until the state model carried it. */
    @Inject(method = "func_179143_c", at = @At("HEAD"), require = 0)
    private static void celtium$depthFunc(int func, CallbackInfo info) {
        celtium$mirror(backend -> backend.depthFunc(func));
    }

    @Inject(method = "func_179094_E", at = @At("HEAD"), require = 0)
    private static void celtium$pushMatrix(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::pushMatrix);
    }

    @Inject(method = "func_179121_F", at = @At("HEAD"), require = 0)
    private static void celtium$popMatrix(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::popMatrix);
    }

    /** Blend factors arrive as enum ordinals, which is what the backend's state model records. */
    @Inject(method = "func_187428_a", at = @At("HEAD"), require = 0)
    private static void celtium$tryBlendFuncSeparate(GlStateManager.SourceFactor sourceRgb,
                                                    GlStateManager.DestFactor destRgb,
                                                    GlStateManager.SourceFactor sourceAlpha,
                                                    GlStateManager.DestFactor destAlpha, CallbackInfo info) {
        // The GL constant each constant carries, not its ordinal: the enum stores the real factor (SRC_ALPHA is 770)
        // and reading that table once keeps the mirror correct even if the declaration order ever changes.
        int src = celtium$glFactor(SOURCE_FACTORS, sourceRgb);
        int dst = celtium$glFactor(DEST_FACTORS, destRgb);
        int srcAlpha = celtium$glFactor(SOURCE_FACTORS, sourceAlpha);
        int dstAlpha = celtium$glFactor(DEST_FACTORS, destAlpha);
        celtium$mirror(backend -> backend.setBlendFunc(src, dst, srcAlpha, dstAlpha));
    }

    // Matrix *contents* matter as much as the stack: without them an immediate draw uses identities and lands in
    // the wrong place (which is exactly what the first end-to-end attempt measured).
    @Inject(method = "func_179128_n", at = @At("HEAD"), require = 0)
    private static void celtium$matrixMode(int mode, CallbackInfo info) {
        celtium$mirror(backend -> backend.matrixMode(mode));
    }

    @Inject(method = "func_179096_D", at = @At("HEAD"), require = 0)
    private static void celtium$loadIdentity(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::loadIdentity);
    }

    @Inject(method = "func_179130_a", at = @At("HEAD"), require = 0)
    private static void celtium$ortho(double left, double right, double bottom, double top, double near, double far,
                                      CallbackInfo info) {
        celtium$mirror(backend -> backend.ortho(left, right, bottom, top, near, far));
    }

    @Inject(method = "func_179137_b", at = @At("HEAD"), require = 0)
    private static void celtium$translate(double x, double y, double z, CallbackInfo info) {
        celtium$mirror(backend -> backend.translate(x, y, z));
    }

    /**
     * The float translation, which is the overload every entity, tile entity, item and camera offset goes through.
     * <p>
     * {@code func_179109_b} is {@code GlStateManager.translate(float,float,float)} and its body calls
     * {@code GL11.glTranslatef}; the double overload above is {@code func_179137_b} and calls {@code glTranslated}. The
     * GL-less stub lists {@code glTranslatef} as a state-set no-op, so before this hook the mirror saw only the camera:
     * measured as translateCalls=59 in a 59-frame run with dozens of tile entities rendered, and as every model list
     * compiling with a zero translation. The mirror has to see what GL sees, or every model - the enchanting table's
     * book is the reported case - is drawn at the camera origin instead of where its block is.
     */
    @Inject(method = "func_179109_b", at = @At("HEAD"), require = 0)
    private static void celtium$translateFloat(float x, float y, float z, CallbackInfo info) {
        celtium$mirror(backend -> backend.translate(x, y, z));
    }

    /**
     * The double scale, the counterpart of the already hooked float scale. The mirror keeps float components, so the
     * cast is where the two overloads meet - the values Minecraft scales models by are small and exact in float.
     */
    @Inject(method = "func_179139_a", at = @At("HEAD"), require = 0)
    private static void celtium$scaleDouble(double x, double y, double z, CallbackInfo info) {
        celtium$mirror(backend -> backend.scale((float) x, (float) y, (float) z));
    }

    /**
     * Texture matrices are per unit, so the mirror must know which unit a {@code glMatrixMode(GL_TEXTURE)} sequence
     * addresses: the lightmap writes its own every frame, and without this it lands on unit 0's stack. The argument
     * is the GL enum ({@code GL_TEXTURE0 + n}); only the unit offset is kept.
     * <p>
     * BH-06's remaining half: this hook was written with the deobfuscated name while every other injection in this
     * mixin uses the SRG name, and {@code require = 0} made the mismatch silent - measured, the mirror never saw a
     * single unit switch in a whole leg (`CELTIUM_UNIT_SWITCH lines: 0`, `bindsOnUnit0=786 bindsOnOtherUnits=0`), so
     * every bind, the light map's included, landed in unit 0's slot. {@code func_179138_g} is
     * {@code GlStateManager.setActiveTexture(I)V} per {@code scripts/mc-sources.sh --srg setActiveTexture}, and
     * {@code require = 1} now fails loudly if a future mapping moves it rather than silently dropping the hook.
     */
    @Inject(method = "func_179138_g", at = @At("HEAD"), require = 1)
    private static void celtium$setActiveTexture(int texture, CallbackInfo info) {
        celtium$mirror(backend -> backend.activeTextureUnit(texture - org.lwjgl.opengl.GL13.GL_TEXTURE0));
    }

    @Inject(method = "func_179083_b", at = @At("HEAD"), require = 0)
    private static void celtium$viewport(int x, int y, int width, int height, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend != null) backend.viewport(x, y, width, height);
    }

    @Inject(method = "func_179114_b", at = @At("HEAD"), require = 0)
    private static void celtium$rotate(float angle, float x, float y, float z, CallbackInfo info) {
        celtium$mirror(backend -> backend.rotate(angle, x, y, z));
    }

    /**
     * The blend factors, which the mirror never saw.
     * <p>
     * The audit lists the integer {@code blendFunc} overloads as part of S07, and this is the one whose body was confirmed
     * to call {@code GL11.glBlendFunc}. The particle family was asserting its own blend because the mirror could not report
     * these; with them captured the pipeline can use what Minecraft actually set.
     */
    /**
     * A clear is an ordering boundary (audit S10).
     * <p>
     * Accepted batches are held until a flush; a clear wipes the target immediately, so anything still held would be
     * drawn <em>after</em> the clear rather than before it. Flushing here keeps the frame's order - the same rule the
     * refusal sites already follow.
     */
    @Inject(method = "func_179086_m", at = @At("HEAD"), require = 0)
    private static void celtium$clear(int mask, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend != null) backend.flushBeforeClear(mask); // ordering failure must not be swallowed as a state hint
    }

    @Inject(method = "func_179112_b", at = @At("HEAD"), require = 0)
    private static void celtium$blendFunc(int sourceFactor, int destinationFactor, CallbackInfo info) {
        celtium$mirror(backend -> backend.blendFunc(sourceFactor, destinationFactor));
    }

    /**
     * The integer {@code tryBlendFuncSeparate}, which is the canonical one: the enum overload above delegates to it,
     * and it is the overload a caller with plain GL constants uses ({@code JsonBlendingMode} is the one in the game's
     * own code). BH-07: the mixin hooked the enum overload alone, so an integer call never reached the mirror - and
     * the two overloads are different SRG names, read in the SRG table rather than guessed
     * ({@code (IIII)V -> func_179120_a}, the enum one {@code func_187428_a}).
     * <p>
     * Feeding both is not a double update in the sense that matters: the enum overload converts its constants and
     * calls this one, so the two hooks write the same four values in the same call. The reason to hook this one as
     * well is every caller that starts here.
     */
    @Inject(method = "func_179120_a", at = @At("HEAD"), require = 0)
    private static void celtium$tryBlendFuncSeparateInt(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha,
                                                        CallbackInfo info) {
        celtium$mirror(backend -> backend.setBlendFunc(sourceRgb, destRgb, sourceAlpha, destAlpha));
    }

    /**
     * BH-07's second half: the blend equation, which bakes into the same pipeline attachment as the factors.
     * {@code GlStateManager.glBlendEquation} is a straight pass-through to {@code GL14.glBlendEquation}, so without a
     * hook the equation never reaches the mirror - {@code JsonBlendingMode} is the caller in the game's own code, and
     * the SRG table calls this one {@code func_187398_d}.
     */
    @Inject(method = "func_187398_d", at = @At("HEAD"), require = 0)
    private static void celtium$blendEquation(int equation, CallbackInfo info) {
        celtium$mirror(backend -> backend.blendEquation(equation, equation));
    }

    /**
     * BH-10: {@code GlStateManager.colorMask}, which is cached (it calls {@code GL11.glColorMask} only when the mask
     * changes) - the Fancy cloud's depth-only pass is the caller that matters ({@code RenderGlobal:1655}). SRG name
     * {@code func_179135_a}. Feeding the mirror here does not replace the stub's record, which is what the capture
     * reads under GL-less; the two receive the same calls, and this half is what makes the bridge path carry the mask
     * as well.
     */
    @Inject(method = "func_179135_a", at = @At("HEAD"), require = 0)
    private static void celtium$colorMask(boolean red, boolean green, boolean blue, boolean alpha, CallbackInfo info) {
        int mask = (red ? 1 : 0) | (green ? 2 : 0) | (blue ? 4 : 0) | (alpha ? 8 : 0);
        celtium$mirror(backend -> backend.colorMask(mask));
    }

    @Inject(method = "func_179152_a", at = @At("HEAD"), require = 0)
    private static void celtium$scale(float x, float y, float z, CallbackInfo info) {
        celtium$mirror(backend -> backend.scale(x, y, z));
    }

    @Inject(method = "func_179110_a", at = @At("HEAD"), require = 0)
    private static void celtium$multiplyMatrix(java.nio.FloatBuffer buffer, CallbackInfo info) {
        celtium$mirror(backend -> backend.multiplyMatrix(buffer));
    }

    /**
     * The alpha test's enable pair, and the lighting pair, on the names the SRG table actually gives them.
     * <p>
     * <b>Corrected.</b> The two hooks that used to stand here were named {@code celtium$enableLighting} /
     * {@code celtium$disableLighting} but injected into {@code func_179141_d} / {@code func_179118_c}, which
     * {@code scripts/mc-sources.sh --srg} resolves as {@code enableAlpha} / {@code disableAlpha} - so the alpha test
     * was never mirrored (the mirror's {@code alphaTestEnabled} stayed false and {@code celtium$captureState} read a
     * reference of 0 for every batch), while the mirror's lighting flag was driven by the font and
     * {@code drawGradientRect}'s alpha calls. The real lighting pair is {@code func_179145_e} /
     * {@code func_179140_f}, and it was not hooked at all.
     * <p>
     * The name and the target are now checked against the table rather than against the handler's own name: every
     * other {@code @Inject} in this class was audited the same way in the same pass and resolved to the method its
     * handler claims (the audit and its result are in the task report).
     */
    @Inject(method = "func_179141_d", at = @At("HEAD"), require = 0)
    private static void celtium$enableAlpha(CallbackInfo info) {
        celtium$mirror(backend -> backend.setAlphaTestEnabled(true));
    }

    @Inject(method = "func_179118_c", at = @At("HEAD"), require = 0)
    private static void celtium$disableAlpha(CallbackInfo info) {
        celtium$mirror(backend -> backend.setAlphaTestEnabled(false));
    }

    // Fixed-function lighting state. Item and entity rendering in 1.12.2 is lit by GL_LIGHTING (RenderHelper sets two
    // directional lights); without these mirrors a native item draw could never reproduce the fixed-pipeline shading.
    @Inject(method = "func_179145_e", at = @At("HEAD"), require = 0)
    private static void celtium$enableLighting(CallbackInfo info) {
        celtium$mirror(backend -> backend.setLightingEnabled(true));
    }

    @Inject(method = "func_179140_f", at = @At("HEAD"), require = 0)
    private static void celtium$disableLighting(CallbackInfo info) {
        celtium$mirror(backend -> backend.setLightingEnabled(false));
    }

    @Inject(method = "func_179085_a", at = @At("HEAD"), require = 0)
    private static void celtium$enableLight(int index, CallbackInfo info) {
        celtium$mirror(backend -> backend.setLightEnabled(index, true));
    }

    @Inject(method = "func_179122_b", at = @At("HEAD"), require = 0)
    private static void celtium$disableLight(int index, CallbackInfo info) {
        celtium$mirror(backend -> backend.setLightEnabled(index, false));
    }

    /**
     * {@code glLight}: the pnames a lit draw actually reads are GL_POSITION (the direction, which the mirror folds the
     * current modelview into the way the driver would) and GL_DIFFUSE. Light indices map GL_LIGHT0+index onto 0 and 1.
     */
    @Inject(method = "func_187438_a", at = @At("HEAD"), require = 0)
    private static void celtium$glLight(int light, int pname, java.nio.FloatBuffer params, CallbackInfo info) {
        int index = light - 16384; // GL_LIGHT0
        if (index < 0 || index >= 2 || params == null || params.remaining() < 3) {
            return;
        }
        int pos = params.position();
        float x = params.get(pos);
        float y = params.get(pos + 1);
        float z = params.get(pos + 2);
        if (pname == 4611) { // GL_POSITION
            celtium$mirror(backend -> backend.setLightPosition(index, x, y, z));
        } else if (pname == 4609) { // GL_DIFFUSE
            celtium$mirror(backend -> backend.setLightDiffuse(index, x, y, z));
        }
    }

    @Inject(method = "func_187424_a", at = @At("HEAD"), require = 0)
    private static void celtium$glLightModel(int pname, java.nio.FloatBuffer params, CallbackInfo info) {
        // 2899 is GL_LIGHT_MODEL_AMBIENT - the scene ambient term the lit eval multiplies the vertex colour by.
        if (pname != 2899 || params == null || params.remaining() < 3) {
            return;
        }
        int pos = params.position();
        float r = params.get(pos);
        float g = params.get(pos + 1);
        float b = params.get(pos + 2);
        celtium$mirror(backend -> backend.setLightAmbient(r, g, b));
    }

    @Inject(method = "func_179103_j", at = @At("HEAD"), require = 0)
    private static void celtium$shadeModel(int mode, CallbackInfo info) {
        celtium$mirror(backend -> backend.setShadeModel(mode));
    }

    private static final int[] SOURCE_FACTORS = celtium$factorTable(GlStateManager.SourceFactor.values());
    private static final int[] DEST_FACTORS = celtium$factorTable(GlStateManager.DestFactor.values());

    /**
     * Reads the OpenGL constant out of each blend-factor enum constant.
     * <p>
     * Both enums declare exactly one {@code int} field, which is the GL factor they wrap, so finding it by type is
     * robust whereas assuming the declaration order is not. The table is built once.
     */
    private static int[] celtium$factorTable(Enum<?>[] values) {
        int[] table = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                for (java.lang.reflect.Field field : values[i].getDeclaringClass().getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        field.setAccessible(true);
                        table[i] = field.getInt(values[i]);
                        break;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                table[i] = 0;
            }
        }
        return table;
    }

    private static int celtium$glFactor(int[] table, Enum<?> value) {
        if (value == null) {
            return 0;
        }
        int ordinal = value.ordinal();
        return ordinal >= 0 && ordinal < table.length ? table[ordinal] : 0;
    }

    private static void celtium$mirror(Consumer<TerrainGpuBackend> action) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            action.accept(backend);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
