"""Wiring pins for item 356-C (the mixin error handler).

The recorder's decisions are pinned by the Java suite beside the ledger
(celtium-vulkan/src/test/.../patch/PatchErrorRecorderTest.java). What that
suite cannot see is the forge122 side: the handler class itself and its
one-line registration. forge122 has no test source set, so the pins live
here, in the same source-reading style as test_check_patch_groups.py.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HANDLER = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeltiumMixinErrorHandler.java"
LOADING_PLUGIN = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java"
RECORDER = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchErrorRecorder.java"


class HandlerShapeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.handler = HANDLER.read_text(encoding="utf-8")

    def test_handler_exists_and_implements_the_mixin_interface(self):
        self.assertIn("public class CeltiumMixinErrorHandler implements IMixinErrorHandler", self.handler)
        self.assertIn("package org.taumc.celeritas.core;", self.handler)

    def test_both_error_entry_points_are_implemented(self):
        self.assertIn("onPrepareError", self.handler)
        self.assertIn("onApplyError", self.handler)
        self.assertIn('"prepare"', self.handler)
        self.assertIn('"apply"', self.handler)

    def test_the_action_is_returned_unchanged_and_never_replaced(self):
        # Record-only: the handler hands Mixin's own action back. Naming a
        # replacement action anywhere in the file would break that contract.
        self.assertIn("return action;", self.handler)
        self.assertNotIn("ErrorAction.WARN", self.handler)
        self.assertNotIn("ErrorAction.ERROR", self.handler)
        self.assertNotIn("ErrorAction.NONE", self.handler)

    def test_recording_is_delegated_to_the_pure_recorder(self):
        self.assertIn("PatchErrorRecorder.isOwnMixin(", self.handler)
        self.assertIn("PatchErrorRecorder.record(", self.handler)


class RegistrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plugin = LOADING_PLUGIN.read_text(encoding="utf-8")

    def test_the_handler_is_registered_from_get_mixin_configs(self):
        self.assertIn("Mixins.registerErrorHandlerClass(", self.plugin)
        self.assertIn("CeltiumMixinErrorHandler.class.getName()", self.plugin)

    def test_registration_precedes_the_config_list(self):
        registered = self.plugin.index("registerErrorHandlerClass")
        configs = self.plugin.index('Collections.singletonList("mixins.celeritas.json")')
        self.assertLess(registered, configs,
                        "registration must run before the configs are handed over, "
                        "so a failure in the first config is still recorded")


class RecorderVocabularyTests(unittest.TestCase):
    def test_the_diagnostic_lines_live_in_the_recorder(self):
        recorder = RECORDER.read_text(encoding="utf-8")
        self.assertIn("CELTIUM_PATCH_QUARANTINED", recorder)
        self.assertIn("CELTIUM_PATCH_ESSENTIAL", recorder)
        self.assertIn("CELTIUM_PATCH_RECORD_FAILED", recorder)


if __name__ == "__main__":
    unittest.main()
