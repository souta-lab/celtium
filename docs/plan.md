# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-17更新。再監査基点 `c46474aee`、resize追試ソース `f0e02f8a3`、loading追試ソース `438266524`（artifactは各証拠を参照）。
resize / loading行のみ新規の実機結果。他の行は従来の記録であり、一括再受入ではない。
以下は特記なき限り**各leg当時のartifactでの記録**であり、最新HEAD全体の再受入ではない。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在状態・優先順・受入

「実装済み」は実機成功と別。AMD成功 / Intel成功の両方が、同じ変更の必要な範囲で揃った場合だけ両GPU成功。
未較正は判定不能、FAILEDは失敗、未完・未検証は成功でない。
証拠の抜粋・full hash・flags・cameraは [監査証拠](<evidence/process-audit-2026-10-17.json>)。

| 優先・項目 | 実装/判定 | AMD受入 | Intel受入 | 根拠と残り |
|---|---|---|---|---|| **P1 FPS の内訳確定（2026-10-20実測）** | **p95/スパイクの主因はG1 GCと確定** | 診断値 | 未検証 | 検証税OFFのKPIペア: celtium p50/p95 **3.201/8.014ms** vs celeritas **1.416/2.029ms** = **2.26x**。棄却済み: present律速（acquire28ms/601・queue38ms/601）✗ / GPU律速（1.4〜2.5ms）✗ / arena枯渇（8MiBで比2.30）✗ / descriptor pool成長（ログ無し）✗。確定: present 6.9msの実体は**readback+dump＝検証税**（`presentedDumpEvery=0`で11.2→8.5ms）。**`-Xlog:gc`（`gv-gc1`）で131停止・合計2165ms・max404ms・p95 24.9ms＝frame p95 18.6msを上回る** ⇒ tailはアロケーション由来。次はホットパスのallocation除去 追記(GC量): 131停止/180s、停止間隔 **median 0.46s**、1停止で **251M->17M** 回収 ⇒ **≈5MB/frame のゴミ**。`celtium$fogSnapshot()` の float[8]/group は ~9KB/frame（全体の0.2%）で**効果なし**と判明、標的は mesh/buffer/一時イメージ級の大きな確保 追記(KPI再現性): 3ペアで ratio **2.26 / 2.30 / 2.29**（p50 celtium 3.20-3.74 vs celeritas 1.40-1.62）。マシン状態が違っても比は安定 ⇒ **以後の最適化はこの2.29xを基準に有意差判定できる**。アロケーション源はモデルキャプチャ(114 quad)・即時グループ(16/frame)では桁が足りず**依然未特定** 【訂正 2026-10-20 23:04】GCは**両arm共通**と判明：同一run・同一sceneで celtium 128停止/2018ms/max313.6/p95 27.7 に対し **celeritas 128停止/1925ms/max280.5/p95 24.0**（ほぼ同一）。⇒ **GCはCeltium固有でなく、2.29xの説明にならない**（前述のGC主因説を撤回）。差はGC以外のCPU作業＝GLスタブ/即時batch経路に由来 | 【棄却 2026-10-20 23:3x】`prepareImmediate` のワンスロットキャッシュは**ヒット率7.7%（1384/17889, gv-pcache-hit1）**で成立せず撤回。連続バッチは format/テクスチャ/stateKey/pass の5項目が揃って一致しない（記述子キャッシュが効いたのはキーが「テクスチャのみ」で粗かったため）。KPIも 2.29→2.42x でノイズ帯、改善なし。
| **転換計画を architecture-2026-10-21.md に固定（2026-10-21）** | **計画** | — | — | [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。Java frontend→DrawIntent収集→順序保存バッチ→native の4層。S0=測定の修復（JAR固定・warm window・全発行点のdraw計数）、S1=バニラ捕捉をスタブ非経由へ、S2=順序保存バッチ実行、S3=フレーム予算、S4=互換/目視、S5=影/BSL/配布。**S0 が通るまで S1 以降へ進まない**（現KPIは測定として不成立） |
| **KPI が同一jarの再測定だった（2026-10-21 判明）** | **測定無効** | 4 leg のmanifest | — | `scripts/gv-kpi.sh` の既定 `CELTIUM_KPI_JARS=.gv-verify/jars/batchfs` の中身は **2026-10-20 21:34 の jar**（sha `ca77e4a5…`）で、実際のビルド出力は `forge122/build/libs/` の **2026-10-21 08:09**。`kpicheck1` / `affine2` / `pcache1` / `physplit1` の4 leg は**すべて同じ sha** を測っており、**変更後JARのA/Bが成立していない**。⇒ 「seam群の非退行 2.30x」「affine bake 2.28x=効果なし」は**同一物の再測定＝ノイズ**であり結論を撤回。以後KPIは `CELTIUM_KPI_JARS` に**そのHEADのビルドを明示stageしたディレクトリ**を渡し、manifest の `artifacts.renderer.sha256` と `git.head`/`dirty` を記録して一致を確認する |
| **KPI は cold window（チャンク構築中）で判定している（2026-10-21 判明）** | **判定条件の欠陥** | fixture 実装 | — | `frameIntervalP50Ms` は `worldTicks 120..200`＝**チャンク構築ウィンドウ**での採取（`ValidationClient.java:5245`）。steady state は `frameIntervalWarmP50Ms`（`warmFromTick=400..600`）だが、100秒legでは 400 tick に届かず**`frameIntervalWarmSampleCount=0`**（`physplit1-celtium-r1` で実測）。⇒ 現在の 2.29x は**起動直後の値**であり、「FPSがCeleritasを上回る」の判定には使えない。受入は warm window で取り、leg 時間を warmTo に届く長さへ伸ばす |
| **計数の範囲を訂正（2026-10-21）** | **前提を撤回** | 計器付きleg | — | `DrawCounters` は `VulkanDynamicMesh.bindAndDraw`/`draw` の**2点だけ**を数えており、`TerrainRenderer.celtium$drawIndexedRun`（地形の逐次draw）と `tryIndirectBatch`、`ShadowMapPass` の `vkCmdDrawIndexedIndirect` は**計数していない**。したがって「1フレーム6〜9 draw」「1:1変換は存在しない」は**部分計数**であり結論に使えない（撤回）。`multiDrawGroups=0` も同じjar・同条件の再測定。全面のdraw数は『計器を全発行点へ広げてから』でなければ語らない |
| **主経路をCPU frontend→native Vulkanへ転換（2026-10-20 利用者指示）** | **方針転換・実装開始前** | 未検証 | 未検証 | GL→VKの逐一変換を主経路から外し、Celeritas/Pintonium系CPU frontend→native Vulkanへ移す。GLスタブはHUD/GUIのfallbackへ縮小。既存の計測が根拠：per-batch約3.5us×約290 batch＝frame 3.3msのうち約1ms、hotspot無し（`bcc6abccf`）、batch列はframe間で非再現（`3cde87ebb`）＝「安くする」の余地は尽き、「回数を減らす」しかない |
| **GLスタブの担当範囲を確定（2026-10-20 利用者指示）** | **方針確定・実装は未完** | 未検証 | 未検証 | スタブが受けてよいのは「Modが直接呼ぶGL」だけ。**HUD/GUIもバニラ側なので原則スタブ非経由**で、未捕捉経路の残留は欠陥として数える（GUI矩形は`Tessellator`経由で`WorldVertexBufferUploader` mixinが捕捉、フォントは`CeltiumFontGlyphCapture`のfast glyph経路がquadを直接nativeへ渡す＝機構は既にある）。バニラ／Forge APIはJavaレベルで捕捉してnativeへ渡し、スタブを通さない（[refactoring-plan §3-7](<refactoring-plan.md>)）。実測（`gv-pcache-hit1`, AMD, cohort, PASSED clean）：バニラ即時描画は `WorldVertexBufferUploader` mixin が **12,500件**捕捉しスタブ非経由（format 6:4691 / 7:4476 / 4:1927 が主）。**残る違反は `glCallList` 610回（モデルのdisplay list再生）とスタブ側 `glDrawArrays` 1024回**。次の単位＝モデルのdisplay list再生を止める（`VulkanModelQuadCapture` の captured が114件に留まる理由を計器で特定して埋める）。受入は上記2計器を0へ近づけること |
| **方向性の見直し（2026-10-21）** | **分析完了・実装は次段** | — | — | [direction-2026-10-21.md](<direction-2026-10-21.md>)。god class 18,209行/980 fields、計器 `CELTIUM_` 309種・println 385・スイッチ93。実測は「GPU 1.4ms で余り、負けは呼び出し単位変換（3.5µs×290≒1ms）」を指す。転換＝①エミュレータ凍結と境界法定化 ②地形以外をモデル単位batching（Sulfide/Rentities 設計）③フレームを明示パスで取る（MC-311557 の轍を避け十数パス以内） |
| **捕捉したバニラ即時描画をパス単位でまとめる（次の実装単位）** | **未着手** | 未検証 | 未検証 | 現状は `Tessellator.draw()` 1回ごとに1グループ（実測 約3.5µs/batch × 約290/frame ≒ 1ms）を作っており、捕捉はできていても**呼び出し単位の変換コストを払い続けている**。対策は (format, texture, blend, matrix) を粗いキーにしたパス内アキュムレータへ頂点を積み、run あたり1 draw にすること。参考設計（コード流用はしない）：VulkanMod の `render/gui/GuiBatchRenderer`（GUIを自前バッチで描く）と `LevelRendererMixin` の `submitBlockEntities` cancel、ImmediatelyFast の `avoid_redundant_framebuffer_switching`。受入は KPI ペア（基準2.29x）と、「1フレームの即時batch数」の低下で判定する |
| **Phase B 試行1: 一般アフィン bake（2026-10-21）** | **判断不能→revert** | — | — | `copyVerticesTransformed` を軸平行限定から**一般アフィン**へ拡張し、回転する entity も pre-transformed グループに統合できるようにした（`celtium$affineModelView`）。leg `gv-affine1` は PASSED/clean だったが、frame 数と scene が基準legと異なり（512 vs 400 frame）比較不能。frame正規化で matrixSplits 5.15→5.97、fastMerged 12.4→13.7 と方向が割れた。⇒ **未実証のため revert**。再試行するなら同一duration・同一sceneでA/Bしてから入れる |
| — | 過去の行（確定済み6件）は [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>) に移した | — | — | plan.md の入口予算16KiBのため |
## 性能の観測（受入・原因確定ではない）

- **同fixture `frameIntervalP50`（RX6400・terrain）**: **Celeritas GL `2.5.0-dev`(`567dcf8a`、pinned `989ebf2e`ではない)=7.87ms**（`perf-cel4-amd` PASSED、2348f）vs **Celtium=32.8-37.7ms**（`loopres2/unified3-amd`）。fixture反射化(`af7ce927e`)で両側に載る。intervalはtick増幅+fixture税で自己平衡する—**renderer作業の比較はbracket**。
- **bracket内分解**（`passSplit[Cpu]`、loopres2-amd, `batchFrameSubmits`）: frame p50 ~6.2-7.9ms（中: terrain 3.3+setupTerrain 1.4+flush 1.6、wall−cpuはGC+preemption）。`f2f573de6`で~9→~6-8ms（下記unified）。
- **unified frame buffer**（flag `-Dceltium.vulkan.batchFrameSubmits`）: frame全録画を1 buffer+1 deferred submitへ。同jar A/B（`d8d4d25c`）: frame bracket 9.1→~6.2ms、queueSubmitCalls ~11.9→3.6/frame、warmP50 41.1→28.8、VUID=0（depth-bias dedupはsegment先頭でinvalidate）。present readbackがframe全実行を直列wait（+3.7ms）するのはfixture側のみ。
- **swap点分解**: present ~11.7ms/call、周期dump ~9ms（`=0`で3.4ms/call）、readback ~0.6ms（**fixture税**）効の~8割**。
- 残荷（出荷構成・入れ子に注意）: **即時パス ~2.2ms**（offer 0.7 + 記録0.95 + window固定~0.4。`terrain` 0.9の0.7は**内側の即時flush**、terrain自身は~0.2/call）、setupTerrain 0.6、gui 0.3、残りloop/present対象外）。`2× Celeritas`はintervalの自平衡性上interval指標では届かない—renderer-work比較で ~6.4 vs ~6.2ms ≈ ほぼparity。

性能測定の正しいキーと残存負荷は [開発・検証手順](<development.md>)。READMEに別の現行性能表を作らない。

## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
  途中経過はローカルか既存Issue/PRへ。失敗・未較正・欠測を説明で成功に変えない。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [手順](<development.md>) /
  [計器索引](<instruments.md>) / [検証の罠](<verification-traps.md>) / [再監査](<audit-2026-10-17-sol-review.md>)。
- 旧表は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、旧日記は [diary](<history/plan-diary.md>)。
  再監査以前の「確定」「税なし」「両GPU成功」には訂正がある。履歴を現在の命令/判定として読み戻さない。
- 古い「plan §0.xxx」は現行の節ではない。[旧handoff](<handoff-2026-10-10-next-model.md>)や履歴を必要な箇所だけ参照する。
  現行判断は原典・artifact・実測結果へ戻す。

<!-- 2026-10-21 の詳細な試行記録は各commitメッセージと docs/direction-2026-10-21.md に移した（plan.md は現在表のみ）。 -->
