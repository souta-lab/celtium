# 2026-10-12 — the blend / colour-mask / alpha-test state contract (BH-07, BH-10, BH-03's comparison half)

担当: blend-state。単位は「GL が設定した状態が pipeline の key と作成引数に届くこと」の1つ。
コードは Lead が `2e49ce0f2` として着地済み（下記の追補込み）。本書は**その測定**の記録である。

## 1. 何を変えたか（file:line は着地時の HEAD）

| 対象 | 内容 |
|---|---|
| `CeltiumGlStub.java` | `SEM_BLEND_FUNC`/`SEM_BLEND_FUNC_SEPARATE`/`SEM_BLEND_EQUATION`/`SEM_BLEND_EQUATION_SEPARATE`/`SEM_COLOR_MASK` を追加し、`glBlendFunc`/`glBlendFuncSeparate`/`glBlendEquation`/`glBlendEquationSeparate`/`glColorMask` を NOOP 表から外して記録に回した。四引数の case は `c[2]`/`c[3]` を読む（dispatch が名付けるのは `a0`/`a1` だけ — scissor と同じ罠）。初期値は GL 自身の初期状態（ONE, ZERO / FUNC_ADD / 全チャンネル）で、`GlStateManager.BlendState` の cache 初期値（1,0,1,0）と一致することを原典で確認した |
| `VulkanGlState.java` | `colorMask` と `blendEquationRgb/Alpha` の mirror slot、`pipelineKey` へ equation と mask を追加 |
| `VulkanColorBlendState.java`（新規） | enable・RGB factor 2・alpha factor 2・equation 2・write mask を1つの不変値にまとめ、pipeline の attachment がここだけを読む |
| `VulkanImmediate*Pipeline.java` | 5クラス全ての colour-blend attachment が `VulkanColorBlendState` を読む（alpha pair の固定と write mask の固定を削除） |
| `VulkanTerrainGpuBackend.java` | `celtium$captureState` が stub の記録から blend enable / 4 factor / 2 equation / mask / alpha 比較関数を読み、配列の**末尾**に追加（先頭9要素の意味は不変）。key は配列全体から作るので、alpha pair だけ・mask だけが違う batch は別 pipeline かつ別 group になる。`push.putFloat(100, state[14])` は group の捕捉値から書く（live ではない） |
| `GlStateManagerMixin.java` | `func_179120_a`（int 版 tryBlendFuncSeparate — 原典 SRG で確認）、`func_187398_d`（glBlendEquation）、`func_179135_a`（colorMask）の3フック。enum 版は int 版へ委譲するので二重更新は同じ値の再書き込み |
| `common/.../TerrainGpuBackend.java` | 上記2つの default メソッド（bridge 側の受け口） |

計測用スイッチ（既定は全て現行挙動）:
`-Dceltium.vulkan.immediateBlendAlpha=false`（alpha pair を旧固定値へ）、
`-Dceltium.vulkan.immediateColorMask=false`（mask を全チャンネルへ）、
`-Dceltium.vulkan.immediateBlendEquation=false`、
`-Dceltium.vulkan.alphaFuncOverride=<GL enum>`（有効な batch の比較関数だけ差し替え）。

## 2. 脚（jar は脚ごとに違う。lock JSON の `mods[].sha256` が正）

| 脚 | jar | 内容 |
|---|---|---|
| bs1-control | `2db9929d` | clouds=2、blend alpha と mask を旧挙動へ |
| bs2-fixed | `2db9929d` | 同上・既定（修正後） |
| bs3-less | `2db9929d` | bs2 + `alphaFuncOverride=513` (GL_LESS) |
| bs4-always | `2db9929d` | bs2 + `alphaFuncOverride=519` (GL_ALWAYS) |
| bs5-repeat | `fd363194` | bs2 と同一旗（同一 build ではない） |
| bs6/7/8 | `6ebe09e7` | clouds=0 の対照 — **3本とも device lost で失敗**（§5） |

条件: `glless=true`・`presentReadback=true`・`diagnostics=true`・`dumpPresentedImage=true`・
`scene=terrain`・`keepGui=true`・`captureEntities=true`・camera 0.5/12/0.5 yaw180 pitch20・
capture は全脚 `worldTick=200`。

## 3. 計器（修正前は構造的に 0、または存在しなかった）

`CELTIUM_BLEND_CENSUS`（bs2・終了時 1 行）:
`blendFuncCalls=154 blendFuncSeparateCalls=1387 blendEquationCalls=0 colorMaskCalls=968
factorsCalled=true equationCalled=false colorMaskCalled=true lastFactors=(770,771,1,0)
lastEquations=(32774,32774) lastColorMask=0xf`

per-batch（`verificationSummary`、bs1 → bs2）:

| 計器 | bs1-control | bs2-fixed |
|---|---|---|
| `blendGroups` | 3517 | 5159 |
| `blendAlphaNonDefaultGroups` | 3517 | **5159** |
| `partialColorMaskGroups` | 382 | **479** |
| `lastPartialColorMask` | 0x7 | 0x7 |
| `nonAddBlendEquationGroups` | 0 | 0 |
| `alphaFuncGroups` | 2650 | 3755 |
| `alphaTestDisabledGroups` | 1215 | 1921 |
| `lastAlphaFunc` | 516 | 516 |
| bs3 `lastAlphaFunc` / `nonGreaterAlphaFuncGroups` | — | 513 / 3927 |
| bs4 `lastAlphaFunc` / `nonGreaterAlphaFuncGroups` | — | 519 / 2568 |

読み: この scene で blend が有効な batch は**全数**が旧 pipeline の固定 alpha pair `(ONE, ONE_MINUS_SRC_ALPHA)`
以外を要求していた（`glBlendFunc` は RGB と alpha を同じ pair にするので、`blendFunc(770,771)` の時点で
alpha も `(770,771)`）。write mask は 479 batch が部分 mask（最後は `0x7`）。比較関数は 516 が既定で、
override 脚では 513/519 が実際に捕捉された。

健全性（bs2）: `validationClean=true`・`validationMessageCount=0`・`refusedBeforeAppend=0`・
`postAcceptDroppedGroups=0`・`immediateGroupsDiscarded=0`・`staleGroupRefusals=0`・`windowFlushFailures=0`・
`modelDrawRefusals=0`・`gpuStalled=false`・`immediateGroupsDrawn=4104`。

## 4. 絵は「未確立」（理由も測った）

- 提示 dump の alpha は **全脚・全画素 255**（4チャンネル PNG で測定）。BH-07 の alpha factor が動かすのは
  destination alpha だけなので、**この artifact では alpha 半分の絵の主張は原理的にできない**。
- 同じ build（`2db9929d`）内の比較: bs1(control) vs bs2(fixed) は sky 帯 56% だが、**bs1 vs bs4 は sky 帯 0.01%**
  （bs4 は修正後 + ALWAYS）。bs4 は修正が入っているのに control と一致する。
- sky の平均輝度は旗ではなく **capture 時の build 状態**（`captureListedSections`）に追従した:
  33 sections → (151.3,172.8,212.8) [bs1] / (152.7,174.4,214.8) [bs4]、
  49–52 sections → (158.2,181.1,223.4) [bs2] / (161.8,185.5,227.0) [bs3]。
  同じ sections の bs1 と bs4 は旗が違うのに 1.4/255 しか違わない。
- 従って bs1↔bs2 の sky/cloud 帯差は **build 状態の交絡**であり、mask の効果としては帰属できない。
  この pose（pitch +20、雲は y≈108 で視界の上）では Fancy 雲の depth-only pass が画面に画素を残さない、
  という説明が最も単純で、`lastPartialColorMask=0x7`（mask されているのは alpha チャンネル）とも整合する。
- 交絡を除く比較（同一 build・同一 sections・flags だけ違う脚）は取れていない。次の実験は
  「clouds=2 + pitch −30（雲を見上げる）」で mask 0x0 の group を画面に入れ、
  さらに mask の histograms（0x0/0x7/0xf）を計器に足すこと。

## 5. 現在の HEAD は脚が取れない

HEAD `f9150335`（jar `6ebe09e7`）で terrain 脚を3本連続で落とした:
`settleGpu(region eviction): vkDeviceWaitIdle returned -4`（bs6/bs7）、
`vkQueueSubmit returned VkResult -4`（VulkanContext.endCommands:650 ← flushUploads:1716 ← reserveUploadSlot:1589、bs8）。
`fixtureStatus=NO_CAPTURE`・exit 255。blend 経路ではなく upload/queue 経路で、OPT-05 と BH-14 が触った場所。
