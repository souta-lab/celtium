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
