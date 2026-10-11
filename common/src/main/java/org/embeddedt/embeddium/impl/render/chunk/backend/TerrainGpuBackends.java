package org.embeddedt.embeddium.impl.render.chunk.backend;

/**
 * Holds the live terrain backend so the Mixins that replace Minecraft's GL drawing can reach it.
 * <p>
 * The backend installs itself when it is created and clears the holder when it is deleted. When the holder is empty
 * (no Vulkan backend, or it failed to initialise) the Mixins do nothing and vanilla keeps drawing through GL, which
 * is the fail-safe the compatibility layer relies on.
 */
public final class TerrainGpuBackends {
    private static volatile TerrainGpuBackend current;

    private TerrainGpuBackends() {
    }

    /** {@return the live backend, or {@code null} when there is none} */
    public static TerrainGpuBackend current() {
        return current;
    }

    /**
     * Texture uploads announced while no backend existed yet.
     * <p>
     * The menu's panorama PNGs load very early - before the terrain backend is created - and the upload hook's
     * {@code TerrainGpuBackends.current() != null} guard silently dropped their announcements, with nothing to replay
     * them later. Measured: {@code announceImage} never fired for the panorama's texture ids while
     * {@code forgetTexture} did, so the reused name kept the splash logo's white fill and the panorama sampled white.
     * A bounded pending list is what makes the early-load window safe: the announcement is kept here and handed to the
     * backend the moment it installs.
     */
    private static final java.util.List<Object[]> PENDING_TEXTURE_UPLOADS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<Object[]>());
    private static final int PENDING_UPLOAD_LIMIT = 64;
    private static long pendingTextureUploadsDropped;

    /** Keeps an announcement that arrived before a backend existed, up to a bound. */
    public static void notePendingTextureUpload(int glTextureId, Object image) {
        if (image == null) {
            return;
        }
        synchronized (PENDING_TEXTURE_UPLOADS) {
            if (PENDING_TEXTURE_UPLOADS.size() >= PENDING_UPLOAD_LIMIT) {
                pendingTextureUploadsDropped++;
                return;
            }
            PENDING_TEXTURE_UPLOADS.add(new Object[] { Integer.valueOf(glTextureId), image });
        }
    }

    /** {@return how many early announcements were kept, and how many were dropped by the bound}. */
    public static String pendingTextureUploadSummary() {
        synchronized (PENDING_TEXTURE_UPLOADS) {
            return "pendingTextureUploads=" + PENDING_TEXTURE_UPLOADS.size()
                    + " dropped=" + pendingTextureUploadsDropped;
        }
    }

    public static void install(TerrainGpuBackend backend) {
        current = backend;
        if (backend == null) {
            return;
        }
        // Replay what arrived before this backend existed, so a name that was filled early keeps its own content
        // rather than whatever the previous user of that GL name left behind.
        Object[][] pending;
        synchronized (PENDING_TEXTURE_UPLOADS) {
            pending = PENDING_TEXTURE_UPLOADS.toArray(new Object[0][]);
            PENDING_TEXTURE_UPLOADS.clear();
        }
        for (Object[] entry : pending) {
            try {
                backend.announceTextureUpload(((Integer) entry[0]).intValue(),
                        (java.awt.image.BufferedImage) entry[1]);
            } catch (Throwable ignored) {
                // An announcement must never break backend installation; the mirror simply keeps what it had.
            }
        }
    }
}
