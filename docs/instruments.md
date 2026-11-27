# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **394 個**／文字列内マーカー **643 個**。
機能設定・故障注入も含む。すべてが計測用スイッチという意味ではない。

名前を検索してリンク先の実装を読む。既定値・有効条件・出力上限は推測で掲載しない。
索引は `System.getProperty` / `Boolean.getBoolean` / `Integer.getInteger` / `Long.getLong` の
リテラルまたは単純な static String 定数引数だけを収録する。計算されたキー・別API・テストは対象外。
マーカーは接頭辞つきの文字列も収録するが、ログ出力以外の参照も含む。`--check` は鮮度検査であり網羅性の証明ではない。

手順と測定用途は [開発・検証手順](<development.md>)。全文を毎回ロードせず、用途から探す。

## 名前空間

| 名前空間 | 読み取りキー数 |
|---|---|
| [celeritas.useVintageFastBlockRenderer](#group-celeritas-useVintageFastBlockRenderer) | 1 |
| [celtium.allowCompetitors](#group-celtium-allowCompetitors) | 1 |
| [celtium.backendCounters](#group-celtium-backendCounters) | 1 |
| [celtium.clearlatch](#group-celtium-clearlatch) | 1 |
| [celtium.debug](#group-celtium-debug) | 53 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.dynamicLights](#group-celtium-dynamicLights) | 2 |
| [celtium.entityLightCache](#group-celtium-entityLightCache) | 1 |
| [celtium.f3](#group-celtium-f3) | 2 |
| [celtium.font](#group-celtium-font) | 8 |
| [celtium.fontAppend](#group-celtium-fontAppend) | 1 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 3 |
| [celtium.fontGlyph](#group-celtium-fontGlyph) | 1 |
| [celtium.forceIdentityTexmat](#group-celtium-forceIdentityTexmat) | 1 |
| [celtium.glcompat](#group-celtium-glcompat) | 1 |
| [celtium.glless](#group-celtium-glless) | 3 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.modelPhaseReport](#group-celtium-modelPhaseReport) | 1 |
| [celtium.native](#group-celtium-native) | 5 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.patch](#group-celtium-patch) | 2 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.releaseTextureCpuData](#group-celtium-releaseTextureCpuData) | 2 |
| [celtium.shaderc](#group-celtium-shaderc) | 2 |
| [celtium.shadowFrustumInitCache](#group-celtium-shadowFrustumInitCache) | 1 |
| [celtium.spirvCache](#group-celtium-spirvCache) | 2 |
| [celtium.terrain](#group-celtium-terrain) | 5 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 273 |
| [celtium.vulkanTerrain](#group-celtium-vulkanTerrain) | 1 |

<a id="group-celeritas-useVintageFastBlockRenderer"></a>
## `celeritas.useVintageFastBlockRenderer.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celeritas.useVintageFastBlockRenderer` | `Boolean.getBoolean` | [SodiumGameOptions.java:93](<../common/src/main/java/org/embeddedt/embeddium/impl/gui/SodiumGameOptions.java#L93>) |

<a id="group-celtium-allowCompetitors"></a>
## `celtium.allowCompetitors.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.allowCompetitors` | `Boolean.getBoolean` | [CeltiumCompetitors.java:130](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java#L130>) |

<a id="group-celtium-backendCounters"></a>
## `celtium.backendCounters.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.backendCounters` | `Boolean.getBoolean` | [BackendCallCounters.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendCallCounters.java#L42>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L780>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15590](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15590>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:23807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23807>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:23808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23808>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:23806](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23806>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [GlsmFrontend.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L272>) |
| `celtium.debug.bindlessSlotDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:25159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L25159>), [VulkanTextureRegistry.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L418>), [BindlessTextureBinding.java:59](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/bindless/BindlessTextureBinding.java#L59>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23279>) |
| `celtium.debug.chainInputPatchFrames` | `System.getProperty` | [ModPrograms.java:651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L651>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.deferEmptyCommitCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L134>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L322>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23811](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23811>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:23792](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23792>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23272>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4682>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4709>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:3604](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3604>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L476>), [VulkanSwapchain.java:2131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2131>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L412>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L418>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L414>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:416](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L416>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L408>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [GlsmFrontend.java:4893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4893>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23274>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23304>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:23305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23305>) |
| `celtium.debug.leafCullingCensus` | `Integer.getInteger`, `System.getProperty` | [LeafCullingCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/LeafCullingCensus.java#L22>), [LeafCullingCensus.java:26](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/LeafCullingCensus.java#L26>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [GlsmFrontend.java:2705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2705>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:501](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L501>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:505](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L505>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L32>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:633](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L633>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L487>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:99](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L99>), [VulkanModelInstances.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L180>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11901>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:128](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L128>) |
| `celtium.debug.packUniformLaneCensus` | `Boolean.getBoolean` | [PackUniformCensus.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L50>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.prepareNullCensus` | `Integer.getInteger`, `System.getProperty` | [PrepareNullCensus.java:114](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L114>), [PrepareNullCensus.java:118](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L118>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23345>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4732>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23270>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4696>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L280>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L826>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4762>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4701>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4755>) |

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

<a id="group-celtium-dynamicLights"></a>
## `celtium.dynamicLights.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.dynamicLights` | `System.getProperty` | [MinecraftDynamicLightService.java:50](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L50>) |
| `celtium.dynamicLights.poison` | `Boolean.getBoolean` | [TerrainRenderer.java:1142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1142>) |

<a id="group-celtium-entityLightCache"></a>
## `celtium.entityLightCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:67](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L67>) |

<a id="group-celtium-f3"></a>
## `celtium.f3.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.f3.fullSummary` | `Boolean.getBoolean` | [CeleritasVintage.java:90](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L90>) |
| `celtium.f3.sectionRefreshFrames` | `Integer.getInteger` | [CeleritasVintage.java:112](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L112>) |

<a id="group-celtium-font"></a>
## `celtium.font.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:195](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L195>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:207](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L207>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L248>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:217](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L217>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:238](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L238>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:262](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L262>) |
| `celtium.font.timings` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:356](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L356>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:229](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L229>) |

<a id="group-celtium-fontAppend"></a>
## `celtium.fontAppend.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L348>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:49](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L49>) |
| `celtium.fontAtlas.dump` | `System.getProperty` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |
| `celtium.fontAtlas.gray` | `System.getProperty` | [CeltiumFontAtlas.java:53](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L53>) |

<a id="group-celtium-fontGlyph"></a>
## `celtium.fontGlyph.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontGlyph.debug` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:50](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L50>) |

<a id="group-celtium-forceIdentityTexmat"></a>
## `celtium.forceIdentityTexmat.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [GroupChange.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupChange.java#L92>) |

<a id="group-celtium-glcompat"></a>
## `celtium.glcompat.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glcompat.forceOrphanStreaming` | `Boolean.getBoolean` | [PersistentStreamingBuffer.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/streaming/PersistentStreamingBuffer.java#L33>), [PersistentStreamingBuffer.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/streaming/PersistentStreamingBuffer.java#L42>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.callerCensus` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:607](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L607>) |
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:601](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L601>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1833](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1833>), [CeltiumGllessNativeTransformer.java:424](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L424>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [GlsmFrontend.java:1105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1105>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [GlsmFrontend.java:9353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9353>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTrampolines.java#L116>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [GlsmFrontend.java:9544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9544>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [GlsmFrontend.java:4651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4651>), [GlsmFrontend.java:9216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9216>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [GlsmFrontend.java:4646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4646>) |

<a id="group-celtium-gpuPassTimings"></a>
## `celtium.gpuPassTimings.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.gpuPassTimings` | `Boolean.getBoolean` | [GpuPassTimingReport.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L30>) |

<a id="group-celtium-itemMeshCache"></a>
## `celtium.itemMeshCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `System.getProperty` | [RenderItemMixin.java:77](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L77>) |

<a id="group-celtium-mirrorDump"></a>
## `celtium.mirrorDump.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.mirrorDump` | `System.getProperty` | [CopyReadSide.java:915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L915>) |

<a id="group-celtium-modelPhaseReport"></a>
## `celtium.modelPhaseReport.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.modelPhaseReport` | `Boolean.getBoolean` | [ModelPhaseTrace.java:34](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L34>) |

<a id="group-celtium-native"></a>
## `celtium.native.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.native.cloudCells` | `System.getProperty` | [ForgeCloudCellsMixin.java:44](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L44>) |
| `celtium.native.cloudCellsFastBuild` | `Boolean.getBoolean` | [ForgeCloudCellsMixin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L52>) |
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:37](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L37>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4470>) |
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
| `celtium.particleCache` | `System.getProperty` | [ParticleMixin.java:53](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleMixin.java#L53>) |

<a id="group-celtium-patch"></a>
## `celtium.patch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.patch.census` | `Boolean.getBoolean` | [PatchLedger.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L133>) |
| `celtium.patch.file` | `System.getProperty` | [PatchLedger.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L129>) |

<a id="group-celtium-probe"></a>
## `celtium.probe.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.probe.draws` | `Boolean.getBoolean` | [CloudDrawProbeMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L48>), [ParticleDrawProbeMixin.java:27](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L27>), [RainDrawProbeMixin.java:26](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L26>) |
| `celtium.probe.every` | `Integer.getInteger` | [CloudDrawProbeMixin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L50>), [ParticleDrawProbeMixin.java:29](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L29>), [RainDrawProbeMixin.java:28](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L28>) |
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:902](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L902>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:890](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L890>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:909](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L909>) |

<a id="group-celtium-releaseTextureCpuData"></a>
## `celtium.releaseTextureCpuData.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.releaseTextureCpuData` | `Boolean.getBoolean` | [CeltiumFrameRelease.java:43](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L43>) |
| `celtium.releaseTextureCpuData.exclude` | `System.getProperty` | [CeltiumFrameRelease.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L63>) |

<a id="group-celtium-shaderc"></a>
## `celtium.shaderc.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shaderc.maxConcurrent` | `Integer.getInteger` | [ModShaderCompiler.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L39>) |
| `celtium.shaderc.timeoutMs` | `Long.getLong` | [ModShaderCompiler.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L31>) |

<a id="group-celtium-shadowFrustumInitCache"></a>
## `celtium.shadowFrustumInitCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shadowFrustumInitCache` | `Boolean.getBoolean` | [DemonicaShadowFrustumInitCacheMixin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L52>) |

<a id="group-celtium-spirvCache"></a>
## `celtium.spirvCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.spirvCache.dir` | `System.getProperty` | [SpirvDiskCache.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/SpirvDiskCache.java#L67>) |
| `celtium.spirvCache.enabled` | `System.getProperty` | [SpirvDiskCache.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/SpirvDiskCache.java#L62>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:84](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L84>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:52](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L52>) |
| `celtium.terrain.engine` | `System.getProperty` | [TerrainEngineAHost.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L83>), [CeleritasWorldRenderer.java:417](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L417>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:413](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L413>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [HarnessConfig.java:70](<../celtium-core/src/harness/java/org/soutalab/celtium/harness/HarnessConfig.java#L70>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [CopyReadSide.java:820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L820>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L110>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4174>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4120>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L76>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L78>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L80>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L83>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L62>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:587](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L587>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23376>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4886>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5071>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4676](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4676>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4683>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2441>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [GlsmFrontend.java:349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L349>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6936](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6936>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12694](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12694>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L192>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2916>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4876>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3963>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3973>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3974>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTextureRecordsPipeline.java:965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L965>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:24463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24463>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3600>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23561>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3603](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3603>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3586>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3589>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3801](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3801>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3803>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3581>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3583>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:24365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24365>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:24359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24359>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3337>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:22](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L22>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4903>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12605](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12605>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5577>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L419>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5212>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L370>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L134>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L90>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1198>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L365>) |
| `celtium.vulkan.fractionalPowGuard` | `System.getProperty` | [ModGlslTranslator.java:1906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1906>) |
| `celtium.vulkan.fractionalPowGuard.census` | `Boolean.getBoolean` | [ModGlslTranslator.java:1916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1916>) |
| `celtium.vulkan.fractionalPowGuard.fastSqrt` | `Boolean.getBoolean` | [ModGlslTranslator.java:1911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1911>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L58>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L56>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:18040](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18040>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18043>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18022](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18022>) |
| `celtium.vulkan.frameRingSlotMiB` | `Integer.getInteger` | [FrameLinearRing.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/FrameLinearRing.java#L68>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23096>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L43>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L509>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4673>) |
| `celtium.vulkan.glDrawExempt` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13701>) |
| `celtium.vulkan.glDrawWindowCensus` | `Boolean.getBoolean` | [GlDrawWindowCensus.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L56>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2260>) |
| `celtium.vulkan.glsmPackState` | `System.getProperty` | [GlsmPackState.java:53](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmPackState.java#L53>) |
| `celtium.vulkan.gpuTerrainCulling` | `Boolean.getBoolean` | [GpuTerrainCulling.java:22](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L22>) |
| `celtium.vulkan.gpuTerrainCulling.census` | `Boolean.getBoolean` | [GpuTerrainCulling.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L30>) |
| `celtium.vulkan.gpuTerrainCulling.maxDistanceBlocks` | `System.getProperty` | [GpuTerrainCulling.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L73>) |
| `celtium.vulkan.gpuTerrainCulling.shadowCensus` | `Boolean.getBoolean` | [GpuTerrainCulling.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L41>) |
| `celtium.vulkan.gpuTerrainCulling.shadowDraw` | `Boolean.getBoolean` | [GpuTerrainCulling.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L60>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3442>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3482>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:3444](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3444>), [VulkanTerrainGpuBackend.java:3460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3460>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3498>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3492>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4154>) |
| `celtium.vulkan.groupDecayCensus` | `Boolean.getBoolean` | [GroupDecayCensus.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L24>) |
| `celtium.vulkan.groupDecayCensusFrames` | `Integer.getInteger` | [GroupDecayCensus.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L26>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L285>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4146>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1561>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4013>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L94>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3515>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3525](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3525>) |
| `celtium.vulkan.hudCensusFrames` | `Integer.getInteger` | [HudCensus.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L32>) |
| `celtium.vulkan.hudCensusMinFrame` | `Long.getLong` | [HudCensus.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L33>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3541>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3544>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2154>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4135](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4135>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4137>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4139>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L73>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L84>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L90>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4535](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4535>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L637>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4613>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:587](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L587>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3055>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4566>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3352>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [CopyReadSide.java:773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L773>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:7035](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7035>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3428>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6772>), [CeltiumGllessHint.java:1812](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1812>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L115>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23259>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23308>) |
| `celtium.vulkan.meshTerrain` | `Boolean.getBoolean` | [VulkanMeshTerrain.java:59](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L59>) |
| `celtium.vulkan.meshTerrainStage2` | `Boolean.getBoolean` | [VulkanMeshTaskDraw.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L38>) |
| `celtium.vulkan.meshTerrainStage2.quadsPerMeshWorkgroup` | `Integer.getInteger` | [VulkanMeshTaskDraw.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L48>) |
| `celtium.vulkan.meshUploadBudgetBytes` | `System.getProperty` | [MeshUploadBudget.java:71](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L71>) |
| `celtium.vulkan.meshUploadBudgetMiB` | `Long.getLong` | [MeshUploadBudget.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L67>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:1319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1319>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L300>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5066>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L144>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L224>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L168>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L180>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L298>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4932>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L108>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2195>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2081>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:1103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1103>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2082>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L636>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:24353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24353>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:24340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24340>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:23447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23447>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L92>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23333>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L195>) |
| `celtium.vulkan.nullRenderer` | `Boolean.getBoolean` | [NullRenderer.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L28>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packShadowCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L470>) |
| `celtium.vulkan.packShadowRebuildBudget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:450](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L450>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [VulkanTerrainGpuBackend.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L441>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18051>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18118>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:311](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L311>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:96](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L96>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:3650](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3650>) |
| `celtium.vulkan.pipelineCreateCensus` | `Boolean.getBoolean` | [PipelineCreateCensus.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L51>) |
| `celtium.vulkan.pipelineCreateCensusSlowMs` | `Long.getLong` | [PipelineCreateCensus.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L54>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L466>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4126>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L291>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L308>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15199>) |
| `celtium.vulkan.presentDrainFinishGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13731>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18105>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L92>), [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>), [VulkanTerrainGpuBackend.java:4640](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4640>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L363>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L507>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18098>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18108>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18111](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18111>) |
| `celtium.vulkan.presentUnbindFlushGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13714>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L511>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3956](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3956>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23091>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L57>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12707>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18087>) |
| `celtium.vulkan.recordsDedicatedPool` | `System.getProperty` | [InstanceDataStore.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java#L342>) |
| `celtium.vulkan.recordsRingSlotMiB` | `Integer.getInteger` | [RecordsRing.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/RecordsRing.java#L60>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3355>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4670>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4671>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L45>) |
| `celtium.vulkan.retainedIndexFlushExempt` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4663>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15731>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2671>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5199>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5905](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5905>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4623>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3958>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18081>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L859>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L895>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:937](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L937>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L939>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L911>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L893>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:884](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L884>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L876>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L585>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:23268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23268>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [GlsmFrontend.java:5620](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5620>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [GlsmFrontend.java:9218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9218>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L250>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L228>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:4030](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4030>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:8253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8253>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>), [GlsmFrontend.java:282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L282>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L403>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:8244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8244>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L411>) |
| `celtium.vulkan.terrainEngineA` | `System.getProperty` | [TerrainEngineAHost.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L84>), [CeleritasWorldRenderer.java:418](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L418>) |
| `celtium.vulkan.terrainMaxDraws` | `Integer.getInteger` | [TerrainFrameBudget.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L29>) |
| `celtium.vulkan.terrainMaxRebuildsPerFrame` | `Integer.getInteger` | [TerrainFrameBudget.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L30>) |
| `celtium.vulkan.terrainMaxVertices` | `Long.getLong` | [TerrainFrameBudget.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L28>) |
| `celtium.vulkan.terrainPreparePublishWalkOnce` | `Boolean.getBoolean` | [PostChainBridge.java:762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L762>) |
| `celtium.vulkan.terrainRebuildTargetMicros` | `Long.getLong` | [TerrainFrameBudget.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L32>) |
| `celtium.vulkan.terrainResidencyGate` | `System.getProperty` | [TerrainEngineA.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L73>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4281>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4284>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4188>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4268>) |
| `celtium.vulkan.terrainUploadBytes` | `System.getProperty` | [TerrainUploadBudget.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/TerrainUploadBudget.java#L51>) |
| `celtium.vulkan.terrainUploadMiB` | `Long.getLong` | [TerrainUploadBudget.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/TerrainUploadBudget.java#L56>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L130>), [VulkanTerrainGpuBackend.java:3357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3357>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18014](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18014>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3021>) |
| `celtium.vulkan.uploadBudgetMiB` | `Long.getLong` | [VulkanContext.java:1057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1057>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17118>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:23423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23423>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L592>) |
| `celtium.vulkan.upstreamArrivalCensus` | `Boolean.getBoolean` | [UpstreamArrivalCensus.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L28>) |
| `celtium.vulkan.upstreamArrivalCensusFrames` | `Integer.getInteger` | [UpstreamArrivalCensus.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L30>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:1727](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1727>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18823](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18823>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L218>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4047>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3606>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3609>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2618](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2618>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:845](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L845>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L35>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:11499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11499>) |
| `CELTIUM_ACCEPT_FLUSH` | [FramePassTimings.java:897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L897>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:236](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L236>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:3621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3621>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4170>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:6309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6309>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:5985](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5985>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1624](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1624>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L815>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:633](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L633>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L848>), [VulkanArena.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L857>), [VulkanArena.java:868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L868>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1102>), [VulkanTerrainPipeline.java:1143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1143>), [VulkanTerrainPipeline.java:1163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1163>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L470>) |
| `CELTIUM_BACKEND_CALLS` | [BackendCallCounters.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendCallCounters.java#L154>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:18625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18625>) |
| `CELTIUM_BATCH_CACHE` | [BatchCacheCensus.java:116](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L116>) |
| `CELTIUM_BATCH_ROUTE` | [VulkanTerrainGpuBackend.java:26761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26761>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:10231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10231>) |
| `CELTIUM_BATCH_VIEWS` | [BatchCacheCensus.java:83](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L83>), [BatchCacheCensus.java:86](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L86>) |
| `CELTIUM_BE_LOS` | [BeVisibilityProbe.java:154](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L154>), [BeVisibilityProbe.java:421](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L421>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:8876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8876>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:8889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8889>) |
| `CELTIUM_BLEND_CENSUS` | [GlsmFrontend.java:1061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1061>) |
| `CELTIUM_BLIT_DEPTH` | [GlsmFrontend.java:7811](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7811>) |
| `CELTIUM_BLIT_UNSERVED` | [GlsmFrontend.java:7753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7753>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:263](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L263>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:542](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L542>) |
| `CELTIUM_BORROW_BYPASS` | [PresentSwapchain.java:367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L367>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:13550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13550>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:13592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13592>) |
| `CELTIUM_BOUND_TARGET_CENSUS` | [VulkanTerrainGpuBackend.java:4219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4219>), [VulkanTerrainGpuBackend.java:21427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21427>) |
| `CELTIUM_BOUND_TARGET_COVERAGE` | [VulkanTerrainGpuBackend.java:21495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21495>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:14467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14467>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L63>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:5710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5710>), [VulkanContext.java:6113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L6113>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:849](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L849>) |
| `CELTIUM_CALL_LISTS_REFUSED` | [GlsmFrontend.java:9090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9090>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURE` | [VulkanTerrainGpuBackend.java:25172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L25172>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1634>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7882>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1655](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1655>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6609](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6609>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:5013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5013>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [CopyReadSide.java:593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L593>), [CopyReadSide.java:623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L623>), [PostChainBridge.java:1533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1533>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L343>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L374>) |
| `CELTIUM_CHAIN_INPUT_FLUSH_FAILED` | [VulkanTerrainGpuBackend.java:2063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2063>) |
| `CELTIUM_CHAIN_INPUT_PATCH` | [ModPrograms.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L900>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:16117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16117>) |
| `CELTIUM_CHAIN_PASS` | [VulkanTerrainGpuBackend.java:234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L234>), [VulkanTerrainGpuBackend.java:256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L256>), [VulkanTerrainGpuBackend.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L309>), [VulkanTerrainGpuBackend.java:345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L345>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:183](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L183>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13076](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13076>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:5543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5543>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:22654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22654>) |
| `CELTIUM_CLIENT_ARRAY` | [GlsmFrontend.java:9329](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9329>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [GlsmFrontend.java:8991](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8991>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:5186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5186>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:189](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L189>), [ForgeCloudCellsMixin.java:296](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L296>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3244>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:429](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L429>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:531](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L531>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L47>), [NativeFramebufferCompositor.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L97>), [WorldVertexBufferUploaderMixin.java:574](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L574>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L452>) |
| `CELTIUM_COPY_IMAGE` | [GlsmFrontend.java:7670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7670>), [GlsmFrontend.java:7707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7707>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1680>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1669>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:23889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23889>), [VulkanTerrainGpuBackend.java:23891](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23891>), [VulkanTerrainGpuBackend.java:23893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23893>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:23918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23918>), [VulkanTerrainGpuBackend.java:23920](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23920>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [PresentSwapchain.java:670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L670>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:23868](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23868>), [VulkanTerrainGpuBackend.java:23871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23871>), [VulkanTerrainGpuBackend.java:23873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23873>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:23926](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23926>), [VulkanTerrainGpuBackend.java:23929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23929>), [VulkanTerrainGpuBackend.java:23932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23932>), [VulkanTerrainGpuBackend.java:23934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23934>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:23846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23846>), [VulkanTerrainGpuBackend.java:23849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23849>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:3997](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3997>) |
| `CELTIUM_DEFER_EMPTY_CENSUS` | [RenderSectionManager.java:634](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L634>) |
| `CELTIUM_DELETE_SETTLED_SLOW` | [VulkanTerrainGpuBackend.java:18588](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18588>) |
| `CELTIUM_DEMONICA_BRIDGE_FAILED` | [DemonicaTerrainBridge.java:81](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DemonicaTerrainBridge.java#L81>) |
| `CELTIUM_DEPTH_COPY` | [CopyReadSide.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L293>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [GlsmFrontend.java:1530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1530>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [VulkanTerrainGpuBackend.java:19044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19044>) |
| `CELTIUM_DEPTH_NOTE` | [VulkanTerrainGpuBackend.java:7461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7461>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTextureRecordsPipeline.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L809>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5483>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockRecordsPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockRecordsPipeline.java#L60>), [VulkanImmediateItemRecordsPipeline.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L80>), [VulkanImmediateParticleRecordsPipeline.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticleRecordsPipeline.java#L67>), [VulkanImmediateTextureRecordsPipeline.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L68>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:4313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4313>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:4635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4635>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:4843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4843>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:27375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27375>), [VulkanTerrainGpuBackend.java:27460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27460>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4902](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4902>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4919](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4919>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4999](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4999>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7770](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7770>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7764>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5010>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8946](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8946>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5065](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5065>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5021>), [ValidationClient.java:8542](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8542>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:6175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6175>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L375>) |
| `CELTIUM_DISPLAY_LISTS` | [GlsmFrontend.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1086>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L308>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L133>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L431>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L403>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L349>) |
| `CELTIUM_DRAWARRAYS` | [GlDrawCensus.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L118>), [GlsmFrontend.java:1064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1064>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [GlsmFrontend.java:8567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8567>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [GlsmFrontend.java:9429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9429>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [GlsmFrontend.java:8622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8622>) |
| `CELTIUM_DRAWELEMENTS` | [GlDrawCensus.java:205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L205>), [GlsmFrontend.java:1067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1067>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:21828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21828>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:23955](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23955>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:26689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26689>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:27043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27043>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:4456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4456>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L626>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:4076](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4076>) |
| `CELTIUM_DYN_POISON` | [TerrainRenderer.java:1219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1219>) |
| `CELTIUM_EFFECT_PAIR_SPAWNED` | [ValidationClient.java:3940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3940>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:160](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L160>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L174>) |
| `CELTIUM_END_PORTAL_LEFTOVER` | [EndPortalProducer.java:74](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L74>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3881>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4958>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4968](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4968>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:11543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11543>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11551>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L780>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L680>), [VulkanTextureRegistry.java:851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L851>) |
| `CELTIUM_EXT_ANSWER` | [GlStateQueries.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L186>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:896](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L896>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:152](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L152>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:189](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L189>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:156](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L156>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:8473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8473>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [VulkanTerrainGpuBackend.java:8778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8778>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:8454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8454>), [VulkanTerrainGpuBackend.java:8525](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8525>), [VulkanTerrainGpuBackend.java:8535](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8535>), [VulkanTerrainGpuBackend.java:8556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8556>), [VulkanTerrainGpuBackend.java:8581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8581>), [VulkanTerrainGpuBackend.java:8723](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8723>), [VulkanTerrainGpuBackend.java:8745](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8745>), [VulkanTerrainGpuBackend.java:8764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8764>), [VulkanTerrainGpuBackend.java:8821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8821>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:8498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8498>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:8592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8592>), [VulkanTerrainGpuBackend.java:8833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8833>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:27366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27366>), [VulkanTerrainGpuBackend.java:27402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27402>), [VulkanTerrainGpuBackend.java:27452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27452>), [VulkanTerrainGpuBackend.java:27484](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27484>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>), [ValidationClient.java:7323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7323>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4622](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4622>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:2180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2180>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:2140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2140>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:12679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12679>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:18226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18226>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:18238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18238>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:10284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10284>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:10318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10318>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:10307](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10307>) |
| `CELTIUM_FN_CENSUS` | [GlsmFrontend.java:1076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1076>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:1551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1551>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:5591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5591>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L238>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L547>) |
| `CELTIUM_FONT_FLUSH_REENTRY` | [FontBatch.java:863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L863>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1322](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1322>), [CeltiumFontGlyphCapture.java:1452](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1452>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:9611](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9611>), [VulkanTerrainGpuBackend.java:9613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9613>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1295](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1295>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:899](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L899>), [CeltiumFontGlyphCapture.java:1434](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1434>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1534](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1534>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L989>), [FontBatch.java:1029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L1029>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:9585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9585>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:9506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9506>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:457](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L457>), [CeltiumFontGlyphCapture.java:461](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L461>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:9596](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9596>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:925](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L925>) |
| `CELTIUM_FRAMEBUFFER_HINT_FAILED` | [VulkanTerrainGpuBackend.java:2078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2078>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:14233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14233>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:9311](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9311>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L686>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L739>), [FrameBudgetTimings.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L743>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L150>) |
| `CELTIUM_FRAME_FORMAT_MOVED` | [VulkanContext.java:1319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1319>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L890>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L157>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L176>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [CopyReadSide.java:1081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1081>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L511>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L594>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L501>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L562>) |
| `CELTIUM_FREE_MEMORY_SLOW` | [VulkanContext.java:3557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3557>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:22441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22441>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:22568](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22568>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:159](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L159>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1363>) |
| `CELTIUM_GLDRAW_WINDOW_CENSUS` | [GlDrawWindowCensus.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L152>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:6368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6368>) |
| `CELTIUM_GLSM_HARVEST` | [VulkanModProgramPipeline.java:1446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1446>) |
| `CELTIUM_GLSM_LINK` | [ModProgramRegistry.java:458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L458>) |
| `CELTIUM_GLSTUB` | [GlsmFrontend.java:1012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1012>), [GlsmFrontend.java:1015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1015>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2949](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2949>), [CeltiumGllessHint.java:2953](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2953>), [CeltiumGllessNativeTransformer.java:154](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L154>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:54](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L54>), [CeleritasVintageMixinPlugin.java:56](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L56>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:6688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6688>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:6716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6716>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:631](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L631>), [CeltiumFontGlyphCapture.java:719](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L719>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1119](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1119>) |
| `CELTIUM_GL_CALLERS` | [CeltiumGllessHint.java:1381](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1381>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2912](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2912>), [CeltiumGllessHint.java:2919](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2919>), [CeltiumGllessHint.java:2925](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2925>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [GlsmFrontend.java:4681](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4681>) |
| `CELTIUM_GL_DISPATCH_FOREIGN` | [GlsmFrontend.java:4743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4743>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:754](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L754>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:784](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L784>) |
| `CELTIUM_GL_SURFACE_NOOP` | [CeltiumGllessNativeTransformer.java:823](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L823>) |
| `CELTIUM_GPU_TERRAIN_CENSUS` | [VulkanGpuTerrainCensus.java:401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L401>) |
| `CELTIUM_GPU_TERRAIN_CENSUS_DISABLED` | [VulkanGpuTerrainCensus.java:246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L246>), [VulkanTerrainGpuBackend.java:20606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20606>), [VulkanTerrainGpuBackend.java:20636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20636>) |
| `CELTIUM_GPU_TERRAIN_CULLING_GATE` | [VulkanGpuTerrainCensus.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L182>), [VulkanTerrainGpuBackend.java:20581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20581>) |
| `CELTIUM_GPU_TERRAIN_DRAW` | [VulkanGpuTerrainDraw.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L223>), [VulkanGpuTerrainDraw.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L228>), [VulkanGpuTerrainDraw.java:423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L423>), [VulkanGpuTerrainDraw.java:613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L613>) |
| `CELTIUM_GPU_TERRAIN_DRAW_DISABLED` | [VulkanGpuTerrainDraw.java:282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L282>), [VulkanTerrainGpuBackend.java:20702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20702>), [VulkanTerrainGpuBackend.java:21052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21052>) |
| `CELTIUM_GPU_TERRAIN_DRAW_MISMATCH` | [VulkanGpuTerrainDraw.java:664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L664>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:27992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27992>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13587](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13587>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:6262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6262>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1562>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:16995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16995>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:26754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26754>) |
| `CELTIUM_GROUP_DECAY` | [GroupDecayCensus.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L82>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:21807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21807>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:1057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1057>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:26962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26962>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:27004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L27004>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10503](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10503>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1376](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1376>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1853](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1853>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1881](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1881>) |
| `CELTIUM_HUD_CENSUS` | [HudCensus.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L237>), [HudCensus.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L240>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:8030](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8030>) |
| `CELTIUM_HUD_P0` | [VulkanTerrainGpuBackend.java:8094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8094>) |
| `CELTIUM_HUD_P0_SUMMARY` | [VulkanTerrainGpuBackend.java:8117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8117>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4105>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5176>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_BIND` | [GlStateQueries.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L195>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3150>) |
| `CELTIUM_IMGUNIT_ANSWER` | [GlStateQueries.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L190>), [CeltiumGllessHint.java:901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L901>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [GlDrawCensus.java:66](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L66>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemRecordsPipeline.java:619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L619>), [VulkanImmediateTextureRecordsPipeline.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L1017>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:16883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16883>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L225>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:10546](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10546>) |
| `CELTIUM_INDEXED_ENABLE` | [GlsmFrontend.java:6479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6479>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:6031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6031>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:26114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26114>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:336](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L336>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_K6` | [WorldVertexBufferUploaderMixin.java:467](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L467>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2918>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:1091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1091>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L248>), [CeleritasWorldRenderer.java:251](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L251>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4619>), [VulkanGlTexture.java:4622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4622>), [VulkanGlTexture.java:4624](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4624>) |
| `CELTIUM_LEAF_CULLING` | [LeafCullingCensus.java:84](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/LeafCullingCensus.java#L84>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [GlsmFrontend.java:3083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3083>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [GlsmFrontend.java:3207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3207>) |
| `CELTIUM_LEGACY_LIST_RUN` | [GlsmFrontend.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3160>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [GlsmFrontend.java:2794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2794>) |
| `CELTIUM_LEGACY_REFUSED` | [GlsmFrontend.java:2785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2785>), [GlsmFrontend.java:2789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2789>), [GlsmFrontend.java:2963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2963>), [GlsmFrontend.java:3088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3088>), [GlsmFrontend.java:3097](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3097>), [GlsmFrontend.java:3104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3104>) |
| `CELTIUM_LEGACY_RUN` | [GlsmFrontend.java:3273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3273>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1261](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1261>), [VulkanTextureRegistry.java:2422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2422>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:10287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10287>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:14770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14770>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:14760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14760>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:14849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14849>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:14880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14880>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:14912](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14912>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:21312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21312>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:10254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10254>) |
| `CELTIUM_LIST_ABANDONED` | [GlsmFrontend.java:5414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5414>) |
| `CELTIUM_LIST_COMPILED` | [GlsmFrontend.java:5474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5474>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:537](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L537>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:866](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L866>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:5721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5721>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9614](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9614>) |
| `CELTIUM_LOADING_SCREEN` | [NativeLoadingScreen.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeLoadingScreen.java#L226>), [CeltiumLoadingProgress.java:116](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L116>), [CeltiumLoadingProgress.java:126](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L126>), [CeltiumLoadingProgress.java:147](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L147>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L163>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L497>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3965>), [ValidationClient.java:3985](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3985>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10336](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10336>), [ValidationClient.java:10346](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10346>), [ValidationClient.java:10352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10352>) |
| `CELTIUM_MAIN_CLEAR` | [GlsmFrontend.java:4258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4258>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/BufferStorageRecord.java#L159>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L337>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L355>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:16867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16867>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1093](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1093>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:6803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6803>) |
| `CELTIUM_MEMORY_PROFILE` | [VulkanContext.java:4837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4837>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:26122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26122>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12514>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:26774](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26774>) |
| `CELTIUM_MESH_TERRAIN` | [VulkanMeshTerrain.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L89>), [VulkanMeshTerrain.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L269>), [VulkanMeshTerrain.java:571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L571>) |
| `CELTIUM_MESH_TERRAIN_STAGE2` | [VulkanMeshTaskDraw.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L262>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_BUDGET_MISMATCH` | [VulkanMeshStage2Pipeline.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L202>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_DISABLED` | [VulkanMeshTaskDraw.java:255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L255>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_GATE` | [VulkanMeshTaskDraw.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L138>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_PIPELINE` | [VulkanMeshStage2Pipeline.java:136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L136>), [VulkanMeshStage2Pipeline.java:556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L556>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_COVERAGE` | [VulkanTerrainGpuBackend.java:20924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20924>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_DISABLED` | [VulkanTerrainGpuBackend.java:20915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20915>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_WIRE` | [VulkanTerrainGpuBackend.java:20906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20906>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1835>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2159>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2330>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L206>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:26133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26133>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3703](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3703>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3717>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:1110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1110>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1973>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2274>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2413>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3269>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:2193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2193>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1739>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1350>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L384>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L439>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L371>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L74>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:7294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7294>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L523>), [ModelReplay.java:560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L560>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L270>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:596](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L596>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L366>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L344>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L292>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1362>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1322>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L159>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L98>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L105>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L467>) |
| `CELTIUM_MODEL_PHASE` | [PatchGroups.java:243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L243>), [ModelPhaseTrace.java:168](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L168>), [ModelPhaseTrace.java:170](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L170>), [ModelPhaseTrace.java:172](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L172>), [ModelPhaseTrace.java:174](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L174>), [ModelPhaseTrace.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L176>), [ModelPhaseTrace.java:178](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L178>), [ModelPhaseTrace.java:180](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L180>), [ModelPhaseTrace.java:182](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L182>), [ModelPhaseTrace.java:184](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L184>), [ModelPhaseTrace.java:186](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L186>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L299>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L481>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L474>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L494>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L147>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L368>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L328>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L340>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L282>) |
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:26498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26498>) |
| `CELTIUM_MOD_PROGRAM_FALLBACK` | [VulkanTerrainGpuBackend.java:11531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11531>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L447>) |
| `CELTIUM_MOD_PROGRAM_SUPPRESSED` | [ModPrograms.java:999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L999>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:927](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L927>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L782>) |
| `CELTIUM_MOD_SAMPLER_DEPTH_ATTACH` | [ModPrograms.java:969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L969>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L406>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L136>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13276](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13276>) |
| `CELTIUM_MULT_MATRIX` | [GlsmFrontend.java:6087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6087>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:3978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3978>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_DRAW_REFUSED` | [CeltiumNativeDraw.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java#L54>) |
| `CELTIUM_NATIVE_GL_SURFACE` | [RenderSectionManager.java:216](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L216>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:18276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18276>), [GlsmFrontend.java:1053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1053>), [GlsmFrontend.java:5765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5765>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:18278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18278>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:368](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L368>), [WorldVertexBufferUploaderMixin.java:381](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L381>) |
| `CELTIUM_NULL_RENDERER` | [NullRenderer.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L64>), [NullRenderer.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L73>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:9075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9075>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:334](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L334>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:442](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L442>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:6509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6509>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L638>), [FrameBudgetTimings.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L653>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L395>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PACK_SHADOW_CACHE` | [VulkanTerrainGpuBackend.java:19468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19468>) |
| `CELTIUM_PACK_TERRAIN_DRAWN` | [VulkanTerrainGpuBackend.java:20256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20256>) |
| `CELTIUM_PACK_TERRAIN_REFUSED` | [VulkanTerrainGpuBackend.java:20543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20543>) |
| `CELTIUM_PACK_UNIFORM` | [PackUniformCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L375>) |
| `CELTIUM_PACK_UNIFORM_LANES` | [PackUniformCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L403>) |
| `CELTIUM_PACK_UNIFORM_PIPELINE` | [PackUniformCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L358>) |
| `CELTIUM_PACK_UNIFORM_PROGRAM` | [PackUniformCensus.java:304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L304>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:22887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22887>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:1245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1245>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:16448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16448>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:1118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1118>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [VulkanTerrainGpuBackend.java:16171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16171>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:65](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L65>) |
| `CELTIUM_PARTICLE_UV` | [GlsmFrontend.java:7470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7470>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L83>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L86>) |
| `CELTIUM_PATCH_ESSENTIAL` | [PatchErrorRecorder.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L98>) |
| `CELTIUM_PATCH_IGNORED` | [PatchLedger.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L177>), [PatchLedger.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L181>), [PatchLedger.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L184>), [PatchLedger.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L201>), [PatchLedger.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L207>) |
| `CELTIUM_PATCH_QUARANTINED` | [PatchErrorRecorder.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L110>) |
| `CELTIUM_PATCH_RECORD_FAILED` | [PatchErrorRecorder.java:114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L114>) |
| `CELTIUM_PATCH_REGISTERED` | [CeleritasVintageMixinPlugin.java:212](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L212>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1996>) |
| `CELTIUM_PATCH_SKIPPED` | [PatchLedger.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L269>), [PatchLedger.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L272>), [PatchLedger.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L275>) |
| `CELTIUM_PATCH_TABLE` | [PatchLedger.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L305>), [CeleritasVintageMixinPlugin.java:167](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L167>), [CeleritasVintageMixinPlugin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L174>), [CeleritasVintageMixinPlugin.java:220](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L220>) |
| `CELTIUM_PBO_REFUSED` | [GlTextureCaptures.java:606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L606>), [GlsmFrontend.java:7879](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7879>) |
| `CELTIUM_PBO_UPLOAD` | [GlTextureCaptures.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L403>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9733](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9733>), [ValidationClient.java:9877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9877>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:3670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3670>), [VulkanContext.java:3689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3689>), [VulkanContext.java:3725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3725>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [VulkanContext.java:3416](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3416>) |
| `CELTIUM_PIPELINE_CREATE_CENSUS` | [PipelineCreateCensus.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L175>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5456>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:3425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3425>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:567](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L567>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:548](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L548>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:538](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L538>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L417>), [VulkanPostChain.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L426>), [VulkanPostChain.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L438>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L547>), [VulkanPostChain.java:748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L748>), [VulkanPostChain.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L805>), [VulkanPostChain.java:835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L835>), [VulkanPostChain.java:839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L839>), [VulkanPostChain.java:1433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1433>), [VulkanPostChain.java:1438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1438>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6862>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1545](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1545>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L929>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1102>) |
| `CELTIUM_POW_GUARD_CENSUS` | [ModGlslTranslator.java:1964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1964>) |
| `CELTIUM_PREPARE_NULL` | [PrepareNullCensus.java:200](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L200>) |
| `CELTIUM_PRESENT` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [PresentSwapchain.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L739>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [PresentSwapchain.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L103>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11936](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11936>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `CELTIUM_PRESENT_DIAGNOSTICS_FAILED` | [VulkanSwapchain.java:2044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2044>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L412>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L722>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3128>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L192>) |
| `CELTIUM_PRESENT_PATCH` | [PresentSwapchain.java:861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L861>) |
| `CELTIUM_PRESENT_SLOW` | [PresentSwapchain.java:708](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L708>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1464>) |
| `CELTIUM_PREWARM_TEARDOWN` | [VulkanContext.java:3783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3783>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [GlStateQueries.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L250>) |
| `CELTIUM_PROGRAM_DRAW` | [GlsmFrontend.java:8412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8412>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:16826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16826>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1704](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1704>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [VulkanTerrainGpuBackend.java:7598](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7598>) |
| `CELTIUM_RAW_BIND_REGISTER` | [VulkanTerrainGpuBackend.java:7622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7622>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [VulkanTerrainGpuBackend.java:7688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7688>) |
| `CELTIUM_RAW_FBO` | [GlsmFrontend.java:6738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6738>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:8295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8295>), [VulkanTerrainGpuBackend.java:8322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8322>) |
| `CELTIUM_RAW_FBO_DEPTH` | [GlsmFrontend.java:6707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6707>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:35](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L35>), [RawFboIdentityProbe.java:259](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L259>) |
| `CELTIUM_RAW_FBO_LEVEL` | [GlsmFrontend.java:6695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6695>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L808>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L274>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1686>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1223>), [GlsmFrontend.java:7890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7890>), [GlsmFrontend.java:7899](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7899>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [GlsmFrontend.java:7871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7871>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1215>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1083>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1070>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:28010](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L28010>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:277](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L277>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTextureRecordsPipeline.java:781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L781>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:417](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L417>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4133](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4133>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:199](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L199>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:727](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L727>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:13995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13995>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9048>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9195](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9195>), [ValidationClient.java:9196](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9196>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9137](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9137>), [ValidationClient.java:9171](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9171>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9298](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9298>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9198](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9198>), [ValidationClient.java:9200](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9200>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5148>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5153>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3253>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2843>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2906>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:736](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L736>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L101>) |
| `CELTIUM_SECTION_MANAGER` | [VintageRenderSectionManager.java:56](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L56>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L212>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:11562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11562>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11574>) |
| `CELTIUM_SHADOW_FRUSTUM_INIT_CACHE` | [DemonicaShadowFrustumInitCacheMixin.java:100](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L100>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13322](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13322>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:15948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15948>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:19153](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19153>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1423>), [VulkanGlTexture.java:1463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1463>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L663>), [VulkanGlTexture.java:755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L755>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L715>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L788>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:931](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L931>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:1198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1198>) |
| `CELTIUM_SKY_DEGRADED` | [GlsmFrontend.java:9532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9532>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:26541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L26541>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:9199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9199>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:9186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9186>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:9141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9141>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:9210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9210>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [PresentSwapchain.java:911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L911>) |
| `CELTIUM_SLOTDUMP` | [VulkanTextureRegistry.java:434](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L434>), [VulkanTextureRegistry.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L441>), [VulkanTextureRegistry.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L534>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:24638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24638>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:14255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14255>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:14368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14368>), [VulkanTerrainGpuBackend.java:14373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14373>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9522](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9522>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:14281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14281>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:14435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14435>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3540>), [VulkanGlTexture.java:3657](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3657>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L197>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4501>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:8367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8367>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:3310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3310>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1017>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L957>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:3237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3237>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2778>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2807>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [PresentSwapchain.java:313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L313>) |
| `CELTIUM_SWAPCHAIN_IMAGE_INIT` | [VulkanSwapchain.java:3398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3398>), [VulkanSwapchain.java:3410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3410>), [VulkanSwapchain.java:3422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3422>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:3293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3293>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:639](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L639>) |
| `CELTIUM_SWAPPOINT_EXCEPTION` | [VulkanTerrainGpuBackend.java:2332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2332>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L269>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:8147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8147>) |
| `CELTIUM_TEARDOWN` | [VulkanContext.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3160>), [VulkanContext.java:3244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3244>), [VulkanTerrainGpuBackend.java:18797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18797>) |
| `CELTIUM_TERRAIN_A` | [TerrainEngineA.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L147>), [TerrainEngineA.java:548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L548>) |
| `CELTIUM_TERRAIN_A_BACKEND_DISABLED` | [VulkanTerrainGpuBackend.java:21168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21168>) |
| `CELTIUM_TERRAIN_A_DELETE_REFUSED` | [TerrainEngineAHost.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L787>) |
| `CELTIUM_TERRAIN_A_DISABLED` | [TerrainEngineA.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L176>) |
| `CELTIUM_TERRAIN_A_GATE_VIOLATION` | [TerrainEngineAHost.java:887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L887>) |
| `CELTIUM_TERRAIN_A_PACK` | [TerrainPackEngine.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java#L139>), [TerrainPackEngine.java:400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java#L400>) |
| `CELTIUM_TERRAIN_A_PACK_DISABLED` | [TerrainPackEngine.java:165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java#L165>) |
| `CELTIUM_TERRAIN_A_REGISTER_FAILED` | [TerrainRenderer.java:1893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1893>), [TerrainRenderer.java:1954](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1954>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:21488](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21488>) |
| `CELTIUM_TERRAIN_BUDGET` | [VulkanTerrainGpuBackend.java:18843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18843>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [VulkanTerrainGpuBackend.java:19094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19094>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [VulkanTerrainGpuBackend.java:22136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22136>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [VulkanTerrainGpuBackend.java:19062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19062>), [VulkanTerrainGpuBackend.java:19079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19079>), [VulkanTerrainGpuBackend.java:19087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19087>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1769>) |
| `CELTIUM_TERRAIN_PIPELINE_FAILED` | [TerrainRenderer.java:567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L567>) |
| `CELTIUM_TERRAIN_SHADOW` | [VulkanTerrainGpuBackend.java:21384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21384>), [VulkanTerrainGpuBackend.java:21551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21551>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L170>) |
| `CELTIUM_TEXTURE_DELETED` | [GlsmFrontend.java:6275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6275>) |
| `CELTIUM_TEXTURE_DIAG` | [CopyReadSide.java:1291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1291>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:1004](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1004>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [GlsmFrontend.java:6996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6996>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:949](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L949>) |
| `CELTIUM_TEXUNIT_ANSWER` | [GlStateQueries.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L174>) |
| `CELTIUM_TEX_COPY` | [GlsmFrontend.java:7612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7612>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [GlsmFrontend.java:1073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1073>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:1038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1038>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L361>), [TickTimings.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L376>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L365>), [TickTimings.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L419>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:143](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L143>), [CeleritasWorldRenderer.java:157](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L157>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1378](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1378>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L957>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1099>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [GlsmFrontend.java:5646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5646>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [GlsmFrontend.java:1054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1054>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:10430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10430>), [WorldVertexBufferUploaderMixin.java:354](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L354>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:10431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10431>), [WorldVertexBufferUploaderMixin.java:798](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L798>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:558](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L558>) |
| `CELTIUM_UNSUPPORTED_INSTANCED` | [CeltiumGllessHint.java:1497](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1497>), [CeltiumGllessNativeTransformer.java:832](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L832>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4322>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:5387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5387>) |
| `CELTIUM_UPSTREAM` | [PatchGroups.java:264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L264>), [UpstreamArrivalCensus.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L142>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:3829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3829>), [VulkanContext.java:3838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3838>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:107](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L107>) |
| `CELTIUM_VERTEXPOINTER` | [GlsmFrontend.java:5534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5534>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:2205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L2205>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:281](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L281>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:2483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2483>), [VulkanContext.java:2643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2643>), [VulkanContext.java:3069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3069>), [VulkanContext.java:3081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3081>), [VulkanContext.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3128>), [VulkanContext.java:3136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3136>), [VulkanContext.java:3184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3184>), [VulkanContext.java:3189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3189>), [VulkanContext.java:3205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3205>), [VulkanContext.java:3213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3213>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:3094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3094>), [VulkanContext.java:3117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3117>), [VulkanContext.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3221>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4606](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4606>), [ValidationClient.java:4610](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4610>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:1073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1073>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1312>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1086>), [VulkanTerrainGpuBackend.java:16408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16408>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1286](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1286>), [CeltiumGllessHint.java:1384](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1384>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1310>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1308>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:14165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14165>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1348>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:562](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L562>), [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>), [MinecraftMixin.java:588](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L588>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:24046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24046>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9261](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9261>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:9731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9731>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1380](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1380>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:1290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1290>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:1270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1270>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:1275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1275>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:1271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1271>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:193](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L193>), [RenderSectionManager.java:195](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L195>), [RenderSectionManager.java:199](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L199>), [RenderSectionManager.java:201](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L201>), [RenderSectionManager.java:207](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L207>), [RenderSectionManager.java:210](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L210>), [SimpleWorldRenderer.java:47](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L47>), [SimpleWorldRenderer.java:50](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L50>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:283](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L283>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:301](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L301>), [SimpleWorldRenderer.java:304](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L304>), [SimpleWorldRenderer.java:307](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L307>), [SimpleWorldRenderer.java:314](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L314>), [SimpleWorldRenderer.java:321](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L321>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:5645](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5645>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9022](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9022>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2649>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemRecordsPipeline.java:421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L421>), [VulkanImmediateTextureRecordsPipeline.java:830](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L830>) |
