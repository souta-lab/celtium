# plan.md から移した過去の現在表行（2026-10-21）

plan.md は16KiBの入口予算を持つため、確定済み・過去の行をここへ移した。内容は移動時点のままで、現在判定は plan.md を見ること。

| 項目 | 判定 | 証拠 | Intel | 内容 |
|---|---|---|---|---|
| **`glNewList` 1 vs 29 の疑問は解消（2026-10-21）** | **計器は正常・scene依存** | 2回で再現 | 未検証 | 同一jarで2本（`gv-model1` / `gv-model1b`）とも **`glNewList=1 glEndList=1`**（`glCallList` 805 / 823）、両方 PASSED/clean。`CELTIUM_HINT_DISPLAY_LISTS` は shutdown hook の**総数**で、このsceneではモデル（`list=110` RenderPlayer）が1回だけコンパイルされ多数回再生される。`gv-dlnative1` の29は別jar・別sceneの値。**計器の欠陥ではない** |
| **`list=-1` の偽 miss を解消（2026-10-21）** | **AMD成功・実装済み** | `gv-nolist1` PASSED / clean | 未検証(Intel) | `celtium$drawModelList` の先頭で `listId <= 0` を「モデルリストではない」として即 false（GL のリスト名は1以上、0は「リスト無し」）。受入どおり **`CELTIUM_MODEL_MISSING list=-1` が 0件**（修正前は出ていた）、他に MISSING も 0件、leg PASSED/validationClean。リスト外のジオメトリは即時経路の担当であり、モデルの取りこぼしとして数えない |
| **互換: Entity Culling 1.12.2** | 未検証 | 未検証 | 未検証 | 1.12.2版が現存（EntityCulling-1.12.2-6.5.0, Forge）。可視判定のみでGL描画を持たないため設計上は共存可能だが、実機で確認する。Celtiumのスタブ縮小後も壊れないことを受入に含める |
| **immediate item pipeline の shadow binding欠落（P0・修正済み）** | 実装済み・**両GPU成功** | PASSED（`gv-fix-vid1`: VUID 0, clean=true） | PASSED（`gv-intel-vid1`: VUID 0, clean=true, UHD 630） | 根因：非lit variant の layout が binding 2/3 を宣言せず（シェーダは無条件宣言）→ `...-layout-07988` と `...-08114`。修正 `22759c413`。修正前は `gv-merge-census1` で VUID 12件・clean=false |
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
| **P1 FPS / p99（#6）** | **未完・参照比 2.22x**（`gv-kpi.sh` 3ペア中央値・負荷0.13-0.3・jar `42b8ac84`: **3.143ms** vs **1.355ms**＝**2.32x**, p95 7.85 vs 1.92） | 診断値あり | 診断値あり（旧） | **測定法**: `gv-kpi.sh`＝同一セッション3ペア中央値・遅ければexit 1（1ペアは±6%ノイズ）CENSUS`: 約31グループ/フレーム・全て独立run＝固有descriptor setのため統合不可）。次は両arm JFR比較: Celtium 792 vs Celeritas 382サンプル（**2.07倍忙しい**）。上乗せはネイティブ19.7%/スタブ5.7%/font3.3%＋`readImageRectangle`（FBO読み戻し）5.4%＝image view直接サンプルで消せる。テクスチャのバイトループは是正済|
| **native shadow map（S3）** | **両GPU成功**（`42b8ac84`: AMD/Intel 影ON validationClean、地形のshadowed率 斜め太陽54.2% vs 真上29.9%＝**光源方向に追従**、全batch描画） | 局所成功 | **局所成功** | コスト **+10%**（p50 5.58 vs 5.07ms 同一セッション） | 地形を光行列で描く→world passでサンプルが次段。S3 |
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

## 2026-10-21 計測の詳細（plan.md から移設）

| 項目 | 内容 |
|---|---|
| **以前の比較（2026-10-21 実測・履歴）** | **1.70x → 1.49x と推移** | 実測 | 未測定 | 同一手法・同一対照で Celtium **2.124 / 2.095 / 2.108 ms**、Celeritas **1.249 / 1.262 / 1.241 ms**、pair ratio **1.701 / 1.659 / 1.699 ＝中央値 1.699x**、p95は約3.8倍。leg `native-v3b-*`、`.gv-verify/native-v3b-kpi-result.json`。affineスコープ修正後の leg `affine-scope-*` は **1.493x**（`.gv-verify/affine-scope-kpi-result.json`）。**改善はこの3点の推移として読む** |
| **1 packetの内訳（2026-10-21 実測・旧）** | **計測済み・原因は一部未特定** | 実測 | — | `PacketCost` を追加し `VulkanDynamicMesh.bindAndDraw` と各familyのpushを計時（`-Dceltium.vulkan.batchTimings=true`）。leg `packet-push-1`（jar `dfe0353f…`）: 1 packet **11.47µs** = push **1µs** / vertex-index bind **2µs** / `vkCmdDrawIndexed` **3µs** / **その他5.5µs**。`bindSkips` は 27.97/frame（同一buffer連続時はbindを省略済み）。**「その他5.5µs」の原因は未特定**（下の行の反証を参照）。なお計測用legは `batchTimings` 有効で絶対値が約2.7倍に膨らむ（uninstrumented warm p50 1.87ms vs 計測leg 5.16ms）ので、比と内訳だけを読む |
| **per-packet `duplicate()` 除去（2026-10-21 実装・効果なし）** | **反証** | 実測 | — | 「その他5.5µs」の最有力候補は per-packet の direct `ByteBuffer.duplicate()`（Cleaner登録を伴う確保、約172/frame）だったが、`ReusableDuplicate` で再利用化した結果 **11.47→11.41µs/packet、frameは不変**（leg `push-view-1`、jar `2ec68269…`）。⇒ **仮説を棄却**。確保自体は消えたが速度効果は無く、コード中にもそう明記した。**5.5µsは依然として説明できていない**ので、次の計測は `bindAndDraw` の外側（`vertexBuffer()`等のアクセサ、MemoryStack操作、pipeline bind判定）を分けること |

### packet loop の撤回（2026-10-21）

| **packet loopは主因ではない（2026-10-21 訂正）** | **前提を撤回** | 実測 | — | KPI条件（**validation OFF**）で測り直すと 1 packet = **約0.76µs**（pipeline bind 0.14 / push 0.08 / vertex-index bind 0.10 / `vkCmdDrawIndexed` 0.44）。172 packet/frame で **packet loop全体 ≈ 0.13ms/frame**。**前ラウンドの「draw call 3µs・packet loopが差の本体」は撤回**：あの leg は validation layer ON で走っており、validationが全 `vkCmd*` を intercept して 2.7倍に膨らませていた（同じビルドで validation OFF の frame p50 1.915ms / ON 5.04ms）。⇒ **0.62msの差は packet loop ではない**。以後 packet 数の削減を主目標にしない |

### グループ統合の上限（2026-10-21）

| **モデル連続マージの上限（2026-10-21 計測・棄却）** | **棄却** | 実測 | — | モデルreplayが直前replayと**同一packet条件**（texture・stateKey・projection・modelView）かを計器化（`modelRepeatsPrev`/`modelRepeatsPercent`）。leg `model-merge-1`（jar `9c80654f…`）: **524,640 replays中84,214＝16%のみ**。⇒ 連続マージで減らせるのはモデル128→約107 packet、全体183→162（**12%減**）に過ぎない。**仮説を棄却**し、残るpacket削減は「同一状態の連続」では届かない（MDI/bindlessのように**非同一状態を跨ぐ**手段か、packet単価そのもの） |
| **残るスプリット（2026-10-21）** | **次の標的** | — | — | `affine-scope-1` の内訳: **texture=97,043（23.6/frame）**、format=40,289（9.8）、matrix=36,945（9.0）、state_key=22,488（5.5）、retained=12,332（3.0）、colour=3,882（0.9）。次は**バインドテクスチャの切替**（本物のper-draw状態）とformat変更。またグループ描画183/frameに対しグループ生成55.8/frameで**約3.3倍**＝1グループが複数passで再描画されている |

### バッチ実費とモデル経路（2026-10-21）

| **バッチ種別ごとの実費（2026-10-21 実測）** | **特定済み** | 実測 | 未測定 | `-Dceltium.vulkan.batchTimings=true` + `passSplit[Cpu]`、leg `batch-timings-1`（jar `f9337863…`）: `groupDraw` **1.91-2.30ms/frame**（flushの大半）、`batchCall` 受理 0.41ms（166.8/frame＝2.5µs）、`prepareImmediate` 0.07ms（55.9/frame）、`regAnnounced` 0.17ms。**モデルenqueueは計器が無かったので追加**（`modelEnqueueMillis/Calls/MicrosPerCall`）: leg `model-enq-2` で **0.159ms/frame・1.24µs/call・127.8 call/frame**。⇒ CPU側の内訳は把握でき、最大は**draw packet数**（183/frame＝モデル128＋即時56） |
| **モデル経路の死んだ行列合成を除去（2026-10-21 実装・実測）** | **効果確認（小）** | 実測 | 未測定 | `VulkanModelQuadCapture.matrixForList(listId)` は**存在するlistに対し常に identity を clone して返す**。呼び出し側はそれを joml で `Matrix4f`×3生成＋逆行列して `V·I⁻¹` を計算し `modelViewNow` に書き戻していた＝**128回/frameの完全な無駄**。`ModelViewRelativizer`（scratch再利用・list/世代別の逆行列キャッシュ・identity短絡）へ置換し、`matrixForList` の clone も廃止。leg `model-rel-1`（jar `0df3439f…`）: モデルenqueue **0.159→0.141ms/frame（−11%）**、warm p50 5.020→4.996ms（同一flags・validation ON）。一般形（実compile行列が来た場合）は保持しユニットテストで固定。**残り1.1µs/callは状態snapshot本体**で、per-draw状態チャネル無しには削れない |

### JFR初回（全体）の記録（2026-10-21）

| **JFRプロファイル（2026-10-21・次の標的）** | **計測済み** | 実測 | — | harness に `-XX:` 許可を追加してJFRを有効化。**warm区間だけ**を録るため `delay=30s,duration=20s` を使用（leg `jfr-warm-1`、jar `d5fa0f38…`、validation OFF、**1,110 samples**）。自己時間: `HashMap$TreeNode` 6.3%・`HashMap.putVal` 3.0%・`getNode` 2.3%・fastutil `Long2ObjectOpenHashMap` 2.9%＝**map操作で約14.5%**、Celtium所有では **`FontBatch.celtium$flushPendingFontBatch` 4.5%**、`submitImmediateBatchBody` 3.5%、`drawPendingImmediate` 1.0%、`GlStateManager.celtium$mirror` 1.0%、`copyVertices` 1.0%、`VulkanGlState.multiplyMatrixBuffer` 0.8%。全体プロファイル（起動込み）では font 2.69%・classgraph 1.76%・asm 1.33% で、**起動を含むと比が歪むので区間を限定した**。証拠 `.gv-verify/jfr-warm-1-self-time.json` |

### block entity の比率（2026-10-21）

| **block entity がフレームの46.6%（2026-10-21 実測）** | **視点の訂正** | 実測 | — | warm区間のJFRを**inclusive**（メソッドがスタックのどこかに現れる）で集計: `renderBlockEntities` 系が **46.6%**（`renderSectionBlockEntities`/`renderBlockEntityList` も同率）、その中で `submitGeometryBatch` 14.7%、font flush 8.7%、map操作（TreeNode/putVal/getNode/fastutil）約14%。**フレームの約半分はblock entity描画**で、我々のVulkan経路はその中の一部。この視点で見えた per-call の実害2件を修正（下の行）。**注意: inclusiveは親の分を含む**ので、これ単独で「我々のコスト」を意味しない |

### affine スコープ修正の詳細（2026-10-21）

| **グループ分割の原因はテクスチャaffine（2026-10-21 実測）** | **特定→修正済み** | 実測 | 未測定 | `ImmediateGroupSplitCensus` を追加（`groupSplits[...]`）。leg `affine-bounds-1`（jar `1b6310c3…`）: 全スプリット**610,554**（177.7/frame）の内訳は **texture_affine=431,615（71%）**、texture=81,211、format=34,116、matrix=30,819、state_key=19,137、retained=10,310、colour=3,346。scissor/pass/light/preparedは0。**affineの値域**は `affineNonIdentity=506,997/624,321`、`affineTx=[-2.144..4.348] affineTy=[-3.883..3.163] affineScale=[-1.973..2.381]`＝有界（ミラーのドリフトではない） |
| **affine分割のスコープ修正（2026-10-21 実装・実測）** | **効果確認** | 実測 | 未測定 | **原因はaffineではなくその適用範囲**だった。`POSITION_COLOR`(1)・`POSITION`(7)・`POSITION_NORMAL`(10) は**テクスチャ座標属性を持たず**、`immediate_color.vert`/`immediate_position.vert` は affine のpushスロットすら宣言していない。それでもミラーのstale値で分割していた。`VulkanImmediateFormats.samplesTexture(formatId)` で限定（slow pathとfast pathの両方、groupのcaptureもnull）。leg `affine-scope-1`（jar `f9337863…`、同一flags・validation ON）で **affineスプリット 431,615→0**、`texMatrixGroups` **147.5→2.0/frame**、グループ生成 **181.7→55.8/frame**、マージ **97.5→223.4/frame**、グループ描画 **307.5→183.0/frame**、warm p50 **5.795→4.992ms**（−14%）、warm p95 12.59→11.50、run内frame 3437→4111。両leg PASSED・validation clean。**ただし同一条件の別jar比較でありpaired KPIではない**（下の行で正式測定） |

### 計測系の修復とプロファイル（2026-10-21）

| **測定系の修復（2026-10-21）** | **実装済み・未使用** | — | — | `gv_kpi.py` が build provenance（build成功＋source revision＋sha一致）を要求し、`frameIntervalWarmP50Ms`＋`warmSampleCount>=50`＋`worldTicksAtEnd>=600`＋両armのscene/camera/resolution/clouds/pacing一致を検証。**3ペア未満・Celtium専用flag・fps上限/vsyncはNO_SPEED_VERDICT**。warm区間はearly finishするため `holdUntilTick=620` が必須 |
| **bind毎の診断map probeを除去（2026-10-21 実装）** | **実装済み・測定は未決着** | 実測 | — | `CeltiumGlStub.noteBindSite` は**診断census**（最初にbindした呼び出し元を記録）だが、毎bindで `containsKey` を払っていた。テーブル上限到達後は「満杯」しか答えないので、**サイズ判定を先に**置いてmap probeを省いた（bindはフレーム内で最も多いGL呼び出しの一つ）。leg `bindsite`（jar `4537f210…`）: pair ratio 1.510/1.508/1.465＝中央値**1.508x**、直前ビルド `state-copy` は1.488x。**判定不能**: このrunのペア内ばらつきは4.5ppで、静穏時に較正した1.4ppを超える。変更自体は**bindあたりmap probe 1回の削減**で、構造上コストになる要因は無い。⇒ 負荷交絡として未決着のまま残す（`state-copy` 1.488x と同等と読むのが妥当） |
| **per-call lambda と hot `String.contains` の除去（2026-10-21 実装・効果なし）** | **反証** | 実測 | — | JFRが `translateFloat` のlambda **2.7%**（block entity内）と `String.contains` **2.1%** を指摘したので修正: (1) ホットな translate/scale/rotate/multMatrix が**呼び出し毎にcapturing lambdaを確保**していた → 専用ヘルパへ。(2) `fn.name.contains("NamedBuffer")` を per-call 実行 → `Fn.namedObject` に事前計算。leg `lambda-free-1`（jar `3f864595…`、validation ON）は画像gate PASSED・validation clean。**paired KPI（leg `lambda-free`）: 2.079/2.056/2.024 vs 1.404/1.346/1.368 ＝ pair ratio 1.481/1.527/1.480・中央値1.481x。前回1.479xから動かず**＝**ノイズ内、効果なし**。なお今回のrunは**両armとも約10%遅い**（Celeritas 1.25→1.37）ので絶対値比較は不可、比のみ読む。⇒ 正しい変更だが速度改善ではない。**分解能の較正**: 同条件3回の3ペア中央値は 1.4928 / 1.4789 / 1.4811＝run間 1.4pp、ペア内 1.4-4.8pp ⇒ **中央値で約1-1.5%まで判別可能**（`development.md` §6 に記載）。よって今回の「4.8%」はJFRの帰属過大であり、比が動かないことと矛盾しない。これで「プロファイルのleaf名→修正→0効果」が3件連続 |
| **JFRプロファイル（2026-10-21）** | **計測済み** | 実測 | — | harnessに`-XX:`許可を追加し、**warm区間だけ**を `delay=30s,duration=20s` で録音（leg `jfr-warm-1`、1,110 samples、validation OFF）。inclusive集計で **block entity系が46.6%**、その内側に font flush・map操作・`submitGeometryBatch` 14.7%。self-time上位は map操作 約14.5%、font flush 4.5%、`submitImmediateBatchBody` 3.5%。**self-timeはインラインしたcalleeを吸う**のでleaf名だけを対象にしない（この教訓で3件連続0効果）。証拠 `.gv-verify/jfr-warm-1-self-time.json` |
| **texture / render target所有** | **未完** | — | — | `TextureManager`/`TextureUtil`/`Framebuffer` のpublic lifecycleと世代をnative化。reload/resize/Mod FBOで旧世代がqueued/recorded/in-flightでも安全であること、pixel対照での一致が必要 |
| **モデル/glyph/GUI/particles** | **未完** | — | — | モデル内容の保持、native material、transient arena。display-list互換の再decodeや二重copyを主経路から外す。4画面・entity/TESR対照 |

### 局所化と計測設計（2026-10-21）

| **残差の形（2026-10-21 算術・要検証）** | **次に検証する** | 実測 | — | 現HEADのpaired差は `1.867 − 1.248 = 0.619ms/frame`。packet数は **183/frame**。割ると **3.4µs/packet**。これは「差＝packet単価×packet数」という形と整合するが、**3.4µsを独立に測った値ではない**（差を仮定して割っただけ）。したがって次は**1 packetの内訳**（pipeline bind / push constants / vertex-index bind / vkCmdDraw）を分けて測り、3.4µsが我々のCPUなのか駆動呼び出しなのかを確定する。それ無しに「packetを安くする」も「数を減らす」も選ばない |
| **KPIに制御armを追加（2026-10-21 実装）** | **測定設計の改善** | 実測 | — | `CELTIUM_KPI_CONTROL_JARS` を渡すと各repetitionが **candidate → celeritas → control**（偶数repは逆順）で走り、**同一arm・同一窓の `medianControlRatio`** を出す。参照比は「Celeritasからどれだけ離れているか」、制御比は「このビルドはあのビルドより速いか」を答える。参照armは別レンダラで負荷応答が違うためマシンドリフトを相殺できず、これが直前5runで 1.481〜1.522 に散らばった原因だった。control側fixtureは再ビルドでbytesが変わるため同一性を要求せず、**窓の契約**（warmFrom/To・サンプル数・完走）で検証し差は `inputs.controlFixtureDiffers` に記録。テスト1件追加、`development.md` §6 に手順を記載 |
| **両arm同一JFR比較（2026-10-21 初・決定的）** | **差を局所化** | 実測 | — | 同じscene・同じflags・同じwarm区間で**Celeritas側もJFRで録った**（leg `jfr-ref-1`、参照jar `567dcf8a…`、542 samples。Celtium側 `jfr-cur-1`、1,225 samples）。**block entity描画の占有率: Celtium 46.6% / 参照 31.2%**（+15.4pp）。外側の `func_78471_a` はほぼ同じ（60.1% / 61.3%）。block entity内のleafを比べると、**参照に存在せずCeltiumだけにある項目**が並ぶ: `flushPendingFontBatch` 8.7%、`submitImmediateBatchBody` 4.8%、`putVal` 4.4%、`translateFloat` 2.7%、`copyVertices` 2.1%、`contains` 2.1%、`containsKey` 2.1%、`multiplyMatrixBuffer` 1.7%、`getIntegerValue` 1.5%。⇒ **我々がblock entity内に足している処理が全体の約14%**で、差の約43%を説明する。残りは非BE側（terrain/immediate window/font）。**これが「次にどこを削るか」の唯一の根拠**になった |

### 2026-10-21 の改善3件と旧比較（plan.md から移設）

| **translate/scaleの専用実装（2026-10-21 実装・効果確認）** | **改善 1.5%** | 実測 | — | `translate`/`scale` が毎回 identity scratchを作って**4×4行列積（64回の乗加算）**をしていたが、column-majorでは translateは**第4列だけ**、scaleは**最初の3列のスケールだけ**を書けばよい。専用実装に置換（回帰テスト2件: 一般乗算経路との一致、回転後のtranslate）。leg `affine-xform-1`（jar `44646423…`、validation ON）: 画像gate PASSED（distinct 54,665 / nonBg 0.943）、validation clean。**制御arm付きKPI（leg `affine-xform`）: 0.985/0.999/0.963＝中央値0.985x＝1.5%高速**（control=census-prim build）。参照比 **1.441x** |
| **warm profile 再取得（2026-10-21・現行ビルド）** | **BE 48.0%** | 実測 | — | leg `jfr-cur2-1`（jar `44646423…`）: block entity占有率 **48.0%**（参照31.2%）、BE内のCeltium所有leaf **31.8%**。上位は `submitImmediateBatchBody` 7.3%、`getTreeNode` 6.9%、`func_192841_a` 5.1%、`elementAt` 5.1%、`get` 4.7%、`hasNext` 4.5%、`func_181667_k` 4.3%、`submitImmediateBatchTimed` 2.6%、`multiplyMatrixBuffer` 2.2%。⇒ 次は**受理経路（`submitImmediateBatchBody`）**が最大の自前コスト |
| **診断censusのprimitive化（2026-10-21 実装・効果確認）** | **改善 2.0%** | 実測 | — | block entity内の `putVal` 4.4%・`contains/containsKey` 4.2%（参照armは0）の呼び出し元をJFRの親フレームで特定 → **`VulkanDisplayListCensus.replay`（15 samples）と `.cancelled`（10 samples）**。表示リスト再生ごとに `LinkedHashMap<Integer,Integer>` のboxing付きget/put、さらに `cancelled` では `String.contains` 3連鎖を実行していた（純粋な診断）。対応: `REPLAYS`/`CANCELLED_PER_LIST` を **fastutil `Int2IntOpenHashMap`**、`String.contains` 判定は**`originIfAbsent` で一度だけ評価して `IntOpenHashSet ITEM_LIKE`**。leg `census-prim-1`（jar `e5f6d797…`、validation ON）: 画像gate PASSED（distinct 54,668 / nonBg 0.943）、validation clean。**制御arm付きKPI（leg `census-prim`）: 0.980/0.954/0.986＝中央値0.980x＝2.0%高速**（control=identity build）。参照比 **1.439x**（Celtium 1.806/1.787/1.857 vs 1.278/1.242/1.235）で過去最良 |
| **`VulkanGlState.identity()` の確保除去（2026-10-21 実装・効果確認）** | **改善 1.3%** | 実測 | — | `identity()` が**呼び出し毎に `float[16]` を新規確保**し、`glLoadIdentity` と translate/scale/rotate の全てが経由していた。warm profileでこのメソッド自身が**フレームの3.4%**。共有 `IDENTITY` 定数＋再利用 `transformScratch` に置換（stack初期化のみ個別配列）。回帰テスト4件。leg `identity-1`（jar `5bedf1f7…`、validation ON）: 画像gate PASSED（distinct 54,702 / nonBg 0.944）、validation clean。**制御arm付きKPI（leg `id-ctl`）で確定**: candidate(identity) vs control(bindsite) 比 **0.9693 / 0.9876 / 0.9946＝中央値0.987x**＝**1.3%高速**（同一arm・同一窓・交互）。参照比は **1.457x**（1.4927/1.4452/1.4568）で、これまでの最良（1.481x）より良い |

### 受理経路の改善と計測ガード（2026-10-21）

| **matrix copyをdirty flagでgating（2026-10-21 実装・検証済み）** | **実装済み・順位付け未了** | 検証PASSED | — | 受理経路はbatch毎に projection/modelview の**16 float × 2 の arraycopy**と、merge判定での**16 float × 2 の比較**をしていた。`matricesDirty` は matrix 系の全操作（`loadIdentity`/`ortho`/`translate`/`rotate`/`scale`/`multiplyMatrix`/`pushMatrix`/`popMatrix`/`loadMatrix`）が立てるので、**変わっていないbatchではcopyをskip**できる（比較対象の値は変わらないのでmerge判定は同一）。leg `matrix-dirty-1`（jar `1795e4e6…`、validation ON）: 画像gate PASSED（distinct 54,791 / nonBg 0.943）、validation clean。**順位付けは環境に阻まれる**: 制御arm付きKPIは、新設のスタベーションガードが実データで初めて発火し `NO_SPEED_VERDICT`（r2/r3でarm間のframe数が1463 vs 6475 など）。数字は引用しない |
| **スタベーションガードの2つ目のバグ（2026-10-21 修正）** | **修正済み** | 実測 | — | 参照armは別レンダラで、同じ窓でも**1.4-1.7倍のframeを完了する**（例 6914 vs 4519）。旧ガードはarm間のframe数を比べていたため**健全なrunを毎回スタベーション認定**していた（leg `ref-md` が `NO_SPEED_VERDICT`）。⇒ 比較は**同一jar系（candidate vs control）だけ**に限定。参照との1.67倍差は正常として扱う。テストで両方向を固定（参照との1.67倍差は通す／同一系の1.7倍差は拒否） |
| **受理経路のコスト除去4件（2026-10-21 実装・順位付け完了）** | **改善 4.5%** | 実測 | — | (1) `supportedImmediateTarget()` の**batch毎 `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)`**（約280回/frame、stub経由で7.6%）→ stubの**bind serial**をキーにキャッシュ。(2) `VulkanTextureFault.shouldFail` の**毎回 `synchronized`＋文字列検索**（2.3%）→ `ARMED` がfalseなら静的boolean読みのみ。(3) 合成経路の `celtium$samplesBoundFramebufferColor` も同じキャッシュ経由に。(4) 受理経路の**projection/modelview 16float×2コピー**を `matricesDirty` でgating（matrix系全操作がフラグを立てるので、変化なしbatchはコピー不要・比較対象の値も不変）。検証: leg `matrix-dirty-1`（jar `1795e4e6…`、validation ON）画像gate PASSED（distinct 54,791 / nonBg 0.943）・validation clean。**順位付け（leg `rank4`、参照arm省略モード・3ペア交互）: 0.974/0.956/0.955＝中央値0.955x＝4.5%高速**。arm間のframe数も 4354/4288・4437/4172・4426/4165 と揃い、ガード通過 |
| **行列スタックtopのcaching（2026-10-21 試行→revert）** | **効果なし・撤回** | 実測 | — | warm profileで `ArrayDeque.elementAt`（= `top(mode)` の `stack(mode).peek()`）が**block entity内の5.1%**だったため、push/pop時にだけ更新するtop参照キャッシュを実装（ネスト・mode切替・texture unit のテスト3件追加）。**制御arm付きKPI（leg `matrix-top`）: 1.057/1.007/1.002＝中央値1.007x＝0.7%遅い**（＝誤差内、効果なし）。追加の状態（キャッシュ4本）を同期させ続けるriskに見合わないため**revert**。テストはスタック挙動の回帰として残した。**教訓: `elementAt`/`peek` の5%は「呼び出し回数」であって「コスト」ではなかった**（JFR self-timeの帰属過大、4回目） |

### 行列・受理経路の改善（2026-10-21）

| **行列比較のversion化（2026-10-21 試行→revert）** | **6.2%悪化・撤回** | 実測 | — | 受理経路のmerge判定は毎batch **32 float の配列比較**をするので、行列コピー時に進む**version番号**に置き換えれば同じ判定がint比較で済む、と考えた。**制御arm付き4ペアで 1.079 / 1.023 / 1.151 / 1.044 ＝中央値1.062x＝6.2%遅い**（全ペアで1超、arm間frame数一致）。原因: `pushMatrix`/`popMatrix` も `matricesDirty` を立てるため、**値が元に戻るpush/pop往復でもversionが進み、旧来なら統合できたbatchが分割**される。TESRは部位ごとにpush/popするので頻繁に起きる。⇒ **「触られた」は「変わった」ではない**。値比較のコストを避けるために値比較を捨てると、統合判定が壊れる。revert。教訓はコード中のコメントにも残した |
| **rotateをin-place化（2026-10-21 実装・順位付け完了）** | **改善 3.3%** | 実測 | — | `rotate` は identity scratchに回転行列を組んでから**4×4積（64乗加算）**していたが、回転の第4列は厳密に (0,0,0,1) なので**積の最初の3列だけ**が変わりうる → 36乗加算のin-place実装に（`translate`/`scale` と同じ扱い）。warm profileで `rotate` は frameの1.1%。実装中に**列の係数を取り違えるバグを自分で入れ、テストが検出**（`translate`後の回転で符号が反転）→ 修正。検証 leg `rotate-1`（jar `0253dd91…`、validation ON）: 画像gate PASSED（distinct 54,835 / nonBg 0.944）、validation clean。**順位付け leg `rank-rotate`（2armモード）: 0.968/0.936/0.983＝中央値0.967x＝3.3%高速**、arm間frame数一致（4415/4156 等） |
| **受理経路のコスト除去4件（2026-10-21 実装・順位付け完了）** | **改善 4.5%** | 実測 | — | (1) `supportedImmediateTarget()` の**batch毎 GL query**（約280/frame、受理経路の7.6%）→ stubのbind serialをキーにキャッシュ。(2) `VulkanTextureFault.shouldFail` の**毎回 synchronized＋文字列検索**（2.3%）→ 未armed時は静的boolean読みのみ。(3) 合成経路の `celtium$samplesBoundFramebufferColor` も同じキャッシュ経由。(4) 受理経路の**projection/modelview 16float×2コピー**を `matricesDirty` でgating。検証 leg `matrix-dirty-1`（jar `1795e4e6…`、validation ON）画像gate PASSED・validation clean。**順位付け leg `rank4`（参照arm省略の2armモード）: 0.974/0.956/0.955＝中央値0.955x＝4.5%高速**、arm間frame数一致 |

### 計測・履歴（2026-10-21）

| **alphaSelectorCountsのprimitive化（2026-10-21 実装・順位付け不能）** | **検証済み** | 検証PASSED | — | 描画ループはgroup毎に `LinkedHashMap.merge(Integer, Long, lambda)` で**boxing付きヒストグラム更新**をしていた（約180回/frame、計器のためのコスト）。fastutil `Int2LongOpenHashMap.addTo` に置換。leg `alpha-prim-1`（jar `ed29ec92…`、validation ON）で画像gate PASSED（distinct 54,837 / nonBg 0.943）・validation clean。**順位付けは不能**: 制御arm比 0.975 / 1.037 / 1.133（ばらつき12pp）で、同時刻のマシン負荷が高く判定に使えない。変更は仕事を減らす方向のみ（boxingとlambdaの除去） |

### JVM統一と計測（2026-10-21）

| **JVM構成の統一（2026-10-21）** | **完了（ビルドのみ25）** | 実測 | — | 利用者指示でPCのJVMを21/27へ統一。**JDK 27をTemurin 27+35で導入**し、ゲーム(cleanroom 2instanceと`celtium-world-accept`)を27へ、NIGHTFALL/FOを21へ、`default-java`と`java`/`javac`/`jar`/`jfr`を21へ。JDK 21はJREのみだったので**JDKへ入れ替え**、Prism同梱の`nightfall-java17`と`java-runtime-delta`(21.0.7)は削除。Gradle wrapperを**9.7.1→9.8.2**（9.8.0がJava 27実行対応の最初）。**実機確認**: leg `jvm-a-1`（jar `61c9d358…`）で `Java is ... version 27 ... /usr/lib/jvm/java-27-openjdk-amd64`、画像gate PASSED、validation clean。**例外はビルドのみ**: RFG 2.0.4が「JDK 25以上」を要求し（21で実行を拒否）、jvmdowngrader 2.0.1（最新）は27で`:common:downgradeJar`に失敗するため、Gradleデーモンだけ25のまま。⇒ **アプリ実行系は21/27、ビルドだけ25**。**注意: 過去の性能測定はすべてJava 25上なので、以後の数値と直接比較できない** |

### 0.7 profile詳細（2026-10-22）

| **0.7 profile（2026-10-22）と、registry primitive化のrevert** | **撤回** | 実測 | — | 0.7のprofile（leg `cr07-jfr-1`）: BE **42.3%**、上位は `submitImmediateBatchBody` **10.1%**、`func_192841_a` 6.6%、`getTreeNode` 6.6%、`elementAt` 5.0%（`ArrayDeque.peekFirst`）、`putVal` 2.2%（`HashSet.add` から）、`rotate` 2.6%。`getTreeNode` の親は `VulkanTextureRegistry.get` だったため `entries` を fastutil `Int2ObjectOpenHashMap` へ変更したが、**ビルドは通るのに実行時に `NoSuchMethodError: Int2ObjectOpenHashMap.get(java.lang.Integer)`** で即クラッシュ（leg `cr07-verify-v7`、`fixtureStatus=NO_CAPTURE`）。ジェネリクスとオーバーロード解決（`get(int)`/`get(Object)`）がビルド時と実行時で食い違う。投機的な変更なので深追いせず**revert** |
| **0.7 profileの提出経路（2026-10-22 実測）** | **フレームの約28%** | 実測 | — | JFR inclusive（leg `cr07-jfr-1`）: `submitImmediateBatch` **14.2%** + `submitGeometryBatch` **13.9%**（中身は99%が同じ受理経路）＝**提出経路がフレームの約28%**で、Celeritasとの差0.58msとほぼ一致。`renderBlockEntities` 42.3%、外側フレーム関数 58.5%、stub 3.9%、パス記録 4.4%。受理経路の内訳は self 40.5%、`expandIndices` 6.5%、`captureState` 6.5%、`closeCurrentImmediateGroup` 4.6%、`groupMaterialMatches` 3.9%、`copyVerticesTransformed` 3.9%。統計カウンタは**Atomic無し**（plain field）でここは削れる余地小 |
| **分割理由の実測（2026-10-22）** | **texture 46%** | 実測 | — | 世界340frameで offer 1,875,811・group 1,223,878・split 344,863。内訳 **texture 159,573（46%）**、format 62,887（18%）、matrix 60,574（18%）、state_key 35,103（10%）、retained 20,214（6%）、colour 6,512（2%）。ただし per-group コストは**0.17µs**（winRecord 0.6ms÷3,600 group/frame）と小さく、**分割削減だけでは差は埋まらない**＝次は「1 offerあたりの固定コスト」か「offer数を減らす構造」 |

### 0.7 profileと撤回（2026-10-22）

| **0.7 profileと分割理由（2026-10-22 実測＝次の根拠）** | **提出経路が約28%** | 実測 | — | JFR inclusive: `submitImmediateBatch` **14.2%** + `submitGeometryBatch` **13.9%**（99%が同じ受理経路）＝**フレームの約28%**で差0.58msとほぼ一致。offer 約5,517/frame、group 3,600/frame、split内訳は **texture 46%** / format 18% / matrix 18% / state_key 10% / retained 6%。**per-groupコストは0.17µs**なので分割削減では届かず、**「1 offerあたりの固定コスト」か「offer数」**が次の標的 |
| **state snapshot cache（2026-10-22 試行→revert）** | **効果なし・撤回** | 実測 | — | setterに「値が変わった時だけ進むstamp」を入れ、同一stamp/formatでstate配列とkeyを再利用（**79.3%再利用**を実測）。制御arm3ペア **1.026 / 0.999 / 1.007＝中央値1.007x＝効果なし** → 79%のcaptureを消しても差が無い＝**captureStateは律速ではない**。revert |

### GPU計器と分割（2026-10-22）

### 相分解・state所有・font flush（2026-10-21/22）

| **ディスク整理（2026-10-21）** | **完了** | — | — | ENOSPC（残り816K）を受け、`.gv-verify/run-*` を**187件削除して3件**（`run-fbo-cache-1`・`run-jfr-cur2-1`・`run-jfr-ref-1`）、`.gv-verify/jars` のstageを**64件削除して2件**（現行`fbo-cache-v1`と制御`affine-xform-v1`）に整理。`/tmp` のJFR dumpも削除。worktree・branchは元からmainのみ。**23G空き**を確保。証拠のうち `-manifest.json`/`-result.json`/`*-kpi-result.json`/`jfr-*-self-time.json` はrun外にあり保持 |
| **グループが state を所有していなかった（2026-10-21 発見・修正）** | **正当性バグ修正・性能中立** | 検証PASSED | 未測定 | モデル経路 `celtium$enqueueModelMesh` は `celtium$stateScratch`（**単一の再利用配列**）をそのまま `ImmediateGroup.obtain` に渡し、グループが参照を保持していた。⇒ 後続captureが**queue済みの全モデルグループのstateを上書き**し、各グループは最後にcaptureされた状態で描かれていた。即時経路だけが `state.clone()` で回避していた。修正: グループが自分の配列を所有し代入時に `System.arraycopy`（回帰テスト3件）。**性能は3段階で測った**: 修正+`clone` = **1.504x**（leg `state-owned-b`）、`clone` を in-place arraycopy に置換 = **1.488x**（leg `state-copy`）、直前ビルド = **1.481x**（leg `lambda-free`）。⇒ **cloneの確保が約1.5pp**を占めており、それを除けば**修正前と同等（分解能1.4pp内）＝性能中立**。`state-ownerd-1`（jar `e8806303…`、validation ON）と `state-copy`（jar `e342be77…`）はいずれも画像gate PASSED・validation clean |
| **font flush の空振り（2026-10-21 計測→修正→反証）** | **修正済み・効果なし** | 実測 | — | `fontFlushCalls` と理由別内訳、`fontWork[pending/capture]` を追加して出所を特定。**主因は `celtium$fontMatrixBoundary()`**（行列変更ごとに呼ばれ、無ゲートでflush）。`celtium$hasWork()`（pending run か capture保持が無ければ払わない）でゲートした結果: **stateChange flush 3,280.1 → 9.4/frame**（全flush 3,640 → 379/frame、349倍減）、**warm p50 1.849 → 1.846ms＝変化なし**（leg `font-work-1` → `font-matgate-1`、jar `a44ae62d…`/`ca952e52…`、PASSED）。⇒ **空振りは実在したが費用ではなかった**。JFRの「`flushPendingFontBatch` 自己時間4.5%」は**インライン展開された実処理（9回/frameのrun submit＝即時描画経路全体）**を指しており、メソッド自身の文ではない。**教訓: JFRのself-timeはインラインしたcalleeを吸う**ので、leaf名だけで対象を決めない。ゲートは正しさの証明付きで残すが、性能改善としては主張しない |
| **フレーム相分解（2026-10-21 実測・KPI条件）** | **この計器の限界を確定** | 実測 | — | leg `phase-split-1`（validation OFF + `passSplit[Cpu]`、jar `d5fa0f38…`）: **frame p50 2.3 / p95 2.7ms**。**`flush` は `terrain` の内側**（`begin("terrain")`:770 → `:803` の `flushPendingBeforeGlDraw()`）＝top-level相は加算できない。正しい地図: **terrain 0.8 = flush 0.6 + terrainRecord 0.1 + 残り0.1**、残るworld約1.5はvanilla自身のループ＋受理経路。flush内は grpPrep 0.1・rcPush 0.1・rcViewport 0.1・grpDraw 0.2。**ただし passSplit は bracket ごとに `nanoTime`×2＋map lookup を払う**（実測17ns/call、`wrecLoop` 内に5 bracket＝約0.5µs/packet）。よって「3.5µs/packet のCPU」の大半は**計器自身**であり、**この相分解でサブµsの帰属は決められない**。サブµsの特定は**JFRのメソッドプロファイル**へ移す（自作bracketをこれ以上増やさない）。**その前提**: `gv_leg.py` の追加JVM引数は `-D`/`-X` のみ許可しており、JFRの `-XX:StartFlightRecording=` が通らない ⇒ harness側の許可リスト拡張が必要 |

### vsync・メニュー場面（2026-10-22）

| **vsync設定の反映（2026-10-22 実装・検証済み）** | **修正・性能差なし** | 実測 | 改善（正しさ） | KPI leg のログが `requested=vsync-on(FIFO)` なのに fixture の報告は `vsyncEnabled=false`、逆に別legでは `chosen=MAILBOX` ＝**レンダラ生成とvsync設定の順序次第のrace**だった。押し込みを**起動時1回→変更時**に修正＋`-Dceltium.vulkan.vsync` の強制上書きを追加。**検証**: leg `cr07-vsync6`（jar `675997d5…`、validation ON）`accepted=true`/PASSED、画像gate PASSED、validation 0件、`chosen=MAILBOX`。**性能差は1.001x（無し）**＝この環境ではpresent modeは性能に効かない（3ペア制御比較 `rank-vsync`）。**訂正**: 途中の3回のleg失敗（validation JSON欠落）を私はMAILBOXのせいと誤認して一度revertしたが、真因は**ディスク満杯（残り12K）**だった。ENOSPCの後始末として `.gv-verify` を **20G→1.5G**（stage 23件・run 149件を削除、直近10runのみ保持）し、18G空きを確保 |
| **メニュー（plain）場面（2026-10-22 実測・対照つき）** | **2.30x** | 実測 | 要改善 | `plain` leg を0.7インスタンスで実行して PASSED（画像gate PASSED、distinct 2,831 / nonBg 0.803、validation clean）。**前ラウンドの「0.7でplainが落ちる」は私の使い方の誤り**: `gv_leg` の `plain` は cohort(0.6) を使う仕様で、0.7向けビルドを0.6で起動していた。**性能は参照つきで 2.30x**（Celtium 15.68ms / Celeritas 6.83ms）＝**両armとも遅い＝チャンク構築主体の場面**なので、worldの1.39xとは性質が違う。我々のCPUブラケットは frame 2.8 / setupTerrain 1.4 / chunkUpload 0.8 / terrain 0.4 / flush 0.2 で計**約2.8ms**、フレーム15msの残りはvanilla側とチャンク待ち。**worldの主戦場ではない**ので優先度は下げ、次はworld側の1.39xに戻る |

### 現行ビルドのプロファイル（2026-10-22）

| **現行ビルドのAMDプロファイル（2026-10-22）** | **受理経路26%** | 実測 | — | leg `cur-jfr-amd`（jar `675997d5…`、world、validation off）: `submitImmediateBatch` **13.2%** + `submitGeometryBatch` **12.9%**（＝同じ受理経路、地形もここを通る）+ `flushImmediateWindow` **6.5%** + `CeltiumGlStub` 4.6%、`renderBlockEntities` 41.9%。自己時間の最大は `submitImmediateBatchBody` 4.2%、次いで `copyVerticesTransformed` 0.9%、`ensureBufferedCapacity` 0.8%、`build` 0.8%、`drawPendingImmediate` 0.8%。⇒ AMDの差0.54msのうち**約0.48msが受理経路**。state captureを79%スキップしても効かなかった（既測）ので、次に削るべきは**per-offerの判定と追記の本体**（＝merge判定をflush側へ遅延させ、offerは範囲追記だけにする設計） |

### QMAGNET画像統計（2026-10-22）

| **QMAGNET(world)の両arm画像統計（2026-10-22 実測）** | **色数 54,923 vs 80,505** | 実測 | 要調査 | 同じscene・同じcamera・同じflagsの `world`（＝QMAGNET's Test Map）legで、presented画像の統計を両armで比較: **Celtium distinct 54,923 / nonBg 0.9466**（`cur-amd-celtium-r1`、status PASSED）、**Celeritas distinct 80,505 / nonBg 0.9684**（`cur-amd-celeritas-r1`、status PASSED）。**我々の絵は色数が約32%少なく、背景/単色寄りが2pp多い**。どちらも画像gateはPASSEDなので「欠陥」の証明ではない（AA・mipmap・ガンマ・雲の有無など設定差で変わる）が、**同一シーンで測れる差がある**ことは事実で、画質受入（機能別対照）の最初の定量根拠。次は差分の可視化（同一座標の画素比較） |

### 0.7移行と基準（2026-10-22）

| **現在のCeleritas比較（2026-10-22・検証済みビルド）** | **SLOWER AMD 1.407x / Intel 1.920x** | 実測 | 未達 | 現行jar **`675997d5…`** で両GPUを同日測定（0.7.11-alpha + Java 27、world、warm 400..600、3ペア交互順、参照 `567dcf8a…`）。**AMD RX 6400: Celtium 1.849 / 1.869 / 1.837 ms vs Celeritas 1.314 / 1.316 / 1.310 ＝中央値 1.407x**、**Intel UHD 630: 3.664 / 3.513 / 3.656 vs 1.873 / 1.861 / 1.904 ＝中央値 1.920x**。p95 は AMD 3.5-5.3 vs 1.9-2.0、Intel 7.1-7.7 vs 2.7-3.0。経緯: 1.699x（旧環境）→ 1.431x（0.7校正）→ **1.407x（現行）**、Intelは 4.6-5x → **1.920x**（提出バッチングのGPU別化による）。この2値が受入（両GPU ≤1.0）に対する現在地 |
| **Cleanroom 0.7への移行（2026-10-22 完了）** | **0.7で受入legが通る** | 実測 | — | 0.7はGLFW無し（実測 `NoClassDefFoundError: org/lwjgl/glfw/GLFW`）。`CeltiumWindow` がCleanroomのSDLを3classloaderで解決し、surfaceは **X11ウィンドウID＋`vkCreateXlibSurfaceKHR`**（`SDL_Vulkan_CreateSurface`は**SIGSEGV**のためX11では不採用、Wayland用に残置）。leg `cr07-world-10`（jar `2721aeed…`、0.7.11-alpha+Java27）: `accepted=true`/`PASSED`、画像gate PASSED（distinct 56,658 / nonBg 0.943）、validation 0件、世界340frame |

### offer単価の訂正（2026-10-22）

| **offer単価（2026-10-22 訂正）** | **1 offer ≈ 1.7µs** | 実測 | 要改善 | 実カウンタ（10,558 frame）: **279 offer/frame**、183 group/frame。受理経路26%（0.48ms/frame）÷279＝**1.7µs/offer**。以前の「5,517 offer/frame」は別環境の取り違えで、それに基づく「44nsだから削れない」は**誤り**だった（撤回済み）。内訳: prepare 256ns / append 248ns / groupCreate 113ns / capture 99ns / key 74ns。Celeritasの1バッチは0.2-0.3µs。 |

### prepareメモの撤回（2026-10-22）

| **prepareImmediateのメモ化（2026-10-22 試行→revert）** | **1.005x＝効果なし** | 実測 | — | 同一 (format,texture,key,lightmap,pass) なら前回の `PreparedImmediate` を返す1エントリメモ。**ヒット率25.9%**、leg PASSED。制御arm3ペア **1.005/0.980/1.006＝1.005x** → revert。**6連続で個別削減がwall時間に出ない**（capture -79%、prepare -26%、行列比較 -100% いずれも0〜0.7%）。 |

### CPU内訳の追加計測（2026-10-22）

| **`presentFromComposite=false` は測定不能（2026-10-22）** | — | 実測 | — | Intelで制御arm比較を試したが、presented dumpが壊れて `missing/inconsistent presented-dump picture evidence` で不成立。この経路は測定用としては使えない（doc にも「証拠が presented image になる」と書かれている） |
| **CPU内訳の追加計測（2026-10-22）** | **rebase 4.35k int/frame≒0.5%** | 実測 | — | offer毎のインデックス再ベース（`baseVertex`≠0で `memPutInt` のint毎ループ）を計測: **4,351 int/frame**、試算8.7µs/frame＝**0.5%**。計器は撤回（jar `675997d5…`に復帰）。**受理経路の候補はほぼ尽きた**: capture(79%)・prepare(26%)・行列比較(100%)・rebase(全部) のどれを削ってもwall時間が動かない。残る説明は**キャッシュ/メモリ局所性**で、構造変更（group境界をflushで決める）以外に手が無い |

### CPU律速の確定（2026-10-22）

| **CPU律速の確定（2026-10-22 決定的）** | **GPU律速ではない** | 実測 | 方針転換 | 640x360（画素1/4）で **1.863ms** ＝1280x720の1.85msとほぼ同じ ⇒ **CPU律速**。GPUタイマーの `wholeFrameGpu`（AMD 1.18ms）等は**待ちを含む**値で、「GPUが忙しい」と読んだのは誤り。GPU側最適化（フィルレート/シェーダ）はAMDに効かない。`CELTIUM_LEG_WINDOW_SIZE` を新設（較正は1280x720） |

### sweep試行の詳細（2026-10-22）

### sweepとネザー（2026-10-22）

| **ネザー次元（2026-10-22 実測・欠陥ではない）** | **両armとも暗い＝場面側** | 実測 | 問題なし | `-Dceltium.validation.scene=nether` だけでは次元が切り替わらない（＝私のフラグ誤用、正しくは `-Dceltium.validation.dimension=-1 -Dceltium.validation.dimensionAtTick=60`）。遷移後の presented PNG は**ほぼ真っ黒**（Celtium distinct **274**、gate FAILED、validation 0件）だったが、**同じ条件で参照arm（Celeritas）を回すと distinct **406** でやはりほぼ真っ黒・gate FAILED**（`sc-nether-ref`）。⇒ **両レンダラで同じ絵が出るので、これは我々の描画欠陥ではなくこのテストマップのネザー面が暗いだけ**。「ネザーが壊れている」という前ラウンドの主張は**撤回**。ネザーの校正下限は両armとも満たさないため、この場面では回帰の主張に使えない（Intelや別mapで再校正が必要） |
| **hot path sweep（2026-10-22）** | **ロック除去は採用／fastutil置換は不可** | 実測 | — | **採用**: `VulkanDescriptorFault.shouldFail` の `synchronized`＋文字列処理を未armed時は静的boolean読みに（texture faultと同じ形）。announce経路は空テーブル時に短絡。leg `sweep1-v`/`sweep3-v`（jar `c322d70e…`）は PASSED・画像gate PASSED・validation clean。**fastutil置換は不可と確定**: `announcedImages`/`announcedGeneration`/`consumedGeneration` をprimitive化したところ、**別クラス（CeltiumNativeDraw→VertexBuffer draw）で `NoSuchMethodError: Int2ObjectOpenHashMap.get(java.lang.Integer)`** が発生し起動不能。これは**2回目の同一故障**（以前は `VulkanTextureRegistry.entries`）。⇒ **このコードベースでは boxed `Map<Integer,X>` を fastutil に置換しない**（規則）。**構造の実測**: groups 183/frame に対し offers 279/frame ＝ **1.5 offer/group**。バッチングは描画数を34%減らすだけで、per-offer の記帳コストは払い続ける＝**コストはgroup数ではなくoffer数に比例**している |

### 両arm画像比較（2026-10-22）

| **両arm画像の比較（2026-10-22）** | **ログ帯を除けば近い** | 実測 | — | 同一カメラ・同一時刻の presented 画像（Celtium）とゲームのスクリーンショット（参照）を領域別に比較。R/B修正後: sky (165,180,207) vs (165,179,207)、stone 完全一致、grass (62,104,49) vs (61,100,47)。全体で `>32` 30%、**オンスクリーンログ帯（我々だけが出す診断文字）を除くと mean 11.4 / >32 10.6%**。残差は**ログ文字とAA/時刻位相**で、撮影経路が違う（presented vs glReadPixels）ことによる差も含む。⇒ 画質の判定は**ログ帯除外＋領域別**で行う |

### 両armのCPU律速（2026-10-22）

| **参照armもCPU律速（2026-10-22 実測）** | **両armとも解像度非依存** | 実測 | 仮説棄却 | Celeritas（GL参照）を同じ `world` sceneで 640x360 で測ると **warm p50 1.333ms / p95 1.886ms**、1280x720では 1.343-1.352ms ＝ **ほぼ同一で解像度に依らない**。⇒ **両armともCPU律速**。「高解像度・重い場面なら我々（CPU律速）が有利になる」という仮説は**棄却**。差は純粋に**CPU仕事の差**: Celtium 1.87ms vs Celeritas 1.33ms、その差 **0.53ms は 279 offer/frame の受理経路**（offer単価 1.7µs vs 0.2-0.3µs）に対応する。画素あたりのコストではないので、解像度・描画距離を変えても順位は変わらない見込み |

### presented画像のR/B反転修正（2026-10-22）

| **presented画像のR/B反転を修正（2026-10-22 重大・検証済み）** | **修正済み** | 実測 | 改善 | presented dump が**無条件に BGRA→RGBA 変換**していたが、`B8G8R8A8` を little-endian int で読むと**すでに `0xAARRGGBB`**（`setRGB` の期待形）なので**二重反転**だった。根拠: 同一カメラ・同一時刻（両arm `worldTimeAtCapture=6000`）で sky **(207,180,165) vs (165,179,207)**、stone **(36,45,61) vs (61,45,36)** ＝**R↔B だけの同じ絵**。**修正**: format 依存に（BGRA系は無変換、RGBA系のみスワップ）。検証 jar `0fdbc506…`、leg `chanfix`/`chanfix-v` PASSED・gate PASSED・validation 0。修正後 sky (165,180,207) vs (165,179,207)、stone **完全一致**、全体差 mean 55.5→**30.5**、`>32` 52%→**30%**、**ログ帯を除くと mean 11.4 / >32 10.6%**。**これ以前の presented 画像証拠（home/ネザー等）は色が入れ替わっていた**（色数統計は順序不変で影響なし） |

### Intelのフレーム内訳（2026-10-22）

| **Intelのフレーム内訳（2026-10-22 実測）** | **地形0.84ms/合成0.14ms/CPU約2.4ms** | 実測 | Intel限定の余地 | 同一フラグ・同一ビルド（jar `65ca8ad3…`）で切り分け: **基準 3.388ms** → **`refuseTerrainPass` で 2.552ms ＝ 地形パス 0.84ms（24.7%）**、**`skipGlComposite` で 3.245ms ＝ 合成 0.14ms（4.2%）**。残り**約2.4msはCPU**。**解像度**: 640x360で 2.937ms（19%減）＝減った0.7msはほぼ地形のフィル仕事と整合（AMDは平坦＝CPU律速）。⇒ IntelのGPU仕事は**地形が主**（0.84ms）で、Celeritasも同じ地形を描くため**差の主因ではない**。差＝CPU約2.4ms vs 参照の総計1.87msで、**AMDと同じ結論（受理経路）**に戻る。次は (a)CPU側の構造改善、(b)地形の早期Z/オーバードロー削減（両arm共通コストの圧縮）。**注意**: Intelの基準値はrun間で3.39-3.66とばらつくので比較は同一フラグ・複数runで |

### 提出バッチングGPU別（2026-10-22）

| **提出バッチングをGPU別に（2026-10-22 実装・大幅改善）** | **Intel 4.6-5x → 1.982x** | 実測 | 改善 | `celtium.vulkan.batchFrameSubmits=false` を**同一jar・同一sceneの制御armペア**で測ったところ、**Intel: ON 31.4/9.6/10.0ms vs OFF 3.69/3.60/3.66ms＝中央値0.374x（2.7倍高速）**、**AMD: OFF は1.102x（10%遅い）**。⇒ バッチ提出はAMDでは安く、Intelでは病理的（spreadも巨大）。**実装**: プロパティ未指定時は「**Intel以外はバッチ、Intelは非バッチ**」をデバイス名で決める（`FrameCoordinator`が`Host.isIntelDevice()`を参照）。検証: 両GPUでvalidation ON・`accepted=true`/PASSED・validation clean。**新KPI（`cr07-dev-*`、jar `647b2120…`）: AMD 1.391x（1.391/1.371/1.362）／Intel 1.982x（1.942/2.005/1.981）**。計器の副作用も修正: 追加したGPUタイムスタンプを計器ON時のみ書き、二重書きのVUID-00830（AMDで10件）を解消 |

### 行列比較ハッシュ化の撤回（2026-10-22）

| **行列比較のハッシュ化（2026-10-22 試行→revert）** | **1.000x＝効果なし** | 実測 | — | 受理経路は毎offerで**32 float（projection/modelView）の `Arrays.equals` 2回**をしていたので、行列コピー時に64bit FNVハッシュを計算して保持し（コピーはdirty時のみ）、判定を**long比較2回**に置換。**制御arm3ペア: 1.000x（1.000/1.000/0.999）＝差なし**（jar `10fa5264…` vs `675997d5…`、両legとも合格・validation clean）。衝突リスクだけ増えて利得が無いので**revert**（jarは`675997d5…`に戻り、検証済みステージとsha一致）。**結論**: 受理経路の26%は**比較やcaptureではなく、per-offerの本質的作業（頂点コピー・追記・記帳）**。micro最適化はここで打ち止め＝次は**offerを範囲追記だけにしてgroup境界判定をflushへ遅延**させる再設計しかない |

### presented画像のR/B反転修正（2026-10-22・詳細）

### 視覚受入の詳細（2026-10-22）

| **互換・目視（4画面 / E2E / RLCraft / BSL / 配布）** | **4画面取得済／影・BSLは未達** | 実測 | 一部未完 | **画面**（jar `0fdbc506…`）: loading `7b44bebe2ff6`／game(HUD) `70dfdab65878`／settings `d105e92f2866`（PASSED・gate PASSED・validation 0）／home `8ae85f058e74`（**VUID-09600が1件**）。旧画像はR/B反転のため差し替え済み。**影**: 生成・複数バッチ捕捉・光源中心=実カメラ(217.5,48,217.5)・Vulkan深度(cameraDepth 0.5)まで数値で確認。**判定を可視化する計器を追加**（`-Dceltium.shadow.debug=true` で地形シェーダが 影=赤/非影=緑 を描く、スペキュラライザ定数1）: このsceneでは**地形が画面の5.24%だけ**で、そのうち**影は0.01%（119画素）/ 非影 5.24%（48,333画素）**。⇒ **地形は可視面の主役ではない**（残り約9割は即時系＝ブロック/エンティティが描く）。地形の影をいくら直しても絵は変わらない。**即時系ライト版にも同じ判定を追加**（leg `shadowdbg2`）: **赤 119（0.01%）／緑 30,853（3.35%）**で、画像では**手前の草の帯とパネル1枚が緑**、それ以外の壁・地面は**未描画**＝**判定を出す2系統（地形・即時ライト）はこのsceneでは全域「lit」**で、画面の大半を占める壁は**定数を入れていない別系統**が描いている。証拠 `docs/evidence/cr07-shadow-verdict-amd-2026-10-22.png`（赤が1つも無い）。**残り3種（`immediate_position_tex`/`immediate_block`/`immediate_tex_color`）にも定数を追加**したが、leg `shadowdbg3` の数値は**まったく同じ（赤119/緑30,853/塗り3.35%）**＝増えていない。一方で地形のみだった初回は5.24%塗れていたので、**塗りが後続の系統に上書きされている**（＝同じ画素を複数系統が描く）。⇒ 次は**系統ごとに色を変えて塗る**（地形=青/アイテム=緑/ブロック=赤/テクスチャ=黄）ことで、「壁を最後に描いているのは誰か」を1枚の絵で確定させる。影の話はその系統が決まってから。**BSL**: パックは解析・（shadercがあれば）コンパイルまで通るが、**ワールド描画にパックのプログラムが未接続**（`probePackProgram`段階）で絵は変わらない（mean 0.31）。**互換**: RLCraft 2.9.3 は0.7非対応（`com.cleanroommc.hackery` が0.7に0エントリ）、E2E 1.93aはfull pack未導入。**配布物は未検証** |

### 記録経路の切り分け（2026-10-22）

| **記録経路の切り分けは不能（2026-10-22 試行）** | **スイッチが測定を壊す** | 実測 | — | `-Dceltium.vulkan.skipImmediateRecord=true` で測ると **warm p50 58.4ms（通常1.87msの31倍）**＝記録しないとフレームが完了せず、**帰属測定に使えない**。よって受理vs記録の分解能は BATCH_TIMINGS（prepare 256ns / append 248ns / capture 99ns / key 74ns）とプロファイル26%以上には上がらず、**§7の結論（残りの大半は名前の無いコスト）は変わらない** |

### Intelの計器制約（2026-10-22）

| **Intelの内訳計測は計器の制約に当たる（2026-10-22）** | **GPU時刻が取れない** | 実測 | 計器の制約 | Intelで `-Dceltium.gpuPassTimings=true`（正しいキー名。`.vulkan.` 付きは効かない）を付けて回しても **`CELTIUM_PASS_GPU` が1行も出ない**＝`passTimings` が空。理由はGPUタイムスタンプクエリが無いため（`timestampSlots` が0、`celtium.vulkan.forceNoTimestamps` 系の分岐）。代わりにCPU側の境界計器は動いており **`CELTIUM_BOUNDARY_SPLIT frames=600 submitMillisPerFrame=0 slotWaitMillisPerFrame=0`**＝Intelでも境界のsubmit待ちは0ms/frame。⇒ 非地形1.13msは**記録側（CPU）かドライバ内**で、次は `FramePassTimings`（recBind/recDraw等のCPUブラケット）をIntelで集計する |

### 受理経路の総括（2026-10-22）

| **受理経路の総括（2026-10-22・設計文書§7）** | **残る道は2つ** | 実測 | 未達 | 30ラウンド分の実測を `architecture` §7 に総括。受理経路は**1.7µs/offer**（279 offer/frame × 26%）、Celeritasは0.2-0.3µs。**名前の付いたコストを全部消してもフレームが動かない**（capture 79%減→0.7%、prepare 26%減→0.0%、行列比較100%減→0.0%、rebase全廃→上限0.5%）。名前の無い約1µsは5-50nsの集まりで、**本機の分解能（5-10ns/offer）以下**。⇒ 残る方針は **(1) accept経路をnativeへ移す**（`CeltiumGlStub` がGL呼び出しで実証済みの手法、見込み1.7→0.6-0.9µs、実装量大）か **(2) captureモデルを変える**（`BufferBuilder` のバッファを所有する、互換リスク大）の2つだけ。**やらないこと**も明記: group数削減・GPU側最適化・fastutil置換・1%未満の単品削減の「改善」報告 |

### Intelのバッチ提出 再検証（2026-10-22）

| **Intelのバッチ提出は現行ビルドでも病理的（2026-10-22 再検証）** | **2.8倍遅い** | 実測 | 既存ポリシーを支持 | 数ラウンド前の結論（Intelでは `batchFrameSubmits` を切る）を**現行jar `c7dd20ad…`・制御arm付き2ペア**で再検証: **ON 9.50/8.92ms（p95 47.2/44.5） vs OFF(制御) 3.15/3.20ms（p95 6.34/6.27）＝約2.8倍遅い**。⇒ **`FrameCoordinator` の「プロパティ未指定時はIntelのみ非バッチ」は正しい**（現行コードのまま維持）。バッチ提出はsubmitを減らす代わりにIntelで待ちを生むらしく、**「回数を減らせば速くなる」はIntelでは単純に成り立たない**。この反例があるため、Intel向けの回数削減は**submitではなく draw/descriptor/bind 側**で行う必要がある |

### やり取り回数の一致（2026-10-22）

| **両GPUでやり取り回数は同一（2026-10-22 確定）** | **コストは回数×単価** | 実測 | 削減対象が決まった | 既存カウンタ比較（同一jar、frame換算）: **immediateWindows 5.0 / immediateOffered 278 / immediateGroupsDrawn 182 / winSubmit 5.0 / terrainSubmit 3.0** が**IntelとAMDで完全に一致**。⇒ Intelの+1.3msは**回数ではなく1回あたりの単価**で、前ラウンドの「全ブラケット一律+0.1ms」と整合。**したがって削減対象は「回数」**で、効果は 回数 × 単価 で両GPUに比例配分される。最大の回数は **groups 182/frame（各=bind+draw）**＝ここを半減できれば Intel `grpDraw` 0.3→0.15・`wrecLoop` 0.5→0.25 で **約0.4ms（Intel）/ 0.1ms（AMD）**。次点は windows 5/frame（各 submit あり）。group数を減らすには分割要因（texture 46% / format 18% / matrix 18%）を潰す＝**bindless** が本命 |

### qmagnet機能別対照（2026-10-22）

| **qmagnet 機能別対照（2026-10-22 実測）** | **領域別に一致** | 実測 | 良好 | 同scene・同カメラ・同時刻（**worldTime=6000**、`fixedWorldTime=true` の既定）で presented画像と参照screenshotを領域別比較: **空 (165,180,207) vs (165,179,207)、石 (61,45,36) vs (61,45,36)＝完全一致、草 (62,104,49) vs (61,100,47)**。ログ帯（このレンダラだけが出す診断文字）を除いた全体差は **mean 11.4 / `>8` 10.6%**＝残差はAA・位相・撮影経路差。証拠は `docs/evidence/cr07-*`（4画面・影）。**影ON経路も現行jar `c7dd20ad…` で再検証**: `shadow-final` は **PASSED・gate PASSED・validation 0件**＝デバッグ定数（判定描画）追加後も**出荷既定の影経路は無影響** |

### Intelの差の分解（2026-10-22）

### Intelの差は全域に分散（2026-10-22）

| **Intelの1.3ms超過は全域に分散（2026-10-22 決定的）** | **ホットスポット無し** | 実測 | 方針確定 | 既存の細分ブラケットを両GPUで比較（同一jar・p50 ms）: **frame Intel 2.6 / AMD 1.3（+1.3）**。内訳の差は **terrain +0.8 / flush +0.6** だが、terrain内は**terrainRecord +0.1・recordPrepare +0.1・prepDescriptors +0.1・recDraw +0.1**、flush内は **winRecord +0.2・wrecLoop +0.2・grpDraw +0.2・winSubmit +0.2**、さらに **gui/hand/clouds が各 +0.1**。⇒ **ほぼ全てのブラケットが一律 +0.1ms**で、**単一のホットスポットは存在しない**＝Intelドライバ呼び出し1回あたりの固定オーバーヘッドが支配的。**したがってレバーは「ドライバとのやり取り回数を減らす」**（draw・submit・barrier・window数）であり、個別関数の最適化ではない。認容: 各ブラケットが+0.1msなので、10回のやり取りを削れば1ms級になる |

### 2026-10-21 に完了した実装（現在表から移動）

| **VertexBuffer保持型データのnative所有（2026-10-21 実装）** | **実装済み・実機PASSED** | PASSED/clean | 未測定 | `VertexBufferMixin` が `VertexBuffer` の生成/bind/upload/draw/unbind/delete を所有し、`NativeGeometryBuffer`（CPU内容＋opaque identity）と `RetainedGeometryCache`（device別・内容世代別mesh）へ渡す。`LeasedResource` で受理済みdrawが未記録/記録中/in-flightの旧世代を保持。sky/stars/horizon/Forge cloudがGL buffer store再decodeから外れる。AMD leg `native-v3-valid-amd`（jar `25668c4e…`）で確認 |
| **terrain targetの不要な失敗計上（2026-10-21 修正）** | **修正済み・実機確認** | 確認 | 未測定 | `FrameCoordinator.Submission`（SKIPPED/RECORDED/QUEUED）を導入。native同一queue targetのRECORDEDを「GLへ同期通知できなかった失敗」と数えていた。修正前は`errors=17310`／`no submission reached the queue`が3324件、修正後は**errors=0**・当該断片0（`native-v2/v3`）。記録受理・queue送信・GPU完了を別状態として扱う |

### テクスチャ参照キャッシュ（2026-10-22 撤回）

| **テクスチャ参照キャッシュは撤回（2026-10-22）** | **0.993x＝誤差** | 実測 | 撤回 | `VulkanTextureRegistry.get`（毎bindのHashMap参照・JFR 1.8%）に**id索引の直接マップキャッシュ**を前置して測定: 旧jar対照・交互3ペア **1.596/1.609/1.663 vs 1.623/1.621/1.551ms＝0.993x**（3ペア目は逆転）で**誤差**。さらにこのマップの**変更点は9箇所**（put5・remove2・clear）あり、実装は4箇所しか無効化しておらず**`clear()` 後は古いEntryを返す**潜在バグになる。⇒ **利得が誤差・リスクが実在なので撤回**（`git checkout`） |

### Intel関連の確定事項（2026-10-22）

| **Intel固有のコストは非地形側（2026-10-22 実測）** | **非地形で1.13ms差** | 実測 | Intel限定の余地 | 同一jar・同一scene・同一フラグで両GPUを比較（`-Dceltium.vulkan.refuseTerrainPass=true`）: **AMD 1.593-1.628 → 1.422ms（地形 約0.19ms＝12%）**、**Intel 3.14 → 2.552ms（地形 0.59-0.84ms）**。非地形どうしでは **Intel 2.55 vs AMD 1.42＝1.13ms差**で、CPUは同一マシン＝**Intel固有のGPU仕事**（合成・提示・チェーン）。`skipGlComposite` は0.14msのみなので残りは提示/blit/チェーン側。⇒ Intel比1.738xの主成分は地形ではなく**提示系** |

### モデル経路・ウィンドウ・flushの測定（2026-10-22）

| **モデル経路は世代キャッシュ済み（2026-10-22）** | **新規capture分のみ** | 実読 | 対象外 | `drawModelList` 4.9%・`ModelGeometryCache.build` 2.6%・`buildModelMeshes` 2.5% が目立つので実装を確認: **メッシュはdisplay-listの世代で再利用**（世代不変なら再構築しない）、ビルドは**新capture毎に1回**に制限（draw側がcaptureVersionを記録して1回だけ要求）。⇒ **残るのは実際に必要な仕事**。削るなら**遅延ビルド**（描くリストだけ）だが未測定。**ウィンドウ機構も中立**: `-Dceltium.vulkan.immediateWindows=false` で **1.637ms**（既定 1.586-1.642）＝差なし、gate PASSED。なお当該コードのコメントが「既定OFF」と書いていたが**実際は既定ON**（古い記述）だったので修正した。**`flushPendingBeforeGlDraw` も疑いを晴らした**: JFRで2.8%あったので「refused=0なのに毎回走っている」と疑ったが、カウンタを追加して測ると **20.0回/frame・うち保留グループありは5.0回**＝大半は空振りで安く、コストは**正当な5回のflush**（ウィンドウ境界）。**隠れた冗長呼び出しではない** |

### 影の重ね合わせ（2026-10-22 詳細）

| **影: 重ね合わせは「回転」を示す（2026-10-22・自己訂正込み）** | **転置+Y反転=9.7%** | 実測＋画像 | 計器を疑う | キャスタ画像とレシーバのuv画像を重ねて当たり率を測った（**式を明示**）: identity **0.095%**、flipX 2.635%、flipY 0.010%、transpose 1.112%、transp+flipX 6.958%、**transp+flipY 9.697%**（整列時の期待値9.6%とほぼ一致）。**過去の「flipX 12.8%」は私の索引ミス（対角線をサンプルしていた）で撤回**。最良が**90度回転**という不自然な関係なので、**レンダラの欠陥ではなく私のマップ画像（ダンプ）の向き**を疑う。一方、**フレーム内で塗る判定描画**（緑=コンテンツをサンプル）は以前 5.24% と出ており、これは**整列している側の値**。⇒ **次は判定描画の緑/赤を現行ビルドで数え直す**（フレーム内の計器は向きの疑いが無い）。緑が5%前後なら**レンダラのサンプリングは正常**で、疑うべきはダンプの向き |

### 受理経路の再設計メモ（2026-10-22）

| **受理経路の再設計（2026-10-22 設計・前提訂正）** | **転写は遅延不可** | 未実装 | — | `architecture` §6。**`vertexAddress` はゲームの`BufferBuilder`内部バッファで次のdrawで再利用**されるため**頂点転写は受理時必須**（遅延すると壊れる）＝遅延できるのは境界判定とgroup生成だけで、判定は既に安く**5%には届かない見込み**。1.7µs/offerは数十個の5-50nsに散る（**長い尾**）ので、次は**まとめたsweepを5%基準**で測る |

### 影の重ね合わせ（2026-10-22・詳細）

| **影: 重ね合わせは「回転」＝ダンプを疑う（2026-10-22）** | **transp+flipY 9.7%** | 実測＋画像 | 計器を疑う | 重ね合わせを**式を明示して**測り直し（前回の「flipX 12.8%」は**索引ミスで撤回**）: identity 0.095%、flipX 2.6%、flipY 0.01%、transpose 1.1%、transp+flipX 7.0%、**transp+flipY 9.697%**（整列時の期待9.6%と一致）。**90度回転は行列共有では説明できず、私のダンプの向きを疑う**。フレーム内の判定描画（緑＝コンテンツ）は以前5.24%＝**整列側の値**。⇒ **次は判定描画の緑/赤を現行ビルドで数え直す**（向きの疑いが無い計器で判定する） |

### 影の初期計測（2026-10-22・撤回/更新済み）

### 受入到達可能性の算術（2026-10-22）

| **受入到達可能性の算術（2026-10-22）** | **構造変更後も1.32x** | 実測＋算術 | 判断材料 | JFR定常＋passSplitから: 受理経路の**遅延可能部分はスレッド6.8%＝フレーム0.10ms**、遅延不可（capture・頂点コピー・配列比較）3.4%。**構造変更を完遂しても AMD 1.63→1.53ms＝1.32x**（受入は≤1.0）。**パリティに必要なのは0.47ms＝フレーム29%＝自分側スレッド仕事の59%**。⇒ **受理経路の内側では受入に届かない**。per-drawのJava仕事をほぼ全廃する再設計（GPU駆動）が必要で、これは最適化の範囲ではない。**group削減も上限5%**（`grpDraw` 0.3ms÷182）で同様に不足 |

### 影の3ch計器（2026-10-22・撤回済み含む）

### JFRの受理経路内訳（2026-10-22）

| **JFR: 受理経路は「本体」が重い（2026-10-22）** | **leaf内で本体5.4%** | 実測 | 構造変更のみ | AMD leg（`28ab830c…`）のJFR定常半（1,215サンプル）で `submitImmediateBatch` を含むスタックは **15.9%**。そのleaf内訳: **本体 `submitImmediateBatchBody` 5.4%**、`captureState` 1.0%、**`copyVerticesTransformed` 0.9%＋`copyVertices` 0.8%＝頂点コピー1.7%**、`ArraysSupport.mismatch` 0.7%、`closeCurrentImmediateGroupBody` 0.7%、`stateKey` 0.7%、`submitImmediateBatch` 0.6%、`expandIndices` 0.5%、HashMap.getNode 0.4%。inclusive上位は submitImmediateBatchBody 14.9%、drawModelList 4.9%、recordRenderPass 4.1%、flush系 3.0。⇒ **特定の子メソッドが重いのではなく、本体のインライン仕事が最大**（フィールド書込み・判定・境界計算）。**個別修正では削れない**ので、次は**構造**（受理時の判断をflushへ遅延、または受理を別スレッドへ）に進む。**JFR共有は2件連続でA/Bで消えた**（1.1%→0.999x、1.8%→0.993x）ので、共有値は探索にのみ使う |

### 指示と中立結果（2026-10-22）

| **利用者指示（2026-10-22・解釈訂正）** | **Java補足をやめnative実装／影は既存modベース** | 指示 | 方針 | ①**「1:1をやめろ」＝parity分析中止ではなく、Javaを1件ずつ補足するのをやめて Vulkan native で実装しろ**。per-draw/per-offerをJava側で継ぎ足す方向を第一手段にしない（受理経路のマイクロ最適化9件が全て中立だった理由）。②**「既存modベース」は影**。`docs/shader-plan.md` のとおり Vitrail/Angelica/Iris-Vulkan-Port の設計（tokenベースGLSL変換・program単位interface・target/flip管理）を土台にし、影を自前ゼロ設計しない。③性能は既存modの手法を採り入れ**実装は自前**。④RLCraftは**Fugue更新待ち**。⑤**0.0.6-alpha リリース済み**（SDL3明記・hash一致） |
| **per-offer診断の除去も効果なし（2026-10-22 実測）** | **1.015x＝わずかに遅い** | 実測 | 撤回 | 受理本体の冒頭で**毎offer無条件に走っていた診断**（`frameBatchSignature`＝ネイティブ読み2回＋乗算5回）を既定OFFにしてA/B: 旧jar `24d31f69` 対照・交互3ペアで **1.643/1.635/1.636 vs 1.625/1.607/1.612ms＝1.015x**（3ペアとも遅い）。作業を**減らしたのに遅くなった**ので、これは利得ではなく**巨大メソッドのJITレイアウト変化**とみられ、**撤回**してjarは `24d31f69` に戻した。⇒ **per-offer経路の変更は1.5%精度でA/Bしないと判断できない**（中立8件・悪化1件の実績） |
