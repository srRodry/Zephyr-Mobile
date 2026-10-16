# Zephyr-Mobile: experimental subnative 360p rendering

Status: **design and safety gates only — NOT IMPLEMENTED**.
Target: Forza Horizon 2 on Adreno 730, 1280x720 guest -> 640x360 host.

## Step 1 — verified code constraints
- `xenia/gpu/texture_cache.cc`: `draw_resolution_scale_x/y` are int32 clamped to >=1 by `TextureCache::GetConfigDrawResolutionScale`; fractional scale is unsupported.
- `xenia/gpu/draw_util.cc`: `GetHostViewportInfo` scales viewport/scissor calculations using integer resolution scale; `GetResolveInfo` and `ResolveInfo::GetCopyShader` depend on the same factors.
- `xenia/gpu/vulkan/vulkan_render_target_cache.cc`: `CreateRenderTarget` uses `key.GetWidth() * GetKeyScaleX(key)` and `GetRenderTargetHeight(...) * GetKeyScaleY(key)`. Other uses include transfer clears, stencil clear rectangles, band replication, and resolves.
- A 4x-MSAA guest surface may have different host storage/sample semantics. Never halve VkImage extents in isolation.

## Step 2 — implementation boundaries
Do not modify guest EDRAM addresses, guest pitch, texture tiling, resolve destination addresses, or guest shader-visible dimensions.
Introduce separate rational host rasterization scale (numerator=1, denominator=2) *only after* mapping every consumer of render-target pixel coordinates.
Required invariant: color/depth attachments, render areas, viewports, scissors, barriers, copy/resolve rectangles, and sampled textures agree on host dimensions.
Guest-memory resolves and host texture promotion must explicitly convert host-to-guest coordinates. Reject or fall back for unsupported formats, MSAA modes, partial resolves and non-2D targets.

## Step 3 — staged validation and rollback
1. Add disabled-by-default opt-in with explicit startup log; at this stage, no change in resolution.
2. Implement host allocation + viewport/scissor together; build with validation layers and assert that all attachment/render-area bounds match.
3. Implement EDRAM resolve/transfer conversions and MSAA/depth cases before enabling actual 360p.
4. Verify visually using the same scene and driver; collect Vulkan validation errors, crashes, FPS, GPU time, audio underruns and device temperature.
5. Compare baseline 720p against real 360p using render-target dimension telemetry. A 360p final presentation image alone is NOT evidence of subnative rendering.

Acceptance: game loads and renders multiple frames; host color/depth surfaces measured at half horizontal and vertical logical pixel dimensions where applicable; resolves correct; no new Vulkan validation errors; measurable GPU-time change. If any invariant fails, leave 360p disabled and restore 720p.

## Current release
Commit ec0ecb5c disables direct-host-resolve-to-texture by default for an unrelated visual-artifact A/B test. It does NOT implement 360p.
