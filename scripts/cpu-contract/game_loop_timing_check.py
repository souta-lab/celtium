#!/usr/bin/env python3
"""Compile the real GameLoopTiming and check its loop/display pairing rules without a game."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "forge122/src/main/java/org/taumc/celeritas/impl/profile/GameLoopTiming.java"


def main():
    java = r'''
import org.taumc.celeritas.impl.profile.GameLoopTiming;
class GameLoopTimingCheck {
    static void check(boolean ok,String label) { if (!ok) throw new AssertionError(label); }
    static void busy(long nanos) { long end=System.nanoTime()+nanos; while (System.nanoTime()<end) {} }
    public static void main(String[] args) {
        // A display update outside any loop is the loading phase's: it must not join the per-frame mean.
        GameLoopTiming.displayUpdateStarted();
        busy(500_000);
        GameLoopTiming.displayUpdateEnded();
        check(GameLoopTiming.displayUpdateTotalMillis()==0,"updateDisplay outside a loop was counted");
        GameLoopTiming.displayUpdateEnded(); // an unmatched end must not throw or corrupt depth
        for (int i=0;i<3;i++) {
            GameLoopTiming.loopStarted();
            GameLoopTiming.tickStarted(); busy(200_000); GameLoopTiming.tickEnded();
            GameLoopTiming.tickStarted(); busy(200_000); GameLoopTiming.tickEnded();
            GameLoopTiming.displayUpdateStarted();
            GameLoopTiming.displayUpdateStarted(); // resize re-entry: the inner call is not a second present
            busy(300_000);
            GameLoopTiming.displayUpdateEnded();
            GameLoopTiming.displayUpdateEnded();
            GameLoopTiming.blitStarted(); busy(100_000); GameLoopTiming.blitEnded();
            GameLoopTiming.syncStarted(); busy(50_000); GameLoopTiming.syncEnded();
            GameLoopTiming.loopEnded();
        }
        long blitBefore=GameLoopTiming.blitTotalMillis(), syncBefore=GameLoopTiming.syncTotalMillis();
        // The blit and sync calls outside a loop are not counted.
        GameLoopTiming.blitStarted(); busy(100_000); GameLoopTiming.blitEnded();
        GameLoopTiming.syncStarted(); busy(100_000); GameLoopTiming.syncEnded();
        check(GameLoopTiming.blitTotalMillis()==blitBefore && GameLoopTiming.syncTotalMillis()==syncBefore,
              "blit/sync outside a loop was counted");
        check(GameLoopTiming.loops()==3,"loops miscounted: "+GameLoopTiming.loops());
        check(GameLoopTiming.ticks()==6,"ticks miscounted: "+GameLoopTiming.ticks());
        long display=GameLoopTiming.displayUpdateTotalMillis();
        check(display>=0,"display total regressed");
        // Three loops at ~300us+ of present each: the re-entrant calls must not double-count a span twice.
        check(display*1_000_000L < 3*600_000L,"re-entrant updateDisplay double-counted: "+display);
        check(GameLoopTiming.lastLoopTickMillis()>=0 && GameLoopTiming.lastLoopMillis()>=GameLoopTiming.lastLoopTickMillis(),
              "loop snapshot inconsistent");
        String s=GameLoopTiming.summary();
        check(s.contains("ticks=6") && s.contains("loops=3") && s.contains("displayUpdateMillisTotal="+display),
              "summary lost the new fields: "+s);
        // A tick that never returns records nothing.
        long before=GameLoopTiming.ticks();
        GameLoopTiming.loopStarted();
        GameLoopTiming.tickStarted();
        GameLoopTiming.loopEnded();
        check(GameLoopTiming.ticks()==before,"unclosed tick was recorded");
        // A loop that never returns records nothing.
        before=GameLoopTiming.loops();
        GameLoopTiming.loopStarted();
        check(GameLoopTiming.loops()==before,"unclosed loop was recorded");
        System.out.println("GameLoopTiming: loop/display pairing, re-entry, unclosed spans and summary PASS");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-looptiming-") as tmp:
        path = Path(tmp) / "GameLoopTimingCheck.java"
        path.write_text(java)
        # GameLoopTiming tells the Vulkan frame timer that a tick ran; a stand-in keeps this check off the
        # Vulkan module's classpath.
        stub = Path(tmp) / "org/soutalab/celtium/vulkan/FramePassTimings.java"
        stub.parent.mkdir(parents=True)
        stub.write_text("package org.soutalab.celtium.vulkan; public final class FramePassTimings "
                        "{ public static void noteTick() {} }")
        subprocess.run([shutil.which("javac"), "-d", tmp, str(SOURCE), str(stub), str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "GameLoopTimingCheck"], check=True)


if __name__ == "__main__":
    main()
