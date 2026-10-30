#!/usr/bin/env python3
"""Generate a deterministic, source-linked index of property reads and marker strings.

This is a lexical index, not a Java evaluator. Only the four Java system-property
reader APIs below are indexed; translation keys and setProperty calls are not knobs.
Simple static String constants are resolved within a file or by an unambiguous class
name. Computed keys/other accessors require reading the source; no defaults are guessed.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/instruments.md"
SCAN_DIRS = (
    "celtium-vulkan/src/main", "forge122/src/main", "common/src/main",
    "validation/client", "celtium-probe/src/main",
)
STRING = r'"(?:\\.|[^"\\])*"'
LEXICAL = re.compile(STRING + r"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*.*?\*/", re.DOTALL)
KEY = re.compile(r"(?:celtium|celeritas)\.[A-Za-z0-9._-]+\Z")
IDENT = r"[A-Za-z_$][A-Za-z0-9_$]*"
CONSTANT = re.compile(
    rf'\b(?:static\s+final|final\s+static)\s+String\s+({IDENT})\s*=\s*({STRING})\s*;'
)
READER = re.compile(
    rf'\b(System\s*\.\s*getProperty|Boolean\s*\.\s*getBoolean|'
    rf'Integer\s*\.\s*getInteger|Long\s*\.\s*getLong)\s*\(\s*'
    rf'({STRING}|{IDENT}(?:\s*\.\s*{IDENT})*)\s*(?=[,)])'
)
MARKER = re.compile(r"\bCELTIUM_[A-Z][A-Z0-9_]*\b")


def without_comments(text: str) -> str:
    # Keep strings and line offsets intact. A regex removing // alone eats URLs
    # inside string literals, and then silently loses the property reads after it.
    return LEXICAL.sub(
        lambda m: re.sub(r"[^\n]", " ", m.group()) if m.group().startswith("/") else m.group(), text
    )


def java_files():
    return sorted({path for base in SCAN_DIRS for path in (ROOT / base).rglob("*.java")
                   if not {"build", ".gradle", "test"}.intersection(path.relative_to(ROOT).parts)})


def collect(paths=None, root=None):
    root = Path(root) if root is not None else ROOT
    sources = {}
    constants = {}
    qualified = defaultdict(set)
    for path in sorted(map(Path, java_files() if paths is None else paths)):
        code = without_comments(path.read_text(encoding="utf-8"))
        sources[path] = code
        values = {name: literal[1:-1] for name, literal in CONSTANT.findall(code)}
        constants[path] = values
        for name, value in values.items():
            qualified[f"{path.stem}.{name}"].add(value)

    props = defaultdict(list)
    markers = defaultdict(list)
    for path, code in sources.items():
        rel = path.relative_to(root).as_posix()
        # Match readers only outside string/char literals. Keep the original code
        # for the argument parser, but mask literals when locating the API name.
        masked = LEXICAL.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), code)
        for match in READER.finditer(code):
            if not masked[match.start():match.start() + 1].strip():
                continue
            api = re.sub(r"\s", "", match.group(1))
            arg = re.sub(r"\s", "", match.group(2)) if not match.group(2).startswith('"') else match.group(2)
            if arg.startswith('"'):
                name = arg[1:-1]
            elif "." not in arg:
                name = constants[path].get(arg)
            else:
                values = qualified.get(".".join(arg.split(".")[-2:]), set())
                name = next(iter(values)) if len(values) == 1 else None
            if name and KEY.fullmatch(name):
                line = code.count("\n", 0, match.start()) + 1
                props[name].append((rel, line, api))
        for literal in LEXICAL.finditer(code):
            if not literal.group().startswith('"'):
                continue
            line = code.count("\n", 0, literal.start()) + 1
            for marker in MARKER.findall(literal.group()):
                if not marker.endswith("_"):
                    markers[marker].append((rel, line))
    for entries in (*props.values(), *markers.values()):
        entries[:] = sorted(set(entries))
    return props, markers


def group_of(name: str) -> str:
    return ".".join(name.split(".")[:2])


def location(rel: str, line: int) -> str:
    return f"[{Path(rel).name}:{line}](<../{rel}#L{line}>)"


def render(props, markers) -> str:
    out = [
        "# Celtium 計器索引（自動生成）", "",
        "生成: `python3 scripts/instrument-index.py`。手で編集しない。", "",
        f"システムプロパティの読み取りキー **{len(props)} 個**／文字列内マーカー **{len(markers)} 個**。",
        "機能設定・故障注入も含む。すべてが計測用スイッチという意味ではない。", "",
        "名前を検索してリンク先の実装を読む。既定値・有効条件・出力上限は推測で掲載しない。",
        "索引は `System.getProperty` / `Boolean.getBoolean` / `Integer.getInteger` / `Long.getLong` の",
        "リテラルまたは単純な static String 定数引数だけを収録する。計算されたキー・別API・テストは対象外。",
        "マーカーは接頭辞つきの文字列も収録するが、ログ出力以外の参照も含む。`--check` は鮮度検査であり網羅性の証明ではない。", "",
        "手順と測定用途は [開発・検証手順](<development.md>)。全文を毎回ロードせず、用途から探す。", "",
        "## 名前空間", "", "| 名前空間 | 読み取りキー数 |", "|---|---|",
    ]
    groups = defaultdict(list)
    for name in props:
        groups[group_of(name)].append(name)
    for group in sorted(groups):
        out.append(f"| [{group}](#group-{group.replace('.', '-')}) | {len(groups[group])} |")
    for group in sorted(groups):
        out.extend(["", f'<a id="group-{group.replace(".", "-")}"></a>', f"## `{group}.*`", "",
                    "| キー | 読み取りAPI | 位置 |", "|---|---|---|"])
        for name in sorted(groups[group]):
            entries = props[name]
            apis = ", ".join(f"`{api}`" for api in sorted({entry[2] for entry in entries}))
            links = ", ".join(location(rel, line) for rel, line, _ in entries)
            out.append(f"| `{name}` | {apis} | {links} |")
    out.extend(["", "## 文字列内マーカー", "", "| マーカー | 位置 |", "|---|---|"])
    for name in sorted(markers):
        links = ", ".join(location(rel, line) for rel, line in markers[name])
        out.append(f"| `{name}` | {links} |")
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="差分があれば非ゼロ終了")
    args = parser.parse_args()
    text = render(*collect())
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if current != text:
            print("docs/instruments.md が古い。python3 scripts/instrument-index.py を実行する。", file=sys.stderr)
            old_lines, new_lines = current.splitlines(), text.splitlines()
            for index in range(max(len(old_lines), len(new_lines))):
                old = old_lines[index] if index < len(old_lines) else "<なし>"
                new = new_lines[index] if index < len(new_lines) else "<なし>"
                if old != new:
                    print(f"  最初の差分 {index + 1} 行目:\n    いま: {old[:160]}\n    生成: {new[:160]}", file=sys.stderr)
                    break
            return 1
        print("docs/instruments.md は最新。")
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)} ({len(text.encode('utf-8'))} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
