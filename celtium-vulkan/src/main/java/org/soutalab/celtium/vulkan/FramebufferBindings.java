package org.soutalab.celtium.vulkan;

/**
 * The HAL seat for the game's framebuffer bindings (S3-b E4-b1, Neon door ruling family 7):
 * which raw framebuffer names are bound for draw and for read, whether the routing owner
 * calls the draw binding the game's main framebuffer, and the serial every bind bumps.
 * <p>
 * The record lived on the capture frontend's instance; the renderer, the pack-stage guard
 * and the copy paths read it from here now, so the binding truth has one home beside
 * {@link RenderTargetRegistry} instead of a second one inside the capture entrance. The
 * frontend keeps the bind's side effects that belong to the entrance (the flush-boundary
 * census note and the raw-framebuffer registration with the backend); this seat owns only
 * the values. A new capture entrance is a new GL context, so the frontend's constructor
 * {@link #reset() resets} the seat the way it clears the texture capture store.
 */
public final class FramebufferBindings {
    private static final FramebufferBindings CURRENT = new FramebufferBindings();

    public static FramebufferBindings current() { return CURRENT; }

    private volatile int drawFbo, readFbo;
    private volatile boolean drawIsMain = true;
    private long bindSerial;

    private FramebufferBindings() {
    }

    /** Back to a fresh context's values: nothing bound, the draw binding counts as main, no binds seen. */
    public void reset() {
        this.drawFbo = 0;
        this.readFbo = 0;
        this.drawIsMain = true;
        this.bindSerial = 0L;
    }

    /** {@return the draw framebuffer the game last bound}, or 0 when none was recorded. */
    public int drawFramebuffer() {
        return this.drawFbo;
    }

    /** {@return the read framebuffer the game last bound}, or 0 when none was recorded. */
    public int readFramebuffer() {
        return this.readFbo;
    }

    /** {@return whether the current draw framebuffer is the game's main one}. */
    public boolean drawIsMain() {
        return this.drawIsMain;
    }

    /** Records whether the routing owner calls the current draw framebuffer the main one. */
    public void noteDrawIsMain(boolean isMain) {
        this.drawIsMain = isMain;
    }

    /**
     * {@return how many framebuffer binds this seat has recorded}.
     * <p>
     * A caller that has to know the bound draw framebuffer can cache its answer against this
     * number instead of asking GL per draw: the answer cannot change without a bind, and the
     * bind is what bumps the serial.
     */
    public long bindSerial() {
        return this.bindSerial;
    }

    /**
     * Records a bind: GL_FRAMEBUFFER (0x8D40) binds both slots, GL_DRAW_FRAMEBUFFER (0x8CA9)
     * and GL_READ_FRAMEBUFFER (0x8CA8) bind one each. Every bind bumps the serial.
     */
    public void bind(int target, int name) {
        if (target == 0x8D40 || target == 0x8CA9) {
            this.drawFbo = name;
        }
        if (target == 0x8D40 || target == 0x8CA8) {
            this.readFbo = name;
        }
        this.bindSerial++;
    }

    /**
     * Forgets a deleted framebuffer name from the bindings: a draw binding that named it
     * returns to 0 and bumps the serial (the bound draw framebuffer changed), a read binding
     * that named it returns to 0.
     */
    public void forgetName(int name) {
        if (this.drawFbo == name) {
            this.drawFbo = 0;
            this.bindSerial++;
        }
        if (this.readFbo == name) {
            this.readFbo = 0;
        }
    }
}
