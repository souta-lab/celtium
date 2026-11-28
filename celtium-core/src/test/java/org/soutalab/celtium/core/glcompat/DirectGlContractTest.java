package org.soutalab.celtium.core.glcompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink.BlitRegion;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink.ClearSpec;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink.FramebufferOps;

import org.soutalab.celtium.core.state.RenderStateKey;

/**
 * The value types of interface 3's sink surface: span arithmetic and validation, draw command
 * shapes, and handle validity.
 */
class DirectGlContractTest {

    @Test
    void spansValidateAndMeasure() {
        VertexBindingSpan vertices = new VertexBindingSpan(9L, 64L, 32, 100);
        assertEquals(3200L, vertices.byteLength());
        assertThrows(IllegalArgumentException.class, () -> new VertexBindingSpan(0L, 0L, 32, 1));
        assertThrows(IllegalArgumentException.class, () -> new VertexBindingSpan(9L, 0L, 0, 1));
        IndexSpan indices = new IndexSpan(9L, 0L, 36, IndexSpan.IndexType.UINT32);
        assertEquals(144L, indices.byteLength());
        assertEquals(2, IndexSpan.IndexType.UINT16.byteSize());
    }

    @Test
    void drawCommandFactoriesZeroTheUnusedHalf() {
        DrawCommand draw = DrawCommand.draw(24, 3);
        assertFalse(draw.indexed());
        assertEquals(24, draw.vertexCount());
        assertEquals(0, draw.indexCount());
        assertEquals(1, draw.instanceCount());
        DrawCommand indexed = DrawCommand.drawIndexed(36, 6, 2);
        assertTrue(indexed.indexed());
        assertEquals(36, indexed.indexCount());
        assertEquals(0, indexed.vertexCount());
        assertThrows(IllegalArgumentException.class, () -> DrawCommand.drawIndexed(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DrawCommand.draw(0, 0));
    }

    @Test
    void drawStateAndHandlesCarryTheirInvariants() {
        DirectGlDrawState state = new DirectGlDrawState(
                RenderStateKey.DEFAULT, 5L, PrimitiveTopology.TRIANGLES, 0);
        assertEquals(PrimitiveTopology.TRIANGLES, state.topology());
        assertThrows(IllegalArgumentException.class, () -> new DirectGlDrawState(
                RenderStateKey.DEFAULT, 5L, PrimitiveTopology.TRIANGLES, -1));
        assertFalse(ComputeProgramHandle.NONE.isValid());
        assertTrue(new ComputeProgramHandle(3L).isValid());
    }

    @Test
    void shaderOpsReceivesThePackedBlockAndRetainsItsOwnSnapshot() {
        class ShaderSeat implements DirectGlVulkanSink.ShaderOps {
            long program;
            byte[] snapshot;

            @Override
            public long createProgram(org.soutalab.celtium.core.shader.SpirvModuleBundle bundle) { return 42; }

            @Override
            public ComputeProgramHandle createComputeProgram(org.soutalab.celtium.core.shader.SpirvModuleBundle bundle) {
                return ComputeProgramHandle.NONE;
            }

            @Override
            public void uploadUniforms(long handle, java.lang.foreign.MemorySegment values) {
                program = handle;
                snapshot = values.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
            }

            @Override
            public void destroyProgram(long handle) {}
        }
        var sink = new ShaderSeat();
        byte[] block = new byte[32];
        block[16] = 9;
        sink.uploadUniforms(42, java.lang.foreign.MemorySegment.ofArray(block));
        block[16] = 12;
        assertEquals(42, sink.program);
        assertEquals(32, sink.snapshot.length);
        assertEquals(9, sink.snapshot[16]);
    }

    @Test
    void framebufferOpsCarryClearAndBlitValuesToBothTargets() {
        record Clear(long target, ClearSpec spec) {}
        record Blit(long source, long destination, BlitRegion region) {}
        List<Clear> clears = new ArrayList<>();
        List<Blit> blits = new ArrayList<>();
        FramebufferOps sink = new FramebufferOps() {
            @Override
            public long createFramebuffer(long[] colors, long depth, int width, int height) {
                return 42L;
            }

            @Override
            public void destroyFramebuffer(long framebufferId) {
            }

            @Override
            public void clearFramebuffer(long framebufferId, ClearSpec spec) {
                clears.add(new Clear(framebufferId, spec));
            }

            @Override
            public void blitFramebuffer(long srcFramebufferId, long dstFramebufferId, BlitRegion region) {
                blits.add(new Blit(srcFramebufferId, dstFramebufferId, region));
            }
        };

        long custom = sink.createFramebuffer(new long[] {1L}, 2L, 32, 16);
        var depth = new ClearSpec(DirectGlVulkanSink.DEPTH_ASPECT, 0, 0, 0, 0, 0.5f, 0, false, 0, 0, 0, 0);
        var color = new ClearSpec(DirectGlVulkanSink.COLOR_ASPECT, 1, 0.5f, 0, 1, 1, 0, true, -2, 3, 12, 8);
        sink.clearFramebuffer(0, depth);
        sink.clearFramebuffer(custom, color);

        var scale = new BlitRegion(2, 4, 10, 8, 31, 0, 1, 16, DirectGlVulkanSink.COLOR_ASPECT, true);
        var copy = new BlitRegion(0, 0, 32, 16, 0, 0, 32, 16,
                DirectGlVulkanSink.DEPTH_ASPECT | DirectGlVulkanSink.STENCIL_ASPECT, false);
        sink.blitFramebuffer(0, custom, scale);
        sink.blitFramebuffer(custom, 0, copy);

        assertEquals(List.of(new Clear(0, depth), new Clear(42, color)), clears);
        assertEquals(List.of(new Blit(0, 42, scale), new Blit(42, 0, copy)), blits);
    }
}
