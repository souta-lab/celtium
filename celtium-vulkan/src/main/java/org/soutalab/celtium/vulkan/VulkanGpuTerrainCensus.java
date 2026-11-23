package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * E10-a's shadow generation (design sections 5.3 (c) and 7.3): while drawing stays on the
 * current CPU path, this class replays the previous frame's terrain draw population through
 * the GPU generation compute shader and compares what comes back against the CPU's canonical
 * command list for that same frame.
 * <p>
 * Why the previous frame: entries are collected during the region loop, which records inside
 * the frame's render pass, and a compute dispatch cannot be recorded there. The finished
 * collection is therefore dispatched at the head of the next frame's first terrain pass,
 * before its render pass opens. The comparison is same-frame on both sides (the stored
 * canonical list is the dispatched frame's own), so the one-frame delay costs freshness,
 * not correctness of the oracle — it is the S-type staleness the design already accepts
 * (section 2.4), applied to the instrument rather than to drawing.
 * <p>
 * The compute price is measured on its own, per design section 7.3: a timestamp pair
 * brackets only the census dispatches, so the PASS_GPU difference a leg would see is the
 * compute-plus-barrier price itself, readable before any drawing switch is contemplated.
 * Results are read back when the frame slot cycles (the slot's prior submission is fenced
 * by then — the same guarantee the indirect packet pages rely on), never by stalling.
 * <p>
 * All buffers are host-visible coherent ({@link VulkanHostBuffer}): the census is an
 * instrument, not a drawing path, so it takes the simple memory shape over the fast one.
 * Nothing here is created unless the census flag is on, and a failure anywhere in the
 * census disables it with one printed line rather than touching the frame.
 */
final class VulkanGpuTerrainCensus {

    private static final int MAX_PASSES_PER_FRAME = 8;
    private static final int COUNT_SLOTS = MAX_PASSES_PER_FRAME;
    private static final int WORKGROUP = 256;
    private static final int PUSH_BYTES = 144;
    private static final int PRINT_EVERY_FRAMES = 60;

    private final VulkanContext context;
    private final float timestampPeriod;
    private long setLayout;
    private long pipelineLayout;
    private long pipeline;
    private long queryPool;
    private boolean disabled;
    private boolean gatePrinted;

    private final Slot[] slots = new Slot[FrameSubmissionScheduler.FRAME_SLOTS];

    /** The frame currently being collected, and the one waiting for its dispatch. */
    private FrameCollection collecting;

    // Cumulative census totals, printed every PRINT_EVERY_FRAMES harvested frames.
    private long framesHarvested;
    private long totalEntries;
    private long totalCpuCommands;
    private long totalGpuCommands;
    private long totalMatched;
    private long totalCpuOnly;
    private long totalGpuOnly;
    private long totalNonUniformBatches;
    private long computeSamples;
    private long computeNanosSum;
    private long computeNanosMax;

    private static final class Slot {
        VulkanHostBuffer table;
        VulkanHostBuffer remap;
        VulkanHostBuffer mask;
        VulkanHostBuffer out;
        VulkanHostBuffer counts;
        long descriptorPool;
        long descriptorSet;
        long describedTable;
        long describedRemap;
        long describedMask;
        long describedOut;
        long describedCounts;
        Pending pending;
    }

    private static final class Pending {
        long frameId;
        int passCount;
        int[][] canonicalPerPass;
        int[] countSlotPerPass;
        int[] outBasePerPass;
        int[] outCapacityPerPass;
    }

    private static final class PassCollection {
        final String name;
        final int ordinal;
        final GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        final List<Integer> canonical = new ArrayList<Integer>();
        int nonUniformBatches;

        PassCollection(String name, int ordinal) {
            this.name = name;
            this.ordinal = ordinal;
        }
    }

    private static final class FrameCollection {
        final long frameId;
        final float[] planes;
        final float cameraX;
        final float cameraY;
        final float cameraZ;
        final Map<String, PassCollection> passes = new LinkedHashMap<String, PassCollection>();

        FrameCollection(long frameId, float[] planes, float cameraX, float cameraY, float cameraZ) {
            this.frameId = frameId;
            this.planes = planes;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
        }
    }

    VulkanGpuTerrainCensus(VulkanContext context) {
        this.context = context;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            this.timestampPeriod = properties.limits().timestampPeriod();
            createPipeline(stack);
            VkQueryPoolCreateInfo queryInfo = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                    .queryType(VK_QUERY_TYPE_TIMESTAMP)
                    .queryCount(FrameSubmissionScheduler.FRAME_SLOTS * 2);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateQueryPool(context.device(), queryInfo, null, handle), "create census query pool");
            this.queryPool = handle.get(0);
        }
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        if (!this.gatePrinted) {
            this.gatePrinted = true;
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_CULLING_GATE enabled="
                    + GpuTerrainCulling.ENABLED + " census=" + GpuTerrainCulling.CENSUS
                    + " drawIndirectCount=" + context.drawIndirectCountEnabled()
                    + " maxDrawIndirectCount=" + context.maxDrawIndirectCount()
                    + " capabilityGate=" + GpuTerrainCulling.capabilityGate(
                            context.drawIndirectCountEnabled(), false)
                    + " mode=shadow-generation (E10-a: drawing unchanged)");
        }
    }

    private void createPipeline(MemoryStack stack) {
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
        for (int i = 0; i < 5; i++) {
            bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        }
        VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default().pBindings(bindings);
        check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "create census set layout");
        this.setLayout = handle.get(0);
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(this.setLayout)).pPushConstantRanges(range);
        check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "create census pipeline layout");
        this.pipelineLayout = handle.get(0);
        ByteBuffer code = SpirvShaders.load("terrain_cull.comp");
        long module = 0L;
        try {
            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            check(vkCreateShaderModule(device, shaderInfo, null, handle), "create census shader module");
            module = handle.get(0);
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().layout(this.pipelineLayout);
            create.get(0).stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                    .pName(stack.UTF8("main"));
            check(vkCreateComputePipelines(device, VK_NULL_HANDLE, create, null, handle),
                    "create census compute pipeline");
            this.pipeline = handle.get(0);
        } finally {
            if (module != 0L) {
                vkDestroyShaderModule(device, module, null);
            }
            MemoryUtil.memFree(code);
        }
    }

    boolean disabled() {
        return this.disabled;
    }

    private void disable(String what, RuntimeException error) {
        if (!this.disabled) {
            this.disabled = true;
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_CENSUS_DISABLED at=" + what
                    + " reason=" + error);
        }
    }

    /**
     * Collects one region's batch into the current frame's collection. Called from the
     * backend's region loop for unsorted (opaque / cutout) passes only; sorted passes keep
     * their order semantics and are outside E10's first shape (design section 2.5).
     * The collection's population is the chain-eligible population — batches whose index
     * source is not the uniform shared one are counted as gate fallbacks and excluded from
     * both sides of the comparison, matching the population E10-b could ever generate for.
     */
    void collect(String passName, long frameId, float[] viewProjection, double cameraX, double cameraY,
                 double cameraZ, org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion region,
                 int frameIndex, VulkanMultiDrawBatch batch) {
        if (this.disabled) {
            return;
        }
        try {
            if (this.collecting == null || this.collecting.frameId != frameId) {
                this.collecting = new FrameCollection(frameId,
                        GpuTerrainDrawTable.extractPlanes(viewProjection),
                        (float) cameraX, (float) cameraY, (float) cameraZ);
            }
            FrameCollection frame = this.collecting;
            PassCollection pass = frame.passes.get(passName);
            if (pass == null) {
                if (frame.passes.size() >= MAX_PASSES_PER_FRAME) {
                    return;
                }
                pass = new PassCollection(passName, frame.passes.size());
                frame.passes.put(passName, pass);
            }
            if (!batch.unsortedOffsetsClean()) {
                pass.nonUniformBatches++;
                return;
            }
            int dictIndex = pass.builder.dictIndexFor(region.getId(), frameIndex);
            int flags = (pass.ordinal << 8) | 0; // index source: shared quad buffer
            for (int i = 0, size = batch.size(); i < size; i++) {
                int elementCount = batch.elementCount(i);
                int baseVertex = batch.baseVertex(i);
                pass.builder.add(new GpuTerrainDrawTable.Entry(elementCount, 0, baseVertex, dictIndex,
                        region.getChunkX(), region.getChunkY(), region.getChunkZ(), flags));
                pass.canonical.add(elementCount);
                pass.canonical.add(1);
                pass.canonical.add(0);
                pass.canonical.add(baseVertex);
                pass.canonical.add(frameIndex);
            }
        } catch (RuntimeException error) {
            disable("collect", error);
        }
    }

    /**
     * Frame rollover, called at the head of a terrain pass recording when the frame has
     * changed: harvests the slot's older pending result, then dispatches the just-finished
     * frame's collection into this slot. Recording happens outside any render pass (the
     * caller is at a pass head, before its begin), which is what the compute dispatch and
     * the timestamp writes require.
     */
    void rollover(VkCommandBuffer commandBuffer, int slotIndex) {
        if (this.disabled) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Slot slot = this.slots[slotIndex];
            harvest(slot, slotIndex);
            FrameCollection finished = this.collecting;
            this.collecting = null;
            if (finished == null || finished.passes.isEmpty()) {
                return;
            }
            dispatch(stack, commandBuffer, slot, slotIndex, finished);
        } catch (RuntimeException error) {
            disable("rollover", error);
        }
    }

    /** Reads back and scores the slot's pending dispatch, if one is waiting. */
    private void harvest(Slot slot, int slotIndex) {
        Pending pending = slot.pending;
        if (pending == null) {
            return;
        }
        slot.pending = null;
        long gpuNanos = -1L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer values = stack.malloc(16);
            int result = vkGetQueryPoolResults(this.context.device(), this.queryPool,
                    slotIndex * 2, 2, values, 8L, VK_QUERY_RESULT_64_BIT);
            if (result == VK_SUCCESS) {
                gpuNanos = (long) ((values.getLong(8) - values.getLong(0)) * (double) this.timestampPeriod);
            }
        }
        long countsAddress = slot.counts.mappedAddress();
        long outAddress = slot.out.mappedAddress();
        for (int p = 0; p < pending.passCount; p++) {
            int gpuCount = MemoryUtil.memGetInt(countsAddress + (long) pending.countSlotPerPass[p] * 4L);
            int capacity = pending.outCapacityPerPass[p];
            if (gpuCount < 0) {
                gpuCount = 0;
            }
            if (gpuCount > capacity) {
                gpuCount = capacity;
            }
            int[] gpuCommands = new int[gpuCount * GpuTerrainDrawTable.COMMAND_INTS];
            for (int i = 0; i < gpuCommands.length; i++) {
                gpuCommands[i] = MemoryUtil.memGetInt(
                        outAddress + ((long) pending.outBasePerPass[p] * GpuTerrainDrawTable.COMMAND_INTS + i) * 4L);
            }
            GpuTerrainDrawTable.Diff diff =
                    GpuTerrainDrawTable.compareCommands(pending.canonicalPerPass[p], gpuCommands);
            this.totalCpuCommands += diff.cpuCount;
            this.totalGpuCommands += diff.gpuCount;
            this.totalMatched += diff.matched;
            this.totalCpuOnly += diff.cpuOnly;
            this.totalGpuOnly += diff.gpuOnly;
        }
        this.framesHarvested++;
        if (gpuNanos >= 0) {
            this.computeSamples++;
            this.computeNanosSum += gpuNanos;
            this.computeNanosMax = Math.max(this.computeNanosMax, gpuNanos);
        }
        if (this.framesHarvested % PRINT_EVERY_FRAMES == 0) {
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_CENSUS frames=" + this.framesHarvested
                    + " entries=" + this.totalEntries
                    + " cpuCommands=" + this.totalCpuCommands
                    + " gpuCommands=" + this.totalGpuCommands
                    + " matched=" + this.totalMatched
                    + " cpuOnly=" + this.totalCpuOnly
                    + " gpuOnly=" + this.totalGpuOnly
                    + " gateFallbacks=[indexSourceNotUniform=" + this.totalNonUniformBatches + "]"
                    + " computeGpuMicrosAvg=" + (this.computeSamples == 0 ? -1
                            : (this.computeNanosSum / this.computeSamples) / 1000)
                    + " computeGpuMicrosMax=" + (this.computeSamples == 0 ? -1 : this.computeNanosMax / 1000)
                    + " computeSamples=" + this.computeSamples);
        }
    }

    /** Uploads one finished frame's collections into the slot and records the dispatches. */
    private void dispatch(MemoryStack stack, VkCommandBuffer commandBuffer, Slot slot, int slotIndex,
                          FrameCollection finished) {
        List<PassCollection> passes = new ArrayList<PassCollection>(finished.passes.values());
        // Build all tables first; a frame with no entries anywhere dispatches nothing.
        List<GpuTerrainDrawTable.Table> tables = new ArrayList<GpuTerrainDrawTable.Table>();
        int totalEntries = 0;
        int totalRemap = 0;
        int totalMaskWords = 0;
        for (PassCollection pass : passes) {
            GpuTerrainDrawTable.Table table = pass.builder.build();
            tables.add(table);
            totalEntries += table.entries.length;
            totalRemap += table.remap.length;
            totalMaskWords += table.maskWords.length;
        }
        if (totalEntries == 0) {
            return;
        }
        ensureBuffers(slot, totalEntries, totalRemap, totalMaskWords);
        // Host writes: tables, remaps and masks are concatenated in pass order; counts zeroed.
        long tableAddress = slot.table.mappedAddress();
        long remapAddress = slot.remap.mappedAddress();
        long maskAddress = slot.mask.mappedAddress();
        int entryCursor = 0;
        int remapCursor = 0;
        int maskCursor = 0;
        int[] entryStart = new int[passes.size()];
        int[] remapBase = new int[passes.size()];
        int[] maskBase = new int[passes.size()];
        for (int p = 0; p < passes.size(); p++) {
            GpuTerrainDrawTable.Table table = tables.get(p);
            entryStart[p] = entryCursor;
            remapBase[p] = remapCursor;
            maskBase[p] = maskCursor;
            ByteBuffer view = MemoryUtil.memByteBuffer(tableAddress + (long) entryCursor
                    * GpuTerrainDrawTable.ENTRY_BYTES, Math.max(1, table.entriesBytes()));
            view.order(java.nio.ByteOrder.nativeOrder());
            table.packEntries(view, 0);
            for (int i = 0; i < table.remap.length; i++) {
                MemoryUtil.memPutInt(remapAddress + (long) (remapCursor + i) * 4L, table.remap[i]);
            }
            for (int i = 0; i < table.maskWords.length; i++) {
                MemoryUtil.memPutInt(maskAddress + (long) (maskCursor + i) * 4L, table.maskWords[i]);
            }
            entryCursor += table.entries.length;
            remapCursor += table.remap.length;
            maskCursor += table.maskWords.length;
            this.totalNonUniformBatches += passes.get(p).nonUniformBatches;
        }
        long countsAddress = slot.counts.mappedAddress();
        for (int i = 0; i < COUNT_SLOTS; i++) {
            MemoryUtil.memPutInt(countsAddress + (long) i * 4L, 0);
        }
        ensureDescriptors(stack, slot);

        VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_HOST_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, barrier, null, null);
        vkCmdResetQueryPool(commandBuffer, this.queryPool, slotIndex * 2, 2);
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, this.queryPool, slotIndex * 2);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0,
                stack.longs(slot.descriptorSet), null);
        Pending pending = new Pending();
        pending.frameId = finished.frameId;
        pending.passCount = passes.size();
        pending.canonicalPerPass = new int[passes.size()][];
        pending.countSlotPerPass = new int[passes.size()];
        pending.outBasePerPass = new int[passes.size()];
        pending.outCapacityPerPass = new int[passes.size()];
        int outCursor = 0;
        for (int p = 0; p < passes.size(); p++) {
            int count = tables.get(p).entries.length;
            pending.canonicalPerPass[p] = toIntArray(passes.get(p).canonical);
            pending.countSlotPerPass[p] = p;
            pending.outBasePerPass[p] = outCursor;
            pending.outCapacityPerPass[p] = count;
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            for (int i = 0; i < 24; i++) {
                push.putFloat(i * 4, finished.planes[i]);
            }
            push.putFloat(96, finished.cameraX);
            push.putFloat(100, finished.cameraY);
            push.putFloat(104, finished.cameraZ);
            push.putFloat(108, GpuTerrainCulling.MAX_DISTANCE_BLOCKS);
            push.putInt(112, entryStart[p]);
            push.putInt(116, count);
            push.putInt(120, p);
            push.putInt(124, remapBase[p]);
            push.putInt(128, outCursor);
            push.putInt(132, maskBase[p]);
            push.putInt(136, count);
            push.putInt(140, 0);
            push.clear();
            vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            vkCmdDispatch(commandBuffer, (count + WORKGROUP - 1) / WORKGROUP, 1, 1);
            outCursor += count;
        }
        vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, this.queryPool, slotIndex * 2 + 1);
        barrier.get(0).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_HOST_BIT, 0, barrier, null, null);
        slot.pending = pending;
        this.totalEntries += totalEntries;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i).intValue();
        }
        return out;
    }

    private void ensureBuffers(Slot slot, int entries, int remapCount, int maskWords) {
        int usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        if (slot.table == null) {
            slot.table = new VulkanHostBuffer(this.context, Math.max(1L, (long) entries
                    * GpuTerrainDrawTable.ENTRY_BYTES), usage);
            slot.remap = new VulkanHostBuffer(this.context, Math.max(4L, (long) remapCount * 4L), usage);
            slot.mask = new VulkanHostBuffer(this.context, Math.max(4L, (long) maskWords * 4L), usage);
            slot.out = new VulkanHostBuffer(this.context, Math.max(20L, (long) entries
                    * GpuTerrainDrawTable.COMMAND_BYTES), usage);
            slot.counts = new VulkanHostBuffer(this.context, COUNT_SLOTS * 4L, usage);
        } else {
            slot.table.ensureCapacity((long) entries * GpuTerrainDrawTable.ENTRY_BYTES);
            slot.remap.ensureCapacity(Math.max(4L, (long) remapCount * 4L));
            slot.mask.ensureCapacity(Math.max(4L, (long) maskWords * 4L));
            slot.out.ensureCapacity(Math.max(20L, (long) entries * GpuTerrainDrawTable.COMMAND_BYTES));
        }
    }

    /** (Re)writes the slot's descriptor set when any buffer handle has changed. */
    private void ensureDescriptors(MemoryStack stack, Slot slot) {
        if (slot.descriptorSet != 0L
                && slot.describedTable == slot.table.buffer()
                && slot.describedRemap == slot.remap.buffer()
                && slot.describedMask == slot.mask.buffer()
                && slot.describedOut == slot.out.buffer()
                && slot.describedCounts == slot.counts.buffer()) {
            return;
        }
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        if (slot.descriptorPool == 0L) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(5);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSize);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle), "create census descriptor pool");
            slot.descriptorPool = handle.get(0);
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(slot.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle), "allocate census descriptor set");
            slot.descriptorSet = handle.get(0);
        }
        VulkanHostBuffer[] buffers = {slot.table, slot.remap, slot.mask, slot.out, slot.counts};
        VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(5, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
        for (int i = 0; i < 5; i++) {
            infos.get(i).buffer(buffers[i].buffer()).offset(0).range(VK_WHOLE_SIZE);
            writes.get(i).sType$Default().dstSet(slot.descriptorSet).dstBinding(i).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(infos.slice(i, 1));
        }
        vkUpdateDescriptorSets(device, writes, null);
        slot.describedTable = slot.table.buffer();
        slot.describedRemap = slot.remap.buffer();
        slot.describedMask = slot.mask.buffer();
        slot.describedOut = slot.out.buffer();
        slot.describedCounts = slot.counts.buffer();
    }

    /** Destroys every census resource. Called from the backend's own teardown. */
    void delete() {
        VkDevice device = this.context.device();
        for (Slot slot : this.slots) {
            if (slot.descriptorPool != 0L) {
                vkDestroyDescriptorPool(device, slot.descriptorPool, null);
            }
            if (slot.table != null) slot.table.delete();
            if (slot.remap != null) slot.remap.delete();
            if (slot.mask != null) slot.mask.delete();
            if (slot.out != null) slot.out.delete();
            if (slot.counts != null) slot.counts.delete();
        }
        if (this.queryPool != 0L) {
            vkDestroyQueryPool(device, this.queryPool, null);
        }
        if (this.pipeline != 0L) {
            vkDestroyPipeline(device, this.pipeline, null);
        }
        if (this.pipelineLayout != 0L) {
            vkDestroyPipelineLayout(device, this.pipelineLayout, null);
        }
        if (this.setLayout != 0L) {
            vkDestroyDescriptorSetLayout(device, this.setLayout, null);
        }
    }

    private void check(int result, String what) {
        if (result == VK_ERROR_DEVICE_LOST) {
            throw this.context.celtium$reportDeviceLost(what, result);
        }
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult " + result);
        }
    }
}
