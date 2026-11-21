"""The build slot lock in scripts/build.sh: it must name a process *instance*, not just a pid.

A pid alone is recycled by the kernel. Before the fix, a lock left behind by a killed build whose pid
had been recycled to an unrelated live process (an editor, a shell) made the next build wait out the
whole CELTIUM_BUILD_WAIT for a slot that was actually free. These tests pin the four shapes: recycled
pid reclaims at once, dead pid reclaims, a genuine holder is waited for, and a legacy pid-only lock
file keeps the old kill -0 semantics.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

REPO_ROOT = Path(__file__).resolve().parents[2]
BUILD_SH = REPO_ROOT / "scripts" / "build.sh"

STUB_GRADLEW = """#!/usr/bin/env bash
echo "STUB-GRADLEW-RAN"
sleep "${STUB_SLEEP:-0}"
exit 0
"""


def proc_start_time(pid):
    stat = Path(f"/proc/{pid}/stat").read_text()
    rest = stat.rsplit(") ", 1)[1].split()
    return rest[19]  # field 22 overall: starttime


@unittest.skipUnless(Path("/proc/self/stat").exists(), "needs /proc for process start times")
class BuildSlotLockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="build-slot-lock-"))
        (self.tmp / "scripts").mkdir()
        shutil.copyfile(BUILD_SH, self.tmp / "scripts" / "build.sh")
        gradlew = self.tmp / "gradlew"
        gradlew.write_text(STUB_GRADLEW)
        gradlew.chmod(0o755)
        self.slot = self.tmp / ".celtium-work" / "build-slot"
        self.slot.mkdir(parents=True)
        self.lock = self.slot / "owner.pid"
        self.children = []

    def tearDown(self):
        for child in self.children:
            child.kill()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def spawn_sleeper(self, seconds=60):
        proc = subprocess.Popen(["sleep", str(seconds)])
        self.children.append(proc)
        return proc

    def run_build(self, wait_seconds, stub_sleep=0):
        env = dict(os.environ)
        env["CELTIUM_BUILD_WAIT"] = str(wait_seconds)
        env["STUB_SLEEP"] = str(stub_sleep)
        return subprocess.run(
            ["bash", str(self.tmp / "scripts" / "build.sh")],
            capture_output=True, text=True, env=env, timeout=120,
        )

    def test_recycled_pid_is_reclaimed_at_once(self):
        sleeper = self.spawn_sleeper()
        # A previous build recorded this pid with *its* start time; the pid now belongs to the sleeper,
        # whose start time differs. The recorded start time is the sleeper's plus one tick: a mismatch.
        self.lock.write_text(f"{sleeper.pid} {int(proc_start_time(sleeper.pid)) + 1}\n")
        started = time.monotonic()
        result = self.run_build(wait_seconds=60)
        elapsed = time.monotonic() - started
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Reclaiming the build slot", result.stdout)
        self.assertIn("STUB-GRADLEW-RAN", result.stdout)
        self.assertLess(elapsed, 30)

    def test_dead_pid_is_reclaimed(self):
        dead = subprocess.Popen(["sleep", "0.1"])
        dead.wait()
        self.lock.write_text(f"{dead.pid} 12345\n")
        result = self.run_build(wait_seconds=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Reclaiming the build slot", result.stdout)
        self.assertIn("STUB-GRADLEW-RAN", result.stdout)

    def test_genuine_holder_is_waited_for(self):
        env = dict(os.environ)
        env["STUB_SLEEP"] = "4"
        first = subprocess.Popen(
            ["bash", str(self.tmp / "scripts" / "build.sh")],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env,
        )
        self.children.append(first)
        # Wait until the first build actually holds the lock.
        deadline = time.monotonic() + 30
        while not self.lock.exists():
            if time.monotonic() > deadline:
                self.fail("first build never took the lock")
            time.sleep(0.05)
        started = time.monotonic()
        result = self.run_build(wait_seconds=60)
        elapsed = time.monotonic() - started
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Waiting for the build slot", result.stdout)
        # It waited for the holder's 4-second build instead of stealing the slot, and it did not
        # wait out the whole CELTIUM_BUILD_WAIT either. (Once the holder exits, reclaiming its
        # leftover lock file is the normal path - build.sh execs gradle, so its EXIT trap never
        # runs - and an unreaped zombie holder must count as gone, not as alive.)
        self.assertGreaterEqual(elapsed, 2)
        self.assertLess(elapsed, 30)
        first.wait(timeout=30)

    def test_legacy_pid_only_lock_keeps_kill0_semantics(self):
        sleeper = self.spawn_sleeper()
        self.lock.write_text(f"{sleeper.pid}\n")
        result = self.run_build(wait_seconds=1)
        self.assertEqual(result.returncode, 1)
        self.assertIn("CELTIUM_BUILD_WAIT expired", result.stderr)


if __name__ == "__main__":
    unittest.main()
