---
name: celtium-improve-loop
description: Celtiumの改善ループ全体（状態復元→1単位選択→観測と仮説→計器→実装→build→AMD/Intel leg→paired KPI→commit→文書→利用者への引渡し）を、どのLLMでも同じ品質で回すための手順と既知の罠。
---

# Celtium 改善ループ

**このskillは作業許可ではない。** 最新の利用者指示が優先する。停止・監査限定の依頼ではbuild/GPUを起動しない。
規約は [AGENTS.md](<../../../AGENTS.md>)、現在表は [plan.md](<../../../docs/plan.md>)、完成条件は [goal.md](<../../../docs/goal.md>)、
手順の正典は [development.md](<../../../docs/development.md>)、検証の読み方は [celtium-verify](<../celtium-verify/SKILL.md>)。
このskillは「何をどの順で回すか」と、実際に踏んだ罠だけを書く。重複する手順はリンク先が正。

## 0. 1周の形

```
復元 → 単位を1つ選ぶ → 観測/仮説/判別条件 → (計器) → 実装 → build+CPUテスト
  → stage → leg(AMD, Intel, 必要scene) → 画像を自分の目で見る → (paired KPI) → commit
  → plan/goal更新(意味が変わった時だけ) → 利用者instanceへ導入(求められている時) → 短い報告
```

1周は小さく。1commit = 1つの検証済み主張。未検証の変更は commit しない（実験用switchは既定OFFなら可）。

## 1. 復元（毎回）

- `git status --short --branch`、`git log --oneline -8`。他の担当（別LLM）が直前まで作業していた場合は
  **未commitの差分と `.gv-verify/jars/latest/*/source.patch` を突き合わせ**、検証済みのものと検証後に変わったものを分ける。
- plan.md の「次の作業単位」と利用者の最新報告（スクショ・クラッシュレポートのパス）を読む。
  利用者がスクショで示した不具合は**再現に時間をかけず**、原典と計器から原因を詰める（利用者指示 2026-10-25）。
- 優先順: クラッシュ > 画面が壊れる不具合 > 利用者が体感している性能/スパイク > KPI > リファクタ > 文書。

## 2. 観測・仮説・判別条件

- 1行ずつ書く: 観測（数値・ログ行・画像）/ 仮説 / それを否定できる次の測定。
- 既存の計器を先に探す: `docs/instruments.md` を grep → リンク先ソースで有効条件・上限・出力先を確認。
  ログは `CeltiumLogLimiter` でタグごと16行に制限される。足りなければ `-Dceltium.debug.logLinesPerTag=N`。
- Minecraft/Forge/他modの挙動は推測しない: `scripts/mc-sources.sh --grep/--srg`、他modは jar を展開して
  `javap -c -p -cp <dir> <class>` で実際のGL呼び出し列を読む（Xaero の PBO/raw FBO/raw matrix はこれで特定した）。
- 参照絵が要る時は **同じsceneをCeleritas(GL)で撮る**: `CELTIUM_LEG_REFERENCE_RENDERER=<Celeritas jar> scripts/gv-leg.sh ...`。
- 性能は JFR: `-XX:FlightRecorderOptions=stackdepth=256 -XX:StartFlightRecording=filename=<scratch>/x.jfr,settings=profile`、
  `jfr print --json --stack-depth 64 --events jdk.ExecutionSample,jdk.NativeMethodSample`（既定は5frameで切れる）。
  driver時間は NativeMethodSample にしか出ない。AMDとIntelを同条件で取り、**同じ区間の比**で比べる。

## 3. 実装の原則（このプロジェクト固有）

- GL stub は **GLを直接呼ぶmod** のためだけ。vanilla/Forgeの経路は mixin で意味を受け取り native に描く。
  1:1 GL→Vulkan変換を増やさない（goal.md の方針）。
- raw GL 経路を足す時は vanilla 経路との**二重適用**を必ず潰す。深さカウンタ（HEAD++/RETURN--）は RETURN が
  走らない経路で漏れた実績がある。直後の1呼び出しだけを打ち消す one-shot token の方が安全。
- `CeltiumGllessNativeTransformer` の分岐は戻り値型ごとのブロックの**中か外か**を確認する
  （非void native を void ブロック内に置いて一度も一致しなかった実績）。descriptor は `javap -s` で確認。
- 資源の再利用は完了で決める: 解放は `VulkanContext.celtium$retireCleanup` で提出列の完了まで遅らせる。
  batched submit では upload batch がフレーム本体より先に queue に入る。
- 巨大メソッド（8000 byte超）は JIT されない。`HugeMethodLimitTest` が守る。分割で回避する。

## 4. build とテスト

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew -p build-1122 \
  :celtium-vulkan:test :forge122:packageJar :forge122:validationRemapJar --console=plain \
  '-Porg.gradle.java.installations.paths=<jdk21>,<jdk8>' > <scratch>/build.log 2>&1; echo exit=$?
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew -p build-1122 --stop -q   # daemonは計測の負荷になる
python3 scripts/instrument-index.py      # 計器を変えたら同じ差分に含める
python3 scripts/check_agent_docs.py && python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

コンパイラメッセージは日本語（`エラー:`）。grep はそれを含める。

## 5. stage と leg

- AGENTS.md の保持規則: **検証runと成果物は直近1セットだけ**。次の検証の前に自分の古い `.gv-verify/run-*` と
  `<leg>-*.json/.out` を消す。`rm` は**リテラル絶対パス**か `"${S:?}"/...` で書く（空変数の rm は安全装置に止められる）。
- stage: `.gv-verify/jars/latest/candidate/` に今回の renderer と fixture を各1個、`git diff > source.patch` を添える。
  検証後にソースを変えたら、そのlegはその差分を覆わない。
- leg 出力の自分用ログは scratchpad へ。**`.gv-verify/<leg>.out` へリダイレクトしない**
  （harness が同名を作るため FATAL: artifact already exists で起動しない）。
- 標準セット: `world amd` と `world intel`（validation 既定ON）。描画経路を触ったら該当sceneも:
  glow `-Dceltium.validation.glowEntities=true -Dceltium.validation.keepEntities=true -Dceltium.validation.captureEntities=true`、
  観戦shader `-Dceltium.validation.spectator=creeper|spider`、時刻固定 `-Dceltium.validation.fixedWorldTime=true -Dceltium.validation.worldTime=1500`。
- 読む順: result の problems → validation-process.json の validationMessageCount/gpuRequirementMet →
  crash-reports → **screenshots の tick200 kept PNG を実際に見る**。未較正sceneは INCONCLUSIVE(exit 2) が正常。
- 利用者instanceに mod を足されていると leg もそれを含む（instance をコピーするため）。急な全滅はまず crash-reports。

## 6. 性能（paired KPI）

```sh
CELTIUM_KPI_GPU=amd|intel CELTIUM_KPI_LOAD_LIMIT=2 CELTIUM_KPI_JARS=<stage> \
CELTIUM_KPI_REFERENCE=<Celeritas jar> CELTIUM_KPI_REPS=3 CELTIUM_INSTANCE_WORLD=<instance> \
CELTIUM_LEG_LWJGL_VULKAN=<lwjgl-vulkan jar> scripts/gv-kpi.sh <tag> 150 \
  -Dorg.lwjgl.librarypath=<natives> -Dceltium.vulkan.frameDumpEvery=0 -Dceltium.vulkan.presentedDumpEvery=0
```

- 負荷ゲート: 開始時 load > 上限なら拒否、途中で上がれば NO_SPEED_VERDICT。待つなら background の until-loop で
  load を見てから起動（固定sleepの反復ではなく条件待ち）。ブラウザ等の外部負荷を自分のプロセスと混同しない。
- 判定は形式verdictと3対の比・p50/p95/p99 を両方報告する。verdict が出ていない数値を「勝った」と書かない。
- 1変数のA/B（例 `-Dceltium.vulkan.batchFrameSubmits=true/false`）は同じjarで交互に2回ずつ。

## 7. commit・文書・引渡し

- commit message: 何を・なぜ・どの jar hash/leg/数値で確かめたか・未解決は何か。末尾に attribution 行。
- plan.md は意味が変わった時だけ、16 KiB 予算内（`check_agent_docs.py` が検査）。履歴は commit に残す。
- 利用者が自分で試す時: 起動中でないことを確認し、instance の旧jarを `minecraft/celtium-backup/celtium-forge-<sha8>.jar`
  へ退避してから入れ替える。入れたjarのhashを報告する。
- 報告は短く: 直ったもの（証拠付き）/ 直っていないもの / 次にやること。

## 8. 止まり方

同じ仮説で2回進展がなければ、追加計器の前に「足りない証拠」と「次の判別条件」を書いて実験を変える。
較正gateの FAILED を説明で消さない。自分が起動した job だけを止める（`TaskStop`/自分のPID）。
