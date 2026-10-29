# SuperSymmetry KPI: Celtium vs Celeritas の paired 計測（2026-10-29）

**目的**: KPI「SuperSymmetry で Celeritas の FPS を上回る」の最初の公平な比較。同一 instance・同一セーブ・
同一カメラ・同一 JVM flags・同一計測窓で 2 アーム（Celtium / Celeritas 参照）を実行した。

## 条件

| 項目 | 値 |
|---|---|
| instance | `Supersymmetry`（Forge 14.23.5.2860 + Cleanroom Relauncher 1.3.0 → Cleanroom 0.7.14-alpha、Fugue 0.25.5） |
| 環境 | AMD RX 6400 (RADV NAVI24)、1280x720 headless X11、ヒープ 5120m、親 Java 8 / ゲーム Zulu 25 |
| scene | `world`（qmagnet セーブを `--seed-save` で隔離コピーへ）、camera `217.5/48.0/217.5 yaw180 pitch10` |
| 計測窓 | cold = worldTicks 120..200、**warm = worldTicks 400..600**（`-Dceltium.validation.holdUntilTick=1300`） |
| 共通 flags | keepGui, minWorldFrames=5, pinSections=64, screenshotAtTick=200, rawFboIdentityProbe=false, opaqueCopyProbe=false |
| 両アーム共通 | `CustomLoadingScreen-*` を除外（Celtium の競合チェック。両アームで同一条件） |
| Celtium | jar `f78b7c03…`（+ glless=true, presentReadback=true） |
| Celeritas | `01-celeritas-forge-mc12.2-2.5.0-dev.jar` sha256 `567dcf8a…`（GL レンダラ、glRendererPreflight で radeonsi を確認） |
| 受入状態 | 両アーム `validationMessageCount=0`、`captureListedSections=17`、`worldTicksAtEnd=1299`（**シーン同一**） |

## 結果（warm 窓 400..600、同一内容）

最初の Celtium アームは `presentedDumpEvery=40`（既定）のまま実行しており、**40 フレームごとの presented
dump readback が 90–100ms の `displayUpdate` ブロックを生んでいた**（`passes` run の warmSpikes で確認:
`loopMs=95 tickMs=2 displayUpdateMs=86`、ほぼ 9 tick 周期）。これは計測税であり、描画のスパイクではない。
`-Dceltium.vulkan.presentedDumpEvery=0` で再実行した Celtium が下段。

| 指標 | Celtium(dump周期ON) | **Celtium(dump周期OFF)** | Celeritas | OFF/Celeritas |
|---|---|---|---|---|
| warm P50 | 9.04 ms | **7.09 ms** (≈141 FPS) | **3.48 ms** (≈288 FPS) | 2.04 倍遅い |
| warm P95 | 32.56 ms | **10.18 ms** | **8.15 ms** | 1.25 倍遅い |
| warm P99 | 105.12 ms | **11.06 ms** | 12.92 ms | 0.86（Celtium が良い） |
| warm >33ms フレーム | 34 | **1** | **0** | |
| warm >50ms フレーム | 24 | **0** | **0** | |
| warm サンプル数 | 704 | **1331** | 2415 | |
| cold P50（参考・構築中） | 5.44 ms | — | 1.68 ms | |

**結論**: 前回の「2.6 倍遅い」のうち P95/P99 の悪化は大半が計測税だった。dump 周期を止めるとスパイクは消え、
残る差は **中央値の +3.6ms**（7.09 vs 3.48）に集約される。P99 では Celtium が Celeritas より良い（11.06 vs 12.92）。
したがって KPI の残差は「スパイク」ではなく「毎フレームの定常コスト」である。

### readback 税を切った真の比較（kpi3, 両アーム同一 flags）

`presentReadback` / presented dump を両アームで外した（画像証拠は取れないが、`holdUntilTick=1300` により
warm 窓まで継続する）。Celtium の present stage は `readbackMs=0 wholeMs=1025 calls=17958` = 0.057ms/call になり、
present 税が消えたことを確認（従来は readback 2.26ms/call）。

| 指標 | **Celtium (kpi3)** | **Celeritas (kpi3)** | 比 |
|---|---|---|---|
| warm P50 | **4.05 ms** | **2.38 ms** | 1.70 倍遅い |
| warm P95 | 6.91 ms | 4.47 ms | 1.55 |
| warm P99 | 7.99 ms | 5.41 ms | 1.48 |
| >33ms / >50ms | 0 / 0 | 0 / 0 | — |
| samples | 2326 | 3895 | — |
| captureListedSections | 17 | 3 | **内容が一致しない** |

Celeritas アームは capture 時の可視セクションが 3（Celtium 17）と報告され、両レンダラでセクション列挙が
一致しない。warm 窓の tick とカメラは同一で、コンテンツを画像で突き合わせるには較正 gate が要る。
よって **kpi3 は「readback 税を除いた定常コストの差は約 1.7 倍」までを言い、KPI の最終判定には使わない**。

### 定常コストの内訳（passSplit, dump OFF・readback ON の run）

frame bracket p50 = 2.0ms（world 1.3 / gui 0.7 / hand 0.1）。world の内訳は winRecord 0.6 / flush 0.8 /
winBarrier 0.1 / sky 0.2 / grpDraw 0.2 / offMerge 0.5（重複あり）。fixture の warm P50 4.05 との差 約2.0ms は
frame bracket 外（ゲーム tick・present 0.06ms・ループ）。**Celeritas の全フレームが 2.38ms なので、Celtium は
frame bracket 2.0ms をさらに削る必要がある**。次の最適化対象は winRecord/flush（即時windowの記録・提出）と gui。

### 残差 +3.6ms の内訳（Celtium 側、passSplit / frameBudget / warmSpikes から）

| 要素 | 実測 | 出所 |
|---|---|---|
| world pass（うち flush 1.3–1.4） | 2.8–3.2 ms | `CELTIUM_FRAME_PASS_SPLIT`（dump OFF 前の run） |
| gui / hand / particles | 0.8 / 0.1 / 0.1 ms | 同上 |
| frame bracket 合計 | 3.6–4.0 ms | 同上 |
| fixture の warm P50 | 7.09 ms | `frameIntervalWarmP50Ms` |
| → bracket 外（tick / present / loop） | **約 3.1–3.5 ms** | 差 |
| present（frameBudget, tax込） | 1.8 ms | `CELTIUM_FRAME_BUDGET` |

推定: **flush（即時window記録/提出）1.3ms + present 1.5–1.8ms + world の残り**。Celeritas の frame 全体が
3.48ms なので、Celeritas の world+present+tick はこの半分以下。次の最適化対象は flush と present。

run: `.gv-verify/run-kpi2-susy-celtium/`（dump周期OFF）と `.gv-verify/run-kpi-susy-celeritas/`。lock JSON に
全 flags とハッシュを記録。**性能 leg では `-Dceltium.vulkan.presentedDumpEvery=0` を必ず付ける**
（AGENTS.md の計測負荷分離の規則。付け忘れると 40 フレームごとに 90ms の readback が乗る）。

## 解釈（この計測が言えること・言えないこと）

- **言える**: 同一ワールド・同一カメラ・同一セクション数（17）・同一 tick で、Celtium のフレーム時間は Celeritas の
  約 2.6 倍（P50）、P95 で約 4 倍。しかも Celtium だけが 33ms/50ms 超のスパイクを出す（34/24 フレーム）。
- **言えない**: 画像の正しさ（このポーズ/構成では gate が較正されず `UNCALIBRATED_PRESENTED_IMAGE`）、
  Windows/NVIDIA の挙動、1920x1080 の利用者環境。単発 run（各 1 回）なので run 間分散も未評価。
- 次の一手: Celtium 側に frameBudget を付けて同じシーンを回し、9ms の内訳（record/batch/submit/fence/gpu/other）を取る。
  また 33ms 超スパイクの発生箇所（GC・チャンク再構築・present 待ち）を `warmSpikes` と突き合わせる。

## 既知の残タスク（KPI 未達の理由）

| 項目 | 実測 |
|---|---|
| **FPS** | 上表のとおり Celeritas 優勢（本 unit の主課題） |
| **エラー/拒否** | device lost 0・validation 0。ただし `DRAWARRAY_REFUSAL_CONTEXT`（IR の stride48 client arrays）8件、`UNMAPPED_REFUSED`（32B particle+normal）3件、mirror の `PATCH_REJECTED` 7件（IR の render target）は残る |
| **完全動作** | メニューはローカル 61.3 FPS（`CELTIUM_PRESENT_INTERVAL`）。利用者の 6FPS・背景黒・文字重なりは次回のログ（frameBudget + validation 有効）で切り分け |

## paired rep（Supersymmetry, 2repずつ, 2026-10-30）

同一 flags（kpi3 と完全一致 + presentInterval）、同一 instance/save/camera、warm 窓 400..600。

| arm | rep1 | rep2 | 備考 |
|---|---|---|---|
| Celtium (`f78b7c03` / `4874d688`) | **4.05 ms** | **4.12 ms** | P95 6.91 / 7.12 |
| Celeritas (`567dcf8a`) | **2.38 ms** | **2.36 ms** | P95 4.47 / 4.95 |
| 比 | 1.70 | 1.75 | 安定 |

→ Supersymmetry の定常差は **約1.7倍**（Celtium が遅い）で安定。ATM3 は約4.4倍。

### 重要な落とし穴（2つ）

1. **flags を落とすと warm 窓の意味が変わる**: `minWorldFrames`/`screenshotAtTick` を外した run は
   warm P50 29.1ms になった。fixture の GUI stage が warm 窓に入り、画面描画中の数字を拾っていた。
   paired は flags 完全一致が必須（lock JSON の jvmArgs を複製する）。
2. **ホットGLパスにスタック走査を入れるな**: `rawMatrix` を「呼び出し元フレーム判定」に変えた版は、
   glPushMatrix/glTranslate が毎モデル走るため warm P50 が 4.05 → **28.5–29.2ms** に退行した。
   旧JAR `f78b7c03` で 4.30ms、フラグ方式へ戻した修正版 `4874d688` で 4.12ms を実測し、
   退行の原因がスタック走査そのものであることを確認（commit `d83489bd9`）。
   mod 起点 glMultMatrix のルートは `-Dceltium.glless.modMatrixRoutes` で**既定OFF**（ホットパス保護）。

## world-only の paired（GUI stage を除外した KPI 数値, 2026-10-30）

`keepGui=true` の warm 窓は fixture の GUI stage（video settings 画面）と重なり、画面描画のコストを
world FPS として拾っていた。`keepGui=false` + `holdUntilTick=1300` で world-only の warm 窓が取れる。

| pack | arm | keepGui あり（参考） | **world-only** | 比（world-only） |
|---|---|---|---|---|
| Supersymmetry | Celtium | 4.05–4.12 ms | **2.50 ms** (P95 4.83) | |
| Supersymmetry | Celeritas | 2.38–2.36 ms | **1.70 ms** (P95 2.57) | **1.47 倍** |
| ATM3 | Celtium | 9.75–10.00 ms | **3.42 ms** (P95 13.91) | |
| ATM3 | Celeritas | 2.24–2.26 ms | **1.25 ms** (P95 3.81) | **2.74 倍** |

- ATM3 は両アーム `captureListedSections=12` で内容一致、Supersymmetry は Celtium 17 / Celeritas 3（列挙差）。
- **KPI の現実の差は 1.47 倍（Susy）/ 2.74 倍（ATM3）**。以前の 1.7 倍 / 4.4 倍は GUI stage 込みで水増しされていた。
- ATM3 の P95（13.9 vs 3.8）が残課題: 1フレームのスパイクが Celeritas の 3.6 倍。

## world-only の passSplit（2026-10-30）

`keepGui=false` の warm 窓で passSplit を取った（`run-susy-nogui-split`）。

| ブラケット | p50 | p95 | 解釈 |
|---|---|---|---|
| frame / world | **1.0 ms** | 1.1 | world 描画はここに収まる |
| flush | 0.4 | 0.5 | うち winRecord 0.3 / offMerge 0.2 / grpDraw 0.1 |
| sky | 0.1 | 0.1 | |
| terrain / gui / hand | 0.0 | 0.0 | |

fixture の warm P50 は 2.50ms なので、**frame bracket の外に約1.5ms**（ゲームtick・present・ループ）。
Celeritas の全フレームは 1.70ms（world-only）。Celtium の *render だけ* は 1.0ms で Celeritas の全frameより
小さいが、外側のコストが Celtium 側で大きい。

### ATM3 world-only（`run-atm3-nogui-split`）

| ブラケット | p50 | p95 | worst |
|---|---|---|---|
| frame / world | **2.1 ms** | 6.0 | 8.4 |
| terrain | 0.2 | 1.0 | 4.6 |
| setupTerrain | 0.2 | 2.2 | 5.9 |
| chunkUpload | 0.1 | 0.6 | 4.7 |
| stUpload | 0.1 | 0.9 | 4.7 |
| flush | 0.1 | 0.9 | 4.5 |
| particles | 0.1 | 0.4 | 4.2 |

Celeritas の全フレームは 1.25ms → **Celtium は render 単体で Celeritas の全フレームを超える**。
p95 の 6.0ms は chunk 構築/アップロード系（setupTerrain/chunkUpload/stUpload）のスパイクが支配。
最適化の第一候補は (a) chunk upload のスパイク平準化、(b) フレーム内 window 記録の削減、の2つ。

## ループ構成（world-only, frameTail）

`run-susy-nogui-tail`（passSplit + frameTail、keepGui=false）の TAIL 平均:

| 部位 | tickなしループ | tickループ |
|---|---|---|
| loop | **2095 µs** | 4276 µs |
| render（updateCameraAndRender 全体） | **1944 µs** | 2338 µs |
| tick（ゲーム） | 0 | 1734 µs |
| display（present） | 53 µs | 72 µs |
| fmlTick / scheduled | 83 / 3 µs | 103 / 8 µs |

ここで **render 1944µs に対し passSplit の frame bracket は 1000µs**。差の約0.9ms は
`updateCameraAndRender` のうちフレームブラケット外（HUD/オーバーレイ・即時バッチの前処理など）。
fixture の warm P50 2.69ms はこの tickなしループと整合する。

→ Supersymmetry world-only の Celtium コストは「world window 1.0ms + その外の HUD/即時 0.9ms」。
Celeritas の全フレーム 1.70ms を上回るには、この **0.9ms 側（即時/HUD のバッチ処理）**が最大の候補。
ATM3 側の最大候補は chunk upload スパイク（p95 setupTerrain 2.2ms）。
