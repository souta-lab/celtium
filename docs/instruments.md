# Celtium 計器索引（自動生成）

生成: `python3 scripts/instrument-index.py`。手で編集しない。

システムプロパティの読み取りキー **347 個**／文字列内マーカー **308 個**。
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
| [celtium.fontAppend](#group-celtium-fontAppend) | 1 |
| [celtium.fontAtlas](#group-celtium-fontAtlas) | 2 |
| [celtium.fontGlyph](#group-celtium-fontGlyph) | 1 |
| [celtium.forceIdentityTexmat](#group-celtium-forceIdentityTexmat) | 1 |
| [celtium.glless](#group-celtium-glless) | 1 |
| [celtium.glstub](#group-celtium-glstub) | 6 |
| [celtium.itemMeshCache](#group-celtium-itemMeshCache) | 1 |
| [celtium.mirrorDump](#group-celtium-mirrorDump) | 1 |
| [celtium.parallelAtlas](#group-celtium-parallelAtlas) | 1 |
| [celtium.particleCache](#group-celtium-particleCache) | 1 |
| [celtium.particleLightCache](#group-celtium-particleLightCache) | 1 |
| [celtium.probe](#group-celtium-probe) | 5 |
| [celtium.stub](#group-celtium-stub) | 1 |
| [celtium.terrain](#group-celtium-terrain) | 3 |
| [celtium.validation](#group-celtium-validation) | 129 |
| [celtium.vulkan](#group-celtium-vulkan) | 168 |
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
| `celtium.clearlatch.lines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:267](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L267>) |

<a id="group-celtium-debug"></a>
## `celtium.debug.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.debug.fnCensus` | `Boolean.getBoolean` | [CeltiumGlStub.java:2511](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2511>) |
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
| `celtium.font.debugRgb` | `Integer.getInteger` | [CeltiumFontGlyphCapture.java:188](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L188>) |
| `celtium.font.fastPath` | `System.getProperty` | [CeltiumFontGlyphCapture.java:333](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L333>) |
| `celtium.font.isolation` | `System.getProperty` | [CeltiumFontGlyphCapture.java:200](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L200>) |
| `celtium.font.matrixProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:241](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L241>) |
| `celtium.font.mode` | `System.getProperty` | [CeltiumFontGlyphCapture.java:210](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L210>) |
| `celtium.font.retaining` | `System.getProperty` | [FontBatch.java:233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L233>) |
| `celtium.font.screenFill` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:231](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L231>) |
| `celtium.font.stateProbe` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:255](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L255>) |
| `celtium.font.winding` | `System.getProperty` | [CeltiumFontGlyphCapture.java:222](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L222>) |

<a id="group-celtium-fontAppend"></a>
## `celtium.fontAppend.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAppend.debug` | `Boolean.getBoolean` | [FontBatch.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L249>) |

<a id="group-celtium-fontAtlas"></a>
## `celtium.fontAtlas.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontAtlas` | `System.getProperty` | [CeltiumFontAtlas.java:46](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L46>) |
| `celtium.fontAtlas.dump` | `System.getProperty` | [CeltiumFontAtlas.java:189](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java#L189>) |

<a id="group-celtium-fontGlyph"></a>
## `celtium.fontGlyph.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.fontGlyph.debug` | `Boolean.getBoolean` | [CeltiumFontGlyphCapture.java:50](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L50>) |

<a id="group-celtium-forceIdentityTexmat"></a>
## `celtium.forceIdentityTexmat.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.forceIdentityTexmat` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:3589](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3589>) |

<a id="group-celtium-glless"></a>
## `celtium.glless.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glless.census` | `Boolean.getBoolean` | [CeltiumGllessNativeTransformer.java:99](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L99>) |

<a id="group-celtium-glstub"></a>
## `celtium.glstub.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.glstub.alphaFuncOverride` | `Integer.getInteger` | [CeltiumGlStub.java:815](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L815>) |
| `celtium.glstub.drawArrayProbeLines` | `Integer.getInteger` | [CeltiumGlStub.java:5864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5864>) |
| `celtium.glstub.ffm` | `System.getProperty` | [CeltiumGlStub.java:2041](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2041>) |
| `celtium.glstub.skyDegradedLines` | `Integer.getInteger` | [CeltiumGlStub.java:6049](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6049>) |
| `celtium.glstub.trace` | `Boolean.getBoolean` | [CeltiumGlStub.java:1999](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1999>), [CeltiumGlStub.java:2418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2418>) |
| `celtium.glstub.traceLimit` | `Integer.getInteger` | [CeltiumGlStub.java:2413](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2413>) |

<a id="group-celtium-itemMeshCache"></a>
## `celtium.itemMeshCache.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.itemMeshCache` | `System.getProperty` | [RenderItemMixin.java:68](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L68>) |

<a id="group-celtium-mirrorDump"></a>
## `celtium.mirrorDump.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.mirrorDump` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14167](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14167>) |

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
| `celtium.stub.texWrapClampControl` | `Boolean.getBoolean` | [CeltiumGlStub.java:4935](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4935>) |

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
| `celtium.validation` | `Boolean.getBoolean` | [VulkanDiagnostics.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L24>), [ValidationClient.java:2926](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2926>) |
| `celtium.validation.allBlocks` | `Boolean.getBoolean` | [ValidationClient.java:1546](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1546>), [ValidationClient.java:3764](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3764>), [ValidationClient.java:9202](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9202>), [ValidationClient.java:10302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10302>) |
| `celtium.validation.blockSweep` | `Integer.getInteger` | [ValidationClient.java:1540](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1540>), [ValidationClient.java:1691](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1691>), [ValidationClient.java:9195](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9195>) |
| `celtium.validation.blockSweepEvery` | `Integer.getInteger` | [ValidationClient.java:1717](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1717>) |
| `celtium.validation.blockSweepFrom` | `Integer.getInteger` | [ValidationClient.java:1705](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1705>), [ValidationClient.java:9196](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9196>) |
| `celtium.validation.blockSweepPerTick` | `Integer.getInteger` | [ValidationClient.java:1724](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1724>) |
| `celtium.validation.breakBlock` | `Boolean.getBoolean` | [ValidationClient.java:5398](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5398>), [ValidationClient.java:5412](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5412>) |
| `celtium.validation.breakSubjectOnly` | `Boolean.getBoolean` | [ValidationClient.java:5405](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5405>) |
| `celtium.validation.cameraPitch` | `System.getProperty` | [ValidationClient.java:1218](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1218>) |
| `celtium.validation.cameraSetTick` | `Integer.getInteger` | [ValidationClient.java:1011](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1011>) |
| `celtium.validation.cameraX` | `System.getProperty` | [ValidationClient.java:1214](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1214>) |
| `celtium.validation.cameraY` | `System.getProperty` | [ValidationClient.java:1215](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1215>) |
| `celtium.validation.cameraYaw` | `System.getProperty` | [ValidationClient.java:1217](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1217>) |
| `celtium.validation.cameraZ` | `System.getProperty` | [ValidationClient.java:1216](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1216>) |
| `celtium.validation.captureAtTotalWorldTime` | `Long.getLong` | [ValidationClient.java:250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L250>) |
| `celtium.validation.captureCondition` | `System.getProperty` | [ValidationClient.java:229](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L229>) |
| `celtium.validation.captureDeadlineTick` | `Integer.getInteger` | [ValidationClient.java:293](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L293>) |
| `celtium.validation.captureEntities` | `Boolean.getBoolean` | [ValidationClient.java:5678](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5678>) |
| `celtium.validation.captureMaxHeldBuilds` | `Integer.getInteger` | [ValidationClient.java:290](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L290>) |
| `celtium.validation.captureMaxP1` | `Integer.getInteger` | [ValidationClient.java:279](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L279>) |
| `celtium.validation.captureMinWorldFrames` | `Integer.getInteger` | [ValidationClient.java:302](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L302>) |
| `celtium.validation.captureSections` | `Integer.getInteger` | [ValidationClient.java:267](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L267>) |
| `celtium.validation.captureStableFrames` | `Integer.getInteger` | [ValidationClient.java:232](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L232>) |
| `celtium.validation.captureTrace` | `Boolean.getBoolean` | [ValidationClient.java:263](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L263>) |
| `celtium.validation.churnInterval` | `Integer.getInteger` | [ValidationClient.java:757](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L757>) |
| `celtium.validation.commandBufferTargets` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L353>), [ValidationClient.java:1934](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1934>) |
| `celtium.validation.compactVertexFormat` | `Boolean.getBoolean` | [ValidationClient.java:2929](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2929>) |
| `celtium.validation.compositeBlit` | `Boolean.getBoolean` | [ValidationClient.java:2004](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2004>) |
| `celtium.validation.dimension` | `Integer.getInteger` | [ValidationClient.java:674](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L674>), [ValidationClient.java:1172](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1172>), [ValidationClient.java:9219](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9219>) |
| `celtium.validation.dimensionAtTick` | `Integer.getInteger` | [ValidationClient.java:673](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L673>), [ValidationClient.java:1173](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1173>), [ValidationClient.java:9220](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9220>) |
| `celtium.validation.dimensionBackAtTick` | `Integer.getInteger` | [ValidationClient.java:681](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L681>) |
| `celtium.validation.dimensionReturn` | `Integer.getInteger` | [ValidationClient.java:683](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L683>) |
| `celtium.validation.dimensionSettleFrames` | `Integer.getInteger` | [ValidationClient.java:821](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L821>) |
| `celtium.validation.dimensionWaitUntilTick` | `Integer.getInteger` | [ValidationClient.java:806](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L806>) |
| `celtium.validation.drawBlockQuad` | `Boolean.getBoolean` | [ValidationClient.java:1932](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1932>) |
| `celtium.validation.drawMergedQuads` | `Boolean.getBoolean` | [ValidationClient.java:2069](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2069>) |
| `celtium.validation.drawTest` | `Boolean.getBoolean` | [ValidationClient.java:2137](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2137>), [ValidationClient.java:5479](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5479>) |
| `celtium.validation.drawTestQuad` | `Boolean.getBoolean` | [ValidationClient.java:1930](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1930>) |
| `celtium.validation.drawTestTargets` | `Boolean.getBoolean` | [ValidationClient.java:1975](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1975>) |
| `celtium.validation.dumpLoadingProgress` | `Boolean.getBoolean` | [LoadingScreenRendererMixin.java:46](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L46>), [ValidationClient.java:7145](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7145>) |
| `celtium.validation.dumpPresentedImage` | `Boolean.getBoolean` | [ValidationClient.java:3063](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3063>), [ValidationClient.java:3083](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3083>), [ValidationClient.java:5346](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5346>), [ValidationClient.java:7086](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7086>), [ValidationClient.java:7087](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7087>), [ValidationClient.java:7116](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7116>), [ValidationClient.java:7146](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7146>), [ValidationClient.java:7476](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7476>), [ValidationClient.java:7877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7877>), [ValidationClient.java:7960](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7960>), [ValidationClient.java:8795](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8795>) |
| `celtium.validation.dumpPresentedMenuTick` | `Integer.getInteger` | [ValidationClient.java:937](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L937>) |
| `celtium.validation.dumpPresentedTick` | `Integer.getInteger` | [ValidationClient.java:5347](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5347>) |
| `celtium.validation.dumpPresentedWorldLoadTick` | `Integer.getInteger` | [ValidationClient.java:947](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L947>) |
| `celtium.validation.emptyHand` | `Boolean.getBoolean` | [ValidationClient.java:5505](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5505>) |
| `celtium.validation.enchantItem` | `Boolean.getBoolean` | [ValidationClient.java:3684](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3684>) |
| `celtium.validation.expectVulkan` | `Boolean.getBoolean` | [ValidationClient.java:4121](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4121>) |
| `celtium.validation.expectVulkanTerrain` | `Boolean.getBoolean` | [ValidationClient.java:5853](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5853>) |
| `celtium.validation.fixedWorldTime` | `Boolean.getBoolean` | [ValidationClient.java:5253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5253>) |
| `celtium.validation.frameViewportHeight` | `Integer.getInteger` | [ValidationClient.java:1116](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1116>) |
| `celtium.validation.gate.maxBlackFraction` | `System.getProperty` | [ValidationClient.java:1248](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1248>) |
| `celtium.validation.gate.minDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1241>) |
| `celtium.validation.gate.minNonBackgroundFraction` | `System.getProperty` | [ValidationClient.java:1243](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1243>) |
| `celtium.validation.gate.minPanelChangedFraction` | `System.getProperty` | [ValidationClient.java:1250](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1250>) |
| `celtium.validation.gate.minPanelDistinctColours` | `Integer.getInteger` | [ValidationClient.java:1256](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1256>) |
| `celtium.validation.gate.minPanelRowAmplitude` | `System.getProperty` | [ValidationClient.java:1258](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1258>) |
| `celtium.validation.gate.minPanelStrongFraction` | `System.getProperty` | [ValidationClient.java:1253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1253>) |
| `celtium.validation.gate.minVerticalBands` | `Integer.getInteger` | [ValidationClient.java:1246](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1246>) |
| `celtium.validation.glowEntities` | `Boolean.getBoolean` | [ValidationClient.java:3818](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3818>) |
| `celtium.validation.glyphParticles` | `Boolean.getBoolean` | [ValidationClient.java:5446](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5446>) |
| `celtium.validation.guiLayoutTicks` | `Integer.getInteger` | [ValidationClient.java:1094](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1094>) |
| `celtium.validation.guiMinFrames` | `Integer.getInteger` | [ValidationClient.java:1096](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1096>) |
| `celtium.validation.guiScale` | `Integer.getInteger` | [ValidationClient.java:74](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L74>) |
| `celtium.validation.guiScaleRestoreAtTick` | `Integer.getInteger` | [ValidationClient.java:80](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L80>) |
| `celtium.validation.guiWaitTicks` | `Integer.getInteger` | [ValidationClient.java:1098](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1098>) |
| `celtium.validation.heldItem` | `System.getProperty` | [ValidationClient.java:4976](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4976>) |
| `celtium.validation.holdUntilTick` | `Integer.getInteger` | [ValidationClient.java:750](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L750>) |
| `celtium.validation.hurtEntities` | `Boolean.getBoolean` | [ValidationClient.java:3808](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3808>) |
| `celtium.validation.hurtEntity` | `Boolean.getBoolean` | [ValidationClient.java:3706](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3706>) |
| `celtium.validation.iconifyAtTick` | `Integer.getInteger` | [ValidationClient.java:873](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L873>) |
| `celtium.validation.interop` | `Boolean.getBoolean` | [ValidationClient.java:3798](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3798>), [ValidationClient.java:3825](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3825>), [ValidationClient.java:5478](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5478>), [ValidationClient.java:5670](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5670>), [ValidationClient.java:5716](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5716>) |
| `celtium.validation.jaProbe` | `Boolean.getBoolean` | [ValidationClient.java:924](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L924>) |
| `celtium.validation.jeiFilterText` | `System.getProperty` | [ValidationClient.java:5183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5183>) |
| `celtium.validation.jeiRecipe` | `Boolean.getBoolean` | [ValidationClient.java:1567](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1567>), [ValidationClient.java:5194](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5194>) |
| `celtium.validation.jeiScreen` | `Boolean.getBoolean` | [ValidationClient.java:1564](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1564>), [ValidationClient.java:4551](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4551>), [ValidationClient.java:5114](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5114>) |
| `celtium.validation.jeiSearch` | `System.getProperty` | [ValidationClient.java:5135](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5135>) |
| `celtium.validation.keepEntities` | `Boolean.getBoolean` | [ValidationClient.java:3316](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3316>), [ValidationClient.java:3661](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3661>), [ValidationClient.java:3799](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3799>) |
| `celtium.validation.keepGui` | `Boolean.getBoolean` | [ValidationClient.java:5487](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5487>), [ValidationClient.java:5488](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5488>) |
| `celtium.validation.keepInventoryScreen` | `Boolean.getBoolean` | [ValidationClient.java:1573](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1573>), [ValidationClient.java:5241](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5241>) |
| `celtium.validation.lookDown` | `Boolean.getBoolean` | [ValidationClient.java:10308](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10308>) |
| `celtium.validation.menuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:973](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L973>) |
| `celtium.validation.mergedQuadCount` | `Integer.getInteger` | [ValidationClient.java:2104](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2104>) |
| `celtium.validation.minDimensionFrames` | `Integer.getInteger` | [ValidationClient.java:799](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L799>) |
| `celtium.validation.minWorldFrames` | `Integer.getInteger` | [ValidationClient.java:5759](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5759>), [ValidationClient.java:5763](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5763>) |
| `celtium.validation.modelLifecycle` | `System.getProperty` | [ModelListLifecycleProbe.java:36](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L36>) |
| `celtium.validation.particles` | `Boolean.getBoolean` | [ValidationClient.java:5428](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5428>) |
| `celtium.validation.pinSections` | `Integer.getInteger` | [ValidationClient.java:238](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L238>) |
| `celtium.validation.pinSectionsStableFrames` | `Integer.getInteger` | [ValidationClient.java:253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L253>) |
| `celtium.validation.postprocess` | `Boolean.getBoolean` | [ValidationClient.java:4956](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4956>) |
| `celtium.validation.presentedImageAtCapture` | `Boolean.getBoolean` | [ValidationClient.java:5935](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5935>) |
| `celtium.validation.probeLoadingProgress` | `Boolean.getBoolean` | [ValidationClient.java:3183](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3183>) |
| `celtium.validation.reload` | `Boolean.getBoolean` | [ValidationClient.java:4947](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4947>) |
| `celtium.validation.reloadAtTick` | `Integer.getInteger` | [ValidationClient.java:3874](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3874>) |
| `celtium.validation.renderClouds` | `Integer.getInteger` | [ValidationClient.java:3857](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3857>), [ValidationClient.java:5697](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5697>) |
| `celtium.validation.rendererlessEntities` | `Boolean.getBoolean` | [ValidationClient.java:3741](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3741>) |
| `celtium.validation.resize` | `Boolean.getBoolean` | [ValidationClient.java:5260](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5260>), [ValidationClient.java:9323](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9323>) |
| `celtium.validation.resizeAtTick` | `Integer.getInteger` | [ValidationClient.java:876](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L876>) |
| `celtium.validation.resizeDuringLoading` | `Boolean.getBoolean` | [ValidationClient.java:7134](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7134>) |
| `celtium.validation.resizeDuringMenu` | `Boolean.getBoolean` | [ValidationClient.java:892](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L892>) |
| `celtium.validation.resizeMenuHoldTicks` | `Integer.getInteger` | [ValidationClient.java:976](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L976>) |
| `celtium.validation.resizeOutput` | `System.getProperty` | [ValidationClient.java:905](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L905>) |
| `celtium.validation.resizeSchedule` | `System.getProperty` | [ValidationClient.java:6879](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6879>) |
| `celtium.validation.resizeTo` | `System.getProperty` | [ValidationClient.java:877](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L877>), [ValidationClient.java:5275](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5275>) |
| `celtium.validation.resizeViaCompositor` | `Boolean.getBoolean` | [ValidationClient.java:903](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L903>) |
| `celtium.validation.resourceReloadAtTick` | `Integer.getInteger` | [ValidationClient.java:854](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L854>) |
| `celtium.validation.runId` | `System.getProperty` | [ValidationClient.java:997](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L997>) |
| `celtium.validation.saveSlot` | `System.getProperty` | [ValidationClient.java:1655](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1655>) |
| `celtium.validation.scene` | `System.getProperty` | [ValidationClient.java:1138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1138>) |
| `celtium.validation.screen` | `System.getProperty` | [ValidationClient.java:5032](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5032>) |
| `celtium.validation.screenReadback` | `Boolean.getBoolean` | [ValidationClient.java:2015](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2015>) |
| `celtium.validation.screenshotAtTick` | `Integer.getInteger` | [ValidationClient.java:5223](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5223>) |
| `celtium.validation.scrollList` | `Integer.getInteger` | [ValidationClient.java:5057](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5057>) |
| `celtium.validation.showDebugInfo` | `System.getProperty` | [ValidationClient.java:3867](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3867>), [ValidationClient.java:5699](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5699>) |
| `celtium.validation.slowFrameMs` | `Integer.getInteger` | [ValidationClient.java:4942](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4942>), [ValidationClient.java:6003](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6003>) |
| `celtium.validation.spawnEntitiesAfterTransfer` | `Boolean.getBoolean` | [ValidationClient.java:3317](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3317>) |
| `celtium.validation.spectator` | `System.getProperty` | [ValidationClient.java:5356](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5356>) |
| `celtium.validation.stateProbes` | `Boolean.getBoolean` | [ValidationClient.java:2193](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2193>) |
| `celtium.validation.texEnvProbe` | `Boolean.getBoolean` | [ValidationClient.java:2260](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2260>) |
| `celtium.validation.thirdPerson` | `Boolean.getBoolean` | [ValidationClient.java:5493](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5493>) |
| `celtium.validation.unicodeFont` | `Boolean.getBoolean` | [ValidationClient.java:3051](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3051>) |
| `celtium.validation.vanillaReference` | `Boolean.getBoolean` | [ValidationClient.java:5829](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5829>) |
| `celtium.validation.videoSettingsOption` | `System.getProperty` | [ValidationClient.java:7862](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7862>), [ValidationClient.java:8156](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L8156>) |
| `celtium.validation.vulkanLayer` | `Boolean.getBoolean` | [ValidationClient.java:5731](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5731>) |
| `celtium.validation.warmFromTick` | `Integer.getInteger` | [ValidationClient.java:4394](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4394>), [ValidationClient.java:4929](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4929>), [ValidationClient.java:5995](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5995>), [ValidationClient.java:6029](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6029>) |
| `celtium.validation.warmToTick` | `Integer.getInteger` | [ValidationClient.java:4395](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4395>), [ValidationClient.java:4930](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4930>), [ValidationClient.java:5996](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5996>), [ValidationClient.java:6030](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6030>) |
| `celtium.validation.weather` | `Boolean.getBoolean` | [ValidationClient.java:5289](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5289>), [ValidationClient.java:5421](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5421>) |
| `celtium.validation.worldLayers` | `System.getProperty` | [ValidationClient.java:1539](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1539>), [ValidationClient.java:1656](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L1656>), [ValidationClient.java:9194](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9194>) |
| `celtium.validation.worldSwitch` | `Boolean.getBoolean` | [ValidationClient.java:3438](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3438>) |
| `celtium.validation.worldTime` | `Long.getLong` | [ValidationClient.java:83](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L83>) |

<a id="group-celtium-vulkan"></a>
## `celtium.vulkan.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkan.adoptGameFramebuffer` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14017>) |
| `celtium.vulkan.allowCpuDevice` | `Boolean.getBoolean` | [VulkanContext.java:100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L100>) |
| `celtium.vulkan.alphaFuncOverride` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2107>) |
| `celtium.vulkan.alphaTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2053](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2053>) |
| `celtium.vulkan.arenaCensus` | `Boolean.getBoolean` | [VulkanArena.java:61](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L61>) |
| `celtium.vulkan.arenaVerify` | `Boolean.getBoolean` | [VulkanArena.java:63](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L63>) |
| `celtium.vulkan.arenaVerifyBytes` | `Integer.getInteger` | [VulkanArena.java:65](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L65>) |
| `celtium.vulkan.arenaVerifyOkReports` | `Integer.getInteger` | [VulkanArena.java:68](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L68>) |
| `celtium.vulkan.assumeNoPresentSupport` | `Boolean.getBoolean` | [VulkanSwapchain.java:617](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L617>) |
| `celtium.vulkan.atlasAudit` | `System.getProperty` | [VulkanTerrainPipeline.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L343>) |
| `celtium.vulkan.batchFrameSubmits` | `Boolean.getBoolean` | [FrameCoordinator.java:39](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L39>), [VulkanTerrainGpuBackend.java:14561](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14561>) |
| `celtium.vulkan.batchTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2386>) |
| `celtium.vulkan.batchedSubmits` | `System.getProperty` | [FrameCoordinator.java:40](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L40>), [VulkanContext.java:330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L330>), [VulkanTerrainGpuBackend.java:14562](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14562>) |
| `celtium.vulkan.chainClearLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2573](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2573>) |
| `celtium.vulkan.chainDumpPasses` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:13766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13766>) |
| `celtium.vulkan.chainFrameDump` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2284](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2284>) |
| `celtium.vulkan.chainFrameDumpPath` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13734>) |
| `celtium.vulkan.chainFrameDumps` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2285](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2285>) |
| `celtium.vulkan.chainInputFlush` | `System.getProperty` | [ShaderGroupMixin.java:49](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L49>) |
| `celtium.vulkan.chainPreflightAdoption` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1193](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1193>) |
| `celtium.vulkan.colourAttrOffset` | `Integer.getInteger` | [VulkanTerrainPipeline.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L184>) |
| `celtium.vulkan.compareBoundTexture` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1436>) |
| `celtium.vulkan.cpuSplit` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2376](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2376>) |
| `celtium.vulkan.depthFormat` | `System.getProperty` | [VulkanDepthFormat.java:129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java#L129>) |
| `celtium.vulkan.descriptorPoolSets` | `Integer.getInteger` | [VulkanImmediateTexturePipeline.java:550](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L550>) |
| `celtium.vulkan.diagnostics` | `Boolean.getBoolean` | [VulkanDiagnostics.java:23](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L23>) |
| `celtium.vulkan.disableGameClearStandalone` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14570](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14570>) |
| `celtium.vulkan.drawSite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1906](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1906>) |
| `celtium.vulkan.drawSiteFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1909](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1909>) |
| `celtium.vulkan.drawSiteLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1892](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1892>) |
| `celtium.vulkan.drawSiteMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1895](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1895>) |
| `celtium.vulkan.dumpTextures` | `System.getProperty` | [VulkanGlTexture.java:3156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3156>), [ValidationClient.java:5754](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5754>) |
| `celtium.vulkan.dumpTexturesEvery` | `Boolean.getBoolean` | [VulkanGlTexture.java:3168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3168>) |
| `celtium.vulkan.enqAll` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1887](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1887>) |
| `celtium.vulkan.enqFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1889](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1889>) |
| `celtium.vulkan.failAllocationAt` | `Integer.getInteger` | [VulkanAllocationFault.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L14>) |
| `celtium.vulkan.failAllocationSite` | `System.getProperty` | [VulkanAllocationFault.java:24](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L24>) |
| `celtium.vulkan.failDescriptorSetAt` | `Long.getLong` | [VulkanDescriptorFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L16>) |
| `celtium.vulkan.failDescriptorSetSite` | `System.getProperty` | [VulkanDescriptorFault.java:17](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L17>) |
| `celtium.vulkan.failDeviceLost` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14127](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14127>) |
| `celtium.vulkan.failFenceWait` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:14122](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14122>) |
| `celtium.vulkan.failIdleAfter` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1669>) |
| `celtium.vulkan.failImmediateSubmits` | `Integer.getInteger` | [ValidationClient.java:2367](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2367>), [ValidationClient.java:2461](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2461>) |
| `celtium.vulkan.failSubmitAt` | `Long.getLong` | [VulkanSubmitFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L15>) |
| `celtium.vulkan.failSubmitSite` | `System.getProperty` | [VulkanSubmitFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L16>) |
| `celtium.vulkan.failSubmits` | `Integer.getInteger` | [FrameCoordinator.java:29](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L29>), [VulkanTerrainGpuBackend.java:2464](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2464>) |
| `celtium.vulkan.failTextureUploadAt` | `Long.getLong` | [VulkanTextureFault.java:15](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L15>) |
| `celtium.vulkan.failTextureUploadSite` | `System.getProperty` | [VulkanTextureFault.java:16](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L16>) |
| `celtium.vulkan.fogCaptureLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:3051](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3051>) |
| `celtium.vulkan.fontDrawStateBoundary` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2708](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2708>) |
| `celtium.vulkan.forceAcquireOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L189>) |
| `celtium.vulkan.forceNoOcclusion` | `Boolean.getBoolean` | [OcclusionCuller.java:251](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L251>) |
| `celtium.vulkan.forceNoTimestamps` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:425](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L425>) |
| `celtium.vulkan.forceOutOfDate` | `Boolean.getBoolean` | [VulkanSwapchain.java:184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L184>) |
| `celtium.vulkan.frameArenaInitialIndexBytes` | `Long.getLong` | [VulkanFrameArena.java:45](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L45>) |
| `celtium.vulkan.frameArenaInitialVertexBytes` | `Long.getLong` | [VulkanFrameArena.java:43](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java#L43>) |
| `celtium.vulkan.frameAtWindowFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:10085](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10085>) |
| `celtium.vulkan.frameAtWindowLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10088](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10088>) |
| `celtium.vulkan.frameClearMark` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10067](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10067>) |
| `celtium.vulkan.frameDumpCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13371](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13371>) |
| `celtium.vulkan.frameDumpDir` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13363](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13363>) |
| `celtium.vulkan.frameDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:13367](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13367>) |
| `celtium.vulkan.freshPresentSemaphores` | `Boolean.getBoolean` | [VulkanSwapchain.java:251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L251>) |
| `celtium.vulkan.gameClearLatch` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2281>) |
| `celtium.vulkan.glessKeepsInvalidations` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1083](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1083>) |
| `celtium.vulkan.glless` | `Boolean.getBoolean`, `System.getProperty` | [MappedStagingBuffer.java:62](<../common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L62>), [CeleritasLoadingPlugin.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L32>), [CeltiumGllessHint.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L32>), [CeltiumGllessHint.java:782](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L782>), [CeltiumGllessSplashTransformer.java:32](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L32>), [CeleritasVintageMixinPlugin.java:23](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L23>), [MinecraftMixin.java:36](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L36>) |
| `celtium.vulkan.gllessFrameSemaphores` | `System.getProperty` | [GllessFrameTarget.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L427>) |
| `celtium.vulkan.groupBounds` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1779](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1779>) |
| `celtium.vulkan.groupBoundsCount` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1819>) |
| `celtium.vulkan.groupBoundsFormat` | `Integer.getInteger`, `System.getProperty` | [VulkanTerrainGpuBackend.java:1781](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1781>), [VulkanTerrainGpuBackend.java:1797](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1797>) |
| `celtium.vulkan.groupBoundsGuiOnly` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1835](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1835>) |
| `celtium.vulkan.groupBoundsMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1829>) |
| `celtium.vulkan.groupColour` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2087](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2087>) |
| `celtium.vulkan.groupPool` | `Boolean.getBoolean` | [ImmediateGroup.java:141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java#L141>) |
| `celtium.vulkan.groupScissor` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2079](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2079>) |
| `celtium.vulkan.handleLog` | `Boolean.getBoolean` | [VulkanGlTexture.java:1188](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1188>) |
| `celtium.vulkan.handoffTimings` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:613](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L613>), [VulkanTerrainGpuBackend.java:2278](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2278>) |
| `celtium.vulkan.hideExternalFdExtensions` | `Boolean.getBoolean` | [VulkanContext.java:2320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2320>) |
| `celtium.vulkan.hudCaptureFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1852](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1852>) |
| `celtium.vulkan.hudCaptureMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1862](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1862>) |
| `celtium.vulkan.immediate` | `System.getProperty` | [VulkanTerrainGpuBackend.java:963](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L963>) |
| `celtium.vulkan.immediateBlendAlpha` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2068>) |
| `celtium.vulkan.immediateBlendEquation` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2070](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2070>) |
| `celtium.vulkan.immediateColorMask` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2072](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2072>) |
| `celtium.vulkan.immediateFlushIndices` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2270>) |
| `celtium.vulkan.immediateFog` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2876](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2876>) |
| `celtium.vulkan.immediateFogDistance` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2888](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2888>) |
| `celtium.vulkan.immediateFogLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2894](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2894>) |
| `celtium.vulkan.immediateFormats` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2161](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2161>) |
| `celtium.vulkan.immediateStateCalls` | `System.getProperty` | [ImmediateStatistics.java:548](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java#L548>) |
| `celtium.vulkan.immediateWindowSamples` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2473>) |
| `celtium.vulkan.immediateWindows` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2230>) |
| `celtium.vulkan.immediateWindowsPerFrame` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2268](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2268>) |
| `celtium.vulkan.indexCacheLimit` | `Integer.getInteger` | [VulkanImmediateDrawer.java:32](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateDrawer.java#L32>) |
| `celtium.vulkan.indirectCompare` | `Boolean.getBoolean` | [TerrainRenderer.java:291](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L291>) |
| `celtium.vulkan.indirectTerrain` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1538>) |
| `celtium.vulkan.lightDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1685](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1685>), [CompactChunkVertex.java:15](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L15>), [VanillaLikeChunkVertex.java:16](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L16>) |
| `celtium.vulkan.lightmapFullBrightGui` | `System.getProperty` | [VulkanTerrainGpuBackend.java:13998](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13998>) |
| `celtium.vulkan.matrixDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1765>) |
| `celtium.vulkan.mipBiasAdd` | `System.getProperty` | [TerrainRenderer.java:631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L631>) |
| `celtium.vulkan.mirrorCapturedLevels` | `System.getProperty` | [VulkanTextureRegistry.java:143](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L143>) |
| `celtium.vulkan.mirrorDrawLog` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2568](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2568>) |
| `celtium.vulkan.mirrorDump` | `System.getProperty` | [VulkanGlTexture.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L121>), [VulkanTerrainGpuBackend.java:7798](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7798>), [VulkanTerrainGpuBackend.java:7924](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7924>), [VulkanTextureRegistry.java:38](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L38>) |
| `celtium.vulkan.mirrorDumpIds` | `System.getProperty` | [VulkanGlTexture.java:123](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L123>) |
| `celtium.vulkan.mirrorEncode` | `System.getProperty` | [VulkanGlTexture.java:196](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L196>) |
| `celtium.vulkan.mirrorGluPerspective` | `System.getProperty` | [GluProjectionMixin.java:48](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GluProjectionMixin.java#L48>) |
| `celtium.vulkan.mirrorPoison` | `System.getProperty` | [VulkanGlTexture.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L145>) |
| `celtium.vulkan.mirrorPoisonFlat` | `System.getProperty` | [VulkanGlTexture.java:157](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L157>) |
| `celtium.vulkan.mirrorReachOverride` | `Integer.getInteger` | [VulkanTextureRegistry.java:141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L141>) |
| `celtium.vulkan.mirrorRefreshFrames` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2430](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2430>) |
| `celtium.vulkan.mirroredMatrices` | `System.getProperty` | [VulkanTerrainGpuBackend.java:1017](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1017>) |
| `celtium.vulkan.modelDraw` | `System.getProperty` | [VulkanTerrainGpuBackend.java:14120](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14120>) |
| `celtium.vulkan.modelDrawOnlyList` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:14107](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14107>) |
| `celtium.vulkan.modelMatrixLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11953](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11953>) |
| `celtium.vulkan.modelMeshBuild` | `System.getProperty` | [ModelGeometryCache.java:33](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L33>) |
| `celtium.vulkan.modelQuadLimit` | `Integer.getInteger` | [ModelGeometryCache.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L35>) |
| `celtium.vulkan.nativeTextureCopy` | `System.getProperty` | [TextureCopyService.java:168](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L168>) |
| `celtium.vulkan.noTerrainDepth` | `Boolean.getBoolean` | [VulkanTerrainPipeline.java:1163](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1163>) |
| `celtium.vulkan.pairLayoutLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10121>) |
| `celtium.vulkan.pairProbeClear` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10184](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10184>) |
| `celtium.vulkan.pairStayGeneral` | `System.getProperty` | [VulkanGlTexture.java:2117](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2117>), [VulkanTerrainGpuBackend.java:10145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10145>) |
| `celtium.vulkan.passSplit` | `Boolean.getBoolean` | [FramePassTimings.java:74](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L74>) |
| `celtium.vulkan.passSplitCpu` | `Boolean.getBoolean` | [FramePassTimings.java:88](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L88>) |
| `celtium.vulkan.patchMipLevels` | `System.getProperty` | [VulkanTextureRegistry.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L145>) |
| `celtium.vulkan.positionNormalDepthTest` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2059](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2059>) |
| `celtium.vulkan.postChainFailFirstUse` | `Integer.getInteger` | [VulkanPostChain.java:271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L271>) |
| `celtium.vulkan.postChainMaxPushConstants` | `Integer.getInteger` | [VulkanPostChain.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L288>) |
| `celtium.vulkan.presentFlushPending` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10171](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10171>) |
| `celtium.vulkan.presentFromComposite` | `System.getProperty` | [VulkanContext.java:1709](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1709>), [VulkanContext.java:1990](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1990>), [VulkanContext.java:2243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2243>), [VulkanSwapchain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L539>), [VulkanTerrainGpuBackend.java:2257](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2257>) |
| `celtium.vulkan.presentMarker` | `Boolean.getBoolean` | [VulkanSwapchain.java:182](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L182>) |
| `celtium.vulkan.presentNoRecord` | `Boolean.getBoolean` | [VulkanSwapchain.java:249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L249>) |
| `celtium.vulkan.presentPatch` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10164>) |
| `celtium.vulkan.presentPatchLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10174>) |
| `celtium.vulkan.presentPatchMinPresent` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:10177](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10177>) |
| `celtium.vulkan.presentReadback` | `Boolean.getBoolean` | [VulkanSwapchain.java:221](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L221>) |
| `celtium.vulkan.presentedDumpEvery` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2283>) |
| `celtium.vulkan.probeOldShape` | `Boolean.getBoolean` | [VulkanSwapchain.java:253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L253>) |
| `celtium.vulkan.readResolveLog` | `Integer.getInteger` | [TextureCopyService.java:189](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L189>) |
| `celtium.vulkan.rebuildDescriptors` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10153](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10153>) |
| `celtium.vulkan.referenceRender` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:1688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1688>) |
| `celtium.vulkan.refuseImmediateFormat` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:2277](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2277>) |
| `celtium.vulkan.refuseTerrainPass` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2279](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2279>) |
| `celtium.vulkan.samplerConfirmEarly` | `System.getProperty` | [VulkanTextureRegistry.java:1705](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1705>) |
| `celtium.vulkan.scratchZero` | `System.getProperty` | [VulkanTerrainGpuBackend.java:2681](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2681>) |
| `celtium.vulkan.skipColorReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:126](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L126>) |
| `celtium.vulkan.skipDepthReturn` | `Boolean.getBoolean` | [VulkanFrameTarget.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L124>) |
| `celtium.vulkan.skipGlComposite` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:2255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2255>) |
| `celtium.vulkan.skipSelfComposite` | `System.getProperty` | [VulkanTerrainGpuBackend.java:10147](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10147>) |
| `celtium.vulkan.solidTerrain` | `Boolean.getBoolean` | [TerrainRenderer.java:718](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L718>) |
| `celtium.vulkan.strictSymbolLines` | `Integer.getInteger` | [CeltiumGlStub.java:2984](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2984>) |
| `celtium.vulkan.strictSymbols` | `Boolean.getBoolean` | [CeltiumGlStub.java:3005](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3005>) |
| `celtium.vulkan.suppressReadbackIds` | `System.getProperty` | [VulkanGlTexture.java:212](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L212>) |
| `celtium.vulkan.swapchainProbe` | `Boolean.getBoolean` | [VulkanContext.java:1708](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1708>), [VulkanContext.java:1989](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1989>), [VulkanContext.java:2242](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2242>), [VulkanSwapchain.java:538](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L538>), [VulkanTerrainGpuBackend.java:14661](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14661>) |
| `celtium.vulkan.targetCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:4743](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4743>) |
| `celtium.vulkan.targetImageLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:4734](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4734>) |
| `celtium.vulkan.terrainAtlasMaxLod` | `Integer.getInteger` | [VulkanTerrainPipeline.java:258](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L258>) |
| `celtium.vulkan.textureDiagnostics` | `Boolean.getBoolean` | [VulkanGlTexture.java:112](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L112>), [VulkanTerrainGpuBackend.java:1690](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1690>) |
| `celtium.vulkan.uiTest` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10060](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10060>) |
| `celtium.vulkan.uploadCallerId` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:11932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11932>) |
| `celtium.vulkan.uploadTrace` | `Boolean.getBoolean` | [VulkanContext.java:405](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L405>) |
| `celtium.vulkan.validation` | `Boolean.getBoolean` | [VulkanContext.java:1693](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1693>), [VulkanDiagnostics.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDiagnostics.java#L25>), [VulkanPostChain.java:272](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L272>) |
| `celtium.vulkan.vertexColourSamples` | `Integer.getInteger` | [TerrainRenderer.java:785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L785>) |
| `celtium.vulkan.vertexDiagnostics` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:10512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10512>) |
| `celtium.vulkan.windowCopyBlit` | `System.getProperty` | [TextureCopyService.java:185](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L185>) |
| `celtium.vulkan.windowDrawLines` | `Integer.getInteger` | [VulkanTerrainGpuBackend.java:1912](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1912>) |
| `celtium.vulkan.windowDrawMinFrame` | `Long.getLong` | [VulkanTerrainGpuBackend.java:1915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1915>) |
| `celtium.vulkan.withoutGlIdentity` | `Boolean.getBoolean` | [VulkanContext.java:2843](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2843>) |
| `celtium.vulkan.worldMatrixCensus` | `Boolean.getBoolean` | [VulkanTerrainGpuBackend.java:309](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L309>) |

<a id="group-celtium-vulkanTerrain"></a>
## `celtium.vulkanTerrain.*`

| キー | 読み取りAPI | 位置 |
|---|---|---|
| `celtium.vulkanTerrain` | `System.getProperty` | [VulkanTerrainFactory.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L42>), [VulkanTerrainFactory.java:108](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainFactory.java#L108>) |

## 文字列内マーカー

| マーカー | 位置 |
|---|---|
| `CELTIUM_ABANDON_WAIT_CONSUMED` | [GllessFrameTarget.java:477](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L477>) |
| `CELTIUM_ACCEPTED` | [VulkanTerrainGpuBackend.java:6547](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6547>) |
| `CELTIUM_ADOPT_PROBE` | [FramebufferMixin.java:150](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java#L150>) |
| `CELTIUM_ALLOCATION_FAULT` | [VulkanAllocationFault.java:46](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L46>), [VulkanAllocationFault.java:60](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanAllocationFault.java#L60>) |
| `CELTIUM_ALL_BLOCKS` | [ValidationClient.java:3796](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3796>) |
| `CELTIUM_ANIM` | [VulkanTerrainGpuBackend.java:3898](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3898>) |
| `CELTIUM_ANIM_ANNOUNCE` | [VulkanTerrainGpuBackend.java:3290](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3290>) |
| `CELTIUM_ARENA_UPLOAD` | [VulkanArena.java:427](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L427>) |
| `CELTIUM_ARENA_VERIFY` | [VulkanArena.java:466](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L466>), [VulkanArena.java:475](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L475>), [VulkanArena.java:486](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L486>) |
| `CELTIUM_ARRIVALS` | [WorldVertexBufferUploaderMixin.java:257](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L257>) |
| `CELTIUM_ATLAS_DESCRIPTOR` | [VulkanTerrainPipeline.java:638](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L638>), [VulkanTerrainPipeline.java:679](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L679>), [VulkanTerrainPipeline.java:699](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L699>) |
| `CELTIUM_ATLAS_SAMPLER` | [VulkanTerrainPipeline.java:295](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L295>) |
| `CELTIUM_BATCH_CACHE` | [SectionRenderDataStorage.java:189](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L189>) |
| `CELTIUM_BIG_MIRROR` | [VulkanTextureRegistry.java:1880](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1880>) |
| `CELTIUM_BIND_CALLER` | [GlStateManagerMixin.java:237](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L237>) |
| `CELTIUM_BIND_ENTRY` | [VulkanTerrainGpuBackend.java:4952](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4952>) |
| `CELTIUM_BIND_PENDING` | [VulkanTerrainGpuBackend.java:4965](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4965>) |
| `CELTIUM_BLEND_CENSUS` | [CeltiumGlStub.java:773](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L773>) |
| `CELTIUM_BLOCKLAYER` | [RenderGlobalMixin.java:192](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L192>) |
| `CELTIUM_BOX_PRE` | [VulkanTerrainGpuBackend.java:7948](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7948>) |
| `CELTIUM_BOX_PROBE` | [TextureCopyService.java:770](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L770>) |
| `CELTIUM_BRIGHTNESS` | [VintageBlockRenderer.java:62](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.java#L62>) |
| `CELTIUM_CANCEL_NOT_CANCELLABLE` | [GlStateManagerMixin.java:114](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L114>) |
| `CELTIUM_CANCEL_STATE` | [GlStateManagerMixin.java:102](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L102>) |
| `CELTIUM_CAPTURED_UPLOAD` | [VulkanGlTexture.java:1251](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1251>) |
| `CELTIUM_CAPTURE_MISS` | [VulkanGlTexture.java:1271](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1271>) |
| `CELTIUM_CAPTURE_TRACE` | [ValidationClient.java:4725](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4725>) |
| `CELTIUM_CHAIN_CLEAR` | [VulkanTerrainGpuBackend.java:2513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2513>) |
| `CELTIUM_CHAIN_FRAME_DUMP` | [VulkanTerrainGpuBackend.java:13754](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13754>), [VulkanTerrainGpuBackend.java:13799](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13799>), [VulkanTerrainGpuBackend.java:13829](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13829>) |
| `CELTIUM_CHAIN_INPUT_BATCHES` | [VulkanTerrainGpuBackend.java:12377](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12377>) |
| `CELTIUM_CHAIN_INPUT_FLUSH` | [VulkanTerrainGpuBackend.java:12408](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12408>) |
| `CELTIUM_CHAIN_PAIR_CLEAR` | [VulkanTerrainGpuBackend.java:8879](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8879>) |
| `CELTIUM_CHANGE_DIM` | [EntityPlayerMPMixin.java:32](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityPlayerMPMixin.java#L32>) |
| `CELTIUM_CHUNK_ADD` | [ClientChunkManagerMixin.java:89](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.java#L89>) |
| `CELTIUM_CHURN` | [ValidationClient.java:10177](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10177>) |
| `CELTIUM_CLEAR_LATCH` | [VulkanTerrainGpuBackend.java:11353](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11353>) |
| `CELTIUM_CLIENT_ARRAY` | [CeltiumGlStub.java:5840](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5840>) |
| `CELTIUM_CLOUD_CALL` | [CloudDrawProbeMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L66>) |
| `CELTIUM_CLOUD_FANCY` | [CloudDrawProbeMixin.java:121](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L121>) |
| `CELTIUM_CLOUD_UPLOADER` | [CloudDrawProbeMixin.java:103](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/CloudDrawProbeMixin.java#L103>) |
| `CELTIUM_COMMAND_BUFFER_TARGETS` | [ValidationClient.java:2612](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L2612>) |
| `CELTIUM_COMPOSITE_LAYOUT_REFUSED` | [WorldVertexBufferUploaderMixin.java:343](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L343>) |
| `CELTIUM_COMPOSITE_REFUSED` | [WorldVertexBufferUploaderMixin.java:385](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L385>) |
| `CELTIUM_COPY_CALLER` | [TextureCopyService.java:364](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L364>) |
| `CELTIUM_COPY_READ` | [TextureCopyService.java:1181](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L1181>) |
| `CELTIUM_DEPTH_END` | [TextureCopyService.java:521](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L521>) |
| `CELTIUM_DEPTH_STAGE` | [TextureCopyService.java:738](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L738>) |
| `CELTIUM_DESCRIPTOR_FAULT` | [VulkanDescriptorFault.java:42](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDescriptorFault.java#L42>) |
| `CELTIUM_DEVICE_CONTRACT` | [VulkanContext.java:2003](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2003>) |
| `CELTIUM_DEVICE_LOST` | [VulkanTerrainGpuBackend.java:15801](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15801>) |
| `CELTIUM_DIM_ARRIVED` | [ValidationClient.java:3268](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3268>) |
| `CELTIUM_DIM_BACK_ARRIVED` | [ValidationClient.java:3285](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3285>) |
| `CELTIUM_DIM_CHANGE` | [ValidationClient.java:3365](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3365>) |
| `CELTIUM_DIM_GATE` | [ValidationClient.java:5621](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5621>) |
| `CELTIUM_DIM_GATE_WAIT` | [ValidationClient.java:5615](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L5615>) |
| `CELTIUM_DIM_LEFT` | [ValidationClient.java:3376](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3376>) |
| `CELTIUM_DIM_PLACEMENT` | [ValidationClient.java:6542](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6542>) |
| `CELTIUM_DIM_PORTAL_ESCAPE` | [ValidationClient.java:3431](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3431>) |
| `CELTIUM_DIM_TRACE` | [ValidationClient.java:3387](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3387>), [ValidationClient.java:6138](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6138>) |
| `CELTIUM_DIRECT_SUPPLY` | [VulkanTerrainGpuBackend.java:3432](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3432>) |
| `CELTIUM_DISPLAY_LIST` | [VulkanDisplayListCensus.java:369](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L369>) |
| `CELTIUM_DISPLAY_LISTS` | [CeltiumGlStub.java:793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L793>) |
| `CELTIUM_DISPLAY_LIST_CANCELLED` | [VulkanDisplayListCensus.java:315](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L315>) |
| `CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE` | [VulkanDisplayListCensus.java:121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L121>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN` | [VulkanDisplayListCensus.java:75](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L75>) |
| `CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN` | [VulkanDisplayListCensus.java:91](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L91>) |
| `CELTIUM_DISPLAY_LIST_REPLAY` | [VulkanDisplayListCensus.java:424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L424>) |
| `CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN` | [VulkanDisplayListCensus.java:397](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L397>) |
| `CELTIUM_DISPLAY_LIST_UNKNOWN` | [VulkanDisplayListCensus.java:343](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDisplayListCensus.java#L343>) |
| `CELTIUM_DRAWARRAYS` | [CeltiumGlStub.java:776](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L776>), [CeltiumGlStub.java:3283](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3283>) |
| `CELTIUM_DRAWARRAY_LAYOUT` | [CeltiumGlStub.java:5453](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5453>) |
| `CELTIUM_DRAWARRAY_PUREBLUE` | [CeltiumGlStub.java:5941](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5941>) |
| `CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` | [CeltiumGlStub.java:5508](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5508>) |
| `CELTIUM_DRAWN_SECTIONS` | [VulkanTerrainGpuBackend.java:10745](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10745>) |
| `CELTIUM_DRAW_SITE` | [VulkanTerrainGpuBackend.java:15256](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15256>) |
| `CELTIUM_DRIVER_INFO` | [VulkanContext.java:2141](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2141>) |
| `CELTIUM_EMPTY` | [VintageRenderSectionManager.java:104](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.java#L104>) |
| `CELTIUM_ENTITIES_SPAWNED` | [ValidationClient.java:3702](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3702>) |
| `CELTIUM_ENTITIES_SPAWNED_AFTER_TRANSFER` | [ValidationClient.java:3324](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3324>) |
| `CELTIUM_ENTITIES_SPAWN_DEFERRED` | [ValidationClient.java:3334](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3334>) |
| `CELTIUM_ENTITY_REFUSED` | [VulkanTerrainGpuBackend.java:6010](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6010>) |
| `CELTIUM_ENTITY_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6018](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6018>) |
| `CELTIUM_ENV_COMBINE` | [VulkanTerrainGpuBackend.java:3701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3701>) |
| `CELTIUM_ENV_SEEN` | [VulkanTerrainGpuBackend.java:3711](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3711>) |
| `CELTIUM_ENV_WRITE` | [CeltiumGlStub.java:4687](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4687>) |
| `CELTIUM_EXTERNAL_IMAGE` | [VulkanTextureRegistry.java:288](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L288>), [VulkanTextureRegistry.java:327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L327>) |
| `CELTIUM_F3_EVENT` | [CeleritasVintage.java:136](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L136>) |
| `CELTIUM_F3_SECTION` | [CeleritasVintage.java:173](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L173>) |
| `CELTIUM_F3_SECTION_REBUILD` | [CeleritasVintage.java:120](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L120>) |
| `CELTIUM_FBO_CENSUS` | [VulkanTerrainGpuBackend.java:4860](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4860>) |
| `CELTIUM_FBO_TEXTURE_PUBLISHED` | [VulkanTerrainGpuBackend.java:4902](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4902>) |
| `CELTIUM_FENCE_TIMEOUT` | [VulkanTerrainGpuBackend.java:15793](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15793>) |
| `CELTIUM_FIXTURE_FRAME` | [ValidationClient.java:4876](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4876>), [ValidationClient.java:4890](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4890>) |
| `CELTIUM_FIXTURE_TICK` | [ValidationClient.java:3043](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3043>) |
| `CELTIUM_FMT5_POS` | [VulkanTerrainGpuBackend.java:5597](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5597>) |
| `CELTIUM_FMT5_SITE` | [VulkanTerrainGpuBackend.java:5631](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5631>) |
| `CELTIUM_FMT5_VERTEX` | [VulkanTerrainGpuBackend.java:5620](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5620>) |
| `CELTIUM_FN_CENSUS` | [CeltiumGlStub.java:785](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L785>) |
| `CELTIUM_FOG` | [TerrainRenderer.java:692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L692>) |
| `CELTIUM_FOG_CAPTURE` | [VulkanTerrainGpuBackend.java:3065](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3065>) |
| `CELTIUM_FONTAPPEND` | [FontBatch.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java#L361>) |
| `CELTIUM_FONT_GLYPH` | [CeltiumFontGlyphCapture.java:1239](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1239>), [CeltiumFontGlyphCapture.java:1373](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1373>) |
| `CELTIUM_FONT_GLYPH_MATRIX` | [CeltiumFontGlyphCapture.java:1214](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1214>) |
| `CELTIUM_FONT_GLYPH_REFUSED` | [CeltiumFontGlyphCapture.java:1355](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1355>) |
| `CELTIUM_FONT_GLYPH_SUMMARY` | [CeltiumFontGlyphCapture.java:1432](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1432>) |
| `CELTIUM_FONT_STATE` | [CeltiumFontGlyphCapture.java:426](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L426>), [CeltiumFontGlyphCapture.java:430](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L430>) |
| `CELTIUM_FORMAT_SITE` | [WorldVertexBufferUploaderMixin.java:662](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L662>) |
| `CELTIUM_FRAME_AT_WINDOW` | [VulkanTerrainGpuBackend.java:7713](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7713>) |
| `CELTIUM_FRAME_BASE` | [VulkanTerrainGpuBackend.java:5173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5173>) |
| `CELTIUM_FRAME_PASS_SPLIT` | [FramePassTimings.java:222](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramePassTimings.java#L222>) |
| `CELTIUM_FRAME_QUEUED` | [VulkanTerrainGpuBackend.java:124](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L124>) |
| `CELTIUM_FRAME_STATE` | [EntityRendererFrameStateProbeMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererFrameStateProbeMixin.java#L84>) |
| `CELTIUM_GAME_CLEAR` | [VulkanTerrainGpuBackend.java:11159](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11159>) |
| `CELTIUM_GAME_CLEAR_ROUTE` | [VulkanTerrainGpuBackend.java:11273](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11273>) |
| `CELTIUM_GLESS_INVALIDATE` | [VulkanTerrainGpuBackend.java:3950](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3950>) |
| `CELTIUM_GLLESS` | [CeltiumGlStub.java:726](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L726>), [CeltiumGlStub.java:729](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L729>), [CeleritasLoadingPlugin.java:43](<../forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java#L43>), [CeltiumGllessHint.java:790](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L790>), [CeltiumGllessHint.java:794](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L794>), [CeltiumGllessNativeTransformer.java:89](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L89>), [CeltiumGllessSplashTransformer.java:54](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L54>), [CeltiumGllessSplashTransformer.java:69](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessSplashTransformer.java#L69>), [CeltiumGllessWindowTransformer.java:79](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java#L79>), [CeleritasVintageMixinPlugin.java:58](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L58>), [CeleritasVintageMixinPlugin.java:60](<../forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java#L60>), [MinecraftMixin.java:391](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L391>), [MinecraftMixin.java:397](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L397>) |
| `CELTIUM_GLUPERSPECTIVE` | [VulkanTerrainGpuBackend.java:4255](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4255>) |
| `CELTIUM_GLUPERSPECTIVE_SKIP` | [VulkanTerrainGpuBackend.java:4281](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4281>) |
| `CELTIUM_GLYPH` | [CeltiumFontGlyphCapture.java:602](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L602>), [CeltiumFontGlyphCapture.java:691](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L691>) |
| `CELTIUM_GLYPHEND` | [CeltiumFontGlyphCapture.java:1038](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L1038>) |
| `CELTIUM_GPU_VERTEX` | [VulkanTerrainGpuBackend.java:16207](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L16207>) |
| `CELTIUM_GROUPS` | [VulkanTerrainGpuBackend.java:3854](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3854>) |
| `CELTIUM_GROUP_BOUNDS` | [VulkanTerrainGpuBackend.java:6817](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6817>) |
| `CELTIUM_GROUP_CLOSE` | [VulkanTerrainGpuBackend.java:9249](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9249>) |
| `CELTIUM_GROUP_DEPTH` | [VulkanTerrainGpuBackend.java:10717](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10717>) |
| `CELTIUM_GROUP_SCISSOR` | [VulkanTerrainGpuBackend.java:15440](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15440>) |
| `CELTIUM_GROUP_VIEWPORT` | [VulkanTerrainGpuBackend.java:15478](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L15478>) |
| `CELTIUM_GUI_CLICK_FAILED` | [ValidationClient.java:7753](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7753>) |
| `CELTIUM_HINT_DISPLAY_LISTS` | [CeltiumGllessHint.java:392](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L392>) |
| `CELTIUM_HUD_GROUP` | [VulkanTerrainGpuBackend.java:4642](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4642>) |
| `CELTIUM_HURT_SPAWNED` | [ValidationClient.java:3731](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3731>) |
| `CELTIUM_ICONIFY` | [ValidationClient.java:3525](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3525>) |
| `CELTIUM_IMAGE_RELEASED` | [VulkanGlTexture.java:2540](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2540>) |
| `CELTIUM_IMMEDIATE_BEGIN` | [CeltiumGlStub.java:3233](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3233>) |
| `CELTIUM_IMMEDIATE_DESCRIPTORS` | [VulkanImmediateItemPipeline.java:361](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L361>), [VulkanImmediateTexturePipeline.java:597](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L597>) |
| `CELTIUM_IMMEDIATE_ENQ` | [VulkanTerrainGpuBackend.java:9172](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9172>) |
| `CELTIUM_IMMEDIATE_FOG` | [VulkanTerrainGpuBackend.java:3165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3165>) |
| `CELTIUM_INVALIDATE_ORIGIN` | [VulkanTerrainGpuBackend.java:3312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3312>) |
| `CELTIUM_ITEM_MESH` | [RenderItemMixin.java:174](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java#L174>) |
| `CELTIUM_JOIN` | [NetHandlerJoinProbeMixin.java:31](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L31>), [NetHandlerJoinProbeMixin.java:39](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/NetHandlerJoinProbeMixin.java#L39>) |
| `CELTIUM_LEVEL0` | [VulkanGlTexture.java:1458](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1458>), [VulkanGlTexture.java:3512](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3512>) |
| `CELTIUM_LIFECYCLE` | [VulkanTextureRegistry.java:555](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L555>), [VulkanTextureRegistry.java:1473](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1473>) |
| `CELTIUM_LIGHTCOORD` | [VulkanTerrainGpuBackend.java:5600](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5600>) |
| `CELTIUM_LIGHTMAP` | [VulkanTerrainGpuBackend.java:8148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8148>) |
| `CELTIUM_LIGHTMAP_FAILURE` | [VulkanTerrainGpuBackend.java:8199](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8199>) |
| `CELTIUM_LIGHTMAP_FALLBACK` | [VulkanTerrainGpuBackend.java:8178](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8178>) |
| `CELTIUM_LIGHTMAP_STAGE` | [VulkanTerrainGpuBackend.java:8230](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8230>) |
| `CELTIUM_LIGHT_BIND` | [VulkanTerrainGpuBackend.java:10572](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10572>) |
| `CELTIUM_LINE_NARROWED` | [VulkanTerrainGpuBackend.java:5569](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5569>) |
| `CELTIUM_LIST_INPUT` | [RenderSectionManager.java:481](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L481>) |
| `CELTIUM_LIST_SIZE` | [RenderSectionManager.java:698](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L698>) |
| `CELTIUM_LOADING_PROGRESS_CAPTURE` | [LoadingScreenRendererMixin.java:55](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java#L55>), [ValidationClient.java:7176](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7176>) |
| `CELTIUM_MATRIX_DUMP` | [VulkanTerrainGpuBackend.java:9156](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9156>) |
| `CELTIUM_MIRROR` | [VulkanGlTexture.java:1436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1436>) |
| `CELTIUM_MIRROR_COVERAGE` | [VulkanGlTexture.java:1702](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1702>) |
| `CELTIUM_MIRROR_COVERAGE_PROGRESS` | [VulkanGlTexture.java:1819](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1819>) |
| `CELTIUM_MIRROR_DRAW` | [VulkanTerrainGpuBackend.java:14994](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14994>) |
| `CELTIUM_MIRROR_DUMP` | [VulkanGlTexture.java:3012](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3012>) |
| `CELTIUM_MIRROR_LEVEL` | [VulkanGlTexture.java:3026](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3026>) |
| `CELTIUM_MIRROR_PATCH` | [VulkanTextureRegistry.java:1150](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1150>) |
| `CELTIUM_MIRROR_PATCH_BEYOND_MIRROR` | [VulkanGlTexture.java:1762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1762>) |
| `CELTIUM_MIRROR_PATCH_REFUSED` | [VulkanGlTexture.java:1901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1901>) |
| `CELTIUM_MIRROR_SOURCE` | [VulkanTextureRegistry.java:1327](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1327>) |
| `CELTIUM_MODEL_BOUNDS` | [VulkanTerrainGpuBackend.java:3762](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3762>) |
| `CELTIUM_MODEL_BUILD` | [ModelGeometryCache.java:334](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L334>) |
| `CELTIUM_MODEL_ENQ` | [VulkanTerrainGpuBackend.java:3749](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3749>) |
| `CELTIUM_MODEL_LAYER` | [VulkanTerrainGpuBackend.java:12056](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12056>) |
| `CELTIUM_MODEL_LIFECYCLE` | [ModelListLifecycleProbe.java:294](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L294>) |
| `CELTIUM_MODEL_LIFECYCLE_SEQUENCE` | [ModelListLifecycleProbe.java:123](<../validation/client/src/main/java/org/soutalab/celtium/validation/ModelListLifecycleProbe.java#L123>) |
| `CELTIUM_MODEL_LIST_CLOSE` | [VulkanModelQuadCapture.java:387](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L387>) |
| `CELTIUM_MODEL_LIST_OPEN` | [VulkanModelQuadCapture.java:365](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L365>) |
| `CELTIUM_MODEL_MATRIX` | [VulkanTerrainGpuBackend.java:12119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12119>) |
| `CELTIUM_MODEL_MESH` | [VulkanDynamicMesh.java:947](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L947>) |
| `CELTIUM_MODEL_MESH_DISPLACED` | [VulkanDynamicMesh.java:907](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L907>) |
| `CELTIUM_MODEL_MISSING` | [ModelGeometryCache.java:265](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelGeometryCache.java#L265>), [VulkanTerrainGpuBackend.java:12028](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12028>) |
| `CELTIUM_MODEL_OFFTARGET` | [VulkanTerrainGpuBackend.java:11985](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11985>) |
| `CELTIUM_MODEL_OFFTARGET_BATCHES` | [VulkanTerrainGpuBackend.java:11992](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11992>) |
| `CELTIUM_MODEL_OFFTARGET_FLUSH` | [VulkanTerrainGpuBackend.java:3836](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3836>) |
| `CELTIUM_MODEL_QUAD` | [VulkanModelQuadCapture.java:320](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L320>) |
| `CELTIUM_MODEL_QUAD_REFUSALS` | [VulkanModelQuadCapture.java:500](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L500>) |
| `CELTIUM_MODEL_QUAD_REFUSED` | [VulkanModelQuadCapture.java:493](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L493>) |
| `CELTIUM_MODEL_QUAD_SOURCE` | [VulkanModelQuadCapture.java:513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanModelQuadCapture.java#L513>) |
| `CELTIUM_MODEL_STATE` | [VulkanTerrainGpuBackend.java:12068](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12068>) |
| `CELTIUM_MODEL_TEXTURE` | [GlStateManagerMixin.java:84](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L84>) |
| `CELTIUM_MOUSEOVER` | [ValidationClient.java:10326](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L10326>) |
| `CELTIUM_MULT_MATRIX` | [CeltiumGlStub.java:3424](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3424>) |
| `CELTIUM_NATIVE_DEVICE` | [VulkanContext.java:1903](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1903>), [TerrainBackendSelection.java:41](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/backend/TerrainBackendSelection.java#L41>) |
| `CELTIUM_NOOP_CENSUS` | [CeltiumGlStub.java:765](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L765>) |
| `CELTIUM_NULLFORMAT` | [WorldVertexBufferUploaderMixin.java:274](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L274>), [WorldVertexBufferUploaderMixin.java:287](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L287>) |
| `CELTIUM_OFFTARGET_SELFTEST` | [VulkanOffscreenTarget.java:280](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanOffscreenTarget.java#L280>) |
| `CELTIUM_OOD` | [VulkanTerrainGpuBackend.java:5080](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5080>) |
| `CELTIUM_OPENGLYPH` | [CeltiumFontGlyphCapture.java:411](<../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java#L411>) |
| `CELTIUM_ORTHO` | [VulkanTerrainGpuBackend.java:4086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4086>) |
| `CELTIUM_OUTLINE_INPUT` | [VulkanTerrainGpuBackend.java:12429](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12429>) |
| `CELTIUM_OWNED_BUFFER` | [ClientArrayBufferOwnershipMixin.java:66](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L66>), [ClientArrayBufferOwnershipMixin.java:78](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L78>), [ClientArrayBufferOwnershipMixin.java:87](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ClientArrayBufferOwnershipMixin.java#L87>) |
| `CELTIUM_PAIR_CLEAR_REFUSED` | [VulkanTerrainGpuBackend.java:11701](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11701>) |
| `CELTIUM_PAIR_LAYOUT` | [VulkanTerrainGpuBackend.java:12914](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12914>) |
| `CELTIUM_PAIR_PROBE_CLEAR` | [VulkanTerrainGpuBackend.java:9015](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9015>) |
| `CELTIUM_PAIR_SKIP` | [VulkanTerrainGpuBackend.java:12852](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12852>) |
| `CELTIUM_PARTICLE_CALL` | [ParticleDrawProbeMixin.java:59](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ParticleDrawProbeMixin.java#L59>) |
| `CELTIUM_PARTICLE_UV` | [CeltiumGlStub.java:4917](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4917>) |
| `CELTIUM_PATCH_REJECTED` | [VulkanTextureRegistry.java:1170](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1170>) |
| `CELTIUM_PICTURE_IDENTITY` | [ValidationClient.java:7253](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7253>), [ValidationClient.java:7393](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7393>) |
| `CELTIUM_POSITIONTEX_REFUSED` | [WorldVertexBufferUploaderMixin.java:378](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L378>) |
| `CELTIUM_POSITION_NORMAL_REFUSED` | [WorldVertexBufferUploaderMixin.java:360](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L360>) |
| `CELTIUM_POSITION_REFUSED` | [WorldVertexBufferUploaderMixin.java:350](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L350>) |
| `CELTIUM_POSTCHAIN` | [VulkanPostChain.java:406](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L406>), [VulkanPostChain.java:418](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L418>), [VulkanPostChain.java:539](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L539>), [VulkanPostChain.java:543](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L543>), [VulkanPostChain.java:659](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L659>), [VulkanPostChain.java:688](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L688>), [VulkanPostChain.java:692](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L692>), [VulkanPostChain.java:1238](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1238>), [VulkanPostChain.java:1243](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1243>), [VulkanTerrainGpuBackend.java:12451](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12451>) |
| `CELTIUM_POSTPROCESS` | [ValidationClient.java:4974](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L4974>) |
| `CELTIUM_POST_ADOPT_REFUSED` | [VulkanPostChain.java:1349](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L1349>) |
| `CELTIUM_POST_BLOCK_REFUSED` | [VulkanPostChain.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L782>) |
| `CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT` | [VulkanPostChain.java:921](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L921>) |
| `CELTIUM_PRESENT` | [VulkanTerrainGpuBackend.java:13169](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13169>) |
| `CELTIUM_PRESENTED_DUMP_HELD` | [VulkanTerrainGpuBackend.java:11572](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11572>) |
| `CELTIUM_PRESENTED_IMAGE` | [ValidationClient.java:9153](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L9153>) |
| `CELTIUM_PRESENT_COUNTER_RESTART` | [ValidationClient.java:3916](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3916>) |
| `CELTIUM_PRESENT_MODE` | [VulkanSwapchain.java:1710](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1710>) |
| `CELTIUM_PRESENT_MODE_REQUEST` | [VulkanSwapchain.java:148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L148>) |
| `CELTIUM_PRESENT_PATCH` | [VulkanTerrainGpuBackend.java:13260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L13260>) |
| `CELTIUM_PRESENT_TIMING` | [VulkanSwapchain.java:900](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L900>) |
| `CELTIUM_PROJ_PROBE` | [VulkanTerrainGpuBackend.java:9115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9115>) |
| `CELTIUM_RAIN_CALL` | [RainDrawProbeMixin.java:40](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/RainDrawProbeMixin.java#L40>) |
| `CELTIUM_RAW_FBO` | [CeltiumGlStub.java:3932](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3932>) |
| `CELTIUM_RAW_FBO_ADOPTED` | [VulkanTerrainGpuBackend.java:4790](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4790>) |
| `CELTIUM_READBACK_REFRESH` | [VulkanTextureRegistry.java:1814](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1814>) |
| `CELTIUM_READBACK_SUPPRESSED` | [VulkanGlTexture.java:1287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1287>) |
| `CELTIUM_READ_PIXELS` | [TextureCopyService.java:957](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L957>), [CeltiumGlStub.java:5165](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5165>), [CeltiumGlStub.java:5174](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5174>) |
| `CELTIUM_READ_RESOLVE` | [TextureCopyService.java:838](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L838>) |
| `CELTIUM_REFUSALS_BY_FORMAT` | [WorldVertexBufferUploaderMixin.java:173](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L173>) |
| `CELTIUM_REGISTRY_TEXTURE` | [VulkanImmediateTexturePipeline.java:436](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L436>) |
| `CELTIUM_RENDERENTITIES` | [RenderGlobalMixin.java:276](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java#L276>) |
| `CELTIUM_RENDERERLESS_ENTITY_SPAWNED` | [ValidationClient.java:3759](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3759>) |
| `CELTIUM_RENDERLIST` | [CeleritasWorldRenderer.java:158](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L158>) |
| `CELTIUM_RENDER_LAYER` | [RenderSectionManager.java:606](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L606>) |
| `CELTIUM_REPLACE_DROP` | [VulkanTerrainGpuBackend.java:7564](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7564>) |
| `CELTIUM_RESIZE` | [ValidationClient.java:6613](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6613>) |
| `CELTIUM_RESIZE_COMPOSITOR` | [ValidationClient.java:6760](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6760>), [ValidationClient.java:6761](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6761>) |
| `CELTIUM_RESIZE_FAILED` | [ValidationClient.java:6702](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6702>), [ValidationClient.java:6736](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6736>) |
| `CELTIUM_RESIZE_OBSERVED` | [ValidationClient.java:6863](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6863>) |
| `CELTIUM_RESIZE_REQUEST` | [ValidationClient.java:6763](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6763>), [ValidationClient.java:6765](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6765>) |
| `CELTIUM_RESOURCE_RELOAD` | [ValidationClient.java:3497](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3497>) |
| `CELTIUM_RESOURCE_RELOAD_FAILED` | [ValidationClient.java:3502](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L3502>) |
| `CELTIUM_SAMPLER` | [VulkanGlTexture.java:2628](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2628>) |
| `CELTIUM_SAMPLER_FILTER` | [VulkanGlTexture.java:2270](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2270>) |
| `CELTIUM_SAMPLER_REACH` | [VulkanGlTexture.java:2312](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2312>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED` | [VulkanTerrainGpuBackend.java:6035](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6035>) |
| `CELTIUM_SHADER_PROGRAM_REFUSED_SITE` | [VulkanTerrainGpuBackend.java:6046](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6046>) |
| `CELTIUM_SHARED_LAYOUT` | [VulkanGlTexture.java:1086](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1086>), [VulkanGlTexture.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1121>) |
| `CELTIUM_SHARED_LAYOUT_ABANDON` | [VulkanGlTexture.java:529](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L529>), [VulkanGlTexture.java:608](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L608>) |
| `CELTIUM_SHARED_LAYOUT_COMMIT` | [VulkanGlTexture.java:577](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L577>) |
| `CELTIUM_SHARED_LAYOUT_DECLARED` | [VulkanGlTexture.java:632](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L632>) |
| `CELTIUM_SHARED_LAYOUT_UNBOUND` | [VulkanGlTexture.java:731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L731>) |
| `CELTIUM_SKIP_DECISION` | [VulkanTextureRegistry.java:492](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L492>) |
| `CELTIUM_SKY_DEGRADED` | [CeltiumGlStub.java:6037](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L6037>) |
| `CELTIUM_SPLASH_FLIP` | [VulkanTerrainGpuBackend.java:7735](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7735>) |
| `CELTIUM_SPLASH_HANDLES` | [VulkanTerrainGpuBackend.java:7848](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7848>), [VulkanTerrainGpuBackend.java:7853](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7853>) |
| `CELTIUM_SPLASH_HOOK` | [MinecraftMixin.java:290](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L290>), [MinecraftMixin.java:295](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L295>), [MinecraftMixin.java:306](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L306>), [MinecraftMixin.java:310](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L310>), [ValidationClient.java:7084](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L7084>) |
| `CELTIUM_SPLASH_PAIRPOST` | [VulkanTerrainGpuBackend.java:7761](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7761>) |
| `CELTIUM_SPLASH_PROBE` | [VulkanTerrainGpuBackend.java:7915](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7915>) |
| `CELTIUM_SRC` | [VulkanGlTexture.java:2846](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2846>), [VulkanGlTexture.java:2967](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2967>) |
| `CELTIUM_STAGE_DUMP` | [TextureCopyService.java:704](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L704>) |
| `CELTIUM_STAGING_RELEASED` | [VulkanGlTexture.java:3592](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L3592>) |
| `CELTIUM_STALL` | [VulkanTerrainGpuBackend.java:1661](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1661>) |
| `CELTIUM_SUBMIT_ENTRY` | [FrameCoordinator.java:145](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java#L145>) |
| `CELTIUM_SUBMIT_FAULT` | [VulkanSubmitFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSubmitFault.java#L41>) |
| `CELTIUM_SWAPCHAIN` | [VulkanSwapchain.java:1813](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1813>) |
| `CELTIUM_SWAPCHAIN_FAILED` | [VulkanTerrainGpuBackend.java:11656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L11656>) |
| `CELTIUM_SWAPPOINT` | [MinecraftMixin.java:515](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L515>) |
| `CELTIUM_TARGET_FOREIGN` | [VulkanTerrainGpuBackend.java:14253](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14253>) |
| `CELTIUM_TARGET_IMAGE` | [VulkanTerrainGpuBackend.java:4669](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4669>) |
| `CELTIUM_TARGET_REFUSAL` | [VulkanTerrainGpuBackend.java:14240](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14240>), [VulkanTerrainGpuBackend.java:14260](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14260>) |
| `CELTIUM_TEARDOWN` | [VulkanTerrainGpuBackend.java:10476](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10476>) |
| `CELTIUM_TERRAIN_LAYOUT` | [VulkanTerrainPipeline.java:1121](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L1121>) |
| `CELTIUM_TEXTURE_DIAG` | [VulkanTerrainGpuBackend.java:14401](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L14401>) |
| `CELTIUM_TEXTURE_FAULT` | [VulkanTextureFault.java:41](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureFault.java#L41>) |
| `CELTIUM_TEXTURE_SITE` | [WorldVertexBufferUploaderMixin.java:686](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L686>) |
| `CELTIUM_TEX_COPY` | [CeltiumGlStub.java:5129](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5129>) |
| `CELTIUM_TEX_COPY_SERVED` | [TextureCopyService.java:636](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L636>) |
| `CELTIUM_TEX_PARAM` | [CeltiumGlStub.java:4864](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L4864>) |
| `CELTIUM_TEX_PARAM_CENSUS` | [CeltiumGlStub.java:782](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L782>) |
| `CELTIUM_TEX_PARAM_QUERY` | [CeltiumGlStub.java:5074](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L5074>) |
| `CELTIUM_TRACKER` | [CeleritasWorldRenderer.java:103](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L103>), [CeleritasWorldRenderer.java:117](<../forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer.java#L117>) |
| `CELTIUM_TRANSFORM_CENSUS` | [CeltiumGllessHint.java:394](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L394>) |
| `CELTIUM_UNIT_BIND` | [VulkanGlState.java:656](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L656>) |
| `CELTIUM_UNIT_SWITCH` | [VulkanGlState.java:796](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L796>) |
| `CELTIUM_UNKNOWN_SYMBOL` | [CeltiumGlStub.java:3010](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L3010>) |
| `CELTIUM_UNKNOWN_SYMBOLS` | [CeltiumGlStub.java:766](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L766>) |
| `CELTIUM_UNMAPPED` | [VulkanTerrainGpuBackend.java:5865](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5865>), [WorldVertexBufferUploaderMixin.java:260](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L260>) |
| `CELTIUM_UNMAPPED_LAYOUT` | [VulkanTerrainGpuBackend.java:5866](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5866>), [WorldVertexBufferUploaderMixin.java:527](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L527>) |
| `CELTIUM_UNMAPPED_REFUSED` | [WorldVertexBufferUploaderMixin.java:370](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L370>) |
| `CELTIUM_UPLOAD_CALLER` | [TextureUtilMixin.java:144](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java#L144>) |
| `CELTIUM_UPLOAD_FLUSH` | [VulkanContext.java:2731](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2731>) |
| `CELTIUM_VERIFY` | [BackendReport.java:35](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendReport.java#L35>), [CeleritasVintage.java:91](<../forge122/src/main/java/org/taumc/celeritas/CeleritasVintage.java#L91>) |
| `CELTIUM_VERTEXPOINTER` | [CeltiumGlStub.java:2901](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2901>) |
| `CELTIUM_VERTEX_COLOR_BY_PASS` | [VanillaLikeChunkVertex.java:72](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L72>) |
| `CELTIUM_VERTEX_COLOUR` | [TerrainRenderer.java:1043](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L1043>) |
| `CELTIUM_VERTEX_LIGHT` | [CompactChunkVertex.java:96](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L96>), [VanillaLikeChunkVertex.java:88](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/VanillaLikeChunkVertex.java#L88>) |
| `CELTIUM_VISIBLE` | [OcclusionCuller.java:280](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java#L280>) |
| `CELTIUM_WAIT_CENSUS` | [VulkanContext.java:1587](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1587>), [VulkanContext.java:1609](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1609>) |
| `CELTIUM_WINDOW_DRAW` | [VulkanTerrainGpuBackend.java:8976](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8976>) |
| `CELTIUM_WINDOW_ORDER` | [VulkanTerrainGpuBackend.java:7673](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7673>) |
| `CELTIUM_WINDOW_RENDERER` | [MinecraftMixin.java:466](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L466>), [MinecraftMixin.java:471](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L471>), [MinecraftMixin.java:490](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java#L490>) |
| `CELTIUM_WINDOW_SIZE_CHANGED` | [ValidationClient.java:6826](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6826>) |
| `CELTIUM_WORLD_ENTRY` | [CeltiumWorldEntryWatch.java:34](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumWorldEntryWatch.java#L34>) |
| `CELTIUM_WORLD_LOAD` | [RenderSectionManager.java:161](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L161>), [RenderSectionManager.java:163](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L163>), [RenderSectionManager.java:167](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L167>), [RenderSectionManager.java:169](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L169>), [RenderSectionManager.java:175](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L175>), [RenderSectionManager.java:178](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L178>), [SimpleWorldRenderer.java:46](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L46>), [SimpleWorldRenderer.java:49](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L49>), [SimpleWorldRenderer.java:67](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L67>), [SimpleWorldRenderer.java:69](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L69>), [SimpleWorldRenderer.java:71](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L71>), [SimpleWorldRenderer.java:73](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L73>), [SimpleWorldRenderer.java:265](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L265>), [SimpleWorldRenderer.java:271](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L271>), [SimpleWorldRenderer.java:274](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L274>), [SimpleWorldRenderer.java:277](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L277>), [SimpleWorldRenderer.java:284](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L284>), [SimpleWorldRenderer.java:291](<../common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java#L291>) |
| `CELTIUM_WORLD_MATRIX` | [VulkanTerrainGpuBackend.java:3119](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3119>) |
| `CELTIUM_WORLD_START` | [ValidationClient.java:6596](<../validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java#L6596>) |
| `CELTIUM_ZERO_HANDLE` | [VulkanGlTexture.java:2090](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L2090>) |
| `CELTIUM_ZERO_VIEW_REFUSAL` | [VulkanImmediateItemPipeline.java:287](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L287>), [VulkanImmediateTexturePipeline.java:468](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L468>) |
