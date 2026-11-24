# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **370 個**／文字列内マーカー **594 個**。
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
| [celtium.debug](#group-celtium-debug) | 51 |
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
| [celtium.native](#group-celtium-native) | 5 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.patch](#group-celtium-patch) | 2 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.releaseTextureCpuData](#group-celtium-releaseTextureCpuData) | 2 |
| [celtium.shaderc](#group-celtium-shaderc) | 2 |
| [celtium.terrain](#group-celtium-terrain) | 4 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 258 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:590](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L590>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14193>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20809>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20810](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20810>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20808>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L277>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20304>) |
| `celtium.debug.chainInputPatchFrames` | `System.getProperty` | [ModPrograms.java:549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L549>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L358>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20813>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20794>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20297>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3956](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3956>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3983](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3983>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2707>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L441>), [VulkanSwapchain.java:1717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1717>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L377>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L383>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L379>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L381>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L373>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:4983](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4983>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20299>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20329](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20329>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20330>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [CeltiumGlStub.java:2771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2771>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:487](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L487>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:491](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L491>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L29>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L531>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L458>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L98>), [VulkanModelInstances.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L179>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10918>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:128](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L128>) |
| `celtium.debug.packUniformLaneCensus` | `Boolean.getBoolean` | [PackUniformCensus.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L50>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.prepareNullCensus` | `Integer.getInteger`, `System.getProperty` | [PrepareNullCensus.java:114](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L114>), [PrepareNullCensus.java:118](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L118>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20370>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4006](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4006>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20295>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3970>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:266](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L266>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L663>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4036>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3975>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4029>) |

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
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1137>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:9365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9365>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTrampolines.java#L116>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:9556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9556>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:4749](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4749>), [CeltiumGlStub.java:9228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9228>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:4744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4744>) |

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
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3760>) |
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

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:84](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L84>) |
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
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [CopyReadSide.java:818](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L818>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L103>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3440>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3386>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L67>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L69>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:71](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L71>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L74>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L18>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L60>) |
| `celtium.vulkan.asyncPresent` | `System.getProperty` | [VulkanContext.java:1629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1629>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:519](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L519>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20401>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4160>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateItemPipeline.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L426>), [VulkanImmediateTexturePipeline.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L509>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4345>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3950>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3957>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2095](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2095>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [CeltiumGlStub.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L366>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6192>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11702>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L190>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2532>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4150>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3234>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3244>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3245>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:1013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1013>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21888>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3053>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20563>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3056>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3039>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3042](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3042>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3219>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3221>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3034](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3034>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3036>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21790>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:21785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21785>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2790>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L27>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4177>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11622>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4851>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L396>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4486>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L335>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L134>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L90>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L978>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L330>) |
| `celtium.vulkan.fractionalPowGuard` | `System.getProperty` | [ModGlslTranslator.java:1738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModGlslTranslator.java#L1738>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L48>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L46>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:16323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16323>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16326>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16305>) |
| `celtium.vulkan.frameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L497>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20121>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L40>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3277>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L474>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3947](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3947>) |
| `celtium.vulkan.glDrawWindowCensus` | `Boolean.getBoolean` | [GlDrawWindowCensus.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L56>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1934>) |
| `celtium.vulkan.gpuTerrainCulling` | `Boolean.getBoolean` | [GpuTerrainCulling.java:20](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L20>) |
| `celtium.vulkan.gpuTerrainCulling.census` | `Boolean.getBoolean` | [GpuTerrainCulling.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L28>) |
| `celtium.vulkan.gpuTerrainCulling.maxDistanceBlocks` | `System.getProperty` | [GpuTerrainCulling.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuTerrainCulling.java#L39>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2895>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2935>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2897>), [VulkanTerrainGpuBackend.java:2913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2913>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2951>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2945](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2945>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3420>) |
| `celtium.vulkan.groupDecayCensus` | `Boolean.getBoolean` | [GroupDecayCensus.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L24>) |
| `celtium.vulkan.groupDecayCensusFrames` | `Integer.getInteger` | [GroupDecayCensus.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L26>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L251>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3412>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1543>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3279>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2968](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2968>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2978>) |
| `celtium.vulkan.hudCensusFrames` | `Integer.getInteger` | [HudCensus.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L32>) |
| `celtium.vulkan.hudCensusMinFrame` | `Long.getLong` | [HudCensus.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L33>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2994](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2994>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2997](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2997>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1828>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3401>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3403>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3405>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L73>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L84>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L90>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3809>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L639>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3887>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L536>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3208](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3208>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2672>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4413>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2805>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [CopyReadSide.java:771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L771>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6278>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2881>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6028>), [CeltiumGllessHint.java:1596](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1596>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L113>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20284>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20333>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1215>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L298>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4340>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L138>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L143>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L223>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L167>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L179>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L296>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4206>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L107>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1869>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:1530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1530>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L995>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:1531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1531>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L534>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:21783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21783>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:21770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21770>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20472>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L91>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20358>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L193>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packShadowRebuildBudget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L398>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [VulkanTerrainGpuBackend.java:389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L389>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16334>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16401>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L309>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L92>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2747](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2747>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L395>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3392>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L291>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L308>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13750](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13750>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16388](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16388>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L88>), [VulkanSwapchain.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L50>), [VulkanTerrainGpuBackend.java:3914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3914>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L328>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L472>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16381>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16391>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16394>) |
| `celtium.vulkan.presentTransferOnly` | `System.getProperty` | [VulkanSwapchain.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L58>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L476>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3227>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20116>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L56>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11715](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11715>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16370>) |
| `celtium.vulkan.recordsPositionTwins` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:77](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L77>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2808>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3944>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3945](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3945>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L45>) |
| `celtium.vulkan.retainedIndexFlushExempt` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3937](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3937>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14334>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2438>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4473>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5161>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3897>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3229](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3229>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16364>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L646>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L682>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:724](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L724>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L726>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L698>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L680>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:671](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L671>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L663>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L534>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20293>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:5698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5698>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:9230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9230>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L249>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L233>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L86>), [VulkanSwapchain.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L48>), [VulkanTerrainGpuBackend.java:3296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3296>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:7412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7412>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>), [CeltiumGlStub.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L287>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L351>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:7403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7403>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L344>) |
| `celtium.vulkan.terrainMaxDraws` | `Integer.getInteger` | [TerrainFrameBudget.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L29>) |
| `celtium.vulkan.terrainMaxRebuildsPerFrame` | `Integer.getInteger` | [TerrainFrameBudget.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L30>) |
| `celtium.vulkan.terrainMaxVertices` | `Long.getLong` | [TerrainFrameBudget.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L28>) |
| `celtium.vulkan.terrainPreparePublishWalkOnce` | `Boolean.getBoolean` | [PostChainBridge.java:668](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L668>) |
| `celtium.vulkan.terrainRebuildTargetMicros` | `Long.getLong` | [TerrainFrameBudget.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainFrameBudget.java#L32>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3547>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3550>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3454>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3534>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L129>), [VulkanTerrainGpuBackend.java:2810](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2810>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16297>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2637>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15654>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:15662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15662>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20448>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L521>) |
| `celtium.vulkan.upstreamArrivalCensus` | `Boolean.getBoolean` | [UpstreamArrivalCensus.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L28>) |
| `celtium.vulkan.upstreamArrivalCensusFrames` | `Integer.getInteger` | [UpstreamArrivalCensus.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L30>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:1577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1577>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17021>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L223>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3313>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3059>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3062>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2234>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L632>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L35>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L547>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:10532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10532>) |
| `CELTIUM_ACCEPT_FLUSH` | [FramePassTimings.java:892](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L892>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2724](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2724>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4170](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4170>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:5565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5565>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:5241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5241>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1395>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L616>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L470>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:647](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L647>), [VulkanArena.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L656>), [VulkanArena.java:667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L667>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L337>) |
| `CELTIUM_ASYNC_PRESENT` | [VulkanContext.java:1687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1687>), [VulkanContext.java:1695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1695>), [VulkanContext.java:1751](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1751>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L952>), [VulkanTerrainPipeline.java:993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L993>), [VulkanTerrainPipeline.java:1013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1013>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L403>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:16833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16833>) |
| `CELTIUM_BATCH_CACHE` | [BatchCacheCensus.java:116](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L116>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:9273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9273>) |
| `CELTIUM_BATCH_VIEWS` | [BatchCacheCensus.java:83](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L83>), [BatchCacheCensus.java:86](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/BatchCacheCensus.java#L86>) |
| `CELTIUM_BE_LOS` | [BeVisibilityProbe.java:154](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L154>), [BeVisibilityProbe.java:421](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/BeVisibilityProbe.java#L421>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:8002](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8002>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:8015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8015>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1093](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1093>) |
| `CELTIUM_BLIT_DEPTH` | [CeltiumGlStub.java:7878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7878>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:263](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L263>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:542](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L542>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:12278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12278>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:12320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12320>) |
| `CELTIUM_BOUND_TARGET_CENSUS` | [VulkanTerrainGpuBackend.java:3485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3485>), [VulkanTerrainGpuBackend.java:18369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18369>) |
| `CELTIUM_BOUND_TARGET_COVERAGE` | [VulkanTerrainGpuBackend.java:18437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18437>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:13034](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13034>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L63>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:4366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4366>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:795](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L795>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1616>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7882>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1637>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6609](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6609>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:4287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4287>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [CopyReadSide.java:591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L591>), [CopyReadSide.java:621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L621>), [PostChainBridge.java:1399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1399>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L258>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L289>) |
| `CELTIUM_CHAIN_INPUT_PATCH` | [ModPrograms.java:796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L796>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:14699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14699>) |
| `CELTIUM_CHAIN_PASS` | [VulkanTerrainGpuBackend.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L186>), [VulkanTerrainGpuBackend.java:206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L206>), [VulkanTerrainGpuBackend.java:257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L257>), [VulkanTerrainGpuBackend.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L293>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13112](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13112>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:4285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4285>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:19422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19422>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:9341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9341>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [CeltiumGlStub.java:9016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9016>) |
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
| `CELTIUM_COPY_IMAGE` | [CeltiumGlStub.java:7779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7779>), [CeltiumGlStub.java:7816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7816>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1669>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1659>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:20891](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20891>), [VulkanTerrainGpuBackend.java:20893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20893>), [VulkanTerrainGpuBackend.java:20895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20895>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:20920](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20920>), [VulkanTerrainGpuBackend.java:20922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20922>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:21245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21245>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:20870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20870>), [VulkanTerrainGpuBackend.java:20873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20873>), [VulkanTerrainGpuBackend.java:20875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20875>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:20928](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20928>), [VulkanTerrainGpuBackend.java:20931](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20931>), [VulkanTerrainGpuBackend.java:20934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20934>), [VulkanTerrainGpuBackend.java:20936](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20936>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:20848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20848>), [VulkanTerrainGpuBackend.java:20851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20851>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:3268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3268>) |
| `CELTIUM_DELETE_SETTLED_SLOW` | [VulkanTerrainGpuBackend.java:16776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16776>) |
| `CELTIUM_DEPTH_COPY` | [CopyReadSide.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L291>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGlStub.java:1564](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1564>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [VulkanTerrainGpuBackend.java:17242](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17242>) |
| `CELTIUM_DEPTH_NOTE` | [VulkanTerrainGpuBackend.java:6642](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6642>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L857>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4225>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:3181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3181>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:3480](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3480>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3616>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:23878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23878>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4902](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4902>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4919](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4919>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4999](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4999>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7770](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7770>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7764>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5010>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8928](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8928>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5065](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5065>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5021>), [ValidationClient.java:8524](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8524>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:5431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5431>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L411>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1118>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L344>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L467>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L439>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L385>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1096>), [GlDrawCensus.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L118>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:8591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8591>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:9441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9441>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:8646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8646>) |
| `CELTIUM_DRAWELEMENTS` | [CeltiumGlStub.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1099>), [GlDrawCensus.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L203>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:18746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18746>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:20957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20957>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:23173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23173>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:23512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23512>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:3323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3323>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L586>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:4076](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4076>) |
| `CELTIUM_DYN_POISON` | [TerrainRenderer.java:1115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1115>) |
| `CELTIUM_EFFECT_PAIR_SPAWNED` | [ValidationClient.java:3940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3940>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:160](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L160>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:172](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L172>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3881>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4958>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4968](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4968>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:10576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10576>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:10584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10584>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L560>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L461>), [VulkanTextureRegistry.java:631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L631>) |
| `CELTIUM_EXT_ANSWER` | [GlStateQueries.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L177>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:896](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L896>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:7632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7632>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [VulkanTerrainGpuBackend.java:7904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7904>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:7613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7613>), [VulkanTerrainGpuBackend.java:7670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7670>), [VulkanTerrainGpuBackend.java:7682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7682>), [VulkanTerrainGpuBackend.java:7707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7707>), [VulkanTerrainGpuBackend.java:7849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7849>), [VulkanTerrainGpuBackend.java:7871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7871>), [VulkanTerrainGpuBackend.java:7890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7890>), [VulkanTerrainGpuBackend.java:7947](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7947>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:7657](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7657>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:7718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7718>), [VulkanTerrainGpuBackend.java:7959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7959>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:23870](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23870>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>), [ValidationClient.java:7323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7323>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4622](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4622>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1766>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:1726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1726>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:11687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11687>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:16512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16512>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:16524](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16524>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:9326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9326>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:9360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9360>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:9349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9349>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1108>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:1406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1406>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:4865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4865>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L238>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L518>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1297](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1297>), [CeltiumFontGlyphCapture.java:1427](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1427>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:8719](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8719>), [VulkanTerrainGpuBackend.java:8721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8721>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1270](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1270>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1409](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1409>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1508](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1508>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L952>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:8693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8693>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:8615](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8615>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:451](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L451>), [CeltiumFontGlyphCapture.java:455](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L455>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:8704](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8704>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:871](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L871>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:12800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12800>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:8432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8432>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L686>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L739>), [FrameBudgetTimings.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L743>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L150>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L885>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L144>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L176>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [CopyReadSide.java:1055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1055>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L506>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L589>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L496>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L557>) |
| `CELTIUM_FREE_MEMORY_SLOW` | [VulkanContext.java:2684](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2684>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:19228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19228>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:19342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19342>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:159](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L159>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1363>) |
| `CELTIUM_GLDRAW_WINDOW_CENSUS` | [GlDrawWindowCensus.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L152>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5624](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5624>) |
| `CELTIUM_GLSTUB` | [CeltiumGlStub.java:1046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1046>), [CeltiumGlStub.java:1049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1049>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2733](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2733>), [CeltiumGllessHint.java:2737](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2737>), [CeltiumGllessNativeTransformer.java:136](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L136>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:54](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L54>), [CeleritasVintageMixinPlugin.java:56](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L56>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5944>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5972>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:625](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L625>), [CeltiumFontGlyphCapture.java:713](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L713>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1094](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1094>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2696](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2696>), [CeltiumGllessHint.java:2703](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2703>), [CeltiumGllessHint.java:2709](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2709>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [CeltiumGlStub.java:4779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4779>) |
| `CELTIUM_GL_DISPATCH_FOREIGN` | [CeltiumGlStub.java:4841](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4841>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:703](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L703>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:733](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L733>) |
| `CELTIUM_GPU_TERRAIN_CENSUS` | [VulkanGpuTerrainCensus.java:334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L334>) |
| `CELTIUM_GPU_TERRAIN_CENSUS_DISABLED` | [VulkanGpuTerrainCensus.java:206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L206>), [VulkanTerrainGpuBackend.java:18163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18163>) |
| `CELTIUM_GPU_TERRAIN_CULLING_GATE` | [VulkanGpuTerrainCensus.java:150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainCensus.java#L150>), [VulkanTerrainGpuBackend.java:18141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18141>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:24345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24345>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13623>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:5518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5518>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1265>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:15533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15533>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:23233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23233>) |
| `CELTIUM_GROUP_DECAY` | [GroupDecayCensus.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L82>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:18725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18725>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L760>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:23431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23431>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:23473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23473>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10539](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10539>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1231](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1231>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1637](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1637>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1665](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1665>) |
| `CELTIUM_HUD_CENSUS` | [HudCensus.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L237>), [HudCensus.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L240>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:7189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7189>) |
| `CELTIUM_HUD_P0` | [VulkanTerrainGpuBackend.java:7253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7253>) |
| `CELTIUM_HUD_P0_SUMMARY` | [VulkanTerrainGpuBackend.java:7276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7276>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4105>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5176>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_BIND` | [GlStateQueries.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L186>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3121>) |
| `CELTIUM_IMGUNIT_ANSWER` | [GlStateQueries.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L181>), [CeltiumGllessHint.java:901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L901>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [GlDrawCensus.java:66](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L66>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L734>), [VulkanImmediateTexturePipeline.java:1065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L1065>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:15427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15427>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L225>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:9584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9584>) |
| `CELTIUM_INDIRECT_PROBE` | [IndirectImmediateRuns.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L168>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:5287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5287>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:22601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22601>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:323](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L323>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2685>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L794>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:246](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L246>), [CeleritasWorldRenderer.java:249](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L249>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4466>), [VulkanGlTexture.java:4469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4469>), [VulkanGlTexture.java:4471](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4471>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [CeltiumGlStub.java:3146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3146>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [CeltiumGlStub.java:3253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3253>) |
| `CELTIUM_LEGACY_LIST_RUN` | [CeltiumGlStub.java:3204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3204>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [CeltiumGlStub.java:2856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2856>) |
| `CELTIUM_LEGACY_REFUSED` | [CeltiumGlStub.java:2847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2847>), [CeltiumGlStub.java:2851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2851>), [CeltiumGlStub.java:3025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3025>), [CeltiumGlStub.java:3151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3151>) |
| `CELTIUM_LEGACY_RUN` | [CeltiumGlStub.java:3317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3317>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1037>), [VulkanTextureRegistry.java:2190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2190>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:9329](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9329>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:13333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13333>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:13323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13323>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:13412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13412>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:13443](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13443>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:13475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13475>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:18254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18254>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:9296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9296>) |
| `CELTIUM_LIST_ABANDONED` | [CeltiumGlStub.java:5495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5495>) |
| `CELTIUM_LIST_COMPILED` | [CeltiumGlStub.java:5556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5556>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:523](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L523>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:812](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L812>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:4995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4995>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9596](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9596>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:116](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L116>), [CeltiumLoadingProgress.java:140](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L140>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L129>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L497>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3965>), [ValidationClient.java:3985](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3985>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10372>), [ValidationClient.java:10382](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10382>), [ValidationClient.java:10388](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10388>) |
| `CELTIUM_MAIN_CLEAR` | [CeltiumGlStub.java:4336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4336>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/BufferStorageRecord.java#L159>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:337](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L337>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L355>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:15411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15411>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1093](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1093>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:6059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6059>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:22609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22609>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12550](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12550>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:23243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23243>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1817>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2133>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2302>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:204](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L204>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:22620](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22620>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3650](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3650>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3664>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L886>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1741>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2246>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2385>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3240>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1961>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1721>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1053>) |
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
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:22993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22993>) |
| `CELTIUM_MOD_PROGRAM_FALLBACK` | [VulkanTerrainGpuBackend.java:10564](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10564>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L319>) |
| `CELTIUM_MOD_PROGRAM_SUPPRESSED` | [ModPrograms.java:894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L894>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:823](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L823>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L680>) |
| `CELTIUM_MOD_SAMPLER_DEPTH_ATTACH` | [ModPrograms.java:865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L865>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L278>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java#L126>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13312](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13312>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:6136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6136>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:3030](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3030>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_GL_SURFACE` | [RenderSectionManager.java:202](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L202>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:16562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16562>), [CeltiumGlStub.java:1085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1085>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:16564](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16564>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L355>), [WorldVertexBufferUploaderMixin.java:368](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L368>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:8195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8195>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:334](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L334>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:436](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L436>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5765>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L638>), [FrameBudgetTimings.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L653>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L310>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PACK_TERRAIN_DRAWN` | [VulkanTerrainGpuBackend.java:17951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17951>) |
| `CELTIUM_PACK_TERRAIN_REFUSED` | [VulkanTerrainGpuBackend.java:18123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18123>) |
| `CELTIUM_PACK_UNIFORM` | [PackUniformCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L375>) |
| `CELTIUM_PACK_UNIFORM_LANES` | [PackUniformCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L403>) |
| `CELTIUM_PACK_UNIFORM_PIPELINE` | [PackUniformCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L358>) |
| `CELTIUM_PACK_UNIFORM_PROGRAM` | [PackUniformCensus.java:304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L304>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:19834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19834>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:1111](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1111>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:15027](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15027>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:1007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1007>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [VulkanTerrainGpuBackend.java:14753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14753>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:65](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L65>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:7579](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7579>) |
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
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:7946](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7946>), [GlTextureCaptures.java:606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L606>) |
| `CELTIUM_PBO_UPLOAD` | [GlTextureCaptures.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L403>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9715](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9715>), [ValidationClient.java:9859](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9859>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2767>), [VulkanContext.java:2786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2786>), [VulkanContext.java:2822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2822>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L38>), [VulkanContext.java:2577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2577>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:4198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4198>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2586>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:516](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L516>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:497](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L497>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:487](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L487>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L332>), [VulkanPostChain.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L426>), [VulkanPostChain.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L438>), [VulkanPostChain.java:540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L540>), [VulkanPostChain.java:544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L544>), [VulkanPostChain.java:720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L720>), [VulkanPostChain.java:775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L775>), [VulkanPostChain.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L805>), [VulkanPostChain.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L809>), [VulkanPostChain.java:1401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1401>), [VulkanPostChain.java:1406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1406>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6862>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1513>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:899](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L899>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:1072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1072>) |
| `CELTIUM_PREPARE_NULL` | [PrepareNullCensus.java:200](<../forge122/src/main/java/org/taumc/celeritas/impl/world/PrepareNullCensus.java#L200>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:21317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21317>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:19609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19609>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11972](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11972>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L412>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L722>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2709>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L197>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:21428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21428>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:21290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21290>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1412>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [GlStateQueries.java:241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L241>) |
| `CELTIUM_PROGRAM_DRAW` | [CeltiumGlStub.java:8436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8436>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:15370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15370>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1488](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1488>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [VulkanTerrainGpuBackend.java:6779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6779>) |
| `CELTIUM_RAW_BIND_REGISTER` | [VulkanTerrainGpuBackend.java:6803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6803>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [VulkanTerrainGpuBackend.java:6869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6869>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:6776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6776>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:7454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7454>), [VulkanTerrainGpuBackend.java:7481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7481>) |
| `CELTIUM_RAW_FBO_DEPTH` | [CeltiumGlStub.java:6742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6742>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:35](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L35>), [RawFboIdentityProbe.java:259](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L259>) |
| `CELTIUM_RAW_FBO_LEVEL` | [CeltiumGlStub.java:6729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6729>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L766>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L231>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1668](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1668>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1223>), [CeltiumGlStub.java:7957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7957>), [CeltiumGlStub.java:7966](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7966>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:7938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7938>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1215>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1083>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1070>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:24363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L24363>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:272](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L272>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L829>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:417](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L417>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4133](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4133>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:197](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L197>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:680](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L680>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:12571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12571>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9030>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9177>), [ValidationClient.java:9178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9178>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9119](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9119>), [ValidationClient.java:9153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9153>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9280>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9180](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9180>), [ValidationClient.java:9182](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9182>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5148>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5153>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3224>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2815>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2878>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:689](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L689>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L101>) |
| `CELTIUM_SECTION_MANAGER` | [VintageRenderSectionManager.java:56](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L56>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L171>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:10595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10595>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:10607](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10607>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13358](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13358>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:14534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14534>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:17351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17351>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1406>), [VulkanGlTexture.java:1445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1445>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L662>), [VulkanGlTexture.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L754>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:714](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L714>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L787>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L930>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L974>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:9544](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9544>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:23032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23032>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:8318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8318>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:8305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8305>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:8261](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8261>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:8329](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8329>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [VulkanTerrainGpuBackend.java:21478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21478>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:22059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22059>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:12822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12822>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:12935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12935>), [VulkanTerrainGpuBackend.java:12940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12940>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9504](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9504>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:12848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12848>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:13002](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13002>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3487>), [VulkanGlTexture.java:3604](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3604>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L198>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4348>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:7526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7526>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2779>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L971>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L911>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2817>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2369>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2398>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:19760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19760>) |
| `CELTIUM_SWAPCHAIN_IMAGE_INIT` | [VulkanSwapchain.java:2972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2972>), [VulkanSwapchain.java:2984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2984>), [VulkanSwapchain.java:2996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2996>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:2873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2873>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:639](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L639>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L264>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:7306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7306>) |
| `CELTIUM_TEARDOWN` | [VulkanContext.java:2367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2367>), [VulkanContext.java:2451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2451>), [VulkanTerrainGpuBackend.java:16995](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16995>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:18430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18430>) |
| `CELTIUM_TERRAIN_BUDGET` | [VulkanTerrainGpuBackend.java:17041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17041>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [VulkanTerrainGpuBackend.java:17292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17292>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [VulkanTerrainGpuBackend.java:18971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18971>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [VulkanTerrainGpuBackend.java:17260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17260>), [VulkanTerrainGpuBackend.java:17277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17277>), [VulkanTerrainGpuBackend.java:17285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17285>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1619>) |
| `CELTIUM_TERRAIN_SHADOW` | [VulkanTerrainGpuBackend.java:18326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18326>), [VulkanTerrainGpuBackend.java:18497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18497>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L170>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L123>), [VulkanTextureArray.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L164>) |
| `CELTIUM_TEXTURE_DELETED` | [CeltiumGlStub.java:6332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6332>) |
| `CELTIUM_TEXTURE_DIAG` | [CopyReadSide.java:1252](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1252>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L707>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CeltiumGlStub.java:7090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7090>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:895](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L895>) |
| `CELTIUM_TEXUNIT_ANSWER` | [GlStateQueries.java:165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L165>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:7721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7721>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1105>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L741>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L361>), [TickTimings.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L376>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L365>), [TickTimings.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L419>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:141](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L141>), [CeleritasWorldRenderer.java:155](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L155>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1233](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1233>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L924>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1066>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:5724](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5724>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1086>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:9472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9472>), [WorldVertexBufferUploaderMixin.java:341](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L341>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:9473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9473>), [WorldVertexBufferUploaderMixin.java:744](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L744>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:507](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L507>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4172>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:4142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4142>) |
| `CELTIUM_UPSTREAM` | [PatchGroups.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L260>), [UpstreamArrivalCensus.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L142>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:2881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2881>), [VulkanContext.java:2890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2890>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:5614](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5614>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1865>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:281](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L281>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1599>), [VulkanContext.java:1929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1929>), [VulkanContext.java:2277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2277>), [VulkanContext.java:2289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2289>), [VulkanContext.java:2335](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2335>), [VulkanContext.java:2343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2343>), [VulkanContext.java:2391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2391>), [VulkanContext.java:2396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2396>), [VulkanContext.java:2412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2412>), [VulkanContext.java:2420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2420>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2297>), [VulkanContext.java:2324](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2324>), [VulkanContext.java:2428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2428>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4606](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4606>), [ValidationClient.java:4610](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4610>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L776>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1015>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L789>), [VulkanTerrainGpuBackend.java:14988](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14988>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1164](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1164>), [CeltiumGllessHint.java:1236](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1236>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1013>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1011](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1011>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:12732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12732>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1051>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:562](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L562>), [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>), [MinecraftMixin.java:588](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L588>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:21052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21052>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9243>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:8839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8839>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1083>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L993>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:973](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L973>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L978>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:974](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L974>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:181](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L181>), [RenderSectionManager.java:185](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L185>), [RenderSectionManager.java:187](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L187>), [RenderSectionManager.java:193](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L193>), [RenderSectionManager.java:196](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L196>), [SimpleWorldRenderer.java:47](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L47>), [SimpleWorldRenderer.java:50](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L50>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:283](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L283>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:301](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L301>), [SimpleWorldRenderer.java:304](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L304>), [SimpleWorldRenderer.java:307](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L307>), [SimpleWorldRenderer.java:314](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L314>), [SimpleWorldRenderer.java:321](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L321>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4919>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9004](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9004>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2621>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L536>), [VulkanImmediateTexturePipeline.java:878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L878>) |
