# leg683_census.py — 項目683 修正検証 census の leg ハーネス

項目701 の準備成果物。設計の正本は本体側の記録 `item701-prep.md`
（night-queue POST-RELEASE-2026-11-22、レーン alpha-item701-prep）で、
判定規則はそこの §5 に事前固定されている。要点だけここに写す。

- 目的: 項目683 修正（`RenderSectionManager.isRebuildDataFinal` フック、
  alpha-bugfix @bdfb6eae8）の検証 census。terrain2 再現条件
  （実パック「Nostalgic Hardcore」private コピー、入場点
  (24849.5, 200, 34436.5)、-Xmx8192m）で
  CELTIUM_PRESENT_CENSUS（提示側の排他分解）と CELTIUM_PREPARE_NULL
  （prepare null の理由内訳: BLANK_CHUNK / SLOT_ABSENT / SLOT_EMPTY /
  Y_OUT_OF_RANGE）を対で読み、捏造されていた EMPTY 終端が消えたかを判定する。
- jar 基点は秘書第20代裁定 #4992: 実施時点の tip に
  `instrument/present-census-683` @0909b366c を必要に応じ cherry-pick した
  leg 専用合成 jar。**683 修正を含まない jar で検証腕を回さない。**
  provenance は prep manifest に記録する。
- 修正前 arm の実測（683 leg 腕1）: frame=80 で visible=898 drawn=212
  terminalBuilt=678（76%）。判定の主は提示 census の分解の移動
  （terminalBuilt の崩れと drawn の伸び）に置き、prepare null 内訳は
  機構側の証人とする。両者をセクション単位で等号で結ばない
  （null 件数は呼び出し回数であってセクション数ではない）。
- yOutOfRange > 0 は y 計算側の別バグとして即起票。
- 実施は item683-census-leg が PC 解禁後、項目697 の直後に fixture を
  取って行う。サブコマンド: preflight / prep / run / aggregate / restore。
