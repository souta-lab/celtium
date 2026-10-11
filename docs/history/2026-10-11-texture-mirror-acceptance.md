# Texture mirrors under GL-less — the refresh path, measured

Owner: **texture-mirror** (task-4). Runs quoted by jar hash; every picture is in
`evidence/texture-mirror-20261011/`. Read `../../handoff-vanilla-completion.md` first.

## The answer to the question the task asked

The missing GUI panel was **not** the texture mirror. The mirror of the panel sheet was
filled from the game's own announcement all along and reported whole content; the panel was
invisible because the colourless format-6 pipeline (`Gui.drawTexturedModalRect`: panel, slot
backgrounds, hotbar frame) was built as `POSITION_TEX_COLOR` and read the vertex buffer at
24-byte stride over 20-byte data. `pipelines`' `allocate()`/`build()` split fixed that, and the
panel appeared with the mirror path untouched.

The one-flag-apart control on a single jar says so directly:

| leg | jar | flag | panel in the presented frame |
|---|---|---|---|
| `tm2-on` | `77f110f92a4907cd35ec` | default | present |
| `tm2-off` | `77f110f92a4907cd35ec` | `-Dceltium.vulkan.glessKeepsInvalidations=false` | present, **bit-identical** in every panel region |

`tm2-off` restores the pre-WIP invalidation behaviour (registry stale mark + the direct
atlas/light-map refresh, `CELTIUM_ANIM directMirrorRefreshes=3/6/9… readbacksSkipped=7/13/19…`,
`invalidationsKeptNoGl=0`) and the picture does not move: mean absolute difference **0.000**
over `panel_left (360,190,470,600)`, `slot_grid (400,210,900,470)`, `panel_bottom
(380,470,920,540)` and `tooltip (640,300,830,365)`; the hottest strip anywhere on the panel is
0.112. So the GL-less invalidation change is picture-neutral and the pipeline fix alone
accounts for the panel.

## The refresh path as it works (GL-less)

1. `TextureUtilMixin.celtium$invalidateUploadedTexture` (HEAD of
   `TextureUtil.uploadTextureImageAllocate`) calls `announceTextureUpload(id, image)` and then
   `invalidateTexture(id)`.
2. `announceTextureUpload` → `VulkanTerrainGpuBackend` → `VulkanTextureRegistry.announceImage`:
   the `BufferedImage` is stored and `announcedGeneration[id]` is bumped.
3. The original body then runs `allocateTexture` → `TextureUtil.deleteTexture` → the
   `func_147942_a` hook → `onGlTextureReallocated(id)` → `VulkanTextureRegistry.forgetTexture`:
   the mirror and its entry are dropped and retired, and the **unconsumed** announcement is
   retained (`announcementUnconsumed`), so the delete-and-reallocate inside the upload does not
   throw the game's own pixels away.
4. `invalidateTexture` keeps the mirror under the stub (`textureInvalidationsKeptGlless`,
   `invalidationsKeptNoGl=`): there is no GL texture to re-read, and the announcement that
   arrived with the upload is the new content.
5. The next bind of that GL name goes through `VulkanTextureRegistry.get`: the announced branch
   converts the image (`fromImage`), uploads it into the mirror **in place**, marks whole content
   and consumes the generation. The mirror the descriptors already name keeps its image, view
   and generation, so no descriptor key moves.

Measured in `tm2-on` (`CELTIUM_MIRROR_SOURCE`, first line per name and source):

- `id=129 source=announcement size=256x256 content=opaque=26502/65536 firstOpaque=0xff000000
  stale=false obj=2fe5003 full=true` — and `mirror-gl-1-256x256-l0-n1-obj2fe5003.png` is
  `creative_inventory/tab_items.png`, the panel with the slot grid and scrollbar.
- `id=124 source=announcement 256x256 opaque=24224/65536 stale=false full=true` — dumped as
  `tabs.png` (SEL/UNSEL tab strip).
- `id=12 source=capture 512x512 opaque=139685/262144` (block atlas),
  `id=15` capture `opaque=0/256` then `announced-int[] opaque=256/256` (light map),
  `id=9 source=capture 128x128 opaque=4015/16384` (font sheet).
- `kept=100 … lazySupply=0/0 partialRefusals=0 partialCompleted=0 retainedAnnouncements=11
  fills=[capture=7,announcement=10,announced-int[]=126 announcedDropped=0]
  cost=[… glLess[fromAnnouncement=215 fromCapture=7 noContent=0]]`; zero
  `CELTIUM_PATCH_REJECTED`, zero `CELTIUM_CAPTURE_MISS`, `unconverted=0`,
  `noContent=0` (no mirror was ever left without content).

Picture: the panel occupies exactly `x 357..923, y 165..554` in the stored GL reference
(`jei-br` tick 60, `GuiContainerCreative`, GL) and in both GL-less legs; the panel-face band
`(380,470,920,540)` differs from that reference by mean 2.59 / 4.5 % of pixels > 8 (the residual
is the item row clipped at the crop's top edge). The item grid itself differs between the two
runs because the item list's content/scroll differs an hour apart — a fixture-state difference,
not a rendering one; the slot frames and panel face match.

Before/after on the panel-face band: the 12:52 GL-less run (`jei-invfix2`, no format-6 fix, no
WIP) reads mean 122.68 / 99.96 % of pixels > 8 against the same stored reference; `tm2-on` reads
2.59 / 4.5 %.

## What changed in this task

- `VulkanTerrainGpuBackend.invalidateTexture`: the GL-less branch is now gated on
  `-Dceltium.vulkan.glessKeepsInvalidations` (default true). The switch is the control above,
  not a mode of operation.
- `VulkanTerrainGpuBackend.celtium$reportGllessInvalidation`: now also prints `lazySupply`,
  `partialRefusals`, `partialCompleted` and `cost=[…glLess[fromAnnouncement/fromCapture/
  noContent]]`, at the 1st and every 100th kept invalidation.
- `VulkanTextureRegistry.celtium$noteMirrorSource`: the line carries `obj=` (the mirror's
  identity hash, which is what the mirror dump's file names are made of) and `full=`, so a
  dumped mirror PNG can be tied to the GL name whose fill produced it.

## Open defect found on this path, not fixed (outside this task's write scope)

`CeltiumGlStub.undefinedLevel` zero-fills a level allocated with a NULL data pointer and files it
as a capture; `VulkanTextureRegistry.fillFromCapture` then treats that surface as content and
marks the mirror whole. Measured in both legs: `id=3` and `id=42 source=capture size=1280x720
content=opaque=0/921600 full=true`, and `id=15` 16x16 `opaque=0/256` before the light map's
announcement replaced it; the capture census reads `undefined=22` at the first kept
invalidation and `undefined=34` at the hundredth. A frame-sized mirror then
claims whole content while holding only zeros. For the light map the announcement corrects it one
hook later, so no picture in this run depended on it; a texture whose pixels arrive only through
the stub capture would draw transparent and claim whole content. Refusing those honestly needs a
per-level "did the game write pixels here" answer from the stub; `CeltiumGlStub` is not one of
this task's files, so it is reported rather than patched.

## Verified and not verified

Verified under GL-less in this scene: the panel and slot backgrounds in the frame (crops above);
the panel sheet (129), tab sheet (124), font sheet (9), block atlas (12) and light map (15)
mirrors' sources, sizes, opaque-texel counts and `full` state; the delete-and-reallocate
retention (`retainedAnnouncements=11`); zero patch rejections, zero capture misses, zero
content-less readback skips.

Not verified: the light map's per-frame refresh compared against GL over time (126
`announced-int[]` fills happened; one content sample was taken); animated atlas sprites
frame-by-frame (`CELTIUM_MIRROR_PATCH` applies with `reachUnwritten=0`, no picture comparison);
entity/TESR skins (no entities in this scene); particles/format 5 (none in this scene); the two
1280x720 capture-filled mirrors (ids 3/42); mod GUI sheets beyond 124/129.

## The reference leg

Upstream Celeritas `989ebf2e` alone crashes at startup: the fixture jar's `ValidationClient`
carries hard references to `org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend`, FML's
annotation gathering loads the class, and without the celtium jar it is
`NoClassDefFoundError` (`tm2-ref`, exit 255, `MISSING_REPORT` after 8.7 s). The stored GL
picture above is therefore used as the reference; it is the same screen class as the candidate.
