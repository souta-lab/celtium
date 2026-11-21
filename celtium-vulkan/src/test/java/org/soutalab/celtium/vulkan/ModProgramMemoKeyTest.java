package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether the one-entry mod-program prepare memo may answer without consulting the
 * pipeline map.
 * <p>
 * A re-link replaces a program's SPIR-V arrays in place - the registry mutates the same Program object, so
 * the program, its id and every variant parameter survive a resource reload unchanged while the bytes do
 * not. A memo keyed on anything but the arrays themselves therefore cannot see the reload at all: it keeps
 * handing back the pipeline built from the pre-link bytes, with the pre-link samplers and bindings, for as
 * long as that program is the only one prepared. The memo is keyed by a {@link ModPrograms.ModProgramKey}
 * for exactly the map's reason, and these pins hold the two sides of that: the same arrays hit, a re-link's
 * new arrays miss.
 */
class ModProgramMemoKeyTest {

    private final Object like = new Object();
    private final byte[] vertex = {1, 2, 3};
    private final byte[] fragment = {4, 5, 6};

    private ModPrograms.ModProgramKey key(byte[] vertex, byte[] fragment, int formatId, Object like,
                                          long renderPass, int colorCount) {
        return new ModPrograms.ModProgramKey(vertex, fragment, formatId, like, renderPass, colorCount);
    }

    @Test
    void sameArraysAndVariantHit() {
        ModPrograms.ModProgramKey memo = key(this.vertex, this.fragment, 7, this.like, 42L, 1);
        assertTrue(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, this.like, 42L, 1), memo, true),
                "an unchanged program prepared twice in a row is the memo's whole purpose");
    }

    @Test
    void relinkedArraysMiss() {
        // The reload shape: identical content, identical parameters, new array objects - a re-link compiled
        // the same source again. Content equality must not rescue the hit; only identity may answer.
        ModPrograms.ModProgramKey memo = key(this.vertex, this.fragment, 7, this.like, 42L, 1);
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}, 7, this.like, 42L, 1), memo, true),
                "a re-link's fresh arrays are a different program generation even when the bytes match");
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, new byte[]{9, 9, 9}, 7, this.like, 42L, 1), memo, true),
                "a fragment-only re-link misses on the fragment identity alone");
    }

    @Test
    void changedVariantMisses() {
        ModPrograms.ModProgramKey memo = key(this.vertex, this.fragment, 7, this.like, 42L, 1);
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 8, this.like, 42L, 1), memo, true), "format");
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, new Object(), 42L, 1), memo, true), "variant");
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, this.like, 43L, 1), memo, true), "render pass");
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, this.like, 42L, 3), memo, true), "colour count");
    }

    @Test
    void emptyMemoMisses() {
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, this.like, 42L, 1), null, false),
                "the first prepare of a run has no memo to answer from");
        assertFalse(ModPrograms.celtium$modMemoHit(
                key(this.vertex, this.fragment, 7, this.like, 42L, 1),
                key(this.vertex, this.fragment, 7, this.like, 42L, 1), false),
                "a memo whose pipeline was forgotten at teardown must not answer");
    }
}
