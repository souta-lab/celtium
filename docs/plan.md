# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-17更新。再監査基点 `c46474aee`、resize追試ソース `f0e02f8a3`、loading追試ソース `438266524`（artifactは各証拠を参照）。
resize / loading行のみ新規の実機結果。他の行は従来の記録であり、一括再受入ではない。
以下は特記なき限り**各leg当時のartifactでの記録**であり、最新HEAD全体の再受入ではない。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在状態・優先順・受入

「実装済み」は実機成功と別。AMD成功 / Intel成功の両方が、同じ変更の必要な範囲で揃った場合だけ両GPU成功。
未較正は判定不能、FAILEDは失敗、未完・未検証は成功でない。
証拠の抜粋・full hash・flags・cameraは [監査証拠](<evidence/process-audit-2026-10-17.json>)。

| 優先・項目 | 実装/判定 | AMD受入 | Intel受入 | 根拠と残り |
|---|---|---|---|---|
| GL-less native Vulkan提示 | 実装済み | 成功（記録） | 成功（記録） | 下記E2E legsのnative device・提示画像。scanout未測。全描画の正しさとは別 |
| cohort terrain画像gate | 実装済み | 成功（記録） | 成功（別artifactの記録） | `reAccCohort1`（AMD, jar `e07e50986`）/ `intelCohort1`（Intel UHD 630, jar `68b6493d`, PASSED） |
| E2E 1.93a terrain画像gate | **AMD PASSED / Intel FAILED**（`e2eRegress4` black **0.0200** capture窓tick2400・`e2eAudit1` 0.0333） | **PASSED** | FAILED | `ca7cce5a`+pinSections: PASSED black 0.0438/0.0439 @8 sections（2run）。pinSections無しは13 sections（streaming途中）で black 0.055〜0.069＝FAILED。**Intel**（`intelE2E1`/`intelE2E2`, 現行jar）は**安定化captureでも** FAILED（0.0735→**0.0607**, `pinned-deadline`, stable 101, 10 sections, VUID 0）＝AMDの安定化PASSはIntelに移らない。**差は帯1（空/地平）に集中**: オーバーレイ除外でAMD 27.0% vs Intel **39.4%** black、他帯は一致＝**GPU固有**。GL参照（`e2eGlRef3`）black **0.00377** vs 我々0.044＝**安定時も約11倍の残差**（display-list census=空）。**完全pack互換も未完** |
| RLCraft 2.9.3 recipe terrain画像gate | 実装済み・両GPU成功（当該legs） | 成功 | 成功 | `reAccRecipe1`（AMD, jar `e07e50986`, VUID 0, 26 sections）/ 旧Intel leg。**全modded scene・保存再読込・完全pack内容の受入は別**。旧runnerの補足contentコピー漏れも再開時に確認 |
| reload | 実装済み・両GPU成功（当該legs） | 成功（記録） | 成功（記録） | `gv-cohort-reload2` / `gv-cohort-reload3` |
| resize寿命（限定受入） | pair直接置換の待機漏れ修正・**当該条件は両GPU成功** | 成功 | 成功 | [resize証拠](<evidence/resize-pair-lifetime-2026-10-17.json>)。同一JARで`1280x720→1280x800`、較正キー一致・validation 0件。`resize-pair-fix1-amd/intel`。旧`resize3`のIntel FAILEDは履歴として保全。`1024x640`基準runはGUI scale差で未較正。全サイズ・minimize・loadingは別 |
| minimize / multi-size resize | iconify呼出+multi-size swapchainは両GPU成功・restore未検証 | 成功 | 成功 | `window-life1-{amd,intel}`：`resizeSchedule`で1280x720→1024x640→800x600→1280x720、3/3 transitionが1-2tickで追従・validation clean。iconifyはtick700で呼出済み（Waylandではno-op+restore不可、GLFW制約）。真のminimize→restore往復はX11/Windows待ち |
| display list / model | 実装済み（既存報告） | 既存画像報告・本監査未再評価 | 同左 | PR #288系。terrain gateを全entity/TESR画質の保証にしない |
| Sodium風メニューの半透明下敷き（利用者#3） | 修正実装済み | 既存報告・本監査未再評価 | 同左 | PR #307・[before/after画像](<evidence/sodium-tooltip-backdrop-2026-10-16.png>)。文字重なりまで独立に検証したと広げない |
| **P0 loadingの進捗/文字/resize（#1/#5/#316）** | **未完・mod起動進捗overlayは2設計とも不成立（`5e3a27111`のrevert維持）・world起動中の文字/制御バーとloading中resizeは局所確認** | 局所確認 | 局所確認 | 「world起動中」は[文字](<evidence/loading-producer-2026-10-17.json>)・[resize](<evidence/loading-resize-2026-10-17.json>)で確認（未較正）。**mod起動進捗overlay（#316再試行）**：bounce Framebuffer設計はper-identity pair後も世界の地形が黒化（`loadp1/2`: `acceptedForOtherTargets` 831→34697、32,629 batchがoverlay identityへ）。bounce FBOを廃止しmain framebufferへ直接描画する設計でも黒（`loadp3`）。component bisectで**barsのみ=PASSED**（`lpbars1`）だが**font追加でloadingが~3fps化しworld未到達**（`lpfont1-3`: presentPerCall 9→24.8ms、fps throttleとfirst-frame gateで不変）。次はtext 1回≈330msの原因（loader threadでのfont atlas mirror upload疑い）を特定してから再実装。
| **P1 FPS / p99（#6）** | **未完・参照比 2.22x**（`scripts/gv-kpi.sh`, 3ペア中央値, jar `9af3e7e6`: Celtium **3.017ms** vs Celeritas **1.357ms**, ばらつき±1.4%, p95 7.38 vs 1.85） | 診断値あり | 診断値あり（旧） | **測定法**: `scripts/gv-kpi.sh TAG`＝同一セッション・同一flag・同一scene/GPUで3ペア走らせ中央値と比を出し、Celtiumが遅ければexit 1。1ペアのノイズは±6%なので単発は煙試験。**内訳**（pass split, world）: terrain 1.3ms（group loop 0.9）＋ immediate flush 1.1ms。driver呼び出しは既にbind/pushキャッシュでdedup済み、push-block cacheはA/Bで悪化（3.20 vs 3.00ms）→ 次の一手は**per-groupデータをSSBO+gl_DrawIDで1 drawに統合**するデータ経路の変更 |
| **native shadow map（S3）** | **地形をmapへ描画**（image/view/depth-only/地形indirect, 既定OFF, clean） | **両GPU成功（validation clean）／床の影は未対応**（AMD `shadowFinal2`/debug計測 地形の**9.5–16.4% shadowed**、Intel `intelShadow1` validationClean） | 局所成功 | **局所成功** | コスト **+10%**（p50 5.58 vs 5.07ms 同一セッション） | 地形を光行列で描く→world passでサンプルが次段。S3 |
| P1 terrain CPU record / setupTerrain / particles | 計器実装済み・最適化未完 | 診断値あり | 未検証 | `recordRenderPass`内訳は候補。CPU p50と非同期GPUスナップショットだけで律速を確定しない |
| P1 panorama cost / blur（#2） | **root fix確定・両GPU局所確認（`menu25-amd`/`menu25-intel`, jar `2658490f`）** | 局所確認 | 局所確認 | 根因：frame imageのalphaにdrawsのblend値が残り、window-source copyはGLの「window alpha=1」rule適用のため同期readback×7/frame＋waitForIdle。修正：(a) target=windowなら`writeMask &= ~0x8`（GL忠実＋clearのalpha-1維持）(b) `celtium.vulkan.windowCopyBlit` default ON（native blit、opt-outはA/B arm）。menu25-amd：`blits=2118`・`readbacks=1`・blur+UI全描画OK；menu25-intel：`blits=2083`・`readbacks=1`（UHD 630）。world FPSへの効果は未計測 |
| P2 雲の隙間（#4） | **画質同等は未検証** | 入力の部分観測 | 未検証 | 透明texel由来という説明は整合するが「欠陥なし確定」を撤回。producer・全形状・同条件画像一致は未証明 |
| P2 texture=506のmirror拒否 | 修正実装済み・拒否0・reload生存（#321） | 成功（両leg） | 成功（両leg） | 根因：506=renderer自身の`whiteTextureId()`（詳細は#321）未消化→`canSkipReadback`/`applyAnnouncedTo`にbacklog gate。(c) registryのbeforeInit deadlock(35,389件)→extent記録済みpartial合法化+coverage完了→`fullyInitialized`。(d) reload後に旧name(353)の削除済みmirrorをbindし続け黒terrain 53%→`refreshTextures`でname変更時に`imageFor`再取得。(e) backlog起源のmirrorはmip level空で遠景黒12%→partial pathでper-level captureを`copyToImage`供給。`e2e-tex506p-reload-amd/intel`共にPASSED（resourceReloadAtTick=150、atlas 353→910/899再割当て生存、遠景までテクスチャ化）。no-mirror拒否0・beforeInit=0 |
| P2 Nether world-switch | Overworld→Nether→Overworld往復の実測成功（#329） | 成功（往復leg） | 成功（往復leg） | fixtureにround-trip実装（`dimensionBackAtTick`/`dimensionReturn`+dimensionBack*証拠+gateをcapture開始前限定に修正）。`e2e-dim-rt3-amd/intel`共にPASSED：Nether到達tick151→226/157 target frame・225/156 presented frame（較正済みnether gate通過）→tick370で帰還→371/372でdim0到達→17/39 return frame・16/38 presented frame、validation違反0・no-mirror拒否0、keepalive/生成制約の旧報告は往復機構とholdで解消。未カバー：save/exit/relaunch後の再転送、End(dimension 1) |
 | **P1 ja_jp誤文字（利用者報告）** | **atlas方式に置換・両GPU局所確認済み。fallback page経路のmojibakeも根因確定・両GPU局所修正済み** | 局所確認 | 局所確認 | 4096x4224 atlasに全unicode page+asciiをCPU bake・1 texture描画（`CeltiumFontAtlas` `c11863030`）。`e2e-atlas1-amd` ja_jp正描画・`e2e-atlas2-amd` PASSED・`e2e-atlas4-intel` en_us正描画・`-Dceltium.fontAtlas=false`で旧経路・SGA等はgate維持。**page経路根因（menu13-24, jar `1337cca8`）**：`submitFontRun`のbindはstate modelのみで、`submitImmediateBatch`のdirect-bind supersedeが次charのpageでrun全体を上書き → 正cell・誤page。修正：run bindを`celtium$glBindTexture`経由化＋unit0強制。`menu24-amd`全ja正描画・`menu23-amd`（batching=false）正描画・`menu25-intel`でも全ja正描画（UHD 630）。world内看板は未検証 |
 | **P1 被弾tint未適用（利用者報告 #319）** | **root fix確定・両GPU局所確認（AMD `plain-hurt5-amd`+Intel `hurtI2`：ENV_WRITE/ENV_COMBINE発火・tick200提示PNGに被弾zombieの赤mix）・pack内leg待ち** | 局所確認 | 局所確認 | **二層の根因**：(1)`glTexEnv`(bare名)がnoop listのみでenv color常に(0,0,0,0)→SEM_TEX_ENV_FV登録で修正。(2)**決定打**：lwjglxの`GL11.glTexEnv*`がLWJGL3 nativeへdelegatingし、transformerが全nativeをdefault-return体に置換→texenv write/read共にstubへ届かず（`plain-hurt3/4`でENV_WRITE=0）。transformerへtexenv natives(`glTexEnvi/f`/`nglTexEnv*`/`nglGetTexEnv*`)→hint→stub routing追加で解消。`CELTIUM_ENV_COMBINE rgba=1.0,0.0,0.0,0.3`連発。Intel `hurtI2`はgpuRequirementMet+validationCleanだがworld capture gateはNO_CAPTURE（`pinSections`待ちが収束せず）で、証拠はtick200提示PNG。fixture probeは`hurtEntity`（look rayにzombie+`hurtTime=9`維持）。スライム白化の原因は未分離 |
| P3 Cleanroom 0.7.x | 未検証 | 未検証 | 未検証 | 基準は0.6.13-alphaのまま。移行は別単位 |
| 影 / Shaderpack・配布・残り互換 | 未完 | 未完 | 未完 | **frontend実装済＋ゲーム内ロード確認**: BSL 10.1.8 が実ゲームで `programs=94 dims=3 errors=0 load=364ms`（`CELTIUM_SHADERPACK`行, world leg PASSED, AMD）。**S2前進**: in-process GLSLコンパイラが実ゲームで動作＋実pack BSL 10.1.8で **graphics pipeline作成（validation message 0）**＋**packのシェーダが画素を生成**（`packOff3` offscreen `#FF0000`）＋**提示フレームにpackの画素が出た**（`packVis3` PASSED/`validationClean=true`/左下64×64が(0,255,0) 4,096px; BSLのTAA jitter文だけ除去、他はpack本人のstage）（offline `RealPackLoweringTest`; 犯人だったOptiFine注釈`//[...]`を除去）。**未完**: **地形のゲーム頂点→pack attribute適合**（画面に出たのは隅のprobe三角形）・**shadow map**・natives同梱・両GPU証明。仕様は [goal.md](<goal.md>) |
| **#352 地図がない（利用者報告）** | **vanilla held filled_mapは描画・内容正解（AMD `mapA1`+Intel `mapI1` PASSED、手持ちmap=世界の赤基盤+白点を実表示）** | 局所確認 | 局所確認 | `-Dceltium.validation.heldItem=map`（server-side `ItemMap.setupNewMap`→slot sync→`SPacketMaps`）。報告が別経路（GUI/額縁/minimap mod）なら再現が要る |
| **per-identity chain pair + glReadPixels + framebufferClear順序（#333系）** | **実装済み・fixture全項PASSED（clearOrder含む）・validation clean・plain regression PASSED** | 局所確認（`fbo-readl/m/o` fixture PASSED+`clearOrderPassed`） | 局所確認（`fbo-readq` UHD630・`gpuRequirementMet`・clean） | `GllessFrameTarget.chainPairs`（MAX 8）+identity付accessorでgame FBOごとに独立pair（leg `pairs-amd` PASSED）。readPixels=transformer→hint→stub→`TextureCopyService`（flush+`waitForIdle`+copy）。**clear-order根因**：`GlStateManager.clear` HEADのS10 flushが`glClear`前にpending群をsubmit→queue空→latchがreadbackまで延命。修正=pair存在時のstandalone clear submit（`withFrameSemaphores=false`、in-pass時`vkCmdClearAttachments`、`-Dceltium.vulkan.disableGameClearStandalone`で旧経路）。副次：pair framebuffer/renderAreaをpair extentへ（VUID-04533/04534解消）。legs `fbo-readl` fixture PASSED、`fbo-readp`/`fbo-readk` PASSED（validation 0件）。**残：flagged legの世界像が後半captureで平坦化（#353）** |

## 性能の観測（受入・原因確定ではない）

- **同fixture `frameIntervalP50`（RX6400・terrain）**: **Celeritas GL `2.5.0-dev`(`567dcf8a`、pinned `989ebf2e`ではない)=7.87ms**（`perf-cel4-amd` PASSED、2348f）vs **Celtium=32.8-37.7ms**（`loopres2/unified3-amd`）。fixture反射化(`af7ce927e`)で両側に載る。intervalはtick増幅+fixture税で自己平衡する—**renderer作業の比較はbracket**。
- **bracket内分解**（`passSplit[Cpu]`、loopres2-amd, `batchFrameSubmits`）: frame p50 ~6.2-7.9ms（中: terrain 3.3+setupTerrain 1.4+flush 1.6、wall−cpuはGC+preemption）。`f2f573de6`で~9→~6-8ms（下記unified）。
- **unified frame buffer**（flag `-Dceltium.vulkan.batchFrameSubmits`）: frame全録画を1 buffer+1 deferred submitへ。同jar A/B（`d8d4d25c`）: frame bracket 9.1→~6.2ms、queueSubmitCalls ~11.9→3.6/frame、warmP50 41.1→28.8、VUID=0（depth-bias dedupはsegment先頭でinvalidate）。present readbackがframe全実行を直列wait（+3.7ms）するのはfixture側のみ。
- **swap点分解**: present ~11.7ms/call、周期dump ~9ms（`=0`で3.4ms/call）、readback ~0.6ms（**fixture税**）効の~8割**。
- 残荷（出荷構成・入れ子に注意）: **即時パス ~2.2ms**（offer 0.7 + 記録0.95 + window固定~0.4。`terrain` 0.9の0.7は**内側の即時flush**、terrain自身は~0.2/call）、setupTerrain 0.6、gui 0.3、残りloop/present対象外）。`2× Celeritas`はintervalの自平衡性上interval指標では届かない—renderer-work比較で ~6.4 vs ~6.2ms ≈ ほぼparity。

性能測定の正しいキーと残存負荷は [開発・検証手順](<development.md>)。READMEに別の現行性能表を作らない。

## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
  途中経過はローカルか既存Issue/PRへ。失敗・未較正・欠測を説明で成功に変えない。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [手順](<development.md>) /
  [計器索引](<instruments.md>) / [検証の罠](<verification-traps.md>) / [再監査](<audit-2026-10-17-sol-review.md>)。
- 旧表は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、旧日記は [diary](<history/plan-diary.md>)。
  再監査以前の「確定」「税なし」「両GPU成功」には訂正がある。履歴を現在の命令/判定として読み戻さない。
- 古い「plan §0.xxx」は現行の節ではない。[旧handoff](<handoff-2026-10-10-next-model.md>)や履歴を必要な箇所だけ参照する。
  現行判断は原典・artifact・実測結果へ戻す。
