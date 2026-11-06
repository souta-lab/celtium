# README rules

The README is the product page. It is read by players deciding whether to install Celtium, so it states what is
measured, in the units a player uses, and nothing more. These rules apply to every README change.

## Units and numbers

- **FPS only.** Performance is written as FPS (higher is better), never as frame time. FPS = 1000 / frame time in ms;
  the KPI still measures frame times, and the conversion happens in the chart generator and the tables.
- Percentiles are explained once: p50 is the typical frame, p95/p99 the slowest 5% / 1% of frames.
- Every number names its **build** (commit or release), **GPU**, **pack and version**, **scene/pose**, **pair count**
  and **run names**, and links its evidence file. A number without them does not go in.
- Relative results are written as "+x% FPS" against the named reference, and say when a value is the median of
  per-pair ratios rather than the ratio of two medians.
- The goal verdict is stated honestly. If `goalCondition` is `NOT_MET`, the README says so, next to the numbers.

## Modpacks

- **Name the modpack, its version and its mod count** (for example "Supersymmetry 0.2.0.2, 188 mods"). Never call a
  measurement "the modpack" or use an instance directory name (`celtium-world-accept`) as if it were a pack name.
- A non-modpack instance (for example the small acceptance instance behind the 0.1.1-alpha numbers) is labelled as
  such and kept apart from modpack numbers.
- Compatibility rows say what passed (world reached, validation count, GPUs) and name the runs; correctness legs are
  not presented as FPS measurements.

## Charts

- Charts are generated, never hand-edited: data lives in `docs/img/<name>.json` (frame times in ms, as measured) and
  `python3 scripts/bench_chart.py docs/img/<name>.json docs/img <name>` writes `<name>-light.svg` and
  `<name>-dark.svg`. The README embeds both through `<picture>` with a descriptive `alt`.
- Every chart in the README uses the same generator and style, and its title names the build, the reference and the
  pack.

## Badges and structure

- Badges are shields.io badges in `style=for-the-badge`, written as HTML `<a><img></a>` in two `<p>` rows (platform
  first, then performance/status/license), with a simple-icons logo where one exists. Each links to the README section
  or page that backs it (requirements, release, performance, known issues, license). Static badges, including the
  FPS badge, must be updated in the same commit as the fact they state.
- Top of the page, centred: the logo (`docs/img/logo-{light,dark}.svg` through `<picture>`), the name, a one-line
  summary, the two badge rows, the name-origin line; then a "📑 Contents" list linking every `##` section.
- Every `##` heading starts with one emoji, and only single-codepoint emoji (no U+FE0F variation selector, so not
  ⚠️ ⚖️ 🛠️): GitHub keeps the invisible selector in the anchor, which breaks `#-section` links. Anchors are therefore
  `#-<slug>`; check every internal link resolves after a heading change.
- Order: name and one-line summary, badges, contents, what it is, highlights, performance, modpack compatibility,
  requirements, install, scope, known issues, building and testing, documentation, license and references.
- Highlights are claims a reader can check in the sections below; no highlight without a number or run behind it.

## Updating

- When a new paired KPI changes the verdict or moves a number by more than its pair noise, update the JSON, regenerate
  the charts and the table in the same commit, and state the date and build.
- Release-specific detail (what changed in a version) belongs in `docs/releases/`, not in the README.
- English only in the README body; the license line at the end stays as written.
