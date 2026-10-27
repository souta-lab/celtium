package org.soutalab.celtium.vulkan;

/** Render-thread compiler for leased lit geometry. Shares the model program and frame-slot records. */
final class LitGeometryExecutor {
    /**
     * {@code -Dceltium.native.litGeometry=false} keeps the legacy push-block path: the same jar can then draw the same
     * world both ways, which is the A/B this change is judged on (an image diff against the path it replaces).
     */
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.native.litGeometry"));
    private final VulkanTerrainGpuBackend b;
    private VulkanModelInstances.Pipeline pipeline;
    private int textureIndex, lightmapIndex;

    LitGeometryExecutor(VulkanTerrainGpuBackend backend) { this.b = backend; }

    /** Called only after the queue's target/generation/texture checks, before legacy push compilation. */
    boolean prepare(ImmediateGroup group, PreparedImmediate prepared) {
        this.pipeline = null;
        if (!ENABLED) return false;
        int format = group.formatId;
        if (group.mesh == null || group.lightParams == null || group.state.length < 15
                || (format != VulkanImmediateFormats.ITEM
                    && format != VulkanImmediateFormats.POSITION_TEX_COLOR_NORMAL
                    && format != VulkanImmediateFormats.POSITION_TEX_NORMAL)
                || !b.context.textureArraySupported() || b.modelInstancesFailed
                || b.textureRegistry == null || b.lightmapTexture == null) return false;
        long pass;
        if (prepared.pipeline instanceof VulkanImmediateItemPipeline item) pass = item.celtium$renderPass();
        else if (prepared.pipeline instanceof VulkanImmediateTexturePipeline texture) pass = texture.celtium$renderPass();
        else return false;
        VulkanGlTexture texture = b.textureRegistry.get(group.texture);
        if (texture == null) return false;
        this.textureIndex = b.celtium$registerTexture(texture);
        this.lightmapIndex = b.celtium$registerTexture(b.lightmapTexture);
        if (this.textureIndex < 0 || this.lightmapIndex < 0 || !b.modelReplay.ensureInstances()
                || !b.modelInstances.hasRoom()) return false;
        int[] state = group.state;
        this.pipeline = b.modelInstances.pipelineFor(pass, group.stateKey, b.celtium$blendState(state),
                state[3] != 0, state[4] != 0, state[5] != 0, b.celtium$vkCompareOp(state[7]), format);
        return true;
    }

    /** The existing queue retains the mesh lease through submission completion. */
    void draw(ImmediateGroup group) {
        b.celtium$flushIndirectRun();
        b.modelInstances.drawGeometry(b.commandBuffer, this.pipeline, b.textureArray.set(), group,
                this.textureIndex, this.lightmapIndex);
        java.util.Arrays.fill(b.immediateBindCache, -1L);
        VulkanPushCache.SHARED.invalidate();
    }
}
