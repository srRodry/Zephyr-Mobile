# UI 2.0: first device recording and icon-only layout correction

Base: e8726386103683dd290f6b02abe1187426848c8b. All edits remain on feature/zephyr-mobile-branding.

## Evidence

The supplied 1000397782.mp4 is 44.117914 seconds, 1080 × 2400. It shows the redesigned library, game overviews, active-profile panel, controls and settings, switching list/grid density and rotating to landscape. No unexpected application closure is visible in the reviewed recording. It does not show game execution or the in-game menu, and its displayed FPS on launcher/settings screens is not an emulated-game performance measurement.

The icon-only hero reserves a 196 dp artwork area for a 104 dp embedded icon, then places the title/metadata/actions below. On the observed phone this leaves excessive empty space. The game name is repeated in the overview header and hero. The landscape library panel retains its older blurred-cover presentation. Floating overlays obscure portions of navigation; their presence is not sufficient to attribute them to this app or diagnose rendering stalls.

## Increment

- Icon-only heroes now put a 64 dp contained icon beside the title and metadata in a naturally sized card, with actions below. No fixed hero height, generated artwork, image upscaling enhancement, new blur or animation is added. Complete user-supplied covers retain their existing editorial crop and 196 dp artwork height.
- The touch overview header uses the localized Details label and a plain background; the main game title appears in the hero. Other sections and controller headers retain game identity.
- The landscape touch details panel reuses this hero, preserving Play, Details, favorite, reported compatibility, play/session statistics, patches, active profile and quick settings. Buttons can wrap and keep 48 dp touch height.

No launch/profile/save behavior, persisted preference, native/audio code or compatibility result is changed. The initial review is evidence for a layout correction, not approval of the complete design or game compatibility.

## Validation

Compose compilation and the existing 149 selected JVM regressions must pass on this increment. Android visual checks remain pending: long titles, large fonts, icon-only/full-cover variants, filtered library, landscape panel scrolling and wrapped actions. No APK is requested by this commit; preserve the workflow gate until validation completes.
