package org.soutalab.celtium.vulkan.hal;

/**
 * The identity census and routing labels for frame targets: which pair an off-target draw identity
 * renders into, and which image a bound framebuffer's draws actually reach.
 * <p>
 * These rules are pure and touch no device: they name routes, they do not own the pairs or images the
 * routes point at (the frame target still owns those until the pair groups move out). The backend
 * routes by these labels and stamps its attribution counters with them, so the counters and the
 * drawing cannot disagree; the census turns the routing into numbers a report can carry.
 * <p>
 * This class is the identity census / routing functional group moved out of the GL-less frame target
 * (HAL wave 3): the frame target keeps no copy of the labels or the census.
 */
public final class FrameTargetRouting {

    private FrameTargetRouting() {
    }

    /**
     * Which of the two pairs an off-target draw identity renders into: the default pair for identity 0 - the
     * default framebuffer, which under the stub carries the game's finished-frame composites - and a chain
     * pair for every other non-main identity, the game's own framebuffers, of which the entity-outline
     * silhouette is the measured case.
     * <p>
     * It is public and static - and touches no device - so the CPU contract test can exercise it without a
     * GPU, which is the same reason {@code ImmediateGroupQueue.retireGroupsForTarget} is written that way.
     *
     * @param identity the framebuffer identity the game bound, as the frame mixin reports it
     * @return the pair label, "default-pair" or "chain-pair"
     */
    public static String pairLabelForIdentity(long identity) {
        return identity == 0L ? "default-pair" : "chain-pair";
    }


    // ---- BH-14 census: how many identities share how few images --------------------------------------------------
    //
    // The routing rule below sends identity 0 to the default pair and *every other* non-main identity to the chain
    // pair, so two non-main identities in one frame draw into the same two images: one identity's clear or draw can
    // reach the other's content between its draw and the copy that reads it. That is a fact about the ownership, not
    // a symptom, and this census turns it into numbers the report can carry: the distinct non-main identities seen,
    // the distinct main identities, and the largest number of identities sharing one pair. When the per-identity
    // image lands, "widest pair" must fall to 1 and the pair count must rise to the identity count - that is the
    // acceptance this counter exists to make readable.
    private static final java.util.Set<Long> CENSUS_NON_MAIN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Long> CENSUS_MAIN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String, java.util.Set<Long>> CENSUS_PAIR_MEMBERS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Long>>();

    /** Records one routed bind: the identity, whether it is main, and which pair it therefore owns. */
    public static void noteIdentityForCensus(long identity, boolean isMain) {
        (isMain ? CENSUS_MAIN : CENSUS_NON_MAIN).add(Long.valueOf(identity));
        // The per-identity chain pair landed: a non-main identity's pair key is its own name, so the census's
        // "widest pair" falls to 1 when every identity owns its image - the acceptance the counter exists to read.
        // The frame-image and default-pair routes keep their shared labels; they are single-owner by design.
        String route = routeLabelForBinding(identity, isMain);
        String pair = "chain-pair".equals(route) ? route + ":" + Long.toHexString(identity) : route;
        java.util.Set<Long> members = CENSUS_PAIR_MEMBERS.get(pair);
        if (members == null) {
            members = java.util.concurrent.ConcurrentHashMap.newKeySet();
            java.util.Set<Long> existing = CENSUS_PAIR_MEMBERS.putIfAbsent(pair, members);
            if (existing != null) {
                members = existing;
            }
        }
        members.add(Long.valueOf(identity));
    }

    /** {@return the BH-14 census as one line}: identities, pairs, and the widest pair's member count. */
    public static String identityPairCensus() {
        int widest = 0;
        String widestPair = "none";
        StringBuilder pairs = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.Set<Long>> entry : CENSUS_PAIR_MEMBERS.entrySet()) {
            if (pairs.length() > 0) {
                pairs.append(',');
            }
            pairs.append(entry.getKey()).append('=').append(entry.getValue().size());
            if (entry.getValue().size() > widest) {
                widest = entry.getValue().size();
                widestPair = entry.getKey();
            }
        }
        return "mainIdentities=" + CENSUS_MAIN.size()
                + " nonMainIdentities=" + CENSUS_NON_MAIN.size()
                + " pairs=" + CENSUS_PAIR_MEMBERS.size()
                + " widestPair=" + widestPair + "(" + widest + " identities share it)"
                + " perPair=[" + pairs + "]";
    }

    /**
     * Which image a bound framebuffer's draws actually render into, as the backend routes them - not which pair an
     * identity <em>would</em> take if it were off-target. The two answers differ for the main framebuffer, and the
     * census used to print only the second: the game's main framebuffer identity is a non-zero
     * {@code System.identityHashCode}, so a bind census line read {@code main=true pair=chain-pair}, which conflates
     * "this is the delivered image" with "these draws go to the outline pair". The routing itself never had that
     * ambiguity - {@code VulkanTerrainGpuBackend} keys it on the main flag, {@code offTarget = !currentRenderTargetIsMain}
     * - so this is the label that was wrong, and this method is the one place the two questions are separated:
     * the main framebuffer renders into the frame image, identity 0 into the default pair and every other
     * non-main identity into a chain pair.
     *
     * @param identity the framebuffer identity the game bound
     * @param isMain   whether that framebuffer is the game's own main one ({@code mc.getFramebuffer()})
     * @return "frame-image", "default-pair" or "chain-pair"
     */
    public static String routeLabelForBinding(long identity, boolean isMain) {
        // Identity 0 is the window (GL name 0 is the default framebuffer), so its draws go to the frame image in every
        // phase. Routing it to a pair instead made the window's own picture and the picture a name-0 copy reads two
        // different images: measured on the main menu, the panorama's faces were drawn into the frame image while the
        // copy that feeds the blur still read the pair, and the presented menu came out washed out and flat
        // (presentedDistinctColours 96k -> 21k, top mean (164,167,177) -> (203,201,198)). One image for the window in
        // both directions is vanilla's own semantics and removes the phase dependence the earlier rule had.
        return isMain || identity == 0L ? "frame-image" : pairLabelForIdentity(identity);
    }
}
