package org.soutalab.celtium.vulkan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GLSM stage 2 (glsm-stage2-design.md §3.1): the frame-target / window state machine.
 * <p>
 * Stage 1 gave the pack route a state machine ({@code GlsmPackState}); this class is the same
 * shape for the frame side. The design's finding was that the state already exists but is
 * scattered across partial owners - the registry, {@code GllessFrameTarget}, the stub and the
 * backend each hold a piece of the same facts, in two identity spaces and four generation
 * systems - so stage 2 is an integration, not a new build: one owner per fact, introduced slice
 * by slice behind the single flag {@code -Dceltium.vulkan.glsmFrameState} (default off; the
 * slices share the flag, there are no per-slice flags). With the flag off, every call site keeps
 * the legacy owner and this class is never touched, so the off path is the original code path
 * byte for byte. When a slice's on path becomes the default (the merge phase's A/B decides),
 * that slice's legacy owners are deleted in the same landing - the design's §5 rule, the same
 * operation stage 1 used for its on-default landing.
 * <p>
 * This slice (S2-2) builds only {@link ClearState}: the single owner of clear issue and
 * application state. The remaining sub-states of the design's §3.2 sketch (TargetRecord, the
 * bound pair, ViewportState, WindowState, ChainRecord) arrive with their own slices and are
 * deliberately absent here.
 * <p>
 * Pure state: no Vulkan or GL calls, render-thread confined like the owners it replaces.
 */
public final class GlsmFrameState {

    /**
     * Whether the stage-2 frame-state route is on: {@code -Dceltium.vulkan.glsmFrameState},
     * default off. One flag for every stage-2 slice (design §5); an explicit {@code true}
     * opts in.
     */
    public static final boolean ENABLED =
            "true".equalsIgnoreCase(System.getProperty("celtium.vulkan.glsmFrameState", "false"));

    private final ClearState clearState = new ClearState();

    /** {@return the clear issue/application state machine}. */
    public ClearState clearState() {
        return this.clearState;
    }

    /**
     * A clear the game issued, recorded with the values in effect at the moment it was issued.
     * <p>
     * The value snapshot follows the per-buffer rule the stub's record established: a later
     * depth-only clear must not replace the colour an earlier colour clear was issued with, so
     * the colour fields move only when the folded mask names colour, and the depth only when it
     * names depth. {@code issues} counts how many issued clears folded into this record, which
     * is what lets the accounting identity in {@link ClearState} stay exact under folding.
     */
    public static final class PendingClear {
        /** GL buffer mask (0x4000 colour, 0x0100 depth, 0x0400 stencil). */
        public int mask;
        public float red;
        public float green;
        public float blue;
        public float alpha;
        public double depth = 1.0D;
        /** The raw GL framebuffer name bound when the (latest folded) clear was issued. */
        public int issueDrawFbo;
        /** How many issued clears this record carries. */
        public int issues;

        PendingClear copy() {
            PendingClear copy = new PendingClear();
            copy.mask = this.mask;
            copy.red = this.red;
            copy.green = this.green;
            copy.blue = this.blue;
            copy.alpha = this.alpha;
            copy.depth = this.depth;
            copy.issueDrawFbo = this.issueDrawFbo;
            copy.issues = this.issues;
            return copy;
        }

        /** Folds one more issued clear into this record, under the per-buffer snapshot rule. */
        void fold(int mask, float red, float green, float blue, float alpha, double depth,
                  int issueDrawFbo) {
            this.mask |= mask;
            if ((mask & 0x4000) != 0) {
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
            }
            if ((mask & 0x0100) != 0) {
                this.depth = depth;
            }
            this.issueDrawFbo = issueDrawFbo;
            this.issues++;
        }
    }

    /** The route a clear took, the design §3.4 classification the census will absorb. */
    public enum ClearRoute {
        /** Rode a queued group of its own target and replayed inside that group's pass. */
        QUEUED,
        /** Recorded as vkCmdClearAttachments inside the target's already-open pass. */
        IN_PASS,
        /** Submitted as a one-off command buffer after the ordering flush sent the draws. */
        STANDALONE,
        /** Latched for the target's pass-begin site. */
        LATCH,
        /** The main clear, carried by the draw queue instead of by the latch. */
        CARRIED,
        /** A carried clear handed back to the latch because its group was discarded undrawn. */
        RESCUED
    }

    /**
     * The single owner of clear issue and application state (design §3.4).
     * <p>
     * What it replaces on the on path, and where each piece used to live:
     * <ul>
     * <li>the frame image's latch ({@code GllessFrameTarget.gameClearLatched*}), and</li>
     * <li>the pair latch ({@code RenderTargetRegistry}'s latched clear) - one record for
     * <em>all</em> identities there, so a latch for one chain pair silently overwrote another
     * pair's; here the pending table is keyed by identity, so that overwrite cannot happen;</li>
     * <li>the colour-clear ownership rule (the registry's {@code colourClearOwner} and its
     * collision refusal), moved with its semantics unchanged - the slot stays the ownership
     * key, and {@link #beginFrame} mirrors the registry's frame rule (in production the
     * registry's own beginFrame is exercised only by its test, so mirroring the API keeps
     * production behaviour identical either way);</li>
     * <li>the queue-carried main clear's reservation (the backend's
     * {@code mainClearCarried*} fields) and its discard rescue: the rescue becomes a
     * transition inside this class - carried back into the pending table - instead of a
     * re-pend call into the stub.</li>
     * </ul>
     * The stub's pending mask is <em>not</em> moved: it is the GL issue record and the live
     * fallback {@code GllessFrameTarget.applyGameClears} reads when no binding change
     * attributed the clear, and the current clear values stay the stub's (the design keeps
     * them there; they are stage 3's glGet answer source). What this class owns is the
     * attributed state: once the backend has resolved a clear to an identity, its pending,
     * carried and applied facts live here and nowhere else.
     * <p>
     * Accounting: every issued clear the state machine attributes increments exactly one
     * bucket, and transitions move the count between buckets, so at any moment
     * {@code issued == applied + pending + carried + dropped} holds exactly
     * ({@link #identityHolds()}). Folding preserves it because a folded record carries its
     * issue count with it. This is the identity the design's §5 S2-2 acceptance reads in a
     * leg, in place of a byte equivalence the clear routes cannot offer.
     */
    public static final class ClearState {

        /** Pending clears by target identity (the registry's identity space, never GL names). */
        private final Map<Long, PendingClear> pending = new LinkedHashMap<>();

        /** The carried main clear's reservation: the handshake record, not the carrier. */
        private int carriedMask;
        private final float[] carriedRgba = new float[4];
        private double carriedDepth;
        private int carriedDrawFbo;
        private long carriedGroupIdentity;

        /** Colour-clear ownership, the registry's rule moved verbatim (slot is the key). */
        private long clearFrame = -1L;
        private RenderTargetRegistry.ImageSlot colourClearOwner;
        private final int[] clearsBySlot = new int[RenderTargetRegistry.ImageSlot.values().length];
        private int clearCollisions;
        private int latchedClearsApplied;
        private int liveClearsApplied;

        private final long[] routeCounts = new long[ClearRoute.values().length];

        private long issuedTotal;
        private long appliedTotal;
        private long droppedTotal;
        private long carriedOutstanding;

        // ------------------------------------------------------------ pending table

        /**
         * Latches a clear for the identity it was issued against, folding it into that
         * identity's pending record when one is already waiting (the GL mask ORs the same
         * way in the stub's pending record; the per-buffer snapshot rule is
         * {@link PendingClear#fold}'s).
         */
        public void latch(long identity, int mask, float red, float green, float blue,
                          float alpha, double depth, int issueDrawFbo) {
            PendingClear entry = this.pending.get(identity);
            if (entry == null) {
                entry = new PendingClear();
                entry.depth = depth;
                this.pending.put(identity, entry);
            }
            entry.fold(mask, red, green, blue, alpha, depth, issueDrawFbo);
            this.issuedTotal++;
            this.routeCounts[ClearRoute.LATCH.ordinal()]++;
        }

        /** {@return a copy of the identity's pending clear}, or {@code null} when none waits. */
        public PendingClear peek(long identity) {
            PendingClear entry = this.pending.get(identity);
            return entry == null ? null : entry.copy();
        }

        /** {@return whether a clear is latched for the identity}. */
        public boolean hasPending(long identity) {
            return this.pending.containsKey(identity);
        }

        /**
         * Takes the identity's pending clear as applied: the record leaves the table and its
         * issue count moves to the applied bucket.
         *
         * @return the record that was applied, or {@code null} when none was pending
         */
        public PendingClear consume(long identity) {
            PendingClear entry = this.pending.remove(identity);
            if (entry != null) {
                this.appliedTotal += entry.issues;
            }
            return entry;
        }

        /**
         * Drops the identity's pending clear unapplied, counted: the exit a target
         * replacement takes when the image the clear named is gone.
         */
        public void dropPending(long identity) {
            PendingClear entry = this.pending.remove(identity);
            if (entry != null) {
                this.droppedTotal += entry.issues;
            }
        }

        /**
         * Puts a pending record back exactly as a snapshot saw it, for the recording
         * rollback: the snapshot/rollback pair around a window recording captures the
         * frame latch's record (and {@link #appliedTotal()}) before the record and hands
         * both back when the recording is discarded, so a consumed latch is restored with
         * the accounting that consumption moved.
         */
        public void restore(long identity, PendingClear snapshot) {
            if (snapshot == null) {
                this.pending.remove(identity);
            } else {
                this.pending.put(identity, snapshot.copy());
            }
        }

        /** {@return how many issued clears are waiting in the pending table}. */
        public long pendingIssues() {
            long total = 0L;
            for (PendingClear entry : this.pending.values()) {
                total += entry.issues;
            }
            return total;
        }

        // ------------------------------------------------------------ carried reservation

        /**
         * Records the queue's reservation of a main clear: the values it was issued with,
         * copied out of the caller's scratch before anything can reuse it. The reservation
         * is the handshake record only - the carrier is the queued group the backend
         * attached the clear to, and the accounting moves in {@link #noteQueuedCarried}.
         * A reservation no clear consumed is simply replaced or cleared; it was never an
         * issued clear in this table's accounting until a group actually carries one.
         */
        public void reserveCarried(int mask, float[] rgba, double depth, int drawFbo,
                                   long groupIdentity) {
            this.carriedMask = mask;
            this.carriedRgba[0] = rgba[0];
            this.carriedRgba[1] = rgba[1];
            this.carriedRgba[2] = rgba[2];
            this.carriedRgba[3] = rgba[3];
            this.carriedDepth = depth;
            this.carriedDrawFbo = drawFbo;
            this.carriedGroupIdentity = groupIdentity;
        }

        /** {@return the mask of the reserved carried clear}, or 0 when none is reserved. */
        public int carriedMask() {
            return this.carriedMask;
        }

        /** {@return the colour the reserved carried clear was issued with}, as {r, g, b, a}. */
        public float[] carriedColour() {
            return new float[] {this.carriedRgba[0], this.carriedRgba[1], this.carriedRgba[2],
                    this.carriedRgba[3]};
        }

        /** {@return the depth the reserved carried clear was issued with}. */
        public double carriedDepth() {
            return this.carriedDepth;
        }

        /** {@return the GL framebuffer name the reserved carried clear was issued against}. */
        public int carriedDrawFbo() {
            return this.carriedDrawFbo;
        }

        /** Drops the reservation (consumed by the take handshake, or never consumed). */
        public void clearCarried() {
            this.carriedMask = 0;
        }

        /**
         * Counts a main clear a queued group now carries: one issued clear moves into the
         * carried bucket. Called where the backend attaches the clear to the group, which
         * is the moment the queue - not the latch - owns it.
         */
        public void noteQueuedCarried() {
            this.issuedTotal++;
            this.carriedOutstanding++;
            this.routeCounts[ClearRoute.CARRIED.ordinal()]++;
        }

        /** Counts a carried clear replayed inside its group's pass: carried becomes applied. */
        public void noteCarriedApplied() {
            if (this.carriedOutstanding > 0L) {
                this.carriedOutstanding--;
            }
            this.appliedTotal++;
        }

        /**
         * The discard rescue as a transition inside the state machine: a carried clear whose
         * group was discarded undrawn folds back into the pending table under the main
         * identity, so the latch applies it at the next pass begin exactly as it would have
         * had the queue never carried it. The issue was already counted when the group took
         * the clear, so the fold moves the bucket (carried to pending) without counting a
         * second issue. On the legacy path this is the stub's re-pend call; here no call
         * leaves the state machine.
         */
        public void rescueCarriedToPending(long mainIdentity, int mask, float[] rgba,
                                           double depth) {
            PendingClear entry = this.pending.get(mainIdentity);
            if (entry == null) {
                entry = new PendingClear();
                entry.depth = depth;
                this.pending.put(mainIdentity, entry);
            }
            entry.fold(mask, rgba[0], rgba[1], rgba[2], rgba[3], depth, this.carriedDrawFbo);
            if (this.carriedOutstanding > 0L) {
                this.carriedOutstanding--;
            }
            this.routeCounts[ClearRoute.RESCUED.ordinal()]++;
        }

        // ------------------------------------------------------------ colour ownership

        /**
         * Begins a frame's clear ownership: one frame, one owner of the colour clear. The
         * registry's rule, mirrored. The stale-latch half of the registry's version has no
         * counterpart here: the pending table is per identity, so a latch that outlives its
         * frame can only ever be read back by the identity that issued it, never attributed
         * to another image.
         */
        public void beginFrame(long frameId) {
            if (this.clearFrame == frameId) {
                return;
            }
            this.clearFrame = frameId;
            this.colourClearOwner = null;
        }

        /**
         * Records that a clear landed on a slot, and refuses one that would erase another
         * slot's content - the registry's {@code noteClear} rule with its semantics
         * unchanged (the slot, not the identity, is the ownership key). A latched clear
         * recorded here is consumed from the pending table at the same point the registry
         * consumed its latch, so the applied bucket moves with the decision, not with the
         * recording that follows it.
         *
         * @param identity the identity the clear names (the pending record to consume)
         * @param slot     the slot the clear names
         * @param colour   whether the clear includes the colour buffer (a depth-only clear
         *                 never collides)
         * @param latched  whether it came from the pending table rather than a live read
         * @return {@code true} when the clear is the frame's first colour clear for that
         *         slot, {@code false} when refused because another slot already owns this
         *         frame's colour clear
         */
        public boolean noteClear(long identity, RenderTargetRegistry.ImageSlot slot,
                                 boolean colour, boolean latched) {
            if (colour) {
                if (this.colourClearOwner != null && this.colourClearOwner != slot) {
                    this.clearCollisions++;
                    return false;
                }
                this.colourClearOwner = slot;
            }
            this.clearsBySlot[slot.ordinal()]++;
            if (latched) {
                this.latchedClearsApplied++;
                consume(identity);
            } else {
                this.liveClearsApplied++;
            }
            return true;
        }

        /** {@return the slot that owns this frame's colour clear}, or {@code null}. */
        public RenderTargetRegistry.ImageSlot colourClearOwner() {
            return this.colourClearOwner;
        }

        /** {@return colour clears refused because another slot owned the frame's clear}. */
        public int clearCollisions() {
            return this.clearCollisions;
        }

        /**
         * {@return how many clears came from the latch and how many were read live}, in
         * the registry's {@code clearAttribution()} naming, so a diagnostics reader sees
         * the same keys whichever owner the flag selected (S2-1 absorb connection: the
         * registry's {@code latchedClearsApplied} / {@code liveClearsApplied} counters are
         * absorbed here on the on path, not duplicated).
         */
        public String clearAttribution() {
            return "latched=" + this.latchedClearsApplied + " live=" + this.liveClearsApplied
                    + " collisions=" + this.clearCollisions;
        }

        // ------------------------------------------------------------ routes and accounting

        /** Counts a clear that took the named route (the backend's route sites call this). */
        public void noteRoute(ClearRoute route) {
            this.routeCounts[route.ordinal()]++;
        }

        /** {@return how many clears took the named route}. */
        public long routeCount(ClearRoute route) {
            return this.routeCounts[route.ordinal()];
        }

        /** {@return issued clears attributed to this state machine}. */
        public long issuedTotal() {
            return this.issuedTotal;
        }

        /** {@return issued clears that have been applied}. */
        public long appliedTotal() {
            return this.appliedTotal;
        }

        /** {@return issued clears dropped unapplied}. */
        public long droppedTotal() {
            return this.droppedTotal;
        }

        /** {@return carried clears not yet applied or rescued}. */
        public long carriedOutstanding() {
            return this.carriedOutstanding;
        }

        /**
         * Restores the applied count a recording snapshot saw, paired with
         * {@link #restore(long, PendingClear)}: the rollback hands back both the latch
         * record the recording consumed and the accounting its consumption moved.
         */
        public void restoreAppliedTotal(long appliedTotal) {
            this.appliedTotal = appliedTotal;
        }

        /**
         * {@return whether the accounting identity holds}: every issued clear is applied,
         * still pending, still carried, or counted dropped - none created, none lost. The
         * merge phase reads this in a leg; a false is a transition leak by construction,
         * not a tuning difference.
         */
        public boolean identityHolds() {
            return this.issuedTotal
                    == this.appliedTotal + pendingIssues() + this.carriedOutstanding
                    + this.droppedTotal;
        }

        /** {@return the accounting as one line}, for the diagnostics row that reports it. */
        public String accountingSummary() {
            return "issued=" + this.issuedTotal + " applied=" + this.appliedTotal
                    + " pending=" + pendingIssues() + " carried=" + this.carriedOutstanding
                    + " dropped=" + this.droppedTotal + " holds=" + identityHolds()
                    + " collisions=" + this.clearCollisions;
        }

        /**
         * {@return the route counts as one line, keyed by the legacy counter names} the
         * backend's individual counters used before the absorb (S2-1 absorb connection):
         * {@code QUEUED} is the on-path successor of {@code gameClearsQueued},
         * {@code IN_PASS} of {@code gameClearsAppliedInPass}, {@code STANDALONE} of
         * {@code gameClearsSubmittedStandalone}, {@code LATCH} of
         * {@code gameClearsLatchedForPairs}, {@code CARRIED} of
         * {@code mainClearsQueued} (the queue-carried main clear) and {@code RESCUED} of
         * {@code mainClearsRescued}. The correspondence is by route, not by identity of
         * values: the legacy counters also count the off path and the main/group split,
         * so a leg compares shapes and the {@link #identityHolds()} verdict, never a
         * numeric equality against a legacy run.
         */
        public String routeSummary() {
            StringBuilder line = new StringBuilder();
            for (ClearRoute route : ClearRoute.values()) {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(route.name()).append('=')
                        .append(this.routeCounts[route.ordinal()]);
            }
            return line.toString();
        }
    }
}
