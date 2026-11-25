package org.soutalab.celtium.vulkan.hal;

/**
 * The clear the game issued while <em>its own main framebuffer</em> was bound, latched at the binding
 * change that ends it, waiting to be applied to the delivered frame image.
 * <p>
 * The game clears three different framebuffers and only one of them is the delivered image: its own
 * main framebuffer is the delivered frame, the entity-outline framebuffer is a chain pair and the
 * default framebuffer is the default pair. The stub records a clear's buffer mask and the colour it
 * would use, but neither says <em>which</em> framebuffer was bound when the game asked for it, and the
 * request is consumed at the next frame's acquire - by which time the previous frame's last
 * {@code glClearColor} is the outline framebuffer's transparent black. Applying that request to the
 * delivered image cleared the whole image black once per glow frame, and the sky kept that black
 * wherever nothing drew over it (measured: {@code presentedBlackFraction=0.18984}, run
 * {@code glow688}). The latch is taken where both facts are still true, and is the only clear the
 * delivered image is cleared with while one is held.
 * <p>
 * The latch is pure state: it records, reports and hands back the latched clear, and it is saved and
 * restored as a unit by the frame target's recording snapshot (a recording discarded before
 * submission must not lose the clear it consumed). Applying the clear - and the GLSM frame-state
 * route's parallel pending record, which is preferred when present - stays with the frame target.
 * <p>
 * This class is the clear-latch functional group moved out of the GL-less frame target (HAL wave 3):
 * the frame target keeps only the delegating {@code noteGameClear} / {@code gameClearLatched} glue.
 */
public final class GameClearLatch {

    private boolean latched;
    private int mask;
    private float red;
    private float green;
    private float blue;
    private float alpha;
    private double depth = 1.0D;

    /**
     * Latches a clear the game asked for while the framebuffer it names was still bound, so it can be
     * applied to the image that framebuffer stands for rather than to whatever is bound when the
     * request is finally read.
     *
     * @param mask  the {@code GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT} bits the game asked for
     * @param red   the clear colour in effect when the clear was issued
     * @param green the clear colour's green channel
     * @param blue  the clear colour's blue channel
     * @param alpha the clear colour's alpha channel
     * @param depth the depth value in effect when the clear was issued
     */
    public void note(int mask, float red, float green, float blue, float alpha, double depth) {
        this.latched = true;
        this.mask = mask;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.depth = depth;
    }

    /** {@return whether a clear addressed to the delivered image is waiting to be recorded}, consuming nothing. */
    public boolean latched() {
        return this.latched;
    }

    /** {@return the latched clear's buffer mask}. */
    public int mask() {
        return this.mask;
    }

    /** {@return the latched clear colour's red channel}. */
    public float red() {
        return this.red;
    }

    /** {@return the latched clear colour's green channel}. */
    public float green() {
        return this.green;
    }

    /** {@return the latched clear colour's blue channel}. */
    public float blue() {
        return this.blue;
    }

    /** {@return the latched clear colour's alpha channel}. */
    public float alpha() {
        return this.alpha;
    }

    /** {@return the latched clear's depth value}. */
    public double depth() {
        return this.depth;
    }

    /** Marks the latched clear consumed; its values stay readable until the next {@link #note}. */
    public void consume() {
        this.latched = false;
    }

    /** Restores a state previously read through the accessors (the recording snapshot's rollback). */
    public void restore(boolean latched, int mask, float red, float green, float blue, float alpha,
                        double depth) {
        this.latched = latched;
        this.mask = mask;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.depth = depth;
    }
}
