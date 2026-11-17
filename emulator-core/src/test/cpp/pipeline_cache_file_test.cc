// Host test of the VkPipelineCache file store (no Vulkan needed).
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <vector>

#include "xenia/gpu/vulkan/vulkan_pipeline_cache_file.h"

namespace pcf = xe::gpu::vulkan::pipeline_cache_file;
namespace fs = std::filesystem;

#define CHECK(condition)                                                    \
  do {                                                                      \
    if (!(condition)) {                                                     \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, \
                   #condition);                                             \
      std::exit(1);                                                         \
    }                                                                       \
  } while (0)

static pcf::Identity MakeIdentity(uint8_t seed) {
  pcf::Identity id;
  id.vendor_id = 0x5143;
  id.device_id = 0x44050000u + seed;
  for (size_t i = 0; i < pcf::kUuidSize; ++i) id.uuid[i] = uint8_t(seed * 31 + i);
  return id;
}

// A driver-like blob: VkPipelineCacheHeaderVersionOne followed by opaque data.
static std::vector<uint8_t> MakeBlob(const pcf::Identity& id, uint8_t fill, size_t extra) {
  std::vector<uint8_t> blob(pcf::kVulkanHeaderSize + extra, fill);
  const uint32_t header_size = 32, version = 1;
  std::memcpy(blob.data(), &header_size, 4);
  std::memcpy(blob.data() + 4, &version, 4);
  std::memcpy(blob.data() + 8, &id.vendor_id, 4);
  std::memcpy(blob.data() + 12, &id.device_id, 4);
  std::memcpy(blob.data() + 16, id.uuid, pcf::kUuidSize);
  return blob;
}

static std::vector<uint8_t> ReadAll(const fs::path& path) {
  std::vector<uint8_t> bytes(static_cast<size_t>(fs::file_size(path)));
  FILE* f = std::fopen(path.string().c_str(), "rb");
  CHECK(f && std::fread(bytes.data(), 1, bytes.size(), f) == bytes.size());
  std::fclose(f);
  return bytes;
}

static void WriteAll(const fs::path& path, const std::vector<uint8_t>& bytes) {
  FILE* f = std::fopen(path.string().c_str(), "wb");
  CHECK(f && std::fwrite(bytes.data(), 1, bytes.size(), f) == bytes.size());
  std::fclose(f);
}

int main(int argc, char** argv) {
  const fs::path root = fs::path(argc > 1 ? argv[1] : "pipeline-cache-test");
  fs::remove_all(root);
  fs::create_directories(root);
  const fs::path main = root / "4D5309C9.vk.bin";
  const pcf::Identity a = MakeIdentity(1), b = MakeIdentity(2);
  const auto blob_a = MakeBlob(a, 0xA5, 4096), blob_b = MakeBlob(b, 0xB6, 2048);
  std::string log;

  // Missing file: nothing to seed, nothing archived.
  {
    pcf::Store store(main, a);
    CHECK(store.LoadForCreate(log).empty());
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kMissing);
  }

  // Round trip through the envelope.
  {
    pcf::Store store(main, a);
    CHECK(store.Save(blob_a.data(), blob_a.size(), log));
    CHECK(!fs::exists(fs::path(main.string() + ".tmp")));
    auto loaded = pcf::Load(main, a);
    CHECK(loaded.status == pcf::LoadStatus::kLoaded);
    CHECK(loaded.payload == blob_a);
    CHECK(pcf::Store(main, a).LoadForCreate(log) == blob_a);
  }

  // Any flipped bit, truncation or size mismatch is rejected before a driver sees it.
  {
    auto bytes = ReadAll(main);
    auto flipped = bytes;
    flipped[pcf::kEnvelopeSize + 100] ^= 1;
    WriteAll(main, flipped);
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kCorrupt);
    CHECK(pcf::Store(main, a).LoadForCreate(log).empty());
    auto truncated = bytes;
    truncated.resize(bytes.size() - 7);
    WriteAll(main, truncated);
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kCorrupt);
    WriteAll(main, std::vector<uint8_t>(bytes.begin(), bytes.begin() + 20));
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kCorrupt);
    WriteAll(main, bytes);
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kLoaded);
  }

  // A raw blob from an older build is still used when its Vulkan header matches.
  {
    WriteAll(main, blob_a);
    auto loaded = pcf::Load(main, a);
    CHECK(loaded.status == pcf::LoadStatus::kLegacyLoaded && loaded.payload == blob_a);
    CHECK(pcf::Load(main, b).status == pcf::LoadStatus::kOtherIdentity);
    WriteAll(main, std::vector<uint8_t>(64, 0x11));  // no Vulkan header at all
    CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kCorrupt);
  }

  // Boundary payloads, truncated headers and a nonuniform large payload.
  {
    for (size_t size = 0; size < pcf::kEnvelopeSize; ++size) {
      WriteAll(main, std::vector<uint8_t>(size, 0));
      CHECK(pcf::Load(main, a).status == pcf::LoadStatus::kCorrupt);
    }
    for (size_t extra : {size_t(0), size_t(1), size_t(1024 * 1024)}) {
      auto blob = MakeBlob(a, 0, extra);
      for (size_t i = pcf::kVulkanHeaderSize; i < blob.size(); ++i)
        blob[i] = uint8_t(i * 37);
      CHECK(pcf::WriteAtomically(main, blob.data(), blob.size()));
      CHECK(pcf::Load(main, a).payload == blob);
      WriteAll(main, blob);
      CHECK(pcf::Load(main, a).payload == blob);
    }
  }

  // Bounded: an absurd file is never read into memory.
  {
    pcf::Store(main, a).Save(blob_a.data(), blob_a.size(), log);
    CHECK(pcf::Load(main, a, 1024).status == pcf::LoadStatus::kTooLarge);
  }

  // Switching drivers keeps each driver's cache: B archives A's file, A gets it back.
  {
    pcf::Store(main, a).Save(blob_a.data(), blob_a.size(), log);
    pcf::Store store_b(main, b);
    CHECK(store_b.LoadForCreate(log).empty());
    CHECK(store_b.Save(blob_b.data(), blob_b.size(), log));
    CHECK(pcf::Load(main, b).payload == blob_b);
    const fs::path archive_a = pcf::ArchivePath(main, a);
    CHECK(archive_a.filename().string() == "4D5309C9." + a.Tag() + ".vk.bin");
    CHECK(pcf::Load(archive_a, a).payload == blob_a);

    pcf::Store store_a(main, a);
    CHECK(store_a.LoadForCreate(log) == blob_a);  // restored from the archive
    auto updated = MakeBlob(a, 0xC7, 4096);
    CHECK(store_a.Save(updated.data(), updated.size(), log));
    CHECK(pcf::Load(main, a).payload == updated);
    CHECK(!fs::exists(archive_a));  // its data now lives in the main file
    CHECK(pcf::Load(pcf::ArchivePath(main, b), b).payload == blob_b);
  }

  // A failed write keeps the previous file.
  {
    const auto before = ReadAll(main);
    const fs::path temp = fs::path(main.string() + ".tmp");
    fs::create_directories(temp);  // fopen("wb") on a directory fails
    auto other = MakeBlob(a, 0xD8, 128);
    CHECK(!pcf::Store(main, a).Save(other.data(), other.size(), log));
    CHECK(ReadAll(main) == before);
    fs::remove_all(temp);
  }

  // At most kMaxArchivesPerStem archives survive, the newest ones; other files stay.
  {
    const fs::path prune = root / "prune";
    fs::create_directories(prune);
    const fs::path prune_main = prune / "415607E6.vk.bin";
    WriteAll(prune / "415607E6.noearlypreamble.vk.bin", blob_a);
    WriteAll(prune / "4D5309C9.0123456789abcdef.vk.bin", blob_a);
    const auto now = fs::file_time_type::clock::now();
    std::vector<fs::path> archives;
    for (uint8_t i = 0; i < 6; ++i) {
      archives.push_back(pcf::ArchivePath(prune_main, MakeIdentity(10 + i)));
      WriteAll(archives.back(), blob_a);
      fs::last_write_time(archives.back(), now - std::chrono::hours(6 - i));
    }
    pcf::PruneArchives(prune_main);
    CHECK(!fs::exists(archives[0]) && !fs::exists(archives[1]));
    for (size_t i = 2; i < archives.size(); ++i) CHECK(fs::exists(archives[i]));
    CHECK(fs::exists(prune / "415607E6.noearlypreamble.vk.bin"));
    CHECK(fs::exists(prune / "4D5309C9.0123456789abcdef.vk.bin"));
  }

  fs::remove_all(root);
  std::puts("pipeline cache file: passed");
  return 0;
}
