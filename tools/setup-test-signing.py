#!/usr/bin/env python3
"""Create a local test key backup; optionally provision GitHub Actions secrets."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

REPO = 'srRodry/Zephyr-Mobile'
NAMES = {'TEST_KEYSTORE_BASE64', 'TEST_KEY_ALIAS', 'TEST_KEYSTORE_PASSWORD'}


def run(args, *, data=None, env=None):
    result = subprocess.run(args, input=data, env=env, capture_output=True)
    if result.returncode:
        # Do not print command output: it could contain secret material.
        raise RuntimeError(f'{args[0]} failed; no credentials were printed. Local backup is retained.')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup_dir', type=Path, help='New private directory outside any Git checkout')
    parser.add_argument('--upload', action='store_true', help='Send secrets using an authenticated GitHub CLI session')
    args = parser.parse_args()
    os.umask(0o077)
    if not shutil.which('keytool'):
        raise RuntimeError('Install a JDK providing keytool first.')
    target = args.backup_dir.expanduser().resolve()
    if any((parent / '.git').exists() for parent in [target, *target.parents]):
        raise RuntimeError('The backup must be outside a Git checkout.')
    if target.exists():
        raise RuntimeError('Backup directory already exists. Refusing to overwrite a signing identity.')
    if args.upload:
        if not shutil.which('gh'):
            raise RuntimeError('Install GitHub CLI and authenticate with gh auth login first.')
        names = json.loads(run(['gh', 'secret', 'list', '--repo', REPO, '--json', 'name']))
        if NAMES.intersection(item['name'] for item in names):
            raise RuntimeError('Test signing secrets already exist. Refusing automatic key rotation.')
    target.mkdir(parents=True, mode=0o700)
    password = secrets.token_urlsafe(36)
    alias = 'zephyr-test'
    (target / 'password.txt').write_text(password, encoding='utf-8')
    (target / 'alias.txt').write_text(alias, encoding='utf-8')
    env = {**os.environ, 'ZEPHYR_STORE_PASSWORD': password}
    run(['keytool', '-genkeypair', '-keystore', str(target / 'test-signing.p12'),
         '-storetype', 'PKCS12', '-alias', alias, '-keyalg', 'RSA', '-keysize', '3072',
         '-validity', '10000', '-storepass:env', 'ZEPHYR_STORE_PASSWORD',
         '-dname', 'CN=Zephyr-Mobile experimental test builds'], env=env)
    run(['keytool', '-exportcert', '-keystore', str(target / 'test-signing.p12'),
         '-alias', alias, '-storepass:env', 'ZEPHYR_STORE_PASSWORD',
         '-file', str(target / 'certificate.der')], env=env)
    if args.upload:
        values = {
            'TEST_KEYSTORE_BASE64': base64.b64encode((target / 'test-signing.p12').read_bytes()),
            'TEST_KEY_ALIAS': alias.encode(),
            'TEST_KEYSTORE_PASSWORD': password.encode(),
        }
        for name, value in values.items():
            run(['gh', 'secret', 'set', name, '--repo', REPO], data=value)
        print('All three test signing secrets configured.')
    print(f'Private backup created at {target}. Keep it encrypted and retain the password in your password manager.')
    print('No production signing key or release was changed.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError) as exc:
        print(f'Error: {exc}', file=sys.stderr)
        sys.exit(1)
