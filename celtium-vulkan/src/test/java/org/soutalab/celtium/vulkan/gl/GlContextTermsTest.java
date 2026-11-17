package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlContextTerms.Terms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The context-terms decision (GLVERSION-DESIGN-2026-11-17.md): the reported version is a property of
 * the created context, decided from the creation request in force. The default terms must reproduce
 * the pre-terms answers exactly, and only a core-profile request at or above the backed cap (3.3)
 * earns the core terms - the cap, never the requested number.
 */
class GlContextTermsTest {

    @AfterEach
    void reset() {
        GlContextTerms.resetForTest();
    }

    @Test
    void defaultTermsAreTheBh35Floor() {
        Terms terms = GlContextTerms.current();
        assertEquals("3.0.0 Celtium", terms.versionString());
        assertEquals(3, terms.major());
        assertEquals(0, terms.minor());
        assertFalse(terms.coreProfile());
        assertTrue(terms.profileMask() < 0, "default terms make no profile claim");
        assertFalse(GlContextTerms.latched());
    }

    @Test
    void compatibilityRequestKeepsTheFloor() {
        // The ambient default (forge_early.cfg's standing 4.6 compatibility request) included: a
        // compatibility context promises the whole legacy surface, whose backed floor is 3.0.
        Terms terms = GlContextTerms.resolve(4, 6, true);
        assertEquals("3.0.0 Celtium", terms.versionString());
        assertFalse(terms.coreProfile());
    }

    @Test
    void coreRequestAtOrAboveTheCapEarnsTheCoreTerms() {
        for (int[] request : new int[][] {{3, 3}, {3, 4}, {4, 1}, {4, 6}}) {
            Terms terms = GlContextTerms.resolve(request[0], request[1], false);
            assertEquals("3.3.0 Celtium", terms.versionString(),
                    "request " + request[0] + "." + request[1]);
            assertEquals(3, terms.major());
            assertEquals(3, terms.minor());
            assertTrue(terms.coreProfile());
            assertEquals(GlContextTerms.CORE_PROFILE_BIT, terms.profileMask());
        }
    }

    @Test
    void coreRequestBelowTheCapKeepsTheFloor() {
        // This design claims exactly one core point; a 3.1/3.2 core request is not silently upgraded
        // to a claim nobody enumerated.
        for (int[] request : new int[][] {{2, 1}, {3, 0}, {3, 1}, {3, 2}}) {
            Terms terms = GlContextTerms.resolve(request[0], request[1], false);
            assertEquals("3.0.0 Celtium", terms.versionString(),
                    "request " + request[0] + "." + request[1]);
            assertFalse(terms.coreProfile());
        }
    }

    @Test
    void latchIsFirstWins() {
        GlContextTerms.noteContextRequest(4, 6, false);
        assertTrue(GlContextTerms.latched());
        assertEquals("3.3.0 Celtium", GlContextTerms.versionString());

        // A later, different request must not rewrite the terms of the context that already exists.
        GlContextTerms.noteContextRequest(4, 6, true);
        assertEquals("3.3.0 Celtium", GlContextTerms.versionString());
        assertTrue(GlContextTerms.current().coreProfile());
    }

    @Test
    void latchOfACompatibilityRequestKeepsTheFloor() {
        GlContextTerms.noteContextRequest(4, 6, true);
        assertTrue(GlContextTerms.latched());
        assertEquals("3.0.0 Celtium", GlContextTerms.versionString());
    }
}
