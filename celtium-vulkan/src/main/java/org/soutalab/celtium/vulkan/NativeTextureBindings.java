package org.soutalab.celtium.vulkan;

/** Render-thread texture bindings shared by the native frontend and the mod adapter, independent of GL dispatch. */
public final class NativeTextureBindings {
    private static final NativeTextureBindings CURRENT = new NativeTextureBindings(NativeRasterState.current());
    /** The units this ledger holds; the same count the stub advertises per stage (TEXTURE_UNITS_PER_STAGE). */
    public static final int UNIT_COUNT = 32;
    private final NativeRasterState boundaries;
    private final int[] texture2d = new int[UNIT_COUNT];
    private final boolean[] enabled2d = new boolean[UNIT_COUNT];
    /** Sampler objects bound per unit by {@code glBindSampler} (GLSM M-4); 0 = no sampler object. */
    private final int[] samplers = new int[UNIT_COUNT];
    private int activeUnit;

    public NativeTextureBindings(NativeRasterState boundaries) {
        this.boundaries = boundaries;
        java.util.Arrays.fill(this.enabled2d, true);
    }
    public static NativeTextureBindings current() { return CURRENT; }
    public int activeUnit() { return this.activeUnit; }
    public void activeUnit(int unit) {
        if (unit < 0 || unit >= this.texture2d.length) return;
        this.activeUnit = unit;
    }
    private int unitIndex(int unit) { return unit >= 0 && unit < this.texture2d.length ? unit : 0; }
    public int texture2d(int unit) { return this.texture2d[unitIndex(unit)]; }
    public int texture2d() { return this.texture2d[this.activeUnit]; }
    public boolean unchanged(int texture) { return texture2d() == texture; }
    public void bindTexture2d(int texture) {
        if (texture < 0 || unchanged(texture)) return;
        // A pending glyph run belongs to the previous material. The callback can temporarily bind that material;
        // preserve the requested unit across it rather than relying on what a callback leaves active.
        int unit = this.activeUnit;
        this.boundaries.flushBeforeChange();
        this.texture2d[unit] = texture;
    }
    /**
     * Records a {@code glBindSampler} binding. Unlike {@link #texture2d(int)}'s read side, an out-of-range
     * unit is ignored rather than folded onto unit 0 (the same discipline as {@link #activeUnit(int)}):
     * folding a write would corrupt unit 0's sampler with a binding GL addressed elsewhere.
     */
    public void bindSampler(int unit, int sampler) {
        if (unit < 0 || unit >= UNIT_COUNT) return;
        this.samplers[unit] = sampler;
    }
    public int sampler(int unit) { return unit >= 0 && unit < UNIT_COUNT ? this.samplers[unit] : 0; }
    public boolean enabled2d(int unit) { return this.enabled2d[unitIndex(unit)]; }
    public void enabled2d(boolean enabled) {
        int unit = this.activeUnit;
        if (this.enabled2d[unit] == enabled) return;
        this.boundaries.flushBeforeChange();
        this.enabled2d[unit] = enabled;
    }
    /** Deletion changes future bindings; already accepted groups keep their own texture generation and lease. */
    public void delete(int texture) {
        if (texture <= 0) return;
        boolean bound = false;
        for (int value : this.texture2d) bound |= value == texture;
        if (!bound) return;
        this.boundaries.flushBeforeChange();
        for (int unit = 0; unit < this.texture2d.length; unit++) {
            if (this.texture2d[unit] == texture) this.texture2d[unit] = 0;
        }
    }
}
