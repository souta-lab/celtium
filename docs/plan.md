# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 次の作業単位（2026-10-22 時点の優先順）

1. **受理経路のCPU構造改善**（性能比を作る唯一の道）。AMDは解像度非依存＝CPU律速、Intelも3.39ms中CPU約2.4ms。
   1.7µs/offerの内訳は名前の付いた項目を全部消しても動かない（`architecture` §7）。設計は同 §6/§8。
2. **シェーダパックをワールド描画へ接続**。現状は `celtium$probePackProgram`（解析→数プログラムのコンパイル→プローブ描画）までで、
   **ワールドのdrawにパックのプログラムが使われていない**（BSLは mean 0.31 で絵が変わらない）。
3. **影のカバレッジ**（現状2%前後）。生成・捕捉・光源中心(217.5,48,217.5)・Vulkan深度は数値で確認済み、残りはレシーバ座標。
   **単発フレームの画素比較は分散2倍なので、複数フレーム平均の指標を先に作る**。
4. **shadercはLWJGL版に固定**（`architecture` §9）。forge122は3.4.1でコンパイル・実行時は0.7が3.4.3で、**3.4.1のjarを3.4.3に置くと `invalid classes` で失敗**する（実測）。⇒「同梱すれば解決」ではない。影は埋め込みSPIR-Vで**回避済み**。パックは (a)インスタンス側に版の合うshadercを要求（文書化）、(b)版別同梱（パス衝突の危険）、(c)shaderc非依存（外部コンパイラ/事前SPIR-V）のいずれかが必要。テスト依存は3.4.3に揃えた（2026-10-22）。
5. **home の `VUID-09600` 1件**（`TRANSFER_DST` を宣言するdescriptor。Java側の書込みには無い＝未書込みbindingかnative経路）。
6. **互換**: RLCraft 2.9.3 は0.7非対応（`hackery` が0.7に0エントリ）、E2E 1.93aはfull pack未導入。
7. **配布物の一致**: **検証済み（2026-10-22）**。`verify_forge_jar.py` は `verified=true` / `errors=[]`（梱包・ライセンス表記）、`verify_version_consistency.py` は `verified=True`（artifact=`0.0.6-alpha`・plan.md のヘッダと一致・旧系列なし）、`jar-content-hash.py` は **`2f5d1299…`（1079 entries）**。**現HEADからの再ビルドjarは `65ca8ad3…` で、KPI測定に使ったjarと同一**。※スクリプト自身の注記どおり、これは**梱包とライセンスの範囲**で、起動・GL/Vulkan相互運用・正しさの主張ではない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **記録経路の切り分けは不能（2026-10-22 試行）** | **スイッチが測定を壊す** | 実測 | — | `-Dceltium.vulkan.skipImmediateRecord=true` で測ると **warm p50 58.4ms（通常1.87msの31倍）**＝記録しないとフレームが完了せず、**帰属測定に使えない**。よって受理vs記録の分解能は BATCH_TIMINGS（prepare 256ns / append 248ns / capture 99ns / key 74ns）とプロファイル26%以上には上がらず、**§7の結論（残りの大半は名前の無いコスト）は変わらない** |
| **受理経路の総括（2026-10-22・設計文書§7）** | **残る道は2つ** | 実測 | 未達 | 30ラウンド分の実測を `architecture` §7 に総括。受理経路は**1.7µs/offer**（279 offer/frame × 26%）、Celeritasは0.2-0.3µs。**名前の付いたコストを全部消してもフレームが動かない**（capture 79%減→0.7%、prepare 26%減→0.0%、行列比較100%減→0.0%、rebase全廃→上限0.5%）。名前の無い約1µsは5-50nsの集まりで、**本機の分解能（5-10ns/offer）以下**。⇒ 残る方針は **(1) accept経路をnativeへ移す**（`CeltiumGlStub` がGL呼び出しで実証済みの手法、見込み1.7→0.6-0.9µs、実装量大）か **(2) captureモデルを変える**（`BufferBuilder` のバッファを所有する、互換リスク大）の2つだけ。**やらないこと**も明記: group数削減・GPU側最適化・fastutil置換・1%未満の単品削減の「改善」報告 |
| **現在のCeleritas比較（2026-10-22・現行ビルド）** | **AMD 1.387x / Intel 1.861x** | 実測 | 未達 | jar **`65ca8ad3…`**（影・行列・カメラ経路の変更込み）で両GPUを測定（0.7.11-alpha + Java 27、world、warm 400..600、3ペア交互順、参照 `567dcf8a…`）。**AMD: 1.720/1.786/1.714 vs 1.240/1.254/1.297 ＝中央値 1.387x**（従来1.408x）、**p95 2.445/2.752/2.501**（従来4.8-5.2＝半減）。**Intel: 3.415/3.313/3.534 vs 1.835/1.800/1.831 ＝中央値 1.861x**（従来1.939x）、p95 6.58/6.92/7.36（従来7.06-7.69）。両GPUとも改善。影は既定OFFのlegなのでこの改善は影由来ではない（sweep＋ドリフト）。受入（両GPU ≤1.0）には未達 |
| **現在のCeleritas比較（2026-10-22・現行ビルド）** | **AMD 1.408x / Intel 1.939x** | 実測 | 未達 | jar **`c322d70e…`**（sweep適用後）で両GPUを同日測定（0.7.11-alpha + Java 27、world、warm 400..600、3ペア交互順、参照 `567dcf8a…`）。**AMD: 1.868/1.893/1.926 vs 1.343/1.345/1.352 ＝中央値 1.408x**、**Intel: 3.624/3.645/3.640 vs 1.869/1.886/1.864 ＝中央値 1.939x**。p95 は AMD 4.8-5.2 vs 2.0-2.1、Intel 7.1-7.6 vs 2.7-2.9。**sweep適用による退行なし**（前回 1.407 / 1.920）。受入（両GPU ≤1.0）に対し未達 |
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
| **受理経路の再設計（2026-10-22 設計・前提訂正）** | **転写は遅延不可** | 未実装 | — | `architecture` §6。**`vertexAddress` はゲームの`BufferBuilder`内部バッファで次のdrawで再利用**されるため**頂点転写は受理時必須**（遅延すると壊れる）＝遅延できるのは境界判定とgroup生成だけで、判定は既に安く**5%には届かない見込み**。1.7µs/offerは数十個の5-50nsに散る（**長い尾**）ので、次は**まとめたsweepを5%基準**で測る |
| **互換・目視（4画面 / E2E / RLCraft / BSL / 配布）** | **4画面取得済／影は射影不一致を追跡中** | 実測 | 一部未完 | 4画面は色順修正後に取得（settings gate PASSED・validation 0、home に VUID-09600 が1件）。**影は計器で原因を特定**: マップ充填率12.2%、in範囲48,319画素のうち**キャスタを踏むのは4画素**＝**キャスタとレシーバの射影が一致しない**。ルックアップ自体は75.4%がin範囲でzも正常、深度スケールも正しい（`cameraDepth=0.5`）。`_draw_id`（per-vertex）・Y反転・region offset の3候補はコードで消去済み。**影マップの画像ダンプは3回失敗**（legが完走せず＝ワールド構築が150-240秒に収まらない。未コミットで撤回、ツリーは健全 `9258bcfe…`）。BSLは解析・（shadercがあれば）コンパイルまで通るがワールド描画に未接続。RLCraft 2.9.3 は0.7非対応。E2Eはfull pack未導入。**影マップの占有グリッド計器は撤回（2026-10-22）**: そのビルド（jar `73e38466…`）は**2回とも同じ地点で停止**（ログ1,518行・ワールド構築未完・exit 250）。**制御ビルド（`e4622406`）は2回とも PASSED（18,696行）**⇒ 変更が原因と確定し、`git checkout` で撤回。**再ビルドしたjarは `e4622406` で検証済みの状態に一致**。**教訓**: legが落ちたら**まず制御ビルドで同じlegを回す**（環境要因と変更要因を分ける）。詳細は [history](<history/plan-archive-2026-10-21.md>) と `architecture` §9/§10 |
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
