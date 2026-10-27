# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.18-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開最新版は **v0.0.18-alpha**（2026-10-25、[release notes](<releases/0.0.18-alpha.md>)）。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、目標設計は [new_architecture.md](<new_architecture.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 2026-10-24: 新アーキテクチャへ全面移行（利用者指示）

目標設計は [new_architecture.md](<new_architecture.md>)（scene database・Native Everything Culling・frame compiler・
Vulkan executor）。**採用する**: §12の結論（offerごとのJava受理・capture・record仕事を消す）と一致し、互換方針の変更
（描画改変Modは競合として非サポート）でproducerまで所有できるため。**順序は実測コストで並べ替える**
（leg `tickjfr-celtium-2`・JAR `652075c9…`・AMD・world・validation off・warm窓のclient threadサンプル692）:

| 項目 | client thread占有（inclusive） | 対応するstage |
|---|---|---|
| block entity描画（TESR→GL stub→受理→即時window） | 34%（end portal 14.6%・看板 6.8%） | Stage 3（TESR/entity/itemのnative producer・retained mesh）。end portalは描画の正しさのみ修正済み（texgen）、1 block×15 layerのCPU費用は未削減 |
| 即時windowの記録 `recordImmediateOnlyPass` | 30% | Stage 4（frame compiler）＋**executor thread**（下記） |
| 受理経路 `submitGeometryBatch` | 10% | Stage 1（packet化）で消す |
| client tick内のアニメtexture転送待ち | 3.6%（vkWaitForFences） | 修正済み（staging ring、下） |
| tick以外のvanilla（tick/世界処理） | 約6% | 対象外（共有コスト） |

- **Stage 2（CPU Everything Culling）は後ろへ**: TESR/entityはvanillaが既にfrustum判定しており、KPI sceneの削減は小さい見込み。
- **Stage 5（GPU Hi-Z）は最後**: AMDのフレームは解像度非依存＝CPU律速。GPU cullingはCPU時間を減らさない。
- **設計への追加（より速い可能性がある案）**: frame packetを不変にできれば、**記録・提出を専用executor threadへ移す**
  （main threadがframe N+1のpacketを作る間にNを記録）。記録30%がmain threadから外れる。遅延は最大1frame、キュー長1。
  採否はpaired KPIで決める。
- 移行は「一度に全部作り直さない」（設計§7）。各段で画像gate・validation 0・paired KPI（control arm）を取ってから次へ。

### 2026-10-24 の実測ダイジェスト（詳細は [archive](<history/plan-archive-2026-10-24.md>)）

| 変更 | 判定（AMD RX 6400・world・paired vs Celeritas 2.5.0-dev） |
|---|---|
| model instance producer（`VulkanModelInstances`、約800 replay/frame） | p50 **0.881x → 再測 1.027x（SLOWER）＝「速い」は非再現**。**p95は3組ともCeleritasより低い**（[証拠](<evidence/modelinst-kpi-2026-10-24.md>)） |
| `drawPendingImmediate` のJIT分割・frame arena slot・end portal producer | 1.70x → **1.113x**、p95は参照より低い（[証拠](<evidence/jit-arena-2026-10-24.md>)） |
| アニメtexture staging ring | p95 5.5–6.9 → **3.4–3.7ms**、p50中立（[証拠](<evidence/staging-ring-2026-10-24.md>)） |
| native raster/texture owner・end portal texgen | AMD/Intel world 受入 PASSED・validation 0、画像gate一致（[証拠](<evidence/native-owner-2026-10-24.json>)） |
| 定常ログ既定OFF（`-Dceltium.debug.periodicLogs=true` で復帰） | 速度主張なし |

- **影**: 利用者指示により本体内は停止。stage (a)–(d計器) は commit 済み・**既定OFF**（[証拠](<evidence/shadow-stage-c-2026-10-24.md>)）。
  PCF受け側は `git stash`「paused: in-core shadow PCF receiver」（leg `shadowpcf-amd-1` PASSED、視覚効果は未確認）。
  **再開は利用者が明示した時だけ。**


## 次の作業単位（2026-10-25 改訂・利用者の実プレイ報告に対応）

1. **実プレイ報告の状況**（利用者: 体感200fps＋スパイク、描画バグは少ない）
   | 報告 | 状況 |
   |---|---|
   | 20 FPS | 体感200fps超（ログ上限修正後）。**残りはスパイク** |
   | スパイク・移動時の体感lag | 移動中はterrainのocclusion探索がclient threadの28%（同期実行）→ **探索をsearch threadへ（既定）**、AMD飛行p50 1.03–1.34→0.74–0.89 ms・validation 0。同条件のCeleritasは0.78 ms・16 ms超0枚、Celtium同期は2–3枚。入室停止は未帰属。計器: `flightBlocksPerSecond`・`warmSpikes` |
   | 殴られたentityが赤い箱 | **修正**: combine中もalphaテストを維持（帽子レイヤの透明部が赤く塗られていた） |
   | 色付きガラスのチカチカ | **修正（実機確認待ち）**: 解放したarena区間を、それを読みうる提出の完了まで再利用しない（`de280e72a`。batched提出ではupload batchがフレーム本体より先にqueueへ入る） |
   | F3+T が重い | reload自体が 2.24 s（Celeritas 2.05 s）。差は font atlas の再upload → pack不変なら保持（`6d056197d`）。reload後のframeは正常 |
   | Xaero's Minimap（クラッシュ・真っ暗・位置ずれ・透け） | **修正**: PBO map/unpack、raw FBO/raw matrixの経路、tile upload（Devin）、mod FBO同士のsample（空mirrorより公開を優先・self-compositeは同一imageだけ）。AMD/Intel world PASSED・validation 0、Celeritas参照と同じ絵 |
   | ワールド退出後ボタン無し | **未再現**（既定・日本語・1920×1080で表示）。手順差の手掛かり待ち |
   | ロード画面 | **Forgeのsplashと同じ画面**（ロゴ・メモリ・進捗・Forgeロゴ・splash.properties）をnative描画（`3ebd22e90`） |
   | ログ量 | **タグごと16行で上限**（`CeltiumLogLimiter`） |
   | E2E 1.93a（Cleanroom 0.7） | 起動・入world・exit 0（AMD）。遠景の黒帯が未解決（[証拠](evidence/e2e-cleanroom07-2026-10-26.md)） |
   | vanilla post shader・発光outline | **native描画**（creeper/spider: `12a45471f`、outline合成: `ef807429c`。AMD/Intel world PASSED・validation 0。glow sceneは未較正） |
   texture environment・空framebuffer初期化・公開cache・material寿命の整理は [実装と検証](evidence/native-texture-owner-2026-10-25.md)。
2. **性能**（KPI flags・1280×720・vsync off/260。いずれも load 上昇で形式 verdict なし）:
   - Xaero 導入前（`ef807429c`）: AMD 0.990x（3対とも<1・p95も低い）。Intel は未batch提出が原因で1.68x → 全deviceでbatch提出に
     （`811c29c46`、Intel 単体 3.6–4.0 → 2.2 ms）。live allocation の寄与は約0.15 ms（`b57e1e7ba`）で主因ではない。
   - Xaero 入り利用者 instance: `0d937df8b` AMD 1.44x・Intel 1.45x → async present（提出とpresentを専用threadへ、既定ON）で **AMD 1.01x**（p50 1.22 ms）・**Intel 1.17x**（2.31 ms）、p95はまだCeleritas超。
     AMD は CPU律速、残差は per-draw 費用の分散。Intel は解像度依存の超過が 0.06 ms まで縮小（outline合成省略・depth OPTIMAL・solid discard特殊化）、
     残差は固定費。上限測定で棄却: 反復acquire barrier全省略 2–3%、texture跨ぎmerge（bindless上限）Intel 2.5%/AMD 4%、
     frame+present 1 submit（fence acquire、実merge確認済）中立、acquire狭小化は遅化、glthread仮説（無効でも3%）。
     **次の単位**: model replay と immediate を一つの順序付きstreamに（Stage 1 packet化）。Intel残差の内訳は[証拠](evidence/intel-gap-2026-10-26.md)。
3. **native 状態・実行**: fog は vanilla・地形・可視距離・即時描画で [native owner](evidence/native-fog-2026-10-26.json) を共有。[instance fog](evidence/model-fog-2026-10-26.json) を実装し、projection/fog の96B run定数化と行列積GPU移動で instance は320→256B、JavaのMVP合成を削除（[証拠](evidence/model-run-2026-10-27.json)）。同版のKPIは両GPU負荷ゲート外、速度未判定。**照明OFFモデルも同じinstance programへ統合、alpha全比較を実装**（[証拠](evidence/unlit-model-2026-10-27.json)）。glint陽性対照で受理を確認、glint単独画素は未分離。155 JUnit・71 Python通過。candidate 10 leg validation 0、world AMD/Intel・video・退出後menuはgate通過。inventory/recipe・actor/glintは未較正。最新JARの速度とGREATER/disabled以外のalpha比較はGPU未測定。非instance lit fogは未対応。

4. **影・パックは Demonica の fork で**: 利用者指示により **Iris を設計参照にするのをやめ、Demonica を fork して Celtium 用にする**。
   Demonica の実体（pinned commit の README より）: **Iris の shader pipeline + Angelica の GLSM + 互換層**を、別途導入の Celeritas の上で動かすもの。
   Actinium から fork し terrain renderer は持たない。**Cleanroom の OpenGL context 上で動く**ため、Celtium では
   「Celeritas との接続面（terrain 描画・pack pipeline・GLSM）」を **Celtium の Vulkan 経路との接続面に置き換える**作業になる。
   **別成果物（addon jar）**として実装し、Celtium core には取り込まない。
5. **本体の影scaffoldとshaderpack frontendは削除済み**（2026-10-27、旧アーキテクチャ削除）。影とpackはDemonica addonで実装する。
6. **ライセンス**: Demonica 本体は **AGPL-3.0**（〜0.4.0 は GPL-3.0）。Angelica/Iris 由来部分は LGPL-3.0、Actinium 由来は GPL-3.0、
   glsl-transformer は AGPL-3.0。**fork するなら fork 側も AGPL-3.0 になり、Celtium core とは別リポジトリ/別jarで境界を保つ**。
   LGPL 部分のチェリーピックはクレジット明記で可能（利用者指示③）。GLSM の丸ごと持ち込みはしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|

| **利用者報告の不具合（2026-10-23）** | **未修正・トリアージ済み** | 利用者の実機報告＋画面 | 修正中 | ①メニューのパノラマ＝**修正済み**（[画像](evidence/2026-10-24-menu-panorama-fixed.png)） ②エンドポータル内の描画がおかしい＝**修正済み**（glTexGenがstubでno-op→`NativeTexGen`＋texgen pipeline、portal面の参照差 平均13.5/21.9/18.1→5.5/9.5/10.2、[証拠](<evidence/end-portal-texgen-2026-10-24.md>)、Intel未実行） ③F2スクショ真っ黒＝**修正済み**（`nglGetTexImage`ルート、leg shot-test6 PASSED・非黒98.3%）④色付きガラスがチカチカ ⑤視点移動時に看板文字などが残像。**⑤は静止条件で棄却済み**（`framesNoClear=0`・F2画像と提示画像が0差分）＝動き依存（提示レイテンシ等）として次段で測定。判明: ③は既定framebuffer(0)からのreadPixels経路（TextureCopyService 890-901のgate）がメニュー/スクショ文脈で拒否されるため。④⑤はフレーム画像の再利用・掃除とブレンド順の疑い。①はメニュー経路（パノラマはworld描画＋GUI）で同じ土台の疑い。次は③から直し、④⑤→①→②の順 |


| **互換・画質・配布** | **0.7.11 で受入完了。0.6.13 は非対応（明示拒否）** | 実測（v0.0.14-alpha・AMD RX 6400 / Intel UHD 630） | 0.7.11: 完了 / 0.6.13: 対象外 | world scene は `cleanroom-0.7.11-alpha` の `minecraft/saves/qmagnet`（配布マップ）を使用。v0.0.14-alpha のforge jar `90a7c505…` は world で **AMD/Intel とも PASSED・validation 0・画像gate一致**。**0.6.13-alpha（GLFW/LWJGL 3.4.1）は起動不可**＝SDL3バインディング不在を検出して`CeltiumGllessHint` がバージョンを名指しして拒否（クラッシュレポートに理由が残る）。0.6.13 で受入を主張するには0.6用の別ビルドが必要 |
| **巨大pack互換** | **RLCraft 2.9.3 は対象外**（Celtiumを外した対照でも `VerifyError` で起動しない） | 利用者指示 2026-10-27 | 未着手 | 代替は Cleanroom対応の **E2E-E**。受入は world を開いた提示画像に異常が無いこと |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない |
| **次の標的** | **native実装へ** | — | 未着手 | ①model/immediateの共通packetとnative実行 ②非instance lit fog ③state/texture/target所有の統合。影はDemonica addonへ分離、本体内は停止。 |

## 撤回した前提

[archive](<history/plan-archive-2026-10-21.md>) の「2026-10-24 移動」節（KPI 2.29x・draw数・GC主因・GLスタブ解消済み 等。再入条件つき）。

## 未確定（数値を前提にしない）

- GPU pass時間、present/readback、アップロード量、GC、フレーム内訳の旧値は、artifact/区間が今回のビルドと一致しないため参考値としても使わない。
- validation clean・`exitCode=0`・色数gateは、それぞれ描画不存在・正しい絵・画像一致を証明しない。


## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [設計](<new_architecture.md>) /
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
leg（world・影OFF）:
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
- ディスク: `.gv-verify/run-*` と `stage-*` は毎回消す（16G→405M、空き18G）

