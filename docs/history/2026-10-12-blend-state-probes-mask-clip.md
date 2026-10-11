# 2026-10-12 — the controlled probes: BH-10's write mask and BH-09/OPT-10's clip rectangle

担当: blend-state。BH-10 の絵の証明、clip 変換の mirror バグの発見、scissor/light の boundary 追加。
本書は**測定の記録**で、着地したコードは Lead の commit（`9c7626783` ほか）と `61d06ef39`。

## 1. なぜ scene ではなく probe なのか（測った理由）

`clouds=2` + `cameraPitch=-30` で Fancy 雲を画面に入れた脚（jar `d5bd7579`、同一旗の2本 bs10/bs11）:

| 帯 | bs10 vs bs11（同一 jar・同一旗） |
|---|---|
| 全画面 | >8 11.13 % |
| rows 0-200 | 21.55 % |
| **rows 60-190（雲）** | **18.44 %** |

雲層は run 間で動く（capture は同じ worldTick=200 でも frame 数が違い partial tick が違う）。
flag の効果（bs9 control vs bs10 fixed、雲帯 18.36 %）は**この床より小さい**ので、雲 scene では
BH-10 の絵の主張はできない。mask 自体は届いている: `colorMaskHistogram=[0x0:397 0x7:357 0xf:6645]`
（0x0 = depth-only pass が native で走っている。以前は最後の1値しか読めなかった）。

## 2. 制御 probe（`-Dceltium.validation.stateProbes=true`、drawTest handler 内）

ゲーム自身の `Gui.drawRect` と GL 入口で、既知の矩形を2組描く:

- **mask probe**: 同じ帯に magenta の quad 2つ。左は `GlStateManager.colorMask(false,false,false,false)`
  （Fancy 雲の depth-only pass と同じ形）、右は全チャンネル。左が「背景のまま」なら mask が届いている。
- **clip probe**: 中央に緑の quad（左半分の `glScissor`）、その下の帯に黄の quad（右半分の `glScissor`）。

結果（**同一 jar `2d0f1f52`、旗1つ違い**）:

| probe | bs15 control（旧形） | bs16 fixed | bs17 fixed 再走 |
|---|---|---|---|
| mask 左（depth-only） | **(255,0,255) 塗られている** | **(97,60,55) 背景** | (96,59,54.5) |
| mask 右（対照） | (255,0,255) | (255,0,255) | (255,0,255) |

同一旗の再走で 1/255 しか違わない矩形が、旗1つで「背景 ↔ magenta」に変わる。これが BH-10 の画素証明。

## 3. clip 変換は mirror していた（見つけたバグ）

probe の GL box は緑 `(0,276,639,84)`・黄 `(639,189,642,87)`。backend は
`CELTIUM_GROUP_SCISSOR glBox=0,276 639x84 applied=0,360 639x84 fmt=7 batches=1` と、
`targetHeight - (y+h)` の変換結果を出していた。提示 frame の quad は rows 360..443 なので
**image の rows は 276..359**（`VulkanSwapchain` が blit で「shared image の row 0 は絵の下端
（GL texture order）」と書いている通り）。つまり clip は mirror した帯に当たり、quad は丸ごと消えていた。

修正（Lead 着地 `9c7626783`）は `clipY0 = clip[1]`（GL も image も下端から測るので flip 不要）。
**lab flag は残さない**（既知の誤りへ戻すスイッチは次の読み手の罠）。旧形は
`-Dceltium.vulkan.groupScissor=false` で到達できる（これは変換を反転するのではなく group scissor を切る）。

## 4. clip の対照（**同一 jar `4f6f5032`、旗1つ違い**）

| probe | bs29 control（`groupScissor=false`） | bs30 fixed（既定） |
|---|---|---|
| 緑の帯 y=400 | 緑 **x 318..959**（全体、clip なし） | 緑 **x 318..638**（左半分だけ） |
| 黄の帯 y=490 | 黄 **x 318..959**（全体） | 黄 **x 639..959**（右半分だけ） |
| `scissorBoundarySplits` | 0 | **473** |
| `scissorCapturedGroups` | 0 | **368** |

旧形が「clip を一切かけない」のは、flush 時に live の stub 記録を読むため（probe は最後に
`glDisable(GL_SCISSOR_TEST)` するので、その時点で clip は無い）。mirror 変換の脚では
「mirror した帯で丸ごと消える」という別の失敗になっていた。どちらも同じ欠陥で、修正は両方を閉じる。

## 5. 追加した boundary（同じ「state は group に届く」規則）

- **scissor boundary**: `celtium$sameScissor(liveApplies)` — 「test 有効 かつ glScissor 済み」を
  1つの clip として比較し、動いたら group を閉じる（`scissorBoundarySplits`）。capture と flush は Lead 着地済みで、
  欠けていたのは境界だけ。値の読み出しは `scissorX/Y/Width/Height`（batch ごとに走るので配列を作らない）。
- **light params boundary**: `VulkanGlState.lightGeneration()` を追加し、4つの setter で
  **値が実際に動いた時だけ** bump。group open で捕捉した generation と比較し、動いたら閉じる
  （`lightParamBoundarySplits`）。enable bit は既に state key にあったが、方向・色だけの変更は
  境界が無く、後続 batch が前の payload で描かれていた（BH-05 の残り半分）。
  実測: probe 脚で 648-663、videoSettings 脚で 226 — 実 scene で頻繁に起きている。

## 6. 到達しなかったもの（名指し）

- **videoSettings 脚は scissor 呼び元に到達しない**: `scissorCapturedGroups=0`。
  fixture の `GuiOptions.actionPerformed` へのクリックは `InvocationTargetException` で失敗し、
  vanilla `GuiVideoSettings` を直接構築する経路へ fallback している（`guiScreenOpenedVia` がそう記録）。
  vanilla の list は `glScissor` を呼ばない（`GL_SCISSOR_TEST` を使うのは Forge の `GuiScrollingList` と
  Celeritas 自身の `VintageDrawContext` だけ）。したがって clip の検証は probe で行った。
- probe 脚は `FAILED_PRESENTED_IMAGE`（`validationClean=true`・`validationMessageCount=0`）。
  **gate は正しい**: probe が terrain scene の較正参照に無い矩形を描くのだから、その scene の絵ではない。


## 7. 追補（同じ日の第2ラウンド）

### 7.1 雲 scene が BH-10 を示せない理由（名指し）

- **depth-only pass は frame に居る**: `CELTIUM_GROUP_BOUNDS` の group 行が
  `fmt=2 worldPass=true batches=1 vertices=5104 state=[blend=1 src=6 dst=7 srcA=1 dstA=0 **mask=0x0** …]`
  ＝ Fancy 雲の depth-only pass（毎フレーム1 group、format 2、world pass）。`colorMaskHistogram` の
  0x0 スロットはこの group である（`drawnByFormat[8]=0` なので format 8 ではない）。
- **scene の床が flag の効果より大きい**: 同一 jar・同一旗の2走（bs32 vs bs33、jar `edccbb6d`）で
  rows 60-140 は raw 73.5 %、**一様項（+7.6,+10.0,+14.3）を引いても 40.5 %**。
  同じ jar の control vs fixed（bs31 vs bs32）は raw 20.5 %、**一様項を引くと 2.2 %**。
  つまり flag の構造的な差は scene のばらつきの **1/18**。
- **ばらつきの正体**: sky の明るさは **capture が tick 200 のどのフレームで起きたか**に追従する。
  6 脚（雲あり・pitch -30）で `captureWorldFrames` と sky 平均の相関は **r = 0.945**
  （frames 87 → 133.4、frames 172 → 144.6）。fixture は tick を固定するがフレーム位相は固定しないので、
  partial tick が走ごとに違い、sky gradient / sun brightness が帯全体を ~+10/255 動かす。
  雲は動いていない（最良整数アラインメントは (0,0)、dy=0）。
- 参考: `clouds.png` は alpha==255 が 20.4 %、0<alpha<255 が 79.6 % なので、depth-only pass の色書きが
  原理的に見えないわけではない。**見えないのは scene のノイズが 18 倍大きいから**である。

### 7.2 light-params boundary は lit のみに絞った（測定つき）

`celtium$lightParams` は lighting enable が off の group に `null` を返し、payload は push block に
書かれない。したがって「light 値が動いた」だけで group を閉じるのは、unlit group では**意味のない分割**だった。
実測（lit 条件を入れる前 / 後、同一 scene・同一 fixture）:

| 脚 | `lightParamBoundarySplits` |
|---|---|
| terrain (bs34 → bs37) | 416 → **17** |
| videoSettings (bs35 → bs38) | 425 → **2** |

両脚とも `validationClean=true`・`validationMessageCount=0`。条件は最適化ではなく定義そのもの
（payload を持たない group は light 値の影響を受けない）。残る 2-17 が boundary が守っている実例で、
crop で示すには「lit の即時描画を fixture が持たない」ため合成 probe が要る（現状の probe quad は unlit）。
