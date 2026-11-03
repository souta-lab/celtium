# the memo's misses are the names' announcements, not the table's churn (2026-11-03, round 36)

Round 35 left the registry's lookup memo at 256 slots with an 11.5% miss rate and no control arm for the size. This
round asked a different question about the same counter - **is the miss rate the table's own churn?** - because every
mutation of `entries` cleared all 256 slots, so one texture created or forgotten mid-frame threw away every other
name's entry.

The answer is no, and the measurement that says so is the change's own control arm.

## The change

`VulkanTextureRegistry`:

* `celtium$dropMemoFor(int glTextureId)` clears the one slot the mutated name is direct-mapped to, and only when that
  slot still holds that name. Every single-name mutation site uses it: `publishExternalImage`'s supersede,
  `publishExternalImage`'s own put, `retireExternal` (which now takes the name), `imageFor`, `createTexture2d`,
  `fillFromCapture`, `fromImage`, `announcePixels`, `forgetTexture`. `delete()` keeps the whole-table clear, because it
  removes every entry at once.
* `-Dceltium.vulkan.memoPerName=false` restores the whole-table clear, so the two behaviours are one flag apart on one
  JAR.
* `externalSummary` now prints `memo[hits=… misses=… slots=… drops=… forgets=… perName=…]`, so a leg says how many
  whole-table clears happened and how many per-name drops replaced them.
* `TextureMemoInvalidationTest` (4 tests) asserts the property the change rests on without a device: dropping one name
  leaves every other entry in place, a colliding name is not evicted by a drop of the name that is not in the slot, and
  dropping an absent name is a no-op.

## The paired set

| | |
|---|---|
| Legs | `r36a-memoctl` (`-Dceltium.vulkan.memoPerName=false`) and `r36b-memopn` (default) |
| Renderer / fixture | `2e3ccc4cd5e0c621626f9d69bec458d66806b72b0a5eaa132add051b002c42fc` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` (same JAR in both arms) |
| Profile | `world` (`celtium-world-accept`, 188 mods, `qmagnet`), AMD Radeon RX 6400 (RADV NAVI24), `gpuRequirementMet=true` |
| Scene | `sceneCalibrationKeyMatches=true` in both, 1280x720, `guiScale3.txt`, 240 s |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.batchTimings=true -Dceltium.vulkan.frameStats=true -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.frameBudget=true` (identical in both arms) |
| Verdict | both PASSED, `presentedImageVerdict=PASSED`, `presentedImageAssertionFailures=0` |

## The answer: the drops were never the cause

| | r36a-memoctl | r36b-memopn |
|---|---|---|
| `memo[drops=…]` (whole-table clears) | **552** | **0** |
| `memo[forgets=…]` (per-name drops) | 0 | 551 |
| hits / misses | 1,613,525 / 209,225 | 1,658,639 / 218,539 |
| worldFrames | 3,927 | 4,038 |
| **misses per frame** | **53.3** | **54.1** |
| **hits per frame** | **410.9** | **410.7** |

552 whole-table clears over 3,927 frames is **0.14 a frame**, not the ~1.3 the round's own estimate assumed. Removing
all of them moved the miss rate by +0.8 a frame, i.e. not at all: the misses are the names' **announcement generations
moving** (an animated texture re-announced every frame must be re-resolved, and the memo's generation check is what
makes that safe). That is inherent to the memo's contract, not a defect in its invalidation.

So the hypothesis is refuted, and the slot count is still not the question either: with 0.14 clears a frame the table
is not being emptied, and the misses are per-name by construction.

## What did move

| | r36a-memoctl | r36b-memopn |
|---|---|---|
| `parts[registry]` ns per accept-path lookup | 1,080 | **931** (−13.8%) |
| `offerStages[s8]` ns per offer | 1,083 | 1,001 |
| `batchTimings[total]` ns per offer | 4,339 | 4,265 |
| `frameIntervalP50Ms` | 2.7226 | 2.5612 |
| `frameIntervalP95Ms` | 3.9301 | 3.5879 |
| `frameIntervalP99Ms` | 4.5654 | 4.4133 |
| `presentedDistinctColours` | 77,009 | 76,874 |
| `presentedNonBackgroundFraction` | 0.990165 | 0.990196 |
| `presentedVerticalBands` | 720 | 720 |
| `presentedBlackFraction` | 0.0065 | 0.006162 |

**No speed claim.** The p50 moved −5.9%, which is inside the project's ±7% band, the two runs are not frame-identical
(3,927 against 4,038 world frames), and the arms ran in a fixed order rather than alternated. The mechanism accounts
for ~1%: 220 accept-path lookups a frame at 149 ns less is 33 µs of a 3.1 ms frame. The picture did not move: distinct
colours 0.18% apart, non-background fraction within 3e-5, 720 bands on both.

The change is kept because the memo's validity is per name by construction - an entry is trusted only while that
name's own announcement generation is unchanged and the name is not stale - so a whole-table clear was
over-invalidation, and the per-name rule is the one that matches the contract. It is not kept as a speed-up.

## The attribution this round also took (new, and it corrects a reading)

`CELTIUM_FRAME_PASS_SPLIT` on the same leg (`r36b-memopn`, p50 ms/frame):

| frame | world | gui | hand | flush | terrain | winRecord | wrecLoop | grpDraw | winHead |
|---|---|---|---|---|---|---|---|---|---|
| 1.5 | 1.1 | 0.4 | 0.1 | 0.5 | **0.1** | 0.4 | 0.2 | 0.1 | 0.1 |

`CELTIUM_FRAME_BUDGET`: `frame=3.1 record=0.5 batch=0.6 present=0.1 other=1.9`.

Two corrections to what the project was reading:

1. **The terrain path is 0.1 ms, not ~0.9.** `terrainRecord` and `terrainSubmit` both read 0.0 at this scene's
   resolution; the only terrain bracket that shows is `terrainPreflush` at 0.1. The earlier "terrain pass' bracket
   3.5 ms against a 0.9 ms record" was a different (dense) scene and must not be carried here.
2. **`other` is not "the game's work" by definition.** `FrameBudgetTimings.record` brackets only
   `recordImmediateOnlyPass`, so `other` = frame − (record + batch + submit + fence + slot + present) holds everything
   the renderer does outside a window pass *and* everything the game does. The pass split is what separates them: the
   game's own frame bracket is 1.5 ms of the 3.1 ms frame, so ~1.6 ms is outside the renderer's brackets entirely and
   the renderer's own chain inside them is `record` 0.5 + `batch` 0.6 + `present` 0.1 = 1.2 ms.

The window's own floor is therefore close: `wrecLoop` 0.2 ms over 112 groups is 1.8 µs a group, of which `grpDraw`
(the driver's bind+draw) is 0.9 µs. Cutting the chain further means offering fewer groups or recording them off the
client thread, not making the loop cheaper.

## What this does not claim

* No speed-up, for the reasons above.
* No statement about Intel: this round ran AMD only.
* The absolute ms carry `batchTimings` and `passSplit` tax in both arms; only the two arms' difference is used, and
  even that only where the mechanism predicts it.
* `validation=false` in both arms: this is a measurement pair, not an acceptance leg. The image gate ran and passed in
  both.
