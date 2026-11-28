package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.soutalab.celtium.vulkan.NativeTextureBindings;
import org.soutalab.celtium.vulkan.GlsmMatrixMode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matrix mode's single ownership after the E5 seat A move (Neon E4 ruling 2026-11-28;
 * rewritten from the M-3 {@code GlsmMatrixModeOwnershipTest}). The owner is the Lane 2 mirror
 * household: {@link Lane2ImmediateState} holds the mode in GL enum values and converts to and
 * from its ordinals, {@link Lane2StateMirror}'s accessors delegate to that field, and the old
 * GLSM record forwards to the mirror until it is deleted at E4-c. These tests pin the
 * conversion, the cross-route visibility the former double ownership lacked, and the isolation
 * of standalone states (production has exactly one state instance: the mirror's).
 */
class MatrixModeOwnershipTest {

    private Lane2StateMirror mirror;
    private Lane2ImmediateState state;

    @BeforeEach
    void resetMirror() {
        this.mirror = Lane2StateMirror.current();
        this.mirror.reset();
        this.state = this.mirror.immediate();
    }

    @AfterEach
    void restoreMirror() {
        this.mirror.reset();
    }

    @Test
    void theProductionStateStartsInModelviewOnAllSides() {
        assertEquals(Lane2ImmediateState.MATRIX_MODE_MODELVIEW, this.state.matrixMode());
        assertEquals(0x1700, this.mirror.matrixMode());
        assertEquals(0x1700, GlsmMatrixMode.current().get());
    }

    @Test
    void theSetterRoundTripsThroughTheMirror() {
        this.state.matrixMode(Lane2ImmediateState.MATRIX_MODE_PROJECTION);
        assertEquals(0x1701, this.mirror.matrixMode());
        assertEquals(0x1701, GlsmMatrixMode.current().get());
        assertEquals(Lane2ImmediateState.MATRIX_MODE_PROJECTION, this.state.matrixMode());
        this.state.matrixMode(Lane2ImmediateState.MATRIX_MODE_TEXTURE);
        assertEquals(0x1702, this.mirror.matrixMode());
        assertEquals(Lane2ImmediateState.MATRIX_MODE_TEXTURE, this.state.matrixMode());
        this.state.matrixMode(Lane2ImmediateState.MATRIX_MODE_MODELVIEW);
        assertEquals(0x1700, this.mirror.matrixMode());
        assertEquals(Lane2ImmediateState.MATRIX_MODE_MODELVIEW, this.state.matrixMode());
    }

    @Test
    void writesOnOneRouteAreVisibleOnTheOther() {
        // The stub route's write (SEM_MATRIX_MODE's form, through the forwarding record) is
        // what the funnel's state reads back.
        GlsmMatrixMode.current().set(0x1702);
        assertEquals(Lane2ImmediateState.MATRIX_MODE_TEXTURE, this.state.matrixMode());
        // And the funnel's setter is what the stub route's redundant-call skip checks.
        this.state.matrixMode(Lane2ImmediateState.MATRIX_MODE_PROJECTION);
        assertTrue(GlsmMatrixMode.current().isCurrent(0x1701));
    }

    @Test
    void stackSelectionFollowsTheSharedRecord() {
        GlsmMatrixMode.current().set(0x1702);
        this.state.translate(2.0, 3.0, 4.0);
        float[] texture = this.state.matrix(Lane2ImmediateState.MATRIX_MODE_TEXTURE);
        assertEquals(2.0f, texture[12], 1e-6f);
        assertEquals(3.0f, texture[13], 1e-6f);
        assertEquals(4.0f, texture[14], 1e-6f);
        assertArrayEquals(identity(), this.state.matrix(Lane2ImmediateState.MATRIX_MODE_MODELVIEW));
    }

    @Test
    void aStandaloneStateIsIsolatedFromTheSharedRecord() {
        NativeRasterState raster = new NativeRasterState();
        Lane2ImmediateState standalone = new Lane2ImmediateState(raster, new NativeTextureBindings(raster));
        assertEquals(Lane2ImmediateState.MATRIX_MODE_MODELVIEW, standalone.matrixMode());
        GlsmMatrixMode.current().set(0x1702);
        assertEquals(Lane2ImmediateState.MATRIX_MODE_MODELVIEW, standalone.matrixMode());
        standalone.matrixMode(Lane2ImmediateState.MATRIX_MODE_PROJECTION);
        assertEquals(0x1702, this.mirror.matrixMode());
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
