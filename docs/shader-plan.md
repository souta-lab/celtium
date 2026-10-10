# Celtium shader plan

**状態: Deferred — 計画・調査のみ。影対応の実装は「フルVK化」と「主要modのshaderless対応」の両方が完了してから。**

調査日: 2026-10-10（JST）。対象: Minecraft 1.12.2 / CleanroomMC / Celtium。Javaの配布ターゲットは `--release 21`。ゲーム・ビルド・検証でJDK 25を使う方針は維持する。

## 1. 決定と着手条件

目指すのは、既存のOptiFine / ShadersMod / Iris形式の**パックをユーザーが書き換えずに読み込み、Celtiumのnative Vulkanで描くこと**。既存影modのOpenGLレンダラをそのまま起動することとは分ける。パックのGLSLはロード時・設定変更時に変換し、SPIR-Vとnative pipelineとして実行する。

開発順序は次に固定する。

1. 全vanillaを含むCeltium本体のフルVK化を完成させる。
2. 主要modのshaderless描画・起動・ライフサイクル対応を完成させる。
3. その確定したnative描画基盤へシェーダーパック対応を追加する。

この文書の存在を影対応の着手指示と解釈しない。以下のgateを受入記録で満たすまで、パックローダー、変換器、shadow pass、影用UI・依存ライブラリの実装を開始しない。現在の本体作業で必要なバニラ `ShaderGroup`、spectator効果、entity outline、FBO/postprocessは延期対象に含めない。

| Gate | 着手前に必要な証拠 |
| --- | --- |
| G1: 全vanilla native | 地形、item/hand、entity/TESR、particle、sky/cloud/weather、GUI/font/HUD、半透明、全dimension、バニラpostprocessの描画と状態を受入済み。単一flat worldやformat受付だけで判定しない。 |
| G2: GLなし通常実行 | GL contextを作らず、通常frameがGL call/readback/bridge/copyback/swap/fallbackに依存せず成立する。identity照会省略だけでは不可。 |
| G3: 実際の提示 | native最終画像がswapchainから実window/compositorへ届く証拠がある。GPU画像の読み戻しと実window出力を区別する。 |
| G4: 主要mod | 対象jar/version/hashを固定したmanifestと互換表がある。各modの実描画・reload・world switchを確認し、未完の重大な描画経路を残さない。JEIだけで全主要mod完了とはしない。 |
| G5: 複数GPUと寿命 | AMD RX6400 / Intel UHD630で必要な場面、resize/reload/dimension変更、submit/wait/割当失敗時の資源寿命が成立する。未検証vendor/OSを明記する。 |
| G6: 基準固定 | shaderless基準jar・画像・性能・native resource/state APIを固定し、既存P0/P1を受入で閉じる。 |

「主要mod」は先に実際のmodpackからリストを固定する。GUI/item、custom model/TESR、半透明・fluid、particle/effect、動的texture/FBO、直接GL/GLSLという描画経路を代表できる構成にする。ここで新たに全mod対応を完成条件へ追加しない。

## 2. 現repoから確認した土台と不足

作業repo: `/home/s0u7a/projects/celtium`。調査開始HEADは `1f9c7486be01446bd7b8dcc8d4f6c4ad92b44190`、後続確認HEADは `299f884906a714cd1935c46fa33b21571c6ffd7f`。DSHが並行して本体を変更しているため、この節は**調査時点のsnapshot**であり、最新の完了判定ではない。後続確認ではbackendと `EntityRendererMixin` がdirtyで、tracked差分のSHA-256は `3a6096ec53253631ee754e5aaf5221bdb697021072fad94ae6ac2c0fefd03e2f` だった。

| 実体 | 確認結果と影対応への意味 |
| --- | --- |
| `celtium-vulkan/.../VulkanTerrainGpuBackend.java` | terrain、immediate、model、提示のnative経路がある。一方でGL由来のmatrix/program参照、未知custom programの拒否、bridge/copyback依存が残る。既存packのnative実行基盤が完成したとは扱わない。 |
| `VulkanFrameTarget.java` / `VulkanSwapchain.java` | target・色/深度・同期・提示の経験を使える。影対応では移行用interopを再導入せず、フルVK完了後のnative所有APIへ接続する。 |
| `celtium-vulkan/build.gradle` / `SpirvShaders.java` | 内蔵GLSLのビルド時SPIR-V生成とclasspathロードがある。現在の実行時パックコンパイラではない。`SpirvShaders` の「チェックイン済みbinary」というコメントだけを仕様にせず、実際のGradle生成taskを基準にする。 |
| `modern/src/main/shaders_java/net/irisshaders/iris/` | パック、include、option、properties、uniform、FOSS transformation等の既存ソースがある。modern専用のMinecraft型やGL型に依存する部分を分離してから再利用判断する。ディレクトリの存在は1.12.2での組込み・動作を意味しない。 |
| `ShaderModBridge.java` | Iris APIの検出・設定画面入口があるが、packをVKで実行する実装ではない。Celtium側の明示的なshader入口を設計し、Iris全APIを偽装しない。 |
| `buildSrc/.../celeritas.shader-conventions.gradle.kts` | modernのshader source setと `org.taumc:glsl-transformation-lib`、jcppの構成がある。1.12.2用buildへ一括で適用しない。 |
| `.celtium-work/major-mods/` | 確認時に置かれていたjarは `jei_1.12.2-4.22.0.1035.jar`。主要modの網羅は別のmanifest・受入で示す必要がある。 |
| README / goal / plan | 初期hybrid説明や古い到達点が残る。現状の判断は最新HEAD、effective flags、artifact、画像を優先する。影対応開始時に本節も更新する。 |

本計画は [本体plan](plan.md) の後続工程であり、active goalのID・完了条件・最後の指示を変更しない。

## 3. MinecraftのVulkan影対応の実例

READMEの主張、ソース上の機構、作者の実描画報告、こちらの実機検証を区別する。外部modは今回ゲームを起動して検証していない。

| プロジェクト | 確認できた方式・状況 | Celtiumでの扱い |
| --- | --- | --- |
| **Vitrail Shaders** | Minecraft 26.2/26.3のnative Vulkan上でOptiFine形式packを扱う。tokenベースのGLSL変換、program単位のinterface統合、target/flip管理を確認。公開互換表にはBSL/Complementary等を実際に描いた記録と画像差がある。[R1–R6] | **主要な設計参考**。言語変換・target寿命・失敗例を調べる。26.xのGPU APIやMixin、reversed-Z設定をそのまま移植しない。 |
| **Aetherium** | native Vulkan用のGLSL AST lowering、std140 uniform統合、stage location、shaderc computeコンパイルを確認。公開22pack表はコンパイル結果が中心で、描画Verdictには未記入が残る。[R7–R11] | **変換・bindingの比較参考**。「22packの見た目が完全互換」とは扱わない。Java25出力とAGPLのtransformer依存を含むため、一括取込み候補にしない。 |
| **Iris-Vulkan-Port** | 非公式の実験fork。VulkanModへIrisのterrain/shadow/compositeを接続すると説明。shaderc/UBO変換ソースを確認したが、regex処理・custom VulkanMod jar依存がある。[R12–R14] | interfaceやterrain接続の参考。動作・配布再現性を別途証明するまで製品基盤にしない。Iris公式の完成済VK版と混同しない。 |
| **Beryl** | VulkanMod上の専用pipelineと内蔵pack。既存OptiFine/Iris pack互換ローダーではない。配布ページはARR。[R15] | 専用pack方式の比較対象。コード再利用は計画しない。 |
| **Sulkan** | Minecraft native Vulkan向けの独自pack形式。無改変のIris/OptiFine packは非対応と明記。[R16] | 言語・formatを新設する代案として把握。今回の既存pack互換の主案にはしない。 |
| **Vulkanite** | GL/VK interopでIrisへhardware ray tracing等の追加passを提供する方式。[R17] | 純VK pack互換の証拠には使わない。GLなし・広いGPU対応というCeltiumの条件に合わない。 |
| **Aperture** | Iris側の別pack API。公式migration資料は新しいpipeline記述を説明するが、資料自身が古くSlangへ移行済みと注意している。[R18] | 将来の別format候補。既存GLSL pack互換の代替として先に採用しない。最新APIを再調査してから独立判断する。 |

### 1.12.2 / Celeritas周辺

| プロジェクト | 確認結果 | 再利用候補・制約 |
| --- | --- | --- |
| **Pintonium** | Celeritas forkへ1.12.2 shaderpack supportを追加。`common-shaders` をForge122へ組み込む構成を確認。公式配布説明にはpack別の設定制限・描画不具合が残る。[R19–R20] | loader、option、legacy material mapping、terrainの接続点を比較する第一候補。GL shader/FBO/state処理はnativeで置き換える。 |
| **Actinium** | Cleanroom 1.12.2でCeleritas・GLSM・Iris風pipelineを統合。実験段階で、BSL等を保証済一覧ではなく検証対象としている。[R21–R22] | 1.12の描画phase・mod統合の知見を参考にする。rootはGPL、部品は別licenseなので全体をLGPL扱いしない。 |
| **Demonica** | Cleanroom上の別途導入Celeritasに接続する影mod。GL contextを使い、Celeritas jarをpin・hashで確認する構成。[R23–R24] | addonとしての境界、Celeritas seam、terrain shadowの接続を参考にする。現在の独自コード・変更とtransformerはAGPLで、GLSMも丸ごと持ち込まない。 |
| **Spectra** | Iris/OculusのForge1.12.2実験backport。READMEではVintagiumとMixinBooter依存。[R25] | 旧版移植の比較参考。Celtium/Cleanroom用の直接導入候補ではない。 |

## 4. 採用する方式

**packのCPU frontendを選んで再利用し、描画backendはCeltiumのnative pipelineとして実装する。**

| 案 | 判断 |
| --- | --- |
| Celeritas/Pintonium系frontend + native Vulkan backend | **主案**。手元のFOSS loader/設定/解析を先に評価でき、現rendererを保ちながらpack互換を追加できる。 |
| Vitrail/Aetherium丸ごと1.12.2へ移植 | 26.x GPU API、頂点形式、registry、Java条件、license差の移植範囲が広い。機構を比較し、必要な部品単位で判断する。 |
| Demonica/Actiniumを無改変で併用 | GL context・GLSM・独自terrain/FBO所有の前提が合わない。将来は明示的adapterを作る場合のみ検討する。 |
| VulkanModへ全面乗換え | CeltiumのForge/Cleanroom API・Celeritas mesher・既存mod成果を再接続する工数が生じる。本計画では採用しない。 |
| 専用native packのみ | 最初の開発用診断packには使うが、既存pack互換を放棄する製品方針にしない。 |
| Zink / 汎用GL shader APIエミュレーション | 採用しない。今回扱うのはpack仕様とMinecraftの描画意味であり、任意のGLアプリ全体ではない。 |

最初の完成範囲は、名前と版・設定を固定した少数の既存pack。初期候補は**BSL、Complementaryの通常preset、Sildur'sの軽いpreset**。各zipが1.12.2のGL参照で正常に描けることを先に確認し、可変の「最新版」を受入対象にしない。[R35–R37]

## 5. 将来のコード境界

以下は将来の責務案であり、現在このclass/moduleを追加する指示ではない。

| 責務 | 入力・出力・所有 |
| --- | --- |
| Pack frontend | zip/folder、include、option/profile、dimension override、properties、custom texture → GL handleやMinecraft modern型を含まないimmutableなpack記述。既存frontendをこの境界へ分離する。 |
| GLSL lowering / compiler | stage群とpack ABI → Vulkan GLSL、SPIR-V、descriptor/UBO/vertex/attachmentのinterface記述。CPU側の非描画処理として独立する。 |
| Pack runtime | native image/view/sampler、color targetのmain/alt、history、shadow、pipelineをpack generation単位で所有。既存native資源管理を使い、第二のownerを作らない。 |
| Minecraft/Forge adapter | 既存のterrain/immediate/model/TESR/hand等のdraw packetへpass種別・material・state・uniform snapshotを供給する。描画をentity名ごとの特例へ分解しない。 |
| Frame executor | targetのread/write、draw順、barrier、submit/完了・retireを既存native frame契約へ接続する。巨大backendへpack管理全体を足さない。 |
| 設定・診断 | pack選択、pack自身のoption、unsupported capability、compile error、対応状況を表示する。描画失敗時はGLへ戻さずnative shaderlessへ戻す。 |

native renderer本体とpack engineのpackageを分ける。GL resource classやmodern専用Mixinをfrontendと一緒に引き込まない。既存の公開Minecraft/Forge署名・event順は維持し、必要なshader hookは意味の分かる限定APIで追加する。

主要modのshaderless受入と、そのmodが影packのmaterial・shadow passへ正しく参加する受入は別に行う。独自shader/effectを持つmodには、既存のnative adapterへpass・depth・blend・materialの契約を追加する。パックのGLSLを変換できても、modの任意の `glCreateShader` / `glUniform` / FBO操作を自動的に扱えることにはならない。対応対象の具体的なeffectを調べてnative passへ接続し、汎用GL program実行器へ範囲を広げない。

## 6. 変換とデータの契約

### 6.1 GLSLをコンパイルする前

- include・macro・設定・`MC_VERSION=11202` を解決する。旧数値ID、metadata/state selector、`world0/world-1/world1` 等のdimension別programを扱い、無条件にbaseとoverrideを混ぜない。
- `DRAWBUFFERS`、`RENDERTARGETS`、format/mipmap/clear等のdirectiveを、コメントと条件分岐を保持した段階で解析する。コメント除去後のGLSLだけから拾わない。[R3–R4, R29]
- legacy builtin（matrix、vertex/color/normal、texture/lightmap、fog）、attribute/varying、fragment outputをnative ABIへloweringする。単純な全置換やregexだけを一般解にしない。
- vertex/fragment（将来はgeometry等）を**program単位**で照合する。location、sampler binding、UBO member順を決定的に割り当て、stage間で一致させる。[R2, R9]
- Vulkan1.2を現行compiler targetの出発点にし、フルVK本体で確定した最低API/featureへ合わせる。影のためだけに1.3/1.4やmesh shaderを必須化しない。
- shaderc/glslangでSPIR-Vへ変換し、layoutとreflectionを照合する。既存内蔵shaderのビルド時コンパイルと、ユーザーpackの実行時コンパイルを分ける。[R27–R28, R34]

GLSL parserの第一候補は、現Celeritasの `org.taumc:glsl-transformation-lib` 系。Vulkan loweringは別に設計する。Vitrailのtoken方式とAST方式を小さな実pack corpusで比較し、frontendを複数並立させない。単なるGLSL文法version更新でOpenGLの描画意味も再現できるとは扱わない。

### 6.2 Uniform / texture / state

- frame、camera、world、pass、drawの更新頻度を分ける。time/rain/wetness/eye brightness、cameraとprevious camera、matrixとinverse/previous、sun/shadow、held item、entity/blockEntity ID等はMinecraft/Forgeの元データから得る。GL状態を問い合わせない。
- `MC_GL_VERSION` / `MC_GLSL_VERSION` 等のlegacy macroはpackに提供する互換dialectとして定義し、存在しないGL contextから照会しない。Iris feature flagは実装・GPU capabilityが本当に満たすものだけ公開する。
- std140のscalar/vector/matrix/array、boolの表現、offset/strideをコンパイル結果と照合する。`vec3`やmatrixをJava側の推測だけで詰めない。binding/memberの順序は起動ごとに変わらない形にする。
- frame/draw UBOはin-flight frameが読む値を上書きしない。descriptorとsampler、texture/image generationも同じ寿命に揃える。
- `mc_Entity`、`entityId`、`blockEntityId`、旧IDのfallback、modのnamespaced IDを明示的に対応させる。packのID変更がmeshed attributeへ反映される再build条件を決める。[R30]
- `mc_midTexCoord` はquadの実UV領域から生成し、spriteの中心で代用しない。normal/tangent/handedness、必要ならmid-block等をmesh生成・model/TESR共通経路で供給する。[R31]
- normal/specular/PBR atlasは既存texture registryのstitch・animation・mip・reload世代と揃える。terrainだけPBRが付き、entity/TESRが別のtextureを参照する構造にしない。
- unknown uniform/featureは出典・fallback値・影響を記録する。必須の値や機能をzero/stubで埋めて「互換」と報告しない。未知のrequired featureはpack/presetを理由付きで拒否する。

### 6.3 座標と深度

packに公開するOpenGL式のmatrix/depthと、実際にVKがrasterizeするclip座標・depthを分ける。clip Z、screen Y、front face/cull、`gl_FragCoord`、shadow compare、depth texture、hand depthを一つの契約で変換する。

Celtiumは1.12.2のnative基準に合わせる。Vitrailのreversed-Z補正を機械的にコピーしない。packによるvertex displacement、水・草の揺れ、TAA jitterにも同じ変換を使い、通常passとshadow passの形状を一致させる。

## 7. 影・gbuffer・合成の実装

| 部分 | 必要な実装 |
| --- | --- |
| Shadow | light camera/frustum、terrainの再利用draw、entity/TESR/hand等の参加条件、alpha cutout、translucent shadow、depth/color target、compare sampler、packのshadow distance/interval/filter指定。main cameraの可視chunkだけを流用しない。 |
| Gbuffers | terrain、water、entity、block entity、hand、particle、sky/cloud/weather等をpack programのfallback表へ接続する。既存のgeneric drawを再利用し、mob/TESRごとの実装追加を基本方式にしない。 |
| Color targets | logical colortexからVk image/view/formatへ対応。main/altのflip、clear無効のhistory、read/write側、mip、resize、generationを明示する。target数と同時attachment数を混同しない。 |
| Prepare / deferred | opaqueの前後とtranslucentの前という意味を再現する。1.12の実event/hand描画位置に合わせ、26.xのhook位置をそのまま使わない。 |
| Composite / final | translucentを含むsceneと各depthを読み、native fullscreen passから既存のnative最終合成・swapchainへ接続。GUI/HUDの位置と色空間を守り、tone map・gammaを二重適用しない。 |
| Temporal | previous matrix/camera、jitter、history target、露出、frame counterの更新を一貫させる。reload/resize/teleport/dimension/pack変更では必要な履歴を無効化する。 |

基本の切れ目は「begin → shadow → prepare → opaque gbuffers → deferred → translucent gbuffers → composite/final」。これは設計上の基礎形であり、各packのprogram無効化・fallback・hand位置、shadowcompやcompute等を無条件で省く規則にはしない。[R3, R29]

imageごとのread/writeを宣言し、attachment→sampled、depth→sampled、将来のstorage→sampledのbarrierを生成する。同一imageを無条件に同じpassでsample/writeしない。flipを全target一括のtoggleにせず、programのwrite maskと個別directiveで管理する。[R3–R4]

pack切替はprepare/compileを済ませた候補をframe境界で適用し、旧generationはGPU完了後にretireする。compileやcapability検査の失敗では旧pack、またはnative shaderlessの確定した経路を維持する。

## 8. GPU要件と対応範囲

**shaderless本体のGPU baselineと、各pack/presetの要件を分ける。** Vulkan対応というだけで全packの全機能が使えるとは限らない。[R32–R33]

| 項目 | 方針 |
| --- | --- |
| MRT | 必要な同時attachment数、format、fragment outputをGPU limitsと照合する。Vulkan coreの最低 `maxColorAttachments` は4。8個以上を本体全員の必須条件にしない。 |
| HDR / shadow | color attachment・sampled・filter・depth compare等のusageとformat supportを実照会する。RGB→RGBAのpromotion等は意味が保てる場合に限定し、差を記録する。 |
| Per-buffer blend | `independentBlend` 等を確認する。必要機能を持たないGPUではそのpack/presetを拒否し、native shaderless自体は動く。 |
| Samplers / UBO | stageごとのsampler/descriptor数、UBO size/alignment、vertex attribute数を事前に確認する。driver名やRX6400専用分岐で成立させない。 |
| Geometry / tessellation | 初期互換範囲へ必須化しない。使用packは検出して未対応を明示する。任意のgeometry shaderを同じ意味のvertex shaderへ自動変換できるとは約束しない。 |
| Compute / SSBO / storage image | 後続拡張。ただしrequired featureを持つpackを初期対応済に含めない。computeを使う人気pack/presetの需要次第で優先順位を上げる。 |
| Ray tracing / mesh shader / bindless | 初期目標の外。広いGPU対応を損なうmandatory機能にしない。 |
| OS/vendor | AMD/Intelは既存実機を使う。NVIDIA、Windows、macOS/MoltenVKはテスターでscope付きに受入。macOSはJava/LWJGL/compiler native/windowの全条件を別途確認する。 |

attachmentを分割すれば必ず同じ画像になる、precisionを落とせば必ず同じshaderになる、といった一般fallbackは採用しない。必要な機能を持つpresetを選べる形で対応範囲を公開する。

## 9. 段階と受入

以下はG1–G6がすべて閉じた後の作業。各段階は同じ所有・状態契約に関わる関連変更をまとめ、毎編集ごとのゲーム起動を反復しない。

| 段階 | 成果物 | 次へ進む条件 |
| --- | --- | --- |
| S0: 基準・reuse確定 | fixed shaderless baseline、主要mod manifest、対象pack zip/hash/options、1.12 GL参照、file単位license表、native shader ABI | packのGL参照が正常。再利用するfrontend/依存/Java21条件を確定。範囲が膨らむ場合はここで再見積もり。 |
| S1: CPU frontend | zip/include/profile/properties/dimension解析、program fallback、必要機能・target計画、設定保存 | 条件分岐とコメントdirective、pack error、ID mappingを実pack corpusで確認。GPU objectをまだ所有しない。 |
| S2: 変換・compile・interface | lowered GLSL、SPIR-V、reflection/binding/UBO layout、compilerの配布と診断、compile cache | diagnostic packと初期既存packの対象programがcompileする。stage間interface/descriptor/layoutが一致。compile成功だけで影完成にしない。 |
| S3: native shadowとworld chain | terrain/gbuffer、shadow、color target/flip、prepare/deferred/composite/final、native提示 | まず小sceneで本当の影と水/半透明を描く。AMD/Intelでlayout/sync/画像を確認。GLなしを維持。 |
| S4: 全draw familyと主要mod | generic model/TESR/item/hand/particle/sky等のpass routing、material/ID/PBR、Forge hooks | 地形だけでなく主要modの見える内容がpackを通る。影offでもS0 baselineを維持。 |
| S5: 人気packと時間変化 | BSL/Complementary/Sildur'sの固定版・preset、TAA/bloom/fog/水・PBR、dimension/reload/resize | pack×preset×GPUのcompile/描画/正しさ/未対応を分けた互換表。時系列画像でhistory/jitter等を確認。 |
| S6: 配布・性能 | native/compiler依存、cache上限、GUI診断、license/notice、再現手順、performance report | clean install、pack切替失敗からのnative復帰、資源retire、shaderless退行なしを受入。 |
| S7: 追加機能 | compute/SSBO/custom images、geometry、より多くのpack、DH等の専用adapter | 個別需要・feature・GPU/OS受入を満たしたものだけ対応表へ追加。初期完成と混同しない。 |

先にdiagnostic packでshadow/normal/material/depth/targetを見分けられる表示を作り、その直後に既存packを動かす。診断専用packだけを長く磨く開発にはしない。人気packごとの場当たり修正を増やす前に、共通のuniform/format/flip/attribute契約を直す。

## 10. 検証と性能測定

### 比較基準

- **影off:** G6で固定したCeltium native shaderlessと同じGPU・設定・sceneで比較する。元Celeritas GLとの本体性能比較も維持する。
- **影on:** 同じ1.12.2で実際に動くOptiFine、またはPintonium等のGL loaderを独立profileでpinする。PintoniumはCeleritas forkなので元Celeritasと二重導入しない。参照側の既知不具合は区別する。
- modern Iris/Vitrailの別Minecraft版はcompiler/interfaceの参考であり、1.12.2の画素一致基準にしない。
- pack zip/version/hash、options、resource pack、world/scene/camera、world time、sprite/vertex animation、uniform history、TAA jitter/seed、warm-up frameを揃える。world time固定だけで全animation固定とはしない。

### 必要な検査

| 層 | 確認する内容 |
| --- | --- |
| 安いCPU検査 | include/条件/コメントdirective、ID解決、fallback、flipのread/write trace、std140/reflection、cache invalidation。実装を写しただけのtestを増やさない。 |
| GPUの小scene | shadow方向・cutout・画面外caster、水とガラス、entity/TESR/hand、particle、normal/PBR、depth。attachment dumpと最終画像を比較する。 |
| 時系列 | 水/草の揺れ、TAA、露出、motion blur、rain/wetness、dimension移動。最終1枚だけでhistory正当性を判定しない。 |
| 資源寿命 | pack on/off/切替、設定変更、F3+T、resize/minimize/out-of-date、world switch、compiler/割当/submit/wait失敗。使用中資源を即解放しない。 |
| 公開互換表 | 「読込」「compile」「描画」「画像/機能受入」「必要preset」「GPU」「未対応」を別欄にする。PASSED・VUID0だけを全互換の根拠にしない。 |

CPU/GPU時間、p50/p95/p99、compile時間、warm/cold cache、pack切替pause、VRAM/RAM、shadow draw数・visible chunk・resource/upload量を測る。画像差はpass別・時間軸・region別に原因を説明し、都合のよい閾値で全体一致を宣言しない。

Vulkanであることだけを高速化根拠にしない。影はGPU shader負荷が大きく、CPU側のsubmission改善がFPSへ直結しない場合がある。shaderlessではpack用pass・target・compiler初期化を不要にし、余計なdraw/alloc/readbackを増やさない。

コンパイルはload・設定変更・reloadで行い、毎drawでGLSLを変換しない。source/options/macros、ABI、transformer/compiler版、target environmentをcache keyへ含める。GPU pipeline cacheはdevice/driver/UUID等を別に管理し、失敗を無限retryしない。compiler nativeと大きなcacheを無条件に全OS分jarへ入れない。

## 11. License・依存・Java条件

| 候補 | 調査結果と採用条件 |
| --- | --- |
| 手元のCeleritas/Iris FOSS frontend | upstream説明はIris1.7系・LGPL。file history、header、依存、GL分離を確認して部品ごとに再利用する。 |
| TauMC GLSL Transformation Library | 公開repoの `COPYING.LESSER` はLGPL3。手元のcoordinateは `0.2.0-31.g980d228`。採用時は**実artifactのsource/license/hash**もpinし、latest mainのlicenseだけで旧jarを判定しない。[R26] |
| Vitrail | LGPL3とNOTICEを確認。流用するfileと変更、Iris等からの元の出典を保持する。game固有部分を丸ごとcopyしない。[R6] |
| Aetherium / Iris-Vulkan-Port | rootのLGPL表示だけで判断しない。Aetheriumのbuildには `glsl-transformer:3.0.0-pre3`、forkのLICENSE-DEPENDENCIESにはAGPLの注意がある。現在のLGPL方針へ無条件に埋め込まない。[R11, R14] |
| Actinium / Demonica | rootのGPL/AGPLと部品の元licenseを区別する。独自portの変更を名前だけ変えてLGPL化できるとは扱わない。[R22, R24] |
| Beryl / OptiFine / pack本体 | BerylはARR。OptiFineは公開仕様・参照動作を使い、実装codeをコピーしない。ユーザーpackの再配布とcache/dump公開もそのpackの条件を確認する。 |
| shaderc / reflection / SPIR-V tools | version・native・license・OS/architectureをpinする。LWJGL bindingとnativeの組合せをCleanroomの版へ合わせる。Java21側へJava25依存のclass/APIを混ぜない。[R27–R28, R34] |

新しいIrisのtransformerはAGPLと明記されており、手元のTauMC libraryとは別物。[R38] 本体のlicenseを変える案や作者からの別許諾は、必要になった時に独立の意思決定として扱う。この計画だけで採用済にはしない。

## 12. 工数感と見直し点

前提はG1–G6完了済み、既存native resource/frame/draw契約を再利用できること、CPU frontendの一部再利用が可能なこと。以下は**作業量の仮置き（人時）**であり、DSHの経過時間や納期の予測ではない。本体・主要modの残工数は含まない。

| 段階 | 仮の工数 |
| --- | ---: |
| S0: 基準とreuse確認 | 8–16 h |
| S1: frontendの分離・legacy対応 | 16–32 h |
| S2: Vulkan lowering、compile、binding、配布 | 32–64 h |
| S3: shadow・gbuffer・target・合成 | 40–80 h |
| S4: generic draw / 主要mod / material統合 | 24–48 h |
| S5: 人気pack・時系列・複数GPU受入 | 40–80 h |
| S6: 配布と性能整理 | 12–24 h |
| **初期互換範囲の合計** | **172–344 h** |
| S7のcompute等・追加OS/vendor/pack | 別見積り。初期合計に含めない。 |

早い「1packで影が見える」到達点と、主要draw family・TAA・複数GPU・reloadまで正しい状態は別の節目。地形移植が短時間で進んだ実績から、この互換工数を単純に比例推測しない。

S0で再利用範囲と本体のnative APIを確定した時、S2で実packの変換失敗を分類した時、S3で最初の既存packが描けた時に見積りを更新する。GLSL変換、material attribute、時間履歴、mod custom rendererのどれが支配的かを実測で判断する。

## 13. 着手時に残る判断

- 主要mod manifest、対象packの正確なzip/hash/preset、1.12.2 GL参照loaderを確定する。
- shaderless native APIの完成形を読み直し、target/state/texture/frameのownerを増やさない接続を決める。
- TauMC parserを使う独立loweringとVitrailの部品reuseをcorpusで比較し、一つの方式を選ぶ。
- runtime compiler binding/nativeのCleanroom提供状況と、配布先OS/architectureを確認する。
- 初期packにcompute/geometry等が必須なら、対応presetを限定するかS7の一部を前倒しするかを決める。黙って機能を捨てない。

本体完了前にこれらを実装の理由にして優先順位を変えない。今はこの文書を将来計画として保持する。

## 14. 一次資料と固定snapshot

固定SHAは2026-10-10の公開repo取得結果。配布版とrepo HEADは一致するとは限らない。ソース確認・作者の報告の範囲であり、外部modのこちらでの実機受入ではない。

| プロジェクト | 調査したSHA |
| --- | --- |
| Vitrail | `f42e5489c6abff655781b40bebc70338bada3039` |
| Aetherium | `3fce170d52929ee34e4ad91138d26ecf58237394` |
| Iris-Vulkan-Port | `47babb4aac66b16084fb419e8d85c2e14e70d41e` |
| VulkanMod | `087ef24e58e5522dec5ccb44f279768786bfb6da` |
| Pintonium | `cd2953613d6166f58a1be905523b64cc46f64c20` |
| Actinium（Q-Engineering-Source） | `1b5cfc84258081ff562a50612e0d9a0383ee539a` |
| Demonica | `a83feba9cb15fe1d54404bcc740cd812d699d023` |
| Spectra | `2565c0a3fc7f0fa1029bc51c37487df5a8abcb9c` |

- **R1** [Vitrail README](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/README.md)
- **R2** [Vitrail GlslTranslator](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/common/src/main/java/dev/vitrail/glsl/GlslTranslator.java) / [ProgramTranslator](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/common/src/main/java/dev/vitrail/glsl/ProgramTranslator.java)
- **R3** [Vitrail frame](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/docs/frame.md)
- **R4** [Vitrail render targets](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/docs/internals/render-targets.md)
- **R5** [Vitrail compatibility](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/docs/compatibility.md)
- **R6** [Vitrail NOTICE](https://github.com/avpbynf/Vitrail-Shaders/blob/f42e5489c6abff655781b40bebc70338bada3039/NOTICE)
- **R7** [Aetherium README](https://github.com/SpaceBod/aetherium/blob/3fce170d52929ee34e4ad91138d26ecf58237394/README.md)
- **R8** [Aetherium tested packs](https://github.com/SpaceBod/aetherium/blob/3fce170d52929ee34e4ad91138d26ecf58237394/docs/tested-packs.md)
- **R9** [Aetherium VulkanLowering](https://github.com/SpaceBod/aetherium/blob/3fce170d52929ee34e4ad91138d26ecf58237394/common/src/client/java/dev/spacebod/aetherium/shaders/pipeline/transform/lowering/VulkanLowering.java)
- **R10** [Aetherium ShaderCompiler](https://github.com/SpaceBod/aetherium/blob/3fce170d52929ee34e4ad91138d26ecf58237394/common/src/client/java/dev/spacebod/aetherium/client/gpu/ShaderCompiler.java)
- **R11** [Aetherium dependencies / Java target](https://github.com/SpaceBod/aetherium/blob/3fce170d52929ee34e4ad91138d26ecf58237394/build-logic/src/main/groovy/spacebod.base.gradle)
- **R12** [Iris-Vulkan-Port README](https://github.com/aaaapai/Iris-Vulkan-Port/blob/47babb4aac66b16084fb419e8d85c2e14e70d41e/README.md)
- **R13** [IrisSPIRVCompiler](https://github.com/aaaapai/Iris-Vulkan-Port/blob/47babb4aac66b16084fb419e8d85c2e14e70d41e/src/main/java/net/irisshaders/iris/vulkan/shader/IrisSPIRVCompiler.java)
- **R14** [Iris-Vulkan-Port dependency licenses](https://github.com/aaaapai/Iris-Vulkan-Port/blob/47babb4aac66b16084fb419e8d85c2e14e70d41e/LICENSE-DEPENDENCIES)
- **R15** [Beryl official distribution](https://modrinth.com/mod/beryl)
- **R16** [Sulkan official distribution](https://modrinth.com/mod/sulkan)
- **R17** [Vulkanite upstream](https://github.com/MCRcortex/vulkanite)
- **R18** [Aperture migration / outdated notice](https://shaders.properties/aperture/migration/)
- **R19** [Pintonium official distribution](https://www.curseforge.com/minecraft/mc-mods/pintonium)
- **R20** [Pintonium Forge122 shader integration](https://github.com/Xplodin/Pintonium/blob/cd2953613d6166f58a1be905523b64cc46f64c20/forge122/build.gradle.kts)
- **R21** [Actinium README](https://github.com/Q-Engineering-Source/Actinium/blob/1b5cfc84258081ff562a50612e0d9a0383ee539a/README.md)
- **R22** [Actinium third-party notices](https://github.com/Q-Engineering-Source/Actinium/blob/1b5cfc84258081ff562a50612e0d9a0383ee539a/THIRD_PARTY_NOTICES.md)
- **R23** [Demonica README](https://github.com/ndellagrotte/Demonica/blob/a83feba9cb15fe1d54404bcc740cd812d699d023/README.MD)
- **R24** [Demonica third-party notices](https://github.com/ndellagrotte/Demonica/blob/a83feba9cb15fe1d54404bcc740cd812d699d023/THIRD_PARTY_NOTICES.md)
- **R25** [Spectra README](https://github.com/kristitrnka/Spectra/blob/2565c0a3fc7f0fa1029bc51c37487df5a8abcb9c/README.md)
- **R26** [TauMC GLSL library](https://github.com/TauMC/glsl-transformation-lib) / [COPYING.LESSER](https://github.com/TauMC/glsl-transformation-lib/blob/main/COPYING.LESSER)
- **R27** [shaderc upstream](https://github.com/google/shaderc)
- **R28** [LWJGL shaderc bindings](https://javadoc.lwjgl.org/org/lwjgl/util/shaderc/Shaderc.html)
- **R29** [OptiFine public shader specification](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt) / [properties](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.properties)
- **R30** [Iris mc_Entity](https://shaders.properties/current/reference/attributes/mc_entity/) / [block.properties](https://shaders.properties/current/reference/miscellaneous/block_properties/)
- **R31** [Iris mc_midTexCoord](https://shaders.properties/current/reference/attributes/mc_midtexcoord/)
- **R32** [Vulkan features](https://docs.vulkan.org/spec/latest/chapters/features.html)
- **R33** [Vulkan limits](https://docs.vulkan.org/spec/latest/chapters/limits.html)
- **R34** [SPIRV-Reflect](https://github.com/KhronosGroup/SPIRV-Reflect) / [SPIRV-Tools](https://github.com/KhronosGroup/SPIRV-Tools)
- **R35** [BSL official distribution / supported versions](https://modrinth.com/shader/bsl-shaders)
- **R36** [Complementary official site](https://www.complementary.dev/shaders/)
- **R37** [Sildur's official installation](https://sildurs-shaders.github.io/install/index.html)
- **R38** [Iris glsl-transformer license](https://github.com/IrisShaders/glsl-transformer)

