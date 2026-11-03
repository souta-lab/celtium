# 現行 scene の窓はどこから来るか（2026-11-03、round 41、leg `r41a-census`）

round 16 の窓 census は別 JAR・別 scene（35.8 窓/フレーム）だった。round 40 の handoff は
「現行 scene は 23.4 窓/フレームで `rawWriteSampleGate` は発火しない」と書いたが、**その 23.4 の内訳は
測られていなかった**。本 round は現行 JAR で測り、**窓の 56% が pack 自身の post chain** であることを確定した。

## 条件

| | |
|---|---|
| Leg | `r41a-census` — `world`（`celtium-world-accept`、188 mods、`qmagnet`）、AMD Radeon RX 6400（RADV NAVI24） |
| Renderer / fixture | `11537725e75c21d9726bd85ddb01b2ae5190d5ce997999c29f73b236e3947978` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65`（round 40 の stage） |
| Scene | 1280x720、`guiScale3.txt`、`sceneCalibrationKeyMatches=true`、`worldTicksAtEnd=336` |
| Flags | `-Dceltium.vulkan.validation=true -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.frameBudget=true -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.flushReasonCensus=true -Dceltium.vulkan.drawSiteCensus=true` |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED`、assertion failures 0、distinct 76,996 / nonBg 0.990197 / bands 720 / black 0.007469、**run dir 全体で VUID 0 行** |

**この leg の絶対 ms は使わない。** validation と 2 つの stack-walk census が frame を膨らませている
（`winRecord` p50 1.8 ms/フレーム = 窓 1 枚 78 µs。validation 無しの同 scene は 17 µs）。使うのは
(a) 窓の**枚数と発生源**、(b) 窓の中の**相対**配分。

## 窓の枚数と発生源（`windowOpenSites`、1618 フレーム・37,523 窓 = **23.2/フレーム**）

| 窓/フレーム | 発生源（最初のバックエンド外フレーム） |
|---|---|
| **13.0** | `Framebuffer…noteRenderTarget` ← `Framebuffer.func_147610_a` ← **`Shaders.renderFullImageInFBO`** |
| 3.1 | `CeltiumGllessHint.glClear` ← `GL11.glClear` |
| 1.0 | `CeltiumGllessHint.reportRawFramebuffer` ← `GL30C.glBindFramebuffer` |
| 1.0 | `GlStateManager…clear` ← `EntityRenderer.func_175068_a`（fog clear） |
| 1.0 | `GlStateManager…clear` ← `DrawUtil.sizeDisplay` |
| 1.0 | `Framebuffer…beforeUnbind` ← `Minecraft.func_71411_J`（フレーム末尾） |
| 1.0 | `RenderSectionManager.renderLayer` ← `CeleritasWorldRenderer.drawChunkLayer` |
| 1.0 | `GlStateManager…clear` ← `EntityRenderer.func_78478_c` |
| 0.04 | `Framebuffer…beforeUnbind` ← `LoadingScreenRenderer.func_73718_a` |
| 0.02 | `Framebuffer…noteRenderTarget` ← `Framebuffer.func_147614_f` |
| <0.01 | `ItemRender.createSprite`・`Minecraft.func_180510_a`・`WorldVertexBufferUploader` |

**`Shaders.renderFullImageInFBO` は GregTech のもの**（`gregtech-1.12.2-2.8.10-beta.jar` の
`gregtech/client/shader/Shaders.class`。`allowedShader()` = `OpenGlHelper.field_148824_g` &&
`ConfigHolder.client.shader.useShader`）。つまり **GregTech の bloom post chain が 1 フレームに 13 回
FBO を bind し、Celtium では 1 回ごとに 1 窓**になる。Celeritas arm では同じ 13 回が安い GL 呼び出しなので、
この差は Celtium 側だけの固定費（13 × ~17 µs ≈ **0.22 ms/フレーム**）である。

`rawWriteSampleGate` はこの scene でも発火しない（`rawWriteGate=[skips=0 flushes=1]`）。round 16 が
9.2 窓/フレームと数えた `WorldVertexBufferUploader…offerBatch` の site は、**現行 JAR では 1 回/run**。

## 窓の中の配分（`CELTIUM_FRAME_PASS_SPLIT`、p50 ms/フレーム、validation 込み）

| frame | world | gui | flush(inside world) | terrain | winRecord | wrecLoop | winBarrier | winSubmit | winHead | winBeginPass | grpDraw | wfpMiddle |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 4.7 | 3.4 | 1.1 | 2.8 | 0.4 | 1.8 | 1.1 | 0.3 | 0.1 | 0.4 | 0.1 | 0.9 | 0.2 |

`windowSetup`・`winEnd`・`winPrefix`・`winPublish`・`winPassEnd` はすべて 0.0。**窓の固定費は
`winRecord`（= `winHead` + `winPrefix` + `winBeginPass` + `wrecLoop` + `winPassEnd`）と `winBarrier` の
2 つで、round 17 の読み（validation 無しで窓 1 枚 ~17 µs、うち `wrecLoop` 5.6 µs）と形が一致する。**

## 受理経路（同じ leg）

`immediateOffered=358,500`（221/フレーム）、`groupCreateCount=185,939`、`prepareCache=180,892/186,002@1024`
（97.3%）、`acceptedByFormat=[1:1, 2:217069, 4:25884, 5:32, 6:88922, 7:25535]`、
`flushBeforeGlDraw=92,115`（57/フレーム）のうち `flushBeforeGlDrawWithGroups=35,830`（22/フレーム）、
`actualFlushes=35,830`。`uploadFlushes=32`（run 全体）、`deferredSubmits[reclaims=20,196 waits=819]`。

## 本 round のコード（`79ddf8aa7`）

1. **窓 head の重複 lookup を削除**。窓 head は target identity とその recorded attachment を pass の
   サイズ決めに使い、そのあと `celtium$publishFramebufferColorTexture(name)` を呼んでいた。この関数は
   まず逆引き（`identityForColorTexture`）で**その identity を再取得**し、次に順引きで**その attachment を
   再取得**していた。off-target 窓 1 枚あたり 2 lookup、13 枚/フレーム。identity と attachment を渡す
   overload を追加し、名前の再利用を弾くサイズ検査は `celtium$validateColorTextureIdentity` として残した。
2. **`CELTIUM_FLUSH_SITES` を常時出力に**。この行は 12 スロットのカウンタ（`celtium$noteFlushSite`、
   1 配列インクリメント）から作られるが、`-Dceltium.vulkan.flushReasonCensus`（flush ごとに
   `getStackTrace`）の下に隠れていた。**census を回さない leg でも窓の発生源が読める**ようになった。

## 主張しないこと

- 速度の改善は主張しない（2 は計器、1 は 13 窓/フレーム × 2 lookup）。
- この leg は validation 有効なので、画像 gate は PASSED だが絶対 ms は性能の証拠にしない。
- GregTech の post chain が**正しい絵**を作っているかは、この leg では判定していない（窓の枚数だけ）。
