# Green UI batch 2 APK candidate — 2026-10-14

This packaging request starts from eb8444a (UI #97: 194 JVM tests, zero failures/errors/skips, Compose/resources and selected Android ARM64 integration approved). It includes the preceding green library/game/guide plus the Settings category hub, local Profiles selector/identity workspace and Controls hub. Related implementation records are zephyr-settings-hub-2026-10-14.md, zephyr-profiles-workspace-2026-10-14.md and zephyr-controls-hub-2026-10-14.md.

The packaging commit changes this record and one descriptive workflow comment only. APK gate, signing policy, package identity, application defaults, native/audio implementation and release conditions remain unchanged. The requested output is an isolated experimental xendroid.compose.zephyr.test ARM64 APK, without official release. The commit's workflows/artifact metadata are authoritative for build, signature and verification results; success is not asserted before completion.

Device checks: start and rotate; Settings categories -> original panel -> Back/Home; global search/clear and direct Data/backup entry; Profiles inspection must not activate a different player, explicit activation/edit/details must target the chosen identity; Controls -> Touch/Pads/Motion/Status/Phones -> Back/Home and original external mapping/editor/test/phone tool routes. Preserve real save/profile relationships. Physical input, real photo picking, game-surface behavior and visual/accessibility checks on the phone remain necessary. No gameplay/FPS/audio improvement or compatibility verdict is claimed.

If a temporary certificate is used, compare its SHA-256 with the installed APK before attempting an update. Differently signed builds cannot update each other directly. Before any uninstall, export saves together with their associated profiles and export settings separately; settings-only backup does not include all saves/profiles/drivers. Confirm the required exports are readable and outside the app's private storage before removing an installation.


## Interface 2.0 geometry and density candidate — 2026-10-15

This packaging request starts from `4a022805` after Zephyr UI checks #147 completed successfully. The candidate includes the simplified phone library hierarchy, enlarged embedded artwork, shared Zephyr structural/action/segmented shape tokens, unified Settings and Controls hub rows, tighter Profiles workspace spacing, denser Game Sheet rhythm and standardized generic section navigation touch geometry.

The native emulator core, audio path, gameplay defaults and compatibility data are not changed by this packaging request. The purpose of this APK is device-side visual validation of Interface 2.0 on Android: geometry consistency, spacing, hierarchy, touch targets, portrait/landscape behavior and regression-free navigation. Automated CI success does not substitute for phone visual validation.
