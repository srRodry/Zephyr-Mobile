# Local profile workspace — 2026-10-14

## Presentation and identity

The touch profile list now uses a horizontal, keyed avatar selector and a single inspected-profile identity panel. Static corner cuts, green shared surfaces, restrained lime selection, near-square actions and 48dp action targets follow the accepted green boards. Long names can wrap; the selector scrolls horizontally and the outer existing screen remains vertically scrollable on short displays.

Inspecting a profile changes only rememberSaveable UI selection. It never invokes setActive or changes P1–P4 assignments. Activation remains an explicit existing callback, shown only for an inactive profile. Actual sign-in status is shown separately from the selector's radio selection. Every edit, save, details and trash callback captures the inspected ProfileEntry/XUID. Trash still goes through the existing measured confirmation/reversible trash flow. If an inspected profile disappears, the presentation falls back to the actual active profile or first available profile; an empty list has no identity actions.

Language/region and save summaries use existing model values. A missing summary is reported as unavailable, not zero saves, and save navigation stays disabled until a nonempty summary exists. No cloud, play-time aggregate, per-profile theme, automatic backup, or synchronized timestamp is invented. Existing avatar picking/validation/storage, profile forms, P1–P4 screen, controller presentation, trash/restore/purge logic, identifiers and native profile operations remain intact. The form's example gamertag is now ZephyrPlayer; no existing gamertag is renamed.

## Validation

Six Compose regressions exercise the production TouchProfiles component: preview vs explicit activation, exact inspected-ID routing for edit/details/trash, removed selection and empty fallback, no-active inspection, 12 profiles in a 640×320dp landscape viewport, and disabled save action for unavailable information. The full prior settings/transfer/library/guide/navigation suite remains in CI. These tests do not emulate a real photo picker, native profile writes, or phone behavior. Workflow results at the commit are the validation authority.

Source-only increment on feature/zephyr-mobile-branding. No APK request marker, APK workflow change, audio/default/native/signing change or release. APK #71 does not contain this workspace or the newer Settings hub; a later packaging and phone visual validation are required.
