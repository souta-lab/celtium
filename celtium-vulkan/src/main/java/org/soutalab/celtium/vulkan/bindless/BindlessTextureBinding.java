package org.soutalab.celtium.vulkan.bindless;

import org.soutalab.celtium.vulkan.VulkanContext;

import java.util.function.LongSupplier;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * The wired form of the bindless texture array (S5-1): one {@link BindlessTextureTable} plus
 * the {@link BindlessDescriptorArray} behind it, fed by the texture registry's lifecycle.
 *
 * <p>The registry calls {@link #onTextureRegistered(int, long, long)} when a mirror is created
 * or its image is replaced, and {@link #onTextureReleased(int)} when the mirror is destroyed.
 * Registration writes the descriptor slot in the same call; release only detaches the name and
 * queues the slot behind the frame currency (see {@link BindlessDeferredReleaseQueue}), because
 * an in-flight frame may still sample it. The frame loop calls {@link #advance(long)} with the
 * completion value to free matured slots.
 *
 * <p>Absence is the fallback: {@link #create(VulkanContext, LongSupplier)} returns {@code null}
 * on a device without descriptor indexing ({@code textureArraySupported() == false}). Callers keep the
 * binding in a nullable field and skip every hook when it is null, which is exactly the
 * per-texture descriptor path the design prescribes for unsupported environments - the batcher
 * side of that fallback is per entry: the capture's array-index lookup answers -1 for every
 * texture while no binding exists, so every group is captured in the per-texture form and no
 * entry ever claims a texture rides in its record. A registration
 * that finds the table full returns {@link BindlessTextureTable#INVALID_HANDLE} and is counted
 * in {@link #overflowRegistrations()}; the draw for that texture takes the same per-texture
 * fallback.
 *
 * <p>Threading: render thread only.
 */
public final class BindlessTextureBinding {

    /** Writes one descriptor array slot; the seam that lets the binding be tested headlessly. */
    interface SlotWriter {
        void writeSlot(int slot, long imageView, long sampler);
    }

    private final BindlessTextureTable table = new BindlessTextureTable();
    private final BindlessDeferredReleaseQueue releases = new BindlessDeferredReleaseQueue();
    private final BindlessDescriptorArray array;
    private final SlotWriter slotWriter;
    private final LongSupplier retireValueSource;
    private int overflowRegistrations;

    // ------------------------------------------------------------------
    // Slot-content dump instrument (panel-background array-form defect,
    // OVERHAUL-RERERUN2-FINDINGS-SEPARATION-2026-11-27 section 1.6 point 1).
    // Enabled by -Dceltium.debug.bindlessSlotDump=<dir>; while enabled the
    // binding remembers, per slot, the (image, imageView, sampler) it last
    // wrote and keeps a bounded journal of register/release events, so the
    // registry can compare what a slot references against the owning
    // texture's current mirror. All arrays stay unallocated while disabled.
    // ------------------------------------------------------------------

    /** Whether the slot dump instrument is armed (read once, like every other debug gate). */
    static final boolean SLOT_DUMP_ENABLED =
            System.getProperty("celtium.debug.bindlessSlotDump") != null;

    private final long[] writtenImages;
    private final long[] writtenViews;
    private final long[] writtenSamplers;
    private final int[] writtenGlIds;
    private final java.util.ArrayList<String> slotJournal;
    private int slotJournalSeq;

    /**
     * @param array             the descriptor array behind the table, or {@code null} in tests
     * @param slotWriter        where slot writes go (the array in production)
     * @param retireValueSource the frame currency's newest allocated value - the value the most
     *                          recently submitted frame will signal, <em>not</em> the completed
     *                          value; releases are stamped with it so a slot is freed only after
     *                          every frame that could sample it has completed
     */
    BindlessTextureBinding(BindlessDescriptorArray array, SlotWriter slotWriter,
                           LongSupplier retireValueSource) {
        if (slotWriter == null) {
            throw new NullPointerException("slotWriter");
        }
        if (retireValueSource == null) {
            throw new NullPointerException("retireValueSource");
        }
        this.array = array;
        this.slotWriter = slotWriter;
        this.retireValueSource = retireValueSource;
        if (SLOT_DUMP_ENABLED) {
            this.writtenImages = new long[BindlessTextureTable.CAPACITY];
            this.writtenViews = new long[BindlessTextureTable.CAPACITY];
            this.writtenSamplers = new long[BindlessTextureTable.CAPACITY];
            this.writtenGlIds = new int[BindlessTextureTable.CAPACITY];
            java.util.Arrays.fill(this.writtenGlIds, -1);
            this.slotJournal = new java.util.ArrayList<String>();
        } else {
            this.writtenImages = null;
            this.writtenViews = null;
            this.writtenSamplers = null;
            this.writtenGlIds = null;
            this.slotJournal = null;
        }
    }

    public BindlessTextureBinding(BindlessDescriptorArray array, LongSupplier retireValueSource) {
        this(array, array::writeSlot, retireValueSource);
    }

    /**
     * Builds the binding for a device, or returns {@code null} when bindless is unavailable -
     * the caller then runs the per-texture fallback: with no binding the
     * capture's index lookup answers -1 for every texture, so no captured entry claims a
     * texture in its record and fused draws never span textures on the per-texture form.
     */
    public static BindlessTextureBinding create(VulkanContext context,
                                                LongSupplier retireValueSource) {
        if (context == null || !context.textureArraySupported()) {
            return null;
        }
        return new BindlessTextureBinding(new BindlessDescriptorArray(context), retireValueSource);
    }

    /**
     * Registers a texture's current image, or rewrites its slot when the image behind an
     * existing registration was replaced.
     *
     * @return the registration handle, or {@link BindlessTextureTable#INVALID_HANDLE} when the
     *         table is full - the caller then keeps that texture on the per-texture path
     */
    public int onTextureRegistered(int glId, long imageView, long sampler) {
        return onTextureRegistered(glId, imageView, sampler, 0L);
    }

    /**
     * The same registration, with the image behind the view named as well. The image handle is
     * not used by the binding itself; the slot dump instrument records it so a slot's content can
     * be compared against the owning texture's current image (see the instrument note above).
     */
    public int onTextureRegistered(int glId, long imageView, long sampler, long image) {
        int existing = this.table.handleOf(glId);
        if (existing != BindlessTextureTable.INVALID_HANDLE) {
            int slot = this.table.slotOf(existing);
            this.slotWriter.writeSlot(slot, imageView, sampler);
            recordSlotWrite(glId, slot, imageView, sampler, image, true);
            return existing;
        }
        int handle = this.table.register(glId);
        if (handle == BindlessTextureTable.INVALID_HANDLE) {
            this.overflowRegistrations++;
            return handle;
        }
        int slot = this.table.slotOf(handle);
        this.slotWriter.writeSlot(slot, imageView, sampler);
        recordSlotWrite(glId, slot, imageView, sampler, image, false);
        return handle;
    }

    /** Ends a texture's registration; the slot itself is freed by {@link #advance(long)}. */
    public void onTextureReleased(int glId) {
        if (SLOT_DUMP_ENABLED) {
            journal("release gl=" + glId + " handle=" + this.table.handleOf(glId));
        }
        this.releases.defer(this.table, glId, this.retireValueSource.getAsLong());
    }

    private void recordSlotWrite(int glId, int slot, long imageView, long sampler, long image,
                                 boolean rewrite) {
        if (!SLOT_DUMP_ENABLED || slot < 0) {
            return;
        }
        this.writtenImages[slot] = image;
        this.writtenViews[slot] = imageView;
        this.writtenSamplers[slot] = sampler;
        this.writtenGlIds[slot] = glId;
        journal((rewrite ? "rewrite" : "register") + " gl=" + glId + " slot=" + slot
                + " view=" + imageView + " image=" + image + " sampler=" + sampler);
    }

    private void journal(String event) {
        if (this.slotJournal == null) {
            return;
        }
        if (this.slotJournal.size() >= 8192) {
            this.slotJournal.remove(0);
        }
        this.slotJournal.add("#" + (this.slotJournalSeq++) + " " + event);
    }

    /** The image handle last written into a slot (slot dump instrument; 0 while disabled). */
    public long writtenImageAt(int slot) {
        return this.writtenImages == null ? 0L : this.writtenImages[slot];
    }

    /** The image view last written into a slot (slot dump instrument; 0 while disabled). */
    public long writtenViewAt(int slot) {
        return this.writtenViews == null ? 0L : this.writtenViews[slot];
    }

    /** The sampler last written into a slot (slot dump instrument; 0 while disabled). */
    public long writtenSamplerAt(int slot) {
        return this.writtenSamplers == null ? 0L : this.writtenSamplers[slot];
    }

    /** The GL name the slot was last written for (slot dump instrument; -1 while disabled). */
    public int writtenGlIdAt(int slot) {
        return this.writtenGlIds == null ? -1 : this.writtenGlIds[slot];
    }

    /** A snapshot of the register/release journal (slot dump instrument; empty while disabled). */
    public java.util.List<String> slotJournal() {
        return this.slotJournal == null
                ? java.util.Collections.<String>emptyList()
                : new java.util.ArrayList<String>(this.slotJournal);
    }

    /**
     * Frees the queued slots whose retire value has passed.
     *
     * @return how many slots were freed
     */
    public int advance(long completedValue) {
        return this.releases.drain(this.table, completedValue);
    }

    /** The current handle for a GL texture name, or {@link BindlessTextureTable#INVALID_HANDLE}. */
    public int handleOf(int glId) {
        return this.table.handleOf(glId);
    }

    /** The slot a handle refers to, or -1 when the handle is stale, invalid or detached. */
    public int slotOf(int handle) {
        return this.table.slotOf(handle);
    }

    /** Live registrations (detached slots awaiting completion are not counted). */
    public int registeredCount() {
        return this.table.size();
    }

    /** Slots detached but not yet freed, waiting on the completion value. */
    public int pendingReleases() {
        return this.releases.pending();
    }

    /** Registrations refused because the 4096-slot table was full. */
    public int overflowRegistrations() {
        return this.overflowRegistrations;
    }

    public long descriptorSet() {
        return this.array == null ? VK_NULL_HANDLE : this.array.descriptorSet();
    }

    public long descriptorSetLayout() {
        return this.array == null ? VK_NULL_HANDLE : this.array.descriptorSetLayout();
    }

    public void delete() {
        if (this.array != null) {
            this.array.delete();
        }
    }
}
