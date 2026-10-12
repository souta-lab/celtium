# 2026-10-12 — the loading screen's steps 2-4: no caller, and no dump contract (stop point)

担当: blend-state（引き継ぎ）。unit は sky-draws の step 1（`654dc9253`）の上に乗る 3 段のはずだった。
**結論: step 2/3 は GL-less の受入構成に呼び元が存在せず、step 4 は未命名の contract を要する。
コードは書いていない（最後の green commit は step 1 のまま）。**

## 1. step 2（record）に呼び元が無い — 測った

`-Dceltium.vulkan.glless=true` では Forge のスプラッシュは**既存の mixin が明示的に止めている**:

- `forge122/.../mixin/core/SplashProgressMixin.java:19-22` が `SplashProgress.start()` を cancel する
  （理由はクラス javadoc: splash は**2つ目の共有 GL context** を要求し、GL-less にはそれが無く
  `DrawableGL.createSharedContext` が投げる）。
- 脚 `bs39-begin`（jar `ab798f274ec013f0`・HEAD `1a308c25`+dirty）のログにその行がある:
  `CELTIUM_GLLESS splash progress skipped (no shared GL context)`。
- 同じ脚で **`CELTIUM_IMMEDIATE_BEGIN` は 0 行**、census に `glBegin`/`glEnd`/`glVertex3f`/`glTexCoord2f` は**出ない**
  （census の top は `glRotatef=21500 … glColor4f=5201 …`）。

つまり plan §0.341 が挙げる `SplashProgress` の `glBegin(GL_QUADS)`（:294/:353/:416）は
**受入構成では一度も実行されない**。step 2 の accumulator を書いても記録する相手がいない。

## 2. plan が splash に帰属させた census 数は font のもの

原典を読むと、`glVertex3f` は splash ではなく **`FontRenderer`** の入口である:

- `FontRenderer.java:238/:292` が `GlStateManager.glBegin(5)`（**GL_TRIANGLE_STRIP**）を開き、
  `FontRenderer.java:240-246/:294-300` が `GlStateManager.glVertex3f(...)` を呼ぶ。
- splash 側は `glVertex2f`（`SplashProgress.java:296/:355/:417`）で、**stub の表にすら無い**
  （表にあるのは `glVertex3f` だけ）＝汎用 no-op で、census にも数えられていない。
- splash の色は `glColor3ub`（`setColor`、:409-412）。`glColor4f` は :337 の1箇所だけ。

したがって plan の「glVertex3f=152460/glBegin=38115 は splash の quad が捨てられた証拠」は
**font の glyph strip の数**である（4 頂点/run で 353316/88329 ≈ 4.0 ✓）。しかも font は
glyph batching の着地後、現行 build では `glBegin` 自体を呼ばない（上記 0 件）＝
step 1 の計器は「呼び元が居ない」ことを正しく示している。

**step 2 の check として指定された「census の glVertex3f=152460 と一致すること」は、
splash ではなく font の頂点数との一致を要求することになる。** 一致しても loading screen の
証明にはならないので、この check は採用していない（paper over しない、という指示に従った）。

## 3. step 4（accept）は contract が未命名

GL-less で実際に走る loading 画面は Forge の fancy splash ではなく
**`Minecraft.drawSplashScreen`（Tessellator 経由）**:

- `Minecraft.java:517` が `SplashProgress.drawVanillaScreen(this.renderEngine)` を呼び、
  `SplashProgress.java:918-922` が `Minecraft.getMinecraft().drawSplashScreen(renderEngine)` へ委譲する。
- 脚のログにその経路が出ている:
  `callers=[TextureUtil.func_110988_a <- DynamicTexture.<init> <- Minecraft.func_180510_a:889 <- SplashProgress.drawVanillaScreen:1051]`
  ＝ **DynamicTexture（logo）** と **Tessellator**（`Tessellator.func_78381_a:22 <- Minecraft.func_180510_a:908`）
  が動いている＝**幾何は immediate/uploader 経路に届いている**（glBegin ではない）。

しかし **presented dump を loading 中に取る contract が無い**: `Minecraft.java:517` は
`startGame` の中、**最初の client tick より前**に走る。既存の dump スイッチはすべてそれより後を指す:

| スイッチ | 起点 | loading 中に発火するか |
|---|---|---|
| `dumpPresentedTick`（既定 20） | **world** tick | しない（world が無い） |
| `dumpPresentedMenuTick` | client tick **かつ `GuiMainMenu` が開いていること** | しない（title screen は loading の後） |
| `screenshotAtTick` | world tick | しない |

必要な contract（未命名・要判断）: **splash 段階で presented dump を要求する hook**。
例えば `Minecraft.drawSplashScreen`（`func_180510_a`）の HEAD/RETURN に fixture の mixin を置き、
record の stage 名を `loading` にする。renderer 側の「最初の presented frame を保存」でもよいが、
どちらも新しい契約であり、勝手に足すと record の意味が増える。

## 4. 現在の状態

- コード変更なし。最後の green commit は step 1（`654dc9253`）。
- 受入未達。次の owner に必要なのは (a) splash skip を GL-less で維持したまま loading 画面を
  測る contract（上記）、または (b) 「loading 画面は drawVanillaScreen の Tessellator 経路で
  届いている」という前提で、届いた幾何が presented frame に出ない理由を測る別 unit。
