package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which real image each framebuffer identity is drawn into, at which generation, in which layout - and who clears it.
 * <p>
 * R5 of {@code docs/refactoring-plan.md}: "main／default／chain／post の実 image が明示され、alias/layout/clear の所有が
 * 一意". This class is the one place that answers the three questions the backend has been answering from scattered
 * fields:
 * <ol>
 *   <li><b>alias</b> - a GL framebuffer identity ({@code 0} for the default framebuffer, the game's own main identity,
 *   and every other identity a mod or the game binds) maps to exactly one {@link ImageSlot}, and that mapping is
 *   recorded with the generation it was made under. Two identities never share a slot silently: {@link #slotFor}
 *   returns {@link ImageSlot#CHAIN_PAIR} for every non-zero non-main identity today, and {@link #sharedSlots()} names
 *   the identities that share one, which is the measurement B10/BH-14 needs rather than an assumption.</li>
 *   <li><b>layout</b> - each slot carries the layout its image is currently in, and the generation at which that
 *   layout was recorded. A layout noted for an older generation is stale by construction and is reported as such
 *   instead of being read as current.</li>
 *   <li><b>clear</b> - the clear a frame issues belongs to the slot of the identity that was bound when the game
 *   issued it, and at most one slot owns the colour clear of a frame. {@link #noteClear} refuses (and counts) a clear
 *   aimed at a slot whose content another identity already occupies this frame, which is the rule that keeps a late
 *   clear from erasing work an earlier window of the same frame did.</li>
 * </ol>
 * <p>
 * Deliberately pure state: no call into the backend, no Vulkan handle it cannot name from the values passed in, and no
 * allocation on the per-bind path beyond the bounded census. The backend keeps the handles; this class keeps the
 * bookkeeping, so the wiring is a delegation per method rather than a behaviour change.
 * <p>
 * Not owned here: the colour-texture map and the publication counters. R1b moved those to
 * {@code PublishedFrameTextures}, and duplicating them here would give the same fact two owners.
 */
public final class RenderTargetRegistry {

    /** The four real images a framebuffer identity can be drawn into. */
    public enum ImageSlot {
        /** The delivered frame image the present path blits from (the game's main framebuffer). */
        MAIN("main"),
        /** The pair for draws with the default framebuffer bound (identity 0). */
        DEFAULT_PAIR("default-pair"),
        /** The pair for the game's other framebuffers - the entity-outline silhouette and the splash's own FBO. */
        CHAIN_PAIR("chain-pair"),
        /** An image a post chain owns or adopted; it is not shared with a framebuffer identity. */
        POST("post");

        private final String label;

        ImageSlot(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        /** {@return the slot a non-main identity is routed to}: identity 0 is the default pair, everything else the
         * chain pair. This is the rule the backend applies today, stated once so a per-identity target (BH-14) has one
         * place to change. */
        public static ImageSlot forNonMainIdentity(long identity) {
            return identity == 0L ? DEFAULT_PAIR : CHAIN_PAIR;
        }
    }

    /** How many distinct identities the census records before it stops growing. */
    private static final int CENSUS_LIMIT = 12;

    // ---------------------------------------------------------------- alias

    /** The identity the game's main framebuffer has, or {@link Long#MIN_VALUE} before the first main bind. */
    private long mainIdentity = Long.MIN_VALUE;
    /** The identity bound right now, and whether the renderer treats it as main. */
    private long currentIdentity = -1L;
    private boolean currentIsMain = true;
    /** The frame-target generation: bumped whenever the frame image is replaced. */
    private long generation;
    /** identity -> the slot it was routed to, with the generation that routing was recorded under. */
    private final Map<Long, Alias> aliases = new HashMap<Long, Alias>();
    /** The distinct identities the game has bound, bounded; the census the routing rule is read against. */
    private final Set<Long> bindCensus = new HashSet<Long>();
    private int bindCensusOverflow;
    private String censusSummary = "none";

    /** One identity's routing: the slot, whether it was main, and the generation it was recorded at. */
    private static final class Alias {
        final ImageSlot slot;
        final boolean main;
        final long generation;

        Alias(ImageSlot slot, boolean main, long generation) {
            this.slot = slot;
            this.main = main;
            this.generation = generation;
        }
    }

    /**
     * Records a framebuffer bind and answers which slot its draws belong to.
     * <p>
     * The first bind of a main identity fixes {@link #mainIdentity}; every later bind is compared against it. The
     * census is bounded and its overflow counted, so "the run only ever bound two identities" is a measurement rather
     * than an artefact of the bound.
     */
    public ImageSlot noteBind(long identity, boolean isMain, String routeLabel) {
        this.currentIdentity = identity;
        this.currentIsMain = isMain;
        if (isMain) {
            this.mainIdentity = identity;
        }
        ImageSlot slot = isMain ? ImageSlot.MAIN : ImageSlot.forNonMainIdentity(identity);
        this.aliases.put(Long.valueOf(identity), new Alias(slot, isMain, this.generation));
        if (this.bindCensus.size() < CENSUS_LIMIT) {
            if (this.bindCensus.add(Long.valueOf(identity)) && this.bindCensus.size() == CENSUS_LIMIT) {
                // The summary is built once, at the point the census stops growing, so the per-bind path stays cheap.
                this.censusSummary = "distinct=" + this.bindCensus.size() + " overflow=" + this.bindCensusOverflow
                        + " main=" + hex(this.mainIdentity) + " lastRoute=" + routeLabel;
            }
        } else if (!this.bindCensus.contains(Long.valueOf(identity))) {
            this.bindCensusOverflow++;
        }
        return slot;
    }

    /** {@return the identity the game's main framebuffer has}, or {@link Long#MIN_VALUE} when none was bound yet. */
    public long mainIdentity() {
        return this.mainIdentity;
    }

    /** {@return the identity bound right now}. */
    public long currentIdentity() {
        return this.currentIdentity;
    }

    /** {@return whether the current bind is the renderer's main target}. */
    public boolean currentIsMain() {
        return this.currentIsMain;
    }

    /**
     * {@return the slot an identity's draws belong to}, as it was routed when it was bound.
     * <p>
     * An identity that was never bound answers with the routing rule's own answer rather than {@code null}: a caller
     * that asks about an identity it has not seen is asking a question about the rule, not about history.
     */
    public ImageSlot slotFor(long identity) {
        Alias alias = this.aliases.get(Long.valueOf(identity));
        return alias != null ? alias.slot : ImageSlot.forNonMainIdentity(identity);
    }

    /**
     * {@return the identities that share one slot}, as {@code slot=[id, id, ...]} entries.
     * <p>
     * This is the BH-14 measurement: a slot with two identities is an image two framebuffers are drawing into, and
     * their sizes are not both representable. An empty answer means every bound identity has an image of its own.
     */
    public String sharedSlots() {
        Map<ImageSlot, List<Long>> bySlot = new HashMap<ImageSlot, List<Long>>();
        for (Map.Entry<Long, Alias> entry : this.aliases.entrySet()) {
            List<Long> ids = bySlot.get(entry.getValue().slot);
            if (ids == null) {
                ids = new ArrayList<Long>();
                bySlot.put(entry.getValue().slot, ids);
            }
            ids.add(entry.getKey());
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<ImageSlot, List<Long>> entry : bySlot.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getKey().label()).append('=').append(entry.getValue());
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    /** {@return the bounded census line}: distinct identities, overflow, the main identity and the last route. */
    public String censusSummary() {
        return this.censusSummary;
    }

    // ---------------------------------------------------------------- layout

    /** slot -> {layout, generation it was noted at}. */
    private final Map<ImageSlot, long[]> layouts = new HashMap<ImageSlot, long[]>();
    private int staleLayoutReads;

    /** Records the layout a slot's image is in, at the current generation. */
    public void noteLayout(ImageSlot slot, int layout) {
        this.layouts.put(slot, new long[] {layout, this.generation});
    }

    /**
     * {@return the layout a slot's image is in}, or {@code -1} when none was recorded for this generation.
     * <p>
     * A layout noted under an older generation is <em>stale</em> - the image was replaced since - and answers -1 with
     * {@link #staleLayoutReads} counted, rather than handing back a layout that describes a destroyed image. That is
     * the failure B10 produced: a layout recorded for the previous frame target read as if it described the new one.
     */
    public int layoutOf(ImageSlot slot) {
        long[] entry = this.layouts.get(slot);
        if (entry == null) {
            return -1;
        }
        if (entry[1] != this.generation) {
            this.staleLayoutReads++;
            return -1;
        }
        return (int) entry[0];
    }

    /** {@return how many layout reads were refused as stale}. */
    public int staleLayoutReads() {
        return this.staleLayoutReads;
    }

    // ---------------------------------------------------------------- generation

    /** {@return the frame-target generation}: bumped by the backend whenever it replaces the frame image. */
    /**
     * The latch the model-draw gate reads: whether the frame target's identity is known.
     * <p>
     * Moved here mechanically from the backend (refactoring plan R5). It is <b>not</b> cleared when the target is
     * replaced, which is today's behaviour: after a resize it can read true while the new target's identity is not
     * known yet. That is the same class as B10 - a fact fixed at the old target's time read as if it described the new
     * one - and clearing it in {@link #noteFrameTargetReplaced()} is a behaviour change that gets its own commit and
     * its own evidence, not a ride on this extraction.
     */
    private boolean targetKnown;

    /** Records that the frame target's identity has been established. */
    public void noteTargetKnown() {
        this.targetKnown = true;
    }

    /** {@return whether the frame target's identity is known}. */
    public boolean targetKnown() {
        return this.targetKnown;
    }

    /** Per pair slot: the framebuffer handle, the render pass it was built for, and how many were created. */
    private final Map<ImageSlot, long[]> pairFramebuffers = new HashMap<ImageSlot, long[]>();

    /** {@return the pair slot's framebuffer handle, or 0 when it has none}. */
    public long pairFramebuffer(ImageSlot slot) {
        long[] entry = this.pairFramebuffers.get(slot);
        return entry == null ? 0L : entry[0];
    }

    /** {@return the render pass the slot's framebuffer was built for, or 0}. */
    public long pairFramebufferRenderPass(ImageSlot slot) {
        long[] entry = this.pairFramebuffers.get(slot);
        return entry == null ? 0L : entry[1];
    }

    /** {@return how many framebuffers were created for the slot}. */
    public int pairFramebufferCreations(ImageSlot slot) {
        long[] entry = this.pairFramebuffers.get(slot);
        return entry == null ? 0 : (int) entry[2];
    }

    /** Records the framebuffer a slot now has, counting the creation when it replaces a different handle. */
    public void notePairFramebuffer(ImageSlot slot, long handle, long renderPass) {
        long[] entry = this.pairFramebuffers.get(slot);
        if (entry == null) {
            this.pairFramebuffers.put(slot, new long[] { handle, renderPass, 1L });
            return;
        }
        if (entry[0] != handle) {
            entry[2]++;
        }
        entry[0] = handle;
        entry[1] = renderPass;
    }

    /** Forgets the slot's framebuffer, after it has been destroyed. */
    public void clearPairFramebuffer(ImageSlot slot) {
        this.pairFramebuffers.remove(slot);
    }

    /** Records the identity adopted as the main frame target, which the bind path can also discover later. */
    public void noteMainIdentity(long identity) {
        this.mainIdentity = identity;
    }

    /**
     * Whether a frame-target replacement is in progress: announced when the bind path sees the framebuffer changing,
     * settled by the first ensure that finds the new target ready. The two setters are named because the flag had
     * three writers in the backend and the two clears were proven equivalent before the move.
     */
    private boolean replacementInProgress;

    /** Records that a frame-target replacement has been announced. */
    public void noteReplacementAnnounced() {
        this.replacementInProgress = true;
    }

    /** Records that the replacement has settled: the target is ready. */
    public void noteReplacementSettled() {
        this.replacementInProgress = false;
    }

    /** {@return whether a frame-target replacement is in progress}. */
    public boolean replacementInProgress() {
        return this.replacementInProgress;
    }

    public long generation() {
        return this.generation;
    }

    /**
     * Bumps the generation because the frame image was replaced.
     * <p>
     * Every layout and alias recorded under the previous generation is stale from this point; they are dropped rather
     * than carried, so a later read cannot mistake them for the new image's state. The aliases are kept but re-stamped
     * lazily by the next bind, because an identity keeps its routing across a replacement.
     */
    public void noteFrameTargetReplaced() {
        this.generation++;
        this.layouts.clear();
    }

    // ---------------------------------------------------------------- clear ownership

    /** The clear latched for an identity: mask, colour, depth, and the slot it was latched for. */
    private int latchedMask;
    private float latchedRed;
    private float latchedGreen;
    private float latchedBlue;
    private float latchedAlpha;
    private double latchedDepth = 1.0D;
    private ImageSlot latchedSlot;
    private long latchedGeneration = -1L;
    private boolean hasLatchedClear;
    /** The frame whose clears have already been attributed, and which slot owns this frame's colour clear. */
    private long clearFrame = -1L;
    private ImageSlot colourClearOwner;
    private final Map<ImageSlot, Integer> clearsBySlot = new HashMap<ImageSlot, Integer>();
    private int latchedClearsApplied;
    private int liveClearsApplied;
    private int clearCollisions;

    /**
     * Latches the clear the game issued, for the identity that was bound when it issued it.
     * <p>
     * The game clears a framebuffer between binding it and binding the next one, so this is the only moment the clear
     * can be attributed to the image it names. The slot is resolved through {@link #slotFor}, so the attribution is
     * the same rule the draws follow.
     */
    public void latchClear(long identity, int mask, float red, float green, float blue, float alpha, double depth) {
        this.latchedMask = mask;
        this.latchedRed = red;
        this.latchedGreen = green;
        this.latchedBlue = blue;
        this.latchedAlpha = alpha;
        this.latchedDepth = depth;
        this.latchedSlot = slotFor(identity);
        this.latchedGeneration = this.generation;
        this.hasLatchedClear = true;
    }

    /** {@return whether a latched clear is waiting to be applied}. */
    public boolean hasLatchedClear() {
        return this.hasLatchedClear;
    }

    /** {@return the slot the latched clear belongs to}, or {@code null} when none is latched. */
    public ImageSlot latchedSlot() {
        return this.latchedSlot;
    }

    /** {@return the latched clear's buffer mask}. */
    public int latchedMask() {
        return this.latchedMask;
    }

    /** {@return the latched clear's colour as {r, g, b, a}}. */
    public float[] latchedColour() {
        return new float[] {this.latchedRed, this.latchedGreen, this.latchedBlue, this.latchedAlpha};
    }

    /** {@return the latched clear's depth value}. */
    public double latchedDepth() {
        return this.latchedDepth;
    }

    /**
     * Begins a frame's clear ownership: one frame, one owner of the colour clear.
     * <p>
     * Called once per frame before any clear is attributed. A latched clear from the previous frame that was never
     * applied is dropped and counted, because applying it in this frame would clear an image with a colour the game
     * asked for while a different framebuffer was bound.
     */
    public void beginFrame(long frameId) {
        if (this.clearFrame == frameId) {
            return;
        }
        this.clearFrame = frameId;
        this.colourClearOwner = null;
        if (this.hasLatchedClear && this.latchedGeneration != this.generation) {
            this.hasLatchedClear = false;
            this.latchedSlot = null;
        }
    }

    /**
     * Records that a clear landed on a slot, and refuses one that would erase another identity's content.
     *
     * @param slot    the slot the clear names
     * @param colour  whether the clear includes the colour buffer (a depth-only clear never collides)
     * @param latched whether it came from the latched game clear rather than a live read
     * @return {@code true} when the clear is the frame's first colour clear for that slot, {@code false} when it was
     *         refused because another identity already owns this frame's colour clear
     */
    public boolean noteClear(ImageSlot slot, boolean colour, boolean latched) {
        if (colour) {
            if (this.colourClearOwner != null && this.colourClearOwner != slot) {
                this.clearCollisions++;
                return false;
            }
            this.colourClearOwner = slot;
        }
        Integer count = this.clearsBySlot.get(slot);
        this.clearsBySlot.put(slot, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        if (latched) {
            this.latchedClearsApplied++;
            this.hasLatchedClear = false;
            this.latchedSlot = null;
        } else {
            this.liveClearsApplied++;
        }
        return true;
    }

    /** {@return the slot that owns this frame's colour clear}, or {@code null} when none has cleared yet. */
    public ImageSlot colourClearOwner() {
        return this.colourClearOwner;
    }

    /** {@return how many clears landed on each slot}, as {@code slot=n} entries. */
    public String clearsBySlot() {
        return this.clearsBySlot.toString();
    }

    /** {@return how many clears came from the latch and how many were read live}. */
    public String clearAttribution() {
        return "latched=" + this.latchedClearsApplied + " live=" + this.liveClearsApplied
                + " collisions=" + this.clearCollisions;
    }

    /** {@return how many clears were refused because another identity owned the frame's colour clear}. */
    public int clearCollisions() {
        return this.clearCollisions;
    }

    private static String hex(long value) {
        return value == Long.MIN_VALUE ? "unset" : "0x" + Long.toHexString(value);
    }
}
