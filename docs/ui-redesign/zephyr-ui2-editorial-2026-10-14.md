# Zephyr UI 2.0 — first editorial implementation

Base: `1993e55769063e178fc506dad763e81c78799787`, branch `feature/zephyr-mobile-branding`.

The approved visual direction is implemented incrementally in the touch library and game overview. Performance investigation is paused at the user's request.

## Behavior

- Compact touch layouts show a scrolling editorial heading and featured game from the current filtered list. A selected game is preferred; otherwise the first current sort result is used. Search results suppress the hero.
- The existing medium density preference becomes a compact horizontal list on compact layouts; small and large cover grids remain available. Persisted enum values and sorting/filtering/collections are unchanged. Wide layouts retain the rail and details panel.
- Featured Play calls the same `GameActions.play` path, including profile choice and disc preparation. Details opens the existing game screen. No resume capability is implied.
- The touch game overview uses an angled artwork header, Play/Start-with actions and a silver panel linking existing settings, content, saves and performance sections. Existing last-session, compatibility, patches and quick settings remain below.
- Covers, names and metadata come from real library records. No concept illustration, fictitious profile, FPS or compatibility result is embedded. Small embedded icons remain contained in the hero.
- Controller layouts, native code, audio, signing and runtime defaults are unchanged. New components use static shapes and surfaces, without new blur or animation effects.

## Validation and remaining work

Both English and Brazilian Portuguese resources parse and have unique keys. Existing CI must compile Compose/resources and run the regression suite for the implementation commit before packaging. This change requests no APK build.

Visual validation on Android remains necessary: portrait/landscape, large font sizes, long game titles, small embedded icons, search/empty library, filter/density transitions and screen-reader focus. Source review and successful compilation do not establish visual approval on the device.

The settings, profiles and in-game menu redesigns from the concept board are later stages, not part of this commit.
