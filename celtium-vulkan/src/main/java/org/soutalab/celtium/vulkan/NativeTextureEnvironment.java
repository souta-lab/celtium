package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

/** Render-thread texture environment owned by Vulkan; the raw GL adapter only forwards setters and queries. */
public final class NativeTextureEnvironment {
    public static final int TARGET = 0x2300, MODE = 0x2200, COLOR = 0x2201;
    public static final int RGB = 0x8571, ALPHA = 0x8572, COMBINE = 0x8570;
    public static final int MODULATE = 0x2100, INTERPOLATE = 0x8575, REPLACE = 0x1E01;
    public static final int SOURCE0_RGB = 0x8580, OPERAND0_RGB = 0x8590;
    public static final int CONSTANT = 0x8576, SRC_COLOR = 0x0300;
    private static final NativeTextureEnvironment CURRENT = new NativeTextureEnvironment(
            NativeTextureBindings.current(), NativeRasterState.current());
    private final NativeTextureBindings bindings;
    private final NativeRasterState boundaries;
    private final Map<Integer, Integer>[] parameters;
    private final float[][] colors = new float[32][4];
    private boolean snapshotValid;
    private float[] snapshot;

    @SuppressWarnings("unchecked")
    public NativeTextureEnvironment(NativeTextureBindings bindings, NativeRasterState boundaries) {
        this.bindings = bindings;
        this.boundaries = boundaries;
        this.parameters = new Map[32];
        for (int i = 0; i < this.parameters.length; i++) this.parameters[i] = new HashMap<>();
    }
    public static NativeTextureEnvironment current() { return CURRENT; }

    public int unitParameter(int unit, int pname) {
        Integer value = this.parameters[unit].get(pname);
        if (value != null) return value;
        if (pname == MODE || pname == RGB || pname == ALPHA) return MODULATE;
        if (pname == SOURCE0_RGB || pname == 0x8588) return 0x1702; // TEXTURE
        if (pname == OPERAND0_RGB) return SRC_COLOR;
        return 0;
    }
    public int activeParameter(int target, int pname) {
        return target == TARGET ? unitParameter(this.bindings.activeUnit(), pname) : 0;
    }
    public void parameter(int target, int pname, int value) {
        if (target != TARGET) return;
        int unit = this.bindings.activeUnit();
        if (unitParameter(unit, pname) == value) return;
        this.boundaries.flushBeforeChange();
        this.parameters[unit].put(pname, value);
        this.snapshotValid = false;
    }
    public void color(int target, int pname, float r, float g, float b, float a) {
        if (target != TARGET || pname != COLOR) return;
        float[] old = this.colors[this.bindings.activeUnit()];
        if (old[0] == r && old[1] == g && old[2] == b && old[3] == a) return;
        this.boundaries.flushBeforeChange();
        old[0] = r; old[1] = g; old[2] = b; old[3] = a;
        this.snapshotValid = false;
    }
    public float colorComponent(int component) { return this.colors[this.bindings.activeUnit()][component]; }

    /** Immutable until the next state mutation; queued draws may retain the previous snapshot through completion. */
    public float[] combinedColor() {
        if (this.snapshotValid) return this.snapshot;
        this.snapshot = null;
        for (int unit = 0; unit < 4; unit++) {
            if (unitParameter(unit, MODE) != COMBINE) continue;
            int rgb = unitParameter(unit, RGB);
            boolean replace = rgb == REPLACE && unitParameter(unit, SOURCE0_RGB) == CONSTANT
                    && unitParameter(unit, OPERAND0_RGB) == SRC_COLOR;
            if (rgb == INTERPOLATE || replace) {
                float[] c = this.colors[unit];
                this.snapshot = new float[] {c[0], c[1], c[2], replace ? 1.0F : c[3]};
                break;
            }
        }
        this.snapshotValid = true;
        return this.snapshot;
    }
}
