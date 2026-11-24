package org.soutalab.celtium.vulkan.gl;

import java.util.HashSet;
import java.util.Set;

/**
 * GLSM stage 3, GAP-ST-10 (covers census S-2 and S-22): the live-name ledger.
 * <p>
 * {@code RenderObjectIds} stays the monotonic counter only (its 19 lines are the invariant:
 * names are never reused). This ledger is the separate owner the superset proof calls for:
 * the live set behind SEM_GEN / SEM_DELETE / SEM_IS_OBJECT and the Q-6 existence answers,
 * the sync ledger, and the null-allocated set (names a gen call handed out whose storage
 * allocation was a no-op, which the stub must still answer as existing).
 */
public final class GlsmNameLedger {
    private static final GlsmNameLedger CURRENT = new GlsmNameLedger();

    public static GlsmNameLedger current() { return CURRENT; }

    private final Set<Integer> liveNames = new HashSet<Integer>();
    private final Set<Long> liveSync = new HashSet<Long>();
    private final Set<Integer> nullAllocatedNames = new HashSet<Integer>();
    private long nextSync = 1L;

    private GlsmNameLedger() {
    }

    public void addName(int name) { this.liveNames.add(name); }

    public void addNames(int first, int count) {
        for (int i = 0; i < count; i++) this.liveNames.add(first + i);
    }

    /** @return true when the name was live (a delete of an unknown name is a GL no-op). */
    public boolean removeName(int name) {
        this.nullAllocatedNames.remove(name);
        return this.liveNames.remove(name);
    }

    public boolean isLive(int name) { return this.liveNames.contains(name); }

    public void markNullAllocated(int name) {
        this.liveNames.add(name);
        this.nullAllocatedNames.add(name);
    }

    public boolean isNullAllocated(int name) { return this.nullAllocatedNames.contains(name); }

    /** Adds only the null-allocated classification (the name's liveness is recorded separately). */
    public void addNullAllocated(int name) { this.nullAllocatedNames.add(name); }

    public void removeNullAllocated(int name) { this.nullAllocatedNames.remove(name); }

    public int liveCount() { return this.liveNames.size(); }

    /** Sync objects live in a 64-bit namespace the int name ledger cannot hold. */
    public long newSync() {
        long sync = this.nextSync++;
        this.liveSync.add(sync);
        return sync;
    }

    public void addSync(long sync) { this.liveSync.add(sync); }

    public boolean removeSync(long sync) { return this.liveSync.remove(sync); }

    public boolean isLiveSync(long sync) { return this.liveSync.contains(sync); }
}
