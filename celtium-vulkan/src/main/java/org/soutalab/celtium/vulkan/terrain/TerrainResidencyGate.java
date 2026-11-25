package org.soutalab.celtium.vulkan.terrain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The leak counters and per-pass residency verification gate that the
 * VulkanArena deletion stands on (coord ruling on the wave-2 item (5) survey,
 * order (i)): before the legacy route can be removed, every section the
 * renderer tried to place in Tier-A must be accounted for — resident, or
 * missing for a named reason.
 * <p>
 * Before this gate existed the permanent leaks were uninstrumented: a section
 * rejected for oversize, an exhausted arena, the vertex-stride gate or a build
 * exception simply never entered Tier-A, and on an armed pass it was not drawn
 * by the legacy route either — it was missing, silently. This class is the
 * bookkeeping that makes that state observable and verifiable:
 * <ul>
 *   <li><b>Reason counters</b> — cumulative event counts per {@link Reason},
 *       incremented at the exact site that decides the section's fate.</li>
 *   <li><b>Expected set</b> — every (section, pass) the renderer attempted to
 *       register, with the reason it is currently missing, if any. A section
 *       leaves the missing set only by becoming resident, and leaves the
 *       expected set only by being unregistered (rebuilt or unloaded).</li>
 *   <li><b>Per-pass verification</b> — {@link #verify(int, TerrainSectionStore)}
 *       cross-checks one pass' expected set against the store's live records
 *       and returns a {@link Report}; {@code complete} is the exact predicate
 *       the arena deletion needs ("no section of this pass is missing").</li>
 *   <li><b>Head-of-line detection</b> — a deferred upload whose slab bytes
 *       exceed the whole per-frame budget can never drain (the deferred queue
 *       is FIFO and admits only at its head); such keys are counted once and
 *       named in the report instead of blocking the queue invisibly forever.</li>
 * </ul>
 * Pure CPU state, no Vulkan: the store, the engines and the renderer feed it,
 * the pass head reads its verdict. All methods are synchronized; registration
 * and frame preparation run on different threads.
 */
public final class TerrainResidencyGate {

    /** Why a section that was expected in Tier-A is not resident. */
    public enum Reason {
        /** Queued behind the per-frame upload budget; transient unless head-of-line blocked. */
        DEFERRED,
        /** A bucket exceeds the SectionDesc uint16 quad cap; the store refuses it outright. */
        OVERSIZE,
        /** An arena (geometry / quad space / factor / sorted, or a slab mismatch) is exhausted. */
        ARENA_FULL,
        /** The region was built with a vertex stride other than TerrainVertex16's, so registration was never attempted. */
        STRIDE_GATE,
        /** The Tier-A build itself threw (non-contiguous ranges, factor length mismatch, ...). */
        BUILD_EXCEPTION
    }

    /**
     * One pass' verification result: how many of its expected sections are
     * resident, how many are missing and for which reasons, and whether the
     * pass is complete (the deletion precondition for that pass).
     */
    public record Report(int passOrdinal, int expected, int resident, int missing,
                         EnumMap<Reason, Integer> missingByReason, int headOfLineBlocked) {
        public boolean complete() {
            return this.missing == 0;
        }
    }

    private final Map<TerrainSectionStore.Key, Reason> missing = new HashMap<>();
    private final Set<TerrainSectionStore.Key> expected = new HashSet<>();
    private final Set<TerrainSectionStore.Key> headOfLine = new HashSet<>();
    private final EnumMap<Reason, Long> counters = new EnumMap<>(Reason.class);

    /** Records that the renderer attempted to place this section in Tier-A. */
    public synchronized void expect(TerrainSectionStore.Key key) {
        this.expected.add(key);
    }

    /**
     * Records one leak event: the section is (still) expected but not resident,
     * for {@code reason}. Increments the reason's cumulative counter.
     */
    public synchronized void noteMissing(TerrainSectionStore.Key key, Reason reason) {
        this.expected.add(key);
        this.missing.put(key, reason);
        this.counters.merge(reason, 1L, Long::sum);
    }

    /** Records that the section is now resident: it leaves the missing set. */
    public synchronized void noteResident(TerrainSectionStore.Key key) {
        this.expected.add(key);
        this.missing.remove(key);
        this.headOfLine.remove(key);
    }

    /**
     * Records that a deferred section can never drain: its slab bytes exceed
     * the whole per-frame budget, so the FIFO head admission will refuse it on
     * every frame. Counted once per key until it becomes resident or is
     * forgotten.
     */
    public synchronized void noteHeadOfLine(TerrainSectionStore.Key key) {
        this.expected.add(key);
        this.missing.putIfAbsent(key, Reason.DEFERRED);
        this.headOfLine.add(key);
    }

    /** Drops a section from all tracking (it was unregistered: rebuilt or unloaded). */
    public synchronized void forget(TerrainSectionStore.Key key) {
        this.expected.remove(key);
        this.missing.remove(key);
        this.headOfLine.remove(key);
    }

    /** Cumulative event count for one reason (0 when nothing was ever recorded). */
    public synchronized long count(Reason reason) {
        return this.counters.getOrDefault(reason, 0L);
    }

    /** Sections currently known to be head-of-line blocked in the deferred queue. */
    public synchronized int headOfLineBlocked() {
        return this.headOfLine.size();
    }

    /**
     * Cross-checks one pass' expected set against the store's live records.
     * A key the gate believes resident but the store no longer holds (or the
     * reverse) is reconciled from the store, which is the residency truth:
     * the report counts store presence, and the missing-by-reason breakdown
     * attributes every absent expected key to its last recorded reason.
     * A key absent with no recorded reason is counted in {@code missing} and
     * in no reason bucket, so a bookkeeping gap stays visible as a sum
     * mismatch rather than being papered over with an invented reason.
     */
    public synchronized Report verify(int passOrdinal, TerrainSectionStore store) {
        int expectedCount = 0;
        int resident = 0;
        EnumMap<Reason, Integer> byReason = new EnumMap<>(Reason.class);
        int head = 0;
        for (TerrainSectionStore.Key key : this.expected) {
            if (key.passOrdinal() != passOrdinal) {
                continue;
            }
            expectedCount++;
            if (store.get(key) != null) {
                resident++;
                continue;
            }
            Reason reason = this.missing.get(key);
            if (reason != null) {
                byReason.merge(reason, 1, Integer::sum);
            }
            if (this.headOfLine.contains(key)) {
                head++;
            }
        }
        return new Report(passOrdinal, expectedCount, resident, expectedCount - resident, byReason, head);
    }

    /** One log-shaped line of a report, for the pass head's periodic census. */
    public static String describe(Report report) {
        StringBuilder out = new StringBuilder();
        out.append("pass=").append(report.passOrdinal())
                .append(" expected=").append(report.expected())
                .append(" resident=").append(report.resident())
                .append(" missing=").append(report.missing())
                .append(" complete=").append(report.complete());
        for (Map.Entry<Reason, Integer> entry : report.missingByReason().entrySet()) {
            out.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        out.append(" headOfLine=").append(report.headOfLineBlocked());
        return out.toString();
    }
}
