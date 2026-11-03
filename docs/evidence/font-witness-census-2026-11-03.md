# The font run's splitter is the colour witness, and the group count is not what grpDraw measures

Round 43. Renderer `f93cbadc1efa9a91ddf8b8d1b94e4d6f02ff28faeab018c5a9d650503dbe851e`
(`celtium-forge-mc1.12.2-0.1.4-alpha-rc.26.jar`), fixture
`0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65`, AMD Radeon RX 6400
(RADV NAVI24), `celtium-world-accept`, `guiScale3`, `-Dceltium.vulkan.passSplit=true`, 240 s.
Legs `r43a-witness` (default) and `r43b-nocolour` (`-Dceltium.font.colourWitness=false`), same
JAR, same scene, same seed options. Both `verdict=PASSED`, `problems=[]`,
`fixtureStatus=PASSED`.

## 1. The previous round's number was the menu's, not a frame's

`docs/plan.md` said the world frame starts `fontRunsStarted=236` runs of one quad each. That line
is the **menu session's teardown** (`frameId=117`), not a per-frame figure, and
`fontQuadsAppended` counts **append calls**, not quads: the glyph capture buffers a whole string
segment and hands it over in one call, so 2,392 glyphs arrived in 236 appends (10.1 a call) on the
same leg.

The world teardown of the same run reads:

```
fontRunsStarted=118407 fontFlushCalls=118407 fontQuadsAppended=141921
fontFlushByReason[intervening=0 boundary=142 stateChange=121731]
groupCreateCount=143585  passBegins[world=1254 ...]
```

118,407 runs over 1,254 world frames = **94.4 runs a frame**, against 114.5 groups a frame
(`groupCreateCount`/`passBegins[world]`). The font path is still the largest single group source,
but it is 94 runs of a ~10-glyph string, not 236 runs of one quad.

## 2. Which witness closes the run: colour, and nothing else

`celtium$fontRunMatches` compares seventeen values. A new census
(`CELTIUM_FONT_WITNESS_CENSUS`, one increment per closed run) names the first one that differed:

```
CELTIUM_FONT_WITNESS_CENSUS colourWitness=true by[colour=142]
```

142 of 142 witness-closed runs were closed by the **colour** alone. No run was closed by the
matrices, the texture, the alpha test, the draw state, the scissor or the target. The census prints
once, at the first teardown, so this is the menu's distribution; the world's is unread (see §5).

## 3. The colour is per vertex, so the witness is not load-bearing

The glyph capture writes the live colour into each quad's four bytes; `immediate_tex_color.vert`
passes `a_Color` through as `v_Color`; `immediate_tex_color.frag` reads `v_Color`. The push block's
`u_ColorR/G/B` is read **only** by the GL_COMBINE branch, which is selected by a negative alpha
reference - a state no text draw uses. So for a run whose alpha reference is non-negative the
colour the group is submitted under cannot reach the picture, and a run may span a colour change.

`-Dceltium.font.colourWitness=false` is that arm. It is **not** the default: the witness was made
correct for a reason and the reason was not recorded as a picture (the first version packed float
bit patterns and produced 0 for almost every colour, so runs merged across colour changes including
the shadow pass and its light pass). The default stays on the side that has been accepted.

## 4. The A/B, and what it refutes

| bracket p50 ms | r43a (witness on) | r43b (witness off) |
|---|---|---|
| frame | 3.8 | 3.7 |
| world | 2.6 | 2.6 |
| gui | 1.1 | 1.0 |
| winRecord | 1.8 | 1.7 |
| **grpDraw** | **0.9** | **0.9** |
| wrecLoop | 1.1 | 1.1 |

Menu runs fell 284 -> 123 (2.3x) and the menu's appends per run rose 1.0 -> 2.0, so the merge
happened. The frame improved by 0.1 ms (2.6 %) and the GUI bracket by 0.1 ms (9 %), and
**`grpDraw` did not move at all**.

That refutes the assumption the previous round was built on: `grpDraw`'s 0.9 ms is not
proportional to the group count. Cutting 2.3x of the font runs moved the GUI bracket by 0.1 ms, so
the per-group cost on this scene is ~1.5 us, not the ~7.9 us that dividing 0.9 ms by 114 groups
suggests. The two numbers cannot both be per-group costs, and the A/B is the one that was measured.

The picture: the HUD's per-segment colours are intact in both arms (green `CeltiumValidation`,
white `| FPS:`, green `| Ping:`, yellow `April 1`, white `Biome:`, yellow `Weather:`, white
`Light:`/`Experience:`/`This is an ore chunk`), and the JourneyMap labels and hotbar counts are
present. A cross-run pixel diff is not evidence here - two runs of one JAR differ on 75 % of their
pixels with every recorded input equal (the `CELTIUM_WORLD_MATRIX` note records the same shape).

## 5. What this leaves

- The world's witness distribution is unread because the census prints once and the menu tears down
  first. Printing it at every teardown is the next instrument change, and it is one line.
- `grpDraw`'s 0.9 ms needs a per-group split that is not the group count: the driver's
  `vkCmdDrawIndexed` is `packetDrawMillis=455` over the run (0.32 ms a frame), so the rest is
  Java inside the bracket and the bracket is not scaling with groups.
- The colour witness stays on by default. Flipping it needs a within-run image comparison, not a
  cross-run one.
