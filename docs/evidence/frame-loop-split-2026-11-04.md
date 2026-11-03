# フレームは tick と render の 2 つで、present は 0（2026-11-04）

round 50。KPI 構成（validation off・readback off・dump off）で、**フレーム間隔の中身**を初めて
2 つの既存計器で取った。結果は round 41–49 の前提を 2 つ壊す。

## 条件

| | |
|---|---|
| Renderer | `302cd2b61781aa3eb9b69407845d587669de186a97a2833a37f6a4d5aa79a6f0`（committed tree `0429a5b91`） |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| GPU | AMD Radeon RX 6400（RADV NAVI24） |
| Scene | `world`、`qmagnet`、1280x720、`guiScale=3`、雲 0、fps 上限 260、vsync off |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Leg | `r50a-loop`（+`frameStats`）、`r50b-split`（+`frameStats`+`passSplit`+`frameBudget`） |
| 結果 | 両方 `PASSED`・`problems=[]`・`fixtureStatus=PASSED`・validation 0 |

## 1. ループの内訳: tick 1 ms + render 1 ms、present は 0

`CELTIUM_FRAME_STATS` の `loop[...]`（`GameLoopTiming`、120 フレーム平均、定常状態）:

```
loop[frames=120 loopMean=2 tickMean=1 displayMean=0 blitMean=0 syncMean=0
     renderMean=1 schedMean=0 wrapperMean=0 toastMean=0 fmlMean=0]
```

- **`displayMean=0`**（`updateDisplay` = swapchain の acquire/present）と **`syncMean=0`**（fps リミッタ）。
  **present 経路はフレーム間隔の一部ではない。** round 47 の「差の ~1.6 ms は bracket の外（tick・chunk・
  display update・limiter）」のうち、display と limiter は 0 で、残るのは **tick** である。
- `loopMean=2` = `tickMean=1` + `renderMean=1`。**tick はフレームの約半分**で、これはゲーム自身の仕事
  （Celeritas も同じだけ払う）。
- 負荷の立ち上がり（`loopMean=21 tickMean=11 renderMean=8 schedMean=4`）は world 読込の数フレームで、
  定常状態は 2 ms。

## 2. render の内訳: frame 1.3 = world 0.9 + gui 0.4、窓の障壁は 0

`CELTIUM_FRAME_PASS_SPLIT`（直近 512 フレーム、p50 ms、`r50b-split`）:

| bracket | p50 | 中身 |
|---|---|---|
| `frame` | **1.3** | `updateCameraAndRender` 全体 |
| `world` | **0.9** | |
| `gui` | **0.4** | HUD（受理経路＋窓の記録） |
| `hand` | 0.1 | |
| `flush`（world 内） | **0.4** | 窓の記録 |
| `terrain`（world 内） | 0.1 | |
| `present`（world 内） | **0.0** | |
| `winRecord`（flush 内） | **0.3** | 窓の group ループ |
| `wrecLoop`（winRecord 内） | **0.2** | group ごとの準備 |
| `grpDraw`（winRecord 内） | **0.1** | 実際の draw |
| `winHead`（flush 内） | **0.1** | routing・pair clear・begin-info |
| `winBarrier`（flush 内） | **0.0** | acquire/release 障壁 |
| `winBeginPass`/`winSubmit`/`winEnd`/`rcPrepared`/`rcResolve` | 0.0 | |

**壊れた前提 1: 「窓の固定費は障壁」ではない。** `winBarrier` は p50 **0.0**（worst 0.3）。窓 23 枚/フレームの
acquire/release 障壁は**測って 0** で、窓の 0.4 ms は **group ループ**（`wrecLoop` 0.2 + `grpDraw` 0.1）と
`winHead` 0.1 である。round 49 の「窓 1 枚の最大項目は pair clear 19 µs」は `windowSplit` を 3 計器同時で
当てた leg の数字で、この leg（`passSplit`+`frameBudget` のみ）では障壁も begin も 0 に出る。

**壊れた前提 2: 「present が 0.2 ms」ではない。** `present` は p50 0.0、ループの `displayMean` も 0。
`frameBudget` の `present` 列は 0.1 ms（p50）。

## 2b. frameBudget の内訳: frame 2.6 = record 0.4 + batch 0.4 + present 0.1 + other 1.7

`CELTIUM_FRAME_BUDGET`（60 フレーム窓、p50/p95/p99 ms、`r50c-inpass`）:

```
frame=2.6/3.4/4.4 record=0.4/0.5/0.5 batch=0.4/0.4/0.4 submit=0.0/0.0/0.0
fence=0.0/0.0/0.0 slot=0.0/0.0/0.0 present=0.1/0.2/0.3 gpu=0.0/0.3/0.3 other=1.7/2.4/3.4
```

- **Celtium 自身の計上分は 0.9 ms**（record 0.4 + batch 0.4 + present 0.1）。`submit`/`fence`/`slot` は 0
  （提出は遅延バッチで、レンダースレッドを止めない）。
- **`other` 1.7 ms がフレームの 65%** で、これはループ計器の tick 1.0 + ゲームの render 仕事に当たる。
- `gpu` 0.0-0.3 で `gpuShare=0.05`。**CPU 律速**は変わらない。

## 3. この 2 つが意味すること

- 定常状態のフレームは **render 1.3 + tick 1.0 ≈ 2.3 ms**。KPI の warm p50 は **2.989 ms** なので、
  **warm 窓（tick 400–600）は定常状態より重い**。`r50a-loop` の `loop` 列は 21 → 6 → 4 → 3 → 3 → 2 と
  単調に下がり、tick 400–600 はまだその途中にある。**KPI の比は「読込の収束途中」を測っている。**
- Celtium 固有の render 費は `gui` 0.4 + `flush` 0.4 + `terrain` 0.1 ≈ 0.9 ms で、world の残り 0.3 は
  ゲームの `renderWorldPass`。**単一項目は 0.4 ms 以下**で、round 47 の「micro-opt の床」と一致する。
- したがって条件 C の 0.64 ms は、**`gui`（受理経路）と `flush`（group ループ）の 2 つを同時に削る**ことで
  しか届かない。障壁・present・tick は対象外である（測って 0 か、ゲームの仕事）。

## 4. 本 round のコード: pair clear を pass 内の 1 コマンドへ（スイッチ・既定 OFF）

`-Dceltium.vulkan.pairClearInPass=true` で、pair の once-per-frame clear を
**`vkCmdClearAttachments` 1 回**（pass の直後）として記録する。置き換えるのは
`vkCmdClearColorImage` + `vkCmdClearDepthStencilImage` + その前後の 2 本の `vkCmdPipelineBarrier`。

- 実装: `GllessFrameTarget.Pair.deferClear`（acquire と、必要なときだけ depth の layout 移動を記録）→
  `recordDeferredClearAttachments`（pass 内で 1 コマンド）。判断は
  `celtium$deferredClearNeedsDepthBarrier`（static・package-private）＋ `PairClearInPassTest` 3 ケース。
- 既定 OFF の理由: colour を `TRANSFER_DST_OPTIMAL` ではなく attachment layout で消すことになり、
  `COLOR_CLEAR_TRANSFER` が Intel で測った CCS fast clear（263 µs → 17 µs）を失う可能性がある。
  **既定を動かす前に両 GPU で leg が要る。**
- 落とし穴: 遅延した clear の消費者は同じメソッドが無条件に記録する pass 1 つだけ。routing が失敗して
  frame image に落ちる分岐では、通常の clear をその場で記録して遅延を捨てる（`clear()` 系は
  `deferredClearPending` を落とす）。

## 5. A/B（同一 scene・同一 flags、スイッチだけ違う）

| leg | JAR | `pairClearInPass` | frame p50/p95/p99 | record p50 | batch p50 | present p50 | 判定 |
|---|---|---|---|---|---|---|---|
| `r50b-split` | `302cd2b6` | off | 2.7 / 4.5 / 88.5 | 0.4 | 0.4 | 0.1 | PASSED・validation 0 |
| `r50c-inpass` | `7b011fb3` | on | **2.6** / 3.4 / 4.4 | 0.4 | 0.4 | 0.1 | PASSED・validation 0 |

`CELTIUM_FRAME_PASS_SPLIT` の同じ 2 leg:

| bracket | off | on |
|---|---|---|
| `frame` | 1.3 | 1.3 |
| `world` | 0.9 | 0.9 |
| `gui` | 0.4 | 0.4 |
| `flush` | 0.4 | 0.4 |
| `winRecord` | 0.3 | 0.3 |
| `wrecLoop` | 0.2 | 0.2 |
| `grpDraw` | 0.1 | 0.1 |
| **`winHead`** | **0.1** | **0.0** |
| `winBarrier` | 0.0 | 0.0 |

**読めること**: pair clear は `winHead`（routing・pair clear・begin-info）の中にあり、スイッチ ON で
**0.1 → 0.0 ms** になった。frame p50 も 2.7 → 2.6。**約 0.1 ms/フレーム（フレームの 4%）**で、
`record`/`batch` は動いていない（pass 内の 1 コマンドは窓の記録に吸収されて 0.1 ms の分解能では見えない）。

**読めないこと**: p95 4.5 → 3.4 と p99 88.5 → 4.4 の差は**この変更の効果ではない**。両 leg の worst frame は
それぞれ 88.5 ms と 109.3 ms の読込スパイクで、最後の 60 フレーム窓の p99 はその 1 フレームが決めている。
p50 の 0.1 ms だけを主張する。

## 6. 計器の欠陥（この round で見つけた）

`CELTIUM_FRAME_BUDGET_SUMMARY`（teardown）が **`frames=0 (no frame boundary was measured)`** を 2 回
出力する一方、周期行は `frames=960` まで出ている。同じログの中で矛盾しているので、**teardown の要約は
読めない**（周期行は読める）。原因は未特定: `written` は `file()` でしか増えず、リセットは無い。
world teardown が 2 回走っている（fixture が world を出て GUI 段へ移る）ことまでは確認した。
次の round で `printSummary` の呼び出し元と `written` の可視性を追う。

## 主張しないこと

- 絶対 ms の機種間比較。`passSplit` と `frameBudget` は計器税を払う（`frame` p50 1.3 は KPI の 2.989 と
  同じ区間ではない）。
- `r50a`/`r50b` は tick 357 で終わり、**warm 窓（400–600）に到達していない**。warm 窓の内訳は未測定。
- スイッチ ON の速度。上の表が埋まるまで主張しない。
- Intel。この round の leg は AMD のみ。
