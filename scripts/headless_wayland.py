"""Own a private, GPU-rendered Sway display without touching the desktop."""

import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import time


class HeadlessWayland:
    def __init__(self, directory, render_node):
        self.directory = Path(directory).resolve()
        self.render_node = Path(render_node).resolve(strict=True)
        self.process = None
        self.log = None
        self.environment = None
        self.metadata = None
        self.runtime = None

    def __enter__(self):
        for executable in ("sway", "swaymsg", "Xwayland"):
            if not shutil.which(executable):
                raise FileNotFoundError(f"Headless validation requires {executable}")
        self.directory.mkdir(parents=True, exist_ok=False)
        # Unix socket paths have a small fixed limit; game paths can be long.
        self.runtime = tempfile.TemporaryDirectory(prefix="celtium-hl-")
        runtime = Path(self.runtime.name)
        capture = self.directory / "capture_environment.py"
        ready = self.directory / "display.json"
        capture.write_text(
            "import json, os\nfrom pathlib import Path\n"
            f"path = Path({str(ready)!r})\n"
            "temporary = path.with_suffix('.tmp')\n"
            "temporary.write_text(json.dumps({key: os.environ[key] for key in "
            "('DISPLAY', 'WAYLAND_DISPLAY', 'SWAYSOCK', 'XDG_RUNTIME_DIR') if key in os.environ}))\n"
            "temporary.replace(path)\n",
            encoding="utf-8",
        )
        config = self.directory / "sway.conf"
        config.write_text(
            "xwayland force\noutput HEADLESS-1 mode 1280x720@60Hz scale 1\n"
            "default_border none\nseat seat0 fallback true\n"
            f"exec {shlex.quote(sys.executable)} {shlex.quote(str(capture))}\n",
            encoding="utf-8",
        )
        env = os.environ.copy()
        for name in ("DISPLAY", "WAYLAND_DISPLAY", "WAYLAND_SOCKET", "SWAYSOCK", "XAUTHORITY",
                     "XDG_SESSION_ID", "XDG_ACTIVATION_TOKEN", "DESKTOP_STARTUP_ID"):
            env.pop(name, None)
        env.update(XDG_RUNTIME_DIR=str(runtime), WLR_BACKENDS="headless", WLR_HEADLESS_OUTPUTS="1",
                   WLR_RENDERER="gles2", WLR_RENDER_DRM_DEVICE=str(self.render_node))
        # An inherited software override must not silently replace this GPU test.
        for name in ("LIBGL_ALWAYS_SOFTWARE", "WLR_RENDERER_FORCE_SOFTWARE", "WLR_RENDERER_ALLOW_SOFTWARE"):
            env.pop(name, None)
        self.log = (self.directory / "sway.log").open("wb")
        try:
            self.process = subprocess.Popen(["sway", "-d", "-c", str(config)], env=env,
                                            stdout=self.log, stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic() + 15
            while not ready.is_file():
                if self.process.poll() is not None:
                    raise RuntimeError(f"Headless compositor exited; see {self.directory / 'sway.log'}")
                if time.monotonic() >= deadline:
                    raise TimeoutError(f"Headless display did not start; see {self.directory / 'sway.log'}")
                time.sleep(0.05)
            display = json.loads(ready.read_text())
            if not all(display.get(key) for key in ("DISPLAY", "WAYLAND_DISPLAY", "SWAYSOCK")):
                raise RuntimeError("Headless compositor did not provide both Wayland and Xwayland")
            env.update(display)
            self.environment = env
            outputs = subprocess.run(["swaymsg", "-s", display["SWAYSOCK"], "-t", "get_outputs", "-r"],
                                     env=env, capture_output=True, text=True, check=True, timeout=5)
            self.metadata = {"backend": "headless", "renderNode": str(self.render_node),
                             "display": display, "outputs": json.loads(outputs.stdout)}
            (self.directory / "session.json").write_text(json.dumps(self.metadata, indent=2) + "\n")
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, exc_type, exc, traceback):
        if self.process is not None and self.process.poll() is None:
            if self.environment:
                try:
                    subprocess.run(["swaymsg", "-s", self.environment["SWAYSOCK"], "exit"],
                                   env=self.environment, stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL, timeout=3)
                except (OSError, subprocess.TimeoutExpired):
                    pass
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGTERM)
                try:
                    self.process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    os.killpg(self.process.pid, signal.SIGKILL)
                    self.process.wait()
        if self.log is not None:
            self.log.close()
        if self.runtime is not None:
            self.runtime.cleanup()
