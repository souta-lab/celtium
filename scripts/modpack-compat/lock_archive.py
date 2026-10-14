#!/usr/bin/env python3
"""Reproducible CurseForge modpack inventory; never extract or redistribute pack assets.

Output consists only of manifest numeric IDs, paths and cryptographic digests.
A full modpack runtime test is a separate acceptance gate.
"""
import argparse
import hashlib
import json
import re
import stat
import sys
from pathlib import Path
from zipfile import ZipFile, BadZipFile

MAX_ARCHIVE_ENTRIES = 20_000
MAX_TOTAL_BYTES = 1024 * 1024 * 1024
MAX_FILE_BYTES = 128 * 1024 * 1024


def sha256_stream(source):
    digest = hashlib.sha256()
    while True:
        data = source.read(1024 * 1024)
        if not data:
            break
        digest.update(data)
    return digest.hexdigest()


def validate_path(raw):
    # ZIP separators must be '/', and decoding/canonicalisation is not an extraction operation.
    if not raw or raw.startswith('/') or '\\' in raw or '\x00' in raw or re.match(r'^[a-zA-Z]:', raw):
        raise ValueError(f'unsafe ZIP path: {raw!r}')
    parts = raw.rstrip('/').split('/')
    if any(part in ('', '.', '..') for part in parts):
        raise ValueError(f'unsafe ZIP path component: {raw!r}')
    return raw


def scan(archive, project_id, file_id):
    # The ZIP itself stays local, and its contents are read only for hashing/manifest fields.
    with archive.open('rb') as handle:
        archive_sha = sha256_stream(handle)
    with ZipFile(archive) as z:
        infos = z.infolist()
        if len(infos) > MAX_ARCHIVE_ENTRIES:
            raise ValueError('ZIP has too many entries')
        seen = set()
        total = 0
        manifest_info = None
        for info in infos:
            path = validate_path(info.filename)
            if path in seen:
                raise ValueError(f'duplicate ZIP entry: {path}')
            seen.add(path)
            file_mode = (info.external_attr >> 16) & 0o170000
            if stat.S_IFMT(file_mode) == stat.S_IFLNK:
                raise ValueError(f'ZIP symlink not permitted: {path}')
            if info.is_dir():
                continue
            if info.file_size > MAX_FILE_BYTES:
                raise ValueError(f'file larger than max: {path}')
            total += info.file_size
            if total > MAX_TOTAL_BYTES:
                raise ValueError('ZIP expands past byte ceiling')
            if path == 'manifest.json':
                manifest_info = info
        if manifest_info is None:
            raise ValueError('manifest.json missing')
        manifest_bytes = z.read(manifest_info)
        manifest = json.loads(manifest_bytes)
        if manifest.get('manifestType') != 'minecraftModpack' or manifest.get('manifestVersion') != 1:
            raise ValueError('unrecognized CurseForge manifest')
        minecraft = manifest['minecraft']
        if minecraft['version'] != '1.12.2':
            raise ValueError('not a Minecraft 1.12.2 pack')
        if len(minecraft['modLoaders']) != 1 or not minecraft['modLoaders'][0]['id'].startswith('forge-'):
            raise ValueError('expected single primary Forge loader')
        if minecraft['modLoaders'][0].get('primary') is not True:
            raise ValueError('Forge loader is not primary')
        mod_files = []
        for item in manifest['files']:
            if not isinstance(item.get('projectID'), int) or not isinstance(item.get('fileID'), int):
                raise ValueError('non-integer projectID/fileID')
            if item['projectID'] <= 0 or item['fileID'] <= 0:
                raise ValueError('invalid mod ID')
            mod_files.append({'projectID': item['projectID'], 'fileID': item['fileID'],
                              'required': item.get('required', True)})
        mod_files.sort(key=lambda x: (x['projectID'], x['fileID']))
        if len({x['projectID'] for x in mod_files}) != len(mod_files):
            raise ValueError('duplicate project IDs (ambiguous file versions)')

        contents = hashlib.sha256()
        override_count = 0
        config_count = 0
        embedded_mods = []
        for info in sorted(infos, key=lambda i: i.filename):
            name = info.filename
            if info.is_dir() or not name.startswith('overrides/'):
                continue
            override_count += 1
            config_count += name.startswith('overrides/config/')
            with z.open(info) as input_file:
                checksum = sha256_stream(input_file)
            contents.update(name.encode('utf-8'))
            contents.update(b'\x00')
            contents.update(checksum.encode('ascii'))
            contents.update(b'\n')
            if name.startswith('overrides/mods/') and name.lower().endswith('.jar'):
                embedded_mods.append({'path': name, 'sha256': checksum, 'bytes': info.file_size})

    return {'schema': 1, 'curseforgeProjectID': project_id, 'curseforgeFileID': file_id,
            'packName': manifest['name'], 'packVersion': manifest['version'],
            'archiveSha256': archive_sha,
            'manifestSha256': hashlib.sha256(manifest_bytes).hexdigest(),
            'minecraftVersion': minecraft['version'], 'forge': minecraft['modLoaders'][0]['id'],
            'manifestModCount': len(mod_files), 'manifestMods': mod_files,
            'zipEntryCount': len(infos), 'overrideFileCount': override_count,
            'configFileCount': config_count, 'overridesSha256': contents.hexdigest(),
            'embeddedModJars': embedded_mods}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('archive', type=Path)
    p.add_argument('--project-id', type=int, required=True)
    p.add_argument('--file-id', type=int, required=True)
    out = p.add_mutually_exclusive_group()
    out.add_argument('--write', type=Path)
    out.add_argument('--check', type=Path)
    args = p.parse_args()
    snapshot = scan(args.archive, args.project_id, args.file_id)
    text = json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + '\n'
    if args.write:
        args.write.parent.mkdir(parents=True, exist_ok=True)
        args.write.write_text(text)
        print(f'wrote {args.write}: {snapshot["packName"]} {snapshot["packVersion"]} '
              f'with {snapshot["manifestModCount"]} mod entries')
    elif args.check:
        if not args.check.exists() or args.check.read_text() != text:
            print(f'LOCK MISMATCH: {args.check} differs from {args.archive}', file=sys.stderr)
            return 1
        print(f'MATCH: {snapshot["packName"]} {snapshot["packVersion"]} '
              f'({snapshot["manifestModCount"]} mods, archive {snapshot["archiveSha256"][:12]})')
    else:
        print(text, end='')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, KeyError, BadZipFile, OSError) as exc:
        print(f'ERROR: {exc}', file=sys.stderr)
        sys.exit(2)
