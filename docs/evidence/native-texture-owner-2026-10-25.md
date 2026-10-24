# Native texture environment・framebuffer 所有権（2026-10-25）

実装と全flags・GPU・camera・区間・画像identity・途中失敗は [JSON](native-texture-owner-2026-10-25.json)。
renderer `50af1c4641373eb76e47ae4e57036b9bb45ee1334249a691c8ab08e3a113c9e8`、
fixture `373354a6d4044466c6d587b0af82490ae234fb56aa63166116192520f0ef99a7`。

- `NativeTextureEnvironment` が各unitの状態を所有する。vanillaの3種類の setter は native owner に直接渡して元のGL呼出しを取消す。
  Mod の raw GL setter/query は同じ owner への互換入口。モデル replay ごとの GL 問合せ・unit切替・buffer確保を撤去した。
  hurt の INTERPOLATE と、vanilla `enableOutlineMode` の REPLACE/CONSTANT を区別し、受理済みdrawには不変のmaterial snapshotを渡す。
- 描画前の空 framebuffer に、画素を受け取っていない別のmirrorを渡していた。native attachmentを初回だけ透明clearしてから公開し、未初期化imageのdescriptor化を拒否する。
  Intelでは変更前 `e8f6849a440a9359352a964a881b7569692ddf3d19e9f6c9e4415aa17b35a2db` と中間版の両方が
  `09600` 10件（全mipがUNDEFINED）で失敗。最終版は0件。
- 外部image初回公開・解除時のcache世代とmemo失効を補正。world終了時に破棄するpipelineを参照したままの prepared-material cache も破棄する。
- native main image は GENERAL を所有契約にする。中間版のworld往復で `01197` 7件を観測し、main画像のsamplerだけがSHADER_READ_ONLYへ動かしていた不整合を補正した。

| leg | GPU / scene | 判定 |
|---|---|---|
| texture-final-intel | UHD 630 / qmagnet→menu→同じqmagnet | PASSED、画像gate PASSED、validation ON 0件 |
| texture-final-amd | RX 6400 / 同上 | PASSED、画像gate PASSED、validation ON 0件 |
| texture-final-video | RX 6400 / Video Settings、GUI scale 3 | PASSED、validation ON 0件 |
| texture-final-inventory / recipe | RX 6400 / HEI | **UNCALIBRATED**、validation ON 0件、実画像を目視。合格には数えない |
| texture-final-glow / hurt | RX 6400 / entities保持、HUD非表示 | **UNCALIBRATED**、validation ON 0件。最終画面のglowは未修正 |

通常worldの拒否format censusは空。MCP実機exportで3 setterのnative経路を確認。
menuの実画像ではボタン・文字が表示された。利用者の「退出後ボタン無し」自体は未再現で、根因を断定しない。
GPU画像はPNGの **kept版＋identity** を使う（raw dump名は後続frameに上書きされる）。
CPU検査は146 JUnit・71 Python tests・包装検査が成功。最初のbuild二重起動によるtest-result競合は失敗として記録し、直列buildで再検証した。

## 性能と未完

[paired KPI](native-texture-kpi-2026-10-25.json) は AMD・world・worldTicks [400,600)、各arm3回、同じfixture。
Celeritas比中央値 **1.0342x**、変更前比 **1.0313x**。
load 0.4604→2.6724 が上限2.0を超えたため **NO_SPEED_VERDICT**。高速化は確認できず、3.1%を改善と解釈しない。

[回転付きprofile](spikes-2026-10-25.json) は変更前・RX 6400・日本語設定・1920×1080・Wayland、validation OFF。
warm [400,980) の50ms超は0枚・最大17.015ms、pipeline作成最大3ms。実デスクトップのまれなスパイクは未再現。
入室時の長い停止を全て説明できたとは言わない。

影・shaderpack接続、glowのmain合成、まれな実プレイスパイク、Intel KPIは未完。
