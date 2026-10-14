// Copyright 2026 Ben Vanik. All rights reserved.
// Released under the BSD license; see LICENSE in the root.
// Frozen RunLoop from 05b494b, before phase instrumentation.
void GuestScheduler::RunLoop(int cpu_index) {
  t_current_cpu = cpu_index;
  Cpu& cpu = cpus_[cpu_index];
  // Adopt this host thread's stack as this CPU's idle fiber.
  cpu.idle_fiber = xe::threading::Fiber::CreateFromThread();
  XELOGI("GuestScheduler: CPU {} dispatch loop started", cpu_index);

  while (!shutting_down_.load()) {
    if (cpu_index == 0) {
      ReportStatsIfDue();
    }
    // Re-poll blocked waiters on a timer even while other fibers run, or a
    // busy fiber that rarely waits would starve them. The timer runs at what
    // the parked waiters actually need, a wake skips it entirely.
    uint64_t now = Clock::QueryHostUptimeMillis();
    bool poked = cpu.repoll_now.exchange(false, std::memory_order_relaxed);
    if (poked || now >= cpu.next_timed_repoll_ms) {
      RereadyBlocked(cpu_index, poked);
    }

    XThread* next = DequeueReady(cpu_index);
    if (next) {
      // Honor an affinity change that landed while it was queued or running
      // here. Safe now, an off-CPU thread's context is saved.
      int home = CpuOf(next);
      auto& links = next->scheduler_links();
      if (home != cpu_index &&
          !links.terminate_pending.load(std::memory_order_relaxed)) {
        {
          std::lock_guard<std::mutex> lock(lock_);
          links.running = false;
          links.queued = true;
          links.cpu = home;
          bool at_head = links.preempted;
          links.preempted = false;
          LinkReadyLocked(cpus_[home], next, at_head);
        }
        if (cpus_[home].parked.load() && cpus_[home].ready_event) {
          cpus_[home].ready_event->Set();
        }
        XELOGD("GuestScheduler: migrated tid={:08X} to CPU {}",
               next->thread_id(), home);
        continue;
      }
      // A suspend landing while the thread ran or was queued takes effect here.
      if (next->suspend_count() > 0 && ParkSuspended(next, cpu_index)) {
        continue;
      }
      cpu.exited_thread = nullptr;
      SwitchTo(next);
      if (cpu.exited_thread) {
        // On the idle fiber with the exited fiber parked on its final yield, so
        // reclaiming never frees a stack still in use.
        XThread* dead = cpu.exited_thread;
        cpu.exited_thread = nullptr;
        dead->ReclaimExited();
      }
      continue;
    }

    // Nothing ready, so sleep until the next re-poll if waiters are blocked (a
    // MarkReady wakes us sooner), otherwise idle until something is runnable.
    // Park before re-checking the queues, so a wake that saw parked still
    // false is caught here instead of slept through.
    cpu.parked.store(true);
    bool have_blocked;
    bool have_work;
    {
      std::lock_guard<std::mutex> lock(lock_);
      have_blocked = cpu.blocked_head != nullptr;
      have_work = cpu.ready_summary.load(std::memory_order_relaxed) != 0 ||
                  cpu.repoll_now.load(std::memory_order_relaxed);
    }
    if (have_work) {
      cpu.parked.store(false);
      continue;
    }
    if (!have_blocked) {
      if (dispatched_any_.load()) {
        xe::threading::Wait(cpu.ready_event.get(), false);
        cpu.parked.store(false);
        continue;
      }
      // Nothing has ever run, so poll instead of sleeping forever and say so.
      xe::threading::Wait(cpu.ready_event.get(), false,
                          std::chrono::seconds(1));
      cpu.parked.store(false);
      bool warned = false;
      if (!dispatched_any_.load() &&
          never_dispatched_warned_.compare_exchange_strong(warned, true)) {
        XELOGW(
            "GuestScheduler: no guest fiber dispatched after 1s, every guest "
            "thread is unqueued (created suspended and never resumed?)");
      }
      continue;
    }
    now = Clock::QueryHostUptimeMillis();
    uint64_t due = cpu.next_timed_repoll_ms;
    uint64_t sleep_ms = due > now ? due - now : 0;
    xe::threading::Wait(cpu.ready_event.get(), false,
                        std::chrono::milliseconds(sleep_ms));
    cpu.parked.store(false);
    stats_.idle_wakes.fetch_add(1, std::memory_order_relaxed);
  }
  XELOGI("GuestScheduler: CPU {} dispatch loop exited (shutting_down={})",
         cpu_index, shutting_down_.load());
}

