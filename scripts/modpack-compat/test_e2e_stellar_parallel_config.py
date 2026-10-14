#!/usr/bin/env python3
"""Synthetic byte-level acceptance for isolated StellarCore config substitution."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('stellar_config', Path(__file__).with_name('e2e_stellar_parallel_config.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StellarCleanroomConfigTest(unittest.TestCase):
    SAMPLE = b'# StellarCore\r\nperformance {\r\n vanilla {\r\n B:ParallelModelLoader=true\r\n B:ParallelTextureLoad=false\r\n }\r\n}\r\n'

    def test_patch_exact_boolean_only(self):
        result = module.transform(self.SAMPLE)
        self.assertEqual(result, self.SAMPLE.replace(b'ParallelModelLoader=true', b'ParallelModelLoader=false'))
        self.assertIn(b'ParallelTextureLoad=false', result)
        self.assertEqual(result.count(b'\r\n'), self.SAMPLE.count(b'\r\n'))

    def test_idempotent_false(self):
        data = module.transform(self.SAMPLE)
        self.assertEqual(module.transform(data), data)

    def test_missing_setting_refused(self):
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            module.transform(b'B:Other=true\n')

    def test_duplicate_setting_refused(self):
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            module.transform(self.SAMPLE + b'B:ParallelModelLoader=true\n')

    def test_rejects_invalid_or_large(self):
        for data in [self.SAMPLE + b'\0', b'a' * (1024*1024+1)]:
            with self.subTest(size=len(data)), self.assertRaises(ValueError):
                module.transform(data)

    def test_copy_preserves_original_config(self):
        with tempfile.TemporaryDirectory() as path:
            root = Path(path)
            source = root/'upstream/stellar_core.cfg';source.parent.mkdir()
            source.write_bytes(self.SAMPLE)
            dest = root/'isolated/config/stellar_core.cfg'
            report = module.apply_copy(source, dest)
            self.assertEqual(source.read_bytes(), self.SAMPLE)
            self.assertEqual(dest.read_bytes(), module.transform(self.SAMPLE))
            self.assertTrue(report['sourceUnchanged'])
            self.assertNotEqual(report['sourceSha256'], report['targetSha256'])

    def test_no_inplace_changes_or_overwrites(self):
        with tempfile.TemporaryDirectory() as path:
            root = Path(path)
            source = root/'stellar_core.cfg';source.write_bytes(self.SAMPLE)
            with self.assertRaisesRegex(ValueError, 'in-place'):
                module.apply_copy(source, source)
            dest = root/'isolated/stellar_core.cfg';dest.parent.mkdir()
            dest.write_bytes(b'private settings')
            with self.assertRaisesRegex(ValueError, 'target exists'):
                module.apply_copy(source, dest)
            self.assertEqual(dest.read_bytes(), b'private settings')

    def test_reject_symlink_source_and_target(self):
        with tempfile.TemporaryDirectory() as path:
            root = Path(path)
            original = root/'upstream/stellar_core.cfg';original.parent.mkdir();original.write_bytes(self.SAMPLE)
            linkdir = root/'link';linkdir.mkdir()
            link = linkdir/'stellar_core.cfg';link.symlink_to(original)
            with self.assertRaisesRegex(ValueError, 'regular'):
                module.apply_copy(link, root/'target/stellar_core.cfg')
            with self.assertRaisesRegex(ValueError, 'symlink'):
                module.apply_copy(original, link)


if __name__ == '__main__':
    unittest.main(verbosity=2)
