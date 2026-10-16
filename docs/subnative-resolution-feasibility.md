# Subnative rendering feasibility gate (540p / 480p)

Status: **investigation only; no native sub-1x implementation enabled**.

Target: Motorola Edge 30 Ultra (Snapdragon 8+ Gen 1 / Adreno 730), Forza Horizon. Keep `main` untouched.

## Why a UI-only fractional scale is unsafe

Xenia's draw-resolution scale is an integer multiplier in its EDRAM / render-target model. Scaling affects render-target extents, tile layout, multisample addressing, texture cache, shader coordinate transformations and resolves. Changing the UI or a config value to 0.75 does not implement native 540p. `Console.internal_display_resolution` is a guest-visible display mode, not a verified replacement for render-target scaling.

Native paths to audit:

- `emulator-core/src/main/cpp/xenia/src/xenia/gpu/draw_util.cc`
- `emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_render_target_cache.cc`
- `emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_texture_cache.cc`
- `emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_command_processor.cc`

## Feasibility checks before implementing

1. Trace guest draw dimensions and host attachment dimensions for color, depth/stencil, MSAA 1x/2x/4x, and all resolve paths.
2. Record whether EDRAM ownership, tile indexing, shader addressing, and texture promotion assume host pixels >= guest pixels.
3. Enumerate all fallbacks: direct host resolve, in-pass resolve, scaled resolve, EDRAM dump and CPU readback.
4. Verify depth/stencil and resolve equivalence with reference captures, especially half-pixel offsets and small render targets.
5. If feasible, implement an **explicit experimental mode** with a default-off guard and runtime reporting of actual host attachment extents; do not overload existing integer scale flags.
6. Fail closed to the native 1x path if unsupported format, sample pattern, or resolve mode is encountered. Do not silently claim 540p.
7. Test 960x540 first; then 854x480. A fractional global scale is not necessarily valid for every guest target.

## A/B device protocol

Keep game, save point, graphics driver, emulator commit, audio backend, and all unrelated flags constant. Record 1x baseline and experimental mode in matched cold/warm runs, alternating order. Log frame-time p50/p95/p99, GPU time where available, audio underruns, thermal state, actual render-target dimensions, artifact counts and crashes. Run at least three comparable trials per mode. Never infer FPS gains from pixel count alone.

## Release gate

No public APK claiming internal 540p or 480p until source audit, native build, CI, actual attachment-size verification and device tests succeed. Keep existing 1x behavior as the default.
