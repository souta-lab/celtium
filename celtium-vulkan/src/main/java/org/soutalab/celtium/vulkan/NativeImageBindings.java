package org.soutalab.celtium.vulkan;

/**
 * The GL image-unit table captured independently of dispatch. Iris's image update is ordered like its sampler update:
 * it writes an image-unit integer to the program and calls {@code glBindImageTexture} immediately before the pass, so
 * resolving a program uniform against a process-wide "current texture" would lose which unit Iris selected.
 * <p>
 * The eight slots are the same limit reported as {@code GL_MAX_IMAGE_UNITS}; the number therefore describes storage
 * that exists here (the full bind tuple) and in the native descriptor layout, rather than a driver-shaped promise
 * with no backing ledger. Bind and deletion changes flush the prior immediate boundary first, matching
 * {@link NativeTextureBindings}: a group accepted before the next GL state change must be prepared against the old
 * tuple. This class has no GL dispatch dependency so both the stub provider and the transformed hint path can share
 * one ledger.
 */
public final class NativeImageBindings {
    private static final NativeImageBindings CURRENT = new NativeImageBindings(NativeRasterState.current());
    public static final int IMAGE_UNITS_PER_PROGRAM = 8;
    private final int[] texture = new int[IMAGE_UNITS_PER_PROGRAM];
    private final int[] level = new int[IMAGE_UNITS_PER_PROGRAM];
    private final boolean[] layered = new boolean[IMAGE_UNITS_PER_PROGRAM];
    private final int[] layer = new int[IMAGE_UNITS_PER_PROGRAM];
    private final int[] access = new int[IMAGE_UNITS_PER_PROGRAM];
    private final int[] format = new int[IMAGE_UNITS_PER_PROGRAM];
    private final NativeRasterState boundaries;

    public NativeImageBindings() { this(null); }
    public NativeImageBindings(NativeRasterState boundaries) { this.boundaries = boundaries; }

    public static NativeImageBindings current() { return CURRENT; }
    public int texture(int unit) { return valid(unit) ? texture[unit] : 0; }
    public int level(int unit) { return valid(unit) ? level[unit] : 0; }
    public boolean layered(int unit) { return valid(unit) && layered[unit]; }
    public int layer(int unit) { return valid(unit) ? layer[unit] : 0; }
    public int access(int unit) { return valid(unit) ? access[unit] : 0; }
    public int format(int unit) { return valid(unit) ? format[unit] : 0; }

    /** Out-of-range units are ignored, matching the texture-unit ledger's treatment of invalid selectors. */
    public void bind(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (!valid(unit)) return;
        if (this.texture[unit] == texture && this.level[unit] == level && this.layered[unit] == layered
                && this.layer[unit] == layer && this.access[unit] == access && this.format[unit] == format) return;
        if (this.boundaries != null) this.boundaries.flushBeforeChange();
        this.texture[unit] = texture;
        this.level[unit] = level;
        this.layered[unit] = layered;
        this.layer[unit] = layer;
        this.access[unit] = access;
        this.format[unit] = format;
    }

    /** Deleting a GL name removes every future image-unit reference to it. */
    public void delete(int texture) {
        if (texture <= 0) return;
        boolean bound = false;
        for (int value : this.texture) bound |= value == texture;
        if (!bound) return;
        if (this.boundaries != null) this.boundaries.flushBeforeChange();
        for (int unit = 0; unit < this.texture.length; unit++) {
            if (this.texture[unit] == texture) bind(unit, 0, 0, false, 0, 0, 0);
        }
    }

    private static boolean valid(int unit) { return unit >= 0 && unit < IMAGE_UNITS_PER_PROGRAM; }
}
