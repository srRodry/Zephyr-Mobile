/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2022 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_GPU_COMMAND_PROCESSOR_H_
#define XENIA_GPU_COMMAND_PROCESSOR_H_

#include <atomic>
#include <cstring>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <queue>
#include <string>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <array>
#include <vector>

#include "xenia/base/guest_gpu_progress.h"
#include "xenia/base/math.h"
#include "xenia/base/ring_buffer.h"
#include "xenia/gpu/register_file.h"
#include "xenia/gpu/trace_writer.h"
#include "xenia/gpu/xenos.h"
#include "xenia/gpu/xenos_zpd_report.h"
#include "xenia/kernel/xthread.h"
#include "xenia/memory.h"
#include "xenia/ui/presentation_runtime.h"
#include "xenia/ui/presenter.h"

namespace xe {

class ByteStream;

namespace gpu {

enum class GPUSetting {
  ClearMemoryPageState,
  MemexportAwaitFences,
};

enum class ReadbackResolveMode {
  kDisabled,  // No readback (none)
  kFast,      // Copy only CPU-read resolves into guest RAM (fast)
  kAll,       // Copy every resolve into guest RAM (all)
  kUma        // Read host-mapped shared memory directly, no device->host copy
              // (uma). Adreno cannot import guest RAM (no external_memory_host)
              // so upstream's zero-copy never engages there; this is the
              // equivalent for such devices. Kept alongside kFast/kAll to A/B.
};
// The readback_resolve_sync cvar makes fast/all copies stall for same-frame
// coherency instead of running deferred, about a frame behind.

// Occlusion queries - ZPD report mode.
enum class ZPDMode {
  kFake,     // Fake sample counts, no real GPU queries (fake)
  kFast,     // Real queries with speculative cached writes (fast)
  kFastAlt,  // Fast queries, but preserves cached zeroes (fast-alt)
  kStrict,   // Real queries, waits before writeback. May hang. (strict)
};

void SaveGPUSetting(GPUSetting setting, uint64_t value);
bool GetGPUSetting(GPUSetting setting);

// Shared pool capacity for D3D12 and Vulkan.
constexpr uint32_t kZPDQueryPoolCapacity = 8192;

// Contiguous range of query indices for batched resolve/copy operations.
struct ResolveRange {
  uint32_t start;
  uint32_t count;
};

// Backstop for strict mode. Abandon any pending retires after this many polls
// so EVENT_WRITE_ZPD doesn't keep spinning on an unresolved report.
constexpr uint32_t kStrictZPDRetireMaxStalls = 16;
// Millisecond deadline for strict ZPD retire.
constexpr uint64_t kStrictZPDRetireDeadlineMs = 2;

// Cap for the fast-mode cached delta map.  Games reuse a small set of report
// addresses so this should never be hit, but prevents unbounded growth if a
// title cycles through unique addresses.  Clearing the cache has no
// correctness impact — it only removes speculative writeback hints.
constexpr size_t kFastZPDCacheMaxEntries = 1024;

class GraphicsSystem;
class Shader;

struct SwapState {
  // Lock must be held when changing data in this structure.
  std::mutex mutex;
  // Dimensions of the framebuffer textures. Should match window size.
  uint32_t width = 0;
  uint32_t height = 0;
  // Current front buffer, being drawn to the screen.
  uintptr_t front_buffer_texture = 0;
  // Current back buffer, being updated by the CP.
  uintptr_t back_buffer_texture = 0;
  // Backend data
  void* backend_data = nullptr;
  // Whether the back buffer is dirty and a swap is pending.
  bool pending = false;
};

enum class SwapMode {
  kNormal,
  kIgnored,
};

enum class GammaRampType {
  kUnknown = 0,
  kTable,
  kPWL,
};

class CommandProcessor {
 public:
  using ReportHandle = uint32_t;
  static constexpr ReportHandle kInvalidReportHandle = 0;

 protected:
  RingBuffer
      reader_;  // chrispy: instead of having ringbuffer on stack, have it near
                // the start of the class so we can access it via rel8. This
                // also reduces the number of params we need to pass
  // Converts the reader's host pointer (+ offset) to a guest physical address.
  uint32_t GuestReadPtrOffset(int32_t offset = 0) const;
  void WritePrimaryReadPointer(uint32_t index);
  void PublishPrimaryProgressBeforeWait();

  // merge_tiling_bands: the bands of predicated tiling (bin selects other than
  // all ones between all-ones ones) drawn as one. Every draw (resolves aside)
  // is executed in the first band it's predicated into - draws of later bands
  // repeating one of an earlier band (the same command buffer replayed) are
  // skipped, and the backend draws the first band's into render targets as
  // tall as the screen and resolves each band from its rows.
  void OnBinSelectWritten();
  // Whether to issue the draw packet just read (false - drawn in an earlier
  // band).
  bool PrepareTilingBandDraw();
  // The band being executed: 0 for the first, -1 outside the bands.
  int32_t tiling_band_ = -1;
  uint32_t tiling_band_select_ = 0;
  // Incremented when the first band starts.
  uint32_t tiling_band_sequence_ = 0;
  // Cleared by the backend (DisableTilingBandMerge) when the bands of this
  // frame can't be merged.
  bool tiling_band_merge_ok_ = false;
  // The current draw is first executed in tiling_band_ > 0.
  bool tiling_band_draw_from_later_band_ = false;
  // The draws of the current band sequence: open addressing with the
  // sequence's epoch beside each key, so a new sequence clears it in O(1) (an
  // unordered_set allocated a node per draw, ~2% of this thread's instructions
  // at ~2900 draws per frame).
  struct TilingBandDrawSet {
    std::vector<uint64_t> keys;
    std::vector<uint32_t> epochs;
    uint32_t count = 0;
    uint32_t epoch = 0;
    // Returns whether the key wasn't in the current sequence yet.
    bool Insert(uint64_t key);
    void Clear();
  };
  // By the guest address right past each draw packet, and with
  // merge_tiling_bands_call_sites the indirect buffer calls made inside the
  // replayed scene on the way to it.
  TilingBandDrawSet tiling_band_draws_;
  // merge_tiling_bands_log: by the key the setting doesn't use (the address
  // alone, or with the calls), to count where the two differ.
  TilingBandDrawSet tiling_band_draw_other_keys_;
  void ClearTilingBandDraws();
  // The indirect buffer calls being executed, outermost first: the guest
  // address right past each INDIRECT_BUFFER packet. A draw packet in a buffer
  // called from several places draws a different object from each place (a
  // routine the game runs per object, each call predicated into its own
  // bands) - with the address alone, an object only in a later band was taken
  // for one drawn in an earlier band and never drawn (whole blocks of scenery
  // missing in Forza Horizon 2 until they came close). The calls made at the
  // depth the first band's bin select was written at - each band calling the
  // scene again - are left out, so a band still matches the earlier ones.
  static constexpr uint32_t kTilingBandMaxCallDepth = 16;
  std::array<uint32_t, kTilingBandMaxCallDepth> ib_call_sites_{};
  uint32_t ib_call_depth_ = 0;
  uint32_t tiling_band_call_depth_ = 0;
  // merge_tiling_bands_log: per band sequence, the draws looked at, the ones
  // skipped as drawn in an earlier band, and the ones in a later band the
  // address alone takes for an earlier band's draw while the calls tell apart
  // (drawn with merge_tiling_bands_call_sites, lost without); and their sums
  // over the sequences since the last report.
  uint32_t tiling_band_stat_draws_ = 0;
  uint32_t tiling_band_stat_skipped_ = 0;
  uint32_t tiling_band_stat_rescued_ = 0;
  uint64_t tiling_band_stat_sum_draws_ = 0;
  uint64_t tiling_band_stat_sum_skipped_ = 0;
  uint64_t tiling_band_stat_sum_rescued_ = 0;
  uint32_t tiling_band_stat_sequences_ = 0;

 public:
  bool tiling_band_merge_active() const {
    return tiling_band_ >= 0 && tiling_band_merge_ok_;
  }
  int32_t tiling_band() const { return tiling_band_; }
  uint32_t tiling_band_sequence() const { return tiling_band_sequence_; }
  bool tiling_band_draw_from_later_band() const {
    return tiling_band_draw_from_later_band_;
  }
  void DisableTilingBandMerge() { tiling_band_merge_ok_ = false; }

 public:
  enum class SwapPostEffect {
    kNone,
    kFxaa,
    kFxaaExtreme,
  };

  CommandProcessor(GraphicsSystem* graphics_system,
                   kernel::KernelState* kernel_state);
  virtual ~CommandProcessor();
  uint32_t counter() const { return counter_; }
  void increment_counter() { counter_++; }

  Shader* active_vertex_shader() const { return active_vertex_shader_; }
  Shader* active_pixel_shader() const { return active_pixel_shader_; }
  uint32_t active_vertex_shader_ucode_address() const {
    return active_vertex_shader_ucode_address_;
  }

  virtual bool Initialize();
  virtual void Shutdown();

  virtual std::string GetTitleStateSuffix() const { return {}; }

  void CallInThread(std::function<void()> fn);

  virtual void ClearCaches();
  virtual void InvalidateGpuMemory();
  virtual void ClearReadbackBuffers();

  // Get cached readback resolve mode (avoids string parsing every frame)
  ReadbackResolveMode GetReadbackResolveMode() const {
    return cached_readback_resolve_mode_;
  }

  // Set readback resolve mode (updates both cvar and cached value). With
  // persist, also saved to the per-game config of the running title (a user
  // choice); run-time debug overrides pass false.
  void SetReadbackResolveMode(ReadbackResolveMode mode, bool persist = true);

  // Get cached ZPD mode (avoids string parsing every frame).
  ZPDMode GetZPDMode() const { return cached_zpd_mode_; }
  // Set ZPD mode (updates both cvar and cached value).
  void SetZPDMode(ZPDMode mode);

  // "Desired" is for the external thread managing the post-processing effect.
  SwapPostEffect GetDesiredSwapPostEffect() const {
    return swap_post_effect_desired_;
  }
  void SetDesiredSwapPostEffect(SwapPostEffect swap_post_effect);
  // Implementations must not make assumptions that the front buffer will
  // necessarily be a resolve destination - it may be a texture generated by any
  // means like written to by the CPU or loaded from a file (the disclaimer
  // screen right in the beginning of 4D530AA4 is not a resolved render target,
  // for instance).
  virtual void IssueSwap(uint32_t frontbuffer_ptr, uint32_t frontbuffer_width,
                         uint32_t frontbuffer_height) {}

  // Throttle presentation based on framerate_limit cvar.
  // Called after IssueSwap to limit host frame rate without affecting guest
  // vblank timing.
  void ThrottlePresentation();

  // May be called not only from the command processor thread when the command
  // processor is paused, and the termination of this function may be explicitly
  // awaited.
  virtual void InitializeShaderStorage(
      const std::filesystem::path& cache_root, uint32_t title_id, bool blocking,
      std::function<void()> completion_callback = nullptr);

  virtual void RequestFrameTrace(const std::filesystem::path& root_path);
  virtual void BeginTracing(const std::filesystem::path& root_path);
  virtual void EndTracing();

  virtual void TracePlaybackWroteMemory(uint32_t base_ptr, uint32_t length) = 0;

  // Shadowed by backends that route memory export through guest RAM (see
  // command_processor_memexport.inc). No-ops where export output never reaches
  // the CPU, so there is nothing to wait for.
  void AwaitMemexportForFence(const char* reason) {}
  void AwaitMemexportForCoherency(uint32_t base_bytes, uint32_t size_bytes) {}
  bool AwaitMemexportReadbacks(const char* reason) { return false; }
  bool MemexportReadbacksPending() const { return false; }

  void RestoreRegisters(uint32_t first_register,
                        const uint32_t* register_values,
                        uint32_t register_count, bool execute_callbacks);
  void RestoreGammaRamp(
      const reg::DC_LUT_30_COLOR* new_gamma_ramp_256_entry_table,
      const reg::DC_LUT_PWL_DATA* new_gamma_ramp_pwl_rgb,
      uint32_t new_gamma_ramp_rw_component);
  virtual void RestoreEdramSnapshot(const void* snapshot) = 0;

  void InitializeRingBuffer(uint32_t ptr, uint32_t size_log2);
  void EnableReadPointerWriteBack(uint32_t ptr, uint32_t block_size_log2);

  void UpdateWritePointer(uint32_t value);

  void LogRegisterSet(uint32_t register_index, uint32_t value);
  void LogRegisterSets(uint32_t base_register_index, const uint32_t* values,
                       uint32_t n_values);

  bool is_paused() const { return paused_; }
  void Pause();
  void Resume();
  // Times Resume() ended a pause, for state to restore after one.
  uint32_t resume_count() const {
    return resume_count_.load(std::memory_order_relaxed);
  }

  bool Save(ByteStream* stream);
  bool Restore(ByteStream* stream);

 protected:
  struct IndexBufferInfo {
    xenos::IndexFormat format = xenos::IndexFormat::kInt16;
    xenos::Endian endianness = xenos::Endian::kNone;
    uint32_t count = 0;
    uint32_t guest_base = 0;
    size_t length = 0;
  };

  static constexpr uint32_t kReadbackBufferSizeIncrement = 16 * 1024 * 1024;

  // Progressive alignment for readback buffers to avoid wasting memory
  static inline uint32_t AlignReadbackBufferSize(uint32_t size) {
    if (size < 1 * 1024 * 1024) {
      return xe::align(size, 256u * 1024u);  // 256KB for < 1MB
    } else if (size < 4 * 1024 * 1024) {
      return xe::align(size, 1u * 1024u * 1024u);  // 1MB for < 4MB
    } else {
      return xe::align(size, kReadbackBufferSizeIncrement);  // 16MB for >= 4MB
    }
  }

  // Generate a cache key for a specific resolve operation
  static inline uint64_t MakeReadbackResolveKey(uint32_t address,
                                                uint32_t length) {
    return (uint64_t(address) << 32) | uint64_t(length);
  }

  // Per-guest-frame time breakdown on the worker thread, reported through
  // XELOGI roughly once per second when log_gpu_frame_time_breakdown is set.
  // All accounting happens on the worker thread - no synchronization.
  struct FrameTimeStats {
    uint64_t frames = 0;
    uint64_t draws = 0;
    uint64_t draw_ns = 0;
    uint64_t swap_ns = 0;
    uint64_t exec_ns = 0;      // ExecutePrimaryBuffer total
    uint64_t stall_ns = 0;     // worker idle, waiting for ring writes
    // PM4_WAIT_REG_MEM found unmet on the first check, and the time until met.
    uint64_t wait_reg_mem_unmet = 0;
    uint64_t wait_reg_mem_ns = 0;
    uint64_t interval_ns = 0;  // sum of swap-to-swap intervals
    uint64_t interval_max_ns = 0;
    // Intervals over 37, 50 and 70 ms (1.1x, 1.5x and 2.1x the 33.3 ms of a
    // 30 fps frame): the stutter an average hides.
    uint64_t long_intervals[3] = {};
    uint64_t last_swap_ns = 0;
    uint64_t last_report_ns = 0;
  };
  FrameTimeStats frame_time_stats_;
  static uint64_t FrameStatsNow();
  // Returns 0 (and skips the clock read) when the breakdown is disabled.
  uint64_t FrameStatsBegin();
  // The same for the waits (for ring writes and WAIT_REG_MEM), also timed for
  // gpu_performance_hint.
  uint64_t FrameWaitBegin();
  void FrameStatsEndDraw(uint64_t begin_ns);
  void FrameStatsEndWaitRegMem(uint64_t begin_ns);
  void FrameStatsEndSwap(uint64_t begin_ns);

  // gpu_performance_hint (Android ADPF): the command thread's work per guest
  // frame - the frame interval minus its waits - reported at each swap.
  void FrameHintEndFrame();
  uint64_t frame_hint_wait_ns_ = 0;
  uint64_t frame_hint_last_swap_ns_ = 0;
  uint64_t frame_hint_interval_sum_ns_ = 0;
  uint32_t frame_hint_interval_count_ = 0;
  void* frame_hint_session_ = nullptr;
  bool frame_hint_failed_ = false;
  int (*frame_hint_report_)(void*, int64_t) = nullptr;
  int (*frame_hint_update_target_)(void*, int64_t) = nullptr;

  // Predicated tiling diagnostics: `adb shell setprop debug.xendroid.
  // pm4_bin_trace N` logs how the next N guest frames use bin select / bin
  // mask predication, screen extent events and indirect buffers (set a
  // different N to trigger again). Worker thread only; a single branch per
  // hooked packet when idle.
  struct BinTrace {
    uint32_t frames_left = 0;
    uint32_t frame_number = 0;
    int32_t last_property_value = -1;
    uint32_t frames_since_poll = 0;
    // debug.xendroid.fake_extents experiment: 0 - the usual full-screen
    // screen extents, 1..3 - report every draw as covering only the rows of
    // band 1..3 (256 each), 4 - an empty extent.
    uint32_t fake_extents_mode = 0;
    uint32_t interrupts = 0;
    uint32_t wait_reg_mems = 0;
    // <packet address, bin select, mask> for masks set this frame.
    std::vector<std::tuple<uint32_t, uint64_t, uint64_t>> mask_writes;
    uint32_t draws = 0;
    uint32_t draws_predicated = 0;
    uint32_t draws_predicated_skipped = 0;
    uint32_t draws_since_select = 0;
    uint32_t packets_predicated = 0;
    uint32_t packets_predicated_skipped = 0;
    uint32_t indirect_buffers = 0;
    uint32_t indirect_buffers_predicated = 0;
    uint32_t indirect_buffers_predicated_skipped = 0;
    uint64_t indirect_buffer_dwords = 0;
    uint32_t bin_mask_writes = 0;
    uint32_t bin_select_writes = 0;
    std::vector<uint32_t> extent_addresses;
    std::vector<std::pair<uint32_t, uint32_t>> indirect_buffer_ranges;
    // <bin select, executed draws> and <bin mask, executed predicated draws>.
    std::vector<std::pair<uint64_t, uint32_t>> draws_by_select;
    std::vector<std::pair<uint64_t, uint32_t>> draws_by_mask;
    std::string sequence;
  };
  BinTrace bin_trace_;
  // debug.xendroid.wrm_log: unmet PM4_WAIT_REG_MEM waits still to be logged.
  uint32_t wrm_log_left_ = 0;
  int32_t wrm_log_last_property_ = -1;
  // debug.xendroid.wrm_log: for a logged memory wait, the guest write that
  // satisfied it, caught by a physical write watch armed on the waited page
  // (the callback runs in the writing guest thread's fault handler, so it only
  // stores what it saw; the command processor maps it to guest code).
  struct WrmWriterEvent {
    uint64_t host_pc;
    uint32_t guest_thread;
    uint64_t time_ns;
  };
  static std::pair<uint32_t, uint32_t> WrmWriterWatchCallback(
      void* context_ptr, uint32_t physical_address_start, uint32_t length,
      bool exact_range);
  void WrmWriterArm(uint32_t physical_address);
  // Disarms and describes the write (who, where in guest code, how long
  // before the wait noticed it).
  std::string WrmWriterTake();
  void* wrm_writer_callback_handle_ = nullptr;
  std::atomic<uint32_t> wrm_writer_address_{UINT32_MAX};
  std::atomic<bool> wrm_writer_hit_{false};
  WrmWriterEvent wrm_writer_event_ = {};
  void BinTracePoll();
  void BinTraceOpcode(uint32_t opcode);
  void BinTraceSetBin(bool is_select, uint32_t packet_guest_address);
  void BinTracePacket(uint32_t opcode, bool predicated, bool executed);
  void BinTraceDraw(bool predicated, bool executed);
  void BinTraceIndirectBuffer(uint32_t address, uint32_t dwords,
                              bool predicated, bool executed);
  void BinTraceExtents(uint32_t address);
  // Appends "<tag><a>x<b>" to the first traced frame's sequence.
  void BinTraceNote(const char* tag, uint32_t a, uint32_t b);
  void BinTraceEndFrame();

  // Constants for the shared resolve-downscale compute shader (used by the
  // D3D12 and Vulkan backends to downscale a scaled resolve back to 1x).
  struct ResolveDownscaleConstants {
    uint32_t scale_x;          // 1 to kMaxDrawResolutionScaleAlongAxis
    uint32_t scale_y;          // 1 to kMaxDrawResolutionScaleAlongAxis
    uint32_t pixel_size_log2;  // 0=8bit, 1=16bit, 2=32bit, 3=64bit
    uint32_t tile_count;       // Number of 32x32 tiles to process
    // Byte offset into the source buffer. On D3D12 this is 0 (the offset is
    // baked into the source SRV); on Vulkan it is the real byte offset.
    uint32_t source_offset_bytes;
    // When non-zero, apply half-pixel offset correction by sampling from
    // (scale/2, scale/2) within each scaled block instead of (0, 0).
    uint32_t half_pixel_offset;
  };

  void WorkerThreadMain();
  virtual bool SetupContext() = 0;
  virtual void ShutdownContext() = 0;
  // rarely needed, most register writes have no special logic here
  XE_NOINLINE
  void HandleSpecialRegisterWrite(uint32_t index, uint32_t value);

  virtual void WriteRegister(uint32_t index, uint32_t value);

  // mem has big-endian register values
  XE_FORCEINLINE
  virtual void WriteRegistersFromMem(uint32_t start_index, uint32_t* base,
                                     uint32_t num_registers);

  XE_FORCEINLINE
  virtual void WriteRegisterRangeFromRing(xe::RingBuffer* ring, uint32_t base,
                                          uint32_t num_registers);

  XE_NOINLINE
  void WriteOneRegisterFromRing(
      uint32_t base,
      uint32_t
          num_times);  // repeatedly write a value to one register, presumably a
                       // register with special handling for writes

  void WriteALURangeFromRing(xe::RingBuffer* ring, uint32_t base,
                             uint32_t num_times);

  void WriteFetchRangeFromRing(xe::RingBuffer* ring, uint32_t base,
                               uint32_t num_times);

  void WriteBoolRangeFromRing(xe::RingBuffer* ring, uint32_t base,
                              uint32_t num_times);

  void WriteLoopRangeFromRing(xe::RingBuffer* ring, uint32_t base,
                              uint32_t num_times);

  void WriteREGISTERSRangeFromRing(xe::RingBuffer* ring, uint32_t base,
                                   uint32_t num_times);

  void WriteALURangeFromMem(uint32_t start_index, uint32_t* base,
                            uint32_t num_registers);

  void WriteFetchRangeFromMem(uint32_t start_index, uint32_t* base,
                              uint32_t num_registers);

  void WriteBoolRangeFromMem(uint32_t start_index, uint32_t* base,
                             uint32_t num_registers);

  void WriteLoopRangeFromMem(uint32_t start_index, uint32_t* base,
                             uint32_t num_registers);

  void WriteREGISTERSRangeFromMem(uint32_t start_index, uint32_t* base,
                                  uint32_t num_registers);

  const reg::DC_LUT_30_COLOR* gamma_ramp_256_entry_table() const {
    return gamma_ramp_256_entry_table_;
  }
  const reg::DC_LUT_PWL_DATA* gamma_ramp_pwl_rgb() const {
    return gamma_ramp_pwl_rgb_[0];
  }
  virtual void OnGammaRamp256EntryTableValueWritten() {}
  virtual void OnGammaRampPWLValueWritten() {}

  virtual void MakeCoherent();
  virtual void PrepareForWait();
  virtual void ReturnFromWait();

  virtual void PollCompletedSubmission() {}

  // Used by strict ZPD to distinguish normal in flight latency from a
  // genuinely stuck report.
  virtual uint64_t GetCompletedSubmission() const { return 0; }

  virtual void OnPrimaryBufferEnd() {}

  // TODO(boma): Add tracking for VIZ & EXT queries.
  enum class QueryOpenResult {
    kOpened,
    kDeferred,
    kPoolExhausted,
    kFailed,
  };

  // One active guest report slot. May span multiple host query segments split
  // across submissions or render passes, final value is the normalized sum.
  struct ZPDReport {
    // Guest sample count. Each segment is normalized by its own scale area
    // when it resolves.
    uint64_t accumulated_samples = 0;
    // Submission of the first closed segment.
    uint64_t first_segment_end_submission = 0;
    // Submission containing the most recently closed segment's resolve.
    uint64_t last_segment_end_submission = 0;
    uint64_t slot_sequence_id = 0;
    uint32_t slot_base = 0;
    uint32_t begin_record = 0;
    uint32_t end_record = 0;
    // Snapshotted at BEGIN from zpd_slot_values_.
    uint32_t begin_value = 0;
    uint32_t pending_segments = 0;
    // Last known delta. Carried forward on forced close so slot doesn't
    // briefly look fully occluded. 0 is a valid delta for alternate fast path.
    uint32_t cached_delta = 0;
    // Distinguishes "we resolved to zero" from "we have no cached value yet".
    bool has_cached_delta = false;
    bool ended = false;
  };

  // TODO(boma): Replace with a map keyed by slot_base for concurrent slots.
  struct ActiveZPDSegment {
    ReportHandle report_handle = kInvalidReportHandle;
    uint32_t slot_base = 0;
    uint32_t begin_record = 0;
    uint32_t end_record = 0;
    uint32_t scale_area = 0;
    bool segment_active = false;
    bool segment_pending_begin = false;
    bool logical_active = false;
  };

  struct PendingZPDSlot {
    ReportHandle report_handle = kInvalidReportHandle;
    uint32_t cached_delta = 0;
    bool has_cached_delta = false;
  };

  // Logged by the backend every 100 frames if ZPD logging cvar is true.
  struct ZPDStats {
    uint64_t logical_begun = 0;
    uint64_t logical_ended = 0;
    uint64_t segments_begun = 0;
    uint64_t segments_ended = 0;
    uint64_t pool_exhausted = 0;
    uint64_t failed = 0;
    uint64_t counter_wraps = 0;
    uint64_t same_slot_reuse = 0;
    uint64_t last_log_frame = 0;

    void Reset(uint64_t current_frame) {
      *this = {};
      last_log_frame = current_frame;
    }
  };

  virtual void EnsureZPDQueryResources() {}
  virtual void ShutdownZPDQueryResources() {}

  virtual bool IsZPDQueryPoolReady() const { return false; }
  virtual bool CanOpenZPDQuery() const { return true; }

  // Backend acquires a pool slot, records BeginQuery, tracks it internally.
  virtual QueryOpenResult OpenZPDQuery(ReportHandle report_handle,
                                       bool can_close_submission) {
    return QueryOpenResult::kFailed;
  }
  // Backend records EndQuery, queues a resolve for the active slot.
  virtual bool CloseZPDQuery(ReportHandle report_handle,
                             uint64_t& out_submission) {
    return false;
  }
  // Backend discards the active query without resolving.
  virtual bool DiscardZPDQuery() { return false; }

  // Backend drains completed resolves and calls OnZPDQueryResolved for each.
  virtual void PumpQueryResolves() {}
  // Backend waits for all pending segments of report_handle to resolve.
  virtual bool AwaitQueryResolve(ReportHandle report_handle,
                                 uint64_t wait_for_submission) {
    return false;
  }

  bool BeginZPDReport(uint32_t report_address);
  bool EndZPDReport(uint32_t report_address, bool guest_forced_end);
  // Opens a new host query segment when CanOpenZPDQuery is true.
  void OpenQuerySegment(bool can_close_submission);
  // Closes the current segment at a submission or render pass boundary.
  // The logical report stays open and a new segment will open at the next
  // opportunity.
  void CloseQuerySegment();
  // Splits the open segment when the draw scale changes so each segment
  // normalizes with one scale.
  void UpdateZPDScale(uint32_t scale_area);
  // Or-ed into the scale area: the host passes half the samples the guest
  // would (4x MSAA stored at 2x) - the count is doubled before the division.
  static constexpr uint32_t kZPDScaleHalfSamples = UINT32_C(1) << 31;

  // Called by backends when a host query resolve completes.  Accumulates
  // the normalized sample count, and if all segments are done, commits the
  // report to guest memory.
  void OnZPDQueryResolved(ReportHandle report_handle, uint64_t raw_samples,
                          uint32_t scale_area);

  // Writes guest report with begin_value read from guest memory.
  // Orphan END path only when no controller snapshot is available.
  void WriteZPDReport(uint32_t begin_record, uint32_t end_record,
                      uint32_t begin_value, uint32_t delta_value,
                      bool write_begin_record);

  // Called from PrepareForWait so strict mode can retire before guest loops
  // again. Gives up after kStrictZPDRetireMaxStalls.
  void PumpPendingRetire();

  // Divides a segment's host count by the scale area it ran under.
  static uint32_t NormalizeSampleCount(uint64_t samples, uint32_t scale_area);

  // Writes the final report to guest memory and advances the slot running
  // total.  Called when a report fully resolves or is abandoned.
  void CommitZPDReport(ZPDReport& report, uint32_t delta_value);
  // Checks that the report's slot sequence is still current (not reused).
  bool IsZPDReportCurrent(const ZPDReport& report) const;
  PendingZPDSlot GetPendingZPDSlot(uint32_t slot_base,
                                   uint32_t end_record) const;

  void ResetZPDState() {
    zpd_active_segment_ = {};
    zpd_next_report_handle_ = 1;
    zpd_slot_sequences_.clear();
    zpd_slot_values_.clear();
    logical_zpd_reports_.clear();
    fast_zpd_report_cached_values_.clear();
    fake_zpd_sample_count_ = 0;
    querybatch_zpd_sample_count_ = UINT32_MAX;
    zpd_pending_retire_handle_ = kInvalidReportHandle;
    zpd_pending_retire_stalls_ = 0;
    zpd_pending_retire_start_ms_ = 0;
    zpd_force_fake_fallback_ = false;
  }

#include "pm4_command_processor_declare.h"

  virtual Shader* LoadShader(xenos::ShaderType shader_type,
                             const uint32_t* host_address,
                             uint32_t dword_count) {
    return nullptr;
  }

  virtual bool IssueDraw(xenos::PrimitiveType prim_type, uint32_t index_count,
                         IndexBufferInfo* index_buffer_info,
                         bool major_mode_explicit) {
    return false;
  }
  virtual bool IssueCopy() { return false; }
  bool ShouldLogDrawFailure() const { return true; }

  // Debug marker stubs for base class (overridden by D3D12/Vulkan backends).
  bool debug_markers_enabled() const { return false; }
  void PushDebugMarker(const char* format, ...) {}
  void PopDebugMarker() {}
  void InsertDebugMarker(const char* format, ...) {}

  // "Actual" is for the command processor thread, to be read by the
  // implementations.
  SwapPostEffect GetActualSwapPostEffect() const {
    // The in-game menu's antialiasing wins over the Settings value while set; the
    // FXAA pipelines exist from setup, so it can change between two swaps.
    const int antialiasing =
        ui::RuntimePresentation().antialiasing.load(std::memory_order_relaxed);
    if (antialiasing >= 0 && antialiasing <= 2) {
      return SwapPostEffect(antialiasing);
    }
    return swap_post_effect_actual_;
  }

  virtual void InitializeTrace();

  Memory* memory_ = nullptr;
  kernel::KernelState* kernel_state_ = nullptr;
  GraphicsSystem* graphics_system_ = nullptr;
  RegisterFile* XE_RESTRICT register_file_ = nullptr;

  ReportHandle zpd_next_report_handle_ = 1;
  std::unordered_map<uint32_t, uint64_t> zpd_slot_sequences_;
  std::unordered_map<uint32_t, uint32_t> zpd_slot_values_;
  std::unordered_map<ReportHandle, ZPDReport> logical_zpd_reports_;
  ActiveZPDSegment zpd_active_segment_{};

  // Cached delta per END.
  // Fast mode uses this for speculative writeback and orphaned END replay.
  std::unordered_map<uint32_t, uint32_t> fast_zpd_report_cached_values_;

  uint32_t querybatch_zpd_sample_count_ = UINT32_MAX;

  // Sticky after host pool init failure. Forces EVENT_WRITE_ZPD onto the fake
  // path so guests don't stall waiting on a pending sentinel that will never
  // be written. Cleared by ResetZPDState.
  bool zpd_force_fake_fallback_ = false;

  // Strict mode defers guest completion until the queued END has retired.
  ReportHandle zpd_pending_retire_handle_ = kInvalidReportHandle;
  uint32_t zpd_pending_retire_stalls_ = 0;
  // Uptime in ms when zpd_pending_retire_handle_ was first set.
  uint64_t zpd_pending_retire_start_ms_ = 0;

  // Set by the backend when resolution scale changes.
  uint32_t zpd_draw_resolution_scale_x_ = 1;
  uint32_t zpd_draw_resolution_scale_y_ = 1;

  uint32_t zpd_draw_resolution_scale_x() const {
    return zpd_draw_resolution_scale_x_;
  }
  uint32_t zpd_draw_resolution_scale_y() const {
    return zpd_draw_resolution_scale_y_;
  }
  // Scale area for the segment being closed.
  uint32_t GetZPDScaleArea() const {
    return zpd_active_segment_.scale_area
               ? zpd_active_segment_.scale_area
               : zpd_draw_resolution_scale_x_ * zpd_draw_resolution_scale_y_;
  }

  uint32_t fake_zpd_sample_count_ = 0;
  ZPDStats zpd_stats_;

  TraceWriter trace_writer_;
  enum class TraceState {
    kDisabled,
    kStreaming,
    kSingleFrame,
  };
  TraceState trace_state_ = TraceState::kDisabled;
  std::filesystem::path trace_stream_path_;
  std::filesystem::path trace_frame_path_;

  std::atomic<bool> worker_running_;
  kernel::object_ref<kernel::XHostThread> worker_thread_;

  // Cross-thread call queue: producers push under the mutex; the worker's hot
  // loop polls only the atomic flag.
  std::queue<std::function<void()>> pending_fns_;
  std::mutex pending_fns_mutex_;
  std::atomic<bool> has_pending_fns_{false};

  // MicroEngine binary from PM4_ME_INIT
  std::vector<uint32_t> me_bin_;

  uint32_t counter_ = 0;

  uint32_t primary_buffer_ptr_ = 0;
  uint32_t primary_buffer_size_ = 0;

  uint32_t read_ptr_index_ = 0;
  uint32_t read_ptr_update_freq_ = 0;
  uint32_t read_ptr_writeback_ptr_ = 0;
  bool primary_buffer_active_ = false;
  bool primary_wait_writeback_logged_ = false;
  // Primary cursor after consuming an outer INDIRECT_BUFFER packet. reader_
  // describes the indirect list, not the primary ring, while executing it.
  std::optional<uint32_t> primary_indirect_read_index_;

  std::unique_ptr<xe::threading::Event> write_ptr_index_event_;
  std::atomic<uint32_t> write_ptr_index_;

  uint64_t bin_select_ = 0xFFFFFFFFull;
  uint64_t bin_mask_ = 0xFFFFFFFFull;

  Shader* active_vertex_shader_ = nullptr;
  Shader* active_pixel_shader_ = nullptr;
  // Guest physical address the active vertex shader's ucode was loaded from,
  // for reading it back from shared memory (the ucode interpreter placeholder).
  // 0 if unknown (loaded immediately, embedded in the command buffer).
  uint32_t active_vertex_shader_ucode_address_ = 0;

  std::atomic<bool> paused_{false};
  std::atomic<uint32_t> resume_count_{0};
  std::unique_ptr<xe::threading::Event> pause_resume_event_;

  // By default (such as for tools), post-processing is disabled.
  // "Desired" is for the external thread managing the post-processing effect.
  SwapPostEffect swap_post_effect_desired_ = SwapPostEffect::kNone;
  SwapPostEffect swap_post_effect_actual_ = SwapPostEffect::kNone;

  // Cached readback resolve mode (parsed once from string cvar)
  ReadbackResolveMode cached_readback_resolve_mode_ =
      ReadbackResolveMode::kUma;

  // Cached ZPD occlusion query mode (defaults to fake)
  ZPDMode cached_zpd_mode_ = ZPDMode::kFake;

  // For host frame rate limiting at IssueSwap
  uint64_t last_swap_time_ = 0;

 private:
  reg::DC_LUT_30_COLOR gamma_ramp_256_entry_table_[256] = {};
  reg::DC_LUT_PWL_DATA gamma_ramp_pwl_rgb_[128][3] = {};
  uint32_t gamma_ramp_rw_component_ = 0;

  XE_NOINLINE XE_COLD void LogKickoffInitator(uint32_t value);
};

}  // namespace gpu
}  // namespace xe

#endif  // XENIA_GPU_COMMAND_PROCESSOR_H_
