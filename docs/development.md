# 開発・検証手順（Linux / Cleanroom実instance）

**作業許可は最新の利用者指示で決まる。** この手順やGoalを読んだだけで開発・GPU実行を再開しない。
現在判定は [plan.md](<plan.md>)、製品の完成条件は [goal.md](<goal.md>)。

## 1. 最小の復元と調査

1. 依頼の範囲、`git status --short --branch`、必要な差分を確認する。文書/ツール修正ならGPUを起動しない。
2. 開発再開なら現在表から**一つの未完単位**を引き継ぐ。履歴全文を読む・branch/担当を毎round作る義務はない。
3. 「観測」「仮説」「次に判別できる条件」を短く分ける。実装のproducer/consumer/寿命と原典を読む。
   計器は [索引](<instruments.md>)から必要なキーだけ検索し、有効条件・出力先・上限をソースで確かめる。
4. 同じ失敗の無条件再実行・同じ仮説の言い換えは進捗ではない。実験を変えるか、不足条件を明示する。
   buildの `ClosedFileSystemException` は**同じ例外を確認した時だけ1回**再試行し、反復するならログを保全して原因調査する。

## 2. GPUを使わない手順検査

```sh
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/cpu-contract/shared_game_slot_check.py
python3 scripts/check_agent_docs.py
python3 scripts/instrument-index.py --check
bash -n scripts/gv-leg.sh
```

これは検証ツール・文書契約の検査。ゲームの画質・FPS・Mod互換を受け入れるテストではない。

## 3. buildとartifactの固定（開発の再開許可がある時）

JDK25でGradleを起動し、JDK21/8のtoolchain、glslangValidatorを用意する。
ローカルJDKパスは環境ごとに `-Porg.gradle.java.installations.paths=...` で渡す。ユーザー名付きの固定パスを正典にしない。

```sh
./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain
```

**build成功を確認してから**今回生成したpackaged JARとvalidation JARを明示する。`ls | head`、mtimeだけ、古いglobの全コピーで選ばない。
以下の`<...>`は実在する今回の値に置換するテンプレートであり、そのまま実行しない。

```sh
JAR=build-1122/build/libs/<version>/celtium-forge-mc1.12.2-<version>.jar
FIXTURE=forge122/build/libs/celtium-validation-client-<version>.jar
python3 scripts/verify_forge_jar.py "$JAR"
python3 scripts/verify_version_consistency.py --jar "$JAR"
mkdir -p .gv-verify/jars
STAGE=.gv-verify/jars/<unique-tag>
mkdir "$STAGE" && cp -- "$JAR" "$FIXTURE" "$STAGE"/
sha256sum "$STAGE"/*.jar
```

hashはbytesの識別であり、単独では「そのHEADからbuildされた」証明ではない。build成功・差分・source revisionも紐づける。
stageにはそれぞれ正しいfamilyのJARを**ちょうど1個**置く。既存tagを使い回さない。

## 4. freshなGPU leg

正典は [gv-leg.sh](<../scripts/gv-leg.sh>) → [gv_leg.py](<../scripts/gv_leg.py>) → [prism_validation.py](<../scripts/prism_validation.py>)。
未追跡の `.gv-verify/` 内の古いrunner/harnessは使わない。CLIは `scripts/gv-leg.sh --help`。

前提: Linux x86_64、Sway/swaymsg/Xwayland、対応するrender nodeとVulkan validation layer、LWJGL 3.4.1 Vulkan binding JAR、
Cleanroom 0.6.13-alphaのPrism profileとlibrary/asset cache。source instanceは読み取り専用で、隔離copyを使う。
既定のGPU名・PCI path・instance配置はこの検証機向けであり、他環境でそのまま動作する保証ではない。

```sh
scripts/gv-leg.sh <unique-leg> .gv-verify/jars/<tag> cohort amd 900
scripts/gv-leg.sh <different-leg> .gv-verify/jars/<tag> cohort intel 900
```

- mode: `plain` / `cohort` / `e2e` / `recipe`。recipeはRLCraft移行profileでresources/structures/scriptsも要求する。
  profileの内容が完全かは別途確認し、mode名・mod数だけで完全pack互換を主張しない。
- `CELTIUM_ROOT` / `CELTIUM_INSTANCE_COHORT` / `CELTIUM_INSTANCE_E2E` / `CELTIUM_INSTANCE_RECIPE`でcheckout/instanceを差し替える。
- IntelはrunnerがMesaの `DRI_PRIME=pci-0000_00_02_0!` を設定する。それでも結果のnative device行とGPU gateを読む。
- 追加JVM引数は秒数の**後**へ1引数ずつ渡す（空白を含む値はquote）。同じキーを複数回書いた時の有効値も記録する。
- **ゲームは同時に1つ**。ハーネスのworktree共通 `game-slot/owner.pid` が直列化する。旧 `gpu.lock` のmkdir待ちを重ねない。
  自分が起動していないゲームがある時は起動しない。lockを消して割り込まない。
- 同名legの出力が存在すれば拒否する。古い失敗を消さず別名で実行する。長いjobは管理ツールのbackground jobとしてIDを保持する。

## 5. 何をもって受入とするか

run内のprocess/fixture JSON・native device・提示PNG・artifact hashを**合わせて**読む。
wrapperのexit 0は、当該legについて実装された受入条件をすべて通った時だけ。**未較正はexit 2で、成功ではない**。
失敗・timeout・欠測・wrong GPU・hash不一致も成功ではない。全scene・全modの受入まで意味を広げない。

| 証拠 | 位置 |
|---|---|
| source/helper/JAR hash・argv/env・依存source | `.gv-verify/<leg>-manifest.json` |
| wrapperのaccepted・exitCode・問題 | `.gv-verify/<leg>-result.json` |
| runtimeのmod hash・effective JVM args・profile | `.gv-verify/<leg>-lock.json` |
| raw JVM exit / fixture / GPU / validation exit | `.gv-verify/run-<leg>/validation-process.json` |
| scene・camera・較正・画像verdict・計器 | `.gv-verify/run-<leg>/logs/celtium-validation.json` |
| device行を含むconsole / 全logs / 画像 | `.gv-verify/run-<leg>/validation-console.log`, `logs/`, `screenshots/` |

**Intel**は `CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)"` と `gpuRequirementMet=true`。
`fixtureStatus=PASSED`でもVulkan validationが失敗すれば受入失敗。古いIntel resize legはまさにその例。
`validationClean=true`だけでも正しい絵は証明しない。図の対照・画像gateの適用範囲は [検証の罠](<verification-traps.md>)。

### loadingを観測する場合

- `-Dceltium.validation.dumpLoadingProgress=true`はworld起動を要求した後、実際の`LoadingScreenRenderer`のswap直前で最大8回captureを要求する。
  `loading-progress-N`のproducer値と`pictureIdentity_loading-progress-N`のPNG・frame束縛・completionErrorを読む。要求だけで欠測を成功にしない。
- `-Dceltium.validation.probeLoadingProgress=true`を併用すると、world起動の直前に**制御された50%バー**も描く。
  `controlledProbe=true`の画像は自然なworld生成の数値進捗ではない。通常の起動は`progress=-1`でバーを要求しない。
- 前述captureを有効にして`-Dceltium.validation.resizeDuringLoading=true`を併用すると、自然な非空タイトルの最初のframeを対照として残し、2回目に一度だけresizeを要求する。
  サイズとcompositorへの要求は既存の`resizeTo` / `resizeViaCompositor`を使う。MC・GLFWサイズ、GUI scale、loadingScreenのidentity、実PNGを合わせて読む。
  成功した最終terrain画像だけで、途中のloading画像やresize過渡frameまで成功にしない。
- `dumpPresentedWorldLoadTick`は旧来の起動後client-tick sample。同期的world起動の内部はそのhandlerから観測できない。
  `currentScreen=GuiMainMenu`や`loadingScreen != null`だけでは、どのproducerが描画しているか分からない。
- これらは**追加の診断画像**で、terrain主gateのPASSEDとは別に評価する。loading中resizeやForge起動進捗の受入まで広げない。


## 6. 性能測定は画像受入と区別する

周期presented dumpを止めるキーは **`-Dceltium.vulkan.presentedDumpEvery=0`**。
`celtium.validation.presentedDumpEvery`は実装されていない。旧`gv-notax-*`のeffective argsにもこの誤指定がある。
さらにframeDumpDir経由のdumpは別経路なので、必要なら **`-Dceltium.vulkan.frameDumpEvery=0`** も使う。
それでもfixtureの要求capture、presentReadback、validation、計器、world生成などの負荷が残る。**「全fixture税を除去」とは呼ばない**。

- warm/cold・tick/frame範囲・サンプル数を揃え、全frame interval、render bracket、tick、CPU/GPUを分ける。
- 平均・p50からp99を説明しない。別区間のGPU debug値をCPU p50から引き算しない。
- whole-runのtick合計にはstartupが含まれる。warm costやどのmod由来かは別に判別する。
- 画像受入を黙って無効化して性能を成功扱いしない。測定条件の異なるrunは明示的に別用途とする。

## 7. 終了と保全

自分のjobは結果を回収し、不要なら管理ツールで停止して終了を確認する。全javaをkillせず、他人のlockを消さない。
runnerは所有process groupの停止を扱うが、強制終了やネイティブcrash後は残存を確認する。

[gv-prune.py](<../scripts/gv-prune.py>)は**既定dry-run**。完了・較正・成功・provenanceと、別の原本のhashが揃った依存JARだけを整理する。
logs・crash・screenshots・framedump・config・options・worldは証拠/再現条件として残す。不明なrun、失敗run、sourceがない依存は触らない。

```sh
python3 scripts/gv-prune.py
# 表示と保存先を確認した後だけ:
python3 scripts/gv-prune.py --apply
```

ディスク空きがあるなら今回の監査のためだけに削除しない。古いpruneで失われた資料を「復元済み」とは主張しない。
