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
| cohort terrain画像gate | 実装済み | 成功（記録） | 成功（別artifactの記録） | `gv-cohort-r30-box2` / `gv-cohort-r13-tex2-intel`。同一最新JARで両GPU再受入したとは言わない |
| E2E 1.93a terrain画像gate | 実装済み・両GPU成功（当該legs） | 成功 | 成功 | `gv-e2e-268-r16-fbo` / `gv-e2e-268-r16-intel`。black fraction 0.005081 / 0.003777、較正gate・validation clean。**完全pack互換は未完** |
| RLCraft 2.9.3 recipe terrain画像gate | 実装済み・両GPU成功（当該legs） | 成功 | 成功 | `gv-recipe-r14` / `gv-recipe-r14-intel`。**全modded scene・保存再読込・完全pack内容の受入は別**。旧runnerの補足contentコピー漏れも再開時に確認 |
| reload | 実装済み・両GPU成功（当該legs） | 成功（記録） | 成功（記録） | `gv-cohort-reload2` / `gv-cohort-reload3` |
| resize寿命（限定受入） | pair直接置換の待機漏れ修正・**当該条件は両GPU成功** | 成功 | 成功 | [resize証拠](<evidence/resize-pair-lifetime-2026-10-17.json>)。同一JARで`1280x720→1280x800`、較正キー一致・validation 0件。`resize-pair-fix1-amd/intel`。旧`resize3`のIntel FAILEDは履歴として保全。`1024x640`基準runはGUI scale差で未較正。全サイズ・minimize・loadingは別 |
| minimize / multi-size resize | iconify呼出+multi-size swapchainは両GPU成功・restore未検証 | 成功 | 成功 | `window-life1-{amd,intel}`：`resizeSchedule`で1280x720→1024x640→800x600→1280x720、3/3 transitionが1-2tickで追従・validation clean。iconifyはtick700で呼出済み（Waylandではno-op+restore不可、GLFW制約）。真のminimize→restore往復はX11/Windows待ち |
| display list / model | 実装済み（既存報告） | 既存画像報告・本監査未再評価 | 同左 | PR #288系。terrain gateを全entity/TESR画質の保証にしない |
| Sodium風メニューの半透明下敷き（利用者#3） | 修正実装済み | 既存報告・本監査未再評価 | 同左 | PR #307・[before/after画像](<evidence/sodium-tooltip-backdrop-2026-10-16.png>)。文字重なりまで独立に検証したと広げない |
| **P0 loadingの進捗/文字/resize（#1/#5/#316）** | **未完・mod起動進捗viewを実装（`CeltiumLoadingProgress`+ProgressManager mixin、leg証拠待ち）・world起動中の文字/制御バーとloading中resizeは局所確認** | 局所確認（同一JAR） | 局所確認（同一JAR） | [producer境界](<evidence/loading-producer-2026-10-17.json>)：world起動中の文字と明示的50%バーを提示PNGで確認（未較正）。自然なworld起動は`progress=-1`で数値バーを要求しない。capture消失は修正、各GPU 8/8 frame束縛・validation 0件。[resize証拠](<evidence/loading-resize-2026-10-17.json>)：baselineはresizeでloading文字を消失（`loadingScreen`再生成がtask stateを捨てる）→resize代入点限定のhandoverで保持、AMD/Intelともpost-resize titled PNGがbyte一致・validation 0件。Forge `SplashProgress`はGL-lessでは**設計上無効**（shared GL contextを`GLFW_NO_API`窓に作れず、Forge公式も`enabled=false`を推奨）—fallbackのMojangスプラッシュの提示は`loading`captureで確認済み。**mod起動中の進捗**：利用者報告「ロゴのみで進捗が見えない」→ `ProgressManager.push/pop/step`を`CeltiumProgressTransformer`（coremod `IClassTransformer`）でinstrumentしloader threadでoverlayを描画・present（`CeltiumLoadingProgress`、~30fpsスロットル、logo+bar+stage text、font/renderer未初期化のloadMods前半は暗背景+barのみ）。**mixin経路は不可能と実測判明**（hurt5）：`client`リストは登録されず、plugin動的発見の登録（~9.8s）時点でProgressManagerは既にdefined（mod discovery ~01:07:05がpushを呼ぶ）。logo読込は`getResource`ではなくclasspath直読（pack未登録期のFileNotFound）＋frame失敗時のtry/finally unbind（bind後例外→splash FBO孤児化→run全体停止を確認）。`mod-loading` stage dump追加済み（`-Dceltium.validation.dumpPresentedImage=true`でfixtureの`requestModLoadingDump`を呼ぶ）。**fixture lookupは`Class.forName`ではなく`Loader.getIndexedModList`経由**（loadMods中の`Class.forName`失敗がFoundationの`invalidClasses`にcacheされ`constructMod`もCNFE化 → hurt6-9で`Failed load class`で全run死亡を実測）。overlay描画中のfixture instanceはconstruction前はnull→次frame再試行。**loading中minimizeは未検証**（他サイズは`window-life1`でworld中3サイズ確認済み。loading中のmulti-sizeは未実施）。terrain主gateのPASSEDをloading全体の受入にしない |
| **P1 FPS / p99（#6）** | **未完・正flagで再測定・帰属は部分確定** | 診断値あり（正flag） | 診断値あり（正flag） | `presentedDumpEvery`/`frameDumpEvery`の**既定は40/60**で旧"notax"は誤キー。state2 legs（warm区間にdump無しを確認）：AMD warm p50 54.5/p99 118.9ms、Intel warm p50 74.6/p99 392ms（worldTicks 400-600）。**render bracketはAMD p50 18.0ms/Intel 28.5ms**—間隔の大半はbracket外：client tick平均15-16ms＋acquire/present/vsync待ち（未計測、AMD残差~20ms）。p99帰属：Intel最大tick=473の936ms間隔は**terrainRecord=779ms**（25 sectionsのCPU記録stall、pipeline prepare計器のみ）。再発成分はgui p95 41ms+flush p95 25ms（Intel）/gui worst 119ms（AMD、immediate decode+submit待ち）。startup tick10-40は別母集団。perf-attrib2 legsでloop分解を完走：**loop mean AMD 77ms/Intel 97ms = tick ~15-19ms + frame bracket ~18-25ms + displayUpdate ~2ms + blit ~0.3ms + fpsSync ~0.15ms + engine/JVM残差~15-30ms**（timer/schedExec/FML render handlers/sound/GL-state mirror操作/GC。`glGetError`はdrainしない）。presentは平均2msだがback-pressure時293-331msにspike→p99尾。recordPrepareがrecord内~70-85%（AMD 5.3/Intel 10.5ms p50の固定CPU、次の最適化候補）、recordBatchは軽微。Intel 779ms stallは再現せず（間歇的）。**HUD batching実測=6groups/6draws/24verts毎frame（hudCapture、warm）→gui spikeはバッチ量ではなくsubmit-wait/texture upload/GCのイベント**。**CPU-clock判別（`celtium.vulkan.passSplitCpu`、render thread cpu time）で帰属を訂正：render threadの実CPUはframe p50 AMD 10-12ms/Intel 12-13ms、recordPrepare ~1.0-1.4ms（prepBarriers ~0.8-1.0ms）。wall-clockの残差はpreemption/GC STW** — pack環境のCPU contentionがframe timeの最大成分で、renderer CPU最適化の価値は限定的。validation+診断ONのcohort計測 |
| P1 terrain CPU record / setupTerrain / particles | 計器実装済み・最適化未完 | 診断値あり | 未検証 | `recordRenderPass`内訳は候補。CPU p50と非同期GPUスナップショットだけで律速を確定しない |
| P1 panorama cost / blur（#2） | 未完 | 交絡あり | 未検証 | `panoramaTimer`の位相差を除く対照が不足。修正効果は未確定 |
| P2 雲の隙間（#4） | **画質同等は未検証** | 入力の部分観測 | 未検証 | 透明texel由来という説明は整合するが「欠陥なし確定」を撤回。producer・全形状・同条件画像一致は未証明 |
| P2 texture=506のmirror拒否 | 修正実装済み・拒否0・reload生存（#321） | 成功（両leg） | 成功（両leg） | 根因確定：506=renderer自身の`whiteTextureId()`。transformerが`glBindTexture`/`glActiveTexture`/`glDeleteTextures`をrouteせず完全no-op → 直接GL11 bindが記録されずuploadが`lastBoundTexture`に誤帰属。route追加+direct-bind serial反映で解消。連鎖修正群：(a) patch-captureがlone patchでmirrorを歪めた問題→over-cap extentをpatch backlogに保持し`uploadFromGlTexture`がregion書込みで再生（計測：atlas level-0の36,099件patchが全backlog経由、unconverted 37K→2）。(b) announce pendingで毎frame `uploadFromGlTexture`がskipされbacklog未消化→`canSkipReadback`/`applyAnnouncedTo`にbacklog gate。(c) registryのbeforeInit deadlock(35,389件)→extent記録済みpartial合法化+coverage完了→`fullyInitialized`。(d) reload後に旧name(353)の削除済みmirrorをbindし続け黒terrain 53%→`refreshTextures`でname変更時に`imageFor`再取得。(e) backlog起源のmirrorはmip level空で遠景黒12%→partial pathでper-level captureを`copyToImage`供給。`e2e-tex506p-reload-amd/intel`共にPASSED（resourceReloadAtTick=150、atlas 353→910/899再割当て生存、遠景までテクスチャ化）。no-mirror拒否0・beforeInit=0 |
| P2 Nether world-switch | Overworld→Nether→Overworld往復の実測成功（#329） | 成功（往復leg） | 成功（往復leg） | fixtureにround-trip実装（`dimensionBackAtTick`/`dimensionReturn`+dimensionBack*証拠+gateをcapture開始前限定に修正）。`e2e-dim-rt3-amd/intel`共にPASSED：Nether到達tick151→226/157 target frame・225/156 presented frame（較正済みnether gate通過）→tick370で帰還→371/372でdim0到達→17/39 return frame・16/38 presented frame、validation違反0・no-mirror拒否0、keepalive/生成制約の旧報告は往復機構とholdで解消。未カバー：save/exit/relaunch後の再転送、End(dimension 1) |
 | **P1 ja_jp誤文字（利用者報告）** | **atlas方式に置換・AMD局所確認・Intel未検証** | 局所確認 | 未検証 | page別bind・UV・mirror内容・active unitは全て正しいのに誤字が出たため、page解決経路を通る限り不確定 — 4096x4224の1枚atlasに全unicode page+asciiをCPU bake・1 textureで全文字描画に置き換え（`CeltiumFontAtlas`、commit `c11863030`）。`e2e-atlas1-amd`のvideoSettings ja_jpで 一般/垂直同期の使用/最大フレームレート/画面の揺れ が正描画（変更前は隣接漢字）。`e2e-atlas2-amd` world leg PASSED。custom sheetのFontRenderer（SGA・mod font）はgateで自前経路維持。`-Dceltium.fontAtlas=false`で旧経路。`e2e-atlas4-intel` videoSettings en_us も正描画（`nativeDeviceName=Intel(R) UHD Graphics 630`）。ja_jp-on-Intel・world内日本語看板は未検証 |
 | **P1 被弾tint未適用（利用者報告 #319）** | **root fix確定・AMD局所確認済み（`plain-hurt5-amd`：ENV_WRITE+ENV_COMBINE両発火・tick200 PNGに被弾entityの赤tint）・Intel未検証・pack内leg待ち** | 局所確認 | 未検証 | **二層の根因**：(1)`glTexEnv`(bare名)がnoop listのみでenv color常に(0,0,0,0) → SEM_TEX_ENV_FV登録で修正。(2)**決定打**：lwjglxの`GL11.glTexEnv*`が実LWJGL3の`org.lwjgl.opengl.GL11`のnativeへdelegatingし、`CeltiumGllessNativeTransformer`がその全nativeをdefault-return体に置換 → **texenv write/read共にstubへ一度も届かなかった**（`plain-hurt3/4`で`ENV_WRITE`=0・被弾zombie無色）。transformerへtexenv natives(`glTexEnvi`/`glTexEnvf`/`nglTexEnviv`/`nglTexEnvfv`/`nglGetTexEnviv`/`nglGetTexEnvfv`)→`CeltiumGllessHint`→stub静的accessorへのrouting追加で解消。`plain-hurt5-amd`：`CELTIUM_ENV_WRITE unit=1 COMBINE_RGB=INTERPOLATE` + `CELTIUM_ENV_COMBINE unit=1 rgba=1.0,0.0,0.0,0.3` が連発、tick200 PNGで被弾zombieに赤mix確認（fixture probeは`hurtEntity`：look ray 1.5m先にzombie+`hurtTime=9`維持。slimeは本worldで初tick死亡する仕様のためzombieへ変更）。`envCombineColor`はunits 0-3スキャン。スライム白化の原因は未分離 |
| P3 Cleanroom 0.7.x | 未検証 | 未検証 | 未検証 | 基準は0.6.13-alphaのまま。移行は別単位 |
| 影 / Shaderpack・配布・残り互換 | 未完 | 未完 | 未完 | 完成仕様は [goal.md](<goal.md>)。実験モジュール存在を製品受入としない |

## 性能の観測（受入・原因確定ではない）

- `gv-notax-r33-cohort`のwarm frame intervalはp50 **34.2 ms** / p95 58.1 / p99 428、menuはp50 **47.7 ms**という既存値。
  ただしeffective JVM argsは `celtium.validation.presentedDumpEvery=0`、実装は**別キー**
  `celtium.vulkan.presentedDumpEvery`。**これらのrun名とflagは「税なし」を証明しない**。数値そのものを捏造と判断する理由でもない。
- `gv-notax-r34-brack`のrender bracket p50 **15.9 ms**、terrain **8.2 ms**等はその計器の観測。
  全runのtick総時間（startupのworst **8471 ms**を含む）を割ってwarm tick約13msや「14 mods由来」と断定しない。
  CeltiumにもrunTick hookがある。全frame interval・CPU bracket・GPU timestampの区間/母集団を揃えて帰属させる。
- `gv-notax-r36-phase`: terrainRecord p50 **6.5 ms** / worst **88.3 ms**、submit **0.5 ms**、GPU debug約**2.6 ms**という既存観測。
  異なる区間の値を引き算しない。record内訳の調査は候補であって唯一の根因としない。
- cache slot増加でhit率25→27%だった既存結果は、そのscene/設定で改善を示さなかったという範囲に限る。
  `VulkanMultiDrawBatch.delete()`がno-opというソース観測から、他のallocation churnまで棄却しない。

性能測定の正しいキーと残存負荷は [開発・検証手順](<development.md>)。
新しい性能値は今回取得していない。READMEに別の現行性能表を作らない。

## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
  途中経過はローカルか既存Issue/PRへ。失敗・未較正・欠測を説明で成功に変えない。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [手順](<development.md>) /
  [計器索引](<instruments.md>) / [検証の罠](<verification-traps.md>) / [再監査](<audit-2026-10-17-sol-review.md>)。
- 旧表は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、旧日記は [diary](<history/plan-diary.md>)。
  再監査以前の「確定」「税なし」「両GPU成功」には訂正がある。履歴を現在の命令/判定として読み戻さない。
- 古い「plan §0.xxx」は現行の節ではない。[旧handoff](<handoff-2026-10-10-next-model.md>)や履歴を必要な箇所だけ参照する。
  現行判断は原典・artifact・実測結果へ戻す。
