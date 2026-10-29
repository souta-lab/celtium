# SuperSymmetry 0.2.0.2 + Cleanroom Relauncher: 2026-10-29 の実測

対象: 利用者の Prism instance `Supersymmetry`（Forge 14.23.5.2860 + `!cleanroom-relauncher-1.3.0.jar` →
Cleanroom 0.7.14-alpha、Fugue 0.25.5、Hesperus 無し）。Celtium `a7f4798e…` / `323b75b1…`、AMD RX 6400
(RADV NAVI24)、1280x720 headless X11、Java 8（親）/ Zulu 25（ゲーム）、ヒープ 5120m。

## できるようになったこと

- **隔離コピーで起動・メニュー・ワールド入場まで到達**（qmagnet セーブを `--seed-save` で導入）。
- ワールド描画は `presented-dump` で確認できる（[画像](../../.gv-verify/run-susy-celtium-long2/screenshots/celtium-presented-world-run-aac69fcf95ef-330bedb4-kept.png)）。
- **Celtium 由来の device lost / validation error / 例外 は 0**（`validationMessageCount=0`、`validationClean=true`）。

## 直した不具合

- `CELTIUM_COMPOSITE_REFUSED … lazyFailure=… NullPointerException: Cannot read field "owned" because "level" is null`
  → `VulkanGlTexture` のアップロード finally が `level == null` を踏んでいた（commit `c7af46449`）。
  修正後の再実行で `offTargetRefusals=0`・NPE 0件を確認。

## 残っている拒否（KPI「Celtium の拒否ゼロ」に未達）

| マーカー | 実測 | 解釈 |
|---|---|---|
| `CELTIUM_UNMAPPED_REFUSED` | 3件。layout `pos3f uv2f color4ub uv2s normal3b pad1b`（size 32） | バニラでも table に無い解釈。format id 追加が要る |
| `CELTIUM_PATCH_REJECTED` | 4件。1920x1080 の render target（GL texture 226 / 50410 等）が「no announced pixels」 | **メニュー背景の本命**: pack は `custombackgrounds` + `custommainmenu` で画面サイズの FBO に描き、それをサンプルする。Celtium はその render target を採用できず content-less mirror になる → **背景が黒** |
| `CELTIUM_CAPTURE_MISS` | 3件。上と同じ texture の level に data が無い | 同じ根因 |

## 利用者報告（ログ https://mclo.gs/NtzY4gj）

- device lost / validation error はログにも **0**。
- present は安い（`presentPerCallMicros≈250`、`swapPointMillis` 合計1.5s/6381回）。GPU待ちもほぼ無し
  （`frameFenceWaitMillis=6`）。**6FPS は描画提出ではなくフレーム内の他所**（ログに frameBudget が無いので未分解）。
- `CELTIUM_VERIFY` の累積で `windowRecords` が 122（120 presents の窓）から最終 2,041,390 まで増加。
  後半のワールドで **1フレームあたり数百 window** が記録されている疑い（メニューではなくワールド/再入場後）。
- スクリーンショットではメニュー文字が二重にずれて重なり、背景が黒 → 上記 `custombackgrounds` の FBO 採用失敗と整合。
- 「ホバーがおかしい」はボタンのハイライト画像が同じ FBO/ミラー経路に乗っている可能性。

## 次の判別（次round）

1. **メニュー専用の計測**: fixture の `scene=menu` は `terrain` に落ちる（実測 `scene=terrain`）。
   メニュー保持（`menuHoldTicks`）中の `CELTIUM_FRAME_BUDGET` と presented-dump を取れる経路を用意する。
2. **render target の採用**: FBO の color attachment が「no announced pixels」でも、その FBO が描かれた時点で
   ownership を採用し mirror を作る（`custombackgrounds` の 1920x1080 で検証）。
3. format 32B（normal+pad 付き）の即時描画対応。
4. 利用者側で `-Dceltium.vulkan.frameBudget=true` を付けたメニュー/ワールドのログを取れば、6FPS の内訳が1行で出る。

## 追加修正: 32バイト粒子レイアウト（2026-10-30, commit `0f7474c88`）

利用者ログの `CELTIUM_UNMAPPED_REFUSED` 3件は、format table に無い
`3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte 2,UV,Short 3,Normal,Byte 1,Padding,Byte`（32B）。
先頭28Bは `PARTICLE_POSITION_TEX_COLOR_LMAP` と同一で、normal/padding はどのシェーダも読まない。
→ 32B を `PARTICLE` にマップし、uploader で 32→28 に詰めて送出するよう修正（`CELTIUM_COMPACTED_BATCH` で実行を確認可能）。
layout マッピングと stride 規則は unit test 済み。**GPU での絵の確認は未実施**（この layout はこちらの qmagnet シーンでは
発生しないため、利用者ログまたは該当modのシーンで `CELTIUM_COMPACTED_BATCH` が出ることを確認する）。

## 追加: IR の stride-48 split client arrays（2026-10-30）

Immersive Railroading の `DRAWARRAY_REFUSAL_CONTEXT`（8件/run）は `stride=48 otherArrays=NORMAL,COLOR,TEX`。
- 原因1: `glTexCoordPointer`/`glColorPointer`/`glNormalPointer` は transformer が**ルートしていなかった**ため、
  stub の split pointer は常に 0 で「layout不明」として拒否されていた。
  → `CeltiumGllessNativeTransformer` に ngl*/gl* のルートを追加、`CeltiumGllessHint` に橋、stub に記録を追加。
- 原因2: layout を `POSITION_TEX_COLOR`(24B) にマップし、uploader ではなく stub の drawArrays で
  pos/tex/color を各配列ポインタから読み直して repack（`CELTIUM_CLIENT_ARRAY_REPACK`）。
- 実測: 拒否理由が **「stride 48 … layout unknown」→「the mirrored projection is the identity at draw time」** に変化。
  つまり配列追跡と repack は作動し、**残ブロッカーは IR の行列がミラーに届かない別問題**。
  repack 自体は各配列のポインタから読むため layout 非依存で、色は float/ubyte を判定して変換する。
- この8件はメニュー/ローディング中の IR 描画で、projection を直すまで描かれない（in-world の同レイアウトは
  正しい projection なら repack 描画される）。GPU 画像での最終確認は projection 修正後に行う。

## 追加: projection mirror の欠落（2026-10-30）

IR の stride-48 拒否は layout から **「the mirrored projection is the identity at draw time」** に移った。
`-Dceltium.glless.census=true` の matrix native census で原因の呼び出しを特定:

```
nglMultMatrixf callers=[org.lwjgl.opengl.GL11.glMultMatrixf <- GL11.glMultMatrix
  <- cam72cam.mod.render.opengl.RenderContext.multMatrix <- RenderContext.apply
  <- VBO$Binding.<init> <- OBJRender$Binding.<init> <- OBJModel$Binder.bind]
```

transformer は glMultMatrix を意図的にルートしていなかった（vanilla の camera は Project.gluPerspective 経由で
GluProjectionMixin が既にミラーしており、二重適用で world が真っ白になった実測がある）。
→ **mod 起点のみ適用**するルートを追加（呼び出し元の最初の非 JVM/LWJGL/Celtium フレームが net.minecraft* なら skip）。
commit `d1b30cc06`。

**結果**: 8件の拒否は不変。multMatrix は適用先スタックがガードの読む PROJECTION でないか、IR の projection 設定が
別の未ルート呼び出し（glLoadMatrix/glFrustum 等）である可能性。次の計器は「適用時の matrix mode と、
拒否時の projection matrix のダンプ」で、ここから先は推測ではなくその2点で判別する。

## mirror 拒否の削減（2026-10-30, build `574dc59d`）

FBO の color attachment に対しては content-less な mirror を作らないようにした（`get()` で
`celtium$framebufferForColorTexture(id) != 0` なら null を返し、公開/採用の経路に任せる）。

| 指標 | 変更前 | 変更後 |
|---|---|---|
| `CELTIUM_PATCH_REJECTED` | 7 | **4** |
| `offTargetRefusals`（summary） | 0 | **0** |
| capture | sections 17 | sections 17 |
| warm P50 / P95 | 2.50 / 4.61 ms | **2.37 / 4.54 ms**（無退行） |

残り: 1280x720 の texture 478/110194/7 はまだ raw FBO map に載る前の時点で mirror を作ろうとしている
（vanilla Framebuffer 側の関連は backend の publishedTextures が持ち、registry からは見えない）。
texture 502 は extent 自体が未記録（`COMPOSITE_REFUSED` も同 texture）。CAPTURE_MISS 3件は 110194/7/89。
