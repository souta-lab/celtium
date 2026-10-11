# Celtium performance plan

調査基準日: **2026-10-12 JST / 2026-10-11 UTC**。対象は Minecraft 1.12.2 / CleanroomMC 専用の native Vulkan renderer。ここでの「最新」はこの基準日の公開版・公開ソースを指す。

**目的は、同じ画・同じ描画距離・同じゲーム動作を、少ない CPU 時間、GPU 時間、転送量、メモリ、待ちで描くこと。mesh shader 非対応 GPU を含む baseline を最速化する。** 他のレンダリング最適化 mod と共存するための分岐・adapter・遅い共通経路は要件から外す。

この文書は性能改善の実行計画である。項目を載せたこと、他 mod で効果が出たこと、コード上の待ちを見つけたことを Celtium の性能改善の受入としない。**今回、新しいベンチマークや GPU 受入は実行していない。全候補の改善量は未測定。** 現在の完成判定は [plan.md](plan.md)、objective は [goal.md](goal.md)、影 mod / shader pack は [shader-plan.md](shader-plan.md) が管理する。

## 1. 固定する方針

### 1.1 対象と互換性

| 対象 | 方針 |
| --- | --- |
| Minecraft 1.12.2 / CleanroomMC | 唯一の起動対象。検証済み Cleanroom 0.6.13-alpha / LWJGL 3.4.1 を基準にする |
| Celtium の Java | `--release 21`、class major 65 以下。ゲーム・build・検証の JDK 25 は許可。Java 22+ API / preview には依存しない |
| OpenGL | 製品の描画経路・性能比較対象にはしない。GL context、GL 描画、GL readback、bridge、copyback、GL swap、GL fallback を要しない |
| 他の描画 overhaul / 最適化 mod | **共存非対応**。Celeritas、OptiFine、Nothirium、他の renderer replacement、描画 hook を競合して置き換える最適化を同時導入するための互換実装は作らない |
| 描画以外も最適化する複合 mod | 描画・model/resource hook が競合する構成は対象外。機能を切り分けられないものは mod 全体の共存を受入条件にしない。非描画機能だけの構成は、実際に競合しないと確認できた範囲で扱う |
| 通常の Forge gameplay / content mod | 維持対象。公開クラス・署名・イベント順・model data・resource reload・custom renderer の意味を可能な限り維持する。最適化 mod 非対応を、JEI・追加 block・entity・TESR 非対応の理由にしない |
| GPU | vendor 名で経路を決めない。必要能力を query し、baseline と同じ画を保つ。AMD RX 6400 と Intel UHD 630 を必須受入機にする |
| mesh shader | **採用しない**。必須にも、最初に高速化する特別経路にも置かない。ray tracing、vendor 専用拡張、tensor も要件にしない |
| 任意の Vulkan feature | baseline が成立した上で、追加要求を持たない GPU に不利益を与えず、効果が測れたものだけ。未対応時の経路も native Vulkan |
| shader pack / 影 mod | 本体と主要 mod の shaderless 成立後。バニラ ShaderGroup・spectator・outline は本体なので先送りしない |

同時導入非対応の検出は、実在する mod ID・版・競合 hook を確認して実装する。起動時に競合 mod と理由を明示し、深い描画中の破損に持ち込まない。非対応 mod を検出して黙って画を欠落させる、GL に戻す、名前だけ Vulkan にする動作は許可しない。

### 1.2 採用の基準

1. **正しさを保持する**: 光、fog、透明順、hurt tint、item transform、GUI clipping、event 順、animation の時間を削らない。
2. **既存の最適化を使い切る**: Celeritas にある worker・visibility・mesh・sort・batch を二重に作らない。実装の有無と、その run で有効だったかを別に記録する。
3. **native の意味でまとめる**: terrain、model/item、entity/TESR、particle、UI、texture、FBO/post、frame の契約を単位にする。汎用 GL API エミュレータには広げない。
4. **弱い GPU と小さいメモリを基準にする**: CPU の draw 数だけでなく、upload bytes、共有メモリ帯域、VRAM、fragment work、queue latency を減らす。
5. **測定で採否を決める**: 速そうな機能名、Vulkan という名前、他 mod の倍率、CPU counter だけで採用しない。

## 2. 今回読んだコードと証拠の範囲

### 2.1 Celtium / Celeritas の固定点

| 項目 | 固定値 |
| --- | --- |
| Celtium 調査 snapshot | `2026-10-11T15:35:19Z`、tracked source 538 files の読取コピー |
| 調査 HEAD | `9dffad96463ede052fafefe1de3b394a871ad23b` |
| snapshot の dirty | `VulkanTerrainGpuBackend.java`、`gl/CeltiumGlStub.java` |
| dirty patch SHA-256 | `7f2278fa320874db72387b72ac28c2cff4d5f4a72432fecaf07345e5ede02b3b` |
| 固定 Celeritas | `989ebf2e52ceed052ec112a569e0c1d172095756` |
| 主要候補の最終再確認 | `2026-10-11T16:26:31Z`、HEAD `f3c0b5b6e938169fca6fe66db21c2a5d20760ae5` とその作業中ファイル |
| 最終再確認時の dirty patch SHA-256 | `1ee68a7436ed55e71c8d07ec68cb710d728f4de0d0288ad8e0073f2a7cfadf2d` |
| その時点で残っていた候補 | group ごとの `new VulkanDynamicMesh`、terrain の per-command `vkCmdDrawIndexed`、present submit の CPU fence wait、draw 周辺の thread CPU 計測 |
| 調査 artifact | `.celtium-work/performance-study-20261011T153519Z/`。`metadata.json`、`dirty.patch`、source copy、external index、`live-check.json`、`final-check.json`、`final-dirty.patch`、`final-check-sources/` |

最終再確認で読んだ file の SHA-256:

| File | SHA-256 |
| --- | --- |
| `V/VulkanTerrainGpuBackend.java` | `726dfc4e70822ec85f12971707f3d9288543d1008d6e40e929014979dff0ff96` |
| `V/VulkanContext.java` | `3e3747483fffd9fad982b727286f7dab62ac85193c31a05ba884bb8241cb2a30` |
| `V/VulkanMultiDrawBatch.java` | `217334fec2ca5396ee9041500307f856a7205bf2338425ab75f5f709a2a7d0fe` |

開発はこの調査と並行して進んでいる。上記の行番号は snapshot の読取位置であり、**後続 HEAD の不具合やボトルネックを確定するものではない**。着手時に HEAD・dirty・原典・計器・実行 artifact を再確認する。受入済みの修正を古い監査から再び「未修正」に戻さない。

外部調査は Exa の **20 query / 検索候補 196 件**から一次資料を選別し、GitHub の 16 repository の HEAD・release 情報を別途確認した。固定した外部 source 57 files は、全 repository の網羅監査ではなく、候補に関連する実装・設定・license の選択取得である。検索結果件数を「196 件をすべて精読・検証済み」と表現しない。Minecraft の公式 release note、Khronos の sample/spec、VMA / meshoptimizer の公式文書も参照した。出典と固定 source は第 11 節に置く。

### 2.2 Minecraft / Forge 原典

この調査では `scripts/mc-sources.sh` で生成済み原典を引き、`EntityRenderer.updateTorchFlicker/updateLightmap/updateCameraAndRender`、`RenderGlobal.renderEntities/renderSky`、`GuiIngame.renderGameOverlay/renderHotbar/renderPotionEffects` と現行計器を確認した。

- Minecraft 原典: `.celtium-work/mc-sources/net/minecraft/`。
- Forge 原典: Gradle の Forge 1.12.2-14.23.5.2847 unpacked source。実パスは `scripts/mc-sources.sh` に従う。
- Cleanroom patch、SRG / notch 名は同 script の `--cleanroom`、`--srg`、`--notch` で確認する。
- 原典の読みは意味・format・順序を確認するために使う。**hook の生死、実際の draw、画の受入は run で確認する**。

特に 1.12.2 の lightmap は既に dirty flag を持ち、torch flicker が更新し、dimension・sunlight・lightning・boss modifier・night vision・gamma・partial tick に依存する。現行版 mod の「lightmap 更新を cache」だけを移植してよい処理ではない。

### 2.3 コード位置の略記

| 略記 | repository 内の directory |
| --- | --- |
| `C/` | `common/src/main/java/org/embeddedt/embeddium/impl/` |
| `F/` | `forge122/src/main/java/org/taumc/celeritas/` |
| `V/` | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/` |
| `S/` | `celtium-vulkan/src/main/shaders/` |

### 2.4 Celeritas から残っているもの

| 契約 / 実装 | 実コード比較 | 性能計画での扱い |
| --- | --- | --- |
| chunk build worker と適応 budget | `C/render/chunk/compile/executor/ChunkBuilder.java` は固定 Celeritas と byte 一致。frame / mesh cost の EMA と weighted queue が既存 | 新規 worker overhaul として数えない。upload bytes・優先度・待ち時間で不足分だけ拡張 |
| 非同期 visibility list | `RenderListManager.java` は byte 一致、`SectionGraph.java` の差は thread 名。既に async 経路あり | 既定 `asyncOcclusionMode=ONLY_SHADOW` なので shaderless の通常描画で有効とは限らない。effective option と run を確認 |
| visibility / occlusion | `SectionLattice`、section graph、frustum、任意の CPU bit raster が存在 | `useRasterOcclusionCulling=false` が既定。追加 GPU culling の前に既存機能の費用と効果を測る |
| chunk face culling、batch assembly、sort | `BatchAssembler`、`DirectMultiDrawBatch`、`IndirectMultiDrawBatch` は byte 一致 | CPU 側の最適化は維持。ただし GL の multidraw 実装があることは native 側の multidraw 成立を意味しない |
| compact terrain vertices | native に 24 B compact と 32 B vanilla-like の双方の pipeline がある | `useCompactVertexFormat=false` が既定。追加実装ではなく画質受入と既定化の候補 |
| empty section skip | `RenderSectionManager` は build task が null なら `EMPTY_DATA` を返す | 「空 chunk を skip」を改めて実装しない |
| visible sprite animation | `TextureAtlasMixin` / `TextureAtlasSpriteMixin`、section sprite ticker、item 側の active marking が存在。既定 true | 再作成しない。native cache が既存の active marking を迂回していないか確認し、必要なら時間契約を修復 |
| model / item の geometry 再利用 | native の `modelMeshes` と generation 管理、immediate group merge が存在 | 「entity ごとに全く cache なし」と扱わない。cache coverage・key・retire・miss の実測から拡張 |

この表は CPU ソースや native 実装の保存を示す。実行時の有効性、全 mod との正しさ、Celeritas と同じ FPS の証明ではない。

### 2.5 Celtium の追加費用と不足

| ID | snapshot で確認した実装 | 候補 / 判断 |
| --- | --- | --- |
| F01 | `VulkanTerrainGpuBackend` L4483–4538: group key に format・texture・state・pass・viewport・projection / modelview 等。新 group で `new VulkanDynamicMesh` | group による隣接 merge はあるが、変換・割当・行列比較が多い候補。frame arena と instance data に分ける |
| F02 | `VulkanDynamicMesh` L63–64 / L115–137: initial vertex 64 KiB + index 32 KiB。`VulkanHostBuffer` は各 buffer を個別 create / allocate / bind / map | group 数に応じた native allocation と最低確保量を減らす。96 KiB は constructor が要求する buffer 容量の和で、実際の heap 使用量ではない |
| F03 | `VulkanMultiDrawBatch.upload()` L54–58 は no-op。backend L7652–7678 は command ごとの indexed draw | Celeritas の assembled batch を本物の native multidraw / indirect buffer に接続する |
| F04 | `VulkanContext.submitWithSemaphores` L399–464 は submit 後に host fence wait。`endCommands` L567–625 も one-shot ごとに fence を作って wait | queue idle の修正だけで終わりにしない。copy / present の同期 submit、CPU / GPU overlap、resource retirement をまとめて直す |
| F05 | backend の mutable command / fence / uniform cursor は単一 frame 寄り。`beginCommands` は前回 completion を待ち reset | 2 frames in flight は mutable resource と final target まで所有を分離してから。wait の削除だけでは不可 |
| F06 | terrain の 128 B push + UBO は既存。immediate texture は 208 B、item は 216 B の layout。Context の表示値は 208 B | 128 B 保証の baseline に寄せる。表示定数ではなく SPIR-V / pipeline layout を原典にする。fog・light を省略して縮めない |
| F07 | immediate pipeline に descriptor cache がある。texture generation key を使い、texture 系は pool 64 sets × 最大 64 pools | cache は既存。reload / generation の churn と回収を測り、上限拡大でなく retirement と bounded reuse を実装 |
| F08 | pipeline object の state map は既存。`VulkanTerrainPipeline` L1182 は `vkCreateGraphicsPipelines(... VK_NULL_HANDLE, ...)` | object cache と driver `VkPipelineCache` を区別。初回 stall と resource reload stall の対策 |
| F09 | `threadCpuNanos` と `ThreadMXBean.getCurrentThreadCpuTime()` を preflight / group create / append / flush の周囲で呼ぶ | 常時の詳細計測費用を A/B。軽量 counter と opt-in / sampled profile を分ける |
| F10 | GPU query は availability 付きで読み、`WAIT_BIT` なし。terrain pass の timestamp と period / valid bits の確認が既存 | 計器を増やす前に既存を使う。frame ごとの query slot と他 pass の集計を拡張 |
| F11 | `VulkanGlTexture.uploadPatchedLevels` は領域・producer の mip levels を既に提出するが、helper の submit / wait を各 upload で使う | 「partial update 未実装」ではない。staging slice と frame 内 copy batch に統合する |
| F12 | 最終 native image を swapchain image へ blit する経路がある | native copy であり GL copyback ではない。final composite / present を統合すると得か測る。無条件の zero-copy 化はしない |

BH-27 の bootstrap `queueWaitIdle` 修正、BH-02 の sorted index 選択、BH-24 の UBO replacement の失敗原子性などは進行中の正しさ修復を尊重する。scissor / viewport の直近修正も、batch key・並べ替え・state cache の変更で壊さない。**これらの指摘は performance hypothesis であり、今回の実測ボトルネックランキングではない。**

## 3. 三世代の参照から何を取り込むか

### 3.1 Minecraft 26.3 以降 / Vulkan 世代

公式表記は **26.3**。2026 年の version 命名であり「1.26.3」とは記載しない。26.3 の Vulkan / SDL3 と、26.4 snapshot の変更を区別する。26.4 はこの調査時点では snapshot である。

| 参照 | 基準日の版 / 確認範囲 | Celtium で使う考え方 | 制限 / 採らないもの |
| --- | --- | --- | --- |
| Sodium | stable `mc26.3-0.9.2`。`0.9.3-alpha.1` は別枠。PR #3735 の Vulkan backend、#2887 の culling / task scheduling を確認 | cached draw list、native multidraw と capability 別経路、visibility と frame の分離、age / distance / work size を含む job priority | **現行 source の取り込み・翻訳・cherry-pick 不可**。PolyForm Shield を LGPL 世代と同一扱いしない。Celeritas にある async / adaptive budget を二重実装しない [R01–R04] |
| ImmediatelyFast | `1.17.1` / 26.3 branch。`1.16.0` で 26.2 Vulkan backend 対応を明示 | entity / TESR / particle / text / GUI の buffer batching、map atlas、sign text cache、FBO switch の削減 | 1.19.3 等の公表倍率を Celtium に転記しない。`canReorder=true` の一律化を 1.12.2 の透明・GUI に移植しない [R05–R06] |
| EntityCulling | `1.11.3`、26.3 artifact を確認 | 非同期・保守的な visibility、bounds inflation、fast camera / mod opt-out / generation の扱い | 1.12.2 対応 mod として同梱しない。protective license のコードは取り込まない。tick skip は renderer の既定動作にしない [R07] |
| MoreCulling | `1.9.0`、26.3 artifact を確認 | opaque face の証明、shape / opacity cache、item frame の不要 face、resource change の invalidation | 葉を hollow / aggressive にする画質変更は不採用。GPL 表示と埋込み許諾の README 条件を無視しない [R08] |
| Enhanced Block Entities | 固定 HEAD の default branch は **1.21.4**。26.3 / Vulkan 対応はこの調査では未確認 | 静的 TESR geometry を baked / cached 化し、蓋などの動く部分だけ instance transform を更新 | 「Vulkan 世代で動作確認済み」とは書かない。全 TESR を chunk に焼き込まない [R09] |
| BadOptimizations | 公開 README / release / MIT 表示を確認。26.3 での各機能成立は未確認 | dependency のある expensive CPU calculation の cache、lightmap / sky 更新の invalidation を考える手掛かり | sky cache の対応版に制限あり。1.12.2 lightmap の flicker / dirty flag / gamma 等を先に読む [R10] |
| Particle Core | 公開 source / README / MIT 表示。26.3 artifact は未確認 | particle quad の buffer 再利用、共通 billboard 計算、light lookup と保守的 culling | spawn 数削減・particle tick 省略は baseline に採らない。custom vertex consumer / renderer を無条件に固定しない [R11] |
| Minecraft 26.3 / 26.4 snapshot | 公式 release note。26.4 snapshot 1 / 3 を確認 | native pass、明示した shader input、present pacing、depth format の能力選択 | closed source を移植しない。SDL3 移行を Celtium の FPS 改善の前提にしない。OIT は bandwidth 費用があり必須化しない [R12–R15] |
| VulkanMod | `0.6.0` と固定 dev HEAD。26.3 の公式 backend とは別の Vulkan renderer | per-frame resource retirement、arena / staging、VMA と native upload の運用を比較 | backend を丸ごと導入しない。API emulation を目的にしない [R16] |

Sodium の #3735 は `VK_EXT_multi_draw` の経路と indirect fallback を区別している。これは参考になる設計だが、**Celtium の全対象 GPU がその extension / feature を持つ保証ではない**。#3307 の slope refine と #3297 の visibility 案は closed / unmerged の研究であり、merge 済み・効果確定として計画に組み込まない。

26.4 snapshot の透明処理の変更も「新 API だから常に高速」という証拠にはしない。1.12.2 の sorted translucent と同じ画を保つ baseline を先に改善する。

### 3.2 Minecraft 1.12.2 の現行参照

| 参照 | 基準点 | 採用候補 | Celtium での境界 |
| --- | --- | --- | --- |
| Celeritas | `989ebf2e` / 2.5.12 系固定 source | 既存の chunk worker・face culling・sort・compact vertex・batch assembly を維持 | 最重要の比較対象。新旧 engine の変更を何でも新しい最適化と数えない [R17] |
| VintageFix | `4159239`。GitHub release がないため古い検索上の版を「最新」としない | model / quad / state data の共有、dynamic resource cache、texture packing、純粋な decode / mipmap の並列化 | lazy load による first-use stall を p99 / miss 費用まで測る。Forge model bake event と custom loader を勝手に並列実行しない [R18] |
| FermiumASM | `5.31`、`76caf6c`。Normal/LoliASM 系譜 | immutable vertex data の dedup、使用 sprite の upload 抑制、resource lifecycle | Celeritas に visible animation は既存。terrain だけの predicate は fire / item / GUI を取りこぼす。FermiumBooter 等を Cleanroom に持ち込まない [R19] |
| StellarCore | `1.6.4` / `98c0ea4` | texture / model loading の粒度、stitcher cache、quad pool、bounded cache / JFR で特定した mod hot path | renderer 競合 mod との共存は追わない。HUD cache 部分は別 license。20 FPS の HUD 更新を標準化しない [R20] |
| Nothirium | `0.4.9-beta` / `eed4511` | GL 版の dynamic VBO・multidraw・upload 管理を設計比較に使う | Celeritas の CPU mesher と重複させない。確認できた tree に明確な license がなく、コード取り込みは許可済みと扱わない [R21] |
| UniversalTweaks | `3239ded` の公開 source | item color / model helper など、profile で hit した計算の cache と mod 固有 invalidation | server tick・AI・ゲーム挙動改変まで FPS 名目で取り込まない [R22] |
| Celeritas Extra / Leaf Culling | 公開機能と license を確認 | profiler / settings 表示、見えない opaque face の証明 | animation disable、距離減少、hollow leaves、見える face の間引きは性能改善に数えない [R23–R24] |

### 3.3 Minecraft 1.7.10: Angelica を中心に

| 参照 | 基準点 | 採用候補 | 注意点 |
| --- | --- | --- | --- |
| Angelica | **2.2.31 / `23aa043`**。PR #1993 は 2026-08-08 merge。SDL GPU の Vulkan / Metal / D3D12、TESR API、batch / instancing の公開実装を確認 | per-frame upload arena、bounded item geometry cache、plain instancing、TESR mesh key + invalidation、部分 model batch、pipeline / constant buffer reuse | 主体の FFP / GL state emulation を移植しない。Celtium は Minecraft の native pass を所有する。SDL backend 採用の計画ではない [R25–R29] |
| FalseTweaks | `4.4.5` / `f9e72dd` | texture packing、pure CPU mipmap 処理、triangulation と lighting / AO 契約の確認、beam などの反復 geometry | voxelized item / 3D rail など画を変える機能は対象外。既存 quad→triangle 変換を壊さない [R30] |
| Neodymium | `0.2.5` / `4c2e018` | bounded mesh / VBO 再利用、CPU draw submission の削減 | chunk meshing 自体を改善する mod と誤認しない。VRAM / RAM と rebuild throughput の交換条件を測る [R31] |
| ArchaicFix | `0.8.0` / `b436d50` | cache lifetime、world change の解放、resource memory の抑制 | occlusion module は root LGPL の対象外と明示されている。そこを LGPL として流用しない。world lighting / tick engine は本計画外 [R32] |
| Hodgepodge | `2.7.216` / `4668d63` | mod renderer の memoization、texture / world leak の修復例、調査計器 | particle 上限変更や chat 背景除去などを同画質の最適化と数えない [R33] |

Angelica の `TesrMeshProvider` / `TesrMeshCache` / `ModelPartBatch` は、entity / TESR を個別に書き換えるより、**geometry の所有・dirty・instance transform を公開契約で分ける**方向の参考になる。自動 cache に安全でない renderer は従来の意味を保つ Celtium の native immediate pass で描く。これは GL fallback ではない。

## 4. 優先順と着手ゲート

性能の全項目を、バニラ・全主要 mod の完成まで凍結しない。**変更する pass の局所的な正しさが受入済みなら、allocation / wait / submission の改善を進められる。** 一方、見えていない描画がある場面の FPS を成功指標にしない。大きな synchronization / ownership の変更は、関係する正しさ修復と一つの所有契約にまとめる。

規模は相対評価: S は単一の cache / 計測境界、M は複数 class の整合、L は frame ownership または広い native pass の変更。**所要日数や AI の完了時刻の予測ではない。** 実測の bottleneck が違えば、同じ着手ゲート内の順序を変更する。

| 順 | 作業群 | ID | 主な依存 | 規模 / リスク | 現在の受入 |
| --- | --- | --- | --- | --- | --- |
| 0 | benchmark 固定・instrumentation 費用・競合 scope | OPT-00 / 01 / 02 | 原典、現行計器、画像と compositor 到達 | S–M / 測定の取り違え | 計画のみ |
| 1 | shader data baseline・UBO lifetime | OPT-03 | format / shader ABI / light / fog の受入 | M / 画・128 B 能力 | 計画のみ |
| 2 | frame completion epoch・upload / geometry arena | OPT-04 / 05 / 06 | OPT-00 / 03、失敗原子性と last-use retirement | L / GPU lifetime | 計画のみ |
| 3 | upload / present の非同期化・frames in flight | OPT-07 / 08 | OPT-04–06、FBO / swapchain 所有 | L / 同期・latency | 計画のみ |
| 4 | terrain native multidraw・state reuse・compact 既定評価 | OPT-09 / 10 / 11 | OPT-03 / 05、sort / index source 受入 | M / optional feature・透明順 | 計画のみ |
| 5 | descriptor / sampler / pipeline cache | OPT-12 / 13 | OPT-04 / 06、texture generation | M / reload・失効 handle | 計画のみ |
| 6 | entity / item / TESR / particle / GUI の再利用 | OPT-14–19 | OPT-03–06 / 10 / 12、各 pass の正しさ | L / mod callback・cache key | 計画のみ |
| 6–8 | quad index 再利用と限定した geometry の locality | OPT-32 | OPT-05 / 11 / 14、原典の topology と順序 | S–M / AO・透明・CPU build 費用 | 前半は候補、後半は研究 |
| 7 | texture / model / resource と CPU scheduling | OPT-20–25 | animation / model / event 契約、generation、計測 | M–L / 初回 stall・backlog | 計画のみ |
| 8 | final pass・barrier・GPU bandwidth / shader | OPT-26–28 | post / color / FBO の画の受入、GPU timings | M–L / GPU 固有 | 計画のみ |
| 9 | measured optional parallelism / visibility | OPT-29 / 30 | baseline 完成、両 GPU の CPU / GPU profile | L / 低性能機の逆効果 | 研究候補 |
| 常時 | 主要 mod の性能受入と regression 防止 | OPT-31 | 各関連群の受入 artifact | M / scope の取り違え | 計画のみ |

**最初の実装単位**は、OPT-00/01 で費用を分離し、OPT-03 の shader data 契約を固め、OPT-04/05/07 の「copy と group による割当・同期」を一つの resource 契約として減らすこと。OPT-08 は必要な per-frame 所有が揃ってから、OPT-09 は Celeritas の cached batch が保存されていることを条件に続ける。

同じ巨大 `VulkanTerrainGpuBackend` を複数担当で同時編集する運用は避ける。並列化するなら、frame resource interface、terrain submission、texture upload、model cache、benchmark / docs の所有を先に分ける。細切れの GPU 起動より、整合した実装単位でコンパイル・CPU 契約確認・局所 GPU 受入を行う。

## 5. 実装する最適化の契約

### OPT-00: 性能を、正しい同じ scene で測る

**既存**: `scripts/benchmark_celtium.py`、`scripts/prism_validation.py`、bench fixture、native timing / resource counter を使う。新しい独立 harness を先に作らない。

- 主比較は固定 Celeritas `989ebf2e` と Celtium **GL-less**。改善の A/B は、同じ Celtium の変更前 jar と変更後 jar。古い bridge / `celtium-gl-only` config は主比較に入れない。
- Celeritas との比較は overhaul 全体の差、Celtium A/B は変更の因果を調べる。両者を混ぜない。
- 画像・提出数・完了数・present・camera の実際の描画値が合う fixture だけ採用する。異なる workload、画の欠落、異なる frame cap の run を倍率にしない。
- benchmark artifact の schema と不足 field を先に確認し、必要な項目だけ拡張する。accepted / recorded / submitted / completed / dropped は同一数に丸めない。

**受入**: 第 8 節の再現条件と第 9 節の判定を満たす対比較が保存される。新しいゲートは名前と測定対象を文書化する。古い文書の FPS、提示周期、cold run は今回の baseline に使わない。

### OPT-01: 調査計器自身の hot-path 費用を減らす

**既存**: backend の `threadCpuNanos`、draw 周囲の wall time、format / resource census、GPU query。

- `ThreadMXBean` の取得を cache し、詳細 CPU 計測・文字列構築・dump を opt-in profile または sampling にする。性能 run の既定は軽量 counter と非同期に回収する GPU timestamp。
- sampled 値に全 draw の総時間という名前を付けない。sample 数・率・測定区間を出す。正しさの重要な失敗・拒否・dropped は profile off でも消さない。
- JFR / allocation profile は短い別 run。performance run の profiler off / on を同一 jar・scene で比べて、計器費用を分離する。

**受入**: profiler off で画像と verdict が不変。Java/native allocation と render-thread CPU の差を示す。時刻の欠測を 0 ms とせず、GPU query を待つための stall を増やさない。

### OPT-02: 競合最適化 mod 向けの分岐を整理する

- 他 renderer を選ぶ互換経路、競合 renderer 向けの per-draw 判定、不要になった adapter を、callsite と runtime coverage を確認して整理する。
- Cleanroom の正式 Mixin / Accessor・直接 LWJGL3 を優先し、Java 21 の `MemoryUtil` / direct buffer / `MemoryStack` の寿命を明示する。JDK 22 の FFM を持ち込まない [R45]。
- Forge の公開 entry / event を消して「速くなった」としない。競合 mod の blacklist を通常 mod の描画拒否リストと同一にしない。

**受入**: renderer 非競合の fixture が同じ画を描く。消した経路と依存理由を説明でき、毎 draw の detection は起動 / reload に移る。通常 Forge / LWJGL2 対応を新しく増やさない。

### OPT-03: frame / material / instance の shader data を分離する

- camera、projection、fog、ambient / directional light、lightmap 等の更新周期を原典と SPIR-V から整理し、共通 frame UBO、material data、instance data に分ける。
- baseline の push constant は **128 B 以下**にする。UBO / dynamic offset の align、range、descriptor layout を device limit に合わせる。大きな push constant が使える GPU でも、基準は同じ UBO 経路。
- model matrix を batch の同一性条件から切り離せる pass では、instance buffer に移す。opaque geometry と順序が必要な translucent / GUI は別契約。
- terrain の既存 dynamic UBO と post の既存 fallback を再利用し、互換性のために照明・fog・alpha を除去しない。

**受入**: reflected shader layout と Java packing を一致させる。128 B 能力の device を起動できる設計、AMD / Intel の同画、UBO growth / reload / failure の所有検査。per-draw push bytes、UBO bytes、bind 数を比較する [R35]。

### OPT-04: GPU completion を資源解放の唯一の根拠にする

- submission ごとの単調な completion epoch と resource の last-use を管理する。baseline は fence で成立させ、timeline semaphore は採用を必須にしない。
- buffer / image / view / sampler / descriptor / pipeline / arena slice は最後の利用が完了するまで再利用・破棄しない。frame 番号の進行だけを completion としない。
- `VulkanRetireQueue.flush()`、replacement、reload、swapchain resize、timeout / device lost の前提を揃える。build-then-publish と失敗原子性を維持し、未完了資源を「エラーが出たから」解放しない。
- 単一 frame 時代に正しかった即時破棄を、frames in flight 導入後も正しいと仮定しない。

**受入**: 成功・途中失敗・world switch・reload・resize・timeout の契約を確認する。validation clean だけでなく、created / retired / live / pending と completed epoch の整合を示す。leak と use-after-free を隠す無期限の保留を作らない。

### OPT-05: immediate vertex / index と upload を frame arena にする

- `VulkanDynamicMesh` の group ごとの個別 `VkBuffer/VkDeviceMemory/map` を、bounded な persistent-mapped arena の slice に置き換える。static model geometry は別の長寿命 arena に置く。
- group は format、offset、count、material、instance の packet を所有し、専用 allocation を所有しない。vertex alignment、index type、16/32-bit index の範囲、overflow を事前検査する。
- ring の再利用は OPT-04 の completion に従う。余裕のない GPU では無制限の arena 増設でなく、上限内の growth / backpressure と完了待ちを使う。
- host coherent が使えない memory type も、`nonCoherentAtomSize` に沿う flush で成立させる。`MemoryStack` 上の参照を frame 越しに保存しない。

**受入**: warm-up 後の同じ scene で group 数に比例した `vkAllocateMemory` が発生しない。allocation count、requested / reserved bytes、peak / retained bytes、render-thread CPU、p99 を比較する。group をまとめるために描画を落とさない [R34 / R42–R43]。

### OPT-06: memory class ごとの suballocation と budget

- allocator は transient host upload、persistent static geometry、image、readback/debug を分ける。弱い GPU 向けの小さい初期値、coarse growth、縮小 / eviction、reload reclamation を設計する。
- discrete GPU の static vertex / index は device-local + batched staging、UMA では host-visible device-local の直接利用を候補にする。同じメモリ flag と copy 方針を全 device に固定しない。
- VMA は MIT の候補であり、必須ではない。現行 LWJGL / native 配布物に binding があるか、依存増加、例外経路、block size、budget query の availability を確認してから、自前の限定 suballocator と比較する。
- `VK_EXT_memory_budget` がなければ unknown と明記し、heap 容量を安全に使い切れる容量と誤認しない。RX 6400 の小さい VRAM と UHD 630 の共有メモリの双方で上限を評価する。

**受入**: steady state と reload / world switch で memory が収束する。heap pressure 下で同じ画を維持し、失敗を明示する。CPU copy、upload、GPU fetch の合計が速くなる placement を採る。host-visible = 常に遅い、device-local = 常に速いとは判定しない [R42–R43]。

### OPT-07: texture / mesh copy を batch し、毎 copy の CPU wait をなくす

- `VulkanContext.endCommands()` 型の one-shot submit / fence wait を、frame の upload command recording と completion 契約に統合する。初期 resource build などの限定 blocking helper は区別して残す。
- `uploadPatchedLevels` の producer mip data・領域を保ち、複数 sprite / lightmap / dynamic texture を staging slice に置いてまとめて copy。copy 前後の依存は consumer pass まで正しく付ける。
- pending command が参照する source slice、更新対象 image、旧 sampler / descriptor を先に再利用しない。texture update と同 frame の draw 順を event 単位で保証する。
- 最初は同じ graphics queue の batch で十分。専用 transfer queue は ownership transfer 費用を含めて測れるまで作らない。

**受入**: steady animation / chunk upload で copy ごとの host fence wait が消える。wait count / time、submit 数、upload bytes、GPU gap、visible chunk completion、p95 / p99 を比較する。queue idle を fence に置き換えただけの同期化を成功にしない [R37 / R39]。

> **2026-10-12 の失敗と、先に書くべきだった契約（実測）**: `VulkanContext.uploadToBuffer` を「staging ring に記録して
> `flushUploads()` まで pending にする」形で実装したところ、**全 leg で device lost** した（`VUID-vkCmdCopyBuffer-dstBuffer-parameter`、
> `vkDeviceWaitIdle returned -4`、`vkQueueSubmit -4` out of `flushUploads`/`reserveUploadSlot`、SIGSEGV in `libvulkan_radeon.so`）。
> 理由は同期ではなく**destination の寿命**で、この節の 3 番目の項目そのもの: pending な copy は `dstBuffer` の **handle を記録時点で
> 固定**するのに、呼び出し側は「upload から戻った時点で copy は片付いている」前提で destination を差し替える
> （`VulkanArena.write` の直後の arena growth = `VulkanBuffer.ensureCapacity` が旧 VkBuffer を破棄、world teardown は arena ごと破棄）。
> よって **batch 化の前提条件は次のいずれか**であり、これが無い限り per-copy settle（1 copy = 1 submit + fence wait）を維持する:
> (a) destination 側の last-use を batch の完了 epoch で gate する（`VulkanBuffer.delete/ensureCapacity` と arena の segment 再利用を含む）、
> または (b) copy の destination handle を **flush 時に解決**する（記録時に固定しない）＋ arena の segment 再利用 gate。

> **2026-10-12 再挑戦（契約 (b) で受入）**: destination を `VulkanBuffer` 参照として保持し `flushUploads()` 時に解決する形にした。
> これで stale handle の class 自体が消える（growth は透過・解放済み destination は skip して計数）。加えて `ensureCapacity` は
> copy-out の前に flush する。arena の segment 再利用は gate 不要（copy は CPU 書込み順・batch は同一 queue に順次 submit）。
> 受入（layers ON の同形 leg）: VUID 0行・`-4` 0件・`validationMessageCount=0`・`validationClean=true`・exit 0。
> 効果: submit 数 **31343 → 6910（−78%）**（cold 3889→1309・warm 7969→579）。**待ち時間の合計は不変**（972→958 ms）＝
> batch 全体の完了を待つため。真に待ちを消すのは semaphore で frame submission に渡す形で、OPT-08 の領域。詳細は `docs/plan.md` §0.225。

### OPT-08: 2 frames in flight と非同期 present

- 各 frame slot に command pool / command buffer、uniform / instance / geometry slice、query range、必要な descriptor と attachment を所有させる。
- acquire semaphore は frame slot、present が消費する render-finished semaphore は swapchain image の再利用条件に合わせる。swapchain image 再取得と fence の signal を同一に扱わない。
- `submitWithSemaphores` の present 前 CPU wait を外すのは所有分離後。CPU が次 frame を準備でき、GPU の未完了 slot だけを待つ設計にする。原則 2 slots、low-latency の 1 slot も同じ画で扱う。
- offscreen main / post target を未完了 frame と共有して上書きしない。resize、minimize、OUT_OF_DATE / SUBOPTIMAL、shutdown、device lost を completion に沿って処理する。

**受入**: CPU / GPU timeline が重なり、p50 と p99 が改善または非回帰。追加 memory と input-to-present latency を併記する。GPU が遅い場面で queue を長くし、FPS だけ上げる設計は採らない [R37–R38]。

### OPT-09: Celeritas batch を native multidraw に接続する

- 現行 `BatchAssembler` の cache / culling / sorted order を producer にし、`VulkanMultiDrawBatch.upload()` と consumer を実装する。batch を CPU で毎 frame 再構築する代償を測る。
- baseline は同じ assembled list からの direct indexed draws。任意 feature として `multiDrawIndirect` / `VK_EXT_multi_draw` を query **かつ enable** し、同じ ordered packet を提出する。
- `firstIndex` は index 単位、buffer offset は byte 単位、`vertexOffset` は符号付き vertex 単位。sorted 専用 index source と unsorted の source を混ぜず、offset 0 を有効値として扱う。
- `drawIndirectFirstInstance` がなければ `firstInstance=0` で成立させる。`maxDrawIndirectCount` / extension の draw count limit を守って分割する。region transform / material が異なる draw の shader data も明示する。
- `multiDrawIndirect=false` の device では間接 1 draw ループより direct が速ければ direct を選ぶ。mesh shader、bindless、buffer device address を前提にしない。

**受入**: submitted geometry と透明順が同一。CPU draw API 数、record time、indirect upload bytes、GPU terrain time を比較する。両 GPU、遠景、多数小 section、透明 block、view change で測る。API call が減っただけでは受入にしない [R36 / R44]。

### OPT-10: command state cache と合法な隣接 merge

- pipeline / descriptor / vertex / index / viewport / scissor の最後の bind を command recording 単位で cache。command buffer reset、pass / subpass、target / layout 変更の境界で invalidate する。
- immediate group は state を draw 時に取得するのでなく、producer 時点の意味を snapshot する。GUI scissor、viewport、blend、depth、color mask、texture environment を正しく key / packet に保持する。
- opaque のみ合法な範囲で state 順にまとめる。translucent、glint、hurt overlay、GUI の paint 順、Forge event 境界は順序を保ち隣接 merge にする。
- 直近の scissor / full viewport 復元を「redundant bind」と誤って消さない。一般的な GL state cache の再発明はしない。

**受入**: state transition fixture が基準と同画。issued / elided binds、group / draw 数、CPU time を出す。省略した command が境界後に必要なら契約検査が失敗すること。

### OPT-11: 既存 24 B compact vertex を正しく活用する

- 32 B vanilla-like と 24 B compact を同一 scene / material で比較する。effective option と shader layout を artifact に保存する。lab の `forceCompactFormat` を恒久的な正当性にしない。
- quantization、UV、normal / AO / light / tint、region offset、mod の quad data、large coordinate を検証してから既定値を決める。
- 同じ頂点数なら vertex data の **bytes は 25% 減る**。これは算術値であり FPS / GPU 時間の改善率ではない。index、texture、fragment、copy の費用は別に残る。
- さらなる field 削減は consumer の実利用を確かめてから。16-bit shader arithmetic や特殊 vertex format を必須にせず、core の packing / unpacking で成立させる。

**受入**: vertex / upload / reserved bytes と GPU / CPU time を測る。resource pack、液体、AO、mod model、両 GPU の画質が成立する。欠落 lighting のまま compact を速い既定にしない。

### OPT-12: descriptor / sampler cache の寿命と上限を改善する

- 既存 texture generation key を活かし、frame / material layout を共通化。必要な descriptor だけ更新し、無関係な draw の update / allocation をしない。
- 旧 generation は last-use 完了後に retire。pool を容量だけ増やして reload leak を隠さない。per-frame の resettable pool と長寿命 material cache を分ける。
- sampler は min/mag filter、mipmap、maxLod、wrap、対応する anisotropy capability の canonical key で共有する。sampler だけ変わった場合も descriptor の意味を更新する。
- update-after-bind / descriptor indexing は baseline に要求しない。未完了 descriptor を書き換えず、generation replacement を使う。

**受入**: descriptor updates / allocations、pool / sampler 数、cold / warm CPU time と reload 後の convergence を比較する。animated / dynamic texture、font、lightmap、atlas、item の stale descriptor がない [R34 / R44]。

### OPT-13: driver pipeline cache と小さい warm-up

- pipeline object map に加えて `VkPipelineCache` を使用する。key は device / driver の cache UUID、shader hash、layout ABI、render target / format と整合させる。
- shaderless の実際に使う少数の variant を loading / world-enter 中に予熱する。全 blend / format / target の直積を作って RAM / 起動時間を悪化させない。
- 初回 create、cache hit / miss、variant 数、compile stall を測る。cache file の破損・旧 driver・旧 shader は安全に再作成する。
- viewport / scissor のような dynamic state を pipeline variant にせず、必要な immutable state だけ key にする。

**受入**: cold と warm の両 run、first item / entity / GUI、reload / dimension / post の p99 stall を比較する。steady FPS に効果がない改善は起動 / hitch 改善として報告する [R40]。

### OPT-14: native model / item geometry cache を広げる

- 既存 `modelMeshes`、resource generation、geometry capture を再利用し、held / dropped / GUI item の **geometry** を共有する。transform、light、tint、hurt / glint、stack / NBT による見た目は instance / material 側に分ける。
- `IBakedModel#getQuads`、override、random seed、world / entity / NBT の依存を原典で分類する。dynamic model を static と決めつけない。unknown は native 即時提出のままにする。
- cache は entry 数と bytes の両方で bounded。reload / model bake / atlas change / world dependency に invalidate し、旧 mesh は completion 後に retire。

**受入**: cache hit / miss / rebuild、geometry bytes、Java/native allocation、CPU time を出す。JEI の全 item、NBT variant、damage / glint、持ち方、drop の大きさ、resource reload を比較する [R18 / R26]。

### OPT-15: 通常 instancing で同じ geometry をまとめる

- repeated dropped item、item frame、共通 entity model parts、対応 TESR の geometry + material に対し、通常の vertex / instance buffer と indexed instanced draw を使う。
- per-instance の matrix、light、tint、overlay を保持する。透明 / glint 等の必要な順序は維持し、bind limit / instance count / stream capacity で分割する。
- shader の `draw ID`、descriptor indexing、mesh shader がなくても動く経路を先に作る。少数 instance で staging / packing の方が高ければ既存 draw に戻す **native 内の選択**にする。

**受入**: 重複 geometry に対する transform / upload / draw CPU を減らし、GPU time と bytes が非回帰。少数 entity と多数 entity の双方、AMD / Intel で測る。entity tick、animation、hurt / visibility を省略しない。

### OPT-16: TESR の geometry / state を一般化する

- static body、moving part、毎 frame 再生成の geometry を区別する。cache key、dirty / invalidation、resource generation、per-instance transform の契約を小さい Celtium API / 正式 entry にする。
- 通常 Forge TESR は native immediate capture で意味を保つ。opt-in provider を実装できる mod / 内蔵 adapter のみ強い cache を使う。毎 mod を専用 renderer に全面書換えしない。
- world data が変わる TESR を、NBT key や block state だけで永久 cache しない。FastTESR の buffer、通常 TESR の custom texture / blend / pass、mod の render bounds を維持する。

**受入**: chest lid、sign、beacon、banner / skull、mod machine / cable / fluid / inventory 表示の dirty 変化、unload / reload を検証。hit / miss だけでなく stale geometry がない画と p95 / p99 を示す [R27]。

### OPT-17: particle の共通処理・buffer reuse

- 共通 camera billboard basis と interpolation を frame 単位にし、既存 particle pass の buffer / shared quad index を再利用する。
- material / texture / blend / depth と透明順の契約に従って batch。custom renderer は同じ contract で扱える場合だけ統合し、分類不能なら native 即時経路で意味を保つ。
- light lookup cache は位置・world lighting generation・必要な partial tick を含め、単なる entity position の key にしない。
- spawn 数、tick、lifetime、画面内の particle 数を減らさない。画面外 culling は conservative bounds と再表示の確認を条件にする。

**受入**: 同じ生成 sequence、shaderless fire / smoke / water / potion / breaking / weather / mod effect で同じ画。CPU geometry / lighting、upload bytes、GPU blending / overdraw と p99 を比較する [R05 / R11]。

### OPT-18: GUI / font / sign text / map を native cache する

- 既存 font batch の format / glyph / unicode 契約を保ち、隣接 material batch と shared quad index を使う。text layout / geometry を文字列・font/resource generation・style・scale の key で bounded cache。
- static panel / button / slot の geometry と dynamic text / tooltip / cursor / animation を分ける。Forge overlay event と paint 順・scissor を維持する。
- sign text の dirty cache、map pixel dirty region / atlas は独立に測る。巨大な map atlas を全 device に固定せず、limit と byte budget で page を分ける。
- HUD を 20 Hz に落とす、動画的な文字や bar を止める、event を frame ごとに発火させない変更は採らない。保持するのは geometry / layout であって callback の省略ではない。

**受入**: chat / unicode / F3 / hotbar / potion / boss bar / loading / video settings / JEI の scroll・tooltip・animation、map と sign の更新が成立。CPU / allocation / draw 数、冷えた cache と warm cache の stall を比較する [R05 / R20]。

### OPT-19: 見える entity / TESR の CPU 費用を保守的に抑える

- Celeritas の existing visibility を first level に使い、mod の render bounding box、glow / name tag / beacon などの例外を明示する。
- 非同期結果は world / resource / camera epoch を持ち、stale または未知は **visible** に倒す。fast turn / teleport / large animation の bounds を inflate し、短い hysteresis を評価する。
- renderer callback 以外の game tick は止めない。render callback に他の意味を持たせている mod は opt-out できるようにする。

**受入**: 室内 / 壁の裏の負荷減と、カメラ回転・移動・透過 block・large bounds で pop-in がない画を併せて確認。CPU の visibility 計算費用を含めた正味改善を測る [R07–R09]。

### OPT-20: sprite visibility と animation 時間を保ち upload を減らす

- Celeritas の既存 active marking を監査し、native model cache / instancing が UV getter や item marking を迂回する場合も使用 sprite を producer が明示する。
- terrain だけでなく entity、TESR、held / dropped / GUI item、fire、portal、glint、mod effect、dynamic map / font を consumer に含める。
- visibility が不明なら更新する。hidden sprite の upload を省く場合は、論理 clock と再表示時の frame / interpolation を原典の意味に合わせる。custom `TextureAtlasSprite` / `ITickableTextureObject` の side effect を勝手に止めない。
- 既存 filtering が止めている animation 時間も確認し、「Celeritas にあるから正しい」で済ませない。時計の修復と upload 抑制は同じ producer / consumer 契約で扱う。

**受入**: しばらく非表示にしてから再表示した animation を参照と比較。upload regions / bytes / submits、CPU animation / interpolation、正味 frame time を測る。fire が静止するような取りこぼしを失敗にする [R17 / R19]。

### OPT-21: lightmap / sky の実際の dependency を cache する

- 1.12.2 lightmap の dirty flag を尊重し、同じ pixels / content version の redundant copy だけを省くところから始める。torch flicker の変化を cache hit にしない。
- sky / fog は dimension、world time、weather、camera / biome、night vision / blindness、mod event の依存を原典で特定する。恒常値の計算や変化しない部分だけ再利用する。
- 全 lightmap buffer の hash 計算が upload より安いか測る。確実な producer generation がある箇所はそれを使い、不確かな heuristic にしない。

**受入**: day / night / weather / Nether / dimension、potion、gamma、torch / lightning / boss で同じ画。update / dedup / bytes と CPU 費用を比較。原典に既にある dirty check を新しい効果と数えない [R10]。

### OPT-22: texture decode / mipmap / atlas の仕事を整理する

- PNG decode と純粋な mipmap 計算は bounded worker pool へ分離候補。Forge custom loader / texture stitch event は thread-safety の契約がないまま worker に移さない。
- packing は最大 texture size、mip alignment、sprite rotation / UV と border に整合させ、atlas 面積 / page 数 / wasted bytes を減らす。
- decode / mip data の中間 copy、int array ↔ byte buffer の変換回数を測る。hash の dedup と CPU copy がむしろ高い小 texture は直送のままにする。
- stitcher cache の key は resource content / pack order / mip settings / algorithm version。失効条件のない disk cache にしない。

**受入**: startup / reload time、peak heap/native/VRAM、first-use p99 と texture 画質を比較。起動だけ短くしてプレイ中に decode を移す改善は採用理由を分ける [R18–R20 / R30]。

### OPT-23: model / quad / state の安全な dedup

- immutable で等しい quad vertex data、shape / state、model metadata の重複を profile して共有する。mutable array / buffer を同じ object にして mod の書換えが他 model に伝播しないようにする。
- hash + exact equality、resource generation、retained bytes / entry limit を持つ。dedup の CPU / GC 費用も測る。
- lazy model loading は第一目標にしない。よく使う inventory / JEI / world material を bounded に warm-up し、rare model の miss も p99 に含める。

**受入**: large modpack の live heap / native geometry bytes、GC pause、first-use stall、model override / reload / random variation を比較。loader event 順と thread affinity を保存する [R18–R20]。

### OPT-24: 既存 task budget を upload bytes / age / fairness で補う

- `ChunkBuilder` の既存 EMA / weighted budget を維持し、待機 age、距離 / visibility、新規 build / rebuild / translucent sort、推定 upload bytes を不足分として追加する。
- frame ごとの upload byte / time budget を geometry arena に接続し、巨大な一件や mod model の重い build で starvation しない。queue 数が少ないだけで消化が良いと判断しない。
- completed visible chunks、oldest age、visible hole の duration、cancellation、CPU worker utilization、pending bytes を出す。
- sort task を遅らせて透明順を壊す、完成 section を出さず FPS を上げる、見えている chunk を永久に低優先度にする動作は不可。

**受入**: walk / fly / block edit / world enter / reload / teleport で time-to-correct-visible-mesh と p99 が両立。静止 scene の FPS だけで budget を決めない [R03 / R17]。

### OPT-25: 既存 async visibility / raster の有効化を評価する

- default `ONLY_SHADOW` と `EVERYTHING`、既存 raster off / on を分離した同 jar 比較で、通常 shaderless pass の費用を測る。
- async の camera / lattice / region / world generation、結果 publish、teleport 時の同期再計算を確認する。古い結果で見える section を取り落とさない。
- GPU culling を新設する前に、CPU list 作成、waiting / joining、visible ratio、raster 費用を測る。安い indoor scene と広い open scene で有効性を分ける。

**受入**: effective option が run に記録され、frustum / section / transparent draw の画像が一致。CPU の空き時間と完了までの latency を含めて評価。既存の async コードを書き直して「移植完了」としない。

### OPT-26: native FBO / post / final present の pass を詰める

- vanilla ShaderGroup、outline、spectator、GUI の target を意味のある pass graph にし、実際に必要な read / write / clear / preserve を特定する。
- 読まれない attachment の store、不要 clear、重複 FBO transition、全画面 copy を測って削る。最終 composite を swapchain に直接出す候補は color space / alpha / orientation / post chain が同じ場合だけ。
- capture / debug readback は性能 run で無効にするが、機能の受入 run と同じ jar に戻せるようにする。画の証拠を取りにくくする設計で速度を得ない。

**受入**: pass / transition / copied pixels / bytes、GPU post / composite / present time を比較。outline / spectator / resize / UI と compositor 到達が成立。native blit の消失だけを目標にしない。

### OPT-27: barrier を producer / consumer に絞る

- upload→vertex / index / uniform、texture→fragment、color→post sample、depth→必要な consumer をそれぞれ記録し、stage / access / layout を最小の正しい依存にする。
- `ALL_COMMANDS` や広すぎる read/write barrier の費用を GPU trace で確かめる。memory hazard がないと証明した境界だけ省略する。
- frame overlap と texture 更新の ownership に従う。barrier を減らして CPU counter だけ良くする、validation off だけで成立させる変更は不可。

**受入**: synchronization validation / failure paths と両 GPU の画像。GPU gap / pass timings が改善し、まれな camera / reload / resize で stale data がない [R41]。

### OPT-28: fragment / bandwidth を、画を保って減らす

- alpha discard、opaque と cutout、depth test / write、fog、light / color、texture samples と overdraw を native shader / pass 単位で profile する。
- opaque の合理的な前後順や不要な同一 texture sample、constant data の再計算を減らす。追加 depth prepass は geometry 再描画の費用も含めて採否を決める。
- GPU time が支配する高解像度・雲・雨・透明密集・mod effect の scene と、低負荷 scene の双方で測る。
- OIT、高精度 / 多 attachment、render scale、AA 変更、雲・影・fog の省略を baseline の高速化に混ぜない。

**受入**: 同画質で frame GPU time / bandwidth / overdraw の proxy が改善。GPU hardware counter が取得不能なら明記し、timestamp から帯域値を捏造しない [R12–R15]。

### OPT-29: command recording 並列化は残る CPU 費用に限定する

- OPT-05/09/10 後も CPU record が支配する場合だけ、immutable draw packet の recording を分ける。Minecraft / Forge / mod renderer callback は呼出し thread を変えない。
- per-worker / per-frame command pool と descriptor resource を持つ。小さい draw のために secondary command buffer を大量に作らず、pass / work size で粒度を選ぶ。
- worker 数は available CPU と chunk build / integrated server の競合を考慮し、UHD 630 の UMA 帯域と i5-8400 の core を食い尽くさない。virtual thread 化を CPU 計算の高速化として扱わない。

**受入**: render-thread CPU、総 CPU、queue / server tick latency、GPU time、p99 が正味改善。低 draw 数では single-thread recording と同等の費用。profile が示さなければ実装しない [R39]。

### OPT-30: Hi-Z / GPU culling / async compute は後順位の研究

- 既存 CPU culling と submission 最適化後、GPU の余力と十分な workload がある場合だけ検討する。Vulkan の core compute が使えることと、低性能 GPU で速いことを区別する。
- Hi-Z の extra passes / memory、readback / barrier、前 frame camera、disocclusion、large bounds、透明物体、occluder の正しさを全て費用と受入に含める。
- 任意経路でも mesh shader / mandatory bindless は使わない。CPU baseline を遅くする追加 preprocessing を常時入れない。
- 最初は camera jump / stale result で conservative visible。compute / graphics overlap が実際に得られない queue 構成は増やさない。

**受入**: UHD 630 と RX 6400 で positive な workload だけ採用し、baseline scene で非回帰。pop-in / false occlusion と memory pressure を失敗にする。未測定の modern mod の culling 効果を根拠に先行実装しない [R29 / R36]。

### OPT-31: 通常 mod の性能改善も同じ仕組みで受入れる

- JEI の多数 item、large mod model、TConstruct 系 item / override、工業 mod の machine / cable / fluid、custom entity / particle / GUI を代表場面にする。実際の導入可否・版は Cleanroom の instance で固定する。
- 同じ material / geometry の repeated draw は共通 batching / cache / instancing に載せる。mod 名ごとの例外を追加し続けず、契約で足りない意味を一般化する。
- mod callback 自体が CPU の主因なら JFR で callsite と invalidation dependency を特定して小さく改善する。最適化 mod との共存 patch はここに戻さない。

**受入**: 通常 mod の表示・操作・animation・reload が成立し、modpack の p50 / p95 / p99、GC / native memory、world-switch convergence を保存する。vanilla だけ速い版を「互換性と性能改善完了」にしない。

### OPT-32: mesh shader を使わず、index / vertex の locality を改善する

- 既に再利用されている terrain index を維持し、immediate / model の固定 quad index pattern を共有する。QUADS / FAN / STRIP の triangulation、strip parity、winding、AO の対角選択は原典と現行実装に従い、同じものとして一律置換しない。
- 静的 opaque mesh に限り、完全に等しい vertex attributes の dedup、triangle の vertex reuse、最終 index に合わせた vertex fetch locality を候補にする。UV / normal / light / AO / tint / material seam を position だけで merge しない。
- meshoptimizer の一般的な index / vertex cache / fetch 最適化は **mesh shader を要求しない**参考。shared quad に対する追加 index bytes と CPU build 費用が、GPU の節約を上回るなら採らない。JNI / native 依存の追加は前提にせず、限定的な独立実装とも比較する。
- translucent sort の順序、GUI paint、custom material の side effect を持つ geometry には triangle reorder を適用しない。simplification / LOD で geometry を削る案は別の品質設定であり、本計画の同画 baseline には入れない。

**受入**: 同じ attributes / topology / coverage、CPU build / cache miss の時間、vertex / index bytes、GPU vertex / terrain / model time を比較する。ACMR 等の静的推定は device 実測と区別。UHD 630 を含む正味改善がある材料だけ採用する [R46]。

## 6. GPU 能力と弱い GPU の基準

### 6.1 capability matrix

| 能力 / limit | baseline | 追加経路と未対応時 |
| --- | --- | --- |
| Vulkan API | 現行 `VulkanContext` は **1.2** を要求。これを今回の計画の既存最低値として明示する | Vulkan 1.0 / 1.1 までの device は未対応。「全 Vulkan GPU」を検証済みと書かない。最低 API を下げるなら別の scope / capability 監査が必要 |
| window / surface / swapchain | 実際の graphics / present queue、surface format / present mode / extent を query | headless で instance が作れたことを window present 対応としない。未検証 OS / window system は明記 |
| push constants | 保証される **128 B** 内で全本体 pass を設計 | 208 / 216 B の現行依存を解消する。256 B を持つ device だけの renderer にしない |
| texture / color / depth format | 必要な attachment / sample / transfer role を format properties から選ぶ | D24、D32、sRGB、blit filter を名前や vendor で固定しない。画と sampling の意味を保つ代替を選ぶ |
| memory type | 必要 usage と memoryTypeBits で選択。coherent / non-coherent、device-local / UMA を扱う | memory budget extension がなくても bounded allocator。未知 budget を 0 bytes / 無制限としない |
| direct indexed / instanced draw | 通常の graphics pipeline、vertex / index / instance buffer | 特殊 shader draw parameter、mesh shader、vendor extension を要求しない |
| `multiDrawIndirect` | **任意**。false の baseline は ordered direct draw | true かつ enabled、`maxDrawIndirectCount` 内なら grouped indirect draw。false なら複数 draw の間接命令を出さない |
| `drawIndirectFirstInstance` | **任意**。false の indirect packet は firstInstance 0 | instance / region data を別の合法な方法で bind。silent に feature を要求しない |
| `VK_EXT_multi_draw` | **任意** | extension・feature・limit を確認。なければ indirect または direct。extension 使用の追加 bytes と CPU 費用も測る |
| timeline semaphore / synchronization2 | **任意**。frame fence と正しい core barrier で成立 | API 1.2 / extension 名だけで feature が有効と推測しない。ある場合も ownership は同じ |
| descriptor indexing / update-after-bind | **不要** | material cache / bounded pool / per-frame UBO で成立。bindless を画や速度の必須条件にしない |
| wide lines / geometry shader / tessellation | **不要** | バニラが要求する線幅などは必要なら screen-space triangle geometry で保つ。画を細くして弱い GPU 対応としない |
| GPU timestamps | 対象 queue の `timestampValidBits` / period と availability を query | 欠測を 0 GPU ms としない。CPU 計測と表示間隔で GPU time を代用しない |
| mesh shader / ray tracing | **不採用** | 未対応 GPU を遅い二級経路に置く最適化を本計画の主目的にしない |

現在の minimum が 1.2 であることと、1.2 device の optional feature が全部使えることは別である [R44]。feature を query しても、device create で enable していなければ使用できない。

### 6.2 低性能 device で採用を止める条件

- baseline で同じ scene が遅くなる追加 compute、過大な atlas、無制限 cache、深い frame queue は導入しない。
- 高速 discrete GPU で良くても、UHD 630 の shared bandwidth / GPU time / p99 が測定誤差を超えて悪化すれば、global default は不採用。合法な native capability / workload 選択で回避できる場合は、その選択自体を検証する。
- CPU の費用を GPU に移しただけなら、最終 frame time・memory・latency で判断する。弱い GPU のために particle / entity / distance / lighting を黙って減らさない。
- low-memory 時も解像度 / 画質変更は自動の隠れた fallback にしない。品質設定として提供するなら性能受入から分離し、利用者に表示する。

## 7. baseline に採らない案

| 案 | 理由 / 扱い |
| --- | --- |
| mesh shader / Nvidium 型の特別経路を先行 | user scope 外。baseline の draw / transfer / allocator が先 |
| 現行 Sodium source を rename / 翻訳して取り込む | license 境界を消せない。固定 Celeritas と独立実装を基準にする |
| 汎用 GL→VK、Zink 組込み、Angelica の FFP / GLSM を丸ごと移植 | Celtium の native semantic pass の目標と異なる |
| 共存のため旧 renderer / bridge を製品経路に残す | 他レンダリング最適化 mod の共存を捨てる今回の方針に反する |
| HUD 全体を 20 Hz 更新、entity / particle tick skip、particle spawn 間引き | responsiveness / event / animation / ゲーム動作が変わる |
| 葉の hollow 化、雲 / 天候 / fog / glint / lighting の省略、距離減少 | 同じ画の性能改善ではない |
| 全 opaque / translucent / GUI を material 順へ一括 sort | 透明順、overlay、event、GUI paint 順を壊す |
| greedy meshing を全 mod quad に適用 | UV repetition、AO / light / color variation、custom model の同一性を証明できない。将来検討は限定材料の独立研究 |
| depth prepass / OIT / Hi-Z / async compute の無条件既定化 | extra passes、VRAM、bandwidth、同期の費用が弱い GPU で逆効果になり得る |
| あらゆる model loader / Forge callback の off-thread 化 | thread affinity / event 順 / mod side effect を破壊する |
| 描画が欠けている fixture、stub identity、60 Hz の表示周期から倍率算出 | workload・GPU・render cost の証拠にならない |
| budget 延長 / timeout 延長 / 定数変更の反復を性能進捗にする | 正味の費用や根因を減らした証拠がない |
| pipeline 数 / cache size / frames in flight を増やせるだけ増やす | memory pressure、compile hitch、input latency と引換えになる |

## 8. benchmark と性能用 fixture

### 8.1 現行 harness の使い方

`scripts/benchmark_celtium.py` は raw frametimes、p50 / p95 / p99 / max、process CPU / peak RSS、host load、chunk rebuild window、取得できた terrain GPU timing、jar hash を既に扱う。candidate jar を run 前に content-addressed path へ pin する仕組みもある。ここから拡張する。

**重要**: 現行の default config list には bridge がある。config 名を明示して実行する。`celtium-gl-only` は model draw を無効化する経路があるため、固定 Celeritas の代用にしない。

設定を確認するための dry-run 例。これは今回実行した benchmark ではない:

```bash
python3 scripts/benchmark_celtium.py \
  --configs celtium-glless,reference-celeritas-989ebf2e \
  --baseline reference-celeritas-989ebf2e \
  --rounds 3 --frames 6000 \
  --warmup-ticks 600 --warmup-frames 300 \
  --render-distance 8 --framelimit 260 \
  --dry-run
```

各 device の `--render-node` / `--vulkan-library` と Java / instance は実在するパスを調べて指定する。1.12.2 原典では `isFramerateLimitBelowMax()` が false なら `Display.sync` を呼ばないが、menu の frame limit、Cleanroom patch、native present の pacing は別に確認する。例の最大 frame limit 設定、vsync、actual present mode が比較条件を制限していないことを調べ、設定値だけで uncapped と判定しない。

現行 script の default world layers は glass を含む superflat fixture。**これだけで普通の地形の性能を代表させない**。既存 `--world-layers` / `--camera` と fixture を拡張し、通常 world と下表の named scene を明示して扱う。現時点で `--scene` / `--resolution` という benchmark CLI があるとは記載しない。window size は既存の instance / validation 設定を確認し、不足分は harness の新機能として記録する。

### 8.2 必須 scene

| Scene ID | 場面 / workload | 主に検証するもの |
| --- | --- | --- |
| B01 | 通常 terrain、opaque / cutout、静止、distance 8 / 16 | native submission、24 / 32 B、GPU vertex / fragment |
| B02 | walk / fly と連続 chunk visibility 変化 | culling、list latency、upload throughput、worker budget |
| B03 | 多数 block edit と近距離 rebuild、透明 sort | task fairness、GPU upload、time-to-visible、p99 |
| B04 | all-block fixture、glass / water / foliage、同じ camera | material / texture / AO / light / opacity、透明順 |
| B05 | 多数 vanilla entities と animation / hurt / equipment | model cache、batch / instancing、overlay、bounds |
| B06 | 同じ / 異なる / NBT variant の dropped / held / GUI items | geometry sharing、instance、override / glint、first-use stall |
| B07 | vanilla TESR と representative mod machine / cable / fluid | dirty geometry、dynamic transform、reload、CPU callback |
| B08 | JEI、多数 slot、scroll、tooltip、unicode / chat / F3 | font / GUI / item、scissor、paint order、allocations |
| B09 | maps / signs / repeated text、値が変わる表示 | dirty region、atlas / layout cache、updates |
| B10 | particle / fire / smoke / potion / rain / snow | billboard、light lookup、blend、upload、fragment |
| B11 | animated textures、hidden→visible、portal / mod fire | sprite clock、visibility coverage、staging |
| B12 | day / night / storm、Nether、sky / fog / cloud | dependency cache、GPU bandwidth、format / pass |
| B13 | outline / spectator / vanilla ShaderGroup、GUI と同時 | FBO / post / final composite / present |
| B14 | world switch、dimension、F3+T / resource pack reload | memory convergence、cache invalidation、compile / upload hitch |
| B15 | minimize / resize / reacquire、低 memory、失敗復旧 | bounded arena、in-flight retirement、latency、correctness |
| B16 | 主要 gameplay modpack、実際の play 操作と移動 | workload の偏り、GC、memory、通常 mod 互換性 |

固定した shaderless 設定で、1280×720 と 1920×1080 を基本にする。resolution / UI scale / FOV / distance / animation / fancy cloud / mipmap / resource pack を A/B で一致させる。static baseline と moving / animated workload を分け、F3 や profiler 表示は通常 play と別場面として測る。

個々の変更は関連 scene の局所受入を先に行う。全表を毎編集後に起動し直す必要はない。まとまった stage と release 候補では B01–B16 の必要な scope を両 GPU で確認する。未導入 mod の scene は未検証のまま残す。

### 8.3 測定条件

1. **hardware / software**: actual native device、vendor / device ID、driver / Vulkan API、enabled feature、memory types / heap、queue / surface / present mode、DRM node、Cleanroom、LWJGL、JDK、OS / compositor を記録する。`StubGL` の表示を native driver 情報に使わない。
2. **source / artifact**: HEAD、dirty patch hash、jar と SPIR-V hash、fixture hash、mod/config/resource pack、effective option を pin。run 中の rebuild / jar 差替えは不可。
3. **world / draw**: seed、world layers / scene variant、dimension、time / weather、camera と **renderer が描いた pose**、animation / capture tick を固定する。RenderTick END で報告された player pose と区別する。
4. **warm / cold**: warm-up ticks と warm-up frames の双方を満たし、steady、first-use、startup / reload を別集計。最短時間と frame 数の不足は未測定にする。6000 frames が十分な時間を含むか実経過を確認し、速い scene では増やす。
5. **order / load**: A/B の順序を入れ替えた paired runs を最低 3 組から始め、noise が大きければ追加。現行 harness の round は自動で ABBA になると仮定しない。counterbalanced order は必要なら既存 harness に追加する。
6. **環境の競合**: 一度にゲームは 1 instance。重い build / capture / download / 別ベンチが動く測定は避ける。host load、process CPU、temperature / clock が取得できる場合は記録し、比較不能な run を除外理由付きで残す。
7. **validation の分離**: 画像・VUID・失敗復旧は validation on の correctness run。性能は同じ jar / scene / flags で validation / image dump off にし、診断費用を別記。性能 run の validation off を VUID 0 の証拠にしない。
8. **end-to-end**: CPU frame、GPU pass、acquire / fence / present wait、compositor output の表示間隔を別に測る。scanout / input latency を測れないなら欠測と明記する。

### 8.4 保存する指標

| 指標群 | 最低項目 | 誤読しないもの |
| --- | --- | --- |
| frame | raw samples、p50 / p95 / p99 / max、stutter count / duration、実測区間 / frames | FPS 平均だけ、全 process duration を計測区間扱い |
| CPU | render thread、upload / record / model / culling / UI、process / worker / server tick | wall wait を純 CPU work と合算、host load を process CPU と扱う |
| GPU | terrain / model / UI / texture copy / post / composite の available timestamp | absent / unavailable を 0、present interval を GPU time に変換 |
| sync | queue submit、wait count / ms、acquire、frame credit、pending epochs | queueIdle 0 だけで非同期完成 |
| resource | create / allocate / retire / live / pending、pool / variant / cache 数、requested / reserved / used / peak bytes | Java heap と native / VRAM、capacity と actual allocation の混同 |
| workload | draws / binds / groups、vertices / indices / instances、upload regions / bytes | API call 削減を geometry / GPU work 削減と同一視 |
| progress | offers / accepted / recorded / submitted / completed / dropped、chunk queue / age / visible completion | accepted だけを描画完了、counter 不在を未描画と判定 |
| correctness | image / crop diff、gate verdict / calibration key、VUID、window / compositor evidence | FAILED を既知正常と改名、同じ mean だけで同画と扱う |

GPU query と計器の stride / layout / unit / capture stage を先に点検する。計器が原因を名指しした場合は、測定の artefact がないことを確認してから実装に進む。

## 9. 性能変更の受入

### 9.1 採用の判定

- **画像と機能**: performance-only A/B の static scene は原則同一 pixels。layout / 算術を変更して差が出る場合は、契約と原因を限定して本体の画像受入を取り直す。mean / histogram / 成功 counter だけで差を無視しない。アニメーション差は同じ capture tick / sequence で分離する。
- **速度**: 同一条件の paired run を単位に、frame p50 / p95 / p99 と対象 CPU / GPU stage を比較する。run のばらつきと改善差を示し、必要なら block bootstrap 等で時間相関を保った confidence interval を算出する。各 frame を独立 sample と誤認しない。
- **非回帰**: 初期の調査用 guard は、未変更 scene の p50 3%、p95 / p99 5% を超える悪化を要調査とする。これは保証値でも許容する画質劣化でもない。小さい時間では絶対差・noise と照合し、両 GPU の悪化を「平均では得」で相殺しない。
- **memory / latency / backlog**: peak / retained memory、pending uploads、visible chunk completion、input / present latency が悪化する場合は交換条件を明示する。上限・native workload 選択で防げない baseline 回帰は採用しない。
- **説明**: expected invariant、変えた producer / consumer / owner、減った費用、残った費用、artifact、未検証範囲を一つの変更単位で記録する。

改善の目標は「全場面 2 倍」「Vulkan なので 4 倍」ではない。まず warm-up 後の per-group native allocation を消す、copy ごとの host wait を消す、assembled terrain batch を native に活かす、同じ geometry の再生成を減らすという検証可能な契約を閉じ、その結果の frame time を測る。

### 9.2 状態の表記

| 状態 | 意味 |
| --- | --- |
| 計画 / 研究 | 変更の候補。効果は未測定 |
| 実装済み | コードがある。run・性能・両 GPU を保証しない |
| 局所受入 | 指定 jar / scene / GPU で機能・計測が成立。scope 外へ拡張しない |
| 性能受入 | 正しい workload、paired measurements、非回帰、memory / latency 条件を満たす |
| 配布受入 | 必要な主要 mod・両 GPU・docs / license / configuration まで一致 |
| 保留 / 不採用 | 効果なし、回帰、能力 / license / correctness の未解決。理由と証拠を残す |

FAILED / UNCALIBRATED / 欠測 / timeout / 未完了はこの表の受入状態へ変換しない。同じ仮説の budget 延長や文書の言換えだけを round として反復しない。

## 10. 導入、license、開発の区切り

### 10.1 code adoption ledger

| Source | 確認した境界 | 方針 |
| --- | --- | --- |
| 固定 Celeritas / 旧 FOSS 系譜 | 固定 commit と既存 notices を基準にする | 保持した実装と改変を追跡。Sodium の現行版と混同しない |
| 現行 Sodium | PolyForm Shield 1.0.0 | コピー・機械翻訳・改名・cherry-pick しない。一般アルゴリズム / Khronos 仕様から独立設計の根拠を残す。書き直しだけで許諾問題が消えたと扱わない |
| ImmediatelyFast / Angelica / EBE | root LGPL 3 系と各 file の notices | ファイルと第三者由来部分を確認してから、必要な許諾・notice・source 提供条件を満たす範囲で限定的に採用する。renderer 丸ごとの併合はしない |
| VintageFix | root LGPL 3、FerriteCore の MIT、他由来部分の別条件 | component ごとに origin / license を保存する |
| FermiumASM | LGPL 2.1 系と第三者 notices | 個別 file と系譜を確認。loader stack を追加しない |
| StellarCore | root LGPL 3。**Patcher 由来 HUD caching は CC-BY-NC-SA 4.0** | root license だけで HUD code を採用しない。本計画の native geometry cache は独立した契約から設計 |
| EntityCulling | tr7zw Protective License、商用利益等への制限 | source の取り込みは行わない。保守的 visibility の一般原理を独立に設計 |
| MoreCulling | GPL 3 表示に加え、README は他 mod / client への merge に permission を要求 | そのまま取り込む候補にしない。条件を解決しないまま全体 GPL という理由だけで import しない |
| Nothirium | 確認した tree / source では採用に必要な license 未確認 | 未確認を permissive と解釈しない。code import 対象外 |
| FalseTweaks / ArchaicFix | LGPL 3 系。ただし **CoFHTweaks / Minecraft 1.8 由来 occlusion は別扱い** | occlusion 部分を LGPL として流用しない。FalseTweaks にも同じ例外が記載されている |
| Neodymium / UniversalTweaks / Hodgepodge | LGPL 3 系の公開表示と file notices | source 単位で条件と変更を追跡する |
| VMA / BadOptimizations / Particle Core | MIT 表示を確認 | origin / copyright / license を残す。MIT でも正しさ・バージョン・依存の評価を省略しない |
| meshoptimizer | MIT 表示と公開 documentation | source / algorithm を使う場合は独立性・origin・license を保存する。simplification の画質変更と index locality の最適化を分ける |
| 最新 Minecraft | release note は設計・挙動の参照 | closed source の持ち込み対象にしない |

取り込む各 file / algorithm の ledger は、URL、固定 commit、license / notice、Celtium の対応 file、採用した内容、既存機能との差、受入 artifact を記録する。出典を後で一括で書く運用にしない。ここに名前を載せたことは取り込み許諾の完了判定ではない。

### 10.2 stage ごとの出口

| Stage | 出口 | 次へ進む条件 |
| --- | --- | --- |
| S0: 測定と baseline | 正しい Celtium A/B と固定 Celeritas 比較を再現できる | device / flags / images / raw data が揃う |
| S1: allocation / synchronization | bounded arena、completion retirement、batch upload、per-copy wait 解消 | 両 GPU の lifetime / failure / images、関連 scene の CPU / GPU / tail 非回帰 |
| S2: terrain / material submission | native multidraw と direct baseline、compact 評価、descriptor / pipeline cache | sort / format / shader ABI が成立、terrain と reload の性能受入 |
| S3: gameplay / UI renderer | shared geometry、instance / TESR 契約、particle、font / GUI / map | vanilla と代表 mod scene の同画・操作・animation・tail 受入 |
| S4: memory / CPU / GPU workload | texture / model memory、task fairness、pass / barrier / shader | scene を減らさない positive な net gain と両 GPU の非回帰 |
| S5: 配布 | 既定設定、実測 report、mod scope、必要能力、license / branding が一致 | 機能の完成条件と performance matrix の未検証が明示される |
| S6: shader pack / 影 mod | 本体 + 主要 mod shaderless 成立後に `shader-plan.md` の計画へ | 本体の性能を shader pack の新しい品質設定と混同しない |

各 stage で full rewrite はしない。暫定経路は runtime の所有と callsite を確認し、同じ契約の新旧を恒久併存させず整理する。関連変更を review 可能な単位で commit / `git push origin main` し、performance 状態と次の未完を `plan.md` へ同期する。goal の objective と最後の作業指示は維持する。

この文書の優先度は初期値である。最新 profile が allocator より mod callback / fragment / cap を主因と示せば、その evidence を保存して順を変える。**FPS を最大化するために、実測に反してこの順を守ることは要求しない。**

## 11. 一次資料と固定 source

### 11.1 調査文献

GitHub release / PR / file、開発元 README、Minecraft 公式、Khronos / GPUOpen / OpenJDK を根拠にした。検索結果の抜粋が古い版を示したものは GitHub の release metadata を優先した。以下は各項目の参照先であり、その mod を同時導入する一覧ではない。

| Ref | 一次資料 | 使った範囲 |
| --- | --- | --- |
| R01 | [Sodium 26.3-0.9.2](https://github.com/CaffeineMC/sodium/releases/tag/mc26.3-0.9.2)、[releases](https://github.com/CaffeineMC/sodium/releases) | stable / alpha、対象 Minecraft 版 |
| R02 | [Sodium PR #3735](https://github.com/CaffeineMC/sodium/pull/3735) | Vulkan / Blaze3D、multi_draw と indirect の設計差 |
| R03 | [Sodium PR #2887](https://github.com/CaffeineMC/sodium/pull/2887) | 非同期 list / adaptive job scheduling。固定 Celeritas の既存機能と重複を確認 |
| R04 | [Sodium LICENSE](https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md)、[#3307](https://github.com/CaffeineMC/sodium/pull/3307)、[#3297](https://github.com/CaffeineMC/sodium/pull/3297) | 現行 source の境界、未 merge の案を採用済みと扱わない |
| R05 | [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast)、[1.16.0](https://github.com/RaphiMC/ImmediatelyFast/releases/tag/v1.16.0)、[1.17.1](https://github.com/RaphiMC/ImmediatelyFast/releases/tag/v1.17.1) | Vulkan / 26.3 対応、batch / atlas / text cache、license |
| R06 | [IF `MapAtlas`](https://github.com/RaphiMC/ImmediatelyFast/blob/a756fbac991bc8aacc2400347f050392193ae51c/common/src/main/java/net/raphimc/immediatelyfast/feature/map_atlas_generation/MapAtlas.java)、[`SignTextCache`](https://github.com/RaphiMC/ImmediatelyFast/blob/a756fbac991bc8aacc2400347f050392193ae51c/common/src/main/java/net/raphimc/immediatelyfast/feature/sign_text_buffering/SignTextCache.java)、[group reorder mixin](https://github.com/RaphiMC/ImmediatelyFast/blob/a756fbac991bc8aacc2400347f050392193ae51c/common/src/main/java/net/raphimc/immediatelyfast/injection/mixins/enhanced_batching/MixinRenderTypeFeatureRenderer_Group.java) | bounded atlas / reload と、順序処理を無条件移植しない根拠 |
| R07 | [EntityCulling](https://github.com/tr7zw/EntityCulling)、[1.11.3](https://github.com/tr7zw/EntityCulling/releases/tag/1.11.3)、[license](https://github.com/tr7zw/EntityCulling/blob/5269dd55d4fb58899d3846c7b2422868ff54f9ac/LICENSE-EntityCulling) | bounds / whitelist 修正、非同期 visibility、採用制限 |
| R08 | [MoreCulling](https://github.com/FxMorin/MoreCulling)、[1.9.0](https://github.com/FxMorin/MoreCulling/releases/tag/v1.9.0)、[README](https://github.com/FxMorin/MoreCulling/blob/83ec1192726d5ec1332d411166addd9aa7557c5e/README.md) | 26.3、opaque / leaves / frame の範囲と merge 条件 |
| R09 | [Enhanced Block Entities](https://github.com/FoundationGames/EnhancedBlockEntities/tree/b0b202a18a1acbddfc038e5403bde973bece7c98)、[chest renderer](https://github.com/FoundationGames/EnhancedBlockEntities/blob/b0b202a18a1acbddfc038e5403bde973bece7c98/src/main/java/foundationgames/enhancedblockentities/client/render/entity/ChestBlockEntityRendererOverride.java) | baked geometry と dynamic part。default branch 1.21.4 |
| R10 | [BadOptimizations](https://github.com/imthosea/BadOptimizations)、[releases](https://github.com/imthosea/BadOptimizations/releases) | dependency cache、機能の対応版と MIT。26.3 成立は未確認 |
| R11 | [Particle Core](https://github.com/fzzyhmstrs/pc) | particle buffer / light / culling の分類。26.3 成立は未確認 |
| R12 | [Minecraft Java 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) | Vulkan / SDL3、透明処理とその費用 |
| R13 | [Minecraft 26.3 snapshot 5](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-5) | Vulkan shader / explicit input の移行 |
| R14 | [Minecraft 26.4 snapshot 1](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-1) | Vulkan default、OIT 変更、depth format / generated item の修正 |
| R15 | [Minecraft 26.4 snapshot 3](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-3) | Vulkan frame-limit / pacing の修正例 |
| R16 | [VulkanMod](https://github.com/xCollateral/VulkanMod/tree/087ef24e58e5522dec5ccb44f279768786bfb6da)、[0.6.0](https://github.com/xCollateral/VulkanMod/releases/tag/0.6.0) | frame retirement / buffer / VMA。独立 Vulkan renderer |
| R17 | [固定 Celeritas](https://github.com/GTNewHorizons/Celeritas/tree/989ebf2e52ceed052ec112a569e0c1d172095756) | source 比較と FOSS 系譜の基準 |
| R18 | [VintageFix](https://github.com/embeddedt/VintageFix/tree/4159239223865ddbf2960ba019ccf08f73466ef2)、[dynamic model cache](https://github.com/embeddedt/VintageFix/blob/4159239223865ddbf2960ba019ccf08f73466ef2/src/main/java/org/embeddedt/vintagefix/dynamicresources/model/DynamicModelCache.java)、[FerriteCore license](https://github.com/embeddedt/VintageFix/blob/4159239223865ddbf2960ba019ccf08f73466ef2/LICENSE.ferritecore) | dynamic resources / dedup / component 境界 |
| R19 | [FermiumASM](https://github.com/FermiumModding/FermiumASM/tree/76caf6ce7ce866ca9929d9262d87047072cb6686)、[5.31](https://github.com/FermiumModding/FermiumASM/releases/tag/5.31)、[animation issue #12](https://github.com/FermiumModding/FermiumASM/issues/12) | on-demand sprite / vertex pool、consumer 漏れの例 |
| R20 | [StellarCore](https://github.com/NovaEngineering-Source/StellarCore/tree/98c0ea4684e4200d76d0c078cb51345c17e0a050)、[1.6.4](https://github.com/NovaEngineering-Source/StellarCore/releases/tag/v1.6.4)、[HUD license](https://github.com/NovaEngineering-Source/StellarCore/blob/98c0ea4684e4200d76d0c078cb51345c17e0a050/src/main/java/github/kasuminova/stellarcore/client/hudcaching/LICENSE-Patcher_HUDCaching.md) | loader / texture / quad cache、HUD component 例外 |
| R21 | [Nothirium](https://github.com/Meldexun/Nothirium/tree/eed451195dbd8207a629523577429b6b0e5968fe)、[0.4.9-beta](https://github.com/Meldexun/Nothirium/releases/tag/v1.12.2-0.4.9-beta) | dynamic VBO / GL43 submission、採用許諾は未確認 |
| R22 | [UniversalTweaks](https://github.com/ACGaming/UniversalTweaks/tree/3239ded2513f19b69b920dfc6ea6225458d70075) | mod 固有 hot path と invalidation の参考 |
| R23 | [Celeritas Extra](https://github.com/Sumire-Labs/Celeritas-Extra) | rendering profiler / settings、品質削減との分離 |
| R24 | [CeleritasLeafCulling](https://github.com/Karnatour/CeleritasLeafCulling) | hollow / aggressive の見た目の交換条件 |
| R25 | [Angelica README](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/README.MD)、[2.2.31](https://github.com/GTNewHorizons/Angelica/releases/tag/2.2.31) | 実在する SDL GPU backend と機能・version |
| R26 | [Angelica item VBO PR #1034](https://github.com/GTNewHorizons/Angelica/pull/1034)、[`ItemRenderListManager`](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/src/main/java/com/gtnewhorizons/angelica/rendering/items/ItemRenderListManager.java) | item mesh cache と bounded lifetime |
| R27 | [Angelica `TesrMeshProvider`](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/src/main/java/com/gtnewhorizons/angelica/api/tesr/TesrMeshProvider.java)、[`TesrMeshCache`](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/src/main/java/com/gtnewhorizons/angelica/api/tesr/TesrMeshCache.java)、[`ModelPartBatch`](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/src/main/java/com/gtnewhorizons/angelica/api/tesr/ModelPartBatch.java) | provider key / dirty / transform と部分 model batch |
| R28 | [Angelica SDL GPU PR #1993](https://github.com/GTNewHorizons/Angelica/pull/1993)、[`FrameManager`](https://github.com/GTNewHorizons/Angelica/blob/23aa04361d5fcb733e1203bea0eac827f4902469/sdl-gpu/src/main/java/com/gtnewhorizons/angelica/sdlgpu/frame/FrameManager.java) | upload batch / arena、pending work / acquisition の費用 |
| R29 | [Angelica culling fix #2212](https://github.com/GTNewHorizons/Angelica/pull/2212)、[SDL vertex replay #2224](https://github.com/GTNewHorizons/Angelica/pull/2224) | edge の visibility bug、shader 対応を本体性能と分離 |
| R30 | [FalseTweaks](https://github.com/FalsePattern/FalseTweaks/tree/f9e72dd65ebc94bb47087309e29eeb8df72c2c38)、[4.4.5](https://github.com/FalsePattern/FalseTweaks/releases/tag/4.4.5)、[license](https://github.com/FalsePattern/FalseTweaks/blob/f9e72dd65ebc94bb47087309e29eeb8df72c2c38/LICENSE) | packing / mipmap / rendering と occlusion の license 例外 |
| R31 | [Neodymium](https://github.com/makamys/Neodymium/tree/4c2e0189266b7ed0c43cc2edcf8cfba64d6779a2)、[0.2.5](https://github.com/makamys/Neodymium/releases/tag/0.2.5) | mesh reuse と RAM / VRAM / throughput の条件 |
| R32 | [ArchaicFix](https://github.com/embeddedt/ArchaicFix/tree/b436d501436fb65672d73e75aaa02ea72c023da2)、[license](https://github.com/embeddedt/ArchaicFix/blob/b436d501436fb65672d73e75aaa02ea72c023da2/LICENSE.md) | lifetime / memory と occlusion の例外 |
| R33 | [Hodgepodge](https://github.com/GTNewHorizons/Hodgepodge/tree/4668d633849fa747d9134b944cfbfcf6dd137dd3)、[2.7.216](https://github.com/GTNewHorizons/Hodgepodge/releases/tag/2.7.216) | mod renderer / memory / 調査の参考 |
| R34 | [Khronos descriptor management](https://docs.vulkan.org/samples/latest/samples/performance/descriptor_management/README.html) | persistent cache、large UBO / dynamic offset、frame 所有 |
| R35 | [Khronos constant data](https://docs.vulkan.org/samples/latest/samples/performance/constant_data/README.html) | 128 B 保証と push / UBO の交換条件 |
| R36 | [Khronos multi draw indirect](https://docs.vulkan.org/samples/latest/samples/performance/multi_draw_indirect/README.html) | packed vertex / index / commands、CPU / GPU generation、feature fallback |
| R37 | [Khronos wait idle](https://docs.vulkan.org/samples/latest/samples/performance/wait_idle/README.html) | pipeline を drain しない frame completion |
| R38 | [Khronos frames in flight](https://docs.vulkan.org/tutorial/latest/03_Drawing_a_triangle/03_Drawing/03_Frames_in_flight.html) | frame ごとの mutable resource と 2 frames の latency |
| R39 | [Khronos command buffer usage](https://docs.vulkan.org/samples/latest/samples/performance/command_buffer_usage/README.html) | per-frame / worker pool、reset と recording 粒度 |
| R40 | [Khronos pipeline cache](https://docs.vulkan.org/samples/latest/samples/performance/pipeline_cache/README.html) | driver cache の保存・再利用・初回費用 |
| R41 | [Khronos pipeline barriers](https://docs.vulkan.org/samples/latest/samples/performance/pipeline_barriers/README.html)、[synchronization guide](https://docs.vulkan.org/guide/latest/synchronization.html) | producer / consumer stage / access と overlap |
| R42 | [Khronos memory allocation](https://docs.vulkan.org/guide/latest/memory_allocation.html)、[VMA usage patterns](https://gpuopen-librariesandsdks.github.io/VulkanMemoryAllocator/html/usage_patterns.html) | suballocation、placement、budget / pressure |
| R43 | [VMA mapping](https://gpuopen-librariesandsdks.github.io/VulkanMemoryAllocator/html/memory_mapping.html)、[MIT license](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/blob/master/LICENSE.txt) | persistent map、non-coherent range、採用境界 |
| R44 | [VkPhysicalDeviceFeatures](https://docs.vulkan.org/refpages/latest/refpages/source/VkPhysicalDeviceFeatures.html)、[VK_EXT_multi_draw](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_multi_draw.html)、[indexed indirect](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdDrawIndexedIndirect.html)、[descriptor arrays](https://docs.vulkan.org/guide/latest/descriptor_arrays.html) | optional features / count / firstInstance と baseline |
| R45 | [OpenJDK JEP 454](https://openjdk.org/jeps/454) | FFM は JDK 22。release 21 / no-preview の boundary |
| R46 | [meshoptimizer README](https://github.com/zeux/meshoptimizer/blob/master/README.md)、[MIT license](https://github.com/zeux/meshoptimizer/blob/master/LICENSE.md) | index / vertex cache / fetch の CPU 処理、GPU 世代差、順序と費用。mesh shader 必須ではない |

Khronos sample の概念紹介より、API version / feature の厳密な条件は refpage / spec を優先する。例えば indexed indirect draw 自体を Vulkan 1.2 で初めて導入された API と扱わない。extension を使わない direct baseline と、optional multidraw の違いを維持する。

### 11.2 外部 code snapshot の pin

release の tag と repository HEAD は同一とは限らない。以下は実装読取の HEAD であり、release 版 artifact の hash を代用しない。

| Repository | 読取 HEAD | 最新 GitHub release と公開日 UTC |
| --- | --- | --- |
| GTNewHorizons/Angelica | `23aa04361d5fcb733e1203bea0eac827f4902469` | 2.2.31 / 2026-10-11 |
| FalsePattern/FalseTweaks | `f9e72dd65ebc94bb47087309e29eeb8df72c2c38` | 4.4.5 / 2026-08-09 |
| makamys/Neodymium | `4c2e0189266b7ed0c43cc2edcf8cfba64d6779a2` | 0.2.5 / 2024-03-27 |
| embeddedt/ArchaicFix | `b436d501436fb65672d73e75aaa02ea72c023da2` | 0.8.0 / 2026-05-21 |
| GTNewHorizons/Hodgepodge | `4668d633849fa747d9134b944cfbfcf6dd137dd3` | 2.7.216 / 2026-10-05 |
| Meldexun/Nothirium | `eed451195dbd8207a629523577429b6b0e5968fe` | v1.12.2-0.4.9-beta / 2026-01-10 |
| embeddedt/VintageFix | `4159239223865ddbf2960ba019ccf08f73466ef2` | GitHub release なし。HEAD で比較 |
| FermiumModding/FermiumASM | `76caf6ce7ce866ca9929d9262d87047072cb6686` | 5.31 / 2026-03-11 |
| NovaEngineering-Source/StellarCore | `98c0ea4684e4200d76d0c078cb51345c17e0a050` | v1.6.4 / 2026-10-08 |
| ACGaming/UniversalTweaks | `3239ded2513f19b69b920dfc6ea6225458d70075` | GitHub release なし。HEAD で比較 |
| RaphiMC/ImmediatelyFast | `a756fbac991bc8aacc2400347f050392193ae51c` | v1.17.1 / 2026-09-17 |
| tr7zw/EntityCulling | `5269dd55d4fb58899d3846c7b2422868ff54f9ac` | 1.11.3 / 2026-10-08 |
| FxMorin/MoreCulling | `83ec1192726d5ec1332d411166addd9aa7557c5e` | v1.9.0 / 2026-10-03 |
| FoundationGames/EnhancedBlockEntities | `b0b202a18a1acbddfc038e5403bde973bece7c98` | GitHub release なし。1.21.4 branch |
| xCollateral/VulkanMod | `087ef24e58e5522dec5ccb44f279768786bfb6da` | 0.6.0 / 2026-03-04 |
| GTNewHorizons/Celeritas | `989ebf2e52ceed052ec112a569e0c1d172095756` | 固定 source を比較基準に使用 |

BadOptimizations / Particle Core / Celeritas Extra / Leaf Culling / meshoptimizer の文献参照は上の 16 HEAD pin に含めていない。引用した README / release の確認と code snapshot の深さを同一にしない。Sodium の新 source は import 候補として採取せず、公開 PR / release / license の設計と境界を参照した。

### 11.3 主要な実装読取先

| Source | 選択した実装 / 確認点 |
| --- | --- |
| Angelica | `sdl-gpu/.../frame/FrameManager.java`、`pipeline/PipelineCache.java`、`pipeline/InstancedConstantBuffers.java`、`resource/PersistentBufferSync.java` |
| Angelica API / item | `api/tesr/TesrMeshProvider.java`、`TesrMeshCache.java`、`ModelPartBatch.java`、`TesrMaterial.java`、`package-info.java`、`rendering/items/{ItemRenderListManager,BlockRenderListManager,DroppedItemInstancer}.java` |
| ImmediatelyFast | `feature/{map_atlas_generation/MapAtlas,sign_text_buffering/SignTextCache,batch_animated_item_updates/AnimatedItemAtlas}.java`、`enhanced_batching/MixinScissorState.java`、group reorder の base / Iris mixin |
| VintageFix | `dynamicresources/model/{DynamicModelProvider,DynamicModelCache}.java`、`stitcher/TurboStitcher.java`、`mixin/textures/MixinTextureAtlasSprite.java`、`LICENSE.ferritecore` |
| FermiumASM | `bakedquad/NormalVertexDataPool.java`、`client/sprite/ondemand/mixins/{TextureMapMixin,TextureAtlasSpriteMixin}.java` |
| StellarCore | `ExampleConfiguration.cfg`、`client/texture/StitcherCache.java`、`client/pool/BakedQuadPool.java`、`client/hudcaching/HUDCaching.java`、HUD mixins と component license |
| Nothirium | `ChunkRendererDynamicVbo.java`、`ChunkRendererGL43.java`、`RenderChunkDispatcher.java`、`IRenderChunkDispatcher.java`、`VertexSortUtil.java` |
| Enhanced Block Entities | `BlockEntityRendererOverride.java`、`SignRenderManager.java`、chest / bell / decorated pot override |
| VulkanMod | `DrawBuffers.java`、`Synchronization.java`、`MemoryManager.java`、`MemoryTypes.java`、`IndirectBuffer.java`、`StagingBuffer.java`、`StagingBuffers.java` |
| UniversalTweaks | `mods/hammerlib/mixin/UTItemColorHelperMixin.java` |

取得した file の完全な URL / byte count は研究 artifact の `external/source-index.json` にある。**名前を列挙したことは、全 dependency / 全法的条件 / 全 branch を監査し終えたという意味ではない。** 実装着手時には使う source と派生元を改めて固定する。

### 11.4 Celtium の着手先

| 契約 | 主要 source |
| --- | --- |
| frame / upload / retirement | `V/VulkanContext.java`、`VulkanSwapchain.java`、`VulkanRetireQueue.java`、`VulkanBuffer.java`、`VulkanHostBuffer.java`、`VulkanUniformBuffer.java` |
| terrain submission | `V/VulkanMultiDrawBatch.java`、`VulkanTerrainGpuBackend.java`、`VulkanTerrainPipeline.java`、`VulkanArena.java` |
| immediate / geometry | `V/VulkanDynamicMesh.java`、`VulkanImmediateDrawer.java`、`VulkanImmediate*Pipeline.java`、model mesh / immediate group producer・consumer |
| texture / descriptor | `V/VulkanGlTexture.java`、`VulkanImmediateTexturePipeline.java`、`VulkanImmediateItemPipeline.java`、`F/mixin/features/textures/` |
| Celeritas CPU engine | `C/render/chunk/compile/executor/ChunkBuilder.java`、`render/chunk/lists/`、`render/chunk/occlusion/`、`render/chunk/multidraw/BatchAssembler.java` |
| terrain vertex format | `C/render/chunk/vertex/format/`、`F/impl/render/terrain/CeleritasWorldRenderer.java`、`C/gui/SodiumGameOptions.java`、対応 native shader / vertex input |
| shader / ABI / post | `S/` と pipeline layout、native ShaderGroup / framebuffer producer・consumer。reflection と原典を優先 |
| benchmark / source access | `scripts/benchmark_celtium.py`、`scripts/prism_validation.py`、`scripts/bench-fixture/src/org/soutalab/celtium/bench/BenchFixture.java`、`scripts/mc-sources.sh` |

## 12. 次に実装を始める担当への指示

1. `AGENTS.md`、最新 `plan.md` / `goal.md`、この文書、実際の HEAD / dirty を読む。既存 source・計器・run artifact を確認して、原典の推測や古い監査だけで修正対象を決めない。
2. OPT-00/01 を既存 harness と計器の範囲で固める。主要な CPU / GPU / wait / allocation を分離し、正しい baseline と performance A/B の jar を pin する。
3. まず OPT-03–07 の data / lifetime / arena / upload を整合した単位で実装する。変更中の code を各 edit ごとに GPU 受入へ持ち込まない。必要な CPU 契約検査で producer / consumer / ownership を揃える。
4. 2 frames in flight と native multidraw、descriptor / pipeline cache へ進む。AMD の局所受入後に Intel の必要 scene を確認し、GPU 同期・memory / feature に関わる変更は早めに両方へ掛ける。
5. entity / item / TESR / particle / GUI と主要 gameplay mod の性能を、geometry / material / instance の共通契約で改善する。特定 renderer ごとの場当たりの改変を積み増さない。
6. 各段階で、同じ画の改善・非回帰・未検証を示して commit / push、`plan.md` の現状と `goal.md` を同期する。本体 shaderless と主要 mod の成立後は `shader-plan.md` に続く。

完了の根拠は、同じ場面で通常プレイの描画・操作・設定・mod が成立し、native Vulkan の最終 frame が window へ届き、両 GPU の実測で速くなったこと。文書の項目消化数や GPU feature の数を完成率にしない。
