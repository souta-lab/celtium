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
    private final int[] freeSlots = new int[CAPACITY];
    private int freeTop;
    private int size;

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

    /** The slot a handle refers to, or -1 when the handle is stale or invalid. */
    public int slotOf(int handle) {
        if (handle < 0) {
            return -1;
        }
        int slot = handle & SLOT_MASK;
        boolean current = this.glIdBySlot[slot] >= 0
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
