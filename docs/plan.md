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
| **現在のCeleritas比較（2026-10-22）** | **AMD 1.373-1.387x / Intel 1.738x** | 実測 | 未達 | **現行jar `24d31f69…`（HEAD・validation leg PASSED・配布物検証済み）で再測定**: **AMD 1.632/1.586/1.642 vs 1.178/1.144/1.173 ＝ 中央値 1.387x**（3ペア交互順・参照 `567dcf8a…`、p95 2.45-2.50 vs 1.46-1.50）。**Intel 1.738x** は `e4622406…` での測定（3.138/3.146/3.214 vs 1.812/1.811/1.808）。AMDの再測定は 1.373-1.387x の範囲で安定。受入（両GPU ≤1.0）には未達。**注意**: この数値は**影OFF**の構成で測ったもの。**影は2026-10-22に既定ONになった**ため、**出荷構成の性能は再測定が必要**（影ONのコストを含む）。**影ON既定**でも両GPU PASSED・gate PASSED・validation 0件は確認済み |
| **Javaアロケーションを39%削減（2026-10-22）** | **13,763→8,393 MB/run** | 実測 | 構造改善 | JFRで**1run 13.8GB**の確保を検出（float[]25%・byte[]24.7%・int[]12.3%）。2件を修正: ①**`VulkanGlState.copy` 977MB**（`glPushMatrix`毎に`new float[16]`）→ **push/popでスロットをプール再利用**（上限64）で**147MB**。②**`celtium$fogSnapshot` 300MB**（**offer毎にfog配列**を確保して比較）→ **比較はスクラッチ、保持は従来通り**（group毎）で**上位から消滅**。**総確保 13,763→8,110MB（−41%）**、**GC 50→39回**。**画像・validation不変**（PASSED・gate PASSED・0件）。**フレーム時間は未確定**（負荷2.7の強制A/Bで0.955x・ペア逆転）。③**`ModelQuadCapture.snapshot` 368MB**（list毎に3配列を新規確保）→ **`snapshotInto`＋再利用スクラッチ**で**上位から消滅**。**残り**: `VulkanGlState.copy`（getter）182MB・`rgbaOf` 139MB・`drawModelList` 94MB・`ImmediateGroup.obtain` 70MB。**サンプル全体の82%は自コード外**（スタックにceltiumが無い）で、LWJGL/検証層の確保とみられる |






| **描画制約の確定（2026-10-21 利用者指示）** | **制約は1つだけ** | — | — | native VK-only、GL変換は**Modが直接使うGL専用**。それ以外（エンジン、状態所有、資源配置、shader、順序表現、パッケージ構成、計器、スレッド分担）は性能と正しさのため自由に置換する。旧「計器追加禁止」「per-call最適化禁止」「backend機能追加禁止」「S0以外実装禁止」「必ずmulti-draw」は撤回 |
| **Mod向けbuffer互換の一本化（2026-10-21 実装）** | **実装済み・CPUテスト済み** | 実機確認済 | 未測定 | `RenderObjectIds` に単一の名前空間（stub/hint/native）。`NativeGeometryBuffer` のidentityをraw GL adapterが解決し、bind/query・data/subdata・transient map/unmap・delete をCPU回帰で固定。native描画はGL storage mirrorを必要としない。persistent mappingは広告しない |
| **次の標的：即時グループ数を減らす（2026-10-21 実測由来の仮説）** | **原因を特定・実装は未着手** | — | — | 同一leg（`native-v3b-celtium-r1`、frame 115→9602）の累積差分から、**immediateOffered 279.8/frame・groupsDrawn 308.2/frame・windows 5.0/frame・refused 0**。週末の仕事は捨てていない。measured差 `2.10 − 1.25 = 0.85ms/frame ÷ 280 ≈ 3.0µs/group` で、既存のper-batch実測と同桁＝差はグループ数に比例する形。**どのマージ条件が効いているかを計器で特定**（下の行） |
| **KPIに順位付け専用モード（2026-10-21 実装）** | **測定設計の改善** | 実測 | — | `CELTIUM_KPI_SKIP_REFERENCE=1`（`CELTIUM_KPI_CONTROL_JARS` 必須）で参照armを省き、**candidate と control の2armだけ**を回す（9 leg → 6 leg）。参照比は「Celeritasからどれだけ離れているか」で静かなマシンが要るが、**ビルド同士の優劣は制御比だけで決まる**。負荷が高い時間帯に、この mode で初めて**ガードを通る測定**が取れた（leg `rank4`）。テスト2件（参照なしでも制御比を出す／参照なしはKPI判定を出さない） |
| **累積の改善（2026-10-21）** | **約4.8%** | 実測 | — | 制御armで測った3変更の積: identity 0.987 × census 0.980 × affine 0.985 ＝ **0.952x**。参照比も 1.508x（bindsite）→ 1.457x（identity）→ 1.439x（census）→ 1.441x（affine）と同方向。**1.481x（lambda-free）→ 1.441x**で、いずれも同一arm交互比較で裏付けられている |
| **テクスチャaffine比較のallocation除去（2026-10-21 実装）** | **実装済み** | 実測（uncontrolled） | 未測定 | `celtium$textureAffineMoved()` が毎offer `float[6]` を確保していた（約280/frame）。scratch配列＋6要素比較に変更し、群のsnapshot取得時だけclone。validation ONの同一条件legで warm p50 6.24→5.69ms だが**これはpaired測定ではないので改善と主張しない** |
| **state所有のnative化** | **未完（次の境界）** | — | — | GlStateManagerのproducer/queryとraw GL adapterのconsumerを同一native ownerへ。現状backendのstate snapshot/queued groupを経由しており、native側から互換状態を参照する残りがある。state push/pop・query・順序の回帰が必要 |
| **terrain upload とframe実行** | **未完** | — | — | dirty bytes/copy/submit/可視判定の実費分離（warm＋streaming）、worker→render owner引渡し、pass/barrierの資源依存からの編成、旧GL handoff分岐の除去 |
| **互換・目視（4画面 / E2E / RLCraft / BSL / 配布）** | **4画面取得済／settings はHEADで再合格** | 実測 | 一部未完 | **settings（`-Dceltium.validation.scene=videoSettings`）を現行HEADのjar `c7dd20ad…` で再検証**: `accepted=true`/PASSED・**画像gate PASSED**・**validation 0件**・distinct 152（以前の `d105e92f2866` と同値）。4画面（loading/game/settings/home）は色順修正後に取得済みで、home の `VUID-09600` が1件残る。**影**: マップ充填12.2%・in範囲75.4%・キャスタとの重なり4画素＝**キャスタとレシーバの射影不一致**が残る唯一の原因（候補12件消去）。**BSL**: 解析・（shadercがあれば）コンパイルまで通るが**ワールド描画に未接続**（mean 0.31）。**互換**: RLCraft 2.9.3 は0.7非対応（`com.cleanroommc.hackery` が0に）、E2E 1.93aはfull pack未導入。**配布物は検証済み**（梱包・版数・ハッシュ）。詳細は [history](<history/plan-archive-2026-10-21.md>) と `architecture` §9/§10/§11 ／ ／ **home の VUID-09600 は現行jarで再現せず**（`mainMenu` leg・validation ON・VUID 0件、"09600"の一致6件はarenaのバイト数）。leg verdict FAILED は**plain-mode gateがworld基準**のため。**配布物は現行HEADで再検証（2026-10-22）**: jar `24d31f69…` で `verify_forge_jar` verified=true、`verify_version_consistency` verified=True（旧2.5.12系の成果物0）、`jar-content-hash` **`6499fdfb…`（1079エントリ）**。詳細は `architecture` §9-§11 と [history](<history/plan-archive-2026-10-21.md>) |
| **RLCraft 2.9.3 は0.6でも起動不能（2026-10-22）** | **Cleanroom側のクラス欠落** | 実測 | 環境制約 | `rlcraft-2.9.3-on-07`（175 mods）を複製して**Cleanroom 0.6.13-alpha** で起動: **同じクラッシュ**。`NoClassDefFoundError: com/cleanroommc/hackery/ReflectionHackery`（パック側 `com.fantasticsource...ReflectionTool` 由来）。⇒ **0.7固有ではなく0.6.13でも同クラスが無い**＝このパックは**当該クラスを同梱する古いCleanroomを要求**。**Celtium側では回避不能**。複製は削除済み（ディスク18G） |

| **影は既定ON・両GPUでvalidation 0件（2026-10-22 達成）** | **AMD 0.92% / Intel 2.17% が暗くなる** | 実測＋画像 | 受入 | **原因は地形パイプラインの「毎bindで5 binding全部を書き直す」**こと（`VulkanTerrainPipeline.bindTextures`）。FrameSlotのsetは実行中コマンドバッファに名指しされ得るので、**変更されたbindingだけ書く**方式に変更 → 影ONでも **AMD/Intel とも PASSED・gate PASSED・validation 0件**。その結果**影を既定ONにした**（`-Dceltium.shadow.enabled=false` で無効化）。既定ONの状態でも両GPU PASSED・0件を確認（jar `be3c14d9…`）。**可視性**: 同一scene・同カメラ・worldTime固定のON/OFF比較で **AMD 0.92%（平均19.1レベル）/ Intel 2.17%（平均48.6）** 暗くなる。証拠 `docs/evidence/cr07-shadow-{on,off}-{amd,intel}-2026-10-22.png` |
| **影ON構成のコスト（2026-10-22 実測）** | **1.674x（影OFF 1.387x）** | 実測 | 既定ONの代償 | 既定（影ON）のAMD測定・**交互5ペア**（負荷2.7・強制）: **Celtium 1.974-2.083ms（2.04）vs 参照 1.192-1.233ms（1.21）＝1.674x**。影OFFは**1.387x**なので**影が約0.35-0.45ms**。**p95が2.4→5.5-5.8ms**に悪化＝塗りより**境界での自前提出＋フェンス待ち**が尾の原因と見られる。**更新間隔**（`celtium.shadow.maxAge`=2）と**シーン不変スキップ**は**効果未確定**（別負荷で1.788x＝ノイズと区別不能）。次の候補: ①影パスをフレームのコマンドバッファ内へ（待ちを消す）②2048²→1024² ③キャスタを光源boxで絞る |










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
