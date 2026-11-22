// Instance manager, JNI utilities.
#include "tox4j/Tox4j.h"

// JNI declarations from javah.
#include "generated/im_tox_tox4j_impl_jni_ToxAvJni.h"

// Header from toxcore.
#include <tox/av.h>

#ifndef SUBSYSTEM
#define SUBSYSTEM TOXAV
#define STRUCT    Toxav
#define CLASS     ToxAv
#define PREFIX    toxav
#endif

void reference_symbols_av ();

namespace av
{
  using Events = EventWriter;

  extern ToxInstances<tox::av_ptr, std::unique_ptr<Events>> instances;
}
