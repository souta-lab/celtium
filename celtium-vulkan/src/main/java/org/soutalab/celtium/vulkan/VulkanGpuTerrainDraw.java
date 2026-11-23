package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * E10-b's production executor (item 723, design {@code e10-design.md} sections 3-5): the
 * drawing switch on top of E10-a's chassis. While a target pass records on the current CPU
 * path, this class collects the pass' draw population exactly as the census does; at the
 * next frame's pass head for that same pass — outside the render pass, where the design's
 * section 4.3 recording order puts the dispatch — the finished collection is finalized and
 * the frame about to record is pre-enumerated by the backend. Only when the pre-enumeration
 * reproduces the collection exactly (same regions, same order, same cached batch objects)
 * does the executor rewrite the remap with this frame's binding-5 indices, upload the
 * table, and record the generation dispatch; the pass' region loop then records no
 * per-batch draws, and one {@code vkCmdDrawIndexedIndirectCount} at the loop's end draws
 * the generated list. Any doubt — no collection, a capability gate, a tainted or
 * non-reproducing population — leaves the pass on the CPU path, with the reason counted
 * below rather than silent (design section 5.2).
 * <p>
 * One instance serves one pass, and the backend holds one instance per target pass. The
 * target set is the design's first shape (sections 2.5 and 3.5): SOLID plus the cutout
 * family ({@code cutout}, {@code cutout_mipped}). A cutout pass differs from SOLID only in
 * its fragment stage — alpha-tested discard is a property of the pass' own pipeline, which
 * the executor never binds or modifies — while everything generation depends on is the
 * same: the batches are unsorted with the uniform shared index source (a command is
 * {@code elementCount} indices of the shared quad pattern at the batch's
 * {@code baseVertex}), and the draws are order-independent, because the pass writes depth
 * and does not blend, so the compute shader's atomic append order cannot change the
 * picture. Per-pass instances are not merely organisational: two passes of one frame can
 * record into the same frame slot, and a slot's buffers are host-written at the pass head,
 * so two armed passes sharing one slot set would overwrite each other's table before the
 * earlier dispatch has executed.
 * <p>
 * Sorted passes stay out, for reasons that are structural rather than cautious (recorded
 * in full in {@code alpha-e10-cutout.md}): generation appends through an atomic counter
 * and preserves no order, while a sorted pass' recording order is blending semantics —
 * including the reverse iteration of the translucent pass when face sorting is off;
 * sorted batches read per-region index arenas at per-command firstIndex values instead of
 * the one shared index buffer an indirect draw binds, so a single generated draw cannot
 * reproduce them; and the population model itself assumes the unsorted batch form (a
 * sorted batch is tainted by construction here).
 * <p>
 * Why the reproduction must be exact: the generated commands carry the collection frame's
 * command decomposition (index counts and arena vertex offsets) and the current frame's
 * region indices (through the remap). A region whose batch was rebuilt, deferred or first
 * built since the collection would be drawn from stale commands over live arena ranges,
 * so the backend's pre-enumeration declares any such region unpredictable and the whole
 * pass falls back; the collection the fallback pass records becomes the next candidate.
 * The one-frame staleness of the visible set itself is the design's accepted S-type
 * difference (section 2.4), narrowed here to frames whose population did not change at
 * all — in a steady scene that is most frames, and in a churning scene the path simply
 * stays on the CPU route it falls back to.
 * <p>
 * Deviations from the design's full form, inherited from the E10-a chassis and recorded
 * in the E10-a record: entries are region-granular (a batch command carries no section
 * identity), the occlusion mask is the collection-local all-ones mask (the culler-fed
 * lattice mask is a later form), the table is re-uploaded per armed frame instead of
 * living device-side at batch-build time, and the generated-command buffer is host-visible
 * (with {@code INDIRECT_BUFFER} usage, as section 3.5 requires of the buffer itself)
 * rather than device-local. The validity rule is also stricter than the loop's own: a
 * stored batch the loop would draw deferred is treated as unpredictable here.
 * <p>
 * Nothing here is created unless the main E10 flag is on, and a failure anywhere disables
 * the executor with one printed line rather than touching the frame. The census
 * ({@link VulkanGpuTerrainCensus}) is independent of this class and unchanged.
 */
final class VulkanGpuTerrainDraw {

    private static final int WORKGROUP = 256;
    private static final int PUSH_BYTES = 144;
    private static final int PRINT_EVERY_PASS_HEADS = 60;

    private final VulkanContext context;
    /** This instance's pass ordinal, written into its table entries' flags (bits 8..15). */
    private final int passOrdinal;
    /** This instance's pass name, carried by the printed lines so passes are told apart. */
    private final String passName;
    private long setLayout;
    private long pipelineLayout;
    private long pipeline;
    private boolean disabled;
    private boolean gatePrinted;

    private final Slot[] slots = new Slot[FrameSubmissionScheduler.FRAME_SLOTS];

    /** The frame currently being collected, and the last finalized one awaiting its arm test. */
    private FrameCollection collecting;
    private BuiltFrame ready;

    // Armed state for the pass currently recording (set at the pass head, consumed at its end).
    private boolean armed;
    private int armedSlot;
    private int armedVisitCount;
    private int armedCommandCount;

    // Fallback and arm counters, printed every PRINT_EVERY_PASS_HEADS pass heads.
    private long passHeads;
    private long framesArmed;
    private long armedCommands;
    private long fallbackNoReady;
    private long fallbackTainted;
    private long fallbackGate;
    private long fallbackPopulation;

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
    }

    /** One frame's collected population, in the region loop's visit order. */
    private static final class FrameCollection {
        final long frameId;
        final float[] planes;
        final float cameraX;
        final float cameraY;
        final float cameraZ;
        final GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        final List<Integer> regionOrder = new ArrayList<Integer>();
        final List<VulkanMultiDrawBatch> batchOrder = new ArrayList<VulkanMultiDrawBatch>();
        /** Dictionary index per visit, or -1 for a visit whose batch generation cannot serve. */
        final List<Integer> dictOrder = new ArrayList<Integer>();
        /** A visited batch did not have the uniform shared index source generation requires. */
        boolean tainted;
        int commandCount;

        FrameCollection(long frameId, float[] planes, float cameraX, float cameraY, float cameraZ) {
            this.frameId = frameId;
            this.planes = planes;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
        }
    }

    /** A finalized collection: the built table plus the visit sequence it was collected in. */
    private static final class BuiltFrame {
        final GpuTerrainDrawTable.Table table;
        final int[] regionIds;
        final VulkanMultiDrawBatch[] batches;
        final int[] dictOrder;
        final boolean tainted;
        final int commandCount;

        BuiltFrame(GpuTerrainDrawTable.Table table, int[] regionIds, VulkanMultiDrawBatch[] batches,
                   int[] dictOrder, boolean tainted, int commandCount) {
            this.table = table;
            this.regionIds = regionIds;
            this.batches = batches;
            this.dictOrder = dictOrder;
            this.tainted = tainted;
            this.commandCount = commandCount;
        }
    }

    /** One region the backend's pre-enumeration resolved for the coming pass, in visit order. */
    public static final class Visit {
        public final int regionId;
        public final VulkanMultiDrawBatch batch;

        public Visit(int regionId, VulkanMultiDrawBatch batch) {
            this.regionId = regionId;
            this.batch = batch;
        }
    }

    VulkanGpuTerrainDraw(VulkanContext context, int passOrdinal, String passName) {
        this.context = context;
        this.passOrdinal = passOrdinal;
        this.passName = passName;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createPipeline(stack);
        }
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        if (!this.gatePrinted) {
            this.gatePrinted = true;
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_DRAW created pass=" + this.passName
                    + " drawIndirectCount="
                    + context.drawIndirectCountEnabled()
                    + " maxDrawIndirectCount=" + context.maxDrawIndirectCount()
                    + " mode=gpu-draw (E10-b: per-pass executor, previous-frame generation)");
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
        check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "create draw set layout");
        this.setLayout = handle.get(0);
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(this.setLayout)).pPushConstantRanges(range);
        check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "create draw pipeline layout");
        this.pipelineLayout = handle.get(0);
        ByteBuffer code = SpirvShaders.load("terrain_cull.comp");
        long module = 0L;
        try {
            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            check(vkCreateShaderModule(device, shaderInfo, null, handle), "create draw shader module");
            module = handle.get(0);
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().layout(this.pipelineLayout);
            create.get(0).stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                    .pName(stack.UTF8("main"));
            check(vkCreateComputePipelines(device, VK_NULL_HANDLE, create, null, handle),
                    "create draw compute pipeline");
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
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_DRAW_DISABLED pass=" + this.passName
                    + " at=" + what
                    + " reason=" + error);
        }
    }

    /**
     * Collects one region's batch into the current frame's collection. Called from the
     * backend's region loop for this instance's pass only, with the same population the census
     * collects: batches without the uniform shared index source taint the collection (the
     * frame can never be generated for), rather than being silently dropped from it — the
     * visit sequence must stay exact for the reproduction test at the next pass head.
     */
    void collect(long frameId, float[] viewProjection, double cameraX, double cameraY, double cameraZ,
                 RenderRegion region, int frameIndex, VulkanMultiDrawBatch batch) {
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
            frame.regionOrder.add(region.getId());
            frame.batchOrder.add(batch);
            if (!batch.unsortedOffsetsClean()) {
                frame.tainted = true;
                frame.dictOrder.add(-1);
                return;
            }
            int dictIndex = frame.builder.dictIndexFor(region.getId(), frameIndex);
            frame.dictOrder.add(dictIndex);
            // Pass ordinal in flags bits 8..15 (see GpuTerrainDrawTable.Entry.passOrdinal);
            // the index-source bit stays 0: the shared quad buffer, as every unsorted
            // target pass' batches are built. SOLID's ordinal is 0, so its entries are
            // bit-identical to the single-pass executor's.
            int flags = this.passOrdinal << 8;
            for (int i = 0, size = batch.size(); i < size; i++) {
                frame.builder.add(new GpuTerrainDrawTable.Entry(batch.elementCount(i), 0,
                        batch.baseVertex(i), dictIndex,
                        region.getChunkX(), region.getChunkY(), region.getChunkZ(), flags));
            }
            frame.commandCount += batch.size();
        } catch (RuntimeException error) {
            disable("collect", error);
        }
    }

    /**
     * This instance's pass head, called by the backend after the pack route has refused the
     * pass and before its render pass opens: finalizes the previous frame's collection, tests
     * this frame's pre-enumerated population against it, and — only on an exact
     * reproduction — rewrites the remap with this frame's binding-5 indices, uploads the
     * table into the frame slot and records the generation dispatch into the pass' command
     * buffer (fill, barrier, dispatch, barrier, in the design's section 4.3 order).
     *
     * @param visits            the regions the region loop will draw, in visit order, or
     *                          {@code null} when any region's drawn batch is unpredictable
     * @param regionOffsetCursor the binding-5 index the loop's first region will take;
     *                          visit position {@code p} takes {@code regionOffsetCursor + p}
     * @return whether the pass' draws are GPU-generated this frame
     */
    boolean passHead(VkCommandBuffer commandBuffer, int slotIndex, List<Visit> visits,
                     int regionOffsetCursor, float[] viewProjection, double cameraX, double cameraY,
                     double cameraZ, boolean budgetActive) {
        if (this.disabled) {
            return false;
        }
        this.armed = false;
        try {
            if (this.collecting != null) {
                this.ready = buildFrame(this.collecting);
                this.collecting = null;
            }
            this.passHeads++;
            boolean armedNow = false;
            if (this.ready == null) {
                this.fallbackNoReady++;
            } else if (this.ready.tainted) {
                this.fallbackTainted++;
                this.ready = null;
            } else if (GpuTerrainCulling.capabilityGate(
                    this.context.drawIndirectCountEnabled(), budgetActive)
                    != GpuTerrainCulling.GateReason.NONE) {
                // A configuration-level gate (no drawIndirectCount, or a frame budget cap
                // is in force): the collection is kept, as the gate is a property of the
                // configuration rather than of this frame's population.
                this.fallbackGate++;
            } else if (GpuTerrainDrawTable.frameGate(this.ready.table,
                    this.context.maxDrawIndirectCount(), this.ready.table.maskGeneration)
                    != GpuTerrainCulling.GateReason.NONE) {
                this.fallbackGate++;
                this.ready = null;
            } else if (visits == null || !reproduces(visits)) {
                this.fallbackPopulation++;
                this.ready = null;
            } else {
                // The current frame's remap (design section 3.3): dictionary index -> the
                // binding-5 index this frame's loop assigns the region, -1 where the
                // dictionary holds a region this frame does not visit (unreachable after
                // an exact reproduction, kept for the shader's own test).
                int[] remap = new int[this.ready.table.remap.length];
                Arrays.fill(remap, -1);
                for (int p = 0; p < visits.size(); p++) {
                    int dict = this.ready.dictOrder[p];
                    if (dict >= 0) {
                        remap[dict] = regionOffsetCursor + p;
                    }
                }
                Slot slot = this.slots[slotIndex];
                dispatch(commandBuffer, slot, this.ready, remap,
                        GpuTerrainDrawTable.extractPlanes(viewProjection),
                        (float) cameraX, (float) cameraY, (float) cameraZ);
                this.armed = true;
                this.armedSlot = slotIndex;
                this.armedVisitCount = visits.size();
                this.armedCommandCount = this.ready.commandCount;
                this.framesArmed++;
                this.armedCommands += this.ready.commandCount;
                armedNow = true;
            }
            if (this.passHeads % PRINT_EVERY_PASS_HEADS == 0) {
                System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_DRAW pass=" + this.passName
                        + " passHeads=" + this.passHeads
                        + " armed=" + this.framesArmed
                        + " armedCommands=" + this.armedCommands
                        + " fallbacks=[noReady=" + this.fallbackNoReady
                        + " tainted=" + this.fallbackTainted
                        + " gate=" + this.fallbackGate
                        + " population=" + this.fallbackPopulation + "]");
            }
            return armedNow;
        } catch (RuntimeException error) {
            disable("passHead", error);
            return false;
        }
    }

    /** Whether the pre-enumerated visits are the collected population, element for element. */
    private boolean reproduces(List<Visit> visits) {
        if (visits.size() != this.ready.regionIds.length) {
            return false;
        }
        for (int i = 0; i < visits.size(); i++) {
            Visit visit = visits.get(i);
            if (visit.regionId != this.ready.regionIds[i] || visit.batch != this.ready.batches[i]) {
                return false;
            }
        }
        return true;
    }

    private static BuiltFrame buildFrame(FrameCollection frame) {
        int count = frame.regionOrder.size();
        int[] regionIds = new int[count];
        VulkanMultiDrawBatch[] batches = new VulkanMultiDrawBatch[count];
        int[] dictOrder = new int[count];
        for (int i = 0; i < count; i++) {
            regionIds[i] = frame.regionOrder.get(i).intValue();
            batches[i] = frame.batchOrder.get(i);
            dictOrder[i] = frame.dictOrder.get(i).intValue();
        }
        return new BuiltFrame(frame.builder.build(), regionIds, batches, dictOrder,
                frame.tainted, frame.commandCount);
    }

    /**
     * Records the pass' single generated draw at the region loop's end, inside the render
     * pass, against the slot the pass head dispatched into. The caller has bound the shared
     * index buffer (the generated commands' only index source). The visit count the loop
     * actually recorded is checked against the pass head's prediction: the prediction is
     * exact by construction (the pre-enumeration resolves the same state the loop reads,
     * on the same thread, with nothing interleaved), so a mismatch means the construction
     * broke — this pass was under-drawn, and the executor disables itself rather than
     * risk a second frame.
     */
    void recordDraw(VkCommandBuffer commandBuffer, int visitedCount) {
        if (!this.armed) {
            return;
        }
        this.armed = false;
        if (visitedCount != this.armedVisitCount) {
            System.out.println("[Celtium] CELTIUM_GPU_TERRAIN_DRAW_MISMATCH pass=" + this.passName
                    + " visited=" + visitedCount
                    + " expected=" + this.armedVisitCount + " (executor disabled)");
            this.disabled = true;
            return;
        }
        Slot slot = this.slots[this.armedSlot];
        VK12.vkCmdDrawIndexedIndirectCount(commandBuffer, slot.out.buffer(), 0L,
                slot.counts.buffer(), 0L, this.armedCommandCount, GpuTerrainDrawTable.COMMAND_BYTES);
    }

    /** Uploads one finalized frame into the slot and records its generation dispatch. */
    private void dispatch(VkCommandBuffer commandBuffer, Slot slot, BuiltFrame frame, int[] remap,
                          float[] planes, float cameraX, float cameraY, float cameraZ) {
        GpuTerrainDrawTable.Table table = frame.table;
        int entries = table.entries.length;
        ensureBuffers(slot, entries, remap.length, table.maskWords.length);
        long tableAddress = slot.table.mappedAddress();
        ByteBuffer view = MemoryUtil.memByteBuffer(tableAddress, Math.max(1, table.entriesBytes()));
        view.order(ByteOrder.nativeOrder());
        table.packEntries(view, 0);
        long remapAddress = slot.remap.mappedAddress();
        for (int i = 0; i < remap.length; i++) {
            MemoryUtil.memPutInt(remapAddress + (long) i * 4L, remap[i]);
        }
        long maskAddress = slot.mask.mappedAddress();
        for (int i = 0; i < table.maskWords.length; i++) {
            MemoryUtil.memPutInt(maskAddress + (long) i * 4L, table.maskWords[i]);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ensureDescriptors(stack, slot);
            // The count slot is zeroed by fill, in the command buffer (design section 4.3
            // step 1), so the zeroing is ordered against the dispatch by the barrier below
            // rather than by host-write visibility alone.
            vkCmdFillBuffer(commandBuffer, slot.counts.buffer(), 0L, 4L, 0);
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_HOST_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, barrier, null, null);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0,
                    stack.longs(slot.descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            for (int i = 0; i < 24; i++) {
                push.putFloat(i * 4, planes[i]);
            }
            push.putFloat(96, cameraX);
            push.putFloat(100, cameraY);
            push.putFloat(104, cameraZ);
            push.putFloat(108, GpuTerrainCulling.MAX_DISTANCE_BLOCKS);
            push.putInt(112, 0);
            push.putInt(116, entries);
            push.putInt(120, 0);
            push.putInt(124, 0);
            push.putInt(128, 0);
            push.putInt(132, 0);
            push.putInt(136, entries);
            push.putInt(140, 0);
            push.clear();
            vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            vkCmdDispatch(commandBuffer, (entries + WORKGROUP - 1) / WORKGROUP, 1, 1);
            barrier.get(0).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, barrier, null, null);
        }
    }

    private void ensureBuffers(Slot slot, int entries, int remapCount, int maskWords) {
        int storage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        if (slot.table == null) {
            slot.table = new VulkanHostBuffer(this.context, Math.max(1L, (long) entries
                    * GpuTerrainDrawTable.ENTRY_BYTES), storage);
            slot.remap = new VulkanHostBuffer(this.context, Math.max(4L, (long) remapCount * 4L), storage);
            slot.mask = new VulkanHostBuffer(this.context, Math.max(4L, (long) maskWords * 4L), storage);
            // The generated list is drawn from as well as written: INDIRECT_BUFFER usage is
            // the design's section 3.5 requirement for this buffer.
            slot.out = new VulkanHostBuffer(this.context, Math.max(20L, (long) entries
                    * GpuTerrainDrawTable.COMMAND_BYTES), storage | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            slot.counts = new VulkanHostBuffer(this.context, 4L, storage | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
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
            check(vkCreateDescriptorPool(device, poolInfo, null, handle), "create draw descriptor pool");
            slot.descriptorPool = handle.get(0);
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(slot.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle), "allocate draw descriptor set");
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

    /** Destroys every executor resource. Called from the backend's own teardown. */
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
