# plan.md の 2026-10-24 付き節（履歴・現在判定ではない）

現在表 [plan.md](../plan.md) から移した日付つき記録。**現在の命令・判定として読み戻さない。**

### 2026-10-24: modelのinstance描画（機構は実装済み）→ **「Celeritasより速い」は再現せず。p50は同等〜3%遅い／p95は一貫して低い**

lit modelのdisplay list再生（約800/frame）を共有mesh pool＋256B record＋bindless texture＋indirect runに（`VulkanModelInstances`）。
window counterのdriftも修正。計器の陽性対照: `modelInstances[appended=2,155,536 runs=240,867 declinedFull=0 declinedMissing=0]`（leg r1）。

KPIを3回取った。**同一JAR `fa32e6e98eb23d6b…`（HEAD `0536bee80` のclean buildで同一sha256を再現）・fixture `98a0bf43…`・
参照 Celeritas 2.5.0-dev `567dcf8a…`・AMD RX 6400・world/qmagnet・warm tick 400–600**:

| leg | 負荷 | 比 (rep1/2/3) | 中央値 | 判定 | p95 (celtium vs celeritas) |
|---|---|---|---|---|---|
| `modelinst-kpi-ref-2` | 1.99→5.58（上限6） | 0.869/0.908/0.881 | **0.881x** | NOT_SLOWER | 1.55–1.62 vs 2.58–2.64ms |
| `modelinst-kpi-conf-1` | 2.60→4.95（上限2を強制突破） | 0.742/0.918/1.010 | 0.918x | **判定なし** | 1.66–1.74 vs 1.93–3.43ms |
| `modelinst-kpi-conf-2` | 2.95→5.79（上限6） | 1.027/0.925/1.067 | **1.027x** | **SLOWER** | 1.70–1.96 vs 2.04–2.42ms |

**結論: 0.881xは再現しない。** 同じJARでguardを通した測定は 1.027x（SLOWER）、比の振れ幅 ±7%。
一方 **p95は3組すべてでCeleritasより低い**（上限側の安定性は実利）。p50の差は測定ノイズと同程度なので、
「Celeritasより速い」とは主張しない。Intel未測定。受入 `modelinst-valid-amd-2` PASSED・validation 0。
[証拠](<../evidence/modelinst-kpi-2026-10-24.md>)

### 2026-10-24: 描画ループのJIT化・arena slot・end portal producer → **Celeritas比 1.70x → 1.113x**

`drawPendingImmediate` が8282 byte（HotSpotの8000 byte上限超過）で**常にinterpreter実行**＝client threadの29.9%。分割でJIT化し3.4%。
速くなったCPUがGPUより2frame以上先行し、2 slotのframe arenaが枯渇→groupごとに`vkAllocateMemory`（695/2831サンプル）。
slot数をscheduler（4）+1に。end portalはlayerごと1 drawのproducerに。fixtureがCeleritas armに毎frame課していた税
（ClassNotFoundException×3・F3再解析、58/525サンプル）を除去＝**以前の比は実態より良く見えていた**。
KPI `arena-kpi-ref-1`（JAR `d3dfb9ba…`、AMD、3 rep、disturbed無し）: p50 **1.113x（SLOWER）**、**p95はCeleritasより低い**（1.93–2.02 vs 2.02–2.18ms）。
`HugeMethodLimitTest` が再発を止める。[証拠](<../evidence/jit-arena-2026-10-24.md>)。Intel未測定。

### 2026-10-24: 定常ログを既定で停止（`-Dceltium.debug.periodicLogs=true`で復帰）。速度主張なし。

### 2026-10-24: client tickのアニメtexture転送待ちを除去（staging ring）

spriteごとの`vkWaitForFences`をstaging ringで除去。KPI `ring-kpi-1`: warm p95 5.5–6.9→3.4–3.7ms、p50中立。[証拠](<../evidence/staging-ring-2026-10-24.md>)。

## 2026-10-24: 本体内の影は停止（利用者指示）

影はDemonica forkで作る（下の2.）。本体の優先は**性能→互換性**。本体内の影stage (a)〜(c)+(d計器)はcommit済み・既定OFF
（[証拠](<../evidence/shadow-stage-c-2026-10-24.md>)）。PCF受け側の未commit差分は `git stash` の
「paused: in-core shadow PCF receiver」（leg `shadowpcf-amd-1` PASSED・JAR `275e622e…`、影の視覚効果は未確認）。再開は利用者が明示した時だけ。

## 2026-10-24: native state/resource owner

`NativeRasterState` が blend/cull/色マスク/scissor/polygon offset、`NativeTextureBindings` が texture unit/binding を所有する。
通常drawでのGL記録の再取り込み・bind ordinal照合を削除。白textureもGL名生成/一時bindを経由せずVulkan画像を作る。
再確保時のstorage破棄と名前削除を分離し、atlasのbindingを保持する。

[実機証拠](<../evidence/native-owner-2026-10-24.json>): JAR `8baddf8701bb623be0f28bdd0be5dfc4124db28c5b924deb147e76c291013dbe`、
AMD RX 6400/Intel UHD 630 のqmagnet、AMDのvideoSettingsは画像gate PASSED・validation 0。F2は提示画像と一致。
menu/HEI inventory/recipeの実画像を保存したが、HEIの専用gateは未較正。性能は3pair測定したが負荷上昇（2.57→5.45）・1組のstarvationにより判定不能（残る比0.973/1.118）。改善を主張しない。
描画要求の受付・実行全体の移管と影/packは未完。以前からの影の未commit変更はこの成果物に含まない。
