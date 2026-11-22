#pragma once

#include <memory>

#include <tox/toxav.h>

namespace tox
{
  struct av_deleter
  {
    void operator () (ToxAV *toxav)
    {
      toxav_kill (toxav);
    }
  };

  typedef std::unique_ptr<ToxAV, av_deleter> av_ptr;
}
