#!/usr/bin/env python3
"""Exercise the production shader translation publication and waiter methods."""

import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
gpu = root / "emulator-core/src/main/cpp/xenia/src/xenia/gpu"

shader = (gpu / "shader.h").read_text()
start = shader.index("  class Translation {")
end = shader.index("  // ucode_source_endian", start)
translation = shader[start:end]

translator = (gpu / "shader_translator.cc").read_text().split(
    "bool ShaderTranslator::TranslateAnalyzedShader(", 1
)[1].split("void ShaderTranslator::", 1)[0]
early_failure = translator.split("if (!shader.is_ucode_analyzed()) {", 1)[1].split(
    "return false;", 1
)[0]
assert early_failure.index("MakeInvalid();") < early_failure.index("PublishTranslationComplete();")
assert translator.index("PostTranslation();") < translator.rindex("PublishTranslationComplete();")
for name in ("guest_spirv_shader_cache.cc", "vulkan/vulkan_pipeline_cache.cc",
             "d3d12/pipeline_cache.cc"):
    reader = (gpu / name).read_text()
    assert "translation.WaitForTranslation();" in reader
    assert "while (!translation.is_translated())" not in reader

harness = r"""
#include <atomic>
#include <cassert>
#include <cstdint>
#include <filesystem>
#include <latch>
#include <thread>
#include <vector>

struct Shader {
  struct Error {};
@TRANSLATION@
};

struct TestTranslation : Shader::Translation {
  TestTranslation(Shader& shader) : Translation(shader, 0) {}
};

struct ShaderTranslator {
  static void Finish(Shader::Translation& translation, bool valid) {
    if (valid) {
      translation.translated_binary_ = {0x03, 0x02, 0x23, 0x07};
      translation.is_valid_.store(true, std::memory_order_relaxed);
    } else {
      translation.MakeInvalid();
    }
    translation.PublishTranslationComplete();
  }
};

void Exercise(bool valid) {
  Shader shader;
  TestTranslation translation(shader);
  constexpr int kReaders = 4;
  std::latch losers_ready(kReaders);
  assert(translation.TryClaimTranslation());
  std::vector<std::thread> readers;
  for (int i = 0; i < kReaders; ++i) {
    readers.emplace_back([&] {
      assert(!translation.TryClaimTranslation());
      losers_ready.count_down();
      translation.WaitForTranslation();
      assert(translation.is_translated() && translation.is_valid() == valid);
      assert(translation.translated_binary() ==
             (valid ? std::vector<uint8_t>{0x03, 0x02, 0x23, 0x07}
                    : std::vector<uint8_t>{}));
    });
  }
  losers_ready.wait();
  ShaderTranslator::Finish(translation, valid);
  for (auto& reader : readers) {
    reader.join();
  }
  // A reader that starts after publication must also finish immediately.
  translation.WaitForTranslation();
}

int main() {
  for (int i = 0; i < 50; ++i) {
    Exercise(true);
    Exercise(false);
  }
}
"""

with tempfile.TemporaryDirectory(prefix="zephyr-shader-wait-") as directory:
    source = Path(directory) / "wait.cc"
    binary = source.with_suffix("")
    source.write_text(harness.replace("@TRANSLATION@", translation))
    subprocess.run(
        [os.environ.get("CXX", "c++"), "-std=c++20", "-pthread",
         str(source), "-o", str(binary)],
        check=True,
        timeout=30,
    )
    subprocess.run([str(binary)], check=True, timeout=10)
print("Shader wait: valid and failed completion released every reader.")
