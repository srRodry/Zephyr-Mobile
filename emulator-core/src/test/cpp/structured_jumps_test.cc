#include <cassert>
#include <cstdint>
#include <cstdio>
#include <set>
#include <vector>
#include "xenia/gpu/structured_jumps.h"

using xe::gpu::AnalyzeStructuredJumps;
using xe::gpu::ShaderJump;
using xe::gpu::StructuredJumps;
using Kind = StructuredJumps::Kind;

constexpr uint32_t kCount = 64;

static void AForwardJumpIsAnIf() {
  auto r = AnalyzeStructuredJumps({{2, 6, false}}, {}, kCount);
  assert(r.kinds[2] == Kind::kIf);
  assert(r.labels == std::set<uint32_t>({6}));
  assert(!r.main_switch_needed);
}

static void AJumpOverTheElseEndsTheRegion() {
  // (!p0) jmp L7; A; jmp L10; L7: B; L10:
  auto r = AnalyzeStructuredJumps({{2, 7, false}, {6, 10, true}}, {}, kCount);
  assert(r.kinds[2] == Kind::kIfElse);
  assert(r.kinds[6] == Kind::kElseJump);
  assert(r.else_ends.at(2) == 10);
  assert(r.labels == std::set<uint32_t>({7, 10}));
  assert(!r.main_switch_needed);
}

static void ElseIfChainsShareTheirEnd() {
  auto r = AnalyzeStructuredJumps(
      {{1, 5, false}, {4, 12, true}, {6, 10, false}, {9, 12, true}}, {}, kCount);
  assert(r.kinds[1] == Kind::kIfElse && r.else_ends.at(1) == 12);
  assert(r.kinds[4] == Kind::kElseJump);
  assert(r.kinds[6] == Kind::kIfElse && r.else_ends.at(6) == 12);
  assert(r.kinds[9] == Kind::kElseJump);
  assert(!r.main_switch_needed);
}

static void AJumpTakenRightAfterTheConditionOne() {
  // (!p0) jmp L7; jmp L162; L7: ... L162: - an empty region and its else.
  auto r = AnalyzeStructuredJumps({{5, 7, false}, {6, 162 % kCount, true}}, {},
                                  kCount);
  assert(r.kinds[5] == Kind::kIfElse);
  assert(r.kinds[6] == Kind::kElseJump);
}

static void BackwardJumpsStayInTheSwitch() {
  auto r = AnalyzeStructuredJumps({{8, 2, false}}, {}, kCount);
  assert(r.kinds[8] == Kind::kNone);
  assert(r.labels.empty());
  assert(r.main_switch_needed);
}

static void ARegionCantHoldACaseOfTheSwitch() {
  // A loop's label inside the region.
  auto r = AnalyzeStructuredJumps({{2, 8, false}}, {5}, kCount);
  assert(r.kinds[2] == Kind::kNone);
  assert(r.labels.empty());
  assert(r.main_switch_needed);
}

static void ARegionInsideALoopIsFine() {
  auto r = AnalyzeStructuredJumps({{5, 9, false}}, {2, 20}, kCount);
  assert(r.kinds[5] == Kind::kIf);
  assert(r.labels == std::set<uint32_t>({9}));
  assert(r.main_switch_needed);
}

static void CrossingJumpsLeaveTheLaterTargetInTheSwitch() {
  auto r = AnalyzeStructuredJumps({{2, 6, false}, {4, 8, false}}, {}, kCount);
  assert(r.kinds[2] == Kind::kIf);
  assert(r.kinds[4] == Kind::kNone);
  assert(r.labels == std::set<uint32_t>({6}));
  assert(r.main_switch_needed);
}

static void ALabelThatStaysTakesTheRegionsAroundItWithIt() {
  // 3-5 and 4-7 cross, so 7 stays, and 1-10 can't hold it.
  auto r = AnalyzeStructuredJumps(
      {{1, 10, false}, {3, 5, false}, {4, 7, false}}, {}, kCount);
  assert(r.kinds[1] == Kind::kNone);
  assert(r.kinds[3] == Kind::kIf);
  assert(r.kinds[4] == Kind::kNone);
  assert(r.labels == std::set<uint32_t>({5}));
}

static void AnUnconditionalJumpInTheOpenStaysInTheSwitch() {
  auto r = AnalyzeStructuredJumps({{3, 9, true}}, {}, kCount);
  assert(r.kinds[3] == Kind::kNone);
  assert(r.main_switch_needed);
}

static void AnElseCantFollowTwoRegions() {
  // Both conditional jumps reach what would be the else.
  auto r = AnalyzeStructuredJumps(
      {{1, 6, false}, {3, 6, false}, {5, 9, true}}, {}, kCount);
  assert(r.kinds[1] == Kind::kIf);
  assert(r.kinds[3] == Kind::kIf);
  assert(r.kinds[5] == Kind::kNone);
  assert(r.labels == std::set<uint32_t>({6}));
  assert(r.main_switch_needed);
}

static void AnElseOverACaseLeavesThePlainRegion() {
  auto r = AnalyzeStructuredJumps({{2, 6, false}, {5, 9, true}}, {7}, kCount);
  assert(r.kinds[2] == Kind::kIf);
  assert(r.else_ends.empty());
  assert(r.kinds[5] == Kind::kNone);
}

static void AnElseCantLeaveTheRegionAroundIt() {
  // The inner else would go past the end of the outer region, and both end at 9.
  auto r = AnalyzeStructuredJumps(
      {{0, 5, false}, {1, 3, false}, {2, 9, true}, {4, 9, true}}, {}, kCount);
  assert(r.kinds[0] == Kind::kIf);
  assert(r.kinds[1] == Kind::kIf);
  assert(r.kinds[2] == Kind::kNone);
  assert(r.kinds[4] == Kind::kNone);
  assert(r.labels == std::set<uint32_t>({3, 5}));
  assert(r.else_ends.empty());
  assert(r.main_switch_needed);
}

static void AJumpIntoAnElseLeavesItInTheSwitch() {
  auto r = AnalyzeStructuredJumps({{1, 5, false}, {2, 7, false}, {4, 9, true}},
                                  {}, kCount);
  assert(r.kinds[1] == Kind::kIf);
  assert(r.kinds[2] == Kind::kNone);
  assert(r.kinds[4] == Kind::kNone);
  assert(r.labels == std::set<uint32_t>({5}));
  assert(r.else_ends.empty());
  assert(r.main_switch_needed);
}

static void JumpsPastTheEndStayInTheSwitch() {
  auto r = AnalyzeStructuredJumps({{2, 100, false}}, {}, 20);
  assert(r.kinds[2] == Kind::kNone);
  assert(r.main_switch_needed);
}

int main() {
  AForwardJumpIsAnIf();
  AJumpOverTheElseEndsTheRegion();
  ElseIfChainsShareTheirEnd();
  AJumpTakenRightAfterTheConditionOne();
  BackwardJumpsStayInTheSwitch();
  ARegionCantHoldACaseOfTheSwitch();
  ARegionInsideALoopIsFine();
  CrossingJumpsLeaveTheLaterTargetInTheSwitch();
  ALabelThatStaysTakesTheRegionsAroundItWithIt();
  AnUnconditionalJumpInTheOpenStaysInTheSwitch();
  AnElseCantFollowTwoRegions();
  AnElseOverACaseLeavesThePlainRegion();
  AnElseCantLeaveTheRegionAroundIt();
  AJumpIntoAnElseLeavesItInTheSwitch();
  JumpsPastTheEndStayInTheSwitch();
  std::puts("structured jumps: passed");
}
