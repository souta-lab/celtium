# フレーム内訳は検証レイヤを測っていた（2026-11-04）

round 42–46 のフレーム会計（`CELTIUM_FRAME_PASS_SPLIT`）は **Khronos validation レイヤを有効にした leg** で
取られていた。paired KPI は `-Dceltium.vulkan.validation=false` で走る。同じ scene・同じ GPU・同じ計器で
検証レイヤだけを切り替えると、**Vulkan 呼び出しを含む bracket は 4–9 倍**に膨らみ、Java 側の bracket は
どちらでも 0.0 のままだった。つまり「frame 3.6 の 25% は描画呼び出し」は**検証レイヤの配分**であって、
出荷構成の配分ではない。

## 条件

| | r47a-shipping | r47b-control |
|---|---|---|
| Renderer | `0801cd8f928fa3600b9542af08b17ebb36454df6c721b3f4d494663896f87428`（本 round の commit） | `135779f4cd8036c73b5273672b17ec41889f75f4048e3e1723ed17c2a202094c`（round 46 の commit） |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` | 同左 |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet` は両 leg で真 | 同左 |
| Scene | `scene=world`、save `qmagnet`、1280x720、`guiScale=3`、240 s、`sceneCalibrationKeyMatches=true` | 同左 |
| Flags | `-Dceltium.vulkan.validation=false`、`-Dceltium.vulkan.presentReadback=true`、`passSplit`+`windowSplit`+`frameStats`（r47a のみ `packetTimings`） | 同左（`packetTimings` なし） |
| 判定 | `PASSED`・`problems=[]`・`fixtureStatus=PASSED`・`presentedImageVerdict=PASSED` | 同左 |

比較に使う「検証 ON」の列は round 46 の leg `r46a-stages`（JAR `4665d8f7`、同じ scene/GPU、`validation=true`）。
JAR が 1 つ違うが、その差は round 46 の計器と重複 lookup の削除だけで、下の bracket を動かす変更ではない。

## 1. 検証レイヤは Vulkan 呼び出しの bracket だけを膨らませる（p50 ms、512 フレーム窓）

| bracket | 検証 ON（r46a） | 検証 OFF（r47b） | 比 |
|---|---|---|---|
| `frame` | 3.6 | **1.4** | 2.6× |
| `world` | 2.5 | **1.0** | 2.5× |
| `gui`（HUD） | 1.0 | **0.4** | 2.5× |
| `flush` | 2.0 | **0.4** | 5.0× |
| `winRecord` | 1.7 | **0.3** | 5.7× |
| `wrecLoop` | 1.1 | **0.2** | 5.5× |
| `grpDraw` | 0.9 | **0.1** | 9.0× |
| `winHead` | 0.4 | **0.1** | 4.0× |
| `winBarrier` | 0.2 | **0.0** | — |
| `winBeginPass` | 0.1 | **0.0** | — |
| `terrain` | 0.3 | **0.1** | 3.0× |
| `rcResolve`/`rcPush`/`rcPrepared`/`offCapture`/`offPrepare`/`offMerge`/`offAppend` | 0.0 | **0.0** | 1× |

**Java 側は 1 つも動いていない。** 動いたのは全部 `vkCmd*` を含む bracket で、膨張率は呼び出し密度に比例する
（`grpDraw` が 9 倍で最大、`terrain` が 3 倍で最小）。したがって round 42–46 が「micro-opt は床」と結論した
対象（group ループの Java 側）は**出荷構成でも床**で正しく、**「最大は描画呼び出し」だけが誤り**だった。

## 2. ドローコストの実測（`packetTimings`、r47a）

| | 検証 ON（r46b） | 検証 OFF（r47a） |
|---|---|---|
| `vkCmdDrawIndexed` | 5.0 µs/回 | **0.9 µs/回** |
| vertex+index bind | 1.9 µs/回（実 bind 3.2 µs） | **0.3 µs/回** |
| pipeline bind | 7 µs（35 回） | 2 µs（40 回） |
| push | <1 µs | <1 µs |

`packetDrawMillis=129` / `packetCalls=143863`。**ドローは 0.11 ms/フレームで、フレームの 8%**。
round 46 が「25%」と読んだのは検証レイヤの分だった。**ドロー回数を減らす構造変更の根拠は消えた**
（間接 multi-draw・material 順ソート・push block の同一性を狙う話は、0.11 ms を削る話になる）。

## 3. 窓の実測（`windowSplit`、r47a。round 46 の shutdown hook 修正で world 行が出た）

```
windows=54532 route=0 offTarget=6 off[pair=0 sync=0 trans=0 clear=4 fb=0]
pair=0 publish=0 begin=0 commands=2 acquire=2 release=0 syncScan=96/30747
```

- **off-target routing は 6 µs/窓**（round 46 の 22 µs は検証レイヤ込み）。内訳は **pair clear が 4 µs**、
  `ensureChainPair`・stale-name 走査・published-name 遷移・framebuffer 解決はどれも 0。
- `syncScan=96/30747`＝stale-name 走査は 96 回走り、memo が 30,747 回当たった（round 45 の修正が効いている）。
- `commands=2`・`acquire=2` は窓あたりの `vkCmd*` 数。**窓の固定費はもう 6 µs で、23 窓/フレームでも 0.14 ms**。

## 4. 見つけた計器の欠陥: `fullUploadBytes` は一度も加算されていなかった

`mirrorUploads[...]` の `bytes=` は宣言（`VulkanGlTexture:1242`）と読み出し（同 1287・1578）だけで、
**代入が 1 つも無かった**。だから全 leg が `bytes=0` を出し、plan.md はそこから
「`supply` は upload ではなかった（62,564 呼が serial check で即 return）」と結論していた。
同じ行の `count` と `millis` がその結論と矛盾していた（113 回 × 3.85 ms = 435 ms の実仕事）。

`fullUploadBytesByGlId` も**累計値を毎回足していた**（2 回目以降の per-id 値が過大）。

## 5. その 3.85 ms の中身: 内容 hash の byte 単位ループ

`uploadFromGlTexture` の内容 digest だけが **1 バイトずつ**の FNV ループだった
（`uploadFromImage` は既に 8 バイト/step の `hashUploadSlice` を使っている）。512×512 は 105 万回の
依存乗算で、`skips=0` は**この digest が測った scene で一度も skip を起こしていない**ことを意味する。

2 つの経路が**別のアルゴリズムで hash していた**ため、announce 経路で入った画像と GL upload 経路の
digest は永久に一致せず、**同じ内容の whole-image 再 upload が毎回走っていた**。同じ関数に揃えると
skip が効くようになる。

### A/B（同一 scene・同一 flags・検証 OFF、JAR だけが違う）

| | r47b-control（`135779f4`） | r47a-shipping（`0801cd8f`） |
|---|---|---|
| `count` | 97 | 97 |
| `bytes` | 0（計器が死んでいた） | **202,612,224** |
| `millis` | **420** | **76** |
| `skips` | **0** | **95** |
| `parts` | — | `read=0 hash=19 copy=5` |

**420 ms → 76 ms（5.5×）。97 回のうち 95 回は whole-image 再 upload そのものが消えた。**
240 s の run で 2,400 フレーム前後なので **0.175 → 0.032 ms/フレーム**。フレーム内訳は両 leg で同一
（`frame` 1.4・`grpDraw` 0.1）で、この差は受理経路の中にあり、この leg では `batchTimings` を
入れていないため bracket には出ない。

## 6. この round のコード

1. `fullUploadBytes` を `commitFullUpload` で実加算（`committedUploadBytes` 経由、早期 return と例外は 0）。
   `fullUploadBytesByGlId` も per-call 値に修正。
2. `uploadFromGlTexture` の digest を `hashUploadSlice`（8 バイト/step）に統一。**2 経路の digest が
   同じ関数になり、skip が初めて発火する。**
3. `mirrorUploads` に `parts[read= hash= copy=]` を追加（3.85 ms の帰属）。
4. `CELTIUM_FRAME_PASS_SPLIT` の行に `conditions[validation=on|off readback=on|off]` を追加。
   **同じ罠を二度踏まないため**、内訳の行自体が測定条件を名乗る。

## 主張しないこと

- 検証 ON/OFF の比較は JAR が 1 つ違う（round 46 の計器と重複 lookup 削除）。比の 4–9 倍は
  `gv_leg.py` 自身の注記（標準 scene で 11.0 ms のうち 6.6 ms）と整合するが、この 2 leg だけでは
  「round 46 の変更が 0」を証明していない。
- `frame` 1.4 ms は**レンダーの bracket**で、フレーム間隔ではない。KPI の `frameIntervalWarmP50Ms` は
  3.03 ms（round 41）で、差の ~1.6 ms は bracket の外（tick などゲーム側）。
- 0.14 ms/フレームの改善を KPI の比の改善とは呼ばない。paired KPI は round 41 の 1.386 以降未測定。
- `skips=95` の正しさは「2 経路が同じバイト列を hash している」ことに依る。内容が違えば hash も違い、
  skip は起きない（`uploadFromImage` は extent も比較する）。
