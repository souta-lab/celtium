#!/usr/bin/env python3
"""Select the exact E2E 1.93a *runtime* JARs, replacing its Nothirium renderer with Celtium.

Original 270-file CurseForge ZIP/download cache is kept immutable for provenance.
This program does NOT mutate any JAR or pack; it enumerates 268 installable JARs
and can assert a runtime mods directory actually obeys that policy.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

# Only the pinned E2E v1.93a versions; unexpected updates must not be silently filtered.
COMPETING_RENDERERS = {
    624960: (7441674, 'Nothirium-1.12.2-0.4.9-beta.jar'),
    1153019: (6473489, 'naughthirium-2.3.0.jar'),
}
E2E_CURSEFORGE_PROJECT = 282744
E2E_CURSEFORGE_FILE = 9088147


def sha256_file(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def select(lock, report, staged_mods, runtime_mods=None):
    if ((lock.get('curseforgeProjectID'), lock.get('curseforgeFileID')) !=
            (E2E_CURSEFORGE_PROJECT, E2E_CURSEFORGE_FILE)):
        raise ValueError('not the locked original E2E 1.93a pack')
    original = {x['projectID']: x['fileID'] for x in lock['manifestMods']}
    if len(original) != 270 or len(lock['manifestMods']) != len(original):
        raise ValueError('E2E original manifest must list exactly 270 unique projects')
    if report.get('entries') != 270 or report.get('failed') != 0:
        raise ValueError('download report is incomplete or has failures')
    scanned = {}
    for item in report['files']:
        project = item['projectID']
        file_id = item['fileID']
        filename = item['name']
        if (project in scanned or original.get(project) != file_id or
                not filename.endswith('.jar') or filename in ('.', '..') or
                '/' in filename or '\\' in filename or '\x00' in filename or
                any(ord(c) < 32 for c in filename)):
            raise ValueError(f'manifest/report mismatch or unsafe filename: {project}')
        if item.get('state') not in ('staged', 'present'):
            raise ValueError(f'unavailable JAR: {project}')
        scanned[project] = item
    if set(scanned) != set(original):
        raise ValueError('reported projects differ from exact original manifest')

    for project, (file_id, name) in COMPETING_RENDERERS.items():
        if original.get(project) != file_id or scanned[project]['name'] != name:
            raise ValueError('competing renderer version differs from the pinned E2E release')

    paths = []
    excluded = []
    for project in sorted(scanned):
        item = scanned[project]
        path = staged_mods/item['name']
        if path.is_symlink() or not path.is_file() or path.parent.resolve() != staged_mods.resolve():
            raise ValueError(f'missing or nonregular stage JAR: {path.name}')
        if path.stat().st_size != item.get('bytes') or sha256_file(path) != item.get('sha256'):
            raise ValueError(f'staged JAR does not match verified download: {path.name}')
        if project in COMPETING_RENDERERS:
            excluded.append(item['name'])
        else:
            paths.append(path)
    if len(paths) != 268 or len(excluded) != 2:
        raise ValueError('expected 268 original E2E content/utility mods after renderer substitution')
    if runtime_mods is not None:
        actual = {p.name for p in runtime_mods.glob('*.jar') if p.is_file() and not p.is_symlink()}
        if set(excluded) & actual:
            raise ValueError('competing Nothirium renderer entered the Celtium runtime')
        missing = {p.name for p in paths} - actual
        if missing:
            raise ValueError(f'E2E runtime is missing {len(missing)} required selected JARs')
    return paths, excluded


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--lock', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--staged-mods', type=Path, required=True)
    p.add_argument('--runtime-mods', type=Path)
    p.add_argument('--format', choices=('json', 'paths'), default='json')
    args = p.parse_args()
    try:
        paths, excluded = select(json.loads(args.lock.read_text()), json.loads(args.report.read_text()),
                                 args.staged_mods, args.runtime_mods)
    except (OSError, KeyError, TypeError, ValueError) as exc:
        print(f'E2E RUNTIME SELECTION FAILED: {exc}', file=sys.stderr)
        return 2
    if args.format == 'paths':
        for path in paths:
            print(path.resolve())
    else:
        print(json.dumps({'upstreamPack': 'E2E 1.93a', 'originalManifestMods': 270,
                          'celtiumRuntimeManifestMods': len(paths), 'excludedRendererJars': excluded,
                          'verifiedRuntimeJars': args.runtime_mods is not None}, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
