#!/usr/bin/env python3
"""Runs ServerShutdownAwaiterCheck, the CPU contract for the integrated server's shutdown await.

ServerShutdownAwaiter.await decides whether a shutdown task queued during the server's last tick
is released, cancelled, or answered after its consumer thread has already terminated - the shape
that otherwise hangs client shutdown. The check is plain JDK (the production class plus a main
that throws AssertionError), and until now nothing ran it: it was referenced by no script, no
workflow and no test, and the production class has no JUnit coverage either (roam-hunt-4).

This wrapper only compiles the two files and propagates the exit code. It needs a JDK 21 javac
(JAVA_HOME, or javac on PATH), which is why the workflow runs it after setup-java.
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "forge122/src/main/java/org/taumc/celeritas/impl/util/ServerShutdownAwaiter.java"
CHECK = ROOT / "scripts/cpu-contract/ServerShutdownAwaiterCheck.java"


def javac():
    home = os.environ.get("JAVA_HOME")
    if home and pathlib.Path(home, "bin", "javac").is_file():
        return str(pathlib.Path(home, "bin", "javac")), str(pathlib.Path(home, "bin", "java"))
    found = shutil.which("javac")
    if found:
        return found, str(pathlib.Path(found).with_name("java"))
    return None, None


def main():
    compiler, runner = javac()
    if compiler is None:
        print("server_shutdown_awaiter_check: no javac found (JAVA_HOME or PATH); the check cannot run")
        return 1
    with tempfile.TemporaryDirectory() as tmp:
        compile_result = subprocess.run(
            [compiler, "-nowarn", "-d", tmp, str(PRODUCTION), str(CHECK)],
            capture_output=True, text=True)
        if compile_result.returncode != 0:
            print("server_shutdown_awaiter_check: compilation failed")
            print(compile_result.stdout)
            print(compile_result.stderr)
            return 1
        run_result = subprocess.run([runner, "-cp", tmp, "ServerShutdownAwaiterCheck"],
                                    capture_output=True, text=True)
        sys.stdout.write(run_result.stdout)
        sys.stderr.write(run_result.stderr)
        if run_result.returncode != 0:
            print("server_shutdown_awaiter_check: FAIL")
            return 1
    print("server_shutdown_awaiter_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
