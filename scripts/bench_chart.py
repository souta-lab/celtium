#!/usr/bin/env python3
"""Draws the README benchmark chart (light and dark SVG) from a paired-KPI summary.

Input JSON: {"title": str, "note": str, "panels": [{"name": str, "rows": [{"metric": "p50",
"celtium": ms, "celeritas": ms}, ...]}, ...]}. Lower is better; every bar carries its value, and each
mark has a <title> so a browser shows it on hover.
"""
import json
import sys
from pathlib import Path
from xml.sax.saxutils import escape

THEMES = {
    "light": {"surface": "#fcfcfb", "ink": "#0b0b0b", "muted": "#52514e", "grid": "#e4e3df",
              "series": ("#2a78d6", "#eb6834")},
    "dark": {"surface": "#1a1a19", "ink": "#f0efec", "muted": "#c3c2b7", "grid": "#383835",
             "series": ("#3987e5", "#d95926")},
}
NAMES = ("Celtium", "Celeritas 2.5.0-dev")
KEYS = ("celtium", "celeritas")

WIDTH = 760
PANEL_GAP = 28
LABEL_W = 56
BAR_H = 12
BAR_GAP = 2
ROW_GAP = 14
VALUE_W = 64


def render(data, theme):
    t = THEMES[theme]
    top = 78
    panels = data["panels"]
    scale_max = max(max(r[k] for k in KEYS) for p in panels for r in p["rows"])
    plot_w = WIDTH - 24 - LABEL_W - VALUE_W
    row_h = 2 * BAR_H + BAR_GAP + ROW_GAP
    out = []
    y = top
    for panel in panels:
        out.append(f'<text x="24" y="{y}" fill="{t["ink"]}" font-size="14" font-weight="600">'
                   f'{escape(panel["name"])}</text>')
        y += 12
        for row in panel["rows"]:
            ry = y + 4
            out.append(f'<text x="24" y="{ry + BAR_H + 4}" fill="{t["muted"]}" font-size="12">'
                       f'{escape(row["metric"])}</text>')
            for i, key in enumerate(KEYS):
                value = row[key]
                w = max(4.0, plot_w * value / scale_max)
                by = ry + i * (BAR_H + BAR_GAP)
                x0 = 24 + LABEL_W
                # Square at the baseline, 4px rounded at the data end.
                path = (f"M{x0},{by} h{w - 4:.1f} a4,4 0 0 1 4,4 v{BAR_H - 8} a4,4 0 0 1 -4,4 h{-(w - 4):.1f} z")
                out.append(f'<path d="{path}" fill="{t["series"][i]}"><title>{escape(panel["name"])} '
                           f'{row["metric"]} {NAMES[i]}: {value:.2f} ms</title></path>')
                out.append(f'<text x="{x0 + w + 6:.1f}" y="{by + BAR_H - 2}" fill="{t["ink"]}" '
                           f'font-size="11">{value:.2f} ms</text>')
            y += row_h
        y += PANEL_GAP - ROW_GAP
    height = y + 30
    legend = []
    lx = 24
    for i, name in enumerate(NAMES):
        legend.append(f'<rect x="{lx}" y="46" width="12" height="12" rx="3" fill="{t["series"][i]}"/>'
                      f'<text x="{lx + 18}" y="56" fill="{t["ink"]}" font-size="12">{escape(name)}</text>')
        lx += 18 + 8 * len(name) + 24
    head = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" '
            f'viewBox="0 0 {WIDTH} {height}" font-family="system-ui, -apple-system, Segoe UI, sans-serif" '
            f'role="img" aria-label="{escape(data["title"])}">'
            f'<rect width="100%" height="100%" rx="8" fill="{t["surface"]}"/>'
            f'<text x="24" y="28" fill="{t["ink"]}" font-size="16" font-weight="600">{escape(data["title"])}</text>')
    foot = (f'<text x="24" y="{height - 12}" fill="{t["muted"]}" font-size="11">{escape(data["note"])}</text></svg>')
    return head + "".join(legend) + "".join(out) + foot


def main():
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    for theme in THEMES:
        (out_dir / f"benchmark-{theme}.svg").write_text(render(data, theme) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
