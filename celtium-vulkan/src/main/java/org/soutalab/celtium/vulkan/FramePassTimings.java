package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.harness.HarnessConfig;

import java.util.Arrays;

/**
 * Attributes the frame's own time to the passes inside it, so the profiler's unattributed
 * {@code gameRenderer} residual has a name.
 * <p>
 * Why this exists: the user's lag-spike capture (perf-ring's decode, {@code docs/plan.md} §0.227) shows 358 frames
 * over 20 s with a mean of 55.9 ms and spikes whose {@code gameRenderer} self time is 318.6 ms against 25.0 ms in
 * median frames - and the profiler's named children explain only about 72 ms of it. Roughly 246 ms per spike frame
 * belongs to no section at all, and {@code frameFenceWaitMillis} is 0.14 ms per wait, orders of magnitude too small.
 * The profiler cannot say which pass holds it; this can, because it brackets the passes with wall-clock time of our
 * own rather than reading the profiler's tree.
 * <p>
 * Pass times are inclusive. Minecraft calls hand/clouds/weather inside renderWorld, so summing those times
 * double-counts their overlap. The residual subtracts the <b>union</b> of world/hand/clouds/weather/gui/screen brackets,
 * not their sum. Auxiliary brackets are reported for attribution and do not change that coverage. This is deliberately
 * not comparable with the profiler's self-time sum, which is 4.01x the frame time because {@code frame}, {@code root}
 * and {@code gameRenderer} each hold all of it; the two must never be added together.
 * <p>
 * Off unless the harness asks for it ({@code passSplit=true} in the harness file, or the legacy
 * {@code -Dceltium.vulkan.passSplit=true} spelling with the master switch on), the same rule the other
 * repeating instruments follow: every switch in this class resolves through {@link HarnessConfig}.
 */
public final class FramePassTimings {

    /** Named inclusive passes whose union is subtracted from the frame. */
    private static final String[] TOP_LEVEL = {"world", "hand", "clouds", "weather", "gui", "screen"};
    /**
     * Measured inside {@code world}; reported, never subtracted.
     * <p>
     * {@code present} and {@code flush} are ours rather than the game's: the lag-spike frame's {@code world} bracket
     * holds the whole spike (fixture: 367.9 ms of a 411.5 ms frame) and the profiler cannot say which part of that
     * pass is the renderer's own present/composite/window-flush chain against the game's renderWorldPass work. These
     * two brackets name it.
     * <p>
     * {@code recordPrepare} and {@code recordBatch} nest inside {@code terrainRecord}: a 779 ms record stall on Intel
     * (leg {@code loading-resize-state2-intel}, worldTick 473, 25 sections) did not scale with section count, so the
     * pass's fixed prepare work (uniform capacity, descriptors, barriers, framebuffer, render-pass begin) and its
     * per-region cached-batch rebuilds needed separate names before a cause could be claimed. The prepare split adds
     * {@code prepDescriptors} (uniform capacity + descriptor writes through {@code bindTextures}),
     * {@code prepBarriers} (acquire barriers + published-texture transitions) and {@code prepBegin} (render-pass
     * begin through pipeline bind) - recordPrepare ran ~70-85% of record on both GPUs, and these three names say
     * which of its driver-visible calls owns that.
     * <p>
     * {@code particlesLit} is the lit particle loop ({@code ParticleManager.renderLitParticles}, layer 3), bracketed
     * separately from {@code particles} (the normal three layers): the lit loop was previously outside every bracket,
     * so a "particles" number silently excluded the explosion/footstep/pickup layer. The two together - never either
     * alone - are the whole particle submission cost.
     */
    private static final String[] INSIDE = {"lightmap", "mouseOver", "worldPass", "entities", "particles", "particlesLit", "present", "flush", "terrain", "chunkUpload",
            // The entity pass' four parts, because `entities` is 54-56 % of the p99 frame's excess and the bracket
            // cannot say which part owns it: the membership gather (tick-gated: the chunk walk runs once per tick),
            // the per-entity cull (every gathered entity, every pass), the draw itself (the model replays), and the
            // block entities the second injection renders inside the same bracket. `entGatherWalk` is the walk alone,
            // so `entGather - entGatherWalk` is the per-pass predicate pass over the gathered membership.
            "entGather", "entGatherWalk", "entCull", "entDraw", "entBlockEntities", "entSetTile",
            "setupTerrain", "stGraph", "stEvents", "stAsync", "stPrepare", "stUpload", "stUpdate", "stVisible", "stReclaim", "sky", "terrainHandoff", "terrainRecord", "terrainSubmit", "recordPrepare",
            "recordBatch", "prepDescriptors", "prepBarriers", "prepBegin",
            // Item 673: recordPrepare's three brackets at their real seams, so the next census can name which
            // driver-visible call owns the fixed cost instead of re-measuring the aggregate. prepUniformCap is
            // the capacity reservations and prepDescBind the descriptor write inside prepDescriptors;
            // prepAcquire is the frame target's acquire barriers, prepRoute the framebuffer lookup plus the
            // depth/bound-target routing decision, and prepPublish the published-texture sampling walk inside
            // prepBarriers; prepBeginPass is vkCmdBeginRenderPass plus its bookkeeping and prepViewportBind
            // the viewport/scissor + pipeline bind + push constants inside prepBegin. The pre-terrain flush
            // keeps its own terrainPreflush name inside prepBegin, so prepBegin minus its children is the
            // flush plus the calloc/timestamp residue. All seven are inclusive INSIDE names: reported, never
            // subtracted, so the double counting that bit the kpi-8 v1 split cannot recur through them.
            "prepUniformCap", "prepDescBind", "prepAcquire", "prepRoute", "prepPublish", "prepBeginPass",
            "prepViewportBind",
            "recBind", "recDraw", "recLoop",
            // Item 691 (flight-census-leg-2 section 4, R1): the terrainRecord residual - the named rec*
            // children summed to 748/996 us of a 1977/2577 us parent, leaving 1.2-1.6 ms a frame in no
            // bracket at all. These five names cover every span of recordRenderPass the older names do
            // not, at their real seams: recHead is the pass head before recordPrepare (budget window
            // roll, routed-state init, the pack-route attempt - in a pack frame that attempt IS the
            // record, in a vanilla frame it is the refusal check); recPassSetup is recordPrepare's end
            // to the region loop (statistics query, iterator and cache key, stack buffers, pass fog,
            // the pass-level recBind nested inside it, shared vertex bind, chain begin); recIter is the
            // per-region iterator advance, which sat outside recLoop; recCharge is the per-region span
            // between recLoop and recBind (batch cast, the budget's draw charge and drop decision,
            // statistics charge, readback slot); recPassTail is the loop's end to the method's end
            // (chain close, statistics end, the immediate tail and UI quads inside the pass, render-pass
            // end, timestamp, readback copies, routed hand-back, release barriers). All five are
            // inclusive INSIDE names: reported, never subtracted, and none joins TOP_LEVEL,
            // FrameBudgetTimings' OTHER_BUCKETS or its WORLD_PASS_MEMBERS - they sit inside the terrain
            // bucket through terrainRecord, so the other-split needs no new member for them.
            "recHead", "recPassSetup", "recIter", "recCharge", "recPassTail",
            "terrainTarget", "terrainSetup", "terrainClose", "terrainPrep", "terrainCapacity", "terrainSharedIndex", "terrainPreflush", "flushCloseGroup",
            "winBarrier", "winRecord", "winSubmit", "winEnd", "winBeginPass", "winTransition",
            // The terrain pass' three unmeasured spans: the fixed prepare work before recording (frame target,
            // pipeline lookup, shared index buffer, uniform capacity, atlas/lightmap mirrors), the command-buffer
            // begin, and the frame end after the submission. The pass' bracket (3.5 ms p50 against a 0.9 ms record
            // and 0.2 ms submit) leaves that gap unnamed, and the three candidates have different owners.
            "terrainEnsure", "terrainRefresh", "terrainLightmap", "dynUpload", "dynAnnounce", "dynFlush", "dynMirror", "terrainBegin", "terrainEnd",
            // The immediate path's two untimed spans: closing the batch still being filled (per group, and the
            // group's payload capture is the expensive half) and the window's own setup before it records
            // (recording snapshot, frame begin, target/device gates). They were the residue of the batch call's
            // measured parts (batchCallMillis 3,153 ms against ~1,450 ms of named sub-timers).
            "closeGroup", "windowSetup",
            // The flush's per-group halves: everything the group's own draw needs prepared (descriptor resolution,
            // push-block fill, viewport and bias state) against the pipeline call that binds and draws it.
            "grpPrep", "grpDraw",
            // The per-group body of the immediate flush, in four pieces: the state/descriptor resolution, the
            // push-block fill (matrix, fog, colour, light map), the viewport/scissor/depth-bias state, and the
            // pipeline's bind+draw call. ~2.5 us a group at 332 groups a frame is the largest renderer-owned item
            // left, and the four names say which piece it is.
            "rcResolve", "rcPush", "rcViewport",
            // The offer path's four pieces: the GL state capture and its key, the pipeline/descriptor preparation,
            // the group-match decision, and the vertex append (the transform for a pre-transformed group, a memcpy
            // otherwise). 0.71 ms a frame for ~292 batches is 2.4 us each, and the names say which part owns it.
            "offCapture", "offPrepare", "offMerge", "offAppend",
            // The flush's non-recording halves: the frame target's begin (semaphores and slot state), and the region
            // between the begin and the acquire barriers - target resolution, group filtering, the queued clear and
            // the diagnostics. actualFlush minus winRecord left more than half of the flush unaccounted for, and these
            // two names say which side of the pass it is on.
            "wfpBegin", "wfpMiddle",
            // The immediate window's group loop on its own: the window's recording is 0.95 ms a frame and the loop's
            // per-group work is the part the driver-call probes could not explain (skipping the pushes and the draws
            // changed neither the loop's own timer nor the frame).
            "wrecLoop", "rcPrepared",
            // The window's two unnamed halves (leg r36win): the prefix before vkCmdBeginRenderPass - the target
            // resolution, the sampling-side transitions and the begin-info - and the pass close after the group loop.
            // winRecord is 0.5-0.7 ms a frame over 35.8 windows, and winBeginPass plus wrecLoop accounted for only
            // part of it; these two names say which side the rest is on.
            "winPrefix", "winPassEnd",
            // The two halves of what winPrefix and winPassEnd still left unnamed: winPublish is the sampling-side
            // transition loop (every published name, every window - the per-name work is hash lookups under
            // VulkanGlTexture's monitor), and winHead is everything recordImmediateOnlyPass does before winPrefix
            // (the target routing, the pair clear and the begin-info). One of the two owns the remainder.
            "winPublish", "winHead",
            // The slow path's own section: the mirror supply and the pipeline/descriptor preparation, between the merge
            // decision and the group open. It sat inside `batch` and inside no named part of it, so the JFR profile's
            // 19.0% `submitGeometryBatch` had its other half (beside the window flush) unattributed.
            "offPreflight",
            // The HUD's share of the two renderer paths, measured only while the `gui` bracket is open: `guiBatch` is
            // the accept path (which contains `guiFlush`) and `guiFlush` is the window recording it triggers. The
            // `gui` bracket is 0.4 ms of the frame, and without these two names "the HUD costs 0.4 ms" cannot be split
            // into the game's own HUD work and ours - measured, ours is 0.1 of it.
            "guiBatch", "guiFlush",
            // The screen's share of the same two paths, measured while the `screen` bracket is open:
            // an open inventory or container screen is the HUD-heavy case (a screenful of item icons,
            // tooltips and status effects), and before this bracket existed its batches and flushes
            // were in no HUD population at all - `GuiScreen.drawScreen` is not the overlay's
            // renderGameOverlay, so `guiBatch`/`guiFlush` never saw them.
            "screenBatch", "screenFlush",
            // The client tick's own names. The tick runs between frames, so these are recorded by TickTimings (the
            // frame brackets return early while no frame is open); they live in this table so one name has one index
            // and the tick report can print them beside the frame's. `anim` is TextureMap.updateAnimations - the whole
            // animated-sprite pass, which is where the tick's texture work happens - and `animUpload` is one sprite's
            // upload inside it (the announcement, the stub's capture and the game's own copy). `animMirror`,
            // `animFlush` and `animStub` split that upload into the three things Celtium adds to it, and
            // `animBatchEnd` is the patch batch's submission. `lightCache` is Celtium's per-tick light lookup cache,
            // the other place Celtium's own code runs inside Minecraft.runTick.
            "anim", "animUpload", "animMirror", "animFlush", "animStub", "animBatchEnd", "lightCache",
            // The animation batch's own halves, because `animBatchEnd` is 68 us of the tick's 203 us animation pass
            // over three atlases and the name alone cannot say whether the recording, the command buffer's begin or
            // its end owns it. `animBatchFlush` is the deferred regions' recording, `animBatchBegin` the command
            // buffer's acquisition inside it, `animBatchCmd` the end-and-file call, and `animInvalidate` the
            // per-sprite invalidation the upload hook issues beside the announcement.
            "animBatchFlush", "animBatchBegin", "animBatchCmd", "animInvalidate",
            // Item 407: the client tick's second split (TickSplit2). The tickTop2 names wrap disjoint call sites
            // inside Minecraft.runTick and their union is what the per-tick residual subtracts; the tickWorld /
            // tickLvlRenderer children (tickEnt, tickTileEnt, tickParticles, tickChunks, tickDrain) are reported,
            // never subtracted - the same rule as the other INSIDE names. Present/display are deliberately absent:
            // they live in runGameLoop, outside the tick, and GameLoopTiming already names them there. tickSched
            // and tickFixture are named but deliberately unhooked (see TickTimings): the scheduled-executables
            // drain runs in runGameLoop, not inside the tick, and the validation client's tick hook may only
            // reach this table reflectively, so both stay at calls=0 as the "not separated" signal.
            "tickSched", "tickNet", "tickInput", "tickGameMode", "tickSound", "tickGuiTick", "tickFml",
            "tickWorld", "tickLvlRenderer", "tickFixture",
            "tickEnt", "tickTileEnt", "tickParticles", "tickChunks", "tickDrain",
            // pack-tail-2: the pack route's two terrain-bearing stages, bracketed inside
            // celtium$tryRenderPackTerrainPass once the execution is committed (a pack program is bound and
            // compiled). terrainRecord sums every terrain record of the frame, and in a pack frame that sum
            // mixes the shadow stage's record with the gbuffers stages' record; these two names separate them
            // by Iris's own stage state (ShadowRenderingState). The deferred/composite stages never enter
            // terrainRecord - their draws are immediate groups, recorded under flush/winRecord - so there is
            // deliberately no third name.
            "packRecordShadow", "packRecordGbuffers",
            // shadow-diff-1: the pack route's two priced spans inside the stage brackets above.
            // packRecordRebuild is BatchAssembler.createCachedBatch - the re-assembly a chunk-supply
            // frame correlates across the whole shadow volume, and the span the rebuild budget defers.
            // packRecordUniform is the per-region uniform span (region-offset write, descriptor-set
            // claim, uniform block pack): the steady per-region cost whose measured share decides the
            // incremental-pack design. Both are inclusive spans inside the stage brackets.
            "packRecordRebuild", "packRecordUniform",
            // pack-tail-2: the world pass' unnamed remainder (FrameBudgetTimings' worldRest), split at its
            // real seams. wpCamera is setupCameraTransform - the camera/frustum setup, with the renderer's
            // world snapshot seeded at its return; wpChunks is RenderGlobal.updateChunks, the chunk-update
            // work renderWorldPass runs between setupTerrain and the terrain layers; wpRenderLast is Forge's
            // RenderWorldLast dispatch, where mod handlers run inside the pass. All three join
            // FrameBudgetTimings' WORLD_PASS_MEMBERS, so what they measure leaves worldRest instead of
            // sitting inside it unnamed.
            "wpCamera", "wpChunks", "wpRenderLast",
            // flight-census R3: the cloudsWeather bucket's inside, which was a leaf. All four are inclusive
            // and report-only - none joins TOP_LEVEL, FrameBudgetTimings' OTHER_BUCKETS or its
            // WORLD_PASS_MEMBERS, so nothing here is subtracted anywhere (the kpi-8 v1 double-count rule:
            // containment is read offline by subtracting the inner names from the outer ones). cloudForge is
            // Forge CloudRenderer.render whole (DynamicTexture update, texture matrix, state setup - the
            // per-frame fixed part - around the replaced draws); cloudAccept is one cell-VBO drawArrays
            // call, i.e. the retained acceptance (submitRetainedGeometry) of one of the two cell passes;
            // cloudBuild is celtium$build whole (the burst-side mesh rebuild); cloudUpload is the build's
            // VBO re-upload inside it, read as cloudBuild's inner number.
            "cloudForge", "cloudAccept", "cloudBuild", "cloudUpload",
            // Item 648 (the pack-tail-2 section 5.2 follow-up): the last two vanilla call sites inside
            // renderWorldPass that still ran unnamed - drawBlockDamageTexture (the destroy-progress tail,
            // after the outline and debug sections) and renderWorldBorder (right after the weather).
            // Both are disjoint vanilla sub-spans like the wp* seams above, so they join
            // FrameBudgetTimings' WORLD_PASS_MEMBERS and what they measure leaves worldRest.
            "wpDestroyProgress", "wpWorldBorder",
            // Item 648: the pack side of the pass, bracketed inside Demonica/Iris itself. wpPackPrepare is
            // PipelineManager.preparePipeline and wpPackBegin is the deferred pipeline's
            // beginLevelRendering - the frame-begin phase Demonica's own EntityRenderer handler runs at
            // the head of the pass, before any vanilla bracket opens. wpShadowPhase is
            // ShadowRenderer.renderShadows whole: the shadow stage's setup (frustum, matrices, target
            // preparation) plus everything it renders. These three are inclusive containers - the shadow
            // phase holds packRecordShadow and the terrain span inside it - so they stay out of
            // WORLD_PASS_MEMBERS on purpose: subtracting them would subtract their contents twice. The
            // census reads them off the per-frame profiles and nets the contained names out by hand, the
            // same offline reconstruction pack-tail-2 used for its terrainRecord split.
            "wpPackPrepare", "wpPackBegin", "wpShadowPhase",
            // Item 690 (the item 648 section 7 follow-up): the inside of the two containers above, split
            // at the seams the 648 census fixed. Inside wpShadowPhase: wpShadowMatrix is the modelview
            // build plus the perspective/ortho generation (getShadowModelView up to the
            // InternalShadowRenderingState.begin call); wpShadowFrustum is the frustum construction,
            // as two sequential spans under one name (the terrain frustum: createShadowFrustum through
            // its position set; the entity frustum: createEntityShadowFrustum through its own);
            // wpShadowTerrainSetup is the Celeritas-side terrain setup between the frustum and the GL
            // state (enterManagedCode through the setupGlState call - viewport creation, the section
            // graph dirty mark, setupShadowTerrain); wpShadowTargetPrep is setupGlState itself, the
            // shadow targets' bind and GL state preparation. Inside wpPackBegin: wpBeginUniforms sums
            // the four uniform-update calls (IrisTimeUniforms.updateTime, WorldTimeUniforms.snapshot,
            // FrameUpdateNotifier.onNewFrame, CustomUniforms.update) as sequential spans under one
            // name, and wpBeginTargets is prepareRenderTargets plus the custom-image clear loop.
            // wpPackBegin minus its two children reads as the section 7 "other" bucket (the begin
            // composite, the phase set, the horizon). All six are inclusive and report-only like their
            // parents: none joins WORLD_PASS_MEMBERS, and the sequential same-name spans never nest,
            // so the outermost-only accumulation stays exact.
            "wpShadowMatrix", "wpShadowFrustum", "wpShadowTerrainSetup", "wpShadowTargetPrep",
            "wpBeginUniforms", "wpBeginTargets",
            // Item 678 (shadow-diff Layer B, first stage): the inside of packRecordUniform, split at its
            // three priced seams so the census can name which of them owns the per-region uniform span
            // before anyone optimises it. packUniformOffset is the backend's region-offset write;
            // packUniformPack is one packUniforms execution at the two pack sites only (the terrain
            // pipeline's ring-slot staging and the backend's push-route call - the post chain's packs
            // deliberately stay out, so the name prices the terrain span and nothing else);
            // packUniformSetAlloc is the descriptor-set miss path only (allocate + update + snapshot
            // registration), so packRecordUniform minus these three reads as the hash / snapshot-check /
            // slot-claim residue by containment. All three are inclusive and report-only: none joins
            // TOP_LEVEL, FrameBudgetTimings' OTHER_BUCKETS or its WORLD_PASS_MEMBERS, so nothing here is
            // subtracted anywhere.
            "packUniformOffset", "packUniformPack", "packUniformSetAlloc",
            // GLSM stage 1: the table route's offset seam - the same span packUniformOffset prices
            // on the uniform route, renamed because what it prices changed (the interface write now
            // only records the value; the delivery is the pipeline's table write). Inclusive and
            // report-only like its sibling.
            "packRegionOffsetTable",
            // flight-c4 (flight-c3-desk section 4): the window flush a retained acceptance trips
            // inside itself, timed under the acceptance's own name. cloudAccept sums the two cell-VBO
            // acceptances whole - the acceptance body and any flush the acceptance forces - and the
            // desk's elimination left the flush as the only code home for the remaining ~1.45 ms, with
            // its count and price unmeasured. This bracket is that measurement: it wraps only the
            // flush call a retained acceptance makes, so its share of cloudAccept is read directly.
            // Inclusive and report-only like every INSIDE name; nothing subtracts it, and the plain
            // `flush` bracket keeps counting the same interval as before.
            "acceptFlushRetained",
            // Item 722 (optim-enum E6, first stage): the pack region loop's own seams, so a census can
            // price what is left of the pack record cost once the uniform span (packRecordUniform and
            // its item-678 children) is netted out. packRegionIter is one region's iteration head:
            // the iterator advance, the region / storage lookup and the cached-batch validity check
            // (packRecordRebuild nests inside it when a rebuild fires). packRegionBind is the
            // per-region bind issuance after the uniform span: bindTerrainUniformsAndSet, the
            // region vertex-buffer bind and the first shared index-buffer bind. packRegionDraw is
            // the per-command loop: the index-buffer rebinds between the arena and the shared
            // buffer, the vkCmdDrawIndexed calls and the closing shared-buffer restore.
            // packSamplerClaim is the samplersAndImages call at this pack site alone (the E11
            // observation point: the bracket's call count is the call count, its span is the
            // sampler / image hash and snapshot check the token design would pre-pay once per
            // pass). All four are inclusive and report-only: none joins TOP_LEVEL,
            // FrameBudgetTimings' OTHER_BUCKETS or its WORLD_PASS_MEMBERS, so nothing here is
            // subtracted anywhere.
            "packRegionIter", "packRegionBind", "packRegionDraw", "packSamplerClaim",
            // GLSM stage 2 (S2-1): the confirmed-window filing at the flush boundary - the
            // cause stamp spend, the trigger note and the census notes at the one commit
            // point. Inclusive and report-only like every INSIDE name.
            "flushBoundary"};

    private static final int CAPACITY = 512;

    private static final long[] frameTotals = new long[CAPACITY];
    private static final long[][] topLevel = new long[TOP_LEVEL.length][CAPACITY];
    private static final long[][] inside = new long[INSIDE.length][CAPACITY];
    private static final long[] residuals = new long[CAPACITY];
    private static int written;

    private static final long[] starts = new long[TOP_LEVEL.length + INSIDE.length];
    private static long frameStart;
    private static final PassTimingCoverage coverage = new PassTimingCoverage(TOP_LEVEL.length);
    private static final int[] insideDepth = new int[INSIDE.length];
    private static long currentFrameTotal;
    private static final long[] currentTopLevel = new long[TOP_LEVEL.length];
    private static final long[] currentInside = new long[INSIDE.length];
    private static long lastReportedFrames;

    /**
     * The thread the bracket tables belong to: the one that opens the frame and tick contexts (the client
     * main thread, through {@link #beginFrame()} and {@code TickTimings.beginTick}).
     * <p>
     * The tables above are plain statics with no synchronisation, which is sound only while one thread
     * writes them - and one bracket's call sites break that. The light funnel opens its {@code lightCache}
     * bracket before its own gate, and its mixins hang on light lookups the integrated server thread also
     * makes (mob AI), so with the timings on, a server-thread bracket could take a name's outermost slot
     * (its start in {@code starts}, the depth in {@code insideDepth}) while the frame's own pair nested
     * inside it and filed nothing - or filed the foreign interval, subtracted across threads when the CPU
     * clock is selected (roam-hunt-10's thread-boundary audit, observation A). Brackets from any other
     * thread are still counted in {@code BEGIN_CALLS}, so whether the bracket ran stays answerable, but
     * they never touch the tables, on either the frame path or the tick delegation.
     */
    private static volatile Thread contextThread;

    /** Records the calling thread as the bracket tables' owner; called when a frame or tick context opens. */
    static void noteContextThread() {
        contextThread = Thread.currentThread();
    }

    private static boolean onContextThread() {
        Thread owner = contextThread;
        return owner == null || Thread.currentThread() == owner;
    }

    private FramePassTimings() {
    }

    /**
     * Resolved through {@link HarnessConfig} like every other repeating instrument's switch: this is asked on
     * both sides of every bracket, and a per-bracket property walk is what the JFR census of the shipping
     * configuration put at 1.8% of the client thread's samples (65 of the 87 ConcurrentHashMap.get samples, from the
     * instrumentation itself) - so the answer stays a field read. The resolution is taken when this class
     * initialises and re-taken at every {@link #beginFrame()}; see {@link #resolveSwitches()} for why the re-take
     * exists.
     * <p>
     * This switch used to be the family's exception, a raw {@code Boolean.getBoolean("celtium.vulkan.passSplit")}:
     * the harness file could not name it at all, and the legacy command-line layer in {@link HarnessConfig} only
     * serves keys it is asked for, so the present fix never reached it. A pass-split leg on the validation harness
     * (FB2, 2026-11-22) ran with the switch set the way its launcher carries switches and the instrument's own
     * summary reported {@code enabled=false} - the same silent shape as QUEUE 641's litFog and the FPS A/B's
     * presentReadback. Through {@link HarnessConfig} the file's {@code passSplit=true} works and the legacy
     * spellings work with the master switch on; a player's run, which has neither, observes exactly the old
     * no-switch behaviour.
     */
    private static volatile boolean PASS_SPLIT_ENABLED;

    /** {@return whether the instrument should measure at all}. */
    public static boolean enabled() {
        // Deliberately not VulkanDiagnostics.enabled(): the acceptance fixture always passes celtium.vulkan.validation,
        // and tying the brackets to it put eight name lookups and two clock reads on every group of every acceptance leg -
        // the frame being judged paid for the instrument judging it. A run that wants the brackets asks for them.
        // TICK_PERCENTILES asks for the same brackets: the report is a split of them, so a leg that sets only the
        // report switch would otherwise print nothing and read as "the instrument measured nothing".
        // ACCEPT_FLUSH is in the same position: its bracket is one of these names, so the census switch
        // alone has to wake the tables or its own leg would measure nothing.
        return PASS_SPLIT_ENABLED || BRACKETS_ENABLED || TICK_PERCENTILES || ACCEPT_FLUSH
                || TickTimings.enabled();
    }

    /**
     * (Re-)resolves every harness switch this class keeps, through {@link HarnessConfig}: the nine answers the
     * bracket path reads as fields ({@link #PASS_SPLIT_ENABLED}, {@link #BRACKETS_ENABLED}, {@link #CPU_CLOCK},
     * {@link #TICK_SPLIT_FRAMES}, {@link #TICK_PERCENTILES} with {@link #PERCENTILE_FRAMES}, {@link #COUNT_CALLS},
     * {@link #MICROS} and {@link #ACCEPT_FLUSH}).
     * <p>
     * Called once when this class initialises, so a run that never opens a frame observes exactly the old
     * class-init resolution, and again at every {@link #beginFrame()} - once a frame, never per bracket, so the
     * property walk the JFR census priced stays off the bracket path. The frame open is the moment the
     * configuration of the frame being recorded is taken, and taking it there is what makes the resolution robust
     * against this class being initialised earlier by an unrelated touch: a production path a unit test exercises
     * can initialise the class before the harness configuration exists (the HUD census' flush hook evaluates this
     * class's UI-section state as a call argument at the head of {@code flushPendingBeforeGlDraw}, so its test
     * initialised the class with no harness properties set), and a resolution fixed at that moment used to hold for
     * the whole JVM - the coverage test then recorded nothing however it set its own properties (hud-census-1
     * sections 9/10, 2026-11-23). A player's run fixes its configuration before launch, so the re-take reads the
     * same values every frame and changes nothing there.
     */
    private static void resolveSwitches() {
        PASS_SPLIT_ENABLED = HarnessConfig.bool("passSplit", false);
        BRACKETS_ENABLED = HarnessConfig.bool("brackets", false);
        CPU_CLOCK = HarnessConfig.bool("passSplitCpu", false);
        TICK_SPLIT_FRAMES = HarnessConfig.integer("tickSplitFrames", 4000);
        TICK_PERCENTILES = HarnessConfig.bool("tickPercentiles", false);
        PERCENTILE_FRAMES = Math.max(64, HarnessConfig.integer("tickPercentileFrames", 4096));
        COUNT_CALLS = HarnessConfig.bool("passCounts", false);
        MICROS = HarnessConfig.bool("passSplitMicros", false);
        ACCEPT_FLUSH = HarnessConfig.bool("acceptFlush", false);
    }

    static {
        resolveSwitches();
    }

    /** The harness's {@code brackets=true}: keep the pass brackets on without the pass-split report. */
    private static volatile boolean BRACKETS_ENABLED;

    /**
     * The clock the brackets read.
     * <p>
     * The harness's {@code passSplitCpu=true} switches every bracket to the render thread's own CPU time, which
     * subtracts scheduler preemption and GC stop-the-world pauses from what the frame wall clock counts. The need is
     * the same one the immediate path's {@code getCurrentThreadCpuTime} split already serves (OPT-01): leg
     * perf-prep1 measured {@code prepBarriers} at ~95% of recordPrepare (5 ms AMD, 10.4 ms Intel p50) for what is
     * only a handful of {@code vkCmd*} recording calls, which are microseconds of CPU each - that gap is either real
     * driver work or preemption counted as work, and only a CPU clock separates them. Default stays wall time, so
     * every measured number before this flag keeps its meaning.
     */
    private static volatile boolean CPU_CLOCK;
    private static final java.lang.management.ThreadMXBean THREAD_BEAN =
            java.lang.management.ManagementFactory.getThreadMXBean();
    private static final boolean THREAD_CPU_SUPPORTED = THREAD_BEAN.isCurrentThreadCpuTimeSupported();

    /** {@return the timestamp the brackets compare, in nanoseconds under the selected clock}. */
    private static long clockNanos() {
        return CPU_CLOCK && THREAD_CPU_SUPPORTED ? THREAD_BEAN.getCurrentThreadCpuTime() : System.nanoTime();
    }

    /**
     * The brackets summed separately for frames drawn right after a client tick and for the rest, printed every
     * {@link #TICK_SPLIT_FRAMES} frames as {@code CELTIUM_FRAME_TICK_PASSES}: Intel's warm p99 is the tick frames, and
     * the frame drawn after a tick costs ~0.3 ms more than a plain one - these say which bracket holds it.
     */
    private static volatile int TICK_SPLIT_FRAMES;
    private static boolean tickSinceLastFrame;
    private static final long[] tickFrameSums = new long[1 + TOP_LEVEL.length + INSIDE.length];
    private static final long[] plainFrameSums = new long[1 + TOP_LEVEL.length + INSIDE.length];
    private static int tickFrames;
    private static int plainFrames;

    /** A client tick ended; the next frame to end is a tick frame. */
    public static void noteTick() {
        tickSinceLastFrame = true;
    }

    /**
     * The harness's {@code tickPercentiles=true}: the same tick/plain split as {@code CELTIUM_FRAME_TICK_PASSES},
     * but as percentiles of the tick frames instead of means.
     * <p>
     * The mean form cannot answer the question the KPI asks. The warm window's p99 is a <em>tick</em> frame - 200 of
     * ~4 200 frames carry a tick, so the 99th percentile of all frames is the ~79th percentile of the tick frames - and
     * a mean over 200 tick frames is dominated by the handful of frames that hit a one-off event (the shared index
     * buffer's single regeneration read 75 us a frame in one block, which is one 14.6 ms frame, not a per-tick cost).
     * The evidence history has read those means as per-tick costs more than once. This form keeps every frame's own
     * values and reports, per bracket, the tick frames' p50, the tick percentile that <em>is</em> the all-frames p99,
     * and the tick p95, beside the plain frames' p79 - so "which bracket owns the p99" is a percentile comparison
     * rather than an average of outliers.
     * <p>
     * Memory is {@code frames x brackets x 8} bytes, allocated only when the switch is on (4 096 frames is ~3 MB).
     */
    private static volatile boolean TICK_PERCENTILES;
    private static volatile int PERCENTILE_FRAMES;
    private static final int PERCENTILE_COLUMNS = 1 + TOP_LEVEL.length + INSIDE.length;
    private static long[][] percentileRows;
    private static boolean[] percentileIsTick;
    private static int percentileCount;

    /**
     * Files one frame for {@link #TICK_PERCENTILES} and prints the percentile report when the buffer is full.
     * <p>
     * Called before {@link #fileTickSplit()}, which consumes {@link #tickSinceLastFrame}.
     */
    private static void fileTickPercentiles() {
        if (!TICK_PERCENTILES) {
            return;
        }
        if (percentileRows == null) {
            percentileRows = new long[PERCENTILE_FRAMES][PERCENTILE_COLUMNS];
            percentileIsTick = new boolean[PERCENTILE_FRAMES];
        }
        long[] row = percentileRows[percentileCount];
        row[0] = currentFrameTotal;
        for (int i = 0; i < TOP_LEVEL.length; i++) row[1 + i] = currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) row[1 + TOP_LEVEL.length + i] = currentInside[i];
        percentileIsTick[percentileCount] = tickSinceLastFrame;
        if (++percentileCount < PERCENTILE_FRAMES) {
            return;
        }
        percentileCount = 0;
        int tickCount = 0;
        for (int i = 0; i < PERCENTILE_FRAMES; i++) {
            if (percentileIsTick[i]) tickCount++;
        }
        int plainCount = PERCENTILE_FRAMES - tickCount;
        if (tickCount == 0 || plainCount == 0) {
            return;
        }
        // The all-frames p99 is a tick frame at this rank among the tick frames: the plain frames are the faster
        // population, so they occupy the low ranks and the p99 lands (0.99 * total - plain) frames into the tick ones.
        int p99Rank = (int) Math.round(0.99 * PERCENTILE_FRAMES) - plainCount;
        if (p99Rank < 0) p99Rank = 0;
        if (p99Rank >= tickCount) p99Rank = tickCount - 1;
        long[] tickValues = new long[tickCount];
        long[] plainValues = new long[plainCount];
        StringBuilder line = new StringBuilder("[Celtium] CELTIUM_FRAME_TICK_PERCENTILES tickFrames=").append(tickCount)
                .append(" plainFrames=").append(plainCount).append(" p99RankInTick=").append(p99Rank)
                .append(" (us; tick p50/p99eq/p95 and plain p79, brackets whose tick p99eq differs from plain p79"
                        + " by >= 10)");
        // The same block's *absolute* costs, which the line above cannot show: its filter keeps only the brackets
        // whose p99 rank differs from the plain p79, so a bracket that costs the same on both frame kinds - the
        // offer path, the window machinery, the terrain prepare - is invisible however large it is. That is the
        // wrong shape for "what does this renderer cost every frame", which is the question a p99 ratio of 0.98
        // asks. This line prints every bracket whose tick p50 is at least 10 us, as tick p50 / plain p50, so the
        // constant costs are read directly instead of being inferred from a difference.
        StringBuilder constants = new StringBuilder("[Celtium] CELTIUM_FRAME_TICK_CONSTANTS tickFrames=")
                .append(tickCount).append(" plainFrames=").append(plainCount)
                .append(" (us; tick p50/plain p50, brackets whose tick p50 is >= 10)");
        // The p99 frame's excess over the median tick frame, per bracket. The percentile line above filters on
        // |p99eq - plainP79|, which keeps exactly the brackets that are expensive on tick frames *and not* on plain
        // ones - so a bracket that costs the same on both frame kinds is invisible there however large it is, and
        // that is the shape a p99 ratio of 1.01 has: the renderer is a minority of the p99 frame and the excess is
        // spread over brackets that are expensive on every frame. This line ranks the excess itself.
        long[] tailExcess = new long[PERCENTILE_COLUMNS];
        long[] tailP99 = new long[PERCENTILE_COLUMNS];
        for (int k = 0; k < PERCENTILE_COLUMNS; k++) {
            int t = 0;
            int p = 0;
            for (int i = 0; i < PERCENTILE_FRAMES; i++) {
                if (percentileIsTick[i]) tickValues[t++] = percentileRows[i][k];
                else plainValues[p++] = percentileRows[i][k];
            }
            Arrays.sort(tickValues);
            Arrays.sort(plainValues);
            long tickP50 = tickValues[tickCount / 2] / 1000L;
            long tickP99 = tickValues[p99Rank] / 1000L;
            long tickP95 = tickValues[Math.min(tickCount - 1, tickCount * 95 / 100)] / 1000L;
            long plainP79 = plainValues[plainCount * 79 / 100] / 1000L;
            long plainP50 = plainValues[plainCount / 2] / 1000L;
            String name = k == 0 ? "frame"
                    : k <= TOP_LEVEL.length ? TOP_LEVEL[k - 1] : INSIDE[k - 1 - TOP_LEVEL.length];
            if (k == 0 || Math.abs(tickP99 - plainP79) >= 10L) {
                line.append(' ').append(name).append('=').append(tickP50).append('/').append(tickP99).append('/')
                        .append(tickP95).append('/').append(plainP79);
            }
            if (k == 0 || tickP50 >= 10L) {
                constants.append(' ').append(name).append('=').append(tickP50).append('/').append(plainP50);
            }
            tailExcess[k] = tickP99 - tickP50;
            tailP99[k] = tickP99;
        }
        System.out.println(line);
        System.out.println(constants);
        System.out.println(tailLine(tailExcess, tailP99));
    }

    /**
     * {@return the p99 frame's excess over the median tick frame, per bracket, largest first}.
     * <p>
     * Each bracket's own p99-rank value minus its own median, so the numbers are percentiles of different brackets
     * rather than one frame's split: a bracket whose p99-rank value is high because it is high on every frame shows
     * a small excess here and a large absolute cost in {@code CELTIUM_FRAME_TICK_CONSTANTS}, and the two lines
     * together are what separate "expensive always" from "expensive at the tail". The frame row is included, so the
     * excess the brackets are being compared against is on the same line.
     */
    private static String tailLine(long[] excess, long[] p99) {
        StringBuilder text = new StringBuilder("[Celtium] CELTIUM_FRAME_TICK_TAIL (us; p99eq-p50 and p99eq,"
                + " excess >= 5, largest first)");
        boolean[] used = new boolean[excess.length];
        for (int n = 0; n < 24; n++) {
            int best = -1;
            for (int k = 0; k < excess.length; k++) {
                if (used[k] || excess[k] < 5L) {
                    continue;
                }
                if (best < 0 || excess[k] > excess[best]) {
                    best = k;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            String name = best == 0 ? "frame"
                    : best <= TOP_LEVEL.length ? TOP_LEVEL[best - 1] : INSIDE[best - 1 - TOP_LEVEL.length];
            text.append(' ').append(name).append('=').append(excess[best]).append('/').append(p99[best]);
        }
        return text.toString();
    }

    private static void fileTickSplit() {
        long[] into = tickSinceLastFrame ? tickFrameSums : plainFrameSums;
        if (tickSinceLastFrame) tickFrames++; else plainFrames++;
        tickSinceLastFrame = false;
        into[0] += currentFrameTotal;
        for (int i = 0; i < TOP_LEVEL.length; i++) into[1 + i] += currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) into[1 + TOP_LEVEL.length + i] += currentInside[i];
        if (tickFrames + plainFrames < TICK_SPLIT_FRAMES || tickFrames == 0) return;
        StringBuilder line = new StringBuilder("[Celtium] CELTIUM_FRAME_TICK_PASSES tickFrames=").append(tickFrames)
                .append(" plainFrames=").append(plainFrames).append(" (mean us, tick/plain, brackets differing by"
                        + " >=10 us)");
        for (int k = 0; k < tickFrameSums.length; k++) {
            long tick = tickFrameSums[k] / tickFrames / 1000L;
            long plain = plainFrameSums[k] / Math.max(1, plainFrames) / 1000L;
            if (k == 0 || Math.abs(tick - plain) >= 10L) {
                String name = k == 0 ? "frame" : k <= TOP_LEVEL.length ? TOP_LEVEL[k - 1] : INSIDE[k - 1 - TOP_LEVEL.length];
                line.append(' ').append(name).append('=').append(tick).append('/').append(plain);
            }
        }
        System.out.println(line);
        Arrays.fill(tickFrameSums, 0L);
        Arrays.fill(plainFrameSums, 0L);
        tickFrames = 0;
        plainFrames = 0;
    }

    /** Brackets the frame itself. */
    public static void beginFrame() {
        // The frame's configuration is taken here, once per frame: see resolveSwitches().
        resolveSwitches();
        if (!enabled()) return;
        noteContextThread();
        coverage.reset();
        Arrays.fill(starts, 0L);
        Arrays.fill(insideDepth, 0);
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        frameStart = clockNanos();
    }

    /** Closes the frame bracket and files the frame's record. */
    public static void endFrame() {
        if (!enabled() || frameStart == 0L) return;
        long frameEnd = clockNanos();
        currentFrameTotal = frameEnd - frameStart;
        frameStart = 0L;
        long residual = currentFrameTotal - coverage.coveredNanos(frameEnd);
        if (residual < 0L) residual = 0L;
        int slot = written % CAPACITY;
        frameTotals[slot] = currentFrameTotal;
        residuals[slot] = residual;
        for (int i = 0; i < TOP_LEVEL.length; i++) topLevel[i][slot] = currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) inside[i][slot] = currentInside[i];
        // Before fileTickSplit(), which consumes the tick flag this reads.
        fileTickPercentiles();
        fileTickSplit();
        written++;
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        Arrays.fill(starts, 0L);
        Arrays.fill(insideDepth, 0);
        coverage.reset();
        maybeReport();
    }

    /**
     * How many times each bracket was opened, counted whether or not the timings are enabled.
     * <p>
     * The times themselves are only recorded under {@link #enabled()}, which is off by default, so an empty
     * {@link #summary()} cannot say whether a bracket ran - and that ambiguity is exactly what the missing HUD needed
     * settled: `GuiIngameMixin` brackets Forge's {@code GuiIngameForge.func_175180_a} with {@code require = 0}, so a
     * failed injection and a HUD that is never drawn both leave the timing summary silent. These counts separate them:
     * a non-zero {@code gui} count with zero identity-0 groups in the frame means the HUD is drawn and its batches are
     * not captured, and a zero count means the function does not run at all.
     */
    private static final long[] BEGIN_CALLS = new long[TOP_LEVEL.length + INSIDE.length];

    /** {@return how many times the bracket for one pass name was opened, enabled or not}. */
    public static long passBeginCalls(String pass) {
        int i = indexOf(pass);
        return i < 0 ? -1L : BEGIN_CALLS[i];
    }

    /**
     * Whether the {@code gui} bracket is open right now.
     * <p>
     * Kept whether or not the timings are enabled, for the same reason the counts above are: the question it answers
     * ("is this group being created inside the HUD's own drawing function?") has to be answerable without turning the
     * timings on. {@code GuiIngameMixin} brackets Forge's {@code func_175180_a} with {@code require = 0}, so an open
     * bracket plus the group's own identity and projection is what says whether the HUD's batches reach the renderer,
     * which image they name, and with which matrix they were projected.
     */
    public static boolean guiSectionActive() {
        return guiActive;
    }

    private static boolean guiActive;

    /**
     * Whether the {@code screen} bracket is open right now: a {@code GuiScreen} (or a
     * {@code GuiContainer}, whose draw nests the base class' draw inside its own) is being drawn.
     * <p>
     * A depth, not a flag, for the nesting the GUI bracket never has: {@code GuiContainer.drawScreen}
     * calls {@code super.drawScreen}, so the base bracket opens and closes inside the container's,
     * and a plain flag would read closed for the container's own item rendering after the super call
     * returns - which is the heaviest part of the screen to attribute.
     */
    public static boolean screenSectionActive() {
        return screenDepth > 0;
    }

    /** {@return whether either UI bracket - the HUD overlay or an open screen - is open}. */
    public static boolean uiSectionActive() {
        return guiActive || screenDepth > 0;
    }

    private static int screenDepth;

    /** {@return every bracket's open count, as {@code name=count} pairs}, so no name has to be guessed. */
    public static String passBeginCallsSummary() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < BEGIN_CALLS.length; i++) {
            if (i > 0) text.append(',');
            text.append(i < TOP_LEVEL.length ? TOP_LEVEL[i] : INSIDE[i - TOP_LEVEL.length]).append('=')
                    .append(BEGIN_CALLS[i]);
        }
        return text.toString();
    }

    /** Opens a pass bracket. Nested passes are allowed: an inner pass's time stays inside its outer pass. */
    public static void begin(String pass) {
        // The GUI flag first, by identity: the call sites pass string literals, so this is the same comparison indexOf
        // made, without the hash lookup. It has to stay unconditional because the group logic reads it while the timings
        // are off.
        if (pass == "gui") {
            guiActive = true;
        }
        if (pass == "screen") {
            screenDepth++;
        }
        // The call counts used to run on every bracket whether the timings were enabled or not - 1.1% of the render
        // thread in a steady-state JFR, spent on a hash lookup per bracket. The question they answer ("was the bracket
        // opened at all, or opened and empty?") is only asked while diagnosing, so they are opt-in now.
        // Resolved when anything needs it: the timings themselves when they are enabled, or the counts when asked for.
        // Skipping it when both are off is the point - it is the hash lookup that cost 1.1% of the render thread.
        // enabled() is a static-field read plus a property OR; asking it once here rather than twice keeps the bracket
        // that runs per group and per window to one question.
        boolean timingsOn = enabled();
        int counted = timingsOn || COUNT_CALLS ? indexOf(pass) : -1;
        if (counted >= 0) BEGIN_CALLS[counted]++;
        if (!timingsOn) return;
        // Off the context thread the bracket is the count above and nothing else: see contextThread.
        if (!onContextThread()) return;
        // The client tick runs between frames, so `frameStart` is zero there and the frame path below would drop the
        // bracket. TickTimings keeps its own row and its own report; the name table is shared, so a bracket means the
        // same thing in both contexts.
        if (TickTimings.open()) {
            TickTimings.begin(counted);
            return;
        }
        if (frameStart == 0L || counted < 0) return;
        int i = counted;
        long now = clockNanos();
        boolean outermost = i < TOP_LEVEL.length ? coverage.begin(i, now)
                : insideDepth[i - TOP_LEVEL.length]++ == 0;
        if (outermost) starts[i] = now;
    }

    /** Closes a pass bracket and adds its time to the current frame. */
    public static void end(String pass) {
        // The GUI flag is the only thing this method needs with the timings off, and it is one string comparison
        // against a literal - which is a pointer compare for the interned name the mixin passes - rather than the
        // map lookup the index needs. With the timings on, both are wanted and the lookup is paid once as before.
        // Measured: with the timings off, FramePassTimings.end was 1.4% of the client thread's JFR samples in
        // HashMap tree-bin lookups, because every bracket close in the frame went through the map.
        if (pass == GUI_PASS || (guiActive && GUI_PASS.equals(pass))) {
            guiActive = false;
        }
        if (pass == SCREEN_PASS || (screenDepth > 0 && SCREEN_PASS.equals(pass))) {
            if (screenDepth > 0) {
                screenDepth--;
            }
        }
        if (!enabled()) return;
        // Off the context thread the close pairs with a count-only open: see contextThread.
        if (!onContextThread()) return;
        if (TickTimings.open()) {
            TickTimings.end(indexOf(pass));
            return;
        }
        if (frameStart == 0L) return;
        int i = indexOf(pass);
        if (i < 0 || starts[i] == 0L) return;
        long now = clockNanos();
        boolean outermost;
        if (i < TOP_LEVEL.length) {
            outermost = coverage.end(i, now);
        } else {
            int insideIndex = i - TOP_LEVEL.length;
            outermost = insideDepth[insideIndex] > 0 && --insideDepth[insideIndex] == 0;
        }
        if (!outermost) return;
        long elapsed = now - starts[i];
        starts[i] = 0L;
        if (i < TOP_LEVEL.length) currentTopLevel[i] += elapsed;
        else currentInside[i - TOP_LEVEL.length] += elapsed;
    }

    /**
     * Name to bracket index, built once.
     * <p>
     * The lookup used to be two linear scans with string compares, run on every bracket open and twice on every close.
     * The dense world scene (leg splitD1, qmagnet's exhibition) opens several thousand brackets a frame - ~1,955
     * immediate batches each with their own spans - and JFR put 1.4% of the client thread's samples in this scan
     * alone. A bracket name is a literal, so a map is the right shape: it costs one hash instead of up to
     * {@code TOP_LEVEL.length + INSIDE.length} string comparisons.
     */
    private static final java.util.Map<String, Integer> INDEX = buildIndex();

    private static java.util.Map<String, Integer> buildIndex() {
        java.util.Map<String, Integer> index = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            index.put(TOP_LEVEL[i], Integer.valueOf(i));
        }
        for (int i = 0; i < INSIDE.length; i++) {
            index.put(INSIDE[i], Integer.valueOf(TOP_LEVEL.length + i));
        }
        // A plain map: the wrapper added a call per lookup and the table is private and never mutated after this, while
        // the JFR showed Collections$UnmodifiableMap.get inside the hottest bracket path (validation runs keep the
        // brackets enabled, because diagnostics turns them on).
        return index;
    }

    /**
     * A direct-mapped cache over the bracket-name lookup.
     * <p>
     * Bracket names are literals, so the same handful of strings repeat for the whole session; a 32-slot table keyed by
     * identity hash answers almost every call with a reference compare instead of a map get. Measured with the timings
     * disabled - the shipping configuration - {@code FramePassTimings.indexOf} was still 1.4% of the client thread's JFR
     * samples, because both begin and end resolve the name even when nothing is being timed.
     */
    private static final String[] INDEX_CACHE_NAME = new String[256];
    private static final int[] INDEX_CACHE_VALUE = new int[256];

    /** The GUI bracket's name: {@link #end} compares it directly so the disabled path pays no map lookup. */
    private static final String GUI_PASS = "gui";

    /** The screen bracket's name, compared directly in {@link #end} for the same reason as {@link #GUI_PASS}. */
    private static final String SCREEN_PASS = "screen";

    /**
     * Whether per-bracket call counts are kept while the timings are disabled.
     * <p>
     * Off by default: the counts cost a hash lookup on every bracket, measured at 1.1% of the render thread in a
     * steady-state profile. They exist to separate "the bracket never ran" from "the bracket ran and measured zero", a
     * question asked while diagnosing, so they are a switch rather than a permanent tax.
     */
    private static volatile boolean COUNT_CALLS;

    /** {@return whether per-bracket call counting is on} (the harness's {@code passCounts}). */
    public static boolean countsCallsEnabled() {
        return COUNT_CALLS;
    }

    /**
     * {@return the bracket index for a name, or -1}, for {@link TickTimings}' own top-level set.
     * <p>
     * Package-private on purpose: the name table is this class's, and a second table would let the same name mean two
     * indices.
     */
    static int indexOfName(String pass) {
        return indexOf(pass);
    }

    /** {@return how many bracket names exist}, the width of a per-bracket row. */
    static int bracketCount() {
        return TOP_LEVEL.length + INSIDE.length;
    }

    /** {@return the name of one bracket index}, for a report that stores indices rather than strings. */
    static String nameOf(int index) {
        if (index < 0) return "?";
        return index < TOP_LEVEL.length ? TOP_LEVEL[index] : INSIDE[index - TOP_LEVEL.length];
    }

    private static int indexOf(String pass) {
        // 256 slots, not 32: the brackets number thirty-odd names and are entered in a repeating order, so a 32-slot
        // direct-mapped table collides and falls back to the map on the very path the cache exists for.
        int slot = System.identityHashCode(pass) & 255;
        if (INDEX_CACHE_NAME[slot] == pass) {
            return INDEX_CACHE_VALUE[slot];
        }
        Integer found = INDEX.get(pass);
        int index = found == null ? -1 : found.intValue();
        INDEX_CACHE_NAME[slot] = pass;
        INDEX_CACHE_VALUE[slot] = index;
        return index;
    }

    /** Prints a one-line split every 200 frames, so a running session shows the shape without a report. */
    private static void maybeReport() {
        if (written < 200 || written - lastReportedFrames < 200) return;
        long windowFrames = written - lastReportedFrames;
        lastReportedFrames = written;
        System.out.println("[Celtium] CELTIUM_FRAME_PASS_SPLIT " + summary());
        // Item 678: the pack-uniform census prints on this same window cadence (its counters only
        // accumulate while an instrument here is enabled, so the ride costs nothing when off).
        PackUniformCensus.reportWindow(windowFrames);
        // GLSM stage 2 (S2-1): the single flush-boundary census rides the same cadence and
        // reconciles against the trigger sums before the window's trigger counts reset.
        FlushBoundaryCensus.reportWindow(windowFrames, windowsByTriggerWindowSum(), windowsByTriggerTotalSum());
        if (ACCEPT_FLUSH) {
            System.out.println("[Celtium] CELTIUM_ACCEPT_FLUSH " + acceptFlushSummary());
            sizeFlushTripsWindow = 0L;
            sizeFlushTripsByRetainedWindow = 0L;
            sizeFlushTripPendingSumWindow = 0L;
        }
        Arrays.fill(windowsByTriggerWindow, 0L);
    }

    /**
     * The flight-c4 accept-flush census (flight-c3-desk section 4): the harness's {@code acceptFlush=true}.
     * <p>
     * The desk's elimination put cloudAccept's unexplained ~1.45 ms in exactly one code home - the
     * size-driven window flush an acceptance trips inside itself once the pending index count crosses
     * the window bound - but the trip count and the flush's price inside the acceptance were unmeasured,
     * and a reduction built on an unmeasured dominant term is the order flight-c2 section 4 warned
     * against. This census is the measurement, in three parts: the {@code acceptFlushRetained} bracket
     * (the flush's time, attributed to the retained acceptance that tripped it), the size-trip counter
     * below (how often the bound is crossed, by a retained acceptance or not, and how full the counter
     * stood when it crossed), and the window trigger counts (what opens the frame's windows at all, so
     * a later change can show the size share of the window population directly). All three are
     * report-only: no name here joins a subtraction set, and with the switch off the note methods are
     * one field read each on their call sites.
     */
    private static volatile boolean ACCEPT_FLUSH;

    /** {@return whether the accept-flush census is on} (the harness's {@code acceptFlush}). */
    public static boolean acceptFlushEnabled() {
        return ACCEPT_FLUSH;
    }

    /** A window opened by the accept path's size bound: the acceptance's pending indices crossed it. */
    public static final int WINDOW_TRIGGER_SIZE = 0;
    /**
     * A window opened by an ordering-only flush: a refusal handoff, a clear order, or a copy / chain /
     * model-replay ordering call - entrances that flush to preserve order before an operation that is
     * not itself a draw.
     */
    public static final int WINDOW_TRIGGER_ORDER = 1;
    /** A window opened at the frame's end: the frame-boundary flush or the present path's drain. */
    public static final int WINDOW_TRIGGER_FRAME_END = 2;
    /**
     * A window opened by the ordering flush at a GL draw or bind boundary - the entrances
     * {@code flushPendingBeforeGlDraw} is named for, and the reading an unstamped entrance gets.
     */
    public static final int WINDOW_TRIGGER_GL_DRAW = 3;

    private static final String[] WINDOW_TRIGGER_NAMES = {"size", "order", "frameEnd", "glDraw"};

    // The window counters cover the frames since the previous report print, matching the split line
    // they ride beside; the totals beside them cover the whole run, so a reader of any single print
    // can tell a window's local shape from the run's.
    private static long sizeFlushTripsWindow;
    private static long sizeFlushTripsByRetainedWindow;
    private static long sizeFlushTripPendingSumWindow;
    private static final long[] windowsByTriggerWindow = new long[WINDOW_TRIGGER_NAMES.length];
    private static long sizeFlushTripsTotal;
    private static long sizeFlushTripsByRetainedTotal;
    private static final long[] windowsByTriggerTotal = new long[WINDOW_TRIGGER_NAMES.length];

    /**
     * Counts one size-driven flush trip at the accept path's firing point: whether the acceptance that
     * crossed the bound is a retained one, and the pending index count it crossed at. Called on every
     * trip, retained or not - the retained share of the trips is the number the design's premise needs,
     * and a counter that only counted retained trips could not give it.
     */
    public static void noteSizeFlushTrip(boolean retained, int pendingIndices) {
        if (!ACCEPT_FLUSH) return;
        sizeFlushTripsWindow++;
        sizeFlushTripsTotal++;
        if (retained) {
            sizeFlushTripsByRetainedWindow++;
            sizeFlushTripsByRetainedTotal++;
        }
        sizeFlushTripPendingSumWindow += pendingIndices;
    }

    /**
     * Counts one opened window under the trigger that opened it; see the {@code WINDOW_TRIGGER_*} kinds.
     * <p>
     * Since GLSM stage 2 (S2-1) this counts unconditionally: the trigger counts are the
     * derivation view {@link FlushBoundaryCensus} reconciles against, so gating them on the
     * accept-flush switch would make the reconciliation compare against a population that
     * only exists on census legs. The cost is two array increments per confirmed window.
     */
    public static void noteWindowTrigger(int trigger) {
        if (trigger < 0 || trigger >= WINDOW_TRIGGER_NAMES.length) return;
        windowsByTriggerWindow[trigger]++;
        windowsByTriggerTotal[trigger]++;
    }

    /** {@return the current report window's windows summed over triggers}, for the S2-1 reconciliation. */
    static long windowsByTriggerWindowSum() {
        long sum = 0L;
        for (long count : windowsByTriggerWindow) sum += count;
        return sum;
    }

    /** {@return the since-start windows summed over triggers}, for the S2-1 reconciliation. */
    static long windowsByTriggerTotalSum() {
        long sum = 0L;
        for (long count : windowsByTriggerTotal) sum += count;
        return sum;
    }

    /** {@return the accept-flush census line for the report print}: window counts, then run totals. */
    private static String acceptFlushSummary() {
        StringBuilder text = new StringBuilder("window[sizeTrips=").append(sizeFlushTripsWindow)
                .append(" sizeTripsByRetained=").append(sizeFlushTripsByRetainedWindow)
                .append(" meanPendingAtTrip=")
                .append(sizeFlushTripsWindow == 0L ? 0L : sizeFlushTripPendingSumWindow / sizeFlushTripsWindow)
                .append(" windows=");
        appendTriggerCounts(text, windowsByTriggerWindow);
        text.append("] total[sizeTrips=").append(sizeFlushTripsTotal)
                .append(" sizeTripsByRetained=").append(sizeFlushTripsByRetainedTotal)
                .append(" windows=");
        appendTriggerCounts(text, windowsByTriggerTotal);
        text.append(']');
        return text.toString();
    }

    private static void appendTriggerCounts(StringBuilder text, long[] counts) {
        for (int i = 0; i < WINDOW_TRIGGER_NAMES.length; i++) {
            if (i > 0) text.append(',');
            text.append(WINDOW_TRIGGER_NAMES[i]).append('=').append(counts[i]);
        }
    }

    /**
     * {@return the frame bracket alone, p50/p95/worst}, the short form the F3 panel's default shows.
     * <p>
     * The complaint about that panel was its length, not its content: the full split is a few hundred characters of
     * bracket text per line. The same numbers stay available through {@link #summary()} in the log's
     * {@code CELTIUM_VERIFY} line and in the panel under {@code -Dceltium.f3.fullSummary=true}.
     */
    public static String summaryShort() {
        if (written == 0) return "not measured yet";
        int n = Math.min(written, CAPACITY);
        return "p50=" + ms(percentile(frameTotals, n, 0.50)) + " p95=" + ms(percentile(frameTotals, n, 0.95))
                + " worst=" + ms(worst(frameTotals, n)) + " " + unit();
    }

    /**
     * {@return the pass split of the <em>worst single frame</em> in the ring, in milliseconds}.
     *
     * <p>Written for the frame-time work: the fixture keeps a bounded record of the slowest frames, and a summary of
     * percentiles over the ring cannot say which pass produced one of them. The worst frame can be read directly,
     * because its leave-behind is a single frame that did happen - {@code terrain} at 470 ms and {@code present} at
     * 3 ms is a statement about one frame, where "the p95 of terrain minus the p95 of the frame" is not.
     */
    /**
     * {@return the pass split of the frame that ended most recently, in milliseconds}.
     *
     * <p>Per-entry fidelity for the fixture's slow-frame record: {@link #worstFrameSummary()} answers "which pass
     * produced the worst frame so far", which is one frame repeated for every entry, while a slow frame is recorded
     * right after it ends and wants its own split. Both exist because they are different questions - the worst frame
     * is the headline, and the entry beside a slow frame has to describe that frame.
     */
    public static String lastFrameSummary() {
        if (!enabled() || written == 0) return "pass split not measured";
        return frameSummary((written - 1) % CAPACITY);
    }

    /** {@return one frame's pass split, from the ring slot that holds it}. */
    private static String frameSummary(int slot) {
        StringBuilder text = new StringBuilder("frame=").append(ms(frameTotals[slot]));
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            text.append(' ').append(TOP_LEVEL[i]).append('=').append(ms(topLevel[i][slot]));
        }
        for (int i = 0; i < INSIDE.length; i++) {
            text.append(' ').append(INSIDE[i]).append('=').append(ms(inside[i][slot]));
        }
        text.append(" residual=").append(ms(residuals[slot]));
        return text.toString();
    }

    public static String worstFrameSummary() {
        if (!enabled() || written == 0) return "pass split not measured";
        int n = Math.min(written, CAPACITY);
        int slot = 0;
        for (int i = 1; i < n; i++) {
            if (frameTotals[i] > frameTotals[slot]) slot = i;
        }
        return frameSummary(slot);
    }

    /**
     * {@return how many frames the ring window currently holds}, for {@link FrameBudgetTimings}' other-split:
     * the two instruments' windows are compared, so each side must say how many frames its numbers cover.
     */
    static int windowFrameCount() {
        return Math.min(written, CAPACITY);
    }

    /**
     * {@return the ring window's per-bracket means in nanoseconds}: index 0 is the frame itself, then
     * {@link #TOP_LEVEL}, then {@link #INSIDE} - the same order {@link #indexOfName} numbers the brackets in.
     * <p>
     * Means, not percentiles, because the consumer adds them: {@link FrameBudgetTimings}' other-split decomposes
     * one mean (the budget's {@code other}) into bracket means over the same steady window, and percentiles of
     * different brackets describe frames that never happened. The ring's slots hold the window's frames in
     * scrambled order once it wraps, which a mean does not care about; before it wraps, the unwritten slots are
     * exactly the ones past {@code written} and are excluded by the same count.
     */
    static long[] windowMeansNanos() {
        int n = windowFrameCount();
        long[] means = new long[1 + TOP_LEVEL.length + INSIDE.length];
        if (n == 0) {
            return means;
        }
        long frameSum = 0L;
        for (int s = 0; s < n; s++) {
            frameSum += frameTotals[s];
        }
        means[0] = frameSum / n;
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            long sum = 0L;
            for (int s = 0; s < n; s++) {
                sum += topLevel[i][s];
            }
            means[1 + i] = sum / n;
        }
        for (int i = 0; i < INSIDE.length; i++) {
            long sum = 0L;
            for (int s = 0; s < n; s++) {
                sum += inside[i][s];
            }
            means[1 + TOP_LEVEL.length + i] = sum / n;
        }
        return means;
    }

    /** {@return the ring window's mean per-frame residual} (frame minus the union of the top-level brackets). */
    static long windowResidualMeanNanos() {
        int n = windowFrameCount();
        if (n == 0) {
            return 0L;
        }
        long sum = 0L;
        for (int s = 0; s < n; s++) {
            sum += residuals[s];
        }
        return sum / n;
    }

    /**
     * {@return the per-pass split of the frames measured so far}, as p50/p95/worst milliseconds plus the residual.
     * <p>
     * The residual is per frame ({@code frame - union(named brackets)}) and then summarised, not a difference of
     * percentiles - a p50 of the frame minus a p50 of the passes would describe a frame that never happened.
     */
    public static String summary() {
        if (written == 0) return "frames=0 (the instrument did not measure; enabled=" + enabled() + ")";
        int n = Math.min(written, CAPACITY);
        StringBuilder text = new StringBuilder("clock=").append(CPU_CLOCK && THREAD_CPU_SUPPORTED ? "cpu" : "wall")
                .append(" unit=").append(unit())
                .append(" frames=").append(n)
                // The conditions the split was measured under, on the split itself. The Khronos validation layer is a
                // measurement switch, not a mode, and it is not free: the runner's own note records it costing 6.6 ms
                // of an 11.0 ms frame on the standard scene, and the per-present readback another 16% of the client
                // thread. The paired KPI runs with both off, so a split taken with either on describes a different
                // frame - rounds 42-46 read one as the KPI's shape and spent five rounds on the bracket it inflated.
                .append(" conditions[validation=")
                .append(HarnessConfig.bool("vulkanValidation", true) ? "on" : "off")
                .append(" readback=").append(HarnessConfig.bool("presentReadback", true) ? "on" : "off")
                .append(']');
        text.append(" frame[p50=").append(ms(percentile(frameTotals, n, 0.50)))
                .append(" p95=").append(ms(percentile(frameTotals, n, 0.95)))
                .append(" worst=").append(ms(worst(frameTotals, n))).append(']');
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            text.append(' ').append(TOP_LEVEL[i]).append("[p50=").append(ms(percentile(topLevel[i], n, 0.50)))
                    .append(" p95=").append(ms(percentile(topLevel[i], n, 0.95)))
                    .append(" worst=").append(ms(worst(topLevel[i], n))).append(']');
        }
        text.append(" residual[p50=").append(ms(percentile(residuals, n, 0.50)))
                .append(" p95=").append(ms(percentile(residuals, n, 0.95)))
                .append(" worst=").append(ms(worst(residuals, n))).append(']');
        for (int i = 0; i < INSIDE.length; i++) {
            text.append(' ').append(INSIDE[i]).append("(inside world)[p50=")
                    .append(ms(percentile(inside[i], n, 0.50)))
                    .append(" p95=").append(ms(percentile(inside[i], n, 0.95)))
                    .append(" worst=").append(ms(worst(inside[i], n))).append(']');
        }
        return text.toString();
    }

    /**
     * The sort scratch {@link #percentile} works on, owned by this class instead of a fresh
     * {@code Arrays.copyOf} per call: a single {@link #summary()} asks for ~34 percentiles over rings of
     * {@link #CAPACITY} longs, and summaries print every 200 frames, so the copies were a steady allocation
     * on the render thread for a purely local computation. Grow-only; the method is synchronized because
     * summary readers are not all the same thread, and a garbled percentile would be a wrong number, not
     * just a wasted one. Lifetime owner: this class (static), confined by the method lock.
     */
    private static long[] percentileScratch = new long[0];

    private static synchronized long percentile(long[] values, int n, double q) {
        if (percentileScratch.length < n) {
            percentileScratch = new long[n];
        }
        System.arraycopy(values, 0, percentileScratch, 0, n);
        Arrays.sort(percentileScratch, 0, n);
        int index = (int) Math.round(q * (n - 1));
        return percentileScratch[Math.max(0, Math.min(n - 1, index))];
    }

    private static long worst(long[] values, int n) {
        long max = 0L;
        for (int i = 0; i < n; i++) max = Math.max(max, values[i]);
        return max;
    }

    /**
     * The harness's {@code passSplitMicros=true}: print the split in microseconds instead of milliseconds.
     * <p>
     * The millisecond form rounds to 0.1 ms, so every bracket under 50 us reads 0.0 and cannot be ranked. Round 58's
     * split left eight window brackets - {@code winHead}, {@code winPublish}, {@code winBeginPass}, {@code winPassEnd},
     * {@code winSubmit}, {@code winBarrier}, {@code wfpBegin}, {@code wfpMiddle} - all at 0.0 while their sum was
     * ~0.2 ms a frame, which is exactly the resolution the next decision needs and exactly what 0.1 ms cannot give.
     * The unit is printed beside the frame count so a microsecond line can never be read as a millisecond one.
     */
    private static volatile boolean MICROS;

    /** {@return the unit the split is printed in, for the line's own label}. */
    public static String unit() {
        return MICROS ? "us" : "ms";
    }

    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", MICROS ? nanos / 1_000.0D : nanos / 1_000_000.0D);
    }
}
