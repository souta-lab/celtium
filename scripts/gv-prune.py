#!/usr/bin/env python3
"""`.gv-verify/run-*` から「証拠」を残して「再生成できる重いもの」だけ落とす。

なぜ要るか: 2026-10-17 時点で run は 90 本・13 GB あり、うち 1 本あたり 400 MB 以上が
`mods/`（パックのコピー）・`saves/`・`config/` など **ゲームディレクトリの中身**で、
証拠ではない。ディスクが 100% になって leg が落ちたこともある。

残すもの（証拠）:
  - `validation-process.json`（verdict・exit code・GPU 名）
  - `validation-console.log`（stdout。`CELTIUM_*` 行はここにしか無い）
  - `screenshots/`（提示画像）
  - `headless/`（session.json）
  - ルート直下の `*.json`

落とすもの（再生成できる）: 既定の HEAVY リストのみ。**未知のファイルには触らない。**

使い方:
    python3 scripts/gv-prune.py                 # 何が消えるか表示（既定は dry-run）
    python3 scripts/gv-prune.py --apply         # 実行
    python3 scripts/gv-prune.py --apply --keep 20
"""
from __future__ import annotations

import argparse
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUNS = os.path.join(ROOT, ".gv-verify")

# 実測（2026-10-17・run-gv-e2e-268-r16-fbo）で重かった順。ゲームディレクトリの中身なので再生成できる。
HEAVY = (
    "mods", "resourcepacks", "resources", "config", "saves", "backups", "dumps", "framedump",
    "vintagefix", "test-libraries", "local", "schematics", "patchouli_books", "journeymap",
    "astralsorcery", "ChatTweaks", "TombManyGraves", "discordsuite", "llibrary",
    "crash-reports", "shaderpacks", "world", "BotaniaVars.dat", "crafttweaker.log",
    "knownkeys.txt", "options.txt", "servers.dat", "usercache.json",
)
KEEP = ("validation-process.json", "validation-console.log", "screenshots", "headless",
        os.path.join("logs", "celtium-validation.json"))
# `logs/` は丸ごと消さない: **fixture のレポート `logs/celtium-validation.json` が主証拠**で、
# 提示画像の verdict も leg の数値もここにしか無い（2026-10-17 に気づいて HEAVY から外した）。
LOGS = "logs"
LOGS_KEEP = (
    "celtium-validation.json",      # fixture のレポート（verdict・全計器の数値）
    "celtium-vulkan-device.json",   # CELTIUM_NATIVE_DEVICE の元。Intel 判定の根拠
    "celtium-capabilities.json",    # capability report（配布品質の根拠）
    "cleanmix.log",                 # mixin の適用記録（Found N mixin classes 等）
)


def size_of(path: str) -> int:
    if os.path.isfile(path):
        return os.path.getsize(path)
    total = 0
    for dirpath, _, filenames in os.walk(path):
        for name in filenames:
            try:
                total += os.path.getsize(os.path.join(dirpath, name))
            except OSError:
                pass
    return total


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024
    return f"{n:.1f} GB"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="実際に削除する（既定は表示のみ）")
    parser.add_argument("--keep", type=int, default=10, help="新しい順に丸ごと残す本数（既定 10）")
    args = parser.parse_args()

    if not os.path.isdir(RUNS):
        print(f"{RUNS} が無い。", file=sys.stderr)
        return 1

    runs = sorted(
        (os.path.join(RUNS, name) for name in os.listdir(RUNS)
         if name.startswith("run-") and os.path.isdir(os.path.join(RUNS, name))),
        key=lambda p: os.path.getmtime(p),
        reverse=True,
    )
    print(f"run {len(runs)} 本。新しい {args.keep} 本はそのまま残す。")
    freed = 0
    for index, run in enumerate(runs):
        # logs/ の中身はレポート以外を落とす（証拠は残す）。
        if index >= args.keep:
            logs = os.path.join(run, LOGS)
            if os.path.isdir(logs):
                for name in os.listdir(logs):
                    if name in LOGS_KEEP:
                        continue
                    path = os.path.join(logs, name)
                    bytes_here = size_of(path)
                    freed += bytes_here
                    print(f"  {'削除' if args.apply else '削除予定'}: {os.path.relpath(path, ROOT)} ({human(bytes_here)})")
                    if args.apply:
                        if os.path.isdir(path):
                            shutil.rmtree(path, ignore_errors=True)
                        else:
                            try:
                                os.remove(path)
                            except OSError:
                                pass
        if index < args.keep:
            continue
        for entry in HEAVY:
            path = os.path.join(run, entry)
            if not os.path.exists(path):
                continue
            bytes_here = size_of(path)
            freed += bytes_here
            print(f"  {'削除' if args.apply else '削除予定'}: {os.path.relpath(path, ROOT)} ({human(bytes_here)})")
            if args.apply:
                if os.path.isdir(path):
                    shutil.rmtree(path, ignore_errors=True)
                else:
                    try:
                        os.remove(path)
                    except OSError:
                        pass
        for entry in KEEP:
            if not os.path.exists(os.path.join(run, entry)):
                print(f"  ⚠ 証拠が欠けている: {os.path.relpath(os.path.join(run, entry), ROOT)}", file=sys.stderr)

    print(f"\n{'解放' if args.apply else '解放予定'}: {human(freed)}")
    if not args.apply:
        print("実行するには --apply を付ける。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
