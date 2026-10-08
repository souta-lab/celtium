# Celtium — Codex handoff prompt

以下を開発引き継ぎの指示として読んで作業を続けてください。初期実装のチェックポイントであり、完成したVulkan modではありません。

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

現状の地形はOpenGL。Vulkan logical deviceの作成、外部画像import、セマフォ同期、深度の往復、Vulkan地形描画、性能改善は未実装。ingame commandもMinecraftで未検証。

## 実機とツール

Debian sid、i5-8400、RX 6400とIntel UHD 630、Mesa 26.2.4-1、Linux 7.2.9-x64v3-xanmod1。headless EGL診断は実行可能。以前の探索では使用中のCleanroom 1.12.2 instanceを特定できていない。既存Prism/Minecraft instanceを勝手に変更しない。

Gradle wrapperは9.7.1。システムのJava 21ディレクトリはcompilerのないJREなので、次の取得済みJDKを使う（GitHubの公式Temurin releaseからダウンロードし、SHA256確認済み）：

```sh
cd /home/s0u7a/projects/celtium
export JAVA_HOME="$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p celtium-probe test installDist --console=plain --no-daemon
```

JDK 8：`.celtium-work/jdks/8/jdk8u504-b01`。

Minecraft側の直近の実行コマンド（現時点では下記依存解決で失敗）：

```sh
./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace --no-daemon \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME,$PWD/.celtium-work/jdks/8/jdk8u504-b01"
```

これらの個人環境の絶対パスはコミットするbuild設定へ埋め込まない。system Java 25 JDKもあるが、上記JDK 21でprobe buildの成功を確認済み。

## 未解決のビルド

- 変更前の上流root buildはStonecutter `0.10-alpha.9`の取得で失敗。`maven.kikugie.dev`に到達できなかった。
- 独立buildの2つの上流helperはコンパイル成功。
- その後、`:forge122`のplugin classpath解決が`net.fabricmc:tiny-remapper:0.11.0`で失敗。included build `plugins/celeritas-mdg-plugin`が要求する依存で、検索先がPlugin PortalとCentralのみになっている。
- 次は`build-1122/settings.gradle.kts`の`pluginManagement.repositories`にFabric Mavenが必要かを確認し、included pluginの他の依存元も合わせて解決する。未検証の推測なので、ログに基づき修正・再ビルドする。
- mod jarはまだ作れていない。コマンドのコンパイル・remap・パッケージング・missing-binding時の挙動も未検証。
- GitHub ActionsのMinecraft jobも現時点で通過を確認していない。
- 移行時点の上記ビルド・hardware probeプロセスは終了済み。

## 重要なGPU診断結果

CLIで実GPUを明示し、UUIDを照合済み。以下の番号はこの実行時のEGL enumeration順であり、恒久的な番号ではない。

- `--egl-device 0`：RX 6400（GL radeonsi / Vulkan RADV NAVI24）。UUID一致。色RGBA8とD32系はimport/export capabilityあり、専用allocationが必要。**D24系2形式の外部画像queryは`VK_ERROR_FORMAT_NOT_SUPPORTED`（-11）**。要求D24条件を満たさずexit 2。
- `--egl-device 1`：UHD 630。要求形式などを満たしexit 0。ただしstatusは`CANDIDATE_FOR_INTEROP_TEST`であり実際のinterop成功ではない。
- `--egl-device 2`：llvmpipe。software deviceなどの理由でexit 2。
- index 99、Vulkan loader欠落、software GL強制も安全側にexit 2になった。

query条件はopaque-FD、optimal 2D、single sample、attachmentとtransfer src/dst usage。GPU全般のdepth対応を否定する結果ではない。また、実MinecraftのFBO形式を観測したわけではない。

RX 6400向けにD32を使える可能性はあるが、D24からD32へ単純にdepth blitできると仮定しない。FBOの変更・変換・shader経由などの方式を選ぶなら、mod互換性、stencil、既存描画順、精度、コピー/同期コストを実証する。Intelでの成功をRadeonの成功として扱わない。

実機ログとJSONの位置は`docs/validation-2026-10-08.md`に記載。古い`rx6400-capabilities.json`は実際にはIntelを選んでいるので、GPU名の根拠にしない。

## 次の作業順

1. 現在のgit状態と上記docs/ログを読み、ビルドの依存解決を修正する。
2. probeの15 testsとForge packagingを通す。jar内のmod ID、manifest、自前probeクラス、ライセンス、LWJGL未同梱、bytecode互換性を確認する。
3. 実際の安定したCleanroom/Celeritas/modpack/Java/LWJGL構成を特定し、変更前の起動・表示・性能を記録する。未確定の版を勝手に一斉更新しない。
4. 実際のcolor/depth/stencil/sample形式を観測し、RX 6400のD24制約を踏まえた小さいinterop harnessを作る。両方向の深度遮蔽、binary semaphore、image layout、resize、resource寿命、同期/copy costを検証する。
5. ここまで通ってからCeleritasの狭いGPU境界分離とnative terrainへ進む。`docs/design.md`のP0〜P6と工数見積もりを参照し、根拠が増えたら更新する。

全体alphaは初期概算で約530〜1,100時間（約13〜28人週）。これは納期の約束でも実装済み工数でもない。今はP0/P1の途中で、まず「mod build成功＋実際のcolor/depth往復」の証拠を出す。問い合わせが成功しただけで「Vulkan化済み」「互換性確認済み」「高速化済み」と報告しない。
