# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **357 個**／文字列内マーカー **573 個**。
機能設定・故障注入も含む。すべてが計測用スイッチという意味ではない。

名前を検索してリンク先の実装を読む。既定値・有効条件・出力上限は推測で掲載しない。
索引は `System.getProperty` / `Boolean.getBoolean` / `Integer.getInteger` / `Long.getLong` の
リテラルまたは単純な static String 定数引数だけを収録する。計算されたキー・別API・テストは対象外。
マーカーは接頭辞つきの文字列も収録するが、ログ出力以外の参照も含む。`--check` は鮮度検査であり網羅性の証明ではない。

手順と測定用途は [開発・検証手順](<development.md>)。全文を毎回ロードせず、用途から探す。

## 名前空間

| 名前空間 | 読み取りキー数 |
|---|---|
| [celtium.allowCompetitors](#group-celtium-allowCompetitors) | 1 |
| [celtium.backendCounters](#group-celtium-backendCounters) | 1 |
| [celtium.clearlatch](#group-celtium-clearlatch) | 1 |
| [celtium.debug](#group-celtium-debug) | 46 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.dynamicLights](#group-celtium-dynamicLights) | 1 |
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
| [celtium.native](#group-celtium-native) | 4 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.patch](#group-celtium-patch) | 2 |
| [celtium.probe](#group-celtium-probe) | 3 |
| [celtium.releaseTextureCpuData](#group-celtium-releaseTextureCpuData) | 2 |
| [celtium.shader](#group-celtium-shader) | 2 |
| [celtium.shaderc](#group-celtium-shaderc) | 2 |
| [celtium.shadowFrustumInitCache](#group-celtium-shadowFrustumInitCache) | 1 |
| [celtium.spirvCache](#group-celtium-spirvCache) | 2 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 251 |
| [celtium.vulkanTerrain](#group-celtium-vulkanTerrain) | 1 |

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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L738>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15132>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:19795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19795>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:19796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19796>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:19794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19794>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [GlsmFrontend.java:274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L274>) |
| `celtium.debug.bindlessSlotDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:21212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21212>), [VulkanTextureRegistry.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L418>), [BindlessTextureBinding.java:59](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/bindless/BindlessTextureBinding.java#L59>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19267>) |
| `celtium.debug.chainInputPatchFrames` | `System.getProperty` | [ModPrograms.java:651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L651>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L322>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19799>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19780](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19780>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19260>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4283>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4310>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:3612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3612>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L476>), [VulkanSwapchain.java:2131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2131>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L412>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L418>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L414>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:416](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L416>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L408>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [GlsmFrontend.java:4924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4924>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19262>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19292>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19293>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [GlsmFrontend.java:2707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2707>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L32>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:633](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L633>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L487>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:99](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L99>), [VulkanModelInstances.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L180>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:11505](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11505>) |
| `celtium.debug.packUniformLaneCensus` | `Boolean.getBoolean` | [PackUniformCensus.java:50](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L50>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19333>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4333>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19258>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4297](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4297>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L826>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4363>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4302>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4356>) |

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
| `celtium.dynamicLights` | `System.getProperty` | [MinecraftDynamicLightService.java:47](<../forge122/src/main/java/org/soutalab/celtium/forge/light/MinecraftDynamicLightService.java#L47>) |

<a id="group-celtium-entityLightCache"></a>
## `celtium.entityLightCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:67](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L67>) |

<a id="group-celtium-f3"></a>
## `celtium.f3.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.f3.fullSummary` | `Boolean.getBoolean` | [CeltiumF3Overlay.java:36](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L36>) |
| `celtium.f3.sectionRefreshFrames` | `Integer.getInteger` | [CeltiumF3Overlay.java:40](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L40>) |

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
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1853](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1853>), [CeltiumGllessNativeTransformer.java:424](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L424>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [GlsmFrontend.java:1107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1107>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [GlsmFrontend.java:9384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9384>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTrampolines.java#L116>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [GlsmFrontend.java:9575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9575>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [GlsmFrontend.java:4682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4682>), [GlsmFrontend.java:9247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9247>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [GlsmFrontend.java:4677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4677>) |

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
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4071>) |

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
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:897](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L897>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:885](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L885>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:904](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L904>) |

<a id="group-celtium-releaseTextureCpuData"></a>
## `celtium.releaseTextureCpuData.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.releaseTextureCpuData` | `Boolean.getBoolean` | [CeltiumFrameRelease.java:43](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L43>) |
| `celtium.releaseTextureCpuData.exclude` | `System.getProperty` | [CeltiumFrameRelease.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L63>) |

<a id="group-celtium-shader"></a>
## `celtium.shader.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shader.debug` | `Boolean.getBoolean` | [ShaderLogger.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/shader/ShaderLogger.java#L14>) |
| `celtium.shader.dumpPropertiesDir` | `System.getProperty` | [ShaderProperties.java:132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/shader/shaderpack/ShaderProperties.java#L132>) |

<a id="group-celtium-shaderc"></a>
## `celtium.shaderc.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shaderc.maxConcurrent` | `Integer.getInteger` | [ModShaderCompiler.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModShaderCompiler.java#L39>) |
| `celtium.shaderc.timeoutMs` | `Long.getLong` | [ModShaderCompiler.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModShaderCompiler.java#L31>) |

<a id="group-celtium-shadowFrustumInitCache"></a>
## `celtium.shadowFrustumInitCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.shadowFrustumInitCache` | `Boolean.getBoolean` | [DemonicaShadowFrustumInitCacheMixin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L52>) |

<a id="group-celtium-spirvCache"></a>
## `celtium.spirvCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.spirvCache.dir` | `System.getProperty` | [SpirvDiskCache.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/SpirvDiskCache.java#L67>) |
| `celtium.spirvCache.enabled` | `System.getProperty` | [SpirvDiskCache.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/SpirvDiskCache.java#L62>) |

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
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3775>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3721>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L75>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:77](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L77>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:79](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L79>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L82>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L62>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L593>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19364>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4487>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4672>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4277>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4284>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2123>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [GlsmFrontend.java:351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L351>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6537>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12298>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L198>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2596](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2596>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4477](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4477>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3566>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3576>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3577>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTextureRecordsPipeline.java:965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L965>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20451>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3203>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19549>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3206>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3189>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3192>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3404](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3404>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3406>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3184>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3186>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20353>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:20347](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20347>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2940>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L23>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4504](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4504>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:12209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12209>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:5178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5178>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L419>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4813>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L370>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1155>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L365>) |
| `celtium.vulkan.fractionalPowGuard` | `System.getProperty` | [ModGlslTranslator.java:2021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2021>) |
| `celtium.vulkan.fractionalPowGuard.census` | `Boolean.getBoolean` | [ModGlslTranslator.java:2031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2031>) |
| `celtium.vulkan.fractionalPowGuard.fastSqrt` | `Boolean.getBoolean` | [ModGlslTranslator.java:2026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2026>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L58>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L56>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:17017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17017>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17020](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17020>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16999>) |
| `celtium.vulkan.frameRingSlotMiB` | `Integer.getInteger` | [FrameLinearRing.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/FrameLinearRing.java#L68>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19084>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L43>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L509>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4274>) |
| `celtium.vulkan.glDrawExempt` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13237>) |
| `celtium.vulkan.glDrawWindowCensus` | `Boolean.getBoolean` | [GlDrawWindowCensus.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L56>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1942](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1942>) |
| `celtium.vulkan.glsmPackState` | `System.getProperty` | [GlsmPackState.java:55](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmPackState.java#L55>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3045>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3085>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:3047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3047>), [VulkanTerrainGpuBackend.java:3063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3063>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3101>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3095](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3095>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3755>) |
| `celtium.vulkan.groupDecayCensus` | `Boolean.getBoolean` | [GroupDecayCensus.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L24>) |
| `celtium.vulkan.groupDecayCensusFrames` | `Integer.getInteger` | [GroupDecayCensus.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L26>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L285>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3747](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3747>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1585>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3616>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L94>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3118>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3128>) |
| `celtium.vulkan.hudCensusFrames` | `Integer.getInteger` | [HudCensus.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L32>) |
| `celtium.vulkan.hudCensusMinFrame` | `Long.getLong` | [HudCensus.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L33>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3144>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3147>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1836](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1836>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3736>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3738>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3740](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3740>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L74>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:85](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L85>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L91>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4136>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L637>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4214>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2735](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2735>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4593>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2955](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2955>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [CopyReadSide.java:773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L773>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:6636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6636>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3031>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6373>), [CeltiumGllessHint.java:1832](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1832>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L115>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19247>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19296>) |
| `celtium.vulkan.meshUploadBudgetBytes` | `System.getProperty` | [MeshUploadBudget.java:71](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L71>) |
| `celtium.vulkan.meshUploadBudgetMiB` | `Long.getLong` | [MeshUploadBudget.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L67>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L300>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4667>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L144>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L224>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L168>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L180>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L298>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4533>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L108>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1877>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2081>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:1103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1103>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:2082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L2082>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L636>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:20341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20341>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:20328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20328>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19435>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L92>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19321>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L201>) |
| `celtium.vulkan.nullRenderer` | `Boolean.getBoolean` | [NullRenderer.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L28>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packShadowCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L428>) |
| `celtium.vulkan.packShadowRebuildBudget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L408>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [VulkanTerrainGpuBackend.java:399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L399>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17028>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17095](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17095>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:311](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L311>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:96](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L96>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:3658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3658>) |
| `celtium.vulkan.pipelineCreateCensus` | `Boolean.getBoolean` | [PipelineCreateCensus.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L51>) |
| `celtium.vulkan.pipelineCreateCensusSlowMs` | `Long.getLong` | [PipelineCreateCensus.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L54>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L466>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3727](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3727>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L291>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L308>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14741>) |
| `celtium.vulkan.presentDrainFinishGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13267>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17082>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L92>), [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>), [VulkanTerrainGpuBackend.java:4241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4241>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L363>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L507>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17075>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17085>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17088>) |
| `celtium.vulkan.presentUnbindFlushGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13250>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L511>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3559](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3559>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19079>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L57>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12311](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12311>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17064>) |
| `celtium.vulkan.recordsDedicatedPool` | `System.getProperty` | [InstanceDataStore.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java#L342>) |
| `celtium.vulkan.recordsRingSlotMiB` | `Integer.getInteger` | [RecordsRing.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/RecordsRing.java#L60>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2958>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4271>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4272>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L45>) |
| `celtium.vulkan.retainedIndexFlushExempt` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4264>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:15273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15273>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2691>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4800>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5506>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4224>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3561>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17058](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17058>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L817>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:853](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L853>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L895>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L897>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L869>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L851>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L842>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L834>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19256>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [GlsmFrontend.java:5651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5651>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [GlsmFrontend.java:9249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9249>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L250>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L228>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:3631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3631>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:7854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7854>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>), [GlsmFrontend.java:284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L284>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L361>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:7845](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7845>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L417>) |
| `celtium.vulkan.terrainPreparePublishWalkOnce` | `Boolean.getBoolean` | [PostChainBridge.java:762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L762>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3882](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3882>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3885>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:87](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L87>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3789>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3869>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L130>), [VulkanTerrainGpuBackend.java:2960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2960>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16991](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16991>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2701>) |
| `celtium.vulkan.uploadBudgetMiB` | `Long.getLong` | [VulkanContext.java:1057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1057>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:19411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19411>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L592>) |
| `celtium.vulkan.upstreamArrivalCensus` | `Boolean.getBoolean` | [UpstreamArrivalCensus.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L28>) |
| `celtium.vulkan.upstreamArrivalCensusFrames` | `Integer.getInteger` | [UpstreamArrivalCensus.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L30>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17704](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17704>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L218>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3648>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3209>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:3212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3212>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2298>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L803>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L27>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:11103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11103>) |
| `CELTIUM_ACCEPT_FLUSH` | [FramePassTimings.java:897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L897>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:236](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L236>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:3629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3629>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4185](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4185>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:5910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5910>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:5586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5586>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1644>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L814>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L632>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L847>), [VulkanArena.java:856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L856>), [VulkanArena.java:867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L867>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:1108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1108>), [VulkanTerrainPipeline.java:1149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1149>), [VulkanTerrainPipeline.java:1169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1169>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L476>) |
| `CELTIUM_BACKEND_CALLS` | [BackendCallCounters.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendCallCounters.java#L154>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:17526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17526>) |
| `CELTIUM_BATCH_ROUTE` | [VulkanTerrainGpuBackend.java:22814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22814>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:9832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9832>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:8477](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8477>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:8490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8490>) |
| `CELTIUM_BLEND_CENSUS` | [GlsmFrontend.java:1063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1063>) |
| `CELTIUM_BLIT_DEPTH` | [GlsmFrontend.java:7842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7842>) |
| `CELTIUM_BLIT_UNSERVED` | [GlsmFrontend.java:7784](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7784>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:250](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L250>) |
| `CELTIUM_BORROW_BYPASS` | [PresentSwapchain.java:367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L367>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:13090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13090>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:13132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13132>) |
| `CELTIUM_BOUND_TARGET_CENSUS` | [VulkanTerrainGpuBackend.java:3820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3820>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:14003](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14003>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:5741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5741>), [VulkanContext.java:6144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L6144>) |
| `CELTIUM_CALL_LISTS_REFUSED` | [GlsmFrontend.java:9121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9121>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURE` | [VulkanTerrainGpuBackend.java:21225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21225>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1658>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7897](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7897>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1679>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6624](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6624>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:4614](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4614>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [CopyReadSide.java:593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L593>), [CopyReadSide.java:623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L623>), [PostChainBridge.java:1533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1533>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L343>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L374>) |
| `CELTIUM_CHAIN_INPUT_FLUSH_FAILED` | [VulkanTerrainGpuBackend.java:1745](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1745>) |
| `CELTIUM_CHAIN_INPUT_PATCH` | [ModPrograms.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L900>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:15659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15659>) |
| `CELTIUM_CHAIN_PASS` | [VulkanTerrainGpuBackend.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L192>), [VulkanTerrainGpuBackend.java:214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L214>), [VulkanTerrainGpuBackend.java:267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L267>), [VulkanTerrainGpuBackend.java:303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L303>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:183](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L183>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13633](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13633>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:5574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5574>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:18642](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18642>) |
| `CELTIUM_CLIENT_ARRAY` | [GlsmFrontend.java:9360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9360>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [GlsmFrontend.java:9022](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9022>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:5201](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5201>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:189](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L189>), [ForgeCloudCellsMixin.java:296](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L296>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3259](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3259>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:429](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L429>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:531](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L531>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L47>), [NativeFramebufferCompositor.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L97>), [WorldVertexBufferUploaderMixin.java:574](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L574>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L452>) |
| `CELTIUM_COPY_IMAGE` | [GlsmFrontend.java:7701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7701>), [GlsmFrontend.java:7738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7738>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1680>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1669>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:19877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19877>), [VulkanTerrainGpuBackend.java:19879](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19879>), [VulkanTerrainGpuBackend.java:19881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19881>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:19906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19906>), [VulkanTerrainGpuBackend.java:19908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19908>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [PresentSwapchain.java:675](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L675>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:19856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19856>), [VulkanTerrainGpuBackend.java:19859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19859>), [VulkanTerrainGpuBackend.java:19861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19861>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:19914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19914>), [VulkanTerrainGpuBackend.java:19917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19917>), [VulkanTerrainGpuBackend.java:19920](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19920>), [VulkanTerrainGpuBackend.java:19922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19922>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:19834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19834>), [VulkanTerrainGpuBackend.java:19837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19837>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:3600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3600>) |
| `CELTIUM_DELETE_SETTLED_SLOW` | [VulkanTerrainGpuBackend.java:17489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17489>) |
| `CELTIUM_DEPTH_COPY` | [CopyReadSide.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L293>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [GlsmFrontend.java:1532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1532>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [VulkanTerrainGpuBackend.java:17908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17908>) |
| `CELTIUM_DEPTH_NOTE` | [VulkanTerrainGpuBackend.java:7062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7062>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTextureRecordsPipeline.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L809>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5503>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockRecordsPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockRecordsPipeline.java#L60>), [VulkanImmediateItemRecordsPipeline.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L80>), [VulkanImmediateParticleRecordsPipeline.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticleRecordsPipeline.java#L67>), [VulkanImmediateTextureRecordsPipeline.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L68>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:4327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4327>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:4655](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4655>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:4863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4863>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:23428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23428>), [VulkanTerrainGpuBackend.java:23516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23516>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4917](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4917>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4934](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4934>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:5014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5014>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7785](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7785>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7779](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7779>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5025](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5025>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8961](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8961>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5080](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5080>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5036](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5036>), [ValidationClient.java:8557](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8557>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:5776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5776>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L375>) |
| `CELTIUM_DISPLAY_LISTS` | [GlsmFrontend.java:1088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1088>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L308>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L133>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L431>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L403>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L349>) |
| `CELTIUM_DRAWARRAYS` | [GlDrawCensus.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L118>), [GlsmFrontend.java:1066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1066>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [GlsmFrontend.java:8598](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8598>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [GlsmFrontend.java:9460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9460>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [GlsmFrontend.java:8653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8653>) |
| `CELTIUM_DRAWELEMENTS` | [GlDrawCensus.java:205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L205>), [GlsmFrontend.java:1069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1069>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:19943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19943>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:22742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22742>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:23096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23096>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:4470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4470>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L626>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:4091](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4091>) |
| `CELTIUM_EFFECT_PAIR_SPAWNED` | [ValidationClient.java:3955](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3955>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L174>) |
| `CELTIUM_END_PORTAL_LEFTOVER` | [EndPortalProducer.java:74](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L74>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3896](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3896>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4973](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4973>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4983](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4983>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:11147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11147>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11155>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L794>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L680>), [VulkanTextureRegistry.java:865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L865>) |
| `CELTIUM_EXT_ANSWER` | [GlStateQueries.java:186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L186>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:896](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L896>) |
| `CELTIUM_F3_EVENT` | [CeltiumF3Overlay.java:90](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L90>) |
| `CELTIUM_F3_SECTION` | [CeltiumF3Overlay.java:122](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L122>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeltiumF3Overlay.java:78](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L78>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:156](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L156>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:8074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8074>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [VulkanTerrainGpuBackend.java:8379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8379>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:8055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8055>), [VulkanTerrainGpuBackend.java:8126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8126>), [VulkanTerrainGpuBackend.java:8136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8136>), [VulkanTerrainGpuBackend.java:8157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8157>), [VulkanTerrainGpuBackend.java:8182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8182>), [VulkanTerrainGpuBackend.java:8324](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8324>), [VulkanTerrainGpuBackend.java:8346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8346>), [VulkanTerrainGpuBackend.java:8365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8365>), [VulkanTerrainGpuBackend.java:8422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8422>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:8099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8099>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:8193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8193>), [VulkanTerrainGpuBackend.java:8434](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8434>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:23419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23419>), [VulkanTerrainGpuBackend.java:23455](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23455>), [VulkanTerrainGpuBackend.java:23508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23508>), [VulkanTerrainGpuBackend.java:23540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23540>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7324](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7324>), [ValidationClient.java:7338](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7338>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4637](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4637>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:2180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2180>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:2140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2140>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:12283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12283>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:17203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17203>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:17215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17215>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:9885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9885>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:9919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9919>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:9908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9908>) |
| `CELTIUM_FN_CENSUS` | [GlsmFrontend.java:1078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1078>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:5192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5192>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L239>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L547>) |
| `CELTIUM_FONT_FLUSH_REENTRY` | [FontBatch.java:863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L863>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1322](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1322>), [CeltiumFontGlyphCapture.java:1452](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1452>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:9212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9212>), [VulkanTerrainGpuBackend.java:9214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9214>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1295](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1295>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:899](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L899>), [CeltiumFontGlyphCapture.java:1434](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1434>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1534](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1534>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L989>), [FontBatch.java:1029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L1029>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:9186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9186>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:9107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9107>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:457](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L457>), [CeltiumFontGlyphCapture.java:461](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L461>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:9197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9197>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:920](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L920>) |
| `CELTIUM_FRAMEBUFFER_HINT_FAILED` | [VulkanTerrainGpuBackend.java:1760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1760>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:13769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13769>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:8912](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8912>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L686>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L739>), [FrameBudgetTimings.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L743>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L150>) |
| `CELTIUM_FRAME_FORMAT_MOVED` | [VulkanContext.java:1319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1319>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L890>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L117>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L176>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [CopyReadSide.java:1081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1081>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L511>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L594>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L501>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L562>) |
| `CELTIUM_FREE_MEMORY_SLOW` | [VulkanContext.java:3565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3565>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:18429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18429>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:18556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18556>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:163](<../forge122/src/main/java/org/soutalab/celtium/forge/light/MinecraftDynamicLightService.java#L163>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1363>) |
| `CELTIUM_GLDRAW_WINDOW_CENSUS` | [GlDrawWindowCensus.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L152>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5969>) |
| `CELTIUM_GLSM_HARVEST` | [VulkanModProgramPipeline.java:1446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L1446>) |
| `CELTIUM_GLSM_LINK` | [ModProgramRegistry.java:461](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L461>) |
| `CELTIUM_GLSTUB` | [GlsmFrontend.java:1014](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1014>), [GlsmFrontend.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1017>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2977](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2977>), [CeltiumGllessHint.java:2981](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2981>), [CeltiumGllessNativeTransformer.java:154](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L154>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:53](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L53>), [CeleritasVintageMixinPlugin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L55>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:6289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6289>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:6317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6317>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:631](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L631>), [CeltiumFontGlyphCapture.java:719](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L719>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1119](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1119>) |
| `CELTIUM_GL_CALLERS` | [CeltiumGllessHint.java:1401](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1401>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2940](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2940>), [CeltiumGllessHint.java:2947](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2947>), [CeltiumGllessHint.java:2953](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2953>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [GlsmFrontend.java:4712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4712>) |
| `CELTIUM_GL_DISPATCH_FOREIGN` | [GlsmFrontend.java:4774](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4774>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:754](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L754>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:784](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L784>) |
| `CELTIUM_GL_SURFACE_NOOP` | [CeltiumGllessNativeTransformer.java:823](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L823>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:23972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23972>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:14144](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L14144>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:5863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5863>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1556](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1556>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:16537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16537>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:22807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22807>) |
| `CELTIUM_GROUP_DECAY` | [GroupDecayCensus.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L82>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:1051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1051>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:23015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23015>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:23057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23057>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10531](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10531>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1396](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1396>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1873](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1873>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1901>) |
| `CELTIUM_HUD_CENSUS` | [HudCensus.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L237>), [HudCensus.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L240>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:7631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7631>) |
| `CELTIUM_HUD_P0` | [VulkanTerrainGpuBackend.java:7695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7695>) |
| `CELTIUM_HUD_P0_SUMMARY` | [VulkanTerrainGpuBackend.java:7718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7718>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4120](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4120>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5191](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5191>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_BIND` | [GlStateQueries.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L195>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3177>) |
| `CELTIUM_IMGUNIT_ANSWER` | [GlStateQueries.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L190>), [CeltiumGllessHint.java:901](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L901>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [GlDrawCensus.java:66](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java#L66>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemRecordsPipeline.java:619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L619>), [VulkanImmediateTextureRecordsPipeline.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L1017>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:16425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16425>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L226>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:10147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10147>) |
| `CELTIUM_INDEXED_ENABLE` | [GlsmFrontend.java:6510](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6510>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:5632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5632>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:22167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22167>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:336](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L336>) |
| `CELTIUM_K6` | [WorldVertexBufferUploaderMixin.java:467](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L467>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2938>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:1085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1085>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4646>), [VulkanGlTexture.java:4649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4649>), [VulkanGlTexture.java:4651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4651>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [GlsmFrontend.java:3085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3085>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [GlsmFrontend.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3209>) |
| `CELTIUM_LEGACY_LIST_RUN` | [GlsmFrontend.java:3162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3162>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [GlsmFrontend.java:2796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2796>) |
| `CELTIUM_LEGACY_REFUSED` | [GlsmFrontend.java:2787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2787>), [GlsmFrontend.java:2791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2791>), [GlsmFrontend.java:2965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L2965>), [GlsmFrontend.java:3090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3090>), [GlsmFrontend.java:3099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3099>), [GlsmFrontend.java:3106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3106>) |
| `CELTIUM_LEGACY_RUN` | [GlsmFrontend.java:3275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L3275>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1281>), [VulkanTextureRegistry.java:2442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2442>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:9888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9888>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:14312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14312>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:14302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14302>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:14391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14391>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:14422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14422>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:14454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14454>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:9855](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9855>) |
| `CELTIUM_LIST_ABANDONED` | [GlsmFrontend.java:5445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5445>) |
| `CELTIUM_LIST_COMPILED` | [GlsmFrontend.java:5505](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5505>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:5322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5322>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9642](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9642>) |
| `CELTIUM_LOADING_SCREEN` | [NativeLoadingScreen.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeLoadingScreen.java#L226>), [CeltiumLoadingProgress.java:116](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L116>), [CeltiumLoadingProgress.java:126](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L126>), [CeltiumLoadingProgress.java:147](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L147>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L163>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L497>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3980](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3980>), [ValidationClient.java:4000](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4000>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10364](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10364>), [ValidationClient.java:10374](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10374>), [ValidationClient.java:10380](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10380>) |
| `CELTIUM_MAIN_CLEAR` | [GlsmFrontend.java:4289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L4289>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/BufferStorageRecord.java#L159>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:326](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L326>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:344](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L344>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:16409](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16409>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1113](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1113>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:6404](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6404>) |
| `CELTIUM_MEMORY_PROFILE` | [VulkanContext.java:4857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4857>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:22175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22175>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:13071](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13071>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:22827](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22827>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1859>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2183>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2354>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L206>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:22186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22186>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3730>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3744>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:1130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1130>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1993>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2298>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2437>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3296>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:2213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2213>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1763](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1763>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1344>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L384>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L439>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L371>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L74>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L523>), [ModelReplay.java:559](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L559>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L270>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L595>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L366>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L344>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L292>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1362>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1322>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L159>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L98>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L105>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L467>) |
| `CELTIUM_MODEL_PHASE` | [PatchGroups.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L231>), [ModelPhaseTrace.java:168](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L168>), [ModelPhaseTrace.java:170](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L170>), [ModelPhaseTrace.java:172](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L172>), [ModelPhaseTrace.java:174](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L174>), [ModelPhaseTrace.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L176>), [ModelPhaseTrace.java:178](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L178>), [ModelPhaseTrace.java:180](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L180>), [ModelPhaseTrace.java:182](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L182>), [ModelPhaseTrace.java:184](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L184>), [ModelPhaseTrace.java:186](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L186>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L299>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L481>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L474>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L494>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L147>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L368>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L328>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L340>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L282>) |
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:22551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22551>) |
| `CELTIUM_MOD_PROGRAM_FALLBACK` | [VulkanTerrainGpuBackend.java:11135](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11135>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:450](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L450>) |
| `CELTIUM_MOD_PROGRAM_SUPPRESSED` | [ModPrograms.java:999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L999>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:927](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L927>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L782>) |
| `CELTIUM_MOD_SAMPLER_DEPTH_ATTACH` | [ModPrograms.java:969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L969>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:409](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L409>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModShaderCompiler.java#L152>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13833](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13833>) |
| `CELTIUM_MULT_MATRIX` | [GlsmFrontend.java:6118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6118>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:3986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3986>) |
| `CELTIUM_NATIVE_DRAW_REFUSED` | [CeltiumNativeDraw.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java#L54>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:17253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17253>), [GlsmFrontend.java:1055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1055>), [GlsmFrontend.java:5796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5796>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:17255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17255>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:368](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L368>), [WorldVertexBufferUploaderMixin.java:381](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L381>) |
| `CELTIUM_NULL_RENDERER` | [NullRenderer.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L64>), [NullRenderer.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L73>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:8676](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8676>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:334](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L334>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:442](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L442>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:6110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6110>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L638>), [FrameBudgetTimings.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L653>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:395](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L395>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PACK_UNIFORM` | [PackUniformCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L375>) |
| `CELTIUM_PACK_UNIFORM_LANES` | [PackUniformCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L403>) |
| `CELTIUM_PACK_UNIFORM_PIPELINE` | [PackUniformCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L358>) |
| `CELTIUM_PACK_UNIFORM_PROGRAM` | [PackUniformCensus.java:304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PackUniformCensus.java#L304>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:18875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18875>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:1245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1245>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:15990](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15990>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:1118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1118>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [VulkanTerrainGpuBackend.java:15713](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15713>) |
| `CELTIUM_PARTICLE_UV` | [GlsmFrontend.java:7501](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7501>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L83>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L86>) |
| `CELTIUM_PATCH_ESSENTIAL` | [PatchErrorRecorder.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L98>) |
| `CELTIUM_PATCH_IGNORED` | [PatchLedger.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L177>), [PatchLedger.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L181>), [PatchLedger.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L184>), [PatchLedger.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L201>), [PatchLedger.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L207>) |
| `CELTIUM_PATCH_QUARANTINED` | [PatchErrorRecorder.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L110>) |
| `CELTIUM_PATCH_RECORD_FAILED` | [PatchErrorRecorder.java:114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L114>) |
| `CELTIUM_PATCH_REGISTERED` | [CeleritasVintageMixinPlugin.java:211](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L211>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:2016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2016>) |
| `CELTIUM_PATCH_SKIPPED` | [PatchLedger.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L269>), [PatchLedger.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L272>), [PatchLedger.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L275>) |
| `CELTIUM_PATCH_TABLE` | [PatchLedger.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L305>), [CeleritasVintageMixinPlugin.java:166](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L166>), [CeleritasVintageMixinPlugin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L173>), [CeleritasVintageMixinPlugin.java:219](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L219>) |
| `CELTIUM_PBO_REFUSED` | [GlTextureCaptures.java:606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L606>), [GlsmFrontend.java:7910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7910>) |
| `CELTIUM_PBO_UPLOAD` | [GlTextureCaptures.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlTextureCaptures.java#L403>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9761>), [ValidationClient.java:9905](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9905>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:3678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3678>), [VulkanContext.java:3697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3697>), [VulkanContext.java:3733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3733>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [VulkanContext.java:3424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3424>) |
| `CELTIUM_PIPELINE_CREATE_CENSUS` | [PipelineCreateCensus.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L175>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5476>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:3433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3433>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:567](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L567>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:548](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L548>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:538](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L538>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L417>), [VulkanPostChain.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L426>), [VulkanPostChain.java:438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L438>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L547>), [VulkanPostChain.java:748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L748>), [VulkanPostChain.java:805](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L805>), [VulkanPostChain.java:835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L835>), [VulkanPostChain.java:839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L839>), [VulkanPostChain.java:1433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1433>), [VulkanPostChain.java:1438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1438>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6877>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1545](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1545>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L929>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:1102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1102>) |
| `CELTIUM_POW_GUARD_CENSUS` | [ModGlslTranslator.java:2079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2079>) |
| `CELTIUM_PRESENT` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [PresentSwapchain.java:744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L744>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [PresentSwapchain.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L103>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:12493](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12493>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5310](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5310>) |
| `CELTIUM_PRESENT_DIAGNOSTICS_FAILED` | [VulkanSwapchain.java:2044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2044>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L412>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L722>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3128>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L192>) |
| `CELTIUM_PRESENT_PATCH` | [PresentSwapchain.java:866](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L866>) |
| `CELTIUM_PRESENT_SLOW` | [PresentSwapchain.java:713](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L713>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1464>) |
| `CELTIUM_PREWARM_TEARDOWN` | [VulkanContext.java:3791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3791>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [GlStateQueries.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L250>) |
| `CELTIUM_PROGRAM_DRAW` | [GlsmFrontend.java:8443](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L8443>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:16368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16368>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1724](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1724>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [VulkanTerrainGpuBackend.java:7199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7199>) |
| `CELTIUM_RAW_BIND_REGISTER` | [VulkanTerrainGpuBackend.java:7223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7223>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [VulkanTerrainGpuBackend.java:7289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7289>) |
| `CELTIUM_RAW_FBO` | [GlsmFrontend.java:6769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6769>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:7896](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7896>), [VulkanTerrainGpuBackend.java:7923](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7923>) |
| `CELTIUM_RAW_FBO_DEPTH` | [GlsmFrontend.java:6738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6738>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:35](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L35>), [RawFboIdentityProbe.java:259](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L259>) |
| `CELTIUM_RAW_FBO_LEVEL` | [GlsmFrontend.java:6726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6726>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L808>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L274>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1710>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1223>), [GlsmFrontend.java:7921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7921>), [GlsmFrontend.java:7930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7930>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [GlsmFrontend.java:7902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7902>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1215>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1083>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1070>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:23990](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L23990>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:277](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L277>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTextureRecordsPipeline.java:781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L781>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:406](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L406>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4148>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:13531](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13531>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9063](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9063>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9210](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9210>), [ValidationClient.java:9211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9211>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9152](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9152>), [ValidationClient.java:9186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9186>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9313](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9313>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9213>), [ValidationClient.java:9215](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9215>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5163](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5163>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5168](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5168>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3280>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2867>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2930>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L212>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:11166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11166>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:11178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11178>) |
| `CELTIUM_SHADOW_FRUSTUM_INIT_CACHE` | [DemonicaShadowFrustumInitCacheMixin.java:100](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L100>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13879](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13879>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:15490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15490>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:18017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18017>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1447>), [VulkanGlTexture.java:1487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1487>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L664>), [VulkanGlTexture.java:756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L756>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L716>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L789>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L932>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:1218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1218>) |
| `CELTIUM_SKY_DEGRADED` | [GlsmFrontend.java:9563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L9563>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:22594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L22594>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:8800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8800>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:8787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8787>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:8742](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8742>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:8811](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8811>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [PresentSwapchain.java:916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L916>) |
| `CELTIUM_SLOTDUMP` | [VulkanTextureRegistry.java:434](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L434>), [VulkanTextureRegistry.java:441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L441>), [VulkanTextureRegistry.java:534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L534>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:20656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20656>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:13791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13791>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:13904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13904>), [VulkanTerrainGpuBackend.java:13909](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13909>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9550](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9550>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:13817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13817>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:13971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13971>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3567>), [VulkanGlTexture.java:3684](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3684>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L197>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4528](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4528>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:7968](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7968>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2913>) |
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
| `CELTIUM_SWAPPOINT_EXCEPTION` | [VulkanTerrainGpuBackend.java:2014](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2014>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L269>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:7748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7748>) |
| `CELTIUM_TEARDOWN` | [VulkanContext.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3160>), [VulkanContext.java:3244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3244>), [VulkanTerrainGpuBackend.java:17678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17678>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [VulkanTerrainGpuBackend.java:17958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17958>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [VulkanTerrainGpuBackend.java:18148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18148>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [VulkanTerrainGpuBackend.java:17926](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17926>), [VulkanTerrainGpuBackend.java:17943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17943>), [VulkanTerrainGpuBackend.java:17951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17951>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1775>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L169>) |
| `CELTIUM_TEXTURE_DELETED` | [GlsmFrontend.java:6306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L6306>) |
| `CELTIUM_TEXTURE_DIAG` | [CopyReadSide.java:1291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1291>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L998>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [GlsmFrontend.java:7027](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7027>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:944](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L944>) |
| `CELTIUM_TEXUNIT_ANSWER` | [GlStateQueries.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlStateQueries.java#L174>) |
| `CELTIUM_TEX_COPY` | [GlsmFrontend.java:7643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L7643>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [GlsmFrontend.java:1075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1075>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:1032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1032>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L361>), [TickTimings.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L376>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L365>), [TickTimings.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L419>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1398](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1398>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L957>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1099>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [GlsmFrontend.java:5677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5677>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [GlsmFrontend.java:1056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L1056>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:10031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10031>), [WorldVertexBufferUploaderMixin.java:354](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L354>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:10032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10032>), [WorldVertexBufferUploaderMixin.java:798](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L798>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:558](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L558>) |
| `CELTIUM_UNSUPPORTED_INSTANCED` | [CeltiumGllessHint.java:1517](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1517>), [CeltiumGllessNativeTransformer.java:832](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L832>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4349>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:5407](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5407>) |
| `CELTIUM_UPSTREAM` | [UpstreamArrivalCensus.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L142>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:3837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3837>), [VulkanContext.java:3846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3846>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeltiumF3Overlay.java:55](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L55>) |
| `CELTIUM_VERTEXPOINTER` | [GlsmFrontend.java:5565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java#L5565>) |
| `CELTIUM_VULKAN` | [CeltiumPackCompiler.java:340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/shader/compiler/CeltiumPackCompiler.java#L340>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:2483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2483>), [VulkanContext.java:2643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2643>), [VulkanContext.java:3069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3069>), [VulkanContext.java:3081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3081>), [VulkanContext.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3128>), [VulkanContext.java:3136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3136>), [VulkanContext.java:3184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3184>), [VulkanContext.java:3189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3189>), [VulkanContext.java:3205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3205>), [VulkanContext.java:3213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3213>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:3094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3094>), [VulkanContext.java:3117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3117>), [VulkanContext.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3221>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4621](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4621>), [ValidationClient.java:4625](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4625>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:1067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1067>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1306>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:1080](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1080>), [VulkanTerrainGpuBackend.java:15950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15950>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1306](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1306>), [CeltiumGllessHint.java:1404](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1404>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1304>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1302>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:13701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13701>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1342>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:562](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L562>), [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>), [MinecraftMixin.java:588](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L588>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:20034](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20034>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9276](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9276>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:9332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9332>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1374>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:1284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1284>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:1264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1264>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:1269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1269>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:1265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1265>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:5246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5246>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9037](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9037>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2673>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemRecordsPipeline.java:421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L421>), [VulkanImmediateTextureRecordsPipeline.java:830](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L830>) |
