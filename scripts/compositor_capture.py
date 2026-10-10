"""Capture a headless run's compositor output, so a presented frame can be traced to the screen.

`prism_validation.py --headless` owns a private, GPU-rendered wlroots/Sway compositor for the length of a
run and then throws it away, which leaves nothing to take a picture of afterwards.  This adapter starts that
same launcher unchanged, waits for the compositor it created to announce itself in `<game-dir>/headless/display.json`,
and then runs `grim` (wlroots' screencopy client) against that compositor's Wayland socket until the run ends.

What `grim` returns is the compositor's own output buffer: the surface Sway composed for its output, which is the
same buffer it would hand to a CRTC.  That is the thing the completion condition asks about - the window/compositor
output, not the renderer's readback - and it is why this adapter is separate from the launcher: the launcher's job
is to run the game, and this one's is to photograph the screen it was run on.

Every capture writes a numbered PNG plus a JSON record: the wall and monotonic clock at the shutter, the game's PID,
the compositor's window tree (so the captured rectangle can be tied to a mapped window), and the PNG's own hash.
The numbering is what binds a picture to a moment; `docs/compositor-output-capture-2026-10-11.md` does the binding
to a presented frame.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = Path(__file__).resolve().parent / "prism_validation.py"


def tree_windows(node, out):
    """Every node in a Sway tree that has a real surface, flattened for the capture record."""
    if node.get("type") in ("con", "floating_con") or node.get("app_id") or node.get("window_properties"):
        properties = node.get("window_properties") or {}
        out.append({
            "id": node.get("id"),
            "type": node.get("type"),
            "name": node.get("name"),
            "app_id": node.get("app_id"),
            "class": properties.get("class"),
            "instance": properties.get("instance"),
            "title": properties.get("title"),
            "pid": node.get("pid"),
            "rect": node.get("rect"),
            "visible": node.get("visible"),
            "focused": node.get("focused"),
            "fullscreen_mode": node.get("fullscreen_mode"),
            "shell": node.get("shell"),
        })
    for child in list(node.get("nodes") or []) + list(node.get("floating_nodes") or []):
        tree_windows(child, out)
    return out


def sway_query(environment, kind):
    """One Sway IPC query, or None: a capture must not fail because the compositor answered slowly."""
    socket = environment.get("SWAYSOCK")
    if not socket:
        return None
    try:
        done = subprocess.run(["swaymsg", "-s", socket, "-t", kind, "-r"], env=environment,
                              capture_output=True, text=True, timeout=5)
        if done.returncode != 0:
            return None
        return json.loads(done.stdout)
    except (OSError, ValueError, subprocess.TimeoutExpired):
        return None


def game_pid(game_directory):
    """The launcher's own --gameDir, so a capture can name the process whose window it holds."""
    marker = str(game_directory)
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            if marker not in (entry / "cmdline").read_bytes().decode("utf-8", "replace"):
                continue
        except OSError:
            continue
        try:
            if "foundation.boot.Foundation" in (entry / "cmdline").read_bytes().decode("utf-8", "replace"):
                return int(entry.name)
        except OSError:
            continue
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game-dir", type=Path, required=True,
                        help="The --game-dir the launcher was given; its headless/display.json names the compositor")
    parser.add_argument("--capture-dir", type=Path, required=True, help="Where the numbered PNGs and their records go")
    parser.add_argument("--interval", type=float, default=0.5, help="Seconds between the starts of two captures")
    parser.add_argument("--output", default=None, help="Compositor output name; default: the first from session.json")
    parser.add_argument("--startup-timeout", type=float, default=120.0,
                        help="Seconds to wait for the launcher's compositor to announce itself")
    parser.add_argument("--launcher-log", type=Path, default=None, help="Where the launcher's own output goes")
    parser.add_argument("launcher_args", nargs=argparse.REMAINDER,
                        help="Arguments for prism_validation.py, after --")
    args = parser.parse_args()
    launcher_args = [a for a in args.launcher_args if a != "--"]
    if not launcher_args:
        parser.error("no launcher arguments: pass them after --")
    if not LAUNCHER.is_file():
        parser.error(f"the launcher is missing: {LAUNCHER}")

    game = args.game_dir.resolve()
    if not any(str(a) == "--game-dir" or str(a).startswith("--game-dir=") for a in launcher_args):
        parser.error("the launcher arguments must carry the same --game-dir this adapter is watching")
    if not any(str(a) == "--headless" for a in launcher_args):
        parser.error("--headless is required: this adapter photographs the compositor that flag creates")
    if not shutil.which("grim"):
        parser.error("grim (wlroots screencopy client) is not installed; nothing here can photograph a Wayland output")
    captures = args.capture_dir.resolve()
    captures.mkdir(parents=True, exist_ok=True)
    launcher_log = (args.launcher_log or (captures / "launcher.log")).resolve()
    ready = game / "headless" / "display.json"
    session_file = game / "headless" / "session.json"
    # The launcher refuses to start twice in one directory, and a stale announcement would be mistaken for the new
    # compositor's. Removing it before the launch is what makes "display.json exists" mean "this run is ready".
    try:
        ready.unlink()
    except FileNotFoundError:
        pass

    command = [sys.executable, str(LAUNCHER), *launcher_args]
    (captures / "launcher-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    print(f"launching: {' '.join(command)}", flush=True)
    with launcher_log.open("wb") as log:
        launcher = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            environment = None
            deadline = time.monotonic() + args.startup_timeout
            while time.monotonic() < deadline:
                if ready.is_file():
                    announced = json.loads(ready.read_text())
                    if all(announced.get(key) for key in ("DISPLAY", "WAYLAND_DISPLAY", "SWAYSOCK")):
                        environment = os.environ.copy()
                        environment.update(announced)
                        break
                if launcher.poll() is not None:
                    raise RuntimeError(f"the launcher exited before its compositor was ready; see {launcher_log}")
                time.sleep(0.05)
            if environment is None:
                raise TimeoutError(f"no compositor was announced within {args.startup_timeout:.0f}s; see {launcher_log}")
            output = args.output
            if output is None and session_file.is_file():
                outputs = json.loads(session_file.read_text()).get("outputs") or []
                if outputs:
                    output = outputs[0].get("name")
            output = output or "HEADLESS-1"
            print(f"compositor ready: WAYLAND_DISPLAY={environment['WAYLAND_DISPLAY']}"
                  f" SWAYSOCK={environment['SWAYSOCK']} DISPLAY={environment['DISPLAY']} output={output}", flush=True)

            records = []
            index = 0
            while launcher.poll() is None:
                started = time.time()
                image = captures / f"compositor-{index:04d}.png"
                shutter_wall_ns = time.time_ns()
                shutter_mono_ns = time.monotonic_ns()
                grim = subprocess.run(["grim", "-o", output, str(image)], env=environment,
                                      capture_output=True, text=True)
                finished_mono_ns = time.monotonic_ns()
                pid = game_pid(game)
                windows = tree_windows(sway_query(environment, "get_tree") or {}, [])
                outputs = sway_query(environment, "get_outputs") or []
                digest = None
                size = None
                if grim.returncode == 0 and image.is_file():
                    data = image.read_bytes()
                    digest = hashlib.sha256(data).hexdigest()
                    size = len(data)
                record = {
                    "capture": index,
                    "image": image.name if size else None,
                    "grimExit": grim.returncode,
                    "grimStderr": grim.stderr.strip() or None,
                    "shutterWallNs": shutter_wall_ns,
                    "shutterMonoNs": shutter_mono_ns,
                    "shutterWall": time.strftime("%H:%M:%S", time.localtime(shutter_wall_ns / 1e9))
                                   + f".{shutter_wall_ns % 1_000_000_000:09d}",
                    "grimDurationMs": round((finished_mono_ns - shutter_mono_ns) / 1e6, 3),
                    "pngBytes": size,
                    "pngSha256": digest,
                    "gamePid": pid,
                    "output": output,
                    "outputModes": [{"name": o.get("name"), "current_mode": o.get("current_mode"),
                                     "scale": o.get("scale")} for o in outputs],
                    "windows": windows,
                }
                records.append(record)
                (captures / f"compositor-{index:04d}.json").write_text(json.dumps(record, indent=2) + "\n",
                                                                      encoding="utf-8")
                print(f"[{record['shutterWall']}] capture {index:04d} grim={grim.returncode}"
                      f" bytes={size} windows={len(windows)}" + (f" err={grim.stderr.strip()}" if grim.returncode else ""),
                      flush=True)
                index += 1
                remaining = args.interval - (time.time() - started)
                if remaining > 0:
                    time.sleep(remaining)
            code = launcher.wait(timeout=60)
            summary = {"launcherExitCode": code, "captures": len(records), "output": output,
                       "waylandDisplay": environment["WAYLAND_DISPLAY"], "swaySocket": environment["SWAYSOCK"],
                       "xDisplay": environment["DISPLAY"], "captureDir": str(captures),
                       "gameDir": str(game), "finishedWall": time.strftime("%Y-%m-%dT%H:%M:%S%z")}
            (captures / "captures.json").write_text(json.dumps({"summary": summary, "records": records}, indent=2) + "\n",
                                                    encoding="utf-8")
            print(json.dumps(summary, indent=2), flush=True)
            return 0
        finally:
            if launcher.poll() is None:
                os.killpg(launcher.pid, signal.SIGTERM)
                try:
                    launcher.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    os.killpg(launcher.pid, signal.SIGKILL)
                    launcher.wait()


if __name__ == "__main__":
    sys.exit(main())
