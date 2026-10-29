package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;

import java.nio.LongBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

/**
 * The framebuffers the backend's passes draw through, and their lifetimes.
 * <p>
 * Three kinds, all keyed by the render pass each is compatible with: the frame target's, the default off-target
 * pair's, and each framebuffer identity's chain pair's. The pair handles are recorded in {@link RenderTargetRegistry},
 * which the draw path also reads; the frame target's are held here.
 * <p>
 * Two release rules, and the reason they differ. A pair whose images were replaced while the session runs is asked
 * for while a frame records, and that open command buffer can already name the old handles, which no settle can wait
 * for because it has not been submitted: those handles are retired ({@link VulkanRetireQueue}). Destroying them in
 * place invalidated the frame (E2E-E leg e2ee-amd10). Teardown, {@link #destroyAll()}, runs only after the caller
 * settled the GPU and nothing more will be recorded against the old target, so it frees in place.
 */
final class FramebufferCache {
    private final VulkanContext context;
    private final RenderTargetRegistry registry;
    /**
     * The frame target's framebuffers keyed by the render pass each is compatible with. One entry per render pass,
     * not one framebuffer total: the terrain and immediate pipelines build their own render passes, and a
     * single-slot cache replaced the entry on every pass alternation - each replacement paying a device-idle wait
     * to retire the old handle. Measured ~5 settleGpu calls per frame on leg ind2-amd.
     */
    private final Map<Long, Long> frameFramebuffers = new HashMap<Long, Long>();
    /** Per identity, the chain-pair generation its cached framebuffers were built over. */
    private final Map<Long, Long> chainPairGenerations = new HashMap<Long, Long>();

    FramebufferCache(VulkanContext context, RenderTargetRegistry registry) {
        this.context = context;
        this.registry = registry;
    }

    /**
     * The frame target's framebuffer for a render pass. Created here rather than in the frame target because
     * compatibility is defined by the render pass the pipeline built, and one framebuffer serves every pass that is
     * compatible with it. Creating one needs no settle: nothing is destroyed, the map keeps the older passes' handles
     * alive beside the new one.
     */
    long frame(long renderPass, long colorView, long depthView, int width, int height) {
        Long cached = this.frameFramebuffers.get(renderPass);
        if (cached != null) {
            return cached.longValue();
        }
        long handle = create(renderPass, colorView, depthView, width, height, null);
        this.frameFramebuffers.put(renderPass, handle);
        return handle;
    }

    /**
     * The framebuffer for a pass that draws into the target's own off-target images rather than the frame's.
     * <p>
     * The game's framebuffers are not the frame image: the entity outline draws its silhouette into
     * {@code entityOutlineFramebuffer} before the chain that turns it into a glow, and drawing that into the frame
     * image instead paints the silhouette flat onto the scene with the scene's depth buffer. With no OpenGL context
     * there is no such framebuffer to bind, so the renderer keeps one image of its own for these draws.
     */
    long offTarget(long renderPass, GllessFrameTarget glless, int width, int height) {
        if (!glless.offTargetMatches(width, height)) {
            // The images every cached framebuffer names were replaced with the target: all handles are stale
            // regardless of their render-pass key, so the whole slot is rebuilt.
            retire(this.registry.pairFramebufferHandles(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR));
            this.registry.clearPairFramebuffer(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR);
        }
        long cached = this.registry.pairFramebuffer(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR, renderPass);
        if (cached != 0L) {
            return cached;
        }
        long handle = create(renderPass, glless.offTargetColorView(), glless.offTargetDepthView(), width, height,
                "off-target");
        this.registry.notePairFramebuffer(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR, handle, renderPass);
        // counted by notePairFramebuffer
        return handle;
    }

    /**
     * The framebuffer for a pass that draws into the current identity's chain pair: the game's own framebuffers'
     * content, the entity-outline silhouette being the measured case. Separate from the default pair's because the
     * attachments differ; otherwise the same cache, keyed by the render pass it is compatible with.
     */
    long chainPair(long renderPass, GllessFrameTarget glless, int windowWidth, int windowHeight) {
        long identity = this.registry.currentIdentity();
        long generation = glless.chainPairGeneration(identity);
        Long builtOver = this.chainPairGenerations.get(Long.valueOf(identity));
        if (builtOver == null || builtOver.longValue() != generation) {
            // Same rule as the default pair: replaced images mean the views every cached handle named are dead. Asked
            // of the pair's own images, not of the window's size - a pair at its framebuffer's size (Xaero's minimap)
            // never matched the window, and every pass into it settled the GPU to rebuild an identical framebuffer
            // (2,940 creations in a 65 s Intel run, vkDeviceWaitIdle 80% of the client thread's native samples).
            Collection<Long> stale = this.registry.chainPairFramebufferHandles(identity);
            if (!stale.isEmpty()) {
                retire(stale);
                this.registry.clearChainPairFramebuffer(identity);
                this.chainPairGenerations.remove(Long.valueOf(identity));
            }
        }
        long cached = this.registry.chainPairFramebuffer(identity, renderPass);
        if (cached != 0L) {
            return cached;
        }
        // The framebuffer's declared extent must cover the attachments': a chain pair renders at its own size
        // (the fixture's 256x256 secondary measured this - declaring the window's 1280x720 was
        // VUID-VkFramebufferCreateInfo-flags-04533/04534 on every such pair).
        long[] pairExtent = glless.chainPairExtent(identity);
        int pairWidth = pairExtent == null ? windowWidth : (int) pairExtent[0];
        int pairHeight = pairExtent == null ? windowHeight : (int) pairExtent[1];
        long handle = create(renderPass, glless.chainPairColorView(identity), glless.chainPairDepthView(identity),
                pairWidth, pairHeight, "chain-pair:" + Long.toHexString(identity));
        this.registry.noteChainPairFramebuffer(identity, handle, renderPass);
        this.chainPairGenerations.put(Long.valueOf(identity), Long.valueOf(generation));
        // counted by noteChainPairFramebuffer
        return handle;
    }

    /** Whether any framebuffer of any kind is held, so teardown can skip its settle when there is nothing to free. */
    boolean isEmpty() {
        return this.frameFramebuffers.isEmpty()
                && this.registry.pairFramebufferHandles(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR).isEmpty()
                && this.registry.allChainPairFramebufferHandles().isEmpty();
    }

    /** Retire framebuffer handles before retiring the image views they reference. */
    void retireChainPair(long identity) {
        Collection<Long> handles = this.registry.chainPairFramebufferHandles(identity);
        if (!handles.isEmpty()) retire(handles);
        this.registry.clearChainPairFramebuffer(identity);
        this.chainPairGenerations.remove(Long.valueOf(identity));
    }

    /**
     * Destroys every framebuffer in place. Only for teardown, after the caller settled the GPU: no submission can
     * still be reading these handles and nothing more will be recorded against them
     * (VUID-vkDestroyFramebuffer-framebuffer-00892).
     */
    void destroyAll() {
        VkDevice device = this.context.device();
        for (Long handle : this.registry.pairFramebufferHandles(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR)) {
            vkDestroyFramebuffer(device, handle.longValue(), null);
        }
        this.registry.clearPairFramebuffer(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR);
        for (Long handle : this.registry.allChainPairFramebufferHandles()) {
            vkDestroyFramebuffer(device, handle.longValue(), null);
        }
        this.registry.clearAllChainPairFramebuffers();
        this.chainPairGenerations.clear();
        for (Long handle : this.frameFramebuffers.values()) {
            vkDestroyFramebuffer(device, handle.longValue(), null);
        }
        this.frameFramebuffers.clear();
    }

    /** Hands framebuffers to {@link VulkanRetireQueue}, which destroys them once nothing in flight can name them. */
    private void retire(Collection<Long> handles) {
        VkDevice device = this.context.device();
        for (Long handle : handles) {
            final long framebuffer = handle.longValue();
            VulkanRetireQueue.retire(() -> vkDestroyFramebuffer(device, framebuffer, null));
        }
    }

    /** Builds one framebuffer over two views; {@code what} names it in a failure, null for the frame target's. */
    private long create(long renderPass, long colorView, long depthView, int width, int height, String what) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, colorView);
            attachments.put(1, depthView);
            VkFramebufferCreateInfo create = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(width)
                    .height(height)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            VulkanTerrainGpuBackend.check(vkCreateFramebuffer(this.context.device(), create, null, handle),
                    what == null ? "vkCreateFramebuffer" : "vkCreateFramebuffer(" + what + ")");
            return handle.get(0);
        }
    }
}
