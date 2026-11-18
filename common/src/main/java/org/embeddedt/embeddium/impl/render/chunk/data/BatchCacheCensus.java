package org.embeddedt.embeddium.impl.render.chunk.data;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The process-wide census behind {@code CELTIUM_BATCH_CACHE}: hits, misses and stores of every region storage's
 * {@link BatchCache}, plus the three fields item 397's plan (§2.5) fixed and item 406 implemented - {@code inval}
 * (invalidations that dropped at least one entry), {@code keyMismatch} (misses against a cache that held only
 * other keys) and {@code views} (the distinct views observed, by identity).
 * <p>
 * The printed line is bounded on purpose: one line every {@value #PRINT_EVERY_LOOKUPS} lookups, at most
 * {@value #MAX_LINES} lines per process. The bound caps the printing only - the counters keep counting after it,
 * so a value in a printed line is a count, while the absence of a later line is not the absence of the event
 * (see docs/verification-traps.md on bounded instruments). What can still change after the bound is re-reported
 * on change instead: the first store of a view the census has not seen yields one {@code CELTIUM_BATCH_VIEWS}
 * transition line, at most {@value #MAX_VIEWS} of them; a ninth distinct view yields a single
 * {@code views=overflow} line and is not tracked.
 * <p>
 * {@code views} counts identities across the whole process, not per storage: a storage's own entry count is
 * printed separately as {@code entries=}, which is what the field called {@code views=} held before item 406 -
 * legs from before the rename read {@code views=} as an entry count.
 */
final class BatchCacheCensus {
    /** Lookups between printed {@code CELTIUM_BATCH_CACHE} lines. */
    static final int PRINT_EVERY_LOOKUPS = 2000;

    /** Printed {@code CELTIUM_BATCH_CACHE} lines per process; the counters are not capped with them. */
    static final int MAX_LINES = 12;

    /** Distinct views tracked; a ninth is reported once as an overflow and not tracked. */
    static final int MAX_VIEWS = 8;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong stores = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();
    private final AtomicLong keyMismatches = new AtomicLong();
    private final AtomicLong prints = new AtomicLong();
    private volatile boolean printsExhausted;
    private volatile int lastEntries;

    private final IdentityHashMap<Object, Boolean> views = new IdentityHashMap<>();
    private boolean overflowAnnounced;

    /**
     * Counts one lookup against a cache holding {@code entries} entries. A miss against a non-empty cache is a
     * key mismatch: the looked-up key holds no entry, so every entry the cache holds is under another key - and a
     * storage's key can only vary by view (the culling flag is a fixed property of the storage's pass), so a
     * mismatch is the observable shape of two views alternating through one storage.
     */
    void noteLookup(boolean hit, int entries) {
        this.lastEntries = entries;
        if (hit) {
            this.hits.incrementAndGet();
        } else {
            this.misses.incrementAndGet();
            if (entries > 0) {
                this.keyMismatches.incrementAndGet();
            }
        }
    }

    /**
     * Counts one store. {@return the {@code CELTIUM_BATCH_VIEWS} transition line when {@code view} is one this
     * census has not seen, or {@code null} when it was already known} The caller prints the line; after the
     * single overflow line has been given out, further new views return {@code null} as well.
     */
    @Nullable
    String noteStore(Object view) {
        this.stores.incrementAndGet();
        synchronized (this.views) {
            if (this.views.containsKey(view)) {
                return null;
            }
            if (this.views.size() >= MAX_VIEWS) {
                if (this.overflowAnnounced) {
                    return null;
                }
                this.overflowAnnounced = true;
                return "[Celtium] CELTIUM_BATCH_VIEWS views=overflow cap=" + MAX_VIEWS;
            }
            this.views.put(view, Boolean.TRUE);
            return "[Celtium] CELTIUM_BATCH_VIEWS views=" + this.views.size()
                    + " lookups=" + this.lookups() + this.snapshot();
        }
    }

    /** Counts one invalidation, but only when it dropped at least one entry: an empty invalidation carries no information. */
    void noteInvalidate(int entriesBefore) {
        if (entriesBefore > 0) {
            this.invalidations.incrementAndGet();
        }
    }

    /**
     * {@return the next {@code CELTIUM_BATCH_CACHE} line when one is due, or {@code null}} A line is due every
     * {@value #PRINT_EVERY_LOOKUPS} lookups until {@value #MAX_LINES} lines have been given out; after that this
     * returns {@code null} for good while the counters keep counting.
     */
    @Nullable
    String line() {
        if (this.printsExhausted) {
            return null;
        }
        long total = this.lookups();
        if (total == 0 || total % PRINT_EVERY_LOOKUPS != 0) {
            return null;
        }
        if (this.prints.getAndIncrement() >= MAX_LINES) {
            this.printsExhausted = true;
            return null;
        }
        return "[Celtium] CELTIUM_BATCH_CACHE lookups=" + total + this.snapshot()
                + " views=" + this.viewCount() + " entries=" + this.lastEntries;
    }

    private String snapshot() {
        return " hits=" + this.hits.get() + " misses=" + this.misses.get()
                + " stores=" + this.stores.get() + " inval=" + this.invalidations.get()
                + " keyMismatch=" + this.keyMismatches.get();
    }

    long lookups() {
        return this.hits.get() + this.misses.get();
    }

    long hits() {
        return this.hits.get();
    }

    long misses() {
        return this.misses.get();
    }

    long stores() {
        return this.stores.get();
    }

    long invalidations() {
        return this.invalidations.get();
    }

    long keyMismatches() {
        return this.keyMismatches.get();
    }

    int viewCount() {
        synchronized (this.views) {
            return this.views.size();
        }
    }
}
