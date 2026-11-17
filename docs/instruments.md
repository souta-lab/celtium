# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **541 個**／文字列内マーカー **496 個**。
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
| [celtium.validation](#group-celtium-validation) | 158 |
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
| `celtium.allowCompetitors` | `Boolean.getBoolean` | [CeltiumCompetitors.java:33](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java#L33>) |

<a id="group-celtium-clearlatch"></a>
## `celtium.clearlatch.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:266](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L266>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12489>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16767>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16768](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16768>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [VulkanTerrainGpuBackend.java:16766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16766>) |
| `celtium.debug.bindSites` | `Boolean.getBoolean` | [CeltiumGlStub.java:575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L575>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16258>) |
| `celtium.debug.buildFlow` | `Boolean.getBoolean` | [RenderSectionManager.java:68](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L68>), [SimpleWorldRenderer.java:483](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L483>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L199>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L358>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16771](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16771>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16752](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16752>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16251>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3286](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3286>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3313>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:2240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2240>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L326>), [VulkanSwapchain.java:1442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1442>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L262>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L268>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L264>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:266](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L266>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L258>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:5601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5601>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16253>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16283>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16284>) |
| `celtium.debug.legacyRuns` | `Integer.getInteger` | [CeltiumGlStub.java:2850](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2850>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:472](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L472>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:476](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L476>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L23>) |
| `celtium.debug.markTrace` | `Boolean.getBoolean` | [RenderGlobalMixin.java:317](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L317>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modProgramReadback` | `Boolean.getBoolean` | [ModPrograms.java:215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L215>) |
| `celtium.debug.modProgramTrace` | `Integer.getInteger` | [ModPrograms.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L39>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L458>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L98>), [VulkanModelInstances.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L179>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9383>) |
| `celtium.debug.noOcclusion` | `Boolean.getBoolean` | [VintageRenderSectionManager.java:106](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L106>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16343>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3336>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16249>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3300>) |
| `celtium.debug.searchJoinCensus` | `Boolean.getBoolean` | [RenderSectionManager.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L251>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:665](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L665>) |
| `celtium.debug.skipPushConstants` | `Boolean.getBoolean` | [VulkanImmediateTexturePipeline.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L106>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3366](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3366>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3305>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3359>) |

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
| `celtium.entityLightCache` | `System.getProperty` | [CeltiumLightCache.java:39](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L39>), [EntityLightCacheMixin.java:33](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityLightCacheMixin.java#L33>) |

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
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:339](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L339>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:207](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L207>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:248](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L248>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:217](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L217>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L260>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:238](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L238>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:262](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L262>) |
| `celtium.font.timings` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:356](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L356>) |
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
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:1474](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1474>), [CeltiumGllessNativeTransformer.java:409](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L409>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:1365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1365>) |
| `celtium.glstub.attribMasks` | `System.getProperty` | [CeltiumGlStub.java:2584](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2584>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:10839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10839>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:5072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5072>) |
| `celtium.glstub.legacyCallLayout` | `System.getProperty` | [CeltiumGlStub.java:2894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2894>) |
| `celtium.glstub.legacyCallState` | `System.getProperty` | [CeltiumGlStub.java:2880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2880>) |
| `celtium.glstub.legacyListCompile` | `System.getProperty` | [CeltiumGlStub.java:2867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2867>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:11031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11031>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:5508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5508>), [CeltiumGlStub.java:10701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10701>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:5503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5503>) |

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
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2796>) |

<a id="group-celtium-native"></a>
## `celtium.native.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.native.cloudCells` | `System.getProperty` | [ForgeCloudCellsMixin.java:44](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L44>) |
| `celtium.native.endPortalBatch` | `System.getProperty` | [EndPortalProducer.java:35](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java#L35>) |
| `celtium.native.modelInstances` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3094>) |
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
| `celtium.probe.format` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:728](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L728>) |
| `celtium.probe.texture` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:716](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L716>) |
| `celtium.probe.textureLines` | `Integer.getInteger` | [WorldVertexBufferUploaderMixin.java:735](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L735>) |

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:9282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9282>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.asyncOcclusion` | `System.getProperty` | [VintageRenderSectionManager.java:67](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L67>) |
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:51](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:322](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L322>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |
| `celtium.terrain.queueHiddenPending` | `Boolean.getBoolean` | [RenderSectionManager.java:80](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L80>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [MinecraftMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L290>), [ValidationClient.java:3466](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3466>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1820](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1820>), [ValidationClient.java:3860](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3860>), [ValidationClient.java:11484](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11484>), [ValidationClient.java:12749](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12749>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1814](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1814>), [ValidationClient.java:2014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2014>), [ValidationClient.java:11477](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11477>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:2040](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2040>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:2028](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2028>), [ValidationClient.java:11478](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11478>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:2047](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2047>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:7097](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7097>), [ValidationClient.java:7111](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7111>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:7104](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7104>) |
| `celtium.validation.cameraEventProbe` | `Boolean.getBoolean` | [ValidationClient.java:3481](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3481>) |
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
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:7386](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7386>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:363](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L363>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:352](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L352>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:375](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L375>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:340](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L340>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:305](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L305>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:336](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L336>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L884>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L469>), [ValidationClient.java:2394](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2394>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:3469](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3469>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2498](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2498>) |
| `celtium.validation.creativeTab` | `Integer.getInteger` | [ValidationClient.java:6530](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6530>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:801](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L801>), [ValidationClient.java:1327](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1327>), [ValidationClient.java:11502](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11502>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:800](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L800>), [ValidationClient.java:1328](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1328>), [ValidationClient.java:11503](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11503>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L808>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:810](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L810>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:958](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L958>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:943](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L943>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:2392](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2392>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2563](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2563>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2631](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2631>), [ValidationClient.java:7178](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7178>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:2390](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2390>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:2435](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2435>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2791>), [LoadingScreenRendererMixin.java:45](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L45>), [ValidationClient.java:9082](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9082>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:4109](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4109>), [ValidationClient.java:4298](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4298>), [ValidationClient.java:4325](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4325>), [ValidationClient.java:7045](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7045>), [ValidationClient.java:9023](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9023>), [ValidationClient.java:9024](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9024>), [ValidationClient.java:9053](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9053>), [ValidationClient.java:9083](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9083>), [ValidationClient.java:9513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9513>), [ValidationClient.java:9913](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9913>), [ValidationClient.java:10126](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10126>), [ValidationClient.java:10209](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10209>), [ValidationClient.java:11048](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11048>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:1074](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1074>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:7046](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7046>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:1084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1084>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:7204](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7204>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3771](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3771>) |
| `celtium.validation.entityType` | `System.getProperty` | [ValidationClient.java:1826](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1826>), [ValidationClient.java:1827](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1827>), [ValidationClient.java:3729](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3729>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:5268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5268>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:7574](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7574>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:6952](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6952>) |
| `celtium.validation.flickerArmTick` | `Integer.getInteger` | [ValidationClient.java:3998](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3998>) |
| `celtium.validation.flightBlocksPerSecond` | `System.getProperty` | [ValidationClient.java:211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L211>) |
| `celtium.validation.flightFromTick` | `Integer.getInteger` | [ValidationClient.java:213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L213>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1271](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1271>) |
| `celtium.validation.framerate` | `Integer.getInteger` | [ValidationClient.java:4416](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4416>) |
| `celtium.validation.gameScreenshot` | `Boolean.getBoolean` | [ValidationClient.java:9464](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9464>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1426](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1426>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1419](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1419>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1421>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1428](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1428>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1434](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1434>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1436](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1436>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1431](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1431>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1424](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1424>) |
| `celtium.validation.glowAbTick` | `Integer.getInteger` | [ValidationClient.java:3933](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3933>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3914](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3914>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:7145](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7145>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1249](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1249>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1251](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1251>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:75](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L75>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:81](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L81>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1253>) |
| `celtium.validation.gvcA4Probe` | `Boolean.getBoolean` | [ValidationClient.java:1825](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1825>), [ValidationClient.java:3488](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3488>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:6374](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6374>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L877>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3904](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3904>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3802](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3802>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:1010](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1010>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3894](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3894>), [ValidationClient.java:4014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4014>), [ValidationClient.java:7177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7177>), [ValidationClient.java:7378](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7378>), [ValidationClient.java:7459](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7459>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:1061](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1061>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:6721](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6721>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1857](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1857>), [ValidationClient.java:6732](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6732>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1851>), [ValidationClient.java:5839](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5839>), [ValidationClient.java:6652](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6652>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:6673](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6673>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3727](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3727>), [ValidationClient.java:3895](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3895>), [ValidationClient.java:4595](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4595>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:4214](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4214>), [ValidationClient.java:7186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7186>), [ValidationClient.java:7187](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7187>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1863](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1863>), [ValidationClient.java:6779](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6779>) |
| `celtium.validation.litFogFrames` | `System.getProperty` | [ValidationClient.java:6808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6808>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:12755](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12755>) |
| `celtium.validation.menuCaptureTicks` | `Integer.getInteger` | [ValidationClient.java:1122](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1122>) |
| `celtium.validation.menuFrameStats` | `Boolean.getBoolean` | [ValidationClient.java:6876](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6876>), [ValidationClient.java:7824](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7824>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1110](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1110>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2598](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2598>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:936](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L936>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:7514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7514>), [ValidationClient.java:7518](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7518>) |
| `celtium.validation.modelGlint` | `Boolean.getBoolean` | [ValidationClient.java:3752](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3752>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.opaqueCopyProbe` | `Boolean.getBoolean` | [RawFboIdentityProbe.java:135](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L135>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:7127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7127>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:311](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L311>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:326](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L326>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:6354](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6354>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:7656](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7656>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:4455](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4455>) |
| `celtium.validation.probePixel` | `System.getProperty` | [ValidationClient.java:6275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6275>) |
| `celtium.validation.rawFboIdentityProbe` | `Boolean.getBoolean` | [ValidationClient.java:3484](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3484>), [ValidationClient.java:7365](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7365>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:6345](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6345>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:4063](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4063>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:4046](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4046>), [ValidationClient.java:7440](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7440>) |
| `celtium.validation.renderDistance` | `Integer.getInteger` | [ValidationClient.java:4409](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4409>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3837](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3837>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:6959](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6959>), [ValidationClient.java:11606](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11606>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:1013](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1013>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:9071](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9071>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:1029](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1029>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:1127](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1127>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:1042](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1042>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:8813](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8813>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:1014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1014>), [ValidationClient.java:6974](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6974>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:1040](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1040>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:991](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L991>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:1148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1148>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1964](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1964>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1293>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:1847](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1847>), [ValidationClient.java:6430](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6430>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2509](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2509>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:6761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6761>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:6512](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6512>) |
| `celtium.validation.shadowStage` | `Boolean.getBoolean` | [ValidationClient.java:3685](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3685>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:4056](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4056>), [ValidationClient.java:7442](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7442>) |
| `celtium.validation.signTextAbTick` | `Integer.getInteger` | [ValidationClient.java:3960](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3960>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:6940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6940>), [ValidationClient.java:7899](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7899>) |
| `celtium.validation.sparseTexture` | `Boolean.getBoolean` | [ValidationClient.java:2462](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2462>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:4596](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4596>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:7055](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7055>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2687](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2687>) |
| `celtium.validation.testFogEnd` | `System.getProperty` | [ValidationClient.java:6831](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6831>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2754](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2754>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:7192](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7192>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:4280](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4280>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:7550](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7550>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:10111](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10111>), [ValidationClient.java:10405](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10405>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:7485](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7485>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:5644](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5644>), [ValidationClient.java:7850](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7850>), [ValidationClient.java:7888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7888>), [ValidationClient.java:7932](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7932>) |
| `celtium.validation.warmSpikeMs` | `System.getProperty` | [ValidationClient.java:5648](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5648>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:5645](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5645>), [ValidationClient.java:7851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7851>), [ValidationClient.java:7889](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7889>), [ValidationClient.java:7933](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7933>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:6988](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6988>), [ValidationClient.java:7120](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7120>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1813](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1813>), [ValidationClient.java:1965](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1965>), [ValidationClient.java:11476](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11476>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:4717](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4717>) |
| `celtium.validation.worldSwitchMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:4729](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4729>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:84](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L84>) |
| `celtium.validation.worldType` | `System.getProperty` | [ValidationClient.java:4448](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4448>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18130>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L108>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2941>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2887>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.arrayRecords` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:18](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L18>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>) |
| `celtium.vulkan.asyncPresent` | `System.getProperty` | [VulkanContext.java:1485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1485>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L437>) |
| `celtium.vulkan.batchAnimationUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4507>) |
| `celtium.vulkan.batchFrameSubmits` | `System.getProperty` | [FrameCoordinator.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L45>), [VulkanTerrainGpuBackend.java:18781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18781>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16374>) |
| `celtium.vulkan.batchTexturePatches` | `System.getProperty` | [VulkanGlTexture.java:4184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4184>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3490>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L46>), [VulkanContext.java:391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L391>), [VulkanTerrainGpuBackend.java:18782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18782>) |
| `celtium.vulkan.bindTimings` | `Boolean.getBoolean` | [VulkanImmediateItemPipeline.java:402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L402>), [VulkanImmediateTexturePipeline.java:446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L446>) |
| `celtium.vulkan.brackets` | `Boolean.getBoolean` | [FramePassTimings.java:162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L162>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3675](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3675>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [PostChainBridge.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L16>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3280>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [PostChainBridge.java:13](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L13>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3287>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPairDiscardDepth` | `System.getProperty` | [VulkanTerrainGpuBackend.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L181>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1644>) |
| `celtium.vulkan.clearCallers` | `System.getProperty` | [CeltiumGlStub.java:659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L659>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5508>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10150>) |
| `celtium.vulkan.colorClearTransfer` | `System.getProperty` | [GllessFrameTarget.java:1068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L1068>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L171>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2060>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L183>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3480](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3480>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L16>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2743>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2753>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2754>) |
| `celtium.vulkan.deferPatchRegions` | `System.getProperty` | [VulkanGlTexture.java:4222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4222>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L886>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L32>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18790>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2571>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16521>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2574>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2557](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2557>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2560>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2728](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2728>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2730>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>), [ValidationClient.java:7509](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7509>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L140>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2552](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2552>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2554>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18239>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:18234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18234>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2295>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2874](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2874>), [ValidationClient.java:2993](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2993>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3507>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10070>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4182>) |
| `celtium.vulkan.fontBake` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2378>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3816>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L220>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:611](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L611>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L215>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:48](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L48>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L46>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:14135](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14135>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14138>) |
| `celtium.vulkan.frameBudget` | `Boolean.getBoolean` | [FrameBudgetTimings.java:52](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L52>) |
| `celtium.vulkan.frameBudgetFrames` | `Integer.getInteger` | [FrameBudgetTimings.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L62>) |
| `celtium.vulkan.frameBudgetLines` | `Integer.getInteger` | [FrameBudgetTimings.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L72>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14117>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2795>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2789>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2794](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2794>) |
| `celtium.vulkan.frameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L462>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16075>) |
| `celtium.vulkan.frameStats` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2786](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2786>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:359](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L359>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3277>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1483>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2444](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2444>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2484](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2484>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:2446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2446>), [VulkanTerrainGpuBackend.java:2462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2462>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2500>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2494>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2921>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L218>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2913>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1374>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2788>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:95](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L95>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2517>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2527>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1382](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1382>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2902>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2904](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2904>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2906>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3267>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L69>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L80>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L86>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3143>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L644>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3221>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3259>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L383>) |
| `celtium.vulkan.indirectImmediate` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2717>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2198>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4117>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2310>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18083>) |
| `celtium.vulkan.litFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4090>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [VulkanTerrainGpuBackend.java:5592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5592>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2430>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:5354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5354>), [CeltiumGllessHint.java:1453](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1453>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L108>) |
| `celtium.vulkan.mergeAcrossFormat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16315>) |
| `celtium.vulkan.mergeAcrossState` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16318>) |
| `celtium.vulkan.mergeAcrossTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16312>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16238>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16287>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L743>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:292](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L292>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3670](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3670>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L137>), [VulkanTerrainGpuBackend.java:2793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2793>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L142>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L222>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L166>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L178>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L290>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3536>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:106](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L106>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1423](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1423>) |
| `celtium.vulkan.modProgramAdditive` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L468>) |
| `celtium.vulkan.modProgramDepth` | `System.getProperty` | [VulkanModProgramPipeline.java:267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L267>) |
| `celtium.vulkan.modProgramSkipDraw` | `Boolean.getBoolean` | [VulkanModProgramPipeline.java:469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java#L469>) |
| `celtium.vulkan.modPrograms` | `System.getProperty` | [ModPrograms.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L31>) |
| `celtium.vulkan.modProgramsOpaqueToMain` | `System.getProperty` | [ModPrograms.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L34>) |
| `celtium.vulkan.modProgramsSampleMain` | `System.getProperty` | [ModPrograms.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L43>) |
| `celtium.vulkan.modProgramsToMain` | `System.getProperty` | [ModPrograms.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L37>) |
| `celtium.vulkan.modReadBarrier` | `System.getProperty` | [ModPrograms.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L218>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:18232](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18232>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:18219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18219>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16445](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16445>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelReplayFastPath` | `System.getProperty` | [ModelReplayFastPath.java:37](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L37>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L91>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L177>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16320>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L174>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L25>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairClearInPass` | `System.getProperty` | [GllessFrameTarget.java:1888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L1888>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14146](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14146>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14234>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2438>), [VulkanTerrainGpuBackend.java:14170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14170>) |
| `celtium.vulkan.passCounts` | `Boolean.getBoolean` | [FramePassTimings.java:566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L566>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L149>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L175>) |
| `celtium.vulkan.passSplitMicros` | `Boolean.getBoolean` | [FramePassTimings.java:736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L736>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L303>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L97>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:2274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2274>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L389>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2893>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.preflushInPass` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10841](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10841>) |
| `celtium.vulkan.preflushWhenEmpty` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10857>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12054>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14221>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:93](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L93>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>), [VulkanTerrainGpuBackend.java:3248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3248>) |
| `celtium.vulkan.presentInterval` | `Boolean.getBoolean` | [FrameBudgetTimings.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L190>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L213>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L357>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14214>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14224>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14227>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [FramePassTimings.java:692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L692>), [VulkanSwapchain.java:256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L256>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3279>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L361>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2736>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16070>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L56>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10163>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L203>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14203>) |
| `celtium.vulkan.recordsPositionTwins` | `Boolean.getBoolean` | [IndirectImmediateRuns.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L62>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2313>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3274>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3275>) |
| `celtium.vulkan.replayInputCache` | `System.getProperty` | [ModelReplayInputCache.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayInputCache.java#L69>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L47>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12586>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2150>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:3803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3803>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [VulkanTerrainGpuBackend.java:4487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4487>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3231>) |
| `celtium.vulkan.skipImmediateRecord` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16331](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16331>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2738>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14197>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L322>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:358](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L358>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [VulkanTerrainGpuBackend.java:400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L400>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L402>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L374>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L356>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:347](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L347>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L339>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L381>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:16247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16247>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:6105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6105>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:10703](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10703>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L248>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L91>), [VulkanSwapchain.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L47>), [VulkanTerrainGpuBackend.java:2797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2797>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:6293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6293>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:6284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6284>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L275>) |
| `celtium.vulkan.terrainToBoundTarget` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2950>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L128>), [VulkanTerrainGpuBackend.java:2315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2315>) |
| `celtium.vulkan.tickPercentileFrames` | `Integer.getInteger` | [FramePassTimings.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L219>) |
| `celtium.vulkan.tickPercentiles` | `Boolean.getBoolean` | [FramePassTimings.java:217](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L217>) |
| `celtium.vulkan.tickSplitFrames` | `Integer.getInteger` | [FramePassTimings.java:190](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L190>) |
| `celtium.vulkan.tickTimings` | `Boolean.getBoolean` | [TickTimings.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L29>) |
| `celtium.vulkan.tickTimingsFrom` | `Integer.getInteger` | [TickTimings.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L30>) |
| `celtium.vulkan.tickTimingsTo` | `Integer.getInteger` | [TickTimings.java:31](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L31>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14109>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2163>) |
| `celtium.vulkan.uploadBudget` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13646>) |
| `celtium.vulkan.uploadBurstThreshold` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13654>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:16421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16421>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L472>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [FramePassTimings.java:691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L691>), [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanDiagnostics.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L33>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L877>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14687>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L181>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L193>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2814>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2577>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:2580](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2580>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1774](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1774>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L308>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L35>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L512>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:9073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9073>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:215](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L215>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:2257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2257>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3892](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3892>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:4895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4895>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L103>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:4571](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4571>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1139>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:279](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L279>) |
| `CELTIUM_ASYNC_PRESENT` | [VulkanContext.java:1543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1543>), [VulkanContext.java:1551](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1551>), [VulkanContext.java:1606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1606>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L773>), [VulkanTerrainPipeline.java:814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L814>), [VulkanTerrainPipeline.java:834](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L834>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L323>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanTerrainGpuBackend.java:14539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14539>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BATCH_SITE` | [VulkanTerrainGpuBackend.java:7825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7825>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:6549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6549>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:6562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6562>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:1324](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1324>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:249](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L249>) |
| `CELTIUM_BLOCK_AREA` | [SimpleWorldRenderer.java:519](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L519>) |
| `CELTIUM_BOUNDARY_SLOW` | [VulkanTerrainGpuBackend.java:10708](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10708>) |
| `CELTIUM_BOUNDARY_SPLIT` | [VulkanTerrainGpuBackend.java:10748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10748>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:11351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11351>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L889>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L148>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:3753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3753>) |
| `CELTIUM_BUILD_FLOW` | [RenderSectionManager.java:793](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L793>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:24](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L24>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1447>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7414](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7414>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1468>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6119](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6119>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:3617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3617>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [PostChainBridge.java:1081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L1081>), [VulkanTerrainGpuBackend.java:17902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17902>), [VulkanTerrainGpuBackend.java:17932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17932>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [PostChainBridge.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L219>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [PostChainBridge.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L250>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:12818](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12818>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:179](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L179>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:81](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L81>) |
| `CELTIUM_CHURN` | [ValidationClient.java:12573](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12573>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:3672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3672>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:15494](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15494>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:10815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10815>) |
| `CELTIUM_CLIENT_ARRAY_REPACK` | [CeltiumGlStub.java:10496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10496>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:4831](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4831>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:163](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L163>), [ForgeCloudCellsMixin.java:233](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L233>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3152](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3152>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:353](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L353>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:418](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L418>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:461](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L461>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L387>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L540>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1576](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1576>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CrashBreadcrumbs.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L61>), [CrashBreadcrumbs.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L80>) |
| `CELTIUM_CRUMB` | [CrashBreadcrumbs.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L72>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [VulkanTerrainGpuBackend.java:16849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16849>), [VulkanTerrainGpuBackend.java:16851](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16851>), [VulkanTerrainGpuBackend.java:16853](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16853>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [VulkanTerrainGpuBackend.java:16878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16878>), [VulkanTerrainGpuBackend.java:16880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16880>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [VulkanTerrainGpuBackend.java:17199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17199>) |
| `CELTIUM_DEBUG_GROWTH` | [VulkanTerrainGpuBackend.java:16828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16828>), [VulkanTerrainGpuBackend.java:16831](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16831>), [VulkanTerrainGpuBackend.java:16833](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16833>) |
| `CELTIUM_DEBUG_MIRROR` | [VulkanTerrainGpuBackend.java:16886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16886>), [VulkanTerrainGpuBackend.java:16889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16889>), [VulkanTerrainGpuBackend.java:16892](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16892>), [VulkanTerrainGpuBackend.java:16894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16894>) |
| `CELTIUM_DEBUG_REPEAT` | [VulkanTerrainGpuBackend.java:16806](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16806>), [VulkanTerrainGpuBackend.java:16809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16809>) |
| `CELTIUM_DEBUG_RESIZE` | [VulkanTerrainGpuBackend.java:2777](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2777>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGlStub.java:1776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1776>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L635>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L857>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTexturePipeline.java:768](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L768>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3612>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockPipeline.java#L60>), [VulkanImmediateItemPipeline.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L74>), [VulkanImmediateParticlePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java#L60>), [VulkanImmediateTexturePipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L60>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2678>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:2970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2970>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:3062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3062>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:20621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20621>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4547](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4547>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4564](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4564>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:4644](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4644>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7320](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7320>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7314](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7314>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:4655](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4655>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8445](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8445>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:4710](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4710>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:4666](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4666>), [ValidationClient.java:8041](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8041>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:4761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4761>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L411>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:1346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1346>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L344>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L138>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L467>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L439>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L385>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:1327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1327>), [CeltiumGlStub.java:6512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6512>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:10122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10122>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:10916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10916>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:10177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L10177>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:14950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14950>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [VulkanTerrainGpuBackend.java:16915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16915>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L176>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:19970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19970>) |
| `CELTIUM_DRAW_STATE` | [VulkanTerrainGpuBackend.java:20294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20294>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2819>) |
| `CELTIUM_DUMP_WRITE` | [VulkanSwapchain.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L466>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:138](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L138>) |
| `CELTIUM_EMPTY_UPLOAD` | [WorldVertexBufferUploaderMixin.java:162](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L162>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3789](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3789>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:4603](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4603>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:4613](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4613>) |
| `CELTIUM_ENTITY_CACHE_MISMATCH` | [EntityGatherCensus.java:91](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L91>) |
| `CELTIUM_ENTITY_GATHER` | [EntityGatherCensus.java:97](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L97>) |
| `CELTIUM_ENTITY_INDEX_MISMATCH` | [EntityGatherCensus.java:60](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L60>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:8214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8214>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:8222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8222>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L456>), [VulkanTextureRegistry.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L509>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:143](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L143>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:180](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L180>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:127](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L127>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:135](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L135>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:6457](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6457>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:6500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6500>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:20613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20613>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:6858](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6858>), [ValidationClient.java:6873](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6873>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4267>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:1491](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1491>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:1451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1451>) |
| `CELTIUM_FLUSH_REASONS` | [VulkanTerrainGpuBackend.java:10135](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10135>) |
| `CELTIUM_FLUSH_SITES` | [VulkanTerrainGpuBackend.java:14339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14339>) |
| `CELTIUM_FLUSH_SOURCES` | [VulkanTerrainGpuBackend.java:14351](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14351>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:7878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7878>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:7912](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7912>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:7901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7901>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:1336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1336>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L781>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:4196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4196>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L237>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L499>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1298](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1298>), [CeltiumFontGlyphCapture.java:1428](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1428>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [VulkanTerrainGpuBackend.java:7336](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7336>), [VulkanTerrainGpuBackend.java:7338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7338>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1271](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1271>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1410](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1410>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1509](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1509>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:899](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L899>) |
| `CELTIUM_FONT_RUN_CENSUS` | [VulkanTerrainGpuBackend.java:7326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7326>) |
| `CELTIUM_FONT_RUN_REFUSED` | [VulkanTerrainGpuBackend.java:7238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7238>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:449](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L449>), [CeltiumFontGlyphCapture.java:453](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L453>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [VulkanTerrainGpuBackend.java:7330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7330>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:751](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L751>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:11117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11117>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:6952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6952>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L275>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L317>), [FrameBudgetTimings.java:321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L321>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L612>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L129>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:88](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L88>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L212>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [VulkanTerrainGpuBackend.java:18404](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18404>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L272>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L355>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:262](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L262>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:323](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L323>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:15300](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15300>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:15414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15414>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1285>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:4954](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4954>) |
| `CELTIUM_GLSTUB` | [CeltiumGlStub.java:1277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1277>), [CeltiumGlStub.java:1280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1280>), [CeleritasLoadingPlugin.java:42](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L42>), [CeltiumGllessHint.java:2602](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2602>), [CeltiumGllessHint.java:2606](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2606>), [CeltiumGllessNativeTransformer.java:139](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L139>), [CeltiumGllessSdlSwapTransformer.java:46](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L46>), [CeltiumGllessSplashTransformer.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L76>), [CeltiumGllessSplashTransformer.java:110](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L110>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:50](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L50>), [CeleritasVintageMixinPlugin.java:52](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L52>), [MinecraftMixin.java:395](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L395>), [MinecraftMixin.java:401](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L401>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:5270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5270>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:5298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5298>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:624](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L624>), [CeltiumFontGlyphCapture.java:713](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L713>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1095](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1095>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:2573](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2573>), [CeltiumGllessHint.java:2578](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2578>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:709](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L709>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:739](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L739>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:21049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21049>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:13084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13084>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:4848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4848>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1230>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:13526](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13526>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L171>), [VulkanTerrainGpuBackend.java:20028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20028>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:14922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14922>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L725>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:20218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20218>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:20256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20256>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10002](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10002>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1074](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1074>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:1494](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1494>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:1522](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1522>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:6154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6154>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3827](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3827>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:4821](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4821>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2876>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:6462](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6462>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L583>), [VulkanImmediateTexturePipeline.java:935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L935>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:13435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13435>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L224>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [VulkanTerrainGpuBackend.java:8118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8118>) |
| `CELTIUM_INDIRECT_PROBE` | [IndirectImmediateRuns.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java#L144>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:4617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4617>) |
| `CELTIUM_ITEM_GROUP` | [VulkanTerrainGpuBackend.java:19427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19427>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:196](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L196>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L290>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2400>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L759>) |
| `CELTIUM_LAYER_GEOMETRY` | [CeleritasWorldRenderer.java:240](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L240>), [CeleritasWorldRenderer.java:243](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L243>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4170>), [VulkanGlTexture.java:4173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4173>), [VulkanGlTexture.java:4175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4175>) |
| `CELTIUM_LEGACY_IMMEDIATE` | [CeltiumGlStub.java:3243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3243>) |
| `CELTIUM_LEGACY_LIST_REPLAY_REFUSED` | [CeltiumGlStub.java:3356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3356>) |
| `CELTIUM_LEGACY_LIST_RUN` | [CeltiumGlStub.java:3301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3301>) |
| `CELTIUM_LEGACY_MODE_REFUSED` | [CeltiumGlStub.java:2953](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2953>) |
| `CELTIUM_LEGACY_REFUSED` | [CeltiumGlStub.java:2944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2944>), [CeltiumGlStub.java:2948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2948>), [CeltiumGlStub.java:3122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3122>), [CeltiumGlStub.java:3248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3248>) |
| `CELTIUM_LEGACY_RUN` | [CeltiumGlStub.java:3420](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3420>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L799>), [VulkanTextureRegistry.java:1901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1901>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:7881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7881>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:11646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11646>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [VulkanTerrainGpuBackend.java:11636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11636>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:11725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11725>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:11756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11756>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:11788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11788>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:14732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14732>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:7848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7848>) |
| `CELTIUM_LIST_COMPILED` | [CeltiumGlStub.java:5975](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5975>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:508](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L508>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:810](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L810>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [VulkanTerrainGpuBackend.java:4326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4326>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L60>), [ValidationClient.java:9113](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9113>) |
| `CELTIUM_LOADING_SCREEN` | [CeltiumLoadingProgress.java:111](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L111>), [CeltiumLoadingProgress.java:135](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L135>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L109>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:9835](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9835>), [ValidationClient.java:9845](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9845>), [ValidationClient.java:9851](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9851>) |
| `CELTIUM_MAIN_CLEAR` | [CeltiumGlStub.java:4390](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4390>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [CeltiumGlStub.java:9008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9008>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:335](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L335>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:353](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L353>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:13419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13419>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1003](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1003>) |
| `CELTIUM_MATRIX_TRACE` | [VulkanTerrainGpuBackend.java:5385](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5385>) |
| `CELTIUM_MENU_GROUP` | [VulkanTerrainGpuBackend.java:19435](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19435>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:12011](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12011>) |
| `CELTIUM_MERGE_CENSUS` | [VulkanTerrainGpuBackend.java:20038](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L20038>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1638>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1950>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2119>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L198>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:19446](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19446>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3384](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3384>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3398>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1456](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1456>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2063>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2202>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:2980](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2980>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1673>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1542](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1542>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1018>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L365>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L411>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L352>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L45>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:6843](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6843>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L492>), [ModelReplay.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L529>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L251>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L563>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L368>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L346>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L273>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1197](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1197>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1157>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L339>), [ModelReplay.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L128>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L67>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L74>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:443](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L443>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:301](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L301>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L483>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L476>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:496](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L496>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L116>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L367>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L327>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L339>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L263>) |
| `CELTIUM_MOD_DRAW_ORDER` | [VulkanTerrainGpuBackend.java:19790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19790>) |
| `CELTIUM_MOD_PROGRAM_LINKED` | [ModProgramRegistry.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L154>) |
| `CELTIUM_MOD_READBACK` | [ModPrograms.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L330>) |
| `CELTIUM_MOD_READBACK_PROBE` | [ModPrograms.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java#L275>) |
| `CELTIUM_MOD_SHADERC` | [ModProgramRegistry.java:140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModProgramRegistry.java#L140>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:12773](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12773>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:6653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6653>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:2554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2554>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NOOP_CENSUS` | [VulkanTerrainGpuBackend.java:14381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14381>), [CeltiumGlStub.java:1316](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1316>) |
| `CELTIUM_NOOP_DRAWING` | [VulkanTerrainGpuBackend.java:14383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14383>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:296](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L296>), [WorldVertexBufferUploaderMixin.java:309](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L309>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:6729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6729>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:332](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L332>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:434](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L434>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:5095](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5095>) |
| `CELTIUM_OUTLINE_COMPOSITE` | [RenderGlobalOutlineCompositeMixin.java:51](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderGlobalOutlineCompositeMixin.java#L51>) |
| `CELTIUM_OUTLINE_INPUT` | [PostChainBridge.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L271>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:15788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15788>) |
| `CELTIUM_PAIR_LAYOUT` | [PostChainBridge.java:800](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L800>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:13062](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13062>) |
| `CELTIUM_PAIR_SKIP` | [PostChainBridge.java:732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L732>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:9264](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9264>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L67>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L70>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1476>) |
| `CELTIUM_PBO_REFUSED` | [CeltiumGlStub.java:8620](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8620>), [CeltiumGlStub.java:9549](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9549>) |
| `CELTIUM_PBO_UPLOAD` | [CeltiumGlStub.java:8436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8436>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9232>), [ValidationClient.java:9376](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9376>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:2294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2294>), [VulkanContext.java:2313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2313>), [VulkanContext.java:2349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2349>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:3585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3585>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:2181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2181>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:454](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L454>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:435](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L435>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:425](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L425>) |
| `CELTIUM_POSTCHAIN` | [PostChainBridge.java:293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L293>), [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:660](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L660>), [VulkanPostChain.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L689>), [VulkanPostChain.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L693>), [VulkanPostChain.java:1245](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1245>), [VulkanPostChain.java:1250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1250>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6372>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1356](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1356>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:783](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L783>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:922](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L922>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:17271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17271>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:15635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15635>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:11435](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L11435>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:4940](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4940>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:216](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L216>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L308>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:2371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2371>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L155>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:17380](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17380>) |
| `CELTIUM_PRESENT_SLOW` | [VulkanTerrainGpuBackend.java:17244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17244>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1222>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROGRAM_BOUND` | [CeltiumGlStub.java:7046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7046>) |
| `CELTIUM_PROGRAM_DRAW` | [CeltiumGlStub.java:9967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9967>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:13378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13378>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1313](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1313>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [CeltiumGllessHint.java:1364](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1364>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:7835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7835>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:6340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6340>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:33](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L33>), [RawFboIdentityProbe.java:257](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L257>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:645](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L645>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:236](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L236>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1489>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:1145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1145>), [CeltiumGlStub.java:9560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9560>), [CeltiumGlStub.java:9569](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9569>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [CeltiumGlStub.java:9541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9541>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1137>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1005>) |
| `CELTIUM_RECORD` | [VulkanTerrainGpuBackend.java:21067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L21067>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:234](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L234>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:740](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L740>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:398](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L398>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3855](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3855>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:191](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L191>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:634](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L634>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:10931](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10931>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:8547](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8547>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:8694](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8694>), [ValidationClient.java:8695](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8695>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:8636](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8636>), [ValidationClient.java:8670](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8670>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:8797](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8797>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:8697](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8697>), [ValidationClient.java:8699](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8699>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:4793](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4793>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:4798](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4798>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L196>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2965>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2594>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2646>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SEARCH_JOIN` | [RenderSectionManager.java:643](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L643>) |
| `CELTIUM_SECOND_TERRAIN_BACKEND` | [VulkanTerrainFactory.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L94>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L165>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:8240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8240>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:8256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8256>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:12819](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12819>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1241](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1241>), [VulkanGlTexture.java:1276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1276>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:634](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L634>), [VulkanGlTexture.java:726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L726>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L686>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L759>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:873](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L873>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L736>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:11019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L11019>) |
| `CELTIUM_SKY_DRAW` | [VulkanTerrainGpuBackend.java:19829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L19829>) |
| `CELTIUM_SKY_FAN` | [VulkanTerrainGpuBackend.java:6852](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6852>) |
| `CELTIUM_SKY_FRAME` | [VulkanTerrainGpuBackend.java:6839](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6839>) |
| `CELTIUM_SKY_FRAME_PROBE` | [VulkanTerrainGpuBackend.java:6795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6795>) |
| `CELTIUM_SKY_GROUP` | [VulkanTerrainGpuBackend.java:6863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6863>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [VulkanTerrainGpuBackend.java:17429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17429>) |
| `CELTIUM_SLOT_WAIT` | [VulkanTerrainGpuBackend.java:18945](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18945>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:11139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11139>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:11252](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11252>), [VulkanTerrainGpuBackend.java:11257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11257>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:286](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L286>), [MinecraftMixin.java:294](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L294>), [MinecraftMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L310>), [MinecraftMixin.java:314](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L314>), [ValidationClient.java:9021](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9021>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:11165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11165>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:11319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11319>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3221>), [VulkanGlTexture.java:3338](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3338>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L821>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4051>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [VulkanTerrainGpuBackend.java:6383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6383>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:2287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2287>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L207>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:825](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L825>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L765>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:2474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2474>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2043>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2069>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:15738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15738>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:568](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L568>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:6187](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6187>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:14662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14662>) |
| `CELTIUM_TERRAIN_BOUND_TARGET` | [VulkanTerrainGpuBackend.java:14770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14770>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1344>) |
| `CELTIUM_TEXTURE_ARRAY` | [VulkanTextureArray.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L121>), [VulkanTextureArray.java:147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureArray.java#L147>) |
| `CELTIUM_TEXTURE_DELETED` | [CeltiumGlStub.java:6837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6837>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:18595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L18595>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L56>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L672>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CeltiumGlStub.java:8044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L8044>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:775](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L775>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:9479](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9479>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L753>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:9211](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9211>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:1333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1333>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:9424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L9424>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L706>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L172>), [TickTimings.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L180>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:135](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L135>), [CeleritasWorldRenderer.java:149](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L149>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1076](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1076>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:923](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L923>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:1065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L1065>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:6131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6131>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:1317](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1317>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:8024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8024>), [WorldVertexBufferUploaderMixin.java:282](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L282>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:8025](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8025>), [WorldVertexBufferUploaderMixin.java:624](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L624>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:445](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L445>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:3883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3883>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:3529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3529>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:2410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2410>), [VulkanContext.java:2419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2419>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:98](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L98>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:6021](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6021>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1175>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1458>), [VulkanContext.java:1752](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1752>), [VulkanContext.java:2036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2036>), [VulkanContext.java:2078](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2078>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:2041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2041>), [VulkanContext.java:2067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2067>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4251](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4251>), [ValidationClient.java:4255](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4255>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L741>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:980](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L980>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L754>), [VulkanTerrainGpuBackend.java:13023](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13023>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L978>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:976](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L976>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:11055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11055>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1016>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:511](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L511>), [MinecraftMixin.java:517](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L517>), [MinecraftMixin.java:537](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L537>) |
| `CELTIUM_WINDOW_SITE` | [VulkanTerrainGpuBackend.java:17008](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L17008>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:8760](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8760>) |
| `CELTIUM_WINDOW_SPLIT` | [VulkanTerrainGpuBackend.java:7399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7399>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1048>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:958](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L958>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L938>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L943>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:939](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L939>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:177](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L177>), [RenderSectionManager.java:179](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L179>), [RenderSectionManager.java:183](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L183>), [RenderSectionManager.java:185](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L185>), [RenderSectionManager.java:191](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L191>), [RenderSectionManager.java:194](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L194>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:287](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L287>), [SimpleWorldRenderer.java:293](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L293>), [SimpleWorldRenderer.java:296](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L296>), [SimpleWorldRenderer.java:299](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L299>), [SimpleWorldRenderer.java:306](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L306>), [SimpleWorldRenderer.java:313](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L313>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:4250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4250>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:8521](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8521>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2417>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L506>), [VulkanImmediateTexturePipeline.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L789>) |
