package org.soutalab.celtium.vulkan.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlsmClientArrayState.VaoAttrib;

/**
 * M-5: the VAO attribute records live in GlsmClientArrayState with the stub's recording
 * semantics unchanged. The owner is a singleton, so the tests use VAO names no other test
 * touches and drop their records again on the way out.
 */
class GlsmVaoAttribStateTest {
    private static final int GL_FLOAT = 0x1406;
    private static final int VAO_A = 9101;
    private static final int VAO_B = 9102;

    @Test
    void aFreshVaoCarriesSixteenAttributesAtTheGlDefaults() {
        GlsmClientArrayState owner = GlsmClientArrayState.current();
        try {
            VaoAttrib[] attribs = owner.attribsFor(VAO_A);
            assertEquals(GlsmClientArrayState.MAX_VERTEX_ATTRIBS, attribs.length);
            for (VaoAttrib attrib : attribs) {
                assertNotNull(attrib);
                assertEquals(4, attrib.size, "GL's default attribute size");
                assertEquals(GL_FLOAT, attrib.type, "GL's default attribute type");
                assertEquals(0, attrib.stride);
                assertEquals(0L, attrib.offset);
                assertEquals(0, attrib.buffer);
                assertFalse(attrib.enabled);
                assertFalse(attrib.pointerSet);
                assertFalse(attrib.normalized);
                assertFalse(attrib.integer);
            }
        } finally {
            owner.removeAttribs(VAO_A);
        }
    }

    @Test
    void recordsArePerVaoAndSurviveASwap() {
        GlsmClientArrayState owner = GlsmClientArrayState.current();
        try {
            VaoAttrib[] a = owner.attribsFor(VAO_A);
            a[1].size = 2;
            a[1].stride = 20;
            a[1].offset = 12L;
            a[1].buffer = 78;
            a[1].pointerSet = true;
            a[1].enabled = true;
            VaoAttrib[] b = owner.attribsFor(VAO_B);
            assertNotSame(a, b);
            assertEquals(4, b[1].size, "the other VAO keeps its own defaults");
            assertFalse(b[1].pointerSet);
            assertSame(a, owner.attribsFor(VAO_A), "rebinding returns the same live array");
            assertEquals(20, owner.attribsFor(VAO_A)[1].stride);
        } finally {
            owner.removeAttribs(VAO_A);
            owner.removeAttribs(VAO_B);
        }
    }

    @Test
    void boundAttribsFollowsTheBoundNameAndNeverHandsBackAnotherVaosArray() {
        GlsmClientArrayState owner = GlsmClientArrayState.current();
        try {
            owner.bindAttribs(VAO_A);
            assertSame(owner.attribsFor(VAO_A), owner.boundAttribs(VAO_A));
            assertSame(owner.attribsFor(VAO_B), owner.boundAttribs(VAO_B),
                    "asking for a different bound name rebinds instead of returning the cached array");
            assertSame(owner.attribsFor(VAO_B), owner.boundAttribs(VAO_B));
        } finally {
            owner.removeAttribs(VAO_A);
            owner.removeAttribs(VAO_B);
        }
    }

    @Test
    void removingTheBoundVaoDropsItsRecordsAndTheCache() {
        GlsmClientArrayState owner = GlsmClientArrayState.current();
        try {
            owner.bindAttribs(VAO_A);
            owner.boundAttribs(VAO_A)[0].stride = 32;
            owner.removeAttribs(VAO_A);
            VaoAttrib[] fresh = owner.boundAttribs(VAO_A);
            assertEquals(0, fresh[0].stride, "a deleted VAO's records go with it; the next use starts at defaults");
        } finally {
            owner.removeAttribs(VAO_A);
        }
    }
}
