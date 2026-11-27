package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;
import org.soutalab.celtium.shader.features.FeatureFlags;
import org.soutalab.celtium.shader.features.PackCompilerCapabilities;
import org.soutalab.celtium.shader.model.InternalTextureFormat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackCompilerCapabilitiesAndFeaturesTest {

    @Test
    void reportsHonestVulkan13Capabilities() {
        PackCompilerCapabilities caps = PackCompilerCapabilities.DEFAULT_VULKAN_13;
        assertTrue(caps.supportsCompute());
        assertTrue(caps.supportsSSBO());
        assertTrue(caps.supportsImageLoadStore());
        assertTrue(caps.supportsBufferBlending());
        assertFalse(caps.supportsTesselation());
        assertEquals(450, caps.maxGlslVersion());

        assertTrue(FeatureFlags.COMPUTE_SHADERS.isUsable(caps));
        assertTrue(FeatureFlags.SSBO.isUsable(caps));
        assertTrue(FeatureFlags.CUSTOM_IMAGES.isUsable(caps));
        assertTrue(FeatureFlags.PER_BUFFER_BLENDING.isUsable(caps));
        assertFalse(FeatureFlags.TESSELLATION_SHADERS.isUsable(caps));
        assertFalse(FeatureFlags.UNKNOWN.isUsable(caps));
    }

    @Test
    void geometryShaderReflectsMeasuredDeviceFeature() {
        PackCompilerCapabilities withGeom = PackCompilerCapabilities.forDevice(true);
        PackCompilerCapabilities withoutGeom = PackCompilerCapabilities.forDevice(false);
        assertTrue(withGeom.supportsGeometryShader());
        assertFalse(withoutGeom.supportsGeometryShader());
    }

    @Test
    void normalisesLegacyTessellationSpellingAndInvalidStatus() {
        assertEquals(FeatureFlags.TESSELLATION_SHADERS, FeatureFlags.getValue("TESSELATION_SHADERS"));
        assertEquals(FeatureFlags.UNKNOWN, FeatureFlags.getValue("NON_EXISTENT_FLAG"));
        assertNotNull(FeatureFlags.getInvalidStatus(List.of(FeatureFlags.TESSELLATION_SHADERS),
                PackCompilerCapabilities.DEFAULT_VULKAN_13));
        assertNull(FeatureFlags.getInvalidStatus(List.of(FeatureFlags.COMPUTE_SHADERS, FeatureFlags.SSBO),
                PackCompilerCapabilities.DEFAULT_VULKAN_13));
    }

    @Test
    void mapsInternalTextureFormatsToColorRenderableVkFormats() {
        assertEquals(InternalTextureFormat.RGBA8, InternalTextureFormat.RGB8.getColorRenderableFallback());
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_UNORM, InternalTextureFormat.RGB8.getVkFormat());
        assertEquals(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, InternalTextureFormat.RGBA16F.getVkFormat());
        assertEquals(VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32, InternalTextureFormat.R11F_G11F_B10F.getVkFormat());
    }
}
