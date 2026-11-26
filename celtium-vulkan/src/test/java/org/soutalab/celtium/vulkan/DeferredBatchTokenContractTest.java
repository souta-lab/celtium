package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-56's pins: on a timeline device a deferred batch carries no fence, the poll reclaims a timeline batch on
 * the counter alone, and the handle the submit paths hand out is the batch serial - a token, resolved against
 * the tracked entries - rather than a fence.
 * <p>
 * T1 and T2 are source contracts, in this codebase's idiom for device-bound shapes: the properties they pin
 * (no fence is borrowed for a timeline batch; no fence query stands between the counter check and the
 * reclaim) are exactly the ones whose violation reintroduces VUID-vkResetFences-pFences-01123 - a batch that
 * still has a fence must never be reclaimed on the counter alone, and a batch with no fence has nothing the
 * counter-only reclaim could illegally reset. The 01123 = 0 proof itself is the landing leg's (design T4);
 * these tests keep the shape the leg's proof stands on from drifting. T3 exercises the token resolution rule
 * itself, without a device, on entries built reflectively: the rule the migration had to preserve from
 * {@code celtium$timelineValueForFence} - an unsubmitted batch resolves to its (zero) value, an untracked
 * token resolves to nothing.
 */
class DeferredBatchTokenContractTest {

    private static final String CONTEXT_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";

    /** T1: the fence borrow is gated on the timeline's absence, and it is the only borrow call site. */
    @Test
    void aTimelineBatchBorrowsNoFence() throws IOException {
        String source = Files.readString(findRepoFile(CONTEXT_SOURCE));
        assertTrue(source.contains(
                        "batch.fence = this.frameTimeline != null ? VK_NULL_HANDLE"
                                + " : celtium$borrowDeferredFence();"),
                "currentBatch borrows a fence only when the device has no frame timeline");
        assertEquals(2, countOccurrences(source, "celtium$borrowDeferredFence()"),
                "the borrow has exactly one call site (plus its definition): the gated one in currentBatch");
        assertTrue(source.contains("batch.serial = ++this.batchSerialSequence;"),
                "serials are pre-incremented, so the first token is 1 and 0 keeps the 'no submission' meaning");
    }

    /** T2: the poll's timeline branch reaches its reclaim without any fence query; the fallback query stays. */
    @Test
    void thePollReclaimsATimelineBatchOnTheCounterAlone() throws IOException {
        String source = Files.readString(findRepoFile(CONTEXT_SOURCE));
        int methodStart = source.indexOf("public int celtium$pollDeferredSubmits()");
        assertTrue(methodStart >= 0, "the poll exists");
        int methodEnd = source.indexOf("return reclaimed;", methodStart);
        assertTrue(methodEnd > methodStart, "the poll's end is found");
        String poll = source.substring(methodStart, methodEnd);
        int branchStart = poll.indexOf("head.batch.timelineValue > 0L");
        assertTrue(branchStart >= 0, "the timeline branch exists");
        int branchReclaim = poll.indexOf("celtium$reclaimDeferred(head, false);", branchStart);
        assertTrue(branchReclaim > branchStart, "the timeline branch reclaims");
        assertFalse(poll.substring(branchStart, branchReclaim).contains("vkGetFenceStatus"),
                "no fence query stands between the counter check and the reclaim (the C1 shape)");
        assertTrue(poll.substring(branchReclaim).contains("vkGetFenceStatus"),
                "the fallback branch still confirms through the fence");
        assertFalse(poll.contains("fenceConfirmedBatch"),
                "the two-stage confirmation state is gone with the second stage");
    }

    /** T3: the token resolution rule - first tracked batch with the serial, nothing for 0 or untracked. */
    @Test
    void tokenResolutionFollowsTheTrackedBatches() throws Exception {
        ArrayDeque<Object> entries = new ArrayDeque<Object>();
        assertNull(resolve(entries, 7L), "an empty queue resolves nothing");

        Object noBatch = newEntry(null);
        Object first = newEntry(newBatch(7L, 0L, 0x11L));
        Object second = newEntry(newBatch(9L, 123L, 0x22L));
        entries.add(noBatch);
        entries.add(first);
        entries.add(second);

        assertNull(resolve(entries, 0L), "token 0 is 'no submission' and resolves nothing");
        assertNull(resolve(entries, 42L), "an untracked token resolves nothing");
        assertSame(batchOf(first), resolve(entries, 7L), "the token names its batch");
        assertEquals(0L, timelineValueOf(resolve(entries, 7L)),
                "an unsubmitted batch resolves to its zero timeline value, as the fence form did");
        assertEquals(123L, timelineValueOf(resolve(entries, 9L)),
                "a submitted batch resolves to the value its submission signals");
        assertEquals(0x22L, fenceOf(resolve(entries, 9L)),
                "the fallback resolution reads the same batch's fence");
    }

    /** T3's delegation pin: both public resolutions go through the one matching rule. */
    @Test
    void bothResolutionsShareTheOneMatchingRule() throws IOException {
        String source = Files.readString(findRepoFile(CONTEXT_SOURCE));
        assertEquals(2, countOccurrences(source, "celtium$batchForToken(this.deferredSubmits, token)"),
                "timelineValueForDeferredToken and fenceForDeferredToken share celtium$batchForToken");
        assertFalse(source.contains("celtium$timelineValueForFence"),
                "the fence-keyed resolution is gone");
    }

    // The resolution itself is typed on VulkanContext's private entry/batch classes; the test builds them
    // reflectively rather than widening their visibility for a test's sake.

    @SuppressWarnings("unchecked")
    private static Object resolve(ArrayDeque<Object> entries, long token) throws Exception {
        ArrayDeque raw = entries;
        return VulkanContext.celtium$batchForToken(raw, token);
    }

    private static Object newEntry(Object batch) throws Exception {
        Class<?> entryClass = Class.forName("org.soutalab.celtium.vulkan.VulkanContext$DeferredSubmit");
        Constructor<?> constructor = entryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object entry = constructor.newInstance();
        if (batch != null) {
            Field batchField = entryClass.getDeclaredField("batch");
            batchField.setAccessible(true);
            batchField.set(entry, batch);
        }
        return entry;
    }

    private static Object newBatch(long serial, long timelineValue, long fence) throws Exception {
        Class<?> batchClass = Class.forName("org.soutalab.celtium.vulkan.VulkanContext$SharedBatch");
        Constructor<?> constructor = batchClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object batch = constructor.newInstance();
        setLong(batchClass, batch, "serial", serial);
        setLong(batchClass, batch, "timelineValue", timelineValue);
        setLong(batchClass, batch, "fence", fence);
        return batch;
    }

    private static Object batchOf(Object entry) throws Exception {
        Field batchField = entry.getClass().getDeclaredField("batch");
        batchField.setAccessible(true);
        return batchField.get(entry);
    }

    private static long timelineValueOf(Object batch) throws Exception {
        return getLong(batch.getClass(), batch, "timelineValue");
    }

    private static long fenceOf(Object batch) throws Exception {
        return getLong(batch.getClass(), batch, "fence");
    }

    private static void setLong(Class<?> type, Object target, String field, long value) throws Exception {
        Field declared = type.getDeclaredField(field);
        declared.setAccessible(true);
        declared.setLong(target, value);
    }

    private static long getLong(Class<?> type, Object target, String field) throws Exception {
        Field declared = type.getDeclaredField(field);
        declared.setAccessible(true);
        return declared.getLong(target);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
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
