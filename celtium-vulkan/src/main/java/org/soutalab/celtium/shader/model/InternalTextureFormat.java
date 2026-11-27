/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 with direct VkFormat mapping.
 */
package org.soutalab.celtium.shader.model;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;
import org.lwjgl.vulkan.VK10;

import java.util.Optional;

public enum InternalTextureFormat {
    // Default
    RGBA(GL11.GL_RGBA, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R8G8B8A8_UNORM),
    // 8-bit normalized
    R8(GL30.GL_R8, GlVersion.GL_30, PixelFormat.RED, VK10.VK_FORMAT_R8_UNORM),
    RG8(GL30.GL_RG8, GlVersion.GL_30, PixelFormat.RG, VK10.VK_FORMAT_R8G8_UNORM),
    RGB8(GL11.GL_RGB8, GlVersion.GL_11, PixelFormat.RGB, VK10.VK_FORMAT_R8G8B8A8_UNORM),
    RGBA8(GL11.GL_RGBA8, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R8G8B8A8_UNORM),
    // 8-bit signed normalized
    R8_SNORM(GL31.GL_R8_SNORM, GlVersion.GL_31, PixelFormat.RED, VK10.VK_FORMAT_R8_SNORM),
    RG8_SNORM(GL31.GL_RG8_SNORM, GlVersion.GL_31, PixelFormat.RG, VK10.VK_FORMAT_R8G8_SNORM),
    RGB8_SNORM(GL31.GL_RGB8_SNORM, GlVersion.GL_31, PixelFormat.RGB, VK10.VK_FORMAT_R8G8B8A8_SNORM),
    RGBA8_SNORM(GL31.GL_RGBA8_SNORM, GlVersion.GL_31, PixelFormat.RGBA, VK10.VK_FORMAT_R8G8B8A8_SNORM),
    // 16-bit normalized
    R16(GL30.GL_R16, GlVersion.GL_30, PixelFormat.RED, VK10.VK_FORMAT_R16_UNORM),
    RG16(GL30.GL_RG16, GlVersion.GL_30, PixelFormat.RG, VK10.VK_FORMAT_R16G16_UNORM),
    RGB16(GL11.GL_RGB16, GlVersion.GL_11, PixelFormat.RGB, VK10.VK_FORMAT_R16G16B16A16_UNORM),
    RGBA16(GL11.GL_RGBA16, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R16G16B16A16_UNORM),
    // 16-bit signed normalized
    R16_SNORM(GL31.GL_R16_SNORM, GlVersion.GL_31, PixelFormat.RED, VK10.VK_FORMAT_R16_SNORM),
    RG16_SNORM(GL31.GL_RG16_SNORM, GlVersion.GL_31, PixelFormat.RG, VK10.VK_FORMAT_R16G16_SNORM),
    RGB16_SNORM(GL31.GL_RGB16_SNORM, GlVersion.GL_31, PixelFormat.RGB, VK10.VK_FORMAT_R16G16B16A16_SNORM),
    RGBA16_SNORM(GL31.GL_RGBA16_SNORM, GlVersion.GL_31, PixelFormat.RGBA, VK10.VK_FORMAT_R16G16B16A16_SNORM),
    // 16-bit float
    R16F(GL30.GL_R16F, GlVersion.GL_30, PixelFormat.RED, VK10.VK_FORMAT_R16_SFLOAT),
    RG16F(GL30.GL_RG16F, GlVersion.GL_30, PixelFormat.RG, VK10.VK_FORMAT_R16G16_SFLOAT),
    RGB16F(GL30.GL_RGB16F, GlVersion.GL_30, PixelFormat.RGB, VK10.VK_FORMAT_R16G16B16A16_SFLOAT),
    RGBA16F(GL30.GL_RGBA16F, GlVersion.GL_30, PixelFormat.RGBA, VK10.VK_FORMAT_R16G16B16A16_SFLOAT),
    // 32-bit float
    R32F(GL30.GL_R32F, GlVersion.GL_30, PixelFormat.RED, VK10.VK_FORMAT_R32_SFLOAT),
    RG32F(GL30.GL_RG32F, GlVersion.GL_30, PixelFormat.RG, VK10.VK_FORMAT_R32G32_SFLOAT),
    RGB32F(GL30.GL_RGB32F, GlVersion.GL_30, PixelFormat.RGB, VK10.VK_FORMAT_R32G32B32A32_SFLOAT),
    RGBA32F(GL30.GL_RGBA32F, GlVersion.GL_30, PixelFormat.RGBA, VK10.VK_FORMAT_R32G32B32A32_SFLOAT),
    // 8-bit integer
    R8I(GL30.GL_R8I, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R8_SINT),
    RG8I(GL30.GL_RG8I, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R8G8_SINT),
    RGB8I(GL30.GL_RGB8I, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R8G8B8A8_SINT),
    RGBA8I(GL30.GL_RGBA8I, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R8G8B8A8_SINT),
    // 8-bit unsigned integer
    R8UI(GL30.GL_R8UI, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R8_UINT),
    RG8UI(GL30.GL_RG8UI, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R8G8_UINT),
    RGB8UI(GL30.GL_RGB8UI, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R8G8B8A8_UINT),
    RGBA8UI(GL30.GL_RGBA8UI, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R8G8B8A8_UINT),
    // 16-bit integer
    R16I(GL30.GL_R16I, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R16_SINT),
    RG16I(GL30.GL_RG16I, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R16G16_SINT),
    RGB16I(GL30.GL_RGB16I, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R16G16B16A16_SINT),
    RGBA16I(GL30.GL_RGBA16I, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R16G16B16A16_SINT),
    // 16-bit unsigned integer
    R16UI(GL30.GL_R16UI, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R16_UINT),
    RG16UI(GL30.GL_RG16UI, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R16G16_UINT),
    RGB16UI(GL30.GL_RGB16UI, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R16G16B16A16_UINT),
    RGBA16UI(GL30.GL_RGBA16UI, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R16G16B16A16_UINT),
    // 32-bit integer
    R32I(GL30.GL_R32I, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R32_SINT),
    RG32I(GL30.GL_RG32I, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R32G32_SINT),
    RGB32I(GL30.GL_RGB32I, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R32G32B32A32_SINT),
    RGBA32I(GL30.GL_RGBA32I, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R32G32B32A32_SINT),
    // 32-bit unsigned integer
    R32UI(GL30.GL_R32UI, GlVersion.GL_30, PixelFormat.RED_INTEGER, VK10.VK_FORMAT_R32_UINT),
    RG32UI(GL30.GL_RG32UI, GlVersion.GL_30, PixelFormat.RG_INTEGER, VK10.VK_FORMAT_R32G32_UINT),
    RGB32UI(GL30.GL_RGB32UI, GlVersion.GL_30, PixelFormat.RGB_INTEGER, VK10.VK_FORMAT_R32G32B32A32_UINT),
    RGBA32UI(GL30.GL_RGBA32UI, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_R32G32B32A32_UINT),
    // 2-bit / 4-bit normalized
    RGBA2(GL11.GL_RGBA2, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R8G8B8A8_UNORM),
    RGBA4(GL11.GL_RGBA4, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R4G4B4A4_UNORM_PACK16),
    // Mixed
    R3_G3_B2(GL11.GL_R3_G3_B2, GlVersion.GL_11, PixelFormat.RGB, VK10.VK_FORMAT_R8G8B8A8_UNORM),
    RGB5_A1(GL11.GL_RGB5_A1, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_R5G5B5A1_UNORM_PACK16),
    RGB565(GL41.GL_RGB565, GlVersion.GL_41, PixelFormat.RGB, VK10.VK_FORMAT_R5G6B5_UNORM_PACK16),
    RGB10_A2(GL11.GL_RGB10_A2, GlVersion.GL_11, PixelFormat.RGBA, VK10.VK_FORMAT_A2B10G10R10_UNORM_PACK32),
    RGB10_A2UI(GL33.GL_RGB10_A2UI, GlVersion.GL_33, PixelFormat.RGBA_INTEGER, VK10.VK_FORMAT_A2B10G10R10_UINT_PACK32),
    R11F_G11F_B10F(GL30.GL_R11F_G11F_B10F, GlVersion.GL_30, PixelFormat.RGB, VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32),
    RGB9_E5(GL30.GL_RGB9_E5, GlVersion.GL_30, PixelFormat.RGB, VK10.VK_FORMAT_E5B9G9R9_UFLOAT_PACK32);

    private final int glFormat;
    private final GlVersion minimumGlVersion;
    private final PixelFormat expectedPixelFormat;
    private final int vkFormat;

    InternalTextureFormat(int glFormat, GlVersion minimumGlVersion, PixelFormat expectedPixelFormat, int vkFormat) {
        this.glFormat = glFormat;
        this.minimumGlVersion = minimumGlVersion;
        this.expectedPixelFormat = expectedPixelFormat;
        this.vkFormat = vkFormat;
    }

    public static Optional<InternalTextureFormat> fromString(String name) {
        try {
            return Optional.of(InternalTextureFormat.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public int getGlFormat() {
        return glFormat;
    }

    public int getVkFormat() {
        return vkFormat;
    }

    public PixelFormat getPixelFormat() {
        return expectedPixelFormat;
    }

    public GlVersion getMinimumGlVersion() {
        return minimumGlVersion;
    }

    public InternalTextureFormat getColorRenderableFallback() {
        return switch (this) {
            case RGB8 -> RGBA8;
            case RGB16 -> RGBA16;
            case RGB16F -> RGBA16F;
            case RGB32F -> RGBA32F;
            case RGB8_SNORM -> RGBA8_SNORM;
            case RGB16_SNORM -> RGBA16_SNORM;
            default -> this;
        };
    }
}
