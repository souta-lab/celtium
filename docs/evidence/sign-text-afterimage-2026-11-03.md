# the sign's text: the world-pass counterpart of the tooltip afterimage, measured (2026-11-03)

利用者報告⑤（ツールチップの残像・にじみ）は GUI の tooltip では2つの arm とも欠落・残像0と測られた
（[tooltip motion](tooltip-motion-2026-11-02.md)・[mod FBO](tooltip-mod-fbo-2026-11-03.md)）。plan.md の現在表には
**「看板は未測定」**が残っていた。看板の文字は world pass の中で TESR から同じ font capture を通って描かれるので、
「保持された glyph run が状態を越えて残る」という同じ機構の、named screen では作れない側の事例である。
この round はその scene と checker を作り、測った。

## scene

`-Dceltium.validation.signTextAbTick=T` で、カメラの前方 2.5 ブロックの空気中に standing sign を置き、
tile entity に4行（`CELTIUM` / `SIGN TEXT` / `AFTERIMAGE` / `PROBE`）を書く。3フレームを **on / off / off** で描く:

| stage | 文字 | 何を言うか |
|---|---|---|
| `signtext-a` | on | 文字が描かれている |
| `signtext-b` | off | 文字を消した**最初のフレーム**。残像ならここに文字が残る |
| `signtext-c` | off | 2フレーム目。b と一致しなければならない |

判定は「a と c が違う画素＝文字の画素」を mask とし、その中で b と c が違う画素を残像とする。b と c は
どちらも off なので、正しい renderer では mask の中で完全に一致する。文字が**無い**場合は mask が0になり
「何も測っていない」として fail する（合格ではない）。対照は a と c の比較で、mask 全体が差として出る。

## 計測を成立させるまでに潰した4つの罠（すべて実測）

1. **client tick はフレームではない。** 最初は tick T/T+1/T+2 で切り替えたが、world load 中は1フレームに
   何 tick も走るので、on と off が同じフレームの tick batch に入り、**on のフレームが存在しなかった**。
   → 3フレームを `TickEvent.RenderTickEvent` の START（フレーム描画の直前）で1フレームずつ進める。
2. **sign の chunk 再構築は非同期。** 置いた直後のフレームは古い mesh を描くので、r28c の「on」フレームには
   sign 自体が無く、r28d では3フレーム目に現れた（8 tick で不足）。→ 配置を T-40 tick にし、フレームは T で arm。
3. **HUD と particle とアニメテクスチャ。** 最初の whole-frame 比較は HUD の文字と world の particle が差の大半で
   （r28a: 3392 px）、flat stack の water/lava/fire 層は毎フレームアニメーションする（r28e: mask 98,295 px）。
   → window 中は HUD を隠し particle を消し、**カメラを空に向ける**（clouds は seeded options で off、sun は
   pinned）。空は静止しているので、3フレームの差は文字だけになる。
4. **present が疎だと dump は狙ったフレームに落ちない。** dump は「次の present」で書かれるので、present が
   0.5 FPS の区間では要求したフレームより何十フレームも後の絵が書かれる（r28f: a/b が空、c が HUD 付きの
   別フレーム）。→ この scene は**軽い flat stack**（`worldLayers=3;bedrock,2*dirt,grass;1;`）で回し、present を
   密にする。代償として scene の calibration key が変わる（下記）。

## 実測（leg `r28h-signtext`）

| | |
|---|---|
| Renderer / fixture | `e34480edeebc3351ce789fa50d188aec545d469315a49f83165ff7657714cdb2` / `bb349ec03dab34d842ff820ab4340b48e85bc85eb280642cdd2e7eca66bf7326` |
| GPU | AMD Radeon RX 6400 (RADV NAVI24), `gpuRequirementMet=true` |
| Scene | `plain`/`terrain`, Supersymmetry, 1280x720, `guiScale3.txt`, `worldLayers=3;bedrock,2*dirt,grass;1;`, validation ON |
| Flags | `-Dceltium.validation.signTextAbTick=150` |
| Sign | `pos=BlockPos{x=0, y=14, z=-2} placed=true lines=4 block=minecraft:standing_sign`, eye `0.500,13.620,0.500`, look `0.000,0.707,-0.707`（空を向く） |
| Frames | a=tick 150 on, b=tick 151 off, c=tick 153 off（render step 0/1/2） |
| Validation | `vulkan-check passes=15658 skipped=0 errors=0`、VUID 0行、`CELTIUM_REFUSALS_BY_FORMAT {}` |
| Verdict | `UNCALIBRATED_PRESENTED_IMAGE`（exit 2）**by construction**: `worldLayers` は calibration key の一部なので、この絵は terrain の floor が記述する絵ではない |

checker `scripts/cpu-contract/sign-text-afterimage-check.py`:

```
ink (a vs c) = 1835 px, box=(576, 433, 701, 501)
ghost (b vs c inside the ink) = 0 px, box=None
noise (b vs c outside the ink) = 0 px (0.0000%)
control (a vs c inside the ink) = 1835 px of 1835
ghost fraction = 0.000% of the text's pixels
PASS: the two off frames agree on the text's pixels; no afterimage
```

文字の mask は 126x69 px の矩形に収まり、その中で b と c は**1画素も違わない**。mask の外も0で、この scene は
3フレームの間じゅう静止している（＝差が出たらそれは renderer のもの）。対照（a vs c）は mask 全体を差として
返すので、この判定は fail しうる。

画像: [sign-text-afterimage-2026-11-03.png](sign-text-afterimage-2026-11-03.png)（左から a=on / b=off / c=off、
文字の矩形を3倍）。

## 言えること・言わないこと

- **言える**: この scene・この JAR・この GPU で、world pass の TESR が描いた文字を消した直後のフレームに、
  その文字の画素は**残らない**。保持された glyph run が状態を越えて再提示される形（tooltip で測ったのと同じ形）は、
  看板の文字では起きていない。
- **言わない**: 看板の文字が編集される・看板が壊される・chunk が再構築される、といった**状態遷移**の残像。
  この scene は「文字を消す」1種類の遷移だけを測る。FPS・validation の主張もしない（validation は上記のとおり
  0 だが、この leg は probe であって受入 leg ではない）。
- **未較正は隠さない**: `worldLayers` を変えたので `sceneCalibrationKeyMatches=false`、verdict は
  `UNCALIBRATED_PRESENTED_IMAGE`。判定は verdict ではなく上の測定であり、そのことを report と本 doc が言う。
- 1パック（Supersymmetry）・AMD・1280x720・guiScale 3 のみ。Intel は未実行。

## この round のコード

- `ValidationClient`: `signTextAbTick` scene（配置・4行の書き込み/消去・render tick での3フレーム・HUD/particle/
  カメラの制御・各 step の block と行数の report）。
- `scripts/cpu-contract/sign-text-afterimage-check.py`: mask/ghost/control/noise の判定と、report が
  「文字を書いて消した」と言っていることの検査。
- `scripts/tests/test_sign_text_afterimage_check.py`: 合成画像で、文字が on フレームだけなら PASS、最初の off
  フレームに残れば FAIL（afterimage）、文字が無ければ FAIL（nothing measured）、scene が動けば FAIL（noise）、
  report が書いていない/置いていない/消していないなら FAIL を固定する（7 tests）。
