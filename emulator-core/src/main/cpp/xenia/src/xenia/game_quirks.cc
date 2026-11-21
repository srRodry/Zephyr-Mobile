// SPDX-License-Identifier: WTFPL
#include "xenia/game_quirks.h"

#include <array>
#include <string>
#include <variant>

#include "xenia/base/cvar.h"
#include "xenia/base/logging.h"

namespace xe {
namespace game_quirks {

using QuirkValue = std::variant<bool, int64_t, double, const char*>;

struct Quirk {
  uint32_t title_id;
  const char* cvar;
  QuirkValue value;
  const char* note;
};

// Entries apply at per-game-config priority, so a user's own per-game config
// still overrides them.
static const Quirk kQuirks[] = {
    // On the Edge 30 Ultra, Turnip GMEM lost the device during boot.
    // The same build and driver reached campaign combat with sysmem.
    {0x415608C3, "turnip_debug", "sysmem",
     "use the Turnip mode that reached Black Ops II campaign combat"},
    {0x415608C3, "apu_aaudio_log_stats", true,
     "measure Black Ops II audio starvation and decoder timing"},
    // Evaluate the port's three-worker budget against the four-worker capture.
    {0x415608C3, "vulkan_pipeline_creation_threads", int64_t(3),
     "reduce pipeline compilation contention during Black Ops II shader bursts"},
    // Use the alternate XMA implementation for the audio comparison.
    {0x415608C3, "xma_decoder", "old",
     "use the alternate XMA decoder for the Black Ops II audio preview"},
    // Army of Two retail: refresh page validity at frame boundaries to avoid
    // stale uploaded data (corrupt/warped character textures and geometry).
    // Source: xenia-manager/optimized-settings/settings/454107F8.toml.
    // Zephyr Edge30 corruption report; device confirmation still pending.
    {0x454107F8, "clear_memory_page_state", true,
     "refresh uploaded page state for Army of Two rendering corruption"},
    // The Devil's Cartel consumes shader export data on the guest CPU.
    // Use the existing host-mapped readback path if direct import is unavailable.
    {0x454109AB, "memexport_enable", true,
     "make shader exports visible to the Army TDC guest CPU"},
    {0x454109AB, "readback_resolve", "uma",
     "provide the host-mapped fallback for Army TDC shader exports"},
    {0x454109AB, "vulkan_memexport_range_coherency", true,
     "avoid draining unrelated GPU work for Army TDC coherency requests"},
    {0x454109AB, "vulkan_memexport_early_submit", true,
     "start Army TDC deferred shader exports before guest coherency waits"},
    {0x454109AB, "memexport_await_fences", true,
     "publish Army TDC shader exports before guest-visible fences"},
    {0x454109AB, "async_shader_skip_draws", false,
     "preserve Army TDC draws while shader translation is pending"},
    {0x454109AB, "vulkan_async_skip_draws", false,
     "preserve Army TDC draws while Vulkan pipelines compile"},
    // The first 3D scene resolves RGBA16 with unsigned integer packing and
    // exponent bias +11. Keep the resolve and texture fetch formats paired.
    {0x454109AB, "accurate_resolve_number_formats", true,
     "use resolve shaders that pack Army TDC integer destinations"},
    {0x454109AB, "resolve_copy_dest_number_packing", true,
     "preserve Army TDC integer resolve destinations"},
    {0x454109AB, "texture_integer_num_format", true,
     "sample Army TDC integer textures in guest units"},
    {0x454109AB, "vulkan_army_tdc_selective_resolve_readback", true,
     "copy the Army TDC lighting inputs and small resolves to the guest CPU"},
    {0x454109AB, "vulkan_sanitize_host_7e3_exports", true,
     "keep non-finite HDR exports out of host blending"},
    {0x454109AB, "vulkan_army_tdc_neutralize_ssao", true,
     "replace the aliased Army TDC ambient occlusion mask with neutral AO"},
    {0x454109AB, "spirv_texture_unorm8_precision", true,
     "match Xenos precision for Army TDC particle mask comparisons"},
    {0x454109AB, "vulkan_drained_submission_window", int64_t(1),
     "keep the short Vulkan batch only until the first submission after a drain"},
    {0x454109AB, "vulkan_frame_sync_callsite_stats", false,
     "preserve aggregate Vulkan timing without scanning barrier call sites"},
    {0x454109AB, "log_gpu_frame_time_breakdown", true,
     "report Army TDC GPU work and fence waits once per second"},
    {0x454109AB, "log_gpu_frame_time_breakdown_passes", false,
     "avoid per-pass timing overhead while testing Army TDC"},
    {0x454109AB, "vulkan_log_slow_submission_ms", int64_t(0),
     "keep frame summaries without logging every slow Army TDC submission"},
    // Ninja Gaiden 2: the opening FMV renders as three time-skewed vertical
    // slices when the restore half of the saverest inline is active. Every
    // emitted expansion verifies correct, so the current reading is a latent
    // guest-side race between the slice decode workers that the faster
    // epilogues expose. Keep the save half inlined, call the restores.
    {0x544307D5, "inline_gprlr_saverest_parts", int64_t(1),
     "FMV slice desync with inlined restores"},
    {0x4D5307D1, "spirv_multiply_zero_test_on_bits", true,
     "ir3 cannot compile fmadz"},
    {0x494707D4, "vulkan_in_pass_resolve", true, "in-pass resolves tuned here"},
    {0x49470804, "vulkan_in_pass_resolve", true, "in-pass resolves tuned here"},
    {0x494707D4, "network_enabled", false, "hangs on a blocking recvfrom"},
    {0x49470804, "network_enabled", false, "hangs on a blocking recvfrom"},
    {0x4E4D083A, "spirv_multiply_zero_test_on_bits", true,
     "ir3 cannot compile fmadz"},
    // NFS Most Wanted (Criterion): the Turnip compiler rejects fmadz in VS
    // 13EE4011483DC17D. The bit test preserves the SM3 zero-multiply result.
    {0x45410961, "spirv_multiply_zero_test_on_bits", true,
     "ir3 cannot compile fmadz in the first 3D scene"},
    // Its CPU consumes memory-exported geometry. Without guest-visible
    // exports the CPU/GPU handshake stalls at PM4_WAIT_REG_MEM after the FMV.
    {0x45410961, "memexport_enable", true,
     "CPU consumes memexport output before continuing the command stream"},
    {0x45410961, "readback_resolve", "uma",
     "host-mapped fallback for memexport when guest RAM import is unavailable"},
    // It moves depth surfaces between 4x and 1x MSAA like Forza Horizon's
    // shadow atlas (1040x2528 at 13 tiles of pitch, among others): ~105
    // transfers and ~13400 tiles a frame. Into the 1x surfaces of their
    // samples, ~6000 tiles: +7% fps cool (11.5 -> 12.3, GPU 67.9 -> 64.8 ms a
    // frame, two pairs of arms switched in one session) and +8% hot, the same
    // image on Connors Bridge Road (2026-10-07).
    {0x45410961, "vulkan_depth_4x_as_1x", true,
     "4x depth-only draws into the 1x surface of their samples"},
    {0x45410961, "vulkan_samples_as_pixels_simple_ps", true,
     "4x draws with simple pixel shaders into the 1x surface"},
    // It samples its reflection cube map between the 36 resolves building its
    // faces and mips every frame, and each reloaded the whole cube: GPU busy
    // 43.5 -> 41.1 ms a frame at the first race's start line, the same image
    // (2026-10-08).
    {0x45410961, "texture_partial_reload", true,
     "only the layers and levels written since the last load reloaded"},
    // Its command stream waits on memory once a frame (~7 ms) for its CPU to
    // finish with the exported data. Re-checked within 200 us instead of
    // after the 1 ms poll interval, the write is seen 0.69 -> 0.29 ms later
    // on average, the wait 7.76 -> 7.23 ms (2026-10-08).
    {0x45410961, "wait_reg_mem_backoff", true,
     "unmet memory waits re-checked early"},
    {0x45410961, "wait_reg_mem_backoff_max_us", int64_t(200),
     "unmet memory waits re-checked within 200 us"},
    // Forza Horizon: both exact (the same image for every input); on the POCO
    // F7 (Adreno 825) together +3.0% fps, -3.7% GPU time (AB5, 2026-09-29).
    {0x4D5309C9, "spirv_texture_sign_branch", true,
     "exact texture sign decode in a uniform branch"},
    {0x4D5309C9, "spirv_fast_precision_rounding", true,
     "exact cheaper 21-bit rounding"},
    // Exact too: ~10 full-screen textures reloaded per frame skip the copy from
    // a buffer; +4.5% fps, -5.4% GPU time on the POCO F7 (2026-09-29).
    {0x4D5309C9, "vulkan_texture_load_to_image", true,
     "texture loads straight into the image"},
    // Exact: the resolves store into the textures read back from them, whose
    // uploads are then skipped (3.0 -> 0.75 ms per frame, GPU -1.4 ms, +1.4%
    // fps with the POCO F7 cool; 2026-09-30).
    {0x4D5309C9, "vulkan_direct_host_resolve_to_texture", true,
     "resolves store straight into their textures"},
    // Main pass -7% (AB5). No visible difference parked, over a 45 s drive
    // and in the pause menu (2026-09-30); night, rain and tunnels unchecked.
    {0x4D5309C9, "spirv_ps_relaxed_math", int64_t(3),
     "no SM3 zero-multiply or 21-bit rounding emulation in pixel shaders"},
    // 55% of the main pass draws use alpha to coverage: main pass -7.5%, the
    // same foliage parked and driving (only the dither pattern differs).
    {0x4D5309C9, "host_alpha_to_coverage", true,
     "alpha to coverage by the host's fixed function"},
    // The shadow atlas is cleared by 4x depth-only quads read back as 1x: no
    // more 1x <-> 4x transfers of it, GPU time -1.1 ms (-3.4%), the same
    // shadows (depth at the double-resolution pixel centers instead of the 4x
    // sample positions; 2026-09-30).
    {0x4D5309C9, "vulkan_depth_4x_as_1x", true,
     "4x depth-only draws into the 1x surface of their samples"},
    // The Direct3D wait for the GPU (only the code with this signature - its
    // first 16 instructions - so another build is left alone) sleeps until
    // the command processor makes progress instead of spinning: Guest CPU 0
    // 97% -> 37% of a core, the same fps (AB3, AB5).
    {0x4D5309C9, "spin_park_guest_functions", "829F04A8:B864F65007F969C0",
     "the Direct3D GPU wait parks instead of spinning"},
    {0x4D5309C9, "spin_park_mode", int64_t(1),
     "the Direct3D GPU wait parks instead of spinning"},
    // Forza Horizon never reads the 7e3 alpha: +3.1% fps, -3.5% GPU time
    // (AB3), the same image in motion (AB5).
    {0x4D5309C9, "render_target_7e3_as_r11g11b10", true,
     "7e3 scene color in 32 bpp"},
    // The clear of a resolve inside the game's own render pass: with the
    // draw barriers moved to the resolve, render passes 205 -> ~133 per frame
    // (AB8), the same GPU time at the 30 fps cap (S18, 2026-10-01). Every
    // visual check since AB7 ran with it.
    {0x4D5309C9, "vulkan_resolve_clear_in_guest_pass", true,
     "resolve clears inside the game's render pass"},
    // Turnip's early preamble costs every draw ~1.6 us of GPU time when the
    // draws are small (one-primitive main pass draws 2.8 -> 1.2 us, S20); off,
    // GPU time -0.8 ms (-2.4%) per frame at the 30 fps cap (S21, 2026-10-01).
    // Only Turnip reads it; the pipeline cache file is separate per flags.
    {0x4D5309C9, "ir3_debug", "noearlypreamble",
     "no early shader preamble in the Turnip compiler"},
    // The EDRAM is reused by targets that start with a clear quad: the
    // transfers it overwrites are skipped - 11 of 49 transfers and 3.7 of 12.5
    // thousand tiles per frame, GPU time -1.1 ms (-3.1%) at the 30 fps cap,
    // the same image parked and over a drive (S24, 2026-10-01).
    {0x4D5309C9, "skip_overwritten_transfers", true,
     "no transfers into what the draw overwrites"},
    // The 1280x720 lighting marks stencil at 640x360 4x (quads with texture-
    // less pixel shaders) between 1x passes: drawn into the 1x surface of
    // their samples, transfers 8071 -> 3703 tiles a frame, GPU time -1.7 ms
    // (-5.2%) at the 30 fps cap, the same image parked (S28, 2026-10-01).
    {0x4D5309C9, "vulkan_samples_as_pixels_simple_ps", true,
     "4x stencil marking with simple pixel shaders into the 1x surface"},
    // Transfers a clear quad covers in part (whole rows of tiles claimed, less
    // drawn) copy only the rest: 9 a frame, GPU time -0.1 to -0.2 ms at the
    // 30 fps cap, the same image parked (S37, 2026-10-01).
    {0x4D5309C9, "skip_overwritten_transfers_cutout", true,
     "transfers skip the part the draw overwrites"},
    // Exact: the texture signs (mostly gamma) as specialization constants of
    // the pixel shader pipelines, no runtime branches around the samples - GPU
    // time -1.9 ms (-7.9%) at the 30 fps cap, both restart A/B pairs within
    // 0.1 ms, the same image parked (S40, 2026-10-01). The first launch after
    // the change compiles the pixel shader pipelines again.
    {0x4D5309C9, "spirv_texture_sign_specialization", true,
     "texture signs known to the host compiler per pipeline"},
    // 2D fetches with the LOD the host computes (implicit LOD plus the bias)
    // instead of 4 coarse derivatives and an explicit-gradient sample: with
    // the signs specialized the texture pipe limits - GPU time -1.6 ms (-7%)
    // at the 30 fps cap, both restart A/B pairs within 0.2 ms, the same image
    // parked (S43, 2026-10-01). Neutral in AB10, when branches still wrapped
    // the samples.
    {0x4D5309C9, "spirv_texture_implicit_lod", true,
     "2D texture fetches with the host's LOD"},
    // Vertex shaders without the Shader Model 3 "0 * x = 0" emulation (a
    // third of the main pass vertex shaders' instructions): the same positions
    // for finite operands, so the passes still match - GPU time -0.7 ms at the
    // 30 fps cap (S43, S45: both restart A/B pairs), no depth fighting on the
    // road markings over a drive (S45, 2026-10-01).
    {0x4D5309C9, "spirv_vs_relaxed_math", int64_t(1),
     "no SM3 zero-multiply emulation in vertex shaders"},
    // The scene is drawn in 3 bands of predicated tiling replaying the same
    // command buffers: every draw executed only in the first band it's
    // predicated into, into render targets as tall as the screen, each band
    // resolved from its rows - 2916 -> ~1980 draws per frame, GPU time -1.0 to
    // -1.1 ms at the 30 fps cap (S49, S51), the same image parked and over
    // drives (2026-10-02). Unchecked: shaders using the pixel position in the
    // ~300 draws only the lower bands have.
    {0x4D5309C9, "merge_tiling_bands", true,
     "the bands of predicated tiling drawn as one"},
    // The JIT with the host's NaN rules for scalar FPU and VMX arithmetic
    // (like the x64 backend: only which NaN an operation with a NaN input
    // returns differs) and leaves up to 32 instructions inlined; loads and
    // stores stay bit exact (lfs/stfs keep signaling NaNs, so data copied
    // through float registers is untouched). With the exact paths cheap
    // (2026-10-02) the gain is small: Guest CPU 5 56.0-56.4 M instructions
    // per frame against 52.7-63.5 without, Guest CPU 1 -2%, at the 30 fps
    // cap (S67, b85); the same image parked.
    {0x4D5309C9, "a64_fpu_nan_fixup", false,
     "scalar FPU NaNs by the host's rules"},
    {0x4D5309C9, "a64_vmx_nan_fixup", false, "VMX NaNs by the host's rules"},
    {0x4D5309C9, "inline_leaf_max_instructions", int64_t(32),
     "leaves up to 32 instructions inlined"},
    // Forza Horizon 2 (the same engine family) takes Forza Horizon's GPU
    // quirks: racing in its first race at full throttle, GPU time 41.1 / 36.4
    // -> 20.2 / 20.2 ms per frame, 24-27 -> 29.95 fps, frames over 37 ms
    // 38-79% -> 0.7% (S70, b89, 2026-10-02); the exact ones alone 29.0 / 26.8
    // ms. The same image at the same spots of the race, no seams at the band
    // boundaries. Without the spin park (Forza Horizon's address). Its band
    // merge relies on merge_tiling_bands_call_sites: 100-200 draws a frame
    // were otherwise taken for an earlier band's (whole blocks of Castelletto
    // missing until close). With the rest of Forza Horizon's quirks too (the
    // 7e3 render target as R11G11B10, the host's NaN rules), against the
    // defaults alone: GPU time 9.37 -> 7.98 ms per frame driving, the same
    // streets (2026-10-05).
    {0x4D530AA4, "spirv_texture_sign_specialization", true,
     "texture signs known to the host compiler per pipeline"},
    {0x4D530AA4, "spirv_texture_sign_branch", true,
     "exact texture sign decode in a uniform branch"},
    {0x4D530AA4, "spirv_fast_precision_rounding", true,
     "exact cheaper 21-bit rounding"},
    {0x4D530AA4, "skip_overwritten_transfers", true,
     "no transfers into what the draw overwrites"},
    {0x4D530AA4, "skip_overwritten_transfers_cutout", true,
     "transfers skip the part the draw overwrites"},
    {0x4D530AA4, "vulkan_texture_load_to_image", true,
     "texture loads straight into the image"},
    {0x4D530AA4, "vulkan_direct_host_resolve_to_texture", true,
     "resolves store straight into their textures"},
    {0x4D530AA4, "vulkan_resolve_clear_in_guest_pass", true,
     "resolve clears inside the game's render pass"},
    {0x4D530AA4, "spirv_texture_implicit_lod", true,
     "2D texture fetches with the host's LOD"},
    {0x4D530AA4, "spirv_ps_relaxed_math", int64_t(3),
     "no SM3 zero-multiply or 21-bit rounding emulation in pixel shaders"},
    {0x4D530AA4, "spirv_vs_relaxed_math", int64_t(1),
     "no SM3 zero-multiply emulation in vertex shaders"},
    {0x4D530AA4, "host_alpha_to_coverage", true,
     "alpha to coverage by the host's fixed function"},
    {0x4D530AA4, "vulkan_depth_4x_as_1x", true,
     "4x depth-only draws into the 1x surface of their samples"},
    {0x4D530AA4, "vulkan_samples_as_pixels_simple_ps", true,
     "4x stencil marking with simple pixel shaders into the 1x surface"},
    {0x4D530AA4, "merge_tiling_bands", true,
     "the bands of predicated tiling drawn as one"},
    {0x4D530AA4, "ir3_debug", "noearlypreamble",
     "no early shader preamble in the Turnip compiler"},
    {0x4D530AA4, "render_target_7e3_as_r11g11b10", true,
     "7e3 scene color in 32 bpp"},
    {0x4D530AA4, "a64_fpu_nan_fixup", false,
     "scalar FPU NaNs by the host's rules"},
    {0x4D530AA4, "a64_vmx_nan_fixup", false,
     "VMX NaNs by the host's rules"},
    // Gears of War 3 (Unreal Engine 3) draws the depth of each projected
    // shadow at 4x MSAA into EDRAM it then reads as 1x - 20 shadows a frame in
    // its first checkpoint, each a 4x -> 1x and a 1x -> 4x transfer: 96
    // transfers (36700 tiles) and 143 render passes a frame, GPU-bound at 25
    // fps. Into the 1x surfaces of their samples (with the overwritten
    // transfers skipped, on for every title), 6300 tiles and 66 render passes
    // a frame, 30 fps (its cap), the same shadows (fork.opt b105, runtime
    // switches in the same scene, 2026-10-05).
    {0x4D5308AB, "vulkan_depth_4x_as_1x", true,
     "4x depth-only shadow draws into the 1x surface of their samples"},
    {0x4D5308AB, "vulkan_samples_as_pixels_simple_ps", true,
     "4x draws with simple pixel shaders into the 1x surface"},
    // Crysis 3 (CryENGINE 3) moves one 1350-tile depth surface between 1x and
    // 4x MSAA 47 times each way a frame on the deck of its first checkpoint:
    // 156 transfers (45100 tiles) and 211 render passes a frame, GPU-bound at
    // ~22 fps (39.6 ms of GPU a frame). Into the 1x surfaces of their samples,
    // 46 transfers (8400 tiles) and 109 render passes: GPU time -15%, +10%
    // fps over all arms, -17% and +18% against the neighboring ones, the same
    // image standing in the rain (fork.ui, runtime switches interleaved in one
    // session, 2026-10-06). The depth draws alone save only 1.1 ms.
    {0x4541098E, "vulkan_depth_4x_as_1x", true,
     "4x depth-only draws into the 1x surface of their samples"},
    {0x4541098E, "vulkan_samples_as_pixels_simple_ps", true,
     "4x draws with simple pixel shaders into the 1x surface"},
    // Its lighting and HDR copies go through integers: the light buffers (7e3
    // in the EDRAM) are resolved as k_2_10_10_10 with exponent bias +10, the
    // scene and the bloom 7e3 to k_10_11_11 (+6) and k_16_16_16_16 (+11), all
    // to unsigned integer destinations, and sampled with the integer
    // num_format and the opposite exponent adjustment (-7, -6, -11) - a
    // lossless HDR round trip. Packed as unsigned fractions, the copies
    // saturated (the light buffers 100% white, the scene ~98%, the exposure
    // chain 0) and the lit surfaces went black; with only the fetch side
    // honoured, the screen went white. Both sides: the lighting of the
    // original, with the direct resolves and the resolves into textures still
    // used for them (frame dump and runtime A/B, 2026-10-06).
    {0x4541098E, "accurate_resolve_number_formats", true,
     "resolve shaders that pack integer destinations"},
    {0x4541098E, "resolve_copy_dest_number_packing", true,
     "resolves honour their integer destinations"},
    {0x4541098E, "texture_integer_num_format", true,
     "integer texture fetches return integers"},
    // Half of the textures it draws on the deck (114-128 of ~260 in frame
    // dumps) were placed by the GPU, in pages its loading screen had resolved
    // to: valid only in the GPU's copy, the guest memory still holding the
    // loading screen (its hint text included). Its streaming writes into the
    // same pool - guest stores and file reads straight into it - invalidated
    // whole pages, and their reupload from guest memory put that in place of
    // the neighbouring textures: in one of four launches Psycho was drawn
    // blue. The GPU's data in those pages is kept now; the image and GPU time
    // as before (24.8 ms a frame, 2026-10-07).
    {0x4541098E, "shared_memory_preserve_gpu_writes", true,
     "CPU writes keep the GPU's data in their pages"},
    // Its worker threads spin on NtYieldExecution whenever they have no job:
    // the 6 guest CPU threads took 460-490% of a core standing still at the
    // 30 fps cap, the phone ~10.7 W, and the SoC throttles within minutes (big
    // cores capped from 2.8-3.2 to ~2.07 GHz, the GPU 4 steps down). Sleeping
    // 50 us per empty yield once a spin is under way: 255-296%, the same 30
    // fps and GPU time (runtime switch interleaved in one session; 20 us:
    // 9.8 W against 10.7 W unplugged, 2026-10-06).
    {0x4541098E, "guest_yield_sleep_us", int64_t(50),
     "spinning workers give the host core back"},
    // Exact: it copies the scene into one k_10_11_11 texture 7.5-9.5 times a
    // frame and samples it after each copy, reloading the whole 1152x720 every
    // time; with the resolves storing into it (and into its other resolve
    // destinations), reloads 1.8-2.4 -> 0.02-0.08 ms, GPU time 28.5 -> 26.9
    // ms a frame, the same image (runtime switch interleaved in one session,
    // 2026-10-06).
    {0x4541098E, "vulkan_direct_host_resolve_to_texture", true,
     "resolves store straight into their textures"},
    // 2D fetches with the LOD the host computes instead of 4 coarse
    // derivatives and an explicit-gradient sample: main pass 12.8 -> 11.0 ms,
    // the 640x4096 passes 2.0 -> 1.5 ms, GPU time ~26.4 -> 24.4 ms a frame,
    // the same image standing on the deck (one restart A/B pair at the same
    // temperature, 2026-10-06).
    {0x4541098E, "spirv_texture_implicit_lod", true,
     "2D texture fetches with the host's LOD"},
    // Without the Shader Model 3 "0 * x = 0" emulation and the 21-bit
    // rounding in pixel shaders, and the former in vertex shaders, as for
    // Forza Horizon: main pass 11.4 -> 9.9 ms, the 640x4096 passes 1.57 ->
    // 1.32 ms, GPU time 24.8 -> 23.5 ms a frame with the phone hotter. The
    // same image standing on the deck at night in the rain with the lighting
    // right - no black or white pixels; the colored specks flickering on the
    // pistol's emblem are there with either (restart A/B pair, 2026-10-07).
    {0x4541098E, "spirv_ps_relaxed_math", int64_t(3),
     "no SM3 zero-multiply or 21-bit rounding emulation in pixel shaders"},
    {0x4541098E, "spirv_vs_relaxed_math", int64_t(1),
     "no SM3 zero-multiply emulation in vertex shaders"},
    // It copies memory with the GPU while loading - one vertex shader
    // (D6A6A2ABFA7AF8A1) fetching 32 bytes a vertex and exporting them, about
    // 2000-2500 draws - and its CPU builds index buffers from the copies.
    // Without the export output in guest memory it read what was there
    // before, and the indices of a breakable object's mesh came out off by
    // 435-3036 vertices: read from the tangent data after its positions, its
    // triangles drew blades across the screen when shots hit inside the ship
    // (5 of 6 sessions). Read back right after each exporting draw, no blades
    // in 2 of 2 sessions, but 4.1-5.6% of the frames over 50 ms (1.7-2.1%
    // without); read back as their submissions complete, no blades and 1.4%
    // (frame dumps: the mesh's indices 0-776, positions within 2.03,
    // 2026-10-07).
    {0x4541098E, "memexport_enable", true,
     "its CPU reads what the GPU copies with memory export"},
    {0x4541098E, "memexport_await_fences", true,
     "the copies are in guest memory when the GPU signals"},
    {0x4541098E, "memexport_readback_deferred", true,
     "the copies read back as submissions complete, not per draw"},
    {0x4541098E, "readback_resolve", "uma",
     "host-mapped buffer the copies are read back from"},
    // Presented with the mailbox mode, after minutes of play SurfaceFlinger
    // took its frames at ever longer intervals (216 -> 400 ms), then none for
    // 112 s while it kept presenting ~31 a second: the image froze until the
    // swapchain was recreated (turning frame generation on, which presents
    // with FIFO). With FIFO, no such stall in ~33 minutes and the same frame
    // times (POCO F7, HyperOS, 2026-10-07).
    {0x4541098E, "vulkan_allow_present_mode_mailbox", false,
     "presentation stalls with the mailbox mode after minutes"},
};

// Same path/priority as a per-game config file.
static bool ApplyOne(const Quirk& quirk) {
  if (!cvar::ConfigVars) {
    return false;
  }
  auto it = cvar::ConfigVars->find(quirk.cvar);
  if (it == cvar::ConfigVars->end()) {
    XELOGW("Game quirk for {:08X} references unknown cvar '{}'", quirk.title_id,
           quirk.cvar);
    return false;
  }
  auto* config_var = static_cast<cvar::IConfigVar*>(it->second);
  std::visit(
      [config_var](auto&& value) {
        using V = std::decay_t<decltype(value)>;
        if constexpr (std::is_same_v<V, const char*>) {
          toml::value<std::string> node{std::string(value)};
          config_var->LoadGameConfigValue(&node);
        } else {
          toml::value<V> node{value};
          config_var->LoadGameConfigValue(&node);
        }
      },
      quirk.value);
  return true;
}

size_t Apply(uint32_t title_id) {
  size_t applied = 0;
  for (const auto& quirk : kQuirks) {
    if (quirk.title_id != title_id) {
      continue;
    }
    if (ApplyOne(quirk)) {
      XELOGI("Game quirk for {:08X}: {} ({})", title_id, quirk.cvar,
             quirk.note);
      ++applied;
    }
  }
  return applied;
}

}  // namespace game_quirks
}  // namespace xe
