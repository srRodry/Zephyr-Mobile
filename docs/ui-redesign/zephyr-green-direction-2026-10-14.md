# Zephyr green direction — 2026-10-14

## Decision and scope

The approved dark green reference supersedes the blue/silver palette proposed earlier today. This increment migrates shared Compose tokens, primary touch actions, blade navigation, editorial panels, library/game-sheet surfaces, guide category selections and vector Z branding. It preserves the four navigation groups and their existing destinations, controller focus mechanics, launch actions, settings persistence and semantic compatibility colors.

| Role | Color |
| --- | --- |
| Background | #070D08 |
| Surface / elevated surface | #0F1A11 / #16251A |
| Deep green primary touch action / label | #107C10 / #FFFFFF |
| Lime highlight / label | #9BF00B / #0B1A0C |
| Primary / secondary / muted text | #EAF3EA / #B8CBB9 / #A0B9A2 |
| Moss divider / stronger border | #40552D / #64834A |

`acc` remains the readable highlight used by links, focus and selections. `action`/`onAction` separate deep green touch buttons from lime highlights, avoiding dark green text on dark surfaces. The controller still derives its adaptive accent from supplied cover colors with its existing readability adjustment; its default accent is now lime. Compatibility, errors, changed-setting and newly-exposed-setting colors keep their semantic roles.

The existing native-vector Z geometry and adaptive safe area are unchanged; its blue/silver fills become green/lime. The monochrome themed icon remains white for system tinting. No Xbox sphere or X trademark is introduced. Adaptive launcher backgrounds and the app/fallback vector use the same dark green backdrop. Legacy raster mipmaps are not regenerated in this increment (minimum supported Android API 29 uses adaptive launcher resources).

## Validation

`ZephyrPaletteTest` checks 4.5:1 text contrast against every opaque touch surface, action/selection labels, and base controller primary labels/highlights against composited surfaces. White on #107C10 is approximately 5.37:1. Muted touch text was lifted to #A0B9A2 so it also passes on the brightest token surface. CI additionally runs the existing library/game-sheet portrait/landscape/controller navigation and launch regressions, Kotlin/resources compilation and selected native integration checks. CI status is authoritative in the workflow associated with the commit.

The generated board is a visual proposal, not an APK capture: artwork, guide layout and descriptions in it are illustrative. This commit establishes palette consistency; it does not implement the board's entire hero layout or diagonal in-game guide. Embedded small game icons retain their contain behavior; the app does not obtain replacement cover assets from the mockup. Compatibility remains locally recorded and is not inferred from pictured games. Device visual validation and APK packaging remain pending. No emulator/audio/performance/default/signing changes or APK trigger are part of this increment.


## Geometry contract

Zephyr 2.0 uses shape by role rather than screen-specific styling. Structural cards and grouped content surfaces use `ZephyrPanelShape`, the shared restrained diagonal cut. Primary and secondary touch actions, selectable rows and menu interactions use `ZephyrActionShape` (4 dp). Grouped tabs and segmented controls use `ZephyrSegmentShape` (6 dp) with action-shaped children. Filter chips, status pills, circular avatars, transient toasts, platform sheets and media-cover rounding remain intentionally distinct because their geometry communicates a different interaction or content role.

This contract applies across Library, Game Sheet, Content, Profiles, Controls and Settings. New screens should reuse these tokens instead of introducing ad-hoc card radii. Media artwork may retain its cover radius and must not be forced into the structural diagonal cut.
