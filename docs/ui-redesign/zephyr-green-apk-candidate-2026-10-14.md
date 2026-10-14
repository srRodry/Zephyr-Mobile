# Green interface APK candidate — 2026-10-14

This build packages the green UI increment already validated at 65a4a3d (UI #92, 173 JVM tests, zero failures/errors/skips, Compose/resource compilation and selected ARM64 integration passed). It includes blade navigation, the featured touch library and Recent filter, real-cover-aware game overview, grouped game settings/data navigation and the diagonal touch guide. Small embedded icons remain contained; generated proposal artwork is not bundled. Existing controller paths and launch/exit callbacks are preserved.

The workflow gate is unchanged: this explicit build commit requests an experimental `.zephyr.test` ARM64 APK, without official release. No native/audio/performance defaults or production signing are changed by this packaging increment. Consult this commit's UI/APK workflows and artifact metadata for the actual build and verification results; this document does not assert success before those runs finish.

If CI reports a temporary signing key, direct updates over a differently signed earlier test APK will fail. Before any uninstall, export saves with associated profiles and export settings; settings-only backup is not a complete save/profile/driver backup. Never delete the existing installation before verifying the needed exports are readable and available outside its private app storage.

Phone validation remains necessary: start the app, portrait/landscape library selection, open the correct game sheet, Overview/Settings/Data and Launch With, open the in-game guide, Controls/Settings -> guide, Resume, and Exit -> cancel/confirm. Physical controller and actual game-surface transparency also remain device tests. No FPS or game compatibility improvement is claimed.
