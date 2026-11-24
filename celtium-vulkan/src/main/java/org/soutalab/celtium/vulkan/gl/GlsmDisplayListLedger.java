package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GLSM stage 3, GAP-ST-7 (census S-11, S-10's list half): the owner of the display-list
 * ledgers - compiled command lists, their captured geometry, their raw payloads, and the
 * executing/compiling bookkeeping.
 * <p>
 * CeltiumModelCapture stays a sink (it produces geometry; it does not own the ledger).
 * The four maps the stub kept privately move here with their shapes intact: commands as
 * {@code Object[]} rows per list, geometry as the backend buffer lists, payloads as the
 * raw byte buffers. The compiled/replayed/unresolved counters move too, so the exit
 * census the stub prints reads one owner.
 */
public final class GlsmDisplayListLedger {
    private static final GlsmDisplayListLedger CURRENT = new GlsmDisplayListLedger();

    public static GlsmDisplayListLedger current() { return CURRENT; }

    private final Map<Integer, List<Object[]>> lists = new HashMap<Integer, List<Object[]>>();
    private final Map<Integer, List<?>> geometry = new HashMap<Integer, List<?>>();
    private final Map<Integer, List<java.nio.ByteBuffer>> payloads =
            new HashMap<Integer, List<java.nio.ByteBuffer>>();
    private int compilingList;
    private int lastCalledList;
    private int listBase;
    private boolean executing;
    private long compiled, replayed, unresolved;
    private long version;

    private GlsmDisplayListLedger() {
    }

    public void putList(int list, List<Object[]> commands) {
        this.lists.put(list, commands);
        this.compiled++;
        this.version++;
    }

    public List<Object[]> list(int list) { return this.lists.get(list); }

    public boolean hasList(int list) { return this.lists.containsKey(list); }

    public void putGeometry(int list, List<?> buffers) { this.geometry.put(list, buffers); this.version++; }

    public List<?> geometry(int list) { return this.geometry.get(list); }

    public void putPayloads(int list, List<java.nio.ByteBuffer> buffers) {
        this.payloads.put(list, buffers);
        this.version++;
    }

    public List<java.nio.ByteBuffer> payloads(int list) { return this.payloads.get(list); }

    /** Deletes a list from every ledger at once - the stub's three separate removes could drift. */
    public void deleteList(int list) {
        this.lists.remove(list);
        this.geometry.remove(list);
        this.payloads.remove(list);
        this.version++;
    }

    public int heldLists() { return this.lists.size(); }

    public void compiling(int list) { this.compilingList = list; }
    public int compiling() { return this.compilingList; }

    // Rewiring additions (stage-3 routes): the compile mode, the executing list's id and
    // the remove-and-return forms the stub's release path needs (it closes/frees what it
    // removes, so a silent deleteList cannot serve it).
    private int compilingMode;
    private int executingListId;

    public void compilingMode(int mode) { this.compilingMode = mode; }
    public int compilingMode() { return this.compilingMode; }

    public void executingListId(int list) { this.executingListId = list; this.executing = list != 0; }
    public int executingListId() { return this.executingListId; }

    public List<Object[]> removeList(int list) { this.version++; return this.lists.remove(list); }

    @SuppressWarnings("unchecked")
    public <T> java.util.List<T> removeGeometry(int list) {
        this.version++;
        return (java.util.List<T>) this.geometry.remove(list);
    }

    public java.util.List<java.nio.ByteBuffer> removePayloads(int list) {
        this.version++;
        return this.payloads.remove(list);
    }

    public java.util.Collection<List<?>> geometryLists() { return this.geometry.values(); }

    public void noteCalled(int list) { this.lastCalledList = list; }
    public void noteReplayed() { this.replayed++; this.version++; }
    public void noteUnresolved() { this.unresolved++; this.version++; }

    public void called(int list, boolean resolved) {
        this.lastCalledList = list;
        this.executing = true;
        if (resolved) this.replayed++; else this.unresolved++;
    }

    public void callFinished() { this.executing = false; }
    public boolean executing() { return this.executing; }
    public int lastCalled() { return this.lastCalledList; }

    public void listBase(int base) { this.listBase = base; this.version++; }
    public int listBase() { return this.listBase; }

    public long compiled() { return this.compiled; }
    public long replayed() { return this.replayed; }
    public long unresolved() { return this.unresolved; }
    public long version() { return this.version; }
}
