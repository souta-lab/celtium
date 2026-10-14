#!/usr/bin/env python3
"""Security + determinism contracts for the real ZIP census (synthetic packs only)."""
import io
import json
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from lock_archive import scan, validate_path


def fixture_zip(path, mods=None, overrides=None, forge='forge-14.23.5.2860', extra=None):
    if mods is None:
        mods = [{'projectID': 22, 'fileID': 44, 'required': True},
                {'projectID': 11, 'fileID': 33, 'required': True}]
    if overrides is None:
        overrides = {'overrides/config/abc.cfg': b'example=true\n',
                     'overrides/mods/fixture.jar': b'synthetic-not-a-real-jar'}
    manifest = {'manifestType': 'minecraftModpack', 'manifestVersion': 1,
                'name': 'Synthetic Fixture', 'version': '0.1', 'author': 'test',
                'minecraft': {'version': '1.12.2',
                              'modLoaders': [{'id': forge, 'primary': True}]},
                'files': mods, 'overrides': 'overrides'}
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', json.dumps(manifest))
        for name, data in overrides.items():
            z.writestr(name, data)
        for name, data in (extra or {}).items():
            z.writestr(name, data)


class ArchiveLockTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.path = Path(self.dir.name)/'pack.zip'

    def scan(self):
        return scan(self.path, 321, 654)

    def test_valid_pin_deterministic_order_and_embedded_mod(self):
        fixture_zip(self.path)
        a = self.scan()
        self.assertEqual(a, self.scan())
        self.assertEqual([x['projectID'] for x in a['manifestMods']], [11, 22])
        self.assertEqual(a['forge'], 'forge-14.23.5.2860')
        self.assertEqual(a['configFileCount'], 1)
        self.assertEqual(len(a['embeddedModJars']), 1)
        self.assertEqual(a['embeddedModJars'][0]['bytes'], len(b'synthetic-not-a-real-jar'))
        self.assertEqual(len(a['archiveSha256']), 64)
        self.assertEqual(len(a['overridesSha256']), 64)

    def test_rejects_path_traversal_and_windows_paths(self):
        for name in ('overrides/../outside.txt', '/absolute/file', 'overrides\\evil.cfg',
                     'C:/Windows/evil', 'overrides/./test.cfg', 'overrides//broken',
                     'overrides/other\x00file'):
            with self.subTest(path=name):
                with self.assertRaises(ValueError):
                    validate_path(name)
        fixture_zip(self.path, extra={'../escape.txt': b'should never extract'})
        with self.assertRaisesRegex(ValueError, 'unsafe ZIP path'):
            self.scan()

    def test_missing_or_invalid_manifest(self):
        with zipfile.ZipFile(self.path, 'w') as z:
            z.writestr('overrides/config/a', b'x')
        with self.assertRaisesRegex(ValueError, 'manifest.json missing'):
            self.scan()
        fixture_zip(self.path, forge='fabric-0.15')
        with self.assertRaisesRegex(ValueError, 'Forge'):
            self.scan()

    def test_duplicate_mod_projects_refused(self):
        fixture_zip(self.path, mods=[{'projectID': 33, 'fileID': 1},
                                     {'projectID': 33, 'fileID': 2}])
        with self.assertRaisesRegex(ValueError, 'duplicate project'):
            self.scan()

    def test_duplicate_zip_entries_refused(self):
        fixture_zip(self.path)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.path, 'a') as z:
                z.writestr('overrides/config/abc.cfg', b'other')
        with self.assertRaisesRegex(ValueError, 'duplicate ZIP'):
            self.scan()

    def test_symlink_entry_refused(self):
        fixture_zip(self.path)
        with zipfile.ZipFile(self.path, 'a') as z:
            entry = zipfile.ZipInfo('overrides/link.cfg')
            entry.create_system = 3
            entry.external_attr = 0o120777 << 16
            z.writestr(entry, 'target')
        with self.assertRaisesRegex(ValueError, 'ZIP symlink'):
            self.scan()

    def test_invalid_lock_or_changed_override_detected(self):
        fixture_zip(self.path)
        before = self.scan()
        fixture_zip(self.path, overrides={'overrides/config/abc.cfg': b'example=false\n'})
        after = self.scan()
        self.assertNotEqual(before['archiveSha256'], after['archiveSha256'])
        self.assertNotEqual(before['overridesSha256'], after['overridesSha256'])

    def test_cli_check_matches_then_fails_if_modified(self):
        fixture_zip(self.path)
        script = Path(__file__).parent/'lock_archive.py'
        lock = Path(self.dir.name)/'lock.json'
        args = [sys.executable, str(script), str(self.path), '--project-id', '321', '--file-id', '654']
        self.assertEqual(subprocess.run(args + ['--write', str(lock)], capture_output=True).returncode, 0)
        self.assertEqual(subprocess.run(args + ['--check', str(lock)], capture_output=True).returncode, 0)
        lock.write_text(lock.read_text().replace('"fileID": 33', '"fileID": 34'))
        self.assertEqual(subprocess.run(args + ['--check', str(lock)], capture_output=True).returncode, 1)


if __name__ == '__main__':
    unittest.main(verbosity=2)
