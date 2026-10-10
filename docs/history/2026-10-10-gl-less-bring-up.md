# GL-context-less bring-up（2026-10-10、commit `4bcff8de7`）

> 本書は証拠と経緯であり、**現在の判定ではない**。現在の到達点・優先順・受入は `docs/plan.md` §0.114 と §1 にある。
> 対象 commit は `4bcff8de7`（"GL-less: owned frame target, float marshalling, texture capture, presented-image
> assertions"、親 `bb271a660` "GL-less window scaffold: stub GL provider, NO_API window, splash skip"）。
> 引用する run はすべて `.celtium-work/runtime/` 配下・headless・flag 付き（`-Dceltium.vulkan.glless=true`）で、
> GPU は `logs/celtium-capabilities.json` の `vulkanDevices` が示す AMD Radeon RX 6400（RADV NAVI24）。

この milestone で言えることと言えないことを先に固定する。

- 言える: **GL context を生成せずにゲームが world まで起動し、Vulkan が毎フレーム提示し、GL swap を1対1で飛ばし、
  validation layer のメッセージが 0 で走った**（`p548-glless`〜`p560-glless`）。
- 言える: **driver なしで terrain pass が開き、geometry が record→submit→complete した**（`p549-glless` の
  `pipeline for solid created`・`firstDraw region=8 pass=solid`）。
- 言えない: **窓が world の絵になっている**。`p559-glless` は `FAILED_PRESENTED_IMAGE` で、owned target の frame dump は
  空色＋灰色 wedge＋全幅の黒帯、提示画像は一様色に近い。**受入ではない。**
- 言えない（harness）: GL-less run の `validation-process.json` は `exitCode=0` だが
  **`validationExitCode=1`** である（`p557-glless` だけは timeout で `exitCode=143`・`validationExitCode=124`・
  `fixtureStatus=NO_CAPTURE`）。run 時に `--require-gpu 'RX 6400'` を渡しており、GL renderer が stub
  （`glRenderer="Celtium stub GL"`）なので harness は GPU を確認できない（`gpuRequirementMet=false`）。
  したがってこの節の根拠は**計器行と画像**であり、harness PASS ではない。対照の `reg-bridge3` は
  `validationExitCode=0`・`gpuRequirementMet=true`。

## 何を作ったか

- **GL stub provider + NO_API window**（`bb271a660`、本 commit で拡張）: LWJGL 3.4.1 の
  `GL.create(FunctionProvider)` は public なので context 不要で capabilities を作れる。boot 時に
  `CELTIUM_GLLESS stub GL provider installed`・`window hinted GLFW_NO_API`・
  `splash progress skipped (no shared GL context)` を出す。texture/framebuffer の name 生成は stub 側の dispatch 表が
  per-name increment id を返す（`nextName`）。
- **owned frame target**（`GllessFrameTarget` + `FrameImageTarget` インタフェース）: colour/depth image を import
  ではなく create し、game の `glClear` をその image へ運び、frame の semaphore は Vulkan submission だけが
  signal/consume する（`signalWait()` が beginFrame の wait semaphore を submit で signal し、`consumeDone()` が
  最後の submission の done semaphore を wait して次 frame のために消費する）。
- **float marshalling の修正**（`CeltiumGlStub`）: 関数ごとの宣言済み引数型から CIF を作る
  (`SIGNATURE_DESCRIPTORS`・`FLOAT4_DESCRIPTOR`・`DOUBLE1_DESCRIPTOR`)。plain upcall は 12 slot に widen し、
  `glTexImage2D` の pixels pointer が Java に届くようにした。
- **driver 無しの texture 内容経路**（`CeltiumGlStub` + `VulkanGlTexture` + `VulkanTextureRegistry`）: stub が
  `glTexImage*`/`glTexSubImage2D` の pixels を RGBA8 で記録し（full image・patch の累積・変換に要る unpack state）、
  mirror はその記録と Minecraft 自身の announce から埋まる。NULL data の allocation は zero-filled surface として
  記録する（game は atlas を sprite 単位で patch するため）。内容の無い mirror は uninitialized memory で埋めず拒否する。
- **terrain pass の前の順序 flush**（`VulkanTerrainGpuBackend.render`）: game が pass より前に描いた sky・void plane・
  sun/moon は即時 work として queue に残る。GL の順序は call 順なので、pass の前に `flushPendingBeforeGlDraw()` する。
- **提示画像の主張**（validation fixture）: GL-less のときだけ、dump した提示 PNG の distinct colours・
  non-background fraction・vertical bands と frame 数を測り、絵でなければ `PASSED` にしない。dump 要求は one-shot。

## 各計測が示したもの

| run | 何を測ったか | 結果（run 内の実ファイル） |
|---|---|---|
| `p546-glless` | owned target 前夜 | 提示 PNG は 100 % 黒。`Transfer Queue: vulkan ... regions=0 draws=0 sections=0 uploaded=0B`・`terrainPasses=0`・`lastDraw=0`（depth が未定義で depth-tested draw が成立しない状態） |
| `p547-glless` | clear が owned image に届くかの lab 診断 | 提示 PNG は 100 % `#3366CC`（`-Dceltium.vulkan.diagnosticClear=true`。**この flag は HEAD に無い**一回限りの診断） |
| `p548-glless` | 1 frame 1 提示・GL swap skip | `CELTIUM_GROUPS`（`logs/debug.log` 21:51:13）: `presentedFromVulkan=287`＝`glSwapsSkipped=287`・`presentFailures=0`・`presentAttempts=287`（最終行は 288/288）。提示 PNG は `#FFFF00` 62.5 %＋黒 37.5 % |
| `p549-glless` | float 修正後の clear 色・lightmap・terrain pass | 提示 PNG は `#B3CFFF` 62.5 %＋黒 37.5 %。`lightmapSuppliedOnDemand=1`・`lightmapSupplyFailure=[none]`。`logs/celtium-validation.json` の `rendererDebug` に `block atlas: gl texture 12 512x512`・`lightmap: gl texture 15 16x16`・`pipeline for solid created [...]`・`firstDraw region=8 pass=solid stride=32 ... elements=16896 ...`。`immediateRecordedBatches=7403`＝`submitted`・`completed=7402`・`invalidImmediateRecords=0`・`celtium/vulkan-check passes=1759 skipped=0 errors=0`・`readbackByOrigin=none` |
| `p550-glless` | 同修正の再現 | 提示 PNG `#B3CFFF` 62.5 %。`lightmapSuppliedOnDemand=1`・`pipeline for solid created`・`firstDraw region=8 pass=solid ... baseVertex=21504 elements=16896`。`worldFrames=191` |
| `p551-glless` | frame dump の開始 | `-Dceltium.vulkan.frameDumpEvery=45` で `screenshots/frame-45/90/135/180/225/270.png`。`presentedFromVulkan=275`＝`glSwapsSkipped=275`。提示画像の主張はまだ無い（`presentedImageAssertions` キーが無い） |
| `isoB` / `isoC` | 絵の帰属（切り分け） | `isoB` = `-Dceltium.vulkan.refuseTerrainPass=true`: frame は 99.8 % が空色 `#B2CFFF` で黒帯なし。`isoC` = `-Dceltium.vulkan.refuseImmediateFormat=6`: frame は通常の GL-less frame と同じ空色＋全幅の黒帯（rows 366-635） |
| `p553-glless` | terrain の断片が出始め | 提示 PNG `#B3CFFF` 61.3 %・distinct 67。frame dump は distinct 1224 |
| `p554`/`p555-glless` | 提示画像の主張を追加 | どちらも `fixtureStatus=FAILED_PRESENTED_IMAGE`。`p555` の patch 拒否メッセージは `outside the mirror 32x32` に変わった |
| `p556-glless` | null-data allocation の修正 | `CELTIUM_PATCH_REJECTED` が 0 件。`fixtureStatus=FAILED` |
| `p557-glless` | capture 不成立 | `fixtureStatus=NO_CAPTURE`・timeout（`exitCode=143`・`validationExitCode=124`）。提示 PNG は `#90A7CE` 100 % |
| `p558`/`p559-glless` | 提示画像の主張（数値つき） | `presentedDistinctColours=2`・`presentedNonBackgroundFraction=0.0`・`presentedVerticalBands=17`（`p558`）/`15`（`p559`）・`presentedBackgroundColour=0x96ADD6`（`p558`）/`0x9DB5E0`（`p559`）・`fixtureStatus=FAILED_PRESENTED_IMAGE`。frame dump は frames 60/120/180 |
| `reg-bridge3` | GL-bridge 既定経路の対照 | fixture `PASSED`・`validationClean=true`・`validationMessageCount=0`・`validationExitCode=0`・`worldFrames=142`・`gpuRequirementMet=true`・`gameFramebufferIsShared=true`・`gameFramebufferAdopted=1` |

GL-less run に共通の計器（すべて `logs/debug.log` の `CELTIUM_GROUPS` と `logs/celtium-validation.json` の
`rendererDebug`）:

- `gameFramebufferIsShared=false`・`gameFramebufferAdopted=0`・`glless target: no handoff, abandoned=0
  lastAbandon=[none] offTarget=none offTargetCreations=0 offTargetClears=0 offTargetSelfTests=0` —— フレームに
  shared image も GL bridge も入っていない（`reg-bridge3` は `gameFramebufferIsShared=true`・`adopted=1`）。
- `logs/celtium-capabilities.json`: `glRenderer="Celtium stub GL"`・`glVendor="Celtium"`・`deviceUuids=[]`・
  `memoryObjectFd=false`。`logs/celtium-vulkan-device.json`: `status=UNSUPPORTED_OR_UNAVAILABLE`・
  `logicalDeviceCreated=false`・`reasons=["The current OpenGL context does not expose device UUIDs
  (GL_EXT_memory_object / GL_EXT_memory_object_fd missing)."]`。bridge の前提（GL device UUID・external
  memory/semaphore FD）はこの経路には無い。
- `logs/celtium-capabilities.json` の `vulkanDevices[0].name` は `AMD Radeon RX 6400 (RADV NAVI24)` で、
  描画はこの device 上で起きている。
- validation: `validationMessageCount=0`・`validationClean=true`（`p548`〜`p559` の全 run）。
- 提示: `rendererDebug` の `swapchain: platform=x11 format=44 colourSpace=0 presentMode=1 images=4 size=1280x720`、
  `CELTIUM_GROUPS` の `presentResult=[present image: blitted 1280x720 onto image N (0)]`。
- post chain はこの fixture では発火していない（`postChains=0`・`postPasses=0`）。

## 計測で見つけた欠陥と、その見つけ方

### 1. 8 pointer の CIF が float を general-purpose レジスタから読む（`CeltiumGlStub`）

- 欠陥: 旧実装は 8 個の pointer を並べた**単一の CIF** を全関数に使い、upcall も `long` 8 引数だった。x86-64 の
  呼び出し側は float/double を SSE レジスタで渡すため、CIF が pointer を宣言していると値は general-purpose
  レジスタから読まれ、そこに残っていた任意の値が届く。コードのコメントに残る計測は
  `glClearColor(0.25, 0.5, 0.75, 1)` が `-1.6e29` として届いた、というものである。
- 見つけ方: 提示画像が game の clear 色にならない。`p548-glless/screenshots/celtium-presented.png` は
  `#FFFF00` 62.5 %＋黒 37.5 %（readback の `presentedMean=170.0` は一様な黄の平均と一致）。
- 修正: 宣言済み引数型から CIF を作り（`SIGNATURE_DESCRIPTORS`）、float 4 引数用 `FLOAT4_DESCRIPTOR`・double 1 引数用
  `DOUBLE1_DESCRIPTOR` を持つ。plain upcall は `SLOTS=12` に widen し、`glTexImage2D` の pixels pointer が届く。
- 修正後の計測: `p549`/`p550-glless` の提示 PNG は `#B3CFFF` 62.5 %＋黒 37.5 %。`#B3CFFF` は game が
  `glClearColor` に渡す fog/sky 色である（`EntityRenderer.updateFogColor` の最後の
  `GlStateManager.clearColor(this.fogColorRed, this.fogColorGreen, this.fogColorBlue, 0.0F)`）。

### 2. lightmap の unit を `33984 + 33985` として問い合わせる（`VulkanTerrainGpuBackend.ensureLightmapMirror`）

- 欠陥: mod 側は `OpenGlHelper.lightmapTexUnit`（= 33985、GL enum の `GL_TEXTURE1`）を渡す。旧コードは
  `int wanted = 33984 + this.lightmapUnit;` だったので、enum に `GL_TEXTURE0` を足した値（67969）を
  `glActiveTexture` に渡していた。light map の id を学べず、`lightmapTexture` が null のままなので terrain pass が
  開かない。
- 見つけ方: refusal 集計が理由を名指しした。`p541-glless`〜`p547-glless` の
  `CELTIUM_REFUSALS_BY_FORMAT` に `5 lightmapped batch with no lightmap texture mirrored (id=-1 unit=33985)=N`
  が出る（この refusal メッセージ自体が本 commit で「どちらの条件で落ちたか」を名指しするために足された）。
- 修正: `int wanted = this.lightmapUnit >= 0x84C0 ? this.lightmapUnit : 33984 + this.lightmapUnit;`
  （mod 側の値が既に enum ならそのまま使う）。
- 修正後の計測: `lightmapSuppliedOnDemand=1`・`lightmapSupplyFailure=[none]`（`p548`〜`p559`）・
  `lightmap: gl texture 15 16x16`。terrain pass が開く（`pipeline for solid created`）。

### 3. sky と void plane が terrain pass の後に submit される（`VulkanTerrainGpuBackend.render`）

- 欠陥: terrain pass より前に game が描いた sky・void plane・sun/moon は即時 group として queue に残る。GL の順序は
  call 順なので、pass の後に submit すると順序が逆転し、void plane が terrain の上に出る。
- 見つけ方: 切り分け flag で帰属を測った。`isoB`（`-Dceltium.vulkan.refuseTerrainPass=true`）の frame は
  99.8 % が空色 `#B2CFFF` で黒帯が無い —— 即時経路だけで描く空は正しい。`isoC`
  （`-Dceltium.vulkan.refuseImmediateFormat=6`）の frame は通常の GL-less frame と同じ空色＋全幅の黒帯
  （rows 366-635）で、band は format 6 の sky だけでは説明できない。通常の GL-less frame（`p551`/`p553`/`p559`）
  は空色の上に wedge と全幅の黒帯を出す。
- 修正: `render()` の先頭（pass の有無を確認した後）で `flushPendingBeforeGlDraw()`。
- 結果: **順序は直ったが絵は直っていない**。`p558`〜`p560` は依然 `FAILED_PRESENTED_IMAGE` で、この修正単独では
  窓の絵は閉じない。

### 4. NULL data の allocation に surface を作らず、atlas の patch が落ちる（`CeltiumGlStub.undefinedLevel`）

- 欠陥: `TextureUtil` は atlas の各 mip level を **NULL data で allocate** し、そのあと sprite 単位で
  `glTexSubImage2D` を呼んで埋める。旧実装は NULL data の allocation を記録しなかったので、最初の patch が
  単一 sprite の大きさの capture を作り、後の patch はその外へ落ちた（コードのコメント: 「the first became a
  capture the size of a single sprite and every later one fell outside it, which left the block atlas mirrored as
  one 16x16 corner」）。
- 見つけ方: patch 拒否のメッセージが変わった。`p548`〜`p554-glless` は各 8 件の
  `CELTIUM_PATCH_REJECTED partial patch 16x16 at 32,80 before the image for GL texture 12 was initialized`、
  `p555-glless` は各 8 件の `CELTIUM_PATCH_REJECTED region 16x16 at 32,80 outside the mirror 32x32`
  （= 最初の patch が 32x32 の mirror を作り、以降が外へ落ちている形そのもの）。
- 修正: NULL data の level は extent どおり zero-filled surface として記録する（`undefinedLevel`）。undefined は
  game の byte ではないので full image とは別に数える。
- 修正後の計測: `p556-glless`〜`p560-glless` は `CELTIUM_PATCH_REJECTED` が 0 件。

### 5. frame dump の要求が tick 一致で、counter が飛ばすと発火しない（validation fixture）

- 欠陥: dump 要求が `worldTicks == dumpPresentedTick`（既定 198）だった。tick counter がその値を踏み越えると
  要求は永久に発火しない（同じ exact-tick 条件が Intel run で 150 を踏み越えて scene ごと起きなかった実測が
  コメントに残っている）。加えて capture は trigger 駆動なので、capture tick を前提にした固定 tick は遅すぎるか
  来ないかのどちらかになる。
- 修正: one-shot flag つきで `worldTicks >= dumpPresentedTick`（既定 20）にした。早すぎる方向は安全側で、
  `presentedImagePngAgeMs` がどれだけ早かったかを報告する。
- 修正後の計測: `p558`/`p559-glless` は dump が書かれ（`presentedImageDumped=true`）、
  その PNG を提示画像の主張が読む。`p559` の `presentedImagePngAgeMs=8566`。

## 未解決（受入ではない）

- **窓の絵**: `p559-glless` の提出値は `presentedDistinctColours=2`・`presentedNonBackgroundFraction=0.0`・
  `presentedVerticalBands=15`・`presentedBackgroundColour=0x9DB5E0`・`presentedImageAssertions=FAILED:
  presentedDistinctColours=2 is not above the floor of 16 (presentedNonBackgroundFraction=0.0,
  presentedVerticalBands=15): the window is a clear colour plus at most a quad or two, not a frame`。
  `p558-glless` も同様（distinct=2・bands=17・bg `0x96ADD6`）。
- **capture 時点の絵**: `presentedImageMeanAtCapture=213.6666717529297` は sky 色 `#B3CFFF` の RGB 平均
  `(179+207+255)/3` と一致する —— capture 時の提示画像の平均は、一様な空色フレームの平均そのものである。
- **frame dump が示す中身**: `p559-glless/screenshots/frame-120.png` は空色の上に大きな灰色の wedge が数枚、
  そして **rows 366-635 の全幅の黒帯**。地形は連続した面ではなく断片である。同じ world の GL-bridge 参照
  `p530-tp/screenshots/celtium-validation.png` は連続した checkerboard の地形面で、chat overlay つき。
  commit message の表現では「terrain は fragments と wedges として描かれ、Minecraft の void plane が全幅の帯として
  透ける」。
- **証拠の限界（隠さない）**: 提示 PNG は tick 20 で要求した one-shot の dump なので capture 時点のフレームではない
  （`p559` の `presentedImagePngAgeMs=8566`）。その色統計（distinct=2 等）は**その早期フレーム**のものである。
  capture 時の「絵になっていない」ことは、上記の mean（一様 sky 色の平均と一致）と owned target の frame dump が
  示す。fixture のコードコメントには「two ticks before the capture」という古い記述が残っており、tick 20 へ
  変えた経緯と食い違っている。
- **GL-less は flag**: 既定は GL-bridge のまま（`-Dceltium.vulkan.glless=true` で切替）。切り分け flag
  （`celtium.vulkan.refuseTerrainPass`・`celtium.vulkan.refuseImmediateFormat`）も lab 用。
- **harness の verdict**: GL-less run は `validationExitCode=1`（`--require-gpu` が stub GL renderer では確認できない。
  `p557` のみ timeout で 124）。p548〜p551 は fixture `PASSED`・clean でも harness は 1 を返す。
- **未検証のまま残るもの**: この fixture では post chain が発火していない（`postChains=0`）ので seeded post chain の
  GL readback 経路（(c)）は GL-less で未検証。Intel GPU での GL-less は未計測（引用 run はすべて AMD）。
  `reg-bridge3` は GL-bridge 経路の対照であって、GL-less の受入ではない。

## 再現コマンド

```bash
export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"
NOLAVA='3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:leaves,minecraft:stained_glass;1;'

timeout 900 python3 scripts/prism_validation.py \
  /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output .celtium-work/glless-repro-lock.json --game-dir .celtium-work/runtime/glless-repro \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.vulkan.glless=true \
  --jvm-arg=-Dceltium.vulkan.validation=true \
  --jvm-arg=-Dceltium.vulkan.presentReadback=true \
  --jvm-arg=-Dceltium.validation.dumpPresentedImage=true \
  --run --validate --duration 120 --headless --force-x11 \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
```

- 4 つの `--jvm-arg` が GL-less 実行そのもの（`glless`・`validation`・`presentReadback`・`dumpPresentedImage`）。
  `prism_validation.py` は `--validate` のとき `-Dceltium.validation=true` を自分で足す。
- frame dump も見るなら `--jvm-arg=-Dceltium.vulkan.frameDumpEvery=60` と
  `--jvm-arg=-Dceltium.vulkan.frameDumpDir=$PWD/.celtium-work/runtime/glless-repro/screenshots` を足す
  （`p551` は 45、`p558`/`p559` は 60）。
- 判定は `.celtium-work/runtime/glless-repro/validation-process.json`（`fixtureStatus`・`validationClean`・
  `validationMessageCount`・`validationExitCode`・`gpuRequirementMet`）と
  `logs/celtium-validation.json`（`presented*` キー・`rendererDebug`）、`logs/debug.log`（`CELTIUM_GROUPS`・
  `CELTIUM_GLLESS`・`CELTIUM_PATCH_REJECTED`）で読む。**`exitCode=0` を PASSED と読まない**。

## 参照

- 計画・受入: `docs/plan.md` §0.114（この milestone の現在状態）と §1 の条件 (b) 行、§5 の item 6 段落。
- コード: `celtium-vulkan/.../GllessFrameTarget.java`・`FrameImageTarget.java`・`gl/CeltiumGlStub.java`・
  `VulkanGlTexture.java`・`VulkanTextureRegistry.java`・`VulkanTerrainGpuBackend.java`、
  `validation/client/.../ValidationClient.java`（提示画像の主張）。
- run: `.celtium-work/runtime/p546-glless`〜`p560-glless`・`isoB`・`isoC`・`reg-bridge3`・`p530-tp`。
  本 commit のあとに走った `p561-glless` は verdict が無い（未完了）ので引用しない。
- 実行手順の一般形: `docs/handoff-2026-10-10-next-model.md` §2.2（この節の `dumpPresentedTick` の既定値 198 は
  `4bcff8de7` で 20 に変わっている）。
