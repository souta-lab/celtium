# 2026-11-03: off-target windows no longer move the frame image, and the frame's group source is the font path

## 何を測ったか

同じ JAR 系列・同じ scene（`celtium-world-accept`、qmagnet、1280x720、guiScale3、AMD RX 6400 / RADV NAVI24）で、
`-Dceltium.vulkan.passSplit=true` だけを付けた 2 leg を前後で取った。frameBudget は付けていない
（timestamp の readback が `present` と `wfpMiddle` を膨らませるため。round 42 の最初の leg `r42a-probe` は
frameBudget 付きで frame p50 5.6 ms、passSplit だけの `r42b-split` は 3.9 ms だった）。

| bracket (p50 ms) | r42b-split（変更前） | r42c-packet（変更後） |
|---|---|---|
| frame | 3.9 | 3.6 |
| world | 2.7 | 2.5 |
| gui | 1.1 | 1.0 |
| flush (inside world) | 2.2 | 2.1 |
| **winBarrier** | **0.3** | **0.2** |
| winRecord | 1.7 | 1.7 |
| winHead | 0.4 | 0.4 |
| winBeginPass | 0.1 | 0.1 |
| wrecLoop | 1.1 | 1.1 |
| grpDraw | 0.9 | 0.9 |
| wfpMiddle | 0.1 | 0.1 |

`winBarrier` の 0.3 → 0.2 が変更の機構そのもの（下記）。frame の 3.9 → 3.6 は 1 run ずつの比較なので
帰属は主張しない（同じ leg 内の対照ではない）。

## 変更: off-target window は frame image を動かさない

`recordAcquireBarriers` / `recordReleaseBarriers` は毎 window 無条件に frame image の 2 枚
（colour と depth）を GENERAL ↔ attachment layout で往復させていた。off-target window
（mod の framebuffer・entity outline・GregTech の bloom chain）の pass は owned pair を attach するので、
この往復は**その pass が触らない image** に対する対の barrier でしかない。現行 scene は 23 window/フレームのうち
13 が GregTech の post chain（`Shaders.renderFullImageInFBO`）なので、26 回/フレームの
`vkCmdPipelineBarrier` が丸ごと無駄だった。

- `GllessFrameTarget.recordAcquireBarriers(cmd, frameImageAttached)` /
  `recordReleaseBarriers(cmd, frameImageAttached)` を追加し、`false` のとき layout barrier を記録しない。
  release を skip しても image は GENERAL のまま（acquire が動かしていないので oldLayout も GENERAL）で、
  次に attach する window が期待する layout と一致する。
- 判定は `recordImmediateOnlyPass` が framebuffer を選ぶ述語と同じ
  （`!currentIsMain() && identity != 0 && offTargetTarget() != null`）。ただし **clear を持つ window は除く**:
  `applyGameClears` は attachment layout で clear を記録するので、`frameImageAcquireNeeded()`
  （`neverCleared || CeltiumGlStub.hasPendingClears()`）が true なら従来どおり full acquire。
- off-target 経路が途中で frame image に fallback した場合は、`recordImmediateOnlyPass` の catch が
  acquire をそこで記録し `windowFrameImageAttached` を立てる。release は同じ field を読むので対は必ず揃う
  （fallback は「off-target 経路が失敗した」時だけなので計測経路には乗らない）。

## ついでに直したもの

- `setViewportAndScissor` は毎 window 無条件に `vkCmdSetViewport` + `vkCmdSetScissor` を出していた。
  viewport/scissor は dynamic state で `vkCmdBeginRenderPass` ではリセットされないので、同じ command buffer 内で
  値が変わらなければ再発行は不要。既存の tracker（`passVpX` 等、`beginCommands` で sentinel に戻る）と比較して
  一致すれば出さない。23 window × 2 呼び出し = 46 回/フレーム。
- `grpDraw` の bracket が indirect 経路で閉じていなかった（`end` が `if (!indirectConsumed)` の中だけ）。
  `indirectDraws=0` の scene では実害は出ていなかったが、閉じるようにした。

## 次の標的: フレームの group は font path が最大

同じ leg の per-frame 行（`r42c-packet`）:

```
fontRunsStarted=236 fontFlushCalls=236 fontFlushByReason[intervening=0 boundary=118 stateChange=118]
fontWork[pending=0 capture=0] fontQuadsAppended=236 ... immediateStateCalls=9145
```

**1 フレームに 236 本の font run が立ち、1 本あたり 1 quad**（`fontQuadsAppended=236`）。118 本が boundary、
118 本が state-change で閉じている。`CELTIUM_MODEL_GROUPS` は同じ scene で 112–113 group なので、
**font path は model の 2 倍以上の group を作っている**。`grpDraw` は 0.9 ms/フレーム・~2.5 µs/group なので、
font の 236 group だけで ~0.6 ms/フレーム＝Celeritas との差 0.85 ms の大半に相当する。

run が 1 quad で閉じる理由は `celtium$fontRunMatches` の 16 個の witness のどれかが glyph ごとに違うため。
次は「どの witness が違うか」を 1 leg で名指しする（`colourBits` / `projection` / `modelView` / `texture` /
`scissorKey` のどれか）。それが判れば、同じ witness の glyph を 1 run に畳める。

## 検証

- build: `:celtium-vulkan:test` 354 tests / 0 failures、`:forge122:packageJar` + `:validationRemapJar` 成功。
- leg `r42c-packet`（JAR `ab8d5e4f…`、passSplit + packetTimings）: `PASSED`、`problems=[]`、`fixtureStatus=PASSED`。
- leg `r42d-valid`（同 JAR、`-Dceltium.vulkan.validation=true`、240 s）: `PASSED`、`problems=[]`、
  `fixtureStatus=PASSED`、**run dir 全体（`latest.log`・`validation-console.log`・gz 2 本）で VUID 0 行**。
  layout barrier を skip した経路の正しさはこの leg が担保する（`VUID-vkCmdBeginRenderPass-initialLayout-00900`
  と `VUID-vkCmdPipelineBarrier-oldLayout-01197` が出ていない）。
- `packetPipelineMicros=5` / `packetBindMicros=2` / `packetDrawMicros=5` / `packetCalls=88063`:
  driver 呼び出し自体は ~80 ns/packet で、`grpDraw` の ~2.5 µs/group は Java 側（push block の比較と
  descriptor/pipeline の dedup 判定）である。group を減らすのが唯一のレバーだという round 40 の読みを裏づける。
