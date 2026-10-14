# UI 2.0: profile and settings hierarchy cleanup

Base: 7e238bfc3101cba8d801c9f84879343177474901. Branch: feature/zephyr-mobile-branding.

The device recording 1000397936.mp4 (35.789622 s) confirms the compact library, touch profile/settings screens and the in-game menu opening with category navigation. Attached title-screen imagery shows Dante's Inferno reaching its title screen, not complete gameplay compatibility. No FPS improvement is established by these files.

The profile screen repeats Profiles above its editorial title and repeats Create in the header and a large empty card. XUID appears prominently on profile cards. The settings summary also repeats its section heading before the editorial title.

## Change

- Touch list/summary sections suppress the redundant section heading; tabs and the top-level screen title remain. Controller section headings remain as before.
- Touch profiles use the fixed header Create button (48 dp target) as the single creation action. The duplicate large card is removed. The loaded empty list has a localized empty state, with creation still accessible in the header. Error/loading behavior is retained.
- Touch profile cards replace the always-visible XUID with a Details action. Details resolves the selected XUID against the current profile list and shows the identifier, language/region, saves and current active/player status, with Copy identifier and Close actions. A deleted profile closes details after the loaded list refreshes.
- Blank gamertags retain the existing XUID name fallback; no identifier or profile is rewritten. Controller cards/menus, creation, rename, activate, P1–P4 assignment, save ownership, trash/restore and purge confirmations are unchanged.

No native, audio, performance, signing, persisted settings or workflow changes. Floating overlays visible in the recording are not modified: these images alone do not establish their ownership.

## Validation

English/Brazilian Portuguese XML must parse with matching unique keys; Compose/resources and the existing 149 selected JVM regressions must pass in CI. This commit requests no APK build. Large fonts, multiple/empty profiles, Create from all tabs, Details/copy/dismiss, restore/delete transitions and landscape require Android visual/interaction validation; CI is not evidence for those UI interactions on the phone.
