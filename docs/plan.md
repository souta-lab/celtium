# Celtium 現状表（現在状態・優先順・受入）

> goal: `goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`（revision 48, phase active / activation armed）。
> 全文は `docs/goal.md`。構造修復の方針は **`docs/handoff-structure-recovery-2026-10-10.md`**、
> 直近の原因候補は `docs/hints-2026-10-10-nether.md`。監査起点は `docs/audit-2026-10-09-third.md`。
> **round ごとの経緯と旧測定は `docs/history/plan-round-log-until-2026-10-10.md` に移した**（削除していない）。
> この文書は**現在状態を一箇所で管理する表**であり、末尾への訂正追記で整合させない。

## 0. 直近の到達点（最新実測・2026-10-10）

- **構造修復 1（texture の所有契約）実装済み**（commit `297ce5caa`）。同じ GL texture id に2つの Vulkan image が
  あった分裂を解消し、registry を唯一の所有者にした（`registry.imageFor(id,w,h,filter)` を backend が sample する）。
  世代の消費は既存の `announcedGeneration`/`consumedGeneration` に統合。実測（AMD・溶岩世界・120 s、同一条件の
  変更前 run と比較）: **`readbacksSkipped=473` 対 `directMirrorRefreshes=472`**（ほぼ全更新が readback 不要）、
  **p50 40.41 → 25.49 ms**、`CELTIUM_PATCH_REJECTED=0`、**VUID 0**、クラッシュなし、**PASSED**。
  **未検証**: patch 経路が mip・全画像内容を readback 経路と同等に保つか（`MIRROR_GL_MIPS=false` で level 0 のみ、
  という読みまでは確認）。**reload / GL id 再使用 / sprite 単位 mip は未測定**。
  **画像同一性は未確認**: 比較した run は**アニメーションする溶岩世界**で、`PINNED_WORLD_TIME` は sprite の
  animation 位相を固定しない。meanAbsDiff 6.29 / 400k 画素の差は**位相差か content 差か未切り分け**。
- **capture 段階化**（`bf27f1975`）: tick 等号をやめ、`captureStartTick` を記録し、捕捉できなくても
  `status=NO_CAPTURE` の部分レポートを残す。Intel は**軽い世界 p50 28.47 ms / 溶岩世界 120 s 完走 p50 64.52 ms**、
  いずれも **PASSED**。**MISSING_REPORT の原因は予算ではなく捕捉 tick の等号**だった。
- **dimension 受入は fail-closed**（`8ee7cf97e`）: サーバー処理を server task に移し、client の実到達を観測し、
  捕捉時に `player/world provider == target` を要求する。
  **End は AMD/Intel とも capture 時 dimension 1 で PASSED**（`p247-end`, `p247-end-intel`）。
  **Nether は NO_CAPTURE**（`p247-nether`, `p248-trace`, `p249-early`）: tick 82 で -1 に到達後、
  **`GuiDownloadTerrain` のまま tick 148 で 0 に戻る**。**旧 Nether PASSED は有効な受入から外した**。
  地形ダウンロードの残留と帰還は**観測事実**で、renderer / packet / fixture のどこが根因かは**未確定**。

## 0.13 最終合成・present の現状を測定: **present 経路は既定で配線済みだが、通常フレームでは到達しない**

- 発見: **`celtium.vulkan.presentFromComposite` は既定 true**（`VulkanTerrainGpuBackend` 925 行）。つまり
  **native 合成からの present は既定の経路**であり、§0.12 で追っていた `COMPOSITE_*_REFUSED` は *GL 側合成* の別ルール。
  合成経路のコードには**設計として明記**されている: `final default-FBO composite remains GL-owned, never inferred from
  format id`。
- **測定（AMD、`presentMarker=true`、duration 90 s、exit 0 / clean / PASSED）**:
  `presentSucceeded=false`、**`presentResult=[none]`**、`submitted=6505`／`completed=6505`。
  **`presentImage` が一度も呼ばれていない**（結果文字列が初期値のまま）。理由は実行コードの gate:
  `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0` のとき
  「presenting the frame skipped: the default framebuffer is bound, so there is no frame content to present here」。
- **これが条件 (b)(c) の具体的な不足条件である**: **通常フレームの内容は依然として GL の既定フレームバッファにあり**、
  Vulkan のフレームターゲットは**鏡**であって**源ではない** → **present するものが Vulkan 側に無い**。
  native present の配線・marker・readback・no-record は既にあり、**フレームの生産者が GL である限り到達しない**。
- **次に必要なこと（順序）**: 通常フレームの色・深度の**生産者**を Vulkan 側へ移す（item 6 の本体）。
  そのとき `presentImage` が呼ばれ、`presentResult` と `presentedMean` が意味を持つ。
  **実 window/compositor 出力の到達**はその次の受入であり、**この環境の DISPLAY=:0 に実 compositor が無い**
  （`xdpyinfo` は root 0×0、`grim` は wl_output 無し）ため、**取得不能時の不足条件はこの1行に集約される**。

## 0.12 拒否理由の確定と、前ラウンド推論の**訂正**

- `prepareImmediate` の **3つの null 経路すべてが理由を記録**するようにし、offer 側の粗いメッセージ
  （3原因を1つに潰していた）を**廃止**して理由を1箇所が持つ形にした。実測で理由が出た:

  `lastPrepareFailure=[format=6 texture=23 (no terrain pipeline for this pass)]`、
  `CELTIUM_COMPOSITE_LAYOUT_REFUSED reason=preflight: format=9 texture=25 (no terrain pipeline for this pass)`。

- これを受けて「フレームの world パイプラインへフォールバック」を実装して測ったところ **`preparePassFallbacks=0`**
  ＝ **その時点では `terrainPipelineForFrame` も null** だった。つまり
  **format 8/9 の到着は entity/TESR のモデルではなく、合成（composite）経路自身の描画**であり、拒否は
  **その経路の別ルール**（`CELTIUM_COMPOSITE_LAYOUT_REFUSED`）によるものである。
- **訂正**: §0.11 の「format 8/9 が受理されない → entity/TESR が native 化されない」という推論は、
  **この計器からは支持されない**。entity のモデルは **display list 経由**（§0.10 で既定化済み）で、
  spider 場面の `modelDraws=1000` と画像一致（床0に対し効果20画素）がその受入である。
- **整理**: 発火しなかったフォールバックは**到達不能として撤去**（goal の「不要になった暫定経路を残さない」）。
  **理由を記録する計器は残す**（この問いを決めたのはそれである）。
- **次に残る問い**: **合成経路の format 8/9 バッチ**を受理できるようにするか、その経路のルールとして
  「対象外」と明示するか。**どちらでもない状態（理由が読めない拒否）は解消した**。

## 0.11 TESR 場面の追加と、未知テクスチャへの一度だけの全面供給

- **世界に TESR ブロックを追加**した（既定の層に `minecraft:chest` と `minecraft:enchanting_table`）。TESR は display list では
  なく**即時描画**なので、モデル経路とは別の未検証領域である。受入 run は exit 0 / clean / **PASSED**、VUID 0、
  `modelMeshLists=2`（＝**TESR は display list 経由ではない**ことの確認）。
- **format 8/9（entity/TESR 系）が到着しても受理されない**理由をコードで追った: 単一の発信点は
  `prepareImmediate(...) == null` で、その null は **`textureRegistry.get(boundTexture) == null`**（＝**そのテクスチャの
  ミラーが存在しない**）から来ていた。**entity/TESR は自分のスキンを即時描画で使い、どの upload フックにも見えない**ため、
  ミラーが作られず**永久に受理されない**構造だった。
- **修正**: **未知のテクスチャに一度だけ全面供給**する（`imageFor` で実体を作り、`uploadFromGlTexture` で1回読み戻し、
  成功後に `markFullyInitialized`）。guard は「まだミラーが無い」なので **GL id ごとに1回**。
- **実測（AMD、軽い世界＋TESR ブロック、120 s）**: exit 0 / clean / **PASSED**、277 frames、p50 23.17 ms、**VUID 0**、
  **`lazyMirrorSupplies=1`・`lazyMirrorFailures=0`**、`submitted=6811`／`completed=6810`。
- **まだ言えないこと（重要）**: **format 8/9 がこの run で受理されたかは未確認**である。`acceptedByFormat` は `9:0` だが、
  **この run に format 9 の到着が何件あったかを確認していない**（到着0なら 0 受理は自明）。**「entity/TESR が native に
  なった」とは言わない**。次の1 runで **到着数と受理数を同時に**読む。
- **計器の置き場所についての教訓（3回繰り返した）**: 新しい counter を `diagnostics()` や要約の途中に足すと
  **レポートにもログにも現れない**。**`CELTIUM_GROUPS`／`CELTIUM_ANIM` のように専用行として出す**のが確実である。

## 0.10 モデル native 描画を**既定**にした（受入一式で回帰なし）

- 既定化: `-Dceltium.vulkan.modelDraw` / `modelMeshBuild` を**既定 true**（`=false` で従来挙動に戻せる）。
- **受入一式（AMD 8場面＋Intel 2場面、軽い世界、duration 120-150 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | 拒否 | `cancelled` | VUID |
|---|---|---|---|---|---|---|---|---|---|
| AMD 通常 | 0 | true | **PASSED** | 562 | 16.08 ms | 1038 | 0 | 1038 | 0 |
| AMD spider | 0 | true | **PASSED** | 544 | 16.67 ms | 1000 | 0 | 1000 | 0 |
| AMD 三人称＋GUI | 0 | true | **PASSED** | 560 | 16.25 ms | 1400 | 0 | 1400 | 0 |
| AMD postprocess | 0 | true | **PASSED** | 563 | 16.17 ms | 1040 | 0 | 1040 | 0 |
| AMD weather | 0 | true | **PASSED** | 562 | 16.20 ms | 1040 | 0 | 1040 | 0 |
| AMD reload | 0 | true | **PASSED** | 555 | 16.17 ms | 526 | 0 | 1022 | 0 |
| AMD resize | 0 | true | **PASSED** | 565 | 16.21 ms | 1046 | 0 | 1046 | 0 |
| AMD world switch | 0 | true | **PASSED** | 554 | 16.14 ms | 524 | 0 | 1016 | 0 |
| Intel 通常 | 0 | true | **PASSED** | 320 | 29.34 ms | 556 | 0 | 556 | 0 |
| Intel 三人称＋GUI | 0 | true | **PASSED** | 313 | 28.50 ms | 822 | 0 | 822 | 0 |

  **全 run PASSED・VUID 0・クラッシュなし**。p50 は既定化前（AMD 16.02〜16.24 / Intel 28.5〜29.3）と同等で**性能の退行なし**。
- **画像**: spider は**20 画素**（フラグON時と同一）、三人称は 2407 画素＝**既定自身の床（1983）水準**。
- **計器の寿命差を修正**: `reload`／`worldSwitch` で `cancelled`（1022/1016）が `modelDraws`（526/524）を上回った。
  原因は **census が static で backend がインスタンス毎**（reload/world switch でインスタンスが作り直される）。
  `modelDraws` を **static** にして寿命を揃えた（**欠陥ではなく計器の問題**だった）。
- **意味**: **モデル描画（display list 経由の player/mob/item モデル）が既定の経路になった**。完成条件 (a) の
  未達項目から「model/item の native 描画（実験フラグ側）」が外れ、**entity/TESR の全種類・他 scene** が残る。
- **まだ言わないこと**: **全 entity/TESR**・影mod・他 GPU/OS は未検証。**「全vanilla native化」ではない**。

## 0.9 モデル描画の段階受入: Intel と「モデルが多い場面」

- **Intel 受入（三人称のプレイヤーモデル、duration 120 s）**: exit 0 / validationClean / **PASSED**、302 frames、p50 30.71 ms、
  **`modelDraws=750`・拒否 0・`displayListCancelled=750`**、`submitted == completed = 8495`、**VUID 0**、クラッシュなし。
- **モデルが多い場面（spectator spider、AMD、隣接3run）**:

| 比較 | 差>2の画素 | 行 540-624 | 行 625 以降 | 最大 |
|---|---|---|---|---|
| **床（既定 vs 既定2）** | **0** | 0 | 0 | 2 |
| **効果（既定 vs 実験ON）** | **20** | 6 | 14 | 3 |
| 効果（既定2 vs 実験ON） | **21** | 7 | 14 | 4 |

  `modelDraws=1004`・拒否 0・`cancelled=1004`、`submitted=13346`／`completed=13345`（**差1は標本時刻の in-flight**、
  `retired=1` と整合）、`discarded=0`、**VUID 0**、クラッシュなし、全 run **PASSED**。
- **意味**: **床が 0 の場面で効果が 20 画素**（92万画素中、最大差3）＝ **native モデル描画は既定と実質一致**。
  三人称場面では効果が既定自身の床と同水準だった（§0.8）。**2場面・両GPUで、モデル描画の捕獲→メッシュ→
  ウィンドウ内描画の経路が成立している**。
- **言えないこと**: entity/TESR の**全種類**、他 scene、影mod、他 GPU/OS は未検証。**「モデル完成」ではない**。

## 0.8 モデル native 描画の非再現性を修正: 自前 submit をやめ、即時ウィンドウの順序に載せた

**根因（このターンで特定・実装）**: モデル描画は `callList` で **自前のコマンドバッファを開いて即 submit** していた。
即時経路は「ウィンドウに溜めて、次の GL 描画の直前のパスでまとめて描く」ことで**フレーム内の順序**を守っているが、
モデルだけがその順序に**参加していなかった** → 同じ入力でも合成順が安定せず、**下段（モデルの帯）が run 間で再現しなかった**
（前単位の実測: 既定の床 32 画素に対し ON の床 1283 画素）。

- **修正**: モデルメッシュを **即時ウィンドウの group として enqueue** し、`flushImmediateWindow` →
  `drawPendingImmediate` のパス内で、他と同じ順序で描かれるようにした（`celtium$enqueueModelMesh`）。
  **mesh の所有は借用**（`GroupStamp.meshBorrowed`）とし、retire ドレインが**フレーム間で使い回すメッシュを解放しない**
  ようにした（この契約を入れないと2フレーム目で消える）。
- **併せて棄却した仮説**: 束縛テクスチャの run 間差（`CELTIUM_MODEL_TEXTURE` の list→texture 対応表は**両runで完全一致**、
  全リストが texture 24）。
- **実測（AMD、軽い世界、三人称＋GUI、duration 120 s）**:

| 比較 | 差>2の画素 | 行 540-624 | **行 625 以降** | 最大 |
|---|---|---|---|---|
| **床 ON（隣接run、修正後）** | **41** | 37 | **4** | 244 |
| **床 既定（隣接run）** | 1983 | 1953 | **30** | 192 |
| **効果: 既定 vs ON（隣接）** | **1989** | 1959 | **30** | 243 |
| 効果: 既定2 vs ON（隣接） | 2162 | 2162 | **0** | 247 |
| （参考）床 ON（修正前） | 3237 | 1954 | **1283** | 249 |

- **結論**: **ON 同士の床が 3237 → 41 に落ちた**（下段は 1283 → **4**）。さらに
  **ON と既定の差（1989 / 2162）は、既定自身の run 間の床（1983）と同水準**である
  → **この場面・この解像度では、native モデル描画は既定のばらつきを超える差を生んでいない**。
  `modelDraws`（=queue 数）1402 に対し `cancelled` 1402（**成功時のみ取消**）、`refusals=0`、
  `submitted == completed`、`discarded=0`、**VUID 0**、クラッシュなし、**PASSED**。
- **言えること / まだ言わないこと**: **三人称のプレイヤーモデル1場面**で、native 描画が既定のばらつき内に収まった。
  **entity/TESR/item 全体・他 scene・他 GPU は未検証**。「pixel 完全一致」でも「モデル完成」でもない。

## 0.7 モデル native 描画の画像比較: **run 間で再現しない**（前ターンの結論・実験は lab flag のまま）

**測定（AMD、軽い世界、三人称＋GUI、duration 120 s、`captureStartTick=200` は全 run 一致）**:

| 比較 | 差>2の画素 | 行 540-624 | **行 625 以降** | 最大 |
|---|---|---|---|---|
| **床（既定 vs 既定2）** | 1283 | 1251 | **32** | 192 |
| **床（実験ON vs ON2）** | **3237** | 1954 | **1283** | 249 |
| 効果（既定 vs ON） | 3184 | 1900 | 1284 | 214 |
| 効果（既定2 vs ON2） | 1608 | 1577 | **31** | 249 |

- **読み方**: **既定同士では下段（行625以降＝モデルが立っている帯）が 32 画素しか違わない**のに、
  **実験ON同士では同じ帯が 1283 画素違う**。つまり **native モデルの寄与が run 間で再現しない**。
  これは「GL と違う絵を出す」とは別の、より強い問題である: **同じ入力から同じ絵が出ていない**。
- **画像の観察**（`docs/evidence/model-onoff-row560-2026-10-10.png`）: 差は**面状のずれではなく薄い筋**として
  地平線帯（行540-624）と下段に散る。**以前に観測した「既定に無い三角形」を、この測定は再現していない**
  （あの観測は取消の根拠が名前ベースだった頃のもので、対象リストが今とは違う）。
- **棄却した仮説（このターン）**: `VulkanDynamicMesh.nextVertex()`/`currentVertex()` の添字が1ずれている
  （読んで確認: `nextVertex()` は開始した頂点の番号を返し、`put*` はその頂点に書く。**規約は正しい**）。
  また round 559 の「list 2 の行列が 0.6 違う」は、**census が *最初の* replay の行列を1回だけ記録する**のに対し
  描画は毎回現在の行列を問い合わせるためで、**時間差による見かけ**だった（欠陥ではない）。
- **状態**: 実験は **lab flag のまま**（既定は描画0・キャンセル0で不変）。**「native モデルが正しい」とも
  「stray triangle がある」とも、この測定からは言わない**。
- **次の問い（具体）**: **なぜ ON の下段だけが run 間で再現しないのか**。候補は
  (a) 描画時点の行列位相が replay と1フレームずれる、(b) mesh の構築時期が list ごとに違い、
  一部の list が *別の* ポーズで捕獲されている、(c) 深度・ブレンドの状態が frame ごとに変わる。
  **測って決める**（同一 run 内で list ごとの matrix と描画時刻を記録する）。

## 0.6 モデル native 描画の実験を再到達可能にした（この単位・全vanilla native化の入口）

**根因**: `callList` フックが **`cancels(list)` の内側でしか `drawModelList` を呼んでいなかった**。構造修復2で
`cancels()` は「compile が native に受理された（リストが空）」だけを根拠にする形になり、その仮説は**実測で反証**されて
いたため、**モデル native 描画は既定でも実験フラグ時でも一度も走らない**状態だった（`modelDraws=0`）。

- **フックを「フラグ時は必ず描き、成功したときだけキャンセル」に直した**:
  `if (modelDrawEnabled()) { drawn = vulkan.celtium$drawModelList(list, boundTexture); if (drawn) cancel(); }`。
  **名前も counter も判断に入らない**（「別経路が実際に描いた」だけが根拠）。
- **反証済みで死んでいた経路を撤去**: `COMPILED_NATIVELY` 一式（`compiledNatively` / `wasCompiledNatively` /
  `nativelyCompiledLists` とアップローダの呼び出し）、`cancels()`、id リスト `CANCEL` / `cancelSet()`、
  要約の `displayListCompiledNatively`。**判断の持ち主は1つ**になった。
- **実測（AMD、軽い世界、duration 120 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | 拒否 | `displayListCancelled` | `modelMeshLists` | VUID |
|---|---|---|---|---|---|---|---|---|---|---|
| **既定（フラグOFF）** | **0** | **true** | **PASSED** | 559 | 16.21 ms | **0** | 0 | **0** | — | **0** |
| **実験（`modelDraw`＋`modelMeshBuild`、三人称）** | **0** | **true** | **PASSED** | 559 | 16.25 ms | **1396** | **0** | **1396** | **12** | **0** |

  **`displayListCancelled == modelDraws`（1396 = 1396）** ＝ **成功した描画だけをキャンセル**している。
  **既定は完全不変**（描画0・キャンセル0・p50 16.21 対 16.25）。クラッシュなし・VUID 0。
- **検証として言えること**: 実験が再び到達可能で、取消の根拠が「実際に描いた」ことだけになっている。
  **まだ言えないこと**: **native モデルの見た目が正しいか**（画像比較は**アニメーションを統制した場面**で行う）。
  entity/TESR/item 全体の native 化は**未完**。

## 0.5 構造修復 3（前半・この単位）: draw の段階を target 世代に紐付ける

**根因**: 5段階（queued / recorded / submitted / completed / retired）は別リストと counter で管理され、
`VulkanCommandBufferState` も正しい状態機械（`INITIAL→RECORDING→RENDER_PASS→EXECUTABLE→PENDING`・target 保持・
`requireDraw(target)`）を持つが、**group 自身が「どのフレーム・どの frame target 世代のものか」を記録していなかった**。
frame target が作り直された後（resize / teardown）に古い group が残っていると、**もう存在しない render pass を束ねて
描く**余地があった。

- **`GroupStamp`（immutable: `frameId`, `targetGeneration`）を group に付与**（tuple の index 14）。
  **texture 世代は入れない** — 描画対象の頂点は texture 再アップロードで無効にならず、落とせば**描画が消える**ため。
  無効条件は **target 同一性だけ**である、と明示した。
- **`targetGeneration` は frame target を破棄した2箇所**（再生成経路と teardown 経路）で進む。
- **draw 時のゲート**: 世代が一致しない group は**描かずに退役**させ（mesh は次の完了ドレインで解放＝未 submit なので安全）、
  `groupsStaleTargetGeneration` として計数する。
- **段階の可視化**: queued/recorded/retired の実数、`targetGeneration`、stale 件数、submitted/completed/discarded を
  **`CELTIUM_GROUPS` 行**として stdout に出す（`CELTIUM_ANIM` と同じ場所＝確実に残る場所）。
- **実測（AMD resize fixture／Intel 通常。軽い世界）**:

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ |
|---|---|---|---|---|---|---|---|
| **AMD + `resize=true`** | **0** | **true** | **PASSED** | 544〜568 | 16.1〜16.2 ms | **0** | なし |
| **Intel** | **0** | **true** | **PASSED** | 320 | 27.41 ms | **0** | なし |

  **段階の整合不変条件（レポートに出ている数値）**: **`immediateSubmittedBatches == immediateCompletedBatches`**
  （1557=1557 / 1749=1749 / 4243=4243）。**submit されて完了しなかった group は無い**。
- **正直な未検証**: 新設の `CELTIUM_GROUPS`（`targetGeneration`・`groupsStaleTargetGeneration` 等）は
  **まだ数値を読み出していない**。要約メソッド内の別エントリに置いた版がレポートに現れなかったため、
  **確実に出る `CELTIUM_ANIM` と同じ場所へ専用行として移した**ところまでが今回の実装で、
  **数値の確認は次の1 runで行う**（`staleTargetGeneration` が 0 である前提は置かない）。

## 0.4 構造修復 3（後半・この単位）: fixture の結果と JSON 所有を snapshot に統一

**根因**: サーバータスクが**3つの volatile フィールド**（`transferResult` / `transferAtTick` / `rotationBefore`）を書き、
クライアントが別々に読む＝半端な結果を読む余地があり、**shutdown フックは生きた `mc.player` / `mc.world` /
`mc.currentScreen` を任意スレッドから読んでいた**（goal が禁じている形）。

- **`TransferSnapshot`（immutable・final フィールド）**: `state` / `completedAtTick` / `rotationBefore`。
  サーバータスクは**完成した1つの object を1回 publish** するだけ。クライアントはそれだけを読む。
- **`ProgressSnapshot`（immutable）**: `ticks` / `frames` / `captureStarted` / `captureStartTick` /
  client・world の dimension / screen 名 / **出力パス**。**render スレッドで毎フレーム取得**し、1参照で publish。
- **shutdown フックは snapshot だけを読む**（`mc` には一切触れない）。ファイルパスも snapshot が持つ。
  捕捉時のレポート書き込みも snapshot 経由に統一。
- **実測（3経路・両GPU）**:

| run | harness | レポート |
|---|---|---|
| AMD（軽い世界、150 s） | exit 0 / clean / **PASSED** | `captureStartTick=200`、`worldFrames=545` |
| **AMD（End、`dimensionAtTick=80`）** | exit 0 / clean / **PASSED** | **`dimensionServerResult=transferred`**、`dimensionArrivedAtTick=81`、**`dimensionClientAtCapture=1`**（転送 snapshot と fail-closed が有効） |
| **Intel**（150 s） | exit 0 / clean / **PASSED** | `captureStartTick=200`、`worldFrames=324` |
| **Intel（強制 NO_CAPTURE、25 s・溶岩世界）** | 124（ハーネス上限）/ clean / **NO_CAPTURE** | **`status=NO_CAPTURE`、`worldTicksAtExit=161`、`worldFramesAtExit=92`、`captureStarted=False`、`clientDimensionAtExit=0`、`screenAtExit=none`** — **フックが snapshot だけで書いた** |

  すべて **VUID 0・クラッシュなし**。
- **消した重複**: volatile フィールド3つ（結果の分割所有）、shutdown スレッドからのゲーム状態参照。
- **残り（構造修復3の前半）**: draw 側の **queued→recorded→submitted→completed→retired を target/frame/generation に
  紐付ける**こと（preflight/retry/失敗時保持・clear/depth/sample 順序の確認を含む）。**未着手**。

## 0.3 構造修復 1 の完成（この単位）: lifecycle と未初期化 patch と実コスト

**同じ根因（texture の実体・更新・寿命）に対する残りを、producer/consumer/invalidator/lifecycle まとめて実装し、
契約が揃ってから小GPU受入した**（goal「実装・検証の進め方」の単位）。

- **A. 未初期化 image への partial patch を拒否**（registry）: 全面が一度も入っていない image に部分 patch を書くと、
   patch 外が未定義のまま「更新済み」と記録してしまう。`announceFullPixels` は**部分 patch を拒否して計数**し、
   その texture は**readback で全面が入るまで patch を受けない**（描画側の `hasFullContent` 拒否と整合）。
- **B. lifecycle（reload / 再stitch / ID 再使用）**: 脆弱な `deleteTexture` への inject は**署名不一致で mixin 適用に失敗し
  両GPUが起動クラッシュした**ため撤去し、**既に発火が実証されている full-upload フック**（`func_110988_a`＝id の
   内容が確保し直される場所）を合図にした。`onGlTextureReallocated(id)` が **registry の entry を忘れ**、
   古い image を **`VulkanRetireQueue` で退役**させ（記録済みフレームが参照し得るため即破棄しない）、
   atlas/lightmap の直接参照を落として**次のフレームで新しい image から再確立**する。
- **C. 実コスト計測**: patch / full image の**回数・バイト数・適用 nanos**、`beforeInit`（拒否数）、`forgotten` を
   registry が出し、`CELTIUM_ANIM` 行と要約に載せる（upload/submit/wait は `VulkanGlTexture` が 1 操作ごとに
   `beginCommands`/`endCommands` で submit+wait するので、この nanos がその実費である）。
- **実測（AMD は reload fixture でこの経路を実際に通す、Intel は同段階の受入。軽い世界、duration 150 s）**:

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ |
|---|---|---|---|---|---|---|---|
| **AMD + `reload=true`** | **0** | **true** | **PASSED** | 534 | 16.33 ms | **0** | なし |
| **Intel** | **0** | **true** | **PASSED** | 321 | 27.56 ms | **0** | なし |

  **不変条件の実測**（壊れれば 0 のままになる数値）:
  **再確保 385（AMD）/ 503（Intel）**、**ライトマップ再確立 96 / 175**、
  **patch 144 件・147456 B・70 ms（AMD）/ 267 件・273408 B・313 ms（Intel）**、
  **`beforeInit=290 / 341`**（新設の拒否が実際に効いている）、`full=0〜2 件`。

- **この単位で消した重複・例外**: backend 側の第2の `VulkanGlTexture.create`（二重実体）、名前ベースの取消分岐、
  到達不能だった interop 絶対 tick 待ち、registry 生成7箇所の散在（前単位まで）。**死んだ lab flag は残していない**。
- **検証として言えること / 言えないこと**: lifecycle と拒否の**不変条件は数値で動いている**。
  **画像同一性はまだ言わない**（比較は非アニメーション統制の場面で行う）。mip は patch が level 0 のみを書き、
  patch 外を保持する（full upload 側が全 level＋chain を書く）ことをコードで確認した。

## 0.2 構造修復 2（着手）: 取消の根拠を「記録された事実」に限定した

- **goal の要求**: 取消は「対応する別経路が同じ内容を引き受けた/描いた」根拠を必要とし、**名前・counter だけで
  描画を消さない**。従来の `cancels()` は **呼び出し元の名前**（`ItemRenderer` を含むか）で決めていた ＝ 名前ベース。
- **名前ベースの判断を撤去した**。新しい判断は「**native のモデル描画が実際にそのリストを描いた**」か
  「**そのリストのコンパイルが native に受理され、GL 側が空である**」のいずれかで、後者は
  **受理が起きた場所（アップローダ）で記録**する（`CELTIUM_LIST_COMPILED_NATIVELY`）。
- **実測（AMD、軽い世界、duration 120 s）**: `CELTIUM_LIST_COMPILED_NATIVELY` は **0 件**、
  `displayListCancelled=0`、`displayListReplaysOpen=1542`、exit 0 / clean / **PASSED**、VUID 0、p50 16.14 ms。
  **つまり「コンパイルが native に受理されて GL のリストが空」という仮説は反証された** —
  display list は空ではなく、コンパイル時のバッチは受理されていない。
- **したがって以前の「529〜1066 件の取消で画像が床水準」という成果は、正しい根拠を持っていなかった**。
  画像が床水準だったのは事実だが、それは**「この fixture では取消対象の replay が可視の差を生まない」**ことの
  観測であって、「別経路が内容を引き受けた」証明ではない。**証拠のない取消は既定から外した**。
- **現在の既定**: 取消は **native モデル描画が成功したときだけ**（＝実際に描いた事実があるとき）起こる。
  ラボフラグ由来の名前ベース分岐（`cancelHandDisplayLists` / `cancelModelDisplayLists`）は**撤去**した。
  代償として **GL 呼び出しの削減（約1000件/run）は失われる**が、それは**根拠のない削減**だった。
- **残る確認（次）**: 「この fixture で手/手持ちが実際に描かれているか」を `emptyHand` の対で測る
  （描かれていないなら、取消の有無は画像に現れないという説明がつく）。format 2 の受理が 0 である事実と併せて、
  **手/手持ちの per-frame 描画がどの経路にあるのか**を特定する。

## 0.1 構造修復キュー（goal「直近の実行順」1〜4 に対応）

| # | 契約 | 状態 |
|---|---|---|
| 1 | texture の実体・更新・寿命を一つにする | **実装済み・両GPU受入**（`297ce5caa` ＋ §0.3。lifecycle/拒否/コスト計測まで。画像同一性の統制比較のみ残る） |
| 2 | draw の queued→recorded→submitted→completed→retired と取消根拠の整合 | **一部実装**（§0.2: 取消を記録された事実に限定、名前ベースを撤去）。queued→recorded→submitted→completed→retired の target/generation 紐付けは未着手 |
| 3 | fixture の準備→到達待ち→描画待ち→capture→report と thread/JSON 所有 | **完了**（§0.4 fixture snapshot、§0.5 draw の target 世代、§0.8 モデル描画も同じウィンドウ順序へ統合） |
| 4 | Nether の未完を具体的に直す | **未着手**。短い trace と client/server stack で帰還・停止の地点を特定する（早い転送・budget 延長の反復はしない） |
| 5 | plan を現状表へ戻し、経緯を history へ移す | **この文書で実施** |

##

> **構造修復フェーズ（2026-10-10〜）**: 方針は `docs/hints-2026-10-10-nether.md` と
> **`docs/handoff-structure-recovery-2026-10-10.md`（構造修復プロンプト）** に従う。要旨は
> (1) texture の実体・更新・寿命を**一つの契約**にまとめる、(2) frame/draw の
> accept→submit→complete→retire と取消根拠を整合させる、(3) fixture の段階・thread 所有を一つの状態機械にする、
> (4) plan を現状表へ戻し経緯を history へ移す。**全面書換え・汎用GLエミュレーション・flag/定数/budget での症状隠しは禁止**。
> 短い修復フェーズのあと、全 vanilla native 化・GL なし frame と実 present へ戻る。

## 構造修復 1: texture の所有契約を一つにする（このターン・実装済み／検証は一部保留）

- **根因（実コードで確認）**: 同じ GL texture id に対して **Vulkan image が2つ**あった。
  `VulkanTextureRegistry` が `entries[id].texture` を持ち、`announceFullPixels(...)` は**そちら**へ patch を書く。
  一方 `VulkanTerrainGpuBackend` は `atlasTexture`/`lightmapTexture` を**自前で `VulkanGlTexture.create`** して保持し、
  **descriptor が sample するのはこちら**。したがって patch はサンプルされず、`invalidateTexture` が
  `applyAnnouncedTo`（BufferedImage 経路のみ有効）に失敗して **GL 全面 readback** に落ちていた。
- **直した契約**:
  - **実体の所有者を registry に一本化**した。バックエンドは `registry.imageFor(id, w, h, filter)` で
    **同じ object** を受け取り、それを sample する（フィルタは所有者側で一度だけ決める: atlas=nearest, lightmap=linear）。
    backend 側の `VulkanGlTexture.create` による第2の実体は**撤去**した。
  - **初期化状態**を明示した（`fullyInitialized`）。**部分 patch だけでは未初期化 image を作らない** —
    全面が一度も入っていないなら readback を残す。
  - **世代の消費**を明示した（既存の `announcedGeneration`/`consumedGeneration` に統合。新しい世代カウンタを
    増やさない）。CPU 適用（int[][] patch・BufferedImage 変換）が世代を進め、**readback を省いた事実が消費**する。
    「無効化を起こした書き込みが既に pixels を渡していた」ことを**事実**として扱う。
  - registry の生成が**7箇所に散っていた**のを単一アクセサ `celtium$registry()` にまとめ、
    「2つの registry ができて実体がずれる」余地を消した。
- **実測（AMD、溶岩世界＝既定、duration 120 s、同一条件の変更前 run と比較）**:

| 指標 | 変更前（p250-upload） | 変更後（p251-owner） |
|---|---|---|
| verdict | exit 0 / clean / **PASSED** | exit 0 / clean / **PASSED** |
| p50 | 40.41 ms | **25.49 ms** |
| `directMirrorRefreshes` / `readbacksSkipped` | — | **472 / 473**（ほぼ全更新が readback 不要） |
| `CELTIUM_PATCH_REJECTED` | — | **0** |
| VUID / クラッシュ | 0 / なし | **0 / なし** |

- **検証として言えること**: **無効化のたびに走っていた全面 readback が消えた**（473 回が skip として計上され、
  実測 p50 が 40.41→25.49 ms に下がった）。`applyAnnouncedTo` は BufferedImage 経路として残る（dynamic texture 用）。
- **まだ言えないこと（重要）**: **画像が同一だとは言っていない**。比較した run は**溶岩世界（アニメーションする
  スプライトを持つ）**で、`PINNED_WORLD_TIME` は world time を固定するが **sprite の animation 位相は固定しない**
  （handoff の指摘どおり）。実際に全画面 meanAbsDiff 6.29 / 400k 画素の差が出ており、**これが位相の違いなのか
  patch 経路の content 差なのかは切り分けていない**。**次の検証は「アニメーションを統制した（または静止した）
  場面」で行う**。
- **残る具体的な確認項目**: patch 経路が **mip と全画像内容**を readback 経路と同等に保つか
  （`MIRROR_GL_MIPS=false` なので level 0 のみを触る点は両経路で同じ、という読みまではした）。
  **reload / GL id 再使用 / sprite 単位 mip** はまだ測っていない。
- このターンで**ヒント1・3 の修正は維持**（capture 段階化・部分レポート・dimension fail-closed）。


本書は **現在の到達点・優先順・受入の唯一の現状表** である。経緯と過去の測定は `docs/history/` にあり、そこにある数値や「closed」は
現在の判定ではない。根拠は `docs/audit-2026-10-09-third.md`（第3回監査、監査開始HEAD `c89bda361`）と、監査後に取得した実測である。
**用語**: 実装済 = コードがあり動く / 局所検証済 = 特定fixtureで確認 / 未検証 = 動く証拠がない / 未実装 = コードがない。
カウンターの増加は描画の証拠ではない。format認識・受理・記録・submit・完了・dropを混同しない。

## 1. 厳密な完成条件の現状（この表は各ラウンドの実測で更新する）

| 条件 | 状態 | 欠落・根拠（現在の実測） |
|---|---|---|
| (a) 全vanilla描画と最終合成・presentがnative Vulkan | **部分** | **nativeで動いているもの**: 地形パス（1フレーム計 約2.2 ms（AMD）/ 約6.1 ms（Intel）、`solid`/`cutout_mipped`/`translucent` の timestamp 計測）、即時系の各family（`immediateSubmittedBatches` 13.5/フレーム）、**フレーム最終合成**（`presentFromComposite` が既定ON、画面サイズの読み戻しは消えた）。**block registry は AMD で 254/254 を掃引済み・失敗0、Intel は 75/254 の抜き取り**（`blockSweep`）。**未検証/未完**: **entity/TESR の全種類**（display list 経由のモデルは §0.10 で既定の経路になったが、
  全 mob/TESR・他 scene は未検証）、破壊/selection overlay、**Nether**（地形ダウンロードが完了せず NO_CAPTURE）。
  水/溶岩/火と dimension(End) は世界・受入とも追加済み（§0）。 |
| (b) GL contextを生成せず、GL call/readback/bridge/copyback/GL swap/GL fallbackを必要としない実行 | **未達成（ただし欠落はブリッジに限定された）** | **前進**: 当時の計器で `regStaleCalls=0`・`inPlaceRefreshes=0`・announce 経由 16.2/フレーム を観測した。
  **ただしこれは通常fixtureの範囲の話であり「全sceneで readback が無い」意味ではない**。構造修復1（`297ce5caa`）で
  atlas/lightmap の**無効化ごとの全面 readback を廃止**し、`readbacksSkipped=473` 対 `directMirrorRefreshes=472` を
  実測した（=ほぼ全ての更新が CPU 供給で閉じる）。**残る依存はブリッジ本体**（`VulkanFrameTarget`: FBO付け替え・色/深度blit・semaphore待ち・状態クエリ48箇所）で、CPUコストは **0.58 ms/フレーム（AMD）/ 0.83（Intel）**。GL context は依然として生成する。 |
| (c) native swapchain frameが実window/compositor出力へ到達 | **前半は確認済み・後半は環境制約** | **確認済み**: `presentMarker` のマゼンタ (1,0,1,1) を提示画像から読み戻すと **mean 191.25** で、期待値 `(255+0+255+255)/4=191.25` と**完全一致**（＝提示された swapchain 画像にこの renderer の合成フレームが入っている）。**未確認**: 実画面への到達。この環境には capture 可能な表示出力が無い（X は screen 0×0、Wayland は `no wl_output`、`grim` は導入済みだが出力が無い）。不足条件は §7.6 に明記。 |
| (d) AMD/Intelで必要場面と失敗復旧、監査P0/P1を明示受入で閉じる | **部分（受入は緑、監査項目の一部が未了）** | **既定の世界は溶岩/火入り**（`worldLayers` で差し替え可）で、**AMD p50 25.49 ms（修復1後）/ Intel 軽い世界 28.47 ms・溶岩世界 64.52 ms**、
  いずれも exit 0 / clean / PASSED。**dimension は End が AMD/Intel とも capture 時 dimension 1 で PASSED、
  Nether は NO_CAPTURE**（§0）。postprocess / weather / spectator spider / command-buffer target / JEI も PASSED。
  失敗復旧は allocation（buffer/uniform/context/frame target/texture）・descriptor・texture upload・submit・
  fence timeout・out-of-date・resize/minimize・reload・world switch を**両GPUで確認済み**（submit/fence は
  exit 1・report あり・有界で、stall 後は資源を解放しない: `stalledTeardownSkips` 547/549）。
  監査項目の残りは §2 を参照。 |
| (e) mod対応範囲・Java条件・最低VK能力・未検証GPU/OS・ライセンス/性能をdocsと配布物で一致 | **部分** | 性能は**同一fixtureで実測**（AMD 16.02 ms 対 参照 16.18 ms、Intel 28.54 ms、参照は Intel でも 16.17 ms で GPU 非依存）。画像は 参照比 **meanAbsDiff 7.78/255・HF 0.81**（統制: 参照同士 0.064 / HF 1.002）。mod 対応範囲・最低VK能力・未検証GPU/OS の記述は `docs/scope-and-limits.md` と要突き合わせ。 |

## 2. 第3回監査 項目別の現在状態

| # | 監査の内容 | 現在 | 根拠・残り |
|---|---|---|---|
| **A01** | model-viewの最初3列のみ送り平行移動が消える | **修正済・受入済** | writerが `projection*modelView` を1行列として送り、**全7即時/UIシェーダ**が `u_ProjectionMatrix * vec4(a_Position,1.0)`。列を再構築するシェーダは**0**。SPIR-Vはビルド時にGLSLから生成（committed .spvなし）のでjarと整合。`immediate_position.vert` の再構築残りも撤去（writerが列をゼロ化したため全頂点が平行移動点へ収束していた）。run: exit 0 / clean / message 0 / PASSED |
| **A02** | `int[][]` を行配列として読む | **修正済・CPU契約受入済**（GPU画像受入は残る） | `data[level][y*levelWidth+x]` の契約に合わせ、level 0 を**平坦**に読む。level別寸法は「書き込みレベルの寸法をpatch寸法として渡す」契約に統一。動的textureの平坦 `int[]` 経路は別契約として分離 |
| **A03** | 部分更新なのに `UNDEFINED` でatlas全体とmipを破棄 | **修正済・CPU契約受入済（GPU受入は残る）** | 全面uploadと部分更新を分離。patchは**レベル別に記録した現在layout**から遷移し、**level 0のみ**の範囲、mip chainを記録しない、patch自身が戻し遷移。**専用受入**: `scripts/cpu-contract/TextureContractCheck.java` が2つの離れたpatch（2x2@(1,2) と 3x1@(5,0)）を当て、**patch外の全byteが不変**・色順ARGB→RGBA・行順・touched=7 を検証（`CELTIUM_CPU_CONTRACT PASS`）。通常fixtureも exit 0 / clean / message 0 / PASSED |
| **A04** | 初回patchだけで全面未初期化のmirrorを作る | **是正側は修正済・全面供給は未解決** | `hasFullContent()` を持ち、全面内容の無いミラーは受理経路がdescriptorを作らず拒否（`partialMirrorRefusals`）。AMD実測では**0件＝このfixtureはpatch先行の順序を作らない**。atlas stitch時の全面データ保持・供給は残る |
| **A05** | patch寸法でmip chainを作り直す | **修正済** | A03の修正でpatchはmip chainを記録しない。sprite単位mipは `MIRROR_GL_MIPS` 経路がGLの各levelをコピーする（全atlas blitを正解とせず、遠景・透明境界での比較は残る） |
| **A06** | 保持画像とpatchの競合、hash skip | **修正済** | patch成功時に `lastPixelHash` を無効化し、patch後の同一内容全面uploadが重複skipされないようにした。CPU shadowとresource generationでの全面管理は残る |
| **A07** | metadata・転送成功の扱い | **修正済** | hashを転送**後**に確定、`imageUploads` は成功時のみ、`Entry.width/height` が画像に追随（寸法を可変に）、skipは `imageUploadsSkipped` として別計数。GL ID再使用の検証は残る |
| **A08** | 受理前失敗でpending meshに頂点が残る | **修正済・受入済（範囲拡大）** | preflightをappend前に置く方針を維持しつつ、`prepareImmediate()` で **pipeline と descriptor を group 生成前に用意**し、format 2/3/4/5/6/7/8/9 と texture にも広げた。`VulkanDynamicMesh` は頂点・indexの容量を両方確保してから書き込む。注入run `sol-cb-final-retry-amd` で `retries=1`・最終カウンタ4/4/4/4を実測 |
| **A09** | 受理後の消失を全形式で数えていない | **修正済** | 描画ループ内の**7つのskip地点すべて**が `postAcceptDroppedGroups` を増やす（以前はformat 5のdescriptor失敗のみ）。実測0は、実装したdrop計器が数えた消失がなかったという事実。全formatのGPU完了・見た目を証明しない。branch別の内訳とbatch ID対応付けは残る |
| **A10** | `offTargetDrawn` は実描画ではない | **修正済・AMD受入済** | 常に `return false` していた旧 offscreen scaffold（空passをclear/copy）を削除し、off-targetの受理済みgroupは **bind切替のHEAD（旧FBOがbind中）で同期windowが描いて書き戻す**。実測 `offTargetAccepted/Recorded/Completed=2/2/2`、`mainWasPending=true`・`secondaryWasPending=true`、別FBOのPNGで赤・緑が正位置・未更新領域の不一致0。copy-back回数を描画証拠にはしない方針を維持 |

| **A11** | format 2（POSITION_TEX_COLOR）は既定でGL（※この記述は撤去済み） | **修正済・AMD/Intel受入済** | 既定maskを `1,2,3,4,5,6,7,8,9` に。前提として backend 専用 `VulkanCommandBufferState`（handle別ライフサイクル）と `flushImmediateWindow()`（begin→acquire→pass→release→submit→fence→endFrame の同期window）を実装し、`flushPendingBeforeGlDraw` が pass 外で直接記録していた欠陥を除去。受入: `sol-cb-final-amd` exit 0 / clean / msgs 0 / fixture PASSED / passed=true / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0。再現は `sol-cb-base-amd`（70メッセージ）。**Intel**: target受入は `passed=true`（最終artifactのログ行）だが、run verdict は
 1回目 `worldFrames<100` の起動ゲート、2回目は480秒上限で report 未到達。**フレーム数不足が未解決**（window往復コストが最有力） |
| **A12** | state modelとpipelineの不一致 | **未解決** | alpha enable/func、depth func、separate alpha blend、color mask、scissor、texture matrix。pipelineはLEQUAL/ADD等を固定 |
| **A13** | current color/alphaの欠落（group keyにも無い） | **未解決** | POSITIONはRGBのみでalpha=1、POSITION_TEXは白固定。tint/alphaの再現証拠なし |
| **A14** | normal無視のITEM対応は照明対応ではない | **未解決** | 固定lighting・normal変換・shade・fog |
| **A15** | sampler固定値と旧比較による調整 | **未解決** | blur/clamp引数をannounceへ渡していない。filter/wrap/LOD固定 |
| **A16** | 「hot-pathのGL読取り除去済み」は過大 | **訂正** | 通常frameのMODELVIEW/CURRENT_COLOR/fog/bound texture/FBO/viewport照会が残る。診断用readと通常readを分けて数える |
| **A17** | `withoutGlIdentity` 成功はGLなし描画ではない | **訂正済** | GL contextを生成せず全frameをVK資源から描く経路が要る（条件(b)） |
| **A18** | bridge presentのownership/layout契約 | **未解決** | 共有画像のbarrierは `QUEUE_FAMILY_IGNORED`、復帰先 `COLOR_ATTACHMENT_OPTIMAL`。最終native経路はbridge資源を使わない |
| **A19** | native presentの画面到達証拠 | **未確認** | compositor/headless capture等を調査し、markerと連番で実window出力を確認。不可なら具体的不足条件を残す |
| **A20** | wait failureを全面閉鎖したとは言えない | **部分（実機構成を追加）** | `waitForIdle()` は fence timeout/error で **false を返し `gpuStalled` を立てる**（以前は true を返し pending buffer を reset していた）。`settleGpu` は stalled で例外。submit前失敗は再試行1回・資源はqueueへ戻す。**submit成功後失敗／device lost は分岐のみ実装で実機注入は未実施** |
| **A21** | vanilla postprocessを根拠なくscope外にした | **訂正済・未実装** | `EntityRenderer.func_175066_a` はcreeper/spider/enderman視点でShaderGroupをロードし、`func_175069_a(ResourceLocation)` は引数あり。spectator 3視点・entity outlineは**本体機能**として実装対象 |
| **A22** | 比較worldは全vanillaのcoverageではない | **未解決** | registryから自動fixture生成、小場面分割。flat worldとlayout受付数で全制覇にしない |
| **A23** | 古い画像差とlocal attributionの混同 | **訂正済** | 68.81%（旧baseline対地形）と 0.267/255・0.40%（同一Celtiumの描画切替）を最新版全体の一致率として使わない。pinned Celeritas対current Celtiumを同一fixture/GPU/camera/sceneで再取得する |
| **A24** | 比較器が欠落verdictをfail-closedにしない | **未解決** | required fieldの存在・型・成功値を必須化 |
| **A25** | counterの単位が完了主張と違う | **部分** | `imageFilledMirrors` はresource数ではなく呼出し/refresh回数。`outsideWorldPassAccepted` はaccept判定前に増える。単位（resources/calls/batches/groups/frames）を分けて文書化（本表がその記録） |
| **A26** | S05/S06の専用受入が不十分 | **未解決** | 2つの独立batchを同groupへmergeし色・位置を画像確認。容量拡大跨ぎの保持 |
| **A27** | 性能向上の根拠がない | **未測定** | 正しい描画成立後に元Celeritasと同条件でCPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新を比較 |
| **A28** | 能力判定と純nativeの必須条件 | **未解決** | 現行はVulkan1.2・GL UUID・external FD・GL照会能力に依存。2GPU通過を全Vulkan GPU対応と同一視しない |
| **A29** | 根拠なく診断し定数と文書を反復 | **規律として受領** | vanilla優先。各roundは具体実装か意思決定に必要な限定検証。同一仮説を定数変更・budget延長で反復しない |
| **A30** | 容量と証拠保全 | **規律として受領** | pinned baseline・失敗再現・受入代表runをhash付きで保存。`.celtium-work/audit-2026-10-09-third` は監査者の再比較証拠として**削除しない** |
| **A31** | plan/scope/旧監査の矛盾蓄積 | **本表で解消** | 現在の完了条件と優先順を本表1つに持つ。scopeは実測に限定。旧24項目は下の再判定による |
| **A32** | off-target passが他targetのgroupまで破棄し、空targetのclearをGL FBOへ書戻す | **修正済・CPU契約受入済** | 規則を依存フリーの `ImmediateGroupQueue.retireGroupsForTarget` に抽出し、**そのtargetのgroupのみretire**・他は保持（`immediateGroupsKeptForOtherTargets`）。一致groupが0ならpassを開始しない（`offTargetPassesSkippedEmpty`）ので空clearをGL所有FBOへ書かない。copy-backはdraw記録数が増えたtargetでのみ記録する。GPU完了・GLへのupload成功の計器は別に必要。**専用受入**: CPU契約チェックが2独立target×4group＋不正groupで、A退去後は3件保持（B×2＋不正1）・B退去後は不正1件のみ残ることを検証 |

## 3. 旧24項目の再判定（第3回監査による）

| 項目 | 再判定 |
|---|---|
| S01 | bridge presentのownership/layoutを再開（A18）。最終native経路は別契約 |
| S02 | steady-state改善は維持。startup/texture/present・無限待機は未解決（A20） |
| S03 | cancellation修正は維持。全branchのbatch対応を追加確認（A09/A11） |
| S04 | state clone修正は維持。state実利用不足はA12 |
| S05/S06 | コード修正は維持。専用受入不足（A26） |
| S07 | 未完。state/lighting/color/alphaを再開（A12–A14） |
| S08/S09 | attribute/decode修正は維持。最終画像・照明の証明とは分ける |
| S10 | 順序・clear・targetを再評価。FBO完了主張は撤回（A08/A10） |
| S11 | counter単位とframe境界を再評価（A25） |
| S12/S13 | generation/retire/cache改善は維持。新upload/resize/保持画像の整合を再開（A06/A07） |
| S14 | 全形式のpreflight/dropを再開（A08/A09） |
| S15 | 通常teardownは維持。失敗時部分生成・cleanupを確認 |
| S16 | 新patch経路はA02–A06。A02/A03は修正済、A04–A06は未解決 |
| S17/S18 | composite意味判定・out-of-date/失敗時資源・画面到達を再開（A11/A18/A19） |
| S19 | 128 byteは満たしたがA01で変換の意味を壊していた。**A01修正で解消** |
| S20/S21 | validation parser/画像delta改善は維持。成功field欠落拒否を追加（A24） |
| S22 | 到達・作用・capture中の状態を確認するfixtureが必要（A21/A22/A26） |
| S23 | sampler/mip正当性を再開（A05/A15） |
| S24 | shader自動build/lockは維持。jar・fixture・SPIR-Vのprovenanceと主張を揃える（A23/A31） |

## 4. 維持する実績（有効な局所受入）

- AMD RX6400 / Intel UHD630 の通常hybrid fixtureが exit 0 / validation message 0 / fixture PASSED を記録している。これは有効な局所受入である。
- 画像からのtexture供給（初回7/8）と、その後のimage/patch経路は実装されている。**回数をunique texture数や正確な更新保証と混同しない**。
- A01/A02/A03の修正後も両runは green（exit 0 / clean / message 0 / PASSED）。

## 5. 優先順（第3回監査とgoalに対応）

1. **正当性を壊すP0**: A01–A03/A32はコード修正済。専用GPU受入の残件を閉じる。A04–A07（初回全面初期化・patch外保存・sprite単位mip・CPU shadow/generation・hash/size/descriptor）を次に閉じる。
2. **受理からGPU実行までの欠落・二重描画**: A08–A10（preflight/rollback、全formatのdrop集計、FBOのoffer/refused/recorded/drawn/copied-back分離）。FBOをGLへfalseで返したofferをVK描画済みとしない。
3. **pinned Celeritas GL対修正後Celtiumの再比較**（同一fixture/GPU/camera/scene）で地形の暗さ・局所変換・照明・alpha/depth/blend・current RGBA・sampler/mipを直す。巨大mod testを毎回回さず、CPU契約確認＋小GPU fixtureを使う。
4. **texture/frame所有のnative移行**: atlas stitch・動的texture/lightmap・sprite mip/animation・lifecycle・reloadの元データ供給、camera/stateをMinecraft側から供給、GL contextなしwindowでVulkan-owned color/depth/FBOから合成→swapchain→present。
5. **vanilla coverage**: 全block/state・item/model・entity/TESR・hand・GUI/font/HUD・粒子・sky/cloud/weather・水/溶岩/火・半透明と並び替え・破壊/selection overlay・dimension・FBO/postprocess・spectator 3視点。registryから自動fixture生成。
6. **reload/resize/world switch・失敗復旧**を両GPUで。timeout後に使用中command bufferをreset/re-record/destroyしない。
7. **vanilla本体成立後にmajor mod互換**（JEI先行収穫は維持、Intel JEIのbudget調整は主作業にしない）。影mod/shader packは本体のshaderless完成後。
8. **性能・配布**: 元Celeritasと同条件の定常CPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新。Sodium上流との差分は既実装・適用条件・ライセンス・採否を記録し、独立改善のみ採用。

## 6. CPU契約チェック（GPU非依存の受入）

`scripts/cpu-contract/TextureContractCheck.java` はデバイスもゲームも使わず、純ロジックの契約を検証する。
実行: `javac -cp <celtium-vulkan classes>:<lwjgl jars> -d /tmp/contract scripts/cpu-contract/TextureContractCheck.java`
→ `java -cp <celtium-vulkan classes>:/tmp/contract:<lwjgl jars> TextureContractCheck`。合格時は `CELTIUM_CPU_CONTRACT PASS`、失敗時は非0で終了する。

| 検証 | 内容 |
|---|---|
| A02 平坦texel契約 | `int[mipLevel][y*width+x]` として読み、行配列として読んだ場合と異なること・ARGB→RGBAの色順・行順 |
| A03 patch外保存 | 2つの離れたpatchを順に当て、**patch外の全byteが不変**・touched texel数・各patchの位置 |
| A32 target別retire | 2独立target×4group＋不正groupで、片方の退去後も他方が保持されること |

**限界**: Vulkanのbarrier・layout・実画像内容は検証しない（validation-cleanのrunが担う）。この検査はGPU受入の代わりではない。

## 7. 測定の規律（カウンターの意味）

| 名前 | 意味 | 完了の証拠になるか |
|---|---|---|
| `offTargetOffered` | off-targetへ**提示**したbatch（target生成前） | ならない |
| `offTargetAccepted` | 受理位置で数えたbatch（現状は0: 経路違いの計器） | 単独ではならない |
| `offTargetCopiedBack` | draw記録数が増えたtargetにcopyを記録した回数（A32修正後） | GPU完了・GL upload成功・画像の証拠には単独でならない。修正前の1957は空targetのclear/copyも含む |
| `imageFilledMirrors` | announced image/patchで**実際にピクセルが動いた回数**（修正後） | 単独ではならない（resource数ではない）。実測: 実転送113 + skip 3448 = 旧3629は約30倍の過大表示だった |
| `imageUploadsSkipped` | 内容が同一で転送しなかった回数（修正後） | ならない |
| `outsideWorldPassAccepted` | accept判定**前**に増える | ならない |
| `offTargetPassesSkippedEmpty` | 一致groupが0でpassを開始しなかった回数（A32修正後） | ならない（空clearを書かないことの証拠） |
| `immediateGroupsKeptForOtherTargets` | 別targetのため保持したgroup数（A32修正後） | ならない（保持の証拠） |
| `acceptedByFormat` | format別の受理数（既定の受付は format 1〜9。**単独では coverage の証明にならない**） | 単独ではならない |

## 8. コマンドバッファ受入（Sol集中修正・2026-10-10）

| 種別 | 条件 | 結果 |
|---|---|---|
| 再現（修正前jar・SHA固定） | `.celtium-work/sol-command-buffer/base-artifacts/`＋`sol-cb-base-amd` | exit 1 / validationClean false / **70メッセージ** / fixture PASSED（70はVUID抑制上限） |
| 受入（AMD通常） | `sol-cb-final-amd` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / delta accepted=recorded=submitted=completed=4 / pending0 / invalid0 / off-target 2,2,2 / 矩形・未更新領域の不一致0 |
| 受入（AMD・submit前失敗注入） | `sol-cb-final-retry-amd` | 同上＋`retries=1`（fixture内で実際に再試行） |
| 受入（AMD・初回修正版） | `sol-cb-fixed2-amd` | 同上（pendingImmediateIndices修正後） |
| 純ロジック | `vulkan:test` | `VulkanCommandBufferStateTest` 4件 pass |
| 通常fixture回帰（format 2 既定受理） | `p31a-format2-default-amd` | exit 0 / clean / msgs 0 / fixture PASSED / worldFrames 228 / `acceptedByFormat=1:0,2:217,3:0,4:0,5:214,6:1394,7:1136,8:0,9:0` / **recorded=submitted=completed=2961** / invalidImmediateRecords 0 / retries 0 |
| 受入（Intel通常） | `sol-cb-clean-intel` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / 4/4/4/4 / off 2/2/2 / 不一致0 / worldFrames 155 |
| 受入（Intel・submit前失敗注入） | `sol-cb-clean-intel-retry` | 同上＋`retries=1`（fixture内で再試行） |
| 通常fixture（Intel・format 2既定受理） | `p31d-intel-clean` | exit 0 / clean / msgs 0 / PASSED / **worldFrames 154**（変更前は114・129）、即時2155件が記録=submit=完了、invalid 0 |
| window数削減（1回目） | `sol-cb-windowcut-amd` / `p31c-windowcut-amd` | viewport変更のflushを削除し、テクスチャ告知は**キュー内groupが参照する場合のみ**flush。`orderPreservingFlushes` **633→388**（228→218フレームで約2.8→1.8/フレーム）、target受入 exit 0・通常fixture PASSED・記録=submit=完了=2829・invalid 0 |
| 訂正（Intelの480秒上限） | `sol-cb-final-intel2` / `p31b-intel-normal` | どちらも480秒上限で report 未到達だったが、**同時にAMD runを走らせていた競合が原因**だった。他を止めた単独runは 24〜27秒で完走し PASSED。**Intelは退行しておらず、むしろ 114/129→154 と改善**。測定は常に単独で行う |
| 受入（clear順序・描画→clear） | `sol-cb-clearorder-amd` / `sol-cb-clearorder-intel` | clear前に受理済みbatchが1件pending→clear境界で accepted=recorded=submitted=completed が各+1・pending 0、clear後の画像は**全ピクセルがclear色**（blue矩形も消滅・不一致0）。「描画→clear」の順序を実geometryで確認 |
| 受入（depth順序・近傍が遠方を隠す） | `sol-cb-depth5-amd` / `sol-cb-depth-intel` | 同じ矩形に近傍batch→遠方batchの順で描き、**近傍の色だけが残る**（中心=青、rectangle不一致0、`farLeakedPixels` 0）・4段カウンタ+2一致・pending 0。D24→D32→D24の往復とGL準拠のdepth順序を実geometryで確認 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 13 runのexit/clean/msgs/fixture/passed/delta/clearOrder/depthOrder/worldFramesとPNG 5枚のハッシュを固定 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 各runのexit/clean/msgs/fixture/passed/deltaとPNGハッシュ |

実行コマンド: `bash scripts/run-command-buffer-targets.sh amd|intel <new-run-name> [failImmediateSubmits]`
（Python/fixture/verdict のどれかが落ちれば非0で終了し、`commandBufferTargets.passed` と3枚のPNGを必須にする）。

## 99. 履歴

round ごとの経緯・旧測定・失敗証拠は **`docs/history/plan-round-log-until-2026-10-10.md`** にある
（この文書の旧版全体。削除していない）。画質・失敗経路の詳細な経緯は
`docs/history/quality-and-failure-investigation-2026-10.md`、goal の旧版は
`docs/history/goal-r46-before-2026-10-10.md`。
