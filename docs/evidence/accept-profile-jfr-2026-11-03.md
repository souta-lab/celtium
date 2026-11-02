# the accept profile's client thread, profiled: Celtium is 43% of it and no single item is over 3.6% (2026-11-03)

Completion condition C ("faster than Celeritas, proved by a paired measurement on the same JAR and the same
conditions") is 1.66-1.76x behind on the real-world profile, and the split that named the brackets
([paired KPI](paired-kpi-world-accept-2026-11-03.md), [window split](window-split-2026-11-03.md)) left `other`
- 1.6 ms of a 3.2 ms frame - unnamed. This round profiles that frame's client thread and answers it.

The answer is not a hotspot. **Celtium's own code is 43.4% of the client thread, the accept path is 19.0% of it,
and the largest single self-time frame in the whole profile is 3.6%.** The gap is spread over the per-offer and
per-window work the renderer does for every draw the game makes, which is the structural item the plan already
named (record off the client thread, or fewer offers), not a line to fix.

## The run

| | |
|---|---|
| Leg | `r45b-accept` - `world` profile (`celtium-world-accept`, 188 mods, `qmagnet`), AMD Radeon RX 6400 (RADV NAVI24), `gpuRequirementMet=true` |
| Renderer / fixture | `479c783b87274f674ed81b734a7e8d43f0625486c124d492c30d65b6c2d4d600` / `79899e494374f89f5c813029d937985d5231265bf91abf027b1ed683860303ca` (the committed tree's reproducible pair) |
| Scene | `sceneCalibrationKeyMatches=true`, `worldTicksAtEnd=619`, 1280x720, `guiScale3.txt`, warm window ticks 400-600 |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` (the KPI flags: this is a timing/profile arm and claims no validation) |
| Recording | `CELTIUM_LEG_JFR="delay=120,duration=45,path=…/r45b.jfr"` - attached with `jcmd` to the game JVM at 120 s, 45 s, `settings=profile` |
| Samples | **1824 client-thread `jdk.ExecutionSample`** in 45 s (plus 477 `NativeMethodSample`) |
| Verdict | `PASSED`, `problems=[]`, `fixtureStatus=PASSED`, `presentedDistinctColours=76734`, `nonBackgroundFraction=0.9902` |

The same JAR on the same profile without a recording (`r45a-accept`) is `PASSED` too, `frameIntervalWarmP50Ms`
4.187 against this leg's 4.359 - the recording's own sampling cost, which is why a JFR leg's milliseconds are not
a performance measurement.

## Where the client thread goes

Inclusive share of the 1824 samples (a frame is in the stack, not necessarily at its top):

| bucket | share |
|---|---|
| `org/soutalab/celtium` anywhere in the stack | **43.4%** |
| `VulkanTerrainGpuBackend` | 36.7% |
| `org/taumc/celeritas` (the font capture and the GL-less hints) | 28.0% |
| `CeltiumGlStub` | 3.7% |

The renderer's own items, inclusive:

| item | share | what it is |
|---|---|---|
| `submitGeometryBatch` | **19.0%** | the accept path, including the flushes it triggers |
| `flushPendingBeforeGlDraw` | 10.8% | the ordering flush |
| `flushImmediateWindow` | 9.8% | one window: begin/end render pass, snapshot, submit bookkeeping |
| `recordImmediateOnlyPass` | 6.7% | the window's recording |
| `fastAtlasGlyph` | 5.3% | the per-glyph font capture |
| `drawPendingImmediate` | 3.6% | the group loop |
| `celtium$flushPendingFontBatch` | 3.6% | the font run's flush |
| `copyVerticesTransformed` | 2.1% | the per-vertex transform for pre-transformed groups |
| `uploadFromGlTexture` | 1.5% | texture uploads |

Top self time (the frame at the top of the stack), which is where a fix would land:

| self | share | frame |
|---|---|---|
| 66 | 3.6% | `VulkanTerrainGpuBackend.submitImmediateBatchBody` |
| 39 | 2.1% | `it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.get` (25 of them `VintageChunkMap.get`, a mod) |
| 38 | 2.1% | `net.minecraftforge.registries.ForgeRegistry.getValue` (mods) |
| 36 | 2.0% | `VulkanDynamicMesh.copyVerticesTransformed` |
| 32+29+21 | 4.6% | `World.getBlockState`/`getChunk`/`getHeight` (172 of the block lookups are Biomes O' Plenty's `FogEventHandler.onRenderFog`) |
| 31 | 1.7% | `ASMEventHandler.invoke` (Forge event dispatch) |
| 28 | 1.5% | `HashBiMap.seekByKey` (Forge registry) |
| 21 | 1.2% | `CeltiumFontGlyphCapture.fastAtlasGlyph` |
| 12 | 0.7% | `drawPendingImmediate` |

Two facts follow, and both matter for what to do next:

* **The frame is not Celtium's alone.** `InGameInfoCore`/`Ticker` is 15.0% inclusive and `GuiIngameForge` 15.1%:
  the pack's HUD draws a lot of text every frame, and Biomes O' Plenty's fog handler alone is 9.4% (172 samples
  of world block lookups). Those are shared with the Celeritas arm and are not the gap.
* **Celtium's share is flat.** The accept path is 19.0% and half of it is the window flush (9.8%); the per-offer
  work is the other half, spread across the state capture, the key, the merge decision and the append. The
  largest single self-time frame in the renderer is 3.6%, so removing any one of them moves the frame by less
  than the run-to-run noise the project already measures (+-7%).

## What this rules out, and what it names

Ruled out as the next unit: a micro-optimization of one accept-path method. The profile is the measurement that
the earlier "the split is even, no single big item exists" reading needed, and it says the same thing with
samples instead of averages.

Named instead, in the order the numbers support:

1. **Fewer windows.** 9.8% of the frame is `flushImmediateWindow` and 6.7% is the recording inside it, for ~36
   windows a frame carrying ~3.7 groups each. A window is a render pass begin/end plus a snapshot plus submit
   bookkeeping; the per-window fixed cost is the item, not the draws.
2. **Fewer or cheaper offers.** 19.0% inclusive, ~220 offers a frame, and the per-offer work is the state
   capture + key + merge + append. This is the plan's §12 Stage 1 (packetize the offer) and Stage 4 (frame
   compiler), and the profile is the go/no-go the plan asked for: there is no single line to cut.
3. **The font path** (5.3% + 3.6%) is the third item and is Celtium's own code
   (`forge122/.../CeltiumFontGlyphCapture`), so it is reachable without touching the mod.

## The measurement defect this round found and fixed

The documented JFR procedure (`-XX:StartFlightRecording=…` as a leg JVM argument) **does not record the game**.
The option reaches both the Java 8 launcher and the Cleanroom relauncher, both write the same file, and the
launcher exits last - so its recording (two idle threads, zero Java samples) overwrites the game's. Measured on
leg `r45a-accept`: the file held 1031 samples, all `AWT-XAWT` and `process reaper`, and `jdk.ExecutionSample`
was 0 while the client thread was rendering a world. The same leg attached with `jcmd` (`r45b-accept`) gave 1824
client-thread samples.

`scripts/gv_leg.py` now takes `CELTIUM_LEG_JFR="delay=SECONDS,duration=SECONDS,path=FILE"`, attaches with the
`jcmd` that sits beside the game JVM's own `java` (so no JDK is assumed), and writes `jfrAttach`
(`started`/`pid`/`command`/`output`) into the leg's result - a leg whose recording did not start says so instead
of leaving a zero-byte file that reads as "nothing was running". The procedure is in
[development.md](../development.md); the reader used here is `.celtium-work/jfr-top.py` (scratch, not tracked).

## The code change

`CeltiumGlStub.textureLevelExtent(name, level, out)` answers the bound texture's recorded width and height in
**one** map lookup. The accept path asked for them with two `textureLevelParameter` calls, which hash the same
`(name, level)` key twice on every offer; the profile puts the pair at ~0.2% of the frame. The caller's "no
extent" test is unchanged (`!hasExtent || width <= 0 || height <= 0`), so a half-recorded level still samples
black exactly as before.

The change is verified on the standard acceptance leg, not by the profile: `r45c-gate` (plain/Supersymmetry,
AMD RX 6400, 1280x720, `guiScale3.txt`, validation ON) with renderer `ad877587…` and fixture `79899e49…` is
**`PASSED`**, `problems=[]`, `validationMessageCount=0`, `validationClean=true`, zero `VUID` lines,
`CELTIUM_REFUSALS_BY_FORMAT {}`, `vulkan-check passes=4237 skipped=0 errors=0`,
`sceneCalibrationKeyMatches=true`, `presentedDistinctColours=10904`, `nonBackgroundFraction=0.888`,
`blackFraction=0.0038` against the terrain floors. That leg is a regression check on the changed path (every
textured offer goes through it), not a speed measurement.

`ad877587…` is the committed tree's JAR, not a nearby one: `:forge122:packageJar --rerun-tasks --no-build-cache`
on this commit rebuilds the same sha256 (round 24's reproducible-archive fix, confirmed on a changed source tree).

## What this does not claim

* **No speed claim.** The change above is ~0.2% of a frame by the profile's own count, and no leg was run to
  measure it: the two legs here are the same JAR (before the change) and their difference is the recording's
  cost.
* **No validation arm.** Both legs ran with validation off (the KPI flags), so neither is a VUID-0 claim.
* **No Intel arm.**
* **No root cause for the 1.68x.** The profile says where the time is, not why Celeritas needs less of it.
* The `other` 1.6 ms of the budget split is now partly named (the pack's HUD text, BOP's fog, Forge registry
  lookups) but not attributed per mod beyond the three named here.
