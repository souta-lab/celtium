# Intel Nether / lava slowdown: targeted hints (2026-10-10)

対象: 稼働中DSHのCeltium session。確認済み: ad9527366 のp241-nether-intel、35adb95bfまでの公開応答、現在のソース。実装作業はDSHに任せ、goal・最後の指示は変更しない。
現在進めている同一jarの溶岩あり/なしA/Bは有用。ただしworldgenや予算だけと決めつけず、以下の具体的なコードを先に確認する。

## 1. MISSING_REPORTは「遅いだけ」ではなく、撮影tickを逃した可能性を最初に確認

ValidationClient.frame() が `if (worldTicks != 200) return;` で撮影/レポート全体を囲んでいる。client tickはrender tick間に複数回進み得るため、重いIntel runでは199→201などになれば、永久にレポートを書けなくなる。さらにremoveNonPlayerEntities(mc)>0でreturnした後、次の描画までに201へ進むケースもある。
interop branchには同じ囲みの内側に `if (worldTicks < 240) return;` があり、200と240の条件が両立しない。これはソース上の確定した問題で、Nether runの原因かはまだ未確定。

最小修正候補:
- capturePending / captured / afterInterop等の一回限りの段階を持ち、開始条件を>=200とし、準備やentity除去でreturnしても翌frameへ継続する。
- 画像比較のアニメーション位相は実captureTickを記録して揃える。厳密な200が必要ならtick/アニメーションを200で保持し描画を待つ。exact equalityで通常frameを逃がさない。
- render eventごとの前後worldTicks、threshold crossed、capturePending、worldFramesを低頻度で出す。終了時は成功でも失敗でも進捗reportをfinally/shutdownへ残し、MISSING_REPORTを無情報のままにしない。

600秒へ延長しても永遠に等号が戻らない問題は直らない。まずこの仮説を観測または小修正で潰す。

## 2. atlas animationが別々のVk mirrorを更新し、直後に全面GL読み戻しする経路

p241の最終CELTIUM_ANIMは directMirrorRefreshes=45122 / invalidations=45124 / atlasGlId=9 / lightmapGlId=12。512x512・imageLevels=10のCELTIUM_MIRROR uploadFromGlTextureが反復している。
このcounter自体はCPU供給も含むため全45122がreadbackとは断定しないが、実際の全面readback反復はログにある。

コードのつながり:
- TextureUtilMixin: announceAnimatedUpload(...) の直後に invalidateBoundTexture()。
- VulkanTextureRegistry.announceFullPixels(): registry.entries[id].textureへ16x16等のpatchを書き込む。
- VulkanTerrainGpuBackendは別にatlasTextureを直接保持する。
- invalidateTexture(): applyAnnouncedTo(atlasTexture,id)を試し、announcedImagesにBufferedImageが無ければatlasTexture.uploadFromGlTexture(...)。
- announceFullPixels()はentriesのVk imageを更新するだけで、announcedImagesや直接保持atlasのpatch供給には接続していない。
- dimension切替でbackend delete/recreateが複数回発生し、元のfull-image announcementを失う可能性も確認する。

優先する修正:
- atlas/lightmapの所有を一つに揃えるか、同じ元CPU pixelsを実際にsampleする直接保持imageにも供給する。registryの別imageだけを更新して成功扱いしない。
- 初回stitch/reload時の全面初期化を保証し、その後はsprite patch + 正しいsprite mip / dirty generationを適用する。未初期化imageへ部分patchだけ入れない。
- 元のGL writeに対し、CPU patch適用成功後のinvalidateが再度GL full mirrorを要求しない契約にする。target/image identityとgenerationを追う。
- uploadFromGlTextureはorigin・id・bytes・wall nanosを数え、同じsprite変更で何回全面upload/submit/waitするか確認する。
- uploadPatchFromPixels自体の全mip生成/queueWait/submit頻度も計測する。複数spriteをframe/update単位でまとめる場合、queued drawが旧pixelsを参照する境界を守る。

アニメーションfreeze/readback停止のA/Bは診断限定。更新無視・無根拠のskip・stale textureで速くして受入にしない。

## 3. dimension受入は実capture時のclient/server到達を必要にする

AMD Nether/EndとIntel EndのPASSED reportは、targetが-1/1でもdimensionAfter=0、dimensionTransferred=true。
dimensionAfterはaddScheduledTaskを投げた直後にclient dimensionを読むため、成功後の観測ではない。これだけで転送失敗とは断定できないが、成功の証拠にもならない。

- clientでUUIDのみcaptureし、server player取得・server.getWorld(target)・transferはserver task内にまとめる。現在getWorldはclient threadで行われ、p241にも[Client thread] Loading dimension -1がある。
- server taskからJsonObjectを直接更新せず結果を安全に渡し、client tickでSPacketRespawn/world replacement後の実到達を観測する。
- capture条件にmc.player.dimension==target AND mc.world.provider.getDimension()==targetを加える。server到達、client world identity、到達後frames/ticks、実sceneを記録し、未到達をfail-closedにする。
- p241では10:05:03にNether advancementが出た後、10:05:06にServer threadがdimension -1をunloadし、後のfogにはOverworld様の値もある。約600秒ずっとNether生成中とは言い切れない。移動後のscreen/world/player/server dimensionを観測する。

## 4. 予算・frame数・実経過時間を混ぜない

公開応答の「duration400s / worldFrames114だから1frame3.5秒」はまだ根拠がない。
--durationは上限であり実経過時間ではない。worldFramesが全実行frameか一定検証区間frameかも確認が必要。起動/生成/転送/テスト待ちを含むwallを定常frame timeとして割らない。
validation-process.jsonのelapsedSecondsと、frame counterの区間、実測p50/p95を分ける。p50=62.64ms等の値と矛盾する報告は期間定義を調べて訂正する。

次の限定runではclient tick/frame、server tick、dimension、screen、capture state、mirror timeを同時に取る。
まだ止まる場合は生きているJava PIDからclient/server双方のthread dumpを2回だけ採り、portal探索/worldgen・render upload/wait・capture missedを分ける。budgetを増やすだけの連続runはここで止め、具体修正へ進める。
