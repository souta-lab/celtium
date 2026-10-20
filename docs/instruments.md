# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **378 個**／文字列内マーカー **354 個**。
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
| [celtium.debug](#group-celtium-debug) | 8 |
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
| [celtium.shaderpack](#group-celtium-shaderpack) | 4 |
| [celtium.shadow](#group-celtium-shadow) | 13 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 3 |
| [celtium.validation](#group-celtium-validation) | 131 |
| [celtium.vulkan](#group-celtium-vulkan) | 174 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L268>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:9131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9131>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:482](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L482>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:2914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2914>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:450](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L450>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:454](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L454>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12541>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L514>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L104>) |

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
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3756>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:99](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1118>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:6539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6539>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:2444](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2444>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:6724](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6724>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:2402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2402>), [CeltiumGlStub.java:2821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2821>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:2816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2816>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15963>) |

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
| `celtium.shaderpack.compileProbe` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14304>) |
| `celtium.shaderpack.drawProbe` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14155>) |
| `celtium.shaderpack.drawProbeDebug` | `Boolean.getBoolean` | [ShaderPackDrawProbe.java:625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L625>), [VulkanTerrainGpuBackend.java:14567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14567>) |
| `celtium.shaderpack.path` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14145>) |

<a id="group-celtium-shadow"></a>
## `celtium.shadow.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shadow.diag` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14791>) |
| `celtium.shadow.direction` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14651>) |
| `celtium.shadow.dummySample` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:703](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L703>) |
| `celtium.shadow.enabled` | `Boolean.getBoolean` | [VulkanImmediateBlockPipeline.java:415](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L415>), [VulkanImmediateItemPipeline.java:644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L644>), [VulkanImmediateTexturePipeline.java:870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L870>), [VulkanTerrainPipeline.java:1281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1281>), [ShadowMapPass.java:111](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L111>) |
| `celtium.shadow.inFrame` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14742>) |
| `celtium.shadow.maxDraws` | `Integer.getInteger` | [ShadowMapPass.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L577>) |
| `celtium.shadow.noBarriers` | `Boolean.getBoolean` | [ShadowMapPass.java:565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L565>) |
| `celtium.shadow.noDescriptors` | `Boolean.getBoolean` | [ShadowMapPass.java:546](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L546>), [ShadowMapPass.java:582](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L582>) |
| `celtium.shadow.noDraw` | `Boolean.getBoolean` | [ShadowMapPass.java:597](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shadow/ShadowMapPass.java#L597>) |
| `celtium.shadow.noPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14733>) |
| `celtium.shadow.noSources` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14692>) |
| `celtium.shadow.probeScope` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14702>) |
| `celtium.shadow.terrain` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14732>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:5595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5595>) |

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
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1605](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1605>), [ValidationClient.java:3918](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3918>), [ValidationClient.java:9521](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9521>), [ValidationClient.java:10625](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10625>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1599](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1599>), [ValidationClient.java:1764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1764>), [ValidationClient.java:9514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9514>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1790](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1790>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1778](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1778>), [ValidationClient.java:9515](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9515>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1797](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1797>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:5717](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5717>), [ValidationClient.java:5731](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5731>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:5724](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5724>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1245](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1245>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1022](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1022>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1241>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1242](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1242>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1244>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1243>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L293>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:5997](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5997>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L290>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L279>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L267>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:263](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L263>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:768](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L768>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L354>), [ValidationClient.java:2009](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2009>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3050>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2079>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:685](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L685>), [ValidationClient.java:1183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1183>), [ValidationClient.java:9538](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9538>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:684](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L684>), [ValidationClient.java:1184](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1184>), [ValidationClient.java:9539](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9539>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:692](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L692>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:694](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L694>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:832](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L832>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:817](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L817>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2007](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2007>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2144](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2144>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2212](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2212>), [ValidationClient.java:5798](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5798>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2005](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2005>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2050>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [LoadingScreenRendererMixin.java:46](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L46>), [ValidationClient.java:7464](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7464>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3198](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3198>), [ValidationClient.java:3218](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3218>), [ValidationClient.java:5665](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5665>), [ValidationClient.java:7405](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7405>), [ValidationClient.java:7406](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7406>), [ValidationClient.java:7435](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7435>), [ValidationClient.java:7465](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7465>), [ValidationClient.java:7795](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7795>), [ValidationClient.java:8196](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8196>), [ValidationClient.java:8279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8279>), [ValidationClient.java:9114](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9114>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:948](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L948>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:5666](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5666>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L958>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:5824](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5824>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3838](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3838>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4275>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:6172](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6172>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:5572](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5572>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1127>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1275>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1268>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1270>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1277](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1277>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1283](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1283>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1285>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1280>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1273](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1273>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3972](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3972>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:5765](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5765>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1105>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1107](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1107>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:74](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:80](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1109>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L761>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3962](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3962>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3860](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3860>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L884>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3952](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3952>), [ValidationClient.java:3979](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3979>), [ValidationClient.java:5797](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5797>), [ValidationClient.java:5989](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5989>), [ValidationClient.java:6035](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6035>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:935](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L935>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:5502](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5502>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1626](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1626>), [ValidationClient.java:5513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5513>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1623>), [ValidationClient.java:4764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4764>), [ValidationClient.java:5433](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5433>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:5454](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5454>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3465](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3465>), [ValidationClient.java:3815](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3815>), [ValidationClient.java:3953](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3953>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:5806](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5806>), [ValidationClient.java:5807](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5807>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1632](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1632>), [ValidationClient.java:5560](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5560>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:10631](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10631>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:984](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L984>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2179](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2179>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:810](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L810>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:6078](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6078>), [ValidationClient.java:6082](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6082>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:5747](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5747>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:238](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:5275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5275>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:6254](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6254>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:3327](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3327>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:5266](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5266>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:4028](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4028>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:4011](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4011>), [ValidationClient.java:6016](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6016>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:3294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3294>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3895](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3895>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:5579](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5579>), [ValidationClient.java:9642](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9642>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:887](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L887>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:7453](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7453>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:903](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L903>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:987](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L987>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L916>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:7198](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7198>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L888>), [ValidationClient.java:5594](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5594>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:914](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L914>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:865](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L865>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1008](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1008>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1714](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1714>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1149](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1149>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:5351](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5351>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2090](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2090>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:5542](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5542>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:5376](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5376>) |
| `celtium.validation.shadowStage` | `Boolean.getBoolean` | [ValidationClient.java:3811](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3811>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:4021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4021>), [ValidationClient.java:6018](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6018>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:5261](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5261>), [ValidationClient.java:6322](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6322>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:3466](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3466>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:5675](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5675>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2268>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2335](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2335>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:5812](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5812>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3186>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:6148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6148>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:8181](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8181>), [ValidationClient.java:8475](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8475>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:6050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6050>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:4590](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4590>), [ValidationClient.java:5248](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5248>), [ValidationClient.java:6314](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6314>), [ValidationClient.java:6348](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6348>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:4591](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4591>), [ValidationClient.java:5249](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5249>), [ValidationClient.java:6315](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6315>), [ValidationClient.java:6349](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6349>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:5608](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5608>), [ValidationClient.java:5740](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5740>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1598](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1598>), [ValidationClient.java:1715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1715>), [ValidationClient.java:9513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9513>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:3587](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3587>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:83](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15813>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2204>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2150>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L653>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L445>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L54>), [VulkanTerrainGpuBackend.java:16413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16413>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12563>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2506>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:55](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L55>), [VulkanContext.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L385>), [VulkanTerrainGpuBackend.java:16414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16414>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2715>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15562>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2404](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2404>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15530>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2405>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1290>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1533>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2496>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:615](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L615>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L32>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16422>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2003](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2003>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13846>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2006](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2006>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1989>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1992>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:3170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3170>), [ValidationClient.java:6073](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6073>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:3182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3182>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1984>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1986>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15923](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15923>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15918>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1766>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2455](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2455>), [ValidationClient.java:2574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2574>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>), [VulkanTerrainGpuBackend.java:2584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2584>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3218>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2875>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L189>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L441>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L184>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:10598](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10598>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10601>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10580](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10580>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15167>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15159>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15163>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L255>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2401>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1132>) |
| `celtium.vulkan.glless` | `Boolean.getBoolean`, `System.getProperty` | [MappedStagingBuffer.java:62](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L62>), [CeleritasLoadingPlugin.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L32>), [CeltiumGllessHint.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L32>), [CeltiumGllessHint.java:794](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L794>), [CeltiumGllessSplashTransformer.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L32>), [CeleritasVintageMixinPlugin.java:23](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23>), [MinecraftMixin.java:36](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L36>) |
| `celtium.vulkan.gllessFrameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L427>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1876>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1916>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:1878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1878>), [VulkanTerrainGpuBackend.java:1894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1894>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1932>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1926](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1926>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2184>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L164>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2176>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1188>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L653>), [VulkanTerrainGpuBackend.java:2398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2398>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:2469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2469>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1949](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1949>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1959>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1012>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2165>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2167>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2169>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2390>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3043>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3055>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3061>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2258>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:570](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L570>) |
| `celtium.vulkan.immediateWindowSamples` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2593>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2336>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2374>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L345>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1635>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1782>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15794>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1862>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12527>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:700](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L700>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L147>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2710>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L121>), [VulkanTerrainGpuBackend.java:8204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8204>), [VulkanTerrainGpuBackend.java:8330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8330>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2550>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1066>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15916>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15903>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12619>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L168>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:1342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1342>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10634>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10697>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2117>), [VulkanTerrainGpuBackend.java:10658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10658>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L110>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L127>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L149>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2156>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10684](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10684>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:1796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1796>), [VulkanContext.java:2087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2087>), [VulkanContext.java:2340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2340>), [VulkanSwapchain.java:575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L575>), [VulkanTerrainGpuBackend.java:2363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2363>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L182>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L253>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10677>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10687>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10690](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10690>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L223>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2403>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L257>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12490>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L189>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10666](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10666>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1785>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2397>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2399>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:1728](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1728>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2848>) |
| `celtium.vulkan.skipColorReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126>) |
| `celtium.vulkan.skipDepthReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2361>) |
| `celtium.vulkan.skipImmediateRecord` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12529>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10660>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L787>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:3421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3421>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:3442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3442>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:1795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1795>), [VulkanContext.java:2086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2086>), [VulkanContext.java:2339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2339>), [VulkanSwapchain.java:574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L574>), [VulkanTerrainGpuBackend.java:16513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16513>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4928](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4928>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4919>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L262>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L112>), [VulkanTerrainGpuBackend.java:1787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1787>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10573](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10573>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12598](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12598>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L463>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:1780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1780>), [VulkanDiagnostics.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L33>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L854>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11031>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L185>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2009](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2009>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2012>) |
| `celtium.vulkan.withoutGlIdentity` | `Boolean.getBoolean` | [VulkanContext.java:3239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3239>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L310>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L42>), [VulkanTerrainFactory.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L108>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:477](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L477>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:6875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6875>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:150](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L150>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3950](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3950>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:4065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4065>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:3457](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3457>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:257](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L257>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L764>), [VulkanTerrainPipeline.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L805>), [VulkanTerrainPipeline.java:825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L825>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L309>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:5847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5847>) |
| `CELTIUM_BIG_MIRROR` | [VulkanTextureRegistry.java:1905](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1905>) |
| `CELTIUM_BIND_CALLER` | [GlStateManagerMixin.java:244](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L244>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:5137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5137>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:5150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5150>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1076>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:192](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L192>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:518](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L518>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:7868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7868>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:7916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7916>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:8354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8354>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L786>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:3105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3105>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:740](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L740>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:114](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L114>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:102](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L102>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1251>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1271>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:5044](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5044>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:2648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2648>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [VulkanTerrainGpuBackend.java:15550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15550>), [VulkanTerrainGpuBackend.java:15595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15595>), [VulkanTerrainGpuBackend.java:15625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15625>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [VulkanTerrainGpuBackend.java:13063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13063>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanTerrainGpuBackend.java:13094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13094>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:9334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9334>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:10496](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10496>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:3024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3024>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:11877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11877>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:6515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6515>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2733](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2733>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:343](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L343>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:385](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L385>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L364>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1242](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1242>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L537>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L754>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L42>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L38>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:2964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2964>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2100>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:2425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2425>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:17740](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17740>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:3417](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3417>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:3434](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3434>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:3514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3514>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:5940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5940>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:5934](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5934>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:3525](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3525>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:6861](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6861>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:3580](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3580>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:3536](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3536>), [ValidationClient.java:6457](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6457>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:3599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3599>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L369>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1096>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L315>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L121>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L75>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L91>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L424>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L397>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L343>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1079>), [CeltiumGlStub.java:3720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3720>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:6128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6128>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:6616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6616>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:6183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6183>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:11269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11269>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L54>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:17184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17184>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2238>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L368>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:104](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L104>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3856](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3856>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:3473](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3473>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:3483](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3483>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:6327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6327>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6335>) |
| `CELTIUM_ENV_COMBINE` | [VulkanTerrainGpuBackend.java:3868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3868>) |
| `CELTIUM_ENV_SEEN` | [VulkanTerrainGpuBackend.java:3878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3878>) |
| `CELTIUM_ENV_WRITE` | [CeltiumGlStub.java:5314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5314>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L293>), [VulkanTextureRegistry.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L332>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:173](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L173>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:120](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L120>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:5045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5045>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:5087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5087>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:17732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17732>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:5195](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5195>), [ValidationClient.java:5209](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5209>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3178>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:5898](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5898>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:5932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5932>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:5921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5921>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1088>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L761>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:3232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3232>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L361>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1239](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1239>), [CeltiumFontGlyphCapture.java:1373](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1373>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1214](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1214>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1355](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1355>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1432](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1432>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:426](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L426>), [CeltiumFontGlyphCapture.java:430](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L430>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:662](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L662>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:8119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8119>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:5398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5398>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L309>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L125>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L84>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:11683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11683>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:11797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11797>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:4117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4117>) |
| `CELTIUM_GLLESS` | [CeltiumGlStub.java:1029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1029>), [CeltiumGlStub.java:1032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1032>), [CeleritasLoadingPlugin.java:43](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L43>), [CeltiumGllessHint.java:802](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L802>), [CeltiumGllessHint.java:806](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L806>), [CeltiumGllessNativeTransformer.java:89](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L89>), [CeltiumGllessSplashTransformer.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L54>), [CeltiumGllessSplashTransformer.java:69](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L69>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:58](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L58>), [CeleritasVintageMixinPlugin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L60>), [MinecraftMixin.java:391](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L391>), [MinecraftMixin.java:397](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L397>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:4422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4422>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:4448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4448>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:602](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L602>), [CeltiumFontGlyphCapture.java:691](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L691>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1038](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1038>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:18164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18164>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:4021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4021>) |
| `CELTIUM_GROUP_BOUNDS` | [VulkanTerrainGpuBackend.java:7145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7145>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:9731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9731>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:11241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11241>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:17379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17379>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:17417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17417>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:8072](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8072>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:404](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L404>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:4827](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4827>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3885](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3885>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:3674](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3674>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2551>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:3670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3670>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L445>), [VulkanImmediateTexturePipeline.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L663>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:9654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9654>) |
| `CELTIUM_IMMEDIATE_FOG` | [VulkanTerrainGpuBackend.java:3332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3332>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:3479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3479>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:183](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L183>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LEVEL0` | [VulkanGlTexture.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1458>), [VulkanGlTexture.java:3526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3526>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:570](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L570>), [VulkanTextureRegistry.java:1495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1495>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:5901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5901>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:8562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8562>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:8613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8613>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:8592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8592>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:8644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8644>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:11091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11091>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:5870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5870>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:486](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L486>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:757](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L757>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L55>), [ValidationClient.java:7495](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7495>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [CeltiumGlStub.java:5262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5262>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:9638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9638>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:16878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16878>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:17217](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17217>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1436>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1702>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1819>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:16889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16889>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3026>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3040](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3040>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1172>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1762>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:1901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1901>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1349>) |
| `CELTIUM_MODEL_BOUNDS` | [VulkanTerrainGpuBackend.java:3929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3929>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L334>) |
| `CELTIUM_MODEL_ENQ` | [VulkanTerrainGpuBackend.java:3916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3916>) |
| `CELTIUM_MODEL_ENTRY` | [VulkanTerrainGpuBackend.java:12648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12648>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_LAYER` | [VulkanTerrainGpuBackend.java:12742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12742>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L387>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L365>) |
| `CELTIUM_MODEL_MATRIX` | [VulkanTerrainGpuBackend.java:12805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12805>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:980](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L980>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L940>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L265>), [VulkanTerrainGpuBackend.java:12714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12714>) |
| `CELTIUM_MODEL_OFFTARGET` | [VulkanTerrainGpuBackend.java:12671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12671>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [VulkanTerrainGpuBackend.java:12678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12678>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [VulkanTerrainGpuBackend.java:4003](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4003>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L320>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L500>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L493>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L513>) |
| `CELTIUM_MODEL_STATE` | [VulkanTerrainGpuBackend.java:12754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12754>) |
| `CELTIUM_MODEL_TEXTURE` | [GlStateManagerMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L84>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:10649](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10649>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:3861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3861>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:1990](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1990>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:1068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1068>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:274](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L274>), [WorldVertexBufferUploaderMixin.java:287](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L287>) |
| `CELTIUM_OFFTARGET_SELFTEST` | [VulkanOffscreenTarget.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanOffscreenTarget.java#L280>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:5265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5265>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:411](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L411>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:4253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4253>) |
| `CELTIUM_OUTLINE_INPUT` | [VulkanTerrainGpuBackend.java:13115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13115>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L66>), [ClientArrayBufferOwnershipMixin.java:78](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L78>), [ClientArrayBufferOwnershipMixin.java:87](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L87>) |
| `CELTIUM_PACK_CAPTURE` | [VulkanTerrainGpuBackend.java:14273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14273>) |
| `CELTIUM_PACK_DRAW` | [VulkanTerrainGpuBackend.java:9520](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9520>), [VulkanTerrainGpuBackend.java:14400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14400>), [VulkanTerrainGpuBackend.java:14401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14401>), [VulkanTerrainGpuBackend.java:14403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14403>), [VulkanTerrainGpuBackend.java:14410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14410>), [VulkanTerrainGpuBackend.java:14412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14412>), [VulkanTerrainGpuBackend.java:14827](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14827>), [VulkanTerrainGpuBackend.java:14849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14849>), [VulkanTerrainGpuBackend.java:14864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14864>) |
| `CELTIUM_PACK_DRAW_HOOK` | [VulkanTerrainGpuBackend.java:14817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14817>) |
| `CELTIUM_PACK_DRAW_SUBMIT` | [VulkanTerrainGpuBackend.java:7855](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7855>) |
| `CELTIUM_PACK_FIT` | [ShaderPackDrawProbe.java:749](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L749>) |
| `CELTIUM_PACK_GEOMETRY` | [VulkanTerrainGpuBackend.java:14838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14838>) |
| `CELTIUM_PACK_PIPELINE` | [VulkanTerrainGpuBackend.java:14495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14495>) |
| `CELTIUM_PACK_PROGRAM` | [VulkanTerrainGpuBackend.java:14422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14422>) |
| `CELTIUM_PACK_SHADER_DUMP` | [VulkanTerrainGpuBackend.java:14571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14571>) |
| `CELTIUM_PACK_VERTEX_DUMP` | [ShaderPackDrawProbe.java:628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L628>), [ShaderPackDrawProbe.java:636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ShaderPackDrawProbe.java#L636>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:12224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12224>) |
| `CELTIUM_PAIR_LAYOUT` | [VulkanTerrainGpuBackend.java:13600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13600>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:9470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9470>) |
| `CELTIUM_PAIR_SKIP` | [VulkanTerrainGpuBackend.java:13538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13538>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:5577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5577>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L46>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1192>) |
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:4973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4973>), [CeltiumGlStub.java:5823](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5823>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:7572](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7572>), [ValidationClient.java:7712](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7712>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:2937](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2937>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:378](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L378>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:360](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L360>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_POSTCHAIN` | [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1239>), [VulkanPostChain.java:1244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1244>), [VulkanTerrainGpuBackend.java:13137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13137>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:5293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5293>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1350>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:14963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14963>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:12096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12096>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:9472](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9472>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4070](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4070>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:1876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1876>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L148>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:15054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15054>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:14936](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14936>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:936](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L936>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:9597](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9597>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:4490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4490>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:4975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4975>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:535](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L535>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L228>) |
| `CELTIUM_READBACK_REFRESH` | [VulkanTextureRegistry.java:1837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1837>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1287>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L973>), [CeltiumGlStub.java:5834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5834>), [CeltiumGlStub.java:5843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5843>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L854>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L173>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L489>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:276](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L276>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3913](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3913>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:164](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L164>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:612](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L612>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:7960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7960>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:6932](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6932>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:7079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7079>), [ValidationClient.java:7080](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7080>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:7021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7021>), [ValidationClient.java:7055](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7055>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:7182](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7182>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:7082](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7082>), [ValidationClient.java:7084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7084>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:3646](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3646>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:3651](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3651>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2639>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2281>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2323>) |
| `CELTIUM_SHADERPACK` | [VulkanTerrainGpuBackend.java:14904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14904>), [VulkanTerrainGpuBackend.java:14907](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14907>) |
| `CELTIUM_SHADER_COMPILE` | [VulkanTerrainGpuBackend.java:14333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14333>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:6352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6352>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6363>) |
| `CELTIUM_SHADOW_DIAG` | [VulkanTerrainGpuBackend.java:14760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14760>) |
| `CELTIUM_SHADOW_FAILED` | [VulkanTerrainGpuBackend.java:14794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14794>) |
| `CELTIUM_SHADOW_OWN` | [VulkanTerrainGpuBackend.java:14751](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14751>) |
| `CELTIUM_SHADOW_PROBE` | [VulkanTerrainGpuBackend.java:14712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14712>) |
| `CELTIUM_SHADOW_RECORD` | [VulkanTerrainGpuBackend.java:7890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7890>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:10695](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10695>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1086>), [VulkanGlTexture.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1121>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L529>), [VulkanGlTexture.java:608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L608>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L577>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L632>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L731>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L507>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:6712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6712>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:16577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16577>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:8141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8141>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:8254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8254>), [VulkanTerrainGpuBackend.java:8259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8259>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L290>), [MinecraftMixin.java:295](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L295>), [MinecraftMixin.java:306](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L306>), [MinecraftMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L310>), [ValidationClient.java:7403](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7403>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:8167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8167>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:8321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8321>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:2860](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2860>), [VulkanGlTexture.java:2981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2981>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L720>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3606>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:1758](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1758>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L162>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:1979](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1979>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:1576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1576>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:1602](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1602>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:12179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12179>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:515](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L515>) |
| `CELTIUM_TARGET_FOREIGN` | [VulkanTerrainGpuBackend.java:16049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16049>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:4854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4854>) |
| `CELTIUM_TARGET_REFUSAL` | [VulkanTerrainGpuBackend.java:16036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16036>), [VulkanTerrainGpuBackend.java:16056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16056>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:10995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10995>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1300>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:16238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16238>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L41>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:686](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L686>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:5789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5789>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:652](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L652>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:5524](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5524>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1085>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:5734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5734>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:112](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L112>), [CeleritasWorldRenderer.java:126](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L126>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:406](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L406>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L656>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L799>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:3447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3447>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1069>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:6176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6176>), [WorldVertexBufferUploaderMixin.java:260](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L260>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:6177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6177>), [WorldVertexBufferUploaderMixin.java:527](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L527>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:370](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L370>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:144](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L144>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:2880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2880>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:91](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L91>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:3338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3338>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1138>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:1671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1671>), [VulkanContext.java:1693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1693>) |
| `CELTIUM_WINDOW_DRAW` | [VulkanTerrainGpuBackend.java:9431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9431>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:8071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8071>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:466](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L466>), [MinecraftMixin.java:471](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L471>), [MinecraftMixin.java:490](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L490>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:14095](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14095>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:7145](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7145>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:165](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L165>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:171](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L171>), [RenderSectionManager.java:173](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L173>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:182](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L182>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:286](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L286>), [SimpleWorldRenderer.java:292](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L292>), [SimpleWorldRenderer.java:295](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L295>), [SimpleWorldRenderer.java:298](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L298>), [SimpleWorldRenderer.java:305](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L305>), [SimpleWorldRenderer.java:312](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L312>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:3286](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3286>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:6915](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6915>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2090>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L361>), [VulkanImmediateTexturePipeline.java:521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L521>) |
