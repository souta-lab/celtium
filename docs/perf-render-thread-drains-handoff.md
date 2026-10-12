# perf/render-thread-drains — レンダースレッド直列化の排除 引き継ぎ

ブランチ: `perf/render-thread-drains` (rebased onto `b4d3f5279`、commit `8b2bcd412`+`53871c9b1`)
worktree: `/home/s0u7a/projects/celtium-drains` — 主担当のcheckout `/home/s0u7a/projects/celtium` とは別。
**証拠範囲の訂正（2026-10-12 追記）**: この文書が証明するのは「記録した fixture 区間で、記録対象の待ちカウンタが 0 になった」ことまで。baseline の `frameFenceWaitMillis` は全 run 合計 40ms で、この drain だけではユーザープロファイルの `gameRenderer` 無名残差 ~246ms/frame・p99≈326ms の主因を説明しない。残差 spike の原因は**未帰属**（GC/atlas/section rebuild/pipeline compile/計器未覆域は候補であり、外部知見だけで断定しない）。帰属は性能 goal の次の作業。

## 根因（測定 + コードから確定）

実プレイの大きなフレーム時間スパイクの主要構造因は「レンダースレッドが1フレームに何十回もGPU完了を直列待ちする」こと:

1. **immediate window ごとの drain** (commit `6897b433d` で入った「同期window」):
   `flushImmediateWindow` が `submitCommands`→`context.flushUploads()`(submit+fence待ち)→`waitForIdle()`(全pending slotの排水)→`endFrame`。1フレーム ~16 window で、この drain は**帳簿専用**で semaphore 正当性には不要だった。
   - GL bridge 経路: GL 側の endFrame が doneSem を待つ → GL が次の beginFrame で waitSem を出す時点で Vulkan 側完了は semaphore で既に保証されている。
   - GL-less 経路: wait/done semaphore が自前 no-op submit の連鎖のみ → 全て同じ queue の提出順序で完全にカバーされる。
2. **`beginCommands` の全量 drain**: `submissionPending` → `waitForIdle()`。直前の submit が GPU で走っていても CPU は全 drain を支払う。
3. **upload batch の逐次 submit+wait**: `flushUploads` がバッチごとに `endCommands`(submit+fence待ち)。
4. **texture 操作の逐次 submit+wait**: `uploadPatchedLevels`/`copyToImage`/`transitionToShaderRead` がそれぞれ submit+fence待ち（layout transition のような「次以降に先行すればよい」ものまで毎回待っていた）。
5. **present submit の逐次 wait**: `submitWithSemaphores` が submit+full fence待ち。

合計で1フレーム数十回の `vkWaitForFences` 系ラウンドトリップ — 軽いGPU負荷では 20–40ms/leg、負荷があると queue に溜まった分だけ待ちが膨らむ。ユーザーのスパイク構造の一因としては**確定的待ちの除去**が有効だが、40ms という baseline の待ち合計では ~246ms/frame の無名残差を説明しない（残差は未帰属、GC/atlas/rebuild/compile/計器未覆域いずれも候補）。

## 修正（全部まとめて、commit `081491bb7`）

- **FRAME_SLOTS=2→4**、各 slot に `slotCommandBuffers[]`/`slotFences[]`/`slotPending[]`/`slotSubmitSequence[]` + per-slot `VulkanCommandBufferState`（`commandState` は beginCommands で現在 slot へ alias）。`beginCommands` は「再利用する slot 自身」のみ bounded wait（5s×3 retry、失敗時は従来通り stall+quarantine）、他 slot は poll のみ。
- **提出順序 watermark** (`submitSequence`/`completedSubmitSequence`) が drain を置換: `celtium$pollCompletedSlots()`(vkGetFenceStatus 非ブロック)→`celtium$noteSlotConfirmed(slot)`→`celtium$onSubmissionConfirmed()`（frameArena.completed + retired group 解放 + 完全排水時のみ RetireQueue.flush）。
- **`VulkanFrameArena`**: `completed(epoch, confirmedSubmitSequence)` + slot 毎 `submitSeq` — 部分確認で若い slot を誤って reclaimable にしない（use-after-free 防止の要）。
- **`ImmediateGroup.retireSequence`** — submit 時退場は当該 `submitSequence`、未提出は 0。`releaseRetiredImmediateGroups` は `retireSequence <= completedSubmitSequence` のみ解放。
- **`VulkanContext` deferred submit 機構**: `DeferredSubmit`{cmd,fence,uploadSlotMask} キュー + pooled fences(cap16)。`celtium$pollDeferredSubmits`/`awaitOldestDeferredSubmit`/`awaitDeferredSubmit`/`settleDeferredSubmits`。`flushUploads` は submitDeferred で**非ブロック**化し、バッチが読んだ ring slot を `uploadSlotMask` で閉鎖→fence 確認時に解放。`writableSlot`/`reserveUploadSlot` は free slot 必須＋圧力時は最古 deferred の有界待ち(`uploadRingPressureWaits`)。
- **`VulkanGlTexture`**: `outstandingUploadFence` を追跡し staging realloc/destroy/delete 前に await。patch/copy/transition は deferred（`endCommands` 維持は readMirror/compareLevelZero の真の readback のみ）。
- **`VulkanSwapchain`**: present submit を `celtium$submitDeferredWithSemaphores` へ（probe 側は診断意味保持のため sync 維持）。null semaphore は旧実装同様に compaction。
- **`waitForIdle`** は「全 pending slot の waitForSlot」へスリム化（旧 tail 単一 wait は slot loop に包含済み）。残る waitForIdle/settleGpu は terrain recovery/discard/teardown/真の readback/region eviction のみ。
- **counter 追加**: `slotPolls/slotPollConfirms/slotWaitConfirms`、`deferredSubmits[outstanding/peak/reclaims/waits/waitMillis/waitWorstMillis/heldAtTeardown]`、`uploadRingPressureWaits`。

不変条件（検証済みで維持）: queue 順序意味で待ちを置換、in-flight 資源は絶対に解放しない、DEVICE_LOST=完了相当、wait timeout/fault は stall+quarantine、borrowed mesh は解放しない、binary semaphore の無消費者 signal 禁止（OPT-08 教訓）、contract(b) の宛先 flush 時解決＋`uploadSkippedDeletedDestinations`。

## 検証（glless leg、jar sha256 `a0c274fb8421fab5` 先頭16、RX 6400 `pci-0000:03:00.0-render`、NOLAVA+keepGui+captureEntities+slowFrameMs=25、validation=on、headless+X11）

| 指標 | baseline g2/g3 | new3 (081491bb7) |
|---|---|---|
| frameFenceWaitCount | 1988 | **0** |
| frameFenceWaitMillis | 40 | 0 |
| bootstrapSubmitWaits | 254 | **145** |
| bootstrapSubmitWaitMillis | 15–20 | 10 |
| uploadFlushMillis | 8 | 6 (非同期化) |
| slotPolls / slotPollConfirms | — | 1802 / 864 |
| slotWaitConfirms / frameSlotReuseWaits | — | 0 / 0 |
| deferredSubmits | — | peak=7 reclaims=251 waits=48 waitMillis=5 heldAtTeardown=0 |
| frameIntervalP50 / P95 / P99 ms | 18.6 / 28.0 / 333 | **15.7 / 22.5 / 326** |
| slowFrameCount | 12 | 12 (spike 帰属は未確定 — 起動系の可能性、証拠なしに断定しない) |
| gpuStalled / errors / VUID | false / 0 / 0 | false / 0 / 0 |
| immediateWindows / submitSequence | 730 / 994 | 784 / 1027 |
| `celtium-validation.png` | — | **baseline と完全一致** |
| presented world/early capture | — | 実質同一（差分は画像内の run 名文字列のみ）|

run 証拠: `.celtium-work/runtime/p-drain-g2|g3`（baseline）、`.celtium-work/runtime/p-drain-new2|new3`（本ブランチ）。`uploadSlotFree` の全 false 初期化バグ（`uploadCopiesBatched=0`→全 fallback）と null-semaphore VUID×2 は new2 で検出→修正済（new3 で `uploadCopiesBatched=315`, `validationMessageCount=0`）。

## 別問題として発見: bridge 経路の deterministic stall

`-Dceltium.vulkan.glless=true` なしの bridge 経路は本機で**最初の world frame で確定的に deadlock** する（3 本の baseline leg が全て frame 8 で `gpuStalled`、submitSequence=1、immediateWindows=0、~5s fence timeout、VUID=0、frameFenceWaitWorst≈5100ms）。主担当の受入 leg は glless で走っておりこの不具合を踏んでいない。wait/done binary semaphore の順序か frame-target 再生成が疑わしい。glless では未再現。**本ブランチの変更とは無関係の既存バグ** — 別 ticket 相当。

## 残り余地 / 次に見るなら

- **この測定場面で記録対象の待ちは 0**（frameFenceWait=0、deferred waits 計 5ms、ring pressure 0、slot reuse 0）。「記録した場面・対象の待ちが 0」の証明であり、全場面・全 GPU 待ち・全最適化余地が尽きた証明ではない。計器未覆域（acquire/present pacing、mod callback、worker 待ち、warm 定常）は未測定のまま。
- p99~326ms の残 spike は**未帰属**。`gameRenderer` 直下の無名残差 ~246ms/frame（ユーザープロファイル、plan.md §0.227）は pass 単位の計器なしには名指しできない。次の必要作業: JFR/GC ログ + FramePassTimings の実時間軸突合、warm window 到達可能な fixture。
- ユーザープロファイル上の最大の単一項目は `gui` self 5,359ms/20s（frame の 26.8%、**スパイクではなく全 frame の定数税**）= OPT-18/18b が実測上の次優先候補。
- `presentReadback=true`（診断）は present ごとに readback `endCommands` を残す — 既定 off でユーザー影響なし。
- `bootstrapSubmitWaits` 残 145 は大半が readback 系 diagnostic。
- `celtium$pollDeferredSubmits` は flushUploads/ring 圧力時のみ発火 — 長期 deferred 滞留なし（peak=7 実測）。
