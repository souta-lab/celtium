# 描画ループに残っていた唯一の「決定に読まれない registry lookup」を出荷経路から外す（2026-11-04、round 62）

round 61 は窓の発生源を `flushReasons` で名指しした（bind 65%・clear 20%）。本 round はその窓の中の
**group ループ**を見て、`rcPrepared`（34.1 µs/フレーム）に混ざっていた診断専用の lookup を 1 つ外した。
あわせて、残る 0.52 ms が **1 round の最適化では動かない**ことを round 40 の census から確定させた。

## 1. 変更: `-Dceltium.vulkan.splashCensus`（既定 OFF）

`drawPendingImmediate` の group ループに、format-2 の group ごとに無条件で走る registry lookup があった:

```java
if (group.formatId == 2 && group.texture != 0) {
    int mirrorFlags = celtium$registry().celtium$mirrorSourceFlags(group.texture);
    if ((mirrorFlags & 1) != 0) this.targetStatistics.noteSplashSourcedGroups();
    if ((mirrorFlags & 2) != 0) this.splashPairAfterCompositeArmed = true;
}
```

**この lookup はどの draw の決定にも読まれない。** 消費者は 2 つだけで、どちらも診断である:

| 消費者 | 何のためにあったか | 状態 |
|---|---|---|
| `targetStatistics.splashSourcedGroups()` | ローディング画面の panorama が splash の白 canvas を sample していた件のカウンタ | 問いは決着済み |
| `splashPairAfterCompositeArmed` | `CELTIUM_SPLASH_PAIRPOST` の 1 回だけの readback を arm する | 同上 |

`mirrorSourceFlags` は registry の monitor 下のハッシュである。format 2 は測定 scene の受理 batch の 18%
（`acceptedByFormat=[1:7645, 2:60110, 6:201447, 7:23476]`）なので、窓ごと・フレームごとに払われていた。
`-Dceltium.vulkan.splashCensus=true` の下へ移した。既定 OFF で、`splashSourcedGroups=0` のまま
（leg `r62b-gate`）— カウンタが 0 なのは census を切ったからで、絵の欠落ではない。

## 2. 残る差の構造（round 40 の census を再確認し、確定させた）

group open の理由（`mergeRefusals` と `open-*` の 2 経路が同じ形を出す）:

| 理由 | 割合 | 畳めるか |
|---|---|---|
| **format** | **63%** | **現行アーキテクチャでは畳めない**（round 40 §3） |
| **texture** | 24% | **bindless でしか畳めない** |
| affine（texgen / ITEM） | 12% | 一部 |
| matrix | 3% | — |

- **format が畳めない理由**は色の出どころが違うこと: `immediate_position_tex.vert`（format 6）は色を
  push block から取り、`immediate_tex_color.vert`（format 2）は頂点ごとの `a_Color` を取る。1 本の pipeline に
  すると色の量子化が要る。format 分割のうち **2↔6 が 52%**・2↔4 が 30%・7↔2 が 14%。
- **texture が畳めない理由**は immediate の pipeline が texture ごとの descriptor set を張るから。
  `ImmediateGroup.textureArrayIndex` は既にあるが、**draw records（indirect）専用**で、
  immediate の sampling には使われていない（`grep textureArrayIndex` の 3 箇所）。

したがって残る 0.52 ms を消す道は 2 つだけで、どちらも複数 round の作業:

1. **immediate の bindless texture 化** — pipeline layout・push block・shader の 3 つを同時に変える。
   texture 24% が消える。
2. **不透明 group 専用の 2 スロット open group** — format 交替で 3 つ目の group を開かず、前の slot に
   append する。format 63% のうち 2↔6 の 52% が対象。`closeCurrentImmediateGroup` が mesh を group に
   渡した後も append できることが前提で、flush で slot を無効化する規則が要る。

**round 62–64 の残りでは入らない。** これは「見つからなかった」ではなく「見つかった上で大きすぎる」で、
次の round 以降の設計入力として残す。

## 3. 測定の罠（本 round で確定）

**leg の絶対 ms は KPI の絶対 ms ではない。** `scripts/gv_leg.py` は常に
`-Dceltium.vulkan.validation=true` を渡し（369 行）、`VulkanDiagnostics.enabled()` が真になる。すると
`TARGET_CENSUS_ENABLED = targetCensus || VulkanDiagnostics.enabled()` が真になり、検証 layer と
診断が受理・記録の両経路に載る。本 round の leg の `immediateSubmitAvgNs=11068` は round 59 の
`batchTimings` leg の 3915 の **2.8 倍**で、同じコードの同じ scene である。**leg の warm p50 を
KPI の frame interval と並べて「遅い/速い」を言ってはいけない。**

## 4. 受入（leg `r62b-gate`）

| | |
|---|---|
| renderer / fixture | `0dc7fc3b10d0f86e517835a35f97ac4cec9f4b1f6ebfd71e03851da0ee335db2` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65`（fixture 不変） |
| GPU / scene | AMD Radeon RX 6400 (RADV NAVI24)、1280x720、`guiScale3.txt`、`sceneCalibrationKeyMatches=true`、`sceneGateCalibrated=true` |
| 判定 | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED` |
| エラー | `validationMessageCount=0`、`validationAllowedCount=0`、`validationClean=true`、VUID/DEVICE_LOST/Celtium 例外 0 件 |
| 規模 | `worldFrames=1675`、`immediateOffered=244722`（146/フレーム）、`immediateGroupsClosed=193119`（115/フレーム）、`immediateWindows=35450`（21.2/フレーム）、`actualFlushes=33697`（20.1/フレーム） |
| 絵 | `worldFrameDistinctColours=76151`、`worldFrameBlackFraction=0.006249`、`worldFrameNonBackgroundFraction=0.989756`。目視でも地形・色付きブロック・entity（TNT/チェスト/ベッド/羊/レッドストーン/ネザーポータル）・HUD 文字・ミニマップ・ホットバーのアイコン・クロスヘア・手持ちアイテムが正しい |

## 5. 棄却した仮説（再入禁止）

- **「bind の target 変更 flush を遅らせれば窓が減る」**（round 61 の next step 1）: **棄却。**
  queue は target ごとで、窓は現在の target の group しか描かない。bind の flush を落とすと旧 target の
  group は次の窓まで残るが、その窓が来る保証はなく、frame boundary の
  `celtium$dropUnflushedTargetGroups` が捨てる。順序規則（新 target の draw が旧 target の image を
  sample し得る）は bind でしか満たせない。GregTech の bloom pyramid は各段が前段を sample するので、
  まさにその規則が要る。
- **「`patchLevels[... reachUnwritten=17355]` は Celtium の mip 欠落バグ」**: **棄却。**
  `VulkanGlTexture` の class doc が明示するとおり、region patch は producer が供給した level だけを書き、
  上位 level は「GL の `glTexSubImage2D` が同じ呼び出しの後に持つ内容」と同じものを保つ。GL も同じなので
  Celtium 固有の欠落ではない。`reachUnwritten` は「sampler が届く level を patch が書かなかった」を数える
  計器で、level 0 だけを patch する producer では 0 にならない。**「0 が保証」という文言のほうが誤り。**
- **「受理経路の `registry().get()` が 654 ns/offer だから memo を強くすれば速くなる」**: **棄却。**
  `VulkanTextureRegistry.get(int)` は既に 256 slot の direct-mapped memo を持ち、ヒット判定は
  `announcedGenerationOfInt`（配列読み）と `staleIds.isEmpty()` だけで、`confirmSamplerStateFromRecord` も
  パラメータ版の比較で早期 return する。round 40 の 654 ns は memo 強化前の値。
