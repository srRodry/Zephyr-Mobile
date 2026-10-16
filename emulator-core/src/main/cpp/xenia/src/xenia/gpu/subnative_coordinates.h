// Copyright 2026 Zephyr-Mobile contributors.
// Experimental coordinate conversion for subnative host rasterization.
// Not wired into the Vulkan pipeline: do not mistake this helper for 360p support.
#ifndef XENIA_GPU_SUBNATIVE_COORDINATES_H_
#define XENIA_GPU_SUBNATIVE_COORDINATES_H_

#include <cstdint>

namespace xe::gpu::subnative {

// Half-resolution host coordinates. Use floor for the beginning and ceil for
// the end of a half-open rectangle, preserving coverage of odd guest pixels.
// Rectangle endpoints are computed only after TryHalfCoverage validates
// that the guest rectangle fits within its attachment.
constexpr uint32_t FloorHalf(uint32_t coordinate) {
  return coordinate / 2;
}
constexpr uint32_t CeilHalf(uint32_t coordinate) {
  return coordinate / 2 + (coordinate & 1u);
}

struct Rectangle {
  uint32_t x;
  uint32_t y;
  uint32_t width;
  uint32_t height;
};

// For valid guest rectangles, the result covers all touched host pixels.
// Coordinates and extents are intentionally separate to avoid truncating
// odd-sized scissor and resolve regions. Use TryHalfCoverage for untrusted
// rectangles: this low-level helper assumes the endpoints do not overflow.
constexpr Rectangle HalfCoverage(Rectangle guest) {
  const uint32_t x0 = FloorHalf(guest.x);
  const uint32_t y0 = FloorHalf(guest.y);
  const uint32_t x1 = CeilHalf(guest.x + guest.width);
  const uint32_t y1 = CeilHalf(guest.y + guest.height);
  return {x0, y0, x1 - x0, y1 - y0};
}

// Scale an attachment extent using ceil division so odd-sized allocations
// retain their final partial host pixel. Guest EDRAM addressing is unchanged.
struct Extent {
  uint32_t width;
  uint32_t height;
};

constexpr Extent HalfExtent(Extent guest) {
  return {CeilHalf(guest.width), CeilHalf(guest.height)};
}

// Invariant: half-resolution coverage must remain inside the corresponding
// half-resolution attachment. Does not permit scaling resolve addresses.
constexpr bool FitsInside(Rectangle region, Extent attachment) {
  return region.x <= attachment.width &&
         region.y <= attachment.height &&
         region.width <= attachment.width - region.x &&
         region.height <= attachment.height - region.y;
}

static_assert(HalfExtent({1280, 720}).width == 640);
static_assert(HalfExtent({1280, 720}).height == 360);
static_assert(HalfExtent({1279, 719}).width == 640);
static_assert(HalfExtent({1279, 719}).height == 360);
static_assert(FitsInside(HalfCoverage({1, 1, 3, 3}), HalfExtent({4, 4})));
static_assert(!FitsInside({1, 1, 2, 2}, {2, 2}));

// Convert a guest viewport/scissor rectangle into the host half-resolution
// rectangle and reject regions outside the corresponding attachment.
// This is a pure helper; the Vulkan command processor does not call it yet.
constexpr bool TryHalfCoverage(Rectangle guest_region, Extent guest_attachment,
                               Rectangle& host_region_out) {
  if (!FitsInside(guest_region, guest_attachment)) {
    return false;
  }
  const Rectangle host_region = HalfCoverage(guest_region);
  if (!FitsInside(host_region, HalfExtent(guest_attachment))) {
    return false;
  }
  host_region_out = host_region;
  return true;
}

constexpr bool TestHalfCoverageValid() {
  Rectangle host{};
  return TryHalfCoverage({1, 1, 3, 3}, {5, 5}, host) &&
         host.x == 0 && host.y == 0 && host.width == 2 &&
         host.height == 2;
}
constexpr bool TestHalfCoverageInvalid() {
  Rectangle host{};
  return !TryHalfCoverage({4, 4, 2, 2}, {5, 5}, host);
}
static_assert(TestHalfCoverageValid());
static_assert(TestHalfCoverageInvalid());

// The maximum 32-bit guest coordinate must not overflow when calculating
// a host extent, and the validator must reject guest rectangles that would
// overflow x + width / y + height before HalfCoverage is called.
constexpr bool TestHalfCoverageBoundary() {
  Rectangle host{};
  return TryHalfCoverage({0xFFFFFFFEu, 0, 1, 1},
                         {0xFFFFFFFFu, 1}, host) &&
         host.x == 0x7FFFFFFFu && host.width == 1 &&
         !TryHalfCoverage({0xFFFFFFFFu, 0, 1, 1},
                          {0xFFFFFFFFu, 1}, host) &&
         !TryHalfCoverage({0xFFFFFFFEu, 0, 3, 1},
                          {0xFFFFFFFFu, 1}, host);
}
static_assert(TestHalfCoverageBoundary());

// A viewport can have a fractional guest-pixel origin. Preserve the
// fractional offset in floating-point host coordinates instead of rounding
// it away when scaling to half resolution. The integer scissor uses
// HalfCoverage independently, so fractional origins do not lose coverage.
constexpr float HalfViewportCoordinate(float guest_coordinate) {
  return guest_coordinate * 0.5f;
}
constexpr float HalfViewportExtent(float guest_extent) {
  return guest_extent * 0.5f;
}
static_assert(HalfViewportCoordinate(0.5f) == 0.25f);
static_assert(HalfViewportCoordinate(-1.0f) == -0.5f);
static_assert(HalfViewportExtent(1280.0f) == 640.0f);
static_assert(HalfViewportExtent(720.0f) == 360.0f);

static_assert(FloorHalf(1280) == 640);
static_assert(CeilHalf(720) == 360);
static_assert(CeilHalf(3) == 2);
static_assert(HalfCoverage({0, 0, 1280, 720}).width == 640);
static_assert(HalfCoverage({0, 0, 1280, 720}).height == 360);
static_assert(HalfCoverage({1, 1, 3, 3}).x == 0);
static_assert(HalfCoverage({1, 1, 3, 3}).width == 2);
static_assert(HalfCoverage({1, 1, 3, 3}).height == 2);

}  // namespace xe::gpu::subnative

#endif  // XENIA_GPU_SUBNATIVE_COORDINATES_H_
