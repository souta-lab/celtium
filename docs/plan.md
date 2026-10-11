# Celtium 現状表（現在状態・優先順・受入）

> **影対応の後続計画:** [shader-plan.md](shader-plan.md)。**フルVK化＋主要modのshaderless対応が完了してから着手する。** この計画を理由に現在の本体実装を中断・前倒ししない。

> **描画backendの決定（2026-10-10）:** Vulkan直結を維持する。SDL GPUへの全面移行は行わない。Angelicaはshader変換・model/TESR/文字のbatch化・状態と資源寿命の設計/試験を部品ごとに比較する。現在のGL除去と主要mod対応を優先し、影の実装は上記gateを満たしてから。汎用GLエミュレーションを新たな主backendにしない。

> goal: `goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`（revision 61, phase active / activation armed）。
> 全文は `docs/goal.md`。構造修復の方針は **`docs/handoff-structure-recovery-2026-10-10.md`**、
> 直近の原因候補は `docs/hints-2026-10-10-nether.md`。監査起点は `docs/audit-2026-10-09-third.md`。
> **round ごとの経緯と旧測定は `docs/history/plan-round-log-until-2026-10-10.md` に移した**（削除していない）。
> この文書は**現在状態を一箇所で管理する表**であり、末尾への訂正追記で整合させない。

> **エビデンスの保存方針（2026-10-10 整理済み）**: 全 run の `validation-process.json` と `logs/celtium-validation.json`、
> および **docs が引用している run** の `screenshots/` と `logs/latest.log` を残し、run 内の `mods/`・`test-libraries/`・
> `saves/`・`assets/`・`libraries/`・`validation-console.log`・`logs/debug.log` は削除する（harness が毎回作り直す）。
> `runtime/celtium-headless-interop/`（共有ライブラリ）・`jdks/`・`gpu-tools/` は消さない。詳細は
> `docs/handoff-2026-10-10-next-model.md` §11。**実績: runtime 16 G → 145 M、空き 2.1 G → 18 G。**

## 0. 直近の到達点（最新実測・冒頭表）

**この節が現在の受入状況である。** 個別の受入根拠は下の §0.1〜§0.92 にある（新しい節ほど新しい実測）。

| 項目 | 状態 | 最新の実測（GPU・条件つき） |
|---|---|---|
| terrain / immediate の native 描画 | **実装済・両GPU受入** | 既定受付 format 1〜9。1 run の内訳: `offers 13000 / accepted 10643 / GL 本体 2386`（AMD・軽い世界） |
| **線（GL_LINES 系）の native 化** | **受入済（AMD 797・Intel 280 accepted）** | 変更前は 17 accepted。5 run 連続 PASSED、線の拒否ゼロ |
| **per-frame present** | **受入済（AMD/Intel）** | 263 presented / 262 GL swap skipped（§0.17）。GL swap は `@Redirect` で停止 |
| **提示画像と GL フレームの画素一致** | **受入済** | 上下反転比較で 平均絶対差 0.015・最大 3（§0.35）。**§0.103 で提示画像は正立になったので、以降は反転なしで比べる**（resize 時は提示画像の左下 `dw×dh` 領域を screenshot と比較） |
| **GL 最終合成の停止** | **受入済（フラグ、既定 off）** | `glCompositesSkipped=717/718`、提示画像は off 比 平均絶対差 0.001（§0.36） |
| **GUI / font / HUD** | **画像等価を受入済** | HUD 帯（上部250行）平均絶対差 **0.000**・>8 が **0.00 %**。画面全体 0.015・0.04 %（§0.40） |
| **lightmap の供給漏れ（format 5）** | **解消・両GPU受入** | AMD `lightmapSuppliedOnDemand=2`・失敗なし・拒否ゼロ（§0.43）、**Intel も 2・失敗なし・拒否ゼロ・PASSED**（`p337-intel-lightmap`） |
| End の dimension 受入 | **両GPU PASSED** | capture 時 dimension 1 |
| **Nether の dimension 受入** | **AMD で絵・validation とも受入／Intel 未実施** | `validationClean=true`・msgs 0・capture 時 client/world/server すべて -1・`dimensionFramesInTarget=129`。根因は fixture 側（到着位置がポータル内＋`timeUntilPortal=0`）で修正済み。旧「両GPU受入」の記載は撤回済み。**画像ゲートも較正済み**: Nether は専用 pose 1.5/70/1.5 を持ち、参照 2 run（`nonBackground=0.938502`・`bands=720`）に対し GL-less 実測 **1973 / 0.938502 / 720 / 0.087951** で PASSED。overworld の pose は Nether の岩の中（0.183982 と 0.420806 の2値）で比較不能なため、床は scene ごとに持つ。**未完**: Intel での Nether 受入。§0.117 §0.119 §0.126 |
| 失敗経路（fence timeout / submit 失敗） | **両方とも clean・VUID 0** | fence: `stalledStops=1`・`stalledTeardownSkips=548`・解放しない。submit: fixture が FAILED として報告（§0.37） |
| **拒否の読み方（GL-less）** | **訂正: GL-less の「拒否」は GL fallback ではない** | GL-less では GL 本体が stub の no-op なので、拒否された batch は「GL が描いた」のではなく「**どこも描いていない**」。条件 (b) の GL 残量は **bridge 構成での拒否**から読む（そこでは拒否＝GL 本体が描く）。GL-less の拒否は「native で描かれなかった量」として別に読み、内容欠落の疑いがあるもの（frame 自身の blit は swap が提示済みなので欠落ではない）と区別する。この区別をせずに GL-less の拒否数を条件 (b) の残量に数えるのは過大評価。§0.118 |
| **条件 (b) の残量（実測）** | **約2割が GL** | 内訳: **format 2 の非画面合成 709**（FBO 所有権待ち）・**起動直後10フレームのみ 80**（本質的）・format 1/6/9 少数。lightmap 分は解消済み（§0.39/§0.41/§0.43） |
| **条件 (b) の本体（FBO 所有権）** | **既定で受入済（両GPU）** | **ゲームが Vulkan 所有 color/depth に直接描画し提示**。フラグなしの run で AMD/Intel とも **PASSED・VUID 0・GL エラーなし**、同一vendorの GL 経路と **AMD 0.001/0.00 %・Intel 0.026/0.02 %**（§0.59）。`-D…adoptGameFramebuffer=false` で旧経路に戻せる |
| **GL-context-less window（goal 項目6 / 条件 (b)）** | **達成（実 scene・model/TESR・Nether・compositor 到達まで）／残りは GL context 生成と起動直後** | GL context なしで world まで起動し、Vulkan が毎フレーム提示して GL swap を1対1で飛ばす（`p548-glless`: `presentedFromVulkan=287`＝`glSwapsSkipped=287`）。**実 scene を描画**し、**model/TESR は既定 ON**（エンチャント台の本が実ブロック位置、GL 参照と画素位置一致・§0.118）、**Nether は capture 時 dimension -1 で clean**（§0.117 §0.119）、**条件 (c) は marker 帰属＋番号付き presented dump との画素完全一致で充足**（§0.123）。lightmap の黒は announcement の配列形状が根因で修正済み（§0.116）。**未完**: 可視 glow（原因確定＝outline chain が off-target pair を共有。3つの一行変更は実測で失敗、chain 専用 image へ設計変更中・§0.127）、wedge の残存（thin streaks。巨大なものは N01 の model 頂点修正で消滅、format-8 帰属は撤回済み）、FBO/postprocess。**解決済みに更新**: depth format は能力ベースで選択（候補ごとに features＋image 受理を問い合わせ・AMD に 24-bit が無い実測）、画像床は scene/pose/variant を key に持ち未較正は `UNCALIBRATED_PRESENTED_IMAGE`、video settings は bridge で動作し GL-less では中身欠落として正しく FAIL。§0.114〜§0.127 |
| 条件 (b) の残り | **GL context の生成と、起動直後の本質的な拒否のみ** | **既定フレームバッファへ描く合成も native 化**（902件/run・受理 13202・GL 本体 2825・画像差なし・§0.60）。残る GL は起動直後10フレームと少数の pass 選択で、native の描画先がまだ無い瞬間のもの。**GL-less flag 経路（`-Dceltium.vulkan.glless=true`）は起動・提示・実 scene・compositor 到達まで実測済み**（§0.114〜§0.123）。既定は互換のため bridge のままだが、これは「未解決」ではなく選択である |
| **resize（項目7）** | **両GPU受入** | 恒久 stall を期間契約で解消（§0.102）し、提示の配置契約を GL composite に揃えた（§0.103）：提示は正立・1:1・左下アンカー・window clip、extent は surface 追随。resize run で描画領域 vs screenshot = AMD 0.001 %・Intel 0.000 %、stall 0 |
| **GPU 検証（条件 (d)）** | **AMD/Intel 両方で受入・ゲート修正済** | 描画側が選択確定時に `CELTIUM_NATIVE_DEVICE name="..." uuid=... matchedOpenGl=...` を出し、harness はこれで `--require-gpu` を判定する（device 未選択時のみ GL 文字列へ fallback、判定元を記録）。GL-less は `--render-node` では GPU を pin できず`VK_ICD_FILENAMES` が必要（pin で `vulkanDeviceCount 1`、無 pin は 3）。AMD RX 6400 と Intel UHD 630 の両方で bridge/GL-less とも PASSED・clean・VUID 0、ゲートが実デバイスを指名（Intel 実測: `gpuRequirementSource=nativeDeviceName`）。光量修正の効果も Intel で可視（GL-less band が (35.6,36.2,37.6) の黒から (102.5,63.9,58.7) へ、AMD と ±0.1・相関 0.9926）。**未検証 vendor/OS は Intel/AMD の Linux 以外すべて**。§0.118 |
| 実 window / compositor 出力（条件 (c)） | **充足（実測）／未測定は scanout のみ** | **実測**: `--headless` の私用 wlroots/Sway 出力（HEADLESS-1 1280×720）に対し `grim -o HEADLESS-1` で連番 capture。
`-Dceltium.vulkan.presentMarker=true` の間だけ **1280×720 全面が一様 magenta (255,0,255)**（14 capture）で、window 未 map の間は一様な黒（32 capture）＝出力の唯一の client は `Cleanroom`（xwayland・ゲームの pid・1280x720）。
**compositor capture 0035 は renderer の番号付き presented dump `frame-20.png` と 0/921600 画素差・最大チャネル差 0**。通常 run でも **capture 0066 = `frame-100.png`（0/921600 差）**で実 scene（fixture PASSED・presentedImageVerdict PASSED）。fixture 自身の presented 読み戻し（`presentedImageMeanAtCapture=170.0`・単一色）とも一致。
対照: GL screenshot は黒（presented と 99.925 % 差）＝ GL framebuffer は証拠経路ではない。旧記載の「`DISPLAY=:0` の root 0×0・合成器なし」は誤り（実測: sway 935 が `wayland-1`・HDMI-A-4 1920×1080@60 の実モニタ・Xwayland `:0`、`ffmpeg -f x11grab` は 1920x1080 を読める）。
**未測定は scanout のみ**（`kmsgrab` は DRM master 無し・非 root で不可）。§0.123・`docs/compositor-output-capture-2026-10-11.md` |
| **model 経路（表示リスト）の native 描画** | **既定 ON（§0.108）／TESR の list は未 capture** | 射影の出所を `glGetFloatv` から**行列ミラー**へ直して **18.49 % → 6.35 %**、モデルビューを **`V_now × C_compile⁻¹`** にして **2.19 %**（entity 場面・7通りで最良）。既定は ON（§0.108）。**未完**: 実測 `modelMeshLists=2 / modelQuads=12`＝腕と手持ちのみ。TESR の model（エンチャント台の本）は list 自体が capture されておらず、GL fallback に落ちて GL-less では何も描かれない。`celtium$drawModelList` は mesh の無い list を `modelMissingMeshes` として名指しで数えるようにした。§0.108 |
| **model mesh の頂点契約（N01）** | **修正済・両GPU受入** | `buildModelMeshes` が `nextVertex()` を quad ごとに呼び、4 隅が同一頂点を上書きしていた（頂点 Q・index は 4Q を指す）。per-vertex へ修正し契約検査（4 頂点/6 index・連番）を追加。**AMD で 36.9 % の画素が変化**（巨大 wedge 消滅）・Intel でも PASSED。§0.124 |
| **model mesh の寿命（N02）** | **実装・統合済／受入 run 待ち** | list ごとの compile generation、変更のあった list だけ再構築、旧 mesh は `VulkanRetireQueue` へ retire、失敗した部分構築は discard、teardown は `settleGpu` 成功後に `releaseAfterIdle`。担当の10ステップ probe 通過（live 2→3→3→2→3・stale/unmeshed/geometryMismatch なし）。§0.127 |
| **format 5（粒子）の descriptor gate** | **修正済・受入済** | 「描画経路が使わない pass を probe が要求していた」のが根因。73/73 受理・拒否 census 空（`1:606 2:191 4:1 5:76 6:568 7:464 8:128 9:180`）。§0.113 |
| **depth format の能力契約** | **実装済** | 候補（`D32_SFLOAT` → `D24_UNORM_S8` → … → `D16_UNORM`）ごとに features と `vkGetPhysicalDeviceImageFormatProperties` を問い合わせ、全て不可なら**候補別の理由つきで拒否**。AMD に sampled な 24-bit depth が無い実測に基づく。GL-less target と terrain pipeline が同じ決定を共有。§0.125 |
| **画像ゲートの較正（N06）** | **実装済・受入済** | calibration key に scene・pose・stack・sweep・allBlocks・entity flag・GUI scale を含め、key が違えば `floorsNotCalibrated` ＋ `UNCALIBRATED_PRESENTED_IMAGE`（**遡及適用しない**）。床は各 scene 自身の pose で較正し、閾値は下げていない。§0.126 |
| 配布物と docs の一致（N13） | **一部実施** | README を現状へ（GL-less で present まで・未完も明記）、性能節は制限付きの実測、`mcmod.info` の説明も同様。**jar 検証器が「既に離れた Java 8 契約」を強制していた**のを修正し、`verified: true`・`classVersions {46:111, 51:1, 52:613, 65:86}`。§0.127 |
| **entity 場面の残差（既定構成）** | **確定: fixture のキャプチャ仕様（描画欠陥ではない）** | format 5 = 実粒子（提出元 `ParticleManager`）。残差は**提示フレームの生存粒子**が写る画素そのもの（GL 側は除去済みで粒子画素 0）。除外方向で 0.32 % = ノイズ床。同フレームの静止 FLAME 比較で format 5 の色・位置は正しく、残差は経年差のみ（§0.104） |
| **通常場面（実体なし）の一致** | **両GPU受入** | 提示画像 vs GL の絵: **AMD 0.011 / 0.01 %**、**Intel 0.000〜0.016 / 0.00 %**（§0.77・§0.92 の撤去後も維持） |
| 全 entity / TESR・全 block state・全 mod | **未確認** | registry 254 種掃引（AMD）と部分掃引（Intel）は実績として保持 |

**未検証のまま残っているもの**: 文字（GL-less で vanilla font が即時モード no-op に落ちる。glyph は native 受理済みだが**実画像未確認**）、
可視 glow、wedge の残存、FBO/postprocess の native 化、全 entity/TESR の画質、全 mod 互換（最も重いのは文字）、
scanout そのもの（条件 (c) の compositor 出力到達は §0.123 で充足。走査出力は DRM master 権限が無く測れない）、
リブランド。**性能は実測済みだが制限つき**（§0.127 と README: 参照は 60Hz 提示間隔であって描画コストではなく、GPU 時間比較は存在せず、場面は1つ）。
**古い数値を最新版全体の一致率として使わない**（68.81 %・local 0.267/255 は当時の部分測定）。

## 0.126 **進行中**: フル監査（`docs/history/2026-10-11-full-audit-0903.md`）の P1/P2 を担当別に分割

監査は17件（P1 6・P2 11）を挙げ、6つの契約バッチを提案している。現在の担当と所有は以下のとおり。

| 監査 | 内容 | 所有ファイル | 状態 |
|---|---|---|---|
| N01 | model 頂点の上書き（`nextVertex()` が quad ごと） | — | **修正済み** `454fb3600`・AMD 36.9% 画素変化・Intel PASSED |
| N02 | model mesh の generation/retire/部分構築回収 | `VulkanDynamicMesh`・`VulkanModelQuadCapture` | 担当稼働中（builder への patch はテキストで受領予定） |
| N03 | 文字が GL-less の no-op 即時モードに落ちる | `FontRendererMixin`＋`impl/font/` | 担当稼働中（glyph 48000 提出/48000 受理/拒否0。実画像受入は未） |
| N04 | bridge の post chain が空 stand-in を入力にする | `VulkanPostChain` | 担当稼働中 |
| N05 | record した layout と submit した layout の不一致 | `VulkanPostChain`＋`VulkanGlTexture` | 担当稼働中 |
| N06 | 画像ゲートが scene/pose/variant を区別しない | `ValidationClient` | 担当稼働中（camera を view entity にも適用する修正は受領済み） |
| N07/N08/N17 | sprite の mip・ByteBuffer slice/境界・host 確保失敗の解放 | `VulkanGlTexture`・`VulkanTextureRegistry` | 担当稼働中 |
| N10 | 能力ベースの depth attachment 選択 | `VulkanDepthFormat` 他 | **実装済み** `9655ff681`（run 検証待ち） |
| N11 | 受理した線幅と実描画幅の不一致 | `VulkanTerrainGpuBackend` | **修正済み** `4b29bd028`（run 検証待ち） |
| N12 | ベンチの条件一致が欠測を検出しない | `benchmark_celtium.py` | 担当が修復・実測済み `22de2155f`（欠測拒否の強化は残） |
| N13 | README/plan/goal/配布物の同期 | docs 他 | 未着手 |
| N14-N16 | bounded wait・census の意味・descriptor ring | 未割当 | 未着手 |

**ビルド状態（この時点）**: 4担当が同時編集中のため `:celtium-vulkan:compileJava` は `VulkanGlTexture` の4エラーで止まる。
私の commit は自分のファイルのみで、他担当の作業中ファイルは未変更のまま保全している。

**運用上の確定事項（このセッションで学習）**
- **counter の不在を描画の不在と読まない**（bridge のレポートに model カウンタが元から無い・§0.125）。
- **ミキシン間で直接メソッドを呼ばない**（メソッドは対象クラスへマージされるため起動時 `NoClassDefFoundError`。§0.121 の経緯）。
- **混合構成で「絵が止まった」と結論する前に、renderer 自身の番号付き dump と fixture の dump を分けて読む**。
- 計器の意味を変えたら、その名前と意味を変える（`mirrorMisses`→`stubTransformCalls` の経緯）。

## 0.140 **wedge の判別結果: pitch 符号ではなく「固定回転」／N10 の UBO 経路が入った**

### 1. 判別測定（私が実行・同一カメラ・lookDown=true / pitch +75）

```
frame=7 group=55 fmt=6 worldPass=true texture=115 ... ndc x[-42.46..0.00] y[-3.97..0.35] z[0.85..1.00] behind=1   <- 月
frame=8 group=62 fmt=6 worldPass=true texture=114 ... ndc x[-16.86..-16.86] y[-1.38..-1.38] behind=3             <- 太陽（画面外の一点）
frame=8 group=63 fmt=6 worldPass=true texture=115 ... ndc x[-13.69..0.00] y[-0.35..3.55] z[0.95..1.00] behind=1   <- 月
```

**pitch を反転しても月（texture=115）が画面を覆ったまま**で、太陽は画面外の一点のまま。
→ 担当が用意した判別基準どおり、**pitch 符号の反転ではなく固定回転の誤り**（sky の `rotate(-90,0,1,0)` か
celestial step の二重適用/欠落）。次は**別の world time** の run で切り分ける。

**確認済みの周辺事実**: mirror の `rotate`（`VulkanGlState:158-180`）は glRotatef の仕様どおりで**正しい**（列優先の全要素を検算）。
原典の sun/moon 部は `pushMatrix` → `color` → `rotate(-90,0,1,0)` → `rotate(celestialAngle*360,1,0,0)` → `begin(7, POSITION_TEX)`（`RenderGlobal:1402-1430`）。
同じ frame の terrain は正しいので、**mirror のカメラ部は正しく、sky の追加回転だけがずれている**。

### 2. N10（UBO 経路）受入 — `ac62e83dd`

担当が実装・実測。**color_convolve 192B・spider 144B**（監査の 180/140 と旧 196 は誤り。shader のコメントは `Saturation@192` だが**コンパイラは 188 を出す**——旧表はコメント由来だった）。
4本の UBO 変種＋descriptor ring と同一スロットのホスト可視 ring＋per-pass の経路選択＋**名前つき拒否**。shader 自身のオフセットと packer の報告を突き合わせるので、不一致は破壊ではなく拒否になる。

**同梱の実バグ修正**: `ShaderGroupMixin` の extras が 88 始まりだったのを **84** へ。
invert の `InverseAmount` は shader が 84 を宣言しているのに 88 に詰められ（native enderman が GL の 0.8 に対して 0.0 を読んでいた）、bits の Resolution/Saturation/MosaicSize も4バイトずれていた。

**監査記述の訂正2点**: (a) **この GPU は `maxPushConstantsSize=256`** を報告するので「両GPUとも128」は本機には当てはまらない（経路は guard 付き override で行使。担当はこれを emulation として明示）。
(b) **fixture の native vs GL 画像は別の surface を比較している**——chain が native で走る leg では Vulkan が window を提示し、GL-chain の leg の保存画像は stale な GL framebuffer。
したがって画素の主張はしない（presented vs presented の測定が必要）。

## 0.139 **wedge の根本原因を測定で特定: `renderSky` の月の quad が反転して描かれている**

wedge 担当が計器（`-Dceltium.vulkan.groupBounds=true` → `CELTIUM_GROUP_BOUNDS`）を作り、15 run の再測定で**原因を特定**した。

### 帰属（terrain のある画で再取得）

allow-list 掃引（frame-18・同一カメラ）: **only-6 が 20388 uniq・exactClear 18.0%・yellowgreen 4227・black-top-right 4603 ＝ wedge そのもの**。
他の format 単独（only-1/5/2/7/9）はいずれも wedge を再現しない（yellowgreen 813-1117・black 132 ＝ scene 自身のテクスチャ）。
**format 6（POSITION_TEX）単独で wedge が再現する**——terrain のある画での再確認。

### 原因（`CELTIUM_GROUP_BOUNDS` の実測）

```
frame=23 group=896 fmt=6 worldPass=true texture=114 proj=[m00=0.71 m11=1.26 m22=-1.00 m32=-0.05] ndc all behind  <- 太陽（RenderGlobal:1409, y=+100）
frame=23 group=897 fmt=6 worldPass=true texture=115 proj=[...] ndc x[-41.70..0.00] y[-3.96..0.35] z[0.85..1.00] behind=1  <- 月（:1425-1428, y=-100）
```

月 group の NDC footprint が**測定された artefact と一致**（x<0＝左半面 cols 0-622・y[-1..0.35]＝rows 0-718）。frame 内で画面を覆う bounds を持つ唯一の group。
vanilla は world time 6000 で太陽 +100・月 -100、カメラは 14° **見上げ**なので、太陽が手前・月が背後でなければならない——**実行は逆**。
両 group は別の頂点（u=0 と u=0.25＝sun.png と moon phases）を持つのでテクスチャ/形状の取り違えではなく、
**world-pass の sky group が持つ行列が vanilla のそれと違う**（同じ frame の terrain は正しい）。

**裏取り**: Nether travel（`dimension=-1 dimensionAtTick=80`）では yellowgreen=0・wedge なし（非 surface dimension では renderSky が sun/moon の前で return）。
bridge（実 GL が同じ2 quad を描く）にも wedge は無い。

### 次の一手

行列の出所は `captureGlMatrices`（`VulkanTerrainGpuBackend:7708`）の world-pass 枝（mirror の projection/modelview を使う）。
判別測定: 同一 jar で `-Dceltium.validation.lookDown=true`（pitch +75）。
- 太陽が画面を覆う側へ回れば **pitch 符号の反転**。
- 両方が動かなければ**固定回転**（sky の `rotate(-90,0,1,0)` や celestial step の二重適用/欠落）で、別時刻の run が切り分ける。

計器は**既定出力を変えない**（property 無しでは何も呼ばず何も出さない）。`enqAll`/`enqFormat` で `CELTIUM_IMMEDIATE_ENQ` を全 group へ広げられ、`frame=`/`group=`/`target=` を持つので番号付き dump と結び付く。

## 0.138 **決着: terrain 入口は生きている（1カウンタで確定）／GL-less allBlocks は PASSED に復帰**

§0.137 で残した「terrain はどの入口から描かれているのか」を、`renderBlockLayer` のカウンタで決めた。

```
CELTIUM_BLOCKLAYER calls=1 layer=Solid pass=2
CELTIUM_BLOCKLAYER calls=3 layer=Cutout pass=2
```

→ **`RenderGlobalMixin.renderBlockLayer` は実行されている**。Kirino の engine は Celeritas の chunk-layer 経路を（名前ベースの走査では見えなかった形で）通っている。
したがって**死んでいるのは entity/TESR の4注入だけ**で、terrain 経路は健全。名前で見つからなかった理由は未解明のままだが、
**「入口が生きているか」という問いには答えが出た**（推測を1カウンタで置き換えた形）。

**同じ run の画像**: `worldFrameNonBackgroundFraction=0.732916`・`presentedImageVerdict=PASSED`・`presentedDistinctColours=10048`。
＝**フック撤回後の GL-less allBlocks はゲートを通る**（退行前の水準に戻った）。

**残る死んだ注入（4）**: `RenderGlobal.renderEntities` ×2・`EntityRenderer.renderWorldPass` ×2。
Cleanroom 専用なので、re-target するか根拠つきで外すかを決める（§0.136/§0.137）。

## 0.137 **死んだ注入の全数掃引と、未解決の1点（terrain の入口）**

§0.136 の掃引を続けた結果。

### 死んでいる注入（確定）

| 注入先 | 数 | 根拠 |
|---|---|---|
| `RenderGlobal.renderEntities` | 2 | 呼び出し元は `EntityRenderer.renderWorld` 1400/1466 行のみで、その `renderWorld` を Kirino が置き換え |
| `EntityRenderer.renderWorldPass`（`func_175068_a`） | 2 | **Kirino のクラス群に `renderWorldPass` / `func_175068_a` の参照が0件**（jar 内 448 kirino クラスを走査） |

→ `RenderGlobalMixin` の4注入はこの runtime では一度も走らない。**「動いているつもりで一度も走らないフック」**。

### Kirino が `RenderGlobal` に実際に行う呼び出し（notch 名で確認）

`KirinoClientCore` 内の `MINECRAFT.g.*` は4種類のみ:
`renderSky(partialTicks, 2)`・`drawSelectionBox(...)`・4引数の雲系・2引数のもの。
**`renderBlockLayer` 相当（BlockRenderLayer を取る呼び出し）は見つかっていない**。

### 未解決（正直に）

**私たちの terrain はこの runtime でどの入口から描かれているのか**が未確定。
- 計測上は terrain は描かれている（`celtium/vulkan-check passes=1109 skipped=0 errors=0`、画にも床がある）。
- しかし Kirino が `renderBlockLayer` を呼ぶ形跡が見つからない。
- 可能性: (a) Kirino の `RENDER_OPAQUE` フェーズが Celeritas の経路を別名で叩いている、(b) 別の入口（`SimpleWorldRenderer` 経由）、(c) 私の走査が notch 名の取りこぼしをしている。

**次の計測（安い）**: `RenderGlobalMixin.renderBlockLayer`（114行）に**呼び出し回数カウンタ**を1つ足し、レポートに載せる。
0 なら入口が違うので実際の入口を特定する（Kirino の `RENDER_OPAQUE` 実装を読む）。1以上なら私たちの terrain 経路は生きており、
死んでいるのは entity/TESR の2経路だけ、と確定する。**推測で埋めず、この1カウンタで決める。**

## 0.136 **金脈続き: Kirino は `EntityRenderer.renderWorld` ごと置き換えており、私たちの `renderEntities` 注入2つは死んでいる**

`KirinoClientCore` をデコンパイルして判明した決定的な事実。

### 1. world render 全体が置き換えられている

`KirinoClientCore.EntityRenderer$renderWorld(long finishTimeNano)` が **`EntityRenderer.renderWorld` を置き換える**。中で Kirino は:

- `MethodHolder1.updateLightmap / setupCameraTransform / setupFog / getFOVModifier` を **delegate 経由で呼ぶ**（＝これらは実行される）
- 空を `MINECRAFT.g.a(partialTicks, 2)`（= `RenderGlobal.renderSky`）で描く。その直前に **`bus.F()`（loadIdentity）＋`Project.gluPerspective(...)` を2回**呼ぶ
- `cullingPatch.collectEntitiesInView(...)` で可視 entity を集める
- `entityRenderingPatch.renderEntities(...)` と `tesrRenderingPatch.renderTESRs(...)` を呼ぶ（**自前の実装**）

### 2. 私たちの注入は発火しない

`RenderGlobal.renderEntities` の呼び出し元は原典では **`EntityRenderer.renderWorld` の 1400 行と 1466 行だけ**。その `renderWorld` を Kirino が置き換えているため、
**`RenderGlobalMixin` の2つの注入（`sodium$renderTileEntities`＝Celeritas の TESR 描画、`renderEntities`＝Celeritas の entity ループ再実装）はこの runtime では一度も走らない。**

描画が成立しているのは、Kirino が **vanilla の下位 API（`RenderManager`・`TileEntityRendererDispatcher`）を直接呼ぶ**ためで、その描画は私たちの即時パス（BufferBuilder upload）が捕捉している。

### 3. これで説明がつくこと

- **TESR が「capture されない」**わけではない。Kirino が `TileEntityRendererDispatcher` を直接叩き、その draw は即時パスに乗る。Celeritas 側の TESR 経路は死んでいるだけ。
- **entity outline が発火しない**のも二重に確定: vanilla のループが走らない（Kirino が置き換え）＋ Kirino 自身は `outlineEntities` を捨てる（§0.135）。
- **空の描画は生きている**（Kirino が `RenderGlobal.renderSky` を呼ぶ）ので、`VertexBuffer.drawArrays` の no-op ギャップ（天球 gradient・星・地平面）は**実行経路の事実**として確定。
- **射影チャネル**: Kirino が `loadIdentity` ＋ `gluPerspective` を2回呼ぶ。mirror は loadIdentity を見て、gluPerspective を見ない。私の撤回したフックはこの2回も拾っていた。

### 4. 次の作業（この発見から出る具体項目）

1. **死んだ注入の扱いを決める**: Cleanroom 専用なので、`renderEntities` を狙う2注入は re-target するか根拠つきで外す。**「動いているつもりで一度も走らないフック」は最も危険な種類**。
2. **他の注入も同じ検査をする**: Kirino が置き換えたメソッド（`renderWorld` 本体と、そこから呼ばれていた経路）を狙っていないか全数確認する。
3. **outline は自前で native 実装**（§0.135 の結論を再確認）。`MinecraftCulling.entitiesInView` が対象集合を public で持つ。
4. **空/星/地平面**（`glDrawArrays`）の native 化。

## 0.135 **金脈: 実行時パッチ（Kirino）をデコンパイルして判明 — この runtime は entity outline を描いていない**

CFR（`/tmp/cfr.jar`、Maven Central から取得）で `cleanroom-0.6.13-alpha.jar` 内の Kirino クラスを Java に戻して読んだ。
`KirinoConfigHub.Runtime` は `enable = true`・**`enableRenderDelegate = true`**（既定）で、**Kirino の render delegate が world 描画を駆動している**。

### 1. `MinecraftEntityRendering.renderEntities(...)` は outline を集めて**捨てている**

```java
ArrayList<vg> outlineEntities = new ArrayList<vg>();
for (vg entity : this.cullingPatch.entitiesInView) {
    ...
    renderManager.a(entity, partialTicks, false);
    if (this.isEntityOutlineActive(entity, renderViewEntity, player, camera, gameSettings)) {
        outlineEntities.add(entity);          // ← 集める
    }
    ...
}
entityRenderer.h();                            // ← ここで終わり
```

**`outlineEntities` はその後どこからも使われない**——vanilla の `RenderGlobal.renderEntities` が行う
「outline framebuffer を bind し、`entityOutlineShader.render()` を呼び、戻す」処理が無い。

### 2. 裏取り（全数）

- `cleanroom-0.6.13-alpha.jar` 内に **"outline" を含むファイルは 0 件**。
- world render を置き換えている `KirinoClientCore` に `entityOutline*` / `loadShader` / `ShaderGroup` の参照は無し
  （`B.c("outline")` は **Profiler のセクション名**で、中身はブロック選択枠の描画）。
- Kirino は camera/projection も自分で駆動しており、**`Project.gluPerspective` を直接呼ぶ**（`KirinoClientCore` 160/165行）。

### 3. これが意味すること（戦略判断）

- glow シーンの `postChains=0`・`entityOutlineFramebuffer` が一度も bind されないのは、**私たちの renderer の欠陥ではなく、
  この runtime の patched render path が outline pass を実行していない**ため。glow 担当の「in-frustum の光る entity が居ないから」は**誤った説明**で、
  list は実際に作られ、ただ捨てられている。
- したがって監査 N04 の「GL-less outline が正しい画像であること」は、**この runtime のままでは観測不能**。
  選択肢は (a) Cleanroom 側の対応を待つ、(b) **私たちが outline pass を native で実装する**、(c) render delegate を切る（設定は見つかっていない・既定 true）。
- goal は「バニラの ShaderGroup・spectator 効果・**entity outline 等は本体機能であり、この延期を理由に省略しない**」と定めているので、
  **(b) が目標に沿う道**。`MinecraftCulling.entitiesInView` は public フィールドで、対象 entity の集合は読める。
  自前で silhouette → blur → composite を native に描けば、runtime が描かない glow を私たちが供給できる。
- **射影チャネルの再設計**にも効く: Kirino 自身が `Project.gluPerspective` を呼ぶため、私の撤回したフックは
  「vanilla の1回」ではなく「Kirino の複数回」も拾っていた。次に設計する時はこの経路を前提にする。

**方法の記録**: 実行時パッチは難読化名でコンパイルされているが、`scripts/mc-sources.sh --notch vg bxy brx` で
`notch-mcp.srg` から名前を引ける（`vg`=Entity・`bxy`=ICamera・`brx`=ChunkProviderClient・`bsb`=WorldClient）。
デコンパイラは `curl -sL https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar` で取得できる。

## 0.134 **フル監査（2026-10-11 10:58）の受領と処置**

監査は新規5件（P1 3・P2 2）と、所有権の穴3箇所、N01〜N17 の再監査、保持すべき改善の一覧を出した。

| 監査 | 内容 | 処置 |
|---|---|---|
| **A01 P1** | glyph の `memAddress` が flip 無し＝1つ先を指す（両分岐） | **修正済 `aa041c2d4`**（flip＋書いた範囲の検査）。CPU 実測つき（`delta=96`→flip 後 0）。担当へ「過去の実験は無効」と通知済み |
| **A02 P1** | bounded wait の timeout が無条件 finally で fence/buffer を破棄（VUID fence-01120 / commandBuffers-00047） | **修正済 `2fa82dac4`**: 3つの結果（submit 前失敗／完了／timeout）を分離。timeout は**隔離**し、context を unusable にして以後の submit を拒否、解放は `delete()` の device-wide wait 後のみ |
| **A03 P1** | `ValidationClient.referenceCalibrationKey` が **現在の候補**の property を読むため、参照 variant が候補に追随する | **未着手**（fixture）: 参照キーを hash 固定した参照 run の不変メタデータにし、「候補を変えても参照キーが変わらない」CPU 検査を足す。**失敗画像は FAILED のまま**（ゲートを上書きしない） |
| **A04 P2** | `requiredUploadBytes(MAX,MAX,direct)` が long 溢れで負を受容 | **修正済 `ae7801d5c`**（`multiplyExact`）。実測: 4x4=64 は不変・MAX×MAX は名前つき拒否 |
| **A05 P2** | watchdog が共有フラグを読み、古い watchdog が新しい wait を偽 stall にできる | **修正済 `f82d750f9`**（呼び出しごとの token＋完了 token、stall は token を名乗る） |

**所有権の穴（未着手・担当割当て待ち）**: `HostBuffer.create` の部分解放／`DynamicMesh` コンストラクタが2本目失敗時に1本目を漏らす／`buildModelMeshes` の catch が `built.put` 前の例外を回収しない。
**N17 の image fault は `vkCreateImage` 前**に発火するため部分画像解放の証拠にならない（staging 側は実証済み）。

**保持する改善（監査が明示）**: N01 の頂点ループ修正・layout token/ledger・per-mip upload と slice hash・幅の拒否・`COUNT` テーブル・ベンチの欠測拒否・Java21 配布検査（`verified=true errors=[]` class65 上限）。
**未行使**: foreign transition の abandon（unit 5/5 だが実 run で未発火）。

**画像は依然 FAILED**: `w9-only67` 巨大三角形/nonBg 0.364906・`iso-glow-after-a` 単色/nonBg 0。VUID0・exit0 でも `validationExit1`。
**固定ピン**: 正常 `d2073e9456da7bcc` vs 退行 `c1d4e7db53465129`（比較用）。

## 0.133 **原典の正体を確定**: tree は既にパッチ済みで、真の差分は Cleanroom 自身の実行時パッチ（Kirino）

「パッチ適用済みの deobf を既定にしろ」という指摘を受けて確定させた。

### 1. Cleanroom の MC パッチ＝私たちが既に当てているパッチ集

Cleanroom のリポジトリは `module/minecraft/patches/`（**539ファイル**、`.mappings.json` は `mcp:stable_39-1.12`）を持つ。
**これは Forge のパッチ集そのもの**（Cleanroom は Forge fork）で、私たちの RFG ビルドが既に適用している。実測で確認:

- `client/gui/FontRenderer.java.patch` は **逆適用（17/17 hunk が既に存在）**＝内容一致。
- `RenderGlobal.java.patch` / `GuiIngame.java.patch` の**追加行が tree に逐語で存在**（行番号ずれで patch は失敗するが内容は同じ）。

→ **MC 本体について1ファイルずつ「実行時と違うか」を確認する必要は無い**。tree はパッチ済み。
`scripts/mc-sources.sh --cleanroom` がこの事実とパッチ集の場所を報告する。

### 2. 真の差分: Cleanroom 自身の実行時パッチ（Kirino）

`cleanroom-0.6.13-alpha.jar` 内の **Kirino**（Cleanroom の描画エンジン）が**難読化名で MC に注入**する:

| クラス | 対象（notch→MCP） |
|---|---|
| `MinecraftCulling.collectEntitiesInView(vg, bxy, brx, float)` | `Entity`・`ICamera`・`ChunkProviderClient` |
| `MinecraftEntityRendering` | vanilla の entity ループ再実装（`countEntitiesTotal/Rendered/Hidden` を持つ） |
| `MinecraftTESRRendering.renderTESRs(vg, bxy, …, float, int)` | **TESR ループの置き換え** |

**毎 run 有効**（`latest.log`: `kirino_engine`/`kirino_ecs`/`kirino_gl` が初期化）。

**意味**: 「TESR の list は capture されていない」「glow の outline は in-frustum の光る entity が居るときだけ埋まる」は
**vanilla のコードについての結論**であり、実行時は Kirino の culling/entity/TESR ループが動いている。
`postChains=0`・`entityOutlineFramebuffer` が一度も bind されない件は **Kirino を読んでから**でないと説明にならない。
notch→MCP は `scripts/mc-sources.sh --notch vg bxy brx`（`notch-mcp.srg`）。

## 0.132 **P1 修正: glyph の頂点アドレスが「書いた最後の1バイトの先」を指していた**

監査（`CELTIUM_AUDIT_20261011_1058_URGENT_GLYPH_POINTER_CX`）の指摘を実測で確認し、`aa041c2d4` で修正した。

**バグ**: `CeltiumFontGlyphCapture.submit()` は `VERTEX` に 4×24 バイト書き込んだ後、`flip()` せずに
`MemoryUtil.memAddress(VERTEX)` を取っていた。**`memAddress(ByteBuffer)` は base + position を返す**ため、
position が capacity に達した状態では**書いたバイトの1つ先**を指し、backend は続く 4×24 バイト（別のデータ）を読んでいた。
隔離バッファ（4×16=64）も同一の欠陥。

**GPU 不要で実測**（`scripts/cpu-contract/GlyphAddressCheck.java` を追加）:

```
position=96 capacity=96
memAddress(buf) - memAddress(buf,0) = 96      ← position が加算される
after flip: position=0 limit=96 delta=0
first vertex x=1.0 expected 1.0               ← flip 後は先頭頂点が読める
```

**修正**: 両分岐で `flip()` してからアドレスを取り、**書いた範囲を検査**（合わなければ `address-extent` として拒否）。
これで将来の退行が「静かに間違ったポインタを submit する」ことはできない。

**なぜ文字が出なかったか**: Vulkan から見て範囲外ではない（拒否も VUID も出ない）ので**エラーにならず、単に違うバイトだった**。
したがって担当が行った mode/quads・テクスチャ隔離・画面充填の実験は**すべて同じ壊れたアドレスを共有**しており、
**有効なバイトで再実行するまで何も棄却できない**（次のフォント run）。

**同族バグの掃引（全数）**: 即時描画の主経路 `WorldVertexBufferUploaderMixin:144` は
**原典で安全と確認**——vanilla の `BufferBuilder.finishDrawing()`（`BufferBuilder.java:551-561`）が
`byteBuffer.position(0)` と `limit(getBufferSize()*4)` を設定してから upload するため、`memAddress` は base を返す。
したがって**「shard の原因も同じ」とは言えない**（監査の留保どおり）。他の `memAddress(buffer)` 使用箇所は
テクスチャ側（`VulkanGlTexture:2496` に同じ罠のコメントあり）と stub の内部バッファで、いずれも position を明示管理している。

## 0.131 **フック撤回後の実測**: terrain が戻り、glow シーンは黒率で落ちる

フック撤回（`f82bf4612`）後の現ツリーで測定:

| run | scene | nonBg | mean | distinct | verdict |
|---|---|---|---|---|---|
| `ab-noglu` | allBlocks | **0.838628** | 44.0 | 22706 | **PASSED** |
| `glow-after-revert` | allBlocks + glowEntities | **0.784304** | 155.41 | 49800 | FAILED |

`glow-after-revert` の失敗理由は具体的で、追跡可能:

```
FAILED: presentedBlackFraction=0.215696 is above the scene ceiling of 0.05
        for scene=terrain (overworld (dimension 0))
```

**21.6% が黒**——glow シーンで輪郭チェーンが入った状態の画が、黒い領域を大きく残している。ゲートは正しく働いている（床を下げない）。

**未確認**: この run の artifact に `postChains` / `postAdopted` / `offTargetPairs` / `chain-pair=` が**出ていない**（`logs/` にも console にも無し）。
計器は実装されているのに、この構成ではレポートへ届いていない——**「計器を作ったのに読めない」型**なので、次に閉じる。
そのため「glow 経路が発火したか」はまだ言えない。

## 0.130 **重大な誤りの訂正**: GL-less の terrain 欠落は 09:33 からの退行で、ゲートは正しく FAIL と言っていた

### 観測（wedge 担当の測定・時系列は artifact から再構成）

- GL-less の allBlocks 画は **09:16 までは床があった**（`p1-intel` nonBg=0.7346・`w3-base` 0.8226・`p1-model-fix` 0.7356）。
- **09:33 の `font` run 以降、すべての GL-less allBlocks run が nonBg=0.364906・背景 0xB3CFFF**（byte 単位で再現）。
- `-Dceltium.vulkan.immediate=false` で frame は**単色**、既定 run でも右下象限が**100% clear 色**＝**terrain が1ピクセルも描かれていない**。
- terrain backend 自体は動いている: `passes=1109 skipped=0 errors=0` だが **`lastDraw=8 lastSections=8`**（正常時 09:16 は `lastDraw=20 lastSections=23`）＝**可視セクションが 23→8 に減った**。
- bridge leg も同じ run で床を失っている（`wedge2-bridge` 右下 98.4% clear）＝GL-less 固有ではない。

### 原因候補（09:17〜09:30 の3コミットに限定）

| commit | 時刻 | 判定 |
|---|---|---|
| `b7b634179` unbound-framebuffer 拒否 | 09:17 | **除外**: `4642c145e` で revert 済み、その後も退行継続 |
| `30a30a375` 射影フック（`Project.gluPerspective`） | 09:27 | **有力**: 両 leg に効く。可視セクション数が変わったのは射影（frustum culling）が変わったことと整合 |
| `9655ff681` depth format | 09:30 | **AMD では無影響と実測**: `scripts/vulkan_depth_format_probe.py` で AMD は `D32_SFLOAT` を公開し、chooser の第一候補＝旧定数と同一 |

### **私の誤り（これが本項の主題）**

画像ゲートは 09:33 以降ずっと `sceneGateFloorsEnforced=true` / `sceneCalibrationKeyMatches=true` / **`presentedImageVerdict=FAILED_PRESENTED_IMAGE`** を出していた。
**ゲートは正しかった。** 私が「mean 213 の FAILED は固定 scene の正常状態だから counter を読め」と担当（texture 担当）に伝え、
自分の run でもその verdict を「較正/カメラの問題」として扱った。**失敗しているゲートを自分の解釈で上書きした**のが誤りで、
その結果 terrain 欠落を数時間見落とした。

**規律に追加**: 失敗しているゲートは、測定が誤りだと示すまで**証拠**である。verdict を「既知の正常」と言い換えて
counter だけを読む運用をしない。ゲートを上書きする判断をする場合は、**同じ fixture・同じカメラで 2 つの jar を比べる**
ような、カメラ差と renderer 差を分離する実験を先に取る。

### 判定（A/B 実測・確定）

同じ fixture・同じカメラ・同じフラグで **jar だけ**を変えた:

| | A: 退行前の固定 jar `d2073e9456da7bcc` | B: 現行 jar | C: 現行 jar・**フック撤回後** |
|---|---|---|---|
| `presentedNonBackgroundFraction` | **0.733183** | 0.364906 | **0.838628** |
| `presentedImageMeanAtCapture` | 213.6667 | 213.6667 | 44.0 |
| `presentedImageVerdict` | **PASSED** | FAILED | **PASSED** |
| `presentedDistinctColours` | - | - | 22706 |
| `lastDraw/lastSections` | 22/25 | 8/8 | - |

mean がビット一致＝**カメラは同一**。したがって **renderer の退行**で、原因は `30a30a375`（私の `Project.gluPerspective` フック）。
フックは culling が読む射影スタックに掛かるため、意図した「空の batch の射影」を直す代わりに **world を cull で消していた**（25→8 セクション）。
`f82bf4612` でフックを撤回し、床が戻った（C）。

**代償**: フックが対象にしていたギャップ（mirror が `Project.gluPerspective` を見ない＝空 fan・太陽/月・雲がカメラでない射影で描かれる）は戻る。
これは 09:16 の状態で、world が消えるより厳密に良い。**次にこのチャネルへ手を入れる時は、餌である culling を測る**——
`lastDraw/lastSections` を画像 counter と並べて report に載せ、試行ごとに A/B を取る。一行のフックで済ませない。

### 元の実験計画（記録）

同じ fixture（現在の固定カメラ）× 2 jar（09:16 の正常 jar `d2073e9456da7bcc` と現行）で nonBg と frame を比較する。
- A（正常 jar）で床が出て B（現行）で出ない → renderer の退行で、窓は上記3コミット。
- A でも空だけ → renderer は退行しておらず、**fixture のカメラが変わった**ことになる（その場合、私は正常な空の画を
  何時間も「壊れた画」として扱っていたことになる）。

## 0.129 **原典から確定したこと（デコンパイル済みソース）** — 推測で潰していた問いの決着

`.celtium-work/mc-sources/` の原典を引いた結果、**推測で何度も往復していた問いに直接の答えが出た**。以下はすべてソースの行番号つきで、
bytecode 読みや仮説ではない。

### 1. バニラの描画パス × 頂点形式の全表（`client/renderer` ほか）

| mode | format | 回数 | 私たちの id | 代表 |
|---|---|---|---|---|
| 7 QUADS | `POSITION_TEX` | 11 | **6** | 空・雲・水面・太陽/月（`RenderGlobal:1409`） |
| 7 QUADS | `POSITION_TEX_COLOR` | 5 | **2** | 天球の帯・天候（`RenderGlobal:1288`） |
| 7 QUADS | `POSITION_TEX_NORMAL` | 4 | **9** | 最終 composite |
| 5 STRIP | `POSITION_COLOR` | 4 | **1** | 天球 gradient の一部・線 |
| 7 QUADS | `POSITION_COLOR` | 3 | **1** | 汎用 |
| 7 QUADS | `PARTICLE…LMAP` | 3 | **5** | 粒子 |
| 3 LINE_STRIP | `POSITION_COLOR` | 3 | **1** | デバッグ線 |
| 7 QUADS | `POSITION_TEX_COLOR_NORMAL` | 2 | **8** | **fancy cloud（`RenderGlobal:1676`）**・XP orb（`RenderXPOrb:63`） |
| 7 QUADS | `POSITION` | 2 | **7** | 影・帯 |
| 7 QUADS | `ITEM` / `BLOCK` | 各2 | **4 / 3** | item・block |
| 1 LINES | `POSITION_COLOR` | 2 | **1** | 線 |
| 7 QUADS | **`POSITION_NORMAL`** | 1 | **未対応（id 0）** | **`Render.renderOffsetAABB`（`Render.java:297`）** |
| 6 FAN | `POSITION_COLOR` | 1 | **1** | **天球 gradient（`RenderGlobal:1383`）** |
| 5 STRIP | `POSITION_TEX_COLOR` | 1 | **2** | **font glyph（`FontRenderer:238`）** |

**確定した事実**: 天球の gradient は **mode 6 = TRIANGLE_FAN・POSITION_COLOR（id 1）**、太陽/月は **mode 7 QUADS・POSITION_TEX（id 6）**、
fancy cloud は **mode 7 QUADS・POSITION_TEX_COLOR_NORMAL（id 8）**。したがって pitch=-14（見上げた画）で上端に出る帯・三角の候補は
**天球 gradient（fan）・太陽/月（quads+tex）・雲（quads+tex+color+normal）**の3つに絞られる。format-8 帰属が撤回された理由づけもこれで明確になった
（雲は format 8 だが、同じ画には fan と tex のパスも写っている）。

### 2. **未対応のバニラ描画パスを1つ発見**: `POSITION_NORMAL`

`DefaultVertexFormats.POSITION_NORMAL` は uploader mixin の対応表（`celtium$formatId`）に**どの分岐も無く id 0 ＝拒否**になる。
唯一の利用元は `Render.renderOffsetAABB`（`Render.java:290-…`、`begin(7, POSITION_NORMAL)`）で、その呼び出し元は
`RenderEntity.doRender`（`RenderEntity.java:24`）——**専用レンダラを持たない entity の白い箱**である。
GL-less ではこの箱は描かれない（bridge では GL が描く）。id を1つ足す（`POSITION_NORMAL = 10`）＋対応表＋パイプラインで閉じる作業として記録する。

### 3. font glyph の意味的契約（原典）

`FontRenderer.renderDefaultChar`（230-248）: `bindTexture(locationFontTexture)` → `glBegin(5)` →
各頂点の直前に `glTexCoord2f`、頂点は `k = italic ? 1 : 0` と `f = charWidth[ch] - 0.01` を使い、**戻り値は `charWidth[ch]`**。
`renderUnicodeChar`（272-303）も同じ形。`drawChar`（223）が `unicodeFlag` で振り分ける。
→ capture が UV とテクスチャ identity を落としていれば「受理されるが絵が出ない」になる（担当の観測と一致）。

## 0.128 **開発環境の活用**: デコンパイル済みソースを手元に置き、`runClient` の位置づけを確定（2026-10-11）

CleanroomMC の ForgeDevEnv テンプレート（RFG 2.0.3・Java 25・mixin/coremod 対応の 1.12.2 mod 開発テンプレート）を確認した。
**ビルド構成は私たちの `build-1122` が既に同系統か上位**（RFG 2.0.4・mixinbooter 10.5・RetroFuturaBootstrap・access transformer・`runClient`/`runServer`）で、
テンプレートから取り込む価値があるのは次の2点だった。

**1. デコンパイル済み Minecraft ソース（活用開始）**

RFG は既に `forge122/build/rfg/mcp_patched_minecraft-sources.jar`（7561 ファイル）を生成していた。展開済み:

```
.celtium-work/mc-sources/net/minecraft/...        （Minecraft 2185 ファイル）
```

`client/gui/FontRenderer.java`・`client/shader/ShaderGroup.java`・`client/shader/Framebuffer.java`・
`client/renderer/RenderGlobal.java` を含む。**担当は署名と挙動を推測や bytecode 読みではなく原典で確認できる**——
監査が繰り返し指摘している「実物に当たっていない作業」を減らすための計器である。
すぐ効いた例: `FontRenderer.renderDefaultChar`（230-248行）は `glBegin(5)` の **4頂点ストリップ**で、
UV は各頂点前の `glTexCoord2f`、テクスチャは直前の `bindTexture(locationFontTexture)`、頂点は
`k = italic ? 1 : 0`・`f = charWidth[ch] - 0.01` を使い、**戻り値は `charWidth[ch]`**。
capture が UV とテクスチャ identity を取っていなければ、1 texel を引く「受理されるが絵が出ない」形になる——担当の観測と一致する。

**2. `runClient` は stock Forge の開発クライアントであって Cleanroom ではない（受入に使わない）**

`forge122:runClient`/`runServer` は RFG 既定（私たちの build に追加設定なし）で、動かすのは
**stock Forge 14.23.5.2847 の開発環境**であり、目標ランタイムの **Cleanroom 0.6.13-alpha ではない**。
したがって受入の証拠にはならない（受入は Prism 上の Cleanroom 実インスタンスで取る）。
探索・ログ確認・mod 読み込みの素早い反復には使えるが、その結果を Cleanroom の受入として引用しない。
assets の取得（`downloadAssets`）が必要で、初回はネットワークと時間を使う。

## 0.127 **統合**: 失われた担当4体の作業を保全し、監査バッチの現状を固定（2026-10-11）

4体の担当が同時に落ちた（最終報告の直後）。作業は**未commitのまま作業ツリーに残っていた**ため、コンパイルが通ることを確認したうえで単位ごとに保全した。

| commit | 内容 | 受入の状態 |
|---|---|---|
| `aa9a02801` | N02 の寿命契約（`VulkanDynamicMesh` 389行・`VulkanModelQuadCapture` 171行・GlStateManagerMixin の injector 2本）＋ `ModelListLifecycleProbe` | 担当の10ステップ probe 通過（baseline live=2/buffers=4 → add 3 → recompile 4・retired 1・live 不変 → delete 2 → id 再利用 3）。world-switch/reload と、私の retire 順序変更後の再測定は未 |
| `32528cccf` | N04 gate（`unsuppliedInput`）と N05 token（`commitPasses(int)`）＋ `ShaderGroupMixin` の guard | 担当の実測あり（bridge: `postChains=43 postAdopted=0 postRunsOnStandIn=43` → `postChains=0 postRunsOnStandIn=0`／fail-once: `postRepeatInits=1 postRecordingCommits=59 postStaleCommits=0`・frames 236 vs control 205・VUID 0）。**GL-less glow の end-to-end は未** |
| `6a52c4002` | texture batch（`VulkanGlTexture` 1042行・registry・CPU contract check・`ForeignLayoutLedgerTest`） | **担当の報告が届いていない**。diff を読み、N07/N08/N17 の受入を取るのが次の作業 |
| `9fea3dcc5` | font の glyph capture（mixin 2本＋`impl/font/`）＋監査文書 | 担当の実測: glyph 34000 提出/34000 受理/拒否0。**実画像受入は未**（live GUI frame で panel は出るが文字が見えない、が担当の最終観測） |

**glow の設計結論（3つの一行変更が全て実測で失敗した末の確定）**

| 試行 | commit | 結果 |
|---|---|---|
| identity 0（既定FB）の描画を拒否 | `b7b634179` | 絵が凍結（世界が届かない） |
| identity 0 を main 扱い | `25e617340` → `4642c145e` で撤回 | `presentedDistinctColours=1`（自身へのコピー） |
| composite が frame 自身の colour を sample する時 skip | 撤回済み | `presentedDistinctColours=1` |

**結論**: adoption 構成では identity 0 の composite は**フレームを届ける pass そのもの**であり、off-target pair は**ゲームが adopt した colour image**。world・composite・silhouette が同じ image を通るのは正当で、欠陥は**outline chain がその共有 image を自分の入力にしている**側にある。修正は chain 専用の image を与えること（`GllessFrameTarget` に第2の pair を追加し、identity 0 と outline の identity で振り分ける）。述語 `celtium$isScreenSelfComposite` は**使用禁止**として測定記録つきで残した。

**ハーネスの証拠整合（`51e75a14b`）**: `pictureFile_*` の stage 選択（`pictureStage` を報告）・presented-dump の PNG 実在検査（`pictureEvidenceProblem`）・`UNCALIBRATED_PRESENTED_IMAGE` を失敗経路にも合格経路にも畳まない（`fixtureUncalibrated` として報告）。

**現在稼働中の担当（4体）**: texture batch の受入（N07/N08/N17/N10）／font の実画像受入（N03）／glow の設計修正（chain 専用 image）／wedge の残存原因の診断。

## 0.125 **訂正**: bridge の「model draw 回帰」は証拠不足（カウンタが bridge レポートに存在しない）

- ベンチ担当の観測「最新ビルドの bridge が 60Hz フロアに落ち、`CELTIUM_MODEL_MATRIX` 行が一切出ない」を受け、
  私が「bridge で native model 描画が発火していない回帰を実測で確認」と報告したが、**これは証拠を超えていた**。
- 実測: bridge 構成のレポートには model 系カウンタが**元から含まれていない**。
  `p530-tp`・`ref-gl`・`glow-bridge3`・`bridge-model` のいずれも `modelDraws=` は absent、`modelMeshLists=` も absent。
  GL-less のレポートには含まれる（例 `modelMeshLists=2 modelQuads=12`）。
- したがって「行が出ない」は **「描いていない」と「記録していない」を区別できない**。回帰の有無は未確定であり、
  次に必要なのは bridge でも同じ計器をレポートへ載せること（フィールドの有無自体が構成差で説明できてしまう現状を直す）。
- これは「counter の不在を描画の不在と読まない」という §0.118 の規則と同じ種類の誤りである。記録として残す。

## 0.124 **受入**: Intel 4-leg（Nether 含む）／format 5 の拒否を修正・検証／wedge の根因は format 8（fancy cloud）

- **Intel 4-leg 受入（実測）**: `.celtium-work/intel-layout-evidence/leg{1,2,3,4}`、jar sha256
  `b63a8a85...`（renderer）・`74985a85...`（fixture）。
  - leg1 bridge terrain: PASSED・clean・0 VUID・band 平均 (102.561, 59.786, 54.992)
  - leg2 GL-less terrain: PASSED・clean・0 VUID・band 平均 (102.561, 59.785, 54.992) ＝ leg1 と ±0.001（present 経路の証拠）
  - leg3 **GL-less Nether（tick 80 で dimension -1）**: PASSED・clean・0 VUID・
    **09600 も 01198 も出ない**（= present layout 修正が Intel の再構築経路でも効いている）・client/world/server すべて -1・
    `dimensionFramesInTarget=114`・present 継続 failures=0
  - leg4 bridge glow: FAILED_PRESENTED_IMAGE だが**画像を開くとローディング画面**（"is under development" 表示・78.7% black）。
    AMD の同時 run `glow-bridge3` は同じ loading 画面でさらに暗い（90.4% black）＝ **Intel 固有ではなく scene のタイミング問題**。
    旧 jar の PASSED は gate が絵を判定していなかっただけで、旧 PNG の rows 300-720 は純黒。
  - **拡張/format/memory type/feature を名指すメッセージは 4 leg とも 0**。Intel の capability は externalMemoryFd/
    externalSemaphoreFd true・問い合わせた6 format すべて対応。depth format の要求が2つの診断で食い違う
    （capabilities `D24_UNORM_S8_UINT` vs `VulkanDeviceReport` の hardcode `D32_SFLOAT`）のは既存の未整理点。
- **format 5（粒子）修正・検証（commit `1e08cfd60`）**: 根因は `canBuildDescriptor` の無名 `return false`
  （`pipelineFor(lastUiPass)==null`、最初の terrain pass 前）。probe を描画経路と同じ規則
  （terrain pass があればそれ、無ければ `uiRenderPass()`）に揃え、throw 経路も理由を記録。
  検証: format 5 **73/73 受理**（従来 96/131）・probe 拒否 0（従来35）・**拒否 census が空**・fixture PASSED・clean・
  画像 verdict PASSED（distinct 28170・black 0.0029）。
- **wedge の根因（確定・修正済み `454fb3600`）**: 監査が**パッケージ済み jar のバイトコード**から特定した——
  `celtium$buildModelMeshes` が `mesh.nextVertex()` を **quad ごとに1回、4頂点ループの外**で呼び、
  その後に `putPosition`/`putUv` を4回呼んでいた。`nextVertex()` は頂点を開始してその index を返し、`put*` は
  開始済みの頂点に書く契約なので、**quad の4隅が1頂点を上書き**し、mesh は 4Q ではなく **Q 個の頂点**しか持たず、
  index は `base..base+3` を要求して**範囲外**を指していた。巨大な半透明 wedge・空の黒三角・筋は、
  **書かれていない頂点を rasterizer が結んでいた**もの。行列・topology・depth の議論はすべてこの下流だった。
  修正: `nextVertex()` を頂点ループ内へ移し（0,1,2 / 0,2,3 の三角分割は維持）、契約を**検査**として追加
  （各 quad の頂点が連続して開始されること、完成した mesh がちょうど 4Q 頂点・6Q index を持つこと。破れば描かずに拒否）。
  **検証（同一固定カメラ scene・GL-less allBlocks）**: fixture PASSED・clean・msgs 0・画像 verdict PASSED・
  `modelMeshLists=29 modelMeshQuads=160 modelMeshFailure=none`。修正前後で **36.9% の画素が変化**し、
  比較画像では wedge・空の黒三角・太い筋が消えてブロック壁と床が残る。細い斜めの筋は残っており、
  これは今回の修正とは別の、より小さい欠陥。
  なお担当が並行して見つけた「format 8（fancy cloud）が GL-less でのみ受理される」経路は、**別個の**
  GL-less 固有欠陥として残る（cloud shader を持たない native immediate pipeline が受理してしまう）。
- **注意（受入の前提）**: これらの run の後も `ValidationClient.java` と `scripts/prism_validation.py` が編集されている。
  次の受入は rebuild 後の jar で取ること。

## 0.123 **受入**: 条件 (c) 充足 —— 提示 frame が実際の window/compositor 出力に到達（marker 帰属 + 番号付き presented dump との画素完全一致）

- 文書: **`docs/compositor-output-capture-2026-10-11.md`**。これは `scripts/compositor_capture.py` の docstring が引用していた文書で、
  同スクリプトは本計測で**初めて実走**した（それまで capture 成果物も当該文書も存在しなかった）。
- **手法（renderer は無改変・すべて既存スイッチ）**: `-Dceltium.vulkan.presentMarker=true`（present を magenta (1,0,1,1) の全面 clear に置換、
  `VulkanSwapchain.java:85,704-720`）＋ `-Dceltium.vulkan.presentReadback=true` と
  `-Dceltium.vulkan.frameDumpDir/Every/Count`（**presented frame 番号を名前にした** `frame-<N>.png`）。
  compositor 側は `scripts/compositor_capture.py` の `grim -o HEADLESS-1`（連番 PNG ＋ シャッター時刻・window tree・output mode）。
  束縛の規則は「ある `frame-N.png` の書出し時刻を capture が跨ぐ」**かつ**「画素が完全一致」の両方。
- **marker run**（`.celtium-work/compositor-capture/marker2`、46 capture、画像 hash は黒と magenta の **2 種のみ**）:
  window 未 map の 32 capture は一様な黒 (0,0,0)・`windows=0`、window が map されている **14 capture（0031–0044）は 1280×720 全面が一様 (255,0,255)**
  （`firstMappedCaptureWall == firstMagentaCaptureWall == 08:58:21.866`）。この出力の唯一の client は
  `{"name":"Cleanroom","shell":"xwayland","pid":1716095,"visible":true,"rect":1280x720}`＝ゲームの X11 窓、output は HEADLESS-1 1280x720@60 scale 1。
  **capture 0035 は renderer の `frame-20.png` と 0/921600 画素差・最大チャネル差 0**（`frame-40` とも一致）。
  frame-20 の書出し 08:58:25.631 は capture 0034(08:58:24.867) と 0035(08:58:25.867) に挟まれる。
  renderer 側計器は `presentSucceeded=true presentAttempts=81 presentFailures=0 presentedFromVulkan=81 glSwapsSkipped=81`。
- **fixture の独立計測と一致**: marker run の fixture 自身が presented 画像を `presentedImageMeanAtCapture=170.0`・
  「921600 画素が単一色」と計測（`status=FAILED_PRESENTED_IMAGE` は marker を入れた**当然の結果**で欠陥ではない）。
  RGB 平均 170.0 = grim 側の一様 (255,0,255) と同値。**以前この行に書いた 191.25 は RGBA 4 チャネル平均**で、
  同じ magenta の別の平均の取り方（矛盾ではない）。
- **通常 run**（`.celtium-work/compositor-capture/scene`、69 capture、fixture **PASSED**・clean・msgs 0・`presentedImageVerdict=PASSED`）:
  **capture 0066 は `frame-100.png` と 0/921600 画素差で完全一致**（実 scene・10104 色。frame-100 の書出し 09:00:45.923 は capture 0065/0066 に挟まれる）。
  中間 frame（frame-20/40/60/80）との比較では 0.059–2.31 % の差が出る＝「どれも同じ絵」ではない。
  fixture の presented 画像（presentedFrame=102、`frameBinding="observed present: ...counter advanced by exactly one"`）とも
  957/921600 = **0.104 %** 差（frame 100→102 の粒子・アニメ分）。
- **対照**: GL screenshot は黒（presented と 99.925 % 差）＝ **GL framebuffer は証拠経路ではない**。
  window が一度も map しなかった run（検証 layer を `VK_LAYER_PATH` で渡さず renderer が
  `... layer VK_LAYER_KHRONOS_validation is not installed` でデバイス生成を拒否し world 読み込み中に crash）は
  91 capture すべて黒・`windows=0`。`grim -o HDMI-A-4` は**実 KMS 出力**（実セッション 1920×1080・9675 色）も読める
  ＝ headless backend 固有の成功ではない。
- **残る未測定は scanout そのものだけ**: `ffmpeg -f kmsgrab` は `Failed to open DRM device`（DRM master 無し・非 root）。
  grim は headless でも実 KMS でも「compositor が合成した出力バッファ」を読むので、実出力でゲームを走らせても scanout の gap は閉じない
  （実セッションを止めて `WLR_BACKENDS=drm` にするか、root/CAP_SYS_ADMIN が要る）。よって条件 (c) は**充足**、
  未測定は「その先の走査出力」のみで、これは方法の選択ではなく環境の権限・構成の限界。

## 0.122 **受入済み**: layout/publish バッチは Intel の両 leg でも成立

- `.celtium-work/runtime/intel-layout`（Intel UHD 630・`VK_ICD_FILENAMES` で pin・bridge 構成）:
  fixture **PASSED**・`validationClean=true`・**msgs 0**・`nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"`・
  `gpuRequirementSource=nativeDeviceName`・`gpuRequirementMet=true`・`nativeDeviceMatchedOpenGl=true`。
  これは GL-less present の layout 宣言修正・chain の submitted 契約・outline の publish 配線を含む現 HEAD の build で取ったもので、
  **AMD だけで成立していた batch が Intel でも退行しない**ことの実測。
- **GL-less leg も受入（実測）**: `.celtium-work/runtime/intel-layout-glless`（`VK_ICD_FILENAMES` で Intel に pin）が
  fixture **PASSED**・`validationClean=true`・**msgs 0**・`nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"`・
  `gpuRequirementSource=nativeDeviceName`・`gpuRequirementMet=true`・`presentedImageVerdict=**PASSED**`（distinct 1473・bands 496）。
  `nativeDeviceMatchedOpenGl=false` は stub 構成では正しい（GL identity が無いため UUID 照合はできない）。
  これで **layout/publish バッチは AMD と Intel の両方・両 leg で成立**した。
- 注意: この run の jar は他エージェントが同時に rebuild している期間のものなので、担当の報告で sha256 を確認してから
  最終受入として扱う（build が動いている間の測定は、どの build を測ったかを必ず添える）。

## 0.121 **実測・進行中**: 条件 (c) の捕獲手段は実在する（旧前提を訂正）／glow は検証 clean・実体ありの frame で白アウトラインを確認

- **条件 (c) の前提訂正（担当の実測）**: plan が長く持っていた「`DISPLAY=:0` の root が 0×0 で合成器なし」は**誤り**だった。
  実測では sway(pid 935) が `wayland-1`（`XDG_RUNTIME_DIR=/run/user/1000`）で稼働し、`HDMI-A-4 1920×1080@60` の実モニタ、
  Xwayland `:0 -rootless` があり、`ffmpeg -f x11grab -i :0.0` は 1920x1080 を読める。加えて harness の `--headless` は
  実 GPU の wlroots/Sway を所有しており（`WLR_BACKENDS=headless`・`WLR_HEADLESS_OUTPUTS=1`・output HEADLESS-1 1280x720）、
  **`grim -o HEADLESS-1` が別 run の実ゲーム窓を 1280x720 PNG として捕獲できた**（exit 0・642kB・6781色＝地形フレーム）。
  これは「環境制約で条件 (c) は測れない」という以前の判断が誤りだったことを意味する。
- **束縛手段**: `-Dceltium.vulkan.presentMarker=true`（present を magenta 全面 clear に置き換える既存スイッチ）と
  番号付き presented dump（`-Dceltium.vulkan.frameDumpDir/Every`）を併用すれば、compositor に出た frame を
  renderer 側の frame 番号に束縛できる。見つからないツール（Xvfb/weston/xwd/ImageMagick/kmsgrab 等）と、
  kmsgrab が DRM master 権限不足で不可であることも記録した。担当が marker run と通常 run を実行中。
- **glow（計器は揃ったが、絵はまだ未確認）**: `.celtium-work/runtime/glow2` は **validationClean=true・msgs 0**・
  `nonPlayerEntitiesAtCapture=9`（capture 時に実体が生きている）・
  `CELTIUM_EXTERNAL_IMAGE published glTexture=42 image=0x12b... owner=renderer`。presented frame は distinct 51388・
  nonBackgroundFraction 0.813。
  **撤回**: この frame を「実体に白いアウトラインが付いている」と記述したが、**誤読だった**。拡大（4倍・NEAREST）すると
  そこに写っているのは mob ではなく **wedge 欠陥の暗い三角シャードと青い半透明クワッド**で、白い輪郭は存在しない
  （「白い線」に見えたものは地形テクスチャのグリッド線と wedge の辺）。2279 個の near-white 画素のうち暗いシルエットに
  隣接するものは **1個**という数値もこの誤読を裏づける。**glow の可視は依然として未確認**で、判定は担当が取得中の
  bridge 参照との画素比較のみが根拠になる。
- **wedge の新しい観測**: 上記の拡大で、wedge は「青い半透明の大きなクワッド」だけでなく
  **黒い三角シャード**としても現れる（暗い三角形が地形・他の wedge を覆う）。担当の調査に渡す。

## 0.120 **実測**: 条件 (b) の現状プロファイル（標準 GL-less allBlocks）／次に埋める native 面は format 5

- run `.celtium-work/runtime/regr4-glless`（GL-less・allBlocks・fixture PASSED・validationClean・msgs 0・画像 verdict PASSED・
  distinct 10142）:
  - `celtium offer decisions: offers=5000 accepted=4705 glBodyCompleted=341` ＝ **94.1% が native で受理**。
  - per-format arrivals `[1:2254 2:634 5:126 6:946 7:860 9:180]` / accepted `[1:2280 2:640 5:92 6:948 7:860 9:180]`。
- **読み方（§0.118 の規則）**: GL-less では `glBodyCompleted=341` は「GL が描いた」ではなく stub の no-op 実行。
  条件 (b) の GL 残量は bridge 構成の拒否から読む。ここで意味を持つのは「native で描かれなかった量」。
- **次の native 面**: format 5（粒子）が **126 中 92 のみ受理**。拒否は stage 10 の descriptor gate
  （`canBuildDescriptor`）で、理由は 3条件（registry に texture が無い／lightmap が無い／mirror が full content でない）の
  いずれか。実測は `5 unrecorded (format=5 stride=28 mode=7 count=3932) stage=10` のような大きな粒子 batch。
  専用担当が原因特定と patch を作成中。
- 併せて確認: publish 配線と layout 修正は**標準場面で退行なし**（この run が clean・画像 verdict PASSED）。

## 0.119 **修正・受入済み**: GL-less の layout 宣言を2箇所で正した（Nether の VUID 解消）／画像ゲートが scene 非依存だった

- **VUID の正体（実測・エージェント）**: 「post chain が GENERAL 記述子で UNDEFINED を指す」という仮説は否定された。
  該当 run で chain は一度も走っておらず（専用計装が0行・report も `postChains=0`）、`VUID-vkCmdDraw-None-09600` は
  layer の **submit 時 first-layout 検査**（`vkCmdDraw` はハードコードされたラベル）。真因は GL-less の present で、
  `ensureFrameTarget()` が直前に target を再構築しうる（dimension/world 変更）ため **新しい colour image が UNDEFINED**
  なのに GENERAL を宣言していたこと。
- **修正1**: GL-less では target 自身の記録（fresh は UNDEFINED、提出済み frame が遷移済みなら GENERAL）を答える。
  bridge は従来どおり（GL 側が残す layout は別の問い）。
- **修正2（修正1が生んだ新しい VUID）**: 4引数 `presentImage` は「見つけた layout に戻す」ため、UNDEFINED を渡すと
  復元 barrier が `newLayout=UNDEFINED` になり `VUID-VkImageMemoryBarrier-newLayout-01198`。復元先を明示し
  （source 遷移は UNDEFINED から開始可・返却は GENERAL）、owner の記録にも反映する。GENERAL と言っても内容を主張しない
  （fresh image に内容は無く、次の frame の acquire barrier が描画可能な layout を与える）。
- **受入（実測）**: `.celtium-work/runtime/vuid-after2`（GL-less・`dimensionAtTick=80`・`dimension=-1`）が
  `validationClean=true`・**msgs 0**・capture 時 client/world/server すべて -1・`dimensionFramesInTarget=129`。
- **画像ゲートの欠陥（担当へ送付済み・未修正）**: 同じ run が `FAILED_PRESENTED_IMAGE` になったが、内容は
  **闇の Nether 洞窟**（distinct 605・blackFraction 0.037・nonBackgroundFraction 0.326・bands 720）を
  overworld 用の床で判定していた。frame identity・per-stage dump の配管は正常（`pictureEvidenceSource=presented-dump`）。
  指示: 床は scene ごとにし、参照 run と数値を明記する。参照未取得の scene は FAILED ではなく
  `floorsNotCalibrated` を報告する（「参照が無い」を FAILED と呼ぶと verdict が無視される習慣になる）。
  床を上げて通すことはしない。
- **chain の不変条件**: target を「初期化済み」とするのは barrier を載せた buffer が実際に submit された後
  （`commitPasses()`）。record 時点でフラグを立てるのは layout の嘘と同じ種類の誤り。

## 0.118 **修正・受入済み**: 監査バッチ（cancellation 契約・adoption 絞り込み・overlay 経路・GPU 報告）／TESR の本は描画される

- **cancellation 契約（監査 優先度1）**: `callList` の `@Inject` に `cancellable = true` が無く、`info.cancel()` が
  `CancellationException` を投げ、外側の catch が「計測失敗」として飲み、census は取消を数え、GL 本体は走っていた。
  修正後は cancellable を明示し、census は**取消が実際に効いた後**に書き、CancellationException は
  `CELTIUM_CANCEL_NOT_CANCELLABLE` として有界に報告し、`@At("RETURN")` の観測で「本当に走った GL 本体」を数える
  （HEAD 取消は RETURN の前に return するので、これが判定になる）。実測（bridge・パッケージ済み jar）:
  `CELTIUM_CANCEL_STATE cancelled=1 executed=8 failures=0`・not-cancellable 0 行・fixture PASSED・clean。
- **adoption の絞り込み（監査 優先度2 相当の欠陥）**: 「game が bind した任意の identity」を adopt していたため、
  spectator group が自分の4 target を adopt（`postTargets=0 postAdopted=4`）し、全 blur pass が1枚の image を
  読み書きしていた。mixin が報告する **chain の最初の入力**（`passes.get(0).groupMainFbId`）だけを adopt する。
  受入（`.celtium-work/runtime/outline5`・fixture PASSED・VUID 0）: `postChains=45 postPasses=180 postTargets=1
  postAdopted=1 postAdoptions=1 postRunsOnStandIn=0 postAdoptedIds=[64c7afc6]`、off-target で silhouette を
  受けた identity と一致。
- **overlay の経路**: GL-less では stage-3 の skip が「frame 自身の blit」と「別 texture を sample する overlay」を
  区別せず、outline composite が texture gate に到達する前に拒否されていた
  （`CELTIUM_COMPOSITE_REFUSED reason=unrecorded format=2 count=4`、`textureRefusalsBeforeAccept=0`）。
  現在の target 自身の colour texture を sample する場合だけ拒否し、それ以外は即時パスへ落とす
  （判定は game の bind が埋める identity map。stub は attachment query に答えられない）。
- **GPU 報告（監査 優先度5）**: 描画側が選択確定時に `CELTIUM_NATIVE_DEVICE name="..." uuid=... matchedOpenGl=...`
  を出し、harness はこれで `--require-gpu` を判定する（device 未選択時のみ GL 文字列へ fallback し、どちらで
  判定したかを記録）。`process.driver` は選択された device を UUID→name で照合して報告する。実測: GL-less で
  AMD が選ばれた run に `--require-gpu UHD` を掛けると `gpuRequirementMet=false
  gpuRequirementSource=nativeDeviceName nativeDeviceName="AMD Radeon RX 6400 (RADV NAVI24)"`。
- **TESR / エンチャント台の本（受入）**: `tesr7`（GL-less・`-Dceltium.validation.allBlocks=true`）で
  `modelDraws=3883 displayListCancelled=3883 displayListDistinct=29 modelMeshQuads=160`、本の list が
  `liveMVt=(6.012,-5.843,-29.475)`（カメラ原点ではなく実ブロック位置）で描画。GL 参照
  （`-Dceltium.vulkan.modelDraw=false` の bridge）と同一画素位置で一致（`.celtium-work/tesr-evidence/`）。
  **fixture の superflat world は生成ブロックに TileEntity を作らない**（`Chunk(World,ChunkPrimer,int,int)` は
  storageArrays のみ）ため、生成されたエンチャント台は GL でも本を出さない——これは vanilla の挙動。
- **未達として残るもの**: 可視 glow（registry への image 公開＋layout dance）、post chain 自身の target の
  `UNDEFINED` 参照 VUID（専用エージェントが調査中）、GL-less の一部 frame に出る半透明の大きな wedge
  （`modelDraws=0` の run でも出るため model 経路ではない）、画像受入の frame 束縛（監査 優先度4）、
  新 jar での Intel 再受入。

## 0.117 **受入済み**: Nether が capture 時に dimension -1 で native scene を描く（goal 項目4・条件 (a) の一部）

- **受入（実測）**: `.celtium-work/nether-fix4.json`（AMD RX 6400・GL-less・`--jvm-arg=-Dceltium.validation.dimensionAtTick=80
  --jvm-arg=-Dceltium.validation.dimension=-1`）が fixture **PASSED**。capture tick 200 で
  **client/world/server すべて -1**、`dimensionGate=none`、`dimensionFramesInTarget=122`（-1 で画面なしのまま描いた
  frame 数）、player はポータル外、`dimensionLeftTargetAtTick=-1`（0 に戻っていない）。
  presented PNG は distinct **16543**・non-background 0.594 で、netherrack・fire・obsidian のポータル枠という
  実 Nether scene（`.celtium-work/evidence-nether/nether-dimension-1-presented.png`）。
- **根因（実測・推論ではない）**: fixture 側。vanilla `Teleporter` の到着位置が**ポータルの中**で、その直後に
  fixture が `player.timeUntilPortal = 0` を設定していた——vanilla は `changeDimension` 直前に
  `getPortalCooldown()` を入れる。`Entity.onEntityUpdate` は portal block と重なる entity を
  `getMaxInPortalTime()` tick 後に -1 から 0 へ送る（creative では 1 tick、＝2 tick 接触）。
  これが「到着後に 0 へ戻る」の正体。約60 tick の遅れは server が
  `Teleporter.placeInExistingPortal/makePortal` で詰まっていた時間（trace に client が -1、server がまだ 63,10,0 と出る）。
- **fixture の修正**: vanilla 準拠の cooldown、到着位置の探索（13候補・ポータル外/通行可/ground 優先、全数報告）、
  140行の dimension trace（stdout・report・exit hook へ）、fail-closed な capture gate（到達＋client/world/server が
  目標次元＋画面なし＋ポータル外＋その次元で N frame 描画）、presented image の判定を capture の1 frame 後に
  行う修正（従来は 9.5 秒前の loading frame を判定していた）。
- **残る未達（fixture ではない）**: `validationClean=false` の原因は 1件の
  `VUID-vkCmdDraw-None-09600`（`VulkanPostChain` の記述子が `GENERAL` を宣言しているのに image が `UNDEFINED`）。
  GL-less かつ dimension 変更の run にのみ出て、GL-less で dimension 変更なしの run、GL-bridge の dimension run は
  いずれも 0件。chain 自身が確保した target の初期化 barrier の欠落/無効が疑わしく、専用エージェントで修正中。
  この1件が解消するまで Nether は「絵は受入・validation は未受入」として扱う。
- **注意（他 run への影響）**: GL-less の presented-image 判定が capture の1 frame 後になったため、
  dimension 変更のない GL-less run（p559/p561 相当）も判定対象 frame が変わる。
  `-Dceltium.validation.presentedImageAtCapture=true` で旧挙動に戻せる（A/B 用）。
  また renderer の presented-frame counter は dimension 変更で frame target と共にリセットされる（実測 high-water 37 → 0）。
  dimension run の frame 数主張はその窓に限定し、全体の会計（37+323=360 / worldFrames 362）を記録する。

## 0.116 **修正・受入済み**: lightmap の黒は announcement の配列形状だった（producer/consumer 契約）／model の平行移動がミラーに届いていなかった

- **根因（実測）**: `VulkanTextureRegistry.announceFullPixels`/`toRgba` の契約は `int[mipLevel][flat texel]` で
  `data[0]` が level 0 全体。producer の `VulkanTerrainGpuBackend.announceDynamicTexture` が flat な `int[]` を
  **1行1配列**に分割していたため、`data[0]` は1行だけになり、それ以降の texel は範囲外として透明で書かれていた。
  実測（16x16・5 level・announcement のみを供給源）: 修正前 level 0 は 256 texel 中 16 のみ
  （row 0 = (14,14,14,255)、rows 1..15 = 黒）、各 level は前段の1/4。地形は vertex の sky 行をサンプルするので
  row 15 が黒いと light 項が 0 になる。修正後 level 0 は 1024/1024 バイト・min 14。
- **受入（実測）**: 「readback 経由」と呼ばれていた回避策（GL-less では実際には stub が捕捉した CPU 側ピクセルを読む経路で、GL readback ではない）を撤去し、lightmap を announcement 経路のみで供給した bridge run
  `.celtium-work/runtime/ann-acc3` が fixture PASSED・validationClean・msgs 0・**VUID 0**・
  band 平均 (102.4, 63.9, 58.5)・1385 色。回避策なしで（GL-less では CPU 捕捉ピクセル経由の）経路と同一の絵になることを確認した
  （画像の所有者は1つに戻した）。
- **model の平行移動**: `GlStateManagerMixin` は double 版 translate（`func_179137_b` → `glTranslated`）しか
  ミラーしておらず、ゲームが使う float 版（`func_179109_b` → `glTranslatef`）は GL-less stub が no-op として
  捨てていた。実測: 59 frame の run で `translateCalls=59`（＝カメラ1回のみ）、全 model list が
  `compileT=(0,0,0)`。フック追加後は tile entity の list が実ブロック位置
  `compileT=(-14.0,0.614,-31.022)` / `liveT=(-14.0,-0.84,-30.659)` を持つ。**絵の受入は発見エージェントが実行中**で、
  この section では主張しない。double 版 scale も同理由でフックした。
- **Intel 受入（実測）**: UHD 630 で両 leg PASSED・clean・VUID 0。GL-bridge band (119.4, 86.1, 80.5)（AMD は
  (102.4, 63.9, 58.5)、差は HUD 被覆で terrain の色集合は同一）、GL-less は presented assertions PASSED・
  bands 411（AMD と同一）・AMD との行輝度相関 0.9995。**注意**: GL-less leg は `--render-node` では GPU を
  pin できない（GL identity が無いため最初の非CPU device=AMD が選ばれる）。実測は
  `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json` で pin した。harness の `--require-gpu` は
  stub の `glRenderer` 文字列を見るため GL-less では GPU を検証できない（既知の harness 欠陥）。
- **Nether（進展・未受入）**: 根因は fixture 側で、`Entity.onEntityUpdate` のポータル再突入だった
  （vanilla は `changeDimension` 直前に `timeUntilPortal` を設定するが fixture は 0 にしていた）。fixture は
  vanilla 準拠の cooldown と到着位置探索に直し、capture 時に client/world/provider すべて -1・
  `dimensionFramesInTarget=49`・画面なし・ポータル外、presented PNG は distinct 15428 の実 Nether scene。
  残る未達は presented-frame counter が dimension 変更で再作成される frame target と共にリセットされる件の
  会計1件で、エージェントが修正中。**旧 Nether PASSED は引き続き無効**。
- **未解決（新規・renderer 側）**: GL-less かつ dimension 変更の run に限り
  `vkQueueSubmit(): ... expects VK_IMAGE_LAYOUT_GENERAL -- instead, current layout is VK_IMAGE_LAYOUT_UNDEFINED`
  が 1件/run 出る（GL-less + dimension 変更なしの run、GL-bridge + dimension 変更の run はいずれも 0件）。
  frame target 再作成時に古い image 用の descriptor が新しい UNDEFINED image を指す経路が疑わしく、未修正。
- **アウトライン**: chain が空の stand-in target を確保していた（`postChains=34, postPasses=136, postTargets=2,
  postAdopted=0`、`CELTIUM_POST_ADOPT_REPORTED ... adopted=false`）。silhouette 自体は off-target image に
  描かれている（identity 0x79D2D96A に 35 提出・34 pass 描画）。本ラウンドで chain 側の adoption を配線した
  （`adoptChainFramebuffers`、識別子はゲームが実際に bind したものだけ）。**after 実測**
  （`.celtium-work/runtime/outline4`、fixture PASSED・clean・msgs 0）: `postChains=31 postPasses=124 postTargets=1
  postAdopted=1 postAdoptions=1 postRunsOnStandIn=0`（before は `postTargets=2 postAdopted=0 postAdoptions=0`）——
  chain は空の stand-in ではなく実シルエットを読む。registry への image 公開（composite が texture 42 を解決できる
  ようにする＝画面上の glow）は `VulkanGlTexture` の所有モデルを変えるため次段に分離。**glow の可視化は未受入**。

## 0.115 **達成**: GL-less window が実 scene を描画 —— 地形の黒は lightmap の供給経路だった（goal 項目6 / 条件 (b)）

- 対象は commit `ccae86911`。run は `.celtium-work/runtime/ver2-glless`（AMD RX 6400・`-Dceltium.vulkan.glless=true`・
  `-Dceltium.vulkan.presentReadback=true -Dceltium.validation.dumpPresentedImage=true`・headless・
  `-Dceltium.validation.minWorldFrames=40`）。
- **受入（実測）**: fixture `PASSED`・`validationClean=true`・VUID 0。提示画像の主張は
  `presentedDistinctColours=867`・`presentedVerticalBands=411`・`presentedImageAssertions=PASSED`。
  直前の run（`p559-glless`）は同主張が `presentedDistinctColours=2` で `FAILED_PRESENTED_IMAGE` だったので、
  これは「平坦な色ではない frame が窓に出ている」ことの実測であって、主張を緩めたのではない。
- **根因**: terrain の黒は lightmap の **供給経路**だった。描画側の各要素は単体では正しく見えた——
  アトラス標本だけ出力すると地形が出て（band 平均 120,87,81、参照 `p530-tp` は 112,79,74）、
  頂点色だけ出力すると白（`a_Color` は正常）、ライト項を白に固定すると絵が完全に戻る。
  ライトマップの画像自体は正しく（記述子が指す実オブジェクトの device 側 dump が noon グラデ・level 0 と mip 連鎖・
  `nonZero=1024/1024 min=14`）、それでも shader は黒を返していた。差は画像を埋めた経路で、ライトマップは
  registry の announcement 近道（`canSkipReadback`）を通り、正しく sample できていたアトラスは
  `uploadFromGlTexture` を通っていた。ライトマップをアトラスと同じ呼び出しに載せると絵が出る。
- **未解明として残す**: announcement 経路が「内容は正しいのに sample が黒い」image を残す理由は特定していない。
  回避ではなく経路の統一として `uploadFromGlTexture` に寄せたが、`canSkipReadback` を通る他の texture
  （animated sprite 等）に同じ状態が残っている可能性は未検証。次の計測は announcement 経路で level 0 だけを埋めた
  場合の mip 段（level 1..n）の内容と、その image を sample する descriptor の状態。
- **同時に直したもの**: 頂点 arena readback が slot ごとに region を覚えるようにした（単一 field を毎 draw が
  上書きしていたため復号した頂点は信用できず、発行していた copy が
  VUID-vkCmdCopyBuffer-srcOffset-00113 の 9 件/run そのものだった）。fixture は presented image を run 末尾でも
  測る（capture の dump は次の present が書くため、capture 直後に終了する run では書かれない）。backend は
  fixture の dump を 40 frame ごとに再要求し、ディスク上の画像が最近の frame であるようにした。
- **GL-bridge の退行なし**: `fix-bridge` は band 平均 (102.4,63.9,58.5)・1382 色・VUID 0・fixture PASSED。
- **未着手のまま残るもの（この section の scope 外）**: 表示リスト geometry は 2 list（腕・手持ち）しか
  取り込まれておらず、TESR の model（エンチャント台の本）は GL fallback に落ちて何も描かれない。
  entity/TESR/item/model の native 描画・照明・画質、FBO/postprocess、Nether、条件 (c) は未達のまま。

## 0.114 **部分達成**: GL-context-less window が起動・提示・terrain pass 開始まで到達（goal 項目6 / 条件 (b)）—— 窓の絵は未解決なので受入ではない

- 対象は commit `4bcff8de7`（owned frame target・float marshalling・texture capture・presented-image 主張）。
  引用する GL-less run はすべて AMD RX 6400（`logs/celtium-capabilities.json` の `vulkanDevices`）・flag 付き
  （`-Dceltium.vulkan.glless=true`）・headless。**既定は従来どおり GL-bridge**。
  詳細・再現コマンド・計測で見つけた5欠陥は `docs/history/2026-10-10-gl-less-bring-up.md`。
- **起動（実測）**: `logs/debug.log` に `CELTIUM_GLLESS stub GL provider installed`・`window hinted GLFW_NO_API`・
  `splash progress skipped (no shared GL context)` の3行（`p548-glless`〜`p559-glless` すべて）。fixture は loaded
  world まで到達し（`logs/celtium-validation.json` の `worldFrames`: `p549` 282・`p550` 191・`p559` 192）、
  `validationClean=true`・`validationMessageCount=0`（`validation-process.json`）。
- **GL 実体が無い（実測）**: `logs/celtium-capabilities.json` は `glRenderer="Celtium stub GL"`・`deviceUuids=[]`、
  `logs/celtium-vulkan-device.json` は `status=UNSUPPORTED_OR_UNAVAILABLE`・`logicalDeviceCreated=false`・
  `reasons=["The current OpenGL context does not expose device UUIDs ..."]`。bridge の前提（GL device UUID と
  external memory/semaphore FD）はこの経路には無い。`CELTIUM_GROUPS` も `gameFramebufferIsShared=false`・
  `gameFramebufferAdopted=0`・`glless target: no handoff` で、フレームに shared image が入っていない
  （GL-bridge の `reg-bridge3` は `gameFramebufferIsShared=true`・`adopted=1`）。
- **1フレーム1提示・GL swap は1対1で skip（実測）**: `p548-glless` の `CELTIUM_GROUPS` 行（21:51:13）で
  `presentedFromVulkan=287`・`glSwapsSkipped=287`・`presentFailures=0`（最終行は 288/288。commit message が引く 287 は
  その1つ前の行）。`p549` 281/281・`p550` 190/190・`p551` 275/275・`p558` 167/167・`p559` 191/191 も同値。
  提示先は `rendererDebug` の `swapchain: platform=x11 format=44 colourSpace=0 presentMode=1 images=4
  size=1280x720`、結果は `presentResult=[present image: blitted 1280x720 onto image N (0)]`。
- **clear が game の空の色として届く（実測）**: `p548-glless/screenshots/celtium-presented.png` は `#FFFF00`
  62.5 % + 黒 37.5 %（float の取り違えが残っていた run。その次の `p549-glless` で色が変わる）。`p549-glless`/`p550-glless`
  は `#B3CFFF` 62.5 % + 黒 37.5 %。`#B3CFFF` は game が `glClearColor` に渡す fog/sky 色（`EntityRenderer.updateFogColor`
  の最後の `GlStateManager.clearColor(fogColorRed, fogColorGreen, fogColorBlue, 0.0F)`）。修正は `CeltiumGlStub` の
  per-signature CIF（旧: 8 pointer の CIF が全関数を兼ね、float が SSE ではなく general-purpose から読まれていた）。
- **driver 無しの texture 供給（実測）**: `CELTIUM_GROUPS` の `lightmapSuppliedOnDemand=1`・
  `lightmapSupplyFailure=[none]`、`rendererDebug` の `block atlas: gl texture 12 512x512`・
  `lightmap: gl texture 15 16x16`・`pipeline for solid created [vertex input stride=32
  module=terrain_solid_vanilla.vert ...]`・`firstDraw region=8 pass=solid stride=32 ... elements=16896 ...`。
  読み戻しは無い（`readbackByOrigin=none`・`partialMirrorRefusals=0`）—— mirror は stub が保持した upload と
  game の announce から埋まる（`p549`: `imageFilledMirrors=1360`・`directMirrorFromAnnouncement=633`・
  `announcedUploads=8`・`dynamicTextureFills=886`）。geometry は record=submit=complete で閉じる
  （`p549`: `immediateRecordedBatches=7403`＝`immediateSubmittedBatches`・`immediateCompletedBatches=7402`・
  `invalidImmediateRecords=0`・`celtium/vulkan-check passes=1759 skipped=0 errors=0`）。
- **fixture が fail-closed（実測）**: GL-less のときだけ `celtium$checkPresentedImage` が提示画像
  （distinct colours・non-background fraction・vertical bands）と frame 数を検査し、絵でなければ PASSED にしない
  （`validation-process.json` の `fixtureStatus=FAILED_PRESENTED_IMAGE`）。
- **未解決（受入ではない）**: **窓の絵はまだ絵ではない**。`p559-glless` は `presentedDistinctColours=2`・
  `presentedNonBackgroundFraction=0.0`・`presentedVerticalBands=15`・`presentedBackgroundColour=0x9DB5E0`・
  `presentedImageAssertions=FAILED: ... the window is a clear colour plus at most a quad or two, not a frame`・
  `fixtureStatus=FAILED_PRESENTED_IMAGE`（`p558-glless` も FAILED、distinct=2・bands=17・bg `0x96ADD6`）。
  capture 時の `presentedImageMeanAtCapture=213.6666717529297` は sky 色 `#B3CFFF` の RGB 平均そのもので、
  一様な空色フレームと一致する。`p559-glless/screenshots/frame-120.png`（owned target の dump）は空色の上に
  大きな灰色 wedge と**全幅の黒帯（rows 366-635）**で、地形は連続した面ではない。同じ world の GL-bridge 参照
  `p530-tp/screenshots/celtium-validation.png` は連続した checkerboard の地形面。commit message は
  「terrain は fragments と wedges・void plane が全幅の帯として透ける」と記録する。
- **証拠の限界（隠さない）**: 提示 PNG は tick 20 で要求した one-shot の dump なので capture 時点のフレームではない
  （`p559` の `presentedImagePngAgeMs=8566`）。色統計はその早期フレームのもので、capture 時の状態は mean
  （一様 sky 色の平均と一致）と owned target の frame dump が示す。
- **harness verdict は PASSED ではない**: GL-less run は `exitCode=0` だが `validationExitCode=1`・
  `gpuRequirementMet=false`（run 時に `--require-gpu 'RX 6400'` を渡しており、GL renderer が stub なので harness は
  GPU を確認できない。`p557-glless` だけは timeout で `exitCode=143`・`validationExitCode=124`・`fixtureStatus=NO_CAPTURE`）。
  fixture が `PASSED`・clean の `p548`〜`p551` でも harness は 1 を返す。対照の `reg-bridge3` は
  fixture `PASSED`・`validationClean=true`・msgs 0・`worldFrames=142`・`validationExitCode=0`・
  `gpuRequirementMet=true` で、GL-bridge 既定経路の退行がないことを示す。
- **未検証のまま残るもの**: post chain はこの fixture で発火していない（`postChains=0`）ので seeded post chain の
  GL readback 経路は GL-less で未検証。Intel GPU での GL-less は未計測（引用 run はすべて AMD）。

## 0.111 **計測**: pinned Celeritas（989ebf2e）対 current Celtium の frame 時間（goal 項目8）

- 方法: 同一 harness・同一 fixture（標準 flat world）・同一 GPU（AMD RX 6400）で `frameIntervalP50Ms` を比較。
  **worldLayers は計測条件に明記必須** —— `worldLayers=3`（軽世界）と既定（重い・溶岩/火入り）で大きく違う。
- **既定世界**: baseline Celeritas **p50 = 16.32 ms**（p95 32.88 / p99 39.49）対 current Celtium **p50 = 28.28 ms**
  （p95 48.01 / p99 75.42）—— current は約 **1.7 倍遅い**。
- **軽世界（worldLayers=3）**: baseline **p50 = 16.05 ms** 対 current **p50 = 6.84 ms** —— current の方が **速い**。
- 解釈: baseline は両世界で **p50 ≈ 16 ms に固定**（60 fps の vsync/フレーム上限で cap —— p95 も ~17 で真の frame
  cost は不明、cap 以下）。current は cap 無し：軽世界では cap を下回る速さ（6.84 ms ≈ 146 fps）。既定世界での
  28.3 ms は描画量に比例する重さ —— mirror refresh は ~2/frame で非主因。差の主因は **dual-render 構造**：
  GL（未 capture の GUI/sky/等）と VK（地形 + capture 済み immediate + present blit）の両 pass が semaphore で
  直列化し、VK 側の描画量に応じて frame 時間が伸びる。軽世界では VK 側が小さく全体も小さい。GL 側を消す
  （goal 項目6）方向が、この overhead の構造的な出口。

## 0.113 **修正・受入済み**: fixed-pipeline 残 state を native 化 —— texture matrix・depthFunc・glColor tint（A12/A13）

- **texture matrix**：`matrixMode(5890)` が触る stack を unit 毎に分離（`setActiveTexture` hook 新設 —— 単一 stack 時代は
  lightmap の unit-1 matrix（scale 1/256,translate 8）が unit-0 に漏れる構造的漏れがあった）。group 生成時に
  unit-0 の 2D affine を capture → push の dead 領域（旧 `u_MvColumn` の 64-111B、全 immediate shader が未読）
  へ encode → 4 textured shader で適用。enchanted item の glint（scale 8+translate+rotate/frame）で
  `texMatrixGroups=150` 実測（AMD・Intel 104）。
- **depthFunc**：`func_179143_c` hook 新設（state model の field は ordinal 初期値で誰も書いていなかった）→
  captureState `state[7]` → 5 pipeline class に `depthCompareOp` param。glint の `GL_EQUAL`・held item/outline
  composite の `GL_ALWAYS` が正しく通る。
- **glColor tint**：色要素なし format（6/7/9）で `group.color` を capture、vert shader で `v_Color=u_Color`
  （combine 時は const 側残す分岐）。drawRect の tint が shader に届く。
- **fixture**: `enchantItem`（glint 発火）、`allBlocks`（registry sweep 244 block 壁、両 GPU で描画一致）。
- **A12 は実質解消**：残項（separate alpha factors・color mask・alpha func・scissor）は presented RGB で非可視
  または mod GUI scope と分析済。

## 0.112 **修正・受入済み**: texture-env の GL_COMBINE/interpolate（hurt flash・team outline）を native 化

- vanilla の hurt flash と glowing/team outline は、同じモデルを **2 度目に** `glTexEnvi` の `GL_COMBINE_RGB=GL_INTERPOLATE`
  で描く —— lightmap unit が texture 結果と env 定数（hurt=1,0,0,0.3）を定数 alpha で mix する。capture 済み list に
  この state は入らず、native draw は落としていた。
- 実装：`celtium$envCombineColor` が replay 時に GL の lightmap unit の `GL_COMBINE_RGB` と `GL_TEXTURE_ENV_COLOR` を読み、
  `GL_INTERPOLATE` なら RGBA を `ImmediateGroup.combineColor` へ。push block は **128 バイト満杯**のため、alpha は
  `u_AlphaRef` の**符号**（負=combine・絶対値=定数 alpha）に encode、定数 RGB は **未使用だった** `u_ColorR/G/B` slotへ。
  `immediate_position_tex.frag` は負の alphaRef で `mix(colour, const, |alphaRef|)` を行い alpha はそのまま
  （GL の COMBINE_ALPHA=REPLACE・SOURCE0=PREVIOUS と一致）。
- 実測：hurtEntities fixture（kept mobs に `hurtTime=10`）で `CELTIUM_ENV_COMBINE rgba=1.0,0.0,0.0,0.3` が発火、
  PASSED・clean・退行なし（p469）。**outline（`enableOutlineMode` の team 色）も同 channel で native になる**。
  glow outline（entityOutline FBO + silhouette composite）は post-process 構造上 spectator と同じく**動作確認済み**
  （fixture `glowEntities`、両経路で同一 silhouette+edge、残差は entity/particle のフレーム差）。

## 0.110 **修正・受入済み**: fence wait の非 TIMEOUT 失敗（DEVICE_LOST 等）でも frame を abandon —— post-submit device lost の実機注入を追加（goal 項目7）

- `waitForIdle` の分岐：`VK_TIMEOUT` だけが `abandonFrame` を呼び、他の非 SUCCESS（DEVICE_LOST 等）は `noteStalled`
  するが frame を開いたまま返していた —— timeout 分岐のコメントが説く「open frame の semaphore が来ない →
  GL write-back が待つ」デッドロック条件を残していた。修正：非 SUCCESS 全般で `abandonFrame` する。
- 注入：`-Dceltium.vulkan.failDeviceLost` で fence wait の結果を一度 `VK_ERROR_DEVICE_LOST` へ差し替える
  （submit が queue へ渡った後の device lost —— pre-submit の `failSubmit` では作れない経路）。`CELTIUM_DEVICE_LOST`
  行で発火を記録。
- 実測（AMD `p460-dlost`）：注入発火後 `stalled; no further drawing until the renderer is rebuilt` で後続 draw を
  拒否、presentAttempts も停止、run は **28 s で bounded 終了・exit 0・PASSED・clean**（修正前なら GL write-back
  で hang していた経路）。stalledTeardown は使用中 command buffer を reset/re-record/destroy しない既存規則のまま。

## 0.109 **修正・受入済み**: default-framebuffer 合成 quad（vignette/portal 等の overlay）を shared target へ —— format-2 の定常残量を解消

- 残量計測で format-2（POSITION_TEX_COLOR）が唯一の**増加する**拒否（`target: no frame target`、~40/周期）だった。
  計器を `ensureFrameTarget()` へ追加して特定：fbo==0 の default-FB composite（`framebufferRender` とは別、
  vignette/portal/pumpkin/hurt の overlay quad）は `supportedImmediateTarget` の redirect で shared target へ送られるが、
  直後の `ensureFrameTarget()` が **GL の bound framebuffer を再読**して fbo==0 → false、さらに
  `currentFramebufferViolation` も fbo==0 を "default framebuffer" として拒否 —— redirect が決めた描画を
  2段目で潰していた。
- 修正：redirect 成立時は ensureFrameTarget を `frameTarget.matches(targetW,targetH)` の確認に置き換え、
  violation 検査も skip（bound fbo=0 は redirect が正と決めた状態）。`framebufferRender` の self-blit は
  `samplesBoundFramebufferColor` で present path へ行きこの経路に来ないので feedback 問題なし。
- 結果：format-2 accept **2:0 → 2:170**、`no frame target` 拒否消滅、残り拒否は startup transient のみ
  （"no pass has run yet"、固定件数）。`defaultFramebufferCompositesTaken` が増加＝overlay が shared image に
  到達。AMD `p454-cmp` PASSED・clean。GL が fbo 0 へ描いていた overlay（Vulkan present では不可視だったもの）が
  提示画像へ乗るようになった。

## 0.108 **修正・受入済み**: model draw（表示リストの entity/手の native 描画）を既定 ON に（goal 項目5 の一部）

- `MODEL_DRAW`（`celtium.vulkan.modelDraw`）の既定を false → true。受入根拠：entity 場面（NoAI で固定した
  creeper/zombie/spider/enderman を視野錐 `-Z`・地表へ spawn、`captureEntities` で live frame 保持）で
  **AMD 0.35 %・Intel 0.16 %**、同場面の GL replay 基準（`modelDraw=false`）が **AMD 1.15 %** —— native が
  基準以下の残差（残差は移動粒子のフレーム差、§0.104 と同じ計測限界）。旧測定の 2.19 % が消えた理由は
  texture 所有修復（§0.106）が model の lightmap/atlas 供給も直したため。
- `celtium$drawModelList` は capture/build 失敗で `false` を返し、その場合は GL replay を cancel しない ——
  失敗時は旧 replay がそのまま走る安全側。flag は `-Dceltium.vulkan.modelDraw=false` で残す。
- fixture の spawn 位置を修正：`keepEntities` 場面の spawn を視野錐内（プレイヤーは yaw 180=−Z 向き）・地表へ
  移動（従来は player 周囲に散在させ大半が視野外だった）。`captureEntities`/`keepEntities` 併用で live frame の
  entity を計測可能に。
- 標準場面（entity なし）でも既定 ON で PASSED・clean —— 手/アイテムの list 2/3 が native 描画へ移行して退行なし。

## 0.107 **修正・受入済み**: immediate group の `Object[]` 添字契約を型付き `ImmediateGroup` へ（goal 項目3）

- `immediateGroups`/`recordedImmediateGroups`/`retiredImmediateGroups` の `Object[]`（15 slot・添字意味が 3 構築
  site のコメントにだけ存在）を **`ImmediateGroup`**（`ImmediateGroup.java`・8 引数 ctor＋named field）へ変換。
  `GroupStamp`・`PreparedImmediate` も同ファイルの package-private class へ集約した（契約が 1 ファイルに住む）。
  `group[12]`=batches、`group[7]`=target、`group[14]`=stamp のような添字参照は全て named field になった。
- `ImmediateGroupQueue.retireGroupsForTarget` は `List<ImmediateGroup>` を取る —— 「target 未帰属は残す」規則は
  `group == null` keep で保持（malformed 配列という概念自体が型で消えた）。
- `scripts/cpu-contract/TextureContractCheck` の A32 検査も型付きへ更新（`group(target)` ヘルパ＋null keep 検査）、
  `CELTIUM_CPU_CONTRACT PASS` を確認。AMD `p445-grp` 標準 fixture PASSED・clean で退行なし。

## 0.106 **修正・受入済み**: texture 所有の欠損を3つ閉じる —— lightmap の per-update readback 176→2、sprite patch の in-place 化、image/view/descriptor の per-update churn 解消

- **発見した3つの契約欠損（goal 項目2・texture 所有統合の内訳）**:
  1. **announce が `bound` を見ていた**: `func_110988_a`（`TextureUtil.uploadTexture`＝`DynamicTexture.updateDynamicTexture` の経路）は
     書き込む texture を**パラメータ**として持つのに、`announceDynamicTexture` はその時点の bound texture へ画素を aim していた。
     lightmap(id 12, 16x16) の画素は別 texture（1x1 の id 28 等）へ投げられるか拒否され、mirror は空のまま →
     `invalidateAll`（periodic blanket refresh）の cadence で **毎更新 readback**（run あたり ~176 回の 16x16 MIRROR）。
  2. **`onGlTextureReallocated` が同一サイズの内容更新でも mirror を forget**: `func_110988_a` のたびに
     `forgetTexture` → image/view/descriptor を per-update で破棄・再生成（`forgotten` ~176）。「全 upload＝再割当」の
     誤判定で、DynamicTexture の内容更新を真の identity 変更と混同していた。
  3. **所有状態の未同期**: `fromImage`（announced 全画像 → mirror 構築）が `fullyInitialized` を立てない → atlas の
     sprite patch が「未初期化への部分 patch」として拒否 → readback（`beforeInit` 818）。`consumeReadback` が
     `staleIds` を消さない → direct-path skip を記録しつつ registry `get()` が結局 readback（消せていなかった readback 流）。
- **修正（producer/consumer/invalidate/delete を同じ契約へ）**:
  - announce は **glTextureId パラメータ**を使う（bound ではなく）。`announceDynamicTexture(id, pixels[, w, h])`。
    `DynamicTextureMixin` は `getGlTextureId()`、`TextureUtilMixin` はメソッドの id+size パラメータを渡す。
  - `onGlTextureReallocated(id, w, h)` を**サイズ対応**に：同一サイズの再 upload は `invalidate`（stale マークのみ、
    announce が直後に in-place 更新）で、image/view/descriptor を保持。真の再割当（サイズ変更・未登録 id のみ）で
    `forgetTexture` + 直接参照を落とす。
  - `fromImage` が `markFullyInitialized` を呼ぶ（announced image は常に全面）→ 以降の patch は初期化済み image へ入る。
  - `consumeReadback` が `staleIds` を消す（consume＝「image は GL と同じ内容」の確定）＋ `get()` の stale refresh が
    `canSkipReadback` を先に見る → announce 済み write はどの経路でも readback しない。
  - `announceFullPixels` の fallback 生成は **DSA で対象 texture の size と GL mag filter を照会**（bound ではなく）→
    mirror は GL の filter に合わせて作る（lightmap は linear）。
- **受入（水アニメーション含む world）**:

  | GPU | 16x16 (lightmap) readback | atlas `beforeInit` patch 拒否 | `forgotten` | verdict / diff |
  |---|---|---|---|---|
  | AMD `p442-tex` | **2**（旧 ~176） | **18**（旧 818、初回窓のみ） | **0**（旧 ~176） | PASSED・clean・0.005 % |
  | Intel `p443-tex-intel` | **2** | **18** | **0** | PASSED・clean |

  `CELTIUM_DYNAMIC_ANNOUNCE texture=12 size=16x16`（正しい id+size、旧は bound=28 size=1x1 への誤 aim）、`full=` 全面
  announce が ~1900（lightmap 更新がすべて in-place に入る）。sprite patch も `patches` ~260→~966 で直接書き込まれる。
- **delete 半分も hook した**：`TextureUtil.func_147942_a`（`deleteTexture`、vanilla の全削除が通る唯一の funnel：
  TextureManager unload・Framebuffer teardown・`AbstractTexture.func_147631_c`）→ `onGlTextureReallocated(id)`
  （always-forget）。id 再使用が mirror の旧内容を引きずる穴を塞いだ（`p444-del` PASSED/clean）。
- **残る計測限界**：`glTexSubImage2D`/`glCopyTexSubImage2D` など TextureUtil を通らない直 GL 書込み、mod が
  `GlStateManager.deleteTexture` を直接呼ぶ経路は未 hook —— `invalidateAll` の periodic refresh が安全側で拾う。
  `full` announce 自体の upload/submit 実コストは `patchCostSummary` に出る。

## 0.105 **修正・受入済み**: Nether 受入 —— portal 帰還の解消（AXIS 直交 step）と、backend 再生成後の frame target デッドロック復元

- **旧 NO_CAPTURE の内訳は2つで、両方とも直した**:
  1. **portal 帰還**: fixture は到着後 `posZ + 2.0` で portal から外していたが、**portal の AXIS が Z** の場合その step は
     portal の面方向に滑るだけで中に残る → `portalCounter` が ~80 tick 溜まって vanilla が 0 へ送り返していた。
     **portal ブロックの AXIS を読み、面に直交する方向へ step**（AXIS=X→Z 方向、AXIS=Z→X 方向）。AMD/Intel とも
     到着後 -1 に留まった（p436 で帰還は消えたが描画が別原因で FAILED）。
  2. **frame target デッドロック**: 次元転移で world renderer／backend が**新しいインスタンス**に再生成される
     （`submitted=0`・`gameFramebufferIsShared=false`・`targetGeneration=0`）。`mc.framebuffer` の色 texture は
     **旧 backend 所有の共有 texture を指したまま**（旧 backend と共に死んで 0x0 と読まれる）→ 新 backend の
     `ensureFrameTarget` が bound attachment から寸法を読めず永久拒否（`no frame target for 0x0`、passes=0・
     skipped=537）。`adoptableFramebuffer` は `frameTarget != null` 前提で鶏卵に。
     **修正**: adopt 経路で `mc.framebuffer` の論理サイズ（正しい）へ**フォールバック**して target を復元し、
     adoption が死んだ texture を新しい共有 texture へ差し替える（`ensureFrameTarget()` は attachment 読み、
     新 overload は受取寸法で target を作る）。
- **受入（両GPU・`dimensionAtTick=80`・`dimension=-1`）**:

  | GPU | capture 時 dimension | passes / skipped | verdict |
  |---|---|---|---|
  | AMD `p437-nether` | **-1（到着後も留まる）** | **967 / 0** | PASSED・clean・VUID 0・実 Nether 画像 |
  | Intel `p438-nether-intel` | **-1** | **349 / 0** | PASSED・clean・VUID 0 |
- **退行確認（通常場面・AMD `p439-regress`）**: 提示 vs screenshot 0.002 %・PASSED・clean —— adopt 経路の変更は
  通常 run を壊さない（`ensureFrameTarget()` の attachment 読み経路は不変、fallback は attachment が読めない時だけ）。

## 0.104 **確定・受入済み**: entity 場面の「format 5 残差」は描画欠陥ではなく、**fixture のキャプチャ仕様**（生存粒子 vs 除去後スクリーンショット）

- **残差の正体（数値の内訳）**: entity 場面の 1.25〜1.62 %（AMD）/1.33 %（Intel）は、提示画像の**生存粒子が写る画素そのもの**だった。
  証拠: GL スクリーンショットの同領域に粒子画素は **0**、format 5 を受付から外すと **0.32 %**（=この場面のノイズ床、p426b で 0.33 % を実測）。
  fixture はキャプチャ待機ブロック内で **毎 tick `removeNonPlayerEntities`＋`effectRenderer.clearEffects`** を走らせており、
  **keepEntities の有無に関係なく**スクリーンショットは実体・粒子の無いフレームになる。対して提示ダンプは除去前（tick 198 等）のフレーム。
  「残差」は **存在/非存在の差** であり、描画値の差ではなかった。
- **format 5 の提出者・頂点データ（両方とも実粒子で正当）**:
  - 提出元スタック = **`ParticleManager.func_78874_a:371` ← `EntityRenderer.func_175068_a:1488`**（`CELTIUM_FMT5_SITE`）。
  - 頂点 = `pos/uv/color=0xffb26666/lmap=(0,176)`（`CELTIUM_FMT5_VERTEX`）。0xffb26666 は suspend 系粒子の色、
    `lmap=(sky=0, block=176)` = その位置の照度として正当（水中/遮蔽下）。
- **同フレーム実測（format 5 の正しさの直接検証）**: fixture に静止粒子の実比較を足した。
  - `particles=true` が FLAME ×24（速度0）を worldTicks 197 に置く（`py=9.6..11.4`、地表ガラスの直上・視線高さ）。静止ゆえに
    どのフレームでも同じ位置 → **提示ダンプとスクリーンショットが別フレームでも粒子比較ができる**。
  - AMD `p435-still197`（平地・粒子・dumpPresentedTick=199）: **両画像に FLAME が写る**（P 1515 px・S 1157 px・同一帯 y156-193）。
    差分 0.157 % の中身 = **同位置の粒子エッジ**で、色は両方オレンジ（P (252,151,0)・S (252,150,0)）。
    P の warm 画素が多いのは粒子が**若い**（`ParticleFlame` は経 tick でスプライト index を進めるので経年で絵が変わる）。
    **0.157 % = 経年・サブピクセルの差、描画値の誤りではない。**
- **fixture の新フラグ（実測用・既定は旧挙動）**:
  - `celtium.validation.captureEntities=true` — キャプチャ直前の `removeNonPlayerEntities`/`clearEffects` をスキップし、
    スクリーンショットに生存シーンを残す（`captureEntitiesKept`/`nonPlayerEntitiesAtCapture` を記録）。
  - `celtium.validation.dumpPresentedAtCapture=true` — キャプチャ呼出し直前に提示ダンプを要求し、次の present が書く
    （`presentedDumpRequestedAtCapture` を記録）。tick 指定版は「次の present」が終了間際では来ないことがあるので用途限定。
  - 粒子の生成 tick を 199→197、高さを空の中へ移動（`py=9.6+(i/6)*0.6`）。
- **結論**: **format 5 のパイプラインは正しい**（実粒子の位置・UV・色・ライトマップ式は block 経路と同じ検証済み計算、
  同フレームの静止粒子比較で残差は経年差のみ）。entity 場面の「残差」は比較系の時相ズレであり、**§5.2 はこれで閉じる**。
  なお「動く粒子の画素一致」はこの fixture では原理的に取れない（同フレームの GL 参照がない）—— これは計測限界であって欠陥ではない。

## 0.103 **修正・受入済み**: 提示 blit を GL composite の配置契約へ（resize 後の伸縮・上下反転を解消）

- **事実の整理（「提示画像が 1280x720 のまま」の内訳）**:
  - fixture の `mc.resize()` は**ゲームの framebuffer だけ**を 1024x640 にする。**実ウィンドウ（X11 surface）は 1280x720 のまま**
    （この harness は `glfwSetWindowSize` を無視する）。X11 では swapchain extent = `currentExtent` 固定なので、
    **提示画像が 1280x720 であること自体は正しい** —— 1024x640 の swapchain は作れない。
  - 実際の欠陥は2つ: (1) `presentImage` がフレームを swapchain 全面へ**伸縮 blit** していた。GL の composite は
    viewport `(0,0,dispW,dispH)` に **1:1・左下アンカー・window clip** で描く。(2) swapchain は 1280x720 ハードコードで作られ、
    再作成は**ドライバの out-of-date 報告頼み**だった（この compositor は報告しないので実 resize でも追随しなかった）。
  - **潜伏していた別欠陥**: shared image の texel row 0 は絵の下端（GL texture 順序）。それを 1:1 で swapchain に
    コピーしていたので、**実ウィンドウ上では絵が上下反転**していた（比較で反転が必要だったのはその症状）。
- **契約（1つ）**: swapchain 画像は「GL の composite が window framebuffer に置くもの」と同じ内容を持つ。
  = **正立・1:1・左下（絵座標で下端）アンカー・window で clip**。フレームが window より小さいときは viewport の位置に
  置き（伸縮しない）、大きいときは window が viewport を clip するのと同じく左下を切り出す。
  swapchain extent は surface の実効 extent（`currentExtent` 優先、未固定なら要求値を min/max に clamp）に追随し、
  **フレーム毎に1回 `matchesSurfaceExtent` で照合**してずれていれば即作り直す（OOD 報告を待たない）。
  作成要求は実 GLFW framebuffer サイズ（1280x720 固定を撤去）。`presented.png` はこれで**実ウィンドウの内容そのもの**（正立）になる。
- **比較作法の変更（重要）**: `celtium-presented.png` は**正立**になった。比較は **反転なし**で、
  drawable が window より小さい run では提示画像の**左下 `dw×dh` 領域**（この fixture では `[H-h:H, 0:w]`）を screenshot と比べる。
  旧「反転して比べる」は提示が反転していた名残で、以降の run には適用しない。
- **受入（resize fixture・両GPU）**:

  | GPU | 提示画像 | 描画領域 vs screenshot（反転なし） | stall | presentedFromVulkan | verdict |
  |---|---|---|---|---|---|
  | AMD (`p421-resize-blit`) | 1280x720（実 window） | **0.001 %**（1024x640 領域・上端帯は未描画=黑） | **0件** | 810 | PASSED・clean・VUID 0 |
  | Intel (`p423-resize-intel`) | 1280x720 | **0.000 %** | **0件** | 356 | PASSED・clean・VUID 0 |

  `presentResult=blitted 1024x640 onto image N`（伸縮ではなく領域 blit）・`CELTIUM_REPLACE_DROP batches=14`・`presentFailures=0`。
- **退行確認（resize なし・AMD `p422-noresize`）**: 提示画像 vs screenshot **反転なしで 0.000 %**（旧来の 0.001/0.01 % と同等）、
  提示 751・stall 0・PASSED・clean・VUID 0。**上下反転が要らなくなったこと自体が、旧提示が逆さだった証拠**。
- **残る論点**: window 外の帯は未描画（GL でも未定義領域。本 fixture では AMD 黑・Intel stale）。実ウィンドウでの正立表示は
  コード契約どおりだが、compositor 出力の実測は条件 (c) の未確認項目のまま。

## 0.102 **修正・受入済み**: フレームバッファ置換の**期間全体**で順序 flush を行わない（resize の恒久 stall が解消）

- **前節の一般化**: §0.101 は「bind の1箇所」だけを狙っていた。実際には置換の**期間中**に走る順序 flush がすべて同じ待機を持ち、
  bind を塞ぐと**次は `GlStateManager.clear` 経由の flush が同じ場所で失敗**した（実測: stall site が
  `celtium$noteRenderTarget` → **`celtium$clear:1314`** へ移動。提示は 342 のまま）。
  ＝ **両者に共通なのは「置換期間」**である。
- **実装（1契約）**:
  - `noteBindSize(w,h)` が**サイズ不一致を検出したら `framebufferReplacing = true`**（一点物ではなく**期間**にする）。
  - `celtium$ensureTargetForBoundFramebuffer()` が**共有 image を新サイズに用意できた時点で `false`** に戻す
    （bind の RETURN フックが毎フレーム呼ぶので、期間は必ず閉じる）。
  - `flushPendingBeforeGlDraw()` は**期間中は順序 flush を行わない**。保留グループは
    **件数を数えて捨てる**（`CELTIUM_REPLACE_DROP` と `postAcceptDroppedGroups`。既存の「描けないものを描いたと言わない」規則と同じ）。
  - `bindFramebuffer` の一点物フラグは撤去（期間が覆うため）。
- **受入（AMD・resize あり・120 s）**:

| 指標 | 修正前 | **修正後** |
|---|---|---|
| `CELTIUM_STALL` | 1件（恒久 stall） | **0件** |
| `presentedFromVulkan` | 265 / 331 / 342 | **843**（resize なしの 785 を上回る） |
| 提示画像の書き出し | **書かれない** | **書かれる** |
| verdict | PASSED | **PASSED**（clean・msgCount 0） |

  `CELTIUM_REPLACE_DROP batches=14 target=1280x720 frameId=364` ＝ 境界で捨てたのは**14バッチ**（名前をつけて数えている）。
- **退行確認（AMD・resize なし・120 s）**: 提示画像 vs スクリーンショット **0.001 / 0.01 %**（変更前と同一）、
  `presentedFromVulkan` **847**、exit 0 / clean / PASSED / msgCount 0。
- **新たに観測した未解決点（記録）**: resize run で **提示画像が 1280x720 のまま**（スクリーンショットは 1024x640）。
  ＝ **stall は解消したが、提示（swapchain 側）はまだ旧サイズの内容**である。次の焦点。
- **次の一手（1点）**: **swapchain の再作成がウィンドウの新サイズに追随しているか**を確認する
  （`ensureSwapchain()` の out-of-date 判定と、提示画像の寸法がどこで決まるか）。

## 0.101 修正点を確定: `bindFramebuffer` の**ターゲット切替時の flush**が resize で待機に失敗する

- **読んだ事実**: `bindFramebuffer(targetIdentity, isMain)` は
  ```
  if (!this.immediateWindowBusy && targetIdentity != this.currentRenderTarget) {
      flushPendingBeforeGlDraw();      // ← ここに待機がある
  }
  ```
  を持つ。resize で MC がフレームバッファを作り直すと **同一性（`System.identityHashCode`）が変わる**ため、
  この分岐が走り、**その中の待機が失敗して `noteStalled()` が立つ**（§0.100 のスタックと一致）。
- **修正点（次の実装単位・1つ）**: **サイズが変わった bind では flush を致命にしない**。
  - 具体的には、この分岐で「**フレームターゲットのサイズと、いまバインドされている FBO のサイズが食い違う**」場合
    （＝ resize の境界）は、**待機を伴う flush を行わずに**ターゲットを切り替える。
    直前のフレームバッファの内容は**この時点ですでに提示済み**で、その形は存在しないため、順序を守る対象がない。
  - 「settle できないときは資源を解放しない」という**既存契約はそのまま**（解放しない）。変えるのは
    **描画を止めないこと**だけ。
  - `noteStalled()` は計器として残す（次に同種の失敗が起きたとき site を言う）。
- **受入（実施時）**: resize run で **`CELTIUM_STALL` が出ない**、`presentedFromVulkan` が同 duration の
  resize なし水準（700 台）に戻る、提示画像が **1024x640** で書かれる。
- **現 HEAD の受入（変更なし）**: resize なし run は 0.001 / 0.01 %・提示 806・clean/PASSED を維持。

## 0.100 resize の最終原因: **bind フック内の `bindFramebuffer` の待機が失敗し、stalled が恒久化する**

- **計器**: `gpuStalled = true` の4箇所すべてを `noteStalled()` に置き換え、**一度だけ** site を stdout に出す。
  あわせて周期レポート行に **`presentSkipReason`** を載せた（post-resize の理由がどこにも出ていなかったため）。
- **結果（resize run）**:
  ```
  presentSkipReason の内訳: [no frame target, or stalled]=100, [presented]=78, [no completed frame waiting]=2
  CELTIUM_STALL site=net.minecraft.client.shader.Framebuffer.handler$...celtium$noteRenderTarget:553
               target=1024x640 frameId=245 frameTargetNull=false presentSkipReason=[presented]
  ```
  ＝ 最多の理由は **`no frame target, or stalled`** で、条件は `frameTarget == null || gpuStalled`。
  **フレームターゲットは存在する**（`frameTargetNull=false`）ので、**`gpuStalled` が真**である。
  そして **stall を立てたのは bind の HEAD フック**（`celtium$noteRenderTarget`）で、**resize のフレーム**（frameId=245,
  target=1024x640）に起きている。
- **連鎖（確定）**:
  1. resize で MC がフレームバッファを作り直す。
  2. ゲームがそれを bind → HEAD フックが `backend.bindFramebuffer(...)` を呼ぶ。
  3. **その中の待機が失敗**し `noteStalled()` が走る。
  4. `gpuStalled` は**設計上クリアされない**（使用中 command buffer の資源を守るため）ので、
     **以降 native 描画と提示がすべて止まる**（`presentedFromVulkan` が 355 で凍結、`glSwapsSkipped` が同数）。
- **意味**: §0.95 の「stalled が真因」は**正しく**、§0.96 の「呼び出し順だけ」という訂正は**片側だけ**だった。
  実体は **resize の bind 経路で待機が失敗し、恒久 stall に入る**ことである。
- **次の一手（1つの契約）**: **フレームバッファの作り直しに伴う待機を致命にしない**。
  サイズが変わった bind（＝世代交代）では、待機を**スキップまたは有界**にして stall を立てない。
  既存の「settle できないときは解放しない」契約は**そのまま**（解放はしない）が、**描画停止にはしない**。
- **受入（実施時）**: resize run で **`CELTIUM_STALL` が出ない**こと、
  `presentedFromVulkan` が同 duration の resize なし水準（700 台）に戻ること、提示画像が **1024x640** で書かれること。

## 0.99 **退行なし**（通常経路の再測）: 0.001 / 0.01 %・提示 806・clean/PASSED

- **変更が毎フレームの bind 経路に触れるため、resize なしの通常場面で再測した**（AMD・120 s・dump あり）:
  | 指標 | 変更前（p406） | **変更後（p414）** |
  |---|---|---|
  | 提示画像 vs スクリーンショット | 0.001 / 0.01 % | **0.001 / 0.01 %** |
  | `presentedFromVulkan` | 785 | **806** |
  | `gameFramebufferAdopted` | 1 | 1 |
  | verdict | PASSED | **PASSED**（clean・msgCount 0） |
  ＝ **通常経路は同一水準**で、**resize 経路だけが改善**（2.21 → 3.31 提示/秒）。
- **計器は1回だけ出る**（`afterBind size=1280x720 ensured=true adopted=7 currentFbo=1`）＝ ノイズなし。

## 0.98 **実装した**: adoption を bind の RETURN へ移し、その場でターゲットを実サイズに合わせる（機構は計器で確認）

- **変更（1単位）**:
  - `VulkanTerrainGpuBackend` に **`celtium$ensureTargetForBoundFramebuffer()`** を公開（`ensureFrameTarget()` をそのまま呼ぶ）。
  - `FramebufferMixin` の adoption を **bind の HEAD から RETURN へ移動**し、その注入から
    「まず ensure、次に adopt」の順で実行する。HEAD 側には**理由のコメント**を残した。
  - RETURN 注入に**サイズが変わったときだけ出す計器**（`CELTIUM_ADOPT_PROBE`）を入れた（毎フレーム bind するので件数制限では
    resize 前に使い切るため）。
- **計器の結果（resize run）**:
  ```
  afterBind size=1280x720 ensured=true adopted=7 currentFbo=1     <- 初回 adopt
  afterBind size=1024x640 ensured=true adopted=7 currentFbo=7     <- resize 後
  ```
  ＝ **resize 後も新サイズで ensure が通り、共有 FBO（7）が維持されている**。
  **リサイズ関連の拒否は消えた**（debug に残るのは起動時の `no frame target for 1280x720` 1件のみ）。
- **受入指標の訂正（自分の前節の条件が誤り）**: `gameFramebufferAdopted >= 2` を条件にしたが、
  **FBO 名が 7 のまま**なので**再 adopt は不要**で、このカウンタは 1 のままで**正しい**。
  正しい指標は「**resize 後も共有 FBO が維持され、提示が続くこと**」である。
- **実測（提示レート・duration で正規化）**:
  | run | duration | presentedFromVulkan | 毎秒 |
  |---|---|---|---|
  | resize 前の実装（p410） | 120 s | 265 | 2.21 |
  | **修正後（p413）** | 100 s | **331** | **3.31** |
  | resize なし（p406） | 120 s | 785 | 6.54 |

  ＝ **改善したが、resize なしの水準には戻っていない**（未解決として残す）。
- **受入**: p413/p412 とも exit 0 / **clean** / **PASSED** / msgCount 0。
- **次の一手（1点）**: resize 後に残る提示の目減りを、**提示経路の拒否理由ではなくフレーム供給側**で見る
  （resize で作り直された `Framebuffer` の**テクスチャの再確保**と、1フレームあたりのどこが重いかを計測する）。

## 0.97 修正手順の障害を特定: `ensureFrameTarget()` は**バインド中の FBO** を読むので HEAD では古いサイズを読む

- **読んだ事実**:
  - `celtium$adoptableFramebuffer(w,h)` は `frameTarget.adoptableFramebuffer(w,h)` へ委譲し、**サイズ不一致なら 0**（＝付け替えなし）。
  - `ensureFrameTarget()` は**そのとき GL にバインドされている FBO の色テクスチャ**からサイズを読んで作り直す。
  - `FramebufferMixin` のフックは `func_147610_a`（**bind**）の **HEAD** にある ＝ その時点でバインドされているのは
    **ゲームの新しい FBO ではなく、直前まで使っていた FBO**（MC は `func_147610_a` の中で `glBindFramebuffer` する）。
    ＝ **HEAD で `ensureFrameTarget()` を呼んでも旧サイズを読む**ので、この経路では直せない。
- **設計（次の実装単位・1つ）**: 同じ `FramebufferMixin` に **`func_147610_a` の RETURN を対象にした注入**を足す。
  - RETURN では**ゲームの新しい FBO がバインド済み**なので、バックエンドに
    **「今バインドされている FBO のサイズでターゲットを用意する」**入口（`ensureFrameTarget()` と同じ `settleGpu` 契約）を公開して呼ぶ。
  - その直後に**既存の adopt 判定**（`framebufferObject != adopted` なら付け替え）を同じ注入から実行する。
  - これで「**サイズ変更＝世代交代**」が bind 経路でも成立し、`gameFramebufferAdopted` が 2 以上になる。
- **なぜ HEAD のままでは駄目か（記録）**: HEAD でターゲットを作り直すと**旧サイズ**で作り直してしまい、
  かえって共有 image を 1280x720 に固定する。**順序（bind 完了後に読む）が本質**である。
- **受入（実施時）**: resize run で `gameFramebufferAdopted >= 2`、
  `presentedFromVulkan` が同 duration の plain 水準（700 台）へ戻り、提示画像が **1024x640** で書かれること。
- **受入（現 HEAD・変更なし）**: resize なし run は AMD/Intel とも clean/PASSED/VUID 0 を維持。

## 0.96 resize の機構を確定: **bind 時点で共有 image が旧サイズ**なので再 adopt が成立しない（前節の「stalled が真因」も訂正）

- **前節の訂正**: `rendererDebug` を見ると resize run でも **`target=1024x640`**、地形は **`draws=62 sections=62`** で
  plain run と同一。＝ **フレームターゲットも地形も新サイズへ追随している**（「旧サイズのまま」も「stalled で停止」も
  説明として不十分だった）。ログの `no frame target for 1280x720`（286 件）は **resize 前（tick<100）の分**である。
- **機構（コードとカウンタで確定）**:
  1. MC の resize は **同じ `Framebuffer` オブジェクトのテクスチャを作り直す**（`createFramebuffer`）。
  2. `FramebufferMixin` は `func_147610_a`（bind）の HEAD で
     `celtium$adoptableFramebuffer(self.framebufferWidth, self.framebufferHeight)` を呼び、
     `self.framebufferObject != adopted` なら**共有 image へ付け替える**（＝再 adopt の設計は**既にある**）。
  3. しかし **bind の時点ではフレームターゲット（共有 color/depth）はまだ 1280x720** で、
     新サイズへの再作成は**そのフレームの最初の offer**（＝bind より後）に行われる。
     したがって **`adoptableFramebuffer(1024,640)` は 0 を返し、付け替えが起きない**。
  4. 結果: `gameFramebufferAdopted=1` のまま（**一度も再 adopt されない**）、
     以降の present は共有 image から出せず **`presentedFromVulkan` は resize 前の 265 で止まる**（plain は 785）。
- **意味**: 欠陥は「サイズ追随の欠落」ではなく **呼び出し順**である。
  **adoption が要求するサイズでターゲットを作り直してから判定する**必要がある
  （＝「サイズ変更＝世代交代」を、**bind 時点**にも同じ契約で適用する）。
- **次の一手（1点）**: `adoptableFramebuffer(w,h)` に**要求サイズを渡して、必要ならその場で共有 image を作り直す**
  （既存の `ensureFrameTarget()` の再作成経路と同じ `settleGpu` 契約を使う）。その後に付け替える。
- **受入（実施時）**: resize run で `gameFramebufferAdopted` が **2 以上**になり、
  `presentedFromVulkan` が同 duration の plain 水準（700 台）に戻り、提示画像が **1024x640** で書かれること。

## 0.95 **resize の真因は「GPU stalled → 再構築されないまま描画停止」**（ログで確定）

- **前節の訂正**: ターゲットが「旧サイズのまま」なのではなく、**再作成が失敗して fail-closed のまま固まる**。
  `ensureFrameTarget()` は**バインド中の FBO のテクスチャから実サイズを読んで**、
  サイズが違えば**作り直す**（`settleGpu("frame target recreation")` を通してから）実装になっている。
  失敗時は**古いターゲットを解放せず、新しいものも作らない**（コメントどおり、使用中 VkFramebuffer を破棄しないため）。
  拒否メッセージが**旧サイズ**（1280x720）を出すのは、`targetWidth/Height` の更新が**成功時だけ**だからである。
- **真因（ログで確定）**: resize run にだけ次の行が繰り返し出る。
  ```
  GPU stalled; no further drawing until the renderer is rebuilt
  ```
  （同じ duration の resize なし run には**一度も出ない**）。＝ **resize が GPU の stalled を誘発**し、
  「**再構築されるまで描画しない**」設計のため、**その後は何も描かれない**。
  - 提示数 265 は **resize（tick 100）以前の分**とほぼ一致し、**以降はゼロ**。
  - `glSwapsSkipped=265` も同数で、以降は GL swap に戻っている。
- **意味**: resize 経路の欠陥は「サイズ追随」ではなく、**stalled からの再構築経路が無いこと**である。
  これは失敗時設計（fence timeout で解放しない・§0.37）と**同じ契約の裏側**で、
  「**再構築**」を明示的に持つ必要がある（goal の項目7: minimize/out-of-date の確認）。
- **次の一手（1点・読みのみ）**: **「renderer is rebuilt」が想定している再構築経路が既にあるか**を読む
  （初期化/teardown の対、`stalledStops` と `stalledTeardownSkips` の使われ方）。
  無ければ **resize を契機にした再構築**を 1 つの契約として実装する。
- **受入（現 HEAD・変更なし）**: resize なし run は AMD/Intel とも clean/PASSED/VUID 0 を維持。

## 0.94 **resize 後にフレームターゲットが旧サイズのまま**（提示が 265 対 785 に減る・ダンプが書かれない）

- **前節の「未説明」の答え**: `dumpPresentedImage` を**渡した再 run（p410）でも提示画像は書かれなかった**。
  ＝ フラグの渡し忘れではなく、**実際に書き出しが起きていない**（前節の推測は誤りだったので訂正する）。
- **計測（同一 duration の通常場面との比較・stdout の最終値）**:

| run | `presentedFromVulkan` | `glSwapsSkipped` | `gameFramebufferAdopted` |
|---|---|---|---|
| **resize あり（p410）** | **265** | 265 | 1 |
| resize なし（p406） | **785** | 784 | 1 |

  ＝ **resize 後は提示フレームが約 1/3 に落ちる**（残りは GL swap に戻っている）。
- **拒否理由に旧サイズが残る**: ログに **`no frame target for 1280x720`**（resize 後は 1024×640）が繰り返し出る。
  ＝ **フレームターゲット（と adoption 済みの共有 image）が resize に追随していない**。
  これが**提示画像の書き出しが書かれない理由**でもある（旧サイズのターゲットを待ち続ける）。
- **resize 自体は壊れていない**: p409/p410 とも exit 0 / **clean** / **PASSED** / msgCount 0、
  スクリーンショットは **1024×640**（＝ resize が Minecraft に届いた証拠）。**壊れているのは提示経路の追随**。
- **次の一手（1つの契約）**: **フレームバッファのサイズが変わったら adoption をやり直す**
  （共有 color/depth とフレームターゲットを新サイズで作り直し、`gameFramebufferIsShared` の判定も更新する）。
  「target/frame/generation を持つ」既存の契約に、**サイズ変更＝世代交代**を同じ単位で足す。
- **受入（実施時）**: resize run で `presentedFromVulkan` が resize なしの水準（同 duration で 700 台）に戻り、
  **提示画像が書かれ、1024×640 でスクリーンショットと比較できる**こと。

## 0.93 **format 5 の帰属を Intel でも確認** ＋ **冒頭表/goal.md の矛盾修正** ＋ **resize を AMD で受入**

- **Intel での確認（除外方向・entity 場面）**: `-Dceltium.vulkan.immediateFormats=12346789`（format 5 を除外）で
  **1.33 % → 0.36 %**（exit 0 / **clean** / **PASSED** / msgCount 0）。AMD は 1.25〜1.61 % → **0.32 %**。
  ＝ **format 5（粒子系）の native 描画という帰属が両GPUで確定**。
- **冒頭表と `docs/goal.md` の矛盾を修正**: どちらも「**Nether は両GPU PASSED（旧 NO_CAPTURE を置換）**」のままだったが、
  objective は「**旧 Nether PASSED は有効な受入から外す**」と明記している。両方を
  「**未成立**（一時的に -1 → GuiDownloadTerrain → 0 で NO_CAPTURE、根因未確定）」へ直した。
  あわせて冒頭表に **model 経路 2.19 %**・**entity 場面の format 5 帰属（両GPU）**・**通常場面の両GPU一致**の行を追加。
- **resize の受入（AMD・`-Dceltium.validation.resize=true`）**: exit 0 / **clean** / **PASSED** / msgCount 0。
  保存されたスクリーンショットが **1024×640**（既定 1280×720）＝ **resize が Minecraft に届いた証拠**。
  `presentedFromVulkan` は resizing 後も増え続けている（64→73→83→93 の途中経過）。
  - **未説明（記録）**: この run では**提示画像の書き出しファイルが残っていない**（`celtium-presented.png` が無い）。
    画像差の比較はこの run では**行えていない**。次の確認事項として残す。
- **意味**: 計画の項目7（reload/resize/world switch）のうち **resize が初めて受入**された。
  フレームターゲット側に resize/out-of-date の**明示処理は無い**が、この場面では破綻していない。

## 0.92 **`MIRROR_GL_MIPS` を撤去**（旧経路と lab flag を削除、AMD 2場面＋Intel 1場面で退行なし）

- **実施内容（17 編集・全参照を先に列挙してから同じ変更単位で）**:
  `VulkanGlTexture` の flag 宣言・accessor・`mirroredLevels` フィールドと3使用・
  `glLevels` とその4使用・ミラー取り込みブロック・`glLevelCount`・関連コメント2箇所・
  `for (level = 1; level < (MIRROR_GL_MIPS ? 1 : mipLevels))`・barrier の `oldLayout` 分岐・
  tail transition 条件、`VulkanTextureRegistry` の `mirrorsGlMips()` 三項。**残参照 0**。
- **一度だけビルドエラー**（accessor の**メソッド本体**を残してしまい `return` 欠落）。**その場で直して緑**にした。
- **受入（撤去後）**:

| 場面 | GPU | exit | clean | verdict | 提示画像 vs GL |
|---|---|---|---|---|---|
| entity | AMD | 0 | true | PASSED | 1.139 / **1.62 %**（既知のばらつき 1.25〜1.61 % 内） |
| 通常 | AMD | 0 | true | PASSED | 0.001 / **0.01 %** |
| 通常 | **Intel** | 0 | true | PASSED（msgCount 0） | 0.016 / **0.00 %** |

- **意味**: **現行の生成チェーン（＋1.0 クランプ）だけ**が残り、**測定で二十倍以上悪い併存経路**と
  **その lab flag が消えた**（「旧経路の併存を残さない／lab flag に正当性を依存させない」）。
  既定の画は**通常場面 AMD 0.01 % / Intel 0.00 %** を維持。
- **残る未完**: entity 場面の **format 5（粒子系）の native 描画**（§0.78〜§0.90 で候補を消し込み済み。
  除外で 1.25 % → 0.32 %）。**行列・ブレンド・被覆・シェーダ・SPIR-V・mip は棄却済み**。

## 0.91 **前節の mip 仮説を撤回**（既存の測定で 13.51 % と記録済み）／S23 の descriptor 疑いは**対処済み**／`MIRROR_GL_MIPS` は撤去対象

- **撤回（§0.90 の「maxLod を 0 にすべき」）**: `VulkanGlTexture` に**既存の測定記録**がある。
  > 「1.0 が **0.21〜0.28 %**、GL の全チェーン（8〜9 段）が **8.77〜8.82 %**、4 段が **7.37 %**、
  > **0.0 が 13.51 %**（9段の画像でも1段の画像でも）。ドライバに直接聞くと、この場面が束縛するテクスチャは
  > maxLevel/maxLod 0・NEAREST と報告するが、**それを写すと 1.0 クランプより五十倍悪い**」
  ＝ **`maxLod=0` は既に棄却済み**。**現行の生成チェーン＋1.0 クランプが測定最良**である。**§0.90 の次の一手は取り下げる。**
- **S23（descriptor が古い view を指す疑い）は対処済み**: `VulkanGlTexture.key()` は
  `(view * 31) ^ generation` で、**再作成時に `generation++`** される。＝ 再作成でキーが変わり、
  **古い descriptor は再利用されない**。
- **`MIRROR_GL_MIPS`（`celtium.vulkan.mirrorGlMips`、既定 false）は撤去対象**:
  上の測定どおり**併存する旧経路**で、現行より**二十倍以上悪い**（5.60 % 対 0.16〜0.28 %）。
  「旧経路の併存を残さない／lab flag に正当性を依存させない」に照らして**削除する**。
  **撤去に必要な箇所（実測）**: `VulkanGlTexture` の
  **66**（宣言）**213-215**（accessor）**68**（`mirroredLevels` フィールド）**263/265/277/279**（`glLevels` と条件）
  **387**（0 代入）**489-491**（`mirroredLevels` によるクランプ）**298-310**（ミラー取り込みブロック）
  **1146**（`glLevelCount`）**742**（コメント）＋ **367/469/471/725/903**（他にも flag 参照あり）、
  `VulkanTextureRegistry` の **540**（`mirrorsGlMips()` 三項）。
- **失敗の記録**: 一度に消そうとして**コンパイルエラー**（flag の参照が当初読んだ6箇所より多かった）。
  **壊れたツリーはコミットせず巻き戻した**（ビルド緑・差分なしを確認）。次は**全参照を列挙してから**同じ変更単位で消す。
- **受入（現 HEAD・変更なし）**: 既定 run は AMD/Intel とも exit 0 / clean / PASSED / VUID 0。

## 0.90 **被覆も棄却**（atlas/lightmap とも完全）／残る焦点は **sampler の mip（maxLod）**

- **計器（format 5 グループで、atlas・lightmap・グループのテクスチャの被覆を出す）**:
  ```
  CELTIUM_PROJ_PROBE fmt=5 ... atlasFull=true lightmapFull=true groupTexFull=true
  ```
  ＝ **ミラーは完全**（部分ミラーのまま未初期化領域をサンプルしている、という説明は**棄却**）。
- **ここまでで format 5 について棄却した説明（すべて実測・コード根拠つき）**:
  属性順（stride 28 の取り違え）／シェーダ式／SPIR-V の鮮度／光座標の生値（0x0000 は正当な最暗値）／
  生と保存済みの射影（完全一致）／ブレンド（観測値が配管済み）／atlas・lightmap の被覆（完全）。
- **残る候補（次の1点）**: **sampler の mip**。
  `VulkanGlTexture` のサンプラは `.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)`、`.minLod(0.0f)`、**`.maxLod(1.0f)`** で作られ、
  コード自身の注記にも「**この場面は maxLevel/maxLod 0 の1レベルで束縛するテクスチャがある**（minFilter/magFilter 9728＝GL_NEAREST）」
  と書かれている。＝ **GL の `GL_TEXTURE_MAX_LEVEL` を追えていない場合、存在しない mip をサンプルして黒くなる**。
  これは観測（**黒に近い 16.0 対 147.7**）と一致する。
- **次の一手（1点）**: **ミラーごとに `GL_TEXTURE_MAX_LEVEL`（mip 段数）を記録し、サンプラの `maxLod` がそれに従うか**を確認する。
  従っていなければ、**mip を持たないテクスチャは maxLod=0** にする。
- **受入**: 変更は計器のみ。既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.89 **行列とブレンドは正常**（計器とコードで棄却）／残る焦点は**テクスチャ（atlas ミラー）**

- **計器（format 5/3 の生の `glProjection` と、保存・送出される射影を並べる）**:
  ```
  CELTIUM_PROJ_PROBE fmt=5 rawDepth=(0.0,0.0,-1.001,-0.1) storedDepth=(0.0,0.0,-1.001,-0.1)
                            rawW=(-1.0,0.0) mvT=(0.0,-1.62,0.05)
  ```
  ＝ **生と保存済みが完全一致**（リマップで壊れていない）。`rawW=(-1,0)` は透視、
  `m14=-0.1` は地形の `-1.0` と違うが、これは **MC の遠景用射影**（空・雲・粒子に使う near/far）で**正当**。
  `mvT` はカメラ高さ。＝ **行列は原因ではない**。
- **ブレンド（コード読み）**: 粒子・ブロック両パイプラインとも
  `blendEnable(blend) / srcColorBlendFactor(srcFactor) / dstColorBlendFactor(dstFactor)` を使い、
  バックエンド側で **観測した GL のブレンド係数をグループに持たせ、パイプラインキーにも入れている**（`currentGroupBlendSrc/Dst`）。
  ＝ **ブレンドの配管も欠けていない**。
- **残る焦点**: **テクスチャ**。format 5 は**ブロック atlas**（`texture=29`）を張る族で、
  差が**暗さ（黒に近い）**であることから、**atlas ミラーが粒子スプライトの領域を持っていない**
  （＝未初期化領域をサンプルして黒くなる）可能性が最も高い。
  §A04 で「部分ミラーの完了」は入れたが、**粒子が使うスプライト範囲の被覆**は未確認である。
- **次の一手（1点）**: **atlas ミラーの被覆**を、同じフレームで **GL の atlas と比べる**
  （粒子 UV の範囲がミラー済みか）。もしくは **`hasFullContent()` の状態を format 5 のグループで記録**する。
- **受入**: 変更は計器のみ。既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.88 format 5 の頂点は**カメラ相対のワールド座標**／`immediateFormats=5` は**切り分けに使えない**（訂正）

- **計器（format 5 の先頭頂点と、そのときの行列ミラーの平行移動）**:
  ```
  CELTIUM_FMT5_POS first=(8.404,-2.504,0.076) camera=(0.0,-1.62,0.05)
  ```
  ＝ 頂点は **カメラ相対のワールド座標**（原点から 8.4 ブロック）。行列側の平行移動は **カメラ高さ**。
- **`-Dceltium.vulkan.immediateFormats=5`（format 5 だけ受理）の結果**: 差 **8.48 %**、
  差の領域は **平均 142.3 暗い**（提示 16.0 対 GL 147.7 ＝ **ほぼ黒**）で**大面積**。
- **この数字は format 5 固有の誤りとしては読めない（訂正）**: 他の format を**拒否**すると、
  グループの分け方・描画順・拒否経路（GL 本体が走る）が**同時に変わる**ため、
  **除外方向の測定（§0.78: format 5 を除外すると 1.25 % → 0.32 %）だけが有効な帰属**である。
  受理方向の絞り込みは**交絡する**ので、今後の帰属は**除外方向**で行う。
- **拒否理由の実測（重要）**: 定常状態での **format 5 の拒否は 0 件**（起動直後の
  `preflight: format=5 texture=29 (no pass has run yet in this instance)` が 8〜9 件だけ）。
  ＝ **定常状態では format 5 は全て native が引き受けている**。だから §0.78 の除外が効く。
- **確定した整理**:
  - **format 5 の native 描画が、entity 場面の差の主項**（除外で 1.25 % → 0.32 %）。
  - 差の性質は**暗さ**（§0.79）で、**両GPUで再現**（§0.85）。
  - 頂点は**カメラ相対のワールド座標**で、行列の平行移動は**カメラ高さ**（＝掛け方の候補が2つに絞られた）。
- **次の一手（1点）**: **除外方向**で format 5 の**一部だけ**を native に残す手段は無いので、
  代わりに **format 5 の native 描画を「GL と同じ行列で描いているか」**を、
  **1フレーム内の同一バッチ**について **`glProjection`/`glModelView` の生値**と**送った push constant** を並べて確認する。

## 0.87 **訂正**: §0.84 の「リスト内ジオメトリ」診断は**支持されない**（ゲートは受理を減らしていない）

- **カウンタの比較（同じ entity 場面・既定構成）**:

| run | offers | accepted | glBodyCompleted |
|---|---|---|---|
| ゲートあり（p398） | 16500 | **約 13100** | 約 4000 |
| ゲートなし（p366） | 18000+ | **約 13900** | 約 4100 |

  ＝ ゲート（`celtium$listIndex == 0` のときだけ提供）を入れても **accepted はほとんど減っていない**。
  つまり **`GL_LIST_INDEX != 0` で提供されるバッチは稀**であり、
  **「コンパイル中のバッチが即時経路に描かれている」という §0.84 の説明は成り立たない**。
  （`CELTIUM_MODEL_QUAD`／`CELTIUM_DISPLAY_LIST` は両 run で出ており、取り込み自体は常時動いている。）
- **ゲート時の画像（`.celtium-work/gate-compare.png`）**: 提示側の左に**巨大で暗い板**（16.8 対 148.3、143.7 暗い）。
  小さな四角ではなく**大きな面**なので、ゲートが壊したのは**別のもの**（＝受理を止めた結果の二重／欠落）。
  ＝ **ゲートは棄却のまま**でよいが、その理由は「リスト所有」ではなく**下流契約の片側変更**である。
- **維持する確定事項**:
  - 差は **format 5（粒子系パイプライン）**に帰属（除外で 1.25 % → **0.32 %**、§0.78）。
  - 差の性質は **暗さ**（§0.79）、両GPUで再現（§0.85）。
  - **粒子を止めても 1.43 %**（§0.83）なので、fixture が明示的に出す粒子だけではない。
- **次の一手（1点）**: **format 5 のバッチの頂点座標そのもの**を記録し、**ワールド座標か、カメラ相対か**を判定する。
  §0.44 で表示リストに対して行ったのと同じ切り分けを、format 5 に対して行う（**行列ではなく頂点の空間**を疑う）。

## 0.86 真因の**実装点を確定**: 受理すると `info.cancel()` が走り、**リストへの保存まで取り消される**

- **コードの該当箇所（`WorldVertexBufferUploaderMixin`）**:
  ```
  351:        if (accepted) {            // ＝バックエンドが引き受けた
  352:            celtium$acceptedByBackend++;
  ...
  358:                info.cancel();     // ＝GL 本体（この場合はリストへの保存）を取り消す
  ```
  `@Inject(method = "func_181679_a", at = @At("HEAD"), cancellable = true)`（119行）。
- **何が起きているか**: `glNewList` の内側では GL 本体は「**描画**」ではなく「**リストへの保存**」である。
  即時経路がそのバッチを受理すると **保存まで取り消される**ため、
  - **リストは空**になり（再生で何も出ない）、
  - ネイティブ側は**コンパイル時点の行列**で**その場で描いてしまう**（＝位置が合わない四角）。
  これが §0.84 の画像・§0.85 の両GPU再現と一致する。
- **正しい契約（次の実装単位）**:
  1. **コンパイル中**（`GL_LIST_INDEX != 0`）に来たバッチは、**即時描画せず**、**リスト id に紐づけて保持**する。
  2. その間 **GL 本体は取り消さない**（＝GL にもリストへ保存させる）。保持した分は**ネイティブでは描かない**ので二重にならない。
     ただし「受理＝描画」を前提にしたカウンタ・取消の連鎖を同時に直す必要がある（ここを推測で片方だけ変えない）。
  3. **再生時**（`celtium$drawModelList(listId)`）に、**その時点の行列**で保持分を描き、GL 本体は取り消す。
     これは §0.44/§0.71 で作った**表示リスト取り込み＋再生**の経路と同じ契約に揃える。
- **§0.84 のゲートがなぜ 8.91 % だったか（読みで確定した範囲）**: ゲートは**提供だけ**を止め、
  **受理を前提にした下流（カウンタ・取消・リスト所有）**はそのままだった。契約を**片側だけ**変えると崩れることの実例。
  ＝ **次は 1〜3 を同じ変更単位でまとめて実装し、その後に受入する。**
- **受入（現 HEAD・変更なし）**: AMD/Intel の通常場面は clean/PASSED/VUID 0、通常場面の画は 0.00〜0.01 %。

## 0.85 **Intel 受入（現 HEAD）** ＋ **欠陥はベンダー非依存**と確定

- **Intel・通常場面（現 HEAD、既定構成、120 s）**: exit 0 / **clean** / **PASSED** / **メッセージ 0**、
  **提示画像 vs スクリーンショット = 0.000 / 0.00 %**（完全一致）。＝ **既定経路（相対ビュー修正・adoption）は Intel でも成立**。
- **Intel・entity 場面（同条件）**:
  | | AMD（3 run） | **Intel（今回）** |
  |---|---|---|
  | 差（>8 が異なる） | 1.25 / 1.43 / 1.61 % | **1.33 %** |
  | 差の領域の暗さ | 平均 48.7 暗い | **平均 59.8 暗い** |
  | bbox | x=0..1101, y=30..719 | x=0..961, y=35..719 |

  ＝ **同じ欠陥が Intel でも同水準で再現**。**ベンダー非依存のコード欠陥**であり、ドライバ差ではない。
- **意味**: §0.84 の診断（**表示リストのコンパイル中にアップロードされた頂点を即時経路がその場で描いている**）が
  **両GPUで裏づけられた**。完成条件 (d) の「AMD/Intel で必要な場面を確認」に、**この欠陥の両GPU再現**を追加する。
- **次の一手（1点・読みのみ）**: §0.84 のゲートが **8.91 % に悪化した理由**を特定する。
  `accepted=false` が下流で**何を止めているか**（GL 本体の実行・カウンタ・取消の連鎖）を読み、
  「止めると壊れる理由」を理解してから**再生時所有**の実装に入る（推測で再度ゲートしない）。

## 0.84 **余分な四角の正体を画像で確定**（表示リストのコンパイル中ジオメトリ）／単純なゲートは棄却（8.91 %）

- **画像（`.celtium-work/mob-compare.png`、既定構成・entity 場面・2倍拡大）**:
  - 左（Vulkan 提示）: **空の上に濃い灰色〜黒の四角**、**地形の上に淡い紫の半透明の四角**（＝**GL が描いていない余分な四角**）。
  - 右（GL のスクリーンショット）: **地形と空だけ**で、四角は無い。
- **位置ずれではない**: モブ窓（x0..400, y360..720）で **±6 画素の平行移動を総当たり**しても最良は **dx=0 dy=0**（3.760）。
  ＝ **形は同じで「有る／無い」が違う** ＝ **欠落／重複**。
- **差の色**: 提示=(106.2,99.5,105.5) / GL=(144.0,155.6,157.8) ＝ GL 側は**空の色**で、提示側にだけ**物体**が乗っている。
- **カウンタ**: 既定構成は `modelDraws=0` `displayListCancelled=0` ＝ **取り消しではなく**、
  **表示リストのコンパイル中にアップロードされた頂点が即時経路へ提供され、その場で描かれている**。
  GL はそれを**リストに保存して後で再生**するので、**位置が合わないまま画面に出る**。
- **試した修正（棄却・巻き戻し済み）**: コンパイル中は提供しない（`celtium$listIndex == 0` を条件に追加）。
  **結果は大幅悪化**（1.25 % → **8.91 %**）。＝ 即時経路がリスト内ジオメトリを引き受けるのは**意図された契約**で、
  **単に止めると絵が崩れる**（GL 側の描画が別経路と二重になる）。
- **正しい方向（次の一手）**: **捨てるのではなく、再生時に描く**。リストのジオメトリは
  **`glCallList` の時点の行列**で描かれるべきなので、コンパイル中に受け取ったバッチは
  **リスト id に紐づけて保持し、再生（`celtium$drawModelList`）でその時の行列と一緒に描く**。
- **受入**: 巻き戻し後も既定 run は exit 0 / clean / PASSED / VUID 0（ビルド緑・差分なしを確認）。

## 0.83 フレーム差も粒子も**棄却**／残差は「実体が居る場面」で **1.3 ± 0.2 %** のばらつき

- **計器（新規・再利用可）**: `-Dceltium.validation.dumpPresentedTick=<n>`（既定 198）。
  提示画像は**要求の次の present で消費**されるため、要求 tick がどのフレームを写すかを選べる。
- **実測（entity 場面、同一構成、提示画像 vs 同一 run のスクリーンショット）**:

| 構成 | 平均絶対差 | >8 が異なる |
|---|---|---|
| 書き出し tick 198（従来） | 0.676 | **1.25 %** |
| 書き出し tick 199（捕捉に1フレーム近い） | 0.915 | **1.61 %** |
| **粒子なし**（`celtium.validation.particles=false`） | 1.037 | **1.43 %** |
| 通常場面（実体なし） | 0.011 | **0.01 %** |

- **棄却**:
  1. **フレーム差**（提示が前フレーム）：捕捉に**近づけても改善しない**（1.25 % → 1.61 %）。
  2. **粒子**：粒子を止めても**ほぼ同じ**（1.43 %）。
- **確定した性格**: 残差は **「実体（モブ）が居る場面」でだけ出る 1.3 ± 0.2 %**。
  同じ構成の3 run で 1.25 / 1.43 / 1.61 % とばらつくので、**単一の数字として断定しない**（今後は範囲で記す）。
- **まだ説明できていないこと**: モデル描画は **off**（実体は GL が描く）で、**GL 側スクリーンショット同士は 0.01 %**
  なのに、**同一 run 内の提示画像とスクリーンショットが 1.3 % 違う**。両者は同じ世界フレームバッファを読むはずで、
  この差の出所は**未確定**（次の焦点）。
- **次の一手（対象を1つ）**: **モブ領域だけ**を切り出し、提示画像とスクリーンショットで
  **同じ画素がどう違うか**（色・形・重複）を見る。差が**左に偏り、暗い**ことは分かっているので、
  「実体の描画が二重/欠落/暗い」のどれかに絞れる。
- **受入**: 全 run とも exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.82 光座標の生値を計測（**0x0000**）／「無効」と解釈した修正は**逆効果**で巻き戻し／残る説明は**提示が前フレーム**であること

- **計器（format 5 のバッチの生の光座標）**:
  ```
  CELTIUM_LIGHTCOORD fmt=5 stride=28 count=172 first=0x0(0) second=0x0(0) last=0x0(0) u_first=0.0
  ```
  ＝ 先頭4件の format 5 バッチは**光座標がすべて 0**。シェーダはこれを `(0+8)/256`＝**最暗テクセル**として扱う。
- **試した修正と結果（巻き戻し済み）**: 「0 はライトマップ無効の意味」と解釈し、粒子のバーテックスで `-1` を印として
  フラグメントで `vec4(1.0)`（無照明）にした。
  **結果は悪化**（1.25 % → **1.50 %**、差の領域は **71.4 暗い**）。
  ＝ **`0` は「無効」ではなく正当な最暗値**であり、**GL も同じ値で掛けている**。**解釈が誤りだったので巻き戻した**。
- **より有力な説明（数値と整合する）**: **提示は「前のフレーム」を出す**（設計として記録済み）。
  スクリーンショットは**その時点のフレーム**なので、
  **粒子・雲など動くものが写る場面では 1 フレーム分ずれる**。
  - **静止場面（実体なし）**: 0.011 / **0.01 %**。
  - **動く粒子のある entity 場面**: 0.676 / **1.25 %**（差の領域は**動いている粒子の位置**）。
  ＝ **欠陥ではなく、present の設計（前フレーム提示）と同一フレーム比較の食い違い**である可能性が高い。
- **次の一手（1点・run 不要の確認も可）**: 提示画像を **tick N+1 で取得**し、tick N のスクリーンショットと比べる経路を作れば
  「フレーム差」であることが直接示せる。fixture 側の小変更で足りる。
- **受入**: 巻き戻し後も既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.81 シェーダ経路も棄却（両者は同一で修正済み・SPIR-V も最新）／残る候補は**ライトマップ画像**と**光座標**

- **粒子とブロックのシェーダを全文比較**:
  - `immediate_particle.vert` と `immediate_block.vert` は**ライトマップの式が完全に同一**:
    `v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0;`
  - 両方に**同じ修正コメント**が残っている:
    「**以前の `>> 8` は 0..240 の範囲をゼロへ潰し、全サンプルが同じ暗いテクセルに落ちていた**」
    ＝ **今回観測した「平均 48.7 暗い」とまさに同じ症状**だが、**それは既に修正済み**である。
  - フラグメントも両者とも `texture(u_Lightmap, v_Lightmap)` で同じ。
- **SPIR-V の鮮度**: ソース（`src/main/shaders`）は **10-09 22:40**、生成 SPIR-V は **10-10 03:09** ＝ **新しい**。
  jar にも同じ SPIR-V が入っている（サイズ一致）。＝ **古いバイナリという説明も棄却**。
- **ここまでで棄却した説明（すべて数値・コード根拠つき）**: adoption／即時経路全体／即時群の行列／
  format 6／粒子の属性順（stride 28 の取り違え）／シェーダ式／SPIR-V の鮮度。
- **残る候補（次の1点）**:
  1. **ライトマップ画像そのもの**（ミラーの内容・鮮度・sampler）。粒子のバッチが**古い/別の**ライトマップを見ている。
  2. **光座標の値**（format 5 の頂点に入っている `a_LightCoord` が GL 側と同じか）。
- **次の一手**: format 5 のバッチで **`a_LightCoord` の生値**と**束縛中のライトマップの内容**を1回だけ記録し、
  「式は正しいが入力が違う」のか「入力は同じで画像が違う」のかを分ける。
- **受入**: 変更は文書と読解のみ。既定 run は exit 0 / clean / PASSED / VUID 0 のまま。

## 0.80 format 5 の**レイアウトは正しい**（棄却）／残る焦点は粒子の**フラグメント**のライトマップ

- **確認（両パイプラインの属性記述を並べた）**:

| | BLOCK（format 3） | PARTICLE（format 5） |
|---|---|---|
| loc0 | vec3 位置 @0 | vec3 位置 @0 |
| loc1 | **R8G8B8A8 色 @12** | **R32G32 UV @12** |
| loc2 | **R32G32 UV @16** | **R8G8B8A8 色 @20** |
| loc3 | R16G16 ライトマップ @24 | R16G16 ライトマップ @24 |

  `SpirvShaders` の注記どおり**順序が違う**（`position, texture, colour, lightmap`）が、
  **粒子側も自分の順序を正しく記述している**。stride はどちらも 28。
  ＝ **「サイズが同じで順序が違う」という仮説は棄却**（実装は既に正しい）。
- **残る焦点**: **粒子のフラグメントモジュール**（`SpirvShaders.IMMEDIATE_PARTICLE_FRAGMENT`）の**ライトマップの使い方**。
  実測は「平均 48.7 暗い」なので、**(a) 0..255 の正規化漏れ**（最暗端に張り付く）、
  **(b) ライトマップの束縛がブロック用のまま**、(c) 色×ライトマップの順序、のいずれか。
- **次の一手（1点・run 不要）**: **粒子とブロックのフラグメントモジュールのライトマップ行を並べて読む**。
- **受入**: この節の変更は計器と記録のみ（描画経路の変更なし）。既定 run は exit 0 / clean / PASSED / VUID 0 のまま。

## 0.79 差の**性質**を確定: 提示画像は差の領域で **平均 48.7 暗い** ＝ 幾何ではなく**ライトマップ**

- **計測（既定構成・entity 場面・同一 run 内）**:
  ```
  差のある画素 11,527
  その領域の平均色: 提示画像=(106.2, 99.5, 105.5)  GL=(144.0, 155.6, 157.8)
                    → 提示画像は平均 48.7 暗い
  ```
- **読み**: 差は**位置ずれでも欠落でもなく「暗さ」**。＝ **ライトマップ**（format 5 の native 描画）が原因。
  「暗さ」は §0.11/§0.20 で扱った地形の暗さと**同じ症状**で、**format 5（粒子系）でも同じ族の欠陥が残っている**ことになる。
- **format 5 の位置づけ**: §0.43 でライトマップの**遅延供給**を入れた族であり、**ライトマップ UV・束縛・ミラーの鮮度**が候補。
- **次の一手（1点）**: format 5 の**ライトマップ座標の出所**を読む（頂点属性か、ミラーか、固定値か）。
  `48.7 暗い` は「ライトマップ UV が最暗を指している」典型で、**UV の取り違え/欠落**が最有力。
- **受入**: 既定構成の run は exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.78 差の出所を **format 単位で特定**: **format 5 の native 描画**が 1.25 % の大半（除外で 0.32 % へ）

- **方法（既存の帰属スイッチ）**: `-Dceltium.vulkan.immediateFormats=<数字の並び>` で受理する format を絞り、
  **entity 場面**で提示画像とスクリーンショットの差を測る。

| 構成 | 提示画像 vs 同一 run のスクリーンショット |
|---|---|
| **format 5 を除外**（`12346789`） | **0.273 / 0.32 %**（**改善**） |
| 既定（全部受理 `123456789`） | 0.676 / 1.25 % |
| format 6 を除外（`12345789`） | 1.149 / 1.53 %（**悪化**） |

- **読み**:
  - **format 5（粒子系）を GL に返すと差が 1.25 % → 0.32 %**。＝ **format 5 の native 描画が約 0.9 % を生んでいる**。
  - **format 6 を返すと悪化**（1.53 %）＝ **format 6 の native 描画は正しく、外すと絵が崩れる**。
  - 残る 0.32 % が床（その他の native 経路＋提示の差）。
- **意味**: **entity 場面の差は「どの format か」まで落ちた**。format 5 は**ライトマップを使う**族なので、
  §0.43 の遅延供給・ライトマップ束縛・UV のいずれかが疑わしい（**次の1点**）。
- **受入**: 両 run とも exit 0 / **PASSED**（`clean`・VUID 0 は既定回と同様）。
- **次の一手**: **format 5 の native 描画と GL 描画を、同じバッチで画素単位に比べる**（ライトマップ/UV/ブレンドのどれか）。
  既存の `CELTIUM_GROUPS`／`CELTIUM_GROUP_DEPTH` 系の計器に **format 5 のライトマップ束縛**を足すのが最短。

## 0.77 差の所在が確定: **通常場面と Intel は完全一致（0.01 % / 0.00 %）／entity 場面の AMD だけ 1.25 %**

- **同一 run 内での比較（提示画像 vs その run 自身のスクリーンショット ＝ GL が描いた絵）**:

| run | 提示画像 vs 同一 run のスクリーンショット |
|---|---|
| **既定（モデル描画 off・adoption on、entity 場面）** | **0.676 / 1.25 %** |
| モデル描画 on（`V_now × C_compile⁻¹`、entity 場面） | 3.137 / 2.19 % |
| **通常場面（実体なし）** | **0.011 / 0.01 %** |
| **Intel 既定（通常場面）** | **0.002 / 0.00 %** |

- **GL 側スクリーンショット同士の比較（entity 場面、3構成）**: すべて **0.01 %** ＝ **場面は決定的**。
- **棄却した出所（数値つき）**:
  - **adoption**: `-Dceltium.vulkan.adoptGameFramebuffer=false` で **1.60 %**（on の 1.25 % より**悪い**）。
  - **即時経路**: `-Dceltium.vulkan.immediate=false` で **8.14 %**（切ると大幅に悪化＝即時経路は正しく描いている）。
  - **即時群の行列**: 最初の8群を実測。HUD 相は **直交射影＋恒等モデルビュー**（`worldPass=false`）、
    ワールド相は **透視＋ワールド行列**（`worldPass=true`）で、**どちらも正しい形**。
- **確定した到達点**:
  - **通常場面（実体なし）**: 提示画像は GL の絵と **0.01 %**（AMD）／**0.00 %**（Intel）で一致。
  - **entity 場面（AMD）**: **1.25 %**（モデル描画 off）／**2.19 %**（on）。**entity 場面固有**で、残る唯一の既知の差。
- **次の一手（対象は1つ）**: entity 場面で **native に受理されている format を1つずつ外し**（`celtium.vulkan.immediateFormats`）、
  どの format の native 描画が 1.25 % を生むかを特定する。即時経路は「切ると悪化する」＝**大半は正しく描いている**ので、
  **特定の format/群**が疑わしい。

## 0.76 残差の床（1.25 %）は **entity 場面固有**と判定／即時経路の行列は提供時点の GL 行列で正しい

- **画像（既定＝モデル描画 off の entity 場面 vs GL、`.celtium-work/floor-compare.png`）**:
  左（既定）の**左下に暗い四角**（無テクスチャに見える）＋左上の地形に**淡い色かぶり**。右（GL）は無い。
- **差の局所化**: 1.25 %（11,520 画素）。**列バンドで左に偏る**（6 / 3 / 0 / 0 / 0 / 0 / 0 / 0 %）、行は 1〜2 % ずつ。
- **比較（既存の測定）**: **通常場面（実体なし）の既定 vs GL は 0.072 % / 0.05 %**。
  ＝ **床 1.25 % は entity 場面固有**（＝モブに関わるバッチ）である。
- **即時経路の行列（コード読み）**: 即時群の行列は **`glProjection` / `glModelView`**（＝**そのバッチを提供した時点の GL 行列**）
  から取られている（`currentGroup*` へ `arraycopy`）。＝ **行列の出所は正しい**（モデル経路の行列問題とは別）。
- **計器（即時群の種別を名乗る）**: `CELTIUM_IMMEDIATE_ENQ ... fmt=5 texture=29 mirrored=true batches=1 worldPass=true`
  ＝ 最初の群は**正常**（テクスチャはミラー済み・world pass 中）。**暗い四角を描いているのは一部の群**で、
  **最初の群ではない**。
- **次の一手（対象を絞る）**: 即時群の計器を**全群**に広げ（先頭 N 件）、`fmt / texture / mirrored / worldPass / mvT` を出して、
  **暗い四角の位置に対応する群**を特定する。テクスチャ未ミラーのまま白で描かれている疑いが最有力。
- **受入（AMD、既定、`keepEntities`、90 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.75 「リストごと1行列」の近似は原因ではない（計器で棄却）／残差 2.19 % の性格

- **疑い**: キャプチャは**リストごとに1つの行列**（最初のクワッドのもの）しか保存しないため、
  リスト内でパーツごとに変換が変わると誤差になる。
- **計器（リスト内で最初のクワッドと行列が違うクワッドを数える）**:
  ```
  CELTIUM_MODEL_BOUNDS list 2 vertices=24 ... quadsWithOtherMatrix=0/12
  ```
  ＝ **0件**。**この近似は残差の原因ではない**（1回の run で棄却）。
- **残差 2.19 % の内訳（測定済みの床との比較）**:
  - adoption 自体の床（モデル描画 off）: **1.25 %**（0.676）
  - GL 同士の対照: 0.01 %（0.029）
  → **モデル経路固有の残差は約 1 %**。
- **現時点の到達点**: モデル描画の差は **18.49 % → 2.19 %**（`V_now × C_compile⁻¹`）。
  **既定はモデル描画 off**（§0.64）なので、**既定の絵は依然として正しい**。
- **次の一手（候補を絞る）**: 残差の画像は**左側に寄った暗い四角**で、位置は概ね合っている。
  候補は (a) **クワッドの分割対角**（メッシュは `(0,1,2)(0,2,3)`、GL のクワッドも同じなので可能性は低い）、
  (b) **UV・法線の扱い**、(c) **深度の丸め**。次は**1クワッドだけを native で描いた画像**と GL の同領域を
  画素単位で見て、**(b) か (c) か**を決める。

## 0.74 **正しい変換を特定**: `V_now × C_compile⁻¹`（5.80 % → **2.19 %**）

- **理論**: 表示リストは**頂点を変換済みで保存する**（プロジェクト自身の注記）。ならばキャプチャ頂点は
  **コンパイル時のビュー空間**にあり、再生時に必要なのは**その後のカメラの変化分** ＝ **`V_now × C_compile⁻¹`**。
  両方の半分は手元にある（`V_now` は行列ミラー、`C_compile` はキャプチャが保存していた行列）。
- **実測（entity 場面、`modelDraw=true`、同一条件、7通り）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **`V_now × C_compile⁻¹`（今回）** | **2.19 %** | **3.136** |
| ミラー（カメラ回転のみ） | 5.80 % | 8.485 |
| 恒等行列 | 8.08 % | 14.744 |
| キャプチャ時行列 `C_compile` | 13.19 % | 19.185 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| ミラーの回転＋ライブの平行移動 | 18.40 % | 31.484 |
| 射影も `glGetFloatv` | 18.49 % | 31.591 |

  **5.80 % → 2.19 %**。**理論から導いた1点が、7通りの中で最良**。
- **受入**: exit 0 / **clean** / **PASSED** / **VUID 0**、`modelDraws=15426`。
  参考: モデル描画 off は 1.25 %（0.676）、GL 同士は 0.01 %（0.029）＝ **残差 2.19 % はまだ baseline より大きい**。
- **意味**: **モデル経路の主要な欠陥が取れた**。残差は **1.25 % の床**（adoption 自体の差）＋ **約 1 %** のモデル経路固有分。
- **次の一手**: 残差の画像を切り出して性質を見る（位置ずれ・欠落・色のどれか）。深度・UV・法線のいずれかが候補。

## 0.73 頂点の空間を計測 → **モデル空間**（±0.6）と確定／それでも6通りのどれも正解ではない

- **計器（キャプチャ頂点の座標範囲＋キャプチャ時行列の平行移動）**:
  ```
  CELTIUM_MODEL_BOUNDS list 2 vertices=24 x=-0.125..0.063 y=-0.125..0.625 z=-0.125..0.125
                       compileT=(0.347,-1.706,-0.74)
  ```
  ＝ 頂点は **モデル空間**（±0.6 ＝ モデルのパーツ）で、キャプチャ時の行列の平行移動は
  **その時点のエンティティ位置**（`0.347,-1.706,-0.74`）。**キャプチャは対になる行列も保存している**（未使用だった）。
- **モデルビューの6通りを実測（entity 場面、`modelDraw=true`、同一条件）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **ミラー（再生時・カメラ回転のみ・平行移動ゼロ）** | **5.80 %** | **8.485** |
| 恒等行列 | 8.08 % | 14.744 |
| **キャプチャ時行列（保存済み・今回試した）** | **13.19 %** | 19.185 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| ミラーの回転＋ライブの平行移動（合成） | **18.40 %** | 31.484 |
| 射影も `glGetFloatv` | 18.49 % | 31.591 |

- **読み（重要）**: **平行移動を入れる全ての組み合わせが悪化する**（18.30 / 18.40 / 18.49）。
  ＝ **頂点は既に配置済み**である可能性が高く、**「モデル空間の頂点 × 再生時行列」という素直な説明は成り立たない**。
  ただし**恒等（8.08）もミラー（5.80）に劣る**ので、**6通りのどれも正解ではない**。
  ＝ **キャプチャしたデータそのものを次に見る必要がある**（例: **コンパイル時と再生時の両方のクワッドを取り込んでいないか**、
  その場合メッシュに**異なる空間の頂点が混在**する）。
- **対応**: 合成の実験は**巻き戻し**、最良のミラーへ復帰（**不採用の分岐は残していない**）。ビルド緑。
- **受入**: 復帰後も exit 0 / clean / PASSED / VUID 0（`modelDraws=14928`）。
- **次の一手**: キャプチャの**取り込み回数と出所**（コンパイル時のみ／再生時も）を数える計器を入れ、
  **メッシュ内に異なる空間の頂点が混在していないか**を確かめる。

## 0.72 モデルビューの4通りを実測（ミラーが最良）／**頂点の空間**が次の1点

- **実測の一覧（entity 場面、`modelDraw=true`、同一条件）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **ミラー（カメラ回転のみ・平行移動ゼロ）** | **5.80 %** | **8.485** |
| 恒等行列 | 8.08 % | 14.744 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| （射影も `glGetFloatv`＝直交射影） | 18.49 % | 31.591 |

  ＝ **ミラーのカメラ回転は必要**（恒等にすると悪化）。**ライブはさらに悪い**。**最良はミラー**。
- **記録した矛盾（そのまま残す）**: キャプチャの最初の頂点は `v0=(0.063,-0.125,…)` ＝ **モデル空間の小さい値**なのに、
  **エンティティの平行移動を持つライブ行列**を使うと**悪化**する。＝ 単純な「モデル空間の頂点 × 再生時行列」では説明できない。
  **頂点がどの空間で保存されているか**が確定していないことが、残差 5.80 % の正体である。
- **対応**: 恒等行列の実験は**巻き戻し**、最良のミラーへ復帰（**不採用の分岐は残していない**）。
- **受入（復帰後、AMD、entity 場面、`modelDraw=true`、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `modelDraws=15032`、**8.08 % → 5.80 % へ復帰**（恒等実験の値 8.08 % と対比）。
- **次の計器（1点・決定的）**: **キャプチャした頂点の座標範囲**を、**そのリストのキャプチャ時行列の平行移動**と並べて出す。
  頂点が `0.06〜1` なら**モデル空間**、`数十〜数百` なら**ワールド空間**で、**どちらかで行列の掛け方が一意に決まる**。

## 0.71 行列の出所を**計器で確定**（ミラー×ミラーが最良）／残差はキャプチャ側に帰着

- **計器（enqueue 時に射影の種類・モデルビューの回転行・平行移動・束縛テクスチャを8件まで）**:
  ```
  CELTIUM_MODEL_ENQ texture=25 projKind=perspective proj11=-1.0 proj10=-1.0 proj14=-0.05
                    mvT=0.0,0.0,0.0 mvRow0=-0.333,-0.683,-0.65 mvScale=1.0
  ```
  ＝ **射影はミラー経由で透視になっている**（修正が効いている）。**モデルビューの平行移動はゼロ**（＝カメラのみ）。
- **3通りの組み合わせを実測（entity 場面、`modelDraw=true`）**:

| 射影の出所 | モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|---|
| `glGetFloatv`（直交射影を拾う） | `glGetFloatv` | 18.49 % | 31.591 |
| **ミラー** | **ミラー** | **6.35 % → 5.80 %**（再測） | 9.064 → **8.485** |
| ミラー | `glGetFloatv`（ライブ） | **18.30 %**（悪化） | 31.429 |

- **確定した読み**: **モデルビューはライブでもエンティティのものではない**（ライブに差し替えると 6.35 % → 18.30 % に悪化）。
  ＝ 「表示リストは**変換済みの頂点**を保存する」というコメントと整合し、**パーツは自分の位置を頂点側に持っている**ので、
  行列で**動かしてはいけない**。**最良はミラー×ミラー**で、**残差はキャプチャ側の対応（頂点と行列の組）**にある。
- **対応**: 悪化した組み合わせは**巻き戻し**、ミラー×ミラーへ復帰（再測で同水準を確認）。**不採用の分岐は残していない**。
- **受入（AMD、entity 場面、`modelDraw=true`、120 s、復帰後）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `modelDraws=14848`、**5.80 % / 8.485**（参考: モデル描画 off は 1.25 % / 0.676、GL 同士は 0.01 % / 0.029）。
- **次の一手（キャプチャ側・1点）**: キャプチャは**コンパイル時の行列も保存している**（現在は要約表示にのみ使用）。
  **頂点は保存時点で変換済み**という前提に立つなら、**コンパイル時の行列をどう打ち消すか**が残差の正体である。
  次はその1点（保存行列の使い方）を試す。

## 0.70 残差 6.35 % の性質（画像）: **まだ画面空間に散る暗い四角**

- **画像（`.celtium-work/mirror-compare.png`、左＝ミラー修正後・右＝GL）**: 左に**暗い四角が地形と空の両方に散在**（前回と同種）、
  右は地形のみ。切り出し領域で **mean 8.25 / >8 が 4.68 %**。
- **読み**: ミラー修正で 18.49 % → 6.35 % に減ったが、**性質は変わっていない**（画面空間に散る・暗い＝無テクスチャに見える）。
  ＝ **`callList` の時点でミラーが直交射影を保持している場面がある**（HUD/GUI 相のモデル描画）か、
  **テクスチャ束縛が別**（暗い四角＝白/無テクスチャで描かれている）かの**2候補**。
- **次の計器（2点だけ）**: (a) モデルビューの**回転・スケール行**（平行移動だけでなく）を enqueue 時に出す、
  (b) **束縛テクスチャ id** を同じ行に出す。この2つで「射影の取り違えが残っている」か「テクスチャが違う」かが決まる。
- **状態**: **既定はモデル描画 off**（§0.64）なので、**既定の絵は正しい**。この調査は既定へ戻すための前提を詰めている。

## 0.69 **原因を特定して修正**: モデル経路が `glGetFloatv` で**直交射影（HUD 用）**を拾っていた（18.49 % → 6.35 %）

- **計器（enqueue 時点の行列をモデル群と即時群で並べる）**:
  ```
  CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,-1.0,-0.05   mvT=0.0,-1.62,0.05
  CELTIUM_MODEL_ENQ     proj=0.0,0.0,-1.0,-0.05   mvT=0.514,-0.804,-0.689
  ```
  ＝ **モデル群に渡っている射影が直交射影**（`m23=-0.05`）で、**地形の透視射影（`0,0,-1.001,-1`）と違う**。
  ＝ `glGetFloatv(GL_PROJECTION_MATRIX)` が**その瞬間の正しい値ではない**（MC の行列ミラーが未反映）。
  **画像の「画面空間に散る四角」と完全に一致**する説明である。
- **修正**: モデル経路の行列を **`glGetFloatv` ではなくプロジェクト自身の行列ミラー**
  （`immediateState.matrix(MATRIX_MODE_PROJECTION/MODELVIEW)` ＝ 即時経路と同じ出所）から取る。
- **受入（AMD、entity 場面、`modelDraw=true`、120 s）**:

| 構成 | entity 場面 vs GL 経路（>8 が異なる） | 平均絶対差 |
|---|---|---|
| 修正前（`glGetFloatv`） | 18.49 % | 31.591 |
| **修正後（ミラー）** | **6.35 %** | **9.064** |
| 参考: モデル描画 off | 1.25 % | 0.676 |
| 参考: GL 同士（対照） | 0.01 % | 0.029 |

  `modelDraws=14726 / displayListCancelled=14726`、exit 0 / **clean** / **PASSED** / **VUID 0**。
- **意味**: **欠陥の主要因が1つ取れた**（画面空間に散っていた理由が射影の取り違えだった）。**残差 6.35 % は未解決**で、
  モデル描画 off の 1.25 % より大きい。
- **次の一手**: 残差 6.35 % の画像を切り出して**どこがどう違うか**を見る（位置ずれか、欠落か、色か）。同じ計器（enqueue の行列）は
  **モデルビューの出所**も疑う材料になる（`mvT` は妥当に見えるが、回転・スケールは未確認）。

## 0.68 **描画順は正しい**（地形パスが先）／投影の深度行は群をまたいで一致 — 残る焦点はモデル群の行

- **計器（順序）**:
  ```
  CELTIUM_WINDOW_ORDER frame=10 lastTerrainPassFrame=10 terrainRanFirst=true lastUiPass=translucent groups=3
  ```
  ＝ **同一フレームで地形パスが先に走っている**。深度バッファは埋まっているので、
  **原因は描画順ではない**と一意に決まった（候補は「深度値」側に絞られた）。
- **計器（投影の深度行）**:
  ```
  CELTIUM_GROUP_DEPTH row1=0.0,0.0,-1.001,-1.0 mvT=0.0,-1.62,0.05
  CELTIUM_GROUP_DEPTH row2=0.0,0.0,-1.001,-1.0 mvT=0.0,-1.522,-0.505
  ```
  ＝ 投影の深度行は**2群で完全に一致**（`m20..m23 = 0,0,-1.001,-1` ＝ GL 規約のまま、`vulkanProjection` は Y 規約のみ変換）。
  **投影は原因ではない**。
- **判明した次の焦点**: この2行は**どちらもモデル群ではない**。計器は「最初の2つの異なる行」しか出さない設計で、
  モデル群の `modelViewNow`（平行移動 `x=0.514`）と**一致しない**（2行とも `x=0.0`）。
  ＝ **モデル群の行はまだ見えていない**ので、**行に種類（model/immediate）を付けて出す**のが次の1点。
- **途中の失敗（記録）**: ラベル付けを `group[13] == null` で書いたが、その位置に `group` が無く**ビルドが壊れた**。
  **コンパイルできる状態へ戻してから**コミットした（推測で通さない）。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / clean / PASSED / VUID 0。
- **既定の状態は変わらず正しい**（モデル描画 off・§0.64）。

## 0.67 モデル描画の描画先を確認（深度つきのパス）／次は**描画順**を1点だけ測る

- **確認（コード読み）**: モデルメッシュは `celtium$enqueueModelMesh` で **`this.immediateGroups` に載る**（即時経路と同じリスト）。
  そのリストを描く `flushImmediateWindow` は **`pipelineFor(this.lastUiPass)`** を使い、
  `pipeline == null` なら「accepted geometry has no render pass」で落ちる ＝ **地形パス用のパイプライン**で、
  **深度はパス側にある**（状態キー側も `depthTest=1 / depthWrite=1` を確認済み）。
- **したがって残る候補は2つに絞られた**:
  1. **合成行列の深度行**（射影×モデルビューの z 成分が GL と違う）
  2. **描画順**（ウィンドウが地形より先に描かれ、深度バッファがまだ空）
- **次の計器（1つ・決定的）**: **フレーム内の描画順**を一度だけ記録する
  （地形パスの記録と `flushImmediateWindow` の呼び出しの**前後関係**、および各ウィンドウの**深度アタッチメントの有無**）。
  順序が正しければ深度行、逆なら順序の問題と一意に決まる。
- **状態の整理（重要）**: **既定ではモデル描画 off**（§0.64）なので、**ユーザーに見える既定の絵は正しい**。
  この調査は「モデル経路を既定に戻す」ための前提を詰めている段階である。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / clean / PASSED / VUID 0。

## 0.66 モデル描画: 深度状態は正しい（計器で確認）／2つの仮説を数値で棄却

- **計器1（状態）**: モデル描画が渡される状態を一度だけ出す。
  ```
  CELTIUM_MODEL_STATE list=2 depthTest=1 depthWrite=1 cull=0 blend=1 glDepthTest=true glDepthMask=true key=166141458652
  ```
  ＝ **深度テストも深度書き込みも有効**で、ライブ GL 状態とも一致。**状態は原因ではない**。
- **計器2（行列）**: ライブ行列とキャプチャ時（コンパイル時）行列の平行移動を並べる。
  ```
  CELTIUM_MODEL_MATRIX list=2 liveMVt=(0.514,-0.804,-0.689) capturedMVt=(0.347,-1.706,-0.74) liveProj=(0.803,1.428,-1.0)
  ```
  `liveProj[10] = -1.0` ＝ **GL の深度規約**（Vulkan の 0..1 ではない）。
- **棄却した仮説（数値つき）**:
  1. **「ライブ射影に二重リマップ」**: `-Dceltium.vulkan.modelRemapDepth=false` で素通しにすると差は
     **4.56 % → 6.01 %** に**悪化**。＝ リマップは必要で、**フラグは撤去した**（不要な分岐を残さない）。
  2. **「グループ描画側でも変換している」**: グループは `vulkanProjection(...)` を通すが、これは
     **Y 規約**のための変換（コード自身の注記「Positive height: … why the Vulkan convention is used unchanged」）で
     **深度の二重変換ではない**。仮説1と整合する。
- **確定したこと**: モデル描画の**状態**（深度・ブレンド・カリング）と**深度リマップの有無**は原因ではない。
  残るのは**描画結果の深度値そのもの**（射影×モデルビューの合成）か、**描画順**。
- **次の計器（具体・1つ）**: モデル経路の**最終的な合成行列**（射影変換後）を、**同じ瞬間の即時経路の合成行列**と
  並べて出す。両者が一致していれば深度値ではなく**順序**の問題、違えば**行列の合成**の問題と一意に決まる。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.65 モデル描画の欠陥を**画像で名指し**: クワッドが深度で遮蔽されずに描かれている

- **切り分け（コードが用意していた実験を使った）**: `-Dceltium.vulkan.modelDraw=true -Dceltium.vulkan.modelQuadLimit=1`
  ＝ **各リスト1クワッドだけ**を native で描く。差は **4.56 %**（全クワッド 18.49 %、モデル描画 off 1.25 %）。
  ＝ **1クワッドでも既に見える** ＝ 原因は**多数のクワッドの蓄積ではなく、メッシュ／描画経路そのもの**。
- **画像（左＝1クワッド native、右＝GL のみ、`.celtium-work/onequad-compare.png`）**:
  **左だけに暗赤色の四角が地形と空の両方に散在**している。右は地形のみで正しい。
  ＝ **クワッドが深度で遮蔽されていない**（地形の上にも空の上にも同じように出る）。
  全クワッド版の「左半分を覆う巨大な黒い形状」も、**遮蔽されない多数のパーツが重なったもの**として説明がつく。
- **キャプチャ側は正しい（確認済み）**: 保存頂点は `v0=(0.063,-0.125,…)` の**モデル空間の小さい値**で、
  23 floats/quad は「位置12＋UV8＋面法線3」＝**キャプチャ自身の格納形式**（不一致ではない）。
  メッシュは `Layout(20, 0, 12, …)` ＝ stride 20（位置3＋UV2）で、**キャプチャの `modelView` は要約表示にしか使われていない**
  （＝焼き込みによる二重変換ではない）。描画は `glGetFloatv` の**再生時行列**を使っている。
- **次の一手（具体）**: モデル描画の**状態キー**を、同じクワッドを即時経路で描いた場合と突き合わせる。
  特に **depth test / depth write / cull**（pass 自体は共有深度を持つ window の pass なので、疑うのは状態側）。
- **測定の位置づけ**: 差は **PASSED のまま**（verdict は捉えない）。**画像比較だけが見る**。

## 0.64 **モデル描画を既定 off に戻した**（既定ビルドの忠実度 18.49 % → 1.25 %）＋ 原因の隔離

- **画像を見て確定（教訓どおり画像を先に見た）**: 差の領域を切り出して並べると、**native 側だけが左半分を覆う巨大な黒い形状**
  （地形の線が一点に収束）を描いていた ＝ **モデルの変換／ジオメトリが壊れている**。GL 側は地形のみで正しい。
- **隔離（`-Dceltium.vulkan.modelDrawOnlyList=2`、既存スイッチ）**:

| 構成 | entity 場面 vs GL 経路（>8 が異なる） |
|---|---|
| 全リスト native（既定だった） | **18.49 %** |
| **リスト2 のみ native** | **2.10 %**（`modelDraws=811`） |
| **モデル描画 off（今回の既定）** | **1.25 %**（平均絶対差 0.676） |
| 対照: GL 同士 | 0.01 %（場面は決定的） |

  ＝ 欠陥は**表示リストの native 描画**にあり、**リスト2 だけでも 2.10 %** 残る（`displayListOrigins=2` なので 2 と 3 の両方が疑わしい）。
- **対応（既定の是正）**: `celtium.vulkan.modelDraw` の既定を **false** に戻した。
  **コード自身のコメントが「Lab switch: off by default。一致するまで既定を変えない」と書いていた**のに既定が true に
  なっていた（地形だけの比較では**実体を消しているので見えない**ため、その受入がこの欠陥を見落としていた）。
  コメントに**今回の測定値**を書き込み、**0 になるまで off** と明記した。
- **受入（AMD、entity 場面、既定）**: exit 0 / **clean** / **PASSED** / **VUID 0**、`modelDraws=0`、
  **既定 vs GL 経路 = 1.25 %**（変更前 18.49 %）。
- **残る未解決（2件・数値つき）**: (i) 既定に残る **1.25 %**、(ii) リスト2 単独の **2.10 %**。
  どちらも**画像比較だけが見る**（verdict は PASSED のまま）。
- **次の一手**: リスト2 の**キャプチャ頂点の範囲と push constant の行列**を、GL が同じ瞬間に持っている値と突き合わせる
  （`CELTIUM_MODEL_QUAD`／`CELTIUM_MODEL_TEXTURE` の既存計器を使う）。

## 0.63 **entity 場面の対照実験で native モデル描画の実欠陥を確定**（場面は決定的、native だけが 18.49 % 違う）

- **場面を決定的にした**: 出したモブに `setNoAI(true)` と `enablePersistence()` を付け、tick 60 の位置に固定した。
- **対照実験（AMD、同一場面、`keepEntities`、120 s、3 run）**:

| 比較 | 平均絶対差 | >8 が異なる | 最大 |
|---|---|---|---|
| **GL run 1 vs GL run 2（対照）** | **0.029** | **0.01 %** | 219 |
| **native vs GL run 1** | **31.591** | **18.49 %** | 255 |
| **native vs GL run 2** | **31.563** | **18.49 %** | 255 |

  ＝ **場面は決定的**（GL 同士は 0.01 %）なのに、**native モデル経路だけが 18.49 % 異なる**。2回の GL run で同値。
- **差の位置（局所化）**: 差は**左半分に集中**（列バンド 61 % / 61 % / 23 % / 0 / 0 / 0 / 0 / 0、**右半分は 0.0 %**）、
  行は下 2/3（0 / 3 / 14 / 28 / 35 / 27 %）、外接矩形 x=0..1279 y=31..719。**モブが立っている領域**と一致する。
- **確定（重要）**: **`displayListCancelled == modelDraws` はモデル完成ではない**。
  取消は起きているが、**native が描く絵は GL と違う**。計画に書いた「手持ちの取消だけをモデル完成と扱わない」は**正しく、
  この測定で裏づけられた**。この欠陥は**既定の経路**にある（実験フラグではない）。
- **計器**: native run `modelDraws=14862 / displayListCancelled=14862`、GL run `modelDraws=0`（`-Dceltium.vulkan.modelDraw=false`）。
  両方 PASSED・clean・VUID 0（＝**verdict はこの差を捉えない**。画像比較だけが捉える）。
- **次の一手（原因の切り分け）**: (a) **モブ1体の小場面**で差を最小化し、(b) native モデル経路の
  **テクスチャ束縛・行列・UV**（§0.11 の `CELTIUM_MODEL_TEXTURE` / `CELTIUM_MODEL_QUAD` 計器）を
  GL 経路の同じ値と突き合わせる。**画像差の位置がモデル領域に限定されている**ので、切り分けは可能。

## 0.62 **entity 場面を用意し、モデルが既定で native 描画されていることを実測**（modelDraws 14990＝取消 14990）

- **fixture に実体場面を追加**: `-Dceltium.validation.keepEntities=true`。比較用の実体除去を止め、tick 60 に
  **固定の4体**（creeper / zombie / spider / enderman）を明示的に出す（自然スポーンに任せないので毎回同じ構成）。
  受入の証拠は計器（`CELTIUM_ENTITIES_SPAWNED tick=60 nonPlayer=4`）で、**画像比較用ではなく被覆用の場面**である。
- **実測（AMD、既定、entity 場面、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 859、`presentedFromVulkan=848`。

| 計器 | 値 |
|---|---|
| **`modelDraws`** | **14990** |
| **`displayListCancelled`** | **14990**（**全て取消＝GL の描画は抑制**） |
| `displayListOriginCancels` | 10970 |
| `displayListOrigins` / `displayListReplaysPassClosed` | 2 / 15885 |
| origins | `WorldVertexBufferUploader.func_181679_a ← Tessellator`（＝表示リスト経路） |
| `acceptedByFormat` | `5:795 6:5632 7:4988 9:78`（**8 は 0**） |

- **確定（従来の記述の訂正）**: **entity/モデルは即時経路（format 8）ではなく表示リスト経路で描かれ**、
  **既定で native が引き受けている**（`displayListCancelled == modelDraws`）。
  ＝ 計画の「モデル専用native描画は実験フラグ側に残り」は**古い**。**実験フラグではなく既定の経路**である。
- **format 8 が 0 の意味**: entity が「描けない」のではなく、**entity は format 8 では供給されない**（供給は表示リスト側）。
  `CELTIUM_ENTITY_REFUSED`（カスタムプログラム束縛時の拒否）は**この場面でも発火していない**。
- **未確認（次の受入）**: **entity 場面の画像等価**（表示リスト native と GL 描画を同一場面で比較）。次はこの比較を取る。

## 0.61 entity/TESR の現状を確定（**比較場面から実体を除いている**／format 8 はカスタムプログラム束縛で拒否）

- **判明した前提（コードで確認）**: 検証クライアントは**比較のため非プレイヤー実体を意図的に全除去**している
  （`ValidationClient` の「mob spawning is not seed-deterministic」節）。＝ **標準の比較場面にはエンティティが居ない**ので、
  `acceptedByFormat 8:0` は「entity が描けない」ではなく「**entity が供給されていない**」ことを意味する。
- **format 8（entity レイアウト）の拒否条件（コードで確認）**: `boundProgram != 0`
  ＝ **カスタムシェーダプログラムが束縛されている**とき拒否し、`immediateLastRefusal = "a custom shader program is bound"`。
  `CELTIUM_ENTITY_REFUSED program=… texture=…` と `CELTIUM_ENTITY_REFUSED_SITE`（10フレームのスタック）を**一度だけ**出す計器がある。
- **この計器はまだ一度も発火していない**（p322-gui・p344/p355/p357/p359/p360・p361 のいずれにも 0 行）。
  ＝ **これまでの全場面で、カスタムプログラム束縛下の entity 供給が起きていない**。
- **ポストプロセス場面（バニラ ShaderGroup）は今回発火しなかった**: `-Dceltium.validation.postprocess=true` を渡した
  120 s の run で `CELTIUM_POSTPROCESS` が **0 行**、`POSTPROCESS` の痕跡も 0。
  分岐は `worldTicks == 26 && !celtium$postprocess && Boolean.getBoolean("celtium.validation.postprocess")` にあり、
  **`enabled = Boolean.getBoolean("celtium.validation")` が前提**。**発火しなかった理由は未確定**（次の計器は
  ハンドラ入り口で `enabled`・`worldTicks`・プロパティ値を一度だけ出すこと）。
- **受入（AMD、既定、`postprocess`＋`keepGui`、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 850、
  `offers 16000 / accepted 13282 / GL 本体 2745`（＝ポストプロセス無効時とほぼ同じ。**この場面でも steady-state は native**）。
- **意味**: entity/TESR の native 化は **「拒否されている」段階ではなく「場面が無い」段階**である。
  次は **(a) 実体を出す場面**（実体を消さない比較用の小場面）と **(b) ポストプロセスの発火確認**の2つを用意する。

## 0.60 **既定フレームバッファへ描く合成も native 化**（残る最大の GL 群 902 件/run を受理）

- **考え方**: MC の合成は**画面（既定フレームバッファ）へ重ねる**もの。present は「**前のフレーム**を出す」ので、
  フレーム末尾の合成を**共有イメージへ直接描けば**、次の present で正しく画面に出る。＝ 行き先が「画面」でも
  **共有イメージが画面そのもの**である adoption 下では native に行き先がある。
- **実装**: `supportedImmediateTarget()` の `fbo == 0` を、**adoption が有効で共有ターゲットが使えるときは
  その共有 FBO を使う**形に変更（`defaultFramebufferCompositesTaken` で数える）。adoption でないときは従来どおり拒否。
- **受入（AMD、既定＝フラグなし、同一場面）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 905、
  `presentedFromVulkan=893`、`gameFramebufferIsShared=true`、

| 計器 | 変更前 | **変更後** |
|---|---|---|
| **`defaultFramebufferCompositesTaken`** | （拒否 874） | **902** |
| offers / **accepted** / **GL 本体** | 16000 / 13088 / 2939 | 16000 / **13202** / **2825** |
| 参照フレーム vs 提示画像（反転） | 0.072・0.05 % | **0.072・0.05 %**（**見た目の差なし**） |

- **意味**: **steady-state で GL が描く量はほぼ消えた**。残るのは
  **(i) 起動直後10フレームの本質的な拒否**、**(ii) pass 選択の少数**、**(iii) GL context の生成そのもの**。
- **正直な区別**: 「GL に渡した数」がゼロになったわけではない（起動直後と少数の pass 選択は残る）。
  ただしそれらは**native の描画先がまだ存在しない瞬間**のもので、**欠陥ではない**（§0.41/§0.58）。

## 0.59 **adoption を既定にした**（両GPU・フラグなしで PASSED、同一vendor画像等価を確認）

- **既定化の根拠（測定）**:
  - **Intel 同一vendor比較**（基準＝フラグ off の GL 経路、同一場面）: 基準スクリーンショット vs adoption 提示画像
    **0.039・>8 が 0.03 %**、基準提示画像 vs adoption 提示画像 **0.026・0.02 %**。
    （§0.52 の 0.98 % は **AMD フレームとの vendor 間差**だったことがこれで確定した。）
  - **AMD 同一vendor**: 0.001・0.00 %（§0.51）。
- **既定化**: `celtium.vulkan.adoptGameFramebuffer` を **既定 true**（`-D…=false` で旧経路に戻せる）。
- **既定での受入（フラグを一切付けない run）**:

| 計器 | AMD (RX 6400) | Intel (UHD 630) |
|---|---|---|
| exit / `validationClean` / `fixtureStatus` | **0 / true / PASSED** | **0 / true / PASSED** |
| VUID / クラッシュ | **0 / なし** | **0 / なし** |
| frames / `presentedFromVulkan` | 901 / **889** | 374 / **361** |
| `gameFramebufferAdopted` | 1 | 1 |
| `firstGlError` | **[null:0x0]** | **[null:0x0]** |
| offers / accepted / GL 本体 | 16000 / **13088** / 2939 | — |
| 参照（同一vendor GL 経路）vs 提示画像 | **0.072・0.05 %** | **0.066・0.04 %** |

- **意味**: **通常のゲームフレームが Vulkan 所有の color/depth で生産され、swapchain へ提示される**のが既定になった。
  bridge の往復 blit と GL 深度変換は走らない。**残る GL 依存は「GL context の生成」と
  「既定フレームバッファへ描く合成（post-present overlay 待ち）」**だけである。
- **旧経路**: `-Dceltium.vulkan.adoptGameFramebuffer=false` で bridge（往復 blit）に戻せる。**移行用として残す**。

## 0.58 無記名拒否の正体を特定: **既定フレームバッファへ描く合成**（＝行き先が無く、拒否が正しい）

- **stage マーカーで一撃特定**: 供給経路の判定点11箇所に段階番号を置き、無記名のときは理由に段階を載せた。
  結果は `stage=4` ＝ **`supportedImmediateTarget()` のゲート**。ところがゲートは**条件別に名乗る形に改訂済み**のはずで、
  実際には**元の1行形式に戻っていた**（＝理由が付かない状態）。
  → **ゲートの条件別名指しを入れ直した**（今回はコミット後に grep で残存を確認）。
- **確定した理由（AMD、adoption on、1サンプル）**:
  ```
  2 target: the default framebuffer is bound = 874
  ```
  ＝ format 2・stride 24・`GL_QUADS`・4頂点の**1枚クワッド**は、**既定フレームバッファ（画面）へ描く合成**として到着している。
- **評価（これは欠陥ではなく、正しい拒否）**: 既定フレームバッファは**共有イメージではない**ので、native の描画先が無い。
  **GL が描くのが正しく**、画像も一致する（PASSED・0.001〜0.09）。native に移すには
  **present の後に swapchain イメージへ重ねる経路**（post-present overlay）が必要で、それは別の機能単位である。
- **受入**: exit 0 / **clean** / **PASSED** / **VUID 0**、`offers 16000 / accepted 13290 / GL 本体 2737`。
- **記録（手順の教訓）**: **コミット済みの改訂が実行中の jar から消えていた**（ゲートが元の1行形式）。
  ファイルを巻き戻す操作のあとに**改訂の残存を grep で確認していなかった**ことが原因。
  **今後は「入れた修正が今もそこにあるか」を commit 直前に確認する。**
- **意味（条件 (b) の残量の性格）**: 起動直後の本質的な拒否・**既定フレームバッファへの合成（post-present overlay 待ち）**・
  pass 選択の少数、という**性質の違う群**に分かれた。**「GL に残っている＝未実装」ではない**。

## 0.57 無記名の拒否を**バッチ種別で分割**し、正体を1つに絞った（format 2・stride 24・mode 7＝GL_QUADS・count 4）

- **やったこと**: 入口で入れる無記名マーカーに**バッチの種別**（format / stride / drawMode / vertexCount）を載せた。
  942 件が一つの数字だった状態から、**群が1つに特定**できた。
- **判明**: 残る無記名拒否は
  `2 unrecorded (format=2 stride=24 mode=7 count=4)` ＝ **1枚のクワッド**（`GL_QUADS` = mode 7、4頂点）で、
  **format 2 の合成/オーバーレイの1枚クワッド**である。＝ **既知の合成ファミリ**と一致する。
- **併せて4箇所の拒否に理由を持たせた**（`submitImmediateBatchTimed` の `gpuStalled`、present が画面に届かない経路、
  present が例外を投げた経路、シェーダプログラムを用意できない経路）。監査の「拒否は原因を名乗る」に沿う。
- **受入（AMD、adoption on、同一場面）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `offers 17000 / accepted 13960 / GL 本体 3067`（**受理は今roundで 13101 → 13960 と増加**）。
- **静的な追跡は打ち切った（記録）**: 「メソッド先頭から return までの間に理由代入があるか」を機械的に判定しようとしたが、
  **メソッド境界の自動検出が複数行シグネチャで失敗**し、false negative になった。**動的な種別マーカーのほうが確実**だった。
- **次の一手（対象が1つに絞られた）**: format 2・stride 24・mode 7・count 4 の**1枚クワッドを拒否している条件**を名乗らせる。

## 0.56 format 2 の「理由なし」拒否を可視化（古い理由の持ち越しを断った）＋ null で壊れた1箇所を修正

- **format 2 の 886 件が `none` と記録されていた理由**: `"none"` は**成功経路**が入れる値（＝拒否理由なし）だった。
  つまり**理由を設定しない拒否経路**が `false` を返し、集計が**直前の成功で残った値**を読んでいた。
  ＝ 監査が求める「拒否は原因を名乗る」が、**stale な読み出し**で満たされていなかった。
- **修正**: `submitImmediateBatch` の**入口で理由を必ずクリア**（非 null のマーカー `"unrecorded"`）。
  これで拒否は**その呼び出し自身の理由**だけを持つ。
- **結果（AMD、adoption on）**: 集計が **`2 no reason recorded=875`** と出るようになり、
  **format 2 は「理由を設定しない経路」で拒否されている**ことが確定した（＝次に名指しする対象が1つに絞れた）。
  併せて `accepted 13101 → 13287`、`GL 本体 2926 → 2740` と改善。
- **途中の失敗（記録）**: 最初 `null` でクリアしたところ、理由を **dereference する呼び出し側**が
  **NullPointerException** を出して fixture が FAILED になった（`exit=1`）。**非 null のマーカー**に変えて解消。
  ＝ 「消したつもりで別の契約を壊す」の実例として残す。
- **次の一手**: format 2 の「理由を設定しない経路」を1つに特定して名乗らせる（`no reason recorded` が手がかり）。

## 0.55 **所有の分裂（同じ事実を2箇所が持つ）を1つにし、format 5 の拒否をゼロにした**

- **根因（goal 項目2 が名指ししていた「所有の分裂」そのもの）**: 「全面内容を受け取った」という**同じ事実**を
  **2つの所有者**が持っていた。
  - `VulkanTextureRegistry.markFullyInitialized(id)` → **レジストリの Set** に入れる
  - `VulkanGlTexture.hasFullContent()` → **インスタンスのフラグ**を読む
  全面供給は前者だけを更新するので、**後者を読むディスクリプタ判定は永遠に偽**のままだった
  （測定: `the mirror for texture 29 has never received its full content` が 709〜801 件/run）。
  さらに、供給条件そのものが `get(id) == null`（ミラー皆無）だけで、**「ミラーはあるが部分書き込みのみ」**という
  A04 の本命を対象外にしていた。
- **修正（1つの根因に producer/consumer を揃える）**:
  1. 供給条件を `existing == null || !existing.hasFullContent()` に拡張（＝A04 のケースも同じ全面アップロードで満たす）。
  2. `markFullyInitialized(id)` が**イメージ本体にも印を付ける**ようにし、判定と供給が同じ事実を見るようにした
     （`VulkanGlTexture.markFullContentArrived()`）。
- **受入（AMD、adoption on、p318 と同一場面）**:

| 計器 | A04 前 | **A04 後** |
|---|---|---|
| `fixtureStatus` / `validationClean` / VUID | PASSED / true / 0 | **PASSED / true / 0** |
| offers / **accepted** / **GL 本体** | 15000 / 11651 / 3376 | **16000 / 13101 / 2926** |
| **format 5 の拒否** | **709〜801** | **0（消滅）** |
| frames | 882 | 906 |
| 参照フレーム vs 提示画像（反転） | 0.001 | **0.091・>8 が 0.05 %** |

- **残る唯一の非起動時拒否**: **format 2 の 886 件**（まだ理由が `none` ＝ 別経路が無言で拒否）。**次はこの経路を名指しする**。
- **註**: 画像差 0.05 % は、新たに native 化した粒子と書き出し要求・捕捉の2フレーム差の範囲で、GUI 場面で観測した 0.04 % と同程度。

## 0.54 adoption 下の拒否の**理由を名指しできるようにし**、format 5 の実因を特定（部分ミラー＝監査 A04 のクラス）

- **やったこと（いずれも「拒否は原因を名乗る」という監査の指摘に沿う）**:
  1. `supportedImmediateTarget()` の**4条件をそれぞれ名指し**する形へ分割（既定フレームバッファ／viewport 原点／
     フレーム目標なし／viewport サイズ不一致／契約違反）。**呼び出し側がメッセージを上書きしていた**ため、
     ゲートの理由が消えていた（＝820件が無記名だった原因）。
  2. **lightmap の遅延供給を1メソッドに統合**（`ensureLightmapMirror()`）し、**ディスクリプタ判定からも呼ぶ**。
     供給が判定より後ろにあったため、再確保の窓で粒子バッチが拒否されていた（798件/run）。
  3. **粒子経路がディスクリプタ判定の理由を上書きしない**ようにした（未設定のときだけ既定文言を入れる）。
- **受入（AMD、adoption on、同一場面＝p318 と同条件）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `offers 15000 / accepted 11651 / GL 本体 3376`（前 15000/11526/3499）、
  **参照フレーム（p318）vs 提示画像 = 平均絶対差 0.001・>8 が 0.00 %**。
  **註**: 途中で `weather=true` の run と比べて 100 % 差が出たが、**それは別場面のため**で回帰ではない。
  **同一場面で比較すること**を改めて記録する。
- **判明した format 5 の実因（次に潰す対象）**:
  `the mirror for texture 29 has never received its full content` ＝ **部分書き込みしか受けていないミラー**で、
  コード自身のコメントどおり**監査 A04 のクラス**（atlas/texture を全面供給するまで GL に渡すのが正直な扱い）。
  ＝ **計画の項目2（texture 所有の統合・全面供給）がそのまま次の実装**である。
- **残る不明**: format 2 の 811 件はまだ**理由が `none`**（別経路が無言で拒否している）。次はこの経路を名指しする。

## 0.53 adoption 下で**受理が激減していた回帰を修正**（受理ゲートが adopted 状態を契約違反と判定していた）

- **計測で回帰を発見**: adoption を入れると受理が `9042 → 982`、GL 本体が `1985 → 17045` に**悪化**していた
  （描画結果は正しい＝PASSED・画素一致だが、**native の割合が下がる**ので放置できない）。
- **理由**: 拒否の理由が**一律** `target is outside the RGBA8/D24/full-viewport bridge contract`
  （format 6 6844・7 6200・2 982・5 975）。実体は `VulkanFrameTarget.currentFramebufferViolation()` が
  **RGBA8＋D24 レンダーバッファ**の契約で adopted 状態（**D32F テクスチャ深度**）を違反と判定していたこと。
  `matches()` と `endFrame` に入れたのと**同じ形の見落とし**。
- **修正**: 束縛 FBO が共有 FBO のときは `currentFramebufferViolation()` が `null` を返す
  （判定対象はこのクラス自身が作ったオブジェクトで、入口の liveness 検査が既に見ている）。
- **受入（AMD、duration 120 s、adoption on、`presentReadback`＋`dumpPresentedImage`）**:

| 計器 | 基準（GL FBO） | adoption 修正前 | **adoption 修正後** |
|---|---|---|---|
| offers | 11000 | 18000 | **15000** |
| **accepted** | 9042 | **982** | **11526** |
| GL 本体 | 1985 | 17045 | **3499** |
| `fixtureStatus` | PASSED | PASSED | **PASSED** |
| `validationClean` / VUID | true / 0 | true / 0 | **true / 0** |
| frames / `presentedFromVulkan` | — / — | — | **898 / 886** |
| `firstGlError` | — | [null:0x0] | **[null:0x0]** |
| 参照フレーム vs 提示画像（反転） | — | 0.001 | **0.012・>8 が 0.00 %** |

- **残る拒否（adoption 下・1サンプル）**: `format 2: 830`・`format 5: 812`・`format 6: 176`・`format 9: 17`・
  `format 4: 12`・`1/7/3/8: 各8〜11`。**次は format 5（粒子・ライトマップ）と format 2（合成）の理由**を読む。
  （基準時は format 5 の拒否は 26 だったので、**adoption 下で format 5 が増えている**点も確認対象。）

## 0.52 **(b) の本体を Intel でも受入**（bridge/sync/memory 経路のため両GPUで確認）

- **受入（Intel UHD Graphics 630 / Mesa、duration 120 s、`adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| **exit / `validationClean` / messages / `fixtureStatus`** | **0 / true / 0 / PASSED** |
| VUID / クラッシュ | **0 / なし** |
| frames | 390 |
| **`presentedFromVulkan`** | **365** |
| **pass 失敗** | **なし** |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]** |
| GPU | `Mesa Intel(R) UHD Graphics 630 (CFL GT2)` |
| 参照（AMD の GL 経路フレーム）vs 提示画像（反転） | 平均絶対差 **0.168**・>8 が 0.98 %・最大 126 |

- **註（正直な区別）**: 画素比較の参照は **AMD のフレーム**なので、**この 0.98 % は vendor 間の差**を含む。
  **同一 vendor 内（AMD の adoption 対 AMD の GL 経路）では 0.001・0.00 %** だった。**vendor 間の画素一致を主張しない**。
- **意味**: **adoption（ゲームが Vulkan 所有 color/depth に直接描画）は AMD/Intel の両方で PASSED**。
  同期・メモリ・interop 経路の変更なので、これは計画が求める「早期の両GPU確認」を満たす。

## 0.51 **(b) の本体が受入成立**: ゲームが Vulkan 所有イメージに直接描画し、PASSED・提示 997・画素一致 0.001

- **残っていた1件の正体**: `endFrame` の契約検証だった。**同じ検証が2行連続で重複**しており（以前のパッチ痕跡）、
  **ガードしたのは1つ目・投げていたのは無条件の2つ目**だった。重複行を削除して解消（`errors=2883` の正体もこれ）。
- **受入（AMD、duration 120 s、`-Dceltium.vulkan.adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| **exit / `validationClean` / messages / `fixtureStatus`** | **0 / true / 0 / PASSED** |
| VUID / クラッシュ | **0 / なし** |
| frames | **1020** |
| **`presentedFromVulkan`** | **997** |
| **pass 失敗（4種すべて）** | **なし**（`frame begin failed` も `frame end failed` も 0） |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]** |
| **参照フレーム vs 提示画像（上下反転）** | **平均絶対差 0.001・>8 が 0.00 %** |

- **意味（条件 (b) の到達点）**: **ゲームの通常フレームが Vulkan 所有の color/depth で生産され、swapchain へ提示される**。
  `beginFrame`/`endFrame` の**往復 blit も GL 深度変換も走っていない**。
  **残る GL 依存は「GL context の生成」と「GL がまだ描いている約2割（format 2 の非画面合成 709 等）」**であり、
  それらは**adoption 後は共有イメージへ直接書かれる**（＝表示経路にはもう GL のコピーが介在しない）。
- **フラグ**: 既定 off のまま（`-Dceltium.vulkan.adoptGameFramebuffer=true`）。**残る GL 描画を native へ移してから既定化**する。
- **次の実装単位**: **format 2 の非画面合成 709 を native へ**（行き先が共有イメージになったので、これで初めて可能になる）→
  その後 **GL context 自体の除去**（条件 (b) の残り）。

## 0.50 **ゲームが Vulkan 所有イメージへ直接描画し、提示が成立した**（(b) の本体が動作）

- **原因は2つの blit の未ガードだった**（前節の続き）: `beginFrame` の呼び出し列は
  `drainGlErrors()` → `glGetInteger` → **`glBlitNamedFramebuffer(fbo, sharedFbo, GL_COLOR_BUFFER_BIT)`** →
  **`glBlitNamedFramebuffer(fbo, scratchFbo, GL_DEPTH_BUFFER_BIT)`** → `convertDepth`（ガード済み）→ `glCheck`。
  adoption 後は `fbo == sharedFbo` なので**1つ目は画像の恒等 blit ＝ `GL_INVALID_OPERATION`**、2つ目も不要。
  **ガードは前回の patch スクリプトが「直前 260 文字に `sharedGameFramebuffer` がある」と誤判定して適用を飛ばしていた**
  （実際は分岐の記述が近くにあった）。**行単位で当てて解消**。
- **受入（AMD、duration 120 s、`-Dceltium.vulkan.adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| frames | **987** |
| **`presentedFromVulkan`** | **960**（従来は 0） |
| **`gameFramebufferAdopted`** | 1 |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]**（GL エラーなし） |
| `validationClean` / VUID / クラッシュ | **true / 0 / なし** |
| **参照フレーム vs 提示画像（上下反転）** | **平均絶対差 0.026・>8 が 0.00 %** |
| `fixtureStatus` | **FAILED**（下記の残欠陥による） |

- **意味**: **ゲームが Vulkan 所有の color/depth に直接描画し、そのフレームが swapchain へ提示され、内容は GL 経路と
  画素単位で一致する**。`beginFrame`/`endFrame` の **両方向の blit と GL 深度変換は走っていない**（＝条件 (b) の本体）。
- **残る欠陥（fixture が FAILED にする原因・1点）**:
  `error: the Vulkan terrain backend did not render cleanly: passes=2883 skipped=0 errors=2883 lastDraw=62`
  ＝ **パスは全部走り（skipped=0）描画もされている（lastDraw=62）**が、**パスごとに1件のエラーが計上**されている。
  次の計器は **この `errors` が何を数えているかを名指しすること**（パス内の例外か、endFrame のハンドオフか）。
- **フラグ**: 既定 off のまま（`-Dceltium.vulkan.adoptGameFramebuffer=true` で有効）。**移行用の bridge を置き換える経路**
  なので、残欠陥を閉じてから既定化を判断する。

## 0.49 **adoption は成立した**（計器で確定）。残るのは差し替え後に他所で出る保留 `GL error 0x502`

- **前節の仮説は棄却**（レポートで確認）: `frame target recreation` は **0 回**（再生成されていない）。
  `matches()` が adopted 状態を受け入れる形にしたうえで再測定したが、症状は同じだった。
- **決め手は失敗メッセージへの計器**（bound / `gameFbo` / `sharedFbo` / adopted / useRemembered を埋め込んだ）:
  ```
  frame begin failed: beginFrame: RGBA8 colour copy and D24 to D32 depth conversion
      [bound=7 gameFbo=7 sharedFbo=7 adopted=true]: GL error 0x502
  ```
  ＝ **束縛 FBO・記憶した宛先・共有 FBO がすべて一致し、adopted も true**
  ＝ **ゲームのフレームバッファ差し替えは機能している**（blit と深度変換はスキップされている）。
- **さらに adoption の瞬間の GL エラーを記録**（Mixin から `glGetError` を渡す計器）:
  ```
  gameFramebufferAdopted = 1
  adoptionGlError        = 0x0     ← 差し替え自体はエラーを残さない
  ```
  ＝ **保留中の `0x502` は「差し替えの後、最初の `beginFrame` の検査まで」の間に他所で発生**している。
- **次の一手（位置が確定している）**: その窓の中を二分する計器を置く
  （(a) 差し替え直後の bind 完了時、(b) `beginFrame` 入り口の `GlState` 直後）。
  これで「MC の描画か、こちらの前処理か」が一意に決まる。**仮説を増やさず、この2点だけを測る。**
- **対応**: 差し替え（フラグ経路）は**巻き戻した**。**失敗メッセージの計器だけを残した**（フラグに依存せず、
  診断として常に有効）。ビルド緑。
- **註**: ここまでで (b) の**設計は完成**しており、**実装も動いている**（差し替え・深度テクスチャ・契約・記憶した宛先）。
  残りは**この 0x502 の発生源1点**である。

## 0.48 深度のテクスチャ差し替えを実装しても残る失敗: **別の beginFrame 呼び出し／別の target 実体**が次の焦点

- **実装したもの（1単位）**: (1) 共有 FBO を契約にする `beginFrame`、(2) `beginFrame()` を**記憶した宛先**で呼ぶ、
  (3) `FramebufferMixin` での adoption、(4) **`OpenGlHelper.glFramebufferRenderbuffer` を redirect し、
  共有深度（外部メモリの**テクスチャ**）を `glFramebufferTexture2D` でアタッチ**（外部メモリのレンダーバッファが無いため）。
  redirect は「**束縛 FBO が共有 FBO のときだけ**」発火する（`glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)` で判定）。
- **結果**: **`gameFramebufferAdopted=1`**、しかし **`presentedFromVulkan=0`／FAILED** は変わらず。診断も同じ2つ:
  `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` と
  `beginFrame: the bound draw framebuffer is the default framebuffer`。
- **確認したこと（切り分け）**:
  - `beginFrame()` が `beginFrame(true)` を呼ぶ patch は**適用済み**（ソースで確認）。
  - mixin は**登録されている**（プラグインの「Found 37 mixin classes」に含まれる）。
  - `OpenGlHelper.glFramebufferRenderbuffer` は **static** で署名も injector と一致（ソースで確認）。
  - **それでも2つのエラーが残る** ＝ 失敗している `beginFrame` は
    **別の呼び出し（または別の target 実体）**であり、`gameFbo` がその実体では 0 のままである。
- **次の計器（具体的・これで決まる）**: `beginFrame` の失敗時に
  **bound FBO・`gameFbo`・`sharedFbo`・`frameTarget` の同一性（hashCode）**を一緒に記録する。
  これで「別実体」か「別呼び出し」かが一意に決まる。
- **対応**: **既知の失敗を抱えたフラグ経路を残さない**方針で**巻き戻した**（HEAD に戻し、追加した mixin も削除。ビルド緑）。
- **この3ラウンドで確定した (b) の全前提（再掲・設計として有効）**:
  1. 共有 FBO を契約にする 2. `beginFrame` は記憶した宛先を使う
  3. **深度はテクスチャとしてアタッチ**（外部メモリのレンダーバッファはこのバインディングに無い）
  4. そのうえで blit 2箇所と束縛クエリを削除 5. format 2 の合成を native へ。

## 0.47 前提1は**このバインディングでは実現できない**と判明（外部メモリのレンダーバッファが無い）

- **前提1の実装を試みた**: 共有深度を「外部メモリのレンダーバッファ」としても公開する
  （`GL45C.glCreateRenderbuffers()` ＋ `glRenderbufferStorageMemEXT`）ことで、MC の `depthBuffer` に渡せるようにする。
- **結果**: **`GL45C.glCreateRenderbuffers()` は存在して通る**が、**`EXTMemoryObject` に renderbuffer 用の関数が無い**
  （`javap` で確認: `EXTMemoryObject` に `*Renderbuffer*` は1つも無い）。
  ＝ **LWJGL のこのバインディングでは、外部メモリから GL のレンダーバッファを作れない**。
- **意味（item 6 の構造）**: **MC の `Framebuffer` は深度を `glFramebufferRenderbuffer` でアタッチする**ため、
  **深度は Vulkan 所有へ移せない**。native のパスは深度を読み書きするので、
  **「色だけ共有・深度は GL」という混成では条件 (b) の通常フレームは成立しない**。
- **代替経路（次の実装候補・実在する API で足りる）**: MC のアタッチ呼び出しを**横取りしてテクスチャとして付ける**。
  MC は `OpenGlHelper.glFramebufferRenderbuffer(...)`（Forge のヘルパー）経由でアタッチしているので、
  **そこを Mixin で redirect し、共有深度を `glFramebufferTexture2D` でアタッチ**すれば、
  外部メモリの**テクスチャ**（これは既に作れている）で深度を Vulkan 所有にできる。
  **`Framebuffer` 側の変更は不要**（呼び出し1箇所を差し替えるだけ）。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。
- **未確認**: 上記 redirect が MC の深度の使い方（`glClear`・深度テスト・`GL_DEPTH_ATTACHMENT` の期待）と両立するかは**未検証**。
  次はこれを最小の場面で試す。

## 0.46 item 6 の (B) に必要な**2つの前提**が実測で確定（深度の型と、記憶した宛先の使用）

- **試作（§0.45 の続き）**: 差し替え時に**先に** frame target へ「これがゲームのフレームバッファだ」と教える
  （`noteGameFramebufferAdopted` で `gameFbo = sharedFbo` 等を設定）ことで、§0.45 の `gameFbo==0` 問題を解消した。
- **結果**: **`gameFramebufferAdopted=1`**（差し替えは1回で成立）だが **`presentedFromVulkan=0`／FAILED**。診断:

| メッセージ | 意味 |
|---|---|
| `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` | **MC は `depthBuffer` をレンダーバッファとしてアタッチする**のに、こちらは**テクスチャ id** を入れた → `GL_INVALID_OPERATION` |
| `beginFrame: the bound draw framebuffer is the default framebuffer …` | **ワールドパスは `useRememberedTarget=false` で `beginFrame` を呼ぶ**ため、記憶した宛先が使われない |
| `skipReason=translucent:_no_frame_target`、`passes=0 skipped=547` | 帰結（フレーム目標が立たないのでパスが全部スキップ） |

- **item 6 の (B) に必要な前提（これで具体化した）**:
  1. **共有深度を「外部メモリのレンダーバッファ」として公開する**（`glImportMemoryFdEXT` ＋
     `glNamedRenderbufferStorageMemEXT`）。MC の `Framebuffer` は深度を**レンダーバッファ**として扱うので、
     テクスチャでは型が合わない。**現在の bridge は D32 をテクスチャで持っている**ため、ここが実装対象。
  2. **ワールドパスの `beginFrame` が記憶した宛先を使うようにする**（`useRememberedTarget` の経路に乗せる）。
  3. そのうえで §0.45 の (A)（blit と束縛クエリの削除）。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。**次の実装単位は前提1（深度をレンダーバッファで公開）**で、
  これは **adoption と独立に検証できる**（bridge の深度アタッチが変わるだけ）。
- **註**: 前提2は `beginFrame` の呼び出し側（`VulkanTerrainGpuBackend`）の分岐なので、1箇所の変更で済む見込み。

## 0.45 共有 FBO を契約にする改訂でも失敗: **順序は「blit を先に消す」**（実測で確定）

- **試作（§0.44 の続き）**: `beginFrame`/`endFrame` を「束縛 FBO が共有 FBO なら契約検証・アタッチメント照会・
  入出力 blit・深度変換をすべて省き、共有イメージ自体を契約とする」形に組み直した（フラグ付き・fail-open）。
- **結果**: **`gameFramebufferAdopted=1154`** と差し替えは機能したが **`fixtureStatus=FAILED`／`presentedFromVulkan=0`**。
  診断に理由が2つ出た:

  | メッセージ | 意味 |
  |---|---|
  | `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` | 恒等 blit／変換が `GL_INVALID_OPERATION` |
  | `beginFrame: the bound draw framebuffer is the default framebuffer; the verified bridge needs an FBO …` | ワールドパスが**既定フレームバッファ束縛**で走った |
  | `did not render cleanly: passes=0 skipped=554 errors=1108` | パスが1つも描かれなかった |

- **根因**: **MC のフレームバッファ束縛プロトコルとの衝突**。MC はフレームの途中で**意図的に unbind（0 を束縛）**する
  箇所があり、差し替えると**ワールドパスが 0 束縛で走る**。bridge の `beginFrame` は「GL が何を束縛しているか」を
  **blit のために読んでいる**ので、その前提が崩れると壊れる。
- **確定した正しい順序（この失敗が示した）**: **先に blit と束縛クエリを消す**。
  **Vulkan のパスは GL の束縛を必要としていない**（描画先は `frameTarget` が持っている）。
  束縛を読む理由は**2つの blit のためだけ**なので、blit を消せば束縛は無関係になり、その後に差し替えれば衝突しない。
  → 段取りは **(A) blit 2箇所と束縛クエリを削除（提示画像を証拠に）→ (B) ゲームの FBO を共有へ差し替え**。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。**(A) が次の実装単位**である。

## 0.44 item 6 の本体（FBO 所有権）は**フレームライフサイクルの置き換え**だと実証（試作は巻き戻し）

- **試作**: ゲームの主フレームバッファ（`mc.getFramebuffer()` と**同一性で**判定）の
  `framebufferObject`／`framebufferTexture`／`depthBuffer` を、共有イメージの GL オブジェクトへ差し替える
  （フラグ `-Dceltium.vulkan.adoptGameFramebuffer`、fail-open）。同一 FBO になる blit 2箇所は恒等コピーとしてスキップ。
- **1回目**: `gameFramebufferAdopted=0` ＝ 発火せず。原因は**私のガードが `frameOpen` を要求していた**こと
  （MC はフレームバッファを bridge の `beginFrame` **より前**に束縛するため、その時点では false）。ガードを直した。
- **2回目**: **`gameFramebufferAdopted=1142`** ＝ 差し替えは機能した。しかし **`fixtureStatus=FAILED`／`presentedFromVulkan=0`**
  ＝ **フレームが出なくなった**。
- **根因（構造）**: bridge は `beginFrame` で **「束縛されている game FBO が検証済みの契約（特定の GL テクスチャ＋
  レンダーバッファ）であること」を検証**している。差し替えると、その検証対象が**まさに共有 FBO になる**ので契約が破れる
  ＝ **ブリッジの begin/end 機構そのものが前提ごと無効になる**。
- **結論**: **item 6 の本体は「小さな付け替え」ではない**。必要なのは
  **フレームライフサイクルの置き換え**（ゲームが共有イメージへ直接描く前提で、検証・開始・終了・レイアウト受け渡しを
  組み直す）であり、**9割の GL 依存はこの一点に集約されている**。この試作は**巻き戻した**（HEAD に戻しビルド緑）。
- **次に必要な設計（順序）**: (1) 共有 FBO を「契約」にする（検証を共有側へ反転）、
  (2) `beginFrame`/`endFrame` を「GL のフレームバッファとの往復」から「共有イメージの前後処理」へ組み直す、
  (3) blit 2箇所を削除、(4) その上で format 2 の合成を native へ。**(1)(2) は不可分**である。

## 0.43 lightmap の供給漏れ（format 5）を解消: mod 側から unit を渡し、その場で取り直す

- **実装（モジュール境界を守る形）**:
  1. `celtium-vulkan` 側に **`celtium$setLightmapUnit(int)`**（**ただの int**。Minecraft クラスは持ち込まない）を足し、
     再確保で `lightmapGlId` が零化された場合に **その unit に束縛されている id を GL に問い合わせる**
     （旧 active unit は復元する＝状態変更ではなく問い合わせ）。
  2. `forge122` 側（`EntityRendererMixin` の world pass 先頭）で **`OpenGlHelper.lightmapTexUnit` の値を一度だけ渡す**
     （model draw が GL に束縛テクスチャを問い合わせているのと同じ形）。
  3. 供給は **id ごとに一度だけ**（`imageFor` → `uploadFromGlTexture` → `markFullyInitialized`）。
- **受入（AMD、weather、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **PASSED** / **VUID 0**、クラッシュなし。
  **`lightmapSuppliedOnDemand=2`**、**`lightmapSupplyFailure=[none]`**、
  そして **`reason=lightmapped …` の拒否行が消滅**（以前は毎 run 12〜23 件）。
- **分岐がデッドでないことの確認**: 前回の測定で `lightmapSuppliedOnDemand=0` だったのは
  **条件のどれかで止まっていた**ためで、**拒否メッセージに `id=` と `unit=` を載せた**ことで切り分けられるようにした。
  その次の run で供給が 2 回走り、拒否が 0 になった ＝ **到達可能な経路**である。
- **意味**: 条件 (b) の3群のうち **(iii) 少数の供給漏れ**が閉じた。残るのは
  **(i) 起動直後 10 フレームの 80 件（本質的）** と **(ii) format 2 の 709 件（FBO 所有権の移動待ち＝item 6）**。

## 0.42 lightmap 未ミラー（format 5）の扱い: **id が零化されるため mod 側の計器が要る**（試作は巻き戻し）

- **試したこと**: format 3/5 のバッチが到着した時点で lightmap ミラーが無いとき、**その場で再供給**する
  （テクスチャで成功した「遅延全面供給」と同じ形）。GL id は**記憶から復旧できない**（再確保の経路が
  `lightmapGlId = 0` と**零化する**ため）ので、**MC 側の公開定数 `OpenGlHelper.lightmapTexUnit` のユニットに
  束縛されている id を問い合わせる**形を書いた。
- **結果**: **ビルドが通らない**。`celtium-vulkan` は **Minecraft に依存しない**モジュールであり、
  `net.minecraft.client.renderer.OpenGlHelper` を参照できない（**この依存の向きは設計として正しい**）。
- **対応**: **試作は巻き戻した**（HEAD の状態に戻し、ビルド緑を確認）。format 5 の拒否は**現状のまま正しい振る舞い**
  として残る（その時点で native の描画先が無いため）。
- **確定した次の一手**: 復旧には **mod 側（`forge122`）から lightmap ユニットの値を渡す経路**が要る。
  既に model draw が GL に束縛テクスチャを問い合わせている**同じ形**（Mixin 側で定数を読み、値だけを渡す）で足りる。
  **モジュール境界を越えて MC クラスを持ち込まない**という制約を、この試行が実証した。
- **量**: この群は 1 サンプルあたり **12〜23 件**（`5 lightmapped batch with no lightmap texture mirrored`）。

## 0.41 条件 (b) の残りを**性質で分離**: 起動直後のみの 80 件と、FBO 所有権に阻まれる 709 件

- **計器**: `lastUiPass == null`（その実体でまだ地形パスが走っていない）と「pass はあるがパイプラインが無い」を分けて数えた。
- **結果（AMD、duration 120 s、676 frames、exit 0 / clean / PASSED）**:

| 計器 | 値 |
|---|---|
| `noPassRefusals` | **80** |
| `noPassFrames` | **1..10**（＝**起動後 10 フレームのみ**） |
| 拒否理由 | `preflight: format=6/9 … (no pass has run yet in this instance)` |

  **起動直後の 10 フレームを過ぎると 0 件**。676 フレーム中 1.2 %。
- **読み方（(b) の性格分け）**:
  1. **80 件は本質的**: その実体でまだ**どのパスも走っていない**ので、**native の描画先（render pass）が存在しない**。
     拒否して GL に渡すのが正しい（**欠陥ではない**。捕捉は tick 200 なので受入には影響しない）。
  2. **709 件（format 2）は「画面合成以外の合成」**（overlay を framebuffer へ混ぜる・postprocess 系）。
     これらは**行き先が GL のフレームバッファ**であり、**FBO の所有権が Vulkan に移るまで native にできない**
     ＝ **item 6（GL context / bridge の除去）と一体**である。**postprocess の設計ではなく所有権の話**だと確定した。
  3. 23 件（format 5・lightmap 未ミラー）は下の節で扱う。
- **意味**: 条件 (b) の残りは「GL を消す作業」ではなく、**(i) 起動直後は本質的に GL に渡す**、
  **(ii) 709 件は FBO 所有権の移動待ち**、(iii) 少数の供給漏れ、という**3つの性質に分かれた**。
  次に着手できるのは (iii) である。

## 0.40 GUI / font / HUD の画像等価（項目5の未比較項目・提示画像経路で測定）

- **場面**: 三人称＋`keepGui`（HUD とフォントが描かれる）。証拠は**提示画像**（`dumpPresentedImage`）。
- **受入（AMD、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **fixture PASSED** / **dumped=true**。

| 比較 | 平均絶対差 | >8 が異なる | 最大 |
|---|---|---|---|
| 提示画像 vs スクリーンショット（上下反転） | **0.015** | **0.04 %** | 160 |
| **うち HUD 帯（上部 250 行）** | **0.000** | **0.00 %** | — |

  **HUD とフォントの帯は画素単位で完全一致**（差は 0）。画面全体の 0.04 % は、
  書き出し要求（tick 198）と捕捉（tick 200）の**2フレーム差**によるもので、帯の外に限られる。
- **意味**: **GUI / font / HUD は native 経路（format 7 等）で描かれ、原版と画素単位で一致する**。
  項目5で「未比較」としていた GUI 系が、**比較済み**になった。
- **併せて確認**: この場面でも提示が成立し（`presentedFromVulkan` が毎フレーム）、validation はクリーンである。

## 0.39 条件 (b) の作業量: **GL にまだ残っている量の内訳**（実測）

- **量**: `offers 11000 / accepted 9042 / glBodyCompleted 1985`（p318）、`13000 / 10643 / 2386`（p321）
  ＝ **8割は native に受理され、約2割（≈2000/run・7/フレーム）が GL に渡っている**。
- **内訳（拒否集計の1サンプル、合計 1172）**:

| 理由 | 件数 |
|---|---|
| **`2 target is outside the RGBA8/D24/full-viewport bridge contract`** | **709** |
| `8 preflight: format=8 texture=16 (no terrain pipeline for this pass)` | **128** |
| `1/6/9 preflight: … (no terrain pipeline for this pass)` | 9〜21 ずつ |
| `5 lightmapped batch with no lightmap texture mirrored` | 23 |

- **読み方**:
  - **709 件の format 2 は「画面合成以外の合成」**（overlay を framebuffer へ混ぜるもの・postprocess 系）で、
    監査 S17 が「この layout は画面合成だけのものではない」と記録しているもの。**正規の GL 作業**であり、
    native 化するには postprocess/overlay の設計が要る（`skipGlComposite` の条件は**画面合成のみ**を対象にしているので、
    これらは意図どおり残る）。
  - **128 件の format 8** と少数の format 1/6/9 は **「その pass 用のパイプラインが無い」**という preflight 失敗。
    §0.12 で扱ったのと同じ種類の問いで、**pass の選び方**の問題である。
  - **23 件の lightmap 未ミラー**は format 5（particle）でライトマップが無いケース。
- **意味**: 条件 (b) の残りは「GL を全部消す」ではなく、**この4群を順に native へ移すか、対象外と明示するか**である。
  **量と理由が数えられている**ので、次はこの順に潰せる。

## 0.38 copyback は**単独では外せない**（ブリッジが循環していることの実証）

- **試したこと**: `-Dceltium.vulkan.skipColorReturn=true`（`endFrame` の色の書き戻しを止める。既存の `SKIP_DEPTH_RETURN` と
  対になるスイッチ）＋ `skipGlComposite`。証拠は提示画像。
- **結果（AMD、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **fixture PASSED** / **VUID 0** / クラッシュなし
  ＝ **壊れはしない**。しかし:
  | 比較 | 平均絶対差 | >8 が異なる | 最大 |
  |---|---|---|---|
  | 提示画像（copyback あり）vs 提示画像（なし） | **31.369** | **69.80 %** | 152 |
  | スクリーンショット（あり）vs 提示画像（なし） | 31.359 | 69.79 % | 152 |

  **GL 側スクリーンショットの平均は 211.7**（暗くならず**明るくなった**）。
- **根因（この結果が示す構造）**: ブリッジは**循環**である。`beginFrame` が
  **GL のフレームバッファ → 共有イメージ**をコピーし、`endFrame` が**共有イメージ → GL のフレームバッファ**を戻す。
  書き戻しだけを止めると、**GL 側が空のまま次フレームの `beginFrame` で共有イメージへ流し込まれ**、
  **native の内容が上書きされる**（だから提示画像まで変わる）。
- **意味（(b) への結論）**: **copyback は単独では外せない**。外すには
  **入ってくる方向の blit と、GL が描いている部分そのものを同時に**外す必要がある ＝ **条件 (b) の本体**
  （GL context を通常フレームから外す）と一体の作業である。**この順序を実測で確定できたことがこのターンの成果**。
- **次の計器**: `offers` / `accepted` / `glBodyCompleted`（＝GL の本体が走った回数）で
  **GL にまだ残っている量**を数える（下の節）。

## 0.37 失敗経路の限定確認（提示・線・合成の変更後）

- 提示は submit と wait を使い、線はパイプライン生成を、合成は受理ゲートを変えたので、**注入した失敗**で再確認した
  （AMD、duration 90 s、既定＝提示・線・合成の変更が入った状態）。

| run | harness | validationClean | fixture | game exit | クラッシュ | VUID | 失速の計器 |
|---|---|---|---|---|---|---|---|
| `failFenceWait=true` | 1 | **true** | **FAILED**（注入失敗として意図どおり） | 0 | なし | **0** | **`stalledStops=1`・`stalledTeardownSkips=548`** |
| `failSubmits=1` | 1 | **true** | **FAILED**（同上） | 0 | なし | **0** | `stalledStops=0` |

- **読み方**: **fence timeout は検出して停止し、teardown を548回スキップして資源を解放しない**
  （計画に記録済みの 547/549 と一致）。**ゲームは exit 0・クラッシュなし・VUID 0**。
  submit 失敗は**描画が欠けたことを fixture が FAILED として報告**する（黙って隠さない）。
  **harness の exit 1 はこの FAILED によるもので、注入した失敗の意図された結果**である
  （`validationClean` は true のまま＝Vulkan のエラーではない）。
- **意味**: 提示・線・合成の変更は、**失敗時の振る舞い（停止・解放しない・報告する）を壊していない**。

## 0.36 **GL の最終合成を外しても提示内容は同一**（条件 (a) の「最終合成が native」に到達・フラグ付き）

- **実装**: `-Dceltium.vulkan.skipGlComposite=true`（既定 off）。**提示済みフレームに対してのみ**（`framePresentedFromVulkan > 0`
  という**記録された事実**で判定）format 2 の画面合成バッチを**飲み込む**（GL の blit を走らせない）。
- **受入（AMD、`presentReadback`＋`dumpPresentedImage`＋`skipGlComposite`、duration 120 s）**:

| 計器 | 値 |
|---|---|
| `validationClean` / `validationExitCode` / `validationMessageCount` | **true / 0 / 0** |
| `fixtureStatus` / VUID / クラッシュ | **PASSED** / **0** / なし |
| **`glCompositesSkipped`** | **717** |
| `presentedFromVulkan` | **718** |
| **フラグ off のスクリーンショット vs フラグ on の提示画像（上下反転）** | **平均絶対差 0.017、>8 が 0.00 %** |
| 提示画像（off）vs 提示画像（on） | 平均絶対差 **0.001**、>8 が 0.00 % |

  **GL の最終合成を毎フレーム省いても、提示される絵は変わらない**（717/718 フレームで省略）。
- **なぜ GL 側スクリーンショットも変わらないのか（整合の説明）**: harness/ゲームのスクリーンショットが読むのは
  **世界フレームバッファ**（`mc.getFramebuffer()`）であり、それは **`endFrame()` の copyback が埋めている**。
  省略したのは**既定フレームバッファへの合成**であり、そちらは**もう表示に使われていない**（表示は Vulkan の present）。
  したがって「スクリーンショットが変わらないこと」と「合成を外したこと」は矛盾しない。
- **意味**: **条件 (a) の「最終合成・present が native」が、内容を変えずに成立した**。
  **残る GL 依存は描画そのもの（bridge・copyback・GL call）と GL context**（条件 (b)）である。
- **既定化はまだしない**: フラグは off のまま。次の段階で、**証拠経路を提示画像に一本化**してから既定化を判断する。

## 0.35 validation エラーの原因は**私の PNG 書き出し**だった（提示中に読む形へ修正・解消）

- **原因（harness の判定を読んで確定）**: `validationMessageCount=1`、
  `severity=ERROR vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage …, but the image has
  not been acquired …`。**私が追加した `dumpPresentedImage` が、提示済み（所有権を手放した）イメージを後から遷移させていた**。
  harness はこのメッセージで `validationClean=false` / `validationExitCode=1` を立てていた（**ゲームは exit 0・fixture は PASSED**）。
- **修正**: 書き出しを**リクエスト方式**にした。fixture は「次の提示でこのパスへ書け」と**フラグを立てるだけ**にし、
  **実際の読み戻しは提示処理の中（まだ所有権がある瞬間）**で行う（中心サンプルとまったく同じ位置・同じコピー処理）。
  さらに**捕捉の2 tick 前（tick 198）**に要求する（捕捉 tick は捕捉時に初めて記録されるため、198 では未設定だった）。
- **受入（AMD、`presentReadback`＋`dumpPresentedImage`、duration 120 s）**:

| 計器 | 値 |
|---|---|
| `validationClean` / `validationExitCode` | **true / 0** |
| `validationMessageCount` | **0** |
| `fixtureStatus` | **PASSED** |
| `presentedImageDumped` | **true** |
| 提示 PNG とスクリーンショット（そのまま） | 平均絶対差 52.956、>8 が 94.24 %、最大 178 |
| **同（上下反転）** | **平均絶対差 0.015、>8 が 0.00 %、最大 3** |

  反転比較の残差（0.015 / 最大3）は、**書き出し要求（tick 198）と捕捉（tick 200）の2フレーム差**によるもので、
  画素単位の一致は保たれている。
- **意味**: **GL に依存しない画像証拠**（提示画像そのもの）が、**validation クリーンな状態で**取れるようになった。
  これは「GL 合成を外す」ための前提である（証拠が GL のスクリーンショットに縛られなくなる）。
- **教訓**: 計器を足したら **harness の validation 判定まで読む**。ゲームが exit 0 で fixture が PASSED でも、
  検証層の ERROR は harness の判定を落とす。

## 0.34 **提示画像は画素単位で GL のフレームと一致**（差は行の並びだけだった）

- **GL に依存しない画像証拠を作った**: `VulkanSwapchain.dumpPresentedImage(Path)`（読み戻しのコピー処理を
  **中心サンプルと全画像で共通化**。バリアの往復＝presentable→transfer source→presentable は1箇所だけが行う）。
  backend に `celtium$dumpPresentedImage(Path)` を公開し、fixture が**捕捉時に** `screenshots/celtium-presented.png` へ書く
  （`-Dceltium.validation.dumpPresentedImage=true`）。
- **測定（AMD、`presentReadback`＋`dumpPresentedImage`、duration 120 s、640〜700 frames）**:

| 比較 | 平均絶対差 | >8 で異なる画素 | 最大 |
|---|---|---|---|
| そのまま | 52.949 | 94.24 % | 178 |
| **上下反転** | **0.000** | **0.00 %** | **0** |
| 左右反転 | 52.956 | 94.24 % | 178 |
| 両方反転 | 9.497 | 49.06 % | 133 |

  **上下反転で完全一致（平均絶対差 0.000・最大 0）**。**native swapchain に出たフレームは、ゲームが描いたフレームそのもの**である。
  差は**行の並びだけ**だった（ゲームのスクリーンショットは GL の読み出しを上下反転するため）。
- **途中で確定した2つのこと**: (1) 平均が完全一致するのに画素がほぼ全部違うのは**並びの違い**の signature である
  （並べ替えは平均を動かさない）。(2) スワップチェーンの形式は **`format=44`（B8G8R8A8 系）**で、チャンネル順は
  B,G,R,A。**チャンネル順は平均では絶対に判別できない**（この2点は記録に値する）。
- **意味**: 条件 (c) のうち **「native swapchain へ出した完成 frame」は画素単位で裏付けられた**。
  残るのは**実 window/compositor 出力の到達**（この環境の `DISPLAY=:0` の root は 0×0 で未確認）である。
- **未解決（別件として記録）**: この run は **ゲームは exit 0・fixture は PASSED** なのに
  **harness の `validationExitCode=1` / `validationClean=false`** だった。追加した PNG がスクリーンショット欄に増えたこと等、
  **harness 側の判定**を次に確認する（**この不一致を放置しない**）。

## 0.33 提示画像の不一致は**読み戻しの上限**が原因だった（§0.32 を撤回・提示内容は一致する）

- **原因**: `VulkanSwapchain` の読み戻しは `PRESENT_READBACK && this.presentReadbacks < 3` で、
  **最初の3フレームしか読んでいなかった**。`lastPresentedMean` は**起動直後のフレーム**の値であり、
  §0.32 で記録した 96.75 はそれだった。§0.16 で直した「別の瞬間の値」問題が、**1段下でそのまま残っていた**。
- **修正**: フラグが有効な間は**毎回読み戻す**（診断用フラグなのでコストは許容。件数は報告と上限のために残す）。
- **再測定（AMD、`presentReadback=true`、duration 120 s、exit 0 / clean / **PASSED**、640 frames、p50 13.51 ms）**:

| 値 | |
|---|---|
| `presentedImageMeanAtCapture` | **164.02** |
| **GL スクリーンショットの平均** | **158.72** |
| 差 | **5.30**（提示が1フレーム先行する分の残差） |
| 参考: スクリーンショットの HUD を除いた平均 | 137.39（差 26.6 → 提示画像は **HUD を含む**） |

- **結論**: **提示画像は GL が描いた完成フレームと同じ絵**である（HUD を含む）。**§0.32 の「一致しない」は撤回**する。
  §0.15/§0.19 の「平均が一致した」という観測は、**今度は正しい理由で**裏付けられた（同一場面・同一瞬間・毎フレーム読み戻し）。
- **教訓（2度目）**: 値を「いつ取るか」だけでなく、**その値がいつ作られたか**を確かめる。
  上限つきの計器は「最初の数回の値」を返し続ける。

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
- **訂正（§0.33）**: この「一致しない」は**読み戻しが最初の3フレームだけだった**ためで、
  提示画像は実際には**完成フレームと同じ絵**（164.02 対 158.72、差 5.3）。**この節の結論は撤回**。
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
| 4 | Nether の未完を具体的に直す | **両GPU受入済み**（§0.105・§0.20/0.21: 帰還根因は「転送先ポータル内に立っていた」AXIS 直交 step 未対応＋backend 再生成後の frame target デッドロック —— AMD 967 / Intel 349 pass・PASSED・実 Nether 画像で確認） |
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
| (a) 全vanilla描画と最終合成・presentがnative Vulkan | **部分** | **nativeで動いているもの**: 地形パス、全即時 format（1〜9 受理、TESR 場面で定常拒否 **0**・startup transient のみ）、**display list 経由の
  entity/model/手/TESR**（`modelDraw` 既定 ON・§0.108：entity 場面 AMD 0.35 %/Intel 0.16 %、GL replay 基準以下）、
  **default-FB composite overlay**（vignette/portal 等・§0.109：format-2 accept 2:0→2:170・`no frame target` 消滅）、
  フレーム最終合成（`presentFromComposite` 既定 ON）。**block registry は AMD 254/254 掃引・失敗0**、**Nether は
  AMD/Intel 両方 portal 帰還・PASSED**（§0.105）。**未検証/未完**: **custom shader/postprocess/spectator 3視点**
  （`a custom shader program is bound` 拒否＋non-shared FBO＝shader chain は GL のまま）、全 mob 種・破壊/selection、
  天候（雨/雪粒子は format 5 で経路あり・未場面計測）、実 FBO が non-shared の描画（`CELTIUM_TARGET_FOREIGN` は
  現在ほぼ発火しない＝通常場面で foreign FBO への描画が観測されていない）。**追記**: **spectator 3視点は
  動作確認済み**（AMD：creeper 緑・spider 5眼モザイク・enderman 反転、いずれも presented=screenshot
  0.000 %・PASSED —— post chain は GL 実行だが出力は shared FBO 経由で提示へ届く）。残る厳密な未了は
  **post-process 自体の native 化**（shader chain は GL のまま —— 出力は正しいが native Vulkan ではない）。 |
| (b) GL contextを生成せず、GL call/readback/bridge/copyback/GL swap/GL fallbackを必要としない実行 | **部分達成（GL-less 起動は実測・窓の絵が未解決なので未受入）** | **実測（`-Dceltium.vulkan.glless=true`、AMD）**: GL context なしで world まで起動し、Vulkan が毎フレーム提示して GL swap を1対1で飛ばす（`p548-glless`: `presentedFromVulkan=287`＝`glSwapsSkipped=287`・`presentFailures=0`・`validationMessageCount=0`）。GL 実体は stub（`glRenderer="Celtium stub GL"`・`deviceUuids=[]`）で、bridge も shared image もフレームに無い（`gameFramebufferIsShared=false`・`glless target: no handoff`）。**未受入の理由**: 提示画像がまだ絵ではない（`p559-glless`: `presentedDistinctColours=2`・`presentedNonBackgroundFraction=0.0`・`presentedVerticalBands=15`・fixture `FAILED_PRESENTED_IMAGE`）。GL-less run の harness verdict は `validationExitCode=1`・`gpuRequirementMet=false` で、PASSED ではない。**既定経路の bridge 側計器は従来どおり**: `regStaleCalls=0`・`inPlaceRefreshes=0`・announce 経由 16.2/フレーム（**ただしこれは通常fixtureの範囲の話であり「全sceneで readback が無い」意味ではない**）、構造修復1（`297ce5caa`）で atlas/lightmap の**無効化ごとの全面 readback を廃止**し `readbacksSkipped=473` 対 `directMirrorRefreshes=472` を実測（=ほぼ全ての更新が CPU 供給で閉じる）、**残る依存はブリッジ本体**（`VulkanFrameTarget`: FBO付け替え・色/深度blit・semaphore待ち・状態クエリ48箇所）で CPUコスト **0.58 ms/フレーム（AMD）/ 0.83（Intel）**、GL context は依然として生成する。§0.114 に全測定 |
| (c) native swapchain frameが実window/compositor出力へ到達 | **充足（実測・2026-10-11）／未測定は scanout のみ** | **実測**: grim（wlroots screencopy）で compositor 出力そのものを取得。marker run で compositor 出力 1280×720 全面が一様 magenta (255,0,255)（14 capture・window map 中のみ。未 map は黒）、**capture 0035 は renderer の番号付き presented dump `frame-20.png` と 0/921600 画素差・最大チャネル差 0**。通常 run でも **capture 0066 = `frame-100.png`（0/921600 差・実 scene 10104 色、fixture PASSED）**。fixture の presented 読み戻しは `presentedImageMeanAtCapture=170.0`・「921600 画素が単一色」で grim 側と一致（**以前ここに書いた 191.25 は RGBA 4ch 平均、170.0 は RGB 3ch 平均**で同じ magenta の別の取り方）。GL screenshot は黒（presented と 99.925 % 差）＝ GL framebuffer は証拠経路ではない。**未測定**: 走査出力そのもの（`ffmpeg -f kmsgrab` は `Failed to open DRM device`＝DRM master 無し・非 root。grim は headless でも実 KMS でも合成済み出力バッファを読むため、実出力で走らせてもこの段は閉じない）。§0.123・`docs/compositor-output-capture-2026-10-11.md` |
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
| **A03** | 部分更新なのに `UNDEFINED` でatlas全体とmipを破棄 | **受入済** | 全面uploadと部分更新を分離。patchは**レベル別に記録した現在layout**から遷移し、**level 0のみ**の範囲、mip chainを記録しない、patch自身が戻し遷移。**CPU契約**: `TextureContractCheck` がpatch外byte不変・色順・touched検証（PASS）。**GPU受入**: spriteの部分更新経路は全runで稼働 —— `animatedImageFills=879`（water/lava等のanimated texture patch、~2/frame）・PASSED・clean・画像一致。patch正しさはCPU契約＋実GPU場面の両方で確認済 |
| **A04** | 初回patchだけで全面未初期化のmirrorを作る | **是正側は修正済・全面供給は未解決** | `hasFullContent()` を持ち、全面内容の無いミラーは受理経路がdescriptorを作らず拒否（`partialMirrorRefusals`）。AMD実測では**0件＝このfixtureはpatch先行の順序を作らない**。atlas stitch時の全面データ保持・供給は残る |
| **A05** | patch寸法でmip chainを作り直す | **修正済** | A03の修正でpatchはmip chainを記録しない。sprite単位mipは `MIRROR_GL_MIPS` 経路がGLの各levelをコピーする（全atlas blitを正解とせず、遠景・透明境界での比較は残る） |
| **A06** | 保持画像とpatchの競合、hash skip | **修正済** | patch成功時に `lastPixelHash` を無効化し、patch後の同一内容全面uploadが重複skipされないようにした。CPU shadowとresource generationでの全面管理は残る |
| **A07** | metadata・転送成功の扱い | **修正済** | hashを転送**後**に確定、`imageUploads` は成功時のみ、`Entry.width/height` が画像に追随（寸法を可変に）、skipは `imageUploadsSkipped` として別計数。GL ID再使用の検証は残る |
| **A08** | 受理前失敗でpending meshに頂点が残る | **修正済・受入済（範囲拡大）** | preflightをappend前に置く方針を維持しつつ、`prepareImmediate()` で **pipeline と descriptor を group 生成前に用意**し、format 2/3/4/5/6/7/8/9 と texture にも広げた。`VulkanDynamicMesh` は頂点・indexの容量を両方確保してから書き込む。注入run `sol-cb-final-retry-amd` で `retries=1`・最終カウンタ4/4/4/4を実測 |
| **A09** | 受理後の消失を全形式で数えていない | **修正済** | 描画ループ内の**7つのskip地点すべて**が `postAcceptDroppedGroups` を増やす（以前はformat 5のdescriptor失敗のみ）。実測0は、実装したdrop計器が数えた消失がなかったという事実。全formatのGPU完了・見た目を証明しない。branch別の内訳とbatch ID対応付けは残る |
| **A10** | `offTargetDrawn` は実描画ではない | **修正済・AMD受入済** | 常に `return false` していた旧 offscreen scaffold（空passをclear/copy）を削除し、off-targetの受理済みgroupは **bind切替のHEAD（旧FBOがbind中）で同期windowが描いて書き戻す**。実測 `offTargetAccepted/Recorded/Completed=2/2/2`、`mainWasPending=true`・`secondaryWasPending=true`、別FBOのPNGで赤・緑が正位置・未更新領域の不一致0。copy-back回数を描画証拠にはしない方針を維持 |

| **A11** | format 2（POSITION_TEX_COLOR）は既定でGL（※この記述は撤去済み） | **修正済・AMD/Intel受入済** | 既定maskを `1,2,3,4,5,6,7,8,9` に。前提として backend 専用 `VulkanCommandBufferState`（handle別ライフサイクル）と `flushImmediateWindow()`（begin→acquire→pass→release→submit→fence→endFrame の同期window）を実装し、`flushPendingBeforeGlDraw` が pass 外で直接記録していた欠陥を除去。受入: `sol-cb-final-amd` exit 0 / clean / msgs 0 / fixture PASSED / passed=true / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0。再現は `sol-cb-base-amd`（70メッセージ）。**Intel**: target受入は `passed=true`（最終artifactのログ行）だが、run verdict は
 1回目 `worldFrames<100` の起動ゲート、2回目は480秒上限で report 未到達。**フレーム数不足が未解決**（window往復コストが最有力） |
| **A12** | state modelとpipelineの不一致 | **実質解消・残項は非可視/scope外** | **texture matrix解消**（unit毎stack＋push affine —— glint `texMatrixGroups=150` AMD実測、Intel 104・PASSED・0.065%）。**depthFunc解消**（hook＋state[7]＋5 pipeline compareOp —— glint EQUAL・held item ALWAYS）。**残項の分析**：(a) separate alpha blend factors —— alpha channelのみ差分、presented RGBでは不可視 (b) color mask RGBA固定 —— vanilla唯一の用途はGuiContainerのalpha-write-off、同様にpresented不可視 (c) alpha func —— vanillaは初期値設定後に動的呼出なし・実質static (d) scissor —— Forge GuiScrollingList等・mod GUI・item-8 scope。item-5（全vanilla coverage）判定に影響する可視差は全て閉じた |
| **A13** | current color/alphaの欠落（group keyにも無い） | **ほぼ解消** | texture-envのCOMBINE定数（hurt flash=1,0,0,0.3）はpushへencode・両GPU実測。**glColor runtime tint解消**：色要素なしformat（6/7/9）でgroup.color capture＋vert shaderで`v_Color=u_Color`適用（combine時はconst側を残す分岐）—— drawRectのtintがshaderに届く。残：POSITION系vertex alphaのGL互換細部のみ |
| **A14** | normal無視のITEM対応は照明対応ではない | **lit ITEM pipeline実装・実行確認・pixel未確認** | 固定function照明を実装（8181689e8+677636b75+48e7a926e）：glLight/glLightModel/enableLighting/enableLight/shadeModelをmirrorし（方向はcall時modelviewでeye space化）、groupはM⁻¹でmodelspace化済方向+ambient+diffuseをpushへ（dot(N,M⁻¹L)≡GL eval）。ITEM pipelineのlit variantはnormal@24をSNORM attrとして読み`immediate_tex_color_lit.vert`が`color*(ambient+Σdiff·max(N·L,0))`を評価、push 208B。**実ITEMレイアウト(color@12/tex@16)の属性offset latent bugも修正**（旧tex@12/color@20はPOSITION_TEX_COLOR順で誤り）。AMD: 388 format-4 batchが1runで描画・VUID 0（heldItem=enchanted fixtureで1433/run・enchant glint pass含む・clean継続）。**残**：lit itemのpixel検証（fixtureのitemは常にoff-frame/微小 — drop apple=+2.5で石陰解消も0.5m、first-person hand=equip外、third-person=player model未撮影、GUI slot=空）、entity model path（POSITION_TEX mesh・normal無し）のlit化、format-8/9 lit variant |
| **A15** | sampler固定値と旧比較による調整 | **ほぼ解消** | **wrap解消**：registryがmirror作成時に`GL_TEXTURE_WRAP_S/T`を実textureから読みU/V address modeへ反映（REPEAT textureがclampingでedge texel化していた欠陥を修正、0.000%維持）。min/magは既に実GL MAG_FILTERから（announceFullPixels既存読取り）+ mag flagでmin/mipmap実効一致。残：maxLod=1.0の測定由来clamp（旧比較より現行が正しいと確立済）とanisotropy無効のみ —— blur/clamp引数のannounce経由は、実param直接読取りの方が正確なため不要と判断 |
| **A16** | 「hot-pathのGL読取り除去済み」は過大 | **訂正・現状棚卸し済** | 残るGL readの棚卸し（call site実測、診断用/機能的に区分済）：**機能的（native入力そのもの）**= MODELVIEW/PROJECTION（matrix mirror不成立時のfallback＋per-batch 6554等）・CURRENT_COLOR（group色capture）・LINE_WIDTH・VIEWPORT/SCISSOR_BOX（pass setup 3626/6051/1697）・TEXTURE_BINDING_2D+ACTIVE_TEXTURE（group開始時）・DRAW_FRAMEBUFFER_BINDING（beginFrame判定）・glGetTexLevelParameteri（texture寸法）・glGetTexImage/glReadPixels（seeded post chain seed＋screenshot）。**診断用**= glGetError probes（firstGlError/frameEntryGlErrorの11箇所）・glGetString（capability判定・起動時）。matrix mirrorが既定のためMODELVIEW/PROJECTIONの頻度はmatrix変更時のみ —— 残りはnative drawの入力であり除去対象ではない（item 6でGL context自体を落とす時にのみ消える） |
| **A17** | `withoutGlIdentity` 成功はGLなし描画ではない | **訂正済** | GL contextを生成せず全frameをVK資源から描く経路が要る（条件(b)） |
| **A18** | bridge presentのownership/layout契約 | **過渡経路として閉じる** | 監査時点のbridge資源（往復blit＋GL深度変換）は既定経路から消えた：adoption既定化（§0.59）でゲームはshared imageへ直接描き、begin/endFrameはsemaphore＋GENERAL layoutのみ。opt-out debug経路（adoptGameFramebuffer=false）だけが旧資源を残し、その契約は別経路のものとして最終native経路の判定対象から外す。A18の「最終native経路がbridge資源を使わない」条件は既定で成立 |
| **A19** | native presentの画面到達証拠 | **確認済** | `--headless` runはnested wlroots-headless sway（HEADLESS-1 @1280x720・独自SWAYSOCK/Wayland socket）を起動する —— `XDG_RUNTIME_DIR=<nested> WAYLAND_DISPLAY=wayland-1 grim` で **compositor出力をscreencopy取得**に成功。captureは実Minecraft scene（flat world+sky）を示し、sky bandの平均RGBがgame screenshotと完全一致（155.4,191,255）—— **swapchainの提示内容がwindow→compositorへ正立・正色で届く実証**。証拠 `docs/history/evidence/grim-compositor-2026-10-10.png`。条件(c)の「実window出力到達」はこの経路で確認できる |
| **A20** | wait failureを全面閉鎖したとは言えない | **AMD+Intel受入済** | `waitForIdle()` の非TIMEOUT失敗（device lost等）でも frame を abandon する欠陥を修正（旧来は GL write-back hang）。`failDeviceLost` でpost-submit lost注入 → `CELTIUM_DEVICE_LOST`発火・bounded終了・PASSED —— AMDで確認済、**Intelでも注入発火・frame abandon・継続PASSED/clean**（p487）。fault matrix（alloc/descriptor/submit/fence/idle/texture upload/device lost）は全typeに注入を持ち両GPUでdevice-lost経路を検証 |
| **A21** | vanilla postprocessを根拠なくscope外にした | **全 vanilla post chain が native 動作・AMD/Intel両受入** | **native executor完成**（4063b7835+26c8c31ba）：(a) game-main系（invert/creeper/spider/desaturate/bits/blur/color_convolve postfx）は`ShaderGroup.render` hookで全pass収集→terrainと同じmid-frame handoff内でnative実行（`minecraft:main`=shared image、named target=pure-VK image）。(b) entity_outline系（group main=outline FBO）はseeded pathでnative実行：glGetTexImage seed→chain target upload→passes→fence download→glTexSubImage2D write-back、frame semaphore pairなしでfence同期。push schema正規化（ProjMat/InSize/OutSize/Time head+extras JSON順）、7 program全port、descriptor ring(32)方式。AMD/Intelで spectator 3 chain + glow outline がnative動作（postChains=40-51・VUID 0・color_convolve 196B push可） |
| **A22** | 比較worldは全vanillaのcoverageではない | **registry sweep実装済・全block描画検証** | `allBlocks` flagが `Block.REGISTRY` を自動列挙し全blockを41幅壁状に配置（stone土台、液体/落下block skip）—— **placed=244/244**・壁の全block modelがterrain passで描画、presented差0.163%（transient particle域）。小場面分割のentity fixture（8 mob・TESR chest/sign・item entity・enchanted glint・hurt flash・team outline・spectator 3視点・weather・Nether/End・animated texture・9 immediate format・GUI/HUD）と合わせ全vanillaの可視content経路を実測で覆う |
| **A23** | 古い画像差とlocal attributionの混同 | **訂正済** | 68.81%（旧baseline対地形）と 0.267/255・0.40%（同一Celtiumの描画切替）を最新版全体の一致率として使わない。pinned Celeritas対current Celtiumを同一fixture/GPU/camera/sceneで再取得する |
| **A24** | 比較器が欠落verdictをfail-closedにしない | **修正済** | 両側欠落は `None==None` で素通りしていた —— REQUIRED_EQUAL・FRAMEBUFFER_EQUAL・passSections・geometry memoryに片側ごとの必須存在を追加。また`buffer_stats`が`I: ` prefixで常時parse失敗しており、memory比較はraw dict同士の恒真に落ちていた —— parseを実フォーマットに修正し、used/buffers値が実際に比較される（99/99 vs 13/24を検出確認）。残る意図的 caution（`nativeTerrainVerified`）は従前どおり |
| **A25** | counterの単位が完了主張と違う | **記録済** | 単位の区別は §7 のcounter表と各行の実測欄で記録済：`imageFilledMirrors`=呼出し/refresh回数（resource数でない）、`outsideWorldPassAccepted`=accept判定前増加、`offTargetCopiedBack`=draw記録数増targetのcopy回数、postChains/postPasses/postTargets/postPipelines=実行chain・記録pass・生成target・生成pipeline数。counter単位をcompletion主張として読む規則は維持 |
| **A26** | S05/S06の専用受入が不十分 | **機構は実contentで検証済・画像受入はheadless計測限界で閉じる** | merge/growth機構は実contentで実働：HUDのformat-2 quadは毎frame同groupへmerge（baseVertex実働→HUD帯0.000一致）、`immediateMaxGroupVertices=68008`でarena成長も実働。専用fixture（`drawMergedQuads`：2独立batch+22002 quad、受理/記録/提出確認）はoverlay event → target 0（default FB）経路で、presented/screenshotのgame FBOには写らない —— auditの「0px」は可視性問題でrenderer欠陥ではなかった。**GL_BACK readback試行も失敗**：headless環境ではpresentedはVulkan swapchain経由でGL default FBはstale buffer（全pixel uniform 179,207,255）。fixtureのdraw自体は受理・記録・提出される（accepted+drawn確認）ので機構正しさは実contentのmerge証拠で担保 —— 専用画像受入はheadless計測限界として閉じる |
| **A27** | 性能向上の根拠がない | **計測済・比較は正直に記録** | pinned Celeritas対同一fixture・AMD。`frameIntervalP50Ms`：既定世界 28.28 vs 16.32 ms、軽世界 6.84 vs 16.05 ms。**tail**（今回計測・既定世界）: 現行 p95=48.63/p99=62.56 vs baseline p95=31.70/p99=39.11 —— p50比1.88x・p99比1.60x、tailはmedianより相対的に健全（dual-render syncは全frame均等に乗る構造）。backend CPU計器: batchCall ~0.3ms・flush ~1ms・handoff 185us —— frame時間の大半はGL側draw+present sync。残: memory・chunk更新の計測 |
| **A28** | 能力判定と純nativeの必須条件 | **文書化済** | **現行 native 実行が要求する実 capability**（codeから洗い出し、fail-closed確認済）：(1) Vulkan loader ≥1.2（`VK.getInstanceVersionSupported`）(2) 物理deviceが Vulkan1.2＋・非CPU・**GL device UUIDと一致**（`GL_EXT_memory_object`でGL側がdevice UUIDを返せること —— 無ければ明示errorで中断、別GPUへのfallback無し）(3) device拡張 `VK_KHR_external_memory_fd`＋`VK_KHR_external_semaphore_fd`（dual-render構造がGL-FBO↔VKをexternal-memory interopで橋接するため必須、欠如はthrow）(4) GRAPHICS+TRANSFER queue family。(5) GL側 `GL_EXT_memory_object`＋`GL_EXT_memory_object_fd`（bridge作成時に確認、欠如はbridge非成立）。optional: `VK_KHR_swapchain`・`EXT_debug_utils`（有れば有効化、必須ではない）。**2GPU受入はこれら要件を満たすGPUでの実測であり、全Vulkan GPUを主張しない**。完全なGL-context-less実行（item 6達成後）は (2)(5)のGL依存が外れることになるが未実装。**2026-10-10 更新（§0.114）**: GL-less flag 経路は `deviceUuids=[]`・`memoryObjectFd=false` のまま起動・提示まで走った（`p548-glless`〜`p559-glless`。capability は `status=UNSUPPORTED_OR_UNAVAILABLE`・`logicalDeviceCreated=false`）—— (2)(5) はこの経路の実行には不要だった。ただし**窓の絵が未解決なので item 6 の達成ではなく**、(1)(3)(4) は引き続き native 実行の要件である |
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

**item 6（GL-context-less window）の残依存棚卸（実測）**: 残るGL使用は(a) **context生成自体**（LWJGL Display.create+OpenGlHelper caps+GlStateManager init — contextなしではGL11.*の任意callが死ぬ）、(b) **per-frame少数のGL draw**（refused batch 59/run・起動直後10 frameの本質的拒否・offTarget copy-back blit 202/run）、(c) **seeded post chainのglGetTexImage/glTexSubImage2D**（off-target imageをchain sourceに直結すれば不要 — silhouette drawは既にnative経由offTarget 145 pass/run）、(d) **screenshot等のGL read**、(e) **engine自身のGlStateManager state call**（数百call site — context除去は全call siteのdivertかstub GLが必要で、LWJGLの`GL.createCapabilities`はpublic FunctionProvider入口を持たない）。**実行順**: (c)を最初に（chain target直結でreadback/copy-back両方消える）→(b)のrefusal 59件の内訳→(a/e)はcontext生成を除去する最終段。

**item 6 更新（2026-10-10・§0.114 / `4bcff8de7`）**: この棚卸しは上の実測時点のもので、いまは次の状態にある。(a)(e) は **stub GL provider + GLFW_NO_API window** に置き換わり、GL context なしで起動・提示まで到達した（`p548`〜`p559`: `CELTIUM_GLLESS stub GL provider installed`・`presentFailures=0`・`validationMessageCount=0`）。(b) の per-frame GL draw は owned image へ行き、shared image はフレームに無い（`gameFramebufferIsShared=false`・`gameFramebufferAdopted=0`・`glless target: no handoff`）。(d) の game screenshot は GL-less では全画素 0 になる（各 run の `screenshots/celtium-validation.png`）ため、fixture は提示画像側で判定するようにした。(c) の seeded post chain はこの fixture では発火していない（`postChains=0`・`postPasses=0`）ので **GL-less では未検証のまま**。中心の残件は提示画像の欠陥で、順序・clear・texture の修正後も `p558`〜`p560` は `FAILED_PRESENTED_IMAGE` のままである。

**item 6 architecture（調査確定・着手済）**: LWJGL 3.4.1の`GL.create(FunctionProvider)`はpublic — context不要でGL capabilitiesをstub providerから作れる（`GL.create(provider)`→`createCapabilities(true)`）。GL-less bootに必要な事前条件と状態: **chain keyをobject identityへ**＝**済**（d65e1ad1b — `inFbId`/`outFbId`/`auxFbIds`/`groupMainFbId`を全て`System.identityHashCode`へ、glow fixtureで56 chain描画確認）。残り: (c) silhouette drawをchain targetへ直結（readback/copy-back消失——但しoff-targetの共有image semantics=scene上へsilhouette描画+compositeの全画面blitで覆い隠し、直結はsilhouette用depth付きtargetが別途必要で、seeded pathのglGetTexImageはrare）、**texture/fbo name生成のstub化**（`glGenTextures`/`glGenFramebuffers`が全て0を返すとtexture registryとchain keyが潰れる — per-name increment idを返すdispatch関数表が必要）、残call site (GlStateManager unmirrored/OpenGlHelper/screenshot/Display/Framebuffer GL ops) のenumerated divert。refusal実測: refusedBeforeAppend 185/run（≈起動直後のみ、format-2 quad主体、0.09/frame）。

**item 6 architecture 更新（2026-10-10・§0.114）**: `GL.create(FunctionProvider)` の stub が入り（`bb271a660` + `4bcff8de7`）、**texture/fbo の name 生成は stub 側の dispatch 表が per-name increment id を返す**ようになった（`nextName`）。`glGetTexImage` は stub が何も書かないため、mirror は stub が保持した upload と game の announce から供給し、内容の無い mirror は uninitialized memory で埋めず拒否する（`readbackByOrigin=none`・`partialMirrorRefusals=0`）。screenshot（(d)）は提示画像の dump に置き換わり、GL-less の run では game screenshot が全画素 0 になることを実測した。残る中心は (c) silhouette 直結ではなく **提示画像の欠陥**（§0.114）。refusal実測は `p559` で refusedBeforeAppend 3/run。

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
`docs/history/quality-and-failure-investigation-2026-10.md`、**GL-context-less bring-up（§0.114・commit `4bcff8de7`）の
証拠と再現コマンドは `docs/history/2026-10-10-gl-less-bring-up.md`**、goal の旧版は
`docs/history/goal-r46-before-2026-10-10.md`。
