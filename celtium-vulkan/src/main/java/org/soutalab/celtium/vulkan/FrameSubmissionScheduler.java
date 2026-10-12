package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

/**
 * Which submission a resource is waiting for, and when a command slot may be used again (refactoring plan R6).
 * <p>
 * The class is state plus decisions. It allocates nothing, calls no Vulkan entry point and never calls back into the
 * renderer: the backend keeps its {@code VkCommandBuffer}/{@code VkFence} objects and asks this class whether a slot
 * may be recorded into, waited for or reset, and the resources themselves release their own memory when told to.
 * <p>
 * The rule this class exists to hold in one place, named rather than repeated as a comment: <b>a resource retires to
 * a slot and is released when that slot is reset</b>, because the completion precondition is the slot's own
 * ({@link #mayResetSlot(int, long, long)}), and because no earlier ticket exists. Measured on 2026-10-12 while fixing
 * audit B05: the deferred queue's newest fence at growth time belongs to an <em>older</em> batch than the one that
 * already recorded the old handle, and the slot's epoch is still {@code -1} while the frame is being recorded, so
 * neither can be the ticket. Retiring to a deferred fence and retiring to the slot epoch were both tried on the GPU
 * and both legs still reported {@code VUID-vkDestroyBuffer-buffer-00922}; retiring into the slot and releasing at its
 * reset reported {@code validationMessageCount=0} on the leg that reproduces the defect
 * ({@code docs/audit-b05-reproduction-2026-10-12.md}).
 * <p>
 * The second condition R6 states is a prohibition: <b>nothing here may add a device-wide wait to the normal frame
 * path</b>. Every method is therefore either a counter, a comparison, or a decision the caller acts on with its own
 * bounded operation, and the only "wait" in the vocabulary is the per-slot one the backend already performs.
 */
public final class FrameSubmissionScheduler {

    /**
     * Command slots the frame cycles through.
     * <p>
     * Four, as measured by OPT-08: the frame is only ready to be recorded again once the slot it used two frames ago
     * has completed, so fewer slots turn into waits and more slots hold more fences and command buffers for no gain.
     */
    public static final int FRAME_SLOTS = 4;

    /** Something that owns an allocation it can release once the submission that reads it has completed. */
    public interface Retirable {
        /** Releases the allocation. Called only from {@link #releaseOnSlotReset(int)} or {@link #releaseAll()}. */
        void release();
    }

    /** One retirement: what is owed, which slot owes it, and the submission that must complete first. */
    public static final class Retirement {
        private final Retirable resource;
        private final long submitSequence;
        private final String what;

        Retirement(Retirable resource, long submitSequence, String what) {
            this.resource = resource;
            this.submitSequence = submitSequence;
            this.what = what;
        }

        /** {@return the resource to release}. */
        public Retirable resource() {
            return this.resource;
        }

        /** {@return the submission sequence the release waits for}, as recorded when it was retired. */
        public long submitSequence() {
            return this.submitSequence;
        }

        /** {@return what was retired}, for a report or a dump. */
        public String what() {
            return this.what;
        }
    }

    // ---- the slots -----------------------------------------------------------------------------------------

    /** Whether a submission is in flight for this slot; the backend's {@code slotPending[slot]}. */
    private final boolean[] slotPending = new boolean[FRAME_SLOTS];
    /** The sequence of the submission that last used this slot; the backend's {@code slotSubmitSequence[slot]}. */
    private final long[] slotSubmitSequence = new long[FRAME_SLOTS];
    /**
     * Whether the slot's submission has been confirmed complete by a fence wait or a poll.
     * <p>
     * This is the arena's {@code awaitingCompletion} for the renderer's own slots: a slot that is pending is not
     * reusable, and a slot whose completion was confirmed is. It is tracked here so the backend, the arena and the
     * present path answer the question from one state rather than from three.
     */
    private final boolean[] slotAwaitingCompletion = new boolean[FRAME_SLOTS];
    /** What each slot owes a release to, in retirement order; released by {@link #releaseOnSlotReset(int)}. */
    private final List<List<Retirement>> slotRetirements = new ArrayList<List<Retirement>>(FRAME_SLOTS);

    // ---- the ledger ----------------------------------------------------------------------------------------

    /** The sequence handed to the newest submission, incremented once per submit (backend {@code submitSequence}). */
    private long submitSequence;
    /** The newest sequence confirmed complete (backend {@code completedSubmitSequence}). */
    private long completedSubmitSequence;
    /** Submissions whose completion was confirmed, and the waits that confirmed them. */
    private int submitCompletions;
    /** Wait outcomes seen by the frame path: success, timeout, device loss, other. */
    private long waitSuccesses;
    private long waitTimeouts;
    private long waitDeviceLosses;
    private long waitFailures;

    // ---- the counters the VERIFY line prints ---------------------------------------------------------------

    /** Slots whose reuse had to wait for a fence (backend {@code slotReuseWaits}). */
    private long slotReuseWaits;
    /** Non-blocking fence queries (backend {@code slotPolls}) and how many of them confirmed completion. */
    private long slotPolls;
    private long slotPollConfirms;
    /** Blocking per-slot waits and how many of them confirmed completion (backend {@code slotWaitConfirms}). */
    private long slotWaitConfirms;

    public FrameSubmissionScheduler() {
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            this.slotRetirements.add(new ArrayList<Retirement>());
        }
    }

    // ---- decisions the backend acts on ---------------------------------------------------------------------

    /** {@return whether the slot has a submission in flight}. */
    public boolean isPending(int slot) {
        return slot >= 0 && slot < FRAME_SLOTS && this.slotPending[slot];
    }

    /** {@return whether a submission for this slot has been recorded and not yet confirmed complete}. */
    public boolean isAwaitingCompletion(int slot) {
        return slot >= 0 && slot < FRAME_SLOTS && this.slotAwaitingCompletion[slot];
    }

    /**
     * Records that the slot is being submitted now.
     *
     * @return the sequence of this submission, which is what {@link #retireToSlot(int, Retirable, String)} stores
     */
    public long noteSubmitted(int slot) {
        requireSlot(slot);
        this.slotPending[slot] = true;
        this.slotAwaitingCompletion[slot] = true;
        this.slotSubmitSequence[slot] = ++this.submitSequence;
        return this.slotSubmitSequence[slot];
    }

    /**
     * Records that the slot's submission completed.
     * <p>
     * {@code confirmedSubmitSequence} is optional - 0 means "no sequence was read" - and the ledger only ever moves
     * forward, because a stale confirmation must not reopen a slot that a newer submission now owns.
     */
    public void noteCompleted(int slot, long confirmedSubmitSequence) {
        requireSlot(slot);
        this.slotPending[slot] = false;
        this.slotAwaitingCompletion[slot] = false;
        this.submitCompletions++;
        if (confirmedSubmitSequence > this.completedSubmitSequence) {
            this.completedSubmitSequence = confirmedSubmitSequence;
        }
    }

    /**
     * {@return whether the slot may be reset, i.e. its submission has completed}.
     * <p>
     * This is the precondition the frame arena already relies on before it resets a slot
     * ({@code !awaitingCompletion && epoch <= completedEpoch}) expressed once, for every caller. {@code slotEpoch} is
     * the completion epoch the slot's submission must reach and {@code completedEpoch} the arena's newest confirmed
     * one; a slot that was never submitted ({@code slotEpoch < 0}) is always resettable.
     */
    public boolean mayResetSlot(int slot, long slotEpoch, long completedEpoch) {
        return !isAwaitingCompletion(slot) && (slotEpoch < 0L || slotEpoch <= completedEpoch);
    }

    /** {@return whether the slot can be recorded into now, without waiting}, i.e. nothing is in flight for it. */
    public boolean canRecordInto(int slot) {
        return !isPending(slot);
    }

    /**
     * Records a wait that the frame path is about to pay for this slot, before it is paid.
     * <p>
     * Counted here rather than at the wait so the number exists even when the wait is interrupted, and the caller
     * passes the outcome afterwards through {@link #noteWaitOutcome(String)}.
     */
    public void noteSlotReuseWait(int slot) {
        requireSlot(slot);
        this.slotReuseWaits++;
    }

    /** Records a non-blocking fence query and whether it confirmed completion. */
    public void notePoll(boolean confirmed) {
        this.slotPolls++;
        if (confirmed) {
            this.slotPollConfirms++;
        }
    }

    /** Records a blocking per-slot wait and whether it confirmed completion. */
    public void noteSlotWait(boolean confirmed) {
        if (confirmed) {
            this.slotWaitConfirms++;
        }
    }

    /** Records the outcome of a fence wait, the vocabulary {@code waitOutcomeSummary()} prints. */
    public void noteWaitOutcome(String outcome) {
        if (outcome == null) {
            this.waitFailures++;
        } else if (outcome.contains("SUCCESS")) {
            this.waitSuccesses++;
        } else if (outcome.contains("TIMEOUT")) {
            this.waitTimeouts++;
        } else if (outcome.contains("DEVICE_LOST")) {
            this.waitDeviceLosses++;
        } else {
            this.waitFailures++;
        }
    }

    // ---- the rule, in one place ----------------------------------------------------------------------------

    /**
     * Retires a resource to a slot: it is released when that slot is reset, never earlier.
     * <p>
     * The resource must be one that only the submission of {@code slot} can read - a buffer the frame's own command
     * buffer recorded against, an allocation a batch replaced, a descriptor set a pending draw uses. The submission
     * sequence recorded beside it is diagnostic, not the ticket: the ticket is the slot reset, because that is the
     * first moment completion is actually known (audit B05).
     */
    public void retireToSlot(int slot, Retirable resource, String what) {
        requireSlot(slot);
        if (resource == null) {
            return;
        }
        this.slotRetirements.get(slot).add(new Retirement(resource, this.slotSubmitSequence[slot], what));
    }

    /**
     * Releases everything retired to the slot. Call this from the slot's reset, after {@link #mayResetSlot(int, long,
     * long)} has said the slot's submission completed - and only from there, so that "released at reset" stays a
     * property of the code rather than of every call site's discipline.
     *
     * @return how many resources were released
     */
    public int releaseOnSlotReset(int slot) {
        requireSlot(slot);
        List<Retirement> owed = this.slotRetirements.get(slot);
        int released = 0;
        for (int i = 0; i < owed.size(); i++) {
            owed.get(i).resource().release();
            released++;
        }
        owed.clear();
        return released;
    }

    /**
     * Releases everything retired to every slot. Only for teardown, where no submission is outstanding any more -
     * calling it while frames are in flight would release memory a submission still reads.
     */
    public int releaseAll() {
        int released = 0;
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            released += releaseOnSlotReset(slot);
        }
        return released;
    }

    /** {@return how many resources the slot still owes a release to}. */
    public int pendingRetirements(int slot) {
        requireSlot(slot);
        return this.slotRetirements.get(slot).size();
    }

    /** {@return every retirement still owed, across the slots}, for a report or a leak check. */
    public List<Retirement> pendingRetirements() {
        List<Retirement> all = new ArrayList<Retirement>();
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            all.addAll(this.slotRetirements.get(slot));
        }
        return all;
    }

    // ---- what a report prints ------------------------------------------------------------------------------

    /** {@return the newest sequence handed out}. */
    public long submitSequence() {
        return this.submitSequence;
    }

    /** {@return the newest sequence confirmed complete}. */
    public long completedSubmitSequence() {
        return this.completedSubmitSequence;
    }

    /** {@return the submission sequence the slot last used}, 0 when it never has. */
    public long slotSubmitSequence(int slot) {
        requireSlot(slot);
        return this.slotSubmitSequence[slot];
    }

    /** {@return the wait-outcome census}, the shape {@code VulkanContext.waitOutcomeSummary()} already prints. */
    public String waitOutcomeSummary() {
        return "completions=" + this.submitCompletions + " success=" + this.waitSuccesses
                + " timeout=" + this.waitTimeouts + " deviceLost=" + this.waitDeviceLosses
                + " other=" + this.waitFailures;
    }

    /** {@return the slot census}, the fields the backend's resource line prints today. */
    public String slotSummary() {
        StringBuilder text = new StringBuilder("frameSlots=").append(FRAME_SLOTS);
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            text.append(" slot").append(slot).append("[pending=").append(this.slotPending[slot])
                    .append(" awaiting=").append(this.slotAwaitingCompletion[slot])
                    .append(" sequence=").append(this.slotSubmitSequence[slot])
                    .append(" owed=").append(this.slotRetirements.get(slot).size()).append(']');
        }
        text.append(" slotReuseWaits=").append(this.slotReuseWaits)
                .append(" slotPolls=").append(this.slotPolls)
                .append(" slotPollConfirms=").append(this.slotPollConfirms)
                .append(" slotWaitConfirms=").append(this.slotWaitConfirms)
                .append(" submitSequence=").append(this.submitSequence)
                .append(" completedSubmitSequence=").append(this.completedSubmitSequence);
        return text.toString();
    }

    private static void requireSlot(int slot) {
        if (slot < 0 || slot >= FRAME_SLOTS) {
            throw new IllegalArgumentException("frame slot " + slot + " is outside 0.." + (FRAME_SLOTS - 1));
        }
    }
}
