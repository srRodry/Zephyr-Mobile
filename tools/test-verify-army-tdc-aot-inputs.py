#!/usr/bin/env python3
"""Test AOT-version preflight with synthetic, non-copyrighted dummy files."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-army-tdc-aot-inputs.py")
spec = importlib.util.spec_from_file_location("ao2dc_aot", SCRIPT)
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class AotInputsTests(unittest.TestCase):
    def test_known_upstream_retail_checksums_are_not_placeholders(self):
        self.assertEqual(tool.TITLE_ID, "454109AB")
        self.assertEqual(tool.MEDIA_ID, "60F8EF51")
        self.assertEqual(tool.KNOWN_SHA1["default.xex"],
                         "b7f987b19a6f392d6c74b65444bd4b3e4eb8d924")
        self.assertEqual(len(tool.KNOWN_SHA1), 3)
        self.assertTrue(all(len(x) == 40 for x in tool.KNOWN_SHA1.values()))

    def test_stream_hash_matching_case_and_mismatch(self):
        with tempfile.TemporaryDirectory(prefix="zephyr-aot-check-") as directory:
            path = Path(directory)
            (path / "Default.XEX").write_bytes(b"synthetic launcher")
            (path / "ao4.main.xex").write_bytes(b"synthetic main")
            expected = {
                "default.xex": hashlib.sha1(b"synthetic launcher").hexdigest(),
                "AO4.Main.xex": hashlib.sha1(b"synthetic main").hexdigest(),
            }
            result = tool.verify(path, expected)
            self.assertTrue(result["valid_for_known_aot_source"])
            (path / "ao4.main.xex").write_bytes(b"changed")
            result = tool.verify(path, expected)
            self.assertFalse(result["valid_for_known_aot_source"])
            self.assertEqual(result["files"][1]["status"], "mismatch")
            (path / "ao4.main.xex").unlink()
            result = tool.verify(path, expected)
            self.assertEqual(result["files"][1]["status"], "missing_or_ambiguous")

    def test_rejects_nondirectory_and_never_writes_game_files(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "f.txt").write_bytes(b"owned")
            with self.assertRaises(ValueError):
                tool.verify(path / "missing")
            self.assertFalse(tool.verify(path)["valid_for_known_aot_source"])
            self.assertEqual((path / "f.txt").read_bytes(), b"owned")


if __name__ == "__main__":
    unittest.main()
