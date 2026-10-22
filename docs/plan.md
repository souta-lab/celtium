# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.8-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 次の作業単位（2026-10-22 改訂・利用者指示）

1. **性能（最優先）**: 出荷構成は影なし。参照実装に対する AMD 1.387x / Intel 1.738x を 1.0 に近づける。
   受け入れの算術（`architecture` §11/§12）では受理経路の内側の最適化では届かず、per-offer のJava仕事を消す再設計が要る。
2. **影・パックは Demonica の fork で**: 利用者指示により **Iris を設計参照にするのをやめ、Demonica を fork して Celtium 用にする**。
   Demonica の実体（pinned commit の README より）: **Iris の shader pipeline + Angelica の GLSM + 互換層**を、別途導入の Celeritas の上で動かすもの。
   Actinium から fork し terrain renderer は持たない。**Cleanroom の OpenGL context 上で動く**ため、Celtium では
   「Celeritas との接続面（terrain 描画・pack pipeline・GLSM）」を **Celtium の Vulkan 経路との接続面に置き換える**作業になる。
   **別成果物（addon jar）**として実装し、Celtium core には取り込まない（`docs/shader-plan.md` の方針）。
3. **組込影は scaffold に降格（既定OFF）**: `ShadowMapPass` は native 深度ターゲットと投影の検証用。既定 `false`。
4. **ライセンス**: Demonica 本体は **AGPL-3.0**（〜0.4.0 は GPL-3.0）。Angelica/Iris 由来部分は LGPL-3.0、Actinium 由来は GPL-3.0、
   glsl-transformer は AGPL-3.0。**fork するなら fork 側も AGPL-3.0 になり、Celtium core とは別リポジトリ/別jarで境界を保つ**。
   LGPL 部分のチェリーピックはクレジット明記で可能（利用者指示③）。GLSM の丸ごと持ち込みはしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **v0.0.7-alpha をリリース（2026-10-22）** | **影の既定ONが主題** | 実測 | 公開済み・両GPU検証済 | https://github.com/souta-lab/celtium/releases/tag/v0.0.7-alpha 。asset `celtium-forge-mc1.12.2-0.0.7-alpha.jar`（3,201,172 bytes）は**ダウンロードしたsha256がローカルビルドと一致**（`7e3b6783…`）。梱包 `verified: true`・版数整合 `verified: True`・内容ハッシュ1079エントリ。ノートに**影ON/OFFの実測（AMD 0.92%・Intel 2.17%）**、アロケーション−41%、S0/S1/S2のパック状況、既知の問題（性能1.674xと尾・パック未接続・RLCraftはFugue待ち）を明記。**公開jarを両GPUで検証**: AMD/Intel とも world leg **PASSED・画像ゲート PASSED・validation 0件**（Intel は jar hash `7e3b6783…` 一致を確認）。**公開jarの測定**（負荷3.2-4.7・強制・交互5ペア）: **AMD 1.400x**（中央値。影OFF構成は1.387x＝この負荷帯では影の上乗せは小さい） |

| **性能（現状・影削除後）** | **AMD 1.249x（最良・静かな時間帯）** | 実測 | 未達 | jar `6150c7a1…`・影なし構成・交互4ペア・負荷3→3.9: **Celtium 1.610-1.681ms vs 参照 1.251-1.377ms＝1.249x**、p95 2.2-2.5 vs 1.6-2.6。同じ手法の以前の値は 1.387x（影あり）→1.350x（影削除後・負荷5-6）で、機械の静穏でここまで下がった。**負荷依存が大きいので比は時間帯と併記**。個別の改善は ①モデルキャッシュ世代ゲート（0.969x）②**受理時のテクスチャ解決**（記録時のper-group Map参照を除去、0.989x）。残る構造課題は `architecture` §11.5（状態をgroupから剥がす＝descriptor配列＋per-draw SSBO） |
| **影（pack接続）** | **未接続＝影が出ない（最大の製品ギャップ）** | 未達 | 着手 | native影は削除済み（利用者指示）。影を出す唯一の道はシェーダーパック（`shader-plan.md` §0 の Demonica fork 方針）。現状は S0/S1（解析・コンパイル・program単位のinterface統合）までで**ワールド描画へ未接続**。次段: packの `shadow`/`shadowtex` program を Celtium の描画グラフに差し込む（target/flip管理は pack 側の設計に従う） |

| **性能: draw-manager（§11.5-11.8）** | **土台3/5段（①②③完了）** | 実測+画像 | 実装中 | ①descriptor indexing 4機能を両GPUで有効（`textureArray=true`・PASSED/0件, jar `3e3dc1a5`） ②共有テクスチャ配列セット実装＝**35枚/4096・書き込み35回**（jar `6cf90a00`） ③groupへ配列index＝**hit 178407/miss0（AMD）・hit 114673/miss0（Intel）**（jar `75bea5b3`） 残り＝④シェーダをindex化（まずtexture系1 family、push定数index）⑤pipelineソート＋geometry arena＋`vkCmdDrawIndexedIndirect`で **159→約11 draw/window**。**①②③は未だ描画に使わず速度未計測**。影削除を含む現ツリーは jar `0b3b49d8…` で AMD/Intel とも PASSED・画像gate PASSED・validation 0件 |

| **影: native実装を完全削除（2026-10-22）** | **コードから消えた（両GPU検証済み）** | 実測＋画像 | 完了 | 利用者指示「native影実装消して」。受信シェーダ全系統・backendの影パス一式・カメラ位置フック（mixin含む）・`ShadowMapPass`本体・専用シェーダ・パイプライン3本のプレースホルダ/binding/spec定数/fan-out・`TerrainRenderer`の簿記（range捕捉・indirect捕捉・カメラ位置フィールド）を削除。**AMD/Intel とも PASSED・画像gate PASSED・validation 0件**（jar `398533cd…`）。残るのはコメント1箇所のみ。影とパックは Demonica fork（`shader-plan.md` §0）が持つ |
| **互換・画質・配布** | **4画面＋qmagnet／RLCraftは環境制約／配布物検証済み** | 実測 | 一部未完 | 空(165,180,207)vs(165,179,207)・石は完全一致・草±4（worldTime=6000）。settings はHEADで再合格、**home のVUID-09600は再現せず**、mainMenuのgate FAILは**影OFFでも同一**（既存）。**RLCraft 2.9.3 は0.6.13でも0.7.11でも `com.cleanroommc.hackery.ReflectionHackery` 欠落で起動不能（Fugue更新待ち）**。配布物は `verify_forge_jar` verified・版数一致・内容ハッシュ一致。**BSLはワールド描画に未接続**（`shader-plan.md` S0/S1） |
| **シェーダパック: S0/S1/S2パイプラインまで（2026-10-22）** | **S2のパイプライン生成OK** | 実測 | 残りは描画接続 | S0: `files=292 programs=94 options=402 errors=0 loadMillis=515`。**フラグ必須**: `-Dceltium.shaderpack.compileProbe=true`（未設定だとprobeが呼ばれない）。S1: **6プログラム全て `result=OK`**（`gbuffers_basic.vsh` lowered=14,248 `spirvWords=1402`、module/descriptorLayout/pipelineLayout すべてok）。**S2**: **`CELTIUM_PACK_PIPELINE program=gbuffers_basic pipeline=ok attributes=7 result=OK`**＝**実フレームのformat（R8G8B8A8+D32）向けのグラフィックスパイプライン生成に成功**。残りは**ワールド描画への接続**（このパイプラインでterrain/immediateを描き、set 0のuniformを供給する） |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない |
| **次の標的** | **native実装へ** | — | 未着手 | ①**受理経路をnativeへ**（Java補足ではなく所有権移動。`architecture` §12が規模の算術）②影の尾の削減（二重バッファ化 or cadenceと品質のトレード）③`shader-plan.md` の既存modベースでBSL接続 ④state/texture/render target所有のnative化（未完） |
| **測定の現状** | **出荷構成のフレーム内訳を実測** | 実測 | 継続 | leg `perf-struct-1`（jar `a78b1f53…`・影OFF・CPUクロック・instrument ON）: `frame p50 2.5ms / world 2.4-2.5`、内訳は **winRecord 0.6（flush 0.7 の内側）・terrainRecord 0.2（terrain 0.8 の内側）**、`terrainSubmit 0.0・present 0.0・gui 0.1・clouds 0.1`、**worldの残差 約0.8msは未bracket**。※以前「passSplitが無出力」と書いたのは私のgrepミス（正しくは `CELTIUM_FRAME_PASS_SPLIT`）。計器は正常。 |

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

## 再開手順（2026-10-22 時点の実コマンド）

ビルド（GradleはJDK25で起動、ゲームはJDK27）:
```
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew -p build-1122 \
  :celtium-vulkan:test :forge122:packageJar :forge122:validationRemapJar --console=plain \
  '-Porg.gradle.java.installations.paths=/usr/lib/jvm/java-21-openjdk-amd64,/home/s0u7a/projects/celtium/.celtium-work/jdks/8/jdk8u504-b01'
```
leg（world・影ON既定）:
```
CELTIUM_INSTANCE_WORLD=$HOME/.local/share/PrismLauncher/instances/cleanroom-0.7.11-alpha \
CELTIUM_LEG_LWJGL_VULKAN=$PWD/.gv-verify/lib/lwjgl-vulkan-3.4.3.jar \
scripts/gv-leg.sh <leg名> .gv-verify/jars/<stage> world {amd|intel} 150 \
  -Dorg.lwjgl.librarypath=$PWD/.gv-verify/lib/lwjgl-natives-3.4.3 \
  -Dceltium.validation.fixedWorldTime=true -Dceltium.validation.worldTime=1500
```
- `.gv-verify/jars/<stage>` には **forge jar と validation-client jar の2つ**を置き、`build-provenance.json` を作る（KPIは両方を要求）
- **stage名・leg名の再利用は不可**（"artifact already exists" でFAIL）
- 影は native 実装を削除済み（pack 側の影は Demonica fork で実装する）。比較画像は presented dump の最終枚を使う
- パック: `-Dceltium.shaderpack.path=<zip>` ＋ `-Dceltium.shaderpack.compileProbe=true`、shaderc は `CELTIUM_LEG_EXTRA_LIBS=.gv-verify/lib/lwjgl-shaderc-3.4.3.jar`
- ディスク: `.gv-verify/run-*` と `stage-*` は毎回消す（16G→405M、空き18G）
