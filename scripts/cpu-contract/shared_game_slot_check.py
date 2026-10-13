#!/usr/bin/env python3
"""Verify shared worktree identity and exclusive, fully-written PID publication using real child processes."""
import importlib.util
import multiprocessing
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("prism_validation", SCRIPTS / "prism_validation.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def contender(lock, barrier, release, results):
    barrier.wait(timeout=5)
    try:
        runner.claim_pid_file(Path(lock))
        results.put(("winner", os.getpid()))
    except FileExistsError:
        results.put(("busy", int(Path(lock).read_text())))
    finally:
        release.wait(timeout=5)


def reclaim_contender(lock, barrier, release, results):
    barrier.wait(timeout=5)
    claimed, holder = runner.try_claim_game_slot(Path(lock))
    results.put(("winner" if claimed else "busy", holder))
    release.wait(timeout=5)
    runner.release_game_slot(Path(lock))



class SharedGameSlotCheck(unittest.TestCase):
    def test_git_worktrees_have_one_slot(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            second = Path(tmp) / "other"
            repo.mkdir()
            def git(*args):
                subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)
            git("init")
            (repo / "tracked").write_text("fixture")
            git("add", "tracked")
            git("-c", "user.name=slot-test", "-c", "user.email=slot@example.invalid",
                "commit", "-m", "fixture")
            git("worktree", "add", "-b", "test-other", str(second))
            self.assertEqual(runner.shared_game_slot(repo), runner.shared_game_slot(second))
            self.assertEqual(runner.shared_game_slot(repo), repo / ".celtium-work/game-slot")

    def test_standalone_source_keeps_a_local_slot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertEqual(runner.shared_game_slot(root), root / ".celtium-work/game-slot")

    def test_competing_processes_see_one_complete_pid(self):
        # fork is deliberate: this Linux-only launcher check does not create GL contexts or launch Minecraft.
        context = multiprocessing.get_context("fork")
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / "owner.pid"
            barrier = context.Barrier(8)
            release = context.Event()
            results = context.Queue()
            workers = [context.Process(target=contender, args=(str(lock), barrier, release, results))
                       for _ in range(8)]
            for worker in workers:
                worker.start()
            try:
                outcomes = [results.get(timeout=5) for _ in workers]
                winners = [pid for kind, pid in outcomes if kind == "winner"]
                self.assertEqual(len(winners), 1)
                self.assertEqual(int(lock.read_text()), winners[0])
                self.assertTrue(all(pid == winners[0] for _, pid in outcomes))
                self.assertFalse(list(Path(tmp).glob(".owner-*")))
            finally:
                release.set()
                for worker in workers:
                    worker.join(timeout=5)
                    if worker.is_alive():
                        worker.terminate()
                        worker.join()
                for worker in workers:
                    self.assertEqual(worker.exitcode, 0)

    def test_competing_stale_reclaimers_cannot_replace_the_new_owner(self):
        context = multiprocessing.get_context("fork")
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / "owner.pid"
            expired = context.Process(target=lambda: None)
            expired.start()
            expired.join(timeout=5)
            self.assertEqual(expired.exitcode, 0)
            lock.write_text(str(expired.pid))
            barrier = context.Barrier(8)
            release = context.Event()
            results = context.Queue()
            workers = [context.Process(target=reclaim_contender, args=(str(lock), barrier, release, results))
                       for _ in range(8)]
            for worker in workers:
                worker.start()
            try:
                outcomes = [results.get(timeout=5) for _ in workers]
                winners = [pid for kind, pid in outcomes if kind == "winner"]
                self.assertEqual(len(winners), 1)
                self.assertEqual(int(lock.read_text()), winners[0])
                self.assertTrue(all(pid == winners[0] for _, pid in outcomes))
            finally:
                release.set()
                for worker in workers:
                    worker.join(timeout=5)
                    if worker.is_alive():
                        worker.terminate()
                        worker.join()
                for worker in workers:
                    self.assertEqual(worker.exitcode, 0)
            self.assertFalse(lock.exists())

    def test_release_does_not_remove_a_different_live_owner(self):
        context = multiprocessing.get_context("fork")
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / "owner.pid"
            released = context.Event()
            worker = context.Process(target=lambda: released.wait(timeout=5))
            worker.start()
            try:
                lock.write_text(str(worker.pid))
                runner.release_game_slot(lock)
                self.assertEqual(int(lock.read_text()), worker.pid)
            finally:
                released.set()
                worker.join(timeout=5)
                if worker.is_alive():
                    worker.terminate()
                    worker.join()

    def test_owner_releases_its_own_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / "owner.pid"
            self.assertEqual(runner.try_claim_game_slot(lock), (True, os.getpid()))
            runner.release_game_slot(lock)
            self.assertFalse(lock.exists())
            self.assertTrue((lock.parent / ".owner.guard").exists())

    def test_existing_owner_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            lock = Path(tmp) / "owner.pid"
            lock.write_text(str(os.getpid()))
            with self.assertRaises(FileExistsError):
                runner.claim_pid_file(lock)
            self.assertEqual(lock.read_text(), str(os.getpid()))
            self.assertFalse(list(Path(tmp).glob(".owner-*")))


if __name__ == "__main__":
    unittest.main()
