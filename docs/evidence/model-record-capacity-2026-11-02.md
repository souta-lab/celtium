# model replay の per-frame segment は pack の 1 frame に足りない — 4096 は 2/3 を低速経路へ落としていた（2026-11-02）

renderer（rc.26、`main` 346ecc77c + 本差分）: r15（cap 16384）`03ab7a9604ab8cd20edc71d683a490660f0c0df7a17037197c2fb42c556768fd`、
r15b（cap 32768）`2a6f78fa7fdd853f3cbcb864c91cab082d02e39a57793605c15d16ca4e96a119`。
対照 r14e（cap 4096、同じ source の 1 つ前のビルド）`053f96db6e42fb06309daad872f6cd9655383e76a105a46dfce26367fb5a884f`。
fixture は r14e `990d4692…`、r15 `11f0c827…`、r15b `ccb7b9d7…`（同一 source の再ビルドで bytes が変わる）。
すべて AMD Radeon RX 6400 (RADV NAVI24)・mode `plain`・X11・validation 既定ON・220 s 上限・Supersymmetry instance・
`CELTIUM_LEG_COPY_PACK_DIRS=resources`（5901 files）・`menuHoldTicks=60`。

## 観測: 遅い経路に落ちた model replay の数

`CELTIUM_VERIFY` の `modelInstances[...]` は、`VulkanModelInstances` が受け取った replay のうち
**per-frame segment に書けた数**と**容量で断った数**を出す。3 つの leg の実測:

| leg | per-frame records | appended | declinedFull | attempts | 断り率 | runs | declinedMissing | pipelines |
|---|---|---|---|---|---|---|---|---|
| r14e | 4096 | 242,944 | 479,232 | 722,176 | **66.4 %** | 72 | 0 | 1 |
| r15 | 16384 | 951,808 | 514,048 | 1,465,856 | **35.1 %** | 68 | 0 | 1 |
| r15b | 32768 | 2,526,525 | 260,903 | 2,787,428 | **9.4 %** | 106 | 0 | 1 |

断りは容量だけが理由で、mesh/list/state の欠落ではない（`declinedMissing=0`、pipeline は 1 本）。attempts が leg 間で
違うのは、1 frame が覆う render pass の数が leg の速度で変わるため（下記）。

### 1 frame が要求する record 数（r15 の census、`-Dceltium.debug.modelRecords=400`）

world stage の frame 18..31 で 1,792 → 26,880 と立ち上がり、**frame 32..79 は毎 frame 26,880 で一定**、
16,384 を書き 10,496 を断った。r15b では frame 47..60 が **26,733 で 0 断り**（usedAtEnd 26,733/32,768 = 81.6 %）、
frame 109..120 は **37,380**（32,768 を書き 4,612 を断る）まで増えた。`worstFrameAttempts=37,380`、
`framesOverCapacity=56`（100 frame 中）。

```
r15b  CELTIUM_MODEL_RECORDS frame=60 attempts=26733 appended=26733 declinedFull=0 runs=1 usedAtEnd=26733/32768 segment=4
r15b  CELTIUM_MODEL_RECORDS frame=120 attempts=37380 appended=32768 declinedFull=4612 runs=1 usedAtEnd=32768/32768 segment=4
```

## 断ると何が起きるか（原典）

`ModelReplay.celtium$enqueueModelMesh` はまず `celtium$appendModelInstance` を試し、false なら
`b.prepareImmediate(ITEM, texture, key, state)` から per-group の `ImmediateGroup` を作る。
断られた replay は **256 B の record + 20 B の indirect command を 1 本の run に足す**代わりに、
group の確保・state のコピー・push block の準備・bind・個別 draw を払う。run は `vkCmdDrawIndexedIndirect` 1 回で
数千 instance を描くので、断りは「同じ絵を高い経路で描く」であり、**拒否＝欠落ではない**（`declinedFull` は
`modelDrawRefusals` ではない）。

## 変更

1. `ModelRecordCapacity`（新規・package-private）: 既定 **32768**、`MIN=1024`、`MAX=262144`、
   プロパティ `-Dceltium.vulkan.modelSegmentRecords=N`。既定は測定値（26,733 の plateau に +22 %、観測 peak 37,380 は
   まだ上回るので「足りた」とは書かない）。`MAX` は segment index（`segment*records+used`）が indirect command の
   `firstInstance`（int）に収まる上限から決めた。
2. `VulkanModelInstances.SEGMENT_RECORDS` はこの解決値。容量は 6 segment 分の host buffer を一度だけ確保する
   （32768 で records 48 MiB・commands 3.75 MiB）。
3. `-Dceltium.debug.modelRecords=N`（既定 0）: frame 境界で **前 frame の差分**を 1 行。attempts / appended /
   declinedFull / runs / usedAtEnd / segment / worstFrameAttempts / framesOverCapacity。追加の hot-path counter は無い
   （append が既に持つ counter の減算）。`describe()` にも `perFrameRecords` / `worstFrameAttempts` /
   `framesOverCapacity` を追加。

## 受入（この leg で言えること）

- r15b: `accepted=true`、`verdict=PASSED`、`problems=[]`、`validationMessageCount=0`、`validationAllowedCount=0`、
  `validationClean=true`、`gpuRequirementMet=true`、`fixtureStatus=PASSED`、world picture mean 105.75 / 非黒 0.9994
  （r15 は 102.33 / 0.9994、r15c は 102.91 / 0.9994）。描画は calibrated world gate を通り、絵の構造は同じ。

### 同一 jar の A/B（r15b vs r15c: 変えたのは `-Dceltium.vulkan.modelSegmentRecords` だけ）

同じ jar `2a6f78fa…`・同じ fixture `ccb7b9d7…`・同じ instance/flags/scene/200 tick。r15c は 4096 を明示、
r15b は既定 32768。

| 指標 | r15c (4096) | r15b (32768) |
|---|---|---|
| declinedFull / attempts | 479,488 / 663,040 = **72.3 %** | 260,903 / 2,787,428 = **9.4 %** |
| rendered frame（`frameId`、`EntityRenderer.func_181560_a` 1回に1） | 75 | **120** |
| `windowRecordMillis` / windows | 4,402 / 3,407 = 1.29 ms | 3,510 / 4,348 = **0.81 ms** |
| `actualFlushMillis` / flushes | 4,849 / 3,314 = 1.46 ms | 3,931 / 4,231 = **0.93 ms** |
| 最悪 flush | 324 ms | **178 ms** |
| 経過 | 196.7 s | 192.6 s |

同じ workload で、32768 の arm は **rendered frame が 75 → 120**、window は 28 % 多く記録しながら
recording+flush の合計 CPU は **9.25 s → 7.44 s**、最悪 flush は 324 → 178 ms。絵と gate は同じ。
fallback の消滅が記録側の仕事を減らした方向と整合する。

## 言えないこと（次の判別）

- **FPS の勝敗ではない**。上の A/B は同一 jar の容量比較であり、Celeritas arm の無い paired KPI ではない。
  「Celeritas より速い」の証拠には使わない。
- **steady state の record 数は未確定**。r15b の末尾 12 frame は 37,380 で一定だが、それは 200 tick の load の
  最後であって steady state の証明ではない。`holdUntilTick=620` + `-Dceltium.vulkan.presentedDumpEvery=0` の
  world leg で per-frame attempts を読み、既定を steady state から決め直す。
- **Intel 未実行**（r14e の色修正の Intel leg も未実行のまま）。
- 既定 32768 は r15b の末尾（37,380）に足りない。足りない分は census が frame 番号つきで出す。
