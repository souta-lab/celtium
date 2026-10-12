# Celtium — code-only full bug hunt

> ## 解決状況（2026-10-12 時点・`docs/plan.md` §0.347 が根拠）
>
> **解決済み（27件）**: BH-01, BH-02, BH-03, BH-04, BH-05, BH-06, BH-07, BH-08, BH-10, BH-11, BH-12, BH-13,
> BH-15, BH-16, BH-19, BH-22, BH-23, BH-25, BH-27, BH-28, BH-29, BH-30, BH-32, BH-34, BH-36, BH-37
> — 各件の根拠（受入値・計器名・commit）は `docs/plan.md` §0.347 と §0.352 の表にある。
>
> **修正は着地・受入が未取得（1件）**: BH-09 — 機構を特定して修正を着地した（`218ea2120`・group が開いた時に
> scissor が無効なら live 記録を採る）が、**`scissorCapturedGroups > 0` の受入脚はまだ取れていない**ため
> **解決済みとは書かない**。
>
> **未解決（9件・担当へ移譲済み）**: BH-14, BH-17, BH-18, BH-20, BH-21, BH-24, BH-26, BH-33, BH-35
> — この見出しの下の各項の本文は**起票時の記述**であり、現在の状態ではない。現在の状態は plan §0.347 / §0.352 を読むこと。


作成日: 2026-10-11（JST）  
対象: Minecraft 1.12.2 / Cleanroom / CeltiumのGL-less native Vulkan経路  
方法: 固定ソースの静的な呼び出し追跡、shader/pipelineのABI照合、Celeritas・Minecraft/Forge原典との比較

## 判定

**独立した根本原因を37件整理した。P1が23件、P2が14件。** 描画・状態の誤りだけでなく、device/surface条件、異常回復、資源寿命、Celeritasからの性能退行を含む。ゲーム内で37件を再現したという意味ではない。

優先して直すべきなのは、追加差分のattachment layout変更（BH-37）、FBOのidentity/サイズ/clear/失敗先の契約（BH-14〜16）、draw状態のsnapshot（BH-03〜07）、viewportとclip（BH-09/13）、128 byte deviceとWSI条件（BH-17〜19）、world teardownとupload失敗時のcommit（BH-21/25/26）。ラグ対策には同期・allocation・multidrawの3点（BH-27/28/36）が必要。

**Celeritasの最適化は残っている。ただし、GPU送信・同期・資源再利用の一部は同等に移植されていない。** また、現コードは完成した汎用OpenGL→Vulkan変換器ではない。既知のMinecraft描画familyをnative Vulkanへ送る実装と、広いGL起動shimが共存している。shimの非対応を「成功したGL呼び出し」のように返す点は互換性上の欠陥であり、Zink型の汎用実装へ拡張して解決する必要はない。

## ソースの固定と範囲

| 対象 | 固定値 | 扱い |
|---|---|---|
| Celtium主監査 | `a971006f6f24165a6b2e9603022ef9284554e1be` | 本文BH-01〜36の行番号の基準。取得時刻のラベルは19:36 JST |
| 追加差分追査 | `2f3f1018e7ae1da37efe2597a17c18ade02a8b31` | 主監査後のproduction Java 3ファイルの変更を確認。BH-23の部分改善とBH-37を反映 |
| Celeritas比較元 | `989ebf2e52ceed052ec112a569e0c1d172095756` | `.celtium-work/upstream-baseline`の実コード。2026-09-27のupstream由来commit。最新upstream全体との比較ではない |
| Celtium repo | [souta-lab/celtium](https://github.com/souta-lab/celtium) | production codeは変更していない |
| Celeritas upstream | [GTNewHorizons/Celeritas](https://github.com/GTNewHorizons/Celeritas) | checkoutのupstream remoteを確認。originはCeltium fork |
| 原典 | Minecraft 1.12.2 / Forge 14.23.5.2847 | 手元のdecompileされたJavaを使用。GL API呼び出し、matrix/lighting、cloud、selection、UI経路を確認 |

監査用ソースarchiveのSHA-256: `39006f5c712d67ece7871b872bc95d6868974aed5582e785ddc633f694e2f003`。productionコードの根拠リンクはcommitを固定しており、開発中のHEADが進んでも参照先は変わらない。

対象snapshotには`celtium-vulkan`のJava 55ファイル/38,978行、`common`の331ファイル/43,204行、`forge122`の97ファイル/8,964行が含まれる。主にnative backend、immediate/model/terrain、texture、FBO/post、swapchain/context、staging/allocator、Forge hooks、関連shaderを追った。ファイル数は取得範囲の規模であり、全関数に形式証明を与えた件数ではない。modernプラットフォームや休止中のGL bridge全経路の完全監査は主張しない。

画像・ゲームログ・DSHの進捗発言・過去の監査MD・計画書の達成表示・benchmarkの数値は、不具合の証拠に使っていない。ビルド、ゲーム起動、GPU実行、追加testは行っていない。以下の「確認」は修正後に行うべき確認条件であり、この監査で通ったcheckではない。

### 重大度と確度

| 表記 | 意味 |
|---|---|
| P1 | 基本描画の破損/欠落、動作可能deviceの取りこぼし、資源所有や回復の不整合、主要な直列化。alpha/安定化の前に優先する |
| P2 | 限定条件の不一致、長時間の枯渇、設定未接続、互換性の能力誤認、性能退行候補 |
| コード上の成立 | 本文の条件で、値・target・寿命の契約が破れることをソースで追えた。現在の実プレイでその条件が起きたかは別 |
| 条件付き | 対応device、特定draw順序、allocation failureなどが必要。条件が未観測だからといって正常経路の再現済みと混ぜない |
| 未確定候補 | 根拠が不足するものは37件に含めず、後半の保留事項へ分けた |

通常経路で必ずプロセスを壊すP0は、この監査では立証していない。GPU manufacturer別の対応/非対応一覧やFPS向上率も出していない。

## 不具合一覧

| ID | 優先度 | 根本原因 |
|---|---|---|
| [BH-01](#bh-01) | P1 | 地形アリーナの容量と頂点strideが揃わず、頂点の先頭位置が切り捨てられる |
| [BH-02](#bh-02) | P1 | 透明地形のindex offset 0を「共有quad index」と誤認する |
| [BH-03](#bh-03) | P1 | alpha testの参照値が別フィールドに書かれ、比較関数も失われる |
| [BH-04](#bh-04) | P1 | immediateのbatch keyに色・texture matrix・fogが入らず、後続drawを前の状態で描く |
| [BH-05](#bh-05) | P1 | lightingをdraw時ではなくgroup終了時のlive stateから読む |
| [BH-06](#bh-06) | P1 | texture bindingとTEXTURE_2D enableがtexture unitごとに保持されない |
| [BH-07](#bh-07) | P1 | separate blendのalpha factorが固定され、int overloadにも穴がある |
| [BH-08](#bh-08) | P2 | immediateのcull stateが更新されず、常に無効のままになる |
| [BH-09](#bh-09) | P1 | scissorが無視され、Celeritas自身のスクロールUIをclipできない |
| [BH-10](#bh-10) | P1 | color maskが無視され、Fancy雲のdepth-only passが色まで描く |
| [BH-11](#bh-11) | P2 | polygon offsetが無視され、破壊overlay等が元の面と競合する |
| [BH-12](#bh-12) | P1 | vanillaの2px選択線を拒否し、GL-lessではfallbackも絵を出さない |
| [BH-13](#bh-13) | P1 | 狭いviewportの次のfull viewportを復元せず、画面外viewportも描いてしまう |
| [BH-14](#bh-14) | P1 | 異なる非main FBOが同じchain imageを共有し、固有のサイズも失われる |
| [BH-15](#bh-15) | P1 | off-target作成失敗をmain frameへの描画に置き換える |
| [BH-16](#bh-16) | P1 | clearのtarget・mask・色・順序が保持されず、off-target clearが消える |
| [BH-17](#bh-17) | P1 | 通常GUIに208 byte push constantを要求し、128 byte機器の経路がない |
| [BH-18](#bh-18) | P1 | swapchain作成引数がsurface capabilitiesの上限・usage・alphaに従わない |
| [BH-19](#bh-19) | P1 | present可能か確認する前にdevice/queueを固定し、代替候補を探さない |
| [BH-20](#bh-20) | P2 | native constructorの途中失敗で既に作ったhandleが回収されない |
| [BH-21](#bh-21) | P1 | world destroyでCeleritasのCPU/off-heap/staging資源のdeleteが抜ける |
| [BH-22](#bh-22) | P2 | texture generationごとのdescriptorが退役せず、有限poolを使い切る |
| [BH-23](#bh-23) | P2 | sampler stateを一度しか確認せず、変更・minificationの全意味を反映できない |
| [BH-24](#bh-24) | P1 | UniformBufferのgrowがreplacement完成前に旧所有状態を壊す |
| [BH-25](#bh-25) | P1 | region upload失敗を握りつぶした後、chunkをbuilt済みとして確定する |
| [BH-26](#bh-26) | P1 | 所有imageのlayout/初回clearをrecord時に確定し、未submitの破棄で戻さない |
| [BH-27](#bh-27) | P1 | 通常のmesh upload/presentでもqueue全体の待機を繰り返す |
| [BH-28](#bh-28) | P2 | immediate groupごとに2つのGPU buffer/memoryを新規確保する |
| [BH-29](#bh-29) | P2 | normal付きformat 8/9/10を受理するが、native texture shaderがnormalを使わない |
| [BH-30](#bh-30) | P2 | Item/Block/Particleのnative fragmentにworld fogが揃っていない |
| [BH-31](#bh-31) | P2 | inverse model-viewで光源だけを正規化し、非一様scaleでnormal lightingが違う |
| [BH-32](#bh-32) | P2 | 地形atlas samplerが固定maxLOD 4で、mipmap設定と結び付いていない |
| [BH-33](#bh-33) | P2 | chunk fade-inの設定を残したまま、native uniformでは常時無効にする |
| [BH-34](#bh-34) | P2 | legacy callbackのglTexEnvfがfloatのbit列をenum値として保存する |
| [BH-35](#bh-35) | P2 | GL4.6/GLSL4.60と関数pointerを広告し、未実装機能へmodを誘導する |
| [BH-36](#bh-36) | P2 | Celeritasのmultidrawをnative側でcommandごとのvkCmdDrawIndexedへ分解する |
| [BH-37](#bh-37) | P1 | 追加差分の一括sample transitionが、直後にattachする画像もREAD_ONLYへ変える |

## 個別の根拠・修正方針

<a id="bh-01"></a>

### BH-01 — P1 — 地形アリーナの容量と頂点strideが揃わず、頂点の先頭位置が切り捨てられる

**成立条件**: Compactの24バイトstride、または24/32バイトを共存させる96バイト共通strideを選ぶ。32バイト単独では初期容量が割り切れるため、この欠陥が隠れる。

**コード上の経路**: 容量は4バイト単位にしか丸めない。割り当て長だけstrideの倍数にした後、free.end()から引いて割り当てるので、末端の余りが全segmentの開始位置に残る。初期容量524,288は24で割ると余り8、96で割ると余り32。getOffset()はbyteOffset/strideの整数除算で、その余りを捨てる。実データを書いた先頭と、baseVertexから参照する先頭が一致しない。

**影響**: 設定・頂点形式の切り替えで地形頂点が別のフィールド境界から読まれる。位置、色、UVなどが同時に壊れ得る。

**修正方針**: 容量・全free rangeの境界・grow後の末端をlcm(4,stride)に揃えるか、Celeritas同様にアリーナを頂点要素単位で管理する。長さだけを丸める修正では足りない。quadの4頂点境界も保持する。

**修正後の確認**: stride 24/32/96それぞれで、初回割り当て・分割・解放再利用・grow後の全segmentについてbyteOffset % stride == 0と、upload先頭 == draw参照先頭を確認する。

**根拠**: [VulkanArena.java L114–179](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L114-L179) / [VulkanArena.java L636–638](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java#L636-L638) / [VulkanTerrainGpuBackend.java L10743–10752](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10743-L10752) / [Celeritas: GlBufferArena.java L37–48](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/GlBufferArena.java#L37-L48)

**限定・補足**: Celeritasの実アリーナはcapacityを要素数で持ち、バッファ容量をcapacity×strideで作る。移植で単位をbyteに変更した部分の退行。

<a id="bh-02"></a>

### BH-02 — P1 — 透明地形のindex offset 0を「共有quad index」と誤認する

**成立条件**: ソート済み透明パスのindexArena内に、正当なbyte offset 0の割り当てができる。アリーナが末端から割り当て、残り領域に完全一致する場合などで成立する。

**コード上の経路**: BatchAssemblerはsortedの実index位置をそのまま渡す。一方、Vulkan描画ループはbyteOffset == 0ならindexArenaをbindせずfirstIndexを0にする。ループ初期には共有quad indexがbindされているので、最初のsorted commandの実位置が0なら別バッファを読んでしまう。0は合法なoffsetであり、未ソートの種別タグにはできない。

**影響**: 透明面の並び順・index内容を取り違える。正のoffsetのcommandが先行していたかどうかで挙動が変わり、見落としやすい。

**修正方針**: render passのisSorted、または明示的なindex sourceでバッファを選択する。offsetは0を含む純粋な位置情報に戻す。

**修正後の確認**: sortedの最初のcommandが0、その後が正のoffsetになるケースと、その逆順を確認する。共有indexのunsortedパスも同時に保つ。

**根拠**: [VulkanTerrainGpuBackend.java L7032–7035](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7032-L7035) / [VulkanTerrainGpuBackend.java L7066–7078](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7066-L7078) / [BatchAssembler.java L454–493](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler.java#L454-L493) / [Celeritas: DefaultChunkRenderer.java L173–190](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer.java#L173-L190)

<a id="bh-03"></a>

### BH-03 — P1 — alpha testの参照値が別フィールドに書かれ、比較関数も失われる

**成立条件**: GlStateManager.alphaFuncで初期値0.1以外の値を設定する、またはGREATER以外の比較関数を指定する。

**コード上の経路**: alphaFunc(func, reference)はalphaRefに書くが、captureStateが読むのは独立したalphaFuncReference。後者のsetterを呼ぶ経路は監査対象内にない。さらにshaderは「ref > 0かつalpha < ref」のdiscardだけで、GLの比較関数は渡していない。GREATERの等号境界、ref == 0、LESS/ALWAYS/NEVER等の契約も一致しない。

**影響**: cutoutの輪郭、半透明レイヤー、低alpha部分の残り方が、呼び出し側の指定と違う。

**修正方針**: enabled・比較関数・referenceを一つのAlphaTestStateにまとめ、draw時に固定する。shaderには比較種別と値を独立して伝える。0をdisabledの代替値にしない。

**修正後の確認**: alpha 0/0.05/0.1/0.5/1、ref 0/0.1/0.5、GREATER/GEQUAL/LESS/ALWAYS/NEVERの期待結果を表で照合する。

**根拠**: [VulkanGlState.java L340–382](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L340-L382) / [VulkanTerrainGpuBackend.java L1819–1827](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1819-L1827) / [VulkanTerrainGpuBackend.java L4618–4641](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4618-L4641) / [immediate_tex_color.frag L28–40](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/immediate_tex_color.frag#L28-L40)

<a id="bh-04"></a>

### BH-04 — P1 — immediateのbatch keyに色・texture matrix・fogが入らず、後続drawを前の状態で描く

**成立条件**: target、viewport、format、texture、projection/model-view、通常のpipeline stateが同じまま、current color・texture matrix・fogだけを変えて連続drawする。

**コード上の経路**: group分割条件にはこれらの値が含まれない。色、texture affine、fogはgroup生成時に一度だけ採る。例えばPOSITION_TEXの赤quad→色だけ青に変更→青quadは同じgroupにappendされ、両方とも最初の色を使う。texture matrixの変更もprojection/model-viewの比較では検出できない。

**影響**: GUIの色、合成レイヤー、textureアニメーション、同一バッファを異なるfog条件で描く場面で状態が漏れる。

**修正方針**: draw受理時のimmutableなDrawStateを作り、使用する全uniformの値・世代をgroup keyに含める。必要なstate変更でgroupを閉じる方式も可能だが、値を変えた後に古いdrawをsnapshotしない。

**修正後の確認**: 同一textureの2quadでcolorだけ、texture matrixだけ、fogだけを変える3ケースを確認する。個別drawとbatch drawの出力を一致させる。

**根拠**: [VulkanTerrainGpuBackend.java L4074–4129](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4074-L4129) / [VulkanTerrainGpuBackend.java L4157–4190](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4157-L4190) / [VulkanTerrainGpuBackend.java L4618–4641](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4618-L4641) / [VulkanTerrainGpuBackend.java L2777–2782](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2777-L2782)

<a id="bh-05"></a>

### BH-05 — P1 — lightingをdraw時ではなくgroup終了時のlive stateから読む

**成立条件**: litのimmediate drawをappendした後、同じgroupを閉じる前に光源方向・diffuse・ambient・enabled lightを変更する。

**コード上の経路**: closeCurrentImmediateGroupはceltium$lightParamsを呼ぶ。lightParamsは保存されたmodel-viewを受け取りながら、光源の方向・色・有効maskを現在のimmediateStateから読む。light setterはgroupを閉じない。したがって、draw→光源変更→flushだけで、先に受理した頂点が後の光源で描かれる。

**影響**: アイテム、entity/TESRに隣接する描画の明るさが、flushのタイミングに依存する。

**修正方針**: light payloadをdraw受理時に採る。光源世代をbatch keyに含め、saved stateとlive stateを混ぜない。モデルcache経路で先に採っているlight payloadと設計を揃える。

**修正後の確認**: draw後にdisableStandardItemLighting相当の状態変更をしてからflushしても、既に受理したdrawの照明が変わらないことを確認する。

**根拠**: [VulkanTerrainGpuBackend.java L1836–1855](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L1836-L1855) / [VulkanTerrainGpuBackend.java L2483–2530](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2483-L2530) / [VulkanTerrainGpuBackend.java L5828–5840](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5828-L5840)

<a id="bh-06"></a>

### BH-06 — P1 — texture bindingとTEXTURE_2D enableがtexture unitごとに保持されない

**成立条件**: unit 0のskin/atlasをbindした後、lightmapやbrightness用の別unitをbindし、再bindせずunit 0へ戻る。

**コード上の経路**: VulkanGlStateはboundTextureNameとtexture2dEnabledを各一個だけ持つ。activeTextureUnitは番号を変えるだけで状態を切り替えない。GlStateManagerのbind/enable/disable hookはunitを区別せずこの値を更新する。immediate drawとinvalidateBoundTextureがこの単一値を読む。vanilla RenderLivingBaseのbrightness処理には、別unitを使った後default unitへ戻るだけの経路がある。

**影響**: unit 0の本来のtextureとは別のtexture・white fallbackを使う、または別textureをinvalidateする。stub内部のunit別bindingが存在することは、このbackend mirrorの欠陥を解消しない。

**修正方針**: UnitState[]にbinding・enable・texture matrix等を保持する。基本samplerが読むunitと、現在操作しているunitを分ける。

**修正後の確認**: unit 0=A、unit 2=B、unit 2=0、unit 0に戻す、という順序の後もunit 0=Aが保持されることを確認する。

**根拠**: [VulkanGlState.java L458–472](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L458-L472) / [VulkanGlState.java L514–519](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L514-L519) / [GlStateManagerMixin.java L193–205](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L193-L205) / [GlStateManagerMixin.java L302–305](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L302-L305) / [VulkanTerrainGpuBackend.java L3991–4001](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3991-L4001)

<a id="bh-07"></a>

### BH-07 — P1 — separate blendのalpha factorが固定され、int overloadにも穴がある

**成立条件**: RGBとalphaで異なるfactorを要求する。enum版でなくint版tryBlendFuncSeparateを呼ぶ場合は、RGBのmirror更新自体も外れる。

**コード上の経路**: stateには4つのfactorを保持するが、captureStateはRGBの2つだけを渡す。ImmediateTexture/Item等のpipelineはalphaをONE / ONE_MINUS_SRC_ALPHAに固定する。mixinがhookするseparate APIはenum版のみで、原典のint版は直接OpenGlHelperへ進む。stubのglBlendFuncSeparateはNOOP扱い。

**影響**: alphaを保存するFBOや後段compositeで誤ったopacityが残る。RGBが正しく見える場面でもalphaが一致した証拠にはならない。

**修正方針**: RGB/alpha factorとequationをDrawStateに保持し、pipeline keyと作成引数へ渡す。原典のcanonicalなint経路を捕捉し、enumの委譲で二重更新しない設計にする。

**修正後の確認**: RGB=SRC_ALPHA/ONE_MINUS_SRC_ALPHA、alpha=ONE/ZEROのblendをRGBA targetに行い、RGBとalpha双方を確認する。int/enum呼び出しで結果を揃える。

**根拠**: [VulkanTerrainGpuBackend.java L4618–4630](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4618-L4630) / [VulkanImmediateTexturePipeline.java L668–675](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L668-L675) / [GlStateManagerMixin.java L236–247](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java#L236-L247) / [CeltiumGlStub.java L374–386](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L374-L386)

<a id="bh-08"></a>

### BH-08 — P2 — immediateのcull stateが更新されず、常に無効のままになる

**成立条件**: 呼び出し側がenableCull/disableCull/cullFaceで裏面の扱いを変更する。対象はimmediate経路。地形のCPU face cullingとは別。

**コード上の経路**: captureStateはcullEnabledを読むが、VulkanGlState.cullEnabled(boolean)への呼び出しが監査対象内にない。対応するGlStateManager hookもなく、raw glCullFaceはstubのNOOP。初期falseがpipelineへ渡り続ける。

**影響**: 背面が見える、両面描画が増える、鏡映・負scaleのモデルで原典との一致が崩れる。

**修正方針**: 既知のゲーム側cull stateをnative raster stateへ渡す。enabledだけでなくfaceとfrontFaceも管理し、matrixの鏡映を含めて規則を決める。

**修正後の確認**: front/back windingの2枚、cull on/off、負scaleモデルの期待面を照合する。地形の面削減を壊さない。

**根拠**: [VulkanGlState.java L348–361](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlState.java#L348-L361) / [VulkanTerrainGpuBackend.java L4627–4641](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4627-L4641) / [VulkanImmediateTexturePipeline.java L647–652](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L647-L652) / [CeltiumGlStub.java L380–386](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L380-L386)

<a id="bh-09"></a>

### BH-09 — P1 — scissorが無視され、Celeritas自身のスクロールUIをclipできない

**成立条件**: VintageDrawContextのscissorによるclipを使う。ScrollableFrameから呼ばれるため、外部modを追加しなくても到達する。

**コード上の経路**: UIはGL_SCISSOR_TESTをenableしglScissorで矩形を指定するが、stubはglScissorをNOOPにする。backendが設定するVkScissorはrender target全体またはviewport矩形であり、UIのclip矩形を受け取っていない。

**影響**: 設定画面のスクロール内容が枠外へはみ出す、親frameのclipが効かない。

**修正方針**: UIのclipをnativeなClipRectとして受け取り、draw snapshotに含める。viewportとscissorを同一視せず、親clipとのintersectionを使う。

**修正後の確認**: ScrollableFrameの上下端から外れる行、入れ子clip、GUI scale変更を確認する。

**根拠**: [VintageDrawContext.java L143–155](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/impl/gui/VintageDrawContext.java#L143-L155) / [ScrollableFrame.java L120–135](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/gui/frame/ScrollableFrame.java#L120-L135) / [CeltiumGlStub.java L380–386](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L380-L386) / [VulkanTerrainGpuBackend.java L10080–10088](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10080-L10088)

<a id="bh-10"></a>

### BH-10 — P1 — color maskが無視され、Fancy雲のdepth-only passが色まで描く

**成立条件**: vanilla Fancy cloudの、colorMask(false,false,false,false)で深度だけ作る最初のpassを通る。

**コード上の経路**: 原典RenderGlobalは雲を二段で描き、最初のpassでは色書き込みを止める。stubのglColorMaskはNOOPで、native immediateのblend attachmentはRGBAすべてのwrite maskを常時有効にする。depth-onlyのはずの雲がvisible colorを出す。

**影響**: 後続の通常passと重なり、雲の濃さ・色・alphaが原典と変わる。anaglyphのチャンネルmaskも保持されない。

**修正方針**: 既知の雲描画をdepth-only/visibleのnative passとして分けるか、colorWriteMaskをDrawState・pipelineに渡す。

**修正後の確認**: 最初の雲pass後はcolor不変でdepthだけが変わり、二段終了後のRGBAが参照と一致することを確認する。

**根拠**: [CeltiumGlStub.java L380–386](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L380-L386) / [VulkanImmediateTexturePipeline.java L668–677](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L668-L677)

**限定・補足**: 原典で確認した呼び出し: net/minecraft/client/renderer/RenderGlobal.java L1651–1668。画像の色味を根拠にした推測ではない。

<a id="bh-11"></a>

### BH-11 — P2 — polygon offsetが無視され、破壊overlay等が元の面と競合する

**成立条件**: vanilla block damage等、coplanarなoverlayにpolygon offsetを使う。

**コード上の経路**: 原典はpolygonOffset(-3,-3)や(-1,-10)とenable/disableを使う。stubはglPolygonOffsetをNOOPにし、native rasterizationには対応するdepth bias stateが入っていない。

**影響**: 破壊textureや同一平面の重ね描きがz-fightingを起こし得る。発生頻度はdepth精度・視点・GPUに依存する。

**修正方針**: block damage/decalのnative passへdepth biasを導入し、GL/VKのdepth規則を踏まえて符号・slopeを対応させる。モデル座標を場当たり的にずらして対処しない。

**修正後の確認**: 近距離と遠距離、斜面方向のoverlay、depth test/writeとの組み合わせを確認する。

**根拠**: [CeltiumGlStub.java L380–386](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L380-L386) / [VulkanImmediateTexturePipeline.java L647–652](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L647-L652)

**限定・補足**: 原典RenderGlobal.java L1815–1816、L1915–1925で用途を確認。

<a id="bh-12"></a>

### BH-12 — P1 — vanillaの2px選択線を拒否し、GL-lessではfallbackも絵を出さない

**成立条件**: ブロック選択枠を描く。vanillaはglLineWidth(2.0F)を指定する。

**コード上の経路**: native line受理は対応formatと幅の条件を持ち、1を超える幅を拒否する。WorldVertexBufferUploaderのmixinは受理falseなら原典へ戻るが、GL-less側は実OpenGL描画を持たない。stubが再提示するdrawも同じ幅制約に当たり、拒否が可視描画へ救済されない。

**影響**: 標準のブロック選択枠が消える経路が成立する。fallbackを呼んだことだけでは描画成功にならない。

**修正方針**: portableなscreen-space quadで2px線を描く。wideLinesに対応する機器だけnative wide lineを使う選択も可能だが、非対応機器向けの経路が必要。

**修正後の確認**: wideLines非対応でもvanillaの2px枠が出ること、遠距離・画面端・GUI scaleにより線が壊れないことを確認する。

**根拠**: [VulkanTerrainGpuBackend.java L3664–3697](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L3664-L3697) / [WorldVertexBufferUploaderMixin.java L418–426](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java#L418-L426) / [CeltiumGlStub.java L2973–3046](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2973-L3046)

**限定・補足**: 原典RenderGlobal.java L1995の要求を確認。これは「wideLines featureを有効にせず2pxを送っている」という指摘ではない。現コードは送る前に拒否している。

<a id="bh-13"></a>

### BH-13 — P1 — 狭いviewportの次のfull viewportを復元せず、画面外viewportも描いてしまう

**成立条件**: 同一passで狭いviewportのgroupを描いた後、full viewportのgroupを描く。またはviewportがtarget外へ完全に出る。

**コード上の経路**: celtium$setGroupViewportは狭い矩形ならvkCmdSetViewport/Scissorを変更するが、full targetなら「passが既に設定した」として早期returnする。直前のgroupが狭めていても復元しない。target外の場合もreturnするだけで、呼び出し元は直後にdrawする。さらにviewportをtargetにclampしてからVkViewportへ設定しており、clipだけでなくNDCからpixelへの写像そのものを変える。

**影響**: 後続GUI/previewが前の矩形へ縮む、配置がずれる、本来見えないdrawが前のviewportに出る。

**修正方針**: 各groupのviewportを明示的に適用し、fullの場合もrestoreする。画面外はdrawをskipする。元viewportの写像を保持し、target境界とのintersectionはscissorに使う。

**修正後の確認**: narrow→full→outside→fullを同一passで連続実行する。部分的にtarget外のviewportも、内容を縮めず正しく切り取る。

**根拠**: [VulkanTerrainGpuBackend.java L9992–9998](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9992-L9998) / [VulkanTerrainGpuBackend.java L10036–10089](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L10036-L10089)

<a id="bh-14"></a>

### BH-14 — P1 — 異なる非main FBOが同じchain imageを共有し、固有のサイズも失われる

**成立条件**: 非mainで非0のFBO AとBを使う。あるいは、main targetより小さいFBOをpreview・loading・mod UIに使う。

**コード上の経路**: GllessFrameTargetはdefaultPairとchainPairの2組しか持たない。backendは非0のoff-targetをすべてchainPairへ送る。ensureChainPair、renderArea、published textureのサイズにはmain由来のtargetWidth/Heightを使い、FBOの報告サイズごとにimageを割り当てていない。AとBは別identityでも同じcolor/depth imageへ書く。

**影響**: 独立して保持すべきFBO内容の上書き、previewのサイズ・sampling・後段合成の不一致。単一FBOの成功は複数FBOの正しさを示さない。

**修正方針**: ゲーム側のFBO identityとgenerationごとにnative targetを所有する。width/height/format/depth attachmentをそのtargetの契約にする。resize/delete時の退役を一箇所へ集める。

**修正後の確認**: A=256×256の赤、B=512×128の青を交互に描き、A/Bを別々にsampleする。片方のresize/deleteでも他方を保持する。

**根拠**: [GllessFrameTarget.java L751–752](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L751-L752) / [VulkanTerrainGpuBackend.java L5650–5693](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5650-L5693) / [VulkanTerrainGpuBackend.java L5715–5722](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5715-L5722)

**限定・補足**: PostChain内部には別target管理がある。この指摘はゲームのimmediate drawを受けるoff-target pairの共有についてであり、PostChainの全内部targetが一組だけという意味ではない。

<a id="bh-15"></a>

### BH-15 — P1 — off-target作成失敗をmain frameへの描画に置き換える

**成立条件**: off-target pair/framebuffer確保や連携処理がRuntimeException/LinkageErrorを投げる。

**コード上の経路**: catch節は「frame imageに代わりに描く」と記録し、ensureFramebuffer(renderPass)を使用する。その後、activeImmediateTargetとcommandStateは依然として元のoff-target identityでpassを開始する。論理targetと物理attachmentが違ったままdrawが成功扱いになる。

**影響**: previewやoutline、loading画面等の失敗がmain frameを汚染する。失敗した機能だけを止める境界がない。

**修正方針**: allocation failureはそのtargetの明示的な失敗にする。drawを保留/拒否し、復旧可能なら正しいtargetでretryする。別targetへの暗黙再送を削除する。

**修正後の確認**: off-target確保へのfault injectionでmain color/depthが不変なことを確認する。成功扱いのcounter/ownershipも更新しない。

**根拠**: [VulkanTerrainGpuBackend.java L5706–5733](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5706-L5733)

<a id="bh-16"></a>

### BH-16 — P1 — clearのtarget・mask・色・順序が保持されず、off-target clearが消える

**成立条件**: FBOをbindし、任意の色でCOLORだけ、またはDEPTHだけをclearする。同一frame中に同じFBOを再clearする。

**コード上の経路**: stubはclear maskを全体のpending maskへORし、clear colorをmutableな一値として持つ。backendのlatchGameClearでは非mainの場合、値を記録するだけで実clearへ渡さずreturnする。Pair.clearは固定RGBAとdepthを同時にclearし、backendは原則frameごと一回に抑える。mainのapplyGameClearsは別の場所で消費するため、clearが発行されたtargetと消費先の契約もない。

**影響**: 背景が指定色にならない、前のpreviewが残る、depthだけのclearがcolorまで消す、同frameの再clearが効かない等が起こり得る。

**修正方針**: clearをtarget identity、mask、色/depth、適用するwrite mask/clip、順序を持つnative commandにする。drawと同じ順序で、該当targetに一度だけ適用する。FBOを使ったという理由だけの固定clearをやめる。

**修正後の確認**: Aへ赤draw→Aを青COLOR clear→緑draw、BのDEPTH-only clear、同frameでAを二回clear、を確認する。mainの内容が巻き込まれないことも見る。

**根拠**: [CeltiumGlStub.java L1206–1213](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1206-L1213) / [VulkanTerrainGpuBackend.java L7307–7318](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7307-L7318) / [GllessFrameTarget.java L483–547](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L483-L547) / [GllessFrameTarget.java L874–904](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L874-L904) / [VulkanTerrainGpuBackend.java L5663–5702](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5663-L5702)

<a id="bh-17"></a>

### BH-17 — P1 — 通常GUIに208 byte push constantを要求し、128 byte機器の経路がない

**成立条件**: maxPushConstantsSizeが128等、208未満のVulkan device。特定メーカーや現在の所有GPUについての実測判定ではない。

**コード上の経路**: ImmediateTexturePipelineとImmediatePipelineは208 byteのrangeを無条件にpipeline layoutへ登録する。lit Itemも208を使う。Contextはlimitを報告するが、これらのnative GUI/world pipelineをUBOへ切り替える処理はない。PostChainにはlimitに応じたUBO経路があり、同じ対応が全体に揃っていない。

**影響**: Vulkan自体に対応するdeviceでも、基本GUIや特定描画familyのpipeline作成が失敗し得る。「Vulkan対応GPUなら動く」という目標を満たせない。

**修正方針**: 小さなpush blockとdynamic UBOに分け、128 byte以内の共通経路を用意する。必要limitを起動前に検証し、成功したdeviceの契約を全pipelineで一貫して使う。

**修正後の確認**: 128/192/256 byte相当のlimit条件で、menu、GUI、lit item、position-only drawが同じ機能範囲で作れることを確認する。

**根拠**: [VulkanImmediateTexturePipeline.java L78](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L78) / [VulkanImmediateTexturePipeline.java L582–591](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L582-L591) / [VulkanImmediatePipeline.java L51](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediatePipeline.java#L51) / [VulkanImmediateItemPipeline.java L53–58](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L53-L58) / [VulkanContext.java L1019–1029](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1019-L1029) / [VulkanPostChain.java L582–599](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L582-L599)

<a id="bh-18"></a>

### BH-18 — P1 — swapchain作成引数がsurface capabilitiesの上限・usage・alphaに従わない

**成立条件**: surfaceのminImageCount==maxImageCount、要求usageの一部が未対応、OPAQUE composite alphaが未対応等のWSI条件。

**コード上の経路**: imageCountはmax(min+1, min(max,3))で計算するため、min=max=2なら3となり上限を超える。COLOR_ATTACHMENT|TRANSFER_DSTはsupportedUsageFlagsに関係なく要求する。compositeAlphaもOPAQUEに固定する。UNORMが見つからず先頭formatを使う分岐も、単一UNDEFINED形式の扱いを分けていない。

**影響**: GPU API対応とは独立に、特定window system/driverでvkCreateSwapchainKHRが失敗する。

**修正方針**: 希望image countをmin/maxの両側でclampする。usage・composite alpha・formatをsurfaceが返した集合から選ぶ。必須usageがない場合は別のnative present方法か明確な非対応判定を行う。

**修正後の確認**: min=max=2、max=0、OPAQUEなし、TRANSFER_DSTなし、単一UNDEFINED formatの入力で作成引数の契約を確認する。

**根拠**: [VulkanSwapchain.java L1059–1075](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1059-L1075) / [VulkanSwapchain.java L1130–1154](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L1130-L1154)

<a id="bh-19"></a>

### BH-19 — P1 — present可能か確認する前にdevice/queueを固定し、代替候補を探さない

**成立条件**: 複数GPU、複数queue family、graphics queueとpresent queueが異なる構成など、最初に選んだ候補がsurfaceをpresentできない環境。

**コード上の経路**: ContextはAPI条件とCPU device除外等でphysical deviceを先に選び、graphics+transferのqueueを選ぶ。後でSwapchain側がsurface supportを確認し、不一致ならreturn nullする。そこで他queue familyや他GPUへ選び直さない。

**影響**: 使えるVulkan GPU/queueが存在しても、列挙順によってpresentできない構成を選んでしまう。

**修正方針**: surfaceを含むDeviceContractを作り、required extensions、graphics、present、必要format/usage/featuresを満たす候補を評価する。別present queueを採用する場合はownershipとsharingの扱いも決める。

**修正後の確認**: 先頭deviceがpresent不可・第二deviceが可、同deviceの第二familyだけがpresent可、という条件を確認する。

**根拠**: [VulkanContext.java L875–929](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L875-L929) / [VulkanContext.java L1180–1188](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1180-L1188) / [VulkanSwapchain.java L450–458](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L450-L458)

<a id="bh-20"></a>

### BH-20 — P2 — native constructorの途中失敗で既に作ったhandleが回収されない

**成立条件**: descriptor layout/pool/pipeline layoutを作った後、後段のpipeline・ring・memory確保等が失敗する。surface作成後にpresent不可と判定する場合も含む。

**コード上の経路**: ImmediateTexture/Item/Immediateのfactoryは複数のcreateを順番に行うが、全体をrollbackするcatch/finallyを持たない。shader moduleのfinally解放だけでは、それより前のhandleは戻らない。PostChain.createにも長い段階的確保のrollbackが揃っていない。swapchainのsemaphore poolはローカル配列を埋める途中で失敗すると、既に作ったsemaphoreを回収しない。surface作成後の早期returnにもdestroyがない。

**影響**: 低memory・一部機器での作成失敗・retry時にVk handle/VRAMが増える。正常終了時のdeleteがあっても、返ってこなかったオブジェクトは回収できない。

**修正方針**: 作成をtransaction化し、全handleをローカル所有のまま保持する。全工程成功時に公開し、失敗時は逆順にdestroyする。retry回数とfailure状態も管理する。

**修正後の確認**: 各native create段階を一つずつ失敗させ、作成数とdestroy数を突き合わせる。次の正常createが成功し、旧handleを再利用しないことを確認する。

**根拠**: [VulkanImmediateTexturePipeline.java L243–253](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L243-L253) / [VulkanImmediateItemPipeline.java L136–146](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L136-L146) / [VulkanImmediatePipeline.java L116–125](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediatePipeline.java#L116-L125) / [VulkanPostChain.java L390–506](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java#L390-L506) / [VulkanSwapchain.java L888–920](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L888-L920) / [VulkanSwapchain.java L450–458](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java#L450-L458)

<a id="bh-21"></a>

### BH-21 — P1 — world destroyでCeleritasのCPU/off-heap/staging資源のdeleteが抜ける

**成立条件**: GL-less worldを終了して次のworldへ移る。region metadataが残っている状態、mapped stagingが選ばれた状態で影響が大きい。

**コード上の経路**: RenderSectionManager.destroyは元のregions.deleteとchunkRenderer.deleteを削り、terrainBackend.deleteへ置き換えた。一方、native backendのreleaseWorldResourcesはnative arena/pipelineを片付けるが、RenderRegionManager.deleteを呼ばない。managerは依然としてstaging bufferを作る。region storageとdraw strategyのoff-heap領域も、そのdelete経路で明示解放する設計である。参照をnullにするだけではnmemAlloc等の資源は解放されない。

**影響**: worldの出入りでhost native memory、stub buffer記録、未使用staging資源が残る。GPU arenaを解放したことだけではworld teardownが完了しない。

**修正方針**: CPU metadataのdeleteをGPU backendから独立させて必ず実行する。GL-lessでは不要なGL staging/rendererの構築自体をやめ、必要なCPU storageはnative側で明示解放する。

**修正後の確認**: world load/unloadを繰り返し、off-heap allocation、staging registry、region storageのlive数が基準値へ戻ることを確認する。

**根拠**: [RenderSectionManager.java L988–1005](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L988-L1005) / [VulkanTerrainGpuBackend.java L6356–6435](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6356-L6435) / [RenderRegionManager.java L203–209](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager.java#L203-L209) / [MappedStagingBuffer.java L40–55](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L40-L55) / [SectionRenderDataStorage.java L210–219](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage.java#L210-L219) / [Celeritas: RenderSectionManager.java L915–939](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L915-L939)

**限定・補足**: Celeritasの実コードではRenderSectionManager.destroyからregion/rendererのdeleteへ進む。移植後の責務置き換えが不完全な部分。

<a id="bh-22"></a>

### BH-22 — P2 — texture generationごとのdescriptorが退役せず、有限poolを使い切る

**成立条件**: 同じworld/windowでtextureのreallocate・sampler変更・resource reload等を繰り返し、pipelineごとのtexture keyが増える。

**コード上の経路**: ImmediateTexturePipelineのdescriptor cacheはtexture.keyをキーとしてsetを保持する。keyにはview/generationが入り、以前の世代のsetを削除する経路がない。poolは64 sets単位で増やすが、pool数の上限があり、このpipelineでは64×64=4096 setsで止まる。古いtextureをdeleteしてもcache entryを返さない。

**影響**: 長時間稼働中に新textureのdescriptor作成が失敗する。古い画像へのdescriptorを保持することも、resource寿命の追跡を複雑にする。

**修正方針**: texture identity/generationの退役通知をdescriptor cacheへ送り、GPU使用完了後にset/poolを再利用する。frame/epoch単位のpool運用か、上限付きcacheを用いる。

**修正後の確認**: 同一pipeline内で4096を超える世代変更を行い、live texture数に比例した資源数へ収束することを確認する。

**根拠**: [VulkanImmediateTexturePipeline.java L372–405](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L372-L405) / [VulkanImmediateTexturePipeline.java L412–450](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L412-L450) / [VulkanGlTexture.java L1821–1826](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1821-L1826)

**限定・補足**: world解放時にはimmediate pipelineもdeleteする経路がある。「worldを跨いでもこのcacheが永久に増える」とは断定しない。同一pipeline寿命内の増加が問題。

<a id="bh-23"></a>

### BH-23 — P2 — sampler stateを一度しか確認せず、変更・minificationの全意味を反映できない

**成立条件**: 同じtexture mirrorを使いながらfilter/wrap/maxLODを変更する。announced imageが既にconsumedになったtextureでは、初回確認を飛ばす条件もある。

**コード上の経路**: 基準版のconfirmWrapFromRecordはwrapConfirmedで一度きりになり、stubのparameter更新はmirrorのsampler generationを更新しない。Registryはconsumedなannounced imageを確認処理より前でreturnする。setSamplerReachは増加しか受け付けない。追加差分はmin/magのNEAREST/LINEARを初回に読む改善を入れたが、同じ一度きりのguardと早期returnは残る。mipmapModeはLINEAR固定で、NEAREST_MIPMAP_NEARESTやmipmapなしの意味も同一にはならない。

**影響**: textureの設定変更が反映されない、blur/clamp/repeat/LODが呼び出し側と食い違う。初回min/mag未反映は追加差分で部分改善しているため、現在も全て無視されるとは扱わない。

**修正方針**: pixelのgenerationとsampler parameterのgenerationを分ける。sampler stateをmin、mag、mipmap有無/方式、wrap、min/maxLODとして保持し、変更時にsnapshotとdescriptor退役を更新する。

**修正後の確認**: texture upload後にparameterを設定、初回draw後に変更、同じannounced imageで再draw、LODを下げる、の順で確認する。

**根拠**: [VulkanGlTexture.java L1848–1901](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1848-L1901) / [VulkanTextureRegistry.java L1502–1537](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java#L1502-L1537) / [CeltiumGlStub.java L2580–2605](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L2580-L2605) / [追加差分: VulkanGlTexture.java L1853–1908](https://github.com/souta-lab/celtium/blob/2f3f1018e7ae1da37efe2597a17c18ade02a8b31/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L1853-L1908)

**限定・補足**: 追加差分2f3f1018eではconfirmSamplerStateFromRecordへ名称変更し、min/magの初回確認が追加された。古い指摘の「filterを一切読まない」を現在のコードへそのまま持ち越していない。

<a id="bh-24"></a>

### BH-24 — P1 — UniformBufferのgrowがreplacement完成前に旧所有状態を壊す

**成立条件**: 既存uniform bufferのensureCapacityで、replacementのallocate/bind/mapが失敗する。

**コード上の経路**: ensureCapacityは旧handleをローカルに保存した後、同じオブジェクトのallocateを呼ぶ。allocateはnew bufferをthis.bufferへ先に書く。memory allocation失敗ならthis.bufferを0にしてthrowし、旧bufferへ復元しない。bind/map失敗時にも段階的に公開されたhandleが残る。失敗すると旧handleを持つローカルは失われ、オブジェクトは旧mapped addressと新/0 handleが混在する状態になり得る。

**影響**: 一時的なmemory failureを回復できず、旧bufferのleakや後続delete/writeの所有状態不整合になる。

**修正方針**: replacementを別のローカル資源集合で完全に作り、copyまで成功してからswapする。失敗時はreplacementだけをdestroyし、旧bufferを保持する。VulkanHostBufferのreplacement方式に揃える。

**修正後の確認**: create/allocate/bind/mapの各段階で失敗させ、旧buffer/address/dataが不変で、replacement資源だけが回収されることを確認する。

**根拠**: [VulkanUniformBuffer.java L49–65](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanUniformBuffer.java#L49-L65) / [VulkanUniformBuffer.java L84–130](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanUniformBuffer.java#L84-L130)

<a id="bh-25"></a>

### BH-25 — P1 — region upload失敗を握りつぶした後、chunkをbuilt済みとして確定する

**成立条件**: regionへのGPU upload中にallocation/copy等がRuntimeExceptionを投げる。

**コード上の経路**: uploadMeshesはregion単位で例外をcatchし、skipしたまま正常returnする。uploadRegionには旧meshを外してから新meshを確保する段階がある。一方、RenderSectionManagerはupload結果の成否を受け取らず、全resultのsection情報・translucency情報・build token・lastBuiltFrameを更新する。GPUにgeometryがないresultもbuiltとして扱われる。

**影響**: 地形が欠けた状態で自動retryされない。古いmeshの保全と新しいmetadataのcommitが分離している。

**修正方針**: sectionごとにupload outcomeを返す。旧meshを保持したままreplacementを用意し、成功したsectionだけmetadataをcommitする。失敗resultはretry/rebuildへ明示的に戻す。

**修正後の確認**: 複数section中の一つを失敗させ、そのsectionだけ旧mesh保持またはretryとなることを確認する。built frame/tokenは成功と一致させる。

**根拠**: [VulkanTerrainGpuBackend.java L6167–6178](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6167-L6178) / [VulkanTerrainGpuBackend.java L6540–6560](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6540-L6560) / [RenderSectionManager.java L728–760](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java#L728-L760)

<a id="bh-26"></a>

### BH-26 — P1 — 所有imageのlayout/初回clearをrecord時に確定し、未submitの破棄で戻さない

**成立条件**: frame/off-targetのbarrierやclearをrecordした後、submit前の例外でcommand bufferをresetして回復する。

**コード上の経路**: GllessFrameTargetはrecordAcquireでfirstAcquire/layoutを変更し、applyGameClearsはlatched maskやneverClearedを消費する。Pairのacquiredもrecord中にtrueになる。backendの例外回復はcommand bufferをresetしてrecordし直すが、これらのowner stateを戻していない。実imageはまだUNDEFINED/旧layoutであるのに、次のrecordがGENERAL/clear済みと考える条件が成立する。

**影響**: 異常回復時に初回transition/clearを飛ばす、oldLayoutを誤る、消費したclearが実行されない。正常frameだけでは見えない。

**修正方針**: 所有imageもcommand recording tokenごとのpending stateを持つ。queue submit成功時にだけconfirmedへcommitし、未submitでreset/abandonしたらlayout・初回clear・clear eventをrollbackする。

**修正後の確認**: acquire/clear後、submit直前に故障を入れ、次のrecordが元のlayoutから必要barrierを再発行し、clearを失わないことを確認する。

**根拠**: [GllessFrameTarget.java L430–435](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L430-L435) / [GllessFrameTarget.java L483–547](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L483-L547) / [GllessFrameTarget.java L874–885](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java#L874-L885) / [VulkanTerrainGpuBackend.java L5149–5168](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5149-L5168)

**限定・補足**: foreign texture wrapperにはbegin/commit/abandon ledgerが既にある。そこまで未実装とする指摘は撤回し、今回の対象をowner側GllessFrameTarget/Pairに限定した。

<a id="bh-27"></a>

### BH-27 — P1 — 通常のmesh upload/presentでもqueue全体の待機を繰り返す

**成立条件**: 地形meshをGPUへ送る通常経路、またはframe imageをswapchainへpresentする通常経路。

**コード上の経路**: arenaのwriteはContext.upload→recordStagingCopy→beginCommands/endCommandsへ進む。beginCommandsはvkQueueWaitIdle、endCommandsもsubmit後vkQueueWaitIdleを呼ぶ。N回このcopy経路を使えば、少なくとも2N回の全queue待機を要求する。present側もbeginCommandsを使い、submitWithSemaphoresはその場でfence completionを待つ。immediate flushにも同期waitがある。

**影響**: CPUとGPUの並行処理を失い、chunk再構築やframe内の細かいwindow切り替えのたびにstallを増やす。コード上の直列化は確定だが、FPS低下率や現在の実プレイの全ラグ原因は測っていない。

**修正方針**: frame/transfer単位にuploadをbatchし、staging ringとfence/timelineによる退役を使う。queue idleはshutdown・明確な再構築等に限定する。少数frameを同時進行できるpresent ownershipを設計する。

**修正後の確認**: 通常frame中のqueue/device idle回数を原則0へし、chunk upload数を増やしてもsubmit/wait回数がpayload数に線形増加しないことを確認する。

**根拠**: [VulkanTerrainGpuBackend.java L6670–6685](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6670-L6685) / [VulkanContext.java L612–624](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L612-L624) / [VulkanContext.java L1415–1422](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L1415-L1422) / [VulkanContext.java L317–327](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L317-L327) / [VulkanContext.java L552–570](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L552-L570) / [VulkanContext.java L434–449](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java#L434-L449) / [Celeritas: MappedStagingBuffer.java L63–104](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L63-L104)

**限定・補足**: Celeritasの実stagingはpending copyをまとめ、ring/fenceで再利用を管理する。低水準APIへの置き換えだけで、その利点が自動的に継承されるわけではない。

<a id="bh-28"></a>

### BH-28 — P2 — immediate groupごとに2つのGPU buffer/memoryを新規確保する

**成立条件**: 非cacheのimmediate描画でmatrix、state、texture、target等が変わり、groupを作る。

**コード上の経路**: group作成時に毎回new VulkanDynamicMeshする。constructorは初期64KiBのvertexと32KiBのindexについてVulkanHostBufferを作り、それぞれVkBuffer/device memory/mapを確保する。group寿命後に解放する設計で、shared upload ring/suballocationになっていない。

**影響**: UIや小さいdrawの多いframeでnative allocation/map/deleteの数が増える。初期データ容量だけでもgroupあたり合計96KiBを要求する。これはVRAM常駐量や実測frame timeの断定ではない。

**修正方針**: frameごとのhost-visible ringにvertex/indexをsuballocateし、offsetでdrawする。fence後に一括再利用する。persistent model mesh cacheは維持する。

**修正後の確認**: 多数GUI drawを行っても、新規VkBuffer/vkAllocateMemoryがgroup数に比例しないことを確認する。ring不足時のgrowも旧frameの所有を守る。

**根拠**: [VulkanTerrainGpuBackend.java L4079–4090](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L4079-L4090) / [VulkanDynamicMesh.java L63–64](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L63-L64) / [VulkanDynamicMesh.java L115–136](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java#L115-L136) / [VulkanHostBuffer.java L105–157](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanHostBuffer.java#L105-L157)

**限定・補足**: cache済みmodel meshはborrow/reuseする経路がある。「全entityの全partを毎draw新規確保する」とまでは言わない。

<a id="bh-29"></a>

### BH-29 — P2 — normal付きformat 8/9/10を受理するが、native texture shaderがnormalを使わない

**成立条件**: POSITION_TEX_NORMAL等のformat 8/9/10でlighting有効のdrawをする。

**コード上の経路**: 対応factoryはstride/trailing bytesを変えるが、vertex attributesはposition/UV/色だけでnormalを定義しない。選ぶshaderもunlitのposition/tex/color familyのまま。backendはlighting payloadを作るので受理と描画成功のcounterは進むが、照明計算に使われない。format 9等のfog用payload領域へlight blockを置く分岐もあり、uniformの意味がfamily間で揃っていない。

**影響**: normal情報があるのに平坦な明るさになる。mod/TESR等の既知vertex format互換を受理件数だけで判断すると取りこぼす。

**修正方針**: native draw familyごとにvertex semanticsとuniform ABIを定義する。normalを使うlit variantを設け、fogとlightingを同じbyte領域の代替用途にしない。

**修正後の確認**: 同じgeometryをformat 4と8/9/10で渡し、光源方向だけ変えたときの応答を比べる。lighting offも確認する。

**根拠**: [VulkanImmediateTexturePipeline.java L197–214](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L197-L214) / [VulkanImmediateTexturePipeline.java L595–635](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java#L595-L635) / [VulkanTerrainGpuBackend.java L5828–5840](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5828-L5840) / [VulkanTerrainGpuBackend.java L9974–9997](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L9974-L9997)

**限定・補足**: format 4のItem lit経路やcapture済みmodelまでnormal非対応という意味ではない。対応済みのfamilyと未使用のfamilyを分けた指摘。

<a id="bh-30"></a>

### BH-30 — P2 — Item/Block/Particleのnative fragmentにworld fogが揃っていない

**成立条件**: fogのあるworldでItem/Block/Particle familyを描く。GUI描画ではworld fogを適用しないため、GUIだけでは検出しづらい。

**コード上の経路**: Itemのlit/unlitはいずれもIMMEDIATE_TEX_COLOR_FRAGMENTを使う。このfragmentにはfog計算がない。Block/Particleのfragmentにも対応するworld fogがない。backendが拡張payloadへfogを書ける場合でも、このfamilyは128 byte rangeやfogを読まないshaderなので、状態伝達だけでは絵に反映しない。

**影響**: 地形はfogに沈む距離でentity/item/particleだけが別の色・濃さで残る。通常の地形fog成功をゲーム全体のfog完了とするのは不適切。

**修正方針**: world/GUIというsemanticを明示し、全world familyに共通fog blockと計算を揃える。lit/unlitとfog有無を独立した条件にする。

**修正後の確認**: 同じ距離のterrain、held/dropped item、model、particleを並べ、LINEAR/EXP/EXP2とfog offで距離応答を照合する。

**根拠**: [VulkanImmediateItemPipeline.java L349–353](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L349-L353) / [immediate_tex_color.frag L1–40](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/immediate_tex_color.frag#L1-L40) / [immediate_block.frag L1](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/immediate_block.frag#L1) / [immediate_particle.frag L1](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/immediate_particle.frag#L1) / [VulkanImmediateItemPipeline.java L53–58](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateItemPipeline.java#L53-L58)

<a id="bh-31"></a>

### BH-31 — P2 — inverse model-viewで光源だけを正規化し、非一様scaleでnormal lightingが違う

**成立条件**: lit mesh/modelに非一様scaleやshearがかかる。rigid transformや一様scaleではこの差が隠れる。

**コード上の経路**: lightParamsはlightをinverse model-viewでobject spaceへ戻してnormalizeする。shaderはobject normalをnormalizeしてそのlightとdotする。これは、normalをinverse-transposeでeye spaceへ移してnormalizeし、eye-space lightとdotする計算と非一様scaleで一致しない。M=diag(2,1,1)、N=(1,0,0)、L=(1,1,0)/sqrt(2)なら、正規化normal matrix方式は約0.707、現方式は約0.447となる。

**影響**: 非一様scaleのモデルに不自然な照明差が出る。個々のentityへの係数補正では解決しない。

**修正方針**: normal matrixとeye-space lightをshaderへ渡すか、同値の正しい計算を実装する。normalの正規化/RESCALE_NORMALをどう扱うかもnative familyの契約に含める。

**修正後の確認**: 上記の数値例、回転＋scale、負scale、shearを使い、期待dot値と一致することを確認する。

**根拠**: [VulkanTerrainGpuBackend.java L2489–2519](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L2489-L2519) / [immediate_tex_color_lit.vert L38–49](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/immediate_tex_color_lit.vert#L38-L49)

<a id="bh-32"></a>

### BH-32 — P2 — 地形atlas samplerが固定maxLOD 4で、mipmap設定と結び付いていない

**成立条件**: Video SettingsのmipmapLevelsを0または既定と異なる値へ変更する。既定のATLAS_MAX_LOD propertyは4。

**コード上の経路**: terrain用atlasSamplerForはproperty値を使った専用samplerを一度作り、maxLod=ATLAS_MAX_LOD、mipmapMode=LINEARとする。ゲーム側mipmap設定・source samplerのmaxLODを受け取らない。materialのmip biasは補助であり、LOD上限をゲーム設定へ揃える機構ではない。

**影響**: 設定変更後の遠方textureの見え方がCeleritas/vanillaの設定契約と一致しない。設定UIが残っていても、native samplingへ反映している根拠がない。

**修正方針**: 現在のatlas imageのmipLevels、初期化済みlevel、ゲームのmipmap設定からnative samplerの有効範囲を決める。変更/reload時にsampler generationを更新する。

**修正後の確認**: mipmap 0/1/4で同じ遠距離atlasを描き、sampling上限と更新経路を照合する。

**根拠**: [VulkanTerrainPipeline.java L257–258](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L257-L258) / [VulkanTerrainPipeline.java L801–829](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L801-L829) / [SodiumGameOptionPages.java L250–268](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.java#L250-L268) / [terrain_solid.vert L150–156](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/shaders/terrain_solid.vert#L150-L156)

**限定・補足**: texture側にmip chain生成は存在するため、「必ず未初期化mipを読む」という別の指摘にはしていない。問題は設定との不一致。

<a id="bh-33"></a>

### BH-33 — P2 — chunk fade-inの設定を残したまま、native uniformでは常時無効にする

**成立条件**: quality.chunkFadeInDurationを有効にして、chunkが新しく表示される。

**コード上の経路**: native writeUniformsはfade enabledを0、関連parameterを0に固定する。設定UIはchunkFadeInDurationを持ち、Celeritasの元rendererはsection ageをshaderへ渡す。UIの設定とVulkanの実描画が結び付いていない。

**影響**: 設定が機能しない。Celeritas由来の見た目の機能が移植で抜けている。

**修正方針**: section age/fade durationをnative uniformへ接続する。未対応の間は設定を有効として見せない。

**修正後の確認**: duration 0と有効値でchunk追加時のuniformと見え方が変わり、設定を戻せることを確認する。

**根拠**: [VulkanTerrainGpuBackend.java L7475–7479](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7475-L7479) / [SodiumGameOptionPages.java L218–231](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/forge122/src/main/java/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.java#L218-L231) / [Celeritas: DefaultChunkRenderer.java L105–119](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer.java#L105-L119)

<a id="bh-34"></a>

### BH-34 — P2 — legacy callbackのglTexEnvfがfloatのbit列をenum値として保存する

**成立条件**: legacy callback経路でglTexEnvf(target,pname,floatEnumValue)を呼ぶ。glTexEnviやglTexEnvfvとは異なる。

**コード上の経路**: legacyDispatchはA_FLOAT引数をfloatToRawIntBitsでlong slotに詰める。SEM_TEX_ENV_FはそのslotをそのままintへcastしてsetTexEnvIntへ渡す。8448.0f等の数値は8448ではなくIEEE754のbit値になる。近くのglTexParameterfはFloat.intBitsToFloat→Math.roundで正しくdecodeしており、この差が明確。

**影響**: texture environment/combine modeをfloat APIで指定する描画だけ、stateが違った値になる。FFM経路のABI安全性は別の未確認事項。

**修正方針**: 引数の型に従ってfloatをdecodeした後、既知のenum指定を整数化する。型付きstate APIへ集約し、raw slotとsemanticな値を混ぜない。

**修正後の確認**: glTexEnviとglTexEnvfで同じGL_MODULATE/GL_COMBINE等を設定し、保存値/query/model combine結果を一致させる。

**根拠**: [CeltiumGlStub.java L1022–1047](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1022-L1047) / [CeltiumGlStub.java L1155–1159](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1155-L1159) / [CeltiumGlStub.java L1178–1180](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1178-L1180)

<a id="bh-35"></a>

### BH-35 — P2 — GL4.6/GLSL4.60と関数pointerを広告し、未実装機能へmodを誘導する

**成立条件**: modやCeleritasがGL version/core function availabilityで機能を選ぶ。unknown symbolを問い合わせる。

**コード上の経路**: stubはOpenGL 4.6とGLSL4.60を返す。一方、shader/draw/compute等にNOOPまたは成功に見える返答が多数ある。addressForは未知のfunction idにも非null callbackを返し、dispatchはid<0を0返しで終える。GL4.x capabilityを根拠に、実装されないcore機能へ進む条件が作られている。

**影響**: 失敗が能力判定で止まらず、描画消失・未使用GL bufferの確保・意味のない成功判定になる。「stubの文字をVulkanへ変える」だけでは契約が直らない。

**修正方針**: native RenderCapabilitiesと起動互換用shimの能力を分離する。既知のゲームAPIにはnative familyを提供し、未知機能は明示的に非対応とする。単にversionを下げるなら、Celeritas/Cleanroomの能力選択も同時にnative化する。

**修正後の確認**: 未対応shader/compute/indexed drawの分岐を誤ってavailableと判定しないことを確認する。ゲーム/主要modの既知経路には必要な正しい能力を返す。

**根拠**: [CeltiumGlStub.java L727–751](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L727-L751) / [CeltiumGlStub.java L1056–1060](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1056-L1060) / [CeltiumGlStub.java L1695–1702](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java#L1695-L1702) / [MappedStagingBuffer.java L40–55](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java#L40-L55)

**限定・補足**: extension listを偽造しているとは言っていない。ここで問題にしているのはcore versionと非null pointerの広告。

<a id="bh-36"></a>

### BH-36 — P2 — Celeritasのmultidrawをnative側でcommandごとのvkCmdDrawIndexedへ分解する

**成立条件**: 一つのregion/passに複数draw commandが入る地形描画。

**コード上の経路**: CPUのCachedBatch/BatchAssemblerは維持されるが、Vulkan描画は全commandをfor loopしてvkCmdDrawIndexedする。CeleritasのDirectMultiDrawBatchは一回のmultiDrawElementsBaseVertex、Indirect版はmultiDrawElementsIndirectへ渡す。native側ではCPUからdriverへ記録する呼び出し数を集約する部分が継承されていない。

**影響**: メッシュ・culling・command mergeは残る一方、地形のnative command記録コストがcommand数に比例する。Celeritasからの性能退行候補であり、実測で常に遅いとまでは断定しない。

**修正方針**: Vulkan indirect command bufferでregion/passのdrawをまとめる。multiDrawIndirect等のdevice featureを確認し、非対応時には同じnative rendererの単一draw経路へ戻す。mesh shaderは必須にしない。

**修正後の確認**: 同じ可視region数でCPUのnative draw記録回数とGPU結果を比較する。透明sort・baseVertex・index sourceを保持する。

**根拠**: [VulkanTerrainGpuBackend.java L6976–6989](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L6976-L6989) / [VulkanTerrainGpuBackend.java L7066–7078](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L7066-L7078) / [Celeritas: DirectMultiDrawBatch.java L78–82](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/gl/device/DirectMultiDrawBatch.java#L78-L82) / [Celeritas: IndirectMultiDrawBatch.java L94–100](https://github.com/GTNewHorizons/Celeritas/blob/989ebf2e52ceed052ec112a569e0c1d172095756/common/src/main/java/org/embeddedt/embeddium/impl/gl/device/IndirectMultiDrawBatch.java#L94-L100)

<a id="bh-37"></a>

### BH-37 — P1 — 追加差分の一括sample transitionが、直後にattachする画像もREAD_ONLYへ変える

**成立条件**: 非0 off-target FBOのcolor textureがpublishedFrameTexturesに登録済みで、同じFBOへ次のimmediate passを開始する。sampling drawがあるかどうかは問わない。

**コード上の経路**: 追加版recordImmediateOnlyはchain imageをensureし、published画像をGENERALへ戻す。その後、render pass開始直前にceltium$transitionPublishedFrameTextures(false)を無条件で呼ぶ。このhelperは全published画像をSHADER_READ_ONLY_OPTIMALへ移す。今からchainPair framebufferへattachする同じ画像も含む。直後のrender passはinitialLayoutとsubpass attachment layoutがGENERALを要求するので、先に戻した状態を自分で崩す。

**影響**: 非main passの開始でlayout契約が不一致になる。描画破損、driver依存挙動、validation errorに結び付く条件がコードだけで成立する。実際のVUID発生をこの監査で観測したという意味ではない。

**修正方針**: 次passでsampleするread setとattachするwrite setをnative image単位で求め、target別にtransitionする。現在のattachmentを一括READ_ONLYへ動かさない。feedback read/writeが本当に必要なら通常の別pass/別imageへ分ける。

**修正後の確認**: published済みFBOへ二回drawし、beginRenderPass時点でattachment layoutがGENERALであることを確認する。無関係なpublished画像を触らず、mainでsampleする場合だけREAD_ONLYにする。

**根拠**: [追加差分: VulkanTerrainGpuBackend.java L5654–5671](https://github.com/souta-lab/celtium/blob/2f3f1018e7ae1da37efe2597a17c18ade02a8b31/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5654-L5671) / [追加差分: VulkanTerrainGpuBackend.java L5728–5749](https://github.com/souta-lab/celtium/blob/2f3f1018e7ae1da37efe2597a17c18ade02a8b31/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L5728-L5749) / [追加差分: VulkanTerrainGpuBackend.java L8664–8674](https://github.com/souta-lab/celtium/blob/2f3f1018e7ae1da37efe2597a17c18ade02a8b31/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java#L8664-L8674) / [VulkanGlTexture.java L928–944](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java#L928-L944) / [VulkanTerrainPipeline.java L959–989](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainPipeline.java#L959-L989) / [VulkanFrameTarget.java L138–145](https://github.com/souta-lab/celtium/blob/a971006f6f24165a6b2e9603022ef9284554e1be/celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java#L138-L145)

**限定・補足**: foreign imageのledgerはVkImage単位で共有済み。この指摘はwrapperごとにlayoutを二重管理するというものではなく、「対象を区別せず全画像を同じ用途へ遷移させる」ことに限定する。

## Celeritasの実コードと比べた継承状況

`common`と`forge122`の取得ファイルをbyte比較した。変更22、同一388、Celtium側の追加36。Javaだけでは変更18、同一374、追加36。これはsnapshotの共通/追加ファイル比較であり、Git全履歴や全プラットフォームの割合ではない。

| 領域 | 比較結果 | 評価 |
|---|---|---|
| chunk meshing/build buffers・scheduler | 大部分がbyte同一。Vintage側のdefault renderer選択には変更がある | geometryをentity別に再発明する設計ではない。mod hookとの意味の差は別途確認が必要 |
| section graph・可視判定 | 主な実装は維持。SectionGraphの変更はthread名等 | CPUで見えない領域を落とす利点は残る |
| BatchAssembler・CachedBatch | 元のcommand組み立て、cache、mergeを利用 | CPUでのbatch生成最適化は残る |
| face culling・透明ソート | 共通処理を利用。sorted passではface cullingを分ける条件もある | 「透明sortを丸ごと失った」とは判定しない。ただしindex bufferの選択にBH-02がある |
| Compact/VanillaLike vertex | encoder本体は保持、主な変更はdiagnostic等 | 圧縮/既存vertex設計の利点は残る。native arenaの単位変更でBH-01が入る |
| terrain shader | Celeritas material、lightmap、fog、mip bias等をnativeへ移している | 独自のGL bytecodeを毎draw解釈する方式ではない。設定接続にBH-32/33が残る |
| GPU arena/upload | GL arenaからVulkanArena/Bufferへ変更 | 単位・alignment、失敗時commit、同期にBH-01/25/27 |
| staging reuse | Celeritasはpending copy/ring/fence。native uploadは細かいsubmit+idle | 性能面の継承が不足。GL-lessに不要なGL stagingの構築/解放もBH-21で問題 |
| multidraw | Celeritasはbase-vertex/indirect multidraw。nativeはcommandごとのdraw | BH-36。CPU側cache維持とGPU側呼び出し集約の喪失は同時に成立する |
| immediate resource reuse | persistent model meshは再利用。通常groupはnew DynamicMesh | 対応済みcacheを捨てず、BH-28のallocationを別に解消する |
| world lifecycle | 元renderer/region deleteをbackend deleteへ置き換え | GPUだけでなくCPU/off-heapの責務を回収しきれていない。BH-21 |

したがって、「Celeritas由来の最適化を全部捨てた」は誤り。一方、「同じbatchクラスを使っているのでGPU送信も同じ効率」は成立しない。二つの層を分けて評価する必要がある。

## 汎用変換器化の判定

### native Vulkan rendererとして残す部分

- 地形はCeleritasのmesh/region/material/culling/sortを利用したnative pipelineへ入る。
- immediateは既知のMinecraft vertex layout、primitive、matrix、texture、light stateを受け取り、決めたdraw familyへ送る。
- display list/model captureは既知のmodel quadをnative meshへ取り込む。cache/borrowed meshの再利用がある。
- postは既知programのSPIR-Vへ対応させる。PROGRAMSにはinvert/blit/blur/bits/entity_outline/color_convolve/spider等の固定対応がある。

### 危険な境界

`CeltiumGlStub`は広いGL function table、buffer/texture/texenv等の記録を持つが、多くはNOOPや固定queryである。任意GLSL・任意GL extension・任意draw contractを忠実に処理する完成した汎用変換器ではない。bound custom GL programを拒否するnative経路もあり、postもprogram whitelistに制限される。

そのため、標準のGlStateManager/Tessellator/BufferBuilderに乗るmodと、独自GL program/FBO/生のGL drawを使うmodは同じ互換範囲ではない。modを個体ごとに対応する前に、既知draw familyの状態・target・lifecycleを正すべきである。BH-03〜16、29〜31を直すだけでも、同じAPIを使う多数のcallerへまとめて効果が及ぶ。

今後は、ゲームが意味を持つ状態をnative DrawState/FrameTarget/TextureStateとして実装し、既存APIは薄い入口にする。未知GLに自動追従しようとしてstub tableを無制限に増やさない。「未対応のGL callを成功風に捨てる」ことも、互換性維持とは扱わない。

## 実プレイ報告との対応 — 原因の断定はしない

| 以前報告された症状 | 今回コードから関連を説明できる部分 | この監査の限界 |
|---|---|---|
| entity攻撃時の赤さがない | unit別binding、blend/texenv、snapshotの整合性（BH-04〜07/34） | そのentityの現在の赤flash pathを実行して確認していない。単一原因としない |
| held blockが暗い | live light state、normal format、normal matrix（BH-05/29/31） | lightmapの内容や現在の実画面は判定していない |
| 設定画面の背景/色/clipがおかしい | scissor、color mask、viewport、FBO clear（BH-09/10/13〜16） | 現在のすべてのUIの再現を見たわけではない |
| loading画面が出ない/画面が固まる | off-target alias、clear、例外回復、追加layout変更（BH-14〜16/26/37） | driver timeoutをログから確認していない。恒常的なfreezeの直接原因とはしない |
| 非常にラグい | per-copy queue idle、同期present、group allocation、multidraw分解（BH-27/28/36） | frame time spikeの実測や、CPU/GPUの現在の支配率は対象外 |
| driver欄がstub GL | 真のnative能力とGL shimの広告の分離（BH-35） | 追加の表示修正が現在入ったかどうかと、描画能力の契約は別に扱う |
| 剣の向き、巨大drop、item占有率 | 今回は未修正と数えていない | 過去の症状を、そのまま現在のコードのbugとして再登録していない |

## 修正をまとめる順序

個々のentityや画面に定数を足す修正ではなく、次の責務ごとに直す。実装はこの監査では行っていない。

| 修正束 | 関連ID | 一つに揃える契約 | 完了条件 |
|---|---|---|---|
| 1. targetとlayoutの所有 | BH-14〜16/26/37 | FBO identity/size、clear順序、read/write set、record/submitのcommit | 複数FBO、再bind、resize、fault時にもattachment/sampleのlayoutと内容が一致 |
| 2. draw snapshot | BH-03〜08/10/11/29〜31/34 | alpha、color、unit、tex matrix、lighting/fog、blend、raster stateのimmutable snapshot | group化の有無とflush位置で見え方が変わらない |
| 3. viewportとclip | BH-09/12/13 | viewportの写像、scissor clip、portableな線描画 | settings clip、narrow/full切替、2px選択線がdeviceに依存せず機能 |
| 4. device/surface contract | BH-17〜19/35 | push/UBO、present可能device/queue、surface capabilities、truthful native capability | 必要条件を満たす全candidateから選び、unsupported機能を成功扱いしない |
| 5. resource/upload transaction | BH-01/02/20〜25 | element単位、index source、作成rollback、retirement、built commit | grow/failure/unload/reload後も所有数とgeometryが整合 |
| 6. 設定の接続 | BH-23/32/33 | sampler世代、mipmap、fade | UIの設定がnative stateを変え、戻せる |
| 7. GPUに仕事をまとめる | BH-27/28/36 | upload ring、frame slots、indirect command、fence退役 | 通常frameのglobal idleとper-group allocationを除き、Celeritas CPU cacheを保つ |

BH-37は追加修正による新しい問題なので、第一束の中でも先に確認する。性能最適化の前にtarget・状態・lifetimeを揃えないと、非同期化で既存の欠陥が広がる。ただし正しさの修正と並行して、ring/retirementに必要な所有設計は先に決められる。

## 修正後に行う最小確認行列

本監査では実行していない。後続の実装担当が、個別sceneだけでなく契約の組み合わせを確認するためのもの。

| 軸 | 必須ケース | 主に拾う問題 |
|---|---|---|
| vertex/arena | stride 24/32/96、exact fit、fragment/grow、baseVertex 0/正 | BH-01/02 |
| batching | 同じmeshで色/光源/tex matrix/fogだけ変更、flush位置変更 | BH-03〜07/29〜31 |
| target | main、0/default、非main A/B、異サイズ、resize/delete、再clear | BH-14〜16/37 |
| viewport/clip | full→narrow→full→outside、入れ子scissor、GUI scale | BH-09/13 |
| raster | 2px選択枠、depth-only雲、damage overlay、cull、separate alpha | BH-07〜12 |
| device/WSI | 128 byte push、別present family、第二GPU、固定image count/alpha/usage条件 | BH-17〜19 |
| lifecycle | world終了、同world reload、sampler世代変更、descriptor上限 | BH-20〜24 |
| fault | create/allocate/bind/map、upload途中、record済み/submit前の破棄 | BH-15/20/24〜26 |
| performance contract | queue idle回数、submit数、allocation数、draw記録数 | BH-27/28/36 |

ゲームとの比較には、通常CeleritasとCeltium GL-lessで同じworld・設定・camera・resource packを用いる。GL bridgeを第三の合格基準として追加する必要はない。受理件数、Vulkan呼び出し数、見た目の一致、resource寿命、性能をそれぞれ別に確認し、一つのcounterを全体の完了率へ換算しない。

## 保留事項 — 37件には含めていない

1. **sampler即時destroyとGPU pending使用。** confirmSamplerState/setSamplerReachは古いsamplerをdestroyする。GPU完了の前に呼ばれるかは、呼び出しタイミングとsubmission ownershipを追加追跡しないと断定できない。descriptor退役設計では扱うべきだが、現在の通常経路のuse-after-freeと断定していない。
2. **uniform ringのrecord中grow。** ensureCapacityで旧bufferをdestroyするが、beginCommandsでcursorをresetし、先に複数pass分をreserveする処理がある。通常の上限を越えてgrowへ到達する条件を立証していない。guard/assertと上限設計の確認対象。
3. **FFM callbackのmixed float ABI。** generic SAM/MethodHandleとLWJGLのnative signatureが完全に一致するかは、生成されたbindingと実際のABI側まで確認が必要。BH-34は型decodeのコード上の誤りだけを数えた。
4. **transfer後のbuffer visibility/barrier。** queue idleによる同期とVulkanのmemory dependencyを区別した検証が必要。現在のqueue idleを除く際にはtransfer→vertex/indexのbarrierを設計する。未確認のspec記憶だけで独立bugにはしていない。
5. **VintageBlockRendererのdefault選択。** 元のblock dispatcher pathから自前rendererをdefaultにした差がある。dispatcherへ注入するmodとの意味の違いはあり得るが、実modのhookを比較していないので「主要modが壊れる」と数えていない。
6. **診断collectionの増加。** backendのdiagnostic蓄積やcallback censusは長寿命で増える箇所がある。重複抑制・上限・既定設定をさらに揃える余地があるが、現在の支配的memory消費を観測していない。
7. **極小fan/strip。** 頂点1で(vertexCount-2)×3のarray確保が負になり得る。OpenGLの不足頂点は絵を出さないため、主要な可視bugと同列には数えなかった。入力契約のcleanup候補。

## 誤検知を避けた項目

- foreign imageのlayout ledgerはVkImage単位で共有され、record/commit/abandonもある。wrapper aliasの二重ledger不具合とはしていない。
- PostChainの内部target管理・UBO fallbackは存在する。全postを単一FBO/208 byteのみとする指摘はしていない。
- DynamicMesh constructorはvertex確保後にindex確保が失敗した場合、vertexをdeleteする。BH-20のrollback不足をこのconstructorにまで広げていない。
- wide lineは送信前に拒否するので、「featureを有効にせず2px lineWidthをVkへ送る」というvalidation違反とはしていない。
- 透明パスのface cullingは別扱いになっている。sorted geometryを必ずCPU cullするという指摘はしていない。
- textureのmip chain作成・level管理は存在する。sampling設定未接続と未初期化imageを混同していない。
- system identity hashやdescriptor keyの理論上のcollisionだけを、実際の不具合数へ足していない。
- fallbackへ戻ったこと、drawが受理されたこと、Vulkan driverが選ばれたことを、正しいpixelが出ることと同一視していない。

## 引き継ぎ上の注意

このMDは固定ソースへの静的監査である。現在のHEADで修正する前に、各IDの該当責務がその後変わっていないかを確認する。特にBH-23は追加差分を踏まえて既に指摘範囲を縮めた。BH-37は追加差分に対する指摘で、元snapshotにはない。

修正後は「何を受理したか」だけで閉じず、各IDに記した契約・失敗時の挙動・Celeritasとの差の確認結果で閉じる。汎用GL translatorへの拡張や影mod対応の先行実装は、この監査の修正方針に含めていない。
