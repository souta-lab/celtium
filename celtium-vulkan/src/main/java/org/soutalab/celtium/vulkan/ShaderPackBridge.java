package org.soutalab.celtium.vulkan;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Reflection bridge to the shader mod's own API (S2-b relocation: celtium's own copy of the
 * bridge the present path consults - whether a shader pack is in use decides whether the
 * direct-present borrow may engage). No compile-time dependency on the shader mod.
 */
public final class ShaderPackBridge {

    private static final MethodHandle SHADERS_ENABLED;
    private static final MethodHandle SHADERS_OPEN_SCREEN;

    static {
        MethodHandle shadersEnabled = null, shaderOpenScreen = null;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceGetter = irisApiClass.getDeclaredMethod("getInstance");
            Object irisApiInstance = instanceGetter.invoke(null);
            shadersEnabled = MethodHandles.lookup()
                    .unreflect(irisApiClass.getDeclaredMethod("isShaderPackInUse"))
                    .bindTo(irisApiInstance);
            shaderOpenScreen = MethodHandles.lookup()
                    .unreflect(irisApiClass.getDeclaredMethod("openMainIrisScreenObj", Object.class))
                    .bindTo(irisApiInstance);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (Throwable ignored) {
        }
        SHADERS_ENABLED = shadersEnabled;
        SHADERS_OPEN_SCREEN = shaderOpenScreen;
    }

    private ShaderPackBridge() {
    }

    public static boolean areShadersEnabled() {
        if (SHADERS_ENABLED != null) {
            try {
                return (boolean) SHADERS_ENABLED.invokeExact();
            } catch (Throwable e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean isShaderModPresent() {
        return SHADERS_ENABLED != null;
    }

    public static Object openShaderScreen(Object parentScreen) {
        if (SHADERS_OPEN_SCREEN != null) {
            try {
                return SHADERS_OPEN_SCREEN.invoke(parentScreen);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
