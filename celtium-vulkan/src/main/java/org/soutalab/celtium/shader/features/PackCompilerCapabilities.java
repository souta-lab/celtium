/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.features;

/**
 * Honest Vulkan 1.3 hardware capabilities reported to shader pack preprocessing and feature-flag
 * evaluation (design V040-FINAL-PLAN section 2.3, interface 2).
 *
 * <p>Celtium Vulkan 1.3 always supports compute shaders, SSBOs, image load/store and per-buffer
 * blending; tessellation defaults to false; geometry shaders reflect the physical device's actual
 * {@code VkPhysicalDeviceFeatures.geometryShader} bit.
 */
public record PackCompilerCapabilities(
        boolean supportsCompute,
        boolean supportsSSBO,
        boolean supportsImageLoadStore,
        boolean supportsBufferBlending,
        boolean supportsTesselation,
        boolean supportsGeometryShader,
        int maxGlslVersion) {

    /** Default Vulkan 1.3 capabilities (with geometry shaders enabled). */
    public static final PackCompilerCapabilities DEFAULT_VULKAN_13 = new PackCompilerCapabilities(
            true, true, true, true, false, true, 450);

    /** Returns a copy with the physical device's measured geometry-shader support. */
    public static PackCompilerCapabilities forDevice(boolean geometryShaderSupported) {
        return new PackCompilerCapabilities(
                true, true, true, true, false, geometryShaderSupported, 450);
    }

    /** Active capabilities consulted by static {@link FeatureFlags} checks. */
    private static volatile PackCompilerCapabilities active = DEFAULT_VULKAN_13;

    public static PackCompilerCapabilities active() {
        return active;
    }

    public static void setActive(PackCompilerCapabilities capabilities) {
        active = capabilities == null ? DEFAULT_VULKAN_13 : capabilities;
    }
}
