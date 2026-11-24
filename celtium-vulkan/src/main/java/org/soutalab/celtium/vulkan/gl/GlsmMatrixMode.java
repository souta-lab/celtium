package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-ST-11 / M-3: the matrix mode's single owner for both routes.
 * <p>
 * The stub route writes this record in GL enum values (SEM_MATRIX_MODE) and reads it for the
 * mixin's redundant-call skip ({@code celtium$matrixModeIsCurrent}). Since M-3, VulkanGlState
 * holds no per-instance mode field either: its getter/setter delegate here, converting between
 * this record's GL enum values and its own ordinals (0=MODELVIEW / 1=PROJECTION / 2=TEXTURE),
 * with unrecognised values on either side falling through to MODELVIEW. A direct
 * GL11.glMatrixMode call that reaches only one route therefore still moves the mode the other
 * route's stack machinery selects by — the disagreement the former double ownership allowed is
 * gone. VulkanGlState's constructor initialises this record to MODELVIEW, preserving the
 * "a fresh state starts in MODELVIEW" meaning the old per-instance field gave it.
 * <p>
 * GL starts in GL_MODELVIEW (0x1700); the texture mode is 0x1702.
 */
public final class GlsmMatrixMode {
    public static final int MODELVIEW = 0x1700;
    public static final int PROJECTION = 0x1701;
    public static final int TEXTURE = 0x1702;

    private static final GlsmMatrixMode CURRENT = new GlsmMatrixMode();

    public static GlsmMatrixMode current() { return CURRENT; }

    private volatile int mode = MODELVIEW;

    private GlsmMatrixMode() {
    }

    public void set(int mode) { this.mode = mode; }

    public int get() { return this.mode; }

    /** The mixin's redundant-call skip, keyed on the same record the write updates. */
    public boolean isCurrent(int mode) { return this.mode == mode; }
}
