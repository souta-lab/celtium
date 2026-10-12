# Celtium 引き継ぎ書（2026-10-10 / 次モデル向け）

> この文書は**引き継ぎ時点のスナップショット**である。現在状態の単一の表は [`docs/plan.md`](plan.md)。
> 両者が食い違う場合は **plan.md と最新 HEAD が勝つ**。過去の経緯は `docs/history/`。
>
> 作業ディレクトリ：`/home/s0u7a/projects/celtium`（`main`、`origin` = https://github.com/souta-lab/celtium）
> 引き継ぎ時 HEAD：`ab11e553a`（その後 `22cf97647` 証拠整理、`834e6ecc9` §0.103 提示 blit 契約、`699c8660e`
> §0.104 format-5 残差確定、`§0.105` Nether 両GPU受入まで進んだ。`git status --short` が空であることを最初に確認する）

---

## 0. 最初にやること（この順で）

1. `git log --oneline -5` と `git status --short` で HEAD と差分を確認する。
2. `docs/plan.md` の **§0（冒頭表）** と **§0.100〜§0.102** を読む（直近3ラウンドの実測と結論）。
3. `docs/goal.md` の **【現在の到達点と未完】** を読む。
4. 下の §5「次の一手」から1つ選び、**§8 の作業規律**に従って実装する。

**goal の状態**：`goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`、**revision 54 / phase `active` / activation `armed` / roundsStarted 636 / max 1000**（2026-10-10 後半に人間の継続指示で再開し、objective を `docs/goal.md` 本文へ同期済み）。
→ もし `disarmed` に戻っていたら、続行には**人間の「継続して」等の指示**が必要（`update_goal` の `action: resume`）。**勝手に complete / blocked にしない。**

---

## 1. これは何か

Minecraft 1.12.2 / CleanroomMC 専用の、**Celeritas fork のネイティブ Vulkan レンダラ**。
GL を置き換えるのではなく、Minecraft の描画の意味（camera / terrain / model / particle / UI / texture / FBO / present）を保ったまま
**native pass へ移す**。汎用 GL→Vulkan 変換器でも GL 全エミュレーションでも Zink でもない。

**基準環境**：Cleanroom 0.6.13-alpha / LWJGL 3.4.1 / ゲーム実行 JDK25 / Celtium モジュールは `--release 21`（class major 65 以下）。
**実機 GPU（シリアルに使う）**：
- AMD：`/dev/dri/by-path/pci-0000:03:00.0-render` → `--require-gpu 'RX 6400'`
- Intel：`/dev/dri/by-path/pci-0000:00:02.0-render` → `--require-gpu 'UHD Graphics 630'`

---

## 2. ビルドと実行（コピペで動く）

### 2.1 ビルド

```bash
cd /home/s0u7a/projects/celtium
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain --no-daemon \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME,$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1,$PWD/.celtium-work/jdks/8/jdk8u504-b01" \
  2>&1 | grep -E "BUILD (SUCCESSFUL|FAILED)|エラー|error:"
```

モジュール単体のコンパイル確認は `:celtium-vulkan:compileJava`。

### 2.2 実行（fixture）

```bash
export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"
NOLAVA='3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:leaves,minecraft:stained_glass;1;'

timeout 900 python3 scripts/prism_validation.py \
  /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output .celtium-work/pNNN-lock.json --game-dir .celtium-work/runtime/pNNN \
  --mod build-1122/build/libs/0.0.2-alpha-dev/celtium-forge-mc1.12.2-0.0.2-alpha-dev.jar \
  --mod forge122/build/libs/celtium-validation-client-0.0.2-alpha-dev.jar \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.validation.worldLayers="$NOLAVA" \
  --jvm-arg=-Dceltium.vulkan.presentReadback=true --jvm-arg=-Dceltium.validation.dumpPresentedImage=true \
  --jvm-arg=-Dceltium.validation.expectVulkan=true --jvm-arg=-Dceltium.validation.expectVulkanTerrain=true \
  --jvm-arg=-Dceltium.vulkan.validation=true \
  --run --validate --duration 120 --headless --force-x11 \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400' > .celtium-work/pNNN.log 2>&1; echo "exit=$?"
```

主な fixture スイッチ（`--jvm-arg=-D...`）：
`celtium.validation.keepEntities=true`（モブを出す）／`celtium.validation.resize=true`（tick 100 でウィンドウを 1024x640 へ）／
`celtium.validation.particles=false`／`celtium.validation.dumpPresentedTick=N`（既定 20）／`celtium.validation.jei` ほか。

### 2.3 判定の読み方

- `.celtium-work/runtime/<run>/validation-process.json` → `validationExitCode` / `validationClean` / `fixtureStatus` / `validationMessageCount`
- `.celtium-work/runtime/<run>/logs/latest.log` → **stdout の計器行**（§6）
- `.celtium-work/runtime/<run>/logs/celtium-validation.json` → `rendererDebug`（**10行だけ**。拒否タリーは入らない）
- `.celtium-work/runtime/<run>/screenshots/` → `celtium-validation.png`（GL が描いた絵）と `celtium-presented.png`（Vulkan が提示した絵）

**画像比較の作法（§0.103 で変更済み・重要）**：`celtium-presented.png` は**正立**になった。比較は**反転なし**で行う。
resize run など「描画領域がウィンドウより小さい」場合は、提示画像の**左下 `dw×dh` 領域**（この fixture では `[H-h:H, 0:w]`）を
スクリーンショットと比べる。旧「反転して比べる」は提示が逆さだった名残で、以降の run には適用しない。

```python
P = np.asarray(Image.open(d+'/celtium-presented.png').convert('RGB'), dtype=float)
S = np.asarray(Image.open(d+'/celtium-validation.png').convert('RGB'), dtype=float)
# drawable < window の run では P = P[H-h:H, 0:w]（左下アンカー領域）に切り出してから比べる
diff = np.abs(P-S); mask = (diff.max(axis=2) > 8)   # >8 が異なる画素の割合を報告する
```

---

## 3. いま出来ていること（実測つき）

| 項目 | 状態 | 根拠（このセッションの実測） |
|---|---|---|
| terrain / immediate の native 描画 | **実装済・両GPU受入** | 既定受付 format 1〜9。offers 16000 / accepted 13202 / GL 本体 2825（AMD） |
| **ゲームが Vulkan 所有 color/depth に直接描画（adoption）** | **既定・両GPU受入** | AMD `presentedFromVulkan=889/901`、同一vendor GL 経路と 0.072/0.05 %。Intel PASSED・0.026/0.02 % |
| 既定フレームバッファ合成の native 化 | **受入済** | `defaultFramebufferCompositesTaken=902/run`（AMD） |
| **通常場面（実体なし）の画素一致** | **両GPU受入** | 提示画像 vs GL の絵 = **AMD 0.001 / 0.01 %**、**Intel 0.000〜0.016 / 0.00 %** |
| **resize（項目7）** | **両GPU受入** | §0.102 で恒久 stall を解消（期間契約）、§0.103 で提示 blit を GL composite 契約へ。resize run で描画領域 vs screenshot（反転なし）= AMD 0.001 %・Intel 0.000 %、stall 0、退行 0.000 % |
| End の dimension | 両GPU PASSED | capture 時 dimension 1 |
| 失敗経路（fence timeout / submit 失敗） | 両方 clean・VUID 0 | `stalledStops=1` / `stalledTeardownSkips=548`、資源は解放しない設計 |
| **entity 場面（モブあり）の残差** | **解決済み: fixture キャプチャ仕様（§0.104）** | 提示フレームの生存粒子が写る画素そのもの（GL 側は除去済みで粒子画素 0）。除外方向で 0.32 % = ノイズ床。同フレーム静止 FLAME で format 5 の色・位置は正しい（AMD 0.157 % = 経年差のみ） |
| model 経路（表示リスト）の native 描画 | **実験フラグ側・既定 off** | `V_now × C_compile⁻¹` で 2.19 %（7通りの実測で最良）。既定 off の理由は下記 |
| **Nether** | **両GPU受入（§0.105）** | capture 時 -1 で native scene（AMD 967 / Intel 349 pass・skip 0）。帰還は portal AXIS 直交 step 未対応 + backend 再生成後の共有 texture 死（0x0 frame target デッドロック）の2件で、両方修正済み |
| 条件 (b) の残り | GL context 生成そのもの＋起動直後10フレーム | native の描画先がまだ無い瞬間のもの |
| 条件 (c) 実 window/compositor 出力 | **未確認** | `DISPLAY=:0` の root が 0×0・合成器なし。不足条件は plan §0.30 |
| 全 entity/TESR・全 block state・全 mod・性能・配布 | 未確認 | registry 254 種掃引（AMD）と部分掃引（Intel）のみ実績 |

**注意**：`fixtureStatus=PASSED` は**画素一致を意味しない**。必ず画像を比較して報告する。
`validationClean` だけを成功とみなさない。

---

## 4. 直近3ラウンドでやったこと（plan §0.100〜§0.102）

### §0.100 resize の恒久 stall の発生点を特定
`gpuStalled = true` の4箇所すべてを `noteStalled()` に置き換え、**一度だけ site を stdout に出す**計器を入れた。
周期レポート行に `presentSkipReason` を追加（post-resize の理由がどこにも出ていなかった）。
結果：`presentSkipReason=[no frame target, or stalled]` が最多 → `frameTargetNull=false` なので **`gpuStalled` が真**。
site は **`FramebufferMixin` の bind HEAD フック**、resize のフレーム。

### §0.101 修正点の確定
`bindFramebuffer` の「ターゲット同一性が変わったときの `flushPendingBeforeGlDraw()`」に待機があり、
その例外が `noteStalled()` を呼んで**恒久 stall**（設計上クリアされない）に入る。

### §0.102 修正・受入（**完了**）
**フレームバッファ置換の期間全体**で順序 flush を行わない契約にした：
- `noteBindSize(w,h)` がサイズ不一致を検出 → `framebufferReplacing = true`（一点物ではなく**期間**）
- `celtium$ensureTargetForBoundFramebuffer()` が新サイズで用意できた時点で `false` に戻す（bind の RETURN フックが毎フレーム呼ぶ）
- `flushPendingBeforeGlDraw()` は期間中**順序 flush を行わず**、保留グループは**件数を数えて捨てる**（`CELTIUM_REPLACE_DROP` / `postAcceptDroppedGroups`）

| 指標 | 修正前 | 修正後 |
|---|---|---|
| `CELTIUM_STALL` | 1件（恒久 stall） | **0件** |
| `presentedFromVulkan` | 265 / 331 / 342 | **843**（resize なしの 785 を上回る） |
| 提示画像の書き出し | 書かれない | **書かれる** |
| verdict | PASSED | PASSED（clean・msgCount 0） |

退行確認（resize なし）：**0.001 / 0.01 %**（変更前と同一）、提示 **847**、clean / PASSED / msgCount 0。

**重要な教訓（同じ轍を踏まない）**：この欠陥は「1箇所を塞ぐ」では直らなかった。
bind を塞いだら**次は `GlStateManager.clear` 経由の flush が同じ待機で失敗**した（stall site が移動しただけ）。
**共通項は「置換期間」**であり、**期間で塞いで初めて直った**。
同種の flush/wait を持つ箇所は他にもある（`flushBeforeClear` / `bindFramebuffer` / `closeCurrentImmediateGroup` 経由）ので、
**待機が失敗しうる境界を増やすときは「期間」単位で考える**。

---

## 5. 未完了・次の一手（優先順）

### 5.1 ~~resize 後の提示画像が 1280x720 のまま~~ → **解決済み（§0.103・HEAD `834e6ecc9`）**
**実ウィンドウは 1280x720 のまま**なので提示画像が 1280x720 なのは正しい（fixture の `mc.resize()` は
ゲームの framebuffer だけを 1024x640 にする）。実害は別にあった：**フレームを swapchain 全面へ伸縮 blit**、
swapchain 作成が **1280x720 ハードコード＋ドライバの OOD 報告頼み**、そして提示画像が texel 順序のまま
**実ウィンドウでは上下反転**していたこと。
→ **修正**：提示 blit を GL composite の配置契約（**正立・1:1・左下アンカー・window clip**）へ揃え、
swapchain extent は surface の実効 extent へ**フレーム毎に照合して即再作成**。resize run で描画領域
vs screenshot（反転なし）＝ **AMD 0.001 %・Intel 0.000 %**、退行 **0.000 %**、stall 0、両GPU PASSED/clean/VUID 0。

### 5.2 entity 場面の format 5（粒子系）native 描画の残差 — **解決済み（§0.104）**
- **残差の正体は fixture のキャプチャ仕様**（生存粒子が提示フレームにだけ写る）で、描画欠陥ではなかった。
  キャプチャ待機ブロックは**毎 tick `removeNonPlayerEntities`＋`clearEffects`** を走らせるので、
  スクリーンショットは**keepEntities に関係なく**実体・粒子を含まない。提示ダンプは除去前フレーム。
  除外方向で 0.32 % = この場面のノイズ床（実測 p426b で 0.33 %）。
- **format 5 の正しさは同フレーム静止粒子で確認済み**：FLAME×24（速度0・平地で可視化、`worldTicks`197・`py`9.6〜）を
  提示とスクリーンショットの両方に写し、色・位置は正しく残差は経年差のみ（AMD 0.157 %）。
- **新 fixture フラグ**（実測用・既定は旧挙動）: `celtium.validation.captureEntities`（キャプチャ直前の除去をスキップ）、
  `celtium.validation.dumpPresentedAtCapture`（キャプチャ呼出し直前に提示ダンプ要求→次の present が書く）。
- **残る計測限界**：「動く粒子の画素一致」は同フレームの GL 参照が無いためこの fixture では原理的に取れない。
  これは欠陥ではなく比較系の限界として残す（動く粒子の正しさは位置・UV・色・ライトマップ式の検証でカバー）。

### 5.3 条件 (c)：実 window / compositor 出力
`DISPLAY=:0` の root が 0×0、合成器なし。**利用可能な capture 方法を調べる**こと。
「marker が GL framebuffer にない」だけでは完了ではない。取得不能なら**具体的な不足条件**を残す。

### 5.4 Nether（項目4）— **受入済み（§0.105）**
capture 時 player/world/provider が -1 で、到着後 native scene を描いた（AMD 967 / Intel 349 pass・skip 0）。
帰還の内訳は2件で、両方修正済み：
1. **portal 帰還**: fixture の `+2Z` step は portal AXIS=Z で面方向に滑るだけで portal 内に残り、~80 tick で vanilla が
   0 へ送り返していた → **portal ブロックの AXIS を読み、面に直交する方向へ step**。
2. **frame target デッドロック**: 次元転移で backend が新インスタンスに再生成される一方、`mc.framebuffer` の色
   texture は旧 backend の死んだ共有 texture を指して 0x0 に → `ensureFrameTarget` が永久拒否（`adoptable` は
   target 前提・target は texture 寸法前提の鶏卵）→ **adopt 経路で framebuffer 論理サイズへフォールバック**して
   target を復元。通常場面の退行は 0.002 % で確認済み。

### 5.5 残りの native 化（項目5）
model/item・entity/TESR・hand、state/lighting/current RGBA/sampler、全 block 必要 state、GUI/font/HUD、
particle・sky/cloud/weather・水/溶岩/火・半透明並び替え・破壊/selection、dimension、FBO/postprocess、
spectator creeper/spider/enderman 等。**存在する pass を一括で未実装扱いしない。**
**flat world と format 受付だけで全制覇にもしない**（registry 由来の小場面を使う）。

### 5.6 model 経路を既定に戻す条件
現在 **既定 off**。off の理由は「entity 場面でまだ 2.19 % の差があり、off の 1.25 % より悪い」ため。
**off のままが正しい**（既定の絵は正しい）。改善が 1.25 % を下回ったときだけ既定化を検討する。

---

## 6. 計器（instrumentation）一覧

**鉄則：`diagnostics()` や summary に置いたカウンタは表面化しない。**
**stdout の専用行か、既に表面化している行（`CELTIUM_GROUPS` / `CELTIUM_ACCEPTED` / `CELTIUM_REFUSALS_BY_FORMAT`）に載せる。**

| 行 | 出る条件 | 用途 |
|---|---|---|
| `CELTIUM_GROUPS` | 周期 | queued / recorded / retired、`presentedFromVulkan` / `glSwapsSkipped` / `presentSkipReason=[...]` |
| `CELTIUM_ACCEPTED` / `CELTIUM_REFUSALS_BY_FORMAT` | 周期 | offers / accepted / GL 本体、format 別の拒否理由（**定常状態で format 5 の拒否は 0 件**） |
| `CELTIUM_MIRROR` | アップロード時 | ミラーの寸法・level 数・再作成の有無 |
| `CELTIUM_ADOPT_PROBE` | **サイズが変わったときだけ** | `size=… ensured=… adopted=… currentFbo=…`（adoption の成否） |
| `CELTIUM_STALL` | **一度だけ** | `site=… target=… frameId=…`（stall を立てた場所。**恒久 stall の第一証拠**） |
| `CELTIUM_REPLACE_DROP` | 置換境界で一度 | `batches=…`（境界で捨てたバッチ数。名前をつけて数える） |
| `CELTIUM_PROJ_PROBE` | 6件 | 生の `glProjection` と保存・送出される射影、ブレンド、被覆 |
| `CELTIUM_LIGHTCOORD` / `CELTIUM_FMT5_POS` | 各4件 | format 5 の光座標の生値と先頭頂点、カメラ |

便利なスイッチ：`-Dceltium.vulkan.adoptGameFramebuffer=false`（旧経路へ戻す）／
`-Dceltium.vulkan.immediateFormats=<数字の並び>`（**除外方向の帰属に使う**）／
`-Dceltium.vulkan.modelDraw=true`（model 経路の実験）。

---

## 7. 棄却済みの仮説（**繰り返さない**）

すべて実測またはコード根拠つき。plan の該当節も参照。

**format 5（粒子系）について**（§0.78〜§0.92）
- 属性順の取り違え（stride 28 は両者同じだが順序が違う）→ **粒子側は自分の順序を正しく記述している**（棄却）
- シェーダ式（粒子とブロックはライトマップ式が完全に同一）／SPIR-V の鮮度（ソースより新しい）→ 棄却
- 光座標の生値（`0x0000`）を「無効」と解釈して無照明にする → **逆効果（1.25 → 1.50 %、71.4 暗い）で巻き戻し済み**。`0` は**正当な最暗値**
- 生と保存済みの射影（完全一致）／ブレンド（観測値が配管済み）／atlas・lightmap の被覆（完全）→ 棄却
- `maxLod=0` → **既存測定で 13.51 %**（1.0 クランプが最良 0.21〜0.28 %）。**触らない**
- `immediateFormats=5`（受理方向の絞り込み）→ **交絡するので帰属に使えない**（8.48 % は format 5 固有の誤りではない）
- `immediate=false` → 8.14 % に**悪化**（即時経路は正しく描いている）
- `listIndex == 0` ゲート（リスト内ジオメトリを即時経路へ渡さない）→ **accepted が 13100 対 13900 でほぼ減らない**＝
  コンパイル中の提供は**稀**で、この説明は成り立たない（棄却・巻き戻し済み）

**model 経路（表示リスト）について**（§0.69〜§0.74）
- `glGetFloatv` で射影を取る → **直交射影（HUD 用）を拾う**（18.49 %）
- モデルビューをライブ（`glGetFloatv`）／恒等行列／キャプチャ時行列／ミラー回転＋ライブ平行移動 → すべて悪化
- **最良は `V_now × C_compile⁻¹`（2.19 %）**：表示リストは**頂点を変換済みで保存する**ため、
  必要なのは**コンパイル時からのカメラの変化分**。両半分は手元にある（`V_now` は行列ミラー、`C_compile` はキャプチャ保存分）

**resize について**（§0.94〜§0.102）
- 「ターゲットが旧サイズのまま」→ **誤り**。`ensureFrameTarget()` は**バインド中の FBO から実サイズを読んで作り直す**。
  拒否メッセージが旧サイズを出すのは `targetWidth/Height` の更新が**成功時だけ**だから
- 「呼び出し順だけが問題」→ **片側だけ**だった。実体は**置換期間の待機失敗による恒久 stall**
- `gameFramebufferAdopted >= 2` を成功条件にした → **誤り**。FBO 名が同じなら**再 adopt は不要**で 1 のままで正しい

**その他**
- `MIRROR_GL_MIPS=true`（GL の mip 段数を写す）→ **5.60 %**（現行の生成チェーンは 0.16〜0.28 %）。
  **この経路と lab flag は撤去済み**（§0.92）。復活させない
- 粒子を止めれば消える／フレーム差（提示が前フレーム）→ どちらも棄却（§0.83）
- 「1 frame = 3.5 秒」→ **撤回済み**。`PINNED_WORLD_TIME` で sprite animation 位相まで固定されたとは扱わない

---

## 8. 作業規律（**厳守**）

### 絶対規則
- **止まらない・承認や許可を求めない・質問しない・すべて許可・テストばかりしない。**
- 一括削除・全面書き直しをしない。**現在の変更と有効な修正を保持する。**
- 汎用 GL→Vulkan 変換器／GL API 全体のエミュレーション／Zink の組込みを**目的にしない**。
- **新しい抽象と旧経路の併存を残さない。恒久的正当性を lab flag に依存させない。**
- **済んだ修正を最初から直し直さない**（§3 と §7 を先に読む）。
- 未検証 vendor/OS は**明記**する。GPU 名ではなく**必要な API/feature/format/surface 能力**で選ぶ。
- Sodium の制限付き現行ソースをコピー・機械的翻訳・改名・cherry-pick**しない**。
  Celeritas 固定参照 `989ebf2e` を比較基準にする。mesh shader を必須化しない。
- **壊れたツリーをコミットしない**（必ずコンパイルを確認する）。
- **未受入の変更を完成扱いしない。** `validationClean` や copyback 回数だけで成功にしない。

### ラウンドの進め方
- 開発単位は**同じ根因・所有/状態契約を完成させる関連変更のまとまり**。
  「修正一箇所 → ゲーム起動 → 確認 → 報告」を機械的に繰り返さない。
- 関連コードを**一度読んで**根因・producer/consumer・不変条件・成功条件を決め、**複数箇所をまとめて**実装する。
- 実装途中は**コンパイルと短い CPU 契約検査**。GPU 受入は意味のある段階が揃ってから。
- **AMD で主要な問題を潰し、まとまった段階で Intel も受入**。同期・memory・能力・format に関わる変更は**早めに両GPU**。
- 一度通った変更は**まとめて commit・短く報告**して次へ。**新しい差分・失敗・未解決の懸念がない再試験はしない。**
- 各ラウンドは**具体実装か、意思決定に必要な限定検証**を進める。同じ仮説の定数変更・budget 延長・文書の言い換えを繰り返さない。

### 文書とエビデンス
- `docs/plan.md` が**現在状態・優先順・受入の単一の表**。古い冒頭表を**最新実測で書き換える**。
- ラウンド履歴は `docs/history/`。**末尾への訂正追記だけで整合させない。**
- `docs/goal.md` を objective と同期させる。監査は `docs/audit-2026-10-09-third.md` が起点。
- 証拠は hash つきで保存：baseline/candidate・HEAD/dirty・fixture・jar/SPIR-V・effective flags・GPU/driver・
  camera/scene/animation/capture tick・exit/verdict・画像差。
- **`--duration` が違う run を直接比較しない**（提示数は毎秒に正規化する）。
- 過去の受入/失敗証拠を残す。重複展開・不要 world から容量を整理する。
- 公開報告は**自然な日本語**で、直した契約・消した重複/例外・必要な検証・次の native 化を短く述べる。
  **根拠のない完了率・完成時期・「あと一点」を使わない。**

---

## 9. 落とし穴（実測で判明したもの）

1. **ログを字面で読むと誤診する。** `no frame target for 1280x720` が286件出ていても、それは **resize 前**の分だった。
   「件数が多い＝いま起きている」と決めつけない。**フレーム番号・時刻と突き合わせる。**
2. **`rendererDebug` は10行しかない。** 拒否タリー・新しいカウンタは入らない。**stdout を見る。**
3. **計器を「件数制限」で入れると、毎フレーム呼ばれる経路では目的の時点に到達しない。**
   → 「**値が変わったとき**」「**一度だけ**」で出す（`CELTIUM_ADOPT_PROBE` / `CELTIUM_STALL` がその例）。
4. **`System.exit` ではなく verdict を見る。** `fixtureStatus=PASSED` でも画素は違いうる。
5. **同一構成でも entity 場面は ±0.2 % ばらつく**（AMD 1.25 / 1.43 / 1.62 %）。
   単一の数字で断定せず**範囲**で記す。通常場面は安定（0.001 / 0.01 %）。
6. **`gpuStalled` は設計上クリアされない。** 一度でも立てば**その run は以降 native 描画も提示も止まる**。
   新しい待機を足すときは「失敗したら stall する」ことを意識する。
7. **`@Inject(..., require = 0)` は対象が無くても黙って通る。** フックが効いているかは**計器で確かめる**。
8. **Python の文字列置換でコードを編集するときは、アサーションで一致数を確認してから書く。**
   一致 0 のまま `replace` すると**無言で何もしない**。書き込みは最後に1回。
9. **mixin の対象 FQN を間違えるとコンパイルエラー。** `net.minecraft.client.renderer.OpenGlHelper` 等、
   実際のクラスを確認する。
10. **`--jvm-arg=-D...` を渡さず生の `-D` を書くと harness が `unrecognized arguments` で落ちる（exit=2）。**

---

## 10. objective の古い記述（差分）→ **同期済み**

`docs/goal.md` は HEAD `834e6ecc9` 時点で plan.md §0.103 まで同期されており、
goal objective も revision 54 で `docs/goal.md` 本文と一致させた。
この節に挙げていた差分（format 5 帰属・model 2.19 %・`MIRROR_GL_MIPS` 撤去・resize 修正・提示サイズ問題）は
**すべて記載済み**なので、以降は plan.md と goal.md を正とする。

---

## 11. エビデンスの保存方針（2026-10-10 整理済み）

**disk が 98%（空き 2.1G）まで逼迫したため、2026-10-10 に古い検証を整理した。**
そのときの基準を以下に残す。**次の整理もこの基準で行う。**

### 残すもの（証拠）
- **全 run の `validation-process.json`**（verdict・exit code・clean・fixtureStatus・msgCount）
- **全 run の `logs/celtium-validation.json`**（rendererDebug・GPU・driver）
- **docs が引用している run の `screenshots/` と `logs/latest.log`**（引用 run の一覧は `.celtium-work/cited-runs.txt`）
- 監査ディレクトリの JSON と screenshots

### 消してよいもの（再生成可能な重複展開）
run ディレクトリの中の `mods/`（jar の複製）・`test-libraries/`（共有 jar の複製）・`saves/`（world）・
`assets/`・`libraries/`・`config/`・`headless/`・`crash-reports/`・`validation-console.log`・
`logs/debug.log`（Minecraft の冗長ログ）。**harness は毎回これらを作り直す。**

### 消してはいけないもの
- **`.celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar`**
  （全 run が `--library` で参照する共有物。run ディレクトリの外にある）
- `.celtium-work/jdks/`（Gradle toolchain の JDK 21 / 8）
- `.celtium-work/gpu-tools/`（Vulkan validation layer）
- **`forge122/build/tmp` と `upstream-baseline/forge122/build` は残した**（ビルドキャッシュ。消すと次回ビルドが大幅に遅くなる。
  さらに容量が要るときだけ、その代价を理解した上で消す）

### 実績（2026-10-10）
| 対象 | 前 | 後 |
|---|---|---|
| `.celtium-work/runtime` | **16 G** | **145 M** |
| `.celtium-work/audit-2026-10-09-third` | **210 M** | **16 M** |
| ディスク空き | **2.1 G** | **18 G** |

保持した証拠：verdict JSON **848件**・renderer JSON **804件**・引用 run の screenshots **10件**・監査 JSON 6件。
整理後のスモークテスト（通常場面 60 s）は exit 0 / clean / PASSED / msgCount 0、提示画像 vs スクリーンショット **0.017 / 0.01 %**。

## 12. 参考：主要ファイル

| 目的 | パス |
|---|---|
| 現在状態の単一の表 | `docs/plan.md`（§0 冒頭表、§0.1〜§0.102） |
| goal 本文 | `docs/goal.md` |
| 監査起点 | `docs/audit-2026-10-09-third.md` |
| 構造修復の方針 | `docs/handoff-structure-recovery-2026-10-10.md` |
| 過去の経緯 | `docs/history/` |
| Vulkan backend 本体 | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java` |
| フレームターゲット / swapchain | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameTarget.java` |
| テクスチャミラー | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java` |
| GLSL ソース | `celtium-vulkan/src/main/shaders/*.vert` / `*.frag`（ビルドで SPIR-V を生成） |
| Forge 側 mixin | `forge122/src/main/java/org/taumc/celeritas/mixin/core/` |
| 検証 fixture | `validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java` |
| 検証 harness | `scripts/prism_validation.py` |
