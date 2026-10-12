package org.soutalab.celtium.vulkan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the publication bookkeeping the backend used to keep in its own fields: which GL texture name a framebuffer's
 * colour attachment is, which image the sampler may read for it, and the counters that say what happened.
 * <p>
 * This is the state half of refactoring plan R1b (docs/refactoring-plan.md section 6). The <em>decision</em> of which
 * image a name stands for stays in the backend until R5 moves the target registry, because it reads the frame target
 * and the off-target pairs; what moves here is the ownership of the census itself - one map from framebuffer identity
 * to its colour texture, one map from GL name to the published texture, and the counters - so that the two are no
 * longer fields of a fourteen-thousand-line class. Section 5.4 of the plan asks for exactly this split: identity,
 * image, generation and the read/write alias are one owner, and nothing else may invent a second census.
 * <p>
 * Thread safety: the maps are concurrent because the backend reports framebuffer binds from the render thread and the
 * stub's announcements can arrive from the thread that created the texture; the counters are only written from the
 * render thread, and are plain fields for that reason.
 */
final class PublishedFrameTextures {

    /** Framebuffer identity to {colour texture name, width, height}, as reported where the framebuffer was bound. */
    private final Map<Long, long[]> colorTextures = new ConcurrentHashMap<>();

    /** GL texture name to the texture published for it, which is what the sampling side resolves. */
    private final Map<Integer, VulkanGlTexture> published = new ConcurrentHashMap<>();

    /** How many framebuffer binds carried a colour texture, published or not. */
    private int colorTextureReports;

    /** How many colour textures were published to an image. */
    private int colorTexturesPublished;

    /** Publication outcomes, so a missing image can be told from a never-published name. */
    private int created;
    private int unpublished;
    private int skippedForAttach;
    private int stalePruned;
    private int republished;

    /** Records a framebuffer's colour attachment. A zero name or an empty extent clears the entry. */
    void bindColorTexture(long framebufferIdentity, int colorTexture, int width, int height) {
        if (colorTexture == 0 || width <= 0 || height <= 0) {
            this.colorTextures.remove(Long.valueOf(framebufferIdentity));
            return;
        }
        this.colorTextures.put(Long.valueOf(framebufferIdentity), new long[] { colorTexture, width, height });
        this.colorTextureReports++;
    }

    /** {@return the colour texture recorded for a framebuffer identity as {texture, width, height}, or null}. */
    long[] colorTexture(long identity) {
        return this.colorTextures.get(Long.valueOf(identity));
    }

    /** {@return the framebuffer identity whose colour attachment carries this GL texture name}, or {@code MIN_VALUE}. */
    long identityForColorTexture(int glTextureName) {
        if (glTextureName == 0) {
            return Long.MIN_VALUE;
        }
        for (Map.Entry<Long, long[]> entry : this.colorTextures.entrySet()) {
            long[] reported = entry.getValue();
            if (reported != null && reported[0] == glTextureName) {
                return entry.getKey().longValue();
            }
        }
        return Long.MIN_VALUE;
    }

    /** {@return the colour texture name recorded for an identity, or 0 when there is none}. */
    int colorTextureName(long identity) {
        long[] reported = colorTexture(identity);
        return reported == null ? 0 : (int) reported[0];
    }

    /** Records a published texture for a GL name. */
    void putPublished(int glTextureName, VulkanGlTexture texture) {
        this.published.put(Integer.valueOf(glTextureName), texture);
    }

    /** {@return the published texture for a GL name, or null}. */
    VulkanGlTexture published(int glTextureName) {
        return this.published.get(Integer.valueOf(glTextureName));
    }

    /** Forgets a published name. */
    void removePublished(int glTextureName) {
        this.published.remove(Integer.valueOf(glTextureName));
    }

    /** {@return how many names are published right now}. */
    int publishedCount() {
        return this.published.size();
    }

    /** {@return how many framebuffer binds carried a colour texture}. */
    int colorTextureReports() {
        return this.colorTextureReports;
    }

    void noteColorTexturePublished() {
        this.colorTexturesPublished++;
    }

    /** {@return how many colour textures were published to an image}. */
    int colorTexturesPublished() {
        return this.colorTexturesPublished;
    }

    void noteCreated() {
        this.created++;
    }

    void noteUnpublished() {
        this.unpublished++;
    }

    void noteSkippedForAttach() {
        this.skippedForAttach++;
    }

    void noteStalePruned() {
        this.stalePruned++;
    }

    void noteRepublished() {
        this.republished++;
    }

    /** {@return the publication counters in the order the verification line prints them}. */
    String summary() {
        return "publishedFrameTexturesCreated=" + this.created
                + " publishedFrameTexturesUnpublished=" + this.unpublished
                + " publishedFrameTexturesSkippedForAttach=" + this.skippedForAttach
                + " publishedFrameTexturesStalePruned=" + this.stalePruned
                + " publishedFrameTexturesRepublished=" + this.republished;
    }
}
