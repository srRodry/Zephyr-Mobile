// Production base timeline and production selection policy; fake fence states.
// No Vulkan device, driver timing or Android FPS measurement.
#include "xenia/ui/gpu_completion_timeline.h"

#include <cassert>
#include <deque>
#include <iostream>
#include <vector>

namespace xe::ui::vulkan {
class VulkanGPUCompletionTimeline : public GPUCompletionTimeline {
 public:
  VulkanGPUCompletionTimeline(bool bounded, uint64_t queued = 4)
      : bounded_(bounded) {
    for (uint64_t i = 1; i <= queued; ++i) {
      pending.push_back(i);
      IncrementUpcomingSubmission();
    }
  }
  bool bounded_collection() const { return bounded_; }
  void UpdateCompletedSubmission() override { PollPendingFences(UINT64_MAX); }
  void UpdateCompletedSubmissionUpTo(uint64_t awaited_submission) override;
  void AwaitSubmissionImpl(uint64_t awaited_submission) override {
    waits.push_back(awaited_submission);
    if (!fail_wait) {
      signaled_through = std::max(signaled_through, awaited_submission);
      CacheThrough(awaited_submission);
    }
  }
  void CacheThrough(uint64_t completed) {
    SetCompletedSubmission(completed);
    while (!pending.empty() && pending.front() <= completed) pending.pop_front();
  }
  void PollPendingFences(uint64_t last_submission) {
    ++collections;
    while (!pending.empty() && pending.front() <= last_submission) {
      const uint64_t index = pending.front();
      polls.push_back(index);
      if (index > signaled_through) break;
      SetCompletedSubmission(index);
      pending.pop_front();
    }
  }
  uint64_t signaled_through = 0;
  bool fail_wait = false;
  unsigned collections = 0;
  std::deque<uint64_t> pending;
  std::vector<uint64_t> polls, waits;
 private:
  bool bounded_;
};
// Definition appended verbatim from the production Vulkan implementation.
}  // namespace xe::ui::vulkan

using Timeline = xe::ui::vulkan::VulkanGPUCompletionTimeline;
int main() {
  Timeline zero(false);
  for (unsigned i = 0; i < 100000; ++i) assert(zero.AwaitSubmissionAndUpdateCompleted(0));
  assert(zero.collections == 0 && zero.polls.empty() && zero.waits.empty());
  assert(zero.pending.size() == 4);

  Timeline cached(false);
  cached.CacheThrough(2);
  for (unsigned i = 0; i < 100000; ++i) assert(cached.AwaitSubmissionAndUpdateCompleted(2));
  assert(cached.collections == 0 && cached.polls.empty() && cached.waits.empty());
  assert(cached.pending == std::deque<uint64_t>({3, 4}));

  Timeline bounded(true), unbounded(false);
  bounded.signaled_through = unbounded.signaled_through = 2;
  assert(bounded.AwaitSubmissionAndUpdateCompleted(2));
  assert(unbounded.AwaitSubmissionAndUpdateCompleted(2));
  assert(bounded.polls == std::vector<uint64_t>({1, 2}));
  assert(unbounded.polls == std::vector<uint64_t>({1, 2, 3, 3}));
  assert(bounded.waits.empty() && unbounded.waits.empty());
  assert(bounded.GetCompletedSubmissionFromLastUpdate() == 2);
  assert(unbounded.GetCompletedSubmissionFromLastUpdate() == 2);

  for (bool mode : {false, true}) {
    Timeline pending(mode);
    assert(pending.AwaitSubmissionAndUpdateCompleted(2));
    assert(pending.waits == std::vector<uint64_t>({2}));
    assert(pending.GetCompletedSubmissionFromLastUpdate() == 2);
    assert(pending.pending == std::deque<uint64_t>({3, 4}));
  }

  Timeline all_bounded(true), all_unbounded(false);
  all_bounded.signaled_through = all_unbounded.signaled_through = 4;
  assert(all_bounded.AwaitSubmissionAndUpdateCompleted(2));
  assert(all_unbounded.AwaitSubmissionAndUpdateCompleted(2));
  assert(all_bounded.GetCompletedSubmissionFromLastUpdate() == 2);
  assert(all_unbounded.GetCompletedSubmissionFromLastUpdate() == 4);
  assert(all_bounded.pending.size() == 2 && all_unbounded.pending.empty());

  Timeline failed(true);
  failed.fail_wait = true;
  assert(!failed.AwaitSubmissionAndUpdateCompleted(2));
  assert(failed.GetCompletedSubmissionFromLastUpdate() == 0);
  assert(failed.waits == std::vector<uint64_t>({2}));

  Timeline empty(true, 0), wait_all(true), limit(true);
  assert(empty.AwaitMaxSubmissionsPendingAndUpdateCompleted(8));
  assert(empty.AwaitAllSubmissions());
  assert(empty.collections == 0);
  assert(wait_all.AwaitAllSubmissions());
  assert(wait_all.waits == std::vector<uint64_t>({4}));
  assert(limit.AwaitMaxSubmissionsPendingAndUpdateCompleted(2));
  assert(limit.waits == std::vector<uint64_t>({3}));
  std::cout << "GPU timeline: 7 regression groups; 200000 zero/cached requests "
               "without driver calls; actual selection policy yields probes "
               "[1,2] bounded versus [1,2,3,3] unbounded in fake fence states.\n";
}
