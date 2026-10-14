#include <array>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <unordered_set>
#include "a64_logical_test.h"
using xe::cpu::backend::a64::IsLogicalImmediate64;
using xe::cpu::backend::a64::IsLogicalImmediate32;
// Independent ARM DecodeBitMasks-style reference, built bit-by-bit.
static bool Decode(unsigned n, unsigned r, unsigned s, unsigned bits, uint64_t& mask) {
  unsigned selector = (n << 6) | ((~s) & 63);
  int length = -1;
  for (int b = 0; b < 7; ++b) if (selector & (1u << b)) length = b;
  if (length < 1) return false;
  unsigned width = 1u << length;
  if (width > bits) return false;
  unsigned levels = width - 1, ones = (s & levels) + 1;
  if (ones == width) return false;
  mask = 0;
  for (unsigned base = 0; base < bits; base += width)
    for (unsigned b = 0; b < ones; ++b)
      mask |= uint64_t(1) << (base + (b + width - (r & levels)) % width);
  return true;
}
static uint64_t Random(uint64_t& state) { state ^= state << 13; state ^= state >> 7; state ^= state << 17; return state; }
static void Execute(const test_a64::A64Emitter& e, std::array<uint64_t,32>& regs) {
  auto words = e.getCode<const uint32_t*>();
  for (size_t k = 0; k < e.getSize()/4; ++k) {
    uint32_t w = words[k], d = w & 31, n = (w >> 5) & 31, m = (w >> 16) & 31;
    unsigned bits = (w >> 31) ? 64 : 32;
    uint64_t limit = bits == 64 ? ~uint64_t(0) : 0xffffffffULL, value = 0;
    auto read = [&](unsigned r) { return r == 31 ? uint64_t(0) : regs[r] & limit; };
    if ((w & 0x1f800000) == 0x12000000) {
      uint64_t mask; assert(Decode((w >> 22) & 1, (w >> 16) & 63, (w >> 10) & 63, bits, mask));
      unsigned op = (w >> 29) & 3;
      assert(op <= 1); value = op == 0 ? read(n) & mask : read(n) | mask;
    } else if ((w & 0x1f800000) == 0x12800000) {
      unsigned op = (w >> 29) & 3, shift = ((w >> 21) & 3) * 16;
      uint64_t imm = uint64_t((w >> 5) & 0xffff) << shift;
      assert(op != 1);
      value = op == 0 ? ~imm : op == 2 ? imm : (read(d) & ~(uint64_t(0xffff) << shift)) | imm;
    } else if ((w & 0x1f000000) == 0x0a000000) {
      assert(((w >> 22) & 3) == 0 && ((w >> 10) & 63) == 0);
      auto a = read(n), b = read(m);
      if (w & (1u << 21)) b = ~b & limit;
      unsigned op = (w >> 29) & 3; assert(op <= 1);
      value = op == 0 ? a & b : a | b;
    } else { std::fprintf(stderr,"Unexpected instruction %08x\n", w); assert(false); }
    if (d != 31) regs[d] = value & limit;
  }
}
static size_t checks = 0;
static void Check64(uint64_t constant, bool invert, bool alias, bool constant_first = false, bool both_constant = false) {
  test_a64::A64Emitter e;
  uint32_t dest = alias ? (constant_first ? 23 : 22) : 28;
  using Op = test_a64::I64Op;
  if (invert) test_a64::AND_NOT_I64::Emit(e, {Op(false,dest,0),Op(constant_first || both_constant,22,constant),Op(!constant_first || both_constant,23,constant)});
  else test_a64::AND_I64::Emit(e, {Op(false,dest,0),Op(constant_first || both_constant,22,constant),Op(!constant_first || both_constant,23,constant)});
  if (!constant_first && !both_constant && IsLogicalImmediate64(invert ? ~constant : constant)) assert(e.getSize() == 4);
  test_a64::A64Emitter baseline;
  bool eligible = false;
  if (both_constant) {
    baseline.mov(Xbyak_aarch64::XReg(dest), invert ? uint64_t(0) : constant);
  } else if (constant_first && invert) {
    baseline.mov(baseline.x0, constant);
    baseline.bic(Xbyak_aarch64::XReg(dest), baseline.x0, Xbyak_aarch64::XReg(23));
  } else {
    const uint64_t mask = invert ? ~constant : constant;
    eligible = IsLogicalImmediate64(mask);
    baseline.mov(baseline.x0, mask);
    baseline.and_(Xbyak_aarch64::XReg(dest), Xbyak_aarch64::XReg(constant_first ? 23 : 22), baseline.x0);
  }
  assert(e.getSize() <= baseline.getSize());
  if (!eligible) {
    assert(e.getSize() == baseline.getSize());
    for (size_t k = 0; k < e.getSize(); ++k) assert(e.getCode()[k] == baseline.getCode()[k]);
  }
  uint64_t seed = constant ^ 0x123456789abcdef0ULL;
  for (int p = 0; p < 4; ++p) {
    std::array<uint64_t,32> regs{}; for (auto& v : regs) v = Random(seed);
    if (p == 0) { regs[22] = 0; regs[23] = 0; }
    if (p == 1) { regs[22] = ~uint64_t(0); regs[23] = ~uint64_t(0); }
    auto before = regs;
    uint64_t a = constant_first || both_constant ? constant : before[22];
    uint64_t b = !constant_first || both_constant ? constant : before[23];
    uint64_t expected = a & (invert ? ~b : b);
    Execute(e,regs); assert(regs[dest] == expected);
    for (unsigned r = 1; r < 31; ++r) if (r != dest) assert(regs[r] == before[r]);
    ++checks;
  }
}
static void Check32(uint32_t constant, bool alias) {
  test_a64::A64Emitter e;
  uint32_t dest = alias ? 22 : 28;
  using Op = test_a64::I32Op;
  test_a64::AND_NOT_I32::Emit(e,{Op(false,dest,0),Op(false,22,0),Op(true,23,constant)});
  test_a64::A64Emitter baseline;
  baseline.mov(baseline.w0, uint64_t(constant));
  baseline.bic(Xbyak_aarch64::WReg(dest), Xbyak_aarch64::WReg(22), baseline.w0);
  if (IsLogicalImmediate32(~constant)) assert(e.getSize() == 4);
  else {
    assert(e.getSize() == baseline.getSize());
    for (size_t k = 0; k < e.getSize(); ++k) assert(e.getCode()[k] == baseline.getCode()[k]);
  }
  uint64_t seed = constant ^ 0xabcdef0123456789ULL;
  for (int p = 0; p < 4; ++p) {
    std::array<uint64_t,32> regs{}; for (auto& v : regs) v = Random(seed);
    auto before = regs; uint64_t expected = uint32_t(before[22]) & ~constant;
    Execute(e,regs); assert(regs[dest] == expected);
    for (unsigned r = 1; r < 31; ++r) if (r != dest) assert(regs[r] == before[r]);
    ++checks;
  }
}
int main() {
  std::unordered_set<uint64_t> masks;
  std::unordered_set<uint32_t> masks32;
  for(unsigned n=0;n<2;++n) for(unsigned r=0;r<64;++r) for(unsigned s=0;s<64;++s) {
    uint64_t mask; if(Decode(n,r,s,64,mask)) masks.insert(mask);
    if(Decode(n,r,s,32,mask)) masks32.insert(uint32_t(mask));
  }
  for(auto mask:masks) {
    assert(IsLogicalImmediate64(mask));
    for(bool alias:{false,true}) { Check64(mask,false,alias); Check64(~mask,true,alias); }
  }
  for (auto mask : masks32) {
    assert(IsLogicalImmediate32(mask));
    Check32(~mask, false); Check32(~mask, true);
  }
  assert(!IsLogicalImmediate32(0) && !IsLogicalImmediate32(~uint32_t(0)));
  assert(!IsLogicalImmediate64(0) && !IsLogicalImmediate64(~uint64_t(0)));
  uint64_t seed=0x123456789abcdef0ULL;
  for(int i=0;i<100000;++i) { auto mask=Random(seed); assert(IsLogicalImmediate64(mask)==(masks.count(mask)!=0)); assert(IsLogicalImmediate32(uint32_t(mask))==(masks32.count(uint32_t(mask))!=0)); }
  for(int i=0;i<2000;++i) {
    auto mask=Random(seed);
    for(bool alias:{false,true}) {
      Check64(mask,false,alias); Check64(mask,true,alias);
      Check64(mask,false,alias,true); Check64(mask,true,alias,true);
      Check64(mask,false,alias,false,true); Check64(mask,true,alias,false,true);
      Check32(uint32_t(mask),alias);
    }
  }
  for(auto mask:{0ULL,~0ULL,0x8000000000000000ULL,0xffffffffULL,0xaaaaaaaaaaaaaaaaULL})
    for(bool alias:{false,true}) { Check64(mask,false,alias); Check64(mask,true,alias); Check32(uint32_t(mask),alias); }
  std::printf("ARM64 logical immediates: %zu 64-bit and %zu 32-bit encodable masks, 200000 predicate cross-checks, %zu emitted-code semantic cases passed.\n",masks.size(),masks32.size(),checks);
}
