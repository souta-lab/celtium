package org.soutalab.celtium.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.glcompat.*;
import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus;
import org.soutalab.celtium.vulkan.hal.FrameLinearRingAccess;
import org.soutalab.celtium.vulkan.hal.LinearRingAllocator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectGlUniformSnapshotTest {
    @Test
    void secondDrawSnapshotLeavesTheFirstDrawsAlignedBytesIntact() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mapped = arena.allocate(512);
            MemorySegment stagedUniformBytes = arena.allocate(16);
            LinearRingAllocator allocator = new LinearRingAllocator(mapped.byteSize());
            FrameLinearRingAccess ring = new FrameLinearRingAccess() {
                @Override public int slots() { return 1; }
                @Override public long slotCapacity(int slot) { return allocator.capacity(); }
                @Override public long used(int slot) { return allocator.used(); }
                @Override public long alloc(int slot, long size, long alignment) { return allocator.alloc(size, alignment); }
                @Override public long mappedAddress(int slot, long offset) { return mapped.address() + offset; }
            };

            stagedUniformBytes.set(ValueLayout.JAVA_INT, 0, 600);
            long drawA = VulkanDirectGlSink.writeUniformSnapshot(ring, 0, 256, stagedUniformBytes);
            stagedUniformBytes.set(ValueLayout.JAVA_INT, 0, 700);
            long drawB = VulkanDirectGlSink.writeUniformSnapshot(ring, 0, 256, stagedUniformBytes);

            assertEquals(0, drawA);
            assertEquals(256, drawB);
            assertEquals(600, mapped.get(ValueLayout.JAVA_INT, drawA));
            assertEquals(700, mapped.get(ValueLayout.JAVA_INT, drawB));
            assertEquals(-1, VulkanDirectGlSink.writeUniformSnapshot(ring, 0, 256, stagedUniformBytes));
            assertEquals(600, mapped.get(ValueLayout.JAVA_INT, drawA));
        }
    }

    @Test
    void submittingADrawWithoutAFrameIncrementsTheRealRefusal() throws Exception {
        var constructor = VulkanContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var sink = new VulkanDirectGlSink(constructor.newInstance(), null, null);
        var reason = DirectGlRefusalCensus.Reason.NO_FRAME;
        long before = DirectGlRefusalCensus.count(reason);
        long total = totalRefusals();
        sink.submitDraw(new DirectGlDrawState(RenderStateKey.DEFAULT, 5, PrimitiveTopology.TRIANGLES, 0),
                new VertexBindingSpan(3, 0, 16, 3), null, DrawCommand.draw(3, 0));

        assertEquals(before + 1, DirectGlRefusalCensus.count(reason));
        assertEquals(total + 1, totalRefusals());
    }

    private static long totalRefusals() {
        return Long.parseLong(DirectGlRefusalCensus.refusalSummary().split(" ")[0].substring("modRefusals=".length()));
    }
}
