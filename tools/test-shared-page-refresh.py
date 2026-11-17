#!/usr/bin/env python3
"""Execute real page-validity methods against a full-refresh reference model."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=root / 'emulator-core/src/main/cpp/xenia/src/xenia/gpu/shared_memory.cc')
parser.add_argument('--expect-regression', action='store_true')
args = parser.parse_args()
source = args.source.read_text()

def method(marker):
    begin = source.index(marker)
    brace = source.index('{', begin)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[begin:end]

code = r'''
#include <algorithm>
#include <array>
#include <bit>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <mutex>
#include <random>
#include <vector>
namespace xe {
unsigned bit_count(uint64_t value) {return std::popcount(value);}
bool bit_scan_forward(uint64_t value, uint32_t* result) {
 if (!value) return false; *result = std::countr_zero(value); return true;
}
}
struct SharedMemory {
 static constexpr uint32_t kBufferSize = 512u * 1024 * 1024;
 struct Region {std::mutex mutex; auto Acquire() {return std::unique_lock(mutex);}} global_critical_region_;
 std::array<uint64_t, 2048> valid{}, gpu{}, invalid{}, exempt{};
 uint64_t* system_page_flags_valid_ = valid.data();
 uint64_t* system_page_flags_valid_and_gpu_written_ = gpu.data();
 uint64_t* system_page_flags_invalidated_in_submission_ = invalid.data();
 uint64_t* system_page_flags_preserve_exempt_ = exempt.data();
 bool gpu_written_data_dirty_ = false;
 uint32_t dirty_blocks_ = 0;
 uint64_t dirty_entries_[32] = {};
 unsigned num_system_page_flags_ = 2048;
 uint32_t page_size_log2_ = 12;
 struct PageHistory {uint64_t gpu_write = 0, upload = 0; uint32_t gpu_write_start = 0, gpu_write_length = 0;};
 std::vector<PageHistory> page_history_;
 template<auto M> void NotePages(uint32_t, uint32_t) {}
 bool memory_invalidation_callback_handle_ = false;
 struct Memory {void EnablePhysicalMemoryAccessCallbacks(uint32_t, uint32_t, bool, bool) {}} memory_;
 Memory& memory() {return memory_;}
 uint64_t refresh_reads = 0;
 uint64_t ReadGpu(uint32_t i) {++refresh_reads; return gpu[i];}
 void InvalidateAllPages();
 void SetSystemPageBlocksValidWithGpuDataWritten();
 void MakeRangeValid(uint32_t, uint32_t, bool, bool);
};
INVALIDATE
REFRESH
MAKE_VALID
int main() {
 for (uint32_t page_shift : {12u, 14u, 16u}) {
  SharedMemory s;
  s.page_size_log2_ = page_shift;
  const uint32_t page_count = SharedMemory::kBufferSize >> page_shift;
  s.num_system_page_flags_ = page_count / 64;
  std::array<uint64_t, 2048> expected_valid{}, expected_gpu{}, expected_invalid{};
  std::mt19937 random(8475 + page_shift);
  auto upload = [&](uint32_t first, uint32_t count, bool gpu, bool read_back) {
    s.MakeRangeValid(first << page_shift, count << page_shift, gpu, read_back);
    for (uint32_t page = first; page < first + count; ++page) {
      const uint64_t bit = uint64_t(1) << (page & 63);
      expected_valid[page / 64] |= bit;
      if (gpu) expected_gpu[page / 64] |= bit;
      else expected_gpu[page / 64] &= ~bit;
    }
  };
  auto refresh = [&] {
    for (uint32_t i = 0; i < s.num_system_page_flags_; ++i) {
      expected_invalid[i] |= expected_valid[i] & ~expected_gpu[i];
      expected_valid[i] = expected_gpu[i];
    }
    s.refresh_reads = 0;
    s.SetSystemPageBlocksValidWithGpuDataWritten();
    if (s.valid != expected_valid || s.gpu != expected_gpu || s.invalid != expected_invalid) return false;
    return true;
  };
  // A CPU-only upload must be eligible for refresh even without a GPU write.
  upload(2, 1, false, false);
  if (!refresh()) {std::puts("REGRESSION: CPU-only uploaded page remained valid after refresh"); return 1;}
  if (s.refresh_reads != 1) {std::puts("REGRESSION: localized upload scanned unchanged masks"); return 1;}
  // GPU-only writes need no refresh; CPU dirtiness must survive a GPU write.
  upload(1, 1, true, false);
  if (!refresh() || s.refresh_reads != 0) return 1;
  upload(2, 1, false, false);
  upload(2, 1, true, true);
  if (!refresh()) return 1;
  // GPU writes in chunk 0 must not hide CPU uploads in another chunk.
  upload(1, 1, true, false);
  upload(4097, 1, false, false);
  if (!refresh()) {std::puts("REGRESSION: CPU upload outside GPU-dirty chunk was skipped"); return 1;}
  // Uploading over a previously GPU-written page must not preserve its old mask.
  upload(1, 1, false, false);
  if (!refresh()) return 1;
  // Cover sparse entries spread over every coarse chunk.
  for (uint32_t first = 0; first < page_count; first += 4096) upload(first, 1, false, false);
  if (!refresh()) return 1;
  if (s.refresh_reads != (page_count + 4095) / 4096) return 1;
  // Reset with pending dirtiness must not leave stale entries or access guards.
  upload(page_count - 1, 1, false, false);
  s.InvalidateAllPages();
  expected_valid.fill(0); expected_gpu.fill(0);
  for (uint32_t i = 0; i < s.num_system_page_flags_; ++i) expected_invalid[i] = UINT64_MAX;
  if (!refresh() || s.refresh_reads != s.num_system_page_flags_) return 1;
  // Full-range uploads exercise the dense linear path and array boundaries.
  upload(0, page_count, false, false);
  if (!refresh()) return 1;
  upload(0, page_count, true, false);
  if (!refresh()) return 1;
  for (int step = 0; step < 20000; ++step) {
    const uint32_t first = random() % page_count;
    const uint32_t count = std::min(uint32_t(1 + random() % 192), page_count - first);
    upload(first, count, random() & 1, random() & 1);
    if (step % 7 == 0 && !refresh()) {std::printf("REGRESSION: mixed lifecycle at step %d\n", step); return 1;}
    if (step % 31 == 0) {s.invalid.fill(0); expected_invalid.fill(0);}
  }
  if (!refresh()) return 1;
 }
 std::puts("Page refresh: CPU-only/cross-chunk/overwrite regressions and reset/dense/sparse work bounds and 60,000 mixed updates passed at 4/16/64 KiB page sizes.");
}
'''.replace('INVALIDATE', method('void SharedMemory::InvalidateAllPages()')).replace('REFRESH', method('void SharedMemory::SetSystemPageBlocksValidWithGpuDataWritten()').replace('system_page_flags_valid_and_gpu_written_[i]', 'ReadGpu(i)')).replace('MAKE_VALID', method('void SharedMemory::MakeRangeValid('))
with tempfile.TemporaryDirectory(prefix='zephyr-page-refresh-') as directory:
    path = Path(directory)
    (path / 'test.cc').write_text(code)
    compiler = os.environ.get('CXX') or shutil.which('clang++') or 'g++'
    subprocess.run([compiler, '-std=c++20', '-O2', '-fsanitize=undefined',
                    '-fno-sanitize-recover=undefined', '-pthread', str(path / 'test.cc'),
                    '-o', str(path / 'test')], check=True)
    result = subprocess.run([str(path / 'test')], check=False)
    if args.expect_regression:
        assert result.returncode == 1, 'The old implementation did not reproduce the expected defect'
        print('Confirmed: previous production methods fail the regression before any fix.')
    else:
        result.check_returncode()
