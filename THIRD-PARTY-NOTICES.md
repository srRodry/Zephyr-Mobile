# Added presentation engines and licensing

Original XenDroid/Xenia components retain their original licenses. The combined
build now links a GPL-3.0-or-later LSFG engine; distribution of that combined
binary must provide corresponding source under the applicable GPL terms as well
as the original notices. This does not relicense unrelated upstream BSD/MIT files.

| Component | Pinned source | License and local notice |
|---|---|---|
| Xenia | Existing source tree | `emulator-core/src/main/cpp/xenia/LICENSE` (BSD) |
| Win-FG | The412Banner/win-fg `fbc69ed6c5bf16b824d0a29bd8b171675101f9dd` | `third_party/winfg/LICENSE` (MIT), FidelityFX notice and THIRD-PARTY.md |
| LSFG host chain | The412Banner/Bannerlator `15d4f73c079824d54c468f079361ce56d88a7c2e` | `third_party/lsfg/LICENSE` and original GPL-3.0-or-later SPDX headers; Eden/WinNative/lsfg-vk credits preserved |
| DXBC translator subset | Same Bannerlator revision, derived from DXVK | `third_party/lsfg-dxbc/LICENSE.md` (zlib-style license) |
| Barlow, Barlow Semi Condensed (app fonts) | google/fonts `ofl/barlow`, `ofl/barlowsemicondensed` (v1.408) | SIL Open Font License 1.1, `app/src/main/assets/font-licenses/Barlow-OFL.txt` |
| JetBrains Mono (app font, IDs and config keys) | google/fonts `ofl/jetbrainsmono` (variable) | SIL Open Font License 1.1, `app/src/main/assets/font-licenses/JetBrainsMono-OFL.txt` |
| Snapdragon Game Super Resolution 1 | SnapdragonStudios/snapdragon-gsr `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e` (`sgsr/v1/include/glsl/sgsr1_shader_mobile.frag`) | `third_party/sgsr/LICENSE` (BSD-3-Clause); ported to XESL as `xenia/src/xenia/ui/shaders/guest_output_sgsr.xesli` with the notice kept |

Paths above are relative to `emulator-core/src/main/cpp` unless fully qualified.
XenDroid-specific modifications are recorded in each `XENDROID_INTEGRATION.md`.
The Win-FG adapter adds bounded allocation/error handling and scratch cleanup;
the LSFG adapter replaces compositor dispatch, bounds cache parsing and provides
a controllable pacing clock for software tests. Original shaders written for
XenDroid's optional SDR filters are generated during the build.

No code from the current CC BY-NC-ND LSFG-VK v2 was imported. Research is in
`docs/lsfg-vk-referencia.md`. `Lossless.dll` and extracted LSFG SPIR-V are
user-provided private runtime data: never included in source, APK, or public
fixtures. Native tests accept a path to the user's DLL and discard the temporary
cache they create.

License texts and engine notices are bundled as offline assets under
`file:///android_asset/engine-licenses/` (fonts: `file:///android_asset/font-licenses/`);
About → Open-source licenses links to them. Distribute the source and build instructions for the exact APK revision,
including these native changes and the imported source/notices.

## Research: structured shader jumps

Imported from phforner0/Xendroid-Plus commit
`4f0d10aa353e3be729862411df59827889de6cc9`, authored by phforner0
with the co-author attribution retained in the upstream history. Files:
`spirv_shader_translator.cc`, `spirv_shader_translator.h`,
`structured_jumps.h` under the Xenia GPU directory, and
`emulator-core/src/test/cpp/structured_jumps_test.cc`.
Original Xenia BSD notices are retained. Xendroid+ contributions are
Copyright (C) 2026 phforner0 and contributors, AGPL-3.0-or-later as declared
by that revision. Full license is bundled in
`emulator-core/src/main/assets/engine-licenses/xendroid-plus-AGPL-3.0.txt`.
For this combined research build, distribute corresponding source under the
applicable AGPL terms alongside the retained third-party notices.
Source: https://github.com/srRodry/Zephyr-Mobile/tree/research/android-x360-gpu-candidate
Upstream: https://github.com/phforner0/Xendroid-Plus/commit/4f0d10aa353e3be729862411df59827889de6cc9
