# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **現在のCeleritas比較（2026-10-21 実測）** | **SLOWER 1.48x** | 実測 | 未測定 | hash固定・warm区間・3ペア交互順。Celtium `frameIntervalWarmP50` **1.850 / 1.867 / 1.872 ms**（jar `0df3439f…`）vs Celeritas **1.257 / 1.248 / 1.266 ms**（`567dcf8a…`）。pair ratio **1.472 / 1.496 / 1.479 ＝中央値 1.479x**。**p95 は 2.88 / 3.06 / 3.32 vs 1.59 / 1.55 / 1.56 ＝ 1.81 / 1.98 / 2.14倍**（当初は約3.8倍）。Celtium 4747-4823 samples、Celeritas 7517-7631。scene/camera/解像度/雲/pacing一致を機械確認。負荷 初期・最終とも `environment` に記録（bypassなし, CPU6）＝**比のみ有効**。leg `model-rel-*`、`.gv-verify/model-rel-kpi-result.json` |
| **描画制約の確定（2026-10-21 利用者指示）** | **制約は1つだけ** | — | — | native VK-only、GL変換は**Modが直接使うGL専用**。それ以外（エンジン、状態所有、資源配置、shader、順序表現、パッケージ構成、計器、スレッド分担）は性能と正しさのため自由に置換する。旧「計器追加禁止」「per-call最適化禁止」「backend機能追加禁止」「S0以外実装禁止」「必ずmulti-draw」は撤回 |
| **VertexBuffer保持型データのnative所有（2026-10-21 実装）** | **実装済み・実機PASSED** | PASSED/clean | 未測定 | `VertexBufferMixin` が `VertexBuffer` の生成/bind/upload/draw/unbind/delete を所有し、`NativeGeometryBuffer`（CPU内容＋opaque identity）と `RetainedGeometryCache`（device別・内容世代別mesh）へ渡す。`LeasedResource` で受理済みdrawが未記録/記録中/in-flightの旧世代を保持。sky/stars/horizon/Forge cloudがGL buffer store再decodeから外れる。AMD leg `native-v3-valid-amd`（jar `25668c4e…`）で確認 |
| **terrain targetの不要な失敗計上（2026-10-21 修正）** | **修正済み・実機確認** | 確認 | 未測定 | `FrameCoordinator.Submission`（SKIPPED/RECORDED/QUEUED）を導入。native同一queue targetのRECORDEDを「GLへ同期通知できなかった失敗」と数えていた。修正前は`errors=17310`／`no submission reached the queue`が3324件、修正後は**errors=0**・当該断片0（`native-v2/v3`）。記録受理・queue送信・GPU完了を別状態として扱う |
| **Mod向けbuffer互換の一本化（2026-10-21 実装）** | **実装済み・CPUテスト済み** | 実機確認済 | 未測定 | `RenderObjectIds` に単一の名前空間（stub/hint/native）。`NativeGeometryBuffer` のidentityをraw GL adapterが解決し、bind/query・data/subdata・transient map/unmap・delete をCPU回帰で固定。native描画はGL storage mirrorを必要としない。persistent mappingは広告しない |
| **測定系の修復（2026-10-21）** | **実装済み・未使用** | — | — | `gv_kpi.py` が build provenance（build成功＋source revision＋sha一致）を要求し、`frameIntervalWarmP50Ms`＋`warmSampleCount>=50`＋`worldTicksAtEnd>=600`＋両armのscene/camera/resolution/clouds/pacing一致を検証。**3ペア未満・Celtium専用flag・fps上限/vsyncはNO_SPEED_VERDICT**。warm区間はearly finishするため `holdUntilTick=620` が必須 |
| **次の標的：即時グループ数を減らす（2026-10-21 実測由来の仮説）** | **原因を特定・実装は未着手** | — | — | 同一leg（`native-v3b-celtium-r1`、frame 115→9602）の累積差分から、**immediateOffered 279.8/frame・groupsDrawn 308.2/frame・windows 5.0/frame・refused 0**。週末の仕事は捨てていない。measured差 `2.10 − 1.25 = 0.85ms/frame ÷ 280 ≈ 3.0µs/group` で、既存のper-batch実測と同桁＝差はグループ数に比例する形。**どのマージ条件が効いているかを計器で特定**（下の行） |
| **残差の形（2026-10-21 算術・要検証）** | **次に検証する** | 実測 | — | 現HEADのpaired差は `1.867 − 1.248 = 0.619ms/frame`。packet数は **183/frame**。割ると **3.4µs/packet**。これは「差＝packet単価×packet数」という形と整合するが、**3.4µsを独立に測った値ではない**（差を仮定して割っただけ）。したがって次は**1 packetの内訳**（pipeline bind / push constants / vertex-index bind / vkCmdDraw）を分けて測り、3.4µsが我々のCPUなのか駆動呼び出しなのかを確定する。それ無しに「packetを安くする」も「数を減らす」も選ばない |
| **グループが state を所有していなかった（2026-10-21 発見・修正）** | **正当性バグ修正** | 検証PASSED | 未測定 | モデル経路 `celtium$enqueueModelMesh` は `celtium$stateScratch`（**単一の再利用配列**）をそのまま `ImmediateGroup.obtain` に渡し、`ImmediateGroup` は参照を保持していた。⇒ 後続のcaptureが**既にqueue済みの全モデルグループのstateを上書き**し、各グループは「最後にcaptureされた状態」で描かれていた。即時経路は `state.clone()` で回避していたが、モデル経路だけが素通りしていた。修正: `ImmediateGroup` が自分の配列を持ち、代入時に `System.arraycopy`（pooled groupは初回以降アロケーションゼロ）。回帰テスト3件（後続captureで書き換わらない／2グループが別stateを保つ／recycle後もコピーでnull安全）。leg `state-owned-1`（jar `e8806303…`、validation ON）: 画像gate PASSED（distinct 54,604 / nonBg 0.943）、validation clean、warm p50 5.289ms（validation込み）。**性能**: paired KPI（leg `state-owned`）は **1.5387/1.5340/1.5020＝中央値1.534x**（前回1.481x）。ただしこのrunは**負荷が高く**（初期2.95・最終2.68、limit 5.0に上げて実施、CPU6）、1.4ppの分解能を超える差ではあるが**負荷交絡を排除できていない**。arraycopy 15 int/group（183/frame）は無視できる量なので、**差は帰属できず未解決**。次は負荷の低い時間帯に再測定する |
| **per-call lambda と hot `String.contains` の除去（2026-10-21 実装・効果なし）** | **反証** | 実測 | — | JFRが `translateFloat` のlambda **2.7%**（block entity内）と `String.contains` **2.1%** を指摘したので修正: (1) ホットな translate/scale/rotate/multMatrix が**呼び出し毎にcapturing lambdaを確保**していた → 専用ヘルパへ。(2) `fn.name.contains("NamedBuffer")` を per-call 実行 → `Fn.namedObject` に事前計算。leg `lambda-free-1`（jar `3f864595…`、validation ON）は画像gate PASSED・validation clean。**paired KPI（leg `lambda-free`）: 2.079/2.056/2.024 vs 1.404/1.346/1.368 ＝ pair ratio 1.481/1.527/1.480・中央値1.481x。前回1.479xから動かず**＝**ノイズ内、効果なし**。なお今回のrunは**両armとも約10%遅い**（Celeritas 1.25→1.37）ので絶対値比較は不可、比のみ読む。⇒ 正しい変更だが速度改善ではない。**分解能の較正**: 同条件3回の3ペア中央値は 1.4928 / 1.4789 / 1.4811＝run間 1.4pp、ペア内 1.4-4.8pp ⇒ **中央値で約1-1.5%まで判別可能**（`development.md` §6 に記載）。よって今回の「4.8%」はJFRの帰属過大であり、比が動かないことと矛盾しない。これで「プロファイルのleaf名→修正→0効果」が3件連続 |
| **font flush の空振り（2026-10-21 計測→修正→反証）** | **修正済み・効果なし** | 実測 | — | `fontFlushCalls` と理由別内訳、`fontWork[pending/capture]` を追加して出所を特定。**主因は `celtium$fontMatrixBoundary()`**（行列変更ごとに呼ばれ、無ゲートでflush）。`celtium$hasWork()`（pending run か capture保持が無ければ払わない）でゲートした結果: **stateChange flush 3,280.1 → 9.4/frame**（全flush 3,640 → 379/frame、349倍減）、**warm p50 1.849 → 1.846ms＝変化なし**（leg `font-work-1` → `font-matgate-1`、jar `a44ae62d…`/`ca952e52…`、PASSED）。⇒ **空振りは実在したが費用ではなかった**。JFRの「`flushPendingFontBatch` 自己時間4.5%」は**インライン展開された実処理（9回/frameのrun submit＝即時描画経路全体）**を指しており、メソッド自身の文ではない。**教訓: JFRのself-timeはインラインしたcalleeを吸う**ので、leaf名だけで対象を決めない。ゲートは正しさの証明付きで残すが、性能改善としては主張しない |
| **JFRプロファイル（2026-10-21）** | **計測済み** | 実測 | — | harnessに`-XX:`許可を追加し、**warm区間だけ**を `delay=30s,duration=20s` で録音（leg `jfr-warm-1`、1,110 samples、validation OFF）。inclusive集計で **block entity系が46.6%**、その内側に font flush・map操作・`submitGeometryBatch` 14.7%。self-time上位は map操作 約14.5%、font flush 4.5%、`submitImmediateBatchBody` 3.5%。**self-timeはインラインしたcalleeを吸う**のでleaf名だけを対象にしない（この教訓で3件連続0効果）。証拠 `.gv-verify/jfr-warm-1-self-time.json` |
| **フレーム相分解（2026-10-21 実測・KPI条件）** | **この計器の限界を確定** | 実測 | — | leg `phase-split-1`（validation OFF + `passSplit[Cpu]`、jar `d5fa0f38…`）: **frame p50 2.3 / p95 2.7ms**。**`flush` は `terrain` の内側**（`begin("terrain")`:770 → `:803` の `flushPendingBeforeGlDraw()`）＝top-level相は加算できない。正しい地図: **terrain 0.8 = flush 0.6 + terrainRecord 0.1 + 残り0.1**、残るworld約1.5はvanilla自身のループ＋受理経路。flush内は grpPrep 0.1・rcPush 0.1・rcViewport 0.1・grpDraw 0.2。**ただし passSplit は bracket ごとに `nanoTime`×2＋map lookup を払う**（実測17ns/call、`wrecLoop` 内に5 bracket＝約0.5µs/packet）。よって「3.5µs/packet のCPU」の大半は**計器自身**であり、**この相分解でサブµsの帰属は決められない**。サブµsの特定は**JFRのメソッドプロファイル**へ移す（自作bracketをこれ以上増やさない）。**その前提**: `gv_leg.py` の追加JVM引数は `-D`/`-X` のみ許可しており、JFRの `-XX:StartFlightRecording=` が通らない ⇒ harness側の許可リスト拡張が必要 |
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
- 計測の生ログと棄却済みの詳細（`duplicate()`除去の反証、packet内訳の旧版、1.70x/1.49xの履歴）は
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)（packet loopの撤回・validation層による膨張の記録もここ）。
- 旧表・旧日記は [snapshot](<history/plan-state-snapshot-2026-10-17.md>)、
  [plan-archive-2026-10-21.md](<history/plan-archive-2026-10-21.md>)、[diary](<history/plan-diary.md>)。
  履歴を現在の命令・判定として読み戻さない。
