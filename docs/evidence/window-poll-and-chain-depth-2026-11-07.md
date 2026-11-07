# The window's redundant poll, and the depth a mod's framebuffer never asked for

2026-11-07, phase 4 round 15. Renderer `1ae204dd` (round 14's stage, `r14-bloom`) is the baseline; the round's own
build is `21279d71` (poll only) and `57d3705b` (poll + chain-pair render pass). Scene: the KPI's own world scene
(Supersymmetry 0.2.0.2, `world` mode, `guiScale=3`, validation off, warm ticks [400,600), 240 s), AMD RX 6400.

## What the round measured first

The round-14 handoff said the bloom chain costs ~545 us a frame and that the target was "the per-window overhead
(~36 us x 15)". Two legs were shot to replace that estimate with a split.

**`r15split-amd`** (round 14's jar, `-Dceltium.vulkan.windowSplit=true`): `windowRecords=183275` over `frameId=9355`
= **19.6 windows a frame**, `windowRecordMillis=4091` = **437 us a frame of recording**, `slotPolls=192906` =
**20.6 polls a frame**. The `CELTIUM_WINDOW_SPLIT` line names only 4 us of the ~22 us a window:
`route=0 offTarget=1 off[pair=0 sync=0 trans=0 clear=0 fb=0] pair=0 publish=0 begin=0 commands=1 acquire=2 release=0`.

**`r15psplit-amd`** (same jar, `windowSplit` + `passSplit`): `flush` p50 **0.4 ms** of a 1.4 ms frame, `winRecord`
p50 **0.3 ms**, `wrecLoop` p50 **0.2 ms**, and every other window bracket (`winHead`, `winPrefix`, `winPublish`,
`winBeginPass`, `winPassEnd`, `winBarrier`, `winSubmit`, `winEnd`, `windowSetup`, `wfpBegin`, `wfpMiddle`) at
**0.0 ms** - the millisecond form rounds to 0.1 ms, so those brackets cannot rank anything under 50 us. The group
loop is 0.2 ms for **139 groups a frame**, so the chain's own 15 groups are only ~21 us of it.

**`r15ab-on-amd` / `r15ab-off-amd`** (the same jar, `-Dceltium.vulkan.modPrograms=false` the only difference):
p50 **2.944 vs 2.359 ms**, so drawing the chain costs **585 us a frame** on this build, of which the recording is
224 us (`windowRecordMillis` 449 vs 225 us a frame). The other 361 us is not in the recording bracket.

## Change 1: a window whose submission stayed in the frame's recording does not poll

`FrameCoordinator.submitCommands()` answers `RECORDED` when the batched path kept the segment in the frame's own
command buffer: the queue was given nothing, so the window's `celtium$pollCompletedSlots()` re-reads fences the
previous poll already read. `SKIPPED` (a stalled device) recorded nothing either. Only `QUEUED` can have changed what
a poll would see. The rule is `celtium$windowPollNeeded` and it is exercised without a device by
`WindowPollDecisionTest` (4 cases, including "every answer is decided" so a new enum value cannot default into a
poll).

The confirmation is lazy by construction - `beginCommands` polls when `submissionPending`, which every accepted
submission sets - so skipping the window's poll defers bookkeeping rather than losing it.

**Measured**: `slotPolls` **20.6 -> 1.03 a frame** (`r15split-amd` 192906/9355 vs `r15ab-on-amd` 10212/9927), and
`frameFenceWaitMillis=68` / `frameFenceWaitCount=55` against round 14's 65 / 53 - no new stall. The chain-off arm
also drops to 1.02 a frame (12292/12051).

## Change 2: a mod's own framebuffer draws through a render pass that clears and discards depth

A mod's bloom chain binds framebuffers with **no depth attachment**, so its passes never read or write depth. Under
the frame pass every one of them loaded and stored the pair's full depth image (`loadOp=LOAD`, `storeOp=STORE`).
`VulkanTerrainPipeline.createChainPairRenderPass` builds the same attachments and subpass with the depth attachment
`CLEAR` / `DONT_CARE`. That is legal to use with the frame pass' pipelines because Vulkan's render-pass compatibility
rule compares attachment formats, samples and subpass references - **not** load and store operations. The framebuffer
cache is keyed by render pass, so the pass gets its own framebuffer handle.

The first leg of this change (`r15depth-val-amd`) reported **20 x `VUID-VkRenderPassBeginInfo-clearValueCount-00902`**:
a pass with a `CLEAR` attachment needs a clear value for it, and the window's begin-info passed none. The fix is in
`celtium$windowBeginInfo` (two clear values, depth 1.0, only for the chain-pair pass). The switch
`-Dceltium.vulkan.chainPairDiscardDepth=false` restores the frame pass for the A/B.

**Status: accepted for correctness, timing still open.** The first timing leg (`r15depth-on-amd`, p50 3.693 ms) was
shot on a machine whose load average was 3.95 - the validation leg before it had just finished - so it is not a
measurement of the change. The acceptance leg `r17wip` (AMD RX 6400, world scene, guiScale 3, validation on, 240 s,
renderer `42c0cf16…`) **PASSED**: `CELTIUM_RECORD chain-pair render pass created (depth cleared and discarded)`,
`CELTIUM_VALIDATION messenger=0x… layer=VK_LAYER_KHRONOS_validation`, **zero validation messages** (the
`VUID-VkRenderPassBeginInfo-clearValueCount-00902` of `r15depth-val-amd` is gone) and the presented picture passed the
fixture gate. The timing re-shoot on an idle machine is still the next step for the FPS claim.

## What this round did not close

- The FPS condition is still `NOT_MET`. The chain's 585 us a frame is the target and this round removed only the
  poll (a few tens of microseconds at most, bounded by `winEnd`'s p50 of 0.0 ms).
- The 361 us of the chain's cost that is not the recording is still unattributed. The brackets that could name it are
  all below the millisecond form's resolution; `-Dceltium.vulkan.passSplitMicros=true` prints the same split in
  microseconds and is the instrument the next round should use before spending a leg on a hypothesis.
- Nomi's picture is still pre-round-14 (its 21,885 format-6 refusals are 0 now, so the chain draws).
