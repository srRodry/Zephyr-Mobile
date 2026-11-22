#!/usr/bin/env python3
"""Preserve an address-to-symbol map for the exact libe.so packaged in the APK."""
import argparse
import gzip
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ['ANDROID_SDK_ROOT'])
toolchain = sdk/'ndk'/os.environ['ANDROID_NDK_VERSION']/'toolchains/llvm/prebuilt/linux-x86_64/bin'

def build_id(path):
    notes = subprocess.check_output([str(toolchain/'llvm-readelf'), '--notes', str(path)], text=True)
    match = re.search(r'Build ID:\s*([0-9a-fA-F]+)', notes)
    if not match:
        raise RuntimeError(f'Missing ELF Build ID: {path}')
    return match[1].lower()

with tempfile.TemporaryDirectory() as d:
    packaged = Path(d)/'libe.so'
    with zipfile.ZipFile(args.apk) as apk:
        packaged.write_bytes(apk.read('lib/arm64-v8a/libe.so'))
    identity = build_id(packaged)
    matches = [p for variant in ('Release', 'RelWithDebInfo')
               for p in (root/'emulator-core/build/intermediates/cxx'/variant).rglob('libe.so')
               if build_id(p) == identity]
    if not matches:
        raise RuntimeError('No unstripped release libe.so matches the APK Build ID')
    native = matches[0]
    symbols = subprocess.check_output([str(toolchain/'llvm-nm'), '--numeric-sort', '--demangle', '--defined-only', str(native)])
    if len(symbols.splitlines()) < 100:
        raise RuntimeError('Native symbol map is missing or stripped')
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out/'libe.nm.txt.gz').write_bytes(gzip.compress(symbols, mtime=0))
    (args.out/'identity.json').write_text(json.dumps({
        'build_id': identity,
        'commit': os.environ.get('GITHUB_SHA'),
        'apk': args.apk.name,
        'map_format': 'llvm-nm --numeric-sort --demangle --defined-only; ELF relative addresses',
    }, indent=2)+'\n')
    print(f'Preserved {len(symbols.splitlines())} symbols; APK ELF Build ID {identity}')
