package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every change to {@code immediateGroups} must advance {@code groupQueueMutation}, because that stamp is the only thing
 * that makes {@link VulkanTerrainGpuBackend#countGroupsForTarget(long)}'s cached answer valid.
 * <p>
 * Item 342's diagnosis names the paths that did not: the native framebuffer composite ({@code NativeFramebufferCompositor}),
 * both model replay paths ({@code ModelReplay}'s display-list replay and its instanced model run), and this backend's own
 * kept-only rebuild at the end of {@code drawPendingImmediate}. Each added or removed a group outside
 * {@code closeCurrentImmediateGroup}, so the stamp stood still while the queue changed. The measured consequence: a
 * flush that had cached "0 groups for this target" kept taking its early return at {@code flushImmediateWindow}, the
 * group stayed in the queue undrawn for seconds (the log shows {@code queued=1} across many
 * {@code CELTIUM_BIND_ENTRY target=0x0 current=0x0} entries), and the next frame boundary destroyed it.
 * <p>
 * The scenarios below are that ordering, one per site: a cached count taken while the queue holds only another target's
 * group, then the site's own queue change, then the same question again. The last two are the diagnosis's §2.3 sequence
 * end to end and the rebuild's inverse - a drawn group must stop being counted.
 * <p>
 * What this proves: the invalidation happens with the mutation, at the level the queue and the count live. It does not
 * prove a composite or a model run reaches a GPU pass; that needs a device and a leg, which is item 342 §8's work.
 */
class GroupQueueMutationStampTest {

    /** The identity the measured drops were against (the menu's composite target, {@code [0:frames=1:batches=1]}). */
    private static final long COMPOSITE_TARGET = 0L;
    /** Some other pass's target: its group is what keeps the queue non-empty while target 0 is asked about. */
    private static final long OTHER_TARGET = 0x55L;

    /**
     * {@code NativeFramebufferCompositor.submit}'s add: the frame composite is the whole group and it is queued by the
     * compositor itself, not by {@code closeCurrentImmediateGroup}.
     */
    @Test
    void aQueuedCompositeInvalidatesACachedZero() throws Exception {
        VulkanTerrainGpuBackend backend = newBackend();
        backend.immediateGroups.add(group(OTHER_TARGET));

        // The flush asks about the bound target while only another target's group is queued: this caches 0.
        assertEquals(0, backend.countGroupsForTarget(COMPOSITE_TARGET), "no group for target 0 is queued yet");

        backend.celtium$queueImmediateGroup(group(COMPOSITE_TARGET));

        assertEquals(1, backend.countGroupsForTarget(COMPOSITE_TARGET),
                "the composite's add must invalidate the cached 0, or the flush returns early and the boundary drops it");
    }

    /** {@code ModelReplay}'s display-list replay add (one group per replayed quad list). */
    @Test
    void aQueuedDisplayListReplayInvalidatesACachedZero() throws Exception {
        VulkanTerrainGpuBackend backend = newBackend();
        backend.immediateGroups.add(group(OTHER_TARGET));
        assertEquals(0, backend.countGroupsForTarget(COMPOSITE_TARGET));

        backend.celtium$queueImmediateGroup(group(COMPOSITE_TARGET));

        assertEquals(1, backend.countGroupsForTarget(COMPOSITE_TARGET),
                "a replayed model's group must be visible to the count, not hidden behind the stale zero");
    }

    /** {@code ModelReplay}'s instanced model-run add (the pack-scale candidate in item 342 §8). */
    @Test
    void aQueuedModelRunInvalidatesACachedZero() throws Exception {
        VulkanTerrainGpuBackend backend = newBackend();
        backend.immediateGroups.add(group(OTHER_TARGET));
        assertEquals(0, backend.countGroupsForTarget(COMPOSITE_TARGET));

        backend.celtium$queueImmediateGroup(group(COMPOSITE_TARGET));

        assertEquals(1, backend.countGroupsForTarget(COMPOSITE_TARGET),
                "a model run is a queue addition of its own, so it owes the same invalidation");
    }

    /**
     * The diagnosis's §2.3 sequence end to end: cache 0, rebuild the queue to the kept groups, then add target 0's
     * group. Under the defect the rebuild and the add left the stamp at the cached mutation, so step 4 - "every later
     * flush of identity 0 answers 0" - followed.
     */
    @Test
    void theDiagnosisSequenceEndsWithTheGroupCounted() throws Exception {
        VulkanTerrainGpuBackend backend = newBackend();
        ImmediateGroup other = group(OTHER_TARGET);
        backend.immediateGroups.add(other);
        assertEquals(0, backend.countGroupsForTarget(COMPOSITE_TARGET));

        // The window drew nothing of target 0, so the kept-only rebuild keeps the other target's group in place.
        java.util.List<ImmediateGroup> kept = new ArrayList<ImmediateGroup>();
        kept.add(other);
        backend.celtium$retainQueuedImmediateGroups(kept);
        backend.celtium$queueImmediateGroup(group(COMPOSITE_TARGET));

        assertEquals(1, backend.countGroupsForTarget(COMPOSITE_TARGET),
                "the sequence must end with the composite visible to the next flush");
        assertEquals(2, backend.immediateGroups.size(), "both groups are queued, in arrival order");
    }

    /**
     * {@code drawPendingImmediate}'s rebuild in the other direction: the count of a target whose group the window just
     * drew must not survive the rebuild. The stale value here used to be too large rather than too small, which is the
     * same defect read backwards - a count that does not describe the queue.
     */
    @Test
    void theKeptOnlyRebuildStopsCountingTheGroupThatWasDrawn() throws Exception {
        VulkanTerrainGpuBackend backend = newBackend();
        ImmediateGroup composite = group(COMPOSITE_TARGET);
        ImmediateGroup other = group(OTHER_TARGET);
        backend.immediateGroups.add(composite);
        backend.immediateGroups.add(other);
        assertEquals(1, backend.countGroupsForTarget(COMPOSITE_TARGET), "the composite is queued, so it counts");

        // What the window leaves behind once it has drawn the composite.
        java.util.List<ImmediateGroup> kept = new ArrayList<ImmediateGroup>();
        kept.add(other);
        backend.celtium$retainQueuedImmediateGroups(kept);

        assertEquals(0, backend.countGroupsForTarget(COMPOSITE_TARGET),
                "the rebuild must invalidate the count, or the next flush believes a drawn group is still queued");
    }

    private static VulkanTerrainGpuBackend newBackend() throws Exception {
        Unsafe unsafe = unsafe();
        VulkanTerrainGpuBackend backend =
                (VulkanTerrainGpuBackend) unsafe.allocateInstance(VulkanTerrainGpuBackend.class);
        setField(VulkanTerrainGpuBackend.class, backend, "targetRegistry", new RenderTargetRegistry());
        setField(VulkanTerrainGpuBackend.class, backend, "immediateStatistics", new ImmediateStatistics());
        setField(VulkanTerrainGpuBackend.class, backend, "immediateGroups", new ArrayList<ImmediateGroup>());
        setField(VulkanTerrainGpuBackend.class, backend, "keptImmediateGroups", new ArrayList<ImmediateGroup>());
        // allocateInstance skips the field initialisers, so the count cache starts at 0/0/0 here - a state the real
        // backend never has (its initialisers set MIN_VALUE / -1). Without this, a first ask about target 0 reads as a
        // cache hit on an uncomputed 0 and the rebuild scenario below fails for a reason production cannot produce.
        setField(VulkanTerrainGpuBackend.class, backend, "cachedCountTarget", Long.valueOf(Long.MIN_VALUE));
        setField(VulkanTerrainGpuBackend.class, backend, "cachedCountMutation", Long.valueOf(-1L));
        return backend;
    }

    /** A group for {@code target} whose mesh is non-empty, which is what {@code countGroupsForTarget} requires. */
    private static ImmediateGroup group(long target) throws Exception {
        VulkanDynamicMesh mesh = (VulkanDynamicMesh) unsafe().allocateInstance(VulkanDynamicMesh.class);
        setField(VulkanDynamicMesh.class, mesh, "indexCount", Integer.valueOf(6));
        setField(VulkanDynamicMesh.class, mesh, "vertexCount", Integer.valueOf(4));
        ImmediateGroup group = ImmediateGroup.obtain(mesh, new float[16], new float[16],
                VulkanImmediateFormats.ITEM, 7, 11L, null, target);
        group.batches = 1;
        return group;
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
}
