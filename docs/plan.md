# Celtium 現状表 — 2026-10-09 第3回監査反映

本書は **現在の到達点・優先順・受入の唯一の現状表** である。経緯と過去の測定は `docs/history/` にあり、そこにある数値や「closed」は
現在の判定ではない。根拠は `docs/audit-2026-10-09-third.md`（第3回監査、監査開始HEAD `c89bda361`）と、監査後に取得した実測である。
**用語**: 実装済 = コードがあり動く / 局所検証済 = 特定fixtureで確認 / 未検証 = 動く証拠がない / 未実装 = コードがない。
カウンターの増加は描画の証拠ではない。format認識・受理・記録・submit・完了・dropを混同しない。

## 1. 厳密な完成条件の現状

| 条件 | 状態 | 欠落・根拠 |
|---|---|---|
| (a) 全vanilla描画と最終合成・presentがnative Vulkan | **部分（native passは存在、coverageは未検証）** | **nativeで描いているもの**: terrain、即時系の各family（block/item/particle/position/position_tex/tex_color/composite=format 9）、GUI/font/HUD・hand（即時系経由）、particles、sky/cloud/water等のformat-6コンテンツ（`outsideWorldPassAccepted` 数千件）。**未検証のcoverage**: 全block/state、全entity/TESR、破壊/selection overlay、dimension、FBO/postprocess、spectator 3視点。**未実装**: vanilla ShaderGroup、entity outline |
| (b) GL contextを生成せず、GL call/readback/bridge/copyback/GL swap/GL fallbackを必要としない実行 | **未達成** | `-Dceltium.vulkan.withoutGlIdentity` は**identity照会を省略する診断**であり、GL contextなし起動・描画ではない。GLは実際にcontextを作り、bridgeのない描画はGL fallbackで完走し得る。通常frameのGL read（MODELVIEW/CURRENT_COLOR/fog/bound texture/FBO/viewport照会）が残る |
| (c) native swapchain frameが実window/compositor出力へ到達 | **未確認** | markerがGL framebufferに無いことは画面到達の証明ではない。x11grab一方式の失敗から「環境上確認不能」とは断定しない。capture方法の調査を残す |
| (d) AMD/Intelで必要場面と失敗復旧、監査P0/P1を明示受入で閉じる | **部分** | 通常hybrid fixtureはAMD/Intelで exit 0 / validation message 0 / PASSED。**今回追加**: format 2の実geometryをmainと別FBOへ描く受入（AMD 3 run、カウンタ4段一致・画像不一致0）、submit前失敗の注入run（再試行1回・geometry保持）、handle別ライフサイクルの純ロジック4件。**Intel最終artifactは実行中**、submit後失敗・depth依存・画面到達は未実施 |
| (e) mod対応範囲・Java条件・最低VK能力・未検証GPU/OS・ライセンス/性能をdocsと配布物で一致 | **部分** | 本表と `docs/scope-and-limits.md`。jar内 `org/soutalab/celtium` は major 65 が49・major 52 が12で、Java21より新しいclassは無い。ゲーム実行はJava25 |

## 2. 第3回監査 項目別の現在状態

| # | 監査の内容 | 現在 | 根拠・残り |
|---|---|---|---|
| **A01** | model-viewの最初3列のみ送り平行移動が消える | **修正済・受入済** | writerが `projection*modelView` を1行列として送り、**全7即時/UIシェーダ**が `u_ProjectionMatrix * vec4(a_Position,1.0)`。列を再構築するシェーダは**0**。SPIR-Vはビルド時にGLSLから生成（committed .spvなし）のでjarと整合。`immediate_position.vert` の再構築残りも撤去（writerが列をゼロ化したため全頂点が平行移動点へ収束していた）。run: exit 0 / clean / message 0 / PASSED |
| **A02** | `int[][]` を行配列として読む | **修正済・CPU契約受入済**（GPU画像受入は残る） | `data[level][y*levelWidth+x]` の契約に合わせ、level 0 を**平坦**に読む。level別寸法は「書き込みレベルの寸法をpatch寸法として渡す」契約に統一。動的textureの平坦 `int[]` 経路は別契約として分離 |
| **A03** | 部分更新なのに `UNDEFINED` でatlas全体とmipを破棄 | **修正済・CPU契約受入済（GPU受入は残る）** | 全面uploadと部分更新を分離。patchは**レベル別に記録した現在layout**から遷移し、**level 0のみ**の範囲、mip chainを記録しない、patch自身が戻し遷移。**専用受入**: `scripts/cpu-contract/TextureContractCheck.java` が2つの離れたpatch（2x2@(1,2) と 3x1@(5,0)）を当て、**patch外の全byteが不変**・色順ARGB→RGBA・行順・touched=7 を検証（`CELTIUM_CPU_CONTRACT PASS`）。通常fixtureも exit 0 / clean / message 0 / PASSED |
| **A04** | 初回patchだけで全面未初期化のmirrorを作る | **是正側は修正済・全面供給は未解決** | `hasFullContent()` を持ち、全面内容の無いミラーは受理経路がdescriptorを作らず拒否（`partialMirrorRefusals`）。AMD実測では**0件＝このfixtureはpatch先行の順序を作らない**。atlas stitch時の全面データ保持・供給は残る |
| **A05** | patch寸法でmip chainを作り直す | **修正済** | A03の修正でpatchはmip chainを記録しない。sprite単位mipは `MIRROR_GL_MIPS` 経路がGLの各levelをコピーする（全atlas blitを正解とせず、遠景・透明境界での比較は残る） |
| **A06** | 保持画像とpatchの競合、hash skip | **修正済** | patch成功時に `lastPixelHash` を無効化し、patch後の同一内容全面uploadが重複skipされないようにした。CPU shadowとresource generationでの全面管理は残る |
| **A07** | metadata・転送成功の扱い | **修正済** | hashを転送**後**に確定、`imageUploads` は成功時のみ、`Entry.width/height` が画像に追随（寸法を可変に）、skipは `imageUploadsSkipped` として別計数。GL ID再使用の検証は残る |
| **A08** | 受理前失敗でpending meshに頂点が残る | **修正済・受入済（範囲拡大）** | preflightをappend前に置く方針を維持しつつ、`prepareImmediate()` で **pipeline と descriptor を group 生成前に用意**し、format 2/3/4/5/6/7/8/9 と texture にも広げた。`VulkanDynamicMesh` は頂点・indexの容量を両方確保してから書き込む。注入run `sol-cb-final-retry-amd` で `retries=1`・最終カウンタ4/4/4/4を実測 |
| **A09** | 受理後の消失を全形式で数えていない | **修正済** | 描画ループ内の**7つのskip地点すべて**が `postAcceptDroppedGroups` を増やす（以前はformat 5のdescriptor失敗のみ）。実測0は、実装したdrop計器が数えた消失がなかったという事実。全formatのGPU完了・見た目を証明しない。branch別の内訳とbatch ID対応付けは残る |
| **A10** | `offTargetDrawn` は実描画ではない | **修正済・AMD受入済** | 常に `return false` していた旧 offscreen scaffold（空passをclear/copy）を削除し、off-targetの受理済みgroupは **bind切替のHEAD（旧FBOがbind中）で同期windowが描いて書き戻す**。実測 `offTargetAccepted/Recorded/Completed=2/2/2`、`mainWasPending=true`・`secondaryWasPending=true`、別FBOのPNGで赤・緑が正位置・未更新領域の不一致0。copy-back回数を描画証拠にはしない方針を維持 |

| **A11** | format 2（POSITION_TEX_COLOR）は既定でGL | **修正済・AMD受入済（Intel実行中）** | 既定maskを `1,2,3,4,5,6,7,8,9` に。前提として backend 専用 `VulkanCommandBufferState`（handle別ライフサイクル）と `flushImmediateWindow()`（begin→acquire→pass→release→submit→fence→endFrame の同期window）を実装し、`flushPendingBeforeGlDraw` が pass 外で直接記録していた欠陥を除去。受入: `sol-cb-final-amd` exit 0 / clean / msgs 0 / fixture PASSED / passed=true / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0。再現は `sol-cb-base-amd`（70メッセージ）。**Intel**: target受入は `passed=true`（最終artifactのログ行）だが、run verdict は
 1回目 `worldFrames<100` の起動ゲート、2回目は480秒上限で report 未到達。**フレーム数不足が未解決**（window往復コストが最有力） |
| **A12** | state modelとpipelineの不一致 | **未解決** | alpha enable/func、depth func、separate alpha blend、color mask、scissor、texture matrix。pipelineはLEQUAL/ADD等を固定 |
| **A13** | current color/alphaの欠落（group keyにも無い） | **未解決** | POSITIONはRGBのみでalpha=1、POSITION_TEXは白固定。tint/alphaの再現証拠なし |
| **A14** | normal無視のITEM対応は照明対応ではない | **未解決** | 固定lighting・normal変換・shade・fog |
| **A15** | sampler固定値と旧比較による調整 | **未解決** | blur/clamp引数をannounceへ渡していない。filter/wrap/LOD固定 |
| **A16** | 「hot-pathのGL読取り除去済み」は過大 | **訂正** | 通常frameのMODELVIEW/CURRENT_COLOR/fog/bound texture/FBO/viewport照会が残る。診断用readと通常readを分けて数える |
| **A17** | `withoutGlIdentity` 成功はGLなし描画ではない | **訂正済** | GL contextを生成せず全frameをVK資源から描く経路が要る（条件(b)） |
| **A18** | bridge presentのownership/layout契約 | **未解決** | 共有画像のbarrierは `QUEUE_FAMILY_IGNORED`、復帰先 `COLOR_ATTACHMENT_OPTIMAL`。最終native経路はbridge資源を使わない |
| **A19** | native presentの画面到達証拠 | **未確認** | compositor/headless capture等を調査し、markerと連番で実window出力を確認。不可なら具体的不足条件を残す |
| **A20** | wait failureを全面閉鎖したとは言えない | **部分（実機構成を追加）** | `waitForIdle()` は fence timeout/error で **false を返し `gpuStalled` を立てる**（以前は true を返し pending buffer を reset していた）。`settleGpu` は stalled で例外。submit前失敗は再試行1回・資源はqueueへ戻す。**submit成功後失敗／device lost は分岐のみ実装で実機注入は未実施** |
| **A21** | vanilla postprocessを根拠なくscope外にした | **訂正済・未実装** | `EntityRenderer.func_175066_a` はcreeper/spider/enderman視点でShaderGroupをロードし、`func_175069_a(ResourceLocation)` は引数あり。spectator 3視点・entity outlineは**本体機能**として実装対象 |
| **A22** | 比較worldは全vanillaのcoverageではない | **未解決** | registryから自動fixture生成、小場面分割。flat worldとlayout受付数で全制覇にしない |
| **A23** | 古い画像差とlocal attributionの混同 | **訂正済** | 68.81%（旧baseline対地形）と 0.267/255・0.40%（同一Celtiumの描画切替）を最新版全体の一致率として使わない。pinned Celeritas対current Celtiumを同一fixture/GPU/camera/sceneで再取得する |
| **A24** | 比較器が欠落verdictをfail-closedにしない | **未解決** | required fieldの存在・型・成功値を必須化 |
| **A25** | counterの単位が完了主張と違う | **部分** | `imageFilledMirrors` はresource数ではなく呼出し/refresh回数。`outsideWorldPassAccepted` はaccept判定前に増える。単位（resources/calls/batches/groups/frames）を分けて文書化（本表がその記録） |
| **A26** | S05/S06の専用受入が不十分 | **未解決** | 2つの独立batchを同groupへmergeし色・位置を画像確認。容量拡大跨ぎの保持 |
| **A27** | 性能向上の根拠がない | **未測定** | 正しい描画成立後に元Celeritasと同条件でCPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新を比較 |
| **A28** | 能力判定と純nativeの必須条件 | **未解決** | 現行はVulkan1.2・GL UUID・external FD・GL照会能力に依存。2GPU通過を全Vulkan GPU対応と同一視しない |
| **A29** | 根拠なく診断し定数と文書を反復 | **規律として受領** | vanilla優先。各roundは具体実装か意思決定に必要な限定検証。同一仮説を定数変更・budget延長で反復しない |
| **A30** | 容量と証拠保全 | **規律として受領** | pinned baseline・失敗再現・受入代表runをhash付きで保存。`.celtium-work/audit-2026-10-09-third` は監査者の再比較証拠として**削除しない** |
| **A31** | plan/scope/旧監査の矛盾蓄積 | **本表で解消** | 現在の完了条件と優先順を本表1つに持つ。scopeは実測に限定。旧24項目は下の再判定による |
| **A32** | off-target passが他targetのgroupまで破棄し、空targetのclearをGL FBOへ書戻す | **修正済・CPU契約受入済** | 規則を依存フリーの `ImmediateGroupQueue.retireGroupsForTarget` に抽出し、**そのtargetのgroupのみretire**・他は保持（`immediateGroupsKeptForOtherTargets`）。一致groupが0ならpassを開始しない（`offTargetPassesSkippedEmpty`）ので空clearをGL所有FBOへ書かない。copy-backはdraw記録数が増えたtargetでのみ記録する。GPU完了・GLへのupload成功の計器は別に必要。**専用受入**: CPU契約チェックが2独立target×4group＋不正groupで、A退去後は3件保持（B×2＋不正1）・B退去後は不正1件のみ残ることを検証 |

## 3. 旧24項目の再判定（第3回監査による）

| 項目 | 再判定 |
|---|---|
| S01 | bridge presentのownership/layoutを再開（A18）。最終native経路は別契約 |
| S02 | steady-state改善は維持。startup/texture/present・無限待機は未解決（A20） |
| S03 | cancellation修正は維持。全branchのbatch対応を追加確認（A09/A11） |
| S04 | state clone修正は維持。state実利用不足はA12 |
| S05/S06 | コード修正は維持。専用受入不足（A26） |
| S07 | 未完。state/lighting/color/alphaを再開（A12–A14） |
| S08/S09 | attribute/decode修正は維持。最終画像・照明の証明とは分ける |
| S10 | 順序・clear・targetを再評価。FBO完了主張は撤回（A08/A10） |
| S11 | counter単位とframe境界を再評価（A25） |
| S12/S13 | generation/retire/cache改善は維持。新upload/resize/保持画像の整合を再開（A06/A07） |
| S14 | 全形式のpreflight/dropを再開（A08/A09） |
| S15 | 通常teardownは維持。失敗時部分生成・cleanupを確認 |
| S16 | 新patch経路はA02–A06。A02/A03は修正済、A04–A06は未解決 |
| S17/S18 | composite意味判定・out-of-date/失敗時資源・画面到達を再開（A11/A18/A19） |
| S19 | 128 byteは満たしたがA01で変換の意味を壊していた。**A01修正で解消** |
| S20/S21 | validation parser/画像delta改善は維持。成功field欠落拒否を追加（A24） |
| S22 | 到達・作用・capture中の状態を確認するfixtureが必要（A21/A22/A26） |
| S23 | sampler/mip正当性を再開（A05/A15） |
| S24 | shader自動build/lockは維持。jar・fixture・SPIR-Vのprovenanceと主張を揃える（A23/A31） |

## 4. 維持する実績（有効な局所受入）

- AMD RX6400 / Intel UHD630 の通常hybrid fixtureが exit 0 / validation message 0 / fixture PASSED を記録している。これは有効な局所受入である。
- 画像からのtexture供給（初回7/8）と、その後のimage/patch経路は実装されている。**回数をunique texture数や正確な更新保証と混同しない**。
- A01/A02/A03の修正後も両runは green（exit 0 / clean / message 0 / PASSED）。

## 5. 優先順（第3回監査とgoalに対応）

1. **正当性を壊すP0**: A01–A03/A32はコード修正済。専用GPU受入の残件を閉じる。A04–A07（初回全面初期化・patch外保存・sprite単位mip・CPU shadow/generation・hash/size/descriptor）を次に閉じる。
2. **受理からGPU実行までの欠落・二重描画**: A08–A10（preflight/rollback、全formatのdrop集計、FBOのoffer/refused/recorded/drawn/copied-back分離）。FBOをGLへfalseで返したofferをVK描画済みとしない。
3. **pinned Celeritas GL対修正後Celtiumの再比較**（同一fixture/GPU/camera/scene）で地形の暗さ・局所変換・照明・alpha/depth/blend・current RGBA・sampler/mipを直す。巨大mod testを毎回回さず、CPU契約確認＋小GPU fixtureを使う。
4. **texture/frame所有のnative移行**: atlas stitch・動的texture/lightmap・sprite mip/animation・lifecycle・reloadの元データ供給、camera/stateをMinecraft側から供給、GL contextなしwindowでVulkan-owned color/depth/FBOから合成→swapchain→present。
5. **vanilla coverage**: 全block/state・item/model・entity/TESR・hand・GUI/font/HUD・粒子・sky/cloud/weather・水/溶岩/火・半透明と並び替え・破壊/selection overlay・dimension・FBO/postprocess・spectator 3視点。registryから自動fixture生成。
6. **reload/resize/world switch・失敗復旧**を両GPUで。timeout後に使用中command bufferをreset/re-record/destroyしない。
7. **vanilla本体成立後にmajor mod互換**（JEI先行収穫は維持、Intel JEIのbudget調整は主作業にしない）。影mod/shader packは本体のshaderless完成後。
8. **性能・配布**: 元Celeritasと同条件の定常CPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新。Sodium上流との差分は既実装・適用条件・ライセンス・採否を記録し、独立改善のみ採用。

## 6. CPU契約チェック（GPU非依存の受入）

`scripts/cpu-contract/TextureContractCheck.java` はデバイスもゲームも使わず、純ロジックの契約を検証する。
実行: `javac -cp <celtium-vulkan classes>:<lwjgl jars> -d /tmp/contract scripts/cpu-contract/TextureContractCheck.java`
→ `java -cp <celtium-vulkan classes>:/tmp/contract:<lwjgl jars> TextureContractCheck`。合格時は `CELTIUM_CPU_CONTRACT PASS`、失敗時は非0で終了する。

| 検証 | 内容 |
|---|---|
| A02 平坦texel契約 | `int[mipLevel][y*width+x]` として読み、行配列として読んだ場合と異なること・ARGB→RGBAの色順・行順 |
| A03 patch外保存 | 2つの離れたpatchを順に当て、**patch外の全byteが不変**・touched texel数・各patchの位置 |
| A32 target別retire | 2独立target×4group＋不正groupで、片方の退去後も他方が保持されること |

**限界**: Vulkanのbarrier・layout・実画像内容は検証しない（validation-cleanのrunが担う）。この検査はGPU受入の代わりではない。

## 7. 測定の規律（カウンターの意味）

| 名前 | 意味 | 完了の証拠になるか |
|---|---|---|
| `offTargetOffered` | off-targetへ**提示**したbatch（target生成前） | ならない |
| `offTargetAccepted` | 受理位置で数えたbatch（現状は0: 経路違いの計器） | 単独ではならない |
| `offTargetCopiedBack` | draw記録数が増えたtargetにcopyを記録した回数（A32修正後） | GPU完了・GL upload成功・画像の証拠には単独でならない。修正前の1957は空targetのclear/copyも含む |
| `imageFilledMirrors` | announced image/patchで**実際にピクセルが動いた回数**（修正後） | 単独ではならない（resource数ではない）。実測: 実転送113 + skip 3448 = 旧3629は約30倍の過大表示だった |
| `imageUploadsSkipped` | 内容が同一で転送しなかった回数（修正後） | ならない |
| `outsideWorldPassAccepted` | accept判定**前**に増える | ならない |
| `offTargetPassesSkippedEmpty` | 一致groupが0でpassを開始しなかった回数（A32修正後） | ならない（空clearを書かないことの証拠） |
| `immediateGroupsKeptForOtherTargets` | 別targetのため保持したgroup数（A32修正後） | ならない（保持の証拠） |
| `acceptedByFormat` | format別の受理数（`2:0` = format 2は既定でGL） | 単独ではならない |

## 7. コマンドバッファ受入（Sol集中修正・2026-10-10）

| 種別 | 条件 | 結果 |
|---|---|---|
| 再現（修正前jar・SHA固定） | `.celtium-work/sol-command-buffer/base-artifacts/`＋`sol-cb-base-amd` | exit 1 / validationClean false / **70メッセージ** / fixture PASSED（70はVUID抑制上限） |
| 受入（AMD通常） | `sol-cb-final-amd` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / delta accepted=recorded=submitted=completed=4 / pending0 / invalid0 / off-target 2,2,2 / 矩形・未更新領域の不一致0 |
| 受入（AMD・submit前失敗注入） | `sol-cb-final-retry-amd` | 同上＋`retries=1`（fixture内で実際に再試行） |
| 受入（AMD・初回修正版） | `sol-cb-fixed2-amd` | 同上（pendingImmediateIndices修正後） |
| 純ロジック | `vulkan:test` | `VulkanCommandBufferStateTest` 4件 pass |
| 通常fixture回帰（format 2 既定受理） | `p31a-format2-default-amd` | exit 0 / clean / msgs 0 / fixture PASSED / worldFrames 228 / `acceptedByFormat=1:0,2:217,3:0,4:0,5:214,6:1394,7:1136,8:0,9:0` / **recorded=submitted=completed=2961** / invalidImmediateRecords 0 / retries 0 |
| 受入（Intel通常） | `sol-cb-clean-intel` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / 4/4/4/4 / off 2/2/2 / 不一致0 / worldFrames 155 |
| 受入（Intel・submit前失敗注入） | `sol-cb-clean-intel-retry` | 同上＋`retries=1`（fixture内で再試行） |
| 通常fixture（Intel・format 2既定受理） | `p31d-intel-clean` | exit 0 / clean / msgs 0 / PASSED / **worldFrames 154**（変更前は114・129）、即時2155件が記録=submit=完了、invalid 0 |
| window数削減（1回目） | `sol-cb-windowcut-amd` / `p31c-windowcut-amd` | viewport変更のflushを削除し、テクスチャ告知は**キュー内groupが参照する場合のみ**flush。`orderPreservingFlushes` **633→388**（228→218フレームで約2.8→1.8/フレーム）、target受入 exit 0・通常fixture PASSED・記録=submit=完了=2829・invalid 0 |
| 訂正（Intelの480秒上限） | `sol-cb-final-intel2` / `p31b-intel-normal` | どちらも480秒上限で report 未到達だったが、**同時にAMD runを走らせていた競合が原因**だった。他を止めた単独runは 24〜27秒で完走し PASSED。**Intelは退行しておらず、むしろ 114/129→154 と改善**。測定は常に単独で行う |
| 受入（clear順序・描画→clear） | `sol-cb-clearorder-amd` / `sol-cb-clearorder-intel` | clear前に受理済みbatchが1件pending→clear境界で accepted=recorded=submitted=completed が各+1・pending 0、clear後の画像は**全ピクセルがclear色**（blue矩形も消滅・不一致0）。「描画→clear」の順序を実geometryで確認 |
| 受入（depth順序・近傍が遠方を隠す） | `sol-cb-depth5-amd` / `sol-cb-depth-intel` | 同じ矩形に近傍batch→遠方batchの順で描き、**近傍の色だけが残る**（中心=青、rectangle不一致0、`farLeakedPixels` 0）・4段カウンタ+2一致・pending 0。D24→D32→D24の往復とGL準拠のdepth順序を実geometryで確認 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 13 runのexit/clean/msgs/fixture/passed/delta/clearOrder/depthOrder/worldFramesとPNG 5枚のハッシュを固定 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 各runのexit/clean/msgs/fixture/passed/deltaとPNGハッシュ |

実行コマンド: `bash scripts/run-command-buffer-targets.sh amd|intel <new-run-name> [failImmediateSubmits]`
（Python/fixture/verdict のどれかが落ちれば非0で終了し、`commandBufferTargets.passed` と3枚のPNGを必須にする）。

## 7.5 画質比較（優先順位3・2026-10-10 現行artifact）

`scripts/run-gl-comparison.sh amd r418` で pinned upstream Celeritas (GL) と現行Celtiumを**同一flag・同一fixture・GPU**で
順に実行し、`scripts/compare_validation.py` で比較した（p32-glref-r418 / p32-celtium-r418）。

| 項目 | 結果 |
|---|---|
| run verdict | 両方 exit 0 / validationClean true / messages 0 / fixture PASSED |
| framebuffer・camera・pass数 | 一致（solid/cutout_mipped/translucent 各62） |
| 画像 | **645,651ピクセル差（70.06%）・最大チャンネル差209** |
| 領域別 | **空 ratio 1.000（完全一致）**、地平線 ~1.0、**地形 0.595 / 0.580 / 0.636**（赤/緑/青） |

症状は「地形が一様に約0.6倍暗い」。切り分けの結果:

- **lightmap mirrorは正しい**: 同一run内で GLの実lightmap（`gl-lightmap-live.png`）と mirror（`gl-texture-12-16x16.png`）を比較。
  capture時点のGLは sky15/block0 = (250,250,250)（正午）、mirrorの**最後のアップロード**も (250,250,250) で一致。
  最初のアップロードは (155,155,187) で、これは**実行初期（朝）の値**だった（mirrorのdumpは最初の1回だけ書く仕様のため、
  以前の「mirrorがstale」という読みは誤りだった。`-Dceltium.vulkan.dumpTexturesEvery=true` を追加して確定させた）。
- **面シェーディング表は上流と完全一致**: `common/.../model/light/smooth/AoNeighborInfo.java` は pinned checkout と diff なし
  （0.6/0.8/0.5/1.0）。
- **shaderは `v_Color = a_Color * lightColor` のみ**（`terrain_solid.vert` 227行）。追加の係数は無い。
- したがって **0.6 ≈ 朝のlightmap 0.61 × 正午の0.98** となり、**メッシュ構築時にlightmap値が頂点色 `a_Color` へ
  焼き込まれ、以後chunkが再構築されないため朝の明るさのまま固定されている**という説明が測定値と一致する。

**「焼き込み」仮説は実験で反証された**（`p32-reload-r418`, `-Dceltium.validation.reloadAtTick=120`）:
正午固定後に `renderGlobal.loadRenderers()` で全chunkメッシュを再構築しても地形の帯は
**79.6 / 85.5 / 75.4 で完全不変**（reload無しと同一）。したがって頂点色へのlightmap焼き込みではない。

追加の測定:

| 指標 | 値 |
|---|---|
| 回帰 fit（地形帯, 緑） | `y ≈ 0.42x + 20`、残差 sd ≈ 19-20（単純な乗算ではない） |
| 高周波エネルギー（地形帯, 緑） | 参照 7.1 / 候補 **5.5**（候補が約22%滑らか＝mip/補間の差） |

**このラウンドで反証した候補**（すべて実測つき）:

| 候補 | 反証 |
|---|---|
| lightmap mirrorがstale | mirrorの最終アップロード = GLの実lightmap = (250,250,250)（`-Dceltium.vulkan.dumpTexturesEvery=true`） |
| 頂点色へのlightmap焼き込み | `reloadAtTick=120` で全chunk再構築しても帯は 79.6/85.5/75.4 で不変 |
| 生成mip鎖が暗い | `-Dceltium.vulkan.mirrorGlMips=true` でも近傍帯は同一・全画面diffも不変 |
| AO/面シェーディング表 | `AoNeighborInfo.java` は pinned checkout と diff なし |
| fogが暗い | fog色は明るい青灰 (0.552,0.640,0.789)、range 96-128ブロックで近傍帯に効かない |
| 地形が未描画(void) | 暗部は778色のテクスチャ（同一座標で参照と内容が対応） |
| atlas mirrorが暗い | GLの実atlas（fixtureが `gl-atlas-live.png` をcapture時に出力）とmirrorを比較: **平均が完全一致** (82.6,76.7,63.8)、差は56/65536テクセル（アニメsprite分）のみ |
| lightmap除数 /256 vs /16 | 過去roundで修正済み。shaderコメントの実測でも「サンプルを1.0に固定して画素が変わらない」＝lightmapは支配要因ではない |

**残る手がかり**: 同一座標での比が **0.42〜0.65 と面ごとに変動**する（単純な乗算ではない）。
面シェーディング係数の適用が参照と異なることを示す。

**頂点データの実測（`-Dceltium.vulkan.vertexColourSamples=N` を追加）**: 地形はvanilla 32バイトlayout（pos float3 @0、色 @12、UV float2 @16、light short2 @24、法線 @28）で、
- **alpha は全頂点 255**（shaderの `rgb *= alpha` は無効）✅
- 頂点色は明るい緑（solid平均 **136,158,108**、先頭 0xff59bd91）で、参照の暗さを説明しない ✅
- UV は sprite 座標として正常（例 0.125, 0.469 = 512atlasの 64,240）✅
- light short は `light×16` の byte（例 0xE0=224=sky14×16）。`/16`（現行）と `/256`（上流）は**同じテクセルに解決**するため、除数の違いは結果に影響しない ✅
- **`textureLod(...,0.0)` でbiasを無効化しても画像は不変**（mid 81.0→83.4、near 同一）→ mip/LODは否定 ✅
  （この実験が本当に反映されたかは、jar内SPIR-Vが診断ソースの手動コンパイルと**バイト一致**することで確認済み）
- 参照がlightmapを適用していることも確認（`CELERITAS_NO_LIGHTMAP` は `pass.hasNoLightmap()` の時だけ定義＝SOLIDでは未定義）✅
- **ビルド健全性の確認**: jar内 `terrain_solid.frag.spv` の SHA-256 が現在のソースの手動コンパイルと一致（`bc1e741d…`）＝shader編集は正しく反映される
- 頂点色(0.53,0.62,0.42)×lightmap(0.98)=(133,155,106) が**参照の実測(136.8,146.5,117.2)とほぼ一致**する一方、候補はその0.6倍。かつ候補の画素値は参照の輝度と単調対応しない（ref133→87、ref103→43）＝**テクスチャ内容そのものが違う**
- Atlas level0 の内容は GL の実atlasと平均完全一致 ✅。dumpに**オブジェクト同一性**を付けて確認した結果、GL texture 9 の mirror は**1つだけ**（obj29960629）で、それも GL の実atlasと一致（差35テクセル・最大5）✅。つまり terrain が bind する atlas の内容は正しい

**比較手法そのものの検証（重要）**: 同一ビルド・同一flagの2 runのスクリーンショットを比べると
**24.8%のピクセルが異なる**（最大チャンネル差180。別の組では1.7%・1.4%）。参照との70%差はこの
**run間ノンデターミニズム**を含むため、ピクセル一致率は受入指標として使えない。
一方で**帯平均は再現する**（候補の複数runが 79.6/85.5/75.4 で一致、参照は 125.0/135.4/117.6）ので、
「地形が約0.6倍暗い」は実在する差である。以後は**領域統計**で判定し、ピクセル一致は
決定性を別途示した場合だけ使う。

**因子の定量（shader診断・jar内SPIR-V一致を確認済み）**:

| 構成 | 地形帯 mid | near |
|---|---|---|
| 参照 (upstream GL) | 136.8, 146.5, 117.2 | 125.0, 135.4, 117.6 |
| 現行（色×alpha乗算あり） | 81.0, 84.5, 74.2 | 79.6, 85.5, 75.4 |
| **頂点色乗算を無効化** | **210.1, 209.8, 209.0** | **215.7, 216.2, 220.5** |
| **alpha乗算を無効化** | 80.8, 84.4, 74.2 | 79.6, 85.5, 75.4 |

- 「texture×lightmap」= 約212（ほぼ無彩色）。参照はその**0.61倍**、現行は**0.38倍**＝**差は頂点色の項だけ**で、現行は参照の0.62倍。
- **alpha乗算は無効**（400セクションすべて alpha=255、無効化しても不変）✅ → AOの二重適用ではない。
- 既存の `-Dceltium.vulkan.lightDiagnostics=true` は全書き込みの平均色を出すが、**ラベルが実際のチャンネル順と逆**（`r=`は実際にはA、`a=`は実際にはR）。集計は全パス・全フレーム混在なので地形の判断には使えない。**pass別に集計する拡張が必要**。
- **GL地形パスは製品構成ではない**: `-Dceltium.vulkanTerrain=false`（またはVulkan bindings無し）では
  `IllegalStateException: Celtium requires a Vulkan terrain backend, but none could be created: ...` で**明示的に停止**する
  （旧run scriptコメントの「GLへフォールバックしてそう言う」は古い）。以降のplayer null NPEはこの停止の二次症状。
  したがって同一buildでのGL/Vulkan A/Bは**この構成では取れない**。

**pass別集計（実装・実測済み）**: `-Dceltium.vulkan.lightDiagnostics=true` を**pass別**に集計するよう拡張し、
同時に**ラベルの誤りを修正**（intは `0xAABBGGRR` なので little-endian のバイト順は R,G,B,A＝`VK_FORMAT_R8G8B8A8_UNORM` と一致）。
実測: **pass1（solid）= (162,171,150,255)**、pass3（cutout_mipped）= (63,90,25,254)。
- solid の色は明るく、`212 × 0.635 ≈ 137` で**参照(125-137)とほぼ一致**する。
- 一方、現行の描画は 79.6（有効な乗算は0.369）。つまり**詰めた色(0.635)が描画に反映されていない**。
- shader内の他項はすべて無効化して確認済み: `rgb *= m_color.a`（alpha=255で不変）、
  `m_color += v_RdhFactor * correctionWeight`（無効化しても 81.1 vs 81.0 で不変）。
- **注意**: 帯平均は可視geometry（solid/cutout/translucentの混在、面の向き）に強く依存するため、
  「詰めた色」と「描画値」を帯平均で直接比べるのは不適切。**制御された単一quad実験が必要**。

**矛盾の確定（このターンの核心）**: 3点を実測で突き合わせた結果、**矛盾**が確定した。

| 層 | 実測値 | 状態 |
|---|---|---|
| mesher → バッファ | `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)`（新規stdout `CELTIUM_GPU_VERTEX`・既存readback経路） | **正しい**（緑のtint） |
| pipeline layout（実行時print） | `stride=32 module=terrain_solid_vanilla.vert colorOffset=12 colorFormat=0x25(R8G8B8A8_UNORM)` | **正しい** |
| fragmentが受ける `v_Color` | 地形帯で**ちょうど2値** `(26,26,26)` と `(255,255,255)`（95%/5%） | **2値＝異常** |

- 検証手順: (a) `fragColor = m_color.rgb`、(b) `fragColor = v_Color.rgb`（Rdh項の加算も排除）、
  (c) `v_Color = a_Color`（lightmap乗算なし）の3通りすべてで同じ2値。各段で**jar内SPIR-Vが診断ソースの
  手動コンパイルとバイト一致**することを確認済み。
- つまり損失は **頂点バッファと varying の間**（入力アセンブリ／属性記述／drawのvertexOffset）にある。

**このターンの追加実測（矛盾の掘り下げ）**:

| 計器 | 結果 |
|---|---|
| fragmentで**定数色**を出力 | 地形帯の**100%**がその定数＝terrain shaderが帯全体を描いている（診断の前提が正しい） |
| `v_Color = vec4(a_TexCoord, 0, 1)` | **81色の滑らかな変化**＝**varying経路自体は正常** |
| pipelineへ**提出済み**の入力状態（実行時print） | `stride=32 bindingStride=32 binding=0 attr1(location=1,binding=0,format=0x25=R8G8B8A8_UNORM,offset=12)`、blendは solid/cutout=false・translucent=true |
| 可視sectionの**先頭頂点32バイト**（upload時に採取） | `0000803f 0000c040 0000803f 2b3e11ff 0c02083f 1904d03e 03fc00e8 00000000` → pos(1,6,1)、**色=(43,62,17,255)の暗い緑**、uv(0.53,0.41)、light、rdh=0 |
| GPU保持値（readback） | `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)`＝正しい |

つまり **バッファのバイト・layout・提出済み入力状態・varying経路のすべてが正しい**のに、
`a_Color` だけが帯全体で2値（26,26,26)/(255,255,255) になる。次は
**属性を `VK_FORMAT_R8G8B8A8_UINT` として読み、生の整数をそのまま出力**して、
shaderが実際に受け取るバイトを正規化の曖昧さなしに確定させる。

**属性offsetプローブ（このターンの決定的な測定）**: `a_Color` の読み出しoffsetをプロパティで振り、
「offsetが効いているか」と「その場所のデータは何か」を分離した（地形帯の色数で判定）。

| offset | 色数 | 上位の値 | 解釈 |
|---|---|---|---|
| 0（位置float） | 225 | (0,0,63/80/78) | 位置のバイト ✅ |
| 8（compactの色offset） | 116 | (0,0,77/93/89) | 位置のバイト |
| **12（宣言された色offset）** | **2** | **(26,26,26) / (255,255,255)** | **描画頂点の色バイトが2値** |
| 16（UV float） | 457 | (50,34,162) 等 | UVのバイト ✅ |
| 24（light uint） | 3 | (1,28,0) / (1,252,0) | lightのバイト ✅（material=1の頂点） |

- **offsetは正しく効いている**（0/16/24は layout どおりのデータを返す）。
- しかし **offset 12 のバイトは可視頂点で2値**しかない。一方 offset 24 の light バイトは正常に変化する。
- 私が採取した「section #252 の先頭頂点」は material=3・色(43,62,17,255) だったが、可視頂点は material=1 で
  **別のsection**だった（`sectionFirstColor` は section 索引で pass 非依存に上書きされるため、以前の
  「可視sectionの色」という読みは**誤り**。ここに訂正する）。
- 既存readback（`baseVertex×stride` の位置をGPUから読み戻す）は最初のdrawについて
  `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)` を返しており、**arena上のその頂点は正しい色**を持つ。
- readbackをdraw単位（最大8）に拡張したが、1フレーム内で複数slotを読む経路がまだ揃わず1件のみ。
  計器は `-Dceltium.vulkan.vertexDiagnostics=true` にゲートし、通常runでは一切走らない。

**原因判明（このターンの核心）**: mesh builderが実際に書いた色を**section別ヒストグラム**で出したところ、
決定的な事実が出た（`-Dceltium.vulkan.vertexColourSamples=N` の出力に `distinctColours` と `top=` を追加）。

| pass | section 252 の頂点色（上位） |
|---|---|
| solid | **0xff59bd91**（明るい緑）x1024、0xff808080 x1024 |
| cutout_mipped | 0xff113e2b / 0xff175239 / 0xff18563c / 0xff2fab77（緑系4種） |
| **translucent** | **0xff1a1a1a（=26,26,26 の暗灰）x1024、0xffffffff（白）x2048** |

- 診断画像で観測された2値 **(26,26,26) と (255,255,255) は translucent pass の頂点色そのもの**だった。
- translucent pass の alpha は **255（完全不透明）**。つまり**可視地面の上に不透明な暗灰色が描かれている**。
- 一方 solid pass の色 0x59bd91 から期待される値は `0.83(texture×lightmap) × 0.35 × 255 ≈ 74` で、
  通常描画の実測 **79.6 とほぼ一致**する＝**solid pass は正常**。
- GPU readback（draw別32slot）でも arena の頂点色は `0xff59bd91`（solid）/`0xff113e2b`（cutout）/
  `0xffffffff`（translucent, section 0）で、**arenaは正しい**。壊れているのは shader ではなく
  **translucent pass に載っているジオメトリとその色**。

**【訂正】前ラウンドの「translucent pass が不透明な暗灰色で地面を塗っている」は誤り**。
UV (0.875,0.28125) のatlas spriteを確認したところ **alpha 197 の青い半透明テクスチャ（水）** だった。
診断shaderが `alpha=1.0` を出力していたため、本来ブレンドされる水が不透明に描かれ地面を覆っていた
（診断アーティファクト）。translucent pass の中身は**正当な水**である。

**面の明るさ別の適用色（通常run ÷ 色乗算無効run で算出、参照側は ref ÷ 同run）**:

| 対象 | 候補の適用色 | 参照相当 | 比 |
|---|---|---|---|
| 明るい面（上面） | (0.405, 0.419, 0.385) | (0.703, 0.733, 0.661) | **0.57** |
| 中間 | (0.341, 0.366, 0.310) | (0.562, 0.613, 0.503) | **0.61** |
| 暗い面（側面） | (0.301, 0.309, 0.288) | (0.485, 0.502, 0.464) | **0.62** |

- 草地だけ (n=101,902): 参照 (132.4,145.3,114.6) / 候補 (84.1,89.9,75.8) = 比 **0.62-0.66**
- 水面だけ (n=8,621): 比 **0.56-0.58**
- **比が面の明るさに依らず一様（0.57〜0.62）** ＝ 面シェーディング差ではなく**一様な係数**である。
- したがって「どの一様係数か」に候補を絞れる: 頂点色の合成（tint/ shade の掛け方）、
  テクスチャサンプル、lightmap のいずれか。ただし **`VanillaLikeChunkVertex` はCeltium固有**で
  pinned upstream には存在しないため、**上流はこの色の参照にならない**（上流はcompact形式）。
  次は「vanilla GLの同じ面が出す色」と比べる必要がある。

**vanilla基準の測定（このターンの決定的evidence）**: fixtureに `-Dceltium.validation.vanillaReference=true` を追加し、
**terrain modを入れずに同じfixtureで同じframeを撮影**した（Celeritasが無いため通常の判定はFAILEDになるが、
`celtium-validation.png` は保存される）。

| レンダラ | mid帯 | near帯 |
|---|---|---|
| **vanilla Minecraft GL（真値）** | **(146.6, 157.1, 132.3)** | **(139.8, 151.4, 131.4)** |
| upstream Celeritas GL | (136.8, 146.5, 117.2) | (125.0, 135.4, 117.6) |
| Celtium Vulkan | (81.0, 84.5, 74.2) | (79.6, 85.5, 75.4) |

- upstream は vanilla の約93%でほぼ正しい ✅。**Celtium は vanilla の約0.56倍**で、**参照側の問題ではなく我々の欠陥**と確定。
- 「色乗算無効run」の band 215.7 は vanilla近傍の最終値139.8と比べて明るすぎ、vanillaの実効色 =
  139.8/215.7 = **0.648 ≈ solid passの詰めた色0.635** ✅ ＝ **mesherの色自体は正しい**。
- 一方、我々の実効色は 0.369。`0.6(側面shade) × 0.6 = 0.36` が実測と一致し、比が面の明るさに依らず
  一様（0.57〜0.62）である事実とも合う。**全faceに一様な≈0.6の余分な係数（AO/brightness項）が
  mesher側で掛かっている**可能性が高い。

**lightmap サンプルの欠陥を発見・修正（このターン）**: vertex shader で `v_Color = lightColor` を出力して
**shaderが実際にサンプルしている値**を測ったところ、地形帯で **(178,178,178)=0.698**（95%）と (223,223,223)=0.875（5%）
だった。lightmapの実データは row13=160 / row14=197 / row15=250 なので、**178.5 = row13,14 の平均**、
**223.5 = row14,15 の平均** ＝ **UVがテクセル境界に落ちている**（中心ではなく）ことが判明。

- terrain shader は Soduim 上流の式 `clamp(uv / 256.0, …)` をそのまま使っていた（256×256 lightmap 用）。
  Minecraft 1.12.2 の lightmap は 16×16 で、頂点の値は「レベル×16」なので正しい中心は `(uv + 8) / 256`。
- **同じプロジェクトの immediate shader は既に正しい式**（`(vec2(a_LightCoord) + 8.0) / 256.0`）を使っており、
  terrain 側だけが半テクセルを欠いていた。
- 修正: `terrain_solid.vert` と `terrain_solid_vanilla.vert` の両方を `(uv + 8) / 256` に統一。

| レンダラ | mid帯 | near帯 |
|---|---|---|
| vanilla Minecraft GL | (146.6, 157.1, 132.3) | (139.8, 151.4, 131.4) |
| upstream Celeritas GL | (136.8, 146.5, 117.2) | (125.0, 135.4, 117.6) |
| celtium 修正前 | (81.0, 84.5, 74.2) | (79.6, 85.5, 75.4) |
| **celtium 修正後** | **(88.9, 93.3, 82.7)** | **(88.9, 95.6, 84.1)** |

効果は **+10〜12%**（vanilla比 0.553 → 0.606）で、**実在する修正だが残差はまだ大きい**。
run verdict は exit 0 / validationClean / fixture PASSED。

**texure UVとshadeの検証（このターン・どちらも否定）**:

| 実験 | 結果 |
|---|---|
| texture UVを半テクセルずらす（`v_TexCoord + 0.5/512`） | mid 88.9 → 86.6、near 88.9 → 88.8、HF 6.2 → 6.5 で**ほぼ不変** → UV位置は原因ではない |
| `-Dceltium.terrain.noShade=true`（diffuse項を1.0固定） | **完全に同一**（88.9/93.3/82.7 が不変）→ `VintageDiffuseProvider` は**可視地形の色に寄与していない**（shadeはquad色に焼き込まれている）＝shade二重適用説は否定 |

**残差の定量（lightmap修正後）**: vanilla 146.6 / upstream 136.8 / celtium 88.9（mid帯）。
- 我々の「色乗算無効run」= 215.7（＝**texture単体**。lightmapは v_Color 経由なので色乗算を切ると一緒に切れる）
- vanilla 139.8 = tex × colour × lm とすると、tex ≈ 216（我々と一致 ✅）、lm は修正後は同一 ✅
- よって **我々の頂点色は vanilla の一様な約0.58〜0.62倍**（面の明るさ別 0.57 / 0.61 / 0.62）
- **欠陥は「色の合成」（quad色）にあり**、texture・lightmap・diffuse provider はいずれも除外済み

**色の出所を確定（このターン・重要な訂正）**:

1. pipeline経路の色合成は `VintageBlockRenderer:215` の
   `ChunkColorWriter.EMBEDDIUM.writeColor(tint, light.br)` で、**EMBEDDIUM は brightness を RGB に掛け算**する
   （`SEPARATE_AO` は alpha に入れる）。我々の実測 alpha=255 はこれと整合する。
2. しかし **その経路は実際には使われていない**: `light.br` のヒストグラムを仕込んで1 run回したが
   **出力が1行も出なかった**（＝`recordBrightness` が一度も呼ばれない）。
3. 実際の経路は `VintageChunkBuildContext.copyBlockData` で、**バニラの `BufferBuilder` の頂点データを
   そのままコピー**している（`vertex.color = memGetInt(ptr + 12)`、`vertex.light = memGetInt(ptr + 24)`、無加工）。
   → **我々の頂点色は vanilla の頂点色そのもの**であり、**色データは0.6倍差の原因になり得ない**（前ラウンドの
   「色の合成が原因」という見立ては誤りだったので訂正する）。
4. lightmap修正（`(uv+8)/256`）は MC の慣習 `level/16 + 0.5/16` と**一致**する正しい式であることも確認した。
5. Minecraft 1.12.2 の地形描画は**固定機能パイプライン**（2テクスチャユニット×`gl_Color`、shaderファイル無し）で、
   最終色 = tex × lightmap × colour。式の形は我々の shader と同じ。

**次の一手（具体）**: 残差0.6は「データ」ではなく「適用」側にある。**較正された単一quad実験**を行う:
既知のテクスチャと既知の色を持つ1枚のquadを terrain と同じ pipeline/format で描き、期待値
`texel × colour × lightmap` と実測画素を厳密に突き合わせる。これで 0.6 がどの段で落ちるかを1回で確定できる。
上面で brightness×AO が 1.0×1.0 になっているか（余分な0.6が無いか）を確かめる。
（旧メモ:
上面の色が「tint × shade(1.0)」になっているか（＝余分な係数が掛かっていないか）を確かめる。
(a) そのジオメトリが何のブロック/面か（UVと位置から）、(b) なぜ alpha=255 の不透明色なのか、
(c) pinned upstream の mesher と pass 分類・色合成のどこが違うのか、を順に確認する。
（旧メモ・切り分け:
色が2値になる頂点の**arena上の実バイト**を確定させる。そこで初めて
「uploadが壊れている」か「shader側の読みが壊れている」かが決まる。

（旧メモ・切り分け手順:
1. pipeline作成時に**5属性すべての (location, format, offset)** と stride をprintして、宣言と実データの対応を確認。
2. draw時の `vertexOffset` / `firstIndex` / 使用pipeline をprintし、読んでいる頂点が想定のものかを確認
   （誤ったvertexOffsetなら「別の頂点」を読み、色が2値に見え得る）。
3. vertex shaderで `v_Color = vec4(a_Color.r, a_Color.g, 0.0, 1.0)` のように**成分を固定**して、
   どの成分が生きているかを1回で見る。

（元の手順: fixtureに、terrain経路と**同じpipeline/同じformat**で
「既知の1枚のquad（既知のテクスチャ・既知の頂点色）」を描いて**厳密な画素値を読み戻す**小実験を足す。
期待値は `texel × colour × lightmap` で計算できるので、どこで0.635→0.369の損失が出るかを1回で確定できる。
（`lightDiagnostics` の pass別集計は恒久的な計器として残す）
mesh builderが実際に詰めた色の平均を出す。それを「無効化runの212 × 詰めた色」と突き合わせ、
描画結果（79.6/85.5/75.4）と一致するかを確認する。一致しなければ損失はshader内、一致すれば
詰めた色自体が参照の0.62倍である（＝mesherの色合成か、VANILLA_LIKE と COMPACT の選択差）。
`CeleritasWorldRenderer` の format 選択（VANILLA_LIKE/COMPACT）は上流checkoutに存在しない
**Celtium側の変更**である点も確認済み。terrain が実際に bind する `this.atlasTexture` の内容を capture 時に dump し、GLの実atlasと比べる（registry mirror との比較だけでは同一性を証明できない）。
そのうえで、同じbuildで **GL terrain にフォールバックした frame** と比べる（参照に依存せず、Vulkan地形の当否を判定できる。初回試行は起動時の `getSkyColorBody` NPE で落ちたため、その原因も見る）。（fragment shaderが `rgb *= a_Color.rgb` と
`rgb *= a_Color.a` を行う。round 267の実測: この乗算ありで地形帯98・参照131、乗算を外すと215）。
Vulkan側の地形頂点バッファから数点をdecodeして `a_Color` / `a_LightCoord` の実値を出し、
解析的な期待値（tint、面shade、AO=1.0、light 15/0）と比べる。`a_Color` が期待の約0.6なら、
mesh builderの色合成か頂点format（`VK_FORMAT_R8G8B8A8_UNORM` のoffset/解釈）が原因と確定する。

## 8. 第3回監査の最終追補

32項目と修正後の境界は `docs/audit-2026-10-09-third.md` の「監査後の修正・検証状況」に集約する。
元Celeritasとの修正前固定比較はvalidationが両方cleanでも大きな画像差あり。GUI差が混ざるため最新版一致率として採用しない。
修正後の独立runはcapture前に終了し、一致率未取得。成功fixtureと独立再現失敗の両方を残す。
DSHのgoalは同じIDでobjectiveを更新、末尾指示を完全一致で維持。23:18前後にClinePassのprovider検証（許可deepseek、応答fireworks、strict）で自動継続がdisarmedになり、resume後も停止した。goal完了による停止ではない。
