# UI 2.0: in-game touch menu

Base: b6caec1eb53f3375f56bf55261976c40e9be7bf5, feature/zephyr-mobile-branding.

The touch menu now uses a silver game/state header, solid blue selected categories (#155ED2, white labels: 5.89:1 contrast) and a persistent Continue/Exit footer in portrait and landscape. Landscape uses up to 720 dp / 78% of the viewport, leaving the running surface visible. Saved-change counts still link to Session: the existing saved line in portrait and a wrapping footer action in landscape. Below 400 dp viewport height, Exit becomes a labeled accessible icon and saved changes show their count with a save icon/full accessibility description, preserving space for the scrolling options.

Category tabs and choice chips have minimum 48 dp touch heights; touch menu rows have minimum 56 dp, and sliders receive 48 dp vertical space. Controller geometry, hardware navigation state and selected-row bring-into-view behavior remain unchanged. The existing HUD preview now explicitly labels its sample figures on touch screens. The actual status line still uses only host-provided measurements.

No InGameMenuModel/State/action API changes, no new dialogs/windows, no pause/resume/quit policy changes, no default/live-setting/native/audio changes. Exit still enters the existing confirmation flow, and adjustments retain their existing callbacks and persistence. The input interception and edge gesture are preserved.

Validation: resource XML parsing and localization parity; existing Compose compilation, menu-state/log-picker and full CI regressions must pass before packaging. This commit does not request an APK.

Remaining device checks: small landscape heights, large fonts and long game names, scroll access to all options with saved-change counts visible, tab/choice touch targets, Back/Guide and LB/RB navigation, quit confirmation, log picker, and paused vs running labels with pause-on-open disabled. CI does not prove visual layout or game behavior on the phone.
