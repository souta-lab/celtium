package org.soutalab.celtium.vulkan;

/** Render-thread texture bindings shared by the native frontend and the mod adapter, independent of GL dispatch. */
public final class NativeTextureBindings {
    private static final NativeTextureBindings CURRENT = new NativeTextureBindings(NativeRasterState.current());
    /** The units this ledger holds; the same count the stub advertises per stage (TEXTURE_UNITS_PER_STAGE). */
    public static final int UNIT_COUNT = 32;
    private final NativeRasterState boundaries;
    private final int[] texture2d = new int[UNIT_COUNT];
    private final boolean[] enabled2d = new boolean[UNIT_COUNT];
    private int activeUnit;
    /**
     * Bindings for targets other than {@code GL_TEXTURE_2D}, keyed {@code (activeUnit << 16) | target}
     * with -1 for unbound; the record lived on the GL entrance until E4-b1 seated every binding ID
     * here (Neon door ruling, family 3).
     */
    private final it.unimi.dsi.fastutil.longs.Long2IntMap otherBindings =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    /** The last texture name a manager-route bind named; the GL-less fallback the upload, parameter and copy paths read. */
    private volatile int lastBoundTexture = -1;

    public NativeTextureBindings(NativeRasterState boundaries) {
        this.boundaries = boundaries;
        java.util.Arrays.fill(this.enabled2d, true);
        this.otherBindings.defaultReturnValue(-1);
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
    private long bindKey(int target) { return ((long) this.activeUnit << 16) | (target & 0xFFFFL); }

    /** {@return the name bound to a texture target on the active unit}, or -1 when unbound. */
    public int boundTexture(int target) {
        return target == 0x0DE1 ? texture2d() : this.otherBindings.get(bindKey(target));
    }

    /**
     * Records a dispatched bind of any target. The last-bound record is deliberately not updated:
     * it belongs to the manager route ({@link #noteBoundTexture}), whose names the GL-less fallback
     * reads; a raw bind the fallback then preferred could file an upload under another target's name.
     */
    public void bindTexture(int target, int texture) {
        if (target == 0x0DE1) bindTexture2d(texture);
        else this.otherBindings.put(bindKey(target), texture);
    }

    /** Records a manager-route bind: the binding itself and the last-bound name the fallback reads. */
    public void noteBoundTexture(int target, int texture) {
        bindTexture(target, texture);
        this.lastBoundTexture = texture;
    }

    /** {@return the last texture name a manager-route bind named}, or -1 when none has. */
    public int lastBoundTexture() { return this.lastBoundTexture; }

    /** Drops every non-2D binding that names {@code texture}, as a texture deletion settles. */
    public void forgetBoundTexture(int texture) {
        if (this.otherBindings.isEmpty()) return;
        it.unimi.dsi.fastutil.longs.LongIterator bindings = this.otherBindings.keySet().iterator();
        while (bindings.hasNext()) {
            if (this.otherBindings.get(bindings.nextLong()) == texture) bindings.remove();
        }
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
