# model replay の hit は 1 replay 何 ns か — 同じ leg で guard と前段を分けた（2026-11-02）

測定した renderer `1c06ef6b861c06b601d4cb0e4a3fa7b983c7d243f1c354f244c8c925c3b0e06d`、fixture
`b83bbc698e79e0fa350c7258afe99bf78b19e133446a418128f46de9af601b93`（rc.26、`main` 5093285f2 + 本差分）。
commit する tree からの再ビルドは **同じ 2 つの hash を再現**した（leg で測った jar = commit する jar）。
AMD Radeon RX 6400（RADV NAVI24）・mode `plain`・X11・validation 既定 ON・200 s・Supersymmetry instance・
`CELTIUM_LEG_SEED_OPTIONS=guiScale3.txt`（guiScale=3、renderDistance=12、Black_Mesa/ShaderTech resource packs）・
`-Dceltium.vulkan.batchTimings=true`。

## 計器: enqueue の内側に fast path の bracket を足した

r18 の `modelEnqueueMillis=865 / 2,149,974` = 402 ns/replay は **enqueue 経路全体**で、その中身
（texture registry・matrix relativisation・projection remap・guard・record・census）のどこが高いかを言えなかった。
`celtium$tryModelReplayFastPath` の呼び出しを `-Dceltium.vulkan.batchTimings` で bracket し、成否別に
`modelFastPath[hitNanos= hitCalls= missNanos= missCalls=]` を出す。外側の `modelEnqueueMillis` からこの 2 つを引いた
残りが前段である。通常の run では nanoTime を呼ばない（switch OFF）。

## 否定: lit payload を「入力」で証明すると hit が 0 になる（leg `r19b-fastpath`）

最初の実装は lit payload を **入力**（light generation + modelview の rotation 9 floats）で証明し、20 floats の
再構築と比較を消そうとした。結果:

```
modelFastPath[hit=0 miss=2940979(shape=747 inputs=2940232 run=0) ...]
modelEnqueueMillis=1885 modelEnqueueCalls=2940981        (= 641 ns/replay)
```

2,940,981 replay が **全 miss**。理由は payload の不変性で、`celtium$lightParams` は
`inv(R_now) * eye_dir`、`eye_dir` は light を set した時の modelview `R_set` で作られている。entity 描画は
entity ごとに同じ world 方向の light をその modelview の下で set し直すため、出力 20 floats は **modelview が
変わっても同じ**。入力キーは「同じ出力を別の入力で再現する」replay を全部 refuse する。r18g の 98.9 % hit は
**byte 比較だから**成立していた。よって比較は 20 floats の byte に戻した（比較は live 配列で行い、probe への
copy はしない）。この leg は画像 gate・validation 0 で PASSED だったので、「速くならないが正しい」変更だった。

## 陽性: guard は replay 単価の 75 %（leg `r19d-fastpath`）

同 leg の計器（102 world frames、16,321 replay/frame）:

| 項目 | 値 |
|---|---|
| `modelDraws` | 1,664,769 |
| `modelFastPath[hit=]` | 1,634,942（98.21 %） |
| `modelFastPath[miss=(shape= inputs= run=)]` | 29,825（593 / 29,232 / 0） |
| `modelEnqueueMillis / calls` | 637 ms / 1,664,769 = **382.6 ns/replay** |
| `hitNanos / hitCalls` | 471,599,642 / 1,634,942 = **288.5 ns/hit** |
| `missNanos / missCalls` | 6,252,455 / 29,827 = **209.6 ns/失敗 guard**（missCalls は miss を数えない早期 return も 2 回含む） |
| 前段（enqueue − hit − miss） | (637e6 − 471.6e6 − 6.3e6) / 1,664,769 ≈ **95.6 ns/replay** |
| `modelReplayCache[light]` | hit=737,589 miss=956,412（**56.5 % の replay が payload を再構築**） |

つまり **guard + record + census が 288 ns（75 %）、texture lookup・matrix relativisation・projection remap の
前段が 96 ns（25 %）**。両方に nanoTime 2 回（bracket の内側と外側）が含まれるので、実 work はこの数字より
数十 ns 小さい。guard の中では「payload 再構築が replay の 56.5 %」が最大の単一項目で、record write は
offline 実測 23.6 ns に過ぎない。したがって次の実装単位を array-records（record の縮小）に置く根拠は、
この leg では弱い。

同じ code の直前 build（`6d3b6caa…`、leg `r19c-fastpath`、1,519,617 replay / 92 world frames）でも
**hit 315.4 ns・前段 104 ns（合計 419 ns、hit 98.86 %）**で、比率は同じ 75/25 だった。単価の絶対値は leg 間で
±10 % 動く（replay 数 1.52M vs 1.66M、frame 数 92 vs 102）ので、読むのは比率と内訳である。

## この round の変更（測定した jar と同一の tree）

1. 計器（上記）。`ModelReplayFastPathTest.theTimingCensusIsSplitByAnswer` が成否別の加算を固定する。
2. guard の値比較から probe への copy を削除: projection 16・lightCoord 2・combine 4 floats を live 配列のまま
   比較する（`matchesValues` が引数で受ける）。lit payload も live 配列で比較する。
3. shape guard の二重実行を削除（`matchesShape` の成功だけが tuple を `matched` にし、`matchesValues` は
   precondition を文書化した value 半だけ）。成功時のみ `matched` を設定するので、shape miss が前の list の
   tuple を「一致した」ように残さない。
4. `celtium$lightParams` の入力を `celtium$lightParamsFromRotation`（9 floats）へ分離し、inverse を
   `celtium$invertRotation`（static）に切り出した。`ModelLightRotationTest` が R·R⁻¹=I を 4 ケースで固定する
   （入力キー guard は撤回したが、payload の幾何はこの分離で device 無しに検証できる）。

## 受入

| leg | jar | 結果 |
|---|---|---|
| `r19b-fastpath`（入力キー guard、撤回） | `434e5f0b…` | `PASSED`・CALIBRATED（distinct 12572、black 0.00377）・validationMessageCount=0・modelDrawRefusals=0・例外 0。**fast path hit=0** |
| `r19c-fastpath`（同 code、直前 build） | `6d3b6caa…` | `PASSED`・CALIBRATED（distinct 12640、black 0.00377）・validation 0・hit 98.86 %・315.4 ns/hit。単価の再現用 |
| `r19d-fastpath`（最終・commit する jar） | `1c06ef6b…` | `accepted=true`・`verdict=PASSED`・`fixtureStatus=PASSED`・`sceneCalibrationKeyMatches=true`・floors enforced・presentedWorldFrame distinct **11318**・black **0.00377**・`validationMessageCount=0`・`validationAllowedCount=0`・`validationClean=true`・`validationExitCode=0`・`gpuRequirementMet=true`・`modelDrawRefusals=0`・`modelLastRefusal=none`・`at org.soutalab.celtium` の stack frame 0・AMD RX 6400・169 s。画像 `screenshots/celtium-presented-world-run-1e67b36c15d9-330bedb4-kept.png`（1280x720） |

CPU: build 260 tests / 0 failures（`ModelReplayFastPathTest` 13・`ModelLightRotationTest` 4・`ModelReplayInputCacheTest` 7・
`ModelStateSignatureTest` 6）・route verify called=241 failed=0・classes 87/87・colour 32/32・texcoord 64/64・
scripts tests 106 OK・check_agent_docs OK。

## 言えないこと

- **frame 時間/FPS の改善は主張しない**。replay 単価 419 ns（r19c）と 402 ns（r18g）は replay 数（1.52M vs 2.15M）も
  frame 数も違う別 leg で、比較ではない。この leg の world は 92 frames / 164 s（約 1.8 s/frame）で、replay の
  CPU はその一部に過ぎない。
- 前段 104 ns は外側 bracket の nanoTime 2 回を含む。内訳（texture lookup / 相対化 / projection remap）はまだ
  分けていない。
- `r19b` は入力キー guard の否定であり、画像 gate は「黒でない・色数」しか見ていない。payload の byte が
  正しいことの証明は、同じ経路が r18 から変えていないこと（比較対象と比較方法）に依る。
