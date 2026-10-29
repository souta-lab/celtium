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
