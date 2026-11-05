package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * The indirect/record run path of the immediate renderer, moved out of {@link VulkanTerrainGpuBackend} unchanged: a
 * group that qualifies is turned into a record and an indirect command, consecutive qualifying groups share one draw.
 * Opt-in ({@code -Dceltium.vulkan.indirectImmediate=true}); see docs/evidence/array-records-census-2026-11-06.md.
 */
final class IndirectImmediateRuns {
    private final VulkanTerrainGpuBackend b;

    IndirectImmediateRuns(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /** {@code -Dceltium.vulkan.arrayRecords=true}: also store each group's bindless texture index in its draw record. */
    static final boolean ARRAY_RECORDS = Boolean.getBoolean("celtium.vulkan.arrayRecords");
    static final boolean DRAW_RECORDS_ARRAY_INDEX = ARRAY_RECORDS;
    static final int INDIRECT_COMMAND_BYTES = 20;
    final java.util.Map<Object, VulkanImmediateTexturePipeline> recordsPipelines = new java.util.IdentityHashMap<>();
    final java.util.Map<Object, VulkanImmediateItemPipeline> recordsItemPipelines = new java.util.IdentityHashMap<>();
    final java.util.Map<Object, VulkanImmediatePipeline> recordsColourPipelines = new java.util.IdentityHashMap<>();
    Object indirectRunPipelineObject;
    VulkanHostBuffer indirectCommands;
    int indirectCommandCount;
    long indirectRunKey;
    /** The fields {@link #indirectRunKey} hashes, compared exactly when the keys agree. */
    long indirectRunDescriptor;
    long indirectRunVertexBuffer;
    long indirectRunIndexBuffer;
    long indirectRunAlpha;
    boolean indirectRunActive;
    int indirectRunFirstCommand;
    int indirectRunCommands;
    VulkanImmediateTexturePipeline indirectRunPipeline;
    long indirectRunTextureSet;
    int indirectProbeLines;
    VulkanDynamicMesh indirectRunMesh;
    java.nio.ByteBuffer indirectRunPush;
    long indirectRunsIssued;
    /** The pipeline the run's groups arrived with, and how many runs held a single group. */
    Object indirectRunOriginalPipeline;
    long indirectSingletonRuns;
    long indirectGroupsIssued;

    /**
     * {@return the record-reading twin of a texture pipeline, created on first use for that exact state}, or null when the
     * state is one of the colourless position variants.
     * <p>
     * Those variants use a different shader and a different push block shape (the current colour's alpha at 96 and dead
     * model-view columns where the textures sit), while the twin this class builds is the textured family's. Drawing one
     * through the other would sample with an affine built from dead data, so they stay on the per-group path until their
     * own record-reading shaders are wired.
     */
    /**
     * {@code -Dceltium.vulkan.recordsPositionTwins=true}: give the position variants their record-reading twins again.
     * They were refused because groups did not share geometry; immediate groups now write into the frame arena
     * (celtium$newImmediateMesh), which is the condition the refusal named for reopening. The array-records census
     * (docs/evidence/array-records-census-2026-11-06.md) has these variants as the largest run breaker.
     */
    static final boolean RECORDS_POSITION_TWINS = Boolean.getBoolean("celtium.vulkan.recordsPositionTwins");

    VulkanImmediateTexturePipeline celtium$recordsPipeline(VulkanImmediateTexturePipeline state) {
        if (!RECORDS_POSITION_TWINS && (state.celtium$positionOnly() || state.celtium$positionNoTexture())) {
            // Measured with twins for these variants: the refusals fell, but runs still closed every group - the indirect
            // key names each group's own vertex and index buffer, and a group's mesh is its own buffer (key-change
            // 26,950 and stride-alignment refusals 18,711 in one world leg), so a run could not span two groups and the
            // run path only cost more (P50 2.66 -> 5.94 ms with the flag on). The twins stay behind this refusal until
            // groups share a geometry arena.
            return null;
        }
        if (state.celtium$texGen()) {
            // The texgen variant computes texture coordinates in the vertex stage from state the record does not carry;
            // the position variants do have record-reading twins now, so only this one is refused.
            return null;
        }
        VulkanImmediateTexturePipeline twin = this.recordsPipelines.get(state);
        if (twin == null) {
            twin = VulkanImmediateTexturePipeline.createRecordsLike(b.context, state,
                    b.drawRecords.descriptorSetLayout());
            this.recordsPipelines.put(state, twin);
        }
        return twin;
    }

    /**
     * {@return whether this group was turned into indirect work}.
     * <p>
     * The geometry stays where it is: a record is a copy of the push block the group would have pushed, and the command
     * carries the offsets the binding already uses. Everything a run has to share - pipeline, descriptor, the two buffers
     * and the stride - is in the key. A group that does not qualify keeps the per-group draw, and the run is flushed
     * first so ordering against it is unchanged.
     */
    boolean celtium$tryEnqueueIndirect(ImmediateGroup group, VulkanDynamicMesh mesh,
                                               PreparedImmediate prepared, java.nio.ByteBuffer push) {
        if (!VulkanTerrainGpuBackend.INDIRECT_IMMEDIATE || mesh == null || prepared == null || push == null || b.drawRecords == null) {
            celtium$flushIndirectRun("not-ready");
            return false;
        }
        // The two families that carry the frame: the textured one (sky, clouds, HUD) and the lit item one, which is the
        // bulk at about 124 draws. They are separate classes, so each has its own record-reading twin.
        boolean texturedFamily = prepared.pipeline instanceof VulkanImmediateTexturePipeline;
        boolean itemFamily = prepared.pipeline instanceof VulkanImmediateItemPipeline item && item.lit();
        boolean colourFamily = prepared.pipeline instanceof VulkanImmediatePipeline;
        if (!texturedFamily && !itemFamily && !colourFamily) {
            celtium$noteIndirectRefusal("family:" + prepared.pipeline.getClass().getSimpleName());
            celtium$flushIndirectRun("family");
            return false;
        }
        // The colourless position variants have no record-reading twin yet; asking here, before a run is started, keeps a
        // refused group from leaving a half-built run behind.
        if (texturedFamily && (((VulkanImmediateTexturePipeline) prepared.pipeline).celtium$texGen()
                || celtium$recordsPipeline((VulkanImmediateTexturePipeline) prepared.pipeline) == null)) {
            VulkanImmediateTexturePipeline refused = (VulkanImmediateTexturePipeline) prepared.pipeline;
            celtium$noteIndirectRefusal(refused.celtium$texGen() ? "texGen"
                    : refused.celtium$positionOnly() ? "positionOnly" : "positionNoTexture");
            celtium$flushIndirectRun("texture-twin");
            return false;
        }
        if (colourFamily) {
            VulkanImmediatePipeline colourState = (VulkanImmediatePipeline) prepared.pipeline;
            if (this.recordsColourPipelines.get(colourState) == null) {
                this.recordsColourPipelines.put(colourState, VulkanImmediatePipeline.createRecords(b.context,
                        colourState.celtium$renderPass(), colourState.celtium$colorBlend(),
                        colourState.celtium$depthTests(), colourState.celtium$depthWrites(),
                        colourState.celtium$cullState(), colourState.celtium$depthCompareOp(),
                        b.drawRecords.descriptorSetLayout()));
            }
        }
        // Blended groups are eligible as well: the opaque ordering only moves the opaque ones, so a blended group keeps its
        // arrival position, and the commands of a run are issued in the order they were recorded. A run therefore never
        // reorders blending; it only stops at a material change, which is where the per-group path took over anyway.
        if (group.postClears != null || mesh.layout() == null) {
            celtium$flushIndirectRun("post-clears");
            return false;
        }
        int stride = mesh.layout().stride;
        long vertexOffsetBytes = mesh.vertexBindOffset();
        long indexOffsetBytes = mesh.indexBindOffset();
        if (this.indirectProbeLines < 12) {
            // Bounded: what a group hands the run path (array-records census, the r110 picture failure).
            this.indirectProbeLines++;
            System.out.println("[Celtium] CELTIUM_INDIRECT_PROBE stride=" + stride + " vertexOffset=" + vertexOffsetBytes
                    + " indexOffset=" + indexOffsetBytes + " vertices=" + mesh.vertexCount() + " indices="
                    + mesh.indexCount() + " arena=" + mesh.isArenaBorrow() + " pipeline="
                    + prepared.pipeline.getClass().getSimpleName());
        }
        if (stride <= 0 || vertexOffsetBytes % stride != 0L || indexOffsetBytes % 4L != 0L
                || mesh.indexCount() <= 0 || mesh.vertexCount() <= 0) {
            celtium$flushIndirectRun("stride");
            return false;
        }
        // The fragment stage still takes the alpha reference and the current colour from the push block, so a run has to
        // agree on those sixteen bytes too; everything else the block carries the vertex stage now reads per record.
        long alphaKey = 0L;
        for (int offset = 112; offset < 128; offset++) {
            alphaKey = 31L * alphaKey + push.get(offset);
        }
        // The textured records fragment reads the alpha reference, the colour and the fog block from the record, so those
        // sixteen push bytes no longer have to agree for a run; the lit item family's fragment still reads the run's push,
        // which is why its key keeps them.
        boolean perRecordMatte = texturedFamily;
        long key = (((long) System.identityHashCode(prepared.pipeline)) * 0x9E3779B97F4A7C15L)
                ^ (prepared.descriptor * 0x94D049BB133111EBL)
                ^ (((long) mesh.vertexBuffer()) * 31L) ^ ((long) mesh.indexBuffer())
                ^ (perRecordMatte ? 0L : (alphaKey * 0xC2B2AE3D27D4EB4FL));
        // The key is a hash, so equal keys are checked against the fields it was built from: a collision must not put a
        // group into a run drawn with another pipeline, descriptor or buffer.
        long runAlpha = perRecordMatte ? 0L : alphaKey;
        if (this.indirectRunActive && (key != this.indirectRunKey
                || prepared.pipeline != this.indirectRunOriginalPipeline
                || prepared.descriptor != this.indirectRunDescriptor
                || mesh.vertexBuffer() != this.indirectRunVertexBuffer
                || mesh.indexBuffer() != this.indirectRunIndexBuffer
                || runAlpha != this.indirectRunAlpha)) {
            celtium$flushIndirectRun("key-change");
        }
        int recordIndex = b.drawRecords.record(push, DRAW_RECORDS_ARRAY_INDEX ? group.textureArrayIndex : -1);
        if (recordIndex < 0) {
            return false;
        }
        if (this.indirectCommands == null) {
            this.indirectCommands = new VulkanHostBuffer(b.context, 256L * INDIRECT_COMMAND_BYTES,
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
        }
        if (!this.indirectRunActive) {
            this.indirectRunActive = true;
            this.indirectRunKey = key;
            this.indirectRunDescriptor = prepared.descriptor;
            this.indirectRunVertexBuffer = mesh.vertexBuffer();
            this.indirectRunIndexBuffer = mesh.indexBuffer();
            this.indirectRunAlpha = runAlpha;
            if (prepared.pipeline instanceof VulkanImmediateTexturePipeline texture) {
                this.indirectRunPipeline = celtium$recordsPipeline(texture);
                this.indirectRunPipelineObject = this.indirectRunPipeline;
            } else if (prepared.pipeline instanceof VulkanImmediatePipeline colourState) {
                this.indirectRunPipeline = null;
                this.indirectRunPipelineObject = this.recordsColourPipelines.get(colourState);
            } else {
                VulkanImmediateItemPipeline itemState = (VulkanImmediateItemPipeline) prepared.pipeline;
                VulkanImmediateItemPipeline twin = this.recordsItemPipelines.get(itemState);
                if (twin == null) {
                    twin = VulkanImmediateItemPipeline.createRecords(b.context, itemState.celtium$renderPass(),
                            itemState.celtium$colorBlend(), itemState.celtium$depthTests(),
                            itemState.celtium$depthWrites(), itemState.celtium$cullState(),
                            itemState.celtium$depthCompareOp(), b.drawRecords.descriptorSetLayout());
                    this.recordsItemPipelines.put(itemState, twin);
                }
                this.indirectRunPipeline = null;
                this.indirectRunPipelineObject = twin;
            }
            this.indirectRunTextureSet = prepared.descriptor;
            this.indirectRunMesh = mesh;
            this.indirectRunOriginalPipeline = prepared.pipeline;
            this.indirectRunFirstCommand = this.indirectCommandCount;
            this.indirectRunCommands = 0;
            if (this.indirectRunPush == null) {
                this.indirectRunPush = org.lwjgl.system.MemoryUtil.memAlloc(256);
            }
            for (int i = 0; i < 208; i++) {
                this.indirectRunPush.put(i, push.get(i));
            }
        }
        this.indirectCommands.ensureCapacity((this.indirectCommandCount + 1L) * INDIRECT_COMMAND_BYTES);
        long commandAddress = this.indirectCommands.mappedAddress() + (long) this.indirectCommandCount * INDIRECT_COMMAND_BYTES;
        org.lwjgl.system.MemoryUtil.memPutInt(commandAddress, mesh.indexCount());
        org.lwjgl.system.MemoryUtil.memPutInt(commandAddress + 4, 1);
        org.lwjgl.system.MemoryUtil.memPutInt(commandAddress + 8, (int) (indexOffsetBytes / 4L));
        org.lwjgl.system.MemoryUtil.memPutInt(commandAddress + 12, (int) (vertexOffsetBytes / (long) stride));
        org.lwjgl.system.MemoryUtil.memPutInt(commandAddress + 16, recordIndex);
        this.indirectCommandCount++;
        this.indirectRunCommands++;
        this.indirectGroupsIssued++;
        return true;
    }

    /** Issues the open run as one vkCmdDrawIndexedIndirect. */
    /** Where an indirect run was closed, by short reason, for the run-formation census. */
    final java.util.Map<String, Integer> indirectFlushReasons = new java.util.LinkedHashMap<String, Integer>();
    /** Why groups were refused before a run could start, by short name. */
    final java.util.Map<String, Integer> indirectRefusalCounts = new java.util.LinkedHashMap<String, Integer>();

    void celtium$noteIndirectRefusal(String reason) {
        Integer seen = this.indirectRefusalCounts.get(reason);
        this.indirectRefusalCounts.put(reason, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
    }


    void celtium$flushIndirectRun() {
        celtium$flushIndirectRun("unknown");
    }

    void celtium$flushIndirectRun(String reason) {
        if (!this.indirectRunActive) {
            return;
        }
        Integer seenReason = this.indirectFlushReasons.get(reason);
        this.indirectFlushReasons.put(reason, Integer.valueOf(seenReason == null ? 1 : seenReason.intValue() + 1));
        if (this.indirectRunCommands == 1) {
            Integer single = this.indirectFlushReasons.get("singleton:" + reason);
            this.indirectFlushReasons.put("singleton:" + reason,
                    Integer.valueOf(single == null ? 1 : single.intValue() + 1));
        }
        this.indirectRunActive = false;
        // The colour family's twin lives in the object field, not in indirectRunPipeline, so testing that field here
        // dropped every colour-family run before it was drawn. The dispatch below is what knows which of the two holds
        // the run's pipeline; this guard only asks whether there is anything to draw at all.
        if (this.indirectRunMesh == null || this.indirectRunCommands <= 0
                || (this.indirectRunPipeline == null && this.indirectRunPipelineObject == null)) {
            return;
        }
        if (this.indirectRunCommands == 1 && this.indirectRunOriginalPipeline != null) {
            // A run of one group has nothing to save: the record and the command would be the same work as the ordinary
            // draw plus the set pair and the dispatch. It is drawn the way it would have been without the run path, which
            // also keeps a frame of all-distinct materials at its old cost instead of paying for the machinery.
            Object original = this.indirectRunOriginalPipeline;
            if (original instanceof VulkanImmediateTexturePipeline textureState) {
                textureState.draw(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                        this.indirectRunTextureSet, b.immediateBindCache);
            } else if (original instanceof VulkanImmediateItemPipeline itemState) {
                itemState.draw(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                        this.indirectRunTextureSet, b.immediateBindCache);
            } else if (original instanceof VulkanImmediatePipeline colourState) {
                colourState.draw(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                        b.immediateBindCache);
            }
            this.indirectSingletonRuns++;
            this.indirectRunsIssued++;
            return;
        }
        if (this.indirectRunPipeline != null) {
            this.indirectRunPipeline.drawIndirectRange(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                    this.indirectRunTextureSet, b.drawRecords.descriptorSet(), b.immediateBindCache,
                    this.indirectCommands.buffer(), (long) this.indirectRunFirstCommand * INDIRECT_COMMAND_BYTES,
                    this.indirectRunCommands);
        } else if (this.indirectRunPipelineObject instanceof VulkanImmediateItemPipeline item) {
            item.drawIndirectRange(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                    this.indirectRunTextureSet, b.drawRecords.descriptorSet(), b.immediateBindCache,
                    this.indirectCommands.buffer(), (long) this.indirectRunFirstCommand * INDIRECT_COMMAND_BYTES,
                    this.indirectRunCommands);
        } else if (this.indirectRunPipelineObject instanceof VulkanImmediatePipeline colour) {
            colour.drawIndirectRange(b.commandBuffer, this.indirectRunMesh, this.indirectRunPush,
                    b.drawRecords.descriptorSet(), b.immediateBindCache,
                    this.indirectCommands.buffer(), (long) this.indirectRunFirstCommand * INDIRECT_COMMAND_BYTES,
                    this.indirectRunCommands);
        }
        this.indirectRunsIssued++;
    }
}
