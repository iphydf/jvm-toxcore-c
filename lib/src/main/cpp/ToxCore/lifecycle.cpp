#include <memory>

#include "ToxCore.h"

using namespace core;


#include "generated/events.h"


static tox::core_ptr
tox_new_unique (Tox_Options const *options, Tox_Err_New *error)
{
  return tox::core_ptr (tox_new (options, error));
}


/**
 * A new instance, with the options set_options sets. It returns false when a
 * setter couldn't allocate.
 */
template<typename SetOptions>
static jint
new_instance (JNIEnv *env, SetOptions set_options)
{
  tox::options_ptr opts (tox_options_new (nullptr));
  if (!opts || !set_options (opts.get ()))
    {
      throw_tox_exception<Tox> (env, TOX_ERR_NEW_MALLOC);
      return 0;
    }

  return instances.with_error_handling (env,
    [env] (tox::core_ptr tox)
      {
        tox4j_assert (tox != nullptr);
        register_callbacks (tox.get ());

        // We can create the new instance outside instance_manager's critical section.
        // This call locks the instance manager.
        return instances.add (
          env,
          std::move (tox),
          std::make_unique<Events> ()
        );
      },
    tox_new_unique, opts.get ()
  );
}


#include "generated/options.h"


/*
 * Class:     im_tox_tox4j_impl_ToxCoreJni
 * Method:    toxKill
 * Signature: (I)I
 */
TOX_METHOD (void, Kill,
  jint instanceNumber)
{
  instances.kill (env, instanceNumber);
}

/*
 * Class:     im_tox_tox4j_impl_ToxCoreJni
 * Method:    toxFinalize
 * Signature: (I)V
 */
TOX_METHOD (void, Finalize,
  jint instanceNumber)
{
  instances.finalize (env, instanceNumber);
}
