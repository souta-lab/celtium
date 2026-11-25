package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.soutalab.celtium.vulkan.hal.SlabAllocationHistogram;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A persistently mapped, host-visible uniform buffer holding the per-draw terrain uniform blocks.
 * <p>
 * Terrain draws rewrite a small block for every region and pass, so the data has to be written from the CPU
 * without a staging copy. Host-coherent memory is required rather than preferred: without it every block would
 * need an explicit flush before the draw that reads it. The buffer only grows; the caller addresses blocks by
 * dynamic offset, so existing contents stay valid across a growth.
 * <p>
 * The memory is a sub-allocation of the context's host-visible slab pool (overhaul Stage 3), not a
 * {@code vkAllocateMemory} of this buffer's own: the mapped address is the slab's persistent mapping plus the
 * sub-allocation's offset, so there is no per-buffer {@code vkMapMemory}/{@code vkUnmapMemory} either. The
 * pool's type is host-visible and host-coherent by construction, which is the property the old private
 * {@code findMemoryType} used to enforce here. Moving this buffer onto the Stage 2 Linear Ring is Stage 2
 * integration work, not part of this landing.
 */
final class VulkanUniformBuffer {
    private final VulkanContext context;

    private long buffer;
    private SlabAllocator.SlabAllocation slabAllocation;
    private long mappedAddress;
    private long size;
    private ByteBuffer mappedView;
    private long deviceAlignment;

    /** The buffer's Vulkan usage: uniform blocks by default, a vertex source for the constant-attribute ring. */
    private final int usage;

    VulkanUniformBuffer(VulkanContext context, long initialSize) {
        this(context, initialSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
    }

    /**
     * The same persistently mapped, host-coherent buffer with another usage - the mod-program pipeline's
     * constant-attribute ring binds it as a stride-0 vertex buffer, which needs the vertex usage bit and nothing else
     * this class does differently.
     */
    VulkanUniformBuffer(VulkanContext context, long initialSize, int usage) {
        this.context = context;
        this.usage = usage;
        publish(create(alignToDevice(initialSize), "VulkanUniformBuffer:initial"));
    }

    long size() {
        return this.size;
    }

    long buffer() {
        return this.buffer;
    }

    long mappedAddress() {
        return this.mappedAddress;
    }

    /** The mapped range. Writes through this view are visible to the GPU after the owning queue submission. */
    ByteBuffer mapped() {
        ByteBuffer view = this.mappedView;
        if (view == null && this.mappedAddress != 0L && this.size > 0L) {
            view = MemoryUtil.memByteBuffer(this.mappedAddress, (int) this.size);
            this.mappedView = view;
        }
        return view;
    }

    /**
     * Grows the buffer to hold at least {@code required} bytes, keeping the old contents.
     * <p>
     * <b>BH-24.</b> The replacement is built as its own resource set and this object is not touched until it is
     * complete: {@link #create} throws with only its own partial resources cleaned up, so a failed growth leaves the
     * previous buffer, memory and mapped address exactly as they were. The earlier version saved the old handles in
     * locals and then called {@code allocate}, which wrote {@code this.buffer} first - so a failure anywhere after
     * that left the object holding a zero or half-published handle while the old buffer was only in a local, leaked
     * and unreachable. Only after the copy and the swap is the old set destroyed.
     */
    void ensureCapacity(long required) {
        if (required <= this.size) {
            return;
        }
        long previousSize = this.size;
        long previousBuffer = this.buffer;
        SlabAllocator.SlabAllocation previousAllocation = this.slabAllocation;
        long previousAddress = this.mappedAddress;

        Allocation replacement;
        try {
            // Geometric: the terrain asks for one more block per new region, so growing to the exact request
            // allocated, mapped and copied a new buffer for nearly every region a moving player loaded. The extra
            // capacity is only reported (uniformBlocks); nothing addresses past what was written.
            long grown = previousSize > 0L && previousSize < (Long.MAX_VALUE / 2L)
                    ? Math.max(required, previousSize + (previousSize >> 1)) : required;
            replacement = create(alignToDevice(grown), "VulkanUniformBuffer:growth");
        } catch (RuntimeException | LinkageError failure) {
            // The old buffer, its memory and its mapped address are untouched by create(), so the caller keeps a
            // working buffer and only this growth is lost (BH-24). Counted, so the injection run says it happened.
            this.growthFailures++;
            this.lastGrowthFailure = String.valueOf(failure.getMessage());
            throw failure;
        }

        // Keep the old contents: blocks written earlier in the frame are still referenced by recorded draws.
        MemoryUtil.memCopy(previousAddress, replacement.mappedAddress, previousSize);

        publish(replacement);
        this.growths++;

        // Not destroyed here: "blocks written earlier in the frame are still referenced by recorded draws" is exactly
        // why the old buffer has to outlive the recording that names it (the unified frame buffer keeps that recording
        // open across the growth, and destroying a bound buffer invalidates it - the E2E 1.93a VUIDs). The context
        // destroys it and returns its slab range once no recording can name it. There is no vkUnmapMemory - the
        // mapping belongs to the slab and stays for the slab's whole life.
        this.context.celtium$retireSlabBuffer(previousBuffer, previousAllocation, "uniformBuffer.growth");
    }

    /** Successful growths, so a run says whether the replacement path was taken at all. */
    private int growths;
    /** Growths that failed and left the previous buffer in place (BH-24), with the last failure's text. */
    private int growthFailures;
    private String lastGrowthFailure = "none";

    /** {@return the growth outcome as one report token}, {@code growths=0 failures=0} before any growth. */
    String growthSummary() {
        return "growths=" + this.growths + " growthFailures=" + this.growthFailures
                + " lastGrowthFailure=" + (this.lastGrowthFailure == null ? "none"
                        : this.lastGrowthFailure.replace(' ', '_'));
    }

    /** {@return how many times the buffer was replaced by a larger one}. */
    int growths() {
        return this.growths;
    }

    /** The handles one allocation owns, built completely before any of them is published to this object. */
    private static final class Allocation {
        private long buffer;
        private SlabAllocator.SlabAllocation slabAllocation;
        private long mappedAddress;
        private long size;
    }

    /** Makes the replacement the live one; called only once its allocation, binding and mapping have all succeeded. */
    private void publish(Allocation allocation) {
        this.buffer = allocation.buffer;
        this.slabAllocation = allocation.slabAllocation;
        this.mappedAddress = allocation.mappedAddress;
        this.size = allocation.size;
        this.mappedView = allocation.mappedAddress != 0L && allocation.size > 0L
                ? MemoryUtil.memByteBuffer(allocation.mappedAddress, (int) allocation.size)
                : null;
    }

    /**
     * Builds a fresh buffer/slab-allocation of {@code bytes} and returns it, or throws with everything it created
     * destroyed. Nothing here writes a field of this object, which is what lets {@link #ensureCapacity} keep the old
     * allocation intact when a growth fails.
     */
    private Allocation create(long bytes, String what) {
        Allocation allocation = new Allocation();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes)
                    .usage(this.usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(uniform)");
            allocation.buffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), allocation.buffer, requirements);

            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail(what)) {
                throw destroyReplacement(allocation, "injected allocation failure: " + what);
            }
            // The memory is a sub-allocation of the context's host-visible slab pool, keyed by the memory
            // type this buffer's own requirement bits select; the pool factory itself enforces the
            // host-visible + host-coherent property this class used to pick a type for. The mapped address
            // is the slab's persistent mapping plus the offset - no vkMapMemory of this buffer's own.
            SlabAllocator allocator = this.context.celtium$hostVisibleSlabs(requirements.memoryTypeBits());
            try {
                allocation.slabAllocation = allocator.allocate(requirements.size(), requirements.alignment(),
                        SlabAllocationHistogram.SITE_UNIFORM_BUFFER);
            } catch (RuntimeException error) {
                throw destroyReplacement(allocation, error.getMessage());
            }
            allocator.bindBuffer(allocation.buffer, allocation.slabAllocation);
            allocation.mappedAddress = allocator.mappedAddress(allocation.slabAllocation);
            allocation.size = bytes;
            return allocation;
        } catch (RuntimeException | LinkageError error) {
            // Every path above either returned a complete allocation or threw through destroyReplacement, which
            // released its own resources; this catch only makes that guarantee explicit for an unexpected failure.
            destroyReplacement(allocation, null);
            throw error;
        }
    }

    /** Releases whatever of a failed replacement exists and returns the exception to throw, never a live allocation. */
    private IllegalStateException destroyReplacement(Allocation allocation, String message) {
        if (allocation.buffer != 0) {
            vkDestroyBuffer(this.context.device(), allocation.buffer, null);
            allocation.buffer = 0;
        }
        if (allocation.slabAllocation != null) {
            // Nothing was ever submitted against this buffer; the deferred release is still the only way
            // back into the pool, keyed on the context's current slab release value. No vkFreeMemory and no
            // vkUnmapMemory - the slab and its mapping are not this buffer's to release.
            SlabAllocator.SlabAllocation failed = allocation.slabAllocation;
            allocation.slabAllocation = null;
            failed.owner().release(failed, this.context.celtium$slabReleaseValue());
        }
        allocation.mappedAddress = 0;
        return new IllegalStateException(message == null ? "uniform buffer replacement failed" : message);
    }

    void delete() {
        this.mappedView = null;
        this.mappedAddress = 0;
        if (this.context.celtium$recordingOpen()) {
            // Same lifetime rule as growth: an open recording may name this buffer, so defer the free.
            this.context.celtium$retireSlabBuffer(this.buffer, this.slabAllocation, "uniformBuffer.delete");
            this.buffer = 0;
            this.slabAllocation = null;
            this.size = 0;
            return;
        }
        if (this.buffer != 0) {
            vkDestroyBuffer(this.context.device(), this.buffer, null);
            this.buffer = 0;
        }
        if (this.slabAllocation != null) {
            SlabAllocator.SlabAllocation retired = this.slabAllocation;
            this.slabAllocation = null;
            retired.owner().release(retired, this.context.celtium$slabReleaseValue());
        }
        this.size = 0;
    }

    private long alignToDevice(long value) {
        long alignment = this.deviceAlignment;
        if (alignment == 0L) {
            alignment = 256;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                        org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
                alignment = Math.max(256, properties.limits().minUniformBufferOffsetAlignment());
            }
            this.deviceAlignment = alignment;
        }
        long mask = alignment - 1;
        return Math.max(1024, (value + mask) & ~mask);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }
}
