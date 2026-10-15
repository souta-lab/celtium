#!/usr/bin/env python3
"""Fail-closed contract: GL-less must not depend on one mixin winning a class-load race.

Measured 2026-10-15 (leg `gv-e2e-268-xlog`, `-Xlog:class+load=info`): in the 268-mod E2E
pack RandomPatches' tweak initialisation defines `org.lwjgl.opengl.Display` at 8.975 s,
while the celtium mixin plugin's `getMixins()` only runs at 9.837 s. A mixin registered
after its target class is defined can never apply, so `core.LwjglxDisplayMixin` never ran,
the GL-less stub was never installed, the window was created with a real GL 4.6 context and
CleanMix logged nothing about it at all. `onLoad` runs at 7.1 s in the same leg.

The contract is that the two things that mixin does are also done from the plugin, gated on
GL-less, and that the mixin itself stays as the strongest place for the window hint.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / 'forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java'


def main():
    checks = []

    def require(label, ok):
        checks.append(bool(ok))
        print(('ok  ' if ok else 'FAIL') + label)

    plugin = PLUGIN.read_text(encoding='utf-8')
    require('onLoad installs GL-less early',
            'celtium$installGllessEarly();' in plugin and 'public void onLoad(String mixinPackage)' in plugin)
    require('the early path is gated on the GL-less property',
            'celtium.vulkan.glless' in plugin and 'GLLESS_ENABLED' in plugin)
    require('the early path installs the stub',
            'CeltiumGlStub.install()' in plugin)
    # A call, not a mention: the method's javadoc explains why the hint cannot be set here.
    require('the early path does NOT call glfwWindowHint before init',
            'glfwWindowHint(' not in plugin)
    require('a failure in the early path is logged, not fatal',
            'early stub install failed' in plugin)
    require('the early install is distinguishable in a leg log',
            'installed early' in plugin)

    config = (ROOT / 'forge122/src/main/resources/mixins.celeritas.json').read_text(encoding='utf-8')
    require('the config JSON no longer claims to register the Display mixin',
            'LwjglxDisplayMixin' not in config)
    require('the dead Display mixin is gone', not (ROOT / 'forge122/src/main/java/org/taumc/celeritas/mixin/core/LwjglxDisplayMixin.java').exists())

    require('the window hint and the stub no longer depend on that mixin',
            'CeltiumGllessWindowTransformer' in (ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java').read_text(encoding='utf-8'))

    coremod = (ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java').read_text(encoding='utf-8')
    require('the coremod registers the GL-less transformer',
            'CeltiumGllessWindowTransformer' in coremod and 'getASMTransformerClass' in coremod)
    transformer = (ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java').read_text(encoding='utf-8')
    require('the transformer injects before glfwCreateWindow',
            'glfwCreateWindow' in transformer and 'beforeWindowCreation' in transformer)
    require('the transformer returns the original bytes on failure',
            'catch (Throwable error)' in transformer and 'return basicClass;' in transformer)
    hint = (ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java').read_text(encoding='utf-8')
    require('the hint is gated on GL-less and applied once',
            'celtium.vulkan.glless' in hint and 'applied' in hint)
    require('the hint sets GLFW_CLIENT_API to GLFW_NO_API',
            'glfwWindowHint' in hint and '0x00022001' in hint)

    # The mechanism changed after this contract was written, and the contract follows the mechanism. Installing a
    # GL capabilities object from the stub was replaced by replacing LWJGL3's own native GL methods (commit
    # 582ad2a18, "replace LWJGL3's native GL methods instead of installing capabilities"): a capabilities object
    # built without a current context aborted the JVM in native code, which no catch can survive, so the stub now
    # answers the methods themselves. The property and the early install are what this check is about, and those are
    # unchanged - what follows asserts the mechanism that exists rather than the one that was abandoned.
    stub = (ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java').read_text(encoding='utf-8')
    require('the stub still installs itself as the process GL function source',
            'install()' in stub and 'FunctionProvider' in stub)
    transformer_path = ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java'
    require('the native-replacement transformer is present', transformer_path.exists())
    native = transformer_path.read_text(encoding='utf-8')
    require('the transformer replaces native GL methods rather than building capabilities',
            'ACC_NATIVE' in native and 'createCapabilities' not in native)
    require('the replacement is counted so a leg can see it happen',
            'replaced {} native GL method' in native)
    require('the coremod registers the native-replacement transformer',
            'CeltiumGllessNativeTransformer' in coremod)

    if not all(checks):
        print(f'{checks.count(False)} contract check(s) failed')
        return 1
    print(f'{len(checks)} contract checks passed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
