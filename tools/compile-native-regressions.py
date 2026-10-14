#!/usr/bin/env python3
"""Build real Android release regression objects, with CMake/NDK flags.

Configure :emulator-core:configureCMakeRelease[arm64-v8a] first. Ninja handles
required generated-header dependencies. This neither links libe.so nor packages
an APK. It checks translation-unit integration, not runtime or link correctness.
"""
import argparse
import json
from pathlib import Path
import shlex
import re
import subprocess

SOURCES = (
    'emulator-core/src/main/cpp/xenia/src/xenia/cpu/backend/a64/a64_sequences.cc',
    'emulator-core/src/main/cpp/xenia/src/xenia/cpu/ppc/ppc_translator.cc',
    'emulator-core/src/main/cpp/emulator_xendroid.cpp',
    'emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc',
    'emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc',
    'emulator-core/src/main/cpp/xenia/src/xenia/ui/vulkan/vulkan_gpu_completion_timeline.cc',
)

def plan(database):
    entries = json.loads(Path(database).read_text())
    directory = None
    targets = []
    for source in SOURCES:
        matches = [e for e in entries if Path(e['file']).as_posix().endswith('/' + source)]
        if len(matches) != 1:
            raise ValueError(f'{source}: expected one compile entry, found {len(matches)}')
        entry = matches[0]
        args = entry.get('arguments') or shlex.split(entry['command'])
        target_flags = [a.split('=', 1)[1] for a in args if a.startswith('--target=')]
        target_flags += [args[i+1] for i, a in enumerate(args[:-1]) if a in ('-target', '--target')]
        if not any(t.startswith('aarch64') and 'android' in t for t in target_flags):
            raise ValueError(f'{source}: compiler target is not Android ARM64')
        here = Path(entry['directory']).resolve()
        if directory is not None and here != directory:
            raise ValueError('Selected sources belong to different Ninja build directories')
        directory = here
        outputs = [args[i+1] for i, a in enumerate(args[:-1]) if a == '-o']
        if len(outputs) != 1:
            raise ValueError(f'{source}: missing or ambiguous object output')
        output = (here / outputs[0]).resolve()
        try:
            target = output.relative_to(here).as_posix()
        except ValueError:
            raise ValueError(f'{source}: object is outside the build directory') from None
        if not target.endswith('.o') or target.startswith('-'):
            raise ValueError(f'{source}: expected an object target, got {target}')
        targets.append(target)
    if len(set(targets)) != len(SOURCES):
        raise ValueError('Sources resolve to duplicate object targets')
    return directory, targets

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path)
    parser.add_argument('--jobs', type=int, default=2)
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error('--jobs must be positive')
    root = Path(__file__).resolve().parents[1]
    database = args.database
    if database is None:
        databases = list(root.glob('emulator-core/.cxx/Release/*/arm64-v8a/compile_commands.json'))
        if len(databases) != 1:
            parser.error('Expected exactly one Release ARM64 compilation database; configure first or pass --database')
        database = databases[0]
    try:
        directory, targets = plan(database)
        cache = (directory / 'CMakeCache.txt').read_text()
        programs = [line.split('=', 1)[1] for line in cache.splitlines()
                    if re.match(r'^CMAKE_MAKE_PROGRAM:[A-Z_]+=', line)]
        if len(programs) != 1 or not Path(programs[0]).is_file():
            raise ValueError(f'Configured Ninja executable is missing: {programs!r}')
    except (ValueError, OSError, KeyError) as error:
        parser.error(str(error))
    print('Android ARM64 release objects: ' + ', '.join(targets), flush=True)
    subprocess.run([programs[0], '-C', str(directory), '-j', str(args.jobs), *targets], check=True)

if __name__ == '__main__':
    main()
