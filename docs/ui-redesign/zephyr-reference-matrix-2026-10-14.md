# Green reference boards — scope and implementation decisions

Reference set supplied 2026-10-14: compatibility/performance, downloads/DLC, controls/mapping, first run, library/details/guide, system settings, game content, discovery/library and profiles.

The common design language is accepted: dark green surfaces, deep-green actions, restrained lime focus, readable hierarchy, cover-led presentation, category rows and static diagonal cuts. Generated reference text and figures are not evidence about the app or a game.

| Board | Existing basis / implementation direction | Truth boundary |
| --- | --- | --- |
| Library/details/guide | Green tokens, blade navigation, selected-game feature, real-cover-aware overview and touch guide implemented before this increment. | Small embedded icons stay contained. Play is a launch action, not a save-state resume guarantee. |
| Settings/system | This increment implements a category hub over existing panels and global search. | Expose only schema-backed controls. The drawings do not create OpenGL/Metal backends or new render capabilities. |
| Compatibility/performance | Existing local compatibility records and session reports can support a later visual refinement. | Keep the actual status scale and data scope; no global Perfect verdict, invented FPS/1% lows, temperatures, driver recommendations or inferred device compatibility. Performance optimization remains paused. |
| Downloads/updates/DLC | Existing local content/patch management and application update checks remain usable. | No game/DLC download catalog, invented installed versions, download queue, speed or background-resume service is implied by the board. |
| Controls/mapping | Existing physical/touch input settings and layout tools are the backend for later redesign. | Do not label universal gamepad buttons with invented game-specific actions; paired status and supported bindings must come from actual input state. |
| First execution | Existing setup/access/scan flows are the basis. | Show completion only for verified steps. No automatic ideal hardware optimization or cloud backup claim. |
| Game content | Existing scanned content, patches, saves and real sessions are the basis. | Do not insert fictional content versions, file sizes, sync timestamps or resume-position data. |
| Discover/library | Existing collections, favorites, recent sorting and scans are available. | Genres, community rankings, installation status and completion percentages require actual data/model support. |
| Profiles | Existing local profile creation/management and backup flows remain intact. | Cloud synchronization, per-profile themes, avatars and automatic backup are separate capability checks, not assumptions from a mockup. Preserve user identity and existing profile/save relationships. |

Next visual passes should audit Profiles, Controls, Content and setup against their actual models before changing layout. No placeholder switch or button should suggest an unavailable service is active. Keep credits and technical XenDroid/Xenia references needed for provenance and compatibility. Do not use the Xbox sphere/X mark.
