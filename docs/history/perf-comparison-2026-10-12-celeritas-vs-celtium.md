# Completion condition (g): a matched performance comparison against Celeritas — 2026-10-12

Two arms, one scene, one camera, one fixture, matched flags. This is the record the 0.0.3-alpha release draft should
cite instead of the RX-6400-only ledger numbers, and it says which numbers are comparable and which are not.

## The two arms

| | reference arm | candidate arm |
|---|---|---|
| jar | upstream Celeritas `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`, sha256 `d12425eaaf184e7925cd…` (the same jar the `intel-ref` legs used) | this build, jar sha256 `e383bc8bf08850106176…`, HEAD `d5fb4a421` |
| path | OpenGL (the game's own GL path) | this renderer, `-Dceltium.vulkan.glless=true` |
| fixture | `celtium-validation-client` sha256 `12601da778fc93391f0b…`, driving both runs | same |

The candidate's own GL bridge (`-Dceltium.vulkan.glless=false`) is **not** an arm: that path deadlocks on the first
world frame on this machine (three baseline legs), so the reference has to be the upstream jar. That is a stated gap,
not a substitution.

## What was matched

same GPU (AMD Radeon RX 6400, X11, node `pci-0000:03:00.0-render`) · same scene (`terrain`, `sceneVariant="default
flat stack, guiScale=2"` in both reports) · same camera (`x=0.5000 y=12.0000 z=0.5000 yaw=180.000 pitch=20.000`, from
`cameraAtVerdict` in both) · same window size (854x480 requested) · same duration (`--duration 150`) · same fixture
flags (`-Dceltium.validation=true`, `minWorldFrames=5`) · **validation layers off on both arms** · no presented-image
readback on either arm · same `--library` · the fixture drove both runs.

## The numbers

| measurement | reference (upstream Celeritas, GL) | candidate (this build, GL-less) |
|---|---|---|
| fixture frame interval, cold window, samples | **267** | **93** |
| p50 / p95 / p99 | **9.07 / 45.10 / 69.70 ms** | **40.69 / 85.22 / 130.09 ms** |
| warm window samples | 0 | 0 |
| `worldFrames` | 748 | 218 |
| `worldTicksAtEnd` | 202 | 241 |
| renderer-side frame bracket (`framePassSplit`) | not instrumented (upstream) | **frame p50 19.2 / p95 45.9; world p50 16.3 / p95 33.3; gui p50 0.7 / p95 14.2** |
| stalls | n/a | `gpuStalled=false`, `submitTimeouts=0`, `bootstrapSubmitWaits=29` |
| submits / deferred waits | n/a | `queueSubmitCalls=3473`, `deferredSubmits[waits=239 waitMillis=445 worst=7]` |
| validation | `validationMessageCount=0`, `validationClean=true` | `validationMessageCount=0`, `validationClean=true` |
| fixture verdict | `UNCALIBRATED_PRESENTED_IMAGE` | `FAILED_PRESENTED_IMAGE` |

## What is not matched, and what that forbids

- **The fixture's picture verdicts differ** (uncalibrated against failed), so this is a *performance* comparison only
  and neither arm's picture claim is supported by it.
- **`worldFrames` differs by 3.4x** (748 against 218) in the same wall-clock duration: the arms did not do the same
  amount of work in the sampled window, and the interval sample counts differ with it (267 against 93). The windows
  are the same window (worldTicks 120..200 cold, 400..600 warm); the *rate* inside them is the thing being measured,
  and the candidate never reached the warm window at all (0 samples on both arms, for different reasons: the
  reference's run ended at tick 202, the candidate's at 241).
- **A renderer-side bracket exists on one arm only.** `framePassSplit` is this project's instrument; upstream Celeritas
  has none, so the only number both arms produce is the fixture's frame interval. The candidate's own renderer p50
  (19.2 ms) is quoted for attribution, never against the reference.
- **The 60 Hz present interval is not a multiplier.** The candidate's interval p50 is 40.69 ms while its own renderer
  bracket is 19.2 ms: roughly half of that interval is outside the bracketed passes (the present/window path and the
  fixture's own work between frames). No "N times faster" claim is made from either arm's present interval.
- The reference arm has no `nativeDeviceName` (it never creates a Vulkan device); the GPU is the same card, named on
  the candidate side by `CELTIUM_NATIVE_DEVICE`. The two arms' device identities are therefore *inferred* to be the
  same physical GPU from the render node and `glVendor=AMD`, not asserted by one shared field.

## What the numbers do say

On this flat validation scene, at 854x480, guiScale 2, with the same camera and the same fixture driving, the
reference's frame interval p50 is **9.07 ms** and the candidate's is **40.69 ms** — the candidate is ~4.5x slower per
frame at p50, and its p95/p99 are 85/130 ms against 45/70 ms. This is the honest shape of the "3x faster" question
until the candidate's warm window and a matched renderer-side instrument on both arms exist.

---

# Addendum 2026-10-12: where the outside-the-bracket time goes (per frame)

Same arm, same flags, jar `908c571ef3130815…` (this build plus a timer on the present path), AMD RX 6400, GL-less,
MAILBOX (the fixture's vsync-off reaches the swapchain), F3 closed, fixture driving, 293 frames,
`frameIntervalP50Ms=38.713554` over 97 cold samples, renderer bracket `frame[p50=19.6 p95=42.9 worst=237.7]`.

| bucket | per frame | source |
|---|---|---|
| renderer bracket (`framePassSplit` frame p50) | **19.6 ms** | `framePassSplit` |
| present path: acquire + submit + `vkQueuePresentKHR` | **0.21 ms** | `CELTIUM_PRESENT_TIMING presentCalls=300 presentMillis=63 presentWorstMillis=2` |
| deferred submission waits | **1.50 ms** | `deferredSubmits[waits=229 waitMillis=447 worst=7]` / 293 |
| bootstrap submit waits | **0.02 ms** | `bootstrapSubmitWaitMillis=7` / 293 |
| frame fence wait, slot waits | **0** | `frameFenceWaitMillis=0`, `slotWaitConfirms=0` |
| swapchain / acquire out-of-date | **0** | `swapchainOutOfDate=0`, `acquireOutOfDate=0` |
| everything this project instruments, summed | **≈21.3 ms** | |
| fixture frame interval p50 | **38.7 ms** | the number the comparison uses |
| **unattributed** | **≈17.4 ms** | arithmetic; no instrument of ours covers it |

Inside the bracket (already counted in the 19.6 ms, listed so they are not double-counted): `batchCallMillis` 5.04 ms,
`actualFlushMillis` 1.79 ms, `batchCallOpeningMillis` 0.71 ms, `slotPollConfirms` 7.55 fence polls per frame with no
waits, `uploadRingPressureWaits` 0.66 per frame.

## What the unattributed 17.4 ms is, and what it is not

It is **the client loop outside every pass we bracket**: at this frame time the game ran **1.11 world ticks per frame**
(241 ticks / 217 frames), so the integrated-server and client tick work sits inside the interval and outside the
renderer's brackets, and no counter of ours times it. That is a named gap, not an explanation: measuring it needs a
timer on the game's own tick/loop (a mixin, not a file this unit owns).

It is not the presentation engine. The FIFO/acquire hypothesis for this gap is **measured false** on this arm: with
MAILBOX and an uncapped loop, the entire present path costs 0.21 ms per frame (worst 2 ms), `swapchainOutOfDate` and
`acquireOutOfDate` are 0, and `frameFenceWaitMillis`/`slotWaitConfirms` are 0. Recorded anyway, for a future arm that
does run FIFO: `VulkanSwapchain` acquires with a `Long.MAX_VALUE` timeout and the swapchain asks for
`max(minImageCount, 3)` images, which is the classic blocking-acquire shape when the present queue is full. It is
**not** the cause here, and the fix (requesting `minImageCount + 1`, or bounding the acquire) was not taken because the
measured cost of this path is already 0.21 ms.

## The cheapest safe item, and why nothing was changed

Of the buckets above, the ones this unit owns are the ring pressure (0.66 waits per frame), the deferred waits
(1.50 ms) and the present path (0.21 ms). The deferred waits are ordering-critical - present semaphores and texture
staging reuse depend on them - so removing or shortening them is exactly the synchronisation change the goal says must
not be made for speed alone. The largest measured item inside the bracket, `batchCallMillis` at 5.04 ms per frame, is
in `VulkanTerrainGpuBackend`, which another unit holds. **No change was therefore made in this addendum**, on purpose:
the honest result is the split, and the next lever it points at (the 17.4 ms of unbracketed loop time) needs an
instrument this unit cannot add.

## The comparison's honest numbers, restated

The arms' only shared instrument is the fixture's frame interval, and that interval is loop-bound on the candidate
(38.7 ms of which 19.6 ms is renderer bracketed and ~17.4 ms is loop/tick work with no instrument). The comparable
renderer-side statement is therefore: **candidate renderer bracket p50 19.6 ms against the reference's whole-loop
interval p50 9.07 ms** - the reference's own loop is faster in absolute terms as well, and the two are still not the
same quantity, which is why no multiplier appears here.

---

# Addendum 2026-10-12 (2): taking the renderer bracket apart

Same leg shape as above (fixture-driven, GL-less, MAILBOX, F3 closed, no readback, 854x480), AMD RX 6400 unless
stated. Two legs of the same shape agreed within noise, so the bracket numbers below are quoted once:
`frame[p50=19.2..19.6] world[p50=15.4..16.3] hand[p50=0.3..0.4] clouds[p50=0.8] gui[p50=0.7] residual[p50=0.0]`.

Inside `world` (sub-brackets, not additive with each other): **terrain p50 5.5**, **chunkUpload 3.8**, **flush 2.1**,
particles 1.6, present 0.0, entities 0.0 - about 13.0 ms named, leaving ~3 ms of `world` in no sub-bracket (the game's
own `renderWorldPass` work). The lead's `GameLoopTiming` closes the interval from the other side
(bracket 18.7 + tick 17 ≈ interval 34.7 on that jar), so the bracket itself is what remains to move.

## The upload path, phase by phase (new instrument, jar `3c9aabdad2670c90…`)

The largest thing inside the bracket that this unit owns is the staging path, so its phases are now timed and reported
inside the existing summary (`uploadPhaseMicros`, `uploadPhaseMillisTotal`, `uploadCopyBytesTotal`). 318 frames,
`uploadCopiesBatched=11116` (35 copies per frame), `uploadCopyBytesTotal=2116239008` (2.1 GB, **6.65 MB per frame**):

| phase | total | per frame |
|---|---|---|
| **reserve slot** (search + ring-pressure wait) | **605,028 us** | **1.90 ms** |
| copy into the ring (`memcpy`) | 311,460 us | 0.98 ms |
| submit the batch | 52,007 us | 0.16 ms |
| record the command buffer | 16,040 us | 0.05 ms |
| poll the deferred queue | 11,897 us | 0.04 ms |
| total | 996 ms | **3.13 ms** |

With 8 MiB of ring and 6.65 MB staged per frame, the ring wrapped every 1.2 frames and the reservation path waited on
the oldest deferred submission **211 times** (0.66 per frame, ~1.9 ms per wait): nearly the whole 1.90 ms was those
waits.

## The fix, and both GPUs

`UPLOAD_SLOT_COUNT` 8 -> 16 (8 -> 16 MiB of host-visible staging, reported as `uploadRingBytes`/`uploadRingSlots`). The
reuse rule is untouched: a slot is free only when the fence of the submission that read it has signalled. Same leg
shape on both GPUs:

| | before (8 slots, AMD) | after (16 slots, AMD) | after (16 slots, Intel UHD 630) |
|---|---|---|---|
| copies / copy bytes | 11,116 / 2.12 GB | 9,614 / 1.82 GB | 10,302 / 1.96 GB |
| **ring-pressure waits** | **211** | **0** | **0** |
| **reserve, per frame** | **1.90 ms** | **0.03 ms** | **0.007 ms** |
| copy, per frame | 0.98 ms | 0.91 ms | 1.32 ms |
| phase total, per frame | 3.13 ms | 1.23 ms | 1.61 ms |
| validation | msgs 0 / clean | msgs 0 / clean | msgs 0 / clean |

Attributable claim: the ring-pressure wait is **gone on both GPUs** (211 -> 0) and the reservation phase falls from
1.90 ms to 0.03 (AMD) / 0.007 (Intel) per frame, for +8 MiB of staging. **Not claimed**: the frame-level difference.
The legs did not do the same amount of work (copies 11,116 against 9,614/10,302) and the intervals moved the other way
(34.4 against 37.5/39.0 ms), which is the run-to-run variance this scene has shown all along.

## What is left inside the bracket for this unit

The copy phase (0.91-1.32 ms per frame for 6-6.8 MB) is real data movement and only shrinks if the game stages less.
Everything else this unit owns inside the bracket is now below 0.2 ms per frame; the largest remaining named items -
terrain 5.5 and chunkUpload 3.8 - are in the backend, which another unit holds.

---

# Addendum 2026-10-12 (3): is the staging time inside `chunkUpload`? (read, not measured)

Question from the lead: does the `chunkUpload` bracket enclose the upload/staging calls that `uploadPhaseMicros` times,
or are the two additive? Answered by reading the call chain, no new counter:

- bracket opens at `VulkanTerrainGpuBackend.java:8618` (`FramePassTimings.begin("chunkUpload")`), closes at `:8622`,
  wrapping `celtium$uploadMeshesPass` at `:8625`;
- inside it, `celtium$uploadMeshesPass` calls `uploadRegion(...)` at `:8645`; `uploadRegion` is defined at `:9096` and
  stages the mesh at `:9235` (`resources.vertexArena.write(...)`);
- `VulkanArena.write` (`VulkanArena.java:262` and `:328`) calls `VulkanBuffer.write` (`VulkanBuffer.java:87`), which
  calls `VulkanContext.uploadToBuffer(...)` - the method whose `reserve`/`copy` phases the counters time.

So the chunk-mesh part of the staging time is **nested inside the 3.8 ms `chunkUpload` bracket, not additive with it**.
What is *not* nested is the rest of the staging traffic: the same counters also cover texture staging
(`VulkanGlTexture`) and uniform/dynamic-mesh writes, which happen outside `chunkUpload`. The counters therefore bound
that bracket from the inside without being subtractable from it:

- `chunkUpload` 3.8 ms contains the mesh part of staging (a subset of the 1.23 ms/frame phase total) plus meshing and
  bookkeeping inside `uploadMeshes` that no counter of ours names;
- the remainder of the 1.23 ms (texture and uniform staging) is outside `chunkUpload` and additive with `world`.

Conclusion for the next unit: a new counter inside `chunkUpload` is not needed to know whether the two numbers
overlap - they partially do - but the *mesh-versus-texture split of staging*, and the meshing/bookkeeping part of
`uploadMeshes`, need one counter each on the backend side, which that file's owner would add.

# Addendum 2026-10-12 (4): a measurement-condition note for the next (g) leg (not re-taken)

The user's own unbounded run reports `frameInterval p50` of about **7.6-8.0 ms** (PR #16's clean/scratch legs), which is
at the reference arm's 9.07 ms, while this record's fixture-driven candidate arm reports **35-40 ms**. The arms are
therefore not measuring the same quantity even when the renderer is the same: the fixture drives the camera, pins
sections and the world time, forces the GUI scale and runs its own per-frame bookkeeping, and the candidate's arm
carries the client tick at ~1.1 ticks per frame (see the tick addendum).

The next (g) leg must separate "renderer" from "fixture-driven work": either a leg without the fixture's pinning, or
both arms reported with the fixture's own cost named. Until then the only honest cross-arm statement stays the one in
this record's first section, and no multiplier is claimed.
