#!/usr/bin/env python3
"""Unit and contract tests for C4 (defect 6: PresentedFrameTracker and ValidationClient wiring)."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TRACKER = ROOT / "validation/client/src/main/java/org/soutalab/celtium/validation/PresentedFrameTracker.java"
CLIENT = ROOT / "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java"

JAVA_DRIVER = r"""
package org.soutalab.celtium.validation;

public class PresentedFrameTrackerTestDriver {
    static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("FAILED: " + label);
        }
    }

    static PresentedFrameTracker.VerdictEvaluation runNormalSingleWorld() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 200; wf++) {
            tracker.onWorldFrame(wf, 0x1111, wf - 1, 0, wf - 1, wf - 1);
        }
        check(tracker.firstPresentedWorldFrame() == 2, "normal firstPresentedWorldFrame");
        return tracker.evaluate(200, 200, 199, 0, 199, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runRlcraftWithSkips(
            int zeroFrames, int skipsDuringZero, int preWorldSkips, int captureFrame) {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        int skips = preWorldSkips;
        for (int wf = 1; wf <= zeroFrames; wf++) {
            tracker.onWorldFrame(wf, 0x1111, 0, skips, 0, wf - 1);
            if (wf <= skipsDuringZero) {
                skips++;
            }
        }
        int presented = 0;
        int attempts = 0;
        for (int wf = zeroFrames + 1; wf <= captureFrame; wf++) {
            presented++;
            attempts++;
            tracker.onWorldFrame(wf, 0x1111, presented, skips, attempts, wf - 1);
        }
        // Dump is written by the present after capture request (presented + 1).
        return tracker.evaluate(captureFrame, presented + 1, presented, skips, attempts,
                tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runRlcraftZeroSkipsCleanAttempts() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 685; wf++) {
            tracker.onWorldFrame(wf, 0x1111, 0, 0, 0, 0);
        }
        int presented = 0;
        int attempts = 0;
        for (int wf = 686; wf <= 700; wf++) {
            presented++;
            attempts++;
            tracker.onWorldFrame(wf, 0x1111, presented, 0, attempts, attempts);
        }
        check(tracker.firstPresentedWorldFrame() == 686, "zero-skip firstPresentedWorldFrame");
        return tracker.evaluate(700, presented + 1, presented, 0, attempts, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runCaptureBeforeFirstPresent() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 700; wf++) {
            tracker.onWorldFrame(wf, 0x1111, 0, 0, 0, 0);
        }
        PresentedFrameTracker.WindowSnapshot snapped = tracker.snapshotCurrentWindow();
        // Dump lands on present #1 after capture at frame 700.
        tracker.onWorldFrame(701, 0x1111, 1, 0, 1, 1);
        return tracker.evaluate(700, 1, 0, 0, 0, snapped);
    }

    static PresentedFrameTracker.VerdictEvaluation runIntermittentSkipsE2E() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        int presented = 0;
        int skips = 0;
        int attempts = 0;
        int presentedAtRequest = 0;
        int skipsAtRequest = 0;
        int attemptsAtRequest = 0;
        for (int wf = 1; wf <= 153; wf++) {
            tracker.onWorldFrame(wf, 0x1111, presented, skips, attempts, wf - 1);
            presentedAtRequest = presented;
            skipsAtRequest = skips;
            attemptsAtRequest = attempts;
            if (wf % 22 == 0) {
                skips++;
            } else {
                presented++;
                attempts++;
            }
        }
        return tracker.evaluate(153, presentedAtRequest + 1, presentedAtRequest, skipsAtRequest,
                attemptsAtRequest, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runWorldSwitch() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 150; wf++) {
            tracker.onWorldFrame(wf, 0x1111, wf - 1, 0, wf - 1, wf - 1);
        }
        // Quit to title (20 menu presents) and rejoin: framePresentedFromVulkan resets to 0 at wf=151.
        int attempts = 170;
        int presented = 0;
        int presentedAtRequest = 0;
        int attemptsAtRequest = 170;
        for (int wf = 151; wf <= 250; wf++) {
            PresentedFrameTracker.FrameObservation obs =
                    tracker.onWorldFrame(wf, 0x1111, presented, 0, attempts, attempts);
            if (wf == 151) {
                check(obs.counterRestarted, "worldSwitch restart detected");
            }
            presentedAtRequest = presented;
            attemptsAtRequest = attempts;
            presented++;
            attempts++;
        }
        check(tracker.presentedCounterRestarts() == 1, "worldSwitch restart count");
        return tracker.evaluate(250, presentedAtRequest + 1, presentedAtRequest, 0, attemptsAtRequest,
                tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runRestartOneFrameAfterCapture() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 4888; wf++) {
            tracker.onWorldFrame(wf, 0x1111, wf - 1, 0, wf - 1, wf - 1);
        }
        PresentedFrameTracker.WindowSnapshot snapped = tracker.snapshotCurrentWindow();
        tracker.onWorldFrame(4889, 0x1111, 0, 0, 4888, 4888);
        return tracker.evaluate(4888, 4888, 4887, 0, 4887, snapped);
    }

    static PresentedFrameTracker.VerdictEvaluation runBackendIdentityChange() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 685; wf++) {
            tracker.onWorldFrame(wf, 0x2222, 0, 0, 0, 0);
        }
        // Switch back to presenting backend 0x1111 at wf=686 (with its own counter at 685).
        int presented = 685;
        int presentedAtRequest = 685;
        for (int wf = 686; wf <= 700; wf++) {
            PresentedFrameTracker.FrameObservation obs =
                    tracker.onWorldFrame(wf, 0x1111, presented, 0, presented, presented);
            if (wf == 686) {
                check(obs.backendChanged, "backend swap detected");
            }
            presentedAtRequest = presented;
            presented++;
        }
        check(tracker.backendIdentityChanges() == 1, "backend identity changes count");
        return tracker.evaluate(700, presentedAtRequest + 1, presentedAtRequest, 0, presentedAtRequest,
                tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runPreWorldLeakShouldFail() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 128; wf++) {
            tracker.onWorldFrame(wf, 0x1111, 17 + wf, 0, 17 + wf, 17 + wf);
        }
        return tracker.evaluate(128, 146, 145, 0, 145, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runFailedSwapchainShouldFail() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 200; wf++) {
            int presented = wf <= 685 ? 0 : wf - 685;
            tracker.onWorldFrame(wf, 0x1111, presented, 0, wf - 1, wf - 1);
        }
        return tracker.evaluate(200, 1, 0, 0, 199, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runMidWorldStallShouldFail() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 200; wf++) {
            int presented = Math.min(50, wf - 1);
            tracker.onWorldFrame(wf, 0x1111, presented, 0, presented, presented);
        }
        return tracker.evaluate(200, 51, 50, 0, 50, tracker.snapshotCurrentWindow());
    }

    static PresentedFrameTracker.VerdictEvaluation runLegacyBackendWithoutAttemptsShouldFail() {
        PresentedFrameTracker tracker = new PresentedFrameTracker();
        for (int wf = 1; wf <= 200; wf++) {
            int presented = wf <= 150 ? 0 : wf - 150;
            tracker.onWorldFrame(wf, 0x1111, presented, -1, -1, -1);
        }
        return tracker.evaluate(200, 51, 50, -1, -1, tracker.snapshotCurrentWindow());
    }

    public static void main(String[] args) {
        PresentedFrameTracker.VerdictEvaluation normal = runNormalSingleWorld();
        check(normal.passed && normal.delta == 0, "normal single world passes with delta 0");

        PresentedFrameTracker.VerdictEvaluation rl1 = runRlcraftWithSkips(685, 683, 12, 700);
        check(rl1.passed, "RLCraft run 1 (683 skips) passes");
        check(rl1.skipsInWindow == 683, "RLCraft run 1 excludes pre-world skips");
        check(rl1.unadjustedDelta == -684, "RLCraft run 1 keeps unadjustedDelta=-684");
        check(Math.abs(rl1.delta) <= 1, "RLCraft run 1 skip-adjusted delta within 1");

        PresentedFrameTracker.VerdictEvaluation rl2 = runRlcraftWithSkips(2583, 2581, 0, 2600);
        check(rl2.passed && Math.abs(rl2.delta) <= 1, "RLCraft run 2 (2581 skips) passes");

        PresentedFrameTracker.VerdictEvaluation rlZero = runRlcraftZeroSkipsCleanAttempts();
        check(rlZero.passed && rlZero.usedPreFirstPresentAnchor && Math.abs(rlZero.delta) <= 1,
                "RLCraft zero-skip pre-first-present window passes");

        PresentedFrameTracker.VerdictEvaluation beforeFirst = runCaptureBeforeFirstPresent();
        check(beforeFirst.passed && beforeFirst.usedPreFirstPresentAnchor,
                "capture landing on present #1 passes");

        PresentedFrameTracker.VerdictEvaluation e2e = runIntermittentSkipsE2E();
        check(e2e.passed && Math.abs(e2e.delta) <= 1, "E2E intermittent skips pass");

        PresentedFrameTracker.VerdictEvaluation ws = runWorldSwitch();
        check(ws.passed && Math.abs(ws.delta) <= 1, "worldSwitch restart passes");

        PresentedFrameTracker.VerdictEvaluation r54c = runRestartOneFrameAfterCapture();
        check(r54c.passed && r54c.delta == 0, "restart after capture passes");

        PresentedFrameTracker.VerdictEvaluation swap = runBackendIdentityChange();
        check(swap.passed && Math.abs(swap.delta) <= 1, "backend identity change passes");

        PresentedFrameTracker.VerdictEvaluation leak = runPreWorldLeakShouldFail();
        check(!leak.passed && leak.delta == 18, "pre-world counter leak still fails");

        PresentedFrameTracker.VerdictEvaluation failedPresents = runFailedSwapchainShouldFail();
        check(!failedPresents.passed, "failed swapchain presents still fail");

        PresentedFrameTracker.VerdictEvaluation stalled = runMidWorldStallShouldFail();
        check(!stalled.passed, "mid-world stall after first present still fails");

        PresentedFrameTracker.VerdictEvaluation legacy = runLegacyBackendWithoutAttemptsShouldFail();
        check(!legacy.passed, "legacy backend without attempt counters still fails closed");

        System.out.println("PresentedFrameTrackerTestDriver: all 13 scenarios PASSED");
    }
}
"""


def find_jdk_tool(name):
    found = shutil.which(name)
    if found:
        return found
    candidates = []
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidates.append(Path(java_home) / "bin" / name)
    candidates.append(Path.home() / ".local/opt/jdk25/bin" / name)
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    return None


class TestPresentedFrameTracker(unittest.TestCase):
    def test_java_tracker_scenarios(self):
        javac = find_jdk_tool("javac")
        java = find_jdk_tool("java")
        self.assertIsNotNone(javac, "javac is required to run PresentedFrameTracker tests")
        self.assertIsNotNone(java, "java is required to run PresentedFrameTracker tests")
        with tempfile.TemporaryDirectory(prefix="celtium-presented-tracker-") as tmp:
            pkg_dir = Path(tmp) / "org/soutalab/celtium/validation"
            pkg_dir.mkdir(parents=True)
            shutil.copy2(TRACKER, pkg_dir / "PresentedFrameTracker.java")
            driver = pkg_dir / "PresentedFrameTrackerTestDriver.java"
            driver.write_text(JAVA_DRIVER, encoding="utf-8")
            subprocess.run(
                [javac, "-d", tmp, str(pkg_dir / "PresentedFrameTracker.java"), str(driver)],
                check=True,
            )
            result = subprocess.run(
                [java, "-cp", tmp, "org.soutalab.celtium.validation.PresentedFrameTrackerTestDriver"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(
                result.returncode, 0,
                f"Java test driver failed:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}",
            )
            self.assertIn("all 13 scenarios PASSED", result.stdout)

    def test_validation_client_wires_tracker_without_hard_backend_type(self):
        src = CLIENT.read_text(encoding="utf-8")
        self.assertIn("new PresentedFrameTracker()", src)
        self.assertIn("celtium$frameTracker.onWorldFrame(", src)
        self.assertIn("celtium$frameTracker.evaluate(", src)
        self.assertIn("celtium$skipsAtArrival", src)
        # Ensure FMLModContainer annotation scanning never sees a hard VulkanTerrainGpuBackend signature.
        for line in src.splitlines():
            stripped = line.strip()
            if stripped.startswith("*") or stripped.startswith("//") or "Class.forName" in stripped or ".equals(" in stripped:
                continue
            self.assertNotIn("VulkanTerrainGpuBackend", stripped, f"hard backend reference in: {stripped}")


if __name__ == "__main__":
    unittest.main()
