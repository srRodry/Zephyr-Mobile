/**
 ******************************************************************************
 * XenDroid: on-disk store of the driver's VkPipelineCache blob.              *
 ******************************************************************************
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_FILE_H_
#define XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_FILE_H_

// Header-only and Vulkan-free so the host tests can exercise it directly.
//
// File layout (version 1, little-endian):
//   char     magic[8]      "XDVKPC" 0 1
//   uint32_t header_size   32
//   uint32_t version       1
//   uint64_t payload_size
//   uint64_t payload_hash  XXH3-64 of the payload
//   payload                vkGetPipelineCacheData output; it starts with the
//                          standard VkPipelineCacheHeaderVersionOne, which
//                          names the vendor, device and pipelineCacheUUID.
//
// The blob only helps the exact driver build that produced it, so the store
// keeps one file per identity: the main file (stable name, the one test tools
// swap) holds the current identity, and a cache written by another driver is
// moved aside to "<stem>.<identity tag>.vk.bin" instead of being overwritten -
// switching drivers back and forth no longer recompiles every pipeline. Files
// are replaced atomically (temporary file + rename), bounded in size and
// verified before the driver ever sees their data. Raw blobs written by older
// builds (no envelope) are still accepted when their Vulkan header matches.

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <string>
#include <system_error>
#include <vector>

#include "xenia/base/xxhash.h"

namespace xe {
namespace gpu {
namespace vulkan {
namespace pipeline_cache_file {

constexpr size_t kUuidSize = 16;
constexpr char kMagic[8] = {'X', 'D', 'V', 'K', 'P', 'C', 0, 1};
constexpr uint32_t kFormatVersion = 1;
constexpr size_t kEnvelopeSize = 32;
constexpr size_t kVulkanHeaderSize = 32;
constexpr uint64_t kDefaultMaxBytes = uint64_t(256) << 20;
constexpr size_t kMaxArchivesPerStem = 4;

struct Identity {
  uint32_t vendor_id = 0;
  uint32_t device_id = 0;
  uint8_t uuid[kUuidSize] = {};

  bool operator==(const Identity& other) const {
    return vendor_id == other.vendor_id && device_id == other.device_id &&
           std::memcmp(uuid, other.uuid, kUuidSize) == 0;
  }
  bool operator!=(const Identity& other) const { return !(*this == other); }

  // 16 hex digits naming this identity's archive file.
  std::string Tag() const {
    uint8_t bytes[8 + kUuidSize];
    std::memcpy(bytes, &vendor_id, 4);
    std::memcpy(bytes + 4, &device_id, 4);
    std::memcpy(bytes + 8, uuid, kUuidSize);
    char text[17];
    std::snprintf(text, sizeof(text), "%016llx",
                  static_cast<unsigned long long>(XXH3_64bits(bytes, sizeof(bytes))));
    return text;
  }
};

inline uint32_t ReadU32(const uint8_t* p) {
  uint32_t v;
  std::memcpy(&v, p, 4);
  return v;
}
inline uint64_t ReadU64(const uint8_t* p) {
  uint64_t v;
  std::memcpy(&v, p, 8);
  return v;
}

// The identity in a VkPipelineCacheHeaderVersionOne at the start of a blob.
inline bool ReadBlobIdentity(const uint8_t* data, size_t size, Identity& out) {
  if (size < kVulkanHeaderSize) return false;
  const uint32_t header_size = ReadU32(data);
  const uint32_t header_version = ReadU32(data + 4);
  // VK_PIPELINE_CACHE_HEADER_VERSION_ONE; header_size covers at least the fields.
  if (header_version != 1 || header_size < kVulkanHeaderSize || header_size > size) {
    return false;
  }
  out.vendor_id = ReadU32(data + 8);
  out.device_id = ReadU32(data + 12);
  std::memcpy(out.uuid, data + 16, kUuidSize);
  return true;
}

enum class LoadStatus {
  kMissing,        // no file
  kLoaded,         // envelope valid, identity matches
  kLegacyLoaded,   // raw blob from an older build, identity matches
  kOtherIdentity,  // valid data, but written by another device/driver build
  kCorrupt,        // unreadable, truncated, wrong checksum or no Vulkan header
  kTooLarge,       // larger than the configured bound; never read into memory
};

inline const char* LoadStatusName(LoadStatus status) {
  switch (status) {
    case LoadStatus::kMissing: return "missing";
    case LoadStatus::kLoaded: return "loaded";
    case LoadStatus::kLegacyLoaded: return "loaded (legacy format)";
    case LoadStatus::kOtherIdentity: return "written by another driver/device";
    case LoadStatus::kCorrupt: return "corrupt";
    case LoadStatus::kTooLarge: return "too large";
  }
  return "unknown";
}

struct LoadResult {
  LoadStatus status = LoadStatus::kMissing;
  // Valid for kLoaded/kLegacyLoaded only.
  std::vector<uint8_t> payload;
  // Valid when has_identity: what the file's Vulkan header names.
  Identity identity;
  bool has_identity = false;
};

inline LoadResult Load(const std::filesystem::path& path, const Identity& current,
                       uint64_t max_bytes = kDefaultMaxBytes) {
  LoadResult result;
  std::error_code ec;
  if (!std::filesystem::is_regular_file(path, ec)) return result;
  const uintmax_t file_size = std::filesystem::file_size(path, ec);
  if (ec) {
    result.status = LoadStatus::kCorrupt;
    return result;
  }
  if (file_size > max_bytes) {
    result.status = LoadStatus::kTooLarge;
    return result;
  }
  std::vector<uint8_t> bytes(static_cast<size_t>(file_size));
  FILE* file = std::fopen(path.string().c_str(), "rb");
  const bool read_ok =
      file && (bytes.empty() || std::fread(bytes.data(), 1, bytes.size(), file) == bytes.size());
  if (file) std::fclose(file);
  if (!read_ok) {
    result.status = LoadStatus::kCorrupt;
    return result;
  }
  bool legacy = true;
  size_t payload_offset = 0;
  if (bytes.size() >= sizeof(kMagic) && std::memcmp(bytes.data(), kMagic, sizeof(kMagic)) == 0) {
    legacy = false;
    if (bytes.size() < kEnvelopeSize || ReadU32(bytes.data() + 8) != kEnvelopeSize ||
        ReadU32(bytes.data() + 12) != kFormatVersion ||
        ReadU64(bytes.data() + 16) != bytes.size() - kEnvelopeSize ||
        ReadU64(bytes.data() + 24) !=
            XXH3_64bits(bytes.data() + kEnvelopeSize, bytes.size() - kEnvelopeSize)) {
      result.status = LoadStatus::kCorrupt;
      return result;
    }
    payload_offset = kEnvelopeSize;
  }
  if (!ReadBlobIdentity(bytes.data() + payload_offset, bytes.size() - payload_offset,
                        result.identity)) {
    result.status = LoadStatus::kCorrupt;
    return result;
  }
  result.has_identity = true;
  if (result.identity != current) {
    result.status = LoadStatus::kOtherIdentity;
    return result;
  }
  // Reuse the verified read buffer instead of allocating a second full blob.
  // Only the small envelope is removed; legacy raw blobs move without copying.
  if (payload_offset) {
    bytes.erase(bytes.begin(), bytes.begin() + payload_offset);
  }
  result.payload = std::move(bytes);
  result.status = legacy ? LoadStatus::kLegacyLoaded : LoadStatus::kLoaded;
  return result;
}

// Writes the envelope and payload to "<path>.tmp" and renames it over <path>.
// A failure removes the temporary file and leaves the previous file intact.
// No fsync: a process death (the common case on Android) cannot expose a
// partial file through the rename; after a power loss the worst case is a file
// that fails the checksum above and is rebuilt, never data fed to the driver.
inline bool WriteAtomically(const std::filesystem::path& path, const uint8_t* payload,
                            size_t payload_size) {
  uint8_t header[kEnvelopeSize] = {};
  std::memcpy(header, kMagic, sizeof(kMagic));
  const uint32_t header_size = uint32_t(kEnvelopeSize);
  const uint32_t version = kFormatVersion;
  const uint64_t size = payload_size;
  const uint64_t hash = XXH3_64bits(payload, payload_size);
  std::memcpy(header + 8, &header_size, 4);
  std::memcpy(header + 12, &version, 4);
  std::memcpy(header + 16, &size, 8);
  std::memcpy(header + 24, &hash, 8);
  std::filesystem::path temp = path;
  temp += ".tmp";
  std::error_code ec;
  FILE* file = std::fopen(temp.string().c_str(), "wb");
  if (!file) return false;
  bool ok = std::fwrite(header, 1, sizeof(header), file) == sizeof(header) &&
            (payload_size == 0 || std::fwrite(payload, 1, payload_size, file) == payload_size);
  ok = (std::fflush(file) == 0) && ok;
  ok = (std::fclose(file) == 0) && ok;
  if (ok) {
    std::filesystem::rename(temp, path, ec);
    ok = !ec;
  }
  if (!ok) std::filesystem::remove(temp, ec);
  return ok;
}

// "<dir>/<stem>.<tag>.vk.bin" for the main file "<dir>/<stem>.vk.bin".
inline std::filesystem::path ArchivePath(const std::filesystem::path& main_path,
                                         const Identity& identity) {
  std::string name = main_path.filename().string();
  const std::string suffix = ".vk.bin";
  if (name.size() > suffix.size() &&
      name.compare(name.size() - suffix.size(), suffix.size(), suffix) == 0) {
    name.resize(name.size() - suffix.size());
  }
  return main_path.parent_path() / (name + "." + identity.Tag() + suffix);
}

// Keeps the newest kMaxArchivesPerStem archives of one main file.
inline void PruneArchives(const std::filesystem::path& main_path,
                          size_t keep = kMaxArchivesPerStem) {
  std::string stem = main_path.filename().string();
  const std::string suffix = ".vk.bin";
  if (stem.size() <= suffix.size()) return;
  stem.resize(stem.size() - suffix.size());
  std::error_code ec;
  std::vector<std::pair<std::filesystem::file_time_type, std::filesystem::path>> archives;
  for (const auto& entry : std::filesystem::directory_iterator(main_path.parent_path(), ec)) {
    const std::string name = entry.path().filename().string();
    // "<stem>." + 16 hex + ".vk.bin"
    if (name.size() != stem.size() + 1 + 16 + suffix.size() ||
        name.compare(0, stem.size() + 1, stem + ".") != 0 ||
        name.compare(name.size() - suffix.size(), suffix.size(), suffix) != 0) {
      continue;
    }
    const std::string tag = name.substr(stem.size() + 1, 16);
    if (!std::all_of(tag.begin(), tag.end(), [](char c) {
          return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
        })) {
      continue;
    }
    archives.emplace_back(entry.last_write_time(ec), entry.path());
  }
  if (archives.size() <= keep) return;
  std::sort(archives.begin(), archives.end(),
            [](const auto& a, const auto& b) { return a.first > b.first; });
  for (size_t i = keep; i < archives.size(); ++i) {
    std::filesystem::remove(archives[i].second, ec);
  }
}

// The per-title store used by VulkanPipelineCache. Not thread-safe: the
// pipeline cache calls it from one thread at a time (load at title start,
// periodic and final saves).
class Store {
 public:
  Store(std::filesystem::path main_path, const Identity& current,
        uint64_t max_bytes = kDefaultMaxBytes)
      : main_path_(std::move(main_path)), current_(current), max_bytes_(max_bytes) {}

  // Data to seed vkCreatePipelineCache with (empty when there is none), plus a
  // one-line description for the log.
  std::vector<uint8_t> LoadForCreate(std::string& description) {
    LoadResult main = Load(main_path_, current_, max_bytes_);
    main_foreign_ = main.status == LoadStatus::kOtherIdentity;
    main_identity_ = main.identity;
    description = std::string("main file ") + LoadStatusName(main.status);
    if (main.status == LoadStatus::kLoaded || main.status == LoadStatus::kLegacyLoaded) {
      return std::move(main.payload);
    }
    const std::filesystem::path archive = ArchivePath(main_path_, current_);
    LoadResult own = Load(archive, current_, max_bytes_);
    if (own.status == LoadStatus::kLoaded || own.status == LoadStatus::kLegacyLoaded) {
      loaded_from_archive_ = true;
      description += "; restored this driver's archive " + archive.filename().string();
      return std::move(own.payload);
    }
    if (own.status != LoadStatus::kMissing) {
      description += std::string("; this driver's archive ") + LoadStatusName(own.status);
    }
    return {};
  }

  // Persists the driver's current blob as the main file. The first save after
  // loading another driver's main file moves that file to its own archive.
  bool Save(const uint8_t* data, size_t size, std::string& description) {
    std::error_code ec;
    description.clear();
    if (main_foreign_) {
      const std::filesystem::path archive = ArchivePath(main_path_, main_identity_);
      std::filesystem::rename(main_path_, archive, ec);
      if (ec) {
        description = "could not keep the other driver's cache aside (" + ec.message() + "); ";
      } else {
        description = "kept the other driver's cache as " + archive.filename().string() + "; ";
        PruneArchives(main_path_);
      }
      main_foreign_ = false;
    }
    if (!WriteAtomically(main_path_, data, size)) {
      description += "write failed, previous file kept";
      return false;
    }
    if (loaded_from_archive_) {
      // Its data now lives in the main file.
      std::filesystem::remove(ArchivePath(main_path_, current_), ec);
      loaded_from_archive_ = false;
    }
    description += "saved";
    return true;
  }

  const std::filesystem::path& main_path() const { return main_path_; }

 private:
  std::filesystem::path main_path_;
  Identity current_;
  uint64_t max_bytes_;
  bool main_foreign_ = false;
  Identity main_identity_;
  bool loaded_from_archive_ = false;
};

}  // namespace pipeline_cache_file
}  // namespace vulkan
}  // namespace gpu
}  // namespace xe

#endif  // XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_FILE_H_
