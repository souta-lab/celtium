package org.soutalab.celtium.core.glcompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
