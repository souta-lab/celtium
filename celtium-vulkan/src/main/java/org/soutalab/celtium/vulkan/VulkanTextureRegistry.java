package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;

/**
 * Mirrors the OpenGL textures Minecraft binds into Vulkan images, keyed by their GL name.
 * <p>
 * The immediate path needs whatever texture the drawing state has bound - the block atlas for terrain-adjacent
 * geometry, a particle sheet, a GUI texture, a mod's own texture - and the only thing the state can observe is the GL
 * name from {@code GlStateManager.bindTexture}. This registry turns that name into a Vulkan image on first use, using
 * the same level-0 readback the terrain atlas goes through, and reuses it afterwards.
 * <p>
 * Honest limitation, stated rather than hidden: an entry is uploaded once. A texture whose contents change afterwards
 * (an animated atlas, a dynamic framebuffer texture, a mod writing into its own texture) keeps the first mirror until
 * the registry is told to refresh it. {@link #invalidate(int)} exists for that, and the refresh policy belongs with
 * the texture-animation work in the plan's stage 4.
 * <p>
 * Threading: render thread only, which is where both the GL binds and the uploads happen.
 */
public final class VulkanTextureRegistry {
    /** One mirrored texture: the Vulkan image plus the GL name and size it was created from. */
    private static final class Entry {
        final int glId;
        final int width;
        final int height;
        final VulkanGlTexture texture;

        Entry(int glId, int width, int height, VulkanGlTexture texture) {
            this.glId = glId;
            this.width = width;
            this.height = height;
            this.texture = texture;
        }
    }

    private final VulkanContext context;
    private final Map<Integer, Entry> entries = new HashMap<Integer, Entry>();
    private int mirrorCount;
    private int failureCount;
    private String lastFailure = "none";

    public VulkanTextureRegistry(VulkanContext context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
    }

    /**
     * {@return the Vulkan mirror of the given GL texture, creating it if needed}, or {@code null} when the name is 0
     * or the mirror could not be created - the caller then refuses the batch instead of drawing with a wrong image.
     */
    public VulkanGlTexture get(int glTextureId) {
        if (glTextureId == 0) {
            return null;
        }
        Entry existing = this.entries.get(glTextureId);
        if (existing != null) {
            return existing.texture;
        }
        try {
            // LWJGL 3 returns the queried value directly rather than filling a buffer.
            int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                this.failureCount++;
                this.lastFailure = "GL texture " + glTextureId + " reports size " + width + "x" + height;
                return null;
            }
            VulkanGlTexture texture = VulkanGlTexture.create(this.context, width, height, false);
            texture.uploadFromGlTexture(glTextureId, GL_TEXTURE_2D);
            this.entries.put(glTextureId, new Entry(glTextureId, width, height, texture));
            this.mirrorCount++;
            return texture;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "GL texture " + glTextureId + ": " + error;
            return null;
        }
    }

    /** Drops one mirror so the next use re-uploads it; used when a texture's contents are known to have changed. */
    /**
     * Drops every mirror, so the next use of each re-reads its GL texture.
     * <p>
     * Needed because Minecraft updates some textures - the lightmap above all - through a path the upload hooks cannot
     * reach: {@code DynamicTexture.func_110564_a} uploads into the texture the object owns, and two attempts to hook that
     * path crashed the client during Mixin transformation. Refreshing from the renderer costs a re-upload per mirrored
     * texture per refresh and cannot be wrong about which texture changed.
     */
    public void invalidateAll() {
        this.entries.clear();
    }

    public void invalidate(int glTextureId) {
        Entry entry = this.entries.remove(glTextureId);
        if (entry != null) {
            entry.texture.delete();
        }
    }

    /** {@return how many textures have been mirrored so far}, for the verification line */
    public int mirrorCount() {
        return this.mirrorCount;
    }

    public int failureCount() {
        return this.failureCount;
    }

    public String lastFailure() {
        return this.lastFailure;
    }

    public int size() {
        return this.entries.size();
    }

    public void delete() {
        for (Entry entry : this.entries.values()) {
            entry.texture.delete();
        }
        this.entries.clear();
    }
}
