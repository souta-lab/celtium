# フレームの window はどこから来るか（2026-11-03、leg `r36win`）

条件: AMD RX 6400（RADV NAVI24）、`celtium-world-accept`（188 mods・`qmagnet`）、
`CELTIUM_LEG_SEED_OPTIONS=.celtium-work/options/guiScale3.txt`、stage `.gv-verify/jars/r35-stage`
（renderer `bc425402fb3be60abc28e059fa327e5b6aa96ee04747b396aff4d2bd3ed387c7`、fixture `6e53864b…`）、
flags `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.frameBudget=true
-Dceltium.vulkan.batchTimings=true -Dceltium.vulkan.drawSiteCensus=true -Dceltium.vulkan.flushReasonCensus=true`。
leg は PASSED（`accepted=true`、exit 0、`fixtureStatus=PASSED`、`gpuRequirementMet=true`）。

**この leg の絶対 ms は使わない。** `batchTimings` と 2 つの stack-walk census が frame を 3.0 → 4.9 ms に
膨らませた（`record` 0.6 → 1.3、`batch` 0.9 → 1.8）。使うのは (a) 窓の**枚数と発生源**、(b) 窓の中の
**相対**配分、(c) 同時に取った `r36a-budget2`（census 無し）との比較。

## 窓の枚数と発生源（`windowOpenSites`、2428 フレーム・86804 窓 = 35.8/フレーム）

| 窓/フレーム | 発生源（最初のバックエンド外フレーム） |
|---|---|
| 13.0 | `Framebuffer…noteRenderTarget` ← `Framebuffer.func_147610_a` ← **`Shaders.renderFullImageInFBO`** |
| 9.2 | `WorldVertexBufferUploader…offerBatch`（pending raw write がある状態での batch offer） |
| 3.2 | `CeltiumGllessHint.glClear` ← `GL11.glClear` |
| 2.0 | `CeltiumFontGlyphCapture.flushBufferedQuads`（`endRenderString` / `openDefaultGlyph`） |
| 1.0 | `Framebuffer…beforeUnbind` ← `Minecraft.func_71411_J`（フレーム末尾） |
| 1.0 | `GlStateManager…clear` ← `EntityRenderer.func_175068_a`（fog clear） |
| 1.0 | `GlStateManager…clear` ← `DrawUtil.sizeDisplay` |
| 1.0 | `RenderSectionManager.renderLayer` ← `CeleritasWorldRenderer.drawChunkLayer` |
| 1.0 | `GlStateManager…clear` ← `EntityRenderer.func_78478_c` |
| 0.35 | `FontBatch.offer` ← `celtium$flushPendingFontBatch` |
| 0.2 | その他（splash・`Framebuffer.func_147614_f`・`ItemRender.createSprite`） |

**最大の発生源は pack 自身の post chain**（`Shaders.renderFullImageInFBO` が 1 フレームに 13 回 FBO を bind する）。
Celeritas arm ではこれらは安い GL 呼び出しだが、Celtium arm では 1 回ごとに 1 窓（≈17 µs）になる。
つまり 1.68× の差のうち ~0.22 ms/フレームは「pack の post chain × 窓の固定費」で、
`refusedBeforeAppend=0`・`offTargetRefusals=none` なので**拒否経路ではない**。

## 窓の中の配分（`CELTIUM_FRAME_PASS_SPLIT`、相対値）

`winRecord` 0.5–0.7 ms/フレーム（35.8 窓 → **1 窓 ≈ 17 µs**）の内訳:

- `wrecLoop`（group loop）0.2 ms = 窓の ~40%。1 窓あたり ~4.7 group を描く。
- `winBeginPass`（`vkCmdBeginRenderPass`）0.0 ms = 無視できる。
- `winBarrier` 0.1 ms（acquire/release の 2 対）。
- 残り ~10 µs/窓が**無名**だった → 本 round で `winPrefix`（遷移＋probe）と `winPassEnd`
  （`vkCmdEndRenderPass`）の bracket を追加した（既定 OFF、`-Dceltium.vulkan.passSplit`）。

## 同じ scene の A/B（census 無し・`frameBudget` のみ）

| JAR | frame | record | batch | present | other |
|---|---|---|---|---|---|
| `bc425402…`（r35a-budget） | 3.0/4.4/5.0 | 0.5/0.6/0.6 | 0.8/0.9/1.1 | 0.2/0.4/0.5 | 1.5/2.9/3.2 |
| `84a9008a…`（r36a-budget2） | 3.0/4.4/4.8 | 0.5/0.6/0.8 | 0.8/1.0/1.0 | 0.1/0.2/0.3 | 1.6/3.1/3.4 |

差は run 間のばらつきの範囲。本 round の変更（item pipeline の 10 回/描画の `nanoTime` を
`celtium.vulkan.bindTimings` の下へ、拒否経路の S10 flush を GL fallback 時のみに）は
**この scene では速度を主張しない**: clock read は ~1.2 µs/フレーム、拒否は 0 件。

## 主張しないこと

- 絶対 ms（計器税込み）。
- 「窓を減らせば 1.68× が消える」— 窓 1 枚の固定費 ~10 µs の**中身**は未帰属（次の round の bracket）。
- validation（この leg は `validation=false`）。画像 gate は PASSED だが、この leg の絵を画質の証拠にしない。
