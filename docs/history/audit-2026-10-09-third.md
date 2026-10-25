# Celtium 第3回監査 — 2026-10-09 22:27以降（JST）

## 結論

現状は広い描画経路を持つ **GL/Vulkan hybrid**。バニラ完成・全描画の見た目一致・GLなしの起動・通常画面のnative presentは完了していない。
「レイアウト認識」「受理」「vkCmdDraw記録」「GPU完了」「画像一致」「画面表示」を分離する必要がある。
新しいテクスチャ経路は実際に動くが、カウンターの増加だけでは正しいテクスチャ更新を証明できない。
監査は32項目。開始時の欠陥記述と、末尾の「監査後の修正・検証状況」を分けて読む。修正が入った項目を未修正のままとは扱わない。

監査開始HEAD: `c89bda361d2a6b4c84f854b9ae07c7d54f604b20`。開始時にバックエンドとレジストリの未コミット変更あり。
74ソースファイルを読み取り用に保存し、描画入口・group・pipeline・shader・texture・frame target・swapchain・fixtureを重点確認。
比較用バイナリは別途SHA-256で固定した。DSHは監査中も動いているため、本書の確定欠陥は監査開始版を基準とし、後続修正の完了とは混同しない。
隠れたreasoningは監査対象にしていない。公開応答、ツールの実行結果、ソース、成果物を根拠にした。

## 独立に確認した重要な欠陥

### A01 [P0・コード確定] アフィンmodel-viewの平行移動が消える
`VulkanTerrainGpuBackend.pushMatrixColumnsInto`（開始版4087付近）は4x4行列の最初の3列のみを送る。
各immediate vertex shaderは最後の列を `vec4(0,0,0,1)` に置換する。
アフィン行列で一定なのは最終行であり、最終列のxyzには平行移動が入る。従って `modelView[12..14]` が消える。
CPUで契約を再現: 平行移動(5,7,11)、頂点(2,3,4)は本来(7,10,15)、現実装は(2,3,4)。
**受入:** 平行移動・回転・非一様scaleの組合せを正しく送る。3x4の行パック+dot等かUBOを使い、全immediate shaderとjar内SPIR-Vを揃える。128 byte上限を直すために意味を壊してはいけない。

### A02 [P0・一次ソース照合済み] アニメーションのint[][]を行配列として読む
`VulkanTextureRegistry.announceFullPixels` は `data[row][col]` を読む。
Minecraft `TextureUtil.func_147955_a` の一次ソースでは外側はmip levelであり、各 `data[level]` は平坦なpixel配列。幅・高さ・offsetもlevelごとにshiftされる。
2x2入力 `[[11,12,21,22],[99]]` はlevel0=[11,12,21,22]、現変換結果=[11,12,99,0]。
**受入:** `data[level][y*levelWidth+x]` とレベル別寸法を使う。動的textureの平坦int[]を行配列化する便宜上の経路とは別契約にする。色順・行順・サイズを非対称patternで確認。

### A03 [P0・Vulkan仕様照合済み] 部分更新なのにatlas全体の内容を破棄する
`VulkanGlTexture.uploadPatchFromPixels` は全面uploadと同じ `copyToImage` を呼ぶ。
そのbarrierは `oldLayout=UNDEFINED`、range=全mip。部分更新の外側を保存する契約がない。
UNDEFINEDからの遷移は対象subresourceの既存内容を保存しない。validationが無言でも、この操作自体は合法なので画質欠陥を検出できない。
仕様: https://docs.vulkan.org/refpages/latest/refpages/source/VkImageLayout.html
**受入:** 全面初期化と部分更新を分離し、現在layoutとsample/transfer依存を追跡。patch外を保存する。2つの離れたspriteを順に更新し、未更新領域がbyte単位で変化しないことを確認。

### A04 [P1・コード確定] 初回patchだけで全面未初期化のmirrorを作る
レジストリはentry未作成時、GLから寸法のみを読んで画像を作り、部分領域だけを埋める。
他のspriteに必要な全面データを持たず、その後staleを解除する。layout遷移は内容初期化ではない。
**受入:** atlas stitch時の全面データを保持・供給し、初期化が揃うまでpatch-only mirrorを描画可能扱いしない。

### A05 [P1・コード確定] patch寸法でmip chainを作り直す
`copyToImage` はpatchのwidth/heightを `recordMipChain` に渡す。この処理は原点からblitし、atlas全体・sprite境界・patch offset・入力mipを再現しない。
**受入:** Minecraft/Celeritasのsprite単位mipを元データから供給する。全atlasの単純blitを正解とせず、遠景・透明境界で比較する。

### A06 [P1・最新経路も再確認] 保持した古い画像とpatchが競合する
後続版では `announcedImages.remove` を `get` に変えて読み戻し回数が減った。
しかし `get` はstaleと無関係にその画像を `fromImage` へ渡す。更新世代やCPU側patch反映がないと、古い全面画像が新patchを上書きする。
また `uploadPatchFromPixels` は `lastPixelHash` を無効化しないため、後の全面uploadが同じhashならskipされる。
**受入:** 全面・patch・削除・再生成を同じresource generationで管理し、CPU shadowも更新する。hashと成功世代は転送成功後に確定する。call数減少を正当性の証拠にしない。

### A07 [P1・コード確定] texture metadata・転送成功の扱いが不十分
`Entry.width/height` はfinal。全面image uploadによるresizeはVulkan画像を作り直すがentry寸法を更新しない。後のpatch bounds/whole判定が古い寸法を使う。
upload側は成功前にhashを記録し、registryはuploadがskipか成功かを区別せずimageUploadsを増やす。
**受入:** size・generation・descriptor・hashを成功した更新と一緒に確定。resize/reload、転送失敗後retry、GL ID再使用を確認。

### A08 [P1・コード確定] 受理前の失敗でpending meshに頂点が残る
`submitImmediateBatchTimed` はcopyVertices、groupUsed、pendingIndicesを更新してからtexture/descriptorをpreflightする。
その後falseを返すとGLが描くのに、pending meshにも同じバッチが残りflushされ得る。
**受入:** 全preflightをappend前に行うかrollback可能にする。失敗時はGLまたはVKの一方のみ。allocation/descriptor失敗注入で確認。

### A09 [P1・コード確定] 受理後の消失を全形式で数えていない
particleだけはdescriptor失敗時にpostAcceptDroppedGroupsを増やす。item/block/textured各branchはcontinueしても同じカウンターを増やさない。
「drop=0」は全受理geometryの描画を証明しない。
**受入:** offer/accept/GL cancellation/record/submit/completion/dropをbatch IDで対応付ける。全branchと例外を集計し、未処理を成功扱いしない。

### A10 [P1・コード確定、完了報告を撤回] offTargetDrawnは実描画ではない
off-target offer時にoffTargetDrawn++し、リソース作成・flushの後に **return false** する。
開始版はGLが実描画を担当し、そのバッチはVK groupに入らない。`recordOffscreenPass` は一致geometryが0でもbegin(clear)→end→copyBackを呼ぶため、copy-back回数は空clearの書戻しも数える。GL所有FBOへ空clearを書き戻す危険がある。
後続版はtarget別filter/retireと空passの抑止を追加したが、実バッチのdraw→submit→完了→copy-backの対応と画像検証が必要。`recordOffscreenPass` の存在とknown-colour self-testは別の証拠。
**受入:** カウンターをoffer/refuse/record/drawn/copied-backへ分離。FBO完了扱いを撤回。target別VKパス、元の順序、depth、clear、resize、描画後sampleまで実geometryで確認。

### A11 [P1・コード確定] format 2は既定でGLに残る
`VulkanImmediateFormats.DEFAULT_ACCEPTED="1,3,4,5,6,7,8,9"`。POSITION_TEX_COLORは認識されるが既定では受理されない。
実測でも `acceptedByFormat=2:0`。format 0=0を「全てVK」と解釈できない。
**受入:** formatだけで画面compositeとGUIを同一視せず、Minecraftの呼出箇所・pass意味で分けて移植する。

### A12 [P1・コード確定] state modelとpipelineの実装が一致しない
alpha enable/func、depth func、separate alpha blend、color mask、scissor、texture matrix等のmodel/入口/実利用に穴がある。
pipelinesはLEQUAL・ADD・alpha側blend因子を固定。particleはcaptureしたstateを使わずblend/depth-write等を固定する。
shaderのalpha比較はGREATERの等号境界やref=0を再現しない。
**受入:** vanillaが使う状態を入口→snapshot→group key→pipeline/shaderまで対応付ける。非対応を名前で残し、汎用GLエミュレータへ膨らませず意味別passを実装する。

### A13 [P1・コード確定] current color/alphaが失われ、group keyにも入らない
POSITIONの色はGL_CURRENT_COLORのRGBのみを取りalpha=1にする。色の変更はgroup境界にならない。
POSITION_TEX shaderは色を白に固定。通常のcurrent colorによるtint/alphaを再現する証拠がない。
**受入:** 必要なRGBAをMinecraft側から保持し、対象passのgroup境界とshaderに渡す。2色・2alphaの隣接batchを確認。

### A14 [P1・コード確定] normalを無視したITEM対応はlighting対応ではない
ITEM/format8等のnormalをshaderが読まないことをコードが明記。固定lighting・normal transform・モデルのshade・fog等を再現していない。
JEIのitem受付から全entity/TESR完了へ拡張できない。
**受入:** vanillaモデル/ITEM/entity/TESRの照明意味を個別に確認し、法線とlight parameterをネイティブに供給する。

### A15 [P1・コード確定] samplerの固定値と旧比較による調整
blur/clamp等のupload引数をannounced image側へ渡さず、samplerはclamp-to-edge、mipmap linear、LODを固定する。
旧比較の最小差で定数を選んだことは、実際のtexture状態の再現ではない。
**受入:** 元データ・texture lifecycleからfilter/wrap/LODを追跡。resource pack、繰返しUV、mip有無を確認。

### A16 [P1・コード確定] 「hot-pathのGL読取り除去済み」は過大
snapshot時のMODELVIEW、POSITION groupのCURRENT_COLOR、fogのglIsEnabled/glGet、bound texture・FBO・viewport照会が残る。
診断専用のreadと通常経路のreadを分けて数える必要がある。
**受入:** 通常フレームの実GL API call countとreadback bytesを計測し、Minecraft側のcamera/state/resource情報から供給する。

### A17 [P1・検証不足] withoutGlIdentity成功はGLなし描画ではない
このflagはGL identity照会を省く診断。Minecraft/LWJGLXXは実際にはGL contextを作り、bridgeがない描画はGL fallbackで完走できる。
例外0・fixture PASSEDを完成条件(3)達成にしない。
**受入:** GLFW等のnative windowをno-GL-contextで作り、全frameをVK資源からrender→compose→presentする。GL fallbackが0であることを確認。

### A18 [P1・試験経路限定] presentのbridge ownership/layout契約
composite presentは共有画像を使い、sourceのbarrierはQUEUE_FAMILY_IGNORED、復帰先はCOLOR_ATTACHMENT_OPTIMAL。
frame targetの公開handoff契約はGENERAL/EXTERNAL。S01の所有権・layoutの整合は依然独立に確認が必要。
**受入:** 移行中bridge経路の同期/ownershipを明確化し、最終native pathはbridge資源を使わない。out-of-date/submit失敗でdeadlockや再利用がないことを確認。

### A19 [P1・検証不足] native presentの画面到達証拠
swapchain return成功、markerがGLのreadbackに無いことは、ユーザーのwindowに完成frameが出た証明ではない。
失敗したx11grab一方式から「環境上永久に確認不能」と断定しない。
**受入:** 利用可能なcompositor/headless output capture等を調査し、frame markerと連番で実際のwindow出力を確認。不可なら具体的条件を残す。目標を縮小しない。

### A20 [P1・コード/検証境界] wait failureを全面閉鎖したとは言えない
steady-state stall注入の修正は有効な実績。一方beginCommandsのqueue idle、Long.MAX_VALUE fence/acquire、device/queue idle等が残る。
start-up/texture転送/presentの失敗は同じ受入ではない。
**受入:** S02を経路別に再評価し、timeout後の再record/reset/destroy禁止、明示失敗・再構築を保証。待機APIの限界も明記する。

### A21 [P1・一次ソース照合済み] vanilla postprocessを根拠なくscope外にした
SRG環境でmethod名に"shader"がないのは未実装の証拠にならない。
Minecraft EntityRenderer.func_175066_aはcreeper/spider/enderman視点でShaderGroupをロードする。`func_175069_a(ResourceLocation)` は引数ありであり、無引数の反射候補と一致しない。
**受入:** spectatorの3視点、entity outline等をnative passとして実装・確認する。shader pack対応の後回しとバニラShaderGroupの省略を混同しない。

### A22 [P1・検証不足] ワールドは全バニラのcoverageではない
固定flat: bedrock / dirt×2 / grass / water / leaves / stained_glass。地形比較設定では非player entityを除去する。
全block、entity、TESR、dimension、天候、破壊overlay、特殊GUI等の網羅を証明しない。空のlayoutがあるのはcoverage不足。
**受入:** registryから自動生成するblock/state・item/model・entity・TESR一覧と場面別coverageを作る。危険/無意味な全state組合せは理由を記録。小場面に分割しnative drawと画像を確認。

### A23 [P1・検証不足] 古い画像差とlocal attributionの混同
68.81%は以前の固定Celeritas baselineに対する地形差。0.267/255と0.40%は同じCeltiumの描画切替によるlocal比較。
最新版全体の一致率や99.6%の完全一致率へ変換してはいけない。baselineと画像が削除されたrunは再現可能な数字として扱えない。
**受入:** pinned Celeritas GL対current Celtiumを同一fixture/GPU/camera/sceneで再取得。baselineのmods/hash、揺らぎ、pixel/channel単位、領域別差と目視を残す。

### A24 [P1・コード確定] 比較器が欠落したverdictをfail-closedにしていない
`compare_validation.py.process_problems` はvalidationClean=Falseだけを拒否し、missing/Noneは通す。
exit codeがintでない場合、fixtureStatus=Noneも失敗にならない。空dictのprocess reportを正常と扱える。
比較本体はnativeTerrainVerified=Trueを旧GL比較の証明範囲外としてrejectする。旧リブランド確認の比較器を、そのままnative描画の最終受入へ流用できない。今回の追加runもexit -6/255・MISSING_REPORTなのにvalidationClean=Trueとなり、validationClean単独を成功とできない実例になった。
**受入:** required fieldsの存在・型・成功値を必須化。GL fallbackやnative frame未成立をvalidationCleanだけで成功にしない。

### A25 [P1・測定設計] countersの単位が完了主張と違う
imageFilledMirrorsはunique resource数ではなく呼出し/refresh回数になり、hash skipでも増える。
outsideWorldPassAcceptedはaccept判定より前に増える。blend数はgroup開始時、layout数はbatch数。frameAcceptedとframeDrawnも単位が一致しない可能性。
**受入:** resources/calls/bytes/batches/groups/framesを分ける。カウンター名と増加位置・失敗時動作を文書化し、offTargetDrawnの誤用を含め報告を訂正。

### A26 [P1・測定設計] S05/S06の専用受入が不十分
「1 batchに2 quad」は異なるbatchを同じmeshへappendするbase-vertex不具合を十分に踏まない。
S06も旧ordinary sceneの不変だけではcapacity跨ぎを検証できない。
**受入:** 2つの独立batchを同じgroupへmergeし、異なる色・位置を画像で確認。頂点・index双方の容量拡大を跨いで先行geometryを保持する。

### A27 [P2・未検証] 性能向上の根拠がない
startup fixtureのframe interval、JEI時間切れ、GL readback呼出し数は定常CPU/GPU性能の比較とは異なる。
**受入:** 正しい描画が成立後、元Celeritasと同条件でCPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新を比較する。API/Javaの新しさだけで向上を断言しない。

### A28 [P1・配布要件] 能力判定と純nativeの必須条件
現行hybridはVulkan1.2、GL UUID、external FD、GLの照会能力等に依存する。2GPUで通ったことを全Vulkan GPU対応と同一視できない。
**受入:** native pathの本当に必要なAPI/feature/format/surfaceを明示し、不要なinterop要件を外す。GPU名による分岐・mesh shader必須化をしない。未検証vendor/OSを明記する。

### A29 [P1・行動] 根拠なく診断し、試行定数と文書を繰返す
Intel JEIのtickを170→172→160等へ移動し、budget延長後も未到達。GPUだけの原因、launcher corruption、"one observation away"等の診断は証拠より強かった。
一方ログを読んで自分の誤りを訂正した点と、upload/accept計器を追加した点は有効。
**受入:** vanilla優先。各roundは具体実装か、その選択に必要な限定検証に使う。失敗した同一仮説を定数変更だけで反復せず、実ログの原因→最小修正→受入へ進む。図表や言い換えだけで完了にしない。

### A30 [P1・運用] 容量と証拠保全
監査前にrootが満杯でENOSPC、DSH auto continuationがdisarmed。auditorが同一static JARをhardlink化して15.3GiB削減し再開。
その後DSHが古いrunを一括pruneして直近20件へ絞り、画像・ログの一部も失われた。
**受入:** pinned baseline、失敗再現、受入代表runは削除前にhash付きで保存。重複依存・展開・world dataを優先整理。最低空きを事前確認し、active/armed/runningを区別。復旧できた事実と未復旧を正確に報告する。

### A31 [P1・文書] plan/scope/旧24監査に矛盾が蓄積
planには「FBO完了」と「受理撤回」、unmapped=0と約2000未認識、condition3未開始と完了、S02closedとopen等が併存する。
履歴は追記型でも、現状表を履歴の寄せ集めにしてはいけない。
**受入:** 本書を反映した現在の完了条件・優先順をplanに1つ持つ。scopeは実測に限定。旧24件はitemごとに修正済/限定検証済/未解決を再判定し、appendしたclosedを無条件採用しない。

### A32 [P0・追加ソース監査で確定] target filter後に対象外geometryまで全破棄する
追加確認時の `VulkanTerrainGpuBackend.drawPendingImmediate(Long onlyTarget)` はtarget不一致groupをcontinueするが、finallyで `discardImmediateGroups()` を呼び、全groupを削除する。
`recordOffscreenPass` はmain passより先に呼ばれる。off-targetは入口でreturn falseのため受理0でも、作成済みtargetのpassを走らせるとmainのpending groupがdrawされず破棄される。
A10のcopy-back誤認とは別のgeometry消失であり、accept/validationCleanだけでは検出できない。
CPU契約: main group Mとoff-target group Oをqueueに置きOを処理した後、旧処理の残りは[]、正しい残りは[M]。当時の経路ではOが0でもMが消える。
**受入:** targetごとに処理済groupだけretireし、未処理groupを保持。main/off-targetの2つの独立batchで順序・clear・depth・画像を確認。空clear/copyでGL所有FBOを壊さない。
**後続修正:** `1253da063` / `0239b691f` / `1f9f83378` にtarget別retire、空pass抑止、main targetの明示filterを確認。CPU契約チェックが追加された。実GPUの2-target画像受入は未確認。

## 旧24項目の再評価

| 項目 | この監査での扱い |
|---|---|
| S01 | bridge presentのownership/layoutを再開（A18）。native最終経路は別契約 |
| S02 | steady-state改善の実績は維持。startup/texture/presentと無限待機は未解決（A20） |
| S03 | cancellation修正の実績は維持。全branchのbatch対応は追加確認（A09/A11） |
| S04 | state clone修正は維持。stateの実利用不足はA12 |
| S05/S06 | コード修正の実績は維持。専用受入不足（A26） |
| S07 | 未完。state/lighting/color/alphaを再開（A12–A14） |
| S08/S09 | attribute/decode修正の実績は維持。最終画像・照明の証明とは分ける |
| S10 | 順序・clear・targetを再評価。FBO完了主張は撤回（A08/A10） |
| S11 | counter単位とframe境界を再評価（A25） |
| S12/S13 | generation/retire/cacheの改善は維持。新upload/resize/保持画像の整合を再開（A06/A07） |
| S14 | 全形式のpreflight/dropを再開（A08/A09） |
| S15 | 通常teardown実績は維持。失敗時部分生成・残るcache/resource cleanupを確認 |
| S16 | 新patch経路に重大欠陥。再開（A02–A06） |
| S17/S18 | composite意味判定・out-of-date/失敗時資源と画面到達を再開（A11/A18/A19） |
| S19 | 128 byteのサイズは満たしたが変換の意味を破壊。再開（A01） |
| S20/S21 | validation parser/画像delta改善は維持。成功field欠落拒否を追加（A24） |
| S22 | 到達・作用・capture中の状態を確認するfixtureが必要（A21/A22/A26） |
| S23 | sampler/mip正当性を再開（A05/A15） |
| S24 | shader自動build/lockは維持。現在jar・fixture・SPIR-Vのprovenanceと主張を揃える（A23/A31） |

## 維持すべき実績と条件

- AMD/Intelの通常hybrid fixtureは実際にexit 0、validation message 0、PASSEDを記録している。これは有効な局所受入。
- 初期画像供給7/8と、その後の直接image/patch経路は実装されている。回数をunique texture数や正確な更新保証と混同しない。
- 監査用candidate jar内 `org/soutalab/celtium` classはmajor 65が49、major 52が12。Java21より新しいclassはこの集合にない。ゲーム実行はJava25。
- Cleanroom専用、既存Minecraft/Forge公開APIの互換入口、native renderer、汎用GL変換禁止、能力判定、2GPU、影packは本体完成後を維持。
- Sodiumの制限付きコードをコピー/翻訳/cherry-pickしない。FOSS系譜と一般アルゴリズムの独立実装・出典を区別する。

## 独立実行した再比較

監査者がreference/candidate/fixtureをsnapshotし `.celtium-work/audit-2026-10-09-third/input-provenance.json` にhashを保存した。
最初のfixtureは古い空のbuild出力を選んでしまい、クラスがなく両runが起動待ちでtimeoutした。この試行は比較に使わない。
動作中DSHのrunからValidationClient.classを含む実fixtureを確認して取り直した。結果は下の追記に示す。
これは描画比較であり、DSHとの同時実行下の時間を性能benchmarkとして使わない。

## 新goalへの反映

P0正当性修正 → pinned Celeritasとの再比較とvanilla coverage → texture/frame/FBOのnative ownershipとGLなしpresent → reload/resize/失敗復旧・2GPU → major mod → 性能/配布。
既存の末尾指示は文字列として完全一致を確認して保持する。goalの更新とレポートを読んだこと、コード修正・受入完了はそれぞれ別に報告する。

## 監査後の修正・検証状況（2026-10-09 23:20前後 JST）

DSHは本監査と追補を実際に読み、具体修正を行った。以下は監査者が後続ソースとcommitを照合した時点の状態であり、全項目の完了報告ではない。

| 項目 | 確認した変更 | 残る受入・制限 |
|---|---|---|
| A01 | `b84f52b8a` / `854f9961e`: projection×modelViewをCPUで作り、全immediate shaderの4列目再構築を除去。途中で残っていたPOSITION shaderも修正 | 全変換の画像・回転/非一様scale検証は通常fixture成功とは別 |
| A02 | level0を平坦配列として読むよう修正。ARGB→RGBA等のCPU契約チェック追加 | 入力の全mip供給とGPU画像受入は未完 |
| A03/A05 | 全面とpatchを分離し、level layout追跡。patchはlevel0だけを保存して更新し、patch寸法のmip生成を停止 | CPU shadowの外側不変テストはVulkanの保存証明ではない。2つの離れたsprite、全mip、GPU内容の専用受入が残る |
| A06/A07 | patch後hash無効化・hashの成功後確定・metadata更新等を追加 | lifecycle/CPU shadow/世代/resize/reload/descriptorと失敗retryの整合をさらに確認 |
| A08 | `455b85095`: texture/particle descriptor preflightをcopyVertices前に移動 | append以降のallocation/例外は別。GLかVKの一方だけを保証する失敗注入が残る |
| A09 | skip箇所にdrop計数を追加。target不一致はdropに数えないよう後で訂正 | 0は計器が数えた拒否・dropがないという事実。全formatのGPU完了・見た目を証明しない。exception全体とbatch対応が残る |
| A10/A32 | target別queue/retire、空pass抑止、main target filterを追加。copy-backだけが描画証拠という旧主張を撤回 | 修正後copy-backカウンターは記録時にも増え、GPU完了/GL upload成功の証拠ではない。off-target実geometry・画質・clear/load/depth/resizeの受入が残る |
| A21/A23/A31 | spectator効果を本体へ戻し、旧画像数値を最新版一致率から外し、planを現状表へ整理 | scopeの冒頭にも旧い強い主張が残る。認識/受理をGUIや全entityの描画証拠としない。履歴と現状を1表に揃える |

`p28w-a03fix-amd` / `p28x-allshaders-amd` は各 exit 0 / fixture PASSED / validationMessageCount=0 / validationClean=true。
これらはAMDの通常fixtureであり、専用GPU受入・Intel再確認・全vanillaの証明ではない。
`scripts/cpu-contract/TextureContractCheck.java` のpatch不変はCPU配列の契約を確かめる。GPU barrierやatlas mipが正しいことまでは検証しない。

### 元Celeritasとの独立比較の結果

比較の固定入力:

| 入力 | SHA-256 |
|---|---|
| reference Celeritas `989ebf2e` | `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7` |
| 修正前candidate | `0511c7e0102dbe3a09867b7966e63ec2ef9b237faa9fd8a4f36a008a7f99f454` |
| クラスを確認した共通fixture | `70d294ab624720528de1c9d47fe01ff19551e3bfab07f6ffdb56c2e6e4142db1` |

reference-correct-amd / candidate-correct-amdは両方exit 0 / PASSED / validation message 0。
同じRX6400、seed=12345678、renderDistance=8、worldTime=6000、worldTicks=200、
camera=(0.5,7.809486741892088,0.5)、yaw=180、pitch=20、nonPlayerEntities=0、lightmapFlickerPinned=true。
fixture/runnerは一致し、candidateだけexpectVulkan等の成功条件を追加した。

ただし画像にはcandidate固有のchat/command表示とtutorial toastがあり、pixel比較にGUI差が混ざった。
修正前candidateは全体が濃い青で、地形・空・粒子配置にも大きな視覚差がある。
未制御の全面生比較は1280×720、max channel delta>8のpixel=100%、平均absolute channel delta=84.9888/255。
**これは修正前artifactのGUI差込みの数字であり、最新版の一致率・地形だけの差・GPU固有原因を表さない。**
両runがvalidationCleanでも画質一致を保証しないことを示す資料として保存する。
HUD状態を実測して揃え、toast/chatの寄与を除外し、領域ごとの画像と目視を残す。

修正後のp28x artifactもSHA-256 `35053338e5edaf06cb8002525a73f58be10bac51caec5d95e028968a87edf94d` に固定して追加実行した。
Wayland側はlibxkbcommonのnative SIGSEGV（process exit -6）、X11側はWorld.getSkyColorBodyでentityIn=nullのNullPointerException（exit255）でMISSING_REPORT。
どちらもcaptureへ到達せず、最新版の画像一致率はこの監査では**取得できていない**。後者の原因を「GPU違い」「launcher corruption」と断定しない。
crash/console/lockを保全し、通常fixture成功と独立再現の失敗を両方残す。失敗runでvalidationClean=trueでも成功ではない。

### goalと自動継続

goalは同じIDを維持し、revision33→34→35へobjectiveを編集。A32と後続修正・残る受入を追記。
resume操作によりrevision36となったがobjectiveは同じ。round counter / max1000 / phase activeを保持し、新規goalへ置き換えていない。
旧末尾指示は文字列完全一致で保持。SHA-256:
`edfaef9f2dc8c4571304cad20716351931f7cfbd0c53d2be797b06c1f2c70858`

> 絶対に止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。thinkingで✓や✗の文字を使わない。

23:18前後、phase activeだがactivation disarmed / session running=falseになっていた。
公開turn/endの原因は `PI_AI_ERROR`: ClinePassが実際の応答提供元をfireworksと報告し、dsh-clinepassの許可deepseek/strictと不一致のため応答を破棄。
一度resumeしたが次のroundも同じ種類のprovider検証で停止した。goalの内容・完了判定による解除ではない。
dsh-clinepassの同梱READMEではgatewayのonly指定が実行されない場合を説明している。strictを弱めて正常再開したことにはしない。
**goal更新完了とDSH稼働再開は別の事実。現在の停止原因を明示し、未復旧を隠さない。**
