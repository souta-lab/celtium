package org.soutalab.celtium.vulkan;

import java.lang.foreign.MemorySegment;

/**
 * The celtium-internal wiring between the direct-GL converter's executor and its Vulkan sink,
 * for the state the core {@code DirectGlVulkanSink} surface deliberately does not carry per
 * call: the fixed-function parameters finer than the {@code RenderStateKey} (blend factors,
 * depth/stencil functions, viewport and scissor rectangles), a program's vertex layout, and a
 * program's current uniform-block contents. The executor pushes these when they change; the
 * sink folds them into its pipeline cache key and its per-draw dynamic state.
 * <p>
 * This is an implementation seam inside one module, not a second public contract: the core
 * interface stays the small surface the plan fixed, and this channel is how the converter's
 * own two halves talk. Values are Vulkan enums already - the executor translates GL constants
 * at its boundary, so the sink never sees a GL token.
 */
public interface DirectGlSinkExtensions {

    /** Pushes the converter's current extended fixed state (called when any field changes). */
    void setExtendedFixedState(ExtendedFixedState state);

    /** Declares a sink program's vertex attributes, in the converter's canonical packing. */
    void registerProgramAttribs(long programHandle, AttribDesc[] attribs);

    /** Replaces a program's uniform block contents (the executor's packed uniform values). */
    void updateUniforms(long programHandle, MemorySegment values);

    /** One vertex attribute of a converted program: its location and its Vulkan format. */
    record AttribDesc(int location, int vkFormat) {

        /** The attribute's byte size, from its Vulkan format's component shape. */
        public int byteSize() {
            return switch (vkFormat) {
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT -> 4;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT -> 8;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT -> 12;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT -> 16;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SNORM,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UINT,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SINT -> 4;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16_SFLOAT -> 4;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> 8;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32_UINT, org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SINT -> 4;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_UINT,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SINT -> 8;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_UINT,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SINT -> 12;
                case org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_UINT,
                     org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SINT -> 16;
                default -> 16;
            };
        }
    }

    /**
     * The fixed state the {@code RenderStateKey} does not pack: blend factors and equations,
     * depth and stencil functions, polygon offset, logic op, front face, and the dynamic
     * viewport/scissor/line-width values. {@code hash} folds every pipeline-relevant field; the
     * dynamic values ride along for the record step but are excluded from the hash by the
     * caller's convention (they are set per draw, not baked into pipelines).
     */
    record ExtendedFixedState(
            int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
            int blendEquationRgb, int blendEquationAlpha,
            float blendConstantR, float blendConstantG, float blendConstantB, float blendConstantA,
            int depthFunc, int logicOp,
            int stencilFunc, int stencilRef, int stencilFuncMask, int stencilWriteMask,
            int stencilFail, int stencilDepthFail, int stencilPass,
            boolean frontFaceCcw, float polygonOffsetFactor, float polygonOffsetUnits,
            int viewportX, int viewportY, int viewportWidth, int viewportHeight,
            int scissorX, int scissorY, int scissorWidth, int scissorHeight,
            float lineWidth, long hash) {

        /** GL's defaults, translated: ONE/ZERO blend, LESS depth, KEEP/KEEP/KEEP stencil. */
        public static final ExtendedFixedState DEFAULT = of(
                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE, org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO,
                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE, org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO,
                org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD,
                0.0f, 0.0f, 0.0f, 0.0f,
                org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL,
                org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY,
                org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS, 0, 0xFFFFFFFF, 0xFFFFFFFF,
                org.lwjgl.vulkan.VK10.VK_STENCIL_OP_KEEP, org.lwjgl.vulkan.VK10.VK_STENCIL_OP_KEEP,
                org.lwjgl.vulkan.VK10.VK_STENCIL_OP_KEEP,
                true, 0.0f, 0.0f,
                0, 0, 0, 0, 0, 0, 0, 0, 1.0f);

        public static ExtendedFixedState of(
                int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                int blendEquationRgb, int blendEquationAlpha,
                float blendConstantR, float blendConstantG, float blendConstantB, float blendConstantA,
                int depthFunc, int logicOp,
                int stencilFunc, int stencilRef, int stencilFuncMask, int stencilWriteMask,
                int stencilFail, int stencilDepthFail, int stencilPass,
                boolean frontFaceCcw, float polygonOffsetFactor, float polygonOffsetUnits,
                int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                int scissorX, int scissorY, int scissorWidth, int scissorHeight,
                float lineWidth) {
            long hash = 0x9E3779B97F4A7C15L;
            int[] fields = {srcRgb, dstRgb, srcAlpha, dstAlpha, blendEquationRgb, blendEquationAlpha,
                    depthFunc, logicOp, stencilFunc, stencilRef, stencilFuncMask, stencilWriteMask,
                    stencilFail, stencilDepthFail, stencilPass, frontFaceCcw ? 1 : 0,
                    Float.floatToIntBits(polygonOffsetFactor), Float.floatToIntBits(polygonOffsetUnits),
                    Float.floatToIntBits(blendConstantR), Float.floatToIntBits(blendConstantG),
                    Float.floatToIntBits(blendConstantB), Float.floatToIntBits(blendConstantA)};
            for (int field : fields) {
                hash = (hash ^ field) * 0xBF58476D1CE4E5B9L;
                hash ^= hash >>> 27;
            }
            return new ExtendedFixedState(srcRgb, dstRgb, srcAlpha, dstAlpha, blendEquationRgb,
                    blendEquationAlpha, blendConstantR, blendConstantG, blendConstantB, blendConstantA,
                    depthFunc, logicOp, stencilFunc, stencilRef, stencilFuncMask, stencilWriteMask,
                    stencilFail, stencilDepthFail, stencilPass, frontFaceCcw, polygonOffsetFactor,
                    polygonOffsetUnits, viewportX, viewportY, viewportWidth, viewportHeight,
                    scissorX, scissorY, scissorWidth, scissorHeight, lineWidth, hash);
        }

        /** A copy with only the dynamic viewport/scissor/line-width values replaced. */
        public ExtendedFixedState withDynamic(int viewportX, int viewportY, int viewportWidth,
                                              int viewportHeight, int scissorX, int scissorY,
                                              int scissorWidth, int scissorHeight, float lineWidth) {
            return new ExtendedFixedState(srcRgb, dstRgb, srcAlpha, dstAlpha, blendEquationRgb,
                    blendEquationAlpha, blendConstantR, blendConstantG, blendConstantB, blendConstantA,
                    depthFunc, logicOp, stencilFunc, stencilRef, stencilFuncMask, stencilWriteMask,
                    stencilFail, stencilDepthFail, stencilPass, frontFaceCcw, polygonOffsetFactor,
                    polygonOffsetUnits, viewportX, viewportY, viewportWidth, viewportHeight,
                    scissorX, scissorY, scissorWidth, scissorHeight, lineWidth, hash);
        }
    }
}
