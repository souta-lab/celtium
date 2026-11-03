# Celtium 現状表（唯一の現在状態表）

開発ベース **0.1.4-alpha-rc.26**（[build定義](<../build-1122/build.gradle.kts>)）。Windows検証用プレリリースは **v0.1.4-alpha-rc.26**（2026-10-30、[release notes](<releases/0.1.4-alpha-rc.26.md>)）。これまでの性能検証は本文に記載した 0.1.1-alpha を基準とする。
2026-10-30 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)。[new_architecture.md](<new_architecture.md>) は参考の設計案で拘束しない（利用者指示 2026-10-28）。実測が勝ればそちらを採る。

## Windows/NVIDIA報告への優先対応（2026-10-29）

- **未解決**: Supersymmetryの最初の `VK_ERROR_DEVICE_LOST`。RC5のFence reset修正は後続AV対策で根本解決の証拠ではない。async presentは維持。
- **FBO/copy**: raw採用ID・8個制限・削除・逆引き・範囲、copy送信順とlayoutを修正。window copyはGPUのscratchで矩形内alphaを1にして既定有効。範囲・12画像・削除順・部分copy画素・scratch resizeはAMD/Intel成功、同期validationエラー0（`release-rc8-{amd,intel}2`、`72665b41…`）。Bloom画素やWindows受入ではない。

## 2026-10-24: 新アーキテクチャ案（2026-10-28 以降は参考扱い）

設計案は [new_architecture.md](<new_architecture.md>)。**採用する**のは §12 の結論（offer ごとの Java 受理・capture・
record 仕事を消す）だけで、順序は実測コストで並べ替える（leg `tickjfr-celtium-2`・JAR `652075c9…`・AMD・world・
validation off・warm 窓の client thread サンプル692）: block entity 描画 34%（end portal 14.6%・看板 6.8%）、
即時 window の記録 30%、受理経路 `submitGeometryBatch` 10%、アニメ texture 転送待ち 3.6%（修正済み）、tick 以外の
vanilla 約6%（対象外）。**Stage 2（CPU culling）は後ろ・Stage 5（GPU Hi-Z）は最後**（AMD のフレームは解像度非依存
＝CPU 律速）。**記録・提出を executor thread へ移す**案は paired KPI で採否を決める。移行は「一度に全部作り直さない」
（設計§7）。各段で画像 gate・validation 0・paired KPI を取ってから次へ。
2026-10-24 の実測ダイジェストは [archive](<history/plan-archive-2026-10-24.md>)（当時の値。現在の判定には使わない）。

- **影**: 利用者指示により本体内は停止。stage (a)–(d計器) は commit 済み・**既定OFF**（[証拠](<evidence/shadow-stage-c-2026-10-24.md>)）。
  PCF受け側は `git stash`（leg `shadowpcf-amd-1` PASSED、視覚効果は未確認）。**再開は利用者が明示した時だけ。**


## 次の作業単位（2026-10-25 改訂・利用者の実プレイ報告に対応）

1. **実プレイ報告の状況**（利用者: 体感200fps＋スパイク、描画バグは少ない）
   | 報告 | 状況 |
   |---|---|
   | 20 FPS | 体感200fps超（ログ上限修正後）。**残りはスパイク** |
   | 入world直後 約3秒の強いラグ | **縮小・qmagnetではCeleritas同等**: 未使用chunk dispatcherのbuilder 61個（~610 MB）→1個（`99e1daf77`）。最初60tickの50ms超フレーム Celtium 1.67–1.95 s / Celeritas 1.67–1.79 s。残りは共通のchunk packet・class load。利用者の3秒は未再現 |
   | スパイク・移動時の体感lag | 移動中はterrainのocclusion探索がclient threadの28%（同期実行）→ **探索をsearch threadへ（既定）**、AMD飛行p50 1.03–1.34→0.74–0.89 ms・validation 0。同条件のCeleritasは0.78 ms・16 ms超0枚、Celtium同期は2–3枚。入室停止は未帰属。計器: `flightBlocksPerSecond`・`warmSpikes` |
   | 視点移動で隠れた海底/洞窟が一瞬見える・GVCのmob/機体が見えない | **修正（実機確認待ち）**: 非同期探索の結果を、視点が変わったフレームだけ描画前にjoin（`22d8d84e4`）。Modのclient-array display list・直接の`GL11.glColor*`/`glDepthMask`・lit時ITEM形式（`eeb6b1d0e`、A4がCeleritas参照と一致）。A4のserver crashはMod側の不具合→`scripts/fix_gvcr2_a4.py` |
   | 殴られたentityが赤い箱 | **修正**: combine中もalphaテストを維持（帽子レイヤの透明部が赤く塗られていた） |
   | 色付きガラスのチカチカ | **修正（実機確認待ち）**: 半透明terrainパスが背面cullなしで描いていた（ガラスの裏面が2層目として合成、視点で順序が変わる）→vanilla同様に背面cull（`5f75fc009`、参照比で~20–25暗かった差が解消、[画像](evidence/2026-10-28-stained-glass-backfaces.png)） |
   | F3+T が重い | reload自体が 2.24 s（Celeritas 2.05 s）。差は font atlas の再upload → pack不変なら保持（`6d056197d`）。reload後のframeは正常 |
   | Xaero's Minimap（クラッシュ・真っ暗・位置ずれ・透け） | **修正**: PBO map/unpack、raw FBO/raw matrixの経路、tile upload（Devin）、mod FBO同士のsample（空mirrorより公開を優先・self-compositeは同一imageだけ）。AMD/Intel world PASSED・validation 0、Celeritas参照と同じ絵 |
   | ワールド退出後ボタン無し | **未再現**（既定・日本語・1920×1080で表示）。手順差の手掛かり待ち |
   | ロード画面 | **Forgeのsplashと同じ画面**（ロゴ・メモリ・進捗・Forgeロゴ・splash.properties）をnative描画（`3ebd22e90`） |
   | ログ量 | **タグごと16行で上限**（`CeltiumLogLimiter`） |
   | E2E 1.93a（Cleanroom 0.7） | Intelの読込SIGSEGVと疎なatlas描画拒否は修正候補で各1回通過。最終jarはE2E未再検証、画像比較未成立（[証拠](evidence/e2e-cleanroom07-2026-10-26.md)） |
   | vanilla post shader・発光outline | **native描画**（creeper/spider: `12a45471f`、outline合成: `ef807429c`。AMD/Intel world PASSED・validation 0。outlineはwithin-run A/Bで実測＝[証拠](evidence/glow-outline-2026-11-03.md): 2669px・対照0・noise 3.69%・`postChains=1`） |
   texture environment・空framebuffer初期化・公開cache・material寿命の整理は [実装と検証](evidence/native-texture-owner-2026-10-25.md)。
2. **性能**（KPI flags・1280×720・vsync off/260・`world-0.7.11` instance（Xaeroなし）・3対×240 s、両GPUとも 0.1.1-alpha の jar `9feaa083`（コードは配布jar `a8bd0480`と同一、差はjar内README）＝`54b42982d`）:
   - **AMD: goal条件1を満たす**（最新 `rel-amd`: verdict NOT_SLOWER、各対のp50/p95/p99比が全て<1、中央値 0.591/0.596/0.826、p50 0.63 vs 1.06 ms。前回 `kpi5` 0.622/0.619/0.811）。
     **この行は scene 限定**（`world-0.7.11`・Xaeroなし・旧JAR）。現行JARを実配布profileで測ると逆:
     `celtium-world-accept`（188 mods・`qmagnet`）3対で **Celtium が 1.66–1.76×遅い**（p50 median 1.677、
     `goalCondition=NOT_MET`、[証拠](evidence/paired-kpi-world-accept-2026-11-03.md)）。frameBudget は
     `gpuShare=0.01`＝CPU律速、Celtium側 bracket は `record`0.6＋`batch`0.9＋`present`0.4 ms。
     **窓の固定費はもう主因ではない**（27.6窓/フレーム、[証拠](evidence/window-split-2026-11-03.md)）。
     **prepareキャッシュの負荷率76%が欠陥**（ミス9.8→1.8%、p50 −5.8%、[証拠](evidence/prepare-cache-load-2026-11-03.md)）。
     鎖は `record`0.5＋`batch`0.8＋`present`0.2、`other`1.6 ms（3.1 p50）。Celeritas 1.79 ms 超えには**鎖~0.2 ms**。
     **次の性能単位は記録の client thread 外への移動**（§12）か offer/窓の削減。client threadのprofile
     （[証拠](evidence/accept-profile-jfr-2026-11-03.md)）: Celtium 43.4%・accept 19.0%・窓 9.8%・
     **最大self 3.6%**＝micro-optでは動かない。JFRは`CELTIUM_LEG_JFR`（attach）。
     **窓の削減は1件実測**: pending copyの順序flushを「offerごと」から「そのbatchがcopy先をsampleする時だけ」へ
     （`-Dceltium.vulkan.rawWriteSampleGate`、既定ON）。同一JAR・同一sceneのpaired setで **36.02→27.59窓/フレーム
     （−23.4%）**、siteのflush 17497→6884、validation 0・拒否0・画像gate両arm PASSED
     （[証拠](evidence/raw-write-sample-gate-2026-11-03.md)）。p50 −4.5%は±7%内＝速度は主張しない。
   - **Intel: 未達**（最新 `rel-intel`: p50 0.896・p95 0.872、**p99 1.048**＝3.46 vs 3.30 ms（中央値）。kpi3 1.110→kpi5 1.051→1.048）。
     p99帯はtickを含むフレーム（3.5%）。tick本体がCeltiumで~10–15%遅い（vanillaコード全般に分布、固定fpsでも残る）。棄却した仮説と計器は[証拠](evidence/intel-p99-2026-10-28.md)（GC・client thread CPU・safepoint・非同期探索は末尾）。
   - **fps上限が無視されていた**（GL-lessのpaceが表示リフレッシュに固定、120上限→60 fps）→ world内は上限値でpace（`99632d996`、Intel 120/60 PASSED）。
   - 過去の棄却（barrier省略・texture跨ぎmerge・1 submit・glthread）は[証拠](evidence/intel-gap-2026-10-26.md)。
3. **native 状態・実行**: cull の面と winding・`GL_VIEWPORT_BIT`・`GL_LIGHTING_BIT` の部分復元・font run の拒否保持・no-op census は出荷経路で発火しない→生きた描画census `CELTIUM_GL_SURFACE_DRAWING`・display-list census は id<512 のみ→総量/id を報告（entity arm modelDraws=5545・拒否0）。証拠は [cull](evidence/cull-face-2026-11-03.md)・[viewport](evidence/viewport-attrib-2026-11-03.md)・[lighting](evidence/lighting-attrib-2026-11-03.md)・[font](evidence/font-run-refusal-2026-11-03.md)・[no-op](evidence/noop-census-unreachable-2026-11-03.md)・[display-list](evidence/display-list-census-2026-11-03.md)。glint画素/inventory・actor較正は未対応（[証拠](evidence/native-fog-2026-10-26.json)・[model-run](evidence/model-run-2026-10-27.json)）。

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

| **利用者報告の不具合（2026-10-23）** | **①②③修正済み・④実機確認待ち・⑤測定済み** | 実機報告＋画面 | ②未実行 | ①パノラマ ②エンドポータル（texgen） ③F2黒スクショ（`nglGetTexImage`） ④ガラスちらつき（背面cull `5f75fc009`） ⑤残像＝静止0差分・tooltipの動きは連続3フレーム残留0（[証拠](evidence/tooltip-motion-2026-11-02.md)）、mod FBO bind中も欠落・残像0（[証拠](evidence/tooltip-mod-fbo-2026-11-03.md)）、看板の文字も0（[証拠](evidence/sign-text-afterimage-2026-11-03.md)） |


| **画質（4画面＋QMAGNET）** | **settings画素一致・worldは16階調超 0.97/1.05 %（AMD/Intel）。menu背景は両GPU確認済み** | 両GPU PASSED・clean | 実測 | [証拠](evidence/image-quality-2026-10-28.md)・[menu](evidence/menu-capture-2026-11-02.md)・[legacy既定ON](evidence/legacy-immediate-default-2026-11-02.md)。修正: 半透明cull・lit clamp・Forgeブロックモデル・legacy既定ON・atlas実寸・色同期・menu背景。残り: 遠い壁1面の陰影勾配、loadingは参照なし。**creative tab の item icon と hover slotのhighlight を修正**（[highlight](evidence/creative-highlight-line-pipeline-2026-11-02.md)）。**creative grid は現行JARで参照一致**（[検証](evidence/creative-grid-verified-2026-11-03.md): mean\|A-B\| 1.17・cell 最大 2.04、旧 19/24 が >6）|
| **互換・画質・配布** | **0.7.11 で受入完了。0.6.13 は非対応（明示拒否）** | 実測（v0.0.14-alpha・AMD RX 6400 / Intel UHD 630） | 0.7.11: 完了 / 0.6.13: 対象外 | v0.0.14-alpha `90a7c505…` は qmagnet world で AMD/Intel PASSED・validation 0・画像gate一致。0.6.13（GLFW/LWJGL 3.4.1）はSDL3不在を検出して版を名指しで拒否 |
| **巨大pack互換（E2E-E 1.87.3）** | **AMD: fixture PASSED・validation 0（`e2ee-amd-c4`）、Intel未実行。残課題は[証拠](evidence/e2e-cleanroom07-2026-10-26.md)の末尾** | AMD | 未実行 | Cleanroom 0.7差はlocal置換と上流PR（`8f121acfb`）。残りは証拠ファイルへ。 |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない（2026-10-30: `celtium.vulkan.glless`とGL bridgeを削除） |
| **次の標的** | **native実装へ** | — | 未着手 | ①model/immediateの共通packetとnative実行 ②~~非instance lit fog~~ **修正**（lit ITEMのfog block 224/240・256B・device contract 256、[証拠](evidence/lit-fog-2026-11-03.md): within-run A/Bで10,355画素が `w=0.12–0.77` のfog混合・残差p50 0.29/255、gate PASSED） ③state/texture/target所有の統合。影はDemonica addonへ分離、本体内は停止。 |

## FPSの現状と残る道（2026-10-31実測）

- ABBA（クール・アイドル時）: Susy **1.27–1.48×**、ATM3 **~1.45×**。CPU律速。**測定はアイドル窓必須**。
- 内訳（Susy p50）: frame 1.9 / flush 0.8（winRecord 0.5＝draw 0.2＋begin 0.1、barrier/submit各0.1）。30 windows・112 groups/frame。
- 残差はwindow固定費とper-batch。pack 1 frame 26.7k–39.4k replay、segmentは**frame要求で自動拡張**。[根拠](evidence/model-record-growth-2026-11-02.md)
- 棄却: bind flush延期（[罠](<verification-traps.md>)）、indirect immediate（singletonで悪化）、フレーム再利用（アニメ凍結）。

## 撤回した前提

[archive](<history/plan-archive-2026-10-21.md>) の「2026-10-24 移動」節（KPI 2.29x・draw数・GC主因 等。再入条件つき）。

- **bind時のflush延期**（2026-10-31、2回ともRADV SIGSEGV）: 再入条件は[検証の罠](<verification-traps.md>)。

## 未確定（数値を前提にしない）

- GPU pass時間・present/readback・アップロード量・GC・フレーム内訳の旧値は、artifact/区間が現ビルドと一致しないため参考値にも使わない。
- validation clean・`exitCode=0`・色数gateは、それぞれ描画不存在・正しい絵・画像一致を証明しない。


## 更新と参照

- [作業規約](<../AGENTS.md>) / [目標](<goal.md>) / [設計案（参考）](<new_architecture.md>) /
  [手順](<development.md>) / [計器](<instruments.md>) / [罠](<verification-traps.md>)。
- 計測の生ログと棄却済みの詳細は [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)。
- 旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)・[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。

## 再開手順

ビルド・stage・実機検証は[開発・検証手順](<development.md>)を参照する。
