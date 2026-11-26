# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **394 個**／文字列内マーカー **621 個**。
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
| [celtium.clearlatch](#group-celtium-clearlatch) | 1 |
| [celtium.debug](#group-celtium-debug) | 52 |
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
| [celtium.present](#group-celtium-present) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.releaseTextureCpuData](#group-celtium-releaseTextureCpuData) | 2 |
| [celtium.shaderc](#group-celtium-shaderc) | 2 |
| [celtium.shadowFrustumInitCache](#group-celtium-shadowFrustumInitCache) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 5 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 277 |
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
| `celtium.allowCompetitors` | `Boolean.getBoolean` | [CeltiumCompetitors.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java#L70>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L744>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14776>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:22271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22271>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:22272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22272>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:22270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22270>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [GlsmFrontend.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L272>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21766>) |
| `celtium.debug.chainInputPatchFrames` | `System.getProperty` | [ModPrograms.java:549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L549>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.deferEmptyCommitCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L134>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L358>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22275>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:22256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22256>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21759>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4235](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4235>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4262>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2753>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L436>), [VulkanSwapchain.java:1962](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1962>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:372](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L372>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L378>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L374>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L376>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L368>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [GlsmFrontend.java:4860](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4860>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21761>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21791>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:21792](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21792>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [GlsmFrontend.java:2686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2686>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:501](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L501>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:505](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L505>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L29>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L531>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L458>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L98>), [VulkanModelInstances.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L179>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11362>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:128](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L128>) |
| `celtium.debug.packUniformLaneCensus` | `Boolean.getBoolean` | [PackUniformCensus.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L50>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.prepareNullCensus` | `Integer.getInteger`, `System.getProperty` | [PrepareNullCensus.java:114](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L114>), [PrepareNullCensus.java:118](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L118>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21832>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4285>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21757](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21757>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4249>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L280>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L663>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4315>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4254>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4308>) |

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
| `celtium.dynamicLights.poison` | `Boolean.getBoolean` | [TerrainRenderer.java:1103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1103>) |

<a id="group-celtium-entityLightCache"></a>
## `celtium.entityLightCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:67](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L67>) |

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
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L344>) |

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

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.callerCensus` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:607](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L607>) |
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:601](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L601>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1742](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1742>), [CeltiumGllessNativeTransformer.java:424](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L424>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [GlsmFrontend.java:1105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1105>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [GlsmFrontend.java:9146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9146>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTrampolines.java#L116>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [GlsmFrontend.java:9337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9337>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [GlsmFrontend.java:4626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4626>), [GlsmFrontend.java:9009](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9009>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [GlsmFrontend.java:4621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4621>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [CopyReadSide.java:913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L913>) |

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
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L35>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4039>) |
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

<a id="group-celtium-present"></a>
## `celtium.present.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.present.direct` | `System.getProperty` | [VulkanContext.java:105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L105>) |

<a id="group-celtium-probe"></a>
## `celtium.probe.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.probe.draws` | `Boolean.getBoolean` | [CloudDrawProbeMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L48>), [ParticleDrawProbeMixin.java:27](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L27>), [RainDrawProbeMixin.java:26](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L26>) |
| `celtium.probe.every` | `Integer.getInteger` | [CloudDrawProbeMixin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L50>), [ParticleDrawProbeMixin.java:29](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L29>), [RainDrawProbeMixin.java:28](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L28>) |
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:848](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L848>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:836](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L836>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:855](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L855>) |

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

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:84](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L84>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:52](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L52>) |
| `celtium.terrain.engine` | `System.getProperty` | [TerrainEngineAHost.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L46>), [CeleritasWorldRenderer.java:417](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L417>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:413](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L413>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [HarnessConfig.java:70](<../common/src/main/java/org/soutalab/celtium/harness/HarnessConfig.java#L70>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [CopyReadSide.java:818](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L818>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L120>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3719](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3719>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3665](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3665>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L76>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L78>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L80>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L83>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L18>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L65>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:587](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L587>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21863>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4439>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateItemPipeline.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L426>), [VulkanImmediateTexturePipeline.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L509>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4624](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4624>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4229](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4229>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4236>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2252](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2252>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [GlsmFrontend.java:349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L349>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6489>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12155>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L192>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2689>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4429>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3513>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3523>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3524](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3524>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:1013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1013>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22930>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3332>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22025>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3335>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3318>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3321>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3498>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3500>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3313>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3315>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22832>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22827](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22827>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3069>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:22](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L22>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4456>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12066>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5130>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L417>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4765>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L332>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L134>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L90>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1139>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L327>) |
| `celtium.vulkan.fractionalPowGuard` | `System.getProperty` | [ModGlslTranslator.java:1906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1906>) |
| `celtium.vulkan.fractionalPowGuard.census` | `Boolean.getBoolean` | [ModGlslTranslator.java:1916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1916>) |
| `celtium.vulkan.fractionalPowGuard.fastSqrt` | `Boolean.getBoolean` | [ModGlslTranslator.java:1911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1911>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L58>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L56>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:16960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16960>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16963>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16942](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16942>) |
| `celtium.vulkan.frameRingMesh` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15761>) |
| `celtium.vulkan.frameRingSlotMiB` | `Integer.getInteger` | [FrameLinearRing.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/FrameLinearRing.java#L64>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21583>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L43>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3556>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L469>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4226>) |
| `celtium.vulkan.glDrawExempt` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12856>) |
| `celtium.vulkan.glDrawWindowCensus` | `Boolean.getBoolean` | [GlDrawWindowCensus.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L56>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2105>) |
| `celtium.vulkan.glsmFrameState` | `System.getProperty` | [GlsmFrameState.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlsmFrameState.java#L36>) |
| `celtium.vulkan.glsmPackState` | `System.getProperty` | [GlsmPackState.java:53](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmPackState.java#L53>) |
| `celtium.vulkan.gpuTerrainCulling` | `Boolean.getBoolean` | [GpuTerrainCulling.java:22](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L22>) |
| `celtium.vulkan.gpuTerrainCulling.census` | `Boolean.getBoolean` | [GpuTerrainCulling.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L30>) |
| `celtium.vulkan.gpuTerrainCulling.maxDistanceBlocks` | `System.getProperty` | [GpuTerrainCulling.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L73>) |
| `celtium.vulkan.gpuTerrainCulling.shadowCensus` | `Boolean.getBoolean` | [GpuTerrainCulling.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L41>) |
| `celtium.vulkan.gpuTerrainCulling.shadowDraw` | `Boolean.getBoolean` | [GpuTerrainCulling.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L60>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3174>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3214>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:3176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3176>), [VulkanTerrainGpuBackend.java:3192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3192>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3230>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3224>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3699>) |
| `celtium.vulkan.groupDecayCensus` | `Boolean.getBoolean` | [GroupDecayCensus.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L24>) |
| `celtium.vulkan.groupDecayCensusFrames` | `Integer.getInteger` | [GroupDecayCensus.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L26>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L251>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3691>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1558](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1558>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3558](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3558>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L107>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3247>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3257>) |
| `celtium.vulkan.hudCensusFrames` | `Integer.getInteger` | [HudCensus.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L32>) |
| `celtium.vulkan.hudCensusMinFrame` | `Long.getLong` | [HudCensus.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L33>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3273>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3276>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1999>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3680>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3682>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3684](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3684>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L73>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L84>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L90>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4088>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L639>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4166>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L562>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3487>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2829>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4441>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3084>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [CopyReadSide.java:771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L771>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6588](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6588>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3160>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6325](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6325>), [CeltiumGllessHint.java:1721](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1721>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L113>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21746>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21795>) |
| `celtium.vulkan.meshTerrain` | `Boolean.getBoolean` | [VulkanMeshTerrain.java:59](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L59>) |
| `celtium.vulkan.meshTerrainStage2` | `Boolean.getBoolean` | [VulkanMeshTaskDraw.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L38>) |
| `celtium.vulkan.meshTerrainStage2.quadsPerMeshWorkgroup` | `Integer.getInteger` | [VulkanMeshTaskDraw.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L48>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:1280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1280>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L298>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4619>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L144>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L224>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L168>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L180>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L296>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4485>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L108>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2040](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2040>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2081>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:1103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1103>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2082>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L534>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:22825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22825>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:22812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22812>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:21934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21934>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L91>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21820>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L195>) |
| `celtium.vulkan.nullRenderer` | `Boolean.getBoolean` | [NullRenderer.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L28>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packShadowCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L461>) |
| `celtium.vulkan.packShadowRebuildBudget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L441>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [VulkanTerrainGpuBackend.java:432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L432>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16971>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17038>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L309>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L109>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2799>) |
| `celtium.vulkan.pipelineCreateCensus` | `Boolean.getBoolean` | [PipelineCreateCensus.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L51>) |
| `celtium.vulkan.pipelineCreateCensusSlowMs` | `Long.getLong` | [PipelineCreateCensus.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L54>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L430>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3671>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L291>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L308>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14333>) |
| `celtium.vulkan.presentDrainFinishGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12886>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17025>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>), [VulkanTerrainGpuBackend.java:4193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4193>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:325](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L325>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L467>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17018>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17028>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17031>) |
| `celtium.vulkan.presentTransferOnly` | `System.getProperty` | [VulkanSwapchain.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L63>) |
| `celtium.vulkan.presentUnbindFlushGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12869>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:471](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L471>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3506>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21578](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21578>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L56>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12168>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17007>) |
| `celtium.vulkan.recordsPositionTwins` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:77](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L77>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3087>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4223>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4224>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L45>) |
| `celtium.vulkan.retainedIndexFlushExempt` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4216>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14917>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2438>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4752](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4752>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5458>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4176>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3508>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17001](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17001>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L800>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:836](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L836>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L878>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L880>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:852](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L852>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L834>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L825>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L817>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L560>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21755>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [GlsmFrontend.java:5574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5574>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [GlsmFrontend.java:9011](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9011>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L250>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L231>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L88>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:3575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3575>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:7828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7828>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>), [GlsmFrontend.java:282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L282>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L394>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:7819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7819>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L411>) |
| `celtium.vulkan.terrainEngineA` | `System.getProperty` | [TerrainEngineAHost.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L47>), [CeleritasWorldRenderer.java:418](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L418>) |
| `celtium.vulkan.terrainMaxDraws` | `Integer.getInteger` | [TerrainFrameBudget.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L29>) |
| `celtium.vulkan.terrainMaxRebuildsPerFrame` | `Integer.getInteger` | [TerrainFrameBudget.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L30>) |
| `celtium.vulkan.terrainMaxVertices` | `Long.getLong` | [TerrainFrameBudget.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L28>) |
| `celtium.vulkan.terrainPreparePublishWalkOnce` | `Boolean.getBoolean` | [PostChainBridge.java:688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L688>) |
| `celtium.vulkan.terrainRebuildTargetMicros` | `Long.getLong` | [TerrainFrameBudget.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L32>) |
| `celtium.vulkan.terrainResidencyGate` | `System.getProperty` | [TerrainEngineA.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L67>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3826>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3829>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3733>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3813>) |
| `celtium.vulkan.terrainUploadMiB` | `Long.getLong` | [TerrainEngineAHost.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java#L52>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L130>), [VulkanTerrainGpuBackend.java:3089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3089>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16934>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2794>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16291>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16299>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:21910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21910>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L556>) |
| `celtium.vulkan.upstreamArrivalCensus` | `Boolean.getBoolean` | [UpstreamArrivalCensus.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L28>) |
| `celtium.vulkan.upstreamArrivalCensusFrames` | `Integer.getInteger` | [UpstreamArrivalCensus.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L30>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:1688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1688>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17696>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L221>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3592>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3338>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3341>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2391>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L786>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L35>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:10973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10973>) |
| `CELTIUM_ACCEPT_FLUSH` | [FramePassTimings.java:897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L897>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2770>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4170>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:5862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5862>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:5538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5538>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1395>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L670>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L515>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L702>), [VulkanArena.java:711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L711>), [VulkanArena.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L722>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L337>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1102>), [VulkanTerrainPipeline.java:1143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1143>), [VulkanTerrainPipeline.java:1163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1163>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L470>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:17498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17498>) |
| `CELTIUM_BATCH_CACHE` | [BatchCacheCensus.java:116](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L116>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:9714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9714>) |
| `CELTIUM_BATCH_VIEWS` | [BatchCacheCensus.java:83](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L83>), [BatchCacheCensus.java:86](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L86>) |
| `CELTIUM_BE_LOS` | [BeVisibilityProbe.java:154](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L154>), [BeVisibilityProbe.java:421](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L421>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:8418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8418>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:8431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8431>) |
| `CELTIUM_BLEND_CENSUS` | [GlsmFrontend.java:1061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1061>) |
| `CELTIUM_BLIT_DEPTH` | [GlsmFrontend.java:7657](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7657>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:263](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L263>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:542](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L542>) |
| `CELTIUM_BORROW_BYPASS` | [PresentSwapchain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L308>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:12736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12736>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:12778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12778>) |
| `CELTIUM_BOUND_TARGET_CENSUS` | [VulkanTerrainGpuBackend.java:3764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3764>), [VulkanTerrainGpuBackend.java:19905](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19905>) |
| `CELTIUM_BOUND_TARGET_COVERAGE` | [VulkanTerrainGpuBackend.java:19973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19973>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:13617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13617>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L63>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:4608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4608>), [VulkanContext.java:4847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4847>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:849](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L849>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1631>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7882>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1652](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1652>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6609](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6609>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:4566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4566>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [CopyReadSide.java:591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L591>), [CopyReadSide.java:621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L621>), [PostChainBridge.java:1454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1454>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L278>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L309>) |
| `CELTIUM_CHAIN_INPUT_PATCH` | [ModPrograms.java:796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L796>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:15303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15303>) |
| `CELTIUM_CHAIN_PASS` | [VulkanTerrainGpuBackend.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L225>), [VulkanTerrainGpuBackend.java:247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L247>), [VulkanTerrainGpuBackend.java:300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L300>), [VulkanTerrainGpuBackend.java:336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L336>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13058](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13058>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:4486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4486>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:21126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21126>) |
| `CELTIUM_CLIENT_ARRAY` | [GlsmFrontend.java:9122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9122>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [GlsmFrontend.java:8796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8796>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:5186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5186>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:189](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L189>), [ForgeCloudCellsMixin.java:296](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L296>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3244>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:416](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L416>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:480](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L480>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L88>), [WorldVertexBufferUploaderMixin.java:523](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L523>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L452>) |
| `CELTIUM_COPY_IMAGE` | [GlsmFrontend.java:7558](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7558>), [GlsmFrontend.java:7595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7595>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1678>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1667>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:22353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22353>), [VulkanTerrainGpuBackend.java:22355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22355>), [VulkanTerrainGpuBackend.java:22357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22357>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:22382](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22382>), [VulkanTerrainGpuBackend.java:22384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22384>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [PresentSwapchain.java:553](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L553>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:22332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22332>), [VulkanTerrainGpuBackend.java:22335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22335>), [VulkanTerrainGpuBackend.java:22337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22337>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:22390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22390>), [VulkanTerrainGpuBackend.java:22393](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22393>), [VulkanTerrainGpuBackend.java:22396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22396>), [VulkanTerrainGpuBackend.java:22398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22398>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:22310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22310>), [VulkanTerrainGpuBackend.java:22313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22313>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:3547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3547>) |
| `CELTIUM_DEFER_EMPTY_CENSUS` | [RenderSectionManager.java:634](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L634>) |
| `CELTIUM_DELETE_SETTLED_SLOW` | [VulkanTerrainGpuBackend.java:17441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17441>) |
| `CELTIUM_DEPTH_COPY` | [CopyReadSide.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L291>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [GlsmFrontend.java:1530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1530>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [VulkanTerrainGpuBackend.java:17917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17917>) |
| `CELTIUM_DEPTH_NOTE` | [VulkanTerrainGpuBackend.java:7036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7036>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L857>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4426>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:3327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3327>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:3649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3649>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3814>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:24946](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24946>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4902](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4902>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4919](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4919>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4999](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4999>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7770](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7770>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7764>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5010>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8928](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8928>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5065](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5065>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5021>), [ValidationClient.java:8524](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8524>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:5728](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5728>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L411>) |
| `CELTIUM_DISPLAY_LISTS` | [GlsmFrontend.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1086>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L344>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L467>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L439>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L385>) |
| `CELTIUM_DRAWARRAYS` | [GlDrawCensus.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L118>), [GlsmFrontend.java:1064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1064>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [GlsmFrontend.java:8371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8371>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [GlsmFrontend.java:9222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9222>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [GlsmFrontend.java:8426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8426>) |
| `CELTIUM_DRAWELEMENTS` | [GlDrawCensus.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L203>), [GlsmFrontend.java:1067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1067>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:20305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20305>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:22419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22419>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:24215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24215>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:24554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24554>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:3470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3470>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L586>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:4076](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4076>) |
| `CELTIUM_DYN_POISON` | [TerrainRenderer.java:1180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1180>) |
| `CELTIUM_EFFECT_PAIR_SPAWNED` | [ValidationClient.java:3940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3940>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:160](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L160>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:172](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L172>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3881>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4958>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4968](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4968>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:11017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11017>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11025>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L560>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L461>), [VulkanTextureRegistry.java:631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L631>) |
| `CELTIUM_EXT_ANSWER` | [GlStateQueries.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L186>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:896](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L896>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:8048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8048>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [VulkanTerrainGpuBackend.java:8320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8320>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:8029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8029>), [VulkanTerrainGpuBackend.java:8086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8086>), [VulkanTerrainGpuBackend.java:8098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8098>), [VulkanTerrainGpuBackend.java:8123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8123>), [VulkanTerrainGpuBackend.java:8265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8265>), [VulkanTerrainGpuBackend.java:8287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8287>), [VulkanTerrainGpuBackend.java:8306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8306>), [VulkanTerrainGpuBackend.java:8363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8363>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:8073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8073>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:8134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8134>), [VulkanTerrainGpuBackend.java:8375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8375>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:24938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24938>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>), [ValidationClient.java:7323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7323>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4622](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4622>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:2011](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2011>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:1971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1971>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:12140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12140>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:17149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17149>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:17161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17161>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:9767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9767>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:9801](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9801>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:9790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9790>) |
| `CELTIUM_FN_CENSUS` | [GlsmFrontend.java:1076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1076>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:1512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1512>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:5144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5144>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L238>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L539>) |
| `CELTIUM_FONT_FLUSH_REENTRY` | [FontBatch.java:864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L864>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1297](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1297>), [CeltiumFontGlyphCapture.java:1427](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1427>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:9142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9142>), [VulkanTerrainGpuBackend.java:9144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9144>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1270](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1270>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1409](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1409>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1508](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1508>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:1022](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L1022>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:9116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9116>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:9038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9038>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:451](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L451>), [CeltiumFontGlyphCapture.java:455](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L455>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:9127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9127>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:871](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L871>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:13383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13383>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:8855](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8855>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L686>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L739>), [FrameBudgetTimings.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L743>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L150>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L890>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L148>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L176>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [CopyReadSide.java:1059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1059>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L511>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L594>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L501>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L562>) |
| `CELTIUM_FREE_MEMORY_SLOW` | [VulkanContext.java:2706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2706>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:20906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20906>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:21039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21039>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:159](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L159>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1363>) |
| `CELTIUM_GLDRAW_WINDOW_CENSUS` | [GlDrawWindowCensus.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L152>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5921>) |
| `CELTIUM_GLSM_HARVEST` | [VulkanModProgramPipeline.java:1446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1446>) |
| `CELTIUM_GLSM_LINK` | [ModProgramRegistry.java:458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L458>) |
| `CELTIUM_GLSTUB` | [GlsmFrontend.java:1012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1012>), [GlsmFrontend.java:1015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1015>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2858](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2858>), [CeltiumGllessHint.java:2862](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2862>), [CeltiumGllessNativeTransformer.java:154](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L154>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:54](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L54>), [CeleritasVintageMixinPlugin.java:56](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L56>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:6241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6241>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:6269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6269>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:625](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L625>), [CeltiumFontGlyphCapture.java:713](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L713>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1094](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1094>) |
| `CELTIUM_GL_CALLERS` | [CeltiumGllessHint.java:1358](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1358>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2821](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2821>), [CeltiumGllessHint.java:2828](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2828>), [CeltiumGllessHint.java:2834](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2834>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [GlsmFrontend.java:4656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4656>) |
| `CELTIUM_GL_DISPATCH_FOREIGN` | [GlsmFrontend.java:4718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4718>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:727](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L727>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:757](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L757>) |
| `CELTIUM_GPU_TERRAIN_CENSUS` | [VulkanGpuTerrainCensus.java:401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L401>) |
| `CELTIUM_GPU_TERRAIN_CENSUS_DISABLED` | [VulkanGpuTerrainCensus.java:246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L246>), [VulkanTerrainGpuBackend.java:19221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19221>), [VulkanTerrainGpuBackend.java:19251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19251>) |
| `CELTIUM_GPU_TERRAIN_CULLING_GATE` | [VulkanGpuTerrainCensus.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L182>), [VulkanTerrainGpuBackend.java:19196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19196>) |
| `CELTIUM_GPU_TERRAIN_DRAW` | [VulkanGpuTerrainDraw.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L195>), [VulkanGpuTerrainDraw.java:200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L200>), [VulkanGpuTerrainDraw.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L395>), [VulkanGpuTerrainDraw.java:489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L489>) |
| `CELTIUM_GPU_TERRAIN_DRAW_DISABLED` | [VulkanGpuTerrainDraw.java:254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L254>), [VulkanTerrainGpuBackend.java:19304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19304>), [VulkanTerrainGpuBackend.java:19615](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19615>) |
| `CELTIUM_GPU_TERRAIN_DRAW_MISMATCH` | [VulkanGpuTerrainDraw.java:550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java#L550>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:25413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L25413>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13569](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13569>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:5815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5815>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1264>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:16170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16170>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:24275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24275>) |
| `CELTIUM_GROUP_DECAY` | [GroupDecayCensus.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L82>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:20284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20284>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L759>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:24473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24473>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:24515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24515>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10485](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10485>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1353](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1353>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1762](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1762>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1790](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1790>) |
| `CELTIUM_HUD_CENSUS` | [HudCensus.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L237>), [HudCensus.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L240>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:7605](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7605>) |
| `CELTIUM_HUD_P0` | [VulkanTerrainGpuBackend.java:7669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7669>) |
| `CELTIUM_HUD_P0_SUMMARY` | [VulkanTerrainGpuBackend.java:7692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7692>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4105>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5176>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_BIND` | [GlStateQueries.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L195>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3137>) |
| `CELTIUM_IMGUNIT_ANSWER` | [GlStateQueries.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L190>), [CeltiumGllessHint.java:901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L901>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [GlDrawCensus.java:66](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L66>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L734>), [VulkanImmediateTexturePipeline.java:1065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1065>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:16064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16064>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L225>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:10025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10025>) |
| `CELTIUM_INDIRECT_PROBE` | [IndirectImmediateRuns.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L168>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:5584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5584>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:23643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23643>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:323](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L323>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2685>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L793>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L248>), [CeleritasWorldRenderer.java:251](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L251>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4494>), [VulkanGlTexture.java:4497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4497>), [VulkanGlTexture.java:4499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4499>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [GlsmFrontend.java:3058](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3058>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [GlsmFrontend.java:3165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3165>) |
| `CELTIUM_LEGACY_LIST_RUN` | [GlsmFrontend.java:3116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3116>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [GlsmFrontend.java:2771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2771>) |
| `CELTIUM_LEGACY_REFUSED` | [GlsmFrontend.java:2762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2762>), [GlsmFrontend.java:2766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2766>), [GlsmFrontend.java:2940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2940>), [GlsmFrontend.java:3063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3063>) |
| `CELTIUM_LEGACY_RUN` | [GlsmFrontend.java:3229](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3229>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1037>), [VulkanTextureRegistry.java:2190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2190>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:9770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9770>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:13916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13916>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:13906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13906>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:13995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13995>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:14026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14026>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:14058](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14058>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:19790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19790>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:9737](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9737>) |
| `CELTIUM_LIST_ABANDONED` | [GlsmFrontend.java:5368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5368>) |
| `CELTIUM_LIST_COMPILED` | [GlsmFrontend.java:5428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5428>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:537](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L537>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:866](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L866>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:5274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5274>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9596](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9596>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:116](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L116>), [CeltiumLoadingProgress.java:140](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L140>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L129>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L497>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3965>), [ValidationClient.java:3985](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3985>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10318](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10318>), [ValidationClient.java:10328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10328>), [ValidationClient.java:10334](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10334>) |
| `CELTIUM_MAIN_CLEAR` | [GlsmFrontend.java:4214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4214>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/BufferStorageRecord.java#L159>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L337>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L355>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:16048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16048>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1093](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1093>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:6356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6356>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:23651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23651>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12496](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12496>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:24285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24285>) |
| `CELTIUM_MESH_TERRAIN` | [VulkanMeshTerrain.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L89>), [VulkanMeshTerrain.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L269>), [VulkanMeshTerrain.java:571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTerrain.java#L571>) |
| `CELTIUM_MESH_TERRAIN_STAGE2` | [VulkanMeshTaskDraw.java:261](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L261>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_BUDGET_MISMATCH` | [VulkanMeshStage2Pipeline.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L202>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_DISABLED` | [VulkanMeshTaskDraw.java:254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L254>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_GATE` | [VulkanMeshTaskDraw.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshTaskDraw.java#L138>) |
| `CELTIUM_MESH_TERRAIN_STAGE2_PIPELINE` | [VulkanMeshStage2Pipeline.java:136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L136>), [VulkanMeshStage2Pipeline.java:556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanMeshStage2Pipeline.java#L556>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_COVERAGE` | [VulkanTerrainGpuBackend.java:19520](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19520>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_DISABLED` | [VulkanTerrainGpuBackend.java:19511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19511>) |
| `CELTIUM_MESH_TERRAIN_STAGE4_WIRE` | [VulkanTerrainGpuBackend.java:19502](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19502>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1832>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2148>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2317>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L204>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:23662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23662>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3678>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3692>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L886>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1741>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2261](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2261>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2400>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3256>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1961>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1736>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1052>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L355>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L439>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L342>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L45>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:7294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7294>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L492>), [ModelReplay.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L529>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L241>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L565>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L366>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L344>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L263>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1195>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1155>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L130>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L69>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L76>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L438>) |
| `CELTIUM_MODEL_PHASE` | [PatchGroups.java:239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L239>), [ModelPhaseTrace.java:168](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L168>), [ModelPhaseTrace.java:170](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L170>), [ModelPhaseTrace.java:172](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L172>), [ModelPhaseTrace.java:174](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L174>), [ModelPhaseTrace.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L176>), [ModelPhaseTrace.java:178](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L178>), [ModelPhaseTrace.java:180](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L180>), [ModelPhaseTrace.java:182](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L182>), [ModelPhaseTrace.java:184](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L184>), [ModelPhaseTrace.java:186](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L186>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L299>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L481>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L474>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L494>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L118>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L367>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L327>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L339>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L253>) |
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:24035](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24035>) |
| `CELTIUM_MOD_PROGRAM_FALLBACK` | [VulkanTerrainGpuBackend.java:11005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11005>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L447>) |
| `CELTIUM_MOD_PROGRAM_SUPPRESSED` | [ModPrograms.java:894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L894>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:823](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L823>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L680>) |
| `CELTIUM_MOD_SAMPLER_DEPTH_ATTACH` | [ModPrograms.java:865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L865>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L406>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L126>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13258](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13258>) |
| `CELTIUM_MULT_MATRIX` | [GlsmFrontend.java:6012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6012>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:3081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3081>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_GL_SURFACE` | [RenderSectionManager.java:216](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L216>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:17199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17199>), [GlsmFrontend.java:1053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1053>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:17201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17201>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L355>), [WorldVertexBufferUploaderMixin.java:368](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L368>) |
| `CELTIUM_NULL_RENDERER` | [NullRenderer.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L64>), [NullRenderer.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L73>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:8617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8617>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:334](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L334>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:436](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L436>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:6062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6062>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L638>), [FrameBudgetTimings.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L653>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L330>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PACK_SHADOW_CACHE` | [VulkanTerrainGpuBackend.java:18292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18292>) |
| `CELTIUM_PACK_TERRAIN_DRAWN` | [VulkanTerrainGpuBackend.java:18904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18904>) |
| `CELTIUM_PACK_TERRAIN_REFUSED` | [VulkanTerrainGpuBackend.java:19158](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19158>) |
| `CELTIUM_PACK_UNIFORM` | [PackUniformCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L375>) |
| `CELTIUM_PACK_UNIFORM_LANES` | [PackUniformCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L403>) |
| `CELTIUM_PACK_UNIFORM_PIPELINE` | [PackUniformCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L358>) |
| `CELTIUM_PACK_UNIFORM_PROGRAM` | [PackUniformCensus.java:304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L304>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:21359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21359>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:1166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1166>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:15631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15631>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:1039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1039>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [VulkanTerrainGpuBackend.java:15357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15357>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:65](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L65>) |
| `CELTIUM_PARTICLE_UV` | [GlsmFrontend.java:7358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7358>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L73>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L76>) |
| `CELTIUM_PATCH_ESSENTIAL` | [PatchErrorRecorder.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L98>) |
| `CELTIUM_PATCH_IGNORED` | [PatchLedger.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L177>), [PatchLedger.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L181>), [PatchLedger.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L184>), [PatchLedger.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L201>), [PatchLedger.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L207>) |
| `CELTIUM_PATCH_QUARANTINED` | [PatchErrorRecorder.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L110>) |
| `CELTIUM_PATCH_RECORD_FAILED` | [PatchErrorRecorder.java:114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L114>) |
| `CELTIUM_PATCH_REGISTERED` | [CeleritasVintageMixinPlugin.java:212](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L212>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1764>) |
| `CELTIUM_PATCH_SKIPPED` | [PatchLedger.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L269>), [PatchLedger.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L272>), [PatchLedger.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L275>) |
| `CELTIUM_PATCH_TABLE` | [PatchLedger.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L305>), [CeleritasVintageMixinPlugin.java:167](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L167>), [CeleritasVintageMixinPlugin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L174>), [CeleritasVintageMixinPlugin.java:220](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L220>) |
| `CELTIUM_PBO_REFUSED` | [GlTextureCaptures.java:606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L606>), [GlsmFrontend.java:7725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7725>) |
| `CELTIUM_PBO_UPLOAD` | [GlTextureCaptures.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L403>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9715>), [ValidationClient.java:9859](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9859>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2819>), [VulkanContext.java:2838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2838>), [VulkanContext.java:2874](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2874>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [VulkanContext.java:2565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2565>) |
| `CELTIUM_PIPELINE_CREATE_CENSUS` | [PipelineCreateCensus.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L175>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4399>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2574>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:516](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L516>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:497](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L497>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:487](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L487>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L352>), [VulkanPostChain.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L426>), [VulkanPostChain.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L438>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L547>), [VulkanPostChain.java:748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L748>), [VulkanPostChain.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L805>), [VulkanPostChain.java:835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L835>), [VulkanPostChain.java:839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L839>), [VulkanPostChain.java:1432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1432>), [VulkanPostChain.java:1437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1437>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6862>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1544>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L929>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1102>) |
| `CELTIUM_POW_GUARD_CENSUS` | [ModGlslTranslator.java:1964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1964>) |
| `CELTIUM_PREPARE_NULL` | [PrepareNullCensus.java:200](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L200>) |
| `CELTIUM_PRESENT` | [PresentSwapchain.java:618](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L618>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [PresentSwapchain.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L103>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11918](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11918>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L412>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L722>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2939>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L195>) |
| `CELTIUM_PRESENT_PATCH` | [PresentSwapchain.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L734>) |
| `CELTIUM_PRESENT_SLOW` | [PresentSwapchain.java:591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L591>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1414>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [GlStateQueries.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L250>) |
| `CELTIUM_PROGRAM_DRAW` | [GlsmFrontend.java:8216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8216>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:16007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16007>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1613](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1613>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [VulkanTerrainGpuBackend.java:7173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7173>) |
| `CELTIUM_RAW_BIND_REGISTER` | [VulkanTerrainGpuBackend.java:7197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7197>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [VulkanTerrainGpuBackend.java:7263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7263>) |
| `CELTIUM_RAW_FBO` | [GlsmFrontend.java:6626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6626>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:7870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7870>), [VulkanTerrainGpuBackend.java:7897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7897>) |
| `CELTIUM_RAW_FBO_DEPTH` | [GlsmFrontend.java:6595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6595>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:35](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L35>), [RawFboIdentityProbe.java:259](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L259>) |
| `CELTIUM_RAW_FBO_LEVEL` | [GlsmFrontend.java:6583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6583>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L766>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L260>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1683>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1223>), [GlsmFrontend.java:7736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7736>), [GlsmFrontend.java:7745](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7745>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [GlsmFrontend.java:7717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7717>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1215>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1083>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1070>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:25431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L25431>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:272](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L272>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L829>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:417](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L417>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4133](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4133>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:199](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L199>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:727](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L727>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:13145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13145>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9030>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9177>), [ValidationClient.java:9178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9178>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9119](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9119>), [ValidationClient.java:9153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9153>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9280>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9180](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9180>), [ValidationClient.java:9182](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9182>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5148>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5153>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3240>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2830](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2830>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2893>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:736](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L736>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L101>) |
| `CELTIUM_SECTION_MANAGER` | [VintageRenderSectionManager.java:56](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L56>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L188>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:11036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11036>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11048>) |
| `CELTIUM_SHADOW_FRUSTUM_INIT_CACHE` | [DemonicaShadowFrustumInitCacheMixin.java:100](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L100>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13304](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13304>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:15134](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15134>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:18026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18026>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1421>), [VulkanGlTexture.java:1460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1460>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L663>), [VulkanGlTexture.java:755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L755>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L715>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L788>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:931](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L931>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L974>) |
| `CELTIUM_SKY_DEGRADED` | [GlsmFrontend.java:9325](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9325>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:24074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24074>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:8741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8741>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:8728](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8728>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:8683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8683>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:8752](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8752>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [PresentSwapchain.java:784](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L784>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:23101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23101>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:13405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13405>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:13518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13518>), [VulkanTerrainGpuBackend.java:13523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13523>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9504](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9504>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:13431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13431>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:13585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13585>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3515>), [VulkanGlTexture.java:3632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3632>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L197>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4376>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:7942](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7942>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:3058](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3058>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L975>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L915>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:3047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3047>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2593>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2622>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [PresentSwapchain.java:254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L254>) |
| `CELTIUM_SWAPCHAIN_IMAGE_INIT` | [VulkanSwapchain.java:3208](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3208>), [VulkanSwapchain.java:3220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3220>), [VulkanSwapchain.java:3232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3232>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:3103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3103>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:639](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L639>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L269>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:7722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7722>) |
| `CELTIUM_TEARDOWN` | [VulkanContext.java:2325](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2325>), [VulkanContext.java:2409](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2409>), [VulkanTerrainGpuBackend.java:17670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17670>) |
| `CELTIUM_TERRAIN_A` | [TerrainEngineA.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L128>), [TerrainEngineA.java:400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L400>) |
| `CELTIUM_TERRAIN_A16_GUARD` | [CeleritasWorldRenderer.java:445](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L445>) |
| `CELTIUM_TERRAIN_A_BACKEND_DISABLED` | [VulkanTerrainGpuBackend.java:19656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19656>) |
| `CELTIUM_TERRAIN_A_DISABLED` | [TerrainEngineA.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java#L154>) |
| `CELTIUM_TERRAIN_A_REGISTER_FAILED` | [TerrainRenderer.java:1848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1848>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:19966](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19966>) |
| `CELTIUM_TERRAIN_BUDGET` | [VulkanTerrainGpuBackend.java:17716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17716>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [VulkanTerrainGpuBackend.java:17967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17967>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [VulkanTerrainGpuBackend.java:20585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20585>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [VulkanTerrainGpuBackend.java:17935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17935>), [VulkanTerrainGpuBackend.java:17952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17952>), [VulkanTerrainGpuBackend.java:17960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17960>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1769>) |
| `CELTIUM_TERRAIN_PIPELINE_FAILED` | [TerrainRenderer.java:542](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L542>) |
| `CELTIUM_TERRAIN_SHADOW` | [VulkanTerrainGpuBackend.java:19862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19862>), [VulkanTerrainGpuBackend.java:20029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20029>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L170>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L123>), [VulkanTextureArray.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L164>) |
| `CELTIUM_TEXTURE_DELETED` | [GlsmFrontend.java:6200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6200>) |
| `CELTIUM_TEXTURE_DIAG` | [CopyReadSide.java:1269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1269>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L706>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [GlsmFrontend.java:6884](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6884>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:895](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L895>) |
| `CELTIUM_TEXUNIT_ANSWER` | [GlStateQueries.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L174>) |
| `CELTIUM_TEX_COPY` | [GlsmFrontend.java:7500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7500>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [GlsmFrontend.java:1073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1073>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:740](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L740>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L361>), [TickTimings.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L376>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L365>), [TickTimings.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L419>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:143](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L143>), [CeleritasWorldRenderer.java:157](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L157>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1355](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1355>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L957>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1099>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [GlsmFrontend.java:5600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5600>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [GlsmFrontend.java:1054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1054>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:9913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9913>), [WorldVertexBufferUploaderMixin.java:341](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L341>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:9914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9914>), [WorldVertexBufferUploaderMixin.java:744](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L744>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:507](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L507>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4202>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:4343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4343>) |
| `CELTIUM_UPSTREAM` | [PatchGroups.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L260>), [UpstreamArrivalCensus.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L142>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:2932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2932>), [VulkanContext.java:2941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2941>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [GlsmFrontend.java:5488](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5488>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:2032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L2032>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:281](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L281>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1728](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1728>), [VulkanContext.java:1894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1894>), [VulkanContext.java:2239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2239>), [VulkanContext.java:2251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2251>), [VulkanContext.java:2293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2293>), [VulkanContext.java:2301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2301>), [VulkanContext.java:2349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2349>), [VulkanContext.java:2354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2354>), [VulkanContext.java:2370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2370>), [VulkanContext.java:2378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2378>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2259>), [VulkanContext.java:2282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2282>), [VulkanContext.java:2386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2386>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4606](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4606>), [ValidationClient.java:4610](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4610>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L775>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1014](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1014>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L788>), [VulkanTerrainGpuBackend.java:15592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15592>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1286](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1286>), [CeltiumGllessHint.java:1361](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1361>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1012>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1010](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1010>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:13315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13315>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1050>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:562](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L562>), [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>), [MinecraftMixin.java:588](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L588>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:22510](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22510>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9243>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:9262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9262>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1082>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L992>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L972>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:977](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L977>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L973>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:193](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L193>), [RenderSectionManager.java:195](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L195>), [RenderSectionManager.java:199](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L199>), [RenderSectionManager.java:201](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L201>), [RenderSectionManager.java:207](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L207>), [RenderSectionManager.java:210](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L210>), [SimpleWorldRenderer.java:47](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L47>), [SimpleWorldRenderer.java:50](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L50>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:283](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L283>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:301](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L301>), [SimpleWorldRenderer.java:304](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L304>), [SimpleWorldRenderer.java:307](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L307>), [SimpleWorldRenderer.java:314](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L314>), [SimpleWorldRenderer.java:321](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L321>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:5198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5198>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9004](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9004>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2636>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L536>), [VulkanImmediateTexturePipeline.java:878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L878>) |
