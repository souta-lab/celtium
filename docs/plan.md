# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.7-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 次の作業単位（2026-10-22）

1. **受理経路を native へ**: Java 側の補足ではなく所有権ごと移す（規模の算術は `architecture` §12）。影ONの尾（p95）も同じ作業で扱う。
2. **影の品質と尾**: `shader-plan.md` の既存modベース方針で改善し、二重バッファ化か cadence で尾を削る。
3. **シェーダパック**: `celtium$probePackProgram` からワールド描画へ接続（S0/S1 の設計に従う）。
4. **測定**: 機械が空いたら出荷構成（影ON）で `gv_kpi.py` を再実行し、5ペア以上で比を取る。


## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **v0.0.7-alpha をリリース（2026-10-22）** | **影の既定ONが主題** | 実測 | 公開済み | https://github.com/souta-lab/celtium/releases/tag/v0.0.7-alpha 。asset `celtium-forge-mc1.12.2-0.0.7-alpha.jar`（3,201,172 bytes）は**ダウンロードしたsha256がローカルビルドと一致**（`7e3b6783…`）。梱包 `verified: true`・版数整合 `verified: True`・内容ハッシュ1079エントリ。ノートに**影ON/OFFの実測（AMD 0.92%・Intel 2.17%）**、アロケーション−41%、S0/S1/S2のパック状況、既知の問題（性能1.674xと尾・パック未接続・RLCraftはFugue待ち）を明記。**公開jarの測定**（負荷3.2-4.7・強制・交互5ペア）: **AMD 1.400x**（中央値。影OFF構成は1.387x＝この負荷帯では影の上乗せは小さい） |

| **性能（現状）** | **AMD 1.387x / Intel 1.738x（影OFF）／影ONはAMD 1.674x** | 実測 | 未達 | 参照 `567dcf8a…`・交互ペア。影OFFのAMDは 1.373-1.387x で安定、Intelは `e4622406…` で 1.738x。**影ON（出荷既定）は 1.674x**（影の実コスト約0.32ms、`noPass` 2.132／`noshadow` 2.123 で切り分け済み）。**Java名付き最適化9件は全て中立**＝Java補足では動かない（`architecture` §11/§12）。**アロケーションは13.8GB→8.1GB/run（−41%）**、GC 50→39（行列プール・fog比較・モデル幾何の再利用） |
| **影（既定ON）** | **両GPU可視・validation 0件** | 実測＋画像 | 受入 | 同一scene・worldTime固定のON/OFFで **AMD 0.92%（平均19.1）/ Intel 2.17%（平均48.6）** の画素が>6レベル暗くなる。既定ONでもAMD/Intel とも **PASSED・gate PASSED・validation 0件**。**IntelのVUID-03047は地形`bindTextures`の毎bind再書き込みが原因で、変更分のみ書く方式にして解消**。スキップ時にキャスタを片付けない**蓄積バグも修正**。残課題は**尾**（p95 5.5-14ms）。**「既存modベース」の具体化**: リポジトリ同梱の Iris 実装（`modern/.../iris/shadows/`）と要素単位で対応表を作成（`architecture` §13.1）。現状は**自前実装**で、**パック宣言値の読み取りを実装**: `CELTIUM_SHADOW_PACK declared resolution=3072 distance=512.0 applied resolution=2048 distance=48`＝BSLの上限（ULTRA）を読み、現状との差を毎回ログ（`ShaderPackLoader.shadowSettings`）。適用はdistortion導入後。優先順は①distortion（half-plane）②解像度・距離の適用③entity casters④shadowtex color target |
| **互換・画質・配布** | **4画面＋qmagnet／RLCraftは環境制約／配布物検証済み** | 実測 | 一部未完 | 空(165,180,207)vs(165,179,207)・石は完全一致・草±4（worldTime=6000）。settings はHEADで再合格、**home のVUID-09600は再現せず**、mainMenuのgate FAILは**影OFFでも同一**（既存）。**RLCraft 2.9.3 は0.6.13でも0.7.11でも `com.cleanroommc.hackery.ReflectionHackery` 欠落で起動不能（Fugue更新待ち）**。配布物は `verify_forge_jar` verified・版数一致・内容ハッシュ一致。**BSLはワールド描画に未接続**（`shader-plan.md` S0/S1） |
| **シェーダパック: S0/S1/S2パイプラインまで（2026-10-22）** | **S2のパイプライン生成OK** | 実測 | 残りは描画接続 | S0: `files=292 programs=94 options=402 errors=0 loadMillis=515`。**フラグ必須**: `-Dceltium.shaderpack.compileProbe=true`（未設定だとprobeが呼ばれない）。S1: **6プログラム全て `result=OK`**（`gbuffers_basic.vsh` lowered=14,248 `spirvWords=1402`、module/descriptorLayout/pipelineLayout すべてok）。**S2**: **`CELTIUM_PACK_PIPELINE program=gbuffers_basic pipeline=ok attributes=7 result=OK`**＝**実フレームのformat（R8G8B8A8+D32）向けのグラフィックスパイプライン生成に成功**。残りは**ワールド描画への接続**（このパイプラインでterrain/immediateを描き、set 0のuniformを供給する） |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない |
| **次の標的** | **native実装へ** | — | 未着手 | ①**受理経路をnativeへ**（Java補足ではなく所有権移動。`architecture` §12が規模の算術）②影の尾の削減（二重バッファ化 or cadenceと品質のトレード）③`shader-plan.md` の既存modベースでBSL接続 ④state/texture/render target所有のnative化（未完） |
| **測定の現状** | **フレーム時間は保留** | — | 欠測 | 機械負荷が5-8でKPI閾値2.0を大きく超え、単発比較は負荷を測ってしまう。**JFR（アロケーション・共有）とvalidation/gate/画像は有効**。共有値はA/Bで2件連続否定済み＝探索にのみ使う |

## 撤回した前提（再入条件つき）

| 主張 | 判定 | 再入条件 |
|---|---|---|
| KPI 2.29x が変更後の比較 | **無効**。4 legが同一 sha `ca77e4a5…`（2026-10-20 21:34）を測定 | 今回の`gv_kpi.py`でprovenance一致を確認してから |
| 2.29x が FPS 判定 | **無効**。`frameIntervalP50Ms` はworldTicks 120..200＝チャンク構築中 | warm区間を完走したlegのみ |
| 「draw 6〜9/frame＝1:1変換なし」 | **無効**。`VulkanDynamicMesh` の2点だけの部分計数 | 全発行点の合計を計数してから |
| 「per-call最適化の余地は尽きた」 | **撤回**。同一条件のA/Bが成立していない | provenance一致のpaired測定 |
| 「GCがp95の主因」 | **撤回**。両armほぼ同一（停止数・時間） | 同一scene・同一区間での再測 |
| 「GLスタブ経由は解消済み」 | **撤回**。`CeltiumNativeDraw` はスタブを直接呼んでいた。今回owner移行を開始 | 各入口の所有者がnativeになった時点で個別に判定 |

## 未確定（数値を前提にしない）

- **Intel性能は未測定**（AMDのみ）。同一hash・同一条件のIntel paired測定を取るまで両GPU優位を主張しない。
- tail（p95）が3.8倍なのにp50が1.70倍という差は、平均的な変換コストより**スパイク源**が競争力を決めることを示す。次の判別はwarm区間のframe毎内訳とアップロード/GC/待ちの同時記録であり、推測で1つに絞らない。
- GPU pass時間、present/readback、アップロード量、GC、フレーム内訳の旧値は、artifact/区間が今回のビルドと一致しないため参考値としても使わない。
- validation clean・`exitCode=0`・色数gateは、それぞれ描画不存在・正しい絵・画像一致を証明しない。

## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [設計](<architecture-2026-10-21.md>) /
  [手順](<development.md>) / [計器索引](<instruments.md>) / [検証の罠](<verification-traps.md>)。
- 計測の生ログと棄却済みの詳細（`duplicate()`除去の反証、packet内訳の旧版、1.70x/1.49xの履歴）は
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)（packet loopの撤回・validation層による膨張の記録もここ）。
- 旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)、[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。
