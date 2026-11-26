package org.soutalab.celtium.vulkan;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The permanent host of the immediate families' record-reading pipelines (Stage 5 stage 6
 * slice S6-b).
 *
 * <p>Every immediate family has exactly one pipeline form - the record-reading one - and this
 * class is where those pipelines live: one branch-keyed cache per family and the single
 * creation dispatch the prepare branches and the pipeline prewarm's creation actions share,
 * moved out of {@link VulkanTerrainGpuBackend} when the per-group forms were deleted. The
 * branch key is the backend's own fold ({@code celtium$immediateBranchKey}: the prepared key
 * folded with the pass mix, the line bit preserved), so an entry built under one pass never
 * answers under another (the R-06 format-axis contract), and the caches are concurrent
 * because the prewarm compiler thread runs the same creation path the render thread does -
 * the particle family's cache already had to be, for exactly that reason.
 *
 * <p>The particle family's dedicated accessor keeps the shape it had in its previous host:
 * the family has no per-group original to twin from, so its state arrives as creation
 * arguments and its cache is keyed by the branch key derived inside the accessor. The other
 * families' accessors are the same shape generalized - state in, twin out - which is what
 * lets the prepare resolve a group's twin directly instead of resolving an original and
 * asking for its twin at capture time.
 *
 * <p>Creation ensures the draw-record store first: every twin's layout names the store's set
 * layout, so a store that cannot be created declines the build, and the prepare then refuses
 * the batch by name, as it does for a failed descriptor.
 */
final class ImmediateRecordsPipelines {
    private final VulkanTerrainGpuBackend b;

    // The branch key folds the prepared key with the pass, not with the format, so two
    // textured variants sharing a state and a pass would fold to the same key: the textured
    // family keeps one cache per variant, as the backend's dispatch did. The colour family's
    // two forms share one cache because the line bit is part of the key itself.
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> texturePipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> positionTexPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> positionOnlyPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> positionTexColorNormalPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> positionTexNormalPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> positionNormalPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> texGenPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> combine4Pipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateColourRecordsPipeline> colourPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateItemRecordsPipeline> itemPipelines =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VulkanImmediateBlockRecordsPipeline> blockPipelines =
            new ConcurrentHashMap<>();
    /**
     * The particle family's record-reading pipelines, by the backend's branch key. Concurrent,
     * as the other families' caches are: the creation path is the backend's generic one, which
     * the prewarm compiler thread also runs.
     */
    private final ConcurrentHashMap<Long, VulkanImmediateParticleRecordsPipeline> particlePipelines =
            new ConcurrentHashMap<>();

    ImmediateRecordsPipelines(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /** {@return the textured variant's cache for a format} - see the field note on why they are separate. */
    private ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> textureCache(int format) {
        switch (format) {
            case VulkanImmediateFormats.POSITION_COLOR_TEXGEN:
                return this.texGenPipelines;
            case VulkanImmediateFormats.COMBINE4:
                return this.combine4Pipelines;
            case VulkanImmediateFormats.POSITION_NORMAL:
                return this.positionNormalPipelines;
            case VulkanImmediateFormats.POSITION_TEX_NORMAL:
                return this.positionTexNormalPipelines;
            case VulkanImmediateFormats.POSITION_TEX_COLOR_NORMAL:
                return this.positionTexColorNormalPipelines;
            case VulkanImmediateFormats.POSITION:
                return this.positionOnlyPipelines;
            case VulkanImmediateFormats.POSITION_TEX:
                return this.positionTexPipelines;
            default:
                return this.texturePipelines;
        }
    }

    /** {@return the cached pipeline for a branch, or null when the branch has not built it}. */
    Object celtium$cachedPipeline(int format, long branchKey) {
        switch (format) {
            case VulkanImmediateFormats.PARTICLE:
                return this.particlePipelines.get(Long.valueOf(branchKey));
            case VulkanImmediateFormats.ITEM:
                return this.itemPipelines.get(Long.valueOf(branchKey));
            case VulkanImmediateFormats.BLOCK:
                return this.blockPipelines.get(Long.valueOf(branchKey));
            case VulkanImmediateFormats.POSITION_COLOR:
                return this.colourPipelines.get(Long.valueOf(branchKey));
            default:
                return textureCache(format).get(Long.valueOf(branchKey));
        }
    }

    /** Stores a pipeline in its branch's cache. */
    void celtium$cachePipeline(int format, long branchKey, Object pipeline) {
        switch (format) {
            case VulkanImmediateFormats.PARTICLE:
                this.particlePipelines.put(Long.valueOf(branchKey),
                        (VulkanImmediateParticleRecordsPipeline) pipeline);
                break;
            case VulkanImmediateFormats.ITEM:
                this.itemPipelines.put(Long.valueOf(branchKey),
                        (VulkanImmediateItemRecordsPipeline) pipeline);
                break;
            case VulkanImmediateFormats.BLOCK:
                this.blockPipelines.put(Long.valueOf(branchKey),
                        (VulkanImmediateBlockRecordsPipeline) pipeline);
                break;
            case VulkanImmediateFormats.POSITION_COLOR:
                this.colourPipelines.put(Long.valueOf(branchKey),
                        (VulkanImmediateColourRecordsPipeline) pipeline);
                break;
            default:
                textureCache(format).put(Long.valueOf(branchKey),
                        (VulkanImmediateTextureRecordsPipeline) pipeline);
                break;
        }
    }

    /**
     * Builds the pipeline for one prepared state through the family's record-reading factory -
     * the single creation path the prepare branches and the pipeline prewarm's creation
     * actions share, so a warmed pipeline is built by exactly the code a frame-path miss would
     * run. No cache is touched here.
     *
     * @return the built pipeline, or null when the factory declined
     */
    Object celtium$createPipeline(int format, long key, int[] state, long pass, int targetColors) {
        if (format == VulkanImmediateFormats.PARTICLE) {
            // BH-07: the colour-blend state is the batch's own, like every other family's. This one used to assert
            // (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) for the RGB pair and the family default for alpha whatever the capture
            // said; ParticleManager itself calls `blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`, and GL's glBlendFunc sets
            // the RGB *and* the alpha pair to that same pair - so the alpha pair the pipeline baked was the wrong one
            // for every particle batch in the scene. The depth and cull arguments are left as this family asserted
            // them: they are a different finding's subject and changing them here would move an accepted picture.
            // The family's only form is the record-reading one, resolved through the dedicated accessor below;
            // the twin is built against the record store's layout, so the store is ensured first, and a store
            // that cannot be created declines the build (the prepare then refuses the batch by name, as it does
            // for a failed descriptor).
            if (!this.b.celtium$ensureDrawRecords()) {
                return null;
            }
            return celtium$recordsParticlePipeline(format, key, pass,
                    VulkanTerrainGpuBackend.celtium$blendState(state),
                    VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]), targetColors);
        }
        // Every family's only form reads the draw records now, so the store is ensured for all
        // of them, where the per-group forms needed it only for their twins.
        if (!this.b.celtium$ensureDrawRecords()) {
            return null;
        }
        long recordsLayout = this.b.drawRecords.descriptorSetLayout();
        if (format == VulkanImmediateFormats.ITEM) {
            // The lit bit only: the per-group lit/unlit census stays at the prepare branch, which
            // runs per group, while this creation path runs per distinct state.
            boolean itemLit = state.length > 8 && state[8] != 0;
            return VulkanImmediateItemRecordsPipeline.create(this.b.context, pass,
                    VulkanTerrainGpuBackend.celtium$blendState(state),
                    state[3] != 0, state[4] != 0, state[5],
                    VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]), itemLit,
                    recordsLayout, targetColors);
        }
        if (format == VulkanImmediateFormats.BLOCK) {
            return VulkanImmediateBlockRecordsPipeline.create(this.b.context, pass,
                    VulkanTerrainGpuBackend.celtium$blendState(state),
                    state[3] != 0, state[4] != 0, state[5],
                    VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]),
                    recordsLayout, targetColors);
        }
        if (format == VulkanImmediateFormats.POSITION_COLOR_TEXGEN) {
            return VulkanImmediateTextureRecordsPipeline.createForTexGen(this.b.context, pass,
                    VulkanTerrainGpuBackend.celtium$blendState(state),
                    state[3] != 0, state[4] != 0, state[5],
                    VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]),
                    recordsLayout, targetColors);
        }
        if (format >= 2) {
            int compareOp = VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]);
            // Experiment switch for the POSITION_NORMAL box: every one of its batches reaches this draw and the
            // box is absent from the full-mask picture (62 accepted, 62 drawn, no pixels), while the same group
            // drawn with format 10 alone lands exactly where GROUP_BOUNDS puts it. Turning its depth test off
            // separates "the fragments are rejected by the depth the window draws against" from "the draw does not
            // reach the image at all". Default on, so the shipped behaviour is unchanged.
            boolean positionNormalDepthTest = state[3] != 0
                    && VulkanTerrainGpuBackend.POSITION_NORMAL_DEPTH_TEST;
            VulkanColorBlendState colorBlend = VulkanTerrainGpuBackend.celtium$blendState(state);
            return format == VulkanImmediateFormats.COMBINE4
                    ? VulkanImmediateTextureRecordsPipeline.createForCombine4(
                    this.b.context, pass, colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp,
                    recordsLayout, targetColors)
                    : format == VulkanImmediateFormats.POSITION_TEX_NORMAL_FLOAT
                    ? VulkanImmediateTextureRecordsPipeline.createForPositionTexNormalFloat(this.b.context,
                    pass, colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout,
                    targetColors)
                    : format == VulkanImmediateFormats.POSITION_NORMAL
                    ? VulkanImmediateTextureRecordsPipeline.createForPositionNormal(this.b.context, pass,
                    colorBlend, positionNormalDepthTest, state[4] != 0, state[5], compareOp, recordsLayout,
                    targetColors)
                    : format == VulkanImmediateFormats.POSITION_TEX_NORMAL
                    ? VulkanImmediateTextureRecordsPipeline.createForPositionTexNormal(this.b.context, pass,
                    colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout, targetColors)
                    : format == VulkanImmediateFormats.POSITION_TEX_COLOR_NORMAL
                    ? VulkanImmediateTextureRecordsPipeline.createForPositionTexColorNormal(this.b.context, pass,
                    colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout, targetColors)
                    : format == VulkanImmediateFormats.POSITION
                    ? VulkanImmediateTextureRecordsPipeline.createForPosition(this.b.context, pass,
                    colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout, targetColors)
                    : format == VulkanImmediateFormats.POSITION_TEX
                    ? VulkanImmediateTextureRecordsPipeline.createForPositionTex(this.b.context, pass,
                    colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout, targetColors)
                    : VulkanImmediateTextureRecordsPipeline.create(this.b.context, pass,
                    colorBlend, state[3] != 0, state[4] != 0, state[5], compareOp, recordsLayout, targetColors);
        }
        boolean lineBatch = (key & VulkanTerrainGpuBackend.LINE_KEY_BIT) != 0;
        VulkanColorBlendState colorBlend = VulkanTerrainGpuBackend.celtium$blendState(state);
        return lineBatch
                ? VulkanImmediateColourRecordsPipeline.createForLines(this.b.context, pass, colorBlend,
                        state[3] != 0, state[4] != 0, state[5],
                        VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]),
                        recordsLayout, targetColors)
                : VulkanImmediateColourRecordsPipeline.create(this.b.context, pass, colorBlend,
                        state[3] != 0, state[4] != 0, state[5],
                        VulkanTerrainGpuBackend.celtium$vkCompareOp(state[7]),
                        recordsLayout, targetColors);
    }

    /**
     * {@return the record-reading particle pipeline for one prepared state, created on first use for that
     * exact state}, or null when the record store does not exist yet (the caller ensures it first; a store
     * that cannot be created leaves the family with no pipeline, and the prepare refuses by name).
     * <p>
     * The particle family's twin access: unlike the other families the particle family has no per-group
     * original to twin from - since Stage 5 S5-3 its record-reading form is its only form - so the state
     * arrives as the creation arguments the backend's particle branch already computes, and the cache key
     * is the backend's branch key (the prepared key folded with the pass mix), derived here through the
     * same pass-keyed fold every branch cache read uses (the R-06 format-axis contract: an entry built
     * under one pass never answers under another). The depth test, depth write and cull arguments are the
     * family's asserted constants (BH-07 leaves them as the family asserted them: depth test on, depth
     * writes off, no culling); the blend state is the batch's captured own, handed down by the caller,
     * and the colour count with it.
     */
    VulkanImmediateParticleRecordsPipeline celtium$recordsParticlePipeline(int format, long key, long pass,
            VulkanColorBlendState colorBlend, int depthCompareOp, int colorAttachments) {
        long branchKey = VulkanTerrainGpuBackend.celtium$immediateBranchKey(format, key, pass);
        VulkanImmediateParticleRecordsPipeline twin =
                this.particlePipelines.get(Long.valueOf(branchKey));
        if (twin == null) {
            if (this.b.drawRecords == null) {
                return null;
            }
            twin = VulkanImmediateParticleRecordsPipeline.create(this.b.context, pass, colorBlend,
                    true, false, VulkanCullState.NONE, depthCompareOp,
                    this.b.drawRecords.descriptorSetLayout(), colorAttachments);
            this.particlePipelines.put(Long.valueOf(branchKey), twin);
        }
        return twin;
    }

    /** {@return the particle pipelines' summed descriptor-set count, for the backend's descriptor census}. */
    long celtium$particleSetsCreated() {
        long total = 0;
        for (VulkanImmediateParticleRecordsPipeline twin : this.particlePipelines.values()) {
            total += twin.setsCreated;
        }
        return total;
    }

    /** {@return the block pipelines' summed descriptor-set count, for the backend's descriptor census}. */
    long celtium$blockSetsCreated() {
        long total = 0;
        for (VulkanImmediateBlockRecordsPipeline twin : this.blockPipelines.values()) {
            total += twin.setsCreated;
        }
        return total;
    }

    /**
     * Deletes every cached pipeline of every family and empties the caches: the release the
     * backend's two teardown sites run, in place of the per-family cache walks they used to do
     * (and of the twin-map walks over the previous host's identity maps).
     *
     * @return how many pipelines were deleted, for the teardown sites' release count
     */
    int celtium$releaseAll() {
        int released = 0;
        for (ConcurrentHashMap<Long, VulkanImmediateTextureRecordsPipeline> cache
                : java.util.List.of(this.texturePipelines, this.positionTexPipelines,
                        this.positionOnlyPipelines, this.positionTexColorNormalPipelines,
                        this.positionTexNormalPipelines, this.positionNormalPipelines,
                        this.texGenPipelines, this.combine4Pipelines)) {
            for (VulkanImmediateTextureRecordsPipeline pipeline : cache.values()) {
                pipeline.delete();
                released++;
            }
            cache.clear();
        }
        for (VulkanImmediateColourRecordsPipeline pipeline : this.colourPipelines.values()) {
            pipeline.delete();
            released++;
        }
        this.colourPipelines.clear();
        for (VulkanImmediateItemRecordsPipeline pipeline : this.itemPipelines.values()) {
            pipeline.delete();
            released++;
        }
        this.itemPipelines.clear();
        for (VulkanImmediateBlockRecordsPipeline pipeline : this.blockPipelines.values()) {
            pipeline.delete();
            released++;
        }
        this.blockPipelines.clear();
        for (VulkanImmediateParticleRecordsPipeline pipeline : this.particlePipelines.values()) {
            pipeline.delete();
            released++;
        }
        this.particlePipelines.clear();
        return released;
    }
}
