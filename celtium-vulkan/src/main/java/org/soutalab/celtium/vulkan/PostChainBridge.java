package org.soutalab.celtium.vulkan;

import java.util.Iterator;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The post-process chain bridge: runs a collected shader chain natively, adopts its framebuffers and keeps the textures it publishes to the game in the right layout.
 * Moved out of {@link VulkanTerrainGpuBackend} unchanged; the state it reads stays there.
 */
final class PostChainBridge {
    /** Where a requested chain frame dump goes; a property cannot change after startup, so it is resolved once. */
    private static final String CHAIN_FRAME_DUMP_PATH = System.getProperty("celtium.vulkan.chainFrameDumpPath",
            System.getProperty("java.io.tmpdir", ".") + java.io.File.separator + "celtium-chain-frame.png");
    /** Off unless asked for; consulted once per window, never per pass. */
    private static final boolean CHAIN_DUMP_PASSES = Boolean.getBoolean("celtium.vulkan.chainDumpPasses");

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
        // The stage gate (item 327, Vulcanizator V-01/V-02): a switched-off or quarantined post chain answers
        // false before any work - the caller's GL chain carries the frame, and a quarantined executor is never
        // retried. The trip itself was recorded once by the gate; per-frame silence is correct here.
        if (!StageGate.system().enabled(StageGate.Stage.POST_CHAIN)) {
            return false;
        }
        try {
            if (b.postChain == null) {
                b.postChain = VulkanPostChain.create(b.context, b.frameTarget);
            }
        } catch (RuntimeException | LinkageError error) {
            // A creation that failed on this context and target fails identically every frame: quarantine the
            // stage instead of retrying the corpse on every subsequent frame (item 327).
            StageGate.system().trip(StageGate.Stage.POST_CHAIN, error);
            b.record("post chain executor creation failed: " + b.describe(error));
            return false;
        }
        RenderTargetRegistry.TargetFormatClass chainFormatClass = celtium$chainFormatClass(passes);
        if (!b.postChain.supportsAll(passes, chainFormatClass)) {
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
            int chainRecording = b.postChain.recordPasses(b.commandBuffer, passes, chainFormatClass);
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
            FlushBoundaryCensus.noteMidFrameSubmit(FlushBoundaryCensus.SUBMIT_CHAIN);
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
            StageGate.system().noteSuccess(StageGate.Stage.POST_CHAIN);
        } catch (RuntimeException | LinkageError error) {
            // Counted toward the stage's quarantine (item 327): three consecutive submission failures
            // quarantine the post chain for the session; the GL chain carries those frames meanwhile.
            StageGate.system().noteFailure(StageGate.Stage.POST_CHAIN, error);
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
            // The pair's class travels with the adoption (QUEUE 395): the chain runs in the class the pair was
            // allocated in, so the adopted image is attached through that class's render pass. Before format
            // selection this site refused any non-default pair (QUEUE 404, rule R10) - the refusal is gone
            // because the mismatch it guarded against no longer exists.
            b.postChain.adoptTarget(identity, glless.chainPairColorImage(chainIdentity), glless.chainPairColorView(chainIdentity),
                    first.groupMainW, first.groupMainH, glless.chainPairFormatClass(chainIdentity));
            b.chainAdoptionDeclarations++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            b.record("post chain input adoption could not be declared: " + b.describe(error));
            return false;
        }
    }

    /**
     * {@return the format class this chain runs in} (QUEUE 395, D18-B): the class the chain pair for the group's
     * own framebuffer was allocated in. The pair's class is the game's own statement of what the chain's targets
     * must hold - a mod that asked GL for a 16F framebuffer to run a bloom chain in gets a chain whose targets
     * are 16F, and the chain's output lands back in that same pair. A chain whose group main is the game's main
     * framebuffer, or that has no GL-less pair behind it, runs in the default class: the shipped 8-bit behaviour,
     * unchanged.
     */
    private RenderTargetRegistry.TargetFormatClass celtium$chainFormatClass(
            java.util.List<VulkanPostChain.Pass> passes) {
        GllessFrameTarget glless = b.offTargetTarget();
        if (glless == null || passes.isEmpty()) {
            return RenderTargetRegistry.TargetFormatClass.RGBA8_UNORM;
        }
        int identity = passes.get(0).groupMainFbId;
        if (identity == 0 || identity == (int) b.targetRegistry.mainIdentity()) {
            return RenderTargetRegistry.TargetFormatClass.RGBA8_UNORM;
        }
        return glless.chainPairFormatClass(identity);
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
        b.celtium$flushPendingForGlDrawCause(FlushBoundaryCensus.CAUSE_CHAIN_ORDER);
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
        // The pair's class is no refusal here (QUEUE 395 replaced the rule R10 refusal QUEUE 404 left): the
        // chain runs in the pair's class, so the adopted image is attached through a pass that declares its
        // format. The class travels with the adoption below.
        // Before anything in this submission clears, attaches or samples the image: the previous frame's composite
        // may have left it in SHADER_READ_ONLY, and both the clear and the chain declare GENERAL.
        celtium$syncPublishedChainOutput(chainIdentity, glless.chainPairColorImage(chainIdentity));
        // The same clear-once contract as the pass sites: this is the post-chain adoption path, and it shares the
        // identity's pair with them, so the rule has to be one rule. Stated here because it is the second site of the
        // same shape - it is not a different question that happened to look alike.
        if (b.celtium$pairClearNeeded(RenderTargetRegistry.ImageSlot.CHAIN_PAIR,
                glless.chainPairAcquired(chainIdentity),
                b.targetRegistry.chainPairClearContentIdentity(chainIdentity),
                chainIdentity)) {
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
                glless.chainPairColorView(chainIdentity), width, height,
                glless.chainPairFormatClass(chainIdentity));
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
        // One lookup: image is non-zero here (the guard above), so 0 is the absent sentinel.
        if (current != null && b.publishedFrameImages.get(glTextureName) == image) {
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
        b.publishedFrameImages.put(glTextureName, image);
        b.publishedTextures.noteCreated();
        // Which image this name stands for decides the layout a descriptor from it declares: a pair image is attached
        // by a pass that declares GENERAL, and pair images stay in GENERAL, so it must declare GENERAL too.
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
                b.publishedFrameImageIdentities.put(glTextureName, owner);
                int slot = offTarget.chainPairColorSlotForImage(owner, image);
                if (slot > 0) {
                    b.publishedFrameImageSlots.put(glTextureName, slot);
                } else {
                    b.publishedFrameImageSlots.remove(glTextureName);
                }
            } else {
                b.publishedFrameImageIdentities.remove(glTextureName);
                b.publishedFrameImageSlots.remove(glTextureName);
            }
        }
    }

    /**
     * The depth twin of {@link #celtium$publishFrameTexture}: one GL depth texture name over the shared depth
     * image that stands for it (SHADOW-NATIVE-DESIGN section 3.4).
     * <p>
     * The name's store key is recorded beside the publication ({@code publishedFrameDepthKeys}), because the
     * shared depth store - not a pair - holds this image's layout record, and the transition walk reports the
     * wrapper's moves back into it. No pair identity is recorded: the pair owner map names colour images only,
     * and the stale-name prune reads the store for depth names instead.
     */
    void celtium$publishFrameDepthTexture(int glTextureName, long image, long view, int width, int height,
            int declaredLayout, long depthKey) {
        if (glTextureName == 0 || image == 0L || view == 0L || depthKey == 0L || width <= 0 || height <= 0) {
            return;
        }
        VulkanGlTexture current = b.publishedTextures.published(glTextureName);
        if (current != null && current.isDepthExternal()
                && b.publishedFrameImages.get(glTextureName) == image
                && b.publishedFrameDepthKeys.get(glTextureName) == depthKey) {
            return;
        }
        if (current != null) {
            b.celtium$registry().unpublishExternalImage(glTextureName);
        }
        VulkanGlTexture published = b.celtium$registry().publishExternalDepthImage(glTextureName, image, view,
                width, height, declaredLayout);
        if (published == null) {
            return;
        }
        b.publishedTextures.putPublished(glTextureName, published);
        b.publishedFrameImages.put(glTextureName, image);
        b.publishedFrameDepthKeys.put(glTextureName, depthKey);
        b.publishedTextures.noteCreated();
    }

    /** Drop wrappers for a deleted target before its owner retires the image and view. */
    void celtium$forgetPublishedTarget(long identity) {
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> it =
                b.publishedFrameImageIdentities.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry = it.next();
            if (entry.getLongValue() != identity) continue;
            int name = entry.getIntKey();
            b.celtium$registry().unpublishExternalImage(name);
            b.publishedTextures.removePublished(name);
            b.publishedFrameImages.remove(name);
            b.publishedFrameImageSlots.remove(name);
            it.remove();
        }
        if (b.publishedChainOutput != null && b.publishedChainIdentity == identity) {
            b.celtium$registry().unpublishExternalImage(b.publishedChainTexture);
            b.publishedChainOutput = null;
            b.publishedChainTexture = 0;
            b.publishedChainImage = 0L;
            b.publishedChainIdentity = 0L;
        }
        // Depth publications record no pair identity, so they are matched through the depth record instead -
        // which the caller still holds at this point (it clears the identity's depth binding after this
        // returns). A name whose depth texture belongs to the forgotten identity is unpublished with the
        // colour names, before the shared depth store entry it stands for is retired.
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> depthIt =
                b.publishedFrameDepthKeys.int2LongEntrySet().fastIterator();
        while (depthIt.hasNext()) {
            it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry = depthIt.next();
            int name = entry.getIntKey();
            if (b.publishedTextures.identityForDepthTexture(name) != identity) continue;
            b.celtium$registry().unpublishExternalImage(name);
            b.publishedTextures.removePublished(name);
            b.publishedFrameImages.remove(name);
            depthIt.remove();
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
     * Name-to-image resolution cached for the transition loop, rebuilt only when the publication set or a chain pair
     * changes. The loop runs once per window (about fifty a frame on a world scene), and it used to prune and rebuild
     * its map views on every one of them.
     */
    private long publishedCacheVersion = Long.MIN_VALUE;
    private long publishedCachePairs = Long.MIN_VALUE;

    /**
     * Item 673's lab switch: {@code -Dceltium.vulkan.terrainPreparePublishWalkOnce=true} lets a sampling-side
     * walk be skipped when it is provably a repeat of the walk that already ran for this frame.
     * <p>
     * The terrain prepare runs the sampling walk once per terrain pass (three or more passes a frame), and
     * each pass re-walks every published name only to find every image already in the layout the walk wants
     * (the settled fast path in {@code VulkanGlTexture}). The repeat is redundant exactly while nothing has
     * moved since the walk that settled them: the key below therefore carries the frame, the publication
     * version, the pair generation, the attach context (the images the pass attaches, which the walk skips)
     * and {@link VulkanGlTexture#foreignLayoutTransitionCount()} - the count of foreign layout barriers
     * actually recorded anywhere, which is what a hand-back between the two walks would advance. An
     * attach-side (general) walk always invalidates the memo, and a walk over an explicit attach-image array
     * (the multi-target window shape) neither uses nor fills it. Off by default: with the switch off the
     * walk runs exactly as before, every pass, every name.
     */
    private static final boolean PUBLISH_WALK_ONCE =
            Boolean.getBoolean("celtium.vulkan.terrainPreparePublishWalkOnce");
    private long samplingWalkFrame = Long.MIN_VALUE;
    private long samplingWalkPublications = Long.MIN_VALUE;
    private long samplingWalkPairs = Long.MIN_VALUE;
    private long samplingWalkTransitions = Long.MIN_VALUE;
    private long samplingWalkAttachColor;
    private long samplingWalkAttachDepth;
    private long samplingWalkPassColor;
    private long samplingWalkPassDepth;
    /** How many sampling walks the memo answered instead of walking, with the switch on. */
    private long samplingWalkSkips;

    /** {@return how many sampling-side walks the item-673 memo skipped}, for a leg's census line. */
    long celtium$samplingWalkSkips() {
        return this.samplingWalkSkips;
    }

    /**
     * {@return whether a sampling-side walk with this key is a repeat of the walk the memo holds}.
     * Pure, so the key's shape is pinned without a device: every component must match, and the switch,
     * the attach-side direction and the array-attach shape each refuse on their own.
     */
    static boolean celtium$samplingWalkRedundant(boolean enabled, boolean general, boolean hasAttachImages,
            long frame, long memoFrame, long publications, long memoPublications,
            long pairs, long memoPairs, long transitions, long memoTransitions,
            long attachColor, long memoAttachColor, long attachDepth, long memoAttachDepth,
            long passColor, long memoPassColor, long passDepth, long memoPassDepth) {
        return enabled && !general && !hasAttachImages
                && memoFrame != Long.MIN_VALUE
                && frame == memoFrame && publications == memoPublications && pairs == memoPairs
                && transitions == memoTransitions
                && attachColor == memoAttachColor && attachDepth == memoAttachDepth
                && passColor == memoPassColor && passDepth == memoPassDepth;
    }
    private int[] cachedNames = new int[0];
    private VulkanGlTexture[] cachedWrappers = new VulkanGlTexture[0];
    private long[] cachedImages = new long[0];
    /**
     * The chain-pair identity each cached name's image stands for, resolved once per cache rebuild.
     * <p>
     * The transition loop used to ask {@code chainPairIdentityForImage} for every name on every window - a hash lookup
     * per name per window, and the loop runs once per window (35.8 a frame on the measured world scene). The answer
     * only changes when the pair generation changes, which is already part of the cache key, so it is resolved here
     * with the rest of the cache instead of 35.8 times a frame.
     */
    private long[] cachedPairIdentities = new long[0];

    /**
     * The chain-pair colour slot each cached name's image fills, resolved with the rest of the cache.
     * <p>
     * The walk used to re-resolve this per name per pass through
     * {@code chainPairColorSlotForImage} - a pair lookup plus a slot scan - although the answer only
     * changes when the pair generation changes, which is already part of the cache key (the same
     * argument as {@link #cachedPairIdentities}). -1 for a name that fills no slot.
     */
    private int[] cachedPairSlots = new int[0];

    /**
     * The shared depth store key each cached name stands for, 0 for a colour name, resolved with the rest of the
     * cache: the walk reports a depth wrapper's layout moves into the store under this key, and resolving it per
     * window would be the same per-name lookup the pair-identity cache exists to avoid.
     */
    private long[] cachedDepthKeys = new long[0];

    /**
     * The pair generation each identity's stale-name scan last completed for, and the publication version it saw.
     * <p>
     * The scan re-points names whose image died with a recreated pair, so it can only find one when that identity's
     * pair was recreated or the publication set moved. The key used to be the identity and the pair's colour image,
     * which is the same thing only while one identity is bound: the measured world scene binds thirteen of
     * GregTech's post-chain framebuffers a frame, so the key missed on every window and the scan walked all published
     * names (38) with two boxed-key lookups each, thirteen times a frame. That is the whole of {@code winHead}'s
     * 0.1 ms a frame, and it is a scan that could not have found anything - no pair was recreated between those
     * windows. The generation is the counter that moves when one is, and it is per identity, so a window for one
     * framebuffer cannot consume the scan another framebuffer still needs.
     */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap syncScanGenerations =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long syncScanVersion = Long.MIN_VALUE;
    /** The frame target's pair-generation total when the memo was last used, so a new target clears it. */
    private long syncScanTotal = Long.MIN_VALUE;
    /**
     * How many times the stale-name scan actually walked the published set, and how many windows the memo answered
     * instead. The pair is what makes "the memo hits" a measurement rather than a reading of the code: the scan is
     * the only per-window work in the off-target routing that is proportional to the published-name count.
     */
    private long syncScanRuns;
    private long syncScanSkips;

    /**
     * Names the attach-side hand-back skipped because they stand for an image this pass does not attach.
     * <p>
     * The narrowing's own measurement: the walk is proportional to the published-name count and runs once per
     * off-target window, so the count of names it no longer visits is the size of the change.
     */
    private long attachNarrowedSkips;

    /** {@return the attach-side hand-back's skipped names}, for the window split's line. */
    long celtium$attachNarrowedSkips() {
        return this.attachNarrowedSkips;
    }

    /**
     * Whether the published-name walk has to visit one name.
     * <p>
     * The sampling side always does: it is the direction that has to cover every name a draw in the pass might sample,
     * and that set is not known at the walk. The attach side only has to visit the names whose image the pass is about
     * to attach - the hand-back exists to satisfy {@code VUID-vkCmdBeginRenderPass-initialLayout-00900} for that one
     * image - so a name over another image is skipped. {@code attachImage == 0} means the caller did not name an
     * image, and the walk keeps its old, unconditional behaviour.
     * <p>
     * Static and package-private so the rule is testable without a device: the walk itself needs a GPU, but "which
     * names may be skipped" is arithmetic, and a green test here is not evidence that a transition happened.
     *
     * @param general     {@code true} for the attach-side hand-back
     * @param attachImage the image the pass attaches, or 0 when the caller did not narrow
     * @param publishedImage the image the published name stands for, 0 when it has none
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long attachImage, long publishedImage) {
        return !general || attachImage == 0L || publishedImage == attachImage;
    }

    /**
     * The same rule for a pass that attaches several images (a multi-target window's whole tuple): the name is
     * visited when any attached image is the one it stands for. A null array is the unnarrowed walk, as
     * {@code attachImage == 0} is above.
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long[] attachImages, long publishedImage) {
        if (!general || attachImages == null) {
            return true;
        }
        for (long attachImage : attachImages) {
            if (celtium$attachHandBackNeeded(true, attachImage, publishedImage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zero-allocation attach hand-back predicate covering a pass's color target(s) AND its borrowed shared depth
     * target (shadow §3.4). When {@code general == false} (sampling side) or all attach arguments are unset
     * (unnarrowed walk), returns {@code true}; otherwise returns {@code true} iff {@code publishedImage} matches
     * {@code attachColor}, {@code attachDepth}, or any element of {@code attachColors}.
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long attachColor, long attachDepth,
            long[] attachColors, long publishedImage) {
        if (!general) {
            return true;
        }
        if (attachColor == 0L && attachDepth == 0L && attachColors == null) {
            return true;
        }
        if (attachColor != 0L && publishedImage == attachColor) {
            return true;
        }
        if (attachDepth != 0L && publishedImage == attachDepth) {
            return true;
        }
        if (attachColors != null) {
            for (long candidate : attachColors) {
                if (candidate != 0L && candidate == publishedImage) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@return the layout the published-name walk moves one name's image to, and the layout it reports to the
     * image's owner afterwards}.
     * <p>
     * One decision for both the transition and the report, so the two cannot disagree: the walk records a barrier
     * to this value and then reports this value (read back as the wrapper's recorded destination). The sampling
     * side ({@code general == false}) is {@code SHADER_READ_ONLY_OPTIMAL} for every published image - the layout
     * the sampling descriptors declare. The attach side differs by kind: a colour image's owner attaches it in
     * {@code GENERAL}, the layout its pass declares, but a depth image's owner attaches it in its own layout
     * ({@code ownerLayout}, normally {@code DEPTH_STENCIL_ATTACHMENT_OPTIMAL}) - {@code GENERAL} is a value the
     * depth owner's records never use, and handing a depth image back there left the owner's store record naming
     * a layout the next draw's descriptor did not declare (item 537 wall (c): the two
     * {@code VUID-vkCmdDraw-None-09600} on the published depth image).
     * <p>
     * Static and package-private so the rule is testable without a device, like
     * {@link #celtium$attachHandBackNeeded}: a green test here pins the decision table, not a recorded barrier.
     *
     * @param depth       {@code true} when the published name stands for a depth image (it carries a depth key)
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling side
     * @param ownerLayout the layout the depth image's owner attaches it in; ignored for colour names and on the
     *                    sampling side
     */
    static int celtium$publishedHandBackLayout(boolean depth, boolean general, int ownerLayout) {
        if (!general) {
            return org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        return depth ? ownerLayout : org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
    }

    /** {@return the stale-name scan's runs and memo hits, as {@code runs/skips}}. */
    String celtium$syncScanSummary() {
        return this.syncScanRuns + "/" + this.syncScanSkips;
    }

    private void celtium$rebuildPublishedCache() {
        int size = b.publishedTextures.publishedCount();
        GllessFrameTarget pairOwner = b.offTargetTarget();
        this.cachedNames = new int[size];
        this.cachedWrappers = new VulkanGlTexture[size];
        this.cachedImages = new long[size];
        this.cachedPairIdentities = new long[size];
        this.cachedPairSlots = new int[size];
        this.cachedDepthKeys = new long[size];
        for (int i = 0; i < size; i++) {
            int name = b.publishedTextures.publishedKeyAt(i);
            VulkanGlTexture wrapper = b.publishedTextures.publishedValueAt(i);
            long image = b.publishedFrameImages.get(name);
            this.cachedNames[i] = name;
            this.cachedWrappers[i] = wrapper;
            this.cachedImages[i] = image;
            this.cachedPairIdentities[i] = pairOwner == null || image == 0L
                    ? Long.MIN_VALUE : pairOwner.chainPairIdentityForImage(image);
            this.cachedPairSlots[i] = pairOwner == null || this.cachedPairIdentities[i] == Long.MIN_VALUE
                    ? -1 : pairOwner.chainPairColorSlotForImage(this.cachedPairIdentities[i], image);
            this.cachedDepthKeys[i] = b.publishedFrameDepthKeys.get(name);
        }
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
        celtium$transitionPublishedFrameTextures(general, 0L);
    }

    /**
     * Records a layout move in the pair that owns the image, at the slot the cache resolved for it:
     * slot 0's record is the pair's own, a tuple slot's record is what that slot's acquire barrier
     * reads as its source layout.
     *
     * @return whether the pair's record changed - a settled name (the record already names this
     *         layout) writes nothing, and its caller skips the layout report for it too
     */
    private boolean notePairColorLayout(GllessFrameTarget pairOwner, long identity, int slot, int layout) {
        return pairOwner.noteChainPairColorLayoutAtSlotIfChanged(identity, slot, layout);
    }

    /**
     * The same walk, narrowed on the attach side to the names that stand for the image the pass is about to attach.
     * <p>
     * The hand-back exists for one reason: the pass declares {@code GENERAL} for attachment 0, so every published name
     * whose image that pass attaches has to be moved back before {@code vkCmdBeginRenderPass}
     * ({@code VUID-vkCmdBeginRenderPass-initialLayout-00900}). A name published over a <em>different</em> image is not
     * attached by this pass and is not moved by it, so handing it back is a transition nothing asked for - and the walk
     * that records it runs once per off-target window, over every published name (38 at the end of the measured run).
     * <p>
     * The sampling side ({@code general == false}) is unchanged: it is the direction that has to cover every name a
     * draw in the pass might sample, and that set is not known here.
     *
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling-side transition
     * @param attachImage the image the pass is about to attach, or 0 to hand back every published name as before
     */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachImage) {
        celtium$transitionPublishedFrameTextures(general, attachImage, 0L, null);
    }

    /** Zero-allocation overload for a single-color pass that may also attach a borrowed shared depth image. */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth) {
        celtium$transitionPublishedFrameTextures(general, attachColor, attachDepth, null);
    }

    /**
     * The same walk for a pass that attaches several images at once (a multi-target window's tuple): the
     * hand-back covers every attached slot's names, and the sampling side skips every attached slot's names.
     *
     * @param attachImages the images the pass is about to attach, or null to hand back every published name
     */
    void celtium$transitionPublishedFrameTextures(boolean general, long[] attachImages) {
        celtium$transitionPublishedFrameTextures(general, 0L, 0L, attachImages);
    }

    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth,
            long[] attachImages) {
        if (b.publishedTextures.isEmptyPublished() || b.commandBuffer == null) {
            return;
        }
        long publications = b.publishedTextures.publishedVersion();
        GllessFrameTarget cachePairsOwner = b.offTargetTarget();
        long pairs = cachePairsOwner == null ? -1L : cachePairsOwner.chainPairGenerationTotal();
        if (general) {
            // A hand-back moves images toward the attach layouts: whatever the sampling memo settled no
            // longer holds, whatever this walk itself does.
            this.samplingWalkFrame = Long.MIN_VALUE;
        } else if (celtium$samplingWalkRedundant(PUBLISH_WALK_ONCE, false, attachImages != null,
                b.frameId, this.samplingWalkFrame, publications, this.samplingWalkPublications,
                pairs, this.samplingWalkPairs,
                VulkanGlTexture.foreignLayoutTransitionCount(), this.samplingWalkTransitions,
                attachColor, this.samplingWalkAttachColor, attachDepth, this.samplingWalkAttachDepth,
                b.passAttachedColorImage, this.samplingWalkPassColor,
                b.passAttachedDepthImage, this.samplingWalkPassDepth)) {
            this.samplingWalkSkips++;
            return;
        }
        if (publications != this.publishedCacheVersion || pairs != this.publishedCachePairs) {
            // The prune re-publishes a name whose pair image was recreated; it is the reason a stale resolution cannot
            // simply be cached forever, and the generation is part of the key for exactly that case.
            celtium$pruneStalePublishedNames();
            this.publishedCacheVersion = b.publishedTextures.publishedVersion();
            this.publishedCachePairs = pairs;
            celtium$rebuildPublishedCache();
            if (b.publishedTextures.isEmptyPublished()) {
                return;
            }
        }
        GllessFrameTarget pairOwner = b.offTargetTarget();
        for (int cachedIndex = 0; cachedIndex < this.cachedNames.length; cachedIndex++) {
            int entryKey = this.cachedNames[cachedIndex];
            VulkanGlTexture entryValue = this.cachedWrappers[cachedIndex];
            long publishedImageHandleValue = this.cachedImages[cachedIndex];
            long publishedImageIdentity = this.cachedPairIdentities[cachedIndex];
            boolean pairImageName = publishedImageIdentity != Long.MIN_VALUE;
            if (!celtium$attachHandBackNeeded(general, attachColor, attachDepth, attachImages,
                    publishedImageHandleValue)) {
                // The hand-back is the attach's own precondition: only the names that stand for the image this pass
                // attaches have to be in GENERAL / DEPTH_STENCIL_ATTACHMENT_OPTIMAL for it. A name over another
                // image is not attached here and is not moved here, so the transition would be one nothing asked for.
                this.attachNarrowedSkips++;
                continue;
            }
            // The sampling side must not move an image (color OR depth) that this pass is about to attach: the pass
            // declares GENERAL for color attachments and DEPTH_STENCIL_ATTACHMENT_OPTIMAL for depth attachments, so
            // transitioning it to SHADER_READ_ONLY_OPTIMAL right before vkCmdBeginRenderPass causes
            // VUID-vkCmdBeginRenderPass-initialLayout-00900.
            if (!general && (b.passAttachedColorImage != 0L || b.passAttachedDepthImage != 0L)) {
                if (publishedImageHandleValue != 0L
                        && b.celtium$passAttachesImage(publishedImageHandleValue)) {
                    b.publishedTextures.noteSkippedForAttach();
                    if (b.publishedTextures.skippedForAttach() == 1) {
                        b.record("published name " + entryKey + " stands for the image this pass attaches (0x"
                                + Long.toHexString(b.passAttachedColorImage) + "); the sampling-side transition"
                                + " would leave it in SHADER_READ_ONLY under a pass that declares GENERAL, so it is"
                                + " skipped and the attach-side hand-back keeps it in GENERAL");
                    }
                    if (b.pairSkipLogs < 8) {
                        // Whether the refusal leaves a draw without the layout its descriptor declares: the count is
                        // of groups that sampled a published chain-pair name, this frame and cumulatively.
                        b.pairSkipLogs++;
                        System.out.println("[Celtium] CELTIUM_PAIR_SKIP frame=" + b.frameId
                                + " name=" + entryKey + " attached=0x"
                                + Long.toHexString(b.passAttachedColorImage)
                                + " identity=0x" + Long.toHexString(b.targetRegistry.currentIdentity())
                                + " samplingDrawsThisRun=" + b.pairNameSamplingDraws
                                + " samplingDrawsLastFrame=" + b.pairNameSamplingDrawsFrame
                                + " (0 means nothing sampled this name before the skip)");
                    }
                    continue;
                }
            }
            // A depth publication is decided apart from the colour names (item 537 wall (c)): its owner's
            // vocabulary has no GENERAL in it, so the hand-back target comes from the same decision the report
            // below uses, with the owner's own attachment layout as the depth answer.
            long depthKey = this.cachedDepthKeys[cachedIndex];
            int pairSlot = this.cachedPairSlots[cachedIndex];
            // Whether this name's pair record actually moves below: a settled name writes nothing
            // and earns no layout report (the report's summary cannot have changed through it).
            boolean pairRecordChanged = false;
            if (general) {
                if (depthKey != 0L) {
                    entryValue.ensureHandBackLayout(b.commandBuffer, celtium$publishedHandBackLayout(true, true,
                            GllessFrameTarget.DEPTH_RENDER_LAYOUT));
                } else {
                    entryValue.ensureGeneralLayout(b.commandBuffer);
                }
            } else if (pairImageName) {
                // The pair is never moved out of the layout its pass declares, so its sampling transition is not
                // served. Counted, so "the transition was skipped" is a measurement; and the pair's own record follows,
                // because the image is still in GENERAL and a record left at SHADER_READ_ONLY would be stale.
                b.publishedTextures.noteSkippedForAttach();
                if (pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                    pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                            org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
                }
            } else {
                entryValue.ensureSampledLayout(b.commandBuffer);
            }
            // The pair's own record of the image's layout follows the wrapper's barrier, but only for the names that
            // stand for the pair image on the hand-back side: on the sampling side (!general), pairImageName already
            // skipped the transition and recorded GENERAL above, so writing SHADER_READ_ONLY_OPTIMAL here would
            // overwrite the true GENERAL layout with a stale sampling layout.
            if (general && pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                        celtium$publishedHandBackLayout(false, true, 0));
                if (pairRecordChanged) {
                    celtium$reportPairLayout("hand-back");
                }
            } else if (!general && pairOwner != null && publishedImageIdentity != Long.MIN_VALUE
                    && pairRecordChanged) {
                celtium$reportPairLayout("sampling");
            }
            // A depth publication's layout record is the shared depth store, not a pair, so the wrapper's move is
            // reported into it under the name's key: the owner's next attachment acquire reads the store for its
            // barrier's source layout, and the native depth copy reads it too. Without the report the store would
            // keep naming the attachment layout while the image sits in the read layout the sampling side left it
            // in, and the next acquire would name a source layout the image is not in. This is the report
            // SHADOW-NATIVE-DESIGN section 3.4 requires of the publication.
            // The reported value is the destination the wrapper's recording actually leaves the image in, read
            // back from the wrapper - not a value synthesised from `general`. The two agreed for colour, but for
            // depth the binary synthesis wrote GENERAL on the hand-back side: a layout the depth owner never
            // uses, over an image the hand-back above moved to the owner's attachment layout (item 537 wall (c)).
            if (depthKey != 0L && pairOwner != null) {
                // The destination is the value the transition above was told to reach, on both
                // deterministic routes: the hand-back names DEPTH_RENDER_LAYOUT, the sampling side
                // names the wrapper's sampling layout. Reading it back through foreignLayoutIn -
                // a synchronized lookup over two shared maps - per depth name per pass bought
                // nothing on those routes. The pair-name sampling route is the exception: it
                // records no transition, so the image stays wherever it was, and only the read-back
                // knows that value. The store write itself is skipped when it already names the
                // destination (a settled depth name).
                int depthDestination;
                if (general) {
                    depthDestination = GllessFrameTarget.DEPTH_RENDER_LAYOUT;
                } else if (pairImageName) {
                    depthDestination = entryValue.foreignLayoutIn(b.commandBuffer);
                } else {
                    depthDestination = entryValue.samplingLayout();
                }
                pairOwner.noteSharedDepthLayoutIfChanged(depthKey, depthDestination);
            }
        }
        if (!general && attachImages == null && PUBLISH_WALK_ONCE) {
            // The memo holds the state this walk just established, with the transition count read after
            // the walk: a name this walk itself had to move advances the count, and the next identical
            // walk must compare against the settled total, not the one from before this walk ran.
            this.samplingWalkFrame = b.frameId;
            this.samplingWalkPublications = publications;
            this.samplingWalkPairs = pairs;
            this.samplingWalkTransitions = VulkanGlTexture.foreignLayoutTransitionCount();
            this.samplingWalkAttachColor = attachColor;
            this.samplingWalkAttachDepth = attachDepth;
            this.samplingWalkPassColor = b.passAttachedColorImage;
            this.samplingWalkPassDepth = b.passAttachedDepthImage;
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
        // The budget first: the summary walks every pair and builds a string, and this runs at every pass that samples
        // a published name - with Xaero's Minimap that was 26% of pass recording (JFR, leg xprof-amd) long after the
        // bounded lines were spent.
        if (b.pairLayoutLogs >= b.PAIR_LAYOUT_LINES) {
            return;
        }
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
            // Only the owned target carries the chain pairs the published names stand for; without one there is
            // nothing here to judge.
            return;
        }
        for (int idx = b.publishedTextures.publishedCount() - 1; idx >= 0; idx--) {
            int name = b.publishedTextures.publishedKeyAt(idx);
            long image = b.publishedFrameImages.get(name);
            long owner = b.publishedFrameImageIdentities.get(name);
            long depthKey = b.publishedFrameDepthKeys.get(name);
            long live;
            if (depthKey != 0L) {
                // A depth publication stands for the shared depth store's image for its key, not a pair's colour
                // image: the store is its liveness record, and a recreated store entry re-points the name the
                // same way a recreated pair re-points a colour name.
                live = offTarget.sharedDepthImage(depthKey);
            } else if (owner != Long.MIN_VALUE) {
                // The name stands for the slot it was published over: a tuple slot's live image is not slot 0's
                // (comparing against slot 0 re-published colortex4/5 and shadowcolor1 over the pair's own image).
                live = offTarget.chainPairColorImageAt(owner, b.publishedFrameImageSlots.get(name));
            } else if (image != 0L && (image == b.frameTarget.colorImage()
                    || image == offTarget.offTargetColorImage())) {
                live = image;
            } else {
                live = 0L;
            }
            if (image != 0L && live != 0L && image == live) {
                continue;
            }
            if (live != 0L) {
                VulkanGlTexture republished;
                if (depthKey != 0L) {
                    long liveView = offTarget.sharedDepthView(depthKey);
                    int[] liveExtent = offTarget.sharedDepthExtent(depthKey);
                    int liveLayout = offTarget.sharedDepthLayout(depthKey);
                    republished = liveView == 0L || liveExtent == null
                            || liveLayout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED ? null
                                    : b.celtium$registry().publishExternalDepthImage(name, live, liveView,
                                            liveExtent[0], liveExtent[1], liveLayout);
                } else {
                    republished = b.celtium$registry().publishExternalImage(name, live,
                            offTarget.chainPairColorViewAt(owner, b.publishedFrameImageSlots.get(name)),
                            b.targetWidth, b.targetHeight);
                    // A republished wrapper is a fresh wrapper: without the flag it declares SHADER_READ_ONLY over an
                    // image the pair keeps in GENERAL (09600/00344 colour form, 00900 on the next attach).
                    if (republished != null) {
                        republished.notePairImage(true);
                    }
                }
                if (republished != null) {
                    b.publishedTextures.setPublishedValueAt(idx, republished);
                    b.publishedFrameImages.put(name, live);
                    b.publishedTextures.noteRepublished();
                    continue;
                }
            }
            b.celtium$registry().unpublishExternalImage(name);
            b.publishedFrameImages.remove(name);
            b.publishedFrameImageIdentities.remove(name);
            b.publishedFrameImageSlots.remove(name);
            b.publishedFrameDepthKeys.remove(name);
            b.publishedTextures.noteUnpublished();
            b.publishedTextures.noteStalePruned();
            if (b.publishedTextures.stalePruned() == 1) {
                b.record("published name " + name + " named image 0x"
                        + (image == 0L ? "unknown" : Long.toHexString(image))
                        + " which is no longer the pair's image (0x" + Long.toHexString(live)
                        + " for identity 0x" + Long.toHexString(owner)
                        + "); unpublished before any barrier could be recorded on it");
            }
            b.publishedTextures.removePublishedAt(idx);
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
            // The scan below walks every published name and asks two boxed-key lookups of each, and it runs on every
            // off-target window (13 a frame on the measured world scene) - which is where winHead's 0.1 ms a frame
            // comes from. What it looks for is a name whose image died with a recreated pair, so it can only find one
            // when that identity's pair was recreated or the publication set moved. Both are counters, and both are in
            // the key; when neither has moved the scan would find nothing again and is skipped. The key used to be the
            // identity and the pair's colour image, which misses on every window of a frame that binds thirteen
            // different framebuffers - the scan then ran thirteen times for a question whose answer could not change.
            long publications = b.publishedTextures.publishedVersion();
            GllessFrameTarget scanTarget = b.offTargetTarget();
            long generation = scanTarget == null ? -1L : scanTarget.chainPairGeneration(identity);
            long total = scanTarget == null ? -1L : scanTarget.chainPairGenerationTotal();
            if (total < this.syncScanTotal) {
                // A new frame target: its generations start over, so a memo from the previous one would say "already
                // scanned" for a pair that has just been created. The total is monotonic within one target.
                this.syncScanGenerations.clear();
            }
            this.syncScanTotal = total;
            if (this.syncScanGenerations.get(identity) == generation
                    && publications == this.syncScanVersion) {
                this.syncScanSkips++;
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
                return;
            }
            this.syncScanGenerations.put(identity, generation);
            this.syncScanVersion = publications;
            this.syncScanRuns++;
            GllessFrameTarget liveTarget = b.offTargetTarget();
            long liveImage = liveTarget == null ? 0L : liveTarget.chainPairColorImage(identity);
            long liveView = liveTarget == null ? 0L : liveTarget.chainPairColorView(identity);
            for (int idx = b.publishedTextures.publishedCount() - 1; idx >= 0; idx--) {
                int name = b.publishedTextures.publishedKeyAt(idx);
                long image = b.publishedFrameImages.get(name);
                long owner = b.publishedFrameImageIdentities.get(name);
                if (owner != identity) {
                    continue;
                }
                int nameSlot = b.publishedFrameImageSlots.get(name);
                long slotImage = nameSlot == 0 || liveTarget == null ? liveImage
                        : liveTarget.chainPairColorImageAt(identity, nameSlot);
                long slotView = nameSlot == 0 || liveTarget == null ? liveView
                        : liveTarget.chainPairColorViewAt(identity, nameSlot);
                if (image != 0L && image == (nameSlot == 0 ? chainPairColorImage : slotImage)) {
                    continue;
                }
                if (slotImage != 0L && slotView != 0L) {
                    VulkanGlTexture republished = b.celtium$registry().publishExternalImage(name,
                            slotImage, slotView, b.targetWidth, b.targetHeight);
                if (republished != null) {
                    republished.notePairImage(true);
                    b.publishedTextures.setPublishedValueAt(idx, republished);
                    b.publishedFrameImages.put(name, slotImage);
                    b.publishedTextures.noteRepublished();
                    continue;
                }
                }
                b.celtium$registry().unpublishExternalImage(name);
                b.publishedFrameImages.remove(name);
                b.publishedFrameImageIdentities.remove(name);
                b.publishedFrameImageSlots.remove(name);
                b.publishedTextures.noteUnpublished();
                b.publishedTextures.removePublishedAt(idx);
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
        if (b.copyReadSide.chainFrameDumps >= b.CHAIN_FRAME_DUMPS) {
            return;
        }
        b.copyReadSide.chainFrameDumps++;
        // Which chain this is: a run holds several, and the frame image is only expected to carry the output of the one
        // whose output target is `main`. The identity is the first pass's program, how many passes there are, whether
        // that first pass reads and the last one writes the frame image, and the framebuffer ids involved.
        String identity = "chain=" + b.copyReadSide.chainFrameDumps + " passes=" + passes.size()
                + " first=" + passes.get(0).program + " last=" + passes.get(passes.size() - 1).program
                + " inIsMain=" + passes.get(0).inIsMain + " outIsMain=" + passes.get(passes.size() - 1).outIsMain
                + " outFbId=" + passes.get(passes.size() - 1).outFbId
                + " frameId=" + b.frameId;
        if (!b.waitForIdle()) {
            b.record("chain frame dump skipped: the GPU did not confirm the chain submission");
            return;
        }
        String base = CHAIN_FRAME_DUMP_PATH;
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
        if (CHAIN_DUMP_PASSES && b.postChain != null) {
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

    /** Writes every chain pair at its own extent; see {@code VulkanTerrainGpuBackend.DUMP_PAIRS_AT_PRESENT}. */
    void dumpChainPairs(String stage) {
        GllessFrameTarget glless = b.offTargetTarget();
        if (glless == null) {
            return;
        }
        for (Long chainId : new java.util.ArrayList<Long>(glless.chainPairIdentities())) {
            long image = glless.chainPairColorImage(chainId.longValue());
            long[] extent = glless.chainPairExtent(chainId.longValue());
            if (image == 0L || extent == null) {
                continue;
            }
            b.celtium$dumpOwnedImage("pair-0x" + Long.toHexString(chainId.longValue()), image,
                    "celtium-" + stage + ".png", "identity=0x" + Long.toHexString(chainId.longValue())
                            + " extent=" + extent[0] + "x" + extent[1], (int) extent[0], (int) extent[1]);
        }
    }
}
