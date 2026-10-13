#!/usr/bin/env python3
"""Guard the GL-less dynamic-texture mirror from reintroducing GL45 DSA size queries.

The 1.12.2 decompiled DynamicTexture holds `width` and `height` final fields.
The mixin must take dimensions from that object and never ask a missing GL
context for texture-level data (which previously caused a fatal JVM abort).
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MIXIN = ROOT / 'forge122/src/main/java/org/taumc/celeritas/mixin/core/DynamicTextureMixin.java'
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java'


def main():
    mixin = MIXIN.read_text()
    backend = BACKEND.read_text()
    for name in ('width', 'height'):
        assert re.search(r'@Shadow\s+@Final\s+private\s+int\s+' + name + r'\s*;', mixin), name
    assert re.search(r'vulkan\.announceDynamicTexture\(\s*\(\(DynamicTexture\).*?celtium\$pixels\.get\(this\),\s*this\.width,\s*this\.height\)', mixin, re.S)
    # A two-argument overload has no dimensions and historically queried GL45C;
    # it must not be reachable from a GL-less game or silently reintroduced.
    assert not re.search(r'public\s+void\s+announceDynamicTexture\s*\(\s*int\s+glTextureId\s*,\s*int\[\]\s+pixels\s*\)', backend)
    assert re.search(r'public\s+void\s+announceDynamicTexture\s*\(\s*int\s+glTextureId\s*,\s*int\[\]\s+pixels\s*,\s*int\s+width\s*,\s*int\s+height\s*\)', backend)
    original = ROOT / '.celtium-work/mc-sources/net/minecraft/client/renderer/texture/DynamicTexture.java'
    if original.exists():
        src = original.read_text()
        for name in ('width', 'height'):
            assert re.search(r'private\s+final\s+int\s+' + name + r'\s*;', src), name
    print('dynamic texture: mapped native fields, 4-arg mirror, no GL45 size-query overload PASS')


if __name__ == '__main__':
    main()
