# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **407 個**／文字列内マーカー **377 個**。
機能設定・故障注入も含む。すべてが計測用スイッチという意味ではない。

名前を検索してリンク先の実装を読む。既定値・有効条件・出力上限は推測で掲載しない。
索引は `System.getProperty` / `Boolean.getBoolean` / `Integer.getInteger` / `Long.getLong` の
リテラルまたは単純な static String 定数引数だけを収録する。計算されたキー・別API・テストは対象外。
マーカーは接頭辞つきの文字列も収録するが、ログ出力以外の参照も含む。`--check` は鮮度検査であり網羅性の証明ではない。

手順と測定用途は [開発・検証手順](<development.md>)。全文を毎回ロードせず、用途から探す。

## 名前空間

| 名前空間 | 読み取りキー数 |
|---|---|
| [celeritas.enableGLDebug](#group-celeritas-enableGLDebug) | 1 |
| [celeritas.useVintageFastBlockRenderer](#group-celeritas-useVintageFastBlockRenderer) | 1 |
| [celtium.clearlatch](#group-celtium-clearlatch) | 1 |
| [celtium.debug](#group-celtium-debug) | 17 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.entityLightCache](#group-celtium-entityLightCache) | 1 |
| [celtium.explicitGc](#group-celtium-explicitGc) | 1 |
| [celtium.f3](#group-celtium-f3) | 2 |
| [celtium.font](#group-celtium-font) | 10 |
| [celtium.fontAppend](#group-celtium-fontAppend) | 1 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 2 |
| [celtium.fontGlyph](#group-celtium-fontGlyph) | 1 |
| [celtium.forceIdentityTexmat](#group-celtium-forceIdentityTexmat) | 1 |
| [celtium.glless](#group-celtium-glless) | 1 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.shaderpack](#group-celtium-shaderpack) | 10 |
| [celtium.shadow](#group-celtium-shadow) | 1 |
| [celtium.splashBar](#group-celtium-splashBar) | 1 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 3 |
| [celtium.validation](#group-celtium-validation) | 132 |
| [celtium.vulkan](#group-celtium-vulkan) | 198 |
| [celtium.vulkanTerrain](#group-celtium-vulkanTerrain) | 1 |

<a id="group-celeritas-enableGLDebug"></a>
## `celeritas.enableGLDebug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celeritas.enableGLDebug` | `Boolean.getBoolean` | [GLDebug.java:146](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/debug/GLDebug.java#L146>) |

<a id="group-celeritas-useVintageFastBlockRenderer"></a>
## `celeritas.useVintageFastBlockRenderer.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celeritas.useVintageFastBlockRenderer` | `System.getProperty` | [ChunkBuilderMeshingTask.java:53](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.java#L53>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L280>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10187](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10187>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L565>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13826>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:482](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L482>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15543>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13824](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13824>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L32>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L246>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:2999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2999>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13958>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13959>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:450](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L450>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:454](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L454>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13997](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13997>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13822>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L609>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |

<a id="group-celtium-droppedItemRange"></a>
## `celtium.droppedItemRange.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.droppedItemRange` | `System.getProperty` | [EntityItemRangeMixin.java:33](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L33>) |

<a id="group-celtium-droppedItemRangeBlocks"></a>
## `celtium.droppedItemRangeBlocks.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.droppedItemRangeBlocks` | `System.getProperty` | [EntityItemRangeMixin.java:36](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L36>) |

<a id="group-celtium-droppedItemRangeSoftCap"></a>
## `celtium.droppedItemRangeSoftCap.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.droppedItemRangeSoftCap` | `System.getProperty` | [EntityItemRangeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityItemRangeMixin.java#L39>) |

<a id="group-celtium-entityLightCache"></a>
## `celtium.entityLightCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L35>), [EntityLightCacheMixin.java:33](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityLightCacheMixin.java#L33>) |

<a id="group-celtium-explicitGc"></a>
## `celtium.explicitGc.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.explicitGc` | `System.getProperty` | [CeltiumExplicitGc.java:21](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumExplicitGc.java#L21>) |

<a id="group-celtium-f3"></a>
## `celtium.f3.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.f3.fullSummary` | `Boolean.getBoolean` | [CeleritasVintage.java:74](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L74>) |
| `celtium.f3.sectionRefreshFrames` | `Integer.getInteger` | [CeleritasVintage.java:96](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L96>) |

<a id="group-celtium-font"></a>
## `celtium.font.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.font.batching` | `System.getProperty` | [FontBatch.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L224>) |
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L188>) |
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L333>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:200](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L200>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:241](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L241>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:210](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L210>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L233>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:231](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L231>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:255](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L255>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:222](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L222>) |

<a id="group-celtium-fontAppend"></a>
## `celtium.fontAppend.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L249>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |
| `celtium.fontAtlas.dump` | `System.getProperty` | [CeltiumFontAtlas.java:189](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L189>) |

<a id="group-celtium-fontGlyph"></a>
## `celtium.fontGlyph.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontGlyph.debug` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:50](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L50>) |

<a id="group-celtium-forceIdentityTexmat"></a>
## `celtium.forceIdentityTexmat.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4503>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:99](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1138>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:6692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6692>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:2470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2470>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:6877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6877>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:2428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2428>), [CeltiumGlStub.java:2906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2906>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:2901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2901>) |

<a id="group-celtium-gpuPassTimings"></a>
## `celtium.gpuPassTimings.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.gpuPassTimings` | `Boolean.getBoolean` | [GpuPassTimingReport.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L24>) |

<a id="group-celtium-itemMeshCache"></a>
## `celtium.itemMeshCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `System.getProperty` | [RenderItemMixin.java:77](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L77>) |

<a id="group-celtium-mirrorDump"></a>
## `celtium.mirrorDump.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17517>) |

<a id="group-celtium-parallelAtlas"></a>
## `celtium.parallelAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.parallelAtlas` | `System.getProperty` | [CeltiumAtlasLoader.java:47](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java#L47>) |

<a id="group-celtium-particleCache"></a>
## `celtium.particleCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.particleCache` | `System.getProperty` | [ParticleMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java#L51>) |

<a id="group-celtium-particleLightCache"></a>
## `celtium.particleLightCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.particleLightCache` | `System.getProperty` | [ParticleMixin.java:53](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java#L53>) |

<a id="group-celtium-probe"></a>
## `celtium.probe.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.probe.draws` | `Boolean.getBoolean` | [CloudDrawProbeMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L48>), [ParticleDrawProbeMixin.java:27](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L27>), [RainDrawProbeMixin.java:26](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L26>) |
| `celtium.probe.every` | `Integer.getInteger` | [CloudDrawProbeMixin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L50>), [ParticleDrawProbeMixin.java:29](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L29>), [RainDrawProbeMixin.java:28](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L28>) |
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:639](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L639>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:627](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L627>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:646](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L646>) |

<a id="group-celtium-shaderpack"></a>
## `celtium.shaderpack.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shaderpack.captureVertices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15803>) |
| `celtium.shaderpack.compileProbe` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15921>) |
| `celtium.shaderpack.drawProbe` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15652](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15652>) |
| `celtium.shaderpack.drawProbeDebug` | `Boolean.getBoolean` | [ShaderPackDrawProbe.java:924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L924>), [VulkanTerrainGpuBackend.java:16200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16200>) |
| `celtium.shaderpack.path` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15642](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15642>) |
| `celtium.shaderpack.probeFullscreen` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16300>) |
| `celtium.shaderpack.probeSample` | `Boolean.getBoolean` | [ShaderPackDrawProbe.java:840](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L840>) |
| `celtium.shaderpack.probeShadow` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16278>) |
| `celtium.shaderpack.probeView` | `System.getProperty` | [ShaderPackDrawProbe.java:1109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L1109>) |
| `celtium.shaderpack.program` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16004>), [VulkanTerrainGpuBackend.java:16109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16109>) |

<a id="group-celtium-shadow"></a>
## `celtium.shadow.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shadow.debug` | `Boolean.getBoolean` | [VulkanImmediateBlockPipeline.java:402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L402>), [VulkanImmediateTexturePipeline.java:994](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L994>) |

<a id="group-celtium-splashBar"></a>
## `celtium.splashBar.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.splashBar` | `System.getProperty` | [CeltiumSplashBar.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/gui/CeltiumSplashBar.java#L18>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:5719](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5719>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:51](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:250](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L250>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [ValidationClient.java:3047](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3047>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1605](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1605>), [ValidationClient.java:3922](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3922>), [ValidationClient.java:9627](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9627>), [ValidationClient.java:10731](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10731>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1599](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1599>), [ValidationClient.java:1764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1764>), [ValidationClient.java:9620](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9620>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1790](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1790>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1778](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1778>), [ValidationClient.java:9621](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9621>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1797](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1797>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:5728](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5728>), [ValidationClient.java:5742](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5742>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:5735](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5735>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1245](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1245>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1022](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1022>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1241>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1242](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1242>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1244>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1243>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L293>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:6008](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6008>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L290>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L279>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L267>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:263](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L263>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:768](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L768>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L366>), [ValidationClient.java:2009](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2009>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3050>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2079>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:685](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L685>), [ValidationClient.java:1183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1183>), [ValidationClient.java:9644](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9644>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:684](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L684>), [ValidationClient.java:1184](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1184>), [ValidationClient.java:9645](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9645>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:692](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L692>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:694](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L694>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:832](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L832>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:817](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L817>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2007](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2007>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2144](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2144>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2212](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2212>), [ValidationClient.java:5809](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5809>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2005](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2005>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2050>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [LoadingScreenRendererMixin.java:46](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L46>), [ValidationClient.java:7480](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7480>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3198](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3198>), [ValidationClient.java:3222](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3222>), [ValidationClient.java:5676](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5676>), [ValidationClient.java:7421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7421>), [ValidationClient.java:7422](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7422>), [ValidationClient.java:7451](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7451>), [ValidationClient.java:7481](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7481>), [ValidationClient.java:7901](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7901>), [ValidationClient.java:8302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8302>), [ValidationClient.java:8385](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8385>), [ValidationClient.java:9220](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9220>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:948](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L948>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:5677](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5677>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L958>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:5835](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5835>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3842](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3842>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4279>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:6183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6183>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:5583](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5583>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1127>) |
| `celtium.validation.gameScreenshot` | `Boolean.getBoolean` | [ValidationClient.java:7852](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7852>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1275>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1268>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1270>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1277](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1277>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1283](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1283>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1285>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1280>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1273](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1273>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3976](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3976>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:5776](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5776>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1105>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1107](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1107>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:74](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:80](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1109>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:5299](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5299>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L761>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3966](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3966>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3864](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3864>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L884>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3956](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3956>), [ValidationClient.java:3983](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3983>), [ValidationClient.java:5808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5808>), [ValidationClient.java:6000](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6000>), [ValidationClient.java:6046](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6046>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:935](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L935>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:5513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5513>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1626](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1626>), [ValidationClient.java:5524](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5524>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1623>), [ValidationClient.java:4768](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4768>), [ValidationClient.java:5444](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5444>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:5465](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5465>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3469](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3469>), [ValidationClient.java:3819](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3819>), [ValidationClient.java:3957](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3957>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:5817](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5817>), [ValidationClient.java:5818](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5818>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1632](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1632>), [ValidationClient.java:5571](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5571>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:10737](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10737>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:984](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L984>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2179](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2179>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:810](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L810>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:6089](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6089>), [ValidationClient.java:6093](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6093>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:5758](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5758>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:238](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:5279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5279>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:6265](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6265>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:3331](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3331>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:5270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5270>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:4032](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4032>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:4015](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4015>), [ValidationClient.java:6027](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6027>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:3298](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3298>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3899](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3899>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:5590](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5590>), [ValidationClient.java:9748](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9748>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:887](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L887>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:7469](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7469>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:903](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L903>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:987](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L987>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L916>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:7214](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7214>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L888>), [ValidationClient.java:5605](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5605>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:914](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L914>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:865](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L865>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1008](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1008>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1714](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1714>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1149](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1149>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:5355](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5355>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2090](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2090>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:5553](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5553>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:5387](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5387>) |
| `celtium.validation.shadowStage` | `Boolean.getBoolean` | [ValidationClient.java:3815](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3815>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:4025](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4025>), [ValidationClient.java:6029](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6029>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:5265](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5265>), [ValidationClient.java:6338](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6338>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:3470](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3470>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:5686](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5686>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2268>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2335](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2335>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:5823](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5823>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3186>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:6159](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6159>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:8287](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8287>), [ValidationClient.java:8581](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8581>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:6061](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6061>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:4594](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4594>), [ValidationClient.java:5252](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5252>), [ValidationClient.java:6319](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6319>), [ValidationClient.java:6327](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6327>), [ValidationClient.java:6364](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6364>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:4595](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4595>), [ValidationClient.java:5253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5253>), [ValidationClient.java:6320](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6320>), [ValidationClient.java:6328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6328>), [ValidationClient.java:6365](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6365>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:5619](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5619>), [ValidationClient.java:5751](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5751>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1598](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1598>), [ValidationClient.java:1715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1715>), [ValidationClient.java:9619](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9619>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:3591](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3591>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:83](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17367>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2903>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2849>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L812>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L422>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L51>), [VulkanTerrainGpuBackend.java:17975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17975>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14019>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3220>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L52>), [VulkanContext.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L385>), [VulkanTerrainGpuBackend.java:17976](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17976>) |
| `celtium.vulkan.brackets` | `Boolean.getBoolean` | [FramePassTimings.java:120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L120>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3429>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17116>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3103>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17084>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3104>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1381>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1654>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L189>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3210>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2489>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2499>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2500>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L731>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L32>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17984>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2137>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15316>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2140>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2123>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2126>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:3170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3170>), [ValidationClient.java:6084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6084>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:3182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3182>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2118>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2120>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L18>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17477](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17477>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17472>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1895>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2455](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2455>), [ValidationClient.java:2574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2574>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>), [VulkanTerrainGpuBackend.java:3298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3298>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3940>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3589>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L210>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L487>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L205>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:11791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11791>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11794>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11773>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16704](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16704>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16696>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16700](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16700>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2532>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L281>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3100>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1218>) |
| `celtium.vulkan.glless` | `Boolean.getBoolean`, `System.getProperty` | [MappedStagingBuffer.java:62](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L62>), [CeleritasLoadingPlugin.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L32>), [CeltiumGllessHint.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L42>), [CeltiumGllessHint.java:810](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L810>), [CeltiumGllessSdlSwapTransformer.java:30](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L30>), [CeltiumGllessSplashTransformer.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L32>), [CeltiumNativeDraw.java:9](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java#L9>), [CeleritasVintageMixinPlugin.java:23](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23>), [MinecraftMixin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L50>) |
| `celtium.vulkan.gllessFrameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L456>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2010](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2010>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2050>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2012>), [VulkanTerrainGpuBackend.java:2028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2028>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2066>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2060>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2883>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L186>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2875>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1188>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L699>), [VulkanTerrainGpuBackend.java:3097](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3097>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:2552](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2552>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2083>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2093](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2093>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1098>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2864>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2866](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2866>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2868>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3089>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3765>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3777](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3777>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3783>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2957>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:570](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L570>) |
| `celtium.vulkan.immediateWindowSamples` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3307](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3307>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3035](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3035>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3073>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L341>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2262>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1756>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1911>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17348>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1996>) |
| `celtium.vulkan.mergeAcrossFormat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13978>) |
| `celtium.vulkan.mergeAcrossState` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13981>) |
| `celtium.vulkan.mergeAcrossTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13975>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13820>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13962>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:700](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L700>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L147>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3424>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L121>), [VulkanTerrainGpuBackend.java:9183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9183>), [VulkanTerrainGpuBackend.java:9309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9309>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3264>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1152>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17470>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17457](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17457>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14075>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L168>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13983](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13983>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:1279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1279>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:20](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L20>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11827](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11827>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11890>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2117>), [VulkanTerrainGpuBackend.java:11851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11851>) |
| `celtium.vulkan.passCounts` | `Boolean.getBoolean` | [FramePassTimings.java:319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L319>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L110>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L133>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L149>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2855](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2855>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11877>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1819>), [VulkanContext.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2117>), [VulkanContext.java:2370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2370>), [VulkanSwapchain.java:629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L629>), [VulkanTerrainGpuBackend.java:3062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3062>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L203>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L279>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11870>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11880>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11883>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L244>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3102>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L283>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2482>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13767>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L196>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11859>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1914>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3096>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3098>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:1839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1839>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3562>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipColorReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126>) |
| `celtium.vulkan.skipDepthReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3060>) |
| `celtium.vulkan.skipImmediateRecord` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13985](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13985>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2485>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11853](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11853>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L787>) |
| `celtium.vulkan.sortOpaque` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13838>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:3510](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3510>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:3531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3531>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:1818](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1818>), [VulkanContext.java:2116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2116>), [VulkanContext.java:2369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2369>), [VulkanSwapchain.java:628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L628>), [VulkanTerrainGpuBackend.java:18075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18075>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:5695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5695>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5686>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L262>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L112>), [VulkanTerrainGpuBackend.java:1916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1916>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11766>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14054>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L463>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:1803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1803>), [VulkanDiagnostics.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L33>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L854>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12246>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L174>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2767>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2143>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2146>) |
| `celtium.vulkan.withoutGlIdentity` | `Boolean.getBoolean` | [VulkanContext.java:3322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3322>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L322>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L42>), [VulkanTerrainFactory.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L108>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L506>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:7842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7842>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:161](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L161>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3954](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3954>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:4832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4832>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:4179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4179>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:257](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L257>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L739>), [VulkanTerrainPipeline.java:780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L780>), [VulkanTerrainPipeline.java:800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L800>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L309>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:6640](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6640>) |
| `CELTIUM_BIG_MIRROR` | [VulkanTextureRegistry.java:2020](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2020>) |
| `CELTIUM_BIND_CALLER` | [GlStateManagerMixin.java:244](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L244>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:5904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5904>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:5917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5917>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1096>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:192](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L192>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:518](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L518>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:8847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8847>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:8885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8885>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:9333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9333>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L799>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_BUFFER_CENSUS` | [VulkanTerrainGpuBackend.java:2636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2636>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:3188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3188>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:740](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L740>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:114](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L114>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:102](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L102>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1251>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1271>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:5048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5048>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:3362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3362>) |
| `CELTIUM_CHAIN_EVICT` | [GllessFrameTarget.java:1124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L1124>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [VulkanTerrainGpuBackend.java:17104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17104>), [VulkanTerrainGpuBackend.java:17149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17149>), [VulkanTerrainGpuBackend.java:17179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17179>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [VulkanTerrainGpuBackend.java:14533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14533>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanTerrainGpuBackend.java:14564](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14564>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:10390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10390>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:10602](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10602>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:3107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3107>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:13097](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13097>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:6668](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6668>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2733](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2733>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:343](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L343>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:385](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L385>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L371>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1341>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:16420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16420>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:2523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2523>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L550>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L767>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L64>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3047>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2130>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:2420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2420>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:2507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2507>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:19541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19541>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:3421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3421>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:3438](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3438>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:3518](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3518>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:5951](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5951>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:5945](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5945>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:3529](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3529>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:6877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6877>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:3584](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3584>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:3540](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3540>), [ValidationClient.java:6473](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6473>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:4321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4321>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L390>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1116>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L336>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L134>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L88>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L104>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L445>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L418>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L364>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1099>), [CeltiumGlStub.java:3809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3809>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:6281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6281>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:6769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6769>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:6336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6336>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:12489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12489>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L102>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:15565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15565>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [VulkanTerrainGpuBackend.java:2648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2648>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:18981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18981>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2268>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L395>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:104](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L104>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3860](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3860>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:3477](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3477>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:3487](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3487>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:7126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7126>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:7134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7134>) |
| `CELTIUM_ENV_COMBINE` | [VulkanTerrainGpuBackend.java:4635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4635>) |
| `CELTIUM_ENV_SEEN` | [VulkanTerrainGpuBackend.java:4645](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4645>) |
| `CELTIUM_ENV_WRITE` | [CeltiumGlStub.java:5438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5438>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L293>), [VulkanTextureRegistry.java:345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L345>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:173](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L173>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:120](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L120>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:82](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L82>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:5812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5812>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:5854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5854>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:19533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19533>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:5199](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5199>), [ValidationClient.java:5213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5213>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3178>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1309>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:6691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6691>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:6725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6725>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:6714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6714>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1108>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L761>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:3954](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3954>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L411>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1246](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1246>), [CeltiumFontGlyphCapture.java:1380](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1380>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1221](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1221>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1362](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1362>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1439](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1439>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:426](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L426>), [CeltiumFontGlyphCapture.java:430](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L430>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:662](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L662>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:9098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9098>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:6177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6177>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L344>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L134>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L84>) |
| `CELTIUM_FRAME_STATS` | [VulkanTerrainGpuBackend.java:2675](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2675>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:12903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12903>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:13017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13017>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1102>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:4884](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4884>) |
| `CELTIUM_GLLESS` | [CeltiumGlStub.java:1049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1049>), [CeltiumGlStub.java:1052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1052>), [CeleritasLoadingPlugin.java:43](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L43>), [CeltiumGllessHint.java:828](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L828>), [CeltiumGllessHint.java:832](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L832>), [CeltiumGllessNativeTransformer.java:89](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L89>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L54>), [CeltiumGllessSplashTransformer.java:69](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L69>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:58](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L58>), [CeleritasVintageMixinPlugin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L60>), [MinecraftMixin.java:467](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L467>), [MinecraftMixin.java:473](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L473>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5189>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5215>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:602](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L602>), [CeltiumFontGlyphCapture.java:691](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L691>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1045](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1045>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:19965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19965>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:4788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4788>) |
| `CELTIUM_GROUP_BOUNDS` | [VulkanTerrainGpuBackend.java:8112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8112>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:10816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10816>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:12461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12461>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:19180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19180>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:19218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19218>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:8178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8178>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:410](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L410>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:5594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5594>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3889](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3889>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:3678](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3678>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2551>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:3759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3759>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L533>), [VulkanImmediateTexturePipeline.java:778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L778>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:10730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10730>) |
| `CELTIUM_IMMEDIATE_FOG` | [VulkanTerrainGpuBackend.java:4054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4054>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:4201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4201>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:18581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18581>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:183](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L183>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LEVEL0` | [VulkanGlTexture.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1458>), [VulkanGlTexture.java:3526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3526>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L584>), [VulkanTextureRegistry.java:1606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1606>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:6694](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6694>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:9541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9541>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:9592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9592>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:9571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9571>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:9623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9623>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:12306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12306>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:6663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6663>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:486](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L486>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:757](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L757>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L55>), [ValidationClient.java:7511](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7511>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [CeltiumGlStub.java:5386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5386>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:10714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10714>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:18589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18589>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:19016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19016>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1436>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1702>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1819>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:18600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18600>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3026>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3040](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3040>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1200>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1762>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:1901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1901>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1377>) |
| `CELTIUM_MODELVIEW_EXACT` | [VulkanTerrainGpuBackend.java:18401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18401>) |
| `CELTIUM_MODEL_BOUNDS` | [VulkanTerrainGpuBackend.java:4696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4696>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L351>) |
| `CELTIUM_MODEL_ENQ` | [VulkanTerrainGpuBackend.java:4683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4683>) |
| `CELTIUM_MODEL_ENTRY` | [VulkanTerrainGpuBackend.java:14104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14104>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_LAYER` | [VulkanTerrainGpuBackend.java:14201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14201>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L387>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L365>) |
| `CELTIUM_MODEL_MATRIX` | [VulkanTerrainGpuBackend.java:14262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14262>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1090>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1050>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L280>), [VulkanTerrainGpuBackend.java:14170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14170>) |
| `CELTIUM_MODEL_OFFTARGET` | [VulkanTerrainGpuBackend.java:14127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14127>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [VulkanTerrainGpuBackend.java:14134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14134>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [VulkanTerrainGpuBackend.java:4770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4770>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L320>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L500>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L493>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L513>) |
| `CELTIUM_MODEL_STATE` | [VulkanTerrainGpuBackend.java:14213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14213>) |
| `CELTIUM_MODEL_TEXTURE` | [GlStateManagerMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L84>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:10755](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10755>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:3950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3950>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:2013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2013>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:1088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1088>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:274](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L274>), [WorldVertexBufferUploaderMixin.java:287](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L287>) |
| `CELTIUM_OFFTARGET_SELFTEST` | [VulkanOffscreenTarget.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanOffscreenTarget.java#L280>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:6032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6032>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:411](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L411>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5020](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5020>) |
| `CELTIUM_OUTLINE_INPUT` | [VulkanTerrainGpuBackend.java:14585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14585>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L66>), [ClientArrayBufferOwnershipMixin.java:78](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L78>), [ClientArrayBufferOwnershipMixin.java:87](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L87>) |
| `CELTIUM_PACK_CAMERA` | [ShaderPackDrawProbe.java:1143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L1143>) |
| `CELTIUM_PACK_CAPTURE` | [VulkanTerrainGpuBackend.java:15832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15832>), [VulkanTerrainGpuBackend.java:15842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15842>), [VulkanTerrainGpuBackend.java:15874](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15874>), [VulkanTerrainGpuBackend.java:15890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15890>) |
| `CELTIUM_PACK_DRAW` | [VulkanTerrainGpuBackend.java:10582](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10582>), [VulkanTerrainGpuBackend.java:16026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16026>), [VulkanTerrainGpuBackend.java:16027](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16027>), [VulkanTerrainGpuBackend.java:16029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16029>), [VulkanTerrainGpuBackend.java:16036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16036>), [VulkanTerrainGpuBackend.java:16038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16038>), [VulkanTerrainGpuBackend.java:16312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16312>), [VulkanTerrainGpuBackend.java:16329](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16329>), [VulkanTerrainGpuBackend.java:16344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16344>) |
| `CELTIUM_PACK_DRAW_HOOK` | [VulkanTerrainGpuBackend.java:16253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16253>) |
| `CELTIUM_PACK_DRAW_SUBMIT` | [VulkanTerrainGpuBackend.java:8834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8834>) |
| `CELTIUM_PACK_FIT` | [ShaderPackDrawProbe.java:1183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L1183>) |
| `CELTIUM_PACK_GEOMETRY` | [VulkanTerrainGpuBackend.java:16268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16268>) |
| `CELTIUM_PACK_PIPELINE` | [VulkanTerrainGpuBackend.java:16128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16128>) |
| `CELTIUM_PACK_PROGRAM` | [VulkanTerrainGpuBackend.java:15987](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15987>), [VulkanTerrainGpuBackend.java:16048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16048>) |
| `CELTIUM_PACK_RERENDER` | [VulkanTerrainGpuBackend.java:16288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16288>) |
| `CELTIUM_PACK_RERENDER_GEOMETRY` | [VulkanTerrainGpuBackend.java:16292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16292>) |
| `CELTIUM_PACK_SHADER_DUMP` | [VulkanTerrainGpuBackend.java:16204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16204>) |
| `CELTIUM_PACK_SHADOW` | [VulkanTerrainGpuBackend.java:16281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16281>) |
| `CELTIUM_PACK_VERTEX_DUMP` | [ShaderPackDrawProbe.java:927](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L927>), [ShaderPackDrawProbe.java:935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L935>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:13469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13469>) |
| `CELTIUM_PAIR_LAYOUT` | [VulkanTerrainGpuBackend.java:15070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15070>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:10526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10526>) |
| `CELTIUM_PAIR_SKIP` | [VulkanTerrainGpuBackend.java:15008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15008>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:5701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5701>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L62>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1220>) |
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:5089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5089>), [CeltiumGlStub.java:5970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5970>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:7624](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7624>), [ValidationClient.java:7764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7764>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3020](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3020>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:378](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L378>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:360](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L360>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_POSTCHAIN` | [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1239>), [VulkanPostChain.java:1244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1244>), [VulkanTerrainGpuBackend.java:14607](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14607>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:5297](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5297>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1350>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:16487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16487>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:13328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13328>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:9578](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9578>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4074](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4074>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2117>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L148>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:16591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16591>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:16460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16460>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1100>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:10673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10673>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:4599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4599>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:5742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5742>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L574>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L228>) |
| `CELTIUM_READBACK_REFRESH` | [VulkanTextureRegistry.java:1950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1950>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1287>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1007>), [CeltiumGlStub.java:5981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5981>), [CeltiumGlStub.java:5990](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5990>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:5962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5962>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L999>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L867>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L173>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:607](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L607>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:276](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L276>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3917](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3917>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:164](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L164>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:612](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L612>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:8939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8939>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:6948](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6948>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:7095](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7095>), [ValidationClient.java:7096](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7096>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:7037](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7037>), [ValidationClient.java:7071](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7071>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:7198](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7198>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:7098](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7098>), [ValidationClient.java:7100](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7100>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:3650](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3650>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:3655](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3655>) |
| `CELTIUM_RETAINED_CENSUS` | [VulkanTerrainGpuBackend.java:2659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2659>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2639>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2281>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2323>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L33>), [CeltiumSdlEventTrace.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L38>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SHADERPACK` | [VulkanTerrainGpuBackend.java:16384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16384>), [VulkanTerrainGpuBackend.java:16387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16387>) |
| `CELTIUM_SHADER_COMPILE` | [VulkanTerrainGpuBackend.java:15950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15950>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L138>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:7151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7151>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:7162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7162>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:10801](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10801>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1086>), [VulkanGlTexture.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1121>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L529>), [VulkanGlTexture.java:608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L608>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L577>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L632>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L731>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L521>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:6865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6865>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:18139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18139>) |
| `CELTIUM_SPLASH_BAR` | [MinecraftMixin.java:389](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L389>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:9120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9120>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:9233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9233>), [VulkanTerrainGpuBackend.java:9238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9238>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:317](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L317>), [MinecraftMixin.java:322](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L322>), [MinecraftMixin.java:333](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L333>), [MinecraftMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L337>), [ValidationClient.java:7419](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7419>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:9146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9146>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:9300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9300>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:2860](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2860>), [VulkanGlTexture.java:2981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2981>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L733>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3606>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:1887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1887>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L213>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L705>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2220>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:1792](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1792>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:1818](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1818>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:13424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13424>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:595](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L595>) |
| `CELTIUM_TARGET_FOREIGN` | [VulkanTerrainGpuBackend.java:17603](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17603>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:5621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5621>) |
| `CELTIUM_TARGET_REFUSAL` | [VulkanTerrainGpuBackend.java:17590](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17590>), [VulkanTerrainGpuBackend.java:17610](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17610>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:12210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12210>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1237>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L115>), [VulkanTextureArray.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L133>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:17800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17800>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L56>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:686](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L686>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:5913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5913>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:665](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L665>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:5648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5648>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1105>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:5858](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5858>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:112](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L112>), [CeleritasWorldRenderer.java:126](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L126>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:412](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L412>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L701>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L844>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:3536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3536>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1089>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:6969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6969>), [WorldVertexBufferUploaderMixin.java:260](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L260>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:6970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6970>), [WorldVertexBufferUploaderMixin.java:527](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L527>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:370](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L370>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:144](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L144>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:2963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2963>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:91](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L91>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:3427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3427>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1138>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:1694](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1694>), [VulkanContext.java:1716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1716>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_DRAW` | [VulkanTerrainGpuBackend.java:10487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10487>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:9050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9050>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:546](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L546>), [MinecraftMixin.java:551](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L551>), [MinecraftMixin.java:570](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L570>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:15592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15592>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:7161](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7161>) |
| `CELTIUM_WINDOW_STATE` | [VulkanTerrainGpuBackend.java:2794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2794>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:165](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L165>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:171](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L171>), [RenderSectionManager.java:173](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L173>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:182](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L182>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:286](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L286>), [SimpleWorldRenderer.java:292](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L292>), [SimpleWorldRenderer.java:295](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L295>), [SimpleWorldRenderer.java:298](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L298>), [SimpleWorldRenderer.java:305](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L305>), [SimpleWorldRenderer.java:312](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L312>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4008>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:6931](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6931>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2090>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:450](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L450>), [VulkanImmediateTexturePipeline.java:639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L639>) |
