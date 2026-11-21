package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flush must not offer the open run past an older retained run for the same target, pinned as a
 * source contract (audit-vulkan-core I1).
 * <p>
 * {@code FontBatch}'s own contract is that while a retained run waits for its target, later runs
 * for that target queue behind it, so glyphs cannot be drawn out of order. But
 * {@code offerRetained} stops at the first refusal, and the flush then offered the open run
 * unconditionally: a retained run refused again in the same flush stayed queued while the newer
 * run - if the sink took it - was drawn first. The harm is bounded (overlapping text drawn in the
 * wrong order, and only inside the transient re-refusal window), which is why this stayed a
 * suspicion; the queue discipline that removes it is structural, so what is pinned here is the
 * shape: the same-target retained check short-circuits the open run's offer, and the check reads
 * the retained queue by target identity.
 */
class FontBatchRetainedOrderTest {

    private static String fontBatchSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java"));
    }

    @Test
    void theOpenRunQueuesBehindARetainedRunForItsTarget() throws IOException {
        String body = methodSlice(fontBatchSource(),
                "public void celtium$flushPendingFontBatch(String how) {", 5200);
        assertTrue(body.contains("if (retainedFor(run.targetIdentity) || !offer(run, target)) {"),
                "the open run is retained without an offer while an older run for its target waits");
        int offered = body.indexOf("offerRetained(view, target);");
        int guarded = body.indexOf("retainedFor(run.targetIdentity)");
        assertTrue(offered >= 0 && guarded > offered,
                "the check runs after the retained runs have had their offer");
    }

    @Test
    void theRetainedCheckReadsTheQueueByTargetIdentity() throws IOException {
        String body = methodSlice(fontBatchSource(), "private boolean retainedFor(long targetIdentity) {", 400);
        assertTrue(body.contains("for (RetainedRun retained : this.retained)"),
                "the check scans the retained queue");
        assertTrue(body.contains("retained.targetIdentity == targetIdentity"),
                "the check matches on target identity");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
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
}
