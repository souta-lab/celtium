package org.soutalab.celtium.vulkan.bindless;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Slot bookkeeping for the one global bindless texture array (new-architecture design
 * section 2.2: a fixed 4096-entry {@code COMBINED_IMAGE_SAMPLER} array that draws index into
 * instead of rebinding). This class owns only the arithmetic - which GL texture name owns which
 * slot, and whether a handle handed out earlier is still the current registration - while
 * {@link BindlessDescriptorArray} owns the Vulkan objects behind the slots.
 *
 * <p>A registration returns a handle packing the slot with a generation counter
 * ({@code generation << 12 | slot}). When a texture is released its slot goes back on the free
 * list and the next registration of that slot bumps the generation, so a stale handle held by a
 * queued draw is detectable instead of silently sampling whatever texture reused the slot.
 *
 * <p>Releases on the render path are deferred (S5-1): a slot whose texture was destroyed may
 * still be sampled by an in-flight frame, so {@link #detachForDeferredRelease(int)} ends the
 * registration (the name stops resolving and can be registered afresh into a different slot)
 * while keeping the slot itself out of the free list until
 * {@link #completeDeferredRelease(int)} runs once the GPU completion value that retires it has
 * passed. {@link BindlessDeferredReleaseQueue} owns the timing; this class owns the state.
 *
 * <p>Threading: render thread only, like the registry it complements.
 */
public final class BindlessTextureTable {

    /** Fixed array length from the design; also the slot field's width in a handle. */
    public static final int CAPACITY = 4096;

    private static final int SLOT_BITS = 12;
    private static final int SLOT_MASK = CAPACITY - 1;

    public static final int INVALID_HANDLE = -1;

    private final Int2IntOpenHashMap slotByGlId = new Int2IntOpenHashMap();
    private final int[] glIdBySlot = new int[CAPACITY];
    private final int[] generationBySlot = new int[CAPACITY];
    private final boolean[] retiringBySlot = new boolean[CAPACITY];
    private final int[] freeSlots = new int[CAPACITY];
    private int freeTop;
    private int size;
    private int retiring;

    public BindlessTextureTable() {
        this.slotByGlId.defaultReturnValue(-1);
        // Fill the free list so slot 0 is handed out first.
        for (int slot = CAPACITY - 1; slot >= 0; slot--) {
            this.freeSlots[this.freeTop++] = slot;
            this.glIdBySlot[slot] = -1;
        }
    }

    /**
     * Registers a GL texture name, returning its handle. Registering the same name twice returns
     * the existing handle. Returns {@link #INVALID_HANDLE} when the table is full.
     */
    public int register(int glId) {
        int existing = this.slotByGlId.get(glId);
        if (existing >= 0) {
            return handle(existing);
        }
        if (this.freeTop == 0) {
            return INVALID_HANDLE;
        }
        int slot = this.freeSlots[--this.freeTop];
        this.glIdBySlot[slot] = glId;
        this.generationBySlot[slot]++;
        this.slotByGlId.put(glId, slot);
        this.size++;
        return handle(slot);
    }

    /** Releases the registration for a GL texture name; unknown names are ignored. */
    public void release(int glId) {
        int slot = this.slotByGlId.remove(glId);
        if (slot < 0) {
            return;
        }
        this.glIdBySlot[slot] = -1;
        this.freeSlots[this.freeTop++] = slot;
        this.size--;
    }

    /**
     * Ends the registration for a GL texture name without freeing its slot: the name stops
     * resolving (and may be registered again, into a different slot), but the slot stays out of
     * the free list - and its descriptor entry untouched - until
     * {@link #completeDeferredRelease(int)} is called with the returned handle. This is the only
     * release the render path may use for a texture whose image is being destroyed, because an
     * in-flight frame may still sample the slot.
     *
     * @return the detached registration's handle, or {@link #INVALID_HANDLE} when the name was
     *         not registered
     */
    public int detachForDeferredRelease(int glId) {
        int slot = this.slotByGlId.remove(glId);
        if (slot < 0) {
            return INVALID_HANDLE;
        }
        this.retiringBySlot[slot] = true;
        this.retiring++;
        this.size--;
        return handle(slot);
    }

    /**
     * Frees a slot detached by {@link #detachForDeferredRelease(int)} once the caller knows the
     * GPU is done with it. A handle whose generation no longer matches (the slot was already
     * completed and reused) or that names a live registration completes nothing.
     *
     * @return whether a slot was freed
     */
    public boolean completeDeferredRelease(int handle) {
        if (handle < 0) {
            return false;
        }
        int slot = handle & SLOT_MASK;
        if (!this.retiringBySlot[slot]
                || this.generationBySlot[slot] != (handle >>> SLOT_BITS)) {
            return false;
        }
        this.retiringBySlot[slot] = false;
        this.retiring--;
        this.glIdBySlot[slot] = -1;
        this.freeSlots[this.freeTop++] = slot;
        return true;
    }

    /** Slots detached for deferred release whose completion has not been applied yet. */
    public int retiringCount() {
        return this.retiring;
    }

    /** The slot a handle refers to, or -1 when the handle is stale or invalid. */
    public int slotOf(int handle) {
        if (handle < 0) {
            return -1;
        }
        int slot = handle & SLOT_MASK;
        boolean current = this.glIdBySlot[slot] >= 0
                && !this.retiringBySlot[slot]
                && this.generationBySlot[slot] == (handle >>> SLOT_BITS);
        return current ? slot : -1;
    }

    /** The current handle for a GL texture name, or {@link #INVALID_HANDLE}. */
    public int handleOf(int glId) {
        int slot = this.slotByGlId.get(glId);
        return slot < 0 ? INVALID_HANDLE : handle(slot);
    }

    /** The GL texture name owning a slot, or -1 when the slot is free. */
    public int glIdAt(int slot) {
        return this.glIdBySlot[slot];
    }

    public int size() {
        return this.size;
    }

    private int handle(int slot) {
        return (this.generationBySlot[slot] << SLOT_BITS) | slot;
    }
}
