# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **メニュー（plain）場面（2026-10-22 実測・対照つき）** | **2.30x** | 実測 | 要改善 | `plain` leg を0.7インスタンスで実行して PASSED（画像gate PASSED、distinct 2,831 / nonBg 0.803、validation clean）。**前ラウンドの「0.7でplainが落ちる」は私の使い方の誤り**: `gv_leg` の `plain` は cohort(0.6) を使う仕様で、0.7向けビルドを0.6で起動していた。**性能は参照つきで 2.30x**（Celtium 15.68ms / Celeritas 6.83ms）＝**両armとも遅い＝チャンク構築主体の場面**なので、worldの1.39xとは性質が違う。我々のCPUブラケットは frame 2.8 / setupTerrain 1.4 / chunkUpload 0.8 / terrain 0.4 / flush 0.2 で計**約2.8ms**、フレーム15msの残りはvanilla側とチャンク待ち。**worldの主戦場ではない**ので優先度は下げ、次はworld側の1.39xに戻る |
| **提出バッチングをGPU別に（2026-10-22 実装・大幅改善）** | **Intel 4.6-5x → 1.982x** | 実測 | 改善 | `celtium.vulkan.batchFrameSubmits=false` を**同一jar・同一sceneの制御armペア**で測ったところ、**Intel: ON 31.4/9.6/10.0ms vs OFF 3.69/3.60/3.66ms＝中央値0.374x（2.7倍高速）**、**AMD: OFF は1.102x（10%遅い）**。⇒ バッチ提出はAMDでは安く、Intelでは病理的（spreadも巨大）。**実装**: プロパティ未指定時は「**Intel以外はバッチ、Intelは非バッチ**」をデバイス名で決める（`FrameCoordinator`が`Host.isIntelDevice()`を参照）。検証: 両GPUでvalidation ON・`accepted=true`/PASSED・validation clean。**新KPI（`cr07-dev-*`、jar `647b2120…`）: AMD 1.391x（1.391/1.371/1.362）／Intel 1.982x（1.942/2.005/1.981）**。計器の副作用も修正: 追加したGPUタイムスタンプを計器ON時のみ書き、二重書きのVUID-00830（AMDで10件）を解消 |
| **0.7環境のKPI校正（2026-10-22 完了）** | **SLOWER 1.431x** | 実測 | 未測定 | 新環境（cleanroom 0.7.11-alpha / Java 27 / AMD RX 6400 / world / warm worldTicks 400..600 / 3ペア交互順）で初の校正。Celtium **1.948 / 1.904 / 1.894 ms**（jar `2721aeed…`）vs Celeritas **1.366 / 1.321 / 1.323 ms**（`567dcf8a…`）、pair ratio **1.426 / 1.441 / 1.432 ＝中央値 1.431x**（SLOWER）。warm p95 は 4.76-5.66 vs 1.99-2.09（約2.4倍）。**0.7でも参照armは動く**（`rendererKind=opengl-reference`、`referenceGlRenderer=AMD Radeon RX 6400 (radeonsi...)`）＝0.7はSDL経由でGLを持つ。旧環境（0.6/Java25）の最良1.427xとほぼ同じで、**移植による性能変化は見られない**。leg `cr07-kpi-b`（load 3.07→記録して続行、`disturbed=[]`）。**ここから先の性能作業はこの環境を基準にする** |
| **Cleanroom 0.7への移行（2026-10-22 完了）** | **0.7で受入legが通る** | 実測 | — | 0.7はGLFW無し（実測 `NoClassDefFoundError: org/lwjgl/glfw/GLFW`）。`CeltiumWindow` がCleanroomのSDLを3classloaderで解決し、surfaceは **X11ウィンドウID＋`vkCreateXlibSurfaceKHR`**（`SDL_Vulkan_CreateSurface`は**SIGSEGV**のためX11では不採用、Wayland用に残置）。leg `cr07-world-10`（jar `2721aeed…`、0.7.11-alpha+Java27）: `accepted=true`/`PASSED`、画像gate PASSED（distinct 56,658 / nonBg 0.943）、validation 0件、世界340frame |
| **現在のCeleritas比較（2026-10-21 実測・負荷下）** | **SLOWER 1.48x（未確定）** | 実測 | 未測定 | hash固定・warm区間・3ペア交互順、leg `ref-rotate`（jar `0253dd91…`）: Celtium **2.001 / 1.965 / 1.982 ms** vs Celeritas **1.346 / 1.360 / 1.337 ms**、pair ratio **1.487 / 1.445 / 1.483**。**ただしこのrunは負荷が高く（Celeritas自身が静穏時1.23-1.27 → 1.34-1.36＝約8%遅い）、参照比は負荷下で改善を追跡できない**（同一arm制御比較では今ラウンドも 0.955x と 0.967x の改善が出ているのに、参照比は1.454→1.483と悪化して見える）。**静穏時の実測は 1.427x**（4変更＋rotate以前）。制御armの積 0.879 から**推定 ≈1.32x**だが、これは**推定であって測定ではない**。確定には静かなマシンでの再測定が要る |
| **描画制約の確定（2026-10-21 利用者指示）** | **制約は1つだけ** | — | — | native VK-only、GL変換は**Modが直接使うGL専用**。それ以外（エンジン、状態所有、資源配置、shader、順序表現、パッケージ構成、計器、スレッド分担）は性能と正しさのため自由に置換する。旧「計器追加禁止」「per-call最適化禁止」「backend機能追加禁止」「S0以外実装禁止」「必ずmulti-draw」は撤回 |
| **VertexBuffer保持型データのnative所有（2026-10-21 実装）** | **実装済み・実機PASSED** | PASSED/clean | 未測定 | `VertexBufferMixin` が `VertexBuffer` の生成/bind/upload/draw/unbind/delete を所有し、`NativeGeometryBuffer`（CPU内容＋opaque identity）と `RetainedGeometryCache`（device別・内容世代別mesh）へ渡す。`LeasedResource` で受理済みdrawが未記録/記録中/in-flightの旧世代を保持。sky/stars/horizon/Forge cloudがGL buffer store再decodeから外れる。AMD leg `native-v3-valid-amd`（jar `25668c4e…`）で確認 |
| **terrain targetの不要な失敗計上（2026-10-21 修正）** | **修正済み・実機確認** | 確認 | 未測定 | `FrameCoordinator.Submission`（SKIPPED/RECORDED/QUEUED）を導入。native同一queue targetのRECORDEDを「GLへ同期通知できなかった失敗」と数えていた。修正前は`errors=17310`／`no submission reached the queue`が3324件、修正後は**errors=0**・当該断片0（`native-v2/v3`）。記録受理・queue送信・GPU完了を別状態として扱う |
| **Mod向けbuffer互換の一本化（2026-10-21 実装）** | **実装済み・CPUテスト済み** | 実機確認済 | 未測定 | `RenderObjectIds` に単一の名前空間（stub/hint/native）。`NativeGeometryBuffer` のidentityをraw GL adapterが解決し、bind/query・data/subdata・transient map/unmap・delete をCPU回帰で固定。native描画はGL storage mirrorを必要としない。persistent mappingは広告しない |
| **次の標的：即時グループ数を減らす（2026-10-21 実測由来の仮説）** | **原因を特定・実装は未着手** | — | — | 同一leg（`native-v3b-celtium-r1`、frame 115→9602）の累積差分から、**immediateOffered 279.8/frame・groupsDrawn 308.2/frame・windows 5.0/frame・refused 0**。週末の仕事は捨てていない。measured差 `2.10 − 1.25 = 0.85ms/frame ÷ 280 ≈ 3.0µs/group` で、既存のper-batch実測と同桁＝差はグループ数に比例する形。**どのマージ条件が効いているかを計器で特定**（下の行） |
| **KPIに順位付け専用モード（2026-10-21 実装）** | **測定設計の改善** | 実測 | — | `CELTIUM_KPI_SKIP_REFERENCE=1`（`CELTIUM_KPI_CONTROL_JARS` 必須）で参照armを省き、**candidate と control の2armだけ**を回す（9 leg → 6 leg）。参照比は「Celeritasからどれだけ離れているか」で静かなマシンが要るが、**ビルド同士の優劣は制御比だけで決まる**。負荷が高い時間帯に、この mode で初めて**ガードを通る測定**が取れた（leg `rank4`）。テスト2件（参照なしでも制御比を出す／参照なしはKPI判定を出さない） |
| **累積の改善（2026-10-21）** | **約4.8%** | 実測 | — | 制御armで測った3変更の積: identity 0.987 × census 0.980 × affine 0.985 ＝ **0.952x**。参照比も 1.508x（bindsite）→ 1.457x（identity）→ 1.439x（census）→ 1.441x（affine）と同方向。**1.481x（lambda-free）→ 1.441x**で、いずれも同一arm交互比較で裏付けられている |
| **テクスチャaffine比較のallocation除去（2026-10-21 実装）** | **実装済み** | 実測（uncontrolled） | 未測定 | `celtium$textureAffineMoved()` が毎offer `float[6]` を確保していた（約280/frame）。scratch配列＋6要素比較に変更し、群のsnapshot取得時だけclone。validation ONの同一条件legで warm p50 6.24→5.69ms だが**これはpaired測定ではないので改善と主張しない** |
| **state所有のnative化** | **未完（次の境界）** | — | — | GlStateManagerのproducer/queryとraw GL adapterのconsumerを同一native ownerへ。現状backendのstate snapshot/queued groupを経由しており、native側から互換状態を参照する残りがある。state push/pop・query・順序の回帰が必要 |
| **terrain upload とframe実行** | **未完** | — | — | dirty bytes/copy/submit/可視判定の実費分離（warm＋streaming）、worker→render owner引渡し、pass/barrierの資源依存からの編成、旧GL handoff分岐の除去 |
| **互換・目視（E2E 1.93a / RLCraft 2.9.3 / qmagnets / 4画面）** | **一部前進** | — | 未完 | 0.7環境で **メニュー(plain) PASSED**（画像gate PASSED、distinct 2,831 / nonBg 0.803）、**影: AMD/Intel両方で PASSED**（`-Dceltium.shadow.enabled=true -Dceltium.shadow.terrain=true`、validation clean、distinct AMD 54,602 / Intel 55,001・nonBg 0.942）。**未完**: 4画面(loading/home/game/settings)の原寸対照、QMAGNET、E2E 1.93a、RLCraft 2.9.3（0.7インスタンスへのpack移植が必要）、BSL、配布物 |
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
