package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-ST-11 (transitional half): the matrix mode's single record for the
 * stub route.
 * <p>
 * The mode is owned twice today: VulkanGlState's per-backend instance field (the stack
 * machinery reads it) and CeltiumGlStub's own {@code matrixMode} (SEM_MATRIX_MODE writes
 * it, {@code celtium$matrixModeIsCurrent} reads it for the mixin's redundant-call skip).
 * They can disagree exactly when a direct GL11.glMatrixMode call reaches one route and
 * not the other. This record is where the stub route's write and the skip's read meet
 * from stage 3 on; folding VulkanGlState's instance field into it (or routing the stub
 * to the backend's instance) is the merge phase's remaining half, because the backend
 * instance is not reachable from the stub's static surface today.
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
