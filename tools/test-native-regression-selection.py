#!/usr/bin/env python3
"""Exercise real compilation-database planning and invocation, without an NDK."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('compile-native-regressions.py')
spec = importlib.util.spec_from_file_location('native_compile', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class SelectionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='zephyr-native-plan-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.directory = self.root / 'build directory'
        self.directory.mkdir()
        self.db = self.directory / 'compile_commands.json'
        self.entries = [dict(file=str(self.root / s), directory=str(self.directory),
            arguments=['clang++', '--target=aarch64-none-linux-android29', '-O3',
                       '-c', str(self.root / s), '-o', f'CMakeFiles/native.dir/{i}.o'])
            for i, s in enumerate(module.SOURCES)]
    def save(self): self.db.write_text(json.dumps(self.entries))
    def test_selects_only_exact_objects_and_handles_quoted_command(self):
        import shlex
        self.entries.append(dict(file='/unrelated/a64_sequences.cc'))
        self.entries[0]['command'] = shlex.join(self.entries[0].pop('arguments'))
        self.save()
        directory, targets = module.plan(self.db)
        self.assertEqual(directory, self.directory)
        self.assertEqual(targets, [f'CMakeFiles/native.dir/{i}.o' for i in range(3)])
    def test_rejects_missing_ambiguous_and_non_android_entries(self):
        for mode in ('missing', 'ambiguous', 'host'):
            original = json.loads(json.dumps(self.entries))
            if mode == 'missing': self.entries.pop()
            elif mode == 'ambiguous': self.entries.append(self.entries[0])
            else: self.entries[0]['arguments'][1] = '--target=x86_64-linux-gnu'
            self.save()
            with self.assertRaises(ValueError): module.plan(self.db)
            self.entries = original
    def test_rejects_link_outputs_outside_directory_and_mixed_builds(self):
        for mode in ('link', 'outside', 'mixed'):
            original = json.loads(json.dumps(self.entries))
            if mode == 'link': self.entries[0]['arguments'][-1] = 'libe.so'
            elif mode == 'outside': self.entries[0]['arguments'][-1] = '../outside.o'
            else: self.entries[0]['directory'] = str(self.root)
            self.save()
            with self.assertRaises(ValueError): module.plan(self.db)
            self.entries = original
    def test_invokes_configured_ninja_and_propagates_failure(self):
        self.save()
        ninja = self.root / 'fake ninja'
        record = self.root / 'invocation.json'
        ninja.write_text('#!' + sys.executable + '\nimport sys,json,pathlib\n' +
                         'pathlib.Path(' + repr(str(record)) + ').write_text(json.dumps(sys.argv[1:]))\n' +
                         'raise SystemExit(7)\n')
        ninja.chmod(0o700)
        (self.directory / 'CMakeCache.txt').write_text('CMAKE_MAKE_PROGRAM:FILEPATH=' + str(ninja) + '\n')
        result = subprocess.run([sys.executable, str(SCRIPT), '--database', str(self.db)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(json.loads(record.read_text()), ['-C', str(self.directory), '-j', '2',
                         *[f'CMakeFiles/native.dir/{i}.o' for i in range(3)]])

if __name__ == '__main__': unittest.main()
