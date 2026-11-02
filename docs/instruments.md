# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **476 個**／文字列内マーカー **445 個**。
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
| [celtium.allowCompetitors](#group-celtium-allowCompetitors) | 1 |
| [celtium.clearlatch](#group-celtium-clearlatch) | 1 |
| [celtium.debug](#group-celtium-debug) | 47 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.entityLightCache](#group-celtium-entityLightCache) | 1 |
| [celtium.f3](#group-celtium-f3) | 2 |
| [celtium.font](#group-celtium-font) | 10 |
| [celtium.fontAppend](#group-celtium-fontAppend) | 1 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 2 |
| [celtium.fontGlyph](#group-celtium-fontGlyph) | 1 |
| [celtium.forceIdentityTexmat](#group-celtium-forceIdentityTexmat) | 1 |
| [celtium.gless](#group-celtium-gless) | 1 |
| [celtium.glless](#group-celtium-glless) | 2 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.native](#group-celtium-native) | 4 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 4 |
| [celtium.validation](#group-celtium-validation) | 153 |
| [celtium.vulkan](#group-celtium-vulkan) | 221 |
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
| `celeritas.useVintageFastBlockRenderer` | `Boolean.getBoolean` | [ChunkBuilderMeshingTask.java:53](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.java#L53>) |

<a id="group-celtium-allowCompetitors"></a>
## `celtium.allowCompetitors.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.allowCompetitors` | `Boolean.getBoolean` | [CeltiumCompetitors.java:33](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java#L33>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L272>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10980](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10980>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15078>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15079>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15077>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L575>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14296>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:483](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L483>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L199>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L359>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15082>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15063>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14289>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3448>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3475>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2232>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L262>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L268>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L264>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:266](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L266>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L258>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:4692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4692>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14291>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14471](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14471>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14472>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [CeltiumGlStub.java:2316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2316>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:460](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L460>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:464](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L464>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L23>) |
| `celtium.debug.markTrace` | `Boolean.getBoolean` | [RenderGlobalMixin.java:305](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L305>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L433>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:77](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L77>), [VulkanModelInstances.java:158](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L158>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:8519](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8519>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:92](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L92>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14522>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3498>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14287>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3462>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:243](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L243>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L667>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3528](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3528>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3467>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3521>) |

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
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:40](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L40>), [EntityLightCacheMixin.java:33](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityLightCacheMixin.java#L33>) |

<a id="group-celtium-f3"></a>
## `celtium.f3.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.f3.fullSummary` | `Boolean.getBoolean` | [CeleritasVintage.java:81](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L81>) |
| `celtium.f3.sectionRefreshFrames` | `Integer.getInteger` | [CeleritasVintage.java:103](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L103>) |

<a id="group-celtium-font"></a>
## `celtium.font.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.font.batching` | `System.getProperty` | [FontBatch.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L224>) |
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:204](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L204>) |
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:348](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L348>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:216](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L216>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:257](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L257>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:226](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L226>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L233>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:247](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L247>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:271](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L271>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:238](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L238>) |

<a id="group-celtium-fontAppend"></a>
## `celtium.fontAppend.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L249>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:49](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L49>) |
| `celtium.fontAtlas.dump` | `System.getProperty` | [CeltiumFontAtlas.java:47](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L47>) |

<a id="group-celtium-fontGlyph"></a>
## `celtium.fontGlyph.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontGlyph.debug` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:50](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L50>) |

<a id="group-celtium-forceIdentityTexmat"></a>
## `celtium.forceIdentityTexmat.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4866](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4866>) |

<a id="group-celtium-gless"></a>
## `celtium.gless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.gless.legacyImmediate` | `System.getProperty` | [CeltiumGllessNativeTransformer.java:591](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L591>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:594](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L594>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1322](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1322>), [CeltiumGllessNativeTransformer.java:414](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L414>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1191](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1191>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:9563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9563>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:4163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4163>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:9755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9755>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:4599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4599>), [CeltiumGlStub.java:9394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9394>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:4594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4594>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2815>) |

<a id="group-celtium-native"></a>
## `celtium.native.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.native.cloudCells` | `System.getProperty` | [ForgeCloudCellsMixin.java:44](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L44>) |
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L35>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3248>) |
| `celtium.native.skipEmptyOutlineComposite` | `System.getProperty` | [RenderGlobalOutlineCompositeMixin.java:28](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L28>) |

<a id="group-celtium-parallelAtlas"></a>
## `celtium.parallelAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.parallelAtlas` | `System.getProperty` | [CeltiumAtlasLoader.java:49](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java#L49>) |

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
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:754](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L754>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:742](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L742>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:761](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L761>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:7993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7993>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:53](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L53>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:51](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:247](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L247>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [MinecraftMixin.java:321](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L321>), [ValidationClient.java:3252](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3252>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1692](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1692>), [ValidationClient.java:3579](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3579>), [ValidationClient.java:10381](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10381>), [ValidationClient.java:11627](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11627>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1686](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1686>), [ValidationClient.java:1868](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1868>), [ValidationClient.java:10374](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10374>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1894](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1894>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1882>), [ValidationClient.java:10375](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10375>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1901](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1901>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:6407](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6407>), [ValidationClient.java:6421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6421>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:6414](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6414>) |
| `celtium.validation.cameraEventProbe` | `Boolean.getBoolean` | [ValidationClient.java:3267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3267>) |
| `celtium.validation.cameraEventStartTick` | `Integer.getInteger` | [CameraEventProbe.java:10](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L10>) |
| `celtium.validation.cameraEventYaw` | `System.getProperty` | [CameraEventProbe.java:11](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L11>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1332](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1332>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1109>) |
| `celtium.validation.cameraSweepDegreesPerSecond` | `System.getProperty` | [ValidationClient.java:201](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L201>) |
| `celtium.validation.cameraSweepFromTick` | `Integer.getInteger` | [ValidationClient.java:203](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L203>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1328>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1329](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1329>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1331](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1331>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1330](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1330>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L323>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:366](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L366>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:6696](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6696>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:363](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L363>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L352>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:375](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L375>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:340](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L340>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:305](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L305>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:336](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L336>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:841](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L841>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L359>), [ValidationClient.java:2180](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2180>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3255](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3255>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2284](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2284>) |
| `celtium.validation.creativeTab` | `Integer.getInteger` | [ValidationClient.java:5885](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5885>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:758](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L758>), [ValidationClient.java:1270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1270>), [ValidationClient.java:10399](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10399>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:757](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L757>), [ValidationClient.java:1271](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1271>), [ValidationClient.java:10400](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10400>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:765](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L765>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:767](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L767>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:905](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L905>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:890](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L890>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2178>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2349](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2349>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2417](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2417>), [ValidationClient.java:6488](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6488>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2176>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2221](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2221>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2810](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2810>), [LoadingScreenRendererMixin.java:45](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L45>), [ValidationClient.java:8231](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8231>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3735](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3735>), [ValidationClient.java:3873](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3873>), [ValidationClient.java:3900](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3900>), [ValidationClient.java:6355](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6355>), [ValidationClient.java:8172](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8172>), [ValidationClient.java:8173](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8173>), [ValidationClient.java:8202](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8202>), [ValidationClient.java:8232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8232>), [ValidationClient.java:8652](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8652>), [ValidationClient.java:9053](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9053>), [ValidationClient.java:9136](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9136>), [ValidationClient.java:9971](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9971>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:1021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1021>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:6356](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6356>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:1031](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1031>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:6514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6514>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3499](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3499>) |
| `celtium.validation.entityType` | `System.getProperty` | [ValidationClient.java:1698](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1698>), [ValidationClient.java:1699](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1699>), [ValidationClient.java:3457](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3457>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4707](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4707>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:6883](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6883>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:6262](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6262>) |
| `celtium.validation.flightBlocksPerSecond` | `System.getProperty` | [ValidationClient.java:211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L211>) |
| `celtium.validation.flightFromTick` | `Integer.getInteger` | [ValidationClient.java:213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L213>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1214](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1214>) |
| `celtium.validation.framerate` | `Integer.getInteger` | [ValidationClient.java:3991](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3991>) |
| `celtium.validation.gameScreenshot` | `Boolean.getBoolean` | [ValidationClient.java:8603](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8603>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1362](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1362>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1355](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1355>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1357](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1357>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1364](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1364>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1370](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1370>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1372>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1367](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1367>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1360](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1360>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3633](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3633>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:6455](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6455>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1192](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1192>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1194](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1194>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:75](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L75>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:81](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L81>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1196](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1196>) |
| `celtium.validation.gvcA4Probe` | `Boolean.getBoolean` | [ValidationClient.java:1697](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1697>), [ValidationClient.java:3274](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3274>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:5768](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5768>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:834](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L834>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3623>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3521](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3521>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:957](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L957>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3613](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3613>), [ValidationClient.java:3640](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3640>), [ValidationClient.java:6487](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6487>), [ValidationClient.java:6688](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6688>), [ValidationClient.java:6734](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6734>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:1008](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1008>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:6072](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6072>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1729](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1729>), [ValidationClient.java:6083](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6083>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1723](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1723>), [ValidationClient.java:5233](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5233>), [ValidationClient.java:6003](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6003>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:6024](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6024>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3455](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3455>), [ValidationClient.java:3614](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3614>), [ValidationClient.java:4169](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4169>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:6496](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6496>), [ValidationClient.java:6497](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6497>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1735](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1735>), [ValidationClient.java:6130](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6130>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:11633](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11633>) |
| `celtium.validation.menuCaptureTicks` | `Integer.getInteger` | [ValidationClient.java:1069](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1069>) |
| `celtium.validation.menuFrameStats` | `Boolean.getBoolean` | [ValidationClient.java:6186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6186>), [ValidationClient.java:7010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7010>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1057](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1057>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2384](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2384>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:883](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L883>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:6789](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6789>), [ValidationClient.java:6793](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6793>) |
| `celtium.validation.modelGlint` | `Boolean.getBoolean` | [ValidationClient.java:3480](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3480>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.opaqueCopyProbe` | `Boolean.getBoolean` | [RawFboIdentityProbe.java:135](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L135>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:6437](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6437>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:311](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L311>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:326](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L326>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:5748](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5748>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:6965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6965>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:4030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4030>) |
| `celtium.validation.probePixel` | `System.getProperty` | [ValidationClient.java:5669](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5669>) |
| `celtium.validation.rawFboIdentityProbe` | `Boolean.getBoolean` | [ValidationClient.java:3270](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3270>), [ValidationClient.java:6675](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6675>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:5739](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5739>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:3689](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3689>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:3672](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3672>), [ValidationClient.java:6715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6715>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:3984](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3984>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3556](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3556>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:6269](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6269>), [ValidationClient.java:10503](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10503>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:960](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L960>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:8220](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8220>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:976](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L976>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1074](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1074>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:989](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L989>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:7965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7965>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:961](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L961>), [ValidationClient.java:6284](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6284>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:987](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L987>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:938](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L938>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1095](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1095>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1818](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1818>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1236](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1236>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:1719](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1719>), [ValidationClient.java:5824](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5824>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2295>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:6112](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6112>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:5867](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5867>) |
| `celtium.validation.shadowStage` | `Boolean.getBoolean` | [ValidationClient.java:3451](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3451>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:3682](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3682>), [ValidationClient.java:6717](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6717>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:6250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6250>), [ValidationClient.java:7079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7079>) |
| `celtium.validation.sparseTexture` | `Boolean.getBoolean` | [ValidationClient.java:2248](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2248>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:4170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4170>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:6365](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6365>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2473](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2473>) |
| `celtium.validation.testFogEnd` | `System.getProperty` | [ValidationClient.java:6142](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6142>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2540](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2540>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:6502](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6502>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3855](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3855>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:6859](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6859>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:9038](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9038>), [ValidationClient.java:9332](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9332>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:6760](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6760>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:3832](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3832>), [ValidationClient.java:5041](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5041>), [ValidationClient.java:5066](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5066>), [ValidationClient.java:6234](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6234>), [ValidationClient.java:7036](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7036>), [ValidationClient.java:7068](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7068>), [ValidationClient.java:7106](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7106>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:3833](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3833>), [ValidationClient.java:5042](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5042>), [ValidationClient.java:5067](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5067>), [ValidationClient.java:6235](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6235>), [ValidationClient.java:7037](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7037>), [ValidationClient.java:7069](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7069>), [ValidationClient.java:7107](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7107>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:6298](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6298>), [ValidationClient.java:6430](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6430>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1685](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1685>), [ValidationClient.java:1819](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1819>), [ValidationClient.java:10373](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10373>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:4291](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4291>) |
| `celtium.validation.worldSwitchMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:4303](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4303>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:84](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L84>) |
| `celtium.validation.worldType` | `System.getProperty` | [ValidationClient.java:4023](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4023>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16417>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L108>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3209>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3155>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2495>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>) |
| `celtium.vulkan.asyncPresent` | `System.getProperty` | [VulkanContext.java:1482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1482>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L449>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L51>), [VulkanTerrainGpuBackend.java:17073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17073>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14545](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14545>) |
| `celtium.vulkan.batchTexturePatches` | `System.getProperty` | [VulkanGlTexture.java:3768](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3768>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3654>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L52>), [VulkanContext.java:399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L399>), [VulkanTerrainGpuBackend.java:17074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17074>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L447>) |
| `celtium.vulkan.brackets` | `Boolean.getBoolean` | [FramePassTimings.java:120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L120>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3844>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3442>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3449>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1514>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:8932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8932>) |
| `celtium.vulkan.colorClearTransfer` | `System.getProperty` | [GllessFrameTarget.java:994](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L994>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L183>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1842>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L183>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3644>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L16>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2762>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2772>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2773>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L861>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L32>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17082>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2313>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14836](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14836>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2316>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2299>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2302>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2489>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2491](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2491>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L127>), [ValidationClient.java:6784](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6784>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L128>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2294>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2296>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16527>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16522>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2069>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2660](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2660>), [ValidationClient.java:2779](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2779>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3670>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:8875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8875>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4305>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4004>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L220>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L513>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L215>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L48>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L46>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:12363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12363>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12366>) |
| `celtium.vulkan.frameBudget` | `Boolean.getBoolean` | [FrameBudgetTimings.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L52>) |
| `celtium.vulkan.frameBudgetFrames` | `Integer.getInteger` | [FrameBudgetTimings.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L62>) |
| `celtium.vulkan.frameBudgetLines` | `Integer.getInteger` | [FrameBudgetTimings.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L72>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12345>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2814>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2808>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2813>) |
| `celtium.vulkan.frameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L464>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2805>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L316>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3439>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1351>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2186>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2226>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2188>), [VulkanTerrainGpuBackend.java:2204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2204>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2242](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2242>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2236>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3189>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L218>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3181>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1233>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2807>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:95](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L95>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2259>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2269>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1228>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3170>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3172>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3174>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3429>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L69>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L80>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L86>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3297>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:570](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L570>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3375>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3413>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L375>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2447>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1930>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2086>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16398>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2172>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:5595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5595>), [CeltiumGllessHint.java:1301](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1301>) |
| `celtium.vulkan.mergeAcrossFormat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14503>) |
| `celtium.vulkan.mergeAcrossState` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14506>) |
| `celtium.vulkan.mergeAcrossTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14500>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14285>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14475>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L734>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L212>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3839>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L125>), [VulkanTerrainGpuBackend.java:2812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2812>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L130>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L210>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L154>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L166>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L210>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3705>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1282>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16520](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16520>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16507>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14603](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14603>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelReplayFastPath` | `System.getProperty` | [ModelReplayFastPath.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L37>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L70>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L177>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14508>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L186>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L25>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12399>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12462>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2232>), [VulkanTerrainGpuBackend.java:12423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12423>) |
| `celtium.vulkan.passCounts` | `Boolean.getBoolean` | [FramePassTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L365>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L110>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L133>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L214>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L97>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2266](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2266>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3161>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12449>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:93](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L93>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:3402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3402>) |
| `celtium.vulkan.presentInterval` | `Boolean.getBoolean` | [FrameBudgetTimings.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L199>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L213>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L314>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12442>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12452>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12455](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12455>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L256>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3441>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2466>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2471](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2471>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2468>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L318>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2755>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14232>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L203>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12431>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2089>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3436>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3437>) |
| `celtium.vulkan.replayInputCache` | `System.getProperty` | [ModelReplayInputCache.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayInputCache.java#L69>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L47>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11063>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2018>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3977](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3977>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3400>) |
| `celtium.vulkan.skipImmediateRecord` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14510](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14510>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2757](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2757>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12425>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L373>) |
| `celtium.vulkan.sortOpaque` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14308>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:5165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5165>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:9396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9396>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L236>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L91>), [VulkanSwapchain.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L47>), [VulkanTerrainGpuBackend.java:2816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2816>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6137>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:6128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6128>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L287>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L116>), [VulkanTerrainGpuBackend.java:2091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2091>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12337>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11894>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11909](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11909>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14579](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14579>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:480](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L480>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanDiagnostics.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L33>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L867>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12867>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L181>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L193>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3083>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2319>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2322>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L315>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L39>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L514>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:8245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8245>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2249>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3611](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3611>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:5143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5143>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:4529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4529>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1026>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:307](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L307>) |
| `CELTIUM_ASYNC_PRESENT` | [VulkanContext.java:1540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1540>), [VulkanContext.java:1548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1548>), [VulkanContext.java:1603](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1603>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L785>), [VulkanTerrainPipeline.java:826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L826>), [VulkanTerrainPipeline.java:846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L846>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L335>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:12712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12712>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:7155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7155>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:6357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6357>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:6370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6370>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1145>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L238>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:519](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L519>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:9411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9411>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:9448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9448>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:9933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9933>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L889>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_BUFFER_CENSUS` | [VulkanTerrainGpuBackend.java:2920](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2920>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:3724](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3724>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:775](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L775>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:24](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L24>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:91](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L91>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:79](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L79>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1295>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1316>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:5513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5513>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:3786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3786>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [PostChainBridge.java:944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L944>), [VulkanTerrainGpuBackend.java:16189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16189>), [VulkanTerrainGpuBackend.java:16219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16219>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L219>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L250>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:11227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11227>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:11453](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11453>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:3643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3643>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:13645](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13645>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:9539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9539>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [CeltiumGlStub.java:9178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9178>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:4405](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4405>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:163](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L163>), [ForgeCloudCellsMixin.java:233](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L233>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2938](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2938>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:371](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L371>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:436](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L436>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:479](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L479>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L387>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L540>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1593>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CrashBreadcrumbs.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L61>), [CrashBreadcrumbs.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L80>) |
| `CELTIUM_CRUMB` | [CrashBreadcrumbs.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L72>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:15160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15160>), [VulkanTerrainGpuBackend.java:15162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15162>), [VulkanTerrainGpuBackend.java:15164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15164>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:15189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15189>), [VulkanTerrainGpuBackend.java:15191](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15191>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:15508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15508>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:15139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15139>), [VulkanTerrainGpuBackend.java:15142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15142>), [VulkanTerrainGpuBackend.java:15144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15144>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:15197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15197>), [VulkanTerrainGpuBackend.java:15200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15200>), [VulkanTerrainGpuBackend.java:15203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15203>), [VulkanTerrainGpuBackend.java:15205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15205>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:15117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15117>), [VulkanTerrainGpuBackend.java:15120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15120>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:2796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2796>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGlStub.java:1555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1555>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L635>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L857>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L743>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3583>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2655](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2655>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:2944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2944>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3036>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:18922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18922>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4121](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4121>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4138>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4218](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4218>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:6630](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6630>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:6624](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6624>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:4229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4229>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:7619](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7619>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:4284](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4284>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:4240](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4240>), [ValidationClient.java:7215](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7215>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:4695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4695>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L412>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1165>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L345>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L468>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L440>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L386>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1148>), [CeltiumGlStub.java:5462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5462>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:8810](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8810>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:9640](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9640>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:8865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8865>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:13101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13101>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:15226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15226>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [VulkanTerrainGpuBackend.java:2948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2948>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:18132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18132>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:18449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18449>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2793>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L430>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:124](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L124>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:162](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L162>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3517](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3517>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4177>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4187](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4187>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:7522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7522>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:7530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7530>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L368>), [VulkanTextureRegistry.java:421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L421>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:6270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6270>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:6313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6313>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:18914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18914>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:6169](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6169>), [ValidationClient.java:6183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6183>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3847](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3847>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1430>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:8917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8917>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:12576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12576>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:12577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12577>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:7207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7207>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:7241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7241>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:7230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7230>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1157>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L772>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:4319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4319>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L237>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L413>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1252](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1252>), [CeltiumFontGlyphCapture.java:1382](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1382>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1227](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1227>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1364](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1364>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1441](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1441>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L715>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:437](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L437>), [CeltiumFontGlyphCapture.java:441](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L441>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:777](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L777>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:9699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9699>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:6692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6692>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L275>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L314>), [FrameBudgetTimings.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L318>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L390>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L129>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [VulkanTerrainGpuBackend.java:2984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2984>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [VulkanTerrainGpuBackend.java:16692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16692>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L168>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:13451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13451>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:13565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13565>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1285>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5195>) |
| `CELTIUM_GLSTUB` | [CeltiumGlStub.java:1098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1098>), [CeltiumGlStub.java:1101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1101>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2407](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2407>), [CeltiumGllessHint.java:2411](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2411>), [CeltiumGllessNativeTransformer.java:144](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L144>), [CeltiumGllessSdlSwapTransformer.java:46](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L46>), [CeltiumGllessSplashTransformer.java:64](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L64>), [CeltiumGllessSplashTransformer.java:80](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L80>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L50>), [CeleritasVintageMixinPlugin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L52>), [MinecraftMixin.java:426](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L426>), [MinecraftMixin.java:432](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L432>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5511>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5539>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:612](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L612>), [CeltiumFontGlyphCapture.java:701](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L701>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1051](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1051>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:701](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L701>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:19347](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19347>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:5096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5096>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1190>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:11769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11769>) |
| `CELTIUM_GROUP_COMPOSITION` | [VulkanTerrainGpuBackend.java:2943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2943>), [VulkanTerrainGpuBackend.java:18183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18183>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:13073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13073>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L682>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:18373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18373>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:18411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18411>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:8929](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8929>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:950](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L950>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1342](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1342>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1370](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1370>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:6004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6004>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3546](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3546>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:4395](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4395>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2673>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:5412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5412>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L550>), [VulkanImmediateTexturePipeline.java:910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L910>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:11678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11678>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L224>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:7440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7440>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:4551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4551>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:17655](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17655>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:196](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L196>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L290>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2267>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L716>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [CeltiumGlStub.java:2634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2634>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [CeltiumGlStub.java:2345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2345>) |
| `CELTIUM_LEGACY_REFUSED` | [CeltiumGlStub.java:2336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2336>), [CeltiumGlStub.java:2340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2340>), [CeltiumGlStub.java:2514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2514>), [CeltiumGlStub.java:2639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2639>) |
| `CELTIUM_LEGACY_RUN` | [CeltiumGlStub.java:2679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2679>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L686>), [VulkanTextureRegistry.java:1769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1769>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:7210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7210>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:10171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10171>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:10161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10161>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:10250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10250>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:10281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10281>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:10313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10313>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:12912](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12912>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:7178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7178>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:496](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L496>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:792](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L792>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L60>), [ValidationClient.java:8262](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8262>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:111](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L111>), [CeltiumLoadingProgress.java:135](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L135>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L109>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [CeltiumGlStub.java:7720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7720>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:323](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L323>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:341](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L341>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:11662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11662>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:879](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L879>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:5626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5626>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:17663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17663>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:10891](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10891>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:18193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18193>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1485>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1769>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1924>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L121>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:17674](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17674>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3176>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3190>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1322>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1867>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2006](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2006>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:2775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2775>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1539>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L975>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L336>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L410>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L323>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L45>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:6154](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6154>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L463>), [ModelReplay.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L500>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L234>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L534>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L368>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L346>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L256>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1148>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1108>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L338>), [ModelReplay.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L112>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L67>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L74>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L414>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L301>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L483>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L476>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L496>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L346>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L306>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L318>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L246>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:11651](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11651>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:5603](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5603>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:2536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2536>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:1137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1137>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:324](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L324>), [WorldVertexBufferUploaderMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L337>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:6540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6540>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:332](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L332>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:422](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L422>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5336>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L271>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:13927](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13927>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L698>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:11356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11356>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:630](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L630>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:7975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7975>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L67>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L70>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1342>) |
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:7356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7356>), [CeltiumGlStub.java:8256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8256>) |
| `CELTIUM_PBO_UPLOAD` | [CeltiumGlStub.java:7195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7195>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:8375](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8375>), [ValidationClient.java:8515](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8515>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2286](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2286>), [VulkanContext.java:2305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2305>), [VulkanContext.java:2341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2341>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3556>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2173>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:472](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L472>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:453](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L453>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:443](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L443>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L293>), [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1245>), [VulkanPostChain.java:1250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1250>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:5766](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5766>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1356>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:15580](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15580>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:13786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13786>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:10332](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10332>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4502](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4502>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L216>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L305>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2238>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L155>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:15682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15682>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:15553](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15553>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1184>) |
| `CELTIUM_PROBE_BORROW` | [VulkanTerrainGpuBackend.java:18582](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18582>) |
| `CELTIUM_PROBE_DRAW` | [VulkanTerrainGpuBackend.java:18544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18544>) |
| `CELTIUM_PROBE_POISON` | [VulkanTerrainGpuBackend.java:18515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18515>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:11621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11621>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:6716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6716>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:6184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6184>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:33](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L33>), [RawFboIdentityProbe.java:257](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L257>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L609>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L244>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1337>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1145>), [CeltiumGlStub.java:8267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8267>), [CeltiumGlStub.java:8276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8276>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:8248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8248>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1137>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1005>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:216](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L216>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L715>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:386](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L386>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3574>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:162](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L162>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:622](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L622>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:9527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9527>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:7699](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7699>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:7846](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7846>), [ValidationClient.java:7847](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7847>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:7788](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7788>), [ValidationClient.java:7822](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7822>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:7949](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7949>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:7849](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7849>), [ValidationClient.java:7851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7851>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:4367](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4367>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:4372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4372>) |
| `CELTIUM_RETAINED_CENSUS` | [VulkanTerrainGpuBackend.java:2968](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2968>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2761>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2393](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2393>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2445>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:631](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L631>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L94>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L165>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:7546](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7546>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:7557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7557>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:11697](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11697>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1129>), [VulkanGlTexture.java:1164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1164>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:569](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L569>), [VulkanGlTexture.java:649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L649>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:618](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L618>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L673>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L772>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L622>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:9743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9743>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:17237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17237>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:9721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9721>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:9834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9834>), [VulkanTerrainGpuBackend.java:9839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9839>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:317](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L317>), [MinecraftMixin.java:325](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L325>), [MinecraftMixin.java:341](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L341>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [ValidationClient.java:8170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8170>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:9747](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9747>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:9901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9901>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3014](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3014>), [VulkanGlTexture.java:3130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3130>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L821>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3707>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:6212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6212>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2061>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L213>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L789>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L729>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2341>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:1913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1913>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:1939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1939>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:13882](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13882>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:569](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L569>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:6031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6031>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:12831](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12831>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1321>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L121>), [VulkanTextureArray.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L147>) |
| `CELTIUM_TEXTURE_DELETED` | [CeltiumGlStub.java:5790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5790>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:16887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16887>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L56>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L629>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CeltiumGlStub.java:6902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6902>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:801](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L801>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:8188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8188>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L753>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:7922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7922>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1154>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:8133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8133>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L663>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:110](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L110>), [CeleritasWorldRenderer.java:124](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L124>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:952](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L952>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:802](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L802>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L939>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:5191](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5191>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1138>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:7363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7363>), [WorldVertexBufferUploaderMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L310>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:7364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7364>), [WorldVertexBufferUploaderMixin.java:642](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L642>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:463](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L463>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:150](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L150>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:3541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3541>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:3499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3499>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L94>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:5081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5081>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1164>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1436>), [VulkanContext.java:1744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1744>), [VulkanContext.java:2028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2028>), [VulkanContext.java:2070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2070>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2033>), [VulkanContext.java:2059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2059>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L698>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:937](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L937>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L711>), [VulkanTerrainGpuBackend.java:11317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11317>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L935>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L933>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:9648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9648>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L973>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:518](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L518>), [MinecraftMixin.java:524](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L524>), [MinecraftMixin.java:544](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L544>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:15301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15301>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:7912](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7912>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1008>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L915>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L895>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L900>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:896](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L896>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:165](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L165>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:171](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L171>), [RenderSectionManager.java:173](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L173>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:182](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L182>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:287](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L287>), [SimpleWorldRenderer.java:293](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L293>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:299](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L299>), [SimpleWorldRenderer.java:306](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L306>), [SimpleWorldRenderer.java:313](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L313>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4373>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:7673](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7673>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2205>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L473>), [VulkanImmediateTexturePipeline.java:764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L764>) |
