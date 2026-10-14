# Touch game guide — 2026-10-14

## Behavior

Touch mode now opens a short guide over the existing game surface. Its four actions route through existing callbacks: Resume -> RESUME; Controls -> CONTROLS category; Settings -> GRAPHICS category (all existing categories stay available); Exit -> QUIT with the original confirmation. A new header Back affordance returns from tools to the guide. Existing OS/controller Back handling remains with the host; this change does not intercept those events.

Landscape uses a static diagonal blade with a bounded 240–320dp target width, clipped to the available viewport when smaller. The game surface remains visible outside it under the existing scrim. Content scrolls on short displays; the right inset protects labels from the diagonal. Portrait keeps the existing bottom-panel placement and 86% height, now with the short guide first. No screenshot capture, blur, decorative animation, native pause policy, guest setting, renderer, audio or input backend is added or changed. The paused/running label uses the existing model flag.

Tools, advanced rows, developer visibility, logging, slider commit callbacks, session actions and exit confirmation retain their existing implementations. Controller mode bypasses the landing page and keeps direct category navigation and the existing hints. The new remembered state is only the touch tool/guide presentation choice. Dialogs bypass the landing page, preserving confirmation/log interactions. Touch tool corners become 4dp; controller geometry remains unchanged.

## Validation

Four Compose/Robolectric regression cases exercise landscape Controls -> guide -> Resume, portrait Settings -> guide, quit cancellation/confirmation on a short 640x320dp landscape viewport, and controller bypass. The host in those cases is a fake callback/state harness; it does not execute a guest or measure device performance. The workflow also retains the full BaseShots suite and shared palette checks. Check the exact commit's workflow for results.

The preceding cover/library increment c7fade3 passed 169 JVM tests (21 BaseShots), zero failures/errors/skips. UI #91's first native job failed resolving Gradle dependencies before native compilation; a re-run of that job on the same source succeeded. Its report archive 11934847904 has verified SHA256 547f5de41dc0ef96952c4a50a4c742328c8f8dbfdf00790ee9b5d7b42c7f0ebd and CRC. No dependency versions were changed to work around that failure.

## Delivery limits

The actual game surface/transparency, touch geometry, physical controller and rotation require phone validation. This increment is source/UI validation, without APK trigger or official release. It implements the guide structure of the green projection without importing its illustrative artwork or inventing FPS, compatibility or playtime.
