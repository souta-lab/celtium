#!/usr/bin/env python3
"""Print a jar's content hash: entry names and entry bytes, with no zip metadata.

A jar's own sha256 is not a content identity. Measured 2026-10-15: two builds of the
validation client jar had byte-identical entries (18 of them, content hash
0fc313c2a87a7c061ca855dfabe87cf9, 145856 bytes each) and different zip sha256 values
(1011e9cd… and ec995ac8…) because the embedded entry timestamps differed by 23 minutes.
Legs that record only the zip sha therefore record when the build ran, not what it built.

Usage: jar-content-hash.py <jar> [<jar> ...]
"""
import hashlib
import json
import sys
import zipfile


def content_hash(path):
    with zipfile.ZipFile(path) as jar:
        entries = {name: hashlib.sha256(jar.read(name)).hexdigest()
                   for name in sorted(jar.namelist()) if not name.endswith('/')}
    digest = hashlib.sha256(json.dumps(entries, sort_keys=True).encode()).hexdigest()
    return digest, len(entries)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    status = 0
    for path in argv[1:]:
        digest, count = content_hash(path)
        print(f'{digest}  entries={count}  {path}')
    return status


if __name__ == '__main__':
    sys.exit(main(sys.argv))
