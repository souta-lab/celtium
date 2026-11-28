package org.soutalab.celtium.vulkan.gl;

import org.soutalab.celtium.vulkan.lane2.Lane2StateMirror;

/**
 * GLSM stage 3, GAP-ST-11 / M-3: the matrix mode's single owner for both routes - now a
 * forwarding record only.
 * <p>
 * Since the E5 seat A move (Neon E4 ruling 2026-11-28) the mode itself is owned by the Lane 2
 * mirror household: {@code Lane2ImmediateState} holds it in GL enum values and
 * {@link Lane2StateMirror}'s mode accessors delegate to that field. This class keeps no value
 * of its own; {@link #get()} and {@link #set(int)} forward to the mirror, so the stub route's
 * writes (SEM_MATRIX_MODE) and reads (the mixin's redundant-call skip,
 * {@code celtium$matrixModeIsCurrent}) keep landing on the single owner until this record is
 * deleted with the rest of the capture package at E4-c. A value outside the three matrix modes
 * falls through to MODELVIEW on write, matching the fall-through the state's ordinal getter
 * always used.
 * <p>
 * GL starts in GL_MODELVIEW (0x1700); the texture mode is 0x1702.
 */
public final class GlsmMatrixMode {
    public static final int MODELVIEW = 0x1700;
    public static final int PROJECTION = 0x1701;
    public static final int TEXTURE = 0x1702;

    private static final GlsmMatrixMode CURRENT = new GlsmMatrixMode();

    public static GlsmMatrixMode current() { return CURRENT; }

    private GlsmMatrixMode() {
    }

    public void set(int mode) {
        if (mode == MODELVIEW || mode == PROJECTION || mode == TEXTURE) {
            Lane2StateMirror.current().setMatrixMode(mode);
        } else {
            Lane2StateMirror.current().setMatrixMode(MODELVIEW);
        }
    }

    public int get() { return Lane2StateMirror.current().matrixMode(); }

    /** The mixin's redundant-call skip, keyed on the same record the write updates. */
    public boolean isCurrent(int mode) { return get() == mode; }
}
