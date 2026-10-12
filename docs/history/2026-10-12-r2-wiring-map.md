# R2 wiring map — the verification line, run by run

Delivered by `acceptance` with the seven statistics classes (`36cef2c4d`). This is the **wiring specification** for
`VulkanTerrainGpuBackend.verificationSummary()`: it replaces the concatenation with the classes' `appendRunN(StringBuilder)`
calls **in this order**, and the line's field names and their order must not change.

The classes were checked mechanically against the line: **every run's emitted text is verbatim in the current string,
0 mismatches, 0 missing runs**. The only text dropped is inline comments, which cannot change the output. So the
baseline is byte-exact and `scripts/cpu-contract/verify_line_check.py` (plus a leg's `CELTIUM_VERIFY` line) is the
oracle: any stray space, bracket or comma shows up as a named key diff.

## Run order and line ranges

"Lines" = replace from the first literal of the run to the line before the next run starts.

| # | class | run | backend lines |
|---|---|---|---|
| 0 | Terrain | run0 | L8307-L8309 |
| 1 | Immediate | run0 | L8310-L8319 |
| 2 | Submission | run0 | L8320-L8338 |
| 3 | Texture | run0 | L8339 |
| 4 | Target | run0 | L8340-L8342 |
| 5 | Immediate | run1 | L8343 |
| 6 | Target | run1 | L8344-L8351 |
| 7 | Submission | run1 | L8352-L8357 |
| 8 | Target | run2 | L8358-L8361 |
| 9 | Submission | run2 | L8362 |
| 10 | Target | run3 | L8363-L8364 |
| 11 | Immediate | run2 | L8365-L8379 |
| 12 | Submission | run3 | L8380 |
| 13 | Immediate | run3 | L8381-L8411 |
| 14 | Texture | run1 | L8412 |
| 15 | Immediate | run4 | L8413-L8414 |
| 16 | Target | run4 | L8415-L8420 |
| 17 | Immediate | run5 | L8421-L8437 |
| 18 | Terrain | run1 | L8438-L8455 |
| 19 | Immediate | run6 | L8456-L8490 |
| 20 | Texture | run2 | L8491 |
| 21 | Immediate | run7 | L8492 *(shared)* |
| 22 | Target | run5 | L8493 |
| 23 | Immediate | run8 | L8494-L8496 |
| 24 | Target | run6 | L8497-L8510 |
| 25 | Immediate | run9 | L8511-L8518 |
| 26 | Target | run7 | L8519-L8523 |
| 27 | Texture | run3 | L8524-L8535 |
| 28 | Submission | run4 | L8536-L8537 |
| 29 | Target | run8 | L8538-L8543 |
| 30 | Submission | run5 | L8544-L8545 |
| 31 | Immediate | run10 | L8546 |
| 32 | Texture | run4 | L8547-L8553 |
| 33 | Model | run0 | L8554 |
| 34 | Immediate | run11 | L8555 |
| 35 | Model | run1 | L8556-L8580 |
| 36 | Immediate | run12 | L8581-L8582 |
| 37 | Texture | run5 | L8583-L8603 |
| 38 | Target | run9 | L8604-L8608 |
| 39 | Texture | run6 | L8609-L8613 |
| 40 | Submission | run6 | L8614-L8616 |
| 41 | Immediate | run13 | L8617-L8621 |
| 42 | Target | run10 | L8622-L8623 *(shared)* |
| 43 | Texture | run7 | L8624 |
| 44 | Immediate | run14 | L8625-L8626 |
| 45 | Submission | run7 | L8627 |
| 46 | Texture | run8 | L8628-L8640 |
| 47 | Immediate | run15 | L8641-L8642 |
| 48 | Submission | run8 | L8643-L8648 |
| 49 | Terrain | run2 | L8649-L8655 |
| 50 | Submission | run9 | L8656 |
| 51 | Terrain | run3 | L8657-L8658 |
| 52 | Texture | run9 | L8659 |
| 53 | Immediate | run16 | L8660-L8682 |
| 54 | Immediate | run17 | L8683-L8686 |
| 55 | Submission | run10 | L8687-L8691 |
| 56 | Immediate | run18 | L8692-L8708 |
| 57 | Font | run0 | L8709-L8715 |
| 58 | Texture | run10 | L8716-L8717 |
| 59 | Immediate | run19 | L8718-L8733 |
| 60 | Terrain | run4 | **L8734 only** *(shared)* |
| 61 | Immediate | run20 | L8734-L8756 |
| 62 | Immediate | run21 | L8757-L8762 |
| 63 | Submission | run11 | L8763-L8764 |
| 64 | Immediate | run22 | L8765-L8775 |
| 65 | Submission | run12 | L8776-L8777 |
| 66 | Immediate | run23 | L8778 |
| 67 | Submission | run13 | L8779-L8782 |
| 68 | Immediate | run24 | L8783-L8788 |

## The three lines two classes share

Only these three cannot be replaced as whole lines; the exact `out.append` sequence disambiguates them, and they are
the only place a stray space can enter by hand:

- **L8492** — Immediate `clearOrderFlushes` + Texture `descriptorMisses`
- **L8622** — Immediate `snapshotCaptures`/`snapshotServed` + Target `outsideWorldPassRefusals`
- **L8734** — Terrain `lastTerrainPassFrame` + Immediate `frameId`/`matrixMirrorDelta`

## Counters the runs emit but no class owns (155 of 349)

Their value expressions stay where they are; the run only places them. Owners: `VulkanContext.*` (bootstrap*,
submitTimeouts, queueSubmitCalls, waitOutcomes, quarantined, fences, driverInfo, submitBuckets),
`CeltiumGlStub.*` (polygonOffsetCalls/FillEnabled/Called), `TextureCopyService.*` (all textureCopy*/textureCopies*),
`VulkanTextureRegistry` (inPlaceRefreshes, mirroredTextures, externalImages, imageFilledMirrors, readbackByOrigin),
`VulkanModelQuadCapture.*` (modelStoredFloats, modelFirstVertex, modelMatrixPair, modelLists, modelListQuads,
modelQuads, modelQuadVertices, modelQuadExtent, displayList*), `VulkanImmediateTexturePipeline.*`
(descriptorHits/Misses, pipelineBuildRollbacks), `VulkanGlTexture.samplerFilterCensus()`, `FramePassSplit`
(`framePassSplit`), the `celtium$arenaUpload*` accessors, and the backend helpers `offerStageSummary()`,
`unflushedTargetDropSummary()`, `flushEntrySummary()`, `offTargetPerTarget()`, `frameTargetPairSummary()`,
`celtium$perFormat(...)`, `round3(...)`, `colorMaskHistogramText()`. Those helpers and objects must stay reachable from
the class that calls them - passed in, or moved with the domain, which is the wiring decision below.

## Ownership decisions (Lead)

- **Emitted twice, one counter**: `frameId`, `bootstrapQueueWaits`, `gpuStalled`, `offTargetRefusals`,
  `outsideWorldPassRefusals` - the counter stays where it is and both runs read it.
- **`publishedFrameTextures*` → Texture**: written by the publication path, but R1b's `PublishedFrameTextures` owns the
  state and the run reads its accessors.
- **`polygonOffset*` → Immediate**: the fragment is Immediate's, the counter stays in the GL stub.
- **`framePassSplit` → Submission**: it is a `FramePassTimings` instrument, placed by where it sits in the line.
- **`frameArena`/`frameSlots`/`slot*` → Submission**: consistent with the earlier decision that the scheduler owns the
  slots and their rules while the arena owns the list and the release target.
- **`stalledStops`/`stalledTeardownSkips`/`idleWaits`/`injectedIdleFailures`/`idleWaitTimeouts`/`idleWaitRetries` →
  Submission**; **`uniformPreparationFailures` → Immediate** - as delivered, and they remain candidates for a shared
  split if a later stage needs one.

## How this is verified

One build, then `scripts/cpu-contract/verify_line_check.py` for the string, and a leg whose last `CELTIUM_VERIFY` line
is diffed against the pre-wiring baseline. The baseline is byte-exact, so a mistake appears as a named key diff rather
than as a silent change in a leg record.
