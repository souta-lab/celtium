# Celtium 最大フル監査 — 2026-10-11 10:58開始

監査者: Codex。時刻はJST。主コードsnapshotは10:58、追加ソースは11:13:55、最終コード確認は11:15:29。開発中の実装を停止せず、前回17件の修正、デコンパイルされた原典、実行jar、CPU契約、最新画像、DSHの状態を横断して調べた。

**結論: native Vulkanの描画・present・compositor到達は実在する。しかし最新版は地形の描画回帰と文字欠落があり、全バニラの完成とは判定できない。新規の確定指摘は5件（P1 3件・P2 2件）。前回17件にも実装の改善と未閉鎖部分が混在する。**

文字の頂点バッファが書き込み末尾のアドレスを渡すP1を、実行jarとLWJGL 3.4.1のCPU実測で確認した。11:01:26にSWE2へ緊急queue済み。11:15:29のソースにも残っている。さらに、有限fence待機がtimeoutした後のpending資源解放、現在のvariantを参照側へ写してしまう画像較正を確認した。

GPU Validation 0、ゲームのexit 0、batch受付成功は、正しい画像を示すものではない。今回の最新runもその実例である。

## 1. 監査対象と固定した証拠

| 項目 | 値・範囲 |
|---|---|
| repository | `/home/s0u7a/projects/celtium` |
| 主snapshotのHEAD | `12730a9c28075b7d62d2c01bb7e504ccb7c685dc` |
| 最終確認HEAD | `93f35f67fe644637c58b3843cddbfe55d5905476`（11:15:29） |
| frozen source | `/tmp/celtium-audit-20261011-1058-4odbaxs9`。30ファイルの主manifest、7ファイルの追加manifest、5ファイルの11:13:55補足、dirty diff等 |
| 保存したsource archive | `docs/history/evidence/full-audit-20261011-1058/source-snapshot.zip`。794195 bytes |
| archive SHA256 | `475df8e7b617ccc1290b28797840561d8e27d5df416bdc8d0b00abb86f7f5a86` |
| 前回監査 | `docs/history/2026-10-11-full-audit-0903.md`。前回N01〜N17の各契約を追跡 |
| 基準環境 | Cleanroom 0.6.13-alpha / LWJGL 3.4.1 / JDK25。対象モジュールのrelease21を維持 |
| 実機の範囲 | 保存済みAMD RX6400 / Intel UHD630のrun。今回の新しい試行群は主にAMD |
| 独立に行った検査 | 実jarの逆アセンブル、既存CPU契約検査、ByteBufferアドレスのCPU実測、extent境界検査、配布jar検査、画像目視、run・source・DSHのread-only調査 |
| 追加していない操作 | 実装の編集、競合するゲーム起動、worker停止、goal変更、既存queueの削除 |

以下のクラス名は、原則 `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/` 内のファイルを指す。Mixinは `forge122/src/main/java/org/taumc/celeritas/mixin/core/`、glyphは `forge122/src/main/java/org/taumc/celeritas/impl/font/`、fixtureは `validation/client/src/main/java/org/soutalab/celtium/validation/` にある。行番号は明記したsnapshotに対する目安で、開発中のlive sourceとはずれる。

### 実行物のSHA256

| 実行物・用途 | SHA256 |
|---|---|
| 正常側に保持したp1-model-fix / p1-intel jar | `d2073e9456da7bccf01820ba961a6564874f3cebedbbc4f83b6a99fb5789899f` |
| 回帰側glu-depth jar | `c1d4e7db534651298b104c3daa43d35600c6e6961772e327d3462b41ed8c134e` |
| integrated 10:41の実jar | `d366510f5e83f06fa398147be132486780086bf73c58738f5d005ed0ad64be6a` |
| 11:09〜11:10のw7/w8/w9等に保持されたjar・独立逆アセンブル対象 | `68c6f5041945395f5fc396a05d8a331b49611e4bca82f88aa70c43482302cacf` |
| CPU検査へ固定コピーしたVulkan module jar | `8e09c7c1f59ad7df99ab5df5a7d09bfb8298db9f1cf25b13b23fe4bf047a099f` |
| 10:58時点の配布jar | `8096ed96858ca194d8ff6a57659f87ee90ae227c688193f6b3aa68f5eabbe533` |

「最新ソース」「buildディレクトリのjar」「各runへ投入したjar」は同一とは限らない。今回もそれぞれのhashが異なる。cleanup済みrunのmodsが空でも、保持したlauncher metadata・run report・pinとの対応を先に調べる。空のmodsディレクトリだけを欠損や破損と判定しない。

## 2. 原典から確定した描画契約

`AGENTS.md` と `scripts/mc-sources.sh` を確認し、既に生成されているMinecraft/Forgeの原典を読んだ。使用したMinecraft側は `.celtium-work/mc-sources/net/minecraft/` の2185 Java filesで、RFGのpatched source jarには7561 entriesがある。Forge側のpatched sourceとSRG名表も既存のGradle cacheにある。これはCleanroomの全変更が同一である保証ではなく、バニラの呼び出しとデータ契約を確定する起点である。

| 呼出し元 | 原典の契約 | 監査への意味 |
|---|---|---|
| `FontRenderer.renderDefaultChar` 230〜248 | mode5 TRIANGLE_STRIP。左上・左下・右上・右下の4頂点。glyph幅からUVとadvanceを決める | QUADSへmodeだけ変更すると同じprimitiveにならない。捕捉した頂点・UV・advanceを保つ |
| `FontRenderer.renderUnicodeChar` 272〜303 | mode5、Unicode pageをbindし、glyphWidthの左右境界でUVとadvanceを決める | ASCII成功だけで日本語やUnicode pageまで閉じない |
| `RenderGlobal`の天球gradient | mode6 TRIANGLE_FAN / POSITION_COLOR | 「空はすべてQUADS」と仮定しない |
| 太陽・月 | mode7 QUADS / POSITION_TEX | native format6との対応を原典で追う |
| fancy cloud | mode7 QUADS / POSITION_TEX_COLOR_NORMAL | native format8の受付から他の空のprimitiveまで推定しない |
| `Render.renderOffsetAABB` 290〜297 | mode7 / POSITION_NORMAL、current color白、texture無効 | uploaderの対応表にこの分岐がなくid0拒否。GL-lessでは描かれない |
| `WorldVertexBufferUploader` 28〜34 | vertex elementごとにByteBuffer positionを変更してpreDrawし、元のmode/countでglDrawArrays | バッファpositionは意味を持つ。native source addressの起点をproducerごとに確認する |
| animated spriteの供給 | `TextureAtlasSprite.generateMipmaps`→`TextureUtil.generateMipmapData`→`uploadTextureMipmap` が各levelを渡す | supplied mipを各extentでpatchする。atlas全体の未更新level数をspriteのstalenessとして数えない |

`POSITION_NORMAL`はSWE2の原典調査でも既に未対応として認識され、goal65に入っている。今回の新規発見数へ重複して加えていない。

## 3. 新規指摘5件

| ID | 優先度 | 指摘 | 根拠・到達した確認 |
|---|---|---|---|
| A01 | P1 | glyphのsource addressが書いた頂点の末尾を指す | source、2つの実jar、LWJGL3.4.1のCPUアドレス実測 |
| A02 | P1 | fenced submitのtimeout後にpending fence/command bufferを解放する | source、実jarのbytecode、Vulkanの有効使用規則 |
| A03 | P1 | reference calibration keyがcandidateのvariantを読む | fixture source、実runのreference key、参照runのvariant |
| A04 | P2 | upload byte countのlong乗算がoverflowし、負の必要量を受理する | actual module jarのhelperをCPU呼出しして再現 |
| A05 | P2 | device wait watchdogの完了flagが呼出し間で共有される | sourceから成立するinterleaving。実機false stallは未再現 |

### A01 — glyph頂点のnative読み出し起点が不正

対象: `CeltiumFontGlyphCapture.submit()`。11:13:55のsource SHA256は `9672f3c4fe7c9f99691ebbc506519e248f123b282622f264770e9ffaeffa0fbb`。主snapshotの同じ不正経路は332〜377、補足sourceでは360〜405付近。

通常branchは96-byteの `VERTEX` をclearし、4頂点×24 bytesをrelative putで書く。その後、flip/position(0)なしで `MemoryUtil.memAddress(VERTEX)` を渡す。positionは96、remainingは0で、アドレスは最初の頂点ではなく確保領域の直後を指す。position-color isolation branchも64-byteに4×16 bytesを書いた後、同じ誤りを持つ。

consumerの `VulkanDynamicMesh.copyVertices()` は渡されたaddressからstride×countをnative memCopyする。したがって、4つの正しい頂点を再生せず、バッファ外のhost memoryを読む。GPU側へは別の合法なbufferへcopyされるため、GPU Validation 0でも欠落や巨大なgeometryを生み得る。native memory読み出し自体にも不正アクセスの危険がある。

11:01のintegrated実jar d366...にmemAddressがあり、flipはない。さらに68c6...でも `javap -p -c` のsubmitにoffset508/518のmemAddressがあり、flipはない。ソースの推測だけではない。

LWJGL3.4.1を実際のclasspathで読み、GPUを作らず実測した:

```text
GLYPH_SIZE=96 position=96 remaining=0 addressDelta=96 explicitBaseDelta=0
AFTER_FLIP addressDelta=0 remaining=96
GLYPH_SIZE=64 position=64 remaining=0 addressDelta=64 explicitBaseDelta=0
AFTER_FLIP addressDelta=0 remaining=64
```

修正は両branchで書いた範囲を読み出し状態にするか、明示したbase addressとwritten extentを渡す。通常glyphをSTRIPのまま保ち、UV・italic offset・vanilla advanceを変えない。modeだけQUADSへ変えるdiagnosticは、stripの頂点順も変えなければ同じ四角形ではない。

**重要: texture isolation、force-quads、screen-fill、matrixのこれまでのglyph試行にも不正なアドレスが共通している。** その陰性結果から「textureではない」「matrixではない」と原因を排除できない。capture arraysへのfinite checkは、実際に渡したaddressの検査にはならない。古いscreen-fill試行には「buffer記入後にarrayだけ変更した」別の無効化要因もあり、こちらはsourceで先に修正されている。

まず、渡したaddressが最初の書いた頂点を指すこと、96/64 bytesのwritten extent内であること、読み戻した位置・UV・colorが原典の4頂点と一致することを安いCPU契約で確認する。その後、実jarをhashで固定してASCII/日本語/装飾/影の必要なGL-less画像を確認する。A01だけで現在のsky wedgeや地形回帰すべての根因と断定しない。

### A02 — timeoutの後にGPU所有物をfreeする

対象: `VulkanContext.submitWithSemaphores(VkCommandBuffer,long[],long[])`、主補足source400〜419。SHA256 `c1b18d1430cb8f1da4cf07b7e7d3f03cce442c1f0b059cd20a894400af4d9b28`。

`vkQueueSubmit` が成功し、 `vkWaitForFences(...SUBMIT_TIMEOUT_NANOS)` がVK_TIMEOUTを返すと例外を投げる。しかしfinallyは無条件に `vkDestroyFence` と `vkFreeCommandBuffers` を呼ぶ。timeoutはGPU完了の証拠ではなく、まだpendingの可能性がある。

68c6...実jarのbytecodeでも、waitが443、timeoutからのathrowが477、例外側cleanupが574のdestroyFence / 586のfreeCommandBuffersとなっている。有限待機への改善は実在するが、「timeoutならfreeしない」というコメントの契約とは一致しない。

Vulkanの `VUID-vkDestroyFence-fence-01120` はfenceを参照したqueue submissionの完了を要求し、 `VUID-vkFreeCommandBuffers-pCommandBuffers-00047` はpending bufferのfreeを禁止する。ここは通常の待機成功runがcleanでも閉じない。

**queueが受理する前の失敗、完了が確認された成功、受理後の未完了timeoutを分ける。** timeoutしたcontextは以降の使用を拒否し、まだGPUが所有し得るfence/buffer/関連資源を隔離する。timeoutをdevice-lostと同一視せず、実driver hangを起こさない安全なfaultでcleanupの所有遷移を検査する。backendの既存fence fault成功を、別のcontext/present submit経路の受入へ転用しない。

### A03 — calibrationのreference variantが自己参照になっている

対象: `ValidationClient.celtium$calibrationKey` 884付近、 `celtium$referenceCalibrationKey` 903付近、 `celtium$variantMeasuredAtVerdict` 4632付近。補足source SHA256 `6a7b2738d1dee768bd7b6045053c8bda8cce8c55f2840842131a7a1d7437e23a`。

calibration keyはscene・pose・GUI scaleだけでなく、System propertiesからworldLayers、blockSweep、allBlocks、entity flagsを読む。reference keyを作る側も同じhelperを、referencePose/referenceGuiScaleだけ変更して呼ぶ。**それ以外のvariantは参照runの値ではなく、いま実行しているcandidateのpropertiesで作られる。** それをcandidate側と比較すると、多くのvariant変更は一致してしまう。

例えば11:11のiso-glow-after-aでは、candidateとreferenceの両keyが `allBlocks=true entities=keepEntities+captureEntities+glowEntities` を含み `sceneCalibrationKeyMatches=true` になる。一方、記載された参照はscene-ref-terrain / scene-floors/terrain-aで、保存されたterrain-aのvariantは「default flat stack」。source自身も「measured references were taken with none of them」とentity flagsについて記している。

従って「keyにvariantが入ったから未知variantを拒否できる」とは言えない。poseやGUI scaleの不一致、欠けたpictureの拒否は改善しているが、N06全体の受入は未閉鎖である。今回の巨大三角形・単色画像のFAILは維持する。A03はそれらをPASSへ戻す理由ではない。

reference keyはhashで固定した参照runの実metadataから作るか、実測済みのvariantをimmutableなtableとして持たせる。candidateのpropertiesをreference側へ流さない。worldLayers、sweep範囲、allBlocks、entity flags等を変更したcandidateで、元の参照keyが変わらずUNCALIBRATEDとなることをCPUで検査する。frame identityの二つのcounter domainも混同しない。

### A04 — 最大int extentが負のbyte countとして通る

対象: `VulkanGlTexture.requiredUploadBytes()`、主snapshot1646付近。

positive width/heightを確認した後、`(long) width * height * 4L` を計算してInteger.MAX_VALUE超過を拒否する。しかし最大int同士ではlong自体がoverflowして負になり、上限検査とremaining検査をすり抜ける。

actual module jar 8e09...のhelperを4-byte direct bufferで呼んだ結果:

```text
width=2147483647 height=2147483647 remaining=4
EXTENT_BUG acceptedBytes=-17179869180
```

この検査ではnative copyもVulkan image作成も実行していない。通常のMinecraft texture寸法でこの値が発生したとは主張しない。ただし公開helperの境界契約としては破れている。longへのcastだけでは安全にならない。

上限へ収まることを乗算前に除算で確認するか、checked multiplicationを使う。負・ゼロのrequired量も拒否する。`x+pixelWidth` 等のregion extentも同じ契約で確認する。N08の普通のnonzero-position slice/hash修正は維持する。

### A05 — watchdogが別のwaitを自分のwaitとして読む

対象: `VulkanTerrainGpuBackend.celtium$watchDeviceWait()`、主snapshot7899〜7916、liveでは7908〜7925付近。

各waitはdaemon watchdogを起動するが、完了確認はbackend共通のvolatile `deviceWaitFinished` 一つで行う。wait Aが正常終了した後、別のwait Bがflagをfalseへ戻すと、AのwatchdogがBの進行中状態を読み、Aが5秒停滞したとしてgpuStalledを立て得る。

成立する例: Aは1msで終了。4.99秒後にB開始。5秒時点にAのwatchdogが起床しflag=falseを読む。Bはまだ10ms程度しか待っていなくてもstall扱いになる。実機でこのfalse stallを再現したとはしていないが、呼出し間の識別がないsourceから成立する。

完了・開始時刻・tokenをwaitごとに所有させる。古いwatchdogは別のwaitへ影響しない。daemonからrender threadへ渡すstall状態も可視性を保証する。無期限のdevice idleをwatchdogが強制中断できると説明しない。

## 4. 前回17件の再監査

「修正済み」はその不正契約が直った範囲を指す。native描画全体が完成した意味ではない。過去の良好runは保持し、新規batchへ自動継承しない。

| 前回 | 今回確認した改善 | 現状・残る確認 |
|---|---|---|
| N01 model頂点上書き P1 | nextVertexをvertex loop内に移し、4Q vertices / 6Q indicesを検査。d207...をAMD/Intelの実jarとして保持 | **当該上書きは修正済み・局所両GPU受入**。別の巨大三角形回帰を「thin streakだけ」と扱わない |
| N02 generation/retire P1 | compile generation、変更listだけ再構築、superseded retire、teardown idle後のreleaseを統合 | **部分実装・局所probe**。in-progress meshの例外回収が不完全。switch2はno probe list yetであり再compile/delete受入にならない |
| N03 glyph欠落 P1 | vanilla default/Unicode glyphを限定windowで捕捉するnative入口を追加 | **未閉鎖**。A01のpointer不正がsource/実jarに残る。48000 acceptedでも画像は保証しない |
| N04 empty stand-in P1 | unsupplied inputをframe開始前に拒否し、bridgeのGL chainを保つ | **旧不正経路を修正・fallback局所確認**。GL-lessの可視outlineは未受入。専用pairの変更中 |
| N05 record/submit layout P1 | post recording token、queue submit成功直後のcommit、shared image layout ledger、abandonを接続 | **部分受入**。token faultの復旧とunbound=0の計器あり。foreign transitionの実GPU abandonはまだ未到達 |
| N06 image false PASS P1 | picture欠落、pose、identity、GUI scale、未知sceneの判定を強化 | **未閉鎖**。A03でreference variantがcandidateから生成される。scene/variantの完全な較正とsemantic画質は未受入 |
| N07 mip patch P2 | producer供給の各levelを各extentでpack/copy。SAMPLER_MAX_LODとcensusを揃える | **CPU・AMD計器で局所改善**。mip無効controlのreachUnwritten356対0を既存証拠で確認。新batchのIntel/実画質は未閉鎖 |
| N08 pixel slice/bounds P2 | hash/copyがpositionから同じsliceを読む。direct/remainingを検査し、tail変更をcounterfactualで検出 | **通常slice修正をCPUで再確認**。A04のlong overflowを追加。glyphのraw pointerはこのhelperを通らない |
| N09 silent state failure P2 | 一部の取消失敗には名前付きcounterを追加 | **未閉鎖**。GlStateManagerMixin.withBackendのcatch(Throwable ignored)、compile/delete等の無報告catchが残る |
| N10 GPU能力 P2 | depth候補ごとのfeatures/image format query、同じ選択をtarget/pipelineで共有 | **depth選択は改善**。純nativeでもFD拡張が必須、128B limitのpostにUBO対応が必要。workerが実装中で受入は未 |
| N11 線幅 P2 | 1px固定pipelineに対して要求幅が1pxより大きければ拒否するgateへ修正 | **誤受理は修正**。2px等のnative描画は未完成。GL-lessで拒否すれば欠落する |
| N12 bench欠測 P2 | nullを等値として扱わずmissingFieldsへ分ける | **欠測の不正判定はCPUで修正確認**。missing/differ/equalがpass。GPU time比較や完成candidate性能は別工程 |
| N13 docs/配布 P2 | README/mcmodを部分更新、jar検証器をrelease21へ修正、goal.mdはrevision65へ同期 | **部分修正**。plan冒頭はrevision61、legacy bridge GUI受入等のmode/時点が混在 |
| N14 wait/present P2 | contextのpresent submit fenceを有限化。bootstrap queue waitsを計器化。device idleにwatchdog | **未閉鎖**。A02のtimeout cleanup、A05のwatchdog、無期限queue/device idle、長いacquireが残る |
| N15 format/counter P2 | COUNTを中央定義しid8/9を配列・受付・報告へ含める | **format集計漏れは修正**。同じ区間のoffered/recorded/submitted/completedとnever-offered coverageは別に必要 |
| N16 post descriptor ring P2 | 同recordingで32slotsを超えるとthrowし、更新済みsetの再使用を防ぐ | **aliasの旧経路をsourceで修正**。supportsAllにはpass数の事前検査がなく33番目で拒否。32/33 boundaryとGL-lessの欠落を受入する |
| N17 partial allocation P2 | textureのstaging/image作成に部分cleanupを追加 | **部分修正**。HostBufferとDynamicMesh constructor、in-progress model meshには漏れが残る。image faultはcreate前でpartial image cleanupの証明ではない |

### N02/N17で取り残した所有契約

`VulkanHostBuffer.create()` 105〜137はbufferを作った後のmemory type選択、allocation、bind、mapにcatch cleanupがない。既存のallocation fault126はbufferを作った後でthrowする。constructorが返らないため、callerはそのobjectをdeleteできない。

`VulkanDynamicMesh` constructor115〜121はvertex bufferを作り、その後index bufferを作る。index側で失敗すると既に作ったvertex側が残る。

`VulkanTerrainGpuBackend.celtium$buildModelMeshes()` 6102〜6178は新meshを作って頂点を埋め、完了後の6164でbuilt mapへ入れる。outer catchが回収するのはbuilt map内だけなので、mapへ入る前のcurrent meshでput/capacity処理が例外になった場合は回収されない。既にmapへ入ったmeshをdiscardする改善と、作成中objectのcleanupは別の契約である。

対応は未提出objectの作成ownerを一つにし、作成・初期化・mapへのcommitのどこで失敗しても現在のobjectを回収する。GPU提出済みobjectを同じ即時cleanupへ混ぜない。

n02-switch2には実際のbackend teardownでlive2→0、次backendで0→2のlogがあり、ここは有効な改善である。一方、probe自身は複数箇所で `verdict=PASS no probe list yet` と書く。これをrecompile/deleteの機能PASSへ膨らませない。旧10-step probeと、新しいretire-before-stamp・double-waitの差分を区別する。

### N05/N07/N17の受入範囲

11:05の08ce52a37はlost workerの実装を別workerが確認したcommitで、限界も記している。

- foreign ledgerは3runでunbound=0、commits1109/1590/970。ForeignLayoutLedgerTest5/5。
- それらの場面にはforeign image transitionがなく、nonzero abandonを実GPUで作れていない。unit rollbackをGPU rollbackへ読み替えない。
- mip producerの原典とper-level copyは整合。patchMipLevels=falseのcontrolはreachUnwritten356、通常0。
- staging allocation faultはbuffer作成→memory拒否→buffer解放を示す。
- image allocation faultはvkCreateImageより前に発火し、logのimage handleはstale。partial-created imageのcleanupを試した証拠にならない。

必要な限定試験はpublishedChainOutputのforeign transitionを含むsubmissionでのfaultと、その後の再record、post chain input/output画質である。資源作成faultは「本当に作成したhandle」をowner別に記録する。

## 5. 最新画像と回帰の再確認

最新画像3枚を実際に開いた。run名だけでsceneやmodeを決めず、process report、fixture metadata、画像sourceを合わせて読んだ。

| run・時刻 | 構成 / scene | 実画像と判定 |
|---|---|---|
| p1-intel 09:16 | Intel GL-less / terrain allBlocks | 非背景0.734557、当時PASSED。正常側pin d207...。新batchの受入へ転用しない |
| integrated 10:41 | AMD GL-less | 巨大な三角形が残る。実jar d366...にglyph pointer不正もある |
| wedge2-bridge 10:51 | bridge / terrain | actual pitch=-14に対しreference+20。比較条件が違いFAIL。参照と同じposeへ直す |
| wedge3-noimmediate 10:56 | AMD GL-less / terrain | 単色、非背景0。pictureFrameDelta=-103のidentity問題もありFAIL |
| tex2 10:58 | AMD GL-less / texture契約確認 | presented world imageが未取得でFAIL。計器の局所試験として使い、画像受入にはしない |
| w6-only6 11:07 | AMD GL-less / terrain allBlocks | 非背景0.364906。w9と同じworld PNG hash、FAIL |
| n03-h-bridgestate 11:09 | AMD **bridge** / videoSettings | GL screenshotの文字は読める。ただし画像ゲートFAIL3件。native GL-less glyphの成功ではない |
| w9-only67 11:10 | AMD GL-less / terrain allBlocks | 巨大三角形・地形回帰、非背景0.364906、FAIL。実jar68c6... |
| iso-glow-after-a 11:11 | AMD GL-less / terrain、allBlocks+entity/glow flags | world presentedは全面同色で非背景0、FAIL。比較variantの較正にもA03がある |

w9とiso-glow-after-aはゲームexitCode0だがvalidationExitCode1、validationClean=true / messageCount0であり、harnessは正しくFAILED_PRESENTED_IMAGEを出している。これを「validation cleanだから画像は正常」と上書きしない。

### 目視した画像の出所とhash

| run | 画像パス（repository基準） | PNG SHA256 |
|---|---|---|
| w9-only67 | `.celtium-work/runtime/w9-only67/screenshots/celtium-presented-world-run-145dbd0141316-66d3c617-kept.png` | `25238b5edf64b19a377584f2b5d4537b1e9e2bf8fc993fb9f5d781360a465f71` |
| n03-h-bridgestate | `.celtium-work/runtime/n03-h-bridgestate/screenshots/celtium-validation.png` | `2638d1f652210cbd6d0835dd3e22ea06226416490137a5a9889a88273691747c` |
| iso-glow-after-a | `.celtium-work/runtime/iso-glow-after-a/screenshots/celtium-presented-world-run-145e68298c0c5-66d3c617-kept.png` | `25b736a60fd720ab11ba46fa7abf72a69aff4d691a680a2d1505539511c6e4aa` |

GL-lessの `celtium-validation.png` はGL screenshotならstubの黒画像で、presented-dumpと異なる。今回、worldのkept PNGとidentity sidecar、画像hashを確認した。保存済み画像の改変はしていない。

### 回帰の切り分け

保存されている正常側d207...と回帰側c1d4...を確認した。DSH内の調査報告も09:16の正常run→09:33の最初の不良runを示し、11a826eee / 93f35f67fでSWE2も回帰とFAILを正式に認めている。

候補の変更区間はb7b634179（09:17 framebuffer identity）、30a30a375（09:27 projection hook）、9655ff681（09:30 depth selection）等。これらは候補であり根因確定ではない。A01のglyph導入時期も、別に実dataの起点から確認する。正常画像のない区間を古いmtimeのrunで埋めない。

format mask変更で単色になったことは、そのformat単独が全sceneの必要条件であることしか示さない場合がある。no-immediateは世界の正当性を受入する構成ではない。まず同一scene/pose/fixture/flagsの良好pinとcandidateを並べ、頂点bytes・projection・main/identity0・target所有を比較する。三角形をdepth biasや色の定数で隠さない。

## 6. Vulkan到達度・互換性の評価

| 領域 | 到達した実装・局所証拠 | 最新版を完成としない理由 |
|---|---|---|
| terrain | native geometry/pipeline/SPIR-V、過去のAMD/Intel画像 | 最新GL-less地形が回帰。透明物・mip・全state等の受入範囲も残る |
| immediate | format1〜9、複数native pipeline | POSITION_NORMAL未対応、wide line拒否、API入口外・state failure・target所有 |
| model/entity/TESR | native mesh、29lists/160quads等の保存実績、本の位置一致、N01修正 | N02のin-progress cleanupと寿命受入、最新統合の画質、全entity/TESR |
| sky/cloud/weather/particle | 原典でmode/formatを整理、particle descriptor gateを改善 | format受付だけでは全variantの見た目と順序を保証しない。wedge根因未確定 |
| GUI/font/HUD | GUI/item batchはnativeに到達、bridgeの文字画像あり | GL-less glyphの不正pointer。ASCII/日本語/装飾/影とrecipe/UI画像未受入 |
| FBO/post/outline | executor/token/ledger、unsupplied拒否、専用pair設計中 | 最新outline画質未成立、foreign abandon GPU試験、128B/long chain、複数FBO所有 |
| present/compositor | swapchain native present、過去のAMD Sway captureとframeの0/921600画素差 | 最新frameが正しい内容かは別。A02、resize/minimize/失敗の新batch受入 |
| Nether/End | dimension到達と局所画像、Nether専用pose | IntelのNether新受入は未。A03のvariant較正を直す |
| existing mods | Fugue/Scalar/JEI/AppleSkin/MapWriter等のload・局所描画実績 | JEI文字/recipe、MapWriter minimap、AppleSkin survival HUD等は全体受入ではない |
| mesh shader | 通常のvertex/index pipelineで進む。mesh shaderを必須化しないgoal | model mesh対応はmesh shader対応ではない。専用機能の性能証明はない |
| 影mod/shader packs | 後続shader-plan.md | 本体+主要modのshaderless成立後。vanilla postは延期対象にしない |

既存Minecraft/Forgeの意味を持つ入口と必要なadapterをnative化する方向は維持する。GL-less stubに直接GLを呼んでも描画は復活しない。新しいno-op censusは未到達経路の特定に使い、呼出しが0になったことだけを正しいnative描画の代用にしない。

既存modの「load」と「目に見える主要機能がnativeで正しく描かれる」は別に記録する。creativeのAppleSkinでsurvival hunger HUDを受入したことにしない。MapWriter minimapがbridge参照にもない場面は、Celtiumだけの故障と決めつけない。JEIのITEM受付9483/9483は文字・recipe layoutの画像を保証しない。

## 7. GPU能力と待機・資源

- Depth選択はD32固定より改善している。ただしattachment用途とsampled用途に本当に必要な能力をそれぞれ要求し、image/view/aspect/render pass/pipelineで同じ決定を使う。過去のAMD24-bit非対応の観測を全GPUの性質へ広げない。
- `VulkanContext.matchPhysicalDevice` 742〜750 / `createLogicalDevice` 784〜786は、GL-lessでもexternal_memory_fd / external_semaphore_fdを必須確認・enableする。interopを使わないnativeの能力契約へこれを残す理由はまだない。移植性は未完で、未検証OS/vendorを対応済みにしない。
- snapshotのpostはspider140B/color_convolve180Bをpush constantsで扱い、128B limitでは拒否する。UBO shader4ファイルとworkerが進行中だが、dirty/untrackedを完成artifact・GPU受入へ読み替えない。
- immediate pipelineのlineWidthは1.0固定。新gateは1pxより大きい要求を正しく拒否するが、GL-less selection outlineの2pxをまだ描けない。必要ならnative geometryで幅を表現し、device wideLines対応名だけで受理しない。
- `VulkanSwapchain`のacquireはLong.MAX_VALUE。これはJava longの最大値をnsへ渡す非常に長い待機で、`UINT64_MAX`と同じbit patternではない。bounded recoveryの保証にはならない。
- context begin/endのqueue idle、context/post deleteのdevice idleはtimeout引数のないAPI。textureのupload/patchでもcontext one-shotを呼ぶため、これを「bootstrapだから通常frameには関係しない」と名前だけで除外しない。既存bootstrapQueueWaitsから実際の頻度と費用を見る。
- HostBuffer resizeはold bufferをdeleteする。即時meshは完成してrecordされたgroupと作成中groupを分ける設計だが、録画済みbufferを後から伸ばす経路がないことを所有契約で維持する。今回、その危険が実際に起きたと確定はしていない。
- model retireのdouble-waitと静的retire queueは改善である一方、「wait回数」だけで任意のpending資源すべての完了を証明しない。recorded/unsubmitted/in-flight/retiredを区別し、実queue/fenceのownerが解放を決める。

## 8. 安い独立検査と配布物

共有Gradle buildを競合させず、既存artifactを `/tmp/celtium-audit-cpu-20261011-vwxi9pkn` へ固定コピーし、javac --release21で既存TextureContractCheckと独立address/extent検査をcompileした。

```text
javac --release 21: exit 0
TextureContractCheck: exit 0 / CELTIUM_CPU_CONTRACT PASS
bench_controls_check: missing -> ok, differ -> ok, equal -> ok
AuditCpu: 96/64-byte glyph address deltaを再現
AuditCpu: 最大int extentが負のrequired bytesとして受理されることを再現
```

TextureContractCheckはflat ARGB→RGBA、row/region、target別queue、nonzero-position slice、remaining/direct、per-level conversion等のCPU契約を確認する。実GPUのbarrier、frame画質、vertex producer全体、失敗後のpending資源を保証する検査ではない。

既存JUnit XML（11:03時点）は6 suite / 24 tests / failures0 / errors0だった: ForeignLayoutLedger5、command state4、device report3、GL state5、immediate drawer6、off-target routing1。今回これらを新たにGPU試験したとはしていない。OffTargetPairRoutingTestはidentity0と非zeroのpredicateの検査であり、複数FBOの独立所有や可視glowの受入ではない。

`verify_forge_jar.py` を正しい配布jar68c6...へ実行した結果はverified=true / errors=[]。class majorは46:111、51:1、52:613、65:87で、65超過・previewはない。release21の配布条件とgame/buildのJDK25を混同しない。中間reobf jarは配布jarではなく、これを検査して不足entriesを配布不良として報告しない。

配布checkerはCOPYING/COPYING.LESSER/META-INF/LICENSE/bitraster noticeや既存互換modid celeritas等を確認した。これらの収録は保持する。今後のshader/最適化コードの取り込み許諾まで保証するものではなく、現行の制限付きSodiumコードをコピー・翻訳しない既存方針を維持する。

性能3.94ms/4.89ms/16.10msの保存測定はある。参照側の16.10msは60Hz提示間隔の影響が残り、「描画GPU costが4倍速い」とは言えない。今回の回帰画像を性能candidateに採用せず、見た目と描画量を揃えた節目で比較する。

## 9. DSHの行動と文書整合性

11:16前後にrootのrunning=true、goal ID `goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`、revision65、phase activeを確認した。rootとは別に、N03 glyph、remaining wedges、glow input、N10 UBOの4セッションがrunningだった。停止していない。armedは文書に記載されているが、今回のAPI readだけで独立に確認したとはしない。

rootの公開turn outlineは「デコンパイル金脈やん。掘るしかないやろ掘れ掘れ」のturn730で進行中。新しいAGENTSとmc-sources command、no-op census、textureの独立受入、gate FAILを正常扱いしない規則をcommitしている。この改善は残す。

一方、11:01のglyph P1は11:10のroot inboxでnext-turnに残っており、**queueの受理は確認できたが、読了・修正済みではない**。font workerの試行も同じ不正pointerを持つjarで続いていた。このMDと具体修正条件をqueueで届ける。

文書の未整合:

| 場所 | 現在の記載 | 必要な訂正 |
|---|---|---|
| plan冒頭7 | revision61 | DSH/goal.mdの65と同期 |
| plan30 GUI/font/HUD | 画像等価受入済み | legacy bridge / 当時のjarと明示。GL-less glyphはA01で未完 |
| plan38 | 巨大wedgeは消滅、残りthin streak | N01の旧根因修正と、09:33以降の別回帰・最新巨大geometryを分ける |
| plan39 | 残りGL context生成と起動直後のみ | 最新のGL-less内容欠落と未対応入口をnative coverage未完として明記 |
| plan47 model/TESR | TESR未capture、lists2/12 | 新しい本/29lists/160quadsの実績と整合させ、最新統合の未受入を別記 |
| plan52 calibration | 実装済・受入済 | A03のreference variant不正を未閉鎖として記載 |
| planの担当表 | 過去workerを担当稼働中と記す行が残る | 現担当・commit・局所受入・未受入を現在の冒頭表へ反映 |
| goal.md | revision65で全体仕様を同期済み | 過去両GPUPASSの時点を明記し、最新版回帰やA03を完了へ継承しない |

goalの最後のユーザー指示、goal ID、shader後続工程、release21/game25、Cleanroom専用、汎用GL→VK化しない方針は維持する。この監査ではgoalの変更をしていない。訂正を末尾に足すだけで古い冒頭表を放置しない。

## 10. 次の作業を最短で進める単位

1. **glyphのproducer/consumer契約を揃える。** A01を両branchで直し、raw native addressとwritten verticesをCPUで照合する。無効だったglyph probeの結論を撤回し、必要なGL-less画像を一まとまりで受入する。
2. **正常側pinから最新の回帰を絞る。** d207...を保持し、同じscene/pose/variantでc1d4...と最新artifactのinput bytes・projection・target identityを比較する。定数の微調整や全format-mask反復だけを主作業にしない。
3. **検証器の参照を固定する。** A03を修正し、candidateのvariant変更でreference keyが変わらないことをCPU検査する。過去のunknown variantへPASSを遡及しない。
4. **GPU所有契約をまとめて修復する。** A02/A05、N02/N17の未提出object cleanupとpending object隔離を一貫させる。既存のtoken/ledger/retire修正を壊さない。
5. **FBO/post/能力のbatchを完成させる。** 専用silhouette input、foreign transitionのfault、32/33 descriptor境界、128B UBO、native不要FD要件を揃えてから必要なAMD/Intel場面へ進む。
6. **原典表の残る入口をnative化する。** POSITION_NORMAL、wide selection line、文字の統合後に主要modの実機能を確認する。汎用OpenGL変換器へ拡大しない。
7. **現在表と配布物を同期する。** mode・jar・GPU・scopeを付けた状態表へ直す。正常なcandidateで必要なベンチ/互換受入へ進む。shader pack工程は本体+主要mod成立後に保持する。

関連するproducer/consumer/ownerを一度に整え、実装途中はcompileと短いCPU契約を使う。ゲームを起動するのは意味のある実装段階が揃った時点にする。新差分・失敗・未解決の懸念に検証を絞り、既に閉じた修正を番号順に繰り返さない。

## 11. 外部一次資料

- [LWJGL MemoryUtil](https://javadoc.lwjgl.org/org/lwjgl/system/MemoryUtil.html): memAddress(ByteBuffer)は現在position、overloadは指定positionを使う。公開Javadocはsnapshot表記のため、今回の根拠は実際の3.4.1 CPU実測も併用した。
- [Vulkan vkDestroyFence](https://docs.vulkan.org/refpages/latest/refpages/source/vkDestroyFence.html): fence参照submissionの完了規則01120。
- [Vulkan vkFreeCommandBuffers](https://docs.vulkan.org/refpages/latest/refpages/source/vkFreeCommandBuffers.html): pending command bufferをfreeしない規則00047。
- [Vulkan vkAcquireNextImageKHR](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html): timeoutとWSIの進行条件。Java Long.MAX_VALUEとUINT64_MAXを混同しない。

監査限界: 全GPU/全OS/全mod/全entity/全block stateを新たに実機で試したものではない。画像の回帰は確認したが全根因を確定したものでもない。source修正、CPU契約、実行物への反映、GPU Validation、実画像、機能全体の受入を別々に示した。

## 12. 配送記録

緊急A01 queue: 2026-10-11 11:01:26 JST、request `93ecccdd-15d6-4394-b7fb-7a986d8ae8d0`、tag `CELTIUM_AUDIT_20261011_1058_URGENT_GLYPH_POINTER_CX`。accepted=true、root inboxにrequest IDを確認。読了・実装完了の証明ではない。

フル監査の配送記録は受理確認後に追記する。
