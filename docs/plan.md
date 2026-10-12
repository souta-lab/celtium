# Celtium 現状表（現在状態・優先順・受入）

> **影対応の後続計画:** [shader-plan.md](shader-plan.md)。**フルVK化＋主要modのshaderless対応が完了してから着手する。** この計画を理由に現在の本体実装を中断・前倒ししない。

> **描画backendの決定（2026-10-10）:** Vulkan直結を維持する。SDL GPUへの全面移行は行わない。Angelicaはshader変換・model/TESR/文字のbatch化・状態と資源寿命の設計/試験を部品ごとに比較する。現在のGL除去と主要mod対応を優先し、影の実装は上記gateを満たしてから。汎用GLエミュレーションを新たな主backendにしない。

> goal: `goal-780c63c7-acf3-4426-80dc-6c99d30d8627`（live API で revision 27 / phase active / activation armed / roundsStarted 103 / maxGoalRounds 1000 を確認。**旧 `goal-0d07c3b5-…` revision 27 の記載は監査 17.2 で不一致と指摘され、この行を live 値へ同期した**）。
> 全文は `docs/goal.md`。構造修復の方針は **`docs/handoff-structure-recovery-2026-10-10.md`**、
> 直近の原因候補は `docs/hints-2026-10-10-nether.md`。監査起点は `docs/audit-2026-10-09-third.md`。
> **round ごとの経緯と旧測定は `docs/history/plan-round-log-until-2026-10-10.md` に移した**（削除していない）。
> この文書は**現在状態を一箇所で管理する表**であり、末尾への訂正追記で整合させない。

> **エビデンスの保存方針（2026-10-10 整理済み）**: 全 run の `validation-process.json` と `logs/celtium-validation.json`、
> および **docs が引用している run** の `screenshots/` と `logs/latest.log` を残し、run 内の `mods/`・`test-libraries/`・
> `saves/`・`assets/`・`libraries/`・`validation-console.log`・`logs/debug.log` は削除する（harness が毎回作り直す）。
> `runtime/celtium-headless-interop/`（共有ライブラリ）・`jdks/`・`gpu-tools/` は消さない。詳細は
> `docs/handoff-2026-10-10-next-model.md` §11。**実績: runtime 16 G → 145 M、空き 2.1 G → 18 G。**

## 0. 直近の到達点（最新実測・冒頭表）

> **冒頭表（現在の受入状況・最終更新 2026-10-12・round 159）** — 詳細と数値は下の §0.2xx〜§0.36x に、旧測定は
> `docs/history/` にある。**過去の数値を現在の事実として転載しない。**

| 対象 | 状態 | 実測の要点 |
|---|---|---|
| entity / item の不可視 | **受入済み** | `drawnByFormat` id9 228/228・`windowFlushFailures=0`・HUD mean\|d\| 1.87 |
| 明るい entity・雲 | **受入済み** | 上流参照との比較で一致 |
| 文字間のアーティファクト | **受入済み** | coalesced font run の退化頂点セパレータ。26/36 → 0 |
| block entity の z-fighting | **受入済み** | polygon offset を draw 時の live 読みから group 捕捉へ |
| ホットバー item の暗転 | **受入済み（上流参照一致）** | 参照 `(106.5,74.7,25.6)`・修正後 `(111.0,76.4,26.2)` |
| 選択枠の欠落・clip 矩形 | **受入済み** | `lineBatchesNarrowed` 215・`colorMaskHistogram` の pixel 証明 |
| 動画設定が開かない | **受入済み（画面が開く）** | 真因 = 0 範囲 slider の assert |
| 較正済み picture gate | **PASSED** | 素の terrain・assertion failure 0・`validationMessageCount=0` |
| **font レンダラ刷新（OPT-18 前半）** | **着地・受入済み（`27123e756`）** | **`glyphMillis` 14,280 → 0（vanilla 本体が走らない）・1 glyph 14.4 → 0.85 µs・cold p50 550.6 → 144.7 ms**（同一 jar・両 leg `pinSections`・両方 clean）。文字不変をテクセル検査で確認（449/449・104/104・空 0/896） |
| **GL-less が既定** | **着地・受入済み（`dd67ed93a`）** | `-Dceltium.vulkan.glless=false` が opt-out。**flag なしの leg で `CELTIUM_GLLESS stub GL provider installed`・`window hinted GLFW_NO_API`・`worldTicksAtEnd=241`** |
| **GPU 待ちの排除（PR#1）** | **着地（`c2595aa9f`）** | `frameIntervalP50/P95/P99` **18.6/28.0/333 → 15.7/22.5/326 ms**・errors 0・**VUID 0**・`celtium-validation.png` が baseline と**完全一致** |
| **GUI batch の matrix 跨ぎ統合（PR#2）** | **着地（`d1f0042b4`）** | 軸並行 modelView をコピー中に適用（**頂点shaderと bit 一致**）。回転は matrix-keyed 経路に残す |
| **0.0.2-alpha** | **公開済み** | `celtium-forge-mc1.12.2-0.0.2-alpha.jar`・2,594,729 bytes・SHA-256 `c2b69d03…b94471fc`・package verifier `verified: true`。**開発版は `0.0.2-alpha-dev`（Celeritas 上流番号を除去）** |
| **BH-03/04/23/34** | **解決済み** | 各1コミット・一旗対照・両脚 clean（`9982ac634` が列挙） |
| **BH-09（scissor）** | **修正着地・受入未取得** | `218ea2120`。**原典で「vanilla は `glScissor`/`GL_SCISSOR_TEST` を一切呼ばない」を確定**（出現0）・唯一の発行元は `VulkanDrawContext.enableScissor`・呼び手は `ScrollableFrame.render` のみ・**`canScrollVertical` が立つ時だけ**。`scissorCalls`/`scissorToggles` が判定子。**leg 側の注意**: `r154-bh09` は `scissorCalls` 計器より前の jar なので**この脚にカウンタが無いこと＝0 ではない**。カウンタを持つ脚は `r157/r158/r160-bh09`・`guiscale2-leg`・`ownerfix-menu`・`splashdrop*`・`bh18b-amd/intel`・`r162-release`・`loading-leg` で、いずれも `scissorCalls=0`（ただし**リストが overflow する条件を踏んだ脚はまだ無い**） |
| BH 全体 | **解決済み 28 / 未解決 8** | **BH-18 解決（`0a8b8d253`・合成入力 harness 全条件 PASS／recreate のみ未測定）**・**BH-35 解決（`4fb0b581b`・広告を列挙で決定し両GPU同一）**。残りは BH-14, 17, 20, 21, 24, 26, **33（UI側は「有効に見せない」で着地・fade 自体は未移植）**（`docs/history/2026-10-11-code-bughunt.md` 冒頭に状態） |
| **パノラマ** | **受入済み（ユーザ可視・メニュー画像）** | メニュー presented 画像が**ブラー付きパノラマ背景＋ロゴ＋ボタン＋版表示**で描画（sha256 `090d98f7dd60be62…`・21,962色・白1.2%）。計器: `CELTIUM_TEX_COPY_SERVED dest=21 256x256 mean=106,119,135 opaque=65536/65536 nonWhite=65536`（旧: `255,255,255` / `0/65536` / `0`）。2修正 = `bbe0bbe71`（read解決の default-pair 対称分岐）＋`aea5fec0c`（**コピーの Y 反転**）。旧本文: 白の出所は **id 8 の mirror**（splash の白 256×256 `DynamicTexture`・`CELTIUM_UPLOAD_CALLER id=8 … first=0xffffffff`）。`59441d6bb` が splash 由来 upload を id で記録し、**splash upload が無かった最初のフレームで `forgetTexture`** する。**現行ツリーの leg（`bh18b-amd`/`bh18b-intel`）で `splashCanvasInvalidations=1` を実測＝破棄は実際に走る**。**`splashSourcedGroups` は旧定義の脚で 239** だったが、その脚（`splashdrop*`）は HEAD `5a8ee96af` で**計器より前**。menu-hold の判定 leg は**まだ無い**。`mirrorSourceById` を `forgetTexture` で消す契約修正は dirty（texture-mirror） |
| **ロード画面** | **未測定（判定子が壊れていた・修正を着手中）** | **GL-less では `SplashProgress.start()` が cancel** され `:294/:353/:416` は実行されない。走るのは Tessellator 経路。**順序を原典で確定**: `Minecraft.startGame` 内で `beginMinecraftLoading`(preInit)=`:514` → `SplashProgress.drawVanillaScreen`=`:517` → `finishMinecraftLoading`(init)=`:588`＝**init での latch は遅すぎた**（fixture をコンストラクタ latch に変更）。隔離は1脚で確定: `CELTIUM_SPLASH_HOOK drawSplashScreen=true`（mixin 適用）＋ `fixture instanceSet=true`（到達）。**受入の絵**: `loading` stage の dump は client tick 0 で取得できたが **1280x720・全白 (255,255,255)・distinct=1**（＝ロード画面の幾何が presented frame に届いていない）。次は `CELTIUM_DRAW_SITE … passImage=` と `offTargetPerTarget` で描画先を追う。**注意**: 直前の1脚は**旧名 jar**（`2.5.12-celtium.0.1.0-dev.1`、`celtium$dumpLoadingFrame` 無し）で走っており**無効**だった（下の「成果物バージョンの罠」） |
| **GUI 文字の暗転** | **現行ツリーでは再現せず（`172b5ab93`）** | ユーザ crop は1行目がアトラスモデルと**画素完全一致**、2行目も `colorCode[7]`/`[23]` で厳密。**影パス漏れ説は反証**。実差は quad 単位の 24/196 セル欠落だが、§7 tooltip 0/2718・fast path on/off 両方 0 |
| **性能（ユーザの実プロファイル）** | **優先順の根拠** | 358 frame/20 s・frame mean 55.9・p50 34.1・p95 331.8・p99 358.9・worst 384.6 ms。**≥50 ms の 64 frame（17.9 %）が wall time の 50 %**。スパイクの `gameRenderer` 318.6 ms のうち**約 246 ms/frame が未帰属**。`gui` は**定数 ~13.7 ms/frame**（スパイク源ではない） |
| **bridge 経路（`glless=false`）** | **本機で deadlock** | 最初の world frame で確定的（baseline 3 leg）。**GL-less 既定なので公開物は晒されない**が、比較 arm としては使えない |
| **成果物バージョンの罠（stale jar）** | **根因を除去（このround）** | rebrand で開発版が `0.0.2-alpha-dev` になった後も **`build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/` と `forge122/build/libs/celtium-forge-…-2.5.12-….jar` が残っており**、**旧パスを `cp` する補助スクリプト／旧パスを書いた手順書が、古いjarでlegを走らせる**。実害: ロード画面の1脚が 13:08 の jar（`celtium-loading.jar` の sha256 が旧 `2.5.12-…jar` と**完全一致**）で走り、`celtium$dumpLoadingFrame` が入っていないのに「hook が発火しない」と読まれた。**対策**: `scripts/benchmark_celtium.py` が build 出力から**名前族とmtimeで解決し候補を全部印字**（旧名・中間物は `ignored` として名指し）、旧 build 出力を削除、`docs/runtime-validation.md`/2つの handoff のレシピを現行パスへ訂正（§0.365） |
| 未了 | — | **パノラマ・ロード画面・BH 9件・BH-09 受入・Intel 受入・mod cohort（HEI は world 到達のみ）・shader 対応（E）** |

**この節が現在の受入状況である。** 個別の受入根拠は下の §0.1〜§0.92 にある（新しい節ほど新しい実測）。

| 項目 | 状態 | 最新の実測（GPU・条件つき） |
| **ログ flood（ユーザー報告 18:2x）** | **修正済（Lead・jar `2a3c59fe21ae831b6a3d` を instance へ投入）** | ユーザー構成は `-Dceltium.vulkan.glless=true` **だけ**なのに `CELTIUM_MODEL_MATRIX` が **model list ごと・フレームごと**に出て、**ログが 100,000 行を超え Prism が表示停止**（screenshot が証拠）。`VulkanDiagnostics` で `MODEL_MATRIX`（上限200）・`ACCEPTED`（500 batch ごと）・arena census（既定 true だった）を gate。一回だけの構成識別行は残す。§0.187 |
| **F3 / driver 表示** | **完了（task-46・両 vendor 実測）** | `glRendererPreflight` が実 device の `VkPhysicalDeviceDriverProperties` を end-to-end で返す: AMD `driverName="radv" driverID=3(MESA_RADV) driverInfo="Mesa 26.2.4-1" driverVersion=109060100(26.2.4)` / Intel `Intel open-source Mesa driver`・両方 `apiVersion=1.4.354`。`CELTIUM_DRIVER_INFO` が「今描いているのは何か」の権威行。§0.194/§0.202 |
| **ロード画面** | **未表示（ユーザー第11報で再報告）／task-61** | `CELTIUM_WINDOW_RENDERER … stage=createDisplay`・`no terrain backend is installed` 0件・最初の immediate group が splash 自身（`mvT z=-2000`・logo texture 8・splash 自身の FBO）。`celtium$publishFrameTexture` が game framebuffer の colour texture 名を chain pair の image に publish し、**全 published name に layout 遷移**（sampling 側 `ensureSampledLayout`・write 側 `ensureGeneralLayout`・unpublish で外す）＝`VUID-vkCmdDraw-None-09600` の修正。**round 11 のユーザー報告は「未だに表示されてない」**＝「renderer に届いた」は「ユーザーが見た」ではない。受入は presented frame に loading 内容が出ること＋`validationMessageCount=0`。§0.201/§0.202/§0.206 |
| **性能（実測・ユーザー構成）** | **cold は依然として悪い／steady は約24fps・audio はその症状／upload 経路は測定により棄却** | ユーザー構成（854x480・validation 無し）の leg: cold window（world tick 120-200＝chunk build）p50 **254ms**・p95 619ms、run 全体は world tick 4006 まで進み presents 4800/約200s＝**約24fps**。audio のとぎれは**独立欠陥ではなく lag の症状**（sound manager は client thread の tick=frame で供給される）。**「最有力は upload 経路の per-segment `vkQueueWaitIdle`」は撤回**: その待ちは BH-27 前半で消え（`bootstrapQueueWaits=0`）、残った per-submission fence 待ちは最悪 frame で **12 ms**・after leg 全体で **48 ms**（233 wait・worst 4 ms）＝ 消せる上限が cold p50 の ~1%。BH-27 後半（staging ring）は**作らない**。cold 窓の帰属は未測定だった（計器の穴を §0.223 で修復）。§0.198/§0.223 |
| **性能（warm 実測・受入は p95/p99）** | **中央値は合格水準・spike がユーザーの lag／現行 jar の user leg は起動ゲートで落ちる** | `lead-warm3`（ユーザー構成・854x480・layer 無し・`worldTicksAtEnd=4041`）: cold 40 sample p50 44.1 / p95 250.2 / p99 575.9 ms、**warm 275 sample p50 24.0 / p95 105.6 / p99 207.4 ms**。**中央値を 24→20 にしても p95 105 が残る限り苦情は直らない**。spike 帰属の計器は `slowFrameThresholdMs`/`slowFrameCount`/`slowFrames`（commit `05935fdd5`・§0.206）だが、**保持が先着 12 件で startup しか写らず、GL-less では phase 行が `glless target: no handoff` に一致していた**（§0.223 で worst-N＋窓別 upload 待ちへ修復）。現行 jar `a47c1bd1453d6957` の user leg は warm 0 sample で `worldFrames=55 < 100` により tick 201 で FAILED（cold 22 sample p50 151.4）。§0.198/§0.199/§0.223 |
| **GUI scale 変更でクラッシュ（ユーザー第11報）** | **未再現／原文解析済み（task-58）** | 唯一の crash report（19:20）は `NoClassDefFoundError: CeltiumGlStub$ReadableRange` で、原因は **Forge の transformer に長さ0の class file が渡った**こと（`ClassReader.readShort` が空配列の offset 6 を読む）。instance の jar は `md5 ff85a8f5…` で `build/libs` と完全一致・長さ0の `.class` entry は無し・20:05 のセッションは同じメソッドを24万回通って落ちていない＝**古い/部分コピー jar の産物という仮説**。**「再現しなかった」を「修正済み」と書かない**。§0.206 |
| **F3 がとても重い（ユーザー第11報）** | **原因の数値あり・task-59** | ユーザーのログ: `CELTIUM_FONT_GLYPH_SUMMARY windows=1012751 … refused=138929 submitMillis=47246`＝**1 glyph window 46.6 µs**・28分で101万 window・13.7% は誰も描いていない。F3 overlay は最も文字が密。受入は F3 on/off の p50/p95 同一 leg 比較。§0.206 |
| **GPU stall latch（ユーザー第11報の lag 側）** | **未帰属・task-60** | ユーザーのログの拒否ヒストグラムで `the GPU stalled; no further drawing until the renderer is rebuilt` が**約50,200 batch**（format 7=21,366 ほか）。`gpuStalled` は latch（`:1102`/`:1126`・wait timeout `:10534`・拒否 `:3629`/`:3665`・geometry 破棄 `:5084`）で、`:10516` のコメント自身が false stall の高コストを警告。§0.206 |
| **video settings の画** | **PASSED（GUI 由来の床で較正）** | `vs47d-cand` **PASSED**（`guiFrameGateFloors nonBackgroundFraction>=0.37 verticalBands>=89 blackFraction<=0.118`・`guiFrameGateSource` が参照の GUI frame を名指し）。Done ボタンは画素一致（8/30,000）・panel 4.56%。bridge 対照は新設 luma 床（`panelMeanLuma<=0.70x`）で正しく FAIL（背景欠落 frame を捕捉）。§0.202 |
| **item 変換 family（ユーザー報告）** | **実プレイで解消（round 11「アイテムは治った」）・task-50/52 完了** | 根因は Forge の生 `GL11.glMultMatrix`（`ForgeHooksClient:425-455`）が GlStateManager を迂回し stub の no-op 表が捨てていたこと。**owner を1つに固定**（`ForgeHooksClientMixin` が `multiplyCurrentGlMatrix` HEAD＝GUI/FIRST/THIRD/GROUND の公開入口・stub は census のみ）。実測: 旧 jar は dome が `F²`（`0.648=0.803²`）が flag の on/off 両方、現行 jar は on `[0.805,1.431,-1.0]`/off `[0.805,1.431,-1.001]` で両方 PASSED。ユーザー実プレイで「アイテムの問題は修正」「ドロップの大きさも治った」「剣の持ち方も治った」、**round 11 で「アイテムは治った」＝形状・サイズ・blur を含めて解消**（blur は `confirmSamplerStateFromRecord` の着地後）。§0.191/§0.202/§0.206 |
| **hurt flash / 手持ち block の暗さ** | **hurt flash は実プレイで確認／night の明るさは task-55** | `immediate_tex_color.frag` に `u_AlphaRef<0` の INTERPOLATE 分岐（SPIR-V も一致）。`CELTIUM_ENV_COMBINE rgba=1.0,0.0,0.0,0.3` が hurt-on のみに出る1フラグ統制。**ユーザー実プレイ「ダメージで赤くなるようになった」**。残り: 夜の entity/雲が明るい＝**lit ITEM に lightmap 項が無い**（task-55）。§0.197 |
| **参照の変更（2026-10-11 14:xx）** | **bridge を廃止し upstream Celeritas 989ebf2e を参照に** | ユーザー決定「bridgeとかいらない。gllessのみでいこう」「glless vs celeritasのほうが楽だし、bridgeの保守もいらなくなる」。参照 leg は同一 fixture・同一 scene/pose/tick で `--mod .celtium-work/upstream-baseline/.../celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`＋celtium jar なし＋glless フラグなし。**検証済み**: `ref-celeritas2` = `nonBg 0.761173`・**PASSED**・2476色・tick 150 の screenshot 取得成功（§0.163） |
| **GL-less のメニュー（ユーザーの苦情）** | **解消（task-15・2026-10-11 16:18）** | **window 生成時に renderer を作り、world renderer が同一インスタンスを adopt** する形にした結果、
**ユーザー構成（Prism instance・fixture なし・validation client なし・`-Dceltium.vulkan.glless=true`）でメインメニューが描かれ、compositor 出力から撮れた**:
`.celtium-work/ww-menu-caps/compositor-0018.png`（1280x720・44,841 B・sha256 `ade674e0be57ea63…`・私が開いて確認＝ロゴ・splash 文言・3ボタン・Options/Quit・footer 14 mods）。
`CELTIUM_WINDOW_RENDERER created=true attempt=1 ticks=0 reasons=[]`・`CELTIUM_PRESENT hookCalls=3900 presented=3899 failures=0 swapsSkipped=3899`。
**残り**: panorama 背景は**まだ灰色**。copy の read 側は実装済（`celtium$serveTextureCopy`・stub の hand-off・1フレーム1回の batching・
`CELTIUM_TEX_COPY_SERVED`）だが、**served した元画像が暗い/空**（`mean=13,15,13 opaque=8192/65536` → 後続は `mean=0,0,0 opaque=0/65536`）。
**真因の一方は確定**: 我々が default pair の colour を毎フレーム clear していたが、vanilla は menu では **depth しか clear しない**
（`EntityRenderer.java:1171-1174`）ので panorama の feedback loop が壊れていた → `clearOffTarget` を depth のみに修正（§0.184）。
**ズレ（配置）は解消**: 1920x1080 で menu/world 両方の resize を実測（`CELTIUM_RESIZE_OBSERVED`・提示 dump が窓全面・black 0.0）。
**次の測定**: chain/frame image の dump で「submit 前に読んでいる」か「faces が pair に無い」かを切り分ける（frame-composite）。 |
| **Intel UHD 630（GL-less の第2 vendor）** | **受入済み（task-11 完了）** | Intel で **GL-less と参照の両 leg が PASSED**（`intel-glless` / `intel-ref`・AMD と同じ fixture/pose/tick・`validationClean=true`・VUID 0・`fixtureStatus=PASSED`）。**rows 0-200 の 256,000 画素で 8 を超える差は 0**（mean 0.453・max 4）、AMD の GL-less と Intel の GL-less は **0 画素差**（rows 0-200）・terrain 帯で 4 画素。`depthFormat=D32_SFLOAT`（両社とも候補1/6＝**fallback 候補は未行使**）。**device を決めるのは `VK_ICD_FILENAMES`**（pin で `vulkanDeviceCount=1`・無 pin は 3、`matchedOpenGl=false`）。**Intel 未検証**: 星/地平線 plane（fixture 制約）・depth fallback 候補・GUI/item/entity 段・resize/device loss・性能。`docs/history/2026-10-11-intel-uhd630-leg.md` |
| **参照 leg の取り直し（task-22 準備）** | **完了** | upstream Celeritas `d12425eaaf184e79`＋凍結 fixture `acfb62b16ed70ac3`・AMD・pose 0.5/12/0.5 yaw180 pitch20・tick 200: terrain `nonBg 0.761173` PASSED／entity `0.774399` UNCALIBRATED（variant 差＝正しい）／inventory `0.888320` UNCALIBRATED。**harness は instance.cfg の JvmArgs を読まない**（`patches/*.json` の `+jvmArgs` と `--jvm-arg` のみ）＝instance.cfg はユーザーの手動起動専用（16:0x に空へ変更されていたのを `-Dceltium.vulkan.glless=true` へ復元） |
| **空（dome+星+地平線）** | **受入済み（task-3 完了）** | GL-less vs upstream Celeritas で **rows 0-199 の 256,000 画素のうち 8 を超える差は 0**（max 4）。行平均が参照と 0.1 カウントまで一致、63色が両側で同一。gate は両分岐とも削除。**地平線 plane と星はこの fixture では行使不可**（flat world の `getHorizon()`=0／worldTime 固定で星輝度 0）と明記 |
| **entity/TESR モデル** | **受入済み（task-7/12 完了）** | モデル経路は正常（228/228 quad・拒否0・family 名を捕捉）。**真因は固定機能ライティング**（`EntityRenderer:1397-1402` が `enableStandardItemLighting` を renderEntities 直前に呼ぶ）。修正後 per-family 比 **0.98〜1.07**（以前 1.2〜1.6）。残差: spider の赤目 layer・hurt flash の combine・lightmap 未適用（この scene では 0.988 で不可視） |
| **雨/粒子（id5）** | **受入済み** | format 定数の誤り（`Short` を `Unsigned Short`）を修正 → `id5=142/142`・**id0 消滅**。参照との sky band 差 77.6% → **21.3%**。粒子の**画**は fixture 制約（炎が chest/enchanting table の層の中に spawn）で行使不可と明記 |
| **水** | **受入済み** | 色なし shader の alpha 1.0 固定 → 水中 overlay が全画面不透明（`black 1.0・1色`）。修正後 `black 0.325・475色`、**参照との差 0.42%**（deep water 0.008%） |
| **video settings（機能）** | **機能側は確立（監査B08を閉じた）** | `vs-apply4`（GL-less）: **操作した画面＝撮影した画面**（`net.minecraft.client.gui.GuiVideoSettings` / "Video Settings"）で、
option **`RENDER_CLOUDS` を "Clouds: Fancy" → "Clouds: OFF"** へ、**画面自身の `mouseClicked`+`mouseReleased`** で押して変更。
**Apply**: `Done (id 200)` を同じ経路で押下（handler が `gameSettings.saveOptions()`）。
**settings file が実際に変化**: `+renderClouds:false -renderClouds:true`。
**画面を開き直すと "Clouds: OFF" のまま**（保存が効いている）。撮影用に元へ戻す処理も入れた。
**残り**: この画面の**画**を同一 variant の参照と比較（保存参照が無いため現在は UNCALIBRATED）。 |
| **雲** | **修正済（census で decode・ユーザーの実プレイでも雲が見える）** | Forge の `CloudRenderer` が引き取る 24バイト POSITION_TEX_COLOR 経路を stub が decode するようにし、census は `drawn=205 vertices=752088`（以前 23/17,992）。**ユーザーの実プレイ報告でも「空も雲も」正常と確認**（task-13・§0.187） |
| **client-memory 描画** | **解消（ユーザーの起動ブロッカーは閉じた）** | window 生成時に renderer を作り（`stage=createDisplay`）、pre-world の batch も uploader mixin の offer 経路が取るので `no terrain backend is installed` は **0件**（ww19）。stub の client-memory decode は B09 の owned-buffer publish 経路へ（`/proc` guard は「guard であって所有権記録ではない」と明記）。§0.189 |
| **地平線直下の terrain 帯** | **閉じた（task-33・等価 build 状態）** | 原因は culling でも fog でもなく **build 状態の差**。`captureCondition=sections captureSections=41 captureMinWorldFrames=295` で両 leg を揃えると rows 200-320 は **mean 0.403・>8 が 744/153,600 = 0.484%**（以前 59%）、sky rows 0-199 は **0/254,720**、frame 0.087%。**22 vs 32 section は build throughput**（我々は 819 frame を要した）。旧「62/94.75/41.66% 差」の記載は撤回。§0.179 |
| **format 2/6/7 同居時の group-drop** | **未修正（task-14）** | id10 は isolate leg で**描画を実証**（白箱 3,909 画素が GROUP_BOUNDS の座標に一致）。2/6/7 のいずれかが同居すると frame から落ちる（6 leg の bisect） |
| **block 破壊 overlay** | **行使済・描画が誤り（task-17/39）** | fixture が subject を選び progress 5 を登録（`breakProgressCalls=5`）→ **`id3=16/16`（初行使）**。参照は `destroy_stage_5` のひび、我々は素の stone。geometry/blend/alphaRef/depth/texture は vanilla どおりで、**sample が bound atlas でない**。`texMat=none`（7 group 全部）で texture matrix 仮説は死に、次の実験は `mirrorPoisonFlat=12`。§0.183 |
| **GUI panel / slot 背景** | **video settings の panel は未修正（task-39）** | 通常 inventory の panel は以前修正済（参照との差 122.678 → 2.590）。**video settings の panel は `guiPanelDistinctColours=133` 対参照 **15,805**・`changedFraction 0.999996`**＝背景 texture から描かれていない。wrap 記録は入った（`wrapRepeat=38 wrapClamp=0`＝hardcoded CLAMP が bug だった）が**必要であって十分でない**。次の測定は GUI frame の panel crop＋背景 texture の mirror dump。§0.189 |
| **fog** | **受入済み（task-2 完了）** | fog-on と fog-off が **sky band のどの画素も違わない**のに `CELTIUM_SKY_DEGRADED fogEnabled=true fogAvailable=false`。原因: fog を group **close 時**に snapshot（行列/GL 状態は open 時）＋ `renderSky` が `:1337` で有効化・`:1354` で無効化 → dome の group が `fog==null`。修正: open 時捕捉＋`CELTIUM_FOG_CAPTURE`（§0.162） |
| **射影チャネル** | **受入済み（task-1 完了）** | `mirrorGluPerspective` の1フラグ差で dome の射影が sky 自身の `gluPerspective`（far 256）に。**terrain は不変**（y[0..120]・y[240..720] が 0 画素差）、dome が地平線に 11 行近づく、純青 0。`lastSections` 23 vs 31 は反復で分離中 |
| **GUI/HUD 段階** | **受入済み（task-5 完了）** | panel rect の平均差 91.145 → **26.405**、tab row 76.337 → **9.863**、label row 137.484 → **47.936**、hotbar strip 15.186 → **6.221**（参照 = 保存 GL 画像・同一 scene/pose/tick）。残差は sky band と下記の blend 問題 |
| **font glyph の alpha test** | **解決（task-5）** | GL-less で glyph quad が `blend=0 src=0 dst=0` で捕捉され、セルが不透明になる（ラベルが黒セル＋文字抜け）。`gs-glstate` は mirror と GL が**両方向に不一致**（mirror が blend=0 で GL が on: 146 group／逆: 322 group）を実測。gui-stage が overlay 段階で GL enable record から補正（1フラグで before/after） |
| **format 表** | **id3/id5 の拒否を修正** | `TEX_2S` は `SHORT`（表示名 "Short"）で私の定数が誤り → **BLOCK(id3)・PARTICLE(id5) が表の作成以来拒否**されていた（「雨が census に無い」の正体）。id0 の 57〜138 offer/run は全てこの1署名 |
| **alpha（色なし variant）** | **修正済み（両半分）** | 色なし shader が fragment alpha を 1.0 に固定 → 水中 overlay（POSITION_TEX・`color(f,f,f,0.5)`・f=0）が**全画面不透明**で blackFraction 1.0。修正: `layout(offset=96) float u_ColorA`（pipelines）＋ `color[3]` push（weather-water）。**受入 run 待ち** |
| **clear latch** | **測定済み・参照に近づく** | latch on/off の画素差は **11396 px（1.24%）で全て rows 213-238**（地平線帯）。保存参照と **latch ON がその帯で画素一致**、OFF は 32.65% が >8 の淡い空色スミア。terrain scene の blackFraction は両 leg 0.0 |
| **テクスチャ mirror** | **受入済み（task-4 完了）** | 更新経路を step 別に記録。`kept=100` で `lazySupply=0/0 partialRefusals=0 retainedAnnouncements=11 glLess[fromAnnouncement=215 fromCapture=7 noContent=0]`、patch 拒否0・capture miss 0。**未検証**: lightmap の経時更新・アニメ sprites・entity skin・format 5・1280x720 capture mirror |
| **チーム運用** | **確立** | 10タスク（task-1〜10）＋10メンバー。**ハーネスがゲームスロットを調停**（`67cf6f806`）・**ビルドロット**（`scripts/build.sh`）・instance の jar はコピーしない（`8ae2e64f8`）。**クリーンビルドが緑の証拠**（増分ビルドは欠落メソッドを隠した） |

|---|---|---|
| terrain / immediate の native 描画 | **実装済・両GPU受入** | 既定受付 format 1〜9。1 run の内訳: `offers 13000 / accepted 10643 / GL 本体 2386`（AMD・軽い世界） |
| **線（GL_LINES 系）の native 化** | **受入済（AMD 797・Intel 280 accepted）** | 変更前は 17 accepted。5 run 連続 PASSED、線の拒否ゼロ |
| **per-frame present** | **受入済（AMD/Intel）** | 263 presented / 262 GL swap skipped（§0.17）。GL swap は `@Redirect` で停止 |
| **提示画像と GL フレームの画素一致** | **受入済** | 上下反転比較で 平均絶対差 0.015・最大 3（§0.35）。**§0.103 で提示画像は正立になったので、以降は反転なしで比べる**（resize 時は提示画像の左下 `dw×dh` 領域を screenshot と比較） |
| **GL 最終合成の停止** | **受入済（フラグ、既定 off）** | `glCompositesSkipped=717/718`、提示画像は off 比 平均絶対差 0.001（§0.36） |
| **GUI / font / HUD** | **画像等価を受入済／N03 の glyph 経路は受入済（§0.144）** | HUD 帯（上部250行）平均絶対差 **0.000**・>8 が **0.00 %**。画面全体 0.015・0.04 %（§0.40） |
| **lightmap の供給漏れ（format 5）** | **解消・両GPU受入** | AMD `lightmapSuppliedOnDemand=2`・失敗なし・拒否ゼロ（§0.43）、**Intel も 2・失敗なし・拒否ゼロ・PASSED**（`p337-intel-lightmap`） |
| End の dimension 受入 | **両GPU PASSED** | capture 時 dimension 1 |
| **Nether の dimension 受入** | **AMD で絵・validation とも受入／Intel 未実施** | `validationClean=true`・msgs 0・capture 時 client/world/server すべて -1・`dimensionFramesInTarget=129`。根因は fixture 側（到着位置がポータル内＋`timeUntilPortal=0`）で修正済み。旧「両GPU受入」の記載は撤回済み。**画像ゲートも較正済み**: Nether は専用 pose 1.5/70/1.5 を持ち、参照 2 run（`nonBackground=0.938502`・`bands=720`）に対し GL-less 実測 **1973 / 0.938502 / 720 / 0.087951** で PASSED。overworld の pose は Nether の岩の中（0.183982 と 0.420806 の2値）で比較不能なため、床は scene ごとに持つ。**未完**: Intel での Nether 受入。§0.117 §0.119 §0.126 |
| 失敗経路（fence timeout / submit 失敗） | **両方とも clean・VUID 0** | fence: `stalledStops=1`・`stalledTeardownSkips=548`・解放しない。submit: fixture が FAILED として報告（§0.37） |
| **拒否の読み方（GL-less）** | **訂正: GL-less の「拒否」は GL fallback ではない** | GL-less では GL 本体が stub の no-op なので、拒否された batch は「GL が描いた」のではなく「**どこも描いていない**」。条件 (b) の GL 残量は **bridge 構成での拒否**から読む（そこでは拒否＝GL 本体が描く）。GL-less の拒否は「native で描かれなかった量」として別に読み、内容欠落の疑いがあるもの（frame 自身の blit は swap が提示済みなので欠落ではない）と区別する。この区別をせずに GL-less の拒否数を条件 (b) の残量に数えるのは過大評価。§0.118 |
| **条件 (b) の残量（実測）** | **約2割が GL** | 内訳: **format 2 の非画面合成 709**（FBO 所有権待ち）・**起動直後10フレームのみ 80**（本質的）・format 1/6/9 少数。lightmap 分は解消済み（§0.39/§0.41/§0.43） |
| **条件 (b) の本体（FBO 所有権）** | **既定で受入済（両GPU）** | **ゲームが Vulkan 所有 color/depth に直接描画し提示**。フラグなしの run で AMD/Intel とも **PASSED・VUID 0・GL エラーなし**、同一vendorの GL 経路と **AMD 0.001/0.00 %・Intel 0.026/0.02 %**（§0.59）。`-D…adoptGameFramebuffer=false` で旧経路に戻せる |
| **GL-context-less window（goal 項目6 / 条件 (b)）** | **達成（実 scene・model/TESR・Nether・compositor 到達まで）／残りは GL context 生成と起動直後** | GL context なしで world まで起動し、Vulkan が毎フレーム提示して GL swap を1対1で飛ばす（`p548-glless`: `presentedFromVulkan=287`＝`glSwapsSkipped=287`）。**実 scene を描画**し、**model/TESR は既定 ON**（エンチャント台の本が実ブロック位置、GL 参照と画素位置一致・§0.118）、**Nether は capture 時 dimension -1 で clean**（§0.117 §0.119）、**条件 (c) は marker 帰属＋番号付き presented dump との画素完全一致で充足**（§0.123）。lightmap の黒は announcement の配列形状が根因で修正済み（§0.116）。**未完**: 可視 glow（原因確定＝outline chain が off-target pair を共有。3つの一行変更は実測で失敗、chain 専用 image へ設計変更中・§0.127）、wedge の残存（thin streaks。巨大なものは N01 の model 頂点修正で消滅、format-8 帰属は撤回済み）、FBO/postprocess。**解決済みに更新**: depth format は能力ベースで選択（候補ごとに features＋image 受理を問い合わせ・AMD に 24-bit が無い実測）、画像床は scene/pose/variant を key に持ち未較正は `UNCALIBRATED_PRESENTED_IMAGE`、video settings は bridge で動作し GL-less では中身欠落として正しく FAIL。§0.114〜§0.127 |
| 条件 (b) の残り | **GL context の生成と、起動直後の本質的な拒否のみ** | **既定フレームバッファへ描く合成も native 化**（902件/run・受理 13202・GL 本体 2825・画像差なし・§0.60）。残る GL は起動直後10フレームと少数の pass 選択で、native の描画先がまだ無い瞬間のもの。**GL-less flag 経路（`-Dceltium.vulkan.glless=true`）は起動・提示・実 scene・compositor 到達まで実測済み**（§0.114〜§0.123）。既定は互換のため bridge のままだが、これは「未解決」ではなく選択である |
| **resize（項目7）** | **両GPU受入** | 恒久 stall を期間契約で解消（§0.102）し、提示の配置契約を GL composite に揃えた（§0.103）：提示は正立・1:1・左下アンカー・window clip、extent は surface 追随。resize run で描画領域 vs screenshot = AMD 0.001 %・Intel 0.000 %、stall 0 |
| **GPU 検証（条件 (d)）** | **AMD/Intel 両方で受入・ゲート修正済** | 描画側が選択確定時に `CELTIUM_NATIVE_DEVICE name="..." uuid=... matchedOpenGl=...` を出し、harness はこれで `--require-gpu` を判定する（device 未選択時のみ GL 文字列へ fallback、判定元を記録）。GL-less は `--render-node` では GPU を pin できず`VK_ICD_FILENAMES` が必要（pin で `vulkanDeviceCount 1`、無 pin は 3）。AMD RX 6400 と Intel UHD 630 の両方で bridge/GL-less とも PASSED・clean・VUID 0、ゲートが実デバイスを指名（Intel 実測: `gpuRequirementSource=nativeDeviceName`）。光量修正の効果も Intel で可視（GL-less band が (35.6,36.2,37.6) の黒から (102.5,63.9,58.7) へ、AMD と ±0.1・相関 0.9926）。**未検証 vendor/OS は Intel/AMD の Linux 以外すべて**。§0.118 |
| 実 window / compositor 出力（条件 (c)） | **充足（実測）／未測定は scanout のみ** | **実測**: `--headless` の私用 wlroots/Sway 出力（HEADLESS-1 1280×720）に対し `grim -o HEADLESS-1` で連番 capture。
`-Dceltium.vulkan.presentMarker=true` の間だけ **1280×720 全面が一様 magenta (255,0,255)**（14 capture）で、window 未 map の間は一様な黒（32 capture）＝出力の唯一の client は `Cleanroom`（xwayland・ゲームの pid・1280x720）。
**compositor capture 0035 は renderer の番号付き presented dump `frame-20.png` と 0/921600 画素差・最大チャネル差 0**。通常 run でも **capture 0066 = `frame-100.png`（0/921600 差）**で実 scene（fixture PASSED・presentedImageVerdict PASSED）。fixture 自身の presented 読み戻し（`presentedImageMeanAtCapture=170.0`・単一色）とも一致。
対照: GL screenshot は黒（presented と 99.925 % 差）＝ GL framebuffer は証拠経路ではない。旧記載の「`DISPLAY=:0` の root 0×0・合成器なし」は誤り（実測: sway 935 が `wayland-1`・HDMI-A-4 1920×1080@60 の実モニタ・Xwayland `:0`、`ffmpeg -f x11grab` は 1920x1080 を読める）。
**未測定は scanout のみ**（`kmsgrab` は DRM master 無し・非 root で不可）。§0.123・`docs/compositor-output-capture-2026-10-11.md` |
| **model 経路（表示リスト）の native 描画** | **既定 ON（§0.108）／TESR の list は未 capture** | 射影の出所を `glGetFloatv` から**行列ミラー**へ直して **18.49 % → 6.35 %**、モデルビューを **`V_now × C_compile⁻¹`** にして **2.19 %**（entity 場面・7通りで最良）。既定は ON（§0.108）。**未完**: 実測 `modelMeshLists=2 / modelQuads=12`＝腕と手持ちのみ。TESR の model（エンチャント台の本）は list 自体が capture されておらず、GL fallback に落ちて GL-less では何も描かれない。`celtium$drawModelList` は mesh の無い list を `modelMissingMeshes` として名指しで数えるようにした。§0.108 |
| **model mesh の頂点契約（N01）** | **修正済・両GPU受入** | `buildModelMeshes` が `nextVertex()` を quad ごとに呼び、4 隅が同一頂点を上書きしていた（頂点 Q・index は 4Q を指す）。per-vertex へ修正し契約検査（4 頂点/6 index・連番）を追加。**AMD で 36.9 % の画素が変化**（巨大 wedge 消滅）・Intel でも PASSED。§0.124 |
| **model mesh の寿命（N02）** | **実装・統合済（所有権ギャップも閉鎖・§0.141/§0.147）** | list ごとの compile generation、変更のあった list だけ再構築、旧 mesh は `VulkanRetireQueue` へ retire、失敗した部分構築は discard、teardown は `settleGpu` 成功後に `releaseAfterIdle`。担当の10ステップ probe 通過（live 2→3→3→2→3・stale/unmeshed/geometryMismatch なし）。§0.127 |
| **format 5（粒子）の descriptor gate** | **修正済・受入済** | 「描画経路が使わない pass を probe が要求していた」のが根因。73/73 受理・拒否 census 空（`1:606 2:191 4:1 5:76 6:568 7:464 8:128 9:180`）。§0.113 |
| **depth format の能力契約** | **実装済（AMD では旧定数と同一＝無影響と実測・§0.130）** | 候補（`D32_SFLOAT` → `D24_UNORM_S8` → … → `D16_UNORM`）ごとに features と `vkGetPhysicalDeviceImageFormatProperties` を問い合わせ、全て不可なら**候補別の理由つきで拒否**。AMD に sampled な 24-bit depth が無い実測に基づく。GL-less target と terrain pipeline が同じ決定を共有。§0.125 |
| **画像ゲートの較正（N06）** | **実装済・受入済** | calibration key に scene・pose・stack・sweep・allBlocks・entity flag・GUI scale を含め、key が違えば `floorsNotCalibrated` ＋ `UNCALIBRATED_PRESENTED_IMAGE`（**遡及適用しない**）。床は各 scene 自身の pose で較正し、閾値は下げていない。§0.126 |
| 配布物と docs の一致（N13） | **一部実施** | README を現状へ（GL-less で present まで・未完も明記）、性能節は制限付きの実測、`mcmod.info` の説明も同様。**jar 検証器が「既に離れた Java 8 契約」を強制していた**のを修正し、`verified: true`・`classVersions {46:111, 51:1, 52:613, 65:86}`。§0.127 |
| **font glyph（N03）** | **受入済** | ポインタ修正（`aa041c2d4`）が全てだった。magenta probe で修正前は magenta 画素ゼロ→修正後に presented frame へ glyph が出る。最終 `quads=52406 submitted=52406 accepted=52406 refused=0`、**unicode 経路を初行使**（`unicodeWindows=40548`）。advance は HEAD/RETURN 注入で構造的に不変。§0.144 |
| **post chain の UBO 経路（N10）** | **受入済** | color_convolve **192B**・spider **144B**（監査の 180/140 と旧 196 は誤り。SPIR-V 実測）。拒否0・VUID 0・両経路のブロックはバイト一致。**同梱の実バグ修正**: ShaderGroupMixin の extras が 88 始まり→84（invert の `InverseAmount` が4バイトずれ、native enderman が 0.8 に対し 0.0 を読んでいた） |
| **outline chain の実行** | **実行を確認** | `CELTIUM_POSTCHAIN create`・`program=entity_outline route=pushConstants bytes=84`。**chain pair も実行時に使用**（`framebuffer bind: … pair=chain-pair`）。旧「runtime は outline を描かない」は**撤回**（§0.143） |
| **監査 A01〜A05** | **全修正済** | A01 glyph ポインタ／A02 timeout の fence・buffer 隔離／A03 参照較正キーの定数化（実測で `keyMatches=false`→未較正）／A04 溢れ拒否／A05 watchdog token |
| **所有権ギャップ（監査）** | **全閉鎖** | `VulkanDynamicMesh` コンストラクタ・`buildModelMeshes` の構築中 mesh・`VulkanHostBuffer.create`・**N17 画像側**（bind 後の fault で image と memory の両方が解放されることを実測・§0.147） |
| **死んだ注入という主張** | **撤回** | `CELTIUM_RENDERENTITIES calls=3`・`CELTIUM_BLOCKLAYER calls=1,2` を実測。**計器で反証**（§0.143） |
| **entity 場面の残差（既定構成）** | **確定: fixture のキャプチャ仕様（描画欠陥ではない）** | format 5 = 実粒子（提出元 `ParticleManager`）。残差は**提示フレームの生存粒子**が写る画素そのもの（GL 側は除去済みで粒子画素 0）。除外方向で 0.32 % = ノイズ床。同フレームの静止 FLAME 比較で format 5 の色・位置は正しく、残差は経年差のみ（§0.104） |
| **通常場面（実体なし）の一致** | **両GPU受入** | 提示画像 vs GL の絵: **AMD 0.011 / 0.01 %**、**Intel 0.000〜0.016 / 0.00 %**（§0.77・§0.92 の撤去後も維持） |
| 全 entity / TESR・全 block state・全 mod | **未確認** | registry 254 種掃引（AMD）と部分掃引（Intel）は実績として保持 |

**未検証のまま残っているもの**: 文字（GL-less で vanilla font が即時モード no-op に落ちる。glyph は native 受理済みだが**実画像未確認**）、
可視 glow、wedge の残存、FBO/postprocess の native 化、全 entity/TESR の画質、全 mod 互換（最も重いのは文字）、
scanout そのもの（条件 (c) の compositor 出力到達は §0.123 で充足。走査出力は DRM master 権限が無く測れない）、
リブランド。**性能は実測済みだが制限つき**（§0.127 と README: 参照は 60Hz 提示間隔であって描画コストではなく、GPU 時間比較は存在せず、場面は1つ）。
**古い数値を最新版全体の一致率として使わない**（68.81 %・local 0.267/255 は当時の部分測定）。

## 0.126 **進行中**: フル監査（`docs/history/2026-10-11-full-audit-0903.md`）の P1/P2 を担当別に分割

監査は17件（P1 6・P2 11）を挙げ、6つの契約バッチを提案している。現在の担当と所有は以下のとおり。

| 監査 | 内容 | 所有ファイル | 状態 |
|---|---|---|---|
| N01 | model 頂点の上書き（`nextVertex()` が quad ごと） | — | **修正済み** `454fb3600`・AMD 36.9% 画素変化・Intel PASSED |
| N02 | model mesh の generation/retire/部分構築回収 | `VulkanDynamicMesh`・`VulkanModelQuadCapture` | 担当稼働中（builder への patch はテキストで受領予定） |
| N03 | 文字が GL-less の no-op 即時モードに落ちる | `FontRendererMixin`＋`impl/font/` | 担当稼働中（glyph 48000 提出/48000 受理/拒否0。実画像受入は未） |
| N04 | bridge の post chain が空 stand-in を入力にする | `VulkanPostChain` | 担当稼働中 |
| N05 | record した layout と submit した layout の不一致 | `VulkanPostChain`＋`VulkanGlTexture` | 担当稼働中 |
| N06 | 画像ゲートが scene/pose/variant を区別しない | `ValidationClient` | 担当稼働中（camera を view entity にも適用する修正は受領済み） |
| N07/N08/N17 | sprite の mip・ByteBuffer slice/境界・host 確保失敗の解放 | `VulkanGlTexture`・`VulkanTextureRegistry` | 担当稼働中 |
| N10 | 能力ベースの depth attachment 選択 | `VulkanDepthFormat` 他 | **実装済み** `9655ff681`（run 検証待ち） |
| N11 | 受理した線幅と実描画幅の不一致 | `VulkanTerrainGpuBackend` | **修正済み** `4b29bd028`（run 検証待ち） |
| N12 | ベンチの条件一致が欠測を検出しない | `benchmark_celtium.py` | 担当が修復・実測済み `22de2155f`（欠測拒否の強化は残） |
| N13 | README/plan/goal/配布物の同期 | docs 他 | 未着手 |
| N14-N16 | bounded wait・census の意味・descriptor ring | 未割当 | 未着手 |

**ビルド状態（この時点）**: 4担当が同時編集中のため `:celtium-vulkan:compileJava` は `VulkanGlTexture` の4エラーで止まる。
私の commit は自分のファイルのみで、他担当の作業中ファイルは未変更のまま保全している。

**運用上の確定事項（このセッションで学習）**
- **counter の不在を描画の不在と読まない**（bridge のレポートに model カウンタが元から無い・§0.125）。
- **ミキシン間で直接メソッドを呼ばない**（メソッドは対象クラスへマージされるため起動時 `NoClassDefFoundError`。§0.121 の経緯）。
- **混合構成で「絵が止まった」と結論する前に、renderer 自身の番号付き dump と fixture の dump を分けて読む**。
- 計器の意味を変えたら、その名前と意味を変える（`mirrorMisses`→`stubTransformCalls` の経緯）。

## 0.229 **BH-35: strict-symbols leg を取得。広告は 4.6.0/4.60、backing は 183 名、到達した未モデル名は 647（ほぼ legacy の 1〜2 回プローブ）。広告を変えるには互換目標の名前が要る**

- **計器の確認（lead の指示どおり名前の意味を検証）**: `-Dceltium.vulkan.strictSymbols=true` は**table 外の名前が到着するたび**に
  `CELTIUM_UNKNOWN_SYMBOL <name>` を出し、`strictSymbolLines`（既定 200）で bounded。**実測でちょうど 200 行**＝名前どおり。
  終了時に `CELTIUM_UNKNOWN_SYMBOLS`（頻度順・先頭 24 名＋`…(N more)`）を出す。**今回その2つを混同しない**: 前者は「到着順の先頭 200 件」、
  後者は「全 distinct の頻度順」で、前者を総数と読むと誤る。
- **leg**（jar は BH-18 と同じ `485bc6d5b55079f2`・fixture `a15857b0b594314b`・glless・layers ON・AMD・150 s）:
  - **広告**: `glVersion=4.6.0 Celtium`（report）・`GL_SHADING_LANGUAGE_VERSION=4.60`・`GL_VENDOR=Celtium`・`GL_EXTENSIONS=""`（indexed は NULL）。
  - **backing**: stub の table は **183 名**。そのうち **35 entry point が no-op として実際に呼ばれた**（`CELTIUM_NOOP_CENSUS noopEntryPoints=35 noopCalls=31,435`、
    top は `glLightfv`・`glTranslatef`・`glLoadIdentity`・`glRotatef`・`glColor4f`・`glPushMatrix`…）。
  - **到達した未モデル名**: `CELTIUM_UNKNOWN_SYMBOLS` の先頭 24 名の count は **1〜2**、`…(623 more)` で **distinct 647 名**。
    strict の 200 行も全て count 1。名は **`glAccum`・`glAreTexturesResident`・`glClearAccum`・`glClearIndex`・`glColor3b`・`glVertex3*s`・
    `glGetMap*`・`glGetPixelMap*`・`glRasterPos*`・`glNormal3d`・`glSecondaryColor*`・`glFogCoord*`** など **GL 1.x/2.x compat の一回プローブ**が支配的で、
    4.x 系は `glNamedFramebufferParameteri`・`glGetnUniformfv`・`glPushDebugGroup`・`glGetTextureSubImage`・`glGetSamplerParameterfv` が各 1〜2 回。
  - `validationMessageCount=0`・`validationClean=true`（fixtureStatus は既知の画像ゲート `FAILED_PRESENTED_IMAGE`）。
- **この leg が言えること**: 走っている mod 集合は **未 backing の 4.6 専用機能に導かれていない**（到達は全て 1〜2 回の capability probe、
  大半は legacy）。「静かに嘘をつく」危険が実証されたのは legacy 側で、値を返す関数は 0/NULL を返すため mod には「無い」と見える。
- **どちらを変えるかは契約が要る（ここで停止）**: (a) **backing を増やす**＝到達 647 名は手書き table の規模を超え、意味のある部分集合を選ぶ基準
  （どれが「compat no-op として正しい」か）が未定義。既知の罠として、到達した legacy 名を **SEM_NOOP として table に入れると UNKNOWN の数が減る**＝
  「吸収して見えなくする」ので**やってはいけない**（この repo が `mirrorMisses`→`stubTransformCalls` で学んだ形）。(b) **広告を下げる**＝
  `GL_VERSION`/`GLSL` を backed な集合に合わせる（例 3.3/2.1）が、**互換目標がどの GL 版かという contract が文書に無い**ため、
  mod 集合が 4.x 必須のときの退行動作を決められない。**決定に必要な名前**: 「ユーザーの mod 集合に見せなければならない GL 版と、
  その版で backed を要求する entry point の範囲」。これが決まるまで advertisement は変えない（変えると黙って別の defect を作る）。

## 0.228 **BH-18 受入: surface が提示した値と要求値を1行で突き合わせ、AMD/Intel で一致（clamp 分岐は未行使と明記）**

- **変更**: `VulkanSwapchain` が **surface の提示値**（`minImageCount`・`maxImageCount`（0 は `unlimited(0)`）・`supportedUsageFlags`・
  `supportedCompositeAlpha`・`currentExtent`（未指定なら `unspecified(0xFFFFFFFF)`）・`min/maxImageExtent`・`currentTransform`・
  `presentModesOffered`）と **要求値＋突き合わせ**（`images=`（clamp の有無）・`extent=`（'surface currentExtent' か 'request clamped into min..max'）・
  `usage=`（落とした任意 bit）・`compositeAlpha=`（OPAQUE でない理由））を `CELTIUM_SWAPCHAIN` の stdout 行と renderer diagnostics の
  `describe()` の両方に出す。**罠は定数と分岐で符号化**: `SURFACE_EXTENT_UNSPECIFIED = 0xFFFFFFFF`（unsigned 比較）と
  `maxImageCount == 0 = unlimited`。
- **受入 leg（両 GPU・layers ON・jar `485bc6d5b55079f2`・fixture `a15857b0b594314b`・glless・`minWorldFrames=5`）**:
  - **AMD RX 6400**: `surface-offer: minImages=3 maxImages=unlimited(0) usage=0x8009f compositeAlpha=0x9 currentExtent=1280x720
    minExtent=1280x720 maxExtent=1280x720 transform=0x1 presentModesOffered=[IMMEDIATE,MAILBOX,FIFO,FIFO_RELAXED]
    requested[images=3 (desired 3, max unlimited) extent=1280x720 (surface currentExtent) usage=0x13 compositeAlpha=0x1 OPAQUE]`・
    **`validationMessageCount=0`・`validationClean=true`**。
  - **Intel UHD 630**: **同じ数字**（`usage=0x8009f`・`compositeAlpha=0x9`・`currentExtent=1280x720`・`min=max=1280x720`・同じ present mode 集合）・
    **`validationMessageCount=0`・`validationClean=true`**。＝要求値はどちらも surface の集合から導出されている（`usage 0x13` は
    COLOR_ATTACHMENT|TRANSFER_SRC|TRANSFER_DST、`compositeAlpha 0x1` は OPAQUE が提示されているため）。
- **未行使（明記）**: この2つの surface は `currentExtent` を**指定**し（`min=max=1280x720`）、`maxImageCount` も unlimited なので、
  **clamp 分岐（0xFFFFFFFF のときの min..max への丸め、有限 maxImageCount への切下げ）はこの leg では走っていない**。
  コードは両分岐を持ち、罠は定数と報告文字列で符号化されているが、**行使した証拠は無い**。
  また world resize 要求（`resizeAtTick=60`・`resizeTo=1000x600`）は trace に `CELTIUM_RESIZE_REQUEST` を残したが
  framebuffer は 1280x720 のまま＝**recreate 経路はこの harness では行使できなかった**（swapchain 生成は同じ関数を通るが、この leg は2回目の生成を示していない）。
- 両 leg の `fixtureStatus` は既知の画像ゲート `FAILED_PRESENTED_IMAGE`（BH-18 とは独立）。

## 0.227 **ユーザーの LagSpike プロファイルを解読: スパイクの 5% の frame が wall time の 50% を食い、中身は `gameRenderer` の無名の残差。`gui` は毎 frame 約14 ms の定数**

- **証拠**: `.celtium-work/evidence/user-reports/2026-10-12-lagspike.json`（bzip2 原版 `.lagspikeprofile` を伸長、sha256
  `ef29c446697ccad047cfa704f30daa8d97fe917ec6a0f926f2cd3acb6335bcf8`）。解読器は `.celtium-work/evidence/user-reports/analyse_lagspike.py`、
  出力は同ディレクトリ `lagspike_analysis.txt`。
- **判明した形式（version 文字列 `1.1.0`）**: `[u8 0][u8 len]["1.1.0"]` → `u32be セクション数 77` → 77 ×（`u8 0`, `u8 len`, 名前）で byte 830 まで →
  **varint 列**（prologue 3個 `0,0,1`、末尾に varint 1個 = 0）。本体は **1,364,259 レコード**、各
  **（childCount varint, sectionId varint, durationNanos varint）**、**frame ごとのブロック**（各ブロックは `frame` レコードで始まる。358 ブロック）。
  単位は ns。**推測であって確定でない点**: 末尾 varint の意味（値 0。これが無いと 3 で割り切れない）、frame レコードの childCount が
  ブロック内の実 children より大きい場合がある（block 0: 102 対 100 で閉じない）。frame 直下のセクションは childCount 0 の
  **平坦な列**で、入れ子なのは entity/particle の部分木だけ（各ブロックが自分のレコード数を過不足なく消費することは検算済み）。
- **捕獲範囲**: **358 frame / 20.00 s**（`frame` レコードの duration 合計）。frame 時間 mean **55.9** / p50 **34.1** / p95 **331.8** /
  p99 **358.9** / worst **384.6 ms**。ヒストグラム: 16.7–33.4 ms 172・33.4–50 122・50–100 41・100–200 1・200–300 2・**300–500 20**。
  **≥50 ms は 64 frame（17.9%）だが wall time の 10.1 s / 20.0 s＝50% を占める**（≥100 ms は 23 frame）。
- **スパイクの帰属**（最悪 5%＝18 frame、≥331.8 ms、中央値 frame 27.0 ms）: **`gameRenderer` が 318.6 ms/frame 対 25.0 ms（12.7×）**、
  `level` 31.9 対 11.3（2.8×）、`tick` +23.5、`blocks` +8.9・`getChunk` +7.8・`regular` +4.1・`getBrightness` +3.7・`chunkCache` +1.9
  （**いずれも中央値 frame では 0**＝chunk 構築/upload 系がスパイク内にだけ現れる）。一方 **`gui` は 15.4 対 13.7 ms（1.1×）＝スパイクではない**。
  最悪 frame（384.6 ms）の中身: gameRenderer 342.0・tick 29.7・keyboard 26.6・level 15.8・gui 14.8。
- **無名の残差（最重要の未帰属）**: スパイクの `gameRenderer` 318.6 ms/frame のうち名前のある子セクションで説明できるのは約 72 ms。
  **残り約 246 ms/frame はどのセクションにも現れない**。形式側の理由は明確で、frame 直下が平坦な列なので
  「profiler が計器を持たない pass」または「pass 内の待ち（GPU 完了待ち等）」は `gameRenderer` 自身の時間に入る。
  我々の `frameFenceWaitMillis`（検証 leg で 1,201 waits / 167 ms＝0.14 ms/wait、別 leg で 5,832 waits / 448 ms）は
  **桁が違いすぎてこの残差の説明にならない**ので、機構は断定しない。
- **定数の `gui` 税**: `gui` は 1 frame あたり約 **13.7 ms**（757 レコード/358 frame＝2.11 record/frame、record p95 19.0 ms）で、
  中央値 frame 27.0 ms の約半分。スパイクで増えない＝**定数コスト**。font/panel の作業が効く対象。
- **我々の計器との不一致（記録すべき発見）**: `forgeHudText` 合計 **2.7 ms**・`chat` **201.8 ms**（20 s 中）で、**文字はこの捕獲では支配要因ではない**
  （F3 を開いていなかった可能性が高い）。「F3 text が最大」という我々の仮説はこの session では支持されない。
  `getBrightness` は **448,707 呼び出し**（p50 0.4 µs・合計 255.7 ms）と回数が突出。
- **限界**: 形式の 2 点（末尾 varint、frame の childCount 過多）は未確定。section の自己時間は `frame`/`root`/`gameRenderer` が
  それぞれ全 frame 時間を持つため単純合計が frame 時間の 4.01 倍になり、**重複しない分解ではない**（本節は record duration＝inclusive として読む）。
  プロファイルからはユーザーがどの jar で遊んだかは分からない（jar hash は profile に無い）。

## 0.226 **OPT-08 step 1（upload batch を semaphore で frame submission へ渡す）は撤回。二重 signal の正体は upload batch 自身の semaphore と計器で確定**

- **やったこと**: `flushUploads()` を「submit して待たない」形にし、binary semaphore を signal、frame の `submitCommands` がその wait を取る形にした
  （batch の barrier は writes を available にするだけなので、可視性には wait が要る）。ring の slot は batch の fence が signal するまで予約、という所有規則は維持。
- **失敗**: `VUID-vkQueueSubmit-pSignalSemaphores-00067`（layers ON の 2 leg で各10件）。**原因は同期ではなく signal の消費者が保証されないこと**:
  `flushUploads()` の呼び出し元は (1) frame の `submitCommands`（消費できる）と (2) ring が埋まったときの `celtium$reserveUploadSlot` 内の early flush
  （**同じ step で私が追加**、消費する submission が続くとは限らない）。消費者がいない signal は outstanding のまま残り、次の flush が再び signal する。
- **trace で正体を確定**（`-Dceltium.vulkan.uploadTrace=true`・layers ON の 2 leg）: VUID 本文の handle `VkSemaphore 0x1c800000001c8` は
  batch 自身の semaphore（`CELTIUM_UPLOAD_SIGNAL batch=N sem=0x1c800000001c8`）。trace は **連続する `signal=true`**（batch 7→8・12/13/14・28/29）を示し、
  ring-pressure flush が支配的な leg では frame 側の trace が **0 行**＝signal を出した経路の消費者が同じ呼び出しに存在しなかった。
  したがって同時期の `a6dbb135d`（frame 自身の wait semaphore）・`cd53bbff2`/`5d508be38`（bridge pair）は**この10件については別の semaphore を指している**。
- **撤回（現行 HEAD・私の revert は `5d508be38` に取り込まれた）**: `flushUploads()` は submit + 自 fence wait（受理済み OPT-07 形）に戻した。
  **残した measured win**: (a) ring が埋まったら settled 経路へ落とさず early flush（**1546/2419 copies → 3/6306 copies**、`uploadEarlyFlushesForRingPressure=136`）、
  (b) frame 側の直列化の計器 `frameFenceWaitMillis`/`...Count`/`...WorstMillis`（OPT-08 第2 step の目標値）。
- **検証 leg**（`chain-t51.sh`・layers ON・terrain＋keepGui＋captureEntities・`minWorldFrames=5`）: **VUID 0行・`-4`/DEVICE_LOST 0件・
  `validationMessageCount=0`・`validationClean=true`・exit 0**（非緑は既知の画像ゲート `FAILED_PRESENTED_IMAGE` のみ）。
  同 leg の計器: `uploadCopiesBatched=3477`・`uploadFallbackCopies=3`・`uploadFlushMillis=393`・`frameFenceWaitMillis=167`/1201 waits/worst 5 ms・
  `frameArena regionsOpened=2809`・`hostBuffersCreated=6`。
- **OPT-08 第2 step（2 frames in flight）の前提**: semaphore は**消費する submission から signal する**（`submitCommands` の flush からのみ signal するか、
  timeline semaphore の単調値で outstanding 状態機械自体を消す）。加えて per-slot 資源（command buffer・fence・uniform region・
  frame target の image/semaphore）が必要で、これは `GllessFrameTarget` 側の作業＝frame target owner との共同 unit。

## 0.225 **OPT-07 再挑戦（契約 (b): destination を flush 時に解決）は受入: 0 VUID・submit 数 −78%・待ち時間の合計は不変**

- **契約 (b)**: pending copy は handle ではなく `VulkanBuffer` を保持し、`flushUploads()` が `target.buffer()` を解決する。
  growth の前後で offset は不変なので、解決を遅らせるだけで透過になる。解放済み destination は `VK_NULL_HANDLE` に解決し、
  copy は **skip して計数**（`uploadSkippedDeletedDestinations`）。加えて `VulkanBuffer.ensureCapacity` は
  **copy-out の前に flush** する（growth が pending の bytes を失わないため。growth は run 中数回なので per-copy wait は戻らない）。
  arena の segment 再利用は gate 不要: copy は CPU 書込み順を保ち、batch は同一 queue に順に submit されるので、
  free→再 allocate された segment は最後の書込みで正しくなる。ordering は既存の `submitCommands` 冒頭の `flushUploads()`。
- **受入 leg 1（layers ON・terrain＋keepGui＋captureEntities・glless・jar `1601de0cc240c073`）**: **VUID 0行・
  `-4`/DEVICE_LOST 0件・`validationMessageCount=0`・`validationClean=true`・exit 0**。非緑は既知の画像ゲート
  `FAILED_PRESENTED_IMAGE` のみ。＝前回 device lost したのと同じ形の leg が通る。
- **受入 leg 2（同じ scene・fixture `0407469a82ef64ee` 固定・layer 無し・sections+700 capture）**: `uploadCopiesBatched=30434`
  （`uploadFlushes=6825`・`uploadFlushMillis=1060`・worst 10 ms・`uploadPendingCopies=0`・`uploadFallbackCopies=3`・
  `uploadSkippedDeletedDestinations=0`・`uploadRingBytes=4 MiB`）。before は受理済み `perf-arena`（jar `a23a6b0b`）:
  - **submit/wait 数**: `uploadSubmitWaitsAtEnd` **31343 → 6910（−78%）**、cold `uploadSubmitWaitsColdWindow` **3889 → 1309**、
    warm **7969 → 579（−93%）**。`uploadQueueWaitMillisAtEnd=0` は両方。
  - **待ち時間**: `uploadSubmitWaitMillisAtEnd` **972 → 958 ms**、cold **124 → 182 ms**・warm **200 → 97 ms**。
    **frame あたりでは改善していない**（cold 1.53→1.67 ms/frame・warm 1.20→1.35 ms/frame）= flush が batch 全体の完了を待つため、
    CPU が待つ総 GPU 時間は同じ。**「queue idle を fence に置き換えただけ」以上の主張はしない**（plan の OPT-07 の但し書きどおり）。
    真に待ちを消すのは semaphore で frame submission に渡す形＝OPT-08 の領域。
  - `frameInterval*` は cold 109 sample p50 33.8 / p95 54.7 / p99 79.3、warm 72 sample p50 49.4 / p95 75.0 / p99 92.3 だが、
    **before と比較しない**: 同じ 700 frame でも tick 到達が 513 対 770、sample 数も 109/72 対 81/166 で、マシン競合も違う
    （`frameInterval` の差を OPT-07 に帰属できない）。
  - OPT-05 の counter は不変で良好: `regionsOpened=8897 blockedOpenings=0 promotions=0`・`hostBuffersCreated=6`。
- **未確立**: Intel での再確認、`uploadFlushWorstMillis` の 10 ms が worst frame に寄与するか、flush 回数（6825＝約 9.75/frame）を
  frame あたり 1 回へ落とす余地。

## 0.224 **P0: OPT-07 の copy batch 化は destination の所有権を壊し device lost を起こした → revert（OPT-05 は無関係と確認）**

- **症状**: HEAD `f9150335` の jar で terrain scene が **device lost**。lead の layered leg は
  `VUID-vkCmdCopyBuffer-dstBuffer-parameter`（×2）＋同 command の threading VUID、`libvulkan_radeon.so` の SIGSEGV。
  jar `513421d0` の私の leg は `settleGpu(region eviction): vkDeviceWaitIdle returned -4`（最初の `updateRegions`）、
  bs6/bs7 は同じ `-4`、bs8 は `vkQueueSubmit returned VkResult -4`（`VulkanContext.endCommands` ← `flushUploads` ← `reserveUploadSlot`）。
- **原因（OPT-07 の batch 化・私の変更）**: `uploadToBuffer` が copy を **pending** のまま保持したため、batch が
  **古い `dstBuffer` handle** を記録した。呼び出し側は「戻った時点で copy は片付いている」前提で、`VulkanArena.write` の後の
  arena growth（`VulkanBuffer.ensureCapacity` が旧 VkBuffer を破棄）や world teardown が destination を差し替える。
  後から submit された batch は存在しない handle を指した。threading は原因ではない（全経路 render thread。threading VUID は
  同じ無効 handle を layer が報告したもの）。
- **対処**: commit `14ed4615f` で `uploadToBuffer`（両形）を**従来の settled one-copy 経路**（`ensureStagingCapacity` → memcpy →
  `recordStagingCopy`＝1 copy ごとに submit+fence wait）へ戻した。ring・`flushUploads()`・`uploadBatchSummary()` は
  **未配線のまま**残し、revert した呼び出し地点に契約を書いた。`submitCommands`/`VulkanBuffer.readInto` の `flushUploads()` は
  pending が無い限り no-op。
- **検証 leg**（`chain-t45b.sh`・jar **a23a6b0b874c393c**・fixture `0407469a82ef64ee`・terrain＋`keepGui`＋`captureEntities`・
  glless・**layer ON**・`minWorldFrames=5`）: **`-4` 0件・VUID 0行・`validationMessageCount=0`・`validationClean=true`・exit 0**、
  capture と verdict まで到達。非緑は既知の画像ゲートのみ（`status=FAILED_PRESENTED_IMAGE`・`worldTicksAtEnd=241`・
  cold 71 sample p50 53.4 / p95 74.3 / p99 151.6 ms）。**注意: この jar には fog-path の未 commit な BH-14 drain revert も入っている**
  （backend 02:04:21 変更・jar 02:04:46 作成）ので、この leg は2つの revert を分離しない。分離するのは copy 経路の VUID と
  `flushUploads` の stack（drain からは到達しない）。
- **OPT-05（frame arena）は無関係**: 故障は copy-buffer 呼び出しで、arena はそれを出さない。上記 leg も arena 有効で完走。
  `VulkanFrameArena` は frame ごとに 1 slot、reset は `!awaitingCompletion && (epoch<0 || epoch<=completedEpoch) && pendingGroups==0`
  のみ、`completed()` は backend の確認済み fence wait から `VulkanContext.noteCompletionConfirmed` で供給、slice の解放は
  `mesh.delete()` → `Region.release()`（OPT-04 の「完了だけが解放の根拠」）。5 pipeline は `mesh.bindAndDraw` に集約
  （同じ3呼び出し・mesh 自身の offset・state/layout/stride 不変）。
- **OPT-05 の受入（取得済み・`perf-arena` leg）**: `chain-t45c.sh`・jar `a23a6b0b874c393c`（revert 後・arena 有効）・
  fixture `0407469a82ef64ee`・terrain・glless・layer 無し・sections+700 capture で **warm 窓に到達**
  （`worldTicksAtEnd=770`・`worldFrames=700`）: **`frameArena regionsOpened=8316 blockedOpenings=0 reclaimedSlots=700
  promotions=0`・`frameArenaFallbackGroups=0`・`peakVertexBytes=748272`・`peakIndexBytes=195560`・
  `hostBuffersCreated=6`（うち `hostBuffersFromFrameArena=2`）** ＝ **group 8316 に対して host buffer 確保は 6 件**
  （旧経路は group ごとに vertex/index の2確保＝約 16,632 件の算術）。slot1 は capacity 0（一度も確保せず）＝
  1 slot 分（1 MiB vertex + 256 KiB index）で足りている。report 時点で `slot0=[... awaiting=true groups=1]`＝
  最後の frame の group が保持され reuse が正しく gate されている。
  `frameIntervalSampleCount=81` cold p50 **47.1** / p95 **69.4** / p99 **173.2 ms**、
  **warm 166 sample p50 57.6 / p95 97.8 / p99 123.8 ms**。status は既知の画像ゲート `FAILED_PRESENTED_IMAGE`。
  **注意**: この jar には fog-path の BH-14 drain revert（現在は `e30a23cd6` で commit 済み）も入るため、
  frame 時間は OPT-05 単独に帰属しない（allocation/fallback の各 counter は drain に依存しない）。
  before 側（jar `a47c1bd1` の `perf-user2-warm`）は cold 68 sample p50 53.4 / p95 80.1 / p99 193.3 ms・
  warm 0 sample＝**現行 jar の warm before は存在しない**。
- **OPT-07 再挑戦の契約（この失敗から）**: destination が copy pending 中に破棄・差し替え・segment 再利用されないこと。
  (a) `VulkanBuffer.delete/ensureCapacity` と arena segment 再利用を batch の完了 epoch で gate する、または
  (b) flush 時に handle を解決する（記録時ではなく）＋ arena segment 再利用の gate。どちらかが無い限り per-copy settle を維持する。

## 0.223 **BH-27 後半（copy の batch 化＋staging ring）は測定により棄却・帰属計器の2つの穴を修復（worst-N slow frame・窓別 upload 待ち）**

- **判定条件は「最悪 frame の phase 行が upload 経路か」。答えは**いいえ**（その leg 自体が FAILED）。** 親が指した
  `chain-t44d`（`perf-user`・mod jar `celtium-t44d a47c1bd1453d6957`・fixture `2b05481a76668fb4`・AMD RX 6400・glless・
  validation layer 無し・`handoffTimings=true`）は **warm 窓のサンプルが 0**で、`--duration` も想定 400 ではなく `t44-run.sh` の既定 **120**。
  world は実時間 ~22 s で **55 frame**（≈2.5 fps）しか出せず、fixture 自身の起動ゲートが
  `Too few world rendering frames for startup validation: worldFrames=55 (threshold 100), worldTicks=201` で **run を落とした**
  （`status=FAILED`・`validationMessageCount=0`）。cold 窓（tick 120..200）は **22 sample で p50 151.4 / p95 349.8 / p99 459.0 ms**。
- **最悪 frame（`worldTick=1` 1350 ms）の phase は startup**: swapchain 作成・chain pair self-test・framebuffer bind/unbind・
  `standalone UI render pass`。**upload 経路の行は無い。** 同じ 12 frame の counters は全 frame で
  `bootstrapQueueWaits=0 bootstrapQueueWaitMillis=0`、`bootstrapSubmitWaits` は **34→49**・`bootstrapSubmitWaitMillis` は
  **12→25 ms**・worst **5 ms**（tick 54 までの累積）＝ 1350 ms の frame が boot から背負った submit fence 待ちは **12 ms**。
- **BH-27 前半の before/after（同一 scene/pose・同一 tick 帯・同一 GPU・layer 無し・presentReadback 有り）**: `bh27-verify`
  cold p50 **216.9**/p95 **817.8**/p99 817.8（16 sample・`worldTicksAtEnd=207`）→ `bh27b-verify`
  cold p50 **70.6**/p95 **145.8**/p99 **503.4**（44 sample・`worldTicksAtEnd=206`）＝ whole-queue idle の除去で **p50 −67%・p95 −82%**。
  after leg の slow frame  snapshot では `bootstrapQueueWaits=0` のまま per-submission fence 待ちが **233 wait / 48 ms 累積 / worst 4 ms**。
  **この 48 ms が後半で消せる上限**（cold p50 70.6 ms/frame に対して ~1 ms/frame 相当）。
- **計器の穴（本 round で修復・`ValidationClient.java`）**: (1) `SLOW_FRAME_MAX=12` が**先着順**だったため user leg の 12 entry は
  全て tick 0..54 の startup frame で、受入が読む cold 窓には phase 行が **1つも無かった** → **遅い順 12 件**の保持へ
  （`slowFrameRetention` が意味を明示）。(2) phase 行の探索が `"handoff"` を含む最初の行を取るため、GL-less では
  `GllessFrameTarget` の **`glless target: no handoff …`** に一致し、**時間を含まない行**が「phase」として入っていた
  （この leg に `handoff: avg` 行は **0 件**＝GL-less に handoff は無い）。timing 行は `handoff: avg`/`handoff detail` の
  接頭辞でのみ採用し、無ければ診断列の先頭 **700 字**（`SLOW_FRAME_PHASE_MAX_CHARS`）を残す。各 entry には
  `upload[thisFrameMs=… submitWaitMillis=… submitWaits=… queueWaitMillis=…]` を付ける。
- **窓別 upload 待ち（新 counter・次の leg で before を取る）**: 毎 render tick に `VulkanContext` の
  `bootstrapSubmitWaitMillis`/`bootstrapSubmitWaits`/`bootstrapQueueWaitMillis` を反射で差分し、
  `uploadSubmitWaitMillisColdWindow`/`uploadSubmitWaitsColdWindow`/`uploadSubmitWaitFramesColdWindow`/
  `uploadSubmitWaitWorstFrameMillisColdWindow`（同 `…WarmWindow`）＋ `uploadSubmitWaitsAtEnd`/`uploadSubmitWaitMillisAtEnd`/
  `uploadQueueWaitMillisAtEnd` を report へ。出所は `uploadWaitSource`、窓は `uploadWaitWindows`、**-1 は「クラス/フィールド欠如」で
  0 ではない**。frame 数（`…Frames`）を併記するのは、sample 数の無い合計は率にならないため。
- **判定: ring は作らない。** 測った範囲では最悪 frame の phase は upload 経路でなく、消せる上限は cold p50 の ~1%。
  **未確立**: cold 窓の帰属。穴 (1) のため tick 120..200 の submit 待ちは**一度も測られていない**（`VulkanArena.write` は
  1 write = 1 submit+fence（`VulkanBuffer.write`→`VulkanContext.uploadToBuffer`）で、arena census の `totalWrites` は
  leg 内で最大 **537**、その大半は tick 54 以降に起きている）。次の leg の `uploadSubmitWaitMillisColdWindow` が
  数十 ms/窓なら BH-27 後半を閉じ、秒単位なら ring ではなく「frame 内 1 submit」へ進む。**leg は未実施**（game slot を他担当が保持・
  `VulkanTerrainGpuBackend.java` を同時編集中）。leg の作り方は `chain-t44e.sh`（mod 半分は t44d と同一 hash・fixture のみ新・
  `-Dceltium.validation.minWorldFrames=5` で起動ゲートに殺されないようにする・`T44_DURATION=400`）。
- **未確立（その2）**: `lead-warm3`（jar `c6e7a8058c3fd511`・19:53・同 shape）は **warm 275 sample で p50 24.0 / p95 105.6 / p99 207.4 ms**、
  cold は 40 sample で p50 **44.1 ms** だった。現行 jar の cold p50 **151.4 ms** との差は **jar 差か leg flag 差（`captureCondition=sections`・
  `captureDeadlineTick=4000`・resize 無し）か**を分離していない＝**回帰と主張しない**。分離実験は同じ jar で flag を替えた 2 leg。

## 0.369 **round 176: パノラマを両GPUで受入。ロード画面の dump が出ない根因は「最初の present 前は swapchain が無い」こと**

- **パノラマ両GPU受入**（完成条件 (d) の当該項目）:
  - **AMD RX 6400**: leg `r174-panorama-menu-dump`・jar `31931ee858a43817`・menu 画像 sha256 `090d98f7dd60be62…`・**21,962色**・白1.2%・中心 `(111,115,133)`・PASSED/`validationClean=true`/`validationMessageCount=0`。
  - **Intel UHD 630**: leg `r162-intel-menu`・jar `8fb40ed4ae9cd8400dc89242`・`VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json`・`--render-node pci-0000:00:02.0-render`・`--require-gpu Intel`・**device `Intel(R) UHD Graphics 630 (CFL GT2)`**・menu 画像 **22,073色**・白1.2%・中心 `(112,116,131)`・左上 `(206,217,249)` / AMD `(207,218,249)`・PASSED/clean/0・exit 0。
  - **2つの jar は同一ではない**: Intel leg は loading フックを `@At("HEAD")` にした実験版（fixture 側のみの差・描画に影響なし）。この差は下の項目の測定のためで、**描画内容の比較は同一形状**であることを色数と中心色が示す。**厳密な同一 jar での両GPU比較は未取得**として残す。
- **ロード画面の presented dump が出ない根因（確定）**: `celtium$dumpPresentedImage`（`VulkanTerrainGpuBackend.java:11298-11305`）は
  ```java
  if (this.swapchain == null) { return false; }
  ```
  で拒否し、`this.swapchain` は present 経路（`:9930` の `VulkanSwapchain.create`）で**遅延生成**される。`drawSplashScreen` の **HEAD ではこの run の present がまだ0回**（`pictureRequested_loading` が `after 0 presented frames` と記録）なので swapchain が無く、要求は拒否されて
  **PNG が1枚も書かれない**（leg `r162-intel-menu` の `screenshots/` に loading のファイルが無い。`menu`/`early`/`world` はある）。
  一方 **`drawSplashScreen` の最後の `this.updateDisplay()` が、この run の最初の present** である。したがって **splash のフレームを撮るには「最初の present より前に要求が入り、その最初の present で書かれる」しかない**。
  **修正**: 要求を拒否せず**保留**し、`VulkanSwapchain.create` の直後に渡す（`return true` で「受理した」と正直に答える）。blend-state へ **task-63 の直後の別 unit** として渡した。
- **測定契約の欠陥（記録）**: fixture の完了検査は `observed == presentedFramesAtRequest + 1`（`ValidationClient.java:5902`）で、
  **「要求から present が1回あった」しか検証せず、その1回が splash かどうかを見ていない**。`@At("RETURN")` 版は splash の `updateDisplay()` の**後**に要求するので、書かれるのは**次の（メニューの）フレーム**だった。
  実測: loading と menu の画像は distinct 21,955 / 21,962・中心色一致・白率一致（パノラマはゆっくり回るのでフレームが違えばバイトは違うが統計は一致）。
  **したがって「ロード画面が白い」は「メニューの白いパノラマ」を測っていた**（測定アーティファクト）。`stage=loading` はロード画面の判定に使えない。
- **このroundの着地（私）**: `46854aa95` — `VulkanTextureRegistry.forgetTexture` が `mirrorSourceById` を消す（GL名は再利用されるので、ラベルを残すと `splashSourcedGroups` が次のテクスチャを前の owner の source として数える）。
- **このroundの割当**: gui-stage → BH-09 受入（scissor が live な scene）、perf-ring → Goal B（通常 frame の支配コストを計器で名指しし1つ消す。出発点の実測を渡した）、acceptance → 上の受入の独立検証と `docs/vanilla-acceptance.md` の更新。
## 0.368 **round 175: パノラマがユーザ可視で成立（メニュー画像を実測）。BH-18 / BH-35 完了、Y反転の受入**

- **パノラマ受入（ユーザ優先順1位）**: leg `r174-panorama-menu-dump`（jar `31931ee858a43817e29b5b76`・fixture `9eab548f9c786592`・AMD RX 6400・`menuHoldTicks=60`・`dumpPresentedMenuTick=30`・
  `validationClean=true`・`validationMessageCount=0`・exit 0）。**menu stage の presented 画像**は
  `.celtium-work/runtime/r174-panorama-menu-dump/screenshots/celtium-presented-menu-run-1a05b3536bf4e-66d3c617-kept.png`
  （sha256 `090d98f7dd60be62f0e9c1162febc14f8e3e3bcdceb22e08608c1a5886cfcf88`・1280x720・**21,962 distinct colours**・白 1.2%）。
  **内容**: ブラー付きのワールドパノラマ背景（空・水面・大地）＋ Minecraft ロゴ＋"Java Edition"＋splash テキスト＋
  Singleplayer/Multiplayer/Mods/Options…/Quit Game のボタン＋左下の `Minecraft 1.12.2 / MCP 9.42 / Cleanroom 0.6.13-alpha / 17 mods loaded, 17 mods active`＋
  著作権行。**白プレートは無い。**
- **その計器側の根拠（同一 leg）**: `CELTIUM_TEX_COPY_SERVED dest=21 256x256 readFbo=0 mean=106,119,135 opaque=65536/65536 nonWhite=65536`。
  旧 jar `3d6ca14b` は `mean=255,255,255 opaque=0/65536 nonWhite=0`。**宛先ミラーが空のクリア面から実パノラマ画素に変わった**。
- **2つの修正が効いた**: (1) `bbe0bbe71` read解決の `default-pair` 対称分岐（`readImage == lastDrawnImage == passAttached`）、
  (2) `aea5fec0c` **コピーの Y 反転**（GL の copy y は下端原点・Vulkan の `imageOffset` は上端原点。`imageOffset.y = targetHeight - y - height`＋`celtium$reverseRows`）。
  併せて**矩形が画像外なら名前付きで拒否**（黙って clamp しない）。
- **測定上の注意（次に読む人へ）**: `stage=loading` の dump は `presentedFrame=2` で取られるが、その画像は**既にメニュー**（loading と menu の distinct は 21,955 / 21,962、サンプル平均も一致）。
  つまり **loading stage の dump は splash を捉えていない**。ロード画面の受入をこの stage で取るのは誤りで、splash を捉えるには `drawSplashScreen` RETURN から**次の present より前**に読む必要がある。
- **BH-35 完了（`4fb0b581b`）**: 広告を **`GL_VERSION="3.0.0 Celtium"` / `GLSL="1.20"` / `EXTENSIONS=""`** に。下限は**列挙**で決定（vanilla の `framebufferSupported` が `OpenGL30` を要求し、
  3.0 未満だと `shadersSupported=false` で outline/spectator/post が落ちる。GLSL は別決めで vanilla の `#version 120`）。AMD/Intel 同一 jar で advertised 一致・
  `validationMessageCount=0`・**未 backing 到達集合は両GPUで完全一致（distinct 647）**・**描画経路の名前ゼロ**・200件の call site は全て LWJGL の自己テスト。
  到達 legacy 名を no-op に移して数を減らす処置は**していない**（裁定どおり）。限界も記載: この cohort では消えたのは「4.6 の機能がある」という**主張**の側。
- **BH-18 完了（`0a8b8d253`）**: 単一 `VK_FORMAT_UNDEFINED` 採用の穴を閉じ、選択を純関数 `SwapchainCreateArguments.select(...)` に切り出して
  **本番 `createSwapchain` がそれを呼ぶ**形にし、**`python3 scripts/cpu-contract/swapchain_create_args_check.py`（GPU不要・1コマンド）で全条件 PASS**。
  実 surface は clamp 分岐に到達せず、**recreate 経路は依然未測定**（分岐の保証は harness 側）。
## 0.367 **round 174: `VulkanTerrainGpuBackend` の分割に着手 — 直列契約と継ぎ目（ユーザ指示）**

- **ユーザ指示**: 「**絶対 vulkanterraingpubackend を分割してみんなで編集したほうがいい**」。現状は **14,018行・334トップレベルメソッド・957フィールド**で、
  **複数担当が同じファイルを触ると必ず衝突する**（texture-mirror・sky-draws・perf-ring・frame-composite・Lead が全部この1ファイルを必要とする）。これが現在の最大のボトルネック。
- **直列契約**: `VulkanTerrainGpuBackend.java` を編集してよいのは**同時に1人だけ**。ボードで **task-63 → task-64 → task-65** の `blocked_by` として符号化した。
  他担当が必要とする変更は**保持者宛の要求**として送る。
- **継ぎ目**（振る舞いを変えず、1継ぎ目=1 commit=1 leg で「抽出前と同一」を確認する）:

| # | 新クラス | 内容 | 担当 | 状態 |
|---|---|---|---|---|
| 63 | `TextureCopyService` | コピー/readback/read解決（`celtium$serveTextureCopy`・`celtium$readImageRectangle`・`celtium$ownedReadImage`・`celtium$countNonWhite`・`celtium$reverseRows` と `textureCopies*`） | blend-state | 割当済（Lead の Y 反転着地後に開始） |
| 64 | `PublishedFrameTextures` | `celtium$publishFrameTexture`・`celtium$transitionPublishedFrameTextures`・`celtium$pruneStalePublishedNames` と `publishedFrameTextures*` | sky-draws | task-63 待ち |
| 65 | `BackendReport` | `verificationSummary()` と `*Summary` ヘルパ群・`describe()`・`record()`（**読み取り専用**の抽出。カウンタの書き込み側は backend に残す） | glyph-fix | task-64 待ち |

- **受入の形（分割の性質に合わせる）**: 抽出は振る舞いを変えないので、受入は「**同一 leg・同一 flags で、対象カウンタと `CELTIUM_VERIFY` の文字列が完全一致**」。
  task-65 は `CELTIUM_VERIFY` 行の文字列一致を機械的に取る（既存 leg `r172b-panorama-menu` の行がベースライン）。**1文字でも違えば抽出ミス**。
- **抽出後に担当が所有するファイル**が決まる: task-63 後は blend-state が `TextureCopyService.java`、task-64 後は sky-draws が `PublishedFrameTextures.java`、task-65 後は glyph-fix が `BackendReport.java`。**backend 本体は Lead が統合に残す**。
- **進行の記録**: texture-mirror は task-63 を**着地させずに返した**（context 枯渇。backend は未編集のまま）→ blend-state へ再割当。sky-draws は task-64 の**読み取り調査のみ**実施（backend 未編集・指示どおり）。
- **このroundの副産物（sky-draws の2脚比較）**: `no-entry` の 1280×720 グループ（`groupTexture=6`）は **content が出た脚でも消えていない**。
  publish カウンタも両脚でほぼ同一（`created=2 republished=0 stalePruned=0 unpublished=0`、`skippedForAttach` だけ 41→33）。
  したがって**ロード画像に content が出た原因は publish 経路では説明できない**＝帰属は同一 jar での A/B が必要（`a6e8b2dfb` の `CeltiumGlStub` が候補）。
## 0.366 **round 173: パノラマのコピーが「別の画像」と「別の行」を読んでいた — read解決の非対称と Y 反転**

- **実測（leg `r172b-panorama-menu`・jar `3d6ca14b0e76aebc`・fixture `3a652903b6edd9d1`・AMD RX 6400・`menuHoldTicks=60`）**: `fixtureStatus=PASSED`・`validationClean=true`・`validationMessageCount=0`・exit 0。
- **旧 leg（`splashdrop3`・HEAD `5a8ee96af`）の白の説明がついた**: コピー行は `readImage=chainPair` だが `passAttached=defaultPair` で食い違っていた。
  `CELTIUM_DRAW_SITE #1 frame=0 target=565929989 passImage=<chainPair> vertices=8 indices=12 batches=2` ＝**splash 自身の2 quad が chain pair に行き**、
  `lastDrawnOffTargetIdentity`（`:5744` の **accept 時**の代入）がその splash FBO の identity を指していたため、read 解決（`:11677-11692`）が
  **splash の白キャンバス（chain pair）を読んでいた**。`opaque=65536/65536` はその白。
- **修正**: `routeLabelForBinding` の結果に対する分岐が **`chain-pair` 側にしか無かった**。対称の **`default-pair` 分岐**を足し、binding 自身の route で決めるようにした
  （identity 0 = default pair、それ以外の非 main = chain pair。`FramebufferMixin:157` が `unbindFramebuffer` で `bindFramebuffer(0L,false)` を報告するので identity 0 が default framebuffer）。
  結果: **`readImage == lastDrawnImage == passAttached`** に一致（`readResolvedFromLastDrawnTarget=294`）。
- **残る欠陥（これがパノラマの白の本体）**: `celtium$readImageRectangle`（`:11749`）が `copy.get(0).imageOffset().set(x, y, 0)`（`:11799`）と
  **GL の y を Vulkan の imageOffset にそのまま渡している**。GL の `glCopyTexSubImage2D` の y は**フレームバッファ下端原点**、Vulkan の imageOffset は**上端原点**なので、
  `y=0` の読みは**上 256 行**（＝クリア色。`mean=255,255,255 opaque=0/65536 nonWhite=0`）になる。panorama の faces は `viewport(0,0,256,256)`＝GL の**下** 256 行に描かれている。
  正しい変換は「読む行を `[H-y-height .. H-1-y]` にし、ミラーの行順を反転して書く」（GL は framebuffer の下から `yoffset` 行目へ書くため）。
- **ロード画面（同時に測れた）**: 同じ leg の `stage=loading`（`presentedFrame=2`）は **405 distinct colours**（中心256×256で189色、`(111,111,111)`/`(0,0,0)`/`(126,136,191)` を含む）。
  直前 leg（jar `3aaeec13`）の同じ stage は **distinct=1 の全面白**（私が PNG を復号して中心260×260まで単色と確認）。**内容が出るようになった**が、
  間に入った landing が複数あるため**1変更への帰属は未分離**（`a6e8b2dfb` の `CeltiumGlStub` 変更が候補）。判定子は「白か」ではなく「**ロゴが居るか**」。
- **`refused=4` は欠落ではないと確定**: 4件すべて `RenderGlobal.func_174976_a:1375`（sky）で、`calls=4` 以降 `refused=4` のまま固定＝**最初のフレームで投影が未 seed のときの拒否**。拒否は正しい選択で、以後 `drawn` は伸びる。
- **ユーザ指示（このround）**: 「絶対 vulkanterraingpubackend を分割してみんなで編集したほうがいい」→ **14,018行の単一ファイルが並列編集のボトルネック**なので分割に入る（§0.367 以降）。
## 0.365 **round 172: 成果物バージョンの罠（stale jar）を根因から除去。BH-18 は未受入と判定し直し、パノラマの破棄が実測で走ることを確認**

- **復帰**: goal は `disarmed` だった（revision 30）。**objective を変更せず** `resume` して `armed`（revision 31）。未 push の2 commit（`5a8ee96af` BH-20・`59441d6bb` パノラマ）を `origin/main` へ push。
- **旧名 jar の罠（このroundの主眼）**: rebrand（`b4d3f5279`）後も `build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/` と
  `forge122/build/libs/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar` が残っていた。**旧名は `0.0.2` より後ろにソートされる**ので、
  ディレクトリを glob して末尾を採る／旧パスを直書きする補助スクリプトは**古い jar を選ぶ**。
  - **実害（証拠）**: `loading-leg`（14:20 起動）の `celtium-loading.jar` は sha256 `283a4a5a…` で、**旧 `celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar` と完全一致**。
    `javap` で **`celtium$dumpLoadingFrame` が存在しない**ことを確認＝その脚は hook を積んでいない。**「hook が発火しない」という読みは無効**。
  - **対策**: `scripts/benchmark_celtium.py` の `CELTIUM_JAR` 直書き（`:91`）をやめ、`resolve_celtium_jar()` が build 出力を**名前族と mtime**で解決し、
    **採用jar・他候補・無視した旧名/中間物を全部印字**する。旧 build 出力（6ファイル）を削除。`docs/runtime-validation.md`・`docs/handoff-2026-10-10-next-model.md`・
    `docs/handoff-vanilla-completion.md` のレシピを現行パスへ訂正し、**「レシピに版を直書きしない・lock JSON の `mods[].sha256` を報告に載せる」**を明記。
  - **sky-draws も独立に同じ罠を検出**（自分の leg スクリプトを修正済み・以降の測定は jar `3aaeec1334eac580`）。
- **パノラマ: 「破棄が走るか」は決着**: `59441d6bb` の計器を持つ**現行ツリーの leg**（`bh18b-amd`・`bh18b-intel`、前者は `git.head=1fd58540b` 相当の dirty ツリー）で
  **`splashCanvasInvalidations=1` / `splashSourcedGroups=1`** を実測。**破棄は実際に走っている**。一方 `splashSourcedGroups=239` を出していた `splashdrop*` の3脚は
  HEAD `5a8ee96af`＝**この計器より前の jar**なので、239 は旧定義の値。**menu-hold の判定 leg はまだ無い**（次: 現行 jar で menu-hold 1脚）。
- **ロード画面: 順序と到達性が確定**（sky-draws）: `Minecraft.startGame` 内で preInit=`beginMinecraftLoading`(**`:514`**) → `SplashProgress.drawVanillaScreen`(**`:517`**) →
  init=`finishMinecraftLoading`(**`:588`**)＝**init での latch は splash より後**。fixture の latch をコンストラクタへ移し、1脚で
  `CELTIUM_SPLASH_HOOK drawSplashScreen=true` ＋ `fixture instanceSet=true alreadyRequested=false dumpEnabled=true` を確認（**mixin は適用され、fixture にも届く**）。
  取得できた `loading` stage の presented dump は **1280x720・全白 (255,255,255)・distinct=1**＝**ロード画面の幾何が presented frame に届いていない**。次は `passImage=`/`offTargetPerTarget` で描画先を追う。
- **BH-18 は「受入済み」ではない**（裁定）: perf-ring の leg は実 surface で **AMD/Intel とも提示値＝要求値・`validationMessageCount=0`** だが、BH-18 本文の
  「修正後の確認」は **`min=max=2` / `max=0` / OPAQUE 無し / TRANSFER_DST 無し / 単一 `VK_FORMAT_UNDEFINED` という合成入力**での契約確認を要求している。
  実 surface はどれも分岐を踏まない（extent 指定・maxImageCount unlimited）ので**未測定**。加えて**実装に穴が1つ残っている**:
  `VulkanSwapchain.java:1195-1203` は「B8G8R8A8/R8G8B8A8 が無い」場合に `formats.get(0).format()` を採るため、**surface が `VK_FORMAT_UNDEFINED` 1件だけを返すと
  `imageFormat=VK_FORMAT_UNDEFINED` で作成に進む**（不正）。perf-ring へ「UNDEFINED を採用しない＋合成入力 harness（GPU不要・実装本体の選択関数を駆動）」として差し戻した。
- **BH-35 の契約を裁定**: goal 本文が「汎用 GL→Vulkan 変換器・GL 全体のエミュレーションを作らない」と明記しているので、**647 名を no-op で吸収しない**という perf-ring の停止判断は正しい。
  契約は「**ゲーム本体と現在の mod cohort にある GL 版/拡張ゲートを列挙し、その全てを通す最小値を広告する**」。`GL_VERSION` と `GLSL` は別々に決め、
  拡張は解釈するものだけ名乗る。no-op に入れてよいのは「状態を持たず pixels を変えない legacy 呼び出し」だけで、**描画経路に乗る名前は吸収しない**。
- **確定した「バグではない」もの**: `CELTIUM_DRAWARRAYS refused=4` は**全て `RenderGlobal.func_174976_a:1375`（sky）**で、`calls=4` 以降 `refused=4` のまま固定＝**最初のフレームで投影が未 seed のときの拒否**。
  拒否は正しい選択（identity 投影で描かない）で、以後 `drawn` は伸びる。count を欠落と読まない。
- **このroundの他**: `build-1122/buildSrc/.kotlin/` を `.gitignore` に追加（未追跡のビルドキャッシュが commit 対象に見えていた）。

## 0.364 **round 171: レジストリの名前キー・キャッシュに穴 — `forgetTexture` が `mirrorSourceById` を消していない（Lead の発見・texture-mirror へ配布）**

- **発見（コードを読んで）**: `VulkanTextureRegistry.forgetTexture`（`:1413`）は `entries.remove(id)` と `fullyInitialized.remove(id)` を行い wrapper も退役させるが、
  **`mirrorSourceById`（`:157` の `Map<Integer,String>`・`:1268` で書込み・`:1340`/`:1350` で読む）を消していない**。
- **なぜ効くか**: **OpenGL は削除された名前を再利用可能にする**（"After a texture is deleted, it has no contents or dimensionality,
  and its name is free for reuse"）＝**次の `glGenTextures` が同じ名前を別内容に渡しうる**。ラベルが残ると、
  **新しい texture のサンプルが古い source に帰属される**。
- **パノラマへの直接の影響**: **`splashSourcedGroups`（修正の効果を読む計器）は `announced-int[]` ラベルを数える**ので、
  ラベルが delete-and-reuse を跨いで生き残ると**計器が誤った texture を指す**。texture-mirror の測定
  （`forgetTexture id=8 hadEntry=true` は発火・`announceImage` は来ない・mirror は `int[]` で再充填）と整合する。
- **依頼（texture-mirror へ送信済み）**: `forgetTexture` で `mirrorSourceById` を消す＋**GL 名でキーされた他の map/cache が同じ経路で消えているか**を確認
  （`entries` と `fullyInitialized` が消えているのでそれが手本）。**ラベルを消して `splashSourcedGroups` が変われば、それは計器についての知見**として記録。
- **このラウンドの他**: ローカルと origin は同期（0/0）・他担当の landings はまだ無し（pipeline 3ファイルが dirty ＝ 作業中）。

## 0.363 **round 170: PR#1・PR#2 をマージし 0.0.2-alpha を公開。ユーザ指示でパノラマ・ロード画面・残BHを最優先化**

- **PR#1 マージ（`c2595aa9f`）**: "perf: remove render-thread GPU drains"。**GPU待ち0** の成果＝
  `frameIntervalP50/P95/P99` **18.6/28.0/333 → 15.7/22.5/326 ms**・**errors 0・VUID 0**・**`celtium-validation.png` が baseline と完全一致**。
  **CI の失敗はインフラ**（runner に存在しない `.celtium-work/...` を実行しようとしていた）でコードの失敗ではない。
  **副作用として判明**: **bridge 経路（`glless=false`）は本機で最初の world frame に確定的に deadlock**（baseline 3 leg）。
- **PR#2 マージ（`d1f0042b4`）**: "perf: merge GUI-class immediate batches across matrix pushes"。
  `copyVerticesTransformed` が **軸並行 modelView をコピー中に適用**し、**頂点shaderと bit 一致**する（off-axis が厳密に0・normal を持たない format のみ）。
  **回転は matrix-keyed 経路に残す**という明示的な拒否条件つき。＝**`gui` self 5.36 s に対する直接の手**。
- **0.0.2-alpha 公開**: `v0.0.2-alpha`・`celtium-forge-mc1.12.2-0.0.2-alpha.jar`・**2,594,729 bytes**・
  SHA-256 `c2b69d0319073b6f06f38c2868a71b3ce363d4a9fb0054b7e5162f40b94471fc`・**prerelease**・
  **package verifier `verified: true` / `errors: []`**。**ユーザの instance に導入済み**（sha256 一致を確認）。
  **リリース jar の受入 leg**: **flag なしで `CELTIUM_GLLESS stub GL provider installed`・`window hinted GLFW_NO_API`**・`worldTicksAtEnd=241` ＝
  **既定 GL-less が公開 jar で成立**。
- **開発版のリブランド**: `2.5.12-celtium.0.1.0-dev.1` → **`0.0.2-alpha-dev`**（`2.5.12` は Celeritas 上流の番号で、fork が他人のリリースのパッチを名乗っていた）。
- **ユーザ指示による最優先（本ラウンドで担当へ配布済み）**:
  | 対象 | 担当 | 与えた内容 |
  |---|---|---|
  | **パノラマ** | **fog-path** | 所有者は確定（**id 8 の mirror＝splash の白 `DynamicTexture`・invalidate されない**）。修正と `splashSourcedGroups`→0 の受入 |
  | **ロード画面** | **sky-draws** | GL-less では `SplashProgress.start()` が cancel され **3サイトは実行されない**・走るのは Tessellator 経路。**必要なのは splash 段階の dump 契約**（既存 switch はどれも届かない） |
  | **BH-20/21/24** | **proj-channel** | BH-20 は契約が既存（census）・BH-21 は分類が無いので先に census・BH-24 は BH-20 の transaction の別面 |
  | **BH-14/17/26** | **frame-composite** | **欠けている契約を名指し**（target identity key・GUI の実測・recording token の pending/confirmed） |
  | **BH-18/35** | **pipelines** | surface capabilities からの導出・広告と backed の一致 |
- **共通の注意として各担当へ渡したこと**: **カウンタを読む前に `CELTIUM_VERIFY` 行の有無を確認**（trigger に届かない leg は「0」ではなく「不在」）・
  **`glless=false` は deadlock するので比較は GL-less 既定で取る**。

## 0.362 **round 169: scissor 発行の条件が確定 — `ScrollableFrame` が「スクロール可能な時だけ」clip する**

- **原典で確定した連鎖**:
  1. **`GL_SCISSOR_TEST`/`glScissor` を呼ぶ唯一の場所は `VintageDrawContext.enableScissor`**（`forge122/.../gui/VintageDrawContext.java:143-150`）。
  2. **その呼び手は1つだけ**: **`ScrollableFrame.render`**（`common/.../gui/frame/ScrollableFrame.java:126`）。
  3. **`ScrollableFrame` は Celeritas 画面で実際に使われている**: `Tab.from(page, …)` がページの frame を `ScrollableFrame` にし、
     `TabFrame:146` が `sidebarFrame = ScrollableFrame.createBuilder(…)` を作る。
  4. **しかし `enableScissor` は条件付き**: `if (this.canScrollHorizontal || this.canScrollVertical)` の内側だけ。
     `canScrollVertical` は **frame の内容が viewport より高い時**に立つ（`:44-57`）。**else 側は clip せずに `super.render`**。
- **帰結（`scissorCalls=0` の説明）**: **Celeritas 画面は開いているが、その内容が viewport に収まっている**ため
  `canScrollVertical=false` → **`enableScissor` が呼ばれない**。＝**scene を「溢れさせる」必要がある**。
- **次の一手（具体的・1変数）**: **内容が溢れるように scene を作る** ＝ **guiScale を上げる**（較正は `guiScale=3`）か **window を小さくする**。
  その後 **`scissorCalls` を最初に読み**、非0 なら `lineBatchesNarrowed` → `scissorCapturedGroups`/`scissorLateCapturedGroups` → `-Dceltium.vulkan.groupScissor=false` 対照。
- **注意（記録）**: 私の `resizeTo=854x480`＋`resizeAtTick=60`＋`resizeViaCompositor=true` の leg（`r159-bh09`）は
  **`glfwWindowSizeAtEnd=854x480` で resize は成立**したが **`CELTIUM_VERIFY` が出ずカウンタ未取得**
  ＝**resize 脚では summary trigger に到達していない**（別の trigger が必要かもしれない）。**この leg は測定として使えない**。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地・計器4 trigger 着地・fixture knob 着地・
  「vanilla は scissor を使わない」確定・「scissor は ScrollableFrame が溢れた時だけ発行」確定**・
  **受入は「溢れる scene」の leg 待ち**。**9 leg を費やしたが、各 leg は計器か原典が理由を示した**。

## 0.361 **round 168: 原典が決着 — vanilla は scissor を一切使わない。scissor の発行元は Celeritas 自身の UI**

- **原典の検索（決定的）**: `forge122/build/rfg/minecraft-src/java/net/minecraft/` 全体で **`GL_SCISSOR_TEST` の出現 0・`glScissor` の出現 0**。
  ＝**vanilla 1.12.2 の GUI は scissor を一切使わない**。Forge javadoc の "GL_SCISSOR is enabled for anything that is rendered outside of the view box" は
  **Forge の `GuiScrollingList`／mod 側の作法**を指すもので、**vanilla の `GuiSlot` は scissor ではなく「描かない」ことで clip する**。
- **では誰が発行するか**: **Celeritas 自身の UI**。stub のコメント（`CeltiumGlStub.java:218`）が既に名指ししている
  ＝ *"BH-09: Celeritas' own UI clips with GL11.glScissor directly (VintageDrawContext.enableScissor)"*。
- **帰結（これまでの7 leg が0だった理由が説明できる）**: **言語画面・リソースパック画面は原理的に scissor を発行しない**
  （vanilla が使わないのだから `scissorCalls=0` は当然）。**scene の選択自体が誤っていた**。
  ＝**`scene=videoSettings`（Celeritas 画面）が正しい対象**で、**そのリストをスクロールさせた時**に `VintageDrawContext.enableScissor` が呼ばれる。
- **次の一手（具体的・これで決まる）**: **Celeritas 画面のオプションリストをスクロールさせる**。
  `CeleritasVideoOptionsController` が `optionPageScrollBarOffset`/`tabFrameScrollBarOffset` を持つ＝**自前のスクロール状態**なので、
  `GuiSlot` ではなく**その画面自身のスクロール経路**を叩く（`mouseClickMove` 相当 or ホイール入力を画面へ渡す）。
  **`scissorCalls` を先に読み**、非0 を確認してから `lineBatchesNarrowed` → 捕捉カウンタ → 一旗対照。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地・計器4 trigger 着地・fixture knob 着地・
  「vanilla は scissor を使わない」を原典で確定**・**受入は Celeritas 画面をスクロールさせる fixture 待ち**。
  **8 leg を費やしたが、最後の2 leg で「対象 scene の選択が誤っていた」ことを測定と原典の両方で示せた**。

## 0.360 **round 167: リソースパック画面でも `scissorCalls=0` — 原因は「リストがスクロールしない」ことと確定**

- **測定（`r158-bh09`・`-Dceltium.validation.screen=resourcepacks`）**: **`namedScreenOpened = net.minecraft.client.gui.GuiScreenResourcePacks`** で
  **`scissorCalls=0`・`scissorToggles=0`・`lineBatchesNarrowed=0`・`scissorCapturedGroups=0`・`scissorLateCapturedGroups=0`**。
  ＝**2つ目の `GuiSlot` 画面でも一度も clip しない**。
- **確定した原因（測定の積み上げ）**: vanilla の `GuiSlot.drawScreen` は **リストがスクロール可能なとき**（内容高 > view 高）に
  `GL_SCISSOR_TEST` を enable して `glScissor` を発行する。**この instance のリソースパックは少なく、言語も少ない**ため
  **どちらのリストもスクロールせず、scissor が一度も発行されない**。
  ＝**`scissorCalls=0` は「scene が clip しない」ことの直接の証拠**であり、**renderer の欠陥ではない**。
- **次の一手（これで決まる・具体的）**: **fixture が開いた画面のリストをスクロールさせる**（`GuiSlot` にホイール入力を送る／
  `handleMouseInput` 相当を叩く）。＝**スクロール可能になれば `GuiSlot` が clip し、`scissorCalls > 0` になる**。
  既存の `screen=` knob の隣に「開いた後 N tick でスクロール」を足すのが最小（新しい機構を作らない）。
  **その leg で `scissorCalls` を先に読み**、非0 を確認してから `lineBatchesNarrowed` → 捕捉カウンタ → 一旗対照。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地・計器4 trigger 着地・fixture knob 着地・
  「scene が clip しない」ことを2画面で測定**・**受入はスクロールさせる fixture 追加待ち**。
  **7 leg を費やしたが、各 leg は計器が自分で理由を示した**（経路未 exercise / trigger 未到達 / scene が clip しない）。

## 0.359 **round 166: 決定的 — `scissorCalls=0`・`scissorToggles=0`（どの scene も clip していない＝scene 側のギャップ）**

- **計器の追加（Lead・`bd0852e78`）**: stub が **`glScissor` の呼び出し回数**と **`GL_SCISSOR_TEST` の enable/disable 回数**を数え、
  static accessor（`scissorCallCount()`/`scissorToggleCount()`）と **verification summary の `scissorCalls=`/`scissorToggles=`** に出す。
  ＝**`lineBatchesNarrowed` では区別できなかった「scene が clip していない」と「捕捉が欠陥」を分ける数**。
- **測定（`r157-bh09`・`-Dceltium.validation.screen=language`）**: `CELTIUM_VERIFY via=present 120`（present trigger も動作）で
  **`scissorCalls=0`・`scissorToggles=0`・`lineBatchesNarrowed=0`・`scissorCapturedGroups=0`・`scissorLateCapturedGroups=0`**。
  ＝**言語画面は `glScissor` を一度も呼ばず、`GL_SCISSOR_TEST` も一度も切り替えていない**。
- **結論（測定に基づく・推測ではない）**: **解釈1が正しい** ＝ **この scene は clip しない**（`GuiLanguage` のリストが短く、
  `GuiSlot.drawScreen` が view box 外を描かないため scissor が発行されない）。**renderer の欠陥ではなく scene のギャップ**。
  ＝**BH-09 の `scissorCapturedGroups > 0` は「実際に clip する scene」が無い限り原理的に取れない**。
- **次の一手（具体的）**: **スクロールする長いリスト**を持つ画面を開く。候補: **リソースパック画面**（`GuiResourcePackList extends GuiListExtended extends GuiSlot`）、
  **クリエイティブインベントリ**（タブ多数）、**ワールド選択**（多数のワールド）。fixture の `screen=` knob は既に `resourcepacks` を実装済み
  ＝**次はそれをそのまま打ち、`scissorCalls` を先に読む**（0 なら別の画面へ、非0 なら `lineBatchesNarrowed` → 捕捉カウンタ → 一旗対照）。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地・計器4 trigger 着地・fixture knob 着地・
  scene ギャップを測定で名指し**・**受入は clip する scene の発見待ち**。**6 leg を費やしたが、各 leg は計器が自分で理由を示した**
  （経路未 exercise / trigger 未到達 / scene が clip しない）。

## 0.358 **round 165: fixture knob は効いた（`namedScreenOpened=GuiLanguage` at tick 31）— ただし画面が開くと frame カウンタが止まり summary が出ない**

- **測定（`r155-bh09`・`-Dceltium.validation.screen=language`）**: report に **`namedScreenOpened = net.minecraft.client.gui.GuiLanguage`・`namedScreenOpenedAtTick = 31`**
  ＝**新しい knob は設計どおり動いた**（`GuiSlot` 派生＝vanilla 自身の scissor 使用者を開けた）。
- **しかし `CELTIUM_VERIFY` は 0 行**（leg は 33.8 s・`endedBy=process-exit`）。frame 120 の trigger に到達していない。
  ＝**画面が開くと `frameId` が進まない**（GUI 画面が上がると world render 経路が走らない）という説明が最も整合する。
  ＝**「画面を開く leg」では frame trigger が使えない**という計器側の制約が判明。
- **次の一手（小さく明確）**: summary の trigger を**画面が開いていても進む経路**に足す（例: present 経路・GUI 描画経路・client tick 経路のいずれか）。
  ＝**その後 `-Dceltium.validation.screen=language` の leg で `lineBatchesNarrowed` を先に読む**（>0 なら経路を踏んだ証拠）→
  `scissorCapturedGroups`/`scissorLateCapturedGroups` → 同一 jar の `-Dceltium.vulkan.groupScissor=false` 一旗対照。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地・計器3 trigger 着地・fixture knob 着地（`770620c0c`）・
  受入は summary の trigger を「画面が開いた状態でも進む経路」に足すまで待ち**。**5 leg を費やした**が、
  各 leg は「経路未 exercise」または「trigger 未到達」と計器側が自分で示している。

## 0.357 **round 164: 履歴からユーザの実画面スクリーンショットを削除（`git filter-repo`・force-push 済み）**

- **ユーザの指示**: `docs/history/evidence/compositor-output-2026-10-11/desktop-real-output-HDMI-A-4.png`（**687,829 bytes**）を
  **GitHub からも履歴からも消す**（実コンポジタ出力＝ユーザの実画面であり、残すべきでない）。
- **実施（手順を記録する）**:
  1. **バックアップ**: `git bundle create /tmp/celtium-before-purge.bundle --all`（**26,583,248 bytes**・4916 commit）
  2. `pip install --break-system-packages git-filter-repo`（同梱の wrapper は module 不在で動かなかった）
  3. `git filter-repo --path <file> --invert-paths --force` → **0.48 s で新履歴**・**4916 commit 維持**
  4. `git remote add origin …`（filter-repo は remote を外す）→ **`git push --force origin main`** ＝ `+ 770620c0c...c9e94bb9e (forced update)`
  5. `git reflog expire --expire=now --all`・`.git/filter-repo` 削除・**`git gc --prune=now`**
- **検証**: **`git log --all -- <file>` = 0**・**worktree から消滅**・**旧 commit `1e08cfd60` は prune 済み**（`git cat-file -e` が失敗）。
- **重要な副作用（記録）**: **この rewrite は全 commit のハッシュを変えた**（ファイルが初期に導入されたため）。
  ＝**本節より前の docs にある commit ハッシュ（`218ea2120`・`7117bbdf3` 等）は rewrite 前の履歴を指す**。
  **バックアップ bundle が rewrite 前の全履歴を持つ**ので、旧ハッシュの照合はそれで行う。**今後の commit は新ハッシュで記録する**。
- **限界（正直に）**: **GitHub 側は force-push 後も旧 object をしばらく保持しうる**（旧 SHA を直接指定すれば到達可能な期間がある）。
  ＝**「消えた」と断言しない**。恒久的な削除が要るなら **GitHub Support に cached view の purge を依頼**する必要がある。
- **他担当への影響**: 共有 working tree なので **未コミットの作業は無事**（rewrite 時点で dirty 0 を確認）。**次の commit は `c9e94bb9e` の上に載る**。

## 0.356 **round 163: BH-09 の受入は fixture gap で塞がっている（`GuiSlot` 系画面を開く knob が無い）**

- **確認**: `ValidationClient` の knob を全部読んだが、**任意の `GuiScreen` を開く経路が無い**。
  画面を開くのは **`GuiOptions` → `MixinGuiOptions` が `CeleritasVideoOptionsScreen` へ divert** する経路だけで、
  利用できる scene は **terrain・videoSettings・videoSettings+GUI_SCALE** の3つ＝**この3つはすべて `lineBatchesNarrowed=0`**。
- **原典**: vanilla で scissor を使うのは **`GuiSlot.drawScreen`**（Forge javadoc: *"GL_SCISSOR is enabled for anything that is rendered outside of the view box"*）。
  `GuiLanguage$List extends GuiSlot`・`GuiScreenResourcePacks`・`GuiContainerCreative`・`GuiScreenWorldSelection` が該当。
  ＝**fixture はそのどれも開けない**。
- **結論（次の unit は leg ではなく fixture）**: **`GuiSlot` 系画面（最小は `GuiLanguage`）を開く knob** を fixture に足す。
  ＝**これが無い限り BH-09 の `scissorCapturedGroups > 0` は原理的に取れない**。
  **leg を重ねるのは無駄**（3 leg で確認済み）。**fixture gap を gap として名指しする**のが正しい処理（「未対応を成功風に黙殺しない」）。
- **fixture 追加の形（次の担当へ）**: `celtium.validation.openScreen=<class名>` のような knob を1つ足し、
  既存の `guiClickDivertedTo`/`guiScreenClassAtCapture` の記録に乗せる（新しい機構を作らず既存の divert 経路の隣に置く）。
  受入は **その leg で `lineBatchesNarrowed > 0`**（＝経路を踏んだ証拠）→ その後に `scissorCapturedGroups`/`scissorLateCapturedGroups` と一旗対照。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地（`218ea2120`）・計器修正着地（3 trigger・各 leg で印字確認）・
  受入は fixture の knob 待ち**。**4 leg を費やしたが、いずれも経路未 exercise と計器が自分で示した**。

## 0.355 **round 162: BH-09 の受入は3 leg 連続で `lineBatchesNarrowed=0` — 「経路を exercise する構成」自体が未発見**

- **3 leg の結果（すべて計器は正常・すべて 0）**:
  | leg | 構成 | `lineBatchesNarrowed` | `scissorCapturedGroups` | `scissorLateCapturedGroups` |
  |---|---|---|---|---|
  | `r152-bh09` | terrain 既定 | **0** | 0 | 0 |
  | `r153-bh09` | `scene=videoSettings`＋`keepGui` | **0** | 0 | 0 |
  | `r154-bh09` | ＋`videoSettingsOption=GUI_SCALE`＋`menuHoldTicks=200` | **0** | 0 | 0 |
  ＝**3 leg とも scissor 経路を踏んでいない**。`CELTIUM_VERIFY via=frame 120` は各 leg で印字＝**計器は正常**。
- **sky-draws の独立確認（成果物の上の測定）**: 手元の脚（`guiscale-leg`・`intel-inv-glless`・`intel-panel-glless`・`t30-count-keys`・`t30-growth-keys`・`glow-glless`）の
  **いずれにも scissor 系カウンタが出ていない**。`guiscale-leg` は報告 JSON はあるが**カウンタ不在**＝あの jar は `CELTIUM_VERIFY` より前なので
  **「0」と「未計装」を区別できない**（＝**カウンタの不在は描画の不在ではない**というこの件の罠そのもの）。
  ＝**`lineBatchesNarrowed=215` を出した構成は成果物からは特定できない**（Lead の結論と一致）。
- **次の一手（より強い見当）**: **`lineBatchesNarrowed` は「開いた group の中で live scissor が動いた」時に数える**ので、
  **group の途中で scissor が変わる**構成が要る。vanilla で確実に scissor を使うのは **`GuiSlot.drawScreen`**（Forge javadoc が
  "GL_SCISSOR is enabled for anything that is rendered outside of the view box" と明記）＝**`GuiLanguage`・`GuiScreenResourcePacks`・
  `GuiContainerCreative`・`GuiScreenWorldSelection`** など。Celeritas のスクロール UI より**こちらが確実**。
  ＝**次は `GuiLanguage`（言語画面）か resource pack 画面を開く leg**で `lineBatchesNarrowed > 0` を先に確認する。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地（`218ea2120`）・計器修正着地（3 trigger・各 leg で印字確認）・
  受入は「scissor が live な構成」の発見待ち**。**3 leg を費やしたが、いずれも経路未 exercise と計器が自分で示した**。

## 0.354 **round 161: BH-09 の受入は GUI scene でも 0（`lineBatchesNarrowed=0`）— 「経路を exercise していない」ことを計器が自分で示した**

- **測定（`r153-bh09`・`scene=videoSettings` ＋ `keepGui=true`）**: `CELTIUM_VERIFY via=frame 120` は印字され（計器は正常）、
  **`scissorCapturedGroups=0`・`scissorLateCapturedGroups=0`・`lineBatchesNarrowed=0`**。
  ＝**この leg も scissor 経路に触れていない**（`lineBatchesNarrowed=0` がそれを名指しする）。
- **重要な副産物**: **`lineBatchesNarrowed` が「その leg が経路を exercise したか」の判定子として機能する**。
  ＝**受入を読む前にこれを先に見る**という手順が、この2 leg で確立した（「経路を exercise していない緑の leg は受入ではない」の実装形）。
- **`lineBatchesNarrowed=215` を出した leg の構成は、現在ディスク上の artifact からは読めない**（runtime 配下に残っていない）。
  ＝**構成を再発見する必要がある**（推測で leg を打たない）。
- **移譲**: **`sky-draws`（BH-09 の担当）** に「scissor が実際に live な構成を1つ見つけ、`scissorCapturedGroups > 0` または
  `scissorLateCapturedGroups > 0` を `-Dceltium.vulkan.groupScissor=false` の一旗対照と同一 jar で示す」ことを依頼済み。
  候補は Celeritas 画面のスクロール list・menu-hold でその画面を出してスクロールした構成。**`mods[].sha256` で同一 build を証明**することも明記。
- **BH-09 の状態（正確に・解決済みとは書かない）**: **機構特定・修正着地（`218ea2120`）・計器修正着地（3 trigger）・受入は leg 待ち**。

## 0.353 **round 160: BH-09 の計器は動いた（`CELTIUM_VERIFY via=frame 120`）— ただし terrain scene は scissor を使わないので受入は GUI scene で取る**

- **計器の修正が効いた**: `638529520`・`d3d4f0a81` の3 trigger により、**leg `r152-bh09` で `CELTIUM_VERIFY via=frame 120` が印字**された
  ＝**「カウンタが 0 ではなく不在」という3 leg 続きの混乱は解消**（summary は F3 の Celeritas 節が描かれた時しか届かなかった）。
- **測定（`r152-bh09`・terrain scene）**: **`scissorCapturedGroups=0`・`scissorLateCapturedGroups=0`・`lineBatchesNarrowed=0`**。
  ＝**この scene は scissor を一切使っていない**（以前 `lineBatchesNarrowed=215` を出したのは **GUI scene**）。
  ＝**受入は GUI scene で取る必要がある**（「経路を exercise していない緑の leg は受入ではない」というこのプロジェクトの規則そのもの）。
- **次の leg**: `scene=videoSettings` ＋ `keepGui=true`（Celeritas 画面のスクロール UI が scissor を使う）で
  **`scissorCapturedGroups > 0` または `scissorLateCapturedGroups > 0`** を確認し、`-Dceltium.vulkan.groupScissor=false` の一旗対照と並べる。
- **BH-09 の状態（正確に）**: **機構特定・修正着地（`218ea2120`）・計器修正着地・受入は GUI scene の leg 待ち**。

## 0.352 **round 159: blend-state が BH-03・04・23・34 を着地・受入（4件）— 解決済み 27 件へ**

- **BH-03（`7117bbdf3`）受入済み**: shader が実際に読む位置（push offset 100/112）で数える計器を追加（`alphaSelectorCounts`/`lastAlphaPushRef`/`alphaReferenceValues`）。
  同一 jar `ab798f27`・一旗対照・両脚 clean: 既定 `selectors=[0x0:1852 0x204:2455]`（0x0204=GL_GREATER）対 `alphaFuncOverride=515` で `[0x0:1744 0x203:2380]`。
  参照は両脚 `refs=[0.0 0.1 0.5 0.004]`＝**ゲームが設定した複数の閾値がそのまま届いている**。
  **併せて property の欠陥を1つ修正**: slot 14 の 0 は「無効」なので、無条件 override は**無効な batch の test をオンにして閾値0で捨てていた**（修正前 `[0x203:4536]` で 0x0 が消滅）。今は比較が有効な batch にだけ効く。
- **BH-34（`c906bb0e5`）受入済み**: 修正は既に着地済みで受入を追加。`texEnvProbe` が GL_TEXTURE_ENV_MODE を int/float 両 API で設定し `glGetTexEnviv` で読み戻す。
  脚 bs44（PASSED・clean）: `before=8448 afterInt=8448 afterFloat=8448 afterIntCombine=34160 afterFloatCombine=34160 verdict="int and float agree"`。
- **BH-04 colour 半分（`ca52c0a1a`）受入済み**: 境界は既に着地済み・fixture の既存 probe が finding の症例そのもの。制御旗 `-Dceltium.vulkan.groupColour=false` を追加。
  同一 jar・両脚 PASSED・clean: 既定 左(255,0,0)/右**(0,255,0)** 対 対照 左(255,0,0)/右**(255,0,0)**（＝後続 draw が前の色で描かれる旧形）。
- **BH-23（`9d26c4079`）受入済み**: (1) **sampler 確認が announcement block の下にあり**、consume 済み announced image はその手前で return するため**announcement 経由で毎回返る texture は一度も再確認されなかった** → 確認を lookup の先頭へ移動。
  **同一 jar で `minFromRecord=14` 対 `3`**（14件中11件が飛ばされていた）。(2) `mipmapMode` の常時 LINEAR を記録された `GL_TEXTURE_MIN_FILTER` から導出（9984/9985→NEAREST・9986/9987→LINEAR・未記録は従来どおり）。
  **やらないと決めたこと**: 非 mipmap filter を maxLod=0 に読み替えること（chain の権威は記録された `GL_TEXTURE_MAX_LEVEL`）。**未行使と明記**: この scene は `*_MIPMAP_NEAREST` を記録しないので新しい分岐は発火していない。
- **BH-09（Lead `218ea2120`）**: 修正着地（group が開いた時に scissor が無効なら **live 記録を採る**・`scissorLateCapturedGroups` で区別）。
  **受入は未取得**（カウンタ summary が F3 の Celeritas 節経由でしか届かず、3 leg で「0 ではなく不在」だった）。
  **計器を修正**: summary を **world teardown と frame 120 の2つの trigger**から `CELTIUM_VERIFY via=…` として1回印字（`638529520`・`d3d4f0a81`）。
- **解決済みは 27 件へ**（`docs/history/2026-10-11-code-bughunt.md` の冒頭も更新）。**未解決は 9 件**（BH-14・17・18・20・21・24・26・33・35）＋**BH-09 は修正着地・受入未取得**。

## 0.351 **round 158: GUI 文字の暗転は「現行ツリーでは再現せず」で閉じた（gui-text）— 私の §0.342 仮説は測定で反証**

- **反証（私の記録の訂正）**: §0.342 で私が書いた「**影パスの暗色が本体パスに漏れる**」説は**測定で否定**された。
  ユーザ crop の2行目は **本体 (170,170,170) = `colorCode[7]`・影 (42,42,42) = `colorCode[23]` で厳密**、1行目はストローク **321/321 が本体色 (255,255,85)・影 (63,63,21) で +1,+1・異色画素 0**＝**ピクセル完全一致**。
- **唯一の実差**: 2行目の ink 196 セルのうち **24 セル（12 %）が本体パスに欠落**（影パスは全部描いている）。欠落は**透明テクセルに接する端の画素**で、**glyph 単位ではなく quad 単位**（同じ 'o' が index 8 では無傷・14 では 2px 欠落）。
- **対照（新計器 `scripts/cpu-contract/font-shadow-main-check.py`＝「影画素の左上1 GUI px は本体色でなければならない」契約）は全て clean**:
  | 対照 | 色 | 影画素 | 欠落 |
  |---|---|---|---|
  | ユーザ crop 1行目 | §e | 1305 | **0** |
  | **ユーザ crop 2行目** | **§7** | 1467 | **207（14.1 %）** |
  | **`r135-e` 09:51 の video options tooltip**（`§7Performance Impact:` を含む） | **§7** | **2718** | **0** |
  | `fast-f3off`（chat） | 白 | 35,208 | **0** |
  | **同一 jar・1 flag 差 `-Dceltium.font.fastPath=false`** | 白 | 36,018 | **0** |
  上流 GL 参照・Intel tooltip・JEI tooltip も 0。**`fastGlyphs=60000` 対 `0`** で両 leg の経路差を証明＝**旧 capture 経路も新 fast path も機序ではない**。
- **結論**: **現行ツリーでは再現せず、直すべき契約は名指しできない**（推測で文字経路を変えない＝AGENTS.md の規則に従った正しい停止）。
  ユーザの jar（10:32・md5 `2747418b…`）と対照 jar（10:47・`c068dd6c…`）の差は **19 class entry**（font 側は1旗実験で除外済み・残るのは stub/backend/registry 側で glyph の色/UV/alpha 契約の所有者ではない）。
- **次の1点**: ユーザに **crop ではなく元の PNG 全体**（または現行 jar での再取得）を依頼し、同じ計器を当てる。違反が出たら
  **`-Dceltium.vulkan.groupBoundsFormat=2`** で影 run と本体 run の group の `texMat`/`state`/`colour0`/`vertexColour0` を並べる（`CELTIUM_GROUP_BOUNDS` が全部印字）＝**2パス間に差が残り得る唯一の場所**。
- **計器の修正（Lead・本ラウンド）**: カウンタ summary が **F3 の Celeritas 節が描かれた時だけ**届く経路だったため、
  `showDebugInfo=true` の leg でも**カウンタが「無い」ように見えた**（0 ではなく不在）。**world teardown で `CELTIUM_VERIFY` を1回印字**するようにした（leg は必ず world を離れるため）。

## 0.350 **round 157: ユーザ profiler の解読完了（perf-ring `d2211795c`）— 「ラグい」の実体はスパイクで、優先順が変わる**

- **解読（`.celtium-work/evidence/user-reports/analyse_lagspike.py`・全表 `lagspike_analysis.txt`・`docs/plan.md` §0.227・`docs/performance_plan.md`）**:
  形式 `1.1.0`・77 section・**1,364,259 レコード**（childCount, sectionId, durationNanos）・**358 frame / 20.00 s**・単位 ns。
  **未確定として明記**: 末尾 varint 1個（値0）・frame の childCount が実 children より大きい場合（block 0: 102 対 100）。
- **frame 分布**: mean **55.9** / p50 **34.1** / p95 **331.8** / p99 **358.9** / worst **384.6 ms**。
  **≥50 ms の 64 frame（17.9 %）が wall time の 10.1 s / 20.0 s = 50 %** ＝**ユーザの「ラグい」はスパイクが本体**。
- **スパイクの行き先（最悪5 % = 18 frame・中央値 frame 27.0 ms との比）**: **`gameRenderer` 318.6 対 25.0 ms（12.7×）**・
  `level` 31.9 対 11.3・`tick` +23.5、**chunk 構築系はスパイクにだけ出現**（`blocks` +8.9・`getChunk` +7.8・`regular` +4.1・
  `getBrightness` +3.7・`chunkCache` +1.9。中央値 frame ではすべて 0）。
- **最重要の未帰属**: スパイクの `gameRenderer` 318.6 ms のうち**名前のある子で説明できるのは約 72 ms・残り約 246 ms/frame はどの section にも現れない**。
  `frameFenceWaitMillis`（0.14 ms/wait）は**桁が違いすぎて説明にならない**＝**機構は断定しない**。**「残差を pass 単位で割る計器」が先**。
  ＝**`acceptance` へ移譲済み**。
- **我々の仮説との不一致（記録）**: `forgeHudText` 合計 **2.7 ms**・`chat` 201.8 ms/20 s ＝**この捕獲では文字は支配要因ではない**（F3 非表示だった可能性）。
  一方 **`gui` は毎 frame 約 13.7 ms の定数**（中央値 frame の約半分・スパイクで増えない）＝**スパイク源ではなく定数税**。`getBrightness` は **448,707 呼び出し**。
- **限界（そのまま）**: 自己時間の単純合計は frame 時間の **4.01 倍**（`frame`/`root`/`gameRenderer` が各々全時間を持つ）＝**重複しない分解ではない**・
  **profile に jar hash は無い**（ユーザのビルド）・形式の2点は未確定。**我々の受入値として引用しない**。
- **優先順の更新**: **(1) 未帰属 246 ms/frame の pass 単位分解**（`acceptance`）→ **(2) chunk 構築系（`getBrightness` 448,707 呼び出しを含む）**→
  **(3) `gui` の定数 13.7 ms/frame**（`gui-stage`）→ **(4) F3 テキスト（font 刷新で −94 %/glyph 済み）**。
- **OPT-08 step 1 も着地**（perf-ring・jar `5ddd93b4`→`98fa051e` 同 scene 比較）: **warm p50 36.8→34.8・p95 50.3→52.3・p99 57.0→56.4 ms（非回帰・wait は未除去）**・`hostBuffersCreated=6` 不変。

## 0.349 **round 156: 停止点と「名前の無い契約」を記録（frame-composite・proj-channel）— BH-20 と BH-21 census を再移譲**

- **記録に値する成果（実装ではなく契約の同定）**: 2担当が**未着手のまま最後の緑コミットで停止**し、その理由と**欠けている契約の名前**を残した。
  | BH | 欠けている契約（名指し） | 実装の入口 |
  |---|---|---|
  | **BH-14** | **target の同一性キー（identity + generation + size）** | `GllessFrameTarget` は `defaultPair`/`chainPair` の2組のみで、非0・非main はすべて `chainPair` へ落ちる。`adoptChainFramebuffer` が「chain の最初の入力 identity」だけを採用する規則を per-identity へ広げる設計名が要る |
  | **BH-17** | **GUI が1 draw あたり実際に必要とする payload**（測ってから分割） | `VulkanImmediateTexturePipeline`/`VulkanImmediatePipeline` が 208 byte を無条件登録・lit は 216。測らずに分割すると ABI を壊す |
  | **BH-26** | **recording token の pending/confirmed 契約** | `GllessFrameTarget.recordAcquire` が記録時に layout/`firstAcquire` を変更・`applyGameClears` が latched mask と `neverCleared` を消費。**submit 前に reset すると実行されなかった transition/clear の前提だけが残る**。この契約が決まるまで layout 順序は論じられない |
  | **BH-21** | **per-world と per-frame の資源分類** | この分類がコードにも plan にも無い。per-frame のものを world destroy で消すと次 frame が宙に浮く。**最初の作業は delete ではなく「world switch を挟んだ created/released の census 1本」**（材料: `hostBuffersCreated`・`frameArena`・`descriptorSetCensus()`・`identityPairCensus()`・`CELTIUM_LIFECYCLE announceImage`） |
  | **BH-24** | **置換の transaction 型**（BH-20 と同じ） | 不変条件は「旧 buffer は新が設置され全 writer が新を指すまで有効かつ所有されたまま」 |
- **BH-20 は contract が既に名前を持つ唯一の1件**（**作成数／解放数の census**）＝受入は「各段階を1つずつ失敗させ、作成数と destroy 数を突き合わせ、次の正常 create が成功する」。
  対象は `VulkanImmediateTexture/Item/Pipeline`・`VulkanPostChain.create`・swapchain の semaphore pool（現状 shader module の finally しか戻らない）。
- **再移譲**: **BH-20（着手可能）** と **BH-21 の census（delete の前提）** を `acceptance` へ。**BH-24 は BH-20 の transaction の型ができてから**・**BH-14/17/26 は契約の名指しが先**という順序を守る。
- **ツリー**: HEAD 緑・未コミットなし（frame-composite の OPT-04 契約コメントは既にコミット済みであることを確認）。**font の証拠 crop 2枚をコミット**（`8e79fe65b`）。

## 0.348 **round 155: 未解決 BH 13件を担当へ移譲（`docs/history/2026-10-11-code-bughunt.md` 冒頭に解決状況を明記）**

- **`docs/history/2026-10-11-code-bughunt.md` の冒頭に解決状況を書いた**（`d4da099db`）:
  **解決済み23件を列挙**・**BH-09 は部分なので解決済みと書かない**・**未解決13件を担当へ移譲済みと明記**・
  **本文は起票時の記述であり現在の状態ではない**と明示（古い段落を生きた欠陥として読ませない）。
- **移譲（5担当・13件）**:
  | 担当 | BH | 契約 |
  |---|---|---|
  | **blend-state** | **BH-03・04・23・34** | alpha test の参照値と比較関数／immediate batch key の colour 半分／sampler の再照会／`glTexEnvf` の float bit 列 |
  | **frame-composite** | **BH-14・17・26** | 非 main FBO の chain identity（site 地図は plan §0.299/303/306）／208 byte push constant と 128 byte 経路／所有 image の layout と初回 clear |
  | **pipelines** | **BH-18・35** | swapchain 引数を surface capabilities から導出／広告する GL4.6・GLSL4.60 と backed な symbol の一致 |
  | **proj-channel** | **BH-20・21・24** | 途中失敗の handle 回収／world destroy の資源 delete／UniformBuffer の grow の所有順序 |
  | **sky-draws** | **BH-09・33** | **`scissorCapturedGroups=0` を >0 にする**（受入は counter）／chunk fade-in の設定と uniform の一致 |
- **各担当への共通指示**: 原典を先に読む・失敗ゲートは測定で反証するまで証拠・counter の不在を描画の不在と読まない・
  **1件1コミット＋その受入**・名指しされていない契約が要るなら**最後の緑コミットで止めて名指しする**・報告は日本語で短く。
- **注意して渡した点**: BH-14 は既存の site 地図から始める（再構築させない）・BH-17 は OPT-03 の全部を待たず GUI が実際に何を要るかを測る・
  BH-26 は「未submit の破棄で layout が戻らない」という寿命契約・BH-21/20/24 は create/grow/destroy の同じ契約の3面なので一緒に取る・
  BH-09 は「counter が動くこと」が受入（絵ではない）。

## 0.347 **round 154: BH-01..37 の到達状況（`docs/history/2026-10-11-code-bughunt.md` の37件）**

- **P1 は 23 件**（BH-01..07・09・10・12..19・21・24・25・26・27・37）、**P2 は 14 件**（BH-08・11・20・22・23・28..36）。
- **着地・受入済み（plan の各 round 記録が根拠）**:
  | BH | 内容 | 根拠 |
  |---|---|---|
  | BH-01 | 地形アリーナ容量と stride | **受入済み**（記録） |
  | BH-02 | 透明地形の index offset 0 誤認 | sorted pass の index-source 規則として着地 |
  | BH-05 | lighting を group 生成時に捕捉 | **着地**（BH-37 と同時に記録） |
  | BH-06 | texture unit ごとの bind 保持 | per-unit `boundTextureByUnit` として着地 |
  | BH-07 | separate blend の alpha factor | **着地**（round 29-32・BH-10 と同時） |
  | BH-08 | immediate の cull state | **着地**（round 29-32） |
  | BH-09 | scissor 無視 | **部分**（`lineBatchesNarrowed=215` 着地。`scissorCapturedGroups=0` は plan の `>0` を満たさず**未了**） |
  | BH-10 | color mask 無視（Fancy 雲） | **受入済み**（506 対 368 partialColorMaskGroups・159 group が zero mask） |
  | BH-11 | polygon offset 無視 | **着地**（`polygonOffsetCalls`・`immediateDepthBiasedGroups`） |
  | BH-12 | 2px 選択線の拒否 | **受入済み**（narrowed 総数 215） |
  | BH-13 | viewport 復元 | **受入済み** |
  | BH-15 | off-target 失敗の置換 | **安全な半分を着地** |
  | BH-16 | clear の target/mask/色/順序 | **着地**（drop 規則が frame 自身の clear を落としていた件を修正） |
  | BH-19 | present 可能性の事前確認 | **受入済み** |
  | BH-22 | descriptor の退役 | **着地**（退役＋census） |
  | BH-25 | region upload 失敗の握りつぶし | **受入済み**（`failedSections()`） |
  | BH-27 | mesh upload/present の queue 待機 | **着地**（OPT-07(b) `uploadSubmitWaitsAtEnd` 31343→6910） |
  | BH-28 | immediate group ごとの buffer 確保 | **着地**（OPT-05 `hostBuffersCreated=6` for 8316 groups） |
  | BH-29 | format 8/9/10 の normal 未使用 | **受入済み**（night/day） |
  | BH-30 | item/block/particle の world fog | **受入済み**（one-flag 対照） |
  | BH-32 | atlas sampler の maxLOD 4 固定 | **着地**（`CELTIUM_ATLAS_SAMPLER maxLod=`） |
  | BH-36 | multidraw の command ごと分解 | **着地**（OPT-09・1086 indirect call が 17,651 draw＝16.3:1） |
  | BH-37 | 一括 sample transition が直後の attach も変える | **既存ルールで被覆** |
- **未了（明示）**:
  | BH | 内容 | 状態 |
  |---|---|---|
  | **BH-03** | alpha test の参照値 | **stub property は着地・検証 leg 未了** |
  | **BH-04** | immediate batch key の colour/texture matrix/fog | **colour 半分が未了**（accept 時 vs flush 時の状態クラスとして記録） |
  | **BH-09** | scissor | **`scissorCapturedGroups=0` が plan の `>0` を満たさない** |
  | **BH-14** | 非 main FBO の chain image 共有 | **未着手**（site 地図と one-pass cut は記録済み） |
  | **BH-17** | 通常 GUI の 208 byte push constant | **未着手**（OPT-03 が前提） |
  | **BH-18** | swapchain 作成引数 | **未着手**（明示的に open として記録） |
  | **BH-21** | world destroy の資源 delete | **担当記録あり・着地記録なし** |
  | **BH-23** | sampler state の再照会 | **未着手** |
  | **BH-34** | `glTexEnvf` の float bit 列 | **未着手** |
  | **BH-35** | GL4.6/GLSL4.60 の広告 | **census 着地・strict-symbols leg 未了** |
  | **BH-20** | native constructor 途中失敗の回収 | **plan に記載なし＝未対応** |
  | **BH-24** | UniformBuffer の grow | **plan に記載なし＝未対応** |
  | **BH-26** | 所有 image の layout/初回 clear | **plan に記載なし＝未対応** |
  | **BH-33** | chunk fade-in の設定 | **plan に記載なし＝未対応** |
- **集計（この記録時点）**: **37件中 23件が着地または受入済み**、**BH-09 は部分**、**13件が未了**（うち4件は plan に記載が無く**未対応**）。
  ＝**未了の P1 は BH-03・04・09・14・17・18・21・23・24・26 の10件**、**未了の P2 は BH-20・33・34・35 の4件**（BH-09 は部分なので P1 の未了に数える）。

## 0.346 **round 153: ロード画面の診断を訂正（blend-state の測定）— `glBegin` 機構は誤り・census の数字はフォントのもの**

- **訂正（前ラウンドの記録は誤り・名指しで直す）**:
  1. **GL-less では `SplashProgress.start()` が cancel される**（`SplashProgressMixin.java:19-22`・fancy splash は2つ目の共有 GL context を要し、
     `DrawableGL.createSharedContext` が GL context 無しで throw するため）。**測定（leg `bs39-begin`）**: skip 行はログにあり、
     **`CELTIUM_IMMEDIATE_BEGIN` は 0 行**・census に `glBegin`/`glEnd`/`glVertex3f`/`glTexCoord2f` が**一切無い**
     ＝**`SplashProgress` の3サイト（:294/:353/:416）は受入構成で実行されない**。＝**蓄積器は蓄積するものが無い**。
  2. **census の `glVertex3f=152460`・`glBegin=38115` はフォントのもの**（原典: `glVertex3f` は `FontRenderer.java:240-246/:294-300` で
     `GlStateManager.glBegin(5)`（GL_TRIANGLE_STRIP）`:238/:292` の中）。**splash は `glVertex2f`**（`:296/:355/:417`）を使い、
     **stub は `glVertex2f` を列挙していない**＝generic no-op で**一度も数えられていない**。色は `glColor3ub`（`:409-412`）。
     **旧 census の 353316/88329 = 4.0 頂点/run はフォントの4頂点 strip と一致**し、**glyph バッチ後はフォントが run を開かない**。
     ＝**「census の `glVertex3f` と一致するか」という指定した判定は、ロード画面ではなくフォントを測っていた**（blend-state は採用しなかった・正しい判断）。
- **受入脚の契約欠落（step 4 が「名指しされていない契約」である理由）**: ロード段階は **`Minecraft.java:517`**（`SplashProgress.drawVanillaScreen` →
  `Minecraft.drawSplashScreen`）＝**`startGame` の中・最初の client tick より前**。既存の dump switch はすべて後段に鍵がある
  （`dumpPresentedTick`＝world tick・`dumpPresentedMenuTick`＝`GuiMainMenu` が開いた client tick・`screenshotAtTick`＝world tick）。
  ＝**どの flag 値でもその窓に落ちない**。必要なのは **splash 段階のフック**（例: `Minecraft.drawSplashScreen` の HEAD/RETURN で presented dump を要求し、
  record に `loading` stage 名を入れる）＝**新しい契約**（blend-state は発明せず止めた・正しい）。
- **GL-less で実際に走っているロード画面**: **Tessellator のもの**で、その幾何は即時経路に届いている
  （ログ: `DynamicTexture.<init> <- Minecraft.func_180510_a:889 <- SplashProgress.drawVanillaScreen:1051`・
  `Tessellator.func_78381_a:22 <- Minecraft.func_180510_a:908`）。
  ＝**「ロード画面が無い」は `glBegin` 機構ではない**。次の unit は **(a) ロード段階の dump 契約を足して presented frame に何が入っているかを測る**か、
  **(b) `drawSplashScreen` の既に届いている幾何の picture/timing 問題**のどちらか。**(a) を先に取る**（測らずに (b) を論じない）。
- **step 1 は最後の緑コミット**（`654dc9253`）。**step 2-3 は前提が崩れたので実装しない**（blend-state の判断を支持する）。

## 0.345 **round 152: ユーザの profiler 画像（frame/tick 内訳）— `gui` self 5.36 s（26.8 %）と `gameRenderer` self 6.19 s（31 %）が最大**

- **受領**: `2026-10-12-profile-frame.png`（48,882 bytes）・`2026-10-12-profile-tick.png`（79,076 bytes）
  ＝`.celtium-work/evidence/user-reports/` に保存。**frame 合計 19,999.36 ms**。
- **frame の内訳（self 時間に注目・self が「その section 自身が使った時間」）**:
  | section | total | **self** | 割合 |
  |---|---|---|---|
  | **`gameRenderer`** | 17,354.98 ms | **6,187.85 ms** | **86.78 % / self 31 %** |
  | **`gui`** | 5,697.02 ms | **5,359.15 ms** | **28.49 % / self 26.8 %** |
  | `root` | 19,998.50 ms | **1,248.31 ms** | self 6.2 % |
  | `entities`（子 `entities`） | 1,744.67 / 1,658.29 ms | **1,658.29 ms** | 8.29 % |
  | `terrain` | 1,008.67 ms | **1,008.67 ms** | 5.04 % |
  | `hand` | 656.30 ms | 656.30 ms | 3.28 % |
  | `translucent` | 570.68 ms | 570.68 ms | 2.85 % |
  | `particles` | 368.13 ms | 368.13 ms | 1.84 % |
  | `sky` | 251.03 ms | 251.03 ms | 1.26 % |
  | `terrain_setup` / `lightTex` / `destroyProgress` / `clear` / `camera` | 165.61 / 138.90 / 54.23 / 35.30 / 34.35 ms | 同 | 各 0.8 % 以下 |
  | `chat` / `bossHealth` / `toolHighlight` / `forgeHudText` | 201.83 / 120.60 / 12.76 / 2.68 ms | 同 | `gui` の子は合計 ~337 ms |
  ＝**`gui` の self 5,359 ms に対し子の合計は約 337 ms**＝**GUI 自身の描画（HUD・widget・即時モード経路）が最大の単一項目**。
  ＝**`gameRenderer` の self 6,188 ms** も同規模（frame の 31 %）。
- **tick の内訳**: `tick` 1,276.29 ms（self 19.82）・`level` 1,071.93（self 55.54）・**`blocks` 613.01 ms**
  （**`getChunk` 533.55 ms・self 266.92**、うち **`getBrightness` self 258.83 ms**、`buildList` 20.72）・
  `entities` 283.88・`chunkCache` 119.36・`animateTick` 56.03・`keyboard` 41.14・`particles` 33.13・`textures` 25.66。
  ＝**tick 側の最大は `getBrightness`（258.83 ms self）**。
- **優先順の更新（ユーザの実データが根拠）**: **(1) `gui` の self 5.36 s**（＝即時モード/GUI 経路。font 刷新は F3 テキストに効いたが
  **GUI 全体は未着手**）→ **(2) `gameRenderer` の self 6.19 s**（frame 自身の renderer 側）→ **(3) `entities` 1.66 s** →
  **(4) `terrain` 1.01 s** → **(5) `hand`+`translucent` 1.23 s**。**tick 側は `getBrightness` 258 ms**。
- **注意**: これは**ユーザの実機・実セッション**の数字であり、**我々の leg の数字と同一視しない**（scene・duration・mod 構成が違う）。
  **比と順位の根拠として使い、絶対値を我々の受入値として引用しない**。

## 0.344 **round 151: font レンダラ刷新 前半が着地（glyph-fix `27123e756`）— テキスト経路 −94 %/glyph・cold p50 −74 %**

- **実装**: `FontRendererMixin` が **`func_78266_a`（default page）で自分で glyph を描いて vanilla の本体を cancel**（HEAD/cancellable/require=0）。
  `CeltiumFontGlyphCapture.fastDefaultGlyph` が **4頂点と UV を `renderDefaultChar` と同一の式**（`FontRenderer.java:230-249`）で書き、
  **既存の `submit()` にそのまま渡す**（format 2・stride 24・mode 5・coalesced run・strip separator・同じ拒否・同じカウンタ）。
  色は capture が mirror している `GlStateManager.color`（window 経路と同一の出所）。
  ＝**消えたのは per-glyph の `TextureManager.bindTexture` 連鎖と9個の `GlStateManager` wrapper**。
- **契約**: unicode は未変更（`func_78277_a` は vanilla のまま）。fast path は unit が sheet を保持していない時だけ再 bind
  （**1.28M glyph で 93 回**・`fontTextureRebinds`）。**`-Dceltium.font.fastPath=false` が同一 jar の制御**。
- **受入（同一 jar・F3 on・両 leg `pinSections=64`・両方 PASSED・`validationClean=true`・`validationMessageCount=0`）**:
  | | fast ON | fast OFF（制御） |
  |---|---|---|
  | glyph / string | 1,280,000 / 2,886 | 980,000 / 2,330 |
  | **`glyphMillis`（vanilla 本体）** | **0**（もう走らない） | 14,280 ms（14.6 µs/glyph） |
  | **`renderStringMillis`（全テキスト）** | **1,093 ms** | 14,076 ms |
  | 1 string / 1 glyph | **0.38 ms / 0.85 µs** | 6.04 ms / 14.4 µs |
  | **cold p50** | **144.7 ms**（23 sample・80 frame） | **550.6 ms**（8 sample・43 frame） |
  | `fastGlyphs` / `refused` | 1,280,000 / **0** | 0 / **0** |
  ＝**テキスト経路 −94 %/glyph・cold p50 −74 %**（同 jar・同セッション）。**F3 off（fast on）は p50 72.7 ms** ＝このセッションの overlay 費用 72 ms/frame。
- **文字が不変である証拠**: 同一テクセル検査で **line1 449/449・line4 104/104・空テクセル 0/896**（vanilla 経路と同値）。
  1x crop: `fastpath-f3-1x.png` sha256 `35c5c6d2c0e17e45…`・`fastpath-chat-1x.png` `afbadd03aeae0cbc…`・frame `05370807e760358d…`。
- **未確立（正直に）**: **chat のテクセル検査**（shadow＋scale で F3 の検査が使えず、leg 間 crop 比較は背景差 11.9 % を見るので文字の証拠にならない
  → **crop と「同じ `func_78266_a` 経路」までを主張**）・**unicode の fast path**（page 変更 bind が要るので未実装）・
  OPT-18 の layout cache/sign/map・**fast path 自身の内訳**（0.85 µs は配列書きと submit を含む全体）。

## 0.343 **round 150: ユーザの LagSpike profiler を受領（「絶対に参考にするべき」）— perf-ring が解析中**

- **受領物**: `profiler-2026-10-12_10-49-18(1).lagspikeprofile`
  （**1,018,235 bytes・bzip2・sha256 `ef29c446697ccad047cfa704f30daa8d97fe917ec6a0f926f2cd3acb6335bcf8`**）
  ＝**ユーザ自身のプレイセッションのプロファイル**＝**我々の計器より優先すべき一次証拠**。
- **複製・展開済み**: `.celtium-work/evidence/user-reports/2026-10-12-lagspike.lagspikeprofile`（原本）と
  `.celtium-work/evidence/user-reports/2026-10-12-lagspike.json`（**4,763,531 bytes**・展開後）。
- **Lead が読んだ構造（担当が再導出しなくて済むように記録）**: ASCII の版文字列 **`1.1.0`** → **長さ前置の section 名テーブル（77 個）** → バイナリの timing データ。
  名前には **`frame`・`render`・`clouds`・`toasts`・`root`・`culling`・`toolHighlight`・`sky`・`center`・`bossHealth`・`prepareterrain`・
  `forgeHudText`・`frustum`・`clear`・`scheduledExecutables`・`terrain_setup`・`sound`・`forge_render_last`・`display_update`・`weather`・
  `preRenderErrors`・`hand`・`chat`・`terrain`・`translucent`・`destroyProgress`・`outline`・`camera`・`litParticles`・`updatechunks`・
  `display`・`keyboard`・`textures`・`animateTick`・`chunkCache`・`reEntryProcessing`・`regular`・`blockentities`・`prepare`・`particles`・
  `mouse`・`gui`・`pick`・`level`・`gameRenderer` 系**が含まれる。名前テーブルは**約 830 byte** で終わり、以降が値。
- **担当**: **`perf-ring`** に解析を依頼（単位と限界を明記・per-section の合計と分布・frame 数と窓・spike の帰属・
  **識別できない field は推測せずそう言う**）。**我々の計器と食い違えばそれが知見**という枠も明示。
- **パノラマ（fog-path が完全着地・`1a308c25d`/`1d759f25d`）**: 白の所有者は **id 8 の mirror を埋めた `SplashProgress.drawVanillaScreen` の
  白一色 256×256 DynamicTexture**。**id 8 は一度も invalidate されない**（`CELTIUM_INVALIDATE_ORIGIN` は 9/10/12/16 に出るが 8 には出ない）。
  descriptor の key は古くなくサイズも一致＝**面はスプラッシュの残り物をサンプルしている**。比較で id 9 は `opaque=4015/16384` の実内容。
  残る修正は2案（stub の bind 捕捉を直す／**splash 終了時に id 8 の mirror を失効**）。計器は landed（`CELTIUM_MIRROR_DRAW`・`splashSourcedGroups`）。

## 0.342 **round 149: パノラマの所有者が判明（fog-path `1a308c25d`）・tooltip の light-state 契約を原典で確認（GUI 文字の暗転の候補機序）**

- **パノラマ（fog-path の着地）**: **所有者は splash の白い `DynamicTexture`（id 8）**
  ＝パノラマの copy が読む内容が **splash のテクスチャ**と混ざっている、という根因。mirror/descriptor の描画行と census を追加（`1a308c25d`）。
- **GUI 文字の暗転（原典から確認・`GuiScreen.func_146283_a`）**: vanilla の tooltip は
  ```
  glDisable(GL_RESCALE_NORMAL); RenderHelper.disableStandardItemLighting(); glDisable(GL_LIGHTING); glDisable(GL_DEPTH_TEST);
  … drawStringWithShadow(各行) …
  glEnable(GL_LIGHTING); glEnable(GL_DEPTH_TEST); RenderHelper.enableStandardItemLighting(); glEnable(GL_RESCALE_NORMAL);
  ```
  ＝**tooltip は lighting を明示的に落として描き、描いた後に戻す**。
  さらに **`drawStringWithShadow` は影パス（RGB を1/4）を先に描いてから本体パスを描く**ので、
  **影の暗い色が本体パスに漏れると文字が暗く見える**＝**ユーザ報告の症状そのもの**。
  ＝`gui-text` の2仮説（カラーコード経路／影パス）のうち**後者が有力**で、判定点は
  **`RenderHelper.disableStandardItemLighting`/`enableStandardItemLighting` と `glDisable(GL_LIGHTING)` を我々が state として追えているか**、
  および **影パスと本体パスの間で色が復元されているか**。
- **font 刷新の進行**: `glyph-fix` が **証拠画像**を出し始めた
  （`docs/history/evidence/font-f3-20261012/fast-on-line1-sheet-vs-frame.png`・`fast-on-line4-background-residual.png`）＝**leg の画像比較つき**。
- **スロット**: 他担当が使用中（pid 3011394）。HEI leg は依然待ち。

## 0.341 **round 148: ロード画面が出ない機序が確定（sky-draws）— 欠けているのはテクスチャではなく「幾何」**

- **機序（原典＋既存 census）**: Forge のスプラッシュは **レガシー即時モード**で描いている
  （`forge122/build/rfg/minecraft-src/java/net/minecraftforge/fml/client/SplashProgress.java` の `glBegin(GL_QUADS)` が **:294/:353/:416**・
  テクスチャ転送 `glTexSubImage2D` **:820**）＝**LWJGL の `GL11` を直接呼び、`GlStateManager` を通らない**。
  **GL-less の no-op census がそれを裏づける**: **`glVertex3f=152460`・`glBegin=38115`・`glEnd=38115`**
  ＝**スプラッシュの四角形はスタブの no-op として捨てられ、draw になっていない**。
  テクスチャ側は `a86273436` の replay で直っており mirror に存在し得るが、**それを貼る幾何が submit されない**という切り分け。
- **presented の早期フレームはロード画面ではない**: `guiscale-leg` の early dump は distinct=5515・mode=(155,179,221) で既にワールド/メニュー内容
  ＝**`dumpPresentedImage` の早期ダンプはロード中のフレームを捕まえていない**（受入脚の設計に効く）。
- **必要な実装（4段・そのまま実行できる形）**:
  1. stub の `SEM_BEGIN`/`SEM_END` に **site を出し**、`SplashProgress` の3サイトが実際に来ていることを確認（no-op 件数だけでは site が言えない）
  2. **上限付き頂点バッファ**で `glBegin(GL_QUADS)` を記録（`glVertex3f`/`glTexCoord2f`/`glColor4f` を蓄積）
  3. `glEnd` で **1バッチとして `submitImmediateBatch`**（format は POSITION_TEX_COLOR 相当）
  4. 検証脚は**ロード中の presented dump**（`dumpPresentedTick` をロード中に合わせる）で `validationMessageCount=0`
- **未着地の理由（sky-draws・記録に値する）**: client-array decode と同じ規模の新機能で、**中途半端に入れると現在動いている経路を落とす**ため、
  残予算では**壊さずに通せない**と判断して着地させなかった。**受け入れは未達**（脚も未取得）。
- **他の担当の進行**: `gui-text` を召喚（GUI 文字の暗転）・`sky-draws` に本 unit・`fog-path` にパノラマ・`glyph-fix` に **font レンダラ刷新（OPT-18）**・`perf-ring` に **OPT-08**。

## 0.340 **round 147: ユーザ報告（2026-10-12 最新）— 残るのは F3 のラグ・ロード画面・パノラマ・GUI 文字の一部が暗くなる**

- **ユーザの言葉（そのまま）**: 「俺が報告した問題のほとんどは治りました。**F3がとてもラグいのはまだ残っています。ロード画面もないです。パノラマもないです。GUI上の文字の一部が暗くなる問題と**」
  （画像は **tooltip の crop**（"Enchanted Book" / "Blast Protection IV"）＝**GUI 文字の暗転の実例**として提示）。
- **整理（4件・状態を区別）**:
  | # | 報告 | 現在の状態 |
  |---|---|---|
  | 1 | **F3 がとてもラグい** | **機序確定・Celtium 側で触れない部分が主**（caller 0.13 %・vanilla glyph 本体 3.15 µs/glyph・bind 連鎖は除去済み）。**未解決のまま残す**（fast path は互換リスクの決定待ち） |
  | 2 | **ロード画面が無い** | **新規**（splash/loading の native 描画が成立していない）→ **未着手** |
  | 3 | **パノラマが無い** | **機序確定**（copy が読む pair は `readImage==passImage` で正しいが **mirror が白**・7仮説棄却）→ **未修正** |
  | 4 | **GUI 上の文字の一部が暗くなる** | **新規**（tooltip の crop が実例）→ **未着手**・**hotbar item の暗転と同じ族**（light-map unit の enable 状態で色が変わる経路）の可能性 |
- **優先順（Lead の判断）**: **4 → 2 → 3 → 1**。(4) は**描画状態の契約**（色・light・alpha）で**根因を1つに絞れる**可能性が高く、
  hotbar の修正（light-map unit の enable 状態を見る）と**同じ計器**（`itemLitGuiGroups`・`CELTIUM_GROUP_BOUNDS` の colour0）で追える。
  (2) は**画面が無い**＝経路自体の欠落で、`SplashProgress`/loading の native pass を確認する。(3) は mirror の内容が残る1点。(1) は決定待ち。

## 0.339 **round 146: 2件の前進 — (1) `videoSettingsRowNames` が実名を返した（`options.renderClouds` を含む）(2) F3 に Vulkan device/driver 行を追加**

- **(1) row-name 経路が動いた（測定 `r139-e`）**:
  ```
  videoSettingsChainSource = controller pages
  videoSettingsRowNames = Render Distance,options.gamma,options.guiScale,options.fullscreen,options.vsync,
                          options.framerateLimit,options.viewBobbing,options.attackIndicator,options.graphics,
                          options.renderClouds,soundCategory.w…
  ```
  ＝**`controller.pages` から Option 名が読めている**。**目的の行 `options.renderClouds` が一覧に存在**。
  実装は `celtium$vsRowNames` が**素の `java.util.List` を直接走査**し、`celtium$vsRowLabel` が
  **`displayString`（GuiButton 形）→ `getName()`（Option 形・`getUnformattedText`/`getString`）**の順で読む（`c012870d9`）。
- **残る最後の gate**: finder は依然 `id`/`field_146127_k` で照合しているため `no option button could be located`。
  ＝**finder に同じ `getName()` 経路を足し、押す操作を control の action に切り替える**（§0.338 の3点セットの残り2点）。
- **(2) goal D（配布準備）を1件前進（`RenderGlobalMixin`）**: **F3 の render-info 行に Vulkan device/driver を追記**
  （`VulkanContext.deviceSummary()`/`driverSummary()` は static・device 未生成時は "unavailable"・空なら行を変えない）。
  ＝goal D の「設定画面・F3のVulkan device/driver表示」を満たす。**他の D 項目は既に整備済み**:
  `mcmod.info` が **Celtium 名・Celeritas/Sodium credits・検証済み GPU と未了項目の一覧**を持ち、
  **`COPYING`/`COPYING.LESSER`** があり、**`docs/scope-and-limits.md`** が要求表と未検証一覧を持つ。
- **このセッションで goal D に対して残るもの**: 再現可能な build 手順の文書化（`scripts/build.sh` はある）・
  配布 jar の最終確認（version・`mcmod.info` の整合）・既知問題の実装との一致（`scope-and-limits.md` の更新）。

## 0.338 **round 145: 次の実装点を原典で確定 — finder は `id`/`field_146127_k`（GuiButton 形）で照合しており、`Option` 形には `getName()` 経路が要る**

- **コード確認（Lead）**: `celtium$vsFindOptionButton` は候補を走査して
  **`celtium$fieldValue(candidate, {"id", "field_146127_k"})` が要求 id と一致するか**で選ぶ（`:6310` 付近）＝**`GuiButton` 形の前提**。
  ＝**`Option` 形の行は `id` を持たない**ため一致せず、`videoSettingsRowNames=none` と「no option button could be located」になる（前ラウンドの測定と一致）。
- **必要な実装（次 unit・2箇所）**:
  1. **finder**: 候補が `id` を持たない場合に **`getName()`（Component → `getString()`/`getUnformattedText()`）**で名前を読み、
     既存の token 照合（clouds/graphics/ao/particles）に載せる。
  2. **row-names**: 同じく `getName()` 経路を足し、`videoSettingsRowNames` に**実際の行名**を出す（`none` が消えることが進捗の証拠）。
  3. **押す操作**: `Option` には `GuiButton.mousePressed` が無いので、**`getControl().createElement(dim)` が返す `ControlElement` の action** を呼ぶ
     （`CyclingControl` は値を1つ進める）。
- **受入（その後）**: 同一 leg で 4 phase（`partialColorMaskGroups` 506→368→復元→GUI scale の `REQUIRES_RENDERER_RELOAD` 行）。
- **fixture 側の gate（5段）**: `optionsRowList`（通過）→ `buttonList`（通過）→ **controller 到達（通過）** →
  **行の読み取り（`Option` 形・本ラウンドで実装点確定）** → 押す操作（control action）。

## 0.337 **round 144: controller 経路が動いた（`videoSettingsChainSource=controller pages`）— 次の gate は「finder が Option 形の行を読めない」**

- **実装の経緯（Lead・3段）**:
  1. `controller` → `pages` → `getOptions()` を平坦化する経路を実装（`fa33b4927`）→ **null を黙って返していた**ので段を名指しする診断を追加（`85d1e3c59`）。
  2. 診断 leg で **`videoSettingsChainSource` が無い**＝経路に到達していないと判明。原因は **`GuiScreen` が必ず `buttonList` を持つ**ため、
     **Celeritas 画面のそれは非 null だが空**で、`list == null` チェックが発火しなかったこと。**空リストを欠落と同様に扱う**修正（`e7c28c2fe`）。
  3. **測定（`r137-e`）**: **`videoSettingsChainSource = controller pages`** ＝**controller 経路が走り、リストを返した**。
- **次の gate（正確に名指しされた）**: `videoSettingsRowNames = none` ＋ `videoSettingsChain = no option button could be located in the screen's row list`
  ＝**finder と行名リーダーが `Option` 形の行を読めない**（`GuiButton` の `displayString`/`id` を前提にしている）。
  ＝**次は finder/row-name を「`Option`（`getName()`）と `GuiButton`（`displayString`）の両方」に対応させ、押す操作を control の action に切り替える**。
- **進捗の証拠が artifact に出るようになった**: `videoSettingsChainSource` が
  **`controller pages`（成功）/ 各失敗段の理由**を返すため、次の leg は「どの段か」を名指しする。
- **fixture 側の gate は4段になった**: `optionsRowList`（通過）→ `buttonList`（通過・空の扱いを修正）→ **controller 到達（通過・本ラウンド）** →
  **行の読み取り（Option 形・次の実装）**。

## 0.336 **round 143: セッション引き継ぎ — goal (e) の fixture traversal は「挿入点確定・未実装」、他は受入済み**

- **このセッションで受入まで到達したもの（ユーザー報告 7 件 + 構造/性能）**:
  entity/item の不可視・明るい entity/雲・**文字間アーティファクト（26/36→0・0.23 µs/glyph）**・
  **block entity の z-fighting（groups 48 対 0・mean|d| 18.75）**・**ホットバー暗転（上流参照一致）**・選択枠・clip/BH-10（probe）・
  **動画設定（Celeritas 画面が開き捕捉される）**・**較正済み picture gate PASSED**・
  OPT-05・OPT-07(b)・**OPT-09（緑 leg・`match=true`）**・BH-22・BH-32・light-params（416→17/425→2）・ring-pressure（1546/2419→3/6306）・
  **bind short-circuit（1,627,482 skip）**・**semaphore VUID 10件→0件**・**per-image present-wait semaphore（実 resize 跨ぎ・VUID 0）**・
  層あり回帰 leg VUID 0。
- **未実装（挿入点まで確定・これだけ）**: goal (e) の fixture traversal —
  `celtium$fieldValue(screen, {"controller", …})` → `pages` → `getGroups()` → `getOptions()` で `Option` リストを作り、
  既存の `celtium$vsFindOptionButton`/`vsRowNames`/`vsOptionName` に渡す。clouds の control action を呼び `saveOptions()`。
  **`videoSettingsRowNames` が実名を返すこと**が進捗の証拠。その後に **4 phase**（506→368→復元→reload 行）。
- **その他の未完（正確に）**: frame-close pairing（`submitSignalOnly` は fixture で到達不能・`signalOnlySubmissions` 可視化済み）・
  BH-14 per-identity pair（site 地図あり）・`scissorCapturedGroups=0`（plan の `>0` が失敗）・主要mod対応（goal C）・
  Intel UHD630 受入・パノラマの白（mirror が白・7仮説棄却・`readImage==passImage`）・F3 fast path（決定・不採用）。
- **fixture の規律（この session で確定・4回の同型失敗から）**: leg を打つ前に **(a) scene の定義と較正条件 (b) capture の発火条件
  (c) その scene が読む widget 構造** を原典で確認する。**各段の失敗メッセージがどの gate かを名指しする**ので次の実装対象は artifact から決まる。

## 0.335 **round 142: traversal の統合点を確定（既存 helper の位置）— 次 unit は「ここに controller 経路を足す」だけ**

- **コード確認（Lead）**: chain の既存 helper と統合点は
  **`celtium$vsFindOptionButton(list)`（`:6084`・`:6150`・`:6204` で使用）**・**`celtium$vsRowNames(list)`（`:6088`）**・**`celtium$vsOptionName(option, button)`（`:6096`）**、
  行の取得は `celtium$fieldValue(screen, {"optionsRowList","field_146501_h"})` → **`buttonList`/`field_146292_n` fallback（`f972ef381`）**。
  ＝**controller 経路を足す場所はこの3 helper の入口**で、**`list` を作るところを「`controller.pages` から `Option` を集めたリスト」に差し替える**のが最小の形。
- **次 unit の具体形（これだけ・§0.334 の6手順と対）**:
  1. `celtium$fieldValue(screen, {"controller", "field_..."})` で controller を取得
  2. controller の `pages`（`List<OptionPage>`）→ 各 `getGroups()` → `getOptions()` を走査し、
     **`Option` のリスト（＋その `getControl()`）を作る**（`celtium$vsFindOptionButton` が扱える形に揃える）
  3. 既存の token 照合で clouds の option を選び、**control の action を呼ぶ**（`CyclingControl` は1つ進める）
  4. `GameSettings.renderClouds` の変化を確認し `saveOptions()`
  5. **`videoSettingsRowNames` に実名を出す**（`none` が消えることが進捗の証拠）
- **受入（その後）**: 同一 leg で 4 phase。
- **現状の区別（再掲・正確に）**: 画面が開く・捕捉される＝**受入済み**／`optionsRowList` gate＝**通過**／`buttonList` gate＝**通過せず**／
  **controller traversal＝統合点まで確定・未実装**／4 phase＝**未取得**。

## 0.334 **round 141: 引き継ぎ — goal (e) の fixture traversal は「明細化済み・未実装」で次 unit に固定**

- **このセッションで確定した goal (e) の現状（正確に区別する）**:
  | 項目 | 状態 |
  |---|---|
  | Celeritas 画面が開く | **受入済み**（`guiClickDivertedTo`・`guiScreenClassAtCapture=CeleritasVideoOptionsScreen`・panel crop 587 色） |
  | 画面が捕捉される | **受入済み**（上と同時に取得） |
  | `optionsRowList` gate | **通過**（`buttonList` fallback・`f972ef381`） |
  | `buttonList` gate | **通過せず**（Celeritas の行は `GuiButton` ではない・`videoSettingsRowNames=none`） |
  | **`controller` → `pages` traversal** | **明細化済み・未実装**（§0.333 の6手順・API 面確定） |
  | 4 phase（変更/復元/reload） | **未取得**（traversal 待ち） |
- **次 unit の手順（再掲・これだけ実装すれば進む）**:
  1. `screen.controller` を取得（field 名は原典で確認）
  2. `controller.pages` → `getGroups()` → `getOptions()` を走査
  3. clouds の `Option` を `getName()`/既存 token 照合で特定
  4. **その control の action を呼ぶ**（`CyclingControl` は値を1つ進める・`GuiButton.mousePressed` ではない）
  5. `GameSettings.renderClouds` の変化を確認し `saveOptions()`
  6. **`videoSettingsRowNames` に実名を出す**（`none` が消えることが進捗の証拠）
- **受入（その後）**: 同一 leg で 4 phase（`partialColorMaskFields` 506→368→復元→GUI scale の `REQUIRES_RENDERER_RELOAD` 行）。
- **このセッションの教訓（fixture 側・4回の同型失敗から）**: **leg を打つ前に (a) scene の定義と較正条件、(b) capture の発火条件、
  (c) その scene が読む widget 構造を原典で確認する**。各段の失敗メッセージがどの gate かを名指しするよう fixture を直したので、
  次の実装対象は artifact から決まる（推測しない）。
- **他に残る未完**: frame-close pairing（`submitSignalOnly` は fixture で到達不能・counter 可視化済み）・BH-14 per-identity pair・
  `scissorCapturedGroups=0`（plan の `>0` が失敗）・主要mod対応（goal C）・Intel UHD630 受入・パノラマの白（mirror が白・7仮説棄却）。

## 0.333 **round 140: traversal に必要な API 面を確定（`OptionPage.getGroups()` → `OptionGroup.getOptions()` → `Option.getControl()`/`getName()`）**

- **原典確認（Lead・upstream-baseline と Sodium の構造）**: 行を辿る API は
  **`OptionPage.getGroups()` → `OptionGroup.getOptions()` → `Option.getControl()` / `Option.getName()`** で、
  widget は **`Control.createElement(Dim2i)`** が作る `ControlElement`（`CyclingControl` は enum を巡回・`SliderControl`/`TickBoxControl` が同階層）。
  Celeritas 側の対応物は **`CeleritasVideoOptionsController.pages`**（`List<OptionPage>`・`OptionPageFrame` が描画を担う）。
- **押す操作の意味**: `GuiButton.mousePressed` ではなく **`ControlElement` 自身の入力処理**（`CyclingControl` は値を1つ進める）。
  ＝**fixture は「行を見つける」だけでなく「control の action を呼ぶ」必要がある**（多段 traversal＋control 呼び出し）。
- **実装の明細（gui-stage へ渡す unit として確定）**:
  1. `screen.controller` を取得（field 名は原典で確認）
  2. `controller.pages` → `getGroups()` → `getOptions()` を走査
  3. **clouds に対応する `Option`** を `getName()`（または既存の token 照合）で特定
  4. **その control の action を呼ぶ**（`CyclingControl` なら値が1つ進む）
  5. **`GameSettings.renderClouds` の変化を確認**し `saveOptions()`
  6. **`videoSettingsRowNames` に実名を出す**（今は `none`）＝進捗の証拠
- **受入（この unit の後）**: **同一 leg で 4 phase**（変更→`partialColorMaskGroups` 506→368→復元→reload 行）。
- **現状**: goal (e) の fixture は**3段の gate の3段目**で、**必要な API 面と手順を全て確定**（実装待ち）。

## 0.332 **round 139: 行の在処を確定 — `CeleritasVideoOptionsController.pages`（`List<OptionPage>`）を辿る必要がある**

- **原典確認（Lead・upstream-baseline）**: `CeleritasVideoOptionsController` の field は
  ```java
  private final List<OptionPage> pages = new ArrayList<>();
  private final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
  private final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);
  ```
  ＝**行は `pages`（`OptionPage` のリスト）**にあり、`OptionPage` → groups → options → **control**（`ControlElement`）という**多段の構造**。
  Sodium 側の同型構造も確認済み（`OptionListWidget` が `ControlElement` を並べ、`CyclingControl` は enum を巡回・`SliderControl`/`TickBoxControl` が同階層）。
- **fixture に必要な実装（前ラウンドの2行 fallback より大きい・明細化する）**:
  1. `screen.controller`（`field` 名は原典で確認）を取得
  2. `controller.pages` を走査 → 各 `OptionPage` の groups → options
  3. **`RENDER_CLOUDS`/clouds に対応する option** を見つける（Sodium の Quality page に `options.renderClouds` がある＝構造で確認済み）
  4. **その control の action を呼ぶ**（`GuiButton.mousePressed` ではない＝**control 自身の入力処理**。`CyclingControl` は値を1つ進める）
  5. 値の変化を `GameSettings.renderClouds` 側で確認し、`saveOptions()` する
- **`videoSettingsRowNames` が実際の行名を返すこと**が次の進捗の証拠（今は `none`）。
- **現状**: goal (e) の fixture は**3段の gate の3段目**（`controller` → `pages` → 行）に到達。
  **各段の失敗メッセージがどの gate かを名指しする**ので、次の実装対象は artifact から決まる。

## 0.331 **round 138: 読むべき field が確定 — `CeleritasVideoOptionsScreen` は `controller`（`CeleritasVideoOptionsController`）に行を持つ**

- **原典確認（Lead・upstream-baseline の `CeleritasVideoOptionsScreen`）**:
  ```java
  private final GuiScreen prevScreen;
  private final CeleritasVideoOptionsController controller;   // ← 行はここ
  ```
  ＝**Celeritas 画面は行を `GuiScreen.buttonList` ではなく自前の `controller` に持つ**（`createExtraTabs`/`applyFlagSideEffects` も同クラスで override）。
- **chain に足す経路（次の実装・小さく明確）**: `celtium$fieldValue(screen, new String[] {"controller", "field_..."})` で
  **`CeleritasVideoOptionsController` を取得**し、その**行（`ControlElement` 相当）を列挙して `RENDER_CLOUDS` に相当する行を押す**。
  ＝**`videoSettingsRowNames` が実際の行名を返すこと**が次に進んだ証拠。
- **fixture 側の進捗（3段の gate が artifact で見える）**:
  1. `optionsRowList` 不在 → **`buttonList` fallback で通過**（`f972ef381`）
  2. `buttonList` に行が無い（`videoSettingsRowNames=none`）→ **`controller` を読む**（本ラウンドで field を確定）
  3. 次の leg で **`controller` から行を拾えるか**を確認
  ＝**各段で失敗メッセージがどの gate かを名指しする**ので、次に読む field が artifact から決まる（推測しない）。
- **未了は変わらず**: frame-close pairing・BH-14・**goal (e) 4 phase（fixture は3段目）**・`scissorCapturedGroups=0`・主要mod対応・Intel UHD630。

## 0.330 **round 137: buttonList fallback で最初の gate を通過 — 次の gate は「Celeritas 画面の row list に option button が無い」**

- **実装（Lead・`f972ef381`）**: chain が `optionsRowList` 不在時に **`buttonList`/`field_146292_n` へ fallback**（build 緑）。
- **測定（`r129-e`・fallback 入り・`scene=videoSettings`＋`videoSettingsOption=RENDER_CLOUDS`・150 s）**:
  ```
  videoSettingsChainScreen = org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen
  videoSettingsChain       = no option button could be located in the screen's row list
  videoSettingsRowNames    = none
  guiScreenClassAtCapture  = org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen
  ```
  ＝**最初の gate（`optionsRowList` 不在）は通過**（`no optionsRowList` の報告は消えた）。**次の gate は `videoSettingsRowNames=none`**
  ＝**Celeritas 画面の `buttonList` に option 行が入っていない**（行は `GuiButton` ではない）。
- **原因（構造）**: Celeritas/Sodium 型の画面は行を **`ControlElement`** として**自前の widget container**（Sodium の `OptionListWidget` 相当）に持ち、
  `GuiScreen.buttonList`（`GuiButton` 専用）には入れない。＝**次に読むべきは Celeritas 画面自身の widget container の field**。
- **次の1点**: `CeleritasVideoOptionsScreen` の field を原典（upstream-baseline）で確認し、**その container から `ControlElement` を拾う経路**を chain に足す
  （`videoSettingsRowNames` が「none」でなく実際の行名を返すことが、次に進んだ証拠）。
- **進捗の記録**: goal (e) の fixture 側は **「2つ目の gate まで到達」**（`optionsRowList` → `buttonList` → **Celeritas 自前 container**）。
  各段で**失敗メッセージがどの gate で止まったかを名指しする**ので、次に読むべき field が artifact から決まる。

## 0.329 **round 135: Celeritas 画面の widget 構造を確認 — `optionsRowList` ではなく自前の `OptionListWidget`/`ControlElement` を使う**

- **原典確認（Lead・upstream-baseline の `CeleritasVideoOptionsScreen` ＋ Sodium の GUI 構造）**:
  Celeritas の画面は**自前の option framework**（`Tab`/`OptionPage`/`Group`/`Option`/`Control`→`ControlElement`）で構築され、
  行は **`OptionListWidget`** に **`ControlElement`** として並ぶ（[Sodium の Options GUI 構造](https://deepwiki.com/CaffeineCeleritas/sodium/5.1-options-gui)・
  [SodiumGameOptionPages](https://github.com/CaffeineMC/sodium/blob/ed6c1afe/common/src/main/java/net/caffeinemc/mods/sodium/client/gui/SodiumGameOptionPages.java)）。
  ＝**vanilla の `optionsRowList` は存在しない**（`videoSettingsChain` の報告どおり）。
- **fixture に必要な実装（小さく明確）**: `ValidationClient` の chain に
  **Celeritas 画面の widget list（`OptionListWidget` 相当）から `ControlElement` を拾う経路**を足し、
  **`RENDER_CLOUDS` に相当する control を押す**（Sodium の Quality page に Clouds がある＝`SodiumGameOptionPages` の構造で確認可能）。
  ＝**`optionsRowList` 依存を「vanilla 画面のときだけ」に限定し、Celeritas 画面では自前 list を読む**のが正しい形。
- **`guiScreenVerdict` の文言も同様**: 現在は `GuiVideoSettings was open at the capture` という**vanilla 前提の文言**なので、
  **Celeritas 画面のときはその旨を出す**よう揃える（同じ gap の一部・判定と実態の不一致を残さない）。
- **受入（この実装の後）**: **同一 leg で 4 phase** — (1) `RENDER_CLOUDS` を変更 (2) **`partialColorMaskGroups` が 506→368 に動く**（native 側の counter）
  (3) 元の値へ復元し **counter が 506 に戻る** (4) **GUI scale の `REQUIRES_RENDERER_RELOAD` 行**を押して frame が壊れないこと。
- **現状**: goal (e) は**「画面は開き捕捉される」まで受入済み**、**「変更・復元・reload」は fixture gap を特定済み**（実装待ち）。

## 0.328 **round 134: 決定的 — `pinSections` が capture を止めていた（仮説確認）・chain は Celeritas 画面に `optionsRowList` が無いため押せない**

- **測定（Lead の `r126-e`・`pinSections` を外した同一 flag・`scene=videoSettings`＋`videoSettingsOption=RENDER_CLOUDS`・150 s）**:
  ```
  guiScreenClassAtCapture = org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen
  guiScreenCurrent        = CeleritasVideoOptionsScreen
  guiScreenOpened         = GuiOptions  (atTick=201・via Minecraft.displayGuiScreen(new GuiOptions(...)))
  guiScreenVerdict        = GuiVideoSettings was open at the capture
  videoSettingsChain      = the captured screen has no optionsRowList to press
  ```
  ＝**(1) `pinSections` が capture を保留していたという仮説は確認**（外したら chain が走った）。
  **(2) chain は `the captured screen has no optionsRowList to press` で止まる**＝**captured screen は Celeritas 画面**（`guiScreenClassAtCapture` が名指し）であり、
  **Celeritas 画面は `optionsRowList` を持たない**ため**option を押せない**。
- **これは gui-stage が予測した fixture gap そのもの**: **panel gate は `optionsRowList` を読む**＝**vanilla の `GuiVideoSettings` にしか無い**。
  ＝**goal (e) の「設定を変更して native frame が変わる」を chain で取るには、Celeritas 画面自身の layout を読む実装が要る**。
  ＝**renderer の gap ではなく fixture の gap**（gate を下げて通すのは不可）。
- **現時点で goal (e) について確定したこと**:
  - **Celeritas 画面は開き・捕捉されている**（`guiScreenClassAtCapture` が名指し・panel crop 587 色）
  - **`guiScreenVerdict` は vanilla の文言**（`GuiVideoSettings was open`）＝**判定文言も Celeritas 画面に対応していない**（同じ gap の一部）
  - **chain は Celeritas 画面の row list を読めない**ため**変更・復元の phase に進めない**
- **次の実装（fixture・小さく明確）**: **Celeritas 画面の row list を読む経路を `ValidationClient` に足す**
  （`SodiumGameOptionPages`/`CeleritasVideoOptionsScreen` の layout を原典で確認し、`optionsRowList` の代わりにその list を使う）。
  その後 **同じ leg で 4 phase（変更→native frame の counter→復元→reload 行）**を取る。

## 0.327 **round 133: video-settings chain の report site を確認 — chain は capture 時に走るため `pinSections` が capture を止めていた可能性**

- **コード確認（Lead）**: chain は capture の時点で走り、その report site は
  **`videoSettingsChainError`・`videoSettingsChainScreen`・`videoSettingsChainTitle`・`videoSettingsChain`・`videoSettingsOptionName`**（`:6059-6091`）。
  `videoSettingsChain` は「**captured screen に optionsRowList が無い**」または「**row list に option button が見つからない**」という**理由文字列**も持つ。
  ＝**私の `r123-e` の report にはこれらが1つも無い**＝**chain のコードが走っていない**（`celtium$videoSettingsExercised` `:823` が立っていない）。
- **次の確認点（1つ・コードを読めば決まる）**: **`pinSections` が capture を保留したまま 150 s が終わった可能性**。
  `celtium$captureDue()` は `pinSections > 0` のとき **section 数が安定して n 以下になるまで capture しない**ため、
  **`pinSections=60` が capture を止めれば chain も走らない**。＝**video-settings の leg は `pinSections` 無し（または緩い閾値）で打つべき**。
  ＝**scene の chain は「capture 時」に走る**という前提を、次に leg を打つ前に確認する。
- **この round の規律の追補**: **「scene が何を必要とするか」に加えて「capture がいつ発火するか」を原典で確認する**
  （`captureDue` の条件と chain の発火点が別々にあるため）。＝**同型の失敗4回目を避けるための記録**。
- **未了は変わらず**: frame-close pairing・BH-14・**goal (e) 4 phase（leg は capture 未発火）**・`scissorCapturedGroups=0`・主要mod対応・Intel UHD630。

## 0.326 **round 132: `scene=videoSettings` の定義を原典で確認 — `VIDEO_SETTINGS("videoSettings", true, true)`＋較正は **guiScale=3・固定 pose** 前提**

- **コード確認（Lead）**: `ValidationClient` の scene 列挙は **`VIDEO_SETTINGS("videoSettings", true, true)`**（`:356`・**2つの真偽値＝GUI 保持と entity 捕捉**）。
  較正 gate（`celtium$videoSettingsGate()` `:1123`）が要求する条件:
  **`scene=videoSettings` at x=0.5 y=12.0 z=0.5 yaw=180 pitch=20・1280x720・`guiScale=3`**、
  world frame は `screenshots/celtium-validation-world.png`、GUI frame は `screenshots/celtium-validation.png`、
  **画面は `GuiVideoSettings`**（GL bridge の参照 run 2本 `scene-floors/video-a`・`video-b`）。
  ＝**gate 自身が「別の guiScale の run は uncalibrated として報告する」と明記**している。
- **Lead の `r123-e` が field を出さなかった理由の候補（次の確認点）**: 私の leg は `pinSections` と `videoSettingsOption` は渡したが、
  **scene の pose/guiScale 前提を満たしているかを確認していない**（scene 定義が自動で設定するのか、flag が要るのか）。
  ＝**推測で flag を足さず**、`celtium$videoSettingsGate` が読む field（`guiScale`・pose・`videoSettingsOption` の chain 前提）を
  **原典で確認してから leg を打ち直す**。
- **この round の規律（3回の同型失敗の後に確定）**: **leg を打つ前に、その scene の定義（enum の真偽値・gate の較正条件・必要な pose/scale）を原典で読む**。
  ＝resize（`resizeAtTick`・`resizeViaCompositor`）と video-settings で同じ形の失敗を繰り返しているので、**手順として固定する**。

## 0.325 **round 131: goal (e) の leg を取得したが report に video-settings の field が出ていない（受入は未取得）**

- **測定（Lead の `r123-e`・`scene=videoSettings`＋`videoSettingsOption=RENDER_CLOUDS`＋`pinSections=60`＋`dumpPresentedImage`・150 s）**:
  `exitCode=143 / endedBy=time-limit`（harness の duration 上限＝正常終了）だが、**report の field が `screenAtExit=none` のみ**で、
  `videoSettingsOption`・`videoSettingsChain`・`videoSettingsRestored`・`guiScreenClassAtCapture`・`partialColorMaskGroups` が**1つも出ていない**
  （console にも `partialColorMaskGroups` が 0 行）。
  ＝**scene の video-settings 経路が走っていない**（field 名の不一致か、scene の前提が欠けている）。**受入は未取得**として記録（緑を以て受入としない）。
- **次の1点**: **`scene=videoSettings` の経路が何を必要とするか**を `ValidationClient` の該当分岐で確認する
  （gui-stage のレシピは「chain が既に着地している」前提だったので、**leg 側の前提**（例: `videoSettingsOption` の名前・
  scene の別名・`keepGui` の要否）を原典で確かめる）。**推測で flag を足さない**。
- **記録の価値**: 「レシピは揃っているが leg が経路に入らない」という**同型の失敗**をこの session で既に3回踏んでいる
  （resize の `resizeAtTick` 欠落・`resizeViaCompositor` 既定 false・今回の video-settings scene）。＝**leg を打つ前に
  「その scene が何を必要とするか」を原典で確認する**のが次の規律として有効。
- **未了は変わらず**: frame-close pairing・BH-14・**goal (e) 4 phase（レシピ記録済み・leg は経路未到達）**・`scissorCapturedGroups=0`・主要mod対応・Intel UHD630。

## 0.324 **round 130: goal (e) の leg レシピを記録（gui-stage が budget 切れで辞退・4 phase の flags と読み方）**

- **base flags（全 phase で同一 scene/camera・両 half に `pinSections`）**:
  `--validate`＋`pinSections=<n>`＋`scene=videoSettings`＋`glless=true`＋`presentReadback=true`＋`dumpPresentedImage=true`＋`videoSettingsOption=RENDER_CLOUDS`。
  **`resizeViaCompositor` は (e) では不要**（有効にすると変数が増える）。
- **Phase 1（変更）**: `RENDER_CLOUDS` を Fancy → OFF（画面自身の dispatch 経由）＋`saveOptions()`。既存 chain が `videoSettingsChain` を記録する。
- **Phase 2（native frame が変わる）**: **counter が正直な読み**（picture floor が boot-to-boot 23.5 % のため）:
  **`partialColorMaskGroups` 506（Fancy）対 368（非 Fancy）**＝BH-10 が着地させた colour-mask 経路で**picture floor の影響を受けない**。
  加えて**雲の見える sky region の crop**（座標つき）。
- **Phase 3（復元）**: chain が**元の値に一致するまで押す**（8 回上限）＋`videoSettingsRestored` の **`(restored)`/`(NOT restored)` の接尾辞を読む**
  （**field 名ではなく**・修正前は値が循環しているのに "restored" と出ていた）。frame は phase 1 と比較し、**pixel hash より counter が 506 に戻ることを優先**。
- **Phase 4（`REQUIRES_RENDERER_RELOAD`）**: **GUI scale** が適切な行（この session が測った scissor/viewport 経路も動く）。
  **名指しすべき gap**: **panel gate は `optionsRowList` を読む**＝**vanilla の `GuiVideoSettings` にしか無い**ため、
  `:6198`/`:6699` が `GuiVideoSettings` に固定されたまま。＝**reload 行は Celeritas 画面で「押せる」が、その panel 統計は既存コードでは gate できない**。
  **Celeritas 画面自身の layout 読みが要る**＝**fixture の gap であり renderer の gap ではない**（gate を下げて通さない）。
- **記録に持ち込む2点（gui-stage の指示）**:
  1. **`guiScreenOpenedVia` は Celeritas 画面を決して名指ししない**（fallback text を運ぶ）。**`guiClickDivertedTo`（divert）と `guiScreenClassAtCapture`（picture）**を読む。
     ＝`guiScreenOpenedVia` に書かれた受入は**永久に失敗として読まれる**。
  2. **139 色の panel 数値は vanilla 画面のもの**（Celeritas は 587 色）。**2画面の panel 統計を比較しない**（「異なる画面の比較」は未検証の row より価値が低い）。
- **担当**: gui-stage は budget 切れで**どちらの leg も取らない**（重複リスク無し）。chain・restore 修正・capture 受入は既に着地済み（`450265208` ほか）なので、**leg は flags と読みの問題**。

## 0.323 **round 129: F3 の split 確定（glyph-fix `7434a950b`）— caller は 0.13 %、vanilla glyph 本体が 3.15 µs/glyph**

- **測定（`rsplit-on`・F3 on・1,280,000 glyph・`renderString` 3,106 回・同一 leg に両 bracket）**:
  | bracket | millis | per unit |
  |---|---|---|
  | `renderStringMillis`（loop・全入口） | **4,663** | 1.50 ms/string |
  | `glyphMillis`（vanilla glyph メソッド） | **4,657** | 3.64 µs/glyph |
  | 我々の capture（submit 328 + window 69 + hook 223） | **620** | 0.48 µs/glyph |
  | **caller 自身の仕事** | **6** | ~2 µs/string（**0.13 %**） |
  | **vanilla glyph 本体** | **4,037** | **3.15 µs/glyph** |
  - **pair**（同一 jar・両 leg `pinSections=64`・両 leg PASSED・`validationClean=true`・`validationMessageCount=0`）:
    cold p50 **177.9 ms（on・21 標本）対 41.5 ms（off・74 標本）**＝**within-pair の overlay コスト 136.4 ms/frame**（前 pair の 128–139 と整合）。
- **結論（決定であって task ではない）**: caller 側（`charmap.indexOf`・style 管理）は **6 ms / 4,663** ＝ **0.13 %**。
  残りは **vanilla glyph 本体 3.15 µs/glyph ≈ 14.5 ms/frame** で、**glyph ごとに約10個の Java 呼び出し**（`TextureManager.bindTexture` ＋ 9 個の `GlStateManager` wrapper＝各 stub no-op ＋ 我々の hook）。
  ＝**`renderStringAtPos` fast path の賞金は ~14.5 ms/frame（最も密な text scene で）**であり、
  **game/Forge/mod が共有するコード経路を迂回する**ため**独自の互換ストーリーが必要**。
  ＝**この session では開始しない**（互換リスクを明示した決定として記録）。
- **次の読者のための記録（glyph-fix が commit に同梱）**: 外側の `drawString` bracket（`func_175065_a`）は**この split には誤った scope**
  （`renderStringAligned` が迂回するため `stringMillis − glyphMillis` が ~0 になった）。残る per-glyph 本体は **vanilla ＋ 我々の stub dispatch** であり、
  **Celtium 所有の連鎖ではない**（bind 連鎖は ~0.07 µs/glyph として既に除去済み）。
- **未確立（正直に）**: **fast path の実際の節約量は未測定**。3.15 µs/glyph は**現行経路のコスト**であり、
  **batched path のコストは測られていない**（glyph-fix も開始していない）。

## 0.322 **round 128: `renderString` bracket の split が出た — caller 側は約 2.5 %、残りは vanilla の glyph 本体**

- **測定（glyph-fix の `rsplit-on`/`rsplit-off`・F3 on leg の集計行）**:
  ```
  rsplit-on : stringMillis=4665 stringCalls=2734 renderStringMillis=4663 renderStringCalls=3106
  rsplit-off: stringMillis=337  stringCalls=581  renderStringMillis=336  renderStringCalls=1076
  ```
  ＝**`renderStringMillis`(4663) ≈ `stringMillis`(4665)**＝bracket が caller を捕捉している。
  **`renderStringCalls=3106` 対 `stringCalls=2734`**＝**372 回が `drawString` を経由せず `renderString` に直接到達**（`renderStringAligned` 等）。
- **split（着地済み short-circuit leg の `glyphMillis=4546` と突き合わせ）**:
  **caller 自身の per-character 仕事 = 4663 − 4546 = 117 ms ≈ 2.5 %**、**残り 4,546 ms は vanilla の glyph 本体**。
  ＝**`renderStringAtPos` fast path の測定された標的は小さい**（caller 側を置き換えても 2.5 % しか動かない）。
- **帰結（判断の材料が揃った）**: F3 の text コストの実体は **vanilla の glyph 本体**（`charmap.indexOf`・`renderDefaultChar` 等）で、
  **Celtium 側で置き換えるには大きな互換リスク**を伴う。caller 側の 2.5 % は fast path の対象として**見合わない**。
  ＝**goal の「根拠のない倍率や達成率を使わない」に従い、F3 の残ラグは「vanilla のフォント本体が支配的」と結論**し、
  **fast path は取らない**（取るなら互換リスクの明示と別 unit として判断）。
- **この session の F3 の成果**: bind short-circuit（1.63M skip・0.07 µs/glyph 除去）＋**split の数値化**＋
  **reporting line（`bindTextureCalls`/`bindShortCircuits`/`immediateStateCalls`）**＝**未説明のまま残さない**。

## 0.321 **round 127: `renderString` bracket を glyph-fix が実装中（leg 実行中・スロット使用中）**

- **状態**: `CeltiumFontGlyphCapture.java` と `FontRendererMixin.java` が更新中（`renderString` = `func_180455_b` の bracket 追加）。
  **leg が1本実行中**（スロット使用中・pid 2942130）。HEAD `5ceda0f99`・**build は直前まで緑**。
- **この bracket の目的（再掲・記録）**: `stringMillis − glyphMillis ≈ 0`（4,533 対 4,546）だったため、
  **`renderStringAligned` 等が `renderString` を直接呼ぶ経路**の per-character 仕事が分離できていない。
  bracket を足して **loop 側（caller）と body 側（vanilla glyph）の split を数値にする**。
  ＝**その split が出るまで `renderStringAtPos` fast path の標的は測られていない**（fast path を主張しない）。
- **受入条件（配布済み）**: 同一 jar・**両 leg に `pinSections`**・split を測定値として報告・
  **fast path についての主張は split を見るまでしない**。split が小さいなら fast path は「大きな互換リスクを持つ決定」として扱い、
  大きいなら測定された標的として次 unit で取る。
- **現状の緑の基準（このセッションの到達点）**: ユーザー報告7件の受入・較正済み picture gate PASSED・
  OPT-05/07(b)/09・BH-22/32・light-params・ring-pressure・**bind short-circuit（1.63M skip）**・**semaphore VUID 10→0**・
  **per-image present-wait semaphore（resize 受入）**・層あり回帰 leg VUID 0。
- **未了**: frame-close pairing（到達不能）・BH-14・goal (e) 3段階・`scissorCapturedGroups=0`・**renderString bracket（実装中）**・
  主要mod対応（goal C）・Intel UHD630 受入。

## 0.320 **round 126: bind short-circuit 着地（`dfeee01cc`）— 連鎖の実測は ~0.07 µs/glyph で 3.17 µs の主因ではない**

- **着地（glyph-fix・`dfeee01cc`・jar `d987b037`・build 緑）**: mixin の早期 return（`VulkanGlState.bindWouldChangeNothing`）＋
  **`bindShortCircuits` を frame counter の隣に出力**＋**reporting line（`bindTextureCalls`/`bindShortCircuits`/`immediateStateCalls`）**＋
  **safety 引数を commit message に**（vanilla の per-unit cache は HEAD 注入の上流で我々に届かない＝二重 filter ではない）。
- **測定（同一 jar・両 leg `pinSections=64`・両 leg `PASSED`・`validationClean=true`・`validationMessageCount=0`）**:
  | | F3 ON | F3 OFF |
  |---|---|---|
  | cold p50 | **171.2 ms** | **43.3 ms** |
  | glyph windows | 1,280,000 | 80,000 |
  | **`bindShortCircuits`** | **1,627,482** | 100,048 |
  | `bindTextureCalls`（forward） | **1,587** | 3,801 |
  | `immediateStateCalls` | 72,188 | 117,034 |
  ＝**skip が設計どおり発火**（1,627,482 skip 対 1,587 forward・変更前は 1,628,711 全 forward）。
- **連鎖の大きさ（既存計器で・新しい timer 無し）**: 同じ 1,280,000 glyph で `glyphMillis` が **4,635 ms → 4,546 ms**（**3.62 → 3.55 µs/glyph**）＝
  **skip 可能な連鎖は ~0.07 µs/glyph ≈ 0.3 ms/frame**（4,600 glyph/frame）。＝**連鎖は 3.17 µs の主因ではない**。
- **frame time の正直な扱い**: ON leg の p50 は前 pair の ON（187.3）より 16 ms 良いが、**OFF leg も同 session で 4.5 ms 改善**しているため
  **session を跨いだ帰属は不可**として**主張しない**。**within-pair では overlay のコストは 128 ms/frame**（171.2 − 43.3）。
- **次の bracket（Lead が承認・glyph-fix へ配布）**: **`renderString` 自身（`func_180455_b`）**を bracket する
  （`stringMillis − glyphMillis ≈ 0` のため caller の per-character 仕事が分離できていない）。
  その split が出て初めて **`renderStringAtPos` fast path に測定された標的**ができる。**split が出るまで fast path を主張しない**。

## 0.319 **round 125: bind short-circuit は glyph-fix が実装中（ツリーは一時的に赤・原因を名指しして依頼）**

- **状態**: `VulkanGlState.java` に **`bindWouldChangeNothing`** が入り、`GlStateManagerMixin.java` はまだ新名を参照していない＝**mid-unit**。
  Lead の build は **赤**で、原因は `VulkanTerrainGpuBackend.java:3553` の **Javadoc が `bindShortCircuits` を参照**しているのに**その field がまだ無い**こと
  （`symbol: variable bindShortCircuits`）。
- **対応**: **glyph-fix の作業中なので revert しない**（他担当の有効な変更を保持する規則）。原因行を名指しして依頼済み。
  着地時に期待する要素を明示: mixin の `VulkanGlState.bindWouldChangeNothing(glTextureId)` による早期 return・`bindShortCircuits` の宣言と frame counter 隣への出力・
  **safety 引数を commit message に**（per unit で mirror が既に値を持つ＝no-op・vanilla の cache は HEAD 注入の上流で我々を止められない）・
  同一 jar の F3 on/off pair に `pinSections`・可能なら `bindTextureCalls`/`immediateStateCalls` の reporting line。
- **記録**: 私の誤読（印刷行数 vs `bindsOnUnit0`）と unit 再開は §0.318（`97dddc900`）。**ツリーが赤いのは実装中の一時状態**であり、
  **未検証のまま放置しない**ことを着地条件に含める。

## 0.318 **round 124: 自分の測定の誤りを訂正 — 到達数は「印刷行数」ではなく `bindsOnUnit0`（1,628,711 / 1,280,000 glyph）＝hook は glyph ごとに走る**

- **glyph-fix の反証（正しい）**: 私が数えた **248 は bounded な `CELTIUM_UNIT_BIND` の「印刷行数」**であり、
  **到達数は同 line が持つ `bindsOnUnit0`**。実測は:
  | leg | glyph windows | `bindsOnUnit0` |
  |---|---|---|
  | `f3split`（F3 on） | **1,280,000** | **1,628,711** |
  | `f3perf-on`（F3 on） | **1,020,000** | **1,011,627** |
  ＝**到達は glyph 数とほぼ 1:1**。**我々の hook は glyph ごとに走っている**。
- **なぜ二つの読みが食い違ったか（記録）**: vanilla の per-unit cache が short-circuit するのは **stub に届く `GL11.glBindTexture`** であり、
  **我々の mixin は `func_179144_i` の method HEAD に注入**されているため **cache hit でも hook は走る**。
  ＝**「hook は texture が変わったときだけ走る」は stub については真、hook については偽**（別サイト）。
  ＝**私の「stop」は誤読に基づいていた**。hook 内の skip は vanilla の filter の二重適用ではなく、**我々自身の冗長仕事の除去**。
- **対応**: glyph-fix に**unit を再開して着地させる**よう送付済み（`bindWouldChangeNothing(glTextureId)` で mirror 呼び出し前に return・
  `bindShortCircuits` を frame counter の隣に出力・**safety 引数を commit message に**・同一 jar の F3 on/off pair に `pinSections`）。
  併せて **`bindTextureCalls`/`immediateStateCalls` の reporting line** も同 unit で（leg ごとに連鎖の大きさが見えるように＝私が今犯した誤りの再発防止）。
- **未確立（正直に）**: **到達が glyph ごとであることは測定済み**、**到達あたりのコストは未測定**
  （最hot な hook に timer を入れる必要があり glyph-fix は意図的に作っていない）。**3.17 µs のうち連鎖が何割かは未確定**。
- **教訓（記録）**: **bounded な印字行数を「到達数」と読まない**（`CELTIUM_*_SUMMARY` や counter の値そのものを読む）。
  ＝「counter の不在は描画の不在ではない」と同じ族の誤りを、私がまた犯した。

## 0.317 **round 123: bind 到達回数を測定 — **248 回 / 1,280,000 glyph**＝bind 経路は実質到達していない。3.17 µs/glyph は vanilla の per-character 仕事**

- **測定（Lead・既存 leg `f3split` の `CELTIUM_UNIT_BIND` を数える・新しい leg 不要）**:
  **`CELTIUM_UNIT_BIND` 248 行**に対し、同 leg の **glyph window は 1,280,000**（glyph-fix の報告）。
  ＝**bind 経路の到達率は 0.02 %**。**`TextureManager.bindTexture` → `GlStateManager` → 我々の hook の連鎖は glyph ごとには走っていない**。
- **帰結（問いの答え）**: 3.17 µs/glyph の実体は **vanilla 自身の per-character 仕事** —
  `renderStringAtPos` の **約200文字テーブルへの `charmap.indexOf`（1文字ごと）**と `renderChar`/`renderDefaultChar` の本体であり、
  **我々の hook でも bind でもない**。
  ＝**§0.315 の「bind short-circuit」unit が不要だったことが二重に確定**（(1) vanilla が既に short-circuit 済み、(2) bind 到達は 248 回）。
  ＝**我々が触れないコードが F3 のコストの大半**であることも確定（overlay の 139 ms のうち text 経路 ~22 ms・その大半が vanilla 側）。
- **次の判断（記録）**: F3 の残ラグに対して Celtium 側でできるのは
  (a) `renderStringAtPos`/`renderDefaultChar` への **Celtium 側 fast path**（vanilla の per-character 仕事を置き換える＝**大きい変更・互換リスク**）、
  (b) **per-frame の overlay 構築**（`GuiOverlayDebug.call()` の約20文字列・`getDebugInfoRenders()`）の削減、
  (c) **何もしない**（vanilla 側のコストは F3 を使う限り避けられない）。
  **現時点の証拠は (b) を支持しない**（per-frame 構築の計測はまだ無い）ので、**(a) は互換リスクに見合うかを見極めてから**着手する。
  ＝**「測ってから直す」を守り、F3 を未説明のまま残さない**ための記録。
- **未了**: frame-close pairing（到達不能）・BH-14・goal (e) 3段階・`scissorCapturedGroups=0`・F3 の (a)/(b) 判断・主要mod対応。

## 0.316 **round 122: 配布の前提を訂正 — vanilla の `bindTexture` は既に short-circuit している（原典で確認）**

- **原典の確認（web 調査・1.12.2 の `GlStateManager.func_179144_i`）**:
  ```java
  public static void func_179144_i(int p_179144_0_) {
     if (p_179144_0_ != field_179174_p[field_179162_o].field_179059_b) {
        field_179174_p[field_179162_o].field_179059_b = p_179144_0_;
        GL11.glBindTexture(3553, p_179144_0_);
     }
  }
  ```
  ＝**vanilla が既に「同じ texture なら GL 呼び出しをしない」を実装している**（1.8 の GlStateManager 導入時からの設計）。
  参照: [GlStateManager（1.9 MCP ソース）](https://github.com/CircuitLord/Minecraft-1.9-MCP/blob/master/temp/src/minecraft/net/minecraft/client/renderer/GlStateManager.java)・
  [1.8 の GlStateManager 解説](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/modification-development/2231761-opengl-calls-glstatemanager)・
  [冗長 state change の一般論](https://community.khronos.org/t/redundant-texture-switches/53432)。
- **訂正（前 round の配布の前提が誤り）**: §0.315 で glyph-fix に「変化しない bind の short-circuit」を依頼したが、
  **それは vanilla に既に存在する**。我々の hook が呼ばれるのは**texture が実際に変わったときだけ**なので、
  **「redundant bind を省く」unit は不要**（実装しても二重の short-circuit になる）。
- **では 3.17 µs/glyph は何か（次の問いの立て直し）**: `TextureManager.bindTexture` → `GlStateManager.bindTexture` の
  **呼び出し連鎖そのもの**（毎 glyph に `ResourceLocation` を引き、`TextureManager` の map を引き、`GlStateManager` の state を読み、
  我々の hook を通る）と、**`renderStringAtPos` の per-character 仕事**（`charmap.indexOf`）。
  ＝**次の実験は「bind が実際に何回 GL/stub に到達しているか」を数えること**（既存の `CELTIUM_UNIT_BIND` があるので**新しい計器は不要**）。
  到達回数が少なければ連鎖の側、多ければ vanilla 側の per-character 仕事が主因と確定する。
- **glyph-fix へ訂正を送付**（誤った unit を走らせない）。

## 0.315 **round 121: F3 の次の unit を配布・主要mod対応（goal C）の互換入口を外部資料で整理**

- **配布（Lead→glyph-fix）**: F3 の次の実験は**自分の数値が指す1点** — `renderDefaultChar` の**毎 glyph の `bindTexture` が我々の hook を通る**ので、
  **変化しない bind の short-circuit**（最後の bind 名を保持し、同一なら stub 呼び出しを省く）＋ counter `bindShortCircuits` を verification summary に出力、
  その上で **同一 jar・`pinSections` 付きの F3 on/off pair**。受入は **cold p50 と glyph 総数＋counter 可視**で、
  **139 ms 全体についての主張はしない**（vanilla 本体 3.17 µs/glyph のうち我々が触れるのは bind の1つだけ）。
- **主要mod対応（goal C）の互換入口（外部資料）**:
  - **JEI の custom-rendered item**: JEI は **`IBakedModel#isBuiltInRenderer`** を見て TESR 扱いにするか単純描画にまとめるかを決める。
    正しい入口は **model の親を `minecraft:builtin/entity` にする**か **`Item#setTileEntityItemStackRenderer`** で、
    これを満たす mod（例: Incorporeal）は JEI で問題なく描画される（[JEI #1347](https://github.com/mezz/JustEnoughItems/issues/1347)）。
    ＝**Celtium が TESR/`TileEntityItemStackRenderer` 経路を正しく通せば、JEI の item 一覧は自動的に正しくなる**（JEI 側に特例を足さない）。
  - **Actinium の互換マトリクス**（Cleanroom 1.12.2・shader pack: MakeUp/BSL/Complementary/Bliss/iterationT を verified、
    SEUS PTGI HRR は未対応と明記）＝**goal E の「対応境界と理由を明記」の先例**（[Actinium](https://github.com/DHJComical/Actinium)）。
  - **Cleanroom の modpack ガイド**（Celeritas は Cleanroom 必須・Nothirium は Naughthirium 併用 等）
    ＝**Celtium の「既存mod対応表」を書くときの比較対象**（[準備ガイド](https://cleanroommc.com/wiki/end-user-guide/preparing-your-modpack)）。
- **現状**: per-image semaphore は**実装済・resize 受入済み**。未了は **frame-close pairing（到達不能）**・BH-14・goal (e) 3段階・
  `scissorCapturedGroups=0`・**F3 の bind short-circuit（今 round 配布）**・主要mod対応（goal C）。

## 0.314 **round 120: resize 受入を取得（`glfwWindowSizeAtEnd=1024x640`・VUID 0）— per-image の最重要項目が閉じた**

- **測定（Lead の `r111-resize`・jar `d744673cc` 系・層あり・`pinSections=60`・`resizeAtTick=200`・`resizeTo=1024x640`・
  **`resizeViaCompositor=true`**・150 s）**:
  ```
  resizeReason          = world
  resizeWindowFrom      = 1280x720
  resizeWindowTo        = 1024x640
  glfwWindowSizeAtEnd   = 1024x640     ← window が実際に resize された
  resizeRequestedAtTick = 200
  VUID                  = 0
  CELTIUM_RESIZE_COMPOSITOR tick=200 why=world mode=1024x640@60Hz exit=0 out=[ { "success": true }
  CELTIUM_RESIZE_REQUEST    tick=200 why=world from=1280x720 to=1024x640
  CELTIUM_RESIZE_OBSERVED   tick=201 from=1280x720 to=1024x640
  ```
  ＝**window が 1280x720 → 1024x640 に実際に変わり**（`CELTIUM_RESIZE_OBSERVED` が 1 tick 後に観測）、
  **swapchain image 数が変わる状況で VUID 0・hang なし**。
- **これが per-image present-wait semaphore の最重要受入項目**: 成長のみの配列が **image 数の変化を安全に跨いだ**
  （Khronos の規則「present wait semaphore は swapchain image 数で大きさを決め acquired index で引く」の検証）。
- **前ラウンドまで経路に入れなかった原因（2段）**: (1) `resizeTo` だけでは guard が動かない（`resizeAtTick` が必要）、
  (2) `resizeViaCompositor` は既定 **false**（`Boolean.getBoolean`）で、**tiling compositor は GLFW の要求だけでは window を変えない**。
  ＝**2つの flag を両方立てて初めて経路に入り、`glfwWindowSizeAtEnd` が要求値に一致した**。
- **ユニットの残り**: **frame-close の pairing**（`submitSignalOnly`・この fixture では到達不能・`signalOnlySubmissions` は可視化済み）。
  ＝**per-image は「実装済・通常経路4 leg 緑・resize 受入済み・frame-close のみ未取得（到達不能）」**。

## 0.313 **round 119: 前ラウンドの結論を訂正 — `resizeAtTick` guard は発火していた（`resizeRequestedAtTick=200`）・止めているのは compositor**

- **訂正（重要・自分の誤り）**: §0.312 で「guard が発火していない」と書いたが、**それは誤り**だった。原因は**私が harness の lock JSON
  （`components`/`mods`/`libraries` しか持たない `.celtium-work/r109-resize.json`）を読んでいたこと**で、
  **validation report は `runtime/<leg>/logs/celtium-validation.json`** にある。そちらを読むと:
  ```
  worldTicksAtEnd        = 2664
  resizeRequestedAtTick  = 200      ← guard は発火している
  glfwWindowSizeAtEnd    = 1280x720 ← window は変わっていない
  ```
  ＝**guard は tick 200 で発火し、`celtium$requestResize` は呼ばれている**。**変わっていないのは window size**。
- **帰結（gui-stage の予測どおり）**: 止めているのは **fixture ではなく compositor**。
  gui-stage の言葉: 「`resizeRequestedAtTick` があって 1280x720 なら、request は発火して **compositor が従っていない**。
  次に見るのは **`resizeOutput`（既定 `HEADLESS-1`）が実際の sway output 名と一致するか**」。
  ＝**resize 受入の次の1点は `resizeOutput` の実名合わせ**（fixture の変更ではなく環境側の設定）。
- **教訓（記録）**: **harness の lock JSON と validation report は別物**で、field を探すときは後者を読む。
  §0.312 の「field が無い＝経路未踏」という推論は**読むファイルを間違えたことによる誤り**だった。
  ＝**「計器の不在は描画の不在ではない」と同じ形の誤りを、私自身が犯した**ので、ここに残す。
- **ユニットの状態（更新）**: per-image は**実装済・通常経路は4 leg 緑**。**resize 受入は「経路は踏んだが compositor が resize を適用していない」**
  段階まで進んだ（未取得であることに変わりはない）。**frame-close の pairing** は fixture で到達不能のまま（counter は可視化済み）。

## 0.312 **round 118: `resizeAtTick` guard が発火していないことを artifact field で確定（受入は未取得）・外部知見の追補**

- **確定（コード＋artifact）**: `celtium$requestResize` は `:5264-5265` で **`resizeRequestedAtTick` を report に書く**。
  Lead の `r109-resize`（`resizeAtTick=200`・`resizeTo=1024x640`・150 s・VUID 0）の report に **その field が無い**
  ＝**`celtium$requestResize` が一度も呼ばれていない**＝**guard（`:3001` の `resizeAtTick > 0 && !resizeRequested && worldTicks >= resizeAtTick`）が発火していない**。
  ＝**resize 受入は依然として未取得**（緑を以て受入としない）。
- **次の確認（1点）**: 同 leg の **`worldTicksAtEnd`** を読み、`resizeAtTick=200` に到達しているかを確認する。
  到達していれば guard の**外側の条件**（どのコードパスに居るか）が問題、到達していなければ **tick を下げる**（例 120）だけで経路に入るはず。
  どちらでも**新しい計器は不要**（`resizeRequestedAtTick` が既に答えを出す）。
- **外部知見の追補（web 調査）**: VVL の 00067 は **SDK 1.4.313 以降、present wait semaphore の安全でない再利用を報告する**ようになったもので
  （[Swapchain Semaphore Reuse](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html)）、
  **Diligent Engine も同じ VUID を報告**している（[DiligentCore #682](https://github.com/DiligentGraphics/DiligentCore/issues/682)）。
  そのメッセージ自身が示す解決策は **(a) image ごとに semaphore を分けて acquired index で引く**・**(b) `VK_EXT_swapchain_maintenance1` の present fence**。
  ＝**Celtium が採ったのは (a)**で、**Khronos の推奨そのもの**。加えて VVL #10364 は **resize 後の同 VUID が false positive になりうる**として
  **PR #10396 で修正済み・次の SDK には未搭載**としている。**両方を知った上で resize leg を読む**必要がある。
- **ユニットの状態（不変）**: per-image は**実装済・通常経路は4 leg 緑**。未取得は **image 数の変化** と **frame-close の pairing**（fixture で到達不能・counter 可視化済み）。

## 0.311 **round 117: resize leg を `resizeAtTick` 付きで再取得 — 緑だが resize の痕跡は無し（受入は未取得のまま）・VVL の既知 false positive を記録**

- **測定（Lead の `r109-resize`・jar `2b5859ab0`・層あり・`pinSections=60`・`resizeAtTick=200`・`resizeTo=1024x640`・150 s）**:
  **VUID 0 行**・exit 143（`endedBy=time-limit`）＝**hang せず完走**。ただし **console に `resizeRequestedAtTick` も window size の変化も出ていない**
  （`resize` の一致は全て**パス名** `r109-resize/mods/...`）＝**resize が実際に発火した証拠が無い**。
  ＝**proj-channel の leg と同じ形**（緑だが経路未踏）。**resize 受入は未取得のまま**として記録する（受入扱いにしない）。
- **重要な外部知見（web 調査・VVL の既知問題）**: **VVL issue #10364「Potential false positive 00067 after swapchain recreation (resize)」** は
  **まさに resize 後の present semaphore 再利用で 00067 が出る**という報告で、Khronos 側が**バグと認めて PR #10396「Track present semaphores from old swapchain」で修正**、
  **「次の SDK には間に合わない」**としている（[issue #10364](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/10364)）。
  ＝**今後 resize leg で 00067 が出た場合、それが我々の欠陥か layer の false positive かを区別する必要がある**。
  区別の材料: 我々の per-image 化は**古い swapchain の semaphore を破棄しない**（成長のみ）ため、この false positive の条件（旧 swapchain の semaphore 再利用）に**構造的に当たりにくい**。
- **現状（3者一致のまま）**: per-image ユニットは **実装済・通常経路は4 leg 緑**（`opt09f-close`・`owed-resize`・`so-baseline`・`r109-resize`・
  全て `validationMessageCount=0` か VUID 0・stream equality 不変）。**未取得**: **image 数の変化**（2回試して経路未踏）・
  **frame-close の pairing**（この fixture では到達不能・counter は可視化済み）。

## 0.310 **round 116: `resizeAtTick` は既存だった（fixture 変更は不要・flag の問題）・counter に print site 追加（`2dc4b5aaf`）**

- **gui-stage の指摘（正しい）**: world leg の**一度きり resize は既に存在**する —
  `-Dceltium.validation.resizeAtTick=<n>`（`ValidationClient:3001-3002` の world-tick guard・`:2664` の menu flow とは別 site）。
  ＝**proj-channel の leg が経路を踏まなかったのは `resizeTo` を `resizeAtTick` 無しで設定したため**（既定 0 が `> 0` guard を満たさない）。
  **必要なのは fixture 変更ではなく leg の flag**: `-Dceltium.validation.resizeAtTick=200 -Dceltium.validation.resizeTo=1024x640`（`pinSections` 併用）。
  artifact には既に `resizeRequestedAtTick`・`glfwWindowSizeAtEnd`・swapchain image 数があるため、**受入は既存 reporting で足りる**。
  ＝**AGENTS.md の「既存の計器を読む」に従い、重複した knob を追加しない**という判断。
- **counter の print site を追加（Lead・`2dc4b5aaf`）**: proj-channel が「`signalOnlySubmissions` は field と increment の2参照のみで
  **print site が無い**＝artifact/console/report に出ない」と指摘（`mirroredMultiplies` と同じ形）。＝**0 が「測定」ではなく「不在」になる**。
  verification summary の `windowFlushFailures` の隣に出力するよう修正。＝**「0 のままならそれが finding」は数が見えて初めて成立**。
- **Lead の resize leg はスロット待ちで走らず**: `r108-resize` は `Waiting for the game slot (held by pid 2912681)` のまま
  `exitCode=0 / endedBy=process-exit / elapsed 31.5 s`＝**結果ではなくスロット待ちの終了**。**resize 受入は未取得**として記録（受入扱いしない）。
- **ユニットの状態（3者一致）**: **実装済・通常経路は3 leg 緑**（`opt09f-close`・`owed-resize`・`so-baseline`・全て `validationMessageCount=0`・
  stream equality 不変）。**未取得の2項目**: **image 数の変化**（`resizeAtTick` 付き leg が1本あれば取れる）と
  **frame-close の pairing**（**この fixture では到達不能**・counter が可視化されたので以後は checkable）。

## 0.309 **round 115: 2 leg は緑だが経路を踏んでいない（proj-channel の検証）・`submitSignalOnly` は fixture で到達不能と判明・counter 追加（`a3c10ac81`）**

- **proj-channel の2 leg（per-image build `cf6c968cd`・較正済み terrain・`pinSections=60`・`indirectCompare` 有効）**:
  | leg | knob | 結果 | 経路を踏んだか |
  |---|---|---|---|
  | `owed-resize` | `resizeTo=1024x640` | PASSED・`validationMessageCount=0` | **いいえ** — `glfwWindowSizeAtEnd=1280x720`＝**window が resized のままにならず image 数が変わっていない** |
  | `owed-signalonly` | `forceAcquireOutOfDate=true` | PASSED・`validationMessageCount=0` | **未証明** — console に signal-only/out-of-date/forced の痕跡が0行・counter も無し |
  ＝**緑の leg でも経路を踏んでいなければ「もう一方の経路についての leg」**。proj-channel は受入として記録することを拒否（正しい判断）。
- **Lead の追加測定（`r107-signalonly`・`-Dceltium.vulkan.failSubmitSite=signal-only`・層あり）**: **VUID 0** だが
  **`CELTIUM_SUBMIT_FAULT` 0行**＝**fault site 自体が到達されていない**。＝**`submitSignalOnly` はこの fixture では到達不能**であり、
  「痕跡が無い」より強い主張。**第2受入項目は「未検証」ではなく「現 fixture で到達不能」**として記録する。
- **counter 追加（`a3c10ac81`）**: `submitSignalOnly` が **`signalOnlySubmissions`** を数える。以後の leg は verdict ではなく
  **「その経路が動いたか」**を言える。0 のままならそれが finding。
- **resize leg の knob は間違いと確定**: `celtium.validation.resizeTo` は **menu-resize flow**（`resizeMenuHeldTicks`/`resizeOutput`）用で、
  素の world leg では到達しない。**必要なもの**: world leg で**指定 tick に一度だけ GLFW window を resize** する hook
  （`resizeWorldAtTick`）。**gui-stage に配布済み**（受入条件: artifact が window size の実際の変化と image 数の差を示すこと）。
- **確立したこと**: 2 leg は per-image build で **通常経路を回帰させていない**（`validationMessageCount=0`・`validationClean=true`・PASSED・
  VUID-00067 0行・hang なし・stream equality 不変）。**未確立**: image 数の変化・frame-close の pairing。
  ＝**per-image ユニットは「実装済・3受入項目のうち1つ」**のまま。

## 0.308 **round 114: per-image の残り2 leg を proj-channel へ配布・成長のみ配列の選択を外部根拠で裏づけ**

- **配布（Lead→proj-channel）**: 未了の2 leg を依頼した。**(1) resize leg**（image 数が変わる場合＝この規則が守る対象）、
  **(2) `submitSignalOnly` が発火する leg**（第2 producer は frame-close semaphore を signal し、`consumeDone` は**記録された handle** を待つため、
  この変更が壊しうるのはまさにその経路）。両 leg に `pinSections`。fixture が present 失敗を作れない場合は**必要な knob を名指しする**よう指示（scene 差し替えは禁止）。
- **成長のみ配列の選択の外部根拠**: [Swapchain Recreation](https://docs.vulkan.org/samples/latest/samples/api/swapchain_recreation/README.html) は
  **「swapchain 再作成時、古い swapchain の semaphore の破棄は、新しい swapchain の最初の present が完了するまで遅延させねばならない」**
  としている（image 数は変わりうるため）。**成長のみの配列は破棄が発生しないので、この遅延問題を構造的に回避**する。
  ＝§0.307 の実装選択が、この外部根拠と一致していることを記録。
- **現状**: `cf6c968cd` で着地・1 leg 緑（VUID 0・00067 0・deadlock なし）・build 緑・ツリーに未検証の編集なし。
  残るのは上記2 leg のみで、**per-image ユニットは「実装済・1/3 leg 受入」**として区別して扱う（受入済みと書かない）。

## 0.307 **round 113: per-image present semaphore を着地（`cf6c968cd`）— 層あり leg で VUID 0・00067 0・deadlock なし（resize leg と submitSignalOnly leg は未了）**

- **実装（Lead・3ファイル＋interface）**: `GllessFrameTarget` が **成長のみの present-wait semaphore 配列**（`presentDoneSemaphore(int)`・既存を破棄しない）と
  **最後に signal した handle の記録**（`noteDoneSignalled`）を持ち、**`consumeDone()` はその記録された handle を wait** する。
  `VulkanSwapchain` は **acquired image index で引いて signal** し、成功後に記録する（`setBridgeTarget` で backend から一度だけ配線）。
  `FrameImageTarget` に default 実装を追加（配列を持たない実装は frame-close semaphore に fallback）。
  **frame-close semaphore は frame 単位のまま**（その pair は frame 内で閉じる）。
- **測定（`r105-perimage`・jar `d464303e`・層あり・`pinSections=60`・150 s）**: **VUID 0 行**・**`pSignalSemaphores-00067` 0 行**・
  exit 143（`endedBy=time-limit`）＝**deadlock していない**（deadlock なら duration まで進まずハングする）。
- **途中で赤くなった経緯も記録**: 最初の build は (a) interface に2メソッドが無い、(b) `GllessFrameTarget` への挿入が
  `@Override public long doneSemaphore()` の**間**に入りクラス構造を壊した、の2点で赤。両方修正して緑。
  ＝**「赤いツリーは未実装の row より悪い」**の原則どおり、**修正して緑に戻してから commit**した。
- **未了（正直に・受入の残り2 leg）**: **resize leg**（image 数が変わる場合＝この規則が守る対象）と
  **`submitSignalOnly` が発火する leg**（第2 producer は frame-close semaphore を signal する）。両 leg に `pinSections`。
- **地図は §0.299／§0.303／§0.306 が揃い、実装は §0.307 で着地**。残るのは検証の2 leg。

## 0.306 **round 112: per-image 化の完全な形が判明 — consumer 側（`consumeDone`）も per-image に追従させる必要がある**

- **コード確認（Lead・実装設計）**: present 経路の signal は
  `VulkanSwapchain` の `this.context.submitWithSemaphores(commands, new long[]{acquire, bridgeWaitSemaphore}, new long[]{renderFinished, bridgeDoneSemaphore})` の
  **2つ目の signal**。この semaphore の **consumer は `GllessFrameTarget.endFrame()` → `consumeDone()`**（`doneSemaphore` を wait する wait-only submission）。
  ＝**producer 側だけを per-image にすると、`consumeDone` が待つ semaphore が signal されず deadlock する**。
- **これが §0.299／§0.303 にも欠けていた最後の1点**。完全な形は次の5点セット:
  1. `GllessFrameTarget` に **per-image の present-done semaphore 配列**（`presentDoneSemaphore(int imageIndex)`・**成長のみ**＝既存 semaphore を破棄しないので in-flight を壊さない）。
  2. `VulkanSwapchain` が **acquired image index** で引く（signal 側）。
  3. **frame target が「最後に signal した done semaphore」を記録**（`noteDoneSignalled(handle)`）。
  4. **`consumeDone()` はその記録された handle を wait する**（`submitSignalOnly` 経路は従来どおり `doneSemaphore` を signal して記録する）。
  5. **`doneSemaphoreOutstanding` 系の guard は退役**（per-image の所有権が規則そのものになるため）。
- **成長のみの配列を選ぶ理由**: swapchain 再作成で image 数が変わる場合、**既存の semaphore を破棄すると in-flight の present が参照している可能性**がある
  （Khronos の swapchain recreation sample は「古い swapchain の semaphore の破棄は最初の新 present が完了するまで遅延させる」としている）。
  **成長のみなら破棄が不要**で、この遅延問題を構造的に回避できる。
- **受入（3 leg）**: 較正済み terrain leg（`validationMessageCount=0`）・**resize leg**・**`submitSignalOnly` が発火する leg**。両 leg に `pinSections`。
- **現状**: 緑（VUID 0件・層あり回帰 leg も 0行・OPT-09 step(3) 閉止）。**未検証の編集は入れない**。

## 0.305 **round 111: goal 文書の同期を修正（live は revision 27・roundsStarted 103）**

- **live の goal（API で確認）**: `goal-780c63c7-acf3-4426-80dc-6c99d30d8627`・**revision 27**・phase **active**・activation **armed**・
  **roundsStarted 103**・**maxGoalRounds 1000**。objective 文は不変（goal は目標のみを保持するというユーザー指示どおり）。
- **修正**: `docs/goal.md` は **revision 16** のまま、`docs/plan.md` の冒頭は **revision 1** のままで、
  **goal が「同期せよ」と求めている2文書が両方 stale** だった。両方を live の値に合わせた（objective 文は触らない）。
- **配布準備（goal D）の外部参考（web 調査）**: Vulkan アプリの配布では
  **(1) loader の扱い（system / bundle / hybrid）・(2) 必要 extension を文書化し起動時に検査し fallback を持つ・
  (3) SPIR-V を事前コンパイルして同梱・(4) 最小 Vulkan 版と feature を明記**が定型
  （[Vulkan の Packaging and Distribution](https://docs.vulkan.org/tutorial/latest/Building_a_Simple_Engine/Tooling/06_packaging_and_distribution.html)・
  [Vulkan Versions & Porting Guide](https://docs.vulkan.org/guide/latest/versions.html) の「**最小 Vulkan 版を明記**」「**版ではなく feature を検出**」）。
  Forge mod 側は `mods.toml` の `license`・`version`・`[[dependencies]]` と、
  バージョン規約 `MCVERSION-MAJORMOD.MAJORAPI.MINOR.PATCH`
  （[Forge の mods.toml](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/modfiles/)・[Forge の versioning](https://docs.minecraftforge.net/en/latest/gettingstarted/versioning/)）。
  ＝**Celtium の配布物に必要な項目**（ライセンス・Celeritas 由来の credits・F3 の device/driver 表示・最小能力・Java 条件・
  既知問題・未検証 OS/GPU）はこの定型と一致しており、**「版ではなく feature で選ぶ」は goal の仕様5と同義**。

## 0.304 **round 110: 現行 tree の回帰確認 leg — 層ありで VUID 0 行（セッション約30 commit 後の確認）**

- **測定（Lead の `r102-regress`・jar `77d15a69`・`glless=true` + `validation=true` + `presentReadback=true` + `pinSections=60`・150 s）**:
  **`VUID` 0行**・exit 143（**harness の duration 上限による正常終了**であり crash ではない・`endedBy=time-limit`・elapsed 150.4 s）。
  ＝**このセッションで入れた約30 commit の後も、層ありで validation 違反が出ていない**。
- **セッションの主な変更（すべて commit 済み・revert 済みのみ残置）**: frame arena と upload batching（OPT-05/07）・indirect terrain（OPT-09）・
  clear 分類・clip の no-flip 化・polygon offset の group 捕捉（BH-11）・font セパレータ・BH-22 の退役・BH-32 の maxLod・
  device feature の有効化・`ensureFramebuffer` の settle guard（OPT-04）・semaphore flag の結果ベース化・
  `pinSections` fixture・BH-03 の override hook・`CELTIUM_ATLAS_SAMPLER`／lifecycle 計装。
- **未了（変わらず・地図あり）**: per-image `doneSemaphore`（§0.299 の地図＋§0.303 の第2 producer の判断）・BH-14・
  goal (e) の変更/復元/reload 段階・`scissorCapturedGroups=0`・F3 の bind short-circuit。

## 0.303 **round 109: per-image 化の地図に欠けていた1点 — `submitSignalOnly()` は image index を持たない第2の producer**

- **コード確認（Lead・実装前の読解）**: `GllessFrameTarget.doneSemaphore`（`:114`）の **producer は2つ**ある。
  1. **present 経路**（`VulkanSwapchain:850-851` が signal・`VulkanTerrainGpuBackend:4903/4907` が渡す）＝**acquired image index を持つ**。
  2. **`submitSignalOnly()`**（`VulkanTerrainGpuBackend:12720`）＝**present しなかった frame を終わらせる**ために
     `waitSemaphore` を wait し **`doneSemaphore` を signal** する。**この経路には image index が無い**。
- **これが地図の欠落だった**: §0.299 の地図は「`doneSemaphore` を image 数配列にし、present 経路で acquired index を使う」で正しいが、
  **`submitSignalOnly` が何を signal するかを決めていない**。単純に「index 0 を使う」と**規則違反が戻る**（image 0 が present 中に再 signal しうる）。
  取り得る形は3つ:
  (a) **`submitSignalOnly` を廃止**し、frame を終える経路を present か `endFrame` に一本化する、
  (b) **最後に acquire した index を frame target が覚え**、`submitSignalOnly` はそれを使う、
  (c) **`submitSignalOnly` の signal を per-frame の別 semaphore に分離**し、present の wait は per-image にする
  （＝**「present が wait する semaphore」と「frame を閉じる semaphore」を別物にする**のが最も規則に忠実）。
- **推奨（次に着手する担当へ）**: **(c)**。理由は Khronos の規則が「**present が wait する側**は image index で引く」と言っているのであり、
  「frame を閉じる側」は frame の都合で決めてよいため。**(b)** は acquire と present の順序に依存する暗黙の状態を増やす。
- **受入（変わらず）**: 較正済み terrain leg で `validationMessageCount=0` ＋ **resize leg**、両 leg `pinSections`。
  加えて **(c) を選ぶ場合は `submitSignalOnly` が実際に発火する leg**（present しない frame を作る）でその semaphore の 1:1 を確認する。
- **現状は緑**（VUID 0件・OPT-09 step(3) 閉止・層あり leg も緑）。**未検証の編集は入れない**方針は維持。

## 0.302 **round 108（round 100 到達）: 同世代プロジェクトとの設計境界を整理 — Celtium は唯一「完全 native・GL 非依存」を主張できる位置にいる**

- **外部調査で確定した構図（goal C/D の材料）**:
  | プロジェクト | 方式 | 1.12.2 での扱い |
  |---|---|---|
  | **Celtium（本プロジェクト）** | **完全 native Vulkan・GL context を生成しない**（GL-less） | — |
  | [VulkanMod-Next](https://www.curseforge.com/minecraft/mc-mods/vulkanmod-next) | vanilla の chunk VBO を Vulkan へ**ミラーし、GPU external-memory interop で framebuffer へ合成** | Forge/Cleanroom 両対応・**OptiFine/shader mod があると Vulkan 経路が降りる** |
  | [Vulcanizator](https://www.curseforge.com/minecraft/mc-mods/vulkanizator) | **本体 scene は LWJGL2/OpenGL が生成**し、その結果を Vulkan へ転送（hybrid） | 1.12.2・alpha・**「compatibility-rendered」を明記** |
  | [Actinium](https://github.com/DHJComical/Actinium) | OpenGL のまま **Iris 風 pass モデル**（shadow/gbuffers/deferred/composite/final） | Cleanroom・Celeritas/GLSM/GTNHLib 統合 |
  | [Kirino-Engine](https://github.com/CleanroomMC/Cleanroom) | Cleanroom 本体のモジュール（ECS・deferred render command・meshlet） | `runKirinoClient`/`runCleanroomClient`/`runVanillaClient` を用意 |
  ＝**goal が除外する「汎用 GL→Vulkan 変換・GL 全体のエミュレーション・Zink 組込み」に該当するのは Vulkanizator の hybrid 方式**で、
  **Celtium はこの表で唯一「GL context を生成しない」を主張する位置**にある。**goal (b) の主張が他プロジェクトとの差別化点そのもの**。
- **本家 Minecraft Java の Vulkan 移行（参考・将来整合）**: 26.2 で Graphics API 設定（Default/Prefer Vulkan/Prefer OpenGL）が入り、
  **26.4 snapshot で Default＝Vulkan**。要求は **Vulkan 1.2＋dynamic rendering＋push descriptors**、macOS は MoltenVK、
  **reversed depth buffer を採用して Z-fighting を減らしている**。
  参照: [Mojang の告知](https://www.minecraft.net/en-us/article/another-step-towards-vibrant-visuals-for-java-edition)・[26.4 の解説](https://pcgaming.io/en-us/blog/minecraft-java-vulkan-26-4)・[snapshot の確認手順](https://www.playnews.gg/en/guides/minecraft-snapshot-26-4-vulkan-rendering-how-to-enable-it-and-check-it-with-f3)。
  ＝**Celtium の baseline 設計（mesh shader を必須にしない・能力で選ぶ）と、BH-11 の z-fighting 修正の方向は本家と一致**している。
  本家の最小要求（Vulkan 1.2＋dynamic rendering＋push descriptors）は **Celtium の対応 GPU 表を書くときの比較対象**として記録。
- **現在の成果物**: `forge122/build/libs/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar`（2.58 MB）ほか3点。
  HEAD `4ae603796`・ツリーは glyph-fix の mixin のみ dirty・build 緑。

## 0.301 **round 107: OPT-08 step1 撤回と ring-pressure の実測改善・層あり leg で現行 tree が緑（`fed5dafbe`）**

- **OPT-08 step1（upload batch の semaphore handoff）は撤回（perf-ring）**: `flushUploads()` に **caller が2つ**あり
  （`submitCommands`＝consumer が居る／ring-pressure の早期 flush＝**consumer が居ない**）、後者で **signal が消費されず次の flush が再 signal**＝
  `VUID-vkQueueSubmit-pSignalSemaphores-00067`（層あり leg で10件）。**trace が handle を名指し**:
  `CELTIUM_UPLOAD_SIGNAL … sem=0x1c800000001c8`（VUID の handle と一致）・連続 `signal=true`（7→8・12/13/14・28/29）・
  ring-pressure 主体の leg では **frame 側 trace が0回**。＝**その10件は upload batch の semaphore**で、
  **彼らの変更を撤回した現行 tree では 0件**（proj-channel の緑 leg と整合）。
- **撤回時に残した実測改善2つ**:
  1. **ring-pressure の早期 flush**（settled な one-copy 経路への fallback をやめる）＝
     **1546/2419 copies が fallback していたのが 3/6306 に**（`uploadEarlyFlushesForRingPressure=136`）。
  2. **frame 側の wait 計器** `frameFenceWaitMillis`/`…Count`/`…WorstMillis`＝**OPT-08 の2段目（2 frames in flight）の大きさを決める数値**。
- **検証 leg（`chain-t51.sh`・層 ON・terrain + keepGui + captureEntities）**: **VUID 0行・`-4`/DEVICE_LOST なし・
  `validationMessageCount=0`・`validationClean=true`・exit 0**（既知の `FAILED_PRESENTED_IMAGE` のみ）。同 leg:
  3477 copies batched・fallback 3・frame wait **167 ms / 1201 waits（worst 5 ms）**・arena region 2809・host buffer 6。
  ＝**現行 tree が層ありで緑**という独立の確認。
- **次の attempt への規則（記録）**: **signal は `submitCommands` が行う flush からのみ**、または **timeline semaphore**（単調値で
  outstanding-signal の状態機械自体を消す）。2段目が必要とする資源（command buffer・fence・uniform region・
  frame-target の image/semaphore per slot）は `GllessFrameTarget` にあるため、**frame target の owner との joint unit**。

## 0.300 **round 106: 機序が1文で確定（frame 単位で引くと「present がまだ待っている semaphore を次 frame が再 signal する」）— 実装は runway のある担当へ**

- **外部根拠（web 調査・Khronos の frames-in-flight 解説）**: なぜ frame index で引いてはいけないかが1文で書かれている —
  > `renderFinishedSemaphores` は graphics queue が**特定の swapchain image への描画を終えた**ときに signal され、
  > **presentation engine が同じ image を present する前に wait** する。presentation engine は我々の `frameIndex` を知らず、
  > **`acquireNextImage` が返した `imageIndex` しか知らない**。swapchain は `MAX_FRAMES_IN_FLIGHT` より多くの image を持ちうるし、
  > 連続する acquire が同じ周期で index を返す保証も無いため、**frameIndex で大きさを決めた semaphore は、
  > presentation engine がまだ前回の wait をしている最中に次の frame が再 signal する**ことになりうる。
  > **実際の swapchain image 数で大きさを決めれば、image index だけで signal と wait が正しく対になる。**
  （[Frames in flight](https://docs.vulkan.org/tutorial/latest/03_Drawing_a_triangle/03_Drawing/03_Frames_in_flight.html)・
  [Swapchain Semaphore Reuse](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html)）
- **Celtium への当てはめ**: `GllessFrameTarget.doneSemaphore` は **frame 単位の単一 long** で、
  `VulkanSwapchain:850-851` が **present の `pWaitSemaphores` に渡している**＝**この1文そのもの**。
  一方 `waitSemaphore` は **frame の submission が wait する側**で、両側が frame index で閉じるため frame 単位で正しい。
  ＝**「どちら側が signal し、どちら側が wait するか」で大きさが決まる**という規則で、`doneSemaphore` だけが例外になっている。
- **実装（次 unit・地図は §0.299）**: `GllessFrameTarget.doneSemaphore` を **image 数配列**に・`presentImageCount(int)` を
  swapchain の create 成功後に呼ぶ・`presentImage` の中で **acquire 済み index** で引く（`:778` に既に index がある）・
  `waitSemaphore` は frame 単位のまま・per-image の signal/wait counter を残す。
  受入は **較正済み terrain leg（`validationMessageCount=0`）＋ resize leg**、両方 `pinSections`。
- **この round で実装しなかった理由（正直に）**: この変更は **multi-file の semaphore 所有権リファクタ＋2 leg** を要し、
  誤ると**deadlock か frame 破壊**になる（proj-channel の辞退理由と同じ）。**現状は緑**（VUID 0件・OPT-09 step(3) 閉止）なので、
  **未検証の編集を入れるより地図と根拠を残す**方を選んだ。runway のある担当が §0.299 の地図から着手する。

## 0.299 **round 105: per-image doneSemaphore は「未着手・地図つき」で次 unit へ（proj-channel が runway 不足で辞退・理由を記録）**

- **辞退の理由（proj-channel・記録に値する）**: 受入が **2 leg**（較正済み terrain ＋ **resize**）である以上、
  **実装と両 leg の検証を同時に持てる runway が無い**。このセッションは**未検証の present 経路変更のコスト**を既に示している
  （彼らの light-map 半分は入ってすぐ戻った）し、**semaphore の誤りは「大声で失敗する」ではなく deadlock か frame 破壊**になる。
  ＝**赤いツリーは未実装の row より悪い**。
- **引き継ぎ地図（全て読了・未編集）**:
  - **signal の owner**: `GllessFrameTarget.doneSemaphore` は単一 `long`（`:114`）・生成 `:277`・払い出し `doneSemaphore()` `:519`・
    `consumeDone()` が wait `:349`・破棄 `:1421`・`doneSemaphoreOutstanding`（`:115-116`）は**まさに ownership 規則が置き換える flag 規律**。
  - **image 数**: `VulkanSwapchain.imageCount()` `:568`。acquired index は **`presentImage` の中に既にある**（`:778` が自前 pool を index している）
    ＝**同じ関数に写すべきパターンがある**。
  - **present 経路**: `VulkanTerrainGpuBackend:4903` が `frameTarget.doneSemaphore()` を読み `presentImage(...)`（`:4907`）へ渡し、
    `VulkanSwapchain:850-851` が signal。**`:4904` の wait semaphore は frame 単位のまま**。
  - **再サイズ**: `VulkanSwapchain.createSwapchain`（`:1124`・呼び元 `:515`）＋ out-of-date/suboptimal の acquire 経路。
    frame target に **`presentImageCount(int)`** を足し、**成功した create の後**（と数が変わった acquire の後）に配列を作り直す。
    ＝**resize leg が踏むのはこの部分**。
  - **counter**: per-image の signal/wait 数を残し、`doneSemaphoreOutstanding` 系の guard は**それらに置き換えて退役**させる。
- **commit に持ち込む2文**: (1) 両 leg に `pinSections`、(2) **「consumer が保証されない flush は semaphore を signal しない」**
  （perf-ring の撤回から。同じ欠陥クラス＝**所有権は consumer が確立するもので producer が仮定するものではない**）。`flushUploads` に触れるなら逐語で入れる。
- **現状（変わらず緑）**: OPT-09 step(3) は緑の leg で閉止（`validationMessageCount=0`・VUID-00067 0件・`match=true`・29,065 command・checksum 一致）。
  **per-image ownership は「次の unit」として地図つきで記録**され、ツリーには未検証の編集を入れていない。

## 0.298 **round 104: per-image present semaphore の修正手順を外部根拠で確定（resize で image 数が変わる場合は再作成が必須）**

- **外部根拠（web 調査）**: [Swapchain Semaphore Reuse](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html) の規則に加え、
  [Swapchain Recreation の sample](https://docs.vulkan.org/samples/latest/samples/api/swapchain_recreation/README.html) が
  **「swapchain を再作成すると image は解放され、**異なる数**で作られうる」**と明記し、
  **image index で引く semaphore は swapchain 再作成のたびに作り直す必要がある**（image 数が変わりうるため）としている。
  ＝**私が `renderFinishedSemaphores` に入れた「pool の長さが `images.length` と違えば作り直す」規則は、この要件そのもの**（`VulkanSwapchain:974-978`）。
- **`doneSemaphore`（bridge）に適用する手順（確定・proj-channel が実行）**:
  1. `GllessFrameTarget` が **acquired image index** を受け取る（`VulkanSwapchain.acquireNextImage` の結果）。
  2. `doneSemaphore` を **`images.length` ぶんの配列**にし、**image index で引く**（present の `pWaitSemaphores` に渡す側）。
  3. **`waitSemaphore` は frame 単位のまま**（frame 内で 1:1 が閉じる）。
  4. **swapchain 再作成で image 数が変わったら配列を作り直す**（上の外部根拠の要件）。
  5. **per-image の signal counter** を report に残す（どの image の signal かが leg で言える）。
- **受入**: 較正済み terrain scene の leg で `validationMessageCount=0`＋**resize leg**（image 数が変わる場合を実際に踏む）。
  両 leg に `pinSections` を付けて同じ world を保つ。
- **現状**: 緑の leg（`opt09f-close`）で VUID は 0 件であり、**この修正は latent な misuse を構成上消すもの**（flag 規律への依存をやめる）。
  ツリーは緑（HEAD `e5670c4d1`）・dirty は glyph-fix の mixin のみ。

## 0.297 **round 103: 緑の leg で semaphore VUID が消え、OPT-09 step(3) が閉じた（`validationMessageCount=0`）**

- **測定（proj-channel の `opt09f-close`・現行 tree・jar `07004475`・較正済み terrain scene・`--validate`・`indirectCompare` 有効）**:
  ```
  fixtureStatus                 = PASSED        (fixtureUncalibrated=false)
  validationMessageCount        = 0             validationClean = true
  pSignalSemaphores-00067 lines = 0             ← 直前4 leg は 10 件
  terrainIndirect               = [offers=1449 calls=1449 draws=29065 clamps=0 growths=1 fallbacks=0]
  terrainIndirectStream         = [indirectDraws=29065 indirectChecksum=d89b513407194482
                                   directDraws=29065   directChecksum=d89b513407194482  match=true]
  drawnByFormat                 = [1:5 2:3094 3:0 4:1860 5:155 6:1126 7:1122 8:128 9:12]
  ```
  ＝**VUID 10件 → 0件**。**`5d508be38`（done semaphore の flag を present の「結果」で記録する修正）が原因を除去した**。
- **OPT-09 step(3) 受入完了**: **1449 indirect call が 29,065 draw を運び（20.1 draws/call・world 依存）、
  indirect が submit する command stream は direct loop のものと command 単位で一致**（同一 world・同一 leg・checksum 一致）。
  「identical submitted geometry and transparent order」が**緑の leg で**成立。比率は world 依存、**等価性は依存しない**。
- **残る latent リスク（記録・受入は緑の上で）**: `GllessFrameTarget.doneSemaphore` は **frame 単位の単一 semaphore**でありながら
  present の `pWaitSemaphores` に渡っている。Khronos guide の規則は **swapchain image 数ぶんの配列＋acquired index で引く**で、
  今の正しさは**flag の規律（意図→結果）に依存**している。**次に producer が増えるか、present が失敗して再 present すると再発しうる**。
  proj-channel が修正の形を明記（target が acquired index を受け取る・`doneSemaphore` を image 数配列に・wait semaphore は frame 単位のまま・
  per-image の signal counter を残す）。**latent として起票し、次の frame-target unit で取る**。
- **perf-ring の指摘（upload batch の semaphore）**: 彼らの leg では handle が upload batch のもので、**consumer の無い signal** が
  二重 signal を生む形だった（ring-pressure flush が frame submission を伴わない）。彼らは handoff を撤回済みで、
  現行 tree では `boolean signal` の分岐自体が無い（OPT-07 の形に戻っている）。**同じ形の再導入を防ぐため、
  将来「consumer のいない flush が signal する」経路を作らない**ことを規則として残す。

## 0.296 **round 102: 決定的な外部根拠 — この VUID は「present の wait semaphore が swapchain にまだ使われている」形で、修正は swapchain image 単位の semaphore（Khronos の guide が名指し）**

- **外部根拠（web 調査・Khronos 公式 guide）**: [Swapchain Semaphore Reuse](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html) が
  **「これは現在 `VUID-vkQueueSubmit-pSignalSemaphores-00067` として報告される（"your VkSemaphore is being signaled by VkQueue, but it may still be
  in use by VkSwapchainKHR"）」**と明記し、原因と修正を名指ししている:
  > `vkQueuePresentKHR` は signal も fence も提供しないため、`pWaitSemaphores` がまだ present に使われているかを知る手段がない。
  > **よくある誤り**は present wait semaphore に frames-in-flight の buffering を適用すること。**修正は2行**:
  > (1) submit semaphore の配列を **swapchain image 数**で確保する、(2) **acquired swapchain image index** で引く（in-flight frame index ではなく）。
- **Celtium への適用（これが本欠陥の owner）**: `GllessFrameTarget.doneSemaphore` は **frame 単位の単一 semaphore**でありながら
  **present の `pWaitSemaphores` に渡っている**（`VulkanSwapchain` の bridge 引数）。＝**swapchain がまだその semaphore を保持している間に次の frame が signal** する形そのもの。
  swapchain 側の `renderFinishedSemaphores` は既に image 数追従＋modulo 撤去で直したが、**bridge の `doneSemaphore` は frame 単位のまま**。
- **修正（次に着手・2箇所）**: `GllessFrameTarget` の done semaphore を **swapchain image 数ぶんの配列**にし、
  **acquire した image index で引く**（`VulkanSwapchain.acquireNextImage` の結果を frame target に渡す）。
  これで「present が使い終えるまで同じ semaphore を再 signal しない」が**構成上**保証される。
  frame 単位の `waitSemaphore`（frame の最初の submission が wait する側）は **1:1 が frame 内で閉じる**ので frame 単位のままでよい。
- **この VUID が3経路（guard・drain・consumer 報告）で動かなかった理由**: それらは**すべて frame 単位の所有権**を扱っており、
  欠陥は**「present の wait semaphore の寿命が frame ではなく swapchain image に属する」**という別の軸だったため。
  ＝**proj-channel の「決定的な生成順で race ではない」という結論とも整合**（image index は決定的に進む）。
- **受入**: 同じ leg で `validationMessageCount=0`（semaphore の VUID が消える）＋ OPT-09 step(3) が緑で閉じる。

## 0.295 **round 101: semaphore の producer は3経路のいずれでもないと測定で確定・flag を「意図」から「結果」へ（`5d508be38`）**

- **測定（proj-channel・同一 jar 規律・`opt09d-close` と `opt09e-close`）**: **どちらも `validationMessageCount=10`**・
  どちらも picture gate `PASSED`・どちらも handle **`0x1eb00000001eb`**（最初の赤 leg と同一）＝
  **boot をまたいで同じ handle**＝**決定的な生成順であって race ではない**。swapchain の semaphore は各 leg の present trace で `0x4e`–`0x53`。
  ＝**guard を入れても・drain を入れても・consumer が消費を報告しても数は動かない**＝**producer はこの3経路のいずれでもない**。
- **着地した修正（proj-channel）**: composite の present submit が `waitSemaphore()` を wait しているのに target へ報告していなかったため、
  **「signal outstanding か」の flag が正常な frame の後も true のまま**だった＝**stale**。`noteWaitConsumed()` を submit の隣に追加（この欠陥と独立に正しい）。
- **撤回された仮説（proj-channel）**: `endFrame()` での drain。**方向が逆**（flag は outstanding ではなく stale）で、
  既に消費済みの semaphore に wait を submit すると**次 frame の signal を消費して pair 全体が1 frame ずれる**。測定が動かなかったので撤去。
- **Lead が着地（`5d508be38`）**: done semaphore には **producer が2つ**（`presentImage` の submit と `submitSignalOnly`）あり、
  flag は **`presentImage` の前に**立てていたため、**submit しなかった present が「起きなかった signal」を主張**し、次の producer を抑止していた。
  **`lastPresentSubmitDone()` を見て present の後で記録**するよう変更＝**意図ではなく結果**。
- **次の一手**: 数が動かなければ、**2つの producer が signal の所有権で食い違っている**ことが残る。不変条件を
  **frame target の1箇所**に置く（`submitSignalOnly` を唯一の producer にするか、target が両方を gate するか）のが本筋で、
  proj-channel に leg と判断を依頼。**緑の leg 1本で OPT-09 step(3) が閉じる**（等価性は測定済み）。

## 0.294 **round 100: 外部参考（goal C/D 用）— 同世代の実装が同じ設計に到達している**

- **Sodium の MC 26.2 port（Blaze3D 内で Vulkan 経路を持つ）**: `GLDrawContext` / **`VKMultiDrawContext`**（push constants を uniform 代わり・`EXT_multi_draw`）/
  **`VKIndirectContext`**（`EXT_multi_draw` の保証が無いため indirect＋追加 upload）＝**Celtium の OPT-09 と同じ設計判断**
  （multiDrawIndirect 対応時は1 call・非対応時は indirect の単発列）。参照: [CaffeineMC/sodium#3735](https://github.com/CaffeineMC/sodium/pull/3735)。
- **Actinium（Cleanroom 1.12.2・実験的 rendering/shader 互換 mod）**: Celeritas/GLSM/GTNHLib を統合し、
  **Iris 風の pass モデル（shadow/gbuffers/prepare/deferred/composite/final）**・framebuffer ownership・
  OpenGL state transition を対象にしている。互換マトリクスと roadmap を docs に持つ。
  参照: [DHJComical/Actinium](https://github.com/DHJComical/Actinium)・[CurseForge の Actinium](https://www.curseforge.com/minecraft/mc-mods/actinium-1-12-2-optimization)。
  ＝**goal E（shader pack 対応）の構造は、同じ Cleanroom 上で既にこの形に落ちている**。
- **Kirino-Engine（Cleanroom 公式）**: ECS・GL abstraction・**deferred render command**・meshlet terrain。
  「mod が GL state を漏らす」「Minecraft の描画が構造化されていない」を動機として明記。
  参照: [CleanroomMC/Kirino-Engine](https://github.com/CleanroomMC/Kirino-Engine)・[Cleanroom Discussion #405](https://github.com/CleanroomMC/Cleanroom/discussions/405)・
  [Cleanroom renderbook](https://cleanroommc.com/renderbook/)。
- **Vulkan validation のコスト（設計判断の根拠）**: VVL は state tracking を常時行うため **draw call 主体のアプリで 10x の overhead** がありうる
  （[VVL issue #4039](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/4039)）。**Celtium の受け入れ leg は `--validate` 付き**で走るため、
  OPT-09 の「indirect の方が速い」という主張は**この overhead 込みの測定**であり、**release 構成での倍率としては主張しない**（goal の「根拠のない倍率を使わない」に沿う）。
- **未了は変わらず**: semaphore drain（frame-open 契約）・BH-14・F3 の bind short-circuit・`scissorCapturedGroups=0`・`guiClickDivertedTo` の report field。

## 0.293 **round 99: 私の guard は欠陥を「消した」のではなく「移した」— skip を撤回（`cd53bbff2`）**

- **分析（Lead・frame target の drain 機構を読んで）**: `GllessFrameTarget` は既に
  **`beginFrame()` が前 frame を open のまま見つけたら `consumeOutstandingWait(...)` で wait signal を drain** し、
  **`endFrame()` が `consumeDone()` で done signal を consume** する＝**pair は構成上 1:1 に保たれている**。
- **私の guard がしたこと**: outstanding のとき **pair ごと skip** したため、**`waitSemaphore` の signal が消費されないまま**になり、
  **次の `beginFrame()` が再び signal** する。結果、**VUID は10件のまま**で、欠陥は **`doneSemaphore` → `waitSemaphore` へ移動**した。
  ＝測定（私の `r94-opt09` と glyph-fix の `vs-celeritas` が共に10件）と整合する。
- **対応**: present 経路の skip を**撤回**（`cd53bbff2`）。tracking（`doneSemaphoreSignalOutstanding`/`noteDoneSemaphoreSignalled`）は
  **drain が参照する計器**として残す。**修正は frame-open 契約側**（`consumeOutstandingWait` が既に住んでいる場所）で、
  **pair を skip せず drain する**形に限る。
- **この round の教訓（記録）**: 「VUID が減らない修正」は**欠陥を移しただけ**でありうる。次に semaphore を触る者は
  **signal と wait の 1:1 を数える計器**（`waitSignalled`・`doneSemaphoreOutstanding` の遷移）を先に読み、**skip ではなく drain** を選ぶこと。
- **未了**: OPT-09 step(3) の緑 leg（等価性自体は測定済み・`match=true`）・BH-14・goal (e) の self-consistency・F3 の bind short-circuit・
  `scissorCapturedGroups=0`（plan の `>0` が失敗）・`guiClickDivertedTo` の report field 不一致。

## 0.292 **round 98: semaphore VUID は私の guard をすり抜けた（10件のまま）— 二重 signal は `doneSemaphore` ではなく `waitSemaphore` の見込み**

- **測定（Lead の `r94-opt09`・修正 `7e3afed8d` 入り jar `a1a3aa96`・`pinSections=60`・`indirectCompare=true`）**: **VUID=10（変わらず）**。
  さらに glyph-fix の `vs-celeritas` leg も **`validationMessageCount=10`** で、彼らは**修正がビルドに入っていることを確認した上で**報告している
  （`doneSemaphoreSignalOutstanding` が `FrameImageTarget`・`GllessFrameTarget`・`VulkanTerrainGpuBackend` に存在）。
  ＝**私の guard はこの経路を覆っていない**。正直に記録する。
- **コードから見た残る producer**: `GllessFrameTarget.signalWait()`（`:311-321`）は
  **空の submission で `waitSemaphore` を毎回 signal する**（`beginFrame()` `:362` / `beginFrameForPresent()` `:436` から呼ばれる）。
  ＝**present の wait が前の signal を消費する前に frame が2回開けば、`waitSemaphore` が2回 signal される**＝VUID-00067。
  私が守ったのは `doneSemaphore`（present が signal する側）で、**露呈しているのは `waitSemaphore`（frame が signal する側）**の可能性が高い。
- **修正の形（推測で着地させない）**: 単に skip すると「signal の無い semaphore を frame の submission が wait する」＝
  待ちが成立しない危険があるため、**「outstanding なら再 signal の前に drain する」**（proj-channel が名指しした形）が正しい。
  `beginFrame` が2回開く経路（present retry/fallback）を特定してから、**frame の open 契約として**1箇所で直す。
  proj-channel に依頼する（彼らが producer/consumer を追えている）。
- **video-settings の self-consistency 受入（glyph-fix・`vs-celeritas`）**: **`guiScreenClassAtCapture = CeleritasVideoOptionsScreen`** ＝
  **Celeritas 画面が捕捉されている**。panel crop `(180,80,1100,624)` は **587 distinct colours**（rows-left 506・Done 145・world strip 103）、
  frame sha256 `7ab2ffa5…`・crop sha256 `a8253fb5…`。**比率列は報告しない**（同じ leg の早期 dump は world であり、
  same-screen の upstream 参照は存在し得ないため）。**未達3点を明示**: `validationMessageCount=10`・
  **`scissorCapturedGroups=0`（plan の `>0` が失敗）**・**`guiClickDivertedTo` はどの leg の report field でもない**（受入と fixture が不一致）。
- **F3 の split 計器が着地（glyph-fix・`5d5cbce73`/`5783eb6cf`）**: 1,280,000 glyph window・2,785 string で
  `stringMillis=4701`（1.69 ms/string）・**`glyphMillis=4635`（3.62 µs/glyph）**・我々の capture は 582 ms（0.45 µs/glyph）。
  ＝**vanilla の glyph 本体が 4,053 ms / 4,701 ms（3.17 µs/glyph）**で、`bindTexture` 毎 glyph ＋ 9つの `GlStateManager` wrapper が実体。
  **~15 ms/frame が vanilla 側・~2 ms/frame が我々**で、overlay の 139 ms のうち text 経路は ~22 ms。残りは per-frame の overlay 構築と追加 immediate group。
  次の実験は **per-glyph `bindTexture` の mirror 経路（変化しない bind の cache/short-circuit）**＝**我々の hook 側**。
- **blend-state の2件も閉じた（`6afbf1a91`）**: BH-10 の雲 scene は **pass は frame に居るが scene が解像しない**
  （`fmt=2 worldPass=true batches=1 vertices=5104 state=[… mask=0x0]`・1 frame 1 group・`drawnByFormat[8]=0`）、
  scatter の機構は **`captureWorldFrames` と sky mean の r=0.945**（fixture は tick を pin するが frame phase を pin しない→`partialTicks` が動く）。
  light-params 境界は **lit-only 化**で terrain 416→**17**・videoSettings 425→**2**（両 leg clean）。crop には synthetic lit draw が要る（未了と明記）。

## 0.291 **round 97: F3 のコストを原典と外部前例で裏づけ（vanilla の per-glyph `charmap.indexOf` と `bindTexture`）**

- **原典の構造（web 調査・1.12.2 の `FontRenderer`）**: `drawString` → `renderString` → **`renderStringAtPos`**（1文字ずつ
  `charmap.indexOf(c0)` を回す per-character loop・`getStringWidth` も同様）→ **`renderDefaultChar`**（その中で毎 glyph
  `bindTexture(this.locationFontTexture)`）→ `doDraw`。
  ＝glyph-fix が名指しした「200文字 ASCII 表の `indexOf` を1文字ごと」「毎 glyph の `bindTexture`」は**原典どおり**で、
  我々の hook（`GlStateManager` の bind・immediate 3 hook）は**その内側に居る**ため、**hook の外側の vanilla 仕事が無計測のまま残る**。
  参照: [mappings.dev の 1.12.2 FontRenderer](https://mappings.dev/1.12.2/net/minecraft/client/gui/FontRenderer.html)・
  [Forge javadoc 1.12.2 FontRenderer](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/client/gui/FontRenderer.html)・
  [Optifine-1.12-SRC の FontRenderer](https://github.com/Awe23123/Optifine-1.12-SRC/blob/master/src/net/minecraft/client/gui/FontRenderer.java)。
- **同種の前例（外部）**: MinecraftConsoles の **"Batch font drawing to fix debug overlay FPS"**（`renderCharacter` を
  `addCharacterQuad` に分け、`draw` の per-character ループを1つの Tessellator batch にまとめる）＝**F3 overlay の FPS を
  font の batch 化で直す**という同じ結論に別実装が到達している。
  参照: [MinecraftConsoles の commit](https://git.minecraftlegacy.com/backups/MinecraftConsoles/commit/7dcecdbd4d6037194e75d9fe3130845dd8690928)。
- **Celtium 側の含意**: 我々の `celtium$fontAppend`/`flushPendingFontBatch` は既に **1 run を1 batch にまとめて**おり
  （`fontStripSeparators=1138913`・0.23 µs/glyph）、**残るのは vanilla 側の per-glyph 仕事**（`indexOf` と `bindTexture`）と
  **per-frame の overlay 構築**。したがって次の実装候補は (a) `FontRenderer.renderStringAtPos`/`renderDefaultChar` に対する
  **Celtium 側の高速経路**（charmap を `int[65536]` 表に、bind を run 内で1回に）、(b) `GuiOverlayDebug` の per-frame 文字列構築の削減。
  どちらも**計器（Profiler section か stub hook timer）で帰属を確定してから**着手する（glyph-fix に依頼済み）。
- **未了は変わらず**: OPT-09 step(3) の緑 leg（semaphore 修正 `7e3afed8d` の検証）・BH-14 per-identity pair・goal (e) の
  self-consistency 受入・F3 の計器。

## 0.290 **round 96: semaphore 二重 signal を修正・較正済み picture gate が PASSED・F3 の実コストを測定・video-settings の参照不能を確定**

- **semaphore VUID を修正（Lead・`7e3afed8d`）**: proj-channel が `0x1eb00000001eb` を **GL-less frame target の bridge pair** と特定
  （present submit が `doneSemaphore` の唯一の producer・`endFrame` の wait-only submission が唯一の consumer）。
  frame target が **signal in flight を追跡**し（interface に accessor/note・既定は安全側）、wait-only submission が消費時に clear、
  present 経路が bridge pair を渡す前に問い合わせる（outstanding なら bridge 無しで present＝他の2 call site と同じ形・
  `presentBridgeSignalOutstanding` で計数）。swapchain の render-finished pool も **image 数に追従**（変更時に再構築・modulo を外す）。
- **較正済み picture gate が PASSED（acceptance・`r39-cal`）**: 素の terrain（keepGui/entity flag 無し）で
  **`PASSED`・assertion failure 0・`validationMessageCount=0`・`presentedDistinctColours=2142`・non-background 0.759463・628 bands・black 0.0**。
  `keepGui+captureEntities` 変種は**未較正**（`UNCALIBRATED_PRESENTED_IMAGE`）と両方記録。**「gate は意味があり、床がある場所では緑」**が実測で確定。
- **canon row（同一 tree・jar `4f56cad4`）**: HUD **1.93**・entity band **18.81**・hand **21.82**。hand 系列は BH-29 の方向（+2/+2/+4）を示すが**閉じておらず**、
  red 差は約17。**pair は tick-keyed で build state が違う（75 対 217 world frame・8 対 7 entity）ため帰属不能**＝
  `captureCondition=sections`＋最小 world frame の same-state pair が次（`pinSections` がまさにそれ）。
- **BH-10 の one-flag cloud control（acceptance・`r40-clouds-*`）**: Fancy clouds ON で **`partialColorMaskGroups=506`**・
  `colorMaskHistogram=[0x0:161 0x7:345 0xf:5483]`、OFF で **368**・`[0x0:2 …]`＝**159 group が zero write mask を得る**＝depth-only cloud pass の mask が
  group と pipeline に届いている。絵はこの pose では scored band に雲が無いため counter が受入（crop には雲を写す pose が要る）。
- **F3 の実コスト（glyph-fix・`7918203bf`・同一 jar・`showDebugInfo` のみ差）**: F3 ON は cold p50 **187.3 ms**・world frame **45**・glyph **1,020,000**、
  OFF は **47.8 ms**・**154**・**60,000**。**capture 経路は 0.48 µs/glyph**（submit 0.26・window 0.05・hooks 0.17）＝text run は約 2.2 ms/frame、
  追加 batch が約 1 ms/frame。**139 ms/frame の差のうち timer が覆うのは 3-4 ms だけ**で、残り約135 ms は
  **vanilla の per-glyph 仕事（`FontRenderer.renderString` の per-character loop・毎 glyph の `bindTexture`・stub wrapper 呼び出し）**と
  **per-frame の overlay 構築**（`GuiOverlayDebug.call()` の約20文字列・`getDebugInfoRenders()`）で、どちらも無計測。
  次の計器（Profiler section か stub hook の timer）を glyph-fix に依頼済み。
- **video-settings の参照は存在し得ないと確定（gui-stage）**: diverter は**我々の tree**（`MixinGuiOptions:20`）にあり、
  upstream jar は vanilla `GuiVideoSettings` を開く。したがって **Celeritas 画面の same-screen 参照は取れない**。
  **決定**: 受入は**自 jar の self-consistency claim**（`guiScreenClassAtCapture` が Celeritas を名指し・panel/rows/Done が描画・
  `validationMessageCount=0`・`scissorCapturedGroups` 報告）とし、139 色の参照は **vanilla 画面のもの**で Celeritas には適用しない。
  upstream の same-screen 参照は**その mixin を載せた upstream build** が要る＝build の決定であって leg ではない、と明記。

## 0.289 **round 95: OPT-09 step(3) を within-leg で受入（`match=true`・checksum 一致）・submit 経路に semaphore VUID 10件・`pinSections` 実装**

- **OPT-09 step(3) の等価性を測定（proj-channel・`5cbeafcfa`・`-Dceltium.vulkan.indirectCompare=true`）**:
  ```
  terrainIndirect=[offers=753 calls=753 draws=8665 clamps=0 fallbacks=0 lastFallback=none]
  terrainIndirectStream=[indirectDraws=8665 indirectChecksum=fb6ba6ff7e1b6f0e
                         directDraws=8665 directChecksum=fb6ba6ff7e1b6f0e match=true]
  ```
  ＝**753 indirect call が 8665 draw を運び、indirect 経路が submit する command stream は direct loop が同じ batch に対して submit するものと
  command 単位で一致**（count・`(indexCount, firstIndex, vertexOffset)`・順序・checksum）。**同一 world 内**での測定なので cross-boot の caveat が無く、
  絵より強い（描画結果ではなく**提出そのもの**を比較）。sorted pass は BH-02 の index-source 規則の下で両列挙が batch 順に歩くので構成上覆われる。
  **比率 11.5 draws/call**（entity scene は 16.3）が OPT-09 の CPU-record 議論の数値。受入は**等価性**であって比率ではない。
- **ただし leg は赤**: `validationMessageCount=10`・全て **`VUID-vkQueueSubmit-pSignalSemaphores-00067`**（binary semaphore が
  **間に wait を挟まず2回 signal** された）。signal 箇所は `VulkanContext.submitWithSemaphores`（`:543-553`・渡された非null handle を全て signal）と
  present 経路の submit（`:1968`）。＝**「同じ binary semaphore を2つの submission が wait 無しで signal している」**。
  proj-channel に追跡を依頼（frame の done-semaphore と present のそれが自然な候補）。
- **`pinSections` を実装（Lead・`3a74fb510`）**: `-Dceltium.validation.pinSections=<n>` で **listed-section 数が
  `pinSectionsStableFrames`（既定20）連続 frame 同一かつ n 以下**になるまで capture を保留し、**settled 値と保持 frame 数を報告**。
  既存の `captureCondition`/`captureSections` の意味は不変（既存 leg に影響しない）。
  根拠はこのセッションで3回測った cross-leg の非決定性（sky pair 54 対 412 world frame・entity scene の 23.5% 床・proj-channel の 35.5%）。
- **BH-03 の override hook 着地（weather-water）**: `-Dceltium.glstub.alphaFuncOverride=<GL enum>`（0=game 自身）。
  push selector は `celtium$alphaFuncSelector(state)` 経由で override を優先し、**並行変更（selector が `state[14]` へ移動）を保持**。
  A/B leg（`ww50-alpha-greater`・`ww51-alpha-always`・`ww52-alpha-lequal`）を同一 jar で queued。

## 0.288 **round 94: 白の owner は id 8 の mirror（3つの独立読みで確定）・announcement の順序計装が着地・外部参考**

- **パノラマの owner 確定（fog-path の測定＋Lead の比較）**: `passImage == readImage` が3 leg で一致し、readback は正しい。
  **faces が白を書いている**ことが3つの独立した読みで確定:
  (1) `vertexColour0=0xffffffff`（group 自身の頂点メモリから読んだ packed colour が不透明白）、
  (2) `CELTIUM_MIRROR_SOURCE id=8 … opaque=65536/65536 firstOpaque=0xffffffff announced=0 hasEntry=true full=true`（mirror が一様白）、
  (3) fragment は `texture × v_Color` で discard なし（`alphaFunc=0`・`alphaRef=0.0`・alphaTest off の A/B で mean 不変）。
  ＝**owner は texture id 8 の mirror 内容**（何が埋めても白だった、または announcement が白を運んだ）。
- **順序を答える計装が着地（texture-mirror）**: `CELTIUM_LIFECYCLE announceImage id=N seq=K size=WxH hasEntry=…` と
  `CELTIUM_LIFECYCLE forgetTexture id=N seq=K hadEntry=… announcedUnconsumed=…` を **bounded 64・単調 seq** で印字
  （frame 番号ではなく**イベント順序**であることを明記）。既存の `CELTIUM_UPLOAD_CALLER id=… WxH first=0x… callers=[…]` と並べれば
  「`int[]` で先に白く埋められた名前へ `BufferedImage` announcement が後から来るか」が2行で決まる。
- **原典の意味づけ（web 調査）**: `glTexImage2D`（**確保**・内容なし）と `glTexSubImage2D`（**更新**・内容あり）の区別が本質で、
  確保だけを announcement として扱うと mirror が内容を持たない（[Khronos の glTexSubImage2D](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glTexSubImage2D.xhtml)・
  [OpenRA の glTexSubImage2D 議論](https://github.com/OpenRA/OpenRA/issues/22496)・[Khronos wiki](https://www.khronos.org/opengl/wiki/GlTexSubImage2D)）。
  ＝**mirror は sub-image 更新から埋める**という設計判断の根拠。
- **次の測定（1 leg・新計器不要）**: `CELTIUM_LIFECYCLE` の seq を panorama id について読み、`int[]`（splash の白）と
  `announceImage`（PNG）の前後を確定する。announceImage が後なら**消費側が古い世代を掴んでいる**、来ないなら **hook が発火していない**。
- **他の担当の着地**: BH-32 の実効 maxLod レポート・BH-22 の census・OPT-04 の残り site 修正（`0638777c6`）・
  `pinSections`/`indirectToggleAtFrame` の決定・BH-03 の stub property 採用。

## 0.287 **round 93: OPT-04 の残り site を修正・OPT-09 step(3) は cross-boot 不能と判明（35.5% 差）・fixture の決定性 knob を配布**

- **OPT-04 の残り site を修正（Lead・`0638777c6`）**: `ensureFramebuffer` が render pass handle の変化時に
  **settleGpu なしで `destroyFramebuffer()`** を呼んでいた（frame-composite が特定）。兄弟 site（frame target recreation）は
  既に guard 済みで、validation layer が規則を名指ししている（`vkDestroyFramebuffer()` は使用中の framebuffer に呼べない）。
  同 guard を適用し `framebufferSettleFailures` で計数・**fail closed**（settle できないときは旧 handle を保持）。
- **OPT-09 step(3) は cross-boot では取れない（proj-channel・同一 jar `e3005991`・flag のみ差）**:
  | | indirect ON | indirect OFF |
  |---|---|---|
  | verdict | PASSED | PASSED |
  | `terrainIndirect` | `offers=696 calls=696 draws=8422 clamps=0 fallbacks=0` | `offers=0 fallbacks=1` |
  | passes / lastSections | **992 / 40** | **1309 / 53** |
  | picture 差 | — | **327,323 px（35.5%）** |
  ＝**2 leg は同じ workload ではない**（entity scene の 23.5% 床より大きい）。cross-leg の絵/geometry 比較は
  「同じ world の2経路」ではなく「別の world」を比べることになる。**両 leg が calibrated gate を通るのは実 signal だが、
  tolerance が2つの section 集合を許すほど広い**ことも判明。within-leg では `draws=8422` が batch の command 数と一致し
  fallback 0・packet は batch 順で書かれるので sorted pass の順序は構成上保たれている。
- **決定（Lead）**: (a) **`-Dceltium.validation.indirectToggleAtFrame=<n>`**（同一 leg 内で flag を frame 境界で切替・次 frame で capture）
  を proj-channel が実装（confound を減らすのではなく**消す**形）。(b) **`-Dceltium.validation.pinSections=<n>`**（section 数が n 以下で
  連続 frame 安定してから capture・値を報告）を gui-stage が実装＝**team の全 cross-leg 比較**（canon row・probe・resize・OPT-09）を守る。
- **BH-03 の hook 決定**: fixture knob ではなく **stub property**（`-Dceltium.glstub.alphaFuncOverride=<GL enum>`・weather-water のファイル）を採用。
  fixture が GL state ごとに knob を増やさないため。
- **BH-32 の実効 maxLod レポート**（`CELTIUM_ATLAS_SAMPLER maxLod=… configured=… mirrorLevels=… source=…`・bounded 6行）が着地。
  mipmap knob の gap は**未確立のまま明記**（別 scene で偽装しない）。

## 0.286 **round 92: 決定的 — `passImage == readImage`（copy は正しい image を読む）・それでも全白＝draw が白を書いている**

- **測定（Lead・既存 2 leg の比較）**:
  ```
  r90-cmp    passImage=0x7f0d78c74e20   readImage=0x7f0d78c74e20   mean=255,255,255
  hold-menu  passImage=0x7f6b74829930   readImage=0x7f6b74829930   mean=255,255,255
  ```
  ＝**両 leg で `passImage == readImage`**。**copy は pass が attach した image そのものを読んでいる**。
- **これで「draw の image ≠ read の image」仮説も棄却**。確定した事実の組み合わせは:
  **faces は 24,738 batch 描画され（拒否・破棄 0）・copy はその image を読み・readback は 65536 画素すべて白**。
  ＝**draw 自体が白を書いている**（image に何も届かないのではなく、白が届いている）。
- **帰結**: fragment は `texture × v_Color` で、`vertexColour0=0xffffffff`（不透明白）＝**出力は texture の内容**。
  白が書かれるなら **descriptor が bind している texture が白**であることになる。group は 45/46/48/49/50 を記録しているが、
  **descriptor が実際に bind する mirror が白**（splash の白い `int[]` で後から埋め直された等）でありうる。
- **次の測定（1点・bounded）**: **descriptor 構築時（`descriptorSetFor`）に `texture.key()`・`texture.width()/height()` と
  その mirror の先頭画素**を印字し、group が記録した texture id と突き合わせる。
  「group の id の mirror が白」なら announcement/mirror の側、「descriptor が別の texture を bind」なら descriptor 側。
- **棄却済みの一覧（再試行しない）**: pass 終了 hand-back・一律 GENERAL・per-descriptor 分岐・serve 前 flush・「描かれていない」・「flush されない」・
  「draw の image ≠ read の image」。

## 0.285 **round 91: BH-14 の偵察を記録（全 site が identity 非依存・一回で切るための手順）＋外部の互換/配布の参考**

- **BH-14 の偵察（sky-draws・実装は未着手・理由つき）**: **pair を触る全 site が identity 非依存**で、clears・publication・post-chain・readback に
  pair が糸通しされているため、一部だけ identity を通すと**現在の絵が依存している唯一の chain path を壊す**。
  site 一覧（`VulkanTerrainGpuBackend.java`）: `:2174`（group-bounds image）・`:7084` `celtium$syncPublishedChainOutput`・`:7103` `clearChainPair`・
  `:7119` `passAttachedColorImage`・`:7130` `celtium$publishFrameTexture`・`:7138` `clearOffTarget`・`:7143` `passAttachedColorImage`（off-target）・
  `:10141` `postChain.adoptTarget(identity, chainPairColorImage(), …)`・`:10296` 同 sync・`:10301` clear・`:10307` `adoptTarget`・
  `:10317`/`:10319` readback と `publishedChainImage`、加えて `pairOfOffTargetIdentity`（`:3914`）と `routeLabelForBinding`（`:4081-4087`）。
- **一回で切る手順**: (1) `GllessFrameTarget` の単一 `chainPair` を **`Map<Long, Pair> chainPairsByIdentity`**（bounded pool・`MAX_IDENTITY_PAIRS=4`・
  LRU 退役・counters `created/retired/reused/poolFull`）に置換、`pairForIdentity(identity)` が初回 bind で確保・同一 frame 内の再登場で再利用・
  frame 末か pool full で退役。`defaultPair` と main route は不変。(2) accessor に identity を足す（`chainPairColorImage(long)`・`clearChainPair(long, cmd)` と off-target 版）。
  (3) backend の上記 site に identity を通す。**最重要は publication と readback**（copy は「描いた identity が routing された pair」を解決する必要がある＝
  まさに panorama の欠陥: faces は非main identity に offer され、copy は name 0 を読む）。(4) 受入は **1 frame に2つの非main identity** の scene で
  `pairs == nonMainIdentities`・`widestPair=1`・`retired/reused` 可視・各 identity の readback が自分の内容。
  現在の census は `pairs=2`・`widestPair=chain(<全非main identity>)`＝**欠陥が数値で出ている**。
- **外部参考（goal C/D 用・web 調査）**: 1.12.2/Cleanroom の同種プロジェクトが複数存在し、互換境界の設計に使える —
  [VulkanMod-Next](https://www.curseforge.com/minecraft/mc-mods/vulkanmod-next)（1.12.2/Cleanroom 両対応・OptiFine/shader mod 併用時は
  Vulkan renderer が退いて fallback、MixinBooter 必須）、[Actinium](https://www.curseforge.com/minecraft/mc-mods/actinium-1-12-2-optimization)
  （Cleanroom 専用・Iris 風 shader pipeline・Celeritas/GLSM/GTNHLib 連携）、[Vulkanizator](https://www.curseforge.com/minecraft/mc-mods/vulkanizator/files/8494271)
  （legacy 版の GL→Vulkan 置換・**capture + native stage の混成**という設計）、[Kirino-Engine](https://github.com/CleanroomMC/Kirino-Engine)
  （Cleanroom 公式の次世代 render engine・ECS/GPU-driven）、[Cleanroom の renderbook](https://cleanroommc.com/renderbook/) と
  [modpack 準備ガイド](https://cleanroommc.com/wiki/end-user-guide/preparing-your-modpack)。
  ＝**未対応を成功風に黙殺せず境界を明記する**という goal C の要求に対し、他プロジェクトがどう境界を引いているかの実例になる。

## 0.284 **round 90: 引き継ぎ — パノラマは「draw の image と read の image の突き合わせ」1点だけが残る**

- **このセッションで棄却した説明（再試行しない）**: pass 終了時の read-only hand-back（VUID 2→4）・descriptor 一律 GENERAL（2→20+20）・
  per-descriptor 分岐（2→4）・serve 前の flush（白のまま・コストのみ）・「描かれていない」（fmt=2 が 24,738 描画）・「pending が flush されない」（submitted=30074・discarded=0）。
- **確定した事実**: mirror は実内容（replay 修正）・bind は 42/44/45-48・group の捕捉は stale でない・clear は face の draw より前・
  copy は panorama 自身（caller 連鎖 `GuiMainMenu.drawPanorama` → `renderSkybox`）・readback は 65536 画素すべて白（`nonWhite=0`）。
- **次の1点（実装済みの計器で足りる）**: `CELTIUM_DRAW_SITE` は `target=` と `passImage=` を印字するので、
  **`-Dceltium.vulkan.enqAll=true -Dceltium.vulkan.drawSiteLines=8` を付けた menu-hold leg** で
  **fmt=2 の `passImage`** と **copy の `readImage`** を同一 console で比較する。同じなら readback 自体（layout/format/extent）、
  違うなら identity→image の写像（routing 表）が owner。新しい計器は不要。
- **計装は残置**（`CELTIUM_COPY_READ`・`CELTIUM_COPY_CALLER`＋state・`CELTIUM_CHAIN_PAIR_CLEAR`・firstPixels/nonWhite・`CELTIUM_READ_RESOLVE`）。
  ツリーは緑・revert 済みのみ。

## 0.283 **round 89: faces は実際に描かれている（fmt=2 が 24,738）— 残る説明は「draw の image ≠ read の image」のみ**

- **測定（Lead・`r86-both` のレポート）**:
  ```
  refusedBeforeAppend=0   acceptedForOtherTargets=21047
  immediateRecordedBatches=30074  immediateSubmittedBatches=30074  immediateGroupsDrawn=25711
  drawnByFormat=[1:109 2:24738 3:0 4:1936 5:168 6:1916 7:1195 8:0 9:12 10:0]
  immediateGroupsDiscarded=0  postAcceptDroppedGroups=0
  ```
  ＝**fmt=2（panorama の face）が 24,738 batch 描かれている**。拒否も破棄も 0。
- **帰結**: 「描かれていない」「pending が flush されない」は**両方棄却**。
  残る唯一の説明は **draw が書いた image と copy が読む image が別**であること
  （identity→image の写像が draw 時と read 時で食い違う、または read の解決が誤っている）。
- **次の一手（1点・bounded）**: **offer/draw 時の `group.target` と、その target が写像される image** を印字し、
  read の `readImage` と突き合わせる。写像が同じなら readback 自体、違うなら routing 表。
- **パノラマの確定事実（不変）**: mirror 実内容・bind 正・捕捉正・clear 順序正・copy は panorama 自身・
  **faces は 24,738 batch 描画済み**・readback は全画素白。

## 0.282 **round 88: flush＋routing の併用も白（`nonWhite=0`）— faces の内容はどちらの pair にも無い**

- **測定（Lead の `r86-both`・hold 60）**: 39 groups queued・window なし・serve 前に flush 実行・read は routing 規則に従う — それでも
  `mean=255,255,255`・`firstPixels=[ffffffff×4]`・**`nonWhite=0`**。
- **flush は revert**（per-copy の同期コストに見合う効果なし）。**routing 修正は残置**（draws と同じ規則・コスト無し）。
- **この leg が示すこと（狭いが重要）**: **faces の内容は copy の時点でどちらの pair にも無い**。
  次の問いは「**faces の pass が frame image に書いたのか、そもそも実行されていないのか**」で、
  `CELTIUM_DRAW_SITE` の `target` と `passImage` を同じ leg で読めば答えが出る（前 leg では `passImage` は chain pair だったが、
  `target` は非main identity であり、**その identity がどの image に写像されるか**を offer 時に印字する必要がある）。
- **パノラマの確定事実（不変）**: mirror 正・bind 正・捕捉正・clear は draw 前・copy は panorama 自身・pending 39・readback 全白。

## 0.281 **round 87: read の routing 修正を実装（identity 0 → default pair）— ルール整合だが白は動かず**

- **実装（Lead）**: `celtium$ownedReadImage` の最後の分岐が `lastDrawnOffTargetIdentity` に対して**無条件に chain pair** を返していたのを、
  **`routeLabelForBinding(lastDrawnOffTargetIdentity, false)`** に従うよう修正（identity 0 → default pair、その他非main → chain pair）。
  ＝draws と同じ規則。ルール不整合は解消。
- **測定（`r85-route`・hold 60）**: **変わらず** `mean=255,255,255`・`firstPixels=[ffffffff×4]`・`nonWhite=0`。
  ＝**この修正は規則整合だが白の原因ではない**（正直に記録）。
- **次の一手**: **offer 時に faces が属した identity の image** と、**read 時に解決される image** の対応を直接比較する。
  `lastDrawnOffTargetIdentity` と `group.target`（offer 時の identity）を並べて印字し、**両者が同じ image に写像されるか**を確認する。
  写像が食い違っていれば registry/routing の対応表の問題、同じなら readback 自体（layout/format/extent）の問題。
- **白の全確定事実は不変**: mirror 正・bind 正・捕捉正・clear は draw 前・copy は panorama 自身・pending 39 あり・readback は全画素白。

## 0.280 **round 86: read 解決の実データ — `readFbo=0`・`mainColorName=3`・`currentTarget=0`・`currentIsMain=false`、しかし readImage は chainPair に落ちている**

- **測定（Lead・`r83-state` の `CELTIUM_READ_RESOLVE`）**:
  ```
  readFbo=0  mainIdentity=1434737802  mainColorName=3  currentTarget=0  currentIsMain=false
  frameImage=140167268583392  defaultPair=140167123062848  chainPair=140167277396864
  ```
  そして copy の `readImage=0x7f7b3cc74380` = **chainPair**。
- **コードの現状**: `celtium$ownedReadImage` は既に
  (1) `mainColorName == readFbo` → frame image、(2) `readFbo == 0 && currentIsMain` → frame image、
  (3) `readFbo == 0 && glless != null` で **`routeLabelForBinding(currentRenderTarget, currentIsMain)`** を見て `chain-pair` のときだけ chain pair、
  という分岐を持つ。ここで `routeLabelForBinding(0, false)` は**identity 0 → default pair** を返すはずで、**chain pair にはならない**。
  ＝**観測（readImage == chainPair）とコードの分岐が一致しない**ため、**その後のフォールバックが chain pair を返している**。
- **次の一手（1点）**: `celtium$ownedReadImage` の**残りの分岐**（上記3つの後）を読み、
  `readFbo=0` が chain pair に落ちる経路を名指しして、**faces が offer された identity の image**（＝pass が attach した image）に解決するよう直す。
  受入は copy の `mean=` が 255 から動くこと（`nonWhite > 0`）。
- **この round の成果**: 白の原因が「**read の解決が faces の書いた image に到達していない**」ことだと、実データ（readFbo/mainColorName/currentTarget/readImage）で確定した。

## 0.279 **round 85: 決定的 — copy 時点で `groups=39` が pending・`windowBusy=false`、しかし readback は全画素白、そして read image ≠ pass が attach した image**

- **測定（Lead の `r83-state`・hold 60）**:
  ```
  CELTIUM_COPY_CALLER … windowBusy=false  groups=39  recorded=0  groupBatches=1
  CELTIUM_TEX_COPY_SERVED … mean=255,255,255  readImage=0x7f7b3cc74380
      passAttached=0x7f7b33945040  lastDrawnFrame=-1  firstPixels=[ffffffff×4]  nonWhite=0
  ```
- **3つの確定事実**:
  1. **flush の早期 return はどちらも発火しない**（`windowBusy=false`・`groups=39`）＝pending はそこにある。
  2. **readback は 65536 画素すべて純白**（`nonWhite=0`）。
  3. **`readImage`（chain pair）≠ `passAttached`**（pass が attach している image は別）＝**pass は pair ではない image に描いている**。
- **帰結**: 白の原因は「pending が描かれない」ではなく、**copy が読む image（chain pair）が、pass が実際に描いている image と別**であること。
  `lastDrawnFrame=-1` は**flush 経路が更新しない counter**であり、証拠として使えない（「counter の不在は描画の不在ではない」）。
- **修正方向**: `celtium$ownedReadImage(readFbo)` の解決が **game の framebuffer が実際に書き込まれている image** を返すようにする
  （identity→image の対応が `passAttached` 側と食い違っている）。次は `readFbo=0` の解決規則と、その identity の pass が attach した image を突き合わせる。
- **計装は残置**（`CELTIUM_COPY_READ`・`CELTIUM_COPY_CALLER`＋state・`CELTIUM_CHAIN_PAIR_CLEAR`・firstPixels/nonWhite）。

## 0.278 **round 84: `flushPendingBeforeGlDraw` の早期 return 条件が容疑者（`immediateWindowBusy` / `immediateGroups` 空）**

- **実装確認（Lead）**: `flushPendingBeforeGlDraw`（`:6326`）は
  1. `if (this.immediateWindowBusy) return;` — **window 実行中なら何もしない**
  2. `closeCurrentImmediateGroup();`
  3. `if (this.immediateGroups.isEmpty()) return;` — **pending が無ければ何もしない**
  4. `framebufferReplacing` なら drop（計数つき）
  の順で、**早期 return が2つ**ある。
- **前ラウンドで serve 前に呼んでも描かれなかった理由の候補**: (a) `immediateWindowBusy` が真（既に window 実行中）、
  (b) `immediateGroups` が空（cube の batch がまだ **offer されていない**）、(c) batch が `recordedImmediateGroups` 側にある。
- **次の測定（1点・bounded）**: serve 入口で **`immediateWindowBusy`・`immediateGroups.size()`・`recordedImmediateGroups.size()`・
  `currentGroupBatches`** を印字し、flush が何を見て早期 return したかを確定する。その上で
  (b) なら「offer が copy より後」＝**Tessellator 経由の offer 順**の問題、(a) なら window 完了待ちの追加が必要。
- **パノラマの確定事実は変わらない**（mirror 正・bind 正・捕捉正・clear は draw 前・copy は panorama 自身・白は「pending が描かれないまま読まれる」）。

## 0.277 **round 83: caller 連鎖が確定 — copy は panorama のもの（`GuiMainMenu.drawPanorama` → `renderSkybox` → `glCopyTexSubImage2D`）**

- **測定（Lead の `r82-caller3`・window 16）**:
  ```
  GL11.glCopyTexSubImage2D:3793 <- GlStateManager.func_187443_a:510
   <- GuiMainMenu.func_73968_a:417   (renderSkybox)
   <- GuiMainMenu.func_73971_c:451   (drawPanorama)
   <- GuiMainMenu.func_73863_a:480   (drawScreen)
   <- ForgeHooksClient.drawScreen:399 <- EntityRenderer.func_181560_a:1183
   <- Minecraft.func_71411_J:1109 <- Minecraft.func_99999_d:399
  ```
  ＝**測っている copy は確かに panorama のもの**（splash ではない）。前ラウンドの「panorama でない可能性」は**棄却**。
- **したがって白の機序はこうなる**: `drawPanorama` は 64 cube を Tessellator 経由で offer し、その直後に `glCopyTexSubImage2D` を発行する。
  我々の serve 経路はその時点で **pending の batch を描いていない**（`lastDrawnFrame=-1`）ので、pair は clear のまま読まれる。
- **前ラウンドの flush 試行が効かなかった理由を次に確認する**: `flushPendingBeforeGlDraw()` がこの経路（GL draw ではない `glCopyTexSubImage2D`）で
  pending を submit していない可能性、または `lastDrawnFrame` が別の site でしか更新されない可能性。
  **次は `flushPendingBeforeGlDraw` の実装を読み、serve 経路から明示的に「pending を描いて submit し、完了を待つ」を呼ぶ**（`waitForIdle` だけでは描かれない）。
- **受入**: `lastDrawnFrame == frameId` になり copy の `mean=` が 255 から動く（同じ menu-hold leg）。

## 0.276 **round 82: copy の呼び元計装を追加 — 判明した分は `GL11.glCopyTexSubImage2D` ← `GlStateManager.func_187443_a:510`、game 側の frame は window 外だった**

- **計装（Lead・serve 入口の bounded caller census・BH-06 と同じ形）と測定（`r80-caller`）**:
  ```
  CELTIUM_COPY_CALLER dest=20 256x256 frame=0 callers=[
    CeltiumGlStub.celtium$noteTextureCopy:3290 <- …CeltiumGlTrampoline.callback:1348
    <- GL11C.glCopyTexSubImage2D:-2 <- GL11.glCopyTexSubImage2D:3793
    <- GlStateManager.func_187443_a:510 <- ]
  ```
  ＝呼び出しは **`GL11.glCopyTexSubImage2D`**（`GlStateManager.func_187443_a` 経由）。**game 側の frame（`GuiMainMenu.renderSkybox` か splash か）は 9 frame の window 外**だったため、
  **window を 16 に広げて再取得中**（`r81-caller2`・leg は完走待ち）。
- **次の1点（変わらず）**: その game 側 frame が `GuiMainMenu` か splash か。前者なら修正は copy の**順序**（cube 描画後に serve される必要）、
  後者なら**測っている copy が panorama のものではない**ので、受入 leg の設計から見直す。
- **ツリー**: 計装（`CELTIUM_COPY_READ`・`CELTIUM_COPY_CALLER`・`CELTIUM_CHAIN_PAIR_CLEAR`）は残置・build 緑・効かなかった変更は revert 済み。

## 0.275 **round 81: serve 前の flush＋idle 待ちは効かない（`lastDrawnFrame=-1` のまま）＝測っている copy は panorama のものではない可能性**

- **実装した修正（serve 入口で `flushPendingBeforeGlDraw()` ＋ `waitForIdle()`）と測定（`r79-flush`・hold 60）**:
  ```
  CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255
    readImage=0x7fe408c74890  lastDrawnImage=0x0  lastDrawnFrame=-1  drawnThisFrame=false  lastDrawnEpoch=-1
  ```
  ＝**変わらず**（`lastDrawnFrame=-1`）。flush しても「pending が無い」ので描かれない。**revert 済み**（効かない変更を残さない）。
- **重要な含意**: `lastDrawnFrame=-1` は「**この run でまだ一度も draw していない**」ことを意味する。
  frame 0 の copy が**全 draw より前**に走っている＝**測っている copy は `GuiMainMenu:483`（panorama の cube 描画後）ではない可能性が高い**
  （起動直後の splash/loading 経路の copy である可能性）。
- **次の測定（1点）**: **copy の呼び元**を bounded な caller census で印字する（BH-06・`func_110988_a` と同じ形）。
  panorama の copy（`GuiMainMenu.renderSkybox`）か splash の copy かで、修正の向きが変わる。
- **これまでの確定（再掲）**: mirror 内容は正しい（replay 修正）・bind は 42/44/45-48・group の捕捉は stale でない・
  clear は face の draw より前・白は「読んだ時点で何も描かれていない」。

## 0.274 **round 80: 決定的 — copy は「まだ何も描かれていない」時点で走っている（`lastDrawnFrame=-1`・`drawnThisFrame=false`）**

- **測定（Lead の `r78-px`・hold 60）**:
  ```
  CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255 opaque=65536/65536 served=1 frame=0
    readImage=0x7fef74c70cf0  lastDrawnImage=0x0  lastDrawnFrame=-1  drawnThisFrame=false  lastDrawnEpoch=-1
    completedEpochNow=33 …
  ```
  ＝**copy が読む image には、backend の記録上まだ一度も描かれていない**（`lastDrawnFrame=-1`・`lastDrawnEpoch=-1`・`drawnThisFrame=false`）。
- **結論**: 白の原因は **copy が face の draw より先に走っていること**。frame 0 の早い段階で `glCopyTexSubImage2D` が stub に届き、
  **その時点では immediate の batch がまだ描かれていない**（pending のまま）ので、pair は clear のまま読まれる。
  以前 fog-path が見た「draw と copy の間に6完了」は**別 flag の leg**のもので、この leg では成立しない（`lastDrawnFrame=-1` が直接の反証）。
- **修正方向（次に着手）**: copy を**提供する前に、その target の pending immediate を flush し、submit と完了まで待つ**
  （`celtium$serveTextureCopy` の入口で `flushPendingBeforeGlDraw()` 相当＋`waitForIdle()`。既存の `flushPendingBeforeGlDraw` が
  pending を flush しているなら、その後に **submit されていない**ことが原因なので、serve 経路で明示的に submit する）。
- **受入**: 同じ menu-hold leg で `lastDrawnFrame == frameId` になり、copy の `mean=` が 255 から動くこと。

## 0.273 **round 79: 行順が確定 — clear(269) → face の draw(273) → copy(後段)＝clear は face の前、残るのは「pass の submit」か「readback の対象」**

- **測定（Lead の `r77-enq`・`enqAll`+`enqFormat=2`+`drawSiteLines=8`+hold 60）**:
  ```
  269  recordImmediateOnlyPass   ← chain pair の clear（CELTIUM_CHAIN_PAIR_CLEAR frame=0）
  273  drawPendingImmediate      ← face の DRAW_SITE #1（clear の4行後）
  372/617-621  drawPendingImmediate ← 以降の draw
  （copy はその後段）
  ```
  ＝**clear は face の draw より前**。前々ラウンドの「clear が後」も、前ラウンドの「clear は wipe ではない」も整合し、
  **face は clear の後に描いている**＝内容はそこにあるはず。
- **残る2候補**:
  (a) **face の draw を記録した window が submit に到達していない**（記録はあるが queue に行かない）— 前ラウンドの「記録と実行のギャップ」と同族。
  (b) **readback が別の対象を読む**（pair の color image ではなく、同じ handle を持つ別 mip/layer、または extent 不一致）。
- **次の測定（(a)/(b) の分離）**: face の draw を記録した window の `submitCommands` 到達を bounded に印字し、
  readback 側では **読み出した pixels の先頭数画素**を印字する（`mean` だけでは「白一色」と「数画素だけ白」が区別できない）。
- **ツリー**: 計装（`CELTIUM_CHAIN_PAIR_CLEAR`・`CELTIUM_COPY_READ`）は残置・build 緑・revert 済みのみ。

## 0.272 **round 78: chain pair の clear は copy より前（frame 0・行 269 対 838）— clear は wipe ではない**

- **計装（Lead・`recordImmediateOnlyPass` の clear 地点）と測定（`r76-order`・hold 60）**: console の行順は
  ```
  269  CELTIUM_CHAIN_PAIR_CLEAR frame=0   (recordImmediateOnlyPass:7111)
  270  CELTIUM_CHAIN_CLEAR frame=0        (celtium$noteChainPairClear:2173)
  838  CELTIUM_COPY_READ image=0x… chainPair=0x… frame=0
  839  CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255
  ```
  ＝**chain pair の clear は copy より数百行前**（同じ frame 0 の早い段階）。したがって
  **「clear が face の描画後に来て内容を消す」という前ラウンドの推定は、この leg では成り立たない**（clear は copy より前）。
- **未取得の比較**: face の `CELTIUM_DRAW_SITE` はこの leg で `enqAll` を渡していないため出ていない。
  **clear と face の pass の前後関係は、`-Dceltium.vulkan.enqAll=true` を足した同一 leg で確定する**（次の1点）。
- **この leg で確定したこと**: 白の原因は「clear が後」ではない。残る候補は (a) face の pass が clear より前（＝内容が clear で消える）、
  (b) face の pass が copy に届いていない、(c) readback が別の mip/layer を読む。**(a) は enqAll leg で即決できる。**
- **計装は残す**（`CELTIUM_CHAIN_PAIR_CLEAR`・bounded 6行・`chainPairClearLogs` は既存 field を共用）。

## 0.271 **round 77: copy の read 事実を実測 — 読むのは chain pair、しかしその時点の attach は別 image**

- **計装（Lead・`celtium$readImageRectangle` 冒頭・bounded 6行）と測定（`r75-read`・hold 60）**:
  ```
  CELTIUM_COPY_READ image=0x7f3060ca19b0 rect=0,0 256x256 frame=0
    passAttached=0x7f3056fc3b00  chainPair=0x7f3060ca19b0  frameImage=0x7f3060c3fc10
  CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255
  ```
  ＝**copy は chain pair を読んでいる**（`image == chainPair`）が、**その時点で attach されている image は別**（`passAttached` は chain pair でも frame image でもない）。
- **意味**: copy は「今 attach されている image」ではなく**chain pair**を読む。したがって白の原因は
  「**face が chain pair に描いた後、copy の前にその pair が消されている**」こと。
  既報 `offTargetPairClears=[default-pair=167, chain-pair=1]` ＝**chain pair は frame に1回 clear される**。
  その clear が **face の pass より後**に来ていれば、まさにこの観測になる。
- **次の測定（1点）**: chain pair の clear の**frame と順序**を face の pass（`CELTIUM_DRAW_SITE`）と並べて印字し、
  「clear が後か前か」を確定する。後なら修正は **clear を最初の非main pass に固定する**（既に `chainPairClearedFrame` があるので、
  その clear の**記録位置**が face の pass より後になっていないかを確認する）。
- **未了（変更なし・revert 済みのみ・ツリー緑）**: menu hold 経路の VUID・OPT-09 step(3)・BH-14・goal (e) 3段階・較正済み picture gate。

## 0.270 **round 76: copy の read 経路の実装を確認（`celtium$readImageRectangle` の readback）— 次の計装点を確定**

- **コード確認（Lead）**: copy は `celtium$serveTextureCopy` から `celtium$readImageRectangle(image, x, y, w, h)` を呼び、
  **その image を staging buffer へ readback して `int[]` を作り、mirror の level0 に書く**（`:11055`〜）。
  既存 leg では `readImage == passImage == chain pair` を確認済み（＝**読む image は face が描いた pair と同一**）。
- **残る問い（次に計装すべき1点）**: その readback が **pair のどの時点の内容**を読むか。
  `celtium$readImageRectangle` の冒頭で **image handle・extent・その時点で backend が把握している pair の layout・
  最後にその image へ書いた pass の frame** を bounded（数行）で印字すれば、白の原因を VUID と独立に決められる。
- **この計装は leg 形状に依存しない**（hold なしでは copy 自体が走らないため、hold leg で読むことになるが、
  印字内容は VUID と無関係に「どの image を・どの layout で・どの frame の内容として読んだか」を答える）。
- **未了のまま残るもの（変更なし・revert 済みのみ・ツリー緑）**: menu hold 経路の VUID（hold 依存・別 unit）、
  OPT-09 step(3)・BH-14・goal (e) の3段階・較正済み scene での picture gate 再取得。

## 0.269 **round 75: hold なしでは copy が走らない＝白の測定は hold leg に限られ、VUID と分離できない**

- **測定（Lead の `r73-nohold`・menu・hold なし・layers ON）**: **VUID = 0**（VUID が hold 依存であることを再確認）だが、
  **`CELTIUM_TEX_COPY_SERVED` が1行も出ない**＝**hold なしでは copy 経路が走らない**（menu 段階が短く `renderSkybox` に到達しない）。
- **帰結**: 白い copy を測れるのは hold leg だけであり、**hold leg は必ず VUID を伴う**。
  ＝**「白」と「VUID」は leg 形状では分離できない**。分離するには (a) hold 経路の VUID を先に直す、
  (b) hold を別実装にする（menu を保持しつつ余計な pass を開かない）、(c) copy の read 経路を直接計装する（leg 形状に依存しない）、のいずれか。
- **推奨する次の一手**: (c) **copy の read 経路を直接計装**する — `celtium$serveTextureCopy` が読む image の
  layout・format・extent と、その image に最後に書いた pass を印字する。leg 形状に依存せず、白の原因を VUID と独立に決められる。
- **未了（変更なし・revert 済みのみ）**: このセッションの3つの layout 仮説はいずれも棄却・revert 済みで、ツリーは `23759c228` 相当で緑。

## 0.268 **round 74: 重要な訂正 — VUID は menu hold の leg だけに出る（hold なしの menu leg は 0件）＝白い copy と VUID は独立**

- **既存 leg の比較（Lead）**:
  ```
  chain-menu  (menu, hold なし)      VUID = 0
  pano-life   (menu)                 VUID = 0
  alpha-on    (menu)                 VUID = 0
  pano-bind   (menu hold 40)         VUID = 2   VUID-vkCmdDraw-None-09600
  pano-fix    (menu hold 40)         VUID = 2   同上
  r66-layout  (menu hold 60)         VUID = 2
  r70-perdesc (menu hold 60)         VUID = 2
  ```
  ＝**VUID は `menuHoldTicks` を使う leg にだけ出る**（hold なしの menu leg はすべて 0）。
- **訂正**: 以前「この VUID がパノラマの白と見える背景の間の最後の1枚」と書いたが**誤り**。
  **白い copy は VUID=0 の leg（`chain-menu`・`pano-life`・`alpha-on`）でも出ている**＝**両者は独立**。
  VUID は **hold 経路（world なしで menu フレームを繰り返す）が露呈させる別の問題**で、パノラマの白の原因ではない。
- **次の測定（白の側）**: **hold なしの menu leg** を**現行 jar（replay 修正入り）**で取り、`CELTIUM_TEX_COPY_SERVED … mean=` を読む。
  それでも白なら **copy の read 経路**（`readFbo=0` が何を読むか・readback の実装）が次の容疑者で、layout 仮説は完全に死ぬ。
- **VUID 側（別 unit）**: hold 経路のみに出るので、まず `menuHoldTicks` の実装（world 生成前の menu フレーム）が
  どの pass を開いているかを確認し、hold なしとの差を1行で説明できるようにする。

## 0.267 **round 73: VUID は menu 経路だけ（terrain leg は 0件）— 探索範囲が menu の pass に確定**

- **測定（既存 leg の比較・Lead）**:
  ```
  r69-canon   (terrain + keepGui + captureEntities + keepEntities, layers ON)  VUID = 0
  r70-perdesc (menu hold 60, layers ON)                                        VUID = 2  VUID-vkCmdDraw-None-09600
  r66-layout  (menu hold 60, layers ON)                                        VUID = 2  同上
  ```
  ＝**terrain 経路は完全に clean**、VUID は **menu 経路だけ**。パノラマの白と VUID は同じ経路に閉じている。
- **3棄却＋この比較から確定した探索範囲**: menu の `recordImmediateOnlyPass`（standalone UI pass）が pair を attach する側の barrier 記録。
  即時 descriptor 側の layout 仮説は**全て棄却済み**（2→4 / 2→20+20 / 2→4）なので再試行しない。
- **次の測定（menu の pass 内の遷移を数える）**: `chainPairColorImage()` について
  「どの barrier がどの oldLayout→newLayout で記録されたか」を bounded に印字し、
  VUID を出した draw の直前の layout を確定する。その上で **attach 側の barrier が tracked layout を使う**ようにする
  （frame image が `:753` で行っているのと同じ形・texture-mirror の change list）。
- **パノラマの他要素（mirror 内容・bind・捕捉・menu hold）は全て確定済み**なので、この1点が閉じれば row が閉じる。

## 0.266 **round 72: 3つ目の仮説（per-descriptor 分岐）も棄却（VUID 2→4）・revert — 即時 descriptor 経路は VUID の owner ではない**

- **仮説3**: `VulkanImmediateTexturePipeline` の descriptor を「その view が pass の attach した image と一致するときだけ GENERAL、他は SHADER_READ_ONLY」に。
  backend が `PASS_ATTACHED_IMAGE` を pass の前後で publish/clear する形。
- **測定（`r70-perdesc`・layers ON・menu hold 60）**: **VUID 4件**（baseline は 2件）・copy は依然白。＝**棄却**・revert（build 緑）。
- **3つの棄却の帰結（重要）**: pass-end hand-back（2→4）・一律 GENERAL（2→20+20）・per-descriptor（2→4）。
  **即時 descriptor 経路への変更はどれも VUID を増やすだけ**＝**baseline の2件の owner は即時経路ではない**。
  次の探索は **terrain pass / composite 側**（pair を attach する側の barrier 記録）に移すべきで、即時側の layout 仮説は**もう試さない**。
- **パノラマの残りはこの VUID のみ**（mirror 内容・bind・捕捉・menu hold は全て確定済み）。

## 0.265 **round 71: セッション末の回帰 leg（canon 行）— 描画カウンタは健全・picture gate は flag 依存で FAILED**

- **leg `r69-canon`（terrain + keepGui + captureEntities + keepEntities・layers ON・`presentReadback`）**:
  ```
  drawnByFormat = [1:111 2:3214 3:0 4:36521 5:178 6:1236 7:1185 8:128 9:228 10:0]
  windowFlushFailures = 0    nonPlayerEntitiesAtCapture = 9    worldTicksAtEnd = 241
  presentedImageVerdict = FAILED_PRESENTED_IMAGE
  ```
  ＝**id9(entity/hand) 228・id4(terrain) 36521・flush failure 0・entity 9体**＝このセッションの修正群（frame arena・upload batch・clear 分類・clip no-flip・polygon offset 捕捉・font セパレータ・BH-22 退役・device feature）で**描画カウンタの回帰は無い**。
- **picture gate は FAILED のまま**（未較正）。この leg は `keepGui`+`captureEntities`+`keepEntities` で**scene key が較正済みのものと異なる**ため、
  既報の `UNCALIBRATED_PRESENTED_IMAGE` と同じ扱いにする（**成功扱いにしない**・較正済み leg での再取得が要る）。
- **未了のまま次に残すもの**: (1) menu 経路の layout VUID（pair を sample する draw の特定 → per-descriptor 分岐）、
  (2) OPT-09 step (3)（同一 geometry と透明順の比較）、(3) BH-14 per-identity pair、(4) goal (e) の3段階（変更→反映→復元/reload）、
  (5) 較正済み scene での picture gate 再取得。

## 0.264 **round 70: 引き継ぎ整理 — パノラマは「pair を sample する draw の特定」だけが残り、他は受入済み**

- **このセッションで確定したパノラマの事実（すべて測定済み）**:
  1. 早期 announcement の replay が効く（`id=45 firstOpaque=0xff8fa3ff` 等・実内容の mirror）＝修正 `a86273436`。
  2. menu hold で copy が走る（`menuHoldTicks`・gui-stage `81d29010a`）。
  3. bind は正しい（42/44/45-48 を unit 0 に）／group の texture 捕捉は stale ではない（A/B で確定・probe 削除）。
  4. copy は依然 `mean=255,255,255`、`VUID-vkCmdDraw-None-09600` が2件（handle `0x360000000036`）。
- **棄却済みの仮説（再試行しない）**: pass 終了時の read-only hand-back（VUID 2→4）・descriptor の一律 GENERAL（2→20+20）。
- **残る唯一の作業**: **pair を sample する draw の特定**（descriptor の view と `passAttachedColorImage` を並べる bounded 計器）。
  その上で **per-descriptor 条件分岐**（view が pass-attached image と一致するときだけ GENERAL、他は SHADER_READ_ONLY）を入れる。
  受入は menu-hold leg で `validationMessageCount=0` かつ copy の `mean=` が 255 から動くこと。
- **注意（このセッションの教訓）**: barrier/descriptor の変更は必ず leg で測る。2つの仮説はいずれも「もっともらしい」が測定で棄却された。

## 0.263 **round 69: descriptor の imageLayout を GENERAL にした仮説も棄却（20+20 VUID に増加）・revert**

- **仮説2（`VulkanImmediateTexturePipeline` の descriptor を `VK_IMAGE_LAYOUT_GENERAL` に）**: GENERAL は sampled にも attachment にも合法なので
  VUID が構造的に消えるはず、という筋。
- **測定（`r67-gen`・layers ON・menu hold 60）**: **`VUID-vkCmdDraw-None-09600` が 20件**＋**新たに `VUID-vkCmdDrawIndexed-imageLayout-00344` が 20件**、
  copy は依然白。＝**棄却**（大多数の texture は SHADER_READ_ONLY にあり、descriptor を GENERAL にすると今度はそちらと食い違う）。
- **revert**: 悪化させた変更は残さない（build 緑）。
- **2つの棄却から確定したこと**: **layout と descriptor 宣言は大多数で整合しており（SHADER_READ_ONLY）、不一致は pair を sample する一部の draw だけ**。
  したがって修正は**descriptor 単位の条件分岐**（その descriptor の view が `passAttachedColorImage` と一致するときだけ GENERAL、他は SHADER_READ_ONLY）が正しい形。
- **次の測定**: その条件分岐を入れる前に、**VUID を出した draw の descriptor がどの view を指しているか**を印字する
  （`passAttachedColorImage` と descriptor の view を並べる・bounded）。これで「pair を sample する draw が実在する」ことを確認してから直す。

## 0.262 **round 68: pass-end hand-back を試して測定 → VUID が 2→4 に増加＝仮説は棄却・revert（`eb147a5cc`）**

- **実装した仮説（`51cf1ac14`）**: 2つの `vkCmdEndRenderPass` の直後で `passAttachedColorImage` を 0 にしてから
  `celtium$transitionPublishedFrameTextures(false)` を呼び、published name を pass 終了時に SHADER_READ_ONLY へ戻す。
- **測定（`r66-layout`・layers ON・menu hold 60）**: **`VUID-vkCmdDraw-None-09600` が 4件**（修正前は 2件）・copy は依然 `mean=255,255,255`。
  ＝**仮説は棄却**。pass 終了時に read-only へ戻すのは、この VUID の原因ではない（むしろ増やした）。
- **revert（`eb147a5cc`）**: 悪化させた変更を残さない。build 緑。
- **この測定が示すこと**: VUID は「pass の外で sample する draw」ではなく、**pass の内側**で起きている可能性が高い
  （`passAttachedColorImage` を消して遷移を足したことで、attach 側の skip が効かなくなり増えた＝**pass 内の draw が pair を sample している**）。
  次の仮説は「**同一 pass 内で attach した image を同じ pass の draw が sample する**フィードバック経路」で、
  修正は descriptor の `imageLayout` を **GENERAL** にする（`VK_IMAGE_LAYOUT_GENERAL` は sampled にも attachment にも合法）こと。
- **未確定**: どの draw がどの descriptor で sample しているか。次は VUID の発生箇所に
  `passAttachedColorImage` と `commandState.inRenderPass()` を並べて印字する。

## 0.261 **round 67: hand-back は「次の pass の直前」にしか無い＝pass 終了後〜次の pass の間に sample する draw が GENERAL を見る**

- **コード確認（Lead）**: `vkCmdBeginRenderPass` は **2箇所だけ**（`:7167`・`:8639`）で、どちらも直前に
  `celtium$transitionPublishedFrameTextures(false)`（`:7166`・`:8638`）が呼ばれている＝**pass 入口側は覆われている**。
  しかし観測された VUID は `vkCmdDraw-None-09600` で、**draw が sample する時点**の layout が問題。
- **結論**: published name（pair の image）は **pass が attach した後 GENERAL のまま**残り、
  **次の pass が始まる直前まで戻されない**。その間に **draw が descriptor（SHADER_READ_ONLY 宣言）で sample すると VUID**。
  つまり hand-back の場所が「次の pass の直前」しか無いことが穴。
- **修正（次に着手する担当へ）**: **pass の終了直後（`vkCmdEndRenderPass` の後）にも published name を
  SHADER_READ_ONLY へ戻す**（`celtium$transitionPublishedFrameTextures(false)` を pass end に追加）。
  これで「pass の外で sample する draw」が常に read-only を見る。off-target pair も同形。
  受入は menu-hold leg で **`validationMessageCount=0`** かつ copy の `mean=` が 255 から動くこと（handle `0x360000000036`）。
- **注意**: barrier は記録位置が命なので、変更は leg で検証する（未検証の遷移が今回の VUID を生んだ）。

## 0.260 **round 66: layout VUID の形を特定（`vkCmdDraw` 側＝pass が pair を GENERAL のまま残し、後続の draw が SHADER_READ_ONLY descriptor で sample する）**

- **コード追跡（Lead）**: pair の hand-back は既に存在する — `celtium$transitionPublishedFrameTextures(boolean general)`（定義 `:10364`）が
  `publishedFrameTextures` の各名前に barrier を記録し、`:7089`（clear/attach の前・`true`＝GENERAL へ）と `:7166`/`:8638`（`false`＝sampling へ）/
  `:10043`（`true`）から呼ばれている。コメント自身が `vkCmdBeginRenderPass` の `initialLayout` VUID を避ける設計を説明している。
- **しかし観測された VUID は `vkCmdDraw-None-09600`**（`vkCmdBeginRenderPass` ではない）＝**draw が descriptor で期待する layout と、
  その時点の image layout が一致していない**。pass が pair を **GENERAL のまま残し**、後続の draw が
  **`SHADER_READ_ONLY_OPTIMAL` を宣言した descriptor** でそれを sample すると、この VUID が両方向に出る（`validationMessageCount=2`）。
- **修正方向（次に着手する担当へ）**: pass が pair を attach した後、**その pair を sample する draw より前に SHADER_READ_ONLY へ戻す** barrier を
  記録する（`celtium$transitionPublishedFrameTextures(false)` がその役だが、`:7089` の `true` の後に `false` が来ていない経路がある）。
  または descriptor 側の `imageLayout` を実際の layout に合わせる。**texture-mirror の change list**（`chainPairLayout` フィールド＋
  `noteChainPairLayout`＋barrier site の `oldLayout` 根拠化・off-target pair も同様）が正しい形で、受入は
  `validationMessageCount=0` かつ copy の `mean=` が 255 から動くこと（handle `0x360000000036` は copy 行が印字）。
- **未確定**: どの経路で `false` が欠けているか（`:7089` の後・`:10043` の後・composite の bind 後）。次の測定は
  `CELTIUM_READ_RESOLVE`／publish 行に **その時点の pair layout** を載せ、VUID を出した draw の直前に並べること。

## 0.259 **round 65: layout handshake の具体的な在処を特定（frame image は layout を記録・chain pair は記録していない）**

- **コード確認（Lead）**: `GllessFrameTarget` は **frame image の layout を記録している**
  （`colorImageLayout()` / `noteColorImageLayout(int)`・`:731-732` で `COLOR_RENDER_LAYOUT`=`VK_IMAGE_LAYOUT_GENERAL` に初期化・`:753` の release barrier が GENERAL と render layout の間を動かす）。
  一方 **pair 経路は同じ `COLOR_RENDER_LAYOUT`（`:88-89`）を使うが、`chainPairLayout` / `noteChainPairLayout` は存在しない**
  （grep しても frame image 側の2メソッドしか出ない）。
- **これが2つの `VUID-vkCmdDraw-None-09600` の形そのもの**: `0x360000000036`（chain pair）について、
  **sampling 用に `SHADER_READ_ONLY_OPTIMAL` へ動かす側**（publish/read）と **GENERAL attachment として使う側**（pass）が
  **記録された layout を共有していない**ため、片方が誤った `oldLayout`（または無し）から遷移している。
- **unit（texture-mirror へ配布）**: chain pair（および同形の off-target pair）に **frame image と同じ layout bookkeeping** を与える —
  `chainPairLayout` フィールド・barrier 記録箇所で呼ぶ note メソッド・2つの barrier site がそれを `oldLayout` の根拠に使う。
  受入は同じ menu leg で **`validationMessageCount=0`** かつ **copy の `mean=` が 255 から動く**こと（handle `0x360000000036` は copy 行が既に印字）。

## 0.258 **round 64: A/B で「捕捉は stale ではない」と確定（group id は bind に追随）・残りは layout handshake のみ**

- **A/B（texture-mirror・同一 scene・1 flag 差・jar のみ）**:
  ```
  pre-change  jar (pano-bind):  901× texture=46  901× 45  899× 50  899× 49  899× 48
  post-change jar (pano-fix):  1285× texture=46 1285× 45 1283× 50 1283× 49 1283× 48
  ```
  ＝**menu-hold 構成では group の id は panorama PNG そのもの**で、**捕捉は stale ではない**。
  texture-mirror が試した state-key 項は**何も直さず group を 901→1285 に分割しただけ**なので **revert**（正しい判断）。
  Lead の `currentGroupTexture != boundTexture`（`:5155-5157`）の読みも正しいことが確認された。
- **Lead の probe も削除（`b0cbf141d`）**: 同じ問い（offer 時の bound texture）に A/B が答えたため、ENQ 行の `boundNow` は残さない。
- **残りは layout handshake のみ（測定済み・texture-mirror）**: copy は依然白
  （`CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255 readImage=0x360000000036`）で、
  **同じ handle に `VUID-vkCmdDraw-None-09600` が2件**（`validationMessageCount=2`・`validationClean=false`）。
  ＝mirror の内容は正しく、bind も正しく、group の id も追随しているので、**publish/layout の handshake** が
  「正しい mirror 内容」と「見える menu 背景」の間の最後の1枚。
- 未確定として残る: Lead の leg で観測した stale 8 は**構成依存**（その leg では offer が bind より先）で、再現には当該構成が必要。

## 0.257 **round 63: 再利用条件は既に texture 変化で group を閉じている＝stale な 8 は「offer 時点で 8 が bound」だった**

- **原典（コード）確認（Lead）**: group 再利用条件（`VulkanTerrainGpuBackend:5155-5157`）は
  `this.currentGroupFormatId != formatId || **this.currentGroupTexture != boundTexture** || this.currentGroupStateKey != stateKey || !samePass …`
  を含む＝**bound texture が変われば group は閉じる**。したがって `texture=8` の group は
  **その batch が offer された時点で unit 0 に 8 が bound だった**ことになる（捕捉コードの単純な取りこぼしではない）。
- **意味**: panorama の face batch は **bind(45) より前に offer** されている。つまり問題は「offer の順序と bind の順序」で、
  描画時点の bind ではなく **offer 時点の bind** が使われていること。同じ「accept 時 vs flush 時」の状態クラス（BH-04/BH-05/BH-11 と同族）だが、
  ここでは *accept 時点が既に古い* ので、**offer 側の呼び出し順**（display list / 遅延描画の経路）を原典で追う必要がある。
- **次の測定（担当へ）**: `CELTIUM_IMMEDIATE_ENQ`（offer 時）に bound texture を載せ、同じ frame の `CELTIUM_UNIT_BIND` と並べる。
  offer が bind より先なら、`GuiMainMenu.drawPanorama` の bind と `Tessellator` の offer の順序を原典（`:448` 以降）で確認し、
  bind が offer より後になる理由（display list の再実行、`WorldVertexBufferUploader` の遅延）を特定する。
- **menu 経路の layout VUID 2件**（`VUID-vkCmdDraw-None-09600`・image handle `0x360000000036`）も残りの1件。

## 0.256 **round 62: bind は 42/44/45-48（正しい）・group は 8 を記録＝即時捕捉が stale と確定・menu 経路に layout VUID 2件**

- **1つの console で両側が揃った（Lead の `r61-bind`・`menuHoldTicks=60`）**:
  ```
  CELTIUM_UNIT_BIND bind texture=42 (199回) / 44 (200) / 45 (3266) / 46 (3266) / 47 (3266) / 48 (3266)
  CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255
  ```
  ＝**game は panorama PNG（42/44/45-48）を unit 0 に bind している**のに、face の group は **`texture=8`** を記録。
  ＝**即時 group の texture 捕捉が stale**（registry の名前→id 解決は無実）。修正は**捕捉側**:
  group を開いた時点の `immediateState.boundTextureName()`（unit 0）を保持したまま、group 内で bound texture が変わった場合に
  **group を閉じる/再 key する**（`celtium$stateKey` と同じ「状態は group に届く」規則）か、batch ごとに捕捉する。
- **menu 経路の layout VUID 2件（texture-mirror の `pano-bind`）**: `validationMessageCount=2`・`validationClean=false`・
  どちらも `VUID-vkCmdDraw-None-09600`（image が GENERAL 期待で SHADER_READ_ONLY_OPTIMAL／その逆）。publish/layout の handshake で、
  その leg は受入を運べない。次の探索は submit 時点の image handle `0x360000000036` の ledger。
- **受入までの残り**: (1) 捕捉側の修正、(2) menu 経路の layout 穴。mirror の replay・menu hold・copy 行・bind の正しさは全て確認済み。

## 0.255 **round 61: 最後の1点は「bind 側の id」— 既存の `CELTIUM_UNIT_BIND` の bound を上げるだけ**

- **確定済み（Lead の `r59-panohold`）**: panorama PNG は **実内容で mirror**（`id=45 firstOpaque=0xff8fa3ff`・`id=46 0xff90a4ff`・
  `source=announcement size=256x256`）＝早期 announcement replay が効いている。copy も走る（`dest=20 … mean=255,255,255`）。
- **残り**: face の group が **`texture=8`**（splash の白）を運ぶ一方 PNG は 45-49。決定に必要なのは **bind 側の id** で、
  texture-mirror の **`CELTIUM_UNIT_BIND bind texture=N`** がまさにその計器。Lead の leg では **最初の約22 bind に bound** されていて
  panorama の bind に到達していない（観測 id: 0,9,8,6,10,3,2,13）。
- **次の一手（texture-mirror へ）**: その bound を上げる（または panorama id に filter する）だけで1 leg。
  - game が **45-49 を bind** し group が 8 → **即時捕捉が stale**（修正は捕捉側）
  - game 自体が **8 を bind** → **`TextureManager` の名前→object マッピング**（registry 側）
- その行の他の要素（replay 修正・menu hold knob・mirror-source 行・copy 行）はすべて揃っている。

## 0.254 **round 60: menu hold で copy が走るようになり、panorama PNG の mirror は実内容になった（白は残る）**

- **fixture knob 着地（gui-stage・`81d29010a`）**: `-Dceltium.validation.menuHoldTicks=N` が **world 生成前に `GuiMainMenu` を N client tick 保持**
  （既存の menu-phase resize の `return` 地点を再利用・`menuHeldTicks`/`menuHoldTicksApplied` を報告・既定 0 で既存 leg と calibration key は不変）。
- **Lead の受入 leg（`r59-panohold`・`menuHoldTicks=40`・`dumpPresentedMenuTick=20`）**:
  - **`CELTIUM_MIRROR_SOURCE id=45 source=announcement size=256x256 content=opaque=65536/65536 firstOpaque=0xff8fa3ff`**、
    `id=46 … firstOpaque=0xff90a4ff` ＝**panorama PNG が実内容で mirror された**（以前は 256×256 の一様白）。
    ＝**早期 announcement の replay（`a86273436`）が効いている**。
  - **`CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255`** ＝copy は**走るようになった**が、まだ白。
- **残る1点**: face の group が sample する id。以前の leg では **`texture=8`（splash の白）**で、panorama PNG は 45-49 に実内容で存在する。
  ＝**「テクスチャ名 → GL id」解決**（`TextureManager.bindTexture` → `getGlTextureId` を即時 group に取り込む箇所）が最後の容疑者。
  確認測定は texture-mirror が用意した name→id 計器（bind が 45-49 を返すのに group が 8 なら捕捉が stale、bind 自体が 8 なら registry のマッピング）。
- **受入は不変**: copy の `mean=` が 255 から動き crop にパノラマ。

## 0.253 **round 59: 早期 announcement の replay は配達を確認（実内容の mirror が出現）・受入 leg には menu hold が要る**

- **replay の効果を確認（Lead の leg `r58-pano` と texture-mirror の `pano-accept`）**: 早期ロード窓で捨てられていた announcement が
  **実内容つきの `source=announcement` mirror** として現れている — `id=44 firstOpaque=0x1ffffff`・`id=117 0xffd2d2d2`・
  `id=118 0xff000000`（59105/65536 opaque）・Lead の leg では `id=45 0xffb9855c`。`PASSED`・`validationClean=true`・`validationMessageCount=0`。
- **ただし受入 leg の形が受入を運ばない（texture-mirror が正直に報告）**: `CELTIUM_TEX_COPY` が**0行**（copy 経路が走っていない）・
  `menuDumpClientTick` が無く `worldTicksAtEnd=206`（menu dump の条件 `worldTicks == 0 && clientTicks >= 20` を満たす前に world が始まる）・
  `id=8` は依然 splash の白。＝**`GuiMainMenu.renderSkybox` の `glCopyTexSubImage2D` が走る長さだけ menu を保持する leg** が要る。
- **fixture 依頼（gui-stage へ）**: menu-stage hold の knob（`dumpPresentedMenuTick` を上げる、または `menuHoldTicks` 的な property で
  world 生成前に `GuiMainMenu` を N client tick 保持）。`ValidationClient.java` は gui-stage のファイル。
- **受入（その leg で）**: `CELTIUM_TEX_COPY_SERVED … mean=` が 255 から動く・panorama id の `source=announcement` が実内容・crop にパノラマ。

## 0.252 **round 58: パノラマの白は「backend 生成前に来た announcement が捨てられていた」＝早期ロード窓・修正を実装（`a86273436`）**

- **分岐の決定（texture-mirror・`pano-life`・jar `e26be087a`・`validationClean=true`）**:
  ```
  forgetTexture id=8  hadEntry=false announcedUnconsumed=false
  forgetTexture id=20 hadEntry=false announcedUnconsumed=false
  forgetTexture id=8  hadEntry=true  announcedUnconsumed=false
  CELTIUM_LIFECYCLE lines: 3        announceImage lines: 0
  ```
  ＝**`announceImage` は id 8/20 で一度も発火していない**（分岐 (b)）。delete 経路は正常（`forgetTexture(8)` が2回・うち1回は `hadEntry=true`）で、
  mirror が落ちた後に**同じ名前が splash logo の白い `int[]` upload で埋められた**。
- **原因**: menu の panorama PNG は**terrain backend が存在する前にロード**され、hook の
  `TerrainGpuBackends.current() != null` ガードが announcement を**黙って捨てていた**（後で再生する仕組みも無し）。
- **修正（Lead・`a86273436`）**: `TerrainGpuBackends` が**早期 announcement の bounded pending list**（64件・dropped counter・
  `pendingTextureUploadSummary()`）を持ち、`install()` が backend 設置時に**全件を replay**する。hook は backend が無いとき
  **捨てずに保持**し、backend があるときは従来どおり直接渡す。
- **受入（texture-mirror へ依頼）**: `pendingTextureUploads` が非ゼロ（早期 announcement が保持された証拠）・
  panorama の id の `CELTIUM_MIRROR_SOURCE` が `source=announcement` で**実内容**・`CELTIUM_TEX_COPY_SERVED … mean=` が 255 から動き
  crop にパノラマ。名前→id がまだ食い違うなら texture-mirror の name→id 計器が次の step。

## 0.251 **round 57: パノラマ PNG の mirror は正しい（id 45-49）・face が sample する id 8 は splash の残骸＝「名前→id 解決」が真の容疑者**

- **原典と計器の突き合わせ（Lead）**:
  - `GuiMainMenu:448` は cube ごとに `TITLE_PANORAMA_PATHS[k]`（`panorama_0..5.png`）を bind
  - `CELTIUM_MIRROR_SOURCE` の **id 45〜49 は `source=announcement` で実内容**（`firstOpaque=0xff8fa3ff` 等）＝**panorama PNG は正しく mirror されている**
  - face の group は **`texture=8`** を運び、id 8 は **splash の logo**（`Minecraft:980` の `DynamicTexture(ImageIO.read(...))`、後に Forge の
    `SplashProgress.clearVanillaResources`（`:595`）が削除）
  - `BufferedImage` 経路の hook は**存在し名前も正しい**: `TextureUtilMixin:130` が `func_110989_a` に注入し、
    `scripts/mc-sources.sh --srg uploadTextureImageAllocate` が `func_110989_a` = `uploadTextureImageAllocate(ILjava/awt/image/BufferedImage;ZZ)I` を確認
- **結論**: mirror の内容欠陥ではなく、**face の batch が運ぶ id が 8（splash の残骸）で 45-49 ではない**こと。
  容疑者は **bind 経路の「テクスチャ名 → GL id」解決**（`TextureManager.bindTexture` → `getGlTextureId`）を即時 group に取り込む箇所。
- **確認測定（texture-mirror へ配布）**: 同じ menu leg で6つの `panorama_N.png` の location について bind が解決する id と group の `texture=` を並べる。
  bind が 45-49 で group が 8 なら**捕捉が誤り**（stale な last-bound 値）、bind 自体が 8 なら**registry の名前マッピング**が容疑者（彼らのファイル）。
- **受入は不変**: copy の `mean=` が 255 から動き menu crop にパノラマが出る。

## 0.250 **round 56: id 8 は splash の dynamic texture（caller census が確定）＝名前の再利用と mirror の残存**

- **caller census の結果（texture-mirror・`pano-caller`・jar `bb480811a`・`validationClean=true`）**:
  ```
  id=8   256x256 first=0xffffffff callers=[TextureUtil.func_110988_a <- DynamicTexture.func_110564_a:35
          <- DynamicTexture.<init>:18 <- Minecraft.func_180510_a:889 <- SplashProgress.drawVanilla…]
  id=13  16x16   first=0xffffffff  (entity white texture・正しい)
  id=15  16x16   first=0xff0e0e0e  (light map・正しい)
  id=121 1x1     first=0xfff2f2f3  (cloud stand-in・正しい)
  ```
  ＝**id 8 は splash の dynamic texture**（`SplashProgress.drawVanilla` が `new DynamicTexture(256,256)` を白で埋める）。
  menu の cube face は**再利用された GL 名**を sample しており、その mirror は splash の白のまま。panorama PNG の announcement はその名前に届いていない。
- **delete 経路は既に hook 済み（Lead の確認）**: stub の `glDeleteTextures` が `liveNames` から外し **`forgetTexture(name)`** を呼ぶ
  （`CeltiumGlStub.delete`）。backend も `VulkanTerrainGpuBackend:3053` で `forgetTexture` を呼ぶ。
  したがって mirror が splash の白のままである理由は3分岐:
  (a) menu 前に `forgetTexture(8)` が発火していない＝**splash の texture が menu 時点で生存**（lifecycle・weather-water の領域）、
  (b) 発火したが **`announceImage(8)`（panorama PNG の BufferedImage 経路）が来ていない**（announcement 経路）、
  (c) 発火し announcement も来たが retention 規則で落ちた。
- **分離計器（texture-mirror へ配布）**: その名前について `forgetTexture(name)` と `announceImage(name)` を**frame 番号つき**で1行ずつ印字。
  leg の log が3分岐のどれかを直接示す。(b) なら announcement 経路の修正、(a) なら splash の texture lifetime として weather-water へ回す。
- **受入は不変**: copy の `mean=` が 255 から動き menu crop にパノラマが出る。

## 0.249 **round 55: 原典がパノラマの texture を名指し（cube は panorama_N.png・copy 先は backgroundTexture）＝mirror が白いのは PNG の中身が announce されていない**

- **原典確認（Lead）**: `GuiMainMenu`（decompile）:
  - `:448` cube の 8×8 グリッドが `TITLE_PANORAMA_PATHS[k]` を bind
  - `:480` `backgroundTexture` を bind → `:483` **`glCopyTexSubImage2D(3553,0,0,0,0,0,256,256)`**
  - `TITLE_PANORAMA_PATHS` = `textures/gui/title/background/panorama_0..5.png`（**6枚の 256×256 PNG**）
  ＝**id 8/13/45..49 は panorama PNG そのもの、id 20 は backgroundTexture** で、**id の取り違えではない**。
  欠陥は「**PNG の mirror が白**」— `panorama_N.png` には実画素があるのに mirror は 65536 texel すべて `0xffffffff`。
- **問いの形（texture-mirror へ）**: `announced=0`（int[] 経路）なので、`SimpleTexture` のロードが
  `TextureUtil.uploadTextureImageAllocate` → **`glTexImage2D`（確保・データは空/白）** → 実画素は **`glTexSubImage2D`** という順なら、
  `func_110988_a` hook が**確保の側で発火**していて PNG を運んでいないことになる。
  分離: **id 8 の hook の caller census**（bounded・BH-06 の形）＋**同じ id に非白の2回目の announce が来るか**。
  来なければ hook が確保のみ、来るのに白なら buffer を game が埋める前に読んでいる（hook の timing/aliasing）。
- **受入は不変**: copy の mean が 255 から動き、menu crop にパノラマが出る。

## 0.248 **round 54: パノラマの白は「copy 先 20 と face が sample する 8 が別」＋両方とも announcement が白**

- **既存 leg の grep で決定的な比較（Lead）**: texture-mirror の分析（`content=` は**announcement が渡した画素**を読む＝mirror ではなく announce 自体が白）を受けて、
  `CELTIUM_TEX_COPY` の dest と `CELTIUM_MIRROR_SOURCE` の id を突き合わせた:
  ```
  CELTIUM_TEX_COPY_SERVED dest=20 … 256x256 readFbo=0 mean=255,255,255 opaque=65536/65536 served=1 frame=0
  CELTIUM_MIRROR_SOURCE id=20 source=announced-int[] size=256x256 firstOpaque=0xffffffff announced=0 stale=false full=true
  CELTIUM_MIRROR_SOURCE id=8  source=announced-int[] size=256x256 firstOpaque=0xffffffff announced=0 stale=false full=true
  ```
  ＝**copy は 20 を埋め、cube face は 8 を sample している（別の texture）**、そして**両方の mirror が自分の announcement 由来で白**。
- **次の3問（優先順・texture-mirror へ配布）**:
  1. **id 8 と id 20 に白を announce している呼び元**（`func_110988_a` hook の bounded caller census＝BH-06 と同じ形）＝「game が白を upload した」か「我々の経路が白で埋めた」かを分ける唯一の方法。
  2. **copy 先(20) と face が sample する(8) がなぜ違うのか** — vanilla では cube は copy が埋める texture を sample する。どちらかの id が誤った GL 名に解決されているなら registry の解決の問題。
  3. **copy の `readFbo=0` が何に解決されるか** — readback の `mean=255,255,255` は同じ白なので、copy の source も白。chain pair に解決されるなら「face 自身の白を 20 に書き戻している」循環であり原因ではない。
- **BH-36/OPT-09 step(2)（`aa20d83af`）と step(3) 未了**、**BH-14 per-identity 未着手**、**videoSettings (e) の3段階未了**は前 round のとおり。

## 0.247 **round 53: パノラマの機序が確定（mirror が白）・BH-36/OPT-09 step(2) 着地（1086 call で 17,651 draw）**

- **パノラマの機序確定（fog-path・`7fb11c10b`）**: 描画側は**すべて測定で棄却**された。
  (a) `colour0=0x0` は**赤いニシン** — 新設 `vertexColour0=` が group 自身の頂点メモリから最初の頂点の packed colour を読み、**`0xffffffff`（不透明白）**。
  (b) alpha test の on/off で copy は同一（`alpha-on`/`alpha-off` とも `mean=255,255,255`）＝shader の読みどおり discard なし。
  (c) 決め手: **texture 8 の mirror が一様な不透明白 256×256**:
  ```
  CELTIUM_MIRROR_SOURCE id=8 source=announced-int[] size=256x256
    content=opaque=65536/65536 firstOpaque=0xffffffff announced=0 hasEntry=true full=true glLess=true
  ```
  ＝face は**白を sample して白を書く**。それが pair の clear 色と同一なので「何も描かれていない」ように見えていた。
  routing・clip・viewport・clear・submission は全て棄却済み。**残る欠陥は mirror の内容**（`announced=0` かつ `full=true`）。
  次の測定は **id 8 の announcement 経路**（announce された size/内容 vs mirror の内容）と、**パノラマの実ソースが mirror されていない別の GL 名**かどうか。texture-mirror へ配布。
- **BH-36/OPT-09 step (2) 着地（proj-channel・`aa20d83af`）**: terrain の batch loop が `VkDrawIndexedIndirectCommand`
  （`indexCount`・`instanceCount=1`・**`firstIndex` は index 単位**・**signed `vertexOffset`**・`firstInstance=0`）を記録し
  `vkCmdDrawIndexedIndirect` で発行、`maxDrawIndirectCount` で clamp。既定 off（`-Dceltium.vulkan.indirectTerrain=true`）。
  直接経路への fallback は**理由つき**: `multiDrawIndirect` なし・limit 未報告・**batch 内で index source が非一様**（BH-02 の規則）・
  packet buffer 作成失敗。実測（`ind2-verify`・terrain+GUI+entities・`--validate`）:
  ```
  terrainIndirect=[offers=1086 calls=1086 draws=17651 clamps=0 growths=1 fallbacks=0 lastFallback=none]
  device contract indirect=[multiDrawIndirect=true drawIndirectFirstInstance=true maxDrawIndirectCount=-1]
  validationMessageCount=0
  ```
  ＝**1086 の indirect call が直接経路の 17,651 draw を運ぶ（16.3:1 削減）**・fallback 0・clamp 0。
  **符号の罠**（最初の leg が `calls=0 fallbacks=1119 lastFallback=…unknown_(0)`）: RADV は `maxDrawIndirectCount=0xFFFFFFFF` を返し
  それが `-1` と印字され、guard `maxDraws <= 0` が「小さな上限なし」を「不明」と誤読していた。**unsigned で読む**よう修正（真の 0 のみ fallback）。
  **step (3)（同一 geometry と透明順の比較）は明示的に未了**で、commit も call 数の削減と fallback 契約のみを主張。
- **Lead の magenta probe は削除**（`27fa1f0dc`）: その問いは安価な計器で答えが出たため、未使用の暫定経路を残さない。

## 0.246 **round 52: パノラマの discard 仮説を原典で棄却・「image に届いていない」vs「clear と同じ色を書いている」の分離実験へ**

- **原典確認（Lead）**: `immediate_tex_color.frag` は
  `colour = texture × v_Color` → `u_AlphaRef < 0` なら combine（定数色との mix）→ そうでなければ
  `alphaFunc = int(u_MvColumn2.y+0.5)` で `alphaFunc != 0` のときだけ discard → fog を mix → `fragColor = colour`。
  face の group は **`alphaFunc=0`＝shader 自身の「test 無効」マーカー**、**`alphaRef=0.0`＝combine 分岐に入らない**ため
  **discard は起きない**。`blend=0` なので fragment は `texture × v_Color` を書き、`colour0=0x0` なら**黒**になるはず。
  しかし copy が読むのは **pair の clear 色（白 `(1,1,1,0)`）**＝**draw が image に何も書いていない**ように見える。
- **分離実験（fog-path へ依頼）**: face の fragment 出力を**他に存在しない色**にする — (a) `-Dceltium.vulkan.debugMagentaFormat=2` で
  `fragColor = vec4(1,0,1,1)` の shader variant、(b) より安価に **push block の捕捉色をマゼンタへ強制**して copy mean を読む。
  mean がマゼンタへ動けば **draw は image に届いており原因は頂点色が 0**、白のままなら **pass が attach されていても draw が届いていない**
  → 次は group が描かれた pipeline（fmt=2 の `drawnByFormat`・その pipeline の vertex layout と stride の一致）を疑う。
  同じ leg で **group の stride と mesh の vertexCount の比較**も印字する（stride 不一致は色属性を誤った offset から読む＝
  非ゼロの draw が見えない別経路）。
- **device feature の前提は `04375d685` で実装済み**（`indirect=[…]` を contract に追加）。

## 0.245 **round 51: device feature の前提を実装（`pEnabledFeatures`）・パノラマは viewport 仮説も弱まり「alpha test / descriptor」へ**

- **device feature の前提（Lead・`04375d685`）**: `createDevice` の `VkDeviceCreateInfo` に **`pEnabledFeatures` が無く全ての optional feature が off**
  だった（`vkCmdDrawIndexedIndirect` は `multiDrawIndirect` を要する＝RADV が advertise していても**合法でない**）。
  **query してサポート時のみ enable**（未サポートを enable すると `vkCreateDevice` が失敗し renderer が丸ごと無くなる）、
  `maxDrawIndirectCount` は **limit** なので properties から読んで記録。
  `CELTIUM_DEVICE_CONTRACT` に **`indirect=[multiDrawIndirect=… drawIndirectFirstInstance=… maxDrawIndirectCount=…]`** を追加
  ＝受入 leg が1行で「何が報告され何が有効化されたか」を読める。挙動変更はこれ以外なし。indirect 経路本体は proj-channel の次の step。
- **パノラマ: viewport 仮説も弱まる（Lead の bounds leg・`r52-bounds`）**:
  ```
  CELTIUM_GROUP_BOUNDS group=1 fmt=2 target=811177456 texture=8 batches=2 vertices=8 indices=12 colour0=0x0
    proj=[m00=0.005 m11=-0.008 m22=0.0 m32=-0.5]
    state=[blend=0 … alphaFunc=0 depthTest=0 depthWrite=1 cull=0 alphaRef=0.0 depthFunc=515]
    ndc x[-1.00..5.00] y[-5.00..1.07] z[0.50..0.50] behind=0
  CELTIUM_GROUP_VIEWPORT gl=0,0 1281x720 clipped=0,0 1280x720 target=1280x720
  ```
  ＝quad の NDC は**NDC 正方形全体を覆い**、viewport も**フルフレーム**。cull off・depth test off なので**画素を書くはず**なのに
  copy は clear 色（白 `(1,1,1,0)`）を読む。残る候補は (1) **`colour0=0x0`（alpha 0）＋alpha test による全 fragment discard**
  （`alphaFunc=0` は vanilla が設定する値ではなく、BH-03 の既定は `GL_GREATER`=516）、(2) **descriptor/texture**（白 texture × alpha 0 × blend off）。
  分離測定は **alpha test を強制 off した leg の copy mean** と、`colour0` の隣に**最初の頂点の packed colour と alpha** を印字すること。
- **BH-14 census・videoSettings の残り・BH-36 の分割は前 round のとおり**。

## 0.244 **round 50: BH-35（未モデル GL 入口の census）・SliderControl の縮退・パノラマは viewport へ・BH-14 census・device feature の前提**

- **BH-35 実装（Lead・`c6a6d3c86`）**: stub は**全ての名前**に trampoline を返し、table 外の名前は generic no-op になるが**どの名前かは記録していなかった**
  ＝mod が未モデルの GL 入口を呼んでも痕跡ゼロ。`celtium$noteUnknownSymbol` で**名前ごとに計数**し、終了時に
  **`CELTIUM_UNKNOWN_SYMBOLS`（頻度順）**を印字、`-Dceltium.vulkan.strictSymbols=true` で到着ごとに印字（`strictSymbolLines` で bounded）。
  ＝「未対応」を**記録された境界**にする（goal C の要求）。
- **SliderControl の縮退（Lead・同 commit）**: 従来は `max > min` を assert し、control は**画面のページ構築中**に作られるため
  **1つの縮退した範囲が画面全体を落としていた**（実測: Celeritas video-options が開けず vanilla へ fallback）。
  3つの**計算された境界**が同じ形で縮退しうる（`computeMaxRangeForRenderDistance`・`ChunkBuilder.getMaxThreadCount`・
  `MuiGuiScaleHook.getMaxGuiScale`）ため、**呼び出し側ではなくここで縮退**させる（範囲が動かない単一値 slider・範囲を割り切る interval）。
- **パノラマは clip を棄却し viewport へ（fog-path・`fa8e304dd`）**: `c68ecefa` で再取得しても `mean=255,255,255`・`readImage == passImage`・
  `completionsSinceDraw=6` は不変。**`CELTIUM_GROUP_SCISSOR` は1行も出ていない**（4行 bounded・最初の scissored group で出る）＝
  **face の group は clip 矩形を持っていない**＝clip 仮説は棄却。残るのは viewport:
  `gl=0,0 256x256 clipped=0,464 256x256 target=1280x720`（panorama face の pass）。
  `0,464` は GL bottom-left → Vulkan top-left の**正しい**写像（`720-(0+256)`）だが、**face の pass は target の下端バンドに描く**。
  もしその pass の geometry が逆の規約（top-left NDC vs bottom-left viewport）なら quad は viewport 矩形外で clip され、
  **pass は走るが image は clear のまま**＝観測と一致。次の測定は **face の NDC bounds と viewport 矩形の比較**
  （`-Dceltium.vulkan.groupBounds=true -Dceltium.vulkan.groupBoundsFormat=2`・一度試して slot wait で落ちた・未取得）。
- **BH-14 の census 着地（sky-draws）**: `GllessFrameTarget.noteIdentityForCensus(identity, isMain)` が
  **main/非main の distinct identity と各 pair の member 集合**を記録し、`identityPairCensus()` が
  `mainIdentities=… nonMainIdentities=… pairs=… widestPair=<pair>(N identities share it) perPair=[chain=…,default=…]` を報告。
  受入の形: 現在 `pairs=2` で widest pair が全非main identity を抱える → **per-identity image の修正で widestPair が 1 に落ち pairs が identity 数へ増える**。
  per-identity image 本体は**未着手**（所有権変更の半端な land を避けた）。census の初回読取は leg 待ち（≥2 非main identity の scene＝glow/outline）。
- **device feature の前提（proj-channel・重要な発見）**: `VulkanContext.createDevice` の `VkDeviceCreateInfo` に **`pEnabledFeatures` が無い**
  ＝**全ての optional feature が off**（`multiDrawIndirect` を含む）。よって `vkCmdDrawIndexedIndirect` は RADV が advertise していても**合法でない**。
  `maxDrawIndirectCount` は limit で `validateDeviceContract`（device 作成前）で読める。proj-channel は「query して**サポート時のみ** enable・limit 記録・
  `CELTIUM_DEVICE_CONTRACT` に `indirect=[…]` 追加」を実装したが**予算内でコンパイルできず revert**（device 作成は半端な変更が致命的）。
  次の分割: (1) 前提のみ（挙動不変・leg で `indirect=[multiDrawIndirect=true …]` と `maxDrawIndirectCount` を読む）、(2) flag-gated indirect path＋counters、(3) geometry/order 比較。
- **videoSettings の残り（gui-stage）**: `guiClickDivertedTo` は Celeritas screen だが **`guiScreenClassAtCapture = GuiVideoSettings`**
  ＝fixture の待機ループが vanilla 画面を再び開くため、**crop は vanilla のもの**。3段階受入の前に「capture される画面を Celeritas にする」修正が要る。
  実ボタン経路は `GuiOptions.buttonList` が空（mod が `GuiOptions` 自体を引き継ぐ）ため**この画面では駆動できない**と判明・caveat 記録。

## 0.243 **round 49: video-options 画面が開いた（受入の第一段）・パノラマは clip 仮説の leg 待ち**

- **videoSettings 修正の受入（gui-stage・`vs-open2`）**: `guiClickDivertedTo = org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen`・
  **`presentedImageVerdict=PASSED`** ＝**Celeritas の video-options 画面が実際に開いた**（0 範囲 slider の throw は消えた）。
  `guiScreenOpenedVia` は「no Video Settings button in 0 options buttons; opened with the two calls the game's own handler makes」＝
  fixture は直接開く経路を取った。`scissorCapturedGroups=0` は blend-state の説明どおり
  （glScissor を呼ぶのは Forge の `GuiScrollingList` と `Celeritas` の `VintageDrawContext` のみ）で、この画面が clip しないこと自体は欠陥ではない。
  残る受入は **goal (e)**: 設定を変更 → native 描画に反映 → 復元（reload 含む）を leg で示すこと。
- **パノラマ**: clip 仮説（face の pass が clip で何も書いていない）の menu leg はまだ完走していない（`clip-menu.json` は `chain-menu` を指し report 無し）。
  現行 jar で `CELTIUM_TEX_COPY_SERVED … mean=` が 255 から動くかが判定点。
- **BH-32 の検証 leg は `PASSED`**（`ww48-bh32`）。

## 0.242 **round 48: パノラマの最有力候補を「face の pass が何も書いていない」へ（clip 修正の時期が合う）**

- **推論（Lead）**: fog-path の完了事実は順序・ルーティング・解決を消した。残る数字は
  **copy が face の pass と同一 handle を読む**（`readImage == passImage == 0x…4b20`）・**間に6 submission 完了**・
  window flush は `submitCommands()` → `waitForIdle()`（`:6323-6324`）で**submit 済みかつ完了**・geometry は非空（`vertices=8 indices=12 batches=2`）。
  それでも白なら、**queue で失われたのではなく draw 自体が何も書いていない**（clip で消えた or 退化）。
- **clip が具体的容疑者で時期も合う**: `9c7626783` で clip 矩形の y 反転を除去・無条件化。blend-state の実測では旧反転形が
  GL box `(0,276,639,84)` を `0,360` として適用していた（＝**鏡像のバンドに着地**）。その状態で menu の face が scissor されれば
  **完全に clip されて image は clear のまま**＝観測と一致。fog-path の `comp-menu` leg は**この修正前**。
- **次の測定**: 現行 jar（`c68ecefa`・clip 修正＋clear 分類＋video-options 修正入り）で menu leg を再取得し、
  `CELTIUM_TEX_COPY_SERVED … mean=`・`passImage`/`readImage`・`immediateScissoredGroups`/`scissorCapturedGroups`・
  face の group の `CELTIUM_GROUP_SCISSOR` を読む。mean が 255 から動けば clip が原因でパノラマは閉じる。
  動かなければ次は **face の viewport**、per-window 記録は第3候補に下がる。
- **BH-22・videoSettings 修正・BH-32 の状態は前 round のとおり**（`1b4bb12bd`・`28e3c3335`・`9c7626783`）。

## 0.241 **round 47: videoSettings の真因を修正（0 範囲 slider）・BH-22 の退役と census・パノラマは「窓の破棄」へ**

- **videoSettings の真因（gui-stage の unwrap → Lead の修正・`28e3c3335`）**:
  `IllegalArgumentException: The maximum value must be greater than the minimum value` の出所は **`SodiumGameOptionPages.java` の
  chunk-fade-in 行**で、0 に固定された行が **`new SliderControl(o, 0, 0, 1, …)`** になっていた。`SliderControl` は
  `max > min` を assert（`SliderControl.java:22`）するため **min == max で throw** し、`CeleritasVideoOptionsScreen` の
  コンストラクタが4ページを組む途中で失敗 → **InvocationTargetException wrapper** → fixture が vanilla `GuiVideoSettings` に fallback →
  **scissor caller に到達しない**。修正は **単一値の `CyclingControl`（"0"）**で、tooltip が既に言っている内容と同じ意味を illegal な範囲なしで表す。
  受入: `guiScreenOpenedVia` に Celeritas screen・`scissorCapturedGroups > 0`・crop `(180,80,1100,624)`。
- **BH-22（texture-mirror の設計 → Lead の実装・`1b4bb12bd`）**: 即時 texture pipeline の descriptor cache は `view ^ generation` key で、
  **何も entry を消していなかった**ため generation bump（mirror refresh・sampler 再作成・再確保）ごとに追加され、有限 pool を枯渇させていた。
  miss 時に**cache が pool 容量に達したら最古の entry を退役**し、census（`descriptorSetsLive`/`Retired`/`AwaitingCompletion`/`Allocated`）を
  通常 leg で読めるようにした。**descriptor set は free しない**（記録済み command buffer が飛行中の可能性・OPT-04 は完了のみを解放根拠とする）＝
  退役 set は完了 gate 付き reset 用の list に保持。この gap は commit に明記。
- **パノラマは「窓の破棄」へ（fog-path・`5110647f0`）**: 完了事実の計測で
  `lastDrawnImage=0x…7bb0`・`passAttached=0x…7bb0`・**`lastDrawnEpoch=27 completedEpochNow=33 completionsSinceDraw=6`**。
  `readImage=0x…4b20` は **chain pair** で `DRAW_SITE #1 target=781913368` の `passImage` と一致。
  ＝**copy は face が描いた image を読み、間に6 submission が完了している**＝順序・ルーティング・解決はすべて正しい。
  残るのは **face 自身の submission**: (1) face の pass を記録した window が submit 前に**破棄/reset**された（flush retry が reset して再記録する）、
  (2) submit が失敗/skip された、(3) 未捕捉の later clear（最も低い確率）。`windowFlushFailures`・`unflushedTargetPassFailures`・
  `immediateGroupsDiscarded` はすべて 0。次の測定は **chain pair の pass の per-window 記録**（submit に到達したか・破棄理由）。
- **BH-32 の未確立（weather-water）**: (a) terrain pass の**実効 maxLod がどこにも印字されない**（mirror 自身の sampler のみ）＝
  `atlasSamplerMaxLod` の1行レポートが要る、(b) fixture に **mipmap property が無い**ため「異なる mipmap 設定」の半分は knob か resource reload が要る。
- **BH-29 night/day 受入**（night luma 35.3 対 day 134.6・entity/terrain 比 0.581 対 1.788）は既報のとおり。

## 0.240 **round 46: videoSettings の throw を名指しできるように（wrapper を unwrap）・BH-32 実装・chain-menu の追確認**

- **videoSettings の欠陥（gui-stage の診断＋Lead の実装・`38b685b0d`）**: fixture は `mouseClicked` を**反射で呼ぶ**ため catch が握るのは
  `InvocationTargetException` の**wrapper**で、`toString()` に cause が出ない＝`guiScreenOpenedVia` は「throw があった」ことしか記録できなかった。
  **wrapper を unwrap**し、cause を**一度だけ stack つきで印字**（`CELTIUM_GUI_CLICK_FAILED`・flag で bounded）、fallback 記録も cause を使う。
  候補集合は `CeleritasVideoOptionsScreen` コンストラクタの4つの page builder で、特に `CommonOptionPages.performance(CeleritasVintage.options())`
  が mod state を読む唯一のもの。gui-stage の task-31 pair では同じ click が**成功していた**ので**回帰**。
  受入: leg が `guiScreenOpenedVia` に Celeritas screen を記録・`scissorCapturedGroups > 0`・crop `(180,80,1100,624)` がその画面。
- **BH-32 実装（Lead・`9c7626783`）**: terrain atlas sampler の `maxLod` を `min(configured, atlas.mipLevels()-1)` に。
  cached sampler を値で key し、reload で存在しない level を読まない。受入は weather-water が `patchLevels` census と mipmap 設定差の leg で取る。
- **chain-menu leg の追確認（fog-path）**: `CELTIUM_CHAIN_CLEAR frame=0 site=first-non-main-pass image=0x…2f50 target=1938698341 onMain=false
  activeTarget=0 windowsThisFrame=0 lastTerrainPassFrame=-1`、`passImage=0x…2f50`（face の pass が attach した image と一致）。
  ＝**pair の clear は face の pass より前**で、copy は同じ image を読む。残るのは記録と実行のギャップ（copy 時点の完了事実で分離）。
- **未着手のまま明記**: BH-14（identity ごとの pair）・BH-17（UBO）・BH-22・BH-35・BH-36/OPT-09・OPT-08・F3 の残ラグ。

## 0.239 **round 45: font バー受入（ユーザーの 1x crop で 26/36→0）・readback 暗転の回帰を routing 判定で解消・パノラマは「記録と実行のギャップ」へ**

- **font セパレータ受入（glyph-fix・`8fcaf5802`）**: `modes={5=1223657}`（run は strip のまま）・
  **`fontStripSeparators=1138913` が `fontQuadsAppended − fontRunsStarted` = 1,141,398 − 2,485 に厳密一致**
  ＝**glyph 境界ごとに1つ**（各2退化頂点）。texel 検査は不変（**449/449・104/104**・空 texel 0）。
  **バーは 26/36 → 0**（detector-safe な gap 列・3 leg 一致）。`refused=0`（1,223,657 quad）・
  **0.23 µs/glyph**（基準 0.56）。受入 crop は**ユーザーの 1x スケール** `font-f3-1x-before-after.png`。
  訂正2件: 以前の「105/165」は detector の ±2px footprint で膨らんでいた（正直な指標は 26/36→0）、
  `fontStripSeparators` は**印字 site が無く**、font summary は shutdown hook のため早期終了 leg では失われる → 検証 census に移動。
- **readback 暗転の回帰を解消（Lead・`d96232125` + fog-path の再取得）**: `51003a602`（PASSED・meanAtCapture 30.72）→
  `f02ae2cce`（FAILED・3.82）の暗転は **BH-16 の drop 規則が frame 自身の clear を落としていた**ため
  （`owner=1` は game の main render target でありうる）。分類を **backend の routing 判定**に変更した結果:
  `chain-terrain` leg で **`presentedImageVerdict=PASSED`・`presentedNonBackgroundFraction=0.952174`（修正前 0.952229 と同画像）・
  `gameClearsDroppedForeignTarget=0`・`gpuStalled=false`** ＝**回帰は解消**。
- **パノラマの残りは「記録と実行のギャップ」へ（fog-path・`276416698`）**: 新計器 `CELTIUM_CHAIN_CLEAR` により
  log 順は `CLEAR_LATCH 233` → **`CHAIN_CLEAR 236`（site=first-non-main-pass・pair image・target=1938698341）** →
  `DRAW_SITE #1 239`（face が描く）→ `TEX_COPY_SERVED 802`。＝**pair 自身の clear は face の draw より前**で、
  **foreign clear も pair 自身の clear も順序では説明できない**。残るのは**記録と実行のギャップ**:
  copy の read が face の pass より先に submit/execute されれば、copy は clear 後の image を見る。
  分離測定は **copy 時点の完了事実**（fence/timestamp）を `readImage=`/`frame=` の隣に印字すること。
- **OPT-10 の訂正（blend-state）**: **fog は key gap ではなかった**（`celtium$fogMoved()` が BH-04 の fog 半分以来 group-open テストにあり、
  fog 変化は group を閉じる）。真の gap は light params と scissor の2つで**両方閉じた**
  （light generation boundary・scissor 捕捉＋boundary＋no-flip）。OPT-10 に残るのは **command-state cache の半分のみ**。
- **clip 受入（blend-state・`61d06ef39`/`adca47755`）**: 同一 jar・1 flag 差で
  green quad が control では x 318..959（未 clip）→ 修正側 x 318..638、yellow は 639..959。
  `CELTIUM_GROUP_SCISSOR glBox=0,276 639x84 applied=0,276 639x84`（以前は 0,360）。
  `scissorBoundarySplits=473`・`scissorCapturedGroups=368`（videoSettings leg は 0）。
- **新規の欠陥候補（blend-state が命名・未着手）**: videoSettings scene は **scissor caller に到達しない** —
  fixture の `GuiOptions.actionPerformed` への click が **`InvocationTargetException`** を投げ、vanilla `GuiVideoSettings` に
  fallback する（`guiScreenOpenedVia` に記録）。Celeritas の video-options 画面が開くべきなら**別欠陥**（goal (e) の受入に直結）。

## 0.238 **round 44: BH-11 受入（counter＋絵）・BH-10 を pixel で証明・clip の y 反転バグを修正・BH-32 実装**

- **BH-11 受入（acceptance・`r38-break`/`r38-control`）**: jar `9186d898`・HEAD `ae32bdbe7`・fixture `fe349299`。
  `breakBlock=true` で **`immediateDepthBiasedGroups=48`**・`polygonOffsetCalls=96`。
  対照（`breakSubjectOnly=true`・`breakProgressCalls=0`）は **`groups=0`・`calls=0`** ＝1フラグ分離が機能。
  さらに**絵が動く**: 修正前後の block region は **mean|d| 18.75・29.1% の画素が >8**。旧 jar は crack が stone に**淡く混ざり**、
  新 jar は **solid black＋赤の stage-5 highlight が face の前に立つ**。以前の「動かない」測定（`r31` 対 `r30` mean|d| 0.70）は
  **どちらも修正前**だったため＝counter leg が「bias すべき対象が無い」と「bias が適用されていない」を分離した。
- **BH-10 を pixel で証明（blend-state・probe）**: clouds scene は同一 jar・同一 flag の2 run で rows 60-190 が **18.4% 差**（雲が動く）ため
  使えない。`-Dceltium.validation.stateProbes` で **左を depth-only（colorMask 全 false）・右を全チャネル**のマゼンタ quad として描き、
  対照（`-Dceltium.vulkan.immediateColorMask=false`）は x 75..254 と 1023..1202 の両方にマゼンタ、修正側は 1023..1202 のみ
  （depth-only 矩形は背景 (97,60,55) のまま）。`colorMaskHistogram=[0x0:397 0x7:357 0xf:6645]`。
- **clip の y 反転バグを修正（Lead・`9c7626783`）**: `VulkanSwapchain` は shared image の row 0 が**絵の下端**（GL texture 順）であるため
  逆順に blit する。game も GL も y を下端から測るので、**image 上の行は `y..y+h` で反転不要**。反転形（`targetHeight-(y+h)`）は
  GL box `(0,276,639,84)` を `0,360` として適用し quad を完全に clip していた（probe の実測）。
  **no-flip を無条件化**し flag を削除（既知の誤りへ戻す switch は罠・恒久的正しさを lab flag に依存させない）。
  修正前の形は `-Dceltium.vulkan.groupScissor=false` で到達可能。
- **BH-32 実装（Lead）**: terrain atlas sampler の `maxLod` を**実際に mirror された level 数**に追従させる
  （`min(configured, atlas.mipLevels()-1)`）。cached sampler を値で key し、reload（mipmap 設定・resource pack 切替）で
  存在しない level を読まないよう再作成する。
- **scissor の境界分割（blend-state）**: `scissorBoundarySplits=228`・`scissorCapturedGroups=180`。
  light-params の境界も着地（`lightParamBoundarySplits` 226-613/leg・値が実際に変わった時だけ generation を進める）。

## 0.237 **round 43: BH-16 の leg は旧 jar・ただし行順が機序を確認（clear は face の draw の後・copy の前）**

- **`bh16-menu` leg は修正前の jar**（lock JSON の mod hash `3481c429`/`e2b449c7`/`e415bf3f`、現行は `ce555f18`）で `mean=255,255,255` のまま
  ＝**修正に対する反証ではない**（再取得が必要）。
- **しかし行順が機序を確認**: 238行 `CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x738e3065`（**0x738e3065 = 1938698341
  ＝face 自身の identity**）→ 243行 `CELTIUM_DRAW_SITE #1 target=1938698341 batches=2`（**face が描く**）→
  **324〜328行に5つの `CELTIUM_CLEAR_LATCH route=pair`**（うち `0x62b7b4d6` は別 framebuffer）＝**draw の後・copy の前**の wipe window。
- 次の測定: 現行 jar で同じ menu leg を取り `gameClearsDroppedForeignTarget`・`lastDroppedClearOwner`・copy の `mean=` を読む。
  **drop 数が 0 なら clear は `applyGameClears` に到達しておらず**（latch が消費している）、wipe は frame 最初の非main pass の
  `clearChainPair` 側ということになる（次の探索点として明記）。
- **BH-11 の修正（`ae32bdbe7`）と font セパレータ（`51003a602`）は leg 待ち**。

## 0.236 **round 42: BH-11 の真の機序＝「draw 時に live を読む」→ group へ捕捉して修正・glyph mode 5 を独立確認**

- **BH-11 の3分岐が決着（acceptance・`r36-break`）**: `polygonOffsetCalls=62`（**呼び出しは stub に届いている**）・
  `polygonOffsetCalled=true`・`polygonOffsetFillEnabled=false`（**report 時点のスナップショット**で、vanilla は同 frame 内で無効化するため
  分岐の判定には使えない）・**`immediateDepthBiasedGroups=0`（overlay は frame 内にある）**。
  **機序**: backend が **draw 時に live な flag を読んでいた**（`:11816-11817`）一方、vanilla は
  `preRenderDamagedBlocks`/`postRenderDamagedBlocks`（`RenderGlobal.java:1914-1925`）で **overlay の draw の周りだけ** enable/disable する。
  したがって overlay の batch を持つ group が disable 後に flush されると **false を読んで bias が一切付かない**。
  ＝**BH-04/BH-05 と同じ accept-vs-flush の状態クラス**。
- **修正（Lead・`ae32bdbe7`）**: polygon offset（factor/units/enable）を **group を開いた時点で捕捉**（fog・state・clip 矩形と同じ場所）し
  `ImmediateGroup` に保存、draw site は**そこから読む**。`vkCmdSetDepthBias` は全 group で発行のまま（`depthBiasEnable` は静的宣言なので
  動的呼び出しが無いと VUID-07834）、捕捉状態が off なら 0 を書く。
- **glyph の mode 5 を独立確認（acceptance・`r37-f3`）**: `windows=1119596 accepted=1119596 refused=0`・
  **`modes={5=1119596}`**・`fontRunsStarted=2433 fontQuadsAppended=1118942`。**全 window が GL_TRIANGLE_STRIP**で、
  coalesced run（2433 run に 111万 quad）が**連続 glyph を連結**する＝ユーザーの文字間バー。`refused=0` なので損失ではなく mode が機序。
  （計器の注意: summary は JVM shutdown hook なので、harness が terminate した leg では progress line のみ出る）
- **BH-12 の narrowed 総数 215**・**font separator（`51003a602`）の受入待ち**・**BH-16（パノラマ）の受入待ち**。

## 0.235 **round 41: BH-16 の計器を検証サマリへ（drop 数と owner）・font セパレータの leg 待ち**

- **`gameClearsDroppedForeignTarget=` と `lastDroppedClearOwner=` を検証サマリに追加（Lead・`f02ae2cce`）**:
  BH-16 の修正（他 framebuffer の clear を共有 pair に適用しない）が**発火したことを leg が言える**ように。
  これが無いと「修正が効いた」を推測でしか言えなかった。`polygonOffsetCalls`/`FillEnabled`/`Called` と同じ場所に並ぶ。
- **font セパレータ（`51003a602`）の検証待ち**: 期待は `modes={5=…}` のまま（run は strip）＋ **`fontStripSeparators` 非ゼロ**、
  texel 一致 449/449・104/104、**1px バー列（glyph 行の 105/165・±6..12 輝度・一部 +55）が 0 近くへ**、
  `refused=0`・0.56 µs/glyph 維持。受入は**ユーザーの 1x スケール crop**（8x 拡大ではない）。
- **パノラマの検証待ち（fog-path）**: `gameClearsDroppedForeignTarget > 0`・owner が `CELTIUM_CLEAR_LATCH route=pair` の
  identity を指す・`CELTIUM_TEX_COPY_SERVED … mean=` が 255 から動く・menu crop にパノラマ。
  同時に `passImage=`（draw site）と `readImage=`（copy）を同一 frame で比較し、まだ白なら**順序**が次の候補。
- **OPT-07(b) 受入済み**（前 round）・**BH-01/BH-19/BH-25/BH-13/BH-11(counter 待ち)/BH-12 受入済み**。

## 0.234 **round 40: パノラマの機序確定（他 framebuffer の clear が共有 pair を消していた）→ BH-16 修正・OPT-07(b) 受入**

- **機序（fog-path・`702cc725b`・1 frame 1 log）**:
  `CELTIUM_DRAW_SITE #1 frame=0 fmt=2 site=immediate-only-window target=1938698341 onMain=false passOpen=true vertices=8 indices=12 batches=2`
  → `CELTIUM_READ_RESOLVE … chainPair=0x…6290` → `CELTIUM_TEX_COPY_SERVED … frame=0 readImage=0x…6290 mean=255,255,255`。
  その**間に5つの `CELTIUM_CLEAR_LATCH route=pair`**（`boundIdentity` は全て**非main・非ゼロ**・`colour=(1.0,1.0,1.0,0.0)`）。
  ＝**face は chain pair に描かれ、別の framebuffer に対して発行された clear が同じ pair に流れて白く消していた**。
  **非main identity は全て chain pair を共有**するため、ある identity の clear が別 identity の内容を消す。**これが BH-16（clear の target が保存されない）そのもの**。
  ロード画面の白も1 frame 前の同じ形。
- **修正（Lead・`787e13d2d`）**: stub が**pending clear の発行元 framebuffer を記録**（`pendingClearDrawFbo`・-1=なし・0=default）。
  `GllessFrameTarget.applyGameClears` は **owner が非ゼロの clear を適用せず drop** し、`gameClearsDroppedForeignTarget` と
  `lastDroppedClearOwner` で計数・帰属。default framebuffer の clear（owner 0）は従来どおり＝**提示 frame 自身の clear**。
- **順序を1 frame で答えられる計器**: `CELTIUM_DRAW_SITE` に **`passImage=0x…`** を追加（copy line は `frame=`/`readImage=` を持つ）。
  さらに `immediateDrawSiteLogged` をレポートに載せ、「0行」と「limit 到達」を混同しないようにした（fog-path の以前の0行は
  `site=immediate-only-window` のハイフンを regex が拾えなかった grep バグと訂正済み）。
- **OPT-07(b) 受入（perf-ring・`f8ee10446`）**: pending copy が `VulkanBuffer` 参照を保持し `flushUploads()` が handle を解決。
  解放済み destination は copy を **skip して計数**（`uploadSkippedDeletedDestinations=0`）。`ensureCapacity` は grow 前に flush、
  arena segment 再利用は**gate 不要**（CPU 書込順＝submit 順）。
  **P0 と同じ leg 形状で zero VUID・zero `-4`・`validationMessageCount=0`・exit 0**。
  `uploadSubmitWaitsAtEnd` **31343 → 6910（−78%）**・warm waits **7969 → 579（−93%）**、30434 copy → 6825 submission。
  **待ち時間そのものは改善していない**（flush が batch 全体を待つため cold 1.53→1.67ms・warm 1.20→1.35ms/frame）と明記。
  frame interval 比較は machine 競合のため**意図的に不比較**。待ちの除去は OPT-08。

## 0.233 **round 39: 文字間バーの機序を確定して修正（font run は strip）・パノラマは「描いている」と訂正（grep バグ）**

- **文字間の 1px バー（ユーザー報告）— 機序確定＋修正（Lead・`51003a602`）**: font census が **`modes={5=84822}`**
  ＝**全ての glyph window が GL_TRIANGLE_STRIP**。4N 頂点の strip は **4N−2 三角形**を描くので、**前の glyph の最後の頂点が次の glyph の最初の頂点と繋がる**
  ＝quad 間の隙間に出るバーの形（quad なら連結しない）。`celtium$fontAppend` が strip/fan run の連続 glyph 間に
  **退化頂点2つ**（前 glyph の最終頂点のコピー＋新 glyph の先頭頂点のコピー）を挿入し、接続三角形を**面積ゼロ**にして
  何もラスタライズしないようにした（run は1回の submit のまま）。`fontStripSeparators` で計数。
  mode はゲーム自身の `glBegin` から来るので**仮定せず census で測定**した。
- **パノラマの訂正（fog-path・`a8ec86e38`）**: 以前の「`CELTIUM_DRAW_SITE` が0行」は**grep のバグ**だった（site 名が
  `immediate-only-window` でハイフンを含むのに regex が `[a-zA-Z]+`）。実際は menu leg に **24行**あり、最初の行は
  `CELTIUM_DRAW_SITE #1 frame=0 fmt=2 site=immediate-only-window target=1941007851 onMain=false passOpen=true vertices=8 indices=12 batches=2`
  ＝**パノラマの face は実際に描かれている**（chain pair・batches=2・pass は開いている）。「描画ループに届いていない」という結論は**撤回**。
  残る候補は**順序**: copy が face の pass より先に走る／pair が draw 後 copy 前に clear される／copy が別の image を読む。
  次の測定は「draw site と copy の両方に frame 番号と image handle を載せる」こと（未実施・機序は未主張）。
- **同じ leg の他の確定事項**: `offerStages` は全 stage 同数（stage 9 のみ欠落＝全 offer が acceptance 到達）、
  `offTargetOffered=765 = acceptedForOtherTargets=765`、`flushEntries` は one-shot identity が **groups を持った状態で flush** されている、
  font は `quads=84822 submitted=84822 accepted=84822 refused=0`。
- **BH-06 は engine が駆動できる範囲で閉じた（texture-mirror）**: caller census により、全ての bind は texture upload か
  framebuffer attach 由来で、**light map 自身の upload は default unit で bind する**（vanilla は light-map unit を *sampling* のために
  unit 1 へ切り替える）＝`bindsOnOtherUnits=0` は**正しい挙動**。SRG 名は正しく unit switch も 24行見えている。
  合成 reproducer（unit 0 に A → unit 2 に B → unit 0 に戻して A を sample）は fixture hook が要る。

## 0.232 **round 38: offer/accept の矛盾は terrain leg では存在しない（計器が解決）・off-target は 225 描画・verdict PASSED**

- **`offerStages` leg の結果（fog-path の計器・jar `f115fe11c` 系）**:
  `offerStages=[1:6188 2:6188 3:6188 4:6188 5:6188 6:6188 7:6188 8:6188 10:6188 11:6188]`＝**全 offer が全 stage を通る**。
  counter も一致: `offTargetOffered=765`・`offTargetAccepted=765`・`acceptedForOtherTargets=765`。
  **`offTargetDrawn=225`**（Lead の死んだ counter 修正が効いている）・`offTargetCopiedBack=212`・
  `immediateGroupsDrawn=4833`・`immediateRecordedBatches=8224`（submitted=completed=8224）・
  `flushEntries=[1bf147d1: entries=1064 groupsAtEntry=4135, 0: 196/196, 2570d541: 15/28, 3fa40416: 1/1]`・
  `unflushedTargetGroupsDropped=0`・**`presentedImageVerdict=PASSED`**。
  ＝**terrain 経路は健全**で、以前の「793 対 7」は別 jar か、また単位の違う counter の対だった可能性が高い（要確認）。
  **パノラマの白は menu leg 固有の現象**なので、次の leg は menu で `offerStages`・`flushEntries`・`CELTIUM_DRAW_SITE`・copy line を同時に取る。
- **glyph の modes census（Lead・`f61f952d5`）**: `CELTIUM_FONT_GLYPH_SUMMARY` に `modes={…}`。strip/fan なら coalesced run が
  連続 glyph を連結する（ユーザー報告の 1px バーの形）。leg 待ち。
- **BH-11 の3分岐（Lead・`8e765012d`）**: `polygonOffsetCalls`・`polygonOffsetFillEnabled`・`polygonOffsetCalled` を検証サマリに追加。leg 待ち。
- **blend-state の制御 probe（`2d9b347f6`）**: write mask と clip 矩形を既知の矩形として描く probe（picture の主張が 23.5% 床に
  埋もれる問題への対処）。
- **BH-06（texture-mirror）**: `func_179138_g` 修正で `CELTIUM_UNIT_SWITCH` が 24 行出るようになったが、`bindsOnOtherUnits=0`
  （unit 1 が active の間に bind が来ない）。次は `func_179144_i` の caller 計装。

## 0.231 **round 37: パノラマは「offer と accept の間の矛盾」へ再帰属・glyph の draw mode census・BH-01/BH-19 受入**

- **パノラマの矛盾（fog-path・`e9f2d56e8`）**: terrain leg で `offTargetOffered=793` に対し **`acceptedForOtherTargets=7`**、
  `offTargetRefusals=none`・`immediateGroupsDiscarded=0`・`postAcceptDroppedGroups=0`・`unflushedTargetGroupsDropped=0`。
  ＝**約786の off-target offer が「offer 加算（`:4348`）」と「accept 加算（`:4762`）」の間で消えている**。既存 counter では区別不能。
  対策として **`offerStageReached[16]`**（11箇所の stage 代入を `celtium$offerStage(int)` 経由にし、`offerStages=[…]` として報告）を追加。
  `CELTIUM_DRAW_SITE` は `enqAll`+`enqFormat=2` でも**0行**（limit 未消費）で、loop の target filter は原因ではない
  （`immediateGroupsKeptForOtherTargets=0`）。ロード画面の白も同機序の可能性が高く同じ line で確認する。
- **glyph の draw mode census（Lead・`f61f952d5`）**: `CELTIUM_FONT_GLYPH_SUMMARY` に `modes={…}` を追加。
  coalesced run は**run の mode で一括描画**するため、mode が strip/fan なら**連続 glyph が連結**する（4N 頂点の strip は 4N-2 三角形＝
  隣の glyph の頂点と繋がる）＝ユーザー報告の「文字間の 1px バー」の形。quad なら連結しない。
  mode はゲーム自身の `glBegin`（`GlStateManagerImmediateMixin` 経由）から読むので**仮定せず計器で確定**する。
- **BH-01 受入（proj-channel・`93d310631`）**: `VulkanArena.allocate` が `-Dceltium.vulkan.arenaVerify=true` で
  **全 segment の offset が stride の倍数・長さが unit の倍数**であることを検査（違反は数値つきで throw・両結果を計数）。
  leg: `arenaSegmentAlignChecks=864`・`arenaSegmentAlignViolations=0`・`validationMessageCount=0`・exit 0
  ＝initial/split/reuse/post-grow の4ケースを通常 leg 1本で被覆。
- **BH-19 完了（sky-draws）**: swapchain 作成失敗路に `CELTIUM_SWAPCHAIN_FAILED <reasons>` を追加し、
  `-Dceltium.vulkan.assumeNoPresentSupport=true` の contract test で
  `queue family 0 cannot present … forced by … (BH-19 contract test; not a device result)` を実測。
  exit 0・`validationMessageCount=0`・`validationClean=true`・crash 0・present は全て skip。
  **`semaphorePoolRollbacks` は「不在による 0」**（成功路にしか印字されない）と明記。selection 半分（probe surface）は stated-not-done。
- **BH-36/OPT-09 は未着手と明記（proj-channel）**: indirect 経路＋feature/fallback 行列＋透明順の比較は大きく、
  半端に land させない。提案は「flag-gated indirect path を counters 付きで先に、geometry/order 比較を次に」。
- **BH-17 は documented のまま（sky-draws）**: `0..127` を全デバイスで push に残し `128..215` を descriptor set 1 の UBO slice へ。
  OPT-03 を先に読むことが前提。

## 0.230 **round 36: BH-11 は「呼び出し・enable・描画」の3つに分離して計装・パノラマは同一 image 上で空と確定・BH-25 受入**

- **BH-11 が break scene でも 0（acceptance・`41a72b6d2`）**: `immediateDepthBiasedGroups=0`。原因を推測せず3つに分離する計装を追加（`8e765012d`）:
  `polygonOffsetCalls=`（stub に届いた glPolygonOffset の回数）・`polygonOffsetFillEnabled=`（`GL_POLYGON_OFFSET_FILL` の enable ビット）・
  `polygonOffsetCalled=`。次の leg が「呼び出しが届いていない」「enable が記録されていない」「両方真で group が描画ループに届かない」を切り分ける。
  （`GlStateManager.doPolygonOffset` は値が変わったときだけ `GL11.glPolygonOffset` を呼ぶ＝最初の変化は見えるはず）
- **パノラマの再帰属（fog-path・`74b54512f`・単位既知）**: 1 frame で4つの計器を並べると
  faces の enqueue `target=1941007851`・composite `target=0`・publish 名 `glTexture=6 image=0x…a4a0`・
  読み候補 `frameImage=0x…d5a0 / defaultPair=0x…b0c0 / chainPair=0x…a4a0`・copy `mean=255,255,255`。
  ＝**publish 名は chain-pair image**（`chainPair == published`、frameImage は別）で、faces・composite・copy が**同じ image を指している**。
  ルーティング不一致は原因ではない。**`CELTIUM_DRAW_SITE` が `enqFormat=2` でも 0 行**＝fmt=2 の group が描画ループに1つも届いておらず、
  その image は**白い clear のまま**。次の測定（`enqFormat=-1` で loop 自体が入るか）は fog-path が実施。
  ロード画面の白も同じ機序の可能性が高く、同じ line で確認する。
- **BH-25 受入（proj-channel・`ef7577cd5`）**: upload が例外を投げた region の**全 section を名指し**（`consumeFailedUploads()` を interface の default で追加）、
  `RenderSectionManager.processChunkBuildResults` が**その section の commit を skip**（`updateSectionInfo`・translucency commit・build token clear・
  `lastBuiltFrame` 前進を行わない＝再 build キューに残る）。fault 注入 leg: `regionUploadFailures=1`・`sectionsLeftUnbuilt=1`・exit 0・`validationMessageCount=0`。
- **BH-01（proj-channel）**: `alignUnit = lcm(4, stride)` を capacity・split・exact-fit に適用。**landed-unverified** と明記し、
  受入は `arenaVerify` ＋ segment ごとの stride 整合 assertion（GPU 不要）。
- **未確立のまま記録**: BH-36/OPT-09（proj-channel が次）・OPT-07(b)（perf-ring）・BH-06 の unit 追跡・BH-22・BH-32/35・
  F3 の縦バー（真因未特定・font run のバッファ再利用が最有力）・BH-17/19 の evidence。

## 0.229 **round 35-36: ホットバー暗転を上流参照で受入（light-map unit enable キー）・BH-11 counter は break overlay が呼び元**

- **ホットバー暗転（ユーザー報告）を修正・受入（glyph-fix・`4883b1ba8`）**: 規則を **capture 時の light-map unit enable 状態**にキー。
  `EntityRendererMixin` が `enableLightmap`/`disableLightmap` を **SRG `func_180436_i` / `func_175072_h`**（原典で解決・推測しない）で hook し、
  `TerrainGpuBackend.setLightmapUnitEnabled`（interface の default no-op）へ渡す。`celtium$lightmapForGroup()` は unit が **無効**なら `(240,240)`、
  **有効**なら捕捉座標を書き、**model 経路もこれを使う**。既定は enabled＝注入が黙って落ちた場合は現状（捕捉座標）に退避し、
  `lightmapUnitEnables/Disables`（実測 805/906・989/1113）が hook の作動を示す。`-Dceltium.vulkan.lightmapFullBrightGui=false` が一変数対照。
- **機序の実測**: `itemLitGroups=4406 itemLitGuiGroups=0 itemUnlitGroups=116 itemUnlitGuiGroups=116`
  ＝**GUI stage の ITEM batch は全て unlit** なので、plain な hotbar item には座標が届き得ない（apple は rule ON/OFF/参照で画素同一）。
  真の該当は **TESR item**（`RenderItem.java:144-148` → `renderByItem`）で、model 経路が **lit**（`itemLitGuiGroups=201/255`）で world 座標を持っていた。
- **上流参照との比較（night `worldTime=18000`・`heldItem=chest`・全 leg `validationClean=true`・`validationMessageCount=0`・`drawnByFormat` id9 228/228）**:

  | leg | chest の wood 画素 mean / max |
  |---|---|
  | 参照（upstream Celeritas `989ebf2e`・実 GL） | **(106.5, 74.7, 25.6) / (171,121,47)** |
  | 現行 jar・規則 ON | **(111.0, 76.4, 26.2) / (223,158,61)** |
  | 現行 jar・規則 OFF（対照） | (82.2, 48.1, 16.8) / (106,63,26) ＝**明るい画素が無い** |

  world pass は不変（mob band 52.7/52.6・terrain 79.9/80.9）。crop `hotbar-chest-ref-on-off.png`（sha256 `68c90846…`）。
  未確立: ユーザーの実 scene に TESR item があったこと・洞窟 leg・inventory の player model。
- **fixture 変更の開示**: `-Dceltium.validation.heldItem=chest` を追加（`Blocks.CHEST`・apple では TESR 経路に届かない）。fixture jar が変わる（`b4e99ad6…`）。
- **BH-11 の counter は 0（`chest-off`・`r35-outline` の両 leg）**: `lineBatchesNarrowed` は 83/215 で line 経路は生きている。
  原典では `GlStateManager.doPolygonOffset` の呼び元は **`RenderGlobal:1815/1900`（block break overlay）・`RenderGlobal:1915/1925`（entity outline, -1/-10）・
  `RenderTNTPrimed`** の3つのみで、chest と outline の scene には break overlay が無い＝**bias すべき対象が無い**。
  BH-11 の受入 leg は **break scene**（acceptance の `r29-break` は `breakProgressCalls=31`）。そこで 0 なら
  `GlStateManager.doPolygonOffset` が stub の `glPolygonOffset` に届いているかを次に確認する。
- **プロセスの注意（glyph-fix の開示）**: ファイル単位の staging で他担当の未 commit hunk（BH-25 の `failedSections()`・BH-09 の scissor hunk）を
  自分の commit に巻き込んだ（build 緑・損失なし）。以後は hunk 単位で stage する。

## 0.228 **round 35: scissor を group へ捕捉（OPT-10 gap #3）・BH-15 の安全な半分・BH-29 night/day 受入**

- **scissor の group 捕捉（Lead・`c4f72b15f`）**: `glScissor` の矩形と enable を **group を開いた時点で捕捉**し（fog・state と同じ場所）、
  `ImmediateGroup` に保存して flush 時に**そこから読む**。従来は flush 時に live な stub 記録を読んでいたため、group が scissor 変更を
  またぐ場合（F3 のリスト・スクロールするパネル）**最後の矩形が全 batch に適用**されていた。OPT-10 が名指しした3つの key gap のうち
  実バグである1つ。残り2つ（fog が key に無い・light params が enable bit のみ）は**未着手と明記**。
- **BH-15 の安全な半分（fog-path・`49b1f5dff`）**: frame 境界で残った group を **target ごとに drop して計数**
  （`unflushedTargetGroupsDropped`・`unflushedTargetDropTargets`）。受入 leg は device loss なし・`unflushedTargetGroupsDropped=0`・
  `nonBg=0.951773`・`worldTicksAtEnd=206`。**per-target pass は未着手**（下記の単位修正で動機が消えたため）。
- **単位の訂正（fog-path・重要）**: `offTargetPerTarget` の `offered` は **batches**、`atPass` は **groups**、`drawnPasses` は **passes**。
  `677/32/17` は「645 group 未描画」ではなく「677 batch が 32 group として pass に到達し 17 pass が描いた」＝**identity は pass を得ている**。
  以前の attribution とその上に作った drain は前提が崩れ、列名を `batchesOffered=`/`groupsAtPass=`/`drawnPasses=` に改名して理由をコードに明記。
  **パノラマとロード画面の白は単位が既知の測定から再帰属する**（`CELTIUM_TEX_COPY_SERVED … mean=255`・`CELTIUM_READ_RESOLVE` の3候補 image・
  enqueue の `target=`・修正済み列）。
- **BH-29 night/day 受入（weather-water）**: 両 leg `validationClean=true`・`validationMessageCount=0`。
  entity band luma **night 35.3 対 day 134.6（3.8倍暗い）**、entity/terrain luma 比 **0.581（夜）対 1.788（昼）**＝light-map 項の signature。
  未主張: 上流参照との同一 pose/tick 比較。
- **BH-11 の検証シーンは既存（gui-stage）**: fixture の `keepEntities` 経路が **chest と sign を配置**（`ValidationClient.java:3025/3027`）。
  新しい hook は不要で、entity leg ＋ chest/sign の edge crop ＋ `immediateDepthBiasedGroups` が受入。
- **OPT-04 の訂正（frame-composite）**: region teardown は**既に confirmed-idle 基準**（`settleGpu("region eviction")` の後）で以前の分類は誤り。
  実該当は present-mode rebuild の `swapchain.delete()` で、契約をコードに明記。
- **OPT-07 再挑戦は shape (b)**（perf-ring）: batched copy が `VulkanBuffer` 参照を持ち flush 時に handle を解決、消えた buffer は copy を skip して計数。
  **arena segment 再利用は gate 不要**（CPU 書込順＝submit 順）。`ensureCapacity` は grow 前に pending copy を flush。stale handle のクラスを消す形。

## 0.227 **round 34: P0 は2つの独立した原因だった（OPT-07 の batched copy ＋ BH-14 の drain）・両方 revert・安定確認・OPT-05 受入**

- **P0 の原因は2つ**（それぞれ独立に device lost を起こす）:
  1. **OPT-07 の batched copy**（`VulkanContext.uploadToBuffer`）: pending の copy が名指しする `dstBuffer` を呼び元が同一 frame で置換
     （`VulkanArena` の grow → `VulkanBuffer.ensureCapacity` が旧 `VkBuffer` 破棄・world teardown は arena ごと破棄）＝
     `VUID-vkCmdCopyBuffer-dstBuffer-parameter`、その後の submit が `-4`。revert `14ed4615f` / `99095d85e`。
  2. **BH-14 の target-driven drain**（`e87aa0ec1`）: `currentRenderTarget` を差し替えて per-target body を再利用したため、
     **command buffer を開いた identity ではない identity** の acquire/release barrier と pass を記録してしまい `VK_ERROR_DEVICE_LOST`。
     `finally` の復元では既に記録済みの内容を戻せない。revert `e30a23cd6`（counter と instrument は保持）。
- **切り分け実験（fog-path）**: drain ありの HEAD は 3/3 失敗、revert 後は **2/2 成功**（`gpuStalled=false`・`stallOrigin=none`・
  `submitTimeouts=0`・device-lost 文字列 0・crash report 0）。acceptance の対照は regression window を `9bc475cb`（01:50 緑）→
  `513421d0` に固定。
- **Lead の安定確認（両 revert 後・layers ON・terrain + keepGui + captureEntities）**: `DEVICE_LOST`/`-4` **0件**、
  `CELTIUM_GROUPS submitted=631 completed=631`、crash report 0。
- **OPT-05 の受入（perf-ring・`1c6c99320`）**: `frameArena regionsOpened=8316 blockedOpenings=0 reclaimedSlots=700
  promotions=0`・`frameArenaFallbackGroups=0`・`peakVertexBytes=748272`・`peakIndexBytes=195560`・slot1 未確保（1 slot pair で足りる）、
  **`hostBuffersCreated=6` for 8316 groups**（旧: 1 group あたり2 buffer＝約16,632）。warm 166 sample **p50 57.6 / p95 97.8 / p99 123.8 ms**。
  未確立: 完全非同期 upload（OPT-08）・Intel の opening cost・warm p50 の他 jar 比較（scene flag が違い method error）。
- **再挑戦の正しい形**:
  - **OPT-07**: destination が copy pending 中に破棄・置換・segment 再利用されないこと。destination 側 last-use を batch の epoch で gate、
    または flush 時に handle を解決＋arena segment 再利用の gate。
  - **BH-14/15/16**: **target ごとに pass を開く**（その target の framebuffer を bind・state を再発行（OPT-10）・自前の barrier 対を記録）。
    per-target body の使い回しでは device lost になる。
- **ホットバー暗転の規則（glyph-fix・`0af5060ae`）**: pass ベースの full-bright は**絵を変えない**（apple は3者で画素同一・vanilla は元から明るい）。
  真の該当は **TESR item（chest/shulker/banner）が model 経路（lit＋world 座標）を通る**こと。規則は
  **capture 時の light-map unit enable 状態**にキーする（world pass 外では unit が無効＝full-bright、有効時は捕捉座標）。

## 0.226 **round 33-34: P0（device lost）は OPT-07 の batch copy と確定・revert で解消・検証済み**

- **症状**: HEAD `f9150335` の jar で terrain scene が 3/3 leg で device を失う（`vkDeviceWaitIdle returned -4`・
  `vkQueueSubmit returned VkResult -4` from `flushUploads`/`reserveUploadSlot`・exit 255）。Lead の layered leg が
  **最初の fault** を捕捉: `VUID-vkCmdCopyBuffer-dstBuffer-parameter` ×2 ＋ 同じコマンドへの threading VUID →
  `libvulkan_radeon.so` で SIGSEGV（exit -6・23秒）。
- **根因（perf-ring）**: OPT-07 の batched copy が copy を **pending のまま**残すため、batch が名指しする `dstBuffer` を
  呼び元が同一 frame で置換していた——`VulkanArena.write` → `VulkanBuffer.write` → `uploadToBuffer` の後で arena が grow し、
  `VulkanBuffer.ensureCapacity` が古い `VkBuffer` を破棄（world teardown は arena ごと破棄）。結果、後から submit される
  `vkCmdCopyBuffer` が**存在しない handle** を指す。threading は原因ではなく、同じ無効 handle への layer 報告。
- **対処**: `VulkanContext.uploadToBuffer`（両形式）を **settled な one-copy path** に revert（`14ed4615f`）。
  ring / `flushUploads()` / `uploadBatchSummary()` は**未配線のまま** file に残し、revert 地点に契約を明記。
- **検証（perf-ring・jar `a23a6b0b`・layers ON）**: **`-4` なし・VUID 0行・`validationMessageCount=0`・`validationClean=true`・exit 0**、
  capture と verdict に到達。cold 71 sample p50 **53.4** / p95 **74.3** / p99 **151.6 ms**。
  唯一の非緑は既知の picture gate（`FAILED_PRESENTED_IMAGE`・`worldTicksAtEnd=241`）。
  **注意**: その jar には fog-path の BH-14 drain revert も含まれるため、2つの revert を分離していない（copy-path VUID と
  `flushUploads` の stack は drain からは到達不能なので、機序としては分離できている）。
- **OPT-05 は無関係**（`52e45948a`・検証 leg でも active）。`VulkanFrameArena` の slot は
  `!awaitingCompletion && (epoch < 0 || epoch <= completedEpoch) && pendingGroups == 0` のときだけ reset され、
  `completed()` は backend の確認済み fence wait（`noteCompletionConfirmed`）から供給される。
  OPT-05 自体の性能受入は、after-leg が crash したため**未取得**。
- **OPT-07 の正しい契約（再挑戦用）**: destination が copy pending 中に破棄・置換・segment 再利用されないこと。
  (a) `VulkanBuffer.delete/ensureCapacity` と arena segment 再利用を batch の完了 epoch で gate する、または
  (b) flush 時に destination handle を解決する＋arena segment 再利用の gate。どちらか無しでは per-copy settling が正しい。
- **運用上の教訓（blend-state）**: jar は leg 実行中に再ビルドされるため、leg 間の比較は**各 lock JSON の `mods[].sha256`** を読む。
- **計器の罠（Lead 修正・`cbed9a5cd`）**: `offTargetDrawn` は宣言・印字のみで**一度も加算されない** counter だった
  （`offTargetOffered=738 / Accepted=738 / Drawn=0` が「何も描かれていない」と読めてしまう）。描画時に加算するよう修正。

## 0.225 **round 32-33: OPT-01/05・shader contract check・BH-06 SRG フック・BH-29 の half-texel 補正・BH-11/12 の counter 出力**

- **OPT-01（Lead）**: `threadCpuNanos` の MXBean を static 化し、**per-batch CPU split を opt-in**
  (`-Dceltium.vulkan.cpuSplit=true`) に。性能 run は「レンダラ」を測り、計装 run はそう名乗る。`threadWaitedMillis` 系は
  レポート行 (`:6838`) からのみ呼ばれることを確認（hot path ではない）。
- **shader push-constant overlap の CPU contract check（Lead）**: `scripts/cpu-contract/shader_push_constant_check.py`。
  glslang は重複メンバーを許すが **spirv-val が拒否**し、`vkCreateShaderModule` 失敗＝**全 leg が真っ黒**になる。
  現在 33 shader・0 問題。**負の対照**（問題になった offset 96 の重複を再挿入）で同じ文言で FAIL することを確認。
- **BH-06 残り（Lead）**: `setActiveTexture` の注入だけが deobf 名で、`require = 0` のため**黙って適用されていなかった**
  （実測 `CELTIUM_UNIT_SWITCH lines: 0`・`bindsOnUnit0=797 bindsOnOtherUnits=0`）。`scripts/mc-sources.sh --srg` で
  **`func_179138_g`** と確定し `require = 1` に。再測でも 0 のままだが、texture-mirror の計器が**ログ予算を switch と
  bind で共有**していた欠陥を特定（counter は real 0）。次の2手は彼らの instrument 修正と、`func_179144_i` の caller 追跡。
- **OPT-05（perf-ring）**: `VulkanFrameArena` が frame ごとの host-visible slice（16 byte 境界・stride はそのまま）を所有し、
  `VulkanDynamicMesh` が region を借りる形に。5つの即時パイプラインの draw は `mesh.bindAndDraw(commandBuffer)` に集約。
  **terrain arena の `alignUnit = lcm(4, stride)`（BH-01）とは別契約**であることをコードに明記。
- **BH-01（proj-channel）**: arena の capacity/split/exact-fit に alignUnit を適用し、`byteOffset` が stride の倍数になる
  不変条件を回復（24 byte vertex × 524288 で remainder 8 が後続 range をずらしていた）。
- **BH-29 / task-55（weather-water）**: light-map 項は既に vertex 側にあり、真因は **half-texel bias**
  （vanilla の unit-1 texture matrix は scale 1/256 **と** translate 8/256）→ `(u_LightCoord + 8.0) / 256.0` に修正。
  night/day leg を queued。
- **BH-11/12 の counter 出力（Lead）**: acceptance が「`immediateDepthBiasedGroups` は宣言と加算のみで**print site が0**」と
  指摘 → 検証サマリに追加。`lineBatchesNarrowed` の total も同様に追加。**BH-12 は受入済み**（`CELTIUM_LINE_NARROWED
  requested=2.0 drawn=1.0` ×2・outline が presented frame に存在）。**BH-11 は counter leg 待ち**（break overlay の scene は
  z-fight しないため絵では決まらない、と acceptance が実測で示した）。
- **canon rows（acceptance・jar `9bc475cb`）**: HUD **1.87**・entity band **18.85**・hand **21.25**、`drawnByFormat` id9 **228/228**、
  `windowFlushFailures=0`。**残差は shading**（candidate が一貫して暗い: hand `(114.3,96.6,83.9)` 対 `(131.2,102.8,91.1)`）
  ＝BH-29 の補正と BH-31 の領域。
- **BH-14/15/16 の機序確定（fog-path）**: `flushImmediateWindow` は**その時 bind されている target** で filter するため、
  一度だけ bind されて二度と bind されない target（panorama の `572879803`・loading screen の one-shot identity）の group は
  **永久に描かれない**。`offTargetPerTarget` は `677 offered / 32 atPass / 17 drawn`（identity 0 は 70/70/70）。
  ＝image は空であって解決ミスではない。target-driven flush（＋BH-15 の drop 計数・BH-16 の clear）を実装中。

## 0.224 **round 29-32: 状態契約の統合（BH-07/10）・BH-08/11/12/13 着地・OPT-01・font artifact の測定**

- **BH-07/BH-10 統合（blend-state・commit `2e49ce0f2`）**: `VulkanColorBlendState` が enable・RGB/alpha 4因子・2 equation・
  **write mask** を保持し、`celtium$captureState` は **stub の記録**から読む（`GlStateManager.blendFunc` は `GL11.glBlendFunc`
  直呼び、`tryBlendFuncSeparate` は `OpenGlHelper` 経由＝全 caller が落ちる唯一の場所）。state key に折り込まれるため
  alpha 因子や write mask だけが違う batch は別パイプライン・別 group になる。全即時パイプラインの blend attachment が mask を取る。
- **BH-11（z-fighting・Lead 実装）**: stub が `glPolygonOffset` を記録、5つの即時パイプラインが `VK_DYNAMIC_STATE_DEPTH_BIAS`＋
  `depthBiasEnable`（**rasterization state**）を宣言、backend が **全 group で** `vkCmdSetDepthBias` を発行（GL units→Vulkan
  constant factor・GL factor→Vulkan slope factor・符号はそのまま）。`immediateDepthBiasedGroups` が到達数を数える。
  **VUID-07834**（dynamic 宣言したのに呼ばない）は proj-channel の leg で検出し、全 group 発行に直して解消。
- **BH-12（選択枠・釣り竿の糸・Lead 実装）**: 線分 batch を**拒否せず描く**。2px 要求は 1.0 で描き `lineBatchesNarrowed` が数える。
  `formatId == 1` 制限を撤去。GL-less では拒否＝誰も描かないため。
- **BH-13（proj-channel）**: `celtium$setGroupViewport` の full-target 早期 return が dynamic viewport を残していた問題を修正。
  `drawnByFormat` id4 0→7430・id9 0→228・`groupViewportSets=4072`。
- **BH-08（proj-channel）**: `cullEnabledGroups=2128` / `cullDisabledGroups=3205`＝**39.9% の group が cull 有効**。
  修正前は enabled 列が run 全体で 0 になり得た（`glEnable(GL_CULL_FACE)` は記録済みだが読まれず、face/winding は no-op だった）。
- **BH-03 比較関数（weather-water）**: 選択子は **offset 100（`u_MvColumn2.y`）**。fragment が GL_NEVER..ALWAYS を実装し、
  `0` は「テスト無効」を意味する（GL enum に 0 は無い）。**fragment が offset 96 に第2メンバーを宣言すると
  spirv-val が重複として拒否**（`vkCreateShaderModule` 失敗＝全 leg が真っ黒）なので、既存列から読む形にした。
- **OPT-01（Lead）**: `threadCpuNanos` が毎回 `ManagementFactory.getThreadMXBean()` を解決していた（即時 batch あたり2回）。
  static に1回解決へ変更＝**計器自身を性能測定から除く**。
- **perf の判断（perf-ring・commit `c96a1fdd7`）**: BH-27 後半（staging ring）は**測定により却下**。最悪 frame は
  `worldTick=1` の 1350ms で内訳は world 生成（`regions=0 draws=0 uploaded=0B`）、upload の fence 待ちは run 全体で **48ms**
  ＝ring が消せる上限は ~1ms/frame（cold p50 70〜151ms）。加えて instrument の欠陥2つ（slow frame が先着順・phase 行の誤マッチ）を修正。
  次は `docs/performance_plan.md` の **OPT-05/07**（frame arena・copy の batch）を perf-ring が担当。
- **font artifact（glyph-fix・commit `71d155483`）**: UV セル境界説は**測定で棄却**（`mag=NEAREST min=NEAREST maxLod=0` では
  inset は no-op）。texel 一致検査は 449/449 stroke・0/769 空 texel 描画＝**glyph 幾何は正しい**。artifact は F3 背景矩形内の
  **1px 縦バー**（±6..12 輝度）で、最有力は font run のバッファ再利用（`-Dceltium.font.batching=false` の一旗実験が次）。
- **BLOCKER 2つを Lead が修復**: `glBlendFuncSeparate` の4引数 case が `a2/a3` 参照（dispatch は `a0/a1` のみ）→ `c[2]/c[3]`。
  および上記の push-constant 重複。どちらも「ビルドは通るが全 leg が描かない」種類。

## 0.222 **round 28: 聖典 item 2/3 を独立測定で受入・BH-09 完成（backend の scissor intersection）**

- **item 3（エンティティ/HUD）受入（acceptance の独立 leg・jar `65a3e7f4`）**:
  `drawnByFormat` **id9=228/228**・`windowFlushFailures=0`・`windowFlushDroppedBatches=0`・`modelDraws=2186`・
  `immediateGroupsDrawn=3820`。presented frame に **creeper・zombie・spider・enderman・stone item・red item・一人称の腕**。
  region: **mob band（chat 無し）mean|d| 19.57**・non-sky fraction **0.609 対 0.624**（同量の geometry が立っている）・
  **hand 24.06（修正前 62.14）**・HUD **2.13**。**一様だったのは GL screenshot**（GL-less では `glReadPixels` が読む context が無く
  黒になるのが当然）で、**gate が採点する presented dump は 17,408 色・nonBg 0.952515・716 bands**。
  gate は `UNCALIBRATED_PRESENTED_IMAGE`（entity variant に較正 key が無い＝**失敗した floor は無い**）。
  残差: candidate の chat overlay（35.36、chat 無しで 19.57）と build 状態差（60 対 155 frame）。
- **item 2（文字）受入（texture-mirror の F3 leg・jar `7acf4a9d`）**: `PASSED`・`validationClean=true`・`validationMessageCount=0`。
  `CELTIUM_SAMPLER_FILTER glTexture=8 … maxLod=0 (maxLod was 1, now from the record's GL_TEXTURE_MAX_LEVEL 0)`
  ＝font sheet は GL が持つ level だけを読む。`CELTIUM_FONT_GLYPH_SUMMARY windows=687406 … refused=0 submitMillis=386`
  ＝**1 glyph 0.56 µs**（要求帯 0.35〜0.65）・append 0.25 µs。crop は
  `docs/history/evidence/texture-mirror-20261011/font-f3-overlay-text-block.png`（sha256 `38ca183c…`）。
  主張は sampler line と crop で、全画面差分は 23.5% 床があるため使わない。
- **BH-09 完成**: group の Vulkan scissor を「target 矩形 ∩ ゲームの glScissor box」（`GL_SCISSOR_TEST` 有効時・
  GL の左下原点を Vulkan の左上へ反転・空 intersection は zero extent）に。**scissor は command state** なので
  group key には入れない（BH-10 の write mask とは意図的に違う）。`immediateScissoredGroups` で到達数を数える。
- **記録した計器の異常**: `drawnByFormat[4]=13163` 対 `acceptedByFormat[4]=47` は batch として読めない
  （id4 は 280 倍）。accepted-vs-drawn として意味があるのは id9 の対のみ。

## 0.221 **round 27-28: エンティティ修正を picture で受入・font sampler reach 着地（leg は F3 で取る）**

- **エンティティ受入（picture レベル・Lead 実測）**: `presentReadback=true` 付き leg で
  `presentedImageVerdict=UNCALIBRATED_PRESENTED_IMAGE`・`presentedDistinctColours=16690`・
  `presentedNonBackgroundFraction=0.952359`・`nonPlayerEntitiesAtCapture=9`・`worldTicksAtEnd=207`。
  保存 frame `celtium-presented-early-…-kept.png`（sha256 `1696f7fb…`）に**一人称の腕**・crosshair・hotbar・terrain・雲・particle。
  counter は `drawnByFormat` id4=17940・id9=228・`windowFlushFailures=0`・`windowFlushDroppedBatches=0`。
  ＝**「受理されたが描かれない」は解消**。残る受入は上流参照との entity band / hand の一致（acceptance が較正 pair を取得中）。
  なお以前の `FAILED_PRESENTED_IMAGE` は **`-Dceltium.vulkan.presentReadback=true` を渡していなかった測定側の欠落**
  （`presentedImageMeanAtCapture=-1.0`・`WatchdogReason="the requested picture did not arrive"`）で、絵の不在ではなかった。
- **font mirror の sampler reach（texture-mirror 着地）**: `glTexParameteri` の記録が全 pname を保持していることを利用し、
  `confirmSamplerStateFromRecord` が **`GL_TEXTURE_MAX_LEVEL` を読んで sampler reach を下げられる**ように。
  font sheet（`allocateTexture` → MAX_LEVEL 0）は `maxLod=0`＝mirror が生成していた level 1（GL に無いぼかし）を
  sample しない。block atlas は MAX_LEVEL 4 で terrain は不変。`CELTIUM_SAMPLER_FILTER` が before/after を1行で出す。
- **font leg の教訓（texture-mirror 実測）**: `keepGui=true` でも capture tick に chat が空だと
  `CELTIUM_FONT_GLYPH_SUMMARY` が出ない＝**計器の不在は描画の不在ではない**。text を実際に描く leg が要るので、
  **`-Dceltium.validation.showDebugInfo=true`（F3 overlay・数十万 glyph）**で取り直す（第二候補は menu scene）。
  その leg 自体は `PASSED`・`validationClean=true`・`validationMessageCount=0`＝**VUID 系が消えた最初の clean leg**。
- **BH-06（texture unit ごとの binding/enable）** も同 leg の tree に着地済み（reproducer leg は次）。

## 0.220 **round 27: FAILED ゲートの正体は「picture が届かなかった」（readback 未指定）・font overhaul の実現可能性（bitmap sheet で TTF 無し）**

- **`FAILED_PRESENTED_IMAGE` の内訳（実測・`r26-entity`）**: `presentedImageMeanAtCapture=-1.0`・
  `presentedVerdictWatchdogReason="the requested picture did not arrive"`・`presentedVerdictWatchdogFrames=117`・
  `presentedImageFramesAtCapture=95`・`presentedDumpRequestedAtCapture=true`・`presentedImageAssertionFailures=1`
  （`FAILED: no picture was requested for scene=terrain stage=world`）。
  ＝**frame が一様だったのではなく、presented 画像の readback が届いていない**。私の leg は
  `-Dceltium.vulkan.presentReadback=true` を渡していなかった（acceptance の成功 leg は渡している）。
  再測 leg `r27-entity` を同 flag 付きで起動済み。**失敗ゲートは証拠のまま**で、原因は「絵が無い」ではなく
  「絵を要求した経路が動いていない」——測定の誤りを立証したので、そこだけ切り分ける。
- **font overhaul の実現可能性（weather-water の原典確認）**: 1.12.2 の font は **bitmap sheet**
  （`Minecraft.java:523` が `textures/font/ascii.png` を読み、`FontRenderer.java:160-187` が画像の alpha から
  `charWidth[256]` を計算、unicode は `font/glyph_sizes.bin`）。**assets に TTF/OTF は無い**ので「4x で再ラスタライズ」は
  同じ bitmap の拡大にしかならず、「自前 atlas」は glyph rasteriser ＋ metrics 再導出を伴う**新しい subsystem**で、
  唯一「文字が悪くなる」方向に振れ得る変更。したがって実現可能な滑らかさは:
  (1) **font mirror の sampler reach**（filter は既に linear。task-26 の `SAMPLER_MAX_LOD = 1` が sheet の mip を
  level 1 で止めている）＝per-texture の sampler reach を font に与える（texture-mirror の file・着手中）、
  (2) subpixel 配置は **game 側の parity break**（vanilla は整数配置）なので較正 pair 付きの独立判断、
  (3) 生成 atlas は subsystem として metrics と受入を持たせる。
  **速度面は既に達成済み**（1 glyph 57.9 → 0.65 µs、F3-on p50 16,414.7 → 595.6 ms）で、残る F3 費用は glyph 経路ではない
  （128,000 glyph × 0.65 µs = 595 ms frame のうち 83 ms）。
- **goal 編集は自動継続 round では拒否される**（"requires a direct human turn on a top-level agent"）。
  objective は revision 14 のままで、font overhaul と item 3 の更新内容は `docs/goal.md` と本 plan に記録済み。
  次にユーザーの直接 turn があるときに goal を同期する。

## 0.219 **round 26: エンティティ不可視の根因を特定・修正（push block 208→216）・font overhaul を聖典に追加**

- **根因（acceptance の実測 + Lead の修正）**: `drawPendingImmediate` の push block が **208 byte** なのに、
  lit ITEM の light-map 座標を **offset 208/212** に書いていた（`push.putFloat(208, …)`）。light-map 座標を持つ
  group＝**model/item の batch** を描くたびに `IndexOutOfBoundsException` が window flush から飛び、catch が
  **即時キュー全体を clear** していた。
  実測（修正前・acceptance）: `windowFlushFailures=64`・`windowFlushDroppedBatches=9680`・
  `acceptedByFormat` id4=30/id9=228 に対し `drawnByFormat` id4=0/id9=0・最初の失敗 frame 43-46・
  `immediateGroupsDiscarded=0`・`pending=4`。
  **修正**: block を lit パイプラインの宣言どおり **216 byte**（`PUSH_CONSTANT_BYTES_LIT`）に。
- **修正後の実測（Lead・jar `5a181988`・terrain・`keepEntities=true`+`captureEntities=true`+`keepGui=true`）**:
  `windowFlushFailures=0`・`windowFlushDroppedBatches=0`・`immediateGroupsDrawn=5297`（前 1408）・`modelDraws=2981`、
  **`drawnByFormat` id4=17940（前 0）・id9=228（前 0）**、`nonPlayerEntitiesAtCapture=9`。
  ＝**model/item batch が実際に描かれるようになった**。
- **未解決（次の測定）**: 同 leg の `presentedImageVerdict=FAILED_PRESENTED_IMAGE` で、
  `screenshots/celtium-validation.png` が**一様（1色・2759 byte）**。group は 5297 描かれているのに presented が
  一様なので、**presented 画像の経路**（readback/dump か最終合成）を次の leg で切り分ける。失敗ゲートは証拠として扱う。
- **flush 失敗時の契約（proj-channel 着地・pending）**: catch と give-up（`attempt == 1`）の**両方**で clear せず retain し、
  `windowFlushRetainedBatches` を数える（group は「描かれるか、落ちたと数えられるか」）。give-up 側が実際の drop 源
  だったことは `ent3-verify`（`windowFlushRetainedBatches=0`・`windowFlushDroppedBatches=11051`）で確認。
- **font renderer overhaul（ユーザー提案・聖典 item 4）**: Smooth Font 的に、バニラ 128x128 glyph sheet の拡大ではなく
  **高解像度 glyph atlas（4x 再ラスタライズ）＋ linear/mipmap ＋ subpixel advance** を実装し、滑らかさ・安定性・速度を
  同時に改善する。受入は (a) F3/chat/メニューの crop が現行より滑らかで可読、(b) `refused=0` 維持、
  (c) glyph あたり submit 費用が悪化しない（現行 0.35〜0.65 µs/glyph）、(d) 同一 scene/pose/tick の crop と p50/p95、
  (e) 未較正 scene のノイズ床 23.5% を踏まえて主張は較正 leg か crop 目視で行う。
  設計前に原典（`scripts/mc-sources.sh --grep FontRenderer`）と現行 font 経路
  （`FontRenderer.renderStringAtPos` → 1 glyph 1 batch → font run batching、format id2、`CeltiumFontGlyphCapture`）を読む。
- **BH-09（scissor）第1段**: stub の NOOP 表から `glScissor` を外し、矩形を記録（`scissorBox()`）、
  `scissorTest()` は `glEnable`/`glDisable` と同じ `enables` を読む（二重mirrorにしない）。backend の
  intersection は次段。

## 0.218 **round 24-25: 37件のbug huntを全担当へ配布・lit ITEM パイプライン復活（entity経路の阻塞解除）・BH-02/04/05/18/23/27/30/34 着地**

- **lit ITEM パイプラインの復活（最重要・entity/手持ち/TESR 経路）**: 全 validation leg を落としていた
  `VUID-RuntimeSpirv-OpEntryPoint-08743`（fragment input location 2 に対応する vertex output が無い）の原因は
  **fog ブロック 128..207 と lighting 128..207 + `u_LightCoord` 208 の同一バイト衝突**。
  proj-channel の算術（fog は 80B・lit は 216B・このデバイスの `maxPush=256`）で「fog を 224+ へ移す」案は**棄却**。
  weather-water が **`immediate_tex_color_lit.frag`**（共有 fragment から location 2 と fog 演算を除いたもの）を新設し、
  lit variant をそこへ向けた＝**パイプライン生成が復活**。unlit 側の range は 128→208。
  **lit 家系は fog 無しのまま**（UBO 再構成＝BH-17 が前提）。検証: `ww43-bh30-after`・`bh2-verify`（両方 clean・VUID 0）。
- **BH-30（world fog）受入**: 同一 jar・`-Dceltium.vulkan.immediateFog=false` の one-flag 対照で
  空バンド **70.1% >8**・遠景 28.3%・近景 **2.9%** ＝**距離依存**（finding の要求どおり）。
- **BH-02（透明地形 index offset 0）**: `pass.isSorted()` で index 源を選ぶよう修正（commit `4955563e6`）。
  前後 leg とも clean で **107,046 px = 11.62% が変化**（小さい差が全体に広がる＝透明 index 修正の期待形）。
  ただし「vanilla に近づいた」は未主張（未較正 variant）＝上流 leg が次の受入。
- **BH-27（queue 全体待ち）**: `bootstrapQueueWaitMillis` **316→0**（worst 13→0）、per-submission fence 待ち 2994・worst 6ms・
  `submitTimeouts=0`・`gpuStalled=false`。絵は不変（nonBg 0.952222→0.95232・bands 707→707）だが**画素一致は未確立**（別 build 状態の leg）。
- **BH-18（swapchain 引数）**・**BH-23（sampler 再照会）**・**BH-34（`glTexEnvf` の bit 列）**・**BH-04 colour 半分**・
  **BH-05（lighting を group 生成時に捕捉）** 着地。BH-37 は既存ルールで被覆。BH-22 は診断のみ（GPU完了順序が前提）。
- **BH-09 の正しいフック位置（gui-stage の原典確認）**: `GlStateManager` に scissor メソッドが無く、
  呼び元は Celeritas 自身の `VintageDrawContext` の直接 LWJGL 呼び出し。**`CeltiumGlStub` の NOOP 表（`:386`）が
  矩形を捨てている**箇所が真の修正点（Lead が編集許可を出した）。scissor は **command state** なので pipeline key ではなく
  group の矩形に入れる（BH-10 とは意図的に違う）。
- **未着手の残りと担当**: BH-01/03/07/08/13/25/36＋BH-04 の残り半分（proj-channel）、
  BH-21/24/26/28/12/14/15/16（fog-path）、BH-06/22（texture-mirror）、BH-09/10/11（gui-stage）、
  BH-17/19/20＋BH-18 の成功路 print（sky-draws）、BH-29/31/32/33/35（weather-water）。
- **ユーザー可視の残件**: エンティティ/手持ち（lit パイプライン復活で前進・受入測定中）、文字（font counter は refused=0・
  可読 crop あり）、HUD（受入 leg で参照一致 1.81）。

## 0.217 **round 21: entity は「受理されたが描かれない」（id9/id4）・HUD は出ている（fixture の hideGUI が罠）・splash group は off-screen**

- **acceptance の決定的測定（`r18h-cand`/`r18h-ref`・jar `6a89906b`・`keepGui=true captureEntities=true`）**:
  - **HUD は出ている**: hotbar strip (367,654)-(913,720) が参照比 **mean|d| 1.81・差 0.2%**、id2 は 1698/1702 描画。
    ＝**私が指定した flag に fixture の罠があった**（`hideGUI = !keepGui`・`captureEntities` 無しだと実体を消す）。
  - **entity と一人称 item は「受理されたが1つも描かれない」**: `acceptedByFormat` は **id4=45・id9=228**、
    `drawnByFormat` は **id4=0・id9=0**（合計 3000 受理・2712 描画）。`modelDraws=2469`・`modelDrawRefusals=0`・
    `postAcceptDroppedGroups=0`・`discarded=0`・`immediateGroupsPending=3` ＝**group が捨てられているのではなく、
    accept と record の間で id4/id9 が落ちている**。
  - **format id の訂正（原典）**: `TexturedQuad:66` は `DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL` を使い、
    これは **id9** に対応する（id8 ではない）。**mob モデルは全て id9 で届く**。id8=0 は entity と無関係。
  - 画像: entity band (450,150)-(850,400) が参照と **62%** 差、一人称 hand (960,500)-(1280,720) が **92%** 差。
- **splash group の NDC（Lead・`r21-bounds`）**: `CELTIUM_GROUP_BOUNDS frame=0 group=1 fmt=2 target=743315227 texture=8
  batches=2 vertices=8 indices=12 proj=[m00=0.005 m11=-0.008 m22=0.0 m32=-0.5…]`。
  ortho は `l=0 r=427 b=240`（splash 自身の 256x256 スケール）と `l=0 r=1281 b=720`（frame）。
  **splash の group は受理されている**（group=2 の composite も）。白の残件は「group が pass に到達しない」側。
- **次の一手**: id4/id9 が accept 後に record されない経路（`submitImmediateBatchTimed` → group append → `drawPendingImmediate`）
  を、format 別の accept/record カウンタで追う。entity-models/pipelines にこの数値を渡した。

## 0.216 **round 19: 起動の白は「白でクリアされた frame に何も描かれていない」・world の id1 拒否は mode 3（LINE_STRIP）**

- **ロード画面（Lead 実測・jar `55542e67`）**: 起動 leg で `CELTIUM_EXTERNAL_IMAGE published glTexture=6 … size=1280x720` が
  **1回だけ**で `dropped` なし（＝viewport 由来の 1281 は解消）、`REPLACE_DROP=0`。それでも白は残る（captures 24-31）。
  分かったこと: **白は「白でクリアされた frame に何も描かれていない」**——game 自身の clear が白
  (`CELTIUM_CLEAR_LATCH … colour=(1.0,1.0,1.0,0.0)`)、同じ leg の `CELTIUM_TEX_COPY_SERVED … mean=255,255,255` が
  frame image から白を読んでいる。splash の group は届いている（`group=1 fmt=2 texture=8 target=<splash FBO>` →
  `group=2 … texture=6 target=0`、`CELTIUM_WINDOW_ORDER frame=0 … groups=1`）。
  ＝次の問いは「composite が frame image に届いているか」「logo quad が画面内に落ちているか」。
- **world の id1 拒否（Lead 実測・`r16-diag`）**: `immediate formats offered/accepted: id1=39/2`、
  `refusedByReason=[draw mode 3 is not handled (only 7 = GL_QUADS)=37; the mirrored projection is the identity at
  draw time=4]`。**mode 3 = GL_LINE_STRIP は即時パスが扱わない**（1 batch も描かれない）。
  `CELTIUM_GROUPS … targetGeneration=43 staleTargetGeneration=1 discarded=1 submitted=970 completed=971`＝
  **frame target が43回作り直されている**（published name が消える原因の1つ）。
- **entity**: `id4=43/43`・`id8` は histogram に出ない（world では model 経路）・`id5=78/78`・`id6=435/434`・`id7=366/366`・
  `id9=228/228` ＝ **format 1 以外は受理されている**。

## 0.215 **round 17-18: 起動 phase の白は残る（target サイズは機序でない）・panorama copy は「白」を served・entity の list 捕捉の穴**

- **ロード画面**: frame target を**窓の drawable サイズ**で決めるよう修正（viewport は最後に bind された framebuffer のものなので、
  起動中は splash の FBO の extent を拾っていた）。**白は変わらなかった**＝target サイズは機序ではない。
  現状: 起動 capture 71枚で 黒(0-23) → **白(24-31・約4秒)** → menu(32+・文字可読)。
  splash の batch は `CELTIUM_IMMEDIATE_ENQ frame=0 group=1 fmt=2 texture=8 target=<splash FBO>` として届き、
  直後の `group=2 … texture=6 target=0`（composite）が publish 名を sample する。
  `published glTexture=6` → **直後に dropped**（`publishedFrameTexturesRepublished=0`＝私の再指しは発火していない＝第三の drop 経路がある）。
- **panorama（menu 背景）**: `CELTIUM_TEX_COPY_SERVED dest=20 … 256x256 readFbo=0 mean=255,255,255 opaque=65536/65536 served=1`
  ＝**copy は served されているが、その source が一様な白**。menu の背景が灰色グラデーションなのはこのため
  （cube face の内容ではなく白が skybox texture に入っている）。task-36 の残件は「served 元の内容」。
- **entity**: `CELTIUM_MODEL_LIST_OPEN/CLOSE` で 24 list 捕捉（`list=147 owner=RenderPlayer` ほか・refused=0）、
  `CELTIUM_MODEL_MESH` 64 build。ただし `CELTIUM_MODEL_MISSING list=-1/160/161`（`VulkanModelQuadCapture.quad` は
  `TexturedQuad` からしか呼ばれないので、別経路で描かれる list は登録されない）。
  `CELTIUM_MODEL_BOUNDS list 147 vertices=24 … quadsWithOtherMatrix=0/42`＝捕捉 quad 42 に対し mesh は 24 頂点。
  `CELTIUM_MODEL_ENQ … projKind=ortho` は GUI item の batch（world entity ではない）。

## 0.214 **round 16 の独立検証（acceptance・jar `e5508fd5`）とロード画面の最終原因**

- **font batching（ON で）検証済み**: menu のボタンが `Singleplayer`/`Multiplayer`/`Mods` と可読（自前 compositor capture）、
  `accepted=704273 refused=0`（修正前は `accepted=0 refused=100%`）、**0.45 µs/glyph**（私の実測 0.35、off は 59）。
- **device lost 連鎖**: `r16-world` **PASSED**・`validationMessageCount=0`・0 assertion・`worldTicksAtEnd=207`・
  `presentedDistinctColours=2058`・nonBg **0.759463**・628 bands・black 0.0。
  `staleGroupDescriptorsRebuilt=17`・`staleGroupRefusals=0`・`publishedFrameTexturesStalePruned=18`・`Republished=0`
  ＝**stale な group の再構築が実際に行使されている**（prune 経路は安全、re-point 経路は未行使）。
- **F3 pair（現行 jar）**: F3 off p50 **83.2 ms**（38 sample・29,214 glyph windows）／F3 on p50 **304.8 ms**（13 sample・704,273 windows）
  ＝**3.7倍**・同 tick で frame 数 2.7分の1・glyph 量 24倍。**glyph の submit 費用は消えた**ので、残る F3 費用は別の場所。
- **ログ量**: 通常起動で census 0行・全425行（CELTIUM 185）＝以前 2152/1479。validation run では従来どおり出る。
- **ロード画面の最終原因（fog-path 実測）**: 一様な白は**消えた**（黒 → 14色 → 4,411 → 8,757 → 15,000+ と world が構築される）。
  ただし capture 21 に出るのは**地形構築中の world frame**（空・雲・crosshair・hotbar）で、
  **vanilla の loading screen 自身（dirt 背景・"Loading terrain…"・progress bar）は依然として出ていない**。
  原因は `SplashProgressMixin.celtium$skipSplashUnderGlless`（`forge122/.../mixin/core/SplashProgressMixin.java:18`）が
  GL-less で **Forge の SplashProgress を cancel している**こと＝**描かれていない**。publish/drop の寿命ではない。
  次の一手: cancel をやめる（GL-less 安全な splash を即時パスで描く）か、map→最初の present の間を埋める。
- **ユーザーの world 不具合（実測）**: ログに `CELTIUM_MODEL_MISSING list=… texture=… (no captured mesh for this display list;
  the GL body runs and there is no context)` ＝**entity の display list から mesh が捕捉されていない**（entity 不可視の直接原因）。
  GUI 欠落は未帰属（次 round で format ヒストグラムと pass 構成を取る）。

## 0.213 **round 16: 一回の timeout で latch しない（resize 回帰）・font batching の機序と 0.35 µs/glyph・ロード画面の残件**

- **resize 回帰の根因（frame-composite 実測・Lead 修正）**: `resizeworld-r` は resize 自体は成功（`resizeObservedAtTick=123`・
  framebuffer 1920x1080）だが、その後 **present が1枚も出ない**（`presentedImageFramesAtCapture=0`）・
  `stalledStops=1`・`stalled; no further drawing until the renderer is rebuilt` 20件。私の `framebufferReplacing` ゲートは
  無関係（`postAcceptDroppedGroups=0`・frame 0 の `CELTIUM_REPLACE_DROP` なし）。**1回の fence wait timeout が
  `gpuStalled` を latch してセッション全体を止めていた**。
  修正: timeout を **1回の wait につき3回まで retry** してから latch（`idleWaitTimeouts`/`idleWaitRetries` を report に追加）。
  `resizemenu-r` は clean（`presentedImagePixels=2073600`・non-bg 0.749・black 0.0）。
- **font batching の文字化け機序（Lead 特定・修正・実測）**: `submitImmediateBatch` は **submission 時に** texture と行列を
  捕捉する。run が intervening submission で flush されると（menu のボタン周り）**割り込み draw の texture/変換で描かれていた**。
  flush 時に run の texture・alpha test・行列を適用し復元するよう修正。**menu の文字は batching ON で全て可読**、
  `submitMillis=304`/879,402 quads＝**0.35 µs/glyph**（off は 59 µs）。**既定 ON**。
- **ロード画面の残件（実測）**: 起動 phase は依然 **一様な白**（frames 64-73・1色）。`CELTIUM_EXTERNAL_IMAGE` は
  `glTexture=6`/`22` を publish した直後に **drop**（`publishedFrameTexturesRepublished=0`＝私の再指しは発火していない）。
  drop は「pair image が live でない（release 済み）」分岐から出ている＝**frame target/pair が再作成されるたびに
  published name が消える**。次は「pair が release される原因（target 再作成の頻度・1281↔1280 のサイズ揺れ）」を計測する。
- **GUI**: panel は描かれている（比 0.983-0.990）。残差は **104 vs 139 色**で、**候補の色は全て参照にも存在**（候補固有 0・
  参照固有 35）＝**中間色の不足＝filtering**。次の測定は sampler の filter census（mirror の中身ではない）。chat 背景は
  fixture に chat が無く未行使（hook が必要）。hotbar frame は画素一致。

## 0.212 **構造: `VulkanTerrainGpuBackend.java` 11,469行の分解計画（挙動を変えない抽出・1クラスタ=1 commit）**

- **実測**: module は 47 file / 35,644 行。`VulkanTerrainGpuBackend.java` は **11,469 行**（全体の32%）・
  メンバ約 **908**・`celtium$` 接頭辞の行 235・診断/集計関連の語を含む行 200。
  内訳の目安: immediate 794行語 / frame・present・swapchain 425 / model 408 / matrix・viewport 339 /
  terrain・region 283 / stall・wait 274 / published 134 / font 122。
- **なぜこうなったか**: `TerrainGpuBackend` の広い interface を1クラスで実装し、round ごとの修正が
  「状態が既にある場所」に着地したため。代償は実測済み: 同時編集でコンフリクトし、1つの修正が無関係な状態に触れる。
- **分解の方針（挙動を変えない・小さく・検証は既存 leg）**:
  1. `VulkanFontRunBatcher` — font run の状態機械（pendingFont* 12 field・append/flush/boundary/summary・約120行）
  2. `PublishedFrameTextures` — published name の寿命（map・publish/prune/repoint/transition）
  3. `VulkanBackendDiagnostics` — `verificationSummary()` と census/report（読み取り専用・約800行）
  4. `VulkanImmediateQueue` — pending group の queue と draw loop（最大の塊）
- **規則**: 抽出 commit は**挙動を変えない**（public 面＝mixin から呼ばれる `celtium$*` は薄い委譲として残す）。
  検証は同じ leg（menu・world・validation on）で行う。**同じクラスタを他の担当が編集中は抽出を始めない**。
- **既知の失敗の記録**: この巨大 backend を複数担当が同時編集した結果、round 12-14 で
  「どの変更が device lost を入れたか」の切り分けに4 leg を要した（proj-channel の `t55-*` 4 leg）。
  抽出はその再発を減らすための構造整理であり、機能追加ではない。

## 0.211 **round 14: 放棄された frame が wait semaphore を残す（device lost の根因）・zero-view ガードの穴・publish の再指し**

- **device lost の根因（fog-path が `00067` から特定・Lead が修正）**: `GllessFrameTarget.beginFrame` が `waitSemaphore` を
  signal-only submit で立て、frame の最初の submit がそれを wait し、`endFrame` が `doneSemaphore` を consume する。
  ところが **frame を `abandonFrame` すると wait signal が未消費のまま残り**、次の `beginFrame` が同じ binary semaphore を
  二度 signal → `VUID-vkQueueSubmit-pSignalSemaphores-00067` → RADV が `VK_ERROR_DEVICE_LOST` → 以後の submit が全部失敗し
  `settleGpu` が latch（`windowFlushFailures=1 @frame 0`・`stallOrigin=…presentAtSwapPoint via settleGpu:10953`）。
  修正: target が「signal 未消費」を保持し、backend が frame semaphore 付き submit（と `submitSignalOnly`）で
  `noteWaitConsumed()` を呼び、`abandonFrame`（と open な frame を見つけた `beginFrame`）が
  `consumeOutstandingWait()` で消費する。`abandonedWaitsConsumed`/`abandonedWaitFailures`・`CELTIUM_ABANDON_WAIT_CONSUMED` で観測。
  **abandon 経路から例外を投げない**（dropped frame を crash に置き換えない）。
- **zero-view ガードの穴（Lead 修正）**: `08114` は `u_Texture` の descriptor に null view が書かれる。texture/item パイプラインには
  ガードを入れたが、**block パイプラインと UI パイプラインに無かった**（particle には元からある）。
  `VulkanImmediateBlockPipeline.descriptorSetFor` と `VulkanUiPipeline.bindTexture` に同じ拒否を追加
  （`zeroViewRefusals`）。**拒否は「descriptor を書く経路そのもの」に無いと意味がない**（fog-path の指摘）。
- **published name の再指しを再生成点にも適用（Lead 修正）**: `celtium$syncPublishedChainOutput` は pair が作り直された時、
  古い image を指す name を **drop していた**（1 leg で glTexture=22 が5回）。drop された name は registry で
  announcement → **空 mirror** に落ち、loading phase が一様な白になる。live な pair image があれば **再 publish** して
  name を新しい image に追随させる（`publishedFrameTexturesRepublished`）。
- **semaphore 修正後の leg（`r14-world`・validation on・300s・AMD）**: 121 frame 時点で
  `SIGSEGV 0・DEVICE_LOST 0・00067 0・08114 0・stall 0`、frame は 黒→白→**menu（3798色）**で安定。
  world 到達は未確認（fixture の world 開始が遅い）。
- **受入ブロッカーの解消を実測（`r15-world`・jar `d69dfb82`・validation on・`--validate`・200s）**:
  `worldTicksAtEnd=243`・`captureListedSections=41`・**`validationMessageCount=0`・`validationClean=true`**・
  `08114 0 / 00067 0 / DEVICE_LOST 0 / SIGSEGV 0 / CELTIUM_STALL 0`。
  format ヒストグラムは `id1=3/3 id2=801/801 id5=6/6 id6=42/41 id7=8/8 id8=128/128 id9=12/12`
  ＝**entity/TESR(id8)・particle(id5)・block(id3 系)が world で実際に提出されている**。
  `fixtureStatus=FAILED_PRESENTED_IMAGE` は「picture を要求していない」ためで、device lost ではない。
  **harness の `--validate` が無いと fixture は world を開始しない**（私の leg が menu のままだった理由）。
- **次**: `dumpPresentedImage` 付きの world leg で「entity が見えるか」を画で確認（ユーザーの「エンティティが不可視」）と、
  ロード画面の content 確認（publish 再指しの効果）。
- **font batching の文字化け根因（Lead が修正・実測）**: `submitImmediateBatch` は **submission 時に**
  bound texture と行列を捕捉する。run が「intervening submission」で flush されると（menu のボタン周りで起きる）、
  **その割り込み draw の texture と変換で描かれていた**（境界 flush の footer だけが正しい＝症状と一致）。
  `celtium$flushPendingFontBatch` が run の texture・alpha test・行列を適用して submit し、終了後に復元するよう修正。
  **実測（jar `64923c0a`・compositor capture）**: batching ON でボタンラベル・splash・copyright・footer すべて可読。
  **既定を ON に戻した**。
  さらに world leg（jar `de835882`・validation on・F3 on・`--validate`）:
  **`validationMessageCount=0`・`validationClean=true`・`worldTicksAtEnd=248`**、
  `CELTIUM_FONT_GLYPH_SUMMARY … quads=879402 submitted=879402 accepted=879402 refused=0 submitMillis=304
  fontRunsStarted=1095 fontQuadsAppended=878742 flushedIntervening=931 flushedBoundary=6 flushedStateChange=158`
  ＝**1 glyph 0.35 µs**（batching off は 59 µs、ユーザー実測の on は 3.7 µs）。**F3 のコストは submit 側ではほぼ消えた**。
  ユーザーの instance には `de83588232c21900634b45c62ad880d0` を投入済み。
- **font batching（weather-water）**: 文字化けの機序は **行列が submit 時に捕捉される**こと（run が行列変更を跨ぐと
  最後の変換で描かれる）。10 の行列入口で run を flush（`celtium$fontMatrixBoundary`）、colour witness も量子化バイトに修正。
  既定は on に戻したが、**device-lost 中の3 leg では batching が一度も発動していない**（`fontRunsStarted=0`）ため
  文字の受入は未取得。clean な tree で再測する。

## 0.210 **round 13: panel は描かれていた（床が別 scene の数字）・published name の drop が白画面と abort の共通根因・zero-view ガード**

- **video settings の panel は描かれている（gui-stage・`1321cf111`・task-42 の既存 leg を再利用）**:
  panel(180,80,1100,624) の比 **0.983/0.987/0.990**・>8 が **4.56%**、Done button **1.000/0.0003%**、hotbar frame **0.00%**（画素一致）。
  **決定的な数字: 参照自身の panel 領域は 139 色**（候補 104）。`guiPanelDistinctColours>=15,805` は **inventory scene の数字**
  （item sprite を含む）で、video-settings の実 GL 参照でも満たせない＝**`verticalBands>=250` と同じ「別の絵で測った床」の2例目**。
  task-47 の provenance 修正の対象。**床は下げていない**。残るのは texture detail（104 vs 139）で、欠落ではない。
- **loading screen の白画面と menu abort は同じ根因（texture-mirror・既存 leg の読み出し）**:
  `splash-vuid2` で `CELTIUM_EXTERNAL_IMAGE published glTexture=6 image=0x300000000030 … size=1281x720` →
  同 name が **drop**、`glTexture=22` も publish 後に **5回 drop**。registry は `externalImages` → announcement → mirror の順に解決するので、
  **frame の途中で unpublish された name は空 mirror に落ちる＝一様な白**（loading 17 capture が1色）。
  そして `pano-menu` の abort が名指しした `Invalid VkImage Object 0x300000000030` は **glTexture=6 が publish したのと同じ handle**。
  ＝**「drop した後に transition している」**。寿命（drop は attach する frame の後・transition は published の間だけ）が修正点。
  併せて `glTexture=6` が **1281x720**（窓は 1280x720）で publish されている——1px 広い owned image は別途の候補。
- **zero-view ガードの leg（`r13-world`・validation on・150s）**: SIGSEGV 0・DEVICE_LOST 0・VUID 0・stall 0・299 frame。
  ただし 150 秒では world に入りきらず menu まで（`exitCode=143`・`validationExitCode=124`）＝**device loss は再現せず**。
  次は duration 300 で world 到達まで見る。
- **ユーザーの現況**: 実プレイで world が描画され（terrain・sky・ブロック）、F3 overlay の文字も正常。**苦情は「f3がラグい」**＝
  1 glyph あたり 59 µs（`meanSubmitMicros=59`）・F3 は最も文字が密。font batching は既定 off（文字が壊れるため）。

## 0.209 **round 12-13: published name の再指し・font batching の既定off・world の device lost 系列**

- **published frame texture の寿命（Lead 修正）**: `glTexture=6/22`（game framebuffer の colour texture 名）は chain pair image を
  publish したもの。pair が作り直されると wrapper が古い image を指したままになり、
  (a) その image への barrier が `VUID-VkImageMemoryBarrier-image-parameter`（invalid object）→ RADV の SIGSEGV、
  (b) name を unpublish すると sample 側の descriptor が `VkImageView 0x0` → `VUID-vkCmdDrawIndexed-None-08114` →
  semaphore 二重 signal → **VK_ERROR_DEVICE_LOST**、という2つの失敗を出していた。
  修正: `celtium$pruneStalePublishedNames` は stale な name を **live な pair image へ再 publish** し
  （`publishedFrameTexturesRepublished`）、live が無い時だけ drop（`publishedFrameTexturesStalePruned`）。
  sampling 側の遷移は pass が attach する image を飛ばす（`publishedFrameTexturesSkippedForAttach`）。
- **zero view の拒否（Lead 修正・実測待ち）**: `descriptorSetFor` が `view==0` の wrapper を渡されたら
  descriptor を書かずに batch を拒否する（`CELTIUM_ZERO_VIEW_REFUSAL`・`ZERO_VIEW_REFUSALS`）。
  **view 0 を descriptor に書くと 1 batch の欠落ではなく device が落ちる**（acceptance 実測の系列）。
  view 0 の wrapper がどこから来るかは計器が名指しする（次の leg）。
- **font batching は既定 off（Lead 裁定）**: ON だと menu のボタンラベル・回転した splash・copyright がブロック状になり、
  同じ jar で `-Dceltium.font.batching=false` にすると全部読める（1フラグ差・compositor capture・私が目視）。
  matrix を witness に足しても直らなかったので機構は未解明。**未検証の最適化が文字を壊す状態を既定にしない**。
  コードと flag は残す（`-Dceltium.font.batching=true` で有効）。
- **stall latch は bounded（fog-path）**: 2つの flush catch が例外文と破棄 batch を記録して次フレームに再試行する。
  latch 自体は wait 経路（`settleGpu` の `vkDeviceWaitIdle` 失敗）に残る。引き金は
  `vkQueueSubmit (glless beginFrame signal) failed with VkResult -4` = **VK_ERROR_DEVICE_LOST**（frame 0）。
- **world leg の現状（`r12-world`・compositor・AMD）**: `CELTIUM_STALL site=…presentAtSwapPoint:3541 latchSite=settleGpu:10953
  frameId=0`・`glDrawArrays drawn=0 refused=749`・frame は黒/白のみ・`presentedImageVerdict=FAILED_PRESENTED_IMAGE`。
  ＝**world 経路は frame 0 で device を失う**。menu（fixture なし・181 frame）は生存するので、失敗は world 側。
- **fixture**: `-Dceltium.validation.worldTime`（既定6000）・`renderClouds`（既定 -1）・`showDebugInfo`（既定 未設定）を追加。
  **凍結 fixture が古いと flag は黙って無視される**（fog-path の実測: night のはずが `pinnedWorldTime=6000`）＝
  leg を読む前に report の `pinnedWorldTime`/`renderCloudsRequested` を確認する。
- **compositor capture の運用**: ゲームスロットの待ち行列が長いので `--startup-timeout` を 900 以上にする
  （240 で timeout した leg がある）。

## 0.208 **Lead が2つの根因を修正: 00900 VUID（published name と attach の不一致）とロード画面（frame 0 の drop）**

### (1) `VUID-vkCmdBeginRenderPass-initialLayout-00900`（20件/run・受入ブロッカー）

- 根因: `recordImmediateOnlyPass` の pass begin（`vkCmdBeginRenderPass`）は attachment 0 の initialLayout が
  `GENERAL`（`COLOR_ATTACHMENT_LAYOUT = VulkanFrameTarget.COLOR_RENDER_LAYOUT`）。その直前に呼ばれる
  `celtium$transitionPublishedFrameTextures(false)` が **published name 全部**を `SHADER_READ_ONLY_OPTIMAL` へ動かしていた。
  ところが `celtium$syncPublishedChainOutput` は「attach する chain pair image を指す名前」だけを残すので、
  **その transition はこれから attach する image 自身を動かしていた** ＝ 直前の layout が SHADER_READ_ONLY のまま
  GENERAL を宣言する pass が始まる。
- 修正: pass が attach する colour image を `passAttachedColorImage` として framebuffer 選択時に記録し、
  sampling 側の transition は **その image を指す published name を飛ばす**（attach 側の hand-back が GENERAL のまま保つ）。
  `publishedFrameTexturesSkippedForAttach` で数え、最初の1件は理由つきで記録する（黙って遷移をやめる規則は観測できないため）。
- 受入: 現行 jar の terrain leg で `validationMessageCount=0`（leg `r12-vuid-fix` を queue）。

**実測（leg `r12-splash`・compositor 連番 capture・AMD RX 6400）**: この leg の jar は `passAttachedColorImage` を含む（`javap` で確認）。
`validationMessageCount=10` だが **`00900` クラスは 0 件**（残りは全て proj-channel の作業中 light-map:
`vkCmdDrawIndexed(): … variable "u_Lightmap" … has never been updated via vkUpdateDescriptorSets()`）＝
**00900 は修正が効いた**。picture は PASSED・`worldTicksAtEnd=213`。
同じ leg の compositor frame（0.5 s 間隔・160枚）: **起動から約30秒は完全な黒（mean 0,0,0・1色）→約10秒 真っ白（255,255,255・1色）→
9色の暗い茶 → 世界（9628色）**。＝**起動時のロード画面は依然として何も出ていない**（ユーザーの報告どおり）。
この leg の jar は 2引数 `celtium$noteBindSize`（＝ロード画面修正前）なので、これは修正前の「before」である。
修正後の再測（leg `r12-splash2`・現行 jar＝`celtium$noteBindSize(int,int,boolean)` と `pruneStalePublishedNames` を含む）を queue した。

### (2) ロード画面が出ない＝frame 0 で splash の composite batch が drop されていた

- ユーザーのセッション（20:37:47・起動直後）の実測:
  `CELTIUM_IMMEDIATE_ENQ frame=0 group=1 … fmt=2 texture=8 … target=15042289`（splash の logo group・Mojang logo texture 8）→
  `CELTIUM_EXTERNAL_IMAGE published glTexture=6 image=0x7f438c8641d0 size=854x480`（splash FBO の colour texture 名を pair image に publish）→
  `CELTIUM_IMMEDIATE_ENQ frame=0 group=2 … fmt=2 texture=6 batches=1 target=0`（**publish 名を sample して frame に書く composite**）→
  `CELTIUM_REPLACE_DROP batches=1 target=854x480 frameId=0` ＝ **その composite が捨てられた**。
  session 全体でこの drop は **1回だけ**（frame 0）。
- 根因: `FramebufferMixin.celtium$noteRenderTarget` が **全 framebuffer の bind** で
  `celtium$noteBindSize(w,h)` を呼び、frame target とサイズが違えば `framebufferReplacing=true` を立てていた。
  起動時は splash 自身の FBO（`CELTIUM_ADOPT_PROBE afterBind size=468x1018`）が window の 854x480 と違うので
  フラグが立ち、次の ordering flush が **pending group を全部 drop** する（`flushPendingBeforeGlDraw` の
  「replacement 中の flush は device を待たせる」規則）。この規則の目的は**本物の resize**（frame target の置換）であって、
  ゲームが意図的に持つ別サイズの FBO ではない。
- 修正: フラグは **main framebuffer（`mc.getFramebuffer() == self`）のときだけ**立てる
  （`celtium$noteBindSize(w,h,mainFramebuffer)`）。mixin が `mc.getFramebuffer() == self` を渡す。
  **resize 経路は main の bind なので不変**（task-28 の resize leg で再確認する）。
- 受入: compositor 連番 capture（`scripts/compositor_capture.py`・0.5s 間隔）で起動〜world load の frame に
  loading/splash の内容が出ること＋`CELTIUM_REPLACE_DROP` が 0 件であること。

## 0.207 **round 11 の進行: クラッシュは再現せず（jar 健全性3計測）・lightmap 座標は stub で捨てられていた・VSync 行は在った**

- **task-58 完了（sky-draws・`guiscale-leg`）＝クラッシュは再現せず、ただし「修正済み」ではない**:
  AMD RX 6400・GL-less・`scene=videoSettings`・layer off・854x480（`resizeObservedTo=854x480`）で
  **GUI Scale を9回**画面自身の `GuiVideoSettings.mouseClicked+mouseReleased` で押した（Auto→1→2→…）。
  `exit 0`・**crash report 0件**・`validationMessageCount=0`・`validationClean=true`・画あり（gui stage・tick 314）。
  `+guiScale:1 -guiScale:0` が `options.txt` に反映、Done 後に開き直して `GUI Scale: 1`。
  **jar 健全性は3計測**: `unzip -t` clean／1073 entry・824 class・**長さ0の class entry 0**（190 の0バイトは全てディレクトリ）／
  全 class の constant-pool walk で **824/824 有効**。`ReadableRange.class` は 574 B。
  **GUI scale の契約（原典）**: `GuiVideoSettings.mouseClicked/mouseReleased` → `ScaledResolution` →
  `setWorldAndResolution`（画面の再レイアウト）。`Minecraft.resize`/`updateFramebufferSize` は**窓**の経路で、
  scale 変更は framebuffer も窓も変えない。native 側が反応すべきは (a) 毎フレームの GUI ortho（mirror・group 単位）、
  (b) viewport/scissor（窓ピクセル・不変）、(c) 再レイアウトされた geometry。**scale 専用の hook は欠けていない**。
  **残る仮説**: 19:20 のクラッシュは長さ0の class file（Forge の guard は `bytes == null` のみ）＝**古い/部分コピー jar**。
  再発時の第一手は `md5sum mods/*.jar` と crash report の class file 長。
- **fixture 欠陥2件（gui-stage へ）**: (1) GUI_SCALE の「restore」が冪等でなく Auto を通り過ぎて `guiScale=2` のまま
  （`sceneCalibrationKey` に残る）＝「restored」と書いた leg は restored でない。(2) `videoSettingsRowNames` は
  `ENABLE_VSYNC:Use VSync: OFF` を列挙しているのに、以前の VSync toggle は「行が無い」と測られていた＝finder の
  token 照合欠陥。**task-56 の toggle は token `ENABLE_VSYNC` で取り直す**（行の不在を verdict にしない）。
- **lightmap 座標は捨てられていた（proj-channel・原典＋自コード）**: `OpenGlHelper.setLightmapTextureCoords`
  （`OpenGlHelper.java:877`）は `GL13.glMultiTexCoord2f` に落ち、**stub の no-op 表（`CeltiumGlStub.java:393`）**にあり
  mirror も無い。呼び手は `RenderLiving.java:64`（全 living entity・`getBrightnessForRender()`）・
  `TileEntityRendererDispatcher.java:129`・`ItemRenderer.java:105`・`LayerSpiderEyes.java:42,50`・`LayerHeldBlock.java:42`、
  および**意図的に full-bright な** `LayerEndermanEyes.java:34`/`LayerEnderDragonEyes.java:34`（`(61680,0)`）。
  ＝sampler を足すだけでは全 batch が1テクセルを読む（現状より悪い）。**capture＋per-group push＋shader の3点が必要**で、
  **task-55 は pipelines から proj-channel へ再割当**（pipelines は3回 turn が起動しなかった）。
  受入は夜 pair で **eye layer を body family と分けて報告**（全部を暗くする修正が通らないように）。
- **entity pair の実測（weather-water・`ww30-cand-entity` vs `ww31-ref-entity`）**: 地面前景 **99.0% byte-identical**
  （`mean|d|=0.00`）、frame 全体 `mean|d|=3.87`/69.4% 一致、chest `mean|d|=1.18`（89% が8以内）、
  **entity band `mean|d|=6.07`/60% 一致**＝地形は画素一致で entity/chest の陰影が一様に数単位ずれる（＝task-55 の行）。
  **一人称の手はどちらの frame にも無い**（この pose では fixture が行使しない＝held item の向きは未行使と明記）。
  hurt flash は両側で赤みが出るが `CELTIUM_ENV_COMBINE` は出ていない（counters-only・verified と呼ばない）。
- **glyph particle の null 結果は配置だった（weather-water・task-54）**: `ww29-glyph` は `PASSED`・`id5=38/38` 受理だが
  粒子画素なし。原因は fixture の FLAME grid が y=8 面用で、既定 world の最上層 y=10 の enchant 面では
  y=9.6-12.2 が面下・視界外。hook を eye level の両 z 方向へ直し、stub に **`CELTIUM_PARTICLE_UV`**
  （受理粒子 batch の先頭4 tex coord と sprite cell `u*16,v*16`）を追加＝glyph なら x1..10/y14..15、既定 sprite なら別セル。
- **fog は明るさの原因ではない（fog-path・`r11-rain-fogon/off`）**: 雨で暗くした fog 色 `(0.4324,0.5009,0.6181)` が
  immediate path に届き（`CELTIUM_IMMEDIATE_FOG enable=true glMode=9729 start=0 end=128 colour=…`）、
  sky band が **-12.7/-20.0/-18.9**、horizon が -10.1/-12.4/-10.0 動き terrain centre は不変。
  ＝苦情は fog ではなく lighting/lightmap 項。雲は fixture に knob が無く未行使（`-Dceltium.validation.renderClouds` を依頼済み）。
- **night scene は到達可能になった**: `-Dceltium.validation.worldTime`（既定 6000・commit `f95c6ed08`）で
  `18000` を指定できる。夜 leg が queue 中（`r11b-night-fogon`・`t35c-night-ref`）。

## 0.206 **ユーザー第11報（5項目）・クラッシュの原文解析・F3 の実測値・stall latch の帰属タスク化**

**ユーザー報告（round 11・原文）**: 「ロード画面は未だに表示されてない。エンティティ/雲/プレイヤーは未だに明るい。ラグいのは少しマシになったが f3開くととてもらぐい。アイテムは治った。**GUIスケール変えようとするとクラッシュする**。」

### クラッシュ（新規・最優先・task-58）

- 前回 round 以降の crash report は1件だけ: `crash-2026-10-11_19.20.30-client.txt`（19:20・`Description: Rendering entity in world`）。
  `NoClassDefFoundError: org/soutalab/celtium/vulkan/gl/CeltiumGlStub$ReadableRange`
  ← `CeltiumGlStub.celtium$refreshReadableMappings(:3301)` ← `celtium$readableMapping(:3221)` ← `drawArrays(:2936)`
  ← `WorldVertexBufferUploader.func_181679_a` ← `ModelBiped.func_78088_a`（player モデルの draw）。
- 原因鎖の最下段が決定的: `ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 0`
  at `org.objectweb.asm.ClassReader.readShort(ClassReader.java:3638)` ← `ClassReader.<init>` ←
  `EventSubscriptionTransformer.transform(EventSubscriptionTransformer.java:64)`。
  **transformer に渡された class file が長さ0だった**（`readShort` が offset 6 を読む＝class file の major version 位置）。
  原典の guard は `bytes == null` だけなので、空配列は `new ClassReader(bytes)` まで届く
  （`forge122/build/rfg/minecraft-src/.../EventSubscriptionTransformer.java`）。
- **配布物は測定で確認した**（仮定しない）: instance の `mods/` の jar は
  `md5 ff85a8f5857eff44112e0f9cd6300507`・2,515,756 B で、`build/libs/` の同名 jar と**完全一致**。
  3つの jar（reobf/dev/本体）に **長さ0の `.class` entry は無い**（0バイトはディレクトリ entry のみ）。
  `CeltiumGlStub$ReadableRange.class` は **574 B**。
- **反証**: 20:05:06–20:33:14 のセッションは `CELTIUM_ARRIVALS total=244000`＝同じメソッドを24万回通って落ちていない。
  → 19:20 のクラッシュは**古い/部分的にコピーされた jar**の産物である可能性が最も高い。
  **ただしこれは仮説であり verdict ではない**（ユーザーは「クラッシュする」と言っている）。
  task-58（sky-draws）が「画面自身の GUI Scale 行」で再現するか、jar 全 entry の class 健全性で反証する。
  **「再現しなかった」を「修正済み」と書かない。**

### F3（新規・task-59）

- ユーザーのログ（20:05–20:33）: `CELTIUM_FONT_GLYPH_SUMMARY windows=1012751 defaultWindows=1012751 unicodeWindows=0
  bridgeWindows=0 quads=1012751 submitted=1012751 accepted=873822 refused=138929 submitMillis=47246 reasons={backend=138929}`。
  **28分で101万 glyph window＝1 window あたり46.6 µs**、submit 内で47.2秒、**13.7% は誰も描いていない**（refused）。
  F3 overlay はゲーム中で最も文字が密なので、「f3開くととてもらぐい」の形と一致する。task-59（gui-stage）が
  F3 on/off の p50/p95 を同一 leg で測り、1テキスト描画内の連続 glyph quad を1 window にまとめる。

### GPU stall latch（新規・task-60）

- ユーザーのログの拒否ヒストグラム: `the GPU stalled; no further drawing until the renderer is rebuilt`
  が format 7=21,366 / 4=12,320 / 6=9,968 / 5=3,337 / 2=2,483 / 1=750 ＝**約50,200 batch**。
- `gpuStalled` は **latch**（`VulkanTerrainGpuBackend.java:1102`・`:1126` で wait timeout `:10534` から設定・
  `:3629`/`:3665` で全拒否・`:5084` で受理済み geometry を破棄）。`:10516` のコメント自身が
  **false stall は permanent だから高くつく**と警告している。prime suspect は upload 経路の per-segment
  `vkQueueWaitIdle`（`VulkanContext.beginCommands`＝task-51）。frame-composite が budget 終了で返却 → **fog-path が受領**。

### その他の実測・裁定

- **ロード画面（task-61・texture-mirror）**: splash は renderer に届いている（§0.201/§0.202）が、ユーザーには見えない。
  「renderer に届いた」は「ユーザーが見た」ではない。presented frame で内容が止まる位置を計測で名指しする。
- **VUID 回帰の報告（proj-channel・`t51-refusal`・jar `fa9487e0cd17ee7c`）**: 通常 terrain leg が
  `validationClean=false` / `validationMessageCount=20`（`VUID-vkCmdBeginRenderPass-initialLayout-00900` ＋
  `VUID-vkCmdDraw-None-09600`・`postChains=0`）。frame-composite の attach 側 hand-back は現行 tree に入っている
  （`:5666` `celtium$syncPublishedChainOutput(offTarget.chainPairColorImage())` → `:5671`
  `celtium$transitionPublishedFrameTextures(true)`・clear/attach の前）。**現行 jar で再測するのが task-61 の最初の仕事**
  （`validationClean=false` の leg は受入を運べない）。
- **model batch の lightmap 不在（proj-channel・原典＋自コード）**: `celtium$enqueueModelMesh` は ITEM group を作り
  `group.lightParams = celtium$lightParams(state, modelView)`（`RenderHelper.enableStandardItemLighting` の固定機能ライト）
  だけを積む。vanilla が world 位置の明るさに使う **unit-1 light map**（`OpenGlHelper.setLightmapTextureCoords`）は
  経路に無く、lit ITEM pipeline は texture を1枚しか bind しない（`immediate_tex_color_lit.vert` に lightmap uniform 無し）。
  ＝**entity/player が夜も昼の明るさのまま**。修正は task-55 の lit ITEM 側（model capture の変更ではない）。
- **拒否4件の帰属（proj-channel・`t51-refusal`）**: sky VBO の最初の数 draw が mirror の identity 射影で拒否されるのは
  **最初の `CELTIUM_GLUPERSPECTIVE` より前**だから（census は log 450行・最初の gluPerspective は 471行）。
  steady-state は被覆済み。件数は per-boot（2〜4）で、「毎 run 2件」ではなく**「≤4・全て最初の受理 draw より前」**と書く。
- **計器の配線（Lead・commit `05935fdd5`）**: 承認済みの slow-frame capture は**収集されるだけで report に書かれていなかった**
  → `slowFrameThresholdMs` / `slowFrameCount` / `slowFrames` として出力（`slowFrames` は `worldTick=<n> <ms>ms <handoff line>`）。
  p95/p99 の帰属はこの計器で取る（**中央値を受入にしない**）。
- **新タスク**: task-58（GUI scale crash・sky-draws）/ task-59（F3 glyph 費用・gui-stage）/ task-60（stall latch・fog-path）/
  task-61（ロード画面＋VUID 再測・texture-mirror）。night scene 用の `-Dceltium.validation.worldTime`（既定 6000）を
  gui-stage が land（4タスクが待っている）。

## 0.205 **task-52 の filter 修正着地（atlas が LINEAR で作られていた実測）・新しい VUID クラス・spike 帰属の計器を承認**

- **task-52 の根因が実測で確定（texture-mirror・jar `c6e7a8058c3fd511c068c2f8`）**:
  `CELTIUM_SAMPLER_FILTER glTexture=12 repeatU=true repeatV=true mag=NEAREST min=NEAREST mipmapMode=LINEAR maxLod=1
  (mag=record min=record; the sampler was built before the game set it) **wasMag=LINEAR wasMin=LINEAR**`——
  **block atlas（GUI item の texture・name 12）は write-shape 推論で LINEAR mag/min で作られていた**（mirror が params 到着前に問い合わせたため）。
  修正: `confirmWrapFromRecord` → **`confirmSamplerStateFromRecord`**（MAG/MIN も record から読み、magnification と minification を分離・
  record が無い texture は従来通り・4つの差のいずれかで sampler を作り直し generation bump・どちらの source が勝ったかを log）。
  **global mipmap 変更はしない**ので terrain band の 6.207/5.86% は不変。**注意2点を明記**: (1) この scene の `maxLod=1`（per-level content path が reach を上げていない）
  (2) **この leg は clean でない**（`validationMessageCount=20`・全て `VUID-vkCmdBeginRenderPass-initialLayout-00900`＝attachment 0 の initial layout が GENERAL 期待に対し
  SHADER_READ_ONLY）→ sampler の証拠は有効だが、**画の受入（sharpness/edge）は clean な leg で取り直す**。
- **新しい VUID クラスは frame-composite の遷移と同じ image lifecycle 領域**として差し戻し: published name を `SHADER_READ_ONLY` にしたまま後続 pass が attachment として使うと
  initial layout が食い違う——sampling 側遷移と write 側遷移の順序を両方向で確認し、**両 VUID で `validationMessageCount=0`** を同じ leg（`splash-vuid`）で受入。
- **spike 帰属の計器を承認（frame-composite）**: `-Dceltium.validation.slowFrameMs`（既定50）で閾値超えの frame の handoff summary を bounded list に記録し、
  **p95/p99 に phase（immediate/terrain/present/upload/wait）を付ける**。まず既存計器（`bootstrapQueueWaits` vs spike frame・`immediateWindows`/`worldPassBoundaryFlushes` per frame）で確認。
  panorama/splash copy は world-only leg では `textureCopyReadbacks=0`（call graph からの除外・次 leg で再確認）。

## 0.204 **task-50 の GUI case を現行 tree で受入（スロット充填率が参照と一致）・glyph particle の fixture hook 投入**

- **GUI item の受入（weather-water・`ww27-cand` jar `fa9487e0cd17ee7c` vs upstream `d12425eaaf184e79`・同一 fixture/scene/pose/tick）**:
  両 frame は同じ JEI "Building Blocks" 画面（等角 block アイコン・同じ "Gold Ore" tooltip・同じ hotbar）。実測:
  **per-slot icon fill fraction**（スロット画素のうち modal colour と異なる割合・6スロット）: 候補
  `[0.736,0.722,0.722,0.746,0.724,0.682]`・参照 `[0.692,0.722,0.722,0.746,0.724,0.696]`——
  **等角アイコンはスロットの約70%を占め、平坦テクスチャなら ~1.0**。**ユーザーの「枠いっぱい」は解消し参照と 0.04 以内で一致**。
  item-grid (375,210..915,510) `mean|d|=5.32`・**60.5% が byte-identical**、tooltip `mean|d|=0.66`。
  crop は `.celtium-work/ww-evidence/crops/item-grid-{cand,ref}.png`・`hotbar-{cand,ref}.png`。
  **正直な caveat 2つ（実測）**: 候補側は fixture 自身の debug テキスト行が出る（`mean|d|=13.92`＝overlay の差で renderer の差ではない）、
  両 leg は **build 状態が違う**（候補 listedSections=8・21 world frames／参照 16・69）ため world 背景も違う（左帯 `mean|d|=6.04`）——
  **有効な比較は item crop であって world frame ではない**。held/dropped GROUND/hurt flash は inventory を開いていると見えないため、
  画面なしの `ww30-cand-entity`/`ww31-ref-entity` を実行中。
- **task-54 の fixture hook 投入（weather-water）**: `celtium.validation.glyphParticles=true`＋report field `glyphParticlesSpawned`。
  still-particle grid 上に tick 197 で 24 個の `ENCHANTMENT_TABLE` を spawn（javadoc に「実 table が使えない実測理由2つ」＝
  superflat 層に bookshelf が無い・全面 plane では midpoint の `!isAirBlock` で loop が先に break する）。`ww29-glyph` が
  `textureDiagnostics=true` で走り、**crop（文字か汎用 particle か）と particle atlas の `CELTIUM_MIRROR_SOURCE` を同一 run で**取る
  ＝「glyph 領域が mirror にあるか」を仮定せずに答える。

## 0.203 **画面背後 27.7% の帰属確定（共有 overlay ではなく inventory panel 自身）・VSync toggle は「行が無い」という計測**

- **gui-stage の判別 pair（事前登録した分岐が的中）**: 参照 `wbs-vs-ref2`（upstream Celeritas＋fog-path fixture）と候補を**両方コピー・hash してから**
  比較（`vs-ref2-full.png 27758cb74a69e25d…`・`vs-cand-gui-full.png 91ef7f2fbdfb58db…`）。

  | 領域 | 候補/参照 比 | >8 |
  |---|---|---|
  | sky band | **1.000,0.993,0.982** | 24.59% |
  | terrain centre | 0.976,0.981,0.985 | 5.54% |
  | panel | 0.983,0.987,0.990 | 4.56% |
  | **Done button** | **1.000,1.000,1.000** | **8/30,000（0.03%）** |
  | whole frame | 0.959,0.966,0.972 | 12.44% |

  **video settings 画面の背後では両 renderer が sky band を 0.22 倍に暗くし、候補は参照に 1.000,0.993,0.982 で一致**。
  同じ build 状態の **inventory 画面背後では terrain centre 27.74%** 差。overlay は両画面で共有されているので、
  **原因は共有 `GuiScreen.drawWorldBackground` ではなく inventory panel 自身の alpha/blend か被覆幾何**＝gui-stage の stage。
  **事前に書いた読み（video settings の背後で差が小さい→inventory panel）が的中**した形で記録。
- **VSync toggle（sky-draws）は negative を計測として返した**: flag は build に逐語で入っているが、
  **その画面の list に `VSYNC` という名前の行が無い**ため finder が既定行へ落ち、`videoSettingsOptionName=GRAPHICS`・
  `changes=0`・`CELTIUM_PRESENT_MODE_REQUEST` 無し（＝option page の setter が走っていない）。
  `guiScreenOpenedVia` が「option list の1ページ目・Video Settings ボタン無し」を示す＝Sodium の VSync は別ページの可能性。
  → **次は token を測る**: list が実際に持つ行名を `videoSettingsRowNames`（64 件上限）として記録する diff を sky-draws が送付・私が配置。
  **`setVSyncRequested` を fixture から呼ぶ shortcut は受入にしない**（プレイヤーの経路ではない）。
  副次結果: この leg は layer off で `validationMessageCount=0`＝先の VUID は present-mode 変更の副作用ではないと確認。

## 0.202 **VUID の修正着地・task-47 完了（床の全数再導出）・item blur の根因を原典/自コードから特定**

- **VUID 修正（frame-composite・`celtium-t36d fa9487e0cd17ee7c`）**: published map が `VulkanGlTexture` wrapper を持ち、
  **全 published name** に outline と同じ扱いをする——sampling 側 `celtium$transitionPublishedFrameTextures(false)`
  （`ensureSampledLayout` を2つの sampling site で、pass の外・draw の前に記録＝VUID が要求した位置）、write 側は
  chain submission で `true`（`ensureGeneralLayout`）、unpublish は「現在の pair image でない name」を外して計数
  （`publishedFrameTexturesUnpublished`）。検証 leg `splash-vuid`（validation on・world capture＝splash は起動時に走る）を queue。
  **受入は `validationMessageCount` が 0 に戻ること**。3つの image が pair の colour image でなければ別 publisher が残る、と明記。
- **task-47 完了（acceptance）**: enforced floor を全数監査し、**誤った provenance は3つ**（world frame 由来の
  `nonBackgroundFraction>=0.40`・`verticalBands>=250`・`blackFraction<=0.05` を GUI stage にも適用）。
  GUI frame 自身から **0.37 / 89 / 0.118** へ再導出。**`panelDistinctColours>=4000` は「欠陥を要求する床」**だった
  （bridge 時代の背景欠落 frame 由来。正しく描かれた画面は世界より平坦＝133色）→ **33**（参照 133 の25%・presence guard）。
  `panelChangedFraction`/`panelStrongFraction` は stage 正・picture 誤 → 0.63/0.545。**新設の判別床** `panelMeanLuma <= 0.70x`
  （参照 0.526・背景欠落 0.799・未描画 1.0）。**実装の罠**: `celtium$resolveGate()` が 13引数 constructor で gate を再構築し
  `guiFloors` を落としていた（最初の検証 run が古い床を報告）→ 修正。検証: `vs47d-cand` **PASSED**（GUI 由来床）、
  bridge 対照は新 luma 床で正しく FAIL（背景欠落 frame を捕捉）。crop は `.celtium-work/acceptance/crops/vs47d/` に保存。
- **item blur の根因（Lead が原典と自コードから特定・task-52）**: `nearestFilterFor` は record が**その時点で無ければ**
  write-shape 推論 `!(x==0&&y==0&&w>0&&h>0)` に落ち、**全面 upload（atlas）では false＝LINEAR**。
  texture-mirror 自身の記録どおり mirror は announce 時に params 到着前へ問い合わせる（texture 2/8/12/13 が `recorded=false`）。
  そして **`confirmWrapFromRecord` は wrap だけを再照会し、MAG/MIN filter は再照会しない**——早期に作られた atlas mirror は
  LINEAR 拡大のまま固定。record の答えは NEAREST（`texture=2 pname=0x2800 (MAG_FILTER) value=0x2600 (NEAREST)`、3/6 も同様）。
  GUI item は scale 3-4 で**拡大**されるので、LINEAR mag は「形は正しいが低解像度」そのもの。→ 同じ one-shot pattern を
  filter へ拡張するよう依頼（record が無い texture は従来の推論を維持・どちらの source が勝ったかを明記）。

## 0.201 **VUID 検出（`vkCmdDraw-None-09600`・sampled image が GENERAL）＝splash publication の layout 遷移欠落へ帰属**

- **sky-draws の `vsync-leg`（AMD・fixture PASSED・picture あり）で `validationClean=false`・10 ×
  `VUID-vkCmdDraw-None-09600`**: `VkImage … (mipLevel 0, arrayLayer 0) は SHADER_READ_ONLY_OPTIMAL を期待されているが
  現在 GENERAL`、3つの image（`0x1400000000140`・`0x19b000000019b`・`0x2260000000226`）で `vkQueueSubmit` 時。
  同日の以前の leg は `validationClean=true` だったので**新しい状態**。
  **帰属**: frame-composite が自分で名指ししていた `celtium$publishFrameTexture` の layout 問題——
  publish した name を main pass の descriptor（`SHADER_READ_ONLY`）が sample するが、image は chain pair の
  `GENERAL` のまま。**outline の name だけが sampling-site 遷移を受けている**。→ 全ての published name に遷移を適用し、
  pair が再び書く時に戻す（unpublish も同様）。**受入は `validationClean=true` の復帰＋最初の loading frame が画面に出ること**。
  再現: `.celtium-work/runtime/vsync-leg`・凍結 jar `.celtium-work/runtime/intel-legs/jars/celtium-vsync.jar`・`--validation`。
  **VUID は証拠であり、受入が黙って通してはいけない**（AGENTS.md の規則）。
- **task-56（sky-draws）実装済み**: VSync on=FIFO（全 surface が必ず提供）／off=MAILBOX→IMMEDIATE、どちらも無ければ
  FIFO へ**fallback を名指しして計数**（`PRESENT_MODE_FALLBACKS`）。`CELTIUM_PRESENT_MODE requested/chosen/supported` を
  作成ごとに、`_REQUEST` を変更時に、`presentModeStale()`/`presentModeRebuilds` で surface 変更と同じ経路から rebuild。
  実測 `requested=vsync-on(FIFO) chosen=FIFO supported=[IMMEDIATE,MAILBOX,FIFO,FIFO_RELAXED] fallbacks=0 changes=0`。
  **未検証**: option page の setter 経由の toggle（fixture は field を直接書き換えるため経路を通らない）——
  **startup property で policy を反転させる shortcut は採らない**（ユーザーの経路を測るため）。fixture flag の diff を私が入れる。
- **item 3 の option 表（正直な形）**: mipmap は native module が触れている（6ファイル・要測定）、
  **render distance / fullscreen / GUI scale / frame cap / cloud quality は native module に現れない＝「未追跡」**（どちらとも主張しない）。
- **task-46 完了**: `glRendererPreflight` が driver summary を end-to-end で返すことを実機で確認
  （`driverName="radv" driverID=3(MESA_RADV) driverInfo="Mesa 26.2.4-1" driverVersion=109060100(26.2.4 …) … apiVersion=1.4.354 via Celtium Vulkan`）。

## 0.200 **証拠保存方針の変更（ユーザー指示）: 最新1 run のみ保存・チームへ周知**

ユーザーが「全 run の実データ保存」を撤回し、**直近1件のみ保存**へ変更。実施済み:
- 直近完了 run `runtime/vs47d-cand` を保存し、古い run の screenshot・log・描画 dump・検証 world・再生成ファイルを削除。
- **古い実行 jar スナップショット 386 個**も削除。**約 4.45 GiB 確保・空き約 16.49 GiB**。
- 台帳: **`.celtium-work/disk-cleanup-latest-only-20261011.json`**（`latest_retained` と `protected` の一覧あり）。
- 小さな `validation-process.json` / `validation-command.json` 等のメタデータと docs の監査 MD は保存。
- **実行中・待機中プロセスが参照する run は保護**: `runtime/celtium-headless-interop`・`runtime/lead-breaktex`・`runtime/t33c-atlas1`・
  `runtime/t41-ref-inv`・`runtime/vs47d-cand`・`runtime/vs47d-ref`・`runtime/wbs-vs-cand`・`runtime/ww-menu4`・
  `runtime/ww18-texparam`・`runtime/ww26-terrain`。

**チームへ周知した2規則（plan にも残す）**:
1. **古い docs が指す PNG/log/jar は既に存在しないことがある**——削除済み artifact を「読んだ」として引用しない。
   過去の結果を**新しい検証済み結果として扱わない**。数値は直近の run か残存メタデータから取り、どちらかを明記する。
2. **受入の証拠は leg 完了時に確保する**——次の整理で残るのは最新 run だけなので、crop/PNG は自分の記録ディレクトリへコピーするか、
   hash と数値を即座に record へ書く。

## 0.199 **最大監査（`docs/history/2026-10-11-full-audit-1832.md`）の受領と P1 割当・item サイズも実プレイで解消**

- **監査の要約（sha256 `49115e2c8cba55a9…`）**: Celeritas 最適化は **341/359 が upstream と同一**で残っているが、
  **転送（C03・per-segment queue idle）と最終 draw（C04・`vkCmdDrawIndexed` ループ）は元の効率ではない**。
  汎用 GL→VK 変換器ではないが、**stub の `glDrawArrays`/raw pointer/`glCopyTex` の境界は低い**（`/proc` readable 判定で
  任意 mod の直接 GL も通り得る＝callsite census は acceptance allowlist ではない）。
  **C02 は配送前 source 是正済み**（`28edeccd6`＝Forge 入口 owner・raw matrix の無条件適用は撤回）と監査が確認。
  **C01 は runtime 受入が残る**。**C07 の 208B push は 1.2/1.3 の保証 128B を超える**（1.4 のみ 256B）。
  **C06 の VSync は native present に届いていない**。**C08 の GUI floor は参照自身も落ちる**。**C09 の splash/panorama ownership** が残る。
  **C13.5**（`diagnostics()` が呼ばれるたび ArrayList に追記＝累積）は P2 の整理対象。
  **24 分野の完了ではない**・**FAIL を PASS に読み替えない**・**goal ID/revision の同期**（下記で実施）が明記されている。
- **新規 P1 タスク**: **task-56**（sky-draws・VSync と他 option の native 適用）・**task-57**（pipelines・208B→128B 契約と
  fog/light の分離）。既存の task-51（C03）・task-47（C08）・task-36/53（C09/lag）と合わせて監査の P1 を全て owner 付きにした。
- **goal header 同期（監査 17.2）**: live は `goal-780c63c7-acf3-4426-80dc-6c99d30d8627`（revision 1・active/armed・
  roundsStarted 5・maxGoalRounds 256）。plan と `docs/goal.md` の旧 `goal-0d07c3b5-…`（revision 61）記載を live 値へ更新した。
- **実プレイ追加確認**: ユーザー「**アイテムの大きさも治りました**」＝task-50 の GROUND scale も実プレイで解消
  （`t45-entity-hurt` の kept frame でドロップ apple が正しい大きさである私の確認と一致）。
- **proj-channel の決定的 before 数値**: 旧 jar（stub が raw matrix を適用）では **flag の on/off どちらでも dome が `F²`**
  （`0.648 = 0.803²`・`2.047 ≈ 1.428²`）——channel 側の flag では二重適用を切り分けられなかった。
  これが「raw call は GL-API 全体に広がり、mirror が偶然持っていた値と合成される」ことの実測で、**単一 owner 化の根拠**。
  現行 jar の A/B（`t50c-single-on/off`）は両 leg とも `≈0.805` を期待し、`0.648` なら raw がまだ適用されている＝findings。

## 0.198 **lag の計器が届いた・audio は独立欠陥ではなく lag の症状（機序を明記）・item 修正の視覚確認**

- **`bootstrapQueueWaits` が summary に届いた**（pipelines・`VulkanTerrainGpuBackend:6036` と upload 量の対）。
  これで task-51/53 の「まず測る」が可能になった。cold leg は **world tick 120-200（chunk build 窓）で 0.8〜1.7 s/frame**、
  upload 経路は `beginCommands()` の `vkQueueWaitIdle` で**segment ごとに queue を drain** する。
- **audio のとぎれは独立欠陥ではなく lag の症状**（機序）: サウンドマネージャの供給は**クライアント thread の tick**
  （=フレーム）で進むため、フレームが 0.5〜1.5 s かかると OpenAL のバッファが枯れて途切れる。
  **つまり lag を直せば audio も直る**——別々の修正を積まない。task-53 の受入はこの相関を数字で示すこと。
- **item 修正の視覚確認（Lead・現行 jar）**: `ww25-itemtransform` の kept frame（sha256 `f5789cfc5ea2d23b…`）で
  inventory の block アイコンが**等角 3D**（stone/bricks/gold ore の面陰影）・hotbar に apple/stick/sword/player head、
  背景の world も正常（地平線が直線＝**投影が二乗されていない**）。`t45-entity-hurt` の kept frame（`a2ecfbbf0a899fbe…`）では
  zombie/creeper/spider と**ドロップ apple が正しい小ささ**＝GROUND transform が同じ owner で届いている。
  両 leg の `glRenderer` は **driver summary**（`driverName="radv" driverID=3(MESA_RADV)…`）＝task-46 の着地を実機で確認。

## 0.197 **実プレイ第3報: 直った4件と残る5件（night lightmap・glyph particle・lag/audio・loading・item 解像度・ログの opengl 表記）**

ユーザーの実プレイ報告（jar `8c5d7b96` 期）:

**直った（実プレイで確認）**: **entity のダメージ赤**（task-45 の INTERPOLATE 分岐）・**item 変換**（GUI アイコンが等角に・
`ww23` の kept frame で私も確認）・**剣の持ち方**（task-50）・**Nether ポータルのパーティクルと画面効果**・**fog**・**夕日**。

**残る（帰属済み）**:
1. **夜の雲が明るすぎる・夜の entity が明るい** → **task-55**（pipelines）。lit ITEM fragment が `u_Texture`×`v_Color` のみで
   **lightmap の項が無い**（entity/cloud/held item は全てこの layout）。**定数で明るさを合わせず項を入れる**——
   day の terrain・sky band（0/256,000）・GUI item を動かさない。受入は night scene の参照比較。
2. **アイテムは直ったが解像度が低い** → **task-52**（texture-mirror）。shape は正しく blurry＝**sampler の LOD/filter**。
   task-26 で atlas の reach を最深 level に上げた（`samplerMaxLod=4`）ため、GUI の 1:1 描画で LOD 0 を引くべき所を
   上位 level から引いている可能性。**per-texture で直す**（terrain band 6.207/5.86% は reach 依存）。
3. **ラグは未改善・音声がとぎれとぎれ** → **task-53**（frame-composite）＋ **task-51**（pipelines）。
   最有力は upload 経路の `vkQueueWaitIdle`（per segment・`bootstrapQueueWaits`）。**まず計器を届かせる**
   （`CELTIUM_WAIT_CENSUS` は `VulkanContext.delete()` からのみで**どの run にも出ていない**＝現状は測定不能）。
4. **ロード画面が出ない** → weather-water（splash は renderer に届いた）＋ frame-composite（splash FBO の owned image）。
5. **エンチャント台の文字パーティクルが出ない**（本は出る）→ **task-54**（weather-water）。本は `TileEntityEnchantmentTableRenderer` で正常、
   文字は `ENCHANTMENT_TABLE` の**パーティクル**（`particleTextureIndex` の sprite 選択）——既定 sprite で描いているか未到達かを測定。
6. **手持ちアイテムになぞの枠** → pipelines の次の leg で crop を取って正体を特定（item texture の縁か・outline pass か・rescaleNormal/blend か）。
7. **「vulkanonlyなのにログに opengl が大量」** → 原因は **`site=[…]` のスタック文字列が `org.lwjgl.opengl.GL11` を毎行フルで書いていた**こと
   （実測 83/813 行が "opengl" を含み、大半がこれ）。`celtium$callSite` に `celtium$shortSiteName` を入れ、
   `org.lwjgl.opengl.` 等の prefix を落として `GL11.glDrawArrays:2559 <- …` の形にした（jar `9b1ecb1acd183e3cc554` を instance へ投入）。
   stub の残る周期診断も `VulkanDiagnostics` で gate するよう weather-water へ依頼。

## 0.196 **item 変換の owner を1つに固定（Lead）・画面なし統制 0.19%・最適化の継承ギャップ（task-51）**

- **二重適用の回避（Lead・jar `79fb7c3ca93a450ebfb1` を instance へ投入）**: weather-water の stub 版は
  `glMultMatrixf` を**全て適用**していたが、`Project.gluPerspective` は `GluProjectionMixin` が frustum を mirror した**後**に
  同じ生呼び出しへ到達する（proj-channel の `CELTIUM_MULT_MATRIX` の site が
  `org.lwjgl.util.glu.Project.gluPerspective:197` を名指し）＝**投影が2回適用され F×F になる**。
  → **stub の SEM_MULT_MATRIX は census のみ**（`celtium$noteMatrixMultiplyArrival`・適用ハンドラと変換 scratch は削除）、
  **item 変換は新規 `ForgeHooksClientMixin`**（`ForgeHooksClient.multiplyCurrentGlMatrix` HEAD＝GUI/FIRST/THIRD/GROUND が通る公開入口）が
  `backend.multiplyMatrix` へ渡す。**1操作=1 owner**（frustum は GluProjectionMixin・item は Forge 入口）、
  GL API 全体への行列エミュレーション拡大はしない。**応用側の計器** `mirroredMultiplies` を summary に追加（arrivals との対で読む）。
  **受入は未取得**（4ケース＋通常 camera/sky projection 不変を同一 jar で）。fog-path の before 数値が基準。
- **画面なし統制（gui-stage・等価 build 状態）**: `wbs-noscreen-ref/cand` で **sky band 0.00%・terrain centre 0.01%・
  chat 0.01%・hotbar 0.00%・whole frame 0.19%**（比は 1.000 前後）＝**画面を開いていなければ両 renderer は一致**。
  よって inventory を開いた状態の残差（whole 10.88%・terrain centre 27.74%）は**画面を開いていること由来**と確定。
  **方法則**: 等価化の基準は **listed-section 数**（frame 数でも tick でもない。44 vs 45 sections で 1165 vs 412 frames でも可）。
  `captureListedSections` が1を超えて違う pair は比較として読まない。
- **最適化の継承ギャップ（ユーザー監査・task-51）**: legacy common/forge122 の 341/359 は upstream 989ebf2e と同一（17変更/1削除）。
  ただし `VulkanArena.upload`（segment ごと）→ `VulkanBuffer.write` → `context.recordStagingCopy` → `beginCommands()`…`endCommands()` が
  **アップロードごとに `vkQueueWaitIdle`**（`bootstrapQueueWaits`。ソース自身が "deliberately bootstrap quality; a real frame uses a
  small pool of buffers and fences" と書いている）。upstream の mapped staging は copy を合流し fence で retire する。
  **cold window（chunk build・0.8〜1.7s/frame）の最有力候補**。→ task-51（pipelines）: 先に測定（`bootstrapQueueWaits`＋upload 量＋
  cold/warm 両窓）、その後に buffer pool＋fence へ。terrain draw の `vkCmdDrawIndexed` ループも測定してから。
  **「ソース継承」と「同性能」を分けて報告する**。
- **task-45（pipelines）**: half (a) 検証済み（`CELTIUM_ENV_COMBINE rgba=1.0,0.0,0.0,0.3` が hurt-on にのみ出る＝1フラグ統制）、
  half (b) は着地済みで SPIR-V も一致。**受入 run は mob が画面内に要る**ため pipelines が entity-models の scene 設定で取る。
  `drawnByFormat[4]=4503` vs `acceptedByFormat[4]=15` は**単位が違う**（borrowed model group は acceptance 経路を通らない）と明記。

## 0.195 **task-50 の実装が tree に入った（glMultMatrix が mirror に届く）・chain の blit は frame image に痕跡ゼロ**

- **task-50 実装済み・緑**（jar `5649052b3b8416c1165d` を instance へ投入）:
  - stub（weather-water）: `fn("glMultMatrixf", R_VOID, SEM_MULT_MATRIX, A_PTR)`＋`celtium$multiplyMatrix(fn, address)`。
    `glMultMatrixd` は float へ変換して同じ経路。**到着/適用/拒否の census**（`MATRIX_MULTIPLY_ARRIVALS/APPLIED/REFUSED`）を持ち、
    「到着したのに適用されない」が miss として見える形を保つ。
  - backend（pipelines が確認）: `VulkanTerrainGpuBackend:3073` に
    `multiplyMatrix(FloatBuffer)`（`matricesDirty=true`・`immediateState.multiplyMatrixBuffer`・`immediateStateCalls++`）が**既にあり**、
    共通 interface 宣言（`:115`）経由で stub の呼び出しが解決する。**追加実装は不要だった**。
  - **受入は未取得**（GUI slot/hotbar の等角アイコン・剣の向き・ドロップ item サイズ・等価 build 状態で参照比較）。
    pipelines の `t45-hurt-on/off` pair と同一 leg で取る予定。fog-path の **before 数値**（item 領域 71.31%・whole frame 0.68%）が基準。
  - `GluProjectionMixin` も同じ生 `glMultMatrixf` を使うので、`MATRIX_MULTIPLY_APPLIED` が射影スタックに正しい時点で乗ったかを見る。
- **chain の最終 blit は frame image に痕跡ゼロ（proj-channel・実測）**: dump が捕まえたのは**まさに `main` を out とする chain**
  （pass trace: `0..3 blur`・`4..8 spider`・**`9 program=blit … out=main`**）。dump 画像は **flipud で presented frame と
  0/921,600 差＝byte-identical**（identity 向きでは 98.11% 差＝readback は presented に対し上下反転、記録すべき事実）。
  つまり `waitForIdle()` 後でも **blit の書き込みが frame image に無い**。参照は detail 0.179→0.097（半減）、我々は不変。
  → 次の計器は **pass 0 の出力 target**（pass 0 は `main` を読んで `owned#2072331124` へ書く）: owned が空なら**executor 内**の
  pipeline/descriptor/viewport、blur 済みなら **`outIsMain` 経路**。frame-composite が dump 行に
  `chain=/passes=/first=/last=/inIsMain=/outIsMain=/outFbId=` を追加し `chainFrameDumps=8` の再取得を queue 済み。
- **四分円の fill は per-boot**: proj-channel の boot は黒（`black=0.2500`）、私の boot は白——幾何は同一で chain とは独立
  （chain の書き込みを挟んでも frame image は presented と byte-identical）。task-8 の latch/clear 機構の候補のまま。

## 0.194 **Intel 1920x1080 menu 受入・driver 情報の実値（両 vendor）・world-crash の一部は script 欠落の可能性**

- **task-40 完了（sky-draws・Intel）**: 1920x1080 menu が窓を満たす（`resizeWindowFrom 1280x720 → 1920x1080`・
  `resizeCompositorResult=mode=1920x1080@60Hz exit=0 success`・`glfwWindowSizeAtEnd=1920x1080`・kept dump は **1920x1080**・
  black 9.59%・449色・nonBackground 0.9718。下帯 72.40% black は**panorama の暗い下三分の一**で footer 文字が枠内にある＝穴ではない。
  同 leg の world dump は 1920x1080・**black 0.0000%**）。terrain は **PASSED**（`sceneCalibrationKeyMatches=true`・nonBg 0.761032）で
  保存済み Intel 参照に対し **rows 0-120 と 0-200 が 0 px >8**（mean 0.733/0.453・max 4）・rows 200-260 21.75%（silhouette・AMD と同形）・
  rows 260-720 **6 px**。**未検証**: 同 tree の Intel 参照 leg（slot 待ちで未起動）・Intel の遷移/panorama copy/glow/B05・
  B04 の wait-failure・F3 文字列の end-to-end・1920x1080 以外の resize。
- **Intel の `CELTIUM_DEVICE_CONTRACT`**: `deviceType=1 apiVersion=1.4 required=[api>=1.2, queue=graphics+transfer, VK_KHR_swapchain,
  color=R8G8B8A8_UNORM depth=D32_SFLOAT samples=1] **fdInterop=false** enabledDeviceExtensions=[VK_KHR_swapchain]
  limits=[maxPushConstantsSize=256 immediatePushBytes=208 maxImageDimension2D=16384] **advertised=[externalMemoryFd=true
  externalSemaphoreFd=true swapchain=true]**`——B02 の第2 vendor（fd 拡張は advertise されているが要求も有効化もしていない）。
- **task-46 実装・両 vendor 実測**: `VkPhysicalDeviceDriverProperties`＋`driverVersion` を**実描画 device**から取得し、
  `CELTIUM_DRIVER_INFO`／`driverInfo=[…]` として出す。AMD `driverName="radv" driverID=3(MESA_RADV) driverInfo="Mesa 26.2.4-1"
  driverVersion=109060100(26.2.4)`、Intel `driverName="Intel open-source Mesa driver" driverID=6 driverInfo="Mesa 26.2.4-1"`。
  **「今描いているのは何か」の権威行は `CELTIUM_DRIVER_INFO`**（GL bootstrap probe の `glRenderer`/`vulkanDevice` は
  以前の identity/interop の問いで、混同しない）。両 leg PASSED・VUID 0。
- **ユーザーのセッションの切り分け**: 18:27 のログに **`Celtium stub GL` は0件**＝fallback ではなく**配線済みで device も取得済み**。
  欠けていたのは**driver identity**（`CELTIUM_DRIVER_INFO` が無い＝何も `VkPhysicalDeviceDriverProperties` を問い合わせていなかった）。
  → stub の `celtium$nativeRendererName()` を `driverSummary()` にし、device 不在時は **"unavailable"** にする（承認済み・
  weather-water のファイルなので task-50 の編集と衝突させない）。
- **process 発見（重要）**: `VK_LAYER_PATH` を渡し忘れた leg は device 作成に失敗し、その下流が
  `World.getSkyColorBody:1608` ← `EntityRenderer.func_78466_h:1910` の NPE になる。**同じ形が `compcap-marker`・`p538-glless`・
  `ww15-gl-terrain`・`ww16-pre-window` にある**——tree の退行ではなく**script の export 欠落**の可能性が高く、確認中。
  （plan が「world-crash」として記録している一部は script 欠陥かもしれない。）

## 0.193 **設定画面の背景は修正・検証済み（ユーザー苦情の1つが閉じた）・poison pair が overlay の読み先を確定**

- **texture-mirror（task-39 part 4）**: videoSettings の GUI frame を保存済み upstream 参照（`vs-pic-ref`・実 GL）と比較し、
  **tiled 背景の左マージン (20,120)-(150,620)・右マージン (1150,120)-(1270,620)・下帯 (20,620)-(1260,700)・
  buttons 領域 (180,100)-(1100,600) がすべて mean 0.00・>8 0.00%＝ビット一致**。
  **修正前**（wrap record 消費前＝hardcoded CLAMP）は同じ領域が **11.43% / 17.15% / 24.55%** 差・
  distinct colour が 8 対 35 に崩壊（1枚の tile が引き伸ばされていた）。**ユーザーの「設定画面の背景がバグってる」は
  これで閉じた**（weather-water の `glTexParameter*` 記録＋texture-mirror の consumer が原因）。
- **task-39 part 3（poison pair）**: `t39-poison`（overlay＋atlas mirror を平坦マゼンタに）vs `t39-poison-control`
  （同 flags＋`breakSubjectOnly`）で block 領域 (560,300)-(720,450) が **24,000/24,000 px 差・mean 111.1**、周辺 137,589/153,600、
  sky 0 px。→ **image/view/descriptor は正しい**（poison が効く）。実 atlas では quad 全体で sampled alpha が 0.1 未満＝
  **UV が透明域に落ちているか、sampler が alpha の落ちた level を読んでいる**。`texMat=none` で affine は除外済み。
  **次の実験**: `mirrorEncode=12`（alpha=255 かつ R,G に sampled (x,y) を符号化）で UV を読み戻し、
  「region が違う」か「level が違う」かを destroy-stage sprite の atlas 矩形に対して分離する。
- **gate の床の第2例**: `guiPanelDistinctColours>=15,805` は**参照 leg 自身**（`vs-pic-ref` で 133）に落ちる——
  画素一致の frame を別 fixture 状態の床で裁いている。task-47 の provenance 監査に追加（下げない）。

## 0.192 **entity pair の受入（task-49）と、落ちた item が task-50 の根因を独立に裏づけた**

- **task-49（fog-path）**: 参照 leg を先に取り（`t49-ref-entity`）、条件を**領域表を読む前に**比較:
  両 leg **listed sections 64**・geometry 24/34 MiB（一致）／world frames 1788 vs 466・rebuild queue `P1=013` vs `P1=063`
  （**一致しない**）／capture tick 1407 vs 2175。よって「**listed geometry では一致、queue 状態では不一致**」と明記。
  領域（比・>8）: sky band **1.000,1.000,1.000 / 0.00%**・horizon 1.24%・terrain centre 1.52%・chat 0.01%・hotbar 0.00%・
  entity cone 1.38%・**whole frame 1.000,0.999,1.006 / 0.68%**。per family: mobs 3.85%・creeper 4.66%・zombie 5.50%・spider 6.78%・
  stone 3.07%・**blockentities/chest/sign は全て 0.00%**・**item (820,380,880,440) 0.867,0.493,0.559 / 71.31%**。
  **item は tick 不一致のせいではない**（tick 一致の旧 pair でも 58.42%）＝**N02/task-7 の実欠陥**。
  per-frame formats は候補のみ（参照は histogram を出さない＝参照側の限界）。
- **この item 差は task-50 の根因を独立に裏づける**: 落ちた item の GROUND transform（translate/rotate/scale 0.5）が
  `glMultMatrix` 経由で落ちているため**モデル尺度のまま大きく**描かれ、向き/法線も狂って**暗く**なる——fog-path の crop が示す
  「large dark sprite vs slender item」と一致。**task-50 の前画像＝受入の基準**として記録。
- **warm/cold split の初実測**（`driver-amd`）: cold p50 **327.3ms** / p95 1049.7・**warm は sample 0**（`frameIntervalWarmP50Ms=-1`＝
  run が world tick 400 に到達せず）。`frameIntervalWindows` が両窓を明示するので、cold を benchmark と取り違えない。

## 0.191 **GUI アイテムが平坦なのは Forge の生 `glMultMatrix` が mirror を迂回していたため（Lead が原典で特定・task-50）**

ユーザーの「アイテム画面、ブロックが枠いっぱいに表示されている」の**根因を両側の原典で確定**した（推測ではない）:

1. Forge は item の `ItemCameraTransforms` を **GlStateManager を迂回する生 GL 呼び出し**で適用する:
   `ForgeHooksClient.handleCameraTransforms`（Forge 1.12.2-14.23.5.2847・`ForgeHooksClient.java:425-440`）→
   `multiplyCurrentGlMatrix(matrix)`（同 `:444-455`）の本体が **`GL11.glMultMatrix(matrixBuf)`**。
2. 他の行列呼び出しは vanilla が `GlStateManager` を通るので `GlStateManagerMixin` が mirror する
   （`func_179109_b`=translate float・`func_179139_a`=scale double・`func_179130_a`=ortho 等）。
3. **`glMultMatrixf` は stub の no-op 表にある**（`CeltiumGlStub.java:381`。`:1284` の `MIRROR_REQUIRED` は到着 census のみ）——
   数えられるが**適用されない**。よって GUI item group の modelview は GUI ortho のままで、block model の6面が同じ正方形に落ちる
   ＝**スロットを平坦なテクスチャが埋める**という症状そのもの。
4. 受け側は既にある: `VulkanGlState.multiplyMatrix(float[])`（`VulkanGlState.java:219`）を translate/scale/rotate が使っている。
   backend に `multMatrix` の入口だけが無い。

**task-50**（stub 半分＝weather-water・backend 半分＝pipelines）で、stub に `glMultMatrixf`/`glMultMatrixd` の意味論 case を足し、
backend に `multMatrix` を足す。**受入**: inventory/JEI のスロットと hotbar が等角の block アイコンになり（等価 build 状態で参照と crop 比較）、
world 段が不変（sky 0/256,000・terrain centre 1〜6%）・`validationMessageCount=0`。`GluProjectionMixin` も同じ生
`GL11.glMultMatrixf` を使うので、射影チャネルの mirror が何を得るかも記録する。

## 0.190 **video settings の画は一致（task-42 完了）・chain は絵に寄与しない（noise floor）・黒四分円は per-boot**

- **task-42 完了（gui-stage）**: 参照 `vs-pic-ref`（upstream Celeritas・tick 235）と候補 `vs-pic-cand`（GL-less・jar `28dabb1dde958a7f`・
  tick 297）で **sceneCalibrationKey 完全一致**・両 leg とも操作した画面を撮影・両 leg とも `Clouds: Fancy` へ復元（variant bug も修正）。

  | 領域 | 比 | >8 |
  |---|---|---|
  | title (0,20,1280,60) | 1.063,1.055,1.044 | 9,297/51,200（**18.16%**） |
  | option rows 左右 | 0.985–0.992 | 4.27% |
  | panel (180,80,1100,624) | 0.983,0.987,0.990 | 4.56% |
  | **Done button** | **1.000,1.000,1.000** | **8/30,000（0.03%）** |
  | whole frame | 0.959,0.966,0.972 | 12.44% |

  **画面自身の描画は正しい**（Done は画素一致・rows/panel は 1〜2%）。**title 帯 18.16% は font 経路**（別項目として記録）、
  whole frame の 12.44% は**画面の背後の world**。両 leg とも `verticalBands`（212/229 対 world 校正の床 250）で FAILED
  ＝task-47 の provenance 修正待ち（**下げない**）。
- **chain は絵に寄与しない（proj-channel・noise floor 実測）**: 同一 jar・同一 fixture の3 boot、差は `spectator=spider` のみ。
  黒四分円 `(640,360)-(1279,719)` を除いた領域で **no-spectator の2 boot は 14 px 差（0.004%）＝noise floor ≈ 0**、
  spectator leg は自分のパターンの boot と同一（14 px）。**chain は `postChains=6 postPasses=60 postTargets=4`・
  `spectatorResult=loaded` で走っているのに出力が presented 画像に届かない**——**全ての chain で passes の下流**が欠陥で、
  adopted silhouette はその特殊例。参照は detail 0.179→0.097（放射ブラー）で我々は不変。
  → 次の計器は frame image の dump（frame-composite の `celtium$dumpImagesAtChainTime()`、chain 自身の `submitCommands()` 直後）。
- **黒四分円は per-boot パターン（task-8 の flag-free 統制）**: 3 boot 中1つで `0.2500`・他2つは `0.0000`、しかも
  **no-spectator の2 boot 間で 3.68%** の差——spectator flag でも chain でもない。frame-composite の帰属（`Minecraft.java:1199`
  の `clear(16640)`・`RenderGlobal.java:679`・latch 消費が1フレーム遅れた時の到達領域）と整合。
- **task-41 完了（fog-path）**: inventory pair を等価 build 状態で（43 sections・521 vs 1384 frames）取り直し、
  **48.0% → 10.88%**・sky 83.4% → **1.28%**。**entity pair は未取得と明記**（参照 leg が slot-wait 900s で起動せず）＝
  task-49 で参照 leg 優先の取り直し。**方法則**: `acceptedByFormat` は run 累積なので**フレーム数で割って**比較（55.5 vs 50.8/frame）。
- **マシン負荷**: 15 GB 中 11 used・空き 1 GB・harness script 多数 queue。**1担当あたり queue は最大2 leg**（frame-composite は順守）。

## 0.189 **splash が renderer に届いた・F3 が実 driver を名乗る・wrap の実測・frame interval は「cold」だった**

- **ww19（weather-water）**: `CELTIUM_WINDOW_RENDERER created=true attempt=1 ticks=0 **stage=createDisplay**`。
  `no terrain backend is installed` は **0件**（ww12 は3件）。run の最初の immediate group が splash 自身:
  `CELTIUM_IMMEDIATE_ENQ frame=0 group=1 proj=0.0,0.0,0.0,-0.5 mvT=0.0,0.0,-2000.0 fmt=2 texture=8 batches=2
  target=1563611679 mirrored=true worldPass=false`——`mvT z=-2000` は `Minecraft.drawSplashScreen` の translate、
  texture 8 は Mojang logo、target は **splash 自身の FBO**。world leg は **PASSED**・VUID 0・
  **`World.getSkyColorBody` NPE は再発せず**（crash retake 完了）。
  **F3 検証**: `glRendererPreflight="AMD Radeon RX 6400 (RADV NAVI24) via Celtium Vulkan"`（以前は "Celtium stub GL"）。
  **残り**: splash の内容は FBO に入り、最後の `framebufferRender` がその colour texture の mirror を sample する——
  GL-less では空（frame-composite の owned image 待ち）。
- **texture-mirror（task-39）**:
  - **混同の決着**（3 leg）: per-level+reach4 = band **6.207**/5.86%・terrain centre 0.010／generated+reach1 = 26.728/50%・1.256／
    **generated+reach4 = 21.309**/50%・1.256。**content が支配的**（reach 単独は mean を約5.4改善するが >8 を動かさない）。
  - **rows 225-256 の残差は build 状態**（pin では **bit-identical 0 px**）。terrain sampler の実効は rows 210-219 の **959 px**で、
    以前の 8,474 px という見積りは build 状態だった（task-26 の数字を下方修正）。
  - **wrap の実測**: `CELTIUM_TEX_PARAM_CENSUS recorded=194 textures=29 wrapRepeat=38 wrapClamp=0 calls=20592`——
    **ゲームは REPEAT を38回設定し CLAMP は一度も設定しない**＝hardcoded CLAMP が bug だった（record を mirror が消費）。
  - **しかし panel はまだ平坦**: `guiPanelDistinctColours=133`（参照 **15,805**）・`changedFraction 0.999996`・
    key 一致で FAILED。**wrap は必要だが十分ではない**——次の測定は GUI frame の panel crop と背景 texture の mirror dump。
- **frame interval の測定法（重要）**: fixture は **`worldTicks >= 120 && < 200`** でしか frame interval を取らない
  （= chunk build の窓）。benchmark は 600 tick warmup 後。よって記録にある 91.8/109/189/222/261/989 ms は**すべて cold**で、
  古い jar が 535 ms と「悪い」のも build throughput の差。**warm/cold を1つの report で分けて出す**よう frame-composite へ依頼。
- **マシン負荷**: 15 GB 中 10 used・Gradle daemon 2・**harness script 15 が queue**。weather-water の `ww18` は
  例外なしで SIGKILL（外部 kill・memory pressure と推定）。**1担当あたり queue は最大2 leg**に。

## 0.188 **F3 の実機表示が動いた（Intel 1920x1080 menu）・splash の install 前倒し・B09 owned-buffer**

- **Intel の 1920x1080 menu leg（task-40・sky-draws）**: `resizeCompositorResult mode=1920x1080@60Hz exit=0 success`・
  `resizeObservedAtTick=0`・`menuDumpWindowSize=1920x1080`・`worldFrameBlackFraction=0.0`・`pictureFrameDelta_world=0`。
  **`glRenderer = "Intel(R) UHD Graphics 630 (CFL GT2) via Celtium Vulkan"`**——weather-water の stub 経由の
  実 device 表示が **Intel でも**出ることを実測（task-46 はさらに driver name/info/version を実値にする）。
- **splash の install 前倒し（weather-water）**: `MinecraftMixin` が `createDisplay` RETURN と `runTick` HEAD の
  2 injector で同じ latch を叩き、行に `stage=createDisplay|runTick` を出す。`startGame` は
  `createDisplay`(505) → `beginMinecraftLoading`(514) → `SplashProgress.drawVanillaScreen`(517) → `Minecraft.drawSplashScreen`(959-1005)
  → `updateDisplay()`（present hook と同じ `func_175601_h`）の順なので、splash の3 batch（背景 quad・logo quad・
  `framebufferRender` composite）が **backend 不在で拒否されなくなる**。期待: その3件が `no terrain backend is installed`
  から消えて decode される。`framebufferRender` の redirect は**採らない**（vanilla を特別扱いして owned-image 欠落を隠すため）。
- **B09 owned-buffer 入口（weather-water）**: stub に `publishClientArrayBuffer`（thread-local・consume-once）＋
  `ClientArrayBufferOwnershipMixin`（`WorldVertexBufferUploader.func_181679_a` 内の `func_187439_f` INVOKE で publish。
  HEAD にしない理由: 同 mixin が HEAD で body を cancel するため、実際に描かない batch にも publish してしまう）。
  census は `ownedBufferDecoded`/`pointerOnlyDecoded`/`ownedBufferMismatch` を追加（`clientMemoryDecoded` は総数のまま）。
  `/proc` guard は**所有権でも寿命でもない**と明記し、非 Linux でも game 自身の buffer は decode できる形にした。

## 0.187 **実プレイ フィードバック（18:24 JST）の backlog 化と、ログ flood の修正（Lead）**

ユーザーが自分の instance で実プレイし、**terrain/entity/空/雲が正常に描画される**ことを確認（これは前進として記録する。
**全 family・全 driver の完成とは解釈しない**）。同時に得た観測をそのまま backlog へ入れ、既存 owner に割り当てた。
**プレイ jar hash / 起動 flag / device はこの発言では未取得**なので、現行 HEAD で再現済みの欠陥とは断定しない。

| ユーザー観測 | 帰属（task） |
|---|---|
| とてもラグい・画面が固まる | **task-44**（frame-composite）＋ **task-48**（acceptance: 通常プレイ受入行） |
| ログを異常に吐きすぎ（Prism が「100,000 行を超えたので表示停止・ストレージを浪費」と警告） | **Lead が修正**（下記） |
| アイテム画面で block が枠いっぱい・剣の持ち方が変・ドロップ item がでかすぎ | **task-43**（entity-models: item 変換 family を1バッチで比較） |
| 攻撃時に赤くならない（hurt flash）・持ってる block が暗い | **task-45**（pipelines: lit ITEM shader の INTERPOLATE 分岐と lightmap 項） |
| ロード画面が無い | weather-water（renderer を startGame の splash より前に install）＋ frame-composite（splash FBO の owned image） |
| F3 が stub GL・driver が分からない | **task-46**（sky-draws: `VkPhysicalDeviceDriverProperties` の実値。文字置換で stub 値を Vulkan と見せない） |
| video settings の色が違う・背景がバグ | gui-stage（背景＝texture wrap の REPEAT/CLAMP は weather-water 記録＋texture-mirror 消費）＋ **task-47**（acceptance: 床の provenance 修正） |

**ログ flood の修正（Lead・jar `2a3c59fe21ae831b6a3d`）**: ユーザーの構成は `-Dceltium.vulkan.glless=true` **だけ**なのに、
`CELTIUM_MODEL_MATRIX` が **model list ごと・フレームごと**に出ており（screenshot の Prism console は list=168..179 を延々と表示）、
**ログが 100,000 行を超えて Prism が表示を停止**、ストレージも浪費していた。render thread 上の同期 stdout 書き込みは
フレームコストでもある。→ `VulkanDiagnostics`（`-Dceltium.vulkan.diagnostics` または validation run のときだけ有効）を追加し、
`CELTIUM_MODEL_MATRIX`（上限 `celtium.vulkan.modelMatrixLines`=200）・`CELTIUM_ACCEPTED`（500 batch ごと）・
arena census（既定 true だった）を gate。**一回だけ出る構成識別行（GL-less marker・device・window renderer・backend 説明）は
gate しない**——support log に必要な情報だから。**他の担当も繰り返し行は同じ gate を使うこと**。
instance へ `2a3c59fe21ae831b6a3d` を install 済み。

## 0.186 **ユーザーの第2報: 固まる・GUI アイテムが枠いっぱい・ロード画面が無い・F3 は stub GL**

同じセッションからの追加報告。**frame time の実測が最優先**になった:

| run（全て validation 付き＝ユーザー構成ではない） | p50 | p95 |
|---|---|---|
| `t33b-fogon` | 91.8 ms | 199.8 ms |
| `t26` | 109.2 ms | 164.1 ms |
| `lc-panorama` | 189.4 ms | 758.4 ms |
| `cand3-terrain` | 261.5 ms | 1002.7 ms |
| **upstream 参照 `ref3-terrain`** | **21.5 ms** | 87.4 ms |

**以前の benchmark は GL-less p50 3.94 ms**（`docs/performance-2026-10-11.md`・validation 無し）。**20〜60倍の差**があり、
ユーザーはそれを体感している。validation layer と `presentReadback`（毎フレーム全画面 readback）も含まれるため、
**validation 無し・fixture のみ・854x480** の leg を queue した（`lead-perf2`）。

**帰属した4点**:
1. **固まる／非常にラグい** → task-44（frame-composite）: frame path の cost を既存計器で名指し
   （panorama copy の1フレーム7回同期 readback・task-37 の world 境界 flush・entity-models の per-batch model flush
   p95 1946 ms・immediate window 自体）。
2. **GUI アイテムが枠いっぱい** → task-43（entity-models）: `.celtium-work/acceptance/crops/mod3-jei/item-grid.png` が
   **各スロットを平坦なテクスチャが埋める**絵で、ユーザーの証言と一致。**world の item 比 ~1.0 は GUI の item を意味しない**
   （`id4=5916/5916` は batch が届いたことしか言わない）。`RenderItem` の model view（30°/225°/1/16）が
   group open 時の capture に乗っているかを原典で確認する。
3. **ロード画面が無い** → Forge の SplashProgress は GL-less で skip、vanilla splash は 256x256 で描かれる
   （frame-composite が見た「OK」の暗い画面）。weather-water へ。
4. **F3 が stub GL** → `GL_RENDERER` が stub を返すため。`CELTIUM_NATIVE_DEVICE` の名前を overlay に出す（weather-water）。

## 0.185 **ユーザーの実起動が成功（地形・entity・空・雲が正常）——残る不満3点の帰属**

ユーザーが自分の instance から起動し、**地形・entity・空・雲が正常に描画**されることを確認（log: https://mclo.gs/gB2Vtjb）。
`-Dceltium.vulkan.glless=true`・instance のライブラリに `lwjgl-vulkan-3.4.1.jar`（私の追加）・window 854x480・約2分で
`CELTIUM_ACCEPTED total=467500`。**残る不満と帰属**:

1. **非常にラグい**。log に timing 行が無い（handoff summary は report 経由のみ）ため、ユーザー構成
   （glless・`--window-size 854x480`・`handoffTimings=true`）で frame interval と handoff を測る leg を queue した。
   疑い: immediate window の per-group submit/fence（`IMMEDIATE_FLUSH_INDICES=512` なので per-group ではない）、
   menu の panorama 経路の**1フレーム7回の同期 copy readback**、entity-models の per-batch model flush（p95 1946ms の測定あり）。
2. **F3 が driver ではなく "stub gl" と表示**。debug overlay が `GL_RENDERER` を読むため stub の文字列が出る。
   `CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)"` を overlay に出す（weather-water へ）。
3. **video settings の色が少し違う／機能しているか分からない／設定画面の背景がバグっている**。
   色は B05 の GUI/sky gradient（task-29）。**背景のバグは原因を特定**:
   - `GuiOptions.drawBackground` と Cleanroom patch の `GuiSlot.drawContainerBackground`
     （`.celtium-work/cleanroom-patches/.../GuiSlot.java.patch`）は **UV が width/32 ≈ 26 まで**行く＝**GL_REPEAT が必須**。
   - stub は `glTexParameteri/f/iv/fv` を **no-op list** に持ち（`CeltiumGlStub.java:357`）、wrap の query には
     **hardcoded `0x812F` = CLAMP_TO_EDGE** を返す（`:3192`）。
   - mirror はそれを読んで `addressModeU/V = CLAMP_TO_EDGE`（`VulkanGlTexture.java:2108-2111`）。
   → **REPEAT の texture が全部 CLAMP でサンプルされ、tile 背景が1枚伸びた絵になる**。
   **修正**: stub が `glTexParameter*` を texture ごとに記録し query を記録から答える（weather-water）＋
   mirror がその記録から wrap を取る（texture-mirror・task-39 の第4項）。**F3 と合わせてユーザー可視の欠陥**。

## 0.184 **panorama の真因＝毎フレームの colour clear が feedback loop を壊していた（frame-composite）**

- 暗い「OK」画面は dialog でも stale image でもなく **loading screen**（menu dump が menu frame を逃した）。fixture の identity 行が
  「要求した 18 ではなく presented counter は 36」と明示。served leg は menu が遅く（1フレーム7回の同期 copy readback）、
  要求を消費した present が world 起動後に着地していた。新しい `t36-menu.sh`（menu を保持し client tick 40 で dump）で
  **両 leg とも同じ grey main menu**（dominant colours 同一・590 vs 524 色）＝dialog は出ていない。
- **panorama が見えない真因**: 背景の差は ~5/255 しかなく、served copy の元が
  `CELTIUM_MIRROR_SOURCE id=52 source=announcement size=256x256 content=opaque=18763/65536 firstOpaque=0xff020101`
  ＝**28% 不透明・ほぼ黒**。理由は原典で確定: `EntityRenderer.updateCameraAndRender` は `GlStateManager.clear(256)`
  （**depth のみ**）を `currentScreen.drawScreen` の前に呼ぶ（`EntityRenderer.java:1171-1174`）が、
  我々は default pair の **colour を毎フレーム clear** していた。`GuiMainMenu.renderSkybox` は 6面を描いて copy するので、
  **前フレームの面の上に重ねていく feedback loop** が blur の前提——colour clear がそれを消していた。
- **修正（`GllessFrameTarget`）**: `clearOffTarget` は **depth を clear し colour は保持**、colour は pair の初回のみ clear、
  colour に `COLOR_ATTACHMENT_WRITE -> COLOR_ATTACHMENT_READ|WRITE` の hand-back barrier。chain pair は従来どおり全面 clear
  （entity outline silhouette は vanilla も clear する）。frozen pair `celtium-t36b 28dabb1dde958a7f` / `fixture-t36b 6dfaf4da1905a061`。
- **統制**: world 画像は t36 vs t32b で 4,383/921,600 px 差・**sky band rows 0-199 は 0/256,000**・nonBg が6桁一致（0.761182）。
  差の帯 rows 225-257 は near-horizon silhouette で、42 vs 47 world frames という **task-33 の build 状態**の機構。

## 0.183 **mod cohort 完了（JEI recipe・AppleSkin/MapWriter）と block overlay の texMat 実験**

- **acceptance（task-22）の mod cohort 完了**（jar `609eb69a9064df05`・fixture `f5b041ae841b2585`・AMD GL-less）:
  - `mod3b-jei-recipe`: `jeiRecipeOpened=True`（tick 41）・Crafting ページ `1/259`・グリッド/矢印/出力スロット/ページボタンが描画・
    `id4=1391/1391`・VUID 0・UNCALIBRATED（jei+keep の較正参照が無い＝正しい）。
  - `mod3-hud`（AppleSkin `4bbd37edecff0b42`＋MapWriter `e9a0551013ab785e`）: **PASSED**・distinct 17080・nonBg 0.952757・
    **MapWriter の円形ミニマップと座標表示が描画**。**AppleSkin はロードされるがこの scene では overlay が見えない**＝未検証と明記。
  - **JEI 場面の Celeritas 参照**（`mod3b-jei-ref`）との比較: sky 6.6%・horizon 40.5%・terrain centre 35.1%・chat 52.7%・
    **hotbar 0 px・hud strip 0 px**・whole 34.3%。**JEI 固有ではなく GUI 段の残差**（B05/build 状態の話と整合）。
- **計器の注意（acceptance 記録）**: `user-cfg-menu` の 90 秒メニューで `CELTIUM_SWAPPOINT calls=1..3` と
  `CELTIUM_PRESENT presented=2` しか出ないのに compositor capture は連続フレームで変化（logo/splash 領域 0.55%）＝
  **「止まった計器は止まった絵ではない」**。menu の present 経路がこの計器で覆われていない（task-15/frame-composite へ）。
- **block overlay の texMat 実験（Lead）**: `fmt=3` の7 group すべて **`texMat=none`**（`id3=5/5`・
  `ndc x[-0.07..0.07] y[-0.23..0.08]`）＝**texture matrix 仮説は死んだ**。UV は生値でバッチは全て正しい。
  → 次の実験は `mirrorPoisonFlat=12=FFFF00FF`（どの image をサンプルしているかを証明）。変わらなければ
  descriptor が mirror と別 image を指している＝block pipeline の descriptor path（`setsByTextureView` の view キー）。

## 0.182 **task-26 完了: per-texture sampler reach とゲーム自身の per-level mip（band 26.7→6.2）**

texture-mirror が両半分を実装（`samplerMaxLod` を global 定数から per-texture へ・`setSamplerReach` は sampler を作り直し
generation を上げるので descriptor cache が破棄済み sampler を名指ししない／registry が stub の per-level capture を歩いて
既存の per-level patch writer へ渡す・control `-Dceltium.vulkan.mirrorCapturedLevels=false`）:

| 参照（upstream 989ebf2e・同一 fixture/scene/pose/tick・band rows 200-320 = 153,600 px） | mean\|d\| | >8 |
|---|---|---|
| per-level content + reach 4（`t26`） | **6.207** | **5.86%** |
| generated chain + reach 1（`t26-nomips`＝変更前） | 26.728 | 50.00% |
| sky rows 0-205 | 0.442 | **0 of 262,400** |
| rows 320-720 / terrain centre | 0.006 / 0.010 | 0.01% / 0.02% |

`CELTIUM_MIRROR_SOURCE id=12 … levels=5 samplerMaxLod=4`・`CELTIUM_SAMPLER … maxLod=4.0`・census
`levels=[0:4 1:4 2:4 3:4 4:4] written=20 samplerReach=4 reachUnwritten=0 refusals=0`・PASSED・msgs 0。
**per-level の半分は「撤回された帯の主張」ではなく自身の対照で根拠づけられた**（26.728→6.207・terrain centre 1.256→0.010）。
**S23 note（mirrored 5.60% vs generated 0.16-0.28%）は global clamp 1.0 の下での測定として上書き**。
**混同の明示**: control は content と reach の2変数が同時に違う（per-level path だけが reach を上げるため）。
3つ目の leg（generated chain + reach を 4 に固定）は未実施。
**等価対照（既定 vs `terrainAtlasMaxLod=1`）は画素一致しない**: band の 8,474 px（5.5%）が rows 225-256 のみで差、
両 leg とも maxLod=4.0 なので差は reach ではなく sampler の他パラメータか build 状態 → **terrain sampler は残す**
（消せば測った 0.2 の残差を測っていない残差と交換することになる）。分離実験は記録に記載。

## 0.181 **task-30（B02/B03/B04）受入・task-33 完了と mip 内容主張の撤回**

- **B02（sky-draws）**: GL-less 経路は **fd 拡張を要求も enable もしない**（`interopPath = openGl != null`）。
  能力契約は `CELTIUM_DEVICE_CONTRACT … required=[api>=1.2, queue=graphics+transfer, VK_KHR_swapchain,
  color=R8G8B8A8_UNORM depth=D32_SFLOAT samples=1] fdInterop=false enabledDeviceExtensions=[VK_KHR_swapchain]
  limits=[maxPushConstantsSize=256 immediatePushBytes=208 …] advertised=[externalMemoryFd=false …]`。
  拡張を隠すスイッチ（`-Dceltium.vulkan.hideExternalFdExtensions=true`）で「持たない device」プロファイルを実機で行使し、
  **提示まで到達**。持つ device でも要求しない（`t30-final-plain`）。
- **B03**: textured/ITEM 両 pipeline の pool が**満杯時に成長**し、`immediateDescriptors=[pools growths sets
  keysCached failures hits misses]` を報告。`t30-count-keys`（JEI+inventory+entity+reload）で **72 key（旧 64 上限超）・
  failures=0**、`t30-growth-keys`（`descriptorPoolSets=1`）で **growths=72・failures=0**＝成長経路を実機で行使。
- **B04**: wait は **4状態**（completion-confirmed / TIMEOUT / DEVICE_LOST / unconfirmed）で、
  **解放するのは SUCCESS と DEVICE_LOST のみ**（spec §4.2.4 の pending/in-use 等価）。bootstrap の `vkQueueWaitIdle` も確認、
  `delete()` は `vkDeviceWaitIdle` を確認してから隔離物を解放。実測 `waitOutcomes=[completions=46 timeouts=0 lostDevices=0
  unconfirmed=0 …]`。**未検証（明記）**: wait 失敗の注入は未実施・interop 方向は code path のみ。
- **task-33 完了 + 主張の撤回（fog-path）**: 等価 build 状態で sampler の必要性が確定
  （maxLod 4: band 0.484%・frame 0.087%／mirror clamp 1: band **1.193%**・frame **0.205%**）。
  **「mip 内容が帯の残差原因」という以前の主張は撤回**——等価状態では rows 225-255 が3 leg で同一
  （std 2.34 / 参照 2.40・mean (113.0,110.8,99.8)）。tick 200 の縞は**未 build の遠 ring が近い低 LOD terrain を置いていた**ため。
  sampler の実効は terrain の edge（rows 216-224 が 174→43 px）。→ task-26 の mip 内容の半分は**根拠なし**として再定義。

## 0.180 **inventory 場面の 48% は「build 状態」だった（B05 帰属は撤回）——task-41 の訂正**

**この節の当初の結論（sky 17% 暗＝B05）は撤回する。** fog-path が等価 build 状態で pair を取り直した:
旧 pair は `cand3-inventory` **54 world frames / 12 sections / 8 MiB** に対し `ref3-inventory` **412 / 43 / 30 MiB** で
**同じ tick でも比較になっていなかった**。再測定（両 leg 43 listed sections・`captureCondition=sections`）:

| 領域 | tick 200（当初） | 等価 build 状態 |
|---|---|---|
| sky band (0,0,1280,120) | 0.857,0.842,0.825 / **83.4%** | **0.992,0.991,0.991 / 1.28%** |
| horizon | 0.991,0.968,0.961 / 53.0% | 1.019,1.000,0.988 / 16.48% |
| terrain centre | 1.023,1.014,0.985 / 35.9% | 1.029,1.016,0.987 / 27.74% |
| hotbar strip | 0.975,0.996,0.993 / 0.0% | 1.000,1.000,1.000 / **0.00%** |
| whole frame | 0.970,0.968,0.949 / **48.0%** | 1.016,1.005,0.990 / **10.88%** |

**lit ITEM の率は両期で同じ**（等価状態 55.5 batch/frame・当初 50.8/frame）——`acceptedByFormat` は
**run 累積**なので「2744 が 54 frame で」という見かけが特別に見えただけ。**同じ率で暗くならない**ため
batches は原因ではない。**B05 の機構自体はコード上実在**（`lightParams != null` の group は `celtium$writeFogPush` を
skip し、lit ITEM shader に fog block が無い）が、**この絵の原因としての根拠は無くなった**（task-29 の受入は
lit+fog が同時に要る場面で取る）。**一般則（plan に残す）**: 画像比較は **等価 build 状態**で取り、
`acceptedByFormat` 等の累積計器は**フレーム数で割って**比べる。
等価状態で残る差は「画面を開いていること」由来（terrain centre 27.74%・chat 17.30%・horizon 16.48%・frame 10.88%）で、
画面なしの同一 scene は 2.65% なので **dim overlay / panel / JEI の被覆**が次の問い（gui-stage）。

**撤回した当初の測定（経緯として残す）**: 同一 tick 200 の pair で「sky band 比 0.857・83.4% 差、format 4 が 0→2744」を
観測し、それを B05 の根拠とした（`task-38`）。**この pair は build 状態が違っていた**ため撤回。
当時の副産物として有効なのは「画面なしの同一 scene では sky が 153,600 中 0 画素差」という統制だけで、
それは等価 build 状態の再測定でも sky 1.28% として残っている。
## 0.179 **地平線帯は「build 状態を揃える」ことで閉じた（task-33）／fixture jar の空コピーという罠**

- **task-33（fog-path）の受入**: `captureCondition=sections captureSections=41 captureMinWorldFrames=295` で
  両 leg が同じ listed sections に到達（候補 **41**・819 world frames／参照 43・405 frames）。
  その状態での band rows 200-320 は **mean 0.403・>8 が 744/153,600 = 0.484%**（以前は 59%）。
  sky rows 0-199 は **0/254,720**、terrain rows 320-720 は 0.012%、frame 全体 0.087%。
  **22 vs 32・34 vs 41 は build throughput の差であり、culling 欠陥ではなかった**——我々は同じ section 数に達するのに
  819 frame を要した（tick 200 では 106-162 frame・34 sections）。**帯の受入はこれで閉じた**。
  残差 0.48% は frame の他領域と同じ桁で、task-26 の mip 内容が候補（`t33c-atlas1` の対照で sampler の去就を決める）。
- **罠（私の失敗・記録）**: 私の `lead-1920`/`lead-instworld` が `MISSING_REPORT` になったのは、
  **run の mods にコピーされた fixture jar が 22 バイトの空 zip（`PK\x05\x06`）だった**ため。
  build が出力ファイルを作った直後に `cp` したためで、FML は正しく "non-mod file" として拒否していた
  （Cleanroom の discovery は `Discovered mod ids: scalar, celeritas, fugue` の3つだけ）。
  **build 中に jar をコピーしない**（`scripts/build.sh` の完了を待つ）。以後は凍結済み fixture を使う。
  修正後、**instance のライブラリだけで（`--library` なし）world まで走って PASSED**・`pictureFrameDelta_world=0`
  ——ユーザー構成（instance の mods にある celtium jar＋instance のライブラリ）が world まで成立することを実測。
- **Cleanroom 固有の罠（確定・訂正済み）**: `GluProjectionMixin` の target について、
  weather-water の静的 scan（`org/lwjgl/util/glu/Project.class` はどの jar にも無い）は**誤りだった**。
  proj-channel が runtime 証拠で確定: CleanMix のログは
  `@Mixin target ... was not found`（1st pass）→ `Mixing core.GluProjectionMixin … into org.lwjgl.util.glu.Project`
  → `[CleanMix/Audit]: APPLY … -> org.lwjgl.util.glu.Project` の3行が**正常なパターン**（`LwjglxDisplayMixin` も同じ）。
  `-Dmixin.debug.export=true` の exported class に **mixin の field と注入 handler** が入っており、
  `EntityRenderer.class` の bytecode は **6箇所で `org/lwjgl/util/glu/Project.gluPerspective` を呼ぶ**。
  計器 `CELTIUM_GLUPERSPECTIVE` も実値（far=256.0/181.019）で発火し、task-1 の A/B は 727 行 vs 0 行。
  **結論: retarget も削除もしない**。runtime 名は `org.lwjgl.util.glu.Project`（静的にはどの jar にも無い）。
  **教訓**: 「ログの not found」だけで死んでいると結論しない——CleanMix の APPLY 行と exported class が判定材料。
- **spec の裏づけ（web）**: viewport は framebuffer 外の fragment を生成しない（rasterization 章）ため
  1281x720 を 1280 target に適用して可視列を描くのが正しい。scissor は offset>=0 の VUID があるため
  両方を target との交差へ clip する実装が正しい（vkCmdSetScissor の VUID 00595-00597）。

## 0.178 **この時間帯の実測（task-14 の結論・task-33 の等価比較・Intel 3場面・mod cohort）**

- **メニューの「ズレ」は解消（ユーザー解像度で実証）**: frame-composite の `resizeworld1080` が
  compositor 出力を **1920x1080** へ変え、`CELTIUM_WINDOW_SIZE_CHANGED tick=121 from=1280x720 to=1920x1080`・
  `resizeObservedAtTick=127`・report に **「the surface extent no longer matches the 1280x720 swapchain
  (window framebuffer is 1920x1080), rebuilding it」**。提示 dump は **1920x1080（2,073,600 px）**で
  **私が開いて確認＝sky/horizon/terrain が画像全面を占め、ズレも黒帯も無い**
  （`.celtium-work/runtime/lc-resizeworld1080/screenshots/celtium-presented-world-run-…-kept.png`・sha256 `eb6ac395a28d9312…`）。
  verdict が UNCALIBRATED なのは較正キーが解像度を含むため（正直な表示）。
  **注意**: 私の `lead-1920` は `--validate` を付け忘れて fixture が有効化されておらず、何も行使していなかった（私の誤り）。
  harness に `--window-size WxH` を追加（既定 1280x720）。
- **world-switch の scope も検証**: `wswitch4` で `pictureFrameDelta_world=1`＋whole-run −24 を併記・PASSED・
  `presentedCounterRestarts=1`・`groupsDiscardedAtWorldTeardown=1`（B10 の world epoch が実際に発火）。
- **task-14（pipelines）の結論: group drop ではない**。同一 jar・同一 flags・1フラグ差:
  `-Dceltium.vulkan.positionNormalDepthTest=false`（POSITION_NORMAL pipeline のみ depth off）で
  box 窓（x520-600, y400-480）の near-white が **90 → 3,936 画素**。**フラグメントが depth test で棄却されている**。
  `acceptedByFormat[10]=62`＝`drawnByFormat[10]=62`・`immediateGroupsDiscarded=0`・`postAcceptDroppedGroups=0`、
  `CELTIUM_DRAW_SITE` は全件 `site=immediate-only-window / passOpen=true / onMain=true`。
  保持系3計器（`keptForOtherTargets`/`deferredNextFrame`/`refusedWindowBudget`）は**増加siteが存在しない**（summary に出して可視化）。
  次の分離: format 2/6/7 の group bounds を出して「terrain と box の間に depth を書く全画面quad」を名指しするか、
  1画素 depth readback。
- **task-33（fog-path）の等価比較**: 両 leg PASSED・`pictureFrameDelta_world=0`・条件 `built-stable`。
  しかし **我々 listedSections=22（113 world frames・P1=073）／参照 32（295 frames・P1=112）** で
  **どちらも queue が残ったまま「安定」と判定**していた（30 frame の窓では偽の plateau）。
  → `captureCondition=sections captureSections=41 captureMinWorldFrames=295` で再測定中。
  **22 vs 32 を culling 欠陥と呼ばない**。
- **Intel（sky-draws・task-23 完了）**: entity / inventory / videoSettings の3場面とも描画（entity 0.75% over 8・
  hotbar 0.00%・videoSettings mean 0.978）。**Intel の upstream 参照自体が videoSettings の床を満たさない**
  （bands 229<250・black 0.068>0.05・panel 125<4000）＝較正の vendor 移植性が問い。
- **mod cohort（acceptance）**: `mod3-jei` が **JEI を読み込み**（43 JEI 行・`jeiScreenOpenedAtTick=32`）・
  `pictureFrameDelta_world=0`・UNCALIBRATED（保存参照が `screen=none` のため）。**recipe 版が継続中**。
- **inventory の 48% は variant 差ではない**（acceptance が `sceneCalibrationKey` 完全一致を確認）→ gui-stage へ。
  hotbar strip は **0 px** で、sky band 83.4%・chat block 64.7% と段ごとに違う＝「GUI段が全体的に違う」ではない。
- **雲（task-13・weather-water）**: stub の decode が `glDrawArrays` の **205/256 call を描画**（752,088 頂点。以前 23/17,992）。
  cloud band は参照の mean に対し **1.7/1.8/0.4**。
- **panorama の残り**: stub が `glCopyTexImage2D`/`glCopyTexSubImage2D` を**名前つき拒否**として登録
  （`CELTIUM_TEX_COPY`・`servedFrom=refused-no-owned-image-readback`）。**read 側は frame-composite の task-36**
  （owned colour image の矩形読み出し）。foreign FBO は名前つき拒否のまま。
- **instance のライブラリだけで world まで走るか**を検証中（`lead-instworld`・`--library` なし）。

## 0.177 **ユーザーの「真っ白な窓」の真因＝instance に lwjgl-vulkan が無い／ズレの修正／監査タスクの起動**

### ユーザー構成で何が起きていたか（確定）

**ユーザーの Prism instance の `patches/org.lwjgl3.json` は `org.lwjgl.vulkan` を1つも持っていない**
（lwjgl / glfw / opengl / stb / tinyfd / jemalloc / openal のみ）。だから **`createForWindow` が
`NoClassDefFoundError: org/lwjgl/vulkan/VkDebugUtilsMessengerCallbackEXTI` で落ち、renderer が作られず、
窓は真っ白のまま音楽だけ鳴る**——これが「ロード画面もメニュー画面もない」の正体。
**harness は `--library lwjgl-vulkan-3.4.1.jar` を足していたので再現しなかった**（＝harness と実起動の差）。
**私が追加した cause chain 表示**（`CELTIUM_WINDOW_RENDERER failed: … <- NoClassDefFoundError <- ClassNotFoundException`）が
これを名指しした。instance へ `org.lwjgl:lwjgl-vulkan:3.4.1` を追加（実 SHA-1 `d0601fdc38890755e5638048c14cc0f7b990964b`）。
**注意**: 追加時に私が仮の SHA-1 を書いたため harness が ~7分 全 run を拒否した（`Library does not match the profile SHA-1`）。
実 SHA-1 へ修正済みで `Locked 101 library jars and 3 mods`。**配布条件として「lwjgl-vulkan が必要」を docs に書く必要がある**。

### メニューの「ズレ」（ユーザー報告）と修正

`ensureSwapchain` の surface extent 検査が **`frameId` gate**（world frame 境界でしか進まない）だったため、
**menu 中の resize で swapchain が最初の present のサイズのまま**残り、左下アンカー・無スケールの blit が
frame の左下だけを出していた。**present ごとに検査**する形へ修正（jar `8f3b34b6`→以降）。
weather-water が viewport/clip 修正入り jar `5da2e2b6` の **headless menu capture** で
**メニューが窓を満たす**（ロゴ中央・footer 左下・切れ無し）ことを確認（`.celtium-work/ww-menu-run4-caps/compositor-0008.png`
sha256 `f3c476d0e66ad1c9…`）。**panorama 背景はまだ灰色**——`GuiMainMenu` が 6面を描いて `glCopyTexSubImage2D` で
コピーする経路が stub に mirror 無し（次の段）。

### その他の確定（この時間帯）

- **frame identity ゲートは閉じた**: presented カウンタを world 採用時にリセット → **`pictureFrameDelta_world=0`**（`ww12-gl-terrain`・PASSED）。
  acceptance の `cand3-terrain` も **PASSED**（distinct 2054・nonBg 0.759463・sky band 0/153600 差）。
  **world-switch の −37 は fixture の scope 問題**として task-28 が修正中。
- **音**: harness の全 run で `ALSOFT_DRIVERS=null`（従来は `--headless` のみ）。ユーザーが「音楽だけ鳴ってうるさい」と報告したため。
- **task-14（pipelines）**: 受理・close・rasterize 済みの group は **draw call まで到達している**
  （`acceptedByFormat[10]=62 / drawnByFormat[10]=62`・`site=immediate-only-window`・`passOpen=true`）。
  queue drop 系の計器（`keptForOtherTargets`/`deferredNextFrame`/`refusedWindowBudget`）は**増加siteが存在しない**。
  深さ拒否の実験（`positionNormalDepthTest=false`）で「depth で落ちている」か「image に届いていない」かを分離中。
- **task-16（fog-path）**: 帯の残差は culling ではなく **build 状態の差**（同一 tick で我々 34 sections／参照 483 world frames で 41 sections・
  23 MiB vs 29 MiB）。**距離を広げない**。→ **task-33**: 既定は `tick` のまま、`captureCondition=built` を opt-in にして
  **同じ build 状態で比較**する（stored 参照を壊さないため）。
- **task-21（entity-models）完了**: per-family 0.97〜1.04・sky は sky-only 列で **0/256,000**（scene 内の mob 頭で 488/256,000、全て x 461..662）。
  hurt flash は **検出も不能**（stub が `glGetTexEnvi`/`glGetTexEnvfv` を登録せず 0 を返す＋lit ITEM fragment に INTERPOLATE 分岐が無い）→
  stub は weather-water・shader は task-29。lightmap は fixture 制約で未行使 → **task-35**。
  spider 赤目は task-27 で修正済み（`blend=1(1,1)`）。
- **task-23（sky-draws）完了**: Intel で entity/inventory/videoSettings の3場面とも描画を確認（entity 0.75% over 8・
  inventory hotbar 0.00%・videoSettings mean 0.978）。**Intel の未受入**: gate-accepted  verdict・dump identity（旧 jar の +20 は reset 前）・
  videoSettings の床が vendor 移植でない（**Intel の upstream 参照自体が床を満たさない**: bands 229<250・black 0.068>0.05・
  panel 125<4000）→ 較正の見直し候補。

## 0.176 **ユーザー監査（15:54）のB01〜B12をownerへ割当て、通常起動のズレを修正**

**監査**: `docs/history/2026-10-11-full-audit-1554.md`（証拠は `.celtium-work/audits/2026-10-11-1554/`・**公開gitへは入れない**）。
**実装は止めず、既存ownerへルーティング**した（新規タスクは既存のtask番号体系に統合）。

| 監査 | 内容 | 担当 |
|---|---|---|
| **B01+B05** | immediate が **208B push を無条件要求**（Vulkan の保証下限は1.3まで128B）＋ **fog と lighting が同じ 128..207B** を書く＝lit group に fog が無い | **task-29 → pipelines** |
| **B02+B03+B04** | GL-less でも **external_memory_fd / external_semaphore_fd を要求・enable**／textured descriptor pool が **64固定・追加key最大63**／**submit後 wait の非TIMEOUT失敗で finally が GPU 所有資源を解放**（DEVICE_LOST は SUCCESS 相当なので同列にしない） | **task-30 → sky-draws** |
| **B06** | panorama/subviewport と loading・退出・再入場の受入 | task-15（weather-water）＋task-28（frame-composite） |
| **B07** | 2引数 blendFunc の mirror 欠落 | **完了**（task-27・`blend=1(1,1)`・GUI段は非退行） |
| **B08** | video settings は「値が変わった」までで **Apply/保存/再開が未証明**（操作した画面と撮った画面も別） | **task-31 → gui-stage** |
| **B09** | client-pointer 検査は Linux 限定・`/proc/self/maps` cache は所有/寿命の保証ではない・64回更新上限 | weather-water（stub）へ通達 |
| **B10** | full teardown の所有が全cache・context・queued group を覆わない／queued group に world epoch が無い | **task-32 → frame-composite** |
| **B11** | summary の format 集計が id9 止まり・drop は8要素・descriptor 計器が block/particle のみ | task-25 へ統合（pipelines） |
| **B12** | docs が過去の JEI/AppleSkin/MapWriter run を全否定／配布物の説明と実jarの差 | **task-31 → gui-stage** |

**通常起動の「ズレ」を修正（Lead・ユーザー報告）**: メニューが**右にズレてロゴと右端が切れ、フルスクリーンでも直らない**。
原因は **`ensureSwapchain` の surface extent 検査が `frameId` gate だった**こと——`frameId` は world frame 境界でしか進まないので、
**menu 中に起きた resize**（実起動は窓を小さく作ってから画面サイズへ広げる。ユーザーの log は 854x480 → 1920x1080）で
swapchain が最初の present のサイズのまま残り、present blit は**左下アンカー・無スケール**なので
**frame の左下だけが小さい swapchain に切られて表示**されていた（compositor が窓へ拡大）。
修正: gate を外し **present ごとに surface capability を問い合わせる**（`lastSwapchainExtentCheck` は削除）。
jar `8f3b34b68a1b2b80ad74`・実セッションで検証中。

**task-18（GL-less post chain）の中間結果（proj-channel）**: chain が止まっていたのは readback ではなく
**preflight の順序**（`unsuppliedInput` が `adoptChainFramebuffers` より先）。修正後は
**`postChains=18 postPasses=72 postTargets=1 postAdopted=1`・VUID 0** で GL-less でも chain が native 実行。
**ただし可視 glow は未完**: silhouette image に入るのは 19 group すべて `fmt=4 texture=12`（item entity 1体/フレーム）で、
mob の outline replay が届かず `CELTIUM_MODEL_MISSING list=125 texture=124` が出る（model 経路側の次の測定）。

## 0.175 **このラウンドの割当（10人・task-11〜22）と、GL-less のクリティカルパス**

ユーザー指示「quota復活。チーム10人。はやく GL-less で完璧に動作するその日を」で開始したラウンド。
**10メンバーは全員既存（spawn 済み・上限10）なので、新規 spawn ではなく task の割当で起こした。**

| task | 担当 | 内容 |
|---|---|---|
| 11 | sky-draws | Intel UHD 630 leg（継続） |
| 13 | weather-water | Forge の雲＝stub の 24バイト decode（task-15 の後） |
| 14 | pipelines | format 2/6/7 同居時の group-drop（継続） |
| 15 | weather-water | **クリティカルパス** — client-memory は decode 済み。残りは「world 前に renderer が無い」＝window 生成時に install |
| 16 | fog-path | 地平線直下の terrain 帯（継続） |
| 17 | texture-mirror | block 破壊 overlay＝BLOCK(id3) の初行使（継続） |
| 18 | proj-channel | **GL-less の post chain と entity outline**（seeded path は GL readback 依存・`postChains=0`・可視 glow 未受入） |
| 19 | frame-composite | **wedge の残存**（細い筋。format-8 帰属は撤回済みで原因未命名） |
| 20 | gui-stage | **完成条件 (e)** — コードが要求する capability と docs/配布物の一致（Java・最低VK・mod範囲・license・性能・未検証GPU/OS） |
| 21 | entity-models | モデルの残3件（spider の赤目 layer・hurt flash・lightmap） |
| 22 | acceptance | **GL-less のユーザー構成そのものでの独立受入**（task-15 完了後に assign・blocked_by task-15） |

### task-15 の設計（私の決定・レビュー結果）

**決定**: renderer を world 生成時ではなく **window 生成時**に install し、world renderer が**その同一インスタンスを adopt**する
（`VulkanTerrainGpuBackend.createForWindow` + `celtium$adoptTerrainState`）。menu 専用の第2 renderer は swapchain の二重所有なので採らない。

**レビューで見つけた2つの欠陥（weather-water へ送付済み・実装待ち）**:
1. **world 破棄が window の device を消す**: `RenderSectionManager.destroy()`（`RenderSectionManager.java:923`）が
   `terrainBackend.delete()` を呼び、`SimpleWorldRenderer.unloadWorld()`（`:67`）と `initRenderer()`（`:247`）がそれを呼ぶ。
   adopt 済みインスタンスだと title へ戻った時点で device/frame target/swapchain が消え、`TerrainGpuBackends.install(null)` が走る。
   **`windowOwned` を別フラグにし、`delete()` は world 資源の解放と detach だけを行う**（device は残す）。
2. **再武装**: 最初の試行が失敗すると latch 済みのフラグでセッション中ずっと menu が空白になる。成功時のみ latch する。

**計測の規律**: 赤いツリーは誰の build も止める。`VulkanTerrainGpuBackend.java` は同時に複数担当が触るため、
**最小の編集＋書く前の再読**を維持する（このラウンドでも `final` 4フィールドの compile エラーで全員が止まった）。

### このラウンドの測定で確定した事実

- **task-15 の第1段は動いた**: window 生成時に renderer が作られ（`CELTIUM_WINDOW_RENDERER created=true attempt=1 ticks=0`）、
  swapchain は提示している。**そして 16:18 にメニューが描かれた**（compositor 出力の capture を私が確認・上の表）。
  **残るのは panorama 背景**: stub の `glDrawArrays` は 1,048,576 件すべて decode されたうえで、
  **256x256 の viewport を持つ batch が `target: the viewport is 256x256, the target is 1280x720` で拒否**される。
  **修正は「拒否ではなく適用」**（GL の viewport は drawable の部分矩形）——`CELTIUM_DRAWARRAY_REFUSAL_CONTEXT` が
  `drawFbo=0`（＝window）を示したので **FBO 説は否定**。uploader 経路は同じ run で 1.3M batch を受理しており、
  2経路の食い違いはこの viewport 拒否が原因。
- **task-15 の user-launch にはまだ絵が無い**: `scripts/compositor_capture.py --output` は **grim の output 名**であり
  JSON path ではない（`--output` に JSON path を渡すと全 capture が `unknown output` で失敗＝PNG 0枚）。**screenshot の無い run は証拠ではない**。
- **spider の赤目 layer の原因は model ではなく blend state**: `VulkanTerrainGpuBackend.blendFunc(int,int)`（~:3021）が
  **mirror を書かない**ため 2引数 `blendFunc(ONE,ONE)` が無視され、4引数の最後の値 `(6,7)` が使われる。
  実測 `blend=1(6,7)`・目の g/b が参照の 0.64〜0.66（r は 0.99）。→ task-27（gui-stage）。
- **配布物の欠陥（task-24）**: 同梱 jar の `META-INF/LICENSE` は **JOML の MIT**（JOML class は 0 個・本体は LGPL-3）、
  `xyz/wagyourtail/jvmdg` 13 class は license 無し、`mcmod.info` の説明と `Celtium-Stage: backend-boundary` は古い、
  `verify_forge_jar.py` は license を見ないのに `verified: true`。
- **GL-less の性能は測定済み**（`docs/performance-2026-10-11.md` §4.1: p50 3.94 ms / 253.8 fps・§7 で 2.83〜3.94 ms を再現）。
  「未測定」と書かない（gui-stage の訂正を採用）。
- **回帰（受入を全員止める・原因確定）**: すべての run が `FAILED_PRESENTED_IMAGE`＋
  `presentedImageAssertions = FAILED: ... frame the renderer presented at 146, which is 18 away from the 128 world frames`
  で落ちる（`t16-atlas4`/`t16-atlas1`/`t19-bounds`/`t19-noimm`/`t17b-break-on` が同一）。
  **原因は task-15**: これまで renderer は world 生成時にしか無かったので presented frame 番号は world frame と一致していたが、
  window 生成時に renderer が出来たことで**menu/loading の presented が番号に混ざる**（差 18）。
  **assertion は正しく、壊れたのは番号**——fixture 側を緩めず、**world 採用時に presented カウンタをリセット**する（weather-water）。
- **地平線帯の mip**: 原因の一部は確定・修正済み（atlas sampler の maxLod 1→4・fog-path）。
  band rows 200-320 の mean|d| **29.187 → 20.996**、>8 65.82% → 59.08%、rows 240-250 の空間 std 58.5 → 0.0-2.2（参照 0.1-1.9）、
  他段は不変（sky rows 0-205 は 0/256,000・terrain centre 2.65%）。**残り2因**: (i) **mirror の mip 内容**
  （GL は per-sprite の level 4・我々は全体 blit の平均 → rows 240-250 が (102.0,63.0,58.0) vs 参照 (112.9,110.7,99.7)・task-26）、
  (ii) **section 数が 34 vs 参照 41**（同一 fixture/camera/tick・task-16 継続）。

## 0.174 **video settings の機能受入（ユーザー要件の半分が閉じた）**

fixture の機能検証が**成立**:

```
videoSettingsFunctionScreen    = org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen
videoSettingsFunctionControls  = 8
videoSettingsFunctionControl   = TickBoxControlElement
videoSettingsFunctionBefore    = false
videoSettingsFunctionAfter     = true
videoSettingsFunctionChanged   = true
videoSettingsFunction          = the screen's own control changed a setting after 4 press(es)
```

**ゲーム自身のクリック経路で画面が開き、その画面自身のコントロールが自身のハンドラで押され、所属する option が変化する。**
4回押したのは、最初に到達したコントロールがトグルではなかったため——**「値が動くまで押す」形にした理由**。

**途中で分かった3点（記録に値する）**:
1. この runtime が表示する画面は **mod のもの**（`CeleritasVideoOptionsScreen`）で、比較対象の vanilla `GuiVideoSettings` は **"Done" 1つだけ**。
   vanilla 画面に向けた機能テストは**押すものが無い**。
2. ウィジェットは **`buttonList` ではなく controller の frame** にある——button list は探す場所として誤り。
3. 押下は **widget 自身に直接**問い合わせる必要がある。画面は `func_146274_d` で**グローバルなマウス状態**を読むため、
   `mouseClicked` に渡す座標は届かない。証拠は **controller の pending フラグではなく option の値**（フラグは render 時に更新される）。

**これで video settings 要件の機能側が閉じた。** 画側は既に panel rect で参照と mean 1.406。

## 0.173 **client-memory decode 成功／メニューは次の段で失敗（backend が無い）**

担当 weather-water がユーザーの構成（fixture なし・validation client なし・GL-less）で測定:

```
CELTIUM_DRAWARRAYS glDrawArraysCalls=524288 drawn=0 vertices=0 refused=524288
  clientMemoryDecoded=524288 readableRanges=404 mappingFailure=none
  refusedByReason=[no terrain backend is installed=524288]
CELTIUM_CLIENT_ARRAY stride=24 format=2 otherArrays=GL_COLOR_ARRAY, GL_TEXTURE_COORD_ARRAY
CELTIUM_CLIENT_ARRAY stride=16 format=1 otherArrays=GL_COLOR_ARRAY
CELTIUM_CLIENT_ARRAY stride=20 format=6 otherArrays=GL_TEXTURE_COORD_ARRAY
```

**全 client-memory 描画が decode された**（524,288件・stride の全数どおり id2/1/6 に着地）、
**安全ガードは 404 の readable mapping に対して全件受理・fault 0**。**しかし1つも描かれない**——
**`TerrainGpuBackends.current()` がメニュー時点で null**。present 経路も同じ gate なので何も提示されない。
**decoder 半分は修正済み。残る半分は「world が存在する前に renderer が無い」こと。**

**決定（私）**: **renderer を world 生成時ではなく window 生成時に install** し、backend が既に持つ
**no-terrain-pass UI render pass** にメニューを描かせる。**menu 専用の別 renderer は「新抽象と旧経路の併存」**であり、
swapchain の所有者も二重になるため採らない。担当の scope を `MinecraftMixin` のライフサイクル／present gate と
backend の構築・ライフサイクルへ拡張（**group queue は pipelines・terrain pipeline は fog-path の所有**）。

**設計として残す価値のあるガード**: pointer は process address として読むが、**`pointer + count×stride` が
`/proc/self/maps` の readable mapping 内にあるときだけ**。外れれば名前つき拒否、maps が読めなければ**読まずに拒否**。
**起動時クラッシュは空白メニューより悪い**——その取引を明示している。

## 0.172 **残る欠陥の一覧（担当つき）と stride の全数**

チームの task-1〜10 はほぼ完了。**残るものと担当**:

| task | 担当 | 内容（測定が名指ししたもの） |
|---|---|---|
| 11 | sky-draws | **Intel UHD 630 leg**。これまでの測定は全て AMD RX 6400。depth format chooser は両社が 24-bit で食い違うために存在する＝形式的な追加ではない |
| 13 | weather-water | **Forge の雲**が stub 経由で描かれ、頂点が 24バイト（decoder は 12 のみ）で拒否 |
| 14 | pipelines | **受理・close・rasterize 実証済みの group が、format 2/6/7 が同居すると frame から落ちる**（6 leg の bisect で測定）。format 表ではなく flush/window/deferral 経路 |
| 15 | weather-water | **client-memory 配列**——ユーザーが見た「中身の無いウィンドウ」の正体。メニュー/ローディング画面の全描画が process memory の頂点 |
| 16 | fog-path | **地平線直下の terrain 帯**。3人が「空でも fog でも GUI でもない」と測定済み——参照は平坦な面、私たちは縞、参照の端が約3行遠い |

**task-13/15 が共有する stride の全数**（全て `size=3 type=0x1406`＝float3 位置）:

```
 99x stride=12  POSITION(id7)         既に decode 済み（sky dome）
 12x stride=16  POSITION_COLOR(id1)   client-memory ケース（task-15）
  8x stride=20  POSITION_TEX(id6)
101x stride=24  POSITION_TEX_COLOR(id2)  Forge の雲（task-13）
 42x stride=28  ITEM/BLOCK/PARTICLE(4/3/5)  stride だけでは区別不能——有効な client array が判別子
```

## 0.171 **現行ツリーの frame 差は client-memory 欠落が支配している（task-15 の重要性の実証）**

committed tree で新たに pair を取った（GL-less entity scene vs upstream Celeritas・同一 scene/pose/tick）:

| 領域 | mean | >8 の画素 |
|---|---|---|
| sky rows 0-168 | 26.124 | 57234 / 215040 |
| horizon 168-240 | 49.307 | 32025 / 92160 |
| terrain centre | 6.399 | 12940 / 217600 |
| hud rows 640-720 | 52.696 | 51841 / 102400 |
| frame 全体 | 25.353 | 239009 / 921600 |

**色が理由を語る**: 参照の sky band の最頻色は **(222,222,222) 12.9%・680色**——それは空ではなく**チュートリアル toast**。
参照の HUD strip は **305色**、候補は **7色**。つまり GL-less の frame は **toast と HUD の大半を欠いて**おり、
それは **client-memory クラス**——**ユーザーの起動が真っ黒なのと同じ根因**が、受入 frame の中にも見えている。

→ この pair は**新しい退行の証拠ではなく、画の受入でもない**。**task-15 の重要性の最良の実証**。
以前の「sky/HUD が 0 画素差」は、その帯に dome と terrain しか無い scene で取ったものだった。

**plan に残すべき一般則**: **参照側のその段階に、候補が一度も描かない内容が入っていれば、「0 画素差」でも正しくない**。
分離する対照は**参照自身の色数**（sky 680 vs 63、HUD 305 vs 7）であり、**最初から引用すべきだった計器**。

## 0.170 **video settings の機能受入——確立した半分と、まだ必要な半分**

ユーザーの指摘どおり video settings は**機能**の要件なので、fixture に機能検証を組み込んで追い込んだ。

### 確立したこと

- **ゲーム自身のクリック経路で開く**: fixture が `GuiScreen.mouseClicked` 経由で Video Settings ボタンを押し、
  `guiClickResult = clicked button id 101 at 133,116 through GuiScreen.mouseClicked` ✓、`guiScreenOpenedVia` が開いた画面を記録。
- その画面は **`org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen`**——この runtime の video settings UI は**mod の画面**であり、
  比較対象の vanilla `GuiVideoSettings` は **"Done" 1つだけ**（実測）。だから vanilla 画面に向けた機能テストは押すものが無かった。
- mod 画面のウィジェットは `buttonList` ではなく **controller** にある: controller の frame が **8つの control element**（`CyclingControlElement` を含む）
  ＋ apply/close/undo ボタンを持つ ✓。**画の受入は既に mean 1.4 で参照と一致**。

### まだ必要なこと

**コントロールの押下が成立しない**: 8つ全てについて各 control 自身の `Dim2i` 中心で press+release を送ったが、
controller の `hasPendingChanges` は false のまま。**理由はクラスに出ている**: mod 画面は `func_146274_d`（handleMouseInput）を
override しており、**呼び出し側が渡す座標ではなく GLFW のグローバルなマウス状態を読む**ため、
座標付きの合成クリックは widget に届かない。次の一手は **GLFW レベルでカーソルを動かしてボタンを押す**こと（fixture 側の変更）。
計器（`videoSettingsFunctionScreen/Controls/Control/Pressed/ControlResponded/Applied`）は既にレポートに出ているので、
次の試行は**測定から始められる**。

**画面が壊れているわけではない**: ゲーム自身の経路で開き、描画され、コントロールも存在する。
**未証明なのは「押すと設定が変わる」こと**であり、それがユーザーの問いの半分。

## 0.169 **ユーザーの起動は「真っ黒なウィンドウ」——client-memory 頂点配列が全拒否**

ユーザーのインスタンスは `instance.cfg` に **`-Dceltium.vulkan.glless=true`** を持つ（＝手動起動は GL-less・fixture なし・validation client なし）。
そのログは `CELTIUM_DRAWARRAYS ... refused=[no GL_ARRAY_BUFFER is bound (the pointer would be client memory, which the stub does not mirror)]` を
**1024 → 1,048,576** まで積み上げ、`CELTIUM_SWAPPOINT` は 2700 に達する。

私がユーザーの **Wayland セッションで再現**した: swapchain は生きており image 0..3 を acquire/present している
＝**ウィンドウは存在しフレームを受け取っている**。そして**メインメニューとローディング画面の描画が全て拒否**されている
——頂点が**クライアントメモリ**にあり、stub が mirror していないため。**ユーザーが見たのは「中身の無いウィンドウ」**で、それは「ウィンドウが出ない」と読める。

**この発見の意味**: fixture のシーンは全て VBO 経由の batch なので、**プレイヤーが最初に出会う描画クラスが全ての受入 run から見えていなかった**。
task-15 として測定ごと起票し、push の最優先に置いた——**人が使えるレンダラか、fixture だけが使えるレンダラかの差**。

## 0.168 **video settings は動作している（受入・ユーザー明示要件）**

ユーザーが完成条件に名指しした video settings を、私が GL-less（fixture の `videoSettings` scene）と
**upstream Celeritas 参照**（同一 scene/pose/stage）で対にして測った:

| 領域 | mean | >8 の画素 |
|---|---|---|
| frame 全体 | 3.776 | 114666 / 921600（12.44%） |
| **panel rect** | **1.406** | 8898 / 211200（**4.21%**） |
| title row | 7.100 | 6138 / 26400（23.25%） |

panel の平均色: GL-less **(71.1,70.4,69.6)** vs 参照 **(72.5,71.4,70.4)**——ほぼ一致。

**画面は描かれ、panel は正しい形と色で出ている。** 残差は title row と panel 外の 12%。
verdict は `presentedVerticalBands=212 is below the scene floor of 250` で FAILED のまま——
**この床は古い bridge run で測られたもので、追うべき欠陥ではなく再較正すべき対象**（「内容は正しいのにゲートが落ちる」という、plan が逆向きに警告しているのと同じ型）。

**再取得する人への罠**: 参照の `screenshotAtTick=60` の画は **world**（fixture は tick 201 で GuiOptions を開く＝world capture の 200 の後）。
比較すべき参照画は**参照自身の GUI 段階 capture**（`celtium-validation.png`）であり、tick 60 の screenshot ではない。
私の最初の比較はそれを混同して 99.9% 差と報告した——**別の段階を比べていた**。

## 0.167 **条件 (b) の構造的証拠と、GL-less における `glBodyCompleted` の意味**

完了条件 (b)「GL context を生成せず、通常 frame の GL call を必要としない実行」の**構造的な半分**は、run ログの次の3行で示される（再導出せず引用する）:

```
CELTIUM_GLLESS window hinted GLFW_NO_API
CELTIUM_GLLESS stub GL provider installed
CELTIUM_GLLESS splash progress skipped (no shared GL context)
```

＝**GL context は存在せず**、GL の全エントリは**私たち自身の stub** に解決し、ゲームの splash 経路もそれを認識している。
この構成での「GL call」は**ドライバではなく私たちの関数テーブルへの呼び出し**であり、present が swapchain 画像を直接書く理由でもある。

**誤読してはいけない値**: `glBodyCompleted`（uploader の `celtium$glUploads`）は GL body に到達した batch 数だが、
**GL-less ではその body が stub** なので、非ゼロは**誰も描かない batch**（被覆の穴）であって **GL 依存の証拠ではない**。
最新 run: `offers=2000 accepted=1771 glBodyCompleted=275` = 1771 が native・275 が stub 行き。
**この数が残る被覆ギャップの正直な大きさ**で、拒否 census と並べて受入表に載せるべきもの。

**併記**: `CELTIUM_NOOP_CENSUS` は依然として競合で、出力経路を logger から移した後もこの run には出ていない。
**その不在はどのエントリが呼ばれたかについて何も言わない。**

## 0.166 **task-3（sky）完了——担当の帰属測定と、この fixture では行使できない段階**

担当 sky-draws が task-3 を完了。**rows 0-199 の 256,000 画素のうち 8 を超えて異なるものは 0**（max 4・mean 0.453）、
行平均は y0 `(149.4,187.0,255.0)` → y120-139 `(178.0,206.4,255.0)` → y140+ fog 色 `(179.0,207.0,255.0)` で参照と**0.1カウントまで一致**。
純青画素は frame 全体で 0（以前 ~174,000）、`pureBlueGroups=0`。**gate は両分岐とも削除**（拒否は誰も描かない。拒否が残す clear 色 `(106,123,151)` は参照帯より遠い）。
射影ガード（`celtium$projectionRefusal`）は残し、早期フレームで2回発火。`celtium$noteFogDegradation` は**報告のみ**で拒否しない。

**帰属（重要）**: final vs control は rows 0-120 で 134,870 画素（dome の footprint）差、**row 260 以下は 0**。
一方 **horizon/terrain の参照との差は dome を描いても拒否しても同じ画素数**（rows 200-260 で 57,594、260 以下で 53,593）＝**別の段階**。

**この fixture では行使できない段階（担当が明記）**:
- **地平線 plane**: 唯一の VBO 描画が `if (d3 < 0.0D)`（`RenderGlobal:1463-1480`）の中にあり、fixture の world は `WorldType.FLAT` で `getHorizon()` が 0.0、眼高 ~13.6 なので到達しない。`:1519` の無条件 `glCallList(glSkyList2)` は `glCallList(-1)` で実 GL でも no-op。
- **星**: worldTime が tick 20 から 6000 に固定され `getStarBrightnessBody` が厳密に 0 を返すため `if (f15 > 0)` が偽。星の描画は**拒否から受理に変わった**（1 draw・3120頂点）が、**画素は未検証**。
- **display list は replay されない**（`glCallList` は id を記録するだけ）→ `vboEnabled=false` の構成では空が出ない（ここでは vboEnabled=true）。

**残る拒否（census: calls=424 drawn=125 refused=299）**: client memory のポインタ（47・stub は mirror しない＝Tessellator 自身の描画は uploader フック経由）／float3・stride 12 以外（250・`VboRenderList` の 28バイト頂点や stride 24 の preDraw ポインタ）／identity 射影（2）／他。

## 0.165 **sky band が upstream Celeritas 参照と完全一致（0/215040 画素差）**

最新の GL-less presented frame と **upstream Celeritas 参照 leg**（同一 scene/pose/tick）の直接比較:

| 領域 | mean | >8 の画素 |
|---|---|---|
| **rows 0-168（空）** | **1.005** | **0 / 215040** |
| rows 640-720（HUD） | 0.002 | 6 / 102400 |
| rows 168-240（地平線） | 37.530 | 31996 / 92160 |
| rows 240-720（地形） | 6.388 | 79191 / 614400 |
| frame 全体 | 8.246 | 111187 / 921600 |

format 7 を拒否した control leg の空は **1色のみ**（distinct=1・flat 179,207,255）で、候補と参照は**どちらも 63色・mean 同一**（164.0,196.9,255.0）。
→ **dome・その fog・射影チャネルが揃って、実 GL レンダラが描く空を再現した。**

**残差は局在**: rows 200-320 が 62%・94.75%・41.66% で、200 より上と 320 より下はほぼ 0。
行ごとの色: 200-220 候補 (174.3,198.0,242.9) vs 参照 (161.6,181.7,214.1)（**候補が明るい**）、
240-260 (101.8,68.0,62.2) vs (112.7,108.2,97.5)、300-320 (102.5,59.4,54.5) vs (109.4,64.1,59.0)（**候補が約8暗い**）。
列は中心対称（chunk 境界ではなく遠近の効果）。

この帯は**地平線直下の地形＝fog が効く場所**で、terrain パスも fog を持つ（uniform block が offset 32..48 に mode/density/start/end/colour を書く）。
**問いは terrain の fog 値と sky の fog 値が、その時点でゲームが設定した値と一致するか**——fog 担当へ数値ごと引き継いだ。

## 0.164 **雨/粒子（id5）の受入——format 定数の修正で id0 が消えた**

同一の修正に対する2つの測定:

| jar | 結果 |
|---|---|
| 定数修正前（`p3b-on-3`） | `CELTIUM_UNMAPPED count=38 shape=[3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte 2,UV,Short \| size=28 count=4]`・`id0=40/0` |
| 定数修正後（`ww6-gl-base`） | **id0 なし**・**`id5=142/142`**（`sky-draws-glless` も `id5=94/94`） |

修正前は**どの run のどのヒストグラムでも id5 は 0**——これが「雨がどの census にも無い」の実体だった。
`TEX_2S` の型は `SHORT`（表示名 "Short"）で、私の2定数が `Unsigned Short` と書いていたため
`PARTICLE_POSITION_TEX_COLOR_LMAP` に名前を与えられず、**id 無しで拒否**されていた。

**見つけ方も記録に値する**: 担当 pipelines が原典に対して表を掃引し、「どの run も id3/id5 を一度も offer していない」ことに気づき、
**mod 形式を推測せず unmapped dump を読んだ**。

## 0.163 **alpha 修正の受入（水）／参照 leg の検証**

### 水の leg（alpha 修正の before/after・同一 pose・同一 stack）

| | blackFraction | distinct colours | mean |
|---|---|---|---|
| 修正前（`ww-gl-water2`） | **1.0** | **1** | 0.0 |
| 修正後（`ww6-gl-water`） | **0.324891** | **475** | 16.135 |

水中 overlay は全画面 `POSITION_TEX` で色は `color(f,f,f,0.5)`、f は眼の光量で**水深5ブロックで 0**。
色なし shader が fragment alpha を 1.0 に固定していたため**全画面を不透明で塗っていた**。
**同じ push offset の2つの半分**（shader の `u_ColorA`・backend の `color[3]`）で画が場面になった。

### 参照 leg（upstream Celeritas）の検証

`ref-celeritas2`（私）と `sky-draws-ref`（担当）が**同一の数値** `nonBg 0.761173`・**PASSED**・tick 150 の screenshot 取得成功 ✓
——参照手順が再現可能であることを2人が独立に確認した。

### 今日の4つの欠陥に共通する形

**「値はそこにあり、経路が運べなかった」**:
panel（mirror に画素はあるが色なし variant が誤った stride で読む）・sky（dome が fog を切った後に fog を捕捉）・
雨（表が名前を与えられない形式。私の2定数が `Short` を `Unsigned Short` と書いた）・水（alpha の行き先が無い）。

## 0.162 **fog の真因（close 時の snapshot）と、原典からの2つの訂正**

### fog（担当 fog-path・測定済み）

fog-on と fog-off の leg が **sky band のどの画素も違わなかった**のに、ゲートは `CELTIUM_SKY_DEGRADED fogEnabled=true fogAvailable=false` を出していた
（「拒否をやめたゲートは fog が正しいことではない」の実例）。

**機序**: `closeCurrentImmediateGroup()` が fog を group **close 時**に snapshot していた（行列と GL 状態は open 時に取っている）。
`RenderGlobal.renderSky` は `:1337` で fog を有効化し `:1354` で無効化するため、**dome の group（fog 下で offer され、後に close）は `fog == null`** を持ち、shader の fog 分岐が no-op だった。
`ImmediateGroup` の javadoc は per-batch capture を主張していたが、実装がそうなっていなかった——**構築順バグと同じ「状態を間違った時点で取る」型**。

修正: `currentGroupFog` を group-open で捕捉（`VulkanTerrainGpuBackend:3789`）、close で使用（`:5303`）、計器 `CELTIUM_FOG_CAPTURE` を追加。

### 私の format 定数の誤り（担当 pipelines が発見）

`TEX_2S` は `EnumType.SHORT` で**表示名は "Short"**。"Unsigned Short" は vanilla が使わない `USHORT`。
→ `LAYOUT_BLOCK`/`LAYOUT_PARTICLE` が誤り、**BLOCK(id3) と PARTICLE(id5) は表の作成以来ずっと拒否**されていた。
**「雨がどの census にも現れない」の正体**。unmapped dump が形を名指し: `3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte 2,UV,Short | size=28 count=4`。

### 原典からの訂正（担当 entity-models）

1. **id 8 は entity/TESR の layout ではない**: `POSITION_TEX_COLOR_NORMAL`(28B, id8) の vanilla の用途は `RenderGlobal.drawWorldBorder`(:1676) と `RenderXPOrb`(:63) のみ。
   **model/entity の layout は `OLDMODEL_POSITION_TEX_NORMAL`(24B, id9)** で、唯一の利用者は `TexturedQuad.draw`。実測で 228 個到着（捕捉 quad ごとに1つ）——
   `TexturedQuad.draw` が `Tessellator.draw()` で終わるため、**model 幾何は compile 時に即時パスでも1回描かれる**（replay 時の display list に加えて）。
2. **living entity は fixed-function lighting では照らされない**: `enableStandardItemLighting` は GUI/item/TESR/dragon/item-frame の経路で呼ばれ、`RenderLivingBase` には呼び出しが無い。
   living entity が受けるのは unit 1 の lightmap（`EntityRenderer.enableLightmap()`）で、通常経路に `setLightmapTextureCoords` は無い。**TESR は `enableStandardItemLighting` を呼ぶ**（`TileEntityRendererDispatcher:125`）。

## 0.161 **GUI panel は pipeline の問題だった（texture mirror ではない）——測定で確定**

担当 texture-mirror の測定: **format-6 パイプライン修正が入った jar では panel と slot 背景が GL-less の presented frame に在る**。
mirror census は panel sheet（gl id 124・129、256x256）が**ゲーム自身の announcement から充填**され 24224・26502 の不透明 texel を持ち、**stale=false・patch 拒否 0**。

→ **mirror は最初から画素を持っていた**。色なし variant が、その画素で描かれた頂点バッファを読めなかった。
§0.157 に「提出・受理されているのに画素が出ない」と記録した欠陥は、**`VulkanImmediateTexturePipeline` の構築順バグ**（全 texture-family variant が POSITION_TEX_COLOR として振る舞う）であり、
**私がそれが何を直しているか理解する前に修正されていた**。

**教訓**: 「提出・受理されているのに画素が出ない」を3人（私を含む）が texture の問題と読んだが、答えは**誰も所有していないファイルの構築順1行**だった。

**この修正で、私が fixture 側で追った JEI/inventory 対照（tick フック・較正キーの screen variant・tick 60 の presented dump）が初めて意味を持つ**——
同一 screen・同一 tick で **GL-less vs upstream Celeritas** を比べられる。

## 0.160 **方針変更: bridge は使わない（GL-less のみ）／チーム運用の修正と新しい欠陥**

### ユーザー決定

**「bridgeとかいらない。gllessのみでいこう」**。GL interop の bridge leg は完成条件に不要。以降の受入は
**保存済みの参照**（bridge 時代の画像と較正済み床＝vanilla が何を描いたかの証拠としては有効）か、
**同一 jar・1フラグ差の自己比較**。GL-less 同士を別 jar で比べたものは比較にならない。全10体へ通達済み。

### 「全体が半分暗い」は私の誤り（撤回）

`jei-gl60`（GL-less・tick 61）と `jei-br`（bridge・tick 60）を比べて「全段階で約半分の明度」と記録したが、担当 frame-composite が**内容の artefact** と示した:
12:41 の bridge は**地形を描いておらず**、その最頻色 `0x8B8B8B` は **JEI の画面パネル**、GL-less 側の最頻色 `0x1D1111` は**地面**。つまり「パネル対世界」を比べていた。

**正しい分離測定**（既存データ・GL-less `van-glless` vs 保存参照 `scene-floors/terrain-a`）:

| stage | >8 で異なる画素 |
|---|---|
| terrain centre (320,300,960,640) | **1 / 204800** |
| bottom-left / bottom-right | 1 / **0** |
| hud / hotbar / tooltip / chat | **0** |
| horizon band | 460 (0.2%) |
| sky band | 87.8%（既知の dome 欠陥。flat 179,207,255 vs bridge の勾配 158,193,255） |

**swapchain は `VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR`/`B8G8R8A8_UNORM`、present は 1:1 の nearest blit**——premultiplied alpha も transfer function も経路上に無い（原典で確認）。
**地形と HUD は既にほぼ一致**しており、**残るは sky band**（dome 担当の領域）。black fraction は terrain scene で 0.0〜0.0023（0.19〜0.99 は glow/GUI 段階）。

### スロット調停（`67cf6f806`）

4体が同じ秒に busy チェックを通り、**3クライアントが1つの GPU で走った**。check-then-launch の窓が問題で、規律の問題ではない。
ハーネスが `.celtium-work/game-slot` に**pid 入りのロックファイルを原子的に作成**し、生存中は待機（`--slot-wait`、既定900秒）、死んでいれば回収、全終了経路で解放。
**1 GPU 2 run は証拠にならない**——今日5件目の競合で、再発しない形になった。

### ハーネスのもう1つの穴（`8ae2e64f8`）

`--mod` と同名の instance 側 jar だけを除外していたため、**別名で凍結した担当は instance の jar もコピーされ duplicate mod で起動失敗**（fog-path・entity-models が各1 leg を失った）。
→ **instance 内の `celtium-forge-*`/`celtium-validation-client-*` は常にコピーしない**。ユーザーの起動用 jar は残せる。

### 新しい欠陥（チームが発見）

- **`GlStateManagerMixin:355-363` のフック名が誤り**（担当 gui-stage が `--srg` で確認）: `func_179141_d`/`func_179118_c` は **enableAlpha/disableAlpha** なのに `setLightingEnabled` として扱っている。
  本来の lighting は `func_179145_e`/`func_179140_f` で**未フック**。→ **mirror の alpha test は常に off、lighting フラグは alpha 呼び出しで駆動**。proj-channel の担当ファイルなので routing 済み。
- **雲（format 8）**: GL-less で **frame 1 のみ受理され以降提出されない**（27 group すべて frame=1）。保存参照の同一 pose には fancy cloud が写っている。
- **雨（format 5）**: どのヒストグラムにも `acceptedByFormat` にも現れない（`5:0`）＝**offer されていない**。
- **flat generator 文字列の先頭は version**（`1;` は `"x"` 区切り）——担当が自分で発見し再取得。

## 0.158 **bridge は 09:47 から地形を描いていなかった——depth format が GL-less でしか選ばれていなかった**

担当 A が**レポートを読んで**（verdict ではなく）発見した、その日で最も重要な欠陥。

```
bridge run            mtime   nonBg     passes/skipped
glow-bridge5          09:02   0.748878  passes=909 skipped=0   <- 最後の正常な bridge
bridge-model          09:47   0.149201  passes=0   skipped=447
n03-ae-terrain-bridge 12:23   0.813402  passes=0   skipped=936   <- 「PASSED」だが地形は無い
van-bridge2           12:59   0.153832  passes=0   skipped=1122
```

すべて `skipReason=translucent:_no_pipeline` と
`pipeline for solid|cutout_mipped|translucent failed: IllegalStateException: the depth format was asked for before anything chose one` を伴う。

**連鎖**: `VulkanDepthFormat.chosen()`（引数なし）は未選択なら**例外**。選択する唯一の呼び出し元は `GllessFrameTarget:187`（**GL-less 専用**）。
`VulkanTerrainPipeline:901` は引数なし形を使う。→ **bridge では誰も選ばず、地形パイプライン生成が全て失敗**。Celeritas は「solid - 46 sections」と報告するが Celtium は 1つも描かない。

**修正（根因側）**: `VulkanContext` が物理デバイスを得た時点で `VulkanDepthFormat.noteDevice(device)` を呼び、引数なし `chosen()` は**初回使用時に自分で選ぶ**。
例外は「Vulkan デバイスが存在する前に depth format を聞いた」という本当の順序エラーのためだけに残し、メッセージもそう書き換えた。

**検証**: `depthfix-bridge` = `nonBg 0.744563`・**PASSED**（修正前 0.153832 FAILED）。担当 A が独立に確認（`passes=138 skipped=0`・背景 0x571716＝地面）。

**私の記録の訂正**: §0.130 の「depth format 変更は AMD では無影響と実測」は
`scripts/vulkan_depth_format_probe.py`＝**能力プローブ**で、chooser が*どの形式を選ぶか*を測るだけで **bridge leg を一度も走らせていない**。
形式については真、**構成については偽**だった。

**「PASSED」だった bridge run は地形参照にならない**（担当 A の測定）: `n03-x`・`n03-ae`・`n03-ah/ai/aj` の画に地形は無く、
`nonBg 0.81` は**空だけの画の上の chat block**（floor 0.40 は chat block で満たせる）。最後の正直な bridge 地形画像は 09:47 以前。

## 0.159 **チーム編成（10タスク）と、ユーザー起動クラッシュの分析**

### チーム

10体を Agent Teams のメンバーとして起動し、共有タスク `task-1`〜`task-10` を割り当てた:
投影チャネル（proj-channel）・fog（fog-path）・天球/星/地平面（sky-draws）・texture mirror（texture-mirror）・GUI/HUD（gui-stage）・
**format/pipeline（pipelines・critical path）**・entity/TESR（entity-models）・**黒領域と全体暗化（frame-composite）**・天候/水/粒子（weather-water）・**独立受入（acceptance）**。

統合時に tree は赤だった（`VulkanImmediateFormats` が未定義の `LAYOUT_*` を参照、fixture の renderer-less entity が `Entity` の抽象3メソッド未実装）。
定数は**原典から**追加（`DefaultVertexFormats` の要素順、`VertexFormatElement` の display name。lightmap 要素は usage が UV なので `2,UV,Short`——**私は当初 `Unsigned Short` と書いて誤った**。`TEX_2S` の型は `SHORT`（表示名 "Short"）であり、"Unsigned Short" は vanilla が使わない `USHORT`。その結果 **BLOCK(id3) と PARTICLE(id5) が表の作成以来ずっと拒否**されていた——「雨がどの census にも現れない」欠陥の正体。担当 pipelines が修正）、
抽象メソッドを実装し、`95d74bac6` としてコミット（**ビルドが通るからコミットした**、完成だからではない）。

### ユーザー起動クラッシュ

`java.lang.NullPointerException: Cannot read field "field_70165_t" because "entityIn" is null at World.getSkyColorBody(World.java:1608)`
← `EntityRenderer.func_78466_h`（updateFogColor）が `mc.getRenderViewEntity()` に null を渡している。
原典では `renderWorld` が `renderViewEntity == null` なら `player` を入れるが、**`player` も null なら NPE**（world 読み込み中・dimension 切替の窓）。

**直接原因はインスタンスの mods にあった WIP jar**（`ac347ff1…`、13:04 にコピー。担当 P が退避）。
HEAD `0fddd6ccf` から**クリーンな jar をビルドして設置**（`11c0a807…`）——これでユーザーは起動できる。

### ハーネスの修正

instance の mods に celtium jar があると `prism_validation.py` が「Test mod would overwrite a copied mod」で**全 run を起動前に落としていた**。
**呼び出し側が `--mod` で指定した名前は instance からコピーしない**よう変更（`5c9bec7a1`）。ユーザーの起動用 jar と検証 run が共存できる。

## 0.157 **inventory/JEI の対照が成立——画が「何が欠けているか」を名指しした**

fixture の2つの欠陥を直したことで、初めて対照が取れた:
1. **tick フックを `>=`＋once-flag へ**（16箇所。6つは新規フラグ——特に **mob spawn と allBlocks の壁**は「読む」ではなく「作る」ので、guard 無しの `>=` は毎フレーム再構築になる）。
2. **較正キーに screen variant を追加**（`jei`/`recipe`/`keep`/`none`）。画面を開いたまま capture すると**画が変わる**のにキーに入っておらず、
   `jeiScreen`+`keepInventoryScreen` の run が plain terrain の参照に対して `sceneCalibrationKeyMatches=true` を出していた（**A03 と同じ型の穴、別の次元**）。

### 対照（同一 tick 60・同一 screen）

```
GL-less: celtium-presented-early-…-kept.png（presented dump 221KB）
bridge : celtium-at-tick-60.png（GL screenshot 225KB）
```

**画（`.celtium-work/jei-pair/side-by-side.png`）が語ったこと**:
- GL-less は **JEI のアイテム格子・アイテム自身・"Gold Ore" ツールチップとその文字・hotbar を描いている**
- GL-less は **panel/slot の背景・カテゴリラベルの背景・hotbar の枠を描いていない**
- bridge はその全てを描く
- **文字は両 leg で内容・大きさ・位置が一致**（＝N03 が必要としていたもの）

**数字の読み方**: panel 領域 94.8%・frame 全体 94.5% の画素が >8 変化——これは**欠けている背景**であってフォントではない。
tooltip 領域は 38%（自身の暗い背景＋文字）。**「frame の 94.5% が違う」だけを読むと描画失敗に見えるが、crop と併せて読むと「1クラスの幾何が欠けている」であり、その上の文字は正しい。**

### 次に名指しされた作業

**GUI の単色 quad（panel/slot 背景・ラベル背景・hotbar 枠）**が欠けている。アイテム・文字・hotbar の中身は描けているので、
欠けているのは **`Gui.drawRect` 系の POSITION_COLOR quad** のクラス。原典で使われる (mode, format) を確認し、対応表に分岐が無いなら追加する。

## 0.156 **訂正: 私の stride 読みは自分の grep の打ち切りだった／真の機序は射影**

担当が確認して私を訂正した: **`pointerStride=1` はどのログにも存在しない**。16箇所すべて `pointerStride=12`。
私が引用した行は**自分の `grep -oE` の120文字打ち切り**でフィールドの途中が切れたもので、`pointerStride=12` が `pointerStride=1` として届いた。
**その上に finding を建て、担当へ「安い修正」として渡した。**

→ **今日3度目の「計器が artefact を作った」**で、今回は**私が打ち込んだ grep の bound** が計器だった。
goal の規則は「計器が主張どおりのものを読んでいるか確かめる」だが、**「届いた値が値そのものか（打ち切りでないか）を確かめる」**も要る。

### probe が実際に見つけたもの（＝§0.136 の第二候補が真の機序）

```
count=676 buffer=18 pointerStride=12 v0..v3=[(-384,16,-384) (-320,16,-384) (-320,16,-320) (-384,16,-320)]
  mirroredProjection=[0.805,1.431,-1.001,-1.0,-0.1,0.0]   ← 導出された透視射影
  mirroredProjection=[1.0,1.0,1.0,0.0,0.0,1.0]            ← identity
```

buffer 18 は **sky dome**で、頂点は `renderSky(builder, 16.0F, false)` の構築どおりに復号される（676頂点・64ブロック quad の13x13・先頭 (-384,16,-384)）＝**復号は正しく、射影が変数**。
**最初の sky 描画で identity が出る run が、紺色の翼を持つ run**（R=G=0 が 88k-120k 画素）。変更前 jar の同一 scene は 4-10k＝ノイズ。
機序は §0.136 の記録どおり: **Kirino が mirror 済み `loadIdentity` の後に自前の `gluPerspective` を呼び、それが mirror へ届かない**ため、
その窓で offer された batch は model-view だけで変換され、**768ブロックの平面が frustum 辺に沿って細片化**する＝翼。
**純青の平坦な ramp である理由もこれで説明できる**（平面自身の幾何であり、fog された空ではない）。

**担当は測定された条件に名前つき拒否を追加**（identity または x/y が潰れた射影での format-7 描画を拒否・`refusedByReason` に計上）。
正しく変換された描画は通り続けるので**経路は生きたまま**、射影チャネルが正しい場所では空が出る。**§0.152 で私が求めた形**（信頼できない変換は名前つきで拒否し、チャネル本体は本来の作業として直す）。

## 0.155 **pose 修正の検証完了——FAIL はゲートが正しい**

再試行は clean にビルドされ新 jar で走った。2つのカメラ文字列が決着させる:

```
cameraRequested: … yaw=180.000 pitch=20.000      （scene pose）
cameraAtVerdict: … yaw=180.000 pitch=-14.000     （allBlocks 上書き）
FAILED: the camera at the capture is not the pose the scene's floors were measured at
        (requested … pitch=20.000, measured … pitch=-14.000)
```

**上書きが描かれるフレームを支配している**（測定 pitch は補間値ではなく上書きそのもの）。修正前は描かれた pitch が
**キャプチャが sweep のどこに落ちるかに依存**していた——だから sky 担当の同一 jar の2 run が 0.741 と 0.851 になり、
bridge が約 -14・GL-less が +20 を描いていた。**fixture は不一致を隠さず報告するようになった。**

**そして FAIL 自体が正しい**（回避すべきものではない）: allBlocks scene は壁を収めるため意図的に pitch -14 を上書きし、
terrain scene の床は pitch +20 で測定されている。**ゲートは「ある pose の画を別の pose の床で判定しない」と言っている**——
監査 A03 が参照キーの追随として別方向から到達した結論と同じ。**allBlocks は別 variant・別 pose であり、ゲートするなら自身の参照 run が要る。**

**残る未検証**: pose を固定した状態で翼がどう変わるか（sky 担当の crop が領域レベルだったのはまさにこの理由）。**次の測定は固定された pose で取れる。**

## 0.154 **翼を名指し（sky dome・format 7・fog 無し）／pose の跨ぎ問題**

sky 担当の最終報告（測定・jar `216cd6284a3c22f7…`・AMD RX 6400・GL-less allBlocks）。

### 局在の撤回（計器バグ）

`reportGroupBounds` が group の頂点を `VERTEX_BYTES`(16) で読み、実際の stride は 20 だった（§0.150）。
**mesh 自身の stride**（`VulkanTerrainGpuBackend:3462`）で読み直すと、**frames 4-24 で太陽・月とも不可視**＝vanilla が描く姿。
group の modelview は `Rx(20°)·Ry(-90°)` に厳密一致、両回転が1回ずつ順序どおり、非ゼロ celestial の第2検査もゲームの 282.4° を復元。
**backend/mirror に変更の正当性は無い**（私が依頼した修正は測定誤差を追っていた）。

### artefact の正体（group 単位で測定）

- **rows 0-270 の 113,120 の暗紺画素**を覆う唯一の group は **676頂点の format-7 group＝`RenderGlobal.renderSky` の sky dome（skyVBO）**。
  modelview は**カメラ base のみ・回転なし**（正しい——dome は renderSky の push/rotate より前）。ndc は 390/676 が背後、残りが `x[-4.92..5.74] y[0.51..0.81]`＝**翼の帯**。
- **新しい**: 11:48 の jar の同一 scene/pose には翼が無い（421 navy px＝壁自身の暗ブロック）。その間の描画に関わる唯一の変更が **`glDrawArrays` 経路**（skyVBO/starVBO を即時パスへ）。
- **bridge 対照**: 実 GL が同じ dome を描き、**空の navy 画素は 0**。
- **私たちの即時パスに fog が無い**——fog が vanilla の平らな dome を「空」に見せている。**修正は dome の所有者が fog で行う**（revert ではない）。

### pose の跨ぎ（計測の整合性・fixture 側の問題）

pose 上書きは `RenderTickEvent` END（描画後）、`celtium$applyPose` は次の render 前——**同じフィールドを別の時点で書き、描かれた pitch が client tick 間で補間される**。
**GL-less の frames 4+ は +20、bridge の screenshot は約 -14** を捉えた。よって担当の crop は**領域レベルの比較**（pixel-for-pixel ではない）。
実 GL の run は今 `"the camera at the capture is not the pose … measured pitch=-14"` で FAIL する＝**fixture が自身の上書きを検出**している。
**2 leg を跨ぐ比較は先に pose を固定する必要があり、それは私が直すべき fixture の変更**。

### 未解決（担当の明記）

翼の pixel-exact な機序は未モデル化（group 帰属は測定済み）／即時パスに fog 無し（dome 担当の作業）／frame-1 の異常は記録のみ／非ゼロ celestial の検証は frame 2 のみ。

## 0.153 **翼の第一候補は「記録された stride が誤っている」——担当の probe が捕捉**

stub 担当の `CELTIUM_DRAWARRAY_LAYOUT` が同じ呼び出し形の2つの buffer で**異なる stride** を出した:

```
buffer=17 pointerSize=3 pointerType=0x1406 pointerStride=1
buffer=18 pointerSize=3 pointerType=0x1406 pointerStride=12
```

ゲームの呼び出しは原典で `glVertexPointer(3, GL_FLOAT, 12, 0)`（`RenderGlobal.java:1344`・`:1441`・`:1470`）なので、**stride 1 はゲームが要求した値ではない**。
**12バイト stride で書かれた配列を stride 1 で読むと、翼が見せる崩れた平面幾何そのもの**になる。size と type は両方正しいので、**stride フィールドだけ**が誤り
＝`glVertexPointer` の**引数マッピングか記録**の問題で、デコードの問題ではない。

**拒否パターンとも矛盾しない**: `glDrawArraysCalls=64 drawn=7 refused=57`・`calls=128 drawn=29 refused=99`。
片方の buffer の記録状態が誤っていれば**拒否が正しく**、描かれているのは状態が正しい方。**census は仕事をしており、バグはその上流**。

→ **翼の第一候補は「記録バグ」**で、§0.152 の射影ギャップは**第二候補**に下がる。順序は保つ価値がある:
**誤った stride と identity 射影はどちらも崩れた平面を作る**が、stride は**既にある layout 行で測定できる**。

**fixture property の動作確認**: font 担当の run が `screenshotAtTick=60` を報告しファイルも存在（`screenshotAtTickScreen=none`＝その構成では tick 60 に画面が開いていない。
GUI capture には `keepInventoryScreen` も必要）。**「none」を「capture 失敗」と読まない**ための記録。

## 0.152 **検証: 射影チャネルの mirror は存在するが呼び出し元が無い**

stub 担当の読みを tree で確認した:

- `TerrainGpuBackend.perspective`（`common/.../TerrainGpuBackend.java:123`）と `frustum`（`:130`）は interface の default として**存在する**。
- `celtium-vulkan` と `forge122` を `.perspective(` / `.frustum(` で検索して**呼び出し元は 0**。

→ **`Project.gluPerspective` を mirror へ運ぶはずのチャネルが、存在するのに未使用**。ゲームは world の射影と sky でその呼び出しに到達し、
Kirino 下の `renderSky` 直前は `loadIdentity`（mirror 済み＝射影 mirror が identity 化）＋`gluPerspective`（GlStateManager を通らない）。
**mirror の射影が sky を描くまさにそこで identity になりうる**——翼の形はこれと整合する。

**これは撤回したフックが対象にしていたギャップそのもの**で、撤回は**到達範囲**の話だった（フックは world の可視セクション数を 23→8 に変え、terrain を失わせた）。

**次の試行の形（試す順）**:
1. **必要な描画（sky）にだけ**射影チャネルを供給し、culling が読むものは触らない。
2. mirror の射影が信頼できない描画は、**名前つきで拒否**する——拒否は画の穴だが、誤った変換で描くのは**誤った画**であり、このプロジェクトは今日その違いを学んだ。

**ここでは実装しない**: そのファイルは今ラウンド stub 担当が所有し、まさにこの問いを測定中。同じファイルに2人目の編集者が入るのが、2つの半修正を1つの壊れた tree にする道。

## 0.151 **翼は `glDrawArrays` 経路と計測で帰属／射影 mirror のギャップが原因候補**

stub 担当の測定（同一 fixture・同一 pose・変更前 jar `0c90f819` vs 変更後 `d50f1a56`・両方 GL-less/allBlocks）:

- **rows 0-200 の 39.4% の画素が >8 変化**、変更後の frame は **90,442 画素（9.8%）が厳密に `(0,0,B)`（B=0..192）**で滑らかな planar ramp＋flat sub-facet を成す。変更前には無い。
- 担当の読みは私の fog 説より強い: **fog 無しの平面なら空の色 (179,207,255) になるはず**で、`R=G=0` の純青が9万画素というのは**誤った変換**（頂点が clip volume 外に出て frustum 辺と交差する部分だけ残る＝角の wedge の形）または**色の誤り**の形。どちらかを測定中。

### 射影 mirror のギャップ（原典から名指し）

Kirino 下では `renderSky` の直前が **`GlStateManager.loadIdentity`（mirror 済み＝射影 mirror が identity になる）＋ `Project.gluPerspective`（GlStateManager を通らない）**で、
**backend の `frustum()`/`perspective()` mirror には呼び出し元が存在しない**。
→ **sky の描画まさにその瞬間に射影 mirror が identity になりうる**。

**これは私が撤回したフックが対象にしていたギャップそのもの**。フックが誤りだったのは**到達範囲**（world 全体の culling に餌を与えた）であって、**診断ではなかった**。
次の試行は**狭い形**——それらの描画で即時パスが使う射影、または信頼できない変換なら**名前つきで拒否**——にすべき。

### 計器の競合（既存の読みに影響）

**`CELTIUM_NOOP_CENSUS` は競合で通常は出ない**: Forge が `System.out` を Log4j へ回し、Log4j の shutdown hook が appender を止め、hook は並行に走る。
同じ run で別の hook の行は出るのに census は出ないことがある。担当が**出力経路だけ**（logger ではなくプロセスの stdout）変更し、内容は不変。
→ **「census が無い＝no-op が呼ばれていない」と読んだ読みは競合を読んでいた**。

## 0.150 **訂正: wedge の局在は計器バグだった／レポートのカメラは描かれたカメラではない**

担当が §0.139 の局在を**計器で反証**した（これが本プロジェクトで繰り返し学んでいる基準）。

### 1. 計器が誤った stride を読んでいた

`reportGroupBounds` は頂点を `VulkanImmediatePipeline.VERTEX_BYTES`（**16バイト**）で読んでいたが、
immediate group は呼び出し側の頂点をそのままコピーし**自分の stride を mesh layout に記録する**——POSITION_TEX は **20バイト**。
したがって頂点1〜3は**他の頂点のテクスチャデータの内側**から読まれ、「月が画面を覆う `ndc x[-41.70..0.00]`」は**読みの artefact** だった。

**group 自身の stride で再測定**（同一 scene・同一カメラ・同一フラグ）:

```
太陽 group: ndc ALL BEHIND (4/4)
月 group:   ndc x[-0.89..0.89] y[-8.09..-2.08] z=1.00 behind=0
```

**両方とも不可視**＝vanilla がその pose で描く姿そのもの。

### 2. 行列は vanilla のもの（行列単位で一致）

group の modelview は `Rx(20°)·Ry(-90°)` に厳密一致。計器の `rot=` は**両方の回転が1回ずつ順序どおり**届いたことを示し、
**非ゼロの celestial angle での第2の検査**でもゲームが渡した値（282.4°≡-77.6°）を符号・大きさとも復元。
**欠落・二重適用・順序誤り・別スタックのいずれでもない** → `VulkanTerrainGpuBackend`/`VulkanGlState` に**変更の正当性は無い**。

### 3. レポートのカメラ ≠ 描かれたカメラ（より広い問題）

`allBlocks`/`lookDown` の上書きは `RenderTickEvent` の **END**（フレーム描画の後）で走り、`celtium$applyPose`（ClientTickEvent）が
次の render 前に scene pose を再適用する。よって **レポートが pitch -14 と言っても renderer は +20 を描いている**
（mirror と frame の両方で実測: 地面/空の境界が rows 240-276、-14 なら row 473）。
→ **「pinned pose」の主張と pose で key された床は、どちらの意味かを明記する必要がある**。前担当の太陽/月の判定基準は
実際に描かれた pose では**反転している**。

### 4. 実際に画にあるもの（残る作業）

**左上・右上の紺色の重なり多边形**＋**それ以前からある magenta の筋**。翼は **12:11 の jar** で出現し、
その間の唯一の描画変更は**他担当の `glDrawArrays` 経路（sky dome・horizon plane・stars を format 7 で提出）**。
**私たちの即時パスには fog が無い**——bridge では fog がそれらを空に見せている。担当へ「計測で帰属を確かめ、fog/state を見る」よう送付（revert は求めていない）。

**結論: wedge は閉じていない。閉じたように見えたのは局在が誤っていたから**で、残る artefact は今まさに作られている別の経路のもの。

## 0.149 **sky 回転修正の中間状態（担当の計器が modelview を印字）**

担当の診断 run を読んだ（報告を待たずに）:

```
texture=113（太陽） ndc all behind the camera   mv=[0.0,-0.342,0.94,0.0,…]
texture=114（月）   ndc x[-0.89..0.89] y[-8.08..-2.08] z[1.00..]
```

修正前の月は `x[-13.69..0.00] y[-0.35..3.55]`＝**画面左半面を覆う**（＝artefact そのもの）だった。
現在は **x が画面中央付近・y は画面のはるか下**で、**左半面の被覆は消えている**。太陽は背後（vanilla の期待どおり）。
残る問いは「月が背後ではなく画面下にいる」ことで、artefact そのものではない。

frame は `nonBg 0.741273`・`distinct 9398`・`UNCALIBRATED`。
**中間状態として記録**し、修正済みとはしない——担当がファイルを所有しており、受入は担当の run が閉じる。

## 0.148 **goal 本文の更新は人間ターン待ち（方針どおり）**

`docs/goal.md` は本セッションの撤回・受入を反映済み（`f7ad1a29d`）。一方 **objective 本文は revision 66 のままで、
「glow は chain 専用 image へ設計変更中」「wedge は3候補から識別」という記述が既に古い**（実際は chain が走り、wedge の原因は測定済み）。

goal tool は編集を拒否した: **objective の edit は top-level agent への直接の人間ターンを要する**。今ラウンドは自動継続なので該当しない。
**これは方針どおりの動作**であり、回避せずに記録する。次に人間ターンが来た時に、以下を反映する:

- 到達点: N03/N10/N07/N08/N17/N05・A01〜A05・所有権ギャップ4つが受入済み。outline chain は実行確認（画の受入が残る）。
- 撤回: 「runtime は outline を描かない」・「注入4つは死んでいる」（`CELTIUM_RENDERENTITIES calls=3` で反証）。
- wedge: 原因は `renderSky` の月 quad の固定回転。残りは sky 回転が modelview に載らない理由。
- 規律に追加: **デコンパイルの読みだけで「死んでいる/置き換えられている」と結論しない**（計器で確かめる）。

## 0.147 **N17 の画像側を実証（fault を bind の後へ追加）**

監査の指摘は正確だった: 既存の fault は `vkAllocateMemory` の**前**に発火するため、証拠として引用された run の image ハンドルは**stale**で、
**そもそも image が作られていなかった**——「image は作られ memory が無い」も「image と memory が作られた後に失敗」も実証されていなかった。

`vkBindImageMemory` の**後**に3つ目の fault site を追加し、実測:

```
CELTIUM_IMAGE_RELEASED image=0x70000000007 memory=0x80000000008 view=0x0 requested=512x512 bytes=[…]
injected allocation failure after vkBindImageMemory: VulkanGlTexture
validationClean=true   validationMessageCount=0
```

**両ハンドルが作られ、両方解放された**（view は `vkCreateImageView` の前に失敗したため 0x0）。監査が「run が無い」と言った当のケースが、
主張ではなく測定で埋まった。frame は `FAILED_PRESENTED_IMAGE` に落ちる（テクスチャに失敗を注入したのだから当然）、validation は clean でクラッシュしない。

**これで監査の所有権ギャップ3つ＋N17 の未実証点がすべて閉じた。**

## 0.146 **chain pair が実行時に使われている／計器の2つの問い**

glow run の `rendererDebug` を（期待した counter ではなく）直接読んだ結果:

```
framebuffer bind: identity=0x34160193 main=true pair=chain-pair (distinct identities so far 1)
framebuffer colour texture: identity=873857427 texture=3 1280x720
```

**glow 担当の2ペア routing が実行時に生きており、chain pair が使われている**——split が counter だけでなく実 run に存在する最初の証拠。

**ただし2つの問いが残る（ここでは答えない）**:

1. この行は `main=true` と `pair=chain-pair` を**同時に**報告している。設計は「identity 0 → default pair／それ以外の非 main identity → chain pair」なので、
   **census が「その bind に選ばれた pair」ではなく「現在の pair」を印字している**（ラベル問題）か、**main の bind が chain pair に到達している**（routing 問題）かのどちらか。
   切り分けは「routing 規則が返した pair」を印字すること。
2. **post chain のサマリ（`postChains`・`postAdopted`・`postRunsOnStandIn`・`offTargetPairs`）が依然レポートに無い**。
   `rendererDebug` には viewport・terrain backend・memory・transfer queue・framebuffer census が載るが、post chain の行が**1つも無い**。
   一方コンソールには `CELTIUM_POSTCHAIN create / UBO_ROUTE / ROUTE / PACKED` が出ている。
   **計器は存在するが、受入が引用する artifact に届いていない**——他行と同じ場所に足すのは私のファイルの1行だが、
   今ラウンドはそのファイルを担当が編集中なので、編集を重ねずに引き継ぐ。

## 0.145 **outline chain が走った（訂正の実測裏づけ）**

§0.143 の訂正を受けて、健全なツリーで glow シーンを再測定した結果、**chain が native executor に到達**:

```
CELTIUM_POSTCHAIN create: a chain reached the native executor on this backend
CELTIUM_POSTCHAIN UBO_ROUTE device=AMD Radeon RX 6400 (RADV NAVI24) binding=2
CELTIUM_POSTCHAIN ROUTE program=entity_outline route=pushConstants
CELTIUM_POSTCHAIN PACKED program=entity_outline bytes=84
```

**`entity_outline` プログラムが記録・経路選択（push constants・84バイト＝ShaderGroupMixin 修正後のサイズ）・native 実行されている。**
以前の `postChains=0` は **scene の状態**であって runtime の挙動ではなく、訂正の予測どおりだった。**glow インフラが初めて実際に行使された**。

同じ run の画: `nonBg 0.81016`・`distinct 47597`・verdict は `UNCALIBRATED_PRESENTED_IMAGE`（glow variant の参照が無いため＝**A03 修正が正しく働いている**）。
`presentedBlackFraction=0.18984` は残っており、UBO 担当が独立に指摘した**黒領域の欠陥**として未解決。

**計器の穴（再掲）**: `postChains`・`postAdopted`・`postRunsOnStandIn`・`offTargetPairs` が**この run のレポート JSON に出ていない**。
読めるのはコンソール行だけで、受入が引用すべきサマリが読めない。計器は存在するのに artifact へ届いていない——**「読めない計器は計器ではない」**の同族。

## 0.143 **重大な訂正: `renderEntities` 注入は発火する（§0.135〜§0.137 の結論を撤回）**

「死んでいる」と私が判断した注入にカウンタを足して測った結果:

```
CELTIUM_RENDERENTITIES pass=... calls=3     （GL-less allBlocks・110秒）
CELTIUM_BLOCKLAYER calls=1 calls=2          （もう一方の入口も生きている）
```

**発火している。** つまり:

- `RenderGlobal.renderEntities` は**この runtime で呼ばれている** → Kirino の `MinecraftEntityRendering` は
  **置き換えではなく追加**（injection か追加パス）で、少なくとも TESR 注入は生きている。
- §0.136 の「死んだ注入4つ」は**誤り**（測定に反証された）。
- §0.135 の「runtime は entity outline を描いていない」も**同じ読みに依存していた**ため撤回する。vanilla の entity ループが走るなら
  **outline 経路も走る**。glow シーンの `postChains=0` は、あの scene が置かれていた状態（私が後に撤回した射影フックで
  in-frustum の entity が居なかった）で説明でき、**runtime が outline を落としているわけではない**。

**方法の教訓（これが本項の主題）**: デコンパイルを読んで「置き換えられている」と結論し、**計器で確かめなかった**。
`renderBlockLayer` では計器を先に置いて正しく判定できたのに、この件では読みを信じた。
**フックが生きているかは run が答える**——`CELTIUM_RENDERENTITIES` と `CELTIUM_BLOCKLAYER` のカウンタは残す。

## 0.144 **N03（font）受入 — glyph ポインタ修正が全てだった**

担当の受入（`318a24d0a`・記録 `2cf9e136b`）:

- **magenta probe が「描かれているか」を分離**: 修正前は 7 run すべてで magenta 画素**ゼロ**、修正後は presented frame に
  タイトル・トースト・chat の glyph が出る（crop 450,0,830,60 の前後比較）。**修正前の probe 結果は全て無効**として破棄。
- 最終: `windows=53140 defaultWindows=53140 quads=52406 submitted=52406 accepted=52406 refused=0`。
- **unicode 経路を初めて行使**: `lang:ja_jp` + `forceUnicodeFont:true` で `unicodeWindows=40548`・40000/40000/0。
- **advance は構造的に不変**（HEAD/RETURN 注入・戻り値書き換えなし）。画像でも title/toast/Done/unicode toast/完了 が pixel 一致。
- 4カテゴリ（ASCII+影・bold+italic・unicode・chat）の crop と sheet を保存。

**未証明（担当が明記）**: inventory/JEI の bridge 対照が**fixture の制約で存在しない**（tick 30 で開き 190 で閉じ、capture は ≥200）、
unicode 経路での §l/§o 未行使、GL-less の GUI に Tessellator 幾何（panel/button・chat 背景・hotbar）がまだ無い、
font texture mirror 自身の census 未取得。

## 0.142 **A03 修正の実測確認と、現在ツリーの健全性（baseline run）**

`baseline686`（GL-less allBlocks・A02〜A05＋所有権修正＋N10 を含むツリー）:

```
fixtureStatus = UNCALIBRATED_PRESENTED_IMAGE   validationClean = true   msgs = 0
worldFrameNonBackgroundFraction = 0.852727     presentedDistinctColours = 24618
presentedImageMeanAtCapture = 44.890625        presentedImageVerdict = UNCALIBRATED_PRESENTED_IMAGE
sceneCalibrationKeyMatches = false             sceneGateFloorsEnforced = false
floorsNotCalibrated = "FLOORS NOT CALIBRATED for scene terrain (overworld (dimension 0), no transfer requested): …"
```

**A03 の修正が端から端まで効いている**: 候補は `allBlocks=true` で走っており、参照は allBlocks なしで測定されている。
修正前は参照キーが候補に追随して `keyMatches=true` になり、**その variant を記述していない床で判定**されていた。
現在は `false` → **未較正として報告**（床を当てない）。これが監査の要求した形。

**正直な帰結**: allBlocks variant の床は（参照が無いので）**今は誰も判定していない**。allBlocks にゲートを掛けたいなら、
その variant 自身の参照 run を取って較正する必要がある——「別 variant の床で判定する」より厳密に正しい状態。

**画像は健全**: `nonBg 0.852727`（これまでで最も高い＝地形が豊かに見えている）・24618色・mean 44.89。

## 0.141 **監査 A03 修正: 参照較正キーが候補の property を読んでいた（ゲートの穴）**

監査の指摘どおりだった。`celtium$referenceCalibrationKey` は `celtium$calibrationKey(scene, referencePose, REFERENCE_GUI_SCALE)` を呼び、
その関数は **実行中 JVM の property**（`celtium.validation.worldLayers`・`blockSweep`・`allBlocks`・entity flags）を読む。
→ **参照 variant が候補に追随**し、`runKey.equals(referenceKey)` が常に成立しうる。監査の実例: 候補が `allBlocks=true` の run で、
参照（plain terrain で測定）まで allBlocks として報告されていた。**`sceneCalibrationKeyMatches=true` が意味を持たない**状態。

**修正**: 参照キーを**定数から構築**（`REFERENCE_STACK="default"`・`REFERENCE_BLOCK_SWEEP=0`・`REFERENCE_ALL_BLOCKS=false`・
`REFERENCE_ENTITIES="none"`・`REFERENCE_GUI_SCALE`）。候補側の既定 variant 文字列（`celtium$entityVariant()` は無指定で `"none"`、
stack は既定で `"default"`）と**同じ形**なので、候補が参照と同じ variant で走れば一致し、`allBlocks` など別 variant なら**未較正**になる
（監査が要求した「key 不一致を未較正として surface させる」形）。**失敗画像は FAILED のまま**で、ゲートは上書きしない。

**検査**: `scripts/cpu-contract/reference_key_check.py` — メソッド本体が live property を読まないこと、候補側の property を読む関数を
呼ばないことを検査する。**旧形に戻すと実際に落ちる**ことを確認済み（`the reference key calls celtium$calibrationKey, which reads the candidate's properties`）。
lint であることは隠さず明記（挙動版には fixture の Scene インスタンスが要る）。

## 0.140 **wedge の判別結果: pitch 符号ではなく「固定回転」／N10 の UBO 経路が入った**

### 1. 判別測定（私が実行・同一カメラ・lookDown=true / pitch +75）

```
frame=7 group=55 fmt=6 worldPass=true texture=115 ... ndc x[-42.46..0.00] y[-3.97..0.35] z[0.85..1.00] behind=1   <- 月
frame=8 group=62 fmt=6 worldPass=true texture=114 ... ndc x[-16.86..-16.86] y[-1.38..-1.38] behind=3             <- 太陽（画面外の一点）
frame=8 group=63 fmt=6 worldPass=true texture=115 ... ndc x[-13.69..0.00] y[-0.35..3.55] z[0.95..1.00] behind=1   <- 月
```

**pitch を反転しても月（texture=115）が画面を覆ったまま**で、太陽は画面外の一点のまま。
→ 担当が用意した判別基準どおり、**pitch 符号の反転ではなく固定回転の誤り**（sky の `rotate(-90,0,1,0)` か
celestial step の二重適用/欠落）。次は**別の world time** の run で切り分ける。

**確認済みの周辺事実**: mirror の `rotate`（`VulkanGlState:158-180`）は glRotatef の仕様どおりで**正しい**（列優先の全要素を検算）。
原典の sun/moon 部は `pushMatrix` → `color` → `rotate(-90,0,1,0)` → `rotate(celestialAngle*360,1,0,0)` → `begin(7, POSITION_TEX)`（`RenderGlobal:1402-1430`）。
同じ frame の terrain は正しいので、**mirror のカメラ部は正しく、sky の追加回転だけがずれている**。

### 2. N10（UBO 経路）受入 — `ac62e83dd`

担当が実装・実測。**color_convolve 192B・spider 144B**（監査の 180/140 と旧 196 は誤り。shader のコメントは `Saturation@192` だが**コンパイラは 188 を出す**——旧表はコメント由来だった）。
4本の UBO 変種＋descriptor ring と同一スロットのホスト可視 ring＋per-pass の経路選択＋**名前つき拒否**。shader 自身のオフセットと packer の報告を突き合わせるので、不一致は破壊ではなく拒否になる。

**同梱の実バグ修正**: `ShaderGroupMixin` の extras が 88 始まりだったのを **84** へ。
invert の `InverseAmount` は shader が 84 を宣言しているのに 88 に詰められ（native enderman が GL の 0.8 に対して 0.0 を読んでいた）、bits の Resolution/Saturation/MosaicSize も4バイトずれていた。

**監査記述の訂正2点**: (a) **この GPU は `maxPushConstantsSize=256`** を報告するので「両GPUとも128」は本機には当てはまらない（経路は guard 付き override で行使。担当はこれを emulation として明示）。
(b) **fixture の native vs GL 画像は別の surface を比較している**——chain が native で走る leg では Vulkan が window を提示し、GL-chain の leg の保存画像は stale な GL framebuffer。
したがって画素の主張はしない（presented vs presented の測定が必要）。

## 0.139 **wedge の根本原因を測定で特定: `renderSky` の月の quad が反転して描かれている**

wedge 担当が計器（`-Dceltium.vulkan.groupBounds=true` → `CELTIUM_GROUP_BOUNDS`）を作り、15 run の再測定で**原因を特定**した。

### 帰属（terrain のある画で再取得）

allow-list 掃引（frame-18・同一カメラ）: **only-6 が 20388 uniq・exactClear 18.0%・yellowgreen 4227・black-top-right 4603 ＝ wedge そのもの**。
他の format 単独（only-1/5/2/7/9）はいずれも wedge を再現しない（yellowgreen 813-1117・black 132 ＝ scene 自身のテクスチャ）。
**format 6（POSITION_TEX）単独で wedge が再現する**——terrain のある画での再確認。

### 原因（`CELTIUM_GROUP_BOUNDS` の実測）

```
frame=23 group=896 fmt=6 worldPass=true texture=114 proj=[m00=0.71 m11=1.26 m22=-1.00 m32=-0.05] ndc all behind  <- 太陽（RenderGlobal:1409, y=+100）
frame=23 group=897 fmt=6 worldPass=true texture=115 proj=[...] ndc x[-41.70..0.00] y[-3.96..0.35] z[0.85..1.00] behind=1  <- 月（:1425-1428, y=-100）
```

月 group の NDC footprint が**測定された artefact と一致**（x<0＝左半面 cols 0-622・y[-1..0.35]＝rows 0-718）。frame 内で画面を覆う bounds を持つ唯一の group。
vanilla は world time 6000 で太陽 +100・月 -100、カメラは 14° **見上げ**なので、太陽が手前・月が背後でなければならない——**実行は逆**。
両 group は別の頂点（u=0 と u=0.25＝sun.png と moon phases）を持つのでテクスチャ/形状の取り違えではなく、
**world-pass の sky group が持つ行列が vanilla のそれと違う**（同じ frame の terrain は正しい）。

**裏取り**: Nether travel（`dimension=-1 dimensionAtTick=80`）では yellowgreen=0・wedge なし（非 surface dimension では renderSky が sun/moon の前で return）。
bridge（実 GL が同じ2 quad を描く）にも wedge は無い。

### 次の一手

行列の出所は `captureGlMatrices`（`VulkanTerrainGpuBackend:7708`）の world-pass 枝（mirror の projection/modelview を使う）。
判別測定: 同一 jar で `-Dceltium.validation.lookDown=true`（pitch +75）。
- 太陽が画面を覆う側へ回れば **pitch 符号の反転**。
- 両方が動かなければ**固定回転**（sky の `rotate(-90,0,1,0)` や celestial step の二重適用/欠落）で、別時刻の run が切り分ける。

計器は**既定出力を変えない**（property 無しでは何も呼ばず何も出さない）。`enqAll`/`enqFormat` で `CELTIUM_IMMEDIATE_ENQ` を全 group へ広げられ、`frame=`/`group=`/`target=` を持つので番号付き dump と結び付く。

## 0.138 **決着: terrain 入口は生きている（1カウンタで確定）／GL-less allBlocks は PASSED に復帰**

§0.137 で残した「terrain はどの入口から描かれているのか」を、`renderBlockLayer` のカウンタで決めた。

```
CELTIUM_BLOCKLAYER calls=1 layer=Solid pass=2
CELTIUM_BLOCKLAYER calls=3 layer=Cutout pass=2
```

→ **`RenderGlobalMixin.renderBlockLayer` は実行されている**。Kirino の engine は Celeritas の chunk-layer 経路を（名前ベースの走査では見えなかった形で）通っている。
したがって**死んでいるのは entity/TESR の4注入だけ**で、terrain 経路は健全。名前で見つからなかった理由は未解明のままだが、
**「入口が生きているか」という問いには答えが出た**（推測を1カウンタで置き換えた形）。

**同じ run の画像**: `worldFrameNonBackgroundFraction=0.732916`・`presentedImageVerdict=PASSED`・`presentedDistinctColours=10048`。
＝**フック撤回後の GL-less allBlocks はゲートを通る**（退行前の水準に戻った）。

**残る死んだ注入（4）**: `RenderGlobal.renderEntities` ×2・`EntityRenderer.renderWorldPass` ×2。
Cleanroom 専用なので、re-target するか根拠つきで外すかを決める（§0.136/§0.137）。

## 0.137 **死んだ注入の全数掃引と、未解決の1点（terrain の入口）**

§0.136 の掃引を続けた結果。

### 死んでいる注入（確定）

| 注入先 | 数 | 根拠 |
|---|---|---|
| `RenderGlobal.renderEntities` | 2 | 呼び出し元は `EntityRenderer.renderWorld` 1400/1466 行のみで、その `renderWorld` を Kirino が置き換え |
| `EntityRenderer.renderWorldPass`（`func_175068_a`） | 2 | **Kirino のクラス群に `renderWorldPass` / `func_175068_a` の参照が0件**（jar 内 448 kirino クラスを走査） |

→ `RenderGlobalMixin` の4注入はこの runtime では一度も走らない。**「動いているつもりで一度も走らないフック」**。

### Kirino が `RenderGlobal` に実際に行う呼び出し（notch 名で確認）

`KirinoClientCore` 内の `MINECRAFT.g.*` は4種類のみ:
`renderSky(partialTicks, 2)`・`drawSelectionBox(...)`・4引数の雲系・2引数のもの。
**`renderBlockLayer` 相当（BlockRenderLayer を取る呼び出し）は見つかっていない**。

### 未解決（正直に）

**私たちの terrain はこの runtime でどの入口から描かれているのか**が未確定。
- 計測上は terrain は描かれている（`celtium/vulkan-check passes=1109 skipped=0 errors=0`、画にも床がある）。
- しかし Kirino が `renderBlockLayer` を呼ぶ形跡が見つからない。
- 可能性: (a) Kirino の `RENDER_OPAQUE` フェーズが Celeritas の経路を別名で叩いている、(b) 別の入口（`SimpleWorldRenderer` 経由）、(c) 私の走査が notch 名の取りこぼしをしている。

**次の計測（安い）**: `RenderGlobalMixin.renderBlockLayer`（114行）に**呼び出し回数カウンタ**を1つ足し、レポートに載せる。
0 なら入口が違うので実際の入口を特定する（Kirino の `RENDER_OPAQUE` 実装を読む）。1以上なら私たちの terrain 経路は生きており、
死んでいるのは entity/TESR の2経路だけ、と確定する。**推測で埋めず、この1カウンタで決める。**

## 0.136 **金脈続き: Kirino は `EntityRenderer.renderWorld` ごと置き換えており、私たちの `renderEntities` 注入2つは死んでいる**

`KirinoClientCore` をデコンパイルして判明した決定的な事実。

### 1. world render 全体が置き換えられている

`KirinoClientCore.EntityRenderer$renderWorld(long finishTimeNano)` が **`EntityRenderer.renderWorld` を置き換える**。中で Kirino は:

- `MethodHolder1.updateLightmap / setupCameraTransform / setupFog / getFOVModifier` を **delegate 経由で呼ぶ**（＝これらは実行される）
- 空を `MINECRAFT.g.a(partialTicks, 2)`（= `RenderGlobal.renderSky`）で描く。その直前に **`bus.F()`（loadIdentity）＋`Project.gluPerspective(...)` を2回**呼ぶ
- `cullingPatch.collectEntitiesInView(...)` で可視 entity を集める
- `entityRenderingPatch.renderEntities(...)` と `tesrRenderingPatch.renderTESRs(...)` を呼ぶ（**自前の実装**）

### 2. 私たちの注入は発火しない

`RenderGlobal.renderEntities` の呼び出し元は原典では **`EntityRenderer.renderWorld` の 1400 行と 1466 行だけ**。その `renderWorld` を Kirino が置き換えているため、
**`RenderGlobalMixin` の2つの注入（`sodium$renderTileEntities`＝Celeritas の TESR 描画、`renderEntities`＝Celeritas の entity ループ再実装）はこの runtime では一度も走らない。**

描画が成立しているのは、Kirino が **vanilla の下位 API（`RenderManager`・`TileEntityRendererDispatcher`）を直接呼ぶ**ためで、その描画は私たちの即時パス（BufferBuilder upload）が捕捉している。

### 3. これで説明がつくこと

- **TESR が「capture されない」**わけではない。Kirino が `TileEntityRendererDispatcher` を直接叩き、その draw は即時パスに乗る。Celeritas 側の TESR 経路は死んでいるだけ。
- **entity outline が発火しない**のも二重に確定: vanilla のループが走らない（Kirino が置き換え）＋ Kirino 自身は `outlineEntities` を捨てる（§0.135）。
- **空の描画は生きている**（Kirino が `RenderGlobal.renderSky` を呼ぶ）ので、`VertexBuffer.drawArrays` の no-op ギャップ（天球 gradient・星・地平面）は**実行経路の事実**として確定。
- **射影チャネル**: Kirino が `loadIdentity` ＋ `gluPerspective` を2回呼ぶ。mirror は loadIdentity を見て、gluPerspective を見ない。私の撤回したフックはこの2回も拾っていた。

### 4. 次の作業（この発見から出る具体項目）

1. **死んだ注入の扱いを決める**: Cleanroom 専用なので、`renderEntities` を狙う2注入は re-target するか根拠つきで外す。**「動いているつもりで一度も走らないフック」は最も危険な種類**。
2. **他の注入も同じ検査をする**: Kirino が置き換えたメソッド（`renderWorld` 本体と、そこから呼ばれていた経路）を狙っていないか全数確認する。
3. **outline は自前で native 実装**（§0.135 の結論を再確認）。`MinecraftCulling.entitiesInView` が対象集合を public で持つ。
4. **空/星/地平面**（`glDrawArrays`）の native 化。

## 0.135 **金脈: 実行時パッチ（Kirino）をデコンパイルして判明 — この runtime は entity outline を描いていない**

CFR（`/tmp/cfr.jar`、Maven Central から取得）で `cleanroom-0.6.13-alpha.jar` 内の Kirino クラスを Java に戻して読んだ。
`KirinoConfigHub.Runtime` は `enable = true`・**`enableRenderDelegate = true`**（既定）で、**Kirino の render delegate が world 描画を駆動している**。

### 1. `MinecraftEntityRendering.renderEntities(...)` は outline を集めて**捨てている**

```java
ArrayList<vg> outlineEntities = new ArrayList<vg>();
for (vg entity : this.cullingPatch.entitiesInView) {
    ...
    renderManager.a(entity, partialTicks, false);
    if (this.isEntityOutlineActive(entity, renderViewEntity, player, camera, gameSettings)) {
        outlineEntities.add(entity);          // ← 集める
    }
    ...
}
entityRenderer.h();                            // ← ここで終わり
```

**`outlineEntities` はその後どこからも使われない**——vanilla の `RenderGlobal.renderEntities` が行う
「outline framebuffer を bind し、`entityOutlineShader.render()` を呼び、戻す」処理が無い。

### 2. 裏取り（全数）

- `cleanroom-0.6.13-alpha.jar` 内に **"outline" を含むファイルは 0 件**。
- world render を置き換えている `KirinoClientCore` に `entityOutline*` / `loadShader` / `ShaderGroup` の参照は無し
  （`B.c("outline")` は **Profiler のセクション名**で、中身はブロック選択枠の描画）。
- Kirino は camera/projection も自分で駆動しており、**`Project.gluPerspective` を直接呼ぶ**（`KirinoClientCore` 160/165行）。

### 3. これが意味すること（戦略判断）

- glow シーンの `postChains=0`・`entityOutlineFramebuffer` が一度も bind されないのは、**私たちの renderer の欠陥ではなく、
  この runtime の patched render path が outline pass を実行していない**ため。glow 担当の「in-frustum の光る entity が居ないから」は**誤った説明**で、
  list は実際に作られ、ただ捨てられている。
- したがって監査 N04 の「GL-less outline が正しい画像であること」は、**この runtime のままでは観測不能**。
  選択肢は (a) Cleanroom 側の対応を待つ、(b) **私たちが outline pass を native で実装する**、(c) render delegate を切る（設定は見つかっていない・既定 true）。
- goal は「バニラの ShaderGroup・spectator 効果・**entity outline 等は本体機能であり、この延期を理由に省略しない**」と定めているので、
  **(b) が目標に沿う道**。`MinecraftCulling.entitiesInView` は public フィールドで、対象 entity の集合は読める。
  自前で silhouette → blur → composite を native に描けば、runtime が描かない glow を私たちが供給できる。
- **射影チャネルの再設計**にも効く: Kirino 自身が `Project.gluPerspective` を呼ぶため、私の撤回したフックは
  「vanilla の1回」ではなく「Kirino の複数回」も拾っていた。次に設計する時はこの経路を前提にする。

**方法の記録**: 実行時パッチは難読化名でコンパイルされているが、`scripts/mc-sources.sh --notch vg bxy brx` で
`notch-mcp.srg` から名前を引ける（`vg`=Entity・`bxy`=ICamera・`brx`=ChunkProviderClient・`bsb`=WorldClient）。
デコンパイラは `curl -sL https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar` で取得できる。

## 0.134 **フル監査（2026-10-11 10:58）の受領と処置**

監査は新規5件（P1 3・P2 2）と、所有権の穴3箇所、N01〜N17 の再監査、保持すべき改善の一覧を出した。

| 監査 | 内容 | 処置 |
|---|---|---|
| **A01 P1** | glyph の `memAddress` が flip 無し＝1つ先を指す（両分岐） | **修正済 `aa041c2d4`**（flip＋書いた範囲の検査）。CPU 実測つき（`delta=96`→flip 後 0）。担当へ「過去の実験は無効」と通知済み |
| **A02 P1** | bounded wait の timeout が無条件 finally で fence/buffer を破棄（VUID fence-01120 / commandBuffers-00047） | **修正済 `2fa82dac4`**: 3つの結果（submit 前失敗／完了／timeout）を分離。timeout は**隔離**し、context を unusable にして以後の submit を拒否、解放は `delete()` の device-wide wait 後のみ |
| **A03 P1** | `ValidationClient.referenceCalibrationKey` が **現在の候補**の property を読むため、参照 variant が候補に追随する | **未着手**（fixture）: 参照キーを hash 固定した参照 run の不変メタデータにし、「候補を変えても参照キーが変わらない」CPU 検査を足す。**失敗画像は FAILED のまま**（ゲートを上書きしない） |
| **A04 P2** | `requiredUploadBytes(MAX,MAX,direct)` が long 溢れで負を受容 | **修正済 `ae7801d5c`**（`multiplyExact`）。実測: 4x4=64 は不変・MAX×MAX は名前つき拒否 |
| **A05 P2** | watchdog が共有フラグを読み、古い watchdog が新しい wait を偽 stall にできる | **修正済 `f82d750f9`**（呼び出しごとの token＋完了 token、stall は token を名乗る） |

**所有権の穴（未着手・担当割当て待ち）**: `HostBuffer.create` の部分解放／`DynamicMesh` コンストラクタが2本目失敗時に1本目を漏らす／`buildModelMeshes` の catch が `built.put` 前の例外を回収しない。
**N17 の image fault は `vkCreateImage` 前**に発火するため部分画像解放の証拠にならない（staging 側は実証済み）。

**保持する改善（監査が明示）**: N01 の頂点ループ修正・layout token/ledger・per-mip upload と slice hash・幅の拒否・`COUNT` テーブル・ベンチの欠測拒否・Java21 配布検査（`verified=true errors=[]` class65 上限）。
**未行使**: foreign transition の abandon（unit 5/5 だが実 run で未発火）。

**画像は依然 FAILED**: `w9-only67` 巨大三角形/nonBg 0.364906・`iso-glow-after-a` 単色/nonBg 0。VUID0・exit0 でも `validationExit1`。
**固定ピン**: 正常 `d2073e9456da7bcc` vs 退行 `c1d4e7db53465129`（比較用）。

## 0.133 **原典の正体を確定**: tree は既にパッチ済みで、真の差分は Cleanroom 自身の実行時パッチ（Kirino）

「パッチ適用済みの deobf を既定にしろ」という指摘を受けて確定させた。

### 1. Cleanroom の MC パッチ＝私たちが既に当てているパッチ集

Cleanroom のリポジトリは `module/minecraft/patches/`（**539ファイル**、`.mappings.json` は `mcp:stable_39-1.12`）を持つ。
**これは Forge のパッチ集そのもの**（Cleanroom は Forge fork）で、私たちの RFG ビルドが既に適用している。実測で確認:

- `client/gui/FontRenderer.java.patch` は **逆適用（17/17 hunk が既に存在）**＝内容一致。
- `RenderGlobal.java.patch` / `GuiIngame.java.patch` の**追加行が tree に逐語で存在**（行番号ずれで patch は失敗するが内容は同じ）。

→ **MC 本体について1ファイルずつ「実行時と違うか」を確認する必要は無い**。tree はパッチ済み。
`scripts/mc-sources.sh --cleanroom` がこの事実とパッチ集の場所を報告する。

### 2. 真の差分: Cleanroom 自身の実行時パッチ（Kirino）

`cleanroom-0.6.13-alpha.jar` 内の **Kirino**（Cleanroom の描画エンジン）が**難読化名で MC に注入**する:

| クラス | 対象（notch→MCP） |
|---|---|
| `MinecraftCulling.collectEntitiesInView(vg, bxy, brx, float)` | `Entity`・`ICamera`・`ChunkProviderClient` |
| `MinecraftEntityRendering` | vanilla の entity ループ再実装（`countEntitiesTotal/Rendered/Hidden` を持つ） |
| `MinecraftTESRRendering.renderTESRs(vg, bxy, …, float, int)` | **TESR ループの置き換え** |

**毎 run 有効**（`latest.log`: `kirino_engine`/`kirino_ecs`/`kirino_gl` が初期化）。

**意味**: 「TESR の list は capture されていない」「glow の outline は in-frustum の光る entity が居るときだけ埋まる」は
**vanilla のコードについての結論**であり、実行時は Kirino の culling/entity/TESR ループが動いている。
`postChains=0`・`entityOutlineFramebuffer` が一度も bind されない件は **Kirino を読んでから**でないと説明にならない。
notch→MCP は `scripts/mc-sources.sh --notch vg bxy brx`（`notch-mcp.srg`）。

## 0.132 **P1 修正: glyph の頂点アドレスが「書いた最後の1バイトの先」を指していた**

監査（`CELTIUM_AUDIT_20261011_1058_URGENT_GLYPH_POINTER_CX`）の指摘を実測で確認し、`aa041c2d4` で修正した。

**バグ**: `CeltiumFontGlyphCapture.submit()` は `VERTEX` に 4×24 バイト書き込んだ後、`flip()` せずに
`MemoryUtil.memAddress(VERTEX)` を取っていた。**`memAddress(ByteBuffer)` は base + position を返す**ため、
position が capacity に達した状態では**書いたバイトの1つ先**を指し、backend は続く 4×24 バイト（別のデータ）を読んでいた。
隔離バッファ（4×16=64）も同一の欠陥。

**GPU 不要で実測**（`scripts/cpu-contract/GlyphAddressCheck.java` を追加）:

```
position=96 capacity=96
memAddress(buf) - memAddress(buf,0) = 96      ← position が加算される
after flip: position=0 limit=96 delta=0
first vertex x=1.0 expected 1.0               ← flip 後は先頭頂点が読める
```

**修正**: 両分岐で `flip()` してからアドレスを取り、**書いた範囲を検査**（合わなければ `address-extent` として拒否）。
これで将来の退行が「静かに間違ったポインタを submit する」ことはできない。

**なぜ文字が出なかったか**: Vulkan から見て範囲外ではない（拒否も VUID も出ない）ので**エラーにならず、単に違うバイトだった**。
したがって担当が行った mode/quads・テクスチャ隔離・画面充填の実験は**すべて同じ壊れたアドレスを共有**しており、
**有効なバイトで再実行するまで何も棄却できない**（次のフォント run）。

**同族バグの掃引（全数）**: 即時描画の主経路 `WorldVertexBufferUploaderMixin:144` は
**原典で安全と確認**——vanilla の `BufferBuilder.finishDrawing()`（`BufferBuilder.java:551-561`）が
`byteBuffer.position(0)` と `limit(getBufferSize()*4)` を設定してから upload するため、`memAddress` は base を返す。
したがって**「shard の原因も同じ」とは言えない**（監査の留保どおり）。他の `memAddress(buffer)` 使用箇所は
テクスチャ側（`VulkanGlTexture:2496` に同じ罠のコメントあり）と stub の内部バッファで、いずれも position を明示管理している。

## 0.131 **フック撤回後の実測**: terrain が戻り、glow シーンは黒率で落ちる

フック撤回（`f82bf4612`）後の現ツリーで測定:

| run | scene | nonBg | mean | distinct | verdict |
|---|---|---|---|---|---|
| `ab-noglu` | allBlocks | **0.838628** | 44.0 | 22706 | **PASSED** |
| `glow-after-revert` | allBlocks + glowEntities | **0.784304** | 155.41 | 49800 | FAILED |

`glow-after-revert` の失敗理由は具体的で、追跡可能:

```
FAILED: presentedBlackFraction=0.215696 is above the scene ceiling of 0.05
        for scene=terrain (overworld (dimension 0))
```

**21.6% が黒**——glow シーンで輪郭チェーンが入った状態の画が、黒い領域を大きく残している。ゲートは正しく働いている（床を下げない）。

**未確認**: この run の artifact に `postChains` / `postAdopted` / `offTargetPairs` / `chain-pair=` が**出ていない**（`logs/` にも console にも無し）。
計器は実装されているのに、この構成ではレポートへ届いていない——**「計器を作ったのに読めない」型**なので、次に閉じる。
そのため「glow 経路が発火したか」はまだ言えない。

## 0.130 **重大な誤りの訂正**: GL-less の terrain 欠落は 09:33 からの退行で、ゲートは正しく FAIL と言っていた

### 観測（wedge 担当の測定・時系列は artifact から再構成）

- GL-less の allBlocks 画は **09:16 までは床があった**（`p1-intel` nonBg=0.7346・`w3-base` 0.8226・`p1-model-fix` 0.7356）。
- **09:33 の `font` run 以降、すべての GL-less allBlocks run が nonBg=0.364906・背景 0xB3CFFF**（byte 単位で再現）。
- `-Dceltium.vulkan.immediate=false` で frame は**単色**、既定 run でも右下象限が**100% clear 色**＝**terrain が1ピクセルも描かれていない**。
- terrain backend 自体は動いている: `passes=1109 skipped=0 errors=0` だが **`lastDraw=8 lastSections=8`**（正常時 09:16 は `lastDraw=20 lastSections=23`）＝**可視セクションが 23→8 に減った**。
- bridge leg も同じ run で床を失っている（`wedge2-bridge` 右下 98.4% clear）＝GL-less 固有ではない。

### 原因候補（09:17〜09:30 の3コミットに限定）

| commit | 時刻 | 判定 |
|---|---|---|
| `b7b634179` unbound-framebuffer 拒否 | 09:17 | **除外**: `4642c145e` で revert 済み、その後も退行継続 |
| `30a30a375` 射影フック（`Project.gluPerspective`） | 09:27 | **有力**: 両 leg に効く。可視セクション数が変わったのは射影（frustum culling）が変わったことと整合 |
| `9655ff681` depth format | 09:30 | **AMD では無影響と実測**: `scripts/vulkan_depth_format_probe.py` で AMD は `D32_SFLOAT` を公開し、chooser の第一候補＝旧定数と同一 |

### **私の誤り（これが本項の主題）**

画像ゲートは 09:33 以降ずっと `sceneGateFloorsEnforced=true` / `sceneCalibrationKeyMatches=true` / **`presentedImageVerdict=FAILED_PRESENTED_IMAGE`** を出していた。
**ゲートは正しかった。** 私が「mean 213 の FAILED は固定 scene の正常状態だから counter を読め」と担当（texture 担当）に伝え、
自分の run でもその verdict を「較正/カメラの問題」として扱った。**失敗しているゲートを自分の解釈で上書きした**のが誤りで、
その結果 terrain 欠落を数時間見落とした。

**規律に追加**: 失敗しているゲートは、測定が誤りだと示すまで**証拠**である。verdict を「既知の正常」と言い換えて
counter だけを読む運用をしない。ゲートを上書きする判断をする場合は、**同じ fixture・同じカメラで 2 つの jar を比べる**
ような、カメラ差と renderer 差を分離する実験を先に取る。

### 判定（A/B 実測・確定）

同じ fixture・同じカメラ・同じフラグで **jar だけ**を変えた:

| | A: 退行前の固定 jar `d2073e9456da7bcc` | B: 現行 jar | C: 現行 jar・**フック撤回後** |
|---|---|---|---|
| `presentedNonBackgroundFraction` | **0.733183** | 0.364906 | **0.838628** |
| `presentedImageMeanAtCapture` | 213.6667 | 213.6667 | 44.0 |
| `presentedImageVerdict` | **PASSED** | FAILED | **PASSED** |
| `presentedDistinctColours` | - | - | 22706 |
| `lastDraw/lastSections` | 22/25 | 8/8 | - |

mean がビット一致＝**カメラは同一**。したがって **renderer の退行**で、原因は `30a30a375`（私の `Project.gluPerspective` フック）。
フックは culling が読む射影スタックに掛かるため、意図した「空の batch の射影」を直す代わりに **world を cull で消していた**（25→8 セクション）。
`f82bf4612` でフックを撤回し、床が戻った（C）。

**代償**: フックが対象にしていたギャップ（mirror が `Project.gluPerspective` を見ない＝空 fan・太陽/月・雲がカメラでない射影で描かれる）は戻る。
これは 09:16 の状態で、world が消えるより厳密に良い。**次にこのチャネルへ手を入れる時は、餌である culling を測る**——
`lastDraw/lastSections` を画像 counter と並べて report に載せ、試行ごとに A/B を取る。一行のフックで済ませない。

### 元の実験計画（記録）

同じ fixture（現在の固定カメラ）× 2 jar（09:16 の正常 jar `d2073e9456da7bcc` と現行）で nonBg と frame を比較する。
- A（正常 jar）で床が出て B（現行）で出ない → renderer の退行で、窓は上記3コミット。
- A でも空だけ → renderer は退行しておらず、**fixture のカメラが変わった**ことになる（その場合、私は正常な空の画を
  何時間も「壊れた画」として扱っていたことになる）。

## 0.129 **原典から確定したこと（デコンパイル済みソース）** — 推測で潰していた問いの決着

`.celtium-work/mc-sources/` の原典を引いた結果、**推測で何度も往復していた問いに直接の答えが出た**。以下はすべてソースの行番号つきで、
bytecode 読みや仮説ではない。

### 1. バニラの描画パス × 頂点形式の全表（`client/renderer` ほか）

| mode | format | 回数 | 私たちの id | 代表 |
|---|---|---|---|---|
| 7 QUADS | `POSITION_TEX` | 11 | **6** | 空・雲・水面・太陽/月（`RenderGlobal:1409`） |
| 7 QUADS | `POSITION_TEX_COLOR` | 5 | **2** | 天球の帯・天候（`RenderGlobal:1288`） |
| 7 QUADS | `POSITION_TEX_NORMAL` | 4 | **9** | 最終 composite |
| 5 STRIP | `POSITION_COLOR` | 4 | **1** | 天球 gradient の一部・線 |
| 7 QUADS | `POSITION_COLOR` | 3 | **1** | 汎用 |
| 7 QUADS | `PARTICLE…LMAP` | 3 | **5** | 粒子 |
| 3 LINE_STRIP | `POSITION_COLOR` | 3 | **1** | デバッグ線 |
| 7 QUADS | `POSITION_TEX_COLOR_NORMAL` | 2 | **8** | **fancy cloud（`RenderGlobal:1676`）**・XP orb（`RenderXPOrb:63`） |
| 7 QUADS | `POSITION` | 2 | **7** | 影・帯 |
| 7 QUADS | `ITEM` / `BLOCK` | 各2 | **4 / 3** | item・block |
| 1 LINES | `POSITION_COLOR` | 2 | **1** | 線 |
| 7 QUADS | **`POSITION_NORMAL`** | 1 | **未対応（id 0）** | **`Render.renderOffsetAABB`（`Render.java:297`）** |
| 6 FAN | `POSITION_COLOR` | 1 | **1** | **天球 gradient（`RenderGlobal:1383`）** |
| 5 STRIP | `POSITION_TEX_COLOR` | 1 | **2** | **font glyph（`FontRenderer:238`）** |

**確定した事実**: 天球の gradient は **mode 6 = TRIANGLE_FAN・POSITION_COLOR（id 1）**、太陽/月は **mode 7 QUADS・POSITION_TEX（id 6）**、
fancy cloud は **mode 7 QUADS・POSITION_TEX_COLOR_NORMAL（id 8）**。したがって pitch=-14（見上げた画）で上端に出る帯・三角の候補は
**天球 gradient（fan）・太陽/月（quads+tex）・雲（quads+tex+color+normal）**の3つに絞られる。format-8 帰属が撤回された理由づけもこれで明確になった
（雲は format 8 だが、同じ画には fan と tex のパスも写っている）。

### 2. **未対応のバニラ描画パスを1つ発見**: `POSITION_NORMAL`

`DefaultVertexFormats.POSITION_NORMAL` は uploader mixin の対応表（`celtium$formatId`）に**どの分岐も無く id 0 ＝拒否**になる。
唯一の利用元は `Render.renderOffsetAABB`（`Render.java:290-…`、`begin(7, POSITION_NORMAL)`）で、その呼び出し元は
`RenderEntity.doRender`（`RenderEntity.java:24`）——**専用レンダラを持たない entity の白い箱**である。
GL-less ではこの箱は描かれない（bridge では GL が描く）。id を1つ足す（`POSITION_NORMAL = 10`）＋対応表＋パイプラインで閉じる作業として記録する。

### 3. font glyph の意味的契約（原典）

`FontRenderer.renderDefaultChar`（230-248）: `bindTexture(locationFontTexture)` → `glBegin(5)` →
各頂点の直前に `glTexCoord2f`、頂点は `k = italic ? 1 : 0` と `f = charWidth[ch] - 0.01` を使い、**戻り値は `charWidth[ch]`**。
`renderUnicodeChar`（272-303）も同じ形。`drawChar`（223）が `unicodeFlag` で振り分ける。
→ capture が UV とテクスチャ identity を落としていれば「受理されるが絵が出ない」になる（担当の観測と一致）。

## 0.128 **開発環境の活用**: デコンパイル済みソースを手元に置き、`runClient` の位置づけを確定（2026-10-11）

CleanroomMC の ForgeDevEnv テンプレート（RFG 2.0.3・Java 25・mixin/coremod 対応の 1.12.2 mod 開発テンプレート）を確認した。
**ビルド構成は私たちの `build-1122` が既に同系統か上位**（RFG 2.0.4・mixinbooter 10.5・RetroFuturaBootstrap・access transformer・`runClient`/`runServer`）で、
テンプレートから取り込む価値があるのは次の2点だった。

**1. デコンパイル済み Minecraft ソース（活用開始）**

RFG は既に `forge122/build/rfg/mcp_patched_minecraft-sources.jar`（7561 ファイル）を生成していた。展開済み:

```
.celtium-work/mc-sources/net/minecraft/...        （Minecraft 2185 ファイル）
```

`client/gui/FontRenderer.java`・`client/shader/ShaderGroup.java`・`client/shader/Framebuffer.java`・
`client/renderer/RenderGlobal.java` を含む。**担当は署名と挙動を推測や bytecode 読みではなく原典で確認できる**——
監査が繰り返し指摘している「実物に当たっていない作業」を減らすための計器である。
すぐ効いた例: `FontRenderer.renderDefaultChar`（230-248行）は `glBegin(5)` の **4頂点ストリップ**で、
UV は各頂点前の `glTexCoord2f`、テクスチャは直前の `bindTexture(locationFontTexture)`、頂点は
`k = italic ? 1 : 0`・`f = charWidth[ch] - 0.01` を使い、**戻り値は `charWidth[ch]`**。
capture が UV とテクスチャ identity を取っていなければ、1 texel を引く「受理されるが絵が出ない」形になる——担当の観測と一致する。

**2. `runClient` は stock Forge の開発クライアントであって Cleanroom ではない（受入に使わない）**

`forge122:runClient`/`runServer` は RFG 既定（私たちの build に追加設定なし）で、動かすのは
**stock Forge 14.23.5.2847 の開発環境**であり、目標ランタイムの **Cleanroom 0.6.13-alpha ではない**。
したがって受入の証拠にはならない（受入は Prism 上の Cleanroom 実インスタンスで取る）。
探索・ログ確認・mod 読み込みの素早い反復には使えるが、その結果を Cleanroom の受入として引用しない。
assets の取得（`downloadAssets`）が必要で、初回はネットワークと時間を使う。

## 0.127 **統合**: 失われた担当4体の作業を保全し、監査バッチの現状を固定（2026-10-11）

4体の担当が同時に落ちた（最終報告の直後）。作業は**未commitのまま作業ツリーに残っていた**ため、コンパイルが通ることを確認したうえで単位ごとに保全した。

| commit | 内容 | 受入の状態 |
|---|---|---|
| `aa9a02801` | N02 の寿命契約（`VulkanDynamicMesh` 389行・`VulkanModelQuadCapture` 171行・GlStateManagerMixin の injector 2本）＋ `ModelListLifecycleProbe` | 担当の10ステップ probe 通過（baseline live=2/buffers=4 → add 3 → recompile 4・retired 1・live 不変 → delete 2 → id 再利用 3）。world-switch/reload と、私の retire 順序変更後の再測定は未 |
| `32528cccf` | N04 gate（`unsuppliedInput`）と N05 token（`commitPasses(int)`）＋ `ShaderGroupMixin` の guard | 担当の実測あり（bridge: `postChains=43 postAdopted=0 postRunsOnStandIn=43` → `postChains=0 postRunsOnStandIn=0`／fail-once: `postRepeatInits=1 postRecordingCommits=59 postStaleCommits=0`・frames 236 vs control 205・VUID 0）。**GL-less glow の end-to-end は未** |
| `6a52c4002` | texture batch（`VulkanGlTexture` 1042行・registry・CPU contract check・`ForeignLayoutLedgerTest`） | **担当の報告が届いていない**。diff を読み、N07/N08/N17 の受入を取るのが次の作業 |
| `9fea3dcc5` | font の glyph capture（mixin 2本＋`impl/font/`）＋監査文書 | 担当の実測: glyph 34000 提出/34000 受理/拒否0。**実画像受入は未**（live GUI frame で panel は出るが文字が見えない、が担当の最終観測） |

**glow の設計結論（3つの一行変更が全て実測で失敗した末の確定）**

| 試行 | commit | 結果 |
|---|---|---|
| identity 0（既定FB）の描画を拒否 | `b7b634179` | 絵が凍結（世界が届かない） |
| identity 0 を main 扱い | `25e617340` → `4642c145e` で撤回 | `presentedDistinctColours=1`（自身へのコピー） |
| composite が frame 自身の colour を sample する時 skip | 撤回済み | `presentedDistinctColours=1` |

**結論**: adoption 構成では identity 0 の composite は**フレームを届ける pass そのもの**であり、off-target pair は**ゲームが adopt した colour image**。world・composite・silhouette が同じ image を通るのは正当で、欠陥は**outline chain がその共有 image を自分の入力にしている**側にある。修正は chain 専用の image を与えること（`GllessFrameTarget` に第2の pair を追加し、identity 0 と outline の identity で振り分ける）。述語 `celtium$isScreenSelfComposite` は**使用禁止**として測定記録つきで残した。

**ハーネスの証拠整合（`51e75a14b`）**: `pictureFile_*` の stage 選択（`pictureStage` を報告）・presented-dump の PNG 実在検査（`pictureEvidenceProblem`）・`UNCALIBRATED_PRESENTED_IMAGE` を失敗経路にも合格経路にも畳まない（`fixtureUncalibrated` として報告）。

**現在稼働中の担当（4体）**: texture batch の受入（N07/N08/N17/N10）／font の実画像受入（N03）／glow の設計修正（chain 専用 image）／wedge の残存原因の診断。

## 0.125 **訂正**: bridge の「model draw 回帰」は証拠不足（カウンタが bridge レポートに存在しない）

- ベンチ担当の観測「最新ビルドの bridge が 60Hz フロアに落ち、`CELTIUM_MODEL_MATRIX` 行が一切出ない」を受け、
  私が「bridge で native model 描画が発火していない回帰を実測で確認」と報告したが、**これは証拠を超えていた**。
- 実測: bridge 構成のレポートには model 系カウンタが**元から含まれていない**。
  `p530-tp`・`ref-gl`・`glow-bridge3`・`bridge-model` のいずれも `modelDraws=` は absent、`modelMeshLists=` も absent。
  GL-less のレポートには含まれる（例 `modelMeshLists=2 modelQuads=12`）。
- したがって「行が出ない」は **「描いていない」と「記録していない」を区別できない**。回帰の有無は未確定であり、
  次に必要なのは bridge でも同じ計器をレポートへ載せること（フィールドの有無自体が構成差で説明できてしまう現状を直す）。
- これは「counter の不在を描画の不在と読まない」という §0.118 の規則と同じ種類の誤りである。記録として残す。

## 0.124 **受入**: Intel 4-leg（Nether 含む）／format 5 の拒否を修正・検証／wedge の根因は format 8（fancy cloud）

- **Intel 4-leg 受入（実測）**: `.celtium-work/intel-layout-evidence/leg{1,2,3,4}`、jar sha256
  `b63a8a85...`（renderer）・`74985a85...`（fixture）。
  - leg1 bridge terrain: PASSED・clean・0 VUID・band 平均 (102.561, 59.786, 54.992)
  - leg2 GL-less terrain: PASSED・clean・0 VUID・band 平均 (102.561, 59.785, 54.992) ＝ leg1 と ±0.001（present 経路の証拠）
  - leg3 **GL-less Nether（tick 80 で dimension -1）**: PASSED・clean・0 VUID・
    **09600 も 01198 も出ない**（= present layout 修正が Intel の再構築経路でも効いている）・client/world/server すべて -1・
    `dimensionFramesInTarget=114`・present 継続 failures=0
  - leg4 bridge glow: FAILED_PRESENTED_IMAGE だが**画像を開くとローディング画面**（"is under development" 表示・78.7% black）。
    AMD の同時 run `glow-bridge3` は同じ loading 画面でさらに暗い（90.4% black）＝ **Intel 固有ではなく scene のタイミング問題**。
    旧 jar の PASSED は gate が絵を判定していなかっただけで、旧 PNG の rows 300-720 は純黒。
  - **拡張/format/memory type/feature を名指すメッセージは 4 leg とも 0**。Intel の capability は externalMemoryFd/
    externalSemaphoreFd true・問い合わせた6 format すべて対応。depth format の要求が2つの診断で食い違う
    （capabilities `D24_UNORM_S8_UINT` vs `VulkanDeviceReport` の hardcode `D32_SFLOAT`）のは既存の未整理点。
- **format 5（粒子）修正・検証（commit `1e08cfd60`）**: 根因は `canBuildDescriptor` の無名 `return false`
  （`pipelineFor(lastUiPass)==null`、最初の terrain pass 前）。probe を描画経路と同じ規則
  （terrain pass があればそれ、無ければ `uiRenderPass()`）に揃え、throw 経路も理由を記録。
  検証: format 5 **73/73 受理**（従来 96/131）・probe 拒否 0（従来35）・**拒否 census が空**・fixture PASSED・clean・
  画像 verdict PASSED（distinct 28170・black 0.0029）。
- **wedge の根因（確定・修正済み `454fb3600`）**: 監査が**パッケージ済み jar のバイトコード**から特定した——
  `celtium$buildModelMeshes` が `mesh.nextVertex()` を **quad ごとに1回、4頂点ループの外**で呼び、
  その後に `putPosition`/`putUv` を4回呼んでいた。`nextVertex()` は頂点を開始してその index を返し、`put*` は
  開始済みの頂点に書く契約なので、**quad の4隅が1頂点を上書き**し、mesh は 4Q ではなく **Q 個の頂点**しか持たず、
  index は `base..base+3` を要求して**範囲外**を指していた。巨大な半透明 wedge・空の黒三角・筋は、
  **書かれていない頂点を rasterizer が結んでいた**もの。行列・topology・depth の議論はすべてこの下流だった。
  修正: `nextVertex()` を頂点ループ内へ移し（0,1,2 / 0,2,3 の三角分割は維持）、契約を**検査**として追加
  （各 quad の頂点が連続して開始されること、完成した mesh がちょうど 4Q 頂点・6Q index を持つこと。破れば描かずに拒否）。
  **検証（同一固定カメラ scene・GL-less allBlocks）**: fixture PASSED・clean・msgs 0・画像 verdict PASSED・
  `modelMeshLists=29 modelMeshQuads=160 modelMeshFailure=none`。修正前後で **36.9% の画素が変化**し、
  比較画像では wedge・空の黒三角・太い筋が消えてブロック壁と床が残る。細い斜めの筋は残っており、
  これは今回の修正とは別の、より小さい欠陥。
  なお担当が並行して見つけた「format 8（fancy cloud）が GL-less でのみ受理される」経路は、**別個の**
  GL-less 固有欠陥として残る（cloud shader を持たない native immediate pipeline が受理してしまう）。
- **注意（受入の前提）**: これらの run の後も `ValidationClient.java` と `scripts/prism_validation.py` が編集されている。
  次の受入は rebuild 後の jar で取ること。

## 0.123 **受入**: 条件 (c) 充足 —— 提示 frame が実際の window/compositor 出力に到達（marker 帰属 + 番号付き presented dump との画素完全一致）

- 文書: **`docs/compositor-output-capture-2026-10-11.md`**。これは `scripts/compositor_capture.py` の docstring が引用していた文書で、
  同スクリプトは本計測で**初めて実走**した（それまで capture 成果物も当該文書も存在しなかった）。
- **手法（renderer は無改変・すべて既存スイッチ）**: `-Dceltium.vulkan.presentMarker=true`（present を magenta (1,0,1,1) の全面 clear に置換、
  `VulkanSwapchain.java:85,704-720`）＋ `-Dceltium.vulkan.presentReadback=true` と
  `-Dceltium.vulkan.frameDumpDir/Every/Count`（**presented frame 番号を名前にした** `frame-<N>.png`）。
  compositor 側は `scripts/compositor_capture.py` の `grim -o HEADLESS-1`（連番 PNG ＋ シャッター時刻・window tree・output mode）。
  束縛の規則は「ある `frame-N.png` の書出し時刻を capture が跨ぐ」**かつ**「画素が完全一致」の両方。
- **marker run**（`.celtium-work/compositor-capture/marker2`、46 capture、画像 hash は黒と magenta の **2 種のみ**）:
  window 未 map の 32 capture は一様な黒 (0,0,0)・`windows=0`、window が map されている **14 capture（0031–0044）は 1280×720 全面が一様 (255,0,255)**
  （`firstMappedCaptureWall == firstMagentaCaptureWall == 08:58:21.866`）。この出力の唯一の client は
  `{"name":"Cleanroom","shell":"xwayland","pid":1716095,"visible":true,"rect":1280x720}`＝ゲームの X11 窓、output は HEADLESS-1 1280x720@60 scale 1。
  **capture 0035 は renderer の `frame-20.png` と 0/921600 画素差・最大チャネル差 0**（`frame-40` とも一致）。
  frame-20 の書出し 08:58:25.631 は capture 0034(08:58:24.867) と 0035(08:58:25.867) に挟まれる。
  renderer 側計器は `presentSucceeded=true presentAttempts=81 presentFailures=0 presentedFromVulkan=81 glSwapsSkipped=81`。
- **fixture の独立計測と一致**: marker run の fixture 自身が presented 画像を `presentedImageMeanAtCapture=170.0`・
  「921600 画素が単一色」と計測（`status=FAILED_PRESENTED_IMAGE` は marker を入れた**当然の結果**で欠陥ではない）。
  RGB 平均 170.0 = grim 側の一様 (255,0,255) と同値。**以前この行に書いた 191.25 は RGBA 4 チャネル平均**で、
  同じ magenta の別の平均の取り方（矛盾ではない）。
- **通常 run**（`.celtium-work/compositor-capture/scene`、69 capture、fixture **PASSED**・clean・msgs 0・`presentedImageVerdict=PASSED`）:
  **capture 0066 は `frame-100.png` と 0/921600 画素差で完全一致**（実 scene・10104 色。frame-100 の書出し 09:00:45.923 は capture 0065/0066 に挟まれる）。
  中間 frame（frame-20/40/60/80）との比較では 0.059–2.31 % の差が出る＝「どれも同じ絵」ではない。
  fixture の presented 画像（presentedFrame=102、`frameBinding="observed present: ...counter advanced by exactly one"`）とも
  957/921600 = **0.104 %** 差（frame 100→102 の粒子・アニメ分）。
- **対照**: GL screenshot は黒（presented と 99.925 % 差）＝ **GL framebuffer は証拠経路ではない**。
  window が一度も map しなかった run（検証 layer を `VK_LAYER_PATH` で渡さず renderer が
  `... layer VK_LAYER_KHRONOS_validation is not installed` でデバイス生成を拒否し world 読み込み中に crash）は
  91 capture すべて黒・`windows=0`。`grim -o HDMI-A-4` は**実 KMS 出力**（実セッション 1920×1080・9675 色）も読める
  ＝ headless backend 固有の成功ではない。
- **残る未測定は scanout そのものだけ**: `ffmpeg -f kmsgrab` は `Failed to open DRM device`（DRM master 無し・非 root）。
  grim は headless でも実 KMS でも「compositor が合成した出力バッファ」を読むので、実出力でゲームを走らせても scanout の gap は閉じない
  （実セッションを止めて `WLR_BACKENDS=drm` にするか、root/CAP_SYS_ADMIN が要る）。よって条件 (c) は**充足**、
  未測定は「その先の走査出力」のみで、これは方法の選択ではなく環境の権限・構成の限界。

## 0.122 **受入済み**: layout/publish バッチは Intel の両 leg でも成立

- `.celtium-work/runtime/intel-layout`（Intel UHD 630・`VK_ICD_FILENAMES` で pin・bridge 構成）:
  fixture **PASSED**・`validationClean=true`・**msgs 0**・`nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"`・
  `gpuRequirementSource=nativeDeviceName`・`gpuRequirementMet=true`・`nativeDeviceMatchedOpenGl=true`。
  これは GL-less present の layout 宣言修正・chain の submitted 契約・outline の publish 配線を含む現 HEAD の build で取ったもので、
  **AMD だけで成立していた batch が Intel でも退行しない**ことの実測。
- **GL-less leg も受入（実測）**: `.celtium-work/runtime/intel-layout-glless`（`VK_ICD_FILENAMES` で Intel に pin）が
  fixture **PASSED**・`validationClean=true`・**msgs 0**・`nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"`・
  `gpuRequirementSource=nativeDeviceName`・`gpuRequirementMet=true`・`presentedImageVerdict=**PASSED**`（distinct 1473・bands 496）。
  `nativeDeviceMatchedOpenGl=false` は stub 構成では正しい（GL identity が無いため UUID 照合はできない）。
  これで **layout/publish バッチは AMD と Intel の両方・両 leg で成立**した。
- 注意: この run の jar は他エージェントが同時に rebuild している期間のものなので、担当の報告で sha256 を確認してから
  最終受入として扱う（build が動いている間の測定は、どの build を測ったかを必ず添える）。

## 0.121 **実測・進行中**: 条件 (c) の捕獲手段は実在する（旧前提を訂正）／glow は検証 clean・実体ありの frame で白アウトラインを確認

- **条件 (c) の前提訂正（担当の実測）**: plan が長く持っていた「`DISPLAY=:0` の root が 0×0 で合成器なし」は**誤り**だった。
  実測では sway(pid 935) が `wayland-1`（`XDG_RUNTIME_DIR=/run/user/1000`）で稼働し、`HDMI-A-4 1920×1080@60` の実モニタ、
  Xwayland `:0 -rootless` があり、`ffmpeg -f x11grab -i :0.0` は 1920x1080 を読める。加えて harness の `--headless` は
  実 GPU の wlroots/Sway を所有しており（`WLR_BACKENDS=headless`・`WLR_HEADLESS_OUTPUTS=1`・output HEADLESS-1 1280x720）、
  **`grim -o HEADLESS-1` が別 run の実ゲーム窓を 1280x720 PNG として捕獲できた**（exit 0・642kB・6781色＝地形フレーム）。
  これは「環境制約で条件 (c) は測れない」という以前の判断が誤りだったことを意味する。
- **束縛手段**: `-Dceltium.vulkan.presentMarker=true`（present を magenta 全面 clear に置き換える既存スイッチ）と
  番号付き presented dump（`-Dceltium.vulkan.frameDumpDir/Every`）を併用すれば、compositor に出た frame を
  renderer 側の frame 番号に束縛できる。見つからないツール（Xvfb/weston/xwd/ImageMagick/kmsgrab 等）と、
  kmsgrab が DRM master 権限不足で不可であることも記録した。担当が marker run と通常 run を実行中。
- **glow（計器は揃ったが、絵はまだ未確認）**: `.celtium-work/runtime/glow2` は **validationClean=true・msgs 0**・
  `nonPlayerEntitiesAtCapture=9`（capture 時に実体が生きている）・
  `CELTIUM_EXTERNAL_IMAGE published glTexture=42 image=0x12b... owner=renderer`。presented frame は distinct 51388・
  nonBackgroundFraction 0.813。
  **撤回**: この frame を「実体に白いアウトラインが付いている」と記述したが、**誤読だった**。拡大（4倍・NEAREST）すると
  そこに写っているのは mob ではなく **wedge 欠陥の暗い三角シャードと青い半透明クワッド**で、白い輪郭は存在しない
  （「白い線」に見えたものは地形テクスチャのグリッド線と wedge の辺）。2279 個の near-white 画素のうち暗いシルエットに
  隣接するものは **1個**という数値もこの誤読を裏づける。**glow の可視は依然として未確認**で、判定は担当が取得中の
  bridge 参照との画素比較のみが根拠になる。
- **wedge の新しい観測**: 上記の拡大で、wedge は「青い半透明の大きなクワッド」だけでなく
  **黒い三角シャード**としても現れる（暗い三角形が地形・他の wedge を覆う）。担当の調査に渡す。

## 0.120 **実測**: 条件 (b) の現状プロファイル（標準 GL-less allBlocks）／次に埋める native 面は format 5

- run `.celtium-work/runtime/regr4-glless`（GL-less・allBlocks・fixture PASSED・validationClean・msgs 0・画像 verdict PASSED・
  distinct 10142）:
  - `celtium offer decisions: offers=5000 accepted=4705 glBodyCompleted=341` ＝ **94.1% が native で受理**。
  - per-format arrivals `[1:2254 2:634 5:126 6:946 7:860 9:180]` / accepted `[1:2280 2:640 5:92 6:948 7:860 9:180]`。
- **読み方（§0.118 の規則）**: GL-less では `glBodyCompleted=341` は「GL が描いた」ではなく stub の no-op 実行。
  条件 (b) の GL 残量は bridge 構成の拒否から読む。ここで意味を持つのは「native で描かれなかった量」。
- **次の native 面**: format 5（粒子）が **126 中 92 のみ受理**。拒否は stage 10 の descriptor gate
  （`canBuildDescriptor`）で、理由は 3条件（registry に texture が無い／lightmap が無い／mirror が full content でない）の
  いずれか。実測は `5 unrecorded (format=5 stride=28 mode=7 count=3932) stage=10` のような大きな粒子 batch。
  専用担当が原因特定と patch を作成中。
- 併せて確認: publish 配線と layout 修正は**標準場面で退行なし**（この run が clean・画像 verdict PASSED）。

## 0.119 **修正・受入済み**: GL-less の layout 宣言を2箇所で正した（Nether の VUID 解消）／画像ゲートが scene 非依存だった

- **VUID の正体（実測・エージェント）**: 「post chain が GENERAL 記述子で UNDEFINED を指す」という仮説は否定された。
  該当 run で chain は一度も走っておらず（専用計装が0行・report も `postChains=0`）、`VUID-vkCmdDraw-None-09600` は
  layer の **submit 時 first-layout 検査**（`vkCmdDraw` はハードコードされたラベル）。真因は GL-less の present で、
  `ensureFrameTarget()` が直前に target を再構築しうる（dimension/world 変更）ため **新しい colour image が UNDEFINED**
  なのに GENERAL を宣言していたこと。
- **修正1**: GL-less では target 自身の記録（fresh は UNDEFINED、提出済み frame が遷移済みなら GENERAL）を答える。
  bridge は従来どおり（GL 側が残す layout は別の問い）。
- **修正2（修正1が生んだ新しい VUID）**: 4引数 `presentImage` は「見つけた layout に戻す」ため、UNDEFINED を渡すと
  復元 barrier が `newLayout=UNDEFINED` になり `VUID-VkImageMemoryBarrier-newLayout-01198`。復元先を明示し
  （source 遷移は UNDEFINED から開始可・返却は GENERAL）、owner の記録にも反映する。GENERAL と言っても内容を主張しない
  （fresh image に内容は無く、次の frame の acquire barrier が描画可能な layout を与える）。
- **受入（実測）**: `.celtium-work/runtime/vuid-after2`（GL-less・`dimensionAtTick=80`・`dimension=-1`）が
  `validationClean=true`・**msgs 0**・capture 時 client/world/server すべて -1・`dimensionFramesInTarget=129`。
- **画像ゲートの欠陥（担当へ送付済み・未修正）**: 同じ run が `FAILED_PRESENTED_IMAGE` になったが、内容は
  **闇の Nether 洞窟**（distinct 605・blackFraction 0.037・nonBackgroundFraction 0.326・bands 720）を
  overworld 用の床で判定していた。frame identity・per-stage dump の配管は正常（`pictureEvidenceSource=presented-dump`）。
  指示: 床は scene ごとにし、参照 run と数値を明記する。参照未取得の scene は FAILED ではなく
  `floorsNotCalibrated` を報告する（「参照が無い」を FAILED と呼ぶと verdict が無視される習慣になる）。
  床を上げて通すことはしない。
- **chain の不変条件**: target を「初期化済み」とするのは barrier を載せた buffer が実際に submit された後
  （`commitPasses()`）。record 時点でフラグを立てるのは layout の嘘と同じ種類の誤り。

## 0.118 **修正・受入済み**: 監査バッチ（cancellation 契約・adoption 絞り込み・overlay 経路・GPU 報告）／TESR の本は描画される

- **cancellation 契約（監査 優先度1）**: `callList` の `@Inject` に `cancellable = true` が無く、`info.cancel()` が
  `CancellationException` を投げ、外側の catch が「計測失敗」として飲み、census は取消を数え、GL 本体は走っていた。
  修正後は cancellable を明示し、census は**取消が実際に効いた後**に書き、CancellationException は
  `CELTIUM_CANCEL_NOT_CANCELLABLE` として有界に報告し、`@At("RETURN")` の観測で「本当に走った GL 本体」を数える
  （HEAD 取消は RETURN の前に return するので、これが判定になる）。実測（bridge・パッケージ済み jar）:
  `CELTIUM_CANCEL_STATE cancelled=1 executed=8 failures=0`・not-cancellable 0 行・fixture PASSED・clean。
- **adoption の絞り込み（監査 優先度2 相当の欠陥）**: 「game が bind した任意の identity」を adopt していたため、
  spectator group が自分の4 target を adopt（`postTargets=0 postAdopted=4`）し、全 blur pass が1枚の image を
  読み書きしていた。mixin が報告する **chain の最初の入力**（`passes.get(0).groupMainFbId`）だけを adopt する。
  受入（`.celtium-work/runtime/outline5`・fixture PASSED・VUID 0）: `postChains=45 postPasses=180 postTargets=1
  postAdopted=1 postAdoptions=1 postRunsOnStandIn=0 postAdoptedIds=[64c7afc6]`、off-target で silhouette を
  受けた identity と一致。
- **overlay の経路**: GL-less では stage-3 の skip が「frame 自身の blit」と「別 texture を sample する overlay」を
  区別せず、outline composite が texture gate に到達する前に拒否されていた
  （`CELTIUM_COMPOSITE_REFUSED reason=unrecorded format=2 count=4`、`textureRefusalsBeforeAccept=0`）。
  現在の target 自身の colour texture を sample する場合だけ拒否し、それ以外は即時パスへ落とす
  （判定は game の bind が埋める identity map。stub は attachment query に答えられない）。
- **GPU 報告（監査 優先度5）**: 描画側が選択確定時に `CELTIUM_NATIVE_DEVICE name="..." uuid=... matchedOpenGl=...`
  を出し、harness はこれで `--require-gpu` を判定する（device 未選択時のみ GL 文字列へ fallback し、どちらで
  判定したかを記録）。`process.driver` は選択された device を UUID→name で照合して報告する。実測: GL-less で
  AMD が選ばれた run に `--require-gpu UHD` を掛けると `gpuRequirementMet=false
  gpuRequirementSource=nativeDeviceName nativeDeviceName="AMD Radeon RX 6400 (RADV NAVI24)"`。
- **TESR / エンチャント台の本（受入）**: `tesr7`（GL-less・`-Dceltium.validation.allBlocks=true`）で
  `modelDraws=3883 displayListCancelled=3883 displayListDistinct=29 modelMeshQuads=160`、本の list が
  `liveMVt=(6.012,-5.843,-29.475)`（カメラ原点ではなく実ブロック位置）で描画。GL 参照
  （`-Dceltium.vulkan.modelDraw=false` の bridge）と同一画素位置で一致（`.celtium-work/tesr-evidence/`）。
  **fixture の superflat world は生成ブロックに TileEntity を作らない**（`Chunk(World,ChunkPrimer,int,int)` は
  storageArrays のみ）ため、生成されたエンチャント台は GL でも本を出さない——これは vanilla の挙動。
- **未達として残るもの**: 可視 glow（registry への image 公開＋layout dance）、post chain 自身の target の
  `UNDEFINED` 参照 VUID（専用エージェントが調査中）、GL-less の一部 frame に出る半透明の大きな wedge
  （`modelDraws=0` の run でも出るため model 経路ではない）、画像受入の frame 束縛（監査 優先度4）、
  新 jar での Intel 再受入。

## 0.117 **受入済み**: Nether が capture 時に dimension -1 で native scene を描く（goal 項目4・条件 (a) の一部）

- **受入（実測）**: `.celtium-work/nether-fix4.json`（AMD RX 6400・GL-less・`--jvm-arg=-Dceltium.validation.dimensionAtTick=80
  --jvm-arg=-Dceltium.validation.dimension=-1`）が fixture **PASSED**。capture tick 200 で
  **client/world/server すべて -1**、`dimensionGate=none`、`dimensionFramesInTarget=122`（-1 で画面なしのまま描いた
  frame 数）、player はポータル外、`dimensionLeftTargetAtTick=-1`（0 に戻っていない）。
  presented PNG は distinct **16543**・non-background 0.594 で、netherrack・fire・obsidian のポータル枠という
  実 Nether scene（`.celtium-work/evidence-nether/nether-dimension-1-presented.png`）。
- **根因（実測・推論ではない）**: fixture 側。vanilla `Teleporter` の到着位置が**ポータルの中**で、その直後に
  fixture が `player.timeUntilPortal = 0` を設定していた——vanilla は `changeDimension` 直前に
  `getPortalCooldown()` を入れる。`Entity.onEntityUpdate` は portal block と重なる entity を
  `getMaxInPortalTime()` tick 後に -1 から 0 へ送る（creative では 1 tick、＝2 tick 接触）。
  これが「到着後に 0 へ戻る」の正体。約60 tick の遅れは server が
  `Teleporter.placeInExistingPortal/makePortal` で詰まっていた時間（trace に client が -1、server がまだ 63,10,0 と出る）。
- **fixture の修正**: vanilla 準拠の cooldown、到着位置の探索（13候補・ポータル外/通行可/ground 優先、全数報告）、
  140行の dimension trace（stdout・report・exit hook へ）、fail-closed な capture gate（到達＋client/world/server が
  目標次元＋画面なし＋ポータル外＋その次元で N frame 描画）、presented image の判定を capture の1 frame 後に
  行う修正（従来は 9.5 秒前の loading frame を判定していた）。
- **残る未達（fixture ではない）**: `validationClean=false` の原因は 1件の
  `VUID-vkCmdDraw-None-09600`（`VulkanPostChain` の記述子が `GENERAL` を宣言しているのに image が `UNDEFINED`）。
  GL-less かつ dimension 変更の run にのみ出て、GL-less で dimension 変更なしの run、GL-bridge の dimension run は
  いずれも 0件。chain 自身が確保した target の初期化 barrier の欠落/無効が疑わしく、専用エージェントで修正中。
  この1件が解消するまで Nether は「絵は受入・validation は未受入」として扱う。
- **注意（他 run への影響）**: GL-less の presented-image 判定が capture の1 frame 後になったため、
  dimension 変更のない GL-less run（p559/p561 相当）も判定対象 frame が変わる。
  `-Dceltium.validation.presentedImageAtCapture=true` で旧挙動に戻せる（A/B 用）。
  また renderer の presented-frame counter は dimension 変更で frame target と共にリセットされる（実測 high-water 37 → 0）。
  dimension run の frame 数主張はその窓に限定し、全体の会計（37+323=360 / worldFrames 362）を記録する。

## 0.116 **修正・受入済み**: lightmap の黒は announcement の配列形状だった（producer/consumer 契約）／model の平行移動がミラーに届いていなかった

- **根因（実測）**: `VulkanTextureRegistry.announceFullPixels`/`toRgba` の契約は `int[mipLevel][flat texel]` で
  `data[0]` が level 0 全体。producer の `VulkanTerrainGpuBackend.announceDynamicTexture` が flat な `int[]` を
  **1行1配列**に分割していたため、`data[0]` は1行だけになり、それ以降の texel は範囲外として透明で書かれていた。
  実測（16x16・5 level・announcement のみを供給源）: 修正前 level 0 は 256 texel 中 16 のみ
  （row 0 = (14,14,14,255)、rows 1..15 = 黒）、各 level は前段の1/4。地形は vertex の sky 行をサンプルするので
  row 15 が黒いと light 項が 0 になる。修正後 level 0 は 1024/1024 バイト・min 14。
- **受入（実測）**: 「readback 経由」と呼ばれていた回避策（GL-less では実際には stub が捕捉した CPU 側ピクセルを読む経路で、GL readback ではない）を撤去し、lightmap を announcement 経路のみで供給した bridge run
  `.celtium-work/runtime/ann-acc3` が fixture PASSED・validationClean・msgs 0・**VUID 0**・
  band 平均 (102.4, 63.9, 58.5)・1385 色。回避策なしで（GL-less では CPU 捕捉ピクセル経由の）経路と同一の絵になることを確認した
  （画像の所有者は1つに戻した）。
- **model の平行移動**: `GlStateManagerMixin` は double 版 translate（`func_179137_b` → `glTranslated`）しか
  ミラーしておらず、ゲームが使う float 版（`func_179109_b` → `glTranslatef`）は GL-less stub が no-op として
  捨てていた。実測: 59 frame の run で `translateCalls=59`（＝カメラ1回のみ）、全 model list が
  `compileT=(0,0,0)`。フック追加後は tile entity の list が実ブロック位置
  `compileT=(-14.0,0.614,-31.022)` / `liveT=(-14.0,-0.84,-30.659)` を持つ。**絵の受入は発見エージェントが実行中**で、
  この section では主張しない。double 版 scale も同理由でフックした。
- **Intel 受入（実測）**: UHD 630 で両 leg PASSED・clean・VUID 0。GL-bridge band (119.4, 86.1, 80.5)（AMD は
  (102.4, 63.9, 58.5)、差は HUD 被覆で terrain の色集合は同一）、GL-less は presented assertions PASSED・
  bands 411（AMD と同一）・AMD との行輝度相関 0.9995。**注意**: GL-less leg は `--render-node` では GPU を
  pin できない（GL identity が無いため最初の非CPU device=AMD が選ばれる）。実測は
  `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json` で pin した。harness の `--require-gpu` は
  stub の `glRenderer` 文字列を見るため GL-less では GPU を検証できない（既知の harness 欠陥）。
- **Nether（進展・未受入）**: 根因は fixture 側で、`Entity.onEntityUpdate` のポータル再突入だった
  （vanilla は `changeDimension` 直前に `timeUntilPortal` を設定するが fixture は 0 にしていた）。fixture は
  vanilla 準拠の cooldown と到着位置探索に直し、capture 時に client/world/provider すべて -1・
  `dimensionFramesInTarget=49`・画面なし・ポータル外、presented PNG は distinct 15428 の実 Nether scene。
  残る未達は presented-frame counter が dimension 変更で再作成される frame target と共にリセットされる件の
  会計1件で、エージェントが修正中。**旧 Nether PASSED は引き続き無効**。
- **未解決（新規・renderer 側）**: GL-less かつ dimension 変更の run に限り
  `vkQueueSubmit(): ... expects VK_IMAGE_LAYOUT_GENERAL -- instead, current layout is VK_IMAGE_LAYOUT_UNDEFINED`
  が 1件/run 出る（GL-less + dimension 変更なしの run、GL-bridge + dimension 変更の run はいずれも 0件）。
  frame target 再作成時に古い image 用の descriptor が新しい UNDEFINED image を指す経路が疑わしく、未修正。
- **アウトライン**: chain が空の stand-in target を確保していた（`postChains=34, postPasses=136, postTargets=2,
  postAdopted=0`、`CELTIUM_POST_ADOPT_REPORTED ... adopted=false`）。silhouette 自体は off-target image に
  描かれている（identity 0x79D2D96A に 35 提出・34 pass 描画）。本ラウンドで chain 側の adoption を配線した
  （`adoptChainFramebuffers`、識別子はゲームが実際に bind したものだけ）。**after 実測**
  （`.celtium-work/runtime/outline4`、fixture PASSED・clean・msgs 0）: `postChains=31 postPasses=124 postTargets=1
  postAdopted=1 postAdoptions=1 postRunsOnStandIn=0`（before は `postTargets=2 postAdopted=0 postAdoptions=0`）——
  chain は空の stand-in ではなく実シルエットを読む。registry への image 公開（composite が texture 42 を解決できる
  ようにする＝画面上の glow）は `VulkanGlTexture` の所有モデルを変えるため次段に分離。**glow の可視化は未受入**。

## 0.115 **達成**: GL-less window が実 scene を描画 —— 地形の黒は lightmap の供給経路だった（goal 項目6 / 条件 (b)）

- 対象は commit `ccae86911`。run は `.celtium-work/runtime/ver2-glless`（AMD RX 6400・`-Dceltium.vulkan.glless=true`・
  `-Dceltium.vulkan.presentReadback=true -Dceltium.validation.dumpPresentedImage=true`・headless・
  `-Dceltium.validation.minWorldFrames=40`）。
- **受入（実測）**: fixture `PASSED`・`validationClean=true`・VUID 0。提示画像の主張は
  `presentedDistinctColours=867`・`presentedVerticalBands=411`・`presentedImageAssertions=PASSED`。
  直前の run（`p559-glless`）は同主張が `presentedDistinctColours=2` で `FAILED_PRESENTED_IMAGE` だったので、
  これは「平坦な色ではない frame が窓に出ている」ことの実測であって、主張を緩めたのではない。
- **根因**: terrain の黒は lightmap の **供給経路**だった。描画側の各要素は単体では正しく見えた——
  アトラス標本だけ出力すると地形が出て（band 平均 120,87,81、参照 `p530-tp` は 112,79,74）、
  頂点色だけ出力すると白（`a_Color` は正常）、ライト項を白に固定すると絵が完全に戻る。
  ライトマップの画像自体は正しく（記述子が指す実オブジェクトの device 側 dump が noon グラデ・level 0 と mip 連鎖・
  `nonZero=1024/1024 min=14`）、それでも shader は黒を返していた。差は画像を埋めた経路で、ライトマップは
  registry の announcement 近道（`canSkipReadback`）を通り、正しく sample できていたアトラスは
  `uploadFromGlTexture` を通っていた。ライトマップをアトラスと同じ呼び出しに載せると絵が出る。
- **未解明として残す**: announcement 経路が「内容は正しいのに sample が黒い」image を残す理由は特定していない。
  回避ではなく経路の統一として `uploadFromGlTexture` に寄せたが、`canSkipReadback` を通る他の texture
  （animated sprite 等）に同じ状態が残っている可能性は未検証。次の計測は announcement 経路で level 0 だけを埋めた
  場合の mip 段（level 1..n）の内容と、その image を sample する descriptor の状態。
- **同時に直したもの**: 頂点 arena readback が slot ごとに region を覚えるようにした（単一 field を毎 draw が
  上書きしていたため復号した頂点は信用できず、発行していた copy が
  VUID-vkCmdCopyBuffer-srcOffset-00113 の 9 件/run そのものだった）。fixture は presented image を run 末尾でも
  測る（capture の dump は次の present が書くため、capture 直後に終了する run では書かれない）。backend は
  fixture の dump を 40 frame ごとに再要求し、ディスク上の画像が最近の frame であるようにした。
- **GL-bridge の退行なし**: `fix-bridge` は band 平均 (102.4,63.9,58.5)・1382 色・VUID 0・fixture PASSED。
- **未着手のまま残るもの（この section の scope 外）**: 表示リスト geometry は 2 list（腕・手持ち）しか
  取り込まれておらず、TESR の model（エンチャント台の本）は GL fallback に落ちて何も描かれない。
  entity/TESR/item/model の native 描画・照明・画質、FBO/postprocess、Nether、条件 (c) は未達のまま。

## 0.114 **部分達成**: GL-context-less window が起動・提示・terrain pass 開始まで到達（goal 項目6 / 条件 (b)）—— 窓の絵は未解決なので受入ではない

- 対象は commit `4bcff8de7`（owned frame target・float marshalling・texture capture・presented-image 主張）。
  引用する GL-less run はすべて AMD RX 6400（`logs/celtium-capabilities.json` の `vulkanDevices`）・flag 付き
  （`-Dceltium.vulkan.glless=true`）・headless。**既定は従来どおり GL-bridge**。
  詳細・再現コマンド・計測で見つけた5欠陥は `docs/history/2026-10-10-gl-less-bring-up.md`。
- **起動（実測）**: `logs/debug.log` に `CELTIUM_GLLESS stub GL provider installed`・`window hinted GLFW_NO_API`・
  `splash progress skipped (no shared GL context)` の3行（`p548-glless`〜`p559-glless` すべて）。fixture は loaded
  world まで到達し（`logs/celtium-validation.json` の `worldFrames`: `p549` 282・`p550` 191・`p559` 192）、
  `validationClean=true`・`validationMessageCount=0`（`validation-process.json`）。
- **GL 実体が無い（実測）**: `logs/celtium-capabilities.json` は `glRenderer="Celtium stub GL"`・`deviceUuids=[]`、
  `logs/celtium-vulkan-device.json` は `status=UNSUPPORTED_OR_UNAVAILABLE`・`logicalDeviceCreated=false`・
  `reasons=["The current OpenGL context does not expose device UUIDs ..."]`。bridge の前提（GL device UUID と
  external memory/semaphore FD）はこの経路には無い。`CELTIUM_GROUPS` も `gameFramebufferIsShared=false`・
  `gameFramebufferAdopted=0`・`glless target: no handoff` で、フレームに shared image が入っていない
  （GL-bridge の `reg-bridge3` は `gameFramebufferIsShared=true`・`adopted=1`）。
- **1フレーム1提示・GL swap は1対1で skip（実測）**: `p548-glless` の `CELTIUM_GROUPS` 行（21:51:13）で
  `presentedFromVulkan=287`・`glSwapsSkipped=287`・`presentFailures=0`（最終行は 288/288。commit message が引く 287 は
  その1つ前の行）。`p549` 281/281・`p550` 190/190・`p551` 275/275・`p558` 167/167・`p559` 191/191 も同値。
  提示先は `rendererDebug` の `swapchain: platform=x11 format=44 colourSpace=0 presentMode=1 images=4
  size=1280x720`、結果は `presentResult=[present image: blitted 1280x720 onto image N (0)]`。
- **clear が game の空の色として届く（実測）**: `p548-glless/screenshots/celtium-presented.png` は `#FFFF00`
  62.5 % + 黒 37.5 %（float の取り違えが残っていた run。その次の `p549-glless` で色が変わる）。`p549-glless`/`p550-glless`
  は `#B3CFFF` 62.5 % + 黒 37.5 %。`#B3CFFF` は game が `glClearColor` に渡す fog/sky 色（`EntityRenderer.updateFogColor`
  の最後の `GlStateManager.clearColor(fogColorRed, fogColorGreen, fogColorBlue, 0.0F)`）。修正は `CeltiumGlStub` の
  per-signature CIF（旧: 8 pointer の CIF が全関数を兼ね、float が SSE ではなく general-purpose から読まれていた）。
- **driver 無しの texture 供給（実測）**: `CELTIUM_GROUPS` の `lightmapSuppliedOnDemand=1`・
  `lightmapSupplyFailure=[none]`、`rendererDebug` の `block atlas: gl texture 12 512x512`・
  `lightmap: gl texture 15 16x16`・`pipeline for solid created [vertex input stride=32
  module=terrain_solid_vanilla.vert ...]`・`firstDraw region=8 pass=solid stride=32 ... elements=16896 ...`。
  読み戻しは無い（`readbackByOrigin=none`・`partialMirrorRefusals=0`）—— mirror は stub が保持した upload と
  game の announce から埋まる（`p549`: `imageFilledMirrors=1360`・`directMirrorFromAnnouncement=633`・
  `announcedUploads=8`・`dynamicTextureFills=886`）。geometry は record=submit=complete で閉じる
  （`p549`: `immediateRecordedBatches=7403`＝`immediateSubmittedBatches`・`immediateCompletedBatches=7402`・
  `invalidImmediateRecords=0`・`celtium/vulkan-check passes=1759 skipped=0 errors=0`）。
- **fixture が fail-closed（実測）**: GL-less のときだけ `celtium$checkPresentedImage` が提示画像
  （distinct colours・non-background fraction・vertical bands）と frame 数を検査し、絵でなければ PASSED にしない
  （`validation-process.json` の `fixtureStatus=FAILED_PRESENTED_IMAGE`）。
- **未解決（受入ではない）**: **窓の絵はまだ絵ではない**。`p559-glless` は `presentedDistinctColours=2`・
  `presentedNonBackgroundFraction=0.0`・`presentedVerticalBands=15`・`presentedBackgroundColour=0x9DB5E0`・
  `presentedImageAssertions=FAILED: ... the window is a clear colour plus at most a quad or two, not a frame`・
  `fixtureStatus=FAILED_PRESENTED_IMAGE`（`p558-glless` も FAILED、distinct=2・bands=17・bg `0x96ADD6`）。
  capture 時の `presentedImageMeanAtCapture=213.6666717529297` は sky 色 `#B3CFFF` の RGB 平均そのもので、
  一様な空色フレームと一致する。`p559-glless/screenshots/frame-120.png`（owned target の dump）は空色の上に
  大きな灰色 wedge と**全幅の黒帯（rows 366-635）**で、地形は連続した面ではない。同じ world の GL-bridge 参照
  `p530-tp/screenshots/celtium-validation.png` は連続した checkerboard の地形面。commit message は
  「terrain は fragments と wedges・void plane が全幅の帯として透ける」と記録する。
- **証拠の限界（隠さない）**: 提示 PNG は tick 20 で要求した one-shot の dump なので capture 時点のフレームではない
  （`p559` の `presentedImagePngAgeMs=8566`）。色統計はその早期フレームのもので、capture 時の状態は mean
  （一様 sky 色の平均と一致）と owned target の frame dump が示す。
- **harness verdict は PASSED ではない**: GL-less run は `exitCode=0` だが `validationExitCode=1`・
  `gpuRequirementMet=false`（run 時に `--require-gpu 'RX 6400'` を渡しており、GL renderer が stub なので harness は
  GPU を確認できない。`p557-glless` だけは timeout で `exitCode=143`・`validationExitCode=124`・`fixtureStatus=NO_CAPTURE`）。
  fixture が `PASSED`・clean の `p548`〜`p551` でも harness は 1 を返す。対照の `reg-bridge3` は
  fixture `PASSED`・`validationClean=true`・msgs 0・`worldFrames=142`・`validationExitCode=0`・
  `gpuRequirementMet=true` で、GL-bridge 既定経路の退行がないことを示す。
- **未検証のまま残るもの**: post chain はこの fixture で発火していない（`postChains=0`）ので seeded post chain の
  GL readback 経路は GL-less で未検証。Intel GPU での GL-less は未計測（引用 run はすべて AMD）。

## 0.111 **計測**: pinned Celeritas（989ebf2e）対 current Celtium の frame 時間（goal 項目8）

- 方法: 同一 harness・同一 fixture（標準 flat world）・同一 GPU（AMD RX 6400）で `frameIntervalP50Ms` を比較。
  **worldLayers は計測条件に明記必須** —— `worldLayers=3`（軽世界）と既定（重い・溶岩/火入り）で大きく違う。
- **既定世界**: baseline Celeritas **p50 = 16.32 ms**（p95 32.88 / p99 39.49）対 current Celtium **p50 = 28.28 ms**
  （p95 48.01 / p99 75.42）—— current は約 **1.7 倍遅い**。
- **軽世界（worldLayers=3）**: baseline **p50 = 16.05 ms** 対 current **p50 = 6.84 ms** —— current の方が **速い**。
- 解釈: baseline は両世界で **p50 ≈ 16 ms に固定**（60 fps の vsync/フレーム上限で cap —— p95 も ~17 で真の frame
  cost は不明、cap 以下）。current は cap 無し：軽世界では cap を下回る速さ（6.84 ms ≈ 146 fps）。既定世界での
  28.3 ms は描画量に比例する重さ —— mirror refresh は ~2/frame で非主因。差の主因は **dual-render 構造**：
  GL（未 capture の GUI/sky/等）と VK（地形 + capture 済み immediate + present blit）の両 pass が semaphore で
  直列化し、VK 側の描画量に応じて frame 時間が伸びる。軽世界では VK 側が小さく全体も小さい。GL 側を消す
  （goal 項目6）方向が、この overhead の構造的な出口。

## 0.113 **修正・受入済み**: fixed-pipeline 残 state を native 化 —— texture matrix・depthFunc・glColor tint（A12/A13）

- **texture matrix**：`matrixMode(5890)` が触る stack を unit 毎に分離（`setActiveTexture` hook 新設 —— 単一 stack 時代は
  lightmap の unit-1 matrix（scale 1/256,translate 8）が unit-0 に漏れる構造的漏れがあった）。group 生成時に
  unit-0 の 2D affine を capture → push の dead 領域（旧 `u_MvColumn` の 64-111B、全 immediate shader が未読）
  へ encode → 4 textured shader で適用。enchanted item の glint（scale 8+translate+rotate/frame）で
  `texMatrixGroups=150` 実測（AMD・Intel 104）。
- **depthFunc**：`func_179143_c` hook 新設（state model の field は ordinal 初期値で誰も書いていなかった）→
  captureState `state[7]` → 5 pipeline class に `depthCompareOp` param。glint の `GL_EQUAL`・held item/outline
  composite の `GL_ALWAYS` が正しく通る。
- **glColor tint**：色要素なし format（6/7/9）で `group.color` を capture、vert shader で `v_Color=u_Color`
  （combine 時は const 側残す分岐）。drawRect の tint が shader に届く。
- **fixture**: `enchantItem`（glint 発火）、`allBlocks`（registry sweep 244 block 壁、両 GPU で描画一致）。
- **A12 は実質解消**：残項（separate alpha factors・color mask・alpha func・scissor）は presented RGB で非可視
  または mod GUI scope と分析済。

## 0.112 **修正・受入済み**: texture-env の GL_COMBINE/interpolate（hurt flash・team outline）を native 化

- vanilla の hurt flash と glowing/team outline は、同じモデルを **2 度目に** `glTexEnvi` の `GL_COMBINE_RGB=GL_INTERPOLATE`
  で描く —— lightmap unit が texture 結果と env 定数（hurt=1,0,0,0.3）を定数 alpha で mix する。capture 済み list に
  この state は入らず、native draw は落としていた。
- 実装：`celtium$envCombineColor` が replay 時に GL の lightmap unit の `GL_COMBINE_RGB` と `GL_TEXTURE_ENV_COLOR` を読み、
  `GL_INTERPOLATE` なら RGBA を `ImmediateGroup.combineColor` へ。push block は **128 バイト満杯**のため、alpha は
  `u_AlphaRef` の**符号**（負=combine・絶対値=定数 alpha）に encode、定数 RGB は **未使用だった** `u_ColorR/G/B` slotへ。
  `immediate_position_tex.frag` は負の alphaRef で `mix(colour, const, |alphaRef|)` を行い alpha はそのまま
  （GL の COMBINE_ALPHA=REPLACE・SOURCE0=PREVIOUS と一致）。
- 実測：hurtEntities fixture（kept mobs に `hurtTime=10`）で `CELTIUM_ENV_COMBINE rgba=1.0,0.0,0.0,0.3` が発火、
  PASSED・clean・退行なし（p469）。**outline（`enableOutlineMode` の team 色）も同 channel で native になる**。
  glow outline（entityOutline FBO + silhouette composite）は post-process 構造上 spectator と同じく**動作確認済み**
  （fixture `glowEntities`、両経路で同一 silhouette+edge、残差は entity/particle のフレーム差）。

## 0.110 **修正・受入済み**: fence wait の非 TIMEOUT 失敗（DEVICE_LOST 等）でも frame を abandon —— post-submit device lost の実機注入を追加（goal 項目7）

- `waitForIdle` の分岐：`VK_TIMEOUT` だけが `abandonFrame` を呼び、他の非 SUCCESS（DEVICE_LOST 等）は `noteStalled`
  するが frame を開いたまま返していた —— timeout 分岐のコメントが説く「open frame の semaphore が来ない →
  GL write-back が待つ」デッドロック条件を残していた。修正：非 SUCCESS 全般で `abandonFrame` する。
- 注入：`-Dceltium.vulkan.failDeviceLost` で fence wait の結果を一度 `VK_ERROR_DEVICE_LOST` へ差し替える
  （submit が queue へ渡った後の device lost —— pre-submit の `failSubmit` では作れない経路）。`CELTIUM_DEVICE_LOST`
  行で発火を記録。
- 実測（AMD `p460-dlost`）：注入発火後 `stalled; no further drawing until the renderer is rebuilt` で後続 draw を
  拒否、presentAttempts も停止、run は **28 s で bounded 終了・exit 0・PASSED・clean**（修正前なら GL write-back
  で hang していた経路）。stalledTeardown は使用中 command buffer を reset/re-record/destroy しない既存規則のまま。

## 0.109 **修正・受入済み**: default-framebuffer 合成 quad（vignette/portal 等の overlay）を shared target へ —— format-2 の定常残量を解消

- 残量計測で format-2（POSITION_TEX_COLOR）が唯一の**増加する**拒否（`target: no frame target`、~40/周期）だった。
  計器を `ensureFrameTarget()` へ追加して特定：fbo==0 の default-FB composite（`framebufferRender` とは別、
  vignette/portal/pumpkin/hurt の overlay quad）は `supportedImmediateTarget` の redirect で shared target へ送られるが、
  直後の `ensureFrameTarget()` が **GL の bound framebuffer を再読**して fbo==0 → false、さらに
  `currentFramebufferViolation` も fbo==0 を "default framebuffer" として拒否 —— redirect が決めた描画を
  2段目で潰していた。
- 修正：redirect 成立時は ensureFrameTarget を `frameTarget.matches(targetW,targetH)` の確認に置き換え、
  violation 検査も skip（bound fbo=0 は redirect が正と決めた状態）。`framebufferRender` の self-blit は
  `samplesBoundFramebufferColor` で present path へ行きこの経路に来ないので feedback 問題なし。
- 結果：format-2 accept **2:0 → 2:170**、`no frame target` 拒否消滅、残り拒否は startup transient のみ
  （"no pass has run yet"、固定件数）。`defaultFramebufferCompositesTaken` が増加＝overlay が shared image に
  到達。AMD `p454-cmp` PASSED・clean。GL が fbo 0 へ描いていた overlay（Vulkan present では不可視だったもの）が
  提示画像へ乗るようになった。

## 0.108 **修正・受入済み**: model draw（表示リストの entity/手の native 描画）を既定 ON に（goal 項目5 の一部）

- `MODEL_DRAW`（`celtium.vulkan.modelDraw`）の既定を false → true。受入根拠：entity 場面（NoAI で固定した
  creeper/zombie/spider/enderman を視野錐 `-Z`・地表へ spawn、`captureEntities` で live frame 保持）で
  **AMD 0.35 %・Intel 0.16 %**、同場面の GL replay 基準（`modelDraw=false`）が **AMD 1.15 %** —— native が
  基準以下の残差（残差は移動粒子のフレーム差、§0.104 と同じ計測限界）。旧測定の 2.19 % が消えた理由は
  texture 所有修復（§0.106）が model の lightmap/atlas 供給も直したため。
- `celtium$drawModelList` は capture/build 失敗で `false` を返し、その場合は GL replay を cancel しない ——
  失敗時は旧 replay がそのまま走る安全側。flag は `-Dceltium.vulkan.modelDraw=false` で残す。
- fixture の spawn 位置を修正：`keepEntities` 場面の spawn を視野錐内（プレイヤーは yaw 180=−Z 向き）・地表へ
  移動（従来は player 周囲に散在させ大半が視野外だった）。`captureEntities`/`keepEntities` 併用で live frame の
  entity を計測可能に。
- 標準場面（entity なし）でも既定 ON で PASSED・clean —— 手/アイテムの list 2/3 が native 描画へ移行して退行なし。

## 0.107 **修正・受入済み**: immediate group の `Object[]` 添字契約を型付き `ImmediateGroup` へ（goal 項目3）

- `immediateGroups`/`recordedImmediateGroups`/`retiredImmediateGroups` の `Object[]`（15 slot・添字意味が 3 構築
  site のコメントにだけ存在）を **`ImmediateGroup`**（`ImmediateGroup.java`・8 引数 ctor＋named field）へ変換。
  `GroupStamp`・`PreparedImmediate` も同ファイルの package-private class へ集約した（契約が 1 ファイルに住む）。
  `group[12]`=batches、`group[7]`=target、`group[14]`=stamp のような添字参照は全て named field になった。
- `ImmediateGroupQueue.retireGroupsForTarget` は `List<ImmediateGroup>` を取る —— 「target 未帰属は残す」規則は
  `group == null` keep で保持（malformed 配列という概念自体が型で消えた）。
- `scripts/cpu-contract/TextureContractCheck` の A32 検査も型付きへ更新（`group(target)` ヘルパ＋null keep 検査）、
  `CELTIUM_CPU_CONTRACT PASS` を確認。AMD `p445-grp` 標準 fixture PASSED・clean で退行なし。

## 0.106 **修正・受入済み**: texture 所有の欠損を3つ閉じる —— lightmap の per-update readback 176→2、sprite patch の in-place 化、image/view/descriptor の per-update churn 解消

- **発見した3つの契約欠損（goal 項目2・texture 所有統合の内訳）**:
  1. **announce が `bound` を見ていた**: `func_110988_a`（`TextureUtil.uploadTexture`＝`DynamicTexture.updateDynamicTexture` の経路）は
     書き込む texture を**パラメータ**として持つのに、`announceDynamicTexture` はその時点の bound texture へ画素を aim していた。
     lightmap(id 12, 16x16) の画素は別 texture（1x1 の id 28 等）へ投げられるか拒否され、mirror は空のまま →
     `invalidateAll`（periodic blanket refresh）の cadence で **毎更新 readback**（run あたり ~176 回の 16x16 MIRROR）。
  2. **`onGlTextureReallocated` が同一サイズの内容更新でも mirror を forget**: `func_110988_a` のたびに
     `forgetTexture` → image/view/descriptor を per-update で破棄・再生成（`forgotten` ~176）。「全 upload＝再割当」の
     誤判定で、DynamicTexture の内容更新を真の identity 変更と混同していた。
  3. **所有状態の未同期**: `fromImage`（announced 全画像 → mirror 構築）が `fullyInitialized` を立てない → atlas の
     sprite patch が「未初期化への部分 patch」として拒否 → readback（`beforeInit` 818）。`consumeReadback` が
     `staleIds` を消さない → direct-path skip を記録しつつ registry `get()` が結局 readback（消せていなかった readback 流）。
- **修正（producer/consumer/invalidate/delete を同じ契約へ）**:
  - announce は **glTextureId パラメータ**を使う（bound ではなく）。`announceDynamicTexture(id, pixels[, w, h])`。
    `DynamicTextureMixin` は `getGlTextureId()`、`TextureUtilMixin` はメソッドの id+size パラメータを渡す。
  - `onGlTextureReallocated(id, w, h)` を**サイズ対応**に：同一サイズの再 upload は `invalidate`（stale マークのみ、
    announce が直後に in-place 更新）で、image/view/descriptor を保持。真の再割当（サイズ変更・未登録 id のみ）で
    `forgetTexture` + 直接参照を落とす。
  - `fromImage` が `markFullyInitialized` を呼ぶ（announced image は常に全面）→ 以降の patch は初期化済み image へ入る。
  - `consumeReadback` が `staleIds` を消す（consume＝「image は GL と同じ内容」の確定）＋ `get()` の stale refresh が
    `canSkipReadback` を先に見る → announce 済み write はどの経路でも readback しない。
  - `announceFullPixels` の fallback 生成は **DSA で対象 texture の size と GL mag filter を照会**（bound ではなく）→
    mirror は GL の filter に合わせて作る（lightmap は linear）。
- **受入（水アニメーション含む world）**:

  | GPU | 16x16 (lightmap) readback | atlas `beforeInit` patch 拒否 | `forgotten` | verdict / diff |
  |---|---|---|---|---|
  | AMD `p442-tex` | **2**（旧 ~176） | **18**（旧 818、初回窓のみ） | **0**（旧 ~176） | PASSED・clean・0.005 % |
  | Intel `p443-tex-intel` | **2** | **18** | **0** | PASSED・clean |

  `CELTIUM_DYNAMIC_ANNOUNCE texture=12 size=16x16`（正しい id+size、旧は bound=28 size=1x1 への誤 aim）、`full=` 全面
  announce が ~1900（lightmap 更新がすべて in-place に入る）。sprite patch も `patches` ~260→~966 で直接書き込まれる。
- **delete 半分も hook した**：`TextureUtil.func_147942_a`（`deleteTexture`、vanilla の全削除が通る唯一の funnel：
  TextureManager unload・Framebuffer teardown・`AbstractTexture.func_147631_c`）→ `onGlTextureReallocated(id)`
  （always-forget）。id 再使用が mirror の旧内容を引きずる穴を塞いだ（`p444-del` PASSED/clean）。
- **残る計測限界**：`glTexSubImage2D`/`glCopyTexSubImage2D` など TextureUtil を通らない直 GL 書込み、mod が
  `GlStateManager.deleteTexture` を直接呼ぶ経路は未 hook —— `invalidateAll` の periodic refresh が安全側で拾う。
  `full` announce 自体の upload/submit 実コストは `patchCostSummary` に出る。

## 0.105 **修正・受入済み**: Nether 受入 —— portal 帰還の解消（AXIS 直交 step）と、backend 再生成後の frame target デッドロック復元

- **旧 NO_CAPTURE の内訳は2つで、両方とも直した**:
  1. **portal 帰還**: fixture は到着後 `posZ + 2.0` で portal から外していたが、**portal の AXIS が Z** の場合その step は
     portal の面方向に滑るだけで中に残る → `portalCounter` が ~80 tick 溜まって vanilla が 0 へ送り返していた。
     **portal ブロックの AXIS を読み、面に直交する方向へ step**（AXIS=X→Z 方向、AXIS=Z→X 方向）。AMD/Intel とも
     到着後 -1 に留まった（p436 で帰還は消えたが描画が別原因で FAILED）。
  2. **frame target デッドロック**: 次元転移で world renderer／backend が**新しいインスタンス**に再生成される
     （`submitted=0`・`gameFramebufferIsShared=false`・`targetGeneration=0`）。`mc.framebuffer` の色 texture は
     **旧 backend 所有の共有 texture を指したまま**（旧 backend と共に死んで 0x0 と読まれる）→ 新 backend の
     `ensureFrameTarget` が bound attachment から寸法を読めず永久拒否（`no frame target for 0x0`、passes=0・
     skipped=537）。`adoptableFramebuffer` は `frameTarget != null` 前提で鶏卵に。
     **修正**: adopt 経路で `mc.framebuffer` の論理サイズ（正しい）へ**フォールバック**して target を復元し、
     adoption が死んだ texture を新しい共有 texture へ差し替える（`ensureFrameTarget()` は attachment 読み、
     新 overload は受取寸法で target を作る）。
- **受入（両GPU・`dimensionAtTick=80`・`dimension=-1`）**:

  | GPU | capture 時 dimension | passes / skipped | verdict |
  |---|---|---|---|
  | AMD `p437-nether` | **-1（到着後も留まる）** | **967 / 0** | PASSED・clean・VUID 0・実 Nether 画像 |
  | Intel `p438-nether-intel` | **-1** | **349 / 0** | PASSED・clean・VUID 0 |
- **退行確認（通常場面・AMD `p439-regress`）**: 提示 vs screenshot 0.002 %・PASSED・clean —— adopt 経路の変更は
  通常 run を壊さない（`ensureFrameTarget()` の attachment 読み経路は不変、fallback は attachment が読めない時だけ）。

## 0.104 **確定・受入済み**: entity 場面の「format 5 残差」は描画欠陥ではなく、**fixture のキャプチャ仕様**（生存粒子 vs 除去後スクリーンショット）

- **残差の正体（数値の内訳）**: entity 場面の 1.25〜1.62 %（AMD）/1.33 %（Intel）は、提示画像の**生存粒子が写る画素そのもの**だった。
  証拠: GL スクリーンショットの同領域に粒子画素は **0**、format 5 を受付から外すと **0.32 %**（=この場面のノイズ床、p426b で 0.33 % を実測）。
  fixture はキャプチャ待機ブロック内で **毎 tick `removeNonPlayerEntities`＋`effectRenderer.clearEffects`** を走らせており、
  **keepEntities の有無に関係なく**スクリーンショットは実体・粒子の無いフレームになる。対して提示ダンプは除去前（tick 198 等）のフレーム。
  「残差」は **存在/非存在の差** であり、描画値の差ではなかった。
- **format 5 の提出者・頂点データ（両方とも実粒子で正当）**:
  - 提出元スタック = **`ParticleManager.func_78874_a:371` ← `EntityRenderer.func_175068_a:1488`**（`CELTIUM_FMT5_SITE`）。
  - 頂点 = `pos/uv/color=0xffb26666/lmap=(0,176)`（`CELTIUM_FMT5_VERTEX`）。0xffb26666 は suspend 系粒子の色、
    `lmap=(sky=0, block=176)` = その位置の照度として正当（水中/遮蔽下）。
- **同フレーム実測（format 5 の正しさの直接検証）**: fixture に静止粒子の実比較を足した。
  - `particles=true` が FLAME ×24（速度0）を worldTicks 197 に置く（`py=9.6..11.4`、地表ガラスの直上・視線高さ）。静止ゆえに
    どのフレームでも同じ位置 → **提示ダンプとスクリーンショットが別フレームでも粒子比較ができる**。
  - AMD `p435-still197`（平地・粒子・dumpPresentedTick=199）: **両画像に FLAME が写る**（P 1515 px・S 1157 px・同一帯 y156-193）。
    差分 0.157 % の中身 = **同位置の粒子エッジ**で、色は両方オレンジ（P (252,151,0)・S (252,150,0)）。
    P の warm 画素が多いのは粒子が**若い**（`ParticleFlame` は経 tick でスプライト index を進めるので経年で絵が変わる）。
    **0.157 % = 経年・サブピクセルの差、描画値の誤りではない。**
- **fixture の新フラグ（実測用・既定は旧挙動）**:
  - `celtium.validation.captureEntities=true` — キャプチャ直前の `removeNonPlayerEntities`/`clearEffects` をスキップし、
    スクリーンショットに生存シーンを残す（`captureEntitiesKept`/`nonPlayerEntitiesAtCapture` を記録）。
  - `celtium.validation.dumpPresentedAtCapture=true` — キャプチャ呼出し直前に提示ダンプを要求し、次の present が書く
    （`presentedDumpRequestedAtCapture` を記録）。tick 指定版は「次の present」が終了間際では来ないことがあるので用途限定。
  - 粒子の生成 tick を 199→197、高さを空の中へ移動（`py=9.6+(i/6)*0.6`）。
- **結論**: **format 5 のパイプラインは正しい**（実粒子の位置・UV・色・ライトマップ式は block 経路と同じ検証済み計算、
  同フレームの静止粒子比較で残差は経年差のみ）。entity 場面の「残差」は比較系の時相ズレであり、**§5.2 はこれで閉じる**。
  なお「動く粒子の画素一致」はこの fixture では原理的に取れない（同フレームの GL 参照がない）—— これは計測限界であって欠陥ではない。

## 0.103 **修正・受入済み**: 提示 blit を GL composite の配置契約へ（resize 後の伸縮・上下反転を解消）

- **事実の整理（「提示画像が 1280x720 のまま」の内訳）**:
  - fixture の `mc.resize()` は**ゲームの framebuffer だけ**を 1024x640 にする。**実ウィンドウ（X11 surface）は 1280x720 のまま**
    （この harness は `glfwSetWindowSize` を無視する）。X11 では swapchain extent = `currentExtent` 固定なので、
    **提示画像が 1280x720 であること自体は正しい** —— 1024x640 の swapchain は作れない。
  - 実際の欠陥は2つ: (1) `presentImage` がフレームを swapchain 全面へ**伸縮 blit** していた。GL の composite は
    viewport `(0,0,dispW,dispH)` に **1:1・左下アンカー・window clip** で描く。(2) swapchain は 1280x720 ハードコードで作られ、
    再作成は**ドライバの out-of-date 報告頼み**だった（この compositor は報告しないので実 resize でも追随しなかった）。
  - **潜伏していた別欠陥**: shared image の texel row 0 は絵の下端（GL texture 順序）。それを 1:1 で swapchain に
    コピーしていたので、**実ウィンドウ上では絵が上下反転**していた（比較で反転が必要だったのはその症状）。
- **契約（1つ）**: swapchain 画像は「GL の composite が window framebuffer に置くもの」と同じ内容を持つ。
  = **正立・1:1・左下（絵座標で下端）アンカー・window で clip**。フレームが window より小さいときは viewport の位置に
  置き（伸縮しない）、大きいときは window が viewport を clip するのと同じく左下を切り出す。
  swapchain extent は surface の実効 extent（`currentExtent` 優先、未固定なら要求値を min/max に clamp）に追随し、
  **フレーム毎に1回 `matchesSurfaceExtent` で照合**してずれていれば即作り直す（OOD 報告を待たない）。
  作成要求は実 GLFW framebuffer サイズ（1280x720 固定を撤去）。`presented.png` はこれで**実ウィンドウの内容そのもの**（正立）になる。
- **比較作法の変更（重要）**: `celtium-presented.png` は**正立**になった。比較は **反転なし**で、
  drawable が window より小さい run では提示画像の**左下 `dw×dh` 領域**（この fixture では `[H-h:H, 0:w]`）を screenshot と比べる。
  旧「反転して比べる」は提示が反転していた名残で、以降の run には適用しない。
- **受入（resize fixture・両GPU）**:

  | GPU | 提示画像 | 描画領域 vs screenshot（反転なし） | stall | presentedFromVulkan | verdict |
  |---|---|---|---|---|---|
  | AMD (`p421-resize-blit`) | 1280x720（実 window） | **0.001 %**（1024x640 領域・上端帯は未描画=黑） | **0件** | 810 | PASSED・clean・VUID 0 |
  | Intel (`p423-resize-intel`) | 1280x720 | **0.000 %** | **0件** | 356 | PASSED・clean・VUID 0 |

  `presentResult=blitted 1024x640 onto image N`（伸縮ではなく領域 blit）・`CELTIUM_REPLACE_DROP batches=14`・`presentFailures=0`。
- **退行確認（resize なし・AMD `p422-noresize`）**: 提示画像 vs screenshot **反転なしで 0.000 %**（旧来の 0.001/0.01 % と同等）、
  提示 751・stall 0・PASSED・clean・VUID 0。**上下反転が要らなくなったこと自体が、旧提示が逆さだった証拠**。
- **残る論点**: window 外の帯は未描画（GL でも未定義領域。本 fixture では AMD 黑・Intel stale）。実ウィンドウでの正立表示は
  コード契約どおりだが、compositor 出力の実測は条件 (c) の未確認項目のまま。

## 0.102 **修正・受入済み**: フレームバッファ置換の**期間全体**で順序 flush を行わない（resize の恒久 stall が解消）

- **前節の一般化**: §0.101 は「bind の1箇所」だけを狙っていた。実際には置換の**期間中**に走る順序 flush がすべて同じ待機を持ち、
  bind を塞ぐと**次は `GlStateManager.clear` 経由の flush が同じ場所で失敗**した（実測: stall site が
  `celtium$noteRenderTarget` → **`celtium$clear:1314`** へ移動。提示は 342 のまま）。
  ＝ **両者に共通なのは「置換期間」**である。
- **実装（1契約）**:
  - `noteBindSize(w,h)` が**サイズ不一致を検出したら `framebufferReplacing = true`**（一点物ではなく**期間**にする）。
  - `celtium$ensureTargetForBoundFramebuffer()` が**共有 image を新サイズに用意できた時点で `false`** に戻す
    （bind の RETURN フックが毎フレーム呼ぶので、期間は必ず閉じる）。
  - `flushPendingBeforeGlDraw()` は**期間中は順序 flush を行わない**。保留グループは
    **件数を数えて捨てる**（`CELTIUM_REPLACE_DROP` と `postAcceptDroppedGroups`。既存の「描けないものを描いたと言わない」規則と同じ）。
  - `bindFramebuffer` の一点物フラグは撤去（期間が覆うため）。
- **受入（AMD・resize あり・120 s）**:

| 指標 | 修正前 | **修正後** |
|---|---|---|
| `CELTIUM_STALL` | 1件（恒久 stall） | **0件** |
| `presentedFromVulkan` | 265 / 331 / 342 | **843**（resize なしの 785 を上回る） |
| 提示画像の書き出し | **書かれない** | **書かれる** |
| verdict | PASSED | **PASSED**（clean・msgCount 0） |

  `CELTIUM_REPLACE_DROP batches=14 target=1280x720 frameId=364` ＝ 境界で捨てたのは**14バッチ**（名前をつけて数えている）。
- **退行確認（AMD・resize なし・120 s）**: 提示画像 vs スクリーンショット **0.001 / 0.01 %**（変更前と同一）、
  `presentedFromVulkan` **847**、exit 0 / clean / PASSED / msgCount 0。
- **新たに観測した未解決点（記録）**: resize run で **提示画像が 1280x720 のまま**（スクリーンショットは 1024x640）。
  ＝ **stall は解消したが、提示（swapchain 側）はまだ旧サイズの内容**である。次の焦点。
- **次の一手（1点）**: **swapchain の再作成がウィンドウの新サイズに追随しているか**を確認する
  （`ensureSwapchain()` の out-of-date 判定と、提示画像の寸法がどこで決まるか）。

## 0.101 修正点を確定: `bindFramebuffer` の**ターゲット切替時の flush**が resize で待機に失敗する

- **読んだ事実**: `bindFramebuffer(targetIdentity, isMain)` は
  ```
  if (!this.immediateWindowBusy && targetIdentity != this.currentRenderTarget) {
      flushPendingBeforeGlDraw();      // ← ここに待機がある
  }
  ```
  を持つ。resize で MC がフレームバッファを作り直すと **同一性（`System.identityHashCode`）が変わる**ため、
  この分岐が走り、**その中の待機が失敗して `noteStalled()` が立つ**（§0.100 のスタックと一致）。
- **修正点（次の実装単位・1つ）**: **サイズが変わった bind では flush を致命にしない**。
  - 具体的には、この分岐で「**フレームターゲットのサイズと、いまバインドされている FBO のサイズが食い違う**」場合
    （＝ resize の境界）は、**待機を伴う flush を行わずに**ターゲットを切り替える。
    直前のフレームバッファの内容は**この時点ですでに提示済み**で、その形は存在しないため、順序を守る対象がない。
  - 「settle できないときは資源を解放しない」という**既存契約はそのまま**（解放しない）。変えるのは
    **描画を止めないこと**だけ。
  - `noteStalled()` は計器として残す（次に同種の失敗が起きたとき site を言う）。
- **受入（実施時）**: resize run で **`CELTIUM_STALL` が出ない**、`presentedFromVulkan` が同 duration の
  resize なし水準（700 台）に戻る、提示画像が **1024x640** で書かれる。
- **現 HEAD の受入（変更なし）**: resize なし run は 0.001 / 0.01 %・提示 806・clean/PASSED を維持。

## 0.100 resize の最終原因: **bind フック内の `bindFramebuffer` の待機が失敗し、stalled が恒久化する**

- **計器**: `gpuStalled = true` の4箇所すべてを `noteStalled()` に置き換え、**一度だけ** site を stdout に出す。
  あわせて周期レポート行に **`presentSkipReason`** を載せた（post-resize の理由がどこにも出ていなかったため）。
- **結果（resize run）**:
  ```
  presentSkipReason の内訳: [no frame target, or stalled]=100, [presented]=78, [no completed frame waiting]=2
  CELTIUM_STALL site=net.minecraft.client.shader.Framebuffer.handler$...celtium$noteRenderTarget:553
               target=1024x640 frameId=245 frameTargetNull=false presentSkipReason=[presented]
  ```
  ＝ 最多の理由は **`no frame target, or stalled`** で、条件は `frameTarget == null || gpuStalled`。
  **フレームターゲットは存在する**（`frameTargetNull=false`）ので、**`gpuStalled` が真**である。
  そして **stall を立てたのは bind の HEAD フック**（`celtium$noteRenderTarget`）で、**resize のフレーム**（frameId=245,
  target=1024x640）に起きている。
- **連鎖（確定）**:
  1. resize で MC がフレームバッファを作り直す。
  2. ゲームがそれを bind → HEAD フックが `backend.bindFramebuffer(...)` を呼ぶ。
  3. **その中の待機が失敗**し `noteStalled()` が走る。
  4. `gpuStalled` は**設計上クリアされない**（使用中 command buffer の資源を守るため）ので、
     **以降 native 描画と提示がすべて止まる**（`presentedFromVulkan` が 355 で凍結、`glSwapsSkipped` が同数）。
- **意味**: §0.95 の「stalled が真因」は**正しく**、§0.96 の「呼び出し順だけ」という訂正は**片側だけ**だった。
  実体は **resize の bind 経路で待機が失敗し、恒久 stall に入る**ことである。
- **次の一手（1つの契約）**: **フレームバッファの作り直しに伴う待機を致命にしない**。
  サイズが変わった bind（＝世代交代）では、待機を**スキップまたは有界**にして stall を立てない。
  既存の「settle できないときは解放しない」契約は**そのまま**（解放はしない）が、**描画停止にはしない**。
- **受入（実施時）**: resize run で **`CELTIUM_STALL` が出ない**こと、
  `presentedFromVulkan` が同 duration の resize なし水準（700 台）に戻ること、提示画像が **1024x640** で書かれること。

## 0.99 **退行なし**（通常経路の再測）: 0.001 / 0.01 %・提示 806・clean/PASSED

- **変更が毎フレームの bind 経路に触れるため、resize なしの通常場面で再測した**（AMD・120 s・dump あり）:
  | 指標 | 変更前（p406） | **変更後（p414）** |
  |---|---|---|
  | 提示画像 vs スクリーンショット | 0.001 / 0.01 % | **0.001 / 0.01 %** |
  | `presentedFromVulkan` | 785 | **806** |
  | `gameFramebufferAdopted` | 1 | 1 |
  | verdict | PASSED | **PASSED**（clean・msgCount 0） |
  ＝ **通常経路は同一水準**で、**resize 経路だけが改善**（2.21 → 3.31 提示/秒）。
- **計器は1回だけ出る**（`afterBind size=1280x720 ensured=true adopted=7 currentFbo=1`）＝ ノイズなし。

## 0.98 **実装した**: adoption を bind の RETURN へ移し、その場でターゲットを実サイズに合わせる（機構は計器で確認）

- **変更（1単位）**:
  - `VulkanTerrainGpuBackend` に **`celtium$ensureTargetForBoundFramebuffer()`** を公開（`ensureFrameTarget()` をそのまま呼ぶ）。
  - `FramebufferMixin` の adoption を **bind の HEAD から RETURN へ移動**し、その注入から
    「まず ensure、次に adopt」の順で実行する。HEAD 側には**理由のコメント**を残した。
  - RETURN 注入に**サイズが変わったときだけ出す計器**（`CELTIUM_ADOPT_PROBE`）を入れた（毎フレーム bind するので件数制限では
    resize 前に使い切るため）。
- **計器の結果（resize run）**:
  ```
  afterBind size=1280x720 ensured=true adopted=7 currentFbo=1     <- 初回 adopt
  afterBind size=1024x640 ensured=true adopted=7 currentFbo=7     <- resize 後
  ```
  ＝ **resize 後も新サイズで ensure が通り、共有 FBO（7）が維持されている**。
  **リサイズ関連の拒否は消えた**（debug に残るのは起動時の `no frame target for 1280x720` 1件のみ）。
- **受入指標の訂正（自分の前節の条件が誤り）**: `gameFramebufferAdopted >= 2` を条件にしたが、
  **FBO 名が 7 のまま**なので**再 adopt は不要**で、このカウンタは 1 のままで**正しい**。
  正しい指標は「**resize 後も共有 FBO が維持され、提示が続くこと**」である。
- **実測（提示レート・duration で正規化）**:
  | run | duration | presentedFromVulkan | 毎秒 |
  |---|---|---|---|
  | resize 前の実装（p410） | 120 s | 265 | 2.21 |
  | **修正後（p413）** | 100 s | **331** | **3.31** |
  | resize なし（p406） | 120 s | 785 | 6.54 |

  ＝ **改善したが、resize なしの水準には戻っていない**（未解決として残す）。
- **受入**: p413/p412 とも exit 0 / **clean** / **PASSED** / msgCount 0。
- **次の一手（1点）**: resize 後に残る提示の目減りを、**提示経路の拒否理由ではなくフレーム供給側**で見る
  （resize で作り直された `Framebuffer` の**テクスチャの再確保**と、1フレームあたりのどこが重いかを計測する）。

## 0.97 修正手順の障害を特定: `ensureFrameTarget()` は**バインド中の FBO** を読むので HEAD では古いサイズを読む

- **読んだ事実**:
  - `celtium$adoptableFramebuffer(w,h)` は `frameTarget.adoptableFramebuffer(w,h)` へ委譲し、**サイズ不一致なら 0**（＝付け替えなし）。
  - `ensureFrameTarget()` は**そのとき GL にバインドされている FBO の色テクスチャ**からサイズを読んで作り直す。
  - `FramebufferMixin` のフックは `func_147610_a`（**bind**）の **HEAD** にある ＝ その時点でバインドされているのは
    **ゲームの新しい FBO ではなく、直前まで使っていた FBO**（MC は `func_147610_a` の中で `glBindFramebuffer` する）。
    ＝ **HEAD で `ensureFrameTarget()` を呼んでも旧サイズを読む**ので、この経路では直せない。
- **設計（次の実装単位・1つ）**: 同じ `FramebufferMixin` に **`func_147610_a` の RETURN を対象にした注入**を足す。
  - RETURN では**ゲームの新しい FBO がバインド済み**なので、バックエンドに
    **「今バインドされている FBO のサイズでターゲットを用意する」**入口（`ensureFrameTarget()` と同じ `settleGpu` 契約）を公開して呼ぶ。
  - その直後に**既存の adopt 判定**（`framebufferObject != adopted` なら付け替え）を同じ注入から実行する。
  - これで「**サイズ変更＝世代交代**」が bind 経路でも成立し、`gameFramebufferAdopted` が 2 以上になる。
- **なぜ HEAD のままでは駄目か（記録）**: HEAD でターゲットを作り直すと**旧サイズ**で作り直してしまい、
  かえって共有 image を 1280x720 に固定する。**順序（bind 完了後に読む）が本質**である。
- **受入（実施時）**: resize run で `gameFramebufferAdopted >= 2`、
  `presentedFromVulkan` が同 duration の plain 水準（700 台）へ戻り、提示画像が **1024x640** で書かれること。
- **受入（現 HEAD・変更なし）**: resize なし run は AMD/Intel とも clean/PASSED/VUID 0 を維持。

## 0.96 resize の機構を確定: **bind 時点で共有 image が旧サイズ**なので再 adopt が成立しない（前節の「stalled が真因」も訂正）

- **前節の訂正**: `rendererDebug` を見ると resize run でも **`target=1024x640`**、地形は **`draws=62 sections=62`** で
  plain run と同一。＝ **フレームターゲットも地形も新サイズへ追随している**（「旧サイズのまま」も「stalled で停止」も
  説明として不十分だった）。ログの `no frame target for 1280x720`（286 件）は **resize 前（tick<100）の分**である。
- **機構（コードとカウンタで確定）**:
  1. MC の resize は **同じ `Framebuffer` オブジェクトのテクスチャを作り直す**（`createFramebuffer`）。
  2. `FramebufferMixin` は `func_147610_a`（bind）の HEAD で
     `celtium$adoptableFramebuffer(self.framebufferWidth, self.framebufferHeight)` を呼び、
     `self.framebufferObject != adopted` なら**共有 image へ付け替える**（＝再 adopt の設計は**既にある**）。
  3. しかし **bind の時点ではフレームターゲット（共有 color/depth）はまだ 1280x720** で、
     新サイズへの再作成は**そのフレームの最初の offer**（＝bind より後）に行われる。
     したがって **`adoptableFramebuffer(1024,640)` は 0 を返し、付け替えが起きない**。
  4. 結果: `gameFramebufferAdopted=1` のまま（**一度も再 adopt されない**）、
     以降の present は共有 image から出せず **`presentedFromVulkan` は resize 前の 265 で止まる**（plain は 785）。
- **意味**: 欠陥は「サイズ追随の欠落」ではなく **呼び出し順**である。
  **adoption が要求するサイズでターゲットを作り直してから判定する**必要がある
  （＝「サイズ変更＝世代交代」を、**bind 時点**にも同じ契約で適用する）。
- **次の一手（1点）**: `adoptableFramebuffer(w,h)` に**要求サイズを渡して、必要ならその場で共有 image を作り直す**
  （既存の `ensureFrameTarget()` の再作成経路と同じ `settleGpu` 契約を使う）。その後に付け替える。
- **受入（実施時）**: resize run で `gameFramebufferAdopted` が **2 以上**になり、
  `presentedFromVulkan` が同 duration の plain 水準（700 台）に戻り、提示画像が **1024x640** で書かれること。

## 0.95 **resize の真因は「GPU stalled → 再構築されないまま描画停止」**（ログで確定）

- **前節の訂正**: ターゲットが「旧サイズのまま」なのではなく、**再作成が失敗して fail-closed のまま固まる**。
  `ensureFrameTarget()` は**バインド中の FBO のテクスチャから実サイズを読んで**、
  サイズが違えば**作り直す**（`settleGpu("frame target recreation")` を通してから）実装になっている。
  失敗時は**古いターゲットを解放せず、新しいものも作らない**（コメントどおり、使用中 VkFramebuffer を破棄しないため）。
  拒否メッセージが**旧サイズ**（1280x720）を出すのは、`targetWidth/Height` の更新が**成功時だけ**だからである。
- **真因（ログで確定）**: resize run にだけ次の行が繰り返し出る。
  ```
  GPU stalled; no further drawing until the renderer is rebuilt
  ```
  （同じ duration の resize なし run には**一度も出ない**）。＝ **resize が GPU の stalled を誘発**し、
  「**再構築されるまで描画しない**」設計のため、**その後は何も描かれない**。
  - 提示数 265 は **resize（tick 100）以前の分**とほぼ一致し、**以降はゼロ**。
  - `glSwapsSkipped=265` も同数で、以降は GL swap に戻っている。
- **意味**: resize 経路の欠陥は「サイズ追随」ではなく、**stalled からの再構築経路が無いこと**である。
  これは失敗時設計（fence timeout で解放しない・§0.37）と**同じ契約の裏側**で、
  「**再構築**」を明示的に持つ必要がある（goal の項目7: minimize/out-of-date の確認）。
- **次の一手（1点・読みのみ）**: **「renderer is rebuilt」が想定している再構築経路が既にあるか**を読む
  （初期化/teardown の対、`stalledStops` と `stalledTeardownSkips` の使われ方）。
  無ければ **resize を契機にした再構築**を 1 つの契約として実装する。
- **受入（現 HEAD・変更なし）**: resize なし run は AMD/Intel とも clean/PASSED/VUID 0 を維持。

## 0.94 **resize 後にフレームターゲットが旧サイズのまま**（提示が 265 対 785 に減る・ダンプが書かれない）

- **前節の「未説明」の答え**: `dumpPresentedImage` を**渡した再 run（p410）でも提示画像は書かれなかった**。
  ＝ フラグの渡し忘れではなく、**実際に書き出しが起きていない**（前節の推測は誤りだったので訂正する）。
- **計測（同一 duration の通常場面との比較・stdout の最終値）**:

| run | `presentedFromVulkan` | `glSwapsSkipped` | `gameFramebufferAdopted` |
|---|---|---|---|
| **resize あり（p410）** | **265** | 265 | 1 |
| resize なし（p406） | **785** | 784 | 1 |

  ＝ **resize 後は提示フレームが約 1/3 に落ちる**（残りは GL swap に戻っている）。
- **拒否理由に旧サイズが残る**: ログに **`no frame target for 1280x720`**（resize 後は 1024×640）が繰り返し出る。
  ＝ **フレームターゲット（と adoption 済みの共有 image）が resize に追随していない**。
  これが**提示画像の書き出しが書かれない理由**でもある（旧サイズのターゲットを待ち続ける）。
- **resize 自体は壊れていない**: p409/p410 とも exit 0 / **clean** / **PASSED** / msgCount 0、
  スクリーンショットは **1024×640**（＝ resize が Minecraft に届いた証拠）。**壊れているのは提示経路の追随**。
- **次の一手（1つの契約）**: **フレームバッファのサイズが変わったら adoption をやり直す**
  （共有 color/depth とフレームターゲットを新サイズで作り直し、`gameFramebufferIsShared` の判定も更新する）。
  「target/frame/generation を持つ」既存の契約に、**サイズ変更＝世代交代**を同じ単位で足す。
- **受入（実施時）**: resize run で `presentedFromVulkan` が resize なしの水準（同 duration で 700 台）に戻り、
  **提示画像が書かれ、1024×640 でスクリーンショットと比較できる**こと。

## 0.93 **format 5 の帰属を Intel でも確認** ＋ **冒頭表/goal.md の矛盾修正** ＋ **resize を AMD で受入**

- **Intel での確認（除外方向・entity 場面）**: `-Dceltium.vulkan.immediateFormats=12346789`（format 5 を除外）で
  **1.33 % → 0.36 %**（exit 0 / **clean** / **PASSED** / msgCount 0）。AMD は 1.25〜1.61 % → **0.32 %**。
  ＝ **format 5（粒子系）の native 描画という帰属が両GPUで確定**。
- **冒頭表と `docs/goal.md` の矛盾を修正**: どちらも「**Nether は両GPU PASSED（旧 NO_CAPTURE を置換）**」のままだったが、
  objective は「**旧 Nether PASSED は有効な受入から外す**」と明記している。両方を
  「**未成立**（一時的に -1 → GuiDownloadTerrain → 0 で NO_CAPTURE、根因未確定）」へ直した。
  あわせて冒頭表に **model 経路 2.19 %**・**entity 場面の format 5 帰属（両GPU）**・**通常場面の両GPU一致**の行を追加。
- **resize の受入（AMD・`-Dceltium.validation.resize=true`）**: exit 0 / **clean** / **PASSED** / msgCount 0。
  保存されたスクリーンショットが **1024×640**（既定 1280×720）＝ **resize が Minecraft に届いた証拠**。
  `presentedFromVulkan` は resizing 後も増え続けている（64→73→83→93 の途中経過）。
  - **未説明（記録）**: この run では**提示画像の書き出しファイルが残っていない**（`celtium-presented.png` が無い）。
    画像差の比較はこの run では**行えていない**。次の確認事項として残す。
- **意味**: 計画の項目7（reload/resize/world switch）のうち **resize が初めて受入**された。
  フレームターゲット側に resize/out-of-date の**明示処理は無い**が、この場面では破綻していない。

## 0.92 **`MIRROR_GL_MIPS` を撤去**（旧経路と lab flag を削除、AMD 2場面＋Intel 1場面で退行なし）

- **実施内容（17 編集・全参照を先に列挙してから同じ変更単位で）**:
  `VulkanGlTexture` の flag 宣言・accessor・`mirroredLevels` フィールドと3使用・
  `glLevels` とその4使用・ミラー取り込みブロック・`glLevelCount`・関連コメント2箇所・
  `for (level = 1; level < (MIRROR_GL_MIPS ? 1 : mipLevels))`・barrier の `oldLayout` 分岐・
  tail transition 条件、`VulkanTextureRegistry` の `mirrorsGlMips()` 三項。**残参照 0**。
- **一度だけビルドエラー**（accessor の**メソッド本体**を残してしまい `return` 欠落）。**その場で直して緑**にした。
- **受入（撤去後）**:

| 場面 | GPU | exit | clean | verdict | 提示画像 vs GL |
|---|---|---|---|---|---|
| entity | AMD | 0 | true | PASSED | 1.139 / **1.62 %**（既知のばらつき 1.25〜1.61 % 内） |
| 通常 | AMD | 0 | true | PASSED | 0.001 / **0.01 %** |
| 通常 | **Intel** | 0 | true | PASSED（msgCount 0） | 0.016 / **0.00 %** |

- **意味**: **現行の生成チェーン（＋1.0 クランプ）だけ**が残り、**測定で二十倍以上悪い併存経路**と
  **その lab flag が消えた**（「旧経路の併存を残さない／lab flag に正当性を依存させない」）。
  既定の画は**通常場面 AMD 0.01 % / Intel 0.00 %** を維持。
- **残る未完**: entity 場面の **format 5（粒子系）の native 描画**（§0.78〜§0.90 で候補を消し込み済み。
  除外で 1.25 % → 0.32 %）。**行列・ブレンド・被覆・シェーダ・SPIR-V・mip は棄却済み**。

## 0.91 **前節の mip 仮説を撤回**（既存の測定で 13.51 % と記録済み）／S23 の descriptor 疑いは**対処済み**／`MIRROR_GL_MIPS` は撤去対象

- **撤回（§0.90 の「maxLod を 0 にすべき」）**: `VulkanGlTexture` に**既存の測定記録**がある。
  > 「1.0 が **0.21〜0.28 %**、GL の全チェーン（8〜9 段）が **8.77〜8.82 %**、4 段が **7.37 %**、
  > **0.0 が 13.51 %**（9段の画像でも1段の画像でも）。ドライバに直接聞くと、この場面が束縛するテクスチャは
  > maxLevel/maxLod 0・NEAREST と報告するが、**それを写すと 1.0 クランプより五十倍悪い**」
  ＝ **`maxLod=0` は既に棄却済み**。**現行の生成チェーン＋1.0 クランプが測定最良**である。**§0.90 の次の一手は取り下げる。**
- **S23（descriptor が古い view を指す疑い）は対処済み**: `VulkanGlTexture.key()` は
  `(view * 31) ^ generation` で、**再作成時に `generation++`** される。＝ 再作成でキーが変わり、
  **古い descriptor は再利用されない**。
- **`MIRROR_GL_MIPS`（`celtium.vulkan.mirrorGlMips`、既定 false）は撤去対象**:
  上の測定どおり**併存する旧経路**で、現行より**二十倍以上悪い**（5.60 % 対 0.16〜0.28 %）。
  「旧経路の併存を残さない／lab flag に正当性を依存させない」に照らして**削除する**。
  **撤去に必要な箇所（実測）**: `VulkanGlTexture` の
  **66**（宣言）**213-215**（accessor）**68**（`mirroredLevels` フィールド）**263/265/277/279**（`glLevels` と条件）
  **387**（0 代入）**489-491**（`mirroredLevels` によるクランプ）**298-310**（ミラー取り込みブロック）
  **1146**（`glLevelCount`）**742**（コメント）＋ **367/469/471/725/903**（他にも flag 参照あり）、
  `VulkanTextureRegistry` の **540**（`mirrorsGlMips()` 三項）。
- **失敗の記録**: 一度に消そうとして**コンパイルエラー**（flag の参照が当初読んだ6箇所より多かった）。
  **壊れたツリーはコミットせず巻き戻した**（ビルド緑・差分なしを確認）。次は**全参照を列挙してから**同じ変更単位で消す。
- **受入（現 HEAD・変更なし）**: 既定 run は AMD/Intel とも exit 0 / clean / PASSED / VUID 0。

## 0.90 **被覆も棄却**（atlas/lightmap とも完全）／残る焦点は **sampler の mip（maxLod）**

- **計器（format 5 グループで、atlas・lightmap・グループのテクスチャの被覆を出す）**:
  ```
  CELTIUM_PROJ_PROBE fmt=5 ... atlasFull=true lightmapFull=true groupTexFull=true
  ```
  ＝ **ミラーは完全**（部分ミラーのまま未初期化領域をサンプルしている、という説明は**棄却**）。
- **ここまでで format 5 について棄却した説明（すべて実測・コード根拠つき）**:
  属性順（stride 28 の取り違え）／シェーダ式／SPIR-V の鮮度／光座標の生値（0x0000 は正当な最暗値）／
  生と保存済みの射影（完全一致）／ブレンド（観測値が配管済み）／atlas・lightmap の被覆（完全）。
- **残る候補（次の1点）**: **sampler の mip**。
  `VulkanGlTexture` のサンプラは `.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)`、`.minLod(0.0f)`、**`.maxLod(1.0f)`** で作られ、
  コード自身の注記にも「**この場面は maxLevel/maxLod 0 の1レベルで束縛するテクスチャがある**（minFilter/magFilter 9728＝GL_NEAREST）」
  と書かれている。＝ **GL の `GL_TEXTURE_MAX_LEVEL` を追えていない場合、存在しない mip をサンプルして黒くなる**。
  これは観測（**黒に近い 16.0 対 147.7**）と一致する。
- **次の一手（1点）**: **ミラーごとに `GL_TEXTURE_MAX_LEVEL`（mip 段数）を記録し、サンプラの `maxLod` がそれに従うか**を確認する。
  従っていなければ、**mip を持たないテクスチャは maxLod=0** にする。
- **受入**: 変更は計器のみ。既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.89 **行列とブレンドは正常**（計器とコードで棄却）／残る焦点は**テクスチャ（atlas ミラー）**

- **計器（format 5/3 の生の `glProjection` と、保存・送出される射影を並べる）**:
  ```
  CELTIUM_PROJ_PROBE fmt=5 rawDepth=(0.0,0.0,-1.001,-0.1) storedDepth=(0.0,0.0,-1.001,-0.1)
                            rawW=(-1.0,0.0) mvT=(0.0,-1.62,0.05)
  ```
  ＝ **生と保存済みが完全一致**（リマップで壊れていない）。`rawW=(-1,0)` は透視、
  `m14=-0.1` は地形の `-1.0` と違うが、これは **MC の遠景用射影**（空・雲・粒子に使う near/far）で**正当**。
  `mvT` はカメラ高さ。＝ **行列は原因ではない**。
- **ブレンド（コード読み）**: 粒子・ブロック両パイプラインとも
  `blendEnable(blend) / srcColorBlendFactor(srcFactor) / dstColorBlendFactor(dstFactor)` を使い、
  バックエンド側で **観測した GL のブレンド係数をグループに持たせ、パイプラインキーにも入れている**（`currentGroupBlendSrc/Dst`）。
  ＝ **ブレンドの配管も欠けていない**。
- **残る焦点**: **テクスチャ**。format 5 は**ブロック atlas**（`texture=29`）を張る族で、
  差が**暗さ（黒に近い）**であることから、**atlas ミラーが粒子スプライトの領域を持っていない**
  （＝未初期化領域をサンプルして黒くなる）可能性が最も高い。
  §A04 で「部分ミラーの完了」は入れたが、**粒子が使うスプライト範囲の被覆**は未確認である。
- **次の一手（1点）**: **atlas ミラーの被覆**を、同じフレームで **GL の atlas と比べる**
  （粒子 UV の範囲がミラー済みか）。もしくは **`hasFullContent()` の状態を format 5 のグループで記録**する。
- **受入**: 変更は計器のみ。既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.88 format 5 の頂点は**カメラ相対のワールド座標**／`immediateFormats=5` は**切り分けに使えない**（訂正）

- **計器（format 5 の先頭頂点と、そのときの行列ミラーの平行移動）**:
  ```
  CELTIUM_FMT5_POS first=(8.404,-2.504,0.076) camera=(0.0,-1.62,0.05)
  ```
  ＝ 頂点は **カメラ相対のワールド座標**（原点から 8.4 ブロック）。行列側の平行移動は **カメラ高さ**。
- **`-Dceltium.vulkan.immediateFormats=5`（format 5 だけ受理）の結果**: 差 **8.48 %**、
  差の領域は **平均 142.3 暗い**（提示 16.0 対 GL 147.7 ＝ **ほぼ黒**）で**大面積**。
- **この数字は format 5 固有の誤りとしては読めない（訂正）**: 他の format を**拒否**すると、
  グループの分け方・描画順・拒否経路（GL 本体が走る）が**同時に変わる**ため、
  **除外方向の測定（§0.78: format 5 を除外すると 1.25 % → 0.32 %）だけが有効な帰属**である。
  受理方向の絞り込みは**交絡する**ので、今後の帰属は**除外方向**で行う。
- **拒否理由の実測（重要）**: 定常状態での **format 5 の拒否は 0 件**（起動直後の
  `preflight: format=5 texture=29 (no pass has run yet in this instance)` が 8〜9 件だけ）。
  ＝ **定常状態では format 5 は全て native が引き受けている**。だから §0.78 の除外が効く。
- **確定した整理**:
  - **format 5 の native 描画が、entity 場面の差の主項**（除外で 1.25 % → 0.32 %）。
  - 差の性質は**暗さ**（§0.79）で、**両GPUで再現**（§0.85）。
  - 頂点は**カメラ相対のワールド座標**で、行列の平行移動は**カメラ高さ**（＝掛け方の候補が2つに絞られた）。
- **次の一手（1点）**: **除外方向**で format 5 の**一部だけ**を native に残す手段は無いので、
  代わりに **format 5 の native 描画を「GL と同じ行列で描いているか」**を、
  **1フレーム内の同一バッチ**について **`glProjection`/`glModelView` の生値**と**送った push constant** を並べて確認する。

## 0.87 **訂正**: §0.84 の「リスト内ジオメトリ」診断は**支持されない**（ゲートは受理を減らしていない）

- **カウンタの比較（同じ entity 場面・既定構成）**:

| run | offers | accepted | glBodyCompleted |
|---|---|---|---|
| ゲートあり（p398） | 16500 | **約 13100** | 約 4000 |
| ゲートなし（p366） | 18000+ | **約 13900** | 約 4100 |

  ＝ ゲート（`celtium$listIndex == 0` のときだけ提供）を入れても **accepted はほとんど減っていない**。
  つまり **`GL_LIST_INDEX != 0` で提供されるバッチは稀**であり、
  **「コンパイル中のバッチが即時経路に描かれている」という §0.84 の説明は成り立たない**。
  （`CELTIUM_MODEL_QUAD`／`CELTIUM_DISPLAY_LIST` は両 run で出ており、取り込み自体は常時動いている。）
- **ゲート時の画像（`.celtium-work/gate-compare.png`）**: 提示側の左に**巨大で暗い板**（16.8 対 148.3、143.7 暗い）。
  小さな四角ではなく**大きな面**なので、ゲートが壊したのは**別のもの**（＝受理を止めた結果の二重／欠落）。
  ＝ **ゲートは棄却のまま**でよいが、その理由は「リスト所有」ではなく**下流契約の片側変更**である。
- **維持する確定事項**:
  - 差は **format 5（粒子系パイプライン）**に帰属（除外で 1.25 % → **0.32 %**、§0.78）。
  - 差の性質は **暗さ**（§0.79）、両GPUで再現（§0.85）。
  - **粒子を止めても 1.43 %**（§0.83）なので、fixture が明示的に出す粒子だけではない。
- **次の一手（1点）**: **format 5 のバッチの頂点座標そのもの**を記録し、**ワールド座標か、カメラ相対か**を判定する。
  §0.44 で表示リストに対して行ったのと同じ切り分けを、format 5 に対して行う（**行列ではなく頂点の空間**を疑う）。

## 0.86 真因の**実装点を確定**: 受理すると `info.cancel()` が走り、**リストへの保存まで取り消される**

- **コードの該当箇所（`WorldVertexBufferUploaderMixin`）**:
  ```
  351:        if (accepted) {            // ＝バックエンドが引き受けた
  352:            celtium$acceptedByBackend++;
  ...
  358:                info.cancel();     // ＝GL 本体（この場合はリストへの保存）を取り消す
  ```
  `@Inject(method = "func_181679_a", at = @At("HEAD"), cancellable = true)`（119行）。
- **何が起きているか**: `glNewList` の内側では GL 本体は「**描画**」ではなく「**リストへの保存**」である。
  即時経路がそのバッチを受理すると **保存まで取り消される**ため、
  - **リストは空**になり（再生で何も出ない）、
  - ネイティブ側は**コンパイル時点の行列**で**その場で描いてしまう**（＝位置が合わない四角）。
  これが §0.84 の画像・§0.85 の両GPU再現と一致する。
- **正しい契約（次の実装単位）**:
  1. **コンパイル中**（`GL_LIST_INDEX != 0`）に来たバッチは、**即時描画せず**、**リスト id に紐づけて保持**する。
  2. その間 **GL 本体は取り消さない**（＝GL にもリストへ保存させる）。保持した分は**ネイティブでは描かない**ので二重にならない。
     ただし「受理＝描画」を前提にしたカウンタ・取消の連鎖を同時に直す必要がある（ここを推測で片方だけ変えない）。
  3. **再生時**（`celtium$drawModelList(listId)`）に、**その時点の行列**で保持分を描き、GL 本体は取り消す。
     これは §0.44/§0.71 で作った**表示リスト取り込み＋再生**の経路と同じ契約に揃える。
- **§0.84 のゲートがなぜ 8.91 % だったか（読みで確定した範囲）**: ゲートは**提供だけ**を止め、
  **受理を前提にした下流（カウンタ・取消・リスト所有）**はそのままだった。契約を**片側だけ**変えると崩れることの実例。
  ＝ **次は 1〜3 を同じ変更単位でまとめて実装し、その後に受入する。**
- **受入（現 HEAD・変更なし）**: AMD/Intel の通常場面は clean/PASSED/VUID 0、通常場面の画は 0.00〜0.01 %。

## 0.85 **Intel 受入（現 HEAD）** ＋ **欠陥はベンダー非依存**と確定

- **Intel・通常場面（現 HEAD、既定構成、120 s）**: exit 0 / **clean** / **PASSED** / **メッセージ 0**、
  **提示画像 vs スクリーンショット = 0.000 / 0.00 %**（完全一致）。＝ **既定経路（相対ビュー修正・adoption）は Intel でも成立**。
- **Intel・entity 場面（同条件）**:
  | | AMD（3 run） | **Intel（今回）** |
  |---|---|---|
  | 差（>8 が異なる） | 1.25 / 1.43 / 1.61 % | **1.33 %** |
  | 差の領域の暗さ | 平均 48.7 暗い | **平均 59.8 暗い** |
  | bbox | x=0..1101, y=30..719 | x=0..961, y=35..719 |

  ＝ **同じ欠陥が Intel でも同水準で再現**。**ベンダー非依存のコード欠陥**であり、ドライバ差ではない。
- **意味**: §0.84 の診断（**表示リストのコンパイル中にアップロードされた頂点を即時経路がその場で描いている**）が
  **両GPUで裏づけられた**。完成条件 (d) の「AMD/Intel で必要な場面を確認」に、**この欠陥の両GPU再現**を追加する。
- **次の一手（1点・読みのみ）**: §0.84 のゲートが **8.91 % に悪化した理由**を特定する。
  `accepted=false` が下流で**何を止めているか**（GL 本体の実行・カウンタ・取消の連鎖）を読み、
  「止めると壊れる理由」を理解してから**再生時所有**の実装に入る（推測で再度ゲートしない）。

## 0.84 **余分な四角の正体を画像で確定**（表示リストのコンパイル中ジオメトリ）／単純なゲートは棄却（8.91 %）

- **画像（`.celtium-work/mob-compare.png`、既定構成・entity 場面・2倍拡大）**:
  - 左（Vulkan 提示）: **空の上に濃い灰色〜黒の四角**、**地形の上に淡い紫の半透明の四角**（＝**GL が描いていない余分な四角**）。
  - 右（GL のスクリーンショット）: **地形と空だけ**で、四角は無い。
- **位置ずれではない**: モブ窓（x0..400, y360..720）で **±6 画素の平行移動を総当たり**しても最良は **dx=0 dy=0**（3.760）。
  ＝ **形は同じで「有る／無い」が違う** ＝ **欠落／重複**。
- **差の色**: 提示=(106.2,99.5,105.5) / GL=(144.0,155.6,157.8) ＝ GL 側は**空の色**で、提示側にだけ**物体**が乗っている。
- **カウンタ**: 既定構成は `modelDraws=0` `displayListCancelled=0` ＝ **取り消しではなく**、
  **表示リストのコンパイル中にアップロードされた頂点が即時経路へ提供され、その場で描かれている**。
  GL はそれを**リストに保存して後で再生**するので、**位置が合わないまま画面に出る**。
- **試した修正（棄却・巻き戻し済み）**: コンパイル中は提供しない（`celtium$listIndex == 0` を条件に追加）。
  **結果は大幅悪化**（1.25 % → **8.91 %**）。＝ 即時経路がリスト内ジオメトリを引き受けるのは**意図された契約**で、
  **単に止めると絵が崩れる**（GL 側の描画が別経路と二重になる）。
- **正しい方向（次の一手）**: **捨てるのではなく、再生時に描く**。リストのジオメトリは
  **`glCallList` の時点の行列**で描かれるべきなので、コンパイル中に受け取ったバッチは
  **リスト id に紐づけて保持し、再生（`celtium$drawModelList`）でその時の行列と一緒に描く**。
- **受入**: 巻き戻し後も既定 run は exit 0 / clean / PASSED / VUID 0（ビルド緑・差分なしを確認）。

## 0.83 フレーム差も粒子も**棄却**／残差は「実体が居る場面」で **1.3 ± 0.2 %** のばらつき

- **計器（新規・再利用可）**: `-Dceltium.validation.dumpPresentedTick=<n>`（既定 198）。
  提示画像は**要求の次の present で消費**されるため、要求 tick がどのフレームを写すかを選べる。
- **実測（entity 場面、同一構成、提示画像 vs 同一 run のスクリーンショット）**:

| 構成 | 平均絶対差 | >8 が異なる |
|---|---|---|
| 書き出し tick 198（従来） | 0.676 | **1.25 %** |
| 書き出し tick 199（捕捉に1フレーム近い） | 0.915 | **1.61 %** |
| **粒子なし**（`celtium.validation.particles=false`） | 1.037 | **1.43 %** |
| 通常場面（実体なし） | 0.011 | **0.01 %** |

- **棄却**:
  1. **フレーム差**（提示が前フレーム）：捕捉に**近づけても改善しない**（1.25 % → 1.61 %）。
  2. **粒子**：粒子を止めても**ほぼ同じ**（1.43 %）。
- **確定した性格**: 残差は **「実体（モブ）が居る場面」でだけ出る 1.3 ± 0.2 %**。
  同じ構成の3 run で 1.25 / 1.43 / 1.61 % とばらつくので、**単一の数字として断定しない**（今後は範囲で記す）。
- **まだ説明できていないこと**: モデル描画は **off**（実体は GL が描く）で、**GL 側スクリーンショット同士は 0.01 %**
  なのに、**同一 run 内の提示画像とスクリーンショットが 1.3 % 違う**。両者は同じ世界フレームバッファを読むはずで、
  この差の出所は**未確定**（次の焦点）。
- **次の一手（対象を1つ）**: **モブ領域だけ**を切り出し、提示画像とスクリーンショットで
  **同じ画素がどう違うか**（色・形・重複）を見る。差が**左に偏り、暗い**ことは分かっているので、
  「実体の描画が二重/欠落/暗い」のどれかに絞れる。
- **受入**: 全 run とも exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.82 光座標の生値を計測（**0x0000**）／「無効」と解釈した修正は**逆効果**で巻き戻し／残る説明は**提示が前フレーム**であること

- **計器（format 5 のバッチの生の光座標）**:
  ```
  CELTIUM_LIGHTCOORD fmt=5 stride=28 count=172 first=0x0(0) second=0x0(0) last=0x0(0) u_first=0.0
  ```
  ＝ 先頭4件の format 5 バッチは**光座標がすべて 0**。シェーダはこれを `(0+8)/256`＝**最暗テクセル**として扱う。
- **試した修正と結果（巻き戻し済み）**: 「0 はライトマップ無効の意味」と解釈し、粒子のバーテックスで `-1` を印として
  フラグメントで `vec4(1.0)`（無照明）にした。
  **結果は悪化**（1.25 % → **1.50 %**、差の領域は **71.4 暗い**）。
  ＝ **`0` は「無効」ではなく正当な最暗値**であり、**GL も同じ値で掛けている**。**解釈が誤りだったので巻き戻した**。
- **より有力な説明（数値と整合する）**: **提示は「前のフレーム」を出す**（設計として記録済み）。
  スクリーンショットは**その時点のフレーム**なので、
  **粒子・雲など動くものが写る場面では 1 フレーム分ずれる**。
  - **静止場面（実体なし）**: 0.011 / **0.01 %**。
  - **動く粒子のある entity 場面**: 0.676 / **1.25 %**（差の領域は**動いている粒子の位置**）。
  ＝ **欠陥ではなく、present の設計（前フレーム提示）と同一フレーム比較の食い違い**である可能性が高い。
- **次の一手（1点・run 不要の確認も可）**: 提示画像を **tick N+1 で取得**し、tick N のスクリーンショットと比べる経路を作れば
  「フレーム差」であることが直接示せる。fixture 側の小変更で足りる。
- **受入**: 巻き戻し後も既定 run は exit 0 / clean / PASSED / VUID 0。

## 0.81 シェーダ経路も棄却（両者は同一で修正済み・SPIR-V も最新）／残る候補は**ライトマップ画像**と**光座標**

- **粒子とブロックのシェーダを全文比較**:
  - `immediate_particle.vert` と `immediate_block.vert` は**ライトマップの式が完全に同一**:
    `v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0;`
  - 両方に**同じ修正コメント**が残っている:
    「**以前の `>> 8` は 0..240 の範囲をゼロへ潰し、全サンプルが同じ暗いテクセルに落ちていた**」
    ＝ **今回観測した「平均 48.7 暗い」とまさに同じ症状**だが、**それは既に修正済み**である。
  - フラグメントも両者とも `texture(u_Lightmap, v_Lightmap)` で同じ。
- **SPIR-V の鮮度**: ソース（`src/main/shaders`）は **10-09 22:40**、生成 SPIR-V は **10-10 03:09** ＝ **新しい**。
  jar にも同じ SPIR-V が入っている（サイズ一致）。＝ **古いバイナリという説明も棄却**。
- **ここまでで棄却した説明（すべて数値・コード根拠つき）**: adoption／即時経路全体／即時群の行列／
  format 6／粒子の属性順（stride 28 の取り違え）／シェーダ式／SPIR-V の鮮度。
- **残る候補（次の1点）**:
  1. **ライトマップ画像そのもの**（ミラーの内容・鮮度・sampler）。粒子のバッチが**古い/別の**ライトマップを見ている。
  2. **光座標の値**（format 5 の頂点に入っている `a_LightCoord` が GL 側と同じか）。
- **次の一手**: format 5 のバッチで **`a_LightCoord` の生値**と**束縛中のライトマップの内容**を1回だけ記録し、
  「式は正しいが入力が違う」のか「入力は同じで画像が違う」のかを分ける。
- **受入**: 変更は文書と読解のみ。既定 run は exit 0 / clean / PASSED / VUID 0 のまま。

## 0.80 format 5 の**レイアウトは正しい**（棄却）／残る焦点は粒子の**フラグメント**のライトマップ

- **確認（両パイプラインの属性記述を並べた）**:

| | BLOCK（format 3） | PARTICLE（format 5） |
|---|---|---|
| loc0 | vec3 位置 @0 | vec3 位置 @0 |
| loc1 | **R8G8B8A8 色 @12** | **R32G32 UV @12** |
| loc2 | **R32G32 UV @16** | **R8G8B8A8 色 @20** |
| loc3 | R16G16 ライトマップ @24 | R16G16 ライトマップ @24 |

  `SpirvShaders` の注記どおり**順序が違う**（`position, texture, colour, lightmap`）が、
  **粒子側も自分の順序を正しく記述している**。stride はどちらも 28。
  ＝ **「サイズが同じで順序が違う」という仮説は棄却**（実装は既に正しい）。
- **残る焦点**: **粒子のフラグメントモジュール**（`SpirvShaders.IMMEDIATE_PARTICLE_FRAGMENT`）の**ライトマップの使い方**。
  実測は「平均 48.7 暗い」なので、**(a) 0..255 の正規化漏れ**（最暗端に張り付く）、
  **(b) ライトマップの束縛がブロック用のまま**、(c) 色×ライトマップの順序、のいずれか。
- **次の一手（1点・run 不要）**: **粒子とブロックのフラグメントモジュールのライトマップ行を並べて読む**。
- **受入**: この節の変更は計器と記録のみ（描画経路の変更なし）。既定 run は exit 0 / clean / PASSED / VUID 0 のまま。

## 0.79 差の**性質**を確定: 提示画像は差の領域で **平均 48.7 暗い** ＝ 幾何ではなく**ライトマップ**

- **計測（既定構成・entity 場面・同一 run 内）**:
  ```
  差のある画素 11,527
  その領域の平均色: 提示画像=(106.2, 99.5, 105.5)  GL=(144.0, 155.6, 157.8)
                    → 提示画像は平均 48.7 暗い
  ```
- **読み**: 差は**位置ずれでも欠落でもなく「暗さ」**。＝ **ライトマップ**（format 5 の native 描画）が原因。
  「暗さ」は §0.11/§0.20 で扱った地形の暗さと**同じ症状**で、**format 5（粒子系）でも同じ族の欠陥が残っている**ことになる。
- **format 5 の位置づけ**: §0.43 でライトマップの**遅延供給**を入れた族であり、**ライトマップ UV・束縛・ミラーの鮮度**が候補。
- **次の一手（1点）**: format 5 の**ライトマップ座標の出所**を読む（頂点属性か、ミラーか、固定値か）。
  `48.7 暗い` は「ライトマップ UV が最暗を指している」典型で、**UV の取り違え/欠落**が最有力。
- **受入**: 既定構成の run は exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.78 差の出所を **format 単位で特定**: **format 5 の native 描画**が 1.25 % の大半（除外で 0.32 % へ）

- **方法（既存の帰属スイッチ）**: `-Dceltium.vulkan.immediateFormats=<数字の並び>` で受理する format を絞り、
  **entity 場面**で提示画像とスクリーンショットの差を測る。

| 構成 | 提示画像 vs 同一 run のスクリーンショット |
|---|---|
| **format 5 を除外**（`12346789`） | **0.273 / 0.32 %**（**改善**） |
| 既定（全部受理 `123456789`） | 0.676 / 1.25 % |
| format 6 を除外（`12345789`） | 1.149 / 1.53 %（**悪化**） |

- **読み**:
  - **format 5（粒子系）を GL に返すと差が 1.25 % → 0.32 %**。＝ **format 5 の native 描画が約 0.9 % を生んでいる**。
  - **format 6 を返すと悪化**（1.53 %）＝ **format 6 の native 描画は正しく、外すと絵が崩れる**。
  - 残る 0.32 % が床（その他の native 経路＋提示の差）。
- **意味**: **entity 場面の差は「どの format か」まで落ちた**。format 5 は**ライトマップを使う**族なので、
  §0.43 の遅延供給・ライトマップ束縛・UV のいずれかが疑わしい（**次の1点**）。
- **受入**: 両 run とも exit 0 / **PASSED**（`clean`・VUID 0 は既定回と同様）。
- **次の一手**: **format 5 の native 描画と GL 描画を、同じバッチで画素単位に比べる**（ライトマップ/UV/ブレンドのどれか）。
  既存の `CELTIUM_GROUPS`／`CELTIUM_GROUP_DEPTH` 系の計器に **format 5 のライトマップ束縛**を足すのが最短。

## 0.77 差の所在が確定: **通常場面と Intel は完全一致（0.01 % / 0.00 %）／entity 場面の AMD だけ 1.25 %**

- **同一 run 内での比較（提示画像 vs その run 自身のスクリーンショット ＝ GL が描いた絵）**:

| run | 提示画像 vs 同一 run のスクリーンショット |
|---|---|
| **既定（モデル描画 off・adoption on、entity 場面）** | **0.676 / 1.25 %** |
| モデル描画 on（`V_now × C_compile⁻¹`、entity 場面） | 3.137 / 2.19 % |
| **通常場面（実体なし）** | **0.011 / 0.01 %** |
| **Intel 既定（通常場面）** | **0.002 / 0.00 %** |

- **GL 側スクリーンショット同士の比較（entity 場面、3構成）**: すべて **0.01 %** ＝ **場面は決定的**。
- **棄却した出所（数値つき）**:
  - **adoption**: `-Dceltium.vulkan.adoptGameFramebuffer=false` で **1.60 %**（on の 1.25 % より**悪い**）。
  - **即時経路**: `-Dceltium.vulkan.immediate=false` で **8.14 %**（切ると大幅に悪化＝即時経路は正しく描いている）。
  - **即時群の行列**: 最初の8群を実測。HUD 相は **直交射影＋恒等モデルビュー**（`worldPass=false`）、
    ワールド相は **透視＋ワールド行列**（`worldPass=true`）で、**どちらも正しい形**。
- **確定した到達点**:
  - **通常場面（実体なし）**: 提示画像は GL の絵と **0.01 %**（AMD）／**0.00 %**（Intel）で一致。
  - **entity 場面（AMD）**: **1.25 %**（モデル描画 off）／**2.19 %**（on）。**entity 場面固有**で、残る唯一の既知の差。
- **次の一手（対象は1つ）**: entity 場面で **native に受理されている format を1つずつ外し**（`celtium.vulkan.immediateFormats`）、
  どの format の native 描画が 1.25 % を生むかを特定する。即時経路は「切ると悪化する」＝**大半は正しく描いている**ので、
  **特定の format/群**が疑わしい。

## 0.76 残差の床（1.25 %）は **entity 場面固有**と判定／即時経路の行列は提供時点の GL 行列で正しい

- **画像（既定＝モデル描画 off の entity 場面 vs GL、`.celtium-work/floor-compare.png`）**:
  左（既定）の**左下に暗い四角**（無テクスチャに見える）＋左上の地形に**淡い色かぶり**。右（GL）は無い。
- **差の局所化**: 1.25 %（11,520 画素）。**列バンドで左に偏る**（6 / 3 / 0 / 0 / 0 / 0 / 0 / 0 %）、行は 1〜2 % ずつ。
- **比較（既存の測定）**: **通常場面（実体なし）の既定 vs GL は 0.072 % / 0.05 %**。
  ＝ **床 1.25 % は entity 場面固有**（＝モブに関わるバッチ）である。
- **即時経路の行列（コード読み）**: 即時群の行列は **`glProjection` / `glModelView`**（＝**そのバッチを提供した時点の GL 行列**）
  から取られている（`currentGroup*` へ `arraycopy`）。＝ **行列の出所は正しい**（モデル経路の行列問題とは別）。
- **計器（即時群の種別を名乗る）**: `CELTIUM_IMMEDIATE_ENQ ... fmt=5 texture=29 mirrored=true batches=1 worldPass=true`
  ＝ 最初の群は**正常**（テクスチャはミラー済み・world pass 中）。**暗い四角を描いているのは一部の群**で、
  **最初の群ではない**。
- **次の一手（対象を絞る）**: 即時群の計器を**全群**に広げ（先頭 N 件）、`fmt / texture / mirrored / worldPass / mvT` を出して、
  **暗い四角の位置に対応する群**を特定する。テクスチャ未ミラーのまま白で描かれている疑いが最有力。
- **受入（AMD、既定、`keepEntities`、90 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.75 「リストごと1行列」の近似は原因ではない（計器で棄却）／残差 2.19 % の性格

- **疑い**: キャプチャは**リストごとに1つの行列**（最初のクワッドのもの）しか保存しないため、
  リスト内でパーツごとに変換が変わると誤差になる。
- **計器（リスト内で最初のクワッドと行列が違うクワッドを数える）**:
  ```
  CELTIUM_MODEL_BOUNDS list 2 vertices=24 ... quadsWithOtherMatrix=0/12
  ```
  ＝ **0件**。**この近似は残差の原因ではない**（1回の run で棄却）。
- **残差 2.19 % の内訳（測定済みの床との比較）**:
  - adoption 自体の床（モデル描画 off）: **1.25 %**（0.676）
  - GL 同士の対照: 0.01 %（0.029）
  → **モデル経路固有の残差は約 1 %**。
- **現時点の到達点**: モデル描画の差は **18.49 % → 2.19 %**（`V_now × C_compile⁻¹`）。
  **既定はモデル描画 off**（§0.64）なので、**既定の絵は依然として正しい**。
- **次の一手（候補を絞る）**: 残差の画像は**左側に寄った暗い四角**で、位置は概ね合っている。
  候補は (a) **クワッドの分割対角**（メッシュは `(0,1,2)(0,2,3)`、GL のクワッドも同じなので可能性は低い）、
  (b) **UV・法線の扱い**、(c) **深度の丸め**。次は**1クワッドだけを native で描いた画像**と GL の同領域を
  画素単位で見て、**(b) か (c) か**を決める。

## 0.74 **正しい変換を特定**: `V_now × C_compile⁻¹`（5.80 % → **2.19 %**）

- **理論**: 表示リストは**頂点を変換済みで保存する**（プロジェクト自身の注記）。ならばキャプチャ頂点は
  **コンパイル時のビュー空間**にあり、再生時に必要なのは**その後のカメラの変化分** ＝ **`V_now × C_compile⁻¹`**。
  両方の半分は手元にある（`V_now` は行列ミラー、`C_compile` はキャプチャが保存していた行列）。
- **実測（entity 場面、`modelDraw=true`、同一条件、7通り）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **`V_now × C_compile⁻¹`（今回）** | **2.19 %** | **3.136** |
| ミラー（カメラ回転のみ） | 5.80 % | 8.485 |
| 恒等行列 | 8.08 % | 14.744 |
| キャプチャ時行列 `C_compile` | 13.19 % | 19.185 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| ミラーの回転＋ライブの平行移動 | 18.40 % | 31.484 |
| 射影も `glGetFloatv` | 18.49 % | 31.591 |

  **5.80 % → 2.19 %**。**理論から導いた1点が、7通りの中で最良**。
- **受入**: exit 0 / **clean** / **PASSED** / **VUID 0**、`modelDraws=15426`。
  参考: モデル描画 off は 1.25 %（0.676）、GL 同士は 0.01 %（0.029）＝ **残差 2.19 % はまだ baseline より大きい**。
- **意味**: **モデル経路の主要な欠陥が取れた**。残差は **1.25 % の床**（adoption 自体の差）＋ **約 1 %** のモデル経路固有分。
- **次の一手**: 残差の画像を切り出して性質を見る（位置ずれ・欠落・色のどれか）。深度・UV・法線のいずれかが候補。

## 0.73 頂点の空間を計測 → **モデル空間**（±0.6）と確定／それでも6通りのどれも正解ではない

- **計器（キャプチャ頂点の座標範囲＋キャプチャ時行列の平行移動）**:
  ```
  CELTIUM_MODEL_BOUNDS list 2 vertices=24 x=-0.125..0.063 y=-0.125..0.625 z=-0.125..0.125
                       compileT=(0.347,-1.706,-0.74)
  ```
  ＝ 頂点は **モデル空間**（±0.6 ＝ モデルのパーツ）で、キャプチャ時の行列の平行移動は
  **その時点のエンティティ位置**（`0.347,-1.706,-0.74`）。**キャプチャは対になる行列も保存している**（未使用だった）。
- **モデルビューの6通りを実測（entity 場面、`modelDraw=true`、同一条件）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **ミラー（再生時・カメラ回転のみ・平行移動ゼロ）** | **5.80 %** | **8.485** |
| 恒等行列 | 8.08 % | 14.744 |
| **キャプチャ時行列（保存済み・今回試した）** | **13.19 %** | 19.185 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| ミラーの回転＋ライブの平行移動（合成） | **18.40 %** | 31.484 |
| 射影も `glGetFloatv` | 18.49 % | 31.591 |

- **読み（重要）**: **平行移動を入れる全ての組み合わせが悪化する**（18.30 / 18.40 / 18.49）。
  ＝ **頂点は既に配置済み**である可能性が高く、**「モデル空間の頂点 × 再生時行列」という素直な説明は成り立たない**。
  ただし**恒等（8.08）もミラー（5.80）に劣る**ので、**6通りのどれも正解ではない**。
  ＝ **キャプチャしたデータそのものを次に見る必要がある**（例: **コンパイル時と再生時の両方のクワッドを取り込んでいないか**、
  その場合メッシュに**異なる空間の頂点が混在**する）。
- **対応**: 合成の実験は**巻き戻し**、最良のミラーへ復帰（**不採用の分岐は残していない**）。ビルド緑。
- **受入**: 復帰後も exit 0 / clean / PASSED / VUID 0（`modelDraws=14928`）。
- **次の一手**: キャプチャの**取り込み回数と出所**（コンパイル時のみ／再生時も）を数える計器を入れ、
  **メッシュ内に異なる空間の頂点が混在していないか**を確かめる。

## 0.72 モデルビューの4通りを実測（ミラーが最良）／**頂点の空間**が次の1点

- **実測の一覧（entity 場面、`modelDraw=true`、同一条件）**:

| モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|
| **ミラー（カメラ回転のみ・平行移動ゼロ）** | **5.80 %** | **8.485** |
| 恒等行列 | 8.08 % | 14.744 |
| `glGetFloatv`（ライブ） | 18.30 % | 31.429 |
| （射影も `glGetFloatv`＝直交射影） | 18.49 % | 31.591 |

  ＝ **ミラーのカメラ回転は必要**（恒等にすると悪化）。**ライブはさらに悪い**。**最良はミラー**。
- **記録した矛盾（そのまま残す）**: キャプチャの最初の頂点は `v0=(0.063,-0.125,…)` ＝ **モデル空間の小さい値**なのに、
  **エンティティの平行移動を持つライブ行列**を使うと**悪化**する。＝ 単純な「モデル空間の頂点 × 再生時行列」では説明できない。
  **頂点がどの空間で保存されているか**が確定していないことが、残差 5.80 % の正体である。
- **対応**: 恒等行列の実験は**巻き戻し**、最良のミラーへ復帰（**不採用の分岐は残していない**）。
- **受入（復帰後、AMD、entity 場面、`modelDraw=true`、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `modelDraws=15032`、**8.08 % → 5.80 % へ復帰**（恒等実験の値 8.08 % と対比）。
- **次の計器（1点・決定的）**: **キャプチャした頂点の座標範囲**を、**そのリストのキャプチャ時行列の平行移動**と並べて出す。
  頂点が `0.06〜1` なら**モデル空間**、`数十〜数百` なら**ワールド空間**で、**どちらかで行列の掛け方が一意に決まる**。

## 0.71 行列の出所を**計器で確定**（ミラー×ミラーが最良）／残差はキャプチャ側に帰着

- **計器（enqueue 時に射影の種類・モデルビューの回転行・平行移動・束縛テクスチャを8件まで）**:
  ```
  CELTIUM_MODEL_ENQ texture=25 projKind=perspective proj11=-1.0 proj10=-1.0 proj14=-0.05
                    mvT=0.0,0.0,0.0 mvRow0=-0.333,-0.683,-0.65 mvScale=1.0
  ```
  ＝ **射影はミラー経由で透視になっている**（修正が効いている）。**モデルビューの平行移動はゼロ**（＝カメラのみ）。
- **3通りの組み合わせを実測（entity 場面、`modelDraw=true`）**:

| 射影の出所 | モデルビューの出所 | 差（>8 が異なる） | 平均絶対差 |
|---|---|---|---|
| `glGetFloatv`（直交射影を拾う） | `glGetFloatv` | 18.49 % | 31.591 |
| **ミラー** | **ミラー** | **6.35 % → 5.80 %**（再測） | 9.064 → **8.485** |
| ミラー | `glGetFloatv`（ライブ） | **18.30 %**（悪化） | 31.429 |

- **確定した読み**: **モデルビューはライブでもエンティティのものではない**（ライブに差し替えると 6.35 % → 18.30 % に悪化）。
  ＝ 「表示リストは**変換済みの頂点**を保存する」というコメントと整合し、**パーツは自分の位置を頂点側に持っている**ので、
  行列で**動かしてはいけない**。**最良はミラー×ミラー**で、**残差はキャプチャ側の対応（頂点と行列の組）**にある。
- **対応**: 悪化した組み合わせは**巻き戻し**、ミラー×ミラーへ復帰（再測で同水準を確認）。**不採用の分岐は残していない**。
- **受入（AMD、entity 場面、`modelDraw=true`、120 s、復帰後）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `modelDraws=14848`、**5.80 % / 8.485**（参考: モデル描画 off は 1.25 % / 0.676、GL 同士は 0.01 % / 0.029）。
- **次の一手（キャプチャ側・1点）**: キャプチャは**コンパイル時の行列も保存している**（現在は要約表示にのみ使用）。
  **頂点は保存時点で変換済み**という前提に立つなら、**コンパイル時の行列をどう打ち消すか**が残差の正体である。
  次はその1点（保存行列の使い方）を試す。

## 0.70 残差 6.35 % の性質（画像）: **まだ画面空間に散る暗い四角**

- **画像（`.celtium-work/mirror-compare.png`、左＝ミラー修正後・右＝GL）**: 左に**暗い四角が地形と空の両方に散在**（前回と同種）、
  右は地形のみ。切り出し領域で **mean 8.25 / >8 が 4.68 %**。
- **読み**: ミラー修正で 18.49 % → 6.35 % に減ったが、**性質は変わっていない**（画面空間に散る・暗い＝無テクスチャに見える）。
  ＝ **`callList` の時点でミラーが直交射影を保持している場面がある**（HUD/GUI 相のモデル描画）か、
  **テクスチャ束縛が別**（暗い四角＝白/無テクスチャで描かれている）かの**2候補**。
- **次の計器（2点だけ）**: (a) モデルビューの**回転・スケール行**（平行移動だけでなく）を enqueue 時に出す、
  (b) **束縛テクスチャ id** を同じ行に出す。この2つで「射影の取り違えが残っている」か「テクスチャが違う」かが決まる。
- **状態**: **既定はモデル描画 off**（§0.64）なので、**既定の絵は正しい**。この調査は既定へ戻すための前提を詰めている。

## 0.69 **原因を特定して修正**: モデル経路が `glGetFloatv` で**直交射影（HUD 用）**を拾っていた（18.49 % → 6.35 %）

- **計器（enqueue 時点の行列をモデル群と即時群で並べる）**:
  ```
  CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,-1.0,-0.05   mvT=0.0,-1.62,0.05
  CELTIUM_MODEL_ENQ     proj=0.0,0.0,-1.0,-0.05   mvT=0.514,-0.804,-0.689
  ```
  ＝ **モデル群に渡っている射影が直交射影**（`m23=-0.05`）で、**地形の透視射影（`0,0,-1.001,-1`）と違う**。
  ＝ `glGetFloatv(GL_PROJECTION_MATRIX)` が**その瞬間の正しい値ではない**（MC の行列ミラーが未反映）。
  **画像の「画面空間に散る四角」と完全に一致**する説明である。
- **修正**: モデル経路の行列を **`glGetFloatv` ではなくプロジェクト自身の行列ミラー**
  （`immediateState.matrix(MATRIX_MODE_PROJECTION/MODELVIEW)` ＝ 即時経路と同じ出所）から取る。
- **受入（AMD、entity 場面、`modelDraw=true`、120 s）**:

| 構成 | entity 場面 vs GL 経路（>8 が異なる） | 平均絶対差 |
|---|---|---|
| 修正前（`glGetFloatv`） | 18.49 % | 31.591 |
| **修正後（ミラー）** | **6.35 %** | **9.064** |
| 参考: モデル描画 off | 1.25 % | 0.676 |
| 参考: GL 同士（対照） | 0.01 % | 0.029 |

  `modelDraws=14726 / displayListCancelled=14726`、exit 0 / **clean** / **PASSED** / **VUID 0**。
- **意味**: **欠陥の主要因が1つ取れた**（画面空間に散っていた理由が射影の取り違えだった）。**残差 6.35 % は未解決**で、
  モデル描画 off の 1.25 % より大きい。
- **次の一手**: 残差 6.35 % の画像を切り出して**どこがどう違うか**を見る（位置ずれか、欠落か、色か）。同じ計器（enqueue の行列）は
  **モデルビューの出所**も疑う材料になる（`mvT` は妥当に見えるが、回転・スケールは未確認）。

## 0.68 **描画順は正しい**（地形パスが先）／投影の深度行は群をまたいで一致 — 残る焦点はモデル群の行

- **計器（順序）**:
  ```
  CELTIUM_WINDOW_ORDER frame=10 lastTerrainPassFrame=10 terrainRanFirst=true lastUiPass=translucent groups=3
  ```
  ＝ **同一フレームで地形パスが先に走っている**。深度バッファは埋まっているので、
  **原因は描画順ではない**と一意に決まった（候補は「深度値」側に絞られた）。
- **計器（投影の深度行）**:
  ```
  CELTIUM_GROUP_DEPTH row1=0.0,0.0,-1.001,-1.0 mvT=0.0,-1.62,0.05
  CELTIUM_GROUP_DEPTH row2=0.0,0.0,-1.001,-1.0 mvT=0.0,-1.522,-0.505
  ```
  ＝ 投影の深度行は**2群で完全に一致**（`m20..m23 = 0,0,-1.001,-1` ＝ GL 規約のまま、`vulkanProjection` は Y 規約のみ変換）。
  **投影は原因ではない**。
- **判明した次の焦点**: この2行は**どちらもモデル群ではない**。計器は「最初の2つの異なる行」しか出さない設計で、
  モデル群の `modelViewNow`（平行移動 `x=0.514`）と**一致しない**（2行とも `x=0.0`）。
  ＝ **モデル群の行はまだ見えていない**ので、**行に種類（model/immediate）を付けて出す**のが次の1点。
- **途中の失敗（記録）**: ラベル付けを `group[13] == null` で書いたが、その位置に `group` が無く**ビルドが壊れた**。
  **コンパイルできる状態へ戻してから**コミットした（推測で通さない）。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / clean / PASSED / VUID 0。
- **既定の状態は変わらず正しい**（モデル描画 off・§0.64）。

## 0.67 モデル描画の描画先を確認（深度つきのパス）／次は**描画順**を1点だけ測る

- **確認（コード読み）**: モデルメッシュは `celtium$enqueueModelMesh` で **`this.immediateGroups` に載る**（即時経路と同じリスト）。
  そのリストを描く `flushImmediateWindow` は **`pipelineFor(this.lastUiPass)`** を使い、
  `pipeline == null` なら「accepted geometry has no render pass」で落ちる ＝ **地形パス用のパイプライン**で、
  **深度はパス側にある**（状態キー側も `depthTest=1 / depthWrite=1` を確認済み）。
- **したがって残る候補は2つに絞られた**:
  1. **合成行列の深度行**（射影×モデルビューの z 成分が GL と違う）
  2. **描画順**（ウィンドウが地形より先に描かれ、深度バッファがまだ空）
- **次の計器（1つ・決定的）**: **フレーム内の描画順**を一度だけ記録する
  （地形パスの記録と `flushImmediateWindow` の呼び出しの**前後関係**、および各ウィンドウの**深度アタッチメントの有無**）。
  順序が正しければ深度行、逆なら順序の問題と一意に決まる。
- **状態の整理（重要）**: **既定ではモデル描画 off**（§0.64）なので、**ユーザーに見える既定の絵は正しい**。
  この調査は「モデル経路を既定に戻す」ための前提を詰めている段階である。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / clean / PASSED / VUID 0。

## 0.66 モデル描画: 深度状態は正しい（計器で確認）／2つの仮説を数値で棄却

- **計器1（状態）**: モデル描画が渡される状態を一度だけ出す。
  ```
  CELTIUM_MODEL_STATE list=2 depthTest=1 depthWrite=1 cull=0 blend=1 glDepthTest=true glDepthMask=true key=166141458652
  ```
  ＝ **深度テストも深度書き込みも有効**で、ライブ GL 状態とも一致。**状態は原因ではない**。
- **計器2（行列）**: ライブ行列とキャプチャ時（コンパイル時）行列の平行移動を並べる。
  ```
  CELTIUM_MODEL_MATRIX list=2 liveMVt=(0.514,-0.804,-0.689) capturedMVt=(0.347,-1.706,-0.74) liveProj=(0.803,1.428,-1.0)
  ```
  `liveProj[10] = -1.0` ＝ **GL の深度規約**（Vulkan の 0..1 ではない）。
- **棄却した仮説（数値つき）**:
  1. **「ライブ射影に二重リマップ」**: `-Dceltium.vulkan.modelRemapDepth=false` で素通しにすると差は
     **4.56 % → 6.01 %** に**悪化**。＝ リマップは必要で、**フラグは撤去した**（不要な分岐を残さない）。
  2. **「グループ描画側でも変換している」**: グループは `vulkanProjection(...)` を通すが、これは
     **Y 規約**のための変換（コード自身の注記「Positive height: … why the Vulkan convention is used unchanged」）で
     **深度の二重変換ではない**。仮説1と整合する。
- **確定したこと**: モデル描画の**状態**（深度・ブレンド・カリング）と**深度リマップの有無**は原因ではない。
  残るのは**描画結果の深度値そのもの**（射影×モデルビューの合成）か、**描画順**。
- **次の計器（具体・1つ）**: モデル経路の**最終的な合成行列**（射影変換後）を、**同じ瞬間の即時経路の合成行列**と
  並べて出す。両者が一致していれば深度値ではなく**順序**の問題、違えば**行列の合成**の問題と一意に決まる。
- **受入（AMD、既定＋`modelDraw=true`＋`modelQuadLimit=1`）**: exit 0 / **clean** / **PASSED** / **VUID 0**。

## 0.65 モデル描画の欠陥を**画像で名指し**: クワッドが深度で遮蔽されずに描かれている

- **切り分け（コードが用意していた実験を使った）**: `-Dceltium.vulkan.modelDraw=true -Dceltium.vulkan.modelQuadLimit=1`
  ＝ **各リスト1クワッドだけ**を native で描く。差は **4.56 %**（全クワッド 18.49 %、モデル描画 off 1.25 %）。
  ＝ **1クワッドでも既に見える** ＝ 原因は**多数のクワッドの蓄積ではなく、メッシュ／描画経路そのもの**。
- **画像（左＝1クワッド native、右＝GL のみ、`.celtium-work/onequad-compare.png`）**:
  **左だけに暗赤色の四角が地形と空の両方に散在**している。右は地形のみで正しい。
  ＝ **クワッドが深度で遮蔽されていない**（地形の上にも空の上にも同じように出る）。
  全クワッド版の「左半分を覆う巨大な黒い形状」も、**遮蔽されない多数のパーツが重なったもの**として説明がつく。
- **キャプチャ側は正しい（確認済み）**: 保存頂点は `v0=(0.063,-0.125,…)` の**モデル空間の小さい値**で、
  23 floats/quad は「位置12＋UV8＋面法線3」＝**キャプチャ自身の格納形式**（不一致ではない）。
  メッシュは `Layout(20, 0, 12, …)` ＝ stride 20（位置3＋UV2）で、**キャプチャの `modelView` は要約表示にしか使われていない**
  （＝焼き込みによる二重変換ではない）。描画は `glGetFloatv` の**再生時行列**を使っている。
- **次の一手（具体）**: モデル描画の**状態キー**を、同じクワッドを即時経路で描いた場合と突き合わせる。
  特に **depth test / depth write / cull**（pass 自体は共有深度を持つ window の pass なので、疑うのは状態側）。
- **測定の位置づけ**: 差は **PASSED のまま**（verdict は捉えない）。**画像比較だけが見る**。

## 0.64 **モデル描画を既定 off に戻した**（既定ビルドの忠実度 18.49 % → 1.25 %）＋ 原因の隔離

- **画像を見て確定（教訓どおり画像を先に見た）**: 差の領域を切り出して並べると、**native 側だけが左半分を覆う巨大な黒い形状**
  （地形の線が一点に収束）を描いていた ＝ **モデルの変換／ジオメトリが壊れている**。GL 側は地形のみで正しい。
- **隔離（`-Dceltium.vulkan.modelDrawOnlyList=2`、既存スイッチ）**:

| 構成 | entity 場面 vs GL 経路（>8 が異なる） |
|---|---|
| 全リスト native（既定だった） | **18.49 %** |
| **リスト2 のみ native** | **2.10 %**（`modelDraws=811`） |
| **モデル描画 off（今回の既定）** | **1.25 %**（平均絶対差 0.676） |
| 対照: GL 同士 | 0.01 %（場面は決定的） |

  ＝ 欠陥は**表示リストの native 描画**にあり、**リスト2 だけでも 2.10 %** 残る（`displayListOrigins=2` なので 2 と 3 の両方が疑わしい）。
- **対応（既定の是正）**: `celtium.vulkan.modelDraw` の既定を **false** に戻した。
  **コード自身のコメントが「Lab switch: off by default。一致するまで既定を変えない」と書いていた**のに既定が true に
  なっていた（地形だけの比較では**実体を消しているので見えない**ため、その受入がこの欠陥を見落としていた）。
  コメントに**今回の測定値**を書き込み、**0 になるまで off** と明記した。
- **受入（AMD、entity 場面、既定）**: exit 0 / **clean** / **PASSED** / **VUID 0**、`modelDraws=0`、
  **既定 vs GL 経路 = 1.25 %**（変更前 18.49 %）。
- **残る未解決（2件・数値つき）**: (i) 既定に残る **1.25 %**、(ii) リスト2 単独の **2.10 %**。
  どちらも**画像比較だけが見る**（verdict は PASSED のまま）。
- **次の一手**: リスト2 の**キャプチャ頂点の範囲と push constant の行列**を、GL が同じ瞬間に持っている値と突き合わせる
  （`CELTIUM_MODEL_QUAD`／`CELTIUM_MODEL_TEXTURE` の既存計器を使う）。

## 0.63 **entity 場面の対照実験で native モデル描画の実欠陥を確定**（場面は決定的、native だけが 18.49 % 違う）

- **場面を決定的にした**: 出したモブに `setNoAI(true)` と `enablePersistence()` を付け、tick 60 の位置に固定した。
- **対照実験（AMD、同一場面、`keepEntities`、120 s、3 run）**:

| 比較 | 平均絶対差 | >8 が異なる | 最大 |
|---|---|---|---|
| **GL run 1 vs GL run 2（対照）** | **0.029** | **0.01 %** | 219 |
| **native vs GL run 1** | **31.591** | **18.49 %** | 255 |
| **native vs GL run 2** | **31.563** | **18.49 %** | 255 |

  ＝ **場面は決定的**（GL 同士は 0.01 %）なのに、**native モデル経路だけが 18.49 % 異なる**。2回の GL run で同値。
- **差の位置（局所化）**: 差は**左半分に集中**（列バンド 61 % / 61 % / 23 % / 0 / 0 / 0 / 0 / 0、**右半分は 0.0 %**）、
  行は下 2/3（0 / 3 / 14 / 28 / 35 / 27 %）、外接矩形 x=0..1279 y=31..719。**モブが立っている領域**と一致する。
- **確定（重要）**: **`displayListCancelled == modelDraws` はモデル完成ではない**。
  取消は起きているが、**native が描く絵は GL と違う**。計画に書いた「手持ちの取消だけをモデル完成と扱わない」は**正しく、
  この測定で裏づけられた**。この欠陥は**既定の経路**にある（実験フラグではない）。
- **計器**: native run `modelDraws=14862 / displayListCancelled=14862`、GL run `modelDraws=0`（`-Dceltium.vulkan.modelDraw=false`）。
  両方 PASSED・clean・VUID 0（＝**verdict はこの差を捉えない**。画像比較だけが捉える）。
- **次の一手（原因の切り分け）**: (a) **モブ1体の小場面**で差を最小化し、(b) native モデル経路の
  **テクスチャ束縛・行列・UV**（§0.11 の `CELTIUM_MODEL_TEXTURE` / `CELTIUM_MODEL_QUAD` 計器）を
  GL 経路の同じ値と突き合わせる。**画像差の位置がモデル領域に限定されている**ので、切り分けは可能。

## 0.62 **entity 場面を用意し、モデルが既定で native 描画されていることを実測**（modelDraws 14990＝取消 14990）

- **fixture に実体場面を追加**: `-Dceltium.validation.keepEntities=true`。比較用の実体除去を止め、tick 60 に
  **固定の4体**（creeper / zombie / spider / enderman）を明示的に出す（自然スポーンに任せないので毎回同じ構成）。
  受入の証拠は計器（`CELTIUM_ENTITIES_SPAWNED tick=60 nonPlayer=4`）で、**画像比較用ではなく被覆用の場面**である。
- **実測（AMD、既定、entity 場面、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 859、`presentedFromVulkan=848`。

| 計器 | 値 |
|---|---|
| **`modelDraws`** | **14990** |
| **`displayListCancelled`** | **14990**（**全て取消＝GL の描画は抑制**） |
| `displayListOriginCancels` | 10970 |
| `displayListOrigins` / `displayListReplaysPassClosed` | 2 / 15885 |
| origins | `WorldVertexBufferUploader.func_181679_a ← Tessellator`（＝表示リスト経路） |
| `acceptedByFormat` | `5:795 6:5632 7:4988 9:78`（**8 は 0**） |

- **確定（従来の記述の訂正）**: **entity/モデルは即時経路（format 8）ではなく表示リスト経路で描かれ**、
  **既定で native が引き受けている**（`displayListCancelled == modelDraws`）。
  ＝ 計画の「モデル専用native描画は実験フラグ側に残り」は**古い**。**実験フラグではなく既定の経路**である。
- **format 8 が 0 の意味**: entity が「描けない」のではなく、**entity は format 8 では供給されない**（供給は表示リスト側）。
  `CELTIUM_ENTITY_REFUSED`（カスタムプログラム束縛時の拒否）は**この場面でも発火していない**。
- **未確認（次の受入）**: **entity 場面の画像等価**（表示リスト native と GL 描画を同一場面で比較）。次はこの比較を取る。

## 0.61 entity/TESR の現状を確定（**比較場面から実体を除いている**／format 8 はカスタムプログラム束縛で拒否）

- **判明した前提（コードで確認）**: 検証クライアントは**比較のため非プレイヤー実体を意図的に全除去**している
  （`ValidationClient` の「mob spawning is not seed-deterministic」節）。＝ **標準の比較場面にはエンティティが居ない**ので、
  `acceptedByFormat 8:0` は「entity が描けない」ではなく「**entity が供給されていない**」ことを意味する。
- **format 8（entity レイアウト）の拒否条件（コードで確認）**: `boundProgram != 0`
  ＝ **カスタムシェーダプログラムが束縛されている**とき拒否し、`immediateLastRefusal = "a custom shader program is bound"`。
  `CELTIUM_ENTITY_REFUSED program=… texture=…` と `CELTIUM_ENTITY_REFUSED_SITE`（10フレームのスタック）を**一度だけ**出す計器がある。
- **この計器はまだ一度も発火していない**（p322-gui・p344/p355/p357/p359/p360・p361 のいずれにも 0 行）。
  ＝ **これまでの全場面で、カスタムプログラム束縛下の entity 供給が起きていない**。
- **ポストプロセス場面（バニラ ShaderGroup）は今回発火しなかった**: `-Dceltium.validation.postprocess=true` を渡した
  120 s の run で `CELTIUM_POSTPROCESS` が **0 行**、`POSTPROCESS` の痕跡も 0。
  分岐は `worldTicks == 26 && !celtium$postprocess && Boolean.getBoolean("celtium.validation.postprocess")` にあり、
  **`enabled = Boolean.getBoolean("celtium.validation")` が前提**。**発火しなかった理由は未確定**（次の計器は
  ハンドラ入り口で `enabled`・`worldTicks`・プロパティ値を一度だけ出すこと）。
- **受入（AMD、既定、`postprocess`＋`keepGui`、120 s）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 850、
  `offers 16000 / accepted 13282 / GL 本体 2745`（＝ポストプロセス無効時とほぼ同じ。**この場面でも steady-state は native**）。
- **意味**: entity/TESR の native 化は **「拒否されている」段階ではなく「場面が無い」段階**である。
  次は **(a) 実体を出す場面**（実体を消さない比較用の小場面）と **(b) ポストプロセスの発火確認**の2つを用意する。

## 0.60 **既定フレームバッファへ描く合成も native 化**（残る最大の GL 群 902 件/run を受理）

- **考え方**: MC の合成は**画面（既定フレームバッファ）へ重ねる**もの。present は「**前のフレーム**を出す」ので、
  フレーム末尾の合成を**共有イメージへ直接描けば**、次の present で正しく画面に出る。＝ 行き先が「画面」でも
  **共有イメージが画面そのもの**である adoption 下では native に行き先がある。
- **実装**: `supportedImmediateTarget()` の `fbo == 0` を、**adoption が有効で共有ターゲットが使えるときは
  その共有 FBO を使う**形に変更（`defaultFramebufferCompositesTaken` で数える）。adoption でないときは従来どおり拒否。
- **受入（AMD、既定＝フラグなし、同一場面）**: exit 0 / **clean** / **PASSED** / **VUID 0**、frames 905、
  `presentedFromVulkan=893`、`gameFramebufferIsShared=true`、

| 計器 | 変更前 | **変更後** |
|---|---|---|
| **`defaultFramebufferCompositesTaken`** | （拒否 874） | **902** |
| offers / **accepted** / **GL 本体** | 16000 / 13088 / 2939 | 16000 / **13202** / **2825** |
| 参照フレーム vs 提示画像（反転） | 0.072・0.05 % | **0.072・0.05 %**（**見た目の差なし**） |

- **意味**: **steady-state で GL が描く量はほぼ消えた**。残るのは
  **(i) 起動直後10フレームの本質的な拒否**、**(ii) pass 選択の少数**、**(iii) GL context の生成そのもの**。
- **正直な区別**: 「GL に渡した数」がゼロになったわけではない（起動直後と少数の pass 選択は残る）。
  ただしそれらは**native の描画先がまだ存在しない瞬間**のもので、**欠陥ではない**（§0.41/§0.58）。

## 0.59 **adoption を既定にした**（両GPU・フラグなしで PASSED、同一vendor画像等価を確認）

- **既定化の根拠（測定）**:
  - **Intel 同一vendor比較**（基準＝フラグ off の GL 経路、同一場面）: 基準スクリーンショット vs adoption 提示画像
    **0.039・>8 が 0.03 %**、基準提示画像 vs adoption 提示画像 **0.026・0.02 %**。
    （§0.52 の 0.98 % は **AMD フレームとの vendor 間差**だったことがこれで確定した。）
  - **AMD 同一vendor**: 0.001・0.00 %（§0.51）。
- **既定化**: `celtium.vulkan.adoptGameFramebuffer` を **既定 true**（`-D…=false` で旧経路に戻せる）。
- **既定での受入（フラグを一切付けない run）**:

| 計器 | AMD (RX 6400) | Intel (UHD 630) |
|---|---|---|
| exit / `validationClean` / `fixtureStatus` | **0 / true / PASSED** | **0 / true / PASSED** |
| VUID / クラッシュ | **0 / なし** | **0 / なし** |
| frames / `presentedFromVulkan` | 901 / **889** | 374 / **361** |
| `gameFramebufferAdopted` | 1 | 1 |
| `firstGlError` | **[null:0x0]** | **[null:0x0]** |
| offers / accepted / GL 本体 | 16000 / **13088** / 2939 | — |
| 参照（同一vendor GL 経路）vs 提示画像 | **0.072・0.05 %** | **0.066・0.04 %** |

- **意味**: **通常のゲームフレームが Vulkan 所有の color/depth で生産され、swapchain へ提示される**のが既定になった。
  bridge の往復 blit と GL 深度変換は走らない。**残る GL 依存は「GL context の生成」と
  「既定フレームバッファへ描く合成（post-present overlay 待ち）」**だけである。
- **旧経路**: `-Dceltium.vulkan.adoptGameFramebuffer=false` で bridge（往復 blit）に戻せる。**移行用として残す**。

## 0.58 無記名拒否の正体を特定: **既定フレームバッファへ描く合成**（＝行き先が無く、拒否が正しい）

- **stage マーカーで一撃特定**: 供給経路の判定点11箇所に段階番号を置き、無記名のときは理由に段階を載せた。
  結果は `stage=4` ＝ **`supportedImmediateTarget()` のゲート**。ところがゲートは**条件別に名乗る形に改訂済み**のはずで、
  実際には**元の1行形式に戻っていた**（＝理由が付かない状態）。
  → **ゲートの条件別名指しを入れ直した**（今回はコミット後に grep で残存を確認）。
- **確定した理由（AMD、adoption on、1サンプル）**:
  ```
  2 target: the default framebuffer is bound = 874
  ```
  ＝ format 2・stride 24・`GL_QUADS`・4頂点の**1枚クワッド**は、**既定フレームバッファ（画面）へ描く合成**として到着している。
- **評価（これは欠陥ではなく、正しい拒否）**: 既定フレームバッファは**共有イメージではない**ので、native の描画先が無い。
  **GL が描くのが正しく**、画像も一致する（PASSED・0.001〜0.09）。native に移すには
  **present の後に swapchain イメージへ重ねる経路**（post-present overlay）が必要で、それは別の機能単位である。
- **受入**: exit 0 / **clean** / **PASSED** / **VUID 0**、`offers 16000 / accepted 13290 / GL 本体 2737`。
- **記録（手順の教訓）**: **コミット済みの改訂が実行中の jar から消えていた**（ゲートが元の1行形式）。
  ファイルを巻き戻す操作のあとに**改訂の残存を grep で確認していなかった**ことが原因。
  **今後は「入れた修正が今もそこにあるか」を commit 直前に確認する。**
- **意味（条件 (b) の残量の性格）**: 起動直後の本質的な拒否・**既定フレームバッファへの合成（post-present overlay 待ち）**・
  pass 選択の少数、という**性質の違う群**に分かれた。**「GL に残っている＝未実装」ではない**。

## 0.57 無記名の拒否を**バッチ種別で分割**し、正体を1つに絞った（format 2・stride 24・mode 7＝GL_QUADS・count 4）

- **やったこと**: 入口で入れる無記名マーカーに**バッチの種別**（format / stride / drawMode / vertexCount）を載せた。
  942 件が一つの数字だった状態から、**群が1つに特定**できた。
- **判明**: 残る無記名拒否は
  `2 unrecorded (format=2 stride=24 mode=7 count=4)` ＝ **1枚のクワッド**（`GL_QUADS` = mode 7、4頂点）で、
  **format 2 の合成/オーバーレイの1枚クワッド**である。＝ **既知の合成ファミリ**と一致する。
- **併せて4箇所の拒否に理由を持たせた**（`submitImmediateBatchTimed` の `gpuStalled`、present が画面に届かない経路、
  present が例外を投げた経路、シェーダプログラムを用意できない経路）。監査の「拒否は原因を名乗る」に沿う。
- **受入（AMD、adoption on、同一場面）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `offers 17000 / accepted 13960 / GL 本体 3067`（**受理は今roundで 13101 → 13960 と増加**）。
- **静的な追跡は打ち切った（記録）**: 「メソッド先頭から return までの間に理由代入があるか」を機械的に判定しようとしたが、
  **メソッド境界の自動検出が複数行シグネチャで失敗**し、false negative になった。**動的な種別マーカーのほうが確実**だった。
- **次の一手（対象が1つに絞られた）**: format 2・stride 24・mode 7・count 4 の**1枚クワッドを拒否している条件**を名乗らせる。

## 0.56 format 2 の「理由なし」拒否を可視化（古い理由の持ち越しを断った）＋ null で壊れた1箇所を修正

- **format 2 の 886 件が `none` と記録されていた理由**: `"none"` は**成功経路**が入れる値（＝拒否理由なし）だった。
  つまり**理由を設定しない拒否経路**が `false` を返し、集計が**直前の成功で残った値**を読んでいた。
  ＝ 監査が求める「拒否は原因を名乗る」が、**stale な読み出し**で満たされていなかった。
- **修正**: `submitImmediateBatch` の**入口で理由を必ずクリア**（非 null のマーカー `"unrecorded"`）。
  これで拒否は**その呼び出し自身の理由**だけを持つ。
- **結果（AMD、adoption on）**: 集計が **`2 no reason recorded=875`** と出るようになり、
  **format 2 は「理由を設定しない経路」で拒否されている**ことが確定した（＝次に名指しする対象が1つに絞れた）。
  併せて `accepted 13101 → 13287`、`GL 本体 2926 → 2740` と改善。
- **途中の失敗（記録）**: 最初 `null` でクリアしたところ、理由を **dereference する呼び出し側**が
  **NullPointerException** を出して fixture が FAILED になった（`exit=1`）。**非 null のマーカー**に変えて解消。
  ＝ 「消したつもりで別の契約を壊す」の実例として残す。
- **次の一手**: format 2 の「理由を設定しない経路」を1つに特定して名乗らせる（`no reason recorded` が手がかり）。

## 0.55 **所有の分裂（同じ事実を2箇所が持つ）を1つにし、format 5 の拒否をゼロにした**

- **根因（goal 項目2 が名指ししていた「所有の分裂」そのもの）**: 「全面内容を受け取った」という**同じ事実**を
  **2つの所有者**が持っていた。
  - `VulkanTextureRegistry.markFullyInitialized(id)` → **レジストリの Set** に入れる
  - `VulkanGlTexture.hasFullContent()` → **インスタンスのフラグ**を読む
  全面供給は前者だけを更新するので、**後者を読むディスクリプタ判定は永遠に偽**のままだった
  （測定: `the mirror for texture 29 has never received its full content` が 709〜801 件/run）。
  さらに、供給条件そのものが `get(id) == null`（ミラー皆無）だけで、**「ミラーはあるが部分書き込みのみ」**という
  A04 の本命を対象外にしていた。
- **修正（1つの根因に producer/consumer を揃える）**:
  1. 供給条件を `existing == null || !existing.hasFullContent()` に拡張（＝A04 のケースも同じ全面アップロードで満たす）。
  2. `markFullyInitialized(id)` が**イメージ本体にも印を付ける**ようにし、判定と供給が同じ事実を見るようにした
     （`VulkanGlTexture.markFullContentArrived()`）。
- **受入（AMD、adoption on、p318 と同一場面）**:

| 計器 | A04 前 | **A04 後** |
|---|---|---|
| `fixtureStatus` / `validationClean` / VUID | PASSED / true / 0 | **PASSED / true / 0** |
| offers / **accepted** / **GL 本体** | 15000 / 11651 / 3376 | **16000 / 13101 / 2926** |
| **format 5 の拒否** | **709〜801** | **0（消滅）** |
| frames | 882 | 906 |
| 参照フレーム vs 提示画像（反転） | 0.001 | **0.091・>8 が 0.05 %** |

- **残る唯一の非起動時拒否**: **format 2 の 886 件**（まだ理由が `none` ＝ 別経路が無言で拒否）。**次はこの経路を名指しする**。
- **註**: 画像差 0.05 % は、新たに native 化した粒子と書き出し要求・捕捉の2フレーム差の範囲で、GUI 場面で観測した 0.04 % と同程度。

## 0.54 adoption 下の拒否の**理由を名指しできるようにし**、format 5 の実因を特定（部分ミラー＝監査 A04 のクラス）

- **やったこと（いずれも「拒否は原因を名乗る」という監査の指摘に沿う）**:
  1. `supportedImmediateTarget()` の**4条件をそれぞれ名指し**する形へ分割（既定フレームバッファ／viewport 原点／
     フレーム目標なし／viewport サイズ不一致／契約違反）。**呼び出し側がメッセージを上書きしていた**ため、
     ゲートの理由が消えていた（＝820件が無記名だった原因）。
  2. **lightmap の遅延供給を1メソッドに統合**（`ensureLightmapMirror()`）し、**ディスクリプタ判定からも呼ぶ**。
     供給が判定より後ろにあったため、再確保の窓で粒子バッチが拒否されていた（798件/run）。
  3. **粒子経路がディスクリプタ判定の理由を上書きしない**ようにした（未設定のときだけ既定文言を入れる）。
- **受入（AMD、adoption on、同一場面＝p318 と同条件）**: exit 0 / **clean** / **PASSED** / **VUID 0**、
  `offers 15000 / accepted 11651 / GL 本体 3376`（前 15000/11526/3499）、
  **参照フレーム（p318）vs 提示画像 = 平均絶対差 0.001・>8 が 0.00 %**。
  **註**: 途中で `weather=true` の run と比べて 100 % 差が出たが、**それは別場面のため**で回帰ではない。
  **同一場面で比較すること**を改めて記録する。
- **判明した format 5 の実因（次に潰す対象）**:
  `the mirror for texture 29 has never received its full content` ＝ **部分書き込みしか受けていないミラー**で、
  コード自身のコメントどおり**監査 A04 のクラス**（atlas/texture を全面供給するまで GL に渡すのが正直な扱い）。
  ＝ **計画の項目2（texture 所有の統合・全面供給）がそのまま次の実装**である。
- **残る不明**: format 2 の 811 件はまだ**理由が `none`**（別経路が無言で拒否している）。次はこの経路を名指しする。

## 0.53 adoption 下で**受理が激減していた回帰を修正**（受理ゲートが adopted 状態を契約違反と判定していた）

- **計測で回帰を発見**: adoption を入れると受理が `9042 → 982`、GL 本体が `1985 → 17045` に**悪化**していた
  （描画結果は正しい＝PASSED・画素一致だが、**native の割合が下がる**ので放置できない）。
- **理由**: 拒否の理由が**一律** `target is outside the RGBA8/D24/full-viewport bridge contract`
  （format 6 6844・7 6200・2 982・5 975）。実体は `VulkanFrameTarget.currentFramebufferViolation()` が
  **RGBA8＋D24 レンダーバッファ**の契約で adopted 状態（**D32F テクスチャ深度**）を違反と判定していたこと。
  `matches()` と `endFrame` に入れたのと**同じ形の見落とし**。
- **修正**: 束縛 FBO が共有 FBO のときは `currentFramebufferViolation()` が `null` を返す
  （判定対象はこのクラス自身が作ったオブジェクトで、入口の liveness 検査が既に見ている）。
- **受入（AMD、duration 120 s、adoption on、`presentReadback`＋`dumpPresentedImage`）**:

| 計器 | 基準（GL FBO） | adoption 修正前 | **adoption 修正後** |
|---|---|---|---|
| offers | 11000 | 18000 | **15000** |
| **accepted** | 9042 | **982** | **11526** |
| GL 本体 | 1985 | 17045 | **3499** |
| `fixtureStatus` | PASSED | PASSED | **PASSED** |
| `validationClean` / VUID | true / 0 | true / 0 | **true / 0** |
| frames / `presentedFromVulkan` | — / — | — | **898 / 886** |
| `firstGlError` | — | [null:0x0] | **[null:0x0]** |
| 参照フレーム vs 提示画像（反転） | — | 0.001 | **0.012・>8 が 0.00 %** |

- **残る拒否（adoption 下・1サンプル）**: `format 2: 830`・`format 5: 812`・`format 6: 176`・`format 9: 17`・
  `format 4: 12`・`1/7/3/8: 各8〜11`。**次は format 5（粒子・ライトマップ）と format 2（合成）の理由**を読む。
  （基準時は format 5 の拒否は 26 だったので、**adoption 下で format 5 が増えている**点も確認対象。）

## 0.52 **(b) の本体を Intel でも受入**（bridge/sync/memory 経路のため両GPUで確認）

- **受入（Intel UHD Graphics 630 / Mesa、duration 120 s、`adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| **exit / `validationClean` / messages / `fixtureStatus`** | **0 / true / 0 / PASSED** |
| VUID / クラッシュ | **0 / なし** |
| frames | 390 |
| **`presentedFromVulkan`** | **365** |
| **pass 失敗** | **なし** |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]** |
| GPU | `Mesa Intel(R) UHD Graphics 630 (CFL GT2)` |
| 参照（AMD の GL 経路フレーム）vs 提示画像（反転） | 平均絶対差 **0.168**・>8 が 0.98 %・最大 126 |

- **註（正直な区別）**: 画素比較の参照は **AMD のフレーム**なので、**この 0.98 % は vendor 間の差**を含む。
  **同一 vendor 内（AMD の adoption 対 AMD の GL 経路）では 0.001・0.00 %** だった。**vendor 間の画素一致を主張しない**。
- **意味**: **adoption（ゲームが Vulkan 所有 color/depth に直接描画）は AMD/Intel の両方で PASSED**。
  同期・メモリ・interop 経路の変更なので、これは計画が求める「早期の両GPU確認」を満たす。

## 0.51 **(b) の本体が受入成立**: ゲームが Vulkan 所有イメージに直接描画し、PASSED・提示 997・画素一致 0.001

- **残っていた1件の正体**: `endFrame` の契約検証だった。**同じ検証が2行連続で重複**しており（以前のパッチ痕跡）、
  **ガードしたのは1つ目・投げていたのは無条件の2つ目**だった。重複行を削除して解消（`errors=2883` の正体もこれ）。
- **受入（AMD、duration 120 s、`-Dceltium.vulkan.adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| **exit / `validationClean` / messages / `fixtureStatus`** | **0 / true / 0 / PASSED** |
| VUID / クラッシュ | **0 / なし** |
| frames | **1020** |
| **`presentedFromVulkan`** | **997** |
| **pass 失敗（4種すべて）** | **なし**（`frame begin failed` も `frame end failed` も 0） |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]** |
| **参照フレーム vs 提示画像（上下反転）** | **平均絶対差 0.001・>8 が 0.00 %** |

- **意味（条件 (b) の到達点）**: **ゲームの通常フレームが Vulkan 所有の color/depth で生産され、swapchain へ提示される**。
  `beginFrame`/`endFrame` の**往復 blit も GL 深度変換も走っていない**。
  **残る GL 依存は「GL context の生成」と「GL がまだ描いている約2割（format 2 の非画面合成 709 等）」**であり、
  それらは**adoption 後は共有イメージへ直接書かれる**（＝表示経路にはもう GL のコピーが介在しない）。
- **フラグ**: 既定 off のまま（`-Dceltium.vulkan.adoptGameFramebuffer=true`）。**残る GL 描画を native へ移してから既定化**する。
- **次の実装単位**: **format 2 の非画面合成 709 を native へ**（行き先が共有イメージになったので、これで初めて可能になる）→
  その後 **GL context 自体の除去**（条件 (b) の残り）。

## 0.50 **ゲームが Vulkan 所有イメージへ直接描画し、提示が成立した**（(b) の本体が動作）

- **原因は2つの blit の未ガードだった**（前節の続き）: `beginFrame` の呼び出し列は
  `drainGlErrors()` → `glGetInteger` → **`glBlitNamedFramebuffer(fbo, sharedFbo, GL_COLOR_BUFFER_BIT)`** →
  **`glBlitNamedFramebuffer(fbo, scratchFbo, GL_DEPTH_BUFFER_BIT)`** → `convertDepth`（ガード済み）→ `glCheck`。
  adoption 後は `fbo == sharedFbo` なので**1つ目は画像の恒等 blit ＝ `GL_INVALID_OPERATION`**、2つ目も不要。
  **ガードは前回の patch スクリプトが「直前 260 文字に `sharedGameFramebuffer` がある」と誤判定して適用を飛ばしていた**
  （実際は分岐の記述が近くにあった）。**行単位で当てて解消**。
- **受入（AMD、duration 120 s、`-Dceltium.vulkan.adoptGameFramebuffer=true`）**:

| 計器 | 値 |
|---|---|
| frames | **987** |
| **`presentedFromVulkan`** | **960**（従来は 0） |
| **`gameFramebufferAdopted`** | 1 |
| `firstGlError` / `frameEntryGlError` | **[null:0x0] / [null]**（GL エラーなし） |
| `validationClean` / VUID / クラッシュ | **true / 0 / なし** |
| **参照フレーム vs 提示画像（上下反転）** | **平均絶対差 0.026・>8 が 0.00 %** |
| `fixtureStatus` | **FAILED**（下記の残欠陥による） |

- **意味**: **ゲームが Vulkan 所有の color/depth に直接描画し、そのフレームが swapchain へ提示され、内容は GL 経路と
  画素単位で一致する**。`beginFrame`/`endFrame` の **両方向の blit と GL 深度変換は走っていない**（＝条件 (b) の本体）。
- **残る欠陥（fixture が FAILED にする原因・1点）**:
  `error: the Vulkan terrain backend did not render cleanly: passes=2883 skipped=0 errors=2883 lastDraw=62`
  ＝ **パスは全部走り（skipped=0）描画もされている（lastDraw=62）**が、**パスごとに1件のエラーが計上**されている。
  次の計器は **この `errors` が何を数えているかを名指しすること**（パス内の例外か、endFrame のハンドオフか）。
- **フラグ**: 既定 off のまま（`-Dceltium.vulkan.adoptGameFramebuffer=true` で有効）。**移行用の bridge を置き換える経路**
  なので、残欠陥を閉じてから既定化を判断する。

## 0.49 **adoption は成立した**（計器で確定）。残るのは差し替え後に他所で出る保留 `GL error 0x502`

- **前節の仮説は棄却**（レポートで確認）: `frame target recreation` は **0 回**（再生成されていない）。
  `matches()` が adopted 状態を受け入れる形にしたうえで再測定したが、症状は同じだった。
- **決め手は失敗メッセージへの計器**（bound / `gameFbo` / `sharedFbo` / adopted / useRemembered を埋め込んだ）:
  ```
  frame begin failed: beginFrame: RGBA8 colour copy and D24 to D32 depth conversion
      [bound=7 gameFbo=7 sharedFbo=7 adopted=true]: GL error 0x502
  ```
  ＝ **束縛 FBO・記憶した宛先・共有 FBO がすべて一致し、adopted も true**
  ＝ **ゲームのフレームバッファ差し替えは機能している**（blit と深度変換はスキップされている）。
- **さらに adoption の瞬間の GL エラーを記録**（Mixin から `glGetError` を渡す計器）:
  ```
  gameFramebufferAdopted = 1
  adoptionGlError        = 0x0     ← 差し替え自体はエラーを残さない
  ```
  ＝ **保留中の `0x502` は「差し替えの後、最初の `beginFrame` の検査まで」の間に他所で発生**している。
- **次の一手（位置が確定している）**: その窓の中を二分する計器を置く
  （(a) 差し替え直後の bind 完了時、(b) `beginFrame` 入り口の `GlState` 直後）。
  これで「MC の描画か、こちらの前処理か」が一意に決まる。**仮説を増やさず、この2点だけを測る。**
- **対応**: 差し替え（フラグ経路）は**巻き戻した**。**失敗メッセージの計器だけを残した**（フラグに依存せず、
  診断として常に有効）。ビルド緑。
- **註**: ここまでで (b) の**設計は完成**しており、**実装も動いている**（差し替え・深度テクスチャ・契約・記憶した宛先）。
  残りは**この 0x502 の発生源1点**である。

## 0.48 深度のテクスチャ差し替えを実装しても残る失敗: **別の beginFrame 呼び出し／別の target 実体**が次の焦点

- **実装したもの（1単位）**: (1) 共有 FBO を契約にする `beginFrame`、(2) `beginFrame()` を**記憶した宛先**で呼ぶ、
  (3) `FramebufferMixin` での adoption、(4) **`OpenGlHelper.glFramebufferRenderbuffer` を redirect し、
  共有深度（外部メモリの**テクスチャ**）を `glFramebufferTexture2D` でアタッチ**（外部メモリのレンダーバッファが無いため）。
  redirect は「**束縛 FBO が共有 FBO のときだけ**」発火する（`glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)` で判定）。
- **結果**: **`gameFramebufferAdopted=1`**、しかし **`presentedFromVulkan=0`／FAILED** は変わらず。診断も同じ2つ:
  `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` と
  `beginFrame: the bound draw framebuffer is the default framebuffer`。
- **確認したこと（切り分け）**:
  - `beginFrame()` が `beginFrame(true)` を呼ぶ patch は**適用済み**（ソースで確認）。
  - mixin は**登録されている**（プラグインの「Found 37 mixin classes」に含まれる）。
  - `OpenGlHelper.glFramebufferRenderbuffer` は **static** で署名も injector と一致（ソースで確認）。
  - **それでも2つのエラーが残る** ＝ 失敗している `beginFrame` は
    **別の呼び出し（または別の target 実体）**であり、`gameFbo` がその実体では 0 のままである。
- **次の計器（具体的・これで決まる）**: `beginFrame` の失敗時に
  **bound FBO・`gameFbo`・`sharedFbo`・`frameTarget` の同一性（hashCode）**を一緒に記録する。
  これで「別実体」か「別呼び出し」かが一意に決まる。
- **対応**: **既知の失敗を抱えたフラグ経路を残さない**方針で**巻き戻した**（HEAD に戻し、追加した mixin も削除。ビルド緑）。
- **この3ラウンドで確定した (b) の全前提（再掲・設計として有効）**:
  1. 共有 FBO を契約にする 2. `beginFrame` は記憶した宛先を使う
  3. **深度はテクスチャとしてアタッチ**（外部メモリのレンダーバッファはこのバインディングに無い）
  4. そのうえで blit 2箇所と束縛クエリを削除 5. format 2 の合成を native へ。

## 0.47 前提1は**このバインディングでは実現できない**と判明（外部メモリのレンダーバッファが無い）

- **前提1の実装を試みた**: 共有深度を「外部メモリのレンダーバッファ」としても公開する
  （`GL45C.glCreateRenderbuffers()` ＋ `glRenderbufferStorageMemEXT`）ことで、MC の `depthBuffer` に渡せるようにする。
- **結果**: **`GL45C.glCreateRenderbuffers()` は存在して通る**が、**`EXTMemoryObject` に renderbuffer 用の関数が無い**
  （`javap` で確認: `EXTMemoryObject` に `*Renderbuffer*` は1つも無い）。
  ＝ **LWJGL のこのバインディングでは、外部メモリから GL のレンダーバッファを作れない**。
- **意味（item 6 の構造）**: **MC の `Framebuffer` は深度を `glFramebufferRenderbuffer` でアタッチする**ため、
  **深度は Vulkan 所有へ移せない**。native のパスは深度を読み書きするので、
  **「色だけ共有・深度は GL」という混成では条件 (b) の通常フレームは成立しない**。
- **代替経路（次の実装候補・実在する API で足りる）**: MC のアタッチ呼び出しを**横取りしてテクスチャとして付ける**。
  MC は `OpenGlHelper.glFramebufferRenderbuffer(...)`（Forge のヘルパー）経由でアタッチしているので、
  **そこを Mixin で redirect し、共有深度を `glFramebufferTexture2D` でアタッチ**すれば、
  外部メモリの**テクスチャ**（これは既に作れている）で深度を Vulkan 所有にできる。
  **`Framebuffer` 側の変更は不要**（呼び出し1箇所を差し替えるだけ）。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。
- **未確認**: 上記 redirect が MC の深度の使い方（`glClear`・深度テスト・`GL_DEPTH_ATTACHMENT` の期待）と両立するかは**未検証**。
  次はこれを最小の場面で試す。

## 0.46 item 6 の (B) に必要な**2つの前提**が実測で確定（深度の型と、記憶した宛先の使用）

- **試作（§0.45 の続き）**: 差し替え時に**先に** frame target へ「これがゲームのフレームバッファだ」と教える
  （`noteGameFramebufferAdopted` で `gameFbo = sharedFbo` 等を設定）ことで、§0.45 の `gameFbo==0` 問題を解消した。
- **結果**: **`gameFramebufferAdopted=1`**（差し替えは1回で成立）だが **`presentedFromVulkan=0`／FAILED**。診断:

| メッセージ | 意味 |
|---|---|
| `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` | **MC は `depthBuffer` をレンダーバッファとしてアタッチする**のに、こちらは**テクスチャ id** を入れた → `GL_INVALID_OPERATION` |
| `beginFrame: the bound draw framebuffer is the default framebuffer …` | **ワールドパスは `useRememberedTarget=false` で `beginFrame` を呼ぶ**ため、記憶した宛先が使われない |
| `skipReason=translucent:_no_frame_target`、`passes=0 skipped=547` | 帰結（フレーム目標が立たないのでパスが全部スキップ） |

- **item 6 の (B) に必要な前提（これで具体化した）**:
  1. **共有深度を「外部メモリのレンダーバッファ」として公開する**（`glImportMemoryFdEXT` ＋
     `glNamedRenderbufferStorageMemEXT`）。MC の `Framebuffer` は深度を**レンダーバッファ**として扱うので、
     テクスチャでは型が合わない。**現在の bridge は D32 をテクスチャで持っている**ため、ここが実装対象。
  2. **ワールドパスの `beginFrame` が記憶した宛先を使うようにする**（`useRememberedTarget` の経路に乗せる）。
  3. そのうえで §0.45 の (A)（blit と束縛クエリの削除）。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。**次の実装単位は前提1（深度をレンダーバッファで公開）**で、
  これは **adoption と独立に検証できる**（bridge の深度アタッチが変わるだけ）。
- **註**: 前提2は `beginFrame` の呼び出し側（`VulkanTerrainGpuBackend`）の分岐なので、1箇所の変更で済む見込み。

## 0.45 共有 FBO を契約にする改訂でも失敗: **順序は「blit を先に消す」**（実測で確定）

- **試作（§0.44 の続き）**: `beginFrame`/`endFrame` を「束縛 FBO が共有 FBO なら契約検証・アタッチメント照会・
  入出力 blit・深度変換をすべて省き、共有イメージ自体を契約とする」形に組み直した（フラグ付き・fail-open）。
- **結果**: **`gameFramebufferAdopted=1154`** と差し替えは機能したが **`fixtureStatus=FAILED`／`presentedFromVulkan=0`**。
  診断に理由が2つ出た:

  | メッセージ | 意味 |
  |---|---|
  | `beginFrame: RGBA8 colour copy and D24 to D32 depth conversion: GL error 0x502` | 恒等 blit／変換が `GL_INVALID_OPERATION` |
  | `beginFrame: the bound draw framebuffer is the default framebuffer; the verified bridge needs an FBO …` | ワールドパスが**既定フレームバッファ束縛**で走った |
  | `did not render cleanly: passes=0 skipped=554 errors=1108` | パスが1つも描かれなかった |

- **根因**: **MC のフレームバッファ束縛プロトコルとの衝突**。MC はフレームの途中で**意図的に unbind（0 を束縛）**する
  箇所があり、差し替えると**ワールドパスが 0 束縛で走る**。bridge の `beginFrame` は「GL が何を束縛しているか」を
  **blit のために読んでいる**ので、その前提が崩れると壊れる。
- **確定した正しい順序（この失敗が示した）**: **先に blit と束縛クエリを消す**。
  **Vulkan のパスは GL の束縛を必要としていない**（描画先は `frameTarget` が持っている）。
  束縛を読む理由は**2つの blit のためだけ**なので、blit を消せば束縛は無関係になり、その後に差し替えれば衝突しない。
  → 段取りは **(A) blit 2箇所と束縛クエリを削除（提示画像を証拠に）→ (B) ゲームの FBO を共有へ差し替え**。
- **対応**: 試作は**巻き戻した**（HEAD に戻しビルド緑）。**(A) が次の実装単位**である。

## 0.44 item 6 の本体（FBO 所有権）は**フレームライフサイクルの置き換え**だと実証（試作は巻き戻し）

- **試作**: ゲームの主フレームバッファ（`mc.getFramebuffer()` と**同一性で**判定）の
  `framebufferObject`／`framebufferTexture`／`depthBuffer` を、共有イメージの GL オブジェクトへ差し替える
  （フラグ `-Dceltium.vulkan.adoptGameFramebuffer`、fail-open）。同一 FBO になる blit 2箇所は恒等コピーとしてスキップ。
- **1回目**: `gameFramebufferAdopted=0` ＝ 発火せず。原因は**私のガードが `frameOpen` を要求していた**こと
  （MC はフレームバッファを bridge の `beginFrame` **より前**に束縛するため、その時点では false）。ガードを直した。
- **2回目**: **`gameFramebufferAdopted=1142`** ＝ 差し替えは機能した。しかし **`fixtureStatus=FAILED`／`presentedFromVulkan=0`**
  ＝ **フレームが出なくなった**。
- **根因（構造）**: bridge は `beginFrame` で **「束縛されている game FBO が検証済みの契約（特定の GL テクスチャ＋
  レンダーバッファ）であること」を検証**している。差し替えると、その検証対象が**まさに共有 FBO になる**ので契約が破れる
  ＝ **ブリッジの begin/end 機構そのものが前提ごと無効になる**。
- **結論**: **item 6 の本体は「小さな付け替え」ではない**。必要なのは
  **フレームライフサイクルの置き換え**（ゲームが共有イメージへ直接描く前提で、検証・開始・終了・レイアウト受け渡しを
  組み直す）であり、**9割の GL 依存はこの一点に集約されている**。この試作は**巻き戻した**（HEAD に戻しビルド緑）。
- **次に必要な設計（順序）**: (1) 共有 FBO を「契約」にする（検証を共有側へ反転）、
  (2) `beginFrame`/`endFrame` を「GL のフレームバッファとの往復」から「共有イメージの前後処理」へ組み直す、
  (3) blit 2箇所を削除、(4) その上で format 2 の合成を native へ。**(1)(2) は不可分**である。

## 0.43 lightmap の供給漏れ（format 5）を解消: mod 側から unit を渡し、その場で取り直す

- **実装（モジュール境界を守る形）**:
  1. `celtium-vulkan` 側に **`celtium$setLightmapUnit(int)`**（**ただの int**。Minecraft クラスは持ち込まない）を足し、
     再確保で `lightmapGlId` が零化された場合に **その unit に束縛されている id を GL に問い合わせる**
     （旧 active unit は復元する＝状態変更ではなく問い合わせ）。
  2. `forge122` 側（`EntityRendererMixin` の world pass 先頭）で **`OpenGlHelper.lightmapTexUnit` の値を一度だけ渡す**
     （model draw が GL に束縛テクスチャを問い合わせているのと同じ形）。
  3. 供給は **id ごとに一度だけ**（`imageFor` → `uploadFromGlTexture` → `markFullyInitialized`）。
- **受入（AMD、weather、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **PASSED** / **VUID 0**、クラッシュなし。
  **`lightmapSuppliedOnDemand=2`**、**`lightmapSupplyFailure=[none]`**、
  そして **`reason=lightmapped …` の拒否行が消滅**（以前は毎 run 12〜23 件）。
- **分岐がデッドでないことの確認**: 前回の測定で `lightmapSuppliedOnDemand=0` だったのは
  **条件のどれかで止まっていた**ためで、**拒否メッセージに `id=` と `unit=` を載せた**ことで切り分けられるようにした。
  その次の run で供給が 2 回走り、拒否が 0 になった ＝ **到達可能な経路**である。
- **意味**: 条件 (b) の3群のうち **(iii) 少数の供給漏れ**が閉じた。残るのは
  **(i) 起動直後 10 フレームの 80 件（本質的）** と **(ii) format 2 の 709 件（FBO 所有権の移動待ち＝item 6）**。

## 0.42 lightmap 未ミラー（format 5）の扱い: **id が零化されるため mod 側の計器が要る**（試作は巻き戻し）

- **試したこと**: format 3/5 のバッチが到着した時点で lightmap ミラーが無いとき、**その場で再供給**する
  （テクスチャで成功した「遅延全面供給」と同じ形）。GL id は**記憶から復旧できない**（再確保の経路が
  `lightmapGlId = 0` と**零化する**ため）ので、**MC 側の公開定数 `OpenGlHelper.lightmapTexUnit` のユニットに
  束縛されている id を問い合わせる**形を書いた。
- **結果**: **ビルドが通らない**。`celtium-vulkan` は **Minecraft に依存しない**モジュールであり、
  `net.minecraft.client.renderer.OpenGlHelper` を参照できない（**この依存の向きは設計として正しい**）。
- **対応**: **試作は巻き戻した**（HEAD の状態に戻し、ビルド緑を確認）。format 5 の拒否は**現状のまま正しい振る舞い**
  として残る（その時点で native の描画先が無いため）。
- **確定した次の一手**: 復旧には **mod 側（`forge122`）から lightmap ユニットの値を渡す経路**が要る。
  既に model draw が GL に束縛テクスチャを問い合わせている**同じ形**（Mixin 側で定数を読み、値だけを渡す）で足りる。
  **モジュール境界を越えて MC クラスを持ち込まない**という制約を、この試行が実証した。
- **量**: この群は 1 サンプルあたり **12〜23 件**（`5 lightmapped batch with no lightmap texture mirrored`）。

## 0.41 条件 (b) の残りを**性質で分離**: 起動直後のみの 80 件と、FBO 所有権に阻まれる 709 件

- **計器**: `lastUiPass == null`（その実体でまだ地形パスが走っていない）と「pass はあるがパイプラインが無い」を分けて数えた。
- **結果（AMD、duration 120 s、676 frames、exit 0 / clean / PASSED）**:

| 計器 | 値 |
|---|---|
| `noPassRefusals` | **80** |
| `noPassFrames` | **1..10**（＝**起動後 10 フレームのみ**） |
| 拒否理由 | `preflight: format=6/9 … (no pass has run yet in this instance)` |

  **起動直後の 10 フレームを過ぎると 0 件**。676 フレーム中 1.2 %。
- **読み方（(b) の性格分け）**:
  1. **80 件は本質的**: その実体でまだ**どのパスも走っていない**ので、**native の描画先（render pass）が存在しない**。
     拒否して GL に渡すのが正しい（**欠陥ではない**。捕捉は tick 200 なので受入には影響しない）。
  2. **709 件（format 2）は「画面合成以外の合成」**（overlay を framebuffer へ混ぜる・postprocess 系）。
     これらは**行き先が GL のフレームバッファ**であり、**FBO の所有権が Vulkan に移るまで native にできない**
     ＝ **item 6（GL context / bridge の除去）と一体**である。**postprocess の設計ではなく所有権の話**だと確定した。
  3. 23 件（format 5・lightmap 未ミラー）は下の節で扱う。
- **意味**: 条件 (b) の残りは「GL を消す作業」ではなく、**(i) 起動直後は本質的に GL に渡す**、
  **(ii) 709 件は FBO 所有権の移動待ち**、(iii) 少数の供給漏れ、という**3つの性質に分かれた**。
  次に着手できるのは (iii) である。

## 0.40 GUI / font / HUD の画像等価（項目5の未比較項目・提示画像経路で測定）

- **場面**: 三人称＋`keepGui`（HUD とフォントが描かれる）。証拠は**提示画像**（`dumpPresentedImage`）。
- **受入（AMD、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **fixture PASSED** / **dumped=true**。

| 比較 | 平均絶対差 | >8 が異なる | 最大 |
|---|---|---|---|
| 提示画像 vs スクリーンショット（上下反転） | **0.015** | **0.04 %** | 160 |
| **うち HUD 帯（上部 250 行）** | **0.000** | **0.00 %** | — |

  **HUD とフォントの帯は画素単位で完全一致**（差は 0）。画面全体の 0.04 % は、
  書き出し要求（tick 198）と捕捉（tick 200）の**2フレーム差**によるもので、帯の外に限られる。
- **意味**: **GUI / font / HUD は native 経路（format 7 等）で描かれ、原版と画素単位で一致する**。
  項目5で「未比較」としていた GUI 系が、**比較済み**になった。
- **併せて確認**: この場面でも提示が成立し（`presentedFromVulkan` が毎フレーム）、validation はクリーンである。

## 0.39 条件 (b) の作業量: **GL にまだ残っている量の内訳**（実測）

- **量**: `offers 11000 / accepted 9042 / glBodyCompleted 1985`（p318）、`13000 / 10643 / 2386`（p321）
  ＝ **8割は native に受理され、約2割（≈2000/run・7/フレーム）が GL に渡っている**。
- **内訳（拒否集計の1サンプル、合計 1172）**:

| 理由 | 件数 |
|---|---|
| **`2 target is outside the RGBA8/D24/full-viewport bridge contract`** | **709** |
| `8 preflight: format=8 texture=16 (no terrain pipeline for this pass)` | **128** |
| `1/6/9 preflight: … (no terrain pipeline for this pass)` | 9〜21 ずつ |
| `5 lightmapped batch with no lightmap texture mirrored` | 23 |

- **読み方**:
  - **709 件の format 2 は「画面合成以外の合成」**（overlay を framebuffer へ混ぜるもの・postprocess 系）で、
    監査 S17 が「この layout は画面合成だけのものではない」と記録しているもの。**正規の GL 作業**であり、
    native 化するには postprocess/overlay の設計が要る（`skipGlComposite` の条件は**画面合成のみ**を対象にしているので、
    これらは意図どおり残る）。
  - **128 件の format 8** と少数の format 1/6/9 は **「その pass 用のパイプラインが無い」**という preflight 失敗。
    §0.12 で扱ったのと同じ種類の問いで、**pass の選び方**の問題である。
  - **23 件の lightmap 未ミラー**は format 5（particle）でライトマップが無いケース。
- **意味**: 条件 (b) の残りは「GL を全部消す」ではなく、**この4群を順に native へ移すか、対象外と明示するか**である。
  **量と理由が数えられている**ので、次はこの順に潰せる。

## 0.38 copyback は**単独では外せない**（ブリッジが循環していることの実証）

- **試したこと**: `-Dceltium.vulkan.skipColorReturn=true`（`endFrame` の色の書き戻しを止める。既存の `SKIP_DEPTH_RETURN` と
  対になるスイッチ）＋ `skipGlComposite`。証拠は提示画像。
- **結果（AMD、duration 120 s）**: exit 0 / **validationClean=true** / messages 0 / **fixture PASSED** / **VUID 0** / クラッシュなし
  ＝ **壊れはしない**。しかし:
  | 比較 | 平均絶対差 | >8 が異なる | 最大 |
  |---|---|---|---|
  | 提示画像（copyback あり）vs 提示画像（なし） | **31.369** | **69.80 %** | 152 |
  | スクリーンショット（あり）vs 提示画像（なし） | 31.359 | 69.79 % | 152 |

  **GL 側スクリーンショットの平均は 211.7**（暗くならず**明るくなった**）。
- **根因（この結果が示す構造）**: ブリッジは**循環**である。`beginFrame` が
  **GL のフレームバッファ → 共有イメージ**をコピーし、`endFrame` が**共有イメージ → GL のフレームバッファ**を戻す。
  書き戻しだけを止めると、**GL 側が空のまま次フレームの `beginFrame` で共有イメージへ流し込まれ**、
  **native の内容が上書きされる**（だから提示画像まで変わる）。
- **意味（(b) への結論）**: **copyback は単独では外せない**。外すには
  **入ってくる方向の blit と、GL が描いている部分そのものを同時に**外す必要がある ＝ **条件 (b) の本体**
  （GL context を通常フレームから外す）と一体の作業である。**この順序を実測で確定できたことがこのターンの成果**。
- **次の計器**: `offers` / `accepted` / `glBodyCompleted`（＝GL の本体が走った回数）で
  **GL にまだ残っている量**を数える（下の節）。

## 0.37 失敗経路の限定確認（提示・線・合成の変更後）

- 提示は submit と wait を使い、線はパイプライン生成を、合成は受理ゲートを変えたので、**注入した失敗**で再確認した
  （AMD、duration 90 s、既定＝提示・線・合成の変更が入った状態）。

| run | harness | validationClean | fixture | game exit | クラッシュ | VUID | 失速の計器 |
|---|---|---|---|---|---|---|---|
| `failFenceWait=true` | 1 | **true** | **FAILED**（注入失敗として意図どおり） | 0 | なし | **0** | **`stalledStops=1`・`stalledTeardownSkips=548`** |
| `failSubmits=1` | 1 | **true** | **FAILED**（同上） | 0 | なし | **0** | `stalledStops=0` |

- **読み方**: **fence timeout は検出して停止し、teardown を548回スキップして資源を解放しない**
  （計画に記録済みの 547/549 と一致）。**ゲームは exit 0・クラッシュなし・VUID 0**。
  submit 失敗は**描画が欠けたことを fixture が FAILED として報告**する（黙って隠さない）。
  **harness の exit 1 はこの FAILED によるもので、注入した失敗の意図された結果**である
  （`validationClean` は true のまま＝Vulkan のエラーではない）。
- **意味**: 提示・線・合成の変更は、**失敗時の振る舞い（停止・解放しない・報告する）を壊していない**。

## 0.36 **GL の最終合成を外しても提示内容は同一**（条件 (a) の「最終合成が native」に到達・フラグ付き）

- **実装**: `-Dceltium.vulkan.skipGlComposite=true`（既定 off）。**提示済みフレームに対してのみ**（`framePresentedFromVulkan > 0`
  という**記録された事実**で判定）format 2 の画面合成バッチを**飲み込む**（GL の blit を走らせない）。
- **受入（AMD、`presentReadback`＋`dumpPresentedImage`＋`skipGlComposite`、duration 120 s）**:

| 計器 | 値 |
|---|---|
| `validationClean` / `validationExitCode` / `validationMessageCount` | **true / 0 / 0** |
| `fixtureStatus` / VUID / クラッシュ | **PASSED** / **0** / なし |
| **`glCompositesSkipped`** | **717** |
| `presentedFromVulkan` | **718** |
| **フラグ off のスクリーンショット vs フラグ on の提示画像（上下反転）** | **平均絶対差 0.017、>8 が 0.00 %** |
| 提示画像（off）vs 提示画像（on） | 平均絶対差 **0.001**、>8 が 0.00 % |

  **GL の最終合成を毎フレーム省いても、提示される絵は変わらない**（717/718 フレームで省略）。
- **なぜ GL 側スクリーンショットも変わらないのか（整合の説明）**: harness/ゲームのスクリーンショットが読むのは
  **世界フレームバッファ**（`mc.getFramebuffer()`）であり、それは **`endFrame()` の copyback が埋めている**。
  省略したのは**既定フレームバッファへの合成**であり、そちらは**もう表示に使われていない**（表示は Vulkan の present）。
  したがって「スクリーンショットが変わらないこと」と「合成を外したこと」は矛盾しない。
- **意味**: **条件 (a) の「最終合成・present が native」が、内容を変えずに成立した**。
  **残る GL 依存は描画そのもの（bridge・copyback・GL call）と GL context**（条件 (b)）である。
- **既定化はまだしない**: フラグは off のまま。次の段階で、**証拠経路を提示画像に一本化**してから既定化を判断する。

## 0.35 validation エラーの原因は**私の PNG 書き出し**だった（提示中に読む形へ修正・解消）

- **原因（harness の判定を読んで確定）**: `validationMessageCount=1`、
  `severity=ERROR vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage …, but the image has
  not been acquired …`。**私が追加した `dumpPresentedImage` が、提示済み（所有権を手放した）イメージを後から遷移させていた**。
  harness はこのメッセージで `validationClean=false` / `validationExitCode=1` を立てていた（**ゲームは exit 0・fixture は PASSED**）。
- **修正**: 書き出しを**リクエスト方式**にした。fixture は「次の提示でこのパスへ書け」と**フラグを立てるだけ**にし、
  **実際の読み戻しは提示処理の中（まだ所有権がある瞬間）**で行う（中心サンプルとまったく同じ位置・同じコピー処理）。
  さらに**捕捉の2 tick 前（tick 198）**に要求する（捕捉 tick は捕捉時に初めて記録されるため、198 では未設定だった）。
- **受入（AMD、`presentReadback`＋`dumpPresentedImage`、duration 120 s）**:

| 計器 | 値 |
|---|---|
| `validationClean` / `validationExitCode` | **true / 0** |
| `validationMessageCount` | **0** |
| `fixtureStatus` | **PASSED** |
| `presentedImageDumped` | **true** |
| 提示 PNG とスクリーンショット（そのまま） | 平均絶対差 52.956、>8 が 94.24 %、最大 178 |
| **同（上下反転）** | **平均絶対差 0.015、>8 が 0.00 %、最大 3** |

  反転比較の残差（0.015 / 最大3）は、**書き出し要求（tick 198）と捕捉（tick 200）の2フレーム差**によるもので、
  画素単位の一致は保たれている。
- **意味**: **GL に依存しない画像証拠**（提示画像そのもの）が、**validation クリーンな状態で**取れるようになった。
  これは「GL 合成を外す」ための前提である（証拠が GL のスクリーンショットに縛られなくなる）。
- **教訓**: 計器を足したら **harness の validation 判定まで読む**。ゲームが exit 0 で fixture が PASSED でも、
  検証層の ERROR は harness の判定を落とす。

## 0.34 **提示画像は画素単位で GL のフレームと一致**（差は行の並びだけだった）

- **GL に依存しない画像証拠を作った**: `VulkanSwapchain.dumpPresentedImage(Path)`（読み戻しのコピー処理を
  **中心サンプルと全画像で共通化**。バリアの往復＝presentable→transfer source→presentable は1箇所だけが行う）。
  backend に `celtium$dumpPresentedImage(Path)` を公開し、fixture が**捕捉時に** `screenshots/celtium-presented.png` へ書く
  （`-Dceltium.validation.dumpPresentedImage=true`）。
- **測定（AMD、`presentReadback`＋`dumpPresentedImage`、duration 120 s、640〜700 frames）**:

| 比較 | 平均絶対差 | >8 で異なる画素 | 最大 |
|---|---|---|---|
| そのまま | 52.949 | 94.24 % | 178 |
| **上下反転** | **0.000** | **0.00 %** | **0** |
| 左右反転 | 52.956 | 94.24 % | 178 |
| 両方反転 | 9.497 | 49.06 % | 133 |

  **上下反転で完全一致（平均絶対差 0.000・最大 0）**。**native swapchain に出たフレームは、ゲームが描いたフレームそのもの**である。
  差は**行の並びだけ**だった（ゲームのスクリーンショットは GL の読み出しを上下反転するため）。
- **途中で確定した2つのこと**: (1) 平均が完全一致するのに画素がほぼ全部違うのは**並びの違い**の signature である
  （並べ替えは平均を動かさない）。(2) スワップチェーンの形式は **`format=44`（B8G8R8A8 系）**で、チャンネル順は
  B,G,R,A。**チャンネル順は平均では絶対に判別できない**（この2点は記録に値する）。
- **意味**: 条件 (c) のうち **「native swapchain へ出した完成 frame」は画素単位で裏付けられた**。
  残るのは**実 window/compositor 出力の到達**（この環境の `DISPLAY=:0` の root は 0×0 で未確認）である。
- **未解決（別件として記録）**: この run は **ゲームは exit 0・fixture は PASSED** なのに
  **harness の `validationExitCode=1` / `validationClean=false`** だった。追加した PNG がスクリーンショット欄に増えたこと等、
  **harness 側の判定**を次に確認する（**この不一致を放置しない**）。

## 0.33 提示画像の不一致は**読み戻しの上限**が原因だった（§0.32 を撤回・提示内容は一致する）

- **原因**: `VulkanSwapchain` の読み戻しは `PRESENT_READBACK && this.presentReadbacks < 3` で、
  **最初の3フレームしか読んでいなかった**。`lastPresentedMean` は**起動直後のフレーム**の値であり、
  §0.32 で記録した 96.75 はそれだった。§0.16 で直した「別の瞬間の値」問題が、**1段下でそのまま残っていた**。
- **修正**: フラグが有効な間は**毎回読み戻す**（診断用フラグなのでコストは許容。件数は報告と上限のために残す）。
- **再測定（AMD、`presentReadback=true`、duration 120 s、exit 0 / clean / **PASSED**、640 frames、p50 13.51 ms）**:

| 値 | |
|---|---|
| `presentedImageMeanAtCapture` | **164.02** |
| **GL スクリーンショットの平均** | **158.72** |
| 差 | **5.30**（提示が1フレーム先行する分の残差） |
| 参考: スクリーンショットの HUD を除いた平均 | 137.39（差 26.6 → 提示画像は **HUD を含む**） |

- **結論**: **提示画像は GL が描いた完成フレームと同じ絵**である（HUD を含む）。**§0.32 の「一致しない」は撤回**する。
  §0.15/§0.19 の「平均が一致した」という観測は、**今度は正しい理由で**裏付けられた（同一場面・同一瞬間・毎フレーム読み戻し）。
- **教訓（2度目）**: 値を「いつ取るか」だけでなく、**その値がいつ作られたか**を確かめる。
  上限つきの計器は「最初の数回の値」を返し続ける。

## 0.32 **提示画像は GL が描いたフレームと一致しない**（同じ瞬間で測って判明・§0.15/§0.19 の楽観を訂正）

- **同じ瞬間の比較を実装した**: 捕捉時に `presentedImageMeanAtCapture`／`presentedImageFramesAtCapture` を記録する
  （backend に `celtium$presentedImageMean()`／`celtium$presentedFrameCount()` を公開し、fixture が捕捉時に読む）。
  §0.16 で「別の瞬間の値だから根拠にしない」とした宿題を、**同じ瞬間の値**に置き換えた。
- **測定（AMD、`presentReadback=true`、duration 120 s、exit 0 / clean / PASSED、676 frames）**:

| 値 | |
|---|---|
| `presentedImageMeanAtCapture` | **96.75** |
| `presentedImageFramesAtCapture` | 666 |
| **GL スクリーンショットの平均** | **158.71** |
| スクリーンショットから HUD 上部 250 行を除いた平均 | **137.93** |
| 同・下部 60 行も除いた平均 | **137.39** |
| 中央 scene のみ | 138.12 |

  **HUD を除いても約41段の差**が残る ＝ **「提示画像は HUD を含まない world フレーム」という仮説は棄却**。
  **提示画像は GL が描いたフレームと系統的に異なる**（暗い）。
- **訂正（§0.33）**: この「一致しない」は**読み戻しが最初の3フレームだけだった**ためで、
  提示画像は実際には**完成フレームと同じ絵**（164.02 対 158.72、差 5.3）。**この節の結論は撤回**。
- **次に潰す候補（この測定で絞れた）**: (a) 読み戻しの**範囲／形式**が違う、(b) **同期**のずれ（GL の書き込みが乗る前に読んでいる）、
  (c) 共有イメージの**レイアウト／所有権**。**次の計器**は「同じフレームで共有イメージと GL スクリーンショットの**画素ごとの差**を
  取る」こと（平均ではなく分布を見る）。

## 0.30 線パイプライン変更の限定回帰（即時経路全体に触れるため）

- 線の変更は **パイプライン生成・受理ゲート・キー** に触れるので、reload/resize/world switch と Intel で回帰を取った。

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ | `line primitives` 拒否 |
|---|---|---|---|---|---|---|---|---|
| AMD 通常 | 0 | true | **PASSED** | 671 | 12.93 ms | 0 | なし | **なし** |
| AMD reload | 0 | true | **PASSED** | 652 | 13.06 ms | 0 | なし | **なし** |
| AMD resize | 0 | true | **PASSED** | 604 | 16.19 ms | 0 | なし | **なし** |
| AMD world switch | 0 | true | **PASSED** | 667 | 12.90 ms | 0 | なし | **なし** |
| Intel 通常 | 0 | true | **PASSED** | 249 | 39.12 ms | 0 | なし | **なし** |

  **5 run すべて PASSED・VUID 0・拒否なし**。p50 も直近の水準内。**native の線は通常/reload/resize/world switch で安定**。

## 0.31 format 2（最終合成）の経路を読んだ結果: **画面は既に Vulkan が出している**

- `PRESENT_FROM_COMPOSITE` は**既定 true** で、format 2 の「画面合成」バッチに対して
  `beginFrameForPresent` → ブリッジ semaphore 付き `presentImage` を行う経路が**実装済み**。ただしその前提条件
  （`currentRenderTargetIsMain` 等）を満たさず、**format 2 は受理マスクで `target is outside the RGBA8/D24/full-viewport
  bridge contract` として拒否**され続けている（816〜817 件/run）。
- **しかし画面は既に Vulkan が出している**: §0.17 の per-frame present（`func_175068_a` 先頭で直前フレームを提示）が
  毎フレーム成功し（`presentedFromVulkan` 263 等）、**GL の swap も省略**されている。つまり
  **条件 (a) の「最終合成・present が native」という結果は、別経路で既に成立している**。
- **残るのは冗長な GL 合成（blit）だけ**であり、それを外すには制約がある:
  **ゲームのスクリーンショットは GL の既定フレームバッファを読む**（`Screenshot` 経由で、これが我々の画像証拠の
  唯一の経路）。**GL の合成を止めるとスクリーンショットが黒くなり、画像比較の手段を失う**。
  → **証拠経路を先に用意する**（Vulkan 画像の読み戻しで比較する、または提示画像 readback を捕捉フレームで取る）まで、
  GL 合成は**意図的に残す**。これは「未着手」ではなく**順序の判断**である。
- **計器**: `compositePresentRefusals` と format 2 の拒否件数が、この経路の状態を引き続き示す。

## 0.29 native の線の **Intel 受入**（能力依存の経路なので必須の段階）

- **受入（Intel、`lookDown`＋TESR 層、duration 150 s）**: exit 0 / validationClean / **PASSED**、302 frames、p50 31.49 ms、
  **VUID 0**、クラッシュなし。**`ACCEPTED byFormat 1 = 280`**（変更前は同種の場面で 1 桁〜26）、
  **拒否集計に `line primitives` のエントリが無い** ＝ **線は native で受理されている**。
- **正直な計器の問題**: 新しく足した `lineBatchesNative` / `lineBatchesRefusedForWidth` / `maxLineWidth` は
  **0 / 0 / -1.0** を返している（挙動ではなく**カウンタの置き場所とインスタンス**の問題。
  `CELTIUM_GROUPS` 行に出したが、この行を出している実体と受理を数えている実体が同じとは限らない）。
  **挙動の証拠は `ACCEPTED byFormat 1` の増加と拒否エントリの消滅で足りている**が、計器としては未完成と明記する。
- **意味**: §0.28 の native 線は **AMD と Intel の両方で成立**した。`lineWidthRange` による能力判定は
  **どちらの GPU でも 2.0 を満たした**（拒否カウンタが 0 であることと整合）。
- **残る最大のもの**: **format 2（最終合成）**。拒否集計で `2 target is outside the RGBA8/D24/full-viewport bridge
  contract` が 816〜817 件＝**毎フレーム1回、全画面クアッドが GL に渡っている**。

## 0.28 **native の線が成立**（format 1 の受理 17 → 797、拒否エントリ消滅）

- **実装したもの**:
  1. **線トポロジのパイプライン** `VulkanImmediatePipeline.createForLines(...)`
     （`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`）。8引数の既存ファクトリのシグネチャは変えず、内部に `lines` を通す形にしたので
     呼び出し側の変更はゼロ。線用のキャッシュを1系統追加（キーは既存の state キー＋**線ビット** `1L << 61`）。
  2. **能力判定**（baseline 方針に沿う形）: `VkPhysicalDeviceLimits.lineWidthRange(1)` を読み、**GL が要求している線幅**
     （`GL_LINE_WIDTH` を実際に問い合わせる）と比較する。`wideLines` は**任意機能**なので必須にしない —
     feature を別途問い合わせる必要も無い（`wideLines` が無ければ範囲が `[1,1]` になり、同じ比較で拒否される）。
     満たさない場合は **理由付きで拒否して GL に渡す**（`lineBatchesRefusedForWidth`）。
  3. `VulkanImmediateDrawer.isLineMode(mode)` を追加し、**線は format 1 のときだけ** native 候補にする。
- **受入（AMD、`lookDown`＋TESR 層、duration 150 s）**: exit 0 / validationClean / **PASSED**、823 frames、
  **p50 10.52 ms**、**VUID 0**、クラッシュなし。

| 計器 | 前 | 後 |
|---|---|---|
| `ACCEPTED byFormat 1` | **17** | **797** |
| `acceptedByFormat 1` | 26 | **810** |
| 拒否集計の `1 line primitives …` | **797** | **消滅** |

  **拒否されていた 797 件が、そのまま 797 件受理されている** ＝ **selection outline を含む線プリミティブが native で描かれる**。
  （残る format 2 の 816 件は**最終合成**で、条件 (a) の最後のピース。）
- **まだ GL に残るもの**: 最終合成（format 2）、GL/VK bridge、copyback、GL context。
- **未確認**: Intel での受入（線の幅能力は Intel でも 2.0 を満たす見込みだが**測っていない**）。
- **計器の置き場所**: `lineBatchesNative`／`lineBatchesRefusedForWidth` を要約側に置いたため**レポートに出ていない**
  （また同じ誤り）。挙動の証拠は `ACCEPTED byFormat 1` の 797 で足りているが、**次に触るときは専用行へ移す**。

## 0.27 native の線に向けた第一歩: 線プリミティブの展開と**CPU 契約検査**

- **実装**: `VulkanImmediateDrawer.expandIndices` が線モードを扱うようにした（それまでは `default` で `null`＝未対応）。
  - `GL_LINES`: そのまま対（頂点数が奇数なら**推測せず拒否**）。
  - `GL_LINE_STRIP`: n 頂点 → n-1 線分。`GL_LINE_LOOP`: ＋閉じる線分。
- **CPU 契約検査（`celtium-vulkan/src/test/java/.../VulkanImmediateDrawerTest.java` を追加）**: 線リスト・奇数拒否・
  ストリップの連続対・ループの閉じ・1頂点拒否・クワッドが従来どおり2三角形、の6件。**`:celtium-vulkan:test` は成功**。
- **なぜこれが安全に進められるか**: `unsupportedReason` は**まだ線を拒否している**ので、**GPU の挙動は変わっていない**
  （selection outline は引き続き GL が描く）。展開だけを先に固定し、**不変条件を壊せば失敗する検査**を付けた。
- **次に必要なたたき台（実装済みの設計判断）**:
  1. **線トポロジのパイプライン**（`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`）。既存は全て `TRIANGLE_LIST`
     （`VulkanImmediatePipeline` 180 行）。線を使うのは **format 1（POSITION_COLOR）だけ**なので、パイプラインは1系統で足りる。
  2. **`glLineWidth(2.0)` は再現できない**: Vulkan の `wideLines` は**任意機能**であり、goal の baseline 方針
     （広い Vulkan 対応 GPU で動くこと）に照らして**必須にしてはいけない**。よって
     **`wideLines` が要求幅を満たすときだけ線を native で受け、満たさなければ理由付きで拒否して GL に渡す**のが正しい形。
  3. `unsupportedReason` を **format に依存させる**（線は format 1 のみ受付）。

## 0.26 **format 1 の ~800 件は「線の正しい拒否」だった**（§0.24 の記述を訂正）+ 最終合成の拒否を特定

per-format の拒否集計を専用行（`CELTIUM_REFUSALS_BY_FORMAT`）に出したところ、内訳が名指しされた。

```
1 line primitives are not implemented in the Vulkan terrain renderer yet = 797
2 target is outside the RGBA8/D24/full-viewport bridge contract           = 817
1 preflight: format=1 texture=21 (no terrain pipeline for this pass)      = 10
6 preflight: format=6 texture=NN (no terrain pipeline for this pass)      = 各 9〜18
9 preflight: format=9 texture=25 (no terrain pipeline for this pass)      = 12
```

- **format 1 の ~800 件（797）は `GL_LINES` の拒否**である ＝ **selection outline は native に提供され、
  「線は native 未実装」という理由で正しく拒否され、GL が描いている**。
  **§0.24 の「outline は提供されていない」は誤り**だった（family 別の print 上限に当たっており、
  ログの grep では 0 件に見えていた。**集計を専用行に出して初めて見えた**）。
  → **overlay 系の残件は「native の線が無いこと」そのもの**であり、ゲーム側のガードの問題ではない。
- **format 2 の 817 件**は `target is outside the RGBA8/D24/full-viewport bridge contract` ＝
  **最終合成（POSITION_TEX_COLOR の全画面クアッド）が GL 所有のまま**であることの実測。
  これは**条件 (a) の最後のピース**（「最終合成が native」）に直結する。
- **次に実装する2つ（どちらも名指しできた）**: (1) **native の線**（`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`＋
  `VK_POLYGON_MODE_LINE`、`fillModeNonSolid`/`wideLines` は feature 確認つき）、
  (2) **native の最終合成**（bridge 契約の外にある format 2 の全画面クアッドを Vulkan の合成パスへ）。
- **計器の教訓（再掲）**: family 別 print の上限は「0 件」と誤読させる。**新しい分類は必ず専用行に集計で出す**。

## 0.25 TESR 可視場面と、**format 1 の未受理 800 件**という新しい具体的ギャップ

- **場面**: 水を含まない層に **chest と enchanting table** を入れて見下ろす（TESR が視界に入る）。受入は
  exit 0 / validationClean / **PASSED**、830 frames、**p50 10.48 ms**、**VUID 0**。
- **確認できたこと**: **TESR は display list ではない**（`modelMeshLists=2 / quads=12` のまま＝プレイヤーの手のみ。
  chest/enchanting table は加わらない）。TESR は即時描画なので別経路である。
- **新しい具体的ギャップ（実測）**: `ARRIVALS byFormat` は **1:826, 2:1639, 6:5748, 7:514…** なのに
  `ACCEPTED` は **1:26, 2:0, 6:5746, 7:5228…** ＝ **format 1（POSITION_COLOR）が 826 到着して 26 しか受理されていない**。
  format 1 は **selection outline・破壊 overlay・デバッグ表示**の family であり、**overlay 系の残件そのもの**である。
- **解決（§0.26）**: その ~800 件は **`GL_LINES` の拒否（797 件）**だった。family 別 print の上限のためログに出ておらず、
  「拒否として数えられていない」ように見えていた。**集計を専用行に出して確定**した。
- **註**: `line primitives` の拒否は依然 0（§0.24 のとおり線は native 対象外で、提供されていない）。

## 0.24 selection outline が提供されない理由を**ガード単位で**分解（レンダラ側は正常と確定）

`drawSelectionBox` の呼び出し条件（`EntityRenderer.renderWorld`）は次の形で、**どこで止まるかを1つずつ計器で潰した**。

```java
if (flag && this.mc.objectMouseOver != null && !entity.isInsideOfMaterial(Material.WATER)) {
    if (!ForgeHooksClient.onDrawBlockHighlight(...))        // Forge イベント。listener が cancel できる
        renderglobal.drawSelectionBox(entityplayer, this.mc.objectMouseOver, 0, partialTicks);
```

| 条件 | 計器/出典 | 結果 |
|---|---|---|
| レイがブロックに当たる | `CELTIUM_MOUSEOVER` | **成立**（`hit=BLOCK@…`、`inGameHasFocus=true`、`hideGui=false`、`screen=none`） |
| 水の中にいない | `!isInsideOfMaterial(WATER)` | **既定の層では不成立**（層は bedrock3→dirt4,5→grass6→**water7** で足元が水）→ **水を含まない層で走らせて成立** |
| 線プリミティブが native で描かれる | `VulkanImmediateDrawer.unsupportedReason` | **native は線を実装していない**（`GL_LINES`/`LINE_STRIP`/`LINE_LOOP`/`POINTS` を理由付きで拒否＝**正しい設計**。GL が描く） |
| Forge の `DrawBlockHighlightEvent` が cancel されない | 未計器 | **残る唯一の候補**（インスタンスには mod が入っている） |

- **受入（AMD、`lookDown=true`、水を含まない層、duration 150 s）**: exit 0 / validationClean / **PASSED**、871 frames、
  **p50 9.92 ms**（これまでで最良）、**VUID 0**、`hit=BLOCK@BlockPos{x=0, y=4, z=-1}`。**line の拒否は依然 0 件**
  ＝ outline は**まだ提供されていない**。
- **確定したこと**: **レンダラ側に欠陥は無い**。線は native の対象外として**理由付きで拒否**され、GL が描く経路になっている。
  提供されない原因はゲーム側のガード（水 or Forge イベント）である。
- **次の安い一手**: 場面から `RenderGlobal.drawSelectionBox` を直接呼ぶ（`ForgeHooksClient` をバイパスする）か、
  `DrawBlockHighlightEvent` の cancel を記録する。前者なら **native の線拒否→GL 描画**の往復がそのまま検証できる。
- **註（破壊 overlay）**: 掘削中のひびは `PlayerControllerMP` の破壊進行が必要で、この場面の対象外。

## 0.23 破壊/selection overlay の場面を追加（**outline は未提供**＝レンダラ側の問題ではないと確定）

- **追加した場面**: `-Dceltium.validation.lookDown=true` で**カメラを真下（pitch 75）に向け**、`hideGUI` を false にする
  （selection outline は HUD 表示中しか描かれないため）。ヘッドレスでは誰もカメラを動かさないので、場面が自分で指定する必要がある。
- **受入（AMD、duration 150 s）**: exit 0 / validationClean / **PASSED**、708 frames、**p50 12.09 ms**、**VUID 0**、スクリーンショット有り。
  `acceptedByFormat 1:16`（従来 1:5）。
- **しかし selection outline は描かれていない**: スクリーンショット中心に輪郭が見えず、**`line primitives` の拒否が 0 件**
  ＝ `drawSelectionBox` の `GL_LINE_STRIP` バッチは**そもそも即時経路に提供されていない**。
  **これはレンダラ側の欠陥ではない**（`VulkanImmediateDrawer.unsupportedReason` は `GL_LINES`/`GL_LINE_STRIP`/`GL_LINE_LOOP`/
  `GL_POINTS` を**既に理由付きで拒否**しており、線は GL が描く経路になっている）。
- **棄却した仮説（このターン）**: (a) 線を三角形として誤描画している → `expandIndices`/`unsupportedReason` を読んで**棄却**、
  (b) 受理されているのに不可視 → **そもそも受理も提供もされていない**ため棄却、
  (c) `hideGUI` が原因 → false にしても件数が変わらず**棄却**。
- **次の計器（1行）**: 場面で `mc.objectMouseOver` の**種別と座標**を記録する。`typeOfHit != BLOCK` なら
  `drawSelectionBox` の手前で止まっていることになり、そこが次の分岐点である。
- **註**: 「破壊 overlay（掘削中のひび）」はさらに手前（ブロックを掘る操作が要る）なので、この場面では対象外。

## 0.22 spectator creeper / enderman の受入（goal 項目5が名指しする3種のうち残り2つ）

- fixture には元から creeper/spider/enderman の spectator 効果（vanilla ShaderGroup の entity outline を
  `loadEntityShader` で読み込む）があり、**spider は受入済み**だった。残り2種を受入した。
- **受入（AMD 2場面＋Intel 1場面、duration 150 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | `modelMeshLists/Quads` | 拒否 | VUID | `spectatorResult` / `shaderActive` |
|---|---|---|---|---|---|---|---|---|---|---|
| AMD creeper | 0 | true | **PASSED** | 663 | 13.36 ms | 1204 | **8 / 48** | 0 | 0 | loaded / **True** |
| AMD enderman | 0 | true | **PASSED** | 663 | 13.32 ms | 1210 | **9 / 54** | 0 | 0 | loaded / **True** |
| Intel creeper | 0 | true | **PASSED** | 234 | 40.83 ms | 414 | **8 / 48** | 0 | 0 | loaded / **True** |

  **creeper と enderman のモデルが native 経路で捕獲・描画されている**（素の場面は 2 リスト/12 クワッドなので、
  8/48 と 9/54 はこの2体のモデルが入ったことを示す）。`displayListCancelled == modelDraws`（成功時のみ取消）、
  `modelDrawRefusals=0`、スクリーンショット有り、クラッシュなし。
- **意味**: goal 項目5が名指しした **spectator creeper / spider / enderman の3種が両GPUで受入済み**になった。
  **entity outline は vanilla ShaderGroup として動いている**（`shaderActive=True`）。ただし**その outline 自体は
  依然 GL のパス**であり、**native 化は残件**である（延期ではなく未着手と明記）。

## 0.21 **Nether 成立（AMD/Intel 両方）**: 根因は「転送先のポータルの中に立っていた」こと

**計器で確定した根因（推測ではなく caller chain）**: `EntityPlayerMP.changeDimension` に stack を出す計器を入れたところ、
戻りの転送は次の連鎖で起きていた。

```
CELTIUM_CHANGE_DIM to=0 teleporter=Teleporter
  EntityPlayerMP.changeDimension
    Entity.func_184204_a            (= Entity.changeDimension)
      Entity.func_70030_z           (= Entity.onEntityUpdate … バニラのポータル論理)
        EntityLivingBase/Entity/EntityPlayer.func_70071_h_ (onUpdate)
          EntityPlayerMP.func_71127_g / NetHandlerPlayServer … / MinecraftServer
```

**つまり**: fixture の転送は `player.setPortal(new BlockPos(player))` を立てて
`transferPlayerToDimension` を呼ぶため、**Teleporter が見つけた/作ったポータルの *中* に着地**し、
**バニラのポータル論理が約55 tick 後にそのまま送り返していた**。
（`inPortal` の足元ブロック判定が false だったのは、体がポータルに接している状態を見ていなかったため。
体力は終始 20.0/20.0 で**死亡仮説は棄却**、チャンクは loaded で**未達仮説も棄却**。）

- **修正（fixture 側・境界が明確）**: 転送成功直後に **ポータルの外へ出す**
  （`player.setPositionAndUpdate(player.posX, player.posY, player.posZ + 2.0D)` — ポータルは軸方向に1ブロック厚なので
  Z に2ブロックで向きによらず外）＋ `timeUntilPortal = 0`。
- **受入（両GPU、`dimensionAtTick=40`、`dimension=-1`）**:

| GPU | exit | validationClean | fixture | frames | `dimensionClientAtCapture` | `dimensionWorldAtCapture` | 戻り転送 | VUID |
|---|---|---|---|---|---|---|---|---|
| **AMD** | 0 | true | **PASSED** | 691 | **-1** | **-1** | **0 回** | 0 |
| **Intel** | 0 | true | **PASSED** | 391 | **-1** | **-1** | **0 回** | 0 |

  `dimensionServerResult=transferred`、`dimensionArrivedAtTick=41/42`。**capture 時の client と world が両方 -1** ＝
  goal 項目4の受入条件（capture時 player/world/provider が target）を満たす。
- **旧記載の撤回**: 「Nether は GuiDownloadTerrain が残留して NO_CAPTURE」は**誤り**だった。実際は
  **到着成功 → ダウンロード画面は約48 tick で正常に消える → その約8 tick 後にポータル論理が送り返す**であり、
  画面の残留は症状ではなかった。**旧 Nether PASSED を外した判断も、この根因判明により更新する**
  （当時の PASSED は capture 時 dimension を見ていなかったため受入としては不十分、現在の方式で取り直した）。
- **残置した計器**: `EntityPlayerMPMixin` は**診断のみ**（次元変更時に caller chain を1行出すだけ・挙動を変えない）。
  次元まわりの次の作業で効くため残す。fixture 側の trace も位置・チャンク・体力・ポータル・onGround を持つ。

## 0.20 Nether の根因が**別物**だった: 「画面で固まる」ではなく「到着後に約56tickで Overworld へ戻される」

- 保存済み trace を読み直した結果、**停止ではなく往復**であることが分かった。新しい測定（AMD、`dimensionAtTick=40`、`dimension=-1`、
  duration 200 s、trace は元から **5 tick ごと**に出ていた）:

| tick | client | world | server | screen | arrivedAt |
|---|---|---|---|---|---|
| 40 | 0 | 0 | 0 | none | -1 |
| **42** | **-1** | **-1** | **-1** | **GuiDownloadTerrain** | **42** |
| 45〜85 | -1 | -1 | -1 | GuiDownloadTerrain（**約43 tick 表示**） | 42 |
| **90 / 95** | -1 | -1 | -1 | **none**（＝ダウンロード完了。チャンクは届いている） | 42 |
| **98** | **0** | **0** | **0** | GuiDownloadTerrain | 42 |
| 100〜120 | 0 | 0 | 0 | GuiDownloadTerrain → none | 42 |

  レポートは `NO_CAPTURE`、`dimensionServerResult=transferred`、`dimensionArrivedAtTick=42`、`clientDimensionAtExit=0`。
- **読み方**: 転送は成功し、**クライアントもサーバーも -1 になり**、**ダウンロード画面も約48 tick で消える**
  （＝チャンクは届いている）。**その約8 tick 後に dimension が 0 へ戻る**。旧説明の「GuiDownloadTerrain 残留」は
  **到着直後の正常な待ち**であり、根因ではなかった。
- **次の計器（実装済み・次の1 runで読む）**: trace に **クライアント位置**・**足元チャンクのロード状態**
  (`world.isBlockLoaded`)・**足元がポータルブロックか**を追加した。候補は
  (a) 転送先が**ポータル内**でゲーム自身のポータル論理が送り返す、(b) 統合サーバー側の再転送、(c) 到着地点が
  チャンク外/不整合。**(a) はこの3つ目までの計器で確定できる**（`inPortal=true` なら確定）。
- **ビルド**: 追加計器は `Chunk.isLoaded()` と `EntityPlayer.isInPortal()` が MCP 1.12.2 に無く2度失敗したため、
  存在が確実な `World.isBlockLoaded(BlockPos)` と `getBlockState(...)==Blocks.PORTAL` に置き換えた（**ビルド緑**）。

## 0.19 提示経路の軽量化: 余分な copyback を外し、**AMD では pure win**（Intel は +10.3 ms が残る）

- **内訳の切り分け**: 提示のたびに `beginFrame`/`endFrame` でフレームを枠取りしていた（＝**2回目の copyback とその待ち**）。
  通常フレームでは内容も copyback も既に済んでいるので、**枠を外して `presentImage` を直接呼ぶ**形にした
  （共有イメージは GL が宣言したレイアウトのままなので `VK_IMAGE_LAYOUT_GENERAL` を渡す）。
- **実測（`presentFromComposite` / framing の有無で比較、軽い世界）**:

| 構成 | AMD p50 / p95 | Intel p50 / p95 |
|---|---|---|
| 提示 OFF（GL swap） | 16.19 / 19.22 | 30.33 / 34.71 |
| 提示 ON・framing あり | 16.17 / 18.40 | 43.73 / 45.60 |
| **提示 ON・lean（今回）** | **13.07 / 16.07** | **40.62 / 42.21** |

  受入は AMD・Intel とも exit 0 / validationClean / **PASSED** / **VUID 0** / `presentFailures=0` /
  `presentResult=[present image: blitted 1280x720 onto image 1 (0)]`。
- **AMD は提示が純益**: **13.07 ms は「提示しない」16.19 ms より速い**（GL swap を省いた分が present のコストを上回る）。
- **Intel は +10.3 ms が残る**（30.33 → 40.62）。framing を外して削れたのは約3 ms なので、残りは
  **present 自体（image の取得と提示の待ち）**である。swapchain は **MAILBOX 優先（無ければ FIFO）・image 数は最小+1**。
  最有力の説明は **headless な X では提示済み image を消費する側がいないため取得が待つ**というもので、
  **実 display では同じコストとは限らない**（これは仮説であり、実 display での確認は未実施）。
- **次に残るもの**: 描画そのもの（GL/VK bridge・copyback・GL call）と GL context。

## 0.18 提示経路の限定回帰と**実測コスト**（AMD はゼロ、Intel は +13.4 ms/frame）

- **限定回帰（提示経路に触れる変更のため）**: すべて exit 0 / validationClean / **PASSED** / **VUID 0** / スクリーンショット有り /
  mixin 失敗なし。

| run | frames | p50 | `presentedFromVulkan` | `glSwapsSkipped` | `presentFailures` |
|---|---|---|---|---|---|
| AMD resize | 591 | 16.17 ms | **276** | **276** | 0 |
| AMD reload | 622 | 13.66 ms | **339** | **338** | 0 |
| AMD world switch | 640 | 12.97 ms | **352** | **351** | 0 |
| **Intel 通常** | 219 | 43.73 ms | **207** | **206** | 0 |

  **resize が通ること自体が、`@Redirect` 方式で resize 判定を残したことの確認**である（メソッド全体を cancel していたら落ちる）。

- **A/B（`-Dceltium.vulkan.presentFromComposite=false` で提示経路ごと切った比較）**:

| GPU | 提示 ON | 提示 OFF | 差 |
|---|---|---|---|
| **AMD** | p50 **16.17 ms** / p95 18.40 | p50 **16.19 ms** / p95 19.22 | **±0** |
| **Intel** | p50 **43.73 ms** / p95 45.60 | p50 **30.33 ms** / p95 34.71 | **+13.4 ms** |

  **AMD では提示のコストが測れない水準**、**Intel では 1 フレームあたり +13.4 ms** という**実測コスト**がある。
  正しさの問題ではない（全 run PASSED・VUID 0・presentFailures 0）。次の調査対象は
  **swapchain の present mode / image 数 / 待ちの構成**（FIFO 固定・image 不足・acquire/待機の直列化など）。

## 0.17 **GL swap の停止に成功**（通常フレームは Vulkan が提示し、GL は swap しない）

**前ターンの失敗の原因（完全に特定）**: `@Redirect` ハンドラが **レシーバ引数 `(Display)` を取っていた**が、
**`Display.update()` は static メソッド**なので引数を取ってはいけない。mixin config の **`defaultRequire: 1`** のため
これは**ハードエラー**になり、**ゲームは起動前に落ちていた**（失敗ログの `InvalidInjectionException ... Found 1 unexpected
additional method arguments: (Display)` がそのまま根拠）。**ハンドラを無引数にし、通す場合は `Display.update()` を静的に呼ぶ**
形にした（`require = 0`、失敗時は必ず元の swap を通す fail-open）。

**受入（AMD、軽い世界、duration 90 s）**: exit 0 / validationClean / **PASSED**、269 frames、p50 25.59 ms、**VUID 0**、
クラッシュなし、**mixin 適用失敗なし**、**スクリーンショット有り**（mean 113.423）。

| 計器 | 値 |
|---|---|
| `presentHookCalls` | 269 |
| **`presentedFromVulkan`** | **263** |
| **`glSwapsSkipped`** | **262** |
| `presentFailures` | 0 |
| `presentResult` | `present image: blitted 1280x720 onto image 2 (0)` |
| `presentSucceeded` / `presentSubmitDone` | true / true |

**意味**: **通常フレームの表示は Vulkan の swapchain が出し、GL の swap は行わない**（提示済みフレームにつき1回だけ省略・
resize 判定とイベント poll は維持）。**条件 (b) の「GL swap 依存」が通常フレームから外れた**。
**まだ GL に残るもの**: 描画そのもの（GL/VK bridge・copyback・GL call）と GL context。`presentedMean` と GL 側
スクリーンショット平均は**別の瞬間の値**なので内容一致の根拠には使わない（§0.16 の訂正どおり）。

## 0.16 GL swap の停止を試みて**失敗**し、巻き戻した（失敗の記録）

- **per-frame フックの正しい SRG 名が判明**: `Minecraft.updateDisplay()` = **`func_175601_h`**、その直後に呼ばれる
  resize 判定 = **`func_175604_i`**（`srg_patched_minecraft-sources.jar` から直接取得）。
- **試したこと**: `func_175601_h` 内の `Display.update()` 呼び出しを **`@Redirect`** で差し替え、**Vulkan が提示済みのフレームでは
  GL swap を行わない**（events は poll し、resize 判定は同じメソッドの残りとしてそのまま走る）形にした。
- **結果（実測1回・即失敗）**: **exit=1 / `MISSING_REPORT` / スクリーンショット無し / クラッシュログ無し**。
  **GL swap を省くとゲームがフレームを出せなくなる**（fixture が捕捉に到達しない）。原因は未特定
  （候補: swap を前提にしたゲーム側の待ち、`Display.update` が担うイベント/バッファ処理、fixture の捕捉段階）。
- **対応**: **失敗した変更は保持せず巻き戻した**（goal の「停止や未到達を完了扱いしない」「失敗時に原因を追える単位を保つ」）。
  巻き戻し後に**同じ条件で再受入**: exit 0 / clean / **PASSED**、240 frames、p50 28.06 ms、**VUID 0**、
  **`presentedFromVulkan=235`／`presentAttempts=235`／`presentFailures=0`**、
  `presentResult=[present image: blitted 1280x720 onto image 1 (0)]`、`presentSucceeded=true`。
  **確立済みの「通常フレームの Vulkan 提示」は壊れていない**ことを確認した。
- **§0.15 の記述の訂正**: 「提示画像の平均 113.25 と GL スクリーンショット平均 113.874 が一致＝独立した裏付け」は
  **言い過ぎ**である。両者は**別の瞬間に取得**される（提示 readback は最後の提示時、スクリーンショットは捕捉時）ため、
  今回の再受入では 140.0 対 114.1 と離れた。**同じ瞬間の比較**（提示 readback を捕捉フレームで取る）を用意するまでは、
  内容一致の裏付けとして扱わない。

## 0.15 **通常フレームが Vulkan から提示されるようになった**（条件 (c) の中核受入）

**根因（このターンの本体）**: 「present を有効にするか」という**同じ判定が3箇所に別々に実装**され、既定が食い違っていた。
- backend 側は `!"false".equalsIgnoreCase(getProperty("presentFromComposite","true"))` ＝ **既定 true**、
- `VulkanContext` の2箇所（インスタンス拡張・デバイス拡張）は `Boolean.getBoolean(...)` ＝ **既定 false**、
- `VulkanSwapchain` の受理判定も `Boolean.getBoolean(...)` ＝ **既定 false**。
結果、**デバイスが `VK_KHR_swapchain` 無しで作られ**、swapchain は「unavailable」で永久に作られなかった。
**既定を1つに揃えた**（backend と同じ「明示的に false のときだけ無効」）。

**併せて直した到達性の問題（すべて実測で特定）**:
1. `celtium$presentFrame()` の per-frame 呼び出し点: 最初は `Minecraft.updateDisplay`（SRG に存在せず）→ 次に
   `func_175068_a` HEAD に付けたが**ハンドラの署名が `(CallbackInfo)` だけ**で対象メソッド
   `(int pass, float partialTicks, long finishTimeNano)` と不一致 → **`require=0` のため無言で適用されず**。
   署名を合わせて解決（**コンパイル済みクラスに注釈が入っていることは `javap` で確認した**）。
2. 入口の早期 return を「フレーム開始でリセットされる `frameSubmitReachedQueue`」で判定していた → **専用の
   `lastFrameReachedQueue`（submit 時に立ち、提示で消費）** に分離。
3. **swapchain を作る呼び出しが無かった**: `ensureSwapchain()` は「present する側が呼ぶ」と自分で書いてあるのに、
   私の入口が呼んでいなかった（プローブだけが呼んでいた）。
4. **計器の誤読を1つ訂正**: `presentAttempts=0` は「フックが発火していない」ことを意味しない（attempt は
   `presentImage` の後でしか増えない）。**入口で無条件に増える `presentHookCalls`** を足したことで、
   フックは 245/237 回**到達している**ことと、理由が `swapchain unavailable` であることが分離できた。

**受入（AMD、軽い世界、duration 90 s）**: exit 0 / validationClean / **PASSED**、237 frames、p50 26.75 ms、**VUID 0**、クラッシュなし。

| 計器 | 値 |
|---|---|
| `presentHookCalls` | **237**（毎フレーム到達） |
| `presentedFromVulkan` / `presentAttempts` | **233 / 233**（`presentFailures=0`） |
| `presentSkipReason` | **`presented`** |
| `presentResult` | **`present image: blitted 1280x720 onto image 0 (0)`** |
| `presentSucceeded` / `presentSubmitDone` | **true / true** |
| **`presentedMean`** | **113.25** |
| **GL スクリーンショットの平均** | **113.874** |

**提示画像の平均（113.25）と GL 側フレームの平均（113.874）が一致** — **Vulkan の swapchain に出た画像が、このフレームの
内容そのもの**であることの独立した裏付けである（差 0.6/255）。

**まだ言わないこと**: **GL swap は止めていない**（両方が提示する。内容は同一なので実測での破綻は無いが、機構としては次段階）。
**実 window/compositor 出力の到達**は未確認（`DISPLAY=:0` の root は 0×0）。起動直後の数フレームは提示しない（237 中 233）。

## 0.14 通常フレームからの Vulkan present に着手: 提示の入口は実装、**per-frame の呼び出し点が未解決**

- **判明した bridge の全体像**（`VulkanFrameTarget` の設計コメントより）: Vulkan 所有の RGBA8 色イメージと D32 深度イメージを
  **外部メモリ FD で GL に import** し、GL はその FBO に描き、Vulkan が所有権を受け取り、**`endFrame()` で GL が共有イメージを
  ゲームの FBO へ blit バック**（copyback）し、最後に **GL が swap** する。**present の配線・marker・readback は既にある**。
- **実装したもの**: `celtium$presentFrame()`（フレーム内容が共有イメージにあるとき `beginFrame → presentImage → endFrame`
  の実測済み手順で提示し、`presentedFromVulkan`／`presentAttempts`／`presentFailures`／`presentResult` を計器として出し、
  成功時は GLFW のイベントを自前で poll する）。提示位置は「フレーム先頭で直前のフレームを提示」とした
  （`renderWorld` の RETURN は **GUI 描画より前**なので、GUI を含む完成フレームにはならない）。
- **未解決（実測2回）**: **per-frame の呼び出し点が発火しない**。`Minecraft.updateDisplay` は SRG に存在せず
  （`forge.srg` に該当なし）、既存で動作している名前 `func_175068_a` の HEAD に付けても **`presentAttempts=0`** のまま
  （＝条件分岐ではなく**メソッド自体が呼ばれていない**。条件を変えても数値が動かなかったことがその証拠）。
  **SRG 名を `srg_merged_minecraft.jar` のバイトコードから解決するのが次の一手**。
- **状態**: 提示の入口と計器は保持（契約は明確・未使用）。**GL swap は触っていない**ので表示の退行は無い
  （`presentedFromVulkan=0` のまま、スクリーンショットは従来どおり GL 側で mean 113 前後）。
  **「通常フレームが Vulkan から提示された」とは言わない**。

## 0.13 最終合成・present の現状を測定: **present 経路は既定で配線済みだが、通常フレームでは到達しない**

- 発見: **`celtium.vulkan.presentFromComposite` は既定 true**（`VulkanTerrainGpuBackend` 925 行）。つまり
  **native 合成からの present は既定の経路**であり、§0.12 で追っていた `COMPOSITE_*_REFUSED` は *GL 側合成* の別ルール。
  合成経路のコードには**設計として明記**されている: `final default-FBO composite remains GL-owned, never inferred from
  format id`。
- **測定（AMD、`presentMarker=true`、duration 90 s、exit 0 / clean / PASSED）**:
  `presentSucceeded=false`、**`presentResult=[none]`**、`submitted=6505`／`completed=6505`。
  **`presentImage` が一度も呼ばれていない**（結果文字列が初期値のまま）。理由は実行コードの gate:
  `glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0` のとき
  「presenting the frame skipped: the default framebuffer is bound, so there is no frame content to present here」。
- **これが条件 (b)(c) の具体的な不足条件である**: **通常フレームの内容は依然として GL の既定フレームバッファにあり**、
  Vulkan のフレームターゲットは**鏡**であって**源ではない** → **present するものが Vulkan 側に無い**。
  native present の配線・marker・readback・no-record は既にあり、**フレームの生産者が GL である限り到達しない**。
- **次に必要なこと（順序）**: 通常フレームの色・深度の**生産者**を Vulkan 側へ移す（item 6 の本体）。
  そのとき `presentImage` が呼ばれ、`presentResult` と `presentedMean` が意味を持つ。
  **実 window/compositor 出力の到達**はその次の受入であり、**この環境の DISPLAY=:0 に実 compositor が無い**
  （`xdpyinfo` は root 0×0、`grim` は wl_output 無し）ため、**取得不能時の不足条件はこの1行に集約される**。

## 0.12 拒否理由の確定と、前ラウンド推論の**訂正**

- `prepareImmediate` の **3つの null 経路すべてが理由を記録**するようにし、offer 側の粗いメッセージ
  （3原因を1つに潰していた）を**廃止**して理由を1箇所が持つ形にした。実測で理由が出た:

  `lastPrepareFailure=[format=6 texture=23 (no terrain pipeline for this pass)]`、
  `CELTIUM_COMPOSITE_LAYOUT_REFUSED reason=preflight: format=9 texture=25 (no terrain pipeline for this pass)`。

- これを受けて「フレームの world パイプラインへフォールバック」を実装して測ったところ **`preparePassFallbacks=0`**
  ＝ **その時点では `terrainPipelineForFrame` も null** だった。つまり
  **format 8/9 の到着は entity/TESR のモデルではなく、合成（composite）経路自身の描画**であり、拒否は
  **その経路の別ルール**（`CELTIUM_COMPOSITE_LAYOUT_REFUSED`）によるものである。
- **訂正**: §0.11 の「format 8/9 が受理されない → entity/TESR が native 化されない」という推論は、
  **この計器からは支持されない**。entity のモデルは **display list 経由**（§0.10 で既定化済み）で、
  spider 場面の `modelDraws=1000` と画像一致（床0に対し効果20画素）がその受入である。
- **整理**: 発火しなかったフォールバックは**到達不能として撤去**（goal の「不要になった暫定経路を残さない」）。
  **理由を記録する計器は残す**（この問いを決めたのはそれである）。
- **次に残る問い**: **合成経路の format 8/9 バッチ**を受理できるようにするか、その経路のルールとして
  「対象外」と明示するか。**どちらでもない状態（理由が読めない拒否）は解消した**。

## 0.11 TESR 場面の追加と、未知テクスチャへの一度だけの全面供給

- **世界に TESR ブロックを追加**した（既定の層に `minecraft:chest` と `minecraft:enchanting_table`）。TESR は display list では
  なく**即時描画**なので、モデル経路とは別の未検証領域である。受入 run は exit 0 / clean / **PASSED**、VUID 0、
  `modelMeshLists=2`（＝**TESR は display list 経由ではない**ことの確認）。
- **format 8/9（entity/TESR 系）が到着しても受理されない**理由をコードで追った: 単一の発信点は
  `prepareImmediate(...) == null` で、その null は **`textureRegistry.get(boundTexture) == null`**（＝**そのテクスチャの
  ミラーが存在しない**）から来ていた。**entity/TESR は自分のスキンを即時描画で使い、どの upload フックにも見えない**ため、
  ミラーが作られず**永久に受理されない**構造だった。
- **修正**: **未知のテクスチャに一度だけ全面供給**する（`imageFor` で実体を作り、`uploadFromGlTexture` で1回読み戻し、
  成功後に `markFullyInitialized`）。guard は「まだミラーが無い」なので **GL id ごとに1回**。
- **実測（AMD、軽い世界＋TESR ブロック、120 s）**: exit 0 / clean / **PASSED**、277 frames、p50 23.17 ms、**VUID 0**、
  **`lazyMirrorSupplies=1`・`lazyMirrorFailures=0`**、`submitted=6811`／`completed=6810`。
- **まだ言えないこと（重要）**: **format 8/9 がこの run で受理されたかは未確認**である。`acceptedByFormat` は `9:0` だが、
  **この run に format 9 の到着が何件あったかを確認していない**（到着0なら 0 受理は自明）。**「entity/TESR が native に
  なった」とは言わない**。次の1 runで **到着数と受理数を同時に**読む。
- **計器の置き場所についての教訓（3回繰り返した）**: 新しい counter を `diagnostics()` や要約の途中に足すと
  **レポートにもログにも現れない**。**`CELTIUM_GROUPS`／`CELTIUM_ANIM` のように専用行として出す**のが確実である。

## 0.10 モデル native 描画を**既定**にした（受入一式で回帰なし）

- 既定化: `-Dceltium.vulkan.modelDraw` / `modelMeshBuild` を**既定 true**（`=false` で従来挙動に戻せる）。
- **受入一式（AMD 8場面＋Intel 2場面、軽い世界、duration 120-150 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | 拒否 | `cancelled` | VUID |
|---|---|---|---|---|---|---|---|---|---|
| AMD 通常 | 0 | true | **PASSED** | 562 | 16.08 ms | 1038 | 0 | 1038 | 0 |
| AMD spider | 0 | true | **PASSED** | 544 | 16.67 ms | 1000 | 0 | 1000 | 0 |
| AMD 三人称＋GUI | 0 | true | **PASSED** | 560 | 16.25 ms | 1400 | 0 | 1400 | 0 |
| AMD postprocess | 0 | true | **PASSED** | 563 | 16.17 ms | 1040 | 0 | 1040 | 0 |
| AMD weather | 0 | true | **PASSED** | 562 | 16.20 ms | 1040 | 0 | 1040 | 0 |
| AMD reload | 0 | true | **PASSED** | 555 | 16.17 ms | 526 | 0 | 1022 | 0 |
| AMD resize | 0 | true | **PASSED** | 565 | 16.21 ms | 1046 | 0 | 1046 | 0 |
| AMD world switch | 0 | true | **PASSED** | 554 | 16.14 ms | 524 | 0 | 1016 | 0 |
| Intel 通常 | 0 | true | **PASSED** | 320 | 29.34 ms | 556 | 0 | 556 | 0 |
| Intel 三人称＋GUI | 0 | true | **PASSED** | 313 | 28.50 ms | 822 | 0 | 822 | 0 |

  **全 run PASSED・VUID 0・クラッシュなし**。p50 は既定化前（AMD 16.02〜16.24 / Intel 28.5〜29.3）と同等で**性能の退行なし**。
- **画像**: spider は**20 画素**（フラグON時と同一）、三人称は 2407 画素＝**既定自身の床（1983）水準**。
- **計器の寿命差を修正**: `reload`／`worldSwitch` で `cancelled`（1022/1016）が `modelDraws`（526/524）を上回った。
  原因は **census が static で backend がインスタンス毎**（reload/world switch でインスタンスが作り直される）。
  `modelDraws` を **static** にして寿命を揃えた（**欠陥ではなく計器の問題**だった）。
- **意味**: **モデル描画（display list 経由の player/mob/item モデル）が既定の経路になった**。完成条件 (a) の
  未達項目から「model/item の native 描画（実験フラグ側）」が外れ、**entity/TESR の全種類・他 scene** が残る。
- **まだ言わないこと**: **全 entity/TESR**・影mod・他 GPU/OS は未検証。**「全vanilla native化」ではない**。

## 0.9 モデル描画の段階受入: Intel と「モデルが多い場面」

- **Intel 受入（三人称のプレイヤーモデル、duration 120 s）**: exit 0 / validationClean / **PASSED**、302 frames、p50 30.71 ms、
  **`modelDraws=750`・拒否 0・`displayListCancelled=750`**、`submitted == completed = 8495`、**VUID 0**、クラッシュなし。
- **モデルが多い場面（spectator spider、AMD、隣接3run）**:

| 比較 | 差>2の画素 | 行 540-624 | 行 625 以降 | 最大 |
|---|---|---|---|---|
| **床（既定 vs 既定2）** | **0** | 0 | 0 | 2 |
| **効果（既定 vs 実験ON）** | **20** | 6 | 14 | 3 |
| 効果（既定2 vs 実験ON） | **21** | 7 | 14 | 4 |

  `modelDraws=1004`・拒否 0・`cancelled=1004`、`submitted=13346`／`completed=13345`（**差1は標本時刻の in-flight**、
  `retired=1` と整合）、`discarded=0`、**VUID 0**、クラッシュなし、全 run **PASSED**。
- **意味**: **床が 0 の場面で効果が 20 画素**（92万画素中、最大差3）＝ **native モデル描画は既定と実質一致**。
  三人称場面では効果が既定自身の床と同水準だった（§0.8）。**2場面・両GPUで、モデル描画の捕獲→メッシュ→
  ウィンドウ内描画の経路が成立している**。
- **言えないこと**: entity/TESR の**全種類**、他 scene、影mod、他 GPU/OS は未検証。**「モデル完成」ではない**。

## 0.8 モデル native 描画の非再現性を修正: 自前 submit をやめ、即時ウィンドウの順序に載せた

**根因（このターンで特定・実装）**: モデル描画は `callList` で **自前のコマンドバッファを開いて即 submit** していた。
即時経路は「ウィンドウに溜めて、次の GL 描画の直前のパスでまとめて描く」ことで**フレーム内の順序**を守っているが、
モデルだけがその順序に**参加していなかった** → 同じ入力でも合成順が安定せず、**下段（モデルの帯）が run 間で再現しなかった**
（前単位の実測: 既定の床 32 画素に対し ON の床 1283 画素）。

- **修正**: モデルメッシュを **即時ウィンドウの group として enqueue** し、`flushImmediateWindow` →
  `drawPendingImmediate` のパス内で、他と同じ順序で描かれるようにした（`celtium$enqueueModelMesh`）。
  **mesh の所有は借用**（`GroupStamp.meshBorrowed`）とし、retire ドレインが**フレーム間で使い回すメッシュを解放しない**
  ようにした（この契約を入れないと2フレーム目で消える）。
- **併せて棄却した仮説**: 束縛テクスチャの run 間差（`CELTIUM_MODEL_TEXTURE` の list→texture 対応表は**両runで完全一致**、
  全リストが texture 24）。
- **実測（AMD、軽い世界、三人称＋GUI、duration 120 s）**:

| 比較 | 差>2の画素 | 行 540-624 | **行 625 以降** | 最大 |
|---|---|---|---|---|
| **床 ON（隣接run、修正後）** | **41** | 37 | **4** | 244 |
| **床 既定（隣接run）** | 1983 | 1953 | **30** | 192 |
| **効果: 既定 vs ON（隣接）** | **1989** | 1959 | **30** | 243 |
| 効果: 既定2 vs ON（隣接） | 2162 | 2162 | **0** | 247 |
| （参考）床 ON（修正前） | 3237 | 1954 | **1283** | 249 |

- **結論**: **ON 同士の床が 3237 → 41 に落ちた**（下段は 1283 → **4**）。さらに
  **ON と既定の差（1989 / 2162）は、既定自身の run 間の床（1983）と同水準**である
  → **この場面・この解像度では、native モデル描画は既定のばらつきを超える差を生んでいない**。
  `modelDraws`（=queue 数）1402 に対し `cancelled` 1402（**成功時のみ取消**）、`refusals=0`、
  `submitted == completed`、`discarded=0`、**VUID 0**、クラッシュなし、**PASSED**。
- **言えること / まだ言わないこと**: **三人称のプレイヤーモデル1場面**で、native 描画が既定のばらつき内に収まった。
  **entity/TESR/item 全体・他 scene・他 GPU は未検証**。「pixel 完全一致」でも「モデル完成」でもない。

## 0.7 モデル native 描画の画像比較: **run 間で再現しない**（前ターンの結論・実験は lab flag のまま）

**測定（AMD、軽い世界、三人称＋GUI、duration 120 s、`captureStartTick=200` は全 run 一致）**:

| 比較 | 差>2の画素 | 行 540-624 | **行 625 以降** | 最大 |
|---|---|---|---|---|
| **床（既定 vs 既定2）** | 1283 | 1251 | **32** | 192 |
| **床（実験ON vs ON2）** | **3237** | 1954 | **1283** | 249 |
| 効果（既定 vs ON） | 3184 | 1900 | 1284 | 214 |
| 効果（既定2 vs ON2） | 1608 | 1577 | **31** | 249 |

- **読み方**: **既定同士では下段（行625以降＝モデルが立っている帯）が 32 画素しか違わない**のに、
  **実験ON同士では同じ帯が 1283 画素違う**。つまり **native モデルの寄与が run 間で再現しない**。
  これは「GL と違う絵を出す」とは別の、より強い問題である: **同じ入力から同じ絵が出ていない**。
- **画像の観察**（`docs/evidence/model-onoff-row560-2026-10-10.png`）: 差は**面状のずれではなく薄い筋**として
  地平線帯（行540-624）と下段に散る。**以前に観測した「既定に無い三角形」を、この測定は再現していない**
  （あの観測は取消の根拠が名前ベースだった頃のもので、対象リストが今とは違う）。
- **棄却した仮説（このターン）**: `VulkanDynamicMesh.nextVertex()`/`currentVertex()` の添字が1ずれている
  （読んで確認: `nextVertex()` は開始した頂点の番号を返し、`put*` はその頂点に書く。**規約は正しい**）。
  また round 559 の「list 2 の行列が 0.6 違う」は、**census が *最初の* replay の行列を1回だけ記録する**のに対し
  描画は毎回現在の行列を問い合わせるためで、**時間差による見かけ**だった（欠陥ではない）。
- **状態**: 実験は **lab flag のまま**（既定は描画0・キャンセル0で不変）。**「native モデルが正しい」とも
  「stray triangle がある」とも、この測定からは言わない**。
- **次の問い（具体）**: **なぜ ON の下段だけが run 間で再現しないのか**。候補は
  (a) 描画時点の行列位相が replay と1フレームずれる、(b) mesh の構築時期が list ごとに違い、
  一部の list が *別の* ポーズで捕獲されている、(c) 深度・ブレンドの状態が frame ごとに変わる。
  **測って決める**（同一 run 内で list ごとの matrix と描画時刻を記録する）。

## 0.6 モデル native 描画の実験を再到達可能にした（この単位・全vanilla native化の入口）

**根因**: `callList` フックが **`cancels(list)` の内側でしか `drawModelList` を呼んでいなかった**。構造修復2で
`cancels()` は「compile が native に受理された（リストが空）」だけを根拠にする形になり、その仮説は**実測で反証**されて
いたため、**モデル native 描画は既定でも実験フラグ時でも一度も走らない**状態だった（`modelDraws=0`）。

- **フックを「フラグ時は必ず描き、成功したときだけキャンセル」に直した**:
  `if (modelDrawEnabled()) { drawn = vulkan.celtium$drawModelList(list, boundTexture); if (drawn) cancel(); }`。
  **名前も counter も判断に入らない**（「別経路が実際に描いた」だけが根拠）。
- **反証済みで死んでいた経路を撤去**: `COMPILED_NATIVELY` 一式（`compiledNatively` / `wasCompiledNatively` /
  `nativelyCompiledLists` とアップローダの呼び出し）、`cancels()`、id リスト `CANCEL` / `cancelSet()`、
  要約の `displayListCompiledNatively`。**判断の持ち主は1つ**になった。
- **実測（AMD、軽い世界、duration 120 s）**:

| run | exit | validationClean | fixture | frames | p50 | `modelDraws` | 拒否 | `displayListCancelled` | `modelMeshLists` | VUID |
|---|---|---|---|---|---|---|---|---|---|---|
| **既定（フラグOFF）** | **0** | **true** | **PASSED** | 559 | 16.21 ms | **0** | 0 | **0** | — | **0** |
| **実験（`modelDraw`＋`modelMeshBuild`、三人称）** | **0** | **true** | **PASSED** | 559 | 16.25 ms | **1396** | **0** | **1396** | **12** | **0** |

  **`displayListCancelled == modelDraws`（1396 = 1396）** ＝ **成功した描画だけをキャンセル**している。
  **既定は完全不変**（描画0・キャンセル0・p50 16.21 対 16.25）。クラッシュなし・VUID 0。
- **検証として言えること**: 実験が再び到達可能で、取消の根拠が「実際に描いた」ことだけになっている。
  **まだ言えないこと**: **native モデルの見た目が正しいか**（画像比較は**アニメーションを統制した場面**で行う）。
  entity/TESR/item 全体の native 化は**未完**。

## 0.5 構造修復 3（前半・この単位）: draw の段階を target 世代に紐付ける

**根因**: 5段階（queued / recorded / submitted / completed / retired）は別リストと counter で管理され、
`VulkanCommandBufferState` も正しい状態機械（`INITIAL→RECORDING→RENDER_PASS→EXECUTABLE→PENDING`・target 保持・
`requireDraw(target)`）を持つが、**group 自身が「どのフレーム・どの frame target 世代のものか」を記録していなかった**。
frame target が作り直された後（resize / teardown）に古い group が残っていると、**もう存在しない render pass を束ねて
描く**余地があった。

- **`GroupStamp`（immutable: `frameId`, `targetGeneration`）を group に付与**（tuple の index 14）。
  **texture 世代は入れない** — 描画対象の頂点は texture 再アップロードで無効にならず、落とせば**描画が消える**ため。
  無効条件は **target 同一性だけ**である、と明示した。
- **`targetGeneration` は frame target を破棄した2箇所**（再生成経路と teardown 経路）で進む。
- **draw 時のゲート**: 世代が一致しない group は**描かずに退役**させ（mesh は次の完了ドレインで解放＝未 submit なので安全）、
  `groupsStaleTargetGeneration` として計数する。
- **段階の可視化**: queued/recorded/retired の実数、`targetGeneration`、stale 件数、submitted/completed/discarded を
  **`CELTIUM_GROUPS` 行**として stdout に出す（`CELTIUM_ANIM` と同じ場所＝確実に残る場所）。
- **実測（AMD resize fixture／Intel 通常。軽い世界）**:

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ |
|---|---|---|---|---|---|---|---|
| **AMD + `resize=true`** | **0** | **true** | **PASSED** | 544〜568 | 16.1〜16.2 ms | **0** | なし |
| **Intel** | **0** | **true** | **PASSED** | 320 | 27.41 ms | **0** | なし |

  **段階の整合不変条件（レポートに出ている数値）**: **`immediateSubmittedBatches == immediateCompletedBatches`**
  （1557=1557 / 1749=1749 / 4243=4243）。**submit されて完了しなかった group は無い**。
- **正直な未検証**: 新設の `CELTIUM_GROUPS`（`targetGeneration`・`groupsStaleTargetGeneration` 等）は
  **まだ数値を読み出していない**。要約メソッド内の別エントリに置いた版がレポートに現れなかったため、
  **確実に出る `CELTIUM_ANIM` と同じ場所へ専用行として移した**ところまでが今回の実装で、
  **数値の確認は次の1 runで行う**（`staleTargetGeneration` が 0 である前提は置かない）。

## 0.4 構造修復 3（後半・この単位）: fixture の結果と JSON 所有を snapshot に統一

**根因**: サーバータスクが**3つの volatile フィールド**（`transferResult` / `transferAtTick` / `rotationBefore`）を書き、
クライアントが別々に読む＝半端な結果を読む余地があり、**shutdown フックは生きた `mc.player` / `mc.world` /
`mc.currentScreen` を任意スレッドから読んでいた**（goal が禁じている形）。

- **`TransferSnapshot`（immutable・final フィールド）**: `state` / `completedAtTick` / `rotationBefore`。
  サーバータスクは**完成した1つの object を1回 publish** するだけ。クライアントはそれだけを読む。
- **`ProgressSnapshot`（immutable）**: `ticks` / `frames` / `captureStarted` / `captureStartTick` /
  client・world の dimension / screen 名 / **出力パス**。**render スレッドで毎フレーム取得**し、1参照で publish。
- **shutdown フックは snapshot だけを読む**（`mc` には一切触れない）。ファイルパスも snapshot が持つ。
  捕捉時のレポート書き込みも snapshot 経由に統一。
- **実測（3経路・両GPU）**:

| run | harness | レポート |
|---|---|---|
| AMD（軽い世界、150 s） | exit 0 / clean / **PASSED** | `captureStartTick=200`、`worldFrames=545` |
| **AMD（End、`dimensionAtTick=80`）** | exit 0 / clean / **PASSED** | **`dimensionServerResult=transferred`**、`dimensionArrivedAtTick=81`、**`dimensionClientAtCapture=1`**（転送 snapshot と fail-closed が有効） |
| **Intel**（150 s） | exit 0 / clean / **PASSED** | `captureStartTick=200`、`worldFrames=324` |
| **Intel（強制 NO_CAPTURE、25 s・溶岩世界）** | 124（ハーネス上限）/ clean / **NO_CAPTURE** | **`status=NO_CAPTURE`、`worldTicksAtExit=161`、`worldFramesAtExit=92`、`captureStarted=False`、`clientDimensionAtExit=0`、`screenAtExit=none`** — **フックが snapshot だけで書いた** |

  すべて **VUID 0・クラッシュなし**。
- **消した重複**: volatile フィールド3つ（結果の分割所有）、shutdown スレッドからのゲーム状態参照。
- **残り（構造修復3の前半）**: draw 側の **queued→recorded→submitted→completed→retired を target/frame/generation に
  紐付ける**こと（preflight/retry/失敗時保持・clear/depth/sample 順序の確認を含む）。**未着手**。

## 0.3 構造修復 1 の完成（この単位）: lifecycle と未初期化 patch と実コスト

**同じ根因（texture の実体・更新・寿命）に対する残りを、producer/consumer/invalidator/lifecycle まとめて実装し、
契約が揃ってから小GPU受入した**（goal「実装・検証の進め方」の単位）。

- **A. 未初期化 image への partial patch を拒否**（registry）: 全面が一度も入っていない image に部分 patch を書くと、
   patch 外が未定義のまま「更新済み」と記録してしまう。`announceFullPixels` は**部分 patch を拒否して計数**し、
   その texture は**readback で全面が入るまで patch を受けない**（描画側の `hasFullContent` 拒否と整合）。
- **B. lifecycle（reload / 再stitch / ID 再使用）**: 脆弱な `deleteTexture` への inject は**署名不一致で mixin 適用に失敗し
  両GPUが起動クラッシュした**ため撤去し、**既に発火が実証されている full-upload フック**（`func_110988_a`＝id の
   内容が確保し直される場所）を合図にした。`onGlTextureReallocated(id)` が **registry の entry を忘れ**、
   古い image を **`VulkanRetireQueue` で退役**させ（記録済みフレームが参照し得るため即破棄しない）、
   atlas/lightmap の直接参照を落として**次のフレームで新しい image から再確立**する。
- **C. 実コスト計測**: patch / full image の**回数・バイト数・適用 nanos**、`beforeInit`（拒否数）、`forgotten` を
   registry が出し、`CELTIUM_ANIM` 行と要約に載せる（upload/submit/wait は `VulkanGlTexture` が 1 操作ごとに
   `beginCommands`/`endCommands` で submit+wait するので、この nanos がその実費である）。
- **実測（AMD は reload fixture でこの経路を実際に通す、Intel は同段階の受入。軽い世界、duration 150 s）**:

| run | exit | validationClean | fixture | frames | p50 | VUID | クラッシュ |
|---|---|---|---|---|---|---|---|
| **AMD + `reload=true`** | **0** | **true** | **PASSED** | 534 | 16.33 ms | **0** | なし |
| **Intel** | **0** | **true** | **PASSED** | 321 | 27.56 ms | **0** | なし |

  **不変条件の実測**（壊れれば 0 のままになる数値）:
  **再確保 385（AMD）/ 503（Intel）**、**ライトマップ再確立 96 / 175**、
  **patch 144 件・147456 B・70 ms（AMD）/ 267 件・273408 B・313 ms（Intel）**、
  **`beforeInit=290 / 341`**（新設の拒否が実際に効いている）、`full=0〜2 件`。

- **この単位で消した重複・例外**: backend 側の第2の `VulkanGlTexture.create`（二重実体）、名前ベースの取消分岐、
  到達不能だった interop 絶対 tick 待ち、registry 生成7箇所の散在（前単位まで）。**死んだ lab flag は残していない**。
- **検証として言えること / 言えないこと**: lifecycle と拒否の**不変条件は数値で動いている**。
  **画像同一性はまだ言わない**（比較は非アニメーション統制の場面で行う）。mip は patch が level 0 のみを書き、
  patch 外を保持する（full upload 側が全 level＋chain を書く）ことをコードで確認した。

## 0.2 構造修復 2（着手）: 取消の根拠を「記録された事実」に限定した

- **goal の要求**: 取消は「対応する別経路が同じ内容を引き受けた/描いた」根拠を必要とし、**名前・counter だけで
  描画を消さない**。従来の `cancels()` は **呼び出し元の名前**（`ItemRenderer` を含むか）で決めていた ＝ 名前ベース。
- **名前ベースの判断を撤去した**。新しい判断は「**native のモデル描画が実際にそのリストを描いた**」か
  「**そのリストのコンパイルが native に受理され、GL 側が空である**」のいずれかで、後者は
  **受理が起きた場所（アップローダ）で記録**する（`CELTIUM_LIST_COMPILED_NATIVELY`）。
- **実測（AMD、軽い世界、duration 120 s）**: `CELTIUM_LIST_COMPILED_NATIVELY` は **0 件**、
  `displayListCancelled=0`、`displayListReplaysOpen=1542`、exit 0 / clean / **PASSED**、VUID 0、p50 16.14 ms。
  **つまり「コンパイルが native に受理されて GL のリストが空」という仮説は反証された** —
  display list は空ではなく、コンパイル時のバッチは受理されていない。
- **したがって以前の「529〜1066 件の取消で画像が床水準」という成果は、正しい根拠を持っていなかった**。
  画像が床水準だったのは事実だが、それは**「この fixture では取消対象の replay が可視の差を生まない」**ことの
  観測であって、「別経路が内容を引き受けた」証明ではない。**証拠のない取消は既定から外した**。
- **現在の既定**: 取消は **native モデル描画が成功したときだけ**（＝実際に描いた事実があるとき）起こる。
  ラボフラグ由来の名前ベース分岐（`cancelHandDisplayLists` / `cancelModelDisplayLists`）は**撤去**した。
  代償として **GL 呼び出しの削減（約1000件/run）は失われる**が、それは**根拠のない削減**だった。
- **残る確認（次）**: 「この fixture で手/手持ちが実際に描かれているか」を `emptyHand` の対で測る
  （描かれていないなら、取消の有無は画像に現れないという説明がつく）。format 2 の受理が 0 である事実と併せて、
  **手/手持ちの per-frame 描画がどの経路にあるのか**を特定する。

## 0.1 構造修復キュー（goal「直近の実行順」1〜4 に対応）

| # | 契約 | 状態 |
|---|---|---|
| 1 | texture の実体・更新・寿命を一つにする | **実装済み・両GPU受入**（`297ce5caa` ＋ §0.3。lifecycle/拒否/コスト計測まで。画像同一性の統制比較のみ残る） |
| 2 | draw の queued→recorded→submitted→completed→retired と取消根拠の整合 | **一部実装**（§0.2: 取消を記録された事実に限定、名前ベースを撤去）。queued→recorded→submitted→completed→retired の target/generation 紐付けは未着手 |
| 3 | fixture の準備→到達待ち→描画待ち→capture→report と thread/JSON 所有 | **完了**（§0.4 fixture snapshot、§0.5 draw の target 世代、§0.8 モデル描画も同じウィンドウ順序へ統合） |
| 4 | Nether の未完を具体的に直す | **両GPU受入済み**（§0.105・§0.20/0.21: 帰還根因は「転送先ポータル内に立っていた」AXIS 直交 step 未対応＋backend 再生成後の frame target デッドロック —— AMD 967 / Intel 349 pass・PASSED・実 Nether 画像で確認） |
| 5 | plan を現状表へ戻し、経緯を history へ移す | **この文書で実施** |

##

> **構造修復フェーズ（2026-10-10〜）**: 方針は `docs/hints-2026-10-10-nether.md` と
> **`docs/handoff-structure-recovery-2026-10-10.md`（構造修復プロンプト）** に従う。要旨は
> (1) texture の実体・更新・寿命を**一つの契約**にまとめる、(2) frame/draw の
> accept→submit→complete→retire と取消根拠を整合させる、(3) fixture の段階・thread 所有を一つの状態機械にする、
> (4) plan を現状表へ戻し経緯を history へ移す。**全面書換え・汎用GLエミュレーション・flag/定数/budget での症状隠しは禁止**。
> 短い修復フェーズのあと、全 vanilla native 化・GL なし frame と実 present へ戻る。

## 構造修復 1: texture の所有契約を一つにする（このターン・実装済み／検証は一部保留）

- **根因（実コードで確認）**: 同じ GL texture id に対して **Vulkan image が2つ**あった。
  `VulkanTextureRegistry` が `entries[id].texture` を持ち、`announceFullPixels(...)` は**そちら**へ patch を書く。
  一方 `VulkanTerrainGpuBackend` は `atlasTexture`/`lightmapTexture` を**自前で `VulkanGlTexture.create`** して保持し、
  **descriptor が sample するのはこちら**。したがって patch はサンプルされず、`invalidateTexture` が
  `applyAnnouncedTo`（BufferedImage 経路のみ有効）に失敗して **GL 全面 readback** に落ちていた。
- **直した契約**:
  - **実体の所有者を registry に一本化**した。バックエンドは `registry.imageFor(id, w, h, filter)` で
    **同じ object** を受け取り、それを sample する（フィルタは所有者側で一度だけ決める: atlas=nearest, lightmap=linear）。
    backend 側の `VulkanGlTexture.create` による第2の実体は**撤去**した。
  - **初期化状態**を明示した（`fullyInitialized`）。**部分 patch だけでは未初期化 image を作らない** —
    全面が一度も入っていないなら readback を残す。
  - **世代の消費**を明示した（既存の `announcedGeneration`/`consumedGeneration` に統合。新しい世代カウンタを
    増やさない）。CPU 適用（int[][] patch・BufferedImage 変換）が世代を進め、**readback を省いた事実が消費**する。
    「無効化を起こした書き込みが既に pixels を渡していた」ことを**事実**として扱う。
  - registry の生成が**7箇所に散っていた**のを単一アクセサ `celtium$registry()` にまとめ、
    「2つの registry ができて実体がずれる」余地を消した。
- **実測（AMD、溶岩世界＝既定、duration 120 s、同一条件の変更前 run と比較）**:

| 指標 | 変更前（p250-upload） | 変更後（p251-owner） |
|---|---|---|
| verdict | exit 0 / clean / **PASSED** | exit 0 / clean / **PASSED** |
| p50 | 40.41 ms | **25.49 ms** |
| `directMirrorRefreshes` / `readbacksSkipped` | — | **472 / 473**（ほぼ全更新が readback 不要） |
| `CELTIUM_PATCH_REJECTED` | — | **0** |
| VUID / クラッシュ | 0 / なし | **0 / なし** |

- **検証として言えること**: **無効化のたびに走っていた全面 readback が消えた**（473 回が skip として計上され、
  実測 p50 が 40.41→25.49 ms に下がった）。`applyAnnouncedTo` は BufferedImage 経路として残る（dynamic texture 用）。
- **まだ言えないこと（重要）**: **画像が同一だとは言っていない**。比較した run は**溶岩世界（アニメーションする
  スプライトを持つ）**で、`PINNED_WORLD_TIME` は world time を固定するが **sprite の animation 位相は固定しない**
  （handoff の指摘どおり）。実際に全画面 meanAbsDiff 6.29 / 400k 画素の差が出ており、**これが位相の違いなのか
  patch 経路の content 差なのかは切り分けていない**。**次の検証は「アニメーションを統制した（または静止した）
  場面」で行う**。
- **残る具体的な確認項目**: patch 経路が **mip と全画像内容**を readback 経路と同等に保つか
  （`MIRROR_GL_MIPS=false` なので level 0 のみを触る点は両経路で同じ、という読みまではした）。
  **reload / GL id 再使用 / sprite 単位 mip** はまだ測っていない。
- このターンで**ヒント1・3 の修正は維持**（capture 段階化・部分レポート・dimension fail-closed）。


本書は **現在の到達点・優先順・受入の唯一の現状表** である。経緯と過去の測定は `docs/history/` にあり、そこにある数値や「closed」は
現在の判定ではない。根拠は `docs/audit-2026-10-09-third.md`（第3回監査、監査開始HEAD `c89bda361`）と、監査後に取得した実測である。
**用語**: 実装済 = コードがあり動く / 局所検証済 = 特定fixtureで確認 / 未検証 = 動く証拠がない / 未実装 = コードがない。
カウンターの増加は描画の証拠ではない。format認識・受理・記録・submit・完了・dropを混同しない。

## 1. 厳密な完成条件の現状（この表は各ラウンドの実測で更新する）

| 条件 | 状態 | 欠落・根拠（現在の実測） |
|---|---|---|
| (a) 全vanilla描画と最終合成・presentがnative Vulkan | **部分** | **nativeで動いているもの**: 地形パス、全即時 format（1〜9 受理、TESR 場面で定常拒否 **0**・startup transient のみ）、**display list 経由の
  entity/model/手/TESR**（`modelDraw` 既定 ON・§0.108：entity 場面 AMD 0.35 %/Intel 0.16 %、GL replay 基準以下）、
  **default-FB composite overlay**（vignette/portal 等・§0.109：format-2 accept 2:0→2:170・`no frame target` 消滅）、
  フレーム最終合成（`presentFromComposite` 既定 ON）。**block registry は AMD 254/254 掃引・失敗0**、**Nether は
  AMD/Intel 両方 portal 帰還・PASSED**（§0.105）。**未検証/未完**: **custom shader/postprocess/spectator 3視点**
  （`a custom shader program is bound` 拒否＋non-shared FBO＝shader chain は GL のまま）、全 mob 種・破壊/selection、
  天候（雨/雪粒子は format 5 で経路あり・未場面計測）、実 FBO が non-shared の描画（`CELTIUM_TARGET_FOREIGN` は
  現在ほぼ発火しない＝通常場面で foreign FBO への描画が観測されていない）。**追記**: **spectator 3視点は
  動作確認済み**（AMD：creeper 緑・spider 5眼モザイク・enderman 反転、いずれも presented=screenshot
  0.000 %・PASSED —— post chain は GL 実行だが出力は shared FBO 経由で提示へ届く）。残る厳密な未了は
  **post-process 自体の native 化**（shader chain は GL のまま —— 出力は正しいが native Vulkan ではない）。 |
| (b) GL contextを生成せず、GL call/readback/bridge/copyback/GL swap/GL fallbackを必要としない実行 | **部分達成（GL-less 起動は実測・窓の絵が未解決なので未受入）** | **実測（`-Dceltium.vulkan.glless=true`、AMD）**: GL context なしで world まで起動し、Vulkan が毎フレーム提示して GL swap を1対1で飛ばす（`p548-glless`: `presentedFromVulkan=287`＝`glSwapsSkipped=287`・`presentFailures=0`・`validationMessageCount=0`）。GL 実体は stub（`glRenderer="Celtium stub GL"`・`deviceUuids=[]`）で、bridge も shared image もフレームに無い（`gameFramebufferIsShared=false`・`glless target: no handoff`）。**未受入の理由**: 提示画像がまだ絵ではない（`p559-glless`: `presentedDistinctColours=2`・`presentedNonBackgroundFraction=0.0`・`presentedVerticalBands=15`・fixture `FAILED_PRESENTED_IMAGE`）。GL-less run の harness verdict は `validationExitCode=1`・`gpuRequirementMet=false` で、PASSED ではない。**既定経路の bridge 側計器は従来どおり**: `regStaleCalls=0`・`inPlaceRefreshes=0`・announce 経由 16.2/フレーム（**ただしこれは通常fixtureの範囲の話であり「全sceneで readback が無い」意味ではない**）、構造修復1（`297ce5caa`）で atlas/lightmap の**無効化ごとの全面 readback を廃止**し `readbacksSkipped=473` 対 `directMirrorRefreshes=472` を実測（=ほぼ全ての更新が CPU 供給で閉じる）、**残る依存はブリッジ本体**（`VulkanFrameTarget`: FBO付け替え・色/深度blit・semaphore待ち・状態クエリ48箇所）で CPUコスト **0.58 ms/フレーム（AMD）/ 0.83（Intel）**、GL context は依然として生成する。§0.114 に全測定 |
| (c) native swapchain frameが実window/compositor出力へ到達 | **充足（実測・2026-10-11）／未測定は scanout のみ** | **実測**: grim（wlroots screencopy）で compositor 出力そのものを取得。marker run で compositor 出力 1280×720 全面が一様 magenta (255,0,255)（14 capture・window map 中のみ。未 map は黒）、**capture 0035 は renderer の番号付き presented dump `frame-20.png` と 0/921600 画素差・最大チャネル差 0**。通常 run でも **capture 0066 = `frame-100.png`（0/921600 差・実 scene 10104 色、fixture PASSED）**。fixture の presented 読み戻しは `presentedImageMeanAtCapture=170.0`・「921600 画素が単一色」で grim 側と一致（**以前ここに書いた 191.25 は RGBA 4ch 平均、170.0 は RGB 3ch 平均**で同じ magenta の別の取り方）。GL screenshot は黒（presented と 99.925 % 差）＝ GL framebuffer は証拠経路ではない。**未測定**: 走査出力そのもの（`ffmpeg -f kmsgrab` は `Failed to open DRM device`＝DRM master 無し・非 root。grim は headless でも実 KMS でも合成済み出力バッファを読むため、実出力で走らせてもこの段は閉じない）。§0.123・`docs/compositor-output-capture-2026-10-11.md` |
| (d) AMD/Intelで必要場面と失敗復旧、監査P0/P1を明示受入で閉じる | **部分（受入は緑、監査項目の一部が未了）** | **既定の世界は溶岩/火入り**（`worldLayers` で差し替え可）で、**AMD p50 25.49 ms（修復1後）/ Intel 軽い世界 28.47 ms・溶岩世界 64.52 ms**、
  いずれも exit 0 / clean / PASSED。**dimension は End が AMD/Intel とも capture 時 dimension 1 で PASSED、
  Nether は NO_CAPTURE**（§0）。postprocess / weather / spectator spider / command-buffer target / JEI も PASSED。
  失敗復旧は allocation（buffer/uniform/context/frame target/texture）・descriptor・texture upload・submit・
  fence timeout・out-of-date・resize/minimize・reload・world switch を**両GPUで確認済み**（submit/fence は
  exit 1・report あり・有界で、stall 後は資源を解放しない: `stalledTeardownSkips` 547/549）。
  監査項目の残りは §2 を参照。 |
| (e) mod対応範囲・Java条件・最低VK能力・未検証GPU/OS・ライセンス/性能をdocsと配布物で一致 | **部分** | 性能は**同一fixtureで実測**（AMD 16.02 ms 対 参照 16.18 ms、Intel 28.54 ms、参照は Intel でも 16.17 ms で GPU 非依存）。画像は 参照比 **meanAbsDiff 7.78/255・HF 0.81**（統制: 参照同士 0.064 / HF 1.002）。mod 対応範囲・最低VK能力・未検証GPU/OS の記述は `docs/scope-and-limits.md` と要突き合わせ。 |

## 2. 第3回監査 項目別の現在状態

| # | 監査の内容 | 現在 | 根拠・残り |
|---|---|---|---|
| **A01** | model-viewの最初3列のみ送り平行移動が消える | **修正済・受入済** | writerが `projection*modelView` を1行列として送り、**全7即時/UIシェーダ**が `u_ProjectionMatrix * vec4(a_Position,1.0)`。列を再構築するシェーダは**0**。SPIR-Vはビルド時にGLSLから生成（committed .spvなし）のでjarと整合。`immediate_position.vert` の再構築残りも撤去（writerが列をゼロ化したため全頂点が平行移動点へ収束していた）。run: exit 0 / clean / message 0 / PASSED |
| **A02** | `int[][]` を行配列として読む | **修正済・CPU契約受入済**（GPU画像受入は残る） | `data[level][y*levelWidth+x]` の契約に合わせ、level 0 を**平坦**に読む。level別寸法は「書き込みレベルの寸法をpatch寸法として渡す」契約に統一。動的textureの平坦 `int[]` 経路は別契約として分離 |
| **A03** | 部分更新なのに `UNDEFINED` でatlas全体とmipを破棄 | **受入済** | 全面uploadと部分更新を分離。patchは**レベル別に記録した現在layout**から遷移し、**level 0のみ**の範囲、mip chainを記録しない、patch自身が戻し遷移。**CPU契約**: `TextureContractCheck` がpatch外byte不変・色順・touched検証（PASS）。**GPU受入**: spriteの部分更新経路は全runで稼働 —— `animatedImageFills=879`（water/lava等のanimated texture patch、~2/frame）・PASSED・clean・画像一致。patch正しさはCPU契約＋実GPU場面の両方で確認済 |
| **A04** | 初回patchだけで全面未初期化のmirrorを作る | **是正側は修正済・全面供給は未解決** | `hasFullContent()` を持ち、全面内容の無いミラーは受理経路がdescriptorを作らず拒否（`partialMirrorRefusals`）。AMD実測では**0件＝このfixtureはpatch先行の順序を作らない**。atlas stitch時の全面データ保持・供給は残る |
| **A05** | patch寸法でmip chainを作り直す | **修正済** | A03の修正でpatchはmip chainを記録しない。sprite単位mipは `MIRROR_GL_MIPS` 経路がGLの各levelをコピーする（全atlas blitを正解とせず、遠景・透明境界での比較は残る） |
| **A06** | 保持画像とpatchの競合、hash skip | **修正済** | patch成功時に `lastPixelHash` を無効化し、patch後の同一内容全面uploadが重複skipされないようにした。CPU shadowとresource generationでの全面管理は残る |
| **A07** | metadata・転送成功の扱い | **修正済** | hashを転送**後**に確定、`imageUploads` は成功時のみ、`Entry.width/height` が画像に追随（寸法を可変に）、skipは `imageUploadsSkipped` として別計数。GL ID再使用の検証は残る |
| **A08** | 受理前失敗でpending meshに頂点が残る | **修正済・受入済（範囲拡大）** | preflightをappend前に置く方針を維持しつつ、`prepareImmediate()` で **pipeline と descriptor を group 生成前に用意**し、format 2/3/4/5/6/7/8/9 と texture にも広げた。`VulkanDynamicMesh` は頂点・indexの容量を両方確保してから書き込む。注入run `sol-cb-final-retry-amd` で `retries=1`・最終カウンタ4/4/4/4を実測 |
| **A09** | 受理後の消失を全形式で数えていない | **修正済** | 描画ループ内の**7つのskip地点すべて**が `postAcceptDroppedGroups` を増やす（以前はformat 5のdescriptor失敗のみ）。実測0は、実装したdrop計器が数えた消失がなかったという事実。全formatのGPU完了・見た目を証明しない。branch別の内訳とbatch ID対応付けは残る |
| **A10** | `offTargetDrawn` は実描画ではない | **修正済・AMD受入済** | 常に `return false` していた旧 offscreen scaffold（空passをclear/copy）を削除し、off-targetの受理済みgroupは **bind切替のHEAD（旧FBOがbind中）で同期windowが描いて書き戻す**。実測 `offTargetAccepted/Recorded/Completed=2/2/2`、`mainWasPending=true`・`secondaryWasPending=true`、別FBOのPNGで赤・緑が正位置・未更新領域の不一致0。copy-back回数を描画証拠にはしない方針を維持 |

| **A11** | format 2（POSITION_TEX_COLOR）は既定でGL（※この記述は撤去済み） | **修正済・AMD/Intel受入済** | 既定maskを `1,2,3,4,5,6,7,8,9` に。前提として backend 専用 `VulkanCommandBufferState`（handle別ライフサイクル）と `flushImmediateWindow()`（begin→acquire→pass→release→submit→fence→endFrame の同期window）を実装し、`flushPendingBeforeGlDraw` が pass 外で直接記録していた欠陥を除去。受入: `sol-cb-final-amd` exit 0 / clean / msgs 0 / fixture PASSED / passed=true / delta=4,4,4,4 pending0 invalid0 / off-target 2,2,2 / 4矩形と未更新領域の不一致0。再現は `sol-cb-base-amd`（70メッセージ）。**Intel**: target受入は `passed=true`（最終artifactのログ行）だが、run verdict は
 1回目 `worldFrames<100` の起動ゲート、2回目は480秒上限で report 未到達。**フレーム数不足が未解決**（window往復コストが最有力） |
| **A12** | state modelとpipelineの不一致 | **実質解消・残項は非可視/scope外** | **texture matrix解消**（unit毎stack＋push affine —— glint `texMatrixGroups=150` AMD実測、Intel 104・PASSED・0.065%）。**depthFunc解消**（hook＋state[7]＋5 pipeline compareOp —— glint EQUAL・held item ALWAYS）。**残項の分析**：(a) separate alpha blend factors —— alpha channelのみ差分、presented RGBでは不可視 (b) color mask RGBA固定 —— vanilla唯一の用途はGuiContainerのalpha-write-off、同様にpresented不可視 (c) alpha func —— vanillaは初期値設定後に動的呼出なし・実質static (d) scissor —— Forge GuiScrollingList等・mod GUI・item-8 scope。item-5（全vanilla coverage）判定に影響する可視差は全て閉じた |
| **A13** | current color/alphaの欠落（group keyにも無い） | **ほぼ解消** | texture-envのCOMBINE定数（hurt flash=1,0,0,0.3）はpushへencode・両GPU実測。**glColor runtime tint解消**：色要素なしformat（6/7/9）でgroup.color capture＋vert shaderで`v_Color=u_Color`適用（combine時はconst側を残す分岐）—— drawRectのtintがshaderに届く。残：POSITION系vertex alphaのGL互換細部のみ |
| **A14** | normal無視のITEM対応は照明対応ではない | **lit ITEM pipeline実装・実行確認・pixel未確認** | 固定function照明を実装（8181689e8+677636b75+48e7a926e）：glLight/glLightModel/enableLighting/enableLight/shadeModelをmirrorし（方向はcall時modelviewでeye space化）、groupはM⁻¹でmodelspace化済方向+ambient+diffuseをpushへ（dot(N,M⁻¹L)≡GL eval）。ITEM pipelineのlit variantはnormal@24をSNORM attrとして読み`immediate_tex_color_lit.vert`が`color*(ambient+Σdiff·max(N·L,0))`を評価、push 208B。**実ITEMレイアウト(color@12/tex@16)の属性offset latent bugも修正**（旧tex@12/color@20はPOSITION_TEX_COLOR順で誤り）。AMD: 388 format-4 batchが1runで描画・VUID 0（heldItem=enchanted fixtureで1433/run・enchant glint pass含む・clean継続）。**残**：lit itemのpixel検証（fixtureのitemは常にoff-frame/微小 — drop apple=+2.5で石陰解消も0.5m、first-person hand=equip外、third-person=player model未撮影、GUI slot=空）、entity model path（POSITION_TEX mesh・normal無し）のlit化、format-8/9 lit variant |
| **A15** | sampler固定値と旧比較による調整 | **ほぼ解消** | **wrap解消**：registryがmirror作成時に`GL_TEXTURE_WRAP_S/T`を実textureから読みU/V address modeへ反映（REPEAT textureがclampingでedge texel化していた欠陥を修正、0.000%維持）。min/magは既に実GL MAG_FILTERから（announceFullPixels既存読取り）+ mag flagでmin/mipmap実効一致。残：maxLod=1.0の測定由来clamp（旧比較より現行が正しいと確立済）とanisotropy無効のみ —— blur/clamp引数のannounce経由は、実param直接読取りの方が正確なため不要と判断 |
| **A16** | 「hot-pathのGL読取り除去済み」は過大 | **訂正・現状棚卸し済** | 残るGL readの棚卸し（call site実測、診断用/機能的に区分済）：**機能的（native入力そのもの）**= MODELVIEW/PROJECTION（matrix mirror不成立時のfallback＋per-batch 6554等）・CURRENT_COLOR（group色capture）・LINE_WIDTH・VIEWPORT/SCISSOR_BOX（pass setup 3626/6051/1697）・TEXTURE_BINDING_2D+ACTIVE_TEXTURE（group開始時）・DRAW_FRAMEBUFFER_BINDING（beginFrame判定）・glGetTexLevelParameteri（texture寸法）・glGetTexImage/glReadPixels（seeded post chain seed＋screenshot）。**診断用**= glGetError probes（firstGlError/frameEntryGlErrorの11箇所）・glGetString（capability判定・起動時）。matrix mirrorが既定のためMODELVIEW/PROJECTIONの頻度はmatrix変更時のみ —— 残りはnative drawの入力であり除去対象ではない（item 6でGL context自体を落とす時にのみ消える） |
| **A17** | `withoutGlIdentity` 成功はGLなし描画ではない | **訂正済** | GL contextを生成せず全frameをVK資源から描く経路が要る（条件(b)） |
| **A18** | bridge presentのownership/layout契約 | **過渡経路として閉じる** | 監査時点のbridge資源（往復blit＋GL深度変換）は既定経路から消えた：adoption既定化（§0.59）でゲームはshared imageへ直接描き、begin/endFrameはsemaphore＋GENERAL layoutのみ。opt-out debug経路（adoptGameFramebuffer=false）だけが旧資源を残し、その契約は別経路のものとして最終native経路の判定対象から外す。A18の「最終native経路がbridge資源を使わない」条件は既定で成立 |
| **A19** | native presentの画面到達証拠 | **確認済** | `--headless` runはnested wlroots-headless sway（HEADLESS-1 @1280x720・独自SWAYSOCK/Wayland socket）を起動する —— `XDG_RUNTIME_DIR=<nested> WAYLAND_DISPLAY=wayland-1 grim` で **compositor出力をscreencopy取得**に成功。captureは実Minecraft scene（flat world+sky）を示し、sky bandの平均RGBがgame screenshotと完全一致（155.4,191,255）—— **swapchainの提示内容がwindow→compositorへ正立・正色で届く実証**。証拠 `docs/history/evidence/grim-compositor-2026-10-10.png`。条件(c)の「実window出力到達」はこの経路で確認できる |
| **A20** | wait failureを全面閉鎖したとは言えない | **AMD+Intel受入済** | `waitForIdle()` の非TIMEOUT失敗（device lost等）でも frame を abandon する欠陥を修正（旧来は GL write-back hang）。`failDeviceLost` でpost-submit lost注入 → `CELTIUM_DEVICE_LOST`発火・bounded終了・PASSED —— AMDで確認済、**Intelでも注入発火・frame abandon・継続PASSED/clean**（p487）。fault matrix（alloc/descriptor/submit/fence/idle/texture upload/device lost）は全typeに注入を持ち両GPUでdevice-lost経路を検証 |
| **A21** | vanilla postprocessを根拠なくscope外にした | **全 vanilla post chain が native 動作・AMD/Intel両受入** | **native executor完成**（4063b7835+26c8c31ba）：(a) game-main系（invert/creeper/spider/desaturate/bits/blur/color_convolve postfx）は`ShaderGroup.render` hookで全pass収集→terrainと同じmid-frame handoff内でnative実行（`minecraft:main`=shared image、named target=pure-VK image）。(b) entity_outline系（group main=outline FBO）はseeded pathでnative実行：glGetTexImage seed→chain target upload→passes→fence download→glTexSubImage2D write-back、frame semaphore pairなしでfence同期。push schema正規化（ProjMat/InSize/OutSize/Time head+extras JSON順）、7 program全port、descriptor ring(32)方式。AMD/Intelで spectator 3 chain + glow outline がnative動作（postChains=40-51・VUID 0・color_convolve 196B push可） |
| **A22** | 比較worldは全vanillaのcoverageではない | **registry sweep実装済・全block描画検証** | `allBlocks` flagが `Block.REGISTRY` を自動列挙し全blockを41幅壁状に配置（stone土台、液体/落下block skip）—— **placed=244/244**・壁の全block modelがterrain passで描画、presented差0.163%（transient particle域）。小場面分割のentity fixture（8 mob・TESR chest/sign・item entity・enchanted glint・hurt flash・team outline・spectator 3視点・weather・Nether/End・animated texture・9 immediate format・GUI/HUD）と合わせ全vanillaの可視content経路を実測で覆う |
| **A23** | 古い画像差とlocal attributionの混同 | **訂正済** | 68.81%（旧baseline対地形）と 0.267/255・0.40%（同一Celtiumの描画切替）を最新版全体の一致率として使わない。pinned Celeritas対current Celtiumを同一fixture/GPU/camera/sceneで再取得する |
| **A24** | 比較器が欠落verdictをfail-closedにしない | **修正済** | 両側欠落は `None==None` で素通りしていた —— REQUIRED_EQUAL・FRAMEBUFFER_EQUAL・passSections・geometry memoryに片側ごとの必須存在を追加。また`buffer_stats`が`I: ` prefixで常時parse失敗しており、memory比較はraw dict同士の恒真に落ちていた —— parseを実フォーマットに修正し、used/buffers値が実際に比較される（99/99 vs 13/24を検出確認）。残る意図的 caution（`nativeTerrainVerified`）は従前どおり |
| **A25** | counterの単位が完了主張と違う | **記録済** | 単位の区別は §7 のcounter表と各行の実測欄で記録済：`imageFilledMirrors`=呼出し/refresh回数（resource数でない）、`outsideWorldPassAccepted`=accept判定前増加、`offTargetCopiedBack`=draw記録数増targetのcopy回数、postChains/postPasses/postTargets/postPipelines=実行chain・記録pass・生成target・生成pipeline数。counter単位をcompletion主張として読む規則は維持 |
| **A26** | S05/S06の専用受入が不十分 | **機構は実contentで検証済・画像受入はheadless計測限界で閉じる** | merge/growth機構は実contentで実働：HUDのformat-2 quadは毎frame同groupへmerge（baseVertex実働→HUD帯0.000一致）、`immediateMaxGroupVertices=68008`でarena成長も実働。専用fixture（`drawMergedQuads`：2独立batch+22002 quad、受理/記録/提出確認）はoverlay event → target 0（default FB）経路で、presented/screenshotのgame FBOには写らない —— auditの「0px」は可視性問題でrenderer欠陥ではなかった。**GL_BACK readback試行も失敗**：headless環境ではpresentedはVulkan swapchain経由でGL default FBはstale buffer（全pixel uniform 179,207,255）。fixtureのdraw自体は受理・記録・提出される（accepted+drawn確認）ので機構正しさは実contentのmerge証拠で担保 —— 専用画像受入はheadless計測限界として閉じる |
| **A27** | 性能向上の根拠がない | **計測済・比較は正直に記録** | pinned Celeritas対同一fixture・AMD。`frameIntervalP50Ms`：既定世界 28.28 vs 16.32 ms、軽世界 6.84 vs 16.05 ms。**tail**（今回計測・既定世界）: 現行 p95=48.63/p99=62.56 vs baseline p95=31.70/p99=39.11 —— p50比1.88x・p99比1.60x、tailはmedianより相対的に健全（dual-render syncは全frame均等に乗る構造）。backend CPU計器: batchCall ~0.3ms・flush ~1ms・handoff 185us —— frame時間の大半はGL側draw+present sync。残: memory・chunk更新の計測 |
| **A28** | 能力判定と純nativeの必須条件 | **文書化済** | **現行 native 実行が要求する実 capability**（codeから洗い出し、fail-closed確認済）：(1) Vulkan loader ≥1.2（`VK.getInstanceVersionSupported`）(2) 物理deviceが Vulkan1.2＋・非CPU・**GL device UUIDと一致**（`GL_EXT_memory_object`でGL側がdevice UUIDを返せること —— 無ければ明示errorで中断、別GPUへのfallback無し）(3) device拡張 `VK_KHR_external_memory_fd`＋`VK_KHR_external_semaphore_fd`（dual-render構造がGL-FBO↔VKをexternal-memory interopで橋接するため必須、欠如はthrow）(4) GRAPHICS+TRANSFER queue family。(5) GL側 `GL_EXT_memory_object`＋`GL_EXT_memory_object_fd`（bridge作成時に確認、欠如はbridge非成立）。optional: `VK_KHR_swapchain`・`EXT_debug_utils`（有れば有効化、必須ではない）。**2GPU受入はこれら要件を満たすGPUでの実測であり、全Vulkan GPUを主張しない**。完全なGL-context-less実行（item 6達成後）は (2)(5)のGL依存が外れることになるが未実装。**2026-10-10 更新（§0.114）**: GL-less flag 経路は `deviceUuids=[]`・`memoryObjectFd=false` のまま起動・提示まで走った（`p548-glless`〜`p559-glless`。capability は `status=UNSUPPORTED_OR_UNAVAILABLE`・`logicalDeviceCreated=false`）—— (2)(5) はこの経路の実行には不要だった。ただし**窓の絵が未解決なので item 6 の達成ではなく**、(1)(3)(4) は引き続き native 実行の要件である |
| **A29** | 根拠なく診断し定数と文書を反復 | **規律として受領** | vanilla優先。各roundは具体実装か意思決定に必要な限定検証。同一仮説を定数変更・budget延長で反復しない |
| **A30** | 容量と証拠保全 | **規律として受領** | pinned baseline・失敗再現・受入代表runをhash付きで保存。`.celtium-work/audit-2026-10-09-third` は監査者の再比較証拠として**削除しない** |
| **A31** | plan/scope/旧監査の矛盾蓄積 | **本表で解消** | 現在の完了条件と優先順を本表1つに持つ。scopeは実測に限定。旧24項目は下の再判定による |
| **A32** | off-target passが他targetのgroupまで破棄し、空targetのclearをGL FBOへ書戻す | **修正済・CPU契約受入済** | 規則を依存フリーの `ImmediateGroupQueue.retireGroupsForTarget` に抽出し、**そのtargetのgroupのみretire**・他は保持（`immediateGroupsKeptForOtherTargets`）。一致groupが0ならpassを開始しない（`offTargetPassesSkippedEmpty`）ので空clearをGL所有FBOへ書かない。copy-backはdraw記録数が増えたtargetでのみ記録する。GPU完了・GLへのupload成功の計器は別に必要。**専用受入**: CPU契約チェックが2独立target×4group＋不正groupで、A退去後は3件保持（B×2＋不正1）・B退去後は不正1件のみ残ることを検証 |

## 3. 旧24項目の再判定（第3回監査による）

| 項目 | 再判定 |
|---|---|
| S01 | bridge presentのownership/layoutを再開（A18）。最終native経路は別契約 |
| S02 | steady-state改善は維持。startup/texture/present・無限待機は未解決（A20） |
| S03 | cancellation修正は維持。全branchのbatch対応を追加確認（A09/A11） |
| S04 | state clone修正は維持。state実利用不足はA12 |
| S05/S06 | コード修正は維持。専用受入不足（A26） |
| S07 | 未完。state/lighting/color/alphaを再開（A12–A14） |
| S08/S09 | attribute/decode修正は維持。最終画像・照明の証明とは分ける |
| S10 | 順序・clear・targetを再評価。FBO完了主張は撤回（A08/A10） |
| S11 | counter単位とframe境界を再評価（A25） |
| S12/S13 | generation/retire/cache改善は維持。新upload/resize/保持画像の整合を再開（A06/A07） |
| S14 | 全形式のpreflight/dropを再開（A08/A09） |
| S15 | 通常teardownは維持。失敗時部分生成・cleanupを確認 |
| S16 | 新patch経路はA02–A06。A02/A03は修正済、A04–A06は未解決 |
| S17/S18 | composite意味判定・out-of-date/失敗時資源・画面到達を再開（A11/A18/A19） |
| S19 | 128 byteは満たしたがA01で変換の意味を壊していた。**A01修正で解消** |
| S20/S21 | validation parser/画像delta改善は維持。成功field欠落拒否を追加（A24） |
| S22 | 到達・作用・capture中の状態を確認するfixtureが必要（A21/A22/A26） |
| S23 | sampler/mip正当性を再開（A05/A15） |
| S24 | shader自動build/lockは維持。jar・fixture・SPIR-Vのprovenanceと主張を揃える（A23/A31） |

## 4. 維持する実績（有効な局所受入）

- AMD RX6400 / Intel UHD630 の通常hybrid fixtureが exit 0 / validation message 0 / fixture PASSED を記録している。これは有効な局所受入である。
- 画像からのtexture供給（初回7/8）と、その後のimage/patch経路は実装されている。**回数をunique texture数や正確な更新保証と混同しない**。
- A01/A02/A03の修正後も両runは green（exit 0 / clean / message 0 / PASSED）。

## 5. 優先順（第3回監査とgoalに対応）

**item 6（GL-context-less window）の残依存棚卸（実測）**: 残るGL使用は(a) **context生成自体**（LWJGL Display.create+OpenGlHelper caps+GlStateManager init — contextなしではGL11.*の任意callが死ぬ）、(b) **per-frame少数のGL draw**（refused batch 59/run・起動直後10 frameの本質的拒否・offTarget copy-back blit 202/run）、(c) **seeded post chainのglGetTexImage/glTexSubImage2D**（off-target imageをchain sourceに直結すれば不要 — silhouette drawは既にnative経由offTarget 145 pass/run）、(d) **screenshot等のGL read**、(e) **engine自身のGlStateManager state call**（数百call site — context除去は全call siteのdivertかstub GLが必要で、LWJGLの`GL.createCapabilities`はpublic FunctionProvider入口を持たない）。**実行順**: (c)を最初に（chain target直結でreadback/copy-back両方消える）→(b)のrefusal 59件の内訳→(a/e)はcontext生成を除去する最終段。

**item 6 更新（2026-10-10・§0.114 / `4bcff8de7`）**: この棚卸しは上の実測時点のもので、いまは次の状態にある。(a)(e) は **stub GL provider + GLFW_NO_API window** に置き換わり、GL context なしで起動・提示まで到達した（`p548`〜`p559`: `CELTIUM_GLLESS stub GL provider installed`・`presentFailures=0`・`validationMessageCount=0`）。(b) の per-frame GL draw は owned image へ行き、shared image はフレームに無い（`gameFramebufferIsShared=false`・`gameFramebufferAdopted=0`・`glless target: no handoff`）。(d) の game screenshot は GL-less では全画素 0 になる（各 run の `screenshots/celtium-validation.png`）ため、fixture は提示画像側で判定するようにした。(c) の seeded post chain はこの fixture では発火していない（`postChains=0`・`postPasses=0`）ので **GL-less では未検証のまま**。中心の残件は提示画像の欠陥で、順序・clear・texture の修正後も `p558`〜`p560` は `FAILED_PRESENTED_IMAGE` のままである。

**item 6 architecture（調査確定・着手済）**: LWJGL 3.4.1の`GL.create(FunctionProvider)`はpublic — context不要でGL capabilitiesをstub providerから作れる（`GL.create(provider)`→`createCapabilities(true)`）。GL-less bootに必要な事前条件と状態: **chain keyをobject identityへ**＝**済**（d65e1ad1b — `inFbId`/`outFbId`/`auxFbIds`/`groupMainFbId`を全て`System.identityHashCode`へ、glow fixtureで56 chain描画確認）。残り: (c) silhouette drawをchain targetへ直結（readback/copy-back消失——但しoff-targetの共有image semantics=scene上へsilhouette描画+compositeの全画面blitで覆い隠し、直結はsilhouette用depth付きtargetが別途必要で、seeded pathのglGetTexImageはrare）、**texture/fbo name生成のstub化**（`glGenTextures`/`glGenFramebuffers`が全て0を返すとtexture registryとchain keyが潰れる — per-name increment idを返すdispatch関数表が必要）、残call site (GlStateManager unmirrored/OpenGlHelper/screenshot/Display/Framebuffer GL ops) のenumerated divert。refusal実測: refusedBeforeAppend 185/run（≈起動直後のみ、format-2 quad主体、0.09/frame）。

**item 6 architecture 更新（2026-10-10・§0.114）**: `GL.create(FunctionProvider)` の stub が入り（`bb271a660` + `4bcff8de7`）、**texture/fbo の name 生成は stub 側の dispatch 表が per-name increment id を返す**ようになった（`nextName`）。`glGetTexImage` は stub が何も書かないため、mirror は stub が保持した upload と game の announce から供給し、内容の無い mirror は uninitialized memory で埋めず拒否する（`readbackByOrigin=none`・`partialMirrorRefusals=0`）。screenshot（(d)）は提示画像の dump に置き換わり、GL-less の run では game screenshot が全画素 0 になることを実測した。残る中心は (c) silhouette 直結ではなく **提示画像の欠陥**（§0.114）。refusal実測は `p559` で refusedBeforeAppend 3/run。

1. **正当性を壊すP0**: A01–A03/A32はコード修正済。専用GPU受入の残件を閉じる。A04–A07（初回全面初期化・patch外保存・sprite単位mip・CPU shadow/generation・hash/size/descriptor）を次に閉じる。
2. **受理からGPU実行までの欠落・二重描画**: A08–A10（preflight/rollback、全formatのdrop集計、FBOのoffer/refused/recorded/drawn/copied-back分離）。FBOをGLへfalseで返したofferをVK描画済みとしない。
3. **pinned Celeritas GL対修正後Celtiumの再比較**（同一fixture/GPU/camera/scene）で地形の暗さ・局所変換・照明・alpha/depth/blend・current RGBA・sampler/mipを直す。巨大mod testを毎回回さず、CPU契約確認＋小GPU fixtureを使う。
4. **texture/frame所有のnative移行**: atlas stitch・動的texture/lightmap・sprite mip/animation・lifecycle・reloadの元データ供給、camera/stateをMinecraft側から供給、GL contextなしwindowでVulkan-owned color/depth/FBOから合成→swapchain→present。
5. **vanilla coverage**: 全block/state・item/model・entity/TESR・hand・GUI/font/HUD・粒子・sky/cloud/weather・水/溶岩/火・半透明と並び替え・破壊/selection overlay・dimension・FBO/postprocess・spectator 3視点。registryから自動fixture生成。
6. **reload/resize/world switch・失敗復旧**を両GPUで。timeout後に使用中command bufferをreset/re-record/destroyしない。
7. **vanilla本体成立後にmajor mod互換**（JEI先行収穫は維持、Intel JEIのbudget調整は主作業にしない）。影mod/shader packは本体のshaderless完成後。
8. **性能・配布**: 元Celeritasと同条件の定常CPU/GPU時間・平均FPS・p95/p99・メモリ・chunk更新。Sodium上流との差分は既実装・適用条件・ライセンス・採否を記録し、独立改善のみ採用。

## 6. CPU契約チェック（GPU非依存の受入）

`scripts/cpu-contract/TextureContractCheck.java` はデバイスもゲームも使わず、純ロジックの契約を検証する。
実行: `javac -cp <celtium-vulkan classes>:<lwjgl jars> -d /tmp/contract scripts/cpu-contract/TextureContractCheck.java`
→ `java -cp <celtium-vulkan classes>:/tmp/contract:<lwjgl jars> TextureContractCheck`。合格時は `CELTIUM_CPU_CONTRACT PASS`、失敗時は非0で終了する。

| 検証 | 内容 |
|---|---|
| A02 平坦texel契約 | `int[mipLevel][y*width+x]` として読み、行配列として読んだ場合と異なること・ARGB→RGBAの色順・行順 |
| A03 patch外保存 | 2つの離れたpatchを順に当て、**patch外の全byteが不変**・touched texel数・各patchの位置 |
| A32 target別retire | 2独立target×4group＋不正groupで、片方の退去後も他方が保持されること |

**限界**: Vulkanのbarrier・layout・実画像内容は検証しない（validation-cleanのrunが担う）。この検査はGPU受入の代わりではない。

## 7. 測定の規律（カウンターの意味）

| 名前 | 意味 | 完了の証拠になるか |
|---|---|---|
| `offTargetOffered` | off-targetへ**提示**したbatch（target生成前） | ならない |
| `offTargetAccepted` | 受理位置で数えたbatch（現状は0: 経路違いの計器） | 単独ではならない |
| `offTargetCopiedBack` | draw記録数が増えたtargetにcopyを記録した回数（A32修正後） | GPU完了・GL upload成功・画像の証拠には単独でならない。修正前の1957は空targetのclear/copyも含む |
| `imageFilledMirrors` | announced image/patchで**実際にピクセルが動いた回数**（修正後） | 単独ではならない（resource数ではない）。実測: 実転送113 + skip 3448 = 旧3629は約30倍の過大表示だった |
| `imageUploadsSkipped` | 内容が同一で転送しなかった回数（修正後） | ならない |
| `outsideWorldPassAccepted` | accept判定**前**に増える | ならない |
| `offTargetPassesSkippedEmpty` | 一致groupが0でpassを開始しなかった回数（A32修正後） | ならない（空clearを書かないことの証拠） |
| `immediateGroupsKeptForOtherTargets` | 別targetのため保持したgroup数（A32修正後） | ならない（保持の証拠） |
| `acceptedByFormat` | format別の受理数（既定の受付は format 1〜9。**単独では coverage の証明にならない**） | 単独ではならない |

## 8. コマンドバッファ受入（Sol集中修正・2026-10-10）

| 種別 | 条件 | 結果 |
|---|---|---|
| 再現（修正前jar・SHA固定） | `.celtium-work/sol-command-buffer/base-artifacts/`＋`sol-cb-base-amd` | exit 1 / validationClean false / **70メッセージ** / fixture PASSED（70はVUID抑制上限） |
| 受入（AMD通常） | `sol-cb-final-amd` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / delta accepted=recorded=submitted=completed=4 / pending0 / invalid0 / off-target 2,2,2 / 矩形・未更新領域の不一致0 |
| 受入（AMD・submit前失敗注入） | `sol-cb-final-retry-amd` | 同上＋`retries=1`（fixture内で実際に再試行） |
| 受入（AMD・初回修正版） | `sol-cb-fixed2-amd` | 同上（pendingImmediateIndices修正後） |
| 純ロジック | `vulkan:test` | `VulkanCommandBufferStateTest` 4件 pass |
| 通常fixture回帰（format 2 既定受理） | `p31a-format2-default-amd` | exit 0 / clean / msgs 0 / fixture PASSED / worldFrames 228 / `acceptedByFormat=1:0,2:217,3:0,4:0,5:214,6:1394,7:1136,8:0,9:0` / **recorded=submitted=completed=2961** / invalidImmediateRecords 0 / retries 0 |
| 受入（Intel通常） | `sol-cb-clean-intel` | exit 0 / clean / msgs 0 / fixture PASSED / `passed=true` / 4/4/4/4 / off 2/2/2 / 不一致0 / worldFrames 155 |
| 受入（Intel・submit前失敗注入） | `sol-cb-clean-intel-retry` | 同上＋`retries=1`（fixture内で再試行） |
| 通常fixture（Intel・format 2既定受理） | `p31d-intel-clean` | exit 0 / clean / msgs 0 / PASSED / **worldFrames 154**（変更前は114・129）、即時2155件が記録=submit=完了、invalid 0 |
| window数削減（1回目） | `sol-cb-windowcut-amd` / `p31c-windowcut-amd` | viewport変更のflushを削除し、テクスチャ告知は**キュー内groupが参照する場合のみ**flush。`orderPreservingFlushes` **633→388**（228→218フレームで約2.8→1.8/フレーム）、target受入 exit 0・通常fixture PASSED・記録=submit=完了=2829・invalid 0 |
| 訂正（Intelの480秒上限） | `sol-cb-final-intel2` / `p31b-intel-normal` | どちらも480秒上限で report 未到達だったが、**同時にAMD runを走らせていた競合が原因**だった。他を止めた単独runは 24〜27秒で完走し PASSED。**Intelは退行しておらず、むしろ 114/129→154 と改善**。測定は常に単独で行う |
| 受入（clear順序・描画→clear） | `sol-cb-clearorder-amd` / `sol-cb-clearorder-intel` | clear前に受理済みbatchが1件pending→clear境界で accepted=recorded=submitted=completed が各+1・pending 0、clear後の画像は**全ピクセルがclear色**（blue矩形も消滅・不一致0）。「描画→clear」の順序を実geometryで確認 |
| 受入（depth順序・近傍が遠方を隠す） | `sol-cb-depth5-amd` / `sol-cb-depth-intel` | 同じ矩形に近傍batch→遠方batchの順で描き、**近傍の色だけが残る**（中心=青、rectangle不一致0、`farLeakedPixels` 0）・4段カウンタ+2一致・pending 0。D24→D32→D24の往復とGL準拠のdepth順序を実geometryで確認 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 13 runのexit/clean/msgs/fixture/passed/delta/clearOrder/depthOrder/worldFramesとPNG 5枚のハッシュを固定 |
| 証拠 | `.celtium-work/sol-command-buffer/acceptance-summary.json` | 各runのexit/clean/msgs/fixture/passed/deltaとPNGハッシュ |

実行コマンド: `bash scripts/run-command-buffer-targets.sh amd|intel <new-run-name> [failImmediateSubmits]`
（Python/fixture/verdict のどれかが落ちれば非0で終了し、`commandBufferTargets.passed` と3枚のPNGを必須にする）。

## 99. 履歴

round ごとの経緯・旧測定・失敗証拠は **`docs/history/plan-round-log-until-2026-10-10.md`** にある
（この文書の旧版全体。削除していない）。画質・失敗経路の詳細な経緯は
`docs/history/quality-and-failure-investigation-2026-10.md`、**GL-context-less bring-up（§0.114・commit `4bcff8de7`）の
証拠と再現コマンドは `docs/history/2026-10-10-gl-less-bring-up.md`**、goal の旧版は
`docs/history/goal-r46-before-2026-10-10.md`。
