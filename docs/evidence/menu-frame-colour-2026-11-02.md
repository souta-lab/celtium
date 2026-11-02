# menu の画像 quad は黒く描かれていた — GlStateManager の色キャッシュが mod の raw glColor で desync する（2026-11-02）

renderer: r14d まで `51e91b5b228a38d0fcc73eb2f5494fc4d1279fa214561104d0d2659ef8663947`、
r14e（修正）`053f96db6e42fb06309daad872f6cd9655383e76a105a46dfce26367fb5a884f`（rc.26、`main` d8fcfa6e7 + 本差分）。
fixture `a84ea1d5…`（r14a/b/c）/ `990d4692…`（r14d/e、同一 source の再ビルド）。
すべて AMD Radeon RX 6400 (RADV NAVI24)・mode `plain`・X11・validation 既定ON・220 s 上限。
画像: [修正前](menu-frame-black-colour-2026-11-02.png) / [pack 未運搬の対照](menu-frame-no-pack-2026-11-02.png) /
[修正後](menu-frame-art-2026-11-02.png)。

## 観測: 大きな画像 quad だけが presented frame に出ない

r13 の記録は「banner・frame・button・icon が描画され、背景だけが黒い」だったが、**その根拠にした PNG は
menu stage の frame ではなかった**。r13f の run で `celtium-presented-menu-*-kept.png` と
`celtium-presented-loading-*-kept.png` は同一 sha256 `7cae79c0…` で、`docs/evidence/menu-legacy-default-2026-11-02.png`
（mean 6.47 / 非黒 0.0446）もその hash である。実際の menu frame を r14c で撮り直すと、出ているのは
**font のラベル・下部の行・右下の icon だけ**で、banner（10000x450）・背景（1920x1080）・frame（304x104、
透明なので元々ほぼ不可視）は無い（mean 6.66 / 非黒 0.0446）。

r13 の「menu 画像は legacy 既定ONで描かれる」という主張は、**quad が即時経路に届く**という範囲では正しく、
**presented frame に出る**という範囲では誤りだった。本 round はその区別を計器で確定させた。

## 計器: `CELTIUM_LEGACY_RUN`（`-Dceltium.debug.legacyRuns=N`、既定0）

`CELTIUM_LEGACY_REFUSED` は1 run 4行で打ち切られ、menu の分は loading が使い切っていた（r13 の罠と同じ）。
新しい census は **run の形（mode/vertices/format）で重複を落として1行**にし、次を並べる:
その run が sample した texture と mirror の実サイズ・完全性・供給元、`taken`（即時経路が受けたか）、
最初と最後の頂点（GL座標と UV）、mirror の projection/modelview（z の平行移動を含む）、拒否理由、**現在色**。

r13f/r14c で「quad は accepted、texture は full」まで分かっていたが、絵に出ない理由は分からなかった。
r14d で色を出した瞬間に確定した:

```
r14d（修正前、pack の resources を運搬）
CELTIUM_LEGACY_RUN ... texture=506 mirror=256x256 full=true taken=true colour=0,0,0,1  first=(0,0) last=(427,0) mvT=0,0,-2000   ← 黒の全面fill
CELTIUM_LEGACY_RUN ... texture=507 mirror=1920x1080 full=true taken=true colour=0,0,0,1 first=(0,0) last=(427,0) mvT=0,0,-2000    ← 背景（黒く描かれた）
CELTIUM_LEGACY_RUN ... texture=508 mirror=10000x450 full=true taken=true colour=0,0,0,1 first=(0,0) last=(1960,0) mvT=-766,-5,-2000 ← banner（黒く描かれた）
CELTIUM_LEGACY_RUN ... texture=509 mirror=304x104  full=true taken=true colour=0,0,0,1 mvT=61,93,-2000                            ← frame
CELTIUM_LEGACY_RUN ... texture=511 mirror=100x40   full=true taken=true colour=1,1,1,1 mvT=63,95,-2000                             ← button（白）
CELTIUM_LEGACY_RUN ... texture=512 mirror=20x40    full=true taken=true colour=1,1,1,1 mvT=402,215,-2000                           ← icon（白・絵に出る）
```

つまり **背景と banner は「描かれていない」のではなく「黒で描かれていた」**。白で描かれた button/icon だけが見える。

## 根因: 1.12.2 の `GlStateManager.color` のキャッシュ

`GlStateManager.color(r,g,b,a)` は自前の `ColorState` と比較し、**同じ値なら GL 呼び出しを省略する**。
CustomMainMenu の title screen は、panorama が無い構成で背景を

```
GL11.glBegin(GL_QUADS); GL11.glColor3f(0,0,0); glVertex3f(...) x4; glEnd()   // 生のGL、キャッシュは通らない
...
GlStateManager.color(1,1,1,1);   // キャッシュは既に白 → GL 呼び出しは省略
<背景画像の quad / banner の quad>
```

の順で描く。生の `glColor3f(0,0,0)` は GL の current colour（Celtium では stub の mirror）を黒にするが、
`GlStateManager` のキャッシュは白のままなので、直後の `color(1,1,1,1)` は**省略され**、mirror は黒のまま
残る。そのため以後の quad はすべて黒で描かれ、黒地に黒で消える。button/icon が白いのは、button 経路が
キャッシュに無い色（hover/disabled 等）を一度通すため、その後の `color(1,1,1,1)` が発行されるからである。

## 修正

`CeltiumGllessHint.glColor4f`（transformer が `GL11.glColor4f` 系を流す入口）で、mirror・font capture・
backend の色を更新した後に **`GlStateManager.color(red, green, blue, alpha)` を呼び、キャッシュを GL の実際の
current colour に合わせる**。キャッシュが既に同値なら本体は省略され、再帰もしない（再入時には既に同値）。
GL が無い環境（`scripts/cpu-contract` の probe）では `GlStateManager` が classpath に無いので try/catch で無視する。

意味: 「`GlStateManager.color(x)` は必ず GL の current colour を x にする」という、ゲーム側が意図した不変条件を
GL-less でも保つ。vanilla の経路は余分な GL 呼び出しをしない（同値なら省略される）。

CPU: `TextureLevelAnswerTest`（7）+ `LegacyImmediateCaptureTest` の `vertexAt`（2）を追加、221 tests / 0 failures。
`scripts/cpu-contract/transformer-route-verify.sh` は両 run とも called=241 failed=0・classes 87/87・
colour 32/32・texcoord 64/64。

## 併せた修正: `glGetTexLevelParameteri` が全 texture に 256 を答えていた

`glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH/HEIGHT)` は Forge の雲（256角）と lightmap
（256角）のために固定 256 を返していた。CustomMainMenu はこれで `MODE.FILL` の cover 矩形と UV を計算するため、
1920x1080 の背景が 256x256 として扱われ、quad は 427x427（本来 427x240）になっていた。
stub が upload 時に記録している level-0 extent（`texLevels`）を答え、記録が無い名前だけ 256 に落とす
（`TextureLevelAnswer`、CPU test で固定）。r14d の census では背景の quad が mirror=1920x1080 の実寸で解決される。

## 実測（すべて AMD RX 6400・problems=[]・validation 0・gpuRequirementMet=true）

| leg | jar | 条件 | menu frame | 所見 |
|---|---|---|---|---|
| r14a-menu-trace | `fa30a5a3…` | menu hold 無し | — | world PASSED（mean 104.82 / 非黒 0.9962）。tex-level 変更の回帰なし |
| r14b-menu-trace | `fa30a5a3…` | hold 60 tick・**pack 未運搬** | mean 29.65 / 非黒 0.1831 | 全面が missing texture の市松。`Failed to load texture: supersymmetry:mainmenu/banner.png`。census は `mirror=absent source=no-entry` を `taken=true` で記録（GL 同様 1x1 黒で描く） |
| r14c-menu-pack | `fa30a5a3…` | hold 60 tick・`CELTIUM_LEG_COPY_PACK_DIRS=resources` | mean 6.66 / 非黒 0.0446 | mirror は全部 full（banner 10000x450・背景 5120x2880・frame 304x104・button 100x40・icon 20x40）。出るのは font と icon だけ |
| r14d-menu-colour | `51e91b5b…` | 同上＋census に色 | mean 6.66 / 非黒 0.0446 | 背景/banner/frame の run が `colour=0,0,0,1`、button/icon が `1,1,1,1`。根因確定 |
| r14e-menu-colour-fix | `053f96db…` | 同上（修正後） | **mean 95.15 / 非黒 0.9824** | 背景・banner・frame・button・icon が全て出る（[修正後](menu-frame-art-2026-11-02.png)）。r14d の frame と 92.4% の画素が異なる |

## 修正後（r14e）の絵

[menu-frame-art-2026-11-02.png](menu-frame-art-2026-11-02.png)（AMD RX 6400、jar `053f96db…`、fixture `990d4692…`、
`CELTIUM_LEG_COPY_PACK_DIRS=resources` 5901 files、menu hold 60 tick、presented frame）:

- 背景（1920x1080 のスライド）が全面に出る。左300x300の平均 105.74 / 非黒 1.0000（修正前 0.00 / 0.0000、
  Celeritas 参照 21.58 / 0.9887 — 参照とスライドの位相が違うため明るさは比較しない）。
- banner（10000x450）が出る。banner 帯（60,40)-(1220,200) の平均 80.98 / 非黒 0.9142（修正前 0.00 / 0.0000、
  参照 47.31 / 0.6696）。
- frame（304x104 の半透明の黒枠）と button・icon も出る。修正前の frame と 92.4% の画素が異なる。
- world は同じ leg で PASSED・validation 0（picture mean 104.12 / 非黒 0.9994）。

## 残り（断定しない）

- slideshow の位相は参照と揃っていない（参照は暗い hall の画像、r14e は明るい市街の画像）。どちらも
  `supersymmetry:backgrounds/NN.png` の1枚で、位相合わせは未実装。`menuDumpPanoramaTimer` は panorama が null の
  構成では意味を持たない（CustomMainMenu の panorama は config に無く、既定でも作られない）。
- banner は TextureApng 相当の 10000x450 を UV 0..1 で引くため、参照の1コマ表示とはスケールが違う可能性がある。
  位相とコマ送りは未測定。
- Intel leg はこの修正では回していない（AMD のみ）。次の paired 確認で Intel を取る。

## Intel leg（2026-11-02、r16d-intel-menu-frame）— この修正の Intel 実測はこれが初めて

renderer `4130f4c8f6bfd637e80f8d5b4d51e645963a1f51ead885b3371846f26ac9479b`、fixture
`1494d31751249f27e9dc301344de813ff86bcae69e8f161595122e879df2bddf`、Intel(R) UHD Graphics 630 (CFL GT2)、
mode `plain`・X11・validation 既定ON・220 s・`CELTIUM_LEG_COPY_PACK_DIRS=resources`・
`-Dceltium.validation.menuHoldTicks=70 -Dceltium.validation.dumpPresentedMenuTick=40 -Dceltium.debug.legacyRuns=6`。
`accepted=true`、`verdict=PASSED`、`problems=[]`、`validationMessageCount=0`、`validationClean=true`、
`gpuRequirementMet=true`（実 device 行 `Intel(R) UHD Graphics 630 (CFL GT2)`）、`fixtureUncalibrated=false`、
device lost/VK_ERROR 0。

`CELTIUM_LEGACY_RUN`（同じ census。修正前の AMD r14d は背景/banner/frame が `colour=0,0,0,1` だった）:

```
r16d  CELTIUM_LEGACY_RUN texture=506 mirror=256x256   full=true taken=true colour=0.0,0.0,0.0,1.0   ← title screen 自身の黒fill
r16d  CELTIUM_LEGACY_RUN texture=507 mirror=1920x1080 full=true taken=true colour=1.0,1.0,1.0,1.0   ← 背景
r16d  CELTIUM_LEGACY_RUN texture=508 mirror=10000x450 full=true taken=true colour=1.0,1.0,1.0,1.0   ← banner
r16d  CELTIUM_LEGACY_RUN texture=509 mirror=304x104   full=true taken=true colour=1.0,1.0,1.0,1.0   ← frame
r16d  CELTIUM_LEGACY_RUN texture=511 mirror=100x40    full=true taken=true colour=1.0,1.0,1.0,1.0   ← button
```

つまり Intel でも `GlStateManager.color(1,1,1,1)` は発行され、背景・banner・frame・button は白で描かれる
（キャッシュ desync の修正は GPU 非依存）。もう 1 本の Intel leg（`r16c-intel-menu`、dump 無し・
`menuDumpTick` のつもりの誤った property 名）も同じ形で、背景が 2560x1369 のスライドだっただけ。

menu presented frame: `celtium-presented-menu-run-17f837310e22-…-kept.png`（presented frame 100、1280x720）、
mean **57.06** / 非黒 **0.93067**。同じ leg の world は PASSED。AMD の r14e は mean 95.15 / 非黒 0.9824 で、
**この差はスライドの画像が違うため**（r14e は 1920x1080 の市街、この leg も 1920x1080 だが別の絵。
`menuDumpPanoramaTimer=0.0`）。数値の一致は主張しない。主張は「Intel で背景・banner・frame・button が
presented frame に出ている」ことと、その形が AMD の修正後と同じ（黒 0,0,0,1 ではない）ことまで。

残り: menu stage の較正 floor は依然として無い（スライド位相を揃える手段が未実装なので、参照画像との一致は
判定できない）。tooltip・creative tab の frame は未取得。
