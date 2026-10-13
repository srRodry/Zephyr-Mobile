# Zephyr-Mobile: stability checkpoint, 2026-10-13

## Verified baseline

Development head before this checkpoint: `a994925ceffea152d33a0b8a06e7e5a298351958`.
Main: `1066f8fd6cab89302b364b341ba8687781d47182`; development was 44 commits ahead, zero behind.
GitHub reported the repository as public at inspection, contrary to the previous handoff.

- UI checks #37: success, https://github.com/srRodry/Zephyr-Mobile/actions/runs/38444248436
- Android APK #23: success, https://github.com/srRodry/Zephyr-Mobile/actions/runs/38444248326
- Artifact `Zephyr-Mobile_test_a994925c`: available, 51,835,746 bytes (ZIP), expires 2026-10-27.
- Build settings choose `xendroid.compose.zephyr.test`; packaging check passed. Release job was skipped.
- Test signing secrets were absent. Certificate SHA-256 from signing verification:
  `867e6646299ca279247d08111140232b3541728cac3bb973f5add76c7026e0dc`.
  This is an ephemeral signer, not a persistent test identity.

## SDL3 wrapper investigation

File: `emulator-core/src/main/cpp/xenia/third_party/SDL3/android-project/gradle/wrapper/gradle-wrapper.jar`.

| Version | Bytes | SHA-256 |
| --- | ---: | --- |
| Main, manifest identifies Gradle 3.4 | 54,213 | `2c23278a62dc9f96ab11bee897d9f53e6479d6419135e4560ce822a6431df5e5` |
| Development, official Gradle 8.12 wrapper | 43,583 | `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |

The change originated in `afec0241be1639912ebaa7cfc7436788ddf0d306`, replacing a legacy wrapper rejected by CI validation. The sample's existing properties request Gradle 8.12.
The development blob SHA `a4b76b9530d66f5e68d973ea569d8e19de379189` matches the wrapper retrieved independently from `gradle/gradle` at tag `v8.12.0`: byte-for-byte identity under Git's blob hash, also locally SHA-256 checked. No SDL3 file was edited in this checkpoint.

## Persistent test signing setup

Prerequisites on your own trusted computer: Python 3, JDK 17+, GitHub CLI authenticated with permission to manage repository Actions secrets. The connector used for this audit cannot write secrets.

From the development checkout, run (choose a private backup location outside Git):

```sh
python3 tools/setup-test-signing.py "$HOME/zephyr-test-signing" --upload
```

The script refuses an existing backup directory or existing test signing secrets. It creates a 3072-bit RSA PKCS12 private key, a random password, alias and public certificate. Secrets go through stdin to GitHub CLI; their contents are never printed or placed in command arguments. Local permissions are 0700/0600. File permissions do not replace encrypted backup: retain the key in encrypted storage and the password in a password manager. Never attach these files to a chat or commit them.

Without `--upload`, it only creates the backup. If any secret upload fails, retain that same key; do not create another identity. Complete the missing uploads from the retained backup using GitHub CLI stdin or the GitHub secrets UI. Verify all three secret names together before the next build:

- `TEST_KEYSTORE_BASE64`: Base64 of `test-signing.p12`.
- `TEST_KEY_ALIAS`: contents of `alias.txt`.
- `TEST_KEYSTORE_PASSWORD`: contents of `password.txt`.

The preflight now opens the keystore and decrypts its selected private key before native compilation. It rejects invalid Base64, wrong aliases/passwords and certificate-only stores. Missing keys still produce explicitly ephemeral experimental builds; stable signing is pending until these secrets are configured.

The APK artifact includes a public `.signing.txt` report. Compare the certificate SHA-256 across two successive builds; the APK hashes themselves should differ. On the phone, install build A, record profiles/settings/library, then install build B as an update and verify preservation. APKs already installed with ephemeral keys may require uninstalling first; export app data before doing so. Matching certificates alone do not prove device-side data retention.

## Branding audit and device checks

The rail uses `zephyr_launcher_foreground` and the localized app name. The About description now names Zephyr-Mobile and explicitly credits XenDroid+/Xenia. Profile default `XenDroid`, existing standard folders and screenshot paths retain their current names because their UI descriptions reflect actual behavior. Technical package IDs, protocols, release parsing and original credits remain intact.

Remaining device evidence: rail appearance, physical-controller navigation, game start/exit, permissions, profile persistence and failure recovery. No game compatibility was established by this audit.
