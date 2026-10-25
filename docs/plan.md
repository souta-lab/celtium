# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.13-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開最新版は **v0.0.16-alpha**（2026-10-24、[release notes](<releases/0.0.16-alpha.md>)）。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、目標設計は [new_architecture.md](<../new_architecture.md>)、旧設計と実測は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 2026-10-24: 新アーキテクチャへ全面移行（利用者指示）

目標設計は [new_architecture.md](<../new_architecture.md>)（scene database・Native Everything Culling・frame compiler・
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
- **Stage 5（GPU Hi-Z）は最後**: AMDのフレームは解像度非依存＝CPU律速（architecture §11）。GPU cullingはCPU時間を減らさない。
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
   | スパイク（入室直後・まれ） | model pool/snapshot全コピー修正済み。10/25の日本語・1920×1080・Wayland・視点回転profileではwarm [400,980) の50ms超0枚・最大17.015ms。入室停止の全帰属は未完、実デスクトップのまれなspikeは未再現（[証拠](evidence/spikes-2026-10-25.json)） |
   | 殴られたentityが赤い箱 | **修正**: combine中もalphaテストを維持（帽子レイヤの透明部が赤く塗られていた） |
   | 色付きガラスのチカチカ | **修正（実機確認待ち）**: upload copyの前に書込み前の実行依存（WAR）を追加。解放直後のindex領域を飛行中フレームが読んでいた |
   | ワールド退出後ボタン無し | **未再現**（fixture: 既定・日本語/Unicode/1920×1080 とも表示）。手順差（ポーズ→終了・非16:9）の手掛かり待ち |
   | ロード画面 | **Forgeのsplashと同じ画面**（ロゴ・メモリ・進捗・Forgeロゴ・splash.properties）をnative描画（`3ebd22e90`） |
   | ログ量 | **タグごと16行で上限**（`CeltiumLogLimiter`、`-Dceltium.debug.periodicLogs=true`で全出力） |
   | vanilla post shader（観戦時のcreeper/spider等・発光outline） | **creeper/spiderはnativeで描画**（leg `postshader4-creeper/spider-amd-1` PASSED・validation 0）。以前はGL-lessで読込自体が失敗し無効だった（compile状態の問合せ署名違い・uniform位置-1・行列の転置・descriptor layout）。**発光outlineもnative合成で表示**（`framebufferRenderExt`を`NativeFramebufferCompositor`へ置換、jar `6aafefa6` で leg `comp-glow-amd` に白outline・validation 0、`comp-world-amd`/`comp-world-intel` PASSED・validation 0）。glow sceneは未較正のため画像gateは判定外 |
   native texture environment 所有へ移行し、モデルごとのGL問合せを撤去。REPLACE/CONSTANT の発光用materialを修正し、glowのmain合成はnative compositorで表示。
   空framebufferのnative初期化・公開cache失効・main imageのGENERAL契約・world終了時material cache寿命を修正。
   **同一最終JARでAMD/Intelのworld往復 PASSED・画像gate PASSED・validation ON 0件**。
   4画面＋qmagnetを保存（HEI2画面は未較正、合格には数えない）。[実装・失敗・検証](evidence/native-texture-owner-2026-10-25.md)。
2. **性能**: 10/25の3-arm×3回ではCeleritas比1.0342x・変更前比1.0313x。load 0.46→2.67 > 上限2のため **NO_SPEED_VERDICT**。高速化未確認。
   `ef807429c`（jar `6aafefa6`）×Celeritas 3対（AMD・1280×720・vsync off/260）: 比 0.986/0.990/0.998（中央0.990x）、warm p95 は3対ともCeleritasより低い（1.69/1.80/1.88 vs 2.02/2.66/2.31 ms）。ただし load 1.74→5.52 で **NO_SPEED_VERDICT**。Intel未測定。
   次は `goal.md` の基盤固有最適化と描画mod手法の吸収（EntityCulling型の遮蔽間引き、静的TESRのchunk mesh化、
   `VkPipelineCache` 永続化、GUI/文字のbatch）を、paired KPIとスパイク計測で1つずつ。Intelは未測定。
3. **分割**: `VulkanTerrainGpuBackend` を所有権ごとに段階抽出（振る舞い不変・受入legで確認）。済: `BackendDiagnostics`・`ShaderPackProbe`・`ModelReplay`・`PostChainBridge`（20,905→17,932行）。
4. **影・パックは Demonica の fork で**: 利用者指示により **Iris を設計参照にするのをやめ、Demonica を fork して Celtium 用にする**。
   Demonica の実体（pinned commit の README より）: **Iris の shader pipeline + Angelica の GLSM + 互換層**を、別途導入の Celeritas の上で動かすもの。
   Actinium から fork し terrain renderer は持たない。**Cleanroom の OpenGL context 上で動く**ため、Celtium では
   「Celeritas との接続面（terrain 描画・pack pipeline・GLSM）」を **Celtium の Vulkan 経路との接続面に置き換える**作業になる。
   **別成果物（addon jar）**として実装し、Celtium core には取り込まない（`docs/shader-plan.md` の方針）。
5. **組込影は scaffold に降格（既定OFF）**: `ShadowMapPass` は native 深度ターゲットと投影の検証用。既定 `false`。
6. **ライセンス**: Demonica 本体は **AGPL-3.0**（〜0.4.0 は GPL-3.0）。Angelica/Iris 由来部分は LGPL-3.0、Actinium 由来は GPL-3.0、
   glsl-transformer は AGPL-3.0。**fork するなら fork 側も AGPL-3.0 になり、Celtium core とは別リポジトリ/別jarで境界を保つ**。
   LGPL 部分のチェリーピックはクレジット明記で可能（利用者指示③）。GLSM の丸ごと持ち込みはしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|

| **利用者報告の不具合（2026-10-23）** | **未修正・トリアージ済み** | 利用者の実機報告＋画面 | 修正中 | ①メニューのパノラマが壊れて重い＝**修正済み**（window読みのコピーをhostルート既定に。leg menu69/70、AMD RX 6400、参照差 65.63%→43.35%、彩度0.042→0.303、画像 `docs/evidence/2026-10-24-menu-panorama-fixed.png`、ワールド受入 `windowfix-acc` PASSED。menuの1present最大7コピーぶんの体感速度は未測定） ②エンドポータル内の描画がおかしい＝**修正済み**（glTexGenがstubでno-op→`NativeTexGen`＋texgen pipeline、portal面の参照差 平均13.5/21.9/18.1→5.5/9.5/10.2、[証拠](<evidence/end-portal-texgen-2026-10-24.md>)、Intel未実行） ③F2スクショ真っ黒＝**修正済み**（`nglGetTexImage`ルート、leg shot-test6 PASSED・非黒98.3%）④色付きガラスがチカチカ ⑤視点移動時に看板文字などが残像。**⑤は静止条件で棄却済み**（`framesNoClear=0`・F2画像と提示画像が0差分）＝動き依存（提示レイテンシ等）として次段で測定。判明: ③は既定framebuffer(0)からのreadPixels経路（TextureCopyService 890-901のgate）がメニュー/スクショ文脈で拒否されるため。④⑤はフレーム画像の再利用・掃除とブレンド順の疑い。①はメニュー経路（パノラマはworld描画＋GUI）で同じ土台の疑い。次は③から直し、④⑤→①→②の順 |


| **互換・画質・配布** | **0.7.11 で受入完了。0.6.13 は非対応（明示拒否）** | 実測（v0.0.14-alpha・AMD RX 6400 / Intel UHD 630） | 0.7.11: 完了 / 0.6.13: 対象外 | world scene は `cleanroom-0.7.11-alpha` の `minecraft/saves/qmagnet`（配布マップ）を使用。v0.0.14-alpha のforge jar `90a7c505…` は world で **AMD/Intel とも PASSED・validation 0・画像gate一致**。**0.6.13-alpha（GLFW/LWJGL 3.4.1）は起動不可**＝SDL3バインディング不在を検出して`CeltiumGllessHint` がバージョンを名指しして拒否（クラッシュレポートに理由が残る）。0.6.13 で受入を主張するには0.6用の別ビルドが必要 |
| **RLCraft 2.9.3 互換** | **Celtium側では無い。0.7.11 + Fugue 0.25.5 でも外部要因で停止（対照実験済み）** | 実測 2026-10-23（probe: 0.7.11 clone + RLCraft mods 166 + scalar-3.9.0） | 外部待ち | Fugue 0.24.4 → `NoClassDefFoundError com.cleanroommc.hackery.ReflectionHackery`（SetBonus）。Fugue 0.25.5 → 44秒まで進行（Sound engine started）後 `VerifyError: EntityRenderer.func_78472_g`。**celtium jar を外した対照でも同じ VerifyError** のため Celtium の不具合ではない。次の候補は Cleanroom 0.7.12-alpha 以降での再試行 |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない |
| **次の標的** | **native実装へ** | — | 未着手 | ①**受理経路をnativeへ**（Java補足ではなく所有権移動。`architecture` §12が規模の算術）②影の尾の削減（二重バッファ化 or cadenceと品質のトレード）③`shader-plan.md` の既存modベースでBSL接続 ④state/texture/render target所有のnative化（未完） |

## 撤回した前提

[archive](<history/plan-archive-2026-10-21.md>) の「2026-10-24 移動」節（KPI 2.29x・draw数・GC主因・GLスタブ解消済み 等。再入条件つき）。

## 未確定（数値を前提にしない）

- **Intel性能は未測定**（AMDのみ）。同一hash・同一条件のIntel paired測定を取るまで両GPU優位を主張しない。
- tail（p95）が3.8倍なのにp50が1.70倍という差は、平均的な変換コストより**スパイク源**が競争力を決めることを示す。次の判別はwarm区間のframe毎内訳とアップロード/GC/待ちの同時記録であり、推測で1つに絞らない。
- GPU pass時間、present/readback、アップロード量、GC、フレーム内訳の旧値は、artifact/区間が今回のビルドと一致しないため参考値としても使わない。
- validation clean・`exitCode=0`・色数gateは、それぞれ描画不存在・正しい絵・画像一致を証明しない。

最新リリース: **v0.0.16-alpha**（2026-10-24、[notes](<releases/0.0.16-alpha.md>)）。

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

