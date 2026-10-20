package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

/**
 * Measures what a multi-draw would actually save on the model path, before any of it is built.
 *
 * <p>Phase B groups model draws by (list identity, texture) and would then issue one indirect multi-draw per group,
 * taking the per-draw matrix from the array the {@code groupid} vertex shader already reads. Whether that is worth
 * building depends on one number nobody has: how many members a group has. A group of one saves nothing, and the whole
 * design is a large amount of machinery for no frame time - so it is measured first, on the real scene, and the number
 * decides.
 *
 * <p>Costs nothing unless {@code -Dceltium.vulkan.diagnostics=true}: the call site asks {@link VulkanDiagnostics} before
 * calling here, so a normal run does no work and allocates nothing.
 */
public final class CeltiumModelBatchGroups {

    /** Only the last frame's grouping is kept: this answers "what would a frame look like", not a history. */
    private static final Map<Long, int[]> GROUPS = new HashMap<>();

    private static long lastFrame = Long.MIN_VALUE;
    private static long draws;
    private static final int[] HISTOGRAM = new int[9];
    private static int reports;

    private CeltiumModelBatchGroups() {
    }

    /**
     * Records one model draw against its group.
     *
     * @param frameId the frame the draw belongs to
     * @param listId  the display list the geometry came from
     * @param texture the texture the draw samples
     */
    public static void note(long frameId, int listId, int texture) {
        if (frameId != lastFrame) {
            if (lastFrame != Long.MIN_VALUE) {
                report(lastFrame);
            }
            reset(frameId);
        }
        long key = ((long) listId << 32) | (texture & 0xFFFFFFFFL);
        int[] slot = GROUPS.get(Long.valueOf(key));
        if (slot == null) {
            GROUPS.put(Long.valueOf(key), new int[] { 1 });
        } else {
            slot[0]++;
        }
        draws++;
    }

    private static void reset(long frameId) {
        GROUPS.clear();
        draws = 0;
        java.util.Arrays.fill(HISTOGRAM, 0);
        lastFrame = frameId;
    }

    private static void report(long frameId) {
        int groups = GROUPS.size();
        int multi = 0;
        int members = 0;
        int max = 0;
        for (int[] slot : GROUPS.values()) {
            int n = slot[0];
            members += n;
            if (n > 1) {
                multi++;
            }
            HISTOGRAM[Math.min(n, 8)]++;
            max = Math.max(max, n);
        }
        // Bounded rather than conditional on the answer: a frame with even one model draw reports, up to a cap. The
        // earlier version printed only when something grouped or the frame index was a multiple of 200, which meant a
        // scene whose draws do not happen to land on those frames reported nothing - and "no output" then reads as
        // "the instrument did not run", which is exactly the confusion this cap avoids.
        if (draws == 0 || reports >= 40) {
            return;
        }
        reports++;
        StringBuilder hist = new StringBuilder();
        for (int n = 1; n < HISTOGRAM.length; n++) {
            if (HISTOGRAM[n] == 0) {
                continue;
            }
            if (hist.length() > 0) {
                hist.append(' ');
            }
            hist.append(n == 8 ? "8+" : String.valueOf(n)).append(':').append(HISTOGRAM[n]);
        }
        System.out.println("[Celtium] CELTIUM_MODEL_GROUPS frame=" + frameId + " draws=" + draws + " groups=" + groups
                + " multiDrawGroups=" + multi + " maxMembers=" + max + " members=" + members
                + " histogram=[" + hist + "]");
    }
}
