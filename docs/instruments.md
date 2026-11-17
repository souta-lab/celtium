# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **543 個**／文字列内マーカー **513 個**。
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
| [celtium.debug](#group-celtium-debug) | 50 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.dynamicLights](#group-celtium-dynamicLights) | 1 |
| [celtium.entityLightCache](#group-celtium-entityLightCache) | 1 |
| [celtium.f3](#group-celtium-f3) | 2 |
| [celtium.font](#group-celtium-font) | 12 |
| [celtium.fontAppend](#group-celtium-fontAppend) | 1 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 2 |
| [celtium.fontGlyph](#group-celtium-fontGlyph) | 1 |
| [celtium.forceIdentityTexmat](#group-celtium-forceIdentityTexmat) | 1 |
| [celtium.gless](#group-celtium-gless) | 1 |
| [celtium.glless](#group-celtium-glless) | 2 |
| [celtium.glstub](#group-celtium-glstub) | 10 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.native](#group-celtium-native) | 4 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 5 |
| [celtium.validation](#group-celtium-validation) | 159 |
| [celtium.vulkan](#group-celtium-vulkan) | 271 |
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
| `celtium.allowCompetitors` | `Boolean.getBoolean` | [CeltiumCompetitors.java:52](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java#L52>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L345>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12699>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17083>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17084>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:17082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17082>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:596](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L596>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16566>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:483](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L483>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L199>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L358>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:17087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17087>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:17068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17068>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16559](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16559>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3378>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3405>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2288>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L330>), [VulkanSwapchain.java:1451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1451>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:266](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L266>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L272>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L268>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L270>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L262>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:5634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5634>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16561>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16591>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16592>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [CeltiumGlStub.java:2883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2883>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:479](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L479>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:483](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L483>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L23>) |
| `celtium.debug.markTrace` | `Boolean.getBoolean` | [RenderGlobalMixin.java:317](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L317>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L334>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L445>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L98>), [VulkanModelInstances.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L179>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9591>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:106](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L106>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16651>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3428>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16557>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3392>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:258](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L258>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L662>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3458>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3397>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3451>) |

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
| `celtium.dynamicLights` | `System.getProperty` | [MinecraftDynamicLightService.java:40](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/light/MinecraftDynamicLightService.java#L40>) |

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
| `celtium.font.batching` | `System.getProperty` | [FontBatch.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L251>) |
| `celtium.font.colourWitness` | `System.getProperty` | [FontBatch.java:284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L284>) |
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:195](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L195>) |
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:340](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L340>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:207](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L207>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L248>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:217](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L217>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L260>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:238](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L238>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:262](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L262>) |
| `celtium.font.timings` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:357](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L357>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:229](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L229>) |

<a id="group-celtium-fontAppend"></a>
## `celtium.fontAppend.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L332>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:49](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L49>) |
| `celtium.fontAtlas.dump` | `System.getProperty` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |

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

<a id="group-celtium-gless"></a>
## `celtium.gless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.gless.legacyImmediate` | `System.getProperty` | [CeltiumGllessNativeTransformer.java:586](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L586>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:589](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L589>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1624](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1624>), [CeltiumGllessNativeTransformer.java:409](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L409>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1386>) |
| `celtium.glstub.attribMasks` | `System.getProperty` | [CeltiumGlStub.java:2617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2617>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:11195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11195>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:5105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5105>) |
| `celtium.glstub.legacyCallLayout` | `System.getProperty` | [CeltiumGlStub.java:2927](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2927>) |
| `celtium.glstub.legacyCallState` | `System.getProperty` | [CeltiumGlStub.java:2913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2913>) |
| `celtium.glstub.legacyListCompile` | `System.getProperty` | [CeltiumGlStub.java:2900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2900>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:11387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11387>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:5541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5541>), [CeltiumGlStub.java:11057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11057>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:5536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5536>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2888>) |

<a id="group-celtium-native"></a>
## `celtium.native.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.native.cloudCells` | `System.getProperty` | [ForgeCloudCellsMixin.java:44](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L44>) |
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L35>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3186>) |
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
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:781](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L781>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:769](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L769>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:788](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L788>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:9575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9575>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:67](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L67>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:52](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L52>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:330](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L330>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |
| `celtium.terrain.queueHiddenPending` | `Boolean.getBoolean` | [RenderSectionManager.java:80](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L80>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [MinecraftMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L290>), [ValidationClient.java:3467](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3467>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1820](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1820>), [ValidationClient.java:3886](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3886>), [ValidationClient.java:11511](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11511>), [ValidationClient.java:12776](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12776>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1814](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1814>), [ValidationClient.java:2014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2014>), [ValidationClient.java:11504](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11504>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:2040](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2040>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:2028](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2028>), [ValidationClient.java:11505](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11505>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:2047](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2047>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:7124](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7124>), [ValidationClient.java:7138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7138>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:7131](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7131>) |
| `celtium.validation.cameraEventProbe` | `Boolean.getBoolean` | [ValidationClient.java:3482](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3482>) |
| `celtium.validation.cameraEventStartTick` | `Integer.getInteger` | [CameraEventProbe.java:10](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L10>) |
| `celtium.validation.cameraEventYaw` | `System.getProperty` | [CameraEventProbe.java:11](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L11>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1396](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1396>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1162](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1162>) |
| `celtium.validation.cameraSweepDegreesPerSecond` | `System.getProperty` | [ValidationClient.java:201](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L201>) |
| `celtium.validation.cameraSweepFromTick` | `Integer.getInteger` | [ValidationClient.java:203](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L203>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1392](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1392>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1393](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1393>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1395](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1395>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1394](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1394>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L323>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:366](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L366>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:7413](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7413>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:363](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L363>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L352>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:375](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L375>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:340](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L340>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:305](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L305>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:336](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L336>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L884>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L548>), [ValidationClient.java:2395](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2395>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3470](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3470>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2499](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2499>) |
| `celtium.validation.creativeTab` | `Integer.getInteger` | [ValidationClient.java:6557](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6557>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:801](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L801>), [ValidationClient.java:1327](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1327>), [ValidationClient.java:11529](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11529>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:800](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L800>), [ValidationClient.java:1328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1328>), [ValidationClient.java:11530](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11530>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L808>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:810](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L810>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L958>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:943](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L943>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2393](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2393>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2564](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2564>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2632](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2632>), [ValidationClient.java:7205](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7205>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2391](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2391>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2436](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2436>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2883>), [LoadingScreenRendererMixin.java:45](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L45>), [ValidationClient.java:9109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9109>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:4136](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4136>), [ValidationClient.java:4325](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4325>), [ValidationClient.java:4352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4352>), [ValidationClient.java:7072](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7072>), [ValidationClient.java:9050](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9050>), [ValidationClient.java:9051](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9051>), [ValidationClient.java:9080](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9080>), [ValidationClient.java:9110](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9110>), [ValidationClient.java:9540](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9540>), [ValidationClient.java:9940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9940>), [ValidationClient.java:10153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10153>), [ValidationClient.java:10236](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10236>), [ValidationClient.java:11075](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11075>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:1074](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1074>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:7073](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7073>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:1084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1084>) |
| `celtium.validation.dynamicLightItem` | `Boolean.getBoolean` | [ValidationClient.java:3803](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3803>), [ValidationClient.java:3922](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3922>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:7231](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7231>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3772](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3772>) |
| `celtium.validation.entityType` | `System.getProperty` | [ValidationClient.java:1826](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1826>), [ValidationClient.java:1827](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1827>), [ValidationClient.java:3730](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3730>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:5295](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5295>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:7601](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7601>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:6979](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6979>) |
| `celtium.validation.flickerArmTick` | `Integer.getInteger` | [ValidationClient.java:4025](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4025>) |
| `celtium.validation.flightBlocksPerSecond` | `System.getProperty` | [ValidationClient.java:211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L211>) |
| `celtium.validation.flightFromTick` | `Integer.getInteger` | [ValidationClient.java:213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L213>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1271](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1271>) |
| `celtium.validation.framerate` | `Integer.getInteger` | [ValidationClient.java:4443](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4443>) |
| `celtium.validation.gameScreenshot` | `Boolean.getBoolean` | [ValidationClient.java:9491](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9491>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1426](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1426>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1419](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1419>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1421>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1428](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1428>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1434](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1434>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1436](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1436>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1431](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1431>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1424](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1424>) |
| `celtium.validation.glowAbTick` | `Integer.getInteger` | [ValidationClient.java:3960](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3960>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3941](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3941>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:7172](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7172>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1249](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1249>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1251](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1251>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:75](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L75>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:81](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L81>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1253>) |
| `celtium.validation.gvcA4Probe` | `Boolean.getBoolean` | [ValidationClient.java:1825](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1825>), [ValidationClient.java:3489](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3489>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:6401](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6401>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L877>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3931](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3931>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3828](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3828>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:1010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1010>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3920](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3920>), [ValidationClient.java:4041](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4041>), [ValidationClient.java:7204](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7204>), [ValidationClient.java:7405](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7405>), [ValidationClient.java:7486](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7486>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:1061](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1061>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:6748](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6748>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1857](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1857>), [ValidationClient.java:6759](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6759>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1851>), [ValidationClient.java:5866](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5866>), [ValidationClient.java:6679](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6679>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:6700](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6700>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3728](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3728>), [ValidationClient.java:3921](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3921>), [ValidationClient.java:4622](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4622>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:4241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4241>), [ValidationClient.java:7213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7213>), [ValidationClient.java:7214](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7214>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1863](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1863>), [ValidationClient.java:6806](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6806>) |
| `celtium.validation.litFogFrames` | `System.getProperty` | [ValidationClient.java:6835](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6835>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:12782](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12782>) |
| `celtium.validation.menuCaptureTicks` | `Integer.getInteger` | [ValidationClient.java:1122](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1122>) |
| `celtium.validation.menuFrameStats` | `Boolean.getBoolean` | [ValidationClient.java:6903](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6903>), [ValidationClient.java:7851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7851>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1110](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1110>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2599](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2599>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:936](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L936>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:7541](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7541>), [ValidationClient.java:7545](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7545>) |
| `celtium.validation.modelGlint` | `Boolean.getBoolean` | [ValidationClient.java:3753](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3753>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.opaqueCopyProbe` | `Boolean.getBoolean` | [RawFboIdentityProbe.java:135](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L135>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:7154](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7154>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:311](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L311>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:326](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L326>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:6381](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6381>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:7683](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7683>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:4482](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4482>) |
| `celtium.validation.probePixel` | `System.getProperty` | [ValidationClient.java:6302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6302>) |
| `celtium.validation.rawFboIdentityProbe` | `Boolean.getBoolean` | [ValidationClient.java:3485](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3485>), [ValidationClient.java:7392](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7392>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:6372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6372>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:4090](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4090>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:4073](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4073>), [ValidationClient.java:7467](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7467>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:4436](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4436>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3863](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3863>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:6986](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6986>), [ValidationClient.java:11633](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11633>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:1013](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1013>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:9098](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9098>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:1029](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1029>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1127>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:1042](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1042>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:8840](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8840>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:1014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1014>), [ValidationClient.java:7001](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7001>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:1040](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1040>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:991](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L991>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1148>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1964](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1964>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1293>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:1847](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1847>), [ValidationClient.java:6457](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6457>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2510](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2510>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:6788](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6788>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:6539](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6539>) |
| `celtium.validation.shadowStage` | `Boolean.getBoolean` | [ValidationClient.java:3686](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3686>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:4083](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4083>), [ValidationClient.java:7469](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7469>) |
| `celtium.validation.signTextAbTick` | `Integer.getInteger` | [ValidationClient.java:3987](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3987>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:6967](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6967>), [ValidationClient.java:7926](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7926>) |
| `celtium.validation.sparseTexture` | `Boolean.getBoolean` | [ValidationClient.java:2463](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2463>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:4623](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4623>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:7082](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7082>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2688](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2688>) |
| `celtium.validation.testFogEnd` | `System.getProperty` | [ValidationClient.java:6858](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6858>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2755](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2755>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:7219](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7219>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:4307](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4307>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:7577](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7577>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:10138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10138>), [ValidationClient.java:10432](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10432>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:7512](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7512>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:5671](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5671>), [ValidationClient.java:7877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7877>), [ValidationClient.java:7915](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7915>), [ValidationClient.java:7959](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7959>) |
| `celtium.validation.warmSpikeMs` | `System.getProperty` | [ValidationClient.java:5675](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5675>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:5672](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5672>), [ValidationClient.java:7878](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7878>), [ValidationClient.java:7916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7916>), [ValidationClient.java:7960](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7960>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:7015](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7015>), [ValidationClient.java:7147](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7147>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1813](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1813>), [ValidationClient.java:1965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1965>), [ValidationClient.java:11503](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11503>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:4744](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4744>) |
| `celtium.validation.worldSwitchMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:4756](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4756>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:84](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L84>) |
| `celtium.validation.worldType` | `System.getProperty` | [ValidationClient.java:4475](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4475>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18618](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18618>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L108>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3033>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2979](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2979>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L18>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>) |
| `celtium.vulkan.asyncPresent` | `System.getProperty` | [VulkanContext.java:1533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1533>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L485>) |
| `celtium.vulkan.batchAnimationUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4599>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L45>), [VulkanTerrainGpuBackend.java:19269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19269>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16682](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16682>) |
| `celtium.vulkan.batchTexturePatches` | `System.getProperty` | [VulkanGlTexture.java:4197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4197>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3582](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3582>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L46>), [VulkanContext.java:392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L392>), [VulkanTerrainGpuBackend.java:19270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19270>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateItemPipeline.java:402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L402>), [VulkanImmediateTexturePipeline.java:449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L449>) |
| `celtium.vulkan.brackets` | `Boolean.getBoolean` | [FramePassTimings.java:162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L162>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3767>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3372](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3372>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3379>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPairDiscardDepth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L231>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1729>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [CeltiumGlStub.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L680>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5604](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5604>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10360>) |
| `celtium.vulkan.colorClearTransfer` | `System.getProperty` | [GllessFrameTarget.java:1072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L1072>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L189>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2152>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L183>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3572](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3572>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L16>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2835>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2845](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2845>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2846>) |
| `celtium.vulkan.deferPatchRegions` | `System.getProperty` | [VulkanGlTexture.java:4235](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4235>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:896](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L896>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L32>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:19278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19278>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2663>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16837>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2666](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2666>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2649>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2652](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2652>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2820>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2822>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>), [ValidationClient.java:7536](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7536>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L140>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2644>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2646>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18727](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18727>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18722>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2387>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2875](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2875>), [ValidationClient.java:2994](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2994>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3599>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10280>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4274>) |
| `celtium.vulkan.fontBake` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2470>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3908>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L224>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:134](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L134>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:690](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L690>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L219>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L48>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L46>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:14437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14437>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14440>) |
| `celtium.vulkan.frameBudget` | `Boolean.getBoolean` | [FrameBudgetTimings.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L52>) |
| `celtium.vulkan.frameBudgetFrames` | `Integer.getInteger` | [FrameBudgetTimings.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L62>) |
| `celtium.vulkan.frameBudgetLines` | `Integer.getInteger` | [FrameBudgetTimings.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L72>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14419>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2887>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2881>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2886>) |
| `celtium.vulkan.frameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L462>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16383>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2878>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L363>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3369>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1568](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1568>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2536>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2576>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2538>), [VulkanTerrainGpuBackend.java:2554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2554>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2592>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2586>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3013>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L218>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3005>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1387>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2880>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:95](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L95>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2609>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2619>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1467>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2994](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2994>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2996>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2998>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3359>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L73>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:84](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L84>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L90>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3235](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3235>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L644>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3313>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3351>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L428>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2809>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2290>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4130>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2402>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18571>) |
| `celtium.vulkan.litFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4182>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5688>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2522>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:5450](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5450>), [CeltiumGllessHint.java:1603](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1603>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L108>) |
| `celtium.vulkan.mergeAcrossFormat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16623](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16623>) |
| `celtium.vulkan.mergeAcrossState` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16626>) |
| `celtium.vulkan.mergeAcrossTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16620](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16620>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16546](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16546>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16595>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:858](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L858>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L293>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3762>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L137>), [VulkanTerrainGpuBackend.java:2885](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2885>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L142>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L222>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L166>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L178>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L291>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3628>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L106>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1508>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:615](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L615>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:341](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L341>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:616](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L616>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L337>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18720>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18707>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16753>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelReplayFastPath` | `System.getProperty` | [ModelReplayFastPath.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L37>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L91>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L177>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16628>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L192>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L25>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairClearInPass` | `System.getProperty` | [GllessFrameTarget.java:2472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L2472>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14448](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14448>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14536>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2451>), [VulkanTerrainGpuBackend.java:14472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14472>) |
| `celtium.vulkan.passCounts` | `Boolean.getBoolean` | [FramePassTimings.java:566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L566>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L149>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L175>) |
| `celtium.vulkan.passSplitMicros` | `Boolean.getBoolean` | [FramePassTimings.java:736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L736>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L304>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L97>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2322>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L390>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2985](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2985>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.preflushInPass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11051>) |
| `celtium.vulkan.preflushWhenEmpty` | `System.getProperty` | [VulkanTerrainGpuBackend.java:11067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11067>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12264>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14523>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:93](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L93>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:3340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3340>) |
| `celtium.vulkan.presentInterval` | `Boolean.getBoolean` | [FrameBudgetTimings.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L190>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:217](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L217>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L361>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14516>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14526>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14529>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [FramePassTimings.java:692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L692>), [VulkanSwapchain.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L260>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3371>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L365>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2828>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16378>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L56>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10373](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10373>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L203>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14505](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14505>) |
| `celtium.vulkan.recordsPositionTwins` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L62>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2405>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3366>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3367>) |
| `celtium.vulkan.replayInputCache` | `System.getProperty` | [ModelReplayInputCache.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayInputCache.java#L69>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L47>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12796>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2151>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3895>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4579](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4579>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3323>) |
| `celtium.vulkan.skipImmediateRecord` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16639>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2830](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2830>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14499>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L401>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L437>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L479>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L481>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:453](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L453>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L435>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L426>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L418>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:426](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L426>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16555>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:6147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6147>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:11059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11059>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L248>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L91>), [VulkanSwapchain.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L47>), [VulkanTerrainGpuBackend.java:2889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2889>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6410>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:6401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6401>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L321>) |
| `celtium.vulkan.terrainToBoundTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3042](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3042>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L128>), [VulkanTerrainGpuBackend.java:2407](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2407>) |
| `celtium.vulkan.tickPercentileFrames` | `Integer.getInteger` | [FramePassTimings.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L219>) |
| `celtium.vulkan.tickPercentiles` | `Boolean.getBoolean` | [FramePassTimings.java:217](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L217>) |
| `celtium.vulkan.tickSplitFrames` | `Integer.getInteger` | [FramePassTimings.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L190>) |
| `celtium.vulkan.tickTimings` | `Boolean.getBoolean` | [TickTimings.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L29>) |
| `celtium.vulkan.tickTimingsFrom` | `Integer.getInteger` | [TickTimings.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L30>) |
| `celtium.vulkan.tickTimingsTo` | `Integer.getInteger` | [TickTimings.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L31>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14411>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2255>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13948>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13956](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13956>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16729>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:504](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L504>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [FramePassTimings.java:691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L691>), [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanDiagnostics.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L33>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:1018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1018>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14996>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L185>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L193>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2906>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2669>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2672>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1859>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L387>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L36>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L512>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:9240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9240>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2305>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3918](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3918>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:4987](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4987>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:110](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L110>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:4663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4663>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:170](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L170>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1141>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L460>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L499>), [VulkanArena.java:508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L508>), [VulkanArena.java:519](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L519>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:281](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L281>) |
| `CELTIUM_ASYNC_PRESENT` | [VulkanContext.java:1591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1591>), [VulkanContext.java:1599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1599>), [VulkanContext.java:1654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1654>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:842](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L842>), [VulkanTerrainPipeline.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L883>), [VulkanTerrainPipeline.java:903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L903>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L370>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:14841](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14841>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:198](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L198>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:8037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8037>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:6761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6761>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:6774](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6774>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1345>) |
| `CELTIUM_BLIT_DEPTH` | [CeltiumGlStub.java:9837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9837>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:249](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L249>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:519](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L519>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:10918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10918>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:10958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10958>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:11561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11561>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L889>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:63](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L63>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:3832](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3832>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:800](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L800>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:24](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L24>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1460>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7441](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7441>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1481>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6146](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6146>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:3709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3709>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [PostChainBridge.java:1101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1101>), [VulkanTerrainGpuBackend.java:18390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18390>), [VulkanTerrainGpuBackend.java:18420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18420>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L232>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L263>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:13091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13091>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:12600](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12600>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:3751](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3751>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:15802](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15802>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:11171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11171>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [CeltiumGlStub.java:10852](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10852>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:4858](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4858>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:163](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L163>), [ForgeCloudCellsMixin.java:233](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L233>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3153>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:355](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L355>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:420](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L420>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L88>), [WorldVertexBufferUploaderMixin.java:463](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L463>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L387>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L540>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1576>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CrashBreadcrumbs.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L61>), [CrashBreadcrumbs.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L80>) |
| `CELTIUM_CRUMB` | [CrashBreadcrumbs.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L72>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:17165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17165>), [VulkanTerrainGpuBackend.java:17167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17167>), [VulkanTerrainGpuBackend.java:17169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17169>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:17194](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17194>), [VulkanTerrainGpuBackend.java:17196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17196>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:17517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17517>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:17144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17144>), [VulkanTerrainGpuBackend.java:17147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17147>), [VulkanTerrainGpuBackend.java:17149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17149>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:17202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17202>), [VulkanTerrainGpuBackend.java:17205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17205>), [VulkanTerrainGpuBackend.java:17208](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17208>), [VulkanTerrainGpuBackend.java:17210](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17210>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:17122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17122>), [VulkanTerrainGpuBackend.java:17125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17125>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:2869](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2869>) |
| `CELTIUM_DEPTH_COPY` | [VulkanTerrainGpuBackend.java:18117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18117>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGlStub.java:1797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1797>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L635>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L857>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L771>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3691>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2738>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:3034](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3034>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3126>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:21132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21132>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4574>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4591](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4591>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4671](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4671>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7347](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7347>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7341](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7341>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:4682](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4682>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8472](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8472>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:4737](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4737>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:4693](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4693>), [ValidationClient.java:8068](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8068>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:4853](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4853>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L411>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1367>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L344>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L467>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L439>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L385>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1348>), [CeltiumGlStub.java:6554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6554>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:10478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10478>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:11272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11272>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:10533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10533>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:15259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15259>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:17231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17231>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:20459](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20459>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:20787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20787>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2879](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2879>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L470>) |
| `CELTIUM_DYNAMIC_LIGHT_ITEM_SPAWNED` | [ValidationClient.java:3824](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3824>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:138](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L138>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:162](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L162>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3790](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3790>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4630](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4630>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4640](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4640>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:9264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9264>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:9272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9272>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L456>), [VulkanTextureRegistry.java:508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L508>) |
| `CELTIUM_EXT_ANSWER` | [CeltiumGlStub.java:7089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7089>), [CeltiumGllessHint.java:206](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L206>), [CeltiumGllessHint.java:879](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L879>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:6587](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6587>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [VulkanTerrainGpuBackend.java:6568](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6568>), [VulkanTerrainGpuBackend.java:6625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6625>), [VulkanTerrainGpuBackend.java:6637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6637>), [VulkanTerrainGpuBackend.java:6662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6662>), [VulkanTerrainGpuBackend.java:6706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6706>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [VulkanTerrainGpuBackend.java:6612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6612>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:6673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6673>), [VulkanTerrainGpuBackend.java:6718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6718>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:21124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21124>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:6885](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6885>), [ValidationClient.java:6900](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6900>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4294>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1500>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:1460](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1460>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:10345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10345>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:14641](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14641>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:14653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14653>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:8090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8090>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:8124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8124>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:8113](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8113>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1357>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L900>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:4288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4288>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L240>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L499>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1298](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1298>), [CeltiumFontGlyphCapture.java:1428](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1428>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:7548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7548>), [VulkanTerrainGpuBackend.java:7550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7550>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1271](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1271>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1410](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1410>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1509](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1509>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L922>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:7538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7538>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:7450](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7450>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:452](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L452>), [CeltiumFontGlyphCapture.java:456](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L456>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:7542](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7542>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:804](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L804>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:11327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11327>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:7164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7164>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L275>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L317>), [FrameBudgetTimings.java:321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L321>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L612>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L131>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [VulkanTerrainGpuBackend.java:18892](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18892>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L272>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L355>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L262>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L323>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:15608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15608>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:15722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15722>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1285>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:5046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5046>) |
| `CELTIUM_GLSTUB` | [CeltiumGlStub.java:1298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1298>), [CeltiumGlStub.java:1301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1301>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2752](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2752>), [CeltiumGllessHint.java:2756](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2756>), [CeltiumGllessNativeTransformer.java:139](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L139>), [CeltiumGllessSdlSwapTransformer.java:46](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L46>), [CeltiumGllessSplashTransformer.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L76>), [CeltiumGllessSplashTransformer.java:110](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L110>), [CeltiumGllessWindowGlTransformer.java:123](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L123>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L50>), [CeleritasVintageMixinPlugin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L52>), [MinecraftMixin.java:395](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L395>), [MinecraftMixin.java:401](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L401>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5366>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5394>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:626](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L626>), [CeltiumFontGlyphCapture.java:714](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L714>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1095](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1095>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2723](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2723>), [CeltiumGllessHint.java:2728](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2728>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:709](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L709>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:739](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L739>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:21560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21560>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13111](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13111>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:4940](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4940>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1230>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:13828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13828>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:20517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20517>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:15232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15232>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L725>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:20709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20709>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:20749](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20749>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10029](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10029>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1211](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1211>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1644](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1644>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1672](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1672>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:6271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6271>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3853](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3853>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:4848](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4848>) |
| `CELTIUM_IMAGE_BIND` | [CeltiumGlStub.java:7098](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7098>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2889>) |
| `CELTIUM_IMGUNIT_ANSWER` | [CeltiumGlStub.java:7093](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7093>), [CeltiumGllessHint.java:884](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L884>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:6504](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6504>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L593>), [VulkanImmediateTexturePipeline.java:945](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L945>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:13737](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13737>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L227>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:8348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8348>) |
| `CELTIUM_INDIRECT_PROBE` | [IndirectImmediateRuns.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L144>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:4709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4709>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:19915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19915>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:216](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L216>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:301](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L301>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2398>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L759>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L248>), [CeleritasWorldRenderer.java:251](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L251>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4183>), [VulkanGlTexture.java:4186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4186>), [VulkanGlTexture.java:4188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4188>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [CeltiumGlStub.java:3276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3276>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [CeltiumGlStub.java:3389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3389>) |
| `CELTIUM_LEGACY_LIST_RUN` | [CeltiumGlStub.java:3334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3334>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [CeltiumGlStub.java:2986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2986>) |
| `CELTIUM_LEGACY_REFUSED` | [CeltiumGlStub.java:2977](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2977>), [CeltiumGlStub.java:2981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2981>), [CeltiumGlStub.java:3155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3155>), [CeltiumGlStub.java:3281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3281>) |
| `CELTIUM_LEGACY_RUN` | [CeltiumGlStub.java:3453](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3453>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L797>), [VulkanTextureRegistry.java:1904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1904>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:8093](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8093>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:11856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11856>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:11846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11846>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:11935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11935>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:11966](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11966>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:11998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11998>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:15041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15041>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:8060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8060>) |
| `CELTIUM_LIST_COMPILED` | [CeltiumGlStub.java:6017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6017>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:515](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L515>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:817](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L817>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:4418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4418>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L60>), [ValidationClient.java:9140](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9140>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:111](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L111>), [CeltiumLoadingProgress.java:135](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L135>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L109>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:9862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9862>), [ValidationClient.java:9872](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9872>), [ValidationClient.java:9878](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9878>) |
| `CELTIUM_MAIN_CLEAR` | [CeltiumGlStub.java:4423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4423>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [CeltiumGlStub.java:9301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9301>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:335](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L335>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:353](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L353>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:13721](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13721>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1076](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1076>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:5481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5481>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:19923](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19923>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12038](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12038>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:20527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20527>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1651>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1963>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2132](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2132>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L199>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:19934](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19934>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3397>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3411>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1458>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2076>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2215>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:2993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2993>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1675](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1675>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1555>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1018>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L352>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L432>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L339>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L45>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:6870](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6870>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L479>), [ModelReplay.java:516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L516>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L238>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L550>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L366>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L344>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L260>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1194](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1194>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1154>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L128>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L67>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L74>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L430>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:299](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L299>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:481](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L481>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L474>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L494>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L116>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L367>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L327>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L339>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L250>) |
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:20279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20279>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:191](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L191>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:449](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L449>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L394>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L177>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:12800](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12800>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:6695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6695>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:2607](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2607>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_GL_SURFACE` | [RenderSectionManager.java:200](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L200>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:14683](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14683>), [CeltiumGlStub.java:1337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1337>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:14685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14685>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:298](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L298>), [WorldVertexBufferUploaderMixin.java:311](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L311>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:6941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6941>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:332](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L332>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:437](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L437>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5187](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5187>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L284>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:16096](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16096>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L847>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:13362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13362>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L779>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:9557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9557>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L67>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L70>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1478>) |
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:8913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8913>), [CeltiumGlStub.java:9905](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9905>) |
| `CELTIUM_PBO_UPLOAD` | [CeltiumGlStub.java:8729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8729>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9259](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9259>), [ValidationClient.java:9403](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9403>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2342>), [VulkanContext.java:2361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2361>), [VulkanContext.java:2397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2397>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3664>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2229](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2229>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:456](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L456>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:437](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L437>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:427](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L427>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L306>), [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1245>), [VulkanPostChain.java:1250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1250>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6399](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6399>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1356>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:17589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17589>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:15943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15943>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11462](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11462>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4967](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4967>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L216>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L308>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2380](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2380>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L159>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:17698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17698>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:17562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17562>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1227>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [CeltiumGlStub.java:7153](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7153>) |
| `CELTIUM_PROGRAM_DRAW` | [CeltiumGlStub.java:10323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10323>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:13680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13680>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1453](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1453>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [CeltiumGllessHint.java:1504](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1504>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:8074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8074>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:6457](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6457>) |
| `CELTIUM_RAW_FBO_DEPTH` | [CeltiumGlStub.java:8049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8049>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:33](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L33>), [RawFboIdentityProbe.java:257](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L257>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L649>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L236>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1502](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1502>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1145>), [CeltiumGlStub.java:9916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9916>), [CeltiumGlStub.java:9925](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9925>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:9897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9897>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1137>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1005>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:21578](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21578>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:236](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L236>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L743>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:398](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L398>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3881>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:192](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L192>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:641](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L641>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:11141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11141>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:8574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8574>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:8721](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8721>), [ValidationClient.java:8722](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8722>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:8663](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8663>), [ValidationClient.java:8697](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8697>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:8824](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8824>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:8724](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8724>), [ValidationClient.java:8726](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8726>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:4820](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4820>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:4825](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4825>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2978>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2607](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2607>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2659>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:650](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L650>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:104](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L104>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L171>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:9283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9283>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:9295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9295>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:12846](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12846>) |
| `CELTIUM_SHARED_DEPTH` | [VulkanTerrainGpuBackend.java:12983](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12983>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1254>), [VulkanGlTexture.java:1289](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1289>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L634>), [VulkanGlTexture.java:726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L726>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L686>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L759>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L880>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L734>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:11375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11375>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:20318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20318>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:7064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7064>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:7051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7051>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:7007](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7007>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:7075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7075>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [VulkanTerrainGpuBackend.java:17747](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17747>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:19433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19433>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:11349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11349>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:11462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11462>), [VulkanTerrainGpuBackend.java:11467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11467>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:286](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L286>), [MinecraftMixin.java:294](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L294>), [MinecraftMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L310>), [MinecraftMixin.java:314](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L314>), [ValidationClient.java:9048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9048>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:11375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11375>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:11529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11529>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3234>), [VulkanGlTexture.java:3351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3351>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L821>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L198>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4064>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:6502](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6502>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2379>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L207>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L829>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:769](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L769>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2483>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2052>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2078>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:16046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16046>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:2536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2536>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:6304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6304>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:14971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14971>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:15079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15079>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1443](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1443>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L121>), [VulkanTextureArray.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L147>) |
| `CELTIUM_TEXTURE_DELETED` | [CeltiumGlStub.java:6880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6880>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:19083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19083>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L56>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L672>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CeltiumGlStub.java:8337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8337>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:828](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L828>) |
| `CELTIUM_TEXUNIT_ANSWER` | [CeltiumGlStub.java:7077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7077>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:9772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9772>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L753>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:9504](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9504>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1354>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:9717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9717>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L706>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L172>), [TickTimings.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L180>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:136](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L136>), [CeleritasWorldRenderer.java:150](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L150>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1213](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1213>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L917>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1059>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:6173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6173>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1338>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:8236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8236>), [WorldVertexBufferUploaderMixin.java:284](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L284>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:8237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8237>), [WorldVertexBufferUploaderMixin.java:677](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L677>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:447](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L447>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:3896](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3896>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:3608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3608>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:2458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2458>), [VulkanContext.java:2467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2467>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:6063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6063>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1310>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:281](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L281>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1503>), [VulkanContext.java:1800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1800>), [VulkanContext.java:2084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2084>), [VulkanContext.java:2126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2126>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2089>), [VulkanContext.java:2115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2115>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4278](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4278>), [ValidationClient.java:4282](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4282>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L741>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:980](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L980>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L754>), [VulkanTerrainGpuBackend.java:13323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13323>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1144](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1144>), [CeltiumGllessHint.java:1216](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1216>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L978>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:976](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L976>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:11265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11265>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1016>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:511](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L511>), [MinecraftMixin.java:517](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L517>), [MinecraftMixin.java:537](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L537>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:17326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17326>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:8787](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8787>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:7611](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7611>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1048>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L958>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L938>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L943>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L939>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:177](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L177>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:183](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L183>), [RenderSectionManager.java:185](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L185>), [RenderSectionManager.java:191](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L191>), [RenderSectionManager.java:194](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L194>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:287](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L287>), [SimpleWorldRenderer.java:293](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L293>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:299](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L299>), [SimpleWorldRenderer.java:306](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L306>), [SimpleWorldRenderer.java:313](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L313>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4342>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:8548](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8548>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2430>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L506>), [VulkanImmediateTexturePipeline.java:792](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L792>) |
