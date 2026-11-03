# The render's own split, and the state signature's version cache (2026-11-04, round 58)

Two products: a current-JAR measurement of where the render's 1.2 ms goes (the round-57 handoff's open
question), and one exact hot-path change whose hit rate the same measurement reports.

## 1. The render split, shipping configuration (leg `r58a-split`)

`-Dceltium.vulkan.passSplit=true -Dceltium.vulkan.packetTimings=true -Dceltium.vulkan.frameStats=true`,
validation off, warm window ticks 400–600, AMD RX 6400 (RADV NAVI24), renderer `051a03dd…`, fixture
`0e90c988…`. Leg PASSED, `fixtureStatus=PASSED`, 175.8 s.

`CELTIUM_FRAME_PASS_SPLIT` (p50 ms, 512 frames):

| bracket | p50 | note |
|---|---|---|
| `frame` | 1.2–1.3 | the bracketed render, not the frame interval |
| `world` | 0.8–0.9 | |
| `gui` | 0.4 | a third of the render |
| `hand` | 0.0–0.1 | |
| `flush` (inside world) | **0.4** | the largest renderer-owned item |
| `winRecord` (inside flush) | 0.3 | |
| `wrecLoop` (inside winRecord) | **0.2** | the group loop |
| `grpDraw` (inside wrecLoop) | 0.1 | the driver calls themselves |
| `terrain` / `terrainPreflush` | 0.1 / 0.1 | |
| `guiBatch` / `guiFlush` | 0.1 / 0.1 | Celtium's share of the HUD |
| `winHead`, `winPublish`, `winBeginPass`, `winPassEnd`, `winSubmit`, `winBarrier`, `wfpBegin`, `wfpMiddle`, `flushCloseGroup` | 0.0 each | the window's fixed cost, spread |
| `rcResolve`, `rcPush`, `rcViewport`, `rcPrepared`, `offCapture`, `offPrepare`, `offMerge`, `offAppend`, `offPreflight` | 0.0 each | the per-group and per-offer Java |

**The window's fixed cost is not one item.** The eight brackets that cover a window outside its group loop
each read 0.0 at the report's 0.1 ms resolution, and together they are the ~0.2 ms that `flush` holds
beyond `wrecLoop`. That is the same shape the plan records as the micro-opt floor, now measured on the
current JAR rather than inferred: **no single renderer-owned item is above 0.2 ms, and the largest is the
group loop itself.**

## 2. The driver calls are already near their floor (same leg)

`packetTimings` run totals over 727,499 packets:

| counter | value | per call |
|---|---|---|
| `packetDrawMillis` | 498 | **0.68 µs per `vkCmdDrawIndexed`** |
| `packetBindMillis` | 76 | 0.10 µs per vertex/index bind pair |
| `packetPipelineMillis` | 2 | 0.5 µs per pipeline bind, **4,117 binds in 727,499 packets** |
| `packetPushMillis` | 3 | — |
| `packetBindSkips` | 348,012 | 48% of packets needed no bind |

The pipeline dedupe is doing its job: 0.6% of packets bind a pipeline, because the opaque groups are
ordered by material. So `grpDraw`'s 0.1 ms is 145 draw calls a frame at 0.68 µs, and the only way to
reduce it is to issue fewer draws — which is the group count, not the call.

`packetPushMicros` read 0 while `packetPushMillis` was non-zero: the push bracket closed inside the
`if` that the push cache guards, so a skipped push left its start unanswered and the next packet's close
attributed everything since to "push". Fixed in this round (see §4); the same unbalanced-bracket defect
the accept path's `offMerge` had.

## 3. What this says about the Celeritas gap

The render is 1.2 ms; Celtium's own part of it is ~0.7 ms (`flush` 0.4 + `guiBatch` 0.1 +
`terrainPreflush` 0.1 + `grpDraw` 0.1), and the rest is the game's own drawing, which Celeritas pays too.
The paired gap is ~0.5 ms. **Closing it needs the 0.7 to become ~0.2, and the measurement says that is
fifteen items of 0.03–0.1 ms, not one of 0.5.** The two structural attempts that would have collapsed
several at once (sticky window pass, bind-flush deferral) are already rejected with evidence.

## 4. The change: the state signature is reused while its three versions are unchanged

`celtium$stateSignature` reads twenty-one inputs on every offer, two of them through a hash map. Every
input is owned by a version that moves when it moves:

- `VulkanGlState.stateVersion()` — the wrapper's own fields plus the raster mirror (blend, colour mask,
  cull, front face, depth, alpha test, lighting);
- `NativeTextureEnvironment.version()` — the two environment parameters (`MODE`, `RGB`);
- the window-target bit — the registry's identity, compared directly.

So "all three are the ones the cached signature was taken under" is exactly "the signature is the same",
and the capture is skipped. The cached array is a copy, not the scratch: `ModelReplay` calls the same
method with its own arrays, and a hit arraycopies into them.

**Measured hit rate (leg `r58b-cache`, shipping configuration, AMD RX 6400, renderer `de8ed2e7…`,
fixture `0e90c988…`): `stateSignatureCache=1192988/1464150` — 44.9% of 2.65 M captures skipped.**
Leg PASSED, `fixtureStatus=PASSED`, 169 s, picture `celtium-presented-world-run-9de21680fac8-330bedb4-kept.png`.

`StateSignatureVersionContractTest` enumerates the inputs one assertion per signature slot, so a setter
that forgets its bump fails by name. That is the failure mode that matters: a stale signature is a wrong
pipeline, not a slow frame. The test also pins the predicate (`celtium$signatureCacheHit`) and the
monotonicity of the version.

## 5. Acceptance

- `JAVA_HOME=java-25 scripts/build.sh :celtium-vulkan:test :forge122:packageJar :forge122:validationRemapJar`
  — BUILD SUCCESSFUL, celtium-vulkan **401 tests / 0 failures** (was 397).
- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` — 177 tests OK.
- `python3 scripts/check_agent_docs.py` — OK; `python3 scripts/instrument-index.py --check` — current
  (regenerated in the same commit).
- `scripts/cpu-contract/transformer-route-verify.sh` — called=241 failed=0, classes 87/0, colour 32/32,
  texcoord 64/64, query 8/8.
- Legs: `r58a-split` PASSED (measurement), `r58b-cache` PASSED (the change).
