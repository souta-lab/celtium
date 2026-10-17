---
name: celtium-verify
description: Celtiumの実機検証・性能測定・画像比較で、artifactと受入証拠を取り違えないための手順。
---

# Celtium 検証

**このskillは作業許可ではない。** 利用者が監査・停止を指示している時は、build/GPU legを起動しない。

## 必要な時だけ読む

- [作業規約](<../../../AGENTS.md>)と[現在表](<../../../docs/plan.md>)の該当範囲。
- 実行手順・前提・exit code・出力位置は[開発・検証手順](<../../../docs/development.md>)が正典。
- 必要なキーを[計器索引](<../../../docs/instruments.md>)で探し、リンク先のread API・ゲート・上限を読む。
  全履歴・全索引を毎回ロードしない。
- 失敗や画像比較の時だけ[罠](<../../../docs/verification-traps.md>)の該当節。

## 実行と解釈の要点

1. build成功と今回の出力を確認し、renderer/fixture各1個をfresh stageへ。full SHA256・HEAD/差分・flagsを紐づける。
   hashだけではHEADからbuildされた証明にならない。
2. `scripts/gv-leg.sh --help`で実CLIを確認する。既存legは使い回さず、正典harnessのworktree共通game-slotを使う。
   自分以外のゲームがあれば起動しない。長いjobは管理IDを保持する。
3. wrapperのresult、runtime lock、process/fixture JSON、native device行、実提示PNGを合わせて読む。
   **exit 0 / fixture PASSED / validation cleanのどれか1つだけでは受入ではない**。未較正は判定不能でexit 2。
4. AMDとIntelの証拠を別々に読む。A/Bは固定条件と交絡を明示し、別JARの条件一致runを不当に禁止しない。
5. 性能の周期dump停止キーは **`-Dceltium.vulkan.presentedDumpEvery=0`**。
   それでも要求capture・frameDumpDir・readback・validation等が残る。「全fixture税除去」と言わない。
6. 自分のjobを回収/停止して終了を確認する。他人のjava/lockを消さない。
   pruneは既定dry-runで、provenanceと別原本のhashが揃った依存JARだけ。logs/world/config/画像は証拠として保持する。

## 不足がある場合

観測・仮説・次の判別条件を短く分ける。同じ無効flag・無出力・失敗buildを盲目的に再試行しない。
較正gateのFAILEDを説明で上書きせず、計測の制約を明示する。
