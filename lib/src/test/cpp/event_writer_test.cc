#include "util/event_writer.h"

#include <gtest/gtest.h>

#include <cstring>
#include <string>
#include <thread>

namespace {

/** Reads what the Kotlin reader reads, or says why it can't. */
class Reader
{
public:
  explicit Reader (std::vector<uint8_t> const &bytes) : bytes (bytes) {}

  bool done () const { return pos == bytes.size (); }

  template<typename T>
  bool
  get (T &value)
  {
    if (bytes.size () - pos < sizeof value)
      return false;
    std::memcpy (&value, bytes.data () + pos, sizeof value);
    pos += sizeof value;
    return true;
  }

  bool
  skip (size_t size)
  {
    if (bytes.size () - pos < size)
      return false;
    pos += size;
    return true;
  }

private:
  std::vector<uint8_t> const &bytes;
  size_t pos = 0;
};

constexpr uint8_t kTag = 7;

// The callbacks write on one thread (tox_iterate's) while iterate takes on
// another (toxav_iterate's).
TEST (EventWriter, EventsFromAnotherThreadArriveWholeAndOnce)
{
  constexpr int32_t count = 100000;
  EventWriter writer;
  std::thread callbacks ([&writer] {
    uint8_t const payload[16] = {};
    for (int32_t i = 0; i < count; ++i)
      {
        auto event = writer.event (kTag);
        event.i32 (i);
        event.bytes (payload, i % 16);
      }
  });

  std::string error;
  int32_t next = 0;
  while (next < count && error.empty ())
    {
      std::vector<uint8_t> const taken = writer.take ();
      Reader reader (taken);
      while (!reader.done () && error.empty ())
        {
          uint8_t tag;
          int32_t number;
          int32_t length;
          if (!reader.get (tag) || !reader.get (number) || !reader.get (length) || !reader.skip (length))
            error = "event " + std::to_string (next) + " is cut short";
          else if (tag != kTag || number != next || length != next % 16)
            error = "event " + std::to_string (next) + " reads as event " + std::to_string (number);
          else
            ++next;
        }
    }
  callbacks.join ();

  EXPECT_EQ (error, "");
  EXPECT_EQ (writer.take ().size (), 0u);
}

}  // namespace
