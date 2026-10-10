# Celtium 構造修復プロンプト — 2026-10-10

ユーザーからの新しい指示:
「1晩動かしてエントロピー溜まって局所修正が多くなってるから修正プロンプト送ってあげて」

現在のgoal達成へ向けて、次の安全な作業境界から構造修復を優先する。これは開発を止める指示でも、全面書き直しでもない。現在進行中のdimension到達修正・必要な受入を完了し、未commitの変更と成功した修正を保全してから進める。

## 事実を読み直す

2026-10-10 10:29前後の確認:
- HEAD bf27f1975: capture tick等号の修正・部分report追加。Intelで完走したことは前進として維持する。
- ValidationClient.javaにdimension到達修正の未commit差分がある。getWorld/server player操作のserver task移動、client到達観測を進めている。この差分を巻き戻さない。
- docs/plan.mdは3,043行。冒頭表はdimension未検証やGL読戻し0/frame等の古いscopeを含み、後半の新しい測定と一致していない。現在状態とround履歴が再び混在している。
- VulkanTerrainGpuBackend.javaは5,671行。pending/recorded/retired groupにObject[]の添字契約があり、textureのregistryと直接保持atlas/lightmapで更新経路が分かれている。行数自体を欠陥扱いせず、所有・状態遷移・責務の重複を直す。
- docs/hints-2026-10-10-nether.mdのatlas更新先の分裂は未解決。capture問題を解いたので、600秒の予算延長を主作業に戻さない。

まずHEAD・dirty差分・該当実装・最新artifactを短く確認する。過去roundの結論を前提に読み進めない。既存fixture PASSEDを万能の正しさ証明にしない。

## 次の作業単位: 局所条件の追加ではなく、同じ契約にまとめる

最初の短い作業単位で次を行い、その後すぐ完成工程へ戻る。全システムの設計書作成や抽象化だけに何roundも使わない。

1. **textureの実体・更新・寿命を一つの契約にする。**
   atlas/lightmap/dynamic textureについて、CPU原本、初回全面初期化、resource generation、spriteごとのlevel別更新、実際にdescriptorがsampleするVk image、reload/delete/ID再使用の所有者を明示する。
   announceFullPixelsがregistryの別imageへpatchし、invalidateTextureが直接保持atlasを全面GL readbackする分裂を修正する。同じ元pixelsを同じ実画像に適用する。一度成功したCPU更新を直後のinvalidateが無効化しない。
   dirtyな範囲とgenerationに基づいて更新し、正当性を守れる境界でまとめる。無条件全体readback・全mip生成・spriteごとの同期submit/waitがあるなら実コストを確認して減らす。サイズ/hash/descriptor更新は成功後に確定する。初回patchだけの未初期化image、更新無視、古い画像の使い回しで高速化しない。
   既存クラスへ統合するか小さな所有クラスへ抽出するかは実コードから選ぶ。新旧所有者を並存させる第三のラッパーを足して完了にしない。stitch/animation/reloadの元データをnativeへ運ぶ方向を保つ。

2. **frame・drawの状態遷移を明確にする。**
   offer/refused/accepted、queued、recorded、submitted、completed、retiredは別の事実として扱う。どのtarget/frame/resource generationに所属するかを保持し、失敗・再試行・target切替・teardownでも二重描画と消失を防ぐ。
   Solが直したVulkanCommandBufferState、target別retire、preflight、clear/depth順序を活用する。新しいフラグや個別分岐で既定経路を変えて同じ退行を再発させない。
   drawを取消す根拠は「対応する別経路が同じ内容を引き受けた/描いた」であることを具体的に保証する。ItemRendererという名前やcounter増加だけを根拠に消さない。
   Object[]の添字は今回触る範囲で意味のある型/recordへ寄せ、metadataと状態を混同しない。全group表現の一括置換を新たな大仕事にせず、移行後に旧経路を残さない。

3. **fixtureの完了判定とthread所有を同じ状態機械にする。**
   tick等号に戻さず、準備→到達待ち→描画待ち→capture→reportの一回限りの進行を維持する。待ちは有界で、到達していないのにPASSEDにならない。
   server結果はimmutableな結果snapshot一つを安全にclientへ渡す等で整合を保ち、複数volatile fieldやserver/render/shutdown threadによるJsonObjectの競合を増やさない。shutdownでは安全な進捗snapshotを使い、GL/game/serverの生きた状態を任意threadから呼び出さない。
   dimensionは実capture時のclient player/world provider、server到達結果、到達後frame/tickで確認する。
   PINNED_WORLD_TIMEは太陽等のworld timeを固定するが、spriteのanimation tick/frame固定とは同義ではない。等号廃止後に同一画像と言い切らず、実capture tickと必要なanimation位相を記録・統制する。
   duration上限・全実経過時間・測定区間frame・定常CPU/GPU時間を分ける。

ここで汎用GL→VK変換器、GL API全面エミュレータ、新しいDI/ECS/万能描画抽象を作らない。Minecraftのtexture/frame/draw/sceneの意味に沿ったnative化を続ける。単なる関数分割・改名・コメント増加で構造修復済みにしない。

## planを履歴ではなく現状表へ戻す

docs/plan.mdの冒頭から各受入表を現在のソース/実測と照合して更新する。「あとで末尾に訂正」を追加するだけでは不十分。
round別経緯と旧測定はdocs/history/へ移し、証拠・リンク・hashを保存する。古い受入や失敗artifactを削除しない。
現状表には完成条件、native/GL担当と既定/実験区分、未解決の根因、直近の具体作業、必要な受入・証拠を残す。一つの項目の現在状態を一箇所で管理し、audit番号やscope文書から参照する。
「通常fixture内の0/frame」と「全sceneでGLなし」を区別する。GL context・bridge・model/postprocess fallback・実window到達などの未完を都合よく縮めない。
仕様は維持する: Cleanroom専用、release21・ゲーム/build JDK25許可、広いGPU能力baseline、通常Forge非対象、既存Forge mod公開入口の互換、shader packは本体完成後、ライセンス規律。

## 実装と受入の出口

- 一度に一つの所有/状態契約を直し、関連するproducer/consumer/invalidate/deleteを同じ変更で整合させる。失敗する枝の見かけだけを合わせない。
- 使用中の実験flag・診断・暫定counterは用途と既定値を確認する。恒久的正当性を実験flagに依存させない。重複/到達不能/不要になった経路は根拠を確認して今回の範囲で撤去する。
- 今回の不変条件を壊せば失敗する最小の検証を行う。textureなら初回全面内容、patch外保存、実sample先、mip、reload/ID世代を確認する。CPUだけではGPU正当性を完了にしない。
- 必要な小GPU fixtureをAMD/Intelで一度通す。新しい差分・失敗・未解決の懸念がないのに同じfixtureや巨大worldを再反復しない。予算延長のみ、表現訂正のみのroundを積まない。
- 変更はreviewできる単位でcommitする。新しい所有者を使わない死んだ抽象、旧経路を併存させたままの半端な移行を残さない。
- 報告は「何の契約を直した / どの重複・例外を消せた / 何で確認した / 次にどのnative化を進める」を短く述べる。計画と監査の整理だけを完成扱いしない。

これは現在goal内での構造修復フェーズであり、完成目標の解除・縮小ではない。既存goal ID・仕様・最後の指示を維持し、修復後は全vanilla native化、GLなしframeと実present、残る互換・配布へ進める。

絶対に止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。thinkingで✓や✗の文字を使わない。
