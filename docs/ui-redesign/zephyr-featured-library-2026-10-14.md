# Featured library and game identity — 2026-10-14

## Implemented behavior

Touch portrait/compact layouts now put the selected visible game above the lazy cover shelf, with explicit Play, Favorite and Details actions. The candidate is the selected game in the current filtered/sorted results, or their first game if selection is absent/outside them. It never advertises a hidden filtered-out game. Details and Play establish that game's selection before calling the existing action. A shelf tap still only selects; long press and existing controller-button shortcuts remain intact. A nonblank search continues to hide the feature/shelf and prioritize matching grid/list results.

The shared feature identity is also used by the touch game sheet and the wide library detail panel. A user-provided cover occupies a bounded 16:9 presentation area with `ContentScale.Fit`: portrait cover text remains intact, and landscape art fits without invented backgrounds or blur. A game with only an extracted small icon keeps the compact art/name layout instead of an enlarged blurry banner. Cover source/cache, game identity, recorded compatibility and actual activity data are unchanged. Generated mockup artwork is not added to the application.

The game sheet presents prominent art only in Overview; Settings/Data keep compact identity to prioritize their tools. Play and Launch With have full-width, wrapping 48dp-minimum touch targets. Existing launch dialogs, section IDs, back-history, profiles, saves and settings callbacks remain unchanged. The existing Recent filter is now discoverable as a touch chip; its order/count derive from the real local activity model. Cover density defaults/persistence and controller layouts remain intact.

## Validation and limits

Four additional BaseShots regressions exercise selected-feature Details routing, touch Recent selection, custom-cover visibility across Overview/Settings, and compact embedded-icon behavior. The full existing BaseShots suite covers portrait, landscape, controller composition, section history and launch dialogs. Existing palette contrast, data persistence and native checks also run in the UI workflow. Check the commit's workflow for authoritative results; no device performance or compatibility measurement is implied.

This implements the feature/identity layer of the green projection. The complete diagonal in-game guide remains a separate increment. No APK trigger, audio/native change, performance-default change, production signing or release is included.
