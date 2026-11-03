# the supply decision read the same two capture maps nine times (2026-11-03, round 38)

The accept path is the largest Celtium-owned bracket after the window recording, and its supply part was measured at
1,792-3,072 ns per slow-path offer with **no upload behind it** - the census that settled that is
[mirror-upload-census-2026-11-03.md](mirror-upload-census-2026-11-03.md), which took `rawWriteRefreshes` from 62,564
to 97 and left the supply bracket at 1,792 ns. That number is the cost of the *check*, and the check asked the
stub's two capture maps through three predicates.

## What the check was doing

`celtium$supplyMirror` asked, in order:

| call | map lookups |
|---|---|
| `textureUploadSerial(name, 0)` | `texCaptures.get`, `texPatchBacklog.get` |
| `textureSize(name)` (for a log line that prints 6 times a run) | `textureLevelParameter` x2, plus a fresh `int[2]` |
| `celtium$mirrorWaitsForFramebuffer(name)` | `framebufferForColorTexture`, `texCaptures.get`, `texCaptures.get` |
| `celtium$levelHasNothingToRead(name, 0)` | `texCaptures.containsKey`, `texPatchBacklog.get` |

Nine lookups on two maps, for two records. The three facts the two rules read - is the level a framebuffer's colour
attachment, does it have a patch backlog, does it hold game pixels - are the same two records, read four times.

## The change

`CeltiumGlStub.celtium$supplyDecision(name, level)` reads the two records once and applies the two rules **in the
order the caller applied them** (framebuffer deferral first, then "nothing to read"), returning one of
`SUPPLY_NOW` / `SUPPLY_WAIT_FRAMEBUFFER` / `SUPPLY_NOTHING_TO_READ`. The order is not incidental: an attachment with
no capture satisfies both rules, and the two deferrals are counted apart because their reasons differ.

`FramebufferColorAttachmentTest.theOnePassDecisionMatchesTheTwoPredicatesItReplaced` pins the answer against both
older predicates on the real stub in all four states - nothing written, a zero surface, a real upload, and an
attachment with and without captured content - including the case where both rules are true.

The extent is read only when the deferral line will print (`VulkanTextureRegistry.wantsMirrorDeferredLine`). The
deferral creates nothing, so the size is a column of a log line, not part of the decision.

Three registry lookups leave the path with it:

* the two deferral branches returned `textureRegistry.get(name) != null` after creating nothing, so the answer was
  the one the caller already held;
* the successful upload returned a lookup of the name it had just created;
* `celtium$publishFramebufferColorTexture` returned "the registry holds an entry", where every caller that reads the
  answer asks the registry itself immediately after, and the two that ignore it are the window head and the group
  open. A registry get is 931 ns on the accept census (memo miss included) and the window head is 2.8 us.

The prepare path also published twice per offer: the second publish ran after
`celtium$adoptRawFramebufferColorTexture` whether or not the adoption recorded anything, so a name that is neither a
reported framebuffer nor a raw one paid two full resolutions (identity, attachment record, level parameters) for an
answer that could not have changed.

## The legs

Same profile, same flags, same 240 s, only the JAR differs from the previous round's.

| | `r38a-supply` | `r38b-parts` |
|---|---|---|
| renderer | `f95dad7c93c06d26de6e9679fc21de5437fe37ba5c7caf1f841d0b702b7723c1` | same |
| fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` | same |
| GPU | AMD Radeon RX 6400 (RADV NAVI24) | same |
| verdict / fixture / image gate | PASSED / PASSED / PASSED | PASSED / PASSED / PASSED |
| `sceneCalibrationKeyMatches` | true | true |
| `frameIntervalP50Ms` (cold) | 2.452 | 2.174 |
| `frameIntervalWarmP50Ms` (ticks 400-600) | 3.405 (2715 samples) | 3.309 (2772 samples) |
| `presentedDistinctColours` / nonBg / bands / black | 76,540 / .989756 / 720 / .007128 | 76,410 / .989693 / 720 / .007205 |
| `worldFrames` | 4,296 | 4,185 |
| flags | validation off, batchTimings, frameBudget, warm window | validation off, batchTimings, frameStats, warm window |

The picture numbers sit inside the scene's own animation band against the previous round's leg
(76,862 / .990131 / 720 / .007391), so the change moved no pixels.

`r38b`'s frame-stats line (last window):

```
offerStages[nanosPerReached s8=755 s9=83 s10=1043 s11=861 s12=41 s13=515]
accept[decideMs=28 preflightMs=739 tailMs=0]
parts[expand=55 matrix=64 extent=62 registry=1053 supply=2560 prepare=1330]
append[ms=296] prepare[nanos=552855394 calls=139480]
lazyMirror[supplies=0 rawWriteRefreshes=97 current=40977 skips=0 failures=0 deferred=2 empty=26542]
```

`r38a`'s frame budget (steady state, 60-frame window):

```
CELTIUM_FRAME_BUDGET frames=960 window=60 ms[frame=2.9/3.2/4.5 record=0.5/0.5/0.5 batch=0.5/0.5/0.5
  present=0.2/0.3/0.4 gpu=0.1/0.1/0.2 other=1.7/2.0/3.2] gpuShare=0.04
```

## What is claimed, and what is not

**Claimed:** the decision is one pass over the two records instead of four, the extent is not read for a line that
will not print, and three registry lookups and one duplicate publish are gone from the path. Each is a mechanism
with a test or a call-site audit behind it.

**Not claimed: a measured speed-up.** `parts[supply]` on `r38b` is 2,560 ns against 1,792 ns on the `r34c` leg, and
`registry` (1,053 vs 931) and `prepare` (1,330 vs 903) moved the same way - the whole leg is slower per offer, which
is what an external core busy on this host (`ly`, ~0.9 cores) predicts. The supply bracket also runs ~27,000 times a
run (26,542 empty deferrals + 97 uploads + 2 framebuffer deferrals), so at ~1 us saved per call the effect is
~6 us a frame, below this scene's leg-to-leg noise. The change is recorded as a defect class removed, not as a win.

**The accept path has nevertheless come down since the paired KPI.** That leg's frame budget read
`batch=0.8/0.9/1.1`; `r38a` reads `batch=0.5/0.5/0.5` with `record` unchanged at 0.5 and `present` at 0.2. The rounds
between (the empty-upload deferral, `celtium$holdsSerial`, the per-name registry memo, the raw-write sample gate)
are the candidates, and this leg cannot separate them - it is one arm, not a pair.

## What is left

* The merge decision is now the largest named per-offer item: `s10` = 1,043 ns per reached offer, against the
  preflight's 861 and the state capture's 755. It is ~40 field compares, two 16-float array compares,
  `celtium$readTextureAffine` and `fog.movedFrom`; 1 us for that is more than the compares cost, so the next step is
  to bracket its four halves rather than guess which one it is.
* `record` is 0.5 ms a frame for 42 windows (12 us a window) and the window census already names the sources: 13 a
  frame from the pack's own post chain (`Shaders.renderFullImageInFBO`) and 9.2 from a pending raw write at a batch
  offer. Neither is a Celtium defect; both are the price of one render pass per GL framebuffer bind.
* The paired KPI still has not been re-taken on an idle machine, which is the only judgement of condition C.
