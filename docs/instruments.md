# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **332 個**／文字列内マーカー **520 個**。
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
| [celtium.debug](#group-celtium-debug) | 40 |
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
| [celtium.glstub](#group-celtium-glstub) | 2 |
| [celtium.gpuPassTimings](#group-celtium-gpuPassTimings) | 1 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.lane1](#group-celtium-lane1) | 2 |
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
| [celtium.terrain](#group-celtium-terrain) | 1 |
| [celtium.validation](#group-celtium-validation) | 1 |
| [celtium.vulkan](#group-celtium-vulkan) | 233 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L702>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.abParity` | `System.getProperty` | [Lane2ImmediateRecorder.java:14959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14959>) |
| `celtium.debug.announceGrowth` | `System.getProperty` | [Lane2ImmediateRecorder.java:18981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18981>) |
| `celtium.debug.announceLarge` | `System.getProperty` | [Lane2ImmediateRecorder.java:18983](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18983>) |
| `celtium.debug.announceRepeat` | `System.getProperty` | [Lane2ImmediateRecorder.java:18979](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18979>) |
| `celtium.debug.bindlessSlotDump` | `System.getProperty` | [Lane2ImmediateRecorder.java:19910](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19910>), [VulkanTextureRegistry.java:431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L431>), [BindlessTextureBinding.java:59](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/bindless/BindlessTextureBinding.java#L59>) |
| `celtium.debug.bufferCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18389>) |
| `celtium.debug.copyOrderProbe` | `Boolean.getBoolean` | [TextureCopyService.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L198>) |
| `celtium.debug.displayListCensus` | `Boolean.getBoolean` | [VulkanDisplayListCensus.java:322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L322>) |
| `celtium.debug.drawAtlasQuad` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18988](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18988>) |
| `celtium.debug.drawBench` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:18960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18960>) |
| `celtium.debug.drawKeyCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18378>) |
| `celtium.debug.dumpPairsAtPresent` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3976](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3976>) |
| `celtium.debug.dumpTextureIds` | `System.getProperty` | [Lane2ImmediateRecorder.java:4012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4012>) |
| `celtium.debug.extraAllocations` | `Integer.getInteger` | [VulkanContext.java:3612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3612>) |
| `celtium.debug.fboBind` | `Boolean.getBoolean` | [FramebufferMixin.java:38](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L38>) |
| `celtium.debug.flickerArm` | `Boolean.getBoolean`, `System.getProperty` | [VulkanSwapchain.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L476>), [VulkanSwapchain.java:2131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2131>) |
| `celtium.debug.flickerDump` | `Boolean.getBoolean` | [VulkanSwapchain.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L412>) |
| `celtium.debug.flickerEvery` | `Integer.getInteger` | [VulkanSwapchain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L418>) |
| `celtium.debug.flickerFrom` | `Integer.getInteger` | [VulkanSwapchain.java:414](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L414>) |
| `celtium.debug.flickerPairs` | `Integer.getInteger` | [VulkanSwapchain.java:416](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L416>) |
| `celtium.debug.flickerSample` | `Boolean.getBoolean` | [VulkanSwapchain.java:408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L408>) |
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [DirectGlCaptureEntry.java:402](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L402>) |
| `celtium.debug.frameTail` | `Boolean.getBoolean` | [GameLoopTiming.java:105](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L105>) |
| `celtium.debug.groupCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18381](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18381>) |
| `celtium.debug.itemCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18436>) |
| `celtium.debug.itemCensusLimit` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:18438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18438>) |
| `celtium.debug.logLinesPerTag` | `Integer.getInteger` | [CeltiumLogLimiter.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L32>) |
| `celtium.debug.mirrorFontAtlas` | `Boolean.getBoolean` | [CeltiumFontAtlas.java:45](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L45>) |
| `celtium.debug.modelLightFrame` | `Long.getLong` | [ModelReplay.java:491](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L491>) |
| `celtium.debug.modelRecords` | `Integer.getInteger` | [VulkanModelInstances.java:99](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L99>), [VulkanModelInstances.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L180>) |
| `celtium.debug.noAffineBake` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:11318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L11318>) |
| `celtium.debug.periodicLogs` | `Boolean.getBoolean` | [PeriodicLog.java:12](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PeriodicLog.java#L12>) |
| `celtium.debug.pushComponentCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18485>) |
| `celtium.debug.replaceTextures` | `System.getProperty` | [Lane2ImmediateRecorder.java:4036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4036>) |
| `celtium.debug.retainedCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18375>) |
| `celtium.debug.sampleWindowImages` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3993](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3993>) |
| `celtium.debug.skipDrawCalls` | `Boolean.getBoolean` | [VulkanDynamicMesh.java:826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L826>) |
| `celtium.debug.skipTextures` | `System.getProperty` | [Lane2ImmediateRecorder.java:4069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4069>) |
| `celtium.debug.vertexDumpTexture` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:4001](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4001>) |
| `celtium.debug.windowCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:4060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4060>) |

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
| `celtium.glless.callerCensus` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:605](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L605>) |
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:599](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L599>) |
| `celtium.glless.modMatrixRoutes` | `Boolean.getBoolean` | [CeltiumGllessHint.java:2089](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2089>), [CeltiumGllessNativeTransformer.java:422](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L422>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [NativeRasterState.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeRasterState.java#L24>) |
| `celtium.glstub.ffm` | `System.getProperty` | [GlTrampolines.java:116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/GlTrampolines.java#L116>) |

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

<a id="group-celtium-lane1"></a>
## `celtium.lane1.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.lane1.gpuCull` | `System.getProperty` | [Lane1TerrainFrontend.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/lane1/Lane1TerrainFrontend.java#L108>) |
| `celtium.lane1.hiZ` | `System.getProperty` | [Lane1TerrainFrontend.java:109](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/lane1/Lane1TerrainFrontend.java#L109>) |

<a id="group-celtium-mirrorDump"></a>
## `celtium.mirrorDump.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.mirrorDump` | `System.getProperty` | [CopyReadSide.java:917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L917>) |

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
| `celtium.native.modelInstances` | `System.getProperty` | [Lane2ImmediateRecorder.java:3732](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3732>) |

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
| `celtium.releaseTextureCpuData` | `Boolean.getBoolean` | [CeltiumFrameRelease.java:42](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L42>) |
| `celtium.releaseTextureCpuData.exclude` | `System.getProperty` | [CeltiumFrameRelease.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L62>) |

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

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.greedy` | `System.getProperty` | [MesherOptions.java:44](<../forge122/src/main/java/org/soutalab/celtium/forge/terrain/MesherOptions.java#L44>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [HarnessConfig.java:70](<../celtium-core/src/harness/java/org/soutalab/celtium/harness/HarnessConfig.java#L70>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [CopyReadSide.java:822](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L822>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L110>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3398>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [Lane2ImmediateRecorder.java:3337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3337>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L75>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:77](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L77>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:79](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L79>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L82>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:62](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L62>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L593>) |
| `celtium.vulkan.batchReuseCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18530](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18530>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:4396](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4396>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3969>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3978>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L61>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [Lane2ImmediateRecorder.java:1513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1513>) |
| `celtium.vulkan.clearSkipFlush` | `System.getProperty` | [Lane2ImmediateRecorder.java:6288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6288>) |
| `celtium.vulkan.coalesceRawWrites` | `System.getProperty` | [Lane2ImmediateRecorder.java:12187](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L12187>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L198>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2072>) |
| `celtium.vulkan.copyOrderWithoutIdle` | `System.getProperty` | [TextureCopyService.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L182>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [Lane2ImmediateRecorder.java:4193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4193>) |
| `celtium.vulkan.crashBreadcrumbs` | `System.getProperty` | [CrashBreadcrumbs.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L17>) |
| `celtium.vulkan.debugFullscreenAfterFrames` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3155>) |
| `celtium.vulkan.debugResizeAfterFrames` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3168>) |
| `celtium.vulkan.debugResizeTo` | `System.getProperty` | [Lane2ImmediateRecorder.java:3170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3170>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L226>) |
| `celtium.vulkan.descWriteCensus` | `Boolean.getBoolean` | [VulkanDescriptorWrites.java:21](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L21>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTextureRecordsPipeline.java:965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L965>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L34>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:19474](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19474>) |
| `celtium.vulkan.displaySync` | `System.getProperty` | [MinecraftMixin.java:20](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L20>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2723](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2723>) |
| `celtium.vulkan.drawSiteCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18718>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2727](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2727>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2707](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2707>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2711>) |
| `celtium.vulkan.drawStateCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2969](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2969>) |
| `celtium.vulkan.drawStateMinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2972](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2972>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2700](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2700>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2703](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2703>) |
| `celtium.vulkan.entityCacheAudit` | `Boolean.getBoolean` | [EntityGatherCensus.java:23](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L23>) |
| `celtium.vulkan.entityGatherCensus` | `Boolean.getBoolean` | [EntityGatherCensus.java:22](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L22>) |
| `celtium.vulkan.entityGatherCensusEvery` | `Integer.getInteger` | [EntityGatherCensus.java:25](<../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherCensus.java#L25>) |
| `celtium.vulkan.eventTrace` | `Boolean.getBoolean` | [CeltiumSdlEventTrace.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L14>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [FrameCoordinator.java:1723](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1723>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [FrameCoordinator.java:1716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1716>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2409](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2409>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L70>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.flushEntryCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:4229](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4229>) |
| `celtium.vulkan.flushReasonCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:12091](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L12091>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:4916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4916>) |
| `celtium.vulkan.fontAtlasAggregation` | `System.getProperty` | [FontBatch.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L419>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [Lane2ImmediateRecorder.java:4548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4548>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L370>) |
| `celtium.vulkan.forceNoTerrainCulling` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:89](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L89>) |
| `celtium.vulkan.forceNoTerrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L88>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [FrameCoordinator.java:918](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L918>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L365>) |
| `celtium.vulkan.fractionalPowGuard` | `System.getProperty` | [ModGlslTranslator.java:2019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2019>) |
| `celtium.vulkan.fractionalPowGuard.census` | `Boolean.getBoolean` | [ModGlslTranslator.java:2029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2029>) |
| `celtium.vulkan.fractionalPowGuard.fastSqrt` | `Boolean.getBoolean` | [ModGlslTranslator.java:2024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2024>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:58](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L58>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L56>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:16516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16516>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:16520](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16520>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:16495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16495>) |
| `celtium.vulkan.frameRingSlotMiB` | `Integer.getInteger` | [FrameLinearRing.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/FrameLinearRing.java#L68>) |
| `celtium.vulkan.frameSignature` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18181>) |
| `celtium.vulkan.frameSlots` | `Integer.getInteger` | [FrameSubmissionScheduler.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameSubmissionScheduler.java#L43>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L509>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [Lane2ImmediateRecorder.java:3964](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3964>) |
| `celtium.vulkan.glDrawExempt` | `System.getProperty` | [Lane2ImmediateRecorder.java:12987](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L12987>) |
| `celtium.vulkan.glDrawWindowCensus` | `Boolean.getBoolean` | [GlDrawWindowCensus.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L56>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [Lane2ImmediateRecorder.java:1379](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1379>) |
| `celtium.vulkan.glsmPackState` | `System.getProperty` | [GlsmPackState.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/GlsmPackState.java#L51>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2534](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2534>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2577>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [Lane2ImmediateRecorder.java:2537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2537>), [Lane2ImmediateRecorder.java:2554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2554>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2595>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2588](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2588>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [Lane2ImmediateRecorder.java:3377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3377>) |
| `celtium.vulkan.groupDecayCensus` | `Boolean.getBoolean` | [GroupDecayCensus.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L24>) |
| `celtium.vulkan.groupDecayCensusFrames` | `Integer.getInteger` | [GroupDecayCensus.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L26>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L285>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [Lane2ImmediateRecorder.java:3368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3368>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1585](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1585>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3213>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L94>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2613>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2624](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2624>) |
| `celtium.vulkan.hudCensusFrames` | `Integer.getInteger` | [HudCensus.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L32>) |
| `celtium.vulkan.hudCensusMinFrame` | `Long.getLong` | [HudCensus.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L33>) |
| `celtium.vulkan.hudP0Frames` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2643>) |
| `celtium.vulkan.hudP0MinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2647](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2647>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [Lane2ImmediateRecorder.java:1230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1230>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [Lane2ImmediateRecorder.java:3354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3354>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [Lane2ImmediateRecorder.java:3357](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3357>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [Lane2ImmediateRecorder.java:3360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3360>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [ImmediateFogState.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L74>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [ImmediateFogState.java:85](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L85>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [ImmediateFogState.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L91>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [Lane2ImmediateRecorder.java:3815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3815>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:637](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L637>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [Lane2ImmediateRecorder.java:3897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3897>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [Lane2ImmediateRecorder.java:2220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2220>) |
| `celtium.vulkan.layoutTrace` | `Boolean.getBoolean` | [VulkanGlTexture.java:4593](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4593>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2431>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [CopyReadSide.java:775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L775>) |
| `celtium.vulkan.mainClearInQueue` | `System.getProperty` | [Lane2ImmediateRecorder.java:6392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6392>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2519](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2519>) |
| `celtium.vulkan.matrixTrace` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:6131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6131>), [CeltiumGllessHint.java:2068](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2068>) |
| `celtium.vulkan.memoPerName` | `System.getProperty` | [VulkanTextureRegistry.java:128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L128>) |
| `celtium.vulkan.mergeCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18362>) |
| `celtium.vulkan.mergeCensusPipelines` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18443](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18443>) |
| `celtium.vulkan.meshUploadBudgetBytes` | `System.getProperty` | [MeshUploadBudget.java:71](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L71>) |
| `celtium.vulkan.meshUploadBudgetMiB` | `Long.getLong` | [MeshUploadBudget.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/MeshUploadBudget.java#L67>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L313>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:4389](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4389>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L139>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L144>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L224>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L168>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L180>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:311](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L311>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [FrameCoordinator.java:1129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1129>) |
| `celtium.vulkan.mirrorUploadLog` | `Integer.getInteger` | [VulkanGlTexture.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L108>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [Lane2ImmediateRecorder.java:1283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1283>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [Lane2ImmediateRecorder.java:19431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19431>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:19417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19417>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:18618](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18618>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.modelSegmentRecords` | `System.getProperty` | [VulkanModelInstances.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L92>) |
| `celtium.vulkan.noMerge` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18472>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L201>) |
| `celtium.vulkan.nullRenderer` | `Boolean.getBoolean` | [NullRenderer.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L28>) |
| `celtium.vulkan.paceToRefresh` | `System.getProperty` | [CeltiumFramePacing.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L38>) |
| `celtium.vulkan.packShadowCache` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L392>) |
| `celtium.vulkan.packShadowRebuildBudget` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L371>) |
| `celtium.vulkan.packTerrainPrograms` | `System.getProperty` | [Lane2ImmediateRecorder.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L361>) |
| `celtium.vulkan.packetTimings` | `Boolean.getBoolean` | [PacketCost.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PacketCost.java#L27>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:16592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16592>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:324](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L324>) |
| `celtium.vulkan.pipelineCache` | `System.getProperty` | [VulkanContext.java:96](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L96>) |
| `celtium.vulkan.pipelineCacheFile` | `System.getProperty` | [VulkanContext.java:3658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3658>) |
| `celtium.vulkan.pipelineCreateCensus` | `Boolean.getBoolean` | [PipelineCreateCensus.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L51>) |
| `celtium.vulkan.pipelineCreateCensusSlowMs` | `Long.getLong` | [PipelineCreateCensus.java:54](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L54>) |
| `celtium.vulkan.pollUploadsAlways` | `Boolean.getBoolean` | [VulkanContext.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L466>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [Lane2ImmediateRecorder.java:3344](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3344>) |
| `celtium.vulkan.prepareCacheBits` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:14553](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14553>) |
| `celtium.vulkan.presentDrainFinishGuard` | `System.getProperty` | [Lane2ImmediateRecorder.java:13019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13019>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [Lane2ImmediateRecorder.java:16575](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16575>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [Lane2ImmediateRecorder.java:3926](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3926>), [VulkanContext.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L92>), [VulkanSwapchain.java:51](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L51>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L363>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L507>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:16567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16567>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:16579](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16579>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:16583](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16583>) |
| `celtium.vulkan.presentUnbindFlushGuard` | `System.getProperty` | [Lane2ImmediateRecorder.java:13001](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13001>) |
| `celtium.vulkan.probeHighlight` | `Boolean.getBoolean` | [HighlightProbe.java:34](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L34>) |
| `celtium.vulkan.probeHighlightBorrow` | `Boolean.getBoolean` | [HighlightProbe.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L39>) |
| `celtium.vulkan.probeHighlightMinFrame` | `Long.getLong` | [HighlightProbe.java:36](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L36>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L511>) |
| `celtium.vulkan.pumpBeforePresent` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3148>) |
| `celtium.vulkan.pumpInRedirect` | `System.getProperty` | [MinecraftMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L31>) |
| `celtium.vulkan.pushBlockCache` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18175>) |
| `celtium.vulkan.rawFboTextureIdentity` | `Boolean.getBoolean` | [RenderTargetRegistry.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L57>) |
| `celtium.vulkan.rawWriteSampleGate` | `System.getProperty` | [Lane2ImmediateRecorder.java:12201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L12201>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:202](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L202>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:16555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16555>) |
| `celtium.vulkan.recordsDedicatedPool` | `System.getProperty` | [InstanceDataStore.java:342](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java#L342>) |
| `celtium.vulkan.recordsRingSlotMiB` | `Integer.getInteger` | [RecordsRing.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/RecordsRing.java#L60>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2436>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:3959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3959>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3961](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3961>) |
| `celtium.vulkan.replayValueVersions` | `System.getProperty` | [ModelReplayFastPath.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplayFastPath.java#L47>) |
| `celtium.vulkan.retainedIndexFlushExempt` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3951>) |
| `celtium.vulkan.retireRecordingGuard` | `System.getProperty` | [Lane2ImmediateRecorder.java:15119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15119>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:2704](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2704>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [Lane2ImmediateRecorder.java:4533](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4533>) |
| `celtium.vulkan.shaderModules` | `Boolean.getBoolean` | [SpirvShaders.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L33>) |
| `celtium.vulkan.skipAnimatedUploads` | `System.getProperty` | [Lane2ImmediateRecorder.java:5259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5259>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3908>) |
| `celtium.vulkan.skipPresent` | `Boolean.getBoolean` | [FrameCoordinator.java:1099](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1099>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [Lane2ImmediateRecorder.java:16548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16548>) |
| `celtium.vulkan.skyCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:772](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L772>) |
| `celtium.vulkan.skyCensusLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:804](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L804>) |
| `celtium.vulkan.skyFrameProbe` | `Long.getLong` | [Lane2ImmediateRecorder.java:859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L859>) |
| `celtium.vulkan.skyFrameProbeLimit` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L862>) |
| `celtium.vulkan.skyGroupLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:826](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L826>) |
| `celtium.vulkan.skyNoDepth` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:801](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L801>) |
| `celtium.vulkan.skyNoFog` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L791>) |
| `celtium.vulkan.skyPoison` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L782>) |
| `celtium.vulkan.splashCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:18372](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18372>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L250>) |
| `celtium.vulkan.swapchainImages` | `Integer.getInteger` | [VulkanSwapchain.java:228](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L228>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [FrameCoordinator.java:1101](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1101>), [VulkanContext.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L90>), [VulkanSwapchain.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L49>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:7672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7672>) |
| `celtium.vulkan.targetFormatCensus` | `Boolean.getBoolean` | [CaptureUploadContext.java:26](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L26>), [TargetFormatCensus.java:57](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L57>) |
| `celtium.vulkan.targetFormatClass` | `System.getProperty` | [Lane2ImmediateRecorder.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L318>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:7662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7662>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:417](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L417>) |
| `celtium.vulkan.terrainShadow` | `System.getProperty` | [Lane2ImmediateRecorder.java:3518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3518>) |
| `celtium.vulkan.terrainShadowDarkness` | `System.getProperty` | [Lane2ImmediateRecorder.java:3522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3522>) |
| `celtium.vulkan.terrainStatistics` | `Boolean.getBoolean` | [TerrainStatisticsReport.java:87](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L87>) |
| `celtium.vulkan.terrainToBoundTarget` | `System.getProperty` | [Lane2ImmediateRecorder.java:3415](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3415>) |
| `celtium.vulkan.terrainToDepthTarget` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3504](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3504>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:2439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2439>), [VulkanGlTexture.java:130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L130>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:16485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16485>) |
| `celtium.vulkan.unflushedTargetLog` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2185>) |
| `celtium.vulkan.uploadBudgetMiB` | `Long.getLong` | [VulkanContext.java:1057](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1057>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:18586](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L18586>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L592>) |
| `celtium.vulkan.upstreamArrivalCensus` | `Boolean.getBoolean` | [UpstreamArrivalCensus.java:28](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L28>) |
| `celtium.vulkan.upstreamArrivalCensusFrames` | `Integer.getInteger` | [UpstreamArrivalCensus.java:30](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L30>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [FrameCoordinator.java:1654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1654>) |
| `celtium.vulkan.vsync` | `System.getProperty` | [VulkanSwapchain.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L218>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L192>) |
| `celtium.vulkan.windowDiag` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:3244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3244>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [Lane2ImmediateRecorder.java:2731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2731>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [Lane2ImmediateRecorder.java:2735](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L2735>) |
| `celtium.vulkan.windowSplit` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:1709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1709>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [Lane2ImmediateRecorder.java:756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L756>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:27](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L27>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ACCEPTED` | [Lane2ImmediateRecorder.java:10929](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10929>) |
| `CELTIUM_ACCEPT_FLUSH` | [FramePassTimings.java:911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L911>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:236](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L236>) |
| `CELTIUM_ALLOCATION_EXPERIMENT` | [VulkanContext.java:3629](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3629>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:4185](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4185>) |
| `CELTIUM_ANIM` | [Lane2ImmediateRecorder.java:5677](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5677>) |
| `CELTIUM_ANIMATION_BATCH_FAILURE` | [TextureAtlasMixin.java:108](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L108>) |
| `CELTIUM_ANIM_ANNOUNCE` | [Lane2ImmediateRecorder.java:5346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5346>) |
| `CELTIUM_ANIM_TICK` | [TextureAtlasMixin.java:191](<../forge122/src/main/java/org/taumc/celeritas/mixin/features/textures/TextureAtlasMixin.java#L191>) |
| `CELTIUM_ANNOUNCE_EXTENT` | [VulkanTextureRegistry.java:1657](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1657>) |
| `CELTIUM_ARENA_FRAGMENTATION` | [VulkanArena.java:814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L814>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L632>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:847](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L847>), [VulkanArena.java:856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L856>), [VulkanArena.java:867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L867>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:1108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1108>), [VulkanTerrainPipeline.java:1149](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1149>), [VulkanTerrainPipeline.java:1169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1169>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L476>) |
| `CELTIUM_BACKEND_CALLS` | [BackendCallCounters.java:154](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendCallCounters.java#L154>) |
| `CELTIUM_BACKEND_DELETE` | [VulkanBackendAdapter.java:1070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L1070>) |
| `CELTIUM_BATCH_ROUTE` | [Lane2ImmediateRecorder.java:21510](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21510>) |
| `CELTIUM_BATCH_SITE` | [Lane2ImmediateRecorder.java:9690](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9690>) |
| `CELTIUM_BIND_ENTRY` | [Lane2ImmediateRecorder.java:8313](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8313>) |
| `CELTIUM_BIND_PENDING` | [Lane2ImmediateRecorder.java:8326](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8326>) |
| `CELTIUM_BLIT_DEPTH` | [DirectGlCaptureEntry.java:1363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1363>) |
| `CELTIUM_BLIT_UNSERVED` | [DirectGlCaptureEntry.java:1315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1315>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:242](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L242>) |
| `CELTIUM_BORROW_BYPASS` | [PresentSwapchain.java:372](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L372>) |
| `CELTIUM_BOUNDARY_SLOW` | [FrameCoordinator.java:1192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1192>) |
| `CELTIUM_BOUNDARY_SPLIT` | [FrameCoordinator.java:1234](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1234>) |
| `CELTIUM_BOUND_TARGET_CENSUS` | [Lane2ImmediateRecorder.java:3451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3451>) |
| `CELTIUM_BOX_PRE` | [Lane2ImmediateRecorder.java:13766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13766>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L951>) |
| `CELTIUM_BUFFER_CENSUS` | [FrameStatsReport.java:151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L151>) |
| `CELTIUM_BUFFER_RETIRED` | [VulkanContext.java:5741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5741>), [VulkanContext.java:6144](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L6144>) |
| `CELTIUM_CAMERA_EVENT_PROBE` | [CameraEventProbe.java:26](<../validation/client/src/main/java/org/soutalab/celtium/validation/CameraEventProbe.java#L26>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:86](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L86>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:74](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L74>) |
| `CELTIUM_CAPS_PROBE` | [CeltiumGllessHint.java:61](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L61>), [CeltiumGllessHint.java:76](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L76>) |
| `CELTIUM_CAPTURE` | [Lane2ImmediateRecorder.java:19924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19924>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1658](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1658>) |
| `CELTIUM_CAPTURE_ENTITY_HOLD` | [ValidationClient.java:7897](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7897>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1679>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:6624](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6624>) |
| `CELTIUM_CHAIN_CLEAR` | [Lane2ImmediateRecorder.java:4328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4328>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [CopyReadSide.java:595](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L595>), [CopyReadSide.java:625](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L625>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanBackendAdapter.java:641](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L641>) |
| `CELTIUM_CHAIN_INPUT_FLUSH_FAILED` | [VulkanBackendAdapter.java:617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L617>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [Lane2ImmediateRecorder.java:15509](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15509>) |
| `CELTIUM_CHAIN_PASS` | [Lane2ImmediateRecorder.java:138](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L138>), [Lane2ImmediateRecorder.java:161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L161>), [Lane2ImmediateRecorder.java:218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L218>), [Lane2ImmediateRecorder.java:256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L256>) |
| `CELTIUM_CHAIN_SKIP` | [ShaderGroupMixin.java:206](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L206>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHURN` | [ValidationClient.java:13636](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13636>) |
| `CELTIUM_CLEANUP_RETIRED` | [VulkanContext.java:5574](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5574>) |
| `CELTIUM_CLEAR_LATCH` | [Lane2ImmediateRecorder.java:17747](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17747>) |
| `CELTIUM_CLIENT_CHUNKS` | [ValidationClient.java:5201](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5201>) |
| `CELTIUM_CLOUD_CELLS` | [ForgeCloudCellsMixin.java:189](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L189>), [ForgeCloudCellsMixin.java:296](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ForgeCloudCellsMixin.java#L296>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:3259](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3259>) |
| `CELTIUM_COMPACTED_BATCH` | [WorldVertexBufferUploaderMixin.java:429](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L429>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:531](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L531>) |
| `CELTIUM_COMPOSITE_REFUSED` | [NativeFramebufferCompositor.java:47](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L47>), [NativeFramebufferCompositor.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java#L97>), [WorldVertexBufferUploaderMixin.java:574](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L574>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:452](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L452>) |
| `CELTIUM_COPY_IMAGE` | [CaptureUploadContext.java:337](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L337>), [CaptureUploadContext.java:374](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L374>) |
| `CELTIUM_COPY_ORDER` | [TextureCopyService.java:601](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L601>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1679>) |
| `CELTIUM_COPY_READ_FORMAT_REFUSED` | [TextureCopyService.java:1668](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1668>) |
| `CELTIUM_CRASH_BREADCRUMBS` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L277>), [CrashBreadcrumbs.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L302>) |
| `CELTIUM_CRASH_CONTEXT` | [CrashBreadcrumbs.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L287>) |
| `CELTIUM_CRUMB` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [CrashBreadcrumbs.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CrashBreadcrumbs.java#L294>) |
| `CELTIUM_DEAD_TEXTURE_REFUSED` | [VulkanDescriptorWrites.java:49](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L49>) |
| `CELTIUM_DEBUG_ANNOUNCE` | [Lane2ImmediateRecorder.java:19071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19071>), [Lane2ImmediateRecorder.java:19073](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19073>), [Lane2ImmediateRecorder.java:19075](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19075>) |
| `CELTIUM_DEBUG_ATLAS_QUAD` | [Lane2ImmediateRecorder.java:19100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19100>), [Lane2ImmediateRecorder.java:19102](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19102>) |
| `CELTIUM_DEBUG_FULLSCREEN` | [PresentSwapchain.java:672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L672>) |
| `CELTIUM_DEBUG_GROWTH` | [Lane2ImmediateRecorder.java:19050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19050>), [Lane2ImmediateRecorder.java:19053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19053>), [Lane2ImmediateRecorder.java:19055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19055>) |
| `CELTIUM_DEBUG_MIRROR` | [Lane2ImmediateRecorder.java:19108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19108>), [Lane2ImmediateRecorder.java:19111](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19111>), [Lane2ImmediateRecorder.java:19114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19114>), [Lane2ImmediateRecorder.java:19116](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19116>) |
| `CELTIUM_DEBUG_REPEAT` | [Lane2ImmediateRecorder.java:19028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19028>), [Lane2ImmediateRecorder.java:19031](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19031>) |
| `CELTIUM_DEBUG_RESIZE` | [Lane2ImmediateRecorder.java:3195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L3195>) |
| `CELTIUM_DELETE_SETTLED_SLOW` | [VulkanBackendAdapter.java:1032](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L1032>) |
| `CELTIUM_DEPTH_COPY` | [CopyReadSide.java:295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L295>) |
| `CELTIUM_DEPTH_COPY_SKIPPED` | [CeltiumGllessHint.java:1557](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1557>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L697>) |
| `CELTIUM_DEPTH_JUDGE` | [Lane2ImmediateRecorder.java:16936](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16936>) |
| `CELTIUM_DEPTH_NOTE` | [Lane2ImmediateRecorder.java:6844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6844>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:919](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L919>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:56](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L56>) |
| `CELTIUM_DESCRIPTOR_VK_RESULT` | [VulkanImmediateTextureRecordsPipeline.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L809>) |
| `CELTIUM_DESC_LAYOUT_WRITE` | [VulkanDescriptorWrites.java:90](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorWrites.java#L90>) |
| `CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5503>) |
| `CELTIUM_DESC_WRITE` | [VulkanImmediateBlockRecordsPipeline.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateBlockRecordsPipeline.java#L60>), [VulkanImmediateItemRecordsPipeline.java:80](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L80>), [VulkanImmediateParticleRecordsPipeline.java:67](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticleRecordsPipeline.java#L67>), [VulkanImmediateTextureRecordsPipeline.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L68>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:4327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4327>) |
| `CELTIUM_DEVICE_DESCRIPTOR_LIMITS` | [VulkanContext.java:4655](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4655>) |
| `CELTIUM_DEVICE_FEATURES` | [VulkanContext.java:4863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4863>) |
| `CELTIUM_DEVICE_LOST` | [FrameCoordinator.java:2112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2112>), [FrameCoordinator.java:2200](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2200>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:4917](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4917>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:4934](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4934>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:5014](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5014>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:7785](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7785>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:7779](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7779>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:5025](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5025>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:8961](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8961>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:5080](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5080>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:5036](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5036>), [ValidationClient.java:8557](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8557>) |
| `CELTIUM_DIRECT_SUPPLY` | [Lane2ImmediateRecorder.java:5537](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5537>) |
| `CELTIUM_DISPLAYLIST_COMPILED` | [DisplayListLegacyScreen.java:540](<../validation/client/src/main/java/org/soutalab/celtium/validation/DisplayListLegacyScreen.java#L540>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L375>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L308>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L133>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:92](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L92>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L108>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:431](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L431>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L403>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L349>) |
| `CELTIUM_DRAWS` | [DrawCounters.java:103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DrawCounters.java#L103>) |
| `CELTIUM_DRAW_BENCH` | [Lane2ImmediateRecorder.java:19137](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19137>) |
| `CELTIUM_DRAW_KEY_CENSUS` | [FrameStatsReport.java:179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L179>) |
| `CELTIUM_DRAW_SITE` | [Lane2ImmediateRecorder.java:21438](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21438>) |
| `CELTIUM_DRAW_STATE` | [Lane2ImmediateRecorder.java:21799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21799>) |
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
| `CELTIUM_ENTITY_REFUSED` | [Lane2ImmediateRecorder.java:10951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10951>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [Lane2ImmediateRecorder.java:10959](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10959>) |
| `CELTIUM_EXTERNAL_DEPTH_IMAGE` | [VulkanTextureRegistry.java:807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L807>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L693>), [VulkanTextureRegistry.java:878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L878>) |
| `CELTIUM_EXT_ANSWER` | [CeltiumGllessHint.java:205](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L205>), [CeltiumGllessHint.java:954](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L954>) |
| `CELTIUM_F3_EVENT` | [CeltiumF3Overlay.java:90](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L90>) |
| `CELTIUM_F3_SECTION` | [CeltiumF3Overlay.java:122](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L122>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeltiumF3Overlay.java:78](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L78>) |
| `CELTIUM_FBO_BIND` | [FramebufferMixin.java:156](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L156>) |
| `CELTIUM_FBO_CENSUS` | [Lane2ImmediateRecorder.java:7905](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7905>) |
| `CELTIUM_FBO_DEPTH_PUBLISHED` | [Lane2ImmediateRecorder.java:8212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8212>) |
| `CELTIUM_FBO_PUBLISH_REFUSED` | [Lane2ImmediateRecorder.java:7886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7886>), [Lane2ImmediateRecorder.java:7957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7957>), [Lane2ImmediateRecorder.java:7967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7967>), [Lane2ImmediateRecorder.java:7988](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7988>), [Lane2ImmediateRecorder.java:8013](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8013>), [Lane2ImmediateRecorder.java:8158](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8158>), [Lane2ImmediateRecorder.java:8179](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8179>), [Lane2ImmediateRecorder.java:8198](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8198>), [Lane2ImmediateRecorder.java:8256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8256>) |
| `CELTIUM_FBO_TARGET_ENSURED` | [Lane2ImmediateRecorder.java:7930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7930>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [Lane2ImmediateRecorder.java:8024](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8024>), [Lane2ImmediateRecorder.java:8268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8268>) |
| `CELTIUM_FENCE_TIMEOUT` | [FrameCoordinator.java:2103](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2103>), [FrameCoordinator.java:2139](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2139>), [FrameCoordinator.java:2192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2192>), [FrameCoordinator.java:2224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2224>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:7324](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7324>), [ValidationClient.java:7338](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7338>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:4637](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4637>) |
| `CELTIUM_FLICKER` | [VulkanSwapchain.java:2180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2180>) |
| `CELTIUM_FLICKER_ARMED` | [VulkanSwapchain.java:2140](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2140>) |
| `CELTIUM_FLUSH_REASONS` | [Lane2ImmediateRecorder.java:12170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L12170>) |
| `CELTIUM_FLUSH_SITES` | [Lane2ImmediateRecorder.java:16662](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16662>) |
| `CELTIUM_FLUSH_SOURCES` | [Lane2ImmediateRecorder.java:16674](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16674>) |
| `CELTIUM_FMT5_POS` | [Lane2ImmediateRecorder.java:9743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9743>) |
| `CELTIUM_FMT5_SITE` | [Lane2ImmediateRecorder.java:9777](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9777>) |
| `CELTIUM_FMT5_VERTEX` | [Lane2ImmediateRecorder.java:9766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9766>) |
| `CELTIUM_FN_CENSUS` | [DirectGlCaptureEntry.java:279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L279>) |
| `CELTIUM_FOG_CAPTURE` | [Lane2ImmediateRecorder.java:4931](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4931>) |
| `CELTIUM_FOG_STATE` | [ImmediateFogState.java:239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L239>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L547>) |
| `CELTIUM_FONT_FLUSH_REENTRY` | [FontBatch.java:863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L863>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1322](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1322>), [CeltiumFontGlyphCapture.java:1452](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1452>) |
| `CELTIUM_FONT_GLYPH_CENSUS` | [Lane2ImmediateRecorder.java:9061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9061>), [Lane2ImmediateRecorder.java:9063](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9063>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1295](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1295>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:899](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L899>), [CeltiumFontGlyphCapture.java:1434](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1434>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1534](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1534>) |
| `CELTIUM_FONT_RETAINED_DROPPED` | [FontBatch.java:989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L989>), [FontBatch.java:1029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L1029>) |
| `CELTIUM_FONT_RUN_CENSUS` | [Lane2ImmediateRecorder.java:9033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9033>) |
| `CELTIUM_FONT_RUN_REFUSED` | [Lane2ImmediateRecorder.java:8950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8950>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:457](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L457>), [CeltiumFontGlyphCapture.java:461](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L461>) |
| `CELTIUM_FONT_WITNESS_CENSUS` | [Lane2ImmediateRecorder.java:9045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9045>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:920](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L920>) |
| `CELTIUM_FRAMEBUFFER_HINT_FAILED` | [Lane2ImmediateRecorder.java:1176](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L1176>) |
| `CELTIUM_FRAME_AT_WINDOW` | [Lane2ImmediateRecorder.java:13532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13532>) |
| `CELTIUM_FRAME_BASE` | [Lane2ImmediateRecorder.java:8751](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8751>) |
| `CELTIUM_FRAME_BUDGET` | [FrameBudgetTimings.java:686](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L686>) |
| `CELTIUM_FRAME_BUDGET_SUMMARY` | [FrameBudgetTimings.java:739](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L739>), [FrameBudgetTimings.java:743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L743>) |
| `CELTIUM_FRAME_CENSUS` | [CeltiumFrameRelease.java:149](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L149>) |
| `CELTIUM_FRAME_FORMAT_MOVED` | [VulkanContext.java:1319](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1319>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:907](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L907>) |
| `CELTIUM_FRAME_QUEUED` | [FrameCoordinator.java:680](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L680>) |
| `CELTIUM_FRAME_RELEASE` | [CeltiumFrameRelease.java:175](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumFrameRelease.java#L175>) |
| `CELTIUM_FRAME_STATS` | [FrameStatsReport.java:215](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L215>) |
| `CELTIUM_FRAME_TAIL` | [GameLoopTiming.java:152](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L152>) |
| `CELTIUM_FRAME_TARGET_DEFERRED` | [CopyReadSide.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1083>) |
| `CELTIUM_FRAME_TICK_CONSTANTS` | [FramePassTimings.java:527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L527>) |
| `CELTIUM_FRAME_TICK_PASSES` | [FramePassTimings.java:610](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L610>) |
| `CELTIUM_FRAME_TICK_PERCENTILES` | [FramePassTimings.java:517](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L517>) |
| `CELTIUM_FRAME_TICK_SPLIT` | [GameLoopTiming.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java#L188>) |
| `CELTIUM_FRAME_TICK_TAIL` | [FramePassTimings.java:578](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L578>) |
| `CELTIUM_FREE_MEMORY_SLOW` | [VulkanContext.java:3565](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3565>) |
| `CELTIUM_FUNNEL_FIRST_RAISE` | [CeltiumLightCache.java:342](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L342>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_HIT` | [CeltiumLightCache.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L333>) |
| `CELTIUM_FUNNEL_FIRST_SOURCE_QUERY` | [CeltiumLightCache.java:324](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L324>) |
| `CELTIUM_FUNNEL_NO_RAISE_YET` | [CeltiumLightCache.java:351](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L351>) |
| `CELTIUM_FUNNEL_SUMMARY` | [CeltiumLightCache.java:218](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L218>), [CeltiumLightCache.java:296](<../forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java#L296>) |
| `CELTIUM_GAME_CLEAR` | [Lane2ImmediateRecorder.java:17522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17522>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [Lane2ImmediateRecorder.java:17650](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17650>) |
| `CELTIUM_GATHER_FIRST` | [MinecraftDynamicLightService.java:163](<../forge122/src/main/java/org/soutalab/celtium/forge/light/MinecraftDynamicLightService.java#L163>) |
| `CELTIUM_GET_TEX_IMAGE` | [TextureCopyService.java:1362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1362>) |
| `CELTIUM_GLDRAW_WINDOW_CENSUS` | [GlDrawWindowCensus.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GlDrawWindowCensus.java#L152>) |
| `CELTIUM_GLESS_INVALIDATE` | [Lane2ImmediateRecorder.java:5736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5736>) |
| `CELTIUM_GLSTUB` | [DirectGlCaptureEntry.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L318>), [DirectGlCaptureEntry.java:321](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L321>), [CeleritasLoadingPlugin.java:59](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L59>), [CeltiumGllessHint.java:3433](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L3433>), [CeltiumGllessHint.java:3437](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L3437>), [CeltiumGllessNativeTransformer.java:152](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L152>), [CeltiumGllessSdlSwapTransformer.java:49](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSdlSwapTransformer.java#L49>), [CeltiumGllessSplashTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L81>), [CeltiumGllessSplashTransformer.java:115](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L115>), [CeltiumGllessWindowGlTransformer.java:125](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowGlTransformer.java#L125>), [CeltiumGllessWindowTransformer.java:81](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L81>), [CeleritasVintageMixinPlugin.java:53](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L53>), [CeleritasVintageMixinPlugin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L55>), [MinecraftMixin.java:446](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L446>), [MinecraftMixin.java:452](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L452>) |
| `CELTIUM_GLUPERSPECTIVE` | [Lane2ImmediateRecorder.java:6045](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6045>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [Lane2ImmediateRecorder.java:6074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6074>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:631](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L631>), [CeltiumFontGlyphCapture.java:719](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L719>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1119](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1119>) |
| `CELTIUM_GL_CALLERS` | [CeltiumGllessHint.java:1488](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1488>) |
| `CELTIUM_GL_CONTEXT_TERMS` | [CeltiumGllessContextTermsTransformer.java:88](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessContextTermsTransformer.java#L88>), [CeltiumGllessHint.java:3396](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L3396>), [CeltiumGllessHint.java:3403](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L3403>), [CeltiumGllessHint.java:3409](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L3409>) |
| `CELTIUM_GL_DISPATCH_FAILURE` | [DirectGlCaptureEntry.java:516](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L516>) |
| `CELTIUM_GL_SURFACE` | [CeltiumGllessNativeTransformer.java:752](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L752>) |
| `CELTIUM_GL_SURFACE_DRAWING` | [CeltiumGllessNativeTransformer.java:782](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L782>) |
| `CELTIUM_GL_SURFACE_NOOP` | [CeltiumGllessNativeTransformer.java:821](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L821>) |
| `CELTIUM_GPU_VERTEX` | [FrameCoordinator.java:2568](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L2568>) |
| `CELTIUM_GREGTECH_STAGE` | [ValidationClient.java:14147](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L14147>) |
| `CELTIUM_GROUPS` | [Lane2ImmediateRecorder.java:5630](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5630>) |
| `CELTIUM_GROUP_BOUNDS` | [BackendDiagnostics.java:1560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1560>) |
| `CELTIUM_GROUP_CLOSE` | [Lane2ImmediateRecorder.java:16349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16349>) |
| `CELTIUM_GROUP_COMPOSITION` | [FrameStatsReport.java:174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L174>), [Lane2ImmediateRecorder.java:21503](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21503>) |
| `CELTIUM_GROUP_DECAY` | [GroupDecayCensus.java:82](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GroupDecayCensus.java#L82>) |
| `CELTIUM_GROUP_DETAIL` | [BackendDiagnostics.java:1055](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1055>) |
| `CELTIUM_GROUP_SCISSOR` | [Lane2ImmediateRecorder.java:21717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21717>) |
| `CELTIUM_GROUP_VIEWPORT` | [Lane2ImmediateRecorder.java:21759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21759>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:10531](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10531>) |
| `CELTIUM_GVC_A4_PROBE` | [GvcA4Probe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L38>), [GvcA4Probe.java:41](<../validation/client/src/main/java/org/soutalab/celtium/validation/GvcA4Probe.java#L41>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:1483](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1483>) |
| `CELTIUM_HINT_MATRIXMODE` | [CeltiumGllessHint.java:2109](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2109>) |
| `CELTIUM_HINT_MULTMATRIX` | [CeltiumGllessHint.java:2137](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L2137>) |
| `CELTIUM_HUD_CENSUS` | [HudCensus.java:237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L237>), [HudCensus.java:240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HudCensus.java#L240>) |
| `CELTIUM_HUD_GROUP` | [Lane2ImmediateRecorder.java:7439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7439>) |
| `CELTIUM_HUD_P0` | [Lane2ImmediateRecorder.java:7505](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7505>) |
| `CELTIUM_HUD_P0_SUMMARY` | [Lane2ImmediateRecorder.java:7529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7529>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:4120](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4120>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:5191](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5191>) |
| `CELTIUM_IDLE_CAP` | [CeltiumFramePacing.java:78](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumFramePacing.java#L78>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:3177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3177>) |
| `CELTIUM_IMGUNIT_ANSWER` | [DirectGlCaptureEntry.java:1287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1287>), [CeltiumGllessHint.java:959](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L959>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemRecordsPipeline.java:619](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L619>), [VulkanImmediateTextureRecordsPipeline.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L1017>) |
| `CELTIUM_IMMEDIATE_ENQ` | [Lane2ImmediateRecorder.java:16237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16237>) |
| `CELTIUM_IMMEDIATE_FOG` | [ImmediateFogState.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java#L226>) |
| `CELTIUM_INCOMPLETE_TEXTURE_BLACK` | [Lane2ImmediateRecorder.java:10005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10005>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [Lane2ImmediateRecorder.java:5391](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5391>) |
| `CELTIUM_ITEM_GROUP` | [Lane2ImmediateRecorder.java:20882](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L20882>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:238](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L238>) |
| `CELTIUM_ITEM_MESH_EMPTY` | [RenderItemMixin.java:336](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L336>) |
| `CELTIUM_K6` | [WorldVertexBufferUploaderMixin.java:467](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L467>) |
| `CELTIUM_LANE1_SETUP_STATS` | [CeltiumTerrainDriver.java:387](<../forge122/src/main/java/org/soutalab/celtium/forge/terrain/CeltiumTerrainDriver.java#L387>) |
| `CELTIUM_LARGE_MIRROR_REMOVED` | [VulkanTextureRegistry.java:2951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2951>) |
| `CELTIUM_LARGE_WINDOWS` | [BackendDiagnostics.java:1089](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1089>) |
| `CELTIUM_LAYOUT_TRACE` | [VulkanGlTexture.java:4646](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4646>), [VulkanGlTexture.java:4649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4649>), [VulkanGlTexture.java:4651](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4651>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:1294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1294>), [VulkanTextureRegistry.java:2455](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2455>) |
| `CELTIUM_LIGHTCOORD` | [Lane2ImmediateRecorder.java:9746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9746>) |
| `CELTIUM_LIGHTMAP` | [Lane2ImmediateRecorder.java:14077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14077>) |
| `CELTIUM_LIGHTMAP_EXTENT` | [Lane2ImmediateRecorder.java:14067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14067>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [Lane2ImmediateRecorder.java:14156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14156>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [Lane2ImmediateRecorder.java:14188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14188>) |
| `CELTIUM_LIGHTMAP_STAGE` | [Lane2ImmediateRecorder.java:14222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L14222>) |
| `CELTIUM_LINE_NARROWED` | [Lane2ImmediateRecorder.java:9713](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9713>) |
| `CELTIUM_LIT_FOG_SKIPPED` | [Lane2ImmediateRecorder.java:5064](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5064>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:61](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L61>), [ValidationClient.java:9642](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9642>) |
| `CELTIUM_LOADING_SCREEN` | [NativeLoadingScreen.java:226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeLoadingScreen.java#L226>), [CeltiumLoadingProgress.java:116](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L116>), [CeltiumLoadingProgress.java:126](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L126>), [CeltiumLoadingProgress.java:147](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java#L147>) |
| `CELTIUM_LOG_LIMIT` | [CeltiumLogLimiter.java:163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L163>) |
| `CELTIUM_LOOP_SPAN` | [FrameBudgetTimings.java:497](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L497>) |
| `CELTIUM_LOOP_SPAN_SUMMARY` | [FrameBudgetTimings.java:730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L730>) |
| `CELTIUM_LOS_WALL` | [ValidationClient.java:3980](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3980>), [ValidationClient.java:4000](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4000>) |
| `CELTIUM_MACHINE_GUI` | [ValidationClient.java:10364](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10364>), [ValidationClient.java:10374](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10374>), [ValidationClient.java:10380](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10380>) |
| `CELTIUM_MAP_RANGE_REFUSED` | [BufferStorageRecord.java:166](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BufferStorageRecord.java#L166>) |
| `CELTIUM_MARK_CALLER` | [RenderGlobalMixin.java:318](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L318>) |
| `CELTIUM_MARK_TRACE` | [RenderGlobalMixin.java:336](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L336>) |
| `CELTIUM_MATRIX_DUMP` | [Lane2ImmediateRecorder.java:16221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16221>) |
| `CELTIUM_MATRIX_NATIVE` | [CeltiumGllessHint.java:1200](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1200>) |
| `CELTIUM_MATRIX_TRACE` | [Lane2ImmediateRecorder.java:6162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6162>) |
| `CELTIUM_MEMORY_PROFILE` | [VulkanContext.java:4857](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L4857>) |
| `CELTIUM_MENU_GROUP` | [Lane2ImmediateRecorder.java:20890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L20890>) |
| `CELTIUM_MENU_REGION_GATE` | [ValidationClient.java:13074](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13074>) |
| `CELTIUM_MERGE_CENSUS` | [Lane2ImmediateRecorder.java:21523](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21523>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1859>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:2183](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2183>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:2354](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2354>) |
| `CELTIUM_MIRROR_DEFERRED` | [VulkanTextureRegistry.java:219](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L219>) |
| `CELTIUM_MIRROR_DRAW` | [Lane2ImmediateRecorder.java:20900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L20900>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3730](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3730>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3744](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3744>) |
| `CELTIUM_MIRROR_MATERIALIZED` | [VulkanTextureRegistry.java:1143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1143>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:2006](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2006>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:2298](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2298>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:2437](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2437>) |
| `CELTIUM_MIRROR_RECREATE` | [VulkanGlTexture.java:3296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3296>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:2226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2226>) |
| `CELTIUM_MIRROR_UPLOAD` | [VulkanGlTexture.java:1763](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1763>) |
| `CELTIUM_MODELVIEW_EXACT` | [BackendDiagnostics.java:1348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1348>) |
| `CELTIUM_MODEL_BOUNDS` | [ModelReplay.java:388](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L388>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L439>) |
| `CELTIUM_MODEL_ENQ` | [ModelReplay.java:375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L375>) |
| `CELTIUM_MODEL_ENTRY` | [ModelReplay.java:76](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L76>) |
| `CELTIUM_MODEL_FOG_TEST` | [ValidationClient.java:7309](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7309>) |
| `CELTIUM_MODEL_GROUPS` | [CeltiumModelBatchGroups.java:94](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumModelBatchGroups.java#L94>) |
| `CELTIUM_MODEL_INSTANCES` | [ModelReplay.java:527](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L527>), [ModelReplay.java:563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L563>) |
| `CELTIUM_MODEL_LAYER` | [ModelReplay.java:274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L274>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIGHT` | [ModelReplay.java:599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L599>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:370](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L370>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L348>) |
| `CELTIUM_MODEL_MATRIX` | [ModelReplay.java:296](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L296>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:1362](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1362>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:1322](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L1322>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:360](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L360>), [ModelReplay.java:163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L163>) |
| `CELTIUM_MODEL_OFFTARGET` | [ModelReplay.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L100>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [ModelReplay.java:107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L107>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [ModelReplay.java:471](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L471>) |
| `CELTIUM_MODEL_PHASE` | [PatchGroups.java:231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java#L231>), [ModelPhaseTrace.java:168](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L168>), [ModelPhaseTrace.java:170](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L170>), [ModelPhaseTrace.java:172](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L172>), [ModelPhaseTrace.java:174](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L174>), [ModelPhaseTrace.java:176](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L176>), [ModelPhaseTrace.java:178](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L178>), [ModelPhaseTrace.java:180](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L180>), [ModelPhaseTrace.java:182](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L182>), [ModelPhaseTrace.java:184](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L184>), [ModelPhaseTrace.java:186](<../forge122/src/main/java/org/taumc/celeritas/impl/model/ModelPhaseTrace.java#L186>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L303>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:485](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L485>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L478>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L498>) |
| `CELTIUM_MODEL_RECORDED_REPLAY` | [ModelReplay.java:151](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L151>) |
| `CELTIUM_MODEL_RECORDS` | [VulkanModelInstances.java:368](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L368>) |
| `CELTIUM_MODEL_RECORDS_GROW` | [VulkanModelInstances.java:328](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L328>) |
| `CELTIUM_MODEL_RECORDS_GROW_FAILED` | [VulkanModelInstances.java:340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelInstances.java#L340>) |
| `CELTIUM_MODEL_STATE` | [ModelReplay.java:286](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java#L286>) |
| `CELTIUM_MOD_SHADERC_TIMEOUT` | [ModShaderCompiler.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModShaderCompiler.java#L152>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:13836](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13836>) |
| `CELTIUM_MULT_MATRIX` | [DirectGlCaptureEntry.java:2186](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L2186>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:3986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3986>) |
| `CELTIUM_NATIVE_DRAW_REFUSED` | [CeltiumNativeDraw.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java#L54>) |
| `CELTIUM_NATIVE_PASS` | [NativePassRegistry.java:302](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativePassRegistry.java#L302>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:368](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L368>), [WorldVertexBufferUploaderMixin.java:381](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L381>) |
| `CELTIUM_NULL_RENDERER` | [NullRenderer.java:64](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L64>), [NullRenderer.java:73](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NullRenderer.java#L73>) |
| `CELTIUM_OOD` | [Lane2ImmediateRecorder.java:8514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8514>) |
| `CELTIUM_OPAQUE_COPY_PROBE` | [RawFboIdentityProbe.java:347](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L347>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:442](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L442>) |
| `CELTIUM_ORTHO` | [Lane2ImmediateRecorder.java:5871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L5871>) |
| `CELTIUM_OTHER_SPLIT` | [FrameBudgetTimings.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L638>), [FrameBudgetTimings.java:653](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L653>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L68>), [ClientArrayBufferOwnershipMixin.java:80](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L80>), [ClientArrayBufferOwnershipMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L89>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [Lane2ImmediateRecorder.java:17933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17933>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [Lane2ImmediateRecorder.java:15817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15817>) |
| `CELTIUM_PAIR_SKIP` | [PresentSwapchain.java:1591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L1591>) |
| `CELTIUM_PAIR_WINDOW_PRESERVE_DEPTH` | [Lane2ImmediateRecorder.java:15563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15563>) |
| `CELTIUM_PASS_GPU` | [GpuPassTimingReport.java:83](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L83>) |
| `CELTIUM_PASS_GPU_MARKS` | [GpuPassTimingReport.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GpuPassTimingReport.java#L86>) |
| `CELTIUM_PATCH_ESSENTIAL` | [PatchErrorRecorder.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L98>) |
| `CELTIUM_PATCH_IGNORED` | [PatchLedger.java:177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L177>), [PatchLedger.java:181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L181>), [PatchLedger.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L184>), [PatchLedger.java:201](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L201>), [PatchLedger.java:207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L207>) |
| `CELTIUM_PATCH_QUARANTINED` | [PatchErrorRecorder.java:110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L110>) |
| `CELTIUM_PATCH_RECORD_FAILED` | [PatchErrorRecorder.java:114](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java#L114>) |
| `CELTIUM_PATCH_REGISTERED` | [CeleritasVintageMixinPlugin.java:211](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L211>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:2029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L2029>) |
| `CELTIUM_PATCH_SKIPPED` | [PatchLedger.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L269>), [PatchLedger.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L272>), [PatchLedger.java:275](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L275>) |
| `CELTIUM_PATCH_TABLE` | [PatchLedger.java:305](<../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchLedger.java#L305>), [CeleritasVintageMixinPlugin.java:166](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L166>), [CeleritasVintageMixinPlugin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L173>), [CeleritasVintageMixinPlugin.java:219](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L219>) |
| `CELTIUM_PBO_REFUSED` | [DirectGlCaptureEntry.java:1383](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1383>), [TextureCaptureStore.java:920](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCaptureStore.java#L920>) |
| `CELTIUM_PBO_UPLOAD` | [TextureCaptureStore.java:718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCaptureStore.java#L718>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:9761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9761>), [ValidationClient.java:9905](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9905>) |
| `CELTIUM_PIPELINE_CACHE` | [VulkanContext.java:3678](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3678>), [VulkanContext.java:3697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3697>), [VulkanContext.java:3733](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3733>) |
| `CELTIUM_PIPELINE_CREATE` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [VulkanContext.java:3424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3424>) |
| `CELTIUM_PIPELINE_CREATE_CENSUS` | [PipelineCreateCensus.java:175](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PipelineCreateCensus.java#L175>) |
| `CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING` | [VulkanContext.java:5476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5476>) |
| `CELTIUM_PIPELINE_SLOW` | [VulkanContext.java:3433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3433>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:567](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L567>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:548](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L548>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:538](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L538>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:6877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6877>) |
| `CELTIUM_POW_GUARD_CENSUS` | [ModGlslTranslator.java:2077](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/shader/ModGlslTranslator.java#L2077>) |
| `CELTIUM_PRESENT` | [CeltiumLogLimiter.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L45>), [PresentSwapchain.java:741](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L741>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [PresentSwapchain.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L108>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:12493](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L12493>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:5310](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5310>) |
| `CELTIUM_PRESENT_DIAGNOSTICS_FAILED` | [VulkanSwapchain.java:2044](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2044>) |
| `CELTIUM_PRESENT_INTERVAL` | [FrameBudgetTimings.java:412](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L412>) |
| `CELTIUM_PRESENT_INTERVAL_SUMMARY` | [FrameBudgetTimings.java:722](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameBudgetTimings.java#L722>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3128>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:192](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L192>) |
| `CELTIUM_PRESENT_PATCH` | [PresentSwapchain.java:863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L863>) |
| `CELTIUM_PRESENT_SLOW` | [PresentSwapchain.java:710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L710>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:1464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1464>) |
| `CELTIUM_PREWARM_TEARDOWN` | [VulkanContext.java:3791](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3791>) |
| `CELTIUM_PROBE_BORROW` | [HighlightProbe.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L164>) |
| `CELTIUM_PROBE_DRAW` | [HighlightProbe.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L126>) |
| `CELTIUM_PROBE_POISON` | [HighlightProbe.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/HighlightProbe.java#L97>) |
| `CELTIUM_PROJ_PROBE` | [Lane2ImmediateRecorder.java:16180](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16180>) |
| `CELTIUM_RAW_ATTACH` | [CeltiumGllessHint.java:1960](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1960>) |
| `CELTIUM_RAW_BIND_GAME_FRAMEBUFFER` | [Lane2ImmediateRecorder.java:6992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L6992>) |
| `CELTIUM_RAW_BIND_REGISTER` | [Lane2ImmediateRecorder.java:7016](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7016>) |
| `CELTIUM_RAW_DEPTH_REGISTER` | [Lane2ImmediateRecorder.java:7083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7083>) |
| `CELTIUM_RAW_FBO` | [DirectGlCaptureEntry.java:2282](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L2282>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [Lane2ImmediateRecorder.java:7719](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7719>), [Lane2ImmediateRecorder.java:7746](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7746>) |
| `CELTIUM_RAW_FBO_DEPTH` | [DirectGlCaptureEntry.java:2249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L2249>) |
| `CELTIUM_RAW_FBO_ID_PROBE` | [RawFboIdentityProbe.java:38](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L38>), [RawFboIdentityProbe.java:272](<../validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java#L272>) |
| `CELTIUM_RAW_FBO_LEVEL` | [DirectGlCaptureEntry.java:2237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L2237>) |
| `CELTIUM_READBACK_PHASES` | [VulkanSwapchain.java:808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L808>) |
| `CELTIUM_READBACK_QUARANTINED` | [VulkanContext.java:274](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L274>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1710>) |
| `CELTIUM_READ_PIXELS` | [DirectGlCaptureEntry.java:1394](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1394>), [DirectGlCaptureEntry.java:1403](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1403>), [TextureCopyService.java:1222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1222>) |
| `CELTIUM_READ_PIXELS_ENTRY` | [DirectGlCaptureEntry.java:1375](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1375>) |
| `CELTIUM_READ_PIXELS_WINDOW` | [TextureCopyService.java:1214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1214>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:1082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1082>) |
| `CELTIUM_READ_VIRGIN_PAIR` | [TextureCopyService.java:1069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1069>) |
| `CELTIUM_RECORD` | [BackendDiagnostics.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L43>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:277](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L277>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTextureRecordsPipeline.java:781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L781>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:398](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L398>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:4148](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4148>) |
| `CELTIUM_REPLACE_DROP` | [Lane2ImmediateRecorder.java:13293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13293>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:9063](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9063>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:9210](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9210>), [ValidationClient.java:9211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9211>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:9152](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9152>), [ValidationClient.java:9186](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9186>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:9313](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9313>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:9213](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9213>), [ValidationClient.java:9215](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9215>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:5163](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5163>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:5168](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5168>) |
| `CELTIUM_RETAINED_CENSUS` | [FrameStatsReport.java:199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java#L199>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:3280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3280>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2867](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2867>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2930>) |
| `CELTIUM_SDL_EVENT` | [CeltiumSdlEventTrace.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L35>), [CeltiumSdlEventTrace.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumSdlEventTrace.java#L40>) |
| `CELTIUM_SDL_LOG` | [CeltiumSdlLogTransformer.java:70](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L70>), [CeltiumSdlLogTransformer.java:75](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumSdlLogTransformer.java#L75>) |
| `CELTIUM_SHADER_MODULE` | [SpirvShaders.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/SpirvShaders.java#L212>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [Lane2ImmediateRecorder.java:10968](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10968>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [Lane2ImmediateRecorder.java:10977](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L10977>) |
| `CELTIUM_SHADOW_FRUSTUM_INIT_CACHE` | [DemonicaShadowFrustumInitCacheMixin.java:100](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/DemonicaShadowFrustumInitCacheMixin.java#L100>) |
| `CELTIUM_SHADOW_STAGE` | [ValidationClient.java:13882](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L13882>) |
| `CELTIUM_SHARED_DEPTH` | [Lane2ImmediateRecorder.java:15340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15340>) |
| `CELTIUM_SHARED_DEPTH_CLEAR_REFUSED` | [Lane2ImmediateRecorder.java:17047](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17047>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1447>), [VulkanGlTexture.java:1487](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1487>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L664>), [VulkanGlTexture.java:756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L756>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:716](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L716>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:789](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L789>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L932>) |
| `CELTIUM_SIGN_TEXT_CACHE` | [SignTextCache.java:57](<../forge122/src/main/java/org/taumc/celeritas/impl/render/producer/SignTextCache.java#L57>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:1231](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1231>) |
| `CELTIUM_SKY_DRAW` | [Lane2ImmediateRecorder.java:21290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L21290>) |
| `CELTIUM_SKY_FAN` | [Lane2ImmediateRecorder.java:8639](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8639>) |
| `CELTIUM_SKY_FRAME` | [Lane2ImmediateRecorder.java:8626](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8626>) |
| `CELTIUM_SKY_FRAME_PROBE` | [Lane2ImmediateRecorder.java:8581](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8581>) |
| `CELTIUM_SKY_GROUP` | [Lane2ImmediateRecorder.java:8650](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L8650>) |
| `CELTIUM_SKY_PRESENT_PROBE` | [PresentSwapchain.java:913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L913>) |
| `CELTIUM_SLOTDUMP` | [VulkanTextureRegistry.java:447](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L447>), [VulkanTextureRegistry.java:454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L454>), [VulkanTextureRegistry.java:547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L547>) |
| `CELTIUM_SLOT_WAIT` | [FrameCoordinator.java:254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L254>) |
| `CELTIUM_SPLASH_FLIP` | [Lane2ImmediateRecorder.java:13554](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13554>) |
| `CELTIUM_SPLASH_HANDLES` | [Lane2ImmediateRecorder.java:13667](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13667>), [Lane2ImmediateRecorder.java:13672](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13672>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:327](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L327>), [MinecraftMixin.java:345](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L345>), [MinecraftMixin.java:361](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L361>), [MinecraftMixin.java:365](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L365>), [ValidationClient.java:9550](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9550>) |
| `CELTIUM_SPLASH_PAIRPOST` | [Lane2ImmediateRecorder.java:13580](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13580>) |
| `CELTIUM_SPLASH_PROBE` | [Lane2ImmediateRecorder.java:13734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13734>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:3567](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3567>), [VulkanGlTexture.java:3684](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3684>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:883](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L883>) |
| `CELTIUM_STAGE_QUARANTINED` | [StageGate.java:195](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/StageGate.java#L195>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:4528](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4528>) |
| `CELTIUM_STALE_COLOR_TEXTURE` | [Lane2ImmediateRecorder.java:7795](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7795>) |
| `CELTIUM_STALL` | [FrameCoordinator.java:1074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L1074>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SURFACE_WAYLAND` | [VulkanSwapchain.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1017>) |
| `CELTIUM_SURFACE_WIN32` | [VulkanSwapchain.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L957>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:3237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3237>) |
| `CELTIUM_SWAPCHAIN_ACQUIRE_ABANDONED` | [VulkanSwapchain.java:2778](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2778>) |
| `CELTIUM_SWAPCHAIN_DRAIN_UNCONFIRMED` | [VulkanSwapchain.java:2807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L2807>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [PresentSwapchain.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java#L318>) |
| `CELTIUM_SWAPCHAIN_IMAGE_INIT` | [VulkanSwapchain.java:3398](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3398>), [VulkanSwapchain.java:3410](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3410>), [VulkanSwapchain.java:3422](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3422>) |
| `CELTIUM_SWAPCHAIN_INIT` | [VulkanSwapchain.java:3293](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L3293>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:643](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L643>) |
| `CELTIUM_SWAPPOINT_EXCEPTION` | [VulkanBackendAdapter.java:693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L693>) |
| `CELTIUM_TARGET_FORMAT` | [TargetFormatCensus.java:269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetFormatCensus.java#L269>) |
| `CELTIUM_TARGET_IMAGE` | [Lane2ImmediateRecorder.java:7560](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L7560>) |
| `CELTIUM_TEARDOWN` | [VulkanBackendAdapter.java:1214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java#L1214>), [VulkanContext.java:3160](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3160>), [VulkanContext.java:3244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3244>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET` | [Lane2ImmediateRecorder.java:16986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16986>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_DRAW` | [Lane2ImmediateRecorder.java:17225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L17225>) |
| `CELTIUM_TERRAIN_DEPTH_TARGET_REFUSED` | [Lane2ImmediateRecorder.java:16954](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16954>), [Lane2ImmediateRecorder.java:16971](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16971>), [Lane2ImmediateRecorder.java:16979](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L16979>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1775](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1775>) |
| `CELTIUM_TERRAIN_STATS` | [TerrainStatisticsReport.java:169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainStatisticsReport.java#L169>) |
| `CELTIUM_TEXTURE_DELETED` | [CaptureUploadContext.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L223>) |
| `CELTIUM_TEXTURE_DIAG` | [CopyReadSide.java:1285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java#L1285>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:98](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L98>) |
| `CELTIUM_TEXTURE_IMAGE` | [BackendDiagnostics.java:1002](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1002>) |
| `CELTIUM_TEXTURE_RESPECIFIED` | [CaptureUploadContext.java:115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L115>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:944](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L944>) |
| `CELTIUM_TEXUNIT_ANSWER` | [DirectGlCaptureEntry.java:1279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L1279>) |
| `CELTIUM_TEX_COPY` | [CaptureUploadContext.java:294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CaptureUploadContext.java#L294>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L815>) |
| `CELTIUM_TEX_PARAM` | [TextureParameterRecord.java:156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureParameterRecord.java#L156>) |
| `CELTIUM_TEX_PARAM_QUERY` | [TextureParameterRecord.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureParameterRecord.java#L287>) |
| `CELTIUM_TEX_VERTICES` | [BackendDiagnostics.java:1036](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1036>) |
| `CELTIUM_TICK_SPLIT` | [TickTimings.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L361>), [TickTimings.java:376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L376>) |
| `CELTIUM_TICK_SPLIT2` | [TickTimings.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L365>), [TickTimings.java:419](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TickTimings.java#L419>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:1485](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1485>) |
| `CELTIUM_UNIT_BIND` | [Lane2ImmediateState.java:984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/lane2/Lane2ImmediateState.java#L984>) |
| `CELTIUM_UNIT_SWITCH` | [Lane2ImmediateState.java:1126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/lane2/Lane2ImmediateState.java#L1126>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [DirectGlCaptureEntry.java:277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java#L277>) |
| `CELTIUM_UNMAPPED` | [Lane2ImmediateRecorder.java:9889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9889>), [WorldVertexBufferUploaderMixin.java:354](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L354>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [Lane2ImmediateRecorder.java:9890](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9890>), [WorldVertexBufferUploaderMixin.java:798](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L798>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:558](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L558>) |
| `CELTIUM_UNSUPPORTED_INSTANCED` | [CeltiumGllessHint.java:1614](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1614>), [CeltiumGllessNativeTransformer.java:830](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L830>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:181](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L181>) |
| `CELTIUM_UPLOAD_EXTENT` | [VulkanGlTexture.java:4349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L4349>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:5407](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L5407>) |
| `CELTIUM_UPSTREAM` | [UpstreamArrivalCensus.java:142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/UpstreamArrivalCensus.java#L142>) |
| `CELTIUM_VALIDATION` | [ValidationSupport.java:97](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ValidationSupport.java#L97>) |
| `CELTIUM_VALIDATION_UNAVAILABLE` | [VulkanContext.java:3837](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3837>), [VulkanContext.java:3846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3846>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeltiumF3Overlay.java:55](<../forge122/src/main/java/org/soutalab/celtium/forge/ui/CeltiumF3Overlay.java#L55>) |
| `CELTIUM_VULKAN` | [CeltiumPackCompiler.java:340](<../celtium-vulkan/src/main/java/org/soutalab/celtium/shader/compiler/CeltiumPackCompiler.java#L340>) |
| `CELTIUM_VULKAN_DEVICE_LOST` | [VulkanContext.java:2483](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2483>), [VulkanContext.java:2643](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2643>), [VulkanContext.java:3069](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3069>), [VulkanContext.java:3081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3081>), [VulkanContext.java:3128](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3128>), [VulkanContext.java:3136](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3136>), [VulkanContext.java:3184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3184>), [VulkanContext.java:3189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3189>), [VulkanContext.java:3205](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3205>), [VulkanContext.java:3213](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3213>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:3094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3094>), [VulkanContext.java:3117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3117>), [VulkanContext.java:3221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L3221>) |
| `CELTIUM_WARM_WINDOW` | [ValidationClient.java:4621](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4621>), [ValidationClient.java:4625](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4625>) |
| `CELTIUM_WINDOW` | [CeltiumWindow.java:69](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L69>), [CeltiumWindow.java:118](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L118>), [CeltiumWindow.java:125](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumWindow.java#L125>) |
| `CELTIUM_WINDOW_CENSUS` | [BackendDiagnostics.java:1071](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1071>) |
| `CELTIUM_WINDOW_DESCRIPTORS` | [BackendDiagnostics.java:1310](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1310>) |
| `CELTIUM_WINDOW_DRAW` | [BackendDiagnostics.java:1084](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1084>), [Lane2ImmediateRecorder.java:15777](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L15777>) |
| `CELTIUM_WINDOW_GL` | [CeltiumGllessHint.java:1393](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1393>), [CeltiumGllessHint.java:1491](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1491>) |
| `CELTIUM_WINDOW_IMAGES` | [BackendDiagnostics.java:1308](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1308>) |
| `CELTIUM_WINDOW_IMAGE_MEANS` | [BackendDiagnostics.java:1306](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1306>) |
| `CELTIUM_WINDOW_ORDER` | [Lane2ImmediateRecorder.java:13464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L13464>) |
| `CELTIUM_WINDOW_PIPELINES` | [BackendDiagnostics.java:1346](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1346>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:566](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L566>), [MinecraftMixin.java:572](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L572>), [MinecraftMixin.java:592](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L592>) |
| `CELTIUM_WINDOW_SITE` | [Lane2ImmediateRecorder.java:19237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L19237>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:9276](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9276>) |
| `CELTIUM_WINDOW_SPLIT` | [Lane2ImmediateRecorder.java:9187](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L9187>) |
| `CELTIUM_WINDOW_STATE` | [BackendDiagnostics.java:1378](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1378>) |
| `CELTIUM_WINDOW_TEXMATRIX` | [BackendDiagnostics.java:1288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1288>) |
| `CELTIUM_WINDOW_TILE_OFFSETS` | [BackendDiagnostics.java:1268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1268>) |
| `CELTIUM_WINDOW_VERTEX_ALPHA` | [BackendDiagnostics.java:1273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1273>) |
| `CELTIUM_WINDOW_VERTICES` | [BackendDiagnostics.java:1269](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java#L1269>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_MATRIX` | [Lane2ImmediateRecorder.java:4986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java#L4986>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:9037](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9037>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2673>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemRecordsPipeline.java:421](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemRecordsPipeline.java#L421>), [VulkanImmediateTextureRecordsPipeline.java:830](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java#L830>) |
