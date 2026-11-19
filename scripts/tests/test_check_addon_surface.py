import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_addon_surface.py"
spec = importlib.util.spec_from_file_location("check_addon_surface", SCRIPT)
surface = importlib.util.module_from_spec(spec)
spec.loader.exec_module(surface)

MIXINS = "forge122/src/main/java/org/taumc/celeritas/mixin"
LIGHT_CACHE = ("forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/"
               "LightDataCache.java")
DOCS = "docs/mod-compatibility.md"

FUNNEL_MIXIN = """package org.taumc.celeritas.mixin.core;
@Mixin(World.class)
public class WorldCombinedLightMixin {
    @WrapMethod(method = "func_175626_b")
    private int funnel(BlockPos pos, int lightValue, Operation<Integer> original) { return 0; }
}
"""

RENDER_GLOBAL = """package org.taumc.celeritas.mixin.core.terrain;
@Mixin(RenderGlobal.class)
public class RenderGlobalMixin {
    @Overwrite
    public void markBlocksForUpdate(int a, int b, int c, int d, int e, int f, boolean g) {}
}
"""

LIGHT_DATA_CACHE = """package org.taumc.celeritas.impl.render.terrain.compile.light;
public class LightDataCache {
    int read(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getPackedLightmapCoords(world, pos);
    }
}
"""

DOC_ROWS = "\n".join([
    "| II-1 | `IBlockState.getPackedLightmapCoords` (T) | plain call |",
    "| II-2 | `Entity.getBrightnessForRender` (E) | none |",
    "| II-3 | `Particle.getBrightnessForRender` (P) | none |",
    "| II-4 | the `getCombinedLight` call in the block-entity dispatcher (B) | none |",
    "| II-5 | `ItemRenderer.setLightmap` (H) | none |",
    "| II-6 | `RenderGlobal.markBlocksForUpdate` (s1) | overwrite kept |",
    "| II-7 | `World.getCombinedLight` return semantics | the funnel |",
]) + "\n"


def mixin(name, body):
    return ("package org.taumc.celeritas.mixin.core;\n"
            f"@Mixin({name}.class)\npublic class SyntheticMixin {{\n{body}\n}}\n")


class AddonSurfaceTests(unittest.TestCase):
    def tree(self, tmp, extra_mixins=None, docs=DOC_ROWS, funnel=FUNNEL_MIXIN,
             render_global=RENDER_GLOBAL, light_cache=LIGHT_DATA_CACHE):
        root = Path(tmp)
        files = {
            "core/WorldCombinedLightMixin.java": funnel,
            "core/terrain/RenderGlobalMixin.java": render_global,
        }
        if extra_mixins:
            files.update(extra_mixins)
        for relative, text in files.items():
            if text is None:
                continue
            path = root / MIXINS / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        if light_cache is not None:
            path = root / LIGHT_CACHE
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(light_cache, encoding="utf-8")
        if docs is not None:
            path = root / DOCS
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(docs, encoding="utf-8")
        return root

    def test_consistent_tree_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(surface.check(self.tree(tmp)), [])

    def test_head_cancel_on_entity_brightness_is_reported(self):
        bad = mixin("Entity", """    @Inject(method = "func_70070_b", at = @At("HEAD"), cancellable = true, require = 0)
    private void hook(CallbackInfoReturnable<Integer> info) {}""")
        with tempfile.TemporaryDirectory() as tmp:
            problems = surface.check(self.tree(tmp, {"core/BadMixin.java": bad}))
            self.assertTrue(any("II-2" in problem for problem in problems), problems)

    def test_wrap_on_combined_light_call_site_is_reported(self):
        bad = mixin("TileEntityRendererDispatcher", """    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"), require = 0)
    private int hook(World world, BlockPos pos, int lightValue, Operation<Integer> original) { return 0; }""")
        with tempfile.TemporaryDirectory() as tmp:
            problems = surface.check(self.tree(tmp, {"core/BadMixin.java": bad}))
            self.assertTrue(any("II-4" in problem for problem in problems), problems)

    def test_injection_on_ledger_point_without_require_is_reported(self):
        bad = mixin("Particle", """    @Inject(method = "func_189214_a", at = @At("HEAD"))
    private void hook(CallbackInfoReturnable<Integer> info) {}""")
        with tempfile.TemporaryDirectory() as tmp:
            problems = surface.check(self.tree(tmp, {"core/BadMixin.java": bad}))
            self.assertTrue(any("require" in problem and "II-3" in problem
                                for problem in problems), problems)

    def test_missing_funnel_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            problems = surface.check(self.tree(tmp, funnel=None))
            self.assertTrue(any("II-7" in problem for problem in problems), problems)

    def test_missing_docs_row_is_reported(self):
        docs = DOC_ROWS.replace("| II-5 | `ItemRenderer.setLightmap` (H) | none |\n", "")
        with tempfile.TemporaryDirectory() as tmp:
            problems = surface.check(self.tree(tmp, docs=docs))
            self.assertTrue(any("II-5" in problem for problem in problems), problems)

    def test_repository_tree_passes(self):
        self.assertEqual(surface.check(), [])


if __name__ == "__main__":
    unittest.main()
