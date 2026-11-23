package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-HINT-1 (census I-1/I-2/I-3/I-6): the receiving ledger for the Hint
 * doors, the second surface beside the 476-name function table.
 * <p>
 * CeltiumGllessHint carries 275 public statics on the current alpha tip (266 doors in
 * the census's passtail accounting). Those doors are entry points, not owners: every
 * door that lands guest state must name the GLSM owner it writes, and this ledger is
 * that naming - door name to owner id, with an arrival count so the merge phase can
 * see which doors real packs actually use. The three installation facts the census
 * groups here (coremod install, the FunctionProvider route, the capabilities-first
 * install of 427 extension names) are recorded as owned facts of this ledger too:
 * they are part of the surface the stub's deletion must account for.
 */
public final class GlsmHintLedger {
    private static final GlsmHintLedger CURRENT = new GlsmHintLedger();

    public static GlsmHintLedger current() { return CURRENT; }

    /** Door name -> the GLSM owner id it writes (e.g. "enable", "viewport", "clear"). */
    private final Map<String, String> doorOwners = new HashMap<String, String>();
    private final Map<String, Long> doorArrivals = new HashMap<String, Long>();
    private boolean stubInstalled;
    private boolean functionProviderInstalled;
    private int capabilitiesExtensions;

    private GlsmHintLedger() {
    }

    static {
        // Doors whose receiving end this stage-3 landing rewired, registered with the owner
        // they now write. The remaining doors register as their routes land; arrivals at an
        // unregistered door are the ledger's unroutedArrivals() leftover list for merge.
        CURRENT.registerDoor("glViewport", "viewport");
        CURRENT.registerDoor("glClearColor", "clear");
        CURRENT.registerDoor("glClearDepth", "clear");
        CURRENT.registerDoor("glDepthMask", "enable");
        CURRENT.registerDoor("glDepthFunc", "raster");
        CURRENT.registerDoor("glLineWidth", "immediate");
        CURRENT.registerDoor("glPointSize", "immediate");
        CURRENT.registerDoor("glBindRenderbuffer", "clientArray");
        CURRENT.registerDoor("glRenderbufferStorage", "clientArray");
        CURRENT.registerDoor("glListBase", "displayList");
        CURRENT.registerDoor("glPushAttrib", "attribStacks");
        CURRENT.registerDoor("glPopAttrib", "attribStacks");
        CURRENT.registerDoor("glPushClientAttrib", "attribStacks");
        CURRENT.registerDoor("glPopClientAttrib", "attribStacks");
    }

    public void registerDoor(String door, String ownerId) { this.doorOwners.put(door, ownerId); }

    /** @return the owner id a door writes, or null when the door has no GLSM receiving end yet. */
    public String ownerOf(String door) { return this.doorOwners.get(door); }

    public void noteArrival(String door) {
        Long count = this.doorArrivals.get(door);
        this.doorArrivals.put(door, Long.valueOf(count == null ? 1L : count.longValue() + 1L));
    }

    public long arrivals(String door) {
        Long count = this.doorArrivals.get(door);
        return count == null ? 0L : count.longValue();
    }

    public int registeredDoors() { return this.doorOwners.size(); }

    /** Doors seen arriving with no registered owner - the merge phase's leftover list. */
    public int unroutedArrivals() {
        int total = 0;
        for (Map.Entry<String, Long> entry : this.doorArrivals.entrySet()) {
            if (!this.doorOwners.containsKey(entry.getKey())) total += entry.getValue().intValue();
        }
        return total;
    }

    public void noteStubInstalled() { this.stubInstalled = true; }
    public boolean stubInstalled() { return this.stubInstalled; }

    public void noteFunctionProviderInstalled() { this.functionProviderInstalled = true; }
    public boolean functionProviderInstalled() { return this.functionProviderInstalled; }

    public void noteCapabilitiesInstalled(int extensionCount) {
        this.capabilitiesExtensions = extensionCount;
    }

    public int capabilitiesExtensions() { return this.capabilitiesExtensions; }
}
