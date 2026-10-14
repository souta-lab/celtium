#!/usr/bin/env python3
"""Offline synthetic checks that no competing terrain renderer reaches the test mods folder."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('e2e_celtium_mods', Path(__file__).with_name('e2e_celtium_mods.py'))
selector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selector)


class RuntimeSelectionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.stage = self.base/'stage'
        self.stage.mkdir()
        self.runtime = self.base/'runtime'
        self.runtime.mkdir()
        self.original = [{'projectID': i, 'fileID': i*10+1, 'required': True}
                         for i in range(10001, 10269)]
        for project, (file_id, _) in selector.COMPETING_RENDERERS.items():
            self.original.append({'projectID': project, 'fileID': file_id, 'required': True})
        self.lock = {'curseforgeProjectID': 282744, 'curseforgeFileID': 9088147,
                     'manifestMods': self.original}
        self.files = []
        for item in self.original:
            p = item['projectID']
            name = selector.COMPETING_RENDERERS[p][1] if p in selector.COMPETING_RENDERERS else f'fixture-{p}.jar'
            raw = f'synthetic-fixture-{p}'.encode('ascii')
            (self.stage/name).write_bytes(raw)
            if p not in selector.COMPETING_RENDERERS:
                (self.runtime/name).write_bytes(raw)
            self.files.append({'projectID': p, 'fileID': item['fileID'], 'name': name,
                               'bytes': len(raw), 'sha256': selector.sha256_file(self.stage/name),
                               'state': 'staged'})
        self.report = {'entries': 270, 'failed': 0, 'files': self.files}

    def test_select_exactly_268_and_verify_runtime(self):
        files, excluded = selector.select(self.lock, self.report, self.stage, self.runtime)
        self.assertEqual(len(files), 268)
        self.assertEqual(set(excluded), {x[1] for x in selector.COMPETING_RENDERERS.values()})

    def test_always_refuses_competing_renderer_in_runtime(self):
        (self.runtime/'Nothirium-1.12.2-0.4.9-beta.jar').write_bytes(b'synthetic')
        with self.assertRaisesRegex(ValueError, 'competing Nothirium renderer'):
            selector.select(self.lock, self.report, self.stage, self.runtime)

    def test_strict_release_lock(self):
        self.lock['curseforgeFileID'] = 1
        with self.assertRaisesRegex(ValueError, 'not the locked'):
            selector.select(self.lock, self.report, self.stage)

    def test_version_drift_fails_closed(self):
        self.lock['manifestMods'][-1]['fileID'] += 1
        with self.assertRaisesRegex(ValueError, 'manifest/report mismatch'):
            selector.select(self.lock, self.report, self.stage)

    def test_report_must_be_complete(self):
        self.report['files'].pop()
        with self.assertRaisesRegex(ValueError, 'reported projects'):
            selector.select(self.lock, self.report, self.stage)

    def test_partial_unverified_mod_cannot_load(self):
        item = self.files[3]
        (self.stage/item['name']).write_bytes(b'altered')
        with self.assertRaisesRegex(ValueError, 'does not match'):
            selector.select(self.lock, self.report, self.stage)

    def test_runtime_cannot_omit_remaining_content_mods(self):
        (self.runtime/'fixture-10001.jar').unlink()
        with self.assertRaisesRegex(ValueError, 'missing 1 required'):
            selector.select(self.lock, self.report, self.stage, self.runtime)

    def test_does_not_allow_staging_symlink(self):
        jar=self.stage/'fixture-10001.jar'
        jar.unlink();jar.symlink_to(self.stage/'fixture-10002.jar')
        with self.assertRaisesRegex(ValueError, 'nonregular stage'):
            selector.select(self.lock, self.report, self.stage)


if __name__ == '__main__':
    unittest.main(verbosity=2)
