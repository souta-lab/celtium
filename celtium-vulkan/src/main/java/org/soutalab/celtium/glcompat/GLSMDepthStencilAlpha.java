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
import org.soutalab.celtium.glcompat.stacks.AlphaStateStack;
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.soutalab.celtium.glcompat.stacks.DepthStateStack;
import org.soutalab.celtium.glcompat.stacks.StencilStateStack;
import org.soutalab.celtium.glcompat.states.AlphaState;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.hooks.DeferredAlphaHandler;
import org.soutalab.celtium.glcompat.hooks.DeferredDepthColorHandler;
import org.soutalab.celtium.glcompat.hooks.GLSMConfig;
import org.soutalab.celtium.glcompat.hooks.GLSMHooks;
import org.lwjgl.opengl.GL11;

import static org.soutalab.celtium.glcompat.GLStateManager.BYPASS_CACHE;
import static org.soutalab.celtium.glcompat.GLStateManager.beforeUncapturedStateChange;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.isCachingEnabled;
import static org.soutalab.celtium.glcompat.GLStateManager.mod;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public final class GLSMDepthStencilAlpha {

    private GLSMDepthStencilAlpha() {}

    public static void glDepthFunc(int func) {
        ctx().exitCapture.write(ExitStateCapture.DEPTH_FUNC);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthFunc(func);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || func != ctx().depthState.getFunc()) {
            if (caching) mod(ctx().depthState).setFunc(func);
            EXECUTOR.depthFunc(func);
        }
    }

    public static void glDepthMask(boolean mask) {
        // The deferred entity overlay replay keeps depth writes off for every caller, including a
        // layer that asks for them itself or a mod subclass that overrides the layer.
        if (mask && GLSMConfig.forceDepthMaskOff) {
            mask = false;
        }
        ctx().exitCapture.writeDepthColor(ExitStateCapture.DEPTH_MASK);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        if (dch != null && dch.isDepthColorLocked()) {
            ctx().depthState.beforeModify();
            dch.deferDepthEnable(mask);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || mask != ctx().depthState.isMaskEnabled()) {
            if (caching) mod(ctx().depthState).setMaskEnabled(mask);
            EXECUTOR.depthMask(mask);
        }
    }

    public static void glClearDepth(double depth) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearDepth(depth);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) mod(ctx().depthState).setClearValue(depth);
        EXECUTOR.clearDepth(depth);
    }

    public static void glAlphaFunc(int function, float reference) {
        ctx().exitCapture.writeAlpha();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordAlphaFunc(function, reference);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
        if (ah != null && ah.isAlphaTestLocked()) {
            ctx().alphaState.beforeModify();
            ah.deferAlphaFunc(function, reference);
            return;
        }
        if (isCachingEnabled()
            && (function != ctx().alphaState.getFunction() || Float.compare(reference, ctx().alphaState.getReference()) != 0)) {
            mod(ctx().alphaState);
            ctx().alphaState.setFunction(function);
            ctx().alphaState.setReference(reference);
            ctx().fragmentGeneration++;
        }
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFunc(func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || ctx().stencilState.getFuncFront() != func || ctx().stencilState.getRefFront() != ref || ctx().stencilState.getValueMaskFront() != mask) {
            beforeUncapturedStateChange();
            if (caching) {
                mod(ctx().stencilState).setFunc(func, ref, mask);
            }
            EXECUTOR.stencilFunc(func, ref, mask);
        }
    }

    public static void glStencilMask(int mask) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || ctx().stencilState.getWriteMaskFront() != mask) {
            beforeUncapturedStateChange();
            if (caching) {
                mod(ctx().stencilState).setWriteMask(mask);
            }
            EXECUTOR.stencilMask(mask);
        }
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOp(fail, zfail, zpass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || ctx().stencilState.getFailOpFront() != fail || ctx().stencilState.getZFailOpFront() != zfail || ctx().stencilState.getZPassOpFront() != zpass) {
            beforeUncapturedStateChange();
            if (caching) {
                mod(ctx().stencilState).setOp(fail, zfail, zpass);
            }
            EXECUTOR.stencilOp(fail, zfail, zpass);
        }
    }

    public static void glClearStencil(int s) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearStencil(s);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || ctx().stencilState.getClearValue() != s) {
            if (caching) {
                mod(ctx().stencilState).setClearValue(s);
            }
            EXECUTOR.clearStencil(s);
        }
    }

    public static void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFuncSeparate(face, func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = ctx().stencilState.getFuncFront() != func || ctx().stencilState.getRefFront() != ref || ctx().stencilState.getValueMaskFront() != mask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = ctx().stencilState.getFuncBack() != func || ctx().stencilState.getRefBack() != ref || ctx().stencilState.getValueMaskBack() != mask;
            }
        }
        if (needsUpdate) {
            beforeUncapturedStateChange();
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setFuncFront(func);
                    mod(ctx().stencilState).setRefFront(ref);
                    mod(ctx().stencilState).setValueMaskFront(mask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setFuncBack(func);
                    mod(ctx().stencilState).setRefBack(ref);
                    mod(ctx().stencilState).setValueMaskBack(mask);
                }
            }
            EXECUTOR.stencilFuncSeparate(face, func, ref, mask);
        }
    }

    public static void glStencilMaskSeparate(int face, int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMaskSeparate(face, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = ctx().stencilState.getWriteMaskFront() != mask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = ctx().stencilState.getWriteMaskBack() != mask;
            }
        }
        if (needsUpdate) {
            beforeUncapturedStateChange();
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setWriteMaskFront(mask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setWriteMaskBack(mask);
                }
            }
            EXECUTOR.stencilMaskSeparate(face, mask);
        }
    }

    public static void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOpSeparate(face, sfail, dpfail, dppass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = ctx().stencilState.getFailOpFront() != sfail || ctx().stencilState.getZFailOpFront() != dpfail || ctx().stencilState.getZPassOpFront() != dppass;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = ctx().stencilState.getFailOpBack() != sfail || ctx().stencilState.getZFailOpBack() != dpfail || ctx().stencilState.getZPassOpBack() != dppass;
            }
        }
        if (needsUpdate) {
            beforeUncapturedStateChange();
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setFailOpFront(sfail);
                    mod(ctx().stencilState).setZFailOpFront(dpfail);
                    mod(ctx().stencilState).setZPassOpFront(dppass);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    mod(ctx().stencilState).setFailOpBack(sfail);
                    mod(ctx().stencilState).setZFailOpBack(dpfail);
                    mod(ctx().stencilState).setZPassOpBack(dppass);
                }
            }
            EXECUTOR.stencilOpSeparate(face, sfail, dpfail, dppass);
        }
    }

    public static boolean isEffectiveDepthMaskEnabled() {
            return ctx().depthState.isEffectiveMaskEnabled();
        }

    public static boolean isEffectiveAlphaTestEnabled() {
            return ctx().alphaTest.isEffectivelyEnabled();
        }

    public static AlphaState getEffectiveAlphaState(AlphaState out) {
            return ctx().alphaState.readEffective(out);
        }

    public static void enableAlphaTest() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordEnable(GL11.GL_ALPHA_TEST);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
            if (ah != null && ah.isAlphaTestLocked()) {
                ctx().exitCapture.capability(GL11.GL_ALPHA_TEST, -1);
                ctx().alphaTest.beforeModify();
                ah.deferAlphaTestToggle(true);
            } else {
                ctx().alphaTest.enable();
            }
            ctx().fragmentGeneration++;
        }

    public static void disableAlphaTest() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordDisable(GL11.GL_ALPHA_TEST);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
            if (ah != null && ah.isAlphaTestLocked()) {
                ctx().exitCapture.capability(GL11.GL_ALPHA_TEST, -1);
                ctx().alphaTest.beforeModify();
                ah.deferAlphaTestToggle(false);
            } else {
                ctx().alphaTest.disable();
            }
            ctx().fragmentGeneration++;
        }

    public static void enableDepthTest() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordEnable(GL11.GL_DEPTH_TEST);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            ctx().depthTest.enable();
        }

    public static void disableDepthTest() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordDisable(GL11.GL_DEPTH_TEST);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            ctx().depthTest.disable();
        }

    public static void glDepthRange(double near, double far) {
        rejectExitCapture();
        if (!isCachingEnabled() || ctx().viewportState.depthRangeNear != near || ctx().viewportState.depthRangeFar != far) {
            beforeUncapturedStateChange();
        }
            if (isCachingEnabled()) mod(ctx().viewportState).setDepthRange(near, far);
            EXECUTOR.depthRange(near, far);
        }

    public static DepthStateStack getDepthState() {
            return ctx().depthState;
        }

    public static BooleanStateStack getDepthTest() {
            return ctx().depthTest;
        }

    public static AlphaStateStack getAlphaState() {
            return ctx().alphaState;
        }

    public static BooleanStateStack getAlphaTest() {
            return ctx().alphaTest;
        }

    public static BooleanStateStack getStencilTest() {
            return ctx().stencilTest;
        }

    public static StencilStateStack getStencilState() {
            return ctx().stencilState;
        }
}
