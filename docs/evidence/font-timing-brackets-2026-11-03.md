# フォント経路の時計 bracket は 1 glyph あたり 26 回で、この scene の glyph は 1 run に 2,456 個だった（2026-11-03、round 37）

`docs/evidence/accept-profile-jfr-2026-11-03.md` は「フォント経路が frame の 8.9%（`fastAtlasGlyph` 5.3% +
`celtium$flushPendingFontBatch` 3.6%）で、Celtium 自身のコードだから mod を触らずに届く3番目の項目」と書いた。
本 round はその 8.9% の**中身**を測った。結論は2つで、どちらも次の単位を変える。

1. **`CeltiumFontGlyphCapture` の時計 bracket は無条件で、1 glyph あたり 26 回の `System.nanoTime()` を取っていた。**
   これは計器の欠陥で、既定 OFF にした（`-Dceltium.font.timings=true` で戻る）。
2. **その修正はこの scene では速度にならない。** 世界 teardown に glyph census を足して数えたところ、
   `celtium-world-accept` の1 run 全体で **glyph は 2,456 個**（4,668 world frame で 0.53 個/frame）しかない。
   8.9% はこの scene のフォント経路ではありえない。**JFR の 8.9% は別の局面（menu/loading）か別 scene の数字**で、
   「フォント経路が3番目の項目」という読みは取り下げる。

## 1. 何が無条件だったか

`forge122/.../CeltiumFontGlyphCapture` の bracket は、F3 overlay の frame 費用を割るために足されたもので、
`openWindow` / `closeWindow` / `immediateBegin` / `immediateTexCoord`×4 / `immediateVertex`×4 / `immediateEnd` /
`beginGlyph` / `endGlyph` / `beginString` / `endString` / `beginRenderString` / `endRenderString` / `submit` が
すべて `System.nanoTime()` の対を取る。glyph 1個は「1 window + 1 begin + 4 texcoord + 4 vertex + 1 end + 1 close」
なので **26 対/glyph**。この host の `nanoTime` は実測 17 ns（`/tmp/NanoCost.java`、1M 回の平均 16.9–18.4 ns）なので
**~440 ns/glyph** が純粋な計器税で、capture 自身の仕事より大きい。

同じ形の欠陥が受理経路にも1つあった: `submitImmediateBatch` の `submitStart`/`submitNanos` の対は無条件で、
`BATCH_TIMINGS`（既定 OFF）と `FrameBudgetTimings`（既定 OFF）のどちらも読まない run でも毎 batch 取っていた。

## 2. 変更

- `CeltiumFontGlyphCapture.TIMINGS = Boolean.getBoolean("celtium.font.timings")`（既定 false）。上の全 bracket が
  これで分岐し、OFF では本体だけを呼ぶ。**カウンタは無条件のまま**（field increment で、受入 leg が読むのは
  個数のほう）。`summary()` は `timings=on|off` を出すので、millis 列が 0 なのが「測っていない」ためだと読める。
- `VulkanTerrainGpuBackend.submitImmediateBatch`: 対を `BATCH_TIMINGS || FrameBudgetTimings.enabled()` の下へ。
- **glyph census を世界 teardown に移した**（`CELTIUM_FONT_GLYPH_CENSUS`）。capture の summary は JVM shutdown hook
  から出るが、shutdown hook の stdout は log に入らない（leg `r39a-gate` で `CELTIUM_FONT_GLYPH_SUMMARY` が
  run のどの file にも無い、と `celtium$printFontRunCensus` の comment が既に記録している）。同じ summary を
  `celtium$setFontCaptureSummary` で backend に渡し、`CELTIUM_FONT_RUN_CENSUS` の隣で1回だけ出す。
  **これが無いと「フォント経路は安い」と「leg からフォント経路が見えない」が同じ無出力になる。**

## 3. A/B（同一 JAR `a6c7e576…`、AMD RX 6400、`celtium-world-accept`、1280×720、guiScale3、240 s）

flags は両 arm 共通で KPI のもの（`-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0`）。**validation は切ってあるのでこの leg は validation の主張をしない。**
差は1つだけ: `r37a-fonttim` は `-Dceltium.font.timings=true`（旧挙動）、`r37b-fontoff` は既定（新挙動）。

| | r37a-fonttim（bracket ON） | r37b-fontoff（bracket OFF） |
|---|---|---|
| verdict / fixture | PASSED / PASSED | PASSED / PASSED |
| `frameIntervalP50Ms`（cold window, ticks 120–200） | 2.040798 | **1.954168（−4.24%）** |
| P95 / P99 | 2.726484 / 3.844164 | 2.792081 / 3.750244 |
| samples | 1860 | 1933 |
| worldFrames | 4864 | 5609 |
| `presentedDistinctColours` | 76808 | 77100 |
| `presentedNonBackgroundFraction` | 0.990197 | 0.990197 |
| `presentedVerticalBands` / black | 720 / 0.007412 | 720 / 0.00728 |
| `presentedImageVerdict` / failures | PASSED / 0 | PASSED / 0 |
| `sceneCalibrationKeyMatches` | true | true |

**速度は主張しない。** −4.24% はこの project の ±7% バンドの内側で、arm は交互でなく固定順、window は
chunk build を含む cold window（ticks 120–200）。そして下の census が「この scene に glyph がほぼ無い」と言うので、
機構から説明できる差は ~0 である。

## 4. なぜ 8.9% がこの scene ではありえないか（新計器の答え）

leg `r37c-census`（JAR `7a41b75f…`、同じ scene、warm window を有効にした leg）の世界 teardown:

```
CELTIUM_FONT_GLYPH_CENSUS windows=2456 defaultWindows=0 unicodeWindows=0 quads=0 submitted=2456 accepted=2456
  refused=0 refusedQuads=0 submitMillis=0 windowMillis=0 hookMillis=0 glyphMillis=0 glyphCalls=2456
  stringMillis=0 stringCalls=512 renderStringMillis=0 renderStringCalls=1024 fastGlyphs=2456
  fontTextureRebinds=254 timings=off reasons={} modes={5:2456 …}
  fontRunsStarted=364 fontQuadsAppended=364 fontStripSeparators=0
  flushedIntervening=0 flushedBoundary=182 flushedStateChange=182 appendMillis=29
```

- **glyph 2,456 個 / run**（`worldFrames=4668` なので 0.53 個/frame）。`stringCalls=512`、`renderStringCalls=1024`。
- 26 対/glyph を外して浮くのは **2,456 × 442 ns ≈ 1.1 ms / run**、つまり frame あたり 0.2 µs。**無視できる。**
- `timings=off` が出ているので、既定 OFF が効いていること自体は leg で確認できる。
- `fontRunsStarted=364` に対し `fontQuadsAppended=364`、`appendMillis=29`（run 全体）。font run の合流も
  この scene では 29 ms/run で、frame の 8.9% にはならない。

したがって `accept-profile-jfr-2026-11-03.md` の「フォント経路 8.9%」は、**この scene の world frame の
フォント経路ではない**。同 leg の JFR は menu/loading の区間も含む 45 s を採っているので、そちらの数字が
world frame の内訳として読まれていた可能性が高い。**「フォント経路が3番目の項目」は取り下げる。**

## 5. 副産物: warm window の baseline と、leg の warm window の作り方

`r37c-census` は `-Dceltium.validation.warmFromTick=400 -Dceltium.validation.warmToTick=600
-Dceltium.validation.holdUntilTick=620` を付けた leg で、`worldTicksAtEnd=619`・`frameIntervalWarmSampleCount=3197`。
**この3つの flag を付けない leg は tick ~330 で終わり、`frameIntervalWarmP50Ms` が -1 になる**
（r37a/r37b がまさにそれで、cold window しか無い）。`scripts/gv_kpi.py` はこの3つを最後に渡している。

同じ JAR `7a41b75f…` の warm window（ticks 400–600、3197 samples）:

| frameIntervalWarm | p50 | p95 | p99 |
|---|---|---|---|
| ms | **2.873053** | 5.393299 | 7.038128 |

画像 gate は PASSED（distinct 76862・nonBg 0.990131・bands 720・black 0.007391）、`sceneCalibrationKeyMatches=true`。
**これは paired ではない1 arm なので条件 C の判定には使わない。** 参考として: 直近の paired KPI
（`paired-kpi-world-accept-2026-11-03.md`）の Celeritas は warm p50 2.288–2.484 ms、Celtium は 3.944–4.124 ms
だったが、あの run は外部負荷 1.07 core を記録している。本 leg の 2.873 ms は idle な machine の1点で、
**「1.68× の gap は外部負荷で膨らんでいた可能性がある」**を示すだけで、何も証明しない。次の paired set で測る。

## 6. 主張しないこと

- 速度（A/B は ±7% 内、cold window、固定順）。
- validation（両 arm とも `-Dceltium.vulkan.validation=false`。この round の変更は Java だけで `vkCmd` を触らない）。
- Intel（未実行）。
- 「フォント経路を直せば frame が軽くなる」— この scene の glyph は 2,456 個/run で、そうならない。
