# Celtium goal — 2026-10-10 現状更新・構造修復反映

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

【現在の到達点と未完】
- terrainと複数のimmediate pipelineはnative描画を持つ。immediateの既定受付はformat 1〜9で、format 2が既定でGLに残るという古い説明は撤去する。format認識・受付は全vanilla coverageを意味しない。
- backend専用command bufferの状態遷移、pass内での記録、preflight、target別group保持/retire、off-targetの色・clear/depth順序が修正され、小GPU fixtureでAMD/Intelの局所受入がある。A01/A02/A03/A08/A09/A32等の有効な修正とSolの受入を保持し、済んだ欠陥を最初から直し直さない。残るbranch・GPU画像・失敗復旧は個別に判定する。
- 地形の暗さの主要因とlightmap座標の修正はあるが、照明・state・alpha/depth/blend・sampler/mip・遠景等の画質残件はある。過去の68.81%やlocal 0.267/255を最新版全体の一致率にしない。
- CPU image/patch供給とregistry更新はあるが、直接保持atlas/lightmapとregistryのVk image所有が分裂し、patchの後に全面GL読み戻しへ戻る経路が残る。通常fixtureの0/frameを全sceneのreadback除去としない。animation・reload・dimensionを含むtexture所有の統合が直近の根因修正対象である。
- モデル専用native描画は実験フラグ側に残り、entity/TESR/item/model全体のnative描画・照明・見た目は未完。手持ちの取消だけをモデル完成と扱わない。vanilla ShaderGroup等にもGL fallbackがあり、mod shader pack延期とは分ける。
- native合成とswapchain提示画像の内容を調べる実験はあるが、GL context・GL/VK bridge・copyback・GL swap依存を除いた通常ゲームframeは未成立。提示画像のmarker一致と実window/compositor出力の到達は別の受入であり、後者は未確認。withoutGlIdentityはGLなし起動ではない。
- captureのtick等号によるMISSING_REPORTはbf27f1975で段階化され、Intelの通常/溶岩fixtureが完走した。duration上限/worldFramesから「1frame3.5秒」とした説明は撤回する。固定world timeだけでsprite animation位相も固定されたとは扱わない。
- dimension受入は8ee7cf97e以降、server taskとclientの実到達・capture時dimensionを見る方式へ修正された。p247-end / p247-end-intelはcapture時player/worldが1でPASSED。Netherはp247-nether / p248-trace / p249-earlyで一時的に-1へ到達後、GuiDownloadTerrainを経て0へ戻りNO_CAPTURE。旧Nether PASSEDは有効な受入から外す。地形ダウンロード画面の残留と帰還は観測事実で、renderer・packet/portal・fixtureのどこが根因かは未確定である。
- AMDのblock registry254種掃引とIntelの部分掃引、weather/particle/GUI等の局所fixtureは実績として保持する。全block state・全entity/TESR・全vanilla・全modの正しさは未確認。現在HEADや未commit差分の進行に合わせてこのsnapshotを更新する。

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

【作業・証拠の規律】
docs/plan.mdは現在状態・優先順・受入を一箇所で管理し、古い冒頭表を最新実測で書き換える。round履歴はdocs/history/へ移し、末尾への訂正追記だけで整合したことにしない。docs/goal.mdとscope・audit参照を現状に合わせる。旧24/第3回32監査は修正済/局所受入/未確認/未完を分け、古い番号順に済んだ修正を反復しない。
一度に一つの所有/状態契約を実装し、変更単位でreview可能なcommitにする。重複・到達不能・不要になった暫定経路は根拠を確認して整理する。恒久的正当性をlab flagに依存させない。新しい抽象と旧経路の併存を残さない。汎用GL変換器や全面書換えへ拡大しない。
各roundは具体実装か意思決定に必要な限定検証を進める。同じ仮説の定数変更・budget延長・文書の言換えを反復しない。不変条件が壊れれば失敗する小検証を選び、CPU契約とGPU画像/完了を区別する。必要なAMD/Intel受入後は新しいnative化へ進む。
baseline/candidate・HEAD/dirty・fixture・jar/SPIR-V・effective flags・GPU/driver・camera/scene/animation/capture tick・exit/verdict・画像差をhash付きで保存する。duration上限と実経過時間、全frameと測定区間、resources/calls、offers/accepted/recorded/submitted/completed/droppedを混同しない。欠落verdict・validationClean・copyback回数だけで成功にしない。過去の受入/失敗証拠を残し、重複展開・不要world等から容量を整理する。
goal IDと完成仕様を維持し、active/armed/runningを別々に確認する。停止や未到達を完了扱いしない。公開報告は自然な日本語で、直した契約・消した重複/例外・必要な検証・次のnative化を短く述べる。根拠のない完了率・完成時期・「あと一点」は使わない。

絶対に止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。thinkingで✓や✗の文字を使わない。
