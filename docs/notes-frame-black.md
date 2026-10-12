# Round 247 - which full-screen GUI-stage draw can black the whole frame image

Source reading only; no leg, no code edits. Paths: `.celtium-work/mc-sources/...` (vanilla, via `scripts/mc-sources.sh`)
and the Forge unpacked tree for `GuiIngameForge`.

## Candidate 1 - the vignette: **full-screen, but it runs BEFORE the HUD**

- `net/minecraft/client/gui/GuiIngame.java:1085` `renderVignette(float lightLevel, ScaledResolution scaledRes)`;
  the blend is `GlStateManager.tryBlendFuncSeparate(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR, ONE, ZERO)` at
  `:1106`, then `bindTexture(VIGNETTE_TEX_PATH)` (`:1116`) and a full-screen quad
  `bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX)` (`:1119`) with `pos(0, scaledHeight, -90)` etc.
- **Correction to the candidate's premise**: the destination factor is `ONE_MINUS_SRC_COLOR`, not `SRC_COLOR`, so the
  result is `dst * (1 - src)`. An **unfilled (zero) mirror** therefore leaves the image unchanged (`dst * 1`), and it is
  a **white** mirror that would black it (`dst * 0`). The measured `state=[blend=1 src=0 dst=3 ...]` is consistent with
  `ZERO` / `ONE_MINUS_SRC_COLOR`.
- Order: Forge's `GuiIngameForge.func_175180_a` calls the vignette at `:125` (`if (renderVignette && Minecraft.func_71375_t())`),
  then the hotbar at `:142`, the crosshair at `:148`, the HUD text at `:173`. A vignette that blacked the image would
  do so **before** the hotbar and crosshair, so the HUD would still be visible on top of it - the measured result is a
  single black colour **including** the HUD, which this order does not produce.

## Candidate 2 - the game's finished-frame composite: **full-screen, and it runs AFTER the HUD**

- `net/minecraft/client/Minecraft.java:1234-1237`: `this.framebuffer.unbindFramebuffer(); ... this.framebuffer.framebufferRender(this.displayWidth, this.displayHeight);`
  inside `runGameLoop`, i.e. **after** `EntityRenderer.updateCameraAndRender` (which contains the HUD bracket
  `GuiIngameForge.func_175180_a`).
- `Framebuffer.framebufferRender` draws one full-screen `POSITION_TEX_COLOR` quad sampling the framebuffer's colour
  texture. Under GL-less that draw is issued with no framebuffer bound (identity 0), which per the round-242 routing
  table attaches the **frame image** - the image the present blits (`VulkanTerrainGpuBackend.java:5088`).
- So if its sampled mirror is the default pair (measured `defaultPairHotbarBand=(0,0,0)`, i.e. black), the composite
  paints the frame image black **after** the HUD has been drawn - which is exactly "one colour, mean 0.0, HUD included".

## Candidate 3 - other full-screen GUI-stage draws

- The HUD's other stages are not full-screen overwrites: the hotbar (`GuiIngameForge:142`), the crosshair (`:148`) and
  the HUD text (`:173`, `renderHUDText`) draw in bands. `renderPotionEffects` is not a full-screen quad. The only other
  full-screen GUI-stage quad is the vignette above.

## Inside `func_175180_a` versus after it

| draw | where | order relative to the HUD |
|---|---|---|
| vignette (`GuiIngame.renderVignette`) | `GuiIngameForge.func_175180_a:125` | **before** hotbar (`:142`) and crosshair (`:148`) |
| hotbar / crosshair / HUD text | `func_175180_a:142/:148/:173` | the HUD itself |
| `Framebuffer.framebufferRender` | `Minecraft.runGameLoop:1237` | **after** `func_175180_a` entirely |

## Most likely, and the confirming evidence

**Candidate 2** - `Framebuffer.framebufferRender` at `Minecraft.java:1237` - is the single draw the sources make most
likely to black the *whole* image including the HUD: it is full-screen, it runs after the HUD, and under GL-less it
lands in the frame image while sampling a mirror that is the default pair (measured black). Candidate 1 is full-screen
but runs before the HUD and, with an unfilled mirror, is a no-op rather than a blackening.

**One line of evidence from the existing logs**: in `r212-world-hud22/23`, find the last full-screen `fmt=2` group of
the frame whose `texture=` is the game main framebuffer's colour texture name (`CELTIUM_GROUP_BOUNDS` or
`CELTIUM_DRAW_SITE` lines carry `target=`/`texture=`/`fmt=`). If that group is drawn **after** the HUD's groups and its
sampled mirror is the default pair, candidate 2 is confirmed. If the sources cannot settle it further, the one-line
probe is the same line with the draw-site frame filter set to the frame that turns black.
