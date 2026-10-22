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
