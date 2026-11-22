package chat.tox.ktox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Connects after the phone starts, if the user wants kTox to stay connected. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !KTox.of(context).stayConnected) return
        try {
            context.startForegroundService(Intent(context, ToxService::class.java))
        } catch (_: IllegalStateException) {
            // Android said no this time: kTox connects when it's next opened.
        }
    }
}
