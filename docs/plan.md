# Celtium 現状表（唯一の現在状態表）

開発ベース **0.1.4-alpha-rc.26**（[build定義](<../build-1122/build.gradle.kts>)）。Windows検証用プレリリースは **v0.1.4-alpha-rc.26**（2026-10-30、[release notes](<releases/0.1.4-alpha-rc.26.md>)）。これまでの性能検証は本文に記載した 0.1.1-alpha を基準とする。
2026-10-30 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)。[new_architecture.md](<new_architecture.md>) は参考の設計案で拘束しない。実測が勝ればそちらを採る。

## Windows/NVIDIA報告への優先対応（2026-10-29）

- **FBO/copy**: raw採用ID・8個制限・削除・逆引き・範囲、copy送信順とlayoutを修正。AMD/Intel成功・validation 0。Bloom画素やWindows受入ではない。

## 2026-10-24: 新アーキテクチャ案（2026-10-28 以降は参考扱い）

[new_architecture.md](<new_architecture.md>) の**§12 の結論**（offer ごとの Java 受理・capture・record 仕事を消す）だけを採用し、
順序は実測コストで並べ替える。**影**は本体内停止（既定OFF、[証拠](<evidence/shadow-stage-c-2026-10-24.md>)）。

## 次の作業単位（2026-10-25 改訂・利用者の実プレイ報告に対応）

1. **実プレイ報告の状況**（利用者: 体感200fps＋スパイク、描画バグは少ない）
   | 報告 | 状況 |
   |---|---|
   | 20 FPS | 体感200fps超（ログ上限修正後）。**残りはスパイク** |
   | 入world直後 約3秒の強いラグ | **縮小・qmagnetではCeleritas同等**: 未使用chunk dispatcherのbuilder 61個（~610 MB）→1個（`99e1daf77`）。最初60tickの50ms超フレーム Celtium 1.67–1.95 s / Celeritas 1.67–1.79 s。残りは共通のchunk packet・class load。利用者の3秒は未再現 |
   | スパイク・移動時の体感lag | 移動中はterrainのocclusion探索がclient threadの28%（同期実行）→ **探索をsearch threadへ（既定）**、AMD飛行p50 1.03–1.34→0.74–0.89 ms・validation 0。同条件のCeleritasは0.78 ms・16 ms超0枚。入室停止は未帰属 |
   | 視点移動で隠れた海底/洞窟が一瞬見える・GVCのmob/機体が見えない | **修正（実機確認待ち）**: 非同期探索の結果を、視点が変わったフレームだけ描画前にjoin（`22d8d84e4`）。Modのclient-array display list・直接の`GL11.glColor*`/`glDepthMask`・lit時ITEM形式（`eeb6b1d0e`）。A4のserver crashはMod側→`scripts/fix_gvcr2_a4.py` |
   | 殴られたentityが赤い箱 | **修正**: combine中もalphaテストを維持（帽子レイヤの透明部が赤塗り） |
   | 色付きガラスのチカチカ | **修正（実機確認待ち）**: 半透明terrainパスが背面cullなしで描いていた→vanilla同様に背面cull（`5f75fc009`、参照比で~20–25暗かった差が解消、[画像](evidence/2026-10-28-stained-glass-backfaces.png)） |   | F3+T が重い | reload自体 2.24 s（Celeritas 2.05 s）。差は font atlas の再upload → pack不変なら保持（`6d056197d`）。reload後のframeは正常 |
   | Xaero's Minimap（クラッシュ・真っ暗・位置ずれ・透け） | **修正**: PBO map/unpack、raw FBO/raw matrix、tile upload、mod FBO同士のsample（空mirrorより公開優先・self-compositeは同一imageだけ）。AMD/Intel world PASSED・validation 0、Celeritas参照と同じ絵 |
   | ワールド退出後ボタン無し | **未再現**（既定・日本語・1920×1080）。手順差の手掛かり待ち |
   | ロード画面 | **Forgeのsplashと同じ画面**（ロゴ・メモリ・進捗・Forgeロゴ）をnative描画（`3ebd22e90`） |
   | ログ量 | **タグごと16行上限**（`CeltiumLogLimiter`） |
   | E2E 1.93a（Cleanroom 0.7） | Intelの読込SIGSEGVと疎なatlas描画拒否は修正候補で各1回通過。最終jarは未再検証・画像比較未成立（[証拠](evidence/e2e-cleanroom07-2026-10-26.md)） |
   | vanilla post shader・発光outline | **native描画**（creeper/spider: `12a45471f`、outline合成: `ef807429c`。AMD/Intel world PASSED・validation 0。outlineはwithin-run A/Bで実測＝[証拠](evidence/glow-outline-2026-11-03.md): 2669px・対照0・noise 3.69%・`postChains=1`） |
   texture environment・空framebuffer初期化・公開cache・material寿命の整理は [実装と検証](evidence/native-texture-owner-2026-10-25.md)。
2. **性能**（KPI flags・1280×720・vsync off/260・`world-0.7.11` instance（Xaeroなし）・3対×240 s、両GPUとも 0.1.1-alpha の jar `9feaa083`（コードは配布jar `a8bd0480`と同一、差はjar内README）＝`54b42982d`）:
   - **AMD: goal条件1を満たす**（最新 `rel-amd`: verdict NOT_SLOWER、各対のp50/p95/p99比が全て<1、中央値 0.591/0.596/0.826、p50 0.63 vs 1.06 ms）。
     **この行は scene 限定**（`world-0.7.11`・Xaeroなし・旧JAR）。現行JARを実配布profileで測ると逆:
     `celtium-world-accept`（188 mods・`qmagnet`）3対で **Celtium が 1.18–1.29×遅い**（p50 median **1.215**、
     `goalCondition=NOT_MET`、[証拠](evidence/paired-kpi-round51-2026-11-04.md)）。frameBudget は
     `gpuShare=0.01`＝CPU律速。**差は 0.52 ms**（Celtium 2.946 / Celeritas 2.427）。
     **KPI と同じ warm 窓（400–600）の内訳**（[証拠](evidence/warm-window-split-2026-11-04.md)）: `loop` 2.28 =
     render 1.56 + **fmlTick 0.52** + display 0.10 + tick 0.08、`passSplit` frame 1.4 = world 1.0 + gui 0.4 + hand 0.1、
     `flush` 0.4（`winRecord` 0.3 = `wrecLoop` 0.2 + `winHead` 0.1）。**`gui` 0.4 のうち Celtium は `guiBatch` 0.1 だけ**。
     pair clear は `winHead` 内（**round 54 で既定 ON**、`-Dceltium.vulkan.pairClearInPass=false` が旧経路）: 同一JAR/flagsの2本で `winHead` **141→43 µs**、frame差は未分離。**Celtium 自身 ~0.7 ms を ~0.2 ms にしないと届かない。**
     **窓の統合（sticky pass）は棄却**（[証拠](evidence/sticky-window-pass-2026-11-04.md)）: merge 2.6/フレーム成立でも
     **warm p50 2.85→11.47 ms**、窓 23.5→20.8 の代わりに **`grpDraw` 0.9→9.6 µs/group**。理由未解明、再開しない。
     **font runの行列bakeも棄却**（[証拠](evidence/font-run-bake-2026-11-04.md)）: 94 run/フレームのうち**8しか畳めない**
     （offer 238.3→230.1、group 136.5→133.5）。残り86は**intervening flush**＝順序規則。既定OFF。
     **受理経路**（[証拠](evidence/accept-path-world-profile-2026-11-03.md)）: 失敗upload 28,272はdeferralへ、`supply`の`bytes=0`は計器の欠陥。
     実uploadは113回・3.85 ms/回＝内容hashのbyte単位ループ（[証拠](evidence/mirror-upload-census-2026-11-03.md)）。**窓flushが最大**（643 µs/フレーム）。
     **round 42–46 の内訳は検証レイヤを測っていた**（[証拠](evidence/frame-split-conditions-2026-11-04.md)）:
     検証OFF は frame 1.4、`vkCmdDrawIndexed` は 5.0→**0.9 µs**。
     **出荷構成の世界scene実測**（[証拠](evidence/world-frame-accounting-2026-11-04.md)）: 219 offer・113.6 group・
     22.1 flush・23.1 window/フレーム、受理**3.49 µs/offer＝0.77 ms**、group draw 0.24、window 0.30、present 0.20＝
     **Celtium自身1.36 ms**。**単一項目は0.53 ms以下で、1つ消しても0.6 ms差に届かない**＝micro-optの床。
     **受理は0.15 ms/フレーム**（round 55: decide 33+preflight 544+flush 189=766 ms/run、`parts[registry]` 834 ns/offer・memo 96%）。
     **窓の94%は`bindFramebuffer`**（183,841 flush 中 173,470＝33/フレーム、index上限は2/フレーム、
     [証拠](evidence/window-source-bind-2026-11-04.md)）。
     JFRは`CELTIUM_LEG_JFR`（attach）。
     **JFRの「フォント経路8.9%」は取り下げ**（[証拠](evidence/font-timing-brackets-2026-11-03.md)）: glyph 2,456個/run、
     captureの時計bracketは既定OFF。**warm windowはflagが要る**（`warmFromTick=400 warmToTick=600
     holdUntilTick=620`）。**font runを割るのはcolour witnessだけ**（[証拠](evidence/font-witness-census-2026-11-03.md)）:
     worldは94/フレーム、`colourWitness=false`のA/Bはmenu run 284→123・frame 3.8→3.7だが**grpDrawは0.9のまま**。
     既定は安全側（ON）。**off-target windowはframe imageを動かさない**（[証拠](evidence/off-target-frame-barriers-2026-11-03.md)）:
     `winBarrier` 0.3→0.2 ms。**offerの55%がslow path**、平均3235 nsは中央値の1.6倍（先取り説は棄却、[証拠](evidence/accept-distribution-2026-11-03.md)）。
     **拒否の内訳**（[証拠](evidence/merge-refusal-census-2026-11-03.md)）: 931,838 group openのうち
     **format 448,268（48%）**・no-mesh 190,619・texture 167,484。**formatは畳めない**（6は色をpush blockから、
     2は頂点から取る＝頂点変換は8bit量子化）。`s9`=99 nsがmerge述語、micro-optは床。
     **窓**: 現行sceneは23.2/フレームで**13.0がGregTechのpost chain**（`Shaders.renderFullImageInFBO`、
     [証拠](evidence/window-sources-2026-11-03.md)）。`rawWriteSampleGate`は発火しない。
   - **Intel: 未達**（最新 `rel-intel`: p50 0.896・p95 0.872、**p99 1.048**＝3.46 vs 3.30 ms（中央値）。kpi3 1.110→kpi5 1.051→1.048）。
     p99帯はtickを含むフレーム（3.5%）。tick本体がCeltiumで~10–15%遅い（vanillaコード全般、固定fpsでも残る）。棄却仮説と計器は[証拠](evidence/intel-p99-2026-10-28.md)。
   - **fps上限が無視されていた**（GL-lessのpaceが表示リフレッシュに固定、120上限→60 fps）→ world内は上限値でpace（`99632d996`、Intel 120/60 PASSED）。
   - 過去の棄却（barrier省略・texture跨ぎmerge・1 submit・glthread）は[証拠](evidence/intel-gap-2026-10-26.md)。
3. **native 状態・実行**: cull の面と winding・`GL_VIEWPORT_BIT`・`GL_LIGHTING_BIT` の部分復元・font run の拒否保持・no-op census は出荷経路で発火しない→生きた描画census `CELTIUM_GL_SURFACE_DRAWING`・display-list census は id<512 のみ→総量/id を報告（entity arm modelDraws=5545・拒否0）。証拠は `evidence/*-2026-11-03.md`。glint画素/inventory・actor較正は未対応。

4. **影・パックは Demonica の fork で**: 利用者指示により **Iris を設計参照にするのをやめ、Demonica を fork して Celtium 用にする**。
   Demonica の実体: **Iris の shader pipeline + Angelica の GLSM + 互換層**を、別途導入の Celeritas の上で動かすもの。
   Actinium から fork し terrain renderer は持たない。**Cleanroom の OpenGL context 上で動く**ため、Celtium では
   「Celeritas との接続面（terrain 描画・pack pipeline・GLSM）」を **Vulkan 経路との接続面に置き換える**作業になる。
   **別成果物（addon jar）**として実装し、core には取り込まない。
5. **本体の影scaffoldとshaderpack frontendは削除済み**（2026-10-27、旧アーキテクチャ削除）。影とpackはDemonica addonで実装する。
6. **ライセンス**: Demonica 本体は **AGPL-3.0**（〜0.4.0 は GPL-3.0）。Angelica/Iris 由来部分は LGPL-3.0、Actinium 由来は GPL-3.0、
   glsl-transformer は AGPL-3.0。**fork するなら fork 側も AGPL-3.0 になり、Celtium core とは別リポジトリ/別jarで境界を保つ**。
   LGPL 部分のチェリーピックはクレジット明記で可能。GLSM の丸ごと持ち込みはしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|

| **利用者報告の不具合（2026-10-23）** | **①②③修正済み・④実機確認待ち・⑤測定済み** | 実機報告＋画面 | ②未実行 | ①パノラマ ②エンドポータル ③F2黒スクショ（`nglGetTexImage`） ④ガラスちらつき（`5f75fc009`） ⑤残像＝静止0差分・tooltipの動きは連続3フレーム残留0（[証拠](evidence/tooltip-motion-2026-11-02.md)）・mod FBO bind中0（[証拠](evidence/tooltip-mod-fbo-2026-11-03.md)）・看板の文字0（[証拠](evidence/sign-text-afterimage-2026-11-03.md)）、⑥空の帯は**一度きりの遷移**＝損失は**記録側**（[証拠](evidence/sky-band-one-transition-2026-11-03.md)） |


| **画質（4画面＋QMAGNET）** | **settings画素一致・worldは16階調超 0.97/1.05 %（AMD/Intel）。menu背景は両GPU確認済み** | 両GPU PASSED・clean | 実測 | [証拠](evidence/image-quality-2026-10-28.md)・[menu](evidence/menu-capture-2026-11-02.md)・[legacy既定ON](evidence/legacy-immediate-default-2026-11-02.md)。修正: 半透明cull・lit clamp・Forgeブロックモデル・legacy既定ON・atlas実寸・色同期・menu背景。残り: 遠い壁1面の陰影勾配、loadingは参照なし。**creative tab の item icon と hover slotのhighlight を修正**（[highlight](evidence/creative-highlight-line-pipeline-2026-11-02.md)）。**creative grid は現行JARで参照一致**（[検証](evidence/creative-grid-verified-2026-11-03.md)）|
| **互換・画質・配布** | **0.7.11 で受入完了。0.6.13 は非対応（明示拒否）** | 実測（v0.0.14-alpha・AMD RX 6400 / Intel UHD 630） | 0.7.11: 完了 / 0.6.13: 対象外 | v0.0.14-alpha `90a7c505…` は qmagnet world で AMD/Intel PASSED・validation 0・画像gate一致。0.6.13（GLFW/LWJGL 3.4.1）はSDL3不在を検出して版を名指しで拒否 |
| **巨大pack互換（E2E-E 1.87.3）** | **AMD: fixture PASSED・validation 0、Intel未実行** | AMD | 未実行 | Cleanroom 0.7差はlocal置換と上流PR（`8f121acfb`）。残りは[証拠](evidence/e2e-cleanroom07-2026-10-26.md)末尾へ。 |

| **描画制約** | **native VK-only** | 利用者指示 | — | vanilla/Forgeの描画・状態・資源はnative経路が所有。GL変換は「Modが直接使うGL」専用、GL context/fallbackに依存しない |
| **次の標的** | **native実装へ** | — | 未着手 | ①model/immediate共通packetのnative実行 ②lit fog **修正**（[証拠](evidence/lit-fog-2026-11-03.md)） ③state/texture/target所有の統合。 |

## FPSの現状と残る道

- 現行の判定は上の表（`celtium-world-accept` 3対、p50 median **1.215**）。旧scene（Susy/ATM3）の
  ABBA値は artifact が現ビルドと一致しないため現在判定に使わない（[archive](<history/plan-archive-2026-10-21.md>)）。
- 残差は window 固定費と per-batch。pack 1 frame 26.7k–39.4k replay、segmentは**frame要求で自動拡張**。[根拠](evidence/model-record-growth-2026-11-02.md)
- 棄却: bind flush延期（2026-10-31、2回ともRADV SIGSEGV、[罠](<verification-traps.md>)）、indirect immediate、フレーム再利用（アニメ凍結）、
  **group数∝grpDraw**（font run 2.3×減で不変、[証拠](evidence/font-witness-census-2026-11-03.md)）。

## 撤回した前提・未確定（数値を前提にしない）

[archive](<history/plan-archive-2026-10-21.md>) の「2026-10-24 移動」節（KPI 2.29x・draw数・GC主因 等。再入条件つき）。
- GPU pass時間・present/readback・アップロード量・GC・フレーム内訳の旧値は、artifact/区間が現ビルドと一致しないため参考値にも使わない。
- validation clean・`exitCode=0`・色数gateは、描画不存在・正しい絵・画像一致をそれぞれ証明しない。

## 更新と参照

- [作業規約](<../AGENTS.md>) / [目標](<goal.md>) / [手順](<development.md>) / [計器](<instruments.md>) /
  [罠](<verification-traps.md>)。
- 計測の生ログと棄却済みの詳細は [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)。
  旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)・[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。ビルド・stage・実機検証は[手順](<development.md>)。
