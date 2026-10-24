package org.soutalab.celtium.vulkan;

/** Render-thread texture bindings shared by the native frontend and the mod adapter, independent of GL dispatch. */
public final class NativeTextureBindings {
    private static final NativeTextureBindings CURRENT = new NativeTextureBindings(NativeRasterState.current());
    private final NativeRasterState boundaries;
    private final int[] texture2d = new int[32];
    private final boolean[] enabled2d = new boolean[32];
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
