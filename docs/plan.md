# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **0.7環境のKPI校正（2026-10-22 完了）** | **SLOWER 1.431x** | 実測 | 未測定 | 新環境（cleanroom 0.7.11-alpha / Java 27 / AMD RX 6400 / world / warm worldTicks 400..600 / 3ペア交互順）で初の校正。Celtium **1.948 / 1.904 / 1.894 ms**（jar `2721aeed…`）vs Celeritas **1.366 / 1.321 / 1.323 ms**（`567dcf8a…`）、pair ratio **1.426 / 1.441 / 1.432 ＝中央値 1.431x**（SLOWER）。warm p95 は 4.76-5.66 vs 1.99-2.09（約2.4倍）。**0.7でも参照armは動く**（`rendererKind=opengl-reference`、`referenceGlRenderer=AMD Radeon RX 6400 (radeonsi...)`）＝0.7はSDL経由でGLを持つ。旧環境（0.6/Java25）の最良1.427xとほぼ同じで、**移植による性能変化は見られない**。leg `cr07-kpi-b`（load 3.07→記録して続行、`disturbed=[]`）。**ここから先の性能作業はこの環境を基準にする** |
| **Cleanroom 0.7への移行（2026-10-22 世界leg PASSED）** | **0.7で受入legが通る** | 実測 | — | 0.7はGLFWが無く、実行時に `NoClassDefFoundError: org/lwjgl/glfw/GLFW` で全offerが落ちていた。**`CeltiumWindow`**（CleanroomのSDLを3classloaderで解決→メソッド単位で解決、`via=mod`/`shape resolved=true`をログ）を追加し、surfaceは **X11ウィンドウID＋`vkCreateXlibSurfaceKHR`** で作る（SDLの `nativeHandle()` を使用）。**`SDL_Vulkan_CreateSurface` は実測でSIGSEGV（libSDL3内、15秒）** を起こしたためX11では採用せず、Wayland用に残した。framebufferサイズ・イベントポンプ・swapchain用ウィンドウ取得も同経路へ。**実測 leg `cr07-world-10`（jar `2721aeed…`、cleanroom 0.7.11-alpha + Java 27）: `accepted=true` / `fixtureStatus=PASSED` / `verdict=PASSED`、画像gate PASSED（distinct 56,658 / nonBg 0.943）、validation 0件、世界340frame、present hookCalls 3,000超**。**注意**: このlegはvalidation ONなので**性能値ではない**。0.7環境でのKPIは未測定（環境が変わったため再校正が必要） |
| **現在のCeleritas比較（2026-10-21 実測・負荷下）** | **SLOWER 1.48x（未確定）** | 実測 | 未測定 | hash固定・warm区間・3ペア交互順、leg `ref-rotate`（jar `0253dd91…`）: Celtium **2.001 / 1.965 / 1.982 ms** vs Celeritas **1.346 / 1.360 / 1.337 ms**、pair ratio **1.487 / 1.445 / 1.483**。**ただしこのrunは負荷が高く（Celeritas自身が静穏時1.23-1.27 → 1.34-1.36＝約8%遅い）、参照比は負荷下で改善を追跡できない**（同一arm制御比較では今ラウンドも 0.955x と 0.967x の改善が出ているのに、参照比は1.454→1.483と悪化して見える）。**静穏時の実測は 1.427x**（4変更＋rotate以前）。制御armの積 0.879 から**推定 ≈1.32x**だが、これは**推定であって測定ではない**。確定には静かなマシンでの再測定が要る |
| **描画制約の確定（2026-10-21 利用者指示）** | **制約は1つだけ** | — | — | native VK-only、GL変換は**Modが直接使うGL専用**。それ以外（エンジン、状態所有、資源配置、shader、順序表現、パッケージ構成、計器、スレッド分担）は性能と正しさのため自由に置換する。旧「計器追加禁止」「per-call最適化禁止」「backend機能追加禁止」「S0以外実装禁止」「必ずmulti-draw」は撤回 |
| **VertexBuffer保持型データのnative所有（2026-10-21 実装）** | **実装済み・実機PASSED** | PASSED/clean | 未測定 | `VertexBufferMixin` が `VertexBuffer` の生成/bind/upload/draw/unbind/delete を所有し、`NativeGeometryBuffer`（CPU内容＋opaque identity）と `RetainedGeometryCache`（device別・内容世代別mesh）へ渡す。`LeasedResource` で受理済みdrawが未記録/記録中/in-flightの旧世代を保持。sky/stars/horizon/Forge cloudがGL buffer store再decodeから外れる。AMD leg `native-v3-valid-amd`（jar `25668c4e…`）で確認 |
| **terrain targetの不要な失敗計上（2026-10-21 修正）** | **修正済み・実機確認** | 確認 | 未測定 | `FrameCoordinator.Submission`（SKIPPED/RECORDED/QUEUED）を導入。native同一queue targetのRECORDEDを「GLへ同期通知できなかった失敗」と数えていた。修正前は`errors=17310`／`no submission reached the queue`が3324件、修正後は**errors=0**・当該断片0（`native-v2/v3`）。記録受理・queue送信・GPU完了を別状態として扱う |
| **Mod向けbuffer互換の一本化（2026-10-21 実装）** | **実装済み・CPUテスト済み** | 実機確認済 | 未測定 | `RenderObjectIds` に単一の名前空間（stub/hint/native）。`NativeGeometryBuffer` のidentityをraw GL adapterが解決し、bind/query・data/subdata・transient map/unmap・delete をCPU回帰で固定。native描画はGL storage mirrorを必要としない。persistent mappingは広告しない |
| **次の標的：即時グループ数を減らす（2026-10-21 実測由来の仮説）** | **原因を特定・実装は未着手** | — | — | 同一leg（`native-v3b-celtium-r1`、frame 115→9602）の累積差分から、**immediateOffered 279.8/frame・groupsDrawn 308.2/frame・windows 5.0/frame・refused 0**。週末の仕事は捨てていない。measured差 `2.10 − 1.25 = 0.85ms/frame ÷ 280 ≈ 3.0µs/group` で、既存のper-batch実測と同桁＝差はグループ数に比例する形。**どのマージ条件が効いているかを計器で特定**（下の行） |
| **KPIに順位付け専用モード（2026-10-21 実装）** | **測定設計の改善** | 実測 | — | `CELTIUM_KPI_SKIP_REFERENCE=1`（`CELTIUM_KPI_CONTROL_JARS` 必須）で参照armを省き、**candidate と control の2armだけ**を回す（9 leg → 6 leg）。参照比は「Celeritasからどれだけ離れているか」で静かなマシンが要るが、**ビルド同士の優劣は制御比だけで決まる**。負荷が高い時間帯に、この mode で初めて**ガードを通る測定**が取れた（leg `rank4`）。テスト2件（参照なしでも制御比を出す／参照なしはKPI判定を出さない） |
| **ディスク整理（2026-10-21）** | **完了** | — | — | ENOSPC（残り816K）を受け、`.gv-verify/run-*` を**187件削除して3件**（`run-fbo-cache-1`・`run-jfr-cur2-1`・`run-jfr-ref-1`）、`.gv-verify/jars` のstageを**64件削除して2件**（現行`fbo-cache-v1`と制御`affine-xform-v1`）に整理。`/tmp` のJFR dumpも削除。worktree・branchは元からmainのみ。**23G空き**を確保。証拠のうち `-manifest.json`/`-result.json`/`*-kpi-result.json`/`jfr-*-self-time.json` はrun外にあり保持 |
| **累積の改善（2026-10-21）** | **約4.8%** | 実測 | — | 制御armで測った3変更の積: identity 0.987 × census 0.980 × affine 0.985 ＝ **0.952x**。参照比も 1.508x（bindsite）→ 1.457x（identity）→ 1.439x（census）→ 1.441x（affine）と同方向。**1.481x（lambda-free）→ 1.441x**で、いずれも同一arm交互比較で裏付けられている |
| **グループが state を所有していなかった（2026-10-21 発見・修正）** | **正当性バグ修正・性能中立** | 検証PASSED | 未測定 | モデル経路 `celtium$enqueueModelMesh` は `celtium$stateScratch`（**単一の再利用配列**）をそのまま `ImmediateGroup.obtain` に渡し、グループが参照を保持していた。⇒ 後続captureが**queue済みの全モデルグループのstateを上書き**し、各グループは最後にcaptureされた状態で描かれていた。即時経路だけが `state.clone()` で回避していた。修正: グループが自分の配列を所有し代入時に `System.arraycopy`（回帰テスト3件）。**性能は3段階で測った**: 修正+`clone` = **1.504x**（leg `state-owned-b`）、`clone` を in-place arraycopy に置換 = **1.488x**（leg `state-copy`）、直前ビルド = **1.481x**（leg `lambda-free`）。⇒ **cloneの確保が約1.5pp**を占めており、それを除けば**修正前と同等（分解能1.4pp内）＝性能中立**。`state-ownerd-1`（jar `e8806303…`、validation ON）と `state-copy`（jar `e342be77…`）はいずれも画像gate PASSED・validation clean |
| **font flush の空振り（2026-10-21 計測→修正→反証）** | **修正済み・効果なし** | 実測 | — | `fontFlushCalls` と理由別内訳、`fontWork[pending/capture]` を追加して出所を特定。**主因は `celtium$fontMatrixBoundary()`**（行列変更ごとに呼ばれ、無ゲートでflush）。`celtium$hasWork()`（pending run か capture保持が無ければ払わない）でゲートした結果: **stateChange flush 3,280.1 → 9.4/frame**（全flush 3,640 → 379/frame、349倍減）、**warm p50 1.849 → 1.846ms＝変化なし**（leg `font-work-1` → `font-matgate-1`、jar `a44ae62d…`/`ca952e52…`、PASSED）。⇒ **空振りは実在したが費用ではなかった**。JFRの「`flushPendingFontBatch` 自己時間4.5%」は**インライン展開された実処理（9回/frameのrun submit＝即時描画経路全体）**を指しており、メソッド自身の文ではない。**教訓: JFRのself-timeはインラインしたcalleeを吸う**ので、leaf名だけで対象を決めない。ゲートは正しさの証明付きで残すが、性能改善としては主張しない |
| **フレーム相分解（2026-10-21 実測・KPI条件）** | **この計器の限界を確定** | 実測 | — | leg `phase-split-1`（validation OFF + `passSplit[Cpu]`、jar `d5fa0f38…`）: **frame p50 2.3 / p95 2.7ms**。**`flush` は `terrain` の内側**（`begin("terrain")`:770 → `:803` の `flushPendingBeforeGlDraw()`）＝top-level相は加算できない。正しい地図: **terrain 0.8 = flush 0.6 + terrainRecord 0.1 + 残り0.1**、残るworld約1.5はvanilla自身のループ＋受理経路。flush内は grpPrep 0.1・rcPush 0.1・rcViewport 0.1・grpDraw 0.2。**ただし passSplit は bracket ごとに `nanoTime`×2＋map lookup を払う**（実測17ns/call、`wrecLoop` 内に5 bracket＝約0.5µs/packet）。よって「3.5µs/packet のCPU」の大半は**計器自身**であり、**この相分解でサブµsの帰属は決められない**。サブµsの特定は**JFRのメソッドプロファイル**へ移す（自作bracketをこれ以上増やさない）。**その前提**: `gv_leg.py` の追加JVM引数は `-D`/`-X` のみ許可しており、JFRの `-XX:StartFlightRecording=` が通らない ⇒ harness側の許可リスト拡張が必要 |
| **テクスチャaffine比較のallocation除去（2026-10-21 実装）** | **実装済み** | 実測（uncontrolled） | 未測定 | `celtium$textureAffineMoved()` が毎offer `float[6]` を確保していた（約280/frame）。scratch配列＋6要素比較に変更し、群のsnapshot取得時だけclone。validation ONの同一条件legで warm p50 6.24→5.69ms だが**これはpaired測定ではないので改善と主張しない** |
| **state所有のnative化** | **未完（次の境界）** | — | — | GlStateManagerのproducer/queryとraw GL adapterのconsumerを同一native ownerへ。現状backendのstate snapshot/queued groupを経由しており、native側から互換状態を参照する残りがある。state push/pop・query・順序の回帰が必要 |
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
- 計測の生ログと棄却済みの詳細（`duplicate()`除去の反証、packet内訳の旧版、1.70x/1.49xの履歴）は
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)（packet loopの撤回・validation層による膨張の記録もここ）。
- 旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)、[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。
