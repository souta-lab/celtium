# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **353 個**／文字列内マーカー **577 個**。
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
| [celtium.debug](#group-celtium-debug) | 49 |
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
| [celtium.glless](#group-celtium-glless) | 2 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.modelPhaseReport](#group-celtium-modelPhaseReport) | 1 |
| [celtium.native](#group-celtium-native) | 4 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.patch](#group-celtium-patch) | 2 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.releaseTextureCpuData](#group-celtium-releaseTextureCpuData) | 2 |
| [celtium.shaderc](#group-celtium-shaderc) | 2 |
| [celtium.terrain](#group-celtium-terrain) | 4 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 244 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L581>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13882](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13882>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20172>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20173>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20171>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L280>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19667>) |
| `celtium.debug.chainInputPatchFrames` | `System.getProperty` | [ModPrograms.java:549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L549>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L358>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20176>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20157>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19660>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3823](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3823>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3850](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3850>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2681](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2681>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L441>), [VulkanSwapchain.java:1717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1717>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L377>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L383>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L379>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L381>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L373>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:5310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5310>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19662>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19692>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19693>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [CeltiumGlStub.java:2785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2785>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:487](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L487>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:491](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L491>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L29>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L531>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:455](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L455>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L98>), [VulkanModelInstances.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L179>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10709>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:127](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L127>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19733>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3873>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19658>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3837>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:266](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L266>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L663>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3903>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3842>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3896](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3896>) |

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
| `celtium.dynamicLights.poison` | `Boolean.getBoolean` | [TerrainRenderer.java:1038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1038>) |

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
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L335>) |

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
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:583](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L583>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1617](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1617>), [CeltiumGllessNativeTransformer.java:406](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L406>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1151>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:11256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11256>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTrampolines.java#L116>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:11448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11448>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:5076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5076>), [CeltiumGlStub.java:11119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11119>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:5071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5071>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3225>) |

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
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L35>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3643>) |
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
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:815](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L815>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:803](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L803>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:822](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L822>) |

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

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:83](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L83>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:52](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L52>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:411](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L411>) |
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
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:21767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21767>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L103>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3370>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3316>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L67>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L69>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:71](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L71>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L74>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L18>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L60>) |
| `celtium.vulkan.asyncPresent` | `System.getProperty` | [VulkanContext.java:1629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1629>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:519](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L519>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19764>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4027](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4027>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateItemPipeline.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L426>), [VulkanImmediateTexturePipeline.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L509>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4212>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3817>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3824](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3824>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2046>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [CeltiumGlStub.java:380](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L380>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6054>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11493>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L190>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2483>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4017>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3164>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3174>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3175>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:1000](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1000>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:22417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22417>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2989>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19926](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19926>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2992>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2975>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2978>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3149>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3151>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2970>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2972>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21876>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21871>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2726>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L27>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4044>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11413>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4718>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L396>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4353>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L335>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L134>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L90>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L969>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L330>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L48>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L46>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:16000](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16000>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16003](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16003>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15982](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15982>) |
| `celtium.vulkan.frameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L497>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19484](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19484>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L40>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3207>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L474>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3814>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1885>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2831](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2831>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2871>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2833>), [VulkanTerrainGpuBackend.java:2849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2849>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2887>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2881>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3350>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L231>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3342>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1528](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1528>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3209>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2904>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2914>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2930>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2933>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1784](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1784>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3331](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3331>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3333>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3335>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L73>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L84>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L90>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3692>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L637>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3770>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L536>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3138>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2623>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4398>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2741>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:21720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21720>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6138>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2817>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:5890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5890>), [CeltiumGllessHint.java:1596](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1596>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L113>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19647](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19647>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19696](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19696>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1215>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L298>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4207>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L138>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L143>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L223>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L167>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L179>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L296>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4073>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L107>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1825>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:1370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1370>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L924>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:1371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1371>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L534>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:21869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21869>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:21856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21856>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19835>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L91>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19721>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L193>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [VulkanTerrainGpuBackend.java:389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L389>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16011](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16011>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16078>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L309>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L92>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2715>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L395>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3322>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L291>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L308>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13439>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16065>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L88>), [VulkanSwapchain.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L50>), [VulkanTerrainGpuBackend.java:3797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3797>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L328>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L472>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16058](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16058>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16068>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16071>) |
| `celtium.vulkan.presentTransferOnly` | `System.getProperty` | [VulkanSwapchain.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L58>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L476>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3157>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19479>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L56>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11506>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16047>) |
| `celtium.vulkan.recordsPositionTwins` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L62>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2744>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3811](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3811>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3812>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L45>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14023](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14023>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2438>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4340>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5023](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5023>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3780>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3159>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16041>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L637>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L673>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L715>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L717>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L689>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L671>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L662>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L654>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L534>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19656>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:6025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6025>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:11121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11121>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L249>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L233>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L86>), [VulkanSwapchain.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L48>), [VulkanTerrainGpuBackend.java:3226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3226>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:7272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7272>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>), [CeltiumGlStub.java:290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L290>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L351>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:7263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7263>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L344>) |
| `celtium.vulkan.terrainMaxDraws` | `Integer.getInteger` | [TerrainFrameBudget.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L29>) |
| `celtium.vulkan.terrainMaxRebuildsPerFrame` | `Integer.getInteger` | [TerrainFrameBudget.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L30>) |
| `celtium.vulkan.terrainMaxVertices` | `Long.getLong` | [TerrainFrameBudget.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L28>) |
| `celtium.vulkan.terrainRebuildTargetMicros` | `Long.getLong` | [TerrainFrameBudget.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L32>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3430>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3433>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3384>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3417>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L129>), [VulkanTerrainGpuBackend.java:2746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2746>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:15974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15974>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2588](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2588>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15331](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15331>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15339>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19811](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19811>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L521>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:1577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1577>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16654>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L223>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3243>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2995>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2998>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2185>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L623>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L35>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L547>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:10339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10339>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2698>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4170>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:5427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5427>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:5103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5103>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1395>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L616>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L470>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:647](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L647>), [VulkanArena.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L656>), [VulkanArena.java:667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L667>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:314](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L314>) |
| `CELTIUM_ASYNC_PRESENT` | [VulkanContext.java:1687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1687>), [VulkanContext.java:1695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1695>), [VulkanContext.java:1751](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1751>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L952>), [VulkanTerrainPipeline.java:993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L993>), [VulkanTerrainPipeline.java:1013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1013>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L403>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:16468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16468>) |
| `CELTIUM_BATCH_CACHE` | [BatchCacheCensus.java:116](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L116>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:9102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9102>) |
| `CELTIUM_BATCH_VIEWS` | [BatchCacheCensus.java:83](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L83>), [BatchCacheCensus.java:86](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L86>) |
| `CELTIUM_BE_LOS` | [BeVisibilityProbe.java:154](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L154>), [BeVisibilityProbe.java:421](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L421>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:7862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7862>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:7875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7875>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1107>) |
| `CELTIUM_BLIT_DEPTH` | [CeltiumGlStub.java:9769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9769>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:263](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L263>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:542](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L542>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:12069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12069>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:12109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12109>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:12727](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12727>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L63>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:4294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4294>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:764](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L764>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1601>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7882>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1622>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6609](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6609>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:4154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4154>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [PostChainBridge.java:1321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1321>), [VulkanTerrainGpuBackend.java:21539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21539>), [VulkanTerrainGpuBackend.java:21569](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21569>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L258>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L289>) |
| `CELTIUM_CHAIN_INPUT_PATCH` | [ModPrograms.java:781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L781>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:14388](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14388>) |
| `CELTIUM_CHAIN_PASS` | [VulkanTerrainGpuBackend.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L186>), [VulkanTerrainGpuBackend.java:206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L206>), [VulkanTerrainGpuBackend.java:257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L257>), [VulkanTerrainGpuBackend.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L293>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13058](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13058>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:4213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4213>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:18785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18785>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:11232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11232>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [CeltiumGlStub.java:10907](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10907>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:5186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5186>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:163](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L163>), [ForgeCloudCellsMixin.java:233](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L233>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3244](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3244>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:393](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L393>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:454](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L454>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L88>), [WorldVertexBufferUploaderMixin.java:497](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L497>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L452>) |
| `CELTIUM_COPY_IMAGE` | [CeltiumGlStub.java:9670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9670>), [CeltiumGlStub.java:9707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9707>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1669>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1659>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:20254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20254>), [VulkanTerrainGpuBackend.java:20256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20256>), [VulkanTerrainGpuBackend.java:20258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20258>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:20283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20283>), [VulkanTerrainGpuBackend.java:20285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20285>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:20606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20606>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:20233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20233>), [VulkanTerrainGpuBackend.java:20236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20236>), [VulkanTerrainGpuBackend.java:20238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20238>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:20291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20291>), [VulkanTerrainGpuBackend.java:20294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20294>), [VulkanTerrainGpuBackend.java:20297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20297>), [VulkanTerrainGpuBackend.java:20299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20299>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:20211](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20211>), [VulkanTerrainGpuBackend.java:20214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20214>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:3198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3198>) |
| `CELTIUM_DEPTH_COPY` | [VulkanTerrainGpuBackend.java:21245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21245>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGlStub.java:1578](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1578>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [VulkanTerrainGpuBackend.java:16875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16875>) |
| `CELTIUM_DEPTH_NOTE` | [VulkanTerrainGpuBackend.java:6502](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6502>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L857>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4153](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4153>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:3136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3136>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:3434](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3434>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3545](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3545>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:24348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24348>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4902](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4902>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4919](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4919>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4999](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4999>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7770](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7770>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7764>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5010>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8928](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8928>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5065](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5065>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5021>), [ValidationClient.java:8524](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8524>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:5293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5293>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L411>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1132>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L344>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L467>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L439>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L385>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1110>), [CeltiumGlStub.java:6432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6432>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:10482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10482>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:11333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11333>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:10537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10537>) |
| `CELTIUM_DRAWELEMENTS` | [CeltiumGlStub.java:1113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1113>), [CeltiumGlStub.java:6513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6513>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:18122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18122>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:20320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20320>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:23653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23653>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:23988](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23988>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:3277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3277>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L586>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:4076](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4076>) |
| `CELTIUM_DYN_POISON` | [TerrainRenderer.java:1115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1115>) |
| `CELTIUM_EFFECT_PAIR_SPAWNED` | [ValidationClient.java:3940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3940>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:159](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L159>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:172](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L172>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3881>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4958>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4968](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4968>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:10382](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10382>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:10390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10390>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L560>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L461>), [VulkanTextureRegistry.java:631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L631>) |
| `CELTIUM_EXT_ANSWER` | [CeltiumGlStub.java:7045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7045>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:896](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L896>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:7492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7492>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [VulkanTerrainGpuBackend.java:7764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7764>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:7473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7473>), [VulkanTerrainGpuBackend.java:7530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7530>), [VulkanTerrainGpuBackend.java:7542](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7542>), [VulkanTerrainGpuBackend.java:7567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7567>), [VulkanTerrainGpuBackend.java:7709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7709>), [VulkanTerrainGpuBackend.java:7731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7731>), [VulkanTerrainGpuBackend.java:7750](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7750>), [VulkanTerrainGpuBackend.java:7807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7807>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:7517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7517>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:7578](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7578>), [VulkanTerrainGpuBackend.java:7819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7819>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:24340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24340>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>), [ValidationClient.java:7323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7323>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4622](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4622>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1766>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:1726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1726>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:11478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11478>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:16183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16183>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:16195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16195>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:9155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9155>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:9189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9189>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:9178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9178>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1122>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:1406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1406>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:4732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4732>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L238>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L518>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1297](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1297>), [CeltiumFontGlyphCapture.java:1427](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1427>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:8556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8556>), [VulkanTerrainGpuBackend.java:8558](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8558>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1270](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1270>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1409](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1409>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1508](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1508>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L952>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:8530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8530>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:8452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8452>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:451](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L451>), [CeltiumFontGlyphCapture.java:455](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L455>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:8541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8541>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:838](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L838>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:12493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12493>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:8269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8269>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L677>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>), [FrameBudgetTimings.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L734>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L150>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L687>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L144>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L176>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [VulkanTerrainGpuBackend.java:22041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22041>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L342>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L425>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L332>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:393](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L393>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:18591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18591>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:18705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18705>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:159](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L159>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1363>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5486>) |
| `CELTIUM_GLSTUB` | [CeltiumGlStub.java:1060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1060>), [CeltiumGlStub.java:1063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1063>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2733](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2733>), [CeltiumGllessHint.java:2737](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2737>), [CeltiumGllessNativeTransformer.java:136](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L136>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:54](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L54>), [CeleritasVintageMixinPlugin.java:56](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L56>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5806](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5806>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5834>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:625](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L625>), [CeltiumFontGlyphCapture.java:713](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L713>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1094](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1094>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2696](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2696>), [CeltiumGllessHint.java:2703](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2703>), [CeltiumGllessHint.java:2709](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2709>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [CeltiumGlStub.java:5106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5106>) |
| `CELTIUM_GL_DISPATCH_FOREIGN` | [CeltiumGlStub.java:5168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5168>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:703](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L703>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:733](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L733>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:24815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24815>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13569](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13569>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:5380](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5380>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1259>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:15211](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15211>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:23711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23711>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:18101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18101>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L754>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:23907](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23907>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:23949](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23949>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10485](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10485>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1231](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1231>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1637](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1637>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1665](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1665>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:7049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7049>) |
| `CELTIUM_HUD_P0` | [VulkanTerrainGpuBackend.java:7113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7113>) |
| `CELTIUM_HUD_P0_SUMMARY` | [VulkanTerrainGpuBackend.java:7136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7136>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4105>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5176>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_BIND` | [CeltiumGlStub.java:7054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7054>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3106>) |
| `CELTIUM_IMGUNIT_ANSWER` | [CeltiumGlStub.java:7049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7049>), [CeltiumGllessHint.java:901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L901>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:6382](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6382>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L717>), [VulkanImmediateTexturePipeline.java:1052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1052>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:15113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15113>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L225>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:9413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9413>) |
| `CELTIUM_INDIRECT_PROBE` | [IndirectImmediateRuns.java:153](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L153>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:5149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5149>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:23102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23102>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:323](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L323>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2685>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L788>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:246](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L246>), [CeleritasWorldRenderer.java:249](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L249>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4451>), [VulkanGlTexture.java:4454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4454>), [VulkanGlTexture.java:4456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4456>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [CeltiumGlStub.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3160>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [CeltiumGlStub.java:3267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3267>) |
| `CELTIUM_LEGACY_LIST_RUN` | [CeltiumGlStub.java:3218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3218>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [CeltiumGlStub.java:2870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2870>) |
| `CELTIUM_LEGACY_REFUSED` | [CeltiumGlStub.java:2861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2861>), [CeltiumGlStub.java:2865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2865>), [CeltiumGlStub.java:3039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3039>), [CeltiumGlStub.java:3165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3165>) |
| `CELTIUM_LEGACY_RUN` | [CeltiumGlStub.java:3331](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3331>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1037>), [VulkanTextureRegistry.java:2190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2190>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:9158](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9158>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:13022](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13022>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:13012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13012>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:13101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13101>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:13132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13132>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:13164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13164>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:17715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17715>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:9125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9125>) |
| `CELTIUM_LIST_ABANDONED` | [CeltiumGlStub.java:5822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5822>) |
| `CELTIUM_LIST_COMPILED` | [CeltiumGlStub.java:5883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5883>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:523](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L523>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:781](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L781>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:4862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4862>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9596](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9596>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:111](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L111>), [CeltiumLoadingProgress.java:135](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L135>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L129>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:488](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L488>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L721>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3965>), [ValidationClient.java:3985](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3985>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10318](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10318>), [ValidationClient.java:10328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10328>), [ValidationClient.java:10334](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10334>) |
| `CELTIUM_MAIN_CLEAR` | [CeltiumGlStub.java:4350](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4350>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/BufferStorageRecord.java#L159>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L337>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L355>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:15097](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15097>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1093](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1093>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:5921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5921>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:23110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23110>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12496](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12496>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:23721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23721>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1802](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1802>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2118>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2287>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L204>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:23121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23121>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3635>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3649>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L886>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1741>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2231>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2370>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3225>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1961>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1706>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1047>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L355>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L439>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L342>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L45>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:7294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7294>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L489>), [ModelReplay.java:526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L526>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L241>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L562>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L366>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L344>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L263>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1195>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1155>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L130>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L69>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L76>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L435>) |
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
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:23473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23473>) |
| `CELTIUM_MOD_PROGRAM_FALLBACK` | [VulkanTerrainGpuBackend.java:10370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10370>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L314>) |
| `CELTIUM_MOD_PROGRAM_SUPPRESSED` | [ModPrograms.java:877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L877>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:806](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L806>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L680>) |
| `CELTIUM_MOD_SAMPLER_DEPTH_ATTACH` | [ModPrograms.java:848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L848>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L273>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L126>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13258](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13258>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:6639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6639>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:2998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2998>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_GL_SURFACE` | [RenderSectionManager.java:202](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L202>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:16233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16233>), [CeltiumGlStub.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1099>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:16235](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16235>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:332](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L332>), [WorldVertexBufferUploaderMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L345>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:8046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8046>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:334](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L334>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:436](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L436>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5627](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5627>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L629>), [FrameBudgetTimings.java:644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L644>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L310>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PACK_TERRAIN_DRAWN` | [VulkanTerrainGpuBackend.java:17476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17476>) |
| `CELTIUM_PACK_TERRAIN_REFUSED` | [VulkanTerrainGpuBackend.java:17648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17648>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:19197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19197>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:1033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1033>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:14716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14716>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:942](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L942>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [VulkanTerrainGpuBackend.java:14442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14442>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:65](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L65>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:9470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9470>) |
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
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:9036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9036>), [CeltiumGlStub.java:9837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9837>) |
| `CELTIUM_PBO_UPLOAD` | [CeltiumGlStub.java:8833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8833>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9715>), [ValidationClient.java:9859](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9859>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2735](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2735>), [VulkanContext.java:2754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2754>), [VulkanContext.java:2790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2790>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [VulkanContext.java:2577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2577>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4126>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2586>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:490](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L490>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:471](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L471>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:461](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L461>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L332>), [VulkanPostChain.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L426>), [VulkanPostChain.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L438>), [VulkanPostChain.java:540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L540>), [VulkanPostChain.java:544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L544>), [VulkanPostChain.java:720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L720>), [VulkanPostChain.java:775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L775>), [VulkanPostChain.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L805>), [VulkanPostChain.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L809>), [VulkanPostChain.java:1401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1401>), [VulkanPostChain.java:1406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1406>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6862>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1513>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:899](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L899>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:1072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1072>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:20678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20678>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:18972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18972>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11918](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11918>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L403>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:713](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L713>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2709>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L197>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:20787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20787>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:20651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20651>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1412>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [CeltiumGlStub.java:7109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7109>) |
| `CELTIUM_PROGRAM_DRAW` | [CeltiumGlStub.java:10327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10327>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:15056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15056>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1488](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1488>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [VulkanTerrainGpuBackend.java:6639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6639>) |
| `CELTIUM_RAW_BIND_REGISTER` | [VulkanTerrainGpuBackend.java:6663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6663>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [VulkanTerrainGpuBackend.java:6729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6729>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:8117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8117>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:7314](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7314>), [VulkanTerrainGpuBackend.java:7341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7341>) |
| `CELTIUM_RAW_FBO_DEPTH` | [CeltiumGlStub.java:8083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8083>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:35](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L35>), [RawFboIdentityProbe.java:259](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L259>) |
| `CELTIUM_RAW_FBO_LEVEL` | [CeltiumGlStub.java:8070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8070>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L766>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L231>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1653>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1223>), [CeltiumGlStub.java:9848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9848>), [CeltiumGlStub.java:9857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9857>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:9829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9829>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1215>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1083>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1070>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:24833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24833>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:261](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L261>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L829>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:417](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L417>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4133](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4133>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:197](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L197>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:649](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L649>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:12293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12293>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9030>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9177>), [ValidationClient.java:9178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9178>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9119](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9119>), [ValidationClient.java:9153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9153>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9280>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9180](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9180>), [ValidationClient.java:9182](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9182>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5148>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5153>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3209>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2800>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2863>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:658](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L658>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L101>) |
| `CELTIUM_SECTION_MANAGER` | [VintageRenderSectionManager.java:55](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L55>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L171>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:10401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10401>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:10413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10413>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13304](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13304>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:14223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14223>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:16984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16984>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1391>), [VulkanGlTexture.java:1430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1430>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L662>), [VulkanGlTexture.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L754>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L714>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L787>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L930>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L974>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:11436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11436>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:23512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23512>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:8169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8169>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:8156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8156>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:8112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8112>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:8180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8180>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [VulkanTerrainGpuBackend.java:20837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20837>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:22593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22593>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:12515](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12515>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:12628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12628>), [VulkanTerrainGpuBackend.java:12633](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12633>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9504](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9504>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:12541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12541>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:12695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12695>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3472>), [VulkanGlTexture.java:3589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3589>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L198>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4333>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:7386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7386>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2715>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L971>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L911>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2817>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2369>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2398>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:19123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19123>) |
| `CELTIUM_SWAPCHAIN_IMAGE_INIT` | [VulkanSwapchain.java:2972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2972>), [VulkanSwapchain.java:2984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2984>), [VulkanSwapchain.java:2996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2996>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:2873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2873>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:639](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L639>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L264>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:7166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7166>) |
| `CELTIUM_TEARDOWN` | [VulkanContext.java:2367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2367>), [VulkanContext.java:2451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2451>), [VulkanTerrainGpuBackend.java:16628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16628>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:17854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17854>) |
| `CELTIUM_TERRAIN_BUDGET` | [VulkanTerrainGpuBackend.java:16674](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16674>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [VulkanTerrainGpuBackend.java:16925](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16925>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [VulkanTerrainGpuBackend.java:18334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18334>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [VulkanTerrainGpuBackend.java:16893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16893>), [VulkanTerrainGpuBackend.java:16910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16910>), [VulkanTerrainGpuBackend.java:16918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16918>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1619>) |
| `CELTIUM_TERRAIN_SHADOW` | [VulkanTerrainGpuBackend.java:17772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17772>), [VulkanTerrainGpuBackend.java:17903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17903>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L170>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L123>), [VulkanTextureArray.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L164>) |
| `CELTIUM_TEXTURE_DELETED` | [CeltiumGlStub.java:6835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6835>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:22238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22238>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L701>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CeltiumGlStub.java:8431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8431>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:862](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L862>) |
| `CELTIUM_TEXUNIT_ANSWER` | [CeltiumGlStub.java:7033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7033>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:9612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9612>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1119>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:735](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L735>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L313>), [TickTimings.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L328>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L317>), [TickTimings.java:371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L371>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:141](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L141>), [CeleritasWorldRenderer.java:155](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L155>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1233](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1233>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L924>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1066>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:6051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6051>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1100>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:9301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9301>), [WorldVertexBufferUploaderMixin.java:318](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L318>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:9302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9302>), [WorldVertexBufferUploaderMixin.java:711](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L711>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:481](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L481>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4157>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:4070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4070>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:2849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2849>), [VulkanContext.java:2858](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2858>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:5941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5941>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1865>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:281](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L281>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1599>), [VulkanContext.java:1929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1929>), [VulkanContext.java:2277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2277>), [VulkanContext.java:2289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2289>), [VulkanContext.java:2335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2335>), [VulkanContext.java:2343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2343>), [VulkanContext.java:2391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2391>), [VulkanContext.java:2396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2396>), [VulkanContext.java:2412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2412>), [VulkanContext.java:2420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2420>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2297>), [VulkanContext.java:2324](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2324>), [VulkanContext.java:2428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2428>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4606](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4606>), [ValidationClient.java:4610](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4610>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L770>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1009](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1009>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L783>), [VulkanTerrainGpuBackend.java:14677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14677>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1164](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1164>), [CeltiumGllessHint.java:1236](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1236>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1007>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1005>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:12431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12431>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1045>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:562](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L562>), [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>), [MinecraftMixin.java:588](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L588>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:20415](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20415>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9243>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:8676](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8676>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1077>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:987](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L987>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L967>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L972>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:968](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L968>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:181](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L181>), [RenderSectionManager.java:185](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L185>), [RenderSectionManager.java:187](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L187>), [RenderSectionManager.java:193](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L193>), [RenderSectionManager.java:196](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L196>), [SimpleWorldRenderer.java:47](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L47>), [SimpleWorldRenderer.java:50](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L50>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:283](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L283>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:301](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L301>), [SimpleWorldRenderer.java:304](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L304>), [SimpleWorldRenderer.java:307](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L307>), [SimpleWorldRenderer.java:314](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L314>), [SimpleWorldRenderer.java:321](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L321>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4786>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9004](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9004>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2606>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L536>), [VulkanImmediateTexturePipeline.java:878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L878>) |
