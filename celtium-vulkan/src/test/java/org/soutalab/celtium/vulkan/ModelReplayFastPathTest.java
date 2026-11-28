package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay fast path may only skip work, never change a draw: a hit must mean every value its record carries is
 * exactly the one the unchanged path would resolve. These cases are that input list - the identities that decide which
 * objects the replay is about, the raw state inputs, the projection, the colour, the texture affine, the light-map
 * coordinate, the combine colour and the lit payload - plus the counters that make the hit rate measurable on a leg.
 * <p>
 * The record's matrix is deliberately not part of the tuple: it is the one thing a repeated replay is allowed to
 * change. The lit payload is compared as bytes rather than through its inputs because it is invariant under a change
 * of modelview - see {@link #theLitPayloadIsComparedAsBytesBecauseItsOutputIsInvariant()}.
 */
class ModelReplayFastPathTest {

    /** A probe with every identity and every value set, so each case changes exactly one thing. */
    private static ModelReplayFastPath.Probe filled() {
        return filled(new ModelReplayFastPath().probe());
    }

    private static ModelReplayFastPath.Probe filled(ModelReplayFastPath.Probe probe) {
        // The signature at values inside the domains the state mapping reads (stage 8, S8-a): the state is judged
        // in PipelineStateKey's packing, whose codes are exact inside those domains and share an escape slot
        // outside them, so a synthetic out-of-domain signature would test the escape, not the judgment.
        int[] signature = {
                1,                                  // BLEND
                768, 769, 1, 0,                     // SRC_RGB, DST_RGB, SRC_ALPHA, DST_ALPHA (GL factors)
                0x8006, 0x8006,                     // EQ_RGB, EQ_ALPHA (FUNC_ADD)
                7,                                  // COLOR_MASK
                1, Float.floatToIntBits(0.5f), 516, // ALPHA_TEST, ALPHA_REF, ALPHA_FUNC
                1,                                  // CULL
                1, 1, 515,                          // DEPTH_TEST, DEPTH_WRITE, DEPTH_FUNC
                1,                                  // LIGHTING
                8448, 8448,                         // ENV_MODE, ENV_RGB (MODULATE)
                1,                                  // WINDOW_TARGET
                0x0405, 0x0901                      // CULL_FACE (GL_BACK), FRONT_FACE (GL_CCW)
        };
        System.arraycopy(signature, 0, probe.signature, 0, ModelReplayFastPath.SIGNATURE);
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
        // The four versions the value half is decided by, at values no other case reuses: a probe that leaves them at
        // zero would take the proven path and make every value comparison below vacuous.
        probe.stateVersion = 41L;
        probe.projectionVersion = 42L;
        probe.texMatrixVersion = 43L;
        probe.envVersion = 44L;
        return probe;
    }

    /**
     * The same tuple with every version moved: the fallback the guard takes when a mutator ran between two replays.
     * A value test has to use this one, or the version would prove the bytes without comparing them.
     */
    private static ModelReplayFastPath.Probe refilled() {
        ModelReplayFastPath.Probe probe = filled();
        probe.stateVersion++;
        probe.projectionVersion++;
        probe.texMatrixVersion++;
        probe.envVersion++;
        return probe;
    }

    /** Notes a probe, handing the live arrays the guard would be given on a replay. */
    private static void note(ModelReplayFastPath fast, ModelReplayFastPath.Probe probe) {
        fast.note(probe, probe.projection, probe.light, probe.lightCoord, probe.combineColour);
    }

    private static void note(ModelReplayFastPath fast) {
        note(fast, filled());
    }

    /** The whole guard as the replay path asks it: the shape first, then the values with the live buffers. */
    private static boolean matches(ModelReplayFastPath fast, ModelReplayFastPath.Probe probe) {
        return fast.matchesShape(probe)
                && fast.matchesValues(probe, probe.projection, probe.lightCoord, probe.combineColour, probe.light);
    }

    @Test void anUnarmedCacheCannotAnswerAHit() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        assertFalse(fast.armed());
        assertFalse(fast.matchesShape(filled()));
        assertFalse(matches(fast, filled()));
    }

    @Test void theSameTupleHitsBothHalves() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        assertTrue(fast.armed());
        assertTrue(fast.matchesShape(filled()));
        assertTrue(matches(fast, filled()));
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
            assertFalse(matches(fast, changed), name + " must miss the whole guard too");
        }
    }

    @Test void everyValueIsPartOfTheInputs() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        // One element of each value array, at both ends: an off-by-one in a copy shows up as a hit that should miss.
        assertTrue(matches(fast, filled()));
        // Each signature slot an in-domain alternate that the judgment must tell apart (stage 8, S8-a): the keyed
        // slots through PipelineStateKey's packing, the residual slots raw. The window-target slot is not here -
        // it is a function of the target identity and has its own case below.
        int[] alternates = new int[ModelReplayFastPath.SIGNATURE];
        alternates[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = 770;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_DST_RGB] = 770;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_SRC_ALPHA] = 768;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_DST_ALPHA] = 1;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB] = 0x8007;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_EQ_ALPHA] = 0x8007;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK] = 11;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(0.25f);
        alternates[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC] = 517;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_CULL] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_TEST] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_WRITE] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_FUNC] = 516;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_LIGHTING] = 0;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_ENV_MODE] = 34160;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_ENV_RGB] = 0x84E7;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_CULL_FACE] = 0x0404;
        alternates[VulkanTerrainGpuBackend.SIGNATURE_FRONT_FACE] = 0x0903;
        for (int index = 0; index < ModelReplayFastPath.SIGNATURE; index++) {
            if (index == VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET) {
                continue;
            }
            ModelReplayFastPath.Probe changed = refilled();
            changed.signature[index] = alternates[index];
            assertFalse(matches(fast, changed), "signature slot " + index + " is part of the state");
        }
        for (int index : new int[] {0, 15}) {
            ModelReplayFastPath.Probe changed = refilled();
            changed.projection[index]++;
            assertFalse(matches(fast, changed), "projection slot " + index + " is part of the run");
        }
        for (int index = 0; index < 4; index++) {
            ModelReplayFastPath.Probe changed = refilled();
            changed.colour[index] += 0.125f;
            assertFalse(matches(fast, changed), "colour slot " + index + " is part of the record");
        }
        for (int index = 0; index < 6; index++) {
            ModelReplayFastPath.Probe changed = refilled();
            changed.affine[index] += 0.125f;
            assertFalse(matches(fast, changed), "affine slot " + index + " is part of the record");
        }
        for (int index = 0; index < 2; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.lightCoord[index] += 0.125f;
            assertFalse(matches(fast, changed), "light-map slot " + index + " is part of the record");
        }
        for (int index = 0; index < 20; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.light[index] += 0.125f;
            assertFalse(matches(fast, changed), "lit payload slot " + index + " is part of the record");
        }
        for (int index = 0; index < 4; index++) {
            ModelReplayFastPath.Probe changed = filled();
            changed.combineColour[index] += 0.125f;
            assertFalse(matches(fast, changed), "combine slot " + index + " is part of the record");
        }
    }

    @Test void theLitPayloadIsComparedAsBytesBecauseItsOutputIsInvariant() {
        // Measured lesson, leg r19b-fastpath: the payload is the inverse of the modelview's rotation times the light
        // directions the game set under that same rotation, so the twenty floats come out the same for every entity
        // orientation. An input-keyed guard (the light generation plus the rotation) therefore refused all 2,940,981
        // replays of the pack's scene, while the bytes were repeating. What a hit may compare is the bytes.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        ModelReplayFastPath.Probe repeated = filled();
        assertTrue(matches(fast, repeated), "the payload the next orientation resolves is the same twenty floats");
        ModelReplayFastPath.Probe changed = filled();
        changed.light[3] += 0.125f;
        assertFalse(matches(fast, changed), "a payload that really moved is a different record");
    }

    @Test void aValueThatIsAbsentIsNotCompared() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        // The unchanged path writes "no affine" as a record that ignores the buffer, so a stale buffer with the flag
        // clear is the same record. A flip in either direction is a different record.
        ModelReplayFastPath.Probe stale = refilled();
        stale.affineValid = false;
        stale.affine[0] = 99;                       // stale contents, which the record ignores
        assertFalse(matches(fast, stale), "an affine that appeared is a different record");
        note(fast, stale);
        assertTrue(matches(fast, stale));
        ModelReplayFastPath.Probe staleOther = filled();
        staleOther.affineValid = false;
        staleOther.affine[0] = -17;                 // a different stale buffer, same absent value
        assertTrue(matches(fast, staleOther));

        // Same rule for the light, the light-map coordinate, the colour and the combine.
        ModelReplayFastPath.Probe noLight = filled();
        noLight.lightValid = false;
        noLight.light[0] = 123;
        note(fast, noLight);
        ModelReplayFastPath.Probe noLightOther = filled();
        noLightOther.lightValid = false;
        noLightOther.light[0] = -123;
        assertTrue(matches(fast, noLightOther));
        ModelReplayFastPath.Probe withLight = filled();
        assertFalse(matches(fast, withLight), "a lit payload that appeared is a different record");
    }

    @Test void theCachedTupleDoesNotAliasTheCallersArray() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe live = filled(fast.probe());
        note(fast, live);
        // The caller writes its scratch on the next replay; a cached tuple that kept the reference would then compare
        // the scratch with itself and fake a hit for a value it never held.
        live.signature[0] = 0;
        live.projection[3] = 0;
        live.light[7] = 0;
        live.state[0] = 0;
        assertTrue(matches(fast, refilled()), "the tuple was copied, not referenced");
    }

    @Test void theStateArrayIsCopiedForTheHitPath() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe live = filled(fast.probe());
        note(fast, live);
        live.state[5] = -1;
        assertTrue(matches(fast, filled()), "the tuple a hit reads is the one that was copied");
        assertEquals(1005, fast.cached().state[5], "the record is written from the cached state, not the scratch");
    }

    @Test void hitsAndMissesAreCountedPerReason() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        assertEquals("hit=0 miss=0(shape=0 inputs=0 run=0) armed=true lastShape=none enabled=true"
                + " hitNanos=0 hitCalls=0 missNanos=0 missCalls=0", fast.describe());
        fast.noteHit();
        fast.noteMiss(ModelReplayFastPath.MISS_SHAPE);
        fast.noteMiss(ModelReplayFastPath.MISS_INPUTS);
        fast.noteMiss(ModelReplayFastPath.MISS_RUN);
        assertEquals("hit=1 miss=3(shape=1 inputs=1 run=1) armed=true lastShape=none enabled=true"
                + " hitNanos=0 hitCalls=0 missNanos=0 missCalls=0", fast.describe());
    }

    @Test void theTimingCensusIsSplitByAnswer() {
        // The measurement the next leg reads: the fast path's own cost per answer, so the enqueue bracket's total can
        // be split into this guard and the preamble around it without a second build.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        fast.noteAttempt(120L, true);
        fast.noteAttempt(30L, true);
        fast.noteAttempt(400L, false);
        String line = fast.describe();
        assertTrue(line.contains("hitNanos=150 hitCalls=2"), line);
        assertTrue(line.contains("missNanos=400 missCalls=1"), line);
    }

    @Test void eachListKeepsItsOwnTuple() {
        // The measured shape (r18e-shape): the pack replays list A, then list B, then A again. A cache of "the previous
        // replay" misses every time there, so the tuple is per list - and each list's own replay must still hit.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast, filled(fast.probe()));
        ModelReplayFastPath.Probe b = filled(fast.probe());
        b.listId = 24017;
        assertFalse(fast.matchesShape(b), "B has not been resolved yet");
        note(fast, b);
        ModelReplayFastPath.Probe aAgain = filled();
        assertTrue(matches(fast, aAgain), "A's own tuple is still armed after B was recorded");
        assertEquals(24016, fast.cached().listId, "the hit names the list it matched, not the last one noted");
        ModelReplayFastPath.Probe bAgain = filled();
        bAgain.listId = 24017;
        assertTrue(matches(fast, bAgain));
    }

    @Test void twoListsThatCollideEvictEachOther() {
        // Direct-mapped: a collision is a miss, which takes the unchanged path; it must never read another list's
        // record. 24016 and 24016 + SLOTS share a slot.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast, filled(fast.probe()));
        ModelReplayFastPath.Probe colliding = filled(fast.probe());
        colliding.listId = 24016 + ModelReplayFastPath.SLOTS;
        assertFalse(fast.matchesShape(colliding));
        note(fast, colliding);
        ModelReplayFastPath.Probe aAgain = filled();
        assertFalse(matches(fast, aAgain), "A was evicted by the list that shares its slot");
    }

    @Test void aProvenVersionSkipsTheStateAndTheProjectionButNeverThePayload() {
        // The versions are the proof: when the state, projection and texture-matrix versions are the tuple's, the
        // signature, colour, affine and projection the caller would have captured are the tuple's by construction, so
        // they are neither read nor compared. The light-map coordinate, the payload and the combine are always compared:
        // the replay's own matrix and the frame's light state can move them without any version moving.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        ModelReplayFastPath.Probe stateMoved = filled();
        stateMoved.signature[0] += 7;
        stateMoved.projection[0] += 7;
        stateMoved.colour[0] += 0.5f;
        stateMoved.affine[0] += 0.5f;
        assertTrue(matches(fast, stateMoved),
                "a byte the version proves is not compared - the hit writes the tuple's own copy");

        for (String what : new String[] {"lightCoord", "light", "combineColour"}) {
            ModelReplayFastPath.Probe changed = filled();
            switch (what) {
                case "lightCoord": changed.lightCoord[0] += 0.125f; break;
                case "light": changed.light[19] += 0.125f; break;
                default: changed.combineColour[3] += 0.125f; break;
            }
            assertFalse(matches(fast, changed), what + " is compared even when every version is unchanged");
        }
    }

    @Test void aMovedVersionFallsBackToTheFullComparison() {
        // A coarse version may only cost work. With a version moved and every byte equal the fallback still serves the
        // hit - so the change that bumped it (a colour written and written back, say) does not disable the path - and
        // with a byte moved as well it refuses it.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        assertTrue(matches(fast, refilled()), "the bytes are equal, so the fallback serves the hit");

        ModelReplayFastPath.Probe changed = refilled();
        changed.colour[2] += 0.25f;
        assertFalse(matches(fast, changed), "the fallback compares the bytes, so a moved one is a miss");
    }

    @Test void everyVersionIsPartOfTheProof() {
        // Each of the four is the only proof of one group: moving it alone must send the guard to the full comparison,
        // which is observable because the tuple is then compared by bytes instead of trusted.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        for (String name : new String[] {"stateVersion", "projectionVersion", "texMatrixVersion", "envVersion"}) {
            ModelReplayFastPath.Probe moved = filled();
            switch (name) {
                case "stateVersion": moved.stateVersion++; break;
                case "projectionVersion": moved.projectionVersion++; break;
                case "texMatrixVersion": moved.texMatrixVersion++; break;
                default: moved.envVersion++; break;
            }
            assertFalse(ModelReplayFastPath.sameVersions(moved, fast.cached()), name + " is part of the proof");
            moved.signature[VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK] = 9;
            assertFalse(matches(fast, moved), name + " moving must fall back to the bytes, and the bytes moved");
        }
    }

    @Test void theProbeKeyIsPipelineStateKeysOwnPacking() {
        // The judgment's key is not a lookalike packing: driven by a signature read from a state mirror, it is
        // the value PipelineStateKey.of packs from that same mirror. The model state carries no shade input, so
        // the mirror's shade is set to the flat 0 the signature-driven packing uses, and the format fold is 0 on
        // both sides (a list's format is fixed and the run conditions discriminate it through the backend key).
        Lane2ImmediateState state = new Lane2ImmediateState();
        state.blendEnabled(true);
        state.blendFunc(770, 771, 1, 0);
        state.blendEquation(Lane2ImmediateState.BLEND_FUNC_ADD, Lane2ImmediateState.BLEND_FUNC_REVERSE_SUBTRACT);
        state.colorMask(0x5);
        state.depthFunc(515);
        state.alphaTestEnabled(true);
        state.alphaFunc(516, 0.5f);
        state.lightingEnabled(true);
        state.shadeModel(Lane2ImmediateState.SHADE_FLAT);
        state.cullEnabled(true);
        state.cullFace(VulkanCullState.GL_FRONT);
        state.frontFace(VulkanCullState.GL_CW);
        int[] signature = new int[ModelReplayFastPath.SIGNATURE];
        signature[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = state.blendEnabled() ? 1 : 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = state.blendSrcRgb();
        signature[VulkanTerrainGpuBackend.SIGNATURE_DST_RGB] = state.blendDstRgb();
        signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_ALPHA] = state.blendSrcAlpha();
        signature[VulkanTerrainGpuBackend.SIGNATURE_DST_ALPHA] = state.blendDstAlpha();
        signature[VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB] = state.blendEquationRgb();
        signature[VulkanTerrainGpuBackend.SIGNATURE_EQ_ALPHA] = state.blendEquationAlpha();
        signature[VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK] = state.colorMask();
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST] = state.alphaTestEnabled() ? 1 : 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(state.alphaFuncReference());
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC] = state.alphaFunc();
        signature[VulkanTerrainGpuBackend.SIGNATURE_CULL] = state.cullEnabled() ? 1 : 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_CULL_FACE] = state.cullFace();
        signature[VulkanTerrainGpuBackend.SIGNATURE_FRONT_FACE] = state.frontFace();
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_TEST] = state.depthTestEnabled() ? 1 : 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_WRITE] = state.depthMask() ? 1 : 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_FUNC] = state.depthFunc();
        signature[VulkanTerrainGpuBackend.SIGNATURE_LIGHTING] = state.lightingEnabled() ? 1 : 0;
        assertEquals(PipelineStateKey.of(state, 0).value(), ModelReplayFastPath.pipelineKeyOf(signature));
    }

    @Test void theKeyErasesWhatTheStateErases() {
        // With blending off, the state mapping substitutes 0 for every factor and the key erases them too, so a
        // factor that changed while blending is off is the same state - the raw comparison refused it, the key
        // judgment serves it, and the record a hit writes is the one the unchanged path resolves either way.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe off = filled();
        off.signature[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 0;
        note(fast, off);
        ModelReplayFastPath.Probe otherFactor = refilled();
        otherFactor.signature[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 0;
        otherFactor.signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = 772;
        assertTrue(matches(fast, otherFactor),
                "a factor blending never reads is erased by the state mapping and by the key alike");
    }

    @Test void anOutOfDomainFactorSharesTheEscapeAndTheState() {
        // PipelineStateKey's documented semantics outside a field's domain: one shared escape slot. For a blend
        // factor that is sound here because the state mapping answers -1 for every unknown factor and substitutes
        // one fixed fallback, so two unknowns are the same state even though the raw inputs differ.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        ModelReplayFastPath.Probe unknown = filled();
        unknown.signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = 12345;
        note(fast, unknown);
        ModelReplayFastPath.Probe otherUnknown = refilled();
        otherUnknown.signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = 23456;
        assertTrue(matches(fast, otherUnknown),
                "every unknown factor maps to the same state fallback, so the escape identifies them");
        ModelReplayFastPath.Probe known = refilled();
        known.signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = 768;
        assertFalse(matches(fast, known), "a defined factor is a different state");
    }

    @Test void theWindowTargetRidesOnTheTargetIdentity() {
        // The window-target input is the target identity restated (it is 1 exactly for the registry's main
        // target), and the shape guard compares that identity before values are asked - so the value half does
        // not carry the bit, and a probe differing only in it names the same target and the same state.
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        ModelReplayFastPath.Probe changed = refilled();
        changed.signature[VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET] = 0;
        assertTrue(matches(fast, changed), "the bit is the target identity restated, not a value of its own");
    }

    @Test void invalidateDropsTheTuple() {
        ModelReplayFastPath fast = new ModelReplayFastPath();
        note(fast);
        fast.invalidate();
        assertFalse(fast.armed());
        assertFalse(fast.matchesShape(filled()));
        assertFalse(matches(fast, filled()));
        note(fast);
        assertTrue(matches(fast, filled()), "the next slow replay arms it again");
    }
}
