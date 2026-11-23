package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-ST-5 (stage-2 design S2-3): the single owner of the viewport's three
 * representations - the raw rectangle, the packed form the group keys read, and the seen flag.
 * <p>
 * The packing is byte-identical to the stub's recordViewportPacked: x|y|w|h in four 16-bit
 * fields, and a negative or oversized rectangle is left out rather than truncated into a
 * different one. {@link #packed()} answers -1 until the first valid set, matching
 * {@code CeltiumGlStub.celtium$viewportPacked()}. The backend's currentViewportKey becomes a
 * dependent of {@link #version()} at merge time.
 */
public final class GlsmViewportState {
    private static final GlsmViewportState CURRENT = new GlsmViewportState();

    public static GlsmViewportState current() { return CURRENT; }

    private int x, y;
    private int width = 1280, height = 720;
    private long packed;
    private boolean seen;
    private long version;

    private GlsmViewportState() {
    }

    public void set(int x, int y, int width, int height) {
        if (this.x == x && this.y == y && this.width == width && this.height == height && this.seen) return;
        this.x = x; this.y = y; this.width = width; this.height = height;
        if (width >= 0 && height >= 0 && width <= 0xFFFF && height <= 0xFFFF) {
            this.packed = ((long) (x & 0xFFFF) << 48) | ((long) (y & 0xFFFF) << 32)
                    | ((long) (width & 0xFFFF) << 16) | (height & 0xFFFF);
            this.seen = true;
        }
        this.version++;
    }

    /** Window-creation initialisation: sets the raw rectangle only, without marking a game-set
     * viewport as seen (the stub's initializeWindowRectangles never recorded the packed form). */
    public void initialize(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.version++;
    }

    public int x() { return this.x; }
    public int y() { return this.y; }
    public int width() { return this.width; }
    public int height() { return this.height; }
    public boolean seen() { return this.seen; }

    /** @return the packed rectangle, or -1 when the game never set a valid one. */
    public long packed() { return this.seen ? this.packed : -1L; }

    public long version() { return this.version; }
}
