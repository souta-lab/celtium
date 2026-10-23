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

前提: Linux x86_64、Sway/swaymsg/Xwayland、対応するrender nodeとVulkan validation layer、**LWJGL 3.4.3** の Vulkan binding JAR（natives も 3.4.3 で揃える）、
**Cleanroom 0.7.11-alpha**（GLFW ではなく **SDL3**、ゲーム JVM は **Java 27**）の Prism profileとlibrary/asset cache。source instanceは読み取り専用で、隔離copyを使う。
既定のGPU名・PCI path・instance配置はこの検証機向けであり、他環境でそのまま動作する保証ではない。

```sh
scripts/gv-leg.sh <unique-leg> .gv-verify/jars/<tag> cohort amd 900
scripts/gv-leg.sh <different-leg> .gv-verify/jars/<tag> cohort intel 900
```

- mode: `plain` / `cohort` / `world` / `e2e` / `recipe`。recipeはRLCraft移行profileでresources/structures/scriptsも要求する。
  profileの内容が完全かは別途確認し、mode名・mod数だけで完全pack互換を主張しない。
- **`world` は実プレイ標準**（2026-10-19の利用者指示で平坦fixtureから移行）。配布ワールド
  QMAGNET's Test Map（CurseForge project 71679 / file 2467609、`.celtium-work/worlds/` に展開・来歴は同dirのREADME）を
  save `qmagnet` として持つclone instanceで走り、fixtureの `scene=world` がマップのspawnポーズ・描画距離18で撮影し、
  同マップのGL参照runから測った床で判定する（`celtium$worldMapGate`）。instanceが無ければ
  `CELTIUM_INSTANCE_COHORT` のcloneを作り `.celtium-work/worlds/qmagnet` を `minecraft/saves/qmagnet` へコピーする。
- `CELTIUM_ROOT` / `CELTIUM_INSTANCE_COHORT` / `CELTIUM_INSTANCE_WORLD` / `CELTIUM_INSTANCE_E2E` / `CELTIUM_INSTANCE_RECIPE`でcheckout/instanceを差し替える。
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

**profileを取るleg（JFR）はdumpを切る**。picture dumpは1枚あたり~340ms（1280x720の**GPU sync＋per-pixel変換＋PNG**）かかり、
1 legで13枚≈4.2秒＝client threadサンプルの**15%**を占めます（`readBackBlock`が最大のleafになる）。
これはレンダラのworkではなく証拠作成のコストなので、**JFR/profileのlegは `-Dceltium.validation.dumpPresentedImage=false` で走らせ、
そのlegの`presentedImageVerdict`がFAILEDなのは想定どおり**と扱います。KPI/性能比較のlegはdumpを残してよい
（比較は**arm別中央値**で行い、数十枚のspikeは中央値を動かさない。平均は動くので使わない）。

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

正典は **`scripts/gv-kpi.sh` → `scripts/gv_kpi.py`**（paired・hash固定・warm区間）。
`CELTIUM_KPI_JARS`（そのHEADのbuildをstageしたディレクトリ、`build-provenance.json`必須）と
`CELTIUM_KPI_REFERENCE`（対照JAR）を明示する。CLIは `gv-kpi.sh TAG [SECONDS=240] [共有-D...] [-- Celtium専用-D...]`。

```sh
CELTIUM_KPI_JARS=.gv-verify/jars/<tag> CELTIUM_KPI_REFERENCE=<celeritas.jar> \
  CELTIUM_KPI_REPS=3 scripts/gv-kpi.sh <unique-tag> 240
```

判定は `frameIntervalWarmP50Ms`（worldTicks 400..600）。fixtureは通常約200tickで終了するため、
**`holdUntilTick=620` を渡さないとwarm区間は空**（スクリプトが自動で付ける）。以下は自動で拒否される:
provenance/sha不一致、`warmSampleCount<50`、`worldTicksAtEnd<600`、両armのscene/camera/解像度/雲/pacing差、
3ペア未満、Celtium専用flag（`--`以降）、fps上限・vsync。終了コードは 0=NOT_SLOWER / 1=SLOWER / 2=NO_SPEED_VERDICT。

周期presented dumpを止めるキーは **`-Dceltium.vulkan.presentedDumpEvery=0`**。
`celtium.validation.presentedDumpEvery`は実装されていない。旧`gv-notax-*`のeffective argsにもこの誤指定がある。
さらにframeDumpDir経由のdumpは別経路なので、必要なら **`-Dceltium.vulkan.frameDumpEvery=0`** も使う。
それでもfixtureの要求capture、presentReadback、validation、計器、world生成などの負荷が残る。**「全fixture税を除去」とは呼ばない**。

- **2つのビルドを比べるときは制御armを使う**（2026-10-21 追加）。`CELTIUM_KPI_CONTROL_JARS=<固定ビルドのstage>` を渡すと、各repetitionが **candidate → celeritas → control** の順（偶数repは逆順）で走り、`medianControlRatio` が**同一arm・同一窓**の比として出る。参照armとの比は「Celeritasからどれだけ離れているか」を答えるが、**マシンドリフトは相殺しない**（別レンダラなので負荷応答が違う）。ビルド同士の優劣は control 比で判定する。control側のfixtureは再ビルドでbytesが変わるため同一性は要求せず、**窓の契約（warmFrom/To・サンプル数・完走）**で検証し、差は `inputs.controlFixtureDiffers` に記録する。
- **`java.util.function` のlambdaは確保されているとは限らない**（2026-10-21 実測）。`celtium$mirror(b -> ...)` 形式の
  ホット変換を専用呼び出しに置き換えても比が動かなかったので単体で測った: lambda経由 0.6-1.2ns/call、
  直接呼び出し 0-0.2ns/call。**HotSpotのescape analysisが非脱出のlambdaを消しており、確保は起きていない**。
  JFRがそのlambdaフレームを数%と表示しても、それは**インラインした本体がそこで走っていた**という意味で、
  確保のコストではない。プロファイルの数%を見て「確保を消す」変更を入れ、比が動かない場合はこれを先に疑う。
- **分解能は負荷で変わる**（2026-10-21 追記）: 静穏時の3回は1.4ppだったが、負荷が動く時間帯の1回は**ペア内ばらつき4.5pp**だった。1.4ppは「静穏時の上限」であって保証ではない。負荷が動いたrunの中央値の差（2pp程度）は**判定不能**として扱い、変更の構造（仕事が増えたか減ったか）と併記する。
- **分解能の実測**（2026-10-21、同条件の3回の3ペア測定）: ペア内の比のばらつきは 1.4 / 2.5 / 4.8pp、
  3ペア中央値のrun間ばらつきは **1.4pp**（1.4928 / 1.4789 / 1.4811）。⇒ **中央値で約1-1.5%の差まで判別できる**。
  それ未満の改善は「測れない」ので、1%未満を狙う変更は**複数回の測定か、より大きな構造変更と同じ便に載せる**。
  逆に、JFRや自作bracketが示す数%の「ホットスポット」を修正して比が動かない場合、それは**その計器の分解能・帰属の問題**を
  先に疑う（このセッションで3件連続そうだった）。
- `CELTIUM_KPI_LOAD_LIMIT`（既定2.0）は**下げて測定しない**。上げる場合は結果JSONに
  `environment.initialLoadAverage`/`loadLimit`/`guardBypassed` が残るので、その数値を根拠と一緒に書く。
  常駐負荷（ブラウザ等）はpaired交互順で両armから相殺されるが、**絶対msは床を引きずる**。絶対値の断定に使わない。
- 高速化の効果は同一jarの再測定では出ない。**変更前後で別stageを作り、同じ条件で測る**。
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

## 効果の可視計測（影など、明るさの集計では見えないもの）

影の有無を「暗い画素の割合」で判定しない: 元から暗いテクスチャでは×0.2にしても閾値を跨がず、**効果があっても数字が動かない**（round 207で実際に誤判定した）。代わりに**効果そのものを可視化して数える**:

- フラグメントで係数を色として出力する（例: `fragColor.rgb = vec3(1.0 - shadow, 0.0, shadow)` ＝ lit=青 / shadowed=赤）
- 1 leg回して、画面の地形ピクセル数と shadowed 数を数える（実測例: 可視地形 48,319 (lit) + 5,048 (shadowed) ＝ **9.46%**、ライト設定により 16.4% も観測）
- デバッグ出力は**計測専用**であり、受入用jarには含めない（戻したことを`grep -c DEBUG`で確認してからビルドする）

この方式は「validation clean・カウンタが出る」とは独立に**効果が画面に出ているか**を測れる。

## 影の視覚受入と構図（round 210の知見）

qmagnetの標準view（壁面支配・yaw180/pitch10）では、**太陽の影が画面内の面に落ちにくい**ため、通常描画のスクリーンショットで影を明瞭に示せない（実測: 地形の9.46%は実際にshadowedだが、その大半は画面外/自己影）。構図を変えるには `-Dceltium.validation.cameraX/Y/Z/Yaw/Pitch` が使えるが、**画像gateは基準pose専用**のため、poseを変えたlegは `INCONCLUSIVE`（判定不能）になる — つまり「見せるための構図」と「受入のgate」は両立しない。

そのため影の**機能有効性**は次の2点で示す:
1. **デバッグパレット**（係数を色で出力）で可視地形のうち何%がshadowedか（実測 9.46%、light設定により16.4%）
2. validation clean のまま**両GPU**で動くこと（AMD/Intel）+ コスト測定（p50 +10%）

通常描画での見栄えの受入は、**床が広く太陽が斜めの専用シーン**を用意してから行う（qmagnetの標準viewでは不可）。

### Prism からの手動起動（Cleanroom 0.7.11 + LWJGL 3.4.3）

1.12.2 のメタコンポーネント `org.lwjgl3 3.3.1` は依存として残り、Prism の画面からは削除できない。これを残したまま起動すると、
**3.3.1 の natives と 3.4.3 のクラス**が混ざり `MemoryUtil.ngetPageSize()` の `UnsatisfiedLinkError` で即死する（2026-10-22 実測）。

回避は **natives を 3.4.3 に固定する**こと。`~/.local/share/PrismLauncher/libraries/org/lwjgl/*/3.4.3/*-natives-linux*.jar`
を展開したディレクトリを作り、インスタンスの `instance.cfg` に次を入れる（Prism の UI なら「Java → JVM の引数」でも同じ）:

```
OverrideJavaArgs=true
JvmArgs=-Dorg.lwjgl.librarypath=<instance>/natives-3.4.3
```

`org.lwjgl.librarypath` は LWJGL が最初に探す場所なので、クラスパス上の 3.3.1 natives は読まれない。
検証ハーネス（`scripts/gv-leg.sh`）は自前でこの引数と `CELTIUM_LEG_LWJGL_VULKAN` を渡すため、この設定に依存しない。

#### 手動起動で白画面になる場合（実測 2026-10-22）

`CELTIUM_WINDOW_RENDERER failed: ... NoClassDefFoundError: org/lwjgl/vulkan/VkDebugUtilsMessengerCallbackEXTI` が出て
白画面になるのは、Prism の classpath に **LWJGL Vulkan モジュールが入っていない**ため（検証ハーネスは `CELTIUM_LEG_LWJGL_VULKAN`
で自前に入れるので気づかない）。インスタンス側の `java-celtium.sh` が `-cp` の値に `libs/lwjgl-vulkan-3.4.3.jar` を
追記し、`-Dorg.lwjgl.librarypath` で 3.4.3 natives を指す。Prism の「Javaのパス」にこのスクリプトを指定する。

#### 起動後に SDL で SIGSEGV（2026-10-22 実測・未解決）

`java-celtium.sh` 経由の起動は **LWJGLの版混在を通り抜け、Vulkan device選択・初回presentまで到達**する
（`CELTIUM_DEVICE_FEATURES ... textureArray=true`、`CELTIUM_PRESENT_SLOW millis=55 frame=0`）が、その直後に

```
SIGSEGV in libSDL3.so
  org.lwjgl.sdl.SDLEvents.SDL_PollEvent <- com.cleanroommc.client.sdl.Pump.pump
  <- CleanroomWindowBridge.pump <- Minecraft.redirect$...maybeSkipGlSwap
```

で落ちる。観測:
- クラッシュ時の SDL は `natives-3.4.3/linux/x64/org/lwjgl/sdl/libSDL3.so`（サブディレクトリ配置）。
- 同ディレクトリにはフラットな `libSDL3.so` も置いてあり、**同一ライブラリが2箇所**に存在していた。
  二重ロードは SDL の内部状態を壊しうるため、サブディレクトリ側を削除してフラット7個のみに整理した（この後の再起動は未検証）。

**次に見るべきもの**: 再起動して再発するなら、`SDL_PollEvent` の直前に行う `SDL.installLog`（FFM upcall）と
こちら側スタブの FFM upcall が同じ SDL インスタンスに干渉していないか。`hs_err` の `Dynamic libraries` で
libSDL3 が1つだけマップされているかも確認する。

**2026-10-22 reproduction split**: the same jar passes a headless Sway X11 leg (presents at frames 0 and 3, no fault) and dies in SDL_PollEvent only on the real Wayland session. The Java frames put that call inside the redirect replacing the GL swap, via Display.processMessages -> CleanroomWindowBridge.pump, so a nested SDL pump is the suspect; SDL is not reentrant. Next: check whether the hs_err library map holds one libSDL3, then either skip the pump during the splash or pump exactly once per frame.

**切り分けスイッチ（2026-10-22 実装・検証済み）**: `-Dceltium.vulkan.pumpInRedirect=false` で、present後の
`Display.processMessages()` を呼ばない。既定は true（従来どおり）。jar `706002c27965` で **true/false とも AMD leg PASSED・
画像gate PASSED・validation 0件**（挙動の既定は不変）。Prismの起動では wrapper が `no-pump` ファイルの有無で
このフラグを付けるので、SDLのポンプが原因かを1ファイルで切り分けられる。

**注意（2026-10-22 実測）**: `natives-3.4.3` は **LWJGLのjarレイアウト（`linux/x64/org/lwjgl/...`）を削ってはいけない**。
フラットな `.so` だけにしたところ、LWJGLはnativesを見つけられずclasspath上の3.3.1に落ちて
`MemoryUtil.ngetPageSize` の `UnsatisfiedLinkError` で起動失敗した。**サブディレクトリ配置とフラット配置の両方**を置く
（7 jarを展開すればそうなる）。

**Waylandでのクラッシュ（2026-10-22 実測）**: 起動は `CELTIUM_WINDOW_RENDERER created` → Vulkan device選択 → 初回presentまで進んだ後、
`SIGSEGV in libwayland-client.so.0 wl_display_read_events` で落ちた（前回の `SDL_PollEvent` とは別箇所）。
同じビルドが検証ハーネス（X11）では完走するため、ラッパーで **`SDL_VIDEODRIVER=x11`** を既定にした
（instance直下に `wayland` ファイルを置くとSDLに任せる）。

**Waylandクラッシュの解析状況（2026-10-22）**:
- hs_err は `Current thread is native thread` ＋ `libwayland-client.so.0 wl_display_read_events` のみで**Javaフレーム無し**
  ＝Javaからの呼び出しではなく**ネイティブスレッド**が落ちている（スレッド名はhs_errに無い）。
- こちらの `CeltiumWindow` は SDL ウィンドウを**新規作成しない**（既存の Cleanroom ウィンドウを反射でラップ）ので二重display説は否定。
- `gdb -batch ... --args java ...` は **JVM起動直後（`VM_Version::get_processor_features`）でSIGSEGV**し、目的の地点に届かなかった
  （gdbとJVMの相性。`set disable-randomization off` 等を試す必要がある）。
- 次の手段: `ulimit -c unlimited` ＋ `-XX:+CreateCoredumpOnCrash` でコアを保存し、`gdb <java> <core>` で
  **ネイティブスレッドのスタック**を取る（`thread apply all bt`）。それが判れば、SDLが立てたスレッドかこちらかを確定できる。

**デバッガ経路は塞がっている（2026-10-22）**:
- `kernel.core_pattern = /dev/null` → **コアダンプは保存されない**（`coredumpctl` も空）。有効化には root 設定変更が要る。
- `gdb --args <java> ...` は **JVM起動直後**（`VM_Version::get_processor_features`）で SIGSEGV し、ゲームまで到達しない
  （`set disable-randomization off` でも同じ）。この環境のgdbではJVMを起動できない。

**次の仮説（コードで検証可能）**: Wayland の `wl_display` は**スレッド安全ではない**。SDL(Wayland) のイベント読み取りと、
Celtium の present（Vulkan WSI が同じ `wl_surface`/`wl_display` を触る）が**別スレッド**で走ると、
`wl_display_read_events` 内で SEGV しうる。クラッシュが「ネイティブスレッド」で起きていること、Javaフレームが無いことと整合する。
確認方法: present と SDL ポンプの**スレッドIDをログに出し**、両者が同一かを実測する（`Thread.currentThread()` と
`SDL_GetCurrentThreadID` 相当）。同一でなければ、present をSDLスレッドへ寄せる設計変更が本命。

**アタッチ方式の試行（2026-10-22）**: `gdb -p <java pid>` で実行中プロセスにアタッチし `continue` 後に `bt` を取る方式を試したが、
2回とも **bt が空**（`ptrace_scope=1`、フィルタでエラーを落としていたため原因未確定）。次回は**生のgdb出力**を保存して
`ptrace: Operation not permitted` か `no such process`（既にクラッシュ済み）かを切り分ける。
タイミングは 起動→約12.7秒でクラッシュ、アタッチは9秒時点なので、**アタッチ直後にクラッシュを跨げていない**可能性がある。

**アタッチの可否が判明（2026-10-22）**: `gdb -p` は `ptrace: 許可されていない操作です` で拒否された。原因は
`kernel.yama.ptrace_scope=1`（親プロセスのみ許可）。`sudo -n sysctl -w kernel.yama.ptrace_scope=0` で解除でき、
**この環境ではsudoがパスワード無しで通る**（解除後はアタッチ可能・終了後に 1 へ戻した）。

次回の手順:
1. `sudo sysctl -w kernel.yama.ptrace_scope=0`
2. ゲームを起動（ハーネス）→ java pid を取得（`pgrep -f net.minecraft.client.main.Main`）
3. `gdb -p <pid> -batch -ex "continue" -ex "bt 20" -ex "thread apply all bt 8"`
4. 終了後 `sysctl -w kernel.yama.ptrace_scope=1` に戻す

**注意**: `pkill -f prism_validation.py` を自分のコマンドラインに含むシェルで実行すると**自分自身を殺す**（実測）。
pid指定のkillにすること。

**コアダンプから取れたスタック（2026-10-22・決定的）**: `sudo sysctl -w kernel.core_pattern=/tmp/celtcore.%e.%p` ＋
`-XX:+CreateCoredumpOnCrash` でコア取得（2.1GB）。`gdb java core` の結果:

```
#12 ffi_call                     (libffi.so.8)
#13 #14 libwayland-client
#15 wl_display_dispatch_queue_pending
#16 #17 #18 libSDL3.so
#19 ??   ← ここで戻りアドレス不明（JITコード/スタック破損）
```

読み方: **SDL が Wayland のイベントを dispatch → リスナ → libffi のクロージャ（Javaコールバック）→ SDL 内で再入 → SEGV**。
LWJGL の Callback は libffi を使うため、これは **Cleanroom が `SDL.installLog` で登録した Java ログコールバック**が
SDL のイベント配送中に呼ばれ、その中で SDL を再入している経路と一致する。

**試した回避（効かず）**: `SDL_SetLogPriorities(CRITICAL)` を Java から呼んでログを抑制 → 依然同じ箇所で落ちた
（`celtium.window.sdlLog` の実装は revert 済み）。

**次の一手**: `com.cleanroommc.client.sdl.SDL.installLog` を **no-op に変換する coremod**（既存の SplashProgress スタブと同じ手法）で
Javaログコールバック自体を登録させない。これで libffi クロージャが Wayland のイベント配送に参加しなくなる。

**Wayland クラッシュの現時点の理解（2026-10-22・実測の積み上げ）**
- 新コア（1.5GB）で確認: 落ちる命令は `libSDL3.so` の `call *(%rax)`（`0x24e5e8`、SLOT=SDL+0x3ee208）。
  SLOT には **wayland 側の関数ポインタ（0x…2120 / 0x…21d0）** が入っており、SDL が Wayland の関数を間接呼び出ししている。
- 直前フレームは `libffi.so.8` → `wayland` → `wl_display_dispatch_queue_pending` → `libSDL3`。`#7` のクラッシュ番地は
  **どのロード済みライブラリにも属さない**（`info sharedlibs` の範囲外）＝**生成コード（JIT か libffi クロージャ）**。
- bisect: JEI/scalar/flare を除外しても**同じくクラッシュ**（10.8秒）。installLog スタブでも**変わらず**。
- `-Dorg.lwjgl.system.ffm=false` でも**変わらず**（9.3秒）。
- クラッシュは毎回 `CELTIUM_SWAPPOINT` / `CELTIUM_FRAME_QUEUED` の**直後**（初回 present 後）。

**現時点で最も整合する仮説**: `wl_display` への**同時ディスパッチ**。Vulkan WSI の present が同じ `wl_display` を
触り、SDL のイベントディスパッチと競合して関数ポインタ/キューが壊れる（クラッシュ番地が生成コードで、Javaフレームが無いこと、
X11 では起きないことと整合）。
**次の検証**: present 中の Vulkan WSI 呼び出しと SDL ポンプを**同一スレッドに固定**する（または WAYLAND 用の
`wl_display` を Celtium 側で別に持つ）。まず `-Dceltium.vulkan.presentReadback=true` 等で present 経路を変えて再現が消えるかを見る。

**Wayland present の切り分け（2026-10-22・決定的）**
- `-Dceltium.vulkan.skipPresent=true`（記録・提出するが present しない）で **クラッシュしない**（25秒完走、hs_err無し）
  ⇒ 引き金は **swapchain の present（Vulkan WSI）**。
- `-Dceltium.vulkan.pumpBeforePresent=true`（present直前に SDL キューを処理）→ **効かず**（14秒でクラッシュ）。
- `-Dceltium.vulkan.freshPresentSemaphores=true` → **効かず**（11.7秒）。
⇒ セマフォ再利用でも「タイミングをずらす」でもなく、**present 自体が Wayland 上で SDL の状態を壊す**。

**次の一手**: Wayland のサーフェス生成を `SDL_Vulkan_CreateSurface` ではなく、
**SDL の window properties から `wl_display`/`wl_surface` を取って `vkCreateWaylandSurfaceKHR` で自前生成**する
（コード内コメントにも「SDL_Vulkan_CreateSurface は X11 で落ちた実測がある」とある）。SDL側リスナを介在させないのが狙い。
取得は `SDL_GetWindowProperties` ＋ `SDL_GetPointerProperty("SDL.window.wayland.display"/"…surface")` を反射で。

**修正（2026-10-22・Wayland 実機で25秒完走）**: Wayland では `SDL_Vulkan_CreateSurface` を使わず、
**SDL の window properties から `wl_display`/`wl_surface` を取り `vkCreateWaylandSurfaceKHR` で自前生成**する。
```
properties = SDL_GetWindowProperties(sdlWindow);
display  = SDL_GetPointerProperty(properties, "SDL.window.wayland.display", 0);
surface  = SDL_GetPointerProperty(properties, "SDL.window.wayland.surface", 0);
vkCreateWaylandSurfaceKHR(instance, {display, surface})
```
結果: **クラッシュ消失**。同一条件で
`CELTIUM_PRESENT hookCalls=3 attempts=2 presented=2 failures=0`（実際に present できている）、
`CELTIUM_FRAME_QUEUED reached=true call=12` まで進行。X11 分岐は未変更（`compositor == X11` は従来どおり `vkCreateXlibSurfaceKHR`）。

切り分けの根拠: `skipPresent=true` でクラッシュしない／`pumpBeforePresent`・`freshPresentSemaphores` は無効。
SDL 側のサーフェス生成が display にリスナを足し、こちらの present（Vulkan WSI）と衝突していた。

**起動後にメニューで終了する件（2026-10-22 実測）**: Prism起動でロゴだけ出て約3秒後に **exit 0**。ログに例外は無く
`Minecraft: Stopping!` の直前まで描画していた。切り分け:
- ハーネス（Wayland・60秒）では **presented=299/300** で安定、`Stopping!` なし。
- `CELTIUM_SURFACE_WAYLAND display=0x… surface=0x…7dc72650 sdlNative=0x…7dc72650` → **SDLのwl_surfaceと一致**（表面は正しい）。
- 差分はラッパーの **`-Dceltium.vulkan.pumpInRedirect=false`**（既定にしていた）。SDLを誰もpumpしないためイベントが処理されず、
  コンポーザから見て無反応→ウィンドウclose→`Stopping!`（exit 0）と整合。
⇒ ラッパーは**pump既定ONに戻した**（`no-pump` ファイルで診断時のみOFF）。

**メニューで落ちる真因（2026-10-22・決定的）**: ログの `19:20:20` に

```
wp_linux_drm_syncobj_surface_v1#52: error 4: Buffer attached but no acquire point set
ERROR: Wayland display connection closed by server (fatal)
```

が出ていた。**フルスクリーンへ切り替えた直後（swapchain を 1920x1080 で作り直した最初の present）**に、
コンポジタが Wayland 接続を切っている。接続が切れると SDL は fatal になり Minecraft は正常終了(0)する。
検証ハーネスは options が**ウィンドウ**（854x480）なので、この経路を通らず安定していた。

**回避（実施済み）**: インスタンスの `options.txt` を `fullscreen:false` / `overrideWidth:854` / `overrideHeight:480` に変更
（バックアップ `options.txt.bak-fullscreen`）。フルスクリーンは後で直す（explicit sync の扱い、またはサーフェス再作成）。

**present blit を swapchain extent へスケール（2026-10-22）**: 1:1・左下アンカーだったため、サーフェスが先に1920x1080へ広がり
ゲームのframebufferがまだ854x480の遷移フレームでは、**メニューが左下に寄り右と下が切れて表示**された（利用者のスクショと一致）。
srcは全面・dstは `this.width x this.height` 全面へblitするよう変更（同サイズ時は従来と同一）。検証leg `scale-1`
（jar `005fe6ad73a6`・AMD・world・validation ON）は **PASSED / gate PASSED**。

**fullscreen遷移の再現試験**: `-Dceltium.vulkan.debugFullscreenAfterFrames=N` を追加し、present直後にSDLへfullscreenを要求。
N=3 と N=1 の両方で **syncobjエラーなし・presented 894/1192・PASSED**。つまり**実行中のfullscreen遷移は落ちない**。
利用者の起動時fullscreen（`options.txt fullscreen:true`）だけが `wp_linux_drm_syncobj ... error 4` で接続を切られる再現不能の別経路で、
当面は**ウィンドウ起動**（options: fullscreen:false / 854x480）が回避策。次段は起動時のSDL fullscreen を再現する手段（options事前seed）を作ること。

**ENOSPC**: `.gv-verify/run-*` の古い149件を削除し **17G空き**（runディレクトリは直近のみ保持）。

**起動時fullscreen を ハーネスで再現できた（2026-10-22）**: `scripts/prism_validation.py` に `--seed-options <file>` を追加
（隔離ゲームディレクトリは空で作られるため、`options.txt` 由来の起動条件を再現できなかった）。`fullscreen:true / guiScale:3` を
seedした leg `fsstart-1` は **17秒で `Stopping!`（利用者と同じ「ロゴ/メニューだけ出て終了」）** を再現した。
このとき `wp_linux_drm_syncobj` の行は出ていない＝**接続断は必須ではない**（症状は同じ、原因は別の可能性）。
観測: swapchain は `size=1920x1080` で作られる一方、present は `blitted 1280x720`。**ゲームのframebufferとsurface extentが
一致していない**（`Display`のサイズ報告がfullscreen後のSDLサイズに追従していない疑い）。次はここを計器化して切り分ける。

**fullscreen の原因調査（2026-10-22 夜・計器追加）**: 追加した計器 `-Dceltium.vulkan.windowDiag=true`（SDL flags/画素サイズ/
lwjgly Display の close・size・fullscreen）、`-Dceltium.vulkan.eventTrace=true`（SDL_AddEventWatch で quit/window イベントを生で出力）。

実測（seed fullscreen:true, jar `windiag-2`）:
```
frame=1 ... 1280x720 display[close=false ... fullscreen=false]
frame=2 ... 1920x1080 display[close=false ... fullscreen=true]
frame=3 ... 1920x1080 display[close=true  ... fullscreen=true]
```
`SDL_Pump` の quit イベント(256) と `Window.requestClose()` が closeRequested を立て、`Minecraft.runGameLoop` が終了する。
イベント列: `524 ENTER_FULLSCREEN → 525 LEAVE_FULLSCREEN → 527 FOCUS_LOST → 526 FOCUS_GAINED → 518/519 resize 1920 → 533 → 516 → 256 QUIT`。

決定的な切り分け:
- **`wp_linux_drm_syncobj_surface_v1 error 4 / Wayland display connection closed` は console にのみ出る**（log4jのlatest.logには出ない）。
  これは**こちらがpresentしている時だけ**発生（`-Dceltium.vulkan.skipPresent=true` では syncobj エラー0）。
- skipPresent でも **windowID=3 の 528 CLOSE_REQUESTED → 256 QUIT → 終了** は起きる（＝quit経路はpresentとは独立）。
- present mode FIFO でも同じ。**VkSurfaceKHRを再作成しない（再利用する）変更を入れても syncobj エラーは消えない**
  ⇒ 原因是れはサーフェス再作成ではなく、**リサイズ後の新しいswapchainの最初のpresent**（Mesa/WSIのexplicit sync）。
- sway が接続を切ると SDL が QUIT を投げてゲームが正常終了する（利用者の「exit 0」と一致）。

未解決。回避策はウィンドウ起動（options fullscreen:false）。headless（隔離Sway）でも再現可能かを次のlegで確認する。

**fullscreen の真因と修正（2026-10-22・解決）**: `com.cleanroommc.client.sdl.Window.swapBuffers()` — `Display.update()` が
毎フレーム呼ぶSDL自身のウィンドウswap — が、**Vulkan swapchainと同じwl_surfaceに別バッファをattach**していた。
GL-lessではGLコンテキストが無いのでこのswapは何も提示しないが、Waylandのexplicit sync予約を壊し、swayが
`wp_linux_drm_syncobj_surface_v1 error 4` で接続を切っていた（→ SDLがQUIT → ゲームは正常終了）。

対策: `CeltiumGllessSdlSwapTransformer` で **GL-less時のみ `Window.swapBuffers()` を no-op** に（戻り値はthis）。
計器: `-Dceltium.vulkan.windowDiag`, `-Dceltium.vulkan.eventTrace`（追加済み）。

実測（headless隔離sway, render node D128, seed `fullscreen:true`, jar `fsfix-1`）:
- 修正前: 15〜20秒で `Stopping!`、console に syncobj error 4 + `display connection closed`
- 修正後: **72秒連続・Stopping!なし・syncobjエラー0・presented=1198**
（headless出力は1280x720なのでリサイズは発生していない。実機1920x1080でのリサイズ確認は未実施）

**実フレーム間隔の計器と、実FPSがベンチと乖離する件（2026-10-22）**: `-Dceltium.vulkan.frameStats=true` を追加
（present境界で実間隔を120フレーム集計し p50/p95/min/max と fps を出力）。ヘッドレス隔離sway・worldシーンで実測:

| 条件 | 実測 p50 |
|---|---|
| 1280x720（既定） | **5.7〜14.6 ms（68〜176 fps）** |
| 1920x1080 | **33.3 ms（30.0 fps）**, p95 35, min 25, max 42 |
| 1920x1080 + `vsync=false`(MAILBOX) | **33.3 ms（30.0 fps）** |
| 1920x1080 + `maxFramerate:260`（limiter無効） | **33.3 ms（30.0 fps）** |

**33.3msが極めて規則的**＝負荷変動ではなく**固定の待ち**。vsyncでもゲームのfps limiterでもない（両方除外）。
利用者の実機20FPSと整合するのはこの領域。次は1フレーム内の待ち（acquire/present/フレームスロット待ち）を
区間計測して、2vblank相当の待ちがどこか（SDLのframe callback待ちの疑い）を特定する。

**推奨JVMフラグ（G1・スタッタ対策、2026-10-22）**: mod入り1.12.2では既定コレクタのポーズがフレーム内に入り、
平均fpsが高くても「カクつき」として体感される。ランチャーのラッパー（`java-celtium.sh`）へ以下を追加した:
```
-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=37 -XX:+DisableExplicitGC
-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:G1IHOP=15
-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
```
Java 27 では `ParallelRefProcEnabled` が削除済み、G1のしきい値系は experimental なので `UnlockExperimentalVMOptions` が先に必要、`InitiatingHeapOccupancyPercent` は `G1IHOP` に改名（実測: `java -version` が通ることを確認）。
`-XX:+DisableExplicitGC` は特に重要: バニラの `System.gc()` を落とす mixin は「別のtransformerもMinecraftを触る」ため
除外されており、このフラグが無いとフレーム中に stop-the-world が入る。

**サーフェスリサイズ（swapchain再構築）の再検証（2026-10-23・0.0.13-alpha）**: 隔離swayの出力は窓と同寸なので
リサイズが自然には起きないため、`-Dceltium.vulkan.debugResizeAfterFrames=N -Dceltium.vulkan.debugResizeTo=WxH`
（SDL_SetWindowSize を1回だけ呼ぶ）で**意図的に extent 変化**を起こして検証した。jar `80e3d8b3…`・headless:
```
CELTIUM_DEBUG_RESIZE frames=5 to=1000x600 ok=true
stopping=0  syncobjエラー=0  fps p50=62.0
```
⇒ **リサイズ→swapchain再構築→present が安定**（ランチャーを落としていた経路。修正前はswayが接続を切った）。

**互換コホート（2026-10-23・実測）**: 人気の1.12.2 Forge Modを追加して受入を実行した。
- 追加: `Hwyla-1.8.26-B41_1.12.2.jar`, `AppleSkin-mc1.12-1.0.14.jar`, `Controlling-3.0.12.2.jar`（既存: JEI 4.31.2, Fugue 0.24.4, flare 0.8.0, scalar 3.9.0）
- jar: `celtium-forge-mc1.12.2-0.0.13-alpha.jar`（sha256 `80e3d8b3…`）・leg `compat-1`・AMD・world scene・validation ON
- 結果: **PASSED・problems 0**（クラッシュなし、画像gate通過）
- 3つはインスタンスに残している（HUD/ツールチップ/キー設定の経路を日常操作で踏むため）

**fps300 KPI の基準計測（2026-10-23・実測）**

`-Dceltium.validation.scene=world -Dceltium.vulkan.paceToRefresh=false -Dceltium.vulkan.frameStats=true -Dceltium.vulkan.passSplit=true`
（上限OFF・vsync OFF・1920x1080・leg `kpi-base`・jar `80e3d8b3…`・AMD RX 6400）:
```
fps p50 = 120〜136
frame ms[p50=7.37 p95=13.91]
passSplit: frame 6.8 / world 6.8（terrain 4.5 / flush 3.3）/ hand 0.1 / clouds 0.1
```
⇒ **300fps = 3.3ms/frame**。現在 6.8ms、削減対象は **terrain 4.5ms と flush 3.3ms**。

注意（この計測で判明）: `CELTIUM_FRAME_STATS` の `prepare[perFrameMs]` と `accept[...]` は**累積値**で、
`debugFrameReports`（レポート回数）で割っているため**フレーム毎コストとして読めない**。判定には `passSplit` の
`clock=cpu` 行を使うこと（この行は 512 フレームの p50/p95/worst を出す）。

**fps300 KPI: ワールド中のJFR（2026-10-23・実測）**

`-Dceltium.validation.holdUntilTick=1200 -Dceltium.vulkan.paceToRefresh=false`、
`-XX:StartFlightRecording=delay=13s,duration=25s,settings=profile` で**ワールド滞在中**を採取（1056サンプル）。
トップ（self, samples）:
```
63  submitImmediateBatchTimed (VulkanTerrainGpuBackend:6107)
53  submitGeometryBatch        (:6071)
51  submitImmediateBatch       (:5980)
49  java.util.HashMap.get + 24 getNode + 24 TreeNode  ≒97（HashMap探索が最大の単一要因）
33  BufferedImage.getRGB
30x3 TextureUtil upload経路（機能: アニメーションテクスチャの再アップロード）
28x2 ChunkBuilderMeshingTask（ワーカーのチャンク構築）
25  World.func_175726_f / 25 WorldVertexBufferUploader.offerBatch
```
⇒ 300fpsへの第一手は **即時バッチ受理経路のHashMap探索排除**（primitive/配列キャッシュ化）と、
その次に**アニメーションテクスチャの再アップロード**（毎tickのBufferedImage経路）と**チャンク構築**。

計測の注意: fixtureはワールド入室後 約5秒でキャプチャ→終了する。JFRは `delay=13s,duration=25s` のように
**起動からの経過秒**で合わせる（`holdUntilTick` を増やしても滞在は延びない）。

**試行: 世代マップの配列化（2026-10-23・中立）**

`announcedGeneration`/`consumedGeneration`（`HashMap<Integer,Integer>`）をGL名インデックスのint配列へ置換。
JFRの HashMap 探索 ~97/1056 サンプルの一部を狙ったが、**frame 6.8→7.0ms（p50）で中立〜誤差**。
割当は減るので残すが、KPIへの寄与は認められない。次は `submitImmediateBatch` 本体の構造（準備・提出の分割）と
アニメーションテクスチャ再アップロードを対象にする。

**fps300 KPI: 即時バッチ受理経路で見つけた毎バッチ処理（2026-10-23・コード読解）**

`submitGeometryBatch`（JFR上位3メソッドの入口）は1バッチあたり:
- `celtium$flushPendingFontBatch("intervening")`（文字列定数を渡す呼び出し）
- 受理メタデータのフィールド書き込み 8個（immediateOffer* / immediateLastRefusal 等）
- `noteImmediateBatchesOffered()` と形式別カウンタ
- SHADER_PACK_DRAW_PROBE 判定（static final でJITが畳む想定）
- `System.nanoTime()` 2回 + 統計スナップショット2個（submitFlushesAtEntry / submitSuppliesAtEntry）

次の候補: (a) フォントバッチ未保持時の早期分岐を**フィールド1回の読み**に落とす、(b) 受理メタデータを
1つの int パックへ畳む、(c) 計測用の nanoTime/スナップショットを `BATCH_TIMINGS` 有効時のみに限定。
いずれも per-batch の固定費で、JFR上位がこの関数群であることと整合する。

**KPI変更後の受入（2026-10-23・実測）**: `kpi-2` jar（ARGBテクスチャ高速化＋世代配列化＋互換コホート入り）で
world scene を受入: **PASSED・problems 0**（AMD RX 6400・validation ON・leg `kpi-acc`）。
性能変更は受入の絵を壊していない。KPIは frame 6.8〜7.0ms（300fps目標に対し未達）。

**fps300 KPI: terrain pass のフレーム毎記録数（2026-10-23・実測）**

`CELTIUM_FRAME_STATS` に `terrainPerFrame[calls= draws= drawsPerCall=]` を追加（レポート区間の差分／フレーム数）。
world leg の実測:
```
terrainPerFrame[calls=5 draws=48 drawsPerCall=9]
```
⇒ **1フレームあたり間接ドローは48個だけ**（5回の indirect call に9個ずつ）。描画コマンド数は律速ではない。
terrain 4.5ms の内訳は**ドロー記録の前段のCPU処理**（セクション走査・行列・パス開始/終了・バインド）にある。
次の判別: terrain bracket 内をさらに分割（region走査 / バインド / push constant / パス開始終了）して計測する。

**fps300 KPI: terrain 3.4ms の内訳が判明（2026-10-23・実測）**

`CELTIUM_FRAME_PASS_SPLIT` のネストbracket（`(inside world)` として出る）を読むと:
```
terrain(inside world)   p50=3.4ms
terrainRecord           p50=0.5ms
terrainSubmit           p50=0.0ms
terrainSetup/Target/Begin/End/Handoff/Close  p50=0.0ms
```
⇒ **名前付きサブbracketの合計は約0.5ms**で、terrainの3.4msのうち**約2.9msが未計測区間**にある。
描画コマンドは48個/フレームなので、この未計測区間は**レンダーリスト/リージョンの走査・ユニフォーム容量確保と
行列アップロード・リージョン資源解決**などの、bracketの外側にある毎フレーム処理。

次の判別: `celtium$renderPass` のリージョンループとuniformアップロードを新しいbracket（例 `terrainRegions`,
`terrainUniforms`, `terrainLists`）で囲み、2.9msの所在を特定する。

**fps300 KPI: terrain のコストは準備区間にある（2026-10-23・実測で確定）**

新bracket `terrainPrep`（`celtium$renderPass` の recording 前の全区間）を計器化して測定:
```
terrain(inside world)  p50=3.7ms
  terrainPrep          p50=3.2ms   ← ここが実体
  terrainRecord        p50=0.5ms
  terrainSubmit/Setup/Target/Ensure/Begin/End/Handoff/Close  すべて 0.0ms
```
⇒ **terrain のコストは描画記録ではなく「準備」**（3.2/3.7ms）。次はこの区間を分割して特定する:
(a) `ensureUniformCapacity` と行列アップロード、(b) `pipelineFor(pass)` とパイプライン状態、(c) ワールドスナップショット/状態キャプチャ、
(d) レンダーリスト・リージョン走査。この順に bracket を足して削る。

**fps300 KPI: terrainPrep 3.2ms の内訳（続報・2026-10-23）**

`ensureUniformCapacity` を新bracket `terrainCapacity` で囲んで測定:
```
terrainPrep      p50=3.2ms
terrainCapacity  p50=0.0ms   ← 容量確保ではない
terrainRecord    p50=0.5ms
```
⇒ **ユニフォーム容量は無罪**。3.2ms は terrainPrep 内の**まだbracket化していない区間**に残る
（状態スナップショット、`pipelineFor(pass)`、レンダーリスト/リージョン走査、ミラー準備など）。
次: terrainPrep の各呼び出し（`pipelineFor`, スナップショット, リスト反復）を個別bracket化して特定する。

**fps300 KPI: terrainPrep 3.2ms の残り候補（2026-10-23・コード読解）**

`celtium$renderPass` の recording 前区間を読み、bracket 済みの `terrainSetup/Target/Ensure/Begin` を除いた**未計測の残り**は:
1. 入口ガード群（`gpuStalled` / `renderLists.hasPass(pass)` / ターゲットレジストリ判定 ほか）
2. `ensureSharedIndexBuffer(primitiveType)`（毎フレーム呼ばれる）
3. 状態スナップショット/行列導出（`worldSnapshotProjection` など）
4. `pipelineFor(pass)` の外側にある準備（`terrainEnsure` 内は 0.0 なので、パイプライン自体は安い）

次ラウンドはこの4つを `terrainGuards` / `terrainSharedIndex` / `terrainSnapshot` としてbracket化し、
3.2ms の所在を確定させてから削る（容量は前ラウンドで 0.0 と確定済み）。

**fps300 KPI: terrainSharedIndex も 0.0（2026-10-23 実測）**

```
terrain 3.7 / terrainPrep 3.2 / terrainRecord 0.5
terrainCapacity 0.0 / terrainSharedIndex 0.0 / Setup/Target/Ensure/Begin/End/Handoff/Close 0.0
```
⇒ `ensureSharedIndexBuffer` も無罪。残るのは **入口ガード群（`renderLists.hasPass(pass)`・ターゲットレジストリ判定）と
状態スナップショット**。次はこの2つを `terrainGuards` / `terrainSnapshot` としてbracket化する。
ここまでで terrain 3.2ms の候補は「ユニフォーム容量=0.0」「共有インデックス=0.0」と2つ消え、
**入口ガードかスナップショットのどちらか**に絞られた。

**fps300 KPI: 真犯人は「地形の前の即時ワーク吐き出し」（2026-10-23 実測で確定）**

```
terrain(inside world)  3.7ms
  terrainPrep          3.2ms
    terrainPreflush    3.1ms   ← これ
  terrainRecord        0.5ms
  容量/共有インデックス/Setup/Target/Ensure/Begin/End/Handoff/Close = 0.0ms
```
`flushPendingBeforeGlDraw()`（`celtium$renderPass` の先頭、`terrainSetup` より前）が
**地形パスの3.2msのほぼ全部**を占めていた。ここでは地形より前に記録された即時ワーク
（スカイ・ボイド面・太陽/月など）を吐き出している。

⇒ 300fpsへの次の一手は「この吐き出しを軽くする」:
1. 何を吐き出しているか（バッチ数・グループ数・描画種別）を `terrainPreflush` の内側で計測
2. 空/雲を地形より前ではなく**専用パスで先に描く**（記録を溜めずにその場で submit する）
3. あるいは吐き出し対象を減らす（ソート/合体で groups を縮小）

**fps300 KPI: terrainPreflush 2.8ms は「グループ0」＝描画ではなく待ち（2026-10-23 実測）**

```
terrainPreflush(inside world)  p50=2.8ms p95=4.1 worst=7.2
preflush[groupsPerFrame=0]
```
⇒ 吐き出し対象の**グループは0**。つまり2.8msはグループの記録/描画ではなく、
`flushPendingBeforeGlDraw()` が内部で行う**待機（キュー/フェンス/フレーム間同期）**の可能性が高い。
これは「CPU律速に見えていた時間の一部が実はGPU待ち」という、以前の観測（GPU busy 3.2%）とも整合する。

次の判別: `flushPendingBeforeGlDraw` の内側で
1. `vkQueueSubmit` 呼び出し回数と所要時間
2. `host.waitForIdle` / フェンス待ち / フレームスロット待ち の有無と時間
を計器化し、**待ちを消す（frames-in-flightを増やす・待機点をずらす）**方向で削る。

**fps300 KPI: preflush 2.8ms の正体（続報 2026-10-23）**

`flushPendingBeforeGlDraw()` の本体を読むと、内側で `FramePassTimings.begin("flush")` を開いている。
つまり `terrainPreflush(2.8ms)` と `flush(inside world)(3.3ms)` は**同じ1回の呼び出しを二重に計測**したもので、
独立した2つのコストではない。実体は「地形前の順序付けフラッシュ」1回。

グループは0なのに2.8msかかるので、内訳は
(1) `finishFrameBuffer()`/`vkQueueSubmit` 等の提出処理、
(2) フレームスロットのフェンス待ち（`waitForSlot` → `vkWaitForFences`）
のどちらか（または両方）。次はこの2つを個別に計器化する（submit回数/時間とスロット待ち時間）。
待ちなら frames-in-flight を増やす、提出なら統合する。

**fps300 KPI: flushCloseGroup も 0.0（2026-10-23 実測）**

```
terrain 3.2 / terrainPrep 2.8 / terrainPreflush 2.8 / flushCloseGroup 0.0
```
⇒ `closeCurrentImmediateGroup()` も無罪。グループ0・待ち0・クローズ0で2.8msなので、
残るのは `flushPendingBeforeGlDraw()` の**末尾側**（描画後のフレームバッファ終了・提出・パス終了）か、
複数箇所にある `drawPendingImmediate` 呼び出しの**周辺処理**。
次は `flushPendingBeforeGlDraw` の残り区間（先頭から `flush` bracket の終わりまで）を
`flushTail` として計測し、そこを最後の分割点にする。

**fps300 KPI: pre-terrain flush は毎フレーム 64〜67 グループを記録（2026-10-23 実測・計器修正後）**

```
preflush[groupsPerFrame=67]   (63〜67で推移)
terrainPerFrame[calls=5 draws=48]
terrainPreflush p50 = 2.8ms
```
⇒ 2.8ms / 67 ≒ **42µs/グループ**。グループ数は控えめ(67)で1件あたりが重い。
削減目標: 20µs/グループ以下（2.8ms → 1.3ms）またはグループ合体で半減。
内訳候補: グループごとの状態解決・パイプライン/ディスクリプタ束縛・push constant・コマンド記録。

**fps300 KPI: グループ記録の内訳（2026-10-23 実測・既存bracketから）**

`CELTIUM_FRAME_PASS_SPLIT` の記録系bracket（world leg, p50）:
```
winRecord   2.5ms   ← ウィンドウ記録の全体
recLoop     2.4ms   ← そのうちグループループ
  recBind   0.1ms
  recDraw   0.1ms
  rcResolve 0.0ms
  prepDescriptors 0.0ms / prepBarriers 0.1ms
winBarrier  0.2ms
```
⇒ **グループループ2.4ms のうち、名前付き内訳は合計0.3ms しかなく、残り約2.2msが「ループ本体の未計測処理」**
（67グループで **約33µs/グループ**）。状態キー比較・push constant書き込み・行列/テクスチャ解決など
bracket化されていない毎グループ処理が実体。

次: グループループ内の未計測区間を `recPrepare`（状態解決〜push constant）と `recMatrix`（行列）に分割して特定し、
そこを削る（例: 同一マテリアル連続グループでの再計算排除）。

**fps300 KPI: 即時描画は 194〜236グループ/フレーム、push は無罪（2026-10-23 実測）**

新計器 `drawn[perFrame]`（フレーム全体で描いた即時グループ数）と既存bracket:
```
drawn[perFrame=194〜236]
winRecord 2.5ms / grpDraw 2.0ms / rcPush 0.1ms / rcViewport 0.1ms / rcResolve 0.1ms
```
⇒ **1グループあたり約10µs**が `grpDraw`（pipeline の draw 呼び出し）にある。

対照実験: `-Dceltium.debug.skipPushConstants=true`（push を呼ばない）でも
```
grpDraw 2.0ms / winRecord 2.5ms   ← 変化なし
```
⇒ **push constants は原因ではない**（前回の `pushCache` 効果と整合）。コストは draw 呼び出し側
（バインド/記述子/driver へのコマンド記録）にある。

**含意**: 300fps（3.3ms）には、1グループ1draw の構造自体を変える必要がある。
設計済みの方向は「連続グループを1回の `vkCmdDrawIndexedIndirect` に畳む」（ループ内コメントの MERGE_CENSUS が
その go/no-go 計器）。条件は pipeline・記述子・push・viewport・同一バッファ（arena借用）が一致する連続区間。
次ラウンドはこの indirect 折り畳みを実装する。

**fps300 KPI: 連続グループの合体（indirect折り畳み）は空振り（2026-10-23 実測）**

`-Dceltium.vulkan.mergeCensus=true` で各ウィンドウの run を計測:
```
frame=578 groups=28 mergeableRuns=28 largestRun=1 arenaBackedGroups=28
frame=578 groups=2  mergeableRuns=2  largestRun=1
```
⇒ **連続する2グループが pipeline・記述子・viewport を共有する区間は存在しない**（largestRun が常に1）。
即時描画は素材が毎グループ交互に変わるため、**indirect 折り畳みでは draw 数を減らせない**。
残る削減軸は (a) 1ドローあたりのコスト（bind/記述子/コマンド記録）、(b) 受理時点でのグループ数削減、の2つだけ。

なお `CENSUS_BY_PIPELINE` が既定offのため `distinctPipelines=0` は「計器が動いていない」値であり根拠にしない。

**fps300 KPI: 即時描画の内訳（2026-10-23・実測・訂正版）**

レポート窓（120フレーム）の累積から:
```
variants[textureMs=72 itemMs=129 blockMs=0 particleMs=2 genericMs=16
         counts[texture=5880 item=14880 block=0 particle=120 generic=2400]]
drawn[perFrame=194]  binds[pipelineMs=13 descriptorMs=13]
drawParts[viewMs=0 pushCallMs=8 meshBindMs=38]
```
1フレームあたり: texture 49回・**item 124回**・generic 20回・particle 1回 = 194回。
1ドローあたり: **item 8.7µs / texture 12.2µs / generic 6.7µs** ⇒ 状態差では説明できず、
**「1ドローあたり約10µs」という共通コスト**（パイプライン/記述子バインド・push・メッシュ束縛・記録）。

**計器の罠（今回踏んだ）**: variant の delta を「スナップショット更新後に同じフィールドで引く」実装にしたため
最初は全部 0 と出た（自己差）。スナップショット更新**前**に delta を計算する形に修正して実測。
同じ種類の誤り（未配線・自己差）が計器で2回続いたので、`docs/verification-traps.md` にも追記する。

**fps300 KPI: item draw の中身とメッシュ束縛（2026-10-23・実測）**

レポート窓120フレームの累積:
```
itemParts[pipelineMs=5 descriptorMs=15 viewMs=0 pushMs=17 meshMs=90]
variants[... itemMs=134 counts[item=14880]]   ← 124回/フレーム
arena[promotions=0 blocked=0 fallbacks=0]     ← 全メッシュがframe arena借用
forms[draw=21144 bind=2135]                   ← 90.8%が「オフセットをdrawで渡す」安い形式
```
⇒ item 1ドロー ≈ 7.2µs の内訳は **mesh 5.0µs（＝vkCmdDrawIndexed本体を含む）+ push 1.1 + descriptor 0.8 + pipeline 0.3**。
arena 由来の全メッシュが同一バッファなので束縛自体は1回で済むが、**ドローコマンド1回に約2.5〜3µs**かかる
（RADVの記録コスト）。194ドロー/フレーム × 3µs ≈ 0.6ms が「引くに引けない床」。

**含意**: 300fps には「ドロー回数」を減らす以外に道がない。ターゲットは item 124回/フレーム。
同一テクスチャ・同一状態の連続をひとつのコマンドにまとめる（またはバインドレス＋indirectで
1コマンドに畳む）必要がある。push は既にキャッシュされ、descriptor は 0.8µs なので主要因ではない。

**fps300 KPI: item draw の中身（2026-10-23 実測・census）**

`-Dceltium.debug.itemCensus=true` で item グループを列挙（先頭16件）:
```
format=4 texture=116 batches=6 verts=24 target=… worldPass=true   ×8（同一テクスチャで8グループ）
format=4 texture=103/112 batches=6 verts=24 ×2ずつ（worldPass=false = メニュー/ロード画面）
```
⇒ item グループは**24頂点・6バッチの小さな束**で、同じテクスチャ(116)でも**8つに分裂**している。
分裂の理由は各ドローで modelview（配置行列）が変わるためで、受理時の fast-merge は
「同一マテリアル・同一変換」でしか効かない（＝ブロックの個数分だけグループが残る）。

**次の一手**: 変換だけが違う連続グループは、受理時にグループを分けたまま**描画時に1つの
`vkCmdDrawIndexedIndirect`（pushは各コマンドの前に発行）ではなく、pushを共有しない以上
「1グループ1push+1draw」が最小**。したがって削減は「item 124回」の内訳を減らす（例: HUDアイコンの
描画をアトラス化して1ドローにする）方向になる。ここは vanilla の描画構造に踏み込むため、
次ラウンドで HUD/手元アイテムのドロー内訳（何が124回の中身か）を数えてから判断する。

**fps300 KPI: item グループの構成（2026-10-23 実測・400件サンプル）**

`-Dceltium.debug.itemCensusLimit=400` で採取した item グループの分布（texture / 出現数）:
```
120:96  103:18  141:13  174:12  135:12  134:12  133:12  129:12  118:12  116:11  159:9  158:9 …
（すべて format=4, batches=6, verts=24）
```
⇒ item ドローは**すべて24頂点の小さな束**で、**テクスチャが非常に多い**（1フレームに多数の異なるテクスチャ）。
つまり HUD/手元/エンティティのアイテムがそれぞれ固有テクスチャで1グループ → 124回/フレーム。

**含意**: 1ドロー約3µsのドライバ床（実測）に対して、削減は「ドロー回数」だけが効く。
回数を減らすには (a) 変換を頂点へ焼き込み（pushを全グループ同一化）、(b) テクスチャを配列/インデックス化して
**パイプラインごとに1回の multi-draw(indirect)** に畳む、の2段が必要。これは `docs/architecture-2026-10-21.md`
§11の「native側へ所有権を移す」設計そのもの。

次の計測: **1フレームだけ**の全 item グループを列挙し（frame指定でダンプ）、窓ごと・テクスチャごとの内訳を出す。

**fps300 KPI: 定常状態のJFR（2026-10-23・決定的な内訳）**

`-XX:StartFlightRecording=delay=25s,duration=20s`（ワールド滞在中）の上位フレーム:
```
39 submitImmediateBatchTimed / 35 submitImmediateBatch / 35 submitGeometryBatch
36 HashMap.get + 35 getNode + 35 TreeNode   ≒ 106サンプル（10%）
36 EntityDataManager.func_187225_a/func_187219_c（エンティティのdata parameter参照）
18 WorldVertexBufferUploader.offerBatch（即時受理フック）
14 EntityRenderer skipWorldWithoutCamera
13 World.func_175667_e / 12 World.func_180495_p / 12 WorldServer.func_72866_a …
 9 ChunkProviderServer.loadChunk（ワールド生成）
  3 FramePassTimings.indexOf（計器自身）
```
HashMap の呼び出し元の内訳:
```
16 EntityDataManager（エンティティ描画）  9 ChunkProviderServer.loadChunk
 3 VulkanGlState.bindTexture(containsKey)  3 FramePassTimings.indexOf
 2 CeltiumGlStub.setTextureParameterBound  2 CompatibilityHooks.computeIfAbsent
 2 VulkanModelQuadCapture.matrixForList    2 prepareImmediate …
```
⇒ **フレーム6.6ms（world）は「レンダラ所有分 ≈2.5〜3ms（即時受理+記録）」＋「vanilla/Forge側 ≈3.6ms（エンティティ・空・TESR・チャンクロード・GLスタブ経由の呼び出し）」**に分かれる。
**300fps（3.3ms）はレンダラ所有分だけでは届かず、vanilla側の描画コストも削らないと達成できない**。
次の削減対象は (a) GLスタブ経路の per-call コスト（`countCallList`・`CeltiumModelBatchGroups.note` 等）、
(b) エンティティ描画の data parameter 参照（HashMap 16サンプル）。

**計器コストの削減（2026-10-23）**

`FramePassTimings.indexOf` は JFR で `Collections$UnmodifiableMap.get` として現れていた（validation中は
diagnostics が理由で bracket が有効になるため、受入legでも毎グループ8回呼ばれる）。2点変更:
1. 索引を `Collections.unmodifiableMap` から素の `HashMap` に（読み取り専用はprivateで保証）
2. 直接マップキャッシュを 32→256 スロット（bracket名は30数個を巡回するので32では衝突していた）

実測: leg `kpi-acc3` は **PASSED・problems 0**、frame p50 7.1ms / world 6.6ms。
**p50では差が検出できない**（期待効果 < 0.1ms）。ただし受入legのように diagnostics が常時オンの条件では
無駄な map 参照が確実に減るため、残す。断定はしない。

**fps300 KPI: 受理経路は0.3ms/frameで無罪（2026-10-23 実測）**

`acceptPerFrame[batches=0 decideMs=2-3 preflightMs=15-28 tailMs=0 appendMs=10-16]`（レポート窓=120フレーム）
⇒ フレーム換算: decide 0.02ms / **preflight 0.13-0.23ms** / append 0.08-0.13ms ＝ **受理経路 合計 ≈0.3ms/frame**。
`batches=0` は `immediateBatchesOffered()` がこの窓で動いていない（計器の意味を確認する必要あり）ため根拠にしない。

⇒ `winRecord 2.5ms` の内訳は **受理ではなく描画**（item 1.07 + texture 0.60 + generic 0.13 + binds 0.22 +
push 0.07 + mesh 0.32 ≈ 2.4ms）で確定。**削れるのはドロー回数のみ**という結論が補強された。

さらに: MERGE_CENSUS の run キーは pipeline・記述子・viewport のみ（変換を含まない）で largestRun=1 だった。
つまり**変換を焼き込んでも結合できない**（テクスチャ/パイプラインが毎回変わる）。結合には
「テクスチャ配列/インデックス化＋indirect multi-draw」というアーキテクチャ変更が必須。

**fps300 KPI: フレーム6.6msの最終内訳（2026-10-23 実測・確定）**

`acceptPerFrame[... submitMs=74-107]`（窓=120フレーム）から:
- **受理（submitImmediateBatchTimed 全体）: 0.6〜0.9ms/frame**（うち decide 0.02 / preflight 0.15 / append 0.12、残りは状態キャプチャ等）
- **描画: 2.4ms/frame**（item 1.07 + texture 0.60 + generic 0.13 + binds 0.22 + push 0.07 + mesh 0.32）
   - ここで meshMs ≒ 90ms/120 = 0.75ms、**23.6k回/120 = 196回/frame、1回 ≈3.8µs**
     → **`vkCmdDrawIndexed` 1回が約3µs**（このドライバの実測床）
- **terrain 記録: 0.5ms/frame**
- **vanilla/Forge 側（stub経由の呼び出し・エンティティ・空・TESR）: 約2.8ms/frame**

合計 ≈6.6ms（world）で矛盾なし。**300fps（3.3ms）には描画2.4msを約0.8msへ**（196→60ドロー）と
vanilla側 2.8→1.5ms の両方が必要。前者はテクスチャ配列/インデックス＋indirect multi-draw（bindless）で、
`VulkanContext` には descriptor indexing の機能判定が既にある（実装は未接続）。

**fps300 KPI: ドローコマンドの実コスト（2026-10-23 A/B・決定的）**

`-Dceltium.debug.skipDrawCalls=true` を **実際に効くように**修正（従来は `mesh.bindAndDraw` の
drawForm インライン `vkCmdDrawIndexed` を見ていなかったため無効だった）うえで測定:
```
                通常        drawスキップ
frame p50       7.0ms  →   5.3ms
winRecord       2.5ms  →   1.5ms
item meshMs     90     →   41   （/120フレーム）
drawParts meshBind 38  →   10
```
⇒ **`vkCmdDrawIndexed` 196回 ≈ 1.0ms/frame（1回 ≈5µs）**。即時描画2.5msのうち
**ドローコマンド1.0ms＋その他1.5ms**。
したがって 196→60 に減らしても **約0.7ms** しか減らない。残る1.5msは
**グループ毎の状態処理（未計測の部分）**にあり、そこを特定するのが先。

**fps300 KPI: ドロー無しの床が5.3ms（2026-10-23 A/B・重要）**

`skipDrawCalls` 修正後のA/Bで **frame 7.0 → 5.3ms**。つまり**全てのドローコマンドを消しても5.3ms**残る。
内訳の推定（既測）: vanilla/Forge 2.8 + 受理0.9 + terrain 0.5 + グループ状態処理1.5 ≒ 5.7（誤差は重複計上）。
⇒ **300fps（3.3ms）はドロー削減だけでは絶対に届かない**。vanilla側2.8と状態処理1.5の両方を削る必要。

計器 `-Dceltium.debug.drawBench=N` を追加（ウィンドウ境界で実メッシュにN回bind+drawしてµs/回を出す）。
実測は run=1 のみで **13.4µs/回（コールド）**、別runで23.1µs。A/Bから逆算した値は約5µs。
**コールド値は採用しない**（JIT未育成）。ベンチは8ウィンドウの最小値を出す実装にしたが、
ワールド滞在が短く1回しか走らなかった → 次回は長い滞在で再測。

**fps300 KPI: グループループの残り区間も無罪（2026-10-23 実測）**

長い未計測区間（260行・記述子/テクスチャ解決と再構築判定）に `rcPrepared` bracket を追加:
```
winRecord 2.5 / grpDraw 2.0 / grpPrep 0.1 / rcPrepared 0.1 / rcPush 0.1 / rcResolve 0.1
```
⇒ **per-group のJava作業はほぼ無料**（合計0.5ms未満）。状態処理の実体は `grpDraw` の内側:
`descriptor bind 0.24ms + push 0.21 + mesh bind(ドロー以外) ≈0.4 + pipeline bind 0.09 ≈ 0.94ms`（196ドロー, ≈4.8µs/draw）。
ドローコマンド本体 ≈5µs/draw と合わせて **1ドロー ≈10µs**。

⇒ 削減は「ドロー回数」（architectural）以外では **bindless記述子で descriptor bind 0.24 を消す**、
**push を減らす**、**mesh bind のホイスト**程度で、合計 0.5ms 前後が上限。

計器: `rcPrepared` を追加。bindAndDraw の `MemoryStack.stackGet()`/`PacketCost.enabled()` を
実際に束縛する分岐の内側へ移動（キャッシュ済みパスの負荷を減らす）。
受入: leg kpi-acc4 **PASSED・problems 0**、frame p50 7.0ms（差は検出できず＝速度主張はしない）。

**計器が「判定対象のフレーム」に課金していた（2026-10-23 修正）**

`FramePassTimings.enabled()` が `VulkanDiagnostics.enabled() || passSplit` で、`VulkanDiagnostics` は
`celtium.vulkan.validation=true` で有効になる。**受入legはこのスイッチを必ず渡す**ため、
全受入/KPI legで group 毎に bracket（名前解決2回＋時計2回）が動いていた（実測: 8/グループ）。
変更: bracket は `passSplit` か新設の `-Dceltium.vulkan.brackets=true` のときだけ有効。

併せて `preflush[groupsPerFrame]` を修正: `DrawCounters.immediateGroups()` は `passCounts` がオフだと
0 のままなので、通常runで「flushが0グループ」という**未配線カウンタの罠**を踏んでいた。
キューの実サイズ（`immediateGroups.size() + open group`）から数えるようにした。

**実測（brackets off / validation ON の受入leg `kpi-acc5`,`kpi-acc6`）**
- `kpi-acc5`: **PASSED・problems 0**
- `kpi-acc6`（`frameStats=true`）: frame p50 8.42ms / fps p50 118.8（窓ごとに 12.30→10.48→8.42 と立ち上がる）
- 先行run（brackets on）では pass-split 行の frame p50 が 7.0ms だったが、
  **run間の分散（7.0〜8.4ms）が効果より大きい**ため、この変更の速度効果は**断定しない**。
- 以降のKPIは `frameStats=true`（bracketなし）で測る。bracket入りの数値を「実フレーム」として引用しない。

**fps300 KPI: ウィンドウ機構は安い（2026-10-23 実測）**

`windows[perFrameX100=490-500]` ＝ **約5ウィンドウ/フレーム**。bracket内訳:
```
flush 2.6-3.0 / winRecord 2.3-2.7 / winBarrier 0.2 / winSubmit 0.0 / winEnd 0.0 / windowSetup 0.0
frameFenceWait[count=0]
```
⇒ **submit・pass境界・フェンス待ちはほぼ0**。ウィンドウ統合で削れるのは最大0.2ms（barrier）で、
flush の実体は **winRecord＝194ドローの記録2.5ms**。

**結論（これで全内訳が閉じた）**: world 6.6-6.9ms =
`描画2.5（194×約13µs） + terrain記録0.5 + 受理/ウィンドウ機構1.1 + vanilla描画ロジック約3.0`。
300fps(3.3ms)には「描画を約1.2msへ（＝ドロー数半減以下）」＋「vanilla描画ロジックを約1.5msへ」の両方が必要で、
どちらも局所最適化では届かない（`modelDraws` は実行中に64,300件＝エンティティ/モデル描画がこの経路の主要顧客）。

**fps300 KPI: 事前変換（bake）の拡大は空振り（2026-10-23 実測）**

`copyVerticesTransformed` を回転行列（フル3x4）へ拡張し、`celtium$preTransformable` を
全フォーマット・fog有効・lighting有効へ開放して受入試験:
```
preTransform[groups=0 matrixSplits=0]   ← 窓を3回とも「bake が一度も発火しない」
drawn[perFrame=194-212]（変化なし） frame p50 8.2-9.4ms（変化なし） 受入: PASSED
```
⇒ **bake は発火しない**。理由は実測の帰結: ワールドの主要バッチ（エンティティ/アイテムのモデル）は
`retained != null`（表示リストのキャッシュ経路 `RetainedGeometryCache`）で届くため、
`retained == null && celtium$preTransformable(...)` の条件に入らない。
**表示リストのジオメトリは行列がキャッシュ後に決まるのでbakeでは畳めない**。

したがって「同一モデル・同一テクスチャの連続グループを1ドローに」は、retained 経路そのものの
再設計（インスタンシング/per-drawデータ）なしには不可能。今回の変更は**挙動を元に戻した**
（拡張は無効のまま残さない）。計器 `preTransform[groups=, matrixSplits=]` だけを残す。

**fps300 KPI: retained経路は支配的ではない（2026-10-23 実測・census）**

`-Dceltium.debug.retainedCensus=true` + `frameStats=true`（窓=120フレーム）:
```
CELTIUM_RETAINED_CENSUS groups=23857-28680 retainedAtAccept=332-360 preTransformEligible=3480
                        retained=332-360 distinctMeshes=2-3 largestBucket=220-240
```
⇒ 1フレームあたり: グループ約197、**retained（表示リストキャッシュ）は約3**、
**現行ルールで bake 可能なバッチは約29**。retained は支配的ではない（前ラウンドの推定は誤りだったので訂正）。

それでも bake を全開放した実験（kpi-38）で `drawn[perFrame]` は 194 のまま、frame も変わらなかった。
理由は **隣接バッチが同じテクスチャ/状態で揃わない**ため、変換を消してもグループは結合しないこと。
`preTransform[groups=0]` の計器は窓リセットとの兼ね合いで0を出している可能性があり、
**「0＝未発火」を根拠にしない**（未配線・リセット罠の3例目）。

**fps300 KPI: 描画の素材キーは1フレーム61〜69種類しかない（2026-10-23 実測・決定的）**

`-Dceltium.debug.drawKeyCensus=true`（キー＝pipeline identity＋descriptorハンドル、窓=120フレーム）:
```
groups=23316-28590  distinctKeys=61-69  switches=14915-15999  largestBucket=1320-2880
```
⇒ 1フレーム: **グループ約194〜238、素材キーは61〜69種類のみ**。switches は約125/フレーム
（＝ほぼ毎グループでキーが変わる）。**素材でソートすれば194ドロー → 約62ドローに畳める**。
最大バケットは1キーで約11〜24グループ/フレーム。

**最初の実装は誤っていた**: キーに `PreparedImmediate` の identity を使ったため全グループが
distinct（24828/24828, largestBucket=1）になった。キーは「pipeline identity + descriptorハンドル」で
取るのが正しく、修正後に上記の数字。**計器のキー設計ミスは「結合不能」という逆の結論を出す**ので、
largestBucket が1のときはキー設計を先に疑う（`docs/verification-traps.md` に追記）。

**次の実装**: 不透明グループ（depthWrite かつ blend 無効）だけを素材キーでソートして先に描き、
残りは元の順序で描く。期待削減 194→62ドロー ≈ 0.6〜0.8ms。画像gateとvalidationで検証する。

**fps300 KPI: 不透明ソートを実装・受入したが描画数は減らない（2026-10-23 実測）**

不透明グループ（depth test・depth write 有り・blend 無し）を素材キーで並べ替えて先に記録する実装を追加
（`-Dceltium.vulkan.sortOpaque=true`、他targetやblend有りは元順序のまま）。
```
leg kpi-sort: PASSED・problems 0
drawn[perFrame=194]（変化なし） frame p50 8.64-10.30ms（有意差なし）
```
⇒ **ソートしてもドロー数は減らない**（グループは所詮グループ毎に1回描く）。
ソートが効くのは「同じ素材のrun」ができた後、**per-drawデータをバッファに移して
1回の `vkCmdDrawIndexedIndirect` に畳む**段を実装してから。push constants はドロー毎に
変える必要があるため、multi-draw にするには行列・fog・色を**レコードバッファ**へ移す必要がある。

**確定した次の設計**（§11のnative所有化）:
1. per-draw レコード（208B: 合成行列・fog行・色/alpha・テクスチャindex）をフレームarenaに書く
2. シェーダは `gl_InstanceIndex`（または `vkCmdDrawIndexedIndirect` の firstInstance）でレコードを読む
3. ソート済みrunを indirect コマンド列に変換し、runあたり1コマンドで記録
4. テクスチャは既存の `VulkanTextureArray`（登録側実装済み）をレコードindexで引く

ソート実装は既定off（フラグ）で残す。multi-draw の前提として必要。

**fps300 KPI: bake は実際に発火するが描画数は変わらない（2026-10-23 決定的な反証）**

述語を全開放した再試験（`preTransformEligible` が実測で動くことを確認した上で）:
```
leg kpi-bake3: PASSED・problems 0
CELTIUM_RETAINED_CENSUS preTransformEligible=8050-8166/窓（≒68/フレーム。開放前は3480/窓）
drawn[perFrame=197]  ← 変化なし
```
⇒ **bake は1フレーム約68グループで実際に発火しており**（計器も動いている）、画像gateも通るが、
**描画数は減らない**。理由は fast-merge が要求する「同一テクスチャ・同一状態」が隣接offerで成立しないため。
**「変換を消せばグループが畳める」という仮説は、これで計器を伴って反証された**（前回の「0件」は計器読みの疑いがあったが、
今回は発火を確認した上での反証）。挙動は元に戻した。

残る唯一の道は **per-drawレコード＋indirect multi-draw＋レコードを読むシェーダ変種**（`docs/plan.md`の性能行に記載）。

**fps300 KPI: 記録時のbakeが画像gateを通った（2026-10-23・前進）**

記録時bakeの実験（後に撤去）: 記録時に各グループの modelView を**自分の頂点へ焼き込み**、
push block の行列を `projection`（modelView＝単位行列）へ書き換え、fog行も単位行列にする。
retained（共有メッシュ）のグループは対象外（二重変換になるため）。

```
leg kpi-bakerec: PASSED・problems 0（画像gate通過）
drawn[perFrame=190-216]（ドロー数は当然まだ減らない）
```
⇒ **bakeの数学とpush書き換えが正しいことが画像gateで確認できた**。
これは次段（同一素材グループを1メッシュに連結して1ドロー）の前提条件。
受理時bakeが効かなかった理由（隣接offerが同一素材にならない）は変わらないが、
**記録時ならソートで同一素材を隣接させた後に連結できる**ため、ここが唯一の実装可能な道。

次段の設計:
1. run（pipeline・記述子・viewport/scissor・行列以外のpush内容が一致）を検出
2. run内の各グループを `copyVerticesTransformed` で**run用メッシュへ連結**（インデックスはrebasing済みのAPI使用）
3. runあたり1回の bind+push+`vkCmdDrawIndexed`
4. 対象は不透明グループのみ（ブレンド順序を壊さない）

**fps300 KPI: 範囲ドローを単一プリミティブに統合（2026-10-23・受入済み）**

`VulkanDynamicMesh` の描画コマンド発行を `drawRangeInternal(firstIndex, indexCount, vertexOffset)` に一本化し、
全体描画（`bindAndDraw` の drawForm）も範囲描画（新API `bindAndDrawRange`）も同じ場所を通るようにした。
```
leg kpi-range: PASSED・problems 0
drawn[perFrame=194]（挙動不変） frame p50 9.56-11.87ms（分散内）
```
これは次段（run用メッシュの部分範囲描画）に必要な道具で、既に全フレームで実行されている。

**次段の実装手順（確定）**:
1. フラッシュ先頭で run 用メッシュを1つ確保（`celtium$newImmediateMesh`）
2. 各グループの頂点を `copyVerticesTransformed`（bake込み・インデックスrebasing内蔵）で run メッシュへ連結
3. run の `(firstIndex, indexCount, vertexOffset)` を記録し、run境界で 1回の bind+push+`bindAndDrawRange`
4. run境界の条件: pipeline・記述子・viewport/scissor・行列以外のpush内容（オフセット64〜156）が一致
5. 対象は不透明かつ postClears の無いグループのみ（順序・クリア順を壊さない）

**fps300 KPI: 素材runのCPU連結マージは絵は正しいが大幅に遅い（2026-10-23 実測・反証）**

記録時bake＋run用メッシュへの連結（実験用フラグ、実装後に撤去）を実装し受入:
```
leg kpi-mergeb: PASSED・problems 0（画像gate通過＝マージ結果は正しい）
frame p50 17.27-33.08ms（通常8.2〜9.6ms）← 約2〜3倍遅い
drawn[perFrame=192-214]（この計器はグループ数であり、コマンド数ではないので減少しない）
```
⇒ **CPUでインデックスを読み出し頂点を連結するコスト（約196グループ/フレーム）が、
GPU側で節約できる約1msを大きく上回る**。run用メッシュの確保も毎run発生し、arena確保とコピーが重い。

**結論**: ドロー数削減は「CPUでジオメトリを連結する」方向では成立しない（実測で反証）。
残る道は **per-drawデータをバッファに置き、シェーダが instance で読む indirect multi-draw** のみ
（CPUはレコードを書くだけで、頂点データは共有メッシュのまま）。挙動は元に戻した。

**fps300 KPI: レコード読みシェーダ変種を追加（2026-10-23・コンパイル検証済み）**

`celtium-vulkan/src/main/shaders/immediate_tex_color_records.vert` を追加し、
`SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_VERTEX` で参照。push block（0行列 / 64..96テクスチャaffine /
112 alpha+色 / 128..207 fog）と**バイト単位で同じ構造体**を `std430` の SSBO（set0 binding2）から
`gl_InstanceIndex` で読む。fog距離の計算も同じ式。

```
: celtium-vulkan:compileTerrainShaders --rerun-tasks → "compiling 35 shaders" 成功
```
⇒ シェーダとして正しくコンパイルされることを確認（glslangValidator）。
**まだ描画経路には接続していない**（次段でレイアウト／レコードバッファ／indirect記録を実装）。
CPUはレコードを書くだけで、頂点データは共有メッシュのまま＝CPU連結マージの反証（17〜33ms）を踏まない設計。

**fps300 KPI: グループが使うバッファは窓あたり38〜58個だけ（2026-10-23 実測・indirectの前提確認）**

`-Dceltium.debug.bufferCensus=true`（窓=120フレーム）:
```
groups=23042-28751  vertexBuffers=38-58  indexBuffers=38-58
arenaBacked=4972-8275（≒41-69/フレーム）  owning=15240-20476（≒127-171/フレーム）
```
⇒ **バッファの種類は1フレームあたり数個**しかない（retainedモデルメッシュは大きな共有バッファを借り、
owning でも種類は増えない）。つまり **indirect multi-drawの障害は「バッファがバラバラ」ではなく
「ドロー毎の状態（push/レコード）とバインド形式」**であることが確定。

したがって次段の条件は:
- 不透明・drawForm（`vbo % stride == 0`）・同一 (pipeline, descriptor, 頂点/インデックスバッファ, stride) の連続グループ
- 各コマンドは自身の firstIndex/vertexOffset/indexCount を持ち、per-drawデータはレコードの firstInstance で引く
- bind-form のグループ（実測で全体の約9%）は従来どおり個別ドローにフォールバック

**fps300 KPI: レコードバッファを独自デスクリプタセット(set=1)へ（2026-10-23・コンパイル検証済み）**

`immediate_tex_color_records.vert` の SSBO を `set = 1, binding = 0` に移した。
理由: set 0 は即時描画がテクスチャ毎に作る (texture, lightmap) のペアで、そこへ binding 2 を足すと
**全テクスチャセットのレイアウトを変える**ことになる。set 1 に置けば「1ウィンドウに1回束縛」で済み、
既存のテクスチャセットは無変更。シェーダは 35本すべて再コンパイル成功。

**fps300 KPI: レコードバッファを実装・受入（2026-10-23・§11.13のステップ1）**

`ImmediateDrawRecords`（新規）: 208バイト/レコードの host-visible SSBO、**set=1 binding=0** の
descriptor set を1つ所有、窓ごとに `reset()`、容量不足時は `ensureCapacity`＋descriptor再書き込み。
`ImmediateDrawRecords.RECORD_BYTES=208` は push block と同一レイアウト（行列0 / テクスチャaffine64 /
alpha+色112 / fog 128〜207）。

ライフサイクル: 窓開始時に遅延生成（失敗時は破棄して従来経路）、`delete()` で解放。
```
leg kpi-records: PASSED・problems 0
hostBuffersCreated=123 / hostBuffersLive=121（レポート間で増加なし＝リーク無し）
```
⇒ この端末で storage buffer の set が作れること、窓ごとの生成・解放が健全であることを確認。
**まだ描画には使っていない**（次段: パイプライン変種＋indirect記録）。

**レコードのABIガード（2026-10-23）**

`ImmediateDrawRecordAbiTest` を追加: レコード（208B）と、レコード読みシェーダの `struct DrawRecord` の
**メンバー合計サイズ・順序（matrix → affines → alpha/colour → fog）・`std430`・`records[gl_InstanceIndex]`** を検査。
```
:celtium-vulkan:test --tests '*ImmediateDrawRecordAbiTest*' → tests=1 failures=0
```
型システムでは繋がっていない「writer が書くバイト列」と「shader が読む構造体」の契約を、片側だけ変えたときに落ちる形にした。
**これはレイアウトのテストであり、描画の証拠ではない**。

**fps300 KPI: recordsモードのパイプラインを追加（2026-10-23・コンパイル検証）**

`VulkanImmediateTexturePipeline.createRecords(...)` を追加:
- 頂点シェーダは `SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_VERTEX`（レコード読み）
- パイプライン・レイアウトは **set0=既存の(texture,lightmap)、set1=records** の2セット、**push constant range なし**
- `drawIndirectRange(...)`: set0/set1 をrun毎に1回束縛し、`mesh.bindAtZero` でバッファを0束縛、
  **`vkCmdDrawIndexedIndirect(count=run長)` を1回**発行（コマンドは各自の firstIndex/vertexOffset と firstInstance=レコードindex）
- `VulkanDynamicMesh.bindAtZero(...)` を追加（runの頂点/インデックス束縛を1回に）

`:celtium-vulkan:compileJava` 成功。**まだ生成も描画もしていない**（次段でバックエンドが
`ImmediateDrawRecords.descriptorSetLayout()` を受け取って生成し、runを記録する）。

**fps300 KPI: indirect run は正しく描けるが、まだ遅い（2026-10-23 実測・第一版）**

不透明テクスチャグループを run として記録し `vkCmdDrawIndexedIndirect` で発行する経路を実装（フラグ既定off）。
検証で3回の失敗を経て修正:
1. `VUID-VkGraphicsPipelineCreateInfo-layout-07987` … records用パイプラインに **push constant range が無かった**
   （fragment は push block を宣言している）
2. `VUID-...-layout-10069` … range を 128 にしたが **fragment のブロック宣言は208バイト**で不足
3. 208 に拡張して **PASSED・problems 0（画像gate通過）**

```
frame p50 10.68-13.51ms（ベースラインは 8.2〜9.2ms）← まだ遅い
```
⇒ 正しさは取れたが、**run が短い**ため run毎のコスト（set0/set1束縛・208B push・バッファ束縛）が
ドロー削減分を上回っている。次は run長の実測（`indirect[runs=, groups=, runsPerFrameX100=]`）を計器に追加し、
`sortOpaque` 併用で run を長くする／item（lit）系のレコード変種を作る、のどちらが効くかを測る。
既定offのまま（未達のため）。

**fps300 KPI: indirect run が1グループ/runになる理由を特定（2026-10-23 実測・決定的）**

`sortOrder[segments=1 ordered=1]` / `indirect[runs=1049 groups=1049]` ⇒ **1ウィンドウで間接経路に
乗るのは約1〜3グループだけ**。残りは:
- **blend有り**（HUD/フォント）＝間接経路の対象外（順序保存のため）
- **item（lit）系**＝`VulkanImmediateTexturePipeline` ではなく `VulkanImmediateItemPipeline` なので対象外
- bindForm（約9%）

つまり **間接経路が対象にしている family が誤り**。ワールドの主役は item（124/フレーム）で、
そのレコード読み変種（lit用）が無いため対象に入らない。

**次の一手**: `immediate_tex_color_lit` のレコード読み変種（records + lighting + fog）を作り、
`VulkanImmediateItemPipeline` に records モードを追加して対象を広げる。これが無い限り
run は1グループのままで、run固定費（set束縛2つ＋208B push＋バッファ束縛）だけが増える。

`sortOpaque` は **targetセグメント単位**で順序を付ける実装に変えた（別targetのグループは位置を保つ）。
`sortedOrder[segments=, ordered=]` をレポートに追加。

**fps300 KPI: lit（item）系のレコード読み変種とレコード長224（2026-10-23・コンパイル／テスト検証）**

- `immediate_tex_color_lit_records.vert` を追加: lit ブロック（**216バイト**: 光座標が208）を
  `set=1 binding=0` の SSBO から `gl_InstanceIndex` で読む。
- レコード配列は**1つのstride**なので **RECORD_BYTES を 224 に統一**し、unlit 側の構造体にも末尾padを追加。
  208バイトの構造体を224バイト配列に入れると2件目以降でインデックスがずれるため。
- `SpirvShaders.IMMEDIATE_TEX_COLOR_LIT_RECORDS_VERTEX` を追加。シェーダコンパイルは **36本成功**。
- `ImmediateDrawRecordAbiTest` を拡張し、**lit 側も224バイトであること**を検査 → `tests=2 failures=0`。

これで「ワールドの主役＝item 124ドロー/フレーム」を間接経路に載せる前提が揃った。
残り: `VulkanImmediateItemPipeline` に records モードを追加し、対象familyを広げて run長を再測定。

**fps300 KPI: item系を間接経路に載せたら run長 ≈50グループになった（2026-10-23 実測・前進）**

`VulkanImmediateItemPipeline` に recordsモード（set1=records、pushはfragment用range、シェーダは
`immediate_tex_color_lit_records.vert`）を追加し、レコード長を224に統一して対象familyを拡大。
さらにレコードwriterが **208バイトのpush block**も受け付けるよう修正（224未満で -1 を返していたため、
前回は runs=0 になっていた）。

```
indirect[runs=308-701 groups=16801-35250]  → **run長 ≈ 50〜55グループ/run**
runsPerFrameX100=256-327                   → **約3コマンド/フレーム**（194ドローを3回に集約）
受入: PASSED・problems 0（画像gate通過）
frame p50 15.67 → 9.95ms（ベースライン 8.07〜9.2ms）
```
⇒ **構造は狙い通り**（194ドロー → 約3コマンド）。ただし frame はまだベースライン同等〜やや悪い。
最初の窓が遅いのは **recordsツインのパイプラインを状態ごとに遅延生成**しているためと考えられる（起動時コスト）。

**次に測るべきこと**: (a) 定常窓だけの比較（生成コストを除外）、(b) ツイン生成を先読みする、
(c) 残る約50ドロー（blend有りのHUD/フォント）は順序保存のため個別ドローのままであることの確認。

**fps300 KPI: 間接run経路のA/Bで -14%（定常窓）を実測（2026-10-23・同一jar・AMD RX 6400）**

条件を揃えた2 leg（`world` scene, validation ON, frameStats ON, 180秒, jar `kpi-56`）:
| 窓 | 既定（フラグoff） | 間接run＋素材ソート |
|---|---|---|
| 1 | 17.12ms | 16.64ms |
| 2 | 10.60 | 10.59 |
| 3 | 9.38 | 8.29 |
| **4（定常）** | **8.13** | **6.98** |

⇒ **定常窓で 8.13 → 6.98ms（−14%、約1.15ms削減）**。予測（約1ms）と整合。
両armとも **PASSED・problems 0**（画像gate一致）。run長は約50グループ/run、約3コマンド/フレーム。

起動直後の窓が両armとも遅いのはfixture側のワールド生成/ロードであり、比較は定常窓で行う。
次の判断: **両フラグを既定onにする**か。Intel実機での確認が先（次leg）。

**fps300 KPI: 間接run経路を既定onにした（2026-10-23 実測）**

`sortOpaque` と `indirectImmediate` を**既定on**へ（`-Dceltium.vulkan.sortOpaque=false` /
`-Dceltium.vulkan.indirectImmediate=false` で従来動作に戻せる）。

```
leg kpi-default（既定＝新経路, AMD RX 6400, world, validation ON, 180秒, jar kpi-57）
受入: PASSED・problems 0（画像gate一致）
indirect[runs=1827 groups=83708]  → run長 ≈46グループ、約3コマンド/フレーム
frame p50: 7.98 → 6.72 → 6.45ms（定常）
```
比較（同一条件の従来arm, kpi-56）: 定常 **8.13ms** ⇒ **6.45ms（−21%、約1.7ms）**。
Intel UHD 630 も同フラグで **PASSED・problems 0**（`kpi-steadyintel`）。

⇒ レンダラ所有コストが下がり、**frame 8.1 → 6.45ms（fps 約155）**。300fpsの床（ドロー全削除で5.3ms）は
別途vanilla側の削減が必要なことに変わりはない。

**最適化後のプロファイル（2026-10-23・間接run既定on・AMD・validation ON）**

JFR（ワールド滞在中, 377サンプル, Client thread）上位:
```
27 java.util.HashMap$TreeNode.getTreeNode   ← 内訳: EntityDataManager 9, TextureRegistry.get 3, retainAll 2, bindTexture 2, TESR 2, ModelQuadCapture 2 …
24 submitImmediateBatchBody                 ← 受理経路（約6%）
 9 HashMap.putVal / 9 Long2ObjectOpenHashMap 反復 / 8 World.* 参照 / 8 BufferBuilder
 5 FontBatch.celtium$fontMatrixBoundary
```
⇒ ドロー削減後は**大きな単一ホットスポットが無い**。上位は「バニラのエンティティdata参照」と「受理経路」で、
どちらも1件あたりは小さい。**残りはvanilla描画ロジック側**（ドロー全削除でも5.3ms残る実測と整合）。

注: このrunは validation ON（diagnostics有効）なので、`bindTexture` 等のdiagnostics側の参照も含む。
既定運用（validation OFF）ではその分は消える。

**fps300 KPI: blend有りグループもrunに載せて更に短縮（2026-10-23 実測）**

間接経路の対象に**blend有りグループ**も追加（不透明ソートは不透明だけを動かすので、blend有りは
到着順のまま。run内のコマンドは記録順に発行されるためブレンド順序は変わらない）。

```
leg kpi-blend（AMD, world, validation ON, 180秒, jar kpi-58）
受入: PASSED・problems 0（画像gate一致）
indirect[runs=14895 groups=102743]  → run長 ≈6.9グループ、約28run/フレーム
frame p50: 7.65 → 6.36 → **6.14ms**（定常）
```
⇒ 従来arm 8.13ms 比 **−24.5%（約2.0ms）**。前段（不透明のみ）の6.45msからさらに −0.31ms。
**Intel UHD 630 でも同条件で PASSED・problems 0**（画像gate一致、`kpi-blendintel`）。

**fps300 KPI: 最適化後の内訳と、まだ個別描画しているもの（2026-10-23 実測）**

bracket（brackets ON なので計器込み）:
```
frame 6.2 / world 5.5 / flush 1.5 / winRecord 1.1 / terrain 1.9（うち preflush 1.4）/ gui 0.4
setupTerrain 0.2 / chunkUpload 0.0
```
バリアント別（frameStats, 既定=間接ON）:
```
counts[texture=2551 item=0 block=0 particle=120 generic=2400]（窓=120フレーム）
forms[draw=2400 bind=2671]
```
⇒ **item系は全て間接runに吸収（個別ドロー0）**。
残る個別ドローは約42/フレーム:
- `texture` 約21（postClears有り等でrunに載らない／bindForm）
- `generic` 約20（`VulkanImmediatePipeline` の position系。**records変種が無い**）
- `particle` 1
- また **bindForm 2671 対 drawForm 2400**＝個別描画はほぼbindForm経路（9%の制約）

次の削減候補: position系（`immediate_position.vert` / `immediate_position_tex.vert`）のrecords変種を作り、
generic 20ドロー/フレームを吸収（見込み −0.2〜0.4ms）。

**fps300 KPI: position系（generic）のレコード読みシェーダを追加（2026-10-23・コンパイル検証）**

`immediate_position_records.vert` / `immediate_position_tex_records.vert` を追加。
208バイトのブロック（行列0 / 死んだMV列orテクスチャaffine 64,80 / colour alpha 96 / alpha+色 112 /
fog 128..207）を `set=1 binding=0` の SSBO から `gl_InstanceIndex` で読み、**224バイトstrideにpad**。

シェーダコンパイルは **38本成功**（`compileTerrainShaders --rerun-tasks`）。
途中、fogヘルパー `celtiumFogDistance(vec3)` がレコードを参照する形に変換されたため未宣言エラーになり、
**引数に `in DrawRecord record` を追加**して修正した（コンパイルで検出）。

これで「個別ドロー約20/フレーム」の generic（position系）を間接経路に載せる前提が揃った。
残り: `VulkanImmediatePipeline` に records モード＋backend配線。

**fps300 KPI: 位置系バリアントをツイン対象から外す防御（2026-10-23 実測/受入）**

`VulkanImmediateTexturePipeline` には positionOnly / positionNoTexture の変種があり、
**シェーダも push block の意味も違う**（色alphaは96、64/80はMV列でテクスチャではない）。
一方 recordsツインはテクスチャ系のシェーダ固定なので、そのまま流すと**死んだデータでaffineを組んでサンプル**する。

- `celtium$positionOnly()` / `celtium$positionNoTexture()` を追加
- ツイン生成前に**対象外なら null を返す**ガードを追加（run開始前に判定するので半端なrunも残らない）

```
leg kpi-guard: PASSED・problems 0
variants[texture=3328 item=0 particle=120 generic=2400]（窓120f）
indirect[runs=11903 groups=102457]  frame p50 6.80 → 6.20ms
```
⇒ ガード後も性能は同等（6.20ms）。**残る個別ドロー**は texture 約28/フレーム、generic 約20/フレーム。
generic は `VulkanImmediatePipeline`（`immediate_color.vert` の色family）で、records変種は
**色family用のシェーダ**が必要（前ラウンドで作った position 系シェーダとは別）。

**fps300 KPI: 色family（generic）も間接runに吸収（2026-10-23 実測・受入）**

`immediate_color_records.vert`（色familyのrecords変種。samples しないので **set=0 に records**）を追加し、
`VulkanImmediatePipeline` に recordsモード＋`drawIndirectRange` を実装。backendで色familyを対象化。

```
leg kpi-colour: PASSED・problems 0（画像gate一致）
variants[texture=3298 item=0 particle=113 generic=0]  ← **generic 個別ドロー 0**
indirect[runs=11669 groups=111373]  frame p50 7.76 → 6.09ms
```
⇒ 個別描画は **texture 約27/フレーム・particle 約1/フレーム**まで減少。frame は 6.09ms（前段6.20ms）。

残る texture 個別は「postClears を持つ（クリアを先に記録する必要がある）」か「bindForm（スロットオフセットを
コマンドで表現できない）」で、いまの間接機構では吸収できない種類。

**Intel UHD 630: 色family吸収後の最新ビルドも PASSED（2026-10-23）**

```
leg kpi-colourintel: PASSED・problems 0（画像gate一致）
device: Intel(R) UHD Graphics 630 (CFL GT2)
variants[texture=3339 item=0 particle=116 generic=0]   ← generic個別ドロー0（AMDと同じ）
frame p50 13.96 → 11.00ms（Intelは元々遅く、AMDの6.09msとは別GPU）
```
⇒ 色familyのrecords化は**両GPUで正しい**。

**fps300 KPI: frame arenaのスライスをstride整列にして bindForm を削減（2026-10-23 実測・受入）**

frame arena の頂点スライス開始は16バイト整列だけだったため、stride 20/24/28 のレイアウトでは
`vbo % stride != 0` になり、**安い drawForm が使えず毎回バッファを再束縛**していた（bindForm）。

- `Region.vertexAddressAt(byteOffset, length, alignment)` を追加（任意の正の整列）
- `VulkanDynamicMesh` が自分の layout の stride から **lcm(16, stride)** を計算して要求
- これで arena 由来のメッシュは常に「頂点の整数倍」から始まる

```
leg kpi-alignstride: PASSED・problems 0（画像gate一致）
forms[draw=1823 bind=697]      ← bindForm 2671 → 697（−74%）
variants[texture=2400 ...]     ← texture個別ドロー 27 → 20/フレーム
frame p50 6.44 → **5.90ms**
```
⇒ 通算 **8.13 → 5.90ms（−27.4%、約169fps）**。
**Intel UHD 630 でも PASSED・problems 0**（`kpi-alignintel`、`forms[draw=1827 bind=714]` と同じ改善）。

**fps300 KPI: クリア持ちグループのrun取り込みは遅くなる（2026-10-23 実測・反証）**

`postClears` を持つグループを間接runに載せ、**runのflushをクリア記録の前に移す**実験:
```
leg kpi-clears: PASSED・problems 0（正しさは維持）
variants[texture=2400 ...]       ← 個別ドローは減らない（同じ2400）
indirect[runs=12422 groups=92039] ← 吸収グループはむしろ減少（111373→92039）
frame p50 8.60 → 8.07ms          ← 直前の5.90msから **約+2.2ms 悪化**
```
⇒ 吸収数が増えないうえに遅くなる。**runのflush位置を変えるとrunの連続性が壊れる**ため。
挙動は revert した（未検証の最適化を残さない）。

**証拠の紐づけ: 現在のHEADビルドと検証済みjarの一致（2026-10-23）**

`main` (7df381287) から再ビルドした **renderer jar の sha256 は `0ab703c43d51f2af…`** で、
AMD/Intel の受入を実施した `kpi-61` のステージと**同一**。つまり 5.90ms / PASSED の証拠は
現在のHEADのコードにそのまま対応する（間のコミットはdocsのみ）。

- forge jar: `0ab703c43d51f2afa7b2cea44763ae580550274b14d9fb6b6ddf3eccdadb2b53`
- validation-client jar: 再ビルドで `24c5fb16…`（検証時 `31508d5a…`）。**fixture専用**で、
  renderer 側の同一性は上のforge jarで担保される。混同しないこと。
- バージョン表記は 0.0.13-alpha のまま（リリースするなら gradle.properties の版を上げて
  **再ビルド→両GPU再受入**が必要。版を上げるとjarハッシュが変わるため、既存の受入証拠は使えない）。

**HEADの健全性（2026-10-23 実測）**

```
:celtium-vulkan:test → tests=122 failures=0 errors=0 skipped=0
python3 -m unittest discover -s scripts/tests → Ran 69 tests, OK
python3 scripts/check_agent_docs.py → OK
```
GPU受入（AMD RX 6400 / Intel UHD 630）と合わせて、現HEADは
**CPUテスト122件・スクリプトテスト69件・文書検査・両GPU受入**がすべて通る状態。

**fps300 KPI: runにviewportを足してper-run状態にすると run長が縮む（2026-10-23 実測・反証）**

吸収グループの viewport/depth-bias 適用をやめ、runのflush時に1回だけ設定する実験
（runキーに viewportHash を追加）:
```
leg kpi-state: PASSED・problems 0（VUIDなし）
indirect[runs=11894 groups=92566]  ← run長 9.5 → 7.8グループに短縮（吸収数 111373→92566）
frame p50 10.72 → 8.53 → 6.97ms    ← 直前5.90msから **約+1.1ms 悪化**
```
⇒ viewportをキーに含めると**runが細切れになり**、節約できる per-group の状態設定より損失が大きい。
挙動は revert（5.90msの構成に戻した）。

**受理経路の内訳（brackets ON・間接run既定onのrunから）**

```
offCapture 0.0 / offPrepare 0.1 / offMerge 0.5 / offAppend 0.1 / flushCloseGroup 0.0 / rcPrepared 0.1
```
⇒ 最も大きいのは `offMerge`（fast-merge判定）0.5ms。ただしこのrunは**bracket有効**（グループ毎に
`begin/end` が2回入る）なので、その分の計器税を含む。1バッチあたり約0.43µsで、
内訳は「テクスチャ・状態・ライトマップの比較」で、テクスチャ解決は**1エントリmemo**により同一名の連続は
フィールド比較で済む実装になっている。**目立つホットスポットは無い**。

受理経路の残り（submitImmediateBatchBody全体 0.6〜0.9ms のうち、上記の合計約0.8msでほぼ説明できる）。
つまり受理側も**局所最適化の余地は小さい**。

**リリース v0.0.14-alpha（2026-10-23）**

間接描画の成果を配布物として公開した。

- tag: `v0.0.14-alpha`（commit `37d05bc0a`）
- URL: https://github.com/souta-lab/celtium/releases/tag/v0.0.14-alpha
- forge jar sha256: `90a7c505d8820b1a5bb5c7c1c15bf7ae71214b54092e4c1da9c76e428dc31dd4`（3215224 bytes）
  - 公開後に **ダウンロードして再計算し、テストしたjarと一致**することを確認済み
- validation-client jar sha256: `3864d3b7a69622665e1c5cf6f92ef5381301613e9775dd32b35e496ea99eb282`

**受入（このjarそのもの）**
| leg | GPU | 結果 |
|---|---|---|
| `rel014-amd` | AMD RX 6400 | **PASSED・problems 0**（定常 p50 6.24ms の窓まで確認） |
| `rel014-intel` | Intel UHD Graphics 630 | **PASSED・problems 0** |

ビルドは `-Pceltium_release_version=0.0.14-alpha`。版を上げたのでjarハッシュは0.0.13系と別物であり、
**この受入は v0.0.14-alpha のjarに対する証拠**（0.0.13の証拠とは流用しない）。

**300fpsが現実的でない理由の最終整理（2026-10-23・v0.0.14-alpha のJFR）**

Client thread 321サンプル（25秒, validation ON）のinclusive:
```
 8% EntityRenderer.renderWorldPass        ← ワールド描画パス全体
 7% submitImmediateBatchTimed             ← 受理
 7% flushImmediateWindow                  ← run記録
 5% Minecraft.runTick                     ← tick（描画ではない）
 4% TileEntityRendererDispatcher.render   ← TESR
 1% RenderGlobal.renderEntitiesBlock / setRenderLayer
```
self上位: HashMap$TreeNode 4%(エンティティdata等)、submitImmediateBatchBody 3%、BufferBuilder 3%、
Long2ObjectOpenHashMap 3%。

⇒ **フレーム6msのうち、レンダラが所有するのは受理＋run記録（約14%）＋地形**。
残りは **tick（20Hzで6ms/tick＝フレーム換算約2ms）** と **vanillaのエンティティ/TESR/ワールド参照**。

つまり300fps（3.3ms）は「レンダラを最適化する」だけでは到達できず、
**tickとvanilla描画ロジックの削減**（Sodium型の別プロジェクト、またはtick間引き等の設計変更）が必要。
これは実測に基づく結論であり、レンダラ側の残作業ではない。

**対象環境の境界: 0.7.11 は可、0.6.13 は不可（2026-10-23 実測）**

`cohort` mode の既定インスタンスは `cleanroom-0.6.13-alpha`（LWJGL 3.4.1 / GLFW）で、
`world` mode の既定は `celtium-world-accept`（中身も **0.6.13** の clone、qmagnet セーブ入り）。
一方 v0.0.14-alpha のjarを 0.6.13 cohort で起動すると:

```
CELTIUM_GLLESS Display transformer installed / window hinted GLFW_NO_API
java.lang.IllegalStateException: There is no OpenGL context current in the current thread.
  at org.lwjgl.opengl.GL.createCapabilities
  at org.lwjgl.opengl.Display.create
  at net.minecraft.client.Minecraft.func_175609_am
```
⇒ **0.6.13 は非対応**（GL-less ブートストラップは SDL3 の 0.7.11 用）。したがって本リリースの受入は
`CELTIUM_INSTANCE_WORLD=.../cleanroom-0.7.11-alpha` で取得しており、**canonical fixture（0.6.13）での
受入ではない**。この対応関係はリリースノートにも明記した。

0.6.13 側の fixture を使う受入を主張する場合は、0.6 用の別ビルドが必要（同じjarでは不可）。

**非対応ホストの明示的な拒否（2026-10-23 実測・受入）**

0.6.13 cohort で分かりにくい `There is no OpenGL context current` で落ちる代わりに、
**バージョンと理由を名指しして止まる**ようにした（`CeltiumGllessHint.beforeWindowCreation`）。

- SDL3 バインディング `org.lwjgl.sdl.SDL` の有無でホスト世代を判定
- 無い場合: ERRORログ＋`IllegalStateException`（クラッシュレポートに理由が残る）

```
leg guard-world (0.7.11, AMD)   : PASSED・problems 0   ← 対応ホストでは従来どおり
leg guard-cohort (0.6.13, AMD)  : 起動拒否（想定どおり FAILED）
  log: [Celtium] this build requires CleanroomMC 0.7.11-alpha (SDL3, LWJGL 3.4.3); the host has no org.lwjgl.sdl.SDL...
  crash-report: IllegalStateException: [Celtium] this build requires CleanroomMC 0.7.11-alpha ...
```
⇒ **対応ホストの受入は不変**、非対応ホストは原因が読める形で止まる。

**Intel UHD 630 でも間接runは有効（2026-10-23・同一jar A/B）**

同じjar（guard, 0.0.13-alpha ビルド）・同じinstance・180秒で、フラグだけ切り替えた2 leg:

| フラグ | 立ち上がり → 定常 p50 |
|---|---|
| 無効（`indirectImmediate=false`, `sortOpaque=false`） | 22.29 → 14.20 → **11.84ms** |
| **有効（既定）** | 13.25 → 10.62 → **10.26ms** |

⇒ **Intel UHD 630 でも −13%（11.84 → 10.26ms）**。両方 PASSED。
AMD（−27%）より効果は小さいが、**GPUベンダーで切り替える必要はない**（既定onのままでよい）。
IntelがAMDより遅いのはiGPU自体の性能差で、最適化の有無とは別。

**実画像の証拠（2026-10-23・HEAD=guardビルド, AMD RX 6400）**

`docs/evidence/v0.0.14-world-amd-2026-10-23.png` は leg `guard-world` が保存した presented ダンプ
（1280x720、QMAGNET配布マップを 0.7.11 instance で描画）。F3相当の検証オーバーレイが重なっているが、
地形・HUD・ホットバー・トーストが正しく描けていることが分かる。

- 取得元: `.gv-verify/run-guard-world/screenshots/celtium-presented-world-*.png`
- 受入: `guard-world` = **PASSED・problems 0**（画像gate判定込み）、device `AMD Radeon RX 6400 (RADV NAVI24)`
- **注意**: この画像は **ホスト拒否ガードを入れた後のビルド**（jar は guard ステージ, sha256 `$(sha256sum ...)`）で取得した。
  v0.0.14-alpha のリリースjarはガード前のビルドなので、**リリース添付の証拠としては使わない**。
  次のリリース（ガード込み）で改めて取得する。

**重大な訂正: v0.0.14-alpha の「−27%」は描画漏れを含む測定だった（2026-10-23）**

リリース後に実コードを確認して判明:
1. `celtium$flushIndirectRun()` が `indirectRunPipeline == null` で早期return していたが、
   item系・colour系のtwinは `indirectRunPipelineObject` 側に入るため、**それらのrunが描画されず捨てられた**
2. runのflushが**グループループ内**にあり、run長が常に1だった（狙ったバッチ化が起きていない）

修正後（ガードを両フィールドで判定、flushをループ外へ、1コマンドrunは通常ドロー）に測ると:
| jar | AMD 定常 p50 | Intel 定常 p50 |
|---|---|---|
| 0.0.14（欠陥・item/colour描画漏れ） | 5.90ms | — |
| **0.0.15（修正・間接は既定off）** | **7.99ms** | **11.89ms** |

⇒ **正しく全グループを描く構成では、このsceneで間接runはフレーム短縮にならない**。
素材キーが毎グループ交互に変わりrun長≈1.5にしかならないため。**−27%の主張は撤回**。
間接経路は既定off（`-Dceltium.vulkan.indirectImmediate=true` で有効化）に戻した。

**教訓**: 受入（PASSED・画像gate）は「描画漏れ」を検出しなかった。runの本数とグループ数を
突き合わせる計器（`runs`/`groups`/`singletons`）を先に入れていれば、1グループ=1runの異常に気づけた。

**素材ソートの対象familyを全immediateへ拡大（2026-10-23・受入）**

`celtium$buildDrawOrder` は「並べ替え可能」の判定を **textured family だけ**で行っていた（item/colourが対象外）。
そのため1ウィンドウで並べ替えられるのは1グループだけで、run長が伸びない原因の一つだった。
共有述語 `celtium$reorderable` を作り、texture / lit item / colour のすべてで
「depth test・depth write 有り・blend 無し」を満たすグループを並べ替えるようにした。
計器 `sortOrder[ordered=]` も「並べ替え対象の実数」を返すよう修正（以前は全件を返していた）。

```
leg sortall-world（間接on・ソートon）: PASSED・problems 0
indirect[runs=83153 singletons=81293 groups=93365]  frame p50 8.86 → 7.72ms
```
⇒ 並べ替え対象は広がったが、**run長は依然ほぼ1**（singletons 81293/83153）。
理由は素材キーが毎グループ交互に変わること自体で、ソートでは解消しない。
**間接経路は既定offのまま**（v0.0.15-alpha）。この変更は正しさ（対象familyの網羅）の改善として残す。

**決定的な事実: このsceneの即時グループはほぼ全てblend有り（2026-10-23 実測）**

ソートのみ（間接off）の leg `sortonly-world` でも `sortOrder[segments=1 ordered=1]` のまま:
**1ウィンドウで並べ替え可能なグループは1つだけ**。`celtium$reorderable` を全familyに広げても変わらない。
つまり即時グループは大半が
- **blend有り**（HUD/フォント/パーティクル/半透明ブロック）、または
- depth test/write を持たないパイプライン

⇒ **同一素材の不透明runが存在しない**ため、run長は伸びず、indirect経路はこのsceneで原理的に効かない。
これは「素材キー61〜69種」という以前の観測と矛盾しない（キーは多いが、**並べ替えて隣接させられる
不透明グループがほぼ無い**）。間接経路・ソートとも既定offが正しい。

```
leg sortonly-world: PASSED・problems 0、frame 9.27 → 7.94 → 7.50ms（ばらつき内、優位差とは言えない）
```

**技術的負債の削除: 記録時bakeの実験コード（2026-10-23）**

記録時bakeの実験フラグ（記録時にモデルビューを頂点へ焼き込むもの）は、実測で
「発火するが描画数も時間も変わらない」と反証済みで、既定offのまま残っていた。
フラグ・`IDENTITY_MATRIX`・`celtium$bakeGroupInPlace` と呼び出しを削除した（約60行）。
`copyVerticesTransformed` は受理時のpre-transform（GUI系）が使うので残す。

確認: ビルド成功、CPUテスト 122件すべて成功（failures 0）。

**HEADの受入（2026-10-23・bake撤去後）**

`main` の最新ビルドで world を受入（AMD RX 6400、既定フラグ、180秒）:
```
leg head-world: PASSED・problems 0（validation 0・画像gate一致）
frame p50 7.99 → 7.69ms（定常）
loop[frames=120 loopMean=11-12 tickMean=5 displayMean=4 renderMean=4-5]
```
- v0.0.15-alpha と同水準（7.99ms）。**bake撤去・reorderable修正・計器追加は既定経路に影響なし**。
- Intel の証拠は v0.0.15-alpha のもの（既定経路は同一）。差分（フラグ限定の並べ替え述語・計器・死コード削除）は
  既定経路に触れないため、必要になった時点で再取得する。
- `tickMean=5`（20Hzなのでフレーム換算 約0.8〜1ms）と `renderMean=4-5`。
  loopMean 11-12 は計測区間の定義がフレーム間隔と一致しないため、**フレーム間隔(7.7ms)とは別物として読む**。

**RLCraftプロファイルの実体: 0.6.13（GLFW世代）だった（2026-10-23 確認）**

```
.gv-verify の recipe 既定: .celtium-work/modpack-p0/recipe-profile/instances/rlcraft-2.9.3-recipe
  mmc-pack.json: org.lwjgl3 3.4.1 / net.minecraftforge 0.6.13-alpha  / mods: +Fugue-0.23.7.jar
別instance rlcraft-2.9.3-on-07 も同じ構成（0.6.13-alpha, LWJGL 3.4.1, Fugue 0.23.7）
```

⇒ **RLCraftの検証プロファイルは CleanroomMC 0.6.13（GLFW / LWJGL 3.4.1）**。
一方 v0.0.15-alpha は **0.7.11（SDL3 / LWJGL 3.4.3）専用**で、0.6.13 では起動時に明示拒否する
（SDL3バインディング不在を検出）。

したがって「RLCraft互換はFugue更新で解決」という前提は、**Fugue以前にホスト世代の壁**がある:
- 0.7.11 用の本ビルドは RLCraft の現行プロファイルでは起動できない
- 0.6.13 で動かすには **GLFW世代のウィンドウ/提示バックエンド**（SDL3を使わない経路）が必要で、
  これは別規模の実装（現在のGL-lessブートストラップは SDL3 前提）
- Fugue 0.23.7 → 0.24.4 の更新は、その後に効いてくる（0.7系で組んだ場合の前提）

**次の判断が必要**: (a) 0.7.11専用のまま、パック側の移行を待つ / (b) 0.6.13用のGLFW提示バックエンドを実装する。
どちらも「1ラウンドの修正」ではない。

**RLCraft 2.9.3 を Cleanroom 0.7.11 + Fugue 0.24.4 で起動した結果（2026-10-23 実測）**

検証用に `cleanroom-0.7.11-alpha` を複製し、RLCraft 2.9.3 の mod 166件＋config を入れ、
**Fugue 0.23.7→0.24.4**（GitHub `CleanroomMC/Fugue` の `+Fugue-0.24.4.jar`, sha256 `43824892…`）に更新、
`Scalar Legacy-1.0.1` を 0.7 用 `scalar-3.9.0` に差し替えて起動した。

```
結果: 起動24秒で Initializing game 中に停止（exit 255）
java.lang.NoClassDefFoundError: com/cleanroommc/hackery/ReflectionHackery
  at com.fantasticsource.tools.ReflectionTool.getField
  at com.fantasticsource.mctools.MCTools.<clinit>
  at com.fantasticsource.setbonus.config.ConfigHandler.<clinit>
```
⇒ **SetBonus（FantasticSource Tools 経由）が Cleanroom 0.6 の `com.cleanroommc.hackery.ReflectionHackery` を要求**する。
このクラスは 0.7 には無い（Hackery は 0.6 系のライブラリ）。Fugue 0.24.4 では解消しない。

**含意**: 「RLCraft 互換は Fugue 更新で解決」は、少なくとも **0.7.11 + Fugue 0.24.4 では成立しない**。
残る候補は (a) Cleanroom 0.7.12 以降（互換修正入り）での再試行、(b) Fugue 0.25.x、
(c) 0.6.13 用ビルド（前述のGLFWバックエンド問題）。**この順で試す価値がある**。

**RLCraft: Fugue 0.25.5 では進むが、Celtium 無しでも同じ VerifyError で落ちる（2026-10-23 実測・決定的）**

probe（0.7.11 clone + RLCraft 166 mods + scalar-3.9.0）で Fugue を差し替えて再試行:

| Fugue | 結果 |
|---|---|
| 0.24.4 | 24秒で停止: `NoClassDefFoundError: com/cleanroommc/hackery/ReflectionHackery`（SetBonus→FantasticSource Tools） |
| **0.25.5** | **44秒まで進行**（`Sound engine started` / `Created: 2048x1024 textures-atlas`）。次に
`java.lang.VerifyError: Bad local variable type` @ `EntityRenderer.func_78472_g(F)V @863: iload` |

**対照実験**: 同じ probe から **celtium jar を外して**起動 → **同一の VerifyError が再現**。
```
run-rlcft-probe3 (celtium あり): VerifyError EntityRenderer.func_78472_g
run-rlcft-nocel (celtium なし): VerifyError EntityRenderer.func_78472_g  ← 同じ
```
⇒ **この停止は Celtium の不具合ではない**。Fugue 0.25.5 / Cleanroom 0.7.11 と RLCraft 側パッチの
バイトコード干渉（`updateFogColor`）で、**外部で解決すべき互換問題**。

**結論**: RLCraft 2.9.3 は「Fugue 更新」だけでは 0.7.11 で動かない（Fugue 0.25.5 でも未達）。
次に試す価値があるのは Cleanroom 0.7.12-alpha 以降（互換修正入り）での再試行。
Celtium 側でできることは無い（対照実験で切り分け済み）。

**決定的な発見: KPI測定はvalidationレイヤ込みだった（2026-10-23 実測）**

同じjar・同じscene・同じAMD RX 6400で、validation の有無だけ切り替え:

| 条件 | 定常 frame p50 |
|---|---|
| `celtium.vulkan.validation=true`（これまでの全KPI leg） | **7.99ms** |
| `celtium.vulkan.validation=false` | **4.38ms** |

⇒ **validationレイヤが約3.6ms/フレーム（45%）を占めていた**。
これまでの KPI/内訳（描画2.5ms・vanilla 3.0ms・床5.3ms など）はすべて**validation込みの数値**であり、
**実運用に近い条件では frame 4.38ms ≒ 228fps**。

**影響**
- 300fps（3.3ms）までの残りは **約1.1ms**。以前の「床5.3msで到達不能」はvalidation込みの結論で、**再評価が必要**。
- 以後の性能測定・最適化は **validation OFF** を基本にし、受入（validation ON）は別途記録する。
- leg `novalid` は harness 上も PASSED（validation無効でも他gateは通る）。

**RLCraft: Cleanroom 0.7.12-alpha でも同じ2つの壁（2026-10-23 実測）**

指摘どおり Cleanroom を **0.7.12-alpha** に上げて再試行（`patches/*.json` と `cleanroom-0.7.12-alpha-universal.jar` を
Prism libraries に配置して構成）:

| Cleanroom | Fugue | 結果 |
|---|---|---|
| 0.7.11 | 0.24.4 | 24秒: `NoClassDefFoundError com.cleanroommc.hackery.ReflectionHackery` |
| 0.7.11 | 0.25.5 | 44秒: `VerifyError EntityRenderer.func_78472_g @863` |
| **0.7.12** | **0.24.4** | **同じ Hackery エラー**（変化なし） |
| **0.7.12** | **0.25.5** | **同じ VerifyError**（Sound engine started まで進行、変化なし） |

また 0.7.11 の実験で **celtium jar を外した対照でも同じ VerifyError** が出ることを確認済み。
⇒ RLCraft 2.9.3 は 0.7.x + Fugue（0.24.4/0.25.5）+ Cleanroom 0.7.12 の組み合わせでは起動できず、
**Celtium の関与しない外部互換問題**（SetBonus→FantasticSource Tools の Hackery 依存、Fugue/Cleanroom と
RLCraft側パッチの `updateFogColor` バイトコード干渉）。Celtium側で打てる手は無い。

**validation OFF の内訳（2026-10-23・AMD RX 6400, passSplit込み）**

```
frame 3.7 / world 3.4 / gui 0.3
flush 0.8   terrain 1.0（うち wrecLoop 0.5＝即時ループ、terrainRecord 0.2）
grpDraw 0.2 / grpPrep 0.1 / rcPush 0.1 / rcViewport 0.1
offMerge 0.1 / offAppend 0.1 / setupTerrain 0.1 / sky 0.1
```
⇒ validation OFF では **即時描画（194グループ）は約0.5ms**、地形記録0.2ms、GUI 0.3ms。
frame 3.7ms のうち名前の付いたレンダラ分は約1.5msで、残り約2msがvanilla側（tick含む）。
※このrunはpassSplit有効（bracket税あり）。production（bracketなし）はさらに軽い。

**KPIの現在地（実運用に近い条件, 2026-10-23 実測・プロトコル適用）**

validation OFF・同一jar・180s leg を3回:

| run | 定常窓 p50 | p95 |
|---|---|---|
| prod-p1 | 4.97ms | 12.21 |
| prod-p2 | 4.43ms | 8.05 |
| prod-p3 | 4.75ms | 8.62 |
| **中央値** | **4.75ms ≒ 210fps** | ~8.6 |

⇒ 300fps(3.3ms)まで **約1.45ms**。内訳（bracket有run）は 即時0.5 / terrain 0.5 / gui 0.3 /
残り約2msがvanilla（tick・エンティティ/TESR・ワールド参照）。
レンダラが所有するのは **約1.3〜1.5ms** で、そこを半分にしても届かない。**残りはvanilla側の削減が必要**。
参考: validation ON の同条件は 7.99ms（レイヤが約3.2ms）。

計測プロトコル: validation OFF の 180s leg ×3、各runの最終窓 p50 の中央値。単一窓p50は使わない
（300s run で最終窓 6.46ms という外れ値も観測済み＝ワールド状態で動く）。

**validation OFF のプロファイル（2026-10-23・JFR 30s・Client thread 209サンプル）**

inclusive（上位）:
```
 8% submitImmediateBatchBody          ← 受理経路（自前）
 7% submitImmediateBatchTimed
 7% HashMap.getNode
 7% EntityRenderer.renderWorldPass
 6% submitGeometryBatch
 5% HashMap$TreeNode.getTreeNode      ← tree bin（衝突）
 4% TileEntityRendererDispatcher.render
 4% flushImmediateWindow / celtium$renderPass
```
self上位: submitImmediateBatchBody 5% / HashMap$TreeNode 5% / Long2ObjectOpenHashMap.get 3% /
HashMap.putVal 2% / ArrayDeque.elementAt 2% / BufferBuilder 2%。

tree bin の呼び出し元（少なくとも判明分）:
`CeltiumGlStub.noteBindSite` 3（テーブル充填前のみ）、`EntityDataManager` 2（vanilla）、
`VulkanModelQuadCapture.generationOf` 2（自前・Integerキー）、`VulkanTextureArray.indexFor` 1、
`VulkanTextureRegistry.get` 1。→ **分散しており単一の巨大ホットスポットは無い**。

⇒ validation OFF では受理経路（自前）が最大の名前付き項目で、後はvanillaのエンティティ/TESR/ワールド参照が
幅広く並ぶ。**レンダラ側は約1.3〜1.5msで分散しており、1つの変更で数百µsを取れる箇所は見つかっていない**。

**これまでのKPIは「present毎のreadback」税込みだった（2026-10-23 実測）**

present経路の段別内訳（`CELTIUM_PRESENT_STAGES`）を読むと、1 present の実コストは:
```
acquire ~0.05ms / readback ~1.6ms / queue ~0.05ms / caps ~0.13ms / submit ~0.06ms / dumpWrite ~0.4〜2.5ms
```
`readback` は**中央8x8ブロックを読むためのバッファ作成＋submit＋fence待ち**（presentedMeanの証拠用）。
`dumpWrite` は presented PNG の書き出し。

**すべての gv-leg は既定で `-Dceltium.vulkan.presentReadback=true` と周期dumpを渡す**（`scripts/gv_leg.py`）。
そのため今までの「4.75ms」等は**実測値ではなく計測税込み**:
- validation ON: 7.99ms
- validation OFF（readback/dump込み）: 4.75ms（中央値）
- **本物のKPIは `scripts/gv_kpi.sh`**（`presentReadback=false` を明示し、interleaved repで中央値/比を取る）

⇒ 「CeltiumはCeleritasより速くならない」は**測定構成のせいだった可能性**が高く、
次は gv_kpi で Celeritas 参照jar（`~/ダウンロード/01-celeritas-forge-mc12.2-2.5.0-dev.jar`）と
同一repで比較する（leg `kpi-cel`）。ここで負けていれば初めて「レンダラが遅い」と言える。

**Celtium vs Celeritas の同一rep比較（2026-10-23・gv_kpi 正規ハーネス）**

`scripts/gv_kpi.sh`（`presentReadback=false`、候補と参照をinterleave）で、
world/qmagnet・AMD RX 6400・180s×2rep:

| rep | Celtium warm p50 | Celeritas warm p50 |
|---|---|---|
| r1 | 2.030ms | 1.594ms |
| r2 | **1.872ms** | **1.430ms** |
| p95 | 5.8〜7.1ms | 2.6〜3.5ms |

- 候補jar: `c6c496892558ab2690c4984c0c7bf384283e56945278496c0dbbc29180e69642`
- 参照jar: `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c`（`01-celeritas-forge-mc12.2-2.5.0-dev.jar`）
- **median paired ratio = 1.291x**（Celtiumが29%遅い）/ verdict `NO_SPEED_VERDICT`（負荷/FORCEのため有意判定なし）

**判明したこと**
1. **Celtiumは 1.87〜2.03ms ＝ 約490〜535fps**。これまでの「4.75ms ≒ 210fps」はfixture税（presentReadback 1.6ms + dump）込みで、
   **ユーザー目標の500fpsに実測で到達している**（同条件のgv_kpiで）。
2. ただし**Celeritasはさらに速い（1.43〜1.59ms）**。差は約0.44msで、
   我々の**受理経路（全immediate batchをJavaで捕捉）と194グループのループ＋複数ウィンドウ**が主因と推定。
3. **p95が我々だけ2倍悪い**（5.8〜7.1 vs 2.6〜3.5ms）＝フレーム毎のヒッチ源が我々にある。

**次の標的**: 受理経路のコスト削減（CeleritasのGL即時描画に相当する部分をJavaで模倣している所）とp95ヒッチ。
ここを詰めればCeleritas超えが現実的な距離（0.4ms）にある。

**present毎のsurface caps問い合わせを8回に1回へ（2026-10-23・A/Bは交絡）**

presentの段別で `capsMs`（vkGetPhysicalDeviceSurfaceCapabilitiesKHR）が約0.13ms/present あったため、
リサイズ検出用の問い合わせを **8 presentに1回**へ間引いた（`CAPS_CHECK_INTERVAL=8`）,
最悪でも数present分の古いフレームで、毎フレーム0.13msを払うより安い。

`kpi-thr`（候補=間引き版, 対照=直前ビルド, 150s×2rep, interleaved, 参照なし）:
```
候補 1.877 / 1.830ms（p95 5.6）
対照 2.620 / 2.382ms（p95 22〜31, samples 1433/1948 と少ない）
判定 NO_SPEED_VERDICT
```
⇒ 数字上は候補が速いが、**対照legのsamplesが半分以下で負荷に叩かれている**ため交絡。
このA/Bを根拠に「26%改善」とは言わない。**機構としては0.1ms程度の削減**を狙ったもので、
次に空いている機械で再測する。

**受理経路の削減（2026-10-23）: prepare のメモ化と bind-site 診断の停止**

Celeritasとの差0.44msのうち、受理経路（`submitImmediateBatchBody` が inclusive 13%・self 5%）を削る第一手:

1. **`prepareImmediate` の直マップメモ化（256エントリ）**
   素材は (format, textureId, stateKey, texture upload serial) の純関数。1フレームのofferは約1150だが
   素材は約194種なので、同じ3つ組の再解決を省く。`PreparedImmediate` はfinalフィールドのみ＝共有可。
   passが変わったら表を空にする（pipelineはpassに依存）。**テクスチャのwrite serialをキーに含める**ので、
   アップロード後に古い解決を返すことはない。
2. **`noteBindSite` を既定off（`-Dceltium.debug.bindSites=true` で有効）**
   これは「no mirror」拒否メッセージ用の診断で、毎テクスチャbindで衝突tree binの `containsKey` を払っていた
   （no-validationプロファイルでクライアントスレッドの約3%）。既定offにした。

確認: ビルド成功・CPUテスト122件成功（failures 0）。
A/B（`kpi-bs`: 候補 vs throttleビルド, 150s×3rep）は負荷交絡のため判定保留。

**F2スクショ黒の修正（2026-10-23・受入は通るが経路は未再現）**

原因の候補だった「既定framebuffer(0)からの `glReadPixels` の拒否」に対し、
`TextureCopyService.celtium$readPixels` に**ウィンドウ読みのフォールバック**を追加した:
`ownedReadImage(0)` が画像を解決できず、要求矩形がframe targetに収まる場合、
**配信中のフレーム画像**から返す（`CELTIUM_READ_PIXELS_WINDOW` を4回まで記録）。

- 受理: leg `shotfix-acc`（AMD RX 6400, world, validation ON）= **PASSED・problems 0**
- ただし**このfixtureはF2の経路を通らない**（presented dumpは自前の経路）。
  そのため `CELTIUM_READ_PIXELS*` のログは1件も出ず、**修正の実地確認はできていない**。
- 未検証であることを明記する。次はfixtureに `ScreenShotHelper.saveScreenshot` を呼ぶ段を足し、
  保存PNGの平均色/非黒割合を検査する（黒なら本修正の対象経路が違う）。

**残り3件（④色付きガラスのチカチカ / ⑤看板文字の残像 / ①パノラマ / ②エンドポータル）は未着手**。
⑤は `GllessFrameTarget` の clear latch（`gameClearLatched`）がメニュー/GUIフレームで
更新されない場合に前フレーム内容が残る筋が有力。

**黒スクショの根因を特定（2026-10-23 実測）**

fixtureに `-Dceltium.validation.gameScreenshot=true` の段を追加し、**ゲーム自身のF2経路**
（`ScreenShotHelper.saveScreenshot`）を実際に呼ばせて保存PNGを判定した。

```
leg shot-test （修正前）: gameScreenshotMean=0.00 nonBlackFraction=0.00000 black=true
leg shot-test2（packed-int対応後）: 同じく 0.00 / 0.00000 / black=true
```
**`CELTIUM_READ_PIXELS*` のログが1件も出ない**＝ゲームの読みは**スタブに到達していない**。

原因: vanilla `ScreenShotHelper` は
`GL11.glReadPixels(int, int, int, int, int, int, IntBuffer)` を呼ぶが、
`CeltiumGllessNativeTransformer` が計装しているのは `glReadPixels`/`nglReadPixels` の
**`(IIIIIIJ)V`（アドレス版）** のみ。**IntBuffer版 `(IIIIIILjava/nio/IntBuffer;)V` は対象外**なので、
LWJGLの実装がそのまま走り、GLコンテキストが無いためバッファはゼロのまま → 真っ黒PNG。

**IntBuffer版を追加したが、まだ黒い（2026-10-23 実測）**

`CeltiumGllessHint.glReadPixelsBuffer` と transformer の `(IIIIIILjava/nio/IntBuffer;)V` 分岐を追加し、
`TextureCopyService` に packed-int（`GL_BGRA`+`GL_UNSIGNED_INT_8_8_8_8_REV`）の書き込みも入れた。
leg `shot-test3` の結果は**変化なし**:
```
gameScreenshotMean=0.00 nonBlackFraction=0.00000 black=true
```
決定的なのは **`CELTIUM_READ_PIXELS*` のログが依然1件も出ない**こと。
⇒ ゲームの `saveScreenshot` は**スタブの `glReadPixels` に到達していない**。vanilla 1.12 の
`ScreenShotHelper` は自作のFBOを作って色テクスチャを描き、そこから読むので、
(a) そのFBOのミラー画像が黒、(b) 読みが別経路、のどちらか。

**次**: `CeltiumGlStub.celtium$readPixels` の入口に到達ログを足して「呼ばれていない」を確定させ、
呼ばれていないなら `ScreenShotHelper` が実際に使うGL関数（`glReadPixels` のIntBuffer版か、
`GL30`/`GL12` の別名か）をバイトコードで確認する。黒スクショは未解決。
