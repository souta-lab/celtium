# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **358 個**／文字列内マーカー **319 個**。
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
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.shaderpack](#group-celtium-shaderpack) | 1 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 3 |
| [celtium.validation](#group-celtium-validation) | 130 |
| [celtium.vulkan](#group-celtium-vulkan) | 172 |
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
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:8988](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8988>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:482](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L482>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:2527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2527>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:450](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L450>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:454](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L454>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12327>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L496>) |
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
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3746>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:99](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L815>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:5880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5880>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:2057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2057>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:6065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6065>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:2015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2015>), [CeltiumGlStub.java:2434](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2434>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:2429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2429>) |

<a id="group-celtium-itemMeshCache"></a>
## `celtium.itemMeshCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `System.getProperty` | [RenderItemMixin.java:77](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L77>) |

<a id="group-celtium-mirrorDump"></a>
## `celtium.mirrorDump.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15007>) |

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
| `celtium.shaderpack.path` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13911>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:4951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4951>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:51](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:253](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L253>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [VulkanDiagnostics.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L24>), [ValidationClient.java:3045](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3045>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1605](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1605>), [ValidationClient.java:3911](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3911>), [ValidationClient.java:9497](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9497>), [ValidationClient.java:10601](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10601>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1599](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1599>), [ValidationClient.java:1764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1764>), [ValidationClient.java:9490](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9490>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1790](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1790>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1778](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1778>), [ValidationClient.java:9491](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9491>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1797](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1797>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:5693](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5693>), [ValidationClient.java:5707](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5707>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:5700](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5700>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1245](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1245>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1022](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1022>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1241>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1242](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1242>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1244>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1243>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L293>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:5973](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5973>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L290>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L279>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L267>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:263](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L263>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:768](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L768>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L354>), [ValidationClient.java:2007](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2007>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3048>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2077](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2077>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:685](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L685>), [ValidationClient.java:1183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1183>), [ValidationClient.java:9514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9514>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:684](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L684>), [ValidationClient.java:1184](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1184>), [ValidationClient.java:9515](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9515>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:692](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L692>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:694](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L694>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:832](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L832>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:817](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L817>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2005](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2005>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2142](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2142>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2210](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2210>), [ValidationClient.java:5774](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5774>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2003](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2003>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2048>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [LoadingScreenRendererMixin.java:46](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L46>), [ValidationClient.java:7440](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7440>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3196](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3196>), [ValidationClient.java:3216](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3216>), [ValidationClient.java:5641](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5641>), [ValidationClient.java:7381](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7381>), [ValidationClient.java:7382](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7382>), [ValidationClient.java:7411](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7411>), [ValidationClient.java:7441](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7441>), [ValidationClient.java:7771](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7771>), [ValidationClient.java:8172](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8172>), [ValidationClient.java:8255](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8255>), [ValidationClient.java:9090](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9090>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:948](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L948>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:5642](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5642>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L958>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:5800](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5800>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3831](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3831>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4268>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:6148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6148>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:5548](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5548>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1127>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1275>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1268>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1270>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1277](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1277>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1283](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1283>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1285>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1280>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1273](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1273>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3965>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:5741](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5741>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1105>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1107](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1107>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:74](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:80](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1109>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:5271](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5271>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L761>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3955](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3955>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3853](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3853>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L884>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3945](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3945>), [ValidationClient.java:3972](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3972>), [ValidationClient.java:5773](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5773>), [ValidationClient.java:5965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5965>), [ValidationClient.java:6011](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6011>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:935](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L935>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:5478](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5478>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1626](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1626>), [ValidationClient.java:5489](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5489>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1623>), [ValidationClient.java:4740](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4740>), [ValidationClient.java:5409](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5409>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:5430](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5430>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3463](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3463>), [ValidationClient.java:3808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3808>), [ValidationClient.java:3946](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3946>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:5782](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5782>), [ValidationClient.java:5783](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5783>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1632](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1632>), [ValidationClient.java:5536](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5536>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:10607](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10607>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:984](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L984>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2177>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:810](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L810>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:6054](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6054>), [ValidationClient.java:6058](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6058>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:5723](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5723>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:238](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:5251](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5251>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:6230](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6230>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:3325](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3325>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:5242](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5242>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:4021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4021>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:4004](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4004>), [ValidationClient.java:5992](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5992>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:3292](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3292>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3888>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:5555](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5555>), [ValidationClient.java:9618](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9618>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:887](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L887>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:7429](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7429>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:903](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L903>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:987](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L987>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L916>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:7174](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7174>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L888>), [ValidationClient.java:5570](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5570>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:914](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L914>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:865](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L865>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1008](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1008>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1714](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1714>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1149](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1149>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:5327](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5327>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2088](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2088>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:5518](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5518>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:5352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5352>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:4014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4014>), [ValidationClient.java:5994](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5994>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:5237](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5237>), [ValidationClient.java:6298](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6298>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:3464](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3464>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:5651](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5651>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2266](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2266>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2333](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2333>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:5788](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5788>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3184](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3184>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:6124](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6124>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:8157](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8157>), [ValidationClient.java:8451](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8451>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:6026](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6026>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:4583](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4583>), [ValidationClient.java:5224](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5224>), [ValidationClient.java:6290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6290>), [ValidationClient.java:6324](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6324>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:4584](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4584>), [ValidationClient.java:5225](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5225>), [ValidationClient.java:6291](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6291>), [ValidationClient.java:6325](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6325>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:5584](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5584>), [ValidationClient.java:5716](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5716>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1598](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1598>), [ValidationClient.java:1715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1715>), [ValidationClient.java:9489](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9489>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:3585](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3585>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:83](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14857>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2216>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2162>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L617>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L353>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L54>), [VulkanTerrainGpuBackend.java:15457](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15457>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12349>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2518>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:55](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L55>), [VulkanContext.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L330>), [VulkanTerrainGpuBackend.java:15458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15458>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2705>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14606>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2416](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2416>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14574>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2417>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1302>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1545](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1545>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2508>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L584>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L23>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15466>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2015>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13612>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2018>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2001](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2001>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2004>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:3159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3159>), [ValidationClient.java:6049](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6049>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:3171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3171>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1996>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1998>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14967>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14962>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1778>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2453](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2453>), [ValidationClient.java:2572](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2572>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>), [VulkanTerrainGpuBackend.java:2596](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2596>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3208](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3208>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2865>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L189>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L439>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L184>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:10437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10437>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10440>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10419>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14211](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14211>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14203>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14207>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L251>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2413>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1144>) |
| `celtium.vulkan.glless` | `Boolean.getBoolean`, `System.getProperty` | [MappedStagingBuffer.java:62](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L62>), [CeleritasLoadingPlugin.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L32>), [CeltiumGllessHint.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L32>), [CeltiumGllessHint.java:782](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L782>), [CeltiumGllessSplashTransformer.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L32>), [CeleritasVintageMixinPlugin.java:23](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23>), [MinecraftMixin.java:36](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L36>) |
| `celtium.vulkan.gllessFrameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L427>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1888>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1928](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1928>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:1890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1890>), [VulkanTerrainGpuBackend.java:1906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1906>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1944>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1938>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2196>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L141>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2188>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1188>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:665](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L665>), [VulkanTerrainGpuBackend.java:2410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2410>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:2349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2349>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1961>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1971>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1024>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2177>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2179>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2181>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2402>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3033>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3045>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3051>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2270>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L548>) |
| `celtium.vulkan.immediateWindowSamples` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2605](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2605>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2348>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2386>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L295>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1647](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1647>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1794>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14838>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1874](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1874>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L635>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L143>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2700](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2700>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L121>), [VulkanTerrainGpuBackend.java:8049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8049>), [VulkanTerrainGpuBackend.java:8175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8175>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L141>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2562>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1078>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14960>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14947](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14947>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12405>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L168>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:1182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1182>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10473>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10536>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2117>), [VulkanTerrainGpuBackend.java:10497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10497>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L101>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L115>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2168>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10523>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:1738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1738>), [VulkanContext.java:2019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2019>), [VulkanContext.java:2272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2272>), [VulkanSwapchain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L539>), [VulkanTerrainGpuBackend.java:2375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2375>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L182>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L249>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10516>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10526>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10529>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L221>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2415](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2415>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L253>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12315>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L189>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10505](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10505>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1797>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2409](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2409>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2411>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:1705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1705>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2838>) |
| `celtium.vulkan.skipColorReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126>) |
| `celtium.vulkan.skipDepthReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2373>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10499>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L722>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:3000](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3000>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:3021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3021>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:1737](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1737>), [VulkanContext.java:2018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2018>), [VulkanContext.java:2271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2271>), [VulkanSwapchain.java:538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L538>), [VulkanTerrainGpuBackend.java:15557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15557>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4918>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4909](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4909>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L258>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L112>), [VulkanTerrainGpuBackend.java:1799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1799>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10412>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12384>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L408>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:1722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1722>), [VulkanDiagnostics.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L25>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L789>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10864>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L185>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2021>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2024>) |
| `celtium.vulkan.withoutGlIdentity` | `Boolean.getBoolean` | [VulkanContext.java:3119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3119>) |
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
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:6781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6781>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:150](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L150>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3943](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3943>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:4055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4055>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:3447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3447>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:257](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L257>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:650](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L650>), [VulkanTerrainPipeline.java:691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L691>), [VulkanTerrainPipeline.java:711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L711>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L305>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BIG_MIRROR` | [VulkanTextureRegistry.java:1880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1880>) |
| `CELTIUM_BIND_CALLER` | [GlStateManagerMixin.java:237](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L237>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:5127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5127>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:5140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5140>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L773>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:192](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L192>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:518](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L518>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:8199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8199>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L786>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:2985](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2985>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:714](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L714>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:114](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L114>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:102](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L102>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1251>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1271>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:5020](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5020>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:2645](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2645>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [VulkanTerrainGpuBackend.java:14594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14594>), [VulkanTerrainGpuBackend.java:14639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14639>), [VulkanTerrainGpuBackend.java:14669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14669>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [VulkanTerrainGpuBackend.java:12829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12829>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanTerrainGpuBackend.java:12860](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12860>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:9191](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9191>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:10472](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10472>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:2904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2904>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:11705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11705>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:5856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5856>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2731](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2731>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:343](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L343>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:385](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L385>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L364>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1197>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L537>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L754>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L42>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L38>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:2844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2844>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2032>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:16738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16738>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:3415](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3415>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:3432](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3432>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:3512](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3512>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:5916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5916>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:5910](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5910>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:3523](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3523>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:6837](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6837>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:3578](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3578>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:3534](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3534>), [ValidationClient.java:6433](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6433>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:3589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3589>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L369>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L793>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L315>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L121>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L75>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L91>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L424>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L397>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L343>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L776>), [CeltiumGlStub.java:3299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3299>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:5469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5469>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:5957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5957>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:5524](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5524>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:11097](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11097>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:16193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16193>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2170>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:104](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L104>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3849](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3849>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:3471](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3471>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:3481](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3481>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:6233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6233>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6241>) |
| `CELTIUM_ENV_COMBINE` | [VulkanTerrainGpuBackend.java:3858](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3858>) |
| `CELTIUM_ENV_SEEN` | [VulkanTerrainGpuBackend.java:3868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3868>) |
| `CELTIUM_ENV_WRITE` | [CeltiumGlStub.java:4703](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4703>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L288>), [VulkanTextureRegistry.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L327>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:173](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L173>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:120](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L120>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:5035](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5035>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:5077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5077>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:16730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16730>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:5171](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5171>), [ValidationClient.java:5185](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5185>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3176>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:5810](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5810>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:5844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5844>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:5833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5833>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L785>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L696>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:3222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3222>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L361>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1239](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1239>), [CeltiumFontGlyphCapture.java:1373](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1373>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1214](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1214>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1355](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1355>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1432](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1432>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:426](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L426>), [CeltiumFontGlyphCapture.java:430](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L430>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:662](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L662>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:7964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7964>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:5386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5386>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L272>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L125>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L84>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:11511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11511>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:11625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11625>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:4107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4107>) |
| `CELTIUM_GLLESS` | [CeltiumGlStub.java:726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L726>), [CeltiumGlStub.java:729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L729>), [CeleritasLoadingPlugin.java:43](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L43>), [CeltiumGllessHint.java:790](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L790>), [CeltiumGllessHint.java:794](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L794>), [CeltiumGllessNativeTransformer.java:89](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L89>), [CeltiumGllessSplashTransformer.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L54>), [CeltiumGllessSplashTransformer.java:69](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L69>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:58](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L58>), [CeleritasVintageMixinPlugin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L60>), [MinecraftMixin.java:391](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L391>), [MinecraftMixin.java:397](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L397>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:4412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4412>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:4438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4438>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:602](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L602>), [CeltiumFontGlyphCapture.java:691](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L691>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1038](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1038>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:17162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17162>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:4011](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4011>) |
| `CELTIUM_GROUP_BOUNDS` | [VulkanTerrainGpuBackend.java:7051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7051>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:9571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9571>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:11069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11069>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:16377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16377>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:16415](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16415>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:8048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8048>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:392](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L392>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:4817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4817>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3878](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3878>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:3672](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3672>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2540>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:3249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3249>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L389>), [VulkanImmediateTexturePipeline.java:631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L631>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:9494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9494>) |
| `CELTIUM_IMMEDIATE_FOG` | [VulkanTerrainGpuBackend.java:3322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3322>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:3469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3469>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:183](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L183>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LEVEL0` | [VulkanGlTexture.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1458>), [VulkanGlTexture.java:3515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3515>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L555>), [VulkanTextureRegistry.java:1473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1473>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:5813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5813>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:8419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8419>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:8470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8470>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:8449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8449>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:8501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8501>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:10924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10924>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:5782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5782>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:486](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L486>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:731](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L731>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L55>), [ValidationClient.java:7471](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7471>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:9478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9478>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:15895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15895>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1436>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1702>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1819>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:15906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15906>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3015>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3029>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1150>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1762>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:1901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1901>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1327>) |
| `CELTIUM_MODEL_BOUNDS` | [VulkanTerrainGpuBackend.java:3919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3919>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L334>) |
| `CELTIUM_MODEL_ENQ` | [VulkanTerrainGpuBackend.java:3906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3906>) |
| `CELTIUM_MODEL_LAYER` | [VulkanTerrainGpuBackend.java:12508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12508>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L387>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L365>) |
| `CELTIUM_MODEL_MATRIX` | [VulkanTerrainGpuBackend.java:12571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12571>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L962>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L922>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L265>), [VulkanTerrainGpuBackend.java:12480](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12480>) |
| `CELTIUM_MODEL_OFFTARGET` | [VulkanTerrainGpuBackend.java:12437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12437>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [VulkanTerrainGpuBackend.java:12444](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12444>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [VulkanTerrainGpuBackend.java:3993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3993>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L320>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L500>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L493>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L513>) |
| `CELTIUM_MODEL_STATE` | [VulkanTerrainGpuBackend.java:12520](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12520>) |
| `CELTIUM_MODEL_TEXTURE` | [GlStateManagerMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L84>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:10625](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10625>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:3440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3440>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:1932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1932>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L765>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:274](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L274>), [WorldVertexBufferUploaderMixin.java:287](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L287>) |
| `CELTIUM_OFFTARGET_SELFTEST` | [VulkanOffscreenTarget.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanOffscreenTarget.java#L280>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:5255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5255>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:411](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L411>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:4243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4243>) |
| `CELTIUM_OUTLINE_INPUT` | [VulkanTerrainGpuBackend.java:12881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12881>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L66>), [ClientArrayBufferOwnershipMixin.java:78](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L78>), [ClientArrayBufferOwnershipMixin.java:87](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L87>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:12053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12053>) |
| `CELTIUM_PAIR_LAYOUT` | [VulkanTerrainGpuBackend.java:13366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13366>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:9327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9327>) |
| `CELTIUM_PAIR_SKIP` | [VulkanTerrainGpuBackend.java:13304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13304>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:4933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4933>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1170>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:7548](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7548>), [ValidationClient.java:7688](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7688>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:2817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2817>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:378](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L378>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:360](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L360>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_POSTCHAIN` | [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1239>), [VulkanPostChain.java:1244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1244>), [VulkanTerrainGpuBackend.java:12903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12903>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:5269](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5269>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1350>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:14007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14007>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:11924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11924>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:9448](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9448>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4063](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4063>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:1741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1741>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L148>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:14098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14098>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L900>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:9437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9437>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:3948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3948>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:4965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4965>) |
| `CELTIUM_READBACK_REFRESH` | [VulkanTextureRegistry.java:1814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1814>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1287>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L973>), [CeltiumGlStub.java:5181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5181>), [CeltiumGlStub.java:5190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5190>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L854>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L173>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L469>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:276](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L276>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3906](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3906>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:167](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L167>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:611](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L611>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:7805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7805>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:6908](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6908>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:7055](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7055>), [ValidationClient.java:7056](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7056>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:6997](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6997>), [ValidationClient.java:7031](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7031>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:7158](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7158>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:7058](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7058>), [ValidationClient.java:7060](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7060>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:3644](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3644>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:3649](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3649>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2628>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2270>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2312>) |
| `CELTIUM_SHADERPACK` | [VulkanTerrainGpuBackend.java:13961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13961>), [VulkanTerrainGpuBackend.java:13964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13964>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:6258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6258>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6269>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1086>), [VulkanGlTexture.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1121>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L529>), [VulkanGlTexture.java:608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L608>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L577>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L632>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L731>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L492>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:6053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6053>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:7986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7986>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:8099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8099>), [VulkanTerrainGpuBackend.java:8104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8104>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L290>), [MinecraftMixin.java:295](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L295>), [MinecraftMixin.java:306](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L306>), [MinecraftMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L310>), [ValidationClient.java:7379](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7379>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:8012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8012>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:8166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8166>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:2849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2849>), [VulkanGlTexture.java:2970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2970>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L720>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3595>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:1770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1770>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L162>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:1844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1844>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:12008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12008>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:515](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L515>) |
| `CELTIUM_TARGET_FOREIGN` | [VulkanTerrainGpuBackend.java:15093](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15093>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:4844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4844>) |
| `CELTIUM_TARGET_REFUSAL` | [VulkanTerrainGpuBackend.java:15080](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15080>), [VulkanTerrainGpuBackend.java:15100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15100>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:10828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10828>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1140>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:15282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15282>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L41>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:686](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L686>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:5145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5145>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:652](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L652>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:4880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4880>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L782>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:5090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5090>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:112](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L112>), [CeleritasWorldRenderer.java:126](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L126>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:394](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L394>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L656>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L796>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:3026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3026>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L766>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:6085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6085>), [WorldVertexBufferUploaderMixin.java:260](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L260>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:6086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6086>), [WorldVertexBufferUploaderMixin.java:527](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L527>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:370](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L370>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:144](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L144>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:2760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2760>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:91](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L91>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:2917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2917>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1073>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:1616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1616>), [VulkanContext.java:1638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1638>) |
| `CELTIUM_WINDOW_DRAW` | [VulkanTerrainGpuBackend.java:9288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9288>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:7916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7916>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:466](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L466>), [MinecraftMixin.java:471](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L471>), [MinecraftMixin.java:490](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L490>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:13861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13861>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:7121](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7121>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:165](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L165>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:171](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L171>), [RenderSectionManager.java:173](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L173>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:182](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L182>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:286](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L286>), [SimpleWorldRenderer.java:292](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L292>), [SimpleWorldRenderer.java:295](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L295>), [SimpleWorldRenderer.java:298](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L298>), [SimpleWorldRenderer.java:305](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L305>), [SimpleWorldRenderer.java:312](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L312>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:3276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3276>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:6891](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6891>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2090>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L314>), [VulkanImmediateTexturePipeline.java:501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L501>) |
