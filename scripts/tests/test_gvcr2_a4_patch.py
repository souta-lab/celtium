import hashlib
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("gvcr2_patch", Path(__file__).parents[1] / "fix_gvcr2_a4.py")
mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)


def sample_class():
    # Structural class-file fixture for the parser/guard, not executable Minecraft bytecode.
    cp = b""
    for s in ["<init>", "(Lnet/minecraft/world/World;)V", "Code"]:
        b = s.encode(); cp += b"\x01" + struct.pack(">H", len(b)) + b
    code = bytearray(512)
    for offset in (475, 484): code[offset-3:offset+5] = b"\xb4\x00\x01\x03\x13\x00\x02\x53"
    body = struct.pack(">HHI", 2, 3, len(code)) + code + struct.pack(">HH", 0, 0)
    return (b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 52, 4) + cp
            + struct.pack(">HHHHH", 1, 0, 0, 0, 0) + struct.pack(">H", 1)
            + struct.pack(">HHHHHI", 1, 1, 2, 1, 3, len(body)) + body + struct.pack(">H", 0))


class A4PatchTests(unittest.TestCase):
    def test_only_two_code_bytes_change_and_archive_payloads_are_preserved(self):
        data = sample_class(); digest = hashlib.sha256(data).hexdigest()
        with tempfile.TemporaryDirectory() as d:
            source, target = Path(d)/"original.zip", Path(d)/"fixed.jar"
            with zipfile.ZipFile(source, "w") as z:
                z.writestr(mod.CLASS, data); z.writestr("assets/keep.bin", b"unchanged")
            original = source.read_bytes()
            with patch.object(mod, "ORIGINAL_CLASS_SHA256", digest): mod.patch_archive(source, target)
            self.assertEqual(original, source.read_bytes())
            with zipfile.ZipFile(target) as z:
                changed = z.read(mod.CLASS); self.assertEqual(b"unchanged", z.read("assets/keep.bin"))
            start, _ = mod.constructor_code_start(data)
            self.assertEqual([start+475, start+484], [i for i,(a,b) in enumerate(zip(data,changed)) if a!=b])
            self.assertEqual(0x1c, changed[start+475]); self.assertEqual(len(data),len(changed))

    def test_unknown_or_already_patched_class_is_refused(self):
        with self.assertRaisesRegex(ValueError, "unsupported or already patched"):
            mod.patch_class(sample_class())
        data = sample_class()
        with patch.object(mod, "ORIGINAL_CLASS_SHA256", hashlib.sha256(data).hexdigest()):
            changed = mod.patch_class(data)
            with self.assertRaises(ValueError): mod.patch_class(changed)

    def test_original_path_existing_output_and_signature_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            source, target = Path(d)/"original.zip", Path(d)/"fixed.jar"
            with zipfile.ZipFile(source,"w") as z:
                z.writestr(mod.CLASS,sample_class()); z.writestr("META-INF/OWNER.SF",b"signature")
            with self.assertRaisesRegex(ValueError,"output must differ"):mod.patch_archive(source,source)
            target.touch()
            with self.assertRaisesRegex(ValueError,"already exists"):mod.patch_archive(source,target)
            target.unlink()
            with self.assertRaisesRegex(ValueError,"signed archive"):mod.patch_archive(source,target)
            self.assertFalse(target.exists())
