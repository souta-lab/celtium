package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay fast path may only skip work, never change a draw: a hit must mean every value its record carries is
 * exactly the one the unchanged path would resolve. These cases are that input list - the identities that decide which
 * objects the replay is about, the raw state inputs, the projection, the colour, the texture affine, the light-map
 * coordinate and the lit payload - plus the counters that make the hit rate measurable on a leg.
 * <p>
 * The record's matrix is deliberately not part of the tuple: it is the one thing a repeated replay is allowed to
 * change, so {@code ModelView} does not appear here at all.
 */
class ModelReplayFastPathTest {

    /** A probe with every identity and every value set, so each case changes exactly one thing. */
    private static ModelReplayFastPath.Probe filled() {
        return filled(new ModelReplayFastPath().probe());
    }

    private static ModelReplayFastPath.Probe filled(ModelReplayFastPath.Probe probe) {
        for (int i = 0; i < ModelReplayFastPath.SIGNATURE; i++) {
            probe.signature[i] = 100 + i;
        }
        for (int i = 0; i < 16; i++) {
            probe.projection[i] = i;
            probe.state[i] = 1000 + i;
        }
        probe.colour[0] = 1;
        probe.colour[1] = 0.5f;
        probe.colour[2] = 0.25f;
        probe.colour[3] = 1;
        probe.affine[0] = 1;
        probe.affine[1] = 0;
        probe.affine[2] = 0;
        probe.affine[3] = 1;
        probe.affine[4] = 0.5f;
        probe.affine[5] = 0.25f;
        probe.lightCoord[0] = 0.5f;
        probe.lightCoord[1] = 0.75f;
        for (int i = 0; i < 20; i++) {
            probe.light[i] = i * 0.5f;
        }
        probe.combineColour[0] = 0.1f;
        probe.combineColour[1] = 0.2f;
        probe.combineColour[2] = 0.3f;
        probe.combineColour[3] = 1;
        probe.colourValid = true;
        probe.affineValid = true;
        probe.lightCoordValid = true;
        probe.lightValid = true;
        probe.combineValid = true;
        probe.listId = 24016;
        probe.texture = 23919;
        probe.renderPass = 0x1234L;
        probe.quads = 6;
        probe.textureIndex = 3;
        probe.lightmapIndex = 4;
        probe.alphaFunction = 0x204;
        probe.frameId = 77L;
        probe.target = 0x5f73abe3L;
        probe.stateKey = 0xDEADBEEFL;
        probe.meshTable = "meshes";
        probe.openGroup = "run";
        probe.instances = "instances";
        probe.registry = "registry";
        probe.lightmap = "lightmap";
        probe.combine = "combine";
        probe.firstIndex = 12;
        probe.vertexOffset = 34;
        probe.indexCount = 56;
        return probe;
    }

    private static void note(ModelReplayFastPath fast) {
        fast.note(filled());
    }

    @Test void anUnarmedCacheCannotAnswerAHit() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        assertFalse(fast.armed());
        assertFalse(fast.matchesShape(filled()));
        assertFalse(fast.matchesInputs(filled()));
    }

    @Test void theSameTupleHitsBothHalves() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        assertTrue(fast.armed());
        assertTrue(fast.matchesShape(filled()));
        assertTrue(fast.matchesInputs(filled()));
    }

    @Test void everyIdentityIsPartOfTheShape() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        // Each identity: changing only that field must break the shape half, and with it the whole guard.
        String[] names = {"frameId", "listId", "texture", "target", "pass", "meshTable", "openGroup", "instances",
                "registry", "lightmap", "combine"};
        for (String name : names) {
            ModelReplayFastPath.Probe changed = filled();
            switch (name) {
                case "frameId": changed.frameId = 78L; break;
                case "listId": changed.listId = 24017; break;
                case "texture": changed.texture = 24015; break;
                case "target": changed.target = 0x1L; break;
                case "pass": changed.pass = new Object(); break;
                case "meshTable": changed.meshTable = "other"; break;
                case "openGroup": changed.openGroup = "other-run"; break;
                case "instances": changed.instances = "other-instances"; break;
                case "registry": changed.registry = "other-registry"; break;
                case "lightmap": changed.lightmap = "other-lightmap"; break;
                case "combine": changed.combine = "other-combine"; break;
                default: fail("unhandled identity " + name);
            }
            assertFalse(fast.matchesShape(changed), name + " is part of the shape guard");
            assertFalse(fast.matchesInputs(changed), name + " must miss the whole guard too");
        }
    }

    @Test void everyValueIsPartOfTheInputs() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        // One element of each value array, at both ends: an off-by-one in a copy shows up as a hit that should miss.
        assertTrue(fast.matchesInputs(filled()) && fast.matchesShape(filled()));
        for (int index = 0; index < ModelReplayFastPath.SIGNATURE; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.signature[index]++;
            assertFalse(fast.matchesInputs(changed), "signature slot " + index + " is part of the state");
        }
        for (int index : new int[] {0, 15}) {
            ModelReplayFastPath.Probe changed = filled();
            changed.projection[index]++;
            assertFalse(fast.matchesInputs(changed), "projection slot " + index + " is part of the run");
        }
        for (int index = 0; index < 4; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.colour[index] += 0.125f;
            assertFalse(fast.matchesInputs(changed), "colour slot " + index + " is part of the record");
        }
        for (int index = 0; index < 6; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.affine[index] += 0.125f;
            assertFalse(fast.matchesInputs(changed), "affine slot " + index + " is part of the record");
        }
        for (int index = 0; index < 2; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.lightCoord[index] += 0.125f;
            assertFalse(fast.matchesInputs(changed), "light-map slot " + index + " is part of the record");
        }
        for (int index = 0; index < 20; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.light[index] += 0.125f;
            assertFalse(fast.matchesInputs(changed), "lit payload slot " + index + " is part of the record");
        }
        for (int index = 0; index < 4; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.combineColour[index] += 0.125f;
            assertFalse(fast.matchesInputs(changed), "combine slot " + index + " is part of the record");
        }
    }

    @Test void aValueThatIsAbsentIsNotCompared() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        // The unchanged path writes "no affine" as a record that ignores the buffer, so a stale buffer with the flag
        // clear is the same record. A flip in either direction is a different record.
        ModelReplayFastPath.Probe stale = filled();
        stale.affineValid = false;
        stale.affine[0] = 99;                       // stale contents, which the record ignores
        assertFalse(fast.matchesInputs(stale), "an affine that appeared is a different record");
        fast.note(stale);
        assertTrue(fast.matchesInputs(stale));
        ModelReplayFastPath.Probe staleOther = filled();
        staleOther.affineValid = false;
        staleOther.affine[0] = -17;                 // a different stale buffer, same absent value
        assertTrue(fast.matchesInputs(staleOther));

        // Same rule for the light, the light-map coordinate, the colour and the combine.
        ModelReplayFastPath.Probe noLight = filled();
        noLight.lightValid = false;
        noLight.light[0] = 123;
        fast.note(noLight);
        ModelReplayFastPath.Probe noLightOther = filled();
        noLightOther.lightValid = false;
        noLightOther.light[0] = -123;
        assertTrue(fast.matchesInputs(noLightOther));
        ModelReplayFastPath.Probe withLight = filled();
        assertFalse(fast.matchesInputs(withLight), "a lit payload that appeared is a different record");
    }

    @Test void theCachedTupleDoesNotAliasTheCallersArray() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe live = filled(fast.probe());
        fast.note(live);
        // The caller writes its scratch on the next replay; a cached tuple that kept the reference would then compare
        // the scratch with itself and fake a hit for a value it never held.
        live.signature[0] = 0;
        live.projection[3] = 0;
        live.light[7] = 0;
        live.state[0] = 0;
        ModelReplayFastPath.Probe same = filled();
        assertTrue(fast.matchesInputs(same), "the tuple was copied, not referenced");
    }

    @Test void theStateArrayIsCopiedForTheHitPath() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe live = filled(fast.probe());
        fast.note(live);
        live.state[5] = -1;
        assertTrue(fast.matchesInputs(filled()), "the tuple a hit reads is the one that was copied");
        assertEquals(1005, fast.cached().state[5], "the record is written from the cached state, not the scratch");
    }

    @Test void hitsAndMissesAreCountedPerReason() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        assertEquals("hit=0 miss=0(shape=0 inputs=0 run=0) armed=true lastShape=none enabled=true", fast.describe());
        fast.noteHit();
        fast.noteMiss(ModelReplayFastPath.MISS_SHAPE);
        fast.noteMiss(ModelReplayFastPath.MISS_INPUTS);
        fast.noteMiss(ModelReplayFastPath.MISS_RUN);
        assertEquals("hit=1 miss=3(shape=1 inputs=1 run=1) armed=true lastShape=none enabled=true", fast.describe());
    }

    @Test void eachListKeepsItsOwnTuple() {
        // The measured shape (r18e-shape): the pack replays list A, then list B, then A again. A cache of "the previous
        // replay" misses every time there, so the tuple is per list - and each list's own replay must still hit.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe a = filled(fast.probe());
        fast.note(a);
        ModelReplayFastPath.Probe b = filled(fast.probe());
        b.listId = 24017;
        assertFalse(fast.matchesShape(b), "B has not been resolved yet");
        fast.note(b);
        ModelReplayFastPath.Probe aAgain = filled();
        assertTrue(fast.matchesInputs(aAgain), "A's own tuple is still armed after B was recorded");
        assertEquals(24016, fast.cached().listId, "the hit names the list it matched, not the last one noted");
        ModelReplayFastPath.Probe bAgain = filled();
        bAgain.listId = 24017;
        assertTrue(fast.matchesInputs(bAgain));
    }

    @Test void twoListsThatCollideEvictEachOther() {
        // Direct-mapped: a collision is a miss, which takes the unchanged path; it must never read another list's
        // record. 24016 and 24016 + SLOTS share a slot.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe a = filled(fast.probe());
        fast.note(a);
        ModelReplayFastPath.Probe colliding = filled(fast.probe());
        colliding.listId = 24016 + ModelReplayFastPath.SLOTS;
        assertFalse(fast.matchesShape(colliding));
        fast.note(colliding);
        ModelReplayFastPath.Probe aAgain = filled();
        assertFalse(fast.matchesInputs(aAgain), "A was evicted by the list that shares its slot");
    }

    @Test void invalidateDropsTheTuple() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        fast.invalidate();
        assertFalse(fast.armed());
        assertFalse(fast.matchesShape(filled()));
        assertFalse(fast.matchesInputs(filled()));
        fast.note(filled());
        assertTrue(fast.matchesInputs(filled()), "the next slow replay arms it again");
    }
}
