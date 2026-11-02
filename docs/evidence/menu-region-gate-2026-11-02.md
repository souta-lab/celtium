# menu の上部帯を floor で判定する — 背景/banner が黒く描かれた frame を PASSED にしない（2026-11-02）

renderer `7426ce5a71c32e612bb58b7af2a9f06b28953df32050c0cb396aa3a1fd9c7530`、
fixture `9e920ab1822f9cc7467994da26271f5977b1a6322aec883824fcfa566834ce94`。
**この round は実機 leg を回していない**。下の 4 枚は既にコミット済みの capture で、判定式はその 4 枚に対して
検証した。ゲート本体の初回実行は次回の menu leg になる。

## 観測: 絵は 2 GPU で確認済みだが、判定はしていなかった

r14e（AMD）と r16d（Intel）で title screen の背景・banner・frame・button が presented frame に出ることは
確認済み（[menu-frame-colour](menu-frame-colour-2026-11-02.md)、両 GPU で `CELTIUM_LEGACY_RUN` の colour が
1.0,1.0,1.0,1.0、`taken=true`）。しかし menu stage は scene の verdict が判定する stage ではなく
（`ValidationClient` の menu dump 節: "its stage is not one the verdict judges"）、**r14c/r14d の
「背景と banner が黒で描かれた」frame（mean 6.66 / 非黒 0.0446）でも menu leg の verdict は PASSED のまま**だった。
fidelity の単位が「人が一度見た」で止まっており、次の回帰を捕まえる floor が無い。

## 変更: 1280x720 の上部帯に対する 2 つの floor

title screen の art が占める帯を、画面に対する比で固定した:

```
x in [0.03, 0.97]   y in [0.05, 0.31]      （1280x720 で (38,36)..(1242,223)）
mean luma >= 20.0            non-black share >= 0.30      （black は luma <= 8）
```

band は r14e の legacy census が示した banner quad の screen 座標 (60,40)..(1220,200) を包含するよう広げた
（別 GUI scale でも banner が帯の外に出ないため）。floor は**動いている 2 枚のうち暗い方（Intel）の 2.8 倍下**、
**欠陥 2 枚はどちらも 0.0** という位置に置いた。

| 画像（コミット済み） | 条件 | mean | non-black | 判定 |
|---|---|---|---|---|
| [menu-frame-art-2026-11-02.png](menu-frame-art-2026-11-02.png) | AMD RX 6400 r14e、pack resources あり、色キャッシュ修正後 | **89.565** | **0.925094** | 通る |
| [menu-region-intel-2026-11-02.png](menu-region-intel-2026-11-02.png) | Intel UHD 630 r16d、同修正、presented frame 100 | **57.488** | **0.835704** | 通る |
| [menu-frame-black-colour-2026-11-02.png](menu-frame-black-colour-2026-11-02.png) | AMD r14c/d、修正前（背景/banner が黒） | 0.000 | 0.000000 | **落ちる** |
| [menu-frame-no-pack-2026-11-02.png](menu-frame-no-pack-2026-11-02.png) | AMD r14b、pack resources 無し（missing texture） | 0.000 | 0.000000 | **落ちる** |

Intel の画像は leg `r16d-intel-menu-frame` の kept copy（`presentedFrame=100`、`source=presented-dump`、
`1280x720`、identity の `imageHash=-210821403`、`completionError=none`）をそのままコピーした。
sha256: AMD `fc45eaf7…`、Intel `1efaf8f9…`、pre-fix `2ca765c4…`、no-pack `cb4bf40c…`。

`ValidationClient`:

- 定数 `MENU_TOP_X0..Y1` / `MENU_TOP_MIN_MEAN_LUMA` / `MENU_TOP_MIN_NON_BLACK` / extent 1280x720 を追加。
- `celtium$gateMenuStage()`: leg が menu dump を要求した時だけ `celtium$evidence.get("menu")` を見る。
  - 要求なし → `menuRegionGate=NOT_APPLICABLE`（失敗にしない）
  - 未完了・読めない → `NOT_CAPTURED`
  - 1280x720 以外 → `NOT_CALIBRATED`（帯は画面比なので別解像度では判定しない）
  - ファイルの hash が identity と違う → 失敗（その frame についての主張ではない）
  - 帯の mean / non-black を report に出し、floor 未満なら **verdict の failures に追加**。
- report: `menuRegionGate`（PASSED/FAILED/…）、`menuRegionSource`、`menuRegionRect`、`menuRegionMeanLuma`、
  `menuRegionNonBlackFraction`。PASSED のときは `CELTIUM_MENU_REGION_GATE status=PASSED mean=… nonBlack=…` を 1 行。
- 呼び出しは `celtium$judgePictures` の scene stage 判定の直後。menu 絵を要求しない leg の verdict は不変。

**変更の性質**: 判定を足すだけで描画経路・record・shader には触れていない。既存 leg で menu dump を要求して
いない run は `NOT_APPLICABLE` の 1 property が増えるだけ。

## CPU 検証（この round で実施）

`python3 scripts/cpu-contract/menu-region-floor-check.py`（新規、GPU 不要）:

```
menu top band x=0.03..0.97 y=0.05..0.31; floors mean>=20.0 nonBlack>=0.3 (black luma<=8)
  WORKING AMD r14e     mean=  89.565 nonBlack=0.925094 rect=(38, 36, 1242, 223) -> PASSED
  WORKING Intel r16d   mean=  57.488 nonBlack=0.835704 rect=(38, 36, 1242, 223) -> PASSED
  DEFECT  pre-fix black art mean=   0.000 nonBlack=0.000000 rect=(38, 36, 1242, 223) -> CAUGHT
  DEFECT  no pack resources mean=   0.000 nonBlack=0.000000 rect=(38, 36, 1242, 223) -> CAUGHT
OK: both GPUs' working menu frames clear the floors and both measured defects are below them.
```

この check は `ValidationClient.java` から 4 つの band 定数と 2 つの floor を読み、check 側の値と違えば失敗する
（画像とゲートが黙ってずれないようにするため）。build は 236 tests / 0 failures、route verify は
called=241 failed=0・classes 87/87、`scripts/tests` は 106 tests OK（下の check を実行する
`test_menu_region_floors.py` を含む）、`check_agent_docs.py` は OK
（`docs/instruments.md` に `CELTIUM_MENU_REGION_GATE` を再生成）。

## まだ言えないこと

- **ゲートの実機実行はまだ**。表示セッションが無いため、`celtium$gateMenuStage` が runtime で通ることは
  未確認（コンパイルと、判定式の 4 枚に対する検証まで）。
- Celeritas reference arm は presented dump を要求しない（`requestPresentedDump` が拒否し、menu は `menu-gl`
  screenshot になる）ので、このゲートの対象外。menu-gl 側を同じ帯で判定するのは次の単位。
- 帯は「menu の上側に絵がある」ことだけを言う。banner そのものの位置・内容・アニメ位相は判定しない
  （r14e の banner band の数値は上の表の帯に含まれるが、banner 固有の floor はまだ置いていない）。

## 次に測る手順

AMD と Intel で、既存の menu leg と同じ条件に `-Dceltium.validation.dumpPresentedMenuTick=40` を付けて走らせ、
`menuRegionGate=PASSED` と `menuRegionMeanLuma` / `menuRegionNonBlackFraction` が上の calibration に近いことを
確認する。FAILED になった場合は、報告に帯の 2 数値と `menuRegionRect` が入るので、画像なしでも原因を切り分けられる。
