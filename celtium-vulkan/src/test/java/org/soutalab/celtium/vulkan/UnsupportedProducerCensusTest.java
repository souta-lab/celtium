package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlsmFrontend;

/**
 * DP-08-c: the draw and compute producers no route serves - the indirect draws, the pixel-transfer calls,
 * {@code glArrayElement}, {@code glRects}, {@code glDispatchCompute} - stay visible rather than vanishing.
 * <p>
 * None of them is served, and this test does not pretend otherwise. What it pins is the visibility the
 * disposition requires: every one is registered as a no-op entry point in the stub's table, so a call that
 * arrives through the trampoline is counted by name in the no-op census; and every drawing one is in the
 * drawing-name set, so a call the transformer answers is named by {@code CELTIUM_GL_SURFACE_DRAWING}. A
 * name dropped from either registration would fall out of both censuses and read as if it had never been
 * called - the failure this audit keeps finding.
 */
class UnsupportedProducerCensusTest {

    /** The producers the draw-path audit named, none of which has a serving route. */
    private static final String[] PRODUCERS = {
            "glDrawArraysIndirect", "glMultiDrawArraysIndirect", "glMultiDrawElementsIndirect",
            "glDrawElementsIndirect", "glBitmap", "glDrawPixels", "glCopyPixels", "glArrayElement",
            "glRects", "glDispatchCompute",
    };

    @Test
    void everyUnservedProducerIsCountedAsANoopEntryPoint() {
        for (String name : PRODUCERS) {
            assertTrue(GlsmFrontend.isNoopEntryPoint(name),
                    name + " is not registered as a no-op entry point, so a trampoline call to it could"
                            + " never be counted and the call would be invisible");
        }
    }

    @Test
    void everyUnservedDrawingProducerIsInTheDrawingCensus() {
        for (String name : PRODUCERS) {
            if ("glDispatchCompute".equals(name)) {
                continue;
            }
            assertTrue(GlsmFrontend.noopDrawingNames().contains(name),
                    name + " is not in the drawing census, so the transformer's drawing line would not"
                            + " name it and its geometry loss would read as 'none'");
        }
    }

    @Test
    void dispatchComputeIsCountedButIsNotDrawing() {
        // Compute produces no geometry for the frame; filing it under drawing would make the drawing line
        // cry wolf. It is visible through the no-op census and the transformer's unrouted line instead.
        assertFalse(GlsmFrontend.noopDrawingNames().contains("glDispatchCompute"),
                "a compute dispatch is not missing geometry and must not be reported as one");
        assertTrue(GlsmFrontend.isNoopEntryPoint("glDispatchCompute"),
                "but it is still counted by name when it arrives");
    }
}
