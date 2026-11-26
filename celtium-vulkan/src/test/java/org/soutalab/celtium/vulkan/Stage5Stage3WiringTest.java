package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Stage 5 stage 3 (bindless) wiring contracts, pinned by source reading in the house style:
 * the connections this stage exists to make - the context owning the binding, the registry
 * feeding it, the model path resolving through it, the frame loop's completion gate advancing
 * its releases, the unified store under the indirect run, and the overflow counter reaching the
 * diagnostic line - are exactly the parts a device-less test cannot execute, so they are pinned
 * as source facts the conformance audit can re-check. Behaviour that CAN run headless lives in
 * {@link InstanceDataStoreTest} and the bindless package's own tests.
 */
class Stage5Stage3WiringTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String CONTEXT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";
    private static final String COPY_READ_SIDE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java";
    private static final String MODEL_REPLAY =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java";
    private static final String INDIRECT_RUNS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";
    private static final String STORE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /** Section 3.5: the context owns the binding, stamped on the merged slab currency. */
    @Test
    void contextOwnsTheBindingOnTheMergedCurrency() throws IOException {
        String context = read(CONTEXT);
        assertTrue(context.contains("BindlessTextureBinding.create(this, this::celtium$slabReleaseValue)"),
                "the binding is created by the context against the merged slab release currency");
        assertTrue(context.contains("celtium$bindlessBindingOrNull()"),
                "a non-creating peek exists for diagnostics and drains");
    }

    /** Plan section 2.1's fixed teardown order: bindless before the shared quad buffer and the ring. */
    @Test
    void contextDeletesTheBindingBeforeTheSharedQuadBufferAndTheRing() throws IOException {
        String context = read(CONTEXT);
        int delete = context.indexOf("public void delete()");
        assertTrue(delete >= 0, "the context teardown is declared");
        String body = context.substring(delete, Math.min(context.length(), delete + 60000));
        int bindless = body.indexOf("this.bindlessBinding.delete()");
        int quad = body.indexOf("this.sharedQuadIndexBuffer.delete()");
        int ring = body.indexOf("this.frameLinearRing.delete()");
        assertTrue(bindless >= 0 && bindless < quad && quad < ring,
                "teardown order: bindless, shared quad index buffer, frame-linear ring");
    }

    /** Section 3.5: the registry receives the binding at its one creation point. */
    @Test
    void registryReceivesTheBindingAtCreation() throws IOException {
        String copyReadSide = read(COPY_READ_SIDE);
        int registry = copyReadSide.indexOf("VulkanTextureRegistry celtium$registry()");
        assertTrue(registry >= 0, "the registry accessor is declared");
        String body = copyReadSide.substring(registry, registry + 1200);
        assertTrue(body.contains("new VulkanTextureRegistry(this.backend.context)")
                        && body.contains("setBindlessBinding(this.backend.context.bindlessBinding())"),
                "creation and injection happen together, before any texture exists");
    }

    /** Section 3.1: the model path resolves indices through the binding, and the old array is gone. */
    @Test
    void modelPathResolvesThroughTheBinding() throws IOException {
        String replay = read(MODEL_REPLAY);
        assertTrue(replay.contains("b.celtium$bindlessIndex(image, texture)"),
                "the model texture resolves through the bindless lookup");
        assertTrue(replay.contains("b.celtium$bindlessIndex(b.lightmapTexture, b.lightmapSourceGlId)"),
                "the lightmap resolves by its source GL name");
        assertTrue(replay.contains("bindlessBinding().descriptorSetLayout()"),
                "the instance pipelines are built on the binding's layout");
        assertTrue(replay.contains("bindlessBinding.descriptorSet()"),
                "the model run binds the binding's set");
        assertFalse(replay.contains("celtium$registerTexture"), "the old registration entry is gone");
        assertFalse(replay.contains("b.textureArray"), "no backend texture array field is read");
        String backend = read(BACKEND);
        assertFalse(backend.contains("celtium$registerTexture"), "the backend registration entry is gone");
        assertFalse(backend.contains("VulkanTextureArray"), "the backend names the deleted class nowhere");
        assertFalse(Files.exists(findRepoRoot().resolve(
                        "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java")),
                "the per-backend texture array class itself is deleted");
    }

    /** Section 3.5: releases advance from the frame loop's completion gate, on the merged currency. */
    @Test
    void releasesAdvanceFromTheSlotWaitSuccessExits() throws IOException {
        String backend = read(BACKEND);
        assertEquals(4, occurrences(backend, "celtium$drainBindlessReleases()"),
                "one definition and a call at each of waitForSlot's three success exits");
        int helper = backend.indexOf("private void celtium$drainBindlessReleases()");
        assertTrue(helper >= 0, "the drain helper is declared");
        String body = backend.substring(helper, helper + 600);
        assertTrue(body.contains("celtium$bindlessBindingOrNull()")
                        && body.contains("binding.advance(this.context.celtium$slabCompletedValue())"),
                "the drain advances the existing binding against the merged completed value");
    }

    /** Section 3.2: the indirect run's store is the unified store, selected per window. */
    @Test
    void recordsStoreIsTheUnifiedStore() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("InstanceDataStore.create(this.context)"),
                "the store is created through the unified store's factory");
        assertTrue(backend.contains("this.drawRecords.selectSlot(this.frameSlot)"),
                "each window selects the frame slot being built");
        assertTrue(backend.contains("this.drawRecords.bindLightmap("),
                "the frame's lightmap is bound into the unified set 1");
        assertFalse(backend.contains("new ImmediateDrawRecords(this.context)"),
                "the single-buffer store's production construction is gone");
        assertFalse(backend.contains("drawRecords.reset()"),
                "the per-window reset of the single-buffer store is gone");
        String runs = read(INDIRECT_RUNS);
        assertEquals(3, occurrences(runs, "b.drawRecords.descriptorDynamicOffset()"),
                "all three run families bind the records set with its slice's dynamic offset");
    }

    /** Section 3.2 plan B: the store's layout is the unified set 1 over ring slices, no own buffer. */
    @Test
    void storeLayoutIsTheUnifiedSet1OverRingSlices() throws IOException {
        String store = read(STORE);
        assertTrue(store.contains("VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC"),
                "binding 0 is the dynamic storage buffer the dynamic offsets address");
        assertTrue(store.contains("VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER"),
                "binding 1 is the lightmap sampler of the unified set 1");
        assertTrue(store.contains("FrameLinearRing"), "production slices come from the frame-linear ring");
        assertFalse(store.contains("VulkanHostBuffer"),
                "plan B owns no buffer object of its own (the plan A per-slot buffers are gone)");
    }

    /** Section 3.4: the overflow counter reaches the diagnostic summary, unconditionally. */
    @Test
    void diagnosticsCarryTheOverflowCounter() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("\" bindlessOverflowDraws=\" + bindlessOverflowDraws()"),
                "the summary line prints bindlessOverflowDraws");
        assertTrue(diagnostics.contains("binding.overflowRegistrations()"),
                "the counter reads the binding's refused registrations");
        assertFalse(diagnostics.contains("b.textureArray"), "the deleted array leaves the summary line");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }

    private static Path findRepoRoot() {
        Path direct = Paths.get("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java");
        if (Files.exists(direct)) {
            return Paths.get("");
        }
        return Paths.get("..");
    }
}
