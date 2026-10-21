# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **現在のCeleritas比較（2026-10-21 実測）** | **SLOWER 1.48x** | 実測 | 未測定 | hash固定・warm区間・3ペア交互順。Celtium `frameIntervalWarmP50` **1.850 / 1.867 / 1.872 ms**（jar `0df3439f…`）vs Celeritas **1.257 / 1.248 / 1.266 ms**（`567dcf8a…`）。pair ratio **1.472 / 1.496 / 1.479 ＝中央値 1.479x**。**p95 は 2.88 / 3.06 / 3.32 vs 1.59 / 1.55 / 1.56 ＝ 1.81 / 1.98 / 2.14倍**（当初は約3.8倍）。Celtium 4747-4823 samples、Celeritas 7517-7631。scene/camera/解像度/雲/pacing一致を機械確認。負荷 初期・最終とも `environment` に記録（bypassなし, CPU6）＝**比のみ有効**。leg `model-rel-*`、`.gv-verify/model-rel-kpi-result.json` |
| **以前の比較（2026-10-21 実測・履歴）** | **1.70x → 1.49x と推移** | 実測 | 未測定 | 同一手法・同一対照で Celtium **2.124 / 2.095 / 2.108 ms**、Celeritas **1.249 / 1.262 / 1.241 ms**、pair ratio **1.701 / 1.659 / 1.699 ＝中央値 1.699x**、p95は約3.8倍。leg `native-v3b-*`、`.gv-verify/native-v3b-kpi-result.json`。affineスコープ修正後の leg `affine-scope-*` は **1.493x**（`.gv-verify/affine-scope-kpi-result.json`）。**改善はこの3点の推移として読む** |
| **描画制約の確定（2026-10-21 利用者指示）** | **制約は1つだけ** | — | — | native VK-only、GL変換は**Modが直接使うGL専用**。それ以外（エンジン、状態所有、資源配置、shader、順序表現、パッケージ構成、計器、スレッド分担）は性能と正しさのため自由に置換する。旧「計器追加禁止」「per-call最適化禁止」「backend機能追加禁止」「S0以外実装禁止」「必ずmulti-draw」は撤回 |
| **VertexBuffer保持型データのnative所有（2026-10-21 実装）** | **実装済み・実機PASSED** | PASSED/clean | 未測定 | `VertexBufferMixin` が `VertexBuffer` の生成/bind/upload/draw/unbind/delete を所有し、`NativeGeometryBuffer`（CPU内容＋opaque identity）と `RetainedGeometryCache`（device別・内容世代別mesh）へ渡す。`LeasedResource` で受理済みdrawが未記録/記録中/in-flightの旧世代を保持。sky/stars/horizon/Forge cloudがGL buffer store再decodeから外れる。AMD leg `native-v3-valid-amd`（jar `25668c4e…`）で確認 |
| **terrain targetの不要な失敗計上（2026-10-21 修正）** | **修正済み・実機確認** | 確認 | 未測定 | `FrameCoordinator.Submission`（SKIPPED/RECORDED/QUEUED）を導入。native同一queue targetのRECORDEDを「GLへ同期通知できなかった失敗」と数えていた。修正前は`errors=17310`／`no submission reached the queue`が3324件、修正後は**errors=0**・当該断片0（`native-v2/v3`）。記録受理・queue送信・GPU完了を別状態として扱う |
| **Mod向けbuffer互換の一本化（2026-10-21 実装）** | **実装済み・CPUテスト済み** | 実機確認済 | 未測定 | `RenderObjectIds` に単一の名前空間（stub/hint/native）。`NativeGeometryBuffer` のidentityをraw GL adapterが解決し、bind/query・data/subdata・transient map/unmap・delete をCPU回帰で固定。native描画はGL storage mirrorを必要としない。persistent mappingは広告しない |
| **測定系の修復（2026-10-21）** | **実装済み・未使用** | — | — | `gv_kpi.py` が build provenance（build成功＋source revision＋sha一致）を要求し、`frameIntervalWarmP50Ms`＋`warmSampleCount>=50`＋`worldTicksAtEnd>=600`＋両armのscene/camera/resolution/clouds/pacing一致を検証。**3ペア未満・Celtium専用flag・fps上限/vsyncはNO_SPEED_VERDICT**。warm区間はearly finishするため `holdUntilTick=620` が必須 |
| **次の標的：即時グループ数を減らす（2026-10-21 実測由来の仮説）** | **原因を特定・実装は未着手** | — | — | 同一leg（`native-v3b-celtium-r1`、frame 115→9602）の累積差分から、**immediateOffered 279.8/frame・groupsDrawn 308.2/frame・windows 5.0/frame・refused 0**。週末の仕事は捨てていない。measured差 `2.10 − 1.25 = 0.85ms/frame ÷ 280 ≈ 3.0µs/group` で、既存のper-batch実測と同桁＝差はグループ数に比例する形。**どのマージ条件が効いているかを計器で特定**（下の行） |
| **グループ分割の原因はテクスチャaffine（2026-10-21 実測）** | **特定→修正済み** | 実測 | 未測定 | `ImmediateGroupSplitCensus` を追加（`groupSplits[...]`）。leg `affine-bounds-1`（jar `1b6310c3…`）: 全スプリット**610,554**（177.7/frame）の内訳は **texture_affine=431,615（71%）**、texture=81,211、format=34,116、matrix=30,819、state_key=19,137、retained=10,310、colour=3,346。scissor/pass/light/preparedは0。**affineの値域**は `affineNonIdentity=506,997/624,321`、`affineTx=[-2.144..4.348] affineTy=[-3.883..3.163] affineScale=[-1.973..2.381]`＝有界（ミラーのドリフトではない） |
| **affine分割のスコープ修正（2026-10-21 実装・実測）** | **効果確認** | 実測 | 未測定 | **原因はaffineではなくその適用範囲**だった。`POSITION_COLOR`(1)・`POSITION`(7)・`POSITION_NORMAL`(10) は**テクスチャ座標属性を持たず**、`immediate_color.vert`/`immediate_position.vert` は affine のpushスロットすら宣言していない。それでもミラーのstale値で分割していた。`VulkanImmediateFormats.samplesTexture(formatId)` で限定（slow pathとfast pathの両方、groupのcaptureもnull）。leg `affine-scope-1`（jar `f9337863…`、同一flags・validation ON）で **affineスプリット 431,615→0**、`texMatrixGroups` **147.5→2.0/frame**、グループ生成 **181.7→55.8/frame**、マージ **97.5→223.4/frame**、グループ描画 **307.5→183.0/frame**、warm p50 **5.795→4.992ms**（−14%）、warm p95 12.59→11.50、run内frame 3437→4111。両leg PASSED・validation clean。**ただし同一条件の別jar比較でありpaired KPIではない**（下の行で正式測定） |
| **バッチ種別ごとの実費（2026-10-21 実測）** | **特定済み** | 実測 | 未測定 | `-Dceltium.vulkan.batchTimings=true` + `passSplit[Cpu]`、leg `batch-timings-1`（jar `f9337863…`）: `groupDraw` **1.91-2.30ms/frame**（flushの大半）、`batchCall` 受理 0.41ms（166.8/frame＝2.5µs）、`prepareImmediate` 0.07ms（55.9/frame）、`regAnnounced` 0.17ms。**モデルenqueueは計器が無かったので追加**（`modelEnqueueMillis/Calls/MicrosPerCall`）: leg `model-enq-2` で **0.159ms/frame・1.24µs/call・127.8 call/frame**。⇒ CPU側の内訳は把握でき、最大は**draw packet数**（183/frame＝モデル128＋即時56） |
| **モデル経路の死んだ行列合成を除去（2026-10-21 実装・実測）** | **効果確認（小）** | 実測 | 未測定 | `VulkanModelQuadCapture.matrixForList(listId)` は**存在するlistに対し常に identity を clone して返す**。呼び出し側はそれを joml で `Matrix4f`×3生成＋逆行列して `V·I⁻¹` を計算し `modelViewNow` に書き戻していた＝**128回/frameの完全な無駄**。`ModelViewRelativizer`（scratch再利用・list/世代別の逆行列キャッシュ・identity短絡）へ置換し、`matrixForList` の clone も廃止。leg `model-rel-1`（jar `0df3439f…`）: モデルenqueue **0.159→0.141ms/frame（−11%）**、warm p50 5.020→4.996ms（同一flags・validation ON）。一般形（実compile行列が来た場合）は保持しユニットテストで固定。**残り1.1µs/callは状態snapshot本体**で、per-draw状態チャネル無しには削れない |
| **1 packetの最終内訳（2026-10-21 実測）** | **計測済み** | 実測 | — | leg `packet-pipe-1`（jar `ead335bf…`、`batchTimings` 有効）: 1 packet **11.25µs** = `vkCmdDrawIndexed` **3µs** / pipeline bind **2µs（packetの41%で発生・70.2回/frame）** / vertex-index bind **2µs** / push定数 **1µs** / **未計上3µs**。`packetCalls` 688,547 に対し `groupDrawCount` 730,927（5.8%はbind-at-offset経路で別カウンタ）。**未計上の3µsは我々のJavaコード**（method entry・`instanceof`連鎖・`clampedView.view()`・カウンタ）で、駆動呼び出しではない。**注意: このlegは計測で絶対値が約2.7倍に膨らむ**ので、この表の比をuninstrumentedの差へ直接掛けない（183×3µs=0.55msを実測差0.619msと一致させるのは誤り） |
| **1 packetの内訳（2026-10-21 実測・旧）** | **計測済み・原因は一部未特定** | 実測 | — | `PacketCost` を追加し `VulkanDynamicMesh.bindAndDraw` と各familyのpushを計時（`-Dceltium.vulkan.batchTimings=true`）。leg `packet-push-1`（jar `dfe0353f…`）: 1 packet **11.47µs** = push **1µs** / vertex-index bind **2µs** / `vkCmdDrawIndexed` **3µs** / **その他5.5µs**。`bindSkips` は 27.97/frame（同一buffer連続時はbindを省略済み）。**「その他5.5µs」の原因は未特定**（下の行の反証を参照）。なお計測用legは `batchTimings` 有効で絶対値が約2.7倍に膨らむ（uninstrumented warm p50 1.87ms vs 計測leg 5.16ms）ので、比と内訳だけを読む |
| **per-packet `duplicate()` 除去（2026-10-21 実装・効果なし）** | **反証** | 実測 | — | 「その他5.5µs」の最有力候補は per-packet の direct `ByteBuffer.duplicate()`（Cleaner登録を伴う確保、約172/frame）だったが、`ReusableDuplicate` で再利用化した結果 **11.47→11.41µs/packet、frameは不変**（leg `push-view-1`、jar `2ec68269…`）。⇒ **仮説を棄却**。確保自体は消えたが速度効果は無く、コード中にもそう明記した。**5.5µsは依然として説明できていない**ので、次の計測は `bindAndDraw` の外側（`vertexBuffer()`等のアクセサ、MemoryStack操作、pipeline bind判定）を分けること |
| **残差の形（2026-10-21 算術・要検証）** | **次に検証する** | 実測 | — | 現HEADのpaired差は `1.867 − 1.248 = 0.619ms/frame`。packet数は **183/frame**。割ると **3.4µs/packet**。これは「差＝packet単価×packet数」という形と整合するが、**3.4µsを独立に測った値ではない**（差を仮定して割っただけ）。したがって次は**1 packetの内訳**（pipeline bind / push constants / vertex-index bind / vkCmdDraw）を分けて測り、3.4µsが我々のCPUなのか駆動呼び出しなのかを確定する。それ無しに「packetを安くする」も「数を減らす」も選ばない |
| **モデル連続マージの上限（2026-10-21 計測・棄却）** | **棄却** | 実測 | — | モデルreplayが直前replayと**同一packet条件**（texture・stateKey・projection・modelView）かを計器化（`modelRepeatsPrev`/`modelRepeatsPercent`）。leg `model-merge-1`（jar `9c80654f…`）: **524,640 replays中84,214＝16%のみ**。⇒ 連続マージで減らせるのはモデル128→約107 packet、全体183→162（**12%減**）に過ぎない。**仮説を棄却**し、残るpacket削減は「同一状態の連続」では届かない（MDI/bindlessのように**非同一状態を跨ぐ**手段か、packet単価そのもの） |
| **残るスプリット（2026-10-21）** | **次の標的** | — | — | `affine-scope-1` の内訳: **texture=97,043（23.6/frame）**、format=40,289（9.8）、matrix=36,945（9.0）、state_key=22,488（5.5）、retained=12,332（3.0）、colour=3,882（0.9）。次は**バインドテクスチャの切替**（本物のper-draw状態）とformat変更。またグループ描画183/frameに対しグループ生成55.8/frameで**約3.3倍**＝1グループが複数passで再描画されている |
| **テクスチャaffine比較のallocation除去（2026-10-21 実装）** | **実装済み** | 実測（uncontrolled） | 未測定 | `celtium$textureAffineMoved()` が毎offer `float[6]` を確保していた（約280/frame）。scratch配列＋6要素比較に変更し、群のsnapshot取得時だけclone。validation ONの同一条件legで warm p50 6.24→5.69ms だが**これはpaired測定ではないので改善と主張しない** |
| **state所有のnative化** | **未完（次の境界）** | — | — | GlStateManagerのproducer/queryとraw GL adapterのconsumerを同一native ownerへ。現状backendのstate snapshot/queued groupを経由しており、native側から互換状態を参照する残りがある。state push/pop・query・順序の回帰が必要 |
| **texture / render target所有** | **未完** | — | — | `TextureManager`/`TextureUtil`/`Framebuffer` のpublic lifecycleと世代をnative化。reload/resize/Mod FBOで旧世代がqueued/recorded/in-flightでも安全であること、pixel対照での一致が必要 |
| **モデル/glyph/GUI/particles** | **未完** | — | — | モデル内容の保持、native material、transient arena。display-list互換の再decodeや二重copyを主経路から外す。4画面・entity/TESR対照 |
| **terrain upload とframe実行** | **未完** | — | — | dirty bytes/copy/submit/可視判定の実費分離（warm＋streaming）、worker→render owner引渡し、pass/barrierの資源依存からの編成、旧GL handoff分岐の除去 |
| **互換・目視（E2E 1.93a / RLCraft 2.9.3 / qmagnets / 4画面）** | **未完** | — | — | 完全packの代表場面。mod数や起動だけで互換を主張しない |
| **影・BSL・配布** | **未完** | — | — | native shadow mapの両GPU実画面、BSLの描画反映、配布物と実装の一致 |

## 撤回した前提（再入条件つき）

| 主張 | 判定 | 再入条件 |
|---|---|---|
| KPI 2.29x が変更後の比較 | **無効**。4 legが同一 sha `ca77e4a5…`（2026-10-20 21:34）を測定 | 今回の`gv_kpi.py`でprovenance一致を確認してから |
| 2.29x が FPS 判定 | **無効**。`frameIntervalP50Ms` はworldTicks 120..200＝チャンク構築中 | warm区間を完走したlegのみ |
| 「draw 6〜9/frame＝1:1変換なし」 | **無効**。`VulkanDynamicMesh` の2点だけの部分計数 | 全発行点の合計を計数してから |
| 「per-call最適化の余地は尽きた」 | **撤回**。同一条件のA/Bが成立していない | provenance一致のpaired測定 |
| 「GCがp95の主因」 | **撤回**。両armほぼ同一（停止数・時間） | 同一scene・同一区間での再測 |
| 「GLスタブ経由は解消済み」 | **撤回**。`CeltiumNativeDraw` はスタブを直接呼んでいた。今回owner移行を開始 | 各入口の所有者がnativeになった時点で個別に判定 |

## 未確定（数値を前提にしない）

- **Intel性能は未測定**（AMDのみ）。同一hash・同一条件のIntel paired測定を取るまで両GPU優位を主張しない。
- tail（p95）が3.8倍なのにp50が1.70倍という差は、平均的な変換コストより**スパイク源**が競争力を決めることを示す。次の判別はwarm区間のframe毎内訳とアップロード/GC/待ちの同時記録であり、推測で1つに絞らない。
- GPU pass時間、present/readback、アップロード量、GC、フレーム内訳の旧値は、artifact/区間が今回のビルドと一致しないため参考値としても使わない。
- validation clean・`exitCode=0`・色数gateは、それぞれ描画不存在・正しい絵・画像一致を証明しない。

## 更新と参照

- この表は実装・根因・受入の意味が変わった時だけ更新する。round日記・毎回のPR作成は不要。
- [作業規約](<../AGENTS.md>) / [製品目標](<goal.md>) / [設計](<architecture-2026-10-21.md>) /
  [手順](<development.md>) / [計器索引](<instruments.md>) / [検証の罠](<verification-traps.md>)。
- 旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)、[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。
