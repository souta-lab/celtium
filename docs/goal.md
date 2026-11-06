# Celtium — 製品目標と完成条件

2026-11-06 全面改訂（旧版の大半は解決済みのため整理）。状態と優先順は [plan.md](<plan.md>)（唯一の現在表）、手順は
[development.md](<development.md>)。本書は作業許可やGoalの稼働状態を変更しない。実測がより良い方針を示せば直す。

## 目的

Minecraft 1.12.2 / CleanroomMC 向けに、**有名modpackがエラー無く動き、Celeritasより高いFPSを出す** native Vulkan
レンダラを作る。

- **native Vulkan-only。** vanilla/Forge の描画・資源・状態・可視性・提出は native renderer が所有する。GL context・
  GL fallback に頼らず、拒否された描画は欠落として数える。
- **GL stub は「GLを直接呼ぶmod」専用。** mod の直接GL（raw FBO・PBO・raw matrix・自前GLSL program）は stub が受けて
  native の target・texture・matrix・pass に届ける。stub が何も描かないことを互換と呼ばない。
- **描画系modは競合として起動時に名指しで拒否**し、その手法（地形・culling・batching・資源遅延）は本体に取り込む。
- **基盤固有の最適化**（SDL3 / Java 25 / LWJGL 3.4 / Vulkan 1.2）は実測で採否を決める。
- **コードの健全性**: 巨大クラス（`VulkanTerrainGpuBackend`）を所有権ごとに分割し、置き換わった経路・効果の無い実験
  switch は削除する。挙動は CPU テストと leg で不変を確かめる。

## 完成条件と現状（2026-11-06）

| # | 条件 | 閉じる証拠 | 現状 |
|---|---|---|---|
| 1 | **FPS**: AMD RX 6400 と Intel UHD 630 の双方で、hash固定の Celeritas 2.5.0-dev より p50・p95・p99 が全て高い | `scripts/gv-kpi.sh` の形式verdict（Supersymmetry 0.2.0.2・validation OFF・warm tick [400,600)・3対以上・全対同方向・負荷ゲート通過） | **中央値は両GPU全percentileで上回る**（r107）。全対条件は p99 の対ごとの揺れで未達 |
| 2 | **有名modpack**: 選んだ有名packが world に入り、**validation 0** | 両GPUの実instance leg（`e2e` mode）、[modpacks.md](<modpacks.md>) に記録 | Supersymmetry・E2E-E 1.87.3 は達成。SkyFactory 4 から順に拡大中 |
| 3 | **native境界**: sky / terrain / entity / block entity / particle / hand / GUI / font / loading / settings が GL 互換層を逆呼出しせず描かれる | 出荷経路の census（`CELTIUM_GL_SURFACE*`・未route・program bind/拒否）が mod 由来だけ | 達成（census は mod 由来のみ） |
| 4 | **画質**: loading / home / game / settings と QMAGNET's Test Map が参照と一致 | 同fixture・同poseの Celeritas 参照比較と機能別の陽性対照 | 両GPUで一致（loading は参照なし） |
| 5 | **mod自前GLSL**: GTCEu bloom など mod の program を実行時に SPIR-V 化して native pass で描く | 該当 scene の参照比較 | opt-in で動作、既定は拒否・計数 |
| 6 | **影・shaderpack**: Demonica fork の別成果物（AGPL-3.0 addon jar）。本体は接続境界を壊さない | addon 側で両GPUの実画面 | 未着手（本体の優先は 1・2） |
| 7 | **配布**: packaged JAR とテスト済み実装が一致し、導入手順・対応環境・既知の非対応が正確 | 配布jarと検証jarの SHA-256 一致、README の規則（[readme-rules.md](<readme-rules.md>)） | 0.2.0 配布済み。main の改善は未リリース |

描画を削って得た FPS、validation clean 単独、`exitCode=0` 単独は達成に数えない。1 GPU・1 scene の成功を他へ広げない。

## 証拠の最低条件

- full JAR／fixture の SHA-256、build provenance、実GPU、pack と版、scene、flags、測定区間、leg名を紐づける。
  実行時の git HEAD は JAR の出自の証明ではない。
- 性能 run と validation/画質 run を分け、計測負荷を明記する。FPS は README では FPS で書く（KPI は ms で測る）。
- 失敗・未較正・欠測・hash 不一致は成功から除く。過去の否定・肯定は再入条件つきの仮説として扱う。
