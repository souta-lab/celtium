# The font colour witness is only load-bearing under the combine (2026-11-04, round 56)

The world frame starts **94.5 font runs a frame**, and every one of them was closed by the colour witness
(`font-witness-census-2026-11-03.md` §2: 142 of 142 witness-closed runs were closed by the colour alone). The
fragment stage reads the colour **per vertex**, and reads the push block's colour only inside the GL_COMBINE branch,
which a negative alpha reference selects. So the witness was comparing a value the picture cannot see, on every run
whose alpha reference is non-negative - which is every text draw.

This round narrows the witness to the branch that reads it, and measures what the merge buys.

## 1. The rule, and why it is not a bet

`immediate_tex_color.frag`:

```glsl
vec4 colour = texture(u_Texture, v_TexCoord) * v_Color;
if (celtium.u_AlphaRef < 0.0) {
    ... colour.rgb = mix(colour.rgb, vec3(u_ColorR, u_ColorG, u_ColorB), -u_AlphaRef);   // GL_COMBINE
} else {
    int alphaFunc = int(celtium.u_MvColumn2.y + 0.5);
    if (alphaFunc != 0 && !celtiumAlphaPass(alphaFunc, colour.a, celtium.u_AlphaRef)) discard;
}
```

`u_ColorR/G/B` appears **only** inside `u_AlphaRef < 0.0`. Outside it the fragment is `texture * v_Color`, and the
glyph capture writes the live colour into each quad's four bytes. A run that spans a colour change therefore draws
each quad with its own colour.

The key still compares the alpha reference on its own line, so a run can never span a change of branch: when the
reference moves the run closes there whether or not the colour was compared. The only runs whose key loses the colour
are the ones whose submission colour the fragment stage cannot read.

`FontBatch.celtium$colourWitnessNeeded(mode, alphaReference)` is the rule; `-Dceltium.font.colourWitness` is now a
mode, not a boolean: `combine` (default, the rule above), `all` (the previous behaviour and the A/B arm), `off` (the
diagnostic control that removes the witness even under the combine).

## 2. What the merge does to the frame's work (acceptance legs, default flags)

Both legs: AMD Radeon RX 6400 (RADV NAVI24), `celtium-world-accept`, `qmagnet`, 1280x720, `guiScale3`,
`sceneCalibrationKeyMatches=true`, default flags so validation is ON. Only the JAR differs.

| per frame | `r55b-gate` (before) | `r56a-merge` (after) | change |
|---|---|---|---|
| `fontRunsStarted` | 94.5 | **19.0** | **-80%** |
| `immediateOffered` | 221.4 | **146.0** | **-34%** |
| `immediateGroupsDrawn` | 115.2 | 115.0 | 0% |
| `immediateWindows` | 23.2 | 23.2 | 0% |
| `appendMillis` | 0.327 | **0.235** | -0.092 |
| `prepareImmediateMillis` | 0.503 | **0.332** | -0.171 |
| `groupDrawMillis` | 1.820 | 1.799 | -0.021 |
| `windowRecordMillis` | 2.512 | 2.494 | -0.018 |

`batchTimings[... prepare=]` is **2271 ns/offer before and 2276 ns/offer after**, and `appendMillis` is 1.48 us per
append before and 1.61 us after - the per-call cost did not move, so the saving is the call count:

```
75.4 fewer offers/frame x 2.27 us (prepare) = 0.171 ms/frame
75.4 fewer appends/frame x 1.48 us (append) = 0.112 ms/frame
                                             0.283 ms/frame of accept-path CPU removed
```

**The groups did not move.** That is the round's refuted hypothesis: the font runs were *not* 82% of the frame's
groups. Each run is offered as a batch and the fast-merge path folds it into the group that is already open, so
`immediateGroupsDrawn` stays at 115 a frame and `drawn[perFrame=112]` is identical in both arms. The merge removes
offers, not draws.

## 3. The picture

| | `r55b-gate` | `r56a-merge` |
|---|---|---|
| `presentedDistinctColours` | 76,920 | 76,990 |
| `presentedNonBackgroundFraction` | 0.990197 | 0.990171 |
| `presentedBlackFraction` | 0.00741 | 0.006834 |
| `presentedImageVerdict` | PASSED | PASSED |

The two legs end at different ticks (353 vs 345), so the animation phase differs and the metrics are not expected to
be bit-identical. The picture is not claimed identical; it is claimed unchanged in every gate and in the shader
contract above.

## 4. The within-JAR A/B, and why it does not settle the end-to-end

Two legs, same JAR (`1275d7dd...`), same scene, same seed options, KPI flags
(`-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0`)
plus the warm window (`warmFromTick=400 warmToTick=600 holdUntilTick=620`) and `frameStats`+`passSplit`:

| | `r56b-combine` | `r56c-all` |
|---|---|---|
| `frameIntervalWarmP50Ms` | **3.053** | 4.316 |
| `frameIntervalWarmP95Ms` | 5.958 | 8.963 |
| `frameIntervalWarmSampleCount` | 2,978 | 2,069 |
| `frameIntervalWarmOver16Ms` | 2 | 15 |
| `CELTIUM_FRAME_STATS ms[p50]` (last 120 frames) | 2.60 | **2.15** |
| `loop[... renderMean]` | 1.85 | **1.51** |
| `loop[... tickMean]` | 1.35 | **1.17** |
| `passSplit frame[p50]` | **1.4** | 1.5 |
| `prepare[calls]` | 169,555 | 192,681 |

**The two windows disagree in sign.** The warm window says the combine arm is 1.26 ms faster; the last-120-frames
window says it is 0.45 ms slower; `passSplit frame[p50]` says 0.1 ms faster. `tickMean` differs by 0.18 ms and
`displayMean` by 0.14 ms, and the change cannot touch either - so the legs are confounded by machine or world state,
and the run-to-run spread is larger than the 0.28 ms the change removes.

**This leg does not claim an end-to-end speed-up.** What it does say is that the effect is below the single-leg noise
floor, so the end-to-end question needs the KPI's own shape: several paired reps, ABBA order, one JAR.

## 5. Acceptance

`r56a-merge`: `verdict=PASSED`, `problems=[]`, `fixtureStatus=PASSED`, `presentedImageVerdict=PASSED`,
`sceneCalibrationKeyMatches=true`, `sceneGateFloorsEnforced=true`, **0 VUID lines**,
`CELTIUM_REFUSALS_BY_FORMAT {}`, `vulkan-check passes=33978 skipped=0 errors=0`, `refusedBeforeAppend=0`,
`shaderProgramRefusals=0`, `windowFlushFailures=0`, `orderingFlushFailures=0`, `gpuStalled=false`,
`presentSucceeded=true`, `worldFrames=1456`, `worldTicksAtEnd=345`.

## 6. What is not claimed

- No end-to-end frame-time improvement (see §4).
- No claim that the picture is pixel-identical (§3).
- No claim about the GPU side: the merge makes one larger draw instead of several smaller ones, and that was not
  measured.
- The `all` arm is not a shipped configuration; it exists so the previous behaviour stays reachable for an A/B.
