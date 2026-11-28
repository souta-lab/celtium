package org.soutalab.celtium.core.glcompat;

import java.lang.foreign.MemorySegment;

import org.soutalab.celtium.core.shader.SpirvModuleBundle;

/**
 * Interface 3's bottom surface (design V040-FINAL-PLAN section 2.3): celtium's own Vulkan 1.3 sink
 * for the direct-GL converter lane. This deliberately replaces the supplier's 276-declaration
 * per-GL-function backend SPI with a small cohesive surface bundled at Vulkan's granularity -
 * submit a draw under a complete state, dispatch a compute program, and four operation groups
 * (textures, buffers, framebuffers, shaders) that share the HAL's single texture table, slab pool,
 * transient-attachment slab and SPIR-V compiler.
 * <p>
 * The sink is the only way the converter lane reaches the GPU. The non-terrain lane never calls
 * it (interface 3 contract, clause 2): shared state travels through the
 * {@code LightStateRegister}, not through here.
 */
public interface DirectGlVulkanSink {

    /**
     * Submits one draw: binds the pipeline for {@code state} (state-hash cached), the vertex span
     * and - for an indexed command - the index span, and records the draw into the frame's current
     * rendering. When the state's render target is the one already being rendered into, recording
     * continues in place; otherwise the sink routes the draw through an isolated side target of
     * the transient-attachment slab, so a mod's custom framebuffer never hijacks the frame's
     * main structure.
     */
    void submitDraw(DirectGlDrawState state, VertexBindingSpan vertices, IndexSpan indices, DrawCommand draw);

    /** Dispatches a converted compute program with the given workgroup counts. */
    void dispatchCompute(ComputeProgramHandle program, int groupsX, int groupsY, int groupsZ);

    TextureOps textureOps();

    BufferOps bufferOps();

    FramebufferOps framebufferOps();

    ShaderOps shaderOps();

    /** Texture operations against the HAL's single bindless texture table. Ids are table ids. */
    interface TextureOps {

        /** Creates a 2D texture and returns its table id. */
        long createTexture2D(int width, int height, int format, int mipLevels);

        /** Writes pixels into one mip level of a texture. */
        void writeTexture(long textureId, int level, int x, int y, int width, int height, MemorySegment pixels);

        void destroyTexture(long textureId);
    }

    /** Buffer operations against the HAL's slab pool. Ids are buffer handles. */
    interface BufferOps {

        /** Creates a buffer of the given size and usage flags (VkBufferUsageFlags, carried opaquely). */
        long createBuffer(long sizeBytes, int usageFlags);

        void writeBuffer(long bufferId, long offsetBytes, MemorySegment data);

        void destroyBuffer(long bufferId);
    }

    // Framebuffer aspect bits. These belong to the Java contract, independent of the GL mask.
    int COLOR_ASPECT = 1;
    int DEPTH_ASPECT = 2;
    int STENCIL_ASPECT = 4;

    /** Clear values and an optional rectangle in framebuffer coordinates. */
    record ClearSpec(int clearMask, float r, float g, float b, float a, float depth, int stencil,
                     boolean scissorEnabled, int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
    }

    /** Ordered rectangle endpoints in framebuffer coordinates; reversing an axis mirrors the image. */
    record BlitRegion(int srcX0, int srcY0, int srcX1, int srcY1,
                      int dstX0, int dstY0, int dstX1, int dstY1, int aspectMask, boolean linearFilter) {
    }

    /** Framebuffer operations: a mod's custom FBO becomes an isolated transient-slab target. */
    interface FramebufferOps {

        /** Creates a framebuffer over existing texture ids and returns its target id. */
        long createFramebuffer(long[] colorTextureIds, long depthTextureId, int width, int height);

        void destroyFramebuffer(long framebufferId);
    }

    /** Shader operations: conversion output (SPIR-V bundles) becomes sink-owned programs. */
    interface ShaderOps {

        /** Compiles and links a program from a stage bundle; returns its graphics program handle. */
        long createProgram(SpirvModuleBundle bundle);

        /** Compiles a compute program; returns its handle for {@link DirectGlVulkanSink#dispatchCompute}. */
        ComputeProgramHandle createComputeProgram(SpirvModuleBundle bundle);

        void destroyProgram(long programHandle);
    }
}
