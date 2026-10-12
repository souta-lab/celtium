package org.soutalab.celtium.vulkan;

/**
 * The texture domain's counters (refactoring plan R2, stage 3).
 *
 * <p>These are counters whose owner did not exist yet, so they move here rather than being read from somewhere: the
 * bind chain, the announcement path and the mirror path each increment them, and every writer now calls a named
 * note method instead of touching a field. The line reads the accessors, so its field names and order are unchanged.
 *
 * <p>Counters whose owner already exists are read from that owner and are not duplicated here: the texture copy
 * census belongs to TextureCopyService, the publication counters to PublishedFrameTextures, the registry's mirror
 * and upload counters to VulkanTextureRegistry, and the descriptor/pipeline counters to
 * VulkanImmediateTexturePipeline.
 */
public final class TextureStatistics {

    private long announcedUploads;

    /** Records one announcedUploads. */
    void noteAnnouncedUpload() {
        this.announcedUploads++;
    }

    /** {@return announcedUploads}. */
    long announcedUploads() {
        return this.announcedUploads;
    }

    private long animatedImageFills;

    /** Records one animatedImageFills. */
    void noteAnimatedImageFill() {
        this.animatedImageFills++;
    }

    /** {@return animatedImageFills}. */
    long animatedImageFills() {
        return this.animatedImageFills;
    }

    private long announcedDynamicTextures;

    /** Records one announcedDynamicTextures. */
    void noteAnnouncedDynamicTexture() {
        this.announcedDynamicTextures++;
    }

    /** {@return announcedDynamicTextures}. */
    long announcedDynamicTextures() {
        return this.announcedDynamicTextures;
    }

    private long colorMirrorUses;

    /** Records one colorMirrorUses. */
    void noteColorMirrorUse() {
        this.colorMirrorUses++;
    }

    /** {@return colorMirrorUses}. */
    long colorMirrorUses() {
        return this.colorMirrorUses;
    }

    private long directMirrorFromAnnouncement;

    /** Records one directMirrorFromAnnouncement. */
    void noteDirectMirrorFromAnnouncement() {
        this.directMirrorFromAnnouncement++;
    }

    /** {@return directMirrorFromAnnouncement}. */
    long directMirrorFromAnnouncement() {
        return this.directMirrorFromAnnouncement;
    }

    private long dynamicTextureFills;

    /** Records one dynamicTextureFills. */
    void noteDynamicTextureFill() {
        this.dynamicTextureFills++;
    }

    /** {@return dynamicTextureFills}. */
    long dynamicTextureFills() {
        return this.dynamicTextureFills;
    }

    private long textureRefusalsBeforeAccept;

    /** Records one textureRefusalsBeforeAccept. */
    void noteTextureRefusalBeforeAccept() {
        this.textureRefusalsBeforeAccept++;
    }

    /** {@return textureRefusalsBeforeAccept}. */
    long textureRefusalsBeforeAccept() {
        return this.textureRefusalsBeforeAccept;
    }

    private long bindTextureCalls;

    /** Records one bindTextureCalls. */
    void noteBindTextureCall() {
        this.bindTextureCalls++;
    }

    /** {@return bindTextureCalls}. */
    long bindTextureCalls() {
        return this.bindTextureCalls;
    }

    private long bindShortCircuits;

    /** Records one bindShortCircuits. */
    void noteBindShortCircuit() {
        this.bindShortCircuits++;
    }

    /** {@return bindShortCircuits}. */
    long bindShortCircuits() {
        return this.bindShortCircuits;
    }

    private long boundTextureComparisons;

    /** Records one boundTextureComparisons. */
    void noteBoundTextureComparison() {
        this.boundTextureComparisons++;
    }

    /** {@return boundTextureComparisons}. */
    long boundTextureComparisons() {
        return this.boundTextureComparisons;
    }

    private long boundTextureMismatches;

    /** Records one boundTextureMismatches. */
    void noteBoundTextureMismatch() {
        this.boundTextureMismatches++;
    }

    /** {@return boundTextureMismatches}. */
    long boundTextureMismatches() {
        return this.boundTextureMismatches;
    }

}
