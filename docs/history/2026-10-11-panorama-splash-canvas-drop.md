# パノラマ: スプラッシュの白キャンバスを落とす修正 (landed、ただし受入は未達)

Owner: **fog-path**. Date: 2026-10-12, ~14:08 JST. GPU: AMD RX 6400 (RADV NAVI24).

## landed したもの

1. **スプラッシュ由来アップロードの識別**: `TextureUtilMixin.celtium$announceUploadedPixels` が自分の
   スタック (既に `CELTIUM_UPLOAD_CALLER` を作っている場所) で `SplashProgress` / `drawSplashScreen` /
   `func_180510_a` を探し、`announceDynamicTexture(id, pixels, w, h, fromSplash)` として渡す。
   backend 側で同じスタックを歩く版は 2 leg とも発火しなかったので、事実は mixin から渡す形にした。
2. **スプラッシュ終了時のキャンバス破棄**: `splashCanvasIds` に id を記録し、**スプラッシュのアップロードが
   無かった最初のフレーム**で `textureRegistry.forgetTexture(id)` を呼ぶ。呼び出し位置は present フック
   (`celtium$presentFrame`) — ワールド用のフレーム境界は menu では来ないため。カウンタは
   `splashCanvasInvalidations` (report)。
3. **計測器** (前 unit から継続): `CELTIUM_MIRROR_DRAW` (描画時の groupTexture / preparedKey / resolvedKey /
   resolvedSize / descriptor / mirrorSource)、`splashSourcedGroups`、`VulkanTextureRegistry.celtium$mirrorSourceFor(int)`。

build 緑、`shader_push_constant_check` 33 shaders / 0 problems。

## 受入は未達

同じ menu-hold leg (jar `fog-path-splashdrop3.jar`) で:

```
CELTIUM_TEX_COPY_SERVED dest=21 … mean=255,255,255 opaque=65536/65536 served=1 frame=0
CELTIUM_MIRROR_DRAW frame=0 groupTexture=8 … mirrorSource=announced-int[]@256x256 target=1972137837 batches=2
```

`mean` は 255 のまま、描画時の id 8 の mirror も依然 `announced-int[]` (スプラッシュの白キャンバス)。
`nonWhite > 0` も未達。

## 未確定の点 (次に読むべきもの)

- 破棄が実際に走ったかは **report の `splashCanvasInvalidations`** でしか読めない: 破棄の記録は
  `record(...)` で diagnostics list 行き、fixture 無し leg の console には出ない。次の leg は fixture 付きで
  `splashCanvasInvalidations` と `splashSourcedGroups` を読む。
- 破棄が走っているのに mirror が白なら、**破棄の後に announce が再充填している** (texture-mirror の
  `int[]` メモと同じ筋)。その場合の修正は「破棄済みキャンバスへのスプラッシュ由来アップロードを拒否する」
  で、`fromSplash` の配線はそのために既に入っている。
- 白が id 8 ではなく**別の texture** である可能性は残る (id 9 は `opaque=4015/16384` の実内容なので違う)。
  その場合は id を report に出す。
