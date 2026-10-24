package org.soutalab.celtium.vulkan;

import java.util.Iterator;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The post-process chain bridge: runs a collected shader chain natively, adopts its framebuffers and keeps the textures it publishes to the game in the right layout.
 * Moved out of {@link VulkanTerrainGpuBackend} unchanged; the state it reads stays there.
 */
final class PostChainBridge {
    private final VulkanTerrainGpuBackend b;

    PostChainBridge(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * Runs one collected shader chain inside a GL-to-Vulkan handoff: GL releases the shared image, the passes draw
     * it (sampling it or the pure-Vulkan chain targets) and release it back, then GL waits and keeps going. The
     * caller binds the game framebuffer first, so the acquire sees the shared framebuffer under adoption.
     *
     * @return true when every pass was recorded and submitted; false leaves the caller's GL path to run instead
     */
    boolean celtium$postChainRun(java.util.List<VulkanPostChain.Pass> passes) {
        if (!b.celtium$postChainAvailable() || passes == null || passes.isEmpty()) {
            return false;
        }
        try {
            if (b.postChain == null) {
                b.postChain = VulkanPostChain.create(b.context, b.frameTarget);
            }
        } catch (RuntimeException | LinkageError error) {
            b.record("post chain executor creation failed: " + b.describe(error));
            return false;
        }
        if (!b.postChain.supportsAll(passes)) {
            StringBuilder reason = new StringBuilder("post chain refused:");
            for (VulkanPostChain.Pass pass : passes) {
                reason.append(' ').append(pass.program).append('(').append(pass.pushBytes).append("B)");
            }
            b.record(reason.toString());
            return false;
        }
        // The chain's own input has to be registered *before* the preflight that judges whether that input can be
        // supplied. The preflight walks the pass list and accepts only the shared frame image, an image an earlier
        // pass of this chain writes, or one already adopted under that identity - and the adoption used to happen
        // only after it, inside the frame. The entity-outline chain is exactly the case where that ordering is
        // wrong: its first pass reads "final", the silhouette target the game filled itself, so nothing in the list
        // supplies it, and the adoption that would have was unreachable. Every frame refused before recording, with
        // the programs routed and no chain: measured under GL-less as ROUTE/PACKED for entity_outline, blur and blit
        // followed by postChains=0 postPasses=0 postTargets=0 postAdopted=0. Registering the target here is the same
        // call the frame-time path makes; it is idempotent for a matching image, and this method only declares the
        // identity when every check the frame-time path performs has already passed, so a preflight that passes here
        // is one the frame can really supply.
        boolean inputDeclared = b.CHAIN_PREFLIGHT_ADOPTION && declareChainInputAdoption(passes);
        String unsupplied = b.postChain.unsuppliedInput(passes);
        if (unsupplied != null) {
            // N04: the chain reads an image this renderer cannot supply - under the GL bridge that is a real GL
            // framebuffer no Vulkan image stands for. Refused before the frame opens: nothing is acquired, no
            // semaphore is signalled, nothing is recorded, so the caller's GL chain runs on an untouched frame.
            b.record("post chain refused before the frame opened: " + unsupplied);
            reportPreflightRefusal(unsupplied, inputDeclared);
            return false;
        }
        boolean submitted = false;
        try {
            b.frameTarget.beginFrame();
            b.beginCommands();
            b.frameTarget.recordAcquireBarriers(b.commandBuffer);
            adoptChainFramebuffers(passes);
            reportChainInputRecorded(passes.get(0).groupMainFbId);
            if (b.publishedChainOutput != null) {
                // The chain's adopted target is GENERAL by contract, and the off-target pass may not have run in
                // this frame (the game can render the chain from an image an earlier frame left behind).
                b.publishedChainOutput.ensureGeneralLayout(b.commandBuffer);
            }
            // The same hand-back for every other published name: the chain is about to attach the pair image, which
            // declares GENERAL, and the sampling side left it in SHADER_READ_ONLY.
            celtium$transitionPublishedFrameTextures(true);
            int chainRecording = b.postChain.recordPasses(b.commandBuffer, passes);
            // The chain records into *this* command buffer and sets its own viewport and scissor
            // (`VulkanPostChain.recordPasses` -> vkCmdSetViewport/vkCmdSetScissor at VulkanPostChain.java:965/968).
            // The dedup trackers describe what this stream last set, so a writer that does not go through them makes
            // them stale, and the next group whose rectangle equals the remembered one would skip the command and be
            // drawn with the *chain's* rectangle. That is BH-13's failure shape reached from the other side, so the
            // trackers are invalidated here instead of routed: after the chain the state on this buffer is the
            // chain's, and the next group emits whatever it needs whatever that is.
            b.passVpX = b.passVpY = b.passVpW = b.passVpH = -1;
            b.passScX = b.passScY = b.passScW = b.passScH = -1;
            b.passBiasC = b.passBiasS = Float.NaN;
            if (b.publishedChainOutput != null) {
                // The chain has written the image and the game's composite samples it next, through a descriptor the
                // immediate pipelines declare as SHADER_READ_ONLY_OPTIMAL. Recorded in the chain's own submission,
                // so the layout is already right when the composite's command buffer runs - and the next frame's
                // off-target pass hands it back before it attaches the image again.
                b.publishedChainOutput.ensureSampledLayout(b.commandBuffer);
            }
            b.frameTarget.recordReleaseBarriers(b.commandBuffer);
            b.frameCoordinator.submitCommands();
            submitted = true;
            // The chain's effect on the picture, asked where it can still be attributed: one readback of the frame image
            // immediately after this submission, before anything later in the frame can overwrite it, and one of the
            // adopted chain pair when there is one. Off unless -Dceltium.vulkan.chainFrameDump=true. The passes go in
            // with it: a run holds several chains, and a sharp frame image after a chain whose output target is not
            // `main` is expected rather than evidence, so every dump line names the chain it took.
            celtium$dumpImagesAtChainTime(passes);
            // The queue accepted this submission, so the first-use barriers of *this* recording are fact - and only
            // this recording's: the token names it, so any other submission's success (a terrain pass, the next
            // frame, a buffer that was reset or abandoned) cannot commit these targets. N05 item 1.
            b.postChain.commitPasses(chainRecording);
        } catch (RuntimeException | LinkageError error) {
            VulkanGlTexture.abandonLayoutRecording(b.commandBuffer,
                    "post chain submission failed: " + b.describe(error));
            b.record("post chain submission failed: " + b.describe(error));
        } finally {
            if (submitted) {
                try {
                    b.frameTarget.endFrame();
                } catch (RuntimeException | LinkageError error) {
                    b.record("post chain frame end failed: " + b.describe(error));
                    return false;
                }
            } else {
// An abandoned frame is not "the frame is still recording": the flag must not route the
// next beginCommands into the buffer whose recording the abandon just discards.
b.frameBufferOpen = false;
                // GL is already holding the image or would wait on a signal that never comes: abandon rather than
                // leave the frame half-closed.
                b.frameTarget.abandonFrame("post chain submission refused");
                if (!b.scheduler.isPending(b.frameSlot) && !b.gpuStalled && b.commandState.recording()) {
                    // The recording the submission never made has to be closed: beginCommands() refuses to open a
                    // second recording on the same command buffer ("nested begin on backend command buffer"), so
                    // leaving the state at "recording" wedges every later terrain/immediate/chain recording
                    // (measured: frame count 205 -> 55, six nested-begin failures after one injected first-use
                    // failure). this.frameCoordinator.submitCommands() throws before vkQueueSubmit, so the buffer is not in flight and this
                    // reset touches nothing the queue owns - the same guard the terrain recovery path uses.
                    b.check(vkResetCommandBuffer(b.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT),
                            "vkResetCommandBuffer(post chain refusal)");
                    b.commandState.reset();
                // A mid-frame reset discards the frame buffer too: the unified flag must not
                // describe a buffer whose next begin thinks it is still recording.
                b.frameBufferOpen = false;
                }
            }
        }
        return submitted;
    }

    /**
     * Registers the chain's reported input image with the executor <em>before</em> the preflight that judges it.
     * <p>
     * The entity-outline chain's first pass reads {@code "final"} - the silhouette target the game filled itself -
     * so the preflight's own resolution (shared frame image, an image an earlier pass writes, or one already adopted
     * under that identity) can only accept it once that identity resolves to an image. The adoption that does that
     * used to run after the preflight, inside the frame, so it was unreachable: every glow frame was refused before
     * recording and the counter set was {@code postChains=0 postPasses=0 postTargets=0 postAdopted=0} with the
     * programs routed. This declares exactly what the frame-time path would adopt, and only after every check that
     * path performs has passed, so a preflight that accepts here is one the frame can really supply.
     *
     * @return whether the identity now resolves to the chain image (or already did)
     */
    boolean declareChainInputAdoption(java.util.List<VulkanPostChain.Pass> passes) {
        GllessFrameTarget glless = b.offTargetTarget();
        if (glless == null || b.postChain == null || passes.isEmpty()) {
            return false;
        }
        VulkanPostChain.Pass first = passes.get(0);
        int identity = first.groupMainFbId;
        if (identity == 0 || first.groupMainW <= 0 || first.groupMainH <= 0
                || identity == (int) b.targetRegistry.mainIdentity()) {
            return false;
        }
        // groupMainFbId is the Framebuffer object's identity hash (ShaderGroupMixin reports identityHashCode), which
        // is the same namespace the chain pairs key by - sign-extension matches how bindFramebuffer widened it.
        long chainIdentity = identity;
        if (b.framebufferColorTexture(chainIdentity) == null) {
            // Nothing was reported under this identity, so there is no GL name for the game's composite to bind
            // either; leaving it undeclared lets the preflight refuse with its own reason.
            return false;
        }
        if (!glless.chainPairMatches(chainIdentity, first.groupMainW, first.groupMainH)) {
            // The frame-time path reports this refusal together with the size it asked for. Declaring it here would
            // let the preflight accept an input the frame cannot supply, which is the N04 stand-in failure.
            return false;
        }
        try {
            b.postChain.adoptTarget(identity, glless.chainPairColorImage(chainIdentity), glless.chainPairColorView(chainIdentity),
                    first.groupMainW, first.groupMainH);
            b.chainAdoptionDeclarations++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            b.record("post chain input adoption could not be declared: " + b.describe(error));
            return false;
        }
    }

    /**
     * One bounded line per run: how much geometry had been recorded before this chain read its input image, and how
     * many groups were still queued for that image's identity at that moment.
     * <p>
     * This is the measurement the glow needs. The model path draws the silhouette into the chain pair and the counter
     * says it was drawn ({@code CELTIUM_MODEL_OFFTARGET_BATCHES calls=N drawn=N}); what is not visible from outside is
     * whether those batches had reached the image when pass 0 sampled it, because the silhouette's pass and the chain
     * are two submissions of the same frame. {@code deltaSinceLastChain} is the geometry recorded between two chain
     * reads - the whole silhouette of one frame if the ordering is right, a fraction of it if the rest is still
     * queued - and {@code pendingForInput} names the groups that are provably not in the image yet.
     */
    void reportChainInputRecorded(int inputIdentity) {
        long pending = inputIdentity == 0 ? 0L : b.countGroupsForTarget(inputIdentity);
        if (b.chainInputBatchLogs < 8) {
            b.chainInputBatchLogs++;
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_BATCHES frame=" + b.frameId
                    + " identity=" + inputIdentity + " pendingForInput=" + pending
                    + " recordedTotal=" + b.immediateStatistics.immediateRecordedBatches()
                    + " deltaSinceLastChain="
                    + (b.lastChainInputRecordedBatches < 0 ? -1
                            : b.immediateStatistics.immediateRecordedBatches() - b.lastChainInputRecordedBatches));
        }
        b.lastChainInputRecordedBatches = b.immediateStatistics.immediateRecordedBatches();
    }

    /**
     * Records the batches still queued for the target the game is drawing into, before a post chain reads that
     * target's image.
     * <p>
     * Called from {@link org.taumc.celeritas.mixin.core.ShaderGroupMixin} while the chain's input framebuffer is
     * still the current one and before the main framebuffer is bound: the model path coalesces its own flushes to one
     * per target change, so the silhouette batches that arrived after that first flush are still queued at chain time
     * and the bind that follows is the only other flush - this makes it explicit and ordered ahead of the read.
     *
     * @return how many groups were pending for the current target (0 when nothing had to be recorded)
     */
    int celtium$flushPendingForChainInput() {
        if (b.gpuStalled || b.immediateWindowBusy) {
            return 0;
        }
        int pending = b.countGroupsForTarget(b.targetRegistry.currentIdentity());
        if (pending == 0) {
            return 0;
        }
        b.flushPendingBeforeGlDraw();
        if (b.chainInputBatchLogs <= 12) {
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_FLUSH frame=" + b.frameId
                    + " target=" + b.targetRegistry.currentIdentity() + " groups=" + pending
                    + " recordedTotal=" + b.immediateStatistics.immediateRecordedBatches());
        }
        return pending;
    }

    /**
     * One bounded line per run naming what the chain's input image holds at the moment the chain is about to read it.
     * <p>
     * The counters can say the chain ran ({@code postChains>0}) while the picture shows no outline, and the two are
     * not separable from the frame: either nothing was drawn into the adopted image, or the image was cleared after
     * the silhouette reached it, or the composite sampled a different name than the one the image was published
     * under. This prints the four facts that tell them apart - the identity and GL name the chain reads, whether the
     * pair was cleared in this frame, every off-target identity that received batches (with the pair each routes to),
     * and the name the image is currently published under.
     */
    void reportChainInputState(int identity, long[] reported, GllessFrameTarget glless) {
        if (b.chainInputReports++ >= 3) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_OUTLINE_INPUT frame=" + b.frameId
                + " identity=" + identity + " texture=" + (reported == null ? -1L : reported[0])
                + " size=" + (reported == null ? "?" : reported[1] + "x" + reported[2])
                + " clearedThisFrame=" + (b.targetRegistry.chainPairClearedFrame(identity)
                        == b.celtium$frameSerial())
                + " chainPairClears=" + b.chainPairClears
                + " boundIdentity=" + b.currentFramebufferIdentity
                + " boundIsMain=" + b.targetRegistry.currentIsMain()
                + " offTargetByTarget=[" + b.offTargetSummary() + "]"
                + " publishedTexture=" + (b.publishedChainOutput != null ? b.publishedChainTexture : 0));
    }

    /**
     * One bounded line per run when the preflight refuses the chain.
     * <p>
     * The refusal was recorded but never printed, so a run whose chain stopped there had no line naming it - the
     * counters said a chain was seen ({@code postChains=0} with the programs routed) and nothing said why.
     */
    void reportPreflightRefusal(String reason, boolean inputDeclared) {
        if (b.preflightRefusalsReported++ >= 2) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_POSTCHAIN PREFLIGHT_REFUSED inputDeclarationSucceeded=" + inputDeclared
                + " declarations=" + b.chainAdoptionDeclarations + " reason=" + reason);
    }

    /**
     * Adopts the renderer-owned chain-pair image under the framebuffer identity the game bound and drew into, so a
     * pass that reads another pass's framebuffer - the entity-outline chain reads the silhouette framebuffer - samples
     * what the game actually drew there instead of an empty stand-in the chain allocated for itself.
     * <p>
     * The identity map is filled only by the game's own framebuffer binds, so a chain-allocated target (its "swap"
     * sibling, say) is never in it and cannot be adopted by mistake. The pair must already be in the layout the chain
     * assumes, which is what the frame's first clear for that pair records; if the frame has not used the pair yet,
     * the clear happens here, and if it has, the silhouette is live and must not be cleared again.
     * <p>
     * The image adopted is the <em>chain</em> pair and not the default one: the default pair is where the game's
     * finished-frame composites land, and adopting it is what made the chain blur a per-frame copy of the whole
     * scene instead of the silhouette (audit N04). The silhouette's own draws reach the chain pair through the
     * identity routing in {@link #recordImmediateOnlyPass}, before this runs - the bind the caller performs just
     * ahead of the chain is what flushes them.
     */
    void adoptChainFramebuffers(java.util.List<VulkanPostChain.Pass> passes) {
        GllessFrameTarget glless = b.offTargetTarget();
        if (glless == null || b.postChain == null || passes.isEmpty()) {
            // The GL bridge has real GL framebuffers behind these identities; there is no stand-in image to adopt.
            return;
        }
        // Only the identity the mixin reports as the chain's own first input, never "any framebuffer the game has
        // bound". That second rule was measured to over-adopt: the colour-texture census
        // (`PublishedFrameTextures.colorTextures`, which this comment still named by its pre-R1b field name) also
        // remembers the targets a chain bound while it was still rendering through GL, so the spectator group adopted
        // all four of its own targets (postTargets=0, postAdopted=4) and every blur pass read and wrote one image. A
        // chain-created target can never be reported as the chain's input, because the mixin reports what the group
        // reads first.
        VulkanPostChain.Pass first = passes.get(0);
        if (first.groupMainFbId == 0) {
            return;
        }
        adoptChainFramebuffer(first.groupMainFbId, first.groupMainW, first.groupMainH, glless);
    }

    void adoptChainFramebuffer(int identity, int width, int height, GllessFrameTarget glless) {
        long[] reported = identity == 0 ? null : b.framebufferColorTexture(identity);
        if (reported == null || width <= 0 || height <= 0 || identity == (int) b.targetRegistry.mainIdentity()) {
            return;
        }
        // identity is the Framebuffer identity hash (see ShaderGroupMixin), the same namespace the chain pairs key by.
        long chainIdentity = identity;
        if (!glless.chainPairMatches(chainIdentity, width, height)) {
            // Never resize a live pair here: the silhouette it holds is what the chain is about to read. A pair that
            // does not exist at all is refused the same way rather than created empty: the chain's real input is the
            // silhouette, and an image with nothing in it would make the chain report a run whose picture is the
            // deterministic result of reading nothing - the stand-in failure the audit measured, with a counter that
            // says otherwise. Refusing leaves the caller's GL chain in charge, which is the audit's N04 rule.
            b.chainAdoptionsRefused++;
            b.record("post chain adoption refused: the chain image is "
                    + (glless.chainPairExists(chainIdentity) ? "not " + width + "x" + height : "absent")
                    + " (asked for " + width + "x" + height + ", identity " + chainIdentity + ")");
            return;
        }
        // Before anything in this submission clears, attaches or samples the image: the previous frame's composite
        // may have left it in SHADER_READ_ONLY, and both the clear and the chain declare GENERAL.
        celtium$syncPublishedChainOutput(chainIdentity, glless.chainPairColorImage(chainIdentity));
        // The same clear-once contract as the pass sites: this is the post-chain adoption path, and it shares the
        // identity's pair with them, so the rule has to be one rule. Stated here because it is the second site of the
        // same shape - it is not a different question that happened to look alike.
        if (b.celtium$pairClearNeeded(RenderTargetRegistry.ImageSlot.CHAIN_PAIR,
                glless.chainPairAcquired(chainIdentity),
                b.targetRegistry.chainPairClearContentIdentity(chainIdentity),
                "chain-pair(sync):" + Long.toHexString(chainIdentity), chainIdentity)) {
            if (glless.chainPairNeedsSelfTest(chainIdentity)) {
                b.record(glless.chainPairSelfTest(chainIdentity, b.commandBuffer));
            } else {
                glless.clearChainPair(chainIdentity, b.commandBuffer);
            }
            b.targetRegistry.noteChainPairCleared(chainIdentity,
                    b.celtium$frameSerial(), chainIdentity);
            b.chainPairClears++;
            b.celtium$noteChainPairClear("sync-published-chain-output");
        }
        b.postChain.adoptTarget(identity, glless.chainPairColorImage(chainIdentity),
                glless.chainPairColorView(chainIdentity), width, height);
        b.chainAdoptions++;
        reportChainInputState(identity, reported, glless);
        // CELTIUM-OUTLINE-COMPOSITE: the chain's output IS this image, and the game composites it by binding the
        // outline framebuffer's colour texture name - the one the bind reported for this identity. Publishing the
        // image under that name is what gives the composite's batch a texture to sample: every batch resolves its
        // bound name through the registry, and with no entry there this one is refused and the glow never reaches
        // the screen. Nothing is created, resized, transitioned or freed on the image's behalf here.
        b.publishedChainOutput = b.celtium$registry().publishExternalImage((int) reported[0],
                glless.chainPairColorImage(chainIdentity), glless.chainPairColorView(chainIdentity), width, height);
        if (b.publishedChainOutput != null) {
            b.publishedChainOutput.notePairImage(true);
        }
        b.publishedChainTexture = (int) reported[0];
        b.publishedChainImage = glless.chainPairColorImage(chainIdentity);
        b.publishedChainIdentity = chainIdentity;
    }

    /**
     * Keeps the published outline output current and hands it back to the layout its owner attaches it in.
     * <p>
     * Called from the two places the chain pair's colour image is about to be used as an attachment: the off-target
     * pass that draws the game's own framebuffer into it, which clears it and then attaches it, and the chain
     * submission, whose adopted target is GENERAL by contract. The composite is the one sampler, and its transition
     * to SHADER_READ_ONLY is recorded at the end of the chain submission; without this hand-back the next attachment
     * would begin a render pass on an image in the wrong layout. A pair recreated at a new size is unpublished
     * rather than transitioned, because its wrapper names a destroyed image.
     *
     * @param chainPairColorImage the colour image the chain pair holds now, which is what the wrapper must name
     */
    /**
     * Publishes a game framebuffer's colour texture name over the chain pair's image, and keeps it current.
     * <p>
     * One entry per GL texture name, holding the image that name currently stands for. The same name is published
     * again only when the pair has been recreated (a new size makes a new image); a recreated pair's old image is
     * destroyed, so the wrapper that named it is unpublished first - the rule {@code celtium$syncPublishedChainOutput}
     * follows for the outline's name. Called from the off-target pass that draws the game's own framebuffer into the
     * pair, which is the moment both facts are true: the identity being drawn is known, and the image its draws land in
     * is the pair.
     */
    void celtium$publishFrameTexture(int glTextureName, long image, long view, int width, int height) {
        if (glTextureName == 0 || image == 0L || width <= 0 || height <= 0) {
            return;
        }
        VulkanGlTexture current = b.publishedTextures.published(glTextureName);
        if (current != null && b.publishedFrameImages.get(Integer.valueOf(glTextureName)) != null
                && b.publishedFrameImages.get(Integer.valueOf(glTextureName)).longValue() == image) {
            return;
        }
        if (current != null) {
            b.celtium$registry().unpublishExternalImage(glTextureName);
        }
        VulkanGlTexture published = b.celtium$registry().publishExternalImage(glTextureName, image, view, width, height);
        if (published == null) {
            return;
        }
        b.publishedTextures.putPublished(glTextureName, published);
        b.publishedFrameImages.put(Integer.valueOf(glTextureName), Long.valueOf(image));
        b.publishedTextures.noteCreated();
        // Which image this name stands for decides the layout a descriptor from it declares: a pair image is attached
        // by a pass that declares GENERAL, so with -Dceltium.vulkan.pairStayGeneral it must declare GENERAL too.
        GllessFrameTarget offTarget = b.offTargetTarget();
        // The native main attachment also stays in GENERAL. Treating only auxiliary pairs this way let its
        // published sampler move the main image to SHADER_READ_ONLY behind the native target owner's back;
        // the next world/menu acquisition then disagreed with the wrapper's remembered layout.
        published.notePairImage((b.frameTarget instanceof GllessFrameTarget && image == b.frameTarget.colorImage())
                || (offTarget != null && (offTarget.isChainPairColorImage(image)
                || image == offTarget.offTargetColorImage())));
        if (offTarget != null) {
            long owner = offTarget.chainPairIdentityForImage(image);
            if (owner != Long.MIN_VALUE) {
                b.publishedFrameImageIdentities.put(Integer.valueOf(glTextureName), Long.valueOf(owner));
            } else {
                b.publishedFrameImageIdentities.remove(Integer.valueOf(glTextureName));
            }
        }
    }

    /**
     * The mean colour of a small rectangle of an image, as {@code (r,g,b)}, read back through the copy path.
     * <p>
     * A patch rather than the whole frame: the question is only whether a region holds the game's picture or a flat
     * field, and 64x64 answers it at a cost a per-window trace can pay. The rectangle is the one the splash's logo
     * occupies, so a flat white patch is "no logo" and a dark patch is "logo".
     */
    String celtium$patchMean(long image, int x, int y, int size) {
        if (image == 0L) {
            return "none";
        }
        int[] pixels = b.celtium$readImageRectangle(image, x, y, size, size);
        if (pixels == null || pixels.length < size * size) {
            return "unreadable";
        }
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (int index = 0; index < size * size; index++) {
            int value = pixels[index];
            red += (value >> 16) & 0xFF;
            green += (value >> 8) & 0xFF;
            blue += value & 0xFF;
        }
        long count = (long) size * size;
        return "(" + (red / count) + "," + (green / count) + "," + (blue / count) + ")";
    }

    /**
     * {@return a live chain pair's colour image handle}, preferring the current target's pair, or 0 when this is not
     * the GL-less target or no pair exists. The probes that call this are one-shot diagnostics on the splash's own
     * pair; with per-identity pairs the "the chain pair" question needs an identity, and the current one is the pair
     * the current pass would attach.
     */
    long celtium$livePairImage() {
        GllessFrameTarget live = b.offTargetTarget();
        if (live == null) {
            return 0L;
        }
        long current = b.targetRegistry.currentIdentity();
        if (live.chainPairExists(current)) {
            return live.chainPairColorImage(current);
        }
        for (Long identity : live.chainPairIdentities()) {
            long image = live.chainPairColorImage(identity.longValue());
            if (image != 0L) {
                return image;
            }
        }
        return 0L;
    }

    /** {@return the same identity's live colour view}, following {@link #celtium$livePairImage}'s pick. */
    long celtium$livePairView() {
        GllessFrameTarget live = b.offTargetTarget();
        if (live == null) {
            return 0L;
        }
        long current = b.targetRegistry.currentIdentity();
        if (live.chainPairExists(current)) {
            return live.chainPairColorView(current);
        }
        for (Long identity : live.chainPairIdentities()) {
            long view = live.chainPairColorView(identity.longValue());
            if (view != 0L) {
                return view;
            }
        }
        return 0L;
    }

    /**
     * Moves every published frame texture to the layout its next use needs.
     * <p>
     * Two uses alternate on the same image: the pass that draws the game's framebuffer into the pair attaches it, which
     * declares GENERAL, and the pass that samples it through the published name has a descriptor declaring
     * SHADER_READ_ONLY_OPTIMAL. {@code general} picks which one is next; the sampling side is the transition that was
     * missing, and its absence is the VUID-vkCmdDraw-None-09600 the vsync leg reported at vkQueueSubmit.
     */
    void celtium$transitionPublishedFrameTextures(boolean general) {
        if (b.publishedTextures.isEmptyPublished() || b.commandBuffer == null) {
            return;
        }
        celtium$pruneStalePublishedNames();
        if (b.publishedTextures.isEmptyPublished()) {
            return;
        }
        for (java.util.Map.Entry<Integer, VulkanGlTexture> entry : b.publishedTextures.publishedEntries()) {
            GllessFrameTarget pairOwner = b.offTargetTarget();
            Long publishedImageHandle = b.publishedFrameImages.get(entry.getKey());
            long publishedImageIdentity = pairOwner == null || publishedImageHandle == null
                    ? Long.MIN_VALUE : pairOwner.chainPairIdentityForImage(publishedImageHandle.longValue());
            boolean pairImageName = publishedImageIdentity != Long.MIN_VALUE;
            // The sampling side must not move the image this pass is about to attach: the pass declares GENERAL for
            // attachment 0, and the transition to SHADER_READ_ONLY_OPTIMAL right before vkCmdBeginRenderPass is
            // VUID-vkCmdBeginRenderPass-initialLayout-00900. The attach-side hand-back (the `true` direction) already
            // moved it to GENERAL for this pass; leaving it there is the whole fix. A batch that samples this same
            // image inside the pass would be a feedback loop and is not a case this rule has to serve.
            if (!general && b.passAttachedColorImage != 0L) {
                Long image = b.publishedFrameImages.get(entry.getKey());
                if (image != null && image.longValue() == b.passAttachedColorImage) {
                    b.publishedTextures.noteSkippedForAttach();
                    if (b.publishedTextures.skippedForAttach() == 1) {
                        b.record("published name " + entry.getKey() + " stands for the image this pass attaches (0x"
                                + Long.toHexString(b.passAttachedColorImage) + "); the sampling-side transition"
                                + " would leave it in SHADER_READ_ONLY under a pass that declares GENERAL, so it is"
                                + " skipped and the attach-side hand-back keeps it in GENERAL");
                    }
                    if (b.pairSkipLogs < 8) {
                        // Whether the refusal leaves a draw without the layout its descriptor declares: the count is
                        // of groups that sampled a published chain-pair name, this frame and cumulatively.
                        b.pairSkipLogs++;
                        System.out.println("[Celtium] CELTIUM_PAIR_SKIP frame=" + b.frameId
                                + " name=" + entry.getKey() + " attached=0x"
                                + Long.toHexString(b.passAttachedColorImage)
                                + " identity=0x" + Long.toHexString(b.targetRegistry.currentIdentity())
                                + " samplingDrawsThisRun=" + b.pairNameSamplingDraws
                                + " samplingDrawsLastFrame=" + b.pairNameSamplingDrawsFrame
                                + " (0 means nothing sampled this name before the skip)");
                    }
                    continue;
                }
            }
            if (general) {
                entry.getValue().ensureGeneralLayout(b.commandBuffer);
            } else if (b.PAIR_STAY_GENERAL && pairImageName) {
                // The pair is never moved out of the layout its pass declares, so its sampling transition is not
                // served. Counted, so "the transition was skipped" is a measurement; and the pair's own record follows,
                // because the image is still in GENERAL and a record left at SHADER_READ_ONLY would be stale.
                b.publishedTextures.noteSkippedForAttach();
                if (pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                    pairOwner.noteChainPairColorLayout(publishedImageIdentity,
                            org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
                }
            } else {
                entry.getValue().ensureSampledLayout(b.commandBuffer);
            }
            // The pair's own record of the image's layout follows the wrapper's barrier, but only for the names that
            // stand for the pair image: a name published over the frame image is not the pair's business. This is what
            // makes the pair's actual layout readable next to the GENERAL its pass declares and the layout the
            // descriptor was written with - the three values VUID-vkCmdDraw-None-09600 compares.
            if (pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                pairOwner.noteChainPairColorLayout(publishedImageIdentity, general
                        ? org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL
                        : org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                celtium$reportPairLayout(general ? "hand-back" : "sampling");
            }
        }
    }

    /**
     * Prints the chain pair's three layouts when they change: what the pass declares, what the image is in, and what
     * the last sampling barrier left the published wrapper recording.
     * <p>
     * Bounded to {@code -Dceltium.vulkan.pairLayoutLines} (default 12) and printed only on a change, the same shape as
     * the splash flip trace: the question is which of the three moves first in a frame, and a per-frame repetition
     * would hide that. The declared value is the constant the pair's pass is built with
     * ({@code GllessFrameTarget.COLOR_RENDER_LAYOUT}); the actual value is the pair's own record; the wrapper column is
     * the published wrapper's recorded layout, which is what a descriptor written from it carries.
     */
    void celtium$reportPairLayout(String site) {
        GllessFrameTarget pairTarget = b.offTargetTarget();
        if (pairTarget == null) {
            return;
        }
        String line = celtium$pairLayoutSummary();
        if ("pair=absent".equals(line)) {
            return;
        }
        if (line.equals(b.lastPairLayoutLine) || b.pairLayoutLogs >= b.PAIR_LAYOUT_LINES) {
            return;
        }
        b.lastPairLayoutLine = line;
        b.pairLayoutLogs++;
        System.out.println("[Celtium] CELTIUM_PAIR_LAYOUT frame=" + b.frameId + " site=" + site + " " + line
                + " attached=0x" + Long.toHexString(b.passAttachedColorImage));
    }

    /**
     * {@return every chain pair's three layouts as one string}: what the pass declares, what the image is actually
     * in, and what each published wrapper records - the three values {@code VUID-vkCmdDraw-None-09600} compares.
     * With per-identity pairs there is one entry per identity. Used by the change-triggered line and by
     * {@code CELTIUM_VERIFY}, so the state at the end of a run is recorded even when nothing changed late enough to
     * print.
     */
    String celtium$pairLayoutSummary() {
        GllessFrameTarget pairTarget = b.offTargetTarget();
        if (pairTarget == null) {
            return "pair=absent";
        }
        StringBuilder pairs = new StringBuilder();
        for (Long identity : pairTarget.chainPairIdentities()) {
            if (pairTarget.chainPairColorImage(identity.longValue()) == 0L) {
                continue;
            }
            if (pairs.length() > 0) {
                pairs.append(' ');
            }
            pairs.append("0x").append(Long.toHexString(identity.longValue())).append(":[declared=")
                    .append(celtium$layoutName(pairTarget.chainPairDeclaredLayout()))
                    .append(" actual=").append(celtium$layoutName(pairTarget.chainPairColorLayout(identity.longValue())))
                    .append(']');
        }
        if (pairs.length() == 0) {
            return "pair=absent";
        }
        return "pair=" + pairs + " wrappers=" + b.publishedTextures.publishedLayoutsSummary();
    }

    /** {@return a Vulkan layout's name, so a line can be read without a table}. */
    static String celtium$layoutName(int layout) {
        switch (layout) {
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED: return "UNDEFINED";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL: return "GENERAL";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return "SHADER_READ_ONLY";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return "TRANSFER_SRC";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL: return "TRANSFER_DST";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL: return "COLOR_ATTACHMENT";
            default: return "layout" + layout;
        }
    }

    /**
     * Drops every published name whose image is no longer the pair's live image.
     * <p>
     * A name is published over the renderer's own image so the game's composite can sample it by the name it binds. That
     * image is destroyed and replaced when the pair is recreated (a size change, the first real frame after the splash),
     * and the wrapper then names freed memory. Recording a barrier on it is not a validation warning: the menu leg
     * measured `vkCmdPipelineBarrier(): pImageMemoryBarriers[0].image Invalid VkImage Object 0x300000000030` and then a
     * <b>SIGSEGV inside libvulkan_radeon</b> — the driver dereferenced the freed handle. The unpublish-on-recreation rule
     * already existed ({@code celtium$syncPublishedChainOutput}), but it only ran on the chain-identity branch, so a
     * frame that samples the name before that branch runs used the stale wrapper. This is the same rule at the point of
     * use, so a recreated pair cannot be sampled through a name that still points at the old image.
     */
    void celtium$pruneStalePublishedNames() {
        if (b.publishedTextures.isEmptyPublished()) {
            return;
        }
        GllessFrameTarget offTarget = b.offTargetTarget();
        if (offTarget == null) {
            // Not the GL-less frame target (the bridge configuration keeps its own target type): the published names are
            // not ours to judge here.
            return;
        }
        java.util.Iterator<java.util.Map.Entry<Integer, VulkanGlTexture>> iterator =
                b.publishedTextures.publishedEntries().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Integer, VulkanGlTexture> entry = iterator.next();
            Long image = b.publishedFrameImages.get(entry.getKey());
            // A name survives only while the pair it belongs to still owns the very image it names. The owner is the
            // identity the image was published under - recorded at publish time - because with per-identity pairs the
            // live image differs per framebuffer and "the" chain pair is a question with no single answer.
            Long ownerBoxed = b.publishedFrameImageIdentities.get(entry.getKey());
            long owner = ownerBoxed == null ? Long.MIN_VALUE : ownerBoxed.longValue();
            // A name with no chain-pair owner stands for the frame image or the default pair: its live handle is the
            // one those owners publish now, so it survives as long as that is still the handle it names.
            long live;
            if (owner != Long.MIN_VALUE) {
                live = offTarget.chainPairColorImage(owner);
            } else if (image != null && (image.longValue() == b.frameTarget.colorImage()
                    || image.longValue() == offTarget.offTargetColorImage())) {
                live = image.longValue();
            } else {
                live = 0L;
            }
            if (image != null && live != 0L && image.longValue() == live) {
                continue;
            }
            // The name's meaning is "the game framebuffer's colour texture, whose pixels the renderer serves from its
            // pair image". When that image is recreated the name must follow it to the new one, not disappear: a draw
            // that samples an unpublished name resolves to no view, and the measured consequence was a descriptor with
            // `VkImageView 0x0` (invalid or destroyed) followed by VK_ERROR_DEVICE_LOST on a plain terrain leg. So the
            // re-publication happens here, before any barrier could be recorded on the old handle, and the wrapper is
            // replaced with one standing for the live image.
            if (live != 0L) {
                VulkanGlTexture republished = b.celtium$registry().publishExternalImage(entry.getKey().intValue(), live,
                        offTarget.chainPairColorView(owner), b.targetWidth, b.targetHeight);
                if (republished != null) {
                    entry.setValue(republished);
                    b.publishedFrameImages.put(entry.getKey(), Long.valueOf(live));
                    b.publishedTextures.noteRepublished();
                    continue;
                }
            }
            // No live pair image at all (teardown, or the release half of a re-creation): there is nothing valid to
            // point the name at, so it is dropped rather than left naming freed memory.
            b.celtium$registry().unpublishExternalImage(entry.getKey().intValue());
            b.publishedFrameImages.remove(entry.getKey());
            b.publishedFrameImageIdentities.remove(entry.getKey());
            b.publishedTextures.noteUnpublished();
            b.publishedTextures.noteStalePruned();
            if (b.publishedTextures.stalePruned() == 1) {
                b.record("published name " + entry.getKey() + " named image 0x"
                        + (image == null ? "unknown" : Long.toHexString(image.longValue()))
                        + " which is no longer the pair's image (0x" + Long.toHexString(live)
                        + " for identity 0x" + Long.toHexString(owner)
                        + "); unpublished before any barrier could be recorded on it");
            }
            iterator.remove();
        }
    }

    /**
     * Follows a pair re-creation for every published name whose image died with it.
     * <p>
     * With per-identity pairs the caller names <em>which</em> identity's pair was recreated: only names published over
     * that identity's image can be stale; the others name living images and are left alone. The name's owner is the
     * identity recorded at publish time.
     *
     * @param identity the framebuffer identity whose pair was (re)created
     * @param chainPairColorImage the pair's live colour image now
     */
    void celtium$syncPublishedChainOutput(long identity, long chainPairColorImage) {
        if (!b.publishedTextures.isEmptyPublished()) {
            GllessFrameTarget liveTarget = b.offTargetTarget();
            long liveImage = liveTarget == null ? 0L : liveTarget.chainPairColorImage(identity);
            long liveView = liveTarget == null ? 0L : liveTarget.chainPairColorView(identity);
            java.util.Iterator<java.util.Map.Entry<Integer, VulkanGlTexture>> iterator =
                    b.publishedTextures.publishedEntries().iterator();
            while (iterator.hasNext()) {
                java.util.Map.Entry<Integer, VulkanGlTexture> entry = iterator.next();
                Long image = b.publishedFrameImages.get(entry.getKey());
                // Only names owned by this identity's pair can be stale when the pair is recreated; a name for the
                // frame image, the default pair or another identity's pair still names a living image.
                Long ownerBoxed = b.publishedFrameImageIdentities.get(entry.getKey());
                if (ownerBoxed == null || ownerBoxed.longValue() != identity) {
                    continue;
                }
                if (image != null && image.longValue() == chainPairColorImage) {
                    continue;
                }
                // The pair was recreated, and the name's meaning is "the game framebuffer's colour texture, whose pixels
                // the renderer serves from its pair image": it must follow the image to the new one. Dropping it here is
                // what made the loading phase a uniform white field - the registry resolves a name with no external image
                // through announcements and then the empty mirror, and the composite that samples it then paints the
                // empty mirror. The re-pointing is the same rule `celtium$pruneStalePublishedNames` applies at the point
                // of use, applied at the point of recreation, which is where it was measured to happen five times in one
                // leg for glTexture=22.
                if (liveImage != 0L && liveView != 0L) {
                    VulkanGlTexture republished = b.celtium$registry().publishExternalImage(entry.getKey().intValue(),
                            liveImage, liveView, b.targetWidth, b.targetHeight);
                    if (republished != null) {
                        entry.setValue(republished);
                        b.publishedFrameImages.put(entry.getKey(), Long.valueOf(liveImage));
                        b.publishedTextures.noteRepublished();
                        continue;
                    }
                }
                b.celtium$registry().unpublishExternalImage(entry.getKey().intValue());
                b.publishedFrameImages.remove(entry.getKey());
                b.publishedFrameImageIdentities.remove(entry.getKey());
                b.publishedTextures.noteUnpublished();
                iterator.remove();
            }
        }
        if (b.publishedChainOutput == null) {
            return;
        }
        if (b.publishedChainIdentity == identity && b.publishedChainImage != chainPairColorImage) {
            b.celtium$registry().unpublishExternalImage(b.publishedChainTexture);
            b.publishedChainOutput = null;
            b.publishedChainImage = 0L;
            return;
        }
        b.publishedChainOutput.ensureGeneralLayout(b.commandBuffer);
    }

    /**
     * One-shot readback of the frame image and of the adopted chain pair immediately after the chain's submission.
     * <p>
     * The question it answers is where a post chain's effect goes: the passes demonstrably run ({@code postChains},
     * {@code postPasses}, {@code postTargets}) and the presented picture still shows no blur, so either the frame image
     * holds the chain's output at this point - and the loss is later, in the present or composite path - or it does not,
     * and the passes' writes are not landing in the image the chain's output target claims. Both answers name the next
     * owner. The readback is the same one the texture-copy path uses, of the whole image, and it is synchronous because
     * it has to see this submission and nothing after it. Off unless
     * {@code -Dceltium.vulkan.chainFrameDump=true}; the path comes from
     * {@code -Dceltium.vulkan.chainFrameDumpPath} (default {@code /tmp/celtium-chain-frame.png}) and the count from
     * {@code -Dceltium.vulkan.chainFrameDumps} (default 1).
     */
    void celtium$dumpImagesAtChainTime(java.util.List<VulkanPostChain.Pass> passes) {
        if (!b.CHAIN_FRAME_DUMP) {
            return;
        }
        if (b.chainFrameDumps >= b.CHAIN_FRAME_DUMPS) {
            return;
        }
        b.chainFrameDumps++;
        // Which chain this is: a run holds several, and the frame image is only expected to carry the output of the one
        // whose output target is `main`. The identity is the first pass's program, how many passes there are, whether
        // that first pass reads and the last one writes the frame image, and the framebuffer ids involved.
        String identity = "chain=" + b.chainFrameDumps + " passes=" + passes.size()
                + " first=" + passes.get(0).program + " last=" + passes.get(passes.size() - 1).program
                + " inIsMain=" + passes.get(0).inIsMain + " outIsMain=" + passes.get(passes.size() - 1).outIsMain
                + " outFbId=" + passes.get(passes.size() - 1).outFbId
                + " frameId=" + b.frameId;
        if (!b.waitForIdle()) {
            b.record("chain frame dump skipped: the GPU did not confirm the chain submission");
            return;
        }
        String base = System.getProperty("celtium.vulkan.chainFrameDumpPath", "/tmp/celtium-chain-frame.png");
        if (b.frameTarget != null) {
            b.celtium$dumpOwnedImage("frame", b.frameTarget.colorImage(), base, identity,
                    b.targetWidth, b.targetHeight);
        }
        GllessFrameTarget glless = b.offTargetTarget();
        if (glless != null) {
            for (Long chainId : glless.chainPairIdentities()) {
                long pairImage = glless.chainPairColorImage(chainId.longValue());
                if (pairImage == 0L) {
                    continue;
                }
                if (glless.chainPairMatches(chainId.longValue(), b.targetWidth, b.targetHeight)) {
                    // Dumped at the frame's size: the pair is created at the size the game's framebuffer asks for,
                    // and the readback's extent has to be inside the image it reads. A pair of another size is named
                    // rather than read.
                    b.celtium$dumpOwnedImage("chain-pair-0x" + Long.toHexString(chainId.longValue()), pairImage,
                            base, identity + " pairSize=" + b.targetWidth + "x" + b.targetHeight,
                            b.targetWidth, b.targetHeight);
                } else {
                    System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=chain-pair skipped=size"
                            + " identity=0x" + Long.toHexString(chainId.longValue())
                            + " frameSize=" + b.targetWidth + "x" + b.targetHeight + " " + identity);
                }
            }
        }
        // Each pass's own output image, from the executor's bookkeeping (`VulkanPostChain.lastRecordedOutputs`, filled
        // as the passes are recorded and readable before `commitPasses`): the frame image alone cannot tell "pass 0
        // wrote nothing" from "the final blit never reached main", and that is the fork this line exists for. The
        // "main" entries are skipped - the frame image above is that image - and each line names its pass, program and
        // label so a chain of several passes is attributable pass by pass. Off unless
        // -Dceltium.vulkan.chainDumpPasses=true, so a normal leg pays nothing for it.
        if (Boolean.getBoolean("celtium.vulkan.chainDumpPasses") && b.postChain != null) {
            for (VulkanPostChain.RecordedOutput out : b.postChain.lastRecordedOutputs()) {
                if (out.image == 0L || "main".equals(out.label)) {
                    continue;
                }
                // The input side rides on the same line: "the pass read the right image but wrote nothing" and "the
                // pass read the wrong image" are different faults, and only the pair tells them apart. A `main` input
                // is the frame image, reported as 0 handles by the executor - the label is what names it.
                b.celtium$dumpOwnedImage("pass-out", out.image, base,
                        identity + " pass=" + out.passIndex + " program=" + out.program + " label=" + out.label
                                + " out=" + out.width + "x" + out.height
                                + " in=" + out.inLabel + (out.inImage == 0L ? "" : "(0x" + Long.toHexString(out.inImage)
                                        + ")") + " in=" + out.inWidth + "x" + out.inHeight,
                        out.width, out.height);
            }
        }
    }
}
