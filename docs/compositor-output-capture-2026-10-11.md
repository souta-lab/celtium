# 実 window / compositor 出力への到達 — 条件 (c) の実測（2026-10-11）

**結論（verdict）: 条件 (c) は充足（satisfied）— 「native swapchain へ出した完成 frame が実際の window/compositor 出力に到達する」ことを、
marker による帰属と、番号付き presented dump との画素完全一致で実測した。**
残る唯一の未測定は **scanout（CRTC が物理モニタへ走査出力する段）そのもの**で、これはこの環境の権限では *いかなる方法でも* 測れない
（後述 §7）。grim が読むのは headless 出力でも実 KMS 出力でも「compositor が合成した出力バッファ」であり、その段への到達は両者で同じ強さで証明される。

この文書は `scripts/compositor_capture.py` の docstring が引用していた文書であり、同スクリプトは本計測で初めて実行された（§1）。

---

## 0. 要約（1 分で読む場合）

| 問い | 実測 | 証拠 |
|---|---|---|
| 提示 frame は window に届いているか | **届いている** | `-Dceltium.vulkan.presentMarker=true` の magenta (255,0,255) が compositor 出力 1280×720 全面に出る。window が map されている 14 capture すべてが magenta、map 前後の 32 capture は黒 | `marker-compositor-0035-magenta.png` |
| その magenta は「この renderer の present」由来か | **由来する（他に書き手が無い）** | 私用 nested sway に client は 1 つだけ（`Cleanroom`/xwayland/ゲームの pid）、背景は黒。magenta を書ける経路は present の clear のみ | `marker-captures.json` の `windows` |
| 特定の frame に束縛できるか | **できる（画素完全一致）** | compositor の capture 0035 は renderer の番号付き presented dump `frame-20.png` と **0/921600 画素差・最大チャネル差 0**（`frame-40` とも一致）。時刻も frame-20 の書出し 08:58:25.631 が capture 0034(08:58:24.867) と 0035(08:58:25.867) に挟まれる | `marker-compositor-0035-magenta.png` ↔ `marker-presented-frame-20.png` |
| 完成 frame（実 scene）も届くか | **届く** | 通常 run の capture 0066 は番号付き dump `frame-100.png` と **0/921600 画素差で完全一致**（28099/10104 色の実 Minecraft frame） | `scene-compositor-0066.png` ↔ `scene-presented-frame-100.png` |
| fixture の独立計測と一致するか | **一致する** | marker run で fixture 自身が presented 画像を `presentedImageMeanAtCapture=170.0`・「921600 画素が単一色」と計測。grim 側は uniform (255,0,255)＝RGB 平均 170 | `logs/celtium-validation.json` |
| GL framebuffer は証拠になるか | **ならない（想定どおり）** | GL screenshot `celtium-validation.png` は黒（2759 B）で、presented 画像と 99.925% の画素が異なる | `scene-gl-screenshot-black-control.png` |
| scanout は測れたか | **測れていない（方法が存在しない）** | `ffmpeg -f kmsgrab` は `Failed to open DRM device`（DRM master 無し・非 root）。§7 | — |

---

## 1. 前提の訂正と、残されていたスクリプトの状態

### 1.1 「`DISPLAY=:0` の root は 0×0 で compositor 無し」は誤り

plan.md が長く持っていたこの記述は、**いま実測すると成立しない**。

```
$ ps -eo pid,user,comm,args | grep -E 'sway|Xwayland'
    935 s0u7a sway      /usr/local/bin/sway
   1013 s0u7a Xwayland  Xwayland :0 -rootless -core -terminate 10 -listenfd 46 ... -wm 99

$ export SWAYSOCK=/run/user/1000/sway-ipc.1000.935.sock
$ swaymsg -t get_version
{"human_readable": "0.6-fd71a6bd (Sep  5 2026, branch 'HEAD')", "sway_original_version": "1.12.0", ...}

$ swaymsg -t get_outputs -r | jq -c '.[] | {name, rect, modes: .modes[0], make, model}'
{"name":"HDMI-A-4","rect":{"x":0,"y":0,"width":1920,"height":1080},
 "modes":{"width":1920,"height":1080,"refresh":60000},"make":"HOYA Corporation PENTAX Lifecare Division","model":"PTFWDE-22W"}

$ ffmpeg -hide_banner -f x11grab -i :0.0 -t 0.3 -f null -
Input #0, x11grab, from ':0.0':
  Stream #0:0: Video: rawvideo (BGR[0] / 0x524742), bgr0, 1920x1080, 29.97 fps
```

つまり `DISPLAY=:0` は **1920×1080 の実 X 画面**（swayfx セッションの Xwayland、`XDG_RUNTIME_DIR=/run/user/1000`,
`wayland-1`）で、compositor は **実モニタ出力 HDMI-A-4 1920×1080@60 を持ち DRM master を握っている**
（`/proc/935/fd` に `card1`/`card2`/`renderD128`/`renderD129`）。`xdpyinfo`/`xrandr` は未導入なので、
X 画面の大きさは `x11grab` で測った。0×0 という以前の観測は、nested harness の Xwayland（`:1`/`:2`）か、
output が構成される前の一時点のものと見られる。

### 1.2 `scripts/compositor_capture.py` — 残されていたが一度も走っていなかった

227 行。`prism_validation.py --headless` を**そのまま**起動し、`<game-dir>/headless/display.json` の出現を待って
（起動前に消すので「新しい compositor の告知」だけを拾う）、launcher が終わるまで
`grim -o HEADLESS-1 compositor-NNNN.png` を `--interval` ごとに回し、capture ごとに
「シャッターの wall/monotonic 時刻・grim の exit/stderr・PNG の sha256・ゲームの PID・sway の window tree・output の mode」
を JSON に残し、最後に `captures.json` にまとめる。docstring の主張（`--headless` が実 GPU の wlroots/Sway を所有する）は
`scripts/headless_wayland.py` を読んで**正しいと確認**した（`WLR_BACKENDS=headless`, `WLR_HEADLESS_OUTPUTS=1`,
`WLR_RENDERER=gles2`, `WLR_RENDER_DRM_DEVICE=<render node>`, output `HEADLESS-1` 1280×720, Xwayland 併設）。

**しかし実行痕跡は無かった**: ツリー全体に `compositor-*.png` も `captures.json` も無く、
docstring が引用する `docs/compositor-output-capture-2026-10-11.md`（本文書）も存在しなかった。
本計測でこのスクリプトを初めて実走させ、その出力を以下に使う。計測用に
`scripts/compositor_capture_report.py` を追加した（capture の一様性・色数・magenta 率と、
番号付き frame dump との画素比較を出す）。

---

## 2. 環境の棚卸し（存在するもの／しないもの）

```
$ for t in Xvfb weston sway swaymsg cage labwc river wayfire gamescope xwd import convert magick \
           ffmpeg wf-recorder grim grimshot slurp drm_info modetest xdpyinfo xrandr xwininfo xdotool; do
    printf '%-14s %s\n' "$t" "$(command -v $t || echo MISSING)"; done
Xvfb           MISSING        weston         MISSING        cage           MISSING
labwc          MISSING        river          MISSING        wayfire        MISSING
gamescope      MISSING        xwd            MISSING        import         MISSING
convert        MISSING        magick         MISSING        wf-recorder    MISSING
grimshot       MISSING        drm_info       MISSING        modetest       MISSING
xdpyinfo       MISSING        xrandr         MISSING        xwininfo       MISSING
xdotool        MISSING        sway           /usr/local/bin/sway
swaymsg        /usr/local/bin/swaymsg     Xwayland   /usr/bin/Xwayland
grim           /usr/bin/grim  slurp          /usr/bin/slurp  ffmpeg  /usr/bin/ffmpeg
swaybg         /usr/bin/swaybg            dbus-run-session /usr/bin/dbus-run-session
```

* **X server / Wayland compositor**: 実セッションの sway(935) が `wayland-1` で稼働（上記）。X ソケットは
  `/tmp/.X11-unix/X0`（実セッションの Xwayland）と `X1`（harness 稼働中のみ）。
  harness の `--headless` は**別の**私用 sway を `/tmp/celtium-hl-*/` に立てる（`DISPLAY=:1` か `:2`、`SWAYSOCK=/tmp/celtium-hl-*/sway-ipc.*.sock`）。
* **DRM**: `/dev/dri/card1`（00:02.0 Intel）, `card2`（03:00.0 AMD）, `renderD128`, `renderD129`。
  権限は `root:video` / `root:render` + ACL。ユーザーは `video` グループ（`render` ではない）が、ACL で読めており
  実際に grim もゲームも動く。`drm_info`/`modetest` は未導入。
* **捕獲手段**: `grim`（wlroots screencopy）が**両方**で成功する。
  * 私用 headless 出力: `grim -o HEADLESS-1` → 1280×720 PNG（他エージェントの実走行中ゲーム窓、6781 色の地形フレーム）。
  * **実 KMS 出力**: `XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-1 grim -o HDMI-A-4 out.png`
    → exit 0・1920×1080・9675 色（`desktop-real-output-HDMI-A-4.png`）。**headless backend 固有の話ではない。**
* **scanout/光子レベルの捕獲**: `ffmpeg -f kmsgrab -i -` は
  `[in#0 @ ...] Failed to open DRM device.` で失敗（DRM master 無し・非 root）。`wf-recorder` は無いが、
  wf-recorder も内部は screencopy なので grim に対する優位は無い。

---

## 3. 方法: capture を「特定の presented frame」に束縛する

使った機構はすべて既存で、**renderer には一切手を入れていない**。

1. **帰属（attribution）**: `-Dceltium.vulkan.presentMarker=true`（`celtium-vulkan/.../VulkanSwapchain.java:85,704-720`）。
   1:1 blit の代わりに swapchain image 全体を `vkCmdClearColorImage` で magenta (1,0,1,1) にする。
   同コードのコメントどおり「marker が出ている画面はこの経路しか書けない」。
2. **番号付け（numbering）**: `-Dceltium.vulkan.frameDumpDir=<abs> -Dceltium.vulkan.frameDumpEvery=N
   -Dceltium.vulkan.frameDumpCount=M` + `-Dceltium.vulkan.presentReadback=true`。
   renderer が present した画像を、**自分自身の presented-frame カウンタ**を名前にして `frame-<N>.png` に書き出す
   （`VulkanTerrainGpuBackend.java:6349,6373-6394`）。
3. **compositor 側の番号付け**: `compositor_capture.py` の `compositor-NNNN.png` ＋ 各 capture の
   シャッター時刻・window tree・output mode（`captures.json`）。
4. **束縛の規則**: 「capture のシャッター時刻が、ある `frame-N.png` の書出し時刻を跨ぐ」ことを時間側の条件とし、
   **画素が完全一致すること**を内容側の条件とする。両方を満たす組だけを束縛として報告する
   （近いだけ・似ているだけの組は束縛と呼ばない）。
5. **独立した第 3 の計測**: fixture は presented 画像を自前で読み戻し、`screenshots/<...>.png` と
   その identity sidecar（`presentedFrame`, `worldTick`, `imageHash`, `frameBinding`）を書く。
   marker run ではさらに `presentedImageMeanAtCapture` を報告する。

---

## 4. Run M（marker run）— compositor 出力の帰属

実行（`VK_LAYER_PATH`/`LD_LIBRARY_PATH` は `.celtium-work/gpu-tools/extracted/...` の検証 layer。**必須**、§6.2）:

```
$ export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
$ export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"
$ python3 scripts/compositor_capture.py \
    --game-dir .celtium-work/runtime/compcap-marker2 \
    --capture-dir .celtium-work/compositor-capture/marker2 --interval 1.0 --startup-timeout 180 \
    -- /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
       --output .celtium-work/compcap-marker2-lock.json \
       --game-dir .celtium-work/runtime/compcap-marker2 \
       --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
       --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
       --library celtium-interop/build/install/celtium-interop/lib/lwjgl-vulkan-3.4.1.jar \
       --jvm-arg=-Dceltium.vulkan.glless=true --jvm-arg=-Dceltium.vulkan.validation=true \
       --jvm-arg=-Dceltium.vulkan.presentReadback=true --jvm-arg=-Dceltium.vulkan.presentMarker=true \
       --jvm-arg=-Dceltium.vulkan.frameDumpDir=$PWD/.celtium-work/compositor-capture/marker2/frames \
       --jvm-arg=-Dceltium.vulkan.frameDumpEvery=20 --jvm-arg=-Dceltium.vulkan.frameDumpCount=80 \
       --jvm-arg=-Dceltium.validation.dumpPresentedImage=true --jvm-arg=-Dceltium.validation.minWorldFrames=5 \
       --run --validate --duration 90 --headless --force-x11 \
       --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
```

結果（compositor が所有する出力の実測）:

```
compositor ready: WAYLAND_DISPLAY=wayland-1 SWAYSOCK=/tmp/celtium-hl-464u7hd1/sway-ipc... DISPLAY=:2 output=HEADLESS-1
[08:57:50.855] capture 0000 grim=0 bytes=2759 windows=0     <- window 未 map: 出力は一様な黒
...
[08:58:20.866] capture 0030 grim=0 bytes=2759 windows=0
[08:58:21.866] capture 0031 grim=0 bytes=4316 windows=1     <- window が map した瞬間から magenta
...
[08:58:34.873] capture 0044 grim=0 bytes=4316 windows=1
[08:58:35.873] capture 0045 grim=0 bytes=2759 windows=0     <- window が消えると黒へ戻る
{"launcherExitCode": 1, "captures": 46, "output": "HEADLESS-1", ...}
```

計測（`scripts/compositor_capture_report.py`）:

```
captures=46  capturesWithMappedWindows=14  capturesWithoutMappedWindows=32
capturesWithStructure=0        <- 構造を持つ画像は 1 枚も無い
capturesUniformlyMagenta=14
firstMappedCaptureWall=08:58:21.866588629  firstMagentaCaptureWall=08:58:21.866588629   <- 完全に同時
distinctImageHashes=2          <- 黒と magenta の 2 種類だけ
```

| capture | sha256 (PNG) | 画素 | window tree |
|---|---|---|---|
| 0000–0030, 0045 | `7efdbbfe…`（黒） | 一様 (0,0,0) 1280×720 | `windows=0` |
| 0031–0044 | `45717d93e4571a6d79c96574d0a8a2aa7b1da82e886e2e63c1e33abe835c9257` | 一様 (255,0,255) 1280×720 | 下記 1 窓 |

capture 0035 の window tree（= この出力に存在する唯一の client）:

```json
[{"id":5,"type":"con","name":"Cleanroom","app_id":null,"class":"Cleanroom","instance":"Cleanroom",
  "title":"Cleanroom","pid":1716095,"rect":{"x":0,"y":0,"width":1280,"height":720},
  "visible":true,"focused":true,"fullscreen_mode":0,"shell":"xwayland"}]
```

output mode も `{"name":"HEADLESS-1","current_mode":{"width":1280,"height":720,"refresh":60000},"scale":1.0}`。
`pid 1716095` はゲームの java プロセス、`shell: xwayland` は `--force-x11` で X11 窓として出ていることを示す。

### 4.1 特定 frame への束縛（画素完全一致）

renderer 側の番号付き presented dump（`frames/`）: `frame-20/40/60/80.png`（各 5305 B）は
いずれも**一様 (255,0,255)**（per-channel min=max）。時間関係:

```
frame-20.png 書出し 08:58:25.631327  -> 直前 capture 0034 (08:58:24.866848, 4316B) / 直後 capture 0035 (08:58:25.866948, 4316B)
frame-40.png 書出し 08:58:25.923326  -> 直前 capture 0035 (08:58:25.866948, 4316B) / 直後 capture 0036
frame-60.png 書出し 08:58:32.271314  -> 直前 capture 0041 / 直後 capture 0042
frame-80.png 書出し 08:58:32.687314  -> 直前 capture 0041 / 直後 capture 0042
```

画素比較（完全一致のみを束縛と呼ぶ）:

```
capture 0035 vs frame-20.png : differentPixels=0/921600  maxChannelDifference=0  identical=True
capture 0035 vs frame-40.png : differentPixels=0/921600  maxChannelDifference=0  identical=True
```

つまり **compositor 出力の capture 0035 は、renderer が presented frame 20 として swapchain へ渡した画像そのもの**
（ファイルの sha256 は PNG エンコーダが違うため異なる — grim と ImageIO — が、画素は 1 bit も違わない）。

renderer 側の同時刻の計器（`validation-console.log`）:

```
presentSucceeded=true presentAttempts=81 presentFailures=0 presentedFromVulkan=81 glSwapsSkipped=81
presentResult=[present image: blitted 1280x720 onto image 2 (0)]
```

fixture（第 3 の独立計測）:

```
pictureIdentity_world = {"source":"presented-dump","presentedFrame":82,"worldTick":204,"width":1280,"height":720,
                         "imageHash":-1013903329,"frameBinding":"observed present: the renderer's presented-frame counter advanced by exactly one from the request"}
presentedImageMeanAtCapture = 170.0
presentedImageAssertions = FAILED: the picture is a single colour for scene=terrain stage=world
                           frame=[source=presented-dump extent=1280x720 worldTick=204 presentedFrame=82 ...]:
                           presentedDistinctColours=1 over 921600 pixels
status = FAILED_PRESENTED_IMAGE
```

fixture の `FAILED` は**この run では期待された結果**である: presented 画像を意図的に magenta 単色へ置き換えているので、
scene の床（色数・非背景率）を満たすはずがなく、fixture 自身が「921600 画素が単一色（平均 170.0）」と正しく計測している。
**この 170.0（fixture の swapchain readback）と、grim 側の一様 (255,0,255)（RGB 平均 170）が一致すること**が、
「swapchain に渡した画像」と「compositor 出力」が同一であることの三角測量になっている。
run 自体は `exitCode 0 / endedBy process-exit / elapsed 45.6s / validationClean true / msgs 0 /
nativeDeviceName "AMD Radeon RX 6400 (RADV NAVI24)" / gpuRequirementMet true`。

---

## 5. Run S（通常 run）— 完成 frame が compositor 出力へ届く

同じ構成から `presentMarker` だけを外し（`allBlocks=true` は直近の合格構成 `regr4-glless` に合わせた）、
`--interval 0.5` で 69 capture を取った。

```
$ (上と同じ。--jvm-arg=-Dceltium.vulkan.presentMarker=true を外し、
   --jvm-arg=-Dceltium.validation.allBlocks=true を追加。--capture-dir .../compositor-capture/scene)
[09:00:35.729] capture 0045 grim=0 bytes=2759   windows=1   <- 窓が map、まだ黒（world 前）
[09:00:36.229] capture 0046 grim=0 bytes=109310 windows=1   <- 構造が出はじめる
...
[09:00:46.232] capture 0066 grim=0 bytes=172778 windows=1
...
{"launcherExitCode": 0, "captures": 69, ...}
```

run の verdict（fixture が自分の絵を判定して PASSED）:

```
exitCode=0  endedBy=process-exit  elapsedSeconds=34.6  fixtureStatus=PASSED
validationClean=true  validationMessageCount=0  nativeDeviceName="AMD Radeon RX 6400 (RADV NAVI24)"  gpuRequirementMet=true
pictureEvidenceSource=presented-dump   presentedImageVerdict=PASSED
presentedImageAssertions=PASSED: every picture is the frame its identity names, and the scene's gate held
presentedImageFramesAtCapture=101  presentedImageMeanAtCapture=44.0
```

identity sidecar（fixture が「どの present がこの絵を書いたか」を観測して書く）:

```json
{"stage":"world","source":"presented-dump","presentedFrame":102,"worldTick":204,"worldFrames":102,
 "width":1280,"height":720,"imageHash":-2054013068,"writtenAtMillis":1791676846469,
 "frameBinding":"observed present: the renderer's presented-frame counter advanced by exactly one from the request"}
```

### 5.1 束縛（画素完全一致）

```
frame-100.png 書出し 09:00:45.923028 -> 直前 capture 0065 (09:00:45.732483, 172701B) / 直後 capture 0066 (09:00:46.232580, 172778B)

capture 0066 vs frame-100.png : differentPixels=0/921600  maxChannelDifference=0  meanAbsDiff=0.0000  identical=True
```

**compositor 出力の capture 0066 は、presented frame 100 として swapchain に渡された完成 frame（10104 色の実 scene）と
1 画素も違わない。** 方法が識別力を持つことの確認として、**時刻が跨る組のうち画素まで一致するのはこの組だけ**で、
他の組は 0.059 %〜20.01 % 離れている（`frame-20`↔0055/0056 = 各 2.31 %、`frame-40`↔0056 = 2.31 %・↔0057 = 20.01 %、
`frame-60`↔0062/0063 = 各 0.065 %、`frame-80`↔0063 = 0.065 %・↔0064 = 0.985 %、`frame-100`↔0065 = 0.059 %）。
capture と dump は別の瞬間なので、静止していない scene では近くても一致はしない ——
「似ている」を束縛と呼ばないために、完全一致した 1 組だけを束縛として報告する。

fixture の presented 画像（presentedFrame=102）との比較:

```
capture 0066 vs fixture presented-world (frame 102) : differentPixels=957/921600  (0.1038%)  maxChannelDifference=255  meanAbsDiff=0.0117
capture 0067 vs fixture presented-world (frame 102) : differentPixels=1288/921600 (0.1398%)  maxAbsDiff=255            meanAbsDiff=0.0141
```

frame 100 と 102 の間に粒子・アニメーションぶんの差（0.1%）が出るのは想定どおりで、
「compositor が持っている絵 = fixture が identity を付けた presented frame」が同じものであることを示す。

---

## 6. 対照（control）— 何が「証拠でない」か

### 6.1 GL framebuffer は証拠にならない（この条件の出発点）

```
scene-gl-screenshot-black-control.png (2759 B, GL screenshot = celtium-validation.png)
  vs scene-presented-frame-102.png : differentPixels=920909/921600 (99.925%)  meanAbsDiff=106.59
```

GL screenshot は GL-less stub では**構造上まっ黒**で、presented 画像とは別物。
「marker が GL framebuffer に無い」ことは何も否定しないし、逆に GL screenshot が証拠にならないことも
この 1 行で示されている。証拠の経路は compositor 出力であって GL ではない。

### 6.2 window が map していない run は compositor 出力に何も出ない

最初の marker run（`VK_LAYER_PATH` を渡し忘れた run）は、renderer が
`Validation was requested with -Dceltium.vulkan.validation=true, but the layer VK_LAYER_KHRONOS_validation is not installed.`
でデバイス生成を拒否し、world 読み込み中に `World.getSkyColorBody` の NPE で crash した。
この run の compositor 捕捉は **91 capture すべてが 2759 B の一様な黒・`windows=0`** だった
（`launcherExitCode=124`、crash report は `.celtium-work/runtime/compcap-marker/crash-reports/`）。
これは「compositor 出力が実在の窓に由来すること」の逆側の対照であり、
同時に **`-Dceltium.vulkan.validation=true` を使うなら layer を `VK_LAYER_PATH` で見せる必要がある**という
再現手順の必須条件でもある（`.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d`）。

### 6.3 grim は実 KMS 出力も読める

```
$ XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-1 grim -o HDMI-A-4 desktop-HDMI-A-4.png   # exit 0
desktop-real-output-HDMI-A-4.png: 1920x1080, distinctColours=9675, meanRGB=[40.4,41.9,56.2], uniform=False
```

headless backend 固有の成功ではないことの確認（実セッションには触れていない、読み取りのみ）。

---

## 7. 何が証明され、何が証明されていないか（不足条件）

### 証明されたこと

1. celtium の native present 経路が swapchain へ渡した完成 frame は、**Xwayland の X11 窓（`Cleanroom`）として
   compositor（sway/wlroots）に渡され、compositor が合成した出力バッファに、正立・等倍・正色で到達する**。
   到達は「renderer の番号付き presented dump と compositor 出力 capture が画素完全一致（0/921600）」で示した。
2. その到達は **marker によって帰属**できる: 出力 1280×720 全面が magenta (255,0,255) になるのは、
   present の clear を magenta に置き換えている間だけであり、その間だけ window が map されている。
3. fixture の presented-image 読み戻し（mean 170.0・単一色）と grim の読み取り（一様 255,0,255）が一致する。
4. 通常 run ではこれが**実 scene**（10104 色・fixture PASSED）で成立する。

### 証明されていないこと（不足条件）

**scanout（CRTC が framebuffer を物理モニタへ走査出力する段）そのもの**は測っていない。理由と、何があれば閉じるか:

| 欠けているもの | 現状 | 何があれば測れるか |
|---|---|---|
| DRM master / root 権限 | `ffmpeg -f kmsgrab` = `Failed to open DRM device`。`/dev/dri/card2` は実セッションの sway が master を保持 | root か `CAP_SYS_ADMIN`（または kmsgrab を許す権限）。ただしそれでも「走査中のバッファ」が読めるだけで、光子の証明には**カメラ**が要る |
| 実 CRTC 出力上でのゲーム窓 | 本計測のゲームは**私用 headless 出力**（`WLR_BACKENDS=headless`、CRTC 無し）に合成されている。実出力 HDMI-A-4 は実セッション sway が保持 | 実セッション（`DISPLAY=:0` / `wayland-1`）でゲームを窓として走らせる。**ただし grim が読むのは同じ「合成済み出力バッファ」なので、scanout の gap は閉じない**（実出力で増えるのは「実 KMS 出力の合成バッファ」という一点）。加えて 4 エージェントが作業中の共有デスクトップに窓を出すため、本計測では**意図的に行っていない** |
| 私用 compositor を実 CRTC に載せる | `WLR_BACKENDS=drm` は seat の DRM master を必要とし、実セッション sway が保持している | 実セッションを止めるか、別 seat/別 GPU を用意する（第 2 GPU は本機に 2 つあるが、どちらも実セッションが master） |
| `xdpyinfo`/`xrandr`/`xwd`/ImageMagick/Xvfb | 未導入 | 不要（grim + ffmpeg で足りる）。導入しても grim に対する証拠の強さは変わらない |

**したがって条件 (c) の「実 window/compositor 出力への到達」は充足であり、未測定として残るのは
「その先の scanout」だけ**である。scanout は利用可能などの方法でも観測できず（kmsgrab は権限で不可、
screencopy は設計上そこまで見ない）、これは方法の選択ミスではなく**この環境の権限・構成の限界**である。
なお headless 出力と実 KMS 出力の差は「CRTC に渡されるかどうか」だけで、grim が読む対象
（compositor の合成済み出力バッファ）はどちらも同じである。

---

## 8. 再現手順（コピペ用）

```bash
cd /home/s0u7a/projects/celtium
export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"

# 逐次実行（1 度に 1 ゲーム。game-dir は新規である必要がある）
for mode in marker scene; do
  extra=""; [ "$mode" = marker ] && extra="--jvm-arg=-Dceltium.vulkan.presentMarker=true" \
                                 || extra="--jvm-arg=-Dceltium.validation.allBlocks=true"
  python3 scripts/compositor_capture.py \
    --game-dir .celtium-work/runtime/compcap-$mode \
    --capture-dir .celtium-work/compositor-capture/$mode --interval 0.5 --startup-timeout 180 \
    -- /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
       --output .celtium-work/compcap-$mode-lock.json \
       --game-dir .celtium-work/runtime/compcap-$mode \
       --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
       --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
       --library celtium-interop/build/install/celtium-interop/lib/lwjgl-vulkan-3.4.1.jar \
       --jvm-arg=-Dceltium.vulkan.glless=true --jvm-arg=-Dceltium.vulkan.validation=true \
       --jvm-arg=-Dceltium.vulkan.presentReadback=true $extra \
       --jvm-arg=-Dceltium.vulkan.frameDumpDir=$PWD/.celtium-work/compositor-capture/$mode/frames \
       --jvm-arg=-Dceltium.vulkan.frameDumpEvery=20 --jvm-arg=-Dceltium.vulkan.frameDumpCount=80 \
       --jvm-arg=-Dceltium.validation.dumpPresentedImage=true --jvm-arg=-Dceltium.validation.minWorldFrames=5 \
       --run --validate --duration 90 --headless --force-x11 \
       --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
done

# 集計（capture の一様性と、番号付き frame との画素比較）
python3 scripts/compositor_capture_report.py --captures .celtium-work/compositor-capture/scene \
        --frames .celtium-work/compositor-capture/scene/frames
```

`--duration` を超えると launcher が `124` を返す（timeout は成功ではない）。
ゲームが自分で終了するのは fixture の verdict が出た時点で、marker run は
「presented が単一色」で FAILED になって終わる（**期待どおり**、§4）。

---

## 9. 成果物（`docs/history/evidence/compositor-output-2026-10-11/`）

| file | 内容 | sha256 |
|---|---|---|
| `marker-compositor-0035-magenta.png` | compositor 出力 capture 0035 = 一様 magenta 1280×720 | `45717d93e457…c9257` |
| `marker-presented-frame-20.png` | renderer の presented frame 20 dump（上と画素完全一致、0/921600） | `b4a5f8c163b4…876a` |
| `marker-compositor-0030-black-no-window.png` | 対照: window 未 map の出力（一様な黒、`windows=0`） | `7efdbbfeb59e…f574` |
| `scene-compositor-0066.png` | 通常 run の compositor 出力 capture 0066（実 scene） | `2cba55b1d718…99bf` |
| `scene-presented-frame-100.png` | renderer の presented frame 100 dump（上と画素完全一致、0/921600） | `917423f7556e…43b7` |
| `scene-fixture-presented-world-frame102.png` | fixture が読んだ presented 画像（presentedFrame=102） | `7ed1cfecac98…38cd` |
| `scene-fixture-presented-world-frame102.identity.json` | その identity sidecar（`presentedFrame`/`imageHash`/`frameBinding`） | — |
| `scene-gl-screenshot-black-control.png` | GL screenshot（GL-less stub では黒。presented と 99.925% 差） | `805aa3bd21f8…98b0` |
| `desktop-real-output-HDMI-A-4.png` | grim が実 KMS 出力（実セッション）を読めることの確認 | `de953da212dc…051f` |
| `marker-captures.json` / `scene-captures.json` | 全 capture の記録（時刻・grim exit・sha256・PID・window tree・output mode） | — |

`docs/plan.md` の条件 (c) の行と §7 の該当行を本計測に合わせて更新した。
