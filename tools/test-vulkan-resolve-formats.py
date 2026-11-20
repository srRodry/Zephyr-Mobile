#!/usr/bin/env python3
"""Exercise the production resolve-format selection without a Vulkan device."""

import os
from pathlib import Path
import re
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
gpu = root / "emulator-core/src/main/cpp/xenia/src/xenia/gpu"
header = (gpu / "draw_util.h").read_text()
implementation = (gpu / "draw_util.cc").read_text()


def extract(source, declaration, indent=""):
    match = re.search(rf"{declaration}[^{{]*{{.*?^{indent}}}", source, re.M | re.S)
    assert match, declaration
    return match.group()


number_matching = extract(
    implementation, r"static constexpr bool ColorResolveNumberFormatMatches\(")
accuracy_selection = extract(header, r"bool NeedsAccurateColorResolve\(", "  ")
check_number = re.search(
    r"const bool check_number_format =[^;]+;", implementation
).group()

program = r"""
#include <cassert>

namespace xenos {
enum class ColorFormat {
  k_8_8_8_8, k_16_FLOAT, k_16_16_FLOAT, k_16_16_16_16_FLOAT,
  k_32_FLOAT, k_32_32_FLOAT, k_32_32_32_32_FLOAT,
};
enum class ColorRenderTargetFormat { k_8_8_8_8, k_8_8_8_8_GAMMA };
enum class SurfaceNumberFormat {
  kUnsignedRepeatingFraction, kSignedRepeatingFraction,
  kUnsignedInteger, kSignedInteger, kFloat,
};
}

namespace cvars {
bool resolve_check_number_format, accurate_resolve_number_formats,
    resolve_copy_dest_number_packing;
}

""" + number_matching + r"""

struct Resolve {
  struct { xenos::SurfaceNumberFormat copy_dest_number; } copy_dest_info{};
  struct {
    bool decode_pwl_gamma;
    xenos::ColorRenderTargetFormat format;
  } color_edram_info{};
  bool depth = false;
  bool IsCopyingDepth() const { return depth; }

""" + accuracy_selection + r"""
};

bool CheckNumberFormat() {
""" + check_number + r"""
  return check_number_format;
}

int main() {
  using Color = xenos::ColorRenderTargetFormat;
  using Dest = xenos::ColorFormat;
  using Number = xenos::SurfaceNumberFormat;
  Resolve resolve;
  assert(!resolve.NeedsAccurateColorResolve());
  resolve.color_edram_info.decode_pwl_gamma = true;
  assert(!resolve.NeedsAccurateColorResolve());
  resolve.color_edram_info.format = Color::k_8_8_8_8_GAMMA;
  assert(resolve.NeedsAccurateColorResolve());
  resolve.color_edram_info.decode_pwl_gamma = false;
  assert(!resolve.NeedsAccurateColorResolve());
  resolve.copy_dest_info.copy_dest_number = Number::kUnsignedInteger;
  assert(resolve.NeedsAccurateColorResolve());
  resolve.depth = true;
  assert(!resolve.NeedsAccurateColorResolve());
  assert(ColorResolveNumberFormatMatches(Dest::k_8_8_8_8,
                                         Number::kUnsignedRepeatingFraction));
  assert(!ColorResolveNumberFormatMatches(Dest::k_8_8_8_8,
                                          Number::kUnsignedInteger));
  assert(ColorResolveNumberFormatMatches(Dest::k_16_16_16_16_FLOAT,
                                         Number::kFloat));
  assert(!ColorResolveNumberFormatMatches(Dest::k_16_16_16_16_FLOAT,
                                          Number::kSignedInteger));
  assert(!CheckNumberFormat());
  cvars::accurate_resolve_number_formats = true;
  assert(!CheckNumberFormat());
  cvars::resolve_copy_dest_number_packing = true;
  assert(CheckNumberFormat());
  cvars::accurate_resolve_number_formats = false;
  assert(!CheckNumberFormat());
  cvars::resolve_check_number_format = true;
  assert(CheckNumberFormat());
}
"""

with tempfile.TemporaryDirectory(prefix="zephyr-resolve-") as directory:
    source = Path(directory) / "test.cc"
    executable = source.with_suffix("")
    source.write_text(program)
    subprocess.run(
        [os.environ.get("CXX", "c++"), "-std=c++20", "-Wall", "-Wextra",
         "-Werror", str(source), "-o", str(executable)], check=True)
    subprocess.run([str(executable)], check=True)

print("Vulkan resolve formats: integer packing, float, PWL gamma and depth.")
