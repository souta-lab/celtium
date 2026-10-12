package org.soutalab.celtium.vulkan;

/**
 * TextureStatistics - the texture domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
 * every leg record (scripts/cpu-contract/verify_line_baseline.txt), so this class does not choose an order: it
 * exposes one appendRunN per contiguous run of its fields and the backend calls them in the existing order. The
 * domains are interleaved in the line, so a single fragment() per class would reorder it.
 *
 * <p>Fields are package-private only for the wiring pass; the writers move in with the domain (R3a/R3b/R4/R5/R6)
 * and the visibility narrows then. Expressions that read another class or a helper (VulkanContext, CeltiumGlStub,
 * TextureCopyService, VulkanModelQuadCapture, the celtium\$* accessors, the *Summary helpers) are left as calls;
 * they are the wiring's decisions, not this class's fields.
 *
 * <p>Runs, in the line's order:
 * <li>run 0 (backend L8339-L8339): readResolvedFromLastDrawnTarget</li>
 * <li>run 1 (backend L8412-L8412): samplerFilters</li>
 * <li>run 2 (backend L8491-L8492): descriptorHits descriptorMisses</li>
 * <li>run 3 (backend L8524-L8531): descriptorSets pipelineBuildRollbacks inPlaceRefreshes mirroredTextures externalImages</li>
 * <li>run 4 (backend L8547-L8550): boundTextureSamples boundTextureMismatches announcedDynamicTextures colorMirrorUses</li>
 * <li>run 5 (backend L8583-L8601): imageFilledMirrors imageUploadsSkipped announcedUploads animatedImageFills textureCopiesServed textureCopiesRefused textureCopiesReusedInFrame textureCopyReadbacks textureCopyReadbackMillis textureCopiesServedPeakPerPresent textureCopiesReServedAfterContentChange textureCopyBlits textureCopyBlitRefusals lastTextureCopyBlitRefusal lastTextureCopyRefusal</li>
 * <li>run 6 (backend L8609-L8612): directMirrorFromAnnouncement dynamicTextureFills readbackByOrigin mirrorsCreatedByAnnouncement</li>
 * <li>run 7 (backend L8624-L8624): textureRefusalsBeforeAccept</li>
 * <li>run 8 (backend L8628-L8637): publishedFrameTexturesCreated publishedFrameTexturesUnpublished publishedFrameTexturesSkippedForAttach publishedFrameTexturesStalePruned publishedFrameTexturesRepublished</li>
 * <li>run 9 (backend L8659-L8659): textureDiagnostics diagnosed</li>
 * <li>run 10 (backend L8716-L8717): bindTextureCalls bindShortCircuits</li>
 */
public final class TextureStatistics {

    /** backend field announcedUploads (declared L1048). */
    int announcedUploads;
    /** backend field animatedImageFills (declared L1069). */
    int animatedImageFills;
    /** backend field directMirrorFromAnnouncement (declared L1070). */
    int directMirrorFromAnnouncement;
    /** backend field dynamicTextureFills (declared L1277). */
    int dynamicTextureFills;
    /** backend field textureRefusalsBeforeAccept (declared L1292). */
    int textureRefusalsBeforeAccept;
    /** backend field boundTextureComparisons (declared L1294). */
    int boundTextureComparisons;
    /** backend field announcedDynamicTextures (declared L1296). */
    int announcedDynamicTextures;
    /** backend field colorMirrorUses (declared L1300). */
    int colorMirrorUses;
    /** backend field boundTextureMismatches (declared L1301). */
    int boundTextureMismatches;
    /** backend field bindTextureCalls (declared L1855). */
    int bindTextureCalls;
    /** backend field bindShortCircuits (declared L1862). */
    int bindShortCircuits;

    /** Run 0, reproducing backend L8339-L8339: readResolvedFromLastDrawnTarget. */
    public void appendRun0(StringBuilder out) {
        out.append(" readResolvedFromLastDrawnTarget=");
        out.append(this.textureCopyService.readResolvedFromLastDrawnTarget());
    }

    /** Run 1, reproducing backend L8412-L8412: samplerFilters. */
    public void appendRun1(StringBuilder out) {
        out.append(" samplerFilters=");
        out.append(VulkanGlTexture.samplerFilterCensus());
    }

    /**
     * Run 2, the head of old L8491-L8492: descriptorHits.
     * <p>
     * Split from run 11 because the old L8492 carries this class's descriptorMisses and Immediate's
     * clearOrderFlushes on the same source line, so no single run could own that line. run 11 is called
     * immediately after this one.
     */
    public void appendRun2(StringBuilder out) {
        out.append(" descriptorHits=");
        out.append(VulkanImmediateTexturePipeline.DESCRIPTOR_HITS);
    }

    /** Run 11, the tail of old L8492: descriptorMisses, emitted between run 2 and Immediate's clearOrderFlushes. */
    public void appendRun11(StringBuilder out) {
        out.append(" descriptorMisses=");
        out.append(VulkanImmediateTexturePipeline.DESCRIPTOR_MISSES);
    }

    /** Run 3, reproducing backend L8524-L8531: descriptorSets pipelineBuildRollbacks inPlaceRefreshes mirroredTextures externalImages. */
    public void appendRun3(StringBuilder out) {
        out.append(" descriptorSets=");
        out.append(this.descriptorSetCensus());
        out.append(" pipelineBuildRollbacks=");
        out.append(VulkanImmediateTexturePipeline.BUILD_ROLLBACKS.get());
        out.append(" inPlaceRefreshes=");
        out.append((this.textureRegistry != null ? this.textureRegistry.inPlaceRefreshes() : 0));
        out.append(" mirroredTextures=");
        out.append((this.textureRegistry != null ? this.textureRegistry.mirrorCount() : 0));
        out.append(" externalImages=[");
        out.append((this.textureRegistry != null ? this.textureRegistry.externalSummary() : "none"));
    }

    /** Run 4, reproducing backend L8547-L8550: boundTextureSamples boundTextureMismatches announcedDynamicTextures colorMirrorUses. */
    public void appendRun4(StringBuilder out) {
        out.append(" boundTextureSamples=");
        out.append(this.boundTextureComparisons);
        out.append(" boundTextureMismatches=");
        out.append(this.boundTextureMismatches);
        out.append(" announcedDynamicTextures=");
        out.append(this.announcedDynamicTextures);
        out.append(" colorMirrorUses=");
        out.append(this.colorMirrorUses);
    }

    /** Run 5, reproducing backend L8583-L8601: imageFilledMirrors imageUploadsSkipped announcedUploads animatedImageFills textureCopiesServed textureCopiesRefused textureCopiesReusedInFrame textureCopyReadbacks textureCopyReadbackMillis textureCopiesServedPeakPerPresent textureCopiesReServedAfterContentChange textureCopyBlits textureCopyBlitRefusals lastTextureCopyBlitRefusal lastTextureCopyRefusal. */
    public void appendRun5(StringBuilder out) {
        out.append(" imageFilledMirrors=");
        out.append((this.textureRegistry != null ? this.textureRegistry.imageUploads() : 0));
        out.append(" imageUploadsSkipped=");
        out.append((this.textureRegistry != null ? this.textureRegistry.imageUploadsSkipped() : 0));
        out.append(" announcedUploads=");
        out.append(this.announcedUploads);
        out.append(" animatedImageFills=");
        out.append(this.animatedImageFills);
        out.append(" textureCopiesServed=");
        out.append(this.textureCopyService.textureCopiesServed());
        out.append(" textureCopiesRefused=");
        out.append(this.textureCopyService.textureCopiesRefused());
        out.append(" textureCopiesReusedInFrame=");
        out.append(this.textureCopyService.textureCopiesReusedInFrame());
        out.append(" textureCopyReadbacks=");
        out.append(this.textureCopyService.textureCopyReadbacks());
        out.append(" textureCopyReadbackMillis=");
        out.append((this.textureCopyService.textureCopyReadbackNanos() / 1000000L));
        out.append(" textureCopiesServedPeakPerPresent=");
        out.append(this.textureCopyService.textureCopiesServedPeakPerPresent());
        out.append(" textureCopiesReServedAfterContentChange=");
        out.append(this.textureCopyService.textureCopiesReServedAfterContentChange());
        out.append(" textureCopyBlits=");
        out.append(this.textureCopyService.textureCopyBlits());
        out.append(" textureCopyBlitRefusals=");
        out.append(this.textureCopyService.textureCopyBlitRefusals());
        out.append(" lastTextureCopyBlitRefusal=[");
        out.append(this.textureCopyService.lastTextureCopyBlitRefusal());
        out.append(" lastTextureCopyRefusal=[");
        out.append(this.textureCopyService.lastTextureCopyRefusal());
    }

    /** Run 6, reproducing backend L8609-L8612: directMirrorFromAnnouncement dynamicTextureFills readbackByOrigin mirrorsCreatedByAnnouncement. */
    public void appendRun6(StringBuilder out) {
        out.append(" directMirrorFromAnnouncement=");
        out.append(this.directMirrorFromAnnouncement);
        out.append(" dynamicTextureFills=");
        out.append(this.dynamicTextureFills);
        out.append(" readbackByOrigin=");
        out.append((this.textureRegistry != null ? this.textureRegistry.readbackByOrigin() : "none"));
        out.append(" mirrorsCreatedByAnnouncement=");
        out.append((this.textureRegistry != null ? this.textureRegistry.createdByAnnouncement() : 0));
    }

    /** Run 7, reproducing backend L8624-L8624: textureRefusalsBeforeAccept. */
    public void appendRun7(StringBuilder out) {
        out.append(" textureRefusalsBeforeAccept=");
        out.append(this.textureRefusalsBeforeAccept);
    }

    /** Run 8, reproducing backend L8628-L8637: publishedFrameTexturesCreated publishedFrameTexturesUnpublished publishedFrameTexturesSkippedForAttach publishedFrameTexturesStalePruned publishedFrameTexturesRepublished. */
    public void appendRun8(StringBuilder out) {
        out.append(" publishedFrameTexturesCreated=");
        out.append(this.publishedTextures.created());
        out.append(" publishedFrameTexturesUnpublished=");
        out.append(this.publishedTextures.unpublished());
        out.append(" publishedFrameTexturesSkippedForAttach=");
        out.append(this.publishedTextures.skippedForAttach());
        out.append(" publishedFrameTexturesStalePruned=");
        out.append(this.publishedTextures.stalePruned());
        out.append(" publishedFrameTexturesRepublished=");
        out.append(this.publishedTextures.republished());
    }

    /** Run 9, reproducing backend L8659-L8659: textureDiagnostics diagnosed. */
    public void appendRun9(StringBuilder out) {
        out.append(" textureDiagnostics=");
        out.append(TEXTURE_DIAGNOSTICS);
        out.append(" diagnosed=");
        out.append(this.diagnosedTextures.size());
    }

    /** Run 10, reproducing backend L8716-L8717: bindTextureCalls bindShortCircuits. */
    public void appendRun10(StringBuilder out) {
        out.append(" bindTextureCalls=");
        out.append(this.bindTextureCalls);
        out.append(" bindShortCircuits=");
        out.append(this.bindShortCircuits);
    }

}
