# model replay の導出値（lit payload・projection remap）を入力一致で再利用する（2026-11-02）

renderer `7426ce5a71c32e612bb58b7af2a9f06b28953df32050c0cb396aa3a1fd9c7530`（rc.26、`main` 45fdaeff4 + 本差分）、
fixture `9e920ab1822f9cc7467994da26271f5977b1a6322aec883824fcfa566834ce94`。
**この round は実機 leg を回していない**（この環境に表示セッションが無く、`celtium-world-accept` も無い）。
数値は前 round の r16b のもので、変更の効果はまだ frame で測っていない。計器は次回の leg に載せた。

## 観測: pack leg の model replay は 1 回 311 ns、2.5 M 回

r16b（Supersymmetry、AMD RX 6400、`-Dceltium.vulkan.batchTimings=true` を含む）の最終 counters:

```
modelDraws=2509753 modelEnqueueCalls=2509753 modelEnqueueMillis=781
modelEnqueueMicrosPerCall=0 modelRepeatsPercent=42
modelInstances[appended=2498379 runs=98 ...]
```

781 ms / 2,509,753 = **311 ns/replay**（`modelEnqueueMillis` は `appendModelInstance` 全体を `System.nanoTime` で
挟んだ値なので、時計 2 回の分を含む）。`runs=98` は leg 全体で、**draw 呼び出しは frame あたり 1 回弱**まで
既に畳まれている（run の merge が効いている）。したがって残る費用は **replay ごとの CPU 側**にある。

1 replay が作るもののうち、入力がほとんど変わらないのに毎回作り直していたものが 2 つある:

| 値 | 入力 | 費用 |
|---|---|---|
| lit payload（20 float、record 128..207） | `modelView[0..10]`（回転のみ。12..14 の平行移動は読まない）、`state[8]`、light の generation | 3x3 逆行列（det + 9 cofactor）＋2 回の sqrt/正規化＋20 float の書き込み |
| depth-remap 済み projection（16 float） | live projection の 16 float | 4x4 積（64 回の積和） |

projection は pass ごとにしか変わらず、lit payload の回転は「同じ向きの entity/block entity」で一致する。
r16b の `modelRepeatsPercent=42` は「直前の replay と list・texture・state key・projection・modelView が全部同じ」
割合で、**同じ回転の replay が少なくとも 42 %** あることの下限になる（平行移動が違っても回転が同じなら
lit payload は同じ）。

## 変更

1. `ModelReplayInputCache`（新規、package-private）: 2 つの値の入力だけを保持し、一致したときだけ再利用を許す。
   - lit: `lightGeneration` + `modelView` の 9 個の回転成分（0,1,2,4,5,6,8,9,10）。`note` はコピーするので、
     呼び出し側の scratch 配列を書き換えても偽の hit にならない。
   - projection: 16 float の内容比較。`note` はコピー。
   - hit/miss を数える（`reuse*` の呼び出しが必ずどちらか 1 つ）。
2. `VulkanTerrainGpuBackend`:
   - `celtium$replayLight(state, modelView)`: hit なら `modelInstanceLight` をそのまま返し、miss なら
     `celtium$lightParams(state, modelView, modelInstanceLight)` を実行してから note。**model 専用 scratch を使う**
     （2 引数版が使う `lightParamsScratch` と共有すると、per-group 経路の計算で中身が変わる）。
   - `celtium$replayProjection(projectionNow)`: hit なら `modelReplayProjection` を返し、miss なら
     `remapClipDepth` して note。
3. `ModelReplay` の 2 か所をこの 2 メソッドに置き換え。record のレイアウト・shader・draw command は不変。
4. レポートに `modelReplayCache[light[hit=… miss=…] projection[hit=… miss=…]]` を追加。hit 率が 0 の scene では
   「この最適化は効いていない」と読めるようにした。
5. **同じ jar の対照**を用意した: `-Dceltium.vulkan.replayInputCache=false` で両 reuse が常に false になり、
   counters は all miss。cache 以外の経路は同一なので、on/off の ABBA がそのまま cache の差になる。

**絵が変わらない理由**: hit は「その値を生成した入力と完全に一致」を意味するので、返すバイト列は再計算した
バイト列と同じ。miss は従来と同じ計算をそのまま行う。record の書き込み先・コマンド・描画順は一切変えていない。
`det` の分岐（特異行列で identity を入れる）も、入力が同じなら同じ枝に入る。

## CPU 検証（この round で実施）

- `JAVA_HOME=… scripts/build.sh :celtium-vulkan:test :forge122:packageJar :forge122:validationRemapJar` →
  BUILD SUCCESSFUL、**236 tests / 0 failures / 0 errors**（`ModelReplayInputCacheTest` が 6 ケース追加）。
  - 9 個の回転スロットを 1 つずつ変えて必ず miss すること、平行移動だけの差は hit、generation 差は miss、
    null/短い配列は miss、note がコピーであること、counters が「呼び出しごとに hit か miss のどちらか 1 つ」を
    満たすことを固定した。
- `scripts/cpu-contract/transformer-route-verify.sh` → called=241 failed=0、classes 87/87、colour 32/32、texcoord 64/64。
- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` → Ran 106 tests, OK。
- `python3 scripts/check_agent_docs.py` → OK（`docs/instruments.md` 再生成込み）。

## まだ言えないこと

- **frame での改善量**。hit 率は scene 依存で、回転が毎 replay 変わる scene では 0 になり得る。同じ jar の
  on/off arm を回すまで FPS の主張はしない（r16b の 311 ns は別 leg・別ビルドなので、その数との比較はしない）。
- Intel での動作。cache は CPU 側だけなので GPU 差は無いが、実行そのものは AMD の leg でしか見ていない
  （今回は GPU leg 無し）。

## 次に測る手順

同じ jar で 2 arm を交互に（ABBA、間隔は数分以内; 熱状態の注意は
[fps-session-2026-10-31](fps-session-2026-10-31.md) の通り）:

```
CELTIUM_INSTANCE_COHORT=$HOME/.local/share/PrismLauncher/instances/Supersymmetry \
CELTIUM_LEG_SEED_OPTIONS=$PWD/.celtium-work/options/guiScale3.txt \
scripts/gv-leg.sh <leg-cache-on>  <stage> plain amd 200 \
  -Dceltium.vulkan.batchTimings=true -Dceltium.debug.modelRecords=400
# 同じコマンドで -Dceltium.vulkan.replayInputCache=false が off arm
```

読む値: `modelReplayCache[...]` の hit/miss、`modelEnqueueMillis / modelEnqueueCalls`、`modelRepeatsPercent`、
frame の interval。on と off の差が cache の効果で、hit 率がそのまま説明になる。on で hit がほぼ 0 なら、
この scene の回転は毎 replay 違い、次の対象は record の書き込み（256 B）か描画側ということになる。
別 leg（r16b の 311 ns）との比較だけでは熱状態の差と区別できないので、判定には使わない。
