# model instance producer の KPI 3回測定（2026-10-24）

## 何を測ったか

`VulkanModelInstances`（commit `8bdeb743a`）は lit model の display list 再生（約800/frame）を
共有 mesh pool＋256B record＋bindless texture＋indirect run に置き換える。その効果を、同一 JAR で
3回 paired 測定した。**1回目は同じ変更を入れた直後の測定（記録済み）、2・3回目が本記録の再測定。**

共通条件:

| 項目 | 値 |
|---|---|
| 候補 JAR | `fa32e6e98eb23d6b59c7d54fb0c360c450fc00569f13dc9ebfb2e7e7c3da159e`（`celtium-forge-mc1.12.2-0.0.13-alpha.jar`） |
| 由来 | `0536bee80` の**clean worktree からの build で同一 sha256 を再現**（`git status --porcelain` 空、`build-1122/build/libs/0.0.13-alpha/` の成果物） |
| fixture | `98a0bf43e63a2d39a245633b051e81d61860f635ace8b4570367a0f5245b90b3` |
| 参照 | Celeritas 2.5.0-dev `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c` |
| GPU / scene | AMD Radeon RX 6400 (RADV NAVI24) / world・`qmagnet`・x=217.5 y=48 z=217.5 yaw=180 pitch=10 |
| 解像度 / 区間 | 1280x720 / warm `worldTicks [400,600)`、validation off |
| 指標 | `frameIntervalWarmP50Ms`（fixture のフレーム間隔） |

計器の陽性対照（instance 経路が実際に走ったことの確認、leg `modelinst-kpi-conf-1-celtium-r1`）:
`modelInstances[appended=2,155,536 runs=240,867 declinedFull=0 declinedMissing=0 poolBuilds=77 poolLists=77 pipelines=9]`。

## 結果

| leg | 負荷 (start→end) / 上限 | rep1 | rep2 | rep3 | 中央値 | 判定 |
|---|---|---|---|---|---|---|
| `modelinst-kpi-ref-2` | 1.99→5.58 / 6 | 0.869 | 0.908 | 0.881 | **0.881x** | NOT_SLOWER |
| `modelinst-kpi-conf-1` | 2.60→4.95 / 2（`CELTIUM_KPI_FORCE=1` で突破） | 0.742 | 0.918 | 1.010 | 0.918x | **判定なし** |
| `modelinst-kpi-conf-2` | 2.95→5.79 / 6（guard 有効） | 1.027 | 0.925 | 1.067 | **1.027x** | **SLOWER** |

絶対値（p50 / p95 ms, samples）:

| leg | rep | celtium | celeritas |
|---|---|---|---|
| ref-2 | 1 | 1.177 / 1.583 / 7958 | 1.354 / 2.638 / 6587 |
| ref-2 | 2 | 1.201 / 1.617 / 7876 | 1.322 / 2.578 / 6795 |
| ref-2 | 3 | 1.119 / 1.547 / 8318 | 1.270 / 2.594 / 6817 |
| conf-1 | 1 | 1.166 / 1.732 / 7923 | 1.571 / 3.433 / 5500 |
| conf-1 | 2 | 1.172 / 1.743 / 7826 | 1.277 / 2.853 / 6651 |
| conf-1 | 3 | 1.186 / 1.657 / 7915 | 1.174 / 1.925 / 7805 |
| conf-2 | 1 | 1.339 / 1.957 / 7010 | 1.303 / 2.423 / 6966 |
| conf-2 | 2 | 1.184 / 1.696 / 7822 | 1.279 / 2.041 / 7295 |
| conf-2 | 3 | 1.351 / 1.894 / 6995 | 1.266 / 2.326 / 7176 |

## 読み

- **p50 の比は 0.88〜1.03 で、3回の測定で符号が変わる。** guard を通した最新測定は 1.027x（SLOWER）。
  したがって「Celeritasより速い」は**主張しない**。比の振れ幅（±7%）が現在の差より大きい。
- **p95 は3組すべてで Celeritas より低い**（1.55–1.96 vs 1.93–3.43 ms）。tail の改善は再現性がある。
- 絶対 p50 は 1.12–1.35ms で、v0.0.16-alpha 時点の 2.02–2.08ms から下がっている（同日・同scene・AMD）。
  **改善自体は実在するが、参照も同時に 1.17–1.57ms と速い**ため、比では並ぶ。
- 負荷は全測定で上昇（ブラウザ等の外部負荷）。`conf-1` は上限を強制突破したので判定を持たない。

## 次に必要なこと

比のノイズを超える改善を1単位入れてから paired を取り直す。対象は推測ではなく、現行 HEAD の
JFR（client thread, warm 区間）で上位に出たものから選ぶ。
