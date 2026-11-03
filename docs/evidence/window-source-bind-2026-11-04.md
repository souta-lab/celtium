# 窓の 94% はフレームバッファ bind から来る（2026-11-04、round 55、leg `r55a-split`）

round 54 の handoff が「未読」としていた 2 つの数字（`accept[flushMs]` と `CELTIUM_WINDOW_SPLIT`）を
1 leg で取り、**窓の発生源が受理経路ではない**ことを確定した。あわせて、world phase の
`CELTIUM_WINDOW_SPLIT` が leg で読めない計器欠陥を直した。

## 条件

| | |
|---|---|
| Leg | `r55a-split`（frameStats + passSplit + frameBudget + windowSplit + batchTimings） |
| Renderer / fixture | `1997081ade1a6fc3ea4ff43c82a1427f6ccff748f1463f2558081166c902c80b` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65`（round 54 の stage） |
| GPU | AMD Radeon RX 6400（RADV NAVI24） |
| Scene | `world`（`celtium-world-accept`、188 mods、`qmagnet`）、1280×720、`guiScale3.txt`、240 s |
| 窓 | `warmFromTick=400 warmToTick=600 holdUntilTick=620`、`worldTicksAtEnd=619`、`worldFrames=5257` |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` + 上記 5 計器 |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`sceneCalibrationKeyMatches=true`、`worldFrameDistinctColours=76424`、`nonBackgroundFraction=0.989756`、`blackFraction=0.007151` |

**絶対 ms は性能の証拠にしない。** 5 つの計器を同時に付けた leg で、`frameIntervalWarmP50Ms=3.5555` は
round 51 の計器なし 2.85 と比較できない。使うのは (a) 窓の発生源、(b) 受理経路の内訳、(c) 窓の中の相対配分。

## 1. 窓の 94% はフレームバッファ bind

```
CELTIUM_FLUSH_SITES raw-write=1 bind-framebuffer=173470
actualFlushes=183841
```

**183,841 flush のうち 173,470（94.4%）が `bindFramebuffer` の site**（`VulkanTerrainGpuBackend.bindFramebuffer`
の `celtium$noteFlushSite(6)`）。raw-write は 1 回、残り 10,370 がその他。`worldFrames=5257` で割ると
**33 窓/フレーム**で、これは `windows[perFrameX100=2300]`（23/フレーム、別の分母）と `flush` p50 0.4 ms の
形に一致する。

**受理経路の index 上限（`IMMEDIATE_FLUSH_INDICES=8192`）は窓を作っていない。**
`accept[flushMs=189]` は run 全体で 189 ms、`flush` の p50 が 0.4 ms/フレームであることと合わせると、
**受理が起こした flush は 2/フレーム程度**。round 54 の handoff は「受理 0.77 ms のうち flush が何割か」を
次の標的にしていたが、**その flush は窓の 6% でしかない**。窓を減らす標的は受理経路ではなく
`bindFramebuffer` の ordering flush である。

## 2. 受理経路は 0.15 ms/フレームで、最大項目ではない

run 全体の累計（`CELTIUM_FRAME_STATS` の最終行）:

| | 累計 | 1 offer あたり |
|---|---|---|
| `accept[decideMs]` | 33 ms | 94 ns（全 offer） |
| `accept[preflightMs]` | 544 ms | 3,059 ns（slow path のみ） |
| `accept[flushMs]` | 189 ms | — |
| 合計 | **766 ms** | — |

`parts[...]`（1 呼び出しあたり、ns）:

| part | ns | 呼ばれた回数 |
|---|---|---|
| `expand` | 54 | 349,343（全 offer） |
| `matrix` | 58 | 349,343 |
| `extent` | 60 | 349,343 |
| **`registry`** | **834** | 349,343 |
| `supply` | 548 | 177,866（slow path） |
| `prepare` | 986 | 177,866 |

`registry` が全 offer で 834 ns なのは memo が効いていないからではない: `memo[hits=2921342 misses=121191]`
＝**ヒット率 96.0%**。121,191 回のミスが平均を押し上げている（`regHitCalls=121135` と一致）。
`fastMergedBatches=878425 / immediateOffered=1948733` ＝ **45.1%** が fast merge で、残り 55% が
3,059 ns の slow path。

## 3. 窓の中（`CELTIUM_FRAME_PASS_SPLIT`、p50 ms/フレーム、計器込み）

```
frame 1.4 = world 0.9 + gui 0.4 + hand 0.1
world 0.9 = flush 0.4 + terrain 0.1 + （ゲームの renderWorldPass 仕事）
flush 0.4 = winRecord 0.3 + （beginCommands/acquire/submit/end で 0.1）
winRecord 0.3 = wrecLoop 0.2 + winHead 0.0 + winPrefix 0.0 + winBeginPass 0.0 + winPassEnd 0.0
wrecLoop 0.2 = grpDraw 0.1 + （rcResolve/rcPush/rcPrepared/rcViewport で 0.1）
```

`winPublish`・`winTransition`・`winBarrier`・`winSubmit`・`winEnd` はすべて p50 0.0。
**round 54 の handoff が「winHead の残り 43 µs を名指す」としていた項目は、`WINDOW_SPLIT` の
`route`/`begin` としてすでに実装済みで、world phase では 0.0 ms**（下記 4 の計器欠陥で読めていなかっただけ）。

## 4. 計器の欠陥: world phase の `CELTIUM_WINDOW_SPLIT` が読めない

この leg の log にあった `CELTIUM_WINDOW_SPLIT` は **1 行だけ**で `windows=361`（menu phase）:

```
windows=361 route=2 offTarget=154 off[pair=21 sync=3 trans=69 clear=25 fb=12] pair=1 publish=22
begin=14 commands=86 acquire=34 release=5 syncScan=80/78
```

world phase の行は出ていない。`celtium$printWindowSplit()` の呼び出しは
`celtium$releaseWorldResources` の 1 箇所だけで、shutdown hook の `FileDescriptor.out` は
この leg の log にも `.out` にも残らなかった。**menu phase の数字は first-use 込みなので
world の per-window コストの証拠にならない**（`commands=86 µs` は slot wait と framebuffer 生成を含む）。

修正: `celtium$reportWindowSplitIfDue()` を frame boundary に置き、600 フレームごとに同じ行を出す。
カウンタは累計なので running average になる。次回の leg で world phase の per-window 配分が読める。

## 5. 本 round のコード

1. **attach 側の published-name hand-back を、その pass が attach する image の名前に限定**
   （`PostChainBridge.celtium$transitionPublishedFrameTextures(boolean, long attachImage)` と
   `celtium$attachHandBackNeeded`）。hand-back は `VUID-vkCmdBeginRenderPass-initialLayout-00900` を
   満たすためのもので、**attach しない image の名前を GENERAL に戻す遷移は誰も要求していない**。
   sampling 側（`general == false`）は狭めない: pass が sample しうる名前の集合はここでは分からない。
   呼び出し元は off-target routing の 1 箇所だけで、`attachImage` を渡さない既存の 3 箇所は従来どおり。
   `PublishedTransitionNarrowingTest`（4 テスト）が表を固定する。
2. **`CELTIUM_WINDOW_SPLIT` を frame boundary から 600 フレームごとに出す**（上記 4）。
   `attachNarrowed=` を同じ行に足し、狭めた名前の数を計器にした。

## 主張しないこと

- 速度の改善は主張しない。1 は world phase で `winTransition` p50 0.0 の項目を狭めただけで、
  上限は 0.05 ms/フレーム未満。2 は計器。
- `r55a-split` の絶対 ms は 5 計器込みで、KPI とも round 51 とも比較しない。
- 窓が 33/フレームであることと、その 94% が bind 由来であることは測った。**その flush を消してよいかは
  測っていない**: post chain の A→B→A→B では B の quad が A を sample するので、bind 時の flush は
  順序規則として必要になりうる。次の判別は「B の quad が実際に sample するか」（program 付き batch は
  拒否されるので、GregTech の post chain は Celtium では描かれていない可能性がある）。
