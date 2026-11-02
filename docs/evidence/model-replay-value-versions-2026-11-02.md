# model replay の値 guard を version 4 本に置き換えた — 効いたのは 14 ns だけ（2026-11-02）

測定した renderer `2a2d71a7d7c90ac79ea0b467f2a12181045bb01673cf730d55b74f9929b6dbf3`、fixture
`a7bf1b725034e3f9eff03c9c4e5b74b6a2fbf5bdf188678b51bd7742bd102077`（rc.26、`main` 23d936050 + 本差分）。
AMD Radeon RX 6400（RADV NAVI24）・mode `plain`・X11・validation 既定 ON・200 s・Supersymmetry instance・
`CELTIUM_LEG_SEED_OPTIONS=guiScale3.txt`（guiScale=3、renderDistance=12）・`-Dceltium.vulkan.batchTimings=true`。
**同じ jar を 2 回**、`-Dceltium.vulkan.replayValueVersions=false`（対照）と既定（本変更）で交互に走らせた。

## 観測 → 仮説 → 判別条件

- 観測（r19d）: hit 288.5 ns・前段 95.6 ns・383 ns/replay。guard の値 half は「19 inputs の signature capture +
  projection 16・colour 4・affine 6・lightmap 2・payload 20・combine 4 floats の比較」で、replay ごとに同じ bytes を
  読み直していた。
- 仮説: それらの bytes は **変更が必ず setter を通る**ので、群ごとの version 1 本で「変わっていない」を証明できる。
- 判別: 同じ jar・同じ scene・同じ flags の 2 leg で `hitNanos/hitCalls` と前段が下がるか。hit 率が落ちないか
  （version が粗すぎれば miss が増える）。**leg 間の単価は 15 % 動く**ので、別 build との比較では判定しない。

## 実装

1. `VulkanGlState`: `stateVersion`（blend 7・colorMask・alpha test 3・cull enable・depth 3・lighting enable・現在色）、
   `projectionVersion`（projection stack）、`textureMatrixVersion`（texture stack 両 unit）。**modelview は上げない** —
   replay ごとに変わる唯一の入力で、上げると全 replay が refuse される。
2. `NativeRasterState`: blendEnabled/blendFunc/blendEquation/colorMask/cullEnabled が**値の変わった時だけ** version++。
   raw-GL adapter（`NativeRasterAdapter`）が VulkanGlState を経由せず直接この owner を書くため、version は owner 側に置く。
   `VulkanGlState.stateVersion()` は両者の**和**（どちらも単調増加なので、和が等しい ⟹ 両方等しい）。
3. `NativeTextureEnvironment`: parameter/color が実際に変わった時だけ version++（unit 0 の MODE/RGB と combine 色）。
4. guard: 4 version が tuple と一致 → signature/colour/affine/projection/combine を **capture も compare もしない**。
   `lightCoord` 2 floats・payload 20 floats・combine 4 floats は常に比較（matrix と光で version を動かさず変わり得る）。
   不一致 → 従来の full compare。粗い version は work を増やすだけで、誤 hit は作らない。
5. projection remap cache の key を 16 floats の比較から `projectionVersion` の比較へ（前段の一部）。
6. slow path では version が証明済みでも signature/colour/affine を capture し直す: tuple に前 replay の bytes を
   入れると、version が覆わない値（lightCoord/payload/combine）が変わった時に誤 hit になる。
7. 対照 switch `-Dceltium.vulkan.replayValueVersions=false`（既定 ON、laboratory 用）。

## 結果（leg `r20b-off` / `r20b-on`、同一 jar・同一 scene・同一 flags）

| 項目 | off（対照） | on（本変更） | 差 |
|---|---|---|---|
| replay 数 / world frames | 2,318,996 / 114 | 3,422,049 / 134 | leg の差（後述） |
| hit 率 | 98.737 % | 99.101 % | +0.36 pt |
| `hitNanos/hitCalls` | 721,296,112 / 2,289,709 = **315.0 ns** | 1,048,815,870 / 3,391,291 = **309.3 ns** | **−5.7 ns** |
| `modelEnqueueMillis / calls` | 954 / 2,318,996 = **411.4 ns** | 1359 / 3,422,049 = **397.1 ns** | **−14.3 ns（−3.5 %）** |
| 前段（enqueue − hit − miss） | **95.3 ns** | **88.1 ns** | **−7.2 ns** |
| `modelReplayCache[light]` | hit 43.2 %（1,012,680 / 2,345,719） | hit 43.1 %（1,488,850 / 3,451,051） | payload 再構築率は不変 |

つまり **値 half の capture + 比較は 6 ns しかなかった**。projection cache の version 化が 7 ns、合計 14 ns/replay
（3.5 %）。前段は scene に依存しない量（matrix copy・projection remap・lightmap coord・combine 色・
ensureUsableLightmap）なので、ここの 7.2 ns は変更の効果として読める。

**同じ変更の直前 build（`1e17cbb6…`、leg `r20a-versions`、58 world frames）は hit 357.4 ns だった**。同じ code が
another leg で 357 と 309 を出したので、**別 build・別 run の単価比較は 15 % の誤差に埋もれる**。r19d（288.5 ns）と
本 leg を比べて「悪化した」と読むのは誤りで、同じ理由で「改善した」とも読めない。判定は上の同一 jar 対照だけで行う。

## 受入（両 arm とも同じ jar）

| leg | 結果 |
|---|---|
| `r20b-off` | `accepted=true`・`verdict=PASSED`・`fixtureStatus=PASSED`・`sceneGateCalibrated=true`・`sceneCalibrationKeyMatches=true`・floors enforced・`presentedDistinctColours=12767`・`presentedBlackFraction=0.003771`・`presentedImageAssertionFailures=0`・`validationMessageCount=0`・`validationAllowedCount=0`・`validationClean=true`・`validationExitCode=0`・`gpuRequirementMet=true`・model refusals 0・161.9 s |
| `r20b-on` | 同左・`presentedDistinctColours=11553`・`presentedBlackFraction=0.00377`・`presentedImageAssertionFailures=0`・`validationMessageCount=0`・`gpuRequirementMet=true`・model refusals 0・173.8 s |

両 arm の calibration key は同一（`scene=terrain pose=x=0.5 y=12 z=0.5 yaw=180 pitch=20 guiScale=3`）。
distinct colours が 12.8k と 11.6k で違うのは frame 数（114 vs 134）と撮影 frame が違うためで、floor は 16。
画像 `screenshots/celtium-presented-world-run-1fabc754a214-330bedb4-kept.png`（off）と
`…-1fd17929fdb7-…-kept.png`（on）。

CPU: build 267 tests / 0 failures（`ModelReplayFastPathTest` 16・`ReplayValueVersionTest` 5・
`ModelReplayInputCacheTest` 6・`ModelStateSignatureTest` 6・`ModelLightRotationTest` 4）・
route verify called=241 failed=0・classes 87/87・colour 32/32・texcoord 64/64・scripts tests 106 OK・
check_agent_docs OK・instruments 再生成。

## 言えないこと / 次に効くもの

- **FPS は主張しない**。replay 単価 −14 ns（−3.5 %）で、この leg の replay CPU は 1.36 s / 174 s。
- 値 half が 6 ns だったので、**guard の残り 309 ns は別物**である: record write（offline 23.6 ns）・
  payload（43 % の replay で再構築 = 3x3 inverse + 2 sqrt + 20 stores）・run 条件（projection 16 floats・
  fog・viewport・`sameBuffers` の比較）・census（projection 16 + modelview 16 floats の copy と比較）・
  `noteStateStatistics`。次はこの 5 つを **1 leg 内で sub-bracket して** 最大のものを撃つ。値 half をさらに削る根拠は
  もう無い（この leg が否定した）。
- **version の完全性はコード監査と `ReplayValueVersionTest` が固定する**が、将来 setter を足して bump を忘れれば
  false hit になる。`VulkanGlState` の setter はこのクラスが唯一の writer、`NativeRasterState` は owner 自身が
  bump する。`stateVersion()` の和は単調増加のみ。
- 同一 jar 対照の 2 leg は **frame 数が違う**（114 vs 134）。per-replay に正規化した比較であり、
  enqueue 総量の比較ではない。
