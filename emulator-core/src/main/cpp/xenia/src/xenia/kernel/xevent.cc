/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2022 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xenia/kernel/xevent.h"

#include <atomic>

#include "xenia/base/byte_stream.h"
#include "xenia/base/cvar.h"
#include "xenia/base/clock.h"
#include "xenia/base/logging.h"
#include "xenia/kernel/guest_scheduler.h"
#include "xenia/kernel/kernel_state.h"
#include "xenia/kernel/xthread.h"
#include "xenia/memory.h"

DEFINE_bool(zephyr_event_signal_trace, false,
            "Log at most 6000 event signals per process for boot diagnostics. "
            "Opt-in only: logging every XEvent::Set adds synchronous hot-path "
            "work and can delay thread-heavy games.",
            "Kernel");

namespace xe {
namespace kernel {

XEvent::XEvent(KernelState* kernel_state)
    : XObject(kernel_state, kObjectType) {}

XEvent::~XEvent() = default;

void XEvent::Initialize(bool manual_reset, bool initial_state) {
  assert_false(event_);

  manual_reset_ = manual_reset;
  CreateNative<X_KEVENT>();
  auto* kevent = memory()->TranslateVirtual<X_KEVENT*>(guest_object());
  // Don't touch header.wait_list: SetNativePointer stashes the handle there.
  kevent->header.type = manual_reset
                            ? X_DISPATCHER_FLAGS::DISPATCHER_MANUAL_RESET_EVENT
                            : X_DISPATCHER_FLAGS::DISPATCHER_AUTO_RESET_EVENT;
  kevent->header.signal_state = initial_state ? 1 : 0;

  if (manual_reset) {
    event_ = xe::threading::Event::CreateManualResetEvent(initial_state);
  } else {
    event_ = xe::threading::Event::CreateAutoResetEvent(initial_state);
  }
  assert_not_null(event_);
  RecordCreator();
}

void XEvent::InitializeNative(void* native_ptr,
                              const X_DISPATCH_HEADER* header) {
  assert_false(event_);

  switch (header->type) {
    case X_DISPATCHER_FLAGS::DISPATCHER_MANUAL_RESET_EVENT:
      manual_reset_ = true;
      break;
    case X_DISPATCHER_FLAGS::DISPATCHER_AUTO_RESET_EVENT:
      manual_reset_ = false;
      break;
    default:
      assert_always();
      return;
  }

  bool initial_state = header->signal_state ? true : false;
  if (manual_reset_) {
    event_ = xe::threading::Event::CreateManualResetEvent(initial_state);
  } else {
    event_ = xe::threading::Event::CreateAutoResetEvent(initial_state);
  }
  assert_not_null(event_);
  RecordCreator();
  SetNativePointer(memory()->HostToGuestVirtual(native_ptr), true);
}

void XEvent::RecordCreator() {
  auto* thread = XThread::GetCurrentThread();
  if (thread && thread->thread_state()) {
    creator_thread_ = thread->handle();
    creator_lr_ = uint32_t(thread->thread_state()->context()->lr);
  } else {
    creator_thread_ = 0xFFFFFFFF;  // host-side creator
    creator_lr_ = 0;
  }
}

void XEvent::RecordSetter() {
  auto* thread = XThread::GetCurrentThread();
  if (thread && thread->thread_state()) {
    last_set_thread_ = thread->handle();
    last_set_lr_ = uint32_t(thread->thread_state()->context()->lr);
  } else {
    last_set_thread_ = 0xFFFFFFFF;  // host-side setter
    last_set_lr_ = 0;
  }
  last_set_uptime_ms_ = Clock::QueryGuestUptimeMillis();

  // Event-set is a heavily contended guest synchronization hot path. Keep
  // the setter metadata for deadlock analysis but make the expensive string
  // formatting and logging explicitly opt-in. A saturating counter avoids an
  // atomic write on every subsequent signal after the diagnostic budget ends.
  if (cvars::zephyr_event_signal_trace) {
    static std::atomic<int> trace_budget{6000};
    int remaining = trace_budget.load(std::memory_order_relaxed);
    while (remaining > 0 &&
           !trace_budget.compare_exchange_weak(remaining, remaining - 1,
                                               std::memory_order_relaxed)) {
    }
    if (remaining > 0) {
      XELOGI("XEvent signal: handle={:08X} by={:08X} lr={:08X} (creator={:08X} "
             "lr={:08X})",
             handle(), last_set_thread_, last_set_lr_, creator_thread_,
             creator_lr_);
    }
  }
}

int32_t XEvent::Set(uint32_t priority_increment, bool wait) {
  set_priority_increment(priority_increment);
  RecordSetter();
  event_->Set();
  memory()->TranslateVirtual<X_KEVENT*>(guest_object())->header.signal_state =
      1;
  WakeCooperativeWaiters();
  return 1;
}

int32_t XEvent::Pulse(uint32_t priority_increment, bool wait) {
  set_priority_increment(priority_increment);
  RecordSetter();
  auto* kevent = memory()->TranslateVirtual<X_KEVENT*>(guest_object());
  // KePulseEvent returns the pre-pulse signal state.
  int32_t old_signal_state = kevent->header.signal_state;
  // A parked cooperative waiter re-polls only after a host pulse has already
  // reset the event, so every pulse would be lost. Deliver as a set the first
  // waiter consumes, which for an auto-reset event with a waiter is exactly
  // pulse semantics.
  if (!manual_reset_ && waiters_.HasWaiters()) {
    Set(priority_increment, wait);
    return old_signal_state;
  }
  if (manual_reset_) {
    // Releases every waiter parked right now. Must precede the wake below.
    pulse_epoch_.fetch_add(1);
  }
  event_->Pulse();
  // Pulse leaves the event reset after releasing waiters.
  kevent->header.signal_state = 0;
  WakeCooperativeWaiters();
  return old_signal_state;
}

int32_t XEvent::Reset() {
  event_->Reset();
  memory()->TranslateVirtual<X_KEVENT*>(guest_object())->header.signal_state =
      0;
  return 1;
}

void XEvent::WaitCallback() {
  // Auto-reset events atomically clear on successful wait; manual stay set.
  if (!manual_reset_) {
    memory()->TranslateVirtual<X_KEVENT*>(guest_object())->header.signal_state =
        0;
  }
}

void XEvent::CooperativeWaitBegin(XThread* thread) { waiters_.Add(thread); }

void XEvent::CooperativeWaitEnd(XThread* thread) { waiters_.Remove(thread); }

// An auto-reset set with a parked waiter belongs to the front of the queue.
// NT wakes the first waiter directly, so a later poller must not steal it.
bool XEvent::CooperativeMayAcquire(XThread* thread) {
  return manual_reset_ || waiters_.MayAcquire(thread);
}
void XEvent::Query(uint32_t* out_type, uint32_t* out_state) {
  auto [type, state] = event_->Query();

  *out_type = type;
  *out_state = state;
}
void XEvent::Clear() { event_->Reset(); }

bool XEvent::Save(ByteStream* stream) {
  XELOGD("XEvent {:08X} ({})", handle(), manual_reset_ ? "manual" : "auto");
  SaveObject(stream);

  bool signaled = true;
  auto result =
      xe::threading::Wait(event_.get(), false, std::chrono::milliseconds(0));
  if (result == xe::threading::WaitResult::kSuccess) {
    signaled = true;
  } else if (result == xe::threading::WaitResult::kTimeout) {
    signaled = false;
  } else {
    assert_always();
  }

  if (signaled) {
    // Reset the event in-case it's an auto-reset.
    event_->Set();
  }

  stream->Write<bool>(signaled);
  stream->Write<bool>(manual_reset_);

  return true;
}

object_ref<XEvent> XEvent::Restore(KernelState* kernel_state,
                                   ByteStream* stream) {
  auto evt = new XEvent(nullptr);
  evt->kernel_state_ = kernel_state;

  evt->RestoreObject(stream);
  bool signaled = stream->Read<bool>();
  evt->manual_reset_ = stream->Read<bool>();

  if (evt->manual_reset_) {
    evt->event_ = xe::threading::Event::CreateManualResetEvent(false);
  } else {
    evt->event_ = xe::threading::Event::CreateAutoResetEvent(false);
  }
  assert_not_null(evt->event_);

  if (signaled) {
    evt->event_->Set();
  }

  return object_ref<XEvent>(evt);
}

}  // namespace kernel
}  // namespace xe
