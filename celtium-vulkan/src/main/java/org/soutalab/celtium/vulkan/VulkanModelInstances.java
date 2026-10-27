package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Lit model display lists drawn as instances: the first native producer of the new frame architecture's model path.
 * <p>
 * Measured need (leg {@code prof-celtium-4}, AMD, world): about 800 display-list replays a frame, each one an
 * {@link ImmediateGroup} of its own with its own push block, binds and draw - most of the record loop and much of the
 * accept path. Here a replay is one 256-byte record (matrix, colour, combine, texture affine, lighting, texture and
 * light-map indices) and one indirect command into a shared mesh pool, and a run of consecutive replays with the same
 * pipeline state is one {@code vkCmdDrawIndexedIndirect}. Textures come from {@link VulkanTextureArray} by index, so
 * a texture change no longer breaks a run.
 * <p>
 * Lifetimes. Records and commands live in {@link #SEGMENTS} per-frame segments of one buffer each; a frame writes only
 * its own segment, and the scheduler lets at most {@link FrameSubmissionScheduler#FRAME_SLOTS} frames be in flight, so
 * a segment is reused only after the frame that wrote it has completed. The mesh pool is rebuilt when the model cache
 * builds a new table; the previous pool's buffers are released {@link #SEGMENTS} frames later, for the same reason.
 * A replay that does not fit (segment full, list absent from the pool, no array index) is declined and takes the
 * per-group path, so a decline is a slower draw, never a missing one.
 * <p>
 * Threading: render thread only.
 */
final class VulkanModelInstances {
    static final int RECORD_BYTES = 256;
    static final int COMMAND_BYTES = 20;
    static final int SEGMENTS = FrameSubmissionScheduler.FRAME_SLOTS + 2;
    static final int SEGMENT_RECORDS = 4096;
    static final int VERTEX_BYTES = 28;

    /** A run of instances that draw with one pipeline: one indirect call. */
    static final class Run {
        final Pipeline pipeline;
        final int firstCommand;
        int count;
        final long commandBuffer;
        final long vertexBuffer;
        final long indexBuffer;
        /** The GL names the run samples, so an upload of one of them can flush the window first. */
        int[] textures = new int[8];
        int textureCount;

        Run(Pipeline pipeline, int firstCommand, long commandBuffer, long vertexBuffer, long indexBuffer) {
            this.pipeline = pipeline;
            this.firstCommand = firstCommand;
            this.commandBuffer = commandBuffer;
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
        }

        boolean usesTexture(int glTextureId) {
            for (int i = 0; i < this.textureCount; i++) {
                if (this.textures[i] == glTextureId) {
                    return true;
                }
            }
            return false;
        }

        void noteTexture(int glTextureId) {
            // Consecutive instances of a run almost always share the texture they were just noted with: one compare
            // before the scan, which ran per appended instance over every texture the run had seen.
            if (this.textureCount > 0 && this.textures[this.textureCount - 1] == glTextureId) {
                return;
            }
            if (usesTexture(glTextureId)) {
                return;
            }
            if (this.textureCount == this.textures.length) {
                this.textures = java.util.Arrays.copyOf(this.textures, this.textureCount * 2);
            }
            this.textures[this.textureCount++] = glTextureId;
        }
    }

    /** One pipeline per render pass and fixed-function state, like the per-group families. */
    static final class Pipeline {
        final long handle;
        final long renderPass;

        Pipeline(long handle, long renderPass) {
            this.handle = handle;
            this.renderPass = renderPass;
        }
    }

    private final VulkanContext context;
    private final long textureSetLayout;
    private VulkanHostBuffer records;
    private VulkanHostBuffer commands;
    private long recordsLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long recordsSet = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private final Map<Long, Pipeline> pipelines = new HashMap<Long, Pipeline>();

    private int segment;
    private int used;
    private long frameId = Long.MIN_VALUE;

    private VulkanHostBuffer poolVertices;
    private VulkanHostBuffer poolIndices;
    private Object poolSource;
    /** listId -> {firstIndex, vertexOffset, indexCount}. */
    // Primitive-keyed: append and hasList look a list up per model replay.
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<int[]> poolEntries =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<int[]>();
    /** The mesh object each pooled list was copied from, so an unchanged list is not copied again. */
    private final Map<Integer, VulkanDynamicMesh> poolMeshes = new HashMap<Integer, VulkanDynamicMesh>();
    private long poolVertexUsed;
    private long poolIndexUsed;
    private long poolAppends;
    private long poolGrowths;
    private final List<Object[]> retiredPools = new ArrayList<Object[]>();

    private long appended;
    private long unlitAppended;
    private long geometryDraws;
    private long declinedFull;
    private long declinedMissing;
    private long runs;
    private long poolBuilds;

    VulkanModelInstances(VulkanContext context, long textureSetLayout) {
        this.context = context;
        this.textureSetLayout = textureSetLayout;
    }

    /** Creates the record and command buffers, the records descriptor and the pipeline layout. */
    boolean create() {
        this.records = new VulkanHostBuffer(this.context, (long) SEGMENTS * SEGMENT_RECORDS * RECORD_BYTES,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        this.commands = new VulkanHostBuffer(this.context, (long) SEGMENTS * SEGMENT_RECORDS * COMMAND_BYTES,
                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(this.context.device(), org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
                    .calloc(stack).sType$Default().pBindings(bindings), null, handle) != VK_SUCCESS) {
                return false;
            }
            this.recordsLayout = handle.get(0);
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer sizes = org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
            if (vkCreateDescriptorPool(this.context.device(), org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(1).pPoolSizes(sizes), null, handle) != VK_SUCCESS) {
                return false;
            }
            this.descriptorPool = handle.get(0);
            if (vkAllocateDescriptorSets(this.context.device(), org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
                    .calloc(stack).sType$Default().descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.recordsLayout)), handle) != VK_SUCCESS) {
                return false;
            }
            this.recordsSet = handle.get(0);
            org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer info = org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(1, stack);
            info.get(0).buffer(this.records.buffer()).offset(0L).range(VK_WHOLE_SIZE);
            org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write = org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default().dstSet(this.recordsSet).dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(info);
            vkUpdateDescriptorSets(this.context.device(), write, null);

            // Projection and fog change per run; individual poses live in the storage records.
            org.lwjgl.vulkan.VkPushConstantRange.Buffer ranges = org.lwjgl.vulkan.VkPushConstantRange.calloc(1, stack);
            ranges.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(ModelRunConstants.BYTES);
            if (vkCreatePipelineLayout(this.context.device(), VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.textureSetLayout, this.recordsLayout)).pPushConstantRanges(ranges), null, handle) != VK_SUCCESS) {
                return false;
            }
            this.pipelineLayout = handle.get(0);
        }
        return true;
    }

    /** Moves to this frame's segment, and releases mesh pools no frame in flight can still read. */
    void beginFrame(long frameId) {
        if (frameId == this.frameId) {
            return;
        }
        this.frameId = frameId;
        this.segment = (this.segment + 1) % SEGMENTS;
        this.used = 0;
        for (int i = this.retiredPools.size() - 1; i >= 0; i--) {
            Object[] retired = this.retiredPools.get(i);
            if (frameId - ((Long) retired[2]).longValue() > SEGMENTS) {
                ((VulkanHostBuffer) retired[0]).delete();
                ((VulkanHostBuffer) retired[1]).delete();
                this.retiredPools.remove(i);
            }
        }
    }

    /**
     * Makes the pool hold the model cache's current table; a new table (identity) rebuilds the pool once. The copy is
     * the meshes' own mapped bytes, so the pool holds exactly what the per-group path would draw.
     */
    void syncPool(Map<Integer, VulkanDynamicMesh> meshes) {
        if (meshes == null || meshes == this.poolSource) {
            return;
        }
        // Incremental: the model cache hands out a new table every time a display list is compiled - 593 times in the
        // user's four-minute session, a few at a time as new mobs and block entities came into view - and copying the
        // whole pool into fresh buffers each time was a frame spike per new model. A list whose mesh object is the one
        // already copied keeps its slice; a new or rebuilt list is appended; only a full pool grows (doubling), and the
        // grown buffers take the used bytes in one copy.
        java.util.Set<Integer> live = new java.util.HashSet<Integer>();
        for (Map.Entry<Integer, VulkanDynamicMesh> entry : meshes.entrySet()) {
            VulkanDynamicMesh mesh = entry.getValue();
            if (mesh == null || mesh.isEmpty() || mesh.layout() == null || mesh.layout().stride != VERTEX_BYTES) {
                continue;
            }
            live.add(entry.getKey());
            if (this.poolMeshes.get(entry.getKey()) == mesh) {
                continue;
            }
            long vBytes = (long) mesh.vertexCount() * VERTEX_BYTES;
            long iBytes = (long) mesh.indexCount() * 4L;
            ensurePoolCapacity(this.poolVertexUsed + vBytes, this.poolIndexUsed + iBytes);
            MemoryUtil.memCopy(mesh.vertexAddress(), this.poolVertices.mappedAddress() + this.poolVertexUsed, vBytes);
            MemoryUtil.memCopy(mesh.indexAddress(), this.poolIndices.mappedAddress() + this.poolIndexUsed, iBytes);
            this.poolEntries.put(entry.getKey(), new int[] {(int) (this.poolIndexUsed / 4L),
                    (int) (this.poolVertexUsed / VERTEX_BYTES), mesh.indexCount()});
            this.poolMeshes.put(entry.getKey(), mesh);
            this.poolVertexUsed += vBytes;
            this.poolIndexUsed += iBytes;
            this.poolAppends++;
        }
        // Lists the cache no longer holds are forgotten; their bytes stay until the next growth reclaims nothing (the
        // pool is append-only between growths, which is what keeps every in-flight run's offsets valid).
        this.poolEntries.keySet().retainAll(live);
        this.poolMeshes.keySet().retainAll(live);
        this.poolSource = meshes;
        this.poolBuilds++;
    }

    /** Grows the pool to hold at least the given used bytes; the old buffers retire behind the frames in flight. */
    private void ensurePoolCapacity(long vertexBytes, long indexBytes) {
        if (this.poolVertices != null && vertexBytes <= this.poolVertices.size() && indexBytes <= this.poolIndices.size()) {
            return;
        }
        long vCapacity = Math.max(1L << 20, Math.max(vertexBytes, this.poolVertices == null ? 0L : this.poolVertices.size() * 2L));
        long iCapacity = Math.max(1L << 18, Math.max(indexBytes, this.poolIndices == null ? 0L : this.poolIndices.size() * 2L));
        VulkanHostBuffer vertices = new VulkanHostBuffer(this.context, vCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        VulkanHostBuffer indices = new VulkanHostBuffer(this.context, iCapacity, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        if (this.poolVertices != null) {
            MemoryUtil.memCopy(this.poolVertices.mappedAddress(), vertices.mappedAddress(), this.poolVertexUsed);
            MemoryUtil.memCopy(this.poolIndices.mappedAddress(), indices.mappedAddress(), this.poolIndexUsed);
            this.retiredPools.add(new Object[] {this.poolVertices, this.poolIndices, Long.valueOf(this.frameId)});
        }
        this.poolVertices = vertices;
        this.poolIndices = indices;
        this.poolGrowths++;
    }

    /** {@return whether the pool holds this list}. */
    boolean hasList(int listId) {
        return this.poolEntries.containsKey(listId);
    }

    /** {@return the pipeline for this pass and state}, creating it on first use. */
    Pipeline pipelineFor(long renderPass, long stateKey, VulkanColorBlendState blend, boolean depthTest,
            boolean depthWrite, boolean cull, int depthCompareOp) {
        return pipelineFor(renderPass, stateKey, blend, depthTest, depthWrite, cull, depthCompareOp,
                VulkanImmediateFormats.ITEM);
    }

    Pipeline pipelineFor(long renderPass, long stateKey, VulkanColorBlendState blend, boolean depthTest,
            boolean depthWrite, boolean cull, int depthCompareOp, int format) {
        long key = (stateKey * 31L + renderPass) * 31L + format;
        Pipeline pipeline = this.pipelines.get(Long.valueOf(key));
        if (pipeline == null) {
            pipeline = new Pipeline(createPipeline(renderPass, blend, depthTest, depthWrite, cull, depthCompareOp, format),
                    renderPass);
            this.pipelines.put(Long.valueOf(key), pipeline);
        }
        return pipeline;
    }

    /** Opens a run at the next command of this frame's segment. */
    Run openRun(Pipeline pipeline) {
        this.runs++;
        return new Run(pipeline, this.segment * SEGMENT_RECORDS + this.used, this.commands.buffer(),
                this.poolVertices.buffer(), this.poolIndices.buffer());
    }

    /** {@return whether a run opened on the current pool may still be appended to}. */
    boolean sameBuffers(Run run) {
        return run.vertexBuffer == this.poolVertices.buffer() && run.indexBuffer == this.poolIndices.buffer()
                && run.firstCommand + run.count == this.segment * SEGMENT_RECORDS + this.used;
    }

    /** {@return whether this frame's segment has room for one more instance}. */
    boolean hasRoom() {
        if (this.used < SEGMENT_RECORDS) {
            return true;
        }
        this.declinedFull++;
        return false;
    }

    /**
     * Writes one instance - its record and its indirect command - and appends it to {@code run}.
     *
     * @param affine the texture affine (a, b, c, d, tx, ty), or null for the identity
     * @param light  the lit payload: two model-space directions, ambient and two diffuses (20 floats)
     */
    void append(Run run, int listId, float[] modelView, float[] colour, float[] combine, float[] affine, float alphaRef, int alphaFunction,
            float[] light, float[] lightCoord, int textureIndex, int lightmapIndex, int glTexture) {
        int[] entry = this.poolEntries.get(listId);
        int index = this.segment * SEGMENT_RECORDS + this.used;
        writeInstance(index, modelView, colour, combine, affine, alphaRef, alphaFunction,
                light, lightCoord, textureIndex, lightmapIndex);
        if (light == null) this.unlitAppended++;
        long c = this.commands.mappedAddress() + (long) index * COMMAND_BYTES;
        MemoryUtil.memPutInt(c, entry[2]);
        MemoryUtil.memPutInt(c + 4, 1);
        MemoryUtil.memPutInt(c + 8, entry[0]);
        MemoryUtil.memPutInt(c + 12, entry[1]);
        MemoryUtil.memPutInt(c + 16, index);
        this.used++;
        run.count++;
        run.noteTexture(glTexture);
        this.appended++;
    }

    private void writeInstance(int index, float[] modelView, float[] colour, float[] combine, float[] affine,
            float alphaRef, int alphaFunction, float[] light, float[] lightCoord, int textureIndex, int lightmapIndex) {
        long r = this.records.mappedAddress() + (long) index * RECORD_BYTES;
        for (int i = 0; i < 16; i++) {
            MemoryUtil.memPutFloat(r + i * 4L, modelView[i]);
        }
        boolean combined = combine != null;
        MemoryUtil.memPutFloat(r + 64, combined || colour == null ? 1.0f : colour[0]);
        MemoryUtil.memPutFloat(r + 68, combined || colour == null ? 1.0f : colour[1]);
        MemoryUtil.memPutFloat(r + 72, combined || colour == null ? 1.0f : colour[2]);
        MemoryUtil.memPutFloat(r + 76, combined || colour == null || colour.length < 4 ? 1.0f : colour[3]);
        MemoryUtil.memPutFloat(r + 80, combined ? combine[0] : 0.0f);
        MemoryUtil.memPutFloat(r + 84, combined ? combine[1] : 0.0f);
        MemoryUtil.memPutFloat(r + 88, combined ? combine[2] : 0.0f);
        MemoryUtil.memPutFloat(r + 92, combined ? combine[3] : 0.0f);
        if (affine != null) {
            for (int i = 0; i < 6; i++) {
                MemoryUtil.memPutFloat(r + 96 + i * 4L, affine[i]);
            }
            MemoryUtil.memPutFloat(r + 120, 1.0f);
        } else {
            MemoryUtil.memPutFloat(r + 120, 0.0f);
        }
        // The alpha test holds under the combine as well (GL's COMBINE_ALPHA keeps the texture alpha the test reads).
        MemoryUtil.memPutFloat(r + 124, alphaRef);
        writeShadingRecord(r, light, lightCoord, textureIndex, lightmapIndex, alphaFunction);
    }

    /** Executes an owned/leased immediate mesh through the same pose/lighting/fog program as retained models. */
    void drawGeometry(VkCommandBuffer commandBuffer, Pipeline pipeline, long textureSet, ImmediateGroup group,
            int textureIndex, int lightmapIndex) {
        int index = this.segment * SEGMENT_RECORDS + this.used++;
        writeInstance(index, group.modelView, group.color, group.combineColor, group.texMatrix,
                Float.intBitsToFloat(group.state[6]), group.state[14], group.lightParams,
                group.lightmapCoords, textureIndex, lightmapIndex);
        if (group.formatId == VulkanImmediateFormats.POSITION_TEX_NORMAL) {
            MemoryUtil.memPutInt(this.records.mappedAddress() + (long) index * RECORD_BYTES + 236, 3);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            bindProgram(commandBuffer, pipeline, textureSet, group.projection, group.fog, stack);
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(group.mesh.vertexBuffer()),
                    stack.longs(group.mesh.vertexBindOffset()));
            vkCmdBindIndexBuffer(commandBuffer, group.mesh.indexBuffer(), group.mesh.indexBindOffset(), VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexed(commandBuffer, group.mesh.indexCount(), 1, 0, 0, index);
        }
        this.geometryDraws++;
    }

    /** Defined for every reused slot, including lit -> unlit and disabled -> zero-reference alpha changes. */
    static void writeShadingRecord(long record, float[] light, float[] lightCoord,
            int textureIndex, int lightmapIndex, int alphaFunction) {
        if (light == null) MemoryUtil.memSet(record + 128, 0, 80);
        else for (int i = 0; i < 20; i++) MemoryUtil.memPutFloat(record + 128 + i * 4L, light[i]);
        MemoryUtil.memPutFloat(record + 208, lightCoord == null ? 0.0f : lightCoord[0]);
        MemoryUtil.memPutFloat(record + 212, lightCoord == null ? 0.0f : lightCoord[1]);
        MemoryUtil.memPutInt(record + 224, textureIndex);
        MemoryUtil.memPutInt(record + 228, lightmapIndex);
        MemoryUtil.memPutInt(record + 232, alphaFunction);
        MemoryUtil.memPutInt(record + 236, light == null ? 0 : 1);
    }

    void noteMissing() {
        this.declinedMissing++;
    }

    /**
     * Records a run: pipeline, the texture array and records sets, the pool buffers, then the indirect draw. The caller has already set the group's viewport and bias and
     * must treat the immediate bind cache as stale afterwards.
     */
    void draw(VkCommandBuffer commandBuffer, Run run, long textureSet, boolean multiDraw,
            float[] projection, float[] fog) {
        if (run.count <= 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            bindProgram(commandBuffer, run.pipeline, textureSet, projection, fog, stack);
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(run.vertexBuffer), stack.longs(0L));
            vkCmdBindIndexBuffer(commandBuffer, run.indexBuffer, 0L, VK_INDEX_TYPE_UINT32);
            long offset = (long) run.firstCommand * COMMAND_BYTES;
            if (multiDraw) {
                vkCmdDrawIndexedIndirect(commandBuffer, run.commandBuffer, offset, run.count, COMMAND_BYTES);
            } else {
                for (int i = 0; i < run.count; i++) {
                    vkCmdDrawIndexedIndirect(commandBuffer, run.commandBuffer, offset + (long) i * COMMAND_BYTES, 1,
                            COMMAND_BYTES);
                }
            }
        }
    }

    private void bindProgram(VkCommandBuffer commandBuffer, Pipeline pipeline, long textureSet,
            float[] projection, float[] fog, MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle);
        ByteBuffer constants = stack.calloc(ModelRunConstants.BYTES);
        ModelRunConstants.write(constants, projection, fog);
        vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                stack.longs(textureSet, this.recordsSet), null);
    }

    String describe() {
        return "modelInstances[appended=" + this.appended + " geometryDraws=" + this.geometryDraws + " unlitAppended=" + this.unlitAppended + " runs=" + this.runs + " declinedFull=" + this.declinedFull
                + " declinedMissing=" + this.declinedMissing + " poolBuilds=" + this.poolBuilds
                + " poolAppends=" + this.poolAppends + " poolGrowths=" + this.poolGrowths
                + " poolLists=" + this.poolEntries.size() + " pipelines=" + this.pipelines.size() + "]";
    }

    /** Destroys everything; the caller has waited for the device. */
    void delete() {
        for (Pipeline pipeline : this.pipelines.values()) {
            vkDestroyPipeline(this.context.device(), pipeline.handle, null);
        }
        this.pipelines.clear();
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
        if (this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
        }
        if (this.recordsLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.recordsLayout, null);
            this.recordsLayout = VK_NULL_HANDLE;
        }
        for (Object[] retired : this.retiredPools) {
            ((VulkanHostBuffer) retired[0]).delete();
            ((VulkanHostBuffer) retired[1]).delete();
        }
        this.retiredPools.clear();
        if (this.poolVertices != null) {
            this.poolVertices.delete();
            this.poolIndices.delete();
            this.poolVertices = null;
            this.poolIndices = null;
        }
        this.poolSource = null;
        this.poolEntries.clear();
        this.poolMeshes.clear();
        this.poolVertexUsed = 0L;
        this.poolIndexUsed = 0L;
        if (this.records != null) {
            this.records.delete();
            this.records = null;
        }
        if (this.commands != null) {
            this.commands.delete();
            this.commands = null;
        }
    }

    private long createPipeline(long renderPass, VulkanColorBlendState colorBlend, boolean depthTest,
            boolean depthWrite, boolean cull, int depthCompareOp, int format) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.MODEL_INSTANCE_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.MODEL_INSTANCE_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                LongBuffer handle = stack.mallocLong(1);
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "model instance vertex");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "model instance fragment");
                fragmentModule = handle.get(0);
                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pName(stack.UTF8("main"));
                // The ITEM layout the model cache builds: position, colour, texture, normal (VulkanImmediateItemPipeline).
                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(format == VulkanImmediateFormats.POSITION_TEX_NORMAL ? 24 : VERTEX_BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(4, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(format == VulkanImmediateFormats.ITEM ? 16 : 12);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(format == VulkanImmediateFormats.ITEM ? 12 : format == VulkanImmediateFormats.POSITION_TEX_NORMAL ? 0 : 20);
                attributes.get(3).location(3).binding(0).format(VK_FORMAT_R8G8B8_SNORM).offset(format == VulkanImmediateFormats.POSITION_TEX_NORMAL ? 20 : 24);
                VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                        .sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attributes);
                VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default().viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).depthBiasEnable(true)
                        .cullMode(cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_CLOCKWISE).lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default().depthTestEnable(depthTest).depthWriteEnable(depthWrite)
                        .depthCompareOp(depthCompareOp);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0).blendEnable(colorBlend.blend())
                        .srcColorBlendFactor(colorBlend.srcColor()).dstColorBlendFactor(colorBlend.dstColor())
                        .colorBlendOp(colorBlend.colorOp()).srcAlphaBlendFactor(colorBlend.srcAlpha())
                        .dstAlphaBlendFactor(colorBlend.dstAlpha()).alphaBlendOp(colorBlend.alphaOp())
                        .colorWriteMask(colorBlend.writeMask());
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                                VK_DYNAMIC_STATE_DEPTH_BIAS));
                VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                create.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                        .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                        .pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend)
                        .pDynamicState(dynamic).layout(this.pipelineLayout).renderPass(renderPass).subpass(0);
                LongBuffer pipelineHandle = stack.mallocLong(1);
                check(VulkanContext.createGraphicsPipelines(this.context.device(), create, pipelineHandle),
                        "model instance pipeline");
                return pipelineHandle.get(0);
            } finally {
                if (vertexModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), vertexModule, null);
                }
                if (fragmentModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), fragmentModule, null);
                }
                MemoryUtil.memFree(vertexCode);
                MemoryUtil.memFree(fragmentCode);
            }
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
