#pragma once

#include <memory>

#include <tox/tox.h>
#include <tox/tox_events.h>


namespace tox
{
  struct core_deleter
  {
    void operator () (Tox *tox)
    {
      tox_kill (tox);
    }
  };

  typedef std::unique_ptr<Tox, core_deleter> core_ptr;

  struct options_deleter
  {
    void operator () (Tox_Options *opts)
    {
      tox_options_free (opts);
    }
  };

  typedef std::unique_ptr<Tox_Options, options_deleter> options_ptr;
}
