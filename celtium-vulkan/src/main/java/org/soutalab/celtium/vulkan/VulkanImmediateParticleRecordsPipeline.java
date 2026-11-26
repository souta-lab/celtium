package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
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

import static org.lwjgl.vulkan.VK10.*;

/**
 * The record-reading pipeline for Minecraft's particle geometry (Stage 5, S5-3): the particle family's
 * only pipeline form since the per-group particle pipeline was deleted in the same landing that
 * introduced this class.
 * <p>
 * The vertex layout is the particle family's own (position, texture, colour, lightmap - a different order
 * from BLOCK), and the state block a group would have pushed - the combined matrix, the texture affine and
 * the alpha reference - is read from the draw-record storage buffer at set 1, indexed by
 * {@code gl_InstanceIndex}, instead of push constants: the layout declares the per-texture pair set at
 * set 0 (texture at binding 0, light map at binding 1, the pair this class's descriptor cache below
 * writes) and the records set at set 1, and no push range at all. A group the batcher does not capture is
 * still drawn through this pipeline, as a one-record segment of its own (the backend's particle fallback
 * draw), so the family has no second drawing form to fall back to.
 * <p>
 * The descriptor machinery - the (texture, light map) pair cache, its eldest eviction into the
 * completion-gated free, the generation guard - is the deleted class's, moved unchanged: the pair a batch
 * samples did not change when the state block's vehicle did. The blend state is the batch's captured own
 * (BH-07), replicated once per colour attachment of the pass; depth test on, depth writes off and no
 * culling are the family's asserted constants, as they were.
 * <p>
 * Threading: render thread only, except that the backend's creation path may build an instance on the
 * prewarm compiler thread, exactly as it built the deleted class's instances.
 */
public final class VulkanImmediateParticleRecordsPipeline {

    /** Bounded census of the layout a descriptor is actually written with, for VUID-vkCmdDraw-None-09600. */
    private static int descLayoutLogs;

    /** Prints the layout of the descriptor write about to be issued, bounded, so the offending write names itself. */
    static void celtium$noteDescWrite(org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write) {
        if (descLayoutLogs >= 24 || write == null || write.remaining() == 0) {
            return;
        }
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer images = write.get(0).pImageInfo();
        int layout = images == null ? -1 : images.get(0).imageLayout();
        descLayoutLogs++;
        String name = layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL ? "GENERAL"
                : layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ? "SHADER_READ_ONLY"
                : layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ? "TRANSFER_DST"
                : Integer.toString(layout);
        System.out.println("[Celtium] CELTIUM_DESC_WRITE layout=" + name + " images="
                + (images == null ? 0 : images.remaining()));
    }

    /** The PARTICLE family: 3 floats position, 2 floats uv, 4 unsigned bytes colour, 2 unsigned shorts lightmap. */
    public static final int VERTEX_BYTES = 28;

    private final VulkanContext context;
    private final long renderPass;
    /**
     * How many colour attachments the pass this pipeline is built against attaches. The blend state is
     * replicated once per attachment when the pipeline is created - the mod-program multi-target form,
     * because GL has one blend state for all draw buffers and the per-buffer form is not captured. One
     * is every pass this family was built against before multi-target targets existed; a factory that
     * builds for such a target sets this before the build, the way the variant fields are set.
     */
    private int colorAttachments = 1;

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap setsByTextureView =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /** How many descriptor sets this pipeline has had to build; a rise means keys stopped matching. */
    public long setsCreated;

    /** Sets this pipeline has retired from its cache to make room; counted, never silently dropped. */
    private int descriptorSetsRetired;
    /** Sets this pipeline has handed back to the pool through the completion-gated free. */
    private int descriptorSetsFreed;
    /**
     * Retired sets with the pool each belongs to (this family's one pool), awaiting the completion-gated free.
     * <p>
     * This cache used to have no eviction at all, and the one pool's 8192 sets were consumed monotonically by
     * distinct (texture, light map) keys until every later particle batch was refused (audit-vulkan-core D2, the
     * particle family's shape of it - the last of the four immediate families to close, found by the interaction
     * audit after the other three landed). Now a cache miss evicts the eldest entries into this store, and a
     * full batch is handed to {@link VulkanContext#celtium$retireCleanup} - OPT-04's completion basis.
     * {@link #delete()} advances {@link #descriptorFreeGeneration} so a batch still queued when the pool is
     * destroyed drops itself instead of freeing into a dead pool.
     */
    private final RetiredDescriptorSets retiredSets = new RetiredDescriptorSets();
    /** Advanced by {@link #delete()}; a queued descriptor free that sees a newer value owns nothing any more. */
    private long descriptorFreeGeneration;
    /** How many retired sets one completion-gated free hands back at a time. */
    private static final int RETIRED_FREE_BATCH = 32;
    /**
     * How many live descriptor sets the cache keeps before evicting the eldest.
     * <p>
     * A quarter of the pool: retired sets awaiting completion still occupy pool slots, so the live cache plus
     * the in-flight retired backlog plus the reserved set must stay under the pool's 8192, and distinct
     * (texture, light map) pairs live in one frame sit far below this.
     */
    private static final int DESCRIPTOR_CACHE_LIMIT = 2048;
    /** Insertion order of the cached keys, so a miss can evict the eldest. */
    private final EvictionRing insertionOrder = new EvictionRing(DESCRIPTOR_CACHE_LIMIT * 2);

    private final VulkanColorBlendState colorBlend;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final int cull;
    /** The Vulkan compare op the mirrored {@code glDepthFunc} mapped to. */
    private final int depthCompareOp;

    private VulkanImmediateParticleRecordsPipeline(VulkanContext context, long renderPass,
                                                   VulkanColorBlendState colorBlend,
                                                   boolean depthTest, boolean depthWrite, int cull,
                                                   int depthCompareOp) {
        this.context = context;
        this.renderPass = renderPass;
        this.colorBlend = colorBlend;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.depthCompareOp = depthCompareOp;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediateParticleRecordsPipeline create(VulkanContext context, long renderPass,
                                                                long recordsSetLayout) {
        return create(context, renderPass, VulkanColorBlendState.legacy(), true, false, VulkanCullState.NONE,
                VK_COMPARE_OP_LESS_OR_EQUAL, recordsSetLayout);
    }

    /**
     * Creates a pipeline for one drawing state: the blend factors, blend equations and colour write mask the batch was
     * captured under (BH-07, BH-10), the depth test and its write mask, and face culling. These bake into the pipeline
     * object, which is why the immediate path caches one per state instead of forcing every batch through a single
     * hardcoded combination. {@code recordsSetLayout} is the draw-record store's set layout, declared at
     * set 1 beside this family's own pair layout at set 0; it must be a live handle, because a pipeline
     * built without it could never be bound with the records set the vertex stage reads.
     */
    public static VulkanImmediateParticleRecordsPipeline create(VulkanContext context, long renderPass,
                            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, int cull,
                            int depthCompareOp, long recordsSetLayout) {
        return create(context, renderPass, colorBlend, depthTest, depthWrite, cull, depthCompareOp,
                recordsSetLayout, 1);
    }

    /**
     * The multi-target form: built against a pass attaching {@code colorAttachments} colour targets,
     * with the blend state replicated for each.
     */
    public static VulkanImmediateParticleRecordsPipeline create(VulkanContext context, long renderPass,
                            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, int cull,
                            int depthCompareOp, long recordsSetLayout, int colorAttachments) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        if (colorBlend == null) {
            throw new IllegalArgumentException("colorBlend must be set");
        }
        if (recordsSetLayout == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("recordsSetLayout must be set");
        }
        VulkanImmediateParticleRecordsPipeline result = new VulkanImmediateParticleRecordsPipeline(context,
                renderPass, colorBlend, depthTest, depthWrite, cull, depthCompareOp);
        result.colorAttachments = Math.max(1, colorAttachments);
        // BH-20: roll back partial layout/pool handles if pipeline creation fails.
        try {
            result.createPipelineLayout(recordsSetLayout);
            result.createPipeline();
        } catch (RuntimeException | LinkageError error) {
            VulkanImmediateTextureRecordsPipeline.BUILD_ROLLBACKS.incrementAndGet();
            result.delete();
            throw error;
        }
        return result;
    }

    public int vertexBytes() {
        return VERTEX_BYTES;
    }

    /**
     * {@return the descriptor set that samples the given texture}, creating and writing it on first use.
     * <p>
     * Each texture gets its own set, written **before** it is ever bound, because updating a set that a recording
     * command buffer has already bound is invalid without {@code UPDATE_AFTER_BIND} - and the validation layer said so
     * precisely: "VkDescriptorSet was destroyed or updated without UPDATE_AFTER_BIND", after which every following
     * command in that buffer failed with "commandBuffer must be in the recording state". Returns 0 when the pool is
     * exhausted, so the caller refuses the batch instead of drawing with a stale binding.
     */
    public long descriptorSetFor(VulkanGlTexture texture, VulkanGlTexture lightmap) {
        if (lightmap == null) {
            return fail("lightmap object absent");
        }
        if (this.descriptorPool == VK_NULL_HANDLE) {
            return fail("descriptor pool handle is null");
        }
        if (texture == null) {
            return fail("texture object absent");
        }
        if (lightmap.view() == VK_NULL_HANDLE || lightmap.sampler() == VK_NULL_HANDLE) {
            return fail("lightmap has no view or sampler");
        }
        if (texture.view() == VK_NULL_HANDLE || texture.sampler() == VK_NULL_HANDLE) {
            return fail("texture has no view or sampler");
        }
        // The light map is part of the key this time (audit S13). The mirror is refreshed every few frames, so its
        // view and generation change while the atlas's do not - keyed on the texture alone, a set built for an old
        // light map would be served for a new one.
        long descriptorKey = (texture.key() * 31L) + (lightmap != null ? lightmap.key() : 0L);
        if (descriptorKey == this.lastDescriptorKey && this.lastDescriptorSet != 0L) {
            return this.lastDescriptorSet;
        }
        // 0 is "absent": a successfully allocated set is never 0.
        long existing = this.setsByTextureView.get(descriptorKey);
        if (existing != 0L) {
            this.lastDescriptorKey = descriptorKey;
            this.lastDescriptorSet = existing;
            return existing;
        }
        celtium$evictEldestDescriptorSets();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPool)
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer handle = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) != VK_SUCCESS) {
                return fail("vkAllocateDescriptorSets failed");
            }
            long set = handle.get(0);
            VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
            image.get(0).imageLayout(texture.samplingLayout())
                    .imageView(texture.view())
                    .sampler(texture.sampler());
            VkDescriptorImageInfo.Buffer lightImage = VkDescriptorImageInfo.calloc(1, stack);
            lightImage.get(0).imageLayout(lightmap.samplingLayout())
                    .imageView(lightmap.view())
                    .sampler(lightmap.sampler());
            org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write =
                    org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(2, stack);
            write.get(1).sType$Default()
                    .dstSet(set)
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(lightImage);
            write.get(0).sType$Default()
                    .dstSet(set)
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(image);
            celtium$noteDescWrite(write);
            VulkanDescriptorWrites.note(write, "VulkanImmediateParticleRecordsPipeline");
            vkUpdateDescriptorSets(this.context.device(), write, null);
            this.setsByTextureView.put(descriptorKey, set);
            celtium$noteCachedKey(descriptorKey);
            this.lastDescriptorKey = descriptorKey;
            this.lastDescriptorSet = set;
            this.setsCreated++;
            return set;
        } catch (RuntimeException | LinkageError error) {
            if (this.context.celtium$isDeviceLost()) {
                throw error;
            }
            return fail(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    /**
     * {@return whether {@code set} is still the descriptor set the cache holds for {@code key}}.
     * <p>
     * The textured family's query, asked of this family's own cache: a queued group's key can stand still while
     * its set dies - a run of misses on other textures evicts the entry, the set is retired, and the
     * completion-gated free hands it back to the pool once the submissions recorded so far complete, a gate that
     * does not count the group's still unrecorded use. The map is the authority: an evicted key is absent, and a
     * key that missed again since names the replacement set, never the retired one. This cache is keyed by the
     * pair key {@code (texture.key() * 31) + lightmap.key()}, so the query is asked with the pair key recomputed
     * from the wrappers current at the draw: a light map refresh since the group was prepared also answers
     * false, which is correct - that set samples the previous light map and has to be rebuilt too. The cache's
     * limit is 2048 rather than the textured family's 64, so eviction here takes a long session's accumulation
     * of distinct pairs; the structure the query guards is the same.
     */
    public boolean celtium$setStillCached(long set, long key) {
        return set != 0L && this.setsByTextureView.get(key) == set;
    }

    private long lastDescriptorKey = -1L;
    private long lastDescriptorSet;

    /** The reason the last zero was returned, so one run can name it instead of guessing between four paths. */
    private String lastFailure;

    public String lastFailure() {
        return this.lastFailure == null ? "none" : this.lastFailure;
    }

    private long fail(String reason) {
        this.lastFailure = reason;
        return 0L;
    }

    /** Records a freshly cached key at the tail of the eviction ring; a full ring leaves the key unevictable. */
    private void celtium$noteCachedKey(long key) {
        this.insertionOrder.note(key);
    }

    /**
     * Evicts the eldest cached sets until the cache is below its limit, retiring each against the pool.
     * <p>
     * A retired set is not freed here - the command buffers that recorded it may still be in flight, and OPT-04
     * makes GPU completion the only basis for releasing a resource - so it goes on the retired store, and a
     * full batch is handed to the completion-gated free. The slot cache naming an evicted set is dropped with
     * it: serving a set that is on its way back to its pool would be a use after free.
     */
    private void celtium$evictEldestDescriptorSets() {
        while (this.setsByTextureView.size() >= DESCRIPTOR_CACHE_LIMIT && this.insertionOrder.hasEldest()) {
            long eldest = this.insertionOrder.eldest();
            long removed = this.setsByTextureView.remove(eldest);
            if (removed != 0L) {
                this.descriptorSetsRetired++;
                if (removed == this.lastDescriptorSet) {
                    this.lastDescriptorKey = -1L;
                    this.lastDescriptorSet = 0L;
                }
                this.retiredSets.add(this.descriptorPool, removed);
                if (this.retiredSets.size() >= RETIRED_FREE_BATCH) {
                    celtium$handOffRetiredSets();
                }
            }
        }
    }

    /**
     * Hands the retired sets to the completion-gated cleanup that frees them back into the pool.
     * <p>
     * A retired set's last recorded use belongs to a submission at or before the sequence current now, and
     * {@link VulkanContext#celtium$retireCleanup} runs the free only once that sequence is confirmed complete -
     * the same basis {@link #delete()} destroys the pool on. If this pipeline is deleted first, the generation
     * check drops the batch: its sets died with the pool and there is nothing left to free.
     */
    private void celtium$handOffRetiredSets() {
        if (this.retiredSets.size() == 0) {
            return;
        }
        final java.util.Map<Long, long[]> byPool = this.retiredSets.drainGrouped();
        final long generation = this.descriptorFreeGeneration;
        final VulkanContext context = this.context;
        context.celtium$retireCleanup("particle.descriptorFree", () -> {
            if (generation != this.descriptorFreeGeneration) {
                return;
            }
            int freed = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                for (java.util.Map.Entry<Long, long[]> entry : byPool.entrySet()) {
                    int result = vkFreeDescriptorSets(context.device(), entry.getKey().longValue(),
                            stack.longs(entry.getValue()));
                    if (result == VK_SUCCESS) {
                        freed += entry.getValue().length;
                    }
                }
            }
            this.descriptorSetsFreed += freed;
        });
    }

    /** {@return the descriptor cache's live, retired and freed counts, in the textured family's census shape}. */
    public String descriptorSetCensus() {
        return "descriptorSetsLive=" + this.setsByTextureView.size()
                + " descriptorSetsRetired=" + this.descriptorSetsRetired
                + " descriptorSetsAwaitingCompletion=" + this.retiredSets.size()
                + " descriptorSetsFreed=" + this.descriptorSetsFreed;
    }

    /**
     * Insertion order of the cached descriptor keys: who is eldest, which is the whole of the eviction policy's
     * pure decision.
     * <p>
     * The block and item families keep this ring as inline arrays; it is a class here so the decision can be
     * pinned without a device, the same reason the retired-set store is one (audit D2 was fixed per family, and
     * each family carries its own copy of the shape). A full ring records nothing further: a key that was never
     * recorded is never evicted, and its set is the pool's to release at {@link #delete()}.
     */
    static final class EvictionRing {
        private final long[] keys;
        private int head;
        private int tail;
        private int count;

        EvictionRing(int capacity) {
            this.keys = new long[capacity];
        }

        /** Records a freshly cached key at the tail; a full ring leaves the key unevictable. */
        void note(long key) {
            if (this.count < this.keys.length) {
                this.keys[this.tail] = key;
                this.tail = (this.tail + 1) % this.keys.length;
                this.count++;
            }
        }

        boolean hasEldest() {
            return this.count > 0;
        }

        /** Removes and returns the eldest recorded key; the caller must have checked {@link #hasEldest()}. */
        long eldest() {
            long key = this.keys[this.head];
            this.head = (this.head + 1) % this.keys.length;
            this.count--;
            return key;
        }

        int size() {
            return this.count;
        }

        void clear() {
            this.head = 0;
            this.tail = 0;
            this.count = 0;
        }
    }

    public void delete() {
        long pipelineToDestroy = this.pipeline;
        long poolToDestroy = this.descriptorPool;
        long setLayoutToDestroy = this.descriptorSetLayout;
        long pipelineLayoutToDestroy = this.pipelineLayout;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSet = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.setsByTextureView.clear();
        // Any descriptor free still queued against this pool drops itself on this generation change: the sets
        // it names die with the pool destroyed below.
        this.descriptorFreeGeneration++;
        this.insertionOrder.clear();
        this.lastDescriptorKey = -1L;
        this.lastDescriptorSet = 0L;
        // Retired sets still awaiting their free are the pool's to release: the pool teardown below releases
        // them with every other set this pipeline allocated.
        this.retiredSets.clear();
        if (this.context.celtium$recordingOpen()) {
            this.context.celtium$retireCleanup("particle.delete", () -> {
                if (pipelineToDestroy != VK_NULL_HANDLE) {
                    this.context.celtium$notePipelineDestroy("immediateparticlerecordspipeline");
                    vkDestroyPipeline(this.context.device(), pipelineToDestroy, null);
                }
                if (poolToDestroy != VK_NULL_HANDLE) {
                    this.context.celtium$noteDescriptorDestroy("particle.delete.deferred");
                    vkDestroyDescriptorPool(this.context.device(), poolToDestroy, null);
                }
                if (setLayoutToDestroy != VK_NULL_HANDLE) {
                    vkDestroyDescriptorSetLayout(this.context.device(), setLayoutToDestroy, null);
                }
                if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
                    vkDestroyPipelineLayout(this.context.device(), pipelineLayoutToDestroy, null);
                }
            });
            return;
        }
        if (pipelineToDestroy != VK_NULL_HANDLE) {
            this.context.celtium$notePipelineDestroy("immediateparticlerecordspipeline");
            vkDestroyPipeline(this.context.device(), pipelineToDestroy, null);
        }
        if (poolToDestroy != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), poolToDestroy, null);
        }
        if (setLayoutToDestroy != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), setLayoutToDestroy, null);
        }
        if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), pipelineLayoutToDestroy, null);
        }
    }

    /** {@return how many colour attachments this pipeline's blend state was replicated for}. */
    public int celtium$colorAttachments() {
        return this.colorAttachments;
    }

    /** {@return the graphics pipeline handle, or 0 when creation rolled back}. */
    public long celtium$handle() {
        return this.pipeline;
    }

    /** {@return the pipeline layout: the pair set at 0, the records set at 1, no push range}. */
    public long celtium$pipelineLayout() {
        return this.pipelineLayout;
    }

    private void createPipelineLayout(long recordsSetLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer binding =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(2, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            binding.get(1).binding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                            .pBindings(binding);
            LongBuffer layoutHandle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, layoutHandle),
                    "vkCreateDescriptorSetLayout(immediate textured)");
            this.descriptorSetLayout = layoutHandle.get(0);
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSize =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)// The per-type count is what caps the pool: maxSets alone was raised and had no effect, because 128
                            // combined-image-sampler descriptors at two per set is 64 sets however large maxSets is. The two
                            // are now sized together.
                            .descriptorCount(8192 * 2);
            // One set per distinct texture the frame binds; the pool refuses beyond that and the caller then refuses
            // the batch rather than drawing with the wrong image.
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            // Sized for a session rather than for a scene: the set is cached per texture view and nothing
                            // evicted it, so 64 was reached in ordinary play and every later allocation failed - which is why
                            // the particle family, with the most distinct views, was taken and then dropped. The cache now
                            // evicts its eldest entries (DESCRIPTOR_CACHE_LIMIT) and the completion-gated free hands the
                            // retired sets back, so the pool's size is headroom rather than a lifetime bound. FREE is what
                            // lets a retired set return individually; without it a pool only ever fills.
                            .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                            .maxSets(8192).pPoolSizes(poolSize);
            LongBuffer poolHandle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(this.context.device(), poolInfo, null, poolHandle),
                    "vkCreateDescriptorPool(immediate textured)");
            this.descriptorPool = poolHandle.get(0);
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPool)
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer setHandle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(this.context.device(), allocate, setHandle),
                    "vkAllocateDescriptorSets(immediate textured)");
            this.descriptorSet = setHandle.get(0);
            // No push range: the state block rides the record at set 1, so the layout is the pair layout
            // this class owns at set 0 and the record store's layout at set 1, in that order.
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.descriptorSetLayout, recordsSetLayout));
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(this.context.device(), create, null, handle),
                    "vkCreatePipelineLayout(immediate)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_PARTICLE_RECORDS_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_PARTICLE_RECORDS_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                LongBuffer handle = stack.mallocLong(1);
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate vertex)");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate fragment)");
                fragmentModule = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(VERTEX_BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(4, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(20);
                attributes.get(3).location(3).binding(0).format(VK_FORMAT_R16G16_UINT).offset(24);
                VkPipelineVertexInputStateCreateInfo vertexInput =
                        VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default()
                                .pVertexBindingDescriptions(binding)
                                .pVertexAttributeDescriptions(attributes);

                VkPipelineInputAssemblyStateCreateInfo assembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                VkPipelineViewportStateCreateInfo viewport =
                        VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                                .viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                // BH-11: the depth bias is command state (vkCmdSetDepthBias) so one pipeline serves every
                                // polygon offset the game sets; the enable bit is what has to be declared here, and it is a
                                // rasterization field - the multisample state next door has no such member.
                                .depthBiasEnable(true)
                                // The enable, the face and the winding are one encoded state (VulkanCullState): a
                                // mod's glCullFace(GL_FRONT) or glFrontFace(GL_CW) selects its own pipeline here
                                // instead of being drawn with GL's default pair.
                                .cullMode(VulkanCullState.vkCullMode(this.cull))
                                .frontFace(VulkanCullState.vkFrontFace(this.cull))
                                .lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                // Depth test on so the geometry participates in the world, writes off because Minecraft's own
                // immediate path for this state does the same, and blending on so the alpha it carries is honoured.
                // Depth test back on with the comparison Minecraft's own immediate path uses (depth writes stay off,
                // which is what the state the fixture sets asks for). The previous round measured the quad appearing
                // with the test disabled; this run answers whether the comparison itself is what hides it.
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(this.depthTest)
                                .depthWriteEnable(this.depthWrite)
                                .depthCompareOp(this.depthCompareOp);
                // One blend state per colour attachment of the pass this pipeline is built against: GL
                // has one blend state for all draw buffers unless a mod sets the per-buffer form, which
                // the capture does not hold, so the batch's state is replicated - the same form
                // VulkanModProgramPipeline's multi-target build uses. A one-colour pass keeps exactly
                // the single state this always built.
                int blendCount = Math.max(1, this.colorAttachments);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(blendCount, stack);
                for (int i = 0; i < blendCount; i++) {
                    blendAttachment.get(i)
                            .blendEnable(this.colorBlend.blend())
                            .srcColorBlendFactor(this.colorBlend.srcColor())
                            .dstColorBlendFactor(this.colorBlend.dstColor())
                            .colorBlendOp(this.colorBlend.colorOp())
                            .srcAlphaBlendFactor(this.colorBlend.srcAlpha())
                            .dstAlphaBlendFactor(this.colorBlend.dstAlpha())
                            .alphaBlendOp(this.colorBlend.alphaOp())
                            .colorWriteMask(this.colorBlend.writeMask());
                }
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_DEPTH_BIAS));

                VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                create.get(0).sType$Default()
                        .pStages(stages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(assembly)
                        .pViewportState(viewport)
                        .pRasterizationState(raster)
                        .pMultisampleState(multisample)
                        .pDepthStencilState(depth)
                        .pColorBlendState(blend)
                        .pDynamicState(dynamic)
                        .layout(this.pipelineLayout)
                        .renderPass(this.renderPass)
                        .subpass(0);
                LongBuffer pipelineHandle = stack.mallocLong(1);
                check(VulkanContext.createGraphicsPipelines(this.context.device(), create, pipelineHandle, "immediate-particle-records"),
                        "vkCreateGraphicsPipelines(immediate)");
                this.pipeline = pipelineHandle.get(0);
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
