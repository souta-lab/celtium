#!/usr/bin/env python3
"""Capability-safety contract: chunk initialization never attempts persistent GL mapping."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
arena_dir = root / 'common/src/main/java/org/embeddedt/embeddium/impl/gl/arena'
staging_dir = arena_dir / 'staging'
manager = (root / 'common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager.java').read_text()
# This guard used to pin the contract inside the GL staging classes themselves: MappedStagingBuffer.isSupported()
# hardcoded to false, no property override, and RenderRegionManager installing only the non-mapped
# FallbackStagingBuffer. The decouple change (PR #426) deleted the whole GL staging/arena machinery, so the
# contract is now structural: the code that could attempt persistent GL mapping no longer exists. The guard
# therefore pins the absence, so reintroducing GL staging into chunk initialization fails here.
# The GL-less property definition itself survives in CeltiumGllessHint and is covered by
# glless_hint_descriptor_check.py, so it is not re-read here.

conditions = {
    'the mapped staging buffer class is deleted, so persistent GL mapping cannot be attempted':
        not (staging_dir / 'MappedStagingBuffer.java').exists(),
    'the GL staging and arena classes are deleted':
        not (staging_dir / 'FallbackStagingBuffer.java').exists()
        and not (staging_dir / 'StagingBuffer.java').exists()
        and not (arena_dir / 'GlBufferArena.java').exists(),
    'region manager references no GL arena package':
        'impl.gl.arena' not in manager,
    'region manager installs no staging buffer':
        'new FallbackStagingBuffer' not in manager
        and 'new MappedStagingBuffer' not in manager,
}
for label, passed in conditions.items():
    print(('PASS ' if passed else 'FAIL ') + label)
print(f'{sum(conditions.values())}/{len(conditions)} CPU source contracts')
sys.exit(0 if all(conditions.values()) else 1)
