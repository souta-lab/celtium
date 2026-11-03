# the accept path on the real-world profile, and 28,272 uploads that could not read (2026-11-03, round 33)

Completion condition C is 1.66-1.76x behind on `celtium-world-accept` (188 mods, `qmagnet`), and the plan's next
performance unit was named from a **different scene**: `parts[registry=2593 supply=4980 prepare=2015]` came from a
Supersymmetry plain leg (`flicker-still-scene-2026-11-03.md`). This round measures the same split on the profile the
condition is about, and the same leg's counters carried a Celtium error count that no earlier round had read.

## The split on the profile that matters

Leg `r33a-accept` - `world` profile (`celtium-world-accept`, 188 mods, `qmagnet`), AMD Radeon RX 6400 (RADV NAVI24),
1280x720, `guiScale3.txt`, 240 s, `sceneCalibrationKeyMatches=true`, `sceneGateCalibrated=true`,
`presentedImageVerdict=PASSED`, `worldFrames=3486`. Flags: `-Dceltium.vulkan.validation=false
-Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.batchTimings=true
-Dceltium.vulkan.frameStats=true` (a timing arm: it claims no validation).

```
accept[decideMs=33 preflightMs=1037 tailMs=0] parts[expand=49 matrix=57 extent=56 registry=821 supply=3072 prepare=1064]
prepare[nanos=780646564 calls=183783]   drawn[perFrame=112]   windows[perFrameX100=2700]
```

| part | ns per slow-path offer | share |
|---|---|---|
| **supply** | **3072** | 60% |
| prepare | 1064 | 21% |
| registry | 821 | 16% |
| extent / matrix / expand | 56 / 57 / 49 | 3% |

183,783 `prepareImmediate` calls over 3,486 world frames is **52.7 offers a frame**, so the accept path is
52.7 x 5,119 ns = **~0.27 ms of the leg's 2.68 ms frame (10%)**. The supply is the largest part and it is texture
uploads, not bookkeeping: the same line reads `rawWriteRefreshes=55539`, i.e. 15.9 uploads a frame.

**The registry memo fix (`d804e6c70`) is not established by this leg.** 821 ns here and 2,593 ns in round 29 are two
different scenes, so the difference is not an A/B. What this leg does establish is that the registry is no longer the
largest part on this profile, and that the plan's "next unit is the two calls, registry 2.6 us + supply 5.0 us" was
written from a scene whose per-offer mix is not this one.

## 28,272 failed uploads, and what they were

The same line carried `lazyMirror[supplies=0 rawWriteRefreshes=55539 skips=0 failures=28272 deferred=2]` - **8.1
failed uploads a world frame** - while `refusals=0` and every batch was accepted. So the failures cost an exception
each and changed no picture: the mirror kept whatever it already held.

The failures are one condition. `uploadFromGlTexture` can read two sources for a level - its own captured record and
its patch backlog - and when neither exists `readCapturedLevel` throws the capture-miss ("the stub captured no upload
for this texture level"). That is a fact about the level, not a failed upload: a name the game allocated empty and
never wrote answers the same way on every bind, and the retry gate could not help because the write serial moves on
every re-allocation.

## The fix

`CeltiumGlStub.celtium$levelHasNothingToRead(name, level)` asks the question directly (no record **and** no backlog),
and the three upload sites defer on it instead of throwing:

- the lazy supply, counted with the framebuffer deferral it sits beside (`lazyMirrorEmptyDeferrals`);
- a raw write applied in place (`celtium$applyRawWrites`), which returns "nothing left to apply" so the caller marks
  the serial and the same write is not asked about again;
- a coalesced raw write applied at the next window (`celtium$applyPendingRawWrites`).

A level that was allocated with no data but **is** recorded is not "nothing to read": the zero surface is content the
mirror is meant to hold, so the render-target idiom still uploads. That distinction is pinned by
`TextureContentSerialTest.nothingToReadIsTheAbsentRecordAndNotTheZeroSurface` on the real stub.

The failure total is also split by site (`failures=N(supply= raw= coalesced=)`) and the last failure is on the
frame-stats line, because one number could not say which of the three was failing and the three have different
repairs.

## The A/B

Same profile, same flags, same 240 s, only the JAR differs.

| | `r33a-accept` (renderer `5169773c`) | `r33b-empty` (renderer `b41f39e6`) |
|---|---|---|
| `lazyMirror` | `failures=28272 deferred=2` | `failures=0(supply=0 raw=0 coalesced=0) empty=33102` |
| empty deferrals / world frame | - | 7.8 |
| failures / world frame | 8.1 | 0 |
| `frameIntervalP50Ms` | 2.676 | 2.293 |
| `frameIntervalP95Ms` | 3.641 | 3.318 |
| `frameIntervalP99Ms` | 4.368 | 4.128 |
| `presentedDistinctColours` | 77,000 | 76,209 |
| `presentedNonBackgroundFraction` | 0.990197 | 0.989702 |
| `presentedVerticalBands` | 720 | 720 |
| `presentedImageVerdict` | PASSED | PASSED |
| validation | `passes=95867 errors=0`, 0 VUID | `passes=118408 errors=0`, 0 VUID |
| refusals | 0 | 0 |

7.8 deferrals a frame against the 8.1 failures a frame the old JAR raised: the same events, without the exception.
The picture gate is PASSED on both and the three picture numbers move by less than the scene's own animation does, so
the deferral changes no pixels.

**The frame interval is not a KPI claim.** p50 2.676 -> 2.293 ms is larger than the +/-7% run-to-run band the docs
record, but the two runs are not frame-identical scenes (distinct colours 77,000 vs 76,209) and this is one pair, not
the three-pair paired protocol. What is established is the error count: 28,272 -> 0 with the same scene and flags.

## What is left

- The supply is 60% of the accept path and it is uploads (15.9 a frame). The next question is whether those uploads
  are content the game really rewrote, or the same content re-uploaded because the mirror was rebuilt - the serial
  check in `uploadFromGlTexture` is the place that answers it.
- `windows[perFrameX100=2700]` = 27 windows a frame, `drawn[perFrame=112]`: the window fixed cost is ~0.15 ms a frame
  by the round-17 split, so it is not the gap either.
- The registry memo fix still needs a same-scene A/B before it can be called a win.
- Intel arm not run this round.
