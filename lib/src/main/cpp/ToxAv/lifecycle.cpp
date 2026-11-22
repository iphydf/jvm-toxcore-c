#include <memory>

#include "ToxAv.h"
#include "../ToxCore/ToxCore.h"

using namespace av;


#include "generated/events.h"


static tox::av_ptr
toxav_new_unique (Tox *tox, TOXAV_ERR_NEW *error)
{
  return tox::av_ptr (toxav_new (tox, error));
}


/*
 * Class:     im_tox_tox4j_impl_ToxAvJni
 * Method:    toxavNew
 * Signature: (ZZILjava/lang/String;I)I
 */
TOX_METHOD (jint, New,
  jint toxInstanceNumber)
{
  return core::instances.with_instance (env, toxInstanceNumber,
    [=] (Tox *tox, core::Events &)
      {
        return instances.with_error_handling (env,
          [env] (tox::av_ptr toxav)
            {
              tox4j_assert (toxav != nullptr);

              auto events = std::make_unique<Events> ();
              register_callbacks (toxav.get (), events.get ());

              // We can create the new instance outside instance_manager's critical section.
              // This call locks the instance manager.
              return instances.add (
                env,
                std::move (toxav),
                std::move (events)
              );
            },
          toxav_new_unique, tox
        );
      }
  );
}

/*
 * Class:     im_tox_tox4j_impl_ToxAvJni
 * Method:    toxavKill
 * Signature: (I)I
 */
TOX_METHOD (void, Kill,
  jint instanceNumber)
{
  instances.kill (env, instanceNumber);
}

/*
 * Class:     im_tox_tox4j_impl_ToxAvJni
 * Method:    toxavFinalize
 * Signature: (I)V
 */
TOX_METHOD (void, Finalize,
  jint instanceNumber)
{
  instances.finalize (env, instanceNumber);
}
