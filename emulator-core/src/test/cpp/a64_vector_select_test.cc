// Encodes the exact production SELECT emitter with the vendored Xbyak encoder.
// Independently interprets the emitted ARM64 bitwise instructions, not ARM CPU
// timing. Also checks that vector SELECT survives the real HIR DCE/allocator.
#include <array>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include "xenia/cpu/backend/machine_info.h"
#include "xenia/cpu/compiler/passes/dead_code_elimination_pass.h"
#include "xenia/cpu/compiler/passes/register_allocation_pass.h"
#include "xenia/cpu/hir/hir_builder.h"
#include "a64_select_test.h"
using namespace xe::cpu;
using Vector = std::array<uint64_t, 2>;
using Registers = std::array<Vector, 32>;
static uint64_t Random(uint64_t& state) {
  state ^= state << 13; state ^= state >> 7; state ^= state << 17;
  return state;
}
static void Execute(const test_a64::A64Emitter& e, Registers& regs) {
  assert(e.getSize() % 4 == 0);
  auto code = e.getCode<const uint32_t*>();
  for (size_t k = 0; k < e.getSize() / 4; ++k) {
    uint32_t word = code[k], opcode = word & 0xffe0fc00u;
    uint32_t d = word & 31, n = (word >> 5) & 31, m = (word >> 16) & 31;
    auto old = regs[d], a = regs[n], b = regs[m]; // simultaneous operand reads
    for (int lane = 0; lane < 2; ++lane) {
      switch (opcode) {
        case 0x4ea01c00u: regs[d][lane] = a[lane] | b[lane]; break; // ORR
        case 0x6e601c00u: regs[d][lane] = (old[lane] & a[lane]) | (~old[lane] & b[lane]); break; // BSL
        case 0x6ea01c00u: regs[d][lane] = (a[lane] & b[lane]) | (old[lane] & ~b[lane]); break; // BIT
        case 0x6ee01c00u: regs[d][lane] = (old[lane] & b[lane]) | (a[lane] & ~b[lane]); break; // BIF
        default: std::fprintf(stderr, "Unexpected ARM64 opcode %08x\n", word); assert(false);
      }
    }
  }
}
static size_t Check(uint32_t d, uint32_t mask, uint32_t no, uint32_t yes,
                    size_t patterns) {
  test_a64::A64Emitter e;
  test_a64::SELECT_V128_V128::Emit(e, {{false, d}, {false, mask}, {false, no}, {false, yes}});
  // The fully distinct destination still needs a copy; every operand alias is one instruction.
  assert(e.getSize() == (d == mask || d == no || d == yes ? 4 : 8));
  uint64_t seed = 0x123456789abcdef0ULL;
  for (size_t p = 0; p < patterns; ++p) {
    Registers regs{};
    for (auto& v : regs) for (auto& lane : v) lane = Random(seed);
    if (p < 4) regs[mask] = p == 0 ? Vector{0,0} : p == 1 ? Vector{~0ULL,~0ULL} :
        p == 2 ? Vector{0xaaaaaaaaaaaaaaaaULL,0x5555555555555555ULL} : Vector{1,1ULL << 63};
    auto before = regs;
    Vector expected{};
    for (int lane = 0; lane < 2; ++lane)
      expected[lane] = (before[mask][lane] & before[yes][lane]) |
                       (~before[mask][lane] & before[no][lane]);
    Execute(e, regs);
    assert(regs[d] == expected);
    for (uint32_t r = 0; r < regs.size(); ++r) if (r != d) assert(regs[r] == before[r]);
  }
  return patterns;
}
static void RealPipeline(bool dead_no) {
  backend::MachineInfo info{};
  info.register_sets[0].id = 0;
  info.register_sets[0].types = backend::MachineInfo::RegisterSet::INT_TYPES;
  info.register_sets[0].count = 7;
  info.register_sets[1].id = 1;
  info.register_sets[1].types = backend::MachineInfo::RegisterSet::FLOAT_TYPES |
                               backend::MachineInfo::RegisterSet::VEC_TYPES;
  info.register_sets[1].count = 28;
  hir::HIRBuilder b; b.MakeCurrent();
  // First free source is a value, not the mask, under the unchanged allocator.
  auto no = b.LoadContext(0, hir::VEC128_TYPE);
  auto yes = b.LoadContext(16, hir::VEC128_TYPE);
  auto mask = b.LoadContext(32, hir::VEC128_TYPE);
  auto result = b.Select(mask, no, yes);
  b.StoreContext(48, result);
  if (!dead_no) b.StoreContext(64, no); // preserve no: allocator reuses yes instead
  b.Return();
  assert(b.Finalize());
  compiler::passes::DeadCodeEliminationPass dce;
  assert(dce.Run(&b));
  compiler::passes::RegisterAllocationPass allocator(&info);
  assert(allocator.Run(&b));
  bool found = false;
  for (auto block = b.first_block(); block; block = block->next)
    for (auto i = block->instr_head; i; i = i->next)
      if (i->GetOpcodeNum() == hir::OPCODE_SELECT) {
        found = true;
        assert(i->dest->reg.index == (dead_no ? no : yes)->reg.index);
        // Backend uses v4-v31; v0-v3 are scratch, not allocator registers.
        Check(4 + i->dest->reg.index, 4 + i->src1.value->reg.index,
              4 + i->src2.value->reg.index, 4 + i->src3.value->reg.index, 1024);
      }
  assert(found);
}
int main() {
  size_t checks = 0;
  // All 4^4 alias arrangements, including repeated sources and destination=mask.
  for (uint32_t d = 4; d < 8; ++d) for (uint32_t m = 4; m < 8; ++m)
    for (uint32_t n = 4; n < 8; ++n) for (uint32_t y = 4; y < 8; ++y)
      checks += Check(d, m, n, y, 1024);
  // Scratch v0/v1/v2 model operands already materialized by unchanged SrcVReg.
  for (uint32_t d = 4; d < 8; ++d) for (uint32_t m : {0u, 4u})
    for (uint32_t n : {1u, 5u}) for (uint32_t y : {2u, 6u})
      checks += Check(d, m, n, y, 1024);
  // Check the high-register encoding boundary as well as the low registers.
  checks += Check(31, 30, 31, 29, 1024);
  checks += Check(31, 30, 29, 31, 1024);
  checks += Check(31, 30, 29, 28, 1024);
  RealPipeline(true); RealPipeline(false);
  std::printf("ARM64 vector SELECT: %zu bit-exact cases, 256 alias arrangements plus scratch/high-register cases, two real HIR/DCE/allocator fixtures passed; encoded 4/8-byte paths verified.\n", checks + 2048);
}
