# Celtium goal — 2026-10-09 第3回監査反映

CeltiumをMinecraft 1.12.2 / CleanroomMC専用のネイティブVulkanレンダラとして完成させる。現在の根拠は docs/audit-2026-10-09-third.md（第3回監査）、docs/goal.md、更新したdocs/plan.mdに集約する。過去の履歴は証拠・経緯であり、現在の完了判定ではない。監査開始後の修正は新たなartifactと受入で確認する。

【維持する仕様】
- Cleanroom専用。通常Forge（旧Java/LWJGL2）や他Forge+lwjgl3ify/RFB構成の起動互換は提供対象外。ただしCleanroomで動く既存Forge modのMinecraft/Forge公開クラス・メソッド署名・イベント順序・modelデータの互換入口は可能な限り維持する。
- Celtiumの対象モジュールは --release 21 / class major 65以下。Java22以降のAPI・言語・previewへ依存しない。ゲーム・build・検証のJDK25は許可し、ゲーム全体のJava21起動を完成条件にしない。
- 検証済みCleanroom 0.6.13-alpha / LWJGL3.4.1 / Java25を基準に、実在するCleanroom公開API・正式Mixin/Accessor・直接LWJGL3を優先する。内部APIは依存理由と版を固定する。無根拠の反射、古いLWJGL用独自互換を惰性で増やさない。
- 汎用OpenGL→Vulkan変換器、GL API全体のエミュレーション、Zinkの組込みをこのgoalの目的にしない。Minecraftのcamera・terrain・model/item・entity/TESR・particle・UI・texture・FBO・postprocess・frameの意味を把握したnative passへ移す。共通VK allocator/buffer/descriptor/pipelineの再利用は許可する。
- GPU名ではなく必要なAPI/feature/format/surface能力で選ぶ。広いVulkan対応GPUで動くbaselineを設計し、不要なinterop/GL要件を純nativeから外す。mesh shaderを必須化しない。AMD RX6400 / Intel UHD630の両方で実機検証し、未検証vendor/OSは明記する。
- Celeritas固定参照989ebf2e（Embeddium、Sodium0.5.11以前等のFOSS系譜）を比較基準にする。制限付きの現行Sodiumソースをコピー・機械的翻訳・改名・cherry-pickしない。独立実装は一般アルゴリズムと出典・ライセンス根拠を残し、既実装か否かと効果を調べる。許諾問題が書き直しで消えるとは扱わない。
- 影mod / shader pack互換はCeltium本体のshaderless完成後。バニラのShaderGroup・spectator効果・entity outline等は本体機能であり、この延期を理由に省略しない。
- 一括削除・全面書き直しはしない。現在の変更と有効な修正を保持して最小の具体修正を積む。リブランド・配布・性能・既存mod互換の強化も完成工程に残す。

【現状の扱い】
terrainと複数のimmediate pipeline、GL/VK bridge、実験的swapchain、画像からのtexture供給はある。通常fixtureのAMD/Intel成功は実績だが、全vanillaや全mod、正しい画質、GLなしのframeを証明しない。format 0=0は認識範囲の事実であり、format 2が既定maskでGLに残ることを隠さない。off-target offer時のoffTargetDrawn++は描画証拠ではない。withoutGlIdentityはidentity照会省略の試験であり、GL contextなしの起動・描画ではない。古い68.81%やlocal 0.267/255を最新版全体の一致率として使わない。

【直近の優先順位】
1. 正当性を壊す監査P0を先に直す。A01: model-viewの最初の3列のみを送り平行移動を落とす誤りを修正し、全immediate shaderとjar内SPIR-Vを揃える（アフィンで定数なのは最終行）。A02: TextureUtilのint[][]はmip level別の平坦pixel配列であり、data[row][col]ではない。A03–A07: patch時にUNDEFINEDで既存atlasを捨てず、初回全面初期化・patch外保存・sprite単位mip・CPU shadow/dirty generation・hash・size・descriptor更新を正しく管理する。
2. 受理からGPU実行までの欠落・二重描画を直す（A08–A10、S03/S05/S06/S10/S14等）。preflight後にappend/acceptするかrollbackする。全formatのdrop/例外を数える。FBOはtargetごとに順序・clear・depth・sample依存を保持し、GLへfalseで返したofferをVK描画済みとしない。frame compositeはvertex formatだけで判定しない。
3. pinned Celeritas GLと修正後Celtiumを同一fixture/GPU/camera/sceneで再比較し、地形の暗さ、局所変換、照明、alpha/depth/blend、current RGBA、sampler/mipを直す。古い数値は過去の測定として残し、現在の結論は新artifactで示す。CPUの契約確認と必要な小GPU fixtureを使い、毎回巨大なmod testを回さない。
4. Minecraftのtexture/frame所有をnativeへ移す。atlas stitch・動的texture/lightmap・sprite mip/animation・texture lifecycle・reloadの元データを供給し、GL readbackを不要にする。camera/stateはMinecraft側から供給する。windowをGL contextなしで生成し、Vulkan-owned color/depth/FBOから最終合成→swapchain→presentまでを普通のゲーム経路として成立させる。現在のGL/VK bridge・copyback・GL swap・GL fallbackは移行用であり完成形として固定しない。
5. vanilla coverageを明示して埋める。全blockと必要なstate、item/model、全vanilla entity・TESR、hand、GUI/font/HUD、粒子、sky/cloud/weather、水/溶岩/火、半透明と並び替え、破壊/selection overlay、dimension、各種FBO/postprocess、spectatorのcreeper/spider/enderman視点等。registryから自動fixtureを生成し、小場面に分割する。平坦worldとlayout受付数だけで全制覇にしない。全場面でnative担当と画質を確認する。
6. reload/resize/world switch、minimize/out-of-date、texture/descriptor/allocation/submit/wait失敗、resource retirementを両GPUで確認する。timeout後に使用中command bufferをreset/re-record/destroyしない。start-up/texture/presentをsteady-state stall成功だけで閉じない。
7. vanilla本体が成立後、major mod互換を進める。JEIの先行収穫は維持するがIntel JEIのbudget調整を主作業にしない。Minecraft/Forge描画API経由は共通入口へ接続し、直接LWJGL/GLSL/native GLを使うmodは具体的な描画箇所とAPIを調査・個別対応する。modのロード/バイナリ互換と実native描画・見た目を別々に確認し、対応/未対応理由を公開する。
8. 正当性と互換を保つ性能改善・配布を仕上げる。元Celeritas同条件で定常CPU/GPU時間、平均FPS、p95/p99、メモリ、chunk update等を測る。最新SodiumやFOSS上流との差分はarena/staging/batching/culling/mesh/translucency/item/state/sync等について既実装・適用条件・ライセンス・採否を記録し、根拠ある独立改善だけ採用する。Java/APIの新しさや専用化だけでFPS向上を断言しない。

【厳密な完成条件】
(a) 普通のゲームの全vanilla描画と最終合成・presentがnative Vulkanで成立する。
(b) GL contextを生成せず、通常frameのGL call、GL readback、GL/VK bridge、copyback、GL swap、GL fallbackを必要としない実行を示す。identity照会省略だけやGL描画でのfixture完走では達成扱いしない。
(c) native swapchainへ出した完成frameが実際のwindow/compositor出力に到達する証拠を得る。markerがGL framebufferにないだけでは完了ではない。利用可能なcapture方法を調べ、取得不能なら具体的な不足条件を残す。未測定の機能を環境やmethod名だけで目標から外さない。
(d) AMD/Intelで必要な場面と失敗復旧を確認し、監査P0/P1を明示した受入で閉じる。原版GLと画像・状態を比較し、実装済/局所検証済/未検証/未実装を区別する。
(e) 既存mod対応範囲、Java条件、最低VK能力、未検証GPU/OS、ライセンス/リブランド/性能実測をdocsと配布物に一致させる。

【作業・証拠の規律】
docs/plan.mdは現在の到達点・優先順・受入の唯一の現状表にし、経緯はhistoryへ移す。scopeをソースと実測に合わせ、旧24監査は番号ごとに再判定する。各roundは具体実装か意思決定に必要な限定検証を進め、同じ仮説を定数変更・budget延長・文書の言い換えで反復しない。失敗の原因は実ログから読む。原版baselineとcandidate、fixture、SPIR-V、head/dirty、effective flags、GPU/driver、camera/scene、exit/validation verdict、画像・差分をhash付きで保存する。pixelとchannel、resourcesとcalls、offersとacceptedとrecorded/submitted/completed/droppedを混同しない。欠落verdictやvalidationCleanだけを成功扱いしない。古い受入・失敗証拠を削除せず、重複ライブラリ・不要展開・world dataから容量を整理する。最低空きを確認する。goal active / activation armed / session runningを区別し、復旧の成否も明示する。公開報告は自然な日本語で、何を実装し何が通り何が残るかを短く具体的に述べる。進捗率や「あと1点」といった根拠のない完了予測をしない。

絶対に止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。thinkingで✓や✗の文字を使わない。
