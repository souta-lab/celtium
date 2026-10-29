"""Regression for the intentional LWJGL Vulkan Java binding without core/native bundling."""
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "verify_forge_jar.py"
spec = importlib.util.spec_from_file_location("verify_forge_jar", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PackagedVulkanModuleTests(unittest.TestCase):
    def test_embedded_vulkan_java_module_is_accepted(self):
        for name in ("org/lwjgl/", "org/lwjgl/vulkan/", "org/lwjgl/vulkan/VK.class",
                     "org/lwjgl/vulkan/KHRWin32Surface.class",
                     "org/lwjgl/vulkan/video/StdVideoH264HrdParameters.class"):
            with self.subTest(name=name):
                self.assertFalse(module.unexpected_runtime_entry(name))

    def test_other_lwjgl_and_shadow_packages_still_rejected(self):
        for name in ("org/lwjgl/system/MemoryUtil.class", "org/lwjgl/opengl/GL11.class",
                     "org/lwjgl/sdl/SDLVideo.class", "org/lwjgl/glfw/GLFW.class",
                     "org/joml/Matrix4f.class", "com/google/gson/Gson.class"):
            with self.subTest(name=name):
                self.assertTrue(module.unexpected_runtime_entry(name))


if __name__ == "__main__":
    unittest.main()
