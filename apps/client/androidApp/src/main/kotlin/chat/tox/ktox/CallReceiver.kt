package chat.tox.ktox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Hang up or Decline in a call's notification. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        KTox
            .of(context)
            .profiles.caller
            ?.hangUp()
    }
}
