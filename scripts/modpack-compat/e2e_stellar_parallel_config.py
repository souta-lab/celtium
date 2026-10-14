#!/usr/bin/env python3
"""Fail-closed, *copy-only* E2E/Cleanroom workaround for StellarCore's parallel model loader.

StellarCore is not removed. Its parallel model loader mixin is incompatible with
Cleanroom's patched ModelManager.onResourceManagerReload injection target. Only
B:ParallelModelLoader is switched off in an isolated destination profile.
"""
import argparse
import hashlib
import re
import sys
from pathlib import Path

CONFIG_NAME = 'stellar_core.cfg'
ACTIVE = re.compile(rb'(?m)^(\s*B:ParallelModelLoader\s*=\s*)(true|false)(\s*)$')


def transform(data: bytes) -> bytes:
    if len(data) > 1024 * 1024 or b'\x00' in data:
        raise ValueError('invalid or oversized StellarCore configuration')
    matches = list(ACTIVE.finditer(data))
    if len(matches) != 1:
        raise ValueError('exactly one ParallelModelLoader setting required')
    m = matches[0]
    if m.group(2) == b'false':
        return data
    return data[:m.start(2)] + b'false' + data[m.end(2):]


def apply_copy(source: Path, target: Path) -> dict:
    if source.name != CONFIG_NAME or target.name != CONFIG_NAME:
        raise ValueError('source and target must both be stellar_core.cfg')
    if source.is_symlink() or target.is_symlink() or not source.is_file():
        raise ValueError('source must be a regular config file, target cannot be a symlink')
    if source.resolve() == target.resolve():
        raise ValueError('in-place changes to upstream E2E configs are prohibited')
    if target.exists():
        raise ValueError('target exists; refusing to overwrite a pre-existing profile')
    old = source.read_bytes()
    new = transform(old)
    target.parent.mkdir(parents=True, exist_ok=True)
    # Refuse to follow existing symlink on creation (or to silently replace files).
    with target.open('xb') as output:
        output.write(new)
    return {'sourceSha256': hashlib.sha256(old).hexdigest(),
            'targetSha256': hashlib.sha256(new).hexdigest(),
            'parallelModelLoaderEnabled': False,
            'sourceUnchanged': source.read_bytes() == old}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--target', type=Path, required=True)
    args = parser.parse_args()
    try:
        import json
        print(json.dumps(apply_copy(args.source, args.target), indent=2))
    except (ValueError, OSError) as exc:
        print(f'REFUSED: {exc}', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
