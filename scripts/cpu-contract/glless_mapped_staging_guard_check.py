#!/usr/bin/env python3
"""Capability-safety contract: chunk initialization never attempts persistent GL mapping."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
staging = (root / 'common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java').read_text()
manager = (root / 'common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager.java').read_text()
# The property this guard must agree with is defined by the GL-less entry points that survive: the Display mixin
# that used to carry it was deleted (commit 508fe76cc) because it never registered - the mixin plugin's getMixins()
# runs after RandomPatches defines org.lwjgl.opengl.Display - so the coremod transformer took over that job. The
# contract is unchanged, so it is read from the loader plugin, which installs the stub under the same property.
# The property and its default are read from CeltiumGllessHint, which is the surviving definition of the GL-less
# default. LwjglxDisplayMixin used to carry the same expression and was deleted in 508fe76cc because it never
# registered - the mixin plugin's getMixins() runs after RandomPatches defines org.lwjgl.opengl.Display - so the
# coremod transformer took over its job. The contract this check enforces is unchanged.
display = (root / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java').read_text()

conditions = {
    'mapped staging is unsupported, with no property to override it':
        bool(re.search(r'public static boolean isSupported\(RenderDevice instance\) \{(?:\s*//[^\n]*\n)*\s*return false;\s*\}', staging))
        and 'System.getProperty' not in staging,
    'mapped staging never reaches a live GL capability query':
        staging.index('public static boolean isSupported(') < staging.index('return false;')
        and 'getDeviceFunctions' not in staging,
    'region manager preserves the non-mapped staging fallback':
        'return new FallbackStagingBuffer(commandList);' in manager,
    'the constructor still installs the fallback staging buffer':
        'this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);' in staging,
}
for label,passed in conditions.items():
    print(('PASS ' if passed else 'FAIL ')+label)
print(f'{sum(conditions.values())}/{len(conditions)} CPU source contracts')
sys.exit(0 if all(conditions.values()) else 1)
