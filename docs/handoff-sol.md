# Sol 集中修正の引き継ぎ（2026-10-10）

このターンで扱った最優先課題は **format 2（POSITION_TEX_COLOR）を既定の受付対象に加えると、`vkBeginCommandBuffer` の外で
`vkCmdBindPipeline`／`vkCmdBindDescriptorSets` を記録したという validation エラーが約70件出る** というものだった。
結論から書く: **原因を特定し、記録を正式なコマンドバッファ・ライフサイクルへ載せる修正を実装した。AMDで再現と受入が取れ、
通常の色pattern受入と「受理後・submit直前に失敗させる」試験の両方が通った。** Intel は最終artifactで実行中。

## 1. 根本原因と修正箇所

### 原因（ソースで確定）

- **backend は自分専用の永続 `VkCommandBuffer` を持つ**（`createCommandResources` が自前の pool から1本確保）。
  この handle は submit 後も **null にならない**ため、`drawPendingImmediate` の `commandBuffer == null` ガードは
  何も検出できない。
- `flushPendingBeforeGlDraw()` が **`drawPendingImmediate()` を直接呼んでいた**。つまり begin も render pass も
  acquire/release も submit も無いまま、pipeline の bind／descriptor／push／vertex/index／draw を記録していた。
  format 2 を受理すると、この経路に実バッチが入るため illegal recording になる。
- 前ターンの `VulkanContext.isRecording()` は **bootstrap 用の別 handle（context の pool から都度確保）** を追っており、
  backend の handle を表せない。「1つの boolean で別々の command buffer を混同しない」という指摘のとおりである。
- validation の「70件」は **7つの VUID が `duplicate_message_limit=10` で抑制された結果**であり、不正描画の件数ではない
  （`.celtium-work/runtime/p30l-format2-amd/validation-console.log` の 374–380 行）。

### 修正

| 変更 | 内容 |
|---|---|
| `VulkanCommandBufferState`（新規） | backend の handle 1本のライフサイクルを追う: `INITIAL → RECORDING → RENDER_PASS → EXECUTABLE → PENDING →（fence確認後）EXECUTABLE`。target も保持し、pass 外・別 target の draw を例外にする |
| `VulkanContext` | 誤った所有者だった context 全体の `recording` フラグと `isRecording()` を削除。bootstrap handle は従来どおり呼出し側が begin/end する |
| `flushImmediateWindow()` | **同期・同一targetの LOAD window** に作り直した: `beginFrame → begin → recordAcquireBarriers → pass(begin/draw/end) → recordReleaseBarriers → end → submit → fence確認 → endFrame`。古い target の受理済みgroupを、GLがまだその FBO を bind している間に描き切る |
| `flushPendingBeforeGlDraw()` | 直接描画をやめ、render pass 内なら pass 内 draw、それ以外は `flushImmediateWindow()`。失敗は握り潰さず `gpuStalled` を立てて例外を投げる（GLへの二重描画や黙殺をしない） |
| `prepareImmediate()` | pipeline と descriptor を **append 前に**用意して group に持たせる。A08 の「受理前preflight」を format 2/3/4/5/6/7/8/9 と texture にも広げた |
| group 所有権 | `immediateGroups → recordedImmediateGroups（記録済み）→ retiredImmediateGroups（submit成功）→ fence確認後に解放`。失敗時は queue へ戻し、記録数を戻す |
| `waitForIdle()` | fence timeout・error で **false を返し `gpuStalled` を立てる**（以前は true を返し、pending buffer を reset していた）。`settleGpu` も stalled なら例外 |
| 順序境界 | `bindFramebuffer` HEAD（旧targetがまだ bind 中）、`flushBeforeClear`、`viewport` HEAD、`announceTextureUpload`／`announceAnimatedUpload`／`announceDynamicTexture` の各入口で flush |
| `captureGlMatrices()` | world 外（GUI/overlay/別FBO）で **mirror の ortho/modelview を snapshot** する。従来は return して前の world 行列が残り、実GUI geometry が clip されていた |
| `pendingImmediateIndices` | 退役時に減算していなかったため全 group が閾値超過で即 flush されていた。減算を追加 |
| `VulkanImmediateFormats` | `DEFAULT_ACCEPTED = "1,2,3,4,5,6,7,8,9"`（format 2 を含む） |
| `VulkanDynamicMesh`／`VulkanHostBuffer` | 頂点・index の容量を**両方確保してから**書き込み（部分書き込みを残さない）。`VulkanHostBuffer` の成長は新規作成→コピー→旧解放の順 |
| `VulkanTextureRegistry.invalidateRenderedTexture()` | FBO書き戻し後、古い CPU announcement を破棄して GL から読み直す（古い画像で上書きしない） |
| off-target scaffold | 常に `return false` していた旧 `VulkanOffscreenTarget` 経路（空 pass を clear/copy していた）を削除。実描画は上記 window が担う |
| Mixin | `FramebufferMixin` に `func_147609_e`（unbind HEAD）を追加、bind/clear/viewport の順序失敗を握り潰さない |

## 2. 検証条件・artifact・結果・限界

### 再現（修正前）

`.celtium-work/runtime/sol-cb-base-amd/`（HEAD `e90e0c848` 相当の jar を `.celtium-work/sol-command-buffer/base-artifacts/`
へ SHA-256 付きで固定して実行）:

```
exit 1 | validationClean false | validationMessageCount 70 | fixture PASSED
vkCmdBindPipeline/vkCmdBindDescriptorSets/vkCmdPushConstants ... was recorded, but vkBeginCommandBuffer() was not called
```

### 受入（修正後・AMD RX 6400）

| run | 結果 |
|---|---|
| `sol-cb-fixed2-amd` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0 |
| `sol-cb-final-amd` | 同上（最終ビルド） |
| `sol-cb-final-retry-amd` | 同上＋`retries=1`（fixture内で submit 直前失敗→再試行が実際に走った） |

fixture が確かめていること（`celtium.validation.commandBufferTargets=true`）:

- main に青 quad を1件 pending させ、別FBOへ bind する瞬間に **旧mainが flush される**（`mainWasPending=true`）
- 別FBOで赤・緑を**別々の begin/draw**で描き、mainへ戻る瞬間に **別FBOが flush される**（`secondaryWasPending=true`）
- main にもう1件（黄）を描いて flush
- 受理・記録・submit・完了が **すべて4件**、pending 0、invalidDraws 0、off-target 2件
- 画像: main の青・黄、別FBOの赤・緑が正しい位置にあり、**両targetの未更新領域は1ピクセルも変わらない**
- 3枚のPNG（`screenshots/celtium-command-buffer-{main-before,main,secondary}.png`）とハッシュを
  `.celtium-work/sol-command-buffer/acceptance-summary.json` に固定

### 純ロジック受入

`celtium-vulkan/src/test/.../VulkanCommandBufferStateTest.java`（4件、`BUILD SUCCESSFUL`）:
別handleの独立、pass外・別targetの draw 拒否、PENDING 中の reset/begin 禁止、pass を開いたままの end 禁止。

### clear順序の受入（追加）

「clear→描画」は既存の赤・緑矩形で確認済みだったが、監査が求める **「描画→clear」** を追加した
（`sol-cb-clearorder-amd` / `sol-cb-clearorder-intel`）: 受理済みbatchを1件pendingさせてから
`framebufferClear()`（実clear）を呼ぶと、clear境界で accepted=recorded=submitted=completed が各+1・pending 0 になり、
clear後の読み戻しは **全ピクセルがclear色**（描いた矩形も消滅、不一致0）。受理済みgeometryがclearの前に描かれ、
clearがそれを消すという順序が実geometryで確認できた。PNGは
`screenshots/celtium-command-buffer-secondary-cleared.png`。

### depth順序の受入（追加）

同じ矩形に**近傍batch→遠方batch**の順で描き、近傍だけが残ることを確認した
（`sol-cb-depth5-amd` / `sol-cb-depth-intel`）: 中心ピクセルは近傍の青、rectangle不一致0、
`farLeakedPixels` 0、4段カウンタが+2、pending 0。D24→D32→D24の往復と、GL準拠のdepth順序
（`glOrtho(near=-1,far=1)` は -z 方向を見るため `z=+0.5` が近傍）が実geometryで確認できた。
PNGは `screenshots/celtium-command-buffer-secondary-depth.png`。

**途中の2つの失敗はどちらもfixture側で、実装の欠陥ではなかった**（記録として残す）:
1. depth相の最初の失敗は `Framebuffer.framebufferClear()` が最後に `unbindFramebuffer()` するため、
   clear後のquadが**既定FBO宛**になっていたこと。backendは契約外として**描かずに拒否**し、
   これは「GLへfalseで返したofferをVK描画済みとしない」が実際に効いている例である。
2. 2度目は期待値のz符号が逆だった。Vulkan側のdepth remap（GLの[0,1]をVulkanの[0,1]へ）は
   GLと一致しており、fixtureの期待値を正した。

### 限界（未実施・未検証）

- **Intel UHD 630**: **受入完了**。`sol-cb-clean-intel` が exit 0 / clean / msgs 0 / fixture PASSED /
  `passed=true` / 4/4/4/4 / off-target 2/2/2 / 全ピクセル不一致0 / worldFrames 155、注入run
  `sol-cb-clean-intel-retry` が `retries=1` で同じ受入。通常fixture `p31d-intel-clean` は worldFrames **154**
  （変更前は114・129）で PASSED、即時2155件が記録=submit=完了、invalid 0。
- **重要な訂正**: 途中のIntel 480秒タイムアウト2件（`sol-cb-final-intel2`・`p31b-intel-normal`）は
  **同時にAMD runを走らせていた競合が原因**で、Intelの退行ではなかった。単独runは24〜27秒で完走する。
  **測定は必ず単独で行う**（この誤診を記録として残す）。
- sample数>1のtargetと resize中の受理は未検証（別FBOの depth 順序と clear 順序は受入済み）。
- 画面（window/compositor）への present 到達は相変わらず未確認（環境の X が 0x0）。
- 失敗経路で確認したのは **submit前の失敗**のみ。submit成功後の `endFrame` 失敗、fence timeout、
  device lost は分岐を実装したが **実機注入していない**。
- `recordImmediateOnlyPass` は acquire/release を呼出し側（window）で記録する形に変わった。terrain の通常 pass は
  従来どおり `render()` 内で acquire/beginPass/…/endPass/release を行う。

## 2.5 画質比較の現行測定（このターンで追加）

`scripts/run-gl-comparison.sh amd r418` → 参照(pinned upstream GL)と候補(Celtium)を同一条件で実行し
`compare_validation.py` で比較。両run clean/PASSED、framebuffer・camera・pass数一致、しかし
**画像は70.06%のピクセルが違い、最大チャンネル差209**。領域別では**空は完全一致**、**地形だけ約0.6倍暗い**。

切り分けで **lightmap mirrorは正常**（GLの実lightmapとmirrorの最終アップロードが一致 (250,250,250)）、
**AO表は上流と完全一致**、shaderは `a_Color * lightColor` のみ。測定値 0.6 ≈ 朝のlightmap 0.61 × 正午0.98 から、
**メッシュ構築時にlightmapが頂点色へ焼き込まれている**可能性が高い（chunkは再構築されないので朝の明るさで固定）。

**「メッシュ構築時にlightmapが焼き込まれる」仮説は実験で反証**（`-Dceltium.validation.reloadAtTick=120` で
正午固定後に全chunkを再構築しても地形帯は 79.6/85.5/75.4 で完全不変）。回帰fitは `y ≈ 0.42x + 20`・残差sd≈19で
単純な乗算ではなく、高周波エネルギーは参照7.1に対し候補5.5（約22%滑らか＝mip/補間の差）。

**このラウンドで反証した候補（8件・すべて実測）**: atlas mirrorの暗さ（GLの実atlasとmirrorの平均が完全一致、
差はアニメspriteの56テクセルのみ）、lightmap除数（過去roundで修正済み・サンプルを1.0に固定しても画素不変）、
lightmap mirrorのstale（mirrorの最終=GLの実測=(250,250,250)）、頂点色への焼き込み
（reloadAtTick=120で全chunk再構築しても不変）、生成mip鎖（mirrorGlMips=trueでも不変）、AO表（上流とdiff無し）、
fog（色は明るい青灰で近傍帯に無関係）、void（暗部は778色のテクスチャ）。残る手がかりは
**同一座標の比が0.42〜0.65と面ごとに変動**することで、面シェーディングの適用差を示す。

診断用に追加したもの: `-Dceltium.vulkan.dumpTexturesEvery=true`（mirrorのdumpを毎回上書き＝最後のアップロードが見える）、
fixtureの `gl-lightmap-live.png`（GLの実lightmap）と `gl-atlas-live.png`（GLの実atlas）をcapture時に読む、
`-Dceltium.validation.reloadAtTick=<tick>`（chunk再構築実験）、`scripts/run-gl-comparison.sh`（直列比較）。

## 2.6 地形の頂点データ実測（このターンで追加）

`-Dceltium.vulkan.vertexColourSamples=N` を追加し、地形セクションのアップロード時に**実際の頂点データ**を出すようにした。
結果: 地形は vanilla 32バイトlayout（pos float3 @0、色 @12、UV float2 @16、light short2 @24、法線 @28）で、
alpha は全頂点255、頂点色は明るい緑（solid平均 136,158,108）、UVはsprite座標として正常（0.125,0.469）、
light short は `light×16`（0xE0=sky14×16）で `/16` と `/256` は同じテクセルに解決する。
`textureLod(...,0.0)` でbiasを無効化しても画像は不変（mip/LOD否定）。

決定的な不整合: **頂点色×lightmap = (133,155,106) が参照の実測 (136.8,146.5,117.2) とほぼ一致**するのに、
候補はその0.6倍で、しかも候補の画素値は参照の輝度と単調対応しない（ref133→87、ref103→43）。
つまり**テクスチャ内容が違う**。atlas level0 は GL の実atlasと平均一致するが、比較したのは registry 側の mirror で、
terrain が bind する `this.atlasTexture` が別オブジェクトである可能性は未検証。

**このターンで追加確認した2点**: (1) 参照がlightmapを適用していること（`CELERITAS_NO_LIGHTMAP` は `pass.hasNoLightmap()`
の時だけ定義されるのでSOLID passでは未定義）。(2) ビルド健全性 — jar内 `terrain_solid.frag.spv` のSHA-256が現在のソースの
手動コンパイルとバイト一致するので、shader変更は正しく反映される（診断実験の結論は有効）。

**比較手法の検証（このターンの最重要の気づき）**: 同一ビルド・同一flagの2 runでも**24.8%のピクセルが異なる**
（最大チャンネル差180）。参照との70%差にはこのrun間ノイズが含まれるため、**ピクセル一致率は受入指標に使えない**。
帯平均は再現する（候補の複数runが79.6/85.5/75.4で一致、参照は125.0/135.4/117.6）ので明度差は実在。
atlasは dump にオブジェクト同一性を付けて確認し、GL texture 9 の mirror は1つだけで内容もGLと一致 ✅。

## 3. 残る問題と、次に実装する具体的な一手

1. **Intel 受入を閉じる**（上記コマンドの完走を確認。落ちたら `logs/latest.log` の
   `CELTIUM_COMMAND_BUFFER_TARGETS` と `validationFirstMessages` を読む）。
2. **1フレームあたりのブリッジ往復を減らす** — **1回目実施済み**: `viewport` 変更のflushを削除（キュー済みgroupは
   常に現targetの全viewportで描かれるため不要）し、テクスチャ告知は **キュー内groupがそのtextureを参照する場合のみ**
   flush（`queuedGroupUsesTexture`）。実測 `orderPreservingFlushes` 633→388、target受入 exit 0、通常fixture PASSED、
   記録=submit=完了=2829、invalid 0。**残る設計案**（同一targetの連続window合体、GL clearをwindow内イベント化、
   main targetはフレーム自身のpassで描く）:
   (a) 同一targetの連続windowを合体する、(b) GLのclearを「windowを強制する境界」ではなく
   「次のwindow内で適用するイベント」として保持する、(c) main targetの受理済みgroupはフレーム自身の
   terrain pass で描く（`recordRenderPass` は既に描いている）。いずれも順序の正しさを保つ条件を
   docsへ明記してから実装する）。
3. **地形の暗さ（優先順位3の本命）**: 候補を9件実測で否定済み（lightmap mirror/焼き込み/mip鎖/AO表/fog/void/atlas mirror/除数/LOD）。
   残るのは **`u_BlockTex` のサンプル内容**。次は terrain が bind する `this.atlasTexture` の内容を dump して
   GLの実atlasと比べ（registry mirror との比較では同一性を証明できない）、あわせて**同じbuildのGL terrain frame**と
   比べる（初回は起動時 `getSkyColorBody` NPE で落ちたので、その原因も見る）。
4. **submit後失敗の実機注入**を足す: `submitCommands()` 成功後・`endFrame()` 前に例外を注入する property を作り、
   「二重signalingしない・資源を壊さない・stallして可視に失敗する」ことを1 run で示す。
3. **別FBOの depth/clear 依存**を小さな場面で確認する（depth test 有効・clear 後の上書き）。
4. 監査 A04/A05 の残り（atlas stitch 時の全面データ供給、sprite単位mip）と A12/A13（state model、current RGBA）。
5. ~~通常fixture回帰の確認~~ **実施済み**: `p31a-format2-default-amd` が exit 0 / clean / msgs 0 / fixture PASSED、
   `worldFrames=228`、`acceptedByFormat=1:0,2:217,3:0,4:0,5:214,6:1394,7:1136,8:0,9:0`、
   **recorded=submitted=completed=2961**、invalidImmediateRecords 0、retries 0。
   → AMDでは format 2 を既定受理しても回帰せず、受理した即時バッチが記録・submit・完了まで完全に一致する。

## 4. 未コミット変更

このターンの変更はすべて **未コミット**（`git status` の11ファイル＋新規3ファイル）。内容は上表のとおり。
コミットは次の単位に分けると読みやすい:

1. `VulkanCommandBufferState`＋テスト＋backend のライフサイクル／window／preflight／所有権
2. fixture（`ValidationClient`）＋runner script
3. 文書（`docs/plan.md`、`docs/scope-and-limits.md`、本ファイル）

ビルドは `:celtium-vulkan:test` を含めて成功済み。**ゲーム内受入は AMD の3 run のみ**である。
