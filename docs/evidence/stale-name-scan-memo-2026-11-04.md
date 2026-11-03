# 窓 head の 0.1 ms は、答えの変わりえない走査だった（2026-11-04、round 45）

round 44 は窓の固定費を 3 µs/窓と測り、`winHead` の 0.1 ms/フレームを「未帰属」として残した。
本 round はその中身を名指しし、**窓ごとに走っていた stale-name 走査の memo キーが、13 窓/フレームの
scene では必ず外れていた**ことを確定した。キーを「その identity の pair generation」に変え、
`winRecord` は **0.4 → 0.3 ms/フレーム p50** になった。

## 条件

| | |
|---|---|
| Leg | `r45a-syncmemo`（memo 修正のみ）・`r45b-scan`（+ 走査カウンタ）・`r45c-gate`（validation ON） |
| Renderer | `10fe7cb889409941d777f334cace904581b758c643e6579364de0d7022ea6bbd`（r45a）／`4665d8f7eec13b58eeae5afcb919efa99996a047db598e3475aaf3deea26a54d`（r45b・r45c） |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet=true` |
| Scene | `celtium-world-accept`（188 mods、`qmagnet`）、1280x720、`guiScale3.txt`、`sceneCalibrationKeyMatches=true` |
| Flags（r45a/r45b） | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.windowSplit=true`（r45a は `frameBudget` も） |
| Flags（r45c） | `-Dceltium.vulkan.validation=true -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Verdict | 3 leg とも `PASSED`、`problems=[]`、`fixtureStatus=PASSED`。r45c は `validationMessageCount=0`・`validationClean=true`・run dir 全体で `VUID` 0 行・`presentedDistinctColours=77002` |

## 観測 1: 走査は窓ごとに走り、答えは変わりえない

`PostChainBridge.celtium$syncPublishedChainOutput` は off-target 窓ごとに published name 全数を走査し、
各名に 2 つの boxed-key lookup を行う。memo キーは `(identity, pair の colour image, publishedVersion)`。

現行 scene は **13 窓/フレームが GregTech の post chain**（[窓の発生源](window-sources-2026-11-03.md)）で、
**13 個の異なる framebuffer identity** を bind する。identity がキーに入っているので、この 13 窓は
**毎フレーム必ず外れる**。走査が探すのは「pair が作り直されて image が死んだ名前」だけであり、
その間に pair は 1 つも作り直されていない - **見つかりえない走査を 13 回/フレーム走らせていた**。

## 観測 2: キーを generation にすると memo が当たる（`syncScan` カウンタ）

`CELTIUM_WINDOW_SPLIT` に `syncScan=runs/hits` を足した。menu の 312–332 窓で **`syncScan=93/71`**
（走査 93 回・memo 71 回）。menu は pair と framebuffer を作る phase なので走査が多い。
**world の行はこの leg では出ていない**（`celtium$releaseWorldResources` が走らず process-exit で終わった。
r44 の leg では出た。leg 側の非決定性で、計器の欠陥ではない）。

## 観測 3: `winRecord` が 0.4 → 0.3 ms/フレーム p50

同一 flags・同一 scene・同一 GPU の 2 leg（r44 の `r44a-split` と本 round の `r45a-syncmemo`）で、
`CELTIUM_FRAME_PASS_SPLIT` の最終 5 行はどちらも安定していた:

| bracket（p50 ms/フレーム） | r44（`7033d622`） | r45（`10fe7cb8`） |
|---|---|---|
| `winRecord` | **0.4**（5 行すべて） | **0.3**（5 行すべて） |
| `winHead` | 0.1 | 0.1 |
| `wrecLoop` | 0.2 | 0.2 |
| `winPrefix`/`winPublish`/`winPassEnd`/`winBeginPass` | 0.0 | 0.0 |
| `flush`（窓全体） | 0.5 | 0.5 |
| `frame` | 1.3–1.4 | 1.3–1.4 |

`winRecord` は `recordImmediateOnlyPass` そのものなので、差は **窓の記録の内側**で起きている。
`winHead` が 0.1 のままなのは、その p50 が 0.1 ms 分解能で、走査の ~0.05 ms がその中に埋もれるため。
**0.1 ms の差が `winHead` の外の bracket に出ていない**ことは、差が `winRecord` の
bracket されていない残余（`winHead`+`wrecLoop`+4 つの和と `winRecord` の差）にあることを意味する。
これは「走査が `winHead` の中にない」ということではなく、**0.1 ms 分解能では帰属が決まらない**ということである。

## 観測 4: フレーム間隔は動いたが、A/B としては弱い

fixture の `frameIntervalP50Ms`（warm flag 無し、tick は止めていない）:

| leg | p50 | p95 | p99 | worldFrames | ticks |
|---|---|---|---|---|---|
| r44a-split | 2.108 | 3.107 | 3.984 | 5,452 | 353 |
| r45a-syncmemo | 2.074 | 3.011 | 3.808 | 5,217 | 335 |
| r45b-scan | 2.008 | 2.698 | 3.659 | 5,842 | 363 |

p50 は 2.108 → 2.008（**4.7%**）、p95 は 3.107 → 2.698（13%）。ただし **tick 数が 353/335/363 と揃っておらず、
r45a と r45b は同じコードで p95 が 3.011 vs 2.698（10%）違う**。したがってこれは
「フレームが速くなった」の証拠ではなく、**この scene の leg 間ノイズが ±10% あること**の測定である。
本 round の主張は bracket（`winRecord` 0.4 → 0.3）までとする。

## 本 round のコード

1. **`celtium$syncPublishedChainOutput` の memo キーを `(identity, chainPairGeneration(identity),
   publishedVersion)` に**。generation は pair が作られる/作り直されるたびに進む単調カウンタ
   （`GllessFrameTarget.Pair.generation = ++chainPairGenerations`）なので、
   「その identity の pair が作り直された」＝「名前が stale になりうる」と同値である。
   identity ごとに覚えるので、**ある framebuffer の窓が別の framebuffer の走査を消費しない**。
   frame target が入れ替わって generation が巻き戻ったら map を捨てる（`syncScanTotal`）。
2. **`CELTIUM_WINDOW_SPLIT` の欠落区間を修復**。publish の site が `wsMark` を加算せずに上書きしていたため、
   route mark から publish mark まで（`ensureChainPair`・走査・`transitionPublishedFrameTextures(true)`・
   pair clear・framebuffer lookup・`framebufferColorTexture`）が**どのカウンタにも入っていなかった**。
   新 `offTarget` 列として加算する。round 44 の `pair=0` は「pair が無料」ではなく「未計測」だった。
3. **`syncScan=runs/hits` を同列に追加**（memo が当たっていることを測定にする）。

## 主張しないこと

- 「フレームが 4.7% 速くなった」とは言わない（tick 数が揃っていない。同じコードの 2 leg で p95 が 10% 違う）。
- world の `offTarget` µs/窓は測れていない（world teardown が走らなかった）。menu の 98–100 µs/窓は
  pair と framebuffer の初回生成を含む値で、world の値ではない。
- `winHead` 0.1 ms の内訳は 0.1 ms 分解能では分けられていない。走査が 0 になったことは
  `syncScan` の hits で言えるが、それが `winHead` の何割だったかは言えない。
- Intel arm は未実行。
