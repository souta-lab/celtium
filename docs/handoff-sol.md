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

**因子の定量（このターン）**: shader診断で「頂点色乗算を無効化」すると地形帯は **212（ほぼ無彩色）**、
参照は130、現行は80。つまり texture×lightmap は一致し、**差は頂点色の項だけ**で現行は参照の0.62倍。
「alpha乗算を無効化」しても不変（400セクションすべてalpha=255）なのでAOの二重適用ではない。
GL地形パスは製品構成ではなく（`Celtium requires a Vulkan terrain backend...` で明示停止）、
同一buildでのGL/Vulkan A/Bは取れない。`CeleritasWorldRenderer` の VANILLA_LIKE/COMPACT 選択は
上流checkoutに無いCeltium側の変更であることも確認した。

**pass別集計（このターンの計器追加）**: `lightDiagnostics` を pass別に集計し、ラベルの誤り（intは `0xAABBGGRR` で
little-endianのバイト順は R,G,B,A）も直した。実測: **solid=(162,171,150,255)**、**cutout_mipped=(63,90,25,254)**。
solidの色(0.635)なら `212×0.635≈137` で参照(125-137)と一致するが、実際の描画は79.6（有効乗算0.369）。
shader内の他項（alpha乗算・Rdh項）は無効化しても不変と確認済み。帯平均は可視geometryの混在に強く依存するため、
**同じpipeline/formatで既知の1枚のquadを描いて厳密な画素を読み戻す**制御実験が必要（次の一手）。

**矛盾の確定（このターンの核心）**: mesherがバッファに書いた色は正しく（GPU保持値を新規stdout
`CELTIUM_GPU_VERTEX` で確認: `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)`）、
pipelineのlayoutも正しい（実行時print: `stride=32 module=terrain_solid_vanilla.vert colorOffset=12
colorFormat=0x25`）のに、**fragmentが受ける `v_Color` は地形帯でちょうど2値 (26,26,26)/(255,255,255)**。
`fragColor = m_color.rgb` / `= v_Color.rgb` / `v_Color = a_Color`（lightmapなし）の3通りすべてで同じ。
各段でjar内SPIR-Vと診断ソースの手動コンパイルがバイト一致することを確認済み。
=> 損失は**頂点バッファとvaryingの間**（入力アセンブリ／属性記述／drawのvertexOffset）。
次の一手は (1) 5属性すべての (location,format,offset) をprint、(2) draw時の vertexOffset/firstIndex/pipeline をprint、
(3) `v_Color = vec4(a_Color.r, a_Color.g, 0.0, 1.0)` で成分ごとの生死を見る、の順。

**追加実測（このターン）**: (1) fragmentで定数色を出すと地形帯の100%がその色＝terrain shaderが帯全体を描く。
(2) `v_Color = vec4(a_TexCoord,0,1)` は81色の滑らかな変化＝**varying経路は正常**。
(3) pipelineへ提出済みの入力状態は `stride=32 / attr1(location=1,binding=0,R8G8B8A8_UNORM,offset=12)` で正しい。
(4) 可視sectionの先頭頂点32バイトは `...2b3e11ff...`＝色(43,62,17,255)の暗い緑で、これも正しい。
(5) GPU保持値も `colour=0xff59bd91` で正しい。それでも `a_Color` だけが2値になる。
次の一手は**属性を `R8G8B8A8_UINT` として読み生の整数を出力**し、shaderが受けるバイトを正規化の曖昧さなしに確定させること。

**属性offsetプローブ（このターンの決定的測定）**: `-Dceltium.vulkan.colourAttrOffset` で a_Color の読み出しoffsetを
振ると、offset 0/16/24 は layout どおりのデータ（位置/UV/lightのバイト）を返し、**offsetは正しく効いている**。
一方 **offset 12（宣言値）だけが可視頂点で2値 (26,26,26)/(255,255,255)**。私が採取した「section #252 の先頭頂点」
(色(43,62,17,255)・material=3) は可視頂点（material=1）とは**別のsection**で、以前の「可視sectionの色」という
読みは誤りだった（docsに訂正済み）。GPU readbackは最初のdrawについて colour=0xff59bd91 を返しており、
arena上のその頂点は正しい色を持つ。readbackをdraw単位に拡張中（1フレームで複数slotを読む経路が未完、
`-Dceltium.vulkan.vertexDiagnostics=true` にゲート済み・通常runでは走らない）。

**【訂正】前ラウンドの「translucent pass が不透明な暗灰色で地面を塗っている」は誤り**。UV先のspriteは
**alpha 197 の青い半透明テクスチャ（水）**で、診断shaderが alpha=1.0 を出力していたため水が不透明に
描かれて地面を覆っていた（診断アーティファクト）。translucent pass の中身は正当な水。

面の明るさ別に「適用色（通常run ÷ 色乗算無効run）」を測ると、上面 (0.405,0.419,0.385) vs 参照相当
(0.703,0.733,0.661) = 比0.57、中間0.61、側面0.62 と、**比が面に依らず一様に約0.6**。
草地だけの比は0.62-0.66、水面は0.56-0.58。つまり面シェーディング差ではなく**一様な係数**。
`VanillaLikeChunkVertex` はCeltium固有（上流に無い）ため、上流はこの色の参照にならない点に注意。
次は「vanilla GLの同じ面が出す色」と比べる。

**（参考・原因判明としていた観測）**: mesh builderが書いた色のsection別ヒストグラム（`vertexColourSamples` の出力に
`distinctColours`/`top=` を追加）で、**診断画像の2値 (26,26,26)/(255,255,255) は translucent pass の頂点色
0xff1a1a1a / 0xffffffff そのもの**だと判明。translucent の alpha は255（不透明）で、**可視地面の上に不透明な
暗灰色が描かれている**。solid pass の色 0xff59bd91 から期待される 0.83×0.35×255 ≈ 74 は通常描画の実測 79.6 と
一致するので solid は正常。GPU readback（32slot）でも arena の色は正しい（0xff59bd91 / 0xff113e2b / 0xffffffff）。
=> 壊れているのは shader ではなく **translucent pass に載るジオメトリとその色**。
次の一手は (a) そのジオメトリが何のブロック/面か（UVと位置）、(b) なぜ alpha=255 の不透明色か、
(c) pinned upstream の mesher と pass 分類・色合成の差分、を順に確認すること。

**vanilla基準（このターンの決定的evidence）**: fixtureに `-Dceltium.validation.vanillaReference=true` を足し、
terrain mod無しで同じframeを撮影した。vanilla GL = mid (146.6,157.1,132.3) / near (139.8,151.4,131.4)、
upstream Celeritas GL = (136.8,146.5,117.2)/(125.0,135.4,117.6)、Celtium Vulkan = (81.0,84.5,74.2)/(79.6,85.5,75.4)。
**upstreamはvanillaの約93%で正しく、Celtiumはvanillaの約0.56倍**＝我々の欠陥と確定。
色乗算無効runの215.7と比較するとvanillaの実効色は0.648≈solid passの詰めた色0.635なのでmesherの色は正しい。
一方こちらの実効色は0.369で、0.6(側面shade)×0.6=0.36と一致し、比が面に依らず一様な事実とも合う。
=> 全faceに一様な≈0.6の余分な係数（AO/brightness項）がmesher側で掛かっている可能性が高い。
次の一手は light pipeline の br と AO を少数quadでdumpし、上面が 1.0×1.0 になっているか確認すること。

**lightmapサンプルの欠陥を発見・修正（このターン）**: vertex shader で `v_Color = lightColor` を出力して実測すると、
地形帯の lightmap サンプルは **(178,178,178)=0.698**（95%）と (223,223,223)=0.875（5%）だった。lightmap の実データは
row13=160 / row14=197 / row15=250 なので、178.5=row13,14の平均、223.5=row14,15の平均＝**UVがテクセル境界**に落ちている。
原因は terrain shader が Sodium 上流の `clamp(uv / 256.0, …)`（256×256 lightmap 用）をそのまま使っていたこと。
MC 1.12.2 の lightmap は 16×16 で頂点値は「レベル×16」なので中心は `(uv + 8) / 256`。**同じプロジェクトの immediate shader は
既に正しい式**（`(vec2(a_LightCoord) + 8.0) / 256.0`）を使っており、terrain 側だけが半テクセルを欠いていた。
両terrain shaderを `(uv + 8) / 256` に統一した。実測効果は **+10〜12%**（mid 81.0 → 88.9、vanilla比 0.553 → 0.606）で、
run verdict は exit 0 / clean / fixture PASSED。**実在する修正だが残差はまだ大きい**。
次の一手は修正後の「色乗算無効run」を取り直して現在の適用色を再測定し、packed な色（solid平均0.635）と比べること。

**texture UVとshadeの検証（このターン・どちらも否定）**: texture UVを半テクセルずらしても画像はほぼ不変
（mid 88.9→86.6、HF 6.2→6.5）。`-Dceltium.terrain.noShade=true`（diffuse項を1.0固定）では**完全に同一**で、
`VintageDiffuseProvider` が可視地形の色に寄与していないこと（＝shadeはquad色に焼き込まれている）が分かった。
残差の定量: 色乗算無効run = 215.7（=texture単体。lightmapはv_Color経由なので一緒に切れる）。vanilla 139.8 を
tex×colour×lm と分解すると tex ≈ 216（我々と一致）、lmは修正後同一なので、**我々の頂点色はvanillaの一様な
約0.58〜0.62倍**（面の明るさ別 0.57/0.61/0.62）。=> 欠陥は「色の合成」（quad色）にあり、texture・lightmap・
diffuse provider は除外済み。次は vanilla-like 経路で quad 色を合成している箇所を特定し、余分な係数
（AOやtint）が掛かっていないかを少数quadの値で確かめる。

**色の出所を確定（このターン・重要な訂正）**: pipeline経路の色合成は `VintageBlockRenderer:215` の
`ChunkColorWriter.EMBEDDIUM.writeColor(tint, light.br)`（EMBEDDIUMはbrightnessをRGBに掛ける／SEPARATE_AOはalphaに入れる。
実測alpha=255と整合）だが、**その経路は実際には使われていない**——`light.br` のヒストグラムを仕込んでも
1行も出力されなかった。実際の経路は `VintageChunkBuildContext.copyBlockData` で、**バニラのBufferBuilderの頂点データを
そのままコピー**している（color=ptr+12、light=ptr+24、無加工）。=> **我々の頂点色はvanillaの頂点色そのもの**で、
色データは0.6倍差の原因になり得ない（前ラウンドの見立てを訂正）。lightmap修正 `(uv+8)/256` はMCの慣習
`level/16 + 0.5/16` と一致することも確認。MC 1.12.2の地形は固定機能パイプライン（2テクスチャユニット×gl_Color）で、
最終色 = tex × lightmap × colour という式の形は我々のshaderと同じ。
=> 残差は「データ」ではなく「適用」側。次は**較正された単一quad実験**（既知テクスチャ・既知色のquadを
terrainと同じpipeline/formatで描き、`texel × colour × lightmap` の期待値と実測画素を厳密に突き合わせる）。

**因数分解の完了（このターン）**: lightmapを1.0に固定しても 88.9→93.2（+5%のみ）＝lightmapの寄与は小さく、
修正後は既に≈0.98で正しい。`v_Color` を（テクスチャalphaを保って）出力すると帯平均 0.445・2645色で、
vanillaデータのpacked mixと一致＝**色の適用は忠実**。texture単体=215.7。vanillaのlightmap内容はGL実テクスチャを
dumpして比較した結果**16行すべて完全一致**。=> 我々の描画は入力と整合（0.445×0.98×215.7≈94≈実測88.9）だが、
**vanillaの146.6は同じ入力から説明できない**（約1.5倍多く掛けている）。データはすべて同一なので差分は「適用」側のみ。
次の一手は「GLの固定機能が我々のshaderに対して余分に掛けているもの」の特定: (a) フレームバッファの色空間(sRGB/gamma)差、
(b) 固定機能のGL_TEXTURE_ENV合成、(c) グローバルなglColor/マテリアル項。まず(a)を画素対応のpower-law fitで判定する。

**画素対応フィット（このターンの決定的結果）**: vanilla→upstream（GL同士）は `upstream = 1.372 × vanilla^0.916`
（log rms **0.013**）でほぼ一致＝比較基盤は健全。vanilla→celtium は `celtium = 2.370 × vanilla^0.727`（rms 0.055）で
**輝度依存の圧縮**（明るい画素ほど比が小さい 0.55→0.46）。項別では fog off が完全同一、LOD 0 で指数不変(0.722)、
mipBias−2 で+3.6%・指数不変、lightmap 1.0 で+5%のみ、shade off 完全同一、UV半テクセルほぼ不変——**どれも説明にならない**。
画像を目視すると、vanillaはブロック境界の白線が明るいのに、我々は**同じ線が暗く滲み遠景にモアレ状の帯**が出る
＝**最も明るい特徴を最も暗く描いている**。コード側は terrain sampler が `maxLod(1.0)`（level 0〜1のみ）＋LINEAR で、
周辺コメントの「0.16-0.28%一致」は過去の別状態の測定なので現在の証拠に使わない。
次の一手は `maxLod` を上げて（level 2以降を許可）指数と遠景の帯を再測定し、併せて我々とGLのmip各levelを数値比較すること。

**upstreamとの対応画素比較（このターン）**: 格子（明るいブロック）は vanilla (192.0,199.8,187.5) / upstream
(171.9,178.7,167.8) / celtium (108.7,112.6,106.3) で比 **0.632**、地面は (113.6,119.4,105.5)/(101.5,106.7,94.4)/
(69.8,72.9,65.5) で比 **0.688**。=> **全体に一様な約0.66倍**（格子固有ではない）。松明は upstream の最も明るい画素が
(251,241,194)（暖色の炎）だが celtium は (201,204,196) の灰色で、**炎（アニメーションテクスチャ）が描けていない**
（plan優先順位4のsprite animation未移植と一致する独立欠陥）。`forceCompactFormat` のA/Bはレイアウト不一致で
青い画像になり不成立だが、**その壊れた画像でもfixtureはPASSED**だった（verdictが画像の正しさを検出しない実例）。
**機構**: `ChunkBuilderMeshingTask` は mod pipeline と vanilla dispatcher を呼び分けるが、`writeGeometry` の
brightnessヒストグラムが一度も出ない＝**全ジオメトリが vanilla dispatcher 経由**。よって頂点色は MC が焼いた色で、
`VintageBlockRenderer` の tint×light.br は使われていない。次の一手は vanilla 参照run から実際のチャンク頂点色を
dumpし、我々の同じ頂点色と数値比較すること（差分→データ側、一致→適用側）。

**根本原因の確定と修正（このターン・最重要）**: `ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER` の既定が
「dev環境 or 明示プロパティ」で、**本番jarでは false** → 全地形がゲームの `BlockRendererDispatcher`（遅いモデル経路）
でメッシュ化され、その色は mod の `VintageBlockRenderer`（`tint × light.br`）より約0.66倍暗かった。
`-Dceleritas.useVintageFastBlockRenderer=true` のA/Bで mid帯 88.9 → **136.5**（upstream 136.8 / vanilla 146.6）、
near帯 88.9 → **133.1**、verdict は exit 0 / validationClean / PASSED。brightness ヒストグラムも出るようになり
`VintageBlockRenderer` 経路が実際に使われていることを確認。**既定を「有効（プロパティで旧経路に戻せる）」に反転**し、
修正後の既定で再測定して同じ値を確認した。
画素対応フィットでも確認: 修正前 `2.370 × vanilla^0.727`（rms 0.055）→ 修正後 **`1.475 × vanilla^0.915`**（rms 0.037）で、
**upstream の `1.372 × vanilla^0.916` と指数がほぼ同一**（圧縮が消滅）。画像の目視でも明るいグリッド線・緑の地面・
青い空が参照と一致することを確認した。なお本修正はメッシュ化経路自体を変えるため、**これ以前の画像受入は
「遅い経路でメッシュ化された状態」のものである**点に注意（以後は新しい既定で取り直す）。
新既定での受入: AMD mid (136.5,140.9,130.4)/near (133.1,140.0,131.2) exit 0 clean PASSED、
**Intel mid (136.6,141.0,130.5)/near (133.0,139.8,131.2) exit 0 clean PASSED 24.6s**（両vendorで帯がほぼ同一）、
AMD/Intel の target受入も exit 0。pinned Celeritas との再比較（p32-glref-r434 vs p32-celtium-r434）は
厳密画素一致で71.02%差だが、**平均絶対差は10.92/255（約4%）**まで下がり、**最良一致が dx,dy=(0,0)** ＝位置ずれ無し、
**目視でグリッド線・地面・空・松明が参照と一致**（松明も描画されるようになった）。残差は地面テクスチャ細部に限られる。
残差の性格: 同一runペアの帯別解析で、平均絶対差は horizon 12.54 / far 10.22 / mid 10.92 / near 10.76 / closest 11.26、
**全体平均 8.00/255** と**画面全体に一様**（局所欠陥ではない）。**HF は参照の 0.80〜0.88 倍**＝我々のテクスチャが15〜20%滑らか。
`mirrorGlMips=true` は数値不変（7.98 / HF 6.41）で、しかも **exit 1 / validationClean=false**（fixtureStatusはPASSED）＝
検証として不成立のため新しい失敗証拠として保存（p48-mips-r435）。atlas用サンプラは既にNEARESTなのでフィルタは原因でない。
ノイズ床を測定: 参照同士 0.22 / 候補同士 0.13（HF比 1.000）で、参照vs候補の 8.00 / HF 0.840 は**再現性のある実差**。
mip仮説は否定（HF: 参照7.63 / 候補6.41 / mipBias-2 6.90 / **LOD 0固定 6.90** / mirrorGlMips 6.41）。
atlas HF の差（vanilla run 34.77 vs mod導入run 32.01）は `TextureAtlasSpriteMixin.embeddium$processTransparentImages`
の透明画素置換（alpha 0 のまま）によるもので**描画には効かない**。残差は平均8.00/255（約3%）・HF 0.84・
幾何エッジ幅は参照と同一。画像忠実度は「構造・明るさとも一致、細部の微差のみ」の状態なので、
次の優先は監査バックログ（失敗経路・coverage・texture所有）へ移す。特に**松明の炎（アニメーションテクスチャ）が未描画**なのは独立の欠陥で、
sprite animation の移植を進める。

**失敗経路の実機検証（このターン・未完）**: `-Dceltium.vulkan.failSubmits=2` で submit 失敗を注入すると
**exit 124（duration cap）でハングし fixture report が MISSING**。ログは
`accepted geometry retained; renderer must be rebuilt` → `beginFrame() ... previous frame is still open` →
`Game crashed!` の連鎖で、**失敗時に frame が開いたまま残る**ため次フレームで別例外→クラッシュ→ヘッドレスが停止する。
`VulkanFrameTarget.abandonFrame(reason)`（GPU作業なしで frame を閉じる・冪等）を追加して backend の2経路から呼んだが、
**再実行でも exit 124 / "still open" ×2 のままで未解決**（frame を開く経路が他にもある。bridge の beginFrameForPresent 系を疑う）。
失敗再現は p49-failsubmit-r437（修正前）/ p49-failsubmit2-r437（修正後）に保存。

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
