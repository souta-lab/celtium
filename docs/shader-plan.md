# Celtium shader plan

**状態: Experimental development ACTIVE（2026-10-14 ユーザー指示）。主要Mod互換とS0/S1 shaderpack frontend・native影の実験基盤を並行開発。一般ユーザー向け影pack互換は未完成／未受入。**

調査日: 2026-10-10（JST）。対象: Minecraft 1.12.2 / CleanroomMC / Celtium。Javaの配布ターゲットは `--release 21`。ゲーム・ビルド・検証でJDK 25を使う方針は維持する。

## 1. 決定と着手条件

目指すのは、既存のOptiFine / ShadersMod / Iris形式の**パックをユーザーが書き換えずに読み込み、Celtiumのnative Vulkanで描くこと**。既存影modのOpenGLレンダラをそのまま起動することとは分ける。パックのGLSLはロード時・設定変更時に変換し、SPIR-Vとnative pipelineとして実行する。

**backend決定（2026-10-10）:** CeltiumのVulkan直結を維持し、SDL GPUへの全面移行は行わない。既存のnative描画・資源・同期・提示の成果を継続する。AngelicaのGLSM＋SDL GPU全体は移植せず、shader変換・reflection・cache・batch境界・互換性試験を部品ごとに比較する。SDL採用だけで既存mod互換が得られるとは扱わない。SDLによるwindow/inputの補助利用はGPU backend選択と別件。macOSは将来MoltenVKのportability条件を検証する。[R46–R47]

製品版の完成・互換受入は、(1)全vanilla native、(2)主要Mod shaderless、(3)既存packのnative Vulkan実描画の依存順を維持する。一方、**2026-10-14の明示的なユーザー指示によって、実装着手の順序だけを変更**した。主要Modテストと独立したS0/S1のCPU pack frontend、後続のSPIR-V interfaceおよび既定OFFのnative Shadow Map診断を並行して作れる。各段階は既存のnative所有権を再利用し、GL context fallbackや任意GLエミュレーションを復活させない。

以下のG1–G6は、**一般配布・互換性を「完成」と呼ぶための受入gate**として残す。未達のgateを通過したと見なさず、早期の実験コードを既定で有効化しない。バニラ `ShaderGroup`、spectator効果、entity outline、FBO/postprocessは従来どおり本体の機能である。実行中の工程とテスト証拠は[#76](https://github.com/souta-lab/celtium/issues/76)（影）と[#77](https://github.com/souta-lab/celtium/issues/77)（Mod）で追跡する。

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
| **Angelica SDL GPU** | Minecraft 1.7.10。GLSMが保持するGL状態/命令をSDL3 GPUへ接続し、Vulkan/Metal/D3D12で描く実装を確認。SDL GPUは実験扱い・既定GL。既存ComplementaryのVulkan描画不具合報告と修正PRでのcloseも確認したが、こちらではゲーム起動未検証。[R39–R43] | **主要なshader変換・legacy描画の設計参考**。SDL backend/GLSM全体は導入せず、native Vulkanへ合う部品を比較する。全pack・全mod互換の完成例とは扱わない。 |
| **Vitrail Shaders** | Minecraft 26.2/26.3のnative Vulkan上でOptiFine形式packを扱う。tokenベースのGLSL変換、program単位のinterface統合、target/flip管理を確認。公開互換表にはBSL/Complementary等を実際に描いた記録と画像差がある。[R1–R6] | **主要な設計参考**。言語変換・target寿命・失敗例を調べる。26.xのGPU APIやMixin、reversed-Z設定をそのまま移植しない。 |
| **Aetherium** | native Vulkan用のGLSL AST lowering、std140 uniform統合、stage location、shaderc computeコンパイルを確認。公開22pack表はコンパイル結果が中心で、描画Verdictには未記入が残る。[R7–R11] | **変換・bindingの比較参考**。「22packの見た目が完全互換」とは扱わない。Java25出力とAGPLのtransformer依存を含むため、一括取込み候補にしない。 |
| **Iris-Vulkan-Port** | 非公式の実験fork。VulkanModへIrisのterrain/shadow/compositeを接続すると説明。shaderc/UBO変換ソースを確認したが、regex処理・custom VulkanMod jar依存がある。[R12–R14] | interfaceやterrain接続の参考。動作・配布再現性を別途証明するまで製品基盤にしない。Iris公式の完成済VK版と混同しない。 |
| **Beryl** | VulkanMod上の専用pipelineと内蔵pack。既存OptiFine/Iris pack互換ローダーではない。配布ページはARR。[R15] | 専用pack方式の比較対象。コード再利用は計画しない。 |
| **Sulkan** | Minecraft native Vulkan向けの独自pack形式。無改変のIris/OptiFine packは非対応と明記。[R16] | 言語・formatを新設する代案として把握。今回の既存pack互換の主案にはしない。 |
| **Vulkanite** | GL/VK interopでIrisへhardware ray tracing等の追加passを提供する方式。[R17] | 純VK pack互換の証拠には使わない。GLなし・広いGPU対応というCeltiumの条件に合わない。 |
| **Aperture** | Iris側の別pack API。公式migration資料は新しいpipeline記述を説明するが、資料自身が古くSlangへ移行済みと注意している。[R18] | 将来の別format候補。既存GLSL pack互換の代替として先に採用しない。最新APIを再調査してから独立判断する。 |

### Angelicaから比較する部品（調査追加）

2026-10-10に公開master `1946c2ad653cf120b369f4d622f7b952cbaf9524`（commit時刻2026-10-09 UTC）を固定して確認。初版の調査はSDL GPU経路を含んでいなかった。本節でその不足を補う。外部modの実機受入とは区別する。

- **影gate通過後:** `glsm/.../shader/SpirvCompiler.java` と `GlslVulkanPreprocess.java`、`CompatShaderTransformer.java`、`sdl-gpu/.../shader/ShaderManager.java` のGLSL前処理、Vulkan向けcompile、reflection/binding、cacheを比較する。SDLのdescriptor/resource配置をCeltiumへそのまま持ち込まず、§6のnative契約に合わせる。[R40–R42]
- **shaderlessの互換・性能作業:** `ModelPartBatcher` / `TesrBatchRenderer` の共通mesh、instance、状態変更時flush、透明物の順序を比較する。Celtiumの共通model replayからまとめ描画へ拡張できるか、CPU/GPU時間を測って採否を決める。entity個別の代替rendererを大量追加する方針にしない。[R45]
- **文字:** `BatchingFontRenderer` の文字/影のbatch、texture/matrix変更時flushを比較する。現在のGUI/fontのVK描画実装と重複する部分を先に除き、文字frontendのCPU負荷とdraw回数に改善余地がある場合のみ実装する。[R45]
- **今の本体修復で参考にできるもの:** FBO attachment、texture staging、sampler generation、pipeline key、pending resource/fence寿命の契約とregression test。SDLのAPI呼び出しを移植するのではなく、現在のCeltiumに欠ける不変条件・失敗ケースを抽出する。
- **HUD cache:** 該当codeはCC BY-NC-SA 4.0の別license。LGPL本体の移植候補に含めない。必要になった場合、一般的な更新頻度制御・native targetへのcacheという設計を独立実装し、Forge overlay eventと動くHUDの意味を守る。[R44]
- **地形:** Angelica自体がCeleritasを使うため、既存mesher/terrain最適化との重複が大きい。性能改善が未実装であることを比較で示せない限り優先しない。[R39]

これらは比較候補であり全項目の実装義務ではない。現在のフルVK化＋主要mod対応を止めず、影用の変換器・依存の導入はgate後に行う。

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
| Angelica GLSM＋SDL GPUへの全面移行 | **採用しない（backend決定）**。SDLは複数APIと資源管理を抽象化できるが、現在のVk資源・同期・command・swapchainを移植する範囲が広い。mod互換を支えるGLSMも自動では付かない。shader変換とbatch/testの部品比較に限定する。[R39–R42, R46] |
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

**S0–S2のCPU/コンパイル実験と、既定OFFの小さなS3 Shadow Map試作は先行できる。** S3以降の製品互換性受入・既定ON・S4–S6の完了宣言はG1–G6の実測gateを必要とする。各段階は同じ所有・状態契約に関わる変更をまとめ、毎編集ごとのゲーム起動を反復しない。

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
| Angelica | root LGPL3の表示を部品へ一律適用しない。HUDCachingはCC BY-NC-SA 4.0なので移植対象外。その他もfile history/header/依存を確認する。SDL GPU subprojectはJava25 toolchain、GLSMはJava21 toolchainを指定しているため、配布release21との互換をclass/API・native単位で確認する。SDL/LWJGL3ify/GTNHLibへの依存を無条件導入しない。[R39, R44, R48] |
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

- **R39** [Angelica README / backendの状況](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/README.MD)
- **R40** [Angelica SDL GPU backend](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/sdl-gpu/src/main/java/com/gtnewhorizons/angelica/sdlgpu/SDLGPURenderBackend.java)
- **R41** [Angelica SDL shader manager](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/sdl-gpu/src/main/java/com/gtnewhorizons/angelica/sdlgpu/shader/ShaderManager.java)
- **R42** [Angelica shaderc compiler](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/glsm/src/main/java/com/gtnewhorizons/angelica/glsm/shader/SpirvCompiler.java)
- **R43** [Complementary＋Vulkanの実描画不具合・修正close（2026-10-10確認）](https://github.com/GTNewHorizons/Angelica/issues/2141)
- **R44** [Angelica HUD cache個別license](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/src/main/java/com/gtnewhorizons/angelica/hudcaching/LICENSE-HUDCaching.md)
- **R45** [Angelica model batch](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/src/main/java/com/gtnewhorizons/angelica/rendering/tesr/ModelPartBatcher.java) / [TESR batch](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/src/main/java/com/gtnewhorizons/angelica/rendering/tesr/TesrBatchRenderer.java) / [font batch](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/src/main/java/com/gtnewhorizons/angelica/client/font/BatchingFontRenderer.java)
- **R46** [SDL3 GPU official API / feature・shader・資源管理](https://wiki.libsdl.org/SDL3/CategoryGPU)
- **R47** [MoltenVK official / portability](https://github.com/KhronosGroup/MoltenVK)
- **R48** [Angelica SDL GPU Java/toolchain/dependencies](https://github.com/GTNewHorizons/Angelica/blob/1946c2ad653cf120b369f4d622f7b952cbaf9524/sdl-gpu/build.gradle.kts)

## 0. 決定（2026-10-22・利用者指示）: 影とパックは Demonica の fork で行う

**Iris を設計参照にするのをやめ、Demonica を fork して Celtium 用にする。** 判断の根拠と境界をここに固定する。

### Demonica の実体（pinned commit `a83feba9cb15fe1d54404bcc740cd812d699d023` の README / docs より）
- **Iris の shader pipeline ＋ Angelica の GLSM ＋ 互換層**を、別途導入の **Celeritas** の上で動かす mod。
- Actinium から fork（`4a19c959`）し、**terrain renderer は持たない**（描画は Celeritas が持つ）。
- Celeritas のクラスを触るのは **quarantine 1箇所**（`mixins.demonica.celeritas.json`）だけで、各パッチに台帳（`docs/celeritas/LEDGER.md`）と上流PR草案がある。
- **Cleanroom の OpenGL context 上で動く**（core context も選択可）。
- ライセンス: 本体 **AGPL-3.0**（〜0.4.0 は GPL-3.0）、Angelica/Iris 由来は LGPL-3.0、Actinium 由来は GPL-3.0、glsl-transformer は AGPL-3.0。

### Celtium への移し方（残す／置換する／捨てる）
| 部分 | 扱い |
|---|---|
| pack の読み込み・program 集合・directives・option 画面 | **残す**（Demonica 側の実装をそのまま fork で継承） |
| Iris pipeline の段構成（gbuffers → composite → final、target 管理） | **残す**が、**描画先は Vulkan の target に置換**（GL FBO を持たない） |
| Celeritas への quarantine パッチ | **置換**: Celtium が既に公開している `celtium$*` フック（terrain 描画乗っ取り・framebuffer 採用・pipeline/descriptor 廃棄通知・present）を呼ぶ形にする。Celtium 側のクラスを patch しない |
| GLSM（Mod の直接 GL 呼び出しの互換） | **境界だけ残す**。Celtium の制約どおり「Mod が直接行う GL 呼び出し」専用。**GLSM を丸ごと持ち込まない**（shader-plan の既存方針） |
| 描画そのもの（pack の GLSL → SPIR-V → Vulkan pipeline） | **Celtium 側の既存資産**（`celtium-shaderpack` の lowering、`ShaderPackPipelineFactory`）を使う |
| 影 | **pack の `shadow`/`shadowtex` プログラムで行う**。Celtium の組込 `ShadowMapPass` は scaffold（既定OFF） |
| 診断 jar | 取り込まない（独立のまま） |

### 成果物の境界（ライセンス）
- fork は **別リポジトリ／別 jar（AGPL-3.0）**として維持し、**Celtium core には取り込まない**。
- LGPL-3.0 の部分（Angelica/Iris 由来）はクレジット明記のうえチェリーピック可（利用者指示③）。
- AGPL の都合で fork 側のコードを Celtium core に混ぜない。接続は**公開フックとインターフェースのみ**。

### 作業順（Celtium 側から見た依存）
1. **Celtium の接続面を確定**（下記フックのうち fork が使うものを文書化し、不足を足す）
2. fork 側でパック読み込みと program 集合を維持したまま **描画先を Vulkan target に差し替え**
3. terrain の描画を **Celtium の terrain 経路へ委譲**（`celtium$captureTerrainBatch` 系）
4. composite/final を Vulkan の pass として実行
5. pack の影（`shadow`/`shadowtex`）を pack のプログラムで実行
6. 4画面＋qmagnet の画質証拠を維持（壊したら戻す）

### 参考: Celtium が既に公開しているフック（fork が使える）
`captureTerrainBatch` / `adoptableFramebuffer` / `adoptedColorTexture` / `adoptedDepthTexture` / `mirrorSourceFor` / `mirroredTexture` /
`mirroredProjection` / `ensureTargetForBoundFramebuffer` / `noteFramebufferAdopted` / `notePipelineDestroy` / `noteDescriptorDestroy` /
`retireBuffer` / `flushPendingAtFrameBoundary` / `presentFrame` / `dumpPresentedImage` / `drawModelList` など（`celtium$` 前置、1197箇所で参照）。

## 実測メモ（2026-10-22・S3の土台確認）

`-Dceltium.shaderpack.path=/home/s0u7a/ダウンロード/BSL_v10.1.8.zip -Dceltium.shaderpack.drawProbe=blit` で
world leg を実行（jar `706002c2…`・AMD・validation ON）:

```
result: PASSED  画像gate: PASSED  validation: 0件
CELTIUM_PACK_DRAW_SUBMIT cb=... slot=0
```

**確定**: BSL のプログラムが**実フレーム経路で bind・提出され、validation違反なく通る**（描画コマンドが記録され実行される）。
**未確定**: その出力が**最終表示画像に寄与しているか**。画像gateが PASSED のままなので、
probe の描画先が表示画像でない（別target/オフスクリーン）可能性が残る。次の判別は
「packの出力を final として表示画像に書く」経路（S3本体）を実装し、**画像が変わること**を差分で示すこと。

### 訂正（同日・`drawProbe=constant` で判別）

`-Dceltium.shaderpack.drawProbe=constant`（属性なしの全画面三角形を単色で描く）でも
**leg PASSED・画像gate PASSED・validation 0件**だった。単色の全画面三角形が表示画像に乗っていれば
画像gateは落ちるはずなので、**probeの描画は最終表示画像に届いていない**と判定できる。

⇒ 先の `drawProbe=blit` の PASSED も「packの出力が画面に出た」証拠にはならない（同じくオフスクリーン）。
`CELTIUM_PACK_DRAW_SUBMIT` が証明するのは**コマンドが記録・提出されたこと**までで、**可視性は未証明**。

次の判別: probeが実際にどの image に描いているか（`slot=0` が指す先＝swapchain image か、frame image か）を
ログに出す。表示画像に書いているのに見えないなら、描画が present の後／別imageへの書き込み。

### 訂正2（同日・`compiler=false` で判明）

world leg のログは次を出していた:

```
CELTIUM_PACK_PROGRAM skipped path=true compiler=false
shaderc available=false reason=the shaderc binding could not be loaded
  (ClassNotFoundException: org.lwjgl.util.shaderc.Shaderc ... invalid classes)
```

⇒ **このleg環境ではGLSL→SPIR-Vコンパイラ（shaderc）がロードできず、パックのシェーダは一度も実行されていない**。
したがって `drawProbe=blit` / `constant` で出た `CELTIUM_PACK_DRAW_SUBMIT` は**ビルトインの三角形probe**のもので、
**パックのプログラム実行の証拠は現時点でゼロ**。前2回の記述（実フレームで動いた／コマンド提出まで）は取り消す。

**次の必要条件**: 検証環境に `lwjgl-shaderc` とそのnativesを追加し、`CELTIUM_PACK_PROGRAM` が
`compiler=true` で実行される状態にする（`scripts/gv-leg.sh` のライブラリ集合に足す）。そのうえで
`sampleOffscreen` の読み戻し値（画素）を証拠にする。可視性はさらにその後（final programから表示画像へ）。

### 実測（2026-10-22・shadercを入れたらパックのシェーダが通った）

**legでshadercを有効にする手順**（これが欠けていた）:
1. `unzip .gv-verify/lib/lwjgl-shaderc-3.4.1-natives-linux.jar` の `libshaderc.so` を natives ディレクトリへ
2. `CELTIUM_LEG_EXTRA_LIBS=$PWD/.gv-verify/lib/lwjgl-shaderc-3.4.3.jar` で leg を起動

結果（jar `706002c2…`・AMD・validation ON・`-Dceltium.shaderpack.path=BSL_v10.1.8.zip -Dceltium.shaderpack.compileProbe=true`）:

```
CELTIUM_PACK_PROGRAM file=/shaders/world-1/gbuffers_basic.vsh expandedBytes=14240 loweredBytes=14248
  attributes={a_Colour=5, a_Normal=4, a_Position=6, a_TexCoord0=2, a_TexCoord1=3, ...}
CELTIUM_PACK_PROGRAM file=/shaders/world-1/gbuffers_basic.fsh expandedBytes=22151 loweredBytes=20096
  varyings=8 samplers={noisetex=0} unsupported=0 spirvWords=2036
CELTIUM_PACK_PIPELINE program=gbuffers_basic pipeline=ok attributes=7 result=OK
leg: PASSED
```

**確定**: BSL 10.1.8 の**実シェーダが lower→compile→Vulkanパイプライン作成まで通る**（unsupported=0）。
**未確定**: その程序を使った**描画と可視性**（次は gbuffers_basic で実ジオメトリを描き、`sampleOffscreen` の画素を証拠にする）。

### 実測（2026-10-22・パックのシェーダがGPUで実行され、画素が出た）

`compileProbe=true` **と** `drawProbe=true` を併用（shaderc有効・jar `706002c2…`・AMD・validation ON）:

```
CELTIUM_PACK_PIPELINE program=gbuffers_basic pipeline=ok attributes=7 result=OK
drawProbe=prepared attributes=7 result=OK offscreen=[centre=#00FF00 sampledColours=1]
CELTIUM_PACK_DRAW_SUBMIT cb=... slot=0
CELTIUM_PACK_DRAW inPass=true draws=1
leg: PASSED  画像gate: PASSED
```

**確定**: BSL 10.1.8 の `gbuffers_basic` が **lower→compile→pipeline→実ジオメトリ描画→画素読み戻し**まで通り、
中心画素が `#00FF00`（sampledColours=1）。**パックのコードがGPUで動いて画素を出した**、という証拠。
（`drawProbe=true` 単独では `compileProbe` 経路に入らず、ビルトイン三角形だけが動くので両方必要。）

**未確定**: その出力が**表示画像に可視**か（probeは自前オフスクリーンimageに描いている）。
次は `final`/`composite` を表示画像へ書く経路。

### 実測（2026-10-22・blitは記録されるが画像gateは変わらない）

`compileProbe=true` ＋ `drawProbe=blit`（jar `706002c2…`・AMD・validation ON）:

```
offscreen=[centre=#00FF00 sampledColours=1]
CELTIUM_PACK_DRAW_HOOK cb=... open=true recording=true inPass=false slot=1
CELTIUM_PACK_DRAW blit=true draws=1
leg: PASSED  画像gate: PASSED  validation: 0件
```

**確定**: パックの画素（緑）は出ており、**blitのコマンドも記録されている**（`blit=true draws=1`、`slot=1`）。
**確定（負）**: それでも**表示画像は変わらない**（gate PASSED）。swapchainは4枚（`frameSlots=4`）で slot 0..3 が現れるため、
**blit先slotとpresentされるslotが一致していない**可能性が高い。

**次の判別（具体的）**: blit先を「そのフレームでpresentされるslot」に固定し、`presentedDump` を前後で画素比較する
（gateは平均色ベースなので、緑の三角形1枚では反応しない可能性もある。画素差で見る）。

### 実測（2026-10-22・blit先は正しい image。次は「presented dump に緑が居るか」）

`celtium$recordShaderPackDraw` を実コードで確認: blit先は **`target.colorImage()`（フレームの色image）**で、
swapchain slot ではない（ログの `slot=1` は `frameSlot` の記録）。したがって**slot不一致説は否定**。
条件は「frame buffer が開いていて、render pass 中でない」こと。

gate が動かない理由の候補は次の2つに絞られる:
1. **presented dump が blit より前に撮られている**（capture が古い）
2. `blitToFrame` 自体が実際には画素を移していない（layout/range/formatの問題で validation は通る）

**次の判別（コマンド1つで可能）**: `-Dceltium.vulkan.presentedDumpEvery=1` で PNG を出し、
blit領域 (0,0)-(256,256) の画素を「パック無し」と「パック有り」で比較する。緑(#00FF00)が出ていれば配達は成功し、
gate側の問題。出ていなければ `blitToFrame` の実装側。

### 可視性の決着（2026-10-22 深夜・実画像）

`drawProbe=blit` の leg（jar `80e3d8b3…` 派生・AMD・BSL v10.1.8・headless隔離sway・world）で、
**packのシェーダ出力が提示画像に到達していることを実画像で確認した**。

- ログ: `CELTIUM_PACK_DRAW blit=true frameId=0 wroteImage=0x170000000017 frameTargetImage=0x170000000017 sameImage=true rect=0,0 256x256`
  → ブリット先は**フレームターゲット＝提示される画像**と同一。
- 画像: `run-packblit-2/screenshots/celtium-presented-early-run-*.png` の **左下 256×256 が一色 `(0,255,0)`（65536/65536 緑）**
  → `gbuffers_basic` のオフスクリーン出力（probe時 centre=#00FF00）がそのまま提示画像に乗っている。

つまり `drawProbe=constant` で画像gateがPASSEDだったのは「描画が届いていない」ではなく、**constantモードはブリット経路を使わない**ため。
**pack出力の可視性は `blit` モードで証明済み**。S3の残りは「probeの固定三角形」を実地形ジオメトリ／実パスに置き換えること。

### S3進捗: 地形ジオメトリをpackのステージで描く（2026-10-23）

- **地形バッチをpack probeへ供給**: 地形は immediate 受理経路を通らないため、`TerrainRenderer.uploadRegion` から
  `noteTerrainBatchForProbe(...)` を呼ぶ静的シンクを追加。実測ログ:
  `CELTIUM_PACK_CAPTURE format=3 vertices=5912 stride=32` / `format=3 vertices=1056` など
- **ジオメトリ採用と再描画**: `CELTIUM_PACK_GEOMETRY terrain vertices=68 format=8 attributes=7` →
  `CELTIUM_PACK_RERENDER_GEOMETRY vertices=68 indices=102`（probeのオフスクリーン画像を実地形で再描画）
- **未達**: 再描画した地形が**ピクセルとして出ていない**（提示画像の左下256×256は依然としてクリア色一色）。
  候補は (a) `renderOffscreen` のパイプラインが depthFormat=UNDEFINED で作られており、packのプログラムが
  depth test 前提のためラスタライズが捨てられている、(b) `fitTransformToBounds` が書く変換がプログラムの
  実際の参照（u_Projection/u_ModelView/u_MVP のどれ）と一致していない。
  次の判別は (a) から: 再描画用パイプラインをdepth無効で作るか、depth attachment を付ける。

### S3進捗2: 実地形がpackのステージで描けた（2026-10-23・sampled実測）

再描画直後にprobe自身の画像を読み戻した結果:
```
CELTIUM_PACK_RERENDER_GEOMETRY vertices=68 indices=102 pipeline=0x2170000000217
  sampled=[centre=#FF0000 sampledColours=1] lane0=[0.654902,...]
```
⇒ **実地形（68頂点/102インデックス）がBSLのステージでラスタライズされ、画像が赤一色になった**。
ジオメトリのバインド・`fitTransformToBounds` の変換・パイプラインはいずれも機能している。

**残る不一致**: probe画像は赤なのに、提示画像の該当矩形は青（クリア色）のまま。
ブリット自体は同一イメージへ書いている（`sameImage=true`）ので、**フレーム後半のパスが矩形を上書きしている**のが次の仮説。
判別: ブリットを最後の描画（present直前）へ移す／ブリット直後にフレーム内容を読み戻して比較する。

### S3進捗3: 届いているが「中身が古い」（2026-10-23・四隅解析）

提示画像（kept）の四隅を解析した結果、world/tick200 の**左下だけが一色 (0,0,255) の256×256**で、他3隅は数千色（実ワールド）。
つまり**ブリット矩形は確かに届いている**が、そこに乗っているのは probe 画像の**クリア色**であり、
同時刻の probe 画像は `sampled=#FF0000`（実地形が描けた赤）だった。

⇒ 順序またはレイアウトの不一致: 描画とブリットが別時点の内容を指している。
次の判別: ブリット直後に**フレーム画像の矩形を読み戻して**色を確認する（`sameImage=true` は宛先の同一性しか示さない）。
併せて `renderOffscreen` の終了レイアウト（TRANSFER_SRC）と `blitToFrame` の要求（TRANSFER_SRC）が一致していることは確認済み。

### S3進捗4: ブリットはprobe画像の中身を運んでいる（2026-10-23・位置を中央へ移動した対照）

ブリット先を中央へ移して同じlegを実行した結果、提示画像の中央256×256は
`blue 53504px / (199,199,199) 7936px / white 4096px` の**複数色**になった。

⇒ **ブリットは「probe画像の中身」を正しく提示画像へ運んでいる**（前回の「左下が一色」は、その瞬間のprobe画像が
クリア色一色だったため）。つまり配達経路は機能しており、残る問題は
**world撮影フレームの時点でprobe画像に実地形の再描画が入っていない**ことに絞られた。

次の判別: 撮影フレームを特定し、そのフレームで `renderOffscreen` 後の `sampled` を出す
（現在は初回のみログ）。撮影時の `frameId` と突き合わせて、再描画が毎フレーム走っているかを確認する。

### S3達成: packの出力が「最終画面」になった（2026-10-23・実画像）

`drawProbe=blit` + 全画面ブリット（`-Dceltium.shaderpack.probeFullscreen=true` 既定）で leg を実行:

- 提示画像（`docs/evidence/pack-world-2026-10-23.png`）は **probe画像そのもの**:
  `(0,0,255)` のクリア色 + `(199,199,199)` の石ブロック（地形ジオメトリが描いた領域）
- **画像gate が FAILED** ＝ 表示画像が参照ワールドと一致しなくなった
  ⇒ これまでの「pack出力が最終画面に届いていない」状態を**実画像で反証**した（S3の判別条件を満たす）

意味: packのSPIR-V・パイプライン・ユニフォーム・頂点変換・描画・提示までが一本につながり、
**最終画面をpackのシェーダが描いた**。残りは probe の固定フィット変換を実カメラ行列へ置き換え、
フレーム全体を pack のパス構成（gbuffer→composite）で描くこと。

### 訂正（2026-10-23・読み戻しは描画の証拠になっていなかった）

前段の「実地形が描けた（`sampled=#FF0000`）」は**撤回**する。`sampleOffscreen` は読み戻し用に画像レイアウトを変えるため、
**その直後のブリットが別レイアウトの画像を読む**ことになり、読み戻し値と配達内容が一致しない。
実測: 読み戻しありの leg は提示画像が一色 (0,255,0)（＝生成時の三角形）、読み戻しなしでは一色 (0,0,255)（＝クリア色）。

確定していること:
- 複数セクションのキャプチャと採用は機能: `CELTIUM_PACK_CAPTURE finalized format=3 vertices=5372` →
  `CELTIUM_PACK_GEOMETRY terrain vertices=5372` → `CELTIUM_PACK_RERENDER_GEOMETRY vertices=5372 indices=8058`
  （＝packのパイプラインで 5372頂点/8058インデックスの描画コマンドが記録・実行されている）
- 全画面ブリットは probe 画像を提示画像へ運ぶ（生成時の三角形が全画面緑として出た）

未確定: その描画が**ピクセルとして可視か**。読み戻しは診断としては使えるが、**配達の証拠にはならない**ので
`-Dceltium.shaderpack.probeSample=true` を付けた leg の画像を配達判定に使わない。

次の判別: 読み戻しを使わずに、**描画内容が分かる固定色**（例: フラグメントで定数色を出す diagnostic pack）で
全画面ブリットし、提示画像がその色になることを確認する。可視性の判定は常に提示画像で行う。

### S3進捗5: fitを頂点へ焼き込み（2026-10-23・未検証）

`fitTransformToBounds` の変換を**a_Positionバッファへ直接焼き込み**、変換行列は単位行列にした。
理由: 三角形（小座標）は描けて地形（ワールド座標）は描けない、という差は**プログラムが読む変換uniformのオフセットが
fitの書き込み先と一致していない**可能性を示す。頂点自体をclip空間へ入れておけば、どの行列を掛けても可視になる。

実測: この変更後の leg `packblit-14` は **キャプチャが確定せず**（`CELTIUM_PACK_CAPTURE` 行なし）、
提示画像は生成時三角形の緑一色だった ⇒ **可視性は未検証**。次は同じlegを再実行して
`CELTIUM_PACK_CAPTURE finalized` と `PACK_GEOMETRY` が出ることを先に確認し、そのうえで提示画像を見る。

### S3達成: 実地形がpackのシェーダで描かれ、提示画像に出た（2026-10-23・実画像）

`docs/evidence/pack-terrain-visible-2026-10-23.png`（1280×720, kept）の色分布:
```
(0,0,255)   797,040 px   クリア色
(199,199,199) 92,160 px  石の面（明るい）
(113,113,113) 28,800 px  石の面（暗い＝ライティング差）
(156,156,156)  3,600 px  中間
```
ログ:
```
CELTIUM_PACK_CAPTURE finalized format=3 vertices=4248 stride=32 (multiple terrain batches)
CELTIUM_PACK_GEOMETRY terrain vertices=4248 format=3 attributes=7
CELTIUM_PACK_RERENDER_GEOMETRY vertices=4248 indices=6372 pipeline=0x2b5...
```
⇒ **地形4,248頂点/6,372インデックスがBSLのステージで描画され、ブリットで提示画像に到達**。
面ごとの明暗差（199/156/113）はpackのフラグメントシェーディングが効いている証拠。

**判明した可視性の条件**: fit変換を**頂点へ焼き込む**こと（行列のuniformオフセット経由では地形が可視にならなかった）。
読み戻し（`probeSample`）はレイアウトを変えるため既定off。

残り: 実カメラ行列（現在はfitによる画面フィット）、パス構成（gbuffer→composite）、影パス。

### S3達成2: ゲームのカメラでpackが地形を描く（2026-10-23・実画像）

`docs/evidence/pack-camera-2026-10-23.png`（1280×720）:
```
CELTIUM_PACK_CAPTURE finalized format=3 vertices=2360
CELTIUM_PACK_CAMERA baked=2360/2360 projection=[0.803,1.428,-1.0002,0.0]
CELTIUM_PACK_GEOMETRY terrain vertices=2360 format=3 attributes=7
提示画像: clear 624,800px / (118,118,118) 277,440px / (199,199,199) 15,520px / white 3,380px
```
⇒ fit（画面フィット）ではなく**ゲームのprojection×modelviewをCPUでNDCへ焼き込み**、
packのステージが**実カメラ視点**で地形を描いた（被覆 92,160→277,440px）。
行列をuploadするのではなく頂点へ焼く方式が効く理由も判明済み（uniformインターフェイスが届かない）。

残り: 全セクション（現在はキャプチャした2360頂点のみ）、gbuffer→composite のパス構成、影パス（ライト行列は同じ焼き込み方式を流用できる）。
