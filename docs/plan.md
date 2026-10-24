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

### 2026-10-24: modelのinstance描画 → **AMDでCeleritas比 0.881x（NOT_SLOWER、初）**

lit modelのdisplay list再生（約800/frame）を共有mesh pool＋256B record＋bindless texture＋indirect runに（`VulkanModelInstances`）。
window counterのdriftも修正。KPI `modelinst-kpi-ref-2`（JAR `fa32e6e9…`、3 rep、比 0.869/0.908/0.881、p95 1.55–1.62 vs 2.58–2.64ms）。
**注意**: 負荷が測定末に5.6、Celeritasが前回より遅め（1.27–1.35ms）→**再測定で確定が必要**。Intel未測定。受入 `modelinst-valid-amd-2` PASSED・validation 0。

### 2026-10-24: 描画ループのJIT化・arena slot・end portal producer → **Celeritas比 1.70x → 1.113x**

`drawPendingImmediate` が8282 byte（HotSpotの8000 byte上限超過）で**常にinterpreter実行**＝client threadの29.9%。分割でJIT化し3.4%。
速くなったCPUがGPUより2frame以上先行し、2 slotのframe arenaが枯渇→groupごとに`vkAllocateMemory`（695/2831サンプル）。
slot数をscheduler（4）+1に。end portalはlayerごと1 drawのproducerに。fixtureがCeleritas armに毎frame課していた税
（ClassNotFoundException×3・F3再解析、58/525サンプル）を除去＝**以前の比は実態より良く見えていた**。
KPI `arena-kpi-ref-1`（JAR `d3dfb9ba…`、AMD、3 rep、disturbed無し）: p50 **1.113x（SLOWER）**、**p95はCeleritasより低い**（1.93–2.02 vs 2.02–2.18ms）。
`HugeMethodLimitTest` が再発を止める。[証拠](<evidence/jit-arena-2026-10-24.md>)。Intel未測定。

### 2026-10-24: 定常ログを既定で停止（`-Dceltium.debug.periodicLogs=true`で復帰）。速度主張なし。

### 2026-10-24: client tickのアニメtexture転送待ちを除去（staging ring）

spriteごとの`vkWaitForFences`をstaging ringで除去。KPI `ring-kpi-1`: warm p95 5.5–6.9→3.4–3.7ms、p50中立。[証拠](<evidence/staging-ring-2026-10-24.md>)。

## 2026-10-24: 本体内の影は停止（利用者指示）

影はDemonica forkで作る（下の2.）。本体の優先は**性能→互換性**。本体内の影stage (a)〜(c)+(d計器)はcommit済み・既定OFF
（[証拠](<evidence/shadow-stage-c-2026-10-24.md>)）。PCF受け側の未commit差分は `git stash` の
「paused: in-core shadow PCF receiver」（leg `shadowpcf-amd-1` PASSED・JAR `275e622e…`、影の視覚効果は未確認）。再開は利用者が明示した時だけ。

## 2026-10-24: native state/resource owner

`NativeRasterState` が blend/cull/色マスク/scissor/polygon offset、`NativeTextureBindings` が texture unit/binding を所有する。
通常drawでのGL記録の再取り込み・bind ordinal照合を削除。白textureもGL名生成/一時bindを経由せずVulkan画像を作る。
再確保時のstorage破棄と名前削除を分離し、atlasのbindingを保持する。

[実機証拠](<evidence/native-owner-2026-10-24.json>): JAR `8baddf8701bb623be0f28bdd0be5dfc4124db28c5b924deb147e76c291013dbe`、
AMD RX 6400/Intel UHD 630 のqmagnet、AMDのvideoSettingsは画像gate PASSED・validation 0。F2は提示画像と一致。
menu/HEI inventory/recipeの実画像を保存したが、HEIの専用gateは未較正。性能は3pair測定したが負荷上昇（2.57→5.45）・1組のstarvationにより判定不能（残る比0.973/1.118）。改善を主張しない。
描画要求の受付・実行全体の移管と影/packは未完。以前からの影の未commit変更はこの成果物に含まない。

## 次の作業単位（2026-10-24 改訂）

1. **性能（最優先）**: 2026-10-24夕の KPI は **1.113x**（上記）。v0.0.16-alpha 時点は約1.7x。同日午前の 1.56ms / 1.31x は、84c220575 の再ビルドJARでも 2.05ms になり**再現せず＝マシン状態**だった（`469ffc4a2`、決着）。次の焦点は **tail**: warm区間のサンプル数がCeleritasの1/3〜1/2、p95 10–20ms（Celeritas 2.4ms）。client tick（fixture計測 mean 4–5ms・p95 約10ms）との対応を対照armで判別中。
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

| **利用者報告の不具合（2026-10-23）** | **未修正・トリアージ済み** | 利用者の実機報告＋画面 | 修正中 | ①メニューのパノラマが壊れて重い＝**修正済み**（window読みのコピーをhostルート既定に。leg menu69/70、AMD RX 6400、参照差 65.63%→43.35%、彩度0.042→0.303、画像 `docs/evidence/2026-10-24-menu-panorama-fixed.png`、ワールド受入 `windowfix-acc` PASSED。menuの1present最大7コピーぶんの体感速度は未測定） ②エンドポータル内の描画がおかしい＝**修正済み**（glTexGenがstubでno-op→`NativeTexGen`＋texgen pipeline、portal面の参照差 平均13.5/21.9/18.1→5.5/9.5/10.2、[証拠](<evidence/end-portal-texgen-2026-10-24.md>)、Intel未実行） ③F2スクショ真っ黒＝**修正済み**（`nglGetTexImage`ルート、leg shot-test6 PASSED・非黒98.3%）④色付きガラスがチカチカ ⑤視点移動時に看板文字などが残像。**⑤は静止条件で棄却済み**（`framesNoClear=0`・F2画像と提示画像が0差分）＝動き依存（提示レイテンシ等）として次段で測定。判明: ③は既定framebuffer(0)からのreadPixels経路（TextureCopyService 890-901のgate）がメニュー/スクショ文脈で拒否されるため。④⑤はフレーム画像の再利用・掃除とブレンド順の疑い。①はメニュー経路（パノラマはworld描画＋GUI）で同じ土台の疑い。次は③から直し、④⑤→①→②の順 |

| **性能: draw-manager（§11.5-11.8）** | **土台3/5段（①②③完了）** | 実測+画像 | 実装中 | ①descriptor indexing 4機能を両GPUで有効（`textureArray=true`・PASSED/0件, jar `3e3dc1a5`） ②共有テクスチャ配列セット実装＝**35枚/4096・書き込み35回**（jar `6cf90a00`） ③groupへ配列index＝**hit 178407/miss0（AMD）・hit 114673/miss0（Intel）**（jar `75bea5b3`） 残り＝④シェーダをindex化（まずtexture系1 family、push定数index）⑤pipelineソート＋geometry arena＋`vkCmdDrawIndexedIndirect`で **159→約11 draw/window**。**①②③は未だ描画に使わず速度未計測**。影削除を含む現ツリーは jar `0b3b49d8…` で AMD/Intel とも PASSED・画像gate PASSED・validation 0件 |

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

