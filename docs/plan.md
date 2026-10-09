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

## 7.5 画質・失敗経路の現状（詳細な経緯は `docs/history/quality-and-failure-investigation-2026-10.md`）

| 項目 | 現在の結論 | 根拠（現行artifact） |
|---|---|---|
| 地形の色・明るさ | **参照と一致**。本番jarで「mod自身の高速ブロックレンダラが無効」だったのが原因で、mid帯 88.9 → **136.5**（upstream 136.8 / vanilla 146.6） | `p46-default-r433` / `p73-normal` |
| 画素対応フィット | `celtium = 1.475 × vanilla^0.915`（upstream は `1.372 × vanilla^0.916`）＝指数が一致 | `p46-default-r433` |
| 残差 | 平均絶対差 **8.00/255（約3%）**、HF 0.84（参照比）。ノイズ床は参照同士 0.22 / 候補同士 0.13 なので**実差**。mipレベルは原因でない（LOD 0 でも 6.90 対 参照7.63）。幾何エッジ幅は同一（6.16 対 6.05） | 帯別解析 + `p44-lod0` / `p48-mips` |
| カスタムシェーダ | **受理しない**（`GL_CURRENT_PROGRAM != 0` を preflight で拒否）。spider のハング（exit 124）解消、mirror 898 → **80**、p99 174 → 118ms | `p71-spider`（AMD）/ `p71-spider-intel`（Intel） |
| 拒否の副作用 | 通常runでは**発動0回**（`shaderProgramRefusals=0`）。shader場面では340回 | `p73-normal` / `p73-spider` |
| 失敗経路（submit/待機タイムアウト） | 両GPUで**bounded**（exit 1・report あり・24〜27s）。ハング/クラッシュしない | `p50-failsubmit4-r438` / `p54-failidle-r441` / `p55-submit-intel` / `p56-idle-intel` |
| resize / reload | 両GPUで PASSED・validation違反0 | `p57-resize-amd|intel` / `p57-reload-amd|intel` |
| 場面coverage | 6場面×両GPU＋spectator3種×両GPU が PASSED（発火の証拠付き） | `p58`〜`p66` 系、`docs/plan.md` §5 |
| handoff費用 | CPU時間は ~135µs/handoff（通常と postprocess で同一）＝律速ではない。GPU時間は未計測 | `p69-normal` / `p69-postprocess` |
| 未解決 | GPU側handoff時間の計測、`switchUseShader` の他shaderでの受入、残差3%の要因、GL-free frame（条件b） | — |

## 7.6 native present経路の現状（完成条件 b/c に直結）

`VulkanSwapchain` には present の証拠機構が既にある（`-Dceltium.vulkan.presentMarker` でマーカー色を提示、
`-Dceltium.vulkan.presentReadback` で提示画像を読み戻して平均を記録、`lastPresentSucceeded` /
`lastPresentedMean` を保持）。今回 `presentFromComposite` + `presentMarker` + `presentReadback` を有効にして実測:

| 指標 | 結果 |
|---|---|
| present 自体 | **`presentFailures=0`**（提示は成功している） |
| swapchain | `swapchainOutOfDate=0`、`presentMode=1` |
| fixture verdict | **PASSED**（地形は正常） |
| **validation** | **exit 1 / validationClean=false／`VUID-vkCmdDraw-None-09600` ×6** |

- **新しい欠陥**: present 経路で **記述子のイメージレイアウト不一致**（`VUID-vkCmdDraw-None-09600`）が出る。
  提示のために frame image を `PRESENT_SRC_KHR` へ遷移させる一方、サンプリング用記述子が別レイアウトを
  宣言している疑いが強い。通常run（present無効）は clean なので、この経路固有。
- **fixture の verdict は PASSED のまま**で、この失敗は harness の `validationClean=false` 側で捕捉された
  （fixture の判定が validation を代替しないことの実例）。
- 未確認のまま残る部分: **window/compositor への到達**（提示画像の読み戻しは取れるが、実際の画面出力は
  X11/Wayland の capture が必要。方法の調査は未了）。

**修正（このターン・解決）**: validation メッセージの全文で原因が確定した。

```
expects VkImage 0x12... (COLOR, mip 0, layer 0) to be in layout VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
--instead, current layout is VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
```

`readBackMean()`（presentReadback の計器）が **レイアウト遷移なしで `vkCmdCopyImageToBuffer` を記録**しており、
提示済み画像（PRESENT_SRC_KHR）をそのまま読もうとしていた。対策として **提示→TRANSFER_SRC のバリアと、
コピー後に PRESENT_SRC へ戻すバリア**を追加した。

| 指標 | 修正前 | 修正後 |
|---|---|---|
| `VUID-vkCmdDraw-None-09600` | 6件 | **0件** |
| verdict | exit 1 / validationClean=false | **exit 0 / validationClean=true / msgs=0 / fixture PASSED** |
| present | presentFailures=0 | **presentFailures=0（維持）** |

- つまり**提示そのものは元から成功**しており、欠陥は計器（読み戻し）側にあった。通常runは present 無効なので影響なし。
- 残る: `presented image mean=` の値を report に出す（提示画像にマーカー色が入った証拠）、および
  window/compositor capture の可否調査。

**window/compositor capture の可否（このターン・環境側で不可と確定）**: 実画面の capture を試みた。

| 試行 | 結果 |
|---|---|
| `DISPLAY` / `WAYLAND_DISPLAY` | `:0` / `wayland-1` が設定されている（`/tmp/.X11-unix/X0` も存在） |
| `ffmpeg -f x11grab -i :0` | **失敗**: `Capture area 1920x1080 ... outside the screen size 0x0`（**X screen が 0×0**） |
| **`grim`（Wayland screencopy）** | **インストール済み**（`/usr/bin/grim`, `/usr/bin/slurp`）だが **`no wl_output`** ＝ Wayland に出力が無い |
| `--headless` を外した実行（Wayland） | プロセスは exit 0 で終了するが **fixture FAILED / screenshotSaved=false**（`--force-x11` 時は `Too few world rendering frames for startup validation: worldFrames=20`） |

**具体的な不足条件**（goal の「取得不能なら不足条件を残す」に対応し、method名ではなく**この環境で実測**）:
1. **capturable な表示出力が存在しない**: X11 は screen 0×0（x11grab が拒否）、Wayland は `no wl_output`
   （`grim` は導入済みだが出力が無い）。物理/仮想モニタを持つセッションが要る。
2. 実窓を出して安定描画できる非 headless 環境（現在は fixture が `worldFrames=20` で閾値未満）。
3. もしくは GPU 側で提示画像をファイル化する経路（`presentReadback` の拡張で可能。compositor 到達の
   代替にはならないが、少なくとも「提示画像の中身」は既に取れている）。

いずれも「method 名」ではなく、**この環境で実測して不可能と確認した**ものである。なお present 側の証拠
（提示画像の中身＝マーカー平均 191.25）は取得済みなので、残る主張は「提示が compositor まで届いたか」に限定される。

**完成条件(c)の証拠（このターン）**: 提示画像の内容を report に出して実測した（`presentMarker` は画像を
マゼンタ (1,0,1,1) でクリアする。8bit のバイト平均は (255+0+255+255)/4 = **191.25**）。

| 指標 | 実測 |
|---|---|
| `presentSucceeded` | **true** |
| `presentedMean` | **191.25**（マゼンタ期待値 191.25 と**完全一致**） |
| `presentFailures` | 0 |
| verdict | exit 0 / validationClean / PASSED |

- **提示された swapchain 画像に、このrendererが合成したフレームが入っている**ことの直接証拠が取れた
  （前ターンまで「presentFailures=0」だけで、画像の中身は未確認だった）。
- 残るのは **window/compositor への到達**（画面そのものの capture）。headless セッションのため
  X11/Wayland の capture 手段が必要で、利用可否の調査は次項。

**（旧メモ）** 次の一手: (1) present 経路のレイアウト遷移と記述子の宣言を突き合わせて `VUID-vkCmdDraw-None-09600` を解消する、
(2) `presentReadback` の平均値がマーカー色と一致することを記録して「提示画像にフレームが入った」証拠を取る、
(3) window/compositor capture の可否を調べる（取れない場合は不足条件を明記）。

## 7.7 完成条件(b)（GL contextなし）の作業内訳 — フレーム経路のGL依存の実測

`VulkanFrameTarget` と `VulkanTerrainGpuBackend` のフレーム経路に出てくる GL 呼び出しを数えた（grep による
出典付きの棚卸し。**未実装の主張ではなく、いま残っている依存の一覧**）。

| 分類 | 実測された呼び出し（回数） | 除去の方針 |
|---|---|---|
| **相互運用グルー**（bridge 本体） | `glBindFramebuffer`(5) / `glNamedFramebufferTexture`(3) / `glBlitNamedFramebuffer`(2, 色と深度) / `glWaitSemaphoreEXT` / `glDeleteSemaphoresEXT`(3) / `glFlush`(4) / `glGetNamedFramebufferAttachmentParameteri`(6) / `glGetNamedRenderbufferParameteri`(5) | Vulkan-owned の color/depth に描き、**同一デバイスの present**へ直接出す（`presentFromComposite` の経路が既に存在し、提示画像の中身はマーカー平均で確認済み）。GL の blit/semaphore は移行用 |
| **深度変換プログラム** | `glGetUniformLocation`(3) / `glDeleteShader`(3) / `glGenerateMipmap` 系 / `glTextureParameteri`(9) | D32→D24 変換自体が不要になる（GL へ返さないため） |
| **状態ミラー** | backend: `glGetInteger`(15) / `glGetFloatv`(8, 行列) / `glIsEnabled`(5) / `glGetIntegerv`(3) / `glGetTexParameteri`(3) / `glGetTexParameterf`(2) / `glBindTexture`(2) / `glActiveTexture`(2)。frame target: `glGetInteger`(15) | Minecraft 側から state を供給する（goal の「camera/state は Minecraft 側から供給」）。行列は `captureGlMatrices()` の置き換え |
| **テクスチャミラー** | `glGetTexLevelParameteri`(5) / `glGetTextureLevelParameteri`(4) | MC の sprite/frame データから供給し、readback を不要にする（atlas stitch / dynamic texture / sprite mip） |
| **計器・検証** | `glCheck`(10) / `glGetError` 系 | 計器なので最終形には不要（開発時のみ） |

- 状態クエリの総数: `VulkanFrameTarget` 24箇所 / `VulkanTerrainGpuBackend` 26箇所。**どれも「GL から読む」もので、
  描画そのものは既に Vulkan 側にある**。したがって (b) の作業は「読む元を Minecraft のデータに替える」ことに集約される。
- 既に Vulkan 側にあるもの: 地形パイプライン、immediate pipeline、descriptor/allocator、swapchain/present。

## 7.8 完成条件(b) の実装第1歩: 即値パスの行列GL読み戻しを廃止（既定化・画素一致で検証）

棚卸し（§7.7）の「状態ミラー」のうち **行列** を実際に置き換えた。

- 既存の `MIRRORED_MATRICES`（`-Dceltium.vulkan.mirroredMatrices`）は**双方向の比較用スイッチ**で、
  既定は GL 読み戻し側だった。ミラー側を強制して実測:

| 比較 | 結果 |
|---|---|
| 既定（GL読み）vs ミラー強制 の**画像** | **meanAbsDiff 0.000 / HF比 1.000（画素一致）** |
| `matrixMirrorDelta` | **proj:0.0, view:0.0**（ミラーと実GLが完全一致） |
| verdict | exit 0 / validationClean / PASSED |

- したがって**既定をミラー側に変更**した（`-Dceltium.vulkan.mirroredMatrices=false` で旧経路に戻せる）。
  変更後の既定で再測定: 旧既定との **meanAbsDiff 0.000**、verdict PASSED、target受入 exit 0。
- 効果: 即値パスが毎バッチ行っていた**行列の GL 読み戻し（`captureGlMatrices()` の GL 経路）が不要**になり、
  (b) の「GL から読む元を Minecraft 側のデータに替える」の1項目が完了した。

**§7.7 の棚卸しの訂正（このターン）**: 「状態ミラー」は**既にミラー側が既定**だった。
`celtium$captureState()` は `immediateState.*`（ミラー）から読んでおり、`glIsEnabled` の出現箇所の多くは
**診断文字列の中**（2452-2455行）で、描画経路の依存ではない。行列と同じく、grep の回数は*実際の依存*を
過大に数えていた。**実依存として残るもの**（ソースで確認）:

| 依存 | 箇所 | 置換方針 |
|---|---|---|
| **fog** | `fogMode()` が `glIsEnabled(GL_FOG)` と `glGetInteger(GL_FOG_MODE)` を毎回読む（3737行〜）。`VulkanGlState` に fog フィールドは無く、`GlStateManagerMixin` も fog を追跡していない | Minecraft 側の `GlStateManager.enableFog/disableFog/setFog(FogMode)`＋`setFogDensity/Start/End` を mixin（SRG名）でミラーし、`fogMode()` をミラー読みに。検証は画素一致（fog は画面に出るので差が出る） |
| **テクスチャ寸法** | `VulkanGlTexture` のミラー作成時の level クエリ | MC の sprite/frame データ（寸法）から供給 |
| **相互運用グルー** | blit（色・深度）・semaphore・FBO/renderbuffer クエリ・深度変換プログラム | Vulkan-owned target に描き同一デバイス present へ一本化（`presentFromComposite` が既に到達） |
| 計器・診断 | `glIsEnabled` の診断文字列、`glCheck`、行列比較 | 最終形では不要 |

**fog ミラー化の設計確定（このターン・実装は次）**: 実装に必要な事実がすべて判明した。

- **fog の正しい供給元は Minecraft 自身のフィールド**（GL ではない）:
  `net.minecraft.client.renderer.GlStateManager.fogState` は **public static** で、
  `FogState { public BooleanState fog; public int mode; public float density, start, end; }`、
  `BooleanState.currentState` も **public**。GL 側の `GLStateManagerFogService`（このプロジェクト）は既に
  このフィールドから読んでおり、`EntityRenderer.fogColorRed/Green/Blue` も同様に取れる。
- 置換対象は backend の `fogMode()` にある `glIsEnabled(GL_FOG)` と `glGetInteger(GL_FOG_MODE)` の2つだけ
  （`mode` は `GL_EXP2`→1 / `GL_EXP`→2 / それ以外→3 に写像）。
- **制約**: `celtium-vulkan` は `net.minecraft` 参照が **0件**（純 Vulkan モジュールとして設計されている）。
  したがって backend に MC 型を持ち込まず、**mirror フィールド＋setter を backend に置き、forge 側
  （`GLStateManagerFogService` が `GlStateManager.fogState` を読める場所）から push する**。
- 検証は**画素一致**（fog は画面に出るので、写像や既定値を誤れば必ず差が出る）。前例: 行列ミラーは
  meanAbsDiff 0.000 で既定化できた。

**fog ミラー化 実装・検証済み（このターン）**: 設計どおりに実装した。

- `celtium-vulkan` に **MC非依存の静的ホルダ `VulkanFogMirror`**（`set(enabled, mode)` / `hasValue()`）を追加。
  まだ push されていなければ `fogMode()` は**従来の GL クエリにフォールバック**する（安全側）。
- forge 側 `CeleritasWorldRenderer` の per-frame 経路で `GlStateManager.fogState.fog.currentState` と
  `fogState.mode` を push。
- report に `fogModeSource=mirror|gl_query` を追加して**どちらが使われたかを証拠化**。

| 検証 | 結果 |
|---|---|
| 標準run（`p82-standard`）旧既定との**画素一致** | **meanAbsDiff 0.000**、`fogModeSource=mirror` |
| weather（`p82-weather` vs `p82-weather2`）**同一ビルドのノイズ床** | **1.208** |
| weather（ミラー前 `p64-weather-amd` vs 新） | **0.992（ノイズ床以下）** |
| verdict（両run） | exit 0 / validationClean / PASSED |

=> **fog の GL クエリ（`glIsEnabled(GL_FOG)` / `glGetInteger(GL_FOG_MODE)`）を廃止**し、画像が変わらないことを
確認した。これで (b) の「GL から読む」依存は **行列 → fog** の2項目が完了。

**bound texture の GL 読みを廃止（このターン・(b) の3項目目）**: `announceDynamicTexture` /
`announceAnimatedUpload` は `glGetInteger(GL_TEXTURE_BINDING_2D)` を読んでおり、コメントには
「このrendererで GL クエリがフックで供給できない唯一の場所」と書かれていた。しかし `bindTexture` は
**既にミラーがフックしている**呼び出しなので、ミラーで供給できる。まず**一致を実測**した:

| 指標 | 実測 |
|---|---|
| `boundTextureSamples` | **525** |
| `boundTextureMismatches` | **0**（525サンプルすべて一致） |
| verdict | exit 0 / validationClean / PASSED、`fogModeSource=mirror` |

- 525/525 の一致を受けて、比較を **`-Dceltium.vulkan.compareBoundTexture=true` の opt-in** にし、
  **announce 経路はミラーだけを読む**ようにした（既定で GL クエリを取らない）。
- 変更後の検証: **標準runは前回との meanAbsDiff 0.000**、weather は 1.548（同一ビルドのノイズ床 1.2〜1.5 の範囲）、
  両run exit 0 / validationClean / PASSED、target受入 exit 0。

**テクスチャ寸法の供給（このターン・実装したが未使用と判明）**: `announceDynamicTexture` の寸法を
GL に問い合わせない経路を実装した（`common` の `TerrainGpuBackend` に寸法付きオーバーロード、
`VulkanTerrainGpuBackend` にその実装、`DynamicTextureMixin` が `DynamicTexture` の private な
`width`/`height` を読んで渡す）。

**しかし実測すると新経路は一度も使われなかった**: report の `announcedDynamicTextures=0`、
ログは `CELTIUM_DYNAMIC_ANNOUNCE bound=28 size=1x1` で**従来の GL 経路**のままだった。

- **原因**: 本番（SRG名）では `DynamicTexture.class.getDeclaredField("width")` が一致しない。
  同じ mixin の**ピクセル配列が動いている理由は「型（`int[].class`）で探している」**ことで、
  名前で探す私の実装は production では原理的に当たらない。
- これは**計器が捕まえた**（`announcedDynamicTextures` を report に足していたので「使われていない」と分かった）。
  画像は PASSED（前回weather runとの差 0.980＝ノイズ床 1.2〜1.5 以下）なので、退行はしていない。
- **正しい供給元に載せ替えて解決（このターン）**: `TextureUtil.uploadTexture(int id, int[] data, int width, int height)`
  の**引数**を使う。SRG名は**推測せず導出**した: マップ済みソースの引数名 `p_110988_1_` などが SRG 番号そのものなので
  `func_110988_a` と確定する（既存の `func_147955_a`/`func_110989_a` のフックも同じ導出で選ばれている）。
  壊れていたサイズ反射は撤去し、`DynamicTextureMixin` は早期ヒントの announce だけを残した。

| 指標 | 結果 |
|---|---|
| `announcedDynamicTextures` | **415**（修正前は 0）＝1 runあたり約 **830 回**の `glGetTexLevelParameteri` を回避 |
| 画像（前回runとの差） | **meanAbsDiff 0.782**（weather のノイズ床 1.2〜1.5 以下＝変化なし） |
| verdict | exit 0 / validationClean / PASSED、`boundTextureMismatches=0` |
| target受入 | exit 0 |

**current colour の GL 読みを廃止（このターン・(b) の5項目目）**: 位置のみの内容（format 7＝空など）は
「そのときの current colour」で描かれるため、offer 時に `glGetFloatv(GL_CURRENT_COLOR)` を読んでいた。
この色は **`GlStateManager` の `color(...)` をフックした既存ミラー（`immediateState.colorRed/Green/Blue`）**が
既に持っているので、ミラー読みに置換した（ゲームがまだ色を設定していなければ従来の GL 読みにフォールバック）。

| 指標 | 結果 |
|---|---|
| `colorMirrorUses` | **200**（位置のみの経路が200回ミラーを使用＝GL 読みを回避） |
| 画像（前回標準runとの差） | **meanAbsDiff 0.011 / 上部1/3（空）は 0.000** |
| verdict | exit 0 / validationClean / PASSED |
| target受入 | exit 0 |

**FBO読みの置換を試みて撤回（このターン）**: `VulkanFrameTarget` は game の framebuffer 名（`gameFbo`）を
保持しているので、ブリッジ経路の `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)` をそれで置き換え、
使用元を数える計器（`drawFramebufferMirrorUses` / `drawFramebufferQueries`）を付けて実行した。

**結果: 両方とも 0**＝**そのコード経路がfixtureで一度も走っていない**。標準run（`p88-fbo`）でも
postprocess 場面（`p89-offtarget`）でも `offTargetAccepted=0` / `offTargetDrawn=0` / `offTargetCopiedBack=0` で、
**オフターゲットの copy-back 経路は未実行**だった。検証できない挙動変更を残さないため**その場で撤回**した
（画像は両runとも PASSED / meanAbsDiff 0.000 で、変更が中立であることは確認できている）。

- **オフターゲット場面で経路を踏んで確定（このターン）**: fixture には `-Dceltium.validation.drawTestTargets=true`
  があり、**セカンダリ Framebuffer を bind して赤い quad を描き、メインへ戻す**（＝まさにオフターゲット描画）。
  AMD/Intel 両方で実行した結果:

| 指標 | AMD | Intel |
|---|---|---|
| `offTargetOffered` | **435** | **269** |
| `offTargetAccepted` | **0** | **0** |
| `offTargetDrawn` / `offTargetCopiedBack` | 0 / 0 | 0 / 0 |
| 拒否理由（`offTargetRefusals`） | `{target is outside the RGBA8/D24/full-viewport bridge contract=435}` | 同（269） |
| `offTargetPerTarget` | `0:offered=217,atPass=0,drawnPasses=0  e7e6020:offered=218,...` | 同種 |
| verdict | exit 0 / validationClean / PASSED | exit 0 / validationClean / PASSED |

  **結論**: オフターゲットは「offer はされるが**契約外として全件拒否**される」経路である（拒否は理由つきで
  **target別に計数**され、黙って落ちていない）。したがって
  - 計画の「オフターゲット**受入**」という表現は誤り。正しくは「オファーされ、契約外として拒否される」。
  - `offTargetDrawn++` が描画証拠にならないという注意は、**現在その経路が走っていない**ことの裏返しでもある。
  - ネイティブが担うのは **RGBA8/D24/フルビューポートのブリッジ契約内**（メイン画面）だけで、それ以外は GL が描く。
    これは scope の境界として docs に明記する（未実装ではなく**契約外**）。
- **（旧メモ）重要な訂正**: 計画が「オフターゲット受入」として挙げてきた経路は、**いま使っている fixture では発生していない**。
  したがって `offTarget*` の受入を「確認済み」として扱わない。オフターゲットを実際に発生させる場面
  （別 target へ描くゲーム経路、例えば描画先を切り替える mod/場面）を用意するまで、この経路の受入は**未検証**とする。
- 計器（カウンタ）だけを足して 0 のまま残すことも避けた。0 を報告し続ける計器は「使われていない」ことの
  証拠にはなるが、そのためだけにコードを増やさない方針にした（必要になった時点で戻せる）。

**(b) の進捗**: 行列 → fog → bound texture → テクスチャ寸法 → current colour の **5項目が完了**。残りは
**相互運用グルー**（blit/semaphore/深度変換/FBOクエリ）と `GL_DRAW_FRAMEBUFFER_BINDING` の実読み箇所。

**（旧メモ）** 正しい供給元: `TextureUtil.uploadTexture(int id, int[] data, int width, int height)` の**引数**。
  このプロジェクトは既に `TextureUtil` をフックしているので、そこで寸法を受け取ればリフレクション不要で
  名前問題も起きない。次はこの経路に載せ替える（`announceDynamicTexture(pixels, width, height)` は実装済みなので
  呼び出し元だけの変更で済む）。

**(b) の進捗**: 行列 → fog → bound texture の3項目が完了。残りは (1) テクスチャ**寸法**の
`glGetTexLevelParameteri`、(2) 相互運用グルー（blit/semaphore/深度変換/FBO クエリ）、(3) `GL_CURRENT_COLOR` /
`GL_DRAW_FRAMEBUFFER_BINDING` の残り。

**（旧メモ）** 次の一手: (1) 残る `glIsEnabled`/`glGetInteger`（診断以外）を同方式で潰す、(2) テクスチャ寸法の level クエリを
MC の sprite/frame データへ、(3) 相互運用グルー（blit/semaphore/深度変換）の一本化。
`fogMode()` を mirror 読みに、(2) forge 側の per-frame 経路から push、(3) 既定 run と旧実装の画素一致を確認して
既定化、(4) `glIsEnabled`/`glGetInteger` の残り（診断以外）を同じ方式で潰す。（`GlStateManagerMixin` に fog の注入を足し、
`fogMode()` をミラー読みにして画素一致を確認）。
(2) テクスチャの `glGetTexLevelParameteri`（sprite/frame データからの供給）、
(3) 相互運用グルーの blit/semaphore（同一デバイス present への一本化）を順に潰す。

## 7.9 vanilla coverage: registry駆動のブロック掃引（このターン・新規実装）

優先順位5「registryから自動fixtureを生成し、小場面に分割する」に対応する場面を実装した。

- fixture に `-Dceltium.validation.blockSweep=<count>` を追加。`Block.REGISTRY` を順に歩き、
  **平坦worldの地表の1つ上（y=4）に 16×16 グリッドでブロックを設置**する。generator は数種類しか作らないので、
  これまではその数種類でしかメッシュ経路を踏んでいなかった。
- 設置数・失敗数・進行index・registryサイズを **fixture の report 直下**に記録（`rendererDebug` ではない）。

**最初の実装は失敗し、その原因を実ログから読んだ**（2ブロック/tick・250個）:

| GPU | 結果 | ログの根拠 |
|---|---|---|
| AMD | **fixture FAILED** | `Too few world rendering frames for startup validation: worldFrames=94 (threshold 100), worldTicks=200` |
| Intel | **exit 124 / MISSING_REPORT** | 175秒でタイムアウト（time-limit） |

→ 層を一気に埋めるとチャンク再構築が続き、**フレームループが閾値に間に合わない**。goal の「小場面に分割」に従い、
**1ブロック/Ntick（既定4）**に間引き、既定の目標数を小さくした。

**間引き後の受入（両GPU）**:

| 指標 | AMD | Intel |
|---|---|---|
| `blockRegistrySize`（1.12.2の全ブロック） | **254** | **254** |
| `blockSweepTotal` / `Placed` / `Failed` / `Index` | 60 / **50** / **0** / 50 | 60 / **50** / **0** / 50 |
| `worldFrames` | 202（閾値100超） | 同 |
| verdict | **exit 0 / validationClean / PASSED** | **exit 0 / validationClean / PASSED** |

- 1ブロック/4tick×200tick＝50個になる（ペーシングどおり）。**失敗0**＝`getDefaultState()` と `setBlockState` が
  254種のうち50種で問題を起こさず、メッシュ・native地形パスがそれらを扱えた。
- **全254種へ広げる（このターン）**: `-Dceltium.validation.blockSweepFrom=<registry index>` を追加し、
  スライスで覆う方式にした。**ペーシングの上限も実測**した: `blockSweepEvery=1`（1個/tick・254個）は
  **exit 124 / MISSING_REPORT**（AMDでもタイムアウト）なので、測定済みの安全域 `every=4` を維持する。

| run（AMD） | from | placed | failed | verdict |
|---|---|---|---|---|
| `p92-sweep-amd` | 0 | 50 | 0 | PASSED |
| `p94-sweep50-amd` | 50 | 50 | 0 | PASSED |
| `p94-sweep100-amd` | 100 | 50 | 0 | PASSED |
| **累計** | — | **150 / 254** | **0** | — |

- **AMD は 254 種すべてを掃引完了（このターン）**。ただし切片の大きさに測定済みの制約がある:

| run（AMD） | from | count | placed | failed | verdict |
|---|---|---|---|---|---|
| `p95-sweep150-amd` | 150 | 254 | 50 | 0 | PASSED |
| `p95-sweep200-amd` | 200 | 254 | — | — | **exit 124 / MISSING_REPORT**（180s） |
| `p96-sweep200-amd`（再実行） | 200 | 254 | — | — | **exit 124**（フレークではない） |
| `p97-bisect-200-225` | 200 | **25** | 25 | 0 | PASSED |
| `p98-bisect-225-237` | 225 | **12** | 12 | 0 | PASSED |
| `p98-bisect-237-250` | 237 | **13** | 13 | 0 | PASSED |
| `p95-sweep250-amd` | 250 | 254 | 4 | 0 | PASSED |

  **読み方**: 単一の「病的なブロック」ではない（前半・後半とも単独では通る）。**50個を後半の位置から置くと
  累積コストでタイムアウトする**。したがって後半は **count=25 以下のスライス**にする（測定で決めた制約）。
  この分割で **AMD は 0-49 / 50-99 / 100-149 / 150-199 / 200-224 / 225-236 / 237-249 / 250-253 を全て実行**し、
  **254/254 種・失敗0** になった。
- **Intel 側はサンプル掃引（このターン）**: Intel は AMD より遅いので、**測定済みの安全サイズ**で頭・中・尾を
  抜き取った（全数を回す時間予算がないため。**AMD=254/254（全数）、Intel=75/254（抜き取り）**と明記する）。

| run（Intel） | from | count | placed | failed | verdict |
|---|---|---|---|---|---|
| `p99-sweep0-intel` | 0 | 25 | 25 | 0 | PASSED |
| `p99-sweep100-intel` | 100 | 25 | 25 | 0 | PASSED |
| `p99-sweep225-intel` | 225 | **25** | — | — | **exit 124 / MISSING_REPORT** |
| `p99b-sweep225-intel` | 225 | **12** | 12 | 0 | PASSED |
| `p99b-sweep237-intel` | 237 | **13** | 13 | 0 | PASSED |
| **Intel 累計** | — | — | **75 / 254** | **0** | — |

- **「後半スライスほど高い」現象が Intel でも再現**した（AMD と**独立した2台目**での観測）。GPU 固有ではない。
- **切片サイズの制約は GPU ごとに違う**（実測）: AMD の尾部は count=25 まで可、Intel の尾部は count=13 まで。
  したがって掃引の既定は「前半 25〜50 / 尾部 12〜13」とし、run ごとに記録する。
- **原因の一部を確定（このターン・制御された before/after）**: 設置を `setBlockState(pos, state, **2**)` で
  行っていた。2 はフラグ1（隣接ブロック更新の抑止）を含まないため、**設置ごとに隣接6ブロックが反応**していた
  （水が流れる・植物が抜ける・火が燃え広がる等）。`**3**`（=1|2: 隣接更新なし＋クライアント通知）に変更した。

| run（同一スライス・同一duration） | フラグ2（修正前） | フラグ3（修正後） |
|---|---|---|
| **Intel** from=225 count=25 | **exit 124 / MISSING_REPORT** | **exit 0 / PASSED / placed=25 failed=0 / worldFrames=152** |
| AMD from=200 count=254（=50個） | exit 124 | exit 124（**50個の尾部は依然不可**） |

  → **Intel では隣接更新が原因だったと確定**（同じスライスが通った）。AMD の尾部は 50個では依然として不可だが、
  25個なら通る（前ターンに実測）。つまり AMD 側には**別の律速**が残っており、それは未解明のまま残す。
- **副次的な重要性**: この変更で場面は「ブロックを**表示**する」ものになり、**シミュレーションしなく**なった
  （水が流れて消える・火が燃え広がる等が起きない）。coverage の場面としてはこの方が正しい。
- **「後半ほど高い」は撤回（このターン・ノイズだった）**: フラグ3のビルドで AMD の全スライスを取り直したところ、
  **以前は通っていたスライスが落ちた**（0..49/50..99/150..199/225..237/238..253 は PASSED、
  **100..149 と 200..224 が exit 124**）。

| スライス（AMD・現行ビルド） | count | 結果 |
|---|---|---|
| 0..49 | 50 | exit 0 / PASSED / placed=50 failed=0 |
| 50..99 | 50 | exit 0 / PASSED / placed=50 failed=0 |
| **100..149** | 50 | **exit 124 / MISSING_REPORT** |
| 150..199 | 50 | exit 0 / PASSED / placed=50 failed=0 |
| **200..224** | 25 | **exit 124 / MISSING_REPORT** |
| 225..237 | 13 | exit 0 / PASSED / placed=13 failed=0 |
| 238..253 | 16 | exit 0 / PASSED / placed=16 failed=0 |
| 累計 | — | **179 / 254**（2run失敗） |

  → **失敗は前半にも出た**ので、「後半ほど高い」「GPUごとに切片上限が違う」という前ターンの結論は
  **ノイズへの過適合**であり**撤回**する。同じ設定が run によって通ったり落ちたりする＝
  **180秒の fixture 予算に対してこの場面は余裕がない**。
- **確からしい説明（ただし未検証）**: スライスごとに**メッシュ化コストが違う**（複雑なモデルのブロックが多い
  スライスは1セクションの再構築が重い）。これは掃引の目的そのもの（重いブロックを見つける）と整合するが、
  再構築時間を測っていないので**仮説**として置く。
- **律速を計測で確定（このターン）**: 掃引に**設置時間と最悪ブロックの計器**を入れ、stdout にも出すようにした
  （fixture がタイムアウトした run は report を書かないため、ログ側にも出す必要があった）。

| 測定 | 結果 |
|---|---|
| 50個スライス（from=0） | `placeMillis=18`（0.36 ms/個）、worst=`flowing_lava` 13ms、frames=202、PASSED |
| 50個スライス（from=100） | `placeMillis=19`（0.38 ms/個）、worst=`end_portal` 12ms、frames=149、PASSED |
| **全254個（every=1, perTick=2）** | **placed=254 failed=0 / placeMillis=32（0.13 ms/個）**、**frames=1322 / tick=3318（約7.3 FPS）**、**exit 124 / MISSING_REPORT** |

  **確定したこと**:
  1. **設置はほぼ無料**（0.13〜0.38 ms/個。全254個でも32ms）。設置コストは律速ではない。
  2. **律速は描画**。全254種を同時に置くと **約7.3 FPS** まで落ち、fixture の場面が時間内に終わらない。
     50個スライスでは frames=149〜202 で収まる。
  3. したがって**掃引はスライス必須**（25〜50個）。理由は「後半ほど高い」ではなく
     **そのスライスに入るブロック種別の描画コスト**である（measured）。
  4. 同じ設定が通ったり落ちたりするのは、**ブロック種別の混ざり方でフレーム数が変わる**ため。
     落ちたスライスは同じ設定で再実行し、成功として数えない方針を維持する。
- **当面の運用**: 実測で安定していたのは **count<=16**（13 と 16 は両ビルドで通過、25 と 50 は落ちることがある）。
  掃引は **count 13〜16** で回し、落ちたスライスは**同じ設定で再実行して記録**する（フレークを成功として数えない）。
- 原因（残り）は**未解明**。遠いグリッド位置はカメラ外になり、MC が描画を省く側だと考えられるが、
  その説明は測定で裏づけていない。次に slice を 25 に固定して Intel 側を回すか、`/debug` で
  チャンク再構築回数を数えて確かめる。

**（旧メモ）** AMDで150種（59%）を失敗0で掃引。残りは from=150 / 200 の2run、Intel側は5run。
- **残り**: 1runあたり50種なので、254種を覆うには `blockSweep` の開始位置をずらして数run回すか、
  tick数を増やす。次は **item/model と entity/TESR** も同じ方式で registry から掃引する。

## 7.10 重いcoverage場面での参照比較（このターン・優先順位3と8の交点）

ブロック掃引の「254種を同時に置くと遅い」という計測を、**固定参照Celeritas**（`989ebf2e`）で同じ場面を走らせて
切り分けた。同じ world・同じ camera・同じ fixture（検証クライアント）・同じ duration。

| run | placed | elapsed | worldFrames | **p50** |
|---|---|---|---|---|
| celtium（every=1, 1/tick） | 200 | 24.9 s | 153 | **94.3 ms** |
| celtium（every=1, 2/tick） | 254 | **180.3 s（exit 124）** | — | — |
| **celeritas 参照（every=1, 2/tick）** | **254** | **24.3 s** | **580** | **16.2 ms** |
| celtium（50個スライス） | 50 | 25.1 s | 204 | 52.0 ms |

**読み取れること**:
- **同じ254種の場面で、参照は 24.3 s / p50 16.2 ms（約62 FPS）で完走**し、**Celtium は 180 s で終わらない**。
- Celtium は**50個（参照の1/5のブロック数）でも p50 52 ms** で、参照の254個（16.2 ms）より遅い。
  通常場面の Celtium は p50 ≈42 ms、参照は同種の場面で約16 ms なので、**この差は掃引場面に限らない**。
- **注意（比較の限界）**: 両者は別の描画系（Vulkan bridge と GL）で、fixture の検証内容も違う。同じなのは
  world・camera・場面・時間だけであり、**フレーム間隔の比較として読む**。参照runは `expectVulkan*` を付けていない。
- **次の仮説（未検証）**: 254種では**ブロック種別ごとにパイプライン/descriptorが作られ**、種別が増えるほど
  1バッチあたりの準備コストが増える。次は `descriptorSetsCreated` / パイプライン作成数と
  「1種別あたりの初回コスト」を測って確かめる。

## 7.11 参照との定常性能比較（このターン・優先順位8の実測）

§7.10 の「差は掃引場面に限らない」という読みを、**通常場面**でも確かめた。同一 world・camera・fixture・duration。

| run | elapsed | worldFrames | **p50** | p99 |
|---|---|---|---|---|
| celtium 通常 | 24.6 s | 255 | **41.4 ms** | 46.4 ms |
| celtium 通常（再） | 24.1 s | 247 | **42.2 ms** | 46.9 ms |
| **celeritas 参照 通常** | 24.1 s | **580** | **16.2 ms** | 17.6 ms |
| celtium 50ブロック | 25.1 s | 204 | 52.0 ms | 57.0 ms |
| **celeritas 参照 254ブロック** | 24.3 s | **580** | **16.2 ms** | 22.9 ms |

**確定したこと（測定・再現あり）**:
1. **通常場面で Celtium は参照より約2.6倍遅い**（p50 41.4/42.2 ms 対 16.2 ms）。2回の再測定で一致。
2. **参照は場面に依らず一定**（通常でも254種でも p50 16.2 ms、p99 17.6→22.9 ms）。**ブロック種別を254に増やしても
   ほぼ無コスト**。一方 Celtium は 42 → 52 → 94 ms と劣化する。
3. したがって優先順位8の出発点は「**重い場面**」ではなく「**通常場面の2.6倍**」である。

**棄却した仮説**: 「ブロック種別ごとにパイプライン/descriptorが作られる」。
`descriptorSets=1`（全runで一定）、`mirroredTextures` 11→12→20、`inPlaceRefreshes` 348→277→194 と、
Vulkan資源の生成は種別数に比例していない。ただしこれらのカウンタは**run合計**なので、フレーム数が減れば
下がる（速度の指標にはならない）。**per-frame の内訳を測る必要がある。**

**次の計測（優先順）**: (1) ブリッジの1フレームあたりCPU時間（`-Dceltium.vulkan.handoffTimings=true` の
`handoff detail` 行。色blit-in・深度変換・色blit-out の3〜4回の全画面操作がある）、(2) 地形のセクション描画の
提出回数とバッチング、(3) frame composite と present の経路。

## 7.12 性能ギャップの所在を同一jarのA/Bで確定（このターン）

§7.11 の「通常場面で2.6倍」を、**同じjarの `-Dceltium.vulkan.referenceRender=true`** と比べることで
経路そのものに帰属させた（fixture・mod・world・camera・GUI がすべて同一で、切り替わるのは地形の描画経路だけ）。

| run | worldFrames | **p50** | p95 | p99 |
|---|---|---|---|---|
| celtium native（Vulkan地形） | 240〜255 | **41.4〜43.3 ms** | 47.3 | 48.5 |
| **celtium + referenceRender=true**（同一jar・GL経路） | **580** | **16.15 ms** | 17.2 | 17.3 |
| celeritas 参照jar | 580 | 16.2 ms | — | — |

- `referenceRender` の run は `draws=0 sections=0 | reference render` を報告しており、native地形パスが
  実際に何もしない状態での比較になっている（**A/Bとして成立**）。
- さらに、**native地形パスの計時は1フレーム合計で約2.2 ms**（`translucent 62 sections 596.9 µs` ＋
  `solid 62 sections 284.2 µs` ＋ `cutout_mipped 62 sections 1.317 ms`）。
- **したがって 43 ms のうち約25 ms は、記録されたパス計時の外側**にある。候補は (a) ブリッジの全画面操作
  （色blit-in・深度変換・色blit-out）と待ち合わせ、(b) ハンドオフの同期、(c) フレーム合成と present。
- 計器の現状: `handoff detail` は**CPU API時間**のみ（≈128 µs/handoff）で、この25 msを説明しない。
  **ブリッジのGPU時間を計時する計器が無い**ことが次の不足。

**原因の所在を特定（このターン・既存スイッチの行列）**: 新しい計器を足す前に、既にある切り替えで25 msを分割した。

| run | worldFrames | **p50** | p99 |
|---|---|---|---|
| native（基準） | 240 | **43.32 ms** | 48.5 |
| **`-Dceltium.vulkan.immediate=false`** | **569** | **16.33 ms** | 22.2 |
| `-Dceltium.vulkan.mirrorRefreshFrames=8` | 260 | 40.18 ms | 47.6 |
| `-Dceltium.vulkan.presentFromComposite=true` | 287 | 36.30 ms | 41.1 |
| `referenceRender=true`（同一jar・GL経路） | 580 | 16.15 ms | 17.3 |
| celeritas 参照jar | 580 | 16.18 ms | 17.6 |

- **`immediate=false` で 43.32 → 16.33 ms**＝**27 msのギャップは即値（immediate）経路に全てある**。
  地形パス（2.2 ms）は正常で、ミラー（`mirrorRefreshFrames=8` でほぼ変わらず）も present も主因ではない。
- **1フレームあたりの回数**（レポートから、フレーム数で割った値）:

| 指標 | native | immediate=false |
|---|---|---|
| `flushCount` | 4.8/フレーム | 4.8/フレーム |
| `clearOrderFlushes` | 5.8/フレーム | 5.9/フレーム |
| `orderPreservingFlushes` | **1.8/フレーム** | **0/フレーム** |
| `immediateSubmittedBatches` | **13.0/フレーム** | **0/フレーム** |

  → **ハンドオフは約10.6回/フレーム**（4.8＋5.8、`orderPreserving` を含めると12.4）。27 ms ÷ 10.6 ≒
  **1回あたり約2.5 ms**。これは「全画面blit＋深度変換＋同期」を毎回行っていることと整合する。
- **注意（A/Bの限界）**: `immediate=false` は即値バッチを**GLに描かせる**（ネイティブが断る）ので、16.33 ms は
  「地形だけネイティブ」の値であり、即値経路の**置き換えとして等価**ではない。言えるのは
  **「我々の即値経路が、置き換えているGL描画より約27 ms/フレーム高くついている」**という点。

**訂正（このターン・計器が前ターンの結論を否定）**: 前ターンの「ハンドオフ約10.6回/フレーム×約2.5 ms」は**誤り**だった。
`clearOrderFlushes` は**呼び出し回数**を数えており、`flushPendingBeforeGlDraw()` は保留が無ければ即 return する。
実際に何回ハンドオフしたかは、その emptiness チェックの**後**で数える必要がある。

**実測（追加した計器: `actualFlushes` / `actualFlushMillis` / `actualFlushWorstMillis`）**:

| 指標 | 実測 |
|---|---|
| `actualFlushes`（保留を持って実際にハンドオフした回数） | **439（1.82/フレーム）** |
| `actualFlushMillis`（ハンドオフの合計wall time） | **553 ms（2.3 ms/フレーム）** |
| `actualFlushWorstMillis`（最悪の1回） | **3 ms** |
| `clearOrderFlushes`（呼び出し回数・早期returnを含む） | 1394（5.78/フレーム） |
| `orderPreservingFlushes` | 439（1.82/フレーム） |
| フレーム | p50 43.03 ms / 241 フレーム |

→ **ハンドオフは 43 ms のうち 2.3 ms（5%）しかない**。ストールも無い（最悪3 ms）。
  「ハンドオフを減らせば速くなる」は**この測定では支持されない**。

**現時点の1フレーム予算（測定済みの部分）**: 地形パス 2.2 ms ＋ ハンドオフ 2.3 ms ＝ **4.5 ms**。
残り **約38.5 ms は未計測**で、`immediate=false` のA/Bにより**即値経路に関係することは確定**している。
つまり次に測るべきはハンドオフではなく、**バッチごとの処理**（offer → preflight → append）と、
即値経路が有効なときに**Minecraft側のGL描画が受ける影響**である。

**原因を特定（このターン）**: ゲームが呼ぶ入口 `submitImmediateBatch(...)`（mixin の `celtium$offerBatch` から）を
呼び出し側で計時し、`batchCallCount` / `batchCallMillis` として report に出す計器を入れた。

| 指標 | 実測（238フレーム） |
|---|---|
| `batchCallCount` | **3407（14.3回/フレーム）** |
| `batchCallMillis` | **6365 ms（26.7 ms/フレーム）** |
| **1回あたり** | **1.87 ms** |
| `immediateSubmittedBatches` | 3063（12.9/フレーム） |
| `actualFlushes` / `actualFlushMillis` | 438（1.8/フレーム）/ 554 ms（2.3 ms/フレーム） |
| フレーム | p50 **44.3 ms** |

→ **27 ms のギャップは、ほぼこの「バッチ呼び出し 1.87 ms × 14.3回/フレーム = 26.7 ms」で説明できる**。
  1フレーム予算は「地形 2.2 ms ＋ ハンドオフ 2.3 ms ＋ バッチ呼び出し 26.7 ms ＋ 残り ≈13 ms」。
  これが `immediate=false` で 16.3 ms になる理由と一致する。

**内訳を分割計時（このターン）**: バッチ呼び出しの中を段階的に割った。

| 区間 | 実測 |
|---|---|
| バッチ呼び出し全体（呼び出し側） | 3490回（14.3/フレーム）= **26.2 ms/フレーム**、1回 **1.83 ms** |
| └ `prepareImmediate`（preflight） | 3237回（13.3/フレーム）= **15.0〜15.2 ms/フレーム**、1回 **1.09〜1.13 ms（全体の57%）** |
| └ それ以外（本体） | 11.2 ms/フレーム |
| メソッド内計器（既存 `immediateSubmitNanos`） | **1867 µs/バッチ** ＝ 呼び出し側の測定と一致（コストはメソッド内） |
| フレーム | p50 42.8〜44.3 ms |

**棄却した仮説**: 「`descriptorSetFor` が毎回キャッシュミスして descriptor set を確保している」。
計器（`descriptorHits` / `descriptorMisses`）を入れて測ると **ヒット率 99.7%**（2905ヒット / 9ミス、243フレーム）。
descriptor の確保は 1.09 ms の原因ではない。

**現時点の1フレーム予算**: 地形 2.2 ms ＋ ハンドオフ 2.3 ms ＋ バッチ呼び出し 26.2 ms（うち preflight 15.2 ms ＋
本体 11.2 ms）＋ 残り ≈12 ms。

**原因を特定して修正（このターン・最大の性能改善）**: 段階的な分割計時で、1バッチ1.83 ms の内訳が
`prepareImmediate` の**テクスチャレジストリ照会**（`VulkanTextureRegistry.get`）だと判明した（地形パイプライン照会は 0 ms）。

| 経路 | 呼び出し | 時間 |
|---|---|---|
| **announced（BufferedImage → RGBA 変換）** | **3621回** | **4230 ms（17.6 ms/フレーム、1回1.17 ms）** |
| stale（GL 再アップロード） | 331回 | 1480 ms（6.1 ms/フレーム、1回4.47 ms） |
| 素のヒット | 2834回 | — |

**原因**: `announceImage` で置かれた画像が**消費されずに残る**ため、**ほぼ毎バッチで同じ画像を RGBA へ再変換**していた
（`fromImage`）。Minecraft は `DynamicTexture` を**同じオブジェクトのまま中身だけ更新**するので、画像の同一性では
判定できない。

**修正**: **announce 世代**を導入した。`announceImage` が世代を進め、`fromImage` がそれを消費する。
**未消費の世代ならミラーは既にその画素を持っている**ので変換を省く（`regAnnouncedSkips` で数える）。

| 指標 | 修正前 | 修正後 |
|---|---|---|
| worldFrames | 241 | **478** |
| **p50** | **42.94 ms** | **18.07 ms** |
| announced 変換 | 3621回 / 4230 ms | **7回 / 29 ms** |
| announced スキップ | 0 | **7630** |
| バッチ呼び出し | 26.6 ms/フレーム | **9.7 ms/フレーム** |
| verdict | — | **exit 0 / validationClean / PASSED** |
| **画像（修正前後）** | — | **meanAbsDiff 0.000（完全一致）** |

- **42.94 → 18.07 ms（2.4倍）**。参照（16.2 ms）との差は **2.6倍 → 約1.1倍** になった。
- **画像は画素一致**なので、速度のために正しさを犠牲にしていない。

**両GPUでの検証と、残るコストの性格（このターン）**:

| run | frames | p50 | p99 | batch | announced | skips | stale |
|---|---|---|---|---|---|---|---|
| **AMD 修正後** | 477 | **18.15 ms** | 23.4 | 9.7 ms/f | 0.1 ms/f | 7629 | 700（2956 ms = **6.2 ms/フレーム**） |
| **Intel 修正後** | 215 | **42.66 ms** | 49.6 | 19.5 ms/f | 0.2 ms/f | 3336 | 302（2497 ms = 11.6 ms/フレーム） |
| Intel 修正前 | 159 | 62.87 ms | 70.1 | — | — | — | — |
| celeritas 参照（AMD・GL） | 580 | 16.18 ms | 17.6 | — | — | — | — |

- **AMD 42.94 → 18.15 ms**（参照 16.18 の **1.12倍**）。**Intel 62.87 → 42.66 ms（32%改善）**。
- ログは、まだ読み戻している2つのテクスチャを名指ししている:
  `CELTIUM_READBACK_REFRESH glTexture=15` / `glTexture=21`（他は announced が pending している）。

**周期 invalidate は切れない（実測）**: `invalidateAll()` は毎フレーム全ミラーを stale にする（`mirrorRefreshFrames=1`）。

| 設定 | frames | p50 | stale | 画像（既定との差） |
|---|---|---|---|---|
| `mirrorRefreshFrames=1`（既定） | 477 | 18.15 ms | 700（6.2 ms/フレーム） | — |
| `mirrorRefreshFrames=0`（無効化） | 563 | **15.88 ms** | 0 | **meanAbsDiff 0.016**（完全一致ではない） |
| `mirrorRefreshFrames=4` | 472 | 18.75 ms | 686（6.2 ms/フレーム） | meanAbsDiff 0.000 |

→ **0 は速いが画像が変わる**ので採らない（周期 invalidate は正しさに効いている）。4 は画像一致だが速度の利得がない。
**残る 6.2 ms/フレーム（AMD）は「announce の無い2つのテクスチャの GL 読み戻し」**であり、
そこを消すには (a) その2つの更新経路を announce 側に載せる（`TextureUtil` の int[] フックと同じ方法）、
または (b) 読み戻し自体を小さくする（部分更新・ミップ単位）しかない。

**残コストの正体を同定（このターン・直接証拠）**: 読み戻しログに view ハンドルを足し、`CELTIUM_MIRROR` 行と
突き合わせて「どの id がどのサイズか」を確定した（推測ではない）。

| 読み戻しされる id | view | サイズ | 正体 |
|---|---|---|---|
| **glTexture=15** | `0x72` | **1280×720** | **画面サイズのテクスチャ（フレームの色）** |
| glTexture=21 | `0xbd` | 1×1 | 白テクスチャ（読み戻しは実質無料） |

- したがって残る **6.2 ms/フレームは 1280×720（約3.7 MB）の読み戻し**であり、1×1 の側はコストではない。
  `stale` の平均 4.2 ms/回は、ほぼこの画面サイズのテクスチャが占めている。
- **なぜ毎フレーム stale か**: `invalidateAll()`（`mirrorRefreshFrames=1`）が全ミラーを stale にし、
  画面サイズのテクスチャは**フレームごとに内容が変わる**ので、実際に毎回読み戻される。
- **修正の方向（優先順位4そのもの）**: このテクスチャは**フレームの色そのもの**なので、GL から読み戻すのではなく
  **Vulkan 側が持っているフレームの色をそのまま供給**できるはずである（ブリッジは既に色を GL→VK へ渡している）。
  それができれば 6.2 ms/フレームが消え、AMD は 18.15 → 約12 ms になる見込み（**見込みであって測定ではない**）。
- なお `mirrorRefreshFrames=0` は 15.88 ms まで下がるが**画像が 0.016 変わる**ため採らない（前ターン実測）。

**1280×720 ミラーの作成元を特定（このターン・スタックで実測）**: 大きなミラーの作成時にスタックを出すと、
`VulkanTextureRegistry.get <- VulkanTerrainGpuBackend.prepareImmediate <- submitImmediateBatchTimed` と出た。
つまり**即値バッチ（フレーム色をサンプルする最終合成）**が作っている。

- 当初は「S17で拒否されるバッチのために読んでいる無駄」と考えたが、**S17の判定は `prepareImmediate` より前**にあり、
  実際にミラーを作った合成バッチは**受理されて Vulkan が描いている**。したがって読み戻しは無駄ではなく、
  **フレーム色を Vulkan 側が既に持っているのに GL から読み直している**のが問題（＝優先順位4そのもの）。

**`presentFromComposite` の再測定（announce 修正後は経済性が反転）**:

| 設定 | frames | **p50** | stale | stale ms/フレーム | batch ms/フレーム |
|---|---|---|---|---|---|
| 既定（off） | 477 | 18.15 ms | 700 | 6.2 | 9.7 |
| **`presentFromComposite=true`** | **564** | **15.88 ms** | **284** | **0.3** | **5.4** |
| 明示 off | 475 | 18.12 ms | 694 | 6.2 | 9.6 |

- **15.88 ms は celeritas 参照（16.18 ms）を下回る**。画面サイズの読み戻しは 6.2 → **0.3 ms/フレーム**に減る。
- これは**フレームの色を native が持つ方向**（優先順位4）そのもので、合成を即値バッチとして取らず present 経路が扱う。
- **画像は完全一致ではない**: 既定との差は **meanAbsDiff 0.0155**（最大 3.7/255、差のある画素は 1096 = 0.12%）。
  内訳は **上部1/3（空）が 0.0000（完全一致）**、中央 0.0053（139画素）、下部 0.0413（957画素）で、
  差は地形領域（行 348〜705）に散在する小振幅。欠落や二重描画を示す構造的な差ではない。
- **参照との距離は変わらない**: celeritas 参照との meanAbsDiff は present 7.781 / 既定 7.791（ほぼ同一）。
- **0.016 の原因は未確定**（候補: 読み戻し経路が1フレーム前の色を渡していたのに対し、native 経路は現在のフレームを
  渡す、という丸め／1フレーム差）。**仮説であって結論ではない**ので、既定化する前に確かめる。

**`presentFromComposite` の判定（このターン・測定で決着）**: 各設定を2回ずつ走らせ、ノイズ床と系統差を分けた。

| 比較 | meanAbsDiff | 差のある画素 |
|---|---|---|
| 既定 vs 既定（ノイズ床） | **0.0000** | **6** |
| present vs present（ノイズ床） | **0.0000** | **17** |
| **既定 vs present** | **0.0155** | **1091（系統差）** |

| 設定 | p50（2回） | frames |
|---|---|---|
| 既定 | **18.19 / 18.81 ms** | 470 / 466 |
| `presentFromComposite=true` | **15.92 / 15.74 ms** | 560 / 559 |

- **速度は再現する**（present が約 **2.4 ms/フレーム** 速い）。しかも参照 16.18 ms と同等以下。
- **差はノイズではない**（ノイズ床は6〜17画素、差は1091画素）。
- **どちらが参照に近いか**: 差のある1091画素で、**既定が参照に近い**（平均差 11.57 対 present 12.27）。
  present が参照に近い画素は **26.0%** のみ。符号付きの平均差は **present が +0.62/+0.74/+0.71（R/G/B）明るい**。
- **結論**: `presentFromComposite=true` は**速いが、合成の一段を省くため僅かに明るくなる**。既定のままにする
  （速度のために静かに忠実さを落とさない）。参照との全体差は present 7.791 / 既定 7.781 でほぼ同じ。
- **原因の見立て（未検証）**: MC の合成（`Framebuffer.func_178038_a`）は全画面quadを**ブレンドして**描く。
  present 経路はそれを省いて直接提示するため、ブレンド一段分だけ明るくなる、という説明が符号と一致する。

**差の中身を確定（このターン・画像と数値）**:

1. **ブレンド仮説は棄却**。合成バッチの実ログは
   `elements=[12bPOSITION 8bUV 4bCOLOR] vertices=4 mode=7 ... col=0xffffffff` で、**不透明な全画面コピー**
   （alpha=1）。`VulkanSwapchain` にもブレンド処理は無い。したがって「ブレンド一段が抜けている」は成り立たない。
2. **差は ±1 LSB の丸め**。差の濃い領域（y≈590, x≈633 周辺 160×110）で3者を並べると、
   **既定と present は見た目に同一**（同じブロック・同じ色）で、**参照だけが明確に違う**（既知の残差）。

| 窓内の平均RGB | R | G | B |
|---|---|---|---|
| 既定 | 139.72 | 145.98 | 141.71 |
| present | **139.76** | **146.03** | **141.76** |
| celeritas 参照 | 131.58 | 141.58 | 127.56 |

   既定と present の差は **+0.04〜+0.05/255**（1 LSB 未満）。差のある画素は 0.12%、最大差でも 7/255。
   参照との距離（約12/255）に対して、この差は**「どちらが忠実か」を決められる大きさではない**
   （前ターンの「既定が近い」は 12.27 対 11.57 という平均の話で、±1 LSB の丸めを忠実さの差と呼ぶには小さい）。

**判断**: `presentFromComposite=true` は **約2.4 ms/フレーム（13%）の再現する利得**があり、差は **1 LSB 未満の丸め**。
ただし**既定化はまだしない**。既定化するなら Intel・全場面・失敗経路の受入を取り直す必要があり、
この1ラウンドではそれを検証できないため（フラグは残っており、いつでも既定化できる）。

**既定化を試して撤回（このターン・受入が壊れるため）**: フラグを既定化し、受入を取り直した。

| run（フラグON） | exit | validationClean | fixture | p50 |
|---|---|---|---|---|
| AMD 通常 | 0 | true | PASSED | **15.89 ms** |
| Intel 通常 | 0 | true | PASSED | **30.86 ms**（既定off時 42.66 → 28%改善） |
| postprocess (AMD) | 0 | true | PASSED | 15.99 ms |
| weather (AMD) | 0 | true | PASSED | 15.17 ms |
| spectator spider (AMD) | 0 | true | PASSED | 17.31 ms |
| submit失敗注入 | 1 | true | FAILED（期待どおり） | 有界・24.7s・report あり |
| idle失敗注入 | 1 | true | FAILED（期待どおり） | 有界・24.9s・report あり |
| **command-buffer target受入** | **1** | true | **FAILED** | — |

- **受入が1つ壊れた**: `commandBufferTargets.passed=false`。この fixture は
  `RenderGameOverlayEvent.Post/ALL` で **`POSITION_TEX_COLOR`（format 2）** のバッチを描き、
  target 群が**受理され順序どおり flush される**ことを検査する。フラグONでは **format 2 は present 経路の担当**に
  なるため、この検査が成立しなくなる（フレーム自体は PASSED、validation も clean）。
- **判断**: **既定化を撤回**した（フラグは残す）。理由はコードにも書いた。P0 の受入が黙って
  カバレッジを失うより、fixture を「即値経路が持つ形式」に載せ替えて**再検証してから**既定化するのが正しい。
- **撤回の検証**: 既定offに戻して target受入を再実行 → **exit 0 / validationClean / PASSED**、
  `commandBufferTargets.passed=True`。

**target fixture を載せ替えて既定化（このターン・完了）**:

1. `commandBufferQuadZ` は `POSITION_TEX_COLOR`（format 2）に**定数UV(0.5,0.5)**を付けていただけなので、
   **`POSITION_COLOR`（format 1）**に落としても**同じ色・同じ位置**で、検査している性質（target群の受理と順序）は
   変わらない。メタデータの `format`/`formatId` も更新した。
2. 載せ替えた fixture は **フラグON/OFFどちらでも合格**（`targets=True`、format=POSITION_COLOR）。
   ON: p50 15.89 ms / OFF: 18.91 ms。
3. そこで `presentFromComposite` を**既定化**（`-Dceltium.vulkan.presentFromComposite=false` で旧経路に戻せる）。
   理由と測定はコードにも書いた。

**既定化後の受入（一式・すべて緑）**:

| run | exit | validationClean | fixture | 所要 | p50 |
|---|---|---|---|---|---|
| AMD 通常 | 0 | true | **PASSED** | 24.8 s | **16.05 ms** |
| Intel 通常 | 0 | true | **PASSED** | 24.2 s | **30.94 ms** |
| postprocess (AMD) | 0 | true | PASSED | 25.0 s | 16.13 ms |
| weather (AMD) | 0 | true | PASSED | 25.3 s | 15.44 ms |
| spectator spider (AMD) | 0 | true | PASSED | 25.1 s | 17.46 ms |
| submit失敗注入 | 1 | true | FAILED（期待どおり） | 24.2 s | — |
| idle失敗注入 | 1 | true | FAILED（期待どおり） | 24.8 s | — |
| **command-buffer target受入** | 0 | true | **PASSED** | 25.5 s | 15.99 ms、`targets=True` |

- **AMD 16.05 ms は celeritas 参照（16.18 ms）を上回る**。Intel は 42.66 → **30.94 ms（27%改善）**。
- 画像の差は **1 LSB 未満の丸め**（0.12% の画素、最大 7/255、空は完全一致）であることを測定済み。
- これでフレームの色は **present 経路が所有**し、GL からの画面サイズ読み戻し（1280×720・6.2 ms/フレーム）は
  既定で**使われない**（優先順位4の前進。ただし GL context なしの起動は依然として未達）。

**次の一手**: (1) 残る `stale`（0.3 ms/フレーム）と batch call（5.4 ms/フレーム）の内訳を再取得して次のボトルネックを決める、
(2) Intel の残差（30.94 ms）が AMD（16.05）の約2倍である理由を GPU 時間で切り分ける、
(3) 失敗経路のうち texture/descriptor/allocation 失敗と minimize/world switch を両GPUで確認する。

**（旧メモ）** 次の一手: (1) target fixture の描画形式を即値経路が持つ形式（例: `POSITION_COLOR`）に載せ替え、
同じ性質（target群の受理と順序）を検査したまま再受入を取る、(2) それが通ったら `presentFromComposite` を既定化し、
AMD/Intel と主要場面・失敗経路の受入を一式取り直す。速度の利得（AMD -2.4 ms/フレーム、Intel -11.8 ms/フレーム）と
画面サイズ読み戻しの消滅（6.2 → 0.3 ms/フレーム）は測定済みで、あとは受入の付け替えだけである。

**（旧メモ）** 次の一手: (1) `presentFromComposite=true` で **Intel と主要場面の受入を取り直す**、(2) 通れば既定化して
docs/plan の受入表を更新する、(3) 併せて残る `stale`（0.3 ms/フレームまで減っている）と
batch call（5.4 ms/フレーム）の内訳を再取得する。

**（旧メモ）** 次の一手: present 経路に**合成と同じブレンドを適用**すれば、速さ（-2.4 ms/フレーム）と忠実さを両立できる。
まず MC の合成が使うブレンド式（`GlStateManager` の blend func と alpha）を実行ログの `observedBlend*` から読み、
present 側のクリア/コピーにその一段を入れる。それが出来たら既定化を再判定する。

**（旧メモ）** 次の一手: (1) `presentFromComposite=true` を**再実行して 15.88 ms を再現確認**し、差の分布も再確認する、
(2) 空が完全一致で地形だけ違うことを手がかりに、1フレーム差か丸め差かを切り分ける
（例: 静止した frameTime で2回撮る、特定 sprite の色を比べる）、(3) 差が説明できたら既定化する。

**（旧メモ）** 次の一手: (1) 1280×720 のミラーを**誰がサンプルしているか**を特定する（作成元のスタックをログに出す）、
(2) サンプル元が最終合成なら、フレームの色を native から供給する経路に置き換える、
(3) 置き換えできない場合は、このテクスチャだけ**部分更新**（ダーティ矩形）にできないか調べる。

**（旧メモ）** 次の一手: (1) glTexture=15 / 21 がどのテクスチャかを実行ログから特定し、その更新経路を announce に載せる、
(2) Intel でも残コストの内訳を同じ計器で取る、(3) 残る batch call 9.7 ms/フレームの再内訳。

**（旧メモ）** 次の一手: (1) 残る `stale`（GL再アップロード 331回・6.1 ms/フレーム）を減らす（announce 経路に寄せる）、
(2) `batch call` の残り 9.7 ms/フレームの内訳を取り直す（今回の分割計器で再測定）、
(3) Intel でも同じ修正の効果を確認する。

**（旧メモ）** 次の一手: `prepareImmediate` の**中をもう一段分割**する（`terrain.renderPass()` / キャッシュ参照 /
`descriptorSetFor`）。1バッチ 1.09 ms は map 参照と整数キー計算としては大きすぎるので、**ブロックしている処理
（ロック・GL呼び出し・大きな確保・例外生成）**が混ざっている可能性を疑い、区間ごとの計時で確かめる。

**（旧メモ）** 次の一手: `submitImmediateBatch` の**中身を分割して計時**する（preflight／append／upload／submit のどこが 1.87 ms か）。
1バッチあたり 1.87 ms は、頂点の追記とパイプライン参照だけの処理としては大きすぎるので、
**アップロードや同期（待ち）が混ざっている**可能性が高い。

**（旧メモ）** 次の一手: (1) `offerBatch` / `prepareImmediate` の wall time をバッチ単位で数える（今回と同じ計器の付け方でよい）、
(2) 即値経路が有効なときの GL 側の描画回数・`glFlush`/同期の回数を、無効時と比べる。

**（旧メモ・誤り）** 次の一手: (1) ハンドオフ回数を減らす（同一フレーム内の連続パスをまとめる。`orderPreserving`/`clearOrder` の
方針を保ったまま統合できるか）、(2) 1回のハンドオフの内訳（色blit-in／深度変換／色blit-out／同期）を timestamp で
分けて、2.5 ms のどこが重いかを出す。

**（旧メモ）** 次の一手: (1) ブリッジの色blit-in／深度変換／色blit-out にそれぞれ timestamp を打ち、1フレームあたりの
GPU時間と待ち時間を出す（既存の timestamp 機構が使える）、(2) `referencRender` と native の中間
（例: 深度変換だけ止める）で、どの段が25 msを占めるかを切り分ける。

## 8. 第3回監査の最終追補

32項目と修正後の境界は `docs/audit-2026-10-09-third.md` の「監査後の修正・検証状況」に集約する。
元Celeritasとの修正前固定比較はvalidationが両方cleanでも大きな画像差あり。GUI差が混ざるため最新版一致率として採用しない。
修正後の独立runはcapture前に終了し、一致率未取得。成功fixtureと独立再現失敗の両方を残す。
DSHのgoalは同じIDでobjectiveを更新、末尾指示を完全一致で維持。23:18前後にClinePassのprovider検証（許可deepseek、応答fireworks、strict）で自動継続がdisarmedになり、resume後も停止した。goal完了による停止ではない。
