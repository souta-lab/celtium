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
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.soutalab.celtium.glcompat.stacks.FogStateStack;
import org.joml.Vector3d;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.hooks.GLSMHooks;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.GLStateManager.HAS_MULTIPLE_SET;
import static org.soutalab.celtium.glcompat.GLStateManager.beforeUncapturedStateChange;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.getFogColor;
import static org.soutalab.celtium.glcompat.GLStateManager.getFogMode;
import static org.soutalab.celtium.glcompat.GLStateManager.isCachingEnabled;
import static org.soutalab.celtium.glcompat.GLStateManager.mod;
import static org.soutalab.celtium.glcompat.GLStateManager.BYPASS_CACHE;

public final class GLSMFog {

    private GLSMFog() {}

    public static void glFog(int pname, FloatBuffer param) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFog(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (HAS_MULTIPLE_SET.contains(pname)) {
            if (pname == GL11.GL_FOG_COLOR && isCachingEnabled()) {
                beforeUncapturedStateChange();
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                mod(ctx().fogState);
                ctx().fogState.getFogColor().set(red, green, blue);
                ctx().fogState.setFogAlpha(param.get(3));
                ctx().fogState.getFogColorBuffer().clear();
                ctx().fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
                ctx().fragmentGeneration++;
            }
        } else {
            GLSMFog.glFogf(pname, param.get(0));
        }
    }

    public static void glFog(int pname, IntBuffer param) {
        if (HAS_MULTIPLE_SET.contains(pname)) {
            final FloatBuffer floats = BufferUtils.createFloatBuffer(param.remaining());
            for (int i = param.position(); i < param.limit(); i++) {
                floats.put((2.0f * param.get(i) + 1.0f) / Integer.MAX_VALUE);
            }
            floats.flip();
            glFog(pname, floats);
        } else {
            glFogi(pname, param.get(0));
        }
    }

    public static void glFogf(int pname, float param) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            boolean changed = true;
            switch (pname) {
                case GL11.GL_FOG_DENSITY -> {
                    if (ctx().fogState.getDensity() != param) {
                        beforeUncapturedStateChange();
                        mod(ctx().fogState).setDensity(param);
                        ctx().fragmentGeneration++;
                    }
                }
                case GL11.GL_FOG_START -> {
                    if (ctx().fogState.getStart() != param) {
                        beforeUncapturedStateChange();
                        mod(ctx().fogState).setStart(param);
                        ctx().fragmentGeneration++;
                    }
                }
                case GL11.GL_FOG_END -> {
                    if (ctx().fogState.getEnd() != param) {
                        beforeUncapturedStateChange();
                        mod(ctx().fogState).setEnd(param);
                        ctx().fragmentGeneration++;
                    }
                }
                case GL11.GL_FOG_MODE -> {
                    if (ctx().fogState.getFogMode() != (int) param) {
                        beforeUncapturedStateChange();
                        mod(ctx().fogState).setFogMode((int) param);
                        ctx().fragmentGeneration++;
                    }
                }
                default -> changed = false;
            }
        }
    }

    public static void glFogi(int pname, int param) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogi(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled() && pname == GL11.GL_FOG_MODE) {
            beforeUncapturedStateChange();
            mod(ctx().fogState).setFogMode(param);
            ctx().fragmentGeneration++;
        }
    }

    public static void enableFog() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordEnable(GL11.GL_FOG);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            ctx().fogMode.enable();
        }

    public static void disableFog() {
            final RecordMode mode = DisplayListManager.getRecordMode();
            if (mode != RecordMode.NONE) {
                DisplayListManager.recordDisable(GL11.GL_FOG);
                if (mode == RecordMode.COMPILE) {
                    return;
                }
            }
            ctx().fogMode.disable();
        }

    public static Vector3d getFogColor() {
            return ctx().fogState.getFogColor();
        }

    public static void fogColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
            final boolean caching = isCachingEnabled();
            if (BYPASS_CACHE || !caching || red != ctx().fogState.getFogColor().x || green != ctx().fogState.getFogColor().y || blue != ctx().fogState.getFogColor().z || alpha != ctx().fogState.getFogAlpha()) {
                beforeUncapturedStateChange();
                if (caching) {
                    mod(ctx().fogState);
                    ctx().fogState.getFogColor().set(red, green, blue);
                    ctx().fogState.setFogAlpha(alpha);
                    ctx().fogState.getFogColorBuffer().clear();
                    ctx().fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
                    ctx().fragmentGeneration++;
                }
            }
        }

    public static FogStateStack getFogState() {
            return ctx().fogState;
        }

    public static float getFogAlpha() {
        return ctx().fogState.getFogAlpha();
    }

    public static BooleanStateStack getFogMode() {
            return ctx().fogMode;
        }
}
