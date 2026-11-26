package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prescription A of the Tier-A disable diagnosis (2026-11-26): device-side
 * slab capacity is a per-section refusal, not an engine fault. The demand
 * ceiling is structural (passes x logical slabs against one shared pool cap),
 * so reaching it must classify the registration REJECTED — with the store
 * registration rolled back — and must skip an unmaterializable slab for one
 * frame in the draw loop, instead of permanently disabling the pass' engine.
 * The executor needs a Vulkan device, so the decision sites are pinned as
 * source contracts (the repo's idiom for device-bound decisions).
 */
class TierASlabCapacityTest {

    private static String engineSource() throws IOException {
        return readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java");
    }

    /** The upload body: from its signature to the unregister delegate that follows it. */
    private static String uploadRegion(String source) {
        int start = source.indexOf("private TerrainSectionStore.Registration upload(");
        int end = source.indexOf("TerrainSectionStore.Registration unregister(", start);
        assertTrue(start >= 0 && end > start, "the upload region is located");
        return source.substring(start, end);
    }

    /** The attempt core: from its signature to the upload body that follows it. */
    private static String attemptRegion(String source) {
        int start = source.indexOf("private RegisterAttempt attemptRegister(");
        int end = source.indexOf("private TerrainSectionStore.Registration upload(", start);
        assertTrue(start >= 0 && end > start, "the attempt region is located");
        return source.substring(start, end);
    }

    /** The draw loop: from its signature to the buffer section that follows it. */
    private static String recordDrawsRegion(String source) {
        int start = source.indexOf("boolean recordDraws(VkCommandBuffer commandBuffer, int slotIndex)");
        int end = source.indexOf("int liveSlabBufferCount()", start);
        assertTrue(start >= 0 && end > start, "the recordDraws region is located");
        return source.substring(start, end);
    }

    /** The slab buffer constructor: from its signature to its first accessor. */
    private static String slabBufferConstructorRegion(String source) {
        int start = source.indexOf("SlabBuffer(long sizeBytes, int usageFlags)");
        int end = source.indexOf("long buffer() {", start);
        assertTrue(start >= 0 && end > start, "the SlabBuffer constructor region is located");
        return source.substring(start, end);
    }

    /** The allocator's serve refusal is classified at its call site, in a dedicated type — never by message text. */
    @Test
    void capacityIsClassifiedAtTheAllocatorCallSite() throws IOException {
        String source = engineSource();
        assertTrue(source.contains(
                        "static final class SlabCapacityException extends IllegalStateException"),
                "the capacity refusal has a dedicated classified type");
        String constructor = slabBufferConstructorRegion(source);
        int allocate = constructor.indexOf("allocator.allocate(");
        int wrap = constructor.indexOf("throw new SlabCapacityException(refusal);");
        assertTrue(allocate >= 0 && wrap > allocate,
                "the allocate call inside SlabBuffer is wrapped into the classified type");
        assertTrue(constructor.contains("catch (IllegalStateException refusal)"),
                "the wrap catches the allocator's refusal type at the call site");
        assertFalse(source.contains("cannot serve") || source.contains("out of memory"),
                "the classification never matches the refusal's message text");
    }

    /** On capacity, upload rolls the store registration back and returns the refusal instead of throwing into the disable catch. */
    @Test
    void uploadRollsBackTheStoreRegistrationOnCapacity() throws IOException {
        String upload = uploadRegion(engineSource());
        int caught = upload.indexOf("catch (SlabCapacityException capacity)");
        int rollback = upload.indexOf("this.store.unregister(registration.key, slabReleaseValue());");
        int missing = upload.indexOf(
                "noteMissing(registration.key, TerrainResidencyGate.Reason.ARENA_FULL)");
        int refused = upload.indexOf("return null;", caught);
        assertTrue(caught >= 0 && rollback > caught && missing > caught && refused > caught,
                "capacity in upload unregisters the section, reports it missing, and refuses");
        assertFalse(upload.contains("disable("),
                "upload itself never disables: capacity returns, other failures propagate");
    }

    /** A rolled-back upload classifies REJECTED through the existing null mapping, while unclassified failures still disable. */
    @Test
    void capacityClassifiesRejectedAndUnclassifiedFailuresStillDisable() throws IOException {
        String attempt = attemptRegion(engineSource());
        assertTrue(attempt.contains(
                        "registration != null ? RegisterOutcome.ACCEPTED : RegisterOutcome.REJECTED"),
                "upload's null (the capacity refusal) classifies REJECTED, keeping the legacy upload");
        assertTrue(attempt.contains("disable(\"register\", error)"),
                "the generic catch still disables on unclassified failures");
    }

    /** Residency is claimed only for a registration whose device copy actually materialized. */
    @Test
    void residencyIsNotedOnlyAfterMaterialization() throws IOException {
        String upload = uploadRegion(engineSource());
        int materialize = upload.indexOf("slabBuffer(registration.geometry.slabId)");
        int resident = upload.indexOf("noteResident(registration.key)");
        assertTrue(materialize >= 0 && resident > materialize,
                "noteResident stands after the slab materialization it certifies");
    }

    /** The draw loop skips a slab it cannot materialize for that frame only, and draws the rest. */
    @Test
    void recordDrawsSkipsASlabItCannotMaterialize() throws IOException {
        String draws = recordDrawsRegion(engineSource());
        int caught = draws.indexOf("catch (SlabCapacityException capacity)");
        int skip = draws.indexOf("continue;", caught);
        int bind = draws.indexOf("vkCmdBindVertexBuffers");
        assertTrue(caught >= 0 && skip > caught && bind > skip,
                "an unmaterializable slab is skipped and the remaining slabs still bind and draw");
        assertFalse(draws.contains("disable("),
                "the draw loop never disables over a materialization refusal");
    }

    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
}
