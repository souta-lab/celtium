# 性能棚卸し (OPT 台帳) — 2026-10-12

根拠は `docs/performance_plan.md` + 実装確認 + leg 実測。「採用」は plan 基準（正しさ保持・測定で採否）に従う。
状態: **実装済/受入済** = 実装があり受入実測が残る、**実装済/未検証** = 動くが性能・画像の受入が足りない、**未実装**、**不採用**。

## 証拠の起点（ユーザープロファイル §0.227/0.345）

- `gameRenderer` スパイク残差 ~246ms/frame は**未帰属**（pass 計器なしでは名指し不能）。JFR/GC ログと FramePassTimings の時間軸突合が必要作業。
- `gui` self 5,359ms/20s = frame の 26.8%（**定数税**、最大の単一項目）。
- chunk 構築系セクションはスパイク内にのみ出現（帰属済だが経路の分解は未済）。
- `getBrightness` 448,707 calls / 255.7ms — 呼出回数が突出。

## 台帳

| OPT | 項目 | 状態 | 根拠・残件 |
|---|---|---|---|
| OPT-01 | 計器自身のコスト削減 | **実装済（main）** | `threadCpuNanos()` は `celtium.vulkan.cpuSplit=true`（既定 off）で -1 を返す — CPU-time read は既に gate 済。MXBean lookup は static init。残る nanoTime ペアは ~0.3-0.6µs/call で受入計器として残置 |
| OPT-02 | 破損した GL 参照（参照実装比較経路）| 未実装→不採用方向 | GL-less が製品経路。原版比較は固定 vanilla jar で行う運用 |
| OPT-03 | shader データ分離・128B push 統一 | 未実装 | terrain=128B 済・immediate=208B・item=216B。lightParams 等を UBO 側へ |
| OPT-04 | completion epoch/代わりの全 drain | **実装済** | submitSequence/completedSubmitSequence watermark + per-slot confirm（`8b2bcd412`）|
| OPT-05 | immediate frame arena | **実装済** | VulkanFrameArena + seq-gated reclaim（同上）|
| OPT-06 | 不要な barrier 数の削減 | 未実装 | layout transition が各 texture op で emit — 連続 transition の coalesce 候補 |
| OPT-07 | batched texture/mesh copy・CPU待ちなし | **実装済** | deferred submit 化（同上）。残: ring 容量超過時の fallback 直列 copy |
| OPT-08 | frames in flight + present 非同期 | **実装済** | FRAME_SLOTS=4 + deferred present（同上）|
| OPT-09 | native multidraw | 未実装 | `VulkanMultiDrawBatch.upload()` は no-op — per-command `vkCmdDrawIndexed` のまま |
| OPT-10 | command/state の merge | 未実装 | group merge 済だが state key 差で分断（GUI の要素毎 enable 切替）|
| OPT-11 | 24B compact vertex | 実装済/未検証 | `useCompactVertexFormat=false` 既定 — 画質比較と VRAM/bandwidth 計測が必要 |
| OPT-12 | descriptor キャッシュ | 一部実装 | per-pipeline map 済（hits 実測）・driver `VkPipelineCache` 未使用（起動時効果は限定的と予想）|
| OPT-13 | pipeline spec/キャッシュ | 一部実装 | `cache`/`itemCache`/`blockCache`/`particleCache` map。specialization constants 未使用 |
| OPT-14 | entity model の Vulkan 直 draw | 一部実装? | captured model mesh 借用（`stamp.meshBorrowed`）あり — 残: model 再利用の範囲・bone animation |
| OPT-15 | item/GUI item の mesh 再利用 | 一部実装 | model mesh 借用経路あり — per-frame 再 tessellate の実態は未計測 |
| OPT-16 | TESR の native batch | 一部実装 | submitImmediateBatch 経由 — TESR 専用の coalescing は未実装 |
| OPT-17 | particle emitter/instance 再利用 | 一部実装 | particle format は merge 対象だが emitter 再利用は未実装 |
| OPT-18 | font/glyph・layout cache・HUD | **font=実装済** | glyph 16x/quad・文字 −94%。sign/map/layout cache・HUD 束ねは未実装 |
| OPT-18b | GUI/HUD 即時モードの coalesced run | **実装済/受入済（fixture 下限のみ）** | (1) pre-transform merge（normal-free + unlit + unfog + axis-aligned MV → CPU bake で matrix split 解消、group MV=identity） (2) fast-merge skip（material 全一致 → prepareImmediate/registry/group-open 全体 skip；image identity で descriptor 再生成 split を保存） 計器: `preTransformedGroups=567` `preTransformedBatches=879/1333` `fastMergedBatches=629/1424` `matrixSplits`。calibrated 画像 bit 一致・VUID=0。fixture は同 texture run が薄く win の下限のみ — 実 HUD 効果は未計測 |
| OPT-19 | entity/TESR visibility | 未実装 | Celeritas existing visibility が first level — render bounding box 例外 |
| OPT-20 | sprite visibility・upload 抑制 | 未実装 | active marking 監査から。hidden sprite の upload skip（interpolation 保ち）|
| OPT-21 | lightmap/sky dependency cache | 未実装 | dirty flag 尊重の dedup から |
| OPT-22 | texture decode/mip/atlas worker 化 | 未実装 | bounded worker pool — startup/reload 効果候補 |
| OPT-23 | model/quad/state dedup | 未実装 | immutable quad vertex data の共有 |
| OPT-24 | 描画系 task の budget | 未実装 | mesh/sort/upload の per-frame budget |
| OPT-25 | visibility graph 深堀 | 未実装 | Celeritas visibility の camera epoch/stale 監査 |
| OPT-26 | FBO/post 最適化 | 未実装 | `avoid_redundant_framebuffer_switching` 相当 |
| OPT-27 | barrier/transition 監査 | 未実装 | OPT-06 と同領域 |
| OPT-28 | fragment work 削減 | 未実装 | overdraw/scissor 効果域 — GUI 帯域は大きくない（実測）|
| OPT-29 | recording 並列化 | research | backend は single-thread 設計 — 大きな別仕事（現時点不採用）|
| OPT-30 | GPU culling/dispatch | research | 同左 |
| OPT-31 | mod 受入互換の維持 | 継続 | submitImmediateBatch の Forge API 形状が土台 |
| OPT-32 | index/vertex locality | 候補 | terrain mesh の post-process cache locality |

## 次の優先順（実測根拠順）

1. ~~OPT-18b pre-transform merge~~ — PR #2 (757ec0b4c)。
2. **`gameRenderer` 残差 / world-pass 分解** — JFR leg (p-jfr1): GC pause 合計 332ms/28s・最大 72ms（worst window 140ms/s）→ GC は spike の一部だが単独説明不可。`readBackBlock` が world-phase main サンプルの ~45%（presentReadback 診断税・製品では無効）。fixture では `FramePassTimings` が `world` bracket に spike の ~90% を割当て（worst 368/411ms）、residual ≤31ms — **fixture の spike は world pass 内**。world 内訳を分ける `present`/`flush` INSIDE bracket を追加（本ブランチ・計器）。
3. ~~OPT-01~~ — cpuSplit flag で既定 off（main）。
4. ~~warm window 到達 fixture~~ — `-Dceltium.validation.holdUntilTick=600`（commit d0b85f804）。p-warm2: `worldTicksAtEnd=599`, warm n=280, **warm p50=17.0/p95=76.7/p99=385.3** — chunk-build を過ぎても 300-800ms spike が残存。slow-frame 証拠: `submitWaits=450-737 submitWaitMillis=31-99`（細かい bounded poll の連鎖≈100ms/frame）+ 残 ~300ms が unnamed — step-2 の bracket で分解。
5. **OPT-09 multidraw** or **OPT-10 state merge**（stateKey 差分断の解消は GUI の残半分）。

## 計器の範囲メモ（今回確認した事実）

- `batchCallMillis`/`prepareImmediateMillis`/`captureStateMillis`/`appendMillis`/`groupCreateMillis`/`stateKeyMillis`/`matrixCopyMillis`/`programQueryMillis`/`regAnnouncedMillis` は全て検証 leg の CELTIUM_VERIFY で読める。
- fixture 上の batchCall=572ms/1343 ≈ 426µs（起動含む）— prepareImmediate が 57%。group merge は fixture 上ほぼ効いていない（1351 groups/1343 calls）。
- `immediateGroupsDrawn` は draw 回数（同一 group の複数 draw を含む）で group 数ではない。
- fast-merge 後: `prepareImmediateCalls`/`prepareImmediateMillis` は slow call のみを数える（意味変化）。per-call 残コストは capture+key+image compare+append。fast-merge 率は fixture 44%。
- jar 名は `0.0.2-alpha-dev`（版番号は main で変更済 — 旧 `2.5.12-celtium.0.1.0-dev.1` を参照するレシピは古い jar を拾う。脚の前にビルド出力を確認すること）。
