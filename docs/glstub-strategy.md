# GLStub 戦略（2026-10-29 利用者方針）

**位置づけ**: vanilla の性能・不具合なしを達成した後、Celtium の成否を左右する中核技術は GLStub。
目的は「OpenGL を Vulkan でエミュレートする」ことではなく、**OpenGL の命令を解析し、最適化された Vulkan 描画へ
コンパイルする**こと。互換性を広げるほど共通の高速描画経路（batch/cache/送信最適化）を使い回せる設計を目指す。

この文書は**方針と受入**の正典。実装状況・優先順は [plan.md](<plan.md>)、手順は [development.md](<development.md>)。
GL-less は唯一のモードであり、切替プロパティ（旧 `celtium.vulkan.glless`）と実 OpenGL の bridge 経路は
2026-10-30 に削除した。以下の interception・GL 意味論の話はすべてこの単一モードの内側の話である。

## 現行コードの事実（2026-10-29 時点で確認）

| 項目 | 現状（2026-11-02 改訂） |
|---|---|
| `glBegin`/`glEnd` | **既定で** `LegacyImmediateCapture` が区間を開閉し、頂点を
  `POSITION_TEX_COLOR`(24B)/`POSITION_COLOR`(16B) に詰めて既存の即時描画経路へ渡す。完結する primitive が1つ以上ある
  run は**末尾の不完全primitiveを落として**渡す（GLと同じ。落とした頂点は `trailingVertices` で数える）。
  `-Dceltium.gless.legacyImmediate=false` で従来どおり記録のみ |
| `glVertex*` | 2f/2d/3f/3d/4f/4d/2i/3i/4i/2s/3s を capture が受ける（4成分は w で除算、w=0 は理由付き拒否）。switch offでは no-op |
| `glTexCoord*` / `glMultiTexCoord*` | **1/2/3/4成分 × f/d/i/s の全scalar形式**と、**全buffer形式**（`nglTexCoord*v`・
  `nglMultiTexCoord*v`）を capture が受ける。即時layoutが持つ (s,t) 1組へ収束し、1成分は t=0、3/4成分の r/q は
  保存先が無いため適用しない（`glTexCoord4f` の q≠1 の射影は未モデル。それでも前のUVを残すよりは正しい）。
  multi の **unit 0** がこの組で、unit≠0 は数えるだけで書かない（lightmap座標をalbedoのUVにしない）。
  変換器の既定応答は bare return のまま（lightmapは`LightmapCoordsMixin`経由） |
| `glRect*` | 1呼び出し＝1クアッド。`Begin(QUADS)`＋GL定義の4組(TexCoord,Vertex)＋`End`に展開して同じ sink へ渡す。
  `glBegin`の内側での`glRect`はGL自身のエラーなので名前付きで数える |
| `glColor*` | **GL11の32ネイティブ全て**が raw colour route を通る（glColor3/4 × b/s/i/f/d/ub/us/ui と 16 の
  `ngl*Color*v` buffer形式）。整数幅は幅ごとに unsigned で [0,1]（255 / 65535 / 2^32-1 が 1.0、`(short)65535`・`-1` も
  1.0）。float/int/byte の全形式が同じ3か所へ流れる: font の current colour・stub の legacy colour・backend の colour
  mirror。**既定経路**（legacy switch 不要）。mirror の色は per-vertex colour を持たない即時layoutが読む |
| Tessellator / 即時描画（vanilla 経路） | `submitImmediateBatch(null, stride, formatId, address, count, mode)` で実描画。
  format 1 = POSITION_COLOR（stride 16）、format 2 = POSITION_TEX_COLOR（stride 24） |
| FBO / chain pair | `GllessFrameTarget` / `PostChainBridge` / `VulkanImmediateTexturePipeline` で既定描画は通る。
  多段 FBO（GregTech Bloom）・Raw FBO の整合は RC7/RC8 で前進、Windows 実測は未完 |
| Display List | `glNewList`/`glCallList` は描画内容の再実行/コンパイル経路が限定的。**list compile中のlegacy geometryは
  捕捉してlistのコマンド列に位置どおり差し込み、`glCallList`でreplay時に即時描画経路へ提出する**（2026-11-02、
  `CompiledLegacyRun`、`-Dceltium.glstub.legacyListCompile=false` で旧拒否に戻せる）。行列・texture・blendは
  replay時の状態を使う（GL仕様どおり）。**色とUVも同じ規則**（2026-11-02）: listが設定していない属性は
  `glCallList`時の現在値で頂点コピーを書き換える（`applyCallState`）。list自身が`glColor`/`glTexCoord`を持つ場合は
  焼いたバイトが正しく、頂点間で変わる場合は焼いたまま（`midRun`で数える）。
  `-Dceltium.glstub.legacyCallState=false` で焼き込みに戻せる。**頂点レイアウトも同じ規則**（2026-11-02）:
  replay時の `GL_TEXTURE_2D` の有効状態で layout を選び、compile時と違えば色/UVの位置を移して詰め替える
  （textured→untextured は焼いた色、untextured→textured は call時の UV で sampler が読む）。
  `-Dceltium.glstub.legacyCallLayout=false` で compile時の layout に戻せる。**compile は caller の色/UVを変えない**（2026-11-02）:
  `GL_COMPILE` の `glColor`/`glTexCoord` は `glCallList` まで実行されないので、`glNewList` で色4成分とUVを
  スナップショットし `glEndList` で戻す（`GL_COMPILE_AND_EXECUTE` は実行済みなので戻さない）。直呼びの色ルートは
  stub の「compile中は実行しない」ガードを通らないため、これが無いとlistの最後の色が次の`glBegin`へ漏れる。
  stub側の`GL_CURRENT_COLOR`とbackendの色mirror（format 6/7/9とmodel replayのgroup tintが読む）も同じsnapshotで戻す。
  2層（stub dispatchとtransformer hint）が同じcompileを報告するのでsnapshotは冪等。コピーはlistのpayloadと同じ寿命で、
  同時保持は 64 MiB上限。計器は検証行の `celtium/legacyListRuns[recorded/replayed/bytes/droppedBytes/
  replayRefusals/callState/layout/provenance[colour tex midRun rollbacks]]`。根拠:
  [list compile](evidence/display-list-legacy-2026-11-02.md)・
  [call-time属性](evidence/display-list-call-state-2026-11-02.md)・
  [call-timeレイアウト](evidence/display-list-call-layout-2026-11-02.md)・
  [compile後のcaller状態](evidence/display-list-compile-colour-2026-11-02.md)（いずれもCeleritas実GLと同一sceneで一致。
  GregTechの`ToolEventHandlers`が同じ形） |
| `glPushAttrib`/`glPopAttrib` | **マスクが復元する集団を決める**（2026-11-02）。旧実装はマスクを無視して追跡していた
  ものを全部戻し（vanilla の `GlStateManager.pushAttrib()` は `8256` = `GL_ENABLE_BIT|GL_LIGHTING_BIT` なので、
  マスク外の現在色・line width・point size まで戻していた）、しかも capability・色・サイズしか保存していなかった。
  `LegacyAttribSnapshot` が1回のpush（mask・capability・色・line/point・**depth write mask/func**・
  **blend係数/式・色writeマスク・alpha test**・cull mode/front face・polygon offset・scissor box・list base・
  **bound texture**）を保持し、pop は **push時のmaskが選んだ集団だけ** を戻す。値は各集団の生きた権威
  （raster mirror／backend の depth・alpha mirror／stub）から読み、スタックは64段で上限・溢れは数える。
  depth/alpha を戻せるように `celtium$noteDepthMask/noteDepthFunc/noteAlphaFunc` を raw/managed 両経路から記録し、
  `glGetFloatv(GL_DEPTH_WRITEMASK)` の欠落caseと初期値（GLはtrue）も直した。計器は
  `celtium/attrib[masks= pushes= pops= unmatchedPops= overflow= restoredGroups= selectedUnrestoredGroups= last=
  histogram=[mask:count]]`。`-Dceltium.glstub.attribMasks=false` は旧「全部戻す」動作で、この規則のA/B対照。
  **未対応集団は数える**: `GL_TRANSFORM_BIT`（行列は mirror 側。modは `glPushMatrix` を使う）、`GL_LIGHTING_BIT`・
  `GL_FOG_BIT`・`GL_STENCIL_BUFFER_BIT`。vanilla の mask `8256` は `GL_LIGHTING_BIT` を含むが、その中身（light 参数）は
  パック内の mod が一切書かない（下記）。根拠: [attribute stack](evidence/attribute-stack-mask-2026-11-02.md)
  （fixture の arm D/E が same-scene で Celtium と Celeritas 実GLの両方で一致、validation 0） |
| `glViewport` と `GL_VIEWPORT_BIT` | **viewport は状態であり、push した集団が戻す**（2026-11-03 修正）。`glViewport` の
  記録自体は既にあった（`GL_VIEWPORT` クエリと、offer ごとに読む packed 値）が、attribute stack の `GL_VIEWPORT_BIT`
  は未対応のままだった。パック193 JARの `glPushAttrib` 呼び出しを `javap -c` で全数読み、mask を取った実測:
  Susy-Core の `SunGlowRenderer`/`PlanetSurfaceRenderer`/`RingFeatureRenderer` が `0xFFFFF` を push して
  **block 内で `glViewport` を呼び pop で戻す**（宇宙次元の sky。較正済み overworld leg では走らない）、
  BetterQuesting `RenderUtils` は `0x40B71`（`GL_VIEWPORT_BIT` を含む）、
  GregTech は `0x40`（`GL_LIGHTING_BIT` のみ）、SGCraft/architecturecraft は `0x41040`
  （`TEXTURE|TRANSFORM|LIGHTING`）。逆に light 系（`glLightfv`/`glLightModelfv`/`glMaterialfv`/`glColorMaterial`）を
  呼ぶ mod は**ゼロ**、`glFog` は BiomesOPlenty のみ、という全数結果なので、`GL_LIGHTING_BIT`/`GL_FOG_BIT` を
  未対応のまま残す理由も推測ではなく数えられる。pop は backend の `viewport(...)` 経由で戻す（group key の viewport 項と
  live サイズを所有しているため）。fixture の attribute arm は `GL_VIEWPORT_BIT` を mask に含めて 17x19 の矩形を
  block 内で設定し `attribBefore/Inside/After/OutsideAfter` の `vw=` として前後を報告、`GL_ENABLE_BIT` のみの push では
  31x37 が**残る**ことを同じ frame で主張する。較正済み world leg の census は `histogram=[0x40:1038]`
  `restoredGroups=0`（= この scene の push は GregTech の `GL_LIGHTING_BIT` だけ）。根拠:
  [viewport group](evidence/viewport-attrib-2026-11-03.md) |
| ネイティブ `glGet*` のクエリ | **ルートが一致しなければ「それらしい0」が返る**（2026-11-02 修正）。transformer は
  LWJGL3 の `ngl*` を Java 本体に置き換えるが、置き換え先が専用ハンドラでなく汎用 zero-writer
  （`glGetZero` が1個の0を書く）に落ちても、class は正常にロードされ例外も出ない。実測: `nglGetBooleanv` と
  `nglGetTexParameteriv/fv/Iiv/Iuiv` の route が `"(IJ)".equals(descriptor)` / `"(IIJ)".equals(descriptor)` で
  比較していて**一度も一致せず**（descriptor は戻り値型を含む）、`glGetBooleanv(GL_COLOR_WRITEMASK)` が同一
  frame の `glGetFloatv` の `1111` に対して `0000`、`glGetTexParameteriv(GL_TEXTURE_MIN_FILTER)` が scene の
  設定した `GL_LINEAR` に対して `0` を返した。今は `descriptor.startsWith("(IJ)")`/`startsWith("(IIJ)")` で
  専用ハンドラに入る。検証は `scripts/cpu-contract/TransformerRouteVerifierCheck` の phase 5（変換後の実 class の
  method body が専用 hint を呼ぶこと）と phase 6（stub を install して変換後の native を**実行**し、boolean/
  float/tex の答えを読む）。fixture は `attrib*` に `cmB=`（boolean 幅）と `textureFilter=`/
  `textureFilterExpected=` を併記し、checker が「同じ状態は全ての幅で同じ値」「設定した filter が返る」を主張する。
  根拠: [query routes](evidence/query-routes-2026-11-02.md) |
| `glCullFace` / `glFrontFace` | **enable は状態、面と winding は pipeline 状態**（2026-11-03 修正）。状態モデルは cull の
  enable だけを持ち、全 pipeline が GL 既定ペア（back・CCW）を焼き込んでいたため、`GL_FRONT` を選ぶ mod は逆の面を
  cull され geometry が消えた。同一 frame の `glGetIntegerv(GL_CULL_FACE_MODE)` は `1028` と正しく答えていた
  （ミラーは忠実、読む者がいない）。`VulkanCullState` が (enable, face, winding) を1個の int に符号化し
  （既定ペアは `1` のままなので既存 key・既存の絵は不変）、`SIGNATURE_CULL_FACE`/`SIGNATURE_FRONT_FACE` から
  `state[5]` に載り、5つの immediate pipeline と `VulkanModelInstances`（間接描画の twin も）が
  `VK_CULL_MODE_FRONT*`/`VK_FRONT_FACE_*` を選ぶ。`NativeRasterState.cullFace/frontFace` は version を上げる
  （model replay の値 guard が見える）。根拠: [cull face](evidence/cull-face-2026-11-03.md)
  （fixture の arm F/G が same-scene で Celtium と Celeritas 実 GL の両方 `armF=3600 armG=0`、validation 0） |
| ミラー（GL名→画像） | 供給元が無い名前には**空のミラーを作らない**（2026-11-02）。作っても sampling 側が
  「全内容が来ていないミラー」として拒否するので描けず、しかも entry が存在するせいで以後の lookup が plain hit に
  なり、後から届いた upload を二度と拾えなかった。いまは null を返し、その判断を**level の write serial ごとに記憶**
  （`MirrorDeferralGate`）して、serial が動いた時だけ capture を訊き直す。framebuffer の colour attachment は
  従来どおり所有者の publish を待つ。計器は `patchCostSummary` の `contentlessDeferred`・`deferralSkips`・
  `deferredNames`（CPUテストのみ、GPU leg 未実施） |
| ライトマップのミラー | **「画像がある」＝「ミラーが確立した」ではない**（2026-11-02）。1.12.2 のライトマップは
  `DynamicTexture(16,16)` で、stub が捕まえる level 0 は NULL data の確保（`readCapturedLevel` が
  「allocated with no data」で拒否する）ため readback は**必ず**空振りする。確立経路は所有者の画素
  （`LightmapIdSource.lightmapPixels`）1本に統一し、extent は配列の平方辺（`LightmapExtent`、stub の記録が
  無い時のみ記録に fallback）、content の無いフィールドは再供給する。供給の再試行は level の write serial で
  bound（`MirrorDeferralGate`）。計器は `CELTIUM_ANIM` の `lightmapExtentFromOwner`・`lightmapSupplyRetrySkips`
  と `lightmapSupplyFailure`（CPUテストのみ、GPU leg 未実施） |
| ブロックアトラスのミラー | **「フィールドに入れた」＝「供給が走った」ではない**（2026-11-02）。`refreshAtlasMirror` は
  `imageFor` の像を供給（`celtium$establishAtlasImage`）の**前に**フィールドへ代入していたため、供給が例外で終わると
  「何も書かれていない像」がフィールドに残り、terrain pass の skip 判定（フィールドの null を見る）がそれを
  atlas として描いていた。代入は供給の後に移した（再供給が失敗した時は同じ GL 名の既存ミラーがフィールドに残る
  ので、確立済みの像は失われない）。ライトマップと同じ規則。**部分的にしか塗られていないアトラスは正常状態**
  （sprite 単位で告知される）なので、ライトマップの「全内容」判定はここへは持ち込まない
  （持ち込むと部分告知のフレームで world が消える）。さらに **readback が例外で答えられない時は、告知済みの
  全面画像で確立する**（2026-11-02、`arm=announced-fallback`）。告知は定義上「全面」で、backlog も capture 読み
  も要らない。ただし告知は**その時点の面**についての陳述なので、stub が記録する現在の extent と一致する時だけ
  適用する（アトラスは一度告知された後に re-stitch される。古い告知で resize すると tile 幾何がずれる）。
  健全な経路は readback が投げた後にしか走らないので、順序も呼び出し回数も変わらない。あわせて
  `applyAnnouncedTo` の告知分岐が **entry の extent 記録を全面書き込み後に追従**させる（A07。`fromImage`・
  `fillFromCapture` が既に守っていた規則で、ここだけ欠けていた。告知サイズが違えば `uploadFromImage` は像を
  作り直すので、古い extent は以後の patch 境界と whole-texture 判定を誤らせる）。
  計器は `CELTIUM_DIRECT_SUPPLY` の `arm` と `full`、`pass skipped` の理由行 |

⇒ 2026-10-29 時点の「`SEM_BEGIN` は記録のみ、`glVertex3f` は no-op」は**当時の事実**であり、現在は上の表の状態。
legacy capture は既定offのまま: 受入は[plan.md](plan.md)のgateどおり（validation 0・device lost 0・AMD/Intelの
world+menu leg）で、それまで既定を反転しない。頂点は取るが、**画面に出ることをGPUで確認していない**。

## 参考実装と採用順位

| 優先 | 実装 | 取り入れるもの | ライセンス |
|---|---|---|---|
| 1 | [gl4es](https://github.com/ptitSeb/gl4es) | Immediate Mode / Display List の頂点記録・状態スナップショット・プリミティブ変換・描画結合 | MIT |
| 2 | [ANGLE Vulkan backend](https://github.com/google/angle/tree/main/src/libANGLE/renderer/vulkan) | Render pass 状態機械、Image/Barrier 追跡、Descriptor 管理、FBO キャッシュ | BSD系（移植時は要確認） |
| 3 | [VulkanMod](https://github.com/xCollateral/VulkanMod) | Minecraft API 置換の軽量パターン（**GL 全状態の再現ではない**点に注意） | LGPL-3.0-only |
| 4 | [Mesa Zink](https://docs.mesa3d.org/drivers/zink.html) | Desktop GL の意味論・互換性テストの参照（丸ごと組込みはしない） | MIT |
| 5 | [GLOVE](https://github.com/Sagoia/GLOVE) | Context / State / Resources の責務分離 | LGPL-3.0 |

**採らない方針**: VulkanMod の GL 互換実装をそのまま輸入する（対象範囲が違う: 同実装は `glEnable` 空実装・
`glGetInteger` 0 など、Minecraft 本体が必要とする範囲に限定）。Zink/ANGLE を別 Vulkan コンテキストとして
丸ごと動かす（Celtium の Image/同期と共有できない）。

## 目標アーキテクチャ

```
Minecraft / Forge mods
  GL11 / GL15 / GL20 / GL30 / EXT / ARB
        │  (CeltiumGlStub + LWJGL transformer。既存の interception を維持)
        ▼
Celtium GL Frontend
  GL State Shadow / Legacy geometry (gl4es 由来) / Display List compiler
  Fixed-function shader / GLSL program state / FBO resource tracking
        │  (C 高速部は batch 境界のみ JNI。1 頂点 1 JNI は禁止)
        ▼
Celtium Render IR
  Draw / Clear / Copy / Barrier / Resource handle
        ▼
既存 VulkanTerrainGpuBackend（唯一の VkDevice・リソース所有）
  batching / pipeline cache / async submit / retire queue
```

**重要**: GLStub を別レンダラーにしない。GL の意味論を解釈し、描画・リソース寿命・GPU 同期は
native 経路と共有する（同一 batch・同一 cache・同一 submit 最適化）。

## 5つの開発原則

1. **GL 命令を即時実行しない**: 状態系（`glBindTexture`/`glEnable`/`glBlendFunc` 等）は Java 側の状態更新だけ。
   描画・Clear・Readback など観測可能な順序だけ維持する。
2. **Legacy GL をバッチ化する**: `glBegin/glEnd`・Display List・固定機能入力を構造化 Draw IR に変換し、
   同一状態をまとめ、繰り返し Display List は生成済み GPU リソースを再利用する。
3. **FBO を最重要の互換対象にする**: 多段 Bloom の実例どおり Render Target 切替が主戦場。GL の FBO 操作を
   Vulkan Image/Attachment/Layout/Barrier に対応させる（ANGLE の設計を参照）。
4. **CPU/GPU の暗黙同期を排除する**: GLStub が毎回 Readback/Fence Wait を挟んだら Vulkan 化の意味が消える。
   GPU で解ける依存は GPU に残し、CPU 読み戻しは要求時だけ。
5. **GLStub 経由コストを削る**: 頻出命令の専用高速経路、状態変更の重複排除、事前計算 state key、Pipeline Cache。

## 受入（測ってから最適化する）

| 検証 | 合格条件 |
|---|---|
| Cyberware Radial Menu | `GL_TRIANGLE_FAN` が正しく描画される |
| Forge Splash | `glBegin/glEnd` 描画が欠落しない（ロード画面） |
| GregTech Unreal Bloom | 多段 FBO の合成結果が正しい（画素比較） |
| Display List | 再実行時の再変換が削減される（カウンタで示す） |
| GLStub CPU 時間 | 旧実装比で短縮（`CELTIUM_FRAME_BUDGET` の record/batch と専用計器） |
| GPU リソース寿命 | Validation Error ゼロ |
| 大規模 Modpack | **SuperSymmetry / ATM3 で同一条件の Celeritas を FPS で上回る**（KPI） |

**数字を測らずに最適化しない**。「Linux では速いが Windows の Modpack で 10 FPS」を再発させないため、
フレーム時間は必ず分解して追う（`-Dceltium.vulkan.frameBudget=true` の record/batch/submit/fence/gpu/other）。

## 最初の PR 範囲: `feat/glstub-native-legacy-geometry`

対象を **legacy immediate geometry の捕捉と実描画** に限定する（FBO/GLSL は触らない）。

1. `glBegin(mode)` / `glVertex2f|3f|3d|4f` / `glColor3f|4f|4b` / `glTexCoord2f` / `glNormal3f` を
   **意味論ハンドラ**として登録し、頂点・色・UV・法線を記録する（no-op から外す）。
2. プリミティブ変換: `GL_QUADS`→2 三角形、`GL_TRIANGLES`、`GL_TRIANGLE_STRIP/FAN`、`GL_LINES`、`GL_LINE_STRIP`。
3. 頂点は現在の「POSITION_TEX_COLOR (stride 24)」または「POSITION_COLOR (stride 16)」に詰め、
   既存 `submitImmediateBatch` へ渡す（行列・fog・color/alpha の push は既存経路のまま）。
4. **JNI は batch 境界のみ**。gl4es の C 実装（頂点記録・状態スナップショット）は、まず Java で意味論を
   固定してから、測って遅い部分だけ C へ移す。C を入れる場合も VkDevice/VkImage の所有権は Java 側に残す。
5. 受入: Forge Splash（`glBegin(GL_QUADS)` 3 箇所）と Cyberware radial menu を実機で描画し、
   validation error 0・フレーム時間の悪化なしを確認してから次段（FBO/表示リスト）へ進む。

### 現在地（2026-11-02、既定ON）

上の 1–3 は既定で有効。捕捉の詰め方は
`LegacyImmediateCapture`（GL/Vulkan 非依存）に分離し、`LegacyImmediateCaptureTest` が
layout・stride・UV/色のバイト・`w` 除算・末尾primitiveの切捨て・拒否理由を CPU で固定する。トランスフォーマの経路は
`glVertex2f/2d/3f/3d/4f/4d` と `glVertex2i/3i/4i`・`glVertex2s/3s`、テクスチャ座標は
`glTexCoord1/2/3/4 × f/d/i/s` とその buffer 形式、`glMultiTexCoord1/2/3/4 × f/d/i/s` とその buffer 形式
（即時layoutの (s,t) 1組へ収束し、整数と short は float layout への丸め。`glVertex4i` も `w` 除算し、`w=0` は名前付きで拒否）、
受理する primitive は `VulkanImmediateDrawer.refusalReason`
（quads/triangles/fan/strip/quad strip/polygon/lines/line strip/line loop）で、ここにあった独自リストは削除した。
`scripts/cpu-contract/transformer-route-verify.sh` は legacy 経路を **ON/OFF 両方の構成で** 同じ検査を行う。

**既定ONの根拠**: 既定OFFの理由だった black screen は rc.24 で retire キューが記録中のコマンドバッファを
破棄する競合（`VUID-vkEndCommandBuffer-00059` → RADV GPUVM fault）と判明し、修正済み。2026-11-02 に AMD RX 6400 と
Intel UHD 630 で world+menu を含む leg（`r13f`/`r13g`、同一jar）を回し、validation clean・device lost 0・
`CELTIUM_LEGACY_REFUSED` の理由が説明可能（内容を持たない 256x256 の viewport canvas で、GL でも alpha 0 で
何も出ない）なことを確認した。`-Dceltium.gless.legacyImmediate=false` で従来の no-op 構成に戻せる。
根因・実測・残りは [証拠](evidence/legacy-immediate-default-2026-11-02.md)。

## 未決・リスク

- **頂点形式の互換**: 固定機能の頂点属性（法線・複数テクスチャ座標・color material）をどこまで format に載せるか。
  未対応属性は「描かない」ではなく、**理由を数える拒否**として記録する（無出力を正しさと誤認しない）。
- **Display List の意味論**: 記録時状態の再現・ネスト・再定義・`glCallList` 内の状態変更を OpenGL 仕様どおりに。
- **C 移植の採算**: JNI/コピーで遅くなるなら Java に残す。C 化自体を成果にしない。
- **ライセンス**: gl4es(MIT) の取り込みは著作権表示を保持。VulkanMod(LGPL-3.0-only)/GLOVE(LGPL-3.0) から
  コードを直接持ち込む場合は条件を確認し、設計のみの参照にとどめる判断もあり得る。
- **KPI との関係**: GLStub 改善は SuperSymmetry / ATM3 の FPS・エラー 0・完全動作の達成手段であり、
  単独の最適化を目的にしない。
