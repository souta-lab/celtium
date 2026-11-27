package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.DisplayListManager;
import org.soutalab.celtium.glcompat.stacks.BlendStateStack;
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.soutalab.celtium.glcompat.stacks.Color4Stack;
import org.soutalab.celtium.glcompat.stacks.ColorMaskStack;
import org.soutalab.celtium.glcompat.states.ColorMask;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import org.soutalab.celtium.glcompat.stacks.IntegerStateStack;
import org.soutalab.celtium.glcompat.states.BlendState;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.hooks.DeferredBlendHandler;
import org.soutalab.celtium.glcompat.hooks.DeferredDepthColorHandler;
import org.soutalab.celtium.glcompat.hooks.GLSMConfig;
import org.soutalab.celtium.glcompat.hooks.GLSMHooks;
import org.lwjgl.opengl.GL11;

import static org.soutalab.celtium.glcompat.GLStateManager.BYPASS_CACHE;
import static org.soutalab.celtium.glcompat.GLStateManager.beforeUncapturedStateChange;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.isCachingEnabled;
import static org.soutalab.celtium.glcompat.GLStateManager.issueBlendFunc;
import static org.soutalab.celtium.glcompat.GLStateManager.mod;
import static org.soutalab.celtium.glcompat.GLStateManager.postBlendFuncChange;
import static org.soutalab.celtium.glcompat.GLStateManager.postVanillaBlendChangeIfMoved;
import static org.soutalab.celtium.glcompat.GLStateManager.snapshotVanillaBlendFunc;
import static org.soutalab.celtium.glcompat.GLStateManager.tryBlendFuncSeparate;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;
import static org.soutalab.celtium.glcompat.GLStateManager.postVanillaBlendChange;

public final class GLSMBlend {

    private GLSMBlend() {}

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || red != ctx().blendState.getBlendColorR() || green != ctx().blendState.getBlendColorG() || blue != ctx().blendState.getBlendColorB() || alpha != ctx().blendState.getBlendColorA()) {
            beforeUncapturedStateChange();
            if (caching) {
                mod(ctx().blendState);
                ctx().blendState.setBlendColorR(red);
                ctx().blendState.setBlendColorG(green);
                ctx().blendState.setBlendColorB(blue);
                ctx().blendState.setBlendColorA(alpha);
            }
            EXECUTOR.blendColor(red, green, blue, alpha);
        }
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        ctx().exitCapture.writeBlend();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean snapshotted = snapshotVanillaBlendFunc();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            ctx().blendState.beforeModify();
            bh.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            postVanillaBlendChangeIfMoved(snapshotted);
            return;
        }
        // Cache thread check - only update state on main thread, but always make GL call if needed
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass
                || ctx().blendState.isFuncUnknown()
                || ctx().blendState.getSrcRgb() != srcFactor
                || ctx().blendState.getDstRgb() != dstFactor
                || ctx().blendState.getSrcAlpha() != srcFactor
                || ctx().blendState.getDstAlpha() != dstFactor) {
            if (caching) {
                final BlendStateStack blend = mod(ctx().blendState);
                blend.setAll(srcFactor, dstFactor, srcFactor, dstFactor);
                blend.clearFuncUnknownState();
            }
            issueBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            postBlendFuncChange(srcFactor, dstFactor, srcFactor, dstFactor);
        }
        postVanillaBlendChangeIfMoved(snapshotted);
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glBlendEquationSeparate in display lists not yet implemented - if you see this, please report!");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || ctx().blendState.getEquationRgb() != modeRGB || ctx().blendState.getEquationAlpha() != modeAlpha) {
            beforeUncapturedStateChange();
            if (caching) {
                mod(ctx().blendState);
                ctx().blendState.setEquationRgb(modeRGB);
                ctx().blendState.setEquationAlpha(modeAlpha);
            }
            EXECUTOR.blendEquationSeparate(modeRGB, modeAlpha);
        }
    }

    public static void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        ctx().exitCapture.writeDepthColor(ExitStateCapture.COLOR_MASK);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColorMask(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        if (dch != null && dch.isDepthColorLocked()) {
            ctx().colorMask.beforeModify();
            dch.deferColorMask(red, green, blue, alpha);
            return;
        }
        // Cache thread check - only update state on main thread, but always make GL call if needed
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || red != ctx().colorMask.red || green != ctx().colorMask.green || blue != ctx().colorMask.blue || alpha != ctx().colorMask.alpha) {
            if (caching) mod(ctx().colorMask).setAll(red, green, blue, alpha);
            EXECUTOR.colorMask(red, green, blue, alpha);
        }
    }

    public static void glClearColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || red != ctx().clearColor.getRed() || green != ctx().clearColor.getGreen() || blue != ctx().clearColor.getBlue() || alpha != ctx().clearColor.getAlpha()) {
            if (caching) {
                mod(ctx().clearColor);
                ctx().clearColor.setRed(red);
                ctx().clearColor.setGreen(green);
                ctx().clearColor.setBlue(blue);
                ctx().clearColor.setAlpha(alpha);
            }
            EXECUTOR.clearColor(red, green, blue, alpha);
        }
    }

    public static void glLogicOp(int opcode) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLogicOp(opcode);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (!isCachingEnabled() || ctx().logicOpMode.getValue() != opcode) beforeUncapturedStateChange();
        if (isCachingEnabled()) mod(ctx().logicOpMode).setValue(opcode);
        EXECUTOR.logicOp(opcode);
    }

    public static void enableBlend() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordEnable(GL11.GL_BLEND);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            final boolean wasEnabled = ctx().blendMode.isEffectivelyEnabled();
            final DeferredBlendHandler bh = GLSMHooks.blendHandler;
            if (bh != null && bh.isBlendLocked()) {
                ctx().exitCapture.capability(GL11.GL_BLEND, -1);
                ctx().blendMode.beforeModify();
                bh.deferBlendModeToggle(true);
            } else {
                ctx().blendMode.enable();
            }
            if (GLSMConfig.hudCacheOverride) {
                GLSMConfig.hudCacheBlendEnabled = true;
            }
            if (!wasEnabled && ctx().blendMode.isEffectivelyEnabled()) {
                postVanillaBlendChange();
            }
        }

    public static void disableBlend() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordDisable(GL11.GL_BLEND);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            final boolean wasEnabled = ctx().blendMode.isEffectivelyEnabled();
            final DeferredBlendHandler bh = GLSMHooks.blendHandler;
            if (bh != null && bh.isBlendLocked()) {
                ctx().exitCapture.capability(GL11.GL_BLEND, -1);
                ctx().blendMode.beforeModify();
                bh.deferBlendModeToggle(false);
            } else {
                ctx().blendMode.disable();
            }
            if (GLSMConfig.hudCacheOverride) {
                GLSMConfig.hudCacheBlendEnabled = false;
            }
            if (wasEnabled && !ctx().blendMode.isEffectivelyEnabled()) {
                postVanillaBlendChange();
            }
        }

    public static boolean isEffectiveBlendEnabled() {
            return ctx().blendMode.isEffectivelyEnabled();
        }

    public static BlendState getEffectiveBlendState(BlendState out) {
            return ctx().blendState.readEffective(out);
        }

    public static ColorMask getEffectiveColorMask(ColorMask out) {
        return ctx().colorMask.readEffective(out);
    }

    public static void glBlendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        rejectExitCapture();
        EXECUTOR.blendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
        ctx().blendState.setFuncUnknownState();
    }

    public static void setHudCacheOverride(boolean enabled) {
        if (GLSMConfig.hudCacheOverride == enabled) return;
        GLSMConfig.hudCacheOverride = enabled;
        reissueBlendFunc();
    }

    /** Controls alpha coverage accumulation for the current context's translucency layer. */
    public static void setLayerBlending(boolean enabled) {
        final GLContextState glCtx = ctx();
        if (glCtx.layerBlending == enabled) return;
        GLStateManager.checkCompiling("setLayerBlending");
        beforeUncapturedStateChange();
        glCtx.layerBlending = enabled;
        reissueBlendFunc();
    }

    /** Returns whether the current context accumulates translucency layer coverage. */
    public static boolean isLayerBlending() {
        return ctx().layerBlending;
    }

    private static void reissueBlendFunc() {
        final BlendStateStack blend = ctx().blendState;
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isOverrideHeld()) {
            blend.setFuncUnknownState();
            return;
        }
        issueBlendFunc(blend);
        blend.clearFuncUnknownState();
    }

    public static void glBlendEquation(int mode) {
        rejectExitCapture();
            if (DisplayListManager.isRecording()) {
                throw new UnsupportedOperationException("glBlendEquation in display lists not yet implemented - if you see this, please report!");
            }
            final boolean caching = isCachingEnabled();
            final boolean bypass = BYPASS_CACHE || !caching;
            if (bypass || ctx().blendState.getEquationRgb() != mode || ctx().blendState.getEquationAlpha() != mode) {
                // The batch records the blend factors per draw but not the equation, so changing it
                // has to drain what is already queued.
                beforeUncapturedStateChange();
                if (caching) {
                    mod(ctx().blendState);
                    ctx().blendState.setEquationRgb(mode);
                    ctx().blendState.setEquationAlpha(mode);
                }
                EXECUTOR.blendEquation(mode);
            }
        }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            final GLContextState glCtx = ctx();
            glCtx.exitCapture.writeBlend();
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            final boolean snapshotted = snapshotVanillaBlendFunc();
            final DeferredBlendHandler bh = GLSMHooks.blendHandler;
            if (bh != null && bh.isBlendLocked()) {
                ctx().blendState.beforeModify();
                bh.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                postVanillaBlendChangeIfMoved(snapshotted);
                return;
            }
            // Cache thread check - only update state on main thread, but always make GL call if needed
            final boolean caching = isCachingEnabled();
            final boolean bypass = BYPASS_CACHE || !caching;
            final BlendStateStack blend = glCtx.blendState;
            if (bypass || blend.isFuncUnknown() || blend.getSrcRgb() != srcRgb || blend.getDstRgb() != dstRgb
                || blend.getSrcAlpha() != srcAlpha || blend.getDstAlpha() != dstAlpha) {
                if (caching) {
                    mod(blend).setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
                    blend.clearFuncUnknownState();
                }
                issueBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                postBlendFuncChange(srcRgb, dstRgb, srcAlpha, dstAlpha);
            }
            postVanillaBlendChangeIfMoved(snapshotted);
        }

    public static void defaultBlendFunc() {
            tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }

    public static BlendStateStack getBlendState() {
            return ctx().blendState;
        }

    public static BooleanStateStack getBlendMode() {
            return ctx().blendMode;
        }

    public static Color4Stack getClearColor() {
            return ctx().clearColor;
        }

    public static ColorMaskStack getColorMask() {
            return ctx().colorMask;
        }

    public static IntegerStateStack getLogicOpMode() {
            return ctx().logicOpMode;
        }

    public static BooleanStateStack getColorLogicOpState() {
            return ctx().colorLogicOpState;
        }

    public static BooleanStateStack getIndexLogicOpState() {
            return ctx().indexLogicOpState;
        }
}
