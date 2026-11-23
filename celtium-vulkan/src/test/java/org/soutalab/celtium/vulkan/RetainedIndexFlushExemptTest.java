package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flight A1's retained-index exemption, pinned the way the eviction guard is pinned: the counter's four
 * adjustment sites live inside device-bound methods, so the wiring is a source contract, and the two rules
 * that were extracted as statics - the draw-side subtraction (covered by {@link PendingIndicesAccountingTest})
 * and the flush-end counting predicate - are exercised directly.
 * <p>
 * The design's failure mode is disagreement between the sites: the accept path skipping a retained group's
 * indices while any later site still subtracts, recounts or re-adds them recreates the {@code -3168} defect
 * (counter driven negative, threshold dead) with the roles reversed, and skipping the re-add guard instead
 * inflates the counter with indices no window holds. These assertions pin each site to the group's stamp,
 * and the stamp to the one decision point at group creation.
 * <p>
 * What these tests can prove: the wiring and the predicate's arithmetic. They prove nothing about a frame -
 * whether the exemption actually removes the mid-acceptance flush from the cloud path is the A/B leg's
 * question, not this class's.
 */
class RetainedIndexFlushExemptTest {

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
    }

    private static String groupSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java"));
    }

    @Test
    void theSwitchReadsTheRegisteredProperty() throws IOException {
        String source = backendSource();
        int flag = source.indexOf("RETAINED_INDEX_FLUSH_EXEMPT");
        assertTrue(flag >= 0, "the backend declares the exemption switch");
        int read = source.indexOf(
                "Boolean.getBoolean(\"celtium.vulkan.retainedIndexFlushExempt\")", flag);
        assertTrue(read > flag, "the switch reads celtium.vulkan.retainedIndexFlushExempt (default off)");
    }

    @Test
    void theSwitchDefaultsOff() throws Exception {
        Field field = VulkanTerrainGpuBackend.class.getDeclaredField("RETAINED_INDEX_FLUSH_EXEMPT");
        field.setAccessible(true);
        assertFalse(field.getBoolean(null),
                "with the property unset the exemption is off and the accounting is the old one");
    }

    @Test
    void theStampIsDecidedOnceAtGroupCreation() throws IOException {
        String source = backendSource();
        int decided = source.indexOf(
                "this.currentGroupIndexFlushExempt = RETAINED_INDEX_FLUSH_EXEMPT && retained != null;");
        assertTrue(decided >= 0,
                "group creation decides the stamp from the switch and the retained offer together");
    }

    @Test
    void theAcceptAdditionHonoursTheStamp() throws IOException {
        String source = backendSource();
        int guard = source.indexOf("if (!this.currentGroupIndexFlushExempt) {");
        assertTrue(guard >= 0, "the accept path guards its counter addition with the stamp");
        int addition = source.indexOf("this.pendingImmediateIndices += indices.length;", guard);
        assertTrue(addition > guard, "the guarded statement is the pending-index addition");
    }

    @Test
    void theCloseStampsTheGroupFromTheScratch() throws IOException {
        String source = backendSource();
        int stamp = source.indexOf("group.indexFlushExempt = this.currentGroupIndexFlushExempt;");
        assertTrue(stamp >= 0, "closing a group copies the scratch stamp onto it");
    }

    @Test
    void theDrawSubtractionAdmitsTheExemptCase() throws IOException {
        String source = backendSource();
        int call = source.indexOf("group.modelRun != null || group.indexFlushExempt");
        assertTrue(call >= 0,
                "the draw side passes model runs and exempt groups to the subtraction as never-counted");
    }

    @Test
    void theRecomputeUsesTheCountingPredicate() throws IOException {
        String source = backendSource();
        int use = source.indexOf("if (celtium$countsTowardPendingIndices(kept)) {");
        assertTrue(use >= 0, "the flush-end recompute counts a kept group only through the predicate");
    }

    @Test
    void bothAbortReAddsHonourTheStamp() throws IOException {
        String source = backendSource();
        String guard = "if (!group.indexFlushExempt) {";
        int first = source.indexOf(guard);
        assertTrue(first >= 0, "an abort re-add guards its counter addition with the stamp");
        int second = source.indexOf(guard, first + guard.length());
        assertTrue(second > first,
                "both abort re-adds (the accept path's catch and the window flush's own) are guarded");
        assertTrue(source.indexOf(guard, second + guard.length()) < 0,
                "and there is no third, unguarded re-add shape hiding behind them");
    }

    @Test
    void theGroupCarriesTheStampAndReuseClearsIt() throws IOException {
        String source = groupSource();
        assertTrue(source.contains("boolean indexFlushExempt;"),
                "ImmediateGroup carries the stamp as a field");
        int clear = source.indexOf("void clearForReuse()");
        assertTrue(clear >= 0, "the group has its reuse reset");
        int reset = source.indexOf("this.indexFlushExempt = false;", clear);
        assertTrue(reset > clear,
                "reuse clears the stamp - a pooled group must not inherit the previous group's accounting");
    }

    @Test
    void aCountedGroupCountsTowardTheRecompute() throws Exception {
        assertTrue(VulkanTerrainGpuBackend.celtium$countsTowardPendingIndices(group()),
                "a plain group with a mesh is part of what the window still holds");
    }

    @Test
    void anExemptGroupDoesNotCountTowardTheRecompute() throws Exception {
        ImmediateGroup group = group();
        group.indexFlushExempt = true;
        assertFalse(VulkanTerrainGpuBackend.celtium$countsTowardPendingIndices(group),
                "an exempt retained group was never added, so the recompute must not recount it");
    }

    @Test
    void aModelRunDoesNotCountTowardTheRecompute() throws Exception {
        ImmediateGroup group = group();
        group.modelRun = new VulkanModelInstances.Run(null, 0, 0L, 0L, 0L);
        assertFalse(VulkanTerrainGpuBackend.celtium$countsTowardPendingIndices(group),
                "a model-run group stands outside the recompute, as it did before the predicate was extracted");
    }

    @Test
    void aMeshlessGroupDoesNotCountTowardTheRecompute() throws Exception {
        ImmediateGroup group = group();
        group.mesh = null;
        assertFalse(VulkanTerrainGpuBackend.celtium$countsTowardPendingIndices(group),
                "a group without a mesh has no indices to count");
    }

    /** A group with a real (device-free, shell) mesh, the shape the stamp tests need. */
    private static ImmediateGroup group() throws Exception {
        VulkanDynamicMesh mesh = (VulkanDynamicMesh) unsafe().allocateInstance(VulkanDynamicMesh.class);
        setField(VulkanDynamicMesh.class, mesh, "indexCount", Integer.valueOf(6));
        return ImmediateGroup.obtain(mesh, new float[16], new float[16],
                VulkanImmediateFormats.ITEM, 7, 11L, null, 0L);
    }

    private static Unsafe unsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
