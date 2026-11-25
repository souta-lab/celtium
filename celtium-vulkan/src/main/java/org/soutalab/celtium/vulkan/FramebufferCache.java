package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
 * <p>
 * Every kind also carries what it was built over, and answers only for that: the frame target's view tuple
 * ({@link FrameEntry}), the default pair's extent, the chain pair's generations. A handle is a fact about the images
 * it names, not only about the render pass it is compatible with, and the caches are asked long after the images
 * changed.
 */
final class FramebufferCache {
    private final VulkanContext context;
    private final RenderTargetRegistry registry;
    /**
     * The frame target's framebuffers keyed by the render pass each is compatible with, then by the colour
     * view each was built over. One entry per render pass was the rule while the frame target owned its
     * colour image: a single-slot cache replaced the entry on every pass alternation - each replacement
     * paying a device-idle wait to retire the old handle (measured ~5 settleGpu calls per frame on leg
     * ind2-amd). The direct present path (lanechat #8830 (3)(4)) rotates the colour view with the borrowed
     * swapchain image, so the colour view joins the key: each (pass, view) pair builds its framebuffer
     * once and keeps it for the swapchain generation's life, instead of retiring and recreating on every
     * frame's rotation. Each entry also carries the attachments it was built over; see {@link FrameEntry}.
     */
    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<FrameEntry>> frameFramebuffers =
            new Long2ObjectOpenHashMap<>();
    /** Per identity, the chain-pair generation its cached framebuffers were built over. */
    private final Long2LongOpenHashMap chainPairGenerations = new Long2LongOpenHashMap();
    /** Per identity, the colour-tuple generation its cached multi-target framebuffers were built over. */
    private final Long2LongOpenHashMap chainPairTupleGenerations = new Long2LongOpenHashMap();
    /**
     * The creation serial of every live handle this cache built, by handle (hs_err triage instrument (i)).
     * A handle value alone cannot distinguish the live framebuffer from a destroyed one whose value the
     * driver recycled; the serial can, and the window-pass breadcrumb records it at every begin. An entry
     * lives as long as its handle does: retirement keeps it (a retired handle can still be named by an open
     * recording, and its begin is exactly what the instrument exists to judge), and the entry is dropped
     * only when the handle is actually destroyed - in the retire queue's destroy lambda, or in
     * {@link #destroyAll()}.
     */
    private final Long2LongOpenHashMap framebufferGenerations = new Long2LongOpenHashMap();
    private long nextFramebufferGeneration;

    FramebufferCache(VulkanContext context, RenderTargetRegistry registry) {
        this.context = context;
        this.registry = registry;
        this.chainPairGenerations.defaultReturnValue(-1L);
        this.chainPairTupleGenerations.defaultReturnValue(-1L);
        this.framebufferGenerations.defaultReturnValue(0L);
    }

    /**
     * {@return the creation serial {@link #create} stamped this handle with}, or 0 when the handle did not
     * come from this cache (or was already destroyed). Read by the window-pass breadcrumb at begin time.
     */
    long framebufferGeneration(long handle) {
        return handle == 0L ? 0L : this.framebufferGenerations.get(handle);
    }

    /**
     * The frame target's framebuffer for a render pass. Created here rather than in the frame target because
     * compatibility is defined by the render pass the pipeline built, and one framebuffer serves every pass that is
     * compatible with it. Creating one needs no settle: nothing is destroyed, the map keeps the older passes' handles
     * alive beside the new one.
     * <p>
     * The render pass alone is not a safe key: it says which pass the framebuffer is compatible with, not which
     * views it attaches. The map was emptied only by {@link #destroyAll()}, whose caller refuses to run when its
     * settle does not land, so an entry built over the previous frame target could survive a replacement and answer
     * the next window - which then began a pass against a framebuffer naming destroyed image views (issue #423,
     * item 372 hole 1). The entry therefore records its attachments and is rebuilt when they no longer match, the
     * same way the chain pair's entry is rebuilt when its generation moves.
     */
    private long lastFramePass = Long.MIN_VALUE;
    private FrameEntry lastFrameEntry;

    long frame(long renderPass, long colorView, long depthView, int width, int height) {
        Long2ObjectOpenHashMap<FrameEntry> byView = this.frameFramebuffers.get(renderPass);
        FrameEntry cached = (renderPass == this.lastFramePass) ? this.lastFrameEntry
                : (byView == null ? null : byView.get(colorView));
        if (celtium$frameEntryReusable(cached, colorView, depthView, width, height)) {
            this.lastFramePass = renderPass;
            this.lastFrameEntry = cached;
            noteFrameCache(CrashBreadcrumbs.FRAME_CACHE_HIT, cached);
            return cached.handle;
        }
        if (cached != null) {
            // Built over attachments that are gone (same colour view, a depth view or extent that moved).
            // This handle is named by nothing the GPU can still be reading - it is an unused cache entry,
            // not a pass in an open recording - so it goes to the retire queue rather than being destroyed
            // in place: the queue is the rule every handle of this kind follows (see the class note).
            // Entries built over the other colour views are not touched: their views are still live.
            byView.remove(colorView);
            if (byView.isEmpty()) {
                this.frameFramebuffers.remove(renderPass);
            }
            if (cached == this.lastFrameEntry) {
                this.lastFramePass = Long.MIN_VALUE;
                this.lastFrameEntry = null;
            }
            retire(List.of(cached.handle));
        }
        FrameEntry entry = new FrameEntry(create(renderPass, colorView, depthView, width, height, null),
                colorView, depthView, width, height);
        Long2ObjectOpenHashMap<FrameEntry> slot = this.frameFramebuffers.get(renderPass);
        if (slot == null) {
            slot = new Long2ObjectOpenHashMap<>();
            this.frameFramebuffers.put(renderPass, slot);
        }
        slot.put(colorView, entry);
        this.lastFramePass = renderPass;
        this.lastFrameEntry = entry;
        noteFrameCache(CrashBreadcrumbs.FRAME_CACHE_MISS, entry);
        return entry.handle;
    }

    /**
     * {@return whether the cached entry may answer for a pass that attaches these views at this extent}.
     * <p>
     * The rule the frame side was missing: the entry is stamped with what it was built over, and only an entry whose
     * stamp matches answers. No entry (null) means "build one", a different stamp means "this handle names views that
     * are gone or an extent that is wrong - retire it and build again".
     */
    static boolean celtium$frameEntryReusable(FrameEntry cached, long colorView, long depthView, int width,
            int height) {
        return cached != null && cached.builtOver(colorView, depthView, width, height);
    }

    /**
     * One frame-target framebuffer and the attachments it was created over.
     * <p>
     * The views are the frame target's colour and depth views, so a replacement of the target makes every entry's
     * views dead while the entry itself still looks usable by its render-pass key. Comparing them is what keeps the
     * cache from answering with such a handle; the extent is compared with them because a framebuffer created at
     * the old size is equally unusable for the new target's passes.
     */
    static final class FrameEntry {
        private final long handle;
        private final long colorView;
        private final long depthView;
        private final int width;
        private final int height;

        FrameEntry(long handle, long colorView, long depthView, int width, int height) {
            this.handle = handle;
            this.colorView = colorView;
            this.depthView = depthView;
            this.width = width;
            this.height = height;
        }

        /** {@return whether this entry was built over exactly the attachments the caller is about to draw with}. */
        boolean builtOver(long colorView, long depthView, int width, int height) {
            return this.colorView == colorView && this.depthView == depthView
                    && this.width == width && this.height == height;
        }
    }

    /**
     * Records the frame cache's decision for the crash ring: which handle answers, and the views it was built over.
     * <p>
     * The ring is dumped only when a device is lost, and an address-only fault report cannot say whether the dead
     * object was the render pass, the framebuffer or one of its views (item 372 audit, section 5). These numbers
     * plus the window's own note are what identify the object when the loss recurs.
     */
    private static void noteFrameCache(int code, FrameEntry entry) {
        if (CrashBreadcrumbs.enabled()) {
            CrashBreadcrumbs.note3(code, entry.handle, entry.colorView, entry.depthView);
        }
    }

    /**
     * Retires every frame-target framebuffer and forgets them all.
     * <p>
     * For a world teardown, where the render passes these entries are keyed by were just destroyed: a driver is free
     * to reuse a freed handle value for the next world's pass, and the cache would then answer with a framebuffer
     * built for a pass that no longer exists (item 372, hole 2). Retired rather than destroyed for the same reason as
     * any other mid-session handle - an open recording can still name them - and every entry goes, including the UI
     * pass's, so the rule need not know which keys are still live: a re-creation costs one command per pass on the
     * next world's first window.
     *
     * @return how many handles were retired
     */
    int retireFrameFramebuffers() {
        if (this.frameFramebuffers.isEmpty()) {
            return 0;
        }
        List<Long> handles = new ArrayList<>();
        for (Long2ObjectOpenHashMap<FrameEntry> byView : this.frameFramebuffers.values()) {
            for (FrameEntry entry : byView.values()) {
                handles.add(entry.handle);
            }
        }
        this.frameFramebuffers.clear();
        this.lastFramePass = Long.MIN_VALUE;
        this.lastFrameEntry = null;
        retire(handles);
        return handles.size();
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
        return chainPair(this.registry.currentIdentity(), renderPass, glless, windowWidth, windowHeight);
    }

    long chainPair(long identity, long renderPass, GllessFrameTarget glless, int windowWidth, int windowHeight) {
        long generation = glless.chainPairGeneration(identity);
        long builtOver = this.chainPairGenerations.get(identity);
        if (builtOver != generation) {
            // Same rule as the default pair: replaced images mean the views every cached handle named are dead. Asked
            // of the pair's own images, not of the window's size - a pair at its framebuffer's size (Xaero's minimap)
            // never matched the window, and every pass into it settled the GPU to rebuild an identical framebuffer
            // (2,940 creations in a 65 s Intel run, vkDeviceWaitIdle 80% of the client thread's native samples).
            Collection<Long> stale = this.registry.chainPairFramebufferHandles(identity);
            if (!stale.isEmpty()) {
                retire(stale);
                this.registry.clearChainPairFramebuffer(identity);
                this.chainPairGenerations.remove(identity);
                this.chainPairTupleGenerations.remove(identity);
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
        this.chainPairGenerations.put(identity, generation);
        // counted by noteChainPairFramebuffer
        return handle;
    }

    /**
     * The framebuffer for a pass that draws into the current identity's chain pair through several colour
     * targets at once: the pair's views at slots 0..colorCount-1, then its depth. Keyed like
     * {@link #chainPair} - by the render pass, which already differs per colour count - plus the tuple
     * generation: a tuple rebuilt at the same pair generation (a count change retires and recreates only the
     * extra images) leaves every cached handle naming dead views, so the staleness rule watches both
     * generations and retires the identity's whole slot when either moves.
     */
    long chainPairMulti(long renderPass, GllessFrameTarget glless, int colorCount, int windowWidth,
            int windowHeight) {
        return chainPairMulti(this.registry.currentIdentity(), renderPass, glless, colorCount, windowWidth,
                windowHeight);
    }

    long chainPairMulti(long identity, long renderPass, GllessFrameTarget glless, int colorCount, int windowWidth,
            int windowHeight) {
        long generation = glless.chainPairGeneration(identity);
        long tupleGeneration = glless.chainPairColorTupleGeneration(identity);
        long recordedTupleGen = this.chainPairTupleGenerations.get(identity);
        boolean stale = this.chainPairGenerations.get(identity) != generation
                || (recordedTupleGen != -1L && recordedTupleGen != tupleGeneration);
        if (stale) {
            Collection<Long> staleHandles = this.registry.chainPairFramebufferHandles(identity);
            if (!staleHandles.isEmpty()) {
                retire(staleHandles);
                this.registry.clearChainPairFramebuffer(identity);
                this.chainPairGenerations.remove(identity);
                this.chainPairTupleGenerations.remove(identity);
            }
        }
        long cached = this.registry.chainPairFramebuffer(identity, renderPass);
        if (cached != 0L) {
            return cached;
        }
        long[] pairExtent = glless.chainPairExtent(identity);
        int pairWidth = pairExtent == null ? windowWidth : (int) pairExtent[0];
        int pairHeight = pairExtent == null ? windowHeight : (int) pairExtent[1];
        long[] colorViews = new long[colorCount];
        for (int slot = 0; slot < colorCount; slot++) {
            colorViews[slot] = glless.chainPairColorViewAt(identity, slot);
        }
        long handle = create(renderPass, colorViews, glless.chainPairDepthView(identity),
                pairWidth, pairHeight, "chain-pair-mrt:" + Long.toHexString(identity));
        this.registry.noteChainPairFramebuffer(identity, handle, renderPass);
        this.chainPairGenerations.put(identity, generation);
        this.chainPairTupleGenerations.put(identity, tupleGeneration);
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
        this.chainPairGenerations.remove(identity);
        this.chainPairTupleGenerations.remove(identity);
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
        this.chainPairTupleGenerations.clear();
        for (Long2ObjectOpenHashMap<FrameEntry> byView : this.frameFramebuffers.values()) {
            for (FrameEntry entry : byView.values()) {
                vkDestroyFramebuffer(device, entry.handle, null);
            }
        }
        this.frameFramebuffers.clear();
        this.framebufferGenerations.clear();
        this.lastFramePass = Long.MIN_VALUE;
        this.lastFrameEntry = null;
    }

    /** Hands framebuffers to {@link VulkanRetireQueue}, which destroys them once nothing in flight can name them. */
    private void retire(Collection<Long> handles) {
        VkDevice device = this.context.device();
        for (Long handle : handles) {
            final long framebuffer = handle.longValue();
            // The generation entry is dropped inside the destroy lambda, not here: until the queue actually
            // destroys the handle it still exists, and a begin against it in that window must still resolve
            // to the generation it was created with.
            VulkanRetireQueue.retire(device.address(), () -> {
                vkDestroyFramebuffer(device, framebuffer, null);
                this.framebufferGenerations.remove(framebuffer);
            });
        }
    }

    /** Builds one framebuffer over two views; {@code what} names it in a failure, null for the frame target's. */
    private long create(long renderPass, long colorView, long depthView, int width, int height, String what) {
        return create(renderPass, new long[] {colorView}, depthView, width, height, what);
    }

    /** Builds one framebuffer over {@code colorViews.length} colour views and the depth view. */
    private long create(long renderPass, long[] colorViews, long depthView, int width, int height, String what) {
        for (long colorView : colorViews) {
            if (colorView == 0L || depthView == 0L) {
                // The pair behind these views is not ensured (it was retired or never created for this identity).
                // Building the framebuffer anyway passed VK_NULL_HANDLE attachments to vkCreateFramebuffer - a
                // VUID the validation layer named, and a RADV segfault when it went unchecked. Refused by name so
                // the caller can drop the pass.
                throw new IllegalStateException(what + " framebuffer would bind a null attachment (colour=0x"
                        + Long.toHexString(colorView) + " depth=0x" + Long.toHexString(depthView)
                        + "); its pair is not ensured");
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(colorViews.length + 1);
            for (int slot = 0; slot < colorViews.length; slot++) {
                attachments.put(slot, colorViews[slot]);
            }
            attachments.put(colorViews.length, depthView);
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
            long created = handle.get(0);
            this.framebufferGenerations.put(created, ++this.nextFramebufferGeneration);
            return created;
        }
    }
}
