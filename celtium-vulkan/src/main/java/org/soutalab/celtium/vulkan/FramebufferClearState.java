package org.soutalab.celtium.vulkan;

/**
 * GLSM stage 3, GAP-ST-4 (census S-8, flush trigger F-9): the owner of clear state in its
 * three forms - the current values, the pending latch (a clear that was issued and nobody
 * consumed yet, with the framebuffer it was issued against and whether routing called that
 * framebuffer the main one), and the issue-time snapshot of the last main clear.
 * <p>
 * Field defaults copy the stub's: colour (0,0,0,1), depth 1.0, pendingIsMain true.
 * {@link #consumePending()} is the F-9 trigger point: the backend's consumption of a pending
 * clear reads and clears the latch here instead of the stub's pendingClears counter.
 */
public final class FramebufferClearState {
    private static final FramebufferClearState CURRENT = new FramebufferClearState();

    public static FramebufferClearState current() { return CURRENT; }

    private float red, green, blue = 0.0f, alpha = 1.0f;
    private double depth = 1.0;
    private int pendingMask;
    private int pendingDrawFbo;
    private boolean pendingIsMain = true;
    private int mainClears, foreignClears;
    private float lastMainRed, lastMainGreen, lastMainBlue, lastMainAlpha;
    private float lastForeignRed, lastForeignGreen, lastForeignBlue;
    private double lastMainDepth = 1.0;
    private boolean mainValuesKnown;
    private long version;

    private FramebufferClearState() {
    }

    public void clearColor(float red, float green, float blue, float alpha) {
        this.red = red; this.green = green; this.blue = blue; this.alpha = alpha;
        this.version++;
    }

    public void clearDepth(double depth) { this.depth = depth; this.version++; }

    public float red() { return this.red; }
    public float green() { return this.green; }
    public float blue() { return this.blue; }
    public float alpha() { return this.alpha; }
    public double depth() { return this.depth; }

    /**
     * Latches a clear: ORs the mask into the pending set and snapshots the issue context.
     * The snapshot follows the stub record's per-buffer rule: colour values are snapshotted
     * only when the mask names colour (0x4000), depth only when it names depth (0x0100), so a
     * later depth-only clear cannot replace the colour an earlier colour clear was issued with.
     */
    public void issue(int mask, int drawFbo, boolean isMain) {
        this.pendingMask |= mask;
        this.pendingDrawFbo = drawFbo;
        this.pendingIsMain = isMain;
        if (isMain) {
            this.mainClears++;
            if ((mask & 0x4000) != 0) {
                this.lastMainRed = this.red; this.lastMainGreen = this.green;
                this.lastMainBlue = this.blue; this.lastMainAlpha = this.alpha;
                this.mainValuesKnown = true;
            }
            if ((mask & 0x0100) != 0) {
                this.lastMainDepth = this.depth;
            }
        } else {
            this.foreignClears++;
            this.lastForeignRed = this.red; this.lastForeignGreen = this.green;
            this.lastForeignBlue = this.blue;
        }
        this.version++;
    }

    public float lastMainRed() { return this.lastMainRed; }
    public float lastMainGreen() { return this.lastMainGreen; }
    public float lastMainBlue() { return this.lastMainBlue; }
    public float lastForeignRed() { return this.lastForeignRed; }
    public float lastForeignGreen() { return this.lastForeignGreen; }
    public float lastForeignBlue() { return this.lastForeignBlue; }

    public boolean hasPending() { return this.pendingMask != 0; }
    public int pendingMask() { return this.pendingMask; }
    public int pendingDrawFbo() { return this.hasPending() ? this.pendingDrawFbo : -1; }
    public boolean pendingIsMain() { return this.pendingIsMain; }

    /** {@return whether the pending clear belongs to the main render target}, false when no clear is pending. */
    public boolean pendingBelongsToMain() { return this.hasPending() && this.pendingIsMain; }

    /** F-9 consumption: returns the latched mask and clears the latch. */
    public int consumePending() {
        int mask = this.pendingMask;
        this.pendingMask = 0;
        if (mask != 0) this.version++;
        return mask;
    }

    public int mainClears() { return this.mainClears; }
    public int foreignClears() { return this.foreignClears; }
    public boolean mainValuesKnown() { return this.mainValuesKnown; }

    /** Fills {@code out[0..3]} with the last main clear colour when known. */
    public boolean mainValues(float[] out) {
        if (!this.mainValuesKnown) return false;
        out[0] = this.lastMainRed; out[1] = this.lastMainGreen;
        out[2] = this.lastMainBlue; out[3] = this.lastMainAlpha;
        return true;
    }

    public double lastMainDepth() { return this.lastMainDepth; }
    public long version() { return this.version; }

    /** The colour the game last asked {@code glClear} to use, into {@code out} as {r,g,b,a}. */
    public void clearColor(float[] out) {
        out[0] = this.red;
        out[1] = this.green;
        out[2] = this.blue;
        out[3] = this.alpha;
    }

    /**
     * The colour and depth of the last {@code glClear} of the main framebuffer, as set when that
     * clear was issued - into {@code out} as {r,g,b,a}; {@return the depth}, or NaN when no main
     * clear has been seen (use the live values).
     * <p>
     * The main clear is carried to the frame later (the latch at the next bind, or the acquire),
     * and reading the live {@code glClearColor} then gave whatever was set in between: a
     * {@code Framebuffer.framebufferClear} sets its own colour (black) before clearing another
     * target, so the world frame was cleared black instead of with the fog colour vanilla's clear
     * used - the E2E pack's black area below the horizon beyond the loaded chunks.
     */
    public double mainClearValues(float[] out) {
        if (!this.mainValuesKnown) {
            this.clearColor(out);
            return Double.NaN;
        }
        this.mainValues(out);
        return this.lastMainDepth;
    }

    /**
     * The mask a {@code glClear} actually erases, after the two write-mask rules GL applies to it.
     * <p>
     * GL's clear obeys the colour write mask: a pass that clears depth with every colour channel
     * masked off - the fancy-cloud depth-only pass does exactly that through
     * {@code GlStateManager.colorMask} - must not erase the image's colour. The latch, the pending
     * mask and every replay read this value, so the masking belongs at this one choke point. A
     * partial mask cannot be expressed by an attachment clear and still clears the colour.
     * <p>
     * The depth half is the same rule: {@code glClear} honours {@code glDepthMask}, and the game's
     * depth writes are mirrored on the backend (a {@code GlStateManager.depthMask} hook), not here,
     * so the answer comes from there at the moment the clear is issued. A pass that disabled depth
     * writes and then cleared must not have the frame image's depth erased.
     * <p>
     * Two callers have to agree on it: the capture entrance applies it when a clear is recorded,
     * and the backend's {@code flushBeforeClear} asks it before it decides whether the queue can
     * carry the clear - a reservation made with the raw mask would not match the mask the clear is
     * recorded with, and the clear would then be applied twice.
     */
    public static int effectiveClearMask(int mask) {
        if (NativeRasterState.current().colorMask() == 0) {
            mask &= ~0x4000;
        }
        if (!depthWritesEnabled()) {
            mask &= ~0x0100;
        }
        return mask;
    }

    /** {@return the game's current depth-write state}; assumed true when no backend is installed. */
    private static boolean depthWritesEnabled() {
        try {
            Object backend = CeltiumBackends.current();
            return !(backend instanceof VulkanBackendAdapter vulkan)
                    || vulkan.celtium$depthWritesLive();
        } catch (RuntimeException | LinkageError error) {
            return true;
        }
    }
}
