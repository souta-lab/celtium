# creative inventory: a scene, a reference pair, and the item icons it is missing (2026-11-02)

Renderer `2a2d71a7d7c90ac79ea0b467f2a12181045bb01673cf730d55b74f9929b6dbf3`（`main` f7f131c29 + 本差分の
fixture）。Reference `01-celeritas-forge-mc12.2-2.5.0-dev.jar` sha256 `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c`。
fixture は両armで同一 `0901e99e1c087a7d98c41614cf88df37b79ad60342cb900b753e74d98ed17f8d`。
AMD Radeon RX 6400 (RADV NAVI24)・mode `plain`・X11・Supersymmetry instance・1280x720・`guiScale3.txt`
（guiScale=3, renderDistance=12）・`resources` 5901 files を運搬・validation 既定ON。

## 観測: 4症状のうち creative tab には scene が無かった

rc.26 の known open issues は「main-menu background / tooltip afterimage / blocks flickering / only some creative
inventory tabs rendering wrong」の4つ。menu は r14e/r16d で両GPU確認済み、tooltip は保持runの修正があるが、
creative inventory は **fixture が開けなかった**（`celtium.validation.screen` は language/resourcepacks/mainmenu のみ）。
scene が無いので「どのtabがどう壊れているか」を絵で言えない状態だった。

## 変更

1. `celtium.validation.screen=creative` で `GuiContainerCreative` を開く。creative world（fixture の world は
   `GameType.CREATIVE`）でのみ開き、非creativeでは vanilla 自身が `GuiInventory` に落ちるので、report は
   **実際に開いたclass**を書く。
2. `celtium.validation.creativeTab=<index>` で `CreativeTabs.CREATIVE_TAB_ARRAY` の tab を選ぶ。切替は画面自身の
   private `setCurrentCreativeTab`（MCP `setCurrentCreativeTab` / SRG `func_147050_b`）で、tab click が呼ぶのと
   同じmethod。選んだ結果（label・translationKey・tabPage）と、**grid の中身**（container の slot 数・stack の入った
   slot 数・先頭stack の表示名）を report に出す。「tabが開いた」を呼び出しの戻り値ではなく絵の中身で言うため。
3. `celtium$screenVariant()` に named screen を追加（`screen=named:creative`）。この knob は world の capture まで
   画面を開いたままにするので、絵は terrain の floor が記述する絵ではない。これまで key は `screen=none` のまま
   `sceneCalibrationKeyMatches=true` になり得た（A03 と同じ形の穴）。追加後は named screen の leg は
   `sceneCalibrationKeyMatches=false` / `UNCALIBRATED_PRESENTED_IMAGE`（exit 2）になり、**未較正を PASSED と呼ばない**。

## 実測（leg `r21d-creative` = Celtium、`r21e-creative-ref` = Celeritas。同一scene・同一flags・同一fixture）

| 項目 | 両arm |
|---|---|
| 開いた画面 | `net.minecraft.client.gui.inventory.GuiContainerCreative`（tick 31–38） |
| `screenshotAtTickScreen` | 同class（tick60 の capture 時に開いていた） |
| tab 数 / 選択 | 92 / index 12 = `littletiles`、`creativeTabPage=1`（page 2、矢印あり） |
| grid | 54 slots・46 stacks・先頭 `Little Hammer x1` |
| `sceneCalibrationKey` | `… screen=named:creative guiScale=3`（参照keyは `screen=none`） |
| verdict | 両arm `INCONCLUSIVE`（`UNCALIBRATED_PRESENTED_IMAGE`）。未較正sceneとして正しい |

**Celeritas と同一のもの**: panel の位置・大きさ（345..930 x 156..564 px = 195x136 units @ scale 3）、tab strip と
page 矢印、`1 / 9` の page 表示、JEI panel（右、`1/164`）、tooltip の枠・行構成・**左の灰色icon**（両armで同じ）、
scrollbar、hotbar、背景。つまり画面の骨格・scale・行列は一致している。

**一致しないもの**: creative grid の item icon。Celeritas は 46 stack の icon が並ぶが、Celtium は
**1行目（8個）と row2 col6 の暗いcube以外が描かれない**。tooltip に隠れていない 24 cell のうち **19 cell が
mean|A-B| > 6**（row0 は全9 cell が 0.0–2.1 で一致、row1–4 の一致は 5 cell のみ）。画像:
[grid pair](creative-inventory-grid-pair-2026-11-02.png)（上=Celtium、下=Celeritas）、
[Celtium 全画面](creative-inventory-celtium-2026-11-02.png) / [Celeritas 全画面](creative-inventory-celeritas-2026-11-02.png)。

tick60 と world capture（tick327）の2枚で同じ欠落 → 単発の部分frameではなく、そのsceneで再現する。

## 言えないこと（この round で否定も断定もしていない）

- **根因は未特定**。`modelDrawRefusals=0`・`immediateRefusal=none`・`droppedByFormat` 全0 なので、
  「renderer が拒否した」形ではない。item の draw 自体が届いていないのか、届いて不可視なのかを分けていない。
- 欠落itemの共通性質（`isGui3d`・cull・item model の transform・atlas sprite・TESR か）は未分類。
  grid の欠落は **item 単位**で、行順ではない（row1 col5 の暗いcubeは描かれ、col4 の板は描かれない）。
- **capture 手段が arm で違う**: Celtium は presented dump（swapchain readback）、Celeritas は game 自身の
  GL screenshot。presented dump が item を落とす可能性は、同じ dump で JEI panel の item が描かれていること・
  同じ欠落が tick60 と tick327 の2枚で一致することで下げたが、**否定はしていない**。次roundは同一 arm 内で
  `-Dceltium.validation.screenshotAtTick` と `-Dceltium.vulkan.frameDumpEvery` の2経路を比べる。
- FPS・validation の主張はしない。両armとも validation message 0・VU 0（reference arm は GL）。

## 次の判別（次roundの計器）

1. `-Dceltium.debug.itemCensus=true`（40行上限）で GUI stage の item 形式の受理・頂点・texture を見る。
2. `-Dceltium.native.modelInstances=false` との同一jar A/B: 欠落itemが戻るなら instanced model path、戻らないなら
   immediate/item path。
3. `-Dceltium.vulkan.groupBoundsFormat=...`（既存）で、欠落するはずの item の group 矩形が出るか（届いていない）/
   出るが画素が無い（不可視）を分ける。
4. 欠落itemの model を mods 側で確認（LittleTiles の premade item と vanilla の door/planks/chest が同じ運命か）。
   同じなら Celtium の共通経路、違うなら mod 固有経路。
