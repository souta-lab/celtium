#!/usr/bin/env python3
# 正本: 手順・判定規則は night-queue/POST-RELEASE-2026-11-22/item681-leg-prep.md（項目681 leg 準備）。
# 本版は alpha-item681 版に判定節（構成比行・混合型の判定行）を追加した版。合体時は同名ファイルより本版を採用。
"""worldRest 残差の (c) JFR サンプリング集計（QUEUE 項目681、worldrest-census-648.md §5 の手順2–3）。

項目648 の bracket census で worldRest の約6割は帰属できたが、残差 39.3%（WR3/WR4 合算
743.6ms）は「呼び出しの形をしていない時間」として残った。§5 の (c) は bracket でなく
サンプリングで割る手順を固定している:

  1. 同一プロトコルの leg を JFR 付きで回す（記録の作り方は2通りあり、どちらでもよい。
     gv-leg 経由なら CELTIUM_LEG_JFR 環境変数（gv_leg.py の jcmd attach。-XX:StartFlightRecording は
     launcher 側の録画に上書きされる実測があるため、そちらは使わない）。Prism relauncher 経由なら
     §5 のとおり JVM 引数に -XX:StartFlightRecording を足す）。
  2. worldPass の窓に対応する区間のレンダースレッド（"Client thread"）のサンプルだけを抽出し、
     bracket 名（FramePassTimings / FrameBudgetTimings のフレーム）を除外してメソッド単位で集計する。
  3. 判別: 上位が park / 同期プリミティブなら待ち型。コレクション操作や Demonica/Iris の
     フレーム単位メソッドが上位なら、そのメソッドへの bracket 追加が次の計器になる。
     GC スレッド側の停止が支配的なら計器でなくヒープ設定側の話として切り分ける。

このスクリプトは手順2–3（と手順4の jstack 代替の集計側）を実装する。入力は .jfr バイナリでは
なく、ゲーム JVM と同じ JDK の `jfr print` が出すテキスト:

    jfr print --events jdk.ExecutionSample wr.jfr > wr-samples.txt
    python3 scripts/jfr_worldrest.py --samples wr-samples.txt [--from ISO --to ISO] [--warm-log game.log]

窓（--from/--to）は JFR の startTime（UTC）で指定する。leg ログ側の対応付け材料として
--warm-log を渡すと、CELTIUM_WARM_WINDOW の begin/end 行と検証レポートの warmSpikes
（worldTick と ms）を抜き出して報告の頭に置く（tick→時刻の換算はログの時刻列から人が読む。
JVM ログのタイムゾーンと JFR の UTC を機械で混ぜると黙ってずれるため、自動換算はしない）。

JFR が使えない環境向けの代替（§5 手順4）: leg 中に繰り返し採取した jstack 出力を
--jstack で渡すと、同じ集計・判別をスレッドダンプ列に対して行う（時刻の窓は使えない）。

判別のしきい値は機械的な目安であり、最終判定は人が上位メソッドを読んで行う。
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

# §5 手順2: 「bracket 名（FramePassTimings.begin/end を含むフレーム）は除外し」。
# 計時機構自身のフレームは残差の実体ではないので、帰属先（スタックの上から最初に残る
# フレーム）を決める際に読み飛ばす。パッケージが移っても効くよう単純名で照合する。
TIMING_FRAME_MARKERS = ("FramePassTimings.", "FrameBudgetTimings.")

# 手順3の待ち型の目印。leaf（帰属先）に出る形と、スタックの途中に呼び出しとして
# 出る形（park はその下に必ず出る）の両方を見る。
WAIT_LEAF_MARKERS = (
    "Unsafe.park",
    "LockSupport.park",
    "Object.wait",
    "Thread.sleep",
    "Thread.yield",
    "ConditionObject.await",
    "CountDownLatch.await",
    "Semaphore.acquire",
    "FutureTask.get",
    "BlockingQueue.take",
    "BlockingQueue.poll",
    "SynchronousQueue",
    "Phaser.arrive",
    "ObjectMonitor",
    "monitorenter",
)
WAIT_STACK_MARKERS = (
    "LockSupport.park",
    "Unsafe.park",
    "CountDownLatch.await",
    "Semaphore.acquire",
    "FutureTask.get",
    "BlockingQueue.take",
    "ConditionObject.await",
)

# GC スレッドの名前の目印（手順3の「GC スレッド側の停止」を見るための全スレッド集計用）。
GC_THREAD_MARKERS = ("GC Thread", "G1 ", "Parallel GC", "Concurrent Mark", "ZGC", "Shenandoah")

DEFAULT_THREAD_MARKERS = ("Client thread", "Render thread")


@dataclass
class Sample:
    """1回の実行サンプル。frames は leaf（実行中メソッド）が先頭。"""

    thread: str
    start_time: str | None
    frames: list[str] = field(default_factory=list)


def method_of(frame: str) -> str:
    """フレーム文字列からメソッド名だけを取り出す。

    jfr print の形: ``java.util.HashMap.getNode(int, Object) line: 617``
    jstack の形:    ``java.util.HashMap.getNode(HashMap.java:617)``
    どちらも引数リストの '(' で切れば完全修飾メソッド名が残る。
    """
    text = frame.strip()
    text = re.sub(r"\s+line:\s*\d+\s*$", "", text)
    paren = text.find("(")
    if paren >= 0:
        text = text[:paren]
    return text.strip()


def parse_jfr_print(text: str) -> list[Sample]:
    """`jfr print --events jdk.ExecutionSample` のテキストを Sample 列にする。"""
    samples: list[Sample] = []
    current: Sample | None = None
    in_stack = False
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("jdk.ExecutionSample"):
            current = Sample(thread="", start_time=None)
            in_stack = False
            continue
        if current is None:
            continue
        if stripped == "}" and not in_stack:
            samples.append(current)
            current = None
            continue
        if in_stack:
            if stripped.startswith("]"):
                in_stack = False
            elif stripped == "}":
                # stackTrace が空のままイベントが閉じた形。境界を取りこぼさない。
                in_stack = False
                samples.append(current)
                current = None
            elif stripped and stripped != "...":
                current.frames.append(method_of(stripped))
            continue
        if stripped.startswith("stackTrace"):
            if "[" in stripped and stripped.endswith("]"):
                # 1行で閉じる形（空か、フレームが同行に収まる場合）。
                inner = stripped[stripped.index("[") + 1:-1].strip()
                if inner:
                    current.frames.append(method_of(inner))
            else:
                in_stack = True
            continue
        if stripped.startswith("startTime"):
            _, _, value = stripped.partition("=")
            current.start_time = value.strip().rstrip(",")
            continue
        if stripped.startswith("sampledThread"):
            match = re.search(r'"([^"]*)"', stripped)
            if match:
                current.thread = match.group(1)
            continue
    if current is not None:
        samples.append(current)
    return samples


def parse_jstack(text: str) -> list[Sample]:
    """繰り返し採取した jstack 出力（§5 手順4）を Sample 列にする。時刻は持たない。"""
    samples: list[Sample] = []
    current: Sample | None = None
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith('"'):
            if current is not None:
                samples.append(current)
            match = re.match(r'"([^"]*)"', stripped)
            current = Sample(thread=match.group(1) if match else "", start_time=None)
            continue
        if current is not None and stripped.startswith("at "):
            current.frames.append(method_of(stripped[3:]))
    if current is not None:
        samples.append(current)
    return samples


def attributed_frame(sample: Sample) -> str | None:
    """帰属先 = スタックの上から、計時機構のフレームを除いて最初に残るメソッド。"""
    for frame in sample.frames:
        if any(marker in frame for marker in TIMING_FRAME_MARKERS):
            continue
        return frame
    return None


def classify(sample: Sample, attributed: str | None) -> str:
    """§5 手順3の判別カテゴリ: wait / inline / timing / unknown。"""
    if attributed is None:
        return "timing" if sample.frames else "unknown"
    if any(marker in attributed for marker in WAIT_LEAF_MARKERS):
        return "wait"
    stack_has_wait = any(
        marker in frame for frame in sample.frames for marker in WAIT_STACK_MARKERS
    )
    leaf_is_park = "park" in attributed.lower()
    if stack_has_wait and (leaf_is_park or attributed.startswith("java.util.concurrent")):
        return "wait"
    return "inline"


def in_window(sample: Sample, start: str | None, end: str | None) -> bool:
    """JFR の startTime（ISO・UTC）による窓。jstack 由来（時刻なし）は窓の対象外＝全部通す。"""
    if sample.start_time is None or (start is None and end is None):
        return True
    moment = sample.start_time.rstrip("Z")
    if start is not None and moment < start.rstrip("Z"):
        return False
    if end is not None and moment > end.rstrip("Z"):
        return False
    return True


def warm_log_notes(path: Path) -> list[str]:
    """leg ログから CELTIUM_WARM_WINDOW の縁と warmSpikes の slowest tick を抜き出す。

    窓の自動換算はしない（docstring 参照）。人が --from/--to を決めるための材料を出す。
    """
    notes: list[str] = []
    spikes: list[tuple[int, int]] = []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as error:
        return [f"warm-log を読めなかった: {error}"]
    for line in lines:
        if "CELTIUM_WARM_WINDOW" in line:
            notes.append(f"warm window: {line.strip()[:160]}")
        for match in re.finditer(r"worldTick=(\d+)\s+(\d+)ms", line):
            spikes.append((int(match.group(1)), int(match.group(2))))
    if spikes:
        spikes.sort(key=lambda pair: pair[1], reverse=True)
        top = ", ".join(f"tick {tick}={ms}ms" for tick, ms in spikes[:16])
        notes.append(f"warmSpikes slowest（検証レポート由来）: {top}")
    return notes


def pct(part: int, whole: int) -> str:
    return f"{(100.0 * part / whole) if whole else 0.0:5.1f}%"


def table(title: str, counter: Counter, whole: int, top: int) -> list[str]:
    lines = [f"== {title} =="]
    if not counter:
        lines.append("  (なし)")
        return lines
    width = max(len(name) for name, _ in counter.most_common(top))
    for name, count in counter.most_common(top):
        lines.append(f"  {count:6d}  {pct(count, whole)}  {name:<{width}}")
    rest = sum(counter.values()) - sum(count for _, count in counter.most_common(top))
    if rest > 0:
        lines.append(f"  {rest:6d}  {pct(rest, whole)}  (上位{top}件以外)")
    return lines


def build_report(samples: list[Sample], thread_markers: list[str], top: int,
                 window: tuple[str | None, str | None], notes: list[str]) -> str:
    out: list[str] = []
    out.append("jfr_worldrest — worldRest 残差 (c) サンプリング集計（項目681 / worldrest-census-648 §5）")
    for note in notes:
        out.append(f"  {note}")
    if window[0] or window[1]:
        out.append(f"  窓: from={window[0] or '-'} to={window[1] or '-'}（JFR startTime, UTC）")
    windowed = [s for s in samples if in_window(s, window[0], window[1])]
    out.append(f"  サンプル総数: {len(samples)}（窓内 {len(windowed)}）")

    thread_counts = Counter(s.thread for s in windowed)
    out += table("全スレッド（サンプル数）", thread_counts, len(windowed), 10)

    gc_samples = sum(count for name, count in thread_counts.items()
                     if any(marker in name for marker in GC_THREAD_MARKERS))
    if windowed and gc_samples:
        out.append(f"  GC 系スレッドの占有: {gc_samples}（{pct(gc_samples, len(windowed))}）")

    focused = [s for s in windowed
               if any(marker in s.thread for marker in thread_markers)]
    out.append(f"  対象スレッド（{' / '.join(thread_markers)}）: {len(focused)} サンプル")
    if not focused:
        out.append("  対象スレッドのサンプルが無い。--thread の指定か録画の付け先"
                   "（gv_leg.py の jcmd attach は game JVM が対象）を確認すること。")
        return "\n".join(out)

    attributed = [attributed_frame(s) for s in focused]
    categories = Counter(classify(s, a) for s, a in zip(focused, attributed))
    out += table("判別カテゴリ（対象スレッド）", categories, len(focused), 10)

    method_counts = Counter(a for a in attributed if a is not None)
    out += table("帰属メソッド 上位（bracket 枠を除いた実行中メソッド）",
                 method_counts, len(focused), top)

    wait_counts = Counter(a for s, a in zip(focused, attributed)
                          if a is not None and classify(s, a) == "wait")
    if wait_counts:
        out += table("待ちサイト 上位", wait_counts, len(focused), top)

    out.append("== 判別（§5 手順3・機械的な目安） ==")
    wait_share = categories.get("wait", 0) / len(focused) if focused else 0.0
    out.append(f"  構成比（対象スレッド）: wait {pct(categories.get('wait', 0), len(focused))} / "
               f"inline {pct(categories.get('inline', 0), len(focused))} / "
               f"timing {pct(categories.get('timing', 0), len(focused))} / "
               f"unknown {pct(categories.get('unknown', 0), len(focused))}")
    if wait_share >= 0.5:
        heads = ", ".join(name for name, _ in wait_counts.most_common(3))
        out.append(f"  待ち型が支配的（wait {pct(categories.get('wait', 0), len(focused))}）。"
                   f"上位の待ちサイト: {heads}。§5.2 の PT4 tick 521 型として切り分ける。")
    elif wait_share >= 0.2:
        wait_heads = ", ".join(name for name, _ in wait_counts.most_common(3))
        inline_heads = ", ".join(name for name, _ in method_counts.most_common(3))
        out.append(f"  混合型（wait {pct(categories.get('wait', 0), len(focused))} / "
                   f"inline {pct(categories.get('inline', 0), len(focused))}）。"
                   "待ち型とインラインの両候補が立つ。次計器の候補は構成比の大きい側から、"
                   f"待ちサイト: {wait_heads}。帰属メソッド: {inline_heads}。")
    elif method_counts:
        heads = ", ".join(name for name, _ in method_counts.most_common(3))
        out.append(f"  インライン仕事が支配的。次の bracket 候補（帰属の上位）: {heads}。")
    if windowed and gc_samples / len(windowed) >= 0.25:
        out.append(f"  GC 系スレッドの占有が {pct(gc_samples, len(windowed))} と大きい。"
                   "計器でなくヒープ設定側の話として切り分ける（§5 手順3）。")
    if categories.get("timing"):
        out.append(f"  計時機構の中で捕まったサンプルが {categories['timing']} 件ある。"
                   "bracket 自身の費用として別に読むこと。")
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="worldRest 残差の (c) JFR/jstack サンプリング集計（項目681）")
    parser.add_argument("--samples", type=Path, default=None,
                        help="jfr print --events jdk.ExecutionSample の出力ファイル（- で標準入力）")
    parser.add_argument("--jstack", type=Path, default=None,
                        help="繰り返し採取した jstack 出力ファイル（§5 手順4の代替経路）")
    parser.add_argument("--thread", action="append", default=None,
                        help="対象スレッド名の部分文字列（複数指定可。既定: Client thread / Render thread）")
    parser.add_argument("--from", dest="start", default=None,
                        help="窓の開始（JFR startTime の ISO 形式、UTC）")
    parser.add_argument("--to", dest="end", default=None,
                        help="窓の終了（同上）")
    parser.add_argument("--top", type=int, default=20, help="各表の表示件数（既定 20）")
    parser.add_argument("--warm-log", type=Path, default=None,
                        help="leg のゲームログ（warm window と warmSpikes の抜き出し用）")
    args = parser.parse_args(argv)

    if (args.samples is None) == (args.jstack is None):
        parser.error("--samples と --jstack のどちらか一方を指定すること")
    if args.jstack is not None:
        samples = parse_jstack(args.jstack.read_text(encoding="utf-8", errors="replace"))
        source_note = f"入力: jstack 列 {args.jstack}（§5 手順4。時刻の窓は使えない）"
    else:
        text = (sys.stdin.read() if str(args.samples) == "-"
                else args.samples.read_text(encoding="utf-8", errors="replace"))
        samples = parse_jfr_print(text)
        source_note = f"入力: jfr print 出力 {args.samples}"
    notes = [source_note]
    if args.warm_log is not None:
        notes += warm_log_notes(args.warm_log)
    markers = args.thread if args.thread else list(DEFAULT_THREAD_MARKERS)
    print(build_report(samples, markers, args.top, (args.start, args.end), notes))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
