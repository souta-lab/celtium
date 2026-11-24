package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-ST-1: the single owner of the guest's capability enable/disable record.
 * <p>
 * Mirrors the stub's flat-array shape exactly (GlsmFrontend: enables byte[0x10000] plus
 * capabilityObserved boolean[0x10000]): capability constants are dense and bounded well under
 * 0x10000, an index of 0 means "never set" which GL reads as disabled, and the observed bit
 * distinguishes an observed disable from an untouched provider default - a native replacement
 * that bypasses callbacks must never erase state captured through Minecraft's API.
 * <p>
 * This owner is the landing target for SEM_ENABLE / SEM_DISABLE / SEM_IS_ENABLED and for the
 * GlStateManagerMixin mirror; the stub keeps its own copy until the merge phase re-points those
 * routes here (stage-3 alpha discipline: owners land first, rewiring is the merge phase's job).
 */
public final class GlsmEnableState {
    private static final int FLAT_MAP_SIZE = 0x10000;
    private static final GlsmEnableState CURRENT = new GlsmEnableState();

    public static GlsmEnableState current() { return CURRENT; }

    private final byte[] enables = new byte[FLAT_MAP_SIZE];
    private final boolean[] capabilityObserved = new boolean[FLAT_MAP_SIZE];
    private long version;

    private GlsmEnableState() {
    }

    /** Records one enable/disable; out-of-range capabilities are counted, never stored. */
    public void set(int capability, boolean enabled) {
        if (capability < 0 || capability >= FLAT_MAP_SIZE) return;
        byte value = enabled ? (byte) 1 : (byte) 0;
        if (this.enables[capability] != value) {
            this.enables[capability] = value;
            this.version++;
        }
        this.capabilityObserved[capability] = true;
    }

    public boolean enabled(int capability) {
        return capability >= 0 && capability < FLAT_MAP_SIZE && this.enables[capability] != 0;
    }

    /** Whether any route has ever recorded this capability (vs. an untouched default). */
    public boolean observed(int capability) {
        return capability >= 0 && capability < FLAT_MAP_SIZE && this.capabilityObserved[capability];
    }

    /** Bumped only when a stored value actually changes; the replay key folds this in. */
    public long version() { return this.version; }
}
