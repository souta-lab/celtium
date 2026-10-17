# Celtium 計器索引（自動生成）

**このファイルは `python3 scripts/instrument-index.py` が生成する。手で編集しない。**

`-Dceltium.*` / `-Dceleritas.*` のスイッチ **337 個**と、
ログに出る `CELTIUM_*` マーカー **34 個**の読み取り位置。

探し方: `grep <名前> docs/instruments.md` → 出た `file:line` を読む。
既定値は読み取り式から機械的に拾ったもので、条件分岐の中身までは表さない。

## 名前空間の目次

| 名前空間 | スイッチ数 |
|---|---|
| [`celeritas.enableGLDebug.*`](#celeritasenableGLDebug-1) | 1 |
| [`celeritas.lwjglService.*`](#celeritaslwjglService-1) | 1 |
| [`celeritas.options.*`](#celeritasoptions-7) | 7 |
| [`celeritas.useVintageFastBlockRenderer.*`](#celeritasuseVintageFastBlockRenderer-1) | 1 |
| [`celtium.clearlatch.*`](#celtiumclearlatch-1) | 1 |
| [`celtium.debug.*`](#celtiumdebug-3) | 3 |
| [`celtium.droppedItemRange.*`](#celtiumdroppedItemRange-1) | 1 |
| [`celtium.droppedItemRangeBlocks.*`](#celtiumdroppedItemRangeBlocks-1) | 1 |
| [`celtium.droppedItemRangeSoftCap.*`](#celtiumdroppedItemRangeSoftCap-1) | 1 |
| [`celtium.entityLightCache.*`](#celtiumentityLightCache-1) | 1 |
| [`celtium.explicitGc.*`](#celtiumexplicitGc-1) | 1 |
| [`celtium.f3.*`](#celtiumf3-2) | 2 |
| [`celtium.font.*`](#celtiumfont-10) | 10 |
| [`celtium.glless.*`](#celtiumglless-1) | 1 |
| [`celtium.glstub.*`](#celtiumglstub-6) | 6 |
| [`celtium.itemMeshCache.*`](#celtiumitemMeshCache-1) | 1 |
| [`celtium.parallelAtlas.*`](#celtiumparallelAtlas-1) | 1 |
| [`celtium.particleCache.*`](#celtiumparticleCache-1) | 1 |
| [`celtium.particleLightCache.*`](#celtiumparticleLightCache-1) | 1 |
| [`celtium.probe.*`](#celtiumprobe-5) | 5 |
| [`celtium.stub.*`](#celtiumstub-1) | 1 |
| [`celtium.terrain.*`](#celtiumterrain-3) | 3 |
| [`celtium.validation.*`](#celtiumvalidation-120) | 120 |
| [`celtium.vulkan.*`](#celtiumvulkan-165) | 165 |
| [`celtium.vulkanTerrain.*`](#celtiumvulkanTerrain-1) | 1 |

---

## スイッチ（名前空間ごと）

### `celeritas.enableGLDebug.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celeritas.enableGLDebug` | `false` | [`common/src/main/java/org/embeddedt/embeddium/impl/gl/debug/GLDebug.java:146`](../common/src/main/java/org/embeddedt/embeddium/impl/gl/debug/GLDebug.java#L146) |

### `celeritas.lwjglService.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celeritas.lwjglService` | `-` | [`common/src/lwjglCommon/java/org/taumc/celeritas/lwjgl/LWJGLServiceProvider.java:24`](../common/src/lwjglCommon/java/org/taumc/celeritas/lwjgl/LWJGLServiceProvider.java#L24) |

### `celeritas.options.*`（7）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celeritas.options.async_graph_search.name` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:69`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L69) |
| `celeritas.options.async_graph_search.tooltip` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:70`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L70) |
| `celeritas.options.chunk_fade_in_duration.name` | `-` | [`forge122/src/main/java/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.java:232`](../forge122/src/main/java/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.java#L232) (+1) |
| `celeritas.options.chunk_fade_in_duration.tooltip` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:34`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L34) |
| `celeritas.options.chunk_fade_in_duration.value` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:35`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L35) |
| `celeritas.options.raster_occlusion_culling.name` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:162`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L162) |
| `celeritas.options.raster_occlusion_culling.tooltip` | `-` | [`common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java:163`](../common/src/main/java/org/embeddedt/embeddium/impl/gui/options/CommonOptionPages.java#L163) |

### `celeritas.useVintageFastBlockRenderer.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celeritas.useVintageFastBlockRenderer` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.java:53`](../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.java#L53) |

### `celtium.clearlatch.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `6` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:253`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L253) |

### `celtium.debug.*`（3）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.debug.fnCensus` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:2177`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2177) |
| `celtium.debug.listInputLog` | `6` | [`common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java:445`](../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L445) |
| `celtium.debug.listSizeLog` | `0` | [`common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java:449`](../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L449) |

### `celtium.droppedItemRange.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.droppedItemRange` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java:33`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L33) |

### `celtium.droppedItemRangeBlocks.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.droppedItemRangeBlocks` | `"32"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java:36`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L36) |

### `celtium.droppedItemRangeSoftCap.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.droppedItemRangeSoftCap` | `"256"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java:39`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L39) |

### `celtium.entityLightCache.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.entityLightCache` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java:35`](../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L35) (+1) |

### `celtium.explicitGc.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.explicitGc` | `"false"` | [`forge122/src/main/java/org/taumc/celeritas/impl/CeltiumExplicitGc.java:21`](../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumExplicitGc.java#L21) |

### `celtium.f3.*`（2）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.f3.fullSummary` | `false` | [`forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java:74`](../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L74) |
| `celtium.f3.sectionRefreshFrames` | `20` | [`forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java:96`](../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L96) |

### `celtium.font.*`（10）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.font.batching` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java:224`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L224) |
| `celtium.font.debugRgb` | `-1` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:182`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L182) |
| `celtium.font.fastPath` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:327`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L327) |
| `celtium.font.isolation` | `""` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:194`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L194) |
| `celtium.font.matrixProbe` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:235`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L235) |
| `celtium.font.mode` | `""` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:204`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L204) |
| `celtium.font.retaining` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java:233`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L233) |
| `celtium.font.screenFill` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:225`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L225) |
| `celtium.font.stateProbe` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:249`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L249) |
| `celtium.font.winding` | `""` | [`forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java:216`](../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L216) |

### `celtium.glless.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.glless.census` | `false` | [`forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java:99`](../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99) |

### `celtium.glstub.*`（6）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:789`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L789) |
| `celtium.glstub.drawArrayProbeLines` | `12` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:5387`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5387) |
| `celtium.glstub.ffm` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:1707`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1707) |
| `celtium.glstub.skyDegradedLines` | `4` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:5572`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5572) |
| `celtium.glstub.trace` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:1665`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1665) (+1) |
| `celtium.glstub.traceLimit` | `40` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:2079`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2079) |

### `celtium.itemMeshCache.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java:68`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L68) |

### `celtium.parallelAtlas.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.parallelAtlas` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java:47`](../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java#L47) |

### `celtium.particleCache.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.particleCache` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java:51`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java#L51) |

### `celtium.particleLightCache.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.particleLightCache` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java:53`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java#L53) |

### `celtium.probe.*`（5）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.probe.draws` | `false` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java:48`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L48) (+2) |
| `celtium.probe.every` | `50` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java:50`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L50) (+2) |
| `celtium.probe.format` | `-1` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java:639`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L639) |
| `celtium.probe.texture` | `-1` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java:627`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L627) |
| `celtium.probe.textureLines` | `8` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java:646`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L646) |

### `celtium.stub.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:4501`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4501) |

### `celtium.terrain.*`（3）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.terrain.brightnessDiagnostics` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java:51`](../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51) |
| `celtium.terrain.forceCompactFormat` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java:244`](../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L244) |
| `celtium.terrain.noShade` | `false` | [`forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java:18`](../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18) |

### `celtium.validation.*`（120）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.validation` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java:24`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L24) (+1) |
| `celtium.validation.allBlocks` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1478`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1478) (+3) |
| `celtium.validation.blockSweep` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1472`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1472) (+2) |
| `celtium.validation.blockSweepEvery` | `4` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1649`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1649) |
| `celtium.validation.blockSweepFrom` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1637`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1637) (+1) |
| `celtium.validation.blockSweepPerTick` | `2` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1656`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1656) |
| `celtium.validation.breakBlock` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5236`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5236) (+1) |
| `celtium.validation.breakSubjectOnly` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5243`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5243) |
| `celtium.validation.cameraPitch` | `Float.toString(base.pitc` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1150`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1150) |
| `celtium.validation.cameraSetTick` | `25` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:943`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L943) |
| `celtium.validation.cameraX` | `Double.toString(base.x` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1146`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1146) |
| `celtium.validation.cameraY` | `Double.toString(base.y` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1147`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1147) |
| `celtium.validation.cameraYaw` | `Float.toString(base.yaw` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1149`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1149) |
| `celtium.validation.cameraZ` | `Double.toString(base.z` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1148`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1148) |
| `celtium.validation.captureAtTotalWorldTime` | `0L` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:250`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250) |
| `celtium.validation.captureCondition` | `"tick"` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:229`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229) |
| `celtium.validation.captureDeadlineTick` | `400` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:288`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L288) |
| `celtium.validation.captureEntities` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1555`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1555) (+1) |
| `celtium.validation.captureMaxHeldBuilds` | `-1` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:285`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L285) |
| `celtium.validation.captureMaxP1` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:274`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L274) |
| `celtium.validation.captureMinWorldFrames` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:297`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L297) |
| `celtium.validation.captureSections` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:262`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L262) |
| `celtium.validation.captureStableFrames` | `30` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:232`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232) |
| `celtium.validation.churnInterval` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:734`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L734) |
| `celtium.validation.commandBufferTargets` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:339`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L339) (+1) |
| `celtium.validation.compactVertexFormat` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2852`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2852) |
| `celtium.validation.compositeBlit` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1933`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1933) |
| `celtium.validation.dimension` | `-1` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:669`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L669) (+2) |
| `celtium.validation.dimensionAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:668`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L668) (+2) |
| `celtium.validation.dimensionWaitUntilTick` | `240` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:783`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L783) |
| `celtium.validation.drawBlockQuad` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1861`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1861) |
| `celtium.validation.drawMergedQuads` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1998`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1998) |
| `celtium.validation.drawTest` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2066`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2066) (+1) |
| `celtium.validation.drawTestQuad` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1859`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1859) |
| `celtium.validation.drawTestTargets` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1904`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1904) |
| `celtium.validation.dumpPresentedImage` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2986`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2986) (+8) |
| `celtium.validation.dumpPresentedMenuTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:868`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L868) |
| `celtium.validation.dumpPresentedTick` | `20` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5185`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5185) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:882`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L882) |
| `celtium.validation.emptyHand` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5343`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5343) |
| `celtium.validation.enchantItem` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3676`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3676) |
| `celtium.validation.expectVulkan` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4068`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4068) |
| `celtium.validation.expectVulkanTerrain` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5685`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5685) |
| `celtium.validation.fixedWorldTime` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5091`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5091) |
| `celtium.validation.frameViewportHeight` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1048`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1048) |
| `celtium.validation.gate.maxBlackFraction` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1180`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1180) |
| `celtium.validation.gate.minDistinctColours` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1173`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1173) |
| `celtium.validation.gate.minNonBackgroundFraction` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1176`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1176) |
| `celtium.validation.gate.minPanelChangedFraction` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1183`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1183) |
| `celtium.validation.gate.minPanelDistinctColours` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1188`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1188) |
| `celtium.validation.gate.minPanelRowAmplitude` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1191`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1191) |
| `celtium.validation.gate.minPanelStrongFraction` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1186`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1186) |
| `celtium.validation.gate.minVerticalBands` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1178`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1178) |
| `celtium.validation.glowEntities` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1556`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1556) (+1) |
| `celtium.validation.glyphParticles` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5284`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5284) |
| `celtium.validation.guiLayoutTicks` | `8` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1026`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1026) |
| `celtium.validation.guiMinFrames` | `3` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1028`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1028) |
| `celtium.validation.guiScale` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:74`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74) |
| `celtium.validation.guiScaleRestoreAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:80`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80) |
| `celtium.validation.guiWaitTicks` | `20` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1030`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1030) |
| `celtium.validation.heldItem` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4840`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4840) |
| `celtium.validation.holdUntilTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:727`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L727) |
| `celtium.validation.hurtEntities` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3765`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3765) |
| `celtium.validation.iconifyAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:835`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L835) |
| `celtium.validation.interop` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3755`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3755) (+4) |
| `celtium.validation.jeiFilterText` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5021`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5021) |
| `celtium.validation.jeiRecipe` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1499`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1499) (+1) |
| `celtium.validation.jeiScreen` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1496`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1496) (+2) |
| `celtium.validation.jeiSearch` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4973`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4973) |
| `celtium.validation.keepEntities` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1555`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1555) (+3) |
| `celtium.validation.keepGui` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5325`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5325) (+1) |
| `celtium.validation.keepInventoryScreen` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1505`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1505) (+1) |
| `celtium.validation.lookDown` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:9754`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9754) |
| `celtium.validation.menuHoldTicks` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:905`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L905) |
| `celtium.validation.mergedQuadCount` | `22000` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2033`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2033) |
| `celtium.validation.minDimensionFrames` | `10` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:776`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L776) |
| `celtium.validation.minWorldFrames` | `100` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5591`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5591) (+1) |
| `celtium.validation.modelLifecycle` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java:35`](../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L35) |
| `celtium.validation.particles` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5266`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5266) |
| `celtium.validation.pinSections` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:238`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238) |
| `celtium.validation.pinSectionsStableFrames` | `20` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:253`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253) |
| `celtium.validation.postprocess` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4820`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4820) |
| `celtium.validation.presentedImageAtCapture` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5767`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5767) |
| `celtium.validation.reload` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4811`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4811) |
| `celtium.validation.reloadAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3831`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3831) |
| `celtium.validation.renderClouds` | `-1` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3814`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3814) (+1) |
| `celtium.validation.rendererlessEntities` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1557`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1557) (+1) |
| `celtium.validation.resize` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5098`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5098) (+1) |
| `celtium.validation.resizeAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:838`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L838) |
| `celtium.validation.resizeDuringMenu` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:844`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L844) |
| `celtium.validation.resizeMenuHoldTicks` | `40` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:908`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L908) |
| `celtium.validation.resizeOutput` | `"HEADLESS-1"` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:857`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L857) |
| `celtium.validation.resizeTo` | `"1024x640"` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:839`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L839) (+1) |
| `celtium.validation.resizeViaCompositor` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:855`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L855) |
| `celtium.validation.resourceReloadAtTick` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:816`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L816) |
| `celtium.validation.runId` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:929`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L929) |
| `celtium.validation.saveSlot` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1587`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1587) |
| `celtium.validation.scene` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:111`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L111) |
| `celtium.validation.screen` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4870`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4870) |
| `celtium.validation.screenReadback` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1944`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1944) |
| `celtium.validation.screenshotAtTick` | `-1` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5061`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5061) |
| `celtium.validation.scrollList` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4895`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4895) |
| `celtium.validation.showDebugInfo` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3824`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3824) (+1) |
| `celtium.validation.slowFrameMs` | `50` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4806`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4806) (+1) |
| `celtium.validation.spawnEntities` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1556`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1556) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `-` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1557`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1557) (+1) |
| `celtium.validation.spectator` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5194`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5194) |
| `celtium.validation.stateProbes` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2122`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2122) |
| `celtium.validation.texEnvProbe` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2189`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2189) |
| `celtium.validation.thirdPerson` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5331`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5331) |
| `celtium.validation.unicodeFont` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2974`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2974) |
| `celtium.validation.vanillaReference` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5661`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5661) |
| `celtium.validation.videoSettingsOption` | `""` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:7329`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7329) (+1) |
| `celtium.validation.vulkanLayer` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5563`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5563) |
| `celtium.validation.warmFromTick` | `400` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4341`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4341) (+3) |
| `celtium.validation.warmToTick` | `600` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:4342`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4342) (+3) |
| `celtium.validation.weather` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5127`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5127) (+1) |
| `celtium.validation.worldLayers` | `celtium$DEFAULT_WORLD_LA` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:1471`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1471) (+2) |
| `celtium.validation.worldSwitch` | `false` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3439`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3439) |
| `celtium.validation.worldTime` | `6000L` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:83`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83) |

### `celtium.vulkan.*`（165）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13171`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13171) |
| `celtium.vulkan.allowCpuDevice` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:100`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100) |
| `celtium.vulkan.alphaFuncOverride` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2072`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2072) |
| `celtium.vulkan.alphaTest` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2018`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2018) |
| `celtium.vulkan.arenaCensus` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java:61`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61) |
| `celtium.vulkan.arenaVerify` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java:63`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63) |
| `celtium.vulkan.arenaVerifyBytes` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java:65`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65) |
| `celtium.vulkan.arenaVerifyOkReports` | `8` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java:68`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68) |
| `celtium.vulkan.assumeNoPresentSupport` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:584`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L584) |
| `celtium.vulkan.atlasAudit` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java:343`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L343) |
| `celtium.vulkan.batchTimings` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2351`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2351) |
| `celtium.vulkan.batchedSubmits` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:318`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L318) |
| `celtium.vulkan.chainClearLog` | `12` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2537`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2537) |
| `celtium.vulkan.chainDumpPasses` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12920`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12920) |
| `celtium.vulkan.chainFrameDump` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2249`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2249) |
| `celtium.vulkan.chainFrameDumpPath` | `"/tmp/celtium-chain-fram` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12897`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12897) |
| `celtium.vulkan.chainFrameDumps` | `1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2250`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2250) |
| `celtium.vulkan.chainInputFlush` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java:49`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49) |
| `celtium.vulkan.chainPreflightAdoption` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1162`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1162) |
| `celtium.vulkan.colourAttrOffset` | `12` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java:184`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184) |
| `celtium.vulkan.compareBoundTexture` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1403`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1403) |
| `celtium.vulkan.cpuSplit` | `"false"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2341`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2341) |
| `celtium.vulkan.depthFormat` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java:49`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L49) |
| `celtium.vulkan.descriptorPoolSets` | `64` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java:541`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L541) |
| `celtium.vulkan.diagnostics` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java:20`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L20) |
| `celtium.vulkan.drawSite` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1871`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1871) |
| `celtium.vulkan.drawSiteFormat` | `-1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1874`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1874) |
| `celtium.vulkan.drawSiteLines` | `16` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1857`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1857) |
| `celtium.vulkan.drawSiteMinFrame` | `0L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1860`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1860) |
| `celtium.vulkan.dumpTextures` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:3028`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3028) (+1) |
| `celtium.vulkan.dumpTexturesEvery` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:3040`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3040) |
| `celtium.vulkan.enqAll` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1852`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1852) |
| `celtium.vulkan.enqFormat` | `-1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1854`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1854) |
| `celtium.vulkan.failAllocationAt` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java:14`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14) |
| `celtium.vulkan.failAllocationSite` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java:24`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24) |
| `celtium.vulkan.failDescriptorSetAt` | `-1L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java:16`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16) |
| `celtium.vulkan.failDescriptorSetSite` | `""` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java:17`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17) |
| `celtium.vulkan.failDeviceLost` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13281`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13281) |
| `celtium.vulkan.failFenceWait` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13276`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13276) |
| `celtium.vulkan.failIdleAfter` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1636`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1636) |
| `celtium.vulkan.failImmediateSubmits` | `0` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:2296`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2296) (+1) |
| `celtium.vulkan.failSubmitAt` | `-1L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java:15`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15) |
| `celtium.vulkan.failSubmitSite` | `""` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java:16`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16) |
| `celtium.vulkan.failSubmits` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java:29`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29) (+1) |
| `celtium.vulkan.failTextureUploadAt` | `-1L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java:15`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15) |
| `celtium.vulkan.failTextureUploadSite` | `""` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java:16`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16) |
| `celtium.vulkan.fogCaptureLog` | `4` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:3015`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3015) |
| `celtium.vulkan.fontDrawStateBoundary` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2672`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2672) |
| `celtium.vulkan.forceAcquireOutOfDate` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:189`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L189) |
| `celtium.vulkan.forceNoOcclusion` | `false` | [`common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java:251`](../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251) |
| `celtium.vulkan.forceNoTimestamps` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:411`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L411) |
| `celtium.vulkan.forceOutOfDate` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:184`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L184) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `1L << 18` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java:45`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `1L << 20` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java:43`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43) |
| `celtium.vulkan.frameAtWindowFrame` | `-1L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9768`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9768) |
| `celtium.vulkan.frameAtWindowLines` | `8` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9771`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9771) |
| `celtium.vulkan.frameClearMark` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9750`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9750) |
| `celtium.vulkan.frameDumpCount` | `6` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12566`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12566) |
| `celtium.vulkan.frameDumpDir` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12558`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12558) |
| `celtium.vulkan.frameDumpEvery` | `60` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12562`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12562) |
| `celtium.vulkan.freshPresentSemaphores` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:225`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L225) |
| `celtium.vulkan.gameClearLatch` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2246`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2246) |
| `celtium.vulkan.glessKeepsInvalidations` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1066`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1066) |
| `celtium.vulkan.glless` | `-` | [`forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java:23`](../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23) (+5) |
| `celtium.vulkan.gllessFrameSemaphores` | `"false"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java:413`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L413) |
| `celtium.vulkan.groupBounds` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1744`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1744) |
| `celtium.vulkan.groupBoundsCount` | `200` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1784`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1784) |
| `celtium.vulkan.groupBoundsFormat` | `-1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1746`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1746) (+1) |
| `celtium.vulkan.groupBoundsGuiOnly` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1800`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1800) |
| `celtium.vulkan.groupBoundsMinFrame` | `0L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1794`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1794) |
| `celtium.vulkan.groupColour` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2052`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2052) |
| `celtium.vulkan.groupPool` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java:131`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L131) |
| `celtium.vulkan.groupScissor` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2044`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2044) |
| `celtium.vulkan.handleLog` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:1184`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1184) |
| `celtium.vulkan.handoffTimings` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:599`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L599) (+1) |
| `celtium.vulkan.hideExternalFdExtensions` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:2216`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2216) |
| `celtium.vulkan.hudCaptureFrames` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1817`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1817) |
| `celtium.vulkan.hudCaptureMinFrame` | `0L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1827`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1827) |
| `celtium.vulkan.immediate` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:946`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L946) |
| `celtium.vulkan.immediateBlendAlpha` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2033`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2033) |
| `celtium.vulkan.immediateBlendEquation` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2035`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2035) |
| `celtium.vulkan.immediateColorMask` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2037`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2037) |
| `celtium.vulkan.immediateFlushIndices` | `512` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2235`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2235) |
| `celtium.vulkan.immediateFog` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2840`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2840) |
| `celtium.vulkan.immediateFogDistance` | `"radial"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2852`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2852) |
| `celtium.vulkan.immediateFogLog` | `8` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2858`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2858) |
| `celtium.vulkan.immediateFormats` | `VulkanImmediateFormats.D` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2126`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2126) |
| `celtium.vulkan.immediateStateCalls` | `"false"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java:548`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L548) |
| `celtium.vulkan.immediateWindowSamples` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2438`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2438) |
| `celtium.vulkan.immediateWindows` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2195`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2195) |
| `celtium.vulkan.immediateWindowsPerFrame` | `16` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2233`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2233) |
| `celtium.vulkan.indexCacheLimit` | `512` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java:32`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32) |
| `celtium.vulkan.indirectCompare` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java:291`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L291) |
| `celtium.vulkan.indirectTerrain` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1505`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1505) |
| `celtium.vulkan.lightDiagnostics` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1650`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1650) (+2) |
| `celtium.vulkan.lightmapFullBrightGui` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13152`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13152) |
| `celtium.vulkan.matrixDiagnostics` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1730`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1730) |
| `celtium.vulkan.mipBiasAdd` | `"0.0"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java:649`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L649) |
| `celtium.vulkan.mirrorCapturedLevels` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java:143`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L143) |
| `celtium.vulkan.mirrorDrawLog` | `8` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2532`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2532) |
| `celtium.vulkan.mirrorDump` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:7590`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7590) (+3) |
| `celtium.vulkan.mirrorDumpIds` | `""` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:123`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123) |
| `celtium.vulkan.mirrorEncode` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:196`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196) |
| `celtium.vulkan.mirrorGluPerspective` | `"true"` | [`forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java:48`](../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48) |
| `celtium.vulkan.mirrorPoison` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:145`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145) |
| `celtium.vulkan.mirrorPoisonFlat` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:157`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157) |
| `celtium.vulkan.mirrorReachOverride` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java:141`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L141) |
| `celtium.vulkan.mirrorRefreshFrames` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2395`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2395) |
| `celtium.vulkan.mirroredMatrices` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1000`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1000) |
| `celtium.vulkan.modelDraw` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13274`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13274) |
| `celtium.vulkan.modelDrawOnlyList` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:13261`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13261) |
| `celtium.vulkan.modelMatrixLines` | `200` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:11268`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11268) |
| `celtium.vulkan.modelMeshBuild` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java:33`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33) |
| `celtium.vulkan.modelQuadLimit` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java:35`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35) |
| `celtium.vulkan.nativeTextureCopy` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java:152`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L152) |
| `celtium.vulkan.noTerrainDepth` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java:1163`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1163) |
| `celtium.vulkan.pairLayoutLines` | `12` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9804`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9804) |
| `celtium.vulkan.pairProbeClear` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9867`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9867) |
| `celtium.vulkan.pairStayGeneral` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9828`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9828) (+1) |
| `celtium.vulkan.passSplit` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java:62`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L62) (+4) |
| `celtium.vulkan.patchMipLevels` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java:145`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145) |
| `celtium.vulkan.positionNormalDepthTest` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2024`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2024) |
| `celtium.vulkan.postChainFailFirstUse` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java:271`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271) |
| `celtium.vulkan.postChainMaxPushConstants` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java:288`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288) |
| `celtium.vulkan.presentFlushPending` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9854`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9854) |
| `celtium.vulkan.presentFromComposite` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2222`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2222) (+4) |
| `celtium.vulkan.presentMarker` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:182`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L182) |
| `celtium.vulkan.presentNoRecord` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:223`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L223) |
| `celtium.vulkan.presentPatch` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9847`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9847) |
| `celtium.vulkan.presentPatchLines` | `8` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9857`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9857) |
| `celtium.vulkan.presentPatchMinPresent` | `1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9860`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9860) |
| `celtium.vulkan.presentReadback` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:221`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L221) |
| `celtium.vulkan.presentedDumpEvery` | `40` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2248`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2248) |
| `celtium.vulkan.probeOldShape` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java:227`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L227) |
| `celtium.vulkan.readResolveLog` | `6` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java:173`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L173) |
| `celtium.vulkan.rebuildDescriptors` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9836`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9836) |
| `celtium.vulkan.referenceRender` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1653`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1653) |
| `celtium.vulkan.refuseImmediateFormat` | `-1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2242`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2242) |
| `celtium.vulkan.refuseTerrainPass` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2244`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2244) |
| `celtium.vulkan.samplerConfirmEarly` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java:1681`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1681) |
| `celtium.vulkan.scratchZero` | `"perspective,ortho"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2645`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2645) |
| `celtium.vulkan.skipColorReturn` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java:126`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126) |
| `celtium.vulkan.skipDepthReturn` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java:124`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124) |
| `celtium.vulkan.skipGlComposite` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:2220`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2220) |
| `celtium.vulkan.skipSelfComposite` | `"true"` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9830`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9830) |
| `celtium.vulkan.solidTerrain` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java:736`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L736) |
| `celtium.vulkan.strictSymbolLines` | `200` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:2646`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2646) |
| `celtium.vulkan.strictSymbols` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:2667`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2667) |
| `celtium.vulkan.suppressReadbackIds` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java:212`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212) |
| `celtium.vulkan.swapchainProbe` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:89`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L89) |
| `celtium.vulkan.targetCensus` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:4665`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4665) |
| `celtium.vulkan.targetImageLines` | `12` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:4656`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4656) |
| `celtium.vulkan.terrainAtlasMaxLod` | `4` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java:258`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L258) |
| `celtium.vulkan.textureDiagnostics` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1655`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1655) (+1) |
| `celtium.vulkan.uiTest` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:9743`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9743) |
| `celtium.vulkan.uploadCallerId` | `-1` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:11247`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11247) |
| `celtium.vulkan.uploadTrace` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:393`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L393) |
| `celtium.vulkan.validation` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java:272`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272) (+2) |
| `celtium.vulkan.vertexColourSamples` | `0` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java:803`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L803) |
| `celtium.vulkan.vertexDiagnostics` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:10195`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10195) |
| `celtium.vulkan.windowCopyBlit` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java:169`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L169) |
| `celtium.vulkan.windowDrawLines` | `6` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1877`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1877) |
| `celtium.vulkan.windowDrawMinFrame` | `0L` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:1880`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1880) |
| `celtium.vulkan.withoutGlIdentity` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:2756`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2756) |
| `celtium.vulkan.worldMatrixCensus` | `false` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:295`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L295) |

### `celtium.vulkanTerrain.*`（1）

| スイッチ | 既定 | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `-` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java:29`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L29) |

## ログマーカー（`CELTIUM_*`）

| マーカー | 位置 |
|---|---|
| `CELTIUM_BLEND_CENSUS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:747`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L747) |
| `CELTIUM_DIM_ARRIVED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3294`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3294) |
| `CELTIUM_DIM_CHANGE` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3366`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3366) |
| `CELTIUM_DIM_GATE_WAIT` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:5447`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5447) |
| `CELTIUM_DIM_LEFT` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3377`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3377) |
| `CELTIUM_DIM_PLACEMENT` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6184`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6184) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3432`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3432) |
| `CELTIUM_DIM_TRACE` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3388`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3388) |
| `CELTIUM_DISPLAY_LISTS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:767`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L767) |
| `CELTIUM_DRAWARRAYS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:750`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L750) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3335`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3335) |
| `CELTIUM_FN_CENSUS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:759`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L759) |
| `CELTIUM_FRAME_AT_WINDOW` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:7514`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7514) |
| `CELTIUM_GLLESS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:700`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L700) (+10) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [`forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java:356`](../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L356) |
| `CELTIUM_ICONIFY` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3517`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3517) |
| `CELTIUM_NOOP_CENSUS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:739`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L739) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:8725`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8725) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3873`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3873) |
| `CELTIUM_PRESENT_PATCH` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:12461`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12461) |
| `CELTIUM_RESIZE_COMPOSITOR` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6358`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6358) |
| `CELTIUM_RESIZE_FAILED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6320`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6320) (+1) |
| `CELTIUM_RESIZE_OBSERVED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6445`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6445) |
| `CELTIUM_RESIZE_REQUEST` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6361`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6361) |
| `CELTIUM_RESOURCE_RELOAD` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3489`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3489) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3494`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3494) |
| `CELTIUM_SPLASH_FLIP` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:7536`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7536) |
| `CELTIUM_SPLASH_HANDLES` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:7634`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7634) |
| `CELTIUM_SPLASH_PAIRPOST` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:7554`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7554) |
| `CELTIUM_TEX_PARAM_CENSUS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:756`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L756) |
| `CELTIUM_TRANSFORM_CENSUS` | [`forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java:358`](../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L358) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java:740`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L740) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:6424`](../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6424) |
| `CELTIUM_WORLD_MATRIX` | [`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java:3083`](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3083) |

---

## 受入でよく使う leg ランナー

`scripts/gv-leg.sh <leg名> <jar-dir> <mode> <gpu> [秒] [追加 -D…]`

- mode: `cohort`（14 mod）／`e2e`（268 mod）／`recipe`（RLCraft 165 mod）／`plain`
- gpu: `amd`／`intel`（Intel は `DRI_PRIME=pci-0000_00_02_0!` を自分で export する）
- 結果: `.gv-verify/run-<leg>/`（`validation-process.json`・`validation-console.log`・`screenshots/`）

