# legacy glBeginの既定ONと、custom title screenの画像（2026-11-02）

renderer `ee183a74ea463f0824838b7bf64689b9fd5b8ee1e974b2341481828837336641`（rc.26、`main` 252e7c103 + 本差分）。
fixture `8b57aae8c805e838b4485f372ac82286866a61bd8c37c447465b0ba2569c7ac3`。
画像: [Celtium menu](menu-legacy-default-2026-11-02.png) / [Celeritas 参照](menu-capture-celeritas-2026-11-02.png)、
[world（既定ON・AMD）](legacy-default-world-amd-2026-11-02.png)。

## 観測: menu画像quadはlegacy経路でしか描かれない

Supersymmetry の title screen は CustomMainMenu の `lumien.custommainmenu.gui.GuiCustom`。画像は
`RenderUtil.drawCompleteImage`（`glPushMatrix`→`glTranslatef`→`glBegin(GL_QUADS)`→`glTexCoord2f`/`glVertex3f`→
`glEnd`→`glPopMatrix`）と `drawPartialImage`（同じ＋`glGetTexLevelParameteri` でUVを計算）で描かれ、
テクスチャは `ITexture.bind()` = `TextureManager.bindTexture`。Celtium の legacy capture は
`-Dceltium.gless.legacyImmediate=true` の時だけ配線され、既定では `glVertex3f`/`glTexCoord2f` が
`CeltiumGllessNativeTransformer` の `VOID_NOOPS` で no-op だった。したがって：

- 画像quad（banner 10000x450・frame 304x104・button 100x40・backgrounds 1920x1080）は**全て捨てられる**。
- font のラベル・下部の行は `FontRenderer` の native 経路なので描かれる。

r12b-menu の絵（ラベルと下部の行だけ、banner/frame/button/背景なし）はこれで説明でき、
`-Dceltium.gless.legacyImmediate=true` で同じ instance・同じ fixture から banner・frame・button・Discord icon が出た。
修正前の presented menu は [menu-capture-celtium-2026-11-02.png](menu-capture-celtium-2026-11-02.png)（r12b）。

## 計器の読み違いの訂正（r12bの「bindが無い」）

r12b では menu hold 中の `CELTIUM_UNIT_BIND` が font atlas 510 と 513 の96行だけ、
`CELTIUM_ANNOUNCE_EXTENT` が12行だけだったことを「menu画像は bind も upload もされていない」と読んだ。
これは**上限の産物**だった: `CELTIUM_UNIT_BIND` は非panorama 96行・panorama帯 40行、`CELTIUM_ANNOUNCE_EXTENT` は32行、
`CELTIUM_LEGACY_REFUSED` は4行で打ち切られ、hold の時点で既に上限に達していた。今回 legacy ON の run では
`CELTIUM_MIRROR_DEFERRED glTexture=515 size=256x256` のように bind された名前が記録されている。
**r12b の「画像経路に到達していない」は取消し**、根因は「quad が既定で落ちていた」。規約は
[検証の罠](../verification-traps.md#上限付きの計器の行が無いを出来事が無いと読まない2026-11-02-実測)。

## 変更

1. `CeltiumGllessNativeTransformer.LEGACY_IMMEDIATE` の既定を `true` にした（`-D...=false` で従来のno-op構成）。
   既定OFFの理由だった RADV GPUVM fault は rc.24 の retire キュー修正
   （`VUID-vkEndCommandBuffer-00059`）で解消しており、legacy quad は Tessellator と同じ immediate accept 経路を通る。
2. `LegacyImmediateCapture.wholePrimitives`: 完結する primitive が1つ以上ある run は**末尾の不完全primitiveを
   落として**渡す（GLと同じ意味論。5頂点の`GL_QUADS`は4頂点を描く）。落とした頂点は `trailingVertices` で数える。
   完結primitiveが無い run はそのまま渡し、renderer の拒否理由を残す。
3. `VulkanTerrainGpuBackend`: 拒否理由の `count=` が `immediateOfferCount`（offer回数と兼用、代入直後に`++`）を
   指しており**実頂点数+1**を表示していた。`immediateOfferVertices`（形）と `immediateOfferCount`（回数）に分離。
   4頂点の run が `count=5` と出ていた実例がこれ（`count=4` に訂正）。

CPU: `LegacyImmediateCaptureTest` に「5頂点→4頂点 + tail=1」「完結primitiveなしはそのまま渡す」「mode別の切捨て」を追加。

## 実測

AMD RX 6400 (RADV NAVI24) / Intel UHD Graphics 630。scene=terrain・guiScale3 seed・`resources` 5901 files を運搬・
validation 既定ON・presented dump。すべて `problems=[]`・validation clean。

| leg | jar | arm | 判定 | menu |
|---|---|---|---|---|
| r13b-menu-legacy | `69b74549…`（r12b jar） | AMD・`-Dceltium.gless.legacyImmediate=true` | PASSED・validation 0 | banner/frame/icon 描画、背景は黒 |
| r13d-menu-default | `c49a6ae5…`（既定ON・中間jar） | AMD・プロパティなし | PASSED・validation 0 | 同上（r13bと同一の絵） |
| r13c-world-off | `69b74549…` | AMD・switch off | PASSED・validation 0 | （menu holdなし） |
| r13e-world-default | `c49a6ae5…` | AMD・既定ON | PASSED・validation 0 | — |
| r13f-menu-default | `ee183a74…`（最終jar） | AMD・プロパティなし | PASSED・validation 0 | menu presented frame 181・held 49 tick |
| r13g-menu-intel | `ee183a74…` | Intel・プロパティなし | PASSED・validation 0 | menu presented frame 116・held 48 tick・[画像](menu-legacy-default-intel-2026-11-02.png) |

r13f（AMD）の fixture report: `menuCaptureHeldScreen=lumien.custommainmenu.gui.GuiCustom`・
`menuCapturePresentedFrame=181`・`menuCaptureHeldTicks=49`・`menuDumpClientTick=45`・
`sceneCalibrationKeyMatches=true`・`worldFrames=95`。r13g（Intel UHD 630）も同じ絵で、banner・frame・button・icon が
出て背景は黒のまま（`menuCapturePresentedFrame=116`・`menuCaptureHeldTicks=48`・`worldFrames=82`）。
`CELTIUM_LEGACY_IMMEDIATE submitted=1 vertices=4 mode=7 format=2`（プロパティなしで legacy quad が即時経路へ）。

### world は変わらない（判定できる範囲で）

AMD の gated `world` stage を ON と OFF で比べた差は 3.78%（旧jar・r13b対r13c）と 8.95%（最終jar・r13f対r13c）で、
どちらも GUI 文字（FPS表示）・minimap・手に持つitemを含む。`worldFrameGate` は両armとも PASSED。
`tick200` stage は menu hold の有無で world の settle 位置が変わるため比較に使わない（50%差は描画差ではない）。

### menu の残り（断定しない）

- 背景は**黒のまま**。Celeritas 参照の背景（hallway）は出ていない。mean 6.51 / 非黒率 0.0446（参照 32.83 / 0.7509）。
  矛盾しない説明: `GuiCustom.renderSkybox` の panorama は Tessellator 経路で chain-pair target（例 `738040967`）へ
  描かれ、menu の presented frame には合成されていない（`CELTIUM_MENU_GROUP frame=0 ... target=738040967`）。
  根因は未確定で、次の判別は「panorama の最終 composite quad がどの target に描かれ、present にどう出るか」。
- `menuDumpPanoramaTimer=0.0`（停止したpanorama）で、参照と同じ位相ではない。
- banner の文字色は参照より暗いが、背景が黒いため「画像の色」と「背景の欠落」を分離できていない。
  （`supersymmetry:mainmenu/banner.png` の不透明画素の平均RGBは (20.5,15.7,14.8) で、画像自体が暗い。）
- `GuiCustom` の `initGui` が作る 256x256 の `DynamicTexture`（`viewportTexture`）は、このpackでは一度も
  update されない。draw は `no complete native image` で refuse されるが、GL でも alpha 0 で何も出ないため
  絵の差ではない（拒否として数えるのは「損失を見える化」の意味で妥当）。3枚がこれ。
- loading stage の presented dump は両armとも黒で、splash のロードバーはこのlegでは判定できない。

## 次の単位

1. menu背景: panorama の final composite。`CELTIUM_MENU_GROUP`/`CELTIUM_MIRROR_DRAW` の target と、
   `rotateAndBlurSkybox` の FBO 付替え・copy が present の frame image にどう合成されるかを追う。
   背景が出たら、参照と同じcropで banner の色を測る（黒背景のまま色を語らない）。
2. FPS はこのroundでは測っていない（picture leg は性能の証拠にしない）。legacy ON の world は
   per-batch 呼び出しを増やしうるので、次に測る時は idle 窓の paired KPI で switch ON/OFF を同一jarで比較する。
3. legacy 経路の `unrecorded ... stage=3` 拒否が menu で1回出る（4頂点quad）。名前の無い拒否なので、
   次の判別は stage 3 の checkpoint を名指しすること。
