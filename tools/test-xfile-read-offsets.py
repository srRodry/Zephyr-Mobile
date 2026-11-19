#!/usr/bin/env python3
"""Exercise the production XFile read and scatter paths with synthetic bytes."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "emulator-core/src/main/cpp/xenia/src/xenia/kernel/xfile.cc").read_text()


def extract(start, end):
    return SOURCE[SOURCE.index(start):SOURCE.index(end)]


methods = (
    extract("void XFile::set_position", "X_STATUS XFile::QueryDirectory(")
    + extract("X_STATUS XFile::ReadInternal", "X_STATUS XFile::ReadScatter(")
    + extract("X_STATUS XFile::ReadScatterInternal", "X_STATUS XFile::Write(")
)
harness = r'''
#include <algorithm>
#include <array>
#include <atomic>
#include <cassert>
#include <cstdint>
#include <functional>
#include <mutex>
#include <span>
#include <vector>

using X_STATUS = uint32_t;
constexpr X_STATUS X_STATUS_SUCCESS = 0;
constexpr X_STATUS X_STATUS_ACCESS_VIOLATION = 0xC0000005;
constexpr X_STATUS X_STATUS_END_OF_FILE = 0xC0000011;
constexpr bool XSUCCEEDED(X_STATUS status) { return status < 0x80000000; }

enum class HeapType { kGuestVirtual, kGuestPhysical };
namespace memory { enum class PageAccess { kReadWrite }; }
namespace xe {
template <typename T> using be = T;
struct global_critical_region { static int AcquireDirect() { return 0; } };
struct BaseHeap { HeapType heap_type() const { return HeapType::kGuestVirtual; } };
struct PhysicalHeap : BaseHeap {
  memory::PageAccess QueryRangeAccess(uint32_t, uint32_t) {
    return memory::PageAccess::kReadWrite;
  }
  uint32_t GetPhysicalAddress(uint32_t address) { return address; }
  void TriggerCallbacks(int, uint32_t, uint32_t, bool, bool) { assert(false); }
};
}

struct Memory {
  std::array<uint8_t, 16384> data{};
  std::array<uint32_t, 2> segments{4096, 8192};
  xe::BaseHeap heap;
  xe::BaseHeap* LookupHeap(uint32_t address) {
    return address < data.size() ? &heap : nullptr;
  }
  uint8_t* TranslateVirtual(uint32_t address) {
    return address == 64 ? reinterpret_cast<uint8_t*>(segments.data())
                         : data.data() + address;
  }
  uint8_t* TranslatePhysical(uint32_t) { assert(false); return nullptr; }
};

struct SyntheticFile {
  std::vector<uint64_t> offsets;
  X_STATUS ReadSync(std::span<uint8_t> out, size_t offset, size_t* bytes) {
    offsets.push_back(offset);
    if (offset >= 16384) return X_STATUS_END_OF_FILE;
    *bytes = std::min(out.size(), 16384 - offset);
    std::fill_n(out.data(), *bytes, uint8_t(offset / 4096));
    return X_STATUS_SUCCESS;
  }
};
struct Event { int sets = 0; void Set() { ++sets; } };
struct XIOCompletion {
  struct IONotification { uint32_t apc_context, num_bytes; X_STATUS status; };
};
struct Scheduler {
  int calls = 0;
  void RunBlockingHostCall(const std::function<void()>& fn) { ++calls; fn(); }
};
struct KernelState {
  Scheduler scheduler;
  Scheduler* guest_scheduler() { return &scheduler; }
};

struct XFile {
  Memory guest_memory;
  SyntheticFile backend;
  Event event;
  KernelState kernel;
  SyntheticFile* file_ = &backend;
  Event* async_event_ = &event;
  std::atomic<uint64_t> position_{0};
  std::mutex file_lock_;
  std::vector<XIOCompletion::IONotification> notifications;
  Memory* memory() { return &guest_memory; }
  KernelState* kernel_state() { return &kernel; }
  void NotifyIOCompletionPorts(XIOCompletion::IONotification& notification) {
    notifications.push_back(notification);
  }
  void set_position(uint64_t);
  X_STATUS ReadInternal(uint32_t, uint32_t, uint64_t, uint32_t*, uint32_t, bool);
  X_STATUS ReadScatterInternal(uint32_t, uint32_t, uint64_t, uint32_t*, uint32_t);
};
''' + methods + r'''
int main() {
  XFile stream;
  uint32_t bytes = 0;
  stream.set_position(8192);
  assert(stream.ReadInternal(4096, 512, 0, &bytes, 7, true) == X_STATUS_SUCCESS);
  assert(bytes == 512 && stream.position_ == 512);
  assert(stream.ReadInternal(4096, 32, uint64_t(-1), &bytes, 7, true) ==
         X_STATUS_SUCCESS);
  assert((stream.backend.offsets == std::vector<uint64_t>{0, 512}));
  assert(stream.position_ == 544);

  XFile scatter;
  scatter.set_position(8192);
  assert(scatter.ReadScatterInternal(64, 8192, 0, &bytes, 42) ==
         X_STATUS_SUCCESS);
  assert((scatter.backend.offsets == std::vector<uint64_t>{0, 4096}));
  assert(scatter.guest_memory.data[4096] == 0 &&
         scatter.guest_memory.data[8192] == 1);
  assert(bytes == 8192 && scatter.position_ == 8192 &&
         scatter.notifications.size() == 1 && scatter.event.sets == 1);
  assert(scatter.notifications[0].apc_context == 42 &&
         scatter.notifications[0].num_bytes == 8192);

  scatter.backend.offsets.clear();
  assert(scatter.ReadScatterInternal(64, 8192, uint64_t(-1), &bytes, 3) ==
         X_STATUS_SUCCESS);
  assert((scatter.backend.offsets == std::vector<uint64_t>{8192, 12288}));
  assert(scatter.position_ == 16384);
  assert(scatter.ReadInternal(4096, 32, 20000, &bytes, 7, true) ==
         X_STATUS_END_OF_FILE);
  assert(bytes == 0 && scatter.position_ == 16384);
  assert(scatter.kernel.scheduler.calls == 1);
}
'''


with tempfile.TemporaryDirectory(prefix="zephyr-xfile-") as directory:
    test_source = Path(directory) / "xfile_test.cc"
    binary = Path(directory) / "xfile_test"
    test_source.write_text(harness)
    subprocess.run(
        [os.environ.get("CXX", "c++"), "-std=c++20", "-O1", "-pthread",
         "-fsanitize=undefined", str(test_source), "-o", str(binary)],
        check=True,
    )
    subprocess.run([str(binary)], check=True, timeout=15)
print("XFile reads: explicit zero, sequential position, scatter and EOF passed.")
