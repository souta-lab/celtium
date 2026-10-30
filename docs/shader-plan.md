# Shaderpack 対応計画（前向き計画・実装指示ではない）

**位置づけ**: 優先順を変えない。現在の優先は modpack bring-up（SuperSymmetry → ATM3 → FTB / RLCraft）で、影と pack はその後
（[plan.md:82-95](<plan.md#L82-L95>)）。影・pack の成果物は Celtium 本体ではなく **Demonica fork の別 jar**（[plan.md:72-80](<plan.md#L72-L80>)、
[goal.md:60-62](<goal.md#L60-L62>)）。本稿は「既存の 1.12.2 OptiFine/ShadersMod 形式 pack が画面に出るまで」の最短経路・境界・受入・棄却条件を先に固定する。
数値は書かない。未知は unknown と書く。

## 1. 現状の証拠（file:line）

| 事実 | 証拠 | 計画への意味 |
|---|---|---|
| vanilla post は native 実行。ただし program は 7 個固定・色 attachment 1 枚 | [ShaderGroupMixin.java:63-172](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java#L63-L172>)、[PostChainBridge.java:25](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L25>)、[VulkanPostChain.java:140-148](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L140-L148>)、同 [:439](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L439>) | pass 実行・layout・submit は再利用できる。program 解決と attachment 数は pack 用に差し替える |
| shader compile は zero-answer（成功を返す） | [CeltiumGlStub.java:205-206](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L205-L206>)、同 [:480](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L480>)、同 [:7817-7821](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L7817-L7821>)、[CeltiumGllessHint.java:1042](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1042>) | pack は「ロード成功」し、draw で欠落する。ここを実装に変えるのが本計画 |
| 非 0 program の draw は拒否され、id で名指しされる | [VulkanTerrainGpuBackend.java:7170-7203](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7170-L7203>)、同 [:7214](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7214>) | 現状の pack は全 draw 欠落。ここに `name=` を足すのが最初の会計変更 |
| uniform location は常に成功し、値は GL へ流れるだけ | [CeltiumGllessHint.java:1079-1092](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java#L1079-L1092>)、[CeltiumGllessNativeTransformer.java:479-483](<../forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java#L479-L483>) | addon は uniform を「送ったつもり」で動く。Vulkan 用の値は addon 側からしか作れない |
| OptiFine / ShadersMod の mixin・依存は無い | `forge122/.../mixin/` に該当なし、[mod-compatibility.md:282](<mod-compatibility.md#L282>) | **OptiFine を統合点にしない**。pack の読み込みは addon（Demonica）が行う |
| 本体の pack frontend は削除済み。影と pack は addon | [plan.md:77](<plan.md#L77>)、[plan.md:72-80](<plan.md#L72-L80>) | core に parser / option 評価 / pass 順を書き直さない |
| 過去に実 pack のロード・pipeline 作成・画素提示まで到達した | [plan-archive-2026-10-21.md:31](<history/plan-archive-2026-10-21.md#L31>)、[shaderpack-frontend-s0-findings.md:23-58](<history/shaderpack-frontend-s0-findings.md#L23-L58>)、同 [:96-113](<history/shaderpack-frontend-s0-findings.md#L96-L113>) | 実現可能性の証拠。ただし「画素が出た」は地形 attribute・影・両 GPU の証拠ではない |
| Demonica は pack 意味論を既に持つ（最終 GLSL は明示 location / MRT out） | `demonica-src/.../ProgramBuilder.java:44-48`、`.../GlShader.java:32-37`、`.../AttributeTransformer.java:44-49`、`.../CommonTransformer.java:121-123` | 捕捉点は `GlShader`。lower の残りは version・binding・UBO・`gl_Position` 程度 |
| 地形 vertex は 24B で normal / block id を持たない | [CompactChunkVertex.java:20-27](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/vertex/format/impl/CompactChunkVertex.java#L20-L27>)、[VulkanTerrainPipeline.java:156-173](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L156-L173>) | gbuffers には属性追加が要る。Demonica に設計例（`ExtendedChunkVertexType.java:1-90`、`TerrainVertexFormatRequirements.java:20-80`） |
| target は GL FBO identity 単位。colortex / shadow 用 slot は無い | [RenderTargetRegistry.java:47-70](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java#L47-L70>)、[GllessFrameTarget.java:164](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L164>)、同 [:1294](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L1294>) | pack target は新設。既存 pair を流用しない |
| dynamic rendering は pack 用に有効化済み、pipeline cache は永続化済み | [VulkanContext.java:2513](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2513>)、同 [:2844-2856](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2844-L2856>)、同 [:2100-2115](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2100-L2115>)、同 [:2219-2248](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L2219-L2248>) | MRT pipeline と program 毎 pipeline の土台はある |
| 行列・camera の源がある | [VulkanTerrainGpuBackend.java:884](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L884>)、同 [:12372-12386](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L12372-L12386>)、[ChunkRenderMatrices.java:8](<../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices.java#L8>)、[CameraTransform.java:15-24](<../common/src/main/java/org/embeddedt/embeddium/impl/render/viewport/CameraTransform.java#L15-L24>) | `gbufferModelView/Projection`・`cameraPosition` はここから。core は Minecraft 非依存なので時刻・太陽は addon |
| shadow は本体停止中。設計と数学の記録だけ残る | [plan.md:39-41](<plan.md#L39-L41>)、[development.md:3263-3317](<development.md#L3263-L3317>) | Phase C は新規。`ShadowMapTarget` の設計・`ShadowLightMatrix` の 6 test・dump 経路は再利用 |
| 1 tag 16 行で log が切られる | [CeltiumLogLimiter.java:14](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L14>)、同 [:19-20](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CeltiumLogLimiter.java#L19-L20>) | 94 stage の列挙は 1 行 summary + `packCensusFile` へ |

**手元の実 pack の反例**: `BSL_v10.1.8.zip`（sha256 は [s0-findings:25](<history/shaderpack-frontend-s0-findings.md#L25>)）は
`gbuffers_terrain.glsl:424-442` で colortex0/3/6/7/8 を書き、`composite.glsl:254-256` が depthtex0/1 を無条件に読み、
既定で `LIGHT_SHAFT`（`settings.glsl:205`）が colortex1/noisetex/shadowtex を読む。**BSL は Phase A の対象外**。

## 2. 最短経路の結論

1. **pack の意味論は addon、実行は core。** core に足すのは汎用の 4 機能だけ: program 登録（SPIR-V + layout）、
   pass 実行（MRT + depth、dynamic rendering）、target 所有（colortex / depthtex / shadowtex）、uniform block 書込み（std140 ring）。
2. **捕捉は addon の `GlShader`**（最終 GLSL 文字列と program 名が同時にある）。stub の `glShaderSource` 引数を読む案は、
   pointer/長さの marshalling と「文字列が消える」問題で machinery が増える。OptiFine は存在しないので捕捉点にならない。
3. **GLSL→SPIR-V は addon 内**。現状は build 時のみ（[celtium-vulkan/build.gradle:117-265](<../celtium-vulkan/build.gradle#L117-L265>)）、
   runtime の shaderc 依存は無い（同 [:32-75](<../celtium-vulkan/build.gradle#L32-L75>)）。ここが唯一の新規大型部品。
4. **stub に GL program 実行器を足さない。** pack の draw は「program + target + uniform + pass 順」という意味で渡し、
   再現不能なものは名前付き拒否にする（[glstub-strategy.md:60-70](<glstub-strategy.md#L60-L70>)）。
5. **実 pack が「正しく」出る最短は Phase B（pack 自身の影 OFF）**。Phase A は実行器の最小実証で、
   条件を満たす実 pack は手元に無い（あれば A が最短）。A の実 pack 受入は scanner の結果次第で、無ければ B が最初の実 pack 受入になる。

## 3. フェーズ表

| Phase | 目的と範囲 | 主な追加（core / addon） | GPU コスト（構造） | 受入の要 | 棄却条件 |
|---|---|---|---|---|---|
| 0 | 捕捉と lower の実証（描画しない） | addon: `PackCapture` / `VulkanGlslLowering` / `PackCompiler`。core: なし | 0 | 実 pack の stage が理由付きで lower される。CPU test のみ | addon が Celtium 上で起動しない / lower 成功率が低い |
| A | composite のみ。colortex0 + depthtex0 + noisetex だけを読む pack | core: `PackGpuBackend`、`VulkanPackChain`、`PackTargetSet`、`PackProgram`、`PackUniformRing`、`PackStatistics` | 画面 fullscreen × pass 数 + frame→colortex0 copy 1 回 | poison が提示 frame に出る / VUID 0 / 両 GPU / chain は all-or-nothing | 条件を満たす実 pack が無い / copy が支配的 |
| B | gbuffers（terrain / sky / water）+ dimension + fog | core: `PackChunkVertex`、`VulkanPackTerrainPipeline`、MRT、depthtex1。addon: Celtium terrain header、block id mapper | MRT 書込み + vertex 属性増 + depth copy 1 回（数値 unknown） | pack 自身の影 OFF で reference と比較（取れた場合だけ「正しい」） | mc_Entity/normal のコスト過大 / depthtex1 不可 / reference が作れない |
| C | shadow pass + shadowtex sampling | core: `ShadowTarget`、`ShadowLightMatrix`、shadow program、shadow pass 記録。addon: shadow pass 順 | terrain 2 回目 draw + D32 target + PCF | 深度 PNG が一様でない / 太陽方向に追従 / VUID 0 / 両 GPU | コスト超過 / 座標系不一致 / B 未達 |
| D | entities / hand / weather / clouds / translucent ordering / blend・clear flags / custom images | core: pipeline state key、entity draw、translucency。addon: 既存 option 評価 | 既存 draw 数に比例（unknown） | program ごとの run/refuse 表 + feature 別 leg | 費用対効果 / compute 必須 pack |

### Phase 0（捕捉・lower）
- 変更: addon `GlShader.java:32-37` に capture を 1 行。新規 `PackCapture`（stage 名・GLSL version・sha256・bounded dump）、
  `VulkanGlslLowering`（`#version 450`、sampler binding、`layout(std140) uniform PackUniforms`、`gl_Position` の depth remap、
  attribute location は `AttributeTransformer.java:44-49` と `ProgramCreator.java:24-31` の固定表から付与）、`PackCompiler`（shaderc）。
- データ: `ProgramBuilder.java:44-48` → `GlShader(name, finalSource)` → lower → SPIR-V 2 stage + sampler/block 情報。
- uniform: なし。GPU: 0。受入: `CELTIUM_PACK_STAGE` は 1 行 summary に集約し、全 stage は `-Dceltium.vulkan.packCensusFile` へ。
  `PackLoweringTest` が SPIR-V magic / entrypoint / binding / block offset を検査。**画素の主張をしない**。

### Phase A（composite のみ）
- 前提ゲート: 実 pack の composite/final が読む sampler ⊆ {colortex0, depthtex0, noisetex}、deferred / shadow 無し。
  scanner は addon の解析済み program から sampler 集合を出す（CPU のみ）。満たす pack が無ければ fixture pack で実行器だけ検証する。
- データ: 既存 terrain は従来どおり frame image へ。chain 開始時に **frame → colortex0 を 1 回 copy**（alias しない。
  colortex0 を sample しながら同じ image へ書く feedback を避ける）。pass は colortex0 / depthtex0 / noise を sample し main へ書く。
  最後の pass が main を書かない pack は core が colortex0 → main を blit する（最終出力の規則を addon が宣言）。
- uniform と源: `gbufferProjection/Inverse`・`gbufferModelView/Inverse`（`ChunkRenderMatrices` と mirror、backend:884 /
  :12372-12386）、`cameraPosition`（`CameraTransform.x/y/z`）、`viewWidth/Height`、`near/far`、`frameTimeCounter`・`worldTime`・
  `rainStrength`・`isEyeInWater`・`sunPosition`（addon の uniform supplier）、noise（addon の custom texture）。
- 受入: (1) CPU: lower + block layout test。(2) 同一 leg で pack OFF → ON、同一 tick / camera / pose。colortex0 を `FF00FF`
  （AARRGGBB、[verification-traps.md](<verification-traps.md#3-poison-の色は-aarrggbb-色指定を読み違えると無意味な測定になる>)）で
  poison し、提示 frame にその色が出ること。(3) chain の pass が 1 つでも拒否なら全体を拒否し frame を保持（`CELTIUM_PACK_CHAIN_REFUSED`）。
  (4) VUID 0、AMD / Intel。(5) perf leg は `presentedDumpEvery=0` で OFF 比を記録（数値は未確定）。
- 棄却: ゲートを通る実 pack が存在しない（A は fixture 実証に留める）、copy が frame 時間を支配する、all-or-nothing が成立しない。

### Phase B（gbuffers）
- 変更 core: `PackChunkVertex`（`mc_Entity` u32、normal、`mc_midTexCoord`、`at_tangent`、`at_midBlock` を requirements で取捨。
  Demonica `ExtendedChunkVertexType.java:1-90` / `TerrainVertexFormatRequirements.java:20-80` の設計を compact format へ移植）、
  `VulkanPackTerrainPipeline`（MRT、pack vertex input、組み込み uniform なし）、`TerrainRenderer.pipelineFor` の level 分岐
  （[TerrainRenderer.java:307-330](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java#L307-L330>)）、
  `PackTargetSet` に colortex0..7 + depthtex0/1、`DRAWBUFFERS` → attachment の対応。
- 変更 addon: `CeleritasTransformer` → `CeltiumTransformer` + `celtium:include/chunk_vertex.glsl`（24B decode を pack 変数へ）、
  `CeleritasTerrainPipeline` / `IrisCeleritasChunkProgramOverrides` の置換、`block.properties` → `mc_Entity` mapper
  （common の hook。mesher は core 側なので addon を直接呼ばない）。
- データ: mesher が pack 属性を書き、terrain pass が MRT へ書く。opaque 後に depth image → depthtex1 を 1 回 copy。
  addon が deferred / composite を `packRunPasses` で投入する。
- uniform: A の全部 + `shadowModelView/Projection`（C まで拒否）、`fogColor/fogDensity`（`VulkanFogMirror`）、`skyColor`、
  `eyeBrightnessSmooth`、`moonPhase`、`alphaTestRef`、block id。源は既存 mirror と addon の supplier。
- 受入: CPU: vertex decode / attribute mapping / block id map / MRT layout。leg: 影 OFF の実 pack を level 2、reference は
  (a) 同一 scene の pack OFF、(b) 可能なら Demonica+Celeritas の GL arm（未確定）。colortexN dump が材質で変わること、VUID 0、両 GPU。
  **「正しい」は reference arm が取れた場合だけ主張**。
- 棄却: `mc_Entity`/normal の帯域・mesher コストが過大、depthtex1 が実装できない（BSL は無条件に読む）、reference が作れない。

### Phase C（shadow）
- 変更 core: `ShadowTarget`（D32、compare sampler、depth-only pass。[development.md:3277-3290](<development.md#L3277-L3290>) の設計）、
  `ShadowLightMatrix`（[development.md:3310-3325](<development.md#L3310-L3325>) の 6 test を再作成）、shadow pass を
  `ensureFrameTarget` 直後に記録（[development.md:3267-3272](<development.md#L3267-L3272>)）、`shadowtex0/1`、`shadowModelView/Projection`。
- 変更 addon: shadow → gbuffers → deferred → composite の順、`ShadowRenderer` の置換。`shadowcomp` compute は拒否。
- コスト: terrain の 2 回目 draw（同じ indirect buffer、pipeline だけ差替え）+ D32 target + composite の PCF。歴史的に in-core PCF は
  +10% p50（[plan.md:39-41](<plan.md#L39-L41>)、**現在値ではない**）。measure。
- 受入: `TextureCopyService.celtium$dumpDepthPng`（[:930](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java#L930>)）が
  一様でない、太陽方向に影が追従、pack の影 ON で VUID 0、両 GPU、reference。deferred: `shadowcomp`、colored shadow、entity shadow。
- 棄却: コスト超過、pack の `shadowProjection` 規約と一致しない、B 未達。

### Phase D（残り）
- `gbuffers_textured_lit` / `basic` / `entities` / `hand` / `weather` / `clouds` / `block` / `beacon` / `damagedblock`、
  translucent ordering、`blend.<program>`・`alphaTest.<program>`・`colortexNClearColor`、custom images、oldLighting、per-dimension。
- core: pipeline state key に blend / depth / clear、entity draw へ pack program、translucency の depth 取扱い。
- 受入: program ごとの run/refuse 表、feature 別 leg、コスト。棄却: entity/hand の費用対効果、compute 必須 pack。

## 4. 実装対象ファイル一覧

| 種別 | ファイル | Phase |
|---|---|---|
| 新規 core | `common/.../backend/PackGpuBackend.java`（`registerProgram` / `runPasses` / `terrainLevel` / `refuse`）、`PackPass.java`、`PackProgramDesc.java` | A |
| 新規 core | `celtium-vulkan/.../VulkanPackChain.java`、`PackTargetSet.java`、`PackProgram.java`、`PackUniformRing.java`、`PackStatistics.java` | A |
| 変更 core | `VulkanTerrainGpuBackend.java`（`implements PackGpuBackend`、frame 順、program id → name、level、拒否会計） | A |
| 変更 core | `TerrainRenderer.java`（level 分岐）、`VulkanTerrainPipeline.java` または新規 `VulkanPackTerrainPipeline.java`（MRT / pack vertex） | B |
| 変更 core | `common/.../vertex/format/impl/CompactChunkVertex.java` または新規 `PackChunkVertex.java`、`ChunkVertexType` 選択 | B |
| 変更 core | `RenderTargetRegistry.java`（`PACK` slot）、`GllessFrameTarget.java` は触らない（pair と混ぜない） | A |
| 新規 core | `ShadowTarget.java`、`ShadowLightMatrix.java`、`PackShadowPass`（記録） | C |
| 変更 addon | `GlShader.java`、`ProgramCreator.java`、新規 `PackCapture` / `VulkanGlslLowering` / `PackCompiler` / `PackUniformBlock` | 0 |
| 変更 addon | `ShaderTransformer.java`（lower hook）、`CeleritasTransformer.java` → `CeltiumTransformer.java` + `celtium:include/chunk_vertex.glsl` | B |
| 変更 addon | `CompositeRenderer` / `FinalPassRenderer` / `DeferredWorldRenderingPipeline` / `ShadowRenderer` / `ShadowRenderTargets`（GL draw → `packRunPasses`） | A/B/C |
| 変更 addon | `gl/uniform/*`（18 class）と `ProgramUniforms`（GL 書込み → block writer）、`gl/texture`・`gl/framebuffer`（pack target の binding） | A |
| テスト | `PackLoweringTest`（addon）、`PackUniformLayoutTest`、`PackVertexDecodeTest`、`PackPipelineKeyTest`（core） | 0/A/B |
| 文書 | `docs/instruments.md` を `python3 scripts/instrument-index.py` で再生成（新 property / marker と同じ差分） | A |

## 5. 高速性・拒否会計・ゲート

- **compile は pack ロード時 1 回**。key = pack zip sha256 + program source sha256 + option hash。失敗は理由付きで保持し、
  毎 frame 再試行しない。SPIR-V と pipeline は `VulkanContext.sharedPipelineCache()` / `pipelineCacheFile` に載せる。
- **pipeline key** = (SPIR-V hash, pass, attachment formats/count, depth format, blend, cull, depth test/write)。毎 frame 作らない。
  MRT 数が変わるのは pass 毎なので、dynamic rendering（`dynamicRenderingSupported`）で render pass の組合せ爆発を避ける。
- **uniform** は frame ごとの host-visible ring に program 毎 1 block。std140 は addon が shader 側の block 宣言ごと生成するので
  offset が drift しない。毎 draw の allocation をしない。変化した member だけ書く（addon の uniform は既に値を cache している）。
- **descriptor** は program × frame slot に 1 set。sampler は binding 番号で固定。pass 毎の layout 変更をしない。
- **pass 結合**: post の全 pass を 1 回の acquire → record → release → submit にまとめる（[PostChainBridge.java:68-100](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PostChainBridge.java#L68-L100>)）。
  gbuffer の terrain draw は既存 terrain render pass の中（追加 pass を開けない）。readback しない（dump は diagnostic のみ）。
- **OFF はゼロコスト**: level 0 では登録・target 生成・分岐をしない。既存 pipeline に触らない。level 変更は resource reload を要求する
  （vertex format が変わるため）。
- **拒否会計**: 全 program に pack 名を付ける。`CELTIUM_PACK_REFUSED name= stage= reason=` を理由 enum で出し、
  `PackStatistics` に数える。summary は `CELTIUM_PACK_SUMMARY level= programs= compiled= refused= passes= targets=` の 1 行。
  program が 1 つでも拒否なら、その chain は **記録しない**（既存の `supportsAll` / `run-refused` と同じ transactional 方式）。
  未解決 sampler を dummy texture で代用しない。compute / geometry / tessellation は拒否。`shadowtex` を level 2 で要求されたら拒否。
  既存 `CELTIUM_SHADER_PROGRAM_REFUSED` に `name=` を追加し、id しか無い場合も残す。
- **ゲート**: `-Dceltium.vulkan.shaderPackLevel=0`（0=off, 1=composite, 2=gbuffers, 3=shadow）を既定 0。`-Dceltium.vulkan.packCensusFile`
  は diagnostic で既定なし。addon の Video Settings に level と refuse 件数を出し、「partial」と明記する。

## 6. 受入条件（共通）

- GPU の主張には full JAR SHA-256・GPU・scene・camera・flags・測定区間・leg を紐づける（[AGENTS.md](<../AGENTS.md>)）。
  correctness leg は validation ON、perf leg は KPI flags（validation off / presentReadback off / `presentedDumpEvery=0`）で分ける。
- CPU test は「lower が通る」「block offset が一致する」「vertex decode が一致する」「pipeline key が安定する」まで。
  1 frame の見た目や「pack がロードできた」を「正しい」にしない。
- pack OFF / ON は同一 run 内の対照を優先し、scene・camera・tick・天候・pose を揃える。poison は AARRGGBB で適用 dump を確認する。
- chain が拒否された leg は「描画されていない」であり、成功ではない。

## 7. リスクと棄却条件

| リスク | 判別 | 棄却するもの |
|---|---|---|
| Demonica fork が Celtium（GL-less）上で起動・pack ロードできない | Phase 0 の 1 leg を time-box。起動・ロード・stage 捕捉の有無 | addon 再利用の前提。代替は削除済み in-core frontend 設計の再評価（A/B/C の見積りは無効） |
| lower が実 pack の stage で通らない（`#version`、extension、GL 固有組み込み） | 成功率と理由の内訳。閾値は未確定 | Phase A 以降。addon の print 段で lower する案へ切替 |
| shaderc native の同梱・配布が受け入れられない | addon の jar 構成レビュー | Phase 0 以降。GL fallback は無い |
| `mc_Entity` / normal の属性追加コストが過大 | mesher 時間・vertex 帯域・frame 時間 | Phase B（level 2 を限定） |
| depthtex1 が実装できない | BSL は無条件に読むため B の gate で判明 | Phase B の BSL 級 pack |
| shadow のコスト / 座標系が合わない | 深度 PNG・影の追従・frame 時間 | Phase C（level 3 を出さない） |
| license 境界を越える（AGPL の glsl-transformer / shaderc 周辺） | 依存と配置のレビュー | core への配置。addon 内に留める限り可 |
| reference arm（Demonica+Celeritas）が作れない | leg 設計時に確認 | 「正しい」の主張。coverage / 構造の受入に落とす |

## 8. 最初の 1 手（これ 1 つで経路の可否が決まる）

**addon の `GlShader` に capture を 1 行入れ、実 pack の全 stage を offline で SPIR-V 化する。描画は変えない。**
- 変更は addon の 1 file + 新規 3 class（`PackCapture` / `VulkanGlslLowering` / `PackCompiler`）。core は触らない。
- 受入: 実 pack（BSL 10.1.8）で stage 名・GLSL version・sampler 集合・attribute 使用・block 候補が出る。
  各 stage の lower 成否と理由が `CELTIUM_PACK_STAGE` 1 行 + census file に残る。`PackLoweringTest` が SPIR-V を検査する。
- ここで落ちれば、Phase A 以降の議論を止めて結論を書き換える（addon 起動不能なら in-core 設計の再評価、lower 不能なら方式変更）。
  ここが通れば、Phase A は「既存の `PostChainBridge` の handoff に program と target を差し込む」作業になる。

## 9. 未確定（unknown）

- Phase A のゲートを通る実 pack が存在するか（手元には BSL のみ。Sildur's 系の実 sampler 集合は未確認）。
- shaderc の同梱サイズ・native 配布・起動時間。
- `mc_Entity` / normal 追加後の vertex stride と帯域（24B から何 B になるか）。
- Demonica fork の Celtium 接続（Celeritas pin の除去、GLSM の扱い）に要する工数。
- 両 GPU での実測コスト、reference arm の較正可否、Intel での MRT / dynamic rendering の実挙動。
