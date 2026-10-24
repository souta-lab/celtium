# Staging ring for texture patch uploads (2026-10-24)

## Attribution (before)
- leg `tickjfr-celtium-2`, JAR `652075c9d4e4a07cf2ea438442458142a863690a955f84608767deb72a3c3e4c` (HEAD 204ca9fdb, docs-only dirty),
  AMD RX 6400 (RADV NAVI24), scene world (qmagnet), flags: validation off, presentReadback off, presentedDumpEvery=0,
  frameDumpEvery=0, warm 400..600, holdUntilTick=620, JFR `settings=profile,stackdepth=1024`.
- Window: last 11 s .. last 1 s of game-loop samples (client thread `main`), 692 samples (Java + native).
- `Minecraft.runTick` 6.2% of samples; `TextureMap.updateAnimations` 4.2%; 73/80 animation samples (whole run) sit in
  `uploadPatchedLevels -> ensureStaging -> awaitOutstandingUpload -> vkWaitForFences`.
- Same flags on Celeritas 2.5.0-dev (`567dcf8a…`, leg `tickjfr-celeritas-1`): updateAnimations 1.0%.
- Note: `jfr print` truncates stacks to 5 frames unless `--stack-depth` is given; the first attribution attempt was
  invalid for that reason.

## Change
`VulkanGlTexture.reserveStagingSlice`: patch uploads take the next slice of the staging buffer; only a wrap waits
(on the latest upload fence, which covers every earlier submission on the queue). Buffers under 1 MiB grow on wrap.

## Result
KPI `ring-kpi-1` (candidate `6c041aa39eaaedebec228d72232c01e5de292e034a610f5d60aba6ddbaec95cb`, control `652075c9…`,
reference Celeritas `567dcf8a…`, 3 interleaved reps, load 1.4→2.6):

| rep | candidate p50/p95/samples | control p50/p95/samples | Celeritas p50/p95/samples |
|---|---|---|---|
| 1 | 2.141 / 3.39 / 4388 | 2.126 / 5.51 / 4193 | 1.258 / 1.72 / 7393 |
| 2 | 2.064 / 3.68 / 4044 | 2.076 / 6.90 / 3606 | 1.244 / 1.68 / 7551 |
| 3 | 2.069 / 3.42 / 4531 | 2.034 / 6.01 / 4299 | 1.217 / 1.51 / 7806 |

median control ratio (p50) 1.007 = neutral; median Celeritas ratio 1.699x. p95 improves ~42%.
Correctness: leg `ring-valid-amd-1` (validation ON) PASSED, validationMessageCount 0, presented image verdict PASSED.
Intel not measured.
