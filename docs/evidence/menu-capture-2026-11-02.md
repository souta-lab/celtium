# カスタムtitle screenの撮影と、menu画像が描かれないこと（2026-11-02）

renderer `69b74549ae276bc878c42773b58ea6ee57f92418e79b37ae8d5424fa50b611fa`（rc.26）。
fixture は final `54ff3809b94f9b1641e60ad02f7faa3694bb843f783f13ff9cb101ffaaa8b7f5`
（menu撮影の修正を含む。下表の leg は撮影時の hash を各段に記載）。

## 何を直したか（fixture）

`dumpPresentedMenuTick` は title screen の frame を撮るための property だが、Supersymmetry では
一度も撮れていなかった。要求条件が `mc.currentScreen instanceof GuiMainMenu` で、受け入れ instance の
title screen は CustomMainMenu の `lumien.custommainmenu.gui.GuiCustom` だからである。さらに、
要求が通っても同じ tick の launch 処理が screen を dismiss するため、次の present は menu ではなく
world の最初の frame になり、同じ `menu` stage に書いた GL screenshot の evidence が presented dump の
evidence を上書きして hold を早期に解放していた（r12-menu: `menuHeldTicks=60`、`menuDumpClientTick` なし、
41 dismissals、menu PNG なし）。

修正は `Celtium validation fixture` の `ValidationClient`:

- 要求は画面クラスを限定せず、任意の非null screen で行う。`menuDumpScreen` にクラス名を残す。
- 要求後は launch を hold し、**presented dump の evidence が完了するまで**待つ。GL arm のように
  presented dump を要求できない場合は即座に解放し、その理由を `menuCaptureNote` に残す。
- GL screenshot の stage を `menu-gl` に分離（同一stageの上書きを構造的に消す）。
- hold は `menuCaptureTicks` で打ち切り、未完了なら pending request を破棄して
  `menuCaptureIncomplete` に理由（requested / clientTicks / presentedFrames / screen）を残す。
  world の frame を menu の絵として完了させない。

## 実測

| leg | fixture | GPU / scene / guiScale | 判定 |
|---|---|---|---|
| r12-menu | `0f62e587` | RX 6400 / terrain / 2（seedなし） | INCONCLUSIVE（guiScaleが較正keyと不一致）。menuは presented frame **3**、ただし GL screenshot の evidence で解放。白背景＋Mojang logo＋font |
| r12b-menu | `beeb34eb` | RX 6400 / terrain / 3（guiScale3 seed） | **PASSED**・validation 0。menuは presented frame **322**、`menuCaptureHeldTicks=49`。黒背景＋fontのみ |
| glref-r12-menu | `54ff3809` | RX 6400 / Celeritas 2.5.0-dev `567dcf8a` / terrain / 3 | **PASSED**（reference arm）。同じ `GuiCustom` を GL で撮影 |

画像: [Celtium](menu-capture-celtium-2026-11-02.png) / [Celeritas](menu-capture-celeritas-2026-11-02.png)。
同じ instance の copy、同じ fixture、同じ `menuHoldTicks`・`dumpPresentedMenuTick`。
Celeritas は SUPERSYMMETRY banner・frame・button texture・background slideshow・Discord icon を描く。
Celtium はそれらを描かず、**font のラベルと下部の文字だけ**を描く。

r12b の hold 中の計器（`-Dceltium.debug.logLinesPerTag=200`）:

- `CELTIUM_UNIT_BIND` は font atlas `510`（4096x4224）と `513`（256x256）だけで、menu の画像
  （banner 10000x450・button 100x40・backgrounds 1920x1080）の bind は無い。
- `CELTIUM_ANNOUNCE_EXTENT` も同じ2枚だけで、menu 画像の upload が無い。
- `CELTIUM_MENU_GROUP` の24行はすべて `frame=0`（起動時のpanorama）で、menu 中の行は無い。

つまり Celtium では menu の画像が **bind も upload もされていない**。GL 参照では同じ画像が描かれ、
`celtium-validation-menu.png` の平均輝度は 32.50、非黒率 0.77123（Celtium の presented menu は
平均 6 前後・ラベル以外は黒）。`supersymmetry:mainmenu/splashes.txt` は **両armとも**
`FastFileNotFoundException` で、この instance に実在しない。splash text の不在は renderer の所見にしない。

## 断定しないこと

- 根因は未確定。mod 側（CustomMainMenu の `ITexture`/`GuiConfig` が画像を解決できず描画を飛ばす）と
  renderer 側（遅延 load の bind/upload が mirror に届かない）を分離する計器はまだ無い。
  「images が空だから白い」と「texture が白い」は同じ絵になる。
- renderer のコードはこの leg で変えていない。menu stage は verdict の対象外で、画像gateは付けていない。
- F2 screenshot（`menu-gl`）は r12b で黒（非黒率 0.04067）だったが、これは main FBO と presented image の
  別問題であり、menu 画像欠落の根因とは分けて扱う。

## 次に走らせる判別

同じ leg で、menu の hold 中に `Minecraft.getTextureManager().getTexture(new ResourceLocation(
"supersymmetry:mainmenu/banner.png"))` 等を reflection で読み、null か、`SimpleTexture` か、
`getGlTextureId()` が正の値か、`TextureUtil` の upload が起きたかを report に出す。null なら
CustomMainMenu が bind に到達していない（mod/config側）、非nullで id>0 なら bind は起きたのに
mirror に pixels が無い（renderer側）へ分岐する。GL arm の同じ probe が対照になる。
