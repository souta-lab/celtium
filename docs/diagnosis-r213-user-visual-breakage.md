# Diagnosis — user-reported visual breakage (r213)

Reproduced headlessly on the user's exact bisect jar
(`celtium-6794b0df2-pre-stub-changes.jar`, sha256 `1ab53f4c`, plus the matching
validation client) via `scripts/compositor_capture.py`, instance
`cleanroom-0.6.13-alpha`, RX 6400 headless, `menuHoldTicks=400`, `saveSlot=New
World` (a `generatorName=flat` save), `holdUntilTick=300`. 107 real compositor
frames in `.celtium-work/acceptance/p-bugrepro-caps/`, presented dumps in
`.celtium-work/runtime/p-bugrepro-bisect/screenshots/`.

All four user symptoms reproduce on the real compositor output:

| Symptom | Reproduced |
|---|---|
| パノラマは依然とおかしいし操作できない | YES — cube fragment displaced to top-left + grey + blur fragment |
| 視点がおかしい / 一人称でもプレイヤーから離れている | YES — world content rendered vertically inverted |
| ロード画面は依然と表示されていない | YES — menu frame frozen ~5 s during world load (no presents); FML splash intentionally skipped under GL-less |
| GUIが一切表示されない | PARTIAL — GUI renders correctly in my captures (menu + buttons + text). User may have seen a different phase; buttons are present but the panorama machinery is broken so "can't operate" reads plausibly as input-vs-visual confusion |

The run's counters are green by construction (`presented=277..577`,
`textureCopiesServedPeakPerPresent=7`, `matrixMirrorDelta=0.0` exact,
`CELTIUM_WINDOW_DRAW identity=0 image=frame` both phases) — the gates measure
colour stats, not orientation. An orientation defect passes them all.

## Current status (round 98 — all user symptoms diagnosed; splash at the last discriminator)

| Symptom | Status |
|---|---|
| パノラマはおかしい / 操作できない | **RESOLVED** — `be3ade744` (matrix-scratch contamination) + `87503bbe3` (blur copy-cache epoch). Menu `distinct 26176→113116→79370`, cube repaint 0.8%→13.1%/frame. Residuals: a ~200px sharp unblurred corner patch + ~30-40px left-edge clip. |
| 視点がおかしい / プレイヤーから離れている | **RESOLVED for orientation** (defect 2). Entities confirmed drawing — `modelDraws=46`, `capturedOwners=RenderPlayer.func_177138_b[128,132]` (the first-person arm/hand renders). Remaining: a pre-first-`recordRenderPass` entity transient (lazy-build fix specced, unlanded) + a possible camera-position feel worth a real-game check. |
| ロード画面は表示されない | **Upstream of GL**: `setLoadingProgress`'s `ortho(100,300)` never appears in 32181 enqueues → the draws were never enqueued → the progress callback never fired in this fixture (the composite path itself is now proven sound — same mechanism as the splash's, below). |
| GUIが一切表示されない | **RESOLVED** — menu GUI renders correctly (buttons + text). |
| FML splash white (new) | **RESOLVED — no defect (round-236, `698cc5c59`)**. The whole rounds-117-123 "mirrored logo + corner tile" arc was a **DUMP-ORIENTATION misreading**: `stage-splash-*.png`/`mirror-*.png` are written **image-order (row 0 = the picture's bottom)** → read upside-down → an upright Mojang logo reads as "horizontally mirrored text" and the `glViewport(0,0,256,256)` **bottom-left** tile reads as a "top-left corner". The picture-order dump (`stage-splash-p1-picture.png`) confirms: **logo upright ✓, panorama tile bottom-left ✓ = GL-viewport-correct**. (1) The panorama chain (`renderSkybox`/`drawPanorama`/`rotateAndBlurSkybox`) is **menu-only** (`GuiMainMenu` — the loading screen never draws it); the 256×256 box is the chain's own viewport seen at a present before it finished. (2) The white first frame is **the game's own loop** (draws FBO → swaps → then the composite — a GL client shows the same picture at that instant). (3) The "logo" is **Mojang's**, not Cleanroom's — `MOOЯNAƎJƆ` is upside-down MOJANG; `mirror-gl8` is mojang.png the right way round. All my exonerations (unmirrored texture/vertices/ortho/announce/`multiplyMatrix`, `glCopyTexSubImage2D` served) were *correct but chasing a non-defect* — the dumps were being read upside-down. Remaining real questions: (a) whether the loading screen shows the menu's blur/full-screen composite, (b) the user's *Cleanroom* logo's texture/mirror (the legs use vanilla Mojang). **Superseded**: `ef3aadf85` ordering + transform arc. **Round-125 — main moved to the HEI-font defect** (`hei-font-base` leg — the "GUI 文字の暗転" item: the vanilla tooltip's shadow/main-pixel structure — HEI's own text matches the reference but the vanilla tooltip `(660,315)-(820,355)` shows `shadow_px=797 shadow_without_main=585 (73.4%)` vs ref `693/0` — column-structured 18-GUI-px-cell missing). The loading-screen ledger is now fully closed: panorama=RESOLVED (menu-only+corner-tile-was-misreading), splash=RESOLVED (no defect), view=RESOLVED, GUI=resolved, loading=upstream-of-GL (`setLoadingProgress` never fires). **Round-126 — HEI-font defect, `hei-font-base` evidence**: `CELTIUM_MODEL_MATRIX list=124 liveMVt=(0.514,-0.804,-0.689) capturedMVt=(0.347,-1.706,...)` — the draw's **captured modelview differs from the live** → a stale captured matrix → the font's main/shadow draws use different transforms → the `drawStringWithShadow` (main at x,y + shadow at x+1,y+1) misaligns → `shadow_without_main=585` (shadow pixels with no main 1-GUI-px up-left) → column-structured missing. `CELTIUM_GROUP_VIEWPORT gl=0,0 1280x720 clipped=0,0` is correct → not a viewport issue → the defect is the **modelview-capture staleness** (`liveMVt != capturedMVt` — the draw uses a matrix captured before the font's `glTranslatef`, or the capture runs on the wrong frame's stack). **Round-127 — the mechanism is `glCallList`'s record-vs-call matrix**: `FontRenderer.renderString` draws every glyph via `glCallLists` (a display-list per glyph). GL semantics: a display-list's draw uses the **modelview at `glCallList`-time** (execution), not at `glNewList`-time (recording) — the glyph quad's positions are recorded, the matrix applies live. `list=124 liveMVt=(0.514,...) capturedMVt=(0.347,...)` → the GL-less **applies the record-time captured matrix** (captured when the list was built) instead of the live call-time modelview → every glyph drawn at the *record-time* position → the main/shadow text drawn at different transforms → `shadow_without_main` → column-structured missing (each list's stale offset = a cell). **The fix**: the `glCallList`/`glCallLists` handler must apply the **live modelview at call-time**, not the captured-at-build matrix — the recorded draw keeps the recorded vertices but the matrix must be the live stack's. **Round-128 correction — `glCallList` draws NOTHING under GL-less** (`SEM_CALL_LIST:1844` only records `lastCalledList`; display-lists aren't replayed) → the font's glyphs are NOT drawn via `glCallList` (they'd show as nothing). The `list=124` in `CELTIUM_MODEL_MATRIX` is a **draw-group/batch id**, not a display-list → `capturedMVt != liveMVt` is a **draw-group's captured matrix vs live** → the group draws with a stale captured modelview → the font's main/shadow drawn at different transforms → `shadow_without_main`. The defect is the **draw-group's matrix-capture staleness** — the group captures the modelview at batch-record, but the draw applies it later after more `glTranslatef`s → the fix is the captured matrix being the live at *draw*-time (or the batch recording the matrix per-draw not per-group). **Round-129 correction — `CELTIUM_MODEL_MATRIX` is a TERRAIN diagnostic, not the font**: `VulkanTerrainGpuBackend:10439` prints `list=N liveMVt=(modelViewNow[12..14]) capturedMVt=(VulkanModelQuadCapture.firstStoredMatrixTranslation())` — `list=` is a **terrain model-quad/chunk list** (`VulkanModelQuadCapture`/`celtium$enqueueModelMesh`), and `celtium$enqueueModelMesh(..., modelViewNow, ...)` draws with the **live** → `capturedMVt` is the *stored* model matrix (for frustum-culling/comparison), not the draw's → the `liveMVt != capturedMVt` mismatch is a terrain-culling diagnostic, **not the font's shadow/main defect**. My rounds-126-128 font-modelview analysis chased a terrain diagnostic — the font's `shadow_without_main` is elsewhere (the font's own draw-group/texture mechanism — main's `hei-font-base` is the baseline for the font instrument). **Round-129b — the defect's real shape**: `drawStringWithShadow` → `renderString(text,x+1,y+1,c,shadow)` then `renderString(text,x,y,c,main)` — `shadow_without_main=585` means the **main-text quads are dropped in 18-GUI-px columns** (the shadow draws, the main doesn't, in cell-blocks) → a **batch/scissor/draw-drop** in the font's main-text path, not a transform — `texture=118`/`WRAP_S/T=REPEAT` (the font atlas) bound and params correct → the quads' draw is dropped in column-groups (a batching boundary or a scissor/clip that culls the main-text's 18-px cells). My rounds-126-128 chased the terrain `MODEL_MATRIX` diagnostic — the font defect is a draw-drop, whose evidence is which draw/batch carries the main-text's quads and why its cells are dropped. **Round-130 — the REAL defect is a descriptor-layout VUID, not a font-path issue**: `VulkanPostChain:1595` declared its sampler descriptors `VK_IMAGE_LAYOUT_GENERAL` while the image was moved to `SHADER_READ_ONLY_OPTIMAL` by the sampling side → `VUID-vkCmdDraw-None-09600` at `vkQueueSubmit` → the post-chain's draw **refused** → the HEI-font rendering broken. Main's fix declares `SHADER_READ_ONLY_OPTIMAL` on `inInfo`/`auxInfo` (+11 uncommitted). **`hei-font-fixed` still fires `VUID-09600` ×4 for `VkImage 0x370000000037`** — but the VUID says "expects GENERAL, got SHADER_READ_ONLY_OPTIMAL" = the **PRE-fix direction** (a descriptor still declaring GENERAL). `grep -c "imageLayout(GENERAL)"` = **0 in `VulkanPostChain`** (inInfo/auxInfo now SHADER_READ_ONLY_OPTIMAL) and `git grep imageLayout(GENERAL)` = **0 across all of `celtium-vulkan/src/main/java`** → every sampling descriptor is now SHADER_READ_ONLY_OPTIMAL → **the source has no GENERAL-declaring descriptor left** → `hei-font-fixed` ran a **pre-fix jar** (the +11 `VulkanPostChain` change is uncommitted and wasn't in that leg's built jar) → the fix is complete-in-source but **not yet validated** — needs a build+rerun, expecting the VUID to flip to none (or, if an image was moved to GENERAL instead, the reverse-direction VUID "expects SHADER_READ_ONLY_OPTIMAL, got GENERAL" → then the image's `ensureSampledLayout`/transition is the gap). My font matrix/draw-drop/scissor analyses (rounds 126-129) all chased symptoms — the defect was a descriptor-layout VUID refusing the post-chain draw. **Round-131 — `hei-font-fixed2` ran the PRE-fix jar + reveals a two-consumer conflict**: `dd0b0389c` (04:05) committed the SHADER_READ_ONLY_OPTIMAL fix, but `hei-font-fixed2`'s jar is dated **04:02** (same 2749955-byte jar as `hei-font-fixed`) → **pre-fix** → both its VUIDs are the pre-fix state → the fix isn't validated. The two VUID directions on the SAME `VkImage 0x370000000037`: (a) "expects GENERAL, got SHADER_READ_ONLY_OPTIMAL" = the `VulkanPostChain` descriptor declaring GENERAL (dd0b0389c fixes it to SHADER_READ_ONLY_OPTIMAL) ✓; (b) **"expects SHADER_READ_ONLY_OPTIMAL, got GENERAL"** = a *second* consumer's descriptor declaring SHADER_READ_ONLY_OPTIMAL while `0x370000000037` is in **GENERAL at its draw** → the image's `ensureSampledLayout` didn't transition it → a **layout-transition defect beyond the descriptor declaration** — dd0b0389c alone won't clear VUID-(b): the sampling side for consumer-2 must ensure `0x370000000037` is SHADER_READ_ONLY_OPTIMAL (or GENERAL) at *its* draw — two consumers of one image transition it between layouts and each descriptor must match its draw-time state. **Round-132 — `0x370000000037` is the off-target frame image whose `ensureSampledLayout` isn't called**: `VulkanPostChain:963-975` sets `inView = inTarget.view / frameTarget.colorView()` and `auxView` similarly — the post-chain's sampled input is the **off-target frame image** (`frameTarget.colorView()` or a published `VulkanGlTexture`). `VulkanGlTexture:977 ensureSampledLayout` transitions such an off-target image to SHADER_READ_ONLY_OPTIMAL before sampling (and `ensureGeneralLayout:986` back for attachment use). The post-chain's `descriptorSet:1558` only *writes* the descriptor — the `dd0b0389c` comment assumes "the sampling side before the draw" calls `ensureSampledLayout` on the published frame texture — but for `frameTarget.colorView()` (or whichever inTarget provides `0x370000000037`) **no `ensureSampledLayout` runs before the post-chain's `vkCmdDraw`** → the image stays GENERAL → VUID-(b). **The fix**: the post-chain's draw path must `ensureSampledLayout` (or an equivalent transition-to-SHADER_READ_ONLY_OPTIMAL barrier) on the inTarget/auxTarget image before the sampled draw — the off-target frame image's layout isn't ensured by the descriptor declaration alone. `f76ed42da` notes the leg "failed on validation messages, the black frame is the GL screenshot" — the VUID-refused draw → black frame. **Round-133 — a SECOND defect main is fixing: `flushPendingForPresent` (+47 uncommitted, `VulkanTerrainGpuBackend:6864`)** — flushes the pending immediate-window at the swap → the presented frame includes the HUD/GUI/loading-screen's **queued-but-unflushed** draws. The evidence: "the presented frame has no HUD and no screen while the screen was open and its batches were accepted and drawn" — the immediate-window batches were queued but the window wasn't flushed before the swap → the present served the *previous* window's frame → no HUD/screen. The flush is guarded: `presentFlushRefusals` counts the terrain-pass-in-progress case (a window recording → the flush is impossible → the present still happens with the prior picture). So the "ロード画面/GUI が表示されない" defect was the pending-window-not-flushed-at-present, not a draw/capture issue — the batches were accepted but the window's draw wasn't emitted before the swap. **Round-134 — the confirmed evidence** (`:11260-66` comment): the HEI leg's presented world frame at `captureStartTick=217` had **no HUD and no JEI screen** although `jeiScreenOpenedAtTick=34`, `keepGui=true`, `screenshotAtTickScreen=GuiContainerCreative` — the screen was open, its batches **accepted and drawn** (`prepareDescriptorFailures=0, postAcceptDroppedGroups=0, immediateGroupsKeptForOtherTargets=0`) "just not in the image the present read" → the pending window's draws weren't flushed before the swap → `flushPendingForPresent` (`:11267`) flushes them → the presented frame has the JEI screen + HUD. "The same contract is why the splash's first presented frame is the game's own white clear" — the present serves the frame the game *finished* drawing. **Round-135 — `hei-font-presentflush` `presentFlushes=0`**: `flushPendingForPresent` ran but **early-returned** (`immediateWindowBusy` or `immediateGroups.isEmpty()` — `presentFlushes=0` + `presentFlushRefusals=0` + only `immediateGroups` reports, no screen-open verdict) → the JEI screen's draws were **not in the pending immediate-window** → the "no screen" defect's draws went through a **different window/group** (already flushed, or a GUI-specific path that doesn't queue on the backend's `immediateGroups`) → `flushPendingForPresent` flushes the wrong group OR the screen's draws are in a window already drained before the swap → the pending-window theory needs the *right* group — the screen's draws live elsewhere (a GUI-screen window/group the backend tracks separately, or `glFlush`-driven). **Round-136 — the screen draws through the game's `Framebuffer` FBO, a different target**: `CELTIUM_BIND_CALLER` shows the FBO lifecycle — `Framebuffer.func_147605_b:112` (`bindFramebuffer`) → draws → `func_147606_d:193` (`unbindFramebuffer`) → `func_147607_a:143` (`bindFramebufferTexture`, `id=3`) — the JEI screen draws into the game's **`Framebuffer` FBO** (a *different render target* than the frame-FBO `flushPendingForPresent` drains). The screen-FBO's draws live on **that FBO's own window** → the frame-FBO's `immediateGroups` is empty at present → `presentFlushes=0` → the screen-FBO's window isn't flushed → the screen's draws never emit → "no screen". **The fix shape**: the screen's FBO window must be flushed too (flush *every* open target's pending window at present, not just `frameTarget`'s `immediateGroups`) — or the `Framebuffer`'s output synced to the frame. The `unbindFramebuffer` → `bindFramebufferTexture` path binds the FBO's color texture to draw it — that's the frame-FBO draw that needs the FBO's window already flushed into its image. **Round-137 — the mechanism is `immediateGroupsKeptForOtherTargets`** (`:3490-3497`): "The immediate window draws the groups of `currentRenderTarget` only (`flushImmediateWindow`: 'others remain owned in the queue') and runs on the main target later in the frame, so a group stamped with another identity **waits for a pass that belongs to it** — which for the entity-outline framebuffer never comes" → the JEI screen's draws (stamped with the screen-`Framebuffer`'s FBO identity) are `immediateGroupsKeptForOtherTargets` — kept in the queue, **wait for the screen-FBO's own pass** → `flushImmediateWindow`/`flushPendingForPresent` draws only `currentRenderTarget`'s → `immediateGroups` reads empty at present → `presentFlushes=0` (the kept groups are a *separate* counter) → the screen's draws never emit → "no screen". The gap is **the screen-FBO's pass never opening** — `Framebuffer.bindFramebuffer` (the `glBindFramebuffer(screenFBO)` → window-open mapping) must open the screen-FBO's window for its kept groups to draw; if the FBO-bind→window mapping fails the kept groups wait forever → "no screen" — same `bindFramebuffer` region-mapping defect as the panorama's. **The fix**: the FBO's bind must open its window + flush its kept groups (or the present must flush *all* targets' windows, not just `currentRenderTarget`'s). **Round-138 — `immediateGroupsKeptForOtherTargets=0` refutes my kept-groups theory** (`95c2dc99a` round-238 acceptance review): (1) the HEI leg fails ONLY on the 2 VUID-09600 — the "black frame" is the **instrument's nature** (`pictureScreenshotUsable_tick200=False`, "black by construction under the GL-less stub"; the real evidence is `presentedDistinctColours=2257 presentedBlackFraction=0.0`). (2) The real defect: the presented world frame at `captureStartTick=217` has no HUD/screen although `jeiScreenOpenedAtTick=34` + `keepGui=true` + `screenshotAtTickScreen=GuiContainerCreative` (the screen WAS open) + `prepareDescriptorFailures=0` + `postAcceptDroppedGroups=0` + **`immediateGroupsKeptForOtherTargets=0`** = batches accepted and drawn into SOME image. (3) `hei-font-presentflush` `presentFlushes=0 presentFlushRefusals=0` = the queue was empty at swap → the pending-window theory **REFUTED** → `flushPendingForPresent` **reverted** (unfounded unreachable path). **So the HUD/screen's batches were accepted+drawn but into a different image than the frame's** → the presented frame lacks them → the discriminating read is `CELTIUM_DRAW_SITE` (`target`/`passImage` — which image each group drew) + `CELTIUM_WINDOW_DRAW` (`identity`/`frameImage`/`chainPair`) matched against the frame's image → the defect is a **target/image mismatch** (the HUD/screen's groups drew into a non-frame image → not the deferred-queue or the flush — a bind→image mapping). **Round-139 — `0x370000000037` is the chainPair, and the VUID-09600 IS the "no screen" mechanism**: `CELTIUM_WINDOW_DRAW identity=0 image=frame attachedImage=0x210000000021 frameImage=0x21 chainPair=0x370000000037` — the HUD/screen's batches draw into `frameImage=0x21` (the frame's image), which is **adopted into `chainPair=0x37`** (the presented pair). So the draws ARE in `0x21` → adopted into `0x37` → presented. The `VUID-09600` on `0x37` — a descriptor-sample of the pair while it's in a mismatched layout → **the composite's `vkCmdDraw` refused** → the pair's composite (which carries the HUD/screen) fails → `0x37` lacks the HUD/screen → presented → "no screen". **The VUID-09600 and the "no HUD/screen" are the SAME defect**, not two: the composite's sampled draw refused → the pair's composite (with the HUD/screen) fails → the presented pair lacks it. `dd0b0389c` (the descriptor declaration) + the `ensureSampledLayout`-on-`0x37`-before-the-composite-draw fix (round-132) are the fix — the VUID cleared → the composite succeeds → the HUD/screen shows. The `ensureSampledLayout` leg (the `0x37` image must be SHADER_READ_ONLY_OPTIMAL at the composite's sampled draw) is the remaining validation. **Round-140 — the view-orientation defect's numeric evidence**: `modelMatrixPair=list 117/119/124 compile(t=0.347,-1.706,-0.74) replay(t=0.514,-1.404,-0.689)` for `RenderPlayer.func_177138_b` (the player model's display-list) — the display-list's **compile-time captured matrix `t=0.347`** vs its **replay-time live `t=0.514`** — a **+0.167 x-translation offset** between the two → `glCallList` replays with the live `0.514` → the player model drawn at `x=0.514` instead of the captured `0.347` → **the player offset ~0.167 in x** → "一人称でもプレイヤーから離れている". The `compile` value is the matrix captured when the list was built (`glNewList` — the model's rest pose); `replay` is the live at `glCallList`-time — GL says the replay uses the live, so `0.514` is "correct" only if the live player modelview is right — the +0.167 offset says the live modelview has an extra x-translation the compile captured one didn't → a **modelview-offset defect on the replay** (the live player modelview carries a stray translate) → the player drawn displaced → "離れている". **Round-141 — the view-orientation fix ALREADY LANDED**: `VulkanModelQuadCapture.matrixForList:723` returns **IDENTITY** — "a GL display list does not bake a matrix in; GL transforms them with whatever modelview is current when `callList` runs; the stored positions are model space" — the `compile vs replay` `+0.167` difference is a **measurement, not a defect** (the compile captured at build `0.347`, the replay draws under the live `0.514` — different times, expected to differ). The OLD code composed `now * compile^-1` (a double-ish transform → the player offset) — replaced with IDENTITY → the replay draws the model-space vertices under the live `0.514` → **correct → view-orientation RESOLVED** (`matrixForList` returns IDENTITY). My `compile vs replay` analysis chased the diagnostic, not a defect — the fix is in. Remaining: the `0x37` chainPair VUID → "no screen" (`dd0b0389c` + `ensureSampledLayout`-on-`0x37`). **Round-142 — `0x37` is the chain-pair's shared non-main framebuffer image**: `identityPairs=[mainIdentities=1 nonMainIdentities=6 pairs=3 widestPair=chain-pair(5 identities share it) perPair=[frame-image=1,chain-pair=5,default-pair=1]]` — `GllessFrameTarget:146 chainPair` takes "every other non-main identity" → **5 non-main identities (the screen-FBO + 4 others) share `chain-pair`'s image `0x37`** → the JEI screen's `Framebuffer` draws into `0x37` → the composite samples `0x37` → the VUID-09600 → the chain-pair's layout conflict (GENERAL at render, SHADER_READ_ONLY at the composite's sample — `initialLayout(UNDEFINED)` → GENERAL via the render, needs SHADER_READ_ONLY via `ensureSampledLayout` before the sample). The screen's draws ARE in `0x37` — the VUID-refused composite draw means the pair's composite fails → "no screen". `dd0b0389c` fixed the descriptor declaration; the remaining fix is `ensureSampledLayout` (or a transition) on `0x37` before the composite's sampled draw — shared non-main framebuffer images' layout must be ensured at the sample. **Round-143 — the chain-pair lifecycle is the missing back-transition** (`:7787-7799` comment): "`celtium$transitionPublishedFrameTextures(true)` — Every other published name gets the same hand-back here, before the clear and the attach: **the sampling transition leaves the pair image in SHADER_READ_ONLY_OPTIMAL, and this pass begins with initialLayout GENERAL — the two VUIDs are one lifecycle: sampled → read-only, attached → general.**" So `0x37` is moved **to GENERAL for the attach** (the render pass's `initialLayout=GENERAL`) → the composite samples it (SHADER_READ_ONLY descriptor) → **the composite's sampling path doesn't transition `0x37` back to SHADER_READ_ONLY** → "expects SHADER_READ_ONLY, got GENERAL" → the 09600 VUID → the composite's draw refused → "no screen". The two VUIDs are the lifecycle's two missing transitions: (a) the attach-side needs `0x37` back to GENERAL after a sample (handled by `transitionPublishedFrameTextures`), (b) **the sample-side needs `0x37` to SHADER_READ_ONLY before the composite's draw — missing** → `ensureSampledLayout`/`transitionPublishedFrameTextures`-for-sample on `0x37` before the composite's sampled draw. **Round-144 — the missing transition is `publishedChainOutput.ensureSampledLayout` gated on `queuedForTargetUsesPublished`** (`:7892-7898`): `this.publishedChainOutput.ensureSampledLayout(this.commandBuffer)` runs **only when `queuedForTargetUsesPublished(this.activeImmediateTarget)`** — "the transition belongs to the submission that draws the sampling batch — recorded outside the pass, before the draw". Then `celtium$transitionPublishedFrameTextures(false)` transitions every *published* name → `ensureSampledLayout` — the comment ALREADY documents the defect ("A name published for a game framebuffer whose draws land in the pair... sampled from a pass whose descriptor declares SHADER_READ_ONLY while the pair's own layout is GENERAL: leaving it there is exactly the VUID-vkCmdDraw-None-09600"). **So `0x37`'s transition is in place but GATED** → `0x37` (`publishedChainOutput`/the chain-pair's image) transitions to SHADER_READ_ONLY only when `queuedForTargetUsesPublished` reports the composite's target uses it → if the composite's target doesn't report `usePublished` → `0x37` stays GENERAL → the composite's sampled draw → "expects SHADER_READ_ONLY, got GENERAL" → VUID → "no screen". **The gap is `queuedForTargetUsesPublished` missing `0x37`'s consumer** — the chain-pair's sample-side transition fires only for targets the queue says use the published name; the composite's consumer isn't in that set → `0x37` not transitioned → GENERAL → VUID. The fix: `ensureSampledLayout` on `0x37` unconditionally before the composite's sampled draw (or `queuedForTargetUsesPublished` covering the chain-pair's consumer). **Round-145 — the gate misses the post-chain's own descriptor-bound draw**: `queuedForTargetUsesPublished:3005-3014` checks `immediateGroups` for `group.texture == publishedChainTexture` — the `publishedChainOutput.ensureSampledLayout` gate fires only when a queued `ImmediateGroup` binds `publishedChainTexture`. **The composite's sampled draw on `0x37` is the post-chain's own draw** — `VulkanPostChain.descriptorSet:1558` binds `0x37`'s `inView`/`auxView` **directly** into `VkDescriptorImageInfo`, not via an `ImmediateGroup.texture` → `queuedForTargetUsesPublished` can't see it → `0x37`'s sample-side transition never fires → `0x37` stays GENERAL → the post-chain's composite draw (SHADER_READ_ONLY descriptor, `dd0b0389c`) on a GENERAL `0x37` → "expects SHADER_READ_ONLY, got GENERAL" → VUID-09600 → "no screen". **The fix**: the post-chain's composite pass must `ensureSampledLayout`/`transitionPublishedFrameTextures`-for-sample on `0x37` (its `inView`/`auxView` source image) **before its `vkCmdDraw`** — the `immediateGroups`-based `usePublished` gate doesn't cover the post-chain's descriptor-bound draws. **Round-146 — the REAL "no screen" mechanism is the finished-frame composite OVERWRITING the frame's own draws** (main's +43 `frameImageDrawnFrame`/`frameImageCompositeSkips`): "87 GUI-stage groups under identity 0 into `attachedImage == frameImage 0x2e`, and the presented frame shows the world and neither hotbar nor crosshair" — the HUD/screen's draws DID land in `frameImage=0x2e` (correctly), but **the finished-frame composite (a full-screen quad sampling the chain-pair `0x37`, drawing onto `frameImage`) overwrote them with the pair's picture** — the pair holds the world *without* the HUD/screen → the composite's copy erased them → "no screen". **The fix**: `frameImageCompositeSkips` skips the finished-frame composite when `frameImageDrawnFrame == frameId` (the frame image already holds this frame's own draws — the HUD/screen recorded after the world) → the composite's copy would erase them → skipped → the HUD/screen preserved. The loading screen keeps its composite (the frame image holds nothing of the game's own → the copy is the only thing bringing the picture to the window). **My rounds-139-145 `0x37`-VUID chase was a DIFFERENT issue** (the composite's sampled draw's layout — `dd0b0389c` + `ensureSampledLayout` fix it) — the "no screen" is the composite *overwriting* the HUD/screen draws, not the VUID refusing them. **Round-147 — `frameImageCompositeSkips=0`: the skip's `!published-image` condition misses the HUD's own textures** (`r212-world-hud2`): `frameImageCompositeSkips=0` → `frameImageDrawnFrame` never set → the HUD/screen's draws (`format=2` ×14) have `mirrorSourceFor(group.texture)` = **"published-image"** (×3 in the log) → the skip's condition `!celtium$registry().celtium$mirrorSourceFor(group.texture).startsWith("published-image")` **excludes the HUD's own published-image textures** → `frameImageDrawnFrame` never set → the finished-frame composite runs → the HUD/screen overwritten → "no screen". The `!published-image` guard was meant to exclude the composite's own texture, but the HUD/screen's textures are *also* published-image (a published GL name over the chain-pair/`0x37` or another published source) → the condition can't distinguish the composite's quad from the HUD's draws → the skip never fires. **The fix shape**: `frameImageDrawnFrame` should be set by *any* non-composite draw into `frameTarget.colorImage()` (the HUD/screen's own picture), not gated on the texture's mirror-source — or gate on `group.texture != publishedChainTexture` (the composite's specific texture) rather than `!published-image`. **Round-148 — `r212-world-hud3` shows the HUD's draws are `texture=0` (untextured GUI)**: `texture=0` ×33 (most groups), `format=2` ×12, `published-image` ×3 — the HUD/screen's draws are `texture=0` (untextured GUI quads — `Gui.drawRect`/the tooltip's background+border — drawn with no texture) → the skip's `group.texture != 0` condition **also excludes the HUD's untextured draws** → `frameImageDrawnFrame` never set → `frameImageCompositeSkips=0` → the composite runs → "no screen". **Two wrong conditions**: `!published-image` (excludes the HUD's published-image textures) AND `texture != 0` (excludes the HUD's untextured `texture=0` quads) → the HUD/screen's draws (untextured GUI + published-image textures) never set `frameImageDrawnFrame` → the composite's copy always runs → "no screen". **The fix**: `frameImageDrawnFrame` should be set by **any non-composite draw into `frameTarget.colorImage()`** — no texture/source gate — i.e. `group.target == mainIdentity && group.texture != publishedChainTexture` (the composite's specific texture) OR `formatId == 2 && target == frameTarget.colorImage()` without the texture!=0/published-image guards. **Round-149 — the `!published-image && texture != 0` guards are STILL in the +49 diff** (`:12989` unchanged) → `frameImageCompositeSkips=0` in `r212-world-hud3` → the HUD's `texture=0` untextured draws (×33) and published-image textures (×3) are still excluded → `frameImageDrawnFrame` never set → the composite always runs → "no screen" persists. The guard intended to exclude the composite's own quad also excludes every GUI draw (the JEI screen's untextured `Gui.drawRect` background `texture=0` + the hotbar's published-image texture). **The condition must gate on `group.texture != publishedChainTexture`** (the composite's specific texture — the chain-pair's published name), not `texture != 0` or `!published-image` — the HUD's draws carry `texture=0` and published-image names legitimately. **Round-150 — `6ae0a5c73` REFUTES BOTH my mechanisms**: (a) `CELTIUM_PRESENT_PATCH hotbarBand=` (read at the present's hotbar band, r212-world-hud4, 1280x720 AMD) → `frameCentre=(33,23,16) hotbarBand=(32,23,16)` — **the band holds the world's own colours** → **the HUD is NOT in `frameImage` at all** → the present/blit isn't losing it. (b) `frameImageCompositeSkips=0` → **the finished-frame composite never runs** → the composite isn't the cause → the mechanism reverted. **My round-146-149 analysis was wrong on two counts**: (i) the "87 GUI groups into `frameImage`" evidence was a **frame-0 logging window artifact**, not the world frames (the group logs lacked the frame filter); (ii) the composite never runs → not the cause. **The real defect**: the HUD/screen's draws at the world frames land in a **different target than `frameImage`** — they draw into `0x37`/the chain-pair or another identity's image, not `frameImage` → `frameImage` (presented) has only the world → "no screen". `CELTIUM_GROUP_BOUNDS`/`groupBoundsMinFrame` is the new census — which target the HUD's draws land in at the world frames. **Round-151 — `r212-world-hud4`: the HUD's draws attach `0x21` but don't land**: `attachedImage=0x210000000021` = `frameImage=0x21` ×15 `worldPass=false` groups — the HUD/screen's draws DO attach `frameImage=0x21` → they should draw into it → but `hotbarBand=` reads `0x21` at the present → **the world's colours** → the HUD's draws into `0x21` **don't land** — either (a) the draws are refused (a VUID on `0x21` — but the VUID-09600 was on `0x37`, not `0x21`), or (b) `0x21` is re-cleared/overwritten after the HUD's draws (the pair `0x37`'s content composited over it), or (c) the HUD's draws land in a different region/sub-image of `0x21` the hotbar band doesn't cover. `groupBoundsMinFrame` wasn't set → `CELTIUM_GROUP_BOUNDS` printed nothing → the HUD's actual target isn't in the census yet. The narrowing: the HUD attaches `0x21` (correctly) but its content isn't in `0x21` at the present → the draw is refused or `0x21` is re-written after → `CELTIUM_GROUP_BOUNDS`/`groupBoundsMinFrame` at the world frames will name the HUD's actual target. **Round-152 — `r212-world-hud5` `CELTIUM_GROUP_BOUNDS` NAMES THE TARGET**: the HUD/screen's `worldPass=false` groups target `1854149592` (`0x6E7A4DD8` — a **non-main identity** → `identityPairs` maps it to `chain-pair`/`0x37`) — `group=1668/1673/1675/1677/1679 fmt=2 worldPass=false target=1854149592 texture=9` → **the HUD/screen's draws land in `0x37` (the chain-pair), NOT `frameImage=0x21`** → `frameImageCompositeSkips=0` → **the `0x37`→`frameImage` finished-frame composite never runs** → `frameImage` has only the world → `hotbarBand` reads the world → "no screen". The HUD's draws DO land — in `0x37` — but the composite that would carry `0x37`→`frameImage` never runs → the HUD never reaches `frameImage` → "no screen". **Two candidate fixes**: (a) run the finished-frame composite (`0x37`→`frameImage`) after the GUI — `frameImageCompositeSkips`'s inverse, a *required* composite, not a skipped one; or (b) route the HUD/screen's `worldPass=false` groups to the **main identity** (`target=0` → `frameImage`) not `1854149592` — the HUD should draw into `frameImage` directly, not the chain-pair. `worldPass=true` groups (the world) also target `1854149592` — the whole world+HUD draws into `0x37`, and only the composite brings it to `frameImage` — so `frameImage` at the present has only what was drawn into it directly. **Round-153 — `1854149592` is a framebuffer's `identityHashCode`, a non-main identity → `chain-pair`/`0x37`**: `GllessFrameTarget:1039` — "identity 0 → `defaultPair`, every OTHER non-main identity → `chainPair`"; `VulkanPostChain:68` — "chain targets are pure-Vulkan images keyed by the framebuffer object's `identityHashCode`". So `1854149592` (`0x6E7A4DD8`) is a non-main `Framebuffer`'s `identityHashCode` → `identityPairs` maps it to `chain-pair`/`0x37` → **the HUD/screen drew under a non-main `Framebuffer` (not the main `Minecraft.getFramebuffer()` whose identity → `frame-image`/`0x21`)** → the HUD's draws land in `0x37` → the `0x37`→`frameImage` composite doesn't run → `frameImage` lacks the HUD → "no screen". **Why the HUD is under a non-main framebuffer**: the game's `GuiIngame`/screen binds its own `Framebuffer` (the `Framebuffer` object `1854149592`) — `identityPairs` routes every non-main identity to `chain-pair` → the HUD draws into `0x37` → `frameImage` (which `mainIdentity`/identity-0 draws into) has only the world → the composite (the `0x37`→`frameImage` copy) never runs → "no screen". **The fix**: either (a) run the `0x37`→`frameImage` composite after the GUI (the published-image copy the game expects), or (b) route the GUI-stage `worldPass=false` groups under a non-main framebuffer to `frameImage` — the HUD's framebuffer isn't the main, so the HUD needs the composite to reach `frameImage`. **Round-154 — `syncPublishedChainOutput` runs only on the chain-identity branch**: `:11078` — "it already existed (`celtium$syncPublishedChainOutput`), but it only ran on the chain-identity branch" — the `0x37`→`frameImage` sync runs **only when the draw's identity is the chain** → `1854149592` is a non-main **non-chain** identity (one of the 5 non-main identities → `chain-pair`/`0x37` per `perPair=[frame-image=1,chain-pair=5,default-pair=1]`) → the HUD's draws into `0x37` → **the sync doesn't run for `1854149592`** → `0x37`'s HUD never synced to `frameImage` → "no screen". `1854149592` isn't the outline-chain's framebuffer — it's the game's own GUI/screen framebuffer → `identityPairs` routes it to `chain-pair`/`0x37` → the sync (gated on the chain identity) misses it → `0x37`'s content (the HUD) never reaches `frameImage` → "no screen". **The fix**: `syncPublishedChainOutput` (the `0x37`→`frameImage` copy) must run for **every non-main identity that draws into `0x37`** — the chain-identity gate misses the game's own GUI framebuffers that share `chain-pair` — i.e. the sync belongs to "the pair's image changed", not "the chain's identity drew". **Round-154 correction — `syncPublishedChainOutput` publishes the *name*, not a copy; the composite is the game's own `framebufferRender` which never runs**: `syncPublishedChainOutput(offTarget.chainPairColorImage())` at `:7794` runs at `ensureChainPair` — it publishes `0x37`'s image under a GL name for the composite to sample — it doesn't copy `0x37`→`frameImage`. The actual `0x37`→`frameImage` copy is the **game's own finished-frame composite** (`Framebuffer.framebufferRender` — a full-screen quad sampling the framebuffer's texture `0x37` → `frameImage`) → `frameImageCompositeSkips=0` → **that composite never queued/ran** → `frameImage` lacks `0x37`'s HUD → "no screen". **So the defect isn't a gated sync — it's the game's own `framebufferRender` composite never being invoked** — `1854149592`'s `Framebuffer` drew the HUD into `0x37` but `framebufferRender` (which would composite `0x37`→`frameImage`) never queued → `frameImage` lacks `0x37`'s content → "no screen". The narrowing: `1854149592` → `chain-pair`/`0x37` → the HUD into `0x37` → `0x37` published (`syncPublishedChainOutput`) → but the game's composite quad never draws → `frameImage` lacks `0x37`'s HUD → "no screen". Next measurement: whether `1854149592`'s `framebufferRender`/`framebufferRenderExt` is invoked at the world frames (a `CELTIUM_GROUP_BOUNDS` for the published-image composite group — the quad sampling `publishedChainTexture` → `frameImage`). **Round-156 — the composite DOES run: `group=1659 fmt=2 worldPass=false target=0 vertices=4`** — `framebufferRender` draws a 4-vertex `POSITION_TEX_COLOR` quad (web-verified) → `group=1659` IS the composite → `target=0` (main → `frameImage`) → `texture=3` → **the `0x37`→`frameImage` composite RUNS** → `frameImage` gets `0x37`'s content → but `hotbarBand` shows the world → `0x37`'s content IS the world → **the HUD's draws into `0x37` come AFTER the composite copied `0x37`→`frameImage`** — `group=1659` (composite) at `frame=93`, `group=1668/1673/...` (the HUD) at `frame=94` → **different frames** → `frameImage` at frame-93 has `0x37`'s frame-93 content (the world, no HUD) → the frame-94 HUD into `0x37` → `frameImage` lacks the frame-94 HUD → "no screen". **The defect is ORDERING**: the composite (`0x37`→`frameImage`) copies `0x37` before the HUD's draws into `0x37` land — the HUD's `worldPass=false` groups into `1854149592`/`0x37` come after the composite's copy → `frameImage` has `0x37`'s world but not the HUD → "no screen". The composite must run AFTER the HUD's draws into `0x37` — or the HUD's draws must precede the composite's `0x37`→`frameImage` copy in the same frame. **Round-157 — the ordering confirmed**: `frame=40-44` shows the pattern — `group=601 fmt=2 target=1854149592 vertices=4` (a quad into `0x37`) then `group=605 target=0 vertices=4` (**the composite `0x37`→`frameImage`**) — same frame, composite AFTER a quad-into-`0x37` → the composite copies `0x37`→`frameImage` → `frameImage` has `0x37`'s content. **The HUD's actual draws are `group=1668/1673/1675/1677/1679 vertices=356/668/596` — the larger batches into `0x37`** → these come AFTER the composite (`group=605`) in the frame → `frameImage` has `0x37`'s pre-HUD content (the world) → `hotbarBand` reads the world → "no screen". **The defect is confirmed ordering**: the `0x37`→`frameImage` composite (`vertices=4` quad, `target=0`) copies `0x37` **before the HUD's larger batches into `0x37` land** — the composite's copy sees `0x37` without the HUD → `frameImage` lacks the HUD → "no screen". The composite must run AFTER the HUD's `worldPass=false` batches into `0x37`. **Round-158 — `frame=90`: the HUD's draws DO precede the composite — `0x37` lacks the HUD anyway**: world (`1503/1504` `worldPass=true`) → `0x37`, a quad (`1508` `texture=43`) → `0x37`, **the HUD's larger batches (`1513/1515/1517/1519/1521/1523/1525/1527/1529/1532/1536` `worldPass=false texture=9 vertices=190-704`) → `0x37`**, then the composite (`1539` `target=0`) → `frameImage`. **The composite runs AFTER the HUD's draws into `0x37`** → `0x37` should hold the world+HUD → `frameImage` has it → but `hotbarBand` reads the world → **`0x37` lacks the HUD** → the HUD's draws into `0x37` are **refused or `0x37` is re-cleared between the HUD's draws and the composite** — `pairClearNeeded` (`:7800`) or a per-window `ensureChainPair`-clear erases the HUD's draws → `0x37` holds only the world → `frameImage` lacks the HUD → "no screen". **Not an ordering defect — a refused-or-cleared HUD draw**: the HUD's `worldPass=false` batches into `1854149592`/`0x37` either (a) fail to land (a VUID/refusal on `0x37`), or (b) land but `0x37` is re-cleared (`pairClearNeeded`/`ensureChainPair` clear) before the composite samples it → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **Round-159 — `pairClearNeeded` REFUSES the re-clear, so `0x37` keeps the world; the HUD's draws into `0x37` are VUID-refused**: `celtium$pairClearNeeded:10033-10052` — `contentIdentity != currentIdentity` → `notePairClearsRefusedOtherIdentity` + `return false` → **the pair's re-clear is REFUSED** → `0x37` keeps the world's content → the HUD's `worldPass=false` batches into `1854149592`/`0x37` → **`0x37` lacks the HUD** → the HUD's draws into `0x37` are **refused, not cleared** — a VUID on `0x37` (`0x37` in SHADER_READ_ONLY after the previous composite's sample → the HUD's draws attach it with `initialLayout GENERAL` → VUID-00900, or the HUD's sampled draws on `0x37` while in GENERAL → VUID-09600) → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **The two VUID directions on `0x37` are the same lifecycle**: `0x37` is the shared chain-pair — the world draws into it, the previous composite samples it (SHADER_READ_ONLY), the HUD's draws attach it (`initialLayout GENERAL`) → `0x37`'s layout conflict → the HUD's draws refused → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". `pairClearNeeded`'s refusal keeps the world (not the HUD) → `0x37` has the world → `frameImage` has the world → `hotbarBand` reads the world → "no screen". **Round-160 — `r212-world-hud5`: `0x37` ×55 but only `VUID-09600` ×2** → the HUD's draws into `0x37` **don't produce VUIDs** → `0x37` lacks the HUD → the HUD's draws into `0x37` **aren't VUID-refused** → `0x37` lacks the HUD → the HUD's draws into `0x37` don't land → a **non-VUID refusal or `0x37` re-cleared or a different `0x37` region**. `pairClearNeeded` refuses the re-clear (`contentIdentity != currentIdentity` → `return false`) → `0x37` keeps the world → the HUD's draws into `0x37` land on top → `0x37` has the world+HUD → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → `0x37` lacks the HUD → the HUD's draws into `0x37` don't land → **a silent refusal (not a VUID) — the HUD's `worldPass=false` batches into `1854149592`/`0x37` are dropped before they reach `0x37`** — a group-queue drop (`immediateGroupsKeptForOtherTargets`/`immediateDeferredNextFrame`) or a pass that's never recorded for `1854149592`'s draws → `0x37` lacks the HUD → "no screen". **Round-161 — the HUD's draws DO land (`immediateGroupsDrawn=1895/4898`), so `0x37` is re-cleared or recreated between the HUD's draws and the composite**: `immediateGroupsKeptForOtherTargets=0 immediateDeferredNextFrame=0 postAcceptDroppedGroups=0 immediateGroupsDrawn=1895/4898` → the HUD's `worldPass=false` batches into `1854149592`/`0x37` DO draw → `0x37` has the HUD → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → **`0x37` is re-cleared or recreated between the HUD's draws and the composite's sample** → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". `pruneStalePublishedNames:11086` — "the pair has been released (teardown, or the release half of a re-creation)" → `0x37` recreated at a size change or a re-creation → the HUD into the OLD `0x37` → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **The defect is `0x37` re-creation/re-clear between the HUD's draws and the composite** — `chainPair` recreated (or `pairClearNeeded` allows a clear on a different frame-serial) → the HUD's draws into the old `0x37` → the composite samples the new `0x37` (world only) → `frameImage` lacks the HUD → "no screen". **Round-162 — `0x37` is cleared per frame (`CELTIUM_CHAIN_CLEAR`/`CELTIUM_CHAIN_PAIR_CLEAR` at `frame=0` `site=first-non-main-pass`/`immediate-only-window`), and `pairClearNeeded` returns `true` (`pairClearedFrame != frameSerial`) → `0x37` re-cleared per frame** → the HUD's `worldPass=false` batches into `1854149592`/`0x37` draw → `0x37` has the HUD → **but `0x37`'s per-frame clear erases it** → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". `CELTIUM_CHAIN_CLEAR frame=0 image=0x370000000037 target=1126602797` — `0x37` cleared at frame-0 under `1126602797` (a non-main identity) → `pairClearNeeded` at the world frames → `pairClearedFrame(0) != frameSerial(94)` → `return true` → `0x37` re-cleared at frame-94 → **the per-frame clear erases the HUD's draws into `0x37`** → `0x37` has only what drew AFTER the clear (the world) → `frameImage` has the world → `hotbarBand` reads the world → "no screen". **The defect is `0x37`'s per-frame clear ordering**: `0x37` is cleared once per frame (`pairClearNeeded` → `pairClearedFrame != frameSerial` → `return true`) — if the HUD's draws into `0x37` come BEFORE `0x37`'s per-frame clear (a window ordering — the HUD's window draws into `0x37`, then `0x37`'s clear runs for the next identity) → the clear erases the HUD → `0x37` has only the world → `frameImage` lacks the HUD → "no screen". **Round-163 — `0x37` is NOT re-cleared or recreated at the world frames (`CELTIUM_CHAIN_CLEAR`/`CELTIUM_CHAIN_PAIR_CLEAR` ×3 at `frame=0` only, no `ensureChainPair`/`pruneStale`/`pairReleased` at the world frames) — `0x37`'s image does not receive the HUD's draws**: `CELTIUM_CHAIN_CLEAR`/`CELTIUM_CHAIN_PAIR_CLEAR` ×3 at `frame=0` `site=first-non-main-pass`/`immediate-only-window` → `0x37` cleared once at frame-0 → **no clear at the world frames** → `0x37` not re-cleared → `0x37` lacks the HUD → the HUD's draws into `0x37` don't land — but `immediateGroupsDrawn=1895` → the HUD's draws DO draw → `0x37` lacks the HUD → **`0x37`'s image doesn't receive the HUD's draws** — `0x37` is the chainPair's image (color+depth) → the HUD's `worldPass=false` batches into `1854149592` → `1854149592`→`0x37` → the HUD's draws into `0x37` → `0x37` has the HUD → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → `0x37` lacks the HUD → **the HUD's draws into `0x37` go to a different image, or `0x37`'s image doesn't receive them**. `CELTIUM_SPLASH_PAIRPOST present=1 pairImage=0x370000000037 centreRgb=(227,227,227)` — `0x37` is sampled at present — `0x37` has the splash → the world frames' `0x37` → `0x37` has the world → the HUD's draws into `0x37` → `0x37` has the HUD → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → `0x37` lacks the HUD → the HUD's draws into `0x37` go to a different image — `0x37` is the chainPair (a Pair: color + depth) → the HUD's draws into `0x37`'s color → `0x37`'s color has the HUD → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → `0x37`'s color lacks the HUD → the HUD's draws into `0x37`'s color don't reach it — **the HUD's `worldPass=false` batches into `1854149592`/`0x37` draw into `0x37`'s DEPTH or a different attachment, or `0x37`'s color image doesn't receive them** → `0x37`'s color lacks the HUD → `frameImage` lacks it → "no screen". **Round-164 — `0x37` IS `chainPair.colorImage` (`Pair` has `colorImage` + `depthImage`; `chainPair = new Pair("chain")` at `GllessFrameTarget:146`) → `1854149592`'s draws into `chainPair` go to `chainPair.colorImage` (`0x37`) — so `0x37` SHOULD receive the HUD**: `Pair.colorImage:102` + `Pair.depthImage:105` → `1854149592`→`chainPair` (`identityPairs`) → `1854149592`'s draws into `chainPair` → `chainPair.colorImage` (`0x37`) + `chainPair.depthImage` → `0x37` has the HUD's color → `frameImage` has it → `hotbarBand` reads the world → `frameImage` lacks the HUD → `0x37` lacks the HUD → **`1854149592`'s draws into `0x37` land on a DIFFERENT image — `0x37` is recreated (`ensureChainPair` at a size change → `chainPair.colorImage` is a new image → the HUD's draws into the old `0x37` → the new `0x37` lacks the HUD), OR `1854149592`'s draws go to `chainPair.depthImage` (the pair's depth)** → `0x37` (color) lacks the HUD → `frameImage` lacks it → "no screen". `ensureChainPair` creates the pair at a size — a `1854149592` size change (the HUD's framebuffer resizes) → `chainPair` recreated → `0x37` recreated → the HUD's draws into the old `0x37` → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **Round-165 — `Pair.ensure` "replaced when that size changes" (`:1244-1253` `matches(w,h)` → `return`, else `release()` + `allocateImage`) — and `chainPair` is shared by ALL non-main identities (`pairLabelForIdentity`: identity 0→default, every other non-main→chain-pair), so a DIFFERENT-SIZED non-main identity's `ensureChainPair` recreates `chainPair` → `0x37` recreated → the HUD's draws into the OLD `0x37` → the NEW `0x37` lacks the HUD → `frameImage` lacks it → "no screen"**: `chainPair.ensure(width, height)` — `chainPair`'s size is the caller's → `1854149592`'s `ensureChainPair` → `chainPair` at `1854149592`'s size → a DIFFERENT non-main identity (a different-sized one) `ensureChainPair` → `matches` fails → `release()` + `allocateImage` → **`chainPair` recreated → `0x37` is a new image** → `1854149592`'s draws into the OLD `0x37` → the NEW `0x37` lacks the HUD → `frameImage` lacks it → "no screen". The world frame's non-main identities — the entity-outline, the panorama's `mc$framebuffer`/`mc$entityOutlineFramebuffer`, the GUI's — share `chainPair` → **each different-sized identity's `ensureChainPair` recreates `chainPair`** → `0x37` thrashes between sizes → the HUD's draws into `0x37` land on whichever `0x37` was current → the composite samples the CURRENT `0x37` → if `0x37` was recreated after the HUD's draws → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **`chainPair` being shared by all non-main identities means a size change on ANY of them recreates `0x37` — the HUD's `1854149592` (a window-sized framebuffer) draws into `0x37`, then a different-sized identity's `ensureChainPair` recreates `0x37` → the composite samples the new `0x37` (world only) → `frameImage` lacks the HUD → "no screen"**. **Round-166 — `chainPair` receives THREE non-main identities (`1854149592` ×346, `1126602797` ×1, `1859471502` ×4) → a different-sized one's `ensureChainPair` recreates `chainPair` → `0x37` recreated → the HUD's draws into the old `0x37` → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen"**: `CELTIUM_GROUP target=` census — `1854149592` ×346 (the HUD), `1126602797` ×1, `1859471502` ×4 — three non-main identities share `chainPair` (`pairLabelForIdentity`: every non-main→chain-pair) → `chainPair.ensure(size)` recreates on a size change → **`1859471502`'s `ensureChainPair` (a different-sized identity — the entity-outline or a GUI framebuffer) recreates `chainPair` → `0x37` recreated** → `1854149592`'s HUD draws into the OLD `0x37` → the NEW `0x37` lacks the HUD → `frameImage` lacks it → "no screen". The web's three.js `_sharedDepthbuffer` issue is the exact same defect — "a shared buffer across different-sized references is brute-force destroyed and re-created per frame" → `chainPair` shared by `1854149592`/`1126602797`/`1859471502` → a different-sized identity's `ensureChainPair` recreates `chainPair` → `0x37` thrashes → the HUD's draws into `0x37` land on a recreated `0x37` → `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **Round-167 — `1859471502` is a 427×240 framebuffer (`v={0.0,240.0...427.0,240.0...}`) → `chainPair` thrashes between `1854149592`'s 1280×720 and `1859471502`'s 427×240 → `0x37` recreated per alternation → the HUD's draws into the old `0x37` → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen"**: `1859471502`'s draw quad is `v={0.0,240.0...427.0,240.0...427.0,0.0...0.0,0.0}` — a **427×240** framebuffer → `1859471502`→`chainPair` → `chainPair.ensure(427, 240)` → `chainPair` recreated at 427×240 → `0x37` is a new 427×240 image → `1854149592` (1280×720) draws into the OLD `0x37` → the NEW `0x37` lacks the HUD → `frameImage` lacks it → "no screen". `chainPair.ensure` recreates on a size change → `1854149592` (1280×720) → `chainPair` at 1280×720 → `1859471502` (427×240) → `chainPair` at 427×240 → `1854149592` → `chainPair` at 1280×720 → **`chainPair` thrashes between 1280×720 and 427×240 → `0x37` recreated per alternation** → the HUD's draws into `0x37` → `0x37` has the HUD → `1859471502`'s `ensureChainPair` → `0x37` recreated at 427×240 → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen". **The defect is `chainPair`'s shared-across-different-sized-identities size thrash**: `1854149592` (HUD, 1280×720) + `1859471502` (a 427×240 framebuffer — the entity-outline's downsampled silhouette) + `1126602797` share `chainPair` → `1859471502`'s `ensureChainPair` recreates `chainPair` at 427×240 → `0x37` recreated → the HUD's draws into the old `0x37` → the new `0x37` lacks the HUD → `frameImage` lacks it → "no screen". The composite is enqueued **AFTER the first present** → the first presented frame is the frame's own white clear (composite draws at 03:36:15, present at 03:36:08) → the splash's one presented frame is white → the "white splash". The composite path is proven sound (magenta: pair→frame correctly). **The real content defects**: the Cleanroom logo is **horizontally mirrored** and the panorama is a **256×256 tile in the top-left** not full-screen — a flip + scale defect in the game's own draw path (`framebufferRenderExt`'s quad is `pos(0,h)→pos(w,0)`+`tex(0,0)→tex(f2,f3)` unmirrored → the flip is ours; the panorama's scale points at the same transform). The whole "composite reads white" arc (rounds 60-106) is refuted — the `PAIR_PROBE_CLEAR` magenta fill proved the composite samples the pair correctly (magenta→frame); the white was the *first* present happening before the composite drew, not a content loss. `mirrorSource=announced-int[]@256x256` (`splash13`) — the panorama's published source is a 256×256 announced image drawn into a bigger framebuffer → the 256×256 tile. The two transform symptoms — logo mirrored, panorama mis-scaled — point at one projection/UV defect in the game's own immediate-draw path (both source quads are unmirrored in mc-sources → the defect is ours). `splash13` vertex dump: `drawnSoFar=0` = the splash's white background quad (full-screen positions `0,1080→1920,0`, **all-UV-0** — samples one texel → white background), `drawnSoFar=1` = the composite (`0,1080→1920,0` + UV `0→1`), `drawnSoFar=2,3` = NDC quads. The logo's quad (verts 4-7 of `count=8`, `splashverts`): `112,263,u=0,v=1 / 368,263,u=1,v=1 / 368,7,u=1,v=0 / 112,7,u=0,v=0` — **u increases with x** → the logo's *geometry* is unmirrored. So the mirror is in the **texture** — the logo's `announced-int[]` pixel data is x-flipped at upload. Both defects (mirrored logo + 256px panorama, `announced-int[]@256x256`) share the `announced`/texture-upload path: the upload flips x and mis-scales — one announced-image bug, not a projection/UV defect. `registry.announceFullPixels(destName, levels, w, h, xoff, yoff)` (`TextureCopyService:646`) is the announced-pixel path — the game's texture data → the mirror → the texture. The x-flip is in how `announceFullPixels` writes the announced `int[]` to the texture (row order / x-direction), and the 256×256 panorama is the announced image's own size (the game's panorama texture is a 256×256 tile, drawn expecting full-screen → mis-scaled). **Round-114 — `a81ba00b8` exonerates the captured geometry; the mirror is "the mirror image's build, the texture-matrix push or the sampler"**; `wrapRepeats` is a panorama-scale candidate. The logo's quad has u rising with x (matches `Minecraft.draw` element-for-element) → the mirror is downstream of the captured vertices. `toRgba`/`readImageRectangle` are x-correct → the candidates narrow to (a) the mirror-image's build — the *announced source* `int[]` is x-flipped (the logo's sprite/framebuffer content itself), or (b) the sampler — `wrapRepeats` (`VulkanTextureRegistry:1921`) returns false (CLAMP) under the stub when nothing recorded the wrap → a CLAMP sampler on a REPEAT-expecting tile texture (the panorama's `GuiOptions.drawBackground`/`options_background.png` tiles under REPEAT) → **doesn't tile → a 256×256 tile in the corner** — exactly the panorama defect. So the two defects likely split: the logo's mirror in the announced source `int[]`/sampler-x, the panorama's 256×256-tile in `wrapRepeats` returning CLAMP instead of REPEAT for a texture the game expects to tile. The discriminating read is `VulkanGlTexture`'s sampler wrap-mode for the panorama's `announced-int[]@256x256` texture (vs its `WRAP_S/T` the game set). **Round-115 instrument**: `dumpMirrorPixels(this.encodeLabel)` (uncommitted +5 in `VulkanGlTexture:1840`) — names the mirror dumps by texture so the logo's mirror-image can be found among the 64 256×256 canvases → reads whether the logo's *texture content* is x-flipped → if flipped, the announced source `int[]` itself is x-flipped (the game's `glTexImage2D` data or the framebuffer-readback that produced it), not the upload path. The logo's `texture=8` has `mirrorSource=announcement@256x256` → `dumpMirrorPixels` discriminates: **unmirrored texture → the flip is the draw/sampler** (the source-draw's `ortho` x-sign — `proj=` prints only the z-column so m00 is unverified — or the texture-matrix); **mirrored texture → the announced `int[]` source** is x-flipped upstream of `toRgba`. **Round-117 verdict — the texture is UNMIRRORED** (`mirror-gl8-256x256-l0` shows "MOJANG" reading correctly) → the flip is **in the draw**, not the texture, not the announce path, not the captured vertices. The one remaining unverified transform is the source-draw's `ortho` **x-sign (m00)** — `proj=` prints only `projection[2],[6],[10],[14]` (the z-column) → an `ortho(r,l)` (left>right → negative m00 → x-mirror) is invisible to it. The discriminating probe is `projection[0]` (m00) on the splash's own group — or the group's `matrixMode`/`glGetFloatv` live-read — a negative m00 confirms the x-mirror; the alternative remaining candidate is the texture-matrix (`textureMatrix(0)` — a `glScalef(-1)` on GL_TEXTURE would flip u for every vertex that uses it). **Round-118 — every transform x-correct → the flip is in the copy chain**: `projDiag=0.004,-0.007 texMat=none` (the logo's source-draw — positive m00, y-down, no texture-matrix) and `ORTHO l=0 r=480/1920 b=270/1080 t=0` (all `l<r` → positive m00 → no ortho x-flips). So the flip is **not** the captured vertices (u rising), **not** the texture (`mirror-gl8` unmirrored "MOJANG"), **not** the announce path (`toRgba`/`readImageRectangle` row-major), **not** any ortho (all l<r), **not** a texture-matrix (none) — the one place left is a **copy/blit in the framebuffer→pair→frame chain**: the splash renders at 480×270 (`ORTHO l=0 r=480`), its framebuffer is copied/scaled into the pair (1920×1080), the composite samples the pair — an x-flip in the framebuffer→pair copy (`celtium$syncPublishedChainOutput`/`TextureCopyService`'s image-copy or a `glBlitFramebuffer` with swapped x) mirrors the whole framebuffer — logo + panorama — which also explains the panorama's 256×256 tile (a sub-region copy that only moves part of the source). **Round-119 — the scale mismatch**: `ORTHO l=0 r=480 b=270` (the splash's own draw ortho is **480×270**), `viewport=262400` (**640×410** GL viewport), the panorama's `resolvedSize=256x256`, the pair's `resolvedSize=1920x1080` — the splash's draws render at ~480×270 into a **1920×1080** pair → the content lands in a ~480×270 *region* (a corner), the composite samples the *full* pair → **a corner tile**. So both defects share the draw-into-a-bigger-target mis-mapping: the splash's ortho/viewport is sized for a smaller framebuffer than the pair it draws into — the panorama's `announced@256x256` is a 256×256 readback of a framebuffer the game expected to fill but only filled a corner of, and the logo lands in the corner with it (its `112-368` coords are inside the 480×270 ortho). The x-mirror then could be the same region-mapping in x (a copy/draw that maps the 480-wide source onto the pair with reversed src/dst x) — or a region-copy whose dst offset lands the content in the corner. The discriminating read is the **viewport the splash's pass applies** (`glViewport` w×h vs the attached image's size) and the framebuffer→pair region — a 480×270 draw into a 1920×1080 attachment with a 0,0-origin viewport lands top-left or bottom-left. **Round-121 — `viewport` is `w<<10|h`-packed → the splash's draws use a 256×256 viewport**: `viewport=262400` = `256<<10|256` = **256×256**, `viewport=1967160` = `1920<<10|1080` = **1920×1080** — so the splash's `framebufferRender`/`Framebuffer` draws use **`glViewport(0,0,256,256)`** (the splash's `Framebuffer` declares `framebufferWidth/Height = 256×256`) while the GL-less attachment is **1920×1080**. The composite's full-screen quad then lands in a **256×256 corner region** — the panorama's `announced@256x256` (its 256×256 texture) + the logo inside it → the "corner panorama as a 256×256 tile" + the surrounding white/empty. The FBO declares 256×256 because under the GL-less window `ScaledResolution`/`Display` reports a 256×256 (or the splash's `Framebuffer` is created `256×256`) — the FBO's declared `framebufferTextureWidth/Height` governs both `framebufferRender`'s `glViewport` and the GL-less image's `resolvedSize` → a 256×256 FBO drawn into a 1920×1080 target → the corner tile. The fix is one of: (a) the `Framebuffer`/`ScaledResolution` declaring the real 1920×1080 under GL-less, or (b) the GL-less target sizing its image to the declared 256×256 and the composite scaling it up — currently the target is 1920×1080 and the draw only fills a 256×256 corner. The x-mirror is a narrower secondary defect on the same region path. **Round-122 — the panorama's texture itself is ROTATED 90°**: `mirror-gl21-256x256` (the panorama's texture, `glTexture=21`) shows the terrain **rotated** — water runs vertically, the scene on its side — so the panorama's cube-face render writes the texture *rotated*, not merely cornered. This is a **90° rotation / transpose** in the panorama's face-render (the per-face camera's projection, or the framebuffer→texture region-mapping doing an x/y swap): a 90° rotate is `(x,y) → (y,x)` — which ALSO reads as a horizontal mirror on a symmetric texture (the logo's "MOJANG" x-mirrored) — so the corner-panorama + mirrored-logo may be **one transpose defect** on the framebuffer→texture/region path, not two. The unmirrored `mirror-gl8` (logo texture correct) vs the rotated `mirror-gl21` (panorama texture rotated) is the puzzle — different source paths: the logo's texture is *announced* (glTexImage2D data — correct), the panorama's texture is *rendered into* (the cube-face render — rotated) → the transpose is in the **cube-face draw's projection/camera** (`gluPerspective`/`glOrtho` for the face views under GL-less), not the upload. **Round-122b — `multiplyMatrix` is correct column-major (no transpose)**: `glMultMatrixf` is arrival-only (`GluProjectionMixin:51` → `mirrorGluPerspective` → `perspective:3841` builds a correct column-major frustum `m[0]=f/aspect, m[5]=f, m[10], m[11]=-1, m[14]`) → `VulkanGlState.multiply` (`:805`) is standard column-major `left·right` → **the frustum multiply is sound** → the panorama's 90° rotation is **not** a projection transpose. What remains: the face's *camera modelview* (`glRotatef`/`gluLookAt` per cube-face — `GuiMainMenu.drawPanorama`/`RenderMinecraft`'s per-face rotation), or the framebuffer→texture region's orientation. The 256×256-into-1920×1080 corner-tile is the separate `glViewport`/`resolvedSize` defect. **Round-123 — `renderSkybox` is vanilla-correct; the rotation is in `drawPanorama`'s face-camera**: mc-sources `GuiMainMenu.renderSkybox` does `glViewport(0,0,256,256)` → `drawPanorama` (the cube at 256×256 by design) → `glCopyTexSubImage2D(0,0,0,0,256,256)` → `viewportTexture` → drawn full-screen. So the 256×256 is **correct** (vanilla renders the panorama at 256×256 and upscales) — the `SEM_COPY_TEX_SUB_IMAGE`/`readImageRectangle`/`announceFullPixels` copy is row-major (no rotate) → the copied `viewportTexture`'s rotation (`mirror-gl21` rotated 90°) means `drawPanorama`'s **face-camera rendered the framebuffer rotated** — `glRotatef(180,1,0,0)`+`glRotatef(90,0,0,1)`+per-face `glRotatef` + `gluPerspective(120,1.0)`. `glRotatef`/`GlStateManager.rotate` is in `MIRROR_REQUIRED` (mirrored via the `GlStateManager` mixin → `multiplyMatrix`, correct) → the remaining unmirrored candidates: a **raw `GL11.glRotatef`/`glRotated`/`glTranslatef`** (FML's `SplashProgress` calls GL11 directly, bypassing `GlStateManager` and its mirror hook) or the `gluPerspective`'s own modelview setup. The face-camera's rotation being applied wrongly/absent → the cube-face rendered rotated → the copied `viewportTexture` rotated. **Round-123b — `glCopyTexSubImage2D` SERVES → the corner tile is a bind/region defect, not a copy-loss**: `TEX_COPY_SERVED dest=21 source=0,0 256x256 readFbo=0 served=80 opaque=65536` — the panorama's `rotateAndBlurSkybox` copy **succeeds** (`viewportTexture`=gl21 gets the 256×256). `rotate`'s matrix (`VulkanGlState:178`) is the correct column-major `glRotatef` build → the face-camera is correct → `mirror-gl21`'s "rotation" is a **side face's own orientation**, not a defect. So the real defect is the **framebuffer-bind/region path**: `renderSkybox` does `unbindFramebuffer()` → `glViewport(0,0,256,256)` → renders the cube **into the pair's 0,0,256×256 corner** → `glCopyTexSubImage2D` (served) → `bindFramebuffer(true)` → `glViewport(0,0,displayW,displayH)` → draws `viewportTexture` full-screen **into the main FBO** → which must sync to the pair. **If the main-FBO→pair republish (`celtium$syncPublishedChainOutput`) or the `bindFramebuffer` mapping fails, the pair keeps the panorama's raw 256×256 corner render → presented → a 256×256 corner tile + the rest white** — matching all symptoms. The discriminating read: whether the `bindFramebuffer`→main-FBO→pair draw lands in the pair (the `viewportTexture` full-screen draw overwrites the corner) vs the pair keeping the `unbindFramebuffer`-era corner render. |

---

## Confirmed defect 1 — `celtium$setGroupViewport` y-conversion (menu panorama)

> **RESOLVED (round 48, `be3ade744`)**: the panorama's root cause was
> `matrixScratch` contamination — `perspective()` (the glFrustum builder)
> wrote only `{0,5,10,11,14}` of the shared scratch, so a `glOrtho` that
> ran first left its `{12,13,15}` = `(-1,+1,1.0)` in the frustum →
> `w_clip = -x+y-0.101z+1` → the cube projected outside the frustum
> (0.8% box coverage). Fix: zero-fill the scratch in the glFrustum
> builder; `m33=0`, `w=-z`, cube repaint 13.1%/frame, menu distinct
> 26176→113116 — verified `r212-panzeroF-frustumonly`. Minor residuals:
> ~90×90 sharp thumbnail at bottom-left + ~40px left-edge text clip.
>
> **Status (round 28): every site documented below is now FIXED and committed.**
> `6e75884c1` viewport y · `640a1984a` alpha=1 · `f30344754` copy ordering ·
> `5d54adfea` the `:677` host-readback offset. `57a40e82c` measures the
> residual: the panorama composite now covers **1486×934 of 1920×1080** —
> content present but the stretch quad covers a sub-rect (top 144 rows + right
> 434 columns empty).
>
> **Plumbing map for the suspect site (rounds 29–30), confirmed against the
> [LWJGLXX source](https://raw.githubusercontent.com/CleanroomMC/lwjglxx/master/src/main/java/org/lwjglx/opengl/Display.java):**
> `Display.getWidth()` returns `displayFramebufferWidth`, updated by its
> `framebufferSizeCallback` — **but `wasResized()` is fed only by
> `windowSizeCallback`** (`latestResized` in `processMessages`). So a
> framebuffer resize *without* a window resize — exactly what a compositor
> output reconfigure or a fractional-scale change does — updates
> `Display.getWidth` yet never fires `Minecraft.resize` → `mc.displayWidth`
> keeps the older, smaller size → `glViewport(0,0,1486,934)` covers a
> bottom-left sub-rect of the 1920×1080 swapchain image. The swapchain itself
> follows `glfwGetFramebufferSize` at **present** (`:9565`,
> `matchesSurfaceExtent` rebuilds on change), so the image stays 1920×1080
> while the game's viewports stay 1486×934 — the measured hole. Fix shape:
> `LwjglxDisplayMixin` (already the GL-less window half, targeting the same
> `org.lwjgl.opengl.Display`) either (a) redirects `wasResized` to also fire
> when `displayFramebufferWidth/Height` changed since the last poll, or (b)
> makes `getWidth/Height` answer live `glfwGetFramebufferSize`. (a) is the
> smaller semantic match — vanilla LWJGL2's `wasResized` covered both, since
> window and drawable were the same rectangle there.
>
> **Round 32 refinement — the sub-rect is the *projection/vertex* side, not
> the viewport.** The `r212-panhost-steady3` leg (1920×1080) logs only
> `gl=0,0 1920x1080` and `gl=0,0 256x256` — **no 1486×934 viewport ever
> arrives** — yet the presented menu shows the GUI squeezed into ~77%×86%
> (logo/buttons compressed) with the panorama backdrop as a ~400×300
> bottom-left fragment (~ a 256×256-space quad under a display-sized ortho).
> A group stamps BOTH `currentGroupViewportValues` (what GROUP_VIEWPORT
> logs) and `currentGroupProjection` (`:5530`, the ortho — invisible to that
> instrument). The join test *does* split on `glProjection` change
> (`:5447`), so a stale-ortho group can't absorb a fresh-ortho batch — **but
> only if `glProjection` refreshed**: the mirror's `glProjection` is set by
> `glOrtho` calls, which read `ScaledResolution`/`mc.displayWidth` at call
> time. If the blur-pass ortho (256×256-ish GUI space) stayed in the mirror
> when the composite quad appended — the game's own `glOrtho` refresh being
> the thing that never arrived — the group's stamped projection is the
> stale one *and* `gl=` still logs the honest 1920×1080 viewport.
> Discriminator: dump `group.projection[0]`/`[5]` + the quad's own pos span
> for the composite batch — `2/256`/`2/1486`/`2/1920` names which ortho
> actually drove it. (The `wasResized` gap below remains the upstream
> suspect for why `mc.displayWidth`-derived state could be stale anywhere.)
>
> **Measured layout, `r212-panhost-steady3` menu frame (1920×1080):**
> white band y≲460 (logo+buttons compressed into the top ~40%), panorama
> fragment x∈[0,1350]×y∈[468,1050] bottom-left, flat grey elsewhere. The
> panorama coverage ≈ a group viewport of ~(0,0,1350,615) bottom-up — a
> value that appears nowhere in the GROUP_VIEWPORT log (only 1920×1080 and
> 256×256) — so the rect lives in the *unlogged* half of the group stamp:
> `group.projection` or the pre-transformed vertices themselves. The log
> also shows `glOrtho` called 1234× with only two shapes — `r=1920` ×2 and
> `r=480` (GUI scale 4) the rest — so a group stamped with an ortho that no
> longer matches the call stream is exactly what the discriminator above
> would reveal.
>
> **Round 34 — the cleaner decode (verified against
> [GuiMainMenu source](https://raw.githubusercontent.com/Awe23123/Optifine-1.12-SRC/master/src/net/minecraft/client/gui/GuiMainMenu.java)):
> the backdrop quad IS fullscreen** — `pos(0,l)-(k,0)` spans the whole
> 480×270 GUI space under `glViewport(0,0,1920,1080)`. So the fragment is
> NOT geometry coverage: the backdrop samples `backgroundTexture`'s center
> window (u∈[0.24,0.76] along y, v∈[0.03,0.97] along x — note vanilla maps
> u→screen-Y, v→screen-X, a 90°-rotated sample) — and *the texture's cube
> content only occupies part of that window*. White band = texture regions
> that read white (unwritten/empty), blue fragment = where the cube actually
> landed in the 256 mirror, grey = the dirt quad showing through where the
> sample is transparent. The defect is upstream of the composite: the cube's
> draws populate only part of the copy region (the cube's own 256×256
> viewport'd draws into the frame, or the copy's read rect, are partial).
> New discriminator: dump the *texture mirror* (the DynamicTexture's
> staging) after `rotateAndBlurSkybox` — if the cube occupies only a
> sub-rect there, the failure is in `windowDrawsToFrameImage`-era routing of
> the cube's draws, not the composite at all.
>
> **Round 35 — the copy read is proven good; the loss is the mirror WRITE or
> the binding.** The leg's `textureCopySamples` line:
> `opaque=26294/65536 bands=93/98/125 90/94/111 95/99/116 106/113/137
> 117/125/140 99/111/137 105/118/153 104/117/178` — **all eight 32-row
> bands hold blue cube content** (the 40% opaque count is just the
> `colorMask(rgb,alpha=false)` writes, expected). The read rect is fully
> populated — yet the screen shows the panorama only in a fragment. So
> neither the cube's draws nor the copy's read is the defect: what remains
> is (a) the `levels[0]=pixels` upload path writing the mirror partially or
> row-mangled, or (b) the composite's sampler binding a different/empty
> image. Discriminator is now one dump: the texture *image* content
> post-upload — all-blue → the sampler binds something else; partial → the
> upload writes a sub-rect.
>
> **Round 35 — RESOLVED by main's own mirror dumps** (`r212-panmirror-mirrors`):
> the `backgroundTexture` mirror (`obj16a83679`, 256×256) holds **the wrong
> content in the wrong orientation**:
>
> - `…-n1-…` dump: white + a small panorama fragment in the bottom-left
>   ~85×85 — the cube occupies a corner, not the read's full 256×256
>   (though `GROUP_VIEWPORT clipped=0,0 256x256` says its draw covered it).
> - `…-n2-…` dump (later): **horizontally-mirrored GUI text** over the same
>   region — the version/copyright strings drawn at GUI bottom-left, i.e.
>   exactly where the copy reads the frame. GUI text can only reach the
>   mirror if the copy ran *after* text landed in the frame: the host route
>   (`TextureCopyService:418-425`) calls `flushPendingBeforeGlDraw` +
>   `waitForIdle` *then* reads — so **queued GUI draws from the previous
>   frame's tail drain into the frame during the next frame's panorama copy**
>   (the frame boundary itself doesn't flush; present doesn't drain the
>   immediate queue). The copy then lifts text + partial cube + leftovers
>   into `backgroundTexture`, and the stretch quad paints it fullscreen.
> - The text's X-mirror (**verified by un-mirroring**: a horizontal flip of
>   the dump reads "Type spawn" + "Cleanroom" cleanly) means the
>   read→upload order reverses *columns*, not rows — a `reverseRows`
>   analog still alive in `readImageRectangle`'s column order or the upload
>   stride.
>
> Two defects, both in `TextureCopyService`'s host route: (1) the flush it
> does to "show the copy the frame" also materializes *later-frame* content
> into the read rect — the ordering needs "draw what the game drew before
> this call", not "draw everything queued"; (2) a column-reverse in
> readback/upload. Fix shape for (1): drain only work enqueued *before* the
> copy (a boundary mark in the immediate queue), or read the queue-state at
> call time.
>
> **Round 36 audit of the X-mirror → re-decoded as 180° rotation (round
> 37)**: high-zoom decoding of the mirror text shows it is **180°-rotated,
> not X-mirrored** — the r180 variant reads "A messages"/version-block text
> ("Minecraft 1.12.2" / "MCP 9.42" / "Cleanroom 0.6.13" / "17 mods loaded")
> while the presented frame's bottom-left holds the *same* version block
> correctly (window-splash leg). Every copy hop verified *straight* —
> `readImageRectangle` (`:691` `imageOffset(x,y,0)`), the buffer→int[] map
> (`:737` linear), `toRgba` (`row*width+col`), `copyToImage` (tight pack),
> `readMirrorLevel`/`writePng` (faithful) — so the rotation entered as
> **frame content**: a second draw of the version text under a rotated
> transform. The rotation's origin is now identifiable: `GuiMainMenu:400`
> `rotate(180,1,0,0)` × `:430` `rotate(180,0,1,0)` = **rotZ(180) — the exact
> in-plane 180° observed**. The panorama's own matrices. → a group stamped
> same region. The contamination vector is a group-stamp (viewport +
> modelview) that outlived the panorama draw — the same family as the
> join-split keys at `:5438-5450` — worth checking whether a `glViewport`
> restore after `rotateAndBlurSkybox` fails to retire a 256×256 group.
>
> **Main's `4b7f43dce` narrows the root cause past the stale-stamp theory**:
> `flushPendingBeforeGlDraw` *records* the queued groups but their
> **submission is deferred to frame-end** — the copy's own
> `readImageRectangle` submits+waits its own command buffer *ahead* of the
> recorded work, so the copy systematically reads the **previous frame's
> composite** (gl21 dumps: version text + cube fragment = last frame's
> content — `drawScreen` draws text *after* `renderSkybox`, so the copy's
> region can only have come from a frame whose text pass already ran).
> The rotated text then self-sustains: once rotated content entered the
> texture, the composite → copy loop re-injects it every frame (the
> composite draws the texture fullscreen; next frame's copy lifts the
> composite's bottom-left back into the texture). The fix shape is not
> "drain less" — it's that the readback must not run ahead of the
> recorded-but-unsubmitted work it was meant to see: submit pending work
> before the read, or defer the copy's read until after the draw
> submission.
>
> **And the "rotation" itself is likely not a defect at all** (round 38
> re-decode): `rotateAndBlurSkybox`'s quad (`GuiMainMenu:499-502`) and the
> composite (`:535-538`) both sample the texture *transposed* —
> screen-x→v, screen-y→(−u) — so the blur feedback rotates the region's
> content ~90° per pass **by vanilla design**. A texture holding rotated
> text is the expected dump content; the anomaly is only that GUI text is
> in the texture at all, which traces to the stale-read: in vanilla the
> version text also sits in the framebuffer's bottom-left and also gets
> lifted — it is masked on screen by the composite's central sampling
> window (u∈[0.5±0.26], v∈[0.5±0.47]) plus the overlay gradient, not by
> any absence from the texture. So the rotated text is evidence *for* the
> stale-read (it proves the copy sees last frame's composite+overlays),
> not a separate mirror defect.
>
> **Stale-read channels** (from `flushPendingBeforeGlDraw` `:6728` +
> `flushImmediateWindow` `:6805`, mapped while main's `windowBusy`-style
> counters land):
> - **(A) `immediateWindowBusy` short-circuit** (`:6729`): if a window is
>   mid-flight when `glCopyTexSubImage` runs, the flush returns without
>   draining — the read proceeds while the pending groups were never
>   submitted. The blur path can hit this if the blur quad's own draw is
>   still an open window at copy time.
> - **(B) `countGroupsForTarget(currentIdentity)==0` early-out** (`:6854`):
>   the pending groups belong to a *different* identity — they stay queued
>   and the read proceeds on the current target — cross-identity staleness.
> - **(C) `flushImmediateWindow` does submit immediately** (`:6892`
>   `frameCoordinator.submitCommands()` — the submission reaches the queue
>   inside the flush call), and the device uses a **single graphics+transfer
>   queue** — so a flush that *runs* orders the draws ahead of the copy's
>   read on the GPU timeline: the read's own `endCommands` submission lands
>   behind the window's commands in-queue. `waitForIdle` is then only a
>   belt on an ordering the queue already guarantees — the read cannot see
>   pre-draw content when the draws were actually submitted.
> - **Consequence**: systematic staleness (every copy a frame old) can only
>   mean the draws were never submitted before the read — i.e. (A) or (B)
>   fired — or that the draw work went into a *different* image (identity
>   routing). There is no race that produces "consistently one frame
>   behind" on a single queue.
> Main's in-flight counters (`pendingWork`/`groups`/`openBatches`/
> `windowBusy`/`windowsThisFrame` on `CELTIUM_TEX_COPY_SERVED`) discriminate
> A vs B exactly — A shows `windowBusy=true` or `pendingWork=false` with
> draws expected, B shows `groups>0` still queued after the flush.
>
> **Round-41 leg `r212-panbounds2-bounds2` settles the ordering question**:
> every `CELTIUM_TEX_COPY_SERVED` reports `pendingWork=false groups=0
> windowBusy=false` — the copy ran with all prior work already submitted —
> channels A and B are **eliminated**. The cube IS landing:
> `nonWhite=58084`, bands `94/98/124 … 105/119/169` (panorama blue), and the
> presented frame now shows buttons, logo, splash and version text
> correctly. Caution: `completionsSinceDraw` grows because `lastDrawnEpoch`
> only updates inside the bounded `DRAW_SITE` log (`:12344-12355`, capped
> by `DRAW_SITE_LIMIT`) — once the cap is hit the counter freezes at
> epoch≈102 and the delta is a log-cap artifact, **not** evidence the image
> stopped being drawn into.
>
> **The remaining panorama defect is a group stamp, not ordering**: the
> presented frame shows the blur composite as a ~550×450 blob at
> bottom-left instead of fullscreen — the composite quad draws under a
> stale `currentGroupViewportValues`/`currentGroupProjection` (256×256 or
> the panorama perspective instead of the GUI ortho + fullscreen
> viewport). Same family as the join-split keys at `:5438-5450`: a group
> stamped during the 256-viewport/perspective phase is still open when the
> composite's batch appends, or the batch joined a group carrying those
> stamps.
>
> **Round-41 narrowing** (`GROUP_BOUNDS`/`IMMEDIATE_ENQ`/`GROUP_VIEWPORT`
> in `r212-panbounds2-bounds2`): the composite itself is group 388 —
> `target=970565335` = `mainIdentity` (the game FBO), `proj` = GUI ortho,
> `ndc x[-1..1]` — and its `GROUP_VIEWPORT` stamps `gl=0,0 1920x1080` —
> all three stamps are correct, so the composite's coverage is NOT a stale
> stamp. The cube-face groups (3–8, `texture=46–51`) stamp the 120°
> perspective correctly too (`m00=0.577`) but their NDC bounds run
> `x[-4.25..-0.07]` — faces project mostly *left* of the 256 viewport —
> the cube view is off-centre, so the blur accumulates only a partial
> panorama; the composite then samples that partial content and stretches
> it. What still wants an answer: why the face NDC window sits off-centre
> — a wrong modelview (the jitter `translate(f,f1,0)` and rotX/rotY are
> mirrored, but the aggregate should still tile the cube) or a projection
> aspect that assumes a different viewport ratio than 256×256.
>
> **Round-42 state** (`r212-panbounds2-stage3`): **the world renders
> correctly** — flat-world ore pattern, placed bed, right-side-up sky —
> `worldFrameDistinctColours=2558 blackFraction=0`; the near-black
> `celtium-validation.png` is a capture-timing artefact, not a defect.
> Menu: all GUI correct; panorama now a ~700×500 tilted real-content blob
> (progress from ~550×450) plus a small sharp ~80×80 thumbnail at the
> corner. The thumbnail is group 387 (7 blur batches, `target=0`) drawing
> the blur output into the frame image — correct — while the composite
> (group 388) goes to `mainIdentity`. The blob therefore means the
> composite's draw inside the FB covers ~36% — yet group 388's stamps all
> verify (ortho, `ndc ±1`, `glViewport=1920x1080` — and `framebufferRender`
> itself draws a fullscreen quad under `glViewport(0,0,displayW,displayH)`
> sampling the whole texture). The remaining shape: the composite's quad
> covers NDC but its fragments only land in part of the image — i.e. a
> scissor the stamp doesn't describe, or the FB attachment's own extent is
> smaller than the viewport assumes (an image-size vs viewport-size
> mismatch on `mainIdentity` — the 915×515 question again, now on the
> attachment rather than the viewport).
>
> **Prime suspect for the blob**: `celtium$setGroupViewport` clips the
> group's viewport to `this.targetWidth`/`this.targetHeight`
> (`:12430-12431`) — the registry's recorded extent for the bound
> `mainIdentity` — *not* to the attached image's real size. If the FBO
> registered at a stale logical extent (the 915×515 class — e.g.
> `framebufferWidth`/`Height` recorded before the drawable's true
> 1920×1080 arrived — the `wasResized` family) while its image is
> physically 1920×1080, the clip truncates the composite to bottom-left
> ~48% even though the group's own stamp is 1920. That fits a
> bottom-left partial blob cleanly. Discriminator: a `CELTIUM_TARGET`
> line or the pass log that names `targetWidth`/`targetHeight` when
> `mainIdentity` binds — if it reports <1920×1080 while `chainPair`/
> `passAttached` is the 1920 image, this is confirmed. **Correction on
> the buttons**: they draw inside the FBO too (`drawScreen` never
> rebinds after `bindFramebuffer` at :525), so a `mainIdentity` extent
> clip would hit them as well — they render fullscreen, which weakens
> the 915×515-extent theory for group 388 specifically; the residual is
> then either (i) the composite's transposed sampling showing a corner's
> content at bottom-left because the blur texture only accumulated a
> partial cube, or (ii) a scissor on a group the bounds line doesn't
> print. Both still need the per-draw image evidence main's next leg
> carries.
>
> **Main's `108b388e8` names the residual**: "the menu's grey is the cube
> not repainting its 256x256" — the faces paint the region early then
> stop arriving, so the blur's feedback fades to grey — consistent with
> the image-routing channel (C′): the face windows draw into an image
> other than the one the copy reads (`chainPair` vs `frameImage` — the
> pair alternates draw/present, so a face that lands in the pair is
> invisible to a copy that always reads `frameImage`). Watch
> `passAttached` on the face groups' DRAW_SITE lines against
> `readImage` on the copy lines — the earlier legs showed
> `passImage=0x…5f30` (the pair) for an `immediate-only-window` draw
> site vs `readImage=0x…7fe0` (frameImage): if the faces' window
> attaches the pair while the copy reads frameImage, every alternate
> frame's faces land where the copy cannot see them — repaint half the
> time, stale the rest.
>
> **Round-43 root cause — `glClear` on the default framebuffer is dropped
> as "foreign"** (`GllessFrameTarget:780-794`): `owner =
> pendingClearDrawFbo` — **owner==0 IS the default framebuffer** (the
> frame image's own), yet `owner >= 0 && !ownerIsMain` drops every
> non-main clear including it. The frame-start
> `Minecraft.runGameLoop:1199 clear(16640)` (COLOR|DEPTH) runs on
> `drawFbo=0` → `drawFramebufferIsMain=false` → **dropped** → the frame
> image's depth never receives the game's per-frame clear. The blur's
> quads then write depth 0.5 into the region every frame; the next frame's
> faces project at depth ~0.9 (the 120° frustum) and fail `LEQUAL` against
> the leftover 0.5 — the cube paints exactly once (before the first blur
> write) then dies. That is main's "cube not repainting" mechanism, and it
> also explains the colour surviving (`framebufferRender` repaints it
> every frame regardless) while only the depth-tested draws die. **Fix
> shape**: `owner > 0 && !ownerIsMain` — or an explicit `owner == 0`
> branch that lets the default framebuffer's clear reach the frame — the
> non-main drop is correct only for a *game-owned* FBO (name > 0); the
> default framebuffer's clear is the frame's own.
>
> **Depth-attachment confirmation**: the frame pass
> (`buildFrameRenderPass`) attaches a depth image with
> `loadOp=LOAD`/`storeOp=STORE` (`VulkanTerrainPipeline:1019-1020`) — a
> **persistent** buffer whose content survives frames untouched unless a
> `glClear` resets it — and both `windowIdentity` (target=0) and
> `mainIdentity` draw into `frameImage` (`:7485-7493`), so every menu draw
> shares that one depth buffer. The world is unaffected because the
> terrain pass's own depth clears per-pass; the menu faces are the only
> draws that test against the frame image's accumulating depth.
>
> **Round-44 measured refutation of the depth theory**
> (`r212-panbounds2-depth3`, `CELTIUM_DEPTH_STAGE`): the frame image's
> depth in the 256 box reads `min=1.000 max=1.000 mean=1.000
> atOne=65536/65536` at the copy — the faces are NOT depth-rejected; this
> round's stale-depth model is withdrawn (the `owner>=0` drop exists in
> code but something else — a latched clear or the `first`-clear path —
> resets the depth before it can bite; the drop is still a code-level
> misclassification worth the `owner>0` guard, it just isn't this
> symptom's cause).
>
> **The standing defect — all six faces share ONE NDC quadrant**
> (main's `5b82c0a68`/`f80c43174`): face groups 3–8 read
> `x[-4.29..-0.08] y[0.25..3.13]` — every face left of and above centre;
> vanilla's interior cube should tile the viewport. The k-rotations DO
> reach the modelview (`f80c` audit: matrices are rotations →
> `axisAligned` gate false → live matrix carried), so the defect is a
> shared transform applied on top of correct per-face rotations.
> **The group's projection stamp is not pure `gluPerspective`**: the
> bounds line prints `m00=0.577 m11=0.577 m22=-1.005 m32=+0.45` —
> `gluPerspective(120,1,0.05,10)` should give `m22=-1.0100,
> m32=-0.1005`. `m32=+0.45` is sign-flipped and ~4.5× off — no valid
> near/far pair yields it, so the stamped projection is a **composite**
> (perspective post-multiplied by something — an ortho, a translate, or
> a second perspective). That composite is exactly the shared transform
> that pushes all faces into one quadrant; the discriminating question
> is what the stamp picked up — `drawPanorama:394` does
> `loadIdentity`+`gluPerspective` under `matrixMode(PROJECTION)`, so a
> leftover ortho/perspective on the projection stack top before the
> loadIdentity (or a projection×modelview cross-contamination in the
> mirror) is the suspect. `P×T(0,0,t)` gives `m32=−1.01t−0.1`; `+0.45`
> implies `t≈−0.545` — a z-translate of that size is not in
> `drawPanorama`, favouring a projection-stack carry-over from the
> pre-panorama phase.
>
> **Round-45 mechanism candidates for the composite stamp** (code-verified):
> - **`matrixScratch` is shared and only partially written**
>   (`VulkanTerrainGpuBackend:3807-3812`): `perspective()` fills
>   `[0],[5],[10],[11],[14]` but leaves `[12],[13],[15]` at whatever the
>   previous user wrote — `ortho()` fills exactly those (`-1, +1, 1.0`,
>   `:3745-3748`). If a `perspective()` follows an `ortho()` without a
>   scratch clear, the multiplied frustum carries the ortho's
>   translate+w — a composite that shifts the cube off-centre. The
>   group's `m32=+0.45` doesn't match *either* formula's clean output for
>   any near/far, so the stamp is a product or a leftover, not a fresh
>   build — consistent.
> - `applyDerivedProjection` (`:4745`) `setMatrix`-overwrites the
>   projection top and sets `worldPassActive` — if it fires between the
>   panorama's `gluPerspective` and the face batches' group stamp, the
>   faces carry the world's derived projection. Its `fov` would have to
>   be 120° to match `m00=0.577` — the panorama's own args — so this is
>   the weaker of the two.
> - The group's `glProjection` is a **copy** of the mirror's projection
>   top (`immediateState.matrix(MODE)`) — the composite enters the mirror
>   upstream of the stamp, either via the contaminated scratch or a
>   `glMultMatrix`-style product on a non-identity top.
>
> **Scratch contamination CONFIRMED by the instrument itself**
> (`CELTIUM_GLUPERSPECTIVE fov=120 aspect=1.0 near=0.05 far=10` in
> `r212-panbounds2-bounds2`): `after=[m00=0.577 m11=0.577 m22=-1.01
> m23=-1.0 m32=-0.101 **m33=1.0**]` — a real perspective has `m33=0`;
> the `1.0` is `ortho()`'s `matrix[15]` left in the shared scratch. The
> mirror's panorama projection is therefore `perspective + ortho
> leftovers`: `w_clip = -z + w` instead of `-z`, so the divide
> collapses near `z≈±1` and faces scatter — and `m[0][3]`/`m[1][3]`
> (indices 12/13, *not* printed by this line) very likely carry
> ortho's `-1`/`+1`, biasing the whole cube left+up. That is the
> one-quadrant mechanism. The group's `m32=+0.45` is a *different,
> later* matrix than the post-call `-0.101` — the stamp captured a
> further-changed value (a second multiply, a derived overwrite, or
> `captureGlMatrices` reading the *stub's* projection rather than the
> mirror's — `glGetFloatv` under GL-less returns the stub's own
> accumulated matrix, a third source distinct from both). Fix shape:
> zero `matrixScratch` in `perspective()`/`applyDerivedProjection()`/
> `ortho()` at entry — one line, removes the leftovers.
>
> **Discriminating instrument**: print all 16 elements of a face group's
> `projection` (the bounds line prints 4). `m[0][3]`/`m[1][3]`/`m[3][3]`
> non-zero → scratch contamination (ortho leftovers); all-zero with
> wrong `m[2][3]` → a genuine product — the two fixes are different
> (clear the scratch vs find the stray multiply).
>
> **Round-46 state**: the scratch fix landed (`f9d64a91e`,
> `612c4a658`) — `r212-panmatrix-scratchfix` shows `m33=0.0` post-call,
> and the faces' model-views are a *correct cube* (three antipodal
> orthogonal pairs, `CELTIUM_MATRIX_DUMP`). Depth/scissor/rotations all
> clean. **Residual**: the box still gets ~0.8% of the cube's pixels —
> the faces' NDC union never covers NDC(0,0) even though the cube is
> geometrically right. The group's stamp (`r212-panbounds2-clip`, pre-fix
> family) still reads `m22=-1.005 m32=+0.45` ≠ the post-call `-0.101` —
> the stamp is NOT the matrix `mirrorGluPerspective` leaves; something
> rewrites `glProjection`/the stack between the call and the group's
> close. `glGetFloatv` delegates to `celtium$mirroredProjection()` (the
> same mirror), `multiplyMatrix` uses a `multiplyResult` temp, and the
> font-run path (`:4699-4719`) save/restores correctly — the remaining
> writers are `applyDerivedProjection` (`setMatrix` overwrite at `:4745`)
> and any `glLoadMatrix`/`glMultMatrix`/`glFrustum` call between
> `gluPerspective` and the face batches. A `persp×T(0,0,−0.545)`
> product fits `m32=+0.45`; what calls translate-z on the *projection*
> stack during the panorama is the next question. Post-fix legs need a
> `GROUP_BOUNDS` re-run to say whether the stamp still carries `+0.45`.
>
> **Round-47 — main's `c614d8435` lands the exact corruption and its
> cost**: the stored panorama projection is
> `[0.577,0,0,0, 0,0.577,0,0, 0,0,−1.01,−1.0, −1.0,+1.0,−0.101,1.0]` —
> column 2 the clean frustum, column 3 the GUI ortho's `(−1,+1)` with
> `m33=1.0` — `w_clip = −x + y − 0.101z + 1`. Faces' NDC
> `x[−4.28..−0.08] y[0.25..3.12]` contains no view centre → 0.8% of the
> box — every earlier channel (depth, scissor, viewport, ordering,
> rotations) is dead; the projection WAS the whole defect. The naive
> `Arrays.fill(matrixScratch, 0)` at all four builders (`glOrtho`,
> `glFrustum`×2, `applyDerivedProjection`) **blacked every presented
> frame** (`r212-panmatrix-scratchfix`, `FAILED_PRESENTED_IMAGE`,
> presented mean 0 while the frame image held a real picture) — some
> call path depends on the inherited leftovers, so the fills were
> reverted and the bisect queued: **which of the four builders' unwritten
> slots does the present path depend on?** Prime suspect: a path that
> builds a *non*-perspective on the frustum-shaped write
> (`{0,5,10,11,14}`) and relies on the inherited `m33=1.0` to keep `w=1`
> — e.g. `applyDerivedProjection` reused for an ortho-class pass, or a
> `glMultMatrix` that post-multiplies the scratch with identity
> expectations. The durable fix is one zeroing helper + writing all
> sixteen slots explicitly in each builder (so no builder ever depends
> on, or leaks into, another's leftovers).
>
> **Round-48 — RESOLVED**: `be3ade744` lands the fix — the panorama
> projection comes through `mirrorGluPerspective` → `perspective()`
> (the glFrustum builder), which now zero-fills `matrixScratch` on
> entry; `m33=0.0`, `w=-z`, cube repaint 0.8%→13.1%/frame, presented
> menu distinct 26176→**113116** — the full-screen blurred panorama is
> back (verified in `r212-panzeroF-frustumonly`). The bisect also
> answered the earlier black-frame riddle: zeroing ALL four builders
> blacks the frame because a *consumer* of another builder relies on
> its inherited values — only `glFrustum`'s fill is safe to land; the
> other three (`glOrtho`, second `glFrustum`, `applyDerivedProjection`)
> have a consumer reading their leftovers and wait for it to be named.
> **Residuals (minor)**: a ~90×90 sharp panorama thumbnail at the
> bottom-left corner (the blur pass's source region showing through
> where the composite's coverage ends) and the version text clipped
> ~40px at the left edge — both consistent with the composite's
> coverage window being slightly narrower than the frame; worth a
> `viewport`/`group` re-check under the fixed projection but not
> blocking.
>
> **Round-52 corroboration — the splash FBO's texture IS mirrored**:
> `r212-splash-splash3` shows `texture=6` binding (UNIT_BIND ×11) and an
> `IMMEDIATE_ENQ frame=0 group=2 texture=6 target=0 mirrored=true` — the
> splash's FBO colour texture has a mirror and its composite draw
> enqueues to the window. The round-168 no-entry reading was indeed the
> duplicate-jar leg, not this tree — task-74's conclusion confirmed
> independently. The composite itself samples `proj[10]=0.0, [14]=-0.5`
> — the `ortho(…,-1,3)` overlay signature, the same 2971-draw family
> identified in round 50.
>
> **Round-55 — the splash logo's loss is the cross-target ordering,
> verified**: `747dd9577` — the dual dump at present 1 reads the chain
> pair (the splash FBO's image) as `mean(247.1,245.6,245.7) distinct=35`
> (white bg + Mojang logo, correct) while the frame image is
> `mean(255,255,255)` single-colour. Code-confirmed at
> `flushImmediateWindow:6913` — `if (countGroupsForTarget(currentIdentity)
> == 0) return; // others remain owned in the queue` — the flush draws
> **only the current identity's groups**; the splash FBO's groups (target
> `0x47869b33`) stayed queued while the composite's window (target=0,
> sampling texture 6) submitted first → the composite sampled the pair as
> `SplashProgress` cleared it (white). The pair holds the logo only
> because the FBO's groups draw *later* in the same frame — too late.
> **This is the architectural gap**: the per-target group isolation
> breaks acceptance-order = draw-order = submission-order whenever a
> same-frame composite reads another target's output.
>
> **Round-56 — task-76 REFUTES the ordering theory, withdraw it**:
> `a774a5dac`'s audit — `bindFramebuffer` calls
> `flushPendingBeforeGlDraw` on a target change (`:4546`), so the FBO's
> groups submit before the composite ever binds; and the window path
> submits **and waits** (`submitCommands → waitForIdle → endFrame`) —
> the fence wait means the composite cannot execute before the FBO's
> writes complete. Submission order was never broken. The remaining
> branch is **routing**: identity-0's pass attaches either the frame
> image (what the present blits) or the "default pair"
> (`pairLabelForIdentity`) — the two rules disagree, so the composite
> may land in the pair instead of the frame image → frame image stays
> white, present blits white. The menu's composite survives because it
> goes through the published-image route, not the same
> identity-0-window path. The triple-dump (frame image / default pair /
> chain pair at present 1) decides which image the composite lands in.
>
> **Code-side narrowing — the *draw* half is already correct**: at
> `recordImmediateOnlyPass:7682-7690`, `windowIdentity` (identity 0) and
> `currentIsMain` both set `offTarget=null` →
> `passAttachedColorImage = frameTarget.colorImage()` — the composite's
> pass **unconditionally attaches the frame image** (the `:7675-7681`
> comment documents the earlier "send to pair once a main framebuffer is
> adopted" variant as the washed-out-menu regression — since reverted).
> So the composite's geometry lands in the frame image. The "two rules
> disagree" is therefore on the *sampling* side:
> `routeLabelForBinding` says identity 0 → default-pair (what a texture
> standing for "the default framebuffer" reads), and the question is
> whether the composite's `bindFramebufferTexture` (texture 6) resolves
> to the chain pair (where the logo is, `route=chain-pair` confirmed)
> or to a wrong/empty image — e.g. if `framebufferIdentityForColorTexture(6)`
> resolves the name to identity 0 rather than `0x47869b33`, the sample
> reads the default pair (empty) → the composite draws a correct white
> quad into the frame image → white presented. The probe's `name=6
> route=chain-pair` line argues it resolves right; if the frame image
> holds a correct-but-white quad at present 1, the sampler read an
> empty image at composite-time — i.e. a *timing* (sample before the
> FBO's content published) not a routing defect after all.
>
> **Round-57 — the triple-dump answers it: a stale publication**
> (`63bc71cdc`). At the same instant: chain pair = logo
> (`247.1,245.6,245.7`, 35 distinct); default pair = black, never
> written; frame image = white — and `windowDrawsToFrameImage` is in the
> thousands with zero drops (`immediateGroupsKeptForOtherTargets=0`,
> `postAcceptDroppedGroups=0`, no `replaceBoundaryDrops`). So the
> composite's group DID draw into the frame image — correctly — but
> its *sample* is white. The smoking gun is
> `publishedFrameTexturesCreated=2` with `Republished=0` and
> `StalePruned=0`: the name was published over a first pair image, the
> pair was then recreated (`ensureChainPair`/ensure at a new size or
> reset → a NEW `VkImage`), and the republish path never ran →
> texture 6's descriptor still names the **stale first image**
> (empty/cleared-white) while the live `chainPairColorImage()` holds
> the logo. My round-56 "empty image at composite-time" candidate is
> confirmed — the mechanism is the stale wrapper, not ordering or
> routing. **Fix shape**: republish the name when the pair's image
> handle changes — run the republish path on `ensureChainPair`
> recreate, or key `publishedFrameTextures` on the image handle and
> re-publish on mismatch. The discriminator main queued: print the
> published image handle for name 6 alongside the live
> `chainPairColorImage()` — differing handles confirm the staleness.
>
> **Round-58 — handles match: the publication is live, stale theory
> out for this leg**: `r212-splash-splash7` — texture 6's published
> image `0x7f876dbd98d0` (`FBO_TEXTURE_PUBLISHED`) equals the live
> `chainPair=0x7f876dbd98d0` (the `WINDOW_DRAW`/`COPY_READ` lines) →
> the wrapper names the SAME image the FBO's draws land in → **not
> stale**. `created=2` was also misread: it counts two *names* (6 and
> 23, both → chain-pair at `:32917`), not a pair recreate — so the
> `Republished=0` evidence was a name-count, not staleness. The
> recreate-gap task-77 found (`celtium$syncPublishedChainOutput` not
> called from `ensureChainPair`) remains a real latent defect — a
> pair recreated at a new size WOULD stale every wrapper — but it is
> not this leg's loss. What remains for the white sample: the
> composite's descriptor/view/sampler resolution at draw-time, or the
> composite sampling before the FBO's groups actually submit (the
> flush order within the same frame — `WINDOW_DRAW identity=0` lines
> exist, but their serials vs the FBO pass's completion need a
> timeline check).
>
> **Round-59 — the residual candidate: a layout mismatch on the
> sample**. The wrapper's own `publishExternalImage` line declares
> `layout=GENERAL` for name 6 — but the chain pair transitions to
> `SHADER_READ_ONLY` when it's sampled (the `:7695-7706` hand-back
> cycle: attach → GENERAL, sampled → SHADER_READ_ONLY). Vulkan
> requires the sampled image to be in the layout the *descriptor*
> declared (VUID-09600): a `GENERAL`-declared descriptor sampling a
> `SHADER_READ_ONLY`-resident image is an undefined read — which on
> RADV can produce exactly this: a correct-looking quad full of the
> clear/cached content. The discriminating check is cheap: print the
> chain-pair's actual layout at the composite's draw against the
> wrapper's declared `GENERAL` — a `SHADER_READ_ONLY` answer is the
> mismatch. (This also fits why `validationClean` can stay true on a
> non-validation build.) Not yet confirmed — next instrument.
>
> **Round-61 — two corrections before the UV probe**:
> `POSITION_TEX_COLOR` (format 2) IS in `DEFAULT_ACCEPTED`
> (`VulkanImmediateFormats:153` = `"1,2,...,10"`) — the `:1989-1992`
> comment ("default is 1,3,4,5 … excluded") is **stale**, the format is
> accepted now → the composite's batch IS taken (no refusal gate). And
> the menu uses the same `POSITION_TEX_COLOR` composite path
> (`framebufferRender`) and renders — so the tex attribute wiring
> *works* in the same format — the splash's difference is upstream of
> the UV read.
>
> **Round-62 — main independently confirmed `publishedIsLivePair=true`
> and narrowed to the sample (layout + descriptor)** — the same branch
> I flagged in round-59. The relevant counter:
> `publishedFrameTexturesSkippedForAttach=27` — the sampling-side
> transition (`celtium$transitionPublishedFrameTextures`, which moves a
> published image to SHADER_READ_ONLY before it's sampled) is skipped
> whenever a name's image == the pass's attached image. For the
> splash's composite that check should NOT fire (it attaches the frame
> image, samples the chain pair — different handles), so texture 6's
> transition should run → image lands in SHADER_READ_ONLY → the
> declared-layout question then is what the descriptor declares.
> The 27 skips are the other names (23 etc); whether the composite's
> own draw took the skip branch is the measurement main queued (the
> skip counter + the group's texture are already logged). If the
> composite's texture-6 transition ran, the image is SHADER_READ_ONLY
> and the residual narrows to the descriptor's declared layout /
> sampler; if it was skipped, the image sits in the attach layout and
> the undefined read follows.
>
> **Round-63 — `declaredMismatches=1` corroborates the layout
> hypothesis**: splash8's `externalImages` line reads
> `layout[transitions=27 skips=1992 commits=1799 … declaredMismatches=1]`
> — exactly **one** declared-layout mismatch in the whole run, and the
> splash's composite is exactly one draw. The instrument counts a
> descriptor whose declared layout disagrees with the image's actual
> layout at draw-time → a single occurrence matching the single
> present-1 composite → the sample read is the undefined one, and the
> white is the result. (Two clarifications from the same block:
> `skippedForAttach=27` is the published-names counter — the skips
> belong to the other names; `offTargetPerTarget`'s `0:29598
> (default-pair)` is the `pairLabelForIdentity` *naming*, not the
> attach — the pass still attaches the frame image per `WINDOW_DRAW`.)
> The fix candidate stands: make the published image's layout match
> what the descriptor declares (or declare what the transition
> produces) at the sample — the transition path itself is correct for
> the other 26 names.
>
> **Round-64 — the mechanism is now specific and RADV-known**: the
> published wrapper declares `layout=GENERAL`
> (`publishExternalImage`'s own line for name 6), while
> `transitionPublishedFrameTextures` moves the chain pair to
> `SHADER_READ_ONLY` for the sample — descriptor says `GENERAL`, image
> sits in `SHADER_READ_ONLY` → the `declaredMismatches=1` → undefined
> read. And on RADV this is a *documented* failure mode, not just UB:
> [Mesa bug 110259](https://lists.freedesktop.org/archives/mesa-dev/
> 2019-March/217285.html) — sampling an image in `GENERAL` layout
> **returns zeros** where `SHADER_READ_ONLY` works (`RADV_DEBUG=nohiz`
> also unmasks it). So the descriptor's declared `GENERAL` is the
> defect: **fix = write the descriptor's `imageLayout` to the same
> layout the sampling transition produces (`SHADER_READ_ONLY`), not
> the `GENERAL` the publish logs** — or transition to `GENERAL` to
> match the declaration (worse: GENERAL sampling is itself the
> RADV-fragile path). The one-line descriptor-side fix is the right
> side; it also explains why the menu survives (its composite samples
> a name whose declared layout happens to match) and why
> `validationClean` stays green on the non-validation build.
>
> **Round-65 — refined: the declaration is already SHADER_READ_ONLY;
> the defect is the image NOT being in it at the one sample**
> (`VulkanTextureRegistry:217-222`, `:276`). The design is correct:
> `shareExternal(..., GENERAL)` records the image's *arrival* state,
> the immediate pipelines declare `SHADER_READ_ONLY`, and
> `ensureSampledLayout`/`ensureGeneralLayout` handshake it across the
> sample. So the `declaredMismatches=1` means the image was **not in
> SHADER_READ_ONLY when texture 6's composite sampled it** — the
> handshake's tracked layout drifted from the actual (a re-attach the
> wrapper didn't see, or the `ensureSampledLayout` for name 6 skipped
> because the wrapper already *thought* it was read-only). That is
> also why the RADV-GENERAL-zeros failure bites: the image sits in
> `GENERAL`, gets sampled as if it were `SHADER_READ_ONLY` — two
> failure modes on the same draw. **Fix shape**: at the composite's
> sample, force `ensureSampledLayout` to issue the barrier when the
> image's actual layout isn't SHADER_READ_ONLY — i.e. don't trust the
> wrapper's tracked state for an image other passes re-attach. The
> discriminator: print the wrapper's tracked layout vs the image's
> real layout at texture 6's draw — a `GENERAL` actual against a
> `SHADER_READ_ONLY` tracked is the drift.
>
> **Round-66 — the counter's own meaning refines the mechanism
> further** (`VulkanGlTexture:602-616`). `foreignLayoutDeclaredMismatches`
> fires in `declareForeignImage` at **wrapper-creation time** — a
> second wrapper of the same image declaring a different layout than
> the shared `confirmed`, which wins ("the wrappers of one image share
> one layout fact and the recorded handshake wins"). So
> `declaredMismatches=1` is a creation-time disagreement — texture 6's
> `GENERAL` declaration against a shared `confirmed` that was already
> `SHADER_READ_ONLY` — not a draw-time sample error. **The real drift
> mechanism**: the shared `confirmed` moves ONLY through
> `transitionForeignLayout` (`:609` — "the recorded handshake wins") —
> but a render pass attaching the chain pair transitions it
> *implicitly* (`initialLayout → COLOR_ATTACHMENT/GENERAL`) with no
> `transitionForeignLayout` call → `confirmed` stays stale → the next
> `ensureSampledLayout` reads the stale `confirmed` and skips the
> barrier it should have issued → the image sits in `GENERAL` while
> the descriptor expects `SHADER_READ_ONLY` → undefined read → white.
> So the fix is narrower than "issue the barrier at the sample": **the
> render pass's implicit attach-transition must be folded into the
> shared `confirmed`** — i.e. when a pass attaches a foreign image,
> its `initialLayout`/attachment transition has to update the shared
> state (the handshake the `:987` doc already describes as the owner's
> job — `ensureGeneralLayout` before the pass — isn't running, or its
> effect isn't reaching `confirmed`). `celtium$syncPublishedChainOutput`
> records the chain output's own hand-back; whether it covers the
> pass's implicit transition for texture 6 is the check.
>
> **Round-67 — the complete causal chain, step by step**:
> (1) the chain pair gets sampled (a composite) →
> `transitionForeignLayout` → `confirmed=SHADER_READ_ONLY`;
> (2) the FBO's own pass then attaches it — the render pass's
> **implicit** `initialLayout=GENERAL → attachment` transition moves
> the *actual* image to `GENERAL`, but this path never calls
> `transitionForeignLayout` → `confirmed` stays `SHADER_READ_ONLY`
> while the real image is now `GENERAL`;
> (3) the composite's `ensureSampledLayout` reads
> `confirmed=SHADER_READ_ONLY`, concludes no barrier is needed, and
> **skips it** → actual stays `GENERAL`;
> (4) the descriptor expects `SHADER_READ_ONLY`, the image sits in
> `GENERAL` → VUID-00344 mismatch + RADV's GENERAL-sample-returns-zeros
> → white. The escape is step (2): **the render pass's implicit
> attach-transition doesn't update the shared `confirmed`**, so the
> wrapper's tracked state drifts one layout behind the truth on every
> attach-after-sample. Fix = record the attach's resulting layout into
> `confirmed` (a `transitionForeignLayout`-equivalent at the pass's
> attach, or fold `initialLayout→attachment-layout` into the shared
> state) — then `ensureSampledLayout` sees `GENERAL` and issues the
> barrier. VUID-00900 (`initialLayout` must equal actual at begin) is
> the spec hook; the pass attaches at `GENERAL` so its implicit
> transition lands in `GENERAL` — which is exactly the update
> `confirmed` is missing.
>
> **Round-69 — main's group-draw instrument is measuring exactly this
> branch** (uncommitted `:12378` diff): at each group's draw it prints
> the descriptor's `view` vs the live `pairView`/`pairImage`,
> `standsForLivePair`, and the wrapper's tracked `layout=`
> (`foreignLayout`) — the two facts that separate "samples the live
> pair in the right layout" from "samples the live pair while it's
> still `GENERAL`". A `layout=SHADER_READ_ONLY` print against an image
> the FBO pass just implicitly moved to `GENERAL` is the drift's
> signature. Same branch as the round-67 causal chain — measurement in
> flight.
>
> **Round-70 — the two trackers, named exactly** — `GllessFrameTarget`
> keeps its OWN `colorImageLayout` (`:178`, `:855` GENERAL,
> `recordLayoutBarriers`' `oldLayout=this.colorImageLayout`), which is
> what actually moves the chain pair to `GENERAL` at every acquire.
> `VulkanGlTexture`'s shared `FOREIGN_STATES.confirmed` — the fact the
> wrappers' `ensureSampledLayout`/`ensureGeneralLayout` consult — is
> moved **only** by `transitionForeignLayout`. `GllessFrameTarget`
> **never calls `transitionForeignLayout`** — its own tracking is
> fully separate. So after every attach, `colorImageLayout`=GENERAL
> (the truth) while `confirmed`=SHADER_READ_ONLY (the stale wrapper
> belief) → `ensureSampledLayout` reads `confirmed`, skips the
> barrier → image stays `GENERAL`, descriptor expects
> `SHADER_READ_ONLY` → `declaredMismatches` + RADV zeros → white.
> **The fix is unification, in one of two shapes**: (a) make
> `GllessFrameTarget`'s acquire/release record the image's new layout
> into `FOREIGN_STATES` (a `transitionForeignLayout`-equivalent on
> `colorImageLayout`'s change), so the shared fact tracks the attach;
> or (b) have `ensureSampledLayout`/`ensureGeneralLayout` read the
> image's *actual* layout from `GllessFrameTarget.colorImageLayout()`
> rather than the shared `confirmed`, so the skip decision is against
> truth. Shape (a) keeps the shared-fact invariant; shape (b) is the
> smaller diff. Either closes the last standing visual defect.
>
> **Round-71 — the mechanism completes: `declaredMismatches=1` IS
> this exact defect.** Texture 6's wrapper is created *during* the
> composite's `publishFramebufferColorTexture` — `shareExternal`
> declares it `GENERAL` — but the chain pair's shared `FOREIGN_STATES`
> entry already exists (from name 23's post-chain wrapper, or a prior
> composite's `ensureSampledLayout`) holding `confirmed=SHADER_READ_ONLY`
> → `declareForeignImage` counts `declaredMismatches++` and **the
> shared fact wins** (`:609` — "the recorded handshake wins"), so
> texture 6's wrapper tracks `SHADER_READ_ONLY`. The FBO's own pass
> then attaches the chain pair — moving the actual image to `GENERAL`
> through `GllessFrameTarget.colorImageLayout` (invisible to
> `confirmed`). Texture 6's `ensureSampledLayout` reads the shared
> `confirmed=SHADER_READ_ONLY`, concludes no barrier is needed, and
> skips → the image is actually `GENERAL`, the descriptor expects
> `SHADER_READ_ONLY` → VUID-00344 + RADV zeros → white. The whole
> chain now closes: it needs the shared-confirmed design (wrappers
> inherit one fact, not their own declaration) AND the un-instrumented
> attach (the target's own tracker, not `transitionForeignLayout`).
> The fix holds: make `GllessFrameTarget`'s acquire/release record the
> resulting layout into `FOREIGN_STATES`, OR have `ensureSampledLayout`
> consult `colorImageLayout()` as truth — and `declaredMismatches`
> is the counter that says it happened exactly once, for exactly the
> splash's one composite.
>
> **Round-72 — `standsForLivePair=true`: the composite DOES sample the
> right image with the right view** (`r212-splash-splash9` `MIRROR_DRAW
> :443`): `groupTexture=6 view=pairView=0x…db0d0
> pairImage=0x…daa30 standsForLivePair=true`. `bindsOnUnit0=8` was a
> red herring — it's the 8th bind-op on unit 0, not texture 8. So the
> composite binds the live chain pair correctly — the loss is NOT a
> wrong-texture binding. `layout=1` is `VK_IMAGE_LAYOUT_GENERAL`: the
> shared `confirmed` at draw-record-time — expected if the
> `ensureSampledLayout` barrier is recorded-but-uncommitted into this
> same buffer (confirmed moves only on commit), and *only* wrong if the
> barrier never issued. The narrowed residual is therefore the
> barrier's **commit into the submitting command buffer**: either the
> `ensureSampledLayout` for name 6 issued into a different/never-
> submitted buffer (image stays `GENERAL` at the draw → RADV zeros),
> or the transition landed but the sample still reads white (the
> content-at-sample-time question — which the fence-wait ordering
> already argued against). `MIRROR_DRAW` gives the group's `descriptor`
> — pairing it with whether `transitionForeignLayout` recorded a
> pending barrier for `pairImage` in the same command buffer is the
> last check.
>
> **Round-73 — `drawnSoFar=1` names it: the composite draws FIRST and
> samples the EMPTY pair** (`splash9`/`splashortho`). `immediateGroupsDrawn`
> counts *every* group's draw (`:12586`); the composite shows
> `drawnSoFar=1` — the first draw of the frame — while the splash
> FBO's own pass (`offTargetPerTarget[47869b33] drawnPasses=1`) is a
> *separate, later* group. So the composite samples the chain pair
> **before the FBO's logo draws ran** → the empty/cleared pair → a
> uniform white quad → the pair holds the logo only *afterward* (which
> is why the probe reads `247,35` at present-time). **This is the
> ordering defect after all** — task-76's "fence wait prevents it"
> reasoning covered *submission* order, but the per-target draw scope
> (`countGroupsForTarget` draws only the current identity's groups at
> a flush) means the composite's target-0 group draws first while the
> FBO's `47869b33` groups stay queued past the composite's sample.
> `flushPendingBeforeGlDraw` on the unbind flushes the *bound* target,
> not the FBO's just-vacated one. **The fix is the cross-target
> ordering flush** — draw the queued FBO groups before the composite
> — the same fix I proposed in round-55 and withdrew; `drawnSoFar=1`
> is the measured proof task-76's audit couldn't see. `layout=1`/
> `standsForLivePair=true` are consistent (the composite binds the
> right image — it's sampling *empty* content, not a wrong image).
>
> **Round-75 — the concrete insertion point**: `flushImmediateWindow:6913`
> (`if (countGroupsForTarget(currentIdentity()) == 0) return` — draws
> only the current identity's groups). Two fix shapes, smallest first:
> (a) at the same point, before the current identity's groups draw,
> drain the queued groups of every *other* target that produces a
> published texture this window's groups sample — a producer-side
> `celtium$queuedForTargetUsesPublished`/`queuedGroupUsesTexture`
> check: if a queued target-`T` group's accepted texture is a
> published name and this window has a group sampling it, run `T`'s
> groups first. (b) the broader invariant: `flushImmediateWindow`
> drains ALL queued groups in acceptance order, not just the current
> identity's — restores `acceptance-order = draw-order` globally, at
> the cost of more cross-target windows per flush. (a) is the minimal
> change that fixes the splash + loading composite; (b) is the
> invariant the per-target scope broke. Either way `drawnSoFar` for a
> composite-sampling group must be > the producing FBO's group count.
>
> **Round-76 — main's direct measurement**: `splashPairAfterCompositeArmed`
> arms a readback of the chain pair *at the composite's own draw-time*
> (read at the next window boundary, the earliest point outside a
> render pass) when a group samples a `published-image`. This is the
> clean version of the ordering test: `drawnSoFar=1` says the
> composite ran first; the readback says what the pair *held* — empty
> → the ordering defect confirmed directly; logo → the composite
> should have shown it and the defect is elsewhere (the sample
> mechanics). It will decide the ordering-vs-sample-mechanics question
> `drawnSoFar=1` strongly implies but doesn't alone close (the counter
> could count draws into a different image). The instrument comment
> names the gap correctly: "what the pair held *at that draw* is the
> one fact the present-time dumps cannot give."
>
> **Round-77 — `splashPairAfterComposite` read: `darkPixels=84512` —
> the pair holds the logo** (`centreRgb=(219,219,219)
> cornerRgb=(255,255,255)` — white ground + dark text, ~4% of
> 1920×1080). So the pair DOES get the logo — but the readback is at
> the *next window boundary* (post-current-window's submission), which
> does not pin whether it held it *at the composite's sample*: the
> FBO's `47869b33` pass draws through `recordImmediateOnlyPass`, which
> `immediateGroupsDrawn` does NOT count — `drawnSoFar=1` orders the
> composite only against other `drawPendingImmediate` groups (the first
> of which, `drawnSoFar=0`, went to a *different* target,
> `1200003891`). So the readback + `standsForLivePair=true` narrow it
> to: the composite binds the right pair, the pair holds the logo —
> either the logo landed *after* the composite's sample (ordering, the
> FBO's pass in the same/later submission) or it was already there and
> the sample mechanics lost it. The remaining discriminating measure:
> read the pair in a buffer recorded *immediately after the
> composite's group* (before `47869b33`'s groups record) — or print
> the pass's own group count so the composite's draw can be ordered
> against it directly.
>
> **Round-78 — the readback fires too late and `drawnSoFar` can't order
> it**: `splashPairAfterComposite` fires at `frameTarget.beginFrame()`
> (`:6938`) — the **next frame's** start, a whole frame after the
> composite — so `darkPixels=84512` only re-confirms the pair ends up
> with the logo (already known from present-time), not what it held
> *at the composite's sample*. And `immediateGroupsDrawn`
> (`noteImmediateGroupsDrawn:12735`) counts only `drawPendingImmediate`
> — `recordImmediateOnlyPass`'s off-target draws bypass it, so
> `drawnSoFar=1` orders the composite only against other immediate-
> window groups, never against the FBO's `47869b33` pass. The single
> discriminator that closes it: a **shared monotonic draw ordinal**
> bumped in BOTH `recordImmediateOnlyPass` AND `drawPendingImmediate`
> (or `immediateGroupsDrawn` incremented at the pass's draw too),
> printed on the composite's `MIRROR_DRAW` + on the `47869b33` pass's
> draw — composite-ordinal < pass-ordinal → ordering confirmed,
> composite-ordinal > pass-ordinal → sample-mechanics. Cheaper still:
> a `PAIR_AT_SAMPLE` readback recorded *inside the same window's
> command buffer right after the composite's group* (the
> `recordImmediateOnlyPass`/release-barrier point — a copy there is
> outside a render pass and before any later draws in the buffer).
>
> **Round-79 — a third mechanism candidate: the shared pair's
> per-identity clear.** `:7754-7770` `celtium$pairClearNeeded` →
> `offTarget.clearChainPair` — the chain pair is cleared once per
> frame *per content identity* at that identity's first non-main
> pass. `identityPairs=[chain-pair=5 identities]` — five targets share
> it — and the first `MIRROR_DRAW` of the frame is `drawnSoFar=0
> target=1200003891`: a *different* non-main target drew **before** the
> composite (`drawnSoFar=1`). If `1200003891`'s pass ran
> `clearChainPair` — the pair's content identity is per-frame, so an
> intervening identity's first pass can clear content another
> identity drew — the composite samples the **re-cleared** pair:
> `47869b33` drew the logo, `1200003891`'s pass cleared the shared
> pair, then the composite samples → white, with the pair holding the
> logo again only once `47869b33` re-drews (which is what
> `darkPixels=84512` at next-frame then reflects). The source
> documents this exact failure at `:7775`: "a clear that lands after
> their DRAW_SITE is what leaves the copy white." So the ordering
> branch is no longer just composite-vs-FBO — it's
> `47869b33`-draw vs the *next* `clearChainPair` vs the composite's
> sample. The discriminator is the same shared ordinal, now read
> three ways: `47869b33`-logo-ordinal, the `1200003891`-clear-ordinal,
> composite-sample-ordinal — logo < clear < sample → the intervening
> clear is the defect (the fix: don't clear the pair an identity is
> mid-read of, or give `47869b33` and the composite a pair identity
> the intervening clear doesn't own).
>
> **Round-80 — the shared pair has MULTIPLE producers + repeated
> clears** (`splash12`): `offTargetPerTarget` shows `47869b33` drew 1
> pass into the chain pair but `5d62610d` drew **16** and `1200003891`
> draws too — three non-main identities share it. `CHAIN_PAIR_CLEAR`
> fires at `drawsSoFar=5397,5399` (`site=immediate-only-window`) —
> repeated clears through the frame — plus `site=none drawsSoFar=0`
> at frame start. So the pair is a contended resource: `47869b33`'s
> one logo pass is one small producer among several, and the pair is
> cleared repeatedly by the others. The composite samples at
> `drawnSoFar=1` — early — but `47869b33`'s pass goes through
> `recordImmediateOnlyPass` which `immediateGroupsDrawn` never counts,
> so its position vs the composite is still the unpinned fact. What
> the contention adds: even if `47869b33` drew the logo *before* the
> composite, a `5d62610d`/`1200003891` clear *between* them would
> erase it — the pair is cleared per content-identity, and three
> identities share it, so "was the logo there at the composite's
> sample" needs the logo-draw ordinal AND every clear's ordinal in the
> same counter. The single-number discriminator from round-78 stands,
> now needed at every `clearChainPair` + every `recordImmediateOnlyPass`
> draw.
>
> **Round-82 — `drawnSoFar` REFUTES ordering: `47869b33` drew FIRST.**
> `47869b33` hex = `1200003891` decimal — the `drawnSoFar=0
> target=1200003891` line IS the splash FBO's own pass, drawn at
> ordinal 0 — *before* the composite's `drawnSoFar=1`. So the logo
> was in the pair when the composite sampled; the composite draws
> correctly and binds the right pair (`standsForLivePair=true`) yet
> produces white → the defect is in the **sample mechanics**, not the
> ordering. The intervening-clear branch narrows too: between
> ordinal-0 (logo) and ordinal-1 (composite) there is no room for a
> `clearChainPair` — the frame-start clear is `site=none
> drawsSoFar=0` *before* `47869b33`'s draw, not after. **This returns
> the mechanism to the layout track**: the composite samples a pair
> that holds the logo, with `layout=`GENERAL at its draw — the image
> in `GENERAL`, the descriptor expecting `SHADER_READ_ONLY` → the
> RADV zero-sample → white. And the earlier "`recordImmediateOnlyPass`
> isn't counted" objection is void: the pass calls
> `drawPendingImmediate` itself (`:7880`), so `immediateGroupsDrawn`
> does count its draws. Ordering eliminated; the layout drift
> (`confirmed` not updated by the implicit attach-transition →
> `ensureSampledLayout` skips → GENERAL sample → RADV zeros) is the
> standing mechanism — the `PAIR_AT_SAMPLE`/`pending`-barrier check
> would confirm whether the barrier committed.
>
> **Round-82 — the honest state of the layout branch**: `layout=1`
> (`GENERAL`) at the composite's draw is `bound.foreignLayout()` — the
> shared `confirmed` — read at *record*-time. If `confirmed`=GENERAL
> there, `ensureSampledLayout` *would* have issued the barrier
> (GENERAL→SHADER_READ) — which is the *correct* path — so a GENERAL
> print does not by itself prove the skip happened; it only proves
> the tracker knows the image is `GENERAL` (consistent with the attach
> having landed it there). The residual question is therefore not
> "is the tracker wrong" but **did the `GENERAL→SHADER_READ_ONLY`
> barrier actually commit into the composite's command buffer** — a
> barrier recorded into a *different* (earlier/later) buffer than the
> one the composite ships in leaves the image `GENERAL` at the draw
> → RADV zeros. That is the one unfalsified step: `PAIR_AT_SAMPLE`
> (a copy recorded right after the composite's group, before the
> release barriers) reading non-empty ⇒ the barrier committed and the
> loss is downstream of the sample; empty/undefined ⇒ the barrier
> never committed for this draw ⇒ the layout skip. Ordering is
> eliminated; the sample's layout-commit is the open question.
>
> **Round-83 — `FRAME_CLEAR_MARK` gives the verdict: the composite
> DREW, its sample returned white.** Main's marker clears the frame
> image to green at the pass head; `splashmark`'s presented loading
> frame is `top=(255,255,255)×2073600, green=0` — **zero green
> pixels**, so the frame's pass drew over it — the composite's draw
> landed, and what it produced was **white**. This separates
> draw-loss from sample-loss cleanly: green would mean every draw of
> the pass was lost; uniform white means the composite drew and its
> *sample returned white*. Combined with `47869b33` drawing first
> (`drawnSoFar=0`) and `standsForLivePair=true` (the composite binds
> the right pair), the chain is complete: the pair held the logo, the
> composite bound it, the composite drew — and its sample returned
> white → **the sample itself lost the logo**. That is the layout
> drift's signature precisely: the chain pair is in `GENERAL`
> (the FBO's implicit attach-transition, untracked by `confirmed`),
> the descriptor declares `SHADER_READ_ONLY` → RADV bug 110259
> samples it as zeros → white over the green. Ordering, routing,
> staleness, UV, wrong-texture, draw-loss — all eliminated. **The fix
> is the two-tracker unification** (round-70): record
> `GllessFrameTarget`'s attach-transition into `FOREIGN_STATES`, or
> make `ensureSampledLayout` read `colorImageLayout()` as truth.
>
> **Round-84 — the fix is a single insertion**: `GllessFrameTarget.
> recordLayoutBarriers` (`:855-890`) already records an **explicit**
> `vkCmdPipelineBarrier` moving the pair (`oldLayout=this.
> colorImageLayout` → `newLayout=GENERAL` on release / to
> `COLOR_RENDER_LAYOUT` on acquire) — it just never tells the shared
> ledger. `VulkanGlTexture.noteForeignTransition(commandBuffer, image,
> from, to, unboundOut)` (`:701`) is the existing register-an-
> already-recorded-barrier hook. So: after the `vkCmdPipelineBarrier`
> in `recordLayoutBarriers`, call `noteForeignTransition(command,
> this.colorImage, oldLayout, newLayout, null)` (and the same for
> `depthImage` if a wrapper can sample it) → the shared `confirmed`/
> `pending` tracks the explicit transition → the next wrapper's
> `foreignTransitionFrom` reads `GENERAL`, issues the `GENERAL →
> SHADER_READ_ONLY` barrier for the descriptor → the composite's
> sample reads the logo instead of RADV zeros. `recordLayoutBarriers`
> is the single place every acquire/release moves the pair — one call
> per image closes the two-tracker gap for the splash, the loading
> composite, and every other published-name sample of a pair.
>
> **Round-85 — accessibility verified**: `noteForeignTransition` is a
> package-private `static` (`VulkanGlTexture:701`) and `GllessFrameTarget`
> sits in the same `org.soutalab.celtium.vulkan` package → the fix is a
> direct call `VulkanGlTexture.noteForeignTransition(command,
> this.colorImage, old, new, null)` inside `recordLayoutBarriers`, after
> the `vkCmdPipelineBarrier` — no new imports, no new plumbing. The
> `unboundOut` arg may be `null`. One call per image, at the single
> place every acquire/release moves the pair.
>
> **Round-88 — `layout=1` WEAKENS the layout-drift**: the composite's
> `layout=` printed `1` = `VK_IMAGE_LAYOUT_GENERAL` — the shared
> `confirmed` for the chain pair, read at descriptor-write. If
> `confirmed`=GENERAL, the descriptor declares `GENERAL` **and** the
> image is in `GENERAL` — consistent, *correct* — the sample should
> read the logo. The drift mechanism needs `confirmed`=SHADER_READ_ONLY
> (stale) → descriptor SHADER_READ_ONLY + image GENERAL → RADV zeros.
> `layout=1`=GENERAL means the tracker and the image *agree* — so the
> layout skip is NOT the producing defect after all (it would show
> `layout=5`, not `1`). This returns the open question to the
> `PAIR_AT_SAMPLE` direct read: `47869b33` drew the logo at
> `drawnSoFar=0`, the composite binds the right pair
> (`standsForLivePair=true`) with a *consistent* layout at
> `drawnSoFar=1`, and produces white — the one fact still unmeasured
> is what the pair **held at the composite's buffer position** (a
> copy recorded right after the composite's group in the same
> command buffer, before the release barriers). If that reads the
> logo, the loss is inside the composite's own draw (a UV/pipeline/
> colour-write residual); if it reads cleared/empty, a draw landed
> between the logo and the composite that `drawnSoFar` didn't order
> (a `clearChainPair` or a different-target pass recorded between
> ordinals 0 and 1 in the same buffer).
>
> **Round-89 — `frameImageReadbacks` + the marker reveal a PARTIAL
> quad**: `splashmark2`'s window-boundary reads show the frame image
> `centreRgb=(219,219,219)` (white-ish) but `cornerRgb=(73,95,36)` —
> the green marker colour surviving at the corner — and a read
> `darkPixels=1424` against the pair's own `84512`. So the
> composite's draw does NOT cover the full framebuffer: it paints
> white over the centre while the marker survives at the corner. A
> `framebufferRender`/`loadingProgress` quad is a *partial* quad —
> the loading screen's progress bar, not a full-screen blit — so
> this is consistent with the composite drawing its small quad white
> (the sample-loss) while the rest of the frame kept the marker
> until later fills painted over it (uniform white at present). The
> marker's remaining discriminating read: does the composite's quad
> *region itself* hold white while the pair's corresponding region
> held the logo — the pair's 84512-dark-pixel logo vs the quad
> region's colour. The UV comment in main's new code names the
> mechanism: "a quad that covers the framebuffer and samples a
> picture the pair holds can still paint one colour" — degenerate or
> clamped-`0..1` UVs on the composite quad → samples one texel → a
> flat colour. The pair holds the logo; the composite samples it —
> whether the sample returns the logo or one white texel is the UV
> question, and the surviving-green corner proves the quad's
> coverage is already not what `framebufferRender` should produce.
>
> **Round-90 — the composite's quad is BOTH partial-cover AND
> white-sampling** — two aspects of one broken quad. `splashflip`
> `IMMEDIATE_ENQ group=2 proj=(0,0,0,-0.5) mvT=0 fmt=2 texture=6
> batches=1`: a single `POSITION_TEX_COLOR` quad. The marker's green
> surviving at the *corner* across boundaries means the quad's
> **position** never reaches the corner — not just that the sample
> is white (which it also is, over the covered centre). A
> `framebufferRender` should rasterize the whole framebuffer; a quad
> whose position transform maps it to a centred sub-region leaves
> the corners untouched — exactly the green-survives picture. So the
> composite's defect is a **transform/coverage** one: the quad's
> `proj`/vertex mapping places a partial rectangle (the centre, which
> it fills with the white its sample returned), and the sample within
> it is white too. Whether the partial coverage and the white sample
> share one cause (degenerate UVs mapping the quad to a point AND
> sampling the clear texel) or two (a wrong ortho mapping + a
> separately-white sample) needs the quad's raw vertex positions +
> texCoords — print the four vertices' position+UV of the
> `texture=6` group's batch. `proj=(0,0,0,-0.5)` is the ortho; the
> menu's identical `framebufferRender` renders correctly, so the
> difference is in *this* composite's transform or the texture the
> ortho was sized for.
>
> **Round-91 — `proj=` shows only z, not the coverage**: the
> `IMMEDIATE_ENQ` print is `projection[2],[6],[10],[14]` — the
> z-column — so `0.0,0.0,0.0,-0.5` is a flat-z ortho (m22→0,
> m32=-0.5), which says nothing about the x/y coverage (m00,m11,m03,
> m13 — the elements that decide whether the quad fills the frame).
> The marker's surviving green corner already proves the partial
> coverage directly; the *cause* is in the unprinted x/y transform or
> the raw vertex positions. The discriminator stays: dump the
> `texture=6` batch's four vertices (position + texCoord) — the
> positions say whether the quad is a centred sub-rect or a
> collapsed point, the UVs say whether it samples the logo's region
> or one clamped texel. `mirrored=true` on the group confirms it's
> the same `POSITION_TEX_COLOR`/`framebufferRender` path the menu
> renders correctly, so the difference is in the values this
> composite fed the shared path, not the path.
>
> **Round-92 — the composite's group carries the WRONG MATRICES —
> a stale matrix snapshot, not `framebufferRenderExt`'s own state**:
> `Framebuffer.framebufferRenderExt` (`:241`) sets
> `GlStateManager.ortho(0,w,h,0, 1000,3000)` — whose m32 =
> `-(far+near)/(far-near)` = `-4000/2000` = **-2** — and
> `GlStateManager.translate(0,0,-2000)` — whose modelView[14] =
> **-2000**. But the composite's `IMMEDIATE_ENQ` prints
> `proj[14]=-0.5` (≠ -2 — a near/far ≈ -1,3 ortho) and `mvT=0,0,0`
> (≠ z=-2000 — an identity modelview). **Neither captured matrix
> matches the state `framebufferRenderExt` itself installs** — so the
> group's projection+modelview snapshot was taken BEFORE
> `framebufferRenderExt`'s `matrixMode(5889)+ortho+matrixMode(5888)+
> translate` ran, or read a different matrix slot: a **stale matrix
> capture** on the composite's enqueue. Under a stale/wrong
> transform the quad's NDC positions map to a centred sub-rect (the
> surviving green corner) and its UV interpolates toward a single
> sampled texel (the white it paints) — one stale-matrix defect
> produces BOTH the partial coverage and the white sample. The
> menu's `framebufferRender` renders correctly, so the stale read is
> specific to the *order* this splash's composite enqueues vs when
> its group's matrices are snapshotted — the group captures the
> matrices at enqueue-time, and for this group that's a snapshot
> from before `framebufferRenderExt`'s own `GlStateManager.ortho`/
> `translate` reached the matrix the capture reads.
>
> **Round-93 — the defect site is the mirror-vs-live matrix source**:
> the group's `projection[]`/`modelView[]` come from
> `immediateState.matrix(MATRIX_MODE_*)` — the **mirrored** matrix —
> not the live `glGetFloatv(GL_*_MATRIX)` (`:3694` reads the mirror,
> `:4076` the live path; a delta is even computed between them at
> `:3695`). `framebufferRenderExt`'s `GlStateManager.ortho`/`translate`
> update the *GL stub's* matrix stack; they only reach `immediateState`
> through the hooked `GlStateManager` mixin → `immediateState.setMatrix`.
> The captured `m32=-0.5` / `mvT=identity` mean the mirror held a
> **different** projection and an **identity** modelview when this group
> enqueued — so for the splash's composite the `ortho`/`translate` did
> NOT land in the mirror before the snapshot (or the snapshot read a
> different slot). The menu's composite renders correctly, so the
> mirror DOES track `GlStateManager` calls in general — the splash's
> composite enqueues through a path where the mirror lags the stub:
> candidates are (a) a snapshot taken at `offerImmediate`-group-open
> *before* `framebufferRenderExt`'s matrixMode/ortho/translate ran,
> (b) a `GlStateManager` call on this path that bypasses the mirror
> (a direct `glOrtho`/`glTranslate` that doesn't hit the mixin), or
> (c) the `derivedProjectionPending`/`worldSnapshot` substitution
> (`:4553`) writing a stale projection into the mirror. The fix is to
> make the composite's group read the live `glGetFloatv` pair at draw
> (the `groupMatricesFromMirror` flag `:955` is exactly the switch —
> its "mirrored state instead of a live glGetFloatv pair" is the
> mechanism), or to flush the mirror through the `GlStateManager`
> hooks on this path.
>
> **Round-94 — `meshVertexPreview` clears the quad: the composite's
> geometry is CORRECT**. `splashrebuild` `uvRange=` for `texture=6`:
> `v={(-1,-1,1, u0,v0) (1,-1,1, u1,v0) (1,1,1, u1,v1) (-1,1,1, u0,v1)}`
> — a **full-screen NDC quad** with correct `[0,1]` UVs — the quad
> covers the frame and samples the pair's whole region. So the
> surviving-green corner was just a mid-boundary read (later draws
> paint it), not a coverage defect — and the matrix mismatch (round
> 92-93) explains only the `-2000`/`-0.5` fields, not the quad (the
> vertex positions are already NDC — the projection is applied to
> them; a `-0.5`-ortho on NDC inputs maps them off-screen, which
> *contradicts* the quad covering the centre unless the projection
> is effectively identity for it — the matrices are wrong for the
> *print* but the quad's own coords are clip-space, so coverage is
> correct either way). What remains is ONLY the white sample:
> `47869b33` drew the logo at `drawnSoFar=0`, `CHAIN_PAIR_CLEAR`
> shows `site=none` at `drawsSoFar=0` then nothing until `4142` —
> **no clear between the logo and the composite's `drawnSoFar=1`** —
> and the composite binds the live pair (`standsForLivePair=true`)
> with correct UVs. The one fact still unmeasured is the pair's
> content at the composite's **buffer position in GPU submission
> order**: `drawnSoFar` is CPU-record order; if `47869b33`'s
> `recordImmediateOnlyPass` records into a *different* command
> buffer / submission than the composite's flush-draw, the composite
> can sample the pair before the logo's submission commits even
> though `drawnSoFar` orders them 0→1. The discriminator is the
> `PAIR_AT_SAMPLE` copy recorded *in the same command buffer right
> after* the composite's draw — if it reads the logo, the loss is
> inside the composite's draw; if it reads cleared/empty, the logo
> hadn't committed at that buffer position (a cross-submission
> ordering gap `drawnSoFar` can't see).
>
> **Round-95 — ordering is now *completely* eliminated**:
> `recordImmediateOnlyPass` records into `this.commandBuffer` (inside
> `recordAcquireBarriers(this.commandBuffer)`, `:858`) — the MAIN
> command buffer, the same one the composite's flush-draw records
> into → same submission → per the spec, commands within a
> submission run in record order (`drawnSoFar` 0→1) → the logo
> commits before the composite samples. No cross-submission gap is
> possible. So the eliminated list is now total: ordering (intra- and
> inter-submission), routing (`standsForLivePair` — the composite's
> `bound.image` IS offTarget's chain-pair, the same image `47869b33`
> attached), coverage (full-screen NDC quad), UV (`[0,1]`), no clear
> between (`CHAIN_PAIR_CLEAR` 0 then 4142). The composite samples a
> logo-bearing pair with a correct quad and produces white. The only
> explanations left are *inside the sample itself*: (a) the
> descriptor's bound view/image is not the pair's despite
> `standsForLivePair` (the view's image vs its `VkImageView` — a
> view over a different subresource), or (b) the image's layout at
> the draw is not the `GENERAL` the descriptor declares (the
> confirmed-vs-actual drift again — `layout=1` was `confirmed` at
> record; the *actual* layout at execution is untracked if the
> attach moved it after the descriptor was written). The
> `PAIR_AT_SAMPLE` copy (the pair's content at the composite's
> buffer position) + the descriptor's `VkImageView`-vs-image
> comparison are the last two instruments needed.
>
> **Round-96 — the view is verified too: `view=pairView=
> 0x7f59b13a8e60`, `standsForLivePair=true`**. The composite's
> descriptor samples the pair's OWN view — the branch (a) "a view
> over a different subresource" is eliminated. So every fact about
> the composite's *binding* is correct: the right `VkImage`
> (`pairImage`), the right `VkImageView` (`view=pairView`), correct
> `[0,1]` UVs, a correct full-screen NDC quad, the logo committed
> before it in the same command buffer, no intervening clear. The
> sample still returns white. The single remaining mechanism is a
> **layout mismatch at the draw**: `layout=1`(GENERAL) is `bound.
> foreignLayout()` — the *confirmed* at descriptor-write — but the
> descriptor's `imageLayout` field commits that value to the
> descriptor set; if the image's *actual* layout at the draw is not
> `GENERAL` (a transition between the descriptor write and the
> draw — e.g., the acquire moved the image to `GENERAL` after the
> wrapper's tracker had it at `SHADER_READ_ONLY`, or vice versa —
> the two-tracker divergence again), VUID-00344's undefined read
> applies and RADV returns zeros. The `PAIR_AT_SAMPLE` copy remains
> the last instrument: reading the pair's content at the composite's
> exact buffer position. If it reads the logo while the composite
> painted white, the loss is inside the composite's *draw* (a
> colour-write/pipeline state that outputs white regardless of the
> sample — but `framebufferRenderExt`'s `color(1,1,1)`+`disableBlend`
> makes that a pure sampled-colour write); if it reads empty, the
> pair's GPU-timeline content at that position is empty despite
> `drawnSoFar=0`'s logo being earlier in the same buffer — which
> cannot happen per the record-order guarantee, so it would point
> back at the descriptor's bound *resource* (image+view are right,
> but the sample itself resolves to zeros — a layout or a
> driver-side feedback-loop issue).
>
> **Round-97 — `splashPairAfterComposite` still fires too late**:
> it records its readback at `frameTarget.beginFrame()` — the *next*
> frame's acquire — long after the composite sampled. What is needed
> is `PAIR_AT_SAMPLE`: a `vkCmdCopyImageToBuffer` recorded **inside
> the same command buffer, immediately after the composite's group
> draw** (in `drawPendingImmediate`, the branch where `bound.
> standsFor(livePair)` just returned true). At that exact buffer
> position the pair holds whatever the composite's sample saw — the
> logo if the sample should have read it, cleared/white if a
> recorded-before-it operation emptied the pair in a way
> `drawnSoFar` can't order (a `ensureChainPair` recreate handing the
> composite a descriptor on the OLD image handle — but
> `standsForLivePair=true` rules a handle change out — or a
> `firstAcquire` clear landing between `47869b33`'s pass and the
> composite within `drawnSoFar=0..1`). If `PAIR_AT_SAMPLE` reads the
> logo while the composite painted white, the loss is the
> descriptor's *resource* (layout-at-draw, or a view over a
> subresource the handle-equality doesn't cover — e.g., the view's
> `baseMipLevel`≠0 sampling an unwritten mip). If it reads white,
> the pair's content at that buffer position is white — the logo
> wasn't there in GPU order despite `47869b33` recording it first —
> which would force the only remaining explanation: `47869b33`'s
> logo-draw and the composite's sample target **different images
> that share the same `VkImage` handle** (a pair recreated between
> them — `ensureChainPair` at a new size — where the composite's
> `published` wrapper still points at the old handle that now backs
> a cleared image). `created=2`/`declaredMismatches=1` keep this
> branch alive: two `chainPair` creations were counted, and the
> composite's `shareExternal` declared `GENERAL` against an
> already-recorded `SHADER_READ_ONLY` handshake — a recreated-pair
> publish would produce exactly this signature.
>
> **Round-98 — `celtium$transitionPublishedFrameTextures` resolves
> the layout branch and promotes the recreate**: `ensureGeneralLayout`
> /`ensureSampledLayout` go through `transitionForeignLayout` → they
> update `confirmed` **and** move the image — so the wrapper's tracker
> is *unified* for the published-texture transitions (`:11005`). The
> only untracked mover is `GllessFrameTarget.recordLayoutBarriers`
> (the target's own `colorImageLayout` bookkeeping, an explicit
> barrier with no `noteForeignTransition`). With `layout=1`=GENERAL —
> `confirmed`=GENERAL — and the attach's `recordLayoutBarriers` moving
> the actual image to GENERAL, descriptor and actual agree → the
> layout-mismatch branch needs `confirmed`≠actual, which
> `layout=1` doesn't show. That leaves the **content**: the composite
> samples the right image+view with correct UVs and reads white → the
> pair's content *at the draw* is white. The mechanism that produces
> it: **`47869b33` drew the logo into the chain-pair image that was
> live at `drawnSoFar=0`; between that draw and the composite's
> sample, `ensureChainPair` recreated the pair (`created=2` — two
> `chainPair` creations counted); the new pair is empty/cleared, and
> the composite's `published` wrapper now names the NEW image** —
> `bound.image == celtium$livePairImage()` still holds
> (`standsForLivePair=true`) because `livePairImage()` reads the
> *current* pair, but the *logo* is in the retired old image. A later
> pass re-drawing into the new pair is why `splashPairAfterComposite`
> (next-frame) still shows `darkPixels=84512` — the pair ends up
> with the logo, just not at the composite's buffer position.
> `PAIR_AT_SAMPLE` is the discriminator and would read the new pair's
> cleared content → confirming the recreate over the layout branch.
>
> **Round-100 — `livePairImage()` == `chainPairColorImage()` — one
> image, fully converged**: `livePairImage()` returns `offTargetTarget
> ().chainPairColorImage()` (`:10963`) — the *exact* image `47869b33`'s
> `recordImmediateOnlyPass` attaches (`offTarget.chainPairColorImage()`
> in its `chainIdentity` branch). So `pairImage=0x7fef50de3830` is ONE
> image end-to-end: `47869b33` drew the logo into it, the composite
> samples it (`view=pairView`), and `PAIRPOST` reads the logo in it
> (`darkPixels=84512`). The earlier `chainPair=0x7fed`/`defaultPair`
> handles are *other* pairs/other targets, not this image — so the
> "different images" branch is closed too. **Every fact is now
> verified consistent and the sample still returns white**: same
> `VkImage`, same `VkImageView`, correct `[0,1]` UVs on a correct
> full-screen NDC quad, logo committed before it in the same command
> buffer (`drawnSoFar=0→1`, same submission — ordering impossible),
> `confirmed`+actual both `GENERAL`. This leaves *two* explanations,
> both needing a GPU-timeline measurement, not more static reading:
> (a) the pair's content **at the composite's buffer position** is
> white — something between `47869b33`'s pass and the composite's
> draw in the same buffer re-cleared it (the `PAIR_AT_SAMPLE` copy);
> or (b) the sample resolves to zeros on a correct-looking
> configuration — a driver/layout-at-execution issue (RADV bug-110259
> on a `GENERAL` image the tracker believes is `SHADER_READ_ONLY`, or
> a feedback-loop: the composite's pass attaches the SAME image it
> samples — the window composite draws to the frame image while
> sampling the pair, but if any group's attachment aliases the
> sampled pair, the read is undefined). The `PAIR_AT_SAMPLE` copy +
> a `passAttachedColorImage == bound.image` check on the composite's
> own pass are the last two instruments.
>
> **Round-101 — the feedback-loop branch is eliminated too**:
> `attachedImage=0x7fee963be0b0` (the frame image) ≠
> `chainPair=0x7fef50de3830` (the sampled pair) — the composite's
> pass attaches the frame image and samples the pair → different
> images → no attachment/sample aliasing → not a feedback loop.
> Every branch is now exhausted except one GPU-timeline fact: the
> pair's content **at the composite's buffer position**. The
> composite samples the exact image `47869b33` drew the logo into
> (`pairImage`, `view=pairView`, `standsForLivePair`), commits
> before it in the same command buffer (ordering impossible), with
> correct UVs on a correct NDC quad, confirmed+actual both `GENERAL`
> — and paints white. If `PAIR_AT_SAMPLE` (a copy right after the
> composite's draw in the same buffer) reads the logo, the loss is
> *inside* the composite's draw — a pipeline state that outputs
> white regardless of the sample (but `framebufferRenderExt`'s
> `color(1,1,1)`+`disableBlend` is a pure sampled-colour write). If
> it reads white/empty, the pair's GPU-timeline content at that
> position is empty — which would mean `47869b33`'s logo-draw into
> `chainPairColorImage()` did not land before the composite sampled
> the same `livePairImage()` — the only way that happens is if the
> draw's recorded commands were *dropped* (an `immediateLastRefusal`,
> a group marked `refused`, or a `unflushedTargetGroupsDropped`)
> rather than executed — the batch-count check (`batches=1` on the
> composite's group vs the logo-draw's) or the `immediateTaken`/
> `immediateRefused` counters for `47869b33`'s group would show a
> refused/dropped logo-draw. That is the branch worth the next probe:
> did `47869b33`'s `recordImmediateOnlyPass` actually *execute* the
> logo batches, or just record them into a group that was then
> refused/dropped before draw?
>
> **Round-101b — the logo-draw executed too**:
> `unflushedTargetGroupsDropped=0`, `immediateTaken=11876` (no
> refusals), `offTargetPerTarget` shows the splash target's
> `drawnPasses=1` into `chain-pair` — the logo-draw *executed*, it
> was not refused or dropped. So the complete verified chain: the
> logo-draw *executed* into `chainPairColorImage()`, the logo is in
> that image (`PAIRPOST` `darkPixels=84512`), the composite samples
> that exact image (`view=pairView`, `standsForLivePair`), commits
> *after* it in the same command buffer (`drawnSoFar=0→1`), with a
> correct full-screen NDC quad + `[0,1]` UVs, confirmed+actual both
> `GENERAL`, attaching the *frame* image (no feedback loop), no
> clear between (`CHAIN_PAIR_CLEAR` at `drawsSoFar=0` then `4142`,
> the composite at `1`), nothing refused or dropped — and paints
> white. **This is now a contradiction only a GPU-timeline read can
> resolve**: `PAIR_AT_SAMPLE` — the pair's content at the composite's
> buffer position — is the one measurement that says whether the
> logo was *there in GPU order* when the sample ran. If it reads the
> logo, the loss is inside the composite's draw (a pipeline/blend
> outputting white on a correct sample — the last static branch); if
> it reads white, the logo's recorded-then-executed draw still
> didn't land *at that buffer position* — which would point at the
> one thing not yet verified inside `47869b33`'s own pass: whether
> its `drawPendingImmediate` actually ran the logo's *batches* at
> that buffer position, or the pass recorded the group and the
> logo's vertices were transformed off the pair (a logo-draw that
> drew nothing where the pair was cleared — the logo-draw's own
> matrix/geometry, the same class of defect the composite's quad
> was checked for).
>
> **Round-105 — the missing `COLOR_ATTACHMENT_WRITE → SHADER_READ`
> visibility barrier is the mechanism** (the one not yet eliminated):
> `GllessFrameTarget.recordLayoutBarriers` (`:861-895`) uses
> `colorAccess=COLOR_ATTACHMENT_READ|WRITE` — release `srcAccess=
> colorAccess, dstAccess=0`; acquire `dstAccess=colorAccess`. It
> moves the *layout* only — **it never records a `COLOR_ATTACHMENT_
> WRITE → SHADER_READ` visibility dependency**. `47869b33` wrote the
> logo as `COLOR_ATTACHMENT_WRITE`; the composite samples it as
> `SHADER_READ`. The composite's `ensureGeneralLayout` — `confirmed`=
> GENERAL (the `shareExternal` declared GENERAL) → wants GENERAL →
> image already GENERAL → **skips** → records *no* barrier → **the
> `SHADER_READ` is never made dependent on the `COLOR_ATTACHMENT_
> WRITE`** → the fragment-shader read isn't synchronized with the
> logo-draw → it reads un-flushed/pre-write data → **white**.
> `PAIRPOST` reads the logo because a *later* `TRANSFER_READ`
> provides the visibility — the pair HAS the logo, it's just not
> visible to *this* `SHADER_READ`. The `GENERAL`-descriptor is the
> broken case specifically: a `SHADER_READ_ONLY`-descriptor sample
> would go through `transitionForeignLayout` (GENERAL→SHADER_READ_ONLY
> *with* `SHADER_READ` dstAccess) which records the visibility — the
> GENERAL descriptor skips it entirely. **Fix**: a pure visibility
> barrier on the composite's sample path — `srcStage=COLOR_
> ATTACHMENT_OUTPUT, srcAccess=COLOR_ATTACHMENT_WRITE, dstStage=
> FRAGMENT_SHADER, dstAccess=SHADER_READ`, no layout change — even
> when the layout is already `GENERAL`. `PAIR_AT_SAMPLE` would read
> the logo (a `TRANSFER_READ` makes it visible, like `PAIRPOST`),
> confirming the visibility-gap over a content gap.
>
> **Round-107 — BREAKTHROUGH: the `PAIR_PROBE_CLEAR` magenta test
> proves the composite's sample WORKS — the white is the PRESENT
> path, and the visibility-gap hypothesis is refuted**. `PAIR_PROBE_
> CLEAR` fills `pairImage=0x7fb634da9e00` with magenta every frame.
> `FRAME_AT_WINDOW` then reads `frameTarget.colorImage()` —
> `centreRgb=(255,0,255)` = **MAGENTA** (`cornerRgb=(73,95,36)` the
> surviving green marker). So the composite **sampled the magenta
> pair correctly and painted magenta into the frame image** — the
> sample was *never* losing the content; the visibility-gap and
> every "composite reads white" hypothesis are refuted by the frame
> image holding magenta. The presented frame is uniform **white**
> while `frameTarget.colorImage()` holds magenta → **the frame
> image's content does not reach the presented surface**. The
> defect is downstream of the composite: the present/output path
> presents a *different* (white) image than the `frameTarget.
> colorImage()` the composite + the other `windowDrawsToFrameImage=
> 1173` draws wrote into. The composite's quad IS partial (magenta
> centre, green corner — it doesn't cover the corner), but the
> dominant white is the frame-image→present handoff dropping the
> content. **This relocates the whole investigation**: the composite
> path is *proven sound*; what loses the frame is whatever maps
> `frameTarget.colorImage()` to the presented swapchain image — a
> routing/blit/copy in the present path that reads a cleared or
> different image. The `READ_RESOLVE readFbo=0 mainIdentity=...
> currentTarget=0 currentIsMain=false` print (TextureCopyService)
> is the next place to look: which image the present/readback path
> resolves vs the `frameTarget.colorImage()` the composite wrote.
>
> **Round-108 — refinement: the magenta reaches `frameTarget.
> colorImage()` at window-time; the present blits *that same image*
> (`readImage=0x7fb579b55d70`==`frameImage`), so the white is a
> *later* draw into the frame image overwriting the composite**.
> `presentImage(this.frameTarget.colorImage(), …)` — the present's
> `readImage` IS the image `FRAME_AT_WINDOW` shows magenta in
> (`0x7fb579b55d70`), and `preRecordedPresentBlit` blits it
> (`srcOffsets(0,blitH,0)→(blitW,0)` → `dstOffsets(0,h-blitH,0)→
> (blitW,h)`, `blitW/min(width,swW)=1920`, `blitH=1080` — a correct
> full flip; `PRESENT_MARKER` paints RED not white, so it's off).
> So the present reads the *right* image and blits the full region
> — if it still reads white, the frame image's *content* is white
> at present-time. `windowDrawsToFrameImage=1173` — the composite
> paints magenta at `drawnSoFar=1`, but 1172 *later* draws write
> the same frame image — a subsequent draw that paints white (or
> a later `FRAME_CLEAR_MARK`-style clear, or a window-draw that
> fills white) overwrites the composite's magenta → the frame ends
> white → present blits white. `CLEAR_LATCH colour=(1.0,1.0,1.0,
> 0.0)` shows the game itself issues *white* clears (`route=pair`,
> `boundIsMain=false` — those land on the *pair*, not the frame
> image — but the same "game clears a framebuffer to white" path
> on `boundIsMain`/`route=frame` would hit the frame image). The
> discriminating read is the frame image **at present-time** (or a
> `drawsSoFar`-ordered dump of every draw that wrote
> `frameTarget.colorImage()` after the composite): whichever of
> the 1173 later window-draws / clears left white wins the presented
> frame. The composite itself is fully exonerated — magenta source
> → magenta in the frame at its buffer position → the loss is a
> *later* write into the same frame image before present.
>
> **Round-109 — the game's white clears land on the PAIR, not the
> frame — the frame's white is a different write**: every
> `CLEAR_LATCH` is `route=pair boundIsMain=false` — non-main clears,
> dropped by `applyGameClears`'s `!ownerIsMain` guard — so the game
> never white-clears the frame image. `neverCleared` clears only on
> a target's first `applyGameClears` (then `neverCleared=false` —
> subsequent window-passes don't re-clear). So the frame image's
> white is *not* a clear — it's a **draw**: one of the 1173 later
> `windowDrawsToFrameImage` writes white over the composite's
> magenta (a splash window-draw painting a white background, a
> `framebufferRender` of a white-cleared source, or a
> `glClearColor`-backed draw). The `drawsSoFar`-ordered dump of
> every draw that touches `frameTarget.colorImage()` after the
> composite — each with its group's `fmt`/`texture`/target — names
> the culprit directly. Candidates: a window-draw whose *source* is
> itself white (another `framebufferRender` of a framebuffer the
> game white-cleared → it composites white over the frame), or a
> draw with a full-screen white quad.
>
> **Round-60 — `publishedIsLivePair=true` (splash8) closes staleness;
> the leading residual is all-zero UVs on the composite quad**. The
> instrument printed the verdict directly: name 6's published image IS
> the live chain pair. With ordering, routing and staleness all ruled
> out, the remaining branch for a *uniformly* white quad sampling a
> logo-holding texture is the **vertex attribute**: if the composite's
> `POSITION_TEX_COLOR` quad has a missing/mismatched `texCoord`, the
> shader receives all-zero UVs → samples texel (0,0) — the top-left of
> the splash image, which is its white background → the whole quad
> reads that one texel → uniform white in the frame image. (Contrast
> with the menu: its composite carries `POSITION_TEX_COLOR` through the
> same sampler — a UV defect there would sample a corner too, but the
> panorama's corner isn't a single colour, so the menu's composite
> wouldn't read as blank.) The discriminating probe: read the composite
> quad's `tex(u,v)` values at enqueue — all-zero → the attribute is
> missing; correct UVs → the loss is in the sampler/descriptor after
> all.
>
> **Loading-screen connection, downgraded accordingly**: the ordering
> doesn't eat `setLoadingProgress`'s draws (the flush-on-unbind +
> fence wait preserve it) — but a *routing* miss could still leave
> them in the pair rather than the frame image, and the
> callback-never-firing remains defect 3's primary gate regardless.
>
> **Round-73 re-instates the composite-path connection — as the *same*
> defect, correctly understood**: `setLoadingProgress` does
> `bindFramebuffer → draws-into-FBO → unbindFramebuffer →
> framebufferRender` in one frame — the identical FBO→composite shape.
> With `drawnSoFar=1` proving the composite draws *before* the FBO's
> own groups (the per-target draw scope), the loading screen's
> composite would **also** sample the empty pair → white — *even when*
> its callback does fire. So the cross-target ordering flush is the
> shared fix for BOTH the splash logo and the loading screen's
> composite path; defect 3's remaining gate is the upstream
> callback-firing, which the fixture still doesn't exercise.
>
> **Round-49 — `87503bbe3` closes the under-blur residual**: the copy
> cache keyed its content marker on `lastDrawnEpoch`, which
> immediate-window draws don't move → 6 of the 7 blur passes were
> served the first pass's pixels → one-pass blur. Adding
> `immediateWindowsThisFrame()` to the marker makes every pass a fresh
> readback (`textureCopyReadbacks=525`, consecutive copies differ
> 96.5%) — the menu's blur now matches vanilla (distinct
> 113116→79370 = more blur, verified `r212-panblur-blurfix`). The
> sharp bottom-left patch is the blur's *source* region rendered into
> the frame image — still present because the composite's coverage
> ends short of that corner.
>
> **Code-level confirmation of the misclassification**
> (`CeltiumGlStub:998-1003`, `:1816-1817`): `pendingClearDrawFbo()`
> returns `-1` when no clear is pending and the `drawFbo` name otherwise —
> **owner==0 is unambiguously the default framebuffer** (GL name 0, the
> window = the frame image's own surface), and `pendingClearIsMain` is
> `drawFramebufferIsMain`, false for drawFbo 0. So `owner >= 0 &&
> !ownerIsMain` catches the default framebuffer in the net meant for
> game-owned FBOs. The correct guard is `owner > 0 && !ownerIsMain` (or an
> `owner == 0` fast path to the frame branch). Note the drop also eats
> the *colour* half of `clear(16640)` — invisible on screen only because
> `framebufferRender` repaints the image every frame.
>
> **Also ruled out this round**: the cube faces are NOT display-list draws
> — this Optifine `drawPanorama` tessellates 64 jitter samples × 6 faces
> directly (`POSITION_TEX_COLOR`, `alpha=255/(j+1)`,
> `TITLE_PANORAMA_PATHS[k]`) — so defect-4's `modelMeshes()==null` refusal
> does not apply here; the fragment in the texture is genuine face
> content. And `gluPerspective` DOES reach the projection mirror
> (`GluProjectionMixin` fires — verified via `gluPerspective:1032` in the
> matrixChannels line).
>
> **Round 38 mechanism detail** (verified in `GuiMainMenu` + stub +
> backend source):
> - The version text IS inside the copy's read region: it draws at GUI
>   bottom-left (x≈2, y≈230–270) → framebuffer rows 0–40, cols 0–160 —
>   inside `copyTexSubImage(0,0,256,256)`'s read rect (image rows 0–255 =
>   screen bottom). The blur feedback (`rotateAndBlurSkybox` ×7, straight
>   UVs `pos(0,0)-(480,270)` ↔ `tex(f1..1+f1, 0..1)` under the same 256
>   viewport) legitimately lifts and re-draws it — its presence in the
>   texture is not itself a defect; the rotation is. **Timeline confirmed
>   by the n1 dump** (`mirror-gl-1-256x256-l0-n1-obj46e82b2c`): the texture's
>   first write holds white + a clean cube fragment and NO text — the
>   rotated text arrives in later writes, consistent with the blur feedback
>   lifting the version-text region on subsequent frames.
> - ~~`gluPerspective` is census-only~~ — **checked and cleared**:
>   `GluProjectionMixin` (`@Mixin(org.lwjgl.util.glu.Project)`, HEAD-injects
>   → `backend.mirrorGluPerspective`) fires 1032 times in the panmirror-400
>   log (`matrixChannels=...gluPerspective:1032`), so the projection mirror
>   does receive the 120° frustum — Cleanroom's remapper evidently maps the
>   mixin target even though LWJGLXX's actual class is
>   `org.lwjglx.util.glu.Project`. The `SEM_MULT_MATRIX` census stays
>   deliberate for the other callers (item-camera, `glFrustum`).
> - `axisAlignedModelView` (`:6109`) checks off-diagonal zeros only — it
>   accepts `diag(±1,±1,±1)` — **all axis flips including rotZ(180) pass the
>   join test** for preTransformable groups and bake into vertices. A leaked
>   flip therefore joins GUI groups silently. Low-cost hardening: require
>   the positive-diagonal form for joins, or log a bounded line when a
>   negative diagonal joins a preTransform group.
>
> **Also resolved this leg**: the `r212-panmirror-400` world frame renders
> **right-side up** (sky top, ore-block terrain receding to the horizon) —
> defect 2's inversion is fixed in this build; the user's remaining
> 「視点」 symptom is camera-position, not orientation.
>
> **The shape decodes to a window-vs-drawable mismatch, not a group stamp:**
> vanilla's final `renderSkybox` stretch is a fullscreen quad
> `pos(0,l)-(k,0)` under `glViewport(0,0,mc.displayWidth,mc.displayHeight)`
> ([GuiMainMenu.java] — verified in the 1.12.2 source). A viewport
> `gl=(0,0,1486,934)` covers image rows 0..933 → missing image rows 934..1079
> = screen top ~144 + cols 1486..1919 = right 434 — **exactly the measured
> hole** (±2px tolerance). So `mc.displayWidth×displayHeight` = 1486×934 while
> the swapchain/drawable is 1920×1080. `mc.displayWidth` comes from
> `Minecraft.resize()` ← `Display.getWidth` (window logical size); the target
> comes from the swapchain extent (drawable physical size). Wherever the two
> disagree — a scaled output, a stale resize, or `Display.getWidth` answering
> the creation size — every `glViewport(0,0,displayW,displayH)` lands as a
> bottom-left sub-rect. Corroborating: my own 1280×720 run shows
> `GROUP_VIEWPORT gl=0,0 1281x720` — displayWidth 1281 vs target 1280, the
> same mismatch at off-by-one scale. Suspect site: whatever the stub/headless
> path feeds `Display.getWidth/Height` (or the resize event that sets them)
> vs the real surface extent. Not a `viewportValues` stamp bug: the group key
> already carries `currentViewportKey` (`:5439,6156`), so a batch under a new
> viewport starts a new group stamped `currentViewport()` — the stamp is
> honest; the *game's numbers* are small.

`VulkanTerrainGpuBackend.java:12426` (identical in the bisect jar at ~line 13013):

```java
int y = empty ? 0 : this.targetHeight - yBottom1;
```

**This is the leftover flip.** The shared frame image is bottom-up (GL texture
order: image row 0 = picture bottom; `VulkanSwapchain` presents it with a
read-back-to-front blit `srcOffsets (0,H)→(W,0)` — one global flip at present,
which is why full-frame GL content lands upright). A GL viewport
`(x, y_bottom, w, h)` therefore maps to image rows `y_bottom .. y_bottom+h`
directly — the correct conversion is `y = yBottom0`, no flip.

**The sibling scissor path, 60 lines below in the same function, already carries
this exact fix** (`:12481-12487`):

> "No y flip. … the shared image the scissor applies to is in GL texture order —
> row 0 is the picture's bottom (VulkanSwapchain blits it read back to front to
> leave the swapchain upright). So the box's rows in that image are `y..y+h`
> unchanged. **The flipped conversion (`targetHeight - (y+h)`) clipped the wrong
> band.**"

The viewport comment above the function (`:12396`) states the wrong premise
("Vulkan's [origin] is the top-left … so the y is flipped here"): the positive
viewport height does not make the image top-down — NDC+1 still lands at the
memory bottom of a bottom-up image.

Full-frame viewports are invariant under the flip (`y = H - H = 0`), which is
why only sub-frame viewports break: the panorama's GL `(0,0,256,256)` cube →
log `CELTIUM_GROUP_VIEWPORT gl=0,0 256x256 clipped=0,464 256x256` → lands at
image rows 464..719 = picture top → displays at screen top-left. The blur loop
"works" only because the copy path commits the same conversion — it reads the
displaced band back (see below) — so real panorama pixels reach the texture
(`TEX_COPY_SERVED dest=21 mean=233,235,242`, not the white of r179) but
mirrored, and each draw-back lands in the same misplaced rect. The sharp cube
fragment at top-left, the displaced+mirror blur at bottom-right and the grey
field are that wreckage.

**One-line fix**: `int y = empty ? 0 : yBottom0;` — same conversion the scissor
uses. Main's own commits already point here: `f8f65e063` "the panorama is
displaced, not absent" and `91adf40fe` "the copy writes the mirror correctly,
so the panorama defect is upstream of it" — the upstream is this line: the cube
lands at the mirrored band, and the copy paths read it back with the same
top-down assumption, so the whole blur loop looks "working" while displaced.

Spec check: the viewport transform is `yf = (py/2)·yd + oy` ([VkViewport]),
so with positive height NDC−1 lands at `viewport.y` (the region's first image
row) and NDC+1 at `viewport.y + height` — the GL rect's own row range on a
bottom-up image; `targetHeight − (y+h)` is the conversion a top-down image
would need, as the scissor comment already says. The known symptom pair for a
y-convention mismatch — "scene upside down" and "culling removes front faces"
([Sascha Willems]) — is exactly what the captures show; `VK_KHR_maintenance1`'s
negative-height flip ([VK_KHR_maintenance1]) was the earlier design the code
comments still describe, and the present-day convention instead keeps the
viewport positive and flips once at present.

[VkViewport]: https://docs.vulkan.org/refpages/latest/refpages/source/VkViewport.html
[VK_KHR_maintenance1]: https://docs.vulkan.org/refpages/latest/refpages/source/VK_KHR_maintenance1.html
[Sascha Willems]: https://www.saschawillems.de/blog/2019/03/29/flipping-the-vulkan-viewport/

**Two more code sites carry the identical top-down assumption** — the same bug
family the scissor fix already documented once:

- `VulkanGlTexture.blitRectangleFrom` (`:2509-2512`):
  `int sourceBottomRow = sourceHeight - sy;` then `srcOffsets (sx, H-sy) →
  (sx+w, H-sy-h)` — treats the source image as top-down. The frame image is
  bottom-up, so this reads the mirrored band *and* flips. For the panorama it is
  accidentally self-consistent with the viewport bug: the cube lands at rows
  464–719 and the copy "finds" it by reading the same wrong band — but the blit's
  flip puts the content into the texture mirror upside-down, which is why the
  blurred strip is both displaced and mirrored.
- `TextureCopyService.readImageRectangle` + `reverseRows` (`:609, :741-746`):
  the host path reads the image's memory rows `y..y+h` (which for a bottom-up
  image are already the GL-order rows) then *reverses* them on the premise the
  buffer arrived top-down — the same convention error, upside-down output into
  the mirror for the CPU path.
- `TextureCopyService.readImageRectangle`'s copy **offset** (now `:677`):
  `imageOffset(x, targetHeight - y - height, 0)` — a *fourth* instance of the
  conversion, **unfixed as of `640a1984a`**, and now *self-evidently* the
  active defect: that commit's own message reports "the mirror's content
  ends white, which says the copy is reading a white source rectangle" —
  with the viewport fixed the cube lives in rows 0..255, while this offset
  reads `720-0-256 = 464..719`, the cleared band it was written to protect
  against. `imageOffset(x, y, 0)` is the fix; no other candidate produces a
  white 256×256 from a source that holds the cube in rows 0..255. A *fourth* instance of the
  same conversion, and the one the current fix round has not touched. Its
  comment's premise was measured while the cube was already displaced by the
  viewport bug: "passing `y` straight through read the top rows — the cleared
  ones" is exactly what rows 0..255 look like once the viewport has moved the
  cube to 464..719. Under the corrected viewport the read rect's rows are
  `y..y+h` again, so the offset should be `y`. **This one is load-bearing, not
  latent**: the run log reports
  `lastTextureCopyBlitRefusal=[no mirror exists for GL texture 21]` — the
  panorama's DynamicTexture has no mirror, so its `glCopyTexSubImage2D` calls
  fall back to this host path; with the viewport and blit fixed but this offset
  left, the host path reads rows `H-y-h..H-y` = the now-empty mirrored band
  and the panorama goes white/grey again. (Also reached by `glReadPixels`-style
  partial reads through `celtium$readImageRectangle`.)
  **Coupled with the in-flight ordering fix**: the uncommitted
  `hasPendingImmediateWork()` change makes the copy flush the group's queued
  cube draws before reading — necessary but not sufficient. While the ordering
  bug existed the read rect was empty at *any* offset, masking this offset;
  once it lands, `:677` still reads `targetHeight-y-h..` and the mirror stays
  white. The copy only produces the cube once both are in.

The class convention ("the first version flipped it — that is the conversion
for a top-down image") therefore repeats **four** times: group **viewport**,
copy **blit src**, copy **readback reversal**, copy **readback offset**.

## Confirmed defect 2 — 3D content vertically inverted on screen (world view)

**Reframed after main's `6e75884c1` measurement** — the world leg's terrain
fixture rendered *upright* — the defect is **scene-dependent**, not a uniform
write-mirror. Two candidate mechanisms were weighed and one is now dead:

- ~~Camera below the world~~ — the save's `generatorOptions` is the empty
  string → the default superflat preset → surface at y≈4. The fixture camera
  at y=13.6 (pitch=20, down) is 9 blocks *above* ground, so an honest view
  would put ground in the lower frame. The capture shows blocks overhead and
  void below — geometrically impossible without inversion. Dead.
- **Content written Vulkan-order in-world** — the flip test produces a
  coherent flat-world scene, the TNT side texture is upside-down (readable
  after flipping the capture — a pure v-mirror, which an inside-out or
  below-world view cannot produce), and the fixture's own terrain scene
  renders upright — so the inversion engages only on the world content of
  this scene, in the frame image or its composite. Audit state: every write
  path reads correct on paper — positive dynamic viewports (incl. terrain
  pipeline line 1204), mirror-exact matrices (`matrixMirrorDelta=0.0` proj
  *and* view, 4001 comparisons), z-only clip remap (`vulkanProjection`,
  `remapClipDepth`), correct depthFunc→VkCompareOp mapping (`:6389-6396`),
  CW front face proven by `33e20313e`, single flip at present,
  identity-0→frame-image routing, pre-transform path excludes the composite
  (`:6127-6144`). The in-world composite batch itself was never logged
  (`CELTIUM_COMPOSITE` only printed the 854×480 splash batch), so its actual
  UVs are the one unverified link.

Separate, co-present defect the same run surfaced — **entities never draw**
(possibly defect 4): `modelDraws=0` with all 20 lists captured and built
(`modelMeshBuilt=20`, `missingMeshes=0`, `modelDrawRefusals=0`, `MODEL_DRAW`
default `true` and in the bisect jar). The reason is in the counters:
`displayListReplays=157` with **`displayListReplaysClosed=157` /
`displayListReplaysPassClosed=157`** — every `glCallList` arrives while no
immediate frame and no render pass is open, so `celtium$drawModelList`
(`:9889`) early-returns *before its try block* — **mechanism confirmed by
counter exclusion**: `CELTIUM_MODEL_STATE` (one-shot, first try entry) never
printed → the try never ran; `CELTIUM_MODEL_TEXTURE` printed once per list →
`drawModelList` was called for all 20 lists; `modelDraws=0` +
`modelDrawRefusals=0` rules out every path inside the try (each ends at one
counter or the other); `immediateOnlyPasses=11727` → `targetKnown` was true;
`MODEL_DRAW=true` and `MODEL_ONLY_LIST=0` in the bisect jar (verified at
`:12015` + no `-D` flags in the launch); `missingMeshes=0` → the get() lookup
never missed. The only remaining silent exit is **`modelMeshes()==null`** —
and the timeline proves it: first replay at log line 2691, first
`modelMeshBuilt` line at 2841 — `celtium$buildModelMeshes` runs only at a
*terrain-pass head* (`:9392`), and every one of the 157 replays (list 141 ×34,
131–137 ×12, 125 ×1 = the fixture's entity-capture burst: dragon + bipeds +
one player-arm draw) arrived in the early world frames **before the first
`recordRenderPass` ran**. With no terrain pass yet, `modelMeshes` was still
null → silent false → `displayListCancelled=0` → the GL body ran and drew
nothing (`CeltiumGlStub:1844-1854` names the gap verbatim). Vanilla calls
`glCallList` for every entity model (player arm/hand, mobs, the sky lists
when VBO is off), so the world loses all entities — a direct match for the
user's 「プレイヤーから離れている」 feel: first person with no
arm/item/entities reads as an empty, disembodied view. **Fix direction:
decouple the build from the terrain pass** — run `buildModelMeshes` lazily
inside `celtium$drawModelList` when `modelMeshes()==null` — verified viable:
`VulkanModelQuadCapture.listIds()` is `public static` (`:678`) and populated
at list-compile time (`compileStarts=20` fired before any replay), so the
first replay itself can trigger the build — or eagerly at world-pass entry /
backend install. **Round 36 viability confirmed**: `build(context)`
(`ModelGeometryCache:105`) takes `VulkanContext` only — `new
VulkanDynamicMesh(context)` + staging upload, no render pass — so calling it
from `drawModelList` pre-pass is safe. Concrete shape:

```java
// in celtium$drawModelList, before the modelMeshes()==null early-exit:
if (modelMeshes() == null && VulkanModelQuadCapture.listIds().length > 0) {
    celtium$buildModelMeshes();   // device-level; safe outside a pass
}
```

**Round-51 re-verification — the fix is still unlanded and still
correct**: `modelMeshes()==null` early-return persists at `:10072`;
`celtium$buildModelMeshes` (`:9921` → `modelGeometry.build(context)`)
remains reachable only from the terrain-pass head (`:9069`).
`build()` (`ModelGeometryCache:109`) already iterates
`VulkanModelQuadCapture.listIds()` internally — an empty list set
builds nothing harmful — so even the `length > 0` guard is optional;
the minimal safe insertion is `if (this.modelGeometry.modelMeshes()
== null) celtium$buildModelMeshes();` immediately before the
`:10072` early-return. `build(context)` needs only `VulkanContext`
(device-level — staging + `VulkanDynamicMesh` upload, no render
pass), so calling it from `drawModelList` pre-pass is sound. The
off-target path (`:10051-10071`) already logs a missing mesh by
name — the same lazy-build applies there identically, and the
`modelDrawCalls` entry counter is still the right addition (one
counter per silent exit).

**Round-53 re-scoping — defect 4 is a *startup transient*, not a
persistent loss**: `r212-splash-splash2` measures `modelDraws=314` with
`displayListReplays=75` and `CELTIUM_MODEL_STATE` printing (the try ran)
— entities DO draw once the first terrain pass completes
`buildModelMeshes`. The only window that loses entities is the frames
*before* the first `recordRenderPass` — so in a real world the player
arm/hand/mobs appear after the first world frame, which is a
one-to-few-frame loss rather than the permanent emptiness the earlier
read implied. The lazy-build fix is still correct (it removes that
initial window entirely), but the user's 「一人称でもプレイヤーから離れ
ている」 may be more than this transient: the phrase reads as a
*camera-position* offset (the view sitting somewhere other than the
player's eye), and the fixture's world shots look normally-placed —
worth a real-game arm/held-item check before attributing the feel to
defect 4 alone.

**Round-54 corroboration — the arm/hand models draw, and the matrix
plumbing is exact**: `r212-splash-splash4`'s census reads
`modelDraws=46` with `capturedOwners=RenderPlayer.func_177138_b[128,132]`
— the *first-person arm/hand* models are captured, built
(`modelMeshBuilt=2`), and drawn (the beige column in the leg's world
shot is the arm). `matrixMirrorDelta=proj:0.0,view:0.0,exact:107` —
every mirrored projection/model-view comparison matches bit-for-bit —
the transform plumbing is clean end-to-end. `textureCopiesServed=525`,
`readbacks=525`, `ReusedInFrame=0` — the blurfix marker delivers fresh
pixels to every pass. So the only entity loss left is the pre-first-
`recordRenderPass` window, and the 「away from player」 feel is unlikely
to be missing-entity at all — camera/FOV or the transient.

Also add a
`modelDrawCalls` entry counter so the guard that fires names itself — this
class of silent-return is exactly what the census's pass-open/closed split
exists to see, and one counter per early-exit ends the guessing.
Minor alongside: `refusedBeforeAppend=4` on `format=4 texture=12 (mirror never
fully initialized)` — 4 refused batches draw nothing.

Deciding instruments stay as proposed: `dumpSourceImage` (frame image vs
swapchain at world-present) + log one in-world composite batch's verts/UVs
(the `CELTIUM_COMPOSITE` dump extended past the splash phase or keyed to the
identity-0 group that carries `POSITION_TEX` quads of 1280×720).

GUI (ortho, m11<0) renders correctly *in the menu*; the world frames contain
no HUD/2D element at all (the fixture suppresses it), so the world captures
cannot distinguish "3D inverted, 2D correct" from "**everything** in-world
written Vulkan-order" — both look identical on this evidence. Two corollaries
worth keeping:

- `post_blit.vert` and siblings carry `texCoord.y = 1.0 - texCoord.y`
  (`post_blit.vert:20`, `post_rotscale.vert:26`, `post_sobel*.vert`) — correct
  only for a top-down source, i.e. the same convention family as the four
  sites above. `postChains=0` in this run so they are dormant, not the world
  inverter — but any future leg that runs the post chain against a bottom-up
  mirror will hit them.
- `offTargetByTarget=[0:123461(default-pair)]` is census labelling, not
  routing: `routeLabelForBinding` (`GllessFrameTarget:1122`) maps identity 0
  → frame-image and the rule is inside the bisect jar. The composite's draws
  live in the frame image like everything else.

Matrices mirror the game exactly
(`matrixMirrorDelta=0.0`, `derivedProj` m11=+1.428), all viewports are
positive-height, and both matrix paths (`vulkanProjection`, `remapClipDepth`)
remap z only. On paper every draw convention predicts upright, so the inverter
is a write-time mirror in a path the counters don't see. Bounded suspects:

1. The composite binds the main-FBO colour texture (name=3) whose mirror is a
   *published view* over whichever image the FBO drew into — no copy, no flip —
   so for the world to be inverted the *published image itself* would have to
   hold it top-down. The frame image is bottom-up, but the identity-0 draws
   route to the **default-pair** (`offTargetByTarget=[0:123461(default-pair)]`)
   and `preRecordedBlitRecords=2` shows two different images were presented —
   if the pair's contents or its present-read assume top-down, the composite
   output inverts while direct-frame GUI stays right.
2. A group viewport narrower than the target inside the world pass
   (`groupViewportSets=1878` narrow applications in-world — only the first 3
   ever print, all splash-era) — displacement alone cannot mirror content, so
   this alone does not explain it, but it feeds suspect 1 if the composite's
   own group ran narrow.
3. `preTransformedGroups` — ruled out: `celtium$preTransformable` requires an
   axis-aligned modelview, so only GUI-class draws qualify.

   Instrument gap feeding both: `groupViewportSets=1878` in-world means
   1878 *applied* viewport changes (past the `stateSkipsViewport` dedup), but
   `CELTIUM_GROUP_VIEWPORT` prints only the first 4 — all splash-era — so the
   in-world rects are invisible. A per-rect histogram (pack the applied rect
   into a `Long` key, `merge(1, Integer::sum)`, dump top entries in the
   summary) names which rects actually run in-world and whether any of them is
   the composite's — cheap, and it either kills suspect 2 or hands over the
   rect.

   Second instrument gap: the presented dump proves the *swapchain* holds the
   inversion, but nothing dumps the **source image** at the same present. A
   `dumpSourceImage` variant of `dumpPresentedImage` (readback of
   `frameTarget().colorImage()` instead of the swapchain image) splits defect 2
   in one run: source inverted → the write path; source upright → the
   present/readback path (and would also answer whether in-world presents
   come from a different source than menu ones — `preRecordedBlitRecords=2`
   says two different sources were presented across the run).

Ruled out: the present blit (would invert GUI too), the matrix mirror (exact,
`derivedProj` m11=+1.428), `FRONT_FACE` (`33e20313e` empirically proved
`CLOCKWISE` correct for the terrain mesh — undersides were the previous bug it
fixed), copy service for the world path (only dest=21 panorama texture served),
`blitRectangleFrom` for the composite texture (published view, not a copy),
the batched-submit/stub work (bisect jar predates it).

## Defect 3 — loading screen

Two independent facts, both already known/accepted in the plan: FML splash is
intentionally skipped (`CELTIUM_GLLESS splash progress skipped` lines in the
user's log too), and the render loop stalls several seconds at world-load
(captures 0068–0077 byte-identical — the last menu frame persists on screen
with no "Loading terrain" composite). The "依然" symptom = the latter: the
world-load stall presents nothing new.

**Plumbing check (round 31) — vanilla's loading draws are NOT suppressed by
FML.** Cleanroom's `FMLClientHandler.handleLoadingScreen` returns true only
when `currentScreen instanceof GuiNotification` (verified via `javap` on
cleanroom-0.6.13-alpha); with no notification up it returns false and
`LoadingScreenRenderer.setLoadingProgress` runs its full draw (dirt quad +
progress bar + text) into `mc.getFramebuffer()` then `framebufferRender` +
`Display.update`. Present-hook calls climbed 300→600→900 across the frozen
window (~20/s), so `Display.update` *was* driven during the load — yet the
presented pixels never changed (byte-identical). Two consistent explanations:
(a) the load path doesn't invoke `setLoadingProgress` often enough/at all in
this flow (the fixture's integrated-server load may skip progress paints), or
(b) the loading draws land but the composite overwrite chain (same
texture-3-empty-mirror path as defect 2's investigation) covers them.

**Round 33 evidence — the answer is (a)-shaped**: the leg's loading-phase
presented frame is *pure* (255,255,255) white — `framebufferClear` ran (the
frame holds a uniform clear) but not a single draw landed: no dirt quad, no
progress bar, no text. A composite-cover could only black or restamp it —
white means the draws were never written to this image at all. Either
`setLoadingProgress`'s draws are refused/dropped wholesale (counter check:
`refusedByFormat`/`lastPrepareFailure` during the load window) or the
progress callbacks themselves never fire in this flow — one grep into
`launchIntegratedServer`/`loadWorld`'s `IProgressUpdate` calls decides which
half. (The early-phase frame is blue+red — draws *did* land before the
window settled — so the pipeline itself works; the loss is specific to the
load window.)

**Round 49 narrowing**: the leg's `CELTIUM_ORTHO` is capped at 6 calls
(`orthoCalls < 6`, `:3764`) — all six are the GUI's `n=1000 f=3000`, so it
*cannot* say whether the loading screen's `ortho(0,w,h,0,100,300)` ever
fired. What the log does show: `setLoadingProgress` never appears, and
`REFUSALS_BY_FORMAT {}` is empty (no draw refused). The draws that *do*
enqueue into the main FBO (2547 groups into `target=0x39d9a6d7` —
the FBO census's `adoptedMain=gameField=0x39d9a6d7 mainNames=[3]`,
`route=chain-pair`, texture 3) are the menu's own GUI/framebufferRender
traffic. So the open question is binary: either the progress callbacks
(`AnvilSaveConverter`/`launchIntegratedServer` → `IProgressUpdate` →
`setLoadingProgress`) never fire in this fixture's load path, or they fire
on the integrated-server thread and their `GlStateManager` calls don't
reach the render-thread mirror (the stub's dispatch is thread-agnostic,
but `immediateState` is fed by `GlStateManagerMixin` — if a server-thread
call writes the same mirror it would still appear, so a missing ortho
leans toward "the callback never ran"). **Instrument that decides it**:
log `ortho` calls whose `near/far != 1000/3000` (or log every ortho with
its site — `gluPerspectiveSite()` already exists), plus a counter at
`LoadingScreenRenderer.setLoadingProgress`/the dirt-quad draw; one
`ortho(…,100,300)` line means the draws ran and were lost downstream,
zero means the callback never fires and the loss is upstream of GL.

**Round 50 — the ENQ projections answer it: zero.** Every enqueued
group's stamped projection (the `proj=[2],[6],[10],[14]` line) is one of
`0,0,-1.005,-0.05` (the perspective, 28800 draws), `0,0,-0.501,-0.5`
(2971 draws — an `ortho(l,r,b,t,-1,3)`, *not* the loading screen —
`framebufferRenderExt` itself uses `ortho(…,1000,3000)`, `Framebuffer.java:241`),
`0,0,-1.0,-0.05` (358), `0,0,-0.505,-0.5` (30) or `0,0,0.0,-0.5` (22).
The loading screen's `ortho(0,w,h,0,100,300)` would stamp `proj[10]=-0.01`
— **it appears nowhere in 32181 enqueues**. So the loading draws never
reached the enqueue channel: `setLoadingProgress` either never fired
(the fixture's world-load path doesn't drive `IProgressUpdate`, or the
`i - systemTime >= 100` throttle in `LoadingScreenRenderer:129` skipped
every call in a fast load) or its `GlStateManager` calls ran on the
integrated-server thread and never touched the mirror. The white frame
is then just "an FBO that was cleared (`framebufferClear` is the only
load-path call that *does* arrive — or the presented dump's own cleared
state) with no loading UI on top." The discriminating instrument stands:
a counter at `setLoadingProgress`/an `ortho(100,300)` site log — if it
stays zero across a slow load, the loss is the callback path
(`Minecraft.loadWorld`'s `loadingScreen.displayLoadingString` at
`Minecraft.java:2526` calls `setLoadingProgress(-1)` on the main thread —
if *that* doesn't produce an enqueue, the call isn't running).

**Round-56 — the composite path is NOT the gate** (ordering theory
withdrawn per task-76/`a774a5dac`): `bindFramebuffer` flushes on the
target change (`:4546`) and the window path waits for completion, so
`setLoadingProgress`'s FBO→composite ordering is sound — a *routing*
miss (frame image vs default pair) is the only downstream loss left.
Fixing defect 3 therefore rests on the upstream question alone: the
progress callback has to fire (no `ortho(100,300)` in the enqueues
says it doesn't in this fixture).

## Suggested experiment for main

**The fixes are coupled — do them together or the experiment reads as a
regression.** `y = yBottom0` alone moves the cube to its correct rows 0..255,
but the copy paths still read `sourceHeight − sy` (rows 464..719): the blur
texture then samples an empty band and the panorama goes blank the other way.
Apply all three conversions at once:

1. `VulkanTerrainGpuBackend.java:12426` → `int y = empty ? 0 : yBottom0;`
2. `VulkanGlTexture.java:2509` → the blit between two bottom-up images is a
   *straight* translation, not a flip: `srcOffsets (sx, sy) → (sx+width, sy+height)`
   (delete `sourceBottomRow`/`sourceTopRow` and the `sourceHeight −` conversion),
   `dstOffsets` unchanged. GL semantics put framebuffer row `sy+j` into texture
   row `yoffset+j` and memory row = GL row on both sides.
3. `TextureCopyService.java:746` → drop `reverseRows(...)` (or read the rect
   and don't reverse — the buffer already arrives in the mirror's order).
4. `TextureCopyService.java:689` → `imageOffset(x, y, 0)` — the GL read rect's
   rows are the image rows `y..y+h` on a bottom-up image; the
   `targetHeight - y - height` form reads the mirrored band.

Then rebuild, re-run `scripts/compositor_capture.py` with the same flags —
outcome splits cleanly:

- Panorama lands bottom-left **and** world rights → the world was the same
  convention family somewhere upstream of the composite.
- Panorama fixes, world still inverted → defect 2 is a separate write-time
  mirror; next look at the pair-image row-order on the identity-0 composite
  path (suspect 1).

## Evidence paths

- Compositor captures: `.celtium-work/acceptance/p-bugrepro-caps/compositor-*.png`
  (+ `captures.json`)
- Presented dumps + identity sidecars:
  `.celtium-work/runtime/p-bugrepro-bisect/screenshots/celtium-presented-*.png`
- Run log: `.celtium-work/runtime/p-bugrepro-bisect/logs/latest.log`
- Key code: `VulkanTerrainGpuBackend.java:12426` (bug), `:12481-12487` (the
  proven-correct sibling), `:12396-12398` (stale convention doc),
  `VulkanSwapchain.java:1202-1207` (the one flip that defines bottom-up).
