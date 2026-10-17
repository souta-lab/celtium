# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **338 個**／文字列内マーカー **297 個**。
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
| [celtium.debug](#group-celtium-debug) | 3 |
| [celtium.droppedItemRange](#group-celtium-droppedItemRange) | 1 |
| [celtium.droppedItemRangeBlocks](#group-celtium-droppedItemRangeBlocks) | 1 |
| [celtium.droppedItemRangeSoftCap](#group-celtium-droppedItemRangeSoftCap) | 1 |
| [celtium.entityLightCache](#group-celtium-entityLightCache) | 1 |
| [celtium.explicitGc](#group-celtium-explicitGc) | 1 |
| [celtium.f3](#group-celtium-f3) | 2 |
| [celtium.font](#group-celtium-font) | 10 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 1 |
| [celtium.glless](#group-celtium-glless) | 1 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 3 |
| [celtium.validation](#group-celtium-validation) | 127 |
| [celtium.vulkan](#group-celtium-vulkan) | 166 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L253>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:2490](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2490>) |
| `celtium.debug.listInputLog` | `Integer.getInteger` | [RenderSectionManager.java:445](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L445>) |
| `celtium.debug.listSizeLog` | `Integer.getInteger` | [RenderSectionManager.java:449](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L449>) |

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
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:182](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L182>) |
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:327](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L327>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:194](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L194>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:235](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L235>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:204](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L204>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L233>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:225](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L225>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:249](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L249>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:216](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L216>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:99](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:809](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L809>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:5797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5797>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:2020](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2020>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:5982](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5982>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:1978](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1978>), [CeltiumGlStub.java:2397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2397>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:2392](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2392>) |

<a id="group-celtium-itemMeshCache"></a>
## `celtium.itemMeshCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `System.getProperty` | [RenderItemMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L68>) |

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

<a id="group-celtium-stub"></a>
## `celtium.stub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:4911](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4911>) |

<a id="group-celtium-terrain"></a>
## `celtium.terrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.terrain.brightnessDiagnostics` | `Boolean.getBoolean` | [VintageBlockRenderer.java:51](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L51>) |
| `celtium.terrain.forceCompactFormat` | `Boolean.getBoolean` | [CeleritasWorldRenderer.java:244](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L244>) |
| `celtium.terrain.noShade` | `Boolean.getBoolean` | [VintageDiffuseProvider.java:18](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/VintageDiffuseProvider.java#L18>) |

<a id="group-celtium-validation"></a>
## `celtium.validation.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.validation` | `Boolean.getBoolean` | [VulkanDiagnostics.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L24>), [ValidationClient.java:2894](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2894>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1520](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1520>), [ValidationClient.java:3732](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3732>), [ValidationClient.java:9087](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9087>), [ValidationClient.java:10187](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10187>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1514](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1514>), [ValidationClient.java:1665](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1665>), [ValidationClient.java:9080](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9080>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1691](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1691>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1679](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1679>), [ValidationClient.java:9081](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9081>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1698](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1698>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:5283](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5283>), [ValidationClient.java:5297](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5297>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:5290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5290>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1192](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1192>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:985](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L985>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1188](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1188>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1189](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1189>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1191](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1191>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1190](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1190>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:288](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L288>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:5563](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5563>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L285>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:274](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L274>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:297](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L297>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:262](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L262>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:752](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L752>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:339](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L339>), [ValidationClient.java:1908](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1908>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:2897](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2897>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:1978](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1978>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:669](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L669>), [ValidationClient.java:1146](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1146>), [ValidationClient.java:9104](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9104>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:668](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L668>), [ValidationClient.java:1147](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1147>), [ValidationClient.java:9105](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9105>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:676](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L676>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:678](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L678>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:816](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L816>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:801](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L801>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:1906](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1906>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2043](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2043>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2111](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2111>), [ValidationClient.java:5364](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5364>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:1904](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1904>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:1949](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1949>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [LoadingScreenRendererMixin.java:46](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L46>), [ValidationClient.java:7030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7030>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3031](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3031>), [ValidationClient.java:3051](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3051>), [ValidationClient.java:5231](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5231>), [ValidationClient.java:6971](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6971>), [ValidationClient.java:6972](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6972>), [ValidationClient.java:7001](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7001>), [ValidationClient.java:7031](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7031>), [ValidationClient.java:7361](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7361>), [ValidationClient.java:7762](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7762>), [ValidationClient.java:7845](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7845>), [ValidationClient.java:8680](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8680>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:911](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L911>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:5232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5232>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:921](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L921>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:5390](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5390>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3652](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3652>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4089](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4089>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:5738](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5738>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:5138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5138>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1090](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1090>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1222](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1222>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1215](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1215>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1217](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1217>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1224](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1224>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1230](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1230>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1232>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1227](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1227>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1220](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1220>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3786](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3786>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:5331](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5331>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1068](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1068>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1070](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1070>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:74](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:80](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1072](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1072>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:4887](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4887>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:745](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L745>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3776](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3776>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3674](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3674>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:868](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L868>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3766](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3766>), [ValidationClient.java:3793](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3793>), [ValidationClient.java:5363](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5363>), [ValidationClient.java:5555](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5555>), [ValidationClient.java:5601](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5601>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:5068](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5068>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1541](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1541>), [ValidationClient.java:5079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5079>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1538](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1538>), [ValidationClient.java:4485](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4485>), [ValidationClient.java:4999](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4999>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:5020](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5020>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3284](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3284>), [ValidationClient.java:3629](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3629>), [ValidationClient.java:3767](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3767>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:5372](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5372>), [ValidationClient.java:5373](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5373>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1547](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1547>), [ValidationClient.java:5126](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5126>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:10193](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10193>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:947](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L947>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2078](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2078>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:794](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L794>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:5644](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5644>), [ValidationClient.java:5648](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5648>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:5313](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5313>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:238](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:4867](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4867>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:5820](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5820>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:3151](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3151>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:4858](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4858>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:3842](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3842>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:3825](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3825>), [ValidationClient.java:5582](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5582>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3709](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3709>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:5145](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5145>), [ValidationClient.java:9208](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9208>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:871](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L871>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:7019](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7019>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:887](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L887>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:950](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L950>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:900](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L900>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:6764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6764>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:872](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L872>), [ValidationClient.java:5160](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5160>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:898](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L898>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:849](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L849>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:971](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L971>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1629](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1629>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1112](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1112>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:4917](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4917>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:1989](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1989>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:5108](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5108>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:4942](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4942>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:3835](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3835>), [ValidationClient.java:5584](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5584>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:4853](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4853>), [ValidationClient.java:5888](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5888>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:3285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3285>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:5241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5241>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2167](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2167>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2234](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2234>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:5378](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5378>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3019](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3019>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:5714](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5714>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:7747](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7747>), [ValidationClient.java:8041](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8041>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:5616](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5616>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:4362](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4362>), [ValidationClient.java:4840](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4840>), [ValidationClient.java:5880](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5880>), [ValidationClient.java:5914](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5914>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:4363](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4363>), [ValidationClient.java:4841](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4841>), [ValidationClient.java:5881](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5881>), [ValidationClient.java:5915](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5915>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:5174](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5174>), [ValidationClient.java:5306](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5306>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1513](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1513>), [ValidationClient.java:1630](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1630>), [ValidationClient.java:9079](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9079>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:3406](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3406>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:83](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13303>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2076](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2076>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2022](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2022>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:591](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L591>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L343>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2355](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2355>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [VulkanContext.java:318](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L318>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2541>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13052](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13052>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2253>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13029](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13029>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2254](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2254>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1162](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1162>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1405>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2345](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2345>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:541](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L541>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L23>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1875](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1875>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1878](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1878>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1861>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1864>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:3130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3130>), [ValidationClient.java:5639](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5639>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:3142](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3142>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1856>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1858](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1858>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13413>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13408>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1638>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2341](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2341>), [ValidationClient.java:2428](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2428>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>), [VulkanTerrainGpuBackend.java:2433](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2433>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3019](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3019>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2676](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2676>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L189>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:411](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L411>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L184>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:9846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9846>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:9849](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9849>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9828](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9828>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12695](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12695>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:12687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12687>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:12691](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12691>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:225](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L225>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2250](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2250>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1066](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1066>) |
| `celtium.vulkan.glless` | `Boolean.getBoolean`, `System.getProperty` | [MappedStagingBuffer.java:62](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L62>), [CeleritasLoadingPlugin.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L32>), [CeltiumGllessHint.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L32>), [CeltiumGllessHint.java:772](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L772>), [CeltiumGllessSplashTransformer.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L32>), [CeltiumProgressTransformer.java:38](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumProgressTransformer.java#L38>), [CeltiumLoadingProgress.java:65](<../forge122/src/main/java/org/taumc/celeritas/impl/gui/CeltiumLoadingProgress.java#L65>), [CeleritasVintageMixinPlugin.java:23](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23>), [MinecraftMixin.java:36](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L36>) |
| `celtium.vulkan.gllessFrameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L413>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1748](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1748>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1788](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1788>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:1750](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1750>), [VulkanTerrainGpuBackend.java:1766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1766>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1804](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1804>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1798](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1798>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2056>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:131](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L131>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2048>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1184>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:599](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L599>), [VulkanTerrainGpuBackend.java:2247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2247>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:2233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2233>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1821>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1831](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1831>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:946](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L946>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2037>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2039](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2039>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2041>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2239](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2239>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2844](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2844>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2856>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2862>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2130](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2130>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L548>) |
| `celtium.vulkan.immediateWindowSamples` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2442>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2199>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2237>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L291>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1507](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1507>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1654](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1654>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13284>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1734>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L649>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L143>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2536](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2536>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L121>), [VulkanTerrainGpuBackend.java:7648](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7648>), [VulkanTerrainGpuBackend.java:7758](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7758>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L141>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2399](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2399>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1000](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1000>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13406>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13393](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13393>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11397>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:152](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L152>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:1163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1163>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:9882](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9882>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9945](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9945>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2108>), [VulkanTerrainGpuBackend.java:9906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9906>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:72](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L72>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:86](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L86>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2028>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:9932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9932>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:1622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1622>), [VulkanContext.java:1903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1903>), [VulkanContext.java:2156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2156>), [VulkanSwapchain.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L513>), [VulkanTerrainGpuBackend.java:2226](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2226>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L182>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:223](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L223>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9925](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9925>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:9935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9935>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:9938](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9938>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L221>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2252](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2252>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:227](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L227>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L173>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9914>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1657](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1657>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2246](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2246>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2248>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:1699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1699>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2649](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2649>) |
| `celtium.vulkan.skipColorReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126>) |
| `celtium.vulkan.skipDepthReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2224](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2224>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:9908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9908>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:736](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L736>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:2960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2960>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:2981](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2981>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:1621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1621>), [VulkanContext.java:1902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1902>), [VulkanContext.java:2155](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2155>), [VulkanSwapchain.java:512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L512>), [VulkanTerrainGpuBackend.java:13863](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13863>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4706](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4706>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4697](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4697>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L258>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L112>), [VulkanTerrainGpuBackend.java:1659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1659>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:9821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9821>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11376>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:393](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L393>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:1606](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1606>), [VulkanDiagnostics.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L25>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L803>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10273>) |
| `celtium.vulkan.windowCopyBlit` | `Boolean.getBoolean` | [TextureCopyService.java:169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L169>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1881](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1881>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1884](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1884>) |
| `celtium.vulkan.withoutGlIdentity` | `Boolean.getBoolean` | [VulkanContext.java:2756](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2756>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L295>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L42>), [VulkanTerrainFactory.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L108>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:463](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L463>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:6442](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6442>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:141](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L141>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3764>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:3861](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3861>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:3258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3258>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:257](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L257>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L638>), [VulkanTerrainPipeline.java:679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L679>), [VulkanTerrainPipeline.java:699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L699>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L295>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BIG_MIRROR` | [VulkanTextureRegistry.java:1874](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1874>) |
| `CELTIUM_BIND_CALLER` | [GlStateManagerMixin.java:237](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L237>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:767](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L767>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:192](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L192>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:7782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7782>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:755](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L755>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:114](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L114>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:102](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L102>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1247](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1247>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1267>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:2482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2482>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [VulkanTerrainGpuBackend.java:13043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13043>), [VulkanTerrainGpuBackend.java:13085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13085>), [VulkanTerrainGpuBackend.java:13115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13115>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [VulkanTerrainGpuBackend.java:11812](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11812>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanTerrainGpuBackend.java:11843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11843>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:8675](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8675>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L89>) |
| `CELTIUM_CHURN` | [ValidationClient.java:10062](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10062>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:10843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10843>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:5773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5773>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2580](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2580>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:343](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L343>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:385](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L385>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:348](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L348>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1061>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:506](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L506>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:723](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L723>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L42>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:1916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1916>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:14951](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14951>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:3236](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3236>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:3253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3253>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:3333](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3333>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:5506](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5506>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:5500](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5500>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:3344](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3344>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:6427](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6427>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:3399](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3399>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:3355](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3355>), [ValidationClient.java:6023](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6023>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:3400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3400>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L369>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:787](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L787>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L315>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L121>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L75>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L91>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L424>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L397>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L343>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L770>), [CeltiumGlStub.java:3259](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3259>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:5386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5386>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:5874](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5874>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:5441](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5441>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:10498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10498>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:14425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14425>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2054](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2054>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:104](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L104>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3670](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3670>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:3292](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3292>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:3302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3302>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:5908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5908>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:5916](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5916>) |
| `CELTIUM_ENV_COMBINE` | [VulkanTerrainGpuBackend.java:3664](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3664>) |
| `CELTIUM_ENV_SEEN` | [VulkanTerrainGpuBackend.java:3674](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3674>) |
| `CELTIUM_ENV_WRITE` | [CeltiumGlStub.java:4663](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4663>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L288>), [VulkanTextureRegistry.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L327>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:173](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L173>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:120](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L120>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:4807](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4807>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:4846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4846>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:14943](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14943>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:4787](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4787>), [ValidationClient.java:4801](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4801>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3011](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3011>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:5495](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5495>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:5529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5529>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:5518](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5518>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L779>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L710>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:3033](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3033>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1192](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1192>), [CeltiumFontGlyphCapture.java:1326](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1326>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1167](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1167>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1308](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1308>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1385](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1385>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:409](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L409>), [CeltiumFontGlyphCapture.java:413](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L413>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:662](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L662>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:7572](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7572>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:5094](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5094>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:220](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L220>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L117>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L84>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:3913](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3913>) |
| `CELTIUM_GLLESS` | [CeltiumGlStub.java:720](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L720>), [CeltiumGlStub.java:723](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L723>), [CeleritasLoadingPlugin.java:43](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L43>), [CeltiumGllessHint.java:780](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L780>), [CeltiumGllessHint.java:784](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L784>), [CeltiumGllessNativeTransformer.java:89](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L89>), [CeltiumGllessSplashTransformer.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L54>), [CeltiumGllessSplashTransformer.java:69](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L69>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeltiumProgressTransformer.java:71](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumProgressTransformer.java#L71>), [CeleritasVintageMixinPlugin.java:58](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L58>), [CeleritasVintageMixinPlugin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L60>), [MinecraftMixin.java:297](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L297>), [MinecraftMixin.java:303](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L303>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:4218](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4218>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:4244](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4244>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:15276](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15276>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:3817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3817>) |
| `CELTIUM_GROUP_BOUNDS` | [VulkanTerrainGpuBackend.java:6712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6712>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:10470](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10470>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:14597](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14597>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:14635](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14635>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:7638](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7638>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:392](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L392>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:4605](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4605>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3699](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3699>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:3493](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3493>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2514](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2514>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:3209](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3209>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:352](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L352>), [VulkanImmediateTexturePipeline.java:588](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L588>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:8960](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8960>) |
| `CELTIUM_IMMEDIATE_FOG` | [VulkanTerrainGpuBackend.java:3133](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3133>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:3280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3280>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L174>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LEVEL0` | [VulkanGlTexture.java:1454](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1454>), [VulkanGlTexture.java:3486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3486>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L555>), [VulkanTextureRegistry.java:1473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1473>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:5498](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5498>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:7966](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7966>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:8017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8017>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:7996](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7996>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:8048](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8048>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:10333](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10333>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:5467](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5467>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:481](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L481>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:698](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L698>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L55>), [ValidationClient.java:7061](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7061>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:8944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8944>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1432>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1698](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1698>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1815>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:14164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14164>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:2986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2986>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3000](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3000>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1150>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1758](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1758>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:1897](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1897>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1327>) |
| `CELTIUM_MODEL_BOUNDS` | [VulkanTerrainGpuBackend.java:3725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3725>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:332](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L332>) |
| `CELTIUM_MODEL_ENQ` | [VulkanTerrainGpuBackend.java:3712](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3712>) |
| `CELTIUM_MODEL_LAYER` | [VulkanTerrainGpuBackend.java:11500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11500>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L387>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L365>) |
| `CELTIUM_MODEL_MATRIX` | [VulkanTerrainGpuBackend.java:11563](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11563>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:899](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L899>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:859](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L859>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:263](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L263>), [VulkanTerrainGpuBackend.java:11472](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11472>) |
| `CELTIUM_MODEL_OFFTARGET` | [VulkanTerrainGpuBackend.java:11429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11429>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [VulkanTerrainGpuBackend.java:11436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11436>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [VulkanTerrainGpuBackend.java:3799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3799>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L320>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L500>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L493>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L513>) |
| `CELTIUM_MODEL_STATE` | [VulkanTerrainGpuBackend.java:11512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11512>) |
| `CELTIUM_MODEL_TEXTURE` | [GlStateManagerMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L84>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:10211](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10211>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:3400](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3400>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:1816](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1816>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:759](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L759>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:274](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L274>), [WorldVertexBufferUploaderMixin.java:287](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L287>) |
| `CELTIUM_OFFTARGET_SELFTEST` | [VulkanOffscreenTarget.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanOffscreenTarget.java#L280>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:5001](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5001>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:4049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4049>) |
| `CELTIUM_OUTLINE_INPUT` | [VulkanTerrainGpuBackend.java:11864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11864>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L66>), [ClientArrayBufferOwnershipMixin.java:78](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L78>), [ClientArrayBufferOwnershipMixin.java:87](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L87>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:11145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11145>) |
| `CELTIUM_PAIR_LAYOUT` | [VulkanTerrainGpuBackend.java:12304](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12304>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:8803](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8803>) |
| `CELTIUM_PAIR_SKIP` | [VulkanTerrainGpuBackend.java:12240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12240>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:4893](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4893>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1170>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:7138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7138>), [ValidationClient.java:7278](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7278>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:378](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L378>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:360](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L360>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_POSTCHAIN` | [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L659>), [VulkanPostChain.java:688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L688>), [VulkanPostChain.java:692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L692>), [VulkanPostChain.java:1238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1238>), [VulkanPostChain.java:1243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1243>), [VulkanTerrainGpuBackend.java:11886](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11886>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:4885](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4885>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1349>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L782>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L921>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:12499](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12499>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:11026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11026>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:9038](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9038>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:3884](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3884>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:1622](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1622>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L148>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:12590](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12590>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:871](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L871>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:8903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8903>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:3908](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3908>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:4753](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4753>) |
| `CELTIUM_READBACK_REFRESH` | [VulkanTextureRegistry.java:1808](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1808>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1283>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L799>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L173>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L427>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:276](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L276>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3727](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3727>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:158](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L158>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:606](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L606>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:7428](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7428>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:6498](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6498>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:6645](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6645>), [ValidationClient.java:6646](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6646>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:6587](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6587>), [ValidationClient.java:6621](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6621>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:6748](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6748>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:6648](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6648>), [ValidationClient.java:6650](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6650>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:3465](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3465>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:3470](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3470>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2602](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2602>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2261](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2261>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2303](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2303>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:5933](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5933>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:5944](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5944>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1082](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1082>), [VulkanGlTexture.java:1117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1117>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L529>), [VulkanGlTexture.java:608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L608>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L577>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L632>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L731>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L492>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:5970](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5970>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:7594](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7594>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:7685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7685>), [VulkanTerrainGpuBackend.java:7692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7692>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:196](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L196>), [MinecraftMixin.java:201](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L201>), [MinecraftMixin.java:212](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L212>), [MinecraftMixin.java:216](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L216>), [ValidationClient.java:6969](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6969>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:7612](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7612>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:7749](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7749>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:2820](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2820>), [VulkanGlTexture.java:2941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2941>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:689](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L689>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3566](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3566>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:1630](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1630>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L123>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:1725](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1725>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:11110](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11110>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:421](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L421>) |
| `CELTIUM_TARGET_FOREIGN` | [VulkanTerrainGpuBackend.java:13482](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13482>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:4632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4632>) |
| `CELTIUM_TARGET_REFUSAL` | [VulkanTerrainGpuBackend.java:13469](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13469>), [VulkanTerrainGpuBackend.java:13489](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13489>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:10237](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10237>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1121>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:13630](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13630>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L41>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:686](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L686>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:5105](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5105>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:621](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L621>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:4840](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4840>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L776>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:5050](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5050>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:103](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L103>), [CeleritasWorldRenderer.java:117](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L117>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:394](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L394>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L656>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L796>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:2986](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2986>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:760](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L760>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:5763](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5763>), [WorldVertexBufferUploaderMixin.java:260](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L260>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:5764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5764>), [WorldVertexBufferUploaderMixin.java:527](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L527>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:370](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L370>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:144](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L144>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:2644](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2644>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:91](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L91>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:2877](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2877>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1061](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1061>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:1500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1500>), [VulkanContext.java:1522](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1522>) |
| `CELTIUM_WINDOW_DRAW` | [VulkanTerrainGpuBackend.java:8764](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8764>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:7532](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7532>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:372](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L372>), [MinecraftMixin.java:377](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L377>), [MinecraftMixin.java:396](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L396>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:6711](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6711>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:161](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L161>), [RenderSectionManager.java:163](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L163>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:169](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L169>), [RenderSectionManager.java:175](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L175>), [RenderSectionManager.java:178](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L178>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:265](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L265>), [SimpleWorldRenderer.java:271](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L271>), [SimpleWorldRenderer.java:274](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L274>), [SimpleWorldRenderer.java:277](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L277>), [SimpleWorldRenderer.java:284](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L284>), [SimpleWorldRenderer.java:291](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L291>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:3087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3087>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:6481](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6481>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2081](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2081>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L278>), [VulkanImmediateTexturePipeline.java:459](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L459>) |
