#!/usr/bin/env python3
"""Capability-safety contract: GL-less chunk initialization never attempts persistent GL mapping."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
staging = (root / 'common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java').read_text()
manager = (root / 'common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager.java').read_text()
# The property this guard must agree with is defined by the GL-less entry points that survive: the Display mixin
# that used to carry it was deleted (commit 508fe76cc) because it never registered - the mixin plugin's getMixins()
# runs after RandomPatches defines org.lwjgl.opengl.Display, so the coremod transformer took over that job. The
# contract is unchanged, so it is read from the loader plugin, which installs the stub under the same property.
display = (root / 'forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java').read_text()

conditions = {
    'GL-less default matches GLFW_NO_API enabled default':
        'return !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"));' in display,
    'mapped staging checks the same explicit GL-less default':
        'if (!"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {' in staging,
    'GL-less guard returns before any live GL capability query':
        bool(re.search(r'public static boolean isSupported\(RenderDevice instance\) \{\s*(?://[^\n]*\n|\s)*if \(!"false"\.equalsIgnoreCase\(System\.getProperty\("celtium\.vulkan\.glless"\)\)\) \{\s*return false;\s*\}\s*var functions = instance\.getDeviceFunctions\(\);',staging)),
    'region manager preserves the non-mapped staging fallback':
        'return new FallbackStagingBuffer(commandList);' in manager,
    'legacy GL enabled option still can use mapped buffers':
        'LWJGL.isOpenGLVersionSupported(4, 4)' in staging and 'LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage)' in staging,
    'no speculative 16MiB GL mapping before GL-less branch':
        staging.index('public static boolean isSupported(') < staging.index('if (!"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {') < staging.index('var functions = instance.getDeviceFunctions();'),
}
for label,passed in conditions.items():
    print(('PASS ' if passed else 'FAIL ')+label)
print(f'{sum(conditions.values())}/{len(conditions)} CPU source contracts')
sys.exit(0 if all(conditions.values()) else 1)
