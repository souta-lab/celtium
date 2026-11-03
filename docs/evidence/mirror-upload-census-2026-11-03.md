# the supply part of the accept path was 62,564 calls that uploaded nothing (2026-11-03, round 34)

Round 33 measured the accept path on the shipped profile, named `supply` as its largest part - 60%, 3,072 ns per
slow-path offer - and asked the next question: are those uploads content the game really rewrote, or the same content
re-uploaded because the mirror was rebuilt? This round answers it, and the answer is neither: **they were calls that
uploaded nothing at all.**

## What the census says

Leg `r34b-census` - `celtium-world-accept` (188 mods, `qmagnet`), AMD Radeon RX 6400 (RADV NAVI24), 1280x720,
`guiScale3.txt`, 240 s, `worldFrames=3951`, `sceneCalibrationKeyMatches=true`, fixture `PASSED`, validation off by
flag (a timing arm). Flags: `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.batchTimings=true -Dceltium.vulkan.frameStats=true`.

```
lazyMirror[supplies=0 rawWriteRefreshes=62564 skips=0 failures=0(...) deferred=2 empty=38600]
mirrorUploads[count=62564 bytes=0 millis=451 skips=0
  byGlId=[110337:16074/0 110345:7144/0 110342:5362/0 110344:3572/0 110355:3552/0 110341:1788/0 110339:1786/0 110277:1786/0]]
parts[expand=48 matrix=60 extent=62 registry=651 supply=2369 prepare=831]
```

`bytes=0` and `skips=0` together are the whole finding. `bytes` is only added by `commitFullUpload`, and `skips` only
by the identical-content branch, so a call that adds neither never reached either upload path: **every one of the
62,564 calls returned at `uploadFromGlTexture`'s own serial check** - "this image already holds the level's current
serial". 62,564 / 3,951 world frames is 15.8 a frame.

**The cost of those calls is 174 ms, not the 451 ms on the line.** The 97 calls that did reach an upload path account
for the other 277 ms (2.9 ms each - the 4096x4224 backlog drain, all of it before the first reported window), so the
no-op calls are 174 ms / 62,564 = **2.8 us each, 0.044 ms of a 2.59 ms world frame - 1.7% of the frame**. The first
draft of this document divided the whole 451 ms by 62,564 and read 7.2 us and 4.4%; that was wrong, and the A/B below
is what showed it.

The `supply` part agrees with the corrected arithmetic: 2,369 ns x 52.2 offers a frame = 0.124 ms, of which the 97
real uploads are 277 ms / 3,951 frames = 0.070 ms and the no-op calls are the remaining 0.054 ms.

## Why the call was made at all

`celtium$refreshRawWrites` keeps two per-name arrays (`rawWriteEpochChecked`, `rawWriteSerialApplied`) so a name whose
serial has not moved costs one array read. Those arrays are bounded by `RAW_WRITE_TRACKED_IDS = 1 << 16`, and a GL
name past that bound takes a branch with **no memo at all**:

```java
if (texture >= RAW_WRITE_TRACKED_IDS) {
    celtium$applyRawWrites(texture, mirror);   // every offer that binds this name
    return;
}
```

Every GL id in the census is past 65,536 - 110,277 to 110,355 - so every one of them took that branch. The comment on
the bound is right that an array that size is not worth allocating for a name that large; what it missed is that the
names which actually appear are few (eight in this leg) and that the question can be answered from the mirror itself.

## The fix

`VulkanGlTexture.celtium$holdsSerial(serial)` exposes the question the upload asks at its head, and
`celtium$applyRawWrites` asks it before making the call:

```java
if (mirror.celtium$holdsSerial(CeltiumGlStub.textureUploadSerial(texture, 0))) {
    this.rawWriteAlreadyCurrent++;
    return true;
}
```

`Long.MIN_VALUE` (no record at all) is never "current", so the "nothing to read" deferral below still sees those
names and still counts them. The announced path (`uploadFromImage`) stores -1 in the same field on purpose, so a
mirror filled by an announcement never answers true for a captured serial it did not take.

The counter is split for the same reason round 33 split the failures: `rawWriteRefreshes` used to count *calls*, and
the plan and the handoff both read it as "15.9 uploads a world frame". It now counts uploads, and
`lazyMirror[current=N]` counts the calls that were not made.

A second, smaller change rides with it: the stub no longer takes a new serial for a whole image
(`glTexImage2D` with data) whose converted bytes are identical to the record the level already holds - the
whole-image half of the rule the patch path has had since OPT-21. `identicalFull` in the capture census counts it.
**It did not fire on this scene** (`identicalFull=0` on both arms), so it is a defect class removed rather than a
measured win, and it is recorded that way.

## The A/B

Same profile, same flags, same 240 s, only the JAR differs.

| | `r34b-census` (renderer `5a9f1151`) | `r34c-current` (renderer `b356ab80`) |
|---|---|---|
| `rawWriteRefreshes` | 62,564 (15.8/frame) | **97** (0.03/frame) |
| `lazyMirror[current=]` | - | **50,746** |
| `mirrorUploads` | `count=62564 bytes=0 millis=451 skips=0` | `count=97 bytes=0 millis=277 skips=0` |
| `parts[supply]` | 2,369 ns | **1,792 ns** |
| offers / world frame | 52.2 | 47.7 |
| `worldFrames` | 3,951 | 3,543 |
| `frameIntervalP50Ms` | 2.668 | 2.589 |
| `frameIntervalP95Ms` | 3.747 | 3.916 |
| `frameIntervalP99Ms` | 4.362 | 6.154 |
| `presentedDistinctColours` | 76,585 | 76,562 |
| `presentedNonBackgroundFraction` | 0.989756 | 0.989708 |
| `presentedVerticalBands` | 720 | 720 |
| `presentedBlackFraction` | 0.007038 | 0.00708 |
| `sceneCalibrationKeyMatches` | true | true |
| fixture verdict | PASSED, `problems=[]` | PASSED, `problems=[]` |

The picture numbers move by less than the scene's own animation does (distinct colours 76,585 vs 76,562, non-background
0.989756 vs 0.989708), so the removed calls changed no pixels - which is what "they uploaded nothing" predicts.

**The frame interval is not a KPI claim.** p50 -3.0% is inside the +/-7% run-to-run band the docs record, and p99
+41% is one pair on two runs whose offer counts differ by 9% and whose world animation is not frame-identical. What
the pair establishes is the call count: 62,564 -> 97 with the same scene and flags.

## What is left

* The 97 remaining calls are 277 ms of the leg and all of them land before the first reported window. They are the
  extent path (`uploadPatchedLevels`) draining a 4096x4224 backlog record - the one `CELTIUM_MIRROR_UPLOAD` line this
  round's census printed. A load-time cost, not a steady-state one, but 2.9 ms a call is worth a look if the load
  hitch is ever revisited.
* `parts[supply]` is still 1,792 ns per offer with no upload behind it. That is the cost of the *check*:
  `textureWriteEpoch`, the ownership guards, `textureUploadSerial` (two map lookups) and `celtium$holdsSerial`. It is
  now the whole of the supply part and it is 0.085 ms a frame; the next step there is to memoise the untracked names
  in a map keyed by GL id so the serial lookup is not repeated per offer, which is the same shape as the tracked
  arrays.
* `atlasGlId` is still -1 on this profile (`CELTIUM_ANIM ... atlasGlId=-1`), so the block atlas is never excluded
  from `refreshRawWrites` by name. It does not appear in the census because no immediate batch binds it, but the
  exclusion the code relies on is not in force.
