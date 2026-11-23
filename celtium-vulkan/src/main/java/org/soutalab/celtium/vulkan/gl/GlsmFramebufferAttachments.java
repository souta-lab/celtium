package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-ST-3 (census S-20; stage-2 design S2-4 deferred it to stage 3 "with the
 * name ledger"): the owner of the framebuffer attachment maps.
 * <p>
 * Same five records the stub keeps, with the same mirroring rules: per-framebuffer colour
 * texture (slot 0) and its reverse texture -&gt; framebuffer map, the per-slot colour array
 * (MRT ledger, GL_MAX_COLOR_ATTACHMENTS entries), and the depth texture pair. Keeping the
 * forward and reverse maps in one owner is the point: the stub's pair could disagree when a
 * texture was re-attached to a second framebuffer, because two call sites updated them;
 * here {@link #attachColor} is the only writer of both.
 */
public final class GlsmFramebufferAttachments {
    /** Colour attachment slots per framebuffer record (GL_MAX_COLOR_ATTACHMENTS). */
    public static final int COLOR_SLOTS = 8;

    private static final GlsmFramebufferAttachments CURRENT = new GlsmFramebufferAttachments();

    public static GlsmFramebufferAttachments current() { return CURRENT; }

    private final Map<Integer, Integer> colorTextureByFbo = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> fboByColorTexture = new HashMap<Integer, Integer>();
    private final Map<Integer, int[]> colorSlotsByFbo = new HashMap<Integer, int[]>();
    private final Map<Integer, Integer> depthTextureByFbo = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> fboByDepthTexture = new HashMap<Integer, Integer>();
    private long version;

    private GlsmFramebufferAttachments() {
    }

    public void attachColor(int framebuffer, int slot, int texture) {
        Integer previous = this.colorTextureByFbo.get(framebuffer);
        if (slot == 0) {
            if (previous != null && previous.intValue() != texture) {
                this.fboByColorTexture.remove(previous);
            }
            if (texture != 0) {
                this.colorTextureByFbo.put(framebuffer, texture);
                this.fboByColorTexture.put(texture, framebuffer);
            } else {
                this.colorTextureByFbo.remove(framebuffer);
            }
        }
        int[] slots = this.colorSlotsByFbo.get(framebuffer);
        if (slots == null) {
            slots = new int[COLOR_SLOTS];
            this.colorSlotsByFbo.put(framebuffer, slots);
        }
        if (slot >= 0 && slot < COLOR_SLOTS) slots[slot] = texture;
        this.version++;
    }

    public void attachDepth(int framebuffer, int texture) {
        Integer previous = this.depthTextureByFbo.get(framebuffer);
        if (previous != null && previous.intValue() != texture) {
            this.fboByDepthTexture.remove(previous);
        }
        if (texture != 0) {
            this.depthTextureByFbo.put(framebuffer, texture);
            this.fboByDepthTexture.put(texture, framebuffer);
        } else {
            this.depthTextureByFbo.remove(framebuffer);
        }
        this.version++;
    }

    /** A deleted texture stops being any framebuffer's attachment, in both directions. */
    public void textureDeleted(int texture) {
        Integer colorFbo = this.fboByColorTexture.remove(texture);
        if (colorFbo != null) {
            this.colorTextureByFbo.remove(colorFbo);
            int[] slots = this.colorSlotsByFbo.get(colorFbo);
            if (slots != null) {
                for (int i = 0; i < slots.length; i++) if (slots[i] == texture) slots[i] = 0;
            }
        }
        Integer depthFbo = this.fboByDepthTexture.remove(texture);
        if (depthFbo != null) this.depthTextureByFbo.remove(depthFbo);
        this.version++;
    }

    public void framebufferDeleted(int framebuffer) {
        Integer color = this.colorTextureByFbo.remove(framebuffer);
        if (color != null) this.fboByColorTexture.remove(color);
        Integer depth = this.depthTextureByFbo.remove(framebuffer);
        if (depth != null) this.fboByDepthTexture.remove(depth);
        this.colorSlotsByFbo.remove(framebuffer);
        this.version++;
    }

    public int colorTexture(int framebuffer) {
        Integer texture = this.colorTextureByFbo.get(framebuffer);
        return texture == null ? 0 : texture.intValue();
    }

    public int colorSlot(int framebuffer, int slot) {
        int[] slots = this.colorSlotsByFbo.get(framebuffer);
        return slots == null || slot < 0 || slot >= COLOR_SLOTS ? 0 : slots[slot];
    }

    public int depthTexture(int framebuffer) {
        Integer texture = this.depthTextureByFbo.get(framebuffer);
        return texture == null ? 0 : texture.intValue();
    }

    /** @return the framebuffer a texture is attached to (colour first), or 0. */
    public int framebufferOfTexture(int texture) {
        Integer fbo = this.fboByColorTexture.get(texture);
        if (fbo == null) fbo = this.fboByDepthTexture.get(texture);
        return fbo == null ? 0 : fbo.intValue();
    }

    public long version() { return this.version; }
}
