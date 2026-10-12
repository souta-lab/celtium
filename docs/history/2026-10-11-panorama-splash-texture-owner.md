# パノラマの白の所有者: スプラッシュ画面の白い DynamicTexture (id 8)

Owner: **fog-path**. Date: 2026-10-12, ~10:50 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `mirror-menu` - fixture-free GL-less menu leg, `menuHoldTicks=60`, `enqAll`, `enqFormat=2`,
`drawSiteLines=8`, jar `fog-path-mirror.jar`.

## 決定打: 誰が白を書いたか

新しい `CELTIUM_MIRROR_DRAW` (描画時の descriptor と mirror) と、既存の `CELTIUM_UPLOAD_CALLER` が所有者を名指しした:

```
CELTIUM_MIRROR_DRAW frame=0 groupTexture=8 preparedKey=0xf7635636b83b2 resolvedKey=0xf7635636b83b2
    resolvedSize=256x256 descriptor=0x7faf24415668 mirrorSource=announced-int[]@256x256
    target=1768484405 batches=2
CELTIUM_UPLOAD_CALLER id=8 256x256 first=0xffffffff callers=[
    TextureUtil.func_110988_a <- DynamicTexture.func_110564_a:35 <- DynamicTexture.<init>:18
    <- Minecraft.func_180510_a:889 <- SplashProgress.drawVanillaScreen:1051]
```

- パノラマの面のグループは `texture=8` を記録し、descriptor の key は**古くない** (`preparedKey == resolvedKey`)、
  サイズも 256x256 で一致。つまり descriptor は「記録された id の mirror」を正しく束縛している。
- その id 8 の mirror を埋めたのは **`SplashProgress.drawVanillaScreen`** が作った **白一色の 256x256
  DynamicTexture** (`first=0xffffffff`)。パノラマの面はスプラッシュ画面の残り物をサンプルしている。
- 比較: id 46/47 は `mirrorSource=announcement@256x256` (別経路) で、パノラマ本来のシートは
  Lead の binds どおり 42/44/45-48。

## つまり

白は「描画が届いていない」のでも「readback が誤読」でもなく、**サンプルしている texture がスプラッシュの
白いキャンバス**だから。7 つの仮説 (layout 3 種、hand-back、GENERAL descriptor、未描画、未 flush) は全て
計測で否定済みで、残っていたのはこの 1 点。

## 直し方 (2 案、どちらも registry/stub 側で描画経路は触らない)

1. **スプラッシュ終了時に id 8 の mirror を失効させる**: `SplashProgress.drawVanillaScreen` が終わった時点で
   その DynamicTexture は生きていないので、mirror を残さない。以後 id 8 をサンプルする面は
   `no-entry` になり、白ではなく「未供給」として数えられる。
2. **パノラマの面が本来の id (42/44/45-48) を記録するようにする**: 記録がずれているなら stub 側の
   バインド捕捉を直す。どちらが正しいかは「面の描画時にゲームがどの id を bind していたか」の 1 計測で決まる。

## この unit で landed

- `CELTIUM_MIRROR_DRAW` (bounded, `-Dceltium.vulkan.mirrorDrawLog`, default 8): 描画時の groupTexture /
  preparedKey / resolvedKey / resolvedSize / descriptor / mirrorSource。
- `VulkanTextureRegistry.celtium$mirrorSourceFor(int)` (read-only): どの経路がどのサイズで mirror を埋めたか。
- `splashSourcedGroups`: int[] 経路 (スプラッシュのキャンバス) で埋まった mirror をサンプルする format-2
  グループ数。修正の効果がこの 1 数で読める。
- 描画経路・shader は変更していない。build 緑、`shader_push_constant_check` 33 shaders / 0 problems。
