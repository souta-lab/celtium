package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlsmMatrixMode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-3: the matrix mode's single ownership. {@link GlsmMatrixMode} owns the mode for both routes in
 * GL enum values; {@link VulkanGlState} holds no field of its own and converts to and from its
 * ordinals in its getter/setter. These tests pin the conversion, the cross-route visibility that
 * the former double ownership lacked, and the constructor's MODELVIEW initialisation that replaces
 * the old per-instance field's initial value.
 */
class GlsmMatrixModeOwnershipTest {

    @Test
    void aFreshStateStartsInModelviewOnBothSides() {
        VulkanGlState state = new VulkanGlState();
        assertEquals(VulkanGlState.MATRIX_MODE_MODELVIEW, state.matrixMode());
        assertEquals(0x1700, GlsmMatrixMode.current().get());
    }

    @Test
    void theSetterRoundTripsThroughTheSharedRecord() {
        VulkanGlState state = new VulkanGlState();
        state.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        assertEquals(0x1701, GlsmMatrixMode.current().get());
        assertEquals(VulkanGlState.MATRIX_MODE_PROJECTION, state.matrixMode());
        state.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        assertEquals(0x1702, GlsmMatrixMode.current().get());
        assertEquals(VulkanGlState.MATRIX_MODE_TEXTURE, state.matrixMode());
        state.matrixMode(VulkanGlState.MATRIX_MODE_MODELVIEW);
        assertEquals(0x1700, GlsmMatrixMode.current().get());
        assertEquals(VulkanGlState.MATRIX_MODE_MODELVIEW, state.matrixMode());
    }

    @Test
    void writesOnOneRouteAreVisibleOnTheOther() {
        VulkanGlState state = new VulkanGlState();
        // The stub route's write (SEM_MATRIX_MODE's form) is what the backend's state reads back.
        GlsmMatrixMode.current().set(0x1702);
        assertEquals(VulkanGlState.MATRIX_MODE_TEXTURE, state.matrixMode());
        // And the backend's setter is what the stub route's redundant-call skip checks.
        state.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        assertTrue(GlsmMatrixMode.current().isCurrent(0x1701));
    }

    @Test
    void stackSelectionFollowsTheSharedRecord() {
        VulkanGlState state = new VulkanGlState();
        GlsmMatrixMode.current().set(0x1702);
        state.translate(2.0, 3.0, 4.0);
        float[] texture = state.matrix(VulkanGlState.MATRIX_MODE_TEXTURE);
        assertEquals(2.0f, texture[12], 1e-6f);
        assertEquals(3.0f, texture[13], 1e-6f);
        assertEquals(4.0f, texture[14], 1e-6f);
        assertArrayEquals(identity(), state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
    }

    @Test
    void aNewStateReturnsTheSharedRecordToModelview() {
        VulkanGlState first = new VulkanGlState();
        first.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        assertEquals(0x1701, GlsmMatrixMode.current().get());
        VulkanGlState second = new VulkanGlState();
        assertEquals(VulkanGlState.MATRIX_MODE_MODELVIEW, second.matrixMode());
        assertEquals(0x1700, GlsmMatrixMode.current().get());
    }

    private static float[] identity() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }
}
