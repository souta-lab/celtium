# Celtium — Codex handoff prompt

以下を開発引き継ぎの指示として読んで作業を続けてください。初期実装のチェックポイントであり、完成したVulkan modではありません。

更新：2026-10-08のCodex引き継ぎ後。ユーザーの追加指示で「Celtium（Celeritas fork）の完成」をactive goalへ設定済み。以下の合意済み方針は継続する。

## 目的と合意済み方針

Minecraft 1.12.2 / CleanroomMC向けに、CeleritasをforkしたCeltiumを開発する。地形だけネイティブVulkanバックエンドへ移し、既存modとの互換性をできるだけ維持する。

- 既存のCleanroom+Celeritas+LWJGL 3で大多数のmodが安定して動くことが前提。旧mod全般のLWJGL移行問題を主題に戻さない。
- Celeritasのメッシュ生成、圧縮頂点形式、可視判定、更新管理、半透明ソート、mod連携を継承する。
- GUI、Entity、TESR、粒子、空、手、描画イベントなどは従来GLのまま。地形との描画順・深度の整合を守る。
- Zinkは任意の追加検証であり、必須依存・mod内への同梱を本線にしない。
- OpenGLバックエンドを残す。mod IDは`celeritas`を維持し、元Celeritasとの同時導入はしない。
- 別バージョンのLWJGLランタイムやnativeをmod jarへ同梱しない。
- 実際の色・深度・同期の相互運用を実証する前に、大規模なGPU抽象化や地形描画の改修を始めない。
- READMEの最初の斜体の元素名説明、上流履歴、LGPLとクレジットを維持する。

ユーザーは実装と`https://github.com/souta-lab/celtium`への反映を依頼済み。routineな開発判断や可逆な修正で毎回確認を求めず、段階ごとの実装・検証結果と残課題を明確に報告する。今回はChatGPTからCodexへ引き継いだため、以降の開発を担当する。

## ディレクトリとソース

- 実機の作業ディレクトリ：`/home/s0u7a/projects/celtium`
- ブランチ：`main`。開始時に`git status`と`git log`で現在の状態を確認する。
- `origin`：`https://github.com/souta-lab/celtium.git`
- `upstream`：`https://github.com/GTNewHorizons/Celeritas.git`。本来の上流は`https://git.taumc.org/embeddedt/celeritas`。
- 固定したfork元：`989ebf2e52ceed052ec112a569e0c1d172095756`（GTNH mirrorの`nh-main`）。ユーザーの導入済みjarと一致すると確認したわけではない。
- 最初に`README.md`、`docs/design.md`、`docs/roadmap.md`、`docs/validation-2026-10-08.md`を読む。
- `.celtium-work/`はgit除外の作業用ディレクトリ。既存ログ・実機JSON・JDK・転送用ファイルがある。ソースの原本は通常のrepo内ファイルであり、転送用JSONを再適用しない。

## 実装済み

1. 上流履歴を継承したfork、README、設計・工数・ロードマップ、CIの初期設定。
2. 独立モジュール`celtium-probe/`：Java 8互換クラス、JDK 21以上でビルド。独立CLIはLWJGL 3.4.3を使う。
3. GLのdevice/driver UUID、外部memory/semaphore FD拡張、Vulkan物理デバイスのUUIDと拡張、opaque-FDバイナリセマフォの両方向、指定色/深度画像形式を問い合わせる。
4. Vulkan API 1.2、同一GPUの有効UUID、拡張、色とD24深度形式などを確認する適格性判定。15件のテストが通過。
5. CLIは独立surfaceless EGL contextを作成し、`--egl-device N`で明示選択できる。`--require-compatible`は不適格時にexit 2。
6. `forge122`へ`/celtium`と`/celtium probe`を追加。後者はMinecraftのrender-thread taskで既存GL contextを問い合わせ、`logs/celtium-capabilities.json`に保存する設計。probeエントリは反射で遅延ロードし、通常起動ではVulkanを初期化しない。
7. Forge jarには自前probeクラスだけを取り込む設定。LWJGL依存は推移的に取り込まない。ingame probeには、実際のCleanroomと一致するVulkan bindingsが別途classpath上に必要。
8. 1.12.2専用の`build-1122/`を追加。共有source dirと必要な上流build helperを直接参照し、Stonecutterを読み込まない。
9. 引き継ぎ後にFabric／NeoForgedのplugin repository、probe jarのvariant、RFGのJDK要件、コマンドの`gameDir`フィールド、Shadowの外部Class-Path・module-info混入を修正。Forge jarを生成し、`scripts/verify_forge_jar.py`でmod ID・manifest・全699クラスのJava 8互換性・ライセンス・LWJGL/native未同梱を確認した。
10. 本人が用意したCleanroomインスタンスを固定し、`scripts/prism_validation.py`で共有cacheを読み取り、独立したtest copyを起動する。認証情報は読まない。別jarの`validation/client/`が固定flat world・描画・コマンド・FBO観測を実行する。fixtureはmod jarへ同梱しない。
11. 固定上流とCeltiumのworld起動・描画・終了を確認。`/celtium`と`/celtium probe`は実クライアントで成功。現profileにVulkan bindingsがないことを診断JSONへ記録し、OpenGL描画が継続することを確認した。probe 15 testsを再実行済み。Java 8でLWJGLなしのoptional entry smokeも通過。
12. 独立`celtium-interop/`でRX 6400とUHD 630のRGBA8/D32外部画像import、GPUによるD24↔D32変換、両方向の遮蔽、15段階の深度精度、4往復/frame、binary semaphoreと外部ownershipを実証した。64×64/1280×720/1920×1080を12回再生成し、未完了GL/Vulkan workをfenceでretireしてから破棄する。両GPUのvalidation error/warningは0、FD数は一定（26/34）。
13. 別のvalidation modへinteropの自前class/shaderを追加したtest copyで、実Cleanroomの既存FBOでも両GPUの往復を確認した。元FBO/texture/renderbufferを維持する。手順は`docs/interop-validation.md`。Vulkan binding 3.4.1だけをtest classpathへ追加し、source profileのcore/OpenGL/nativeは変更しない。実験jarをapplication classpathへ置くと別のGL classloaderを使ってabortしたため、validation modへ埋め込み、Java capability/loaderをnative呼び出し前に確認する。

現状の地形はOpenGL。別実験でlogical device、画像import、セマフォ同期、深度の往復と限定したcopy/sync費用を実証したが、Vulkan地形描画と性能改善は未実装。検証済み契約はRGBA8/D24 renderbuffer、stencilなし、single sampleだけ。固定flat worldやsynthetic occlusionを広いMod互換性・game FPS改善の証拠に広げない。

## 実機とツール

Debian sid、i5-8400、RX 6400とIntel UHD 630、Mesa 26.2.4-1、Linux 7.2.9-x64v3-xanmod1。headless EGL診断は実行可能。本人が今回用意したinstanceは`/home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha/minecraft`。Cleanroom 0.6.13-alpha、Java 25.0.5-ea、LWJGL 3.4.1（coreは3.4.1-unsafe）、Fugue 0.24.4、Scalar 3.9.0。既存Prism/Minecraft instanceを勝手に変更しない。テストは`.celtium-work/runtime/`のcopyで実施する。

Gradle wrapperは9.7.1。システムのJava 21ディレクトリはcompilerのないJREなので、次の取得済みJDKを使う（GitHubの公式Temurin releaseからダウンロードし、SHA256確認済み）：

```sh
cd /home/s0u7a/projects/celtium
export JAVA_HOME="$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p celtium-probe test installDist --console=plain --no-daemon
```

JDK 8：`.celtium-work/jdks/8/jdk8u504-b01`。

Minecraft側はRFG 2.0.4のclass version 69のため、GradleをJDK 25で実行する。既存のシステムJDKをビルドへ使って成功したコマンド：

```sh
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace --no-daemon \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME,$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1,$PWD/.celtium-work/jdks/8/jdk8u504-b01"
python3 scripts/verify_forge_jar.py build-1122/build/libs/*/*.jar --output .celtium-work/forge-jar-verification.json
```

これらの個人環境の絶対パスはコミットするbuild設定へ埋め込まない。ゲームのJava・LWJGLは本人が用意した版を維持する。probe単独buildは取得済みJDK 21で成功済み。

## ビルドの経過と現在の注意点

- 変更前の上流root buildはStonecutter `0.10-alpha.9`の取得で失敗。`maven.kikugie.dev`に到達できなかった。
- `tiny-remapper`解決は修正済み。独立buildでcompile・remap・packaging・jar内容検査が通る。最新の成功ログは`.celtium-work/forge-build-verified.log`。
- 上流Mixinの既にSRG名になった`RenderItem.func_175032_a` aliasでremap warningがある。固定上流buildでも同じwarningがあり、両方のworld描画は通った。
- JDK 25をGradle、JDK 21をmod compiler、JDK 8をMinecraft preparationに使う。CIも3つのJDKを供給する設定へ修正済み。push `0e6fcc69b`のGitHub Actionsはアカウントの支払／spending limit理由でjob開始前に停止し、stepやbuild logがない。ローカル成功とCI成功を混同しない。
- README等の同梱文書を変更するとjar hashも変わる。配布前にpackageJarと内容検査を実行して最新の検証JSONを残す。
- fixtureは`:forge122:validationRemapJar`で別jarとして作る。実行例・結果の読み方は`docs/runtime-validation.md`。時間切れやfixture report欠落は成功扱いにしない。

## 重要なGPU診断結果

CLIで実GPUを明示し、UUIDを照合済み。以下の番号はこの実行時のEGL enumeration順であり、恒久的な番号ではない。

- `--egl-device 0`：RX 6400（GL radeonsi / Vulkan RADV NAVI24）。UUID一致。色RGBA8とD32系はimport/export capabilityあり、専用allocationが必要。**D24系2形式の外部画像queryは`VK_ERROR_FORMAT_NOT_SUPPORTED`（-11）**。要求D24条件を満たさずexit 2。
- `--egl-device 1`：UHD 630。要求形式などを満たしexit 0。ただしstatusは`CANDIDATE_FOR_INTEROP_TEST`であり実際のinterop成功ではない。
- `--egl-device 2`：llvmpipe。software deviceなどの理由でexit 2。
- index 99、Vulkan loader欠落、software GL強制も安全側にexit 2になった。

query条件はopaque-FD、optimal 2D、single sample、attachmentとtransfer src/dst usage。GPU全般のdepth対応を否定する結果ではない。引き継ぎ後のworld描画で実Minecraft FBOを観測し、RGBA8（0x8058）、D24 depth renderbuffer（0x81a6）、stencilなし、single sample、RX 6400であることを確認した。実drawableはdesktop tilingにより944×1018だった。

上記は当初のquery結果。別実験で、普通のD24 depth textureへの同形式コピー、GL shaderで共有D32へ変換、逆方向のshader変換を両GPUで実証済み。既存FBO/renderbufferのIDを維持する。sample/stencil/formatが異なる構成へこの成功を広げない。Intelでの成功をRadeonの成功として扱わない。Intelは同方式のcopy費用が大きく、性能改善をまだ主張できない。

`.celtium-work/gpu-tools/`へDebian sid公式packageからglslang-tools 16.6.0-1とvulkan-validationlayers 1.4.363.0-1をdownload・extract済み。system packageは変更していない。`extracted/usr/bin/glslangValidator`が動く。Validation Layersを使う場合は抽出したJSONのlibrary_pathとlocal library検索パスを確認する。

実機ログとJSONの位置は`docs/validation-2026-10-08.md`に記載。古い`rx6400-capabilities.json`は実際にはIntelを選んでいるので、GPU名の根拠にしない。

## 次の作業順

本人から追加要件：RX 6400専用の実装を完成形にせず、能力判定と複数GPU対応を本体の要件にする。実装選択は現在のGL device/driver UUIDと共有形式・usage・sample数・同期・必要featureに基づく。AMDとIntelの両実GPUで検証する。GPU名のハードコードで有効化しない。非対応条件では理由を示してGLを維持し、別GPUへ黙って切り替えない。

検証対象は本人の指定でRX 6400とUHD 630の2環境。NVIDIAの実機はないので未検証と記録する。この2 GPUの実証を完成要件に含め、NVIDIA実機の入手を完了条件として要求しない。

1. 現在のgit状態と上記docs/ログを読み、最新のbuild・fixture結果を確認する。依存解決とjar生成は解決済み。
2. ソースや同梱文書を変更した後はprobeの15 tests・Forge packaging・jar内容検査を実行する。renderer owner・camera記録を含むfixtureは両rendererで正常終了を確認済み。
3. 固定したCleanroom構成で、必要な代表場面と正式な性能基準測定を追加する。未確定の版を勝手に一斉更新しない。現段階のstartup frame intervalを速度改善の根拠にしない。
4. P1のinterop実験は両GPUで通過。最新fixtureは往復後の通常world描画を40 ticks継続し、元IDとGL errorを検査し、復帰スクショも保存する。再実行するときは`docs/interop-validation.md`を使い、Gradleの2 buildを同時実行しない。実験はprobeと別でmod jarへ混ぜない。
5. 次はP2：Celeritasの狭いGPU境界を分離し、まずGLだけで上流と表示・イベント順・Mod動作を比較する。CPUメッシュ生成、graph、sortアルゴリズムを変えない。GLの`CommandList`へVkBufferを詰め込まない。P3 native terrain、P4各レイヤー/更新/lifetime、P5最適化とQAの順に進む。`docs/design.md`のP0〜P6を参照する。

全体alphaは初期概算で約530〜1,100時間（約13〜28人週）。これは納期の約束でも実装済み工数でもない。現在はbuildと限定したP1契約の実証を済ませ、P2へ進む段階。問い合わせや小さいharnessの成功だけで「Vulkan地形化済み」「広い互換性確認済み」「高速化済み」と報告しない。

### Headless実行の希望

本人から、開発とスクショ取得に支障がなければheadlessで実行したいとの希望あり。以後のMinecraft検証は`prism_validation.py --headless --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'`を使う。独立Sway/Xwayland、1280×720、GPUレンダリング、無音で動かし、desktopへwindowを出さない。Celtiumのworld・2コマンド・正常終了・PNG保存・実GPU名を確認済み（`.celtium-work/runtime/celtium-headless-2/`）。実装と注意点は`docs/runtime-validation.md`。独立interop CLIも従来通りsurfaceless EGLを使う。
