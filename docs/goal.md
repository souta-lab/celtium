# Celtium goal — 2026-10-10 現状更新・構造修復・開発単位の整理

CeltiumをMinecraft 1.12.2 / CleanroomMC専用のネイティブVulkanレンダラとして完成させる。2026-10-10の実装・受入に合わせてgoalを更新する。docs/plan.mdを現在の到達点・優先順・受入の現状表とし、docs/goal.mdはこのobjectiveと同期する。docs/audit-2026-10-09-third.mdは監査起点、docs/history/は過去の証拠・経緯であり、現在の完了判定ではない。構造修復の具体方針はdocs/handoff-structure-recovery-2026-10-10.md、直近の原因候補はdocs/hints-2026-10-10-nether.mdを参照し、最新HEAD・dirty差分・artifactを優先する。

【維持する仕様】
- Cleanroom専用。通常Forge（旧Java/LWJGL2）や他Forge+lwjgl3ify/RFB構成の起動互換は提供対象外。ただしCleanroomで動く既存Forge modのMinecraft/Forge公開クラス・メソッド署名・イベント順序・modelデータの互換入口は可能な限り維持する。
- Celtiumの対象モジュールは --release 21 / class major 65以下。Java22以降のAPI・言語・previewへ依存しない。ゲーム・build・検証のJDK25は許可し、ゲーム全体のJava21起動を完成条件にしない。
- 検証済みCleanroom 0.6.13-alpha / LWJGL3.4.1 / Java25を基準に、実在するCleanroom公開API・正式Mixin/Accessor・直接LWJGL3を優先する。内部APIは依存理由と版を固定する。無根拠の反射、古いLWJGL用独自互換を惰性で増やさない。
- 汎用OpenGL→Vulkan変換器、GL API全体のエミュレーション、Zinkの組込みをこのgoalの目的にしない。Minecraftのcamera・terrain・model/item・entity/TESR・particle・UI・texture・FBO・postprocess・frameの意味を把握したnative passへ移す。共通VK allocator/buffer/descriptor/pipelineの再利用は許可する。
- GPU名ではなく必要なAPI/feature/format/surface能力で選ぶ。広いVulkan対応GPUで動くbaselineを設計し、不要なinterop/GL要件を純nativeから外す。mesh shaderを必須化しない。AMD RX6400 / Intel UHD630の両方で実機検証し、未検証vendor/OSは明記する。
- Celeritas固定参照989ebf2e（Embeddium、Sodium0.5.11以前等のFOSS系譜）を比較基準にする。制限付きの現行Sodiumソースをコピー・機械的翻訳・改名・cherry-pickしない。独立実装は一般アルゴリズムと出典・ライセンス根拠を残し、既実装か否かと効果を調べる。許諾問題が書き直しで消えるとは扱わない。
- 影mod / shader pack互換はCeltium本体のshaderless完成後。バニラのShaderGroup・spectator効果・entity outline等は本体機能であり、この延期を理由に省略しない。
- 一括削除・全面書き直しはしない。現在の変更と有効な修正を保持し、一つの根因の所有・状態契約を揃える小さな修正と必要な構造整理を進める。リブランド・配布・性能・既存mod互換の強化も完成工程に残す。

【現在の到達点と未完】（2026-10-11 時点。証拠と受入の現状表は docs/plan.md、履歴は docs/history/）

到達点:
- terrain・複数の immediate pipeline・線は native 描画を持ち、**GL context なしの window が実 scene を描いて提示する**
  （fixture PASSED・validationClean・0 VUID・画像主張 PASSED。§0.115〜§0.119）。
- 提示は per-frame で成立し、GL swap は 1対1 で停止。resize は両 GPU で受入済み（§0.102 §0.103）。
- **lightmap の黒は解消**。根因は announcement の配列形状（producer が 1行1配列、consumer は level 0 全体を要求）で、
  producer を契約に合わせた（§0.116）。**model/TESR は既定 ON** で、float translate のミラー欠落を修正し、
  エンチャント台の本が実ブロック位置に描画されることを GL 参照と画素位置で確認（§0.118）。
- **Nether は capture 時に dimension -1 で native scene を描き、validation も clean**
  （`.celtium-work/runtime/vuid-after2`: client/world/server すべて -1・`dimensionFramesInTarget=129`・msgs 0・§0.117 §0.119）。
  根因は fixture 側（到着位置がポータル内＋`timeUntilPortal=0`）だった。
- **entity outline の chain は実シルエットを読み**（`postTargets=1 postAdopted=1 postRunsOnStandIn=0`）、
  その出力を registry に publish する配線まで入った（§0.118）。
- GPU 検証は AMD RX 6400 と Intel UHD 630 の両方で bridge/GL-less とも PASSED。
  描画側が選択デバイスを `CELTIUM_NATIVE_DEVICE` で名乗り、harness はそれで `--require-gpu` を判定する（§0.118）。
- 監査バッチ（cancellation 契約・adoption 絞り込み・overlay 経路・GPU 報告）は修正・受入済み（§0.118）。

未完:
- **可視 glow**（publish は入ったが画面上の glow は未受入）、**GL-less の一部 frame に出る半透明 wedge**、
  **depth format の契約**（D32_SFLOAT 固定）、**Nether の画像ゲート較正**（scene 非依存の床で誤 FAIL）、
  **video settings シーン**の受入。
- 全 entity/TESR/item/model の画質（照明・alpha/depth/blend・sampler/mip・遠景）、FBO/postprocess の native 化、
  全 block state、**全 mod 互換**（ロード互換と native 描画を分けて報告）、**性能実測**（Celeritas 同条件・p95/p99・
  memory・chunk update）、リブランド/配布。
- **条件 (c)（実 window/compositor 出力）は未確認**。捕獲手段の棚卸しと不足条件の記録を進めている。
  環境制約を理由に目標から外さない。
- 未検証 vendor/OS: この環境の AMD/Intel の Linux 以外はすべて未検証。

使ってはいけない古い数値・古い主張:
- 旧「Nether 両GPU受入」（§0.105 相当）は**撤回**。現在の Nether 受入は上記の run が根拠。
- 過去の 68.81% や local 0.267/255 を最新版全体の一致率として使わない。固定 world time だけで
  sprite animation 位相まで固定されたとは扱わない。
- ⚠ GL-less の「拒否」は GL fallback ではない（GL 本体が stub の no-op なので、拒否＝どこも描いていない）。
  条件 (b) の GL 残量は bridge 構成の拒否から読む（§0.118）。

【直近の実行順】
1. 現在進行中のdimension修正・readback計測と必要な受入を安全な境界でまとめる。未commit変更・成功した修正を保全し、短い構造修復フェーズへ入る。局所flag/定数/budget追加で症状だけ隠さず、一つの根因に対してproducer/consumer/invalidate/deleteを同じ契約へ揃える。抽象化や監査文書だけのroundを続けない。
2. atlas/lightmap/dynamic textureの実体・更新・寿命を統合する。CPU原本と初回全面stitch供給、resource generation、sprite別level更新、実際にsampleするVk image、hash/size/descriptor、reload/delete/ID再使用を整合させる。CPU patch成功直後の全面readbackを除く。初回未初期化imageへpatchだけ入れず、patch外保存とmipを守る。更新をまとめる際はqueued drawの旧内容参照境界を保持し、upload/submit/waitの実コストを測る。重複所有者を増やすラッパーで終えない。
3. 触る範囲のdraw/fixture契約を整理する。target/frame/generationを持つqueued→recorded→submitted→completed→retiredの区別、preflight・retry・失敗時保持、clear/depth/sample順序を守る。取消は同じ内容を別経路が引き受けた根拠を必要とし、名前・counterだけで描画を消さない。Object[]添字契約は必要な範囲で意味のある型へ寄せる。fixtureは準備→到達待ち→描画待ち→capture→reportとし、server/client/shutdownの結果・JSON所有を安全なsnapshotで整合させる。
4. Netherの未完を具体的に直す。短いtraceと必要なclient/server stackでRespawn、chunk/player位置同期、画面完了、portal/cooldown、fixture操作のどこで帰還・停止が起きるか特定する。早い転送・budget延長だけを反復しない。capture時player/world/providerがtargetで、到達後に実native sceneを描いたことを受入条件にする。Endの局所成功をNetherに流用しない。
5. 構造修復後すぐ全vanilla native化を進める。残るmodel/item・entity/TESR・hand、state/lighting/current RGBA/sampler、全block必要state、GUI/font/HUD、particle・sky/cloud/weather・水/溶岩/火・半透明並び替え・破壊/selection、dimension、FBO/postprocess、spectator creeper/spider/enderman等を現状表で分割し、未完のnative担当と画質を埋める。存在するpassを一括で未実装扱いせず、flat worldとformat受付だけで全制覇にもせず、registry由来の小場面を使う。pinned Celeritas GLと同一GPU/camera/scene/animation位相で必要な画像・state比較をする。
6. texture/camera/state/frameの所有をMinecraft側の元データとnative resourceへ移し、GL contextなしwindowでVulkan-owned color/depth/FBO→最終合成→swapchain→presentを通常ゲーム経路として成立させる。bridgeは移行用。実window/compositor出力はmarkerと連番等で確認し、取得不能なら不足条件を具体的に残して実現手段を調べる。method名や環境制約だけで完成目標から外さない。
7. 各移行に必要なreload/resize/world switch、minimize/out-of-date、texture/descriptor/allocation/submit/wait失敗・resource retirementをAMD/Intelで限定確認する。既存受入はscope付きで再利用し、新差分・失敗・未解決懸念なしに反復しない。timeout後の使用中command bufferはreset/re-record/destroyしない。steady-stateだけでstart-up/texture/presentを閉じない。
8. vanilla本体成立後、既存mod互換・正当性を守る性能改善・配布を仕上げる。JEI先行成果は維持し、budget調整を主作業にしない。Minecraft/Forge公開描画API経由をnativeへ接続し、直接LWJGL/GLSL/native GL使用modは具体的箇所を調べ個別対応する。ロード互換と実native描画を分けて公開する。元Celeritas同条件で定常CPU/GPU時間・FPS・p95/p99・memory・chunk updateを測り、FOSS上流との差分は既実装/適用条件/ライセンス/効果を調べ独立改善を採用する。Java/APIの新しさだけで高速化を断言しない。

【厳密な完成条件】
(a) 普通のゲームの全vanilla描画と最終合成・presentがnative Vulkanで成立する。
(b) GL contextを生成せず、通常frameのGL call、GL readback、GL/VK bridge、copyback、GL swap、GL fallbackを必要としない実行を示す。identity照会省略だけやGL描画でのfixture完走では達成扱いしない。
(c) native swapchainへ出した完成frameが実際のwindow/compositor出力に到達する証拠を得る。markerがGL framebufferにないだけでは完了ではない。利用可能なcapture方法を調べ、取得不能なら具体的な不足条件を残す。未測定の機能を環境やmethod名だけで目標から外さない。
(d) AMD/Intelで必要な場面と失敗復旧を確認し、監査P0/P1を明示した受入で閉じる。原版GLと画像・状態を比較し、実装済/局所検証済/未検証/未実装を区別する。
(e) 既存mod対応範囲、Java条件、最低VK能力、未検証GPU/OS、ライセンス/リブランド/性能実測をdocsと配布物に一致させる。

【実装・検証の進め方】
開発単位は同じ根因・所有/状態契約を完成させる関連変更のまとまりとする。「修正一箇所→ゲーム起動→確認→報告」を機械的に繰り返さない。
- 関連コードを一度読んで根因・producer/consumer・変更範囲・不変条件・成功条件を決め、関連する複数箇所をまとめて実装する。途中の不整合な状態を毎回GPU受入に掛けない。独立した大機能を無制限に同じ変更へ混ぜず、失敗時に原因を追える単位を保つ。
- 実装途中はコンパイル・型/境界確認と必要な短いCPU契約検査を使う。ゲーム起動・画像比較・長いworld試験は意味のある実装段階が揃ってから行う。各編集のたびに同じGPU fixtureを回さない。安い検査も実装を写しただけの試験にせず、不変条件を壊せば失敗するものを選ぶ。
- 小さいGPU場面でAMDの主要な問題を潰し、まとまった段階でIntelの受入も行う。同期・memory・feature/format能力・GPU固有経路に関わる変更は早めに両GPUへ掛ける。AMD成功をIntel成功と同一視せず、未受入の変更を完成にしない。
- atlasでは全面stitch供給、実sample先のimage所有統合、sprite patch/mip、invalidate、reload/delete/ID generationまで関連する経路を揃えてからまとめて受入する。必要な契約が揃った時点で小場面を検証し、遠い無関係機能の完成まで検証を延期しない。
- 一度通った変更はまとめてcommit・短く報告し、次の機能へ進む。再試験は新しい変更・失敗・未解決の具体的懸念がある場合に絞る。フルworld/全mod再試験や両GPU全場面は統合節目と必要な退行確認へ集約し、変更と無関係な場面を毎round反復しない。
- 並列化する場合は担当ファイル・interface・資源所有を先に分ける。同じ巨大backendの同時編集や統合コストが利益を上回るなら使わない。

【作業・証拠の規律】
docs/plan.mdは現在状態・優先順・受入を一箇所で管理し、古い冒頭表を最新実測で書き換える。round履歴はdocs/history/へ移し、末尾への訂正追記だけで整合したことにしない。docs/goal.mdとscope・audit参照を現状に合わせる。旧24/第3回32監査は修正済/局所受入/未確認/未完を分け、古い番号順に済んだ修正を反復しない。
関連変更を整合した単位で実装し、変更単位でreview可能なcommitにする。重複・到達不能・不要になった暫定経路は根拠を確認して整理する。恒久的正当性をlab flagに依存させない。新しい抽象と旧経路の併存を残さない。汎用GL変換器や全面書換えへ拡大しない。
各roundは具体実装か意思決定に必要な限定検証を進める。同じ仮説の定数変更・budget延長・文書の言換えを反復しない。CPU契約とGPU画像/完了を区別する。必要な受入後は新しいnative化へ進む。
baseline/candidate・HEAD/dirty・fixture・jar/SPIR-V・effective flags・GPU/driver・camera/scene/animation/capture tick・exit/verdict・画像差をhash付きで保存する。duration上限と実経過時間、全frameと測定区間、resources/calls、offers/accepted/recorded/submitted/completed/droppedを混同しない。欠落verdict・validationClean・copyback回数だけで成功にしない。過去の受入/失敗証拠を残し、重複展開・不要world等から容量を整理する。
goal IDと完成仕様を維持し、active/armed/runningを別々に確認する。停止や未到達を完了扱いしない。公開報告は自然な日本語で、直した契約・消した重複/例外・必要な検証・次のnative化を短く述べる。根拠のない完了率・完成時期・「あと一点」は使わない。

絶対に止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。thinkingで✓や✗の文字を使わない。
