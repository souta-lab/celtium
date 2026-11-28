package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring records separation's wiring, pinned at the layers a CPU gate can reach, in the house
 * source-contract style (the failure shapes behind it need a device and a mod scene to reproduce
 * at runtime; their measured forms are in the separation records).
 * <p>
 * The separation has three load-bearing wirings. (1) The records store's production factory
 * defaults to the dedicated pool and keeps the shared frame ring as the named A/B arm - if the
 * default silently flipped back, geometry exhaustion would again take every record of a window
 * with it (the all-or-nothing freeze). (2) The queue discard frees each dropped group's mesh
 * before recycling the group object: {@code ImmediateGroup.recycle} releases no mesh, so a
 * discard without the free leaks the group's ring hold for the rest of the process and pins its
 * slot - the permanent-pin seat. (3) A group built for a non-main target never opens a ring
 * mesh: it waits across frames for its target's pass, so a ring mesh would hold its birth slot
 * across every cycle in between (F-2).
 */
class RingRecordsSeparationContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String STORE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    /**
     * The body of the method whose declaration contains {@code signature}, from its opening
     * brace to the matching close. Strings, char literals and comments are skipped while
     * matching, so braces inside them cannot end the body early.
     */
    private static String methodBody(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "declaration not found: " + signature);
        int open = source.indexOf('{', at);
        assertTrue(open >= 0, "no body opens after: " + signature);
        int depth = 0;
        int i = open;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(source, i, c);
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth--;
                i++;
                if (depth == 0) {
                    return source.substring(open, i);
                }
            } else {
                i++;
            }
        }
        throw new IllegalStateException("unbalanced braces after: " + signature);
    }

    private static int skipLiteral(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return source.length();
    }

    @Test
    @DisplayName("the records store defaults to the dedicated pool, shared ring kept as the named arm")
    void theStoreDefaultsToTheDedicatedPool() throws IOException {
        String body = methodBody(read(STORE), "static InstanceDataStore create(VulkanContext context)");
        assertTrue(body.contains("\"celtium.vulkan.recordsDedicatedPool\", \"true\""),
                "the dedicated pool is the default source; anything else re-shares the records'"
                        + " fate with the geometry ring");
        assertTrue(body.contains("new RecordsRingSource(new RecordsRing(context))"),
                "the default source is the records' own pool");
        assertTrue(body.contains("new FrameLinearRingSource(context.celtium$frameLinearRing())"),
                "the shared ring remains reachable as the A/B arm and the creation fallback");
    }

    @Test
    @DisplayName("the queue discard frees non-borrowed meshes before recycling their groups")
    void theDiscardFreesMeshesBeforeRecycling() throws IOException {
        String body = methodBody(read(BACKEND), "void celtium$discardImmediateGroups()");
        int free = body.indexOf("group.mesh.delete()");
        int recycle = body.indexOf("ImmediateGroup.recycle(group)");
        assertTrue(free >= 0, "a discard that only recycles leaks every dropped group's ring hold");
        assertTrue(body.contains("meshBorrowed"),
                "borrowed meshes are their lender's and must not be freed by the discard");
        assertTrue(recycle > free,
                "the mesh is freed before the group object returns to the freelist");
    }

    @Test
    @DisplayName("a group built for a non-main target never opens a ring mesh")
    void nonMainTargetGroupsSkipTheRing() throws IOException {
        String body = methodBody(read(BACKEND), "VulkanDynamicMesh celtium$newImmediateMesh()");
        int gate = body.indexOf("this.targetRegistry.currentIsMain()");
        int ring = body.indexOf("openRingMesh(");
        assertTrue(gate >= 0, "the mesh home is decided by the registry's routing answer");
        assertTrue(ring > gate,
                "the ring is opened only inside the main-target branch; a non-main group falls"
                        + " through to the arena and owning meshes, so it cannot pin a ring slot"
                        + " while it waits for its target's pass");
        assertTrue(body.contains("this.frame.frameArena.openMesh()"),
                "the arena remains the next home down for a group the ring does not take");
    }

    @Test
    @DisplayName("the verify line carries the ring's per-slot state and the records store's counters")
    void theVerifyLineCarriesTheSeparationInstruments() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        String line = methodBody(diagnostics, "String verificationSummary()");
        assertTrue(line.contains("\" frameRing=[\" + frameRingSummary() + \"]\""),
                "every CELTIUM_VERIFY sample names the shared ring's per-slot state, so a leg"
                        + " reads the time series instead of one terminal snapshot");
        assertTrue(line.contains("\" recordsStore=[\" + recordsStoreSummary() + \"]\""),
                "every CELTIUM_VERIFY sample names the records store's counters");
        String ring = methodBody(diagnostics, "private String frameRingSummary()");
        assertTrue(ring.contains("ring.used(slot)") && ring.contains("ring.highWater(slot)")
                        && ring.contains("ring.holds(slot)") && ring.contains("ring.resetDeferred(slot)"),
                "the ring segment reads used, high-water, holds and the deferred flag per slot:"
                        + " the four numbers that separate a held reset from plain pressure");
        assertTrue(ring.contains("celtium$frameLinearRingOrNull()"),
                "the ring is read through the non-creating peek, never created by the summary");
        String records = methodBody(diagnostics, "private String recordsStoreSummary()");
        assertTrue(records.contains("store.sourceKind()") && records.contains("store.demandBytes(slot)")
                        && records.contains("store.selectFailures(slot)")
                        && records.contains("store.refusalsNoSlice()")
                        && records.contains("store.refusalsGrow()")
                        && records.contains("store.refusalsBlock()"),
                "the records segment reads the pool kind, per-slot demand and select failures,"
                        + " and the refusal split - the diagnosis section 2.7 observation points");
    }
}
