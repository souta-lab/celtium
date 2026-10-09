# Sol 集中修正の引き継ぎ（2026-10-10）

このターンで扱った最優先課題は **format 2（POSITION_TEX_COLOR）を既定の受付対象に加えると、`vkBeginCommandBuffer` の外で
`vkCmdBindPipeline`／`vkCmdBindDescriptorSets` を記録したという validation エラーが約70件出る** というものだった。
結論から書く: **原因を特定し、記録を正式なコマンドバッファ・ライフサイクルへ載せる修正を実装した。AMDで再現と受入が取れ、
通常の色pattern受入と「受理後・submit直前に失敗させる」試験の両方が通った。** Intel は最終artifactで実行中。

## 1. 根本原因と修正箇所

### 原因（ソースで確定）

- **backend は自分専用の永続 `VkCommandBuffer` を持つ**（`createCommandResources` が自前の pool から1本確保）。
  この handle は submit 後も **null にならない**ため、`drawPendingImmediate` の `commandBuffer == null` ガードは
  何も検出できない。
- `flushPendingBeforeGlDraw()` が **`drawPendingImmediate()` を直接呼んでいた**。つまり begin も render pass も
  acquire/release も submit も無いまま、pipeline の bind／descriptor／push／vertex/index／draw を記録していた。
  format 2 を受理すると、この経路に実バッチが入るため illegal recording になる。
- 前ターンの `VulkanContext.isRecording()` は **bootstrap 用の別 handle（context の pool から都度確保）** を追っており、
  backend の handle を表せない。「1つの boolean で別々の command buffer を混同しない」という指摘のとおりである。
- validation の「70件」は **7つの VUID が `duplicate_message_limit=10` で抑制された結果**であり、不正描画の件数ではない
  （`.celtium-work/runtime/p30l-format2-amd/validation-console.log` の 374–380 行）。

### 修正

| 変更 | 内容 |
|---|---|
| `VulkanCommandBufferState`（新規） | backend の handle 1本のライフサイクルを追う: `INITIAL → RECORDING → RENDER_PASS → EXECUTABLE → PENDING →（fence確認後）EXECUTABLE`。target も保持し、pass 外・別 target の draw を例外にする |
| `VulkanContext` | 誤った所有者だった context 全体の `recording` フラグと `isRecording()` を削除。bootstrap handle は従来どおり呼出し側が begin/end する |
| `flushImmediateWindow()` | **同期・同一targetの LOAD window** に作り直した: `beginFrame → begin → recordAcquireBarriers → pass(begin/draw/end) → recordReleaseBarriers → end → submit → fence確認 → endFrame`。古い target の受理済みgroupを、GLがまだその FBO を bind している間に描き切る |
| `flushPendingBeforeGlDraw()` | 直接描画をやめ、render pass 内なら pass 内 draw、それ以外は `flushImmediateWindow()`。失敗は握り潰さず `gpuStalled` を立てて例外を投げる（GLへの二重描画や黙殺をしない） |
| `prepareImmediate()` | pipeline と descriptor を **append 前に**用意して group に持たせる。A08 の「受理前preflight」を format 2/3/4/5/6/7/8/9 と texture にも広げた |
| group 所有権 | `immediateGroups → recordedImmediateGroups（記録済み）→ retiredImmediateGroups（submit成功）→ fence確認後に解放`。失敗時は queue へ戻し、記録数を戻す |
| `waitForIdle()` | fence timeout・error で **false を返し `gpuStalled` を立てる**（以前は true を返し、pending buffer を reset していた）。`settleGpu` も stalled なら例外 |
| 順序境界 | `bindFramebuffer` HEAD（旧targetがまだ bind 中）、`flushBeforeClear`、`viewport` HEAD、`announceTextureUpload`／`announceAnimatedUpload`／`announceDynamicTexture` の各入口で flush |
| `captureGlMatrices()` | world 外（GUI/overlay/別FBO）で **mirror の ortho/modelview を snapshot** する。従来は return して前の world 行列が残り、実GUI geometry が clip されていた |
| `pendingImmediateIndices` | 退役時に減算していなかったため全 group が閾値超過で即 flush されていた。減算を追加 |
| `VulkanImmediateFormats` | `DEFAULT_ACCEPTED = "1,2,3,4,5,6,7,8,9"`（format 2 を含む） |
| `VulkanDynamicMesh`／`VulkanHostBuffer` | 頂点・index の容量を**両方確保してから**書き込み（部分書き込みを残さない）。`VulkanHostBuffer` の成長は新規作成→コピー→旧解放の順 |
| `VulkanTextureRegistry.invalidateRenderedTexture()` | FBO書き戻し後、古い CPU announcement を破棄して GL から読み直す（古い画像で上書きしない） |
| off-target scaffold | 常に `return false` していた旧 `VulkanOffscreenTarget` 経路（空 pass を clear/copy していた）を削除。実描画は上記 window が担う |
| Mixin | `FramebufferMixin` に `func_147609_e`（unbind HEAD）を追加、bind/clear/viewport の順序失敗を握り潰さない |

## 2. 検証条件・artifact・結果・限界

### 再現（修正前）

`.celtium-work/runtime/sol-cb-base-amd/`（HEAD `e90e0c848` 相当の jar を `.celtium-work/sol-command-buffer/base-artifacts/`
へ SHA-256 付きで固定して実行）:

```
exit 1 | validationClean false | validationMessageCount 70 | fixture PASSED
vkCmdBindPipeline/vkCmdBindDescriptorSets/vkCmdPushConstants ... was recorded, but vkBeginCommandBuffer() was not called
```

### 受入（修正後・AMD RX 6400）

| run | 結果 |
|---|---|
| `sol-cb-fixed2-amd` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0 |
| `sol-cb-final-amd` | 同上（最終ビルド） |
| `sol-cb-final-retry-amd` | 同上＋`retries=1`（fixture内で submit 直前失敗→再試行が実際に走った） |

fixture が確かめていること（`celtium.validation.commandBufferTargets=true`）:

- main に青 quad を1件 pending させ、別FBOへ bind する瞬間に **旧mainが flush される**（`mainWasPending=true`）
- 別FBOで赤・緑を**別々の begin/draw**で描き、mainへ戻る瞬間に **別FBOが flush される**（`secondaryWasPending=true`）
- main にもう1件（黄）を描いて flush
- 受理・記録・submit・完了が **すべて4件**、pending 0、invalidDraws 0、off-target 2件
- 画像: main の青・黄、別FBOの赤・緑が正しい位置にあり、**両targetの未更新領域は1ピクセルも変わらない**
- 3枚のPNG（`screenshots/celtium-command-buffer-{main-before,main,secondary}.png`）とハッシュを
  `.celtium-work/sol-command-buffer/acceptance-summary.json` に固定

### 純ロジック受入

`celtium-vulkan/src/test/.../VulkanCommandBufferStateTest.java`（4件、`BUILD SUCCESSFUL`）:
別handleの独立、pass外・別targetの draw 拒否、PENDING 中の reset/begin 禁止、pass を開いたままの end 禁止。

### 限界（未実施・未検証）

- **Intel UHD 630**: 最終artifactで **target受入は `passed=true`**（ログの `CELTIUM_COMMAND_BUFFER_TARGETS` 行、カウンタ4/4/4/4・
  off-target 2/2/2・ピクセル不一致0）。しかし **run verdict は2回とも得られていない**:
  1回目は `worldFrames < 100` の起動ゲートで FAILED（過去のIntelは114・129と余裕が小さい）、
  2回目は480秒の duration cap に当たり report 未到達（`fixtureStatus MISSING_REPORT`）。
  どちらも **Intelでフレーム数が足りない**ことを示す。最有力の原因は、同期windowが1回につき
  ブリッジ往復（color blit in・depth変換・fence待ち・color blit back・depth変換）を伴うことで、
  format 2（GUI/overlay）が既定受理になってwindow数が増えたため。**次段の最優先は window 数の削減**。
- このfixtureは **depth occlusion を確かめない**（depth test を切っている）。別FBOの depth 依存・順序は未検証。
- 画面（window/compositor）への present 到達は相変わらず未確認（環境の X が 0x0）。
- 失敗経路で確認したのは **submit前の失敗**のみ。submit成功後の `endFrame` 失敗、fence timeout、
  device lost は分岐を実装したが **実機注入していない**。
- `recordImmediateOnlyPass` は acquire/release を呼出し側（window）で記録する形に変わった。terrain の通常 pass は
  従来どおり `render()` 内で acquire/beginPass/…/endPass/release を行う。

## 3. 残る問題と、次に実装する具体的な一手

1. **Intel 受入を閉じる**（上記コマンドの完走を確認。落ちたら `logs/latest.log` の
   `CELTIUM_COMMAND_BUFFER_TARGETS` と `validationFirstMessages` を読む）。
2. **1フレームあたりのブリッジ往復を減らす**（Intelのフレーム数対策。設計案は3つ:
   (a) 同一targetの連続windowを合体する、(b) GLのclearを「windowを強制する境界」ではなく
   「次のwindow内で適用するイベント」として保持する、(c) main targetの受理済みgroupはフレーム自身の
   terrain pass で描く（`recordRenderPass` は既に描いている）。いずれも順序の正しさを保つ条件を
   docsへ明記してから実装する）。
3. **submit後失敗の実機注入**を足す: `submitCommands()` 成功後・`endFrame()` 前に例外を注入する property を作り、
   「二重signalingしない・資源を壊さない・stallして可視に失敗する」ことを1 run で示す。
3. **別FBOの depth/clear 依存**を小さな場面で確認する（depth test 有効・clear 後の上書き）。
4. 監査 A04/A05 の残り（atlas stitch 時の全面データ供給、sprite単位mip）と A12/A13（state model、current RGBA）。
5. ~~通常fixture回帰の確認~~ **実施済み**: `p31a-format2-default-amd` が exit 0 / clean / msgs 0 / fixture PASSED、
   `worldFrames=228`、`acceptedByFormat=1:0,2:217,3:0,4:0,5:214,6:1394,7:1136,8:0,9:0`、
   **recorded=submitted=completed=2961**、invalidImmediateRecords 0、retries 0。
   → AMDでは format 2 を既定受理しても回帰せず、受理した即時バッチが記録・submit・完了まで完全に一致する。

## 4. 未コミット変更

このターンの変更はすべて **未コミット**（`git status` の11ファイル＋新規3ファイル）。内容は上表のとおり。
コミットは次の単位に分けると読みやすい:

1. `VulkanCommandBufferState`＋テスト＋backend のライフサイクル／window／preflight／所有権
2. fixture（`ValidationClient`）＋runner script
3. 文書（`docs/plan.md`、`docs/scope-and-limits.md`、本ファイル）

ビルドは `:celtium-vulkan:test` を含めて成功済み。**ゲーム内受入は AMD の3 run のみ**である。
