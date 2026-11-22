package chat.tox.ktox

import android.Manifest
import android.app.Notification
import android.app.Person
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps the process, and so Tox, running while the app is in the background, and a call's microphone and camera
 * with it. The app updates it each time the call changes.
 */
class ToxService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onDestroy() {
        running = null
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = if (update()) START_STICKY else START_NOT_STICKY

    /** Shows the call or the connection, and says whether it keeps running. */
    fun update(): Boolean {
        val app = KTox.of(this)
        val call = app.call
        val notification = if (call == null) connected(app) else ongoing(app, call)
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        if (call != null && granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (call?.video == true && granted(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        try {
            startForeground(NOTIFICATION_ID, notification, types)
        } catch (e: SecurityException) {
            // Android lets the microphone and camera start only from the front.
            Log.w("kTox", "call in the background without microphone or camera", e)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        }
        if (call == null && !app.stayConnected) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        return true
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun connected(app: KTox) =
        Notification
            .Builder(this, KTox.CONNECTED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("kTox is connected")
            .setContentIntent(app.openIntent(null, null))
            .setOngoing(true)
            .build()

    private fun ongoing(
        app: KTox,
        call: Call,
    ): Notification {
        val name = app.callFriend?.displayName ?: call.friend.hex.take(SHORT_KEY)
        val builder =
            Notification
                .Builder(this, KTox.ONGOING_CALLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(name)
                .setContentText(if (call.answered) "Ongoing call" else "Calling…")
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(app.callIntent(answer = null))
                .setOngoing(true)
        if (call.answered) builder.setUsesChronometer(true).setWhen(call.started)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(Notification.CallStyle.forOngoingCall(Person.Builder().setName(name).build(), app.hangUpIntent()))
        } else {
            builder.addAction(Notification.Action.Builder(null, "Hang up", app.hangUpIntent()).build())
        }
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 100

        /** The service while it runs, on the main thread. */
        var running: ToxService? = null
            private set
    }
}
