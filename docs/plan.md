# Celtium 現状表（唯一の現在状態表）

開発ベース **0.0.6-alpha**（[build定義](<../build-1122/build.gradle.kts>)）。公開版とは別。
2026-10-21 改訂。**過去の性能値・根因断定・完了宣言を前提にしない。**
製品の出口は [goal.md](<goal.md>)、設計は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)。
利用者の最新指示とGoal停止状態をこの表で上書きしない。

## 現在の優先順

| 優先・項目 | 実装/判定 | AMD | Intel | 根拠と残り |
|---|---|---|---|---|
| **提出バッチングをGPU別に（2026-10-22 実装・大幅改善）** | **Intel 4.6-5x → 1.982x** | 実測 | 改善 | `celtium.vulkan.batchFrameSubmits=false` を**同一jar・同一sceneの制御armペア**で測ったところ、**Intel: ON 31.4/9.6/10.0ms vs OFF 3.69/3.60/3.66ms＝中央値0.374x（2.7倍高速）**、**AMD: OFF は1.102x（10%遅い）**。⇒ バッチ提出はAMDでは安く、Intelでは病理的（spreadも巨大）。**実装**: プロパティ未指定時は「**Intel以外はバッチ、Intelは非バッチ**」をデバイス名で決める（`FrameCoordinator`が`Host.isIntelDevice()`を参照）。検証: 両GPUでvalidation ON・`accepted=true`/PASSED・validation clean。**新KPI（`cr07-dev-*`、jar `647b2120…`）: AMD 1.391x（1.391/1.371/1.362）／Intel 1.982x（1.942/2.005/1.981）**。計器の副作用も修正: 追加したGPUタイムスタンプを計器ON時のみ書き、二重書きのVUID-00830（AMDで10件）を解消 |
| **行列比較のハッシュ化（2026-10-22 試行→revert）** | **1.000x＝効果なし** | 実測 | — | 受理経路は毎offerで**32 float（projection/modelView）の `Arrays.equals` 2回**をしていたので、行列コピー時に64bit FNVハッシュを計算して保持し（コピーはdirty時のみ）、判定を**long比較2回**に置換。**制御arm3ペア: 1.000x（1.000/1.000/0.999）＝差なし**（jar `10fa5264…` vs `675997d5…`、両legとも合格・validation clean）。衝突リスクだけ増えて利得が無いので**revert**（jarは`675997d5…`に戻り、検証済みステージとsha一致）。**結論**: 受理経路の26%は**比較やcaptureではなく、per-offerの本質的作業（頂点コピー・追記・記帳）**。micro最適化はここで打ち止め＝次は**offerを範囲追記だけにしてgroup境界判定をflushへ遅延**させる再設計しかない |
| **presented画像のR/B反転を修正（2026-10-22 重大・検証済み）** | **修正済み** | 実測 | 改善 | presented dump が**無条件に BGRA→RGBA 変換**していたが、`B8G8R8A8` を little-endian int で読むと**すでに `0xAARRGGBB`**（`setRGB` の期待形）なので**二重反転**だった。根拠: 同一カメラ・同一時刻（両arm `worldTimeAtCapture=6000`）で sky **(207,180,165) vs (165,179,207)**、stone **(36,45,61) vs (61,45,36)** ＝**R↔B だけの同じ絵**。**修正**: format 依存に（BGRA系は無変換、RGBA系のみスワップ）。検証 jar `0fdbc506…`、leg `chanfix`/`chanfix-v` PASSED・gate PASSED・validation 0。修正後 sky (165,180,207) vs (165,179,207)、stone **完全一致**、全体差 mean 55.5→**30.5**、`>32` 52%→**30%**、**ログ帯を除くと mean 11.4 / >32 10.6%**。**これ以前の presented 画像証拠（home/ネザー等）は色が入れ替わっていた**（色数統計は順序不変で影響なし） |
| **両arm画像の比較（2026-10-22）** | **ログ帯を除けば近い** | 実測 | — | 同一カメラ・同一時刻の presented 画像（Celtium）とゲームのスクリーンショット（参照）を領域別に比較。R/B修正後: sky (165,180,207) vs (165,179,207)、stone 完全一致、grass (62,104,49) vs (61,100,47)。全体で `>32` 30%、**オンスクリーンログ帯（我々だけが出す診断文字）を除くと mean 11.4 / >32 10.6%**。残差は**ログ文字とAA/時刻位相**で、撮影経路が違う（presented vs glReadPixels）ことによる差も含む。⇒ 画質の判定は**ログ帯除外＋領域別**で行う |
| **受理経路の総括（2026-10-22・設計文書§7）** | **残る道は2つ** | 実測 | 未達 | 30ラウンド分の実測を `architecture` §7 に総括。受理経路は**1.7µs/offer**（279 offer/frame × 26%）、Celeritasは0.2-0.3µs。**名前の付いたコストを全部消してもフレームが動かない**（capture 79%減→0.7%、prepare 26%減→0.0%、行列比較100%減→0.0%、rebase全廃→上限0.5%）。名前の無い約1µsは5-50nsの集まりで、**本機の分解能（5-10ns/offer）以下**。⇒ 残る方針は **(1) accept経路をnativeへ移す**（`CeltiumGlStub` がGL呼び出しで実証済みの手法、見込み1.7→0.6-0.9µs、実装量大）か **(2) captureモデルを変える**（`BufferBuilder` のバッファを所有する、互換リスク大）の2つだけ。**やらないこと**も明記: group数削減・GPU側最適化・fastutil置換・1%未満の単品削減の「改善」報告 |
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
| **ネザー次元（2026-10-22 実測・欠陥ではない）** | **両armとも暗い＝場面側** | 実測 | 問題なし | `-Dceltium.validation.scene=nether` だけでは次元が切り替わらない（＝私のフラグ誤用、正しくは `-Dceltium.validation.dimension=-1 -Dceltium.validation.dimensionAtTick=60`）。遷移後の presented PNG は**ほぼ真っ黒**（Celtium distinct **274**、gate FAILED、validation 0件）だったが、**同じ条件で参照arm（Celeritas）を回すと distinct **406** でやはりほぼ真っ黒・gate FAILED**（`sc-nether-ref`）。⇒ **両レンダラで同じ絵が出るので、これは我々の描画欠陥ではなくこのテストマップのネザー面が暗いだけ**。「ネザーが壊れている」という前ラウンドの主張は**撤回**。ネザーの校正下限は両armとも満たさないため、この場面では回帰の主張に使えない（Intelや別mapで再校正が必要） |
| **hot path sweep（2026-10-22）** | **ロック除去は採用／fastutil置換は不可** | 実測 | — | **採用**: `VulkanDescriptorFault.shouldFail` の `synchronized`＋文字列処理を未armed時は静的boolean読みに（texture faultと同じ形）。announce経路は空テーブル時に短絡。leg `sweep1-v`/`sweep3-v`（jar `c322d70e…`）は PASSED・画像gate PASSED・validation clean。**fastutil置換は不可と確定**: `announcedImages`/`announcedGeneration`/`consumedGeneration` をprimitive化したところ、**別クラス（CeltiumNativeDraw→VertexBuffer draw）で `NoSuchMethodError: Int2ObjectOpenHashMap.get(java.lang.Integer)`** が発生し起動不能。これは**2回目の同一故障**（以前は `VulkanTextureRegistry.entries`）。⇒ **このコードベースでは boxed `Map<Integer,X>` を fastutil に置換しない**（規則）。**構造の実測**: groups 183/frame に対し offers 279/frame ＝ **1.5 offer/group**。バッチングは描画数を34%減らすだけで、per-offer の記帳コストは払い続ける＝**コストはgroup数ではなくoffer数に比例**している |
| **受理経路の再設計（2026-10-22 設計・前提訂正）** | **転写は遅延不可** | 未実装 | — | `architecture` §6。**`vertexAddress` はゲームの`BufferBuilder`内部バッファで次のdrawで再利用**されるため**頂点転写は受理時必須**（遅延すると壊れる）＝遅延できるのは境界判定とgroup生成だけで、判定は既に安く**5%には届かない見込み**。1.7µs/offerは数十個の5-50nsに散る（**長い尾**）ので、次は**まとめたsweepを5%基準**で測る |
| **互換・目視（4画面 / E2E / RLCraft / qmagnet / BSL / 配布）** | **4画面取得済み・homeに欠陥** | 実測 | 一部未完 | 0.7環境（AMD、jar `675997d5…`）で loading `9d57f3085aba`（MOJANGロゴ・鏡像なし）、game `0baeeb33f468`（空/地形/HUD）、settings `30dadcc8b84d`（Video Settings 全パネル、gate PASSED・validation 0）、home `26f86a60db67`（ロゴ/5ボタン/スプラッシュ/パノラマ、**ただし `VUID-vkCmdDraw-None-09600` が2件**＝descriptorが `TRANSFER_DST` を宣言、`CELTIUM_COPY_CALLER dest=112 256x256` と同フレーム。**未修正**）。影は AMD/Intel 両方 PASSED。**RLCraft 2.9.3 は0.7非対応**: `com.cleanroommc.hackery` パッケージが0.6に5エントリ/0.7に0で**削除**（`ReflectionHackery` 使用modが起動不能）。E2Eはfull pack未導入（ローカルprofileは40MB・1modの縮小版）。未完: homeのVUID、QMAGNET対照、E2E/RLCraft(0.6またはpack更新)、BSL、配布物 |
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
