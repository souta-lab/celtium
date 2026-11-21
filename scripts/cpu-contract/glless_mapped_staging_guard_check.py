#!/usr/bin/env python3
"""Capability-safety contract: chunk initialization never attempts persistent GL mapping."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
arena_dir = root / 'common/src/main/java/org/embeddedt/embeddium/impl/gl/arena'
staging_dir = arena_dir / 'staging'
manager_path = root / 'common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager.java'
# This guard used to pin the contract inside the GL staging classes themselves: MappedStagingBuffer.isSupported()
# hardcoded to false, no property override, and RenderRegionManager installing only the non-mapped
# FallbackStagingBuffer. The decouple change (PR #426) deleted the whole GL staging/arena machinery, so the
# contract is now structural: the code that could attempt persistent GL mapping no longer exists. The guard
# therefore pins the absence, so reintroducing GL staging into chunk initialization fails here.
# The GL-less property definition itself survives in CeltiumGllessHint and is covered by
# glless_hint_descriptor_check.py, so it is not re-read here.
#
# Two shapes of rot are pinned against as well (bughunt 09-05, the #426 shape one level up): the pinned
# manager path is read only if it exists - a rename or deletion is a named FAIL below, not an exception
# that fails CI opaquely - and the absence is pinned over the whole common tree, not only at the three
# historical paths, so the staging machinery cannot return under another package or name variant of
# these class names.
manager_exists = manager_path.is_file()
manager = manager_path.read_text() if manager_exists else ''

java_root = root / 'common/src/main/java'
staging_class_names = ('MappedStagingBuffer.java', 'FallbackStagingBuffer.java',
                       'StagingBuffer.java', 'GlBufferArena.java')
found_staging = sorted(str(path.relative_to(java_root))
                       for name in staging_class_names for path in java_root.rglob(name))
if found_staging:
    print('found GL staging/arena classes: ' + ', '.join(found_staging))

conditions = {
    'the region manager source exists at its pinned path':
        manager_exists,
    'the mapped staging buffer class is deleted, so persistent GL mapping cannot be attempted':
        not (staging_dir / 'MappedStagingBuffer.java').exists(),
    'the GL staging and arena classes are deleted':
        not (staging_dir / 'FallbackStagingBuffer.java').exists()
        and not (staging_dir / 'StagingBuffer.java').exists()
        and not (arena_dir / 'GlBufferArena.java').exists(),
    'no GL staging or arena class exists under any package of common/src/main/java':
        not found_staging,
    'region manager references no GL arena package':
        manager_exists and 'impl.gl.arena' not in manager,
    'region manager installs no staging buffer':
        manager_exists
        and 'new FallbackStagingBuffer' not in manager
        and 'new MappedStagingBuffer' not in manager,
}
for label, passed in conditions.items():
    print(('PASS ' if passed else 'FAIL ') + label)
print(f'{sum(conditions.values())}/{len(conditions)} CPU source contracts')
sys.exit(0 if all(conditions.values()) else 1)
