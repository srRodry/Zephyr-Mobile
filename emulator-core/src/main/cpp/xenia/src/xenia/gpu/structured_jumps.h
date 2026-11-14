/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_GPU_STRUCTURED_JUMPS_H_
#define XENIA_GPU_STRUCTURED_JUMPS_H_

#include <cstdint>
#include <set>
#include <unordered_map>
#include <vector>

namespace xe {
namespace gpu {

// XenDroid: which forward jumps of the shader microcode can be translated as if
// and else, because they nest like the if and else they usually come from,
// instead of a loop iteration and a dispatch through a switch over all labels.

struct ShaderJump {
  uint32_t source;
  uint32_t target;
  bool unconditional;
};

struct StructuredJumps {
  enum class Kind : uint8_t {
    // Goes through the main switch.
    kNone,
    // Conditional, over a region that runs if it isn't taken.
    kIf,
    // The same, with an else region - else_ends has where it ends.
    kIfElse,
    // Unconditional, ending a region with an else.
    kElseJump,
  };
  // By control flow instruction index.
  std::vector<Kind> kinds;
  std::unordered_map<uint32_t, uint32_t> else_ends;
  // The labels that end regions, not cases of the main switch.
  std::set<uint32_t> labels;
  // Whether labels stay in the main switch.
  bool main_switch_needed = false;
};

// switch_labels: the labels that have to stay cases of the main switch - those
// of loops, for instance. Jumps that aren't structured still go through the main
// loop from anywhere, also from inside an if. What can't be inside an if is a
// case of the switch.
inline StructuredJumps AnalyzeStructuredJumps(
    const std::vector<ShaderJump>& jumps, std::set<uint32_t> switch_labels,
    uint32_t cf_count) {
  for (const ShaderJump& jump : jumps) {
    // Only forward jumps, to labels that will be reached.
    if (jump.target <= jump.source || jump.target >= cf_count) {
      switch_labels.insert(jump.target);
    }
  }
  std::unordered_map<uint32_t, std::vector<const ShaderJump*>> jumps_by_target;
  for (const ShaderJump& jump : jumps) {
    jumps_by_target[jump.target].push_back(&jump);
  }
  bool changed;
  do {
    changed = false;
    auto stay = [&](uint32_t label) {
      changed |= switch_labels.insert(label).second;
    };
    for (const ShaderJump& jump : jumps) {
      if (switch_labels.count(jump.target)) {
        continue;
      }
      if (jump.unconditional) {
        // Only ends a region with an else: the region of the one conditional
        // jump to the instruction right after it.
        auto after = jumps_by_target.find(jump.source + 1);
        if (after == jumps_by_target.end() ||
            switch_labels.count(jump.source + 1) ||
            after->second.size() != 1 || after->second[0]->unconditional) {
          stay(jump.target);
          continue;
        }
      }
      auto inner = switch_labels.upper_bound(jump.source);
      if (inner != switch_labels.end() && *inner < jump.target) {
        stay(jump.target);
      }
    }
    if (changed) {
      continue;
    }
    // Regions must nest, the else of a region taking its place.
    for (const ShaderJump& a : jumps) {
      if (switch_labels.count(a.target)) {
        continue;
      }
      for (const ShaderJump& b : jumps) {
        if (switch_labels.count(b.target) || a.source >= b.source ||
            b.source >= a.target || a.target >= b.target) {
          continue;
        }
        if (!(b.unconditional && b.source + 1 == a.target)) {
          stay(b.target);
        }
      }
    }
  } while (changed);
  StructuredJumps result;
  result.main_switch_needed = !switch_labels.empty();
  result.kinds.resize(cf_count, StructuredJumps::Kind::kNone);
  for (const ShaderJump& jump : jumps) {
    if (switch_labels.count(jump.target)) {
      continue;
    }
    result.labels.insert(jump.target);
    if (jump.unconditional) {
      result.kinds[jump.source] = StructuredJumps::Kind::kElseJump;
      const ShaderJump& region_jump = *jumps_by_target[jump.source + 1][0];
      result.kinds[region_jump.source] = StructuredJumps::Kind::kIfElse;
      result.else_ends[region_jump.source] = jump.target;
    } else if (result.kinds[jump.source] != StructuredJumps::Kind::kIfElse) {
      result.kinds[jump.source] = StructuredJumps::Kind::kIf;
    }
  }
  return result;
}

}  // namespace gpu
}  // namespace xe

#endif  // XENIA_GPU_STRUCTURED_JUMPS_H_
