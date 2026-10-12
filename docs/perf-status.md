# 性能棚卸し (OPT 台帳) — 2026-10-12

根拠は `docs/performance_plan.md` + 実装確認 + leg 実測。「採用」は plan 基準（正しさ保持・測定で採否）に従う。
状態: **実装済/受入済** = 実装があり受入実測が残る、**実装済/未検証** = 動くが性能・画像の受入が足りない、**未実装**、**不採用**。

## 証拠の起点（ユーザープロファイル §0.227/0.345）

- `gameRenderer` スパイク残差 ~246ms/frame は**未帰属**（pass 計器なしでは名指し不能）。JFR/GC ログと FramePassTimings の時間軸突合が必要作業。
- `gui` self 5,359ms/20s = frame の 26.8%（**定数税**、最大の単一項目）。
- chunk 構築系セクションはスパイク内にのみ出現（帰属済だが経路の分解は未済）。
- `getBrightness` 448,707 calls / 255.7ms — 呼出回数が突出 → particle/entity light cache（PR #27 merged・#30 open）で per-frame lookup は tick-epoch cache 化済。

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
| OPT-10 | command/state の merge | **実装済/検証済（`3870c4ae6`）** | dynamic-state 重複 skip: viewport/scissor/depthBias の同値コマンドを tracker sentinel で skip。warm leg: `stateSkipsViewport=2192 Scissor=2192 DepthBias=1390`（~5.8k vkCmd 除去）・`groupViewportSets=0`。post chain 経由の buffer 共有で tracker が古くなる穴は chain 記録後の invalidate で閉塞（lead 修正）。ユーザー A/B: noise 4,255px (0.4617%) > candidate 4,180px (0.4536%) → picture-neutral |
| OPT-11 | 24B compact vertex | **実装済/計測済（fixture）** | entity-free NOLAVA pair (tick=200 同一 tick capture): `arenaUploadedBytes` 24.2MB→18.5MB **-23.8%**・frame p50 16.15→16.18 不変。**画像差は実行間 jitter と区別不能**: 統制(base同士)で masked-diff 495,236px/mean 2.11/>16=16、format pair で 496,202/mean 2.18/>16=93（超過 ~0.008%）。初回比較の 63.8% は overlay テキスト(run dir 埋込) + 全画素の lightmap flicker ノイズで、format 効果ではなかった（目視では両画像同一）。既定 off は opt-in のまま、画質上の阻害は実測なし。切替: `-Dceltium.validation.compactVertexFormat=true` |
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
| OPT-21 | lightmap/sky dependency cache | **一部実装** | texSubImage identical dedup（PR #29 merged: serial bump 回避）済。残: dirty flag 尊重の sky/fog cache |
| OPT-22 | texture decode/mip/atlas worker 化 | **実装・実測（PR #35）** | `generateMipmaps`（resource read+PNG decode+mip pyramid、sprite-local）を bounded daemon pool(<=4) に submit。`stitcher.addSprite` は順序記録のみ→`finishLoading` HEAD で順次 await+stitch（sequential・順序保持・FML tracking は render thread）。custom-loader sprite は replicated body で sync。`-Dceltium.parallelAtlas=false` で kill。Leg: submitted=817 awaited=817 awaitMillis=0 window=470ms busy=92ms・clean。効果はこの instance で ~92ms — pack sprite 数に比例。**発見: json client list 非空が CleanMix の mixin 適用を阻害する**（plugin getMixins 発見済でも apply されない）— mixin 追加時は client list に書かないこと |

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
| OPT-33 | glless frame semaphore pair の撤去 | **実装済/検証済（PR #4）** | glless の wait/done pair は同一 queue 自己同期の儀式（bridge の GL↔Vulkan 橋渡し由来）。stage 毎 beginFrame signal-only + work + endFrame consume = 3 submits → 1 submit。同一 jar A/B: `queueSubmitCalls` 1304/136F = **9.6 submits/frame** vs pair 復活 3225/173F = **18.6** → ~9 packet/frame 除去。VUID=0・画像 gate 正常（fixture 未較正注記）。`queueSubmitCalls` 計器追加（全 vkQueueSubmit 経路）。opt-out: `-Dceltium.vulkan.gllessFrameSemaphores=true` |
| OPT-34 | texture 変更検出の serial 化 | **実装済/検証済（PR #5）** | `uploadFromGlTexture` が unchanged 判定に全画素 FNV hash を毎 call 実行 → light-map 経路で warm-JFR top frame 9.8%。stub の filed write 毎に serial 採番（`TexCapture.serial`）→ 比較 O(1)・pixel 読取自体も skip。1ms-JFR 同一脚: **9.8%→2.1%** top-frame、submitImmediateBatchTimed 11.6%→4.4%。bridge は write log が無いので hash 維持 |
| — | presented-dump PNG 税の帰属 | **確定（計器税）** | held leg の p99≈318-390 spike は `dumpPresentedImage` の定期 PNG 書込み（present %40、render-thread 上）だった。`presentedDumpEvery=0`（本ブランチの flag）で capture dump のみ残した脚は warm p50=16.3 / p95=18.5 / **p99=21.2**・warm slow frame ゼロ。両 flag とも既定 OFF → production 影響なし。**過去 leg の warm p99 は計器税を含む** |
| — | section churn fixture | **実装済（本ブランチ）** | `churnInterval=N` で in-view ブロックを周期性 dirt↔stone → 19 toggles/leg。単一 section rebuild は chunkUpload p50=0・worst ~9ms = この fixture 規模では安い |
| — | upload ring slots 4→8 | **実装済（`534a34066`・perf-ring）** | `uploadRingPressureWaits` 66→7 — ring 拡張で GPU 待ち解消。本 ledger に欠落していた着陸項目 |
| — | lazy-supply retry の連続失敗 | **実装済/検証済（PR #8 merged `8c35b4306`）** | `submitImmediateBatch` が full-content 未所持 texture の mirror supply を bind 毎に retry → glTexture=43(未書込の game FBO) が ~3×/frame 失敗 = 1183 失敗/leg・warm submit の 68%。write serial で skip → `lazySupply 0/1183→0/2`・**frameIntervalP95 49.7→18.7ms・p99 62.8→21.7** |
| — | dispatch 二重確保 + bind-log 洪水 | **実装済/検証済（PR #9 merged `bf086019d`）** | `Boolean.getBoolean("celtium.glstub.trace")` 毎呼出 → static TRACE; legacy path が long[12]×2 確保 → `dispatchArgs` で 1 回; `bindTexture` id 40-60 の無条件 println 3402行/leg → `UNIT_RANGE_LOG_LIMIT=40` budget |
| — | fixture vsync 押下 | **実装済（PR #10 merged）** | GameSettings.enableVsync の field 書込みは backend に届かない → ValidationClient が `VulkanSwapchain.setVSyncRequested` + `Display.setVSyncEnabled` を reflection で両方 push。真の unbounded 定常: **p50=6.91/p95=10.1/p99=15.8ms (RX 6400)** |
| — | entity gather の per-tick cache | **実装済（PR #11 merged）** | `getLoadedEntityList` が全 chunk の entity map + shouldRenderInPass を frame 毎（~2%@145fps）→ (world,totalWorldTime) キーで per-tick cache。mixin の前置 clear() 削除（tick stamp を潰していた）。leg p-egc2: 画像 masked diff 37px=同一・p50 6.91 維持 |
| — | entity gather の per-tick cache | **merged（PR #11 `6771e90a5`）** | (world,totalWorldTime) キーの cache。leg p-egc2: 画像 masked diff 37px=同一 |
| — | stub primitive maps | **merged（PR #13 `6b2237f6a`）** | boxed-CHM → fastutil open-addressed（~4.6% の alloc+probe 除去）。leg p-prm: p50=7.13・masked diff 9945px=同一 |
| — | unknown-symbol mojibake | **merged（PR #14 `3f1a9a410`）** | F3 "Unmodelled GL entry points" 行が壊れた symbol 名（mod の pointer/未終端 buffer 入力）を verbatim 描画 → sanitizeSymbolName で \xNN エスケープ。unicodeFont fixture knob（`setUnicodeFlag` 経路）も同梱 |
| — | char-index cache + unicode fast path | **実装済（PR #15 merged）** | (1) 巨大 font テーブルの per-glyph `String.indexOf`（~7%）→ interned 定数キーの direct-index map（範囲外は実 indexOf fallback）。func_181559_a/func_78255_a/func_78263_a の 3 redirect。(2) `unicodeFlag`（ja_jp・forceUnicode）で全文字 renderUnicodeChar 経由・fast path 全死（p-fu: 880K unicode windows・**p50=71.5ms**）→ func_78277_a intercept・page-0 glyph を unicode ジオメトリ通り buffer。**ASCII F3 43→10.4ms・unicode F3 71.5→10.3ms・no-F3 6.9ms との差 ~1.5×** |
| — | glyph per-string batching（F3 軽減） | **merged（PR #12 `783102ad9`）** | 1 quad/glyph の submit 経路（~2µs/glyph・F3 ~15K quad=~30ms）→ capture 層で string 単位 buffer・1 append/segment。順序: 色変化で flush・unicode/debug は per-glyph・intervening draw は backend hook で capture flush。`MODE_CENSUS` int[16]・VERTEX memPut 化・copyVerticesTransformed identity fast path（10.5%）も同居。F3 leg: **p50 43.1→13.8ms・p95 136→28・p99 159→38**。初期版は hook 再帰で timeout → open flag を append 前に閉じて解消。Clean-room 実装 |

## 累積効果（fixture・NOLAVA flat・RX 6400・unbounded で測れる範囲のみ）

| 指標 | 開始時 | 現在 | 倍率 |
|---|---|---|---|
| frame render p50（backend 時間軸） | ~9-11ms | **3.5ms** | **~2.5-3×** |
| frame interval p50（frame-to-frame） | ~16.3-16.7ms（vsync floor 込み） | **6.9-7.1ms** | **~2.4×**（≈60→~145fps 換算） |
| F3 overlay on の frame interval | 43.1ms（unicode は 71.5ms） | **10.4ms**（unicode も 10.3ms） | **~4-7×** → no-F3 との差 ~1.5× |
| GUI bracket p50 | ~2-4ms | **0.2ms** | — |
| submit 当たりの queue packet | 18.6/frame | 9.6/frame | ~2× 減 |

積算の内訳: lazy-supply retry 除去（p95 49.7→18.7）・semaphore pair 撤去（18.6→9.6 submits/frame）・pre-transform merge・texture serial・dispatch/CHM・font string batch（F3 時）・entity tick cache。**実ワールドは fixture より地形・entity・mod が多いので倍率は下がる** — ユーザー実機での計測が残る（fixture は flat 60-64 sections のみ）。

| — | char-index cache + unicode fast path | **実装済（PR #15 merged）** | (1) 巨大 font テーブルの per-glyph `String.indexOf`（~7%）→ interned 定数キーの direct-index map（範囲外は実 indexOf fallback）。func_181559_a/func_78255_a/func_78263_a の 3 redirect。(2) `unicodeFlag`（ja_jp・forceUnicode）で全文字 renderUnicodeChar 経由・fast path 全死（p-fu: 880K unicode windows・**p50=71.5ms**）→ func_78277_a intercept・page-0 glyph を unicode ジオメトリ通り buffer。**ASCII F3 43→10.4ms・unicode F3 71.5→10.3ms・no-F3 6.9ms との差 ~1.5×** |
| OPT-14 | item mesh cache (Angelica PR1034 port) | **実装済（PR #21 merged）** | `RenderItem.func_191967_a` HEAD で (model,color) 毎に segment plan（bytes + shade/light flags）を cache — miss は同一 emit+d raw で bytes 保存、hit は cached ints を同 path で replay（lastBrightness は live read）。tint quads/light-carrying format は除外。fixture では item render ほぼ無く効果未測定（NOLAVA=0 calls、実 save=1 call）— benefit は inventory/drop/item-frame 密集場面のみ |
| — | index list cache | **実装済（PR #25 merged）** | `expandIndices` が batch 毎に int[] alloc（~300/frame、GC churn の一因）→ (mode,count) key の LRU 512 cache。consumers は read-only のため安全。census `indexCache=hits/misses/entries` |
| — | particle basis/light cache | **実装済（PR #27 merged）** | Flerovium 移植: `renderParticle` の per-particle Vec3d×4 + basis 計算を frame 共通化（angle==0 のみ、rotating は元経路）・`getBrightnessForRender` の BlockPos alloc + getCombinedLight を tick 単位 cache。実 save leg: particle pass 正常・clean・p50 4.27/p95 6.2 |
| — | item mesh audit fix | **実装済（PR #28 merged）** | B01: `finishDrawing` なしで `draw(scratch)` → `bytes()` が capacity 全体を保存（CPU witness: 旧 131072 ints → 新 168 ints for 24v cube）。B02: replay に `renderLitItem` の hasLighting cleanup がなく hit で lighting=false が漏れた → Plan.hasLighting + finally cleanup（normal+exception 両方、javap 確認済）。B14: negative/tinted cache が無制限 + reload なし → 4 構造全て LRU 256 + ResourceManager epoch invalidation + hit 毎の probe（null-face size + first-quad identity）で mutation 検出・4連続で uncacheable 降格 |
| — | VulkanMod 照合 | **評価済** | 主要技法の我々側対応: UberBuffer/VirtualBuffer = **frame arena 実装済**（OPT-05、group 毎 suballoc・per-batch VkBuffer/map なし）・combined renderlayer = **group state merge 済**（~160 group/frame）・clear-skip = **pendingClears OR-accumulation で coalesce 済**（連続 glClear → 1 vkCmdClear、draw 間 clear は意味的に必要）。残差は indirect draw（~160 draws/frame の cmd record 節約、µs 級で限界）→ 優先度低として inventory に留める |
| — | format census | **計測済** | 実 save leg `drawnByFormat`: **POSITION_TEX_COLOR=106K**（text+GUI quads ~60/frame）・ITEM=83K・他は少数。text batching（ImmediatelyFast 流）は order 依存で risk 高 |
| — | identical texSubImage dedup | **実装済（PR #29 merged）** | OPT-21 第一歩: patch が surface row と byte 完全一致なら serial bump なし（GPU re-supply 回避）・`gamePixels` guard で undefined surface 初 patch は必ず landing。実 leg: `identicalPatches=17/4206` — lightmap はほぼ毎回変化するので効果は小さいが honest |
| — | entity light cache | **実装済（PR #30 open、B19 epoch 強化込み）** | `Entity.getBrightnessForRender`/`getBrightness` も per-frame `getCombinedLight`/`getLightBrightness` → 共有 `CeltiumLightCache`（tick epoch・packed-pos key・1024 LRU）へ。particle 側 cache も helper へ移行。leg clean・injection error なし |
| — | Angelica 残移植棚卸 | **評価済** | TESR translucency sorting（#1384, region 反復 + sort）= backend 領域 → main 解体中のため保留。`efbca29` block-item renderer cache = 1.12.2 では全 block baked model → `func_191967_a` cache で cover 済。item VBO 化（PR1034）・item render dist（#653）= PR #21/#23 済 |
| — | font flush 部分拒否 (B16) | **実装済（PR #31 open）** | `flushBufferedQuads` fallback が OR で全件 accepted 化 → per-quad submit 結果を各カウント・`refusedQuads` 新設・`flush-quad-refused` reason 記録。全受理/部分受理/拒否が識別可能に |
| — | fast block dispatch の vanilla hook 復元 (B23) | **実装済（PR #32 open）** | `VintageBlockRenderer` が `blockModelShapes.getModelForState` 直呼び → `dispatcher.getModelForState` 経由へ（mod hook 復元）+ `getActualState` に vanilla 同様の try/catch（Exception→un-extended state 維持）。fast path 既定 ON・leg clean |
| — | Celeritas GL vs Celtium Vulkan A/B | **実測（同一 jar base・同 fixture・RX6400）** | upstream 2.5.12 @989ebf2e GL leg: warm p50 **0.284ms**/p95 0.78/p99 2.81・worldFrames 8501。celtium Vulkan（fac98e6ac+fog guard）: warm p50 **1.41ms**/p95 4.50/p99 6.10・worldFrames 1149 → **GL が warm で約 5× 速い**（fixture は CPU-bound で per-call 税のみ測る worst case）。GL leg は capture 後 ~49s 自己終了 vs Vulkan 300s — warm window の長さは異なる。発見①: GL leg で `updateFogColor`→`getSkyColorBody(null)` NPE 再現 crash → fog-null-guard 修復。②: main HEAD `eac3a96a6` 以降 build 不通（ImmediateStatistics.perFormat 未移行）— latest compilable=fac98e6ac |
| — | GL vs Vulkan gap 分解 | **帰属済（訂正）** | **`-Dceltium.vulkan.presentReadback=true` は present 毎の GPU→CPU readback で +0.48ms/frame 自損していた** — presentPerCallMicros 608→122µs（無 readback leg）。steady-state frame p50=0.9ms vs GL 0.28ms → gap ≈0.62ms/frame = world/flush ~0.5ms + present 0.12ms + residual 0.1ms。offer/batch は ~1µs/batch で既にタイト。**過去の readback 付き leg の p50 は約 +1ms 過大評価**。残 gap の大半 = offer+flush+submit の aggregate overhead（~280 groups/frame × ~1µs + 固定 submit）→ submit aggregation 候補 |

| OPT-40 | present blit pre-record | **実測有効** | `-Dceltium.vulkan.presentReadback` 除去で判明した residual。present blit（4 transition+1:1 flip）は毎 frame 記録+deferred submit だった → swapchain image 毎に SIMULTANEOUS CB を (sourceImage,sourceLayout,sourceLayoutAfter,w,h,swapchain) key で record once。key 変更で全再録・pool 破棄。vkQueueSubmit 直叩き（fence 不要 — CB は pool 寿命）。RX6400 leg: preRecordedBlitRecords=1/2399 presents・failures=0・presentPerCallMicros 122→89µs（-27%）→ **PR #34** |
| — | **System.gc() Full GC 抑止** | **実測有効（PR #36）** | JFR+gc.log で p99 帰属: 3×`Pause Full (System.gc())` 103/95/88ms（計286ms）が world join 経路で発生 — launchIntegratedServer/freeMemory×2/loadWorld-end が原因。call site を @Redirect で抑止（カウンタ+site 報告・`-Dceltium.explicitGc=true` で復活・MemoryFix 判例）。Leg: Full (System.gc()) 3→0・max pause 103→27.7ms・clean。残 young evac 20-48ms は alloc rate 起因 → dedup/alloc 系 item へ |
| — | **World-move（dimension switch）A/B** | **実測・敗北** | `dimensionAtTick=100 dimension=-1`（nether 転移）・同 fixture: **Celeritas GL: p50=2.84 p95=18.8 p99=154.5 slow=12** vs **Celtium Vulkan: p50=9.35 p95=139.2 p99=289.3 slow=12**。Full GC は 0（抑止済・最大 pause 53ms）にも関わらず負け — 残 spike = chunk rebuild+upload（nether 全 section 再 mesh 化）で backend 支配的。worldFrames 254 vs 1145。world-move 指標は現状最大の敗北 — chunk build throttle/pipelining が次候補 |
| — | GC/p99 freeze 帰属 | **計測済** | submit tail の 99% は外部 pause（`tailCarried neither`）。GC ログ: **G1@2g = 47 pauses/729ms/max 100.2ms freeze**。3-way 比較（同 save・RX6400）: G1@6g = 37/680ms/max 98.6ms・p50 **4.22**/p95 6.6/p99 8.57（最良 throughput）・ZGC = **61 pauses 合計 0.6ms/max 0.06ms（freeze 消滅）**だが p50 7.7/p95 17.5 と throughput 大幅犠牲（load barrier + concurrent workers）。結論: heap は pause 時間を減らさない（live-set 依存）→ 本当の対策は **live-set 削減（dedup）**、これが VintageFix/FoamFix の価値を裏付ける |
| — | submit cost attribution | **計器済** | `immediateSubmitWorldAvgNs`（world baseline）+ `submitBuckets`：steady ~15µs/call（5-50µs bucket = 137K calls ≈ 2s）、**tail: 2,119 calls >200µs = 1.77s** — spike は flush-carrying calls + lazy texture uploads + GC。Avg 48µs は tail に引っ張られた値。submit の本当の問題は steady overhead ではなく **spike 帰属** — p99 と直結 |
| — | hot-path property reads | **実装済（PR #24 merged）** | `refuseImmediateFormat` war getInteger pro offer（~300/frame）+ 6 getBoolean/getInteger pro frame（handoffTimings/refuseTerrainPass/gameClearLatch/presentedDumpEvery/chainFrameDump(s)）→ static finals |
| OPT-15a | dropped item range 動的縮退 (Angelica PR653 port) | **実装済（PR #23 merged）** | `Entity.func_70112_a` HEAD + instanceof EntityItem — vanilla ~16blocks（box×64）→ base 32blocks、tick 当り 256 item 超で sqrt(cap/count) 縮退（floor 4）。fixture に drop item 無く効果未測定 |
| — | lazy unrecorded-refusal | **実装済（PR #22 merged）** | offer entry が毎 batch `"unrecorded (format=..)"` を string concat（~300/frame）→ shape を int field に退避し read 時のみ format。alloc pressure 削減、semantics 同一。README に Angelica attribution（LGPL3・PR1034）正式記載 |
| — | group draw 計器 | **測定済** | `drawPendingImmediate` の per-group body = **2.44µs**（`groupDrawMillis/count`）— group close の draw は cost の正体ではない。`immediateFlushes ≈ groupsDrawn ≈ offered`（98%+ = batch 毎に flush）だが draw 自体は軽い。残 ~40µs/call は offer 入口の未計器部（batch marshal・JIT 汚染 avg 47µs）— submit path 本当の hotspot は offer side |
| — | submit cost 分解（計器） | **測定済・aggregation 以外に残 cost なし** | submit 計器 segment 合計 ~334ms vs 総計 ~3.9s — **~90% は group close の実 draw**。`immediateGroupsDrawn/offered` ≈ 96% で batch 毎に group 分断（state 不一致）→ pipeline/descriptor bind が batch 数だけ走る。残削減は真の aggregation のみ（descriptor-set 共通化・~200行・次 round 評価） |
| — | fn census + stub getInteger | **実装済（PR #20 merged）** | `-Dceltium.debug.fnCensus` で trampoline producer を名指し → glGetIntegerv 全 arrival 39%、うち GL_TEXTURE_BINDING_2D 72% は**我々自身の instrument read**（font bind check per string 等）。stub の tracked field を JVM 内直接 read に切替 → glGetIntegerv **-68%・0x8069 -94%**。絵同一 |
| — | upstream Celeritas audit | **完了** | git.taumc.org HEAD (7818b33) clone・file-by-file diff: section graph/occlusion/fog culling・Euclidean ordering・priority-queue meshing・adaptive raster budget・translucency sorting v3・cached multidraw batch・fast block renderer は全て生きてる・bitraster/文字列系は同一。`useRasterOcclusionCulling` 休眠は upstream も同じ。削除されたのは GL device 層のみ（Vulkan 置換の構造差）。**vk 化で失われた最適化は実質ゼロ** |
| — | 16-slot upload ring | **実装済（PR #18 closed（採用：別経路で main 入り済か検証））** | cold world-entry burst で 8×1MiB ring が枯渇 → pressure waits 168/leg。16 slot=16MiB で **waits/earlyFlushes 168→0・deferred waitMillis 445→10**。frame は cold jitter 内不変 — stall source 除去 |
| — | dispatch arg scratch | **実装済（PR #16 merged）** | GL call 毎 `new long[12]`（~4MB/frame garbage）→ owner-claimed field scratch（非 owner thread は fresh alloc・handler は pure Java で再入経路なし）+ Fn 二重解消。同じ脚で clean と back-to-back: **p50 7.99→7.56・p95 20.0→13.0・p99 31.2→18.4**（tail で効く＝GC 圧除去） |
| — | matrix float[16] scratch | **実装済（PR #19 merged）** | multiplyMatrix の result・両 Buffer 版 unpack・backend ortho/frustum/perspective builder の per-call float[16]（~200+/frame）→ instance scratch（消費側は必ず copy）。leg: p50=7.15・絵同一 |
| — | glMatrixMode 冗長 skip | **実装済（PR #17 merged）** | stub `matrixMode` field を権威に GlStateManager wrapper HEAD で identical set を cancel — FFI+unpack+dispatch 全体を回避。mode arrivals **243,684→108,538（55% skip）**。p50 は脚ノイズ内・絵 byte-identical |
| — | 実ワールド fixture（saves ロード） | **実装済（本ブランチ・未 commit）** | runner が instance の `saves/` を leg dir へ copy + `-Dceltium.validation.saveSlot="New World"` で既存 save を `launchIntegratedServer` 起動 → **End（エンドラ戦）で実ワールド JFR**。frame p50=10.5ms。帰属（warm main）: GL trampoline ~15%・submit ~6%・EntityRenderer world pass ~15%（game）・Zip/jimage ~6%（mod scan）・present/readback ~8%（leg instrument）。verdict は uncalibrated scene で FAILED 想定通り |

## 次の優先順（実測根拠順）

1. ~~OPT-18b pre-transform merge~~ — PR #2 (757ec0b4c)。
2. ~~`gameRenderer` 残差 / world-pass 分解~~ — 帰属確定: spike は計器税（上段参照）。真の定常数値（skip ブランチ・cadence-0）: frame p50=6.2/worst=12.5、world=5.4=terrain 1.5+flush 0.8+unnamed ~2.9（game renderWorldPass + Celeritas orchestration・1ms-JFR で diffuse）。
3. ~~OPT-01~~ — cpuSplit flag で既定 off（main）。
4. ~~warm window 到達 fixture~~ — `-Dceltium.validation.holdUntilTick=600`（commit d0b85f804）+ `churnInterval` + `presentedDumpEvery`（d7a1dd4bd）。
5. **submitImmediateBatchTimed 残 ~4.4%（1ms-JFR 比）の分解 — 確定（p-jfr5・main + OPT-33/34/10 統合後）**: submit 配下 646 sample のうち `uploadFromGlTexture` **49.8%**（lightmap の実 upload — serial skip 後の残は変更分＝正当）、vertex copy `copyVertices`+`copyVerticesTransformed`+`memCopy` **~18%**（OPT-11 が直接削減）、`prepareImmediate`/`TextureRegistry.get`/group bookkeeping **~10%**。残は分散。
   - **world-pass「unnamed」の帰属も確定**（同 JFR・renderWorldPass 内 391 sample）: `ParticleManager.func_78874_a` 直下 **22.5%**（particle tessellation — `Particle.func_180434_a` 7.4% + `BufferBuilder` 9.4% + `ParticlePortal` 3.1%）、`RenderGlobal.func_180446_a` entities **15.9%**、`EntityRenderer` camera/hand/rain 系 **~15%**、`func_174970_a` terrain_setup **10.2%**、clouds/sky ~8%、Forge `dispatchRenderLast`/EventBus ~4%、`EntityGatherer.getLoadedEntityList` 4.9%。**ほぼ全て game/Celeritas 側の orchestration** — backend 側の残は無し。
   - **game-Profiler 帰属 route は不採用**（実測で判明）: `mc.profiler.profilingEnabled` は `Minecraft.runFrame` が毎 frame 書き戻し、stack 補正には `profilingSection` が stack 全体を string 連結するため二次コスト（**246ms/frame**）— instrument として不成立（revert 済）。JFR で同等の帰属が取れる。
6. **ユーザ環境での `-Dceltium.vulkan.passSplit` 計測** — spike 内の unnamed ~200ms が game-side renderWorldPass か GC か（fixture はそこまで辿り切れない）。GC は `-Xlog:gc*` で Full (System.gc()) 68-101ms が periodic に出る。
7. **OPT-11 compact vertex の検証 leg** — `useCompactVertexFormat` flag 済み・未検証。vertex copy ~18% share の直接削減候補（24B vs 32B）。画質比較 + upload byte 削減の実測が必要。

## 計器の範囲メモ（今回確認した事実）

- `batchCallMillis`/`prepareImmediateMillis`/`captureStateMillis`/`appendMillis`/`groupCreateMillis`/`stateKeyMillis`/`matrixCopyMillis`/`programQueryMillis`/`regAnnouncedMillis` は全て検証 leg の CELTIUM_VERIFY で読める。
- fixture 上の batchCall=572ms/1343 ≈ 426µs（起動含む）— prepareImmediate が 57%。group merge は fixture 上ほぼ効いていない（1351 groups/1343 calls）。
- `immediateGroupsDrawn` は draw 回数（同一 group の複数 draw を含む）で group 数ではない。
- fast-merge 後: `prepareImmediateCalls`/`prepareImmediateMillis` は slow call のみを数える（意味変化）。per-call 残コストは capture+key+image compare+append。fast-merge 率は fixture 44%。
- jar 名は `0.0.2-alpha-dev`（版番号は main で変更済 — 旧 `2.5.12-celtium.0.1.0-dev.1` を参照するレシピは古い jar を拾う。脚の前にビルド出力を確認すること）。
