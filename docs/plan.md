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

## 0.32 **提示画像は GL が描いたフレームと一致しない**（同じ瞬間で測って判明・§0.15/§0.19 の楽観を訂正）

- **同じ瞬間の比較を実装した**: 捕捉時に `presentedImageMeanAtCapture`／`presentedImageFramesAtCapture` を記録する
  （backend に `celtium$presentedImageMean()`／`celtium$presentedFrameCount()` を公開し、fixture が捕捉時に読む）。
  §0.16 で「別の瞬間の値だから根拠にしない」とした宿題を、**同じ瞬間の値**に置き換えた。
- **測定（AMD、`presentReadback=true`、duration 120 s、exit 0 / clean / PASSED、676 frames）**:

| 値 | |
|---|---|
| `presentedImageMeanAtCapture` | **96.75** |
| `presentedImageFramesAtCapture` | 666 |
| **GL スクリーンショットの平均** | **158.71** |
| スクリーンショットから HUD 上部 250 行を除いた平均 | **137.93** |
| 同・下部 60 行も除いた平均 | **137.39** |
| 中央 scene のみ | 138.12 |

  **HUD を除いても約41段の差**が残る ＝ **「提示画像は HUD を含まない world フレーム」という仮説は棄却**。
  **提示画像は GL が描いたフレームと系統的に異なる**（暗い）。
- **意味（重要）**: §0.15／§0.19 で観測した「平均が一致した」は**場面依存の偶然**であり、**提示内容の一致の根拠にはならない**。
  **条件 (c) の「native swapchain へ出した完成 frame」は、内容の点でまだ成立していない**。
- **次に潰す候補（この測定で絞れた）**: (a) 読み戻しの**範囲／形式**が違う、(b) **同期**のずれ（GL の書き込みが乗る前に読んでいる）、
  (c) 共有イメージの**レイアウト／所有権**。**次の計器**は「同じフレームで共有イメージと GL スクリーンショットの**画素ごとの差**を
  取る」こと（平均ではなく分布を見る）。

## 0.30 線パイプライン変更の限定回帰（即時経路全体に触れるため）

- 線の変更は **パイプライン生成・受理ゲート・キー** に触れるので、reload/resize/world switch と Intel で回帰を取った。

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ | `line primitives` 拒否 |
|---|---|---|---|---|---|---|---|---|
| AMD 通常 | 0 | true | **PASSED** | 671 | 12.93 ms | 0 | なし | **なし** |
| AMD reload | 0 | true | **PASSED** | 652 | 13.06 ms | 0 | なし | **なし** |
| AMD resize | 0 | true | **PASSED** | 604 | 16.19 ms | 0 | なし | **なし** |
| AMD world switch | 0 | true | **PASSED** | 667 | 12.90 ms | 0 | なし | **なし** |
| Intel 通常 | 0 | true | **PASSED** | 249 | 39.12 ms | 0 | なし | **なし** |

  **5 run すべて PASSED・VUID 0・拒否なし**。p50 も直近の水準内。**native の線は通常/reload/resize/world switch で安定**。

## 0.31 format 2（最終合成）の経路を読んだ結果: **画面は既に Vulkan が出している**

- `PRESENT_FROM_COMPOSITE` は**既定 true** で、format 2 の「画面合成」バッチに対して
  `beginFrameForPresent` → ブリッジ semaphore 付き `presentImage` を行う経路が**実装済み**。ただしその前提条件
  （`currentRenderTargetIsMain` 等）を満たさず、**format 2 は受理マスクで `target is outside the RGBA8/D24/full-viewport
  bridge contract` として拒否**され続けている（816〜817 件/run）。
- **しかし画面は既に Vulkan が出している**: §0.17 の per-frame present（`func_175068_a` 先頭で直前フレームを提示）が
  毎フレーム成功し（`presentedFromVulkan` 263 等）、**GL の swap も省略**されている。つまり
  **条件 (a) の「最終合成・present が native」という結果は、別経路で既に成立している**。
- **残るのは冗長な GL 合成（blit）だけ**であり、それを外すには制約がある:
  **ゲームのスクリーンショットは GL の既定フレームバッファを読む**（`Screenshot` 経由で、これが我々の画像証拠の
  唯一の経路）。**GL の合成を止めるとスクリーンショットが黒くなり、画像比較の手段を失う**。
  → **証拠経路を先に用意する**（Vulkan 画像の読み戻しで比較する、または提示画像 readback を捕捉フレームで取る）まで、
  GL 合成は**意図的に残す**。これは「未着手」ではなく**順序の判断**である。
- **計器**: `compositePresentRefusals` と format 2 の拒否件数が、この経路の状態を引き続き示す。

## 0.29 native の線の **Intel 受入**（能力依存の経路なので必須の段階）

- **受入（Intel、`lookDown`＋TESR 層、duration 150 s）**: exit 0 / validationClean / **PASSED**、302 frames、p50 31.49 ms、
  **VUID 0**、クラッシュなし。**`ACCEPTED byFormat 1 = 280`**（変更前は同種の場面で 1 桁〜26）、
  **拒否集計に `line primitives` のエントリが無い** ＝ **線は native で受理されている**。
- **正直な計器の問題**: 新しく足した `lineBatchesNative` / `lineBatchesRefusedForWidth` / `maxLineWidth` は
  **0 / 0 / -1.0** を返している（挙動ではなく**カウンタの置き場所とインスタンス**の問題。
  `CELTIUM_GROUPS` 行に出したが、この行を出している実体と受理を数えている実体が同じとは限らない）。
  **挙動の証拠は `ACCEPTED byFormat 1` の増加と拒否エントリの消滅で足りている**が、計器としては未完成と明記する。
- **意味**: §0.28 の native 線は **AMD と Intel の両方で成立**した。`lineWidthRange` による能力判定は
  **どちらの GPU でも 2.0 を満たした**（拒否カウンタが 0 であることと整合）。
- **残る最大のもの**: **format 2（最終合成）**。拒否集計で `2 target is outside the RGBA8/D24/full-viewport bridge
  contract` が 816〜817 件＝**毎フレーム1回、全画面クアッドが GL に渡っている**。

## 0.28 **native の線が成立**（format 1 の受理 17 → 797、拒否エントリ消滅）

- **実装したもの**:
  1. **線トポロジのパイプライン** `VulkanImmediatePipeline.createForLines(...)`
     （`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`）。8引数の既存ファクトリのシグネチャは変えず、内部に `lines` を通す形にしたので
     呼び出し側の変更はゼロ。線用のキャッシュを1系統追加（キーは既存の state キー＋**線ビット** `1L << 61`）。
  2. **能力判定**（baseline 方針に沿う形）: `VkPhysicalDeviceLimits.lineWidthRange(1)` を読み、**GL が要求している線幅**
     （`GL_LINE_WIDTH` を実際に問い合わせる）と比較する。`wideLines` は**任意機能**なので必須にしない —
     feature を別途問い合わせる必要も無い（`wideLines` が無ければ範囲が `[1,1]` になり、同じ比較で拒否される）。
     満たさない場合は **理由付きで拒否して GL に渡す**（`lineBatchesRefusedForWidth`）。
  3. `VulkanImmediateDrawer.isLineMode(mode)` を追加し、**線は format 1 のときだけ** native 候補にする。
- **受入（AMD、`lookDown`＋TESR 層、duration 150 s）**: exit 0 / validationClean / **PASSED**、823 frames、
  **p50 10.52 ms**、**VUID 0**、クラッシュなし。

| 計器 | 前 | 後 |
|---|---|---|
| `ACCEPTED byFormat 1` | **17** | **797** |
| `acceptedByFormat 1` | 26 | **810** |
| 拒否集計の `1 line primitives …` | **797** | **消滅** |

  **拒否されていた 797 件が、そのまま 797 件受理されている** ＝ **selection outline を含む線プリミティブが native で描かれる**。
  （残る format 2 の 816 件は**最終合成**で、条件 (a) の最後のピース。）
- **まだ GL に残るもの**: 最終合成（format 2）、GL/VK bridge、copyback、GL context。
- **未確認**: Intel での受入（線の幅能力は Intel でも 2.0 を満たす見込みだが**測っていない**）。
- **計器の置き場所**: `lineBatchesNative`／`lineBatchesRefusedForWidth` を要約側に置いたため**レポートに出ていない**
  （また同じ誤り）。挙動の証拠は `ACCEPTED byFormat 1` の 797 で足りているが、**次に触るときは専用行へ移す**。

## 0.27 native の線に向けた第一歩: 線プリミティブの展開と**CPU 契約検査**

- **実装**: `VulkanImmediateDrawer.expandIndices` が線モードを扱うようにした（それまでは `default` で `null`＝未対応）。
  - `GL_LINES`: そのまま対（頂点数が奇数なら**推測せず拒否**）。
  - `GL_LINE_STRIP`: n 頂点 → n-1 線分。`GL_LINE_LOOP`: ＋閉じる線分。
- **CPU 契約検査（`celtium-vulkan/src/test/java/.../VulkanImmediateDrawerTest.java` を追加）**: 線リスト・奇数拒否・
  ストリップの連続対・ループの閉じ・1頂点拒否・クワッドが従来どおり2三角形、の6件。**`:celtium-vulkan:test` は成功**。
- **なぜこれが安全に進められるか**: `unsupportedReason` は**まだ線を拒否している**ので、**GPU の挙動は変わっていない**
  （selection outline は引き続き GL が描く）。展開だけを先に固定し、**不変条件を壊せば失敗する検査**を付けた。
- **次に必要なたたき台（実装済みの設計判断）**:
  1. **線トポロジのパイプライン**（`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`）。既存は全て `TRIANGLE_LIST`
     （`VulkanImmediatePipeline` 180 行）。線を使うのは **format 1（POSITION_COLOR）だけ**なので、パイプラインは1系統で足りる。
  2. **`glLineWidth(2.0)` は再現できない**: Vulkan の `wideLines` は**任意機能**であり、goal の baseline 方針
     （広い Vulkan 対応 GPU で動くこと）に照らして**必須にしてはいけない**。よって
     **`wideLines` が要求幅を満たすときだけ線を native で受け、満たさなければ理由付きで拒否して GL に渡す**のが正しい形。
  3. `unsupportedReason` を **format に依存させる**（線は format 1 のみ受付）。

## 0.26 **format 1 の ~800 件は「線の正しい拒否」だった**（§0.24 の記述を訂正）+ 最終合成の拒否を特定

per-format の拒否集計を専用行（`CELTIUM_REFUSALS_BY_FORMAT`）に出したところ、内訳が名指しされた。

```
1 line primitives are not implemented in the Vulkan terrain renderer yet = 797
2 target is outside the RGBA8/D24/full-viewport bridge contract           = 817
1 preflight: format=1 texture=21 (no terrain pipeline for this pass)      = 10
6 preflight: format=6 texture=NN (no terrain pipeline for this pass)      = 各 9〜18
9 preflight: format=9 texture=25 (no terrain pipeline for this pass)      = 12
```

- **format 1 の ~800 件（797）は `GL_LINES` の拒否**である ＝ **selection outline は native に提供され、
  「線は native 未実装」という理由で正しく拒否され、GL が描いている**。
  **§0.24 の「outline は提供されていない」は誤り**だった（family 別の print 上限に当たっており、
  ログの grep では 0 件に見えていた。**集計を専用行に出して初めて見えた**）。
  → **overlay 系の残件は「native の線が無いこと」そのもの**であり、ゲーム側のガードの問題ではない。
- **format 2 の 817 件**は `target is outside the RGBA8/D24/full-viewport bridge contract` ＝
  **最終合成（POSITION_TEX_COLOR の全画面クアッド）が GL 所有のまま**であることの実測。
  これは**条件 (a) の最後のピース**（「最終合成が native」）に直結する。
- **次に実装する2つ（どちらも名指しできた）**: (1) **native の線**（`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`＋
  `VK_POLYGON_MODE_LINE`、`fillModeNonSolid`/`wideLines` は feature 確認つき）、
  (2) **native の最終合成**（bridge 契約の外にある format 2 の全画面クアッドを Vulkan の合成パスへ）。
- **計器の教訓（再掲）**: family 別 print の上限は「0 件」と誤読させる。**新しい分類は必ず専用行に集計で出す**。

## 0.25 TESR 可視場面と、**format 1 の未受理 800 件**という新しい具体的ギャップ

- **場面**: 水を含まない層に **chest と enchanting table** を入れて見下ろす（TESR が視界に入る）。受入は
  exit 0 / validationClean / **PASSED**、830 frames、**p50 10.48 ms**、**VUID 0**。
- **確認できたこと**: **TESR は display list ではない**（`modelMeshLists=2 / quads=12` のまま＝プレイヤーの手のみ。
  chest/enchanting table は加わらない）。TESR は即時描画なので別経路である。
- **新しい具体的ギャップ（実測）**: `ARRIVALS byFormat` は **1:826, 2:1639, 6:5748, 7:514…** なのに
  `ACCEPTED` は **1:26, 2:0, 6:5746, 7:5228…** ＝ **format 1（POSITION_COLOR）が 826 到着して 26 しか受理されていない**。
  format 1 は **selection outline・破壊 overlay・デバッグ表示**の family であり、**overlay 系の残件そのもの**である。
- **解決（§0.26）**: その ~800 件は **`GL_LINES` の拒否（797 件）**だった。family 別 print の上限のためログに出ておらず、
  「拒否として数えられていない」ように見えていた。**集計を専用行に出して確定**した。
- **註**: `line primitives` の拒否は依然 0（§0.24 のとおり線は native 対象外で、提供されていない）。

## 0.24 selection outline が提供されない理由を**ガード単位で**分解（レンダラ側は正常と確定）

`drawSelectionBox` の呼び出し条件（`EntityRenderer.renderWorld`）は次の形で、**どこで止まるかを1つずつ計器で潰した**。

```java
if (flag && this.mc.objectMouseOver != null && !entity.isInsideOfMaterial(Material.WATER)) {
    if (!ForgeHooksClient.onDrawBlockHighlight(...))        // Forge イベント。listener が cancel できる
        renderglobal.drawSelectionBox(entityplayer, this.mc.objectMouseOver, 0, partialTicks);
```

| 条件 | 計器/出典 | 結果 |
|---|---|---|
| レイがブロックに当たる | `CELTIUM_MOUSEOVER` | **成立**（`hit=BLOCK@…`、`inGameHasFocus=true`、`hideGui=false`、`screen=none`） |
| 水の中にいない | `!isInsideOfMaterial(WATER)` | **既定の層では不成立**（層は bedrock3→dirt4,5→grass6→**water7** で足元が水）→ **水を含まない層で走らせて成立** |
| 線プリミティブが native で描かれる | `VulkanImmediateDrawer.unsupportedReason` | **native は線を実装していない**（`GL_LINES`/`LINE_STRIP`/`LINE_LOOP`/`POINTS` を理由付きで拒否＝**正しい設計**。GL が描く） |
| Forge の `DrawBlockHighlightEvent` が cancel されない | 未計器 | **残る唯一の候補**（インスタンスには mod が入っている） |

- **受入（AMD、`lookDown=true`、水を含まない層、duration 150 s）**: exit 0 / validationClean / **PASSED**、871 frames、
  **p50 9.92 ms**（これまでで最良）、**VUID 0**、`hit=BLOCK@BlockPos{x=0, y=4, z=-1}`。**line の拒否は依然 0 件**
  ＝ outline は**まだ提供されていない**。
- **確定したこと**: **レンダラ側に欠陥は無い**。線は native の対象外として**理由付きで拒否**され、GL が描く経路になっている。
  提供されない原因はゲーム側のガード（水 or Forge イベント）である。
- **次の安い一手**: 場面から `RenderGlobal.drawSelectionBox` を直接呼ぶ（`ForgeHooksClient` をバイパスする）か、
  `DrawBlockHighlightEvent` の cancel を記録する。前者なら **native の線拒否→GL 描画**の往復がそのまま検証できる。
- **註（破壊 overlay）**: 掘削中のひびは `PlayerControllerMP` の破壊進行が必要で、この場面の対象外。

## 0.23 破壊/selection overlay の場面を追加（**outline は未提供**＝レンダラ側の問題ではないと確定）

- **追加した場面**: `-Dceltium.validation.lookDown=true` で**カメラを真下（pitch 75）に向け**、`hideGUI` を false にする
  （selection outline は HUD 表示中しか描かれないため）。ヘッドレスでは誰もカメラを動かさないので、場面が自分で指定する必要がある。
- **受入（AMD、duration 150 s）**: exit 0 / validationClean / **PASSED**、708 frames、**p50 12.09 ms**、**VUID 0**、スクリーンショット有り。
  `acceptedByFormat 1:16`（従来 1:5）。
- **しかし selection outline は描かれていない**: スクリーンショット中心に輪郭が見えず、**`line primitives` の拒否が 0 件**
  ＝ `drawSelectionBox` の `GL_LINE_STRIP` バッチは**そもそも即時経路に提供されていない**。
  **これはレンダラ側の欠陥ではない**（`VulkanImmediateDrawer.unsupportedReason` は `GL_LINES`/`GL_LINE_STRIP`/`GL_LINE_LOOP`/
  `GL_POINTS` を**既に理由付きで拒否**しており、線は GL が描く経路になっている）。
- **棄却した仮説（このターン）**: (a) 線を三角形として誤描画している → `expandIndices`/`unsupportedReason` を読んで**棄却**、
  (b) 受理されているのに不可視 → **そもそも受理も提供もされていない**ため棄却、
  (c) `hideGUI` が原因 → false にしても件数が変わらず**棄却**。
- **次の計器（1行）**: 場面で `mc.objectMouseOver` の**種別と座標**を記録する。`typeOfHit != BLOCK` なら
  `drawSelectionBox` の手前で止まっていることになり、そこが次の分岐点である。
- **註**: 「破壊 overlay（掘削中のひび）」はさらに手前（ブロックを掘る操作が要る）なので、この場面では対象外。

## 0.22 spectator creeper / enderman の受入（goal 項目5が名指しする3種のうち残り2つ）

- fixture には元から creeper/spider/enderman の spectator 効果（vanilla ShaderGroup の entity outline を
  `loadEntityShader` で読み込む）があり、**spider は受入済み**だった。残り2種を受入した。
- **受入（AMD 2場面＋Intel 1場面、duration 150 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | `modelMeshLists/Quads` | 拒否 | VUID | `spectatorResult` / `shaderActive` |
|---|---|---|---|---|---|---|---|---|---|---|
| AMD creeper | 0 | true | **PASSED** | 663 | 13.36 ms | 1204 | **8 / 48** | 0 | 0 | loaded / **True** |
| AMD enderman | 0 | true | **PASSED** | 663 | 13.32 ms | 1210 | **9 / 54** | 0 | 0 | loaded / **True** |
| Intel creeper | 0 | true | **PASSED** | 234 | 40.83 ms | 414 | **8 / 48** | 0 | 0 | loaded / **True** |

  **creeper と enderman のモデルが native 経路で捕獲・描画されている**（素の場面は 2 リスト/12 クワッドなので、
  8/48 と 9/54 はこの2体のモデルが入ったことを示す）。`displayListCancelled == modelDraws`（成功時のみ取消）、
  `modelDrawRefusals=0`、スクリーンショット有り、クラッシュなし。
- **意味**: goal 項目5が名指しした **spectator creeper / spider / enderman の3種が両GPUで受入済み**になった。
  **entity outline は vanilla ShaderGroup として動いている**（`shaderActive=True`）。ただし**その outline 自体は
  依然 GL のパス**であり、**native 化は残件**である（延期ではなく未着手と明記）。

## 0.21 **Nether 成立（AMD/Intel 両方）**: 根因は「転送先のポータルの中に立っていた」こと

**計器で確定した根因（推測ではなく caller chain）**: `EntityPlayerMP.changeDimension` に stack を出す計器を入れたところ、
戻りの転送は次の連鎖で起きていた。

```
CELTIUM_CHANGE_DIM to=0 teleporter=Teleporter
  EntityPlayerMP.changeDimension
    Entity.func_184204_a            (= Entity.changeDimension)
      Entity.func_70030_z           (= Entity.onEntityUpdate … バニラのポータル論理)
        EntityLivingBase/Entity/EntityPlayer.func_70071_h_ (onUpdate)
          EntityPlayerMP.func_71127_g / NetHandlerPlayServer … / MinecraftServer
```

**つまり**: fixture の転送は `player.setPortal(new BlockPos(player))` を立てて
`transferPlayerToDimension` を呼ぶため、**Teleporter が見つけた/作ったポータルの *中* に着地**し、
**バニラのポータル論理が約55 tick 後にそのまま送り返していた**。
（`inPortal` の足元ブロック判定が false だったのは、体がポータルに接している状態を見ていなかったため。
体力は終始 20.0/20.0 で**死亡仮説は棄却**、チャンクは loaded で**未達仮説も棄却**。）

- **修正（fixture 側・境界が明確）**: 転送成功直後に **ポータルの外へ出す**
  （`player.setPositionAndUpdate(player.posX, player.posY, player.posZ + 2.0D)` — ポータルは軸方向に1ブロック厚なので
  Z に2ブロックで向きによらず外）＋ `timeUntilPortal = 0`。
- **受入（両GPU、`dimensionAtTick=40`、`dimension=-1`）**:

| GPU | exit | validationClean | fixture | frames | `dimensionClientAtCapture` | `dimensionWorldAtCapture` | 戻り転送 | VUID |
|---|---|---|---|---|---|---|---|---|
| **AMD** | 0 | true | **PASSED** | 691 | **-1** | **-1** | **0 回** | 0 |
| **Intel** | 0 | true | **PASSED** | 391 | **-1** | **-1** | **0 回** | 0 |

  `dimensionServerResult=transferred`、`dimensionArrivedAtTick=41/42`。**capture 時の client と world が両方 -1** ＝
  goal 項目4の受入条件（capture時 player/world/provider が target）を満たす。
- **旧記載の撤回**: 「Nether は GuiDownloadTerrain が残留して NO_CAPTURE」は**誤り**だった。実際は
  **到着成功 → ダウンロード画面は約48 tick で正常に消える → その約8 tick 後にポータル論理が送り返す**であり、
  画面の残留は症状ではなかった。**旧 Nether PASSED を外した判断も、この根因判明により更新する**
  （当時の PASSED は capture 時 dimension を見ていなかったため受入としては不十分、現在の方式で取り直した）。
- **残置した計器**: `EntityPlayerMPMixin` は**診断のみ**（次元変更時に caller chain を1行出すだけ・挙動を変えない）。
  次元まわりの次の作業で効くため残す。fixture 側の trace も位置・チャンク・体力・ポータル・onGround を持つ。

## 0.20 Nether の根因が**別物**だった: 「画面で固まる」ではなく「到着後に約56tickで Overworld へ戻される」

- 保存済み trace を読み直した結果、**停止ではなく往復**であることが分かった。新しい測定（AMD、`dimensionAtTick=40`、`dimension=-1`、
  duration 200 s、trace は元から **5 tick ごと**に出ていた）:

| tick | client | world | server | screen | arrivedAt |
|---|---|---|---|---|---|
| 40 | 0 | 0 | 0 | none | -1 |
| **42** | **-1** | **-1** | **-1** | **GuiDownloadTerrain** | **42** |
| 45〜85 | -1 | -1 | -1 | GuiDownloadTerrain（**約43 tick 表示**） | 42 |
| **90 / 95** | -1 | -1 | -1 | **none**（＝ダウンロード完了。チャンクは届いている） | 42 |
| **98** | **0** | **0** | **0** | GuiDownloadTerrain | 42 |
| 100〜120 | 0 | 0 | 0 | GuiDownloadTerrain → none | 42 |

  レポートは `NO_CAPTURE`、`dimensionServerResult=transferred`、`dimensionArrivedAtTick=42`、`clientDimensionAtExit=0`。
- **読み方**: 転送は成功し、**クライアントもサーバーも -1 になり**、**ダウンロード画面も約48 tick で消える**
  （＝チャンクは届いている）。**その約8 tick 後に dimension が 0 へ戻る**。旧説明の「GuiDownloadTerrain 残留」は
  **到着直後の正常な待ち**であり、根因ではなかった。
- **次の計器（実装済み・次の1 runで読む）**: trace に **クライアント位置**・**足元チャンクのロード状態**
  (`world.isBlockLoaded`)・**足元がポータルブロックか**を追加した。候補は
  (a) 転送先が**ポータル内**でゲーム自身のポータル論理が送り返す、(b) 統合サーバー側の再転送、(c) 到着地点が
  チャンク外/不整合。**(a) はこの3つ目までの計器で確定できる**（`inPortal=true` なら確定）。
- **ビルド**: 追加計器は `Chunk.isLoaded()` と `EntityPlayer.isInPortal()` が MCP 1.12.2 に無く2度失敗したため、
  存在が確実な `World.isBlockLoaded(BlockPos)` と `getBlockState(...)==Blocks.PORTAL` に置き換えた（**ビルド緑**）。

## 0.19 提示経路の軽量化: 余分な copyback を外し、**AMD では pure win**（Intel は +10.3 ms が残る）

- **内訳の切り分け**: 提示のたびに `beginFrame`/`endFrame` でフレームを枠取りしていた（＝**2回目の copyback とその待ち**）。
  通常フレームでは内容も copyback も既に済んでいるので、**枠を外して `presentImage` を直接呼ぶ**形にした
  （共有イメージは GL が宣言したレイアウトのままなので `VK_IMAGE_LAYOUT_GENERAL` を渡す）。
- **実測（`presentFromComposite` / framing の有無で比較、軽い世界）**:

| 構成 | AMD p50 / p95 | Intel p50 / p95 |
|---|---|---|
| 提示 OFF（GL swap） | 16.19 / 19.22 | 30.33 / 34.71 |
| 提示 ON・framing あり | 16.17 / 18.40 | 43.73 / 45.60 |
| **提示 ON・lean（今回）** | **13.07 / 16.07** | **40.62 / 42.21** |

  受入は AMD・Intel とも exit 0 / validationClean / **PASSED** / **VUID 0** / `presentFailures=0` /
  `presentResult=[present image: blitted 1280x720 onto image 1 (0)]`。
- **AMD は提示が純益**: **13.07 ms は「提示しない」16.19 ms より速い**（GL swap を省いた分が present のコストを上回る）。
- **Intel は +10.3 ms が残る**（30.33 → 40.62）。framing を外して削れたのは約3 ms なので、残りは
  **present 自体（image の取得と提示の待ち）**である。swapchain は **MAILBOX 優先（無ければ FIFO）・image 数は最小+1**。
  最有力の説明は **headless な X では提示済み image を消費する側がいないため取得が待つ**というもので、
  **実 display では同じコストとは限らない**（これは仮説であり、実 display での確認は未実施）。
- **次に残るもの**: 描画そのもの（GL/VK bridge・copyback・GL call）と GL context。

## 0.18 提示経路の限定回帰と**実測コスト**（AMD はゼロ、Intel は +13.4 ms/frame）

- **限定回帰（提示経路に触れる変更のため）**: すべて exit 0 / validationClean / **PASSED** / **VUID 0** / スクリーンショット有り /
  mixin 失敗なし。

| run | frames | p50 | `presentedFromVulkan` | `glSwapsSkipped` | `presentFailures` |
|---|---|---|---|---|---|
| AMD resize | 591 | 16.17 ms | **276** | **276** | 0 |
| AMD reload | 622 | 13.66 ms | **339** | **338** | 0 |
| AMD world switch | 640 | 12.97 ms | **352** | **351** | 0 |
| **Intel 通常** | 219 | 43.73 ms | **207** | **206** | 0 |

  **resize が通ること自体が、`@Redirect` 方式で resize 判定を残したことの確認**である（メソッド全体を cancel していたら落ちる）。

- **A/B（`-Dceltium.vulkan.presentFromComposite=false` で提示経路ごと切った比較）**:

| GPU | 提示 ON | 提示 OFF | 差 |
|---|---|---|---|
| **AMD** | p50 **16.17 ms** / p95 18.40 | p50 **16.19 ms** / p95 19.22 | **±0** |
| **Intel** | p50 **43.73 ms** / p95 45.60 | p50 **30.33 ms** / p95 34.71 | **+13.4 ms** |

  **AMD では提示のコストが測れない水準**、**Intel では 1 フレームあたり +13.4 ms** という**実測コスト**がある。
  正しさの問題ではない（全 run PASSED・VUID 0・presentFailures 0）。次の調査対象は
  **swapchain の present mode / image 数 / 待ちの構成**（FIFO 固定・image 不足・acquire/待機の直列化など）。

## 0.17 **GL swap の停止に成功**（通常フレームは Vulkan が提示し、GL は swap しない）

**前ターンの失敗の原因（完全に特定）**: `@Redirect` ハンドラが **レシーバ引数 `(Display)` を取っていた**が、
**`Display.update()` は static メソッド**なので引数を取ってはいけない。mixin config の **`defaultRequire: 1`** のため
これは**ハードエラー**になり、**ゲームは起動前に落ちていた**（失敗ログの `InvalidInjectionException ... Found 1 unexpected
additional method arguments: (Display)` がそのまま根拠）。**ハンドラを無引数にし、通す場合は `Display.update()` を静的に呼ぶ**
形にした（`require = 0`、失敗時は必ず元の swap を通す fail-open）。

**受入（AMD、軽い世界、duration 90 s）**: exit 0 / validationClean / **PASSED**、269 frames、p50 25.59 ms、**VUID 0**、
クラッシュなし、**mixin 適用失敗なし**、**スクリーンショット有り**（mean 113.423）。

| 計器 | 値 |
|---|---|
| `presentHookCalls` | 269 |
| **`presentedFromVulkan`** | **263** |
| **`glSwapsSkipped`** | **262** |
| `presentFailures` | 0 |
| `presentResult` | `present image: blitted 1280x720 onto image 2 (0)` |
| `presentSucceeded` / `presentSubmitDone` | true / true |

**意味**: **通常フレームの表示は Vulkan の swapchain が出し、GL の swap は行わない**（提示済みフレームにつき1回だけ省略・
resize 判定とイベント poll は維持）。**条件 (b) の「GL swap 依存」が通常フレームから外れた**。
**まだ GL に残るもの**: 描画そのもの（GL/VK bridge・copyback・GL call）と GL context。`presentedMean` と GL 側
スクリーンショット平均は**別の瞬間の値**なので内容一致の根拠には使わない（§0.16 の訂正どおり）。

## 0.16 GL swap の停止を試みて**失敗**し、巻き戻した（失敗の記録）

- **per-frame フックの正しい SRG 名が判明**: `Minecraft.updateDisplay()` = **`func_175601_h`**、その直後に呼ばれる
  resize 判定 = **`func_175604_i`**（`srg_patched_minecraft-sources.jar` から直接取得）。
- **試したこと**: `func_175601_h` 内の `Display.update()` 呼び出しを **`@Redirect`** で差し替え、**Vulkan が提示済みのフレームでは
  GL swap を行わない**（events は poll し、resize 判定は同じメソッドの残りとしてそのまま走る）形にした。
- **結果（実測1回・即失敗）**: **exit=1 / `MISSING_REPORT` / スクリーンショット無し / クラッシュログ無し**。
  **GL swap を省くとゲームがフレームを出せなくなる**（fixture が捕捉に到達しない）。原因は未特定
  （候補: swap を前提にしたゲーム側の待ち、`Display.update` が担うイベント/バッファ処理、fixture の捕捉段階）。
- **対応**: **失敗した変更は保持せず巻き戻した**（goal の「停止や未到達を完了扱いしない」「失敗時に原因を追える単位を保つ」）。
  巻き戻し後に**同じ条件で再受入**: exit 0 / clean / **PASSED**、240 frames、p50 28.06 ms、**VUID 0**、
  **`presentedFromVulkan=235`／`presentAttempts=235`／`presentFailures=0`**、
  `presentResult=[present image: blitted 1280x720 onto image 1 (0)]`、`presentSucceeded=true`。
  **確立済みの「通常フレームの Vulkan 提示」は壊れていない**ことを確認した。
- **§0.15 の記述の訂正**: 「提示画像の平均 113.25 と GL スクリーンショット平均 113.874 が一致＝独立した裏付け」は
  **言い過ぎ**である。両者は**別の瞬間に取得**される（提示 readback は最後の提示時、スクリーンショットは捕捉時）ため、
  今回の再受入では 140.0 対 114.1 と離れた。**同じ瞬間の比較**（提示 readback を捕捉フレームで取る）を用意するまでは、
  内容一致の裏付けとして扱わない。

## 0.15 **通常フレームが Vulkan から提示されるようになった**（条件 (c) の中核受入）

**根因（このターンの本体）**: 「present を有効にするか」という**同じ判定が3箇所に別々に実装**され、既定が食い違っていた。
- backend 側は `!"false".equalsIgnoreCase(getProperty("presentFromComposite","true"))` ＝ **既定 true**、
- `VulkanContext` の2箇所（インスタンス拡張・デバイス拡張）は `Boolean.getBoolean(...)` ＝ **既定 false**、
- `VulkanSwapchain` の受理判定も `Boolean.getBoolean(...)` ＝ **既定 false**。
結果、**デバイスが `VK_KHR_swapchain` 無しで作られ**、swapchain は「unavailable」で永久に作られなかった。
**既定を1つに揃えた**（backend と同じ「明示的に false のときだけ無効」）。

**併せて直した到達性の問題（すべて実測で特定）**:
1. `celtium$presentFrame()` の per-frame 呼び出し点: 最初は `Minecraft.updateDisplay`（SRG に存在せず）→ 次に
   `func_175068_a` HEAD に付けたが**ハンドラの署名が `(CallbackInfo)` だけ**で対象メソッド
   `(int pass, float partialTicks, long finishTimeNano)` と不一致 → **`require=0` のため無言で適用されず**。
   署名を合わせて解決（**コンパイル済みクラスに注釈が入っていることは `javap` で確認した**）。
2. 入口の早期 return を「フレーム開始でリセットされる `frameSubmitReachedQueue`」で判定していた → **専用の
   `lastFrameReachedQueue`（submit 時に立ち、提示で消費）** に分離。
3. **swapchain を作る呼び出しが無かった**: `ensureSwapchain()` は「present する側が呼ぶ」と自分で書いてあるのに、
   私の入口が呼んでいなかった（プローブだけが呼んでいた）。
4. **計器の誤読を1つ訂正**: `presentAttempts=0` は「フックが発火していない」ことを意味しない（attempt は
   `presentImage` の後でしか増えない）。**入口で無条件に増える `presentHookCalls`** を足したことで、
   フックは 245/237 回**到達している**ことと、理由が `swapchain unavailable` であることが分離できた。

**受入（AMD、軽い世界、duration 90 s）**: exit 0 / validationClean / **PASSED**、237 frames、p50 26.75 ms、**VUID 0**、クラッシュなし。

| 計器 | 値 |
|---|---|
| `presentHookCalls` | **237**（毎フレーム到達） |
| `presentedFromVulkan` / `presentAttempts` | **233 / 233**（`presentFailures=0`） |
| `presentSkipReason` | **`presented`** |
| `presentResult` | **`present image: blitted 1280x720 onto image 0 (0)`** |
| `presentSucceeded` / `presentSubmitDone` | **true / true** |
| **`presentedMean`** | **113.25** |
| **GL スクリーンショットの平均** | **113.874** |

**提示画像の平均（113.25）と GL 側フレームの平均（113.874）が一致** — **Vulkan の swapchain に出た画像が、このフレームの
内容そのもの**であることの独立した裏付けである（差 0.6/255）。

**まだ言わないこと**: **GL swap は止めていない**（両方が提示する。内容は同一なので実測での破綻は無いが、機構としては次段階）。
**実 window/compositor 出力の到達**は未確認（`DISPLAY=:0` の root は 0×0）。起動直後の数フレームは提示しない（237 中 233）。

## 0.14 通常フレームからの Vulkan present に着手: 提示の入口は実装、**per-frame の呼び出し点が未解決**

- **判明した bridge の全体像**（`VulkanFrameTarget` の設計コメントより）: Vulkan 所有の RGBA8 色イメージと D32 深度イメージを
  **外部メモリ FD で GL に import** し、GL はその FBO に描き、Vulkan が所有権を受け取り、**`endFrame()` で GL が共有イメージを
  ゲームの FBO へ blit バック**（copyback）し、最後に **GL が swap** する。**present の配線・marker・readback は既にある**。
- **実装したもの**: `celtium$presentFrame()`（フレーム内容が共有イメージにあるとき `beginFrame → presentImage → endFrame`
  の実測済み手順で提示し、`presentedFromVulkan`／`presentAttempts`／`presentFailures`／`presentResult` を計器として出し、
  成功時は GLFW のイベントを自前で poll する）。提示位置は「フレーム先頭で直前のフレームを提示」とした
  （`renderWorld` の RETURN は **GUI 描画より前**なので、GUI を含む完成フレームにはならない）。
- **未解決（実測2回）**: **per-frame の呼び出し点が発火しない**。`Minecraft.updateDisplay` は SRG に存在せず
  （`forge.srg` に該当なし）、既存で動作している名前 `func_175068_a` の HEAD に付けても **`presentAttempts=0`** のまま
  （＝条件分岐ではなく**メソッド自体が呼ばれていない**。条件を変えても数値が動かなかったことがその証拠）。
  **SRG 名を `srg_merged_minecraft.jar` のバイトコードから解決するのが次の一手**。
- **状態**: 提示の入口と計器は保持（契約は明確・未使用）。**GL swap は触っていない**ので表示の退行は無い
  （`presentedFromVulkan=0` のまま、スクリーンショットは従来どおり GL 側で mean 113 前後）。
  **「通常フレームが Vulkan から提示された」とは言わない**。

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
