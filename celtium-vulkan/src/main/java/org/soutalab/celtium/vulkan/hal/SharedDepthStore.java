package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.vulkan.VkDevice;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanRetireQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;

/**
 * One depth image shared by every frame target pair whose framebuffer attaches the same depth texture.
 * <p>
 * GL keys depth storage by the texture: Iris builds its whole shadow framebuffer family over one
 * mainDepth texture, and the pre-translucent depth copy moves that storage into a second depth texture.
 * A pair's depth is private to its framebuffer identity, so the shadow depth used to fragment across the
 * family's pairs and no single image was "the shadow depth" — the copy out of it was refused as not owned
 * and the depth samplers resolved to empty mirrors. The store is the texture-keyed storage GL describes:
 * one image per depth-texture identity, allocated with the pair depth's own allocation (device-chosen
 * format, attachment | transfer | sampled usage), its layout recorded here because every pair that
 * borrows it must agree on where it is.
 * <p>
 * The store owns its images outright — handles, layout records and the cleared-frame record — and pairs
 * only borrow them. It knows nothing about pairs: allocation is the owner's one image allocator, injected
 * as {@link Allocator}, and the eviction of borrowers when an entry is replaced or forgotten is the
 * owner's callback, injected as {@link BorrowerRetirer}. Handle retirement goes through the retire queue
 * like every other deferred destruction; {@link #destroyAll()} is the owner's {@code delete()} path and
 * destroys in place, with the GPU settled.
 * <p>
 * This class is the shared-depth functional group moved out of the GL-less frame target (HAL wave 3):
 * the frame target keeps only the allocator / borrower glue and hands the store to its consumers.
 */
public final class SharedDepthStore {

    /** Allocates one depth image at the requested extent, as {@code {image, memory, view}}. */
    public interface Allocator {
        long[] allocate(int width, int height);
    }

    /** Retires every pair that borrowed the depth under {@code depthKey}; they re-ensure on next use. */
    public interface BorrowerRetirer {
        void retireBorrowers(long depthKey);
    }

    private static final class Entry {
        private long image;
        private long memory;
        private long view;
        private int width;
        private int height;
        private int layout = VK_IMAGE_LAYOUT_UNDEFINED;
        /**
         * The frame serial this image was last cleared in, or -1 when it has not been cleared yet.
         * <p>
         * A GL shadow family is several framebuffers attaching one depth texture, so a clear of one
         * identity's pair is a clear of every family member's depth. The identity-keyed once-per-frame
         * clear rule cannot see that - it asks about the pair, and each family member has its own - so a
         * second identity whose pair has just been created (not acquired yet) clears the family's shadow
         * depth in the middle of the frame. This record is the depth-keyed half of that rule; see
         * {@link SharedDepthStore#clearNeeded(long, long, long)}.
         */
        private long clearedFrameSerial = -1L;
    }

    private final Supplier<VkDevice> device;
    private final Allocator allocator;
    private final BorrowerRetirer borrowerRetirer;

    /** Shared depth images by depth-texture identity; see the class documentation. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Entry> entries =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    public SharedDepthStore(Supplier<VkDevice> device, Allocator allocator, BorrowerRetirer borrowerRetirer) {
        this.device = device;
        this.allocator = allocator;
        this.borrowerRetirer = borrowerRetirer;
    }

    /**
     * Makes the shared depth image for {@code depthKey} exist at the requested extent and returns its
     * {@code {image, memory, view}} triple. A live entry at the same extent is returned as it stands; a
     * different extent retires the old image (through the retire queue — a pair may still be recorded
     * against it) and retires every pair that borrowed it, so no framebuffer keeps a view of the
     * replaced image.
     */
    public long[] ensure(long depthKey, int width, int height) {
        Entry existing = this.entries.get(depthKey);
        if (existing != null && existing.width == width && existing.height == height) {
            return new long[] {existing.image, existing.memory, existing.view};
        }
        if (existing != null) {
            retireHandles(existing);
            this.entries.remove(depthKey);
            this.borrowerRetirer.retireBorrowers(depthKey);
        }
        long[] allocated = this.allocator.allocate(width, height);
        Entry created = new Entry();
        created.image = allocated[0];
        created.memory = allocated[1];
        created.view = allocated[2];
        created.width = width;
        created.height = height;
        this.entries.put(depthKey, created);
        return allocated;
    }

    /** Hands an entry's handles to the retire queue and forgets them in the entry. */
    private void retireHandles(Entry entry) {
        VkDevice device = this.device.get();
        final long view = entry.view;
        final long image = entry.image;
        final long memory = entry.memory;
        entry.view = VK_NULL_HANDLE;
        entry.image = VK_NULL_HANDLE;
        entry.memory = VK_NULL_HANDLE;
        entry.layout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (view != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> vkDestroyImageView(device, view, null));
        }
        if (image != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> vkDestroyImage(device, image, null));
        }
        if (memory != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> VulkanContext.freeMemory(device, memory));
        }
    }

    /**
     * Forgets the shared depth image for {@code depthKey} (its GL texture was deleted): the entry's
     * handles retire and every pair that borrowed it retires with it.
     */
    public void forget(long depthKey) {
        Entry existing = this.entries.remove(depthKey);
        if (existing != null) {
            retireHandles(existing);
        }
        this.borrowerRetirer.retireBorrowers(depthKey);
    }

    /** Whether the store holds an image for {@code depthKey}. */
    public boolean contains(long depthKey) {
        return this.entries.containsKey(depthKey);
    }

    /** The shared depth image for {@code depthKey}, or 0 when none exists. */
    public long image(long depthKey) {
        Entry entry = this.entries.get(depthKey);
        return entry == null ? 0L : entry.image;
    }

    /** The shared depth image view for {@code depthKey}, or 0 when none exists. */
    public long view(long depthKey) {
        Entry entry = this.entries.get(depthKey);
        return entry == null ? 0L : entry.view;
    }

    /** {@return the shared depth image's extent as {width, height}}, or null when none exists. */
    public int[] extent(long depthKey) {
        Entry entry = this.entries.get(depthKey);
        return entry == null ? null : new int[] {entry.width, entry.height};
    }

    /** {@return where the shared depth image for {@code depthKey} is}, UNDEFINED when none exists. */
    public int layout(long depthKey) {
        Entry entry = this.entries.get(depthKey);
        return entry == null ? VK_IMAGE_LAYOUT_UNDEFINED : entry.layout;
    }

    /** Records where the shared depth image for {@code depthKey} has been moved to, by whoever moved it. */
    public void noteLayout(long depthKey, int layout) {
        Entry entry = this.entries.get(depthKey);
        if (entry != null) {
            entry.layout = layout;
        }
    }

    /**
     * The same record as {@link #noteLayout}, skipped when the store already names {@code layout}.
     *
     * @return whether the store's record changed
     */
    public boolean noteLayoutIfChanged(long depthKey, int layout) {
        Entry entry = this.entries.get(depthKey);
        if (entry == null || entry.layout == layout) {
            return false;
        }
        entry.layout = layout;
        return true;
    }

    /** {@return the frame serial the shared depth image for {@code depthKey} was last cleared in}, -1 when never. */
    public long clearedFrameSerial(long depthKey) {
        Entry entry = this.entries.get(depthKey);
        return entry == null ? -1L : entry.clearedFrameSerial;
    }

    /** Records that the shared depth image for {@code depthKey} was cleared in {@code frameSerial}. */
    public void noteCleared(long depthKey, long frameSerial) {
        Entry entry = this.entries.get(depthKey);
        if (entry != null) {
            entry.clearedFrameSerial = frameSerial;
        }
    }

    /** {@return how many shared depth images the store holds}. */
    public int count() {
        return this.entries.size();
    }

    /**
     * Destroys every image the store holds, in place, and empties the store. The owner's {@code delete()}
     * path: pairs only borrowed these images, so they are destroyed here, after the pairs have handed
     * their references back, with the GPU settled.
     */
    public void destroyAll() {
        VkDevice device = this.device.get();
        List<Entry> held = new ArrayList<>(this.entries.values());
        this.entries.clear();
        for (Entry entry : held) {
            if (entry.view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, entry.view, null);
            }
            if (entry.image != VK_NULL_HANDLE) {
                vkDestroyImage(device, entry.image, null);
            }
            if (entry.memory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(device, entry.memory);
            }
        }
    }

    /**
     * {@return whether a pair that renders into the shared depth image for {@code depthKey} may clear its
     * depth in {@code frameSerial}}.
     * <p>
     * Once per frame per depth image, not once per pair: the shadow family's framebuffers share one depth
     * texture (GL's own semantics - the game's {@code glClear} on any of them clears that texture), and
     * every member borrowing it here means the first member's clear is enough for the frame. Without this
     * a member whose pair was created later in the same frame clears the depth the earlier member's pass
     * already wrote, and the copy that follows reads a cleared image.
     * <p>
     * Pure and static so the rule is pinned without a device. A {@code depthKey} of 0 means "no shared
     * depth" and answers {@code false}: there is nothing to guard, and the caller only asks when the pair
     * borrows one.
     */
    public static boolean clearNeeded(long depthKey, long clearedFrameSerial, long frameSerial) {
        return depthKey != 0L && clearedFrameSerial != frameSerial;
    }
}
