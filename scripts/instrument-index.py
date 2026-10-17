#!/usr/bin/env python3
"""Celtium の計器索引（docs/instruments.md）を生成する。

なぜ要るか: 2026-10-17 時点で `-Dceltium.*` のスイッチは 350 個以上あり、
どの計器がどこにあり何を出すのかを毎回ソース全体の grep で探していた。
この索引は「名前 → 読み取り位置(file:line)」の表なので、
`grep <名前> docs/instruments.md` で実装箇所へ直行できる。

使い方:
    python3 scripts/instrument-index.py          # docs/instruments.md を再生成
    python3 scripts/instrument-index.py --check  # 差分があれば非ゼロ終了（CI 用）

規約: 計器を足したらこのスクリプトを走らせ、docs/instruments.md を同じ commit に含める。
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "instruments.md")
SCAN_DIRS = ("celtium-vulkan/src", "forge122/src", "common/src", "validation", "celtium-interop/src")
SKIP_PARTS = (".celtium-work", "/build/", "/.git/", "run-", "modpack-p0")

PROPERTY = re.compile(r'"((?:celtium|celeritas)\.[A-Za-z0-9._-]+)"')
ACCESSOR = re.compile(r"(getProperty|getBoolean|getInteger|getLong|getDouble|getPropertyBoolean)")
MARKER = re.compile(r'"(CELTIUM_[A-Z0-9_]+)')


def java_files():
    for base in SCAN_DIRS:
        top = os.path.join(ROOT, base)
        if not os.path.isdir(top):
            continue
        for dirpath, dirnames, filenames in os.walk(top):
            if any(part in dirpath for part in SKIP_PARTS):
                continue
            dirnames[:] = [d for d in dirnames if d not in ("build", ".gradle")]
            for name in filenames:
                if name.endswith(".java"):
                    yield os.path.join(dirpath, name)


def collect():
    props: dict[str, list[tuple[str, int, str, str]]] = defaultdict(list)
    markers: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for path in java_files():
        rel = os.path.relpath(path, ROOT)
        try:
            lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
        except OSError:
            continue
        for number, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("*") or stripped.startswith("//"):
                continue
            for match in PROPERTY.finditer(line):
                name = match.group(1)
                tail = line[match.end():]
                default = ""
                if "getBoolean" in line:
                    default = "false"
                else:
                    d = re.search(r",\s*([^)]+?)\)", tail)
                    if d:
                        default = d.group(1).strip()
                        default = re.sub(r"\s+", " ", default)[:24]
                props[name].append((rel, number, default, ""))
            for match in MARKER.finditer(line):
                markers[match.group(1)].append((rel, number))
    return props, markers


def group_of(name: str) -> str:
    """2階層でまとめる（`celtium.vulkan.*` / `celtium.validation.*` …）。

    3階層で切るとほぼ1スイッチ=1見出しになり、一覧として使えなかった（2026-10-17 実測）。
    """
    parts = name.split(".")
    return ".".join(parts[:2])


def render(props, markers) -> str:
    out = []
    out.append("# Celtium 計器索引（自動生成）")
    out.append("")
    out.append("**このファイルは `python3 scripts/instrument-index.py` が生成する。手で編集しない。**")
    out.append("")
    out.append(f"`-Dceltium.*` / `-Dceleritas.*` のスイッチ **{len(props)} 個**と、")
    out.append(f"ログに出る `CELTIUM_*` マーカー **{len(markers)} 個**の読み取り位置。")
    out.append("")
    out.append("探し方: `grep <名前> docs/instruments.md` → 出た `file:line` を読む。")
    out.append("既定値は読み取り式から機械的に拾ったもので、条件分岐の中身までは表さない。")
    out.append("")
    groups: dict[str, list[str]] = defaultdict(list)
    for name in props:
        groups[group_of(name)].append(name)
    out.append("## 名前空間の目次")
    out.append("")
    out.append("| 名前空間 | スイッチ数 |")
    out.append("|---|---|")
    for group in sorted(groups):
        out.append(f"| [`{group}.*`](#{group.replace('.', '')}-{len(groups[group])}) | {len(groups[group])} |")
    out.append("")
    out.append("---")
    out.append("")

    groups = defaultdict(list)
    for name in props:
        groups[group_of(name)].append(name)

    out.append("## スイッチ（名前空間ごと）")
    out.append("")
    for group in sorted(groups):
        names = sorted(groups[group])
        out.append(f"### `{group}.*`（{len(names)}）")
        out.append("")
        out.append("| スイッチ | 既定 | 位置 |")
        out.append("|---|---|---|")
        for name in names:
            entries = props[name]
            rel, number, default, _ = entries[0]
            extra = "" if len(entries) == 1 else f" (+{len(entries) - 1})"
            out.append(f"| `{name}` | `{default or '-'}` | [`{rel}:{number}`](../{rel}#L{number}){extra} |")
        out.append("")

    out.append("## ログマーカー（`CELTIUM_*`）")
    out.append("")
    out.append("| マーカー | 位置 |")
    out.append("|---|---|")
    for name in sorted(markers):
        rel, number = markers[name][0]
        extra = "" if len(markers[name]) == 1 else f" (+{len(markers[name]) - 1})"
        out.append(f"| `{name}` | [`{rel}:{number}`](../{rel}#L{number}){extra} |")
    out.append("")
    out.append("---")
    out.append("")
    out.append("## 受入でよく使う leg ランナー")
    out.append("")
    out.append("`scripts/gv-leg.sh <leg名> <jar-dir> <mode> <gpu> [秒] [追加 -D…]`")
    out.append("")
    out.append("- mode: `cohort`（14 mod）／`e2e`（268 mod）／`recipe`（RLCraft 165 mod）／`plain`")
    out.append("- gpu: `amd`／`intel`（Intel は `DRI_PRIME=pci-0000_00_02_0!` を自分で export する）")
    out.append("- 結果: `.gv-verify/run-<leg>/`（`validation-process.json`・`validation-console.log`・`screenshots/`）")
    out.append("")
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="差分があれば非ゼロ終了")
    args = parser.parse_args()

    text = render(*collect())
    if args.check:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if current != text:
            print("docs/instruments.md が古い。python3 scripts/instrument-index.py を実行する。", file=sys.stderr)
            return 1
        print("docs/instruments.md は最新。")
        return 0

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write(text)
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({len(text)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
