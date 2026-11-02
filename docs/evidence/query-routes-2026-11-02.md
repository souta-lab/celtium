# ネイティブ `glGet*` のクエリ: 一致しないルートは「それらしい0」を返す（2026-11-02, round 11）

Owner: **custom rendering fidelity**（mod の save/restore が読む状態クエリ）。Unit: 「mod が GL に訊いた値は GL が
答える値と同じ」。

## 症状（前 round の実測から）

leg `r30b-attribmasks`（同一 frame・同一 raster）で:

* `glGetFloatv(GL_COLOR_WRITEMASK)` は `1111`、`glGetBooleanv(GL_COLOR_WRITEMASK)` は `0000`。実 GL（Celeritas
  参照 `r30c`）は両方 `1111`。pipeline 自身の state key histogram は全 group `0xf`。
* fixture の tex 設定は `GL_LINEAR` なのに `glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER)` は `0`。

in-process の dispatch テスト（stub の table 経由）は両方正しく答えており、route verify も緑だった。

## 根因

transformer が LWJGL3 の native を置き換えるときの分岐が:

```java
if (returnType.equals("V") && "(IJ)".equals(descriptor))          // boolean
if (returnType.equals("V") && "(IIJ)".equals(descriptor))         // tex parameter
```

descriptor は**戻り値型を含む**（`(IJ)V` / `(IIJ)V`）ので、この比較は一度も一致しない。一致しない native は
汎用の zero-writer（`glGetZero(out, 1)` が1バイト/1 int の0を書く）に落ちる。class は正常にロードされ、呼び出しは
例外も出さないため、ロード検証・bytecode 検証・`called=241 failed=0` のどれからも見えない。同じファイルの別の
route は既に `startsWith` に直されており、そのコメントが「`(IJ)` との一致は実 native に決して一致しない」と
書いている隣に、この2箇所が残っていた。

## 修正

* `descriptor.startsWith("(IIJ)")` / `descriptor.startsWith("(IJ)")`。これで `nglGetBooleanv` は
  `CeltiumGllessHint.glGetBooleanv`、`nglGetTexParameteriv/fv/Iiv/Iuiv` はそれぞれの専用ハンドラに入る。
* `glGetIntegerv(GL_CULL_FACE_MODE)` の `cf=1` も同じ「状態モデルに2つの符号化」だった: `NativeRasterState.cullFace`
  の初期値だけが内部 enum（`CULL_BACK=1`）で、書き手（raw adapter / managed setter / stub）は GL enum を入れていた。
  `VulkanGlState.CULL_FRONT/BACK/BOTH` を GL の enum（0x0404/0x0405/0x0408）にし、初期値も GL_BACK にした。

## 検証（CPU・実機）

CPU（`scripts/cpu-contract/transformer-route-verify.sh`）:

* phase 5: 実際の LWJGL class（`GL11C`, `GL30C`）を変換し、method body が**専用 hint** を呼ぶことを読む。
  `query natives routed=8/8`（boolean/float/double/integer/tex iv/fv/Iiv/Iuiv）。
* phase 6: stub を install し、stub の dispatch で状態を書き、変換後の native を**実行**して答えを読む:
  `query answers boolean=1010 float=1010 texFilter=0x2601`。
* 陽性対照: route を旧実装に戻すと phase 5 が `query natives routed=3/8`、phase 6 が
  `boolean=0111 float=1010 texFilter=0x0` で FAIL（`0111` は zero-writer が1バイトだけ書き、残りが sentinel
  `0x7F` のままであることの証拠）。修正を戻すと再び PASS。

実機 AMD（同一 fixture JAR・同一 scene・`guiScale3`・validation ON・300 s）:

* Celtium `r31a-queryroutes`（renderer `c06cf023…`、fixture `3d4ae395…`、AMD RX 6400 RADV NAVI24、plain、
  1280x720、guiScale3、validation ON、300 s）: exit 2 `UNCALIBRATED_PRESENTED_IMAGE`（named screen なので正しい）、
  problems 0。`displayListDrawnFrames=62`、dumps は presentedFrame 10/11 で completed=true。
  `attribBefore=cm=1111,cmB=1111,dm=1,bs=770,bd=771,dt=1,cf=1029,sb=0,0,1280,720,po=0.0`、
  `attribInside=cm=0000,cmB=0000,...,cf=1028,...`、`attribAfter`=before、
  `attribOutsideAfter=cm=0000,cmB=0000`、`booleanQueryMismatches=0`、`textureFilter=9729`
  （`textureFilterExpected=9729`）。checker **PASSED**（armD_green=3600 / armE_green=0、既存4arm不変、
  陽性対照4種すべて拒否）。健全性: `validationMessageCount=0` / `validationClean=true`、VUID 0、device lost 0。
* Celeritas 参照 `r31b-queryroutes-ref`（`567dcf8a…`、fixture 同一 `3d4ae395…`、同じ scene/flags/300 s、
  radeonsi navi24）: exit 2 `UNCALIBRATED_PRESENTED_IMAGE`、**同じ readback**（before/inside/after/outside、
  `cmB`、`cf=1029`、`textureFilter=9729` まで一致）、`--picture` で checker **PASSED**（4陽性対照すべて拒否）。
  実 GL が同じ値を返すので、この round の修正は Celtium 固有の言い訳ではない。
* 再現性: leg が使った stage の JAR（renderer `c06cf023…`、fixture `3d4ae395…`）と、commit する tree からの再ビルドを
  entry 単位で比較すると **両 JAR とも全 entry が byte 一致**（renderer 5252 entry / fixture 33 entry、差分 0）。
  renderer JAR の全体 hash も `c06cf023…` で一致し、fixture は zip metadata だけが違う（`57f13ed6…`）。
* 画像: `docs/evidence/query-routes-2026-11-02.png`（上: Celtium の presented dump `displaylist-f8-a`、
  下: Celeritas の同 scene screenshot）。状態文字列はこの frame のもの。

## 残り

* `GL_LIGHTING_BIT`/`GL_FOG_BIT`/`GL_STENCIL_BUFFER_BIT`/`GL_VIEWPORT_BIT` は attribute stack の未対応集団のまま
  （census の `selectedUnrestoredGroups`）。`GL_TRANSFORM_BIT` は行列が mirror 側。
* `glGetBooleani_v` は依然として1バイトの0を返す（indexed query の route は未実装）。
