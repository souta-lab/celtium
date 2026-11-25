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
 * on a device without descriptor indexing ({@code textureArraySupported() == false}) and under
 * {@code -Dceltium.vulkan.forceNoBindless=true} (the measurement kill-switch). Callers keep the
 * binding in a nullable field and skip every hook when it is null, which is exactly the
 * per-texture descriptor path the design prescribes for unsupported environments - the batcher
 * side of that fallback is {@code ImmediateBatcher.setBindlessEnabled(false)}. A registration
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
    }

    public BindlessTextureBinding(BindlessDescriptorArray array, LongSupplier retireValueSource) {
        this(array, array::writeSlot, retireValueSource);
    }

    /**
     * Builds the binding for a device, or returns {@code null} when bindless is unavailable or
     * force-disabled - the caller then runs the per-texture fallback and must tell the batcher
     * ({@code setBindlessEnabled(false)}) so fused draws never span textures.
     */
    public static BindlessTextureBinding create(VulkanContext context,
                                                LongSupplier retireValueSource) {
        if (context == null || !context.textureArraySupported()) {
            return null;
        }
        if ("true".equalsIgnoreCase(System.getProperty("celtium.vulkan.forceNoBindless", "false"))) {
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
        int existing = this.table.handleOf(glId);
        if (existing != BindlessTextureTable.INVALID_HANDLE) {
            this.slotWriter.writeSlot(this.table.slotOf(existing), imageView, sampler);
            return existing;
        }
        int handle = this.table.register(glId);
        if (handle == BindlessTextureTable.INVALID_HANDLE) {
            this.overflowRegistrations++;
            return handle;
        }
        this.slotWriter.writeSlot(this.table.slotOf(handle), imageView, sampler);
        return handle;
    }

    /** Ends a texture's registration; the slot itself is freed by {@link #advance(long)}. */
    public void onTextureReleased(int glId) {
        this.releases.defer(this.table, glId, this.retireValueSource.getAsLong());
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
