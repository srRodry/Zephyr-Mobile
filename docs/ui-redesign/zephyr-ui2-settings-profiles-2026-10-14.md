# UI 2.0: settings and profiles

Base: ff2438c97046580e1258ebc5881e6996e3e9c0a4 on feature/zephyr-mobile-branding.

The touch settings summary now has a wrapping editorial heading and a silver navigation panel for the existing interface, data/backup and diagnostics sections. Search still spans the existing tabs. Changed settings retain both current/default values and reset actions; on narrow screens the action goes below the text. No values or settings levels are changed.

Touch profiles now feature the real active profile in a silver surface before the other profile cards and Create card. The featured profile retains edit, saves, trash, language/region and XUID. No profile is renamed or recreated, and XUID, save ownership, P1–P4 assignment, trash/restore and avatar policy are unchanged. Other profiles retain Activate, Edit, Saves and Trash. Active cards wrap names; profile action targets are at least 48 dp. Controller layouts retain their existing presentation.

The silver surface now defines light pressed/secondary backgrounds as well as dark text, so its interactive states remain readable. This also applies to the previous game overview shortcuts.

Localized resources are supplied in English and Brazilian Portuguese. CI must compile Compose and run the existing regression suite before packaging. No APK build is requested by this commit.

Device visual validation remains pending: large fonts, long gamertags, profile switching, missing avatars, empty/error/loading states, settings search, keyboard/controller focus and portrait/landscape. Source review and CI do not replace these checks. Performance and audio work remain paused.
