package org.soutalab.celtium.vulkan;

/**
 * The GL names whose lookup last answered "there is no source for a mirror", and the upload serial that answer was
 * given at.
 * <p>
 * <b>Why the answer needs remembering.</b> Every lookup for a name with no entry asks the stub for its captured level 0
 * surface ({@code fillFromCapture}), and a name that nothing has written - a mod's texture before its first upload, a
 * framebuffer's attachment before the owner publishes - answers "no source" every time. On a 1920x1080 render target
 * that question materializes a full-surface view per refused lookup, in the batch path, once per group that samples
 * the name. The lazy supply on the backend side already records the write serial for exactly this reason; this is the
 * same rule for the registry's own lookups.
 * <p>
 * <b>Why a serial and not a boolean.</b> "Nothing to build" is only true for the content it was asked about. The stub
 * numbers every upload, and {@code textureUploadSerial} answers the number of the level's last write - so a name that
 * gains pixels (a late {@code glTexSubImage2D} from a mod, a whole upload, a patch backlog) answers a different number
 * and is asked again. A plain "deferred" bit would have turned into a permanent refusal, which is the state the
 * content-less mirror was in: it existed, so the lookup returned it as a hit and never asked the capture again.
 * <p>
 * <b>What is never skipped.</b> A serial of {@link Long#MIN_VALUE} means the stub holds no record of the level at all
 * - not "unchanged", and not evidence that a later write cannot arrive. It is not recorded, so such a name is asked
 * every time, and the answer is the one the caller already gave. Likewise a name from another thread's stub epoch: the
 * gate is read and written on the render thread only, like every other registry table.
 */
final class MirrorDeferralGate {
    /** A world frame asks about about 194 names; 256 covers a frame's worth before the array has to grow. */
    private static final int MINIMUM_CAPACITY = 256;

    private long[] serialByTexture = new long[MINIMUM_CAPACITY];
    private boolean[] recorded = new boolean[MINIMUM_CAPACITY];
    private int namesDeferred;

    /**
     * {@return whether this name's lookup already decided there is no mirror source at this exact content}.
     * <p>
     * Pure: it records nothing and counts nothing, so a caller can ask before it pays for the question
     * {@code fillFromCapture} would have to answer again.
     */
    boolean isCurrent(int glTextureId, long contentSerial) {
        if (glTextureId <= 0 || glTextureId >= this.serialByTexture.length || !this.recorded[glTextureId]) {
            return false;
        }
        if (contentSerial == Long.MIN_VALUE) {
            // No record of the level: "nothing happened" and "nothing is known" are different answers, and only the
            // first one may be remembered.
            return false;
        }
        return this.serialByTexture[glTextureId] == contentSerial;
    }

    /**
     * Records that this name had no mirror source at this content serial.
     * <p>
     * A serial of {@link Long#MIN_VALUE} is not recorded - see {@link #isCurrent}: a name the stub knows nothing about
     * must keep being asked, because the next thing that happens to it may be its first upload.
     */
    void note(int glTextureId, long contentSerial) {
        if (glTextureId <= 0 || contentSerial == Long.MIN_VALUE) {
            return;
        }
        if (glTextureId >= this.serialByTexture.length) {
            int grown = Math.max(glTextureId + 1, this.serialByTexture.length * 2);
            this.serialByTexture = java.util.Arrays.copyOf(this.serialByTexture, grown);
            this.recorded = java.util.Arrays.copyOf(this.recorded, grown);
        }
        if (!this.recorded[glTextureId]) {
            this.namesDeferred++;
        }
        this.serialByTexture[glTextureId] = contentSerial;
        this.recorded[glTextureId] = true;
    }

    /** Forgets a name, so a re-created GL name does not inherit its predecessor's answer. */
    void forget(int glTextureId) {
        if (glTextureId > 0 && glTextureId < this.serialByTexture.length) {
            this.recorded[glTextureId] = false;
            this.serialByTexture[glTextureId] = 0L;
        }
    }

    /**
     * {@return how many names have been deferred}, for the mirror-economy line: a name forgotten and deferred again
     * counts twice, because that is two GL names worth of state (a reused id is a new texture).
     */
    int namesDeferred() {
        return this.namesDeferred;
    }
}
