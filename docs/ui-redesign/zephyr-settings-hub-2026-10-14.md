# Touch settings hub — 2026-10-14

## Implemented

The new touch entry point presents six categories: Graphics, Audio, Input, Storage, System and Advanced. Graphics groups existing Image, Driver and Performance panels. Audio/Input/Storage lead directly to their existing sections. System groups interface, language, console system, optional application updates and About. Advanced keeps compatibility options, debug, diagnostics, community status and the original Summary (changed settings, game overrides, driver summary and TOML access).

Every original section ID and content/callback remains intact. Only touch adds synthetic home/category destinations. The shared section history handles category -> panel -> Back; the Home affordance clears that history. Existing initialSection deep links bypass the hub. Controller mode retains the original section list and Summary, with synthetic touch destinations normalized when switching mode. Loading/error/retry, flush-on-pause, transfer pre-flush/post-import and failure dialogs are unchanged.

The global search uses the existing SettingsPanelState and XdSettingsPanel search/filter/level behavior. Clearing it clears focus and restores the category cards. No search or navigation action changes setting values. Future real sections not explicitly mapped remain discoverable under Advanced; absent optional destinations (such as app updates without a configured repository) are omitted.

The layout uses shared green tokens, static corner cuts, 80dp minimum category targets, 48dp navigation/search targets, one column on narrow screens and two when at least 640dp of card width is available. Content remains scrollable on short displays. Redundant section headings matching the fixed header are omitted.

## Regression coverage

Three routing tests check exact-once reachability of original setting/app sections, absence of dead update links, and fallback reachability for future sections. Six real SettingsScreen/SettingsViewModel Compose tests cover portrait Graphics/Image/Back without value changes, landscape System/Language/Home, direct Data entry, search/clear without value changes, short landscape Advanced/Summary and controller Summary. Existing transfer/failure recovery, game/library, guide, palette and native regression checks are retained. The commit's workflow is authoritative for results; no device validation is implied.

This is a UI presentation change. SettingCatalog, SettingsSchema, defaults, native emulation, audio implementation, signing, APK workflow gate and releases are not changed. APK #71 at dad314a3 remains the preceding installable build; this increment requires a later packaging step to reach a phone.
