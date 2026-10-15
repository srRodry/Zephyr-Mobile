# Controls touch hub — 2026-10-14

## Implemented scope

The touch Controls area now opens with nine readable destinations: physical key mapping, touch options, touch layout editor, connected pads, vibration/motion, controller test, phone-controller connection information, this phone as a controller, and the complete previous overview under Input status. The hub uses shared green surfaces, static diagonal corners, minimum 88dp rows, one column on narrow screens and two when the content width reaches 640dp. Buttons in its panels use the shared near-square touch presentation. Fixed Back/Home controls remain available while panel content scrolls.

All original section IDs, panel content and callbacks remain intact. The added input-status section reuses the existing overview content, preserving player-slot, touch, vibration and layout summaries. Tool rows call each original goTo directly and do not change the selected internal section. Internal panel navigation uses the shared section history; Home clears it. Existing initialSection deep links still open their target directly. Controller mode bypasses the custom touch frame and keeps its original section list and overview; touch-only status selection is normalized on a mode switch.

Connected-pad count still comes from rememberInputDevices. The compact touch summary displays loading until the layout configuration and global settings are loaded. It reports configured touch enablement, not a guarantee that an overlay is currently visible during a running game (auto-hide and controller rules still apply). No Bluetooth pairing, controller detection, per-game action label, dead-zone setting or input feature is invented from the reference board.

This increment redesigns the Controls landing/navigation frame. The mapping tool and touch editor remain the existing tools; their input capture, bindings, layout persistence, gyro, vibration, lifecycle flush and all update callbacks are unchanged. InputDevices.kt, TouchOptions.kt, KeymapScreen.kt, core/gamepad implementation, defaults, native emulation and audio are not modified.

## Regression coverage and delivery

Six production ControlsScreen Compose regressions use the actual SettingsViewModel/KeymapViewModel and the existing fake guest backend: Touch/Back with global values unchanged; Pads/Motion/Home without global changes; four tool links invoking exactly their original callbacks; direct Touch entry/back; complete Status and Phones access on a 640×320dp landscape display; original controller overview bypass. Empty device fixtures do not claim physical-controller or Bluetooth validation. Existing settings/profile/library/guide/native regressions remain in CI. Workflow results at the commit are authoritative.

Source-only branch increment, no APK marker, APK gate change, signing change or release. APK #71 lacks the new Settings hub, Profiles workspace and Controls hub. These need later packaging and real phone visual/input validation; tests are not device screenshots or compatibility evidence.
