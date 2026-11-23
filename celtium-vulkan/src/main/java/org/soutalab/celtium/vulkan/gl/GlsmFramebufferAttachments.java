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
            // The stub record's guard: a texture shared with another framebuffer keeps its
            // reverse entry when this framebuffer re-attaches (several FBOs share one depth
            // texture in the measured packs; colour shares the same rule here).
            if (previous != null && previous.intValue() != texture
                    && Integer.valueOf(framebuffer).equals(this.fboByColorTexture.get(previous))) {
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
        if (previous != null && previous.intValue() != texture
                && Integer.valueOf(framebuffer).equals(this.fboByDepthTexture.get(previous))) {
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

    // ---- rewiring additions (stage-3 routes): the stub readers' self-healing forms ----

    public int colorTextureCount() { return this.colorTextureByFbo.size(); }
    public int depthTextureCount() { return this.depthTextureByFbo.size(); }

    /** Every framebuffer whose slot-0 colour texture is {@code texture} (the delete notify sweep). */
    public java.util.List<Integer> framebuffersWithColorTexture(int texture) {
        java.util.List<Integer> out = new java.util.ArrayList<Integer>();
        for (Map.Entry<Integer, Integer> entry : this.colorTextureByFbo.entrySet()) {
            if (entry.getValue().intValue() == texture) out.add(entry.getKey());
        }
        return out;
    }

    /** @return whether {@code framebuffer}'s recorded colour attachments name {@code texture} at any slot. */
    public boolean attachesColor(int framebuffer, int texture) {
        if (this.colorTexture(framebuffer) == texture) return true;
        int[] slots = this.colorSlotsByFbo.get(framebuffer);
        if (slots != null) {
            for (int slot = 1; slot < slots.length; slot++) if (slots[slot] == texture) return true;
        }
        return false;
    }

    /**
     * The framebuffer a colour texture belongs to, with the stub record's self-healing:
     * a reverse entry that no longer agrees with the forward record is dropped and the
     * forward maps (slot 0, then the other slots) are scanned and the reverse repaired.
     */
    public int framebufferForColorTexture(int texture) {
        Integer cached = this.fboByColorTexture.get(texture);
        if (cached != null && this.attachesColor(cached.intValue(), texture)) return cached.intValue();
        this.fboByColorTexture.remove(texture);
        for (Map.Entry<Integer, Integer> entry : this.colorTextureByFbo.entrySet()) {
            if (entry.getValue().intValue() == texture) {
                this.fboByColorTexture.put(texture, entry.getKey());
                return entry.getKey().intValue();
            }
        }
        for (Map.Entry<Integer, int[]> entry : this.colorSlotsByFbo.entrySet()) {
            int[] slots = entry.getValue();
            for (int slot = 1; slot < slots.length; slot++) {
                if (slots[slot] == texture) {
                    this.fboByColorTexture.put(texture, entry.getKey());
                    return entry.getKey().intValue();
                }
            }
        }
        return 0;
    }

    /** The depth twin of {@link #framebufferForColorTexture(int)}. */
    public int framebufferForDepthTexture(int texture) {
        Integer cached = this.fboByDepthTexture.get(texture);
        if (cached != null && this.depthTexture(cached.intValue()) == texture) return cached.intValue();
        this.fboByDepthTexture.remove(texture);
        for (Map.Entry<Integer, Integer> entry : this.depthTextureByFbo.entrySet()) {
            if (entry.getValue().intValue() == texture) {
                this.fboByDepthTexture.put(texture, entry.getKey());
                return entry.getKey().intValue();
            }
        }
        return 0;
    }

    /**
     * Fills {@code out} (COLOR_SLOTS long) with a framebuffer's colour attachments by slot;
     * with no slot record, slot 0 falls back to the single-texture record, as the stub's did.
     */
    public void colorAttachments(int framebuffer, int[] out) {
        int[] slots = this.colorSlotsByFbo.get(framebuffer);
        if (slots != null) {
            System.arraycopy(slots, 0, out, 0, COLOR_SLOTS);
        } else {
            out[0] = this.colorTexture(framebuffer);
        }
    }

    public long version() { return this.version; }
}
