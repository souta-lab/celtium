package org.soutalab.celtium.vulkan.gl;

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
public final class GlsmClearState {
    private static final GlsmClearState CURRENT = new GlsmClearState();

    public static GlsmClearState current() { return CURRENT; }

    private float red, green, blue = 0.0f, alpha = 1.0f;
    private double depth = 1.0;
    private int pendingMask;
    private int pendingDrawFbo;
    private boolean pendingIsMain = true;
    private int mainClears, foreignClears;
    private float lastMainRed, lastMainGreen, lastMainBlue, lastMainAlpha;
    private double lastMainDepth = 1.0;
    private boolean mainValuesKnown;
    private long version;

    private GlsmClearState() {
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

    /** Latches a clear: ORs the mask into the pending set and snapshots the issue context. */
    public void issue(int mask, int drawFbo, boolean isMain) {
        this.pendingMask |= mask;
        this.pendingDrawFbo = drawFbo;
        this.pendingIsMain = isMain;
        if (isMain) {
            this.mainClears++;
            this.lastMainRed = this.red; this.lastMainGreen = this.green;
            this.lastMainBlue = this.blue; this.lastMainAlpha = this.alpha;
            this.lastMainDepth = this.depth;
            this.mainValuesKnown = true;
        } else {
            this.foreignClears++;
        }
        this.version++;
    }

    public boolean hasPending() { return this.pendingMask != 0; }
    public int pendingMask() { return this.pendingMask; }
    public int pendingDrawFbo() { return this.hasPending() ? this.pendingDrawFbo : -1; }
    public boolean pendingIsMain() { return this.pendingIsMain; }

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
}
