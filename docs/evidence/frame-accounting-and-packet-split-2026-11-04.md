# フレームの内訳が閉じた: 描画呼び出しが 25%、そして packet の 5 µs は `vkCmdDrawIndexed`（2026-11-04、round 46）

round 45 まで「鎖は record 0.5 + batch 0.5 + present 0.2」という**フレーム予算の列**で語っていた。
本 round は `CELTIUM_FRAME_PASS_SPLIT` の**入れ子が閉じている**ことを確かめ、その中で最大の項目を
`packetTimings` で割った。結果は 2 つで、**2 つめが「micro-opt の床」という前提を崩す**。

## 1. 条件

| | |
|---|---|
| Leg | `r46a-stages`（passSplit + windowSplit + batchTimings + frameStats）、`r46b-packet`（passSplit + windowSplit + packetTimings + bindTimings） |
| Renderer / fixture | `4665d8f7eec13b58eeae5afcb919efa99996a047db598e3475aaf3deea26a54d` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet=true` |
| Scene | `world`（`celtium-world-accept`、188 mods、`qmagnet`）、1280×720、`guiScale3.txt`、240 s、`sceneCalibrationKeyMatches=true` |
| Verdict | 両 leg `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED` |
| 注意 | 両 leg とも **bracket 税を払っている**。絶対 ms は KPI の値ではない。使うのは**同一 leg 内の配分**。 |

## 2. フレームの内訳は閉じている（`r46a-stages`、512 フレーム窓、p50 ms）

```
frame 3.6 = world 2.5 + gui 1.0 + hand 0.1 + residual 0.1
world 2.5 = flush 2.0 + terrain 0.3 + sky 0.1 + setupTerrain 0.1
flush 2.0 = winRecord 1.7 + winBarrier 0.2 + wfpMiddle 0.1
winRecord 1.7 = wrecLoop 1.1 + winHead 0.4 + winBeginPass 0.1
wrecLoop 1.1 = grpDraw 0.9 + grpPrep 0.1 + rcViewport 0.1
```

**足すと各段で閉じる。** つまり:

| 項目 | ms/フレーム | フレームの |
|---|---|---|
| **`grpDraw`（`vkCmd*` の描画呼び出しそのもの）** | **0.9** | **25%** |
| `winHead`（窓の off-target routing） | 0.4 | 11% |
| `winBarrier`（acquire + release） | 0.2 | 6% |
| `grpPrep` + `rcViewport` | 0.2 | 6% |
| `terrain` + `sky` + `setupTerrain` | 0.5 | 14% |
| `gui` | 1.0 | 28% |

`rcResolve` / `rcPush` / `rcPrepared` / `offCapture` / `offPrepare` / `offMerge` / `offAppend` はすべて 0.0。
**group ループの中で残っているのは描画呼び出しだけ**で、その手前の Java 側はもう床にある。

## 3. その描画呼び出しは 1 packet あたり 7 µs（`r46b-packet`）

```
packetPipelineMicros=7 packetPushMicros=0 packetBindMicros=1 packetDrawMicros=5
packetBindMillis=186 packetDrawMillis=496 packetCalls=98328 packetBindSkips=40488
packetRuns[packets=186059 runs=176346 meanPermille=1055 longest=14]
```

| 区間 | µs/packet | 何か |
|---|---|---|
| `packetDrawMicros` | **5.0** | `vkCmdDrawIndexed` 1 回（`drawRangeInternal` の全体） |
| `packetBindMicros` | 1.9 | `vkCmdBindVertexBuffers` + `vkCmdBindIndexed`（bind した packet だけ） |
| `packetPushMicros` | <1 | `vkCmdPushConstants`（208 byte） |
| `packetPipelineMicros` | 7（35 回だけ） | `vkCmdBindPipeline` |

**`vkCmdDrawIndexed` が 5 µs かかる。** 記録呼び出しとしては異常で、これが `grpDraw` 0.9 ms の主因である
（496 ms / 1209 フレーム = 0.41 ms/フレーム、bind 186 ms = 0.15 ms/フレーム）。**残り ~0.33 ms は未帰属**で、
push（<1 µs/packet）と pipeline bind（35 回だけ）では説明しきれない。次の判別は
`-Dceltium.debug.skipDrawCalls=true` の同一 leg。

**packet の 47% は bind form**（`packetRuns[packets=186059]` に対し `PacketCost` が数えたのは 98,328 =
draw form だけ。差の 87,731 が bind form という**導出**で、`BIND_FORM_BINDS` はまだ検証行に出ていない）。
draw form は「arena のスライス開始が stride の倍数」のときに成立し、`VulkanDynamicMesh` は
すでに `lcm(16, stride)` で確保している。**それでも半分が bind form になる理由は未特定**（owning mesh か
retained geometry か、`layout()` が null の family か）。次の判別は `DRAW_FORM_BINDS` / `BIND_FORM_BINDS`
を検証行に出すこと。

**この 5 µs はまだ A/B で確認していない。** `-Dceltium.debug.skipDrawCalls=true` の同一 leg で
`grpDraw` が 0.9 → ~0.4 に落ちれば確定する。落ちなければ `PacketCost` の区間が描画以外を含んでいる。

## 4. 受理経路（`r46a-stages`、`offerStages` は 1 ずれるので注意）

```
offerStages[nanosPerReached s1=75 s2=21 s3=299 s4=81 s5=28 s6=21 s7=25 s8=1838 s9=130 s10=5246 s11=1516 s12=45 s13=1421]
accept[decideMs=27 preflightMs=932 tailMs=0] parts[expand=72 matrix=64 extent=63 registry=1075 supply=3316 prepare=1672]
partCalls[266883 266883 266882 266883 138316 138316]
```

| 表記 | 区間 | ns | 母集団 | 1 フレーム |
|---|---|---|---|---|
| `s8` | マーク8→9: lightmap 検査・program query・state capture・state key・**registry lookup** | 1838 | 全 offer | 97 µs |
| `s9` | マーク9→10: merge 述語 | 130 | 全 offer | 7 µs |
| `s10` | マーク10→11: **supply + prepare** | 5246 | slow path | 143 µs |
| `s11` | マーク11→12: group open | 1516 | slow path | 41 µs |
| `s13` | マーク13→14: 頂点 append | 1421 | 受理 | 75 µs |

`immediateOffered=269443` / `frameId=1209` = **223 offer/フレーム**、`groupCreateCount=139856` = 116 group/フレーム、
`immediateWindows=28051` = **23.2 窓/フレーム**。受理経路の合計は **~0.36 ms/フレーム（10%）**。

`parts[supply=3316]` の 95% は **113 回の upload**（`mirrorUploads[count=113 bytes=0 millis=435]` = 3.85 ms/回、
`bytes=0`）。113 回 / 1209 フレーム = 0.09 回/フレームなので、**フレーム平均では 86 µs**。残りは epoch gate の
早期 return。`parts[registry=1075]` は memo ヒット 93.4%（`memo[hits=261636 misses=18491]`）なので、
**ミス 15.3 回/フレームがほぼ全部**（1 ミス ~14 µs の capture path）。

## 5. 窓の off-target routing は 22 µs/窓、そして world 行が出ていなかった

`r46a-stages` の `CELTIUM_WINDOW_SPLIT`（menu 行のみ）:

```
windows=206 route=1 offTarget=241 pair=1 publish=45 begin=23 commands=79 acquire=38 release=4 syncScan=80/70
```

**world 行は出ていない。** `celtium$printWindowSplit` の唯一の呼び出し元が
`celtium$releaseWorldResources` で、この leg は process-exit で終わったため。round 45 の r45a/r45b でも同じで、
**「world の off-target は未計測」が 2 round 続いていた**。本 round は shutdown hook から
`FileDescriptor.out` へ同じ行を出すようにした（Forge は `System.out` を Log4j に流し、Log4j の hook が
appender を止めるため、GL stub の no-op census と同じ経路）。

同じ round で `offTarget` を 5 つに割った（`off[pair sync trans clear fb]`）。既存の sub-mark
（`pair` / `publish` / `begin`）はすべて 0 で、22 µs はそのどれでもない部分にあったため。
**この 5 分割の値はまだ読んでいない**（次 round の leg が最初）。

## 6. 本 round のコード

1. `offTarget` の 5 分割（`wsOffPairNanos` / `wsOffSyncNanos` / `wsOffTransitionNanos` / `wsOffClearNanos` /
   `wsOffFramebufferNanos`）と `CELTIUM_WINDOW_SPLIT` の `off[...]` 列。
2. **`wsOffTargetNanos` の取りこぼしを修正**: 累算が publish 分岐の中にあったため、framebuffer が colour
   texture を報告しない窓は routing を払ったのに 0 と数えられ、**その分が次の窓の `pair` 列に載っていた**。
   分岐の外（off-target ブロックの末尾）へ移した。
3. **`framebufferColorTexture` の二重呼び出しを削除**: `chainIdentityValue` は
   `targetRegistry.currentIdentity()` そのもので、間に stub の framebuffer→colour-texture 表を触るものはない。
   2 回目は map 読み + `long[]` 確保が窓ごとに 1 回分無駄だった。
4. **`CELTIUM_WINDOW_SPLIT` を shutdown hook からも出す**（上記）。

## 7. 主張しないこと

- **絶対 ms の比較**。両 leg とも bracket 税を払っており、`frame` 3.6 ms は KPI の 3.03 ms ではない。
- **`vkCmdDrawIndexed` 5 µs の原因**。区間は `drawRangeInternal` そのものだが、A/B（`skipDrawCalls`）を
  取っていない。ドライバのどの経路かも分かっていない。
- **packet の 47% が bind form である理由**。draw form の成立条件は満たされているはずで、満たしていない
  母集団がどれかは未特定。
- **速度の改善**。本 round のコードは計器と 1 つの重複除去で、フレームを動かす主張はしない。
