#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <utility>
#include <vector>

/**
 * The events since the last iterate, as the Kotlin reader reads them: each
 * event's tag, then its fields, in the machine's byte order.
 *
 * Callbacks can come from two threads: toxav's call states come from
 * tox_iterate, its frames from toxav_iterate.
 */
class EventWriter
{
public:
  /** Writes one event, with the writer locked until it's whole. */
  class Event
  {
  public:
    Event (EventWriter &writer, uint8_t tag)
      : lock (writer.mutex)
      , buffer (writer.buffer)
    {
      buffer.push_back (tag);
    }

    void b (bool value) { buffer.push_back (value); }
    void i32 (int64_t value) { put (static_cast<int32_t> (value)); }
    void i64 (int64_t value) { put (value); }

    void
    bytes (uint8_t const *data, size_t length)
    {
      i32 (length);
      put (data, length);
    }

    void
    shorts (int16_t const *data, size_t count)
    {
      i32 (count);
      put (data, count * sizeof *data);
    }

  private:
    template<typename T>
    void put (T value) { put (&value, sizeof value); }

    void
    put (void const *data, size_t size)
    {
      if (size == 0)
        return;
      auto const *bytes = static_cast<uint8_t const *> (data);
      buffer.insert (buffer.end (), bytes, bytes + size);
    }

    std::lock_guard<std::mutex> lock;
    std::vector<uint8_t> &buffer;
  };

  Event event (uint8_t tag) { return Event (*this, tag); }

  /** The events since the last take. */
  std::vector<uint8_t>
  take ()
  {
    std::lock_guard<std::mutex> lock (mutex);
    return std::exchange (buffer, {});
  }

private:
  std::mutex mutex;
  std::vector<uint8_t> buffer;
};

/** The functions in C's size expressions: `max(width, abs(ystride))`. */
namespace size_expr
{
  inline size_t max (size_t a, size_t b) { return std::max (a, b); }
  inline size_t abs (int32_t value) { return std::abs (value); }
}
