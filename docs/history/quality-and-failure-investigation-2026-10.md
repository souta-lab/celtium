# 画質・失敗経路の調査経緯（2026-10、Celtium現状表の履歴）

本書は `docs/plan.md` §7.5 に追記されていた調査経緯を移したものである。**現在の判定ではない**。
現在の到達点・受入は `docs/plan.md` を参照。各roundの測定・否定・訂正がそのまま残っている。

`scripts/run-gl-comparison.sh amd r418` で pinned upstream Celeritas (GL) と現行Celtiumを**同一flag・同一fixture・GPU**で
順に実行し、`scripts/compare_validation.py` で比較した（p32-glref-r418 / p32-celtium-r418）。

| 項目 | 結果 |
|---|---|
| run verdict | 両方 exit 0 / validationClean true / messages 0 / fixture PASSED |
| framebuffer・camera・pass数 | 一致（solid/cutout_mipped/translucent 各62） |
| 画像 | **645,651ピクセル差（70.06%）・最大チャンネル差209** |
| 領域別 | **空 ratio 1.000（完全一致）**、地平線 ~1.0、**地形 0.595 / 0.580 / 0.636**（赤/緑/青） |

症状は「地形が一様に約0.6倍暗い」。切り分けの結果:

- **lightmap mirrorは正しい**: 同一run内で GLの実lightmap（`gl-lightmap-live.png`）と mirror（`gl-texture-12-16x16.png`）を比較。
  capture時点のGLは sky15/block0 = (250,250,250)（正午）、mirrorの**最後のアップロード**も (250,250,250) で一致。
  最初のアップロードは (155,155,187) で、これは**実行初期（朝）の値**だった（mirrorのdumpは最初の1回だけ書く仕様のため、
  以前の「mirrorがstale」という読みは誤りだった。`-Dceltium.vulkan.dumpTexturesEvery=true` を追加して確定させた）。
- **面シェーディング表は上流と完全一致**: `common/.../model/light/smooth/AoNeighborInfo.java` は pinned checkout と diff なし
  （0.6/0.8/0.5/1.0）。
- **shaderは `v_Color = a_Color * lightColor` のみ**（`terrain_solid.vert` 227行）。追加の係数は無い。
- したがって **0.6 ≈ 朝のlightmap 0.61 × 正午の0.98** となり、**メッシュ構築時にlightmap値が頂点色 `a_Color` へ
  焼き込まれ、以後chunkが再構築されないため朝の明るさのまま固定されている**という説明が測定値と一致する。

**「焼き込み」仮説は実験で反証された**（`p32-reload-r418`, `-Dceltium.validation.reloadAtTick=120`）:
正午固定後に `renderGlobal.loadRenderers()` で全chunkメッシュを再構築しても地形の帯は
**79.6 / 85.5 / 75.4 で完全不変**（reload無しと同一）。したがって頂点色へのlightmap焼き込みではない。

追加の測定:

| 指標 | 値 |
|---|---|
| 回帰 fit（地形帯, 緑） | `y ≈ 0.42x + 20`、残差 sd ≈ 19-20（単純な乗算ではない） |
| 高周波エネルギー（地形帯, 緑） | 参照 7.1 / 候補 **5.5**（候補が約22%滑らか＝mip/補間の差） |

**このラウンドで反証した候補**（すべて実測つき）:

| 候補 | 反証 |
|---|---|
| lightmap mirrorがstale | mirrorの最終アップロード = GLの実lightmap = (250,250,250)（`-Dceltium.vulkan.dumpTexturesEvery=true`） |
| 頂点色へのlightmap焼き込み | `reloadAtTick=120` で全chunk再構築しても帯は 79.6/85.5/75.4 で不変 |
| 生成mip鎖が暗い | `-Dceltium.vulkan.mirrorGlMips=true` でも近傍帯は同一・全画面diffも不変 |
| AO/面シェーディング表 | `AoNeighborInfo.java` は pinned checkout と diff なし |
| fogが暗い | fog色は明るい青灰 (0.552,0.640,0.789)、range 96-128ブロックで近傍帯に効かない |
| 地形が未描画(void) | 暗部は778色のテクスチャ（同一座標で参照と内容が対応） |
| atlas mirrorが暗い | GLの実atlas（fixtureが `gl-atlas-live.png` をcapture時に出力）とmirrorを比較: **平均が完全一致** (82.6,76.7,63.8)、差は56/65536テクセル（アニメsprite分）のみ |
| lightmap除数 /256 vs /16 | 過去roundで修正済み。shaderコメントの実測でも「サンプルを1.0に固定して画素が変わらない」＝lightmapは支配要因ではない |

**残る手がかり**: 同一座標での比が **0.42〜0.65 と面ごとに変動**する（単純な乗算ではない）。
面シェーディング係数の適用が参照と異なることを示す。

**頂点データの実測（`-Dceltium.vulkan.vertexColourSamples=N` を追加）**: 地形はvanilla 32バイトlayout（pos float3 @0、色 @12、UV float2 @16、light short2 @24、法線 @28）で、
- **alpha は全頂点 255**（shaderの `rgb *= alpha` は無効）✅
- 頂点色は明るい緑（solid平均 **136,158,108**、先頭 0xff59bd91）で、参照の暗さを説明しない ✅
- UV は sprite 座標として正常（例 0.125, 0.469 = 512atlasの 64,240）✅
- light short は `light×16` の byte（例 0xE0=224=sky14×16）。`/16`（現行）と `/256`（上流）は**同じテクセルに解決**するため、除数の違いは結果に影響しない ✅
- **`textureLod(...,0.0)` でbiasを無効化しても画像は不変**（mid 81.0→83.4、near 同一）→ mip/LODは否定 ✅
  （この実験が本当に反映されたかは、jar内SPIR-Vが診断ソースの手動コンパイルと**バイト一致**することで確認済み）
- 参照がlightmapを適用していることも確認（`CELERITAS_NO_LIGHTMAP` は `pass.hasNoLightmap()` の時だけ定義＝SOLIDでは未定義）✅
- **ビルド健全性の確認**: jar内 `terrain_solid.frag.spv` の SHA-256 が現在のソースの手動コンパイルと一致（`bc1e741d…`）＝shader編集は正しく反映される
- 頂点色(0.53,0.62,0.42)×lightmap(0.98)=(133,155,106) が**参照の実測(136.8,146.5,117.2)とほぼ一致**する一方、候補はその0.6倍。かつ候補の画素値は参照の輝度と単調対応しない（ref133→87、ref103→43）＝**テクスチャ内容そのものが違う**
- Atlas level0 の内容は GL の実atlasと平均完全一致 ✅。dumpに**オブジェクト同一性**を付けて確認した結果、GL texture 9 の mirror は**1つだけ**（obj29960629）で、それも GL の実atlasと一致（差35テクセル・最大5）✅。つまり terrain が bind する atlas の内容は正しい

**比較手法そのものの検証（重要）**: 同一ビルド・同一flagの2 runのスクリーンショットを比べると
**24.8%のピクセルが異なる**（最大チャンネル差180。別の組では1.7%・1.4%）。参照との70%差はこの
**run間ノンデターミニズム**を含むため、ピクセル一致率は受入指標として使えない。
一方で**帯平均は再現する**（候補の複数runが 79.6/85.5/75.4 で一致、参照は 125.0/135.4/117.6）ので、
「地形が約0.6倍暗い」は実在する差である。以後は**領域統計**で判定し、ピクセル一致は
決定性を別途示した場合だけ使う。

**因子の定量（shader診断・jar内SPIR-V一致を確認済み）**:

| 構成 | 地形帯 mid | near |
|---|---|---|
| 参照 (upstream GL) | 136.8, 146.5, 117.2 | 125.0, 135.4, 117.6 |
| 現行（色×alpha乗算あり） | 81.0, 84.5, 74.2 | 79.6, 85.5, 75.4 |
| **頂点色乗算を無効化** | **210.1, 209.8, 209.0** | **215.7, 216.2, 220.5** |
| **alpha乗算を無効化** | 80.8, 84.4, 74.2 | 79.6, 85.5, 75.4 |

- 「texture×lightmap」= 約212（ほぼ無彩色）。参照はその**0.61倍**、現行は**0.38倍**＝**差は頂点色の項だけ**で、現行は参照の0.62倍。
- **alpha乗算は無効**（400セクションすべて alpha=255、無効化しても不変）✅ → AOの二重適用ではない。
- 既存の `-Dceltium.vulkan.lightDiagnostics=true` は全書き込みの平均色を出すが、**ラベルが実際のチャンネル順と逆**（`r=`は実際にはA、`a=`は実際にはR）。集計は全パス・全フレーム混在なので地形の判断には使えない。**pass別に集計する拡張が必要**。
- **GL地形パスは製品構成ではない**: `-Dceltium.vulkanTerrain=false`（またはVulkan bindings無し）では
  `IllegalStateException: Celtium requires a Vulkan terrain backend, but none could be created: ...` で**明示的に停止**する
  （旧run scriptコメントの「GLへフォールバックしてそう言う」は古い）。以降のplayer null NPEはこの停止の二次症状。
  したがって同一buildでのGL/Vulkan A/Bは**この構成では取れない**。

**pass別集計（実装・実測済み）**: `-Dceltium.vulkan.lightDiagnostics=true` を**pass別**に集計するよう拡張し、
同時に**ラベルの誤りを修正**（intは `0xAABBGGRR` なので little-endian のバイト順は R,G,B,A＝`VK_FORMAT_R8G8B8A8_UNORM` と一致）。
実測: **pass1（solid）= (162,171,150,255)**、pass3（cutout_mipped）= (63,90,25,254)。
- solid の色は明るく、`212 × 0.635 ≈ 137` で**参照(125-137)とほぼ一致**する。
- 一方、現行の描画は 79.6（有効な乗算は0.369）。つまり**詰めた色(0.635)が描画に反映されていない**。
- shader内の他項はすべて無効化して確認済み: `rgb *= m_color.a`（alpha=255で不変）、
  `m_color += v_RdhFactor * correctionWeight`（無効化しても 81.1 vs 81.0 で不変）。
- **注意**: 帯平均は可視geometry（solid/cutout/translucentの混在、面の向き）に強く依存するため、
  「詰めた色」と「描画値」を帯平均で直接比べるのは不適切。**制御された単一quad実験が必要**。

**矛盾の確定（このターンの核心）**: 3点を実測で突き合わせた結果、**矛盾**が確定した。

| 層 | 実測値 | 状態 |
|---|---|---|
| mesher → バッファ | `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)`（新規stdout `CELTIUM_GPU_VERTEX`・既存readback経路） | **正しい**（緑のtint） |
| pipeline layout（実行時print） | `stride=32 module=terrain_solid_vanilla.vert colorOffset=12 colorFormat=0x25(R8G8B8A8_UNORM)` | **正しい** |
| fragmentが受ける `v_Color` | 地形帯で**ちょうど2値** `(26,26,26)` と `(255,255,255)`（95%/5%） | **2値＝異常** |

- 検証手順: (a) `fragColor = m_color.rgb`、(b) `fragColor = v_Color.rgb`（Rdh項の加算も排除）、
  (c) `v_Color = a_Color`（lightmap乗算なし）の3通りすべてで同じ2値。各段で**jar内SPIR-Vが診断ソースの
  手動コンパイルとバイト一致**することを確認済み。
- つまり損失は **頂点バッファと varying の間**（入力アセンブリ／属性記述／drawのvertexOffset）にある。

**このターンの追加実測（矛盾の掘り下げ）**:

| 計器 | 結果 |
|---|---|
| fragmentで**定数色**を出力 | 地形帯の**100%**がその定数＝terrain shaderが帯全体を描いている（診断の前提が正しい） |
| `v_Color = vec4(a_TexCoord, 0, 1)` | **81色の滑らかな変化**＝**varying経路自体は正常** |
| pipelineへ**提出済み**の入力状態（実行時print） | `stride=32 bindingStride=32 binding=0 attr1(location=1,binding=0,format=0x25=R8G8B8A8_UNORM,offset=12)`、blendは solid/cutout=false・translucent=true |
| 可視sectionの**先頭頂点32バイト**（upload時に採取） | `0000803f 0000c040 0000803f 2b3e11ff 0c02083f 1904d03e 03fc00e8 00000000` → pos(1,6,1)、**色=(43,62,17,255)の暗い緑**、uv(0.53,0.41)、light、rdh=0 |
| GPU保持値（readback） | `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)`＝正しい |

つまり **バッファのバイト・layout・提出済み入力状態・varying経路のすべてが正しい**のに、
`a_Color` だけが帯全体で2値（26,26,26)/(255,255,255) になる。次は
**属性を `VK_FORMAT_R8G8B8A8_UINT` として読み、生の整数をそのまま出力**して、
shaderが実際に受け取るバイトを正規化の曖昧さなしに確定させる。

**属性offsetプローブ（このターンの決定的な測定）**: `a_Color` の読み出しoffsetをプロパティで振り、
「offsetが効いているか」と「その場所のデータは何か」を分離した（地形帯の色数で判定）。

| offset | 色数 | 上位の値 | 解釈 |
|---|---|---|---|
| 0（位置float） | 225 | (0,0,63/80/78) | 位置のバイト ✅ |
| 8（compactの色offset） | 116 | (0,0,77/93/89) | 位置のバイト |
| **12（宣言された色offset）** | **2** | **(26,26,26) / (255,255,255)** | **描画頂点の色バイトが2値** |
| 16（UV float） | 457 | (50,34,162) 等 | UVのバイト ✅ |
| 24（light uint） | 3 | (1,28,0) / (1,252,0) | lightのバイト ✅（material=1の頂点） |

- **offsetは正しく効いている**（0/16/24は layout どおりのデータを返す）。
- しかし **offset 12 のバイトは可視頂点で2値**しかない。一方 offset 24 の light バイトは正常に変化する。
- 私が採取した「section #252 の先頭頂点」は material=3・色(43,62,17,255) だったが、可視頂点は material=1 で
  **別のsection**だった（`sectionFirstColor` は section 索引で pass 非依存に上書きされるため、以前の
  「可視sectionの色」という読みは**誤り**。ここに訂正する）。
- 既存readback（`baseVertex×stride` の位置をGPUから読み戻す）は最初のdrawについて
  `colour=0xff59bd91 light=0xe000fc01 pos=(0.0,4.0,0.0)` を返しており、**arena上のその頂点は正しい色**を持つ。
- readbackをdraw単位（最大8）に拡張したが、1フレーム内で複数slotを読む経路がまだ揃わず1件のみ。
  計器は `-Dceltium.vulkan.vertexDiagnostics=true` にゲートし、通常runでは一切走らない。

**原因判明（このターンの核心）**: mesh builderが実際に書いた色を**section別ヒストグラム**で出したところ、
決定的な事実が出た（`-Dceltium.vulkan.vertexColourSamples=N` の出力に `distinctColours` と `top=` を追加）。

| pass | section 252 の頂点色（上位） |
|---|---|
| solid | **0xff59bd91**（明るい緑）x1024、0xff808080 x1024 |
| cutout_mipped | 0xff113e2b / 0xff175239 / 0xff18563c / 0xff2fab77（緑系4種） |
| **translucent** | **0xff1a1a1a（=26,26,26 の暗灰）x1024、0xffffffff（白）x2048** |

- 診断画像で観測された2値 **(26,26,26) と (255,255,255) は translucent pass の頂点色そのもの**だった。
- translucent pass の alpha は **255（完全不透明）**。つまり**可視地面の上に不透明な暗灰色が描かれている**。
- 一方 solid pass の色 0x59bd91 から期待される値は `0.83(texture×lightmap) × 0.35 × 255 ≈ 74` で、
  通常描画の実測 **79.6 とほぼ一致**する＝**solid pass は正常**。
- GPU readback（draw別32slot）でも arena の頂点色は `0xff59bd91`（solid）/`0xff113e2b`（cutout）/
  `0xffffffff`（translucent, section 0）で、**arenaは正しい**。壊れているのは shader ではなく
  **translucent pass に載っているジオメトリとその色**。

**【訂正】前ラウンドの「translucent pass が不透明な暗灰色で地面を塗っている」は誤り**。
UV (0.875,0.28125) のatlas spriteを確認したところ **alpha 197 の青い半透明テクスチャ（水）** だった。
診断shaderが `alpha=1.0` を出力していたため、本来ブレンドされる水が不透明に描かれ地面を覆っていた
（診断アーティファクト）。translucent pass の中身は**正当な水**である。

**面の明るさ別の適用色（通常run ÷ 色乗算無効run で算出、参照側は ref ÷ 同run）**:

| 対象 | 候補の適用色 | 参照相当 | 比 |
|---|---|---|---|
| 明るい面（上面） | (0.405, 0.419, 0.385) | (0.703, 0.733, 0.661) | **0.57** |
| 中間 | (0.341, 0.366, 0.310) | (0.562, 0.613, 0.503) | **0.61** |
| 暗い面（側面） | (0.301, 0.309, 0.288) | (0.485, 0.502, 0.464) | **0.62** |

- 草地だけ (n=101,902): 参照 (132.4,145.3,114.6) / 候補 (84.1,89.9,75.8) = 比 **0.62-0.66**
- 水面だけ (n=8,621): 比 **0.56-0.58**
- **比が面の明るさに依らず一様（0.57〜0.62）** ＝ 面シェーディング差ではなく**一様な係数**である。
- したがって「どの一様係数か」に候補を絞れる: 頂点色の合成（tint/ shade の掛け方）、
  テクスチャサンプル、lightmap のいずれか。ただし **`VanillaLikeChunkVertex` はCeltium固有**で
  pinned upstream には存在しないため、**上流はこの色の参照にならない**（上流はcompact形式）。
  次は「vanilla GLの同じ面が出す色」と比べる必要がある。

**vanilla基準の測定（このターンの決定的evidence）**: fixtureに `-Dceltium.validation.vanillaReference=true` を追加し、
**terrain modを入れずに同じfixtureで同じframeを撮影**した（Celeritasが無いため通常の判定はFAILEDになるが、
`celtium-validation.png` は保存される）。

| レンダラ | mid帯 | near帯 |
|---|---|---|
| **vanilla Minecraft GL（真値）** | **(146.6, 157.1, 132.3)** | **(139.8, 151.4, 131.4)** |
| upstream Celeritas GL | (136.8, 146.5, 117.2) | (125.0, 135.4, 117.6) |
| Celtium Vulkan | (81.0, 84.5, 74.2) | (79.6, 85.5, 75.4) |

- upstream は vanilla の約93%でほぼ正しい ✅。**Celtium は vanilla の約0.56倍**で、**参照側の問題ではなく我々の欠陥**と確定。
- 「色乗算無効run」の band 215.7 は vanilla近傍の最終値139.8と比べて明るすぎ、vanillaの実効色 =
  139.8/215.7 = **0.648 ≈ solid passの詰めた色0.635** ✅ ＝ **mesherの色自体は正しい**。
- 一方、我々の実効色は 0.369。`0.6(側面shade) × 0.6 = 0.36` が実測と一致し、比が面の明るさに依らず
  一様（0.57〜0.62）である事実とも合う。**全faceに一様な≈0.6の余分な係数（AO/brightness項）が
  mesher側で掛かっている**可能性が高い。

**lightmap サンプルの欠陥を発見・修正（このターン）**: vertex shader で `v_Color = lightColor` を出力して
**shaderが実際にサンプルしている値**を測ったところ、地形帯で **(178,178,178)=0.698**（95%）と (223,223,223)=0.875（5%）
だった。lightmapの実データは row13=160 / row14=197 / row15=250 なので、**178.5 = row13,14 の平均**、
**223.5 = row14,15 の平均** ＝ **UVがテクセル境界に落ちている**（中心ではなく）ことが判明。

- terrain shader は Soduim 上流の式 `clamp(uv / 256.0, …)` をそのまま使っていた（256×256 lightmap 用）。
  Minecraft 1.12.2 の lightmap は 16×16 で、頂点の値は「レベル×16」なので正しい中心は `(uv + 8) / 256`。
- **同じプロジェクトの immediate shader は既に正しい式**（`(vec2(a_LightCoord) + 8.0) / 256.0`）を使っており、
  terrain 側だけが半テクセルを欠いていた。
- 修正: `terrain_solid.vert` と `terrain_solid_vanilla.vert` の両方を `(uv + 8) / 256` に統一。

| レンダラ | mid帯 | near帯 |
|---|---|---|
| vanilla Minecraft GL | (146.6, 157.1, 132.3) | (139.8, 151.4, 131.4) |
| upstream Celeritas GL | (136.8, 146.5, 117.2) | (125.0, 135.4, 117.6) |
| celtium 修正前 | (81.0, 84.5, 74.2) | (79.6, 85.5, 75.4) |
| **celtium 修正後** | **(88.9, 93.3, 82.7)** | **(88.9, 95.6, 84.1)** |

効果は **+10〜12%**（vanilla比 0.553 → 0.606）で、**実在する修正だが残差はまだ大きい**。
run verdict は exit 0 / validationClean / fixture PASSED。

**texure UVとshadeの検証（このターン・どちらも否定）**:

| 実験 | 結果 |
|---|---|
| texture UVを半テクセルずらす（`v_TexCoord + 0.5/512`） | mid 88.9 → 86.6、near 88.9 → 88.8、HF 6.2 → 6.5 で**ほぼ不変** → UV位置は原因ではない |
| `-Dceltium.terrain.noShade=true`（diffuse項を1.0固定） | **完全に同一**（88.9/93.3/82.7 が不変）→ `VintageDiffuseProvider` は**可視地形の色に寄与していない**（shadeはquad色に焼き込まれている）＝shade二重適用説は否定 |

**残差の定量（lightmap修正後）**: vanilla 146.6 / upstream 136.8 / celtium 88.9（mid帯）。
- 我々の「色乗算無効run」= 215.7（＝**texture単体**。lightmapは v_Color 経由なので色乗算を切ると一緒に切れる）
- vanilla 139.8 = tex × colour × lm とすると、tex ≈ 216（我々と一致 ✅）、lm は修正後は同一 ✅
- よって **我々の頂点色は vanilla の一様な約0.58〜0.62倍**（面の明るさ別 0.57 / 0.61 / 0.62）
- **欠陥は「色の合成」（quad色）にあり**、texture・lightmap・diffuse provider はいずれも除外済み

**色の出所を確定（このターン・重要な訂正）**:

1. pipeline経路の色合成は `VintageBlockRenderer:215` の
   `ChunkColorWriter.EMBEDDIUM.writeColor(tint, light.br)` で、**EMBEDDIUM は brightness を RGB に掛け算**する
   （`SEPARATE_AO` は alpha に入れる）。我々の実測 alpha=255 はこれと整合する。
2. しかし **その経路は実際には使われていない**: `light.br` のヒストグラムを仕込んで1 run回したが
   **出力が1行も出なかった**（＝`recordBrightness` が一度も呼ばれない）。
3. 実際の経路は `VintageChunkBuildContext.copyBlockData` で、**バニラの `BufferBuilder` の頂点データを
   そのままコピー**している（`vertex.color = memGetInt(ptr + 12)`、`vertex.light = memGetInt(ptr + 24)`、無加工）。
   → **我々の頂点色は vanilla の頂点色そのもの**であり、**色データは0.6倍差の原因になり得ない**（前ラウンドの
   「色の合成が原因」という見立ては誤りだったので訂正する）。
4. lightmap修正（`(uv+8)/256`）は MC の慣習 `level/16 + 0.5/16` と**一致**する正しい式であることも確認した。
5. Minecraft 1.12.2 の地形描画は**固定機能パイプライン**（2テクスチャユニット×`gl_Color`、shaderファイル無し）で、
   最終色 = tex × lightmap × colour。式の形は我々の shader と同じ。

**因数分解の完了（このターンの測定）**: 各因子を個別に切って寄与を測った。

| 実験 | 結果 | 解釈 |
|---|---|---|
| lightmapを1.0に固定 | 88.9 → **93.2**（mid）/ 93.9（near）＝**+5%のみ** | lightmapの寄与は小さい。修正後は既に≈0.98で正しい |
| `v_Color` をそのまま出力（テクスチャのalphaは保持して水のアーティファクトを回避） | 帯平均 **(102.4, 113.6, 87.0) = 0.445**、2645色 | 適用色はvanillaデータのpacked mixと一致＝**色の適用は忠実** |
| texture単体（色乗算無効run） | 215.7 | テクスチャも一致 |
| vanillaのlightmap内容（GL実テクスチャをdumpして比較） | **16行すべて完全一致** | lightmapは除外 |

- 我々の描画は**入力と整合**している: `0.445(色) × 0.98(lm) × 215.7(tex) ≈ 94` ≈ 実測 88.9 ✅
- 一方 **vanillaの146.6は同じ入力からは説明できない**（まるで約1.5倍多く掛けている）✗
- データ（頂点色・テクスチャ・lightmap）はすべて同一で、**差分は「適用」側にのみ残る**

**画素対応フィットによる切り分け（このターンの決定的結果）**:

| 比較 | フィット | log rms |
|---|---|---|
| vanilla → upstream（GL同士） | `upstream = 1.372 × vanilla^0.916` | **0.013** |
| vanilla → celtium | `celtium = 2.370 × vanilla^0.727` | 0.055 |

- **GL同士は指数0.916・残差1.3%でほぼ一致**（upstreamはvanillaより約7%暗いだけ）＝比較基盤は健全。
- 我々は**指数0.727の輝度依存圧縮**を示す（明るい画素ほど比が小さい: 0.55 → 0.46）。

**項別の除外（すべて実測）**: fog off → **画像が完全に同一**／LOD 0 → 指数0.722で不変／mipBias −2 → +3.6%・指数不変／
lightmap 1.0 → +5%のみ／shade off → 完全同一／UV半テクセル → ほぼ不変。**いずれも説明にならない**。

**画像の目視（新しい証拠）**: vanillaではブロック境界の白い線が**明るい**のに、我々の画像では**同じ線が暗く滲み、
遠景に強いモアレ状の帯**が出る。＝**最も明るい特徴を最も暗く描いている**（指数0.727の正体）。

- コード側: terrain sampler は `maxLod(1.0)`（level 0〜1のみ）＋LINEAR。周辺コメントの「0.16-0.28%一致」は
  **過去の別状態の測定**であり現在の証拠に使わない（goal の警告どおり）。

**upstreamとの対応画素比較（このターン）**: 画像を分離して測った（upstreamの明るい格子/地面を基準に分類）。

| 対象 | vanilla GL | upstream GL | celtium | celtium/upstream |
|---|---|---|---|---|
| 格子（明るいブロック） | (192.0,199.8,187.5) | (171.9,178.7,167.8) | **(108.7,112.6,106.3)** | **0.632** |
| 地面（暗いブロック） | (113.6,119.4,105.5) | (101.5,106.7,94.4) | **(69.8,72.9,65.5)** | **0.688** |

- 差は**格子だけでなく全体に一様な約0.66倍**（以前の「格子だけ暗い」という見立ては誤り。コントラストがやや悪いだけ）。
- **松明**: upstreamの水平帯で最も明るい画素は (251,241,194)（暖色の炎）だが、celtiumは (201,204,196) の灰色。
  → **cutout/炎（アニメーションテクスチャ）が正しく描かれていない**。これはplan優先順位4の
  「sprite animation未移植」と一致する**独立した欠陥**。
- `-Dceltium.terrain.forceCompactFormat=true` のA/Bは**レイアウト不一致で青い画像**になり不成立。
  ただし**その壊れた画像でもfixtureは exit 0 / validationClean / PASSED** だった（fixtureのverdictは画像の
  正しさを検出しないという事実の実例として記録）。

**機構の確定（このターン・重要）**: `ChunkBuilderMeshingTask` は mod 自身の pipeline（`VintageBlockRenderer`）と
vanilla の `BlockRendererDispatcher` の両方を呼び分けるが、`writeGeometry` に仕込んだbrightnessヒストグラムが
**一度も出ない**＝**全ジオメトリが vanilla dispatcher 経由**で作られている。したがって我々の頂点色は
**MC自身が焼いた色**であり、`VintageBlockRenderer` の `tint × light.br` は**使われていない**。

**根本原因を確定・修正（このターン・最重要）**: `ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER` の既定が

```java
(Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")
        || Boolean.getBoolean("celeritas.useVintageFastBlockRenderer");
```

で、**開発環境か明示プロパティのときだけ mod 自身の高速ブロックレンダラが有効**だった。本番jar（検証run）では
false になり、**全地形がゲームの `BlockRendererDispatcher`（遅いモデル経路）でメッシュ化**されていた。
その経路が焼く色は mod の `VintageBlockRenderer`（`tint × light.br`）より **約0.66倍暗い**。

`-Dceleritas.useVintageFastBlockRenderer=true` を付けたA/Bで確認:

| レンダラ | mid帯 | near帯 |
|---|---|---|
| vanilla GL | (146.6, 157.1, 132.3) | (139.8, 151.4, 131.4) |
| upstream Celeritas GL | (136.8, 146.5, 117.2) | (125.0, 135.4, 117.6) |
| celtium（修正前の既定） | (88.9, 93.3, 82.7) | (88.9, 95.6, 84.1) |
| **celtium（fast renderer有効）** | **(136.5, 140.9, 130.4)** | **(133.1, 140.0, 131.2)** |

**upstream の mid帯とほぼ一致（136.5 対 136.8）**。brightness ヒストグラム（0.1/0.36/0.48/0.5/1.0）も出るようになり、
`VintageBlockRenderer` 経路が実際に使われていることを確認。**既定を「有効（プロパティで無効化可能）」に反転**した
（`-Dceleritas.useVintageFastBlockRenderer=false` で旧経路に戻せる）。修正後の既定での再測定でも
mid (136.5, 140.9, 130.4) / near (133.1, 140.0, 131.2)、verdict は exit 0 / validationClean / PASSED。

**画素対応フィットでの確認（修正の定量的証明）**:

| 対象 | フィット | log rms |
|---|---|---|
| celtium 修正前 | `2.370 × vanilla^0.727` | 0.055 |
| **celtium 修正後** | **`1.475 × vanilla^0.915`** | 0.037 |
| upstream GL | `1.372 × vanilla^0.916` | 0.013 |

**指数 0.727 → 0.915 で upstream の 0.916 とほぼ同一**になり、輝度依存の圧縮が消滅した。
画像の目視でも、明るいグリッド線・緑の地面・青い空が参照と一致することを確認（`p46-default-r433`）。

**注意（受入の読み方）**: これはメッシュ化経路自体を変える修正なので、**この修正より前の画像受入は
「遅い経路でメッシュ化された状態」のもの**である。以後の受入は新しい既定で取り直す。

**新既定での受入（このターン）**:

| 検証 | 結果 |
|---|---|
| AMD 通常fixture | mid (136.5, 140.9, 130.4) / near (133.1, 140.0, 131.2)、exit 0 / clean / PASSED |
| **Intel 通常fixture** | mid **(136.6, 141.0, 130.5)** / near (133.0, 139.8, 131.2)、exit 0 / clean / PASSED、24.6s |
| AMD target受入 | exit 0（clear/depth順序とも pass） |
| Intel target受入 | exit 0（`sol-cb-r434-intel`） |

**AMD と Intel の帯がほぼ同一**（136.5 対 136.6）で、両vendorで参照に一致した。

**pinned Celeritas との再比較（同一fixture/GPU/camera/scene、`p32-glref-r434` vs `p32-celtium-r434`）**:
- 厳密画素一致（tolerance 0）では 654488 画素（**71.02%**）が異なる。ただし**同条件の2回run間でも24.8%異なる**ため、
  この指標は残差の大きさを表さない。
- **平均絶対差は 10.92/255（約4%）**まで低下し、**平行移動の最良一致が dx,dy=(0,0)** ＝位置ずれは無い。
- **目視では、明るいグリッド線・緑の地面・青い空・松明の位置と明るさが参照と一致**する。
- **松明（炎）も描画されるようになった**（遅い経路では炎が灰色だった）。残差は主に地面テクスチャの細部（サンプリング）に限られる。

**残差の性格を測定（このターン）**: 同一run の参照/候補ペア（`p32-glref-r434` / `p32-celtium-r434`）を帯別に解析。

| 帯 | 平均絶対差 | HF（参照比） |
|---|---|---|
| horizon (y200-300) | 12.54 | — |
| far (300-420) | 10.22 | 0.88 |
| mid (420-540) | 10.92 | 0.80 |
| near (540-660) | 10.76 | 0.84 |
| closest (660-720) | 11.26 | — |

- 残差は**画面全体に一様（全体平均 8.00/255）**で、局所欠陥ではない。最悪5%の画素も全幅に散る。
- **HF（高周波）が参照の 0.80〜0.88 倍** ＝ 我々のテクスチャが**15〜20%滑らか**。これが残差の正体。
- `-Dceltium.vulkan.mirrorGlMips=true`（GLのmipをミラー）では **平均絶対差 7.98 / HF 6.41 と不変**。
  ただし**この経路は exit 1 / validationClean=false**（fixtureStatus は PASSED のまま）で、**検証としては不成立**。
  新しい失敗証拠として保存する（`p48-mips-r435`）。
- atlas用サンプラは既に NEAREST（`VulkanGlTexture.create(..., nearestFilter=true)`）なのでフィルタ設定は原因ではない。

**ノイズ床とmip仮説の決着（このターン）**:

| 比較 | 平均絶対差 | HF比 |
|---|---|---|
| 参照 r418 vs 参照 r434（同一レンダラ2 run） | **0.22** | **1.000** |
| 候補 r434 vs 候補 r433（同一レンダラ2 run） | **0.13** | **1.000** |
| 参照 vs 候補 | **8.00** | **0.840** |

=> **ノイズ床は 0.13〜0.22** で、8.00 / HF 0.840 は**再現性のある実差**（以前の「run間ノイズ24.8%」は厳密画素一致指標の
もので、平均値は安定している）。

**mip仮説は否定**: HF を測ると 参照 **7.63** / 候補既定 **6.41** / `mipBiasAdd=-2` **6.90** / **LOD 0固定 6.90** /
`mirrorGlMips` **6.41**。**LOD 0（level 0のみ）でも 6.90 で参照に10%届かない** ＝ 差はmipレベルではない。

**atlas内容の差は描画に効かない**: GLのatlas実物は vanilla run で HF 34.77、mod導入runで 32.01（8%低い）。
これは `TextureAtlasSpriteMixin.embeddium$processTransparentImages` が**完全透明画素をスプライト平均色で置換**する
（Sodium由来のmipmapフリンジ対策）ためで、置換後も **alpha 0 のまま**なので**描画結果には影響しない**。
したがってこのatlas HF差は残差の説明にならない。

**失敗経路の実機検証（このターン・未完）**: `-Dceltium.vulkan.failSubmits=2` で submit 失敗を注入すると、
**exit 124（harness の duration cap）＝ハングし、fixture report が MISSING** になる。ログには

- `IllegalStateException: accepted geometry retained; renderer must be rebuilt`
- `IllegalStateException: beginFrame() was called while the previous frame is still open; endFrame() is required first`
- 最後に `Game crashed!`

が出ており、**失敗時に frame が開いたまま残り、次フレームで別の例外→クラッシュ→ヘッドレスはクラッシュ画面で停止**
という連鎖だった（validationClean は true / 0 messages のまま＝validationでは検出されない）。

**待機タイムアウト注入の検証（このターン・未完）**: `-Dceltium.vulkan.failIdleAfter=60`（定常状態で発火）では
**exit 124（ハング）→ 修正後 exit 255（クラッシュ）** と推移し、**まだ run が終わらない**。判明した連鎖:

1. `waitForIdle()` の失敗で `gpuStalled` が立ち、frame が開いたまま → `endFrame()` のGL書き戻しがVulkanシグナルを
   待ち続ける（`abandonFrame` を待機失敗経路にも追加して解消）。
2. 次に `flushImmediateWindow()` の `GPU stalled with accepted geometry pending` が**render loop まで到達**して
   クラッシュ → **例外ではなく計数して降りる**方式に変更（`stalledGeometryDrops` / `stalledGeometryDroppedBatches`、
   監査の「全dropを数える／falseを描画済みとしない」に沿う）。
3. 次に `beginCommands()` の `pending backend command buffer did not complete` が発覚。

**この過程で通常経路を一度壊した（重要）**: `flushImmediateWindow()` の早期returnより前に `beginCommands()` を
ガード付きで呼ぶ変更を入れたところ、command buffer が recording のまま残り、**通常runが exit 255 でクラッシュ**した
（`cannot settle/retire a stalled GPU: region eviction`）。当該パッチは**撤回済み**で、通常runは
exit 0 / mid (136.5,140.9,130.4) / PASSED / target受入 exit 0 に復帰。**ホットパスへの前置ガードは早期returnと
両立しない**という教訓として残す。待機タイムアウト経路は引き続き未解決（`settleGpu` の teardown 経路が次の候補）。

**settleGpu の例外を撤去（このターン）**: `settleGpu(reason)` は停止中に例外を投げていた（呼び出し元が資源を
壊さないための手段だった）が、その例外も render loop に到達してクラッシュしていた。**boolean を返す**形にして
`false` のとき呼び出し側が破壊的操作を**スキップ**するようにした（region eviction は break、frame target recreation は
`return false`、backend delete は `return`）。資源はプロセス終了まで残す＝元コメントの意図どおり。

しかし待機タイムアウト注入は**依然 exit 255（16.7s でクラッシュ）**。現在の最後の障害は
`beginCommands()` の `pending backend command buffer did not complete`（`submissionPending` の wait 失敗時に
throw している）。正しい縮退点は**外側の GL intercept で false を返してGLに描かせる**ことで、
`beginCommands` に boolean を返させる（5呼び出し元）か、intercept 側で捕捉して計数するのが次の一手。

**カスタムシェーダ拒否の修正と再受入（このターン）**: 受理経路の preflight に
`GL_CURRENT_PROGRAM != 0` のバッチを拒否する判定を追加した（任意の program は固定パイプラインで再現できないため、
これは正しさの修正でもある）。修正は「受理するバッチの集合」を変えるので、shader を使う場面を取り直した:

| run | 結果 |
|---|---|
| spider (AMD) | **exit 124（ハング）→ exit 0 / validationClean / PASSED / 24.9s**、mirror 898 → **80（−91%）**、p99 174 → 118ms |
| spider (Intel) | **exit 124（ハング）→ exit 0 / validationClean / PASSED / 28.9s**（`shaderActive=True`） |
| postprocess (AMD) | exit 0 / PASSED / 24.4s / p50 42.4ms / p99 47.1ms（修正前 41.6/47.3 と同等） |
| 通常 (AMD) | exit 0 / PASSED / 24.8s / p50 42.4ms（不変） |
| target受入 (AMD) | exit 0（clear/depth 順序とも pass） |

- 拒否カウンタを `diagnostics()` に追加（`-Dceltium.vulkan.handoffTimings=true`）。**report に出ていることを確認**:
  postprocess run の `rendererDebug` に **`refusedBeforeAppend=97`** があり、**新しい受理判定が実際に発動**している。
  （`handoff detail` 行も同じ report に出ている。`shaderProgramRefusals=` / `accepted=` は report 側の長さで
  切れている可能性があるため、次に確認する。）
- 真因の記録: `mirrorRefreshFrames=8` でもハングし mirror は増えた（898回/927 invalidations）＝周期ではなく
  **使用ごと**に発生。ShaderGroup は real program で複数パスを描くため、パスごとにフルスクリーン読み戻しが走り
  ~15回/フレーム＝約2秒/フレームになっていた。

**容量整理（このターン）**: `.celtium-work/runtime` が 10G まで増え、ディスク空きが 9.8G（90%）になっていた。
内訳は **mods のコピー 5.8G / test-libraries のコピー 2.2G / saves（world data）1.5G** で、いずれも**受入証拠ではない**
（run ごとの入力コピーと再生成可能なworld ✗ 証拠は `validation-process.json` / `logs/` / `screenshots/`）。
canonical な `celtium-headless-interop/test-libraries` だけ残して削除した結果:

- `.celtium-work/runtime`: **10G → 589M**
- ディスク: 空き **9.8G → 20G**（使用率 90% → 80%）
- 保持を確認: `validation-process.json` **282件**、`celtium-validation.png` **248件**
- 引用中の受入 run（p32-glref-r434 / p32-celtium-r434 / p39-vanilla2-r426 / p71-spider / p71-spider-intel /
  p68-spider-intel / p49-failsubmit-r437 / p64-weather-intel / sol-cb-r454-amd）は report+PNG とも残存を確認。

**計器を接続して修正の的を証明（このターン）**: 前ターンの「未接続」は**部分的に誤り**だった。
`verificationSummary()`（= report の `rendererDebug` に入る `celtium/vulkan-check ...` 文字列）には
`refusedBeforeAppend` が**既に載っていた**。一方 `diagnostics()` は report に出ない（別リスト・report は10項目上限）。
そこで `shaderProgramRefusals` と `acceptedBatches` を `verificationSummary()` に追加した。実測:

| run | refusedBeforeAppend | **shaderProgramRefusals** | mirroredTextures | p50 / p99 |
|---|---|---|---|---|
| **通常** | 106 | **0** | 11 | 42.1 / 47.3ms |
| **spider** | 439 | **340** | 17 | 46.3 / 120.0ms |

- **通常runでは新判定が一度も発動しない（0）**＝正当なバッチを拒否していない ✅
- **shader場面では340回発動**し、mirror は 898（修正前）→ **17** に減少 ✅
- どちらも exit 0 / validationClean / PASSED。p99 は spider 120ms / 通常 47ms。

**計器の未接続を確認（このターン）**: 拒否カウンタを `VulkanTerrainGpuBackend.diagnostics()` の先頭に足したが、
report の `rendererDebug` には**出ていない**。report 側は **10項目で上限**されており（実測: count = 10、内容は
`solid - 62 sections, 284.2 us` などのパス統計）、backend の `diagnostics()` とは**別経路**の文字列が入っている。
したがって次に必要なのは「`rendererDebug` を組み立てている箇所へカウンタを載せる」ことで、
`diagnostics()` への追加だけでは足りない。これは「計器を足したが読めていない」状態なので、
未完了として明示する（読めた `refusedBeforeAppend=97` は別の `vulkan-check` 系の行に含まれる値）。

**修正後の Intel 再受入（このターン）**: カスタムシェーダ拒否はホットパス（受理判定）に触れるため、
Intel 側を取り直した。

| run | exit | validationClean | fixture | 所要 | nativeTerrainVerified | errors | p50 |
|---|---|---|---|---|---|---|---|
| Intel 通常 | 0 | true | PASSED | 24.6s | true | 0 | 62.9ms |
| Intel postprocess | 0 | true | PASSED | 24.8s | true | 0 | 63.5ms |
| Intel weather | 0 | true | PASSED | 28.1s | true | 0 | 74.6ms |
| Intel target受入 | 0 | — | — | — | — | — | — |

spider は前ターンに AMD/Intel 両方で PASSED（修正後）。これで修正後の受入は AMD/Intel の両方で揃った。

**次の一手**: (1) `shaderProgramRefusals` / `accepted` が report に出るかの確認、(2) `switchUseShader` の各 shader
でも受入、(3) timestamp クエリで handoff の GPU 時間を測る。

**修正（submit失敗・解決済み）**: frame を開く経路は4系統（terrain render / present bridge / composite / present）あり、
失敗時に開いたまま残っていた。`VulkanFrameTarget` を**自己修復**にした:

- `beginFrame()`: 前のframeが開いていたら**例外を投げずに放棄して計数**（`leakedFrames`）し、続行する。
- `endFrame()`: 失敗経路が既に放棄したframeなら**GL書き戻しをせず no-op**（`abandonedWrites` に計数）。
- `abandonFrame(reason)`: GPU作業なしでframeを閉じる（冪等）。

| 状態 | exit | fixture | 所要 |
|---|---|---|---|
| 修正前 | **124（ハング）** | MISSING_REPORT | 165.2s |
| abandonFrameのみ | **124（ハング）** | MISSING_REPORT | 165.2s |
| **自己修復beginFrame** | **1** | **FAILED（reportあり）** | **24.2s** |

修正後は `vulkanErrors=2`（注入した2回の失敗を計上）、`vulkanPasses=1406`、"still open" は**0件**、
`nativeTerrainVerified=false` として**レポートに残る**＝「停止した device はハングせずメッセージを出して run を終える」
という監査の要求を満たした。通常runは無影響（mid (136.5,140.9,130.4) / verdict PASSED / target受入 exit 0）。

**残差の現状**: 平均 8.00/255（約3%）、HF 0.84、幾何エッジの10-90%幅は参照と同一（6.16 対 6.05）。
画像忠実度は「構造・明るさとも一致、細部の微差のみ」の状態。次の優先は監査バックログ（失敗経路・coverage・
texture所有）へ移す。特に **松明の炎（アニメーションテクスチャ）**
は依然として未描画なので、sprite animation の移植を進める。

**（旧メモ）**: 「同一データか否か」を確定させる。*vanilla参照run* の中から（= modの経路を介さずに）
実際のチャンク頂点色をdumpして、我々のrunの同じ頂点色と**数値で**比較する。差分が出ればデータ側、
一致すれば適用側（我々のVulkan経路）に残因がある。

**（旧メモ）**「GLの固定機能パイプラインが我々のshaderに対して*余分に*掛けているもの」を特定する。
候補: (a) フレームバッファの色空間（sRGB/gamma）差、(b) 固定機能の `GL_TEXTURE_ENV` 合成（RGB_SCALE等）、
(c) グローバルな `glColor`/マテリアル項。まず (a) を、vanilla画像と我々の画像の**画素対応のpower-law fit**
（`ours = k × vanilla^γ` のγと残差）で判定する（γ≈1で残差大なら色空間ではない）。

（旧メモ・較正実験:
既知のテクスチャと既知の色を持つ1枚のquadを terrain と同じ pipeline/format で描き、期待値
`texel × colour × lightmap` と実測画素を厳密に突き合わせる。これで 0.6 がどの段で落ちるかを1回で確定できる。
上面で brightness×AO が 1.0×1.0 になっているか（余分な0.6が無いか）を確かめる。
（旧メモ:
上面の色が「tint × shade(1.0)」になっているか（＝余分な係数が掛かっていないか）を確かめる。
(a) そのジオメトリが何のブロック/面か（UVと位置から）、(b) なぜ alpha=255 の不透明色なのか、
(c) pinned upstream の mesher と pass 分類・色合成のどこが違うのか、を順に確認する。
（旧メモ・切り分け:
色が2値になる頂点の**arena上の実バイト**を確定させる。そこで初めて
「uploadが壊れている」か「shader側の読みが壊れている」かが決まる。

（旧メモ・切り分け手順:
1. pipeline作成時に**5属性すべての (location, format, offset)** と stride をprintして、宣言と実データの対応を確認。
2. draw時の `vertexOffset` / `firstIndex` / 使用pipeline をprintし、読んでいる頂点が想定のものかを確認
   （誤ったvertexOffsetなら「別の頂点」を読み、色が2値に見え得る）。
3. vertex shaderで `v_Color = vec4(a_Color.r, a_Color.g, 0.0, 1.0)` のように**成分を固定**して、
   どの成分が生きているかを1回で見る。

（元の手順: fixtureに、terrain経路と**同じpipeline/同じformat**で
「既知の1枚のquad（既知のテクスチャ・既知の頂点色）」を描いて**厳密な画素値を読み戻す**小実験を足す。
期待値は `texel × colour × lightmap` で計算できるので、どこで0.635→0.369の損失が出るかを1回で確定できる。
（`lightDiagnostics` の pass別集計は恒久的な計器として残す）
mesh builderが実際に詰めた色の平均を出す。それを「無効化runの212 × 詰めた色」と突き合わせ、
描画結果（79.6/85.5/75.4）と一致するかを確認する。一致しなければ損失はshader内、一致すれば
詰めた色自体が参照の0.62倍である（＝mesherの色合成か、VANILLA_LIKE と COMPACT の選択差）。
`CeleritasWorldRenderer` の format 選択（VANILLA_LIKE/COMPACT）は上流checkoutに存在しない
**Celtium側の変更**である点も確認済み。terrain が実際に bind する `this.atlasTexture` の内容を capture 時に dump し、GLの実atlasと比べる（registry mirror との比較だけでは同一性を証明できない）。
そのうえで、同じbuildで **GL terrain にフォールバックした frame** と比べる（参照に依存せず、Vulkan地形の当否を判定できる。初回試行は起動時の `getSkyColorBody` NPE で落ちたため、その原因も見る）。（fragment shaderが `rgb *= a_Color.rgb` と
`rgb *= a_Color.a` を行う。round 267の実測: この乗算ありで地形帯98・参照131、乗算を外すと215）。
Vulkan側の地形頂点バッファから数点をdecodeして `a_Color` / `a_LightCoord` の実値を出し、
解析的な期待値（tint、面shade、AO=1.0、light 15/0）と比べる。`a_Color` が期待の約0.6なら、
mesh builderの色合成か頂点format（`VK_FORMAT_R8G8B8A8_UNORM` のoffset/解釈）が原因と確定する。

