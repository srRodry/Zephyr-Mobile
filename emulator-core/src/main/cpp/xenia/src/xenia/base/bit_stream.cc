/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2021 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xenia/base/bit_stream.h"

#include <algorithm>
#include <cstring>

#include "xenia/base/assert.h"
#include "xenia/base/byte_order.h"

namespace xe {

BitStream::BitStream(uint8_t* buffer, size_t size_in_bits)
    : buffer_(buffer), size_bits_(size_in_bits) {}

BitStream::~BitStream() = default;

void BitStream::SetOffset(size_t offset_bits) {
  assert_false(offset_bits > size_bits_);
  offset_bits_ = std::min(offset_bits, size_bits_);
}

size_t BitStream::BitsRemaining() { return size_bits_ - offset_bits_; }

bool BitStream::IsOffsetValid(size_t num_bits) {
  return num_bits <= BitsRemaining();
}

uint64_t BitStream::Peek(size_t num_bits) {
  if (!num_bits || num_bits > 57 || !IsOffsetValid(num_bits)) {
    return 0;
  }
  const size_t first = offset_bits_ / 8;
  const size_t skip = offset_bits_ % 8;
  uint64_t word = 0;
  // Interior reads keep the single word load. At the tail, copy only
  // bytes belonging to this stream, including its final partial byte.
  const size_t bytes_left = (size_bits_ / 8 - first) + (size_bits_ % 8 != 0);
  if (bytes_left >= sizeof(word)) {
    std::memcpy(&word, buffer_ + first, sizeof(word));
  } else {
    std::memcpy(&word, buffer_ + first, bytes_left);
  }
  return (xe::byte_swap(word) >> (64 - skip - num_bits)) &
         ((uint64_t(1) << num_bits) - 1);
}

uint64_t BitStream::Read(size_t num_bits) {
  if (num_bits > 57) return 0;
  const uint64_t value = Peek(num_bits);
  Advance(num_bits);
  return value;
}

bool BitStream::Write(uint64_t val, size_t num_bits) {
  if (num_bits > 57 || !IsOffsetValid(num_bits)) return false;
  while (num_bits) {
    const size_t skip = offset_bits_ % 8;
    const size_t take = std::min(num_bits, 8 - skip);
    const size_t shift = 8 - skip - take;
    const uint8_t mask = uint8_t(((1u << take) - 1) << shift);
    uint8_t& byte = buffer_[offset_bits_ / 8];
    num_bits -= take;
    byte = (byte & ~mask) | (uint8_t((val >> num_bits) << shift) & mask);
    offset_bits_ += take;
  }
  return true;
}

size_t BitStream::Copy(uint8_t* dest_buffer, size_t num_bits) {
  const size_t skip = offset_bits_ % 8;
  size_t left = std::min(num_bits, BitsRemaining());
  // XMA expects the copied frame to keep the input's initial bit offset.
  if (skip && left) {
    const size_t take = std::min(left, 8 - skip);
    const size_t shift = 8 - skip - take;
    const uint8_t mask = uint8_t(((1u << take) - 1) << shift);
    *dest_buffer = (*dest_buffer & ~mask) | (buffer_[offset_bits_ / 8] & mask);
    ++dest_buffer;
    offset_bits_ += take;
    left -= take;
  }
  const size_t bytes = left / 8;
  if (bytes) {
    std::memcpy(dest_buffer, buffer_ + offset_bits_ / 8, bytes);
    dest_buffer += bytes;
    offset_bits_ += bytes * 8;
  }
  const size_t tail = left % 8;
  if (tail) {
    const uint8_t mask = uint8_t(0xffu << (8 - tail));
    *dest_buffer = (*dest_buffer & ~mask) | (buffer_[offset_bits_ / 8] & mask);
    offset_bits_ += tail;
  }
  return skip;
}

void BitStream::Advance(size_t num_bits) {
  offset_bits_ += std::min(num_bits, BitsRemaining());
}

}  // namespace xe
