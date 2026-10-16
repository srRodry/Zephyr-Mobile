// Copyright 2026 Zephyr-Mobile contributors.
// Experimental coordinate conversion for subnative host rasterization.
// Not wired into the Vulkan pipeline: do not mistake this helper for 360p support.
#ifndef XENIA_GPU_SUBNATIVE_COORDINATES_H_
#define XENIA_GPU_SUBNATIVE_COORDINATES_H_

#include <cstdint>

namespace xe::gpu::subnative {

// Half-resolution host coordinates. Use floor for the beginning and ceil for
// the end of a half-open rectangle, preserving coverage of odd guest pixels.
// All arithmetic is widened to avoid overflow in caller-provided uint32 values.
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
// odd-sized scissor and resolve regions. Callers must ensure no overflow in
// guest x + width and y + height before invoking this function.
constexpr Rectangle HalfCoverage(Rectangle guest) {
  const uint32_t x0 = FloorHalf(guest.x);
  const uint32_t y0 = FloorHalf(guest.y);
  const uint32_t x1 = CeilHalf(guest.x + guest.width);
  const uint32_t y1 = CeilHalf(guest.y + guest.height);
  return {x0, y0, x1 - x0, y1 - y0};
}

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
