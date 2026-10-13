#!/usr/bin/env python3
"""Integration tests: real keytool keys, invalid credentials and secret hygiene."""
import base64
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('validate-signing.sh')


class SigningPreflightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.key = Path(cls.tmp.name) / 'test.p12'
        cls.password = 'test-only-password-92847'
        env = {**os.environ, 'TEST_PASS': cls.password}
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(cls.key), '-storetype', 'PKCS12',
                        '-alias', 'test', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                        '-storepass:env', 'TEST_PASS', '-dname', 'CN=preflight test'],
                       env=env, check=True, capture_output=True)
        cls.encoded = base64.b64encode(cls.key.read_bytes()).decode()
        cert = Path(cls.tmp.name) / 'test.der'
        trusted = Path(cls.tmp.name) / 'trusted.p12'
        subprocess.run(['keytool', '-exportcert', '-keystore', str(cls.key), '-alias', 'test',
                        '-storepass:env', 'TEST_PASS', '-file', str(cert)], env=env, check=True, capture_output=True)
        subprocess.run(['keytool', '-importcert', '-noprompt', '-keystore', str(trusted), '-alias', 'test',
                        '-storepass:env', 'TEST_PASS', '-file', str(cert)], env=env, check=True, capture_output=True)
        cls.trusted = base64.b64encode(trusted.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def check_case(self, updates, success):
        env = {k: v for k, v in os.environ.items() if not k.startswith(('TEST_', 'RELEASE_'))}
        env.update({'VARIANT': 'test', **updates})
        result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        self.assertNotIn(self.password, result.stdout + result.stderr)
        self.assertNotIn(self.encoded, result.stdout + result.stderr)
        return result.stdout

    def valid(self):
        return {'TEST_KEYSTORE': self.encoded, 'TEST_ALIAS': 'test', 'TEST_PASSWORD': self.password}

    def test_persistent_private_key(self):
        self.assertIn('verified before compilation', self.check_case(self.valid(), True))

    def test_missing_key_is_explicitly_ephemeral(self):
        self.assertIn('ephemeral', self.check_case({}, True))

    def test_partial_configuration(self):
        self.check_case({'TEST_ALIAS': 'test'}, False)

    def test_bad_base64(self):
        self.check_case({**self.valid(), 'TEST_KEYSTORE': 'invalid!'}, False)

    def test_certificate_without_private_key(self):
        self.check_case({**self.valid(), 'TEST_KEYSTORE': self.trusted}, False)

    def test_wrong_password(self):
        self.check_case({**self.valid(), 'TEST_PASSWORD': 'wrong-password'}, False)

    def test_wrong_alias(self):
        self.check_case({**self.valid(), 'TEST_ALIAS': 'missing'}, False)

    def test_release_fallback(self):
        self.check_case({'RELEASE_KEYSTORE': self.encoded, 'RELEASE_ALIAS': 'test',
                         'RELEASE_PASSWORD': self.password}, True)

    def test_bad_release_private_key_password(self):
        self.check_case({'RELEASE_KEYSTORE': self.encoded, 'RELEASE_ALIAS': 'test',
                         'RELEASE_PASSWORD': self.password, 'RELEASE_KEY_PASSWORD': 'incorrect'}, False)


if __name__ == '__main__':
    unittest.main()
