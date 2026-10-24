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

**根因確定: ゲームのスクショは `glGetTexImage` を使う（2026-10-23 実測＋原典）**

到達ログを足して判明: `celtium$readPixels` に届くのは fixture 自身の 8x8 centre sample だけで、
スクショの読みは**一度も来ない**（`CELTIUM_READ_PIXELS_ENTRY readFbo=1 ... 8x8 ...` のみ）。

原典（`.celtium-work/mc-sources/net/minecraft/util/ScreenShotHelper.java:createScreenshot`）:
```java
if (OpenGlHelper.isFramebufferEnabled()) {
    GlStateManager.bindTexture(framebufferIn.framebufferTexture);
    GlStateManager.glGetTexImage(3553, 0, 32993, 33639, pixelBuffer);   // 32993=GL_BGRA 33639=UNSIGNED_INT_8_8_8_8_REV
} else {
    GlStateManager.glReadPixels(0, 0, width, height, 32993, 33639, pixelBuffer);
}
```
GL-less では `isFramebufferEnabled()` が真なので**必ず `glGetTexImage` 側**を通る。
そして `glGetTexImage` は `CeltiumGlStub` の**ゼロ書き**リストに入っている（`celtium-vulkan/.../CeltiumGlStub.java:488`）。
⇒ **バッファがゼロのままPNG化＝真っ黒**。transformer にも hint にも `glGetTexImage` の経路は無い。

**修正・検証済み（2026-10-23）**

`nglGetTexImage(IIIIJ)V`（LWJGLのnative）を transformer → `CeltiumGllessHint.glGetTexImage` →
`CeltiumGlStub.celtium$getTexImage` → `TextureCopyService.celtium$getTexImage` に配線し、
バインド中テクスチャ名のミラーから packed-int(BGRA) で返すようにした。

**訂正**: 最初に `(IIIILjava/nio/IntBuffer;)V` を計装しようとして何も起きなかったのは、
このtransformerが**LWJGLのnativeメソッド**を書き換える方式で、バッファ版の呼び出しは
`GL11` 内で native の `nglGetTexImage(IIIIJ)V` に化けるため。descriptorは必ず `(IIIIJ)V`。

検証（leg `shot-test6`, AMD RX 6400, world, validation ON）:
```
受入: PASSED・problems 0
CELTIUM_GET_TEX_IMAGE texture=3 1280x720  (ルート発火)
gameScreenshotMean=113.74  nonBlackFraction=0.98308  black=false
```
保存PNGのsha256 `24aafadd27d659c3d995070970d8d8980c979273ecd221038b400f20a6641d64`、
証拠として `docs/evidence/v0.0.15-f2-screenshot-fixed.png` に保存。
⇒ **ゲーム自身のF2スクリーンショットが実画像を返すようになった**（不具合③は修正）。

**フレーム画像のclear計測（2026-10-23・残像⑤の切り分け途中）**

`GllessFrameTarget` に「acquire時にclear要求が無かった回数」を追加し、`-Dceltium.vulkan.handoffTimings=true` で
`gameClears=[latched=435,live=869,noClear=2177]` を観測。

**ただしこれは「ウィンドウ毎」の計数**で、1フレームに複数ウィンドウ（描画サイト毎）を開くため、
2つ目以降のウィンドウは毎回 clear を持たないのが正常。したがって **2177 は「フレームがclearされなかった」証拠にはならない**。
「フレーム単位でclearが漏れたか」を判定するには、フレーム開始でのみ計数する必要がある（次にやる）。

⇒ 残像⑤の原因は未確定。今回の計器だけでは断定しない。

**フレーム単位で測り直した結果: clearは毎フレーム入っている（2026-10-23）**

フレーム境界で「そのフレームにclearが1回も入らなかった回数」を数え、実際に印字される
`gameClears=[...]` の行に載せた（`framesNoClear=`）。leg `frameclear2`（AMD, world, 90s）:
```
gameClears=[latched=347,live=686,noClear=1720,framesNoClear=0]
```
⇒ **clear漏れのフレームは0**。**残像⑤の原因はclear漏れではない**（この仮説は棄却）。
残る候補: (a) フレーム画像を次フレームが上書きしている最中にpresentが読む、
(b) 同じ文字が二重に描かれている（immediate経路＋display list経路）、
(c) フォントアトラスのミラーが1フレーム遅れる。
次は (b) を疑い、看板TESRの描画回数を計器で数える（`drawModelList`/文字クアッドのサイト別カウント）。

**ゲームのF2画像と提示フレームは静止時にピクセル一致（2026-10-23 実測）**

leg `shot-test6` は同じtickで「ゲーム自身のスクショ」と「提示フレームのdump」の両方を残すので、比較した:
```
game screenshot : screenshots/celtium-validation-screen.png
presented (kept): screenshots/celtium-presented-world-...-kept.png
サイズ一致 1280x720 / 差分ピクセル 0（0.00%）
```
⇒ 静止時は**提示画像がゲームのF2と完全一致**。提示経路が古いフレームを混ぜている・別画像を出している、
という筋は**静止条件では否定**される。フレーム画像のclearも毎フレーム入っている（`framesNoClear=0`）。

したがって残像⑤は**動きに依存する現象**（提示レイテンシ＝1フレーム遅れ、または入力と表示のずれ）の可能性が高い。
次は fixture に「視点を動かしながら2フレーム撮る」段を足し、F2と提示画像の**位置ずれ**を測る。
（静止画比較で0差分だったことは、動きの指標にはならない。）

**提示の遅れを計測: 0フレーム（2026-10-23 実測）**

presentが「ゲームが描いているフレーム」からどれだけ遅れているかを、submit時に記録したフレームIDと
present時のフレームIDの差で計測（`framesNoClear` と同じ印字行に `presentLag=[max,one,more]`）。
leg `preslag3`（AMD, world, 90s）:
```
gameClears=[latched=426,live=846,noClear=2120,framesNoClear=0 presentLag=[max=0,one=430,more=0]
受入: PASSED
```
⇒ **提示は常に最新フレーム（差0）、2フレーム以上遅れた回は0**。
**残像⑤の原因は「提示が古いフレームを出している」ことではない**（この仮説も棄却）。

ここまでで⑤について棄却した仮説:
1. フレーム画像のclear漏れ → `framesNoClear=0`
2. 提示画像が古い/混ざる（静止） → F2画像と提示画像の差分0
3. 提示のフレーム遅れ → `presentLag max=0, more=0`

残る筋: ゲーム自身の入力→描画の遅延（Minecraft 1.12の1フレーム遅れは仕様）、
またはGPU側の描画順（半透明の並び）で「見づらさ」が出ている可能性。④と同じ土台を疑う。

**色付きガラスのチカチカ④: コードで確認できた範囲（2026-10-23）**

- 半透明terrainのpipelineは `depthWriteEnable(depth && !this.blended)` ＝**blendedではdepth書き込みOFF**
  （vanillaのtranslucentレイヤと同じ）。
- 深度formatは `D32_SFLOAT` を第一候補（`VulkanDepthFormat`）。vanillaは24bitなので**精度は同等以上**で、
  z-fightingが我々だけ悪化する理由にはならない。
- よって「初歩的なstate誤り」では説明できず、**同距離セクションの並び順の非決定性**か
  **透明面の重なり（同一平面）**が残る。

**必要な計器**: 静止カメラで**連続2フレーム**を別ファイルにdumpし、ピクセル差分を取る。
- 差分が大きい → フレーム間で描画順が変わっている（＝チカチカの直接証拠）
- 差分が小さい → 並び順ではなく、視点依存のz-fight（見た目の問題）
この2フレームdump段をfixtureに足すのが次の作業。撮影は `celtium$requestPresentedDump` を
world撮影の直後と次tickで別名に対して発行し、ファイルを比較する。

**時間方向のちらつき計器を追加（2026-10-23 実測・基準値つき）**

`-Dceltium.debug.flickerSample=true` で、**連続する2枚の提示フレームをreadbackして比較**する
（150提示以降に2枚、差分>8/255 を「変化」として数える）。leg `flicker`（AMD, world, 90s, 静止カメラ）:
```
CELTIUM_FLICKER samples=2 changed=1259/921600 fraction=0.00137 maxDelta=190
受入: PASSED・problems 0
```
⇒ 静止カメラでも **0.137%のピクセルがフレーム間で変わる**（maxDelta 190）。
この値はHUD/トースト/雲など動く要素の寄与を含む**基準値**で、これ以上の変化が出れば
「時間方向に不安定な描画」（＝チカチカ）の客観的証拠になる。

次の使い方: 色付きガラスがある地点をfixtureのポーズに足し、同じ計器で fraction を比較する。
（ガラスが写っていない今のポーズでは、チカチカ④の有無は判定できない。）

**パノラマ①のメニュー撮影を試行（2026-10-23・発火せず）**

`-Dceltium.validation.dumpPresentedImage=true -Dceltium.validation.dumpPresentedMenuTick=40` で
メニュー提示画像のdumpを要求したが、**撮影条件が発火しなかった**（report に `menuDump*` が無く、
`screenshots/` に menu 画像も無い）。条件は
`worldTicks==0 && clientTicks>=40 && mc.currentScreen instanceof GuiMainMenu` で、
world mode のlegは起動後すぐワールドへ進むため滞在tickが足りない。

先人のメモ（同コード内）に「round 166: 描かれたパノラマは全く動かない」とあり、①の症状は
**パノラマが回転しない**こと。切り分けは `menuDumpPanoramaTimer`（panoramaTimerが進んでいるか）と
`menuDumpRenderPartialTicks` の2値で行う設計になっている。

**次**: menu 用の短い leg（world に入らず 60 client tick 滞在するモード）を追加するか、
`dumpPresentedMenuTick` を world 突入前の実測tickに合わせる。撮影できれば、
panoramaTimer が進んでいるのに絵が動かない＝我々の描画側、両方止まっている＝フィクスチャ側と分離できる。

**パノラマ①の症状を画像で再現（2026-10-23 実測）**

`-Dceltium.validation.menuHoldTicks=80 -Dceltium.validation.dumpPresentedImage=true
-Dceltium.validation.dumpPresentedMenuTick=40` でタイトル画面を保持してメニュー提示画像を取得できた。

```
menuDumpPanoramaTimer=271.03995   ← ゲーム側のパノラマ回転タイマーは進んでいる
menuDumpRenderPartialTicks=0.9999969
picture: screenshots/celtium-presented-menu-run-...-kept.png (1280x720)
受入はFAILED（メニュー保持がワールド撮影のタイミングをずらしたため。画像取得は成功）
```
取得画像は `docs/evidence/2026-10-23-menu-panorama-broken.png`。内容:
- ロゴ・ボタン・スプラッシュ・MOD数表示は**正しい**
- **背景パノラマが壊れている**: 巨大な文字（アトラスの一部）が引き伸ばされたような絵

**切り分け結果**: panoramaTimer は進んでいる（271）ので「ゲームが角度を進めていない」側ではない。
⇒ **我々の描画側**で、パノラマの背景画像（6面×64複製をオフスクリーンFBOへ描く）が
正しく合成・参照されていない。`CELTIUM_RAW_FBO framebuffer=1/6/24 colorTexture=3/7/25` は出ており
テクスチャ拒否は0件。したがって「FBOの色テクスチャを描画元として使う経路」（コピー/参照）の
ルーティングが本命。パノラマは 6面テクスチャ→FBO→ぼかし→合成 の多段なので、
どこで別画像（アトラス）を引いているかを次に特定する。

**パノラマの参照画像は取れず（2026-10-23 実測）**

Celeritas（`01-celeritas-forge-mc12.2-2.5.0-dev.jar`）＋fixture で同じメニュー保持フラグを付けて実行したが、
`screenshots/` に出たのは `celtium-validation.png`（GL armのワールド撮影）だけで、**menu dumpは無い**。
presented dump は**Celtiumのpresentフックが書くもの**なので、参照レンダラ側では発火しない。
⇒ パノラマの「正しい絵」を fixture で取るには、メニュー段で `celtium$captureGlScreenshot`
（GL armのスクショ経路）を呼ぶ段を足す必要がある。**未実装**。

現時点の①の状態: 壊れた画像（`docs/evidence/2026-10-23-menu-panorama-broken.png`）と
「panoramaTimerは進む・テクスチャ拒否0・生FBO記録あり」まで確認済み。参照画像なしで
多段合成のどこが誤りかを詰めるのは非効率なので、次は参照画像の取得を先に整える。

**パノラマ①の正解画像を取得（2026-10-24 実測・これが基準）**

menu段に「ゲーム自身のスクショ」を追加したことで、**GLアーム（Celeritas）でもメニュー画像が取れる**ようになった。
同じフラグ（menuHoldTicks=60/80, dumpPresentedMenuTick=40）で両アームを撮影:

| アーム | 画像 | 内容 |
|---|---|---|
| **GL（Celeritas 2.5.0-dev）** | `celtium-validation-menu.png`（534KB） | **正しい**: 左に緑/青の風景、右に灰色の石、スプラッシュは画面中央上寄り。全体にぼかし |
| GL-less（Celtium） | `celtium-presented-menu-...`（255KB） | **壊れている**: 巨大な白い文字（アトラス断片）が画面全体に引き伸ばされる |

保存: `docs/evidence/2026-10-24-menu-panorama-reference.png`（正解）と
`docs/evidence/2026-10-23-menu-panorama-broken.png`（我々）。

さらに **GL-less側でもF2経路（nglGetTexImage修正後）とpresented dumpがほぼ一致**（差分0.48%＝トースト等の時間差）。
⇒ 壊れているのは「ゲームのスクショ」ではなく**我々が描いている絵そのもの**。

**次の一手**: パノラマは 6面テクスチャ×64複製 → FBO(256x256) → ぼかし → 合成 の多段。
巨大文字は「合成時に別テクスチャ（アトラス）を引いている」形なので、
メニュー期間の `bindTexture` と、合成クアッドが解決したテクスチャ名を計器で出す。

**パノラマ①の追加計測（2026-10-24）**

- chain pairの**退避（eviction）は発生していない**（`CELTIUM_CHAIN_EVICT` 0件）。上限8でも
  メニューのFBO数は収まっており、画像の取り違えはエビクションではない。計器は残置（今後の再発検出用）。
- `CELTIUM_FBO_CENSUS` / `CELTIUM_FBO_TEXTURE_PUBLISHED` により、vanilla Framebufferの色テクスチャは
  **公開（publish）されている**ことを確認: name=7 identity=0x5b7518c8 route=chain-pair（1281x720）、
  name=25 identity=0x4d3efa84 route=chain-pair（1280x720）。
- 「毎フレーム再publish」を試したが**絵は変わらなかった**（参照画像との差分65%のまま）ため**revertした**。
  未検証の挙動変更を残さない方針。
- パノラマFBO（256x256想定）のテクスチャ名は `CELTIUM_PREP_TEX` で 23 として見えており、
  これに対応する公開ログが出ていない。**次の的にする**: name=23 の identity を引き、
  そのchain pairの中身（読み出し平均色）を測る。ここが空/別物なら合成元の解決が誤り。

現状の①: **未修正**。壊れた絵と正解画像、計器、棄却済み仮説（eviction/再publish）は揃っている。

**パノラマ①の現状と次に試すこと（2026-10-24 記録）**

判明している事実（実測）:
- タイトル画面は撮れる。`menuHoldTicks`＋`dumpPresentedMenuTick` で **GLアーム（Celeritas）と GL-less の両方**の絵が取れ、
  正解画像 `docs/evidence/2026-10-24-menu-panorama-reference.png` と 壊れた絵
  `docs/evidence/2026-10-23-menu-panorama-broken.png` が比較できる。
- `panoramaTimer=262〜271` は進む（ゲーム側は動いている）。
- chain pairの退避は0（上限8は原因でない）。毎フレーム再publishしても絵は変わらない（revert済み）。
- パノラマは「6枚の背景テクスチャ × 64タイル → 256x256のFBO → ぼかし → 合成」。
  `CELTIUM_PREP_TEX` では背景6枚（256x256）は正しく解決されている。
- `celtium.debug.prepareTextures` の「FBO色テクスチャとして解決された描画」は**0件**だった（＝描画側では
  FBOテクスチャを直接サンプルしていない）。ただし逆引きマップが空である可能性と区別できていないので
  **この0を根拠に断定しない**（AGENTSの罠）。

壊れた絵の見え方は「巨大な白い文字片（アトラス様）」。パノラマの合成が
**別テクスチャ（フォントアトラス等）を引いている**形。

次に試す（優先順）:
1. `celtium$publishFramebufferColorTexture` を、パノラマFBOの色テクスチャ（推定 name=23）に対して
   明示的に呼び、`CELTIUM_FBO_CENSUS` が出るか・identity が解決するかを見る（逆引きの有無を確定）。
2. パノラマFBOの中身を直接読む: その identity の chain pair 画像を `readImageRectangle` で読み、
   平均色と非黒率を出す。正解なら風景の平均色、壊れていれば別物。
3. ぼかし段（glCopyTexSubImage2D 連鎖）の宛先テクスチャが正しいか、`CELTIUM_TEX_COPY_*` の拒否/宛先を確認。

**「GL-lessのスクショは構造上黒い」という前提を削除（2026-10-24 実測）**

fixture は長らく `pictureScreenshotUsable_<stage>=False` を**決め打ち**し、
「GL-lessでは黒に決まっている」という注記を書いていた。しかし `nglGetTexImage` を
レンダラのミラーから返すようにした後は事実と異なる。

修正: `saveScreenshot` の後で**保存PNGを読んで** 非黒率と平均値を測り、`>0.05` で usable を決める。
leg `menu6`（AMD, menuHold 80, world/menu両方を撮影）:
```
pictureScreenshotNonBlackFraction_menu = 0.94741
pictureScreenshotNonBlackFraction_world = 0.98295
pictureScreenshotUsable_menu = True / _world = True
```
⇒ ゲーム自身のスクショは**GL-lessでも実画像**（F2修正の裏付けが計測として残る）。

**パノラマ①: ジオメトリとテクスチャは正しく、射影行列が疑わしい（2026-10-24 実測）**

計器を2つ追加して分かったこと:

1. `CELTIUM_WINDOW_ROUTE`（100グループ以上のウィンドウで経路を記録）
```
identity=0x0 isMain=false route=frame-image groups=384 textures=[34,35,36,37,38,39] frame=0
```
- 384グループ＝バニラのパノラマ描画（64タイル×6面）そのもの。
- 使っているテクスチャは **34〜39＝パノラマ6面の画像で正しい**。
- 経路は `frame-image`（＝画面）。これはパノラマが画面へ直接描かれるバニラの構造どおりで**正常**。

2. `CELTIUM_FBO_BIND`（mixinが捕捉したFramebuffer bind）
- 出てくるのは 1280x720 / 1281x720 / main のみで、**256x256のFBOは登場しない**。
  パノラマはFBOを使わない（直接画面へ描く）ため。

⇒ したがって「巨大な文字片に見える」のは**テクスチャでも経路でもなく射影**の疑いが濃い。
ベースは `matrixMode(GL_PROJECTION); loadIdentity; Project.gluPerspective(120,1,0.05,10)` で
作られる透視射影。この射影が我々の push constant に載っていない（GUIの直交射影のまま等）なら、
同じ384クアッドが画面全体を覆う巨大タイルとして描かれる＝観測と一致する。

**次の一手**: パノラマ期間の `projection`（stubのミラー）と、記録された group.projection を
数値で出す（例: p[0], p[5], p[11], p[14]）。正しい透視なら p[11]=-1, p[14]≈-0.1 付近、
直交なら p[0]=2/w のような値になる。ここが違えば射影チャネルの修正（stubの projection stack 追跡）へ。

**パノラマ①: 射影は正しい。次はモデルビューの重複を疑う（2026-10-24 実測）**

`CELTIUM_WINDOW_ROUTE` に最初のグループの射影を載せて計測:
```
groups=384 textures=[34,35,36,37,38,39] projection=[0.577,0.577,-1.000,-0.050]
```
- p[5]=0.577＝1/tan(60°)（`gluPerspective(120,1,...)`）、p[11]=-1、p[14]=-0.05。
  **正しい透視射影**であり、「GUIの直交射影のまま」という仮説は**棄却**。
- テクスチャ（34〜39＝6面）も経路（frame-image）も正しい。

残るのは**モデルビュー**。バニラは64タイルそれぞれに微小な translate（±0.07 程度）と回転を与えて
視差を作る。もし我々のグループ捕捉で modelView が全タイル共通（または恒等）になっていれば、
384枚がほぼ同一位置に重なって**1枚の巨大画像**に見える＝観測（巨大な文字片）と整合する。

**次の一手**: 384グループの `modelView` の**相異数**を数える。
- 相異数が1〜2 → 捕捉が壊れている（毎タイル同じ行列）→ 捕捉側の修正
- 相異数が多数（30〜64）→ モデルビューは正常。次は UV/テクスチャ行列を疑う

**パノラマ①: モデルビューは384グループ中172種（2026-10-24 実測）**

`CELTIUM_WINDOW_MODELVIEWS distinct=172 groups=384 first=[0.189,-0.000,0.000,-0.000]`

- 384タイルが**同一行列ではない**ことは確認できた（捕捉が全部同じ、という仮説は棄却）。
- ただしバニラの構造上 64タイル×6面は**384種**になるはずで、172種しかない。
  何割かが同一行列に潰れている（＝捕捉のどこかで行列が共有/上書きされている）可能性がある。
  **断定はしない**: 6面の一部が同じ行列（面の回転が同一）である可能性や、
  グループ統合で同一行列のタイルが1グループにまとまった可能性をまだ分離していない。

**次の一手**: 期待値（384種）との差を説明するため、
(a) 面ごとの回転が同じになる組み合わせを原典で確認、(b) 同一行列グループの件数分布を出す。
そのうえで、潰れが捕捉の不具合なら修正、仕様ならUV側へ進む。

**パノラマ①: 行列の共有は最大4件まで（2026-10-24 実測）**

`CELTIUM_WINDOW_MODELVIEWS distinct=172 groups=384 largest=4 second=4 third=4`

- 384グループ中172種、**最も共有される行列でも4グループ**まで。1〜2種への潰れではない。
- したがって「全タイルが同一行列で重なっている」は否定済み。ただし期待384種に対して172種で、
  **同一行列が最大4件**という分布は「タイルの一部が同じ変換を持つ」ことを示す。

考えられる説明（未分離）:
(a) 受理時の**pre-transform（頂点へ行列を焼く経路）**に入ったグループは modelView が恒等になり、
    同じ恒等行列を持つグループが複数できる。GUI系レイアウトはこの経路に乗る。
(b) バニラの6面回転の組み合わせで同一行列が生じる（仕様）。
(c) 捕捉が同一配列を共有している（値が同じに見える）。

**次の一手**: 384グループのうち **modelView が恒等なものの件数**を出す。
多数なら (a) が本命で、pre-transform の焼き込み（特に透視射影下での回転・拡大）を検証する。

**パノラマ①: 事前変換は0件、バニラは384ドロー。ならば行列の「捕捉」を疑う（2026-10-24 実測＋原典）**

- `CELTIUM_WINDOW_IDENTITY_MV identityViews=0 groups=384`
  ⇒ 384グループに**恒等モデルビューは1つも無い**。pre-transform（頂点焼き込み）仮説は**棄却**。
- 原典 `GuiMainMenu.drawPanorama`（`.celtium-work/mc-sources`）: kループ内で
  `bufferbuilder.begin(7, POSITION_TEX_COLOR)` → 4頂点 → **`tessellator.draw()`**。
  つまり **64タイル × 6面 = 384ドロー/フレーム**で、行列はすべて異なるはず。
- それに対し捕捉は **384グループ中172種・最大4件共有**。

⇒ **384ドローが384種の行列を持つのに172種しか無い**＝どこかで行列が**共有/上書き**されている。
疑わしいのは「グループが stub の行列ミラー配列を参照していて、後の更新で書き換わる」型の別名化。
（完全共有なら1種になるはずで、172種という中途半端さは、捕捉時コピーと参照が混在している形。）

**次の一手**: `ImmediateGroup.modelView` が捕捉時に**コピー**されているかを実コードで確認し、
参照なら `Arrays.copyOf` に変えて再測（384種になれば確定）。

**パノラマ①: 行列は捕捉時にコピーされている（別名化を棄却）**

`closeCurrentImmediateGroupBody` を読むと、グループ確定時に
```
System.arraycopy(this.currentGroupModelView, 0, modelView, 0, 16);
```
と**コピー**してから `ImmediateGroup` に渡している（同様に projection も remap 後にコピー）。
⇒ 先に立てた「stub の行列ミラーを参照していて後で書き換わる」仮説は**棄却**。

残る謎は「バニラは384ドロー/フレーム（64タイル×6面、各面で tessellator.draw()）なのに、
捕捉のモデルビューは172種しかない」こと。次の判別:
- 各グループの**並進成分 (p12,p13,p14)** の分布を出す。タイルごとに ±0.07 程度ずつ異なるはずで、
  値が数種類しか出ないなら `glTranslate` の反映漏れ（行列ミラーの更新漏れ）、
  細かく分かれているなら回転側の合成が潰れている。

**パノラマ①: 並進は64種で完璧。回転側が少ない（2026-10-24 実測）**

```
CELTIUM_WINDOW_TRANSLATIONS distinct=64 groups=384 first=(0.0078,0.0078,-0.0000)
CELTIUM_WINDOW_IDENTITY_MV identityViews=0
CELTIUM_WINDOW_MODELVIEWS distinct=172 groups=384
```
- 並進は**64種**＝バニラの64タイル位置と一致。`glTranslate` はミラーに正しく届いている。
- にもかかわらず行列全体は172種のみ。64並進 × 6面なら**384種**になるはず。
  ⇒ **面ごとの回転が一部しか反映されていない**（または面の回転が同一に潰れている）。
- `matrixMirrorDelta=proj:0.0,view:0.0`（GL照会とミラーが完全一致）なので、
  ミラー自体は正しい。回転のミラー更新（`GlStateManager.rotate` 経路）を疑う。

**次の一手**: 同一タイルの6面ぶんの回転3x3だけを並べて出す。
  - 6面が同値 → `rotate` が行列に効いていない（ミラー更新漏れ）＝修正対象
  - 6面が別値 → 回転は正常。ならば残差172種は「面の組み合わせで同値になる」仕様側

**パノラマ①: matrixMirrorDelta は自己差分（2026-10-24 訂正）**

前ラウンドで「`matrixMirrorDelta=proj:0.0,view:0.0` なのでミラーは正しい」と書いたが、
`glGetFloatv` 系はミラー自身から答えているため、この差分は**自己差分**であり証拠にならない。
`docs/verification-traps.md` に罠として記録した。

独立な観測は「64並進・384ドローに対して捕捉行列が172種」というもの。
次の判別は、**同一タイルの6面ぶんの回転行列が実際に異なるか**を直接ログに出すこと。

**パノラマ①: 面ごとの回転は「あるタイルでは効き、大半では効いていない」可能性（2026-10-24 実測）**

同一並進（＝同じタイル）の6面の回転3x3を出した:
```
CELTIUM_TILE_FACES count=6
rotations=(0.128,-0.965,-0.872) (-0.472,-0.262,-0.872) (-0.128,0.965,-0.872)
          (0.472,0.262,-0.872) (0.128,-0.965,0.472) (0.128,-0.965,-0.472)
```
このタイルでは**6面が別々の回転**を持っている（回転は効いている）。

一方、全体では 384グループ中172種（並進は64種）。算術的な目安:
- 64タイル全部で6面が異なれば **384種**
- 43タイルだけ同一回転・21タイルが6面別なら 43 + 126 = 169 ≒ **172**

⇒ **「大半のタイルは6面が同一回転（＝面の回転が落ちている）、一部だけ正しい」**という仮説が
172という数と整合する（**推測であり、まだ未確定**）。

次の計器: **タイルごとに「6面の回転が何種あるか」の分布**を数える
（64タイルのうち何タイルが6種、何タイルが1種か）。これで上記仮説が確定/棄却できる。
もし「大半が1種」なら、`GlStateManager.rotate` がミラーに届かない条件
（matrixModeの取り違え等）を特定して直す。

**パノラマ①: 2つの計器が矛盾。計器側を先に疑う（2026-10-24）**

新計器 `CELTIUM_TILE_ROTATION_HIST`:
```
tilesWithDistinctRotations=[3:64] placements=64
```
＝**64タイルすべてで「6面の回転が3種」**。しかし直前に別の計器（`CELTIUM_TILE_FACES`）は
同じタイルの6面が**6種の異なる回転**を出していた:
```
rotations=(0.128,-0.965,-0.872) (-0.472,-0.262,-0.872) (-0.128,0.965,-0.872)
          (0.472,0.262,-0.872) (0.128,-0.965,0.472) (0.128,-0.965,-0.472)
```
⇒ **2つの計器が矛盾している**（片方が壊れている）。HIST側は (m0,m1,m4,m5) の4要素だけを
キーにしているため、面の違いがその4要素の外（例: m9 や m2）にある場合に「同一」と誤判定する。
一方 `distinctModelViews=172`（全16要素のハッシュ）とも整合しない。

**結論**: 172という数を含め、パノラマの行列計器は**まだ信用できない**。
次は「全16要素を厳密比較（ハッシュではなくソート比較）して384種か否かを確定」させ、
計器の誤りを潰してから描画側の判断に進む。**未確定のまま修正に入らない**。

**パノラマ①: 行列計器を厳密比較に直したら「全部正しい」だった（2026-10-24 重要訂正）**

ハッシュ比較をやめ、**16要素の厳密比較**（と回転9要素の厳密比較）で測り直した結果:
```
CELTIUM_MODELVIEW_EXACT distinct=384 groups=384 identity=0 placements=64 perTileRotations=[6:64]
  textures=[34,35,36,37,38,39] route=frame-image projection=[0.577,0.577,-1.000,-0.050]
```
- **384ドローすべてが異なる行列**（期待どおり）
- **64の並進位置すべてで6面の回転が異なる**（期待どおり）
- identity 0、テクスチャは6面、経路 frame-image、射影も正しい

⇒ これまでの「172種」「1タイル3種」は**ハッシュ計器の誤り**だった。行列・並進・回転・テクスチャ・射影・経路は
**すべて正しい**。データ面で異常は無い。

**したがって原因は「データ」ではなく「その描画の実行時状態」**（ブレンド/深度/カリング等）にある。
パノラマは `enableBlend / disableAlpha / disableCull / depthMask(false) / tryBlendFuncSeparate(SRC_ALPHA,
ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` の下で384クアッドを重ねる。ここが効いていなければ
半透明の重なりが不透明になり、**巨大なパッチワーク**に見える（観測と一致）。

**次の一手**: この384グループの state（blend有無・depthMask・cull・blendFunc）を記録し、
選択された pipeline がその state を反映しているか照合する。

**パノラマ①: パイプライン状態も「ブレンド有効・深度書き込み無効」（2026-10-24 実測）**

```
CELTIUM_WINDOW_PIPELINES distinct=1 texture blends=true depthWrite=false depthTest=true   ← 1フレーム目
CELTIUM_WINDOW_PIPELINES distinct=1 texture blends=true depthWrite=false depthTest=false  ← 以降のフレーム
```
- パノラマ384グループは**単一のtexture pipeline**で、**blend有効・depth書き込み無効**。
  データ（行列・並進・回転・テクスチャ・射影・経路）も直前の厳密比較で全て正しいと確定済み。
- 1フレーム目だけ `depthTest=true`（以降 false）。バニラは `depthMask(false)` のみで深度テストは
  有効のままの局面もあるため、これ自体は直ちに異常とは言えないが、**遷移として記録**する。

⇒ データも状態も妥当。残るのは「**描画結果のどこがどう違うか**」の性格づけ。
次は壊れた絵と参照画像を**領域別に比較**する（例: 画面を16x9に分割し、各セルの平均色差).
特に「白い巨大な文字片」がフォントアトラス由来か、パノラマ画像の拡大かを、
アトラスの色分布・パノラマ画像の色分布と突き合わせて判別する。

**パノラマ①: 壊れた絵の正体は「巨大なフォント字形」（2026-10-24 画像解析）**

壊れたメニュー画像（`docs/evidence/2026-10-23-menu-panorama-broken.png`）の右下1/4を切り出して拡大すると、
**白い巨大な字形（フォントアトラスの文字）**が確認できる。統計でも裏づく:
```
                 mean   nearWhite  dark   saturated
broken           105.3   0.022     0.186   0.043
reference(GL)    103.4   0.023     0.097   0.131   ← 参照は彩度が高い（風景の緑/青）
背景領域(400-700行,0-300列)
broken mean=69.7 / reference mean=80.6
```
⇒ 参照は**彩度の高い風景**、我々は**灰色の巨大字形**。明るさ平均が近いため以前の統計では見落としていた。

**わかっていること**: グループの `texture` は34〜39（パノラマ6面）で、ミラー内容も正しい色（0xff8fa3ff 等）。
pipelineは blends=true/depthWrite=false。行列・射影も正しい。
それでも**描画時にフォントアトラスを引いている**ように見える。

**次の一手**: 384グループの `prepared.descriptor` が**何種類あるか**を数え、draw時の
`vkCmdBindDescriptorSets` に渡す descriptor が group ごとに変わっているかを記録する。
（1種類しか無いなら、テクスチャ束縛が共有されていて全グループが最後のアトラスを引く＝症状と一致）

**パノラマ①: descriptor も6種で正しい（2026-10-24 実測）**

```
CELTIUM_WINDOW_DESCRIPTORS distinct=6 groups=384 textureImages=6 first=[0x122...,0x123...,...]
CELTIUM_WINDOW_PIPELINES distinct=1 texture blends=true depthWrite=false depthTest=false
CELTIUM_MODELVIEW_EXACT  distinct=384 groups=384 placements=64 perTileRotations=[6:64]
```
⇒ **受理側の解決は完全に正しい**: 6面のテクスチャ・6つのdescriptor・384の正しい行列・
blend有効/depth書き込み無効・route=frame-image・射影も正しい。

**それでも描画結果は巨大なフォント字形**。考えられる残りの説明は
「**6つのdescriptorが指す先の画像が、実際には別のもの（フレーム画像など）になっている**」こと。
フレーム画像をパノラマのクアッドで拡大サンプルすると、直前までに描かれたGUIの文字が
巨大化して見える（＝観測と一致）。

**次の一手**: `textureImage`（6個）それぞれについて
- レジストリ上の名前・サイズ
- その画像が「フレーム画像に公開されている」か（publish経路の有無）
を記録し、6つが 256x256 のパノラマ画像を指しているか、フレーム画像を指していないかを確定する。

**パノラマ①: 画像ハンドルも6枚の256x256で正しい（2026-10-24 実測）**

```
CELTIUM_WINDOW_IMAGES distinct=6 images=[256x256@0x7e.. 256x256@0x8c.. 256x256@0x93..
                                            256x256@0x9a.. 256x256@0xa1.. 256x256@0xa8..]
```
⇒ パノラマ384グループの descriptor が指す画像は**6枚の256x256**で、フレーム画像ではない。
これで受理側の全要素（行列・並進・回転・テクスチャ名・descriptor・画像・パイプラインのblend/depth・
経路・射影）が**すべて正しい**と確定した。

**残る候補**（データではなく実行/座標系）:
1. **UV/テクスチャ行列**（同じ256x256のどこを引くか。texture matrix の適用漏れ・二重適用）
2. **draw時のdescriptor束縛**（record時の解決は正しいが、bindの実効性。bindCacheの取り違え）
3. キャプチャしたフレームが別フレーム（提示画像の遅延）※静止比較ではF2と0差分だったので可能性は低い

**次の一手**: 384グループの `textureMatrix`（アフィン6値）が非自明か、null（恒等）かを数える。
パノラマはテクスチャ行列を使わないはずなので、**非nullが出たら誤適用**＝UVがずれる直接原因。

**パノラマ①: テクスチャ行列は無し（2026-10-24 実測）**

```
CELTIUM_WINDOW_TEXMATRIX withMatrix=0 groups=384 first=none
```
⇒ 384グループはどれも texture matrix を持たない（＝恒等）。UVは頂点データのまま。

これで受理側の**全項目**が正しいと確定:
行列（384種）・並進（64）・回転（各配置6種）・テクスチャ名（34〜39）・descriptor（6種）・
画像（6枚の256x256）・pipeline（blend有効/depth書き込み無効）・経路（frame-image）・射影・
texture matrix（無し）。

**残る候補は「頂点データの解釈（vertex format/UV）」か「draw時の束縛実効性」**。
パノラマは `DefaultVertexFormats.POSITION_TEX_COLOR`（位置3f・UV2f・色4ub）で描かれ、
ワールドの即時描画とは**別のvertex format**を使う。ここを取り違えるとUVだけが壊れ、
「同じテクスチャの別領域を引く」＝巨大な別絵に見える。

**次の一手**: この384グループの `formatId` を記録し、その format の属性オフセット/strideを
`VulkanImmediateFormats` の定義と突き合わせる。POSITION_TEX_COLOR が正しくマップされているか確認。

**パノラマ①: vertex format の対応も正しい（2026-10-24 原典確認）**

`VulkanImmediateFormats` の定義を確認:
```
POSITION_TEX_COLOR  pos3f tex2f col4ub   stride 24   id=2   VulkanImmediateTexturePipeline
```
パノラマの 384グループは `format=2`（PREP_TEXログで確認済み）で、これは POSITION_TEX_COLOR の
正しい対応。**よって vertex format の取り違え仮説も棄却**。

受理側で測れる項目はこれで**全て正しい**ことになった:
行列384/並進64/回転6/テクスチャ34-39/descriptor6/画像256x256×6/format=2/pipeline blend/
route=frame-image/射影/texMatrixなし。

**残るのは「受理後の実行」だけ**。疑うべきは:
1. この window が **frame image に描かれる前に GUI が先に描かれている**（順序）
2. window の render pass の loadOp/clear が panorama を消す/混ぜる
3. 提示フレームが別フレーム（静止比較ではF2と一致していたので低い）

**次の一手**: 384グループのwindowを描く直前に **frame image の内容をreadbackして平均色を出す**
（描く前の状態）。パノラマ前は「GUIが未描画の黒/空」であるべきで、そこに既に文字があれば順序の誤り。

**パノラマ①: 画面の内容は「全部我々の描画」と確定（2026-10-24 決定的）**

`-Dceltium.debug.skipDrawCalls=true` でメニューを撮影すると **全面真っ白（mean=255, nearWhite=1.0000）**。
⇒ ロゴ・ボタン・文字・例の「巨大な形」まで、**すべてCeltiumの即時描画から出ている**。
（GUI側の別経路や残像ではない。）

したがって「巨大な形」は、パノラマ384クアッドが**引いている内容そのもの**。
バニラのパノラマは「画面全体を覆うほぼ同一のクアッドを64×6枚、alphaを変えて重ねる」構造なので、
**alpha（頂点色のA）やブレンド係数が壊れていると、重なりが暗い断片のモザイクになる**（観測と一致）。
参照画像が「ぼけた風景」なのは384枚が正しく重なっているため。

**次の一手**: パノラマグループの**頂点色（RGBA 4ub）**を1グループぶんダンプし、
バニラが書く `color(255,255,255,l)` の l（0〜1）が正しく読めているか、A が 255 に潰れていないかを確認。
同時に、選ばれた pipeline の blend 係数（SRC_ALPHA, ONE_MINUS_SRC_ALPHA）が
バニラの `tryBlendFuncSeparate` と一致しているかを照合する。

**パノラマ①: 頂点アルファはバニラの重みと一致（2026-10-24 実測）**

頂点バッファ（format 2、stride 24、色は offset 20..23）から alpha を採取:
```
CELTIUM_WINDOW_VERTEX_ALPHA distinct=11 values=[255,127,85,63,51,42,36,31,28,25,23]
firstQuad=[255 255 255 255]
```
この並びは **alpha = 255/(j+1)**（255,127,85,63,51,42,36,31,28,25,23）と一致。
⇒ 頂点色（バニラが書く `color(255,255,255,l)`）は**正しく捕捉・格納されている**。

**ここまでで棄却/確定した項目**（すべて実測）:
行列384・並進64・回転6/配置・テクスチャ34-39・descriptor6・画像256x256×6・format2・
pipeline blend有効/depth書き込み無効・route frame-image・射影・texMatrix無し・**頂点alpha**。

残るのは **実行時のブレンド係数** と **draw時のdescriptor束縛の実効性**。
（skip-drawで全面白＝内容は全て自前の描画、という事実も確定済み）

**次の一手**: この pipeline の `colorBlend`（srcColor/dstColor/srcAlpha/dstAlpha）を数値で出し、
バニラの `tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` と比較。

**パノラマ①: ブレンド係数もバニラと一致（2026-10-24 実測）**

```
CELTIUM_WINDOW_PIPELINES ... blend=blend[rgb=6,7 a=1,0 op=0/0 mask=0x7]
```
Vulkanのenum値で rgb=(6,7)=**(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)**、alpha=(1,0)、加算命令(0,0)、
書き込みマスク0x7=RGBのみ。バニラが `tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE,
ONE_MINUS_SRC_ALPHA)` で設定する色合成と**一致**している。

⇒ 受理側の全項目＋アルファ＋ブレンド係数まで**すべて正しい**。

**残る唯一の層 = テクスチャ画像の中身（アップロード/mip/サンプラ）**。
ミラー登録ログでは id=34〜39 が `source=announcement ... opaque=65536/65536` と「内容はある」と出るが、
これは**CPU側の記録**であり、GPU画像へ実際に書かれたかは別。もし画像が未アップロード（未初期化）なら
サンプル結果は不定（白っぽい塊）になり、観測（淡い巨大な形）と整合する。

**次の一手**: id=34〜39 が「fullyInitialized」かどうかを記録し、未初期化なら
`uploadFromGlTexture` 経路（announcement からの供給）がメニュー時に走っているかを追う。

**パノラマ①: テクスチャ画像は `full`（内容あり）。受理側は完全に健全（2026-10-24 実測）**

```
CELTIUM_WINDOW_IMAGES distinct=6 images=[256x256/full@0x7e.. 256x256/full@0x8c.. ... ]
```
⇒ 6枚とも「中身あり」。これで**受理側の全項目＋alpha＋blend＋画像内容**まで全て正常と確定。

**残るのは「どの画像に描かれたか」の実行時ルーティング**。過去ログの `CELTIUM_BIND_ENTRY` は
```
target=0x0 current=0x440a6f4c pending=78 ... recorded=391
```
と、**大きなウィンドウが2つ**あることを示す:
- identity 0x0 の384グループ（パノラマ、ログの route=frame-image）
- identity 0x440a6f4c（ゲームのmain FB）側の記録

`GllessFrameTarget.routeLabelForBinding` は「main→frame image / identity 0→**default pair** / その他→chain pair」
という規則を持つとコメントされており、ログの route 表示と**規則の食い違い**が疑わしい。
（ログは frame-image と言うが、コメント上の規則では identity 0 は default pair。）

**次の一手**: フレーム内で「パノラマウィンドウ描画の直前と直後」に
frame image と default pair の**平均色をreadback**し、どちらにパノラマが入ったかを確定する。
入っていない方に描かれているなら、それが直接の原因（合成が読まない画像に描いている）。

**パノラマ①: 最重要の手がかり（2026-10-24 実測＋推論）**

メニュー時のコピー経路ログ:
```
CELTIUM_READ_RESOLVE readFbo=0 mainIdentity=25503723 mainColorName=3 currentTarget=0
  currentIsMain=false frameImage=... defaultPair=0 chainPair=0
CELTIUM_TEX_COPY_SERVED dest=23 level=0 destOffset=0,0 source=0,0 256x256 readFbo=0
  mean=107,120,138 opaque=65536/65536 served=1
```
⇒ 読み出しは成立し、コピー先テクスチャ23の中身は**青空っぽい平均色**で不透明。つまり経路は動いている。

一方、ミラー登録ログでは id=34〜39 の**CPU側の内容**は正しい色（0xff8fa3ff 等）。
しかし `hasFullContent()` は「アップロード済み」の意味しか持たず、**GPU画像に何が書かれたか**は未検証。
もし6枚が**白で初期化**されていれば、パノラマは「白い形」をalpha合成したものになり、
**観測（淡い＝白っぽい巨大な形）と完全に一致**する。

**次の一手（決定的）**: id=34〜39 の**GPU画像の平均色をreadback**する。
- 白（255,255,255）→ アップロード経路が白埋め＝これが原因。修正は「announcement→GPUアップロード」の配線
- 写真の色 → テクスチャは正常。サンプラ/mipを疑う

**パノラマ①: GPU画像の中身は写真で正しい。だが背景は「拡大されたフォントアトラスの格子」に見える（2026-10-24）**

新計器（`-Dceltium.debug.sampleWindowImages=true`）でパノラマ6画像の**GPU内容**をreadback:
```
CELTIUM_WINDOW_IMAGE_MEANS means=[198.3 169.2 66.6 41.6 184.4 79.3]
```
白ではない。写真として妥当な平均色。⇒ テクスチャ画像は正しい。

しかし最新のメニュー画像（`/tmp/menu26.png` 相当）を目視すると、背景は**巨大な文字の格子**で、
フォントアトラス（4096x4224 の字形グリッド）を拡大したものに見える。
全画面を覆う描画が**アトラスをサンプルしている**なら、まさにこの見え方になる。

**現在の矛盾**: グループの `texture` は34〜39（写真）、`textureImage` も写真、GPU内容も写真。
それでも描画結果はアトラス的。⇒ **descriptor set の中身**（どの view が書かれているか）が
受理側の記録と食い違っている可能性が高い（6個のset値は違うが、中身が同一/別物の疑い）。

**次の一手**: `descriptorSetFor(texture)` が書く `texture.view()` と、
グループが持つ `textureImage.view()` を**照合してログ**に出す。
一致していれば descriptor は正しく、次はサンプラ/mip。不一致ならキャッシュのキー衝突を修正。

**パノラマ①: descriptor生成コードは `texture.view()` を書いている（原典確認）**

`VulkanImmediateTexturePipeline.descriptorSetFor` を読む限り、
```
image.get(0).imageLayout(texture.samplingLayout())
        .imageView(texture.view())
        .sampler(texture.sampler());
...
this.setsByTextureView.put(texture.key(), set);
```
と、**テクスチャ自身の view** を書いており、キャッシュも `texture.key()` 単位。コード上の取り違えは見当たらない。

一方、壊れた背景は「白くピクセル状の巨大な字形」で、
- フォントアトラス（4096x4224）を拡大したもの、よりは
- **`textures/gui/title/minecraft.png`（白い文字・低解像度・透明背景）**を拡大した見え方に近い。

つまりパノラマのクアッドがどこかで**ロゴテクスチャ**を引いている可能性。
次の計器候補:
1. `descriptorSetFor` のキャッシュミス時に `key` と `view` と `texture.width x height` を境界付きで記録し、
   パノラマ期間に生成された set が 256x256 の6枚を指しているかを確認
2. 実際に描画で使われた descriptor を draw 時に記録（bindCache[5] の変化）

（このラウンドはコード変更なし・原典確認と画像の再解釈のみ）

**パノラマ①: 頂点データは完全にバニラ通り（2026-10-24 実測）**

最初のグループの4頂点を実ダンプ:
```
count=4
(-1.000,-1.000,1.000 | uv 0.000,0.000 | 255,255,255,255)
( 1.000,-1.000,1.000 | uv 1.000,0.000 | 255,255,255,255)
( 1.000, 1.000,1.000 | uv 1.000,1.000 | 255,255,255,255)
(-1.000, 1.000,1.000 | uv 0.000,1.000 | 255,255,255,255)
```
- 位置 ±1（バニラのタイル板）、UV 0..1、色 255,255,255,alpha=255（j=0のタイル）
- グループ毎のalphaは 255,127,85,63,…（=255/(j+1)、既測）で正しい

⇒ **頂点バッファ・属性レイアウト・UV・色・alpha まで完全に正しい**（format2: pos R32G32B32@0,
uv R32G32@12, colour R8G8B8A8_UNORM@20, stride24 も原典一致）。

パノラマの受理データは「全部正しい」で完全に閉じた。
残るのは**この正しいデータを描く実行側**だけだが、pipeline state（blend=SRC_ALPHA/ONE_MINUS_SRC_ALPHA,
depthWrite=false）、画像内容（GPU meanが写真）、descriptor生成（viewを正しく書く）も確認済み。

**次の一手（描画回数の確認）**: メニューフレームで `drawn[perFrame=]` が384か（=1フレームに1回ずつ）を確認。
384より多いなら同じ384クアッドが複数回描かれていて、alpha合成が「不透明な1枚」に収束してしまう
（＝シャープな巨大画像の説明になる）。

**パノラマ①: タイルの並進は 0.0078 と正しい大きさ（2026-10-24 実測）**

```
CELTIUM_WINDOW_TILE_OFFSETS first=[(0.0078,0.0078) (0.0078,0.0078) ... (0.0078,0.0059)]
```
- 0.0078 = 1/128 = **vanilla の f = ((j%8)/8 - 0.5)/64** と同スケール（±0.0078〜±0.055 の格子）
- つまり 384 層はほぼ同一位置に微小オフセットで重なる = **本来はぼける**

ここまでで、受理データ（頂点・UV・色・alpha・行列・並進量）、テクスチャ6枚とGPU内容、
descriptor、format、pipeline（blend有効・係数正しい）、描画回数（1フレーム1回）が
**すべて正しい**と実測で確定。それでも絵はぼけない。

**残る唯一の候補**: 「blend 有効」の記録はあるが、**実際に使われた VkPipeline が
ブレンド無効で作られたもの**である可能性（同一stateで複数pipelineが作られ、古い方が使われる等）。
**次の一手**: `create(...)` 内で `colorBlend.blend()` と生成した pipeline ハンドルを境界付きで記録し、
draw 時に使われた pipeline ハンドルと突き合わせる。

**パノラマ①: パイプラインキャッシュ鍵も正しい（2026-10-24 原典確認・新計器なし）**

`celtium$stateKey(formatId, state)` は **state の全値を畳み込む**（`key = formatId; for (value : state) key = key*31 + value`）。
したがって blend 有無/係数が違えば別キー＝別パイプライン。**「古いブレンド無効パイプラインの再利用」仮説は棄却**。
生成も `computeIfAbsent(key, ... celtium$blendState(state) ...)` で、state から作っている。

これで**実測可能な入力・状態・生成側はすべて正しい**と確定した。
残るのは
1. **テクスチャIDの対応そのもの**（34〜39 が本当に `textures/gui/title/background/panorama_*.png` か。
   我々は「256x256 で写真らしい平均色」までしか確認しておらず、*どのリソースか*は未確認）
2. サンプラの mip/LOD（maxLod=1.0、mipLevels は画像由来）

**次の一手（1回で決着させる）**: ロード時に `TextureManager` が panorama 画像へ割り当てた
**GLテクスチャ名を特定**する。方法は `CELTIUM_MIRROR_SOURCE` の id と、そのアップロード元
（リソースパス）を対応付ける計器を足すか、6枚の平均色を バニラの参照実装（Celeritas）側で
同じ計器で測って一致するかを見る。

**パノラマ①の根因を特定: textured family の shader が modelview を一切適用しない（2026-10-24 決定）**

`celtium-vulkan/src/main/shaders/immediate_tex_color.vert` の末尾:
```glsl
void main() {
    ...
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
```
- この family の **push constant に modelview が無い**（u_TexA/B はテクスチャ行列、fog 行は fog 専用）
- つまり **a_Position は「既にワールド空間へ変換済み」が前提**で、projection だけ掛ける

ところがパノラマの384グループは `identityViews=0`（＝modelview は非恒等のまま、**bakeされていない**）。
バニラはタイル毎に `translate(f,f1)` と `rotate(...)` を modelview に与えるので、
**変換が丸ごと落ちて 384枚が完全に同一位置に重なる**。
α合成は 255/(j+1) で進むため、結果は「**1枚のくっきりした写真**」に収束する
＝ 観測（ぼけない・シャープ）と完全一致。

テクスチャ同一性も外部参照と一致して確定済み（下記）。したがって原因はここで確定。
```
panorama_0..5.png の中央16x16平均 = 198.3 / 169.2 / 66.6 / 41.6 / 184.4 / 79.3
GPUからreadbackした6枚の平均   = 198.3 / 169.2 / 66.6 / 41.6 / 184.4 / 79.3  ← 完全一致
```

**修正方針**: textured family に限り、**非恒等 modelview のグループは受理時に頂点へbake**する
（`VulkanDynamicMesh.copyVerticesTransformed` は 3x4 全体を焼けるので回転も扱える）。
bake 後 group.modelView を恒等にすれば、shader の前提（position は変換済み）と一致する。

**修正第1版: textured family の非軸整列 affines を bake（2026-10-24 実測・効果なし）**

`celtium$preTransformable` を拡張し、format 1/2/6 で modelview が affine なら
回転・並進も bake するようにした（shader が行列を適用しない family のため）。

結果:
- **bake は発火した**（`CELTIUM_WINDOW_TILE_OFFSETS` が `(0.0078,...)` から `(0.0000,...)` に変化）
- **しかし絵は変わらない**（mean 105.4 / saturated 0.040 / 参照との差 65.70%、修正前と同一）
- ワールド受入は **PASSED 維持**

⇒ bake に使った `this.glModelView` が、グループ行列として記録されていた行列と**別物**（bake時は恒等）で、
実質何も変わっていない疑い。つまり「グループの行列」と「bakeに使う生きた行列」の**情報源が2つある**。

**次の一手**: パノラマの受理時に `this.glModelView` の並進 (m[12],m[13]) を記録し、
`currentGroupModelView` の値（0.0078）と一致するか比較。食い違うなら、
グループ行列の捕捉元と bake元を同一にそろえる（これが真の修正）。

**修正第1版の撤回と、その理由（2026-10-24）**

`copyVerticesTransformed` の実装を読むと、bake は
```
x' = sx*x + tx    y' = sy*y + ty    z' = sz*z + tz
```
の**対角＋並進のみ**（回転項は一切適用しない）。コメントにも
「every off-axis element is zero, which is what the caller checks」とある。
つまり適用条件を「affine一般」に広げた私の変更は、**回転を黙って落とす**（並進だけ焼く）ため、
正しさの面で改善ではなく**別の不正確さ**を導入していた。

さらに実測では絵が変わらなかった（mean 105.4／彩度0.040／参照差65.7%）。
⇒ 撤回し、`celtium$preTransformable` は元の「軸整列のみ」に戻した。

**次の一手（正しい順序）**:
1. `copyVerticesTransformed` を**完全な3x3回転＋並進**に拡張する（1頂点あたり9積和、コストは増えるが
   パノラマは384頂点×4＝1536頂点/フレームで無視できる）
2. そのうえで textured family の affine 一般を bake 許可にし、メニュー画像と参照を比較
3. ワールド受入（画像gate）で回帰が無いことを確認

**修正第2版: bake を完全な3x3へ（2026-10-24 実測）**

`copyVerticesTransformed` を**完全な3x3回転＋並進**に拡張（identity / 軸整列 / 一般の3経路、一般は1頂点9積和）。
そのうえで textured family（format 1/2/6）の affine modelview を bake 許可に戻した。

結果:
- **ワールド受入: PASSED**（problems 0）＝回帰なし
- **メニュー背景は依然として変わらない**（mean 105.2 / 彩度0.039 / 参照差 65.26%）

⇒ パノラマ384グループは「正しい形（pos±1, uv0..1, 写真テクスチャ）」で、変換も今は焼けている。
それでも画面が変わらないということは、**この背景を作っているのはこのウィンドウではない**可能性が高い。
（`skipDrawCalls` で全面白＝内容は我々の描画、までは確定しているが、*どの*ウィンドウかは未同定。）

**次の一手**: 背景を描いているウィンドウを同定する。方法は
- 全ウィンドウについて「グループ数・テクスチャ・射影・ターゲット・画面占有の推定」を1フレームだけ完全に記録
- もしくは `-Dceltium.debug.skipDrawCalls=true` をグループ条件付きで（例: texture==34..39 のみスキップ）適用

**パノラマ①: 背景を描いているのは「テクスチャ23を貼る8枚の全画面クアッド」だった（2026-10-24 決定的）**

ウィンドウ・センサス（1メニューフレーム）:
```
1) target=0x0  groups=384  textures=[34..39]  projection=[0.577,0.577,-1,-0.05]  ← パノラマ本体
2) target=0x0  groups=1    textures=[23]      projection=[0.005,-0.008,0,-0.5] ×8  ← 全画面クアッド（ぼかし合成）
3) target=0x26.. groups=40 textures=[23,10,40,41,42,43]                          ← ロゴ/ボタン
4) 小窓 textures=[7], [10], [3]
```
さらに **texture 34〜39 の描画をスキップしても背景は変わらなかった**（mean 105.3）。
⇒ 画面に見える背景は、**texture 23（256x256のコピー）を貼る8枚の全画面クアッド**が作っている。

これはバニラの手順と一致する:
パノラマを 256x256 のビューポート/フレームバッファへ描く → `glCopyTexSubImage2D` で texture 23 へ →
その texture 23 を全画面クアッドで拡大描画（＝ぼけた背景）。
**我々の絵は、この 256x256 コピー元の領域と、パノラマ本体が描かれた領域が食い違っている**疑いが濃い
（例: パノラマが 1280x720 全面に描かれ、コピーは (0,0)-(256,256) の「左下の角」を取っている →
角の断片を全画面に拡大＝観測された巨大なブロック状の形）。

**次の一手**: パノラマ384グループの**ビューポート**を記録（256x256 か 1280x720 か）し、
コピー元 (0,0,256,256) と整合しているかを確認。食い違えばビューポート適用（またはコピー元座標）が原因。

**パノラマ①: ビューポートは正しく256x256だった（2026-10-24 実測）**

センサスに viewport を追加:
```
groups=384 textures=[34..39] projection=[0.577,0.577,-1,-0.05] viewport=256x256   ← パノラマ本体
groups=1   textures=[23]     projection=[0.005,-0.008,0,-0.5]  viewport=256x256 ×8 ← ぼかし合成
groups=40  textures=[23,...]                                   viewport=1280x720   ← 画面への最終合成
```
⇒ **バニラの構造どおり**: パノラマは256x256に描かれ、256x256のコピーを経て、
最後に1280x720で全画面合成される。ビューポートの食い違い仮説は**棄却**。

残るのは**座標系**: Vulkanは y-down、GLは y-up。
- `setGroupViewport` が GL の (x,y,w,h) を Vulkan の viewport へ変換する際の y 反転
- `glCopyTexSubImage2D` の読み出し（readImageRectangle）の y 反転
この2つが食い違うと、**256x256のうち別の領域をコピー**して全画面に拡大 → 観測（巨大な断片）と一致。

**次の一手**: `setGroupViewport` が実際に渡す Vulkan viewport の y と、
コピー元矩形の y（readImageRectangle 内の変換）を並べてログに出す。

**パノラマ①: ビューポートy変換のコード確認（2026-10-24）**

`celtium$setGroupViewport` を読むと:
```java
int y = empty ? 0 : yBottom0;            // ← GLのyをそのままVulkan viewportへ
...
int scissorY = y;                        // コメントは「GLの左下原点をVulkanの左上へ反転」と書いてある
```
- **viewport は y を反転していない**（GLの y をそのまま渡す）。scissor も同値。
- もしアプリ内の画像が「row 0 = 画面の下端」（GL準拠のボトムアップ）で統一されているなら、
  この非反転は正しい。実際、ワールドもGUIもこの経路で正しく出ている。
- 一方 `readImageRectangle` は「row 0 は絵の下端」と明記しており、コピー元 y も同じ規約。

⇒ 少なくとも**コード上の規約は一貫**している（片方だけ反転という形ではない）。

**次の一手**: パノラマのコピー先 texture 23 の中身を**画像として**取り出して目視する。
`mean=107,120,138` までは測ってあるが、それが「ぼけた風景」なのか「別物」なのかは見ていない。
`TextureCopyService` の image 読み出しを PNG に落とす計器（debug用）を足せば、
texture 23 の内容そのものを検証できる。

**パノラマ①: texture 23 の中身は正しい風景だった（2026-10-24 決定的・新計器）**

`-Dceltium.debug.dumpTextureIds=23,34,42` を追加し、描画に使われたテクスチャの**GPU画像をPNGへ書き出した**。
```
CELTIUM_TEXTURE_IMAGE id=34 path=/tmp/celtium-tex-34-256x256.png 256x256 wrote=true
CELTIUM_TEXTURE_IMAGE id=23 path=/tmp/celtium-tex-23-256x256.png 256x256 wrote=true
```
texture 23（パノラマを256x256へ描いてコピーしたもの）を開くと、
**緑の草地・青い水面・石の崖・空**という正しいMinecraftの風景が写っていた。
保存: `docs/evidence/2026-10-24-panorama-copy-texture23.png`

⇒ **パノラマの描画とコピーは正しい**。texture 23 は「ぼける前の風景」として完璧。

したがって画面の異常（巨大な形）は、**この23を画面へ拡大合成する段**（8枚のぼかしクアッド＋
1280x720での最終合成）のサンプリング座標/UVのどこかにある。
texture 23 が正しい以上、原因は「合成側が23のごく一部だけを引いている」形。

**次の一手**: ぼかし8クアッドと最終合成クアッドの**頂点UV**を記録する
（`CELTIUM_WINDOW_VERTICES` は format 2 の最初のグループしか出さないので、texture 23 のグループに限定して出す）。

**パノラマ①: 最終合成クアッドのUVが 0.237〜0.763（中央53%だけ）だった（2026-10-24 決定的）**

texture 23 をサンプルする**最終合成（大きいウィンドウ）**の頂点:
```
id=23 groups=31
(0,240,-2000|uv 0.237,0.969) (427,240,-2000|uv 0.237,0.031)
(427,0,-2000|uv 0.763,0.031) (0,0,-2000|uv 0.763,0.969)
```
- 位置 0..427 × 0..240、射影 [0.005,-0.008,...]（＝2/427, 2/240）で**画面全体**を覆う ✓
- しかし **UVが 0.237..0.763** ＝ 256x256 の**中央53%だけ**を全画面に拡大している
  ⇒ 9.4倍拡大のブロック状 → **観測された「巨大な形」と完全一致**

バニラなら全画面合成は **uv 0..1**（テクスチャ全体）を引くはず。
0.237〜0.763 という値は「0..1 にアフィン変換が1回かかった」形。
我々は **テクスチャ行列をグループに捕捉**し、シェーダが `u_TexB.z>0.5` のとき適用する。
もし**捕捉時にUVへ焼き込み済み**なら、シェーダでもう一度掛かって二重変換になる。

**次の一手**: この合成グループの `texMatrix`（withMatrix）と、生のUV（BufferBuilder由来）を
並べて記録し、二重適用かを確定する。

**パノラマ①: 背景の文字は「7,9,10,40〜43」のどれかの描画（2026-10-24 スキップ実験）**

`-Dceltium.debug.skipTextures=<ids>` で描画を選択的に落として比較:

| スキップ対象 | mean | 彩度 | 所見 |
|---|---|---|---|
| （なし） | 105.3 | 0.042 | 灰地＋白い巨大文字 |
| 34〜39（パノラマ） | 105.3 | 0.041 | **変化なし** |
| 3 | 105.3 | 0.043 | 変化なし |
| 40〜43 | 89.5 | **0.000** | 青い風景が消え、灰一色に |
| 7,9,10,40〜43 | 89.0 | **1.000** | **文字が消え、青いグラデーションだけ** |

⇒ **画面の「巨大な文字」は 7,9,10,40〜43 のいずれかの描画**。
40〜43 を落とすと青い風景が消える（＝青は40〜43側）。全部落とすと文字も消えて青一色になる。
つまり文字層は **7/9/10 側**、風景層は **40〜43 側**の可能性が高い（次で1つずつ切る）。

なお texture 23（コピー）の中身は正しい風景（既測）で、UVも 0.237..0.763 のアスペクトcropで正しい。
**問題は合成の後段、別テクスチャの全画面クアッドが文字を描いている**こと。

**パノラマ①: 残る2つの全画面クアッドを実測（2026-10-24）**

1) **texture 9 = `textures/gui/title/mojang.png`（MOJANGロゴ, 256x256, GPUダンプで確認）**
```
id=9 groups=1 texMat=none
(0,720,-2000|uv 0.000,0.000) (1280,720,-2000|uv 0.000,0.000)
(1280,0,-2000|uv 0.000,0.000) (0,0,-2000|uv 0.000,0.000)
```
⇒ 画面全体を覆うが **UVが全部 (0,0)** ＝単一テクセルを引くだけ（バグ候補）。
ただしUV0固定では文字の格子は出ない。

2) **texture 7 = 1281x720（フレームサイズ）のフレームバッファ色テクスチャ**
```
id=7 groups=1 texMat=none
(0,720,-2000|uv 0,0) (1281,720,-2000|uv 1,0) (1281,0,-2000|uv 1,1) (0,0,-2000|uv 0,1)
```
⇒ **正しい全画面blit**（位置0..1281×0..720、UV 0..1、行列なし）。
GPUダンプはほぼ単色（2.7KB）＝この時点では内容が少ない。

**現時点の観測**: 34〜39を落としても絵は変わらず、7,9,10,40〜43を全部落とすと
文字が消えて青一色になる。個別の 3 / 40〜43 スキップでは文字は残る。
残る候補は **texture 7 の全画面blit**（フレーム画像を自分自身に引いている疑い＝フィードバック）。
その場合、画面左下に描かれた検証オーバーレイの文字が拡大されて全面に出る、という説明がつく。

**次の一手**: texture 7 の blit が「フレーム画像自身をサンプルしている」かを確認する
（7 の image ハンドルと frameTarget.colorImage() を比較する計器を足す）。

**パノラマ①: 個別スキップでは文字は消えない＝文字層はスキップ対象外の経路（2026-10-24）**

| スキップ | mean | 彩度 | 文字 |
|---|---|---|---|
| なし | 105.3 | 0.042 | あり |
| 3 | 105.3 | 0.043 | あり |
| 7 | 105.3 | 0.043 | あり |
| 9 | 105.4 | 0.043 | あり |
| 40〜43 | 89.5 | 0.000 | **あり**（青が消える） |
| 7,9,10,40〜43 | 89.0 | 1.000 | **消える**（青一色） |

個別にどれを落としても文字は残る。**組み合わせでのみ消える**＝文字層は
`skipTextures` が拾えていない描画（immediate直描以外の経路）から出ている可能性が高い。

追加で確定:
- **texture 7** は 1281x720 の**フレーム画像とは別の**FBO色テクスチャ（`selfSampling=false`）で、
  その全画面blitは位置0..1281×0..720・UV0..1・行列なしの**正しい形**。
- **texture 9** = `textures/gui/title/mojang.png`。画面全体を覆うが **UVが4頂点とも(0,0)** という
  明確な欠陥（本来 0..1 を張るべき）。これは別件として修正候補。

**次の一手**:
1. `skipTextures` を全描画サイト（terrain / UI / レコード経路 / post chain）に広げ、
   どのテクスチャの描画が文字を出しているかを1つに絞る
2. UVが全部(0,0)になっているMojangロゴquadの捕捉（format2のUV列）を調べる

**パノラマ①: texture 23 をスキップするとメニューはほぼ正しい（2026-10-24 決定的）**

`-Dceltium.debug.skipTextures=23`:
- 背景の**グラデーション（明→暗）が正しく出る**
- ロゴ・ボタン・スプラッシュ・文字すべて正常
- **左下に 256x256 の風景が正しく描かれている**（＝パノラマ本体の描画は正しい）
保存: `docs/evidence/2026-10-24-menu-without-23.png`

⇒ **壊れているのは texture 23 のチェーン（8枚のぼかし＋合成）ただ1つ**。
パノラマ描画・GUI・ロゴ・文字はすべて正しい。

そしてチェーンが「文字の格子」を出す理由は、**このチェーンが `frame画像の(0,0)-(256,256)` を7回コピーする**構造にあり、
バニラのGUI文字（画面左下）が同じコーナーに描かれた後にコピーが走ると、**文字がそのまま取り込まれて増幅**される。
（`copyCallsThisPresent` は最大7。ダンプした23は「最初のコピー直後」なので風景のままだった。）

**次の一手**: 7回目のコピー直後の texture 23 をダンプして、そこに文字が入っているかを確認する
（入っていれば「コピー元が window のコーナーであること」が根因。パノラマ専用のオフスクリーンを持つか、
コピー元をパノラマ描画時点のスナップショットに固定する修正になる）。

**パノラマ①: texture 23 はチェーンの全段で汚れていない／texture 7 もfeedback無し（2026-10-24 実測）**

ダンプを段ごとに残すよう変更（`-n1`〜`-n8`）。結果は**8枚すべて同一**で
mean=122.1・nearWhite=0.000、**文字は混入していない**（すべて風景）。
⇒ 「コピーがGUI文字を拾っている」という前ラウンドの仮説は**この経路では否定**された。

また texture 7 の全画面blitについて:
```
image=0x7fd165486cb0 frameImage=0x7fd07df44520 attached=0x7fd07df44520
selfSampling=false feedback=false
```
⇒ サンプル元は描画先ともフレーム画像とも別（自己フィードバック無し）。

**注意（交絡）**: texture 23 をスキップすると描画エポックが進まないため、
コピーのキャッシュ判定（`lastDrawnEpoch`/`immediateWindowsThisFrame`）も変わり、
**コピーチェーン自体が走らなくなる**可能性がある。「23をスキップすると正常」は
「23の描画が原因」ではなく「チェーン全体が止まった」の意味かもしれない。
この交絡を切るには、スキップではなく**合成クアッドだけを別テクスチャへ差し替える**
（例: 白1x1テクスチャ10で描く）実験が必要。

**次の一手**: `skipTextures` に「置換」モードを足し、texture 23 のグループを
texture 10（1x1白）で描画させる。背景が一色になれば23由来、文字が残れば別層。

**パノラマ①: texture 23 を白1x1に差し替えるとメニューは完全に正しい（2026-10-24 決定的）**

新計器 `-Dceltium.debug.replaceTextures=23:10`（スキップではなく**置換**＝描画は残す。交絡なし）。
結果（`docs/evidence/2026-10-24-menu-texture23-replaced-white.png`）:
- **グラデーション背景・ロゴ・ボタン・スプラッシュ・文字すべて正常。文字の格子は消えた。**

⇒ 画面の「巨大な文字」は **texture 23 をサンプルする描画**（8枚のぼかし＋合成）が作っている。

一方、23のGPU画像を8回ダンプすると全部**同じ風景（mean 122.1、文字なし）**。
これは**同じフレームの8パス**をダンプしていたため。フレームNでは
「フレームN-1のコーナー（ボタン・文字が描かれている）」がコピー元になり得る。
つまり **パノラマ描画より先にコピーが走り、前フレームのGUI内容を取り込んでいる**公算が高い。

**修正方針**: コピー実行前に「パノラマ（identity 0 の256ビューポート窓）」を必ず描画・提出し、
コピー元のコーナーが**当フレームのパノラマ**であることを保証する。
（`hasPendingImmediateWork()` が false でも描画が必要なケース、あるいは
identity 0 の窓がコピー時点で未フラッシュである可能性を、次に計器で確認する。）

**パノラマ①: 後半フレームの texture 23 には検証オーバーレイの文字が写っている（2026-10-24 決定的）**

ダンプを「present 120以降」に限定して再測定:
```
first frame : mean=122.1 nearWhite=0.000  （風景）
late frame  : mean=67.8  nearWhite=0.080  （文字を含む）
```
後半フレームの texture 23 を開くと、**逆さまの "Minecraft 1.12.2 / MCF 9.42 / Cleanroom 0.7.11-alpha"**
（＝検証クライアントが画面左下に描くオーバーレイ）がそのまま写っていた。
保存: `docs/evidence/2026-10-24-texture23-contains-overlay-text.png`

⇒ コピー元 `frame(0,0)-(256,256)`（画面左下）は、**当フレームのパノラマが描かれる前は
前フレームのGUI文字が残っている**。コピーがそれを取り込み、ぼかし8パスで増幅して全面に出す。

**修正の方向**: コピー実行時に「当フレームのパノラマ（identity 0 / viewport 256）が
既にフレーム画像の左下に描かれている」ことを保証する。
コピーサイトの flush を無条件化しても絵は変わらなかった（mean 105.3）ため、
flush ではなく **blit 経路の提出順（open command buffer の submit）** が原因の公算。
次は blit 経路の前後で `waitForIdle` を強制し、提出順を確定させる。

**パノラマ①: 提出順の修正は効かなかった（2026-10-24 実測）**

コピーサイトで「記録済みの描画を必ず先に提出する」ように変更（`hasPendingImmediateWork` のゲートを外し、
`celtium$flushBatchedSubmits` を無条件で呼ぶ）→
- メニュー画像: mean 105.2 / 彩度 0.040（**変化なし**）
- texture 23 のダンプ: 依然 mean 67.8 / nearWhite 0.080（**文字のまま**）

⇒ 提出順ではなく、**そもそもパノラマはその時点でフレーム画像の左下に描かれていない**。

**残る最有力仮説**: コピー元は本来**オフスクリーンの256x256 FBO**（パノラマ専用）であり、
我々の追跡ではそれが `readFbo=0`（＝ウィンドウ）として届いているため、
**ウィンドウの左下（前フレームのGUI文字）を読んでしまっている**。
傍証: センサスに `target=0xd9f667a groups=1` というオフターゲット窓が存在する。

**次の一手**: `glCopyTexSubImage2D` 呼び出し時の FBO 束縛追跡を確認し、
256x256 の FBO が bind されているのに readFbo=0 として届いていないかをログで確かめる。

**パノラマ①: コピー時には常に pending=true（2026-10-24 実測）**

コピーサイトに順序ログを追加（境界付き14行）:
```
CELTIUM_COPY_ORDER present=1 call=2 pending=true src=0,0 256x256
CELTIUM_COPY_ORDER present=2 call=1 pending=true src=0,0 256x256
...
```
⇒ **毎回 flush は走っている**。それでもコピー元の左下は前フレームの文字のまま。
つまり「flushが呼ばれていない」のではなく、**flush が描いた先（画像/パス）と
コピーが読む先が食い違っている**可能性が高い。

**次の一手（最終局面）**: コピー直前の flush 後に
「この時点で frame画像の左下256x256をreadbackした平均色」を測る。
それが風景なら描画先は正しく、blitの読み先が別画像。
文字ならパノラマがまだ描かれていない（受理順の問題）。

**パノラマ①: コピー元コーナーは「1フレーム目だけ風景、2フレーム目以降は文字」（2026-10-24 決定的）**

コピー直前（flush後）に frame画像の (0,0,256,256) を readback した:
```
present=1 call=2 pending=true  corner mean=122.4 bright=0.0000   ← 風景（パノラマ）
present=2 call=1 pending=true  corner mean=74.2  bright=0.0802   ← オーバーレイ文字
```
⇒ **1フレーム目は正しい**（パノラマがコーナーに描かれている）。
**2フレーム目以降は、コピーの時点でコーナーに前フレームの文字が残っている**。

つまり定常状態で「コピーの前にパノラマが描かれない」＝**フレーム内の順序/クリアの問題**。
GLならフレーム頭のクリア + パノラマ描画でコーナーが塗り直されるが、我々は
1) 毎フレームのクリアが効いていない、または
2) パノラマ描画がコピーより後になっている。

**次の一手**: 2フレーム目のフレーム内順序を記録する（受理されたパノラマ384グループの時刻と
コピー呼び出しの時刻、およびフレーム頭のclearの有無）。それで 1) と 2) を切り分ける。

**パノラマ①: フレーム2でも「パノラマ窓はコピーより先に描かれている」（2026-10-24 実測）**

ログを時系列で混ぜて読むと:
```
present=1: ... COPY_ORDER corner=mean=122.1 bright=0.0000  ← 風景
present=2: WINDOW_DRAW presents=2 groups=384 viewport=256x256 target=0x0   ← 先にパノラマ窓
present=2: COPY_ORDER present=2 call=1 corner=mean=74.3 bright=0.0802    ← その後にコピー、しかし文字
```
⇒ **描画順は正しい**（コピー前に384グループのパノラマ窓が描かれている）。

**重要な注意**: この `corner=` はホスト側の readback で、**GPUの完了を待っていない**。
したがって「present=2で文字」は「その時点で画像が文字」ではなく、
**デバイスがまだ描いていない（前フレームのまま）**を読んでいる可能性がある。
present=1で風景だったのは、待たなくても直前の描画が間に合っていたため説明できる。

**次の一手**: `corner=` の readback の前に `waitForIdle()` を入れて再測する。
それで「本当に文字」か「読むのが早すぎた」かが確定する。

**パノラマ①: 計器の待機漏れを修正したら、コピー元コーナーは風景だった（2026-10-24 決定的）**

`corner=` の readback と `celtium$dumpImage` の前に `waitForIdle()` を入れて再測:
```
present=1 call=2 corner=mean=122.0 bright=0.0000
present=2 call=1 corner=mean=120.8 bright=0.0000
present=2 call=1 corner=mean=126.0 bright=0.0000
present=3 call=1 corner=mean=120.8 bright=0.0000
```
⇒ **コピー時点のフレーム左下は正しく風景（パノラマ）**。
前ラウンドの「present=2 は文字」は**待機漏れによる古い読み**だった。

一方、`present>=120` の texture 23 のダンプは依然として文字（mean 67.8, nearWhite 0.080、
ここもwaitを入れてある）。⇒ **1フレーム内の後半のコピー（call 2〜7）が文字を取り込んでいる**。

**次の一手**: 同一present内の**全コピー呼び出し**のcornerを記録し、
call 1 と call 5〜7 で内容が変わるかを測る。変われば
「後半コピーの前にGUI文字がフレームへ描かれている（＝提出順序）」が原因と確定する。

**パノラマ①: 計器を待機付きに統一したうえでの現在地（2026-10-24）**

- `corner=`（コピー直前・wait有り）: present 1〜6 の全コピーで **mean 120〜126 / bright 0.0000**＝風景。
  ⇒ **コピー元のフレーム左下は毎フレーム正しくパノラマ**。
- `present>=120` の texture 23 ダンプ（wait有り）: **依然として文字**（mean 67.8, nearWhite 0.080）。
- 既存計器 `CELTIUM_COPY_CALLER`: `dest=23 256x256 windowBusy=false groups=383 recorded=1` →
  最初のコピー時点で未描画グループ383（＝パノラマ）が存在し、flushされている。

**矛盾**: コーナーは風景なのに、コピー後の23は文字。
⇒ 23へ文字を書いているのは **このコピー呼び出しではない**（別の経路／別のフレーム位置）可能性。
次の判別は「23へ書き込む全経路」の列挙（copy以外の書き込み、blurパスのフィードバック、
または23のミラー差し替え）を計器で確認すること。

**パノラマ①を修正（2026-10-24）: window由来コピーはhostルートを使う**

`-Dceltium.vulkan.windowCopyBlit=false`（＝window読みのコピーをhostルートで提供）を**既定**に変更した。
根拠（同一JAR・AMD RX 6400・leg menu69 / menu70）:
| 経路 | mean | 彩度 | 参照との差 |
|---|---|---|---|
| blit（従来） | 105.3 | 0.042 | 65.63% |
| **host（新既定）** | **112.0** | **0.303** | **43.35%** |
- host経路のメニュー画像は**ぼけた風景**（`docs/evidence/2026-10-24-menu-panorama-fixed.png`）
- ワールド受入は **PASSED（problems 0）** を維持（leg `windowfix-acc`）

計器の待機漏れも修正済み: `corner=` と `celtium$dumpImage` の前に `waitForIdle()`。
これが無いと「コピー元が文字」という**古い読み**を測ってしまう（実測で訂正）。
順序の証拠: wait付きで corner は present 1〜122 すべて mean 118〜126 / bright 0.0000（風景）。

**残課題（性能）**: menuでは1 presentあたり最大7コピー（`textureCopiesPeakPerPresent=7`）が
hostルートを通る。blitルートは同じ画像を読むのに誤った内容を焼くため、blitのy/提出順の
不整合を直せばhostルートを外せる。menuの体感速度は未測定。

**パノラマ①の後始末: コピー元readbackをpresent単位でキャッシュ（2026-10-24 実測）**

menuのぼかしは「同じ (0,0)-(256,256) を7回、行き先オフセットだけ変えてコピー」なので、
読み出しは1回/presentで足りる。`-Dceltium.vulkan.cacheCopySource=false` で無効化できる。

| leg | readbacks | mean | 彩度 | 参照差 |
|---|---|---|---|---|
| menu69（キャッシュ無し） | **756** | 112.0 | 0.303 | 43.35% |
| menu72（キャッシュ有り） | **119〜130** | 112.2 | 0.321 | 43.84% |

⇒ 画質を変えずに**読み出しが約6分の1**。

blitルート側も「無条件wait」を試した（menu71: mean 116.4 / 彩度 0.072 / 参照差 58.07%）が
hostルートに一致しないため、**既定はhostルートのまま**。blitのy/提出順の不整合は未解決の負債。

**性能回帰の追跡（2026-10-24）: affine bake が world を ~30% 遅くしていた**

同一マシン・同一KPIハーネスでの測定（celtium warm p50 ms / paired ratio）:
| JAR | 内容 | celtium p50 | 比 |
|---|---|---|---|
| `texmat` | affine bake 無し | 1.542 | **1.304x** |
| `final1` | affine bake 全面 | 2.030–2.103 | 1.742x |
| `final2` | bake を world 外に限定 | 1.991–2.035 | 1.682x |
| `final3` | + window copy も world 外のみ host | 2.019–2.050 | 1.687x |

⇒ `texmat` 比で final系は依然 **1.68x**。bake の world ゲートでは戻らなかったので、
**残る回帰は別の変更**（world パスの窓コピー or その他）。二分探索の次の候補は `bake` JAR
（bake あり・window修正前）。※KPIは `CELTIUM_INSTANCE_WORLD` を明示しないと
0.6.13 instance を掴んで即死する（leg final3-blit が exitCode 255 / Cleanroom 0.6.13）ので、
次回は必ず env を付ける。

**性能: 回帰の帰属は未決（2026-10-24 追記）**

- `-Dceltium.debug.noAffineBake=true` で affine bake を実行時に切っても **2.083ms / 1.727x**（変わらず）。
- world leg のコピー統計は readbacks=0 / blits=0 → **コピー経路は world に関与していない**。
- したがって「texmat/bake JAR が 1.56ms」という以前の測定との差は、affine bake でもコピー経路でもない。
  同一セッション後半で `celeritas` は 1.19–1.27ms と安定しているため、機械全体の速度変化でも説明しにくい。
  次の判別は **同じ commit 84c220575 のソースで JAR を今ビルドし直して測る**こと
  （当時の JAR は削除済み。git の3ファイル差し替えで再現可能）。

**リリース v0.0.16-alpha（2026-10-24）**

- JAR: `celtium-forge-mc1.12.2-0.0.16-alpha.jar`
  sha256 `3aba97924eb881f4066aca51c1f069f5cd7b3d6b9e8b0e5482c22ba46f57998e`（3,229,034 bytes）
- ビルド: このcommitで `-Pceltium_release_version=0.0.16-alpha`
- 証拠（同一JAR）:
  - world 受入 leg `release-world`（AMD RX 6400、validation ON、180s）**PASSED・problems 0**
  - メニュー画像 leg `release-menu`（同JAR）: mean 111.8 / 彩度 0.315 / 参照差 42.75%
    → `docs/evidence/2026-10-24-v0.0.16-menu.png`
- 公開: https://github.com/souta-lab/celtium/releases/tag/v0.0.16-alpha
- 未決: KPI warm p50 2.02–2.08ms（参照比 約1.7x）。同日午前の同一コード系は 1.56ms / 1.31x。
  affine bake 無効化・window copy 経路切替のいずれでも不変で帰属未決。次は 84c220575 再ビルドJARとの A/B。
- ディスク整理: 完了runは直近の `release-world` / `release-menu` のみ、staged jarは `jars/release` のみを残した。

**性能: 「1.56ms の速いJAR」は再現せず＝環境差だった（2026-10-24 追記・決着）**

commit 84c220575 のソースから**今ビルドし直したJAR**（`216f8fd7…`）と
v0.0.16-alpha リリースJAR（`3aba9792…`）を、同一セッションで連続してKPI（各1rep、240s）:

| JAR | celtium warm p50 | celeritas warm p50 | 比 | warm samples |
|---|---|---|---|---|
| 84c220575 再ビルド | 2.052ms | 1.313ms | **1.563x** | 1450 |
| v0.0.16-alpha | 2.351ms | 1.349ms | **1.743x** | 1921 |

⇒ 朝に測った 1.56ms / 1.31x は**同じソースでも再現しない**。マシン状態（負荷/クロック）の差であり、
「コード回帰」ではなかった。ただし今回の測定は**サンプル数が少なく p95 が 25–35ms と荒れている**
（以前は p95 2–5ms・4300–5700サンプル）ため、1.563x と 1.743x の差も**この状態では判定に使えない**。
性能の結論は「環境が落ち着いた時に測り直す」。

**④ステンドグラスのちらつき: 静止ポーズでは再現せず（2026-10-24 実測）**

`-Dceltium.debug.flickerSample=true` に加えて、比較している2枚をPNGで保存する
`-Dceltium.debug.flickerDump=true` を追加した（`/tmp/celtium-flicker-prev.png` / `-cur.png`）。

- 世界: `worldLayers` で水/溶岩/火を除きステンドグラスを2層追加
- leg `glass-on`（100s, 静止）: `fraction=0.00269 maxDelta=255`
- leg `glass-off`（ガラス無し）: `fraction=0.00126 maxDelta=224`
- leg `glass-dump`（差分画像を取得）: `fraction=0.00161`、差分の位置は
  **画面右上の小さな明部（太陽/空）と単ピクセルのスペックルのみ**、bbox x=665..1279 y=0..419。
  ステンドグラスの面・辺に沿った差分は出ていない。

⇒ 静止ポーズの「フレーム間差分」はガラス起因ではない（小さな明部とノイズが支配的）。
④の再現には**ガラスが画面を占めるポーズ**と**視点移動**が必要で、現行fixtureでは作れない。
次の手順: fixture に「ガラス越しの近接ポーズ」を追加し、ゆっくり視点を動かしながら
2フレーム差分を測る（計器は本ラウンドで揃った）。

**コピー元キャッシュは撤回（2026-10-24 実測・v0.0.16の簡略化を戻す）**

キャッシュ（present+矩形キー）を**新しい読みと突き合わせて検証**した結果:
```
CELTIUM_COPY_CACHE_VERIFY present=1 differing=65374/65536
```
⇒ 同じpresent内の7回のコピーの間で、コピー元の矩形内容はほぼ毎回変わっている（7回の間に
その隅へ描画が入る）。したがって矩形キーのキャッシュは**構造的にstale**。
内容マーカー（copyMarker）をキーに足すと**ヒットは0回**（検証ログが出ない）＝キャッシュは無意味。

**撤回**: キャッシュと検証計器を削除し、毎回readbackに戻した。
leg `no-cache`（AMD、メニュー保持）: mean 111.9 / 彩度 0.304 / 参照差 42.49%・PASSED。
キャッシュ有り（112.2 / 0.321 / 43.84%）と画質同等で、**staleの危険だけが消える**。
v0.0.16-alpha には矩形キーのキャッシュが入っている（画質差は測れていない）ので、次版で本修正を載せる。
