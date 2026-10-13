# Zephyr-Mobile: phone-only test checkpoint

## Scope

Development branch only: `feature/zephyr-mobile-branding`. Persistent signing remains pending repository secret configuration. No production key or release is changed. Tests on a phone remain necessary; automated screen tests use a simulated core and do not establish game compatibility.

## Settings transfer fix

Both data-bundle interfaces now call the pending-settings save callback before export. The current settings screen uses a transfer-specific flush that propagates a save failure. Previously lifecycle flush reported the error but returned normally, allowing a transfer to proceed with old values. Lifecycle error handling is preserved; transfer failures now stop before opening the output file or importing a bundle.

Four new screen regression cases exercise the actual settings repository and view model with the existing fake-core test harness:

- New card exports a pending edit, not the previous disk value.
- Legacy section exports that same pending edit.
- A settings-write failure prevents creating a misleading export ZIP.
- Cancelling the document picker does not flush or export anything.

CI also selects the existing data-bundle, saves/backups, profile-slot, atomic-config-write and updater unit tests alongside library and controller tests. This validates serialization, integrity and persistence logic; it does not substitute for device storage permissions or real game saves.

## Test on the phone

1. Note the installed commit/build in About. Keep the current installation until you have backed up the data you need.
2. Settings → Data: export the settings ZIP to a location outside the app's private directory, such as Downloads. Reopen the ZIP through the import preview and confirm it is recognized; cancel the import if no changes are needed.
3. Settings bundles contain global/per-game settings, touch controls, favorites, collections, compatibility notes and library sort. They do **not** contain saves, profiles, game files, drivers, selected storage paths or session history. Use the separate save-backup workflow for saves/profiles, including profiles when offered. Verify the resulting backup before removing any installation.
4. When changing builds, a different ephemeral signer may make Android refuse the update. Do not assume that refusal means the APK is corrupt. Do not uninstall until data is backed up and verified; the signing mismatch is unresolved until persistent secrets are configured.
5. In a build containing the transfer fix, change a setting, export, change it again, open the exported ZIP's import preview and confirm that the first edited value would be restored. Apply only if you want that restoration.
6. Check the Zephyr Z in the navigation rail, onboarding, About and updates; check portrait/landscape navigation and return from Android's file picker.
7. Close/reopen the app and verify settings, favorites and collections persist. Record permission errors, blank states, crashes and the installed commit so results can be traced to a build.

The updater intentionally does not query the release feed for the `.zephyr.test` package. Experimental APKs come from GitHub Actions artifacts; the in-app stable/preview channels are not a distribution path for these builds.
